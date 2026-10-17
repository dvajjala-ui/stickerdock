"""Build explicit local static/animated packs from supplied media and captions.

Example: python tools/build_reaction_pack.py --input clip.webm --captions captions.json
 --credit credit.json --font path/to/bold.ttf --output work/my-reactions
No upload, publication or AI request. Credits remain outside the native archive
because its strict backup format only allows manifest-declared sticker files.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import uuid
import zipfile

from PIL import Image

from reaction_media import caption_text, compose, decode_clip, encode_animation, encode_static, focal_point, read_photo
from sticker_fonts import default_font


def build(source, captions, credit, output, font, start_ms=0, duration_ms=3000, fps=10, fit="cover", title="My reactions", focus=(.5, .5)):
    focus = focal_point(focus)
    if not isinstance(title, str) or not title.strip() or len(title) > 45 or any(ord(c) < 32 for c in title):
        raise ValueError("Use a pack title of 1–45 characters without control characters")
    if not isinstance(captions, list):
        raise ValueError("Supply a JSON list of captions")
    captions = [caption_text(caption) for caption in captions]
    if not 3 <= len(captions) <= 30 or len(set(captions)) != len(captions):
        raise ValueError("Supply 3–30 distinct captions for a usable pack")
    if not isinstance(credit, dict) or not all(isinstance(credit.get(key), str) and credit[key].strip()
                                            for key in ("title", "author", "source_url", "license", "license_url")):
        raise ValueError("Supply source title, author, source_url, license and license_url")
    source, output = Path(source).resolve(), Path(output).resolve()
    if output.exists():
        raise ValueError("Choose a new output folder; existing outputs are never replaced")
    moving = source.suffix.lower() in {".mp4", ".webm", ".mov", ".gif"}
    originals = decode_clip(source, start_ms, duration_ms, fps) if moving else [read_photo(source)]
    output.mkdir(parents=True)
    credit = {**credit, "source_sha256": hashlib.sha256(source.read_bytes()).hexdigest(),
              "framing": {"fit": fit, "focus_x": focus[0], "focus_y": focus[1]},
              "changes": "Trimmed, cropped/fitted to 512px, overlaid original captions, converted to WebP; audio removed.",
              "source_uploaded": False, "public_catalog_published": False}
    (output / "credits.json").write_text(json.dumps(credit, ensure_ascii=False, indent=2), encoding="utf-8")
    report = {"source": credit, "artifacts": [], "sticker_quality_approved": False}
    for kind in (["STATIC", "ANIMATED"] if moving else ["STATIC"]):
        pack_id = str(uuid.uuid4())
        pack = {"id": pack_id, "name": f"{title.strip()} · {kind.title()}", "kind": kind,
                "rating": "GENERAL", "category": "GENERAL", "stickers": []}
        media = {}
        for index, caption in enumerate(captions):
            frames = [compose(frame, caption, font, fit, focus=focus) for frame in originals] if kind == "ANIMATED" else [
                compose(originals[len(originals) // 2], caption, font, fit, focus=focus)]
            data = encode_animation(frames, fps) if kind == "ANIMATED" else encode_static(frames[0])
            name = str(uuid.uuid4()) + ".webp"
            media[f"packs/{pack_id}/{name}"] = data
            pack["stickers"].append({"name": name, "sha256": hashlib.sha256(data).hexdigest(),
                                     "label": caption, "tags": "reaction, meme, real media"})
            path = output / f"{kind.lower()}-{index + 1}.webp"
            path.write_bytes(data)
            with Image.open(path) as checked:
                report["artifacts"].append({"file": path.name, "kind": kind, "bytes": len(data),
                                            "frames": getattr(checked, "n_frames", 1), "size": checked.size})
            if index == 0:
                frames[0].save(output / f"{kind.lower()}-preview.png")
        archive = output / f"real-reactions-{kind.lower()}.zip"
        with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as zipped:
            zipped.writestr("manifest.json", json.dumps({"schema": 5, "packs": [pack]}, ensure_ascii=False))
            for name, data in media.items():
                zipped.writestr(name, data)
    (output / "README.txt").write_text(
        f"{credit['title']} — {credit['author']}\n{credit['source_url']}\n"
        f"License: {credit['license']}\n{credit['license_url']}\n{credit['changes']}\n\n"
        "Derivative sticker media carries the source license; StickerDock source code is separate.\n"
        "Keep these credits with the media. Import the inner static/animated ZIP separately in StickerDock.\n"
        "This local trial is not approved for automatic public publication.\n", encoding="utf-8")
    (output / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--captions", type=Path, required=True)
    parser.add_argument("--credit", type=Path, required=True)
    parser.add_argument("--font", type=Path, default=default_font())
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--start-ms", type=int, default=0)
    parser.add_argument("--duration-ms", type=int, default=3000)
    parser.add_argument("--fps", type=int, default=10)
    parser.add_argument("--fit", choices=("cover", "contain"), default="cover")
    parser.add_argument("--name", default="My reactions")
    parser.add_argument("--focus-x", type=float, default=.5, help="Horizontal crop anchor: 0 left, 1 right")
    parser.add_argument("--focus-y", type=float, default=.5, help="Vertical crop anchor: 0 top, 1 bottom")
    args = parser.parse_args()
    result = build(args.input, json.loads(args.captions.read_text(encoding="utf-8")),
                   json.loads(args.credit.read_text(encoding="utf-8")), args.output, args.font,
                   args.start_ms, args.duration_ms, args.fps, args.fit, args.name, (args.focus_x, args.focus_y))
    print(json.dumps({"created": len(result["artifacts"]), "output": str(args.output), "uploaded": False}))
