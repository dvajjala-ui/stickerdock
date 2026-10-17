# Gmail account email for StickerDock

This uses a small Google Apps Script owned by **dvajjala@gmail.com**. It uses
`MailApp` with only `script.send_mail` permission: no inbox access and no Gmail
password or app password. The sender address is the account authorizing the script.
The owner explicitly approved correcting the Admin allowlist/form to
`dvajjala@gmail.com` on October 17. `STUDIO_OWNER_EMAIL` can override that default.

## Files and flow

- `integrations/google-mail/Code.gs`: fixed plain-text account emails, HMAC
  verification, five-minute request window, persistent retry deduplication and
  a 90-recipient daily ceiling. Recipients/reset links/codes are never stored in
  Script Properties or logged. Only the server's signed requests can send mail.
- `integrations/google-mail/appsscript.json`: explicit send-only OAuth scope.
- `/api/auth_email`: verifies Supabase Standard Webhooks signatures against the
  original request, creates fixed Supabase verification links redirecting to
  `/account`, and signs the outgoing Apps Script request. A Google JSON
  `ok:false` reply is a delivery failure even when Google's HTTP status is 200.

Supabase retains account creation, confirmation, reset tokens and rate limits.
The adapter does not mint tokens, change passwords or bypass confirmation.
Both relay and hook secrets must remain server-side; no secret belongs in Git,
the APK, page JavaScript, URLs, screenshots or chat.

## Deployment

1. In Apps Script signed into `dvajjala@gmail.com`, create **StickerDock Account
   Mail**. Paste `Code.gs`. Enable viewing `appsscript.json` in Project settings
   and replace it with the provided manifest.
2. Generate a unique random 32-byte-or-longer relay secret. Set the same value
   in Script Properties as `RELAY_SECRET` and Vercel Production as
   `GOOGLE_MAIL_RELAY_SECRET`. Keep this value out of source and logs.
3. Run `authorizeMail`. Approve only send mail permission. Deploy a Web app,
   execute as the owner, access **Anyone**. The endpoint rejects unsigned mail
   requests. Copy its `/exec` deployment URL to Vercel Production environment
   variable `GOOGLE_MAIL_RELAY_URL`.
4. In Supabase Authentication → Auth Hooks, prepare a **Send Email** HTTP hook
   to `https://stickerdock.vercel.app/api/auth_email`. Generate the signing
   secret, and store it in Vercel Production as
   `SUPABASE_SEND_EMAIL_HOOK_SECRET` (the `v1,whsec_...` form is accepted).
5. Deploy Vercel with those variables. Test signed relay delivery, tampered
   signature rejection, duplicate request handling and cold/warm timing before
   enabling the Supabase hook. HTTP hooks must finish within five seconds;
   Apps Script cold starts can exceed this. Do not enable a slow/unverified
   relay for real account recovery. Use SMTP or a durable delivery service if
   repeat tests cannot fit that budget. The adapter calculates a four-second
   request timeout. Socket timeouts apply per network operation; slow redirects
   or reads can still exceed the Supabase deadline.
6. Enable only after the timing check, then request a real confirmation or
   recovery email for the intended owner. Verify inbox delivery and the
   `/account` redirect. The account holder enters/submits their new password.
   Keep email confirmation enabled. Disable the new hook to roll back delivery
   routing if the end-to-end check fails.

New scope/access grants and secret entry may require the account holder at
Google's authorization and deployment screens. Apps Script daily quotas vary by
account; the script also stops at 90 recipients/day. This is a small-volume
account sender, not a bulk-mail service. Quota exhaustion fails closed.
Delivery can still duplicate if Gmail accepts a message and execution dies
before the sent-ID property is written; exactly-once email is not guaranteed.

## Verification

October 17 live setup: Google send-only authorization completed, Apps Script
deployment version 2 is active, all three connection variables are stored as
Vercel Production secrets, and the Supabase Send Email HTTPS hook is enabled.
No Gmail password is required. Two synthetic code-only test messages arrived
in `dvajjala@gmail.com`; their codes are test fixtures, not usable login codes.

- Direct Google cold delivery: 3.438 seconds; retry deduplicated in 3.234
  seconds; expired request rejected.
- Signed request through the production site: HTTP 200 in 2.735 seconds;
  duplicate HTTP 200 in 1.640 seconds; unsigned request HTTP 401.
- Actual `/api/account` recovery request for the owner was accepted (HTTP 200).
  That response intentionally conceals account existence and does not by itself
  prove reset-email delivery. Owner signup/password choice and real confirmation
  or recovery-link acceptance still need the account holder.

Secure email-change notifications use one signed two-recipient batch to avoid
two Apps Script startup delays. Partial batch retries retain per-recipient
deduplication. Tests cover the mapping and partial-send recovery; a live real
email-change flow has not been performed. Observed timing is not a guarantee
against future Google cold starts or service outages.

```powershell
.\.venv\Scripts\python.exe -m unittest tools.test_auth_email tools.test_owner_session -v
node tools/test_google_mail.cjs
```

Source tests use fake addresses, tokens and Google services. They do not prove
Google authorization, inbox delivery or live Supabase hook latency.

The initial 198 Python/backend tests and [hosted build](https://github.com/dvajjala-ui/stickerdock/actions/runs/39041025722)
pass. Subsequent latency/batch changes pass focused hook/session Python tests,
the actual Apps Script source against Google-service fakes, undefined-name
checks and whitespace validation. The [final hosted build for d061cb1](https://github.com/dvajjala-ui/stickerdock/actions/runs/39049622843)
passes the complete backend suite, mail script checks and Android build checks.
Secrets and test recovery links are excluded from source and public evidence.

References: [Google MailApp](https://developers.google.com/apps-script/reference/mail/mail-app),
[web app deployment](https://developers.google.com/apps-script/guides/web),
[Google quotas](https://developers.google.com/apps-script/guides/services/quotas),
[Supabase Send Email Hook](https://supabase.com/docs/guides/auth/auth-hooks/send-email-hook),
[Supabase hook timeout](https://supabase.com/docs/guides/auth/auth-hooks#error-handling).
