"""Cheap due check before installing rendering tools or downloading Factory state."""
from datetime import datetime, timezone
import os
import time

from supabase_factory import SupabaseFactory, SupabaseError


def due(client, target=40, budget=16, now=None):
    now = time.time() if now is None else now
    try:
        settings = client.find("factory_settings", "id", "default")
    except SupabaseError as error:
        if "HTTP 404" not in str(error):
            raise
        settings = None
    if settings:
        if settings.get("enabled") is not True:
            return False, "Paused by owner"
        target = settings["daily_target"]
    day = datetime.fromtimestamp(now, timezone.utc).date().isoformat()
    if settings and settings.get("auto_migrate_completed") is True:
        for previous in client.list_rows("factory_runs", "day.desc", 100):
            metrics = previous.get("groq_metrics") or {}
            if (previous.get("day", "") < day
                    and (metrics.get("postprocess_complete") is False or metrics.get("draft_delivery_complete") is False)
                    and metrics.get("delivery_recovery", {}).get("status") != "blocked"):
                return True, "Recover previous-day batch delivery without a new AI request"
    run = client.find("factory_runs", "day", day)
    if not run:
        return True, "First batch of this UTC day is due"
    metrics = run.get("groq_metrics") or {}
    if settings and settings.get("auto_migrate_completed") is True and metrics.get("draft_delivery_complete") is False:
        return True, "Deliver saved batches to private review without a new AI request"
    if metrics.get("postprocess_complete") is False:
        return True, "Resume saved batch delivery without a new AI request"
    if metrics.get("pending_render"):
        return True, "Resume saved artwork without a new caption request"
    if run.get("generated", 0) >= target:
        if run.get("status") == "complete" and metrics.get("postprocess_complete", True):
            return False, "Daily target already reached"
        return True, "Resume completed output storage/migration without a new AI request"
    if run.get("attempts", 0) >= budget:
        return False, "Daily request budget reached"
    recovery = metrics.get("recovery") or {}
    retry_at = max(metrics.get("next_request_at", 0), recovery.get("retry_at", 0))
    if now < retry_at:
        return False, "Waiting for saved batch/provider cooldown"
    return True, "Next bounded batch or recovery attempt is due"


if __name__ == "__main__":
    should_run, reason = due(SupabaseFactory(), int(os.environ.get("FACTORY_DAILY_TARGET", "40")))
    output = os.environ.get("GITHUB_OUTPUT")
    if output:
        with open(output, "a", encoding="utf-8") as handle:
            handle.write(f"run={'true' if should_run else 'false'}\n")
    print(reason)
