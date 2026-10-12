# Moderator-curated public sticker catalog

This repository-based catalog is a low-cost, **read-only proof of concept**, not an open upload service. Anyone can propose a general-audience pack by pull request; a maintainer must verify ownership/permission, file checksums, security and content policies before adding it to `packs.json`.

Public entries currently support **GENERAL** content only. MATURE or sexual sticker packs are not listed while age-screening, default-safe filters, moderation, in-app reports and user blocking remain unimplemented. Do not add unlawful, non-consensual, exploitative or minor sexual material.

To propose a pack, provide a StickerDock export ZIP under `catalog/packs/` and metadata with `id`, `name`, `creator`, `rating: "GENERAL"`, `file` (basename), and lowercase SHA-256 `sha256`. Open a PR for manual review. The app downloads only from this repo and validates the archive hash before importing. If this becomes popular, move content hosting to a proper API/object store with robust abuse tooling and legal review.

Avoid uploading other creators' Sticker.ly assets without their explicit redistribution rights. References: https://sticker.ly/terms-of-use and https://support.google.com/googleplay/android-developer/answer/9876937.
