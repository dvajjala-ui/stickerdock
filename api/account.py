from http.server import BaseHTTPRequestHandler
from api._cloud import auth_call
from api._shared import StudioError, read_json, send_json, safe_text, same_origin
import re


class handler(BaseHTTPRequestHandler):
    def do_POST(self):
        if not same_origin(self.headers):
            send_json(self, 403, {"error": "Use the StickerDock account page"}); return
        try:
            value = read_json(self)
            if value.get("action") == "recover":
                email = safe_text(value.get("email"), "email", 254, required=True)
                if not re.fullmatch(r"[^\s@]+@[^\s@]+\.[^\s@]+", email):
                    raise StudioError("Enter a valid email address")
                auth_call("recover", {"email": email})
                send_json(self, 200, {"message": "If this account exists, check its inbox for a password reset link."})
            elif value.get("action") == "password":
                token = str(self.headers.get("Authorization", ""))
                if not token.startswith("Bearer ") or len(token) > 8192:
                    raise StudioError("Open your password reset email link first")
                password = value.get("password", "")
                if not isinstance(password, str) or not 10 <= len(password) <= 128:
                    raise StudioError("Use a password of 10–128 characters")
                user = auth_call("user", token=token[7:])
                if not user.get("email_confirmed_at"):
                    raise StudioError("Confirm your email first")
                auth_call("user", {"password": password}, token=token[7:], method="PUT")
                send_json(self, 200, {"message": "Password updated. Sign in again on your phone."})
            else:
                raise StudioError("Unknown account action")
        except StudioError as error:
            send_json(self, 400, {"error": str(error)})
        except Exception:
            send_json(self, 503, {"error": "Account service unavailable. Please retry."})
