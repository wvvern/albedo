"""Runs a suite of the test harness in the dev game of each target on a hidden desktop and checks the result.

The default suite builds a test scene in a superflat world and takes screenshots of vanilla, each built-in profile, night with and
without fullbright, and the settings screen. Screenshots go to <out>/<target>/ with result.txt, which starts with PASS or FAIL and then
has each reason. The exit code is 1 when a target fails. tools/harness/verdict.py has what a run must show to pass.

    python -m tools harness fabric-26.3 forge-1.8.9
    python -m tools harness fabric-26.3 forge-1.8.9 --suite sky-sharp --out build/sky-sharp
    python -m tools harness fabric-1.21.11 --compat sodium --extra scene-compat --out build/compat-sodium
    python -m tools harness fabric-26.3 --compat iris --shader-pack C:/temp/pack.zip --extra scene-compat
    python -m tools harness fabric-26.3 --backend vulkan --out build/vulkan
    python -m tools harness all
"""

import argparse
from pathlib import Path

from tools import project
from tools.harness import devrun, files, script, session, verdict
from tools.harness.devrun import RunOptions
from tools.harness.session import System
from tools.harness.windows import Windows
from tools.project import Target
from tools.report import ToolError

ALL = "all"


def run_target(target: Target, options: RunOptions, system: System) -> bool:
    """Runs one target, writes its output folder and tells whether it passed."""

    def launch_of(harness: Path) -> session.Launch:
        devrun.prepare_run_dir(target, options)
        return devrun.launch(target, harness, options)

    suites = options.suites(target)
    out = project.ROOT / options.out / target.name
    commands = script.compose(script.read_suites(suites), target)
    finished = session.run(target.name, target.name, commands, launch_of, out, options.timeout, system)
    evidence = session.read_evidence(finished.harness, finished.launch.log, target.run_dir)
    failures = verdict.judge(evidence, devrun.expectations(target, options))
    result = "FAIL" if failures else "PASS"
    summary = [result, f"target: {target.name}", f"commands: {', '.join(f'{suite}.txt' for suite in suites)}", f"screenshots: {len(finished.images)}"]
    files.write_lines(out / "result.txt", summary + failures)
    for line in [*failures, result]:
        print(f"[{target.name}] {line}", flush=True)
    return not failures


def parse(arguments: list[str]) -> tuple[list[Target], RunOptions]:
    parser = argparse.ArgumentParser(prog="python -m tools harness", description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("targets", nargs="+", metavar="TARGET", help=f'for example fabric-26.3, or "{ALL}" for each target')
    parser.add_argument("--suite", default=RunOptions.suite, help="the suite that runs after the scene (default: %(default)s)")
    parser.add_argument("--extra", nargs="+", default=[], metavar="SUITE", help="more suites, between the scene and the suite")
    parser.add_argument("--out", type=Path, default=RunOptions.out, help="the output folder, from the repository root (default: %(default)s)")
    parser.add_argument("--timeout", type=int, default=RunOptions.timeout, metavar="SECONDS")
    parser.add_argument("--compat", default=RunOptions.compat, metavar="MOD", help="a dev-only renderer mod: sodium, embeddium, rubidium or iris")
    parser.add_argument("--shader-pack", type=Path, help="a shader pack for --compat iris; without it, Iris has shaders off")
    parser.add_argument("--config", type=Path, help="a config.json that the game starts with in place of the default config")
    parser.add_argument("--option", action="append", default=[], metavar="KEY:VALUE", help="one more line of options.txt, for example useVbo:false")
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
        suite=parsed.suite,
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
    for suite in (options.suite, *options.extra):
        script.suite_file(suite)
    if options.shader_pack and "iris" not in options.compat:
        raise ToolError("--shader-pack needs --compat iris")
    if options.loader != "fabric" and any(target.loader != "fabric" for target in targets):
        raise ToolError(f"--loader {options.loader} runs only fabric targets")
    return targets, options


def main(arguments: list[str]) -> int:
    targets, options = parse(arguments)
    system = Windows()
    failed = [target.name for target in targets if not run_target(target, options, system)]
    if failed:
        print(f"FAIL: {', '.join(failed)}")
    return 1 if failed else 0
