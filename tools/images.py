"""Makes the images of the store page from screenshots, and compares the screenshots of two runs. Needs Pillow.

frame   puts each screenshot in a Minecraft-style GUI panel with a pixel caption, for the gallery
rim     gives an image rounded corners, a thin dark rim and a soft shadow, for the top image of the description
split   makes one image from equal vertical strips of screenshots from the same camera
diff    prints the difference of each pair of PNG files with the same relative path in two folders
"""

import argparse
import io
import zipfile
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageStat

from tools import gradle
from tools.report import ToolError

Color = tuple[int, int, int]

GUI_PIXEL = 4
PANEL: Color = (198, 198, 198)
LIGHT: Color = (255, 255, 255)
SHADE: Color = (85, 85, 85)
OUTLINE: Color = (0, 0, 0)
SLOT_DARK: Color = (55, 55, 55)
CAPTION: Color = (64, 64, 64)
FONT_PIXEL = 4
PANEL_PADDING = 6 * GUI_PIXEL

RIM_RADIUS = 18
RIM_WIDTH = 1
RIM_COLOR = (10, 10, 12, 255)
SHADOW_BLUR = 24
SHADOW_ALPHA = 90
# The corner mask is drawn larger and scaled down, for smooth corners.
SUPERSAMPLING = 4

STRIP_LINE = 4
STRIP_LINE_COLOR: Color = (0, 0, 0)


def minecraft_font_sheet() -> Image.Image:
    for jar in sorted(gradle.CACHES.glob("*loom*/*/minecraft-client.jar"), reverse=True):
        with zipfile.ZipFile(jar) as archive:
            if "assets/minecraft/textures/font/ascii.png" in archive.namelist():
                return Image.open(io.BytesIO(archive.read("assets/minecraft/textures/font/ascii.png"))).convert("RGBA")
    raise ToolError("no Minecraft client jar in the Gradle cache; build the modern or legacy project once")


class PixelFont:
    """The ASCII font of the game, 16 glyphs in each row of its sheet."""

    def __init__(self, sheet: Image.Image):
        self.sheet = sheet
        self.cell = sheet.width // 16
        self.widths = {code: self.glyph_width(code) for code in range(256)}

    def glyph(self, code: int) -> Image.Image:
        x, y = (code % 16) * self.cell, (code // 16) * self.cell
        return self.sheet.crop((x, y, x + self.cell, y + self.cell))

    def glyph_width(self, code: int) -> int:
        if chr(code) == " ":
            return 3
        box = self.glyph(code).getchannel("A").getbbox()
        return box[2] if box else 0

    def render(self, text: str, color: Color) -> Image.Image:
        width = sum(self.widths[ord(character)] + 1 for character in text)
        image = Image.new("RGBA", (width, self.cell), (0, 0, 0, 0))
        x = 0
        for character in text:
            glyph = self.glyph(ord(character))
            image.paste(Image.new("RGBA", glyph.size, (*color, 255)), (x, 0), glyph.getchannel("A"))
            x += self.widths[ord(character)] + 1
        return image


def bevel(draw: ImageDraw.ImageDraw, x0: int, y0: int, x1: int, y1: int, top_left: Color, bottom_right: Color) -> None:
    """Draws a bevel of one GUI pixel inside the box [x0, x1) x [y0, y1)."""
    draw.rectangle((x0, y0, x1 - 1, y0 + GUI_PIXEL - 1), fill=top_left)
    draw.rectangle((x0, y0, x0 + GUI_PIXEL - 1, y1 - 1), fill=top_left)
    draw.rectangle((x0, y1 - GUI_PIXEL, x1 - 1, y1 - 1), fill=bottom_right)
    draw.rectangle((x1 - GUI_PIXEL, y0, x1 - 1, y1 - 1), fill=bottom_right)


def frame(shot: Image.Image, caption: str, font: PixelFont) -> Image.Image:
    shot = shot.convert("RGB")
    text = font.render(caption, CAPTION)
    text = text.resize((text.width * FONT_PIXEL, text.height * FONT_PIXEL), Image.NEAREST)

    unit = GUI_PIXEL
    # The outline, the highlight and the shade of the panel, and the bevel of the slot around the image.
    edge, slot = 3 * unit, unit
    width = shot.width + 2 * (edge + PANEL_PADDING + slot)
    height = shot.height + 2 * (edge + slot) + PANEL_PADDING + text.height + 2 * PANEL_PADDING
    width -= width % unit
    height -= height % unit

    canvas = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    draw = ImageDraw.Draw(canvas)
    # The outline has notched corners, as on vanilla panels.
    draw.rectangle((unit, 0, width - unit - 1, height - 1), fill=OUTLINE)
    draw.rectangle((0, unit, width - 1, height - unit - 1), fill=OUTLINE)
    draw.rectangle((unit, unit, width - unit - 1, height - unit - 1), fill=PANEL)
    bevel(draw, unit, unit, width - unit, height - unit, LIGHT, SHADE)
    # The two corners where the highlight meets the shade have the panel color.
    draw.rectangle((width - 2 * unit, unit, width - unit - 1, 2 * unit - 1), fill=PANEL)
    draw.rectangle((unit, height - 2 * unit, 2 * unit - 1, height - unit - 1), fill=PANEL)

    left = top = edge + PANEL_PADDING
    bevel(draw, left, top, left + shot.width + 2 * slot, top + shot.height + 2 * slot, SLOT_DARK, LIGHT)
    canvas.paste(shot, (left + slot, top + slot))
    canvas.alpha_composite(text, (left, top + shot.height + 2 * slot + PANEL_PADDING))
    return canvas.convert("RGB")


def rounded_mask(width: int, height: int, radius: int) -> Image.Image:
    scale = SUPERSAMPLING
    mask = Image.new("L", (width * scale, height * scale), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, width * scale - 1, height * scale - 1), radius * scale, fill=255)
    return mask.resize((width, height), Image.LANCZOS)


def rim(image: Image.Image) -> Image.Image:
    image = image.convert("RGBA")
    width, height = image.width + 2 * RIM_WIDTH, image.height + 2 * RIM_WIDTH
    padding = SHADOW_BLUR * 2
    canvas = Image.new("RGBA", (width + 2 * padding, height + 2 * padding), (0, 0, 0, 0))
    outline = rounded_mask(width, height, RIM_RADIUS)
    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    shadow.paste((0, 0, 0, SHADOW_ALPHA), (padding, padding + SHADOW_BLUR // 3), outline)
    canvas = Image.alpha_composite(canvas, shadow.filter(ImageFilter.GaussianBlur(SHADOW_BLUR / 2)))
    layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    layer.paste(RIM_COLOR, (padding, padding), outline)
    layer.paste(image, (padding + RIM_WIDTH, padding + RIM_WIDTH), rounded_mask(image.width, image.height, RIM_RADIUS - RIM_WIDTH))
    return Image.alpha_composite(canvas, layer)


def split(shots: list[Image.Image]) -> Image.Image:
    """Strip i comes from the same place in image i, so the blocks and mobs continue across the lines between the strips."""
    shots = [shot.convert("RGB") for shot in shots]
    width, height = shots[0].size
    if any(shot.size != (width, height) for shot in shots):
        raise ToolError("all images must have the same size")
    canvas = Image.new("RGB", (width, height), STRIP_LINE_COLOR)
    count = len(shots)
    for index, shot in enumerate(shots):
        # Each line takes half of its width from the strip on each side.
        left = index * width // count + (STRIP_LINE // 2 if index > 0 else 0)
        right = (index + 1) * width // count - (STRIP_LINE - STRIP_LINE // 2 if index < count - 1 else 0)
        canvas.paste(shot.crop((left, 0, right, height)), (left, 0))
    return canvas


def difference(before: Image.Image, after: Image.Image, step: int) -> tuple[float, float, int]:
    """The mean difference of the color values (0 to 255), the percent of the pixels where a color value differs by more than step, and the
    largest difference of a color value."""
    delta = ImageChops.difference(before.convert("RGB"), after.convert("RGB"))
    red, green, blue = delta.split()
    largest_of_pixel = ImageChops.lighter(ImageChops.lighter(red, green), blue).histogram()
    pixels = delta.width * delta.height
    mean = sum(ImageStat.Stat(delta).sum) / (pixels * 3)
    return mean, 100 * sum(largest_of_pixel[step + 1 :]) / pixels, max(value for value, count in enumerate(largest_of_pixel) if count)


def diff_lines(before: Path, after: Path, step: int) -> list[str]:
    names = sorted({file.relative_to(folder).as_posix() for folder in (before, after) for file in folder.rglob("*.png")})
    lines = ["mean\tpercent\tmax\tfile"]
    for name in names:
        if not (before / name).is_file():
            lines.append(f"! only in the second folder: {name}")
        elif not (after / name).is_file():
            lines.append(f"! only in the first folder: {name}")
        else:
            with Image.open(before / name) as first, Image.open(after / name) as second:
                if first.size != second.size:
                    lines.append(f"! different sizes: {name}")
                else:
                    mean, percent, largest = difference(first, second, step)
                    lines.append(f"{mean:.3f}\t{percent:.3f}\t{largest}\t{name}")
    return lines


def frame_command(options: argparse.Namespace) -> None:
    options.out_dir.mkdir(parents=True, exist_ok=True)
    font = PixelFont(minecraft_font_sheet())
    for number, pair in enumerate(options.images, 1):
        source, _, caption = pair.partition("=")
        # The number of the gallery image takes the place of the number of the screenshot.
        out = options.out_dir / f"{number:02d}-{Path(source).stem.split('-', 1)[-1]}.png"
        frame(Image.open(source), caption, font).save(out, optimize=True)
        print(out)


def rim_command(options: argparse.Namespace) -> None:
    rim(Image.open(options.image)).save(options.out)


def split_command(options: argparse.Namespace) -> None:
    split([Image.open(image) for image in options.images]).save(options.out, optimize=True)
    print(options.out)


def diff_command(options: argparse.Namespace) -> None:
    print("\n".join(diff_lines(options.before, options.after, options.step)))


def main(arguments: list[str]) -> int:
    parser = argparse.ArgumentParser(prog="python -m tools image", description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    commands = parser.add_subparsers(dest="command", required=True)

    framed = commands.add_parser("frame")
    framed.add_argument("out_dir", type=Path)
    framed.add_argument("images", nargs="+", metavar="IMAGE=CAPTION")
    framed.set_defaults(run=frame_command)

    rimmed = commands.add_parser("rim")
    rimmed.add_argument("out", type=Path)
    rimmed.add_argument("image", type=Path)
    rimmed.set_defaults(run=rim_command)

    strips = commands.add_parser("split")
    strips.add_argument("out", type=Path)
    strips.add_argument("images", nargs="+", type=Path, metavar="IMAGE")
    strips.set_defaults(run=split_command)

    compared = commands.add_parser("diff")
    compared.add_argument("before", type=Path)
    compared.add_argument("after", type=Path)
    compared.add_argument("--step", type=int, default=16, help='a pixel counts in "percent" when a color value differs by more than this')
    compared.set_defaults(run=diff_command)

    options = parser.parse_args(arguments)
    options.run(options)
    return 0
