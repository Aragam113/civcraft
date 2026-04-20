"""Rasterize the Figma-exported game-icons SVGs via `npx svgexport` and retint
the (black) silhouette into the CivCraft gold palette, saving the result as
32×32 PNGs alongside the other UI textures.

The SVGs are monochrome so we pick every pixel by alpha, then replace the RGB
with a solid gold tone. A subtle darker outline is added by blitting the same
silhouette shifted 1 px in two directions before the main pass.
"""
import os
import subprocess
import sys
from PIL import Image

SRC_DIR = os.path.expanduser(r"C:\Users\fajar\AppData\Local\Temp\game_icons")
OUT_DIR = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                      "assets", "civcraft", "textures", "gui")

# svg_filename -> output texture name (without extension)
MAPPING = {
    "Name=GiStoneThrone.svg":       "icon_town_hall",
    "Name=GiConqueror.svg":         "icon_spawn_settlers",
    # Optional extras we may wire up later:
    "Name=GiStoneTower.svg":        "icon_tower",
    "Name=GiAnvil.svg":             "icon_anvil",
    "Name=GiBlackFlag.svg":         "icon_flag",
    "Name=GiScrollUnfurled.svg":    "icon_scroll",
    "Name=GiFastForwardButton.svg": "icon_next",
    "Name=GiFastBackwardButton.svg": "icon_prev",
}

GOLD = (212, 175, 55)
OUTLINE = (60, 40, 10)


def rasterize(svg_path, out_path, size=32):
    """Run svgexport to convert SVG to a transparent PNG at the given size."""
    cmd = ["npx", "--yes", "svgexport", svg_path, out_path, f"{size}:{size}"]
    # shell=True lets Windows locate npx.cmd without needing its full path.
    res = subprocess.run(cmd, shell=True, capture_output=True, text=True)
    if res.returncode != 0:
        print(res.stdout, res.stderr, file=sys.stderr)
        raise RuntimeError(f"svgexport failed for {svg_path}")


def retint(path):
    """Replace every opaque pixel with gold, and add a 1-px dark outline by
    overlaying an alpha-expanded copy underneath."""
    src = Image.open(path).convert("RGBA")
    w, h = src.size
    # Outline layer = pixels that have any neighbor with alpha > 0.
    outline = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    pix_src = src.load()
    pix_out = outline.load()
    for y in range(h):
        for x in range(w):
            a = pix_src[x, y][3]
            if a > 30: continue
            neighbor = False
            for dy in (-1, 0, 1):
                for dx in (-1, 0, 1):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < w and 0 <= ny < h:
                        if pix_src[nx, ny][3] > 30:
                            neighbor = True; break
                if neighbor: break
            if neighbor:
                pix_out[x, y] = (*OUTLINE, 255)

    # Gold main layer — preserve alpha from source, replace RGB.
    gold_layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    pix_gold = gold_layer.load()
    for y in range(h):
        for x in range(w):
            a = pix_src[x, y][3]
            if a > 30:
                pix_gold[x, y] = (*GOLD, 255)

    out = Image.alpha_composite(outline, gold_layer)
    out.save(path)


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    for svg, out_name in MAPPING.items():
        svg_path = os.path.join(SRC_DIR, svg)
        if not os.path.exists(svg_path):
            print(f"skip missing {svg}")
            continue
        out_path = os.path.join(OUT_DIR, out_name + ".png")
        rasterize(svg_path, out_path, 32)
        retint(out_path)
        print(f"wrote {out_path}")


if __name__ == "__main__":
    main()
