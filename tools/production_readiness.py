"""Read-only database/catalog readiness checks; never exposes provider credentials."""
from __future__ import annotations

from concurrent.futures import ThreadPoolExecutor
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
    return {"admin_ready": set(TABLES).issubset(available),
            "worker_ready": {"factory_runs", "sticker_packs", "creator_requests"}.issubset(available),
            "published_packs": published, "catalog_base": public_catalog_base(client.url), "checks": checks}


if __name__ == "__main__":
    result = readiness()
    print(json.dumps(result, indent=2))
    if not result["admin_ready"]:
        raise SystemExit(2)
