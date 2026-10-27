"""StickerDock's server-side caller. Discovery/downloads run only in HandNavigate."""
from __future__ import annotations
import argparse
from datetime import datetime
import hashlib
import json
import os
import time
from pathlib import Path
import urllib.error
import urllib.parse
import urllib.request
from zoneinfo import ZoneInfo


class HandNavigate:
    def __init__(self, url=None, token=None):
        self.url = (url or os.environ.get("HANDNAVIGATE_URL", "")).rstrip("/")
        self.token = token or os.environ.get("HANDNAVIGATE_API_TOKEN", "")
        parsed = urllib.parse.urlsplit(self.url)
        if (parsed.scheme not in {"http", "https"} or not parsed.hostname or parsed.username or parsed.password
                or parsed.query or parsed.fragment or parsed.path
                or (parsed.scheme == "http" and parsed.hostname not in {"localhost", "127.0.0.1", "::1"})):
            raise ValueError("Use HandNavigate HTTPS, or HTTP on a local loopback tunnel")
        if not isinstance(self.token, str) or len(self.token) < 32:
            raise ValueError("Configure the server-only HandNavigate API token")

    def call(self, path, body=None):
        request = urllib.request.Request(self.url + path,
            data=json.dumps(body).encode() if body is not None else None,
            headers={"Authorization": "Bearer " + self.token, "Content-Type": "application/json"})
        # Do not send the service token to a redirect destination.
        class NoRedirect(urllib.request.HTTPRedirectHandler):
            def redirect_request(self, *args, **kwargs):
                return None
        try:
            with urllib.request.build_opener(NoRedirect).open(request, timeout=45) as response:
                raw = response.read(4_000_001)
        except urllib.error.HTTPError as error:
            raise ValueError("HandNavigate returned HTTP " + str(error.code)) from None
        if len(raw) > 4_000_000:
            raise ValueError("HandNavigate response exceeded the client bound")
        return json.loads(raw)

    def collect(self, topics, sources, limit=20, day=None):
        day = day or datetime.now(ZoneInfo("Asia/Kolkata")).date().isoformat()
        values = {"topics": topics, "sources": sources, "limit": limit, "destination": "stickerdock"}
        digest = hashlib.sha256(json.dumps(values, sort_keys=True, separators=(",", ":")).encode()).hexdigest()[:20]
        values["requestId"] = "stickerdock-" + day + "-" + digest
        return self.call("/v1/collections", values)

    def wait(self, collection_id, timeout=960, interval=5):
        import uuid
        identity = str(uuid.UUID(str(collection_id)))
        end = time.monotonic() + timeout
        while time.monotonic() < end:
            result = self.call("/v1/collections/" + identity)
            if result.get("status") not in {"QUEUED", "RUNNING", "WAITING_FOR_LOGIN"}:
                return result
            time.sleep(interval)
        raise ValueError("HandNavigate collection wait timed out; inspect its durable status before retrying")


def brief_topics(brief):
    topics = brief.get("topics") or [item.get("topic") for item in brief.get("topic_plans", [])]
    if not topics:
        topics = [item.get("sticker_angle") or item.get("title") for item in brief.get("items", [])]
    return list(dict.fromkeys(" ".join(t.split())[:240] for t in topics if isinstance(t, str) and t.strip()))[:10]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--topic", action="append", default=[])
    parser.add_argument("--sources", default="pinterest,reddit")
    parser.add_argument("--limit", type=int, default=20)
    parser.add_argument("--status")
    parser.add_argument("--brief", type=Path, help="Local planner output JSON containing topics or items[].topic")
    parser.add_argument("--latest-brief", action="store_true", help="Read today's existing content_briefs row; never call an AI provider")
    parser.add_argument("--wait", action="store_true", help="Poll the durable collection; exit nonzero unless storage verification completes")
    args = parser.parse_args()
    client = HandNavigate()
    if args.status:
        import uuid
        result = client.call("/v1/collections/" + str(uuid.UUID(args.status)))
    else:
        topics = list(args.topic)
        if args.brief:
            brief = json.loads(args.brief.read_text(encoding="utf-8"))
            topics += brief_topics(brief)
        if args.latest_brief:
            from datetime import timezone
            from tools.supabase_factory import SupabaseFactory
            # The existing planner keys briefs by UTC day; collection budgets use Asia/Kolkata.
            day = datetime.now(timezone.utc).date().isoformat()
            brief = SupabaseFactory().find("content_briefs", "day", day)
            if not brief:
                raise ValueError("Today's planner brief is not available; no collection was submitted")
            topics += brief_topics(brief)
        if not topics:
            raise ValueError("Supply --topic or a planner brief; the caller does not invent search topics")
        result = client.collect(topics[:10], args.sources.split(","), args.limit)
    if args.wait:
        result = client.wait(result["id"])
    # Never print the token, full local media paths, or browser profile data.
    print(json.dumps({key: result.get(key) for key in ("id", "status", "limit", "reused", "error")}))
    if args.wait and result.get("status") != "VERIFIED_STORAGE":
        raise SystemExit(1)


if __name__ == "__main__":
    main()
