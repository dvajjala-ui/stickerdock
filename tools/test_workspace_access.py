"""Open workspace endpoints retain origin checks and private account boundaries."""
import io
import json
import unittest
from unittest.mock import patch

from api import admin, captions, cloud, library, upload
from api._shared import same_origin


class WorkspaceAccessTest(unittest.TestCase):
    def request(self, module, payload, headers=None):
        raw = json.dumps(payload).encode()
        request = object.__new__(module.handler)
        request.headers = {"Content-Length": str(len(raw)), **(headers or {})}
        request.rfile = io.BytesIO(raw)
        return request

    def test_workspace_reads_and_commands_need_no_cookie_password_or_token(self):
        cases = [
            (admin, {"action": "overview"}, "dispatch", {"packs": []}, 200),
            (admin, {"action": "settings_save", "daily_target": 40}, "dispatch", {"saved": True}, 200),
            (library, {"action": "list"}, "list_inbox", [], 200),
            (upload, {"image": "fixture-image", "title": "Owned source"}, "upload_user_image", {"path": "inbox/a.png"}, 200),
            (captions, {"action": "queue", "prompt": "Reaction", "source_path": "inbox/a.png"}, "queue_creator_request", {"status": "queued"}, 202),
        ]
        for module, payload, operation, result, status in cases:
            with self.subTest(endpoint=module.__name__, action=payload.get("action")), \
                    patch.dict("os.environ", {}, clear=True), \
                    patch("api._cloud.auth_call") as authenticate, \
                    patch.object(module, operation, return_value=result) as call, \
                    patch.object(module, "send_json") as send:
                request = self.request(module, payload)
                request.do_POST()
                self.assertEqual(send.call_args.args[1], status)
                call.assert_called_once()
                authenticate.assert_not_called()

    def test_foreign_browser_requests_cannot_invoke_workspace_operations(self):
        for module in (admin, captions, library, upload):
            with self.subTest(endpoint=module.__name__), \
                    patch.object(module, "read_json") as read, \
                    patch.object(module, "send_json") as send:
                self.request(module, {}, {"Origin": "https://foreign.example", "Host": "stickerdock.vercel.app"}).do_POST()
                self.assertEqual(send.call_args.args[1], 403)
                read.assert_not_called()

    def test_origin_is_not_an_account_gate_and_supports_loopback_preview(self):
        self.assertTrue(same_origin({}))  # Operational HTTP clients, no identity.
        self.assertTrue(same_origin({"Host": "stickerdock.vercel.app", "Origin": "https://stickerdock.vercel.app"}))
        self.assertTrue(same_origin({"Host": "127.0.0.1:4179", "Origin": "http://127.0.0.1:4179"}))
        for headers in (
            {"Sec-Fetch-Site": "cross-site"},
            {"Origin": "null"},
            {"Origin": "http://[bad"},
            {"Host": "stickerdock.vercel.app", "Origin": "http://stickerdock.vercel.app"},
            {"Host": "stickerdock.vercel.app", "Origin": "https://stickerdock.vercel.app.foreign.example"},
        ):
            with self.subTest(headers=headers):
                self.assertFalse(same_origin(headers))

    def test_private_phone_library_still_requires_account_authentication(self):
        with patch.object(cloud, "dispatch") as dispatch, patch.object(cloud, "send_json") as send:
            self.request(cloud, {"action": "inventory"}).do_POST()
            self.assertEqual(send.call_args.args[1], 401)
            dispatch.assert_not_called()


if __name__ == "__main__":
    unittest.main()
