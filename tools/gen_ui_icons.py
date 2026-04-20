"""Pixel-art UI sprites for CivCraft:

  - planet spin sheet: 8 frames × 32×32 laid out horizontally (256×32). The
    "rotation" is a simple horizontal wrap of the continent layer inside a
    circular ocean mask — it looks like spinning Earth without needing real
    3D projection.
  - perk icons: 32×32 pixel tiles for each selectable perk action.

Everything is generated with plain pixel ops in Pillow so it stays reproducible
and we never download images at build time.
"""
from PIL import Image, ImageDraw
import os
import random

OUT_DIR = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                      "assets", "civcraft", "textures", "gui")
os.makedirs(OUT_DIR, exist_ok=True)

PLANET_SIZE = 32
PLANET_FRAMES = 8


def disc_mask(size):
    """Return a size×size bool array, True inside the filled pixel disc."""
    r = size / 2.0
    cx = cy = r - 0.5
    mask = [[False] * size for _ in range(size)]
    for y in range(size):
        for x in range(size):
            dx = x - cx
            dy = y - cy
            if dx * dx + dy * dy <= (r - 0.5) ** 2:
                mask[y][x] = True
    return mask


def make_continent_layer():
    """Deterministic pseudo-continent bitmap of width = 2*PLANET_SIZE (so it can
    wrap around). Mostly sea, with a handful of green/brown blobs."""
    random.seed(1337)
    w = PLANET_SIZE * 2
    h = PLANET_SIZE
    layer = [[None] * w for _ in range(h)]
    blobs = [
        (8, 10, 5, (40, 140, 40)),
        (16, 18, 3, (60, 160, 60)),
        (28, 8, 4, (50, 150, 50)),
        (38, 20, 5, (40, 130, 40)),
        (50, 14, 3, (60, 170, 70)),
        (56, 22, 4, (50, 150, 50)),
    ]
    for bx, by, br, color in blobs:
        for dy in range(-br, br + 1):
            for dx in range(-br, br + 1):
                if dx * dx + dy * dy <= br * br:
                    x = (bx + dx) % w
                    y = by + dy
                    if 0 <= y < h:
                        layer[y][x] = color
        # Noisy coastline pixels
        for _ in range(br * 3):
            ox = bx + random.randint(-br - 1, br + 1)
            oy = by + random.randint(-br - 1, br + 1)
            if 0 <= oy < h:
                layer[oy][ox % w] = (30, 100, 30)
    return layer


def ocean_color(x, y, r, cx, cy):
    """Slight vertical shading inside the disc to feel 3D."""
    # Darker near edges, lighter near top-left (fake sun-lit hemisphere).
    dx = (x - cx) / r
    dy = (y - cy) / r
    highlight = max(0.0, -dx * 0.4 - dy * 0.4)
    base = (40, 100, 180)
    bright = (80, 170, 230)
    r_c = int(base[0] + (bright[0] - base[0]) * highlight)
    g_c = int(base[1] + (bright[1] - base[1]) * highlight)
    b_c = int(base[2] + (bright[2] - base[2]) * highlight)
    return (r_c, g_c, b_c, 255)


def make_planet_frame(offset, continents, mask):
    size = PLANET_SIZE
    r = size / 2.0
    cx = cy = r - 0.5
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    pix = img.load()
    for y in range(size):
        for x in range(size):
            if not mask[y][x]:
                continue
            src_x = (x + offset) % (size * 2)
            cont = continents[y][src_x]
            if cont is None:
                pix[x, y] = ocean_color(x, y, r, cx, cy)
            else:
                pix[x, y] = (*cont, 255)
    # Darken rim pixels for a round "edge" look
    for y in range(size):
        for x in range(size):
            if not mask[y][x]:
                continue
            if (x == 0 or x == size - 1 or y == 0 or y == size - 1
                    or not mask[max(0, y - 1)][x] or not mask[min(size - 1, y + 1)][x]
                    or not mask[y][max(0, x - 1)] or not mask[y][min(size - 1, x + 1)]):
                r0, g0, b0, a0 = pix[x, y]
                pix[x, y] = (r0 // 2, g0 // 2, b0 // 2, 255)
    return img


def build_planet_sheet():
    mask = disc_mask(PLANET_SIZE)
    continents = make_continent_layer()
    sheet = Image.new("RGBA", (PLANET_SIZE * PLANET_FRAMES, PLANET_SIZE), (0, 0, 0, 0))
    for i in range(PLANET_FRAMES):
        offset = int(i * (PLANET_SIZE * 2) / PLANET_FRAMES)
        frame = make_planet_frame(offset, continents, mask)
        sheet.paste(frame, (i * PLANET_SIZE, 0))
    path = os.path.join(OUT_DIR, "planet.png")
    sheet.save(path)
    print(f"wrote {path} ({sheet.size})")


# --- Perk icons ---------------------------------------------------------------

def save_icon(name, pixels):
    """pixels: list of strings (32 chars × 32 rows), palette below."""
    palette = {
        ".": (0, 0, 0, 0),
        "#": (26, 14, 4, 255),        # dark wood
        "G": (212, 175, 55, 255),     # gold
        "g": (150, 110, 30, 255),     # dark gold
        "B": (60, 40, 20, 255),       # brown
        "R": (138, 58, 58, 255),      # roof red
        "W": (242, 235, 200, 255),    # ivory
        "C": (92, 200, 255, 255),     # window glass
        "s": (100, 100, 100, 255),    # stone shadow
        "S": (160, 160, 160, 255),    # stone light
        "p": (220, 180, 140, 255),    # skin
        "h": (80, 40, 10, 255),       # hair
        "c": (160, 40, 40, 255),      # cloak red
        "k": (40, 40, 40, 255),       # dark outline
    }
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    pix = img.load()
    for y, row in enumerate(pixels):
        for x, ch in enumerate(row):
            pix[x, y] = palette.get(ch, palette["."])
    path = os.path.join(OUT_DIR, f"icon_{name}.png")
    img.save(path)
    print(f"wrote {path}")


def build_town_hall_icon():
    # Classic RTS-style fortified hall: central keep + two side towers,
    # gold roofs with flags, arched door, small windows.
    rows = [
        "................................",
        "................................",
        "...............k................",
        "..............kGk...............",
        "..............kGk...............",
        "..............kGk...............",
        "........k.....kGk.....k.........",
        ".......kGk...kGGGk...kGk........",
        ".......kGk...kGGGk...kGk........",
        "......kGGGk..kGGGk..kGGGk.......",
        "......kGGGk.kGGGGGk.kGGGk.......",
        ".....kGGGGGkkGGGGGkkGGGGGk......",
        ".....kRRRRRkkRRRRRkkRRRRRk......",
        "....kRRRRRRkkRRRRRkkRRRRRRk.....",
        "....kSSSSSSSSSSSSSSSSSSSSSSk....",
        "....kSSSSSSSSSSSSSSSSSSSSSSk....",
        "....kSSCCSSSSSSSSSSSSSSCCSSk....",
        "....kSSCCSSSSSSSSSSSSSSCCSSk....",
        "....kSSSSSSkkSSSSSSkkSSSSSSSk...",
        "....kSSSSSSkkSSSSSSkkSSSSSSSk...",
        "....kSSCCSSSSSBBBSSSSSSCCSSSk...",
        "....kSSCCSSSSSBBBSSSSSSCCSSSk...",
        "....kSSSSSSSSSBBBSSSSSSSSSSSk...",
        "....kSSSSSSSSSBBBSSSSSSSSSSSk...",
        "....kssssssssBBBBBssssssssssk...",
        "....kkkkkkkkkkkkkkkkkkkkkkkkk...",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
    ]
    save_icon("town_hall", rows)


def build_spawn_settlers_icon():
    # Two settlers with hats + cloaks standing side-by-side, RTS icon vibe.
    # Palette: k outline, h hair/hat, p skin, c cloak, G gold buckle,
    # B brown belt, # boot, W white shirt.
    rows = [
        "................................",
        "................................",
        "................................",
        "........kkk............kkk......",
        ".......khhhk..........khhhk.....",
        "......khhhhhk........khhhhhk....",
        "......kpppppk........kpppppk....",
        "......kpWpWpk........kpWpWpk....",
        "......kppppppk.......kppppppk...",
        ".....kppppppppk.....kppppppppk..",
        ".....kcccccccck.....kcccccccck..",
        "....kccccccccccck..kccccccccccck",
        "....kccccGccccccck.kccccGccccccck",
        "....kccccccccccck..kccccccccccck",
        "....kccccccccccck..kccccccccccck",
        "....kccccBBBcccck..kccccBBBcccck",
        "....kccccccccccck..kccccccccccck",
        "....kccccccccccck..kccccccccccck",
        ".....kpppppppppk....kpppppppppk.",
        ".....kpppk.kpppk....kpppk.kpppk.",
        ".....kpppk.kpppk....kpppk.kpppk.",
        ".....k##k..k##k.....k##k..k##k..",
        ".....kkkk..kkkk.....kkkk..kkkk..",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
    ]
    # Enforce exact 32×32 — truncate or pad just in case.
    rows = [(r + "." * 32)[:32] for r in rows]
    rows = (rows + ["." * 32] * 32)[:32]
    save_icon("spawn_settlers", rows)


if __name__ == "__main__":
    build_planet_sheet()
    build_town_hall_icon()
    build_spawn_settlers_icon()
