# StickerDock has no reflection-based application models. Keep Android provider
# classes referenced by the manifest explicit for release shrinker stability.
-keep class app.stickerdock.duo.WhatsAppStickerProvider { *; }
-keep class app.stickerdock.duo.MainActivity { *; }
