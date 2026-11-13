"""How the tools start Gradle in one of the two builds."""

import os
import subprocess
from pathlib import Path

from tools.report import ToolError

# Gradle configures only the projects of the requested tasks, because Loom mixes the mappings of the nodes when one run configures all of them.
ONE_NODE = "--configure-on-demand"


def wrapper(build_dir: Path) -> Path:
    return build_dir / ("gradlew.bat" if os.name == "nt" else "gradlew")


def run(build_dir: Path, *arguments: str) -> None:
    result = subprocess.run([str(wrapper(build_dir)), ONE_NODE, *arguments], cwd=build_dir, check=False)
    if result.returncode != 0:
        raise ToolError(f"the build failed: gradlew {' '.join(arguments)} in {build_dir}")
