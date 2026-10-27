"""Verify admission races against a disposable local PostgreSQL Docker server.

Creates/removes only a uniquely named test database. The container must be an
isolated test server, not the production Supabase database. No provider calls.
"""
from concurrent.futures import ThreadPoolExecutor
import argparse
import json
from pathlib import Path
import re
import subprocess
from threading import Barrier
import uuid


def check(container):
    if not re.fullmatch(r"[a-zA-Z0-9][a-zA-Z0-9_.-]{0,127}", container):
        raise ValueError("Invalid disposable PostgreSQL container")
    prefix = ["docker", "--host=unix:///var/run/docker.sock", "exec", "-i", container,
              "psql", "-U", "postgres", "-X", "-qAt", "-v", "ON_ERROR_STOP=1"]
    database = "stickerdock_admission_test_" + uuid.uuid4().hex

    def sql(statement, name=database):
        return subprocess.run(prefix + ["-d", name], input=statement, text=True,
            capture_output=True, check=True, timeout=45).stdout.strip()

    def request(identity, retry=False, prompt="Reaction"):
        return json.loads(sql("select row_to_json(r) from public.admit_creator_request("
            f"'{identity}'::uuid, 'inbox/" + "a" * 64 + ".webp', "
            f"'{prompt}', {'true' if retry else 'false'}) r;"))

    def seed():
        sql("truncate public.creator_requests; insert into public.creator_requests(id,source_path,prompt) "
            "select md5(i::text)::uuid, 'inbox/" + "a" * 64 + ".webp','Reaction' from generate_series(1,24) i;")

    def concurrent(calls):
        barrier = Barrier(len(calls))
        def invoke(call):
            barrier.wait(timeout=15)
            return call()
        with ThreadPoolExecutor(max_workers=len(calls)) as pool:
            return list(pool.map(invoke, calls))

    sql(f'create database "{database}";', "postgres")
    try:
        # Roles are cluster-scoped; this server must be disposable. Existing roles
        # are left untouched. Tables, triggers and RPCs use the real migrations.
        sql("""do $$ begin
            if not exists(select 1 from pg_roles where rolname='anon') then create role anon; end if;
            if not exists(select 1 from pg_roles where rolname='authenticated') then create role authenticated; end if;
            if not exists(select 1 from pg_roles where rolname='service_role') then create role service_role bypassrls; end if;
            end $$;
            create function public.set_updated_at() returns trigger language plpgsql as $$
            begin new.updated_at=now(); return new; end $$;""")
        root = Path(__file__).resolve().parent.parent
        for filename in ("202610130001_creator_requests.sql", "202610130002_creator_request_leases.sql",
                         "202610270001_creator_atomic_admission.sql", "202610270001_creator_atomic_admission.sql"):
            sql((root / "supabase" / "migrations" / filename).read_text())

        seed()
        result = concurrent([lambda: request(str(uuid.uuid4())) for _ in range(12)])
        assert sum(row["accepted"] for row in result) == 1, result
        assert sql("select count(*) from public.creator_requests where status in ('queued','running')") == "25"

        seed()
        same_id = str(uuid.uuid4())
        result = concurrent([lambda: request(same_id) for _ in range(12)])
        assert all(row["accepted"] for row in result), result
        assert sum(row["reason"] == "created" for row in result) == 1, result
        assert request(same_id)["reason"] == "replay"  # Replay works at capacity.
        assert request(same_id, prompt="Different")["reason"] == "identity_conflict"
        assert sql("select count(*) from public.creator_requests") == "25"

        seed()
        retry_id = str(uuid.uuid4())
        sql("insert into public.creator_requests(id,source_path,prompt,status,attempts,result_path,error_message) "
            f"values('{retry_id}', 'inbox/" + "a" * 64 + ".webp','Old','failed',5,'inbox/" +
            "b" * 64 + ".webp','failed');")
        result = concurrent([lambda: request(retry_id, retry=True), lambda: request(str(uuid.uuid4()))])
        assert sum(row["accepted"] for row in result) == 1, result
        assert sql("select count(*) from public.creator_requests where status in ('queued','running')") == "25"
        if result[0]["accepted"]:
            assert sql(f"select attempts=0 and result_path is null and error_message is null from public.creator_requests where id='{retry_id}'") == "t"
            assert request(retry_id, retry=True)["reason"] == "changed"
        assert request(str(uuid.uuid4()), retry=True)["reason"] == "not_found"

        # Claiming does not free capacity. Cancellation does, without resetting
        # other rows or permitting an ordinary replay to requeue cancelled work.
        claimed = sql("select id from public.claim_creator_request('test-worker') limit 1")
        assert claimed
        assert request(str(uuid.uuid4()))["reason"] == "full"
        sql("update public.creator_requests set status='cancelled' where id=(select id from public.creator_requests where status='queued' limit 1)")
        assert request(str(uuid.uuid4()))["accepted"]

        for role in ("anon", "authenticated"):
            try:
                sql(f"set role {role}; select * from public.admit_creator_request('{uuid.uuid4()}'::uuid,'inbox/" + "a" * 64 + ".webp','Reaction');")
            except subprocess.CalledProcessError as error:
                assert "permission denied" in error.stderr
            else:
                raise AssertionError("Untrusted client role can admit work")
        assert sql("set role service_role; select count(*) from public.creator_requests where status in ('queued','running');") == "25"
        allowed_role_result = json.loads(sql("set role service_role; select row_to_json(r) from public.admit_creator_request('" +
            str(uuid.uuid4()) + "'::uuid,'inbox/" + "a" * 64 + ".webp','Reaction') r;"))
        assert allowed_role_result["reason"] == "full"  # RPC executes; quota still enforced.
        print(json.dumps({"passed": True, "database": "isolated disposable database",
            "checks": ["12 concurrent distinct submissions at 24", "12 same-ID replays at capacity",
                "identity conflict", "retry racing new admission", "claim/cancellation capacity",
                "attempt/result reset", "migration replay", "service-role grants"]}))
    finally:
        sql(f'drop database "{database}" with (force);', "postgres")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--docker-container", required=True, help="Disposable PostgreSQL test container only")
    check(parser.parse_args().docker_container)
