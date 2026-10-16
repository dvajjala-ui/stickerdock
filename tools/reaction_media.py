"""Local reaction-photo/video renderer. No API calls, uploads or generated faces.

Keeps the supplied media prominent with outlined meme captions rather than the
Factory's legacy emoji/card composition. Native-app creation integration is a
separate consumer of this media contract; this module is not a phone UI.
"""
from __future__ import annotations

import io
import math
from pathlib import Path
import subprocess
import tempfile
import unicodedata

from PIL import Image, ImageChops, ImageDraw, ImageFont, ImageOps

MAX_SOURCE_BYTES = 20_000_000
MAX_PIXELS = 20_000_000
MAX_ANIMATED_BYTES = 500_000
MAX_STATIC_BYTES = 100_000


def caption_text(value):
    if not isinstance(value, str) or any(unicodedata.category(c) == "Cc" for c in value):
        raise ValueError("Use a caption without control characters")
    result = " ".join(value.split())
    if not 1 <= len(result) <= 80:
        raise ValueError("Use a caption of 1–80 characters")
    return result


def local_source(path):
    path = Path(path).resolve()
    if not path.is_file() or not 12 <= path.stat().st_size <= MAX_SOURCE_BYTES:
        raise ValueError("Choose a local image or clip up to 20 MB")
    return path


def read_photo(path):
    path = local_source(path)
    with Image.open(path) as image:
        if image.width * image.height > MAX_PIXELS:
            raise ValueError("Photo exceeds the 20 megapixel limit")
        if getattr(image, "n_frames", 1) > 1:
            raise ValueError("Use the animated creator for moving images")
        return ImageOps.exif_transpose(image).convert("RGBA")


def decode_clip(path, start_ms=0, duration_ms=3000, fps=10):
    path = local_source(path)
    if path.suffix.lower() not in {".gif", ".mp4", ".webm", ".mov"}:
        raise ValueError("Choose GIF, MP4, WebM or MOV")
    if type(start_ms) is not int or not 0 <= start_ms <= 600_000:
        raise ValueError("Clip start must be within ten minutes")
    if type(duration_ms) is not int or not 500 <= duration_ms <= 4000:
        raise ValueError("Choose a 0.5–4 second reaction")
    if type(fps) is not int or not 6 <= fps <= 15:
        raise ValueError("Choose 6–15 frames per second")
    with tempfile.TemporaryDirectory(prefix="stickerdock-reaction-") as temporary:
        # Selected local inputs cannot fetch a playlist's remote dependencies.
        # Force the libvpx decoder first for WebM, preserving VP9 alpha. VP8
        # inputs fail that attempt and use FFmpeg's ordinary decoder.
        decoders = (["-c:v", "libvpx-vp9"], []) if path.suffix.lower() == ".webm" else ([],)
        for decoder in decoders:
            args = ["ffmpeg", "-nostdin", "-hide_banner", "-loglevel", "error", "-y",
                    "-protocol_whitelist", "file,pipe", "-threads", "1", *decoder,
                    "-ss", str(start_ms / 1000), "-i", str(path), "-t", str(duration_ms / 1000),
                    "-an", "-vf", f"fps={fps},scale=512:512:force_original_aspect_ratio=decrease",
                    "-frames:v", str((duration_ms * fps + 999) // 1000), "-threads", "1",
                    str(Path(temporary) / "%03d.png")]
            try:
                subprocess.run(args, check=True, capture_output=True, timeout=30)
                break
            except subprocess.CalledProcessError:
                for output in Path(temporary).glob("*.png"):
                    output.unlink()
            except (OSError, subprocess.TimeoutExpired):
                raise ValueError("Clip conversion failed or timed out; check FFmpeg and the media") from None
        else:
            raise ValueError("Could not decode this clip")
        frames = []
        for output in sorted(Path(temporary).glob("*.png")):
            with Image.open(output) as image:
                frames.append(image.convert("RGBA"))
        if len(frames) < 2:
            raise ValueError("Choose a section containing at least two frames")
        return frames


def _lines(text, font, draw):
    lines, current = [], ""
    for character in text:
        candidate = current + character
        box = draw.textbbox((0, 0), candidate, font=font, stroke_width=3)
        if box[2] - box[0] > 464:
            if not current:
                return None
            split = current.rfind(" ")
            if split > 0:
                lines.append(current[:split]); current = current[split + 1:] + character
            else:
                lines.append(current); current = character
        else:
            current = candidate
    if current.strip():
        lines.append(current.strip())
    return lines if len(lines) <= 3 else None


def focal_point(value):
    if not isinstance(value, (list, tuple)) or len(value) != 2 or any(
            type(v) not in (int, float) or not math.isfinite(v) or not 0 <= v <= 1 for v in value):
        raise ValueError("Choose horizontal/vertical focus between 0 and 1")
    return tuple(float(v) for v in value)


def compose(image, caption, font_path, fit="cover", position="bottom", focus=(.5, .5)):
    text = caption_text(caption)
    focus = focal_point(focus)
    if fit not in {"cover", "contain"} or position not in {"top", "bottom"}:
        raise ValueError("Choose cover/contain and top/bottom caption placement")
    if image.width * image.height > MAX_PIXELS:
        raise ValueError("Image exceeds pixel limit")
    image = image.convert("RGBA")
    if fit == "cover":
        # Keep a small transparent margin and white edge so rectangular photo
        # reactions still read against both light and dark chat backgrounds.
        canvas = Image.new("RGBA", (512, 512))
        ImageDraw.Draw(canvas).rounded_rectangle((8, 8, 503, 503), radius=32, fill="white")
        fitted = ImageOps.fit(image, (484, 484), method=Image.Resampling.LANCZOS, centering=focus)
        mask = Image.new("L", fitted.size)
        ImageDraw.Draw(mask).rounded_rectangle((0, 0, 483, 483), radius=26, fill=255)
        fitted.putalpha(ImageChops.multiply(fitted.getchannel("A"), mask))
        canvas.alpha_composite(fitted, (14, 14))
    else:
        canvas = Image.new("RGBA", (512, 512))
        fitted = ImageOps.contain(image, (488, 488), method=Image.Resampling.LANCZOS)
        canvas.alpha_composite(fitted, ((512 - fitted.width) // 2, (512 - fitted.height) // 2))
    draw = ImageDraw.Draw(canvas)
    for size in range(42, 19, -2):
        font = ImageFont.truetype(str(font_path), size)
        lines = _lines(text, font, draw)
        if lines:
            break
    else:
        raise ValueError("Shorten the caption to keep it readable")
    line_height = size + 10
    height = len(lines) * line_height
    y = 20 if position == "top" else 492 - height
    for line in lines:
        draw.text((256, y), line, font=font, anchor="mt", fill="white", stroke_width=3, stroke_fill="black")
        y += line_height
    return canvas


def encode_static(image):
    if image.size != (512, 512):
        raise ValueError("Static stickers must be 512px square")
    for quality in (92, 80, 65, 50, 35):
        output = io.BytesIO()
        image.save(output, format="WEBP", quality=quality, method=4)
        if output.tell() <= MAX_STATIC_BYTES:
            return output.getvalue()
    raise ValueError("Image is too detailed; choose a simpler crop")


def encode_animation(frames, fps):
    if not 2 <= len(frames) <= 60 or type(fps) is not int or not 6 <= fps <= 15:
        raise ValueError("Supply 2–60 frames at 6–15 fps")
    if any(frame.size != (512, 512) for frame in frames):
        raise ValueError("Animation frames must be 512px square")
    duration = round(1000 / fps)
    if duration * len(frames) > 10_000:
        raise ValueError("Animation must not exceed ten seconds")
    for quality in (85, 70, 55, 40):
        output = io.BytesIO()
        frames[0].save(output, format="WEBP", save_all=True, append_images=frames[1:],
                       duration=duration, loop=0, quality=quality, method=4)
        data = output.getvalue()
        with Image.open(io.BytesIO(data)) as check:
            if getattr(check, "n_frames", 1) < 2:
                raise ValueError("This section has no visible motion; save it as a static sticker")
        if len(data) <= MAX_ANIMATED_BYTES:
            return data
    raise ValueError("Reaction is too detailed; shorten the clip or choose fewer frames per second")
