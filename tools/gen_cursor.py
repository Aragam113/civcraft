"""Generate a 32x32 RTS-style arrow cursor with a dark outline + bright fill.
Saved into assets/civcraft/textures/gui/cursor.png so the mod ships with it.
"""
from PIL import Image, ImageDraw
import os

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                   "assets", "civcraft", "textures", "gui", "cursor.png")
os.makedirs(os.path.dirname(OUT), exist_ok=True)

SIZE = 32
img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
draw = ImageDraw.Draw(img)

gold = (212, 175, 55, 255)
dark = (30, 15, 15, 255)

# Classic triangular arrow from (0,0) top-left tip.
arrow = [(1, 1), (1, 22), (7, 17), (11, 27), (15, 25), (11, 16), (19, 15)]
# Outline
for i in range(len(arrow)):
    draw.line([arrow[i], arrow[(i + 1) % len(arrow)]], fill=dark, width=2)
# Fill
draw.polygon(arrow, fill=gold, outline=dark)
img.save(OUT)
print(f"wrote {OUT}")
