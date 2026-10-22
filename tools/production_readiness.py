"""Read-only database/catalog readiness checks; never exposes provider credentials."""
from __future__ import annotations

from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
import json
import os

try:
    from .supabase_factory import SupabaseFactory, SupabaseError, PUBLIC_BUCKET, public_catalog_base
except ImportError:
    from supabase_factory import SupabaseFactory, SupabaseError, PUBLIC_BUCKET, public_catalog_base

TABLES = {
    "factory_runs": "day,status,generated,target",
    "sticker_packs": "id,status,sha256,private_path",
    "creator_requests": "id,status,result_path,attempts",
    "admin_packs": "id,version,references,private_path",
    "factory_settings": "id,batch_size,rating,content_categories",
    "content_briefs": "day,settings_hash,topic_plans",
    "admin_events": "id,action",
    "catalog_leases": "id,owner,expires_at",
}


def latest_row(client, table, columns, order, filters=""):
    path = f"/rest/v1/{table}?select={columns}&order={order}&limit=1{filters}"
    rows = json.loads(client._request("GET", path) or b"[]")
    if not isinstance(rows, list) or any(not isinstance(row, dict) for row in rows):
        raise ValueError("Invalid delivery response")
    return rows[0] if rows else None


def age_days(value, now):
    if not value:
        return None
    try:
        stamp = datetime.fromisoformat(str(value).replace("Z", "+00:00"))
        if stamp.tzinfo is None:
            return None
        return max(0, (now - stamp.astimezone(timezone.utc)).total_seconds() / 86400)
    except (TypeError, ValueError):
        return None


def delivery_checks(client, published, now):
    """Report observable pipeline stages, separately from schema readiness."""
    checks = []
    freshness = {"generation": False, "review_draft": False, "catalog": False}
    try:
        run = latest_row(client, "factory_runs", "day,status,generated,target,updated_at", "day.desc")
        today = now.date().isoformat()
        if run:
            day, generated = str(run.get("day", "")), int(run.get("generated") or 0)
            freshness["generation"] = day == today and generated > 0
            status = "ready" if freshness["generation"] else "pending"
            message = (f"Latest UTC day {day}: {generated}/{run.get('target', '?')} saved, "
                       f"status {run.get('status', 'unknown')}; updated {run.get('updated_at', 'unknown')}.")
        else:
            status, message = "pending", "No Factory run recorded yet."
        checks.append({"name": "Recent generation", "status": status, "message": message})
    except Exception:
        checks.append({"name": "Recent generation", "status": "blocked", "message": "Factory activity could not be read."})
    try:
        draft = latest_row(client, "admin_packs", "id,created_at", "created_at.desc",
                           "&status=eq.draft&creator=eq.StickerDock%20AI%20Factory")
        age = age_days(draft.get("created_at"), now) if draft else None
        freshness["review_draft"] = age is not None and age <= 2
        checks.append({"name": "Review draft delivery",
                       "status": "ready" if freshness["review_draft"] else "pending",
                       "message": (f"Newest private draft created {draft['created_at']}." if draft else
                                   "No private review draft is available.")})
    except Exception:
        checks.append({"name": "Review draft delivery", "status": "blocked", "message": "Review drafts could not be read."})
    try:
        latest = latest_row(client, "admin_packs", "id,updated_at", "updated_at.desc", "&status=eq.published")
        age = age_days(latest.get("updated_at"), now) if latest else None
        freshness["catalog"] = published > 0 and age is not None and age <= 7
        checks.append({"name": "Catalog freshness", "status": "ready" if freshness["catalog"] else "pending",
                       "message": (f"Newest managed publication updated {latest['updated_at']}; {published} public packs."
                                   if latest else f"{published} public packs; no recent managed publication recorded.")})
    except Exception:
        checks.append({"name": "Catalog freshness", "status": "blocked", "message": "Publication age could not be read."})
    return checks, freshness


def readiness(client=None):
    if client is None:
        if not os.environ.get("SUPABASE_URL") or not os.environ.get("SUPABASE_SERVICE_ROLE_KEY"):
            return {"admin_ready": False, "worker_ready": False, "published_packs": 0,
                    "checks": [{"name": "Server configuration", "status": "blocked",
                                "message": "Configure SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY on the server."}]}
        client = SupabaseFactory()

    def probe(item):
        table, columns = item
        try:
            rows = json.loads(client._request("GET", f"/rest/v1/{table}?select={columns}&limit=1") or b"[]")
            if not isinstance(rows, list):
                raise ValueError("Invalid table response")
            return {"name": table, "status": "ready", "message": "Table and required columns are available."}
        except SupabaseError as error:
            missing = "HTTP 404" in str(error) or "HTTP 400" in str(error)
            return {"name": table, "status": "blocked", "message":
                    "Apply the pending database migration or refresh the schema cache." if missing else
                    "Database access failed. Check server configuration and provider availability."}
        except Exception:
            return {"name": table, "status": "blocked", "message": "Database check could not complete."}

    with ThreadPoolExecutor(max_workers=4) as pool:
        checks = list(pool.map(probe, TABLES.items()))
    available = {item["name"] for item in checks if item["status"] == "ready"}
    published = 0
    try:
        raw = client.download(PUBLIC_BUCKET, "catalog/packs.json", missing_ok=True)
        catalog = json.loads(raw) if raw else None
        if catalog is not None and (catalog.get("version") != 1 or not isinstance(catalog.get("packs"), list)):
            raise ValueError("Invalid catalog")
        published = len(catalog["packs"]) if catalog else 0
        checks.append({"name": "Published catalog", "status": "ready" if published else "pending",
                       "message": f"{published} published packs available." if published else
                       "Review and publish a GENERAL pack to enable delivery to the Android app."})
    except Exception:
        checks.append({"name": "Published catalog", "status": "blocked", "message": "Public catalog could not be read."})
    delivery, freshness = delivery_checks(client, published, datetime.now(timezone.utc))
    checks.extend(delivery)
    return {"admin_ready": set(TABLES).issubset(available),
            "worker_ready": {"factory_runs", "sticker_packs", "creator_requests"}.issubset(available),
            "delivery_fresh": all(freshness.values()), "delivery_stages": freshness,
            "published_packs": published, "catalog_base": public_catalog_base(client.url), "checks": checks}


if __name__ == "__main__":
    result = readiness()
    print(json.dumps(result, indent=2))
    if not result["admin_ready"]:
        raise SystemExit(2)
