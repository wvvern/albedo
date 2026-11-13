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
    flags = [f"-P{name}" if value is True else f'"-P{name}={value}"' for name, value in properties.items() if value]
    return " ".join([f'"{wrapper(build_dir)}"', "--no-daemon", ONE_NODE, task, *flags])


def run(build_dir: Path, *arguments: str) -> None:
    result = subprocess.run([str(wrapper(build_dir)), ONE_NODE, *arguments], cwd=build_dir, check=False)
    if result.returncode != 0:
        raise ToolError(f"the build failed: gradlew {' '.join(arguments)} in {build_dir}")
