# Technical research notes

- WhatsApp official Android sticker integration: https://github.com/WhatsApp/stickers/blob/main/Android/README.md
- WhatsApp sticker provider sample: https://github.com/WhatsApp/stickers/blob/main/Android/app/src/main/java/com/example/samplestickerapp/StickerContentProvider.java
- WhatsApp pack sharing: https://faq.whatsapp.com/1056840314992666
- Static stickers: 512x512 WebP <=100KB, 3–30 stickers/pack, tray PNG 96x96 <=50KB. Confirm current platform requirements before release.
- Sticker.ly account-wide export and open source status are not verified. No scraping or full-account importer is shipped.
- Android Intent.setPackage selects WhatsApp app package, not a WhatsApp account within that app.
- No Android APK or physical-device QA completed when project was authored.
