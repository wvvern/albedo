"""Builds the release jar of each target and copies the jars to build/libs in the repository root.

Each node has its own Gradle run, and the command stops at the first build that fails.
"""

import argparse
import shutil
from collections.abc import Iterable
from itertools import groupby
from pathlib import Path

from tools import gradle, project
from tools.project import Target

LIBS = project.ROOT / "build" / "libs"


def build(targets: Iterable[Target], libs: Path) -> None:
    mod_version = project.mod_version()
    libs.mkdir(parents=True, exist_ok=True)
    for stale in libs.glob("albedo-*.jar"):
        stale.unlink()
    for (build_dir, node), group in groupby(targets, key=lambda target: (target.build_dir, target.node)):
        loaders = list(group)
        print(f"[{node}] {', '.join(target.loader for target in loaders)}", flush=True)
        gradle.run(build_dir, *(target.release_task for target in loaders))
        for target in loaders:
            shutil.copy2(target.jar(mod_version), libs)
    for jar in sorted(libs.glob("albedo-*.jar")):
        print(jar.name)


def main(arguments: list[str]) -> int:
    parser = argparse.ArgumentParser(prog="python -m tools build", description=__doc__.splitlines()[0])
    parser.add_argument("targets", nargs="*", metavar="TARGET", help="for example fabric-26.3; with no target, all targets")
    names = parser.parse_args(arguments).targets
    wanted = {project.find_target(name) for name in names}
    build([target for target in project.targets() if not names or target in wanted], LIBS)
    return 0
