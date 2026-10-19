from http.server import BaseHTTPRequestHandler

from api._shared import StudioError, same_origin, options, read_json, send_json, upload_user_image


class handler(BaseHTTPRequestHandler):
    def do_OPTIONS(self):
        options(self)

    def do_POST(self):
        if not same_origin(self.headers):
            send_json(self, 403, {"error": "Use the StickerDock workspace for this request"})
            return
        try:
            request = read_json(self)
            result = upload_user_image(request.get("image", ""), request.get("title", ""),
                                       request.get("source_path"))
            send_json(self, 200, result)
        except StudioError as error:
            send_json(self, 400, {"error": str(error)})
        except Exception:
            send_json(self, 500, {"error": "Inbox upload failed"})
