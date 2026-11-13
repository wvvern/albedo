"""The small text files of a run: a read that takes a missing file as empty, and a write with one line end on each system."""

from collections.abc import Iterable
from pathlib import Path


def read_text(file: Path) -> str:
    return file.read_text(encoding="utf-8", errors="replace") if file.is_file() else ""


def read_lines(file: Path) -> list[str]:
    return read_text(file).splitlines()


def write_lines(file: Path, lines: Iterable[str], encoding: str = "ascii") -> None:
    """Writes each line with "\\n" and makes the folder of the file; a character outside the encoding becomes "?"."""
    file.parent.mkdir(parents=True, exist_ok=True)
    file.write_text("".join(f"{line}\n" for line in lines), encoding=encoding, errors="replace", newline="\n")
