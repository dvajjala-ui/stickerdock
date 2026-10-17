import base64
import hashlib
import hmac
import io
import json
import unittest
from unittest.mock import patch, MagicMock
from api import _auth_email as mail
from api.auth_email import handler

KEY = b'unit-test-hook-key-with-32-bytes!!'
SECRET = 'v1,whsec_' + base64.b64encode(KEY).decode()
RELAY_SECRET = 'unit-test-relay-secret-with-32-bytes'


def event(action='recovery'):
    return {'user': {'email': 'owner@example.com'}, 'email_data': {
        'email_action_type': action, 'token_hash': 'a' * 56, 'token': '123456',
        'redirect_to': 'https://attacker.invalid', 'site_url': 'https://attacker.invalid'}}


def signed(body, stamp=1000):
    headers = {'webhook-id': 'msg_test', 'webhook-timestamp': str(stamp)}
    signature = hmac.digest(KEY, b'msg_test.' + str(stamp).encode() + b'.' + body, 'sha256')
    headers['webhook-signature'] = 'v1,' + base64.b64encode(signature).decode()
    return headers


class AuthEmailTest(unittest.TestCase):
    def test_signed_original_bytes_and_rotated_signatures(self):
        body = json.dumps(event()).encode()
        headers = signed(body)
        headers['webhook-signature'] = 'v1,old-invalid ' + headers['webhook-signature']
        self.assertEqual(mail.verify_webhook(body, headers, SECRET, 1000), 'msg_test')
        with self.assertRaises(ValueError):
            mail.verify_webhook(body + b' ', headers, SECRET, 1000)

    def test_expired_future_unsigned_and_missing_configuration(self):
        body = b'{}'
        for headers, now in [(signed(body, 699), 1000), (signed(body, 1301), 1000), ({}, 1000)]:
            with self.assertRaises(ValueError):
                mail.verify_webhook(body, headers, SECRET, now)
        for secret in ('', 'whsec_invalid!'):
            with self.assertRaises(RuntimeError):
                mail.verify_webhook(body, signed(body), secret, 1000)

    def test_fixed_verification_and_redirect_hosts(self):
        result = mail.messages(event())[0]
        self.assertEqual(result['to'], 'owner@example.com')
        self.assertTrue(result['link'].startswith(mail.SUPABASE_URL + '/auth/v1/verify?'))
        self.assertIn('redirect_to=https%3A%2F%2Fstickerdock.vercel.app%2Faccount', result['link'])
        self.assertNotIn('attacker', result['link'])

    def test_email_change_uses_supabase_reversed_hash_mapping(self):
        value = event('email_change')
        value['user']['new_email'] = 'new@example.com'
        value['email_data'].update(token_hash_new='b' * 56, token_new='654321')
        new, old = mail.messages(value)
        self.assertEqual((new['to'], new['code']), ('new@example.com', '654321'))
        self.assertIn('token=' + 'a' * 56, new['link'])
        self.assertEqual((old['to'], old['code']), ('owner@example.com', '123456'))
        self.assertIn('token=' + 'b' * 56, old['link'])

    def test_reauthentication_is_code_only(self):
        value = event('reauthentication')
        value['email_data'].pop('token_hash')
        self.assertEqual(mail.messages(value)[0]['link'], '')

    def test_invalid_recipients_actions_and_tokens(self):
        for target, key, value in [('user', 'email', 'x@example.com,y@example.com'),
                ('email_data', 'email_action_type', 'arbitrary'),
                ('email_data', 'token_hash', 'https://attacker.invalid'),
                ('email_data', 'token', '123456\nBcc: x@example.com')]:
            payload = event()
            payload[target][key] = value
            with self.assertRaises(ValueError):
                mail.messages(payload)

    def test_relay_envelope_is_authenticated_and_retry_id_is_stable(self):
        message = mail.messages(event())[0]
        envelope = mail.relay_envelope(message, 'msg_test', RELAY_SECRET, 1000)
        signed_text = f"1000.{envelope['id']}.{envelope['payload']}".encode()
        self.assertEqual(envelope['signature'], base64.b64encode(hmac.digest(RELAY_SECRET.encode(), signed_text, 'sha256')).decode())
        self.assertEqual(json.loads(base64.b64decode(envelope['payload'])), message)
        self.assertEqual(envelope['id'], mail.relay_envelope(message, 'msg_test', RELAY_SECRET, 1001)['id'])
        self.assertNotIn(RELAY_SECRET, json.dumps(envelope))

    def test_google_ok_false_is_failure_and_url_is_restricted(self):
        env = {'GOOGLE_MAIL_RELAY_URL': 'https://script.google.com/macros/s/test/exec', 'GOOGLE_MAIL_RELAY_SECRET': RELAY_SECRET}
        response = MagicMock()
        response.__enter__.return_value.read.return_value = b'{"ok":false}'
        with patch.dict('os.environ', env), patch('urllib.request.urlopen', return_value=response):
            with self.assertRaises(RuntimeError):
                mail.deliver(event(), 'msg_test')
        with patch.dict('os.environ', {**env, 'GOOGLE_MAIL_RELAY_URL': 'https://attacker.invalid'}), patch('urllib.request.urlopen') as outbound:
            with self.assertRaises(RuntimeError):
                mail.deliver(event(), 'msg_test')
            outbound.assert_not_called()

    def test_secure_email_change_sends_one_signed_batch(self):
        value = event('email_change')
        value['user']['new_email'] = 'new@example.com'
        value['email_data'].update(token_hash_new='b' * 56, token_new='654321')
        env = {'GOOGLE_MAIL_RELAY_URL': 'https://script.google.com/macros/s/test/exec', 'GOOGLE_MAIL_RELAY_SECRET': RELAY_SECRET}
        response = MagicMock()
        response.__enter__.return_value.read.return_value = b'{"ok":true}'
        with patch.dict('os.environ', env), patch('urllib.request.urlopen', return_value=response) as send:
            mail.deliver(value, 'msg_test')
        send.assert_called_once()
        envelope = json.loads(send.call_args.args[0].data)
        batch = json.loads(base64.b64decode(envelope['payload']))
        self.assertEqual([message['to'] for message in batch], ['new@example.com', 'owner@example.com'])
        self.assertLessEqual(send.call_args.kwargs['timeout'], 4)

    def test_handler_rejects_unsigned_before_delivery_and_redacts_failures(self):
        body = json.dumps(event()).encode()
        instance = handler.__new__(handler)
        instance.headers = {'Content-Length': str(len(body))}
        instance.rfile = io.BytesIO(body)
        instance.reply = MagicMock()
        with patch.dict('os.environ', {'SUPABASE_SEND_EMAIL_HOOK_SECRET': SECRET}), patch('api.auth_email.deliver') as send:
            instance.do_POST()
            send.assert_not_called()
            self.assertEqual(instance.reply.call_args.args[0], 401)
        instance.rfile = io.BytesIO(body)
        with patch('api.auth_email.verify_webhook', return_value='msg_test'), patch('api.auth_email.deliver', side_effect=RuntimeError('private token and email')):
            instance.do_POST()
            self.assertEqual(instance.reply.call_args.args[0], 503)
            self.assertNotIn('private token', str(instance.reply.call_args))


if __name__ == '__main__':
    unittest.main()
