"""Rebuild the private original-art sticker draft from retained imagegen output."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import sys
import uuid

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT.parents[2] / "tools"))
from daily_stickers import export_zip  # noqa: E402
from publish_factory_pack import validate_pack, validate_references  # noqa: E402
from reaction_media import compose, encode_static  # noqa: E402
from sticker_fonts import default_font  # noqa: E402

PACK = [
    ("meeting", "Ye meeting email ho sakti thi.", "meeting, work, annoyed", "thinking"),
    ("relief", "Call chhuti, jaan bachi!", "call, work, happy, relief", "happy"),
    ("messages", "Main 5 min offline kya hui?", "messages, work, surprise", "wow"),
]


def build() -> None:
    rendered = ROOT / "rendered"
    rendered.mkdir(exist_ok=True)
    records, references, previews = [], [], []
    for name, caption, tags, mood in PACK:
        source = ROOT / "sources" / f"{name}.png"
        with Image.open(source) as original:
            original.load()
            if original.getchannel("A").getextrema()[0] == 255:
                raise ValueError(f"{name} lost its transparent cutout")
            canvas = compose(original, caption, default_font(), fit="contain")
        data = encode_static(canvas)
        sticker_id = str(uuid.uuid5(uuid.NAMESPACE_URL, "stickerdock-original-workday:" + name))
        output = rendered / f"{sticker_id}.webp"
        output.write_bytes(data)
        records.append({"id": sticker_id, "file": str(output), "caption": caption,
                        "tags": tags, "mood": mood, "kind": "STATIC", "rating": "GENERAL",
                        "category": "WORK", "pack_title": "Workday Reactions"})
        references.append({"media_credit": {
            "title": f"Original fictional {name} reaction",
            "credit": "StickerDock original AI-assisted artwork",
            "license": "USER_OWNED",
            "source_url": "https://github.com/dvajjala-ui/stickerdock/blob/main/assets/drafts/workday-reactions/sources/" + name + ".png",
            "source_sha256": hashlib.sha256(source.read_bytes()).hexdigest()}})
        previews.append(canvas)
    export_zip(ROOT / "pack.zip", records, "2026-10-23-workday-original")
    (ROOT / "references.json").write_text(json.dumps(references, indent=2) + "\n", encoding="utf-8")
    validate_pack((ROOT / "pack.zip").read_bytes(), public=True)
    validate_references((ROOT / "references.json").read_bytes(), count=3)
    # View each rendered sticker at a realistic size on both chat backgrounds.
    sheet = Image.new("RGB", (3 * 160, 2 * 160), "#f2f2ef")
    draw = ImageDraw.Draw(sheet)
    draw.rectangle((0, 160, 480, 320), fill="#242a2b")
    for index, sticker in enumerate(previews):
        small = sticker.resize((128, 128), Image.Resampling.LANCZOS)
        for row in (0, 1):
            sheet.paste(small, (index * 160 + 16, row * 160 + 16), small)
    sheet.save(ROOT / "preview.png")
    print(json.dumps({"pack_bytes": (ROOT / "pack.zip").stat().st_size,
                      "sticker_bytes": [Path(item["file"]).stat().st_size for item in records]}))


if __name__ == "__main__":
    build()
