"""Makes one image from equal vertical strips of screenshots from the same camera, with a thin line between the strips.

Usage: python tools/split.py OUT IMAGE IMAGE [IMAGE ...]
Strip i comes from the same place in image i, so the blocks and mobs continue across the lines. Needs Pillow.
"""
import sys

from PIL import Image

LINE = 4                   # the width of a line between two strips, in image pixels
LINE_COLOR = (0, 0, 0)


def split(sources, out):
    shots = [Image.open(s).convert("RGB") for s in sources]
    w, h = shots[0].size
    if any(s.size != (w, h) for s in shots):
        sys.exit("All images must have the same size.")
    canvas = Image.new("RGB", (w, h), LINE_COLOR)
    n = len(shots)
    for i, shot in enumerate(shots):
        x0, x1 = i * w // n, (i + 1) * w // n
        # Each line takes half of its width from the strip on each side.
        left = x0 + (LINE // 2 if i > 0 else 0)
        right = x1 - (LINE - LINE // 2 if i < n - 1 else 0)
        canvas.paste(shot.crop((left, 0, right, h)), (left, 0))
    canvas.save(out, optimize=True)


def main(argv):
    if len(argv) < 4:
        sys.exit(__doc__)
    split(argv[2:], argv[1])
    print(argv[1])


if __name__ == "__main__":
    main(sys.argv)
