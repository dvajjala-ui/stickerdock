from http.server import BaseHTTPRequestHandler
import logging
from api._shared import StudioError, authorized, options, read_json, send_json
from api._admin import dispatch
from tools.supabase_factory import SupabaseError


class handler(BaseHTTPRequestHandler):
    def do_OPTIONS(self):
        options(self)

    def do_POST(self):
        if not authorized(self.headers):
            send_json(self, 401, {"error": "Enter a valid owner access token"})
            return
        try:
            send_json(self, 200, dispatch(read_json(self)))
        except (StudioError, ValueError) as error:
            send_json(self, 400, {"error": str(error)})
        except SupabaseError:
            logging.exception("Admin provider operation failed")
            send_json(self, 503, {"error": "Admin storage is unavailable. Check configuration and apply the admin migration."})
        except Exception:
            logging.exception("Admin operation failed")
            send_json(self, 500, {"error": "Admin operation failed; inspect server logs"})
