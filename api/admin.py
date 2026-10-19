from http.server import BaseHTTPRequestHandler
import logging
from api._shared import StudioError, same_origin, options, read_json, send_json
from api._admin import dispatch
from tools.supabase_factory import SupabaseError
from tools.pinterest_connection import oauth_start


class handler(BaseHTTPRequestHandler):
    def do_OPTIONS(self):
        options(self)

    def do_POST(self):
        if not same_origin(self.headers):
            send_json(self, 403, {"error": "Use the StickerDock workspace for this request"})
            return
        try:
            payload = read_json(self)
            if payload.get("action") == "pinterest_connect":
                url, cookie = oauth_start()
                send_json(self, 200, {"url": url}, {"Set-Cookie": cookie})
                return
            send_json(self, 200, dispatch(payload))
        except (StudioError, ValueError) as error:
            send_json(self, 400, {"error": str(error)})
        except SupabaseError:
            logging.exception("Admin provider operation failed")
            send_json(self, 503, {"error": "Admin storage is unavailable. Check configuration and apply the admin migration."})
        except Exception:
            logging.exception("Admin operation failed")
            send_json(self, 500, {"error": "Admin operation failed; inspect server logs"})
