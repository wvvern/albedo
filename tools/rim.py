"""Gives an image rounded corners, a thin dark rim and a soft shadow, for the top image of the store description.

Usage: python tools/rim.py OUT IMAGE
Needs Pillow.
"""
import sys

from PIL import Image, ImageDraw, ImageFilter

RADIUS = 18                # the corner radius, in image pixels
RIM = 1                    # the width of the rim, in image pixels
RIM_COLOR = (10, 10, 12, 255)
SHADOW = 24                # the blur of the shadow, in image pixels
SHADOW_ALPHA = 90
SS = 4                     # supersampling for smooth corners


def rounded(w, h, r):
    mask = Image.new("L", (w * SS, h * SS), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, w * SS - 1, h * SS - 1), r * SS, fill=255)
    return mask.resize((w, h), Image.LANCZOS)


def rim(image):
    w, h = image.width + 2 * RIM, image.height + 2 * RIM
    pad = SHADOW * 2
    out = Image.new("RGBA", (w + 2 * pad, h + 2 * pad), (0, 0, 0, 0))
    shadow = Image.new("RGBA", out.size, (0, 0, 0, 0))
    shadow.paste((0, 0, 0, SHADOW_ALPHA), (pad, pad + SHADOW // 3), rounded(w, h, RADIUS))
    out = Image.alpha_composite(out, shadow.filter(ImageFilter.GaussianBlur(SHADOW / 2)))
    layer = Image.new("RGBA", out.size, (0, 0, 0, 0))
    layer.paste(RIM_COLOR, (pad, pad), rounded(w, h, RADIUS))
    layer.paste(image, (pad + RIM, pad + RIM), rounded(image.width, image.height, RADIUS - RIM))
    return Image.alpha_composite(out, layer)


def main(argv):
    if len(argv) != 3:
        sys.exit(__doc__)
    rim(Image.open(argv[2]).convert("RGBA")).save(argv[1])


if __name__ == "__main__":
    main(sys.argv)
