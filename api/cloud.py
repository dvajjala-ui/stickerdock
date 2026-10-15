from http.server import BaseHTTPRequestHandler
import logging
from api._shared import StudioError, read_json, send_json
from api._cloud import authenticate, auth_action, dispatch
from tools.supabase_factory import SupabaseError


class handler(BaseHTTPRequestHandler):
    def do_POST(self):
        try:
            payload = read_json(self)
            if payload.get("action") in {"login", "signup", "refresh", "resend"}:
                send_json(self, 200, auth_action(payload))
            else:
                owner = authenticate(self.headers)
                send_json(self, 200, dispatch(payload, owner))
        except PermissionError as error:
            send_json(self, 401, {"error": str(error)})
        except (StudioError, ValueError) as error:
            send_json(self, 400, {"error": str(error)})
        except SupabaseError:
            logging.exception("Cloud database operation failed")
            send_json(self, 503, {"error": "Cloud storage unavailable; your phone library is safe. Retry later."})
        except Exception:
            logging.exception("Cloud operation failed")
            send_json(self, 503, {"error": "Cloud sync unavailable; your phone library is safe. Retry later."})
