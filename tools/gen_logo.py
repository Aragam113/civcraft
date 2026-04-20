"""Generate CivCraft title logo PNGs matching vanilla Minecraft texture dimensions.

minecraft.png: 1024x256 — main logo. Vanilla engine samples this atlas by letter;
               we repaint the whole canvas with 'CIVCRAFT' so whatever UV slices
               the engine takes will land on our letters.
edition.png:   512x64  — subtitle under the logo; repainted as 'STRATEGY EDITION'.
"""
from PIL import Image, ImageDraw, ImageFont
import os, sys

OUT_DIR = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                       "assets", "minecraft", "textures", "gui", "title")
os.makedirs(OUT_DIR, exist_ok=True)


def _find_font(size, bold=True):
    candidates = [
        r"C:\Windows\Fonts\trajanpro-bold.ttf",
        r"C:\Windows\Fonts\timesbd.ttf",
        r"C:\Windows\Fonts\georgiab.ttf",
        r"C:\Windows\Fonts\constanb.ttf",
        r"C:\Windows\Fonts\impact.ttf",
        r"C:\Windows\Fonts\arialbd.ttf",
        r"C:\Windows\Fonts\arial.ttf",
    ]
    for path in candidates:
        if os.path.exists(path):
            try:
                return ImageFont.truetype(path, size)
            except Exception:
                continue
    return ImageFont.load_default()


def _draw_text_centered(img, text, font, fill, stroke_fill=None, stroke_width=0):
    draw = ImageDraw.Draw(img)
    bbox = draw.textbbox((0, 0), text, font=font, stroke_width=stroke_width)
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    x = (img.width - tw) // 2 - bbox[0]
    y = (img.height - th) // 2 - bbox[1]
    draw.text((x, y), text, font=font, fill=fill,
              stroke_fill=stroke_fill, stroke_width=stroke_width)


def build_main():
    W, H = 1024, 256
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))

    # Pick the largest font that fits 'CIVCRAFT' comfortably inside the canvas.
    text = "CIVCRAFT"
    font = None
    for size in range(230, 80, -5):
        font = _find_font(size)
        bbox = ImageDraw.Draw(img).textbbox((0, 0), text, font=font, stroke_width=6)
        if (bbox[2] - bbox[0]) <= W - 40 and (bbox[3] - bbox[1]) <= H - 20:
            break

    # Imperial palette: deep wine red fill + gold stroke.
    _draw_text_centered(img, text, font,
                        fill=(212, 175, 55, 255),        # gold
                        stroke_fill=(80, 8, 8, 255),     # dark wine
                        stroke_width=6)
    path = os.path.join(OUT_DIR, "minecraft.png")
    img.save(path)
    print(f"wrote {path} ({W}x{H})")


def build_edition():
    W, H = 512, 64
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    text = "STRATEGY EDITION"
    font = None
    for size in range(60, 20, -2):
        font = _find_font(size)
        bbox = ImageDraw.Draw(img).textbbox((0, 0), text, font=font, stroke_width=2)
        if (bbox[2] - bbox[0]) <= W - 20 and (bbox[3] - bbox[1]) <= H - 6:
            break
    _draw_text_centered(img, text, font,
                        fill=(212, 175, 55, 255),
                        stroke_fill=(40, 4, 4, 255),
                        stroke_width=2)
    path = os.path.join(OUT_DIR, "edition.png")
    img.save(path)
    print(f"wrote {path} ({W}x{H})")


if __name__ == "__main__":
    build_main()
    build_edition()
