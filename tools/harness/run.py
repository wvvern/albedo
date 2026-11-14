"""Runs a suite of the test harness in the dev game of each target on a hidden desktop and checks the result.

The default suite builds a test scene in a superflat world and takes screenshots of vanilla, each built-in profile, night with and
without fullbright, and the settings screen. Screenshots go to <out>/<target>/ with result.txt, which starts with PASS or FAIL and then
has each reason. The exit code is 1 when a target fails. tools/harness/verdict.py has what a run must show to pass.

Each target runs in its own game, and all targets run at the same time. With several suites, each suite of each target runs in its own
game, in the game folder runs/<suite> of the node. All of these games run at the same time, as many as the free memory and the
hardware threads hold.
The output of each goes to <out>/<suite>/<target>/.

    python -m tools harness fabric-26.3 forge-1.8.9
    python -m tools harness fabric-26.3 forge-1.8.9 --suite sky-sharp --out build/sky-sharp
    python -m tools harness all --suite shots chroma edges --out build/release
    python -m tools harness fabric-1.21.11 --compat sodium --extra scene-compat --out build/compat-sodium
    python -m tools harness fabric-26.3 --compat iris --shader-pack C:/temp/pack.zip --extra scene-compat
    python -m tools harness fabric-26.3 --backend vulkan --out build/vulkan
    python -m tools harness all
"""

import argparse
import dataclasses
import shutil
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from tools import project
from tools.harness import devrun, files, rundir, script, session, verdict
from tools.harness.devrun import RunOptions
from tools.harness.session import System
from tools.harness.verdict import Loader
from tools.project import Target
from tools.report import ToolError, say

ALL = "all"
# The memory of one hidden game and its Gradle process; the free memory divided by this is the number of games that run at the same time.
GAME_BYTES = 3 * 1024**3
# The hardware threads for each game. With more games, the sky bake and the game start fall behind the waits of the suites.
THREADS_PER_GAME = 1.5


def run_target(target: Target, options: RunOptions, system: System) -> bool:
    """Runs one target, writes its output folder and tells whether it passed."""

    def launch_of(harness: Path) -> session.Launch:
        devrun.prepare_run_dir(target, options)
        return devrun.launch(target, harness, options)

    suites = options.suites(target)
    label = f"{target.name} {options.suite}"
    out = project.ROOT / options.out / target.name
    commands = script.compose(script.read_suites(suites), target)
    finished = session.run(label, options.harness_name(target), commands, launch_of, out, options.timeout, system)
    evidence = session.read_evidence(finished.harness, finished.launch.log, options.run_dir(target))
    failures = verdict.judge(evidence, devrun.expectations(target, options))
    result = "FAIL" if failures else "PASS"
    summary = [result, f"target: {target.name}", f"commands: {', '.join(f'{suite}.txt' for suite in suites)}", f"screenshots: {len(finished.images)}"]
    files.write_lines(out / "result.txt", summary + failures)
    # The next suite of the target starts a new harness folder, so the output folder keeps the log of a failed run.
    if failures and finished.launch.log.is_file():
        shutil.copy2(finished.launch.log, out)
    for line in [*failures, result]:
        say(label, line)
    return not failures


def run_job(target: Target, options: RunOptions, system: System) -> bool:
    """Runs one suite of one target and tells whether it passed; an error fails only this run."""
    try:
        return run_target(target, options, system)
    except ToolError as error:
        say(f"{target.name} {options.suite}", f"FAIL: {error}")
        return False


def game_slots(jobs: int, free_bytes: int, threads: int) -> int:
    return max(1, min(jobs, free_bytes // GAME_BYTES, int(threads / THREADS_PER_GAME)))


def parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="python -m tools harness", description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("targets", nargs="+", metavar="TARGET", help=f'for example fabric-26.3, or "{ALL}" for each target')
    parser.add_argument("--suite", nargs="+", default=[RunOptions.suite], help="the suites that run after the scene (default: %(default)s)")
    parser.add_argument("--extra", nargs="+", default=[], metavar="SUITE", help="more suites, between the scene and the suite")
    parser.add_argument("--out", type=Path, default=RunOptions.out, help="the output folder, from the repository root (default: %(default)s)")
    parser.add_argument("--timeout", type=int, default=RunOptions.timeout, metavar="SECONDS")
    parser.add_argument(
        "--compat", default=RunOptions.compat, metavar="MOD", help="a dev-only renderer mod: sodium, embeddium, rubidium, iris or oculus"
    )
    parser.add_argument("--shader-pack", type=Path, help="a shader pack for --compat iris; without it, Iris has shaders off")
    parser.add_argument("--config", type=Path, help="a config.json that the game starts with in place of the default config")
    rundir.add_option_argument(parser, "useVbo:false")
    parser.add_argument("--gui-scale", type=int, default=RunOptions.gui_scale, help="0 is auto; 2 gives the settings screen room for the sky preview")
    parser.add_argument("--backend", choices=("opengl", "vulkan"), default=RunOptions.backend, help="the graphics backend of 26.2 and later")
    parser.add_argument("--vulkan-validation", action="store_true", help="the Vulkan validation layers of the game")
    parser.add_argument(
        "--loader",
        choices=[loader.value for loader in Loader],
        default=RunOptions.loader.value,
        help="for a fabric target: quilt runs the release jar of the node on Quilt Loader, quilt-dev runs the dev classes on it",
    )
    return parser


def options_of(parsed: argparse.Namespace) -> RunOptions:
    """The options of the first suite."""
    return RunOptions(
        suite=parsed.suite[0],
        extra=tuple(parsed.extra),
        out=parsed.out,
        timeout=parsed.timeout,
        compat=parsed.compat,
        shader_pack=parsed.shader_pack.resolve() if parsed.shader_pack else None,
        config=parsed.config.resolve() if parsed.config else None,
        option_lines=tuple(parsed.option),
        gui_scale=parsed.gui_scale,
        backend=parsed.backend,
        vulkan_validation=parsed.vulkan_validation,
        loader=Loader(parsed.loader),
    )


def check(targets: list[Target], options: RunOptions, suites: list[str]) -> None:
    """Raises a ToolError for a suite with no file or for options that do not go together."""
    for suite in (*suites, *options.extra):
        script.suite_file(suite)
    if options.shader_pack and "iris" not in options.compat:
        raise ToolError("--shader-pack needs --compat iris")
    if options.loader.on_quilt and any(target.loader != "fabric" for target in targets):
        raise ToolError(f"--loader {options.loader} runs only fabric targets")


def parse(arguments: list[str]) -> tuple[list[Target], list[RunOptions]]:
    parsed = parser().parse_args(arguments)
    options = options_of(parsed)
    targets = list(project.targets()) if parsed.targets == [ALL] else [project.find_target(name) for name in parsed.targets]
    check(targets, options, parsed.suite)
    if len(parsed.suite) == 1:
        return targets, [options]
    return targets, [dataclasses.replace(options, suite=suite, out=options.out / suite, own_folder=True) for suite in parsed.suite]


def main(arguments: list[str]) -> int:
    # Only a real run needs psutil and Windows, so the tests of parse run without them.
    import psutil

    from tools.harness import processes
    from tools.harness.windows import Windows

    targets, runs = parse(arguments)
    system = Windows()
    processes.wait_for_tool(project.ROOT, "build")
    # The first suite of each target starts first, so a slow target does not wait behind the other targets.
    jobs = [(target, options) for options in runs for target in targets]
    with ThreadPoolExecutor(max_workers=game_slots(len(jobs), psutil.virtual_memory().available, psutil.cpu_count())) as pool:
        passed = list(pool.map(lambda job: run_job(*job, system), jobs))
    failed = list(dict.fromkeys(target.name for (target, _), ok in zip(jobs, passed, strict=True) if not ok))
    if failed:
        print(f"FAIL: {', '.join(failed)}")
    return 1 if failed else 0
