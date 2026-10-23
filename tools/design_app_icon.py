"""Render small-size studies for a sticker-and-chat launcher mark.

This is a design review aid. It does not replace the shipping app icon.
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageOps

ROOT = Path(__file__).resolve().parents[1] / "assets" / "drafts" / "app-icon"
S = 10
BG = "#12130f"
PAPER = "#f6f7f1"
LIME = "#d9ff6a"


def box(draw, xy, radius, fill):
    draw.rounded_rectangle(tuple(v * S for v in xy), radius=radius * S, fill=fill)


def poly(draw, points, fill):
    draw.polygon([(x * S, y * S) for x, y in points], fill=fill)


def render(variant):
    image = Image.new("RGB", (108 * S, 108 * S), BG)
    draw = ImageDraw.Draw(image)
    if variant == "stacked":
        box(draw, (27, 20, 88, 81), 8, LIME)
        box(draw, (20, 27, 81, 88), 8, PAPER)
        poly(draw, [(65, 88), (81, 72), (81, 80), (73, 88)], LIME)
    elif variant == "single":
        box(draw, (21, 21, 87, 87), 8, PAPER)
        poly(draw, [(67, 87), (87, 67), (87, 79), (79, 87)], LIME)
    else:
        box(draw, (24, 23, 84, 83), 7, LIME)
        box(draw, (20, 30, 78, 88), 7, PAPER)
        poly(draw, [(62, 88), (78, 72), (78, 81), (71, 88)], LIME)
    if variant == "single":
        box(draw, (33, 38, 75, 65), 9, BG)
        poly(draw, [(39, 61), (35, 73), (48, 64)], BG)
        poly(draw, [(43, 51), (54, 41), (54, 47), (67, 47), (56, 60), (56, 54)], LIME)
    else:
        box(draw, (31, 40, 70, 65), 8, BG)
        poly(draw, [(38, 61), (34, 72), (47, 64)], BG)
        poly(draw, [(41, 52), (52, 42), (52, 48), (64, 48), (53, 60), (53, 54)], LIME)
    return image.resize((1024, 1024), Image.Resampling.LANCZOS)


def main():
    ROOT.mkdir(parents=True, exist_ok=True)
    names = ("stacked", "single", "offset")
    sheet = Image.new("RGB", (3 * 280, 180), "#e8e8e4")
    draw = ImageDraw.Draw(sheet)
    for index, name in enumerate(names):
        icon = render(name)
        icon.save(ROOT / f"{name}.png")
        large = icon.resize((128, 128), Image.Resampling.LANCZOS)
        small = icon.resize((48, 48), Image.Resampling.LANCZOS)
        sheet.paste(large, (index * 280 + 16, 20))
        sheet.paste(small, (index * 280 + 173, 60))
        draw.text((index * 280 + 16, 155), name, fill=BG)
    sheet.save(ROOT / "comparison.png")


if __name__ == "__main__":
    main()
