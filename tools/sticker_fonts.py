"""Resolve local fonts without downloading assets or changing Linux defaults."""
import os
from pathlib import Path


def default_font():
    configured = os.environ.get("STICKERDOCK_FONT")
    if configured:
        return Path(configured)
    linux = Path("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf")
    if linux.is_file() or os.name != "nt":
        return linux
    return Path(os.environ.get("WINDIR", "C:/Windows")) / "Fonts" / "arialbd.ttf"
