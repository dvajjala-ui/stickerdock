#!/usr/bin/env python3
"""Report StickerDock Supabase storage usage without deleting remote objects."""
from __future__ import annotations

from collections import deque
from datetime import date, datetime, timedelta, timezone
import json
import os
from pathlib import PurePosixPath
import re

from supabase_factory import PRIVATE_BUCKET, PUBLIC_BUCKET, SupabaseFactory

MAX_OBJECTS = 50_000
PAGE_SIZE = 100
GENERATED_DAY_RE = re.compile(r"^generated/(\d{4}-\d{2}-\d{2})/")


def _joined(prefix, name):
    name = str(name or "").strip("/")
    prefix = str(prefix or "").strip("/")
    if not name:
        return ""
    if prefix and not name.startswith(prefix + "/"):
        return prefix + "/" + name
    return name


def iter_objects(client, bucket, root="", max_objects=MAX_OBJECTS):
    """Recursively enumerate Storage API objects while bounding provider work."""
    queue = deque([str(root or "").strip("/")])
    visited = set()
    yielded = 0
    while queue:
        prefix = queue.popleft()
        if prefix in visited:
            continue
        visited.add(prefix)
        for offset in range(0, max_objects, PAGE_SIZE):
            page = client.list_objects(bucket, prefix, PAGE_SIZE, offset=offset)
            if not isinstance(page, list):
                raise ValueError("Storage list response must be an array")
            for item in page:
                if not isinstance(item, dict):
                    continue
                path = _joined(prefix, item.get("name"))
                if not path or ".." in PurePosixPath(path).parts:
                    continue
                metadata = item.get("metadata")
                size = metadata.get("size") if isinstance(metadata, dict) else None
                if isinstance(size, (int, float)) and not isinstance(size, bool) and size >= 0:
                    yielded += 1
                    if yielded > max_objects:
                        raise ValueError("Storage scan exceeds object limit")
                    yield {
                        "path": path,
                        "size": int(size),
                        "created_at": item.get("created_at"),
                        "updated_at": item.get("updated_at"),
                    }
                else:
                    queue.append(path)
            if len(page) < PAGE_SIZE:
                break


def bucket_usage(client, bucket):
    objects = list(iter_objects(client, bucket))
    return {
        "bucket": bucket,
        "objects": len(objects),
        "bytes": sum(item["size"] for item in objects),
        "items": objects,
    }


def retention_candidates(items, today=None, days=30):
    if not 1 <= int(days) <= 3650:
        raise ValueError("Retention days must be between 1 and 3650")
    today = today or datetime.now(timezone.utc).date()
    cutoff = today - timedelta(days=int(days))
    grouped = {}
    for item in items:
        match = GENERATED_DAY_RE.match(str(item.get("path", "")))
        if not match:
            continue
        try:
            generated_day = date.fromisoformat(match.group(1))
        except ValueError:
            continue
        if generated_day >= cutoff:
            continue
        record = grouped.setdefault(match.group(1), {"day": match.group(1), "objects": 0, "bytes": 0})
        record["objects"] += 1
        record["bytes"] += int(item.get("size", 0))
    return [grouped[key] for key in sorted(grouped)]


def usage_report(client, quota_mb=None, warn_percent=70.0, fail_percent=90.0,
                 retention_days=30, today=None):
    private = bucket_usage(client, PRIVATE_BUCKET)
    public = bucket_usage(client, PUBLIC_BUCKET)
    total = private["bytes"] + public["bytes"]
    if not 1 <= float(warn_percent) < float(fail_percent) <= 100:
        raise ValueError("Storage thresholds must satisfy 1 <= warn < fail <= 100")
    quota = None
    percent = None
    level = "ok"
    if quota_mb not in (None, ""):
        quota = int(float(quota_mb) * 1024 * 1024)
        if quota <= 0:
            raise ValueError("Storage quota must be positive")
        percent = total * 100.0 / quota
        if percent >= float(fail_percent):
            level = "fail"
        elif percent >= float(warn_percent):
            level = "warn"
    return {
        "total_bytes": total,
        "quota_bytes": quota,
        "usage_percent": round(percent, 2) if percent is not None else None,
        "level": level,
        "buckets": [
            {key: private[key] for key in ("bucket", "objects", "bytes")},
            {key: public[key] for key in ("bucket", "objects", "bytes")},
        ],
        "private_generated_retention_days": int(retention_days),
        "private_generated_candidates": retention_candidates(
            private["items"], today=today, days=int(retention_days)),
    }


def _mb(value):
    return f"{value / 1024 / 1024:.2f} MB"


def markdown(report):
    lines = ["## StickerDock Supabase storage", "", "| Bucket | Objects | Usage |", "| --- | ---: | ---: |"]
    for bucket in report["buckets"]:
        lines.append(f"| `{bucket['bucket']}` | {bucket['objects']} | {_mb(bucket['bytes'])} |")
    lines += ["", f"**Total:** {_mb(report['total_bytes'])}"]
    if report["quota_bytes"]:
        lines[-1] += f" / {_mb(report['quota_bytes'])} ({report['usage_percent']:.2f}%)"
    else:
        lines.append("No quota variable is configured, so this run is report-only.")
    candidates = report["private_generated_candidates"]
    lines += ["", f"Private generated artifacts older than {report['private_generated_retention_days']} days: {len(candidates)} day(s)."]
    if candidates:
        candidate_bytes = sum(item["bytes"] for item in candidates)
        lines.append(f"Retention candidates total {_mb(candidate_bytes)}. Review manually; this workflow never deletes objects.")
    return "\n".join(lines) + "\n"


def main():
    client = SupabaseFactory()
    quota = os.environ.get("SUPABASE_STORAGE_QUOTA_MB", "").strip() or None
    warn = float(os.environ.get("SUPABASE_STORAGE_WARN_PERCENT", "70"))
    fail = float(os.environ.get("SUPABASE_STORAGE_FAIL_PERCENT", "90"))
    retention = int(os.environ.get("PRIVATE_GENERATED_RETENTION_DAYS", "30"))
    report = usage_report(client, quota, warn, fail, retention)
    print(json.dumps(report, separators=(",", ":")))
    summary = os.environ.get("GITHUB_STEP_SUMMARY", "")
    if summary:
        with open(summary, "a", encoding="utf-8") as handle:
            handle.write(markdown(report))
    if report["level"] == "warn":
        print(f"::warning::Supabase storage is {report['usage_percent']:.2f}% of the configured quota")
    elif report["level"] == "fail":
        print(f"::error::Supabase storage is {report['usage_percent']:.2f}% of the configured quota")
        raise SystemExit(2)


if __name__ == "__main__":
    main()
