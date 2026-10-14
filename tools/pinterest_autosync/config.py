from __future__ import annotations

import json
from pathlib import Path
from zoneinfo import ZoneInfo


def load_config(path: Path) -> dict:
    return validate_config(json.loads(path.read_text(encoding="utf-8")))


def validate_config(config: dict) -> dict:
    limits = config["limits"]
    if config.get("source_mode") != "mixed":
        raise ValueError("source_mode must be 'mixed'")
    media_types = config.get("media_types")
    if not isinstance(media_types, list) or not media_types or not set(media_types) <= {"image", "video"}:
        raise ValueError("media_types must contain image and/or video")
    ZoneInfo(config["timezone"])
    for key in ("daily_downloads", "max_downloads_per_scan",
                "official_api_requests_per_rolling_24h", "max_file_mb",
                "max_attempts"):
        if not 1 <= int(limits[key]):
            raise ValueError(f"limits.{key} must be positive")
    if not 0 < float(limits["max_total_storage_gb"]) <= 10:
        raise ValueError("Storage cap must be positive and at most 10 GB")
    if int(limits["daily_downloads"]) > 100:
        raise ValueError("Daily download target must not exceed 100")
    if int(limits["max_downloads_per_scan"]) > 30:
        raise ValueError("Per-scan download target must not exceed 30")
    if not 1 <= int(limits.get("max_attempts_per_scan", 60)) <= 60:
        raise ValueError("Per-scan attempt cap must be 1..60")
    if not 60 <= float(limits.get("scan_timeout_seconds", 900)) <= 1500:
        raise ValueError("Scan timeout must be 60..1500 seconds")
    if int(limits["official_api_requests_per_rolling_24h"]) > 1000:
        raise ValueError("Official API cap must not exceed 1000 requests per 24h")
    if not (0 < float(config["schedule"]["interval_hours"]) <= 24):
        raise ValueError("schedule.interval_hours must be between 0 and 24")
    official = config["official"]
    if not 1 <= int(official["page_size"]) <= 250:
        raise ValueError("official.page_size must be 1..250")
    if not 1 <= int(official["max_pages_per_board"]) <= 30:
        raise ValueError("official.max_pages_per_board must be 1..30")
    if not 1 <= int(config["unofficial"]["results_per_topic"]) <= 500:
        raise ValueError("unofficial.results_per_topic must be 1..500")
    resolution = config["unofficial"].get("min_resolution", [256, 256])
    if (not isinstance(resolution, list) or len(resolution) != 2 or
            any(not isinstance(value, int) or not 0 <= value <= 4096 for value in resolution)):
        raise ValueError("unofficial.min_resolution must contain two integers from 0..4096")
    if (float(limits["delay_between_downloads_seconds"]) < 0 or
            float(config["unofficial"]["request_delay_seconds"]) < 0.4):
        raise ValueError("Download delay must be >= 0; unofficial delay must be >= 0.4 seconds")
    return config
