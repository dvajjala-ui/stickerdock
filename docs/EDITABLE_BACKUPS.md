# Editable Android backups

The import/backups screen offers two explicit choices. **Editable backup** keeps
saved packs and their normalized 512×512 starting artwork, saved edit histories,
unfinished drafts, Undo/Redo positions and referenced cutout masks. Keep it private:
an original can include parts of a picture removed from the shared sticker.
It does not retain a full-resolution camera original or a video editing project.
**Sticker backup** keeps the existing portable schema-5 ZIP of saved artwork,
names, tags, categories and ratings. Pack sharing and cloud copies still use that
portable format and exclude private editing material.

The editable format is an Android envelope, version 1. Its first ZIP entry is
`editable-backup.json`, followed by `library.zip` (the unchanged schema-5 archive)
and content-addressed `blobs/<sha256>.webp` originals / `.png` masks. The manifest
binds each project to a saved sticker archive path and checksum. No database IDs
are reused on restore. The nested `library.zip` remains usable by older clients
and the iPhone app; they cannot directly restore the editable envelope yet.

Restore stages entries into generated cache filenames, bounds counts, bytes and
recipe sizes, checks hashes, dimensions, histories and exact mask references,
and rejects unexpected paths or unreferenced files. Projects can only target
static stickers. Library restore creates new packs; if media or project writes
fail, only those new packs are rolled back. Existing packs are never replaced.
Staging folders are removed on success and failure. A killed process may leave a
staging folder; resumable backup/restore and automatic stale-cache cleanup remain
future work. This format is not encrypted.

`EditableBackupTest` covers saved/draft/mask round-trip, restart, exact Reset,
portable compatibility, generic importer dispatch, damaged/missing/unreferenced
blobs, invalid recipes, duplicate references and storage failure/retry. Source
checks do not replace the matching CI run or physical document-provider testing.

Source `f1bcb0d` passes [Android CI](https://github.com/dvajjala-ui/stickerdock/actions/runs/39205131984)
with eight backup tests and [complete signed acceptance](https://github.com/dvajjala-ui/stickerdock/actions/runs/39205128245),
including real DocumentsUI export, original/mask checksums, one-project restore,
restart/history preservation, cancellation and 200% text. The full-resolution,
video, iPhone-editable, resumable-restore and physical-provider limits above remain.
