"""Private native AI API. Never use the shared Creator Studio queue here."""
from http.server import BaseHTTPRequestHandler
import logging

from api._cloud import authenticate
from api._personal_ai import PersonalAiConflict, PersonalAiLimit, dispatch
from api._shared import StudioError, read_json, send_json
from tools.supabase_factory import SupabaseError


class handler(BaseHTTPRequestHandler):
    def do_POST(self):
        try:
            payload = read_json(self)
            owner = authenticate(self.headers)
            result = dispatch(payload, owner)
            send_json(self, 202 if payload.get("action") == "submit" else 200, result)
        except PermissionError as error:
            send_json(self, 401, {"error": str(error)})
        except PersonalAiConflict as error:
            send_json(self, 409, {"error": str(error)})
        except PersonalAiLimit as error:
            send_json(self, 429, {"error": str(error)})
        except (StudioError, ValueError) as error:
            send_json(self, 400, {"error": str(error)})
        except Exception:
            logging.exception("Private AI request failed")
            send_json(self, 503, {"error": "AI service unavailable. Your phone stickers are safe; retry later."})
