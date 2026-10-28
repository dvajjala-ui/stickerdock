"""Local integration fixture: production intake handler, disposable SQLite/blob storage.

Never connects to Supabase or an AI provider. Used by HandNavigate's cross-repo check.
"""
import argparse
import json
from pathlib import Path
import sqlite3
import sys
from http.server import ThreadingHTTPServer

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from api import ingest, _media_intake


class Store:
    def __init__(self, folder):
        self.folder = folder
        folder.mkdir(parents=True, exist_ok=True)
        with sqlite3.connect(folder / "intake.sqlite3") as db:
            db.execute("create table if not exists items (id text primary key, document text not null)")
    def find(self, table, column, value):
        with sqlite3.connect(self.folder / "intake.sqlite3") as db:
            row = db.execute("select document from items where id=?", (value,)).fetchone()
        return json.loads(row[0]) if row else None
    def upload(self, bucket, path, data, content_type):
        file = self.folder / path
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_bytes(data)
    def download(self, bucket, path):
        return (self.folder / path).read_bytes()
    def _request(self, method, path, data, headers):
        value = json.loads(data)[0]
        with sqlite3.connect(self.folder / "intake.sqlite3") as db:
            cursor = db.execute("insert or ignore into items values (?,?)", (value["id"], json.dumps(value)))
            inserted = cursor.rowcount
        return json.dumps([value] if inserted else []).encode()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--folder", type=Path, required=True)
    args = parser.parse_args()
    store = Store(args.folder)
    ingest.dispatch = lambda payload: _media_intake.dispatch(payload, store)
    server = ThreadingHTTPServer(("127.0.0.1", 0), ingest.handler)
    print(json.dumps({"port": server.server_address[1], "storage": "disposable SQLite and local blobs"}), flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
