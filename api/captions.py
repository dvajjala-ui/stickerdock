from http.server import BaseHTTPRequestHandler

from api._shared import (
    StudioError,
    authorized,
    creator_request_status,
    options,
    queue_creator_request,
    read_json,
    send_json,
)


class handler(BaseHTTPRequestHandler):
    def do_OPTIONS(self):
        options(self)

    def do_POST(self):
        if not authorized(self.headers):
            send_json(self, 401, {"error": "Creator access token is invalid"})
            return
        try:
            payload = read_json(self)
            action = str(payload.get("action", "queue"))
            if action == "queue":
                result = queue_creator_request(payload.get("prompt"), payload.get("source_path"))
                send_json(self, 202, result)
            elif action == "status":
                send_json(self, 200, creator_request_status(payload.get("id")))
            else:
                send_json(self, 400, {"error": "Unsupported creator request action"})
        except StudioError as error:
            send_json(self, 400, {"error": str(error)})
        except Exception:
            send_json(self, 503, {"error": "Creator request service is unavailable"})
