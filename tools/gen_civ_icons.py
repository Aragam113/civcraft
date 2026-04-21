"""Civ-style resource + building icons. Writes into textures/gui as pixel-art PNGs."""
import os
import subprocess
import sys
from PIL import Image

SRC_DIR = os.path.join(os.path.dirname(__file__), "svg_src")
OUT_DIR = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                       "assets", "civcraft", "textures", "gui")

# (svg, out_name, fill_rgb, outline_rgb)
MAPPING = [
    ("Name=GiBread.svg",           "res_food",        (214, 160,  82), (70, 40, 15)),
    ("Name=GiAnvil.svg",           "res_production",  (180, 180, 200), (55, 55, 70)),
    ("Name=GiGoldBar.svg",         "res_gold",        (212, 175,  55), (70, 50, 10)),
    ("Name=GiAtomCore.svg",        "res_science",     ( 90, 200, 230), (20, 60, 90)),
    ("Name=GiScrollUnfurled.svg",  "res_culture",     (215, 180, 130), (75, 50, 20)),
    ("Name=GiBarn.svg",            "icon_storehouse", (160, 110,  65), (55, 35, 15)),
    ("Name=GiStoneBlock.svg",      "icon_quarry",     (170, 170, 170), (60, 60, 60)),
]


def rasterize(svg_path, out_path, size=32):
    cmd = ["npx", "--yes", "svgexport", svg_path, out_path, f"{size}:{size}"]
    res = subprocess.run(cmd, shell=True, capture_output=True, text=True)
    if res.returncode != 0:
        print(res.stdout, res.stderr, file=sys.stderr)
        raise RuntimeError(f"svgexport failed for {svg_path}")


def retint(path, fill, outline):
    src = Image.open(path).convert("RGBA")
    w, h = src.size
    pix_src = src.load()
    outline_layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    pix_out = outline_layer.load()
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
                pix_out[x, y] = (*outline, 255)

    fill_layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    pix_fill = fill_layer.load()
    for y in range(h):
        for x in range(w):
            if pix_src[x, y][3] > 30:
                pix_fill[x, y] = (*fill, 255)
    out = Image.alpha_composite(outline_layer, fill_layer)
    out.save(path)


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    for svg, name, fill, outline in MAPPING:
        svg_path = os.path.join(SRC_DIR, svg)
        if not os.path.exists(svg_path):
            print(f"skip missing {svg}")
            continue
        out_path = os.path.join(OUT_DIR, name + ".png")
        rasterize(svg_path, out_path, 32)
        retint(out_path, fill, outline)
        print(f"wrote {out_path}")


if __name__ == "__main__":
    main()
