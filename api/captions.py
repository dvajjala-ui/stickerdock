from http.server import BaseHTTPRequestHandler

from api._shared import authorized, options, send_json


class handler(BaseHTTPRequestHandler):
    def do_OPTIONS(self):
        options(self)

    def do_POST(self):
        if not authorized(self.headers):
            send_json(self, 401, {"error": "Creator access token is invalid"})
            return
        send_json(self, 503, {"error": "AI meme captions are coming soon"})
