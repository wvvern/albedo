"""Frames screenshots for a store page as a Minecraft-style GUI panel with a pixel caption.

Usage: python tools/gallery.py OUT_DIR IMAGE=CAPTION [IMAGE=CAPTION ...]
Needs Pillow. The caption uses the Minecraft font from a Minecraft client jar in the Gradle cache.
"""
import glob
import io
import os
import sys
import zipfile

from PIL import Image, ImageDraw

U = 4                      # one GUI pixel, in image pixels
PANEL = (198, 198, 198)
LIGHT = (255, 255, 255)
SHADE = (85, 85, 85)
OUTLINE = (0, 0, 0)
SLOT_DARK = (55, 55, 55)
TEXT = (64, 64, 64)
FONT_SCALE = 4             # one font pixel, in image pixels
PAD = 6                    # panel padding, in GUI pixels


def font_sheet():
    caches = os.path.join(os.path.expanduser("~"), ".gradle", "caches")
    for jar in sorted(glob.glob(os.path.join(caches, "*loom*", "*", "minecraft-client.jar")), reverse=True):
        with zipfile.ZipFile(jar) as z:
            try:
                return Image.open(io.BytesIO(z.read("assets/minecraft/textures/font/ascii.png"))).convert("RGBA")
            except KeyError:
                continue
    sys.exit("No Minecraft client jar in the Gradle cache. Build the modern or legacy project once.")


class PixelFont:
    def __init__(self, sheet):
        self.sheet = sheet
        self.cell = sheet.width // 16
        self.widths = {}
        for code in range(256):
            glyph = self.glyph(code)
            box = glyph.getchannel("A").getbbox()
            self.widths[code] = 3 if code == 32 else (box[2] if box else 0)

    def glyph(self, code):
        x, y = (code % 16) * self.cell, (code // 16) * self.cell
        return self.sheet.crop((x, y, x + self.cell, y + self.cell))

    def width(self, text):
        return sum(self.widths[ord(c)] + 1 for c in text) - 1

    def render(self, text, color):
        img = Image.new("RGBA", (self.width(text) + 1, self.cell), (0, 0, 0, 0))
        x = 0
        for c in text:
            g = self.glyph(ord(c))
            ink = Image.new("RGBA", g.size, color + (255,))
            img.paste(ink, (x, 0), g.getchannel("A"))
            x += self.widths[ord(c)] + 1
        return img


def bevel(d, x0, y0, x1, y1, top_left, bottom_right):
    """Draws a 1-unit bevel inside the box [x0, x1) x [y0, y1), in image pixels."""
    d.rectangle((x0, y0, x1 - 1, y0 + U - 1), fill=top_left)
    d.rectangle((x0, y0, x0 + U - 1, y1 - 1), fill=top_left)
    d.rectangle((x0, y1 - U, x1 - 1, y1 - 1), fill=bottom_right)
    d.rectangle((x1 - U, y0, x1 - 1, y1 - 1), fill=bottom_right)


def frame(src, caption, out, font):
    shot = Image.open(src).convert("RGB")
    w, h = shot.size
    text = font.render(caption, TEXT)
    text = text.resize((text.width * FONT_SCALE, text.height * FONT_SCALE), Image.NEAREST)

    edge = 3 * U                                   # outline, highlight, shade
    slot = U                                       # slot bevel around the image
    caption_h = text.height + 2 * PAD * U
    W = w + 2 * (edge + PAD * U + slot)
    H = h + 2 * (edge + slot) + PAD * U + caption_h
    W -= W % U
    H -= H % U

    canvas = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(canvas)
    # Outline with notched corners, as on vanilla panels.
    d.rectangle((U, 0, W - U - 1, H - 1), fill=OUTLINE)
    d.rectangle((0, U, W - 1, H - U - 1), fill=OUTLINE)
    d.rectangle((U, U, W - U - 1, H - U - 1), fill=PANEL)
    bevel(d, U, U, W - U, H - U, LIGHT, SHADE)
    # Restore the panel color at the two mixed corners of the bevel.
    d.rectangle((W - 2 * U, U, W - U - 1, 2 * U - 1), fill=PANEL)
    d.rectangle((U, H - 2 * U, 2 * U - 1, H - U - 1), fill=PANEL)

    sx, sy = edge + PAD * U, edge + PAD * U
    bevel(d, sx, sy, sx + w + 2 * slot, sy + h + 2 * slot, SLOT_DARK, LIGHT)
    canvas.paste(shot, (sx + slot, sy + slot))

    ty = sy + h + 2 * slot + PAD * U
    canvas.alpha_composite(text, (sx, ty))
    canvas.convert("RGB").save(out, optimize=True)


def main(argv):
    if len(argv) < 3:
        sys.exit(__doc__)
    out_dir = argv[1]
    os.makedirs(out_dir, exist_ok=True)
    font = PixelFont(font_sheet())
    for i, spec in enumerate(argv[2:], 1):
        src, caption = spec.split("=", 1)
        name = "%02d-%s" % (i, os.path.splitext(os.path.basename(src))[0].split("-", 1)[-1])
        out = os.path.join(out_dir, name + ".png")
        frame(src, caption, out, font)
        print(out)


if __name__ == "__main__":
    main(sys.argv)
