import unittest
from unittest.mock import patch
from api import session
from api import _shared


class OwnerSessionTest(unittest.TestCase):
    def test_default_owner_matches_authorized_gmail_account(self):
        with patch.dict("os.environ", {}, clear=True):
            self.assertEqual(session.owner_email(), "dvajjala@gmail.com")
            self.assertTrue(session.owner_user({"email": "dvajjala@gmail.com", "email_confirmed_at": "now"}))
            self.assertFalse(session.owner_user({"email": "dvjjala@gmail.com", "email_confirmed_at": "now"}))

    def test_only_confirmed_owner_email_is_allowed(self):
        with patch.dict("os.environ", {"STUDIO_OWNER_EMAIL": "owner@example.com"}):
            self.assertTrue(session.owner_user({"email": "OWNER@example.com", "email_confirmed_at": "now"}))
            self.assertFalse(session.owner_user({"email": "other@example.com", "email_confirmed_at": "now"}))
            self.assertFalse(session.owner_user({"email": "owner@example.com"}))
    def test_http_only_secure_scoped_session_cookie(self):
        value = session.cookie("sd_owner", "opaque", 3600)
        for required in ("HttpOnly", "Secure", "SameSite=Strict", "Path=/api", "Max-Age=3600"):
            self.assertIn(required, value)
    def test_browser_cookie_session_and_foreign_origin(self):
        user = {"email": "owner@example.com", "email_confirmed_at": "now"}
        headers = {"Host": "stickerdock.vercel.app", "Origin": "https://stickerdock.vercel.app", "Cookie": "sd_owner=opaque"}
        with patch.dict("os.environ", {"STUDIO_OWNER_EMAIL": "owner@example.com", "STUDIO_ACCESS_TOKEN": ""}), patch("api._cloud.auth_call", return_value=user):
            self.assertTrue(_shared.authorized(headers))
            self.assertFalse(_shared.authorized({**headers, "Origin": "https://attacker.example"}))
    def test_other_account_cannot_use_cookie_as_admin(self):
        with patch("api._cloud.auth_call", return_value={"email": "other@example.com", "email_confirmed_at": "now"}), patch.dict("os.environ", {"STUDIO_ACCESS_TOKEN": ""}):
            self.assertFalse(_shared.authorized({"Cookie": "sd_owner=opaque"}))


if __name__ == "__main__": unittest.main()
