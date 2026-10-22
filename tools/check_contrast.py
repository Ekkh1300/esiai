"""Contrast check for the ESIAI chat screen.

Reads a screenshot, finds the text/bubble regions reported by the UI dump and
computes the WCAG contrast ratio between the glyph pixels and their
surrounding surface.  Run:  python tools/check_contrast.py <png> <xml>
"""
import re
import sys
from collections import Counter

from PIL import Image


def relative_luminance(rgb):
    def channel(value):
        v = value / 255.0
        return v / 12.92 if v <= 0.03928 else ((v + 0.055) / 1.055) ** 2.4
    r, g, b = (channel(c) for c in rgb)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def contrast(a, b):
    la, lb = relative_luminance(a), relative_luminance(b)
    hi, lo = max(la, lb), min(la, lb)
    return (hi + 0.05) / (lo + 0.05)


def main():
    png, xml = sys.argv[1], sys.argv[2]
    image = Image.open(png).convert("RGB")
    width, height = image.size
    dump = open(xml, encoding="utf-8").read()

    nodes = re.findall(
        r'text="([^"]+)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', dump
    )
    print(f"image {width}x{height}, {len(nodes)} text nodes\n")

    worst = []
    for text, x1, y1, x2, y2 in nodes:
        if not text.strip():
            continue
        box = (int(x1), int(y1), int(x2), int(y2))
        crop = image.crop(box)
        pixels = list(crop.getdata())
        if not pixels:
            continue
        # The modal tone is the background; the minority tone is the glyph.
        counter = Counter(pixels)
        background = counter.most_common(1)[0][0]
        # Glyph pixels: those far enough from the background colour.
        glyphs = [
            p
            for p in pixels
            if sum((p[i] - background[i]) ** 2 for i in range(3)) > 900
        ]
        if len(glyphs) < 12:
            continue
        # Use the glyph core: the pixels furthest from the background tone
        # (anti-aliased edges would otherwise wash the measurement out).
        glyphs.sort(key=lambda p: sum((p[i] - background[i]) ** 2 for i in range(3)))
        take = max(3, len(glyphs) // 20)
        edge = glyphs[-take:]
        fg = tuple(sum(c[i] for c in edge) // len(edge) for i in range(3))
        ratio = contrast(fg, background)
        size = max(1, (int(y2) - int(y1)))
        # 420dpi device: 1sp is about 2.6px, so 48px is roughly 18sp.
        large = size >= 48
        need = 3.0 if large else 4.5
        flag = "OK " if ratio >= need else "LOW"
        worst.append((ratio, need, flag, text[:38], fg, background, size))

    worst.sort()
    for ratio, need, flag, text, fg, bg, size in worst[:14]:
        print(
            f"{flag} ratio {ratio:4.2f} (need {need})  size {size:3d}px  "
            f"fg={fg} bg={bg}  {text!r}"
        )
    low = [w for w in worst if w[2] == "LOW"]
    print(f"\n{len(worst)} measured, {len(low)} below the threshold")


if __name__ == "__main__":
    main()
