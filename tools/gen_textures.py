"""Generate placeholder 16x16 textures for town_hall block and settler_charter item.

Meant as MVP art — can be swapped with real textures later.
"""
from PIL import Image, ImageDraw
import os

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "civcraft", "textures")


def save(img, rel):
    path = os.path.join(OUT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    print(f"wrote {path}")


def town_hall_block():
    img = Image.new("RGBA", (16, 16), (96, 78, 55, 255))
    draw = ImageDraw.Draw(img)
    # brick-like lines
    brick = (60, 48, 30, 255)
    for y in (3, 7, 11):
        for x in range(16):
            draw.point((x, y), brick)
    # vertical joints offset per row
    for x in (2, 8, 14):
        for y in (0, 1, 2):
            draw.point((x, y), brick)
    for x in (5, 11):
        for y in (4, 5, 6):
            draw.point((x, y), brick)
    for x in (2, 8, 14):
        for y in (8, 9, 10):
            draw.point((x, y), brick)
    for x in (5, 11):
        for y in (12, 13, 14, 15):
            draw.point((x, y), brick)
    # gold trim around the edges
    gold = (212, 175, 55, 255)
    for x in range(16):
        img.putpixel((x, 0), gold)
        img.putpixel((x, 15), gold)
    for y in range(16):
        img.putpixel((0, y), gold)
        img.putpixel((15, y), gold)
    save(img, "block/town_hall.png")


def settler_charter_item():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    parchment = (222, 196, 150, 255)
    edge = (140, 110, 70, 255)
    ink = (60, 20, 20, 255)
    wax = (160, 20, 20, 255)
    # scroll body
    draw.rectangle([2, 3, 13, 12], fill=parchment, outline=edge)
    # three ink lines
    for y in (5, 7, 9):
        for x in range(4, 12):
            draw.point((x, y), ink)
    # wax seal
    for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
        img.putpixel((7 + dx, 11 + dy), wax)
    save(img, "item/settler_charter.png")


if __name__ == "__main__":
    town_hall_block()
    settler_charter_item()
