# Private cloud library and reviewed sharing — 0.6.0

Phone imports and edits always remain in SQLite and app-private local files.
Open **Cloud**, create/confirm an email account, sign in, and enable **Private
cloud sync** to copy general-audience packs to that account. Mature packs stay
offline. Uncategorised GENERAL/OTHER packs are stored as ALL in the cloud.

Sync runs after library changes while the app is open and Android schedules a
network-constrained retry about every six hours. Android battery/background
restrictions can delay it. The Cloud sheet shows progress, errors and retry.
The account has a 512 MB / 20,000 asset / 1,000 pack limit; hourly request quotas
bound accidental loops. Uploads resume from per-image and per-pack checkpoints.
All bytes are SHA-256 checked. Missing images cannot be committed as ready packs.
No local file is removed because of a network error.

**Restore cloud packs** restores missing packs. If a remote revision changed,
it creates a separate cloud copy and preserves local edits. Cloud commit uses
optimistic revisions to prevent another device's changes being overwritten.
Deleting a previously synced local pack removes its cloud metadata on the next
successful sync from that installation. Turning sync off keeps cloud copies.
Account removal/full media erasure requests go through dvjjala@gmail.com;
unreferenced uploaded assets currently need operator cleanup. ZIP backups remain
independent and never contain account credentials.

Open a general pack → **Backup** → **Share with everyone**. Public opt-in requires
an adult/redistribution-rights confirmation. It sends an immutable snapshot to
Admin → **User submissions**. Moderators preview every sticker, create a draft,
review content/privacy/rights, then explicitly review and publish through the
existing catalog flow. Sync alone grants no public or AI-training permission.
Review drafts strip EXIF/XMP and outer custom metadata without recompressing
sticker pixels/frames; the private source checksum stays in provenance.
Changes to shared packs require withdrawal and resubmission. Withdrawal removes
the pack from future catalog discovery; already downloaded/publicly cached
copies cannot be recalled. No open unmoderated publishing is enabled.

Discover provides hide pack/creator and report controls. Hidden creators are
stored locally and excluded from catalog browsing. Reset hidden
packs/creators restores discovery. Existing local copies stay under user control.
Signed-in reports appear in Admin; email support is also available without login.

## Backend and deployment

- Additive migration: `supabase/migrations/202610150001_private_cloud_library.sql`.
- Tables: cloud_packs, cloud_assets, community_submissions, community_reports,
  cloud_request_budgets. RLS enabled, anon/authenticated table grants revoked.
- Server verifies Supabase Auth user on every library request. Every query and
  object path derives owner_id from that verified user, never from client input.
- Images: existing PRIVATE bucket, users/{owner_id}/{sha256}.webp. No service key
  or owner token ships in Android. Auth sessions use Android Keystore encryption.
- `/api/cloud`: signup/login/refresh, private upload/commit/list/download,
  snapshot sharing/withdrawal, delete and reports. Responses never cache.
- Email confirmation/SMTP must be configured for the intended user audience;
  Supabase's default email sender is not a general production mail service.
  The owner explicitly requested skipping SMTP setup on October 15; do not
  disable confirmation to conceal this gate. The production Site URL is now
  https://stickerdock.vercel.app/account, fixing the localhost redirect.
- Existing Vercel Production credentials support the new routes without new
  secrets. `/api/session` gives owner browser sign-in using Secure HttpOnly
  SameSite cookies, with refresh and logout. Owner allowlist defaults to the
  user's confirmed dvjjala@gmail.com; STUDIO_OWNER_EMAIL can override it.
- Legacy owner-token authorization remains for operational scripts; the admin
  website never asks a user to paste a key and starts against live data.

## Acceptance still requiring a phone

1. Import a phone folder; confirm local packs still export to WhatsApp offline.
2. Enable sync and interrupt Wi-Fi; retry and compare hashes/counts/categories.
3. Sign into another installation and restore; verify static/animated separation.
4. Edit on two devices and verify conflict preservation rather than overwrite.
5. Submit a rights-cleared pack, review/publish, receive on another phone; hide,
   report, withdraw, and confirm it disappears from new catalog discovery.
6. Disable sync, sign out, and verify the local library remains available.

Compilation, unit tests, and hosted HTTP checks do not prove phone acceptance.
