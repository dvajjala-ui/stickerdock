"""Local GIF/short-clip conversion, no cloud provider or video-generation fees."""
from __future__ import annotations

import argparse
import io
from pathlib import Path
import subprocess
import tempfile

from PIL import Image

CLIP_EXTENSIONS = {".gif", ".mp4", ".webm", ".mov"}


def clip_frames(path):
    path = Path(path).resolve()
    if path.suffix.lower() not in CLIP_EXTENSIONS or not path.is_file() or path.stat().st_size > 20_000_000:
        raise ValueError("Supply a local GIF/MP4/WebM/MOV under 20MB")
    # Protocol whitelist prevents a playlist/container from fetching remote URLs.
    with tempfile.TemporaryDirectory(prefix="stickerdock-frames-") as temporary:
        args = ["ffmpeg", "-nostdin", "-hide_banner", "-loglevel", "error",
                "-protocol_whitelist", "file,pipe", "-threads", "1", "-i", str(path),
                "-t", "2", "-an", "-vf", "fps=8,scale=430:310:force_original_aspect_ratio=decrease",
                "-frames:v", "16", "-threads", "1", str(Path(temporary) / "%03d.png")]
        try:
            subprocess.run(args, check=True, capture_output=True, timeout=30)
        except (OSError, subprocess.SubprocessError):
            raise ValueError("Clip decoding failed; install FFmpeg and check the media") from None
        frames = []
        for frame in sorted(Path(temporary).glob("*.png")):
            with Image.open(frame) as image:
                frames.append(image.convert("RGBA"))
        if len(frames) < 2:
            raise ValueError("Clip must contain at least two decodable frames")
        return frames


def render_clip(path, idea, font_path):
    return render_clip_frames(clip_frames(path), idea, font_path)


def render_clip_frames(originals, idea, font_path):
    from reaction_media import compose, encode_animation
    if not 2 <= len(originals) <= 16:
        raise ValueError("Supply 2–16 decoded clip frames")
    frames = [compose(original, idea["caption"], font_path, fit="contain") for original in originals]
    return encode_animation(frames, fps=8)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--caption", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--font", type=Path, default=Path("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"))
    args = parser.parse_args()
    if not 1 <= len(args.caption) <= 48 or any(ord(c) < 32 or ord(c) > 126 for c in args.caption):
        parser.error("Caption must be 1–48 printable ASCII characters")
    data = render_clip(args.input, {"caption": args.caption}, args.font)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_bytes(data)
    print(f"Created animated WebP: {len(data)} bytes")
