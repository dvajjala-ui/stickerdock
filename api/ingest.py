from http.server import BaseHTTPRequestHandler
import logging
from api._shared import StudioError, read_json, send_json
from api._media_intake import authorized, dispatch
from tools.supabase_factory import SupabaseError


class handler(BaseHTTPRequestHandler):
    def do_POST(self):
        if not authorized(self.headers):
            send_json(self, 401, {"error": "HandNavigate intake authentication required"})
            return
        try:
            send_json(self, 200, dispatch(read_json(self)))
        except (StudioError, ValueError, TypeError):
            send_json(self, 400, {"error": "Invalid or conflicting media intake request"})
        except SupabaseError:
            send_json(self, 503, {"error": "Media intake is unavailable; check configuration and its migration"})
        except Exception:
            logging.exception("Media intake failed")
            send_json(self, 500, {"error": "Media intake failed"})
