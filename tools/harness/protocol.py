"""The files and the log lines of the game side of the harness, core/src/main/java/dev/albedo/core/dev/Harness.java.

The game runs with -Dalbedo.harness=<folder>. It takes <folder>/commands.txt, renames it to commands.taken, and appends to log.txt:
"> <command>" for each command that it runs, the result lines of the commands, and a line that starts with "!" for each error.
A screenshot is <folder>/<name>.png. The command cursorfree runs the command line of clip-probe.txt, one argument in each line.
"""

import re
from collections.abc import Iterable
from pathlib import Path

COMMANDS = "commands.txt"
TAKEN = "commands.taken"
LOG = "log.txt"
CLIP_PROBE = "clip-probe.txt"
JVM_PROPERTY = "-Dalbedo.harness="
# Same as --width and --height of the run task in modern/gradle/shared.gradle and legacy/version.gradle.kts.
WINDOW_SIZE = (1600, 900)

HOOKS = "hooks"
QUIT = "quit"
SCREENSHOT = "screenshot"
FRAMES = "frames"
# Each command that logs its result in a later frame: the log line of a good result, and how a verdict names such lines.
RESULTS = (
    ("skycompare", re.compile(r"^skycompare .*: same$"), "results with 'same'"),
    ("framechanged", re.compile(r"^framechanged "), "results"),
    ("cursorfree", re.compile(r"^cursorfree: (ok|skipped)"), "results with 'ok'"),
)
# What the command line of a game that a player started has, from a launcher or from a dev run with no harness.
PLAYER_GAME = re.compile(r"--gameDir|KnotClient|net\.minecraft\.client\.main\.Main|launchwrapper\.Launch|BootstrapLauncher")


def names_harness(command_line: str, harness: str | Path) -> bool:
    """Whether a command line names a harness folder, and not a folder whose name only starts with the name of this one."""
    return re.search(re.escape(str(harness)) + r"(?![\w.-])", command_line) is not None


def harness_game(java_command_line: str) -> bool:
    return JVM_PROPERTY in java_command_line


def game_runs(java_command_lines: Iterable[str]) -> bool:
    """Whether a harness game or a game of a player runs, which can each hold the cursor on purpose."""
    return any(harness_game(line) or PLAYER_GAME.search(line) for line in java_command_lines)


def ran_quit(log: Iterable[str]) -> bool:
    return any(line.startswith(f"> {QUIT}") for line in log)


def errors(log: Iterable[str]) -> list[str]:
    return [line for line in log if line.startswith("!")]


def failed_compares(log: Iterable[str]) -> list[str]:
    return [line for line in log if "DIFFERENT" in line]


def hook_lines(log: Iterable[str]) -> list[str]:
    """The lines of the command hooks, each with the counter of each optional hook, for example "hooks: afterSolid=1807 tint=100562"."""
    return [line for line in log if line.startswith(f"{HOOKS}:")]


def hook_ran(hook_line: str, hook: str) -> bool:
    return re.search(rf"\b{re.escape(hook)}=[1-9]", hook_line) is not None


def verb(command: str) -> str:
    """The first word of a command, or "" for an empty line."""
    return command.split(maxsplit=1)[0] if command.strip() else ""


def images_of(command: str, present: Iterable[str]) -> tuple[list[str], bool]:
    """The image files that a taken command made, of the files in the harness folder, and whether all of its images are there."""
    name, *arguments = command.split() or [""]
    if name == SCREENSHOT and arguments:
        image = arguments[0].removesuffix(".png") + ".png"
        found = [image] if image in present else []
        return found, bool(found)
    if name == FRAMES and len(arguments) >= 2:
        found = sorted(image for image in present if image.startswith(f"{arguments[1]}-") and image.endswith(".png"))
        return found, len(found) >= int(arguments[0])
    return [], True
