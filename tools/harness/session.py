"""One run of a hidden game with a command file: its harness folder, the wait for its end, and what it left behind.

The dev run of a target and the production run with OptiFine both use this, each with its own Launch.
"""

import shutil
import struct
import sys
import tempfile
from collections.abc import Callable
from dataclasses import dataclass
from pathlib import Path
from typing import Protocol

from tools import project
from tools.harness import files, protocol, rundir
from tools.harness.verdict import Evidence
from tools.report import ToolError

# Each checkout has its own folder, so that runs of the same target in two worktrees do not share a harness folder.
HARNESS_ROOT = Path(tempfile.gettempdir()) / "albedo-harness" / project.ROOT.name
POLL_SECONDS = 3
DELETE_TRIES = 15
# The game saves the world before it exits; 26.x can take 10 s or more.
EXIT_SECONDS = 30
# A loader that fails to start leaves the Gradle process running.
LOADER_FAILURE = 'Uncaught exception in thread "main"'
PNG_HEADER_SIZE = 24


@dataclass(frozen=True)
class Launch:
    """A command line to run in a folder, with its output in a log file."""

    command_line: str
    cwd: Path
    log: Path
    # Variables for the process on top of the ones of this process.
    environment: tuple[tuple[str, str], ...] = ()


@dataclass(frozen=True)
class Finished:
    harness: Path
    launch: Launch
    images: list[Path]


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


def open_harness(name: str, commands: list[str], system: System) -> Path:
    """A new harness folder with the command file and the clip probe of the command cursorfree."""
    harness = HARNESS_ROOT / name
    # A run of the same name from a different shell would lose its folder.
    if system.uses(harness):
        raise ToolError(f"a game with the harness {harness} is already running")
    # The window fit and the cursor watch of the previous run hold a log in the folder for a few seconds after its game ends.
    for _ in range(DELETE_TRIES):
        shutil.rmtree(harness, ignore_errors=True)
        if not harness.exists():
            break
        system.sleep(1)
    # A process of an earlier run can hold the folder, and its old log and images would then count for this run.
    if harness.exists():
        raise ToolError(f"cannot delete {harness}; stop the process that uses it")
    files.write_lines(harness / protocol.CLIP_PROBE, [sys.executable, "-I", str(Path(__file__).with_name("cursor.py"))], encoding="utf-8")
    files.write_lines(harness / protocol.COMMANDS, commands)
    return harness


def play(launch: Launch, harness: Path, timeout: float, system: System, say: Callable[[str], None]) -> None:
    """Starts the game and returns when it has ended: by itself after quit, or stopped at a failed start or at the timeout."""
    pid = system.start(launch, harness)
    deadline = system.now() + timeout
    while system.now() < deadline:
        system.sleep(POLL_SECONDS)
        if not system.alive(pid):
            break
        if LOADER_FAILURE in files.read_text(launch.log):
            say("the loader did not start")
            break
        if protocol.ran_quit(files.read_lines(harness / protocol.LOG)):
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
    # The folder stays when a viewer holds one of its files, and the run still gets its result.
    out.mkdir(parents=True, exist_ok=True)
    images = sorted(harness.glob("*.png"))
    for image in images:
        shutil.copy2(image, out)
    return images


def run(
    label: str,
    harness_name: str,
    commands: list[str],
    launch_of: Callable[[Path], Launch],
    out: Path,
    timeout: float,
    system: System,
) -> Finished:
    """The sequence of each run: a new harness folder, the game until its end, and its screenshots in the output folder.

    launch_of gets the harness folder after the check that no game uses it, so it is the place to change the game folder.
    """

    def say(message: str) -> None:
        print(f"[{label}] {message}", flush=True)

    harness = open_harness(harness_name, commands, system)
    launch = launch_of(harness)
    say("launching hidden")
    play(launch, harness, timeout, system, say)
    images = collect(harness, out)
    say(f"{len(images)} screenshots -> {out}")
    return Finished(harness, launch, images)


def png_size(file: Path) -> tuple[int, int] | None:
    """The width and the height are the first two big-endian numbers of the IHDR chunk, at byte 16."""
    with file.open("rb") as stream:
        header = stream.read(PNG_HEADER_SIZE)
    return struct.unpack(">II", header[16:24]) if len(header) == PNG_HEADER_SIZE else None


def read_evidence(harness: Path, game_log: Path, run_dir: Path) -> Evidence:
    return Evidence(
        harness_log=files.read_lines(harness / protocol.LOG),
        game_log=files.read_text(game_log),
        taken_commands=files.read_lines(harness / protocol.TAKEN),
        image_sizes={image.name: png_size(image) for image in harness.glob("*.png")},
        has_config=rundir.mod_config(run_dir).is_file(),
    )
