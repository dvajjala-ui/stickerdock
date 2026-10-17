# Local editable artwork — October 16, 2026

This implements the next creator slice of the recovery plan. Device acceptance
and artifact identity belong in `RECOVERY_PROGRESS.md`; source alone is not a
claim of runtime completion.

## User behavior

Opening a static sticker's editor retains the 512×512 starting sticker before
its first edit in this version. Saved rotations, positioning and captions remain
editable when reopened. Reset restores those exact starting bytes when saved.
It cannot reconstruct an unedited photo from a sticker flattened by an older
release, and does not retain the full-resolution camera input.

Manual cutout accepts a traced or tapped outline. Outside pixels become
transparent before rotation/crop/captions are applied. Up to 64 outline points
are quantized to half-pixel coordinates, allowing all 51 history states to remain
bounded. Apply, Remove cutout and Reset are undoable; saved outlines stay editable.
This is a manual tool, not automatic subject segmentation. Empty/tiny/out-of-bounds
outlines are rejected. Gesture handling uses separate tap/drag listeners following
[Compose's guidance](https://developer.android.com/develop/ui/compose/touch-input/pointer-input/understand-gestures).

Unsaved changes autosave locally after a short debounce. The editor reports
“Saving draft…” until the write completes, then “Draft saved on this device.”
Force-stop/reboot recovery applies to the last completed draft write. Cancel
offers Keep draft, Keep editing or Discard edits. Library lists unfinished edits
with Resume edit; opening one does not update the saved pack until Save.

The last saved WebP remains the WhatsApp/share/cloud artifact. A draft does not
change sticker or pack version, and cannot enter a public pack or cloud upload.
Originals and recipes remain on this device. Existing schema-5 ZIP exports and
private cloud backup contain saved stickers, not editable projects. The editor
states this boundary; project-aware portable backup remains separate work.

## Storage and commit ordering

The additive SQLite v7→v8 migration adds `artwork_projects`, keyed by sticker ID
with a cascading foreign key. Rows contain the starting-image checksum, current
saved-artifact checksum, bounded saved history, optional draft history and update
time. A partial index supports the draft list. Existing pack/sticker/import
journal rows and indexes remain intact.

Starting images live in app-private `artwork-originals/<sticker UUID>/<SHA>.webp`.
Paths are allowlisted, sizes and dimensions bounded, and checksums verified on
read. A source file must be synced and verified before SQLite references it.
Corrupt source/recipe reads fail without overwriting the saved sticker or draft.

Save renders from the retained starting image, syncs/verifies a new immutable
WebP, and updates the sticker row and saved recipe in the same SQLite transaction.
Only after commit is the previous exported WebP removed. A recipe commit failure
rolls back both database updates and removes the uncommitted new export, leaving
the old artifact intact. Drafts use SQLite persistence rather than depending
solely on Android saved instance state. This follows the distinction in Android's
[state-saving guidance](https://developer.android.com/topic/libraries/architecture/saving-states).

Each history has at most 51 states. Slider gestures/caption bursts are grouped.
Persistent recipes use strict bounded decoding; malformed data must not silently
turn into a default edit. A stale editor is rejected by the current sticker ID,
filename and checksum before draft/save/discard mutations.

Full-screen editor/cutout dialogs union owner activity insets with dialog insets
so Compose can pad system bars even when the dialog receives consumed values.
The caption editor explicitly selects keyboard resize: Compose 1.7.8 only does
this automatically below Android 12, and its newer-platform default panned the
header underneath the status bar during typing. Emulator acceptance requires
the header to retain its position with the keyboard open and actual Start
over/Apply interactions above three-button navigation at narrow/large-font
settings. See Android's [DialogProperties contract](https://developer.android.com/reference/kotlin/androidx/compose/ui/window/DialogProperties).

Copy preserves the saved recipe and starting image. Move also transfers the
unsaved draft. Removing a sticker/pack cascades its project row and removes only
its own private starting-image directory. Single-sticker deletion Undo retains
a bounded in-memory project snapshot and restores it along with the WebP.

## Acceptance

- Upgrade legacy databases without changing saved media, metadata or scan state.
- Save → reopen → replace caption → reset returns the exact retained image.
- Draft → close database / force-stop / cold boot → Resume edit retains history.
- Fail image write or project commit: current row, artwork and draft remain.
- Copy, move, delete and Undo retain or remove project data as described.
- Export schema remains 5 and excludes starting images, recipes and unsaved text.
- No personal-media fixtures, project records or signing secrets in Git.

Broader photo inputs, project-aware portable backups and automatic
segmentation need their own acceptance evidence before being marked complete.
