# Shared GIF and video creation

Android 0.11.2 source adds StickerDock to video share targets. Share one video or
GIF from a gallery/chat/file app to open the animated creator. Its existing trim,
caption, crop, preview and explicit Save controls apply. Opening, previewing and
cancelling do not create a pack or sticker. Source bytes stay local and use the
existing 20 MB, ten-minute, bounded-frame limits. Provider grants can expire;
reselect the source in the system picker if the sender removes access.

The dispatcher checks the content provider's MIME and filename on IO. Photos
remain in the photo destination preview; ZIP/.wastickers remain in explicit pack
import. A single GIF uses the moving creator instead of being flattened through
photo import. Mixed media or multiple moving clips show an actionable error
before import. Only granted `content://` URIs are accepted; remote URLs are not
downloaded. No schema, backup or existing media is changed by dispatch.

Five regression tests cover broad sender MIME, provider MIME, generic files,
mixed batches, multiple clips, unsupported files and remote URI rejection.
Signed acceptance adds cold video SEND, warm GIF SEND, captioned moving previews,
disabled Save before preview and cancel/restart without library mutations.
Source implementation alone does not establish passing signed acceptance; see
QA_MATRIX.md for the latest exact artifact evidence. Physical sender apps and
their permission lifetimes remain device acceptance work.
