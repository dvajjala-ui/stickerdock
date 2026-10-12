from __future__ import annotations

import json
import sqlite3
from datetime import datetime, timedelta, timezone
from pathlib import Path
from zoneinfo import ZoneInfo

from .models import Candidate


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


class Database:
    def __init__(self, path: Path):
        self.db = sqlite3.connect(path)
        self.db.row_factory = sqlite3.Row
        self.db.execute("PRAGMA journal_mode=WAL")
        self.db.execute("PRAGMA busy_timeout=5000")
        self.db.executescript("""
        CREATE TABLE IF NOT EXISTS pins (
           key TEXT PRIMARY KEY, source TEXT NOT NULL, source_ref TEXT NOT NULL,
           pin_id TEXT, pin_url TEXT, kind TEXT NOT NULL, media_url TEXT,
           raw_json TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'pending',
           relative_path TEXT, content_hash TEXT, file_bytes INTEGER DEFAULT 0,
           attempts INTEGER NOT NULL DEFAULT 0, error TEXT, discovered_at TEXT NOT NULL,
           last_seen_at TEXT NOT NULL, last_attempt_at TEXT, downloaded_at TEXT
        );
        CREATE INDEX IF NOT EXISTS idx_pins_status ON pins(status, last_attempt_at);
        CREATE INDEX IF NOT EXISTS idx_pins_done ON pins(downloaded_at);
        CREATE TABLE IF NOT EXISTS files (
           sha256 TEXT PRIMARY KEY, relative_path TEXT NOT NULL, file_bytes INTEGER NOT NULL
        );
        CREATE TABLE IF NOT EXISTS api_requests (
           id INTEGER PRIMARY KEY AUTOINCREMENT, attempted_at TEXT NOT NULL
        );
        CREATE INDEX IF NOT EXISTS idx_api_requests_attempted ON api_requests(attempted_at);
        """)
        self.db.commit()

    def close(self):
        self.db.close()

    def __enter__(self):
        return self

    def __exit__(self, *_):
        self.close()

    def upsert_candidate(self, item: Candidate):
        now = now_iso()
        self.db.execute("""
          INSERT INTO pins (key,source,source_ref,pin_id,pin_url,kind,media_url,raw_json,
                            status,discovered_at,last_seen_at)
          VALUES (?,?,?,?,?,?,?,?,?,?,?)
          ON CONFLICT(key) DO UPDATE SET last_seen_at=excluded.last_seen_at,
            source_ref=excluded.source_ref,
            pin_url=COALESCE(excluded.pin_url,pins.pin_url),
            media_url=COALESCE(excluded.media_url,pins.media_url),
            status=CASE WHEN pins.status='unsupported' AND pins.media_url IS NULL
                        AND excluded.media_url IS NOT NULL THEN 'pending' ELSE pins.status END,
            attempts=CASE WHEN pins.status='unsupported' AND pins.media_url IS NULL
                          AND excluded.media_url IS NOT NULL THEN 0 ELSE pins.attempts END,
            raw_json=excluded.raw_json
        """, (item.key, item.source, item.source_ref, item.pin_id, item.pin_url, item.kind,
              item.media_url, json.dumps(item.raw, default=str), "pending", now, now))
        self.db.commit()

    def get(self, key: str):
        return self.db.execute("SELECT * FROM pins WHERE key=?", (key,)).fetchone()

    def recent_failed(self, max_attempts: int, retry_after_hours: float, limit: int):
        cutoff = (datetime.now(timezone.utc) - timedelta(hours=retry_after_hours)).isoformat(
            timespec="seconds")
        return self.db.execute("""
          SELECT * FROM pins WHERE status IN ('failed','pending') AND attempts < ?
          AND (last_attempt_at IS NULL OR last_attempt_at <= ?)
          ORDER BY COALESCE(last_attempt_at, discovered_at) ASC LIMIT ?
        """, (max_attempts, cutoff, limit)).fetchall()

    def begin_attempt(self, key: str):
        self.db.execute("UPDATE pins SET status='pending', attempts=attempts+1, "
                        "last_attempt_at=?, error=NULL WHERE key=?", (now_iso(), key))
        self.db.commit()

    def update_media_kind(self, key: str, kind: str):
        self.db.execute("UPDATE pins SET kind=? WHERE key=?", (kind, key))
        self.db.commit()

    def mark_failed(self, key: str, error: str, status: str = "failed"):
        self.db.execute("UPDATE pins SET status=?, error=? WHERE key=?",
                        (status, error[:700], key))
        self.db.commit()

    def mark_done(self, key: str, path: str, sha: str, size: int, duplicate: bool = False):
        self.db.execute("""
          UPDATE pins SET status=?, relative_path=?,content_hash=?,file_bytes=?,error=NULL,
                          downloaded_at=? WHERE key=?
        """, ("duplicate" if duplicate else "done", path, sha, size,
              None if duplicate else now_iso(), key))
        self.db.commit()

    def file_for_hash(self, sha: str):
        return self.db.execute("SELECT relative_path,file_bytes FROM files WHERE sha256=?",
                               (sha,)).fetchone()

    def register_hash(self, sha: str, path: str, size: int):
        self.db.execute("INSERT OR REPLACE INTO files(sha256,relative_path,file_bytes) "
                        "VALUES(?,?,?)", (sha, path, size))
        self.db.commit()

    def completed_today(self, tz_name: str) -> int:
        tz = ZoneInfo(tz_name)
        date = datetime.now(tz).date()
        start = datetime.combine(date, datetime.min.time(), tzinfo=tz).astimezone(
            timezone.utc).isoformat(timespec="seconds")
        end = datetime.combine(date + timedelta(days=1), datetime.min.time(),
                               tzinfo=tz).astimezone(timezone.utc).isoformat(timespec="seconds")
        return self.db.execute("SELECT COUNT(*) FROM pins WHERE status='done' "
                               "AND downloaded_at >= ? AND downloaded_at < ?",
                               (start, end)).fetchone()[0]

    def recorded_storage_bytes(self) -> int:
        return int(self.db.execute("SELECT COALESCE(SUM(file_bytes),0) FROM files").fetchone()[0])

    def consume_api_request(self, cap: int) -> bool:
        cutoff = (datetime.now(timezone.utc) - timedelta(hours=24)).isoformat(timespec="seconds")
        self.db.execute("BEGIN IMMEDIATE")
        try:
            self.db.execute("DELETE FROM api_requests WHERE attempted_at < ?", (cutoff,))
            count = self.db.execute("SELECT COUNT(*) FROM api_requests").fetchone()[0]
            if count >= cap:
                self.db.commit()
                return False
            self.db.execute("INSERT INTO api_requests(attempted_at) VALUES (?)", (now_iso(),))
            self.db.commit()
            return True
        except Exception:
            self.db.rollback()
            raise

    def api_used_24h(self) -> int:
        cutoff = (datetime.now(timezone.utc) - timedelta(hours=24)).isoformat(timespec="seconds")
        return self.db.execute("SELECT COUNT(*) FROM api_requests WHERE attempted_at >= ?",
                               (cutoff,)).fetchone()[0]

    def counts(self):
        return {row[0]: row[1] for row in self.db.execute(
            "SELECT status,COUNT(*) FROM pins GROUP BY status")}

    def ready_for_upload(self):
        return self.db.execute("""
          SELECT key,source,source_ref,pin_id,pin_url,kind,media_url,raw_json,
                 relative_path,content_hash,file_bytes
          FROM pins WHERE status='done' AND relative_path IS NOT NULL
          ORDER BY downloaded_at ASC
        """).fetchall()

    def offload_file(self, sha: str):
        self.db.execute("UPDATE files SET file_bytes=0 WHERE sha256=?", (sha,))
        self.db.execute("UPDATE pins SET file_bytes=0 WHERE content_hash=?", (sha,))
        self.db.commit()
