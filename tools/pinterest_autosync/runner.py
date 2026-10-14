from __future__ import annotations

import json
import logging
import os
import random
import time
from pathlib import Path

from .connectors import OfficialBoards, PinterestAccessDenied, PublicTopics
from .database import Database
from .downloader import StorageLimitReached, UnsupportedMedia, save_candidate
from .models import Candidate

LOG = logging.getLogger("pinterest_autosync")


class AlreadyRunning(Exception):
    pass


class ProcessLock:
    def __init__(self, path: Path):
        self.path = path
        self.handle = None

    def __enter__(self):
        self.handle = self.path.open("a+b")
        try:
            if os.name == "nt":
                import msvcrt
                self.handle.seek(0)
                self.handle.write(b"1")
                self.handle.flush()
                self.handle.seek(0)
                msvcrt.locking(self.handle.fileno(), msvcrt.LK_NBLCK, 1)
            else:
                import fcntl
                fcntl.flock(self.handle.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        except (OSError, IOError):
            self.handle.close()
            self.handle = None
            raise AlreadyRunning("Pinterest synchronization is already running")
        return self

    def __exit__(self, *_):
        if self.handle:
            if os.name == "nt":
                import msvcrt
                self.handle.seek(0)
                msvcrt.locking(self.handle.fileno(), msvcrt.LK_UNLCK, 1)
            else:
                import fcntl
                fcntl.flock(self.handle.fileno(), fcntl.LOCK_UN)
            self.handle.close()


def from_row(row) -> Candidate:
    return Candidate(key=row["key"], source=row["source"], source_ref=row["source_ref"],
                     pin_id=row["pin_id"], pin_url=row["pin_url"], kind=row["kind"],
                     media_url=row["media_url"], raw=json.loads(row["raw_json"]))


def eligible(row, base_dir, limits):
    if row is None:
        return False
    if row["status"] in ("done", "duplicate"):
        path = row["relative_path"]
        return not (path and (base_dir / path).is_file())
    if row["status"] == "unsupported" or row["attempts"] >= int(limits["max_attempts"]):
        return False
    if row["last_attempt_at"]:
        from datetime import datetime, timedelta, timezone
        last = datetime.fromisoformat(row["last_attempt_at"])
        if datetime.now(timezone.utc) - last < timedelta(hours=float(limits["retry_after_hours"])):
            return False
    return True


def run_scan(config: dict, root: Path, official=None, public=None, db=None) -> dict:
    output = (root / config["output_dir"]).resolve()
    output.mkdir(parents=True, exist_ok=True)
    limits = config["limits"]
    owns_db = db is None
    if db is None:
        db = Database(root / "pinterest.sqlite3")
    stats = {"new_downloads": 0, "content_duplicates": 0, "failed": 0,
             "unsupported": 0, "discovered": 0, "already_present": 0,
             "daily_used": 0, "official_api_used_24h": 0, "download_attempts": 0}
    deadline = time.monotonic() + float(limits.get("scan_timeout_seconds", 900))
    try:
        current = db.completed_today(config["timezone"])
        stats["daily_used"] = current
        if current >= int(limits["daily_downloads"]):
            return stats
        public = public or PublicTopics(config["unofficial"])
        official = official or OfficialBoards(config["official"], db)
        items = []
        old = [from_row(row) for row in db.recent_failed(
            int(limits["max_attempts"]), float(limits["retry_after_hours"]), 100)]
        if config["official"].get("enabled", False):
            try:
                items.extend(official.discover(
                    int(limits["official_api_requests_per_rolling_24h"])))
            except PinterestAccessDenied:
                # Authentication failure needs operator action. Do not turn it
                # into a misleading successful run with zero discoveries.
                raise
            except Exception:
                LOG.exception("Official Pinterest discovery failed")
        if config["unofficial"].get("enabled", False):
            try:
                items.extend(public.discover())
            except Exception:
                LOG.exception("Unofficial Pinterest discovery failed")
                if not config["official"].get("enabled", False):
                    raise
        stats["discovered"] = len(items)
        deduplicated = {candidate.key: candidate for candidate in old}
        for candidate in items:
            db.upsert_candidate(candidate)
            if (candidate.key not in deduplicated or candidate.native is not None or
                    candidate.media_url is not None):
                deduplicated[candidate.key] = candidate
        candidates = list(deduplicated.values())
        if config.get("shuffle_candidates", False):
            random.shuffle(candidates)
        cap = min(int(limits["max_downloads_per_scan"]),
                  int(limits["daily_downloads"]) - current)
        attempt_cap = int(limits.get("max_attempts_per_scan", min(60, cap * 2)))
        for candidate in candidates:
            if (stats["new_downloads"] >= cap or stats["download_attempts"] >= attempt_cap or
                    time.monotonic() >= deadline):
                break
            row = db.get(candidate.key)
            if not eligible(row, output, limits):
                stats["already_present"] += 1
                continue
            if candidate.kind not in config["media_types"]:
                continue
            stats["download_attempts"] += 1
            db.begin_attempt(candidate.key)
            if candidate.source == "unofficial" and candidate.native is None:
                if not candidate.pin_url:
                    db.mark_failed(candidate.key, "No source URL for rediscovery", "unsupported")
                    continue
                fresh = public.get_single(candidate.pin_url)
                if not fresh:
                    db.mark_failed(candidate.key, "Source rediscovery unavailable")
                    continue
                candidate.native = fresh.native
                candidate.kind = fresh.kind
                if candidate.kind not in config["media_types"]:
                    db.mark_failed(candidate.key, "Source media type is excluded", "unsupported")
                    continue
            try:
                state, path = save_candidate(candidate, db, output, limits)
                if state == "saved":
                    stats["new_downloads"] += 1
                    LOG.info("Saved Pinterest %s to %s", candidate.key, path)
                else:
                    stats["content_duplicates"] += 1
            except UnsupportedMedia as error:
                db.mark_failed(candidate.key, str(error), "unsupported")
                stats["unsupported"] += 1
            except StorageLimitReached as error:
                db.mark_failed(candidate.key, str(error))
                stats["failed"] += 1
                if "storage" in str(error).lower() and "limit" in str(error).lower():
                    break
            except Exception as error:
                db.mark_failed(candidate.key, type(error).__name__)
                stats["failed"] += 1
            if float(limits["delay_between_downloads_seconds"]) > 0:
                time.sleep(float(limits["delay_between_downloads_seconds"]))
        stats["daily_used"] = db.completed_today(config["timezone"])
        stats["official_api_used_24h"] = db.api_used_24h()
        return stats
    finally:
        if owns_db:
            db.close()
