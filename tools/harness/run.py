"""Runs a suite of the test harness in the dev game of each target on a hidden desktop and checks the result.

The default suite builds a test scene in a superflat world and takes screenshots of vanilla, each built-in profile, night with and
without fullbright, and the settings screen. Screenshots go to <out>/<target>/ with result.txt, which starts with PASS or FAIL and then
has each reason. The exit code is 1 when a target fails. tools/harness/verdict.py has what a run must show to pass.

The targets run at the same time, each in its own game. With several suites, the game of each target runs them one after the other,
because they share its game folder, and the output of each goes to <out>/<suite>/<target>/.

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
from tools.project import Target
from tools.report import ToolError, say

ALL = "all"


def run_target(target: Target, options: RunOptions, system: System) -> bool:
    """Runs one target, writes its output folder and tells whether it passed."""

    def launch_of(harness: Path) -> session.Launch:
        devrun.prepare_run_dir(target, options)
        return devrun.launch(target, harness, options)

    suites = options.suites(target)
    label = f"{target.name} {options.suite}"
    out = project.ROOT / options.out / target.name
    commands = script.compose(script.read_suites(suites), target)
    finished = session.run(label, target.name, commands, launch_of, out, options.timeout, system)
    evidence = session.read_evidence(finished.harness, finished.launch.log, target.run_dir)
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


def run_suites(target: Target, runs: list[RunOptions], system: System) -> bool:
    """Runs each suite of one target, also after a failed one, and tells whether all passed."""
    passed = True
    for options in runs:
        try:
            passed &= run_target(target, options, system)
        except ToolError as error:
            say(f"{target.name} {options.suite}", f"FAIL: {error}")
            passed = False
    return passed


def parse(arguments: list[str]) -> tuple[list[Target], list[RunOptions]]:
    parser = argparse.ArgumentParser(prog="python -m tools harness", description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("targets", nargs="+", metavar="TARGET", help=f'for example fabric-26.3, or "{ALL}" for each target')
    parser.add_argument("--suite", nargs="+", default=[RunOptions.suite], help="the suites that run after the scene (default: %(default)s)")
    parser.add_argument("--extra", nargs="+", default=[], metavar="SUITE", help="more suites, between the scene and the suite")
    parser.add_argument("--out", type=Path, default=RunOptions.out, help="the output folder, from the repository root (default: %(default)s)")
    parser.add_argument("--timeout", type=int, default=RunOptions.timeout, metavar="SECONDS")
    parser.add_argument("--compat", default=RunOptions.compat, metavar="MOD", help="a dev-only renderer mod: sodium, embeddium, rubidium or iris")
    parser.add_argument("--shader-pack", type=Path, help="a shader pack for --compat iris; without it, Iris has shaders off")
    parser.add_argument("--config", type=Path, help="a config.json that the game starts with in place of the default config")
    parser.add_argument(
        "--option",
        action="append",
        default=[],
        type=rundir.key_value,
        metavar="KEY:VALUE",
        help="one more line of options.txt, for example useVbo:false",
    )
    parser.add_argument("--gui-scale", type=int, default=RunOptions.gui_scale, help="0 is auto; 2 gives the settings screen room for the sky preview")
    parser.add_argument("--backend", choices=("opengl", "vulkan"), default=RunOptions.backend, help="the graphics backend of 26.2 and later")
    parser.add_argument("--vulkan-validation", action="store_true", help="the Vulkan validation layers of the game")
    parser.add_argument(
        "--loader",
        choices=("fabric", "quilt", "quilt-dev"),
        default=RunOptions.loader,
        help="for a fabric target: quilt runs the release jar of the node on Quilt Loader, quilt-dev runs the dev classes on it",
    )
    parsed = parser.parse_args(arguments)
    options = RunOptions(
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
        loader=parsed.loader,
    )
    targets = list(project.targets()) if parsed.targets == [ALL] else [project.find_target(name) for name in parsed.targets]
    for suite in (*parsed.suite, *options.extra):
        script.suite_file(suite)
    if options.shader_pack and "iris" not in options.compat:
        raise ToolError("--shader-pack needs --compat iris")
    if options.loader != "fabric" and any(target.loader != "fabric" for target in targets):
        raise ToolError(f"--loader {options.loader} runs only fabric targets")
    if len(parsed.suite) == 1:
        return targets, [options]
    return targets, [dataclasses.replace(options, suite=suite, out=options.out / suite) for suite in parsed.suite]


def main(arguments: list[str]) -> int:
    # Only a real run needs psutil and Windows, so the tests of parse run without them.
    from tools.harness import processes
    from tools.harness.windows import Windows

    targets, runs = parse(arguments)
    system = Windows()
    processes.wait_for_tool(project.ROOT, "build")
    with ThreadPoolExecutor(max_workers=len(targets)) as pool:
        passed = list(pool.map(lambda target: run_suites(target, runs, system), targets))
    failed = [target.name for target, ok in zip(targets, passed, strict=True) if not ok]
    if failed:
        print(f"FAIL: {', '.join(failed)}")
    return 1 if failed else 0
