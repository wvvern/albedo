"""Compares the screenshots of two runs, for the 'diff' subcommand of python -m tools image. Needs Pillow."""

from pathlib import Path

from PIL import Image, ImageChops, ImageStat


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
