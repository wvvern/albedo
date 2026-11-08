"""Frames screenshots for a store page: dark backdrop, rounded image, thin accent rim, soft shadow, caption.

Usage: python tools/gallery.py OUT_DIR IMAGE=CAPTION [IMAGE=CAPTION ...]
Needs Pillow.
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

PAD = 56
CAPTION_H = 64
RADIUS = 18
BG_TOP = (20, 23, 28)
BG_BOTTOM = (12, 14, 17)
RIM_A = (77, 142, 255)
RIM_B = (150, 110, 255)
TEXT = (233, 236, 241)
MUTED = (139, 148, 165)
SWATCHES = [(245, 245, 245), (40, 40, 40), (77, 142, 255), (229, 99, 107)]
FONT_DIRS = [os.path.join(os.environ.get("WINDIR", "C:/Windows"), "Fonts"), "/usr/share/fonts/truetype"]


def font(names, size):
    for d in FONT_DIRS:
        for n in names:
            p = os.path.join(d, n)
            if os.path.exists(p):
                return ImageFont.truetype(p, size)
    return ImageFont.load_default()


def gradient(w, h, top, bottom):
    col = Image.new("RGB", (1, h))
    for y in range(h):
        t = y / max(1, h - 1)
        col.putpixel((0, y), tuple(round(a + (b - a) * t) for a, b in zip(top, bottom)))
    return col.resize((w, h))


def rounded_mask(w, h, r, scale=4):
    m = Image.new("L", (w * scale, h * scale), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, w * scale - 1, h * scale - 1), r * scale, fill=255)
    return m.resize((w, h), Image.LANCZOS)


def frame(src, caption, out):
    shot = Image.open(src).convert("RGB")
    w, h = shot.size
    W, H = w + 2 * PAD, h + 2 * PAD + CAPTION_H
    canvas = gradient(W, H, BG_TOP, BG_BOTTOM).convert("RGBA")

    shadow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle((PAD, PAD + 10, PAD + w, PAD + h + 10), RADIUS, fill=(0, 0, 0, 150))
    canvas = Image.alpha_composite(canvas, shadow.filter(ImageFilter.GaussianBlur(18)))

    # Rim: a 2 px left-to-right accent gradient behind the image.
    rim = 2
    rw, rh = w + 2 * rim, h + 2 * rim
    horizontal = gradient(rh, rw, RIM_A, RIM_B).transpose(Image.Transpose.ROTATE_90)
    canvas.paste(horizontal, (PAD - rim, PAD - rim), rounded_mask(rw, rh, RADIUS + rim))
    canvas.paste(shot, (PAD, PAD), rounded_mask(w, h, RADIUS))

    d = ImageDraw.Draw(canvas)
    title_font = font(["Inter-SemiBold.ttf", "segoeuib.ttf", "DejaVuSans-Bold.ttf"], 30)
    brand_font = font(["Inter-Medium.ttf", "segoeui.ttf", "DejaVuSans.ttf"], 22)
    ty = PAD + h + (CAPTION_H + PAD) // 2 - 4
    d.text((PAD + 4, ty), caption, font=title_font, fill=TEXT, anchor="lm")
    brand = "Albedo"
    bx = PAD + w - 4 - d.textlength(brand, font=brand_font)
    d.text((bx, ty), brand, font=brand_font, fill=MUTED, anchor="lm")
    s, gap = 12, 6
    sx = bx - 18 - len(SWATCHES) * (s + gap)
    for i, c in enumerate(SWATCHES):
        x = sx + i * (s + gap)
        d.rounded_rectangle((x, ty - s // 2, x + s, ty + s // 2), 3, fill=c, outline=(60, 66, 78))
    canvas.convert("RGB").save(out, optimize=True)


def main(argv):
    if len(argv) < 3:
        sys.exit(__doc__)
    out_dir = argv[1]
    os.makedirs(out_dir, exist_ok=True)
    for i, spec in enumerate(argv[2:], 1):
        src, caption = spec.split("=", 1)
        name = "%02d-%s" % (i, os.path.splitext(os.path.basename(src))[0].split("-", 1)[-1])
        out = os.path.join(out_dir, name + ".png")
        frame(src, caption, out)
        print(out)


if __name__ == "__main__":
    main(sys.argv)
