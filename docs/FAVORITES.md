# Favorite reactions

Android 0.11.1 adds device-local favorite shortcuts to existing stickers. Open
a sticker and choose **Add to favorites**. In Library, choose **Favorites** to
browse their artwork, search labels/tags/filenames/packs, or filter by type and
topic. Tap a favorite to open its original pack and preview/manage the sticker.
The heart control removes the shortcut; it does not delete the sticker.

Favorites retain their original pack and private rating. Moving a sticker moves
its shortcut to the saved replacement row. Copying keeps the original shortcut
and does not favorite the copy. Delete followed by Undo restores the shortcut;
deleted stickers/packs are pruned from the collection.

Favorite IDs live in app-private preferences. No media, pack version, SQLite 8
schema or portable ZIP 5 contract changes. Favorite shortcuts are intentionally
not included in cloud/portable/editable backups; restored stickers remain usable
and can be favorited again. Existing photos and packs remain authoritative.

Validation: six native regression tests cover restart/privacy/version retention,
deleted-row cleanup, move/copy semantics, bound SQL parameters, more than 1,000
IDs and search/type/topic/sorting. Signed emulator acceptance adds explicit
favorite/search/restart, 200% text, move, delete/Undo and non-destructive removal
to the existing 10,000-sticker release journey. Source, CI and exact release
evidence are recorded in QA_MATRIX.md; implementation alone is not acceptance.
