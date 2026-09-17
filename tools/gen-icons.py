#!/usr/bin/env python3
"""Generates src/main/resources/assets/schematicpreview/textures/gui/icons.png.

Own pixel-art drawings (nothing copied from the original Fabric mod's assets). Each icon is
12x12 with three malilib MultiIcon variants side by side: disabled, normal, hovered (see
gui/SchematicPreviewIcons.java for the u/v table). Re-run after editing the glyphs below.
"""
from pathlib import Path

from PIL import Image

SIZE = 12
VARIANTS = [(128, 128, 128, 255), (224, 224, 224, 255), (255, 255, 160, 255)]

# 12x12 glyphs, '#' = pixel, '.' = transparent
GLYPHS = {
    "fullscreen": [
        "............",
        ".###....###.",
        ".#........#.",
        ".#........#.",
        "............",
        "............",
        "............",
        "............",
        ".#........#.",
        ".#........#.",
        ".###....###.",
        "............",
    ],
    "freecam": [
        "............",
        "............",
        "....##......",
        ".#########..",
        ".#..###...#.",
        ".#.#...#..#.",
        ".#.#...#..#.",
        ".#.#...#..#.",
        ".#..###...#.",
        ".#########..",
        "............",
        "............",
    ],
    "save": [
        "............",
        ".#########..",
        ".#.#####.##.",
        ".#.#####.##.",
        ".#.......##.",
        ".#.......##.",
        ".#..####..#.",
        ".#.#....#.#.",
        ".#.#....#.#.",
        ".#.#....#.#.",
        ".##########.",
        "............",
    ],
    "copy": [
        "............",
        ".######.....",
        ".#....#.....",
        ".#....#.....",
        ".#..######..",
        ".#..#....#..",
        ".#..#....#..",
        ".####....#..",
        "....#....#..",
        "....#....#..",
        "....######..",
        "............",
    ],
    "preview_type": [
        "............",
        ".####.####..",
        ".#..#.#..#..",
        ".#..#.#..#..",
        ".####.####..",
        "............",
        ".####.####..",
        ".#..#.#..#..",
        ".#..#.#..#..",
        ".####.####..",
        "............",
        "............",
    ],
}

ORDER = ["fullscreen", "freecam", "save", "copy", "preview_type"]


def main():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = img.load()

    for row, name in enumerate(ORDER):
        glyph = GLYPHS[name]
        assert len(glyph) == SIZE and all(len(line) == SIZE for line in glyph), name

        for variant, color in enumerate(VARIANTS):
            for y, line in enumerate(glyph):
                for x, ch in enumerate(line):
                    if ch == "#":
                        px[variant * SIZE + x, row * SIZE + y] = color

    out = Path(__file__).resolve().parent.parent / "src/main/resources/assets/schematicpreview/textures/gui/icons.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out)
    print("wrote", out)


if __name__ == "__main__":
    main()
