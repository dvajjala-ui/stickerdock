# Direct admin dashboard — October 19, 2026

User instruction: “isndie thea dmin panel remvoe the passwrd give direct
daashbapord like. no paswrd bneed just all data vailbel as it is dont remvoe
teh db just api adn forntend rmeove it coemptlely and push”.

Admin and Creator Studio open directly, load existing operational data and use
their APIs without passwords, cookies, owner accounts or access tokens. This is
an intentionally shared workspace: anyone with the URL can read and use its
controls. It is not a private or read-only dashboard. The privacy page and
Settings describe this access model.

The login dialog, signup/reset/logout controls, session refresh logic, fake
preview records and `/api/session` owner-login endpoint are removed. Startup
loads health/overview immediately; outages show a retry action rather than
invented totals. Packs, settings, media, queue, review and publishing operations
keep the same existing database/storage implementation.

No SQL, schema migration, stored user, pack, image, bucket policy or phone backup
is removed or reset. Supabase/Groq credentials remain server-side. The old
`STUDIO_ACCESS_TOKEN` is no longer an admin access gate; its existing server-only
use signing Pinterest OAuth state is retained. Pinterest still requires its own
official account authorization and application approval.

Workspace APIs reject explicit foreign browser origins and do not grant
cross-origin CORS access. This is not authentication: direct HTTP clients can
still read and operate the workspace. Phone `/api/cloud` bearer authentication,
owner isolation and account password recovery remain separate and enforced.

Verification covers credential-free Admin/Studio API reads and commands using
fixtures, foreign-origin rejection, private phone-cloud denial without a bearer,
and browser startup, errors/retry, navigation, edits, background polling, mobile
layout and Studio source → fixture AI request → result. Provider generation and
destructive production operations are not invoked by these checks.
