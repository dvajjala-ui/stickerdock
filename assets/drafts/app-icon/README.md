# Launcher icon study — October 23, 2026

The old face-on-sticker icon was legible but read as a generic emoji pack.
`comparison.png` places three code-drawn alternatives at 128 px and 48 px.
The **stacked** design is selected: two sticker sheets show the library, a
single chat glyph shows where the reactions are used, and the lime stroke
keeps the established StickerDock accent. The silhouette is inside Android's
adaptive-icon safe area. There is no tiny text or expression that collapses at
launcher size.

Run `python tools/design_app_icon.py` to regenerate the comparisons and the
1024 px opaque iOS source. The shipping iOS `AppIcon.png` is a copy of
`stacked.png`; Android uses matching vector paths in
`app/src/main/res/drawable/ic_launcher_foreground.xml`. Compare both on actual
light/dark launchers and at standard and large display sizes before claiming
device acceptance. This changes only the launcher mark, not in-app navigation.
