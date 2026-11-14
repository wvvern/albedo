"""How the tools start Gradle in one of the two builds, and where Gradle keeps its files."""

import os
import subprocess
from pathlib import Path

from tools.report import ToolError

CACHES = Path.home() / ".gradle" / "caches"
# Gradle configures only the projects of the requested tasks, because Loom mixes the mappings of the nodes when one run configures all of them.
ONE_NODE = "--configure-on-demand"


def wrapper(build_dir: Path) -> Path:
    return build_dir / ("gradlew.bat" if os.name == "nt" else "gradlew")


def hidden_command_line(build_dir: Path, task: str, properties: dict[str, str | bool]) -> str:
    """The command line of a task for a hidden desktop, with a -P flag for each property that is set.

    --no-daemon: a daemon from the visible desktop starts the game window on that desktop.
    """
    flags = [f'"{flag}"' if "=" in flag else flag for flag in property_flags(properties)]
    return " ".join([f'"{wrapper(build_dir)}"', "--no-daemon", ONE_NODE, task, *flags])


def property_flags(properties: dict[str, str | bool]) -> list[str]:
    """A -P flag for each property that is set."""
    return [f"-P{name}" if value is True else f"-P{name}={value}" for name, value in properties.items() if value]


def run(build_dir: Path, *arguments: str, log: Path | None = None) -> None:
    """Runs Gradle with its output in this console, or in the file log."""
    command = [str(wrapper(build_dir)), ONE_NODE, *arguments]
    if log is None:
        result = subprocess.run(command, cwd=build_dir, check=False)
    else:
        log.parent.mkdir(parents=True, exist_ok=True)
        with log.open("w", encoding="utf-8") as output:
            result = subprocess.run(command, cwd=build_dir, check=False, stdout=output, stderr=subprocess.STDOUT)
    if result.returncode != 0:
        where = f", output in {log}" if log else ""
        raise ToolError(f"the build failed: gradlew {' '.join(arguments)} in {build_dir}{where}")
