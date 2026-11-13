"""One run of a hidden game with a command file: its harness folder, the wait for its end, and what it left behind.

The dev run of a target and the production run with OptiFine both use this, each with its own Launch.
"""

import shutil
import struct
import sys
import tempfile
from collections.abc import Callable
from pathlib import Path
from typing import Protocol

from tools import project
from tools.harness import protocol
from tools.harness.desktop import Launch
from tools.harness.verdict import Evidence
from tools.report import ToolError

# Each checkout has its own folder, so that runs of the same target in two worktrees do not share a harness folder.
HARNESS_ROOT = Path(tempfile.gettempdir()) / "albedo-harness" / project.ROOT.name
POLL_SECONDS = 3
# The game saves the world before it exits; 26.x can take 10 s or more.
EXIT_SECONDS = 30
# A loader that fails to start leaves the Gradle process running.
LOADER_FAILURE = 'Uncaught exception in thread "main"'
PNG_HEADER_SIZE = 24


class System(Protocol):
    """What a run needs from the operating system."""

    def uses(self, harness: Path) -> bool:
        """Whether a game runs with this harness folder."""
        ...

    def start(self, launch: Launch, harness: Path) -> int: ...

    def alive(self, pid: int) -> bool: ...

    def stop(self, pid: int, harness: Path) -> None: ...

    def sleep(self, seconds: float) -> None: ...

    def now(self) -> float: ...


def read_text(file: Path) -> str:
    return file.read_text(encoding="utf-8", errors="replace") if file.is_file() else ""


def open_harness(name: str, commands: list[str], system: System) -> Path:
    """A new harness folder with the command file and the clip probe of the command cursorfree."""
    harness = HARNESS_ROOT / name
    # A run of the same name from a different shell would lose its folder.
    if system.uses(harness):
        raise ToolError(f"a game with the harness {harness} is already running")
    shutil.rmtree(harness, ignore_errors=True)
    # A process of an earlier run can hold the folder, and its old log and images would then count for this run.
    if harness.exists():
        raise ToolError(f"cannot delete {harness}; stop the process that uses it")
    harness.mkdir(parents=True)
    probe = [sys.executable, "-I", str(Path(__file__).with_name("cursor.py"))]
    (harness / protocol.CLIP_PROBE).write_text("".join(f"{argument}\n" for argument in probe), encoding="utf-8", newline="\n")
    (harness / protocol.COMMANDS).write_text("".join(f"{command}\n" for command in commands), encoding="ascii", newline="\n")
    return harness


def play(launch: Launch, harness: Path, timeout: float, system: System, say: Callable[[str], None]) -> None:
    """Starts the game and returns when it has ended: by itself after quit, or stopped at a failed start or at the timeout."""
    pid = system.start(launch, harness)
    deadline = system.now() + timeout
    while system.now() < deadline:
        system.sleep(POLL_SECONDS)
        if not system.alive(pid):
            break
        if LOADER_FAILURE in read_text(launch.log):
            say("the loader did not start")
            break
        if protocol.ran_quit(read_text(harness / protocol.LOG).splitlines()):
            exit_by = system.now() + EXIT_SECONDS
            while system.now() < exit_by and system.alive(pid):
                system.sleep(1)
            break
    if system.alive(pid):
        say("still running; stopping it")
        system.stop(pid, harness)


def collect(harness: Path, out: Path) -> list[Path]:
    """Copies the screenshots of a run to a new output folder."""
    shutil.rmtree(out, ignore_errors=True)
    out.mkdir(parents=True)
    images = sorted(harness.glob("*.png"))
    for image in images:
        shutil.copy2(image, out)
    return images


def png_size(file: Path) -> tuple[int, int] | None:
    """The width and the height are the first two big-endian numbers of the IHDR chunk, at byte 16."""
    with file.open("rb") as stream:
        header = stream.read(PNG_HEADER_SIZE)
    return struct.unpack(">II", header[16:24]) if len(header) == PNG_HEADER_SIZE else None


def read_evidence(harness: Path, game_log: Path, run_dir: Path) -> Evidence:
    return Evidence(
        harness_log=read_text(harness / protocol.LOG).splitlines(),
        game_log=read_text(game_log),
        taken_commands=read_text(harness / protocol.TAKEN).splitlines(),
        image_sizes={image.name: png_size(image) for image in harness.glob("*.png")},
        has_config=(run_dir / "config" / "albedo" / "config.json").is_file(),
    )
