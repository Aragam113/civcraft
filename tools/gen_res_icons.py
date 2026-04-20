"""Rasterize resource-bar SVGs to 32x32 pixel-art PNGs with per-resource tints.

Uses npx svgexport (same as svg_to_icons.py) to go SVG -> PNG, then applies a
flat color + 1-px dark outline in PIL. Each resource gets a distinct color so
icons are readable at a glance on the top HUD bar.
"""
import os
import subprocess
import sys
from PIL import Image

SRC_DIR = os.path.join(os.path.dirname(__file__), "svg_src")
OUT_DIR = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                       "assets", "civcraft", "textures", "gui")

# svg_filename -> (output_name, fill_rgb, outline_rgb)
MAPPING = {
    "Name=GiBread.svg":     ("res_food",  (214, 160,  82), (70, 40, 15)),
    "Name=GiLog.svg":       ("res_wood",  (150,  90,  40), (55, 30, 10)),
    "Name=GiStonePile.svg": ("res_stone", (180, 180, 180), (60, 60, 60)),
    "Name=GiCoalPile.svg":  ("res_coal",  ( 40,  40,  45), (180, 180, 180)),
    "Name=GiMetalBar.svg":  ("res_iron",  (205, 205, 220), (60, 65, 80)),
    "Name=GiGoldBar.svg":   ("res_gold",  (212, 175,  55), (70, 50, 10)),
}


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
            has_neighbor = False
            for dy in (-1, 0, 1):
                for dx in (-1, 0, 1):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < w and 0 <= ny < h and pix_src[nx, ny][3] > 30:
                        has_neighbor = True
                        break
                if has_neighbor:
                    break
            if has_neighbor:
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
    for svg, (name, fill, outline) in MAPPING.items():
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
