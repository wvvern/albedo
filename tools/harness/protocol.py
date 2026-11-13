"""The files and the log lines of the game side of the harness, core/src/main/java/dev/albedo/core/dev/Harness.java.

The game runs with -Dalbedo.harness=<folder>. It takes <folder>/commands.txt, renames it to commands.taken, and appends to log.txt:
"> <command>" for each command that it runs, the result lines of the commands, and a line that starts with "!" for each error.
A screenshot is <folder>/<name>.png. The command cursorfree runs the command line of clip-probe.txt, one argument in each line.
"""

import re
from collections.abc import Iterable

COMMANDS = "commands.txt"
TAKEN = "commands.taken"
LOG = "log.txt"
CLIP_PROBE = "clip-probe.txt"
JVM_PROPERTY = "-Dalbedo.harness="
# The game starts at the size of the run task.
WINDOW_SIZE = (1600, 900)

HOOKS = "hooks"
QUIT = "quit"

SKY_SAME = re.compile(r"^skycompare .*: same$")
CURSOR_FREE = re.compile(r"^cursorfree: (ok|skipped)")
# What the command line of a game that a player started has, from a launcher or from a dev run with no harness.
PLAYER_GAME = re.compile(r"--gameDir|KnotClient|net\.minecraft\.client\.main\.Main|launchwrapper\.Launch|BootstrapLauncher")


def harness_game(java_command_line: str, harness: object = "") -> bool:
    """Whether a Java process is a harness game, of one harness folder or of any."""
    return f"{JVM_PROPERTY}{harness}" in java_command_line


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


def count_results(log: Iterable[str], result: re.Pattern[str] | str) -> int:
    """How many log lines are the result of a command, by a pattern or by the start of the line."""
    if isinstance(result, str):
        return sum(line.startswith(result) for line in log)
    return sum(result.search(line) is not None for line in log)


def words(command: str) -> list[str]:
    return command.split()


def images_of(command: str, present: Iterable[str]) -> tuple[list[str], bool]:
    """The image files that a taken command made, of the files in the harness folder, and whether all of its images are there."""
    verb, *arguments = words(command) or [""]
    if verb == "screenshot" and arguments:
        name = arguments[0].removesuffix(".png") + ".png"
        found = [name] if name in present else []
        return found, bool(found)
    if verb == "frames" and len(arguments) >= 2:
        found = sorted(name for name in present if name.startswith(f"{arguments[1]}-") and name.endswith(".png"))
        return found, len(found) >= int(arguments[0])
    return [], True
