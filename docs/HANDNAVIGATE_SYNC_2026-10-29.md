# HandNavigate / StickerDock integration — October 29

Latest HandNavigate main `038cfd1` and StickerDock main `f5ca664` were fetched and fast-forwarded before integrating the unfinished custom-source work. Recovery copies of both local changes were kept. The merge preserves hosted bounded execution, cloud duplicate history, browser-origin checks, Factory source admission, OCR screening, the Sources workspace and native Library changes.

The capability contract stays `supported_sources`. New desktop source recipes advertise only ID/name/revision; URLs, taught actions, browser profiles and cookies remain local. Admin loads registered custom source names from the private catalog, accepts them in manual and daily topic plans, and keeps the existing single-compatible-worker check for create/retry/daily work. No new migration is needed: the private source-catalog/compatible-claim migration is already on main.

The collection UI now explains that a scheduled worker exits between runs and manual jobs wait for the next connection. It displays the server-provided daily schedule instead of the retired GitHub time. The default label reflects the documented Railway 10:00 India run; `HANDNAVIGATE_SCHEDULE_LABEL` can override it when hosting changes. This label is not a live scheduler probe.

A custom recipe installed on a desktop is not automatically installed on Railway. The target worker needs that reviewed recipe and separate site-access qualification. Source review/admission and generation/publication behavior from the new main are preserved; this merge performs no bulk approvals, paid generation or hosting changes.

Local verification: 325 Python tests, all six SQL checks, and a real Chromium fixture run covering Sources upload/admission, custom-source selection, scheduled-worker copy and 390px layout. HandNavigate covers 37 core tests, 26 worker tests and desktop acceptance with actual owned HTTP image downloads. Source-bound CI links and packaged acceptance are recorded after the integration build completes.

StickerDock integration `3a6d7a6` passed [Web/API CI](https://github.com/dvajjala-ui/stickerdock/actions/runs/40752108556), including source capability regressions, SQL checks, concurrent Creator admission and the real browser fixture. HandNavigate also repairs an asynchronous-search race: all requests in its dedicated browser session contribute to settling, and taught image-result checks gate gallery reads before any file is saved.
