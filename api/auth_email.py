"""Supabase Send Email Hook. Not a browser-facing mail endpoint."""
from http.server import BaseHTTPRequestHandler
import json
import os
from api._auth_email import MAX_BODY, verify_webhook, deliver


class handler(BaseHTTPRequestHandler):
    def reply(self, status, body):
        encoded = json.dumps(body).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

    def do_POST(self):
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if not 1 <= length <= MAX_BODY:
                self.reply(413, {"error": {"http_code": 413, "message": "Invalid request size"}})
                return
            body = self.rfile.read(length)
            identifier = verify_webhook(body, self.headers, os.environ.get("SUPABASE_SEND_EMAIL_HOOK_SECRET", ""))
        except RuntimeError:
            self.reply(503, {"error": {"http_code": 503, "message": "Account email is not configured"}})
            return
        except (ValueError, TypeError):
            self.reply(401, {"error": {"http_code": 401, "message": "Invalid email hook request"}})
            return
        try:
            deliver(json.loads(body), identifier)
            self.reply(200, {})
        except Exception:
            # Do not echo recipient addresses, reset tokens or provider replies.
            self.reply(503, {"error": {"http_code": 503, "message": "Account email delivery unavailable. Retry later."}})
