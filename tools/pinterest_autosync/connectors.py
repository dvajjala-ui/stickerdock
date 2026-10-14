from __future__ import annotations

import hashlib
import json
import logging
import os
import re
from urllib.parse import urlparse

import requests

from .models import Candidate

LOG = logging.getLogger("pinterest_autosync")


class APIBudgetExceeded(Exception):
    pass


class PinterestAccessDenied(Exception):
    pass


class OfficialBoards:
    """Read only the boards available to the authorized Pinterest OAuth token."""

    API_BASE = "https://api.pinterest.com/v5"

    def __init__(self, config: dict, db, session=None):
        self.cfg = config
        self.db = db
        self.http = session or requests.Session()
        self.token = os.environ.get(config["access_token_env"], "").strip()

    def fetch_page(self, board_id: str, bookmark: str | None) -> dict:
        if not self.db.consume_api_request(int(self.cfg["_api_cap"])):
            raise APIBudgetExceeded("Local Pinterest rolling request budget reached")
        params = {"page_size": int(self.cfg["page_size"])}
        if bookmark:
            params["bookmark"] = bookmark
        response = self.http.get(
            f"{self.API_BASE}/boards/{board_id}/pins",
            headers={"Authorization": f"Bearer {self.token}", "Accept": "application/json"},
            params=params,
            timeout=float(self.cfg["request_timeout_seconds"]),
        )
        if response.status_code == 403:
            raise PinterestAccessDenied("Pinterest denied access to the authorized board")
        if response.status_code == 401:
            raise PinterestAccessDenied("Pinterest OAuth token is expired or invalid")
        if response.status_code == 429:
            raise APIBudgetExceeded("Pinterest provider rate limit reached")
        response.raise_for_status()
        data = response.json()
        if not isinstance(data, dict) or not isinstance(data.get("items"), list):
            raise ValueError("Unexpected Pinterest boards response")
        return data

    def fetch_boards_page(self, bookmark: str | None) -> dict:
        """List boards visible to the authorized account when no IDs were configured."""
        if not self.db.consume_api_request(int(self.cfg["_api_cap"])):
            raise APIBudgetExceeded("Local Pinterest rolling request budget reached")
        params = {"page_size": min(250, int(self.cfg["page_size"]))}
        if bookmark:
            params["bookmark"] = bookmark
        response = self.http.get(
            f"{self.API_BASE}/boards",
            headers={"Authorization": f"Bearer {self.token}", "Accept": "application/json"},
            params=params,
            timeout=float(self.cfg["request_timeout_seconds"]),
        )
        if response.status_code == 403:
            raise PinterestAccessDenied("Pinterest denied access to the authorized account's boards")
        if response.status_code == 401:
            raise PinterestAccessDenied("Pinterest OAuth token is expired or invalid")
        if response.status_code == 429:
            raise APIBudgetExceeded("Pinterest provider rate limit reached")
        response.raise_for_status()
        data = response.json()
        if not isinstance(data, dict) or not isinstance(data.get("items"), list):
            raise ValueError("Unexpected Pinterest board-list response")
        return data

    def authorized_boards(self) -> list[dict]:
        configured = [board for board in self.cfg["boards"] if board.get("enabled", True)]
        if configured:
            return configured
        boards, bookmark, seen = [], None, set()
        # Board discovery is bounded independently of the number of user boards.
        for _ in range(3):
            page = self.fetch_boards_page(bookmark)
            for board in page["items"]:
                board_id = str(board.get("id", "")) if isinstance(board, dict) else ""
                if re.fullmatch(r"[0-9]{5,25}", board_id):
                    boards.append({"id": board_id, "enabled": True})
            bookmark = page.get("bookmark")
            if not bookmark or bookmark in seen:
                break
            seen.add(bookmark)
        return boards

    def discover(self, api_cap: int) -> list[Candidate]:
        if not self.cfg.get("enabled", False):
            return []
        if not self.token:
            LOG.warning("Official connector enabled but %s is unset", self.cfg["access_token_env"])
            return []
        self.cfg["_api_cap"] = api_cap
        found = []
        for board in self.authorized_boards():
            board_id = str(board["id"])
            if not re.fullmatch(r"[0-9]{5,25}", board_id):
                LOG.warning("Skipping invalid Pinterest board ID")
                continue
            bookmark = None
            seen_bookmarks = set()
            for _ in range(int(self.cfg["max_pages_per_board"])):
                try:
                    page = self.fetch_page(board_id, bookmark)
                except (PinterestAccessDenied, APIBudgetExceeded):
                    LOG.exception("Official connector stopped for board %s", board_id)
                    break
                except requests.RequestException:
                    LOG.exception("Official Pinterest board request failed")
                    break
                found.extend(self._candidate(pin, board_id) for pin in page["items"]
                             if isinstance(pin, dict) and pin.get("id"))
                bookmark = page.get("bookmark")
                if not bookmark or bookmark in seen_bookmarks:
                    break
                seen_bookmarks.add(bookmark)
        return found

    @staticmethod
    def _candidate(pin: dict, board_id: str) -> Candidate:
        pin_id = str(pin["id"])
        media = pin.get("media") or {}
        kind = ("video" if str(media.get("media_type", "")).lower() == "video" or
                str(pin.get("creative_type", "")).upper() == "VIDEO" else "image")
        url = None
        if kind == "video":
            url = pin.get("video_url") or media.get("video_url")
        else:
            images = media.get("images") or {}
            options = [value for value in images.values()
                       if isinstance(value, dict) and value.get("url")]
            if options:
                url = max(options, key=lambda value:
                          int(value.get("width") or 0) * int(value.get("height") or 0))["url"]
        if not isinstance(url, str):
            url = None
        return Candidate(key=f"pin:{pin_id}", source="official",
                         source_ref=f"board:{board_id}", pin_id=pin_id,
                         pin_url=f"https://www.pinterest.com/pin/{pin_id}/",
                         kind=kind, media_url=url, raw=pin)


class PublicTopics:
    """Optional public-topic adapter. It does not use login cookies or bypasses."""

    def __init__(self, config: dict, client=None):
        self.cfg = config
        self.client = client

    def _client(self):
        if self.client is None:
            try:
                from pinterest_dl import PinterestDL
            except ImportError as error:
                raise RuntimeError("Install tools/requirements-pinterest-unofficial.txt") from error
            self.client = PinterestDL.with_api(timeout=float(self.cfg["request_timeout_seconds"]))
        return self.client

    @staticmethod
    def from_native(media, topic: str) -> Candidate:
        payload = media.to_dict()
        if not isinstance(payload, dict):
            raise ValueError("Unsupported pinterest-dl media format")
        pin_id = next((str(payload[key]) for key in ("pin_id", "pinId", "id")
                       if payload.get(key)), None)
        urls = [str(payload[key]) for key in ("pin_url", "link", "url", "source_url")
                if isinstance(payload.get(key), str)]
        pin_url = next((url for url in urls if re.search(
            r"https?://(?:www\.)?pinterest\.[^/]+/pin/\d+", url)), None)
        if pin_id and not pin_url:
            pin_url = f"https://www.pinterest.com/pin/{pin_id}/"
        if not pin_id and pin_url:
            match = re.search(r"/pin/(\d+)", pin_url)
            pin_id = match.group(1) if match else None
        label = str(payload.get("media_type") or payload.get("type") or "").lower()
        has_streams = bool(payload.get("streams") or payload.get("videos") or
                           payload.get("video_url"))
        kind = "video" if label in ("video", "video_pin") or has_streams else "image"
        fallback = next((str(payload[key]) for key in ("image_url", "src", "url", "link")
                         if isinstance(payload.get(key), str)), None)
        identity = (f"pin:{pin_id}" if pin_id else "public:" + hashlib.sha256(
            str(fallback or json.dumps(payload, sort_keys=True, default=str)).encode()
        ).hexdigest()[:32])
        return Candidate(key=identity, source="unofficial", source_ref=f"topic:{topic}",
                         pin_id=pin_id, pin_url=pin_url, kind=kind,
                         media_url=None, raw=payload, native=media)

    def discover(self) -> list[Candidate]:
        if not self.cfg.get("enabled", False):
            return []
        collected = []
        for item in self.cfg["topics"]:
            if not item.get("enabled", True):
                continue
            topic = str(item["query"]).strip()
            if not topic:
                continue
            try:
                results = self._client().search(
                    query=topic, num=int(self.cfg["results_per_topic"]),
                    delay=float(self.cfg["request_delay_seconds"]))
                collected.extend(self.from_native(media, topic) for media in results)
            except Exception:
                LOG.exception("Public Pinterest topic discovery failed")
        return collected

    def get_single(self, pin_url: str) -> Candidate | None:
        parsed = urlparse(pin_url)
        host = (parsed.hostname or "").lower()
        if host not in ("pinterest.com", "www.pinterest.com") or not re.fullmatch(
                r"/pin/\d+/?", parsed.path):
            return None
        try:
            medias = self._client().scrape(url=pin_url, num=1)
            return self.from_native(medias[0], "retry") if medias else None
        except Exception:
            LOG.warning("Could not rediscover Pinterest item for retry")
            return None
