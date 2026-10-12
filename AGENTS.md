# Instructions for Codex — StickerDock

Read `docs/CODEX_HANDOFF.md`, `README.md`, `docs/PRODUCT_SPEC.md`, and `docs/QA_MATRIX.md` before editing. The user explicitly wants the Android WhatsApp sticker-manager app built, not just another design proposal. Continue implementing and testing the checked-in project.

Priority order:
1. Run `gradle :app:assembleDebug` (JDK17, Gradle8.9, SDK35). Fix all compiler errors and then add passing automated tests.
2. Prove pack import, duplicate detection, ZIP backup/restore and provider asset retrieval on an emulator/phone.
3. Test export into WhatsApp and WhatsApp Business separately; document behavior for in-app WhatsApp Switch Account and Samsung Dual Messenger without making unsupported claims.
4. Improve bulk operations, sticker-level search/tags, UI responsiveness, accessibility and animated sticker handling.
5. Investigate ethical, authorized Sticker.ly share-link imports only once the core is stable.

Guardrails: preserve local user files and DB in upgrades, do not deploy/pay/create cloud infrastructure without approval, do not scrape private/third-party data or claim access to another app's private database. Never repurpose unrelated GitHub repositories.

Important: initial source generation did not include an Android SDK or Gradle build, so treat existing code as unverified until build and device QA are completed.
