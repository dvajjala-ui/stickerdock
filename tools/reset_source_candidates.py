"""One-time, fingerprint-bound reset of the private HandNavigate source inbox.

This never touches personal phone libraries, published packs, Factory-generated
artwork, or legacy inbox objects. Run without --apply first and record its SHA.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from collections import Counter

import psycopg

from tools.supabase_factory import PRIVATE_BUCKET, SupabaseFactory

PROJECT = "qqicsllktmcmvmzplsxl"
PREFIX = "handnavigate/candidates/"


def connect():
    password = os.environ["SUPABASE_DB_PASSWORD"]
    hosts = [os.environ["SUPABASE_DB_HOST"]] if os.environ.get("SUPABASE_DB_HOST") else [
        "db." + PROJECT + ".supabase.co",
        *[f"aws-{number}-{region}.pooler.supabase.com"
          for region in ("ap-south-1", "ap-southeast-1", "us-east-1", "eu-central-1", "us-west-1")
          for number in (0, 1)],
    ]
    for host in hosts:
        if host != "db." + PROJECT + ".supabase.co" and not host.endswith(".pooler.supabase.com"):
            raise ValueError("Unexpected Supabase database host")
        try:
            return psycopg.connect(
                host=host, port=5432, dbname="postgres",
                user="postgres" if host.startswith("db.") else "postgres." + PROJECT,
                password=password, sslmode="require", connect_timeout=4,
                options="-c statement_timeout=30000",
            )
        except psycopg.OperationalError:
            continue  # Never print database credentials or server error bodies.
    raise RuntimeError("Supabase database unavailable")


def snapshot(conn):
    with conn.cursor() as cur:
        cur.execute("""select id::text, platform, original_sha256, stored_sha256,
                             storage_path, rights_status, generation_eligible, archived
                       from public.media_intake_items order by id""")
        return [dict(zip(("id", "platform", "original_sha256", "stored_sha256",
                          "storage_path", "rights_status", "generation_eligible", "archived"), row))
                for row in cur.fetchall()]


def fingerprint(items):
    stable = [(r["id"], r["original_sha256"], r["stored_sha256"], r["storage_path"])
              for r in items]
    return hashlib.sha256(json.dumps(stable, separators=(",", ":")).encode()).hexdigest()


def preflight(conn, client, items):
    if not items:
        raise ValueError("No source candidates to reset")
    paths = [r["storage_path"] for r in items]
    if (len(paths) != len(set(paths)) or any(not p.startswith(PREFIX) or
            not p.endswith(".webp") for p in paths)):
        raise ValueError("Candidate path is shared or outside the private candidate prefix")
    if any(r["rights_status"] == "APPROVED" or r["generation_eligible"] for r in items):
        raise ValueError("An approved or generation-ready source must be reviewed separately")
    with conn.cursor() as cur:
        cur.execute("select count(*) from public.collection_plans where daily and not archived")
        if cur.fetchone()[0]:
            raise ValueError("Archive active daily plans before resetting sources")
        # Guard historical pack/request references, including JSON metadata fields.
        for table in ("admin_packs", "creator_requests"):
            cur.execute(f"select row_to_json(t)::text from public.{table} t")
            text = "\n".join(row[0] for row in cur.fetchall())
            for item in items:
                if item["id"] in text or item["storage_path"] in text:
                    raise ValueError(f"A source is referenced by {table}")
    objects = {}
    for offset in range(0, 10000, 100):
        page = client.list_objects(PRIVATE_BUCKET, PREFIX.rstrip("/"), 100, offset=offset)
        for entry in page:
            name = entry.get("name", "")
            path = name if name.startswith(PREFIX) else PREFIX + name
            objects[path] = int((entry.get("metadata") or {}).get("size") or 0)
        if len(page) < 100:
            break
    missing = set(paths) - objects.keys()
    if missing:
        raise ValueError(f"{len(missing)} candidate objects are already missing")
    return sum(objects[path] for path in paths)


def run(apply, expected_count, expected_sha):
    client = SupabaseFactory()
    with connect() as conn:
        items = snapshot(conn)
        size = preflight(conn, client, items)
        sha = fingerprint(items)
        result = {"count": len(items), "bytes": size, "sha256": sha,
                  "platforms": dict(Counter(r["platform"] for r in items)),
                  "scope": "private HandNavigate candidate objects and media_intake_items only"}
        print(json.dumps(result, sort_keys=True), flush=True)
        if not apply:
            return result
        if expected_count != len(items) or expected_sha != sha:
            raise ValueError("Candidate count or fingerprint changed; rerun the dry run")
        run_id = os.environ.get("GITHUB_RUN_ID", "")
        if not run_id.isdecimal():
            raise ValueError("Apply must run from a reviewed GitHub Actions run")
        manifest_path = f"maintenance/source-reset-{run_id}.json"
        manifest = json.dumps({"run_id": run_id, "items": items}, sort_keys=True).encode()
        if client.download(PRIVATE_BUCKET, manifest_path, missing_ok=True) is not None:
            raise ValueError("This reset run has already written a manifest")
        client.upload(PRIVATE_BUCKET, manifest_path, manifest, "application/json")
        if client.download(PRIVATE_BUCKET, manifest_path) != manifest:
            raise RuntimeError("Private reset manifest did not verify")
        # The transaction prevents a concurrent new source from entering the
        # same snapshot between validation and row deletion.
        with conn.cursor() as cur:
            cur.execute("lock table public.media_intake_items in share row exclusive mode")
            if fingerprint(snapshot(conn)) != sha:
                raise ValueError("Source rows changed during reset")
            cur.execute("delete from public.media_intake_items returning id")
            if cur.rowcount != expected_count:
                raise RuntimeError("Database deletion count changed")
        conn.commit()
    paths = [r["storage_path"] for r in items]
    for start in range(0, len(paths), 100):
        client.remove(PRIVATE_BUCKET, paths[start:start + 100])
    remaining = []
    for offset in range(0, 10000, 100):
        page = client.list_objects(PRIVATE_BUCKET, PREFIX.rstrip("/"), 100, offset=offset)
        remaining.extend(entry.get("name", "") for entry in page)
        if len(page) < 100:
            break
    remaining = [name for name in remaining if (name if name.startswith(PREFIX) else PREFIX + name) in paths]
    if remaining:
        raise RuntimeError(f"{len(remaining)} candidate objects remain after database reset; see private manifest")
    print(json.dumps({"deleted_rows": len(items), "deleted_objects": len(paths),
                      "freed_bytes": size, "manifest": manifest_path}), flush=True)
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--expected-count", type=int)
    parser.add_argument("--expected-sha")
    args = parser.parse_args()
    run(args.apply, args.expected_count, args.expected_sha)
