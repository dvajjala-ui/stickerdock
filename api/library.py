from http.server import BaseHTTPRequestHandler

from api._shared import StudioError, same_origin, fetch_inbox, list_inbox, preview_inbox, options, read_json, send_json


class handler(BaseHTTPRequestHandler):
    def do_OPTIONS(self):
        options(self)

    def do_POST(self):
        if not same_origin(self.headers):
            send_json(self, 403, {"error": "Use the StickerDock workspace for this request"})
            return
        try:
            request = read_json(self)
            if request.get("action") == "list":
                send_json(self, 200, list_inbox(request.get("offset", 0), request.get("limit", 100)))
            elif request.get("action") == "fetch":
                send_json(self, 200, {"image": fetch_inbox(str(request.get("path", "")))})
            elif request.get("action") == "preview":
                send_json(self, 200, preview_inbox(str(request.get("path", ""))))
            else:
                raise StudioError("Unknown library action")
        except StudioError as error:
            send_json(self, 400, {"error": str(error)})
        except Exception:
            send_json(self, 500, {"error": "Library request failed"})
