import json
import os
import time
import unittest
from unittest.mock import patch
from urllib.parse import urlsplit, parse_qs
from tools import pinterest_connection as connection
from test_production_worker import FakeSupabase


class PinterestConnectionTest(unittest.TestCase):
    def setUp(self):
        self.client = FakeSupabase()
        self.env = patch.dict(os.environ, {"STUDIO_ACCESS_TOKEN":"x" * 48,
            "PINTEREST_APP_ID":"1624134", "PINTEREST_APP_SECRET":"fixture-secret",
            "PINTEREST_ACCESS_TOKEN":""})
        self.env.start()
        self.addCleanup(self.env.stop)

    def test_oauth_uses_fixed_callback_minimal_scopes_and_secure_cookie(self):
        url, cookie = connection.oauth_start()
        args = parse_qs(urlsplit(url).query)
        self.assertEqual(args["redirect_uri"], [connection.CALLBACK])
        self.assertEqual(args["scope"], ["boards:read,pins:read"])
        self.assertIn("Secure; HttpOnly; SameSite=Lax; Path=/", cookie)
        self.assertNotIn("fixture-secret", url)

    def test_valid_callback_stores_only_credentials_in_private_bucket(self):
        url, cookie = connection.oauth_start()
        state = parse_qs(urlsplit(url).query)["state"][0]
        with patch.object(connection, "provider", return_value={"access_token":"access-fixture-token", "refresh_token":"refresh-fixture", "expires_in":3600}):
            connection.oauth_complete("code=example&state=" + state, cookie, self.client)
        row = connection.load_connection(self.client)
        self.assertEqual(row["access_token"], "access-fixture-token")
        self.assertNotIn("pins", row)
        self.assertEqual(len(self.client.objects), 1)

    def test_callback_rejects_missing_mismatched_expired_and_tampered_state(self):
        url, cookie = connection.oauth_start()
        state = parse_qs(urlsplit(url).query)["state"][0]
        with patch.object(connection, "provider") as provider:
            for query, header in [("code=x&state="+state, ""), ("code=x&state="+state+"0", cookie), ("state="+state, cookie)]:
                with self.assertRaises(ValueError):connection.oauth_complete(query, header, self.client)
            with patch.object(connection.time, "time", return_value=time.time()+601):
                with self.assertRaises(ValueError):connection.oauth_complete("code=x&state="+state,cookie,self.client)
            provider.assert_not_called()

    def test_live_browse_returns_links_without_persisting_provider_content(self):
        with patch.dict(os.environ, {"PINTEREST_ACCESS_TOKEN":"test-token"}), patch.object(connection,"provider",return_value={"items":[{"id":"123456789", "title":"Reaction", "media":{"images":{"url":"https://example.com/image.jpg"}}}],"bookmark":"next"}):
            result=connection.browse(self.client,{"board_id":"987654321"})
        self.assertEqual(result["items"][0]["url"], "https://www.pinterest.com/pin/123456789/")
        self.assertNotIn("media",json.dumps(result))
        self.assertEqual(self.client.objects,{})

    def test_status_never_exposes_credentials_and_disconnect_overrides_environment(self):
        with patch.dict(os.environ,{"PINTEREST_ACCESS_TOKEN":"environment-secret"}):
            self.assertTrue(connection.connection_status(self.client)["configured"])
            self.client.upload(connection.PRIVATE_BUCKET, connection.CONNECTION_PATH,b'{"disconnected":true}')
            self.assertEqual(connection.access_token(self.client),"")
            result=connection.connection_status(self.client)
        self.assertFalse(result["configured"])
        self.assertNotIn("environment-secret",json.dumps(result))

    def test_expired_token_refresh_preserves_refresh_credential(self):
        connection.save_tokens(self.client,{"access_token":"old-access-token", "refresh_token":"old-refresh-token", "expires_in":1})
        with patch.object(connection,"provider",return_value={"access_token":"new-access-token", "expires_in":3600}) as provider:
            self.assertEqual(connection.access_token(self.client),"new-access-token")
        provider.assert_called_once()
        self.assertEqual(connection.load_connection(self.client)["refresh_token"],"old-refresh-token")
