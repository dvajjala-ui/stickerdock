# Pinterest reconnection and real-image stickers

Updated October 22, 2026. Supersedes the archived Pin-download recommendations
in older Factory/Pinterest documents; it does not remove the user's requirements.

## Current connection status

The Pinterest developer dashboard shows **Trial access active** for StickerDock
app **1624615**. On October 22, the exact redirect URI was saved, the existing
app secret was configured as a server-only Vercel Production secret and the
read-only OAuth consent completed. The live Admin **Check connection** returned
`connected` after the server called the official boards API. **Browse my boards**
returned the `Quick Saves` board; that board currently returned no Pins. This
verifies connection and board access, not that any reference media is available
for sticker creation. No write or advertising scope was requested.

Before Trial approval, a user-generated 24-hour limited token was tested once
against `/v5/boards?page_size=1`: HTTP 401, code 3, consumer type not supported.
Do not reuse it or claim it verifies the approved application.

The public About, Privacy and Support URLs all returned HTTP 200 with the chosen
contact after deployment. Three independently verified public-domain/CC0 cat
photos are seeded into the existing bounded Factory media queue; provenance stays
attached. A discovery outage no longer blocks an explicit licensed media queue.

## Verified dashboard and policy findings

- App `apphidwarkesh`, ID `1624134`, is **Trial access denied**. Details and
  redirect fields are locked. Website/privacy fields still contain `http://hi.com/`.
  The dashboard does not identify a specific rejection reason, so fake links are
  a plausible problem, not a verified exclusive cause.
- Pinterest's published guidelines prohibit archiving API-provided Pin/Board
  information, scraping without permission, and creating distributable content
  from Pins. A new application must honestly describe the revised integration.
  Do not use a new account/name to evade denial of the old downloader use case.
- Current official references:
  https://policy.pinterest.com/en/developer-guidelines
  https://developers.pinterest.com/docs/key-concepts/access-tiers/

## Correct public application values

- Product: StickerDock
- Developer/operator: Dwarkesh Vajjala (StickerDock)
- Website: https://stickerdock.vercel.app/about
- Privacy: https://stickerdock.vercel.app/privacy
- Support: https://stickerdock.vercel.app/support
- Contact chosen by owner: dvjjala@gmail.com
- OAuth redirect: https://stickerdock.vercel.app/api/pinterest_callback
- Scopes: `boards:read,pins:read`. No ads, write, or secret-board access.

Accurate purpose: StickerDock is a personal Android sticker library and a private
web creative workspace. The Pinterest integration lets the authenticated owner
view their own boards and Pin titles/source links live through the official API.
Pin/Board responses are not retained, downloaded to the Factory, sent to Groq,
or converted into distributable stickers. Meme generation uses independently
uploaded or licensed source media. The previous app ID 1624134 was denied and
contained placeholder links; this request supplies the real product pages and
replaces the earlier download/archive use case with this read-only integration.

The app ID defaults to 1624615 in source. `PINTEREST_APP_SECRET` exists only as
a protected Vercel Production value; the OAuth access and refresh credentials
are stored in private Supabase storage. The approved scopes are
`boards:read,pins:read`. Never commit credentials. The first OAuth attempt
failed because Admin requests had `credentials: "omit"`, which discarded the
short-lived state cookie. Commit `2ab52c7` changed requests to same-origin
credentials; the subsequent live attempt succeeded.

## Implemented connection

Admin → Pinterest connection supports status/check, official OAuth, live boards,
live Pin source links, cursor pagination and disconnect. Ten-minute state signed
with the server-only Pinterest app secret is bound to a Secure/HttpOnly/SameSite
cookie; token exchange happens server-side.
Access/refresh credentials live in private Supabase storage, not browser storage.
Revocation is available in Pinterest's own account settings. The daily job now
checks connection health without saving Pins or downloading images. Legacy Pin
downloads already held remotely were not deleted. The old optional Factory
`pinterest_pin_licenses` flag no longer creates download candidates; the board
reader returns source-link bookmarks only, even if that flag is supplied.
The shared Admin has no account gate by owner request: anyone with its URL can
browse the connected account's **public** boards/Pins or disconnect Pinterest.
This access model should be revisited before inviting external users.

## Desired visual style and personal data

The user wants recognizable real-image reactions, funny Hinglish captions,
animals/people and meme-like cutouts rather than generic emoji faces. Public
Sticker.ly reaction previews were consulted as visual/product references; no
third-party packs were imported or represented as ours. Production configuration
now requires independent photos/clips and fails clearly when none are available.
Legacy illustration outputs stay identifiable and are not evidence of this style.

WhatsApp stickers imported using Android are stored in local SQLite/app-private
media. They are not accessible from the backend and there is no existing account
or automatic cloud sync. R18 retains the new optional private library-sync request.
Do not publish private imported content or use it for model training without an
appropriate separate decision. No device was connected through ADB at inspection.
