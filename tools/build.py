"""Builds the release jar of each target and copies the jars to one folder.

Each node has its own Gradle run, and up to JOBS of them run at the same time, each with its output in build/logs/<node>.log. A
single node writes its output to the console. The jars are copied only when all builds pass. Arguments after "--" go to Gradle.

    python -m tools build                                    all targets -> build/libs
    python -m tools build fabric-26.3 neoforge-26.3
    python -m tools build forge-1.8.9 --mod-version 1.2.0 --out dist -- --offline
"""

import argparse
import shutil
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from tools import gradle, project
from tools.project import Node, Target
from tools.report import ToolError, say

LIBS = Path("build/libs")
LOGS = Path("build/logs")
# Each Gradle run starts a daemon with the heap of org.gradle.jvmargs in modern/gradle.properties (6 GB).
JOBS = 8


def wait_for_harness() -> None:
    """Waits until no harness of this checkout runs, because the Gradle run of each of its games writes the build output of its node.

    Without psutil (CI) no harness runs here.
    """
    try:
        from tools.harness import processes
    except ImportError:
        return
    processes.wait_for_tool(project.ROOT, "harness")


def build_node(node: Node, wanted: list[Target], mod_version: str | None, gradle_arguments: list[str], log: Path | None) -> str | None:
    """Runs the Gradle build of one node, and gives the reason when it fails."""
    say(node.name, ", ".join(target.loader for target in wanted))
    version = [f"-P{wanted[0].mod_version_property}={mod_version}"] if mod_version else []
    try:
        gradle.run(node.build_dir, *version, *gradle_arguments, *(target.release_task for target in wanted), log=log)
    except ToolError as error:
        say(node.name, "FAIL")
        return str(error)
    say(node.name, "done")
    return None


def build(targets: set[Target], mod_version: str | None, out: Path, gradle_arguments: list[str]) -> None:
    wait_for_harness()
    jar_version = mod_version or project.mod_version()
    work = [(node, [target for target in node.targets if target in targets]) for node in project.nodes()]
    work = [(node, wanted) for node, wanted in work if wanted]

    def run(node: Node, wanted: list[Target]) -> str | None:
        log = project.ROOT / LOGS / f"{node.name}.log" if len(work) > 1 else None
        return build_node(node, wanted, mod_version, gradle_arguments, log)

    with ThreadPoolExecutor(max_workers=JOBS) as pool:
        failures = [reason for reason in pool.map(lambda item: run(*item), work) if reason]
    if failures:
        raise ToolError("\n".join(failures))
    out.mkdir(parents=True, exist_ok=True)
    for _, wanted in work:
        for target in wanted:
            # The jar of an older mod version would stay next to the new one.
            for stale in out.glob(target.jar_name("*")):
                stale.unlink()
            shutil.copy2(target.jar(jar_version), out)
    for jar in sorted(out.glob(project.JAR_GLOB)):
        print(jar.name)


def main(arguments: list[str]) -> int:
    cut = arguments.index("--") if "--" in arguments else len(arguments)
    own, gradle_arguments = arguments[:cut], arguments[cut + 1 :]
    parser = argparse.ArgumentParser(prog="python -m tools build", description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("targets", nargs="*", metavar="TARGET", help="for example fabric-26.3; with no target, all targets")
    parser.add_argument("--mod-version", help="the mod version of the jars (default: mod_version of modern/gradle.properties)")
    parser.add_argument("--out", type=Path, default=LIBS, help="the folder for the jars, from the repository root (default: build/libs)")
    options = parser.parse_args(own)
    targets = {project.find_target(name) for name in options.targets} or set(project.targets())
    build(targets, options.mod_version, project.ROOT / options.out, gradle_arguments)
    return 0
