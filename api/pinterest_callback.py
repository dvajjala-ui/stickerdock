from http.server import BaseHTTPRequestHandler
from urllib.parse import urlsplit
from tools.pinterest_connection import COOKIE, oauth_complete
from tools.supabase_factory import SupabaseFactory


class handler(BaseHTTPRequestHandler):
    def do_GET(self):
        try:
            oauth_complete(urlsplit(self.path).query, self.headers.get("Cookie", ""), SupabaseFactory())
            destination = "/admin?pinterest=connected"
        except Exception:
            destination = "/admin?pinterest=failed"
        self.send_response(303)
        self.send_header("Location", destination)
        self.send_header("Cache-Control", "no-store")
        self.send_header("Set-Cookie", f"{COOKIE}=; Secure; HttpOnly; SameSite=Lax; Path=/; Max-Age=0")
        self.end_headers()

    def log_message(self, *args):
        pass  # OAuth codes and state must never enter application request logs.
