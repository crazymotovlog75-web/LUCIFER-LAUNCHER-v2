#!/usr/bin/env python3
"""Generate the original Minecraft-inspired wallpaper gallery art.

Requires Pillow. Wallpaper 01 is intentionally kept as the
user-provided Ender Dragon scene; this script creates wallpapers 02 through 20
and matching compact picker previews without modifying the launcher source image.
"""

from __future__ import annotations

import math
import random
from pathlib import Path
import xml.etree.ElementTree as ET

from PIL import Image, ImageChops, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
DRAWABLE_DIR = ROOT / "app_pojavlauncher/src/main/res/drawable-nodpi"
WIDTH, HEIGHT = 1600, 900

SCENES = [
    # id, name, type, sky top, sky bottom, far, middle, land, accent, glow
    (2, "Snowy Spruce Village", "snow", "#13263A", "#7D9FB6", "#25475B", "#547F91", "#91B4B8", "#D9F4F1", "#9FE9F4"),
    (3, "Amethyst Geode", "geode", "#101327", "#382B67", "#25254A", "#51438B", "#342B55", "#C39AFF", "#C574FF"),
    (4, "Desert Temple", "desert", "#4B2639", "#F39A62", "#8A5260", "#B66D55", "#C98C57", "#FFD082", "#FFB25F"),
    (5, "Cherry Blossom Village", "cherry", "#342543", "#CF7798", "#60466F", "#9671A0", "#756044", "#FFC1D6", "#FF93C5"),
    (6, "Emerald Jungle Ruins", "jungle", "#0D302C", "#4D9470", "#174D42", "#2B7650", "#3B7146", "#9FEA75", "#5DFFD1"),
    (7, "Coral Reef", "ocean", "#0B3550", "#137A91", "#15576B", "#167B88", "#194F55", "#6EF0D3", "#6DEEFF"),
    (8, "Nether Fortress", "nether", "#190F25", "#642535", "#391C35", "#6E2B35", "#54262E", "#FF7447", "#FF5537"),
    (9, "Golden Plains Village", "village", "#34233C", "#E8A05D", "#65505A", "#A17B56", "#93734C", "#FFD46B", "#FFC45B"),
    (10, "Frozen Peaks", "mountain", "#182C4D", "#83C6D9", "#496F98", "#789BB5", "#4E6D7A", "#D8F7FF", "#9FE5FF"),
    (11, "Crimson Forest", "crimson", "#160D2C", "#70233E", "#391A3E", "#70243C", "#3C202F", "#FA526E", "#FF4765"),
    (12, "Lush Cave", "cave", "#071A20", "#164444", "#14353A", "#27594B", "#243D37", "#75F0C5", "#55F8D6"),
    (13, "Bamboo Grove", "bamboo", "#173334", "#8CB778", "#3E6D52", "#547D4C", "#6D7640", "#D8E881", "#C8FF85"),
    (14, "Badlands Mesa", "mesa", "#321C35", "#D17B52", "#80414D", "#B05C4C", "#9B5140", "#F2B768", "#FFB46A"),
    (15, "Ancient City", "ancient", "#07111D", "#202C46", "#172537", "#26384C", "#273342", "#67D5D2", "#56DDE5"),
    (16, "Woodland Mansion", "mansion", "#101A35", "#6B557B", "#24354C", "#405343", "#34443D", "#D2AE79", "#E5C681"),
    (17, "Wildflower Meadow", "meadow", "#28476B", "#A9C9BA", "#5A786F", "#79956D", "#55744D", "#F4D777", "#FFE48A"),
    (18, "Mushroom Fields", "mushroom", "#201F48", "#B1678C", "#4D3866", "#826071", "#5C5948", "#FFB3D0", "#FF78A8"),
    (19, "Ocean Monument", "monument", "#062C42", "#12687C", "#124454", "#17606A", "#16434A", "#71E3D2", "#79E9FF"),
    (20, "The End Islands", "end", "#0D0D27", "#2F1C50", "#282043", "#463167", "#302545", "#D3A5FF", "#C474FF"),
]


def rect(x: float, y: float, w: float, h: float, fill: str, opacity: float = 1.0, rx: float = 0) -> str:
    return (f'<rect x="{x:.1f}" y="{y:.1f}" width="{w:.1f}" height="{h:.1f}" '
            f'fill="{fill}" opacity="{opacity:.3f}" rx="{rx:.1f}"/>')


def polygon(points: list[tuple[float, float]], fill: str, opacity: float = 1.0) -> str:
    pairs = " ".join(f"{x:.1f},{y:.1f}" for x, y in points)
    return f'<polygon points="{pairs}" fill="{fill}" opacity="{opacity:.3f}"/>'


def ridge(rng: random.Random, base: int, spread: int, fill: str, step: int = 48, opacity: float = 1.0) -> str:
    x = 0
    points = [(0, HEIGHT)]
    while x < WIDTH:
        y = base - rng.randrange(max(16, spread // 2), spread + 1)
        points.extend([(x, y), (min(WIDTH, x + step), y)])
        x += step
    points.append((WIDTH, HEIGHT))
    return polygon(points, fill, opacity)


def clouds(rng: random.Random, color: str, count: int = 8, max_y: int = 350) -> str:
    parts = []
    for _ in range(count):
        x = rng.randrange(-70, WIDTH - 100)
        y = rng.randrange(55, max_y)
        unit = rng.randrange(14, 27)
        blocks = [(0, unit, 4), (unit, 0, 4), (unit, unit, 5), (unit * 2, unit, 4), (unit * 3, unit, 3)]
        for dx, dy, width in blocks:
            parts.append(rect(x + dx, y + dy, unit * width, unit, color, 0.28))
    return "".join(parts)


def stars(rng: random.Random, color: str, count: int = 130) -> str:
    parts = []
    for _ in range(count):
        x, y = rng.randrange(WIDTH), rng.randrange(30, 520)
        size = rng.choice([2, 3, 4, 6])
        alpha = rng.uniform(0.25, 0.9)
        parts.append(rect(x, y, size, size, color, alpha))
        if size >= 5 and rng.random() < 0.25:
            parts.append(rect(x - size, y + size // 2, size * 3, 2, color, alpha * 0.35))
            parts.append(rect(x + size // 2, y - size, 2, size * 3, color, alpha * 0.35))
    return "".join(parts)


def tree(rng: random.Random, x: int, base: int, scale: float, kind: str,
         leaf: str, leaf_hi: str, trunk: str, opacity: float = 1.0) -> str:
    parts = []
    trunk_w = int((20 if kind != "spruce" else 16) * scale)
    trunk_h = int((104 if kind != "spruce" else 135) * scale)
    top = base - trunk_h
    parts.append(rect(x - trunk_w / 2, top + trunk_h * 0.26, trunk_w, trunk_h * 0.74, trunk, opacity))
    parts.append(rect(x - trunk_w / 2, top + trunk_h * 0.26, trunk_w * 0.32, trunk_h * 0.74, "#D3A277", opacity * 0.34))
    if kind == "spruce":
        layers = 5
        for layer in range(layers):
            width = (4.6 - layer * 0.56) * 31 * scale
            y = top + layer * 30 * scale
            size = 38 * scale
            parts.append(rect(x - width / 2, y, width, size, leaf, opacity))
            parts.append(rect(x - width / 2, y, width, max(4, size * 0.22), leaf_hi, opacity * 0.58))
            parts.append(rect(x - width / 2, y + size * 0.74, width, size * 0.26, "#111A20", opacity * 0.18))
        return "".join(parts)

    cell = max(18, int(34 * scale))
    radius = 2 if kind == "cherry" else 3
    for row in range(-radius, radius + 1):
        for col in range(-radius, radius + 1):
            distance = abs(row) * 0.82 + abs(col) * 0.72
            if distance > radius + rng.uniform(-0.35, 0.75):
                continue
            bx = x + col * cell * 0.78
            by = top + row * cell * 0.74
            width = cell * rng.uniform(0.86, 1.16)
            height = cell * rng.uniform(0.82, 1.12)
            color = leaf_hi if rng.random() < 0.2 else leaf
            parts.append(rect(bx, by, width, height, color, opacity * rng.uniform(0.78, 1.0)))
            parts.append(rect(bx, by + height * 0.76, width, height * 0.24, "#07140F", opacity * 0.17))
    if kind == "cherry":
        for _ in range(10):
            px = x + rng.randrange(-int(120 * scale), int(121 * scale))
            py = top + rng.randrange(20, int(170 * scale))
            parts.append(rect(px, py, 5 * scale, 5 * scale, leaf_hi, opacity * 0.9))
    return "".join(parts)


def block_house(x: int, base: int, scale: float, wall: str, roof: str, lit: str) -> str:
    w, h = 168 * scale, 112 * scale
    x0, y0 = x - w / 2, base - h
    parts = [
        polygon([(x0, y0 + 22 * scale), (x0 + w * .72, y0), (x0 + w, y0 + 20 * scale),
                 (x0 + w * .28, y0 + 42 * scale)], roof),
        polygon([(x0 + w * .28, y0 + 42 * scale), (x0 + w, y0 + 20 * scale),
                 (x0 + w, base - 10 * scale), (x0 + w * .28, base)], wall),
        polygon([(x0, y0 + 22 * scale), (x0 + w * .28, y0 + 42 * scale),
                 (x0 + w * .28, base), (x0, base - 18 * scale)], "#513F3E"),
        rect(x0 + 72 * scale, y0 + 66 * scale, 26 * scale, 46 * scale, "#382D32"),
        rect(x0 + 28 * scale, y0 + 62 * scale, 24 * scale, 25 * scale, lit),
        rect(x0 + 120 * scale, y0 + 62 * scale, 24 * scale, 25 * scale, lit),
        rect(x0 + 31 * scale, y0 + 65 * scale, 4 * scale, 19 * scale, "#FFF1B0", .56),
        rect(x0 + 130 * scale, y0 + 65 * scale, 4 * scale, 19 * scale, "#FFF1B0", .56),
    ]
    # Lintel pixels and a chimney make the buildings read as block-built.
    parts.append(rect(x0 + 15 * scale, y0 + 31 * scale, 20 * scale, 12 * scale, roof))
    parts.append(rect(x0 + 128 * scale, y0 - 18 * scale, 16 * scale, 35 * scale, roof))
    return "".join(parts)


def portal(x: int, base: int, scale: float, frame: str, glow: str) -> str:
    w, h, b = 78 * scale, 144 * scale, 20 * scale
    left = x - w / 2
    top = base - h
    parts = [
        rect(left, top, b, h, frame), rect(left + w - b, top, b, h, frame),
        rect(left, top, w, b, frame), rect(left, base - b, w, b, frame),
        rect(left + b, top + b, w - 2 * b, h - 2 * b, glow, .46),
        rect(left + b + 7 * scale, top + b + 6 * scale, w - 2 * b - 14 * scale, h - 2 * b - 12 * scale, "#FFFFFF", .08),
    ]
    return "".join(parts)


def stepped_temple(x: int, base: int, scale: float, stone: str, highlight: str, glow: str = "#F2B768") -> str:
    parts = []
    levels = [(190, 30), (150, 34), (110, 34), (70, 32)]
    for i, (width, height) in enumerate(levels):
        y = base - sum(v[1] for v in levels[:i + 1]) * scale
        w, h = width * scale, height * scale
        left = x - w / 2
        parts.extend([rect(left, y, w, h, stone), rect(left, y, w, h * .22, highlight, .58),
                      rect(left + w * .76, y, w * .24, h, "#171725", .24)])
    parts.append(rect(x - 20 * scale, base - 178 * scale, 40 * scale, 30 * scale, glow, .78))
    return "".join(parts)


def crystal(x: int, base: int, height: int, color: str, glow: str, width: int = 34) -> str:
    left = x - width / 2
    return "".join([
        polygon([(left, base), (left + width * .22, base - height * .73),
                 (x, base - height), (left + width * .8, base - height * .64),
                 (left + width, base)], color),
        polygon([(x, base - height), (left + width * .8, base - height * .64),
                 (left + width, base), (x + width * .28, base)], glow, .58),
        polygon([(left + width * .22, base - height * .73), (x, base - height),
                 (x - width * .08, base)], "#FFFFFF", .25),
    ])


def player(x: int, base: int, scale: float, glow: str) -> str:
    """A small original blocky adventurer silhouette for scale and scene life."""
    s = scale
    head_x, head_y = x - 20 * s, base - 146 * s
    parts = [
        # Legs and boots
        rect(x - 18 * s, base - 46 * s, 16 * s, 42 * s, "#26304A"),
        rect(x + 2 * s, base - 46 * s, 16 * s, 42 * s, "#303B59"),
        rect(x - 20 * s, base - 9 * s, 18 * s, 9 * s, "#171C2B"),
        rect(x + 2 * s, base - 9 * s, 18 * s, 9 * s, "#171C2B"),
        # Arms, torso, and pack shadow
        rect(x - 32 * s, base - 100 * s, 13 * s, 52 * s, "#986B4E"),
        rect(x + 19 * s, base - 100 * s, 13 * s, 52 * s, "#B07A59"),
        rect(x - 20 * s, base - 103 * s, 40 * s, 57 * s, "#347E9A"),
        rect(x + 13 * s, base - 103 * s, 7 * s, 57 * s, "#1D596F"),
        # Head, hair cap, and face
        rect(head_x, head_y, 40 * s, 40 * s, "#C58D69"),
        rect(head_x, head_y, 40 * s, 11 * s, "#49382F"),
        rect(head_x, head_y + 9 * s, 9 * s, 8 * s, "#49382F"),
        rect(head_x + 7 * s, head_y + 21 * s, 6 * s, 4 * s, "#273D60"),
        rect(head_x + 27 * s, head_y + 21 * s, 6 * s, 4 * s, "#273D60"),
        rect(head_x + 17 * s, head_y + 29 * s, 8 * s, 4 * s, "#815642"),
        # Edge highlight
        rect(head_x, head_y, 3 * s, 40 * s, glow, .18),
    ]
    return "".join(parts)


def scene_svg(scene: tuple) -> str:
    scene_id, name, kind, sky_top, sky_bottom, far, middle, land, accent, glow = scene
    rng = random.Random(0x51A7 + scene_id * 7919)
    pieces = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{WIDTH}" height="{HEIGHT}" viewBox="0 0 {WIDTH} {HEIGHT}">',
        '<defs>',
        f'<linearGradient id="sky" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{sky_top}"/><stop offset="1" stop-color="{sky_bottom}"/></linearGradient>',
        f'<linearGradient id="ground" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{land}"/><stop offset="1" stop-color="{far}"/></linearGradient>',
        f'<radialGradient id="haze"><stop offset="0" stop-color="{glow}" stop-opacity=".32"/><stop offset="1" stop-color="{glow}" stop-opacity="0"/></radialGradient>',
        '</defs>',
        '<rect width="1600" height="900" fill="url(#sky)"/>',
    ]

    night = kind in {"geode", "nether", "crimson", "ancient", "mansion", "end"}
    if night:
        pieces.append(stars(rng, "#E6E5FF", 160 if kind in {"end", "ancient"} else 105))
        moon_x = 1190 if scene_id % 2 else 360
        moon_y = 180 if scene_id % 3 else 225
        moon_size = 52 if kind in {"end", "ancient"} else 38
        pieces.append(f'<circle cx="{moon_x}" cy="{moon_y}" r="{moon_size * 2.8}" fill="url(#haze)" opacity=".8"/>')
        pieces.append(f'<rect x="{moon_x - moon_size}" y="{moon_y - moon_size}" width="{moon_size * 2}" height="{moon_size * 2}" fill="{accent}" opacity=".82"/>')
        pieces.append(f'<rect x="{moon_x - moon_size * .45}" y="{moon_y - moon_size}" width="{moon_size * .45}" height="{moon_size * 2}" fill="#FFFFFF" opacity=".18"/>')
    else:
        pieces.append(clouds(rng, "#F5F4F4", 9 if kind not in {"ocean", "monument"} else 5, 330))
        sun_x = 1230 if scene_id % 2 else 410
        sun_y = 210 if kind != "snow" else 170
        sun_size = 53 if kind in {"desert", "mesa"} else 38
        pieces.append(f'<circle cx="{sun_x}" cy="{sun_y}" r="{sun_size * 3.5}" fill="url(#haze)"/>')
        pieces.append(rect(sun_x - sun_size, sun_y - sun_size, sun_size * 2, sun_size * 2, glow, .94))
        pieces.append(rect(sun_x - sun_size * .72, sun_y - sun_size, sun_size * .45, sun_size * 2, "#FFF9DE", .2))

    # Pixel-stepped distant ridges establish depth while staying recognizably voxel-built.
    pieces.append(ridge(rng, 555 if kind not in {"ocean", "monument", "end"} else 600, 245, far, 58, .72))
    pieces.append(ridge(rng, 630 if kind not in {"ocean", "monument", "end"} else 690, 185, middle, 42, .92))

    # Special distant silhouettes.
    if kind in {"snow", "mountain"}:
        for x, y, w, h in [(160, 410, 220, 220), (520, 365, 270, 260), (1050, 390, 320, 270), (1430, 430, 220, 230)]:
            pieces.append(polygon([(x - w / 2, y + h), (x - w * .34, y + h * .48), (x - w * .13, y + h * .62),
                                   (x, y), (x + w * .15, y + h * .42), (x + w * .28, y + h * .31),
                                   (x + w / 2, y + h)], "#DFF5F7" if kind == "snow" else "#A7DCE3", .68))
            pieces.append(polygon([(x - w * .13, y + h * .62), (x, y), (x + w * .15, y + h * .42),
                                   (x + w * .02, y + h * .34), (x - w * .02, y + h * .48)], "#FFFFFF", .74))
    elif kind == "mesa":
        for x, y, w, h in [(230, 440, 330, 200), (690, 375, 390, 270), (1210, 430, 360, 220)]:
            pieces.extend([rect(x - w / 2, y, w, h, far), rect(x - w / 2 + 25, y - 18, w - 50, 24, accent, .42),
                           rect(x - w / 2 + 50, y + h * .30, w - 100, 22, glow, .33),
                           rect(x - w / 2 + 13, y + h * .72, w - 26, 25, "#71364A", .55)])
    elif kind in {"ancient", "mansion"}:
        for x in [230, 385, 1200, 1370]:
            height = rng.randrange(100, 210)
            pieces.append(rect(x, 600 - height, rng.randrange(28, 54), height, "#182435", .78))
            pieces.append(rect(x - 12, 600 - height, rng.randrange(48, 70), 18, "#405368", .64))
            pieces.append(rect(x + 7, 600 - height + 38, 6, 24, glow, .56))

    water_mode = kind in {"ocean", "monument"}
    if kind == "end":
        # Floating End islands, and small suspended chorus-like blocks.
        pieces.append(polygon([(0, 585), (190, 550), (380, 574), (490, 635), (690, 600), (820, 560), (980, 582),
                               (1100, 636), (1320, 580), (1600, 566), (1600, 900), (0, 900)], land, .96))
        for x, y, w in [(290, 575, 250), (870, 560, 230), (1330, 600, 320)]:
            pieces.append(polygon([(x - w / 2, y), (x - w * .32, y + 46), (x - w * .16, y + 83),
                                   (x + w * .15, y + 84), (x + w * .34, y + 48), (x + w / 2, y)], "#25213D"))
            for bx in range(int(x - w / 2 + 18), int(x + w / 2 - 24), 42):
                pieces.append(rect(bx, y + 14, 22, 10, accent, .5))
        for x, y in [(250, 330), (930, 395), (1380, 300), (720, 270)]:
            pieces.append(rect(x, y, 35, 35, far, .82))
            pieces.append(rect(x + 6, y + 6, 18, 18, accent, .6))
    elif water_mode:
        water_top = 410 if kind == "ocean" else 455
        pieces.append(rect(0, water_top, WIDTH, HEIGHT - water_top, "#0A6073", .55))
        for i in range(8):
            y = water_top + 36 + i * 39
            parts = []
            for x in range((i * 91) % 180, WIDTH, 210):
                parts.append(rect(x, y, rng.randrange(55, 140), 4, "#9AF4EC", rng.uniform(.12, .28)))
            pieces.extend(parts)
        # Seabed with stepped sand/stone bands.
        pieces.append(polygon([(0, 690), (120, 660), (300, 710), (475, 668), (620, 735), (780, 680),
                               (980, 720), (1170, 660), (1360, 705), (1600, 650), (1600, 900), (0, 900)], land))
    else:
        ground_y = 580 if kind not in {"snow", "mountain", "desert", "mesa"} else 620
        points = [(0, 900), (0, ground_y)]
        x = 0
        while x < WIDTH:
            y = ground_y + rng.randrange(-32, 40)
            points.extend([(x, y), (min(WIDTH, x + 45), y)])
            x += 45
        points.append((WIDTH, 900))
        pieces.append(polygon(points, "url(#ground)"))
        top_edge = []
        x = 0
        while x < WIDTH:
            y = ground_y + rng.randrange(-32, 40)
            top_edge.extend([(x, y), (min(WIDTH, x + 45), y)])
            x += 45
        pieces.append(polygon(top_edge + [(WIDTH, 698), (0, 698)], land, .86))

    # Scene-specific landmarks.
    if kind == "desert":
        pieces.append(stepped_temple(1080, 635, 1.28, "#C58B5B", "#E7B879", "#FFDF8A"))
        pieces.append(stepped_temple(330, 655, .58, "#AE7753", "#D9A66D", "#FFC66F"))
        for x in [130, 470, 1410]:
            pieces.append(rect(x, 580 - rng.randrange(25, 65), 14, 80, "#3C6D4F"))
            pieces.append(rect(x - 25, 560 - rng.randrange(20, 50), 64, 18, "#4F8A5C"))
    elif kind == "geode":
        pieces.append(polygon([(720, 560), (810, 405), (1040, 370), (1195, 468), (1120, 630), (855, 665)], "#302340", .9))
        pieces.append(polygon([(790, 558), (860, 435), (1035, 412), (1130, 483), (1072, 598), (870, 620)], "#13172A", .93))
        for x, y, h, w in [(850, 560, 124, 40), (955, 540, 165, 42), (1050, 567, 102, 34), (770, 560, 74, 27), (1120, 575, 89, 28)]:
            pieces.append(crystal(x, y, h, accent, glow, w))
        pieces.append(portal(953, 630, .65, "#372954", "#9D62E5"))
    elif kind in {"village", "snow", "cherry", "mansion"}:
        if kind == "mansion":
            pieces.append(block_house(810, 615, 1.55, "#373949", "#171B2D", "#F4C57C"))
            pieces.append(rect(655, 462, 32, 155, "#28283A"))
            pieces.append(rect(960, 462, 32, 155, "#28283A"))
            pieces.append(rect(682, 450, 286, 25, "#28283A"))
        else:
            pieces.append(block_house(880, 625, 1.28, "#A77956" if kind != "snow" else "#8E7668", "#573C43" if kind != "snow" else "#4B3A45", "#FFE7A0"))
            pieces.append(block_house(1200, 640, .72, "#8A694E", "#453641", "#FFD98A"))
            pieces.append(rect(720, 602, 370, 20, "#A7D8D1" if kind == "snow" else "#755445", .65))
    elif kind == "jungle":
        pieces.append(stepped_temple(1050, 650, 1.25, "#4E6650", "#8B9C67", "#74FFD6"))
        pieces.append(rect(1000, 510, 100, 140, "#0F322F", .42))
        pieces.append(rect(1020, 522, 60, 112, "#3FFFD0", .18))
        # Waterfall through the old temple ravine.
        pieces.append(rect(425, 395, 88, 282, "#62DDD4", .45))
        pieces.append(rect(444, 395, 22, 280, "#D9FFEC", .25))
    elif kind in {"nether", "crimson"}:
        pieces.append(portal(1050, 650, 1.18, "#21172C", "#FF4773" if kind == "crimson" else "#D74741"))
        pieces.append(rect(795, 500, 38, 155, "#47263C"))
        pieces.append(rect(1290, 470, 50, 185, "#4A2537"))
        pieces.append(rect(0, 740, WIDTH, 45, "#F25735", .82))
        for x in [220, 480, 1380]:
            pieces.append(polygon([(x - 35, 600), (x - 20, 495), (x, 455), (x + 18, 540), (x + 42, 600)], "#301B2E"))
    elif kind == "ocean":
        pieces.append(stepped_temple(1120, 705, .92, "#458C82", "#8CC9B2", "#D8E89F"))
        for x in [225, 330, 1330, 1440, 560, 720]:
            h = rng.randrange(35, 95)
            pieces.append(crystal(x, 765, h, rng.choice(["#F87AA8", "#FF9A67", "#9C8CFF", "#72E6BE"]), "#A4FFF4", rng.randrange(22, 42)))
        for _ in range(32):
            x, y = rng.randrange(WIDTH), rng.randrange(490, 745)
            size = rng.choice([5, 7, 9, 12])
            pieces.append(f'<circle cx="{x}" cy="{y}" r="{size}" fill="#C8FFFF" opacity=".48"/>')
    elif kind == "monument":
        pieces.append(stepped_temple(800, 720, 1.55, "#599D8E", "#A6D6A2", "#FFF1A0"))
        for x in [500, 1080]:
            pieces.append(rect(x, 565, 28, 155, "#579588"))
            pieces.append(rect(x - 20, 556, 68, 17, "#B0D8A4"))
    elif kind == "cave":
        pieces.insert(5, polygon([(0, 0), (1600, 0), (1600, 180), (1450, 195), (1360, 260), (1210, 220),
                                  (1080, 288), (920, 230), (790, 300), (640, 228), (500, 285), (350, 210),
                                  (180, 272), (0, 200)], "#071217", .94))
        for x in [260, 470, 1235, 1400]:
            pieces.append(crystal(x, 685, rng.randrange(90, 170), accent, glow, rng.randrange(34, 58)))
        pieces.append(stepped_temple(890, 675, .65, "#29433A", "#59765B", "#8AFFF0"))
        for x in range(0, WIDTH, 125):
            pieces.append(rect(x + rng.randrange(12, 48), 615 + rng.randrange(-10, 20), 16, 85, "#3D8162", .7))
            pieces.append(rect(x, 650, 67, 14, "#75D39B", .56))
    elif kind == "bamboo":
        for x in [140, 255, 390, 1180, 1320, 1460]:
            h = rng.randrange(250, 520)
            width = rng.randrange(15, 24)
            pieces.append(rect(x, 620 - h, width, h, "#4E8C4E"))
            for y in range(620 - h + 42, 620, 54):
                pieces.append(rect(x - 3, y, width + 6, 5, "#B3D56C", .7))
            for side in [-1, 1]:
                pieces.append(polygon([(x + width / 2, 620 - h + 55), (x + side * 72, 620 - h + 30),
                                       (x + side * 100, 620 - h + 57), (x + width / 2, 620 - h + 72)], "#8FC46B", .78))
        pieces.append(block_house(840, 624, .92, "#9A744C", "#354D3E", "#F6D58B"))
    elif kind == "mesa":
        pieces.extend([rect(260, 475, 225, 168, "#A8534D"), rect(220, 460, 305, 35, "#DA8A57"),
                       rect(660, 390, 290, 255, "#A54D49"), rect(610, 372, 390, 45, "#E29B60"),
                       rect(1120, 455, 280, 188, "#98464C"), rect(1080, 437, 360, 42, "#D68A59")])
        for x in [350, 800, 1235]:
            pieces.append(rect(x, 640, 16, 35, "#2E4C42"))
            pieces.append(rect(x - 18, 620, 54, 16, "#477252"))
    elif kind == "ancient":
        pieces.append(stepped_temple(800, 710, 1.85, "#28394B", "#52677B", "#5CE3DE"))
        pieces.append(rect(0, 730, WIDTH, 52, "#142535", .92))
        for x in [300, 500, 1100, 1320]:
            pieces.append(rect(x, 525, 38, 195, "#23374B"))
            pieces.append(rect(x - 13, 516, 66, 18, "#637889"))
            pieces.append(rect(x + 13, 575, 7, 55, glow, .8))
    elif kind == "mushroom":
        for x, base, scale in [(320, 645, 1.2), (670, 605, .86), (1120, 650, 1.55), (1425, 620, .82)]:
            cap_w, cap_h = 145 * scale, 65 * scale
            pieces.append(rect(x - 19 * scale, base - 95 * scale, 38 * scale, 95 * scale, "#E9DAC0"))
            pieces.append(polygon([(x - cap_w / 2, base - 90 * scale), (x - cap_w * .42, base - 142 * scale),
                                   (x - cap_w * .16, base - 158 * scale), (x + cap_w * .18, base - 158 * scale),
                                   (x + cap_w * .44, base - 136 * scale), (x + cap_w / 2, base - 90 * scale)], "#D84E72"))
            for dx, dy in [(-35, -135), (12, -145), (42, -112), (-8, -116)]:
                pieces.append(rect(x + dx * scale, base + dy * scale, 18 * scale, 12 * scale, "#FFE9CE", .94))
        pieces.append(block_house(840, 650, .82, "#896C69", "#563E60", "#FFD58E"))
    if kind == "snow":
        for x in range(100, WIDTH, 210):
            pieces.append(tree(rng, x, 645 + rng.randrange(-10, 20), rng.uniform(.85, 1.25), "spruce", "#31585A", "#88B1A8", "#5B473D", .9))
        pieces.append(block_house(850, 636, 1.05, "#8D766D", "#4E3C47", "#FFE5A0"))
        pieces.append(rect(0, 632, WIDTH, 15, "#D4ECE8", .86))
    elif kind == "mountain":
        pieces.append(polygon([(0, 635), (200, 590), (390, 660), (560, 600), (780, 660), (980, 595), (1200, 650), (1400, 585), (1600, 640), (1600, 900), (0, 900)], "#415E6C"))
        for x, base, s in [(240, 680, 1.3), (530, 690, .9), (1250, 675, 1.45), (1480, 680, .9)]:
            pieces.append(tree(rng, x, base, s, "spruce", "#31585A", "#DDF2EC", "#594A4D", .92))
        pieces.append(rect(0, 680, WIDTH, 16, "#DFF4F5", .7))
    elif kind == "cherry":
        for x in [190, 490, 1320, 1490]:
            pieces.append(tree(rng, x, 640 + rng.randrange(-10, 18), rng.uniform(.95, 1.4), "cherry", "#D989AD", "#FFC0D6", "#554047", .96))
        pieces.append(block_house(850, 620, .92, "#97765E", "#61434C", "#FFDE99"))
        for _ in range(25):
            x, y = rng.randrange(WIDTH), rng.randrange(240, 620)
            pieces.append(rect(x, y, 6, 6, "#FFE0EA", rng.uniform(.38, .82)))
    elif kind == "jungle":
        for x in [110, 280, 430, 1260, 1430, 1550]:
            pieces.append(tree(rng, x, 650 + rng.randrange(-15, 18), rng.uniform(1.2, 1.75), "oak", "#245E43", "#54A567", "#55423A", .94))
        for x in [575, 690, 800]:
            pieces.append(rect(x, 645 - rng.randrange(80, 190), 12, 230, "#45A071", .58))
    elif kind == "village":
        pieces.extend([block_house(300, 628, .73, "#9F7954", "#59424A", "#FFE3A0"),
                       block_house(720, 620, .86, "#A47D58", "#4E3B4A", "#FFE0A0"),
                       block_house(1220, 635, .72, "#9B7455", "#53404C", "#FFDF9E")])
        for x in [140, 510, 1000, 1440]:
            pieces.append(tree(rng, x, 635, rng.uniform(.6, .9), "oak", "#36664B", "#64A16A", "#5B433A", .88))
    elif kind == "crimson":
        for x in [130, 340, 610, 1320, 1490]:
            pieces.append(tree(rng, x, 640 + rng.randrange(-10, 20), rng.uniform(1.0, 1.45), "oak", "#7D263D", "#D54056", "#341B2D", .96))
        pieces.append(rect(0, 720, WIDTH, 35, "#E54B42", .82))
    elif kind == "end":
        pieces.append(portal(840, 592, .82, "#403057", "#9C64FF"))
        for x in [170, 550, 1260, 1490]:
            pieces.append(tree(rng, x, 585 + rng.randrange(-12, 12), rng.uniform(.55, .82), "oak", "#4B376B", "#8060A8", "#332A48", .84))
    elif kind == "monument":
        for x in [210, 420, 1360, 1500]:
            pieces.append(crystal(x, 805, rng.randrange(45, 100), rng.choice(["#F079A7", "#FDAC62", "#78DFC2"]), "#A3FFFF", 28))

    # Trees are layered after landmarks to bring the foreground into focus.
    if kind not in {"snow", "mountain", "cherry", "jungle", "village", "crimson", "mushroom", "end", "bamboo", "ocean", "monument", "nether", "ancient", "geode", "cave", "mesa", "desert"}:
        for x in [120, 290, 1320, 1510]:
            pieces.append(tree(rng, x, 650, rng.uniform(.95, 1.35), "oak", "#315A43", "#6B9C5A", "#56433A", .9))

    if kind == "bamboo":
        for x in [120, 360, 520, 1100, 1390, 1530]:
            h = rng.randrange(150, 310)
            pieces.append(rect(x, 642 - h, 18, h, "#3E7E4C", .94))
            for y in range(642 - h + 30, 642, 45):
                pieces.append(rect(x - 2, y, 22, 4, "#B4D66A", .75))

    # Foreground blocks, pixel grass/stone flecks, small flowers, and bioluminescent motes.
    for _ in range(100):
        x, y = rng.randrange(WIDTH), rng.randrange(700, 890)
        size = rng.randrange(4, 15)
        color = rng.choice(["#D6C59A", "#111725", accent, "#F8E3A5"])
        pieces.append(rect(x, y, size, size, color, rng.uniform(.06, .24)))
    for _ in range(38):
        x, y = rng.randrange(WIDTH), rng.randrange(420, 690)
        size = rng.choice([3, 4, 6, 8])
        pieces.append(rect(x, y, size, size, glow, rng.uniform(.18, .62)))

    # A tiny silhouette gives each vista a clear sense of scale without competing with the launcher UI.
    if kind in {"snow", "desert", "cherry", "jungle", "ocean", "nether", "village", "mountain", "crimson", "bamboo", "mesa", "mushroom", "end"}:
        base = 655 if not water_mode else 740
        pieces.append(player(520 if scene_id % 2 else 1125, base, .72, glow))

    # The app-wide backdrop scrim provides UI contrast; keep the scene itself crisp and clear.
    pieces.append('</svg>')
    return "\n".join(str(piece) for piece in pieces)


def parse_hex_color(value: str, alpha: float = 1.0) -> tuple[int, int, int, int]:
    value = value.strip().lstrip("#")
    if len(value) == 3:
        value = "".join(channel * 2 for channel in value)
    red, green, blue = (int(value[i:i + 2], 16) for i in (0, 2, 4))
    return red, green, blue, max(0, min(255, round(255 * alpha)))


def gradient_color(stops: list[tuple[float, tuple[int, int, int, int]]], amount: float) -> tuple[int, int, int, int]:
    amount = max(0.0, min(1.0, amount))
    if amount <= stops[0][0]:
        return stops[0][1]
    for (left_at, left), (right_at, right) in zip(stops, stops[1:]):
        if amount <= right_at:
            span = max(0.0001, right_at - left_at)
            fraction = (amount - left_at) / span
            return tuple(round(a + (b - a) * fraction) for a, b in zip(left, right))
    return stops[-1][1]


def gradient_image(gradient: ET.Element, width: int, height: int) -> Image.Image:
    stops = []
    for stop in gradient:
        offset = stop.attrib.get("offset", "0").strip()
        offset_value = float(offset[:-1]) / 100 if offset.endswith("%") else float(offset)
        stop_opacity = float(stop.attrib.get("stop-opacity", "1"))
        stops.append((offset_value, parse_hex_color(stop.attrib.get("stop-color", "#000000"), stop_opacity)))
    if not stops:
        return Image.new("RGBA", (width, height), (0, 0, 0, 0))
    stops.sort(key=lambda item: item[0])
    tag = gradient.tag.rsplit("}", 1)[-1]

    if tag == "linearGradient":
        x1, y1 = float(gradient.attrib.get("x1", "0")), float(gradient.attrib.get("y1", "0"))
        x2, y2 = float(gradient.attrib.get("x2", "0")), float(gradient.attrib.get("y2", "0"))
        dx, dy = (x2 - x1) * width, (y2 - y1) * height
        if abs(dy) >= abs(dx):
            strip = Image.new("RGBA", (1, height))
            strip_draw = ImageDraw.Draw(strip)
            start, end = y1 * height, y2 * height
            for y in range(height):
                amount = (y - start) / max(1.0, end - start)
                strip_draw.point((0, y), fill=gradient_color(stops, amount))
            return strip.resize((width, height), Image.Resampling.BILINEAR)
        strip = Image.new("RGBA", (width, 1))
        strip_draw = ImageDraw.Draw(strip)
        start, end = x1 * width, x2 * width
        for x in range(width):
            amount = (x - start) / max(1.0, end - start)
            strip_draw.point((x, 0), fill=gradient_color(stops, amount))
        return strip.resize((width, height), Image.Resampling.BILINEAR)

    # The gallery uses object-bounding-box radial gradients for soft pixel-art light blooms.
    preview = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    draw = ImageDraw.Draw(preview)
    center = (127.5, 127.5)
    for index in range(255, -1, -1):
        t = index / 255
        color = gradient_color(stops, t)
        rx, ry = 127.5 * t, 127.5 * t
        draw.ellipse((center[0] - rx, center[1] - ry, center[0] + rx, center[1] + ry), fill=color)
    # Stretching to the target bounds preserves the gradient's elliptical aspect ratio.
    return preview.resize((width, height), Image.Resampling.BILINEAR)


def rasterize_svg(svg_text: str) -> Image.Image:
    """Rasterize the small SVG subset used by this generator with Pillow only."""
    root = ET.fromstring(svg_text)
    gradients: dict[str, ET.Element] = {}
    for definition in root.iter():
        if definition.tag.rsplit("}", 1)[-1] in {"linearGradient", "radialGradient"}:
            gradients[definition.attrib["id"]] = definition

    image = Image.new("RGBA", (WIDTH, HEIGHT), (0, 0, 0, 0))
    for element in root:
        tag = element.tag.rsplit("}", 1)[-1]
        if tag == "defs":
            continue
        attrs = element.attrib
        opacity = float(attrs.get("opacity", "1"))
        fill_value = attrs.get("fill", "#000000")
        gradient = gradients.get(fill_value[5:-1]) if fill_value.startswith("url(#") else None

        if tag == "rect":
            x, y = float(attrs.get("x", 0)), float(attrs.get("y", 0))
            width, height = float(attrs.get("width", 0)), float(attrs.get("height", 0))
            bounds = (x, y, x + width, y + height)
            shape = "rect"
        elif tag == "polygon":
            points = [tuple(map(float, pair.split(","))) for pair in attrs.get("points", "").split()]
            if not points:
                continue
            xs, ys = zip(*points)
            bounds = (min(xs), min(ys), max(xs), max(ys))
            shape = "polygon"
        elif tag == "circle":
            cx, cy, radius = float(attrs.get("cx", 0)), float(attrs.get("cy", 0)), float(attrs.get("r", 0))
            bounds = (cx - radius, cy - radius, cx + radius, cy + radius)
            shape = "ellipse"
        elif tag == "ellipse":
            cx, cy = float(attrs.get("cx", 0)), float(attrs.get("cy", 0))
            rx, ry = float(attrs.get("rx", 0)), float(attrs.get("ry", 0))
            bounds = (cx - rx, cy - ry, cx + rx, cy + ry)
            shape = "ellipse"
        else:
            continue

        left = max(0, math.floor(bounds[0]))
        top = max(0, math.floor(bounds[1]))
        right = min(WIDTH, math.ceil(bounds[2]))
        bottom = min(HEIGHT, math.ceil(bounds[3]))
        if right <= left or bottom <= top:
            continue
        local_width, local_height = right - left, bottom - top
        mask = Image.new("L", (local_width, local_height), 0)
        mask_draw = ImageDraw.Draw(mask)
        if shape == "rect":
            mask_draw.rectangle((bounds[0] - left, bounds[1] - top,
                                 bounds[2] - left, bounds[3] - top), fill=255)
        elif shape == "ellipse":
            mask_draw.ellipse((bounds[0] - left, bounds[1] - top,
                               bounds[2] - left, bounds[3] - top), fill=255)
        else:
            mask_draw.polygon([(x - left, y - top) for x, y in points], fill=255)

        if gradient is not None:
            overlay = gradient_image(gradient, local_width, local_height)
        else:
            overlay = Image.new("RGBA", (local_width, local_height), parse_hex_color(fill_value))
        alpha = overlay.getchannel("A")
        if opacity < 0.999:
            alpha = alpha.point(lambda value, factor=opacity: round(value * factor))
        overlay.putalpha(ImageChops.multiply(alpha, mask))
        destination = image.crop((left, top, right, bottom))
        image.paste(Image.alpha_composite(destination, overlay), (left, top))
    return image.convert("RGB")


def main() -> None:
    DRAWABLE_DIR.mkdir(parents=True, exist_ok=True)
    for scene in SCENES:
        scene_id = scene[0]
        wallpaper = DRAWABLE_DIR / f"lucifer_wallpaper_{scene_id:02d}.webp"
        preview = DRAWABLE_DIR / f"lucifer_wallpaper_preview_{scene_id:02d}.webp"
        svg_text = scene_svg(scene)
        image = rasterize_svg(svg_text)
        image.save(wallpaper, format="WEBP", quality=88, method=6)
        image.resize((480, 270), Image.Resampling.LANCZOS).save(
            preview, format="WEBP", quality=84, method=6)
        print(f"generated {wallpaper.name} and {preview.name}: {scene[1]}")


if __name__ == "__main__":
    main()
