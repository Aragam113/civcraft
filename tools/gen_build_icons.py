"""Generate perk icons for the Builder menu: Smithy (anvil) + Sawmill (wood pile)."""
import os
import subprocess
import sys
from PIL import Image

SRC_DIR = os.path.join(os.path.dirname(__file__), "svg_src")
OUT_DIR = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                       "assets", "civcraft", "textures", "gui")

GOLD = (212, 175, 55)
OUTLINE = (60, 40, 10)

MAPPING = {
    "Name=GiAnvil.svg":    "icon_smithy",
    "Name=GiWoodPile.svg": "icon_sawmill",
}


def rasterize(svg_path, out_path, size=32):
    cmd = ["npx", "--yes", "svgexport", svg_path, out_path, f"{size}:{size}"]
    res = subprocess.run(cmd, shell=True, capture_output=True, text=True)
    if res.returncode != 0:
        print(res.stdout, res.stderr, file=sys.stderr)
        raise RuntimeError(f"svgexport failed for {svg_path}")


def retint(path):
    src = Image.open(path).convert("RGBA")
    w, h = src.size
    pix_src = src.load()
    outline = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    pix_out = outline.load()
    for y in range(h):
        for x in range(w):
            if pix_src[x, y][3] > 30:
                continue
            has = False
            for dy in (-1, 0, 1):
                for dx in (-1, 0, 1):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < w and 0 <= ny < h and pix_src[nx, ny][3] > 30:
                        has = True
                        break
                if has:
                    break
            if has:
                pix_out[x, y] = (*OUTLINE, 255)

    gold = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    pix_gold = gold.load()
    for y in range(h):
        for x in range(w):
            if pix_src[x, y][3] > 30:
                pix_gold[x, y] = (*GOLD, 255)
    Image.alpha_composite(outline, gold).save(path)


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    for svg, name in MAPPING.items():
        svg_path = os.path.join(SRC_DIR, svg)
        out_path = os.path.join(OUT_DIR, name + ".png")
        rasterize(svg_path, out_path, 32)
        retint(out_path)
        print(f"wrote {out_path}")


if __name__ == "__main__":
    main()
