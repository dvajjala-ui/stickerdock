# iPhone photo archive import

Candidate iPhone 0.10.2/build 14 extends **Import sticker ZIP** to archives of
PNG, JPG/JPEG and WebP images. Choose a ZIP in Files and explicitly import it;
the existing cancellable checking operation converts on a detached worker.
It does not scan other apps, upload media or add packs on startup.

PNG/JPEG use the existing native static-photo renderer/encoder: preserve alpha,
apply EXIF orientation, fit within the 512px transparent canvas and enforce the
100 KB WebP limit. ImageIO probes reject unsupported types and animated images
disguised as photos, with a direction to Create's moving-media flow. Existing
animated WebP is validated and retains exact bytes. Static and animated outputs
are separate, at most 30 stickers per pack; normalized duplicates are skipped.

Bounds remain explicit: 50 MB compressed/expanded archive, 3,001 paths, no
traversal/duplicates/symlinks, 500 KB WebP entries, 20 MB per photo, at most 20
million source pixels with dimensions no greater than 16,000. Native decode
downsamples to 1,600px before rendering. Retained converted media also has a
50 MB total limit. Every entry's CRC is checked. All validation/conversion
finishes before the local store commits anything, so a bad later photo cannot
leave earlier images partially imported. Cancellation remains checked during
reading/conversion/encoding.

StickerDock manifests retain their strict schema 1–5 contract and exact saved
media; malformed manifests never fall back to generic photo conversion. Generic
archives have no private rating/category metadata and default to GENERAL local
packs. This does not imply public sharing. Generic EXIF pack-name recovery and
.wastickers metadata/tray ingestion remain separate parity work.

Four native regression tests cover PNG transparency, uppercase PNG/JPEG, native
conversion/dedup with byte-preserved animation, local store/restart/portable
export, EXIF-rotated JPEG geometry, a bad later photo preserving the existing
library and a renamed GIF being rejected instead of flattened. Apple build and
simulator acceptance must pass on this candidate before merge; Windows source
inspection is not an Apple compile. Signing/physical iPhone/WhatsApp remain open.
