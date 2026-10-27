"""Loopback-only admin/Studio preview, with optional real APIs from environment."""
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import argparse
import sys
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from api.admin import handler as Admin
from api.library import handler as Library
from api.upload import handler as Upload
from api.captions import handler as Captions
from api.pinterest_callback import handler as PinterestCallback
from api.ingest import handler as Ingest


class Preview(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(ROOT), **kwargs)

    def do_GET(self):
        path = urlsplit(self.path).path
        if path == "/api/pinterest_callback":
            PinterestCallback.do_GET(self)
            return
        if path in ("/", "/admin", "/admin/"):
            self.path = "/admin/index.html"
        elif path in ("/about", "/support", "/privacy"):
            self.path = {"/about":"/site/about.html", "/support":"/site/support.html", "/privacy":"/studio/privacy.html"}[path]
        elif not (path.startswith(("/admin/", "/studio/", "/site/")) and ".." not in path and "%" not in path):
            self.send_error(404)
            return
        super().do_GET()

    def do_POST(self):
        endpoint = {"/api/ingest": Ingest, "/api/admin": Admin, "/api/library": Library, "/api/upload": Upload, "/api/captions": Captions}.get(urlsplit(self.path).path)
        if endpoint:
            endpoint.do_POST(self)
        else:
            self.send_error(404)

    def end_headers(self):
        self.send_header("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data: blob:; connect-src 'self'; frame-ancestors 'none'; object-src 'none'")
        self.send_header("X-Content-Type-Options", "nosniff")
        super().end_headers()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=4173)
    args = parser.parse_args()
    print(f"Admin preview: http://127.0.0.1:{args.port}/admin", flush=True)
    ThreadingHTTPServer(("127.0.0.1", args.port), Preview).serve_forever()
