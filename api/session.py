"""Owner sign-in with secure HTTP-only cookies; no manually copied API token."""
from http.server import BaseHTTPRequestHandler
import os
from http.cookies import SimpleCookie
from api._cloud import auth_call, auth_action
from api._shared import StudioError, read_json, send_json


def owner_email():
    return os.environ.get("STUDIO_OWNER_EMAIL", "dvajjala@gmail.com").strip().lower()


def owner_user(user):
    return bool(user.get("email_confirmed_at") and str(user.get("email", "")).lower() == owner_email())


def cookies(headers):
    try:
        result = SimpleCookie(); result.load(headers.get("Cookie", ""))
        return {key: value.value for key, value in result.items()}
    except Exception:
        return {}


def same_origin(headers):
    origin = headers.get("Origin", "")
    return not origin or origin == "https://" + headers.get("Host", "")


def cookie(name, value, age):
    return f"{name}={value}; Path=/api; Max-Age={age}; HttpOnly; Secure; SameSite=Strict"


def reply(handler, result, session=None, clear=False):
    handler.send_response(200)
    handler.send_header("Content-Type", "application/json")
    handler.send_header("Cache-Control", "no-store")
    if session:
        handler.send_header("Set-Cookie", cookie("sd_owner", session["access_token"], session.get("expires_in", 3600)))
        handler.send_header("Set-Cookie", cookie("sd_refresh", session["refresh_token"], 30 * 86400))
    if clear:
        handler.send_header("Set-Cookie", cookie("sd_owner", "", 0))
        handler.send_header("Set-Cookie", cookie("sd_refresh", "", 0))
    handler.end_headers()
    import json
    handler.wfile.write(json.dumps(result).encode())


class handler(BaseHTTPRequestHandler):
    def do_POST(self):
        if not same_origin(self.headers):
            send_json(self, 403, {"error": "Use the StickerDock website to sign in"}); return
        try:
            payload = read_json(self)
            action = payload.get("action")
            if action == "logout":
                reply(self, {"signed_in": False}, clear=True); return
            if action in {"signup", "resend", "login"}:
                if str(payload.get("email", "")).strip().lower() != owner_email():
                    raise StudioError("Use the configured owner's email address")
                result = auth_action(payload)
                if not result.get("access_token"):
                    reply(self, {"signed_in": False, "message": result.get("message", "Confirm your email, then sign in")}); return
            elif action == "status":
                values = cookies(self.headers)
                if values.get("sd_owner"):
                    try:
                        user = auth_call("user", token=values["sd_owner"])
                        if owner_user(user):
                            reply(self, {"signed_in": True, "email": user["email"]}); return
                    except StudioError:
                        pass
                if not values.get("sd_refresh"):
                    reply(self, {"signed_in": False}); return
                result = auth_call("token?grant_type=refresh_token", {"refresh_token": values["sd_refresh"]})
            else:
                raise StudioError("Unknown sign-in action")
            if not owner_user(result.get("user", {})):
                raise StudioError("Confirm the owner email before accessing Admin")
            reply(self, {"signed_in": True, "email": result["user"]["email"]}, session=result)
        except StudioError as error:
            send_json(self, 401, {"error": str(error)})
        except Exception:
            send_json(self, 503, {"error": "Sign-in is temporarily unavailable. Please retry."})
