"""Runs a suite of the test harness in the dev game of each target on a hidden desktop and checks the result.

The default suite builds a test scene in a superflat world and takes screenshots of vanilla, each built-in profile, night with and
without fullbright, and the settings screen. Screenshots go to <out>/<target>/ with result.txt, which starts with PASS or FAIL and then
has each reason. The exit code is 1 when a target fails. tools/harness/verdict.py has what a run must show to pass.

    python -m tools harness                                   the main targets
    python -m tools harness fabric-26.3 forge-1.8.9 --suite sky-sharp --out build/sky-sharp
    python -m tools harness fabric-1.21.11 --compat sodium --extra scene-compat --out build/compat-sodium
    python -m tools harness fabric-26.3 --compat iris --shader-pack C:/temp/pack.zip --extra scene-compat
    python -m tools harness fabric-26.3 --backend vulkan --out build/vulkan
"""

import argparse
from dataclasses import dataclass
from pathlib import Path

from tools import gradle, project
from tools.harness import rundir, script, session, verdict
from tools.harness.desktop import Launch
from tools.harness.session import System
from tools.harness.verdict import Expectations
from tools.harness.windows import Windows
from tools.project import Target
from tools.report import ToolError

DEFAULT_TARGETS = ("fabric-26.3", "neoforge-26.3", "fabric-1.21.11", "neoforge-1.21.11", "fabric-1.21.1", "neoforge-1.21.1", "forge-1.8.9")
# The first version with the launch argument for the graphics backend; an older game has only OpenGL and no backend line to check.
FIRST_BACKEND_CHOICE = "26.2"


@dataclass(frozen=True)
class RunOptions:
    suite: str = "shots"
    extra: tuple[str, ...] = ()
    out: Path = Path("build/screenshots")
    timeout: int = 600
    compat: str = ""
    shader_pack: Path | None = None
    config: Path | None = None
    option_lines: tuple[str, ...] = ()
    gui_scale: int = 0
    backend: str = ""
    vulkan_validation: bool = False
    loader: str = "fabric"

    def iris(self, target: Target) -> bool:
        return not target.legacy and "iris" in [mod.strip() for mod in self.compat.split(",")]

    def forced_backend(self, target: Target) -> str:
        return self.backend if not target.legacy and target.at_least(FIRST_BACKEND_CHOICE) else ""

    def suites(self, target: Target) -> list[str]:
        return [script.scene_of(target), *self.extra, self.suite]


def prepare_run_dir(target: Target, options: RunOptions) -> None:
    run_dir = target.run_dir
    # A new options file each run, so that no option of an earlier run changes the screenshots.
    game_options = rundir.QUIET_OPTIONS | {"guiScale": str(options.gui_scale)} | rundir.parse_option_lines(list(options.option_lines))
    rundir.write_options(run_dir / "options.txt", game_options)
    # A release jar that an earlier --loader quilt run left here would load next to the classes of the dev run.
    for jar in (run_dir / "mods").glob("albedo-*.jar"):
        jar.unlink()
    if not target.legacy and target.loader != "fabric":
        # The early loading window of Forge and NeoForge does not open on the hidden desktop, so the game makes its window itself.
        rundir.set_toml_key(run_dir / "config" / "fml.toml", "earlyWindowControl", "false")
    if not target.legacy and target.loader == "neoforge":
        # The NeoForge dev run checks each Blaze3D call, but Sodium 0.8.14 casts a texture that this check wraps, so --compat stops it.
        rundir.set_toml_key(run_dir / "config" / "neoforge-client.toml", "enableB3DValidationLayer", "false" if options.compat else "true")
    if options.iris(target):
        rundir.write_iris_properties(run_dir, options.shader_pack)
    rundir.reset_mod_state(run_dir, options.config)


def dev_run(target: Target, harness: Path, options: RunOptions) -> Launch:
    properties = {
        "harness": str(harness),
        "compat": options.compat,
        "backend": options.forced_backend(target),
        "vulkanValidation": options.vulkan_validation,
        "quilt": options.loader != "fabric",
        # The release jar of the node as a mod, not the classes of the dev run.
        "quiltJar": options.loader == "quilt",
    }
    return Launch(gradle.hidden_command_line(target.build_dir, target.run_task, properties), target.build_dir, harness / "gradle.log")


def expectations(target: Target, options: RunOptions) -> Expectations:
    return Expectations(
        hooks=verdict.required_hooks(target, bool(options.compat)),
        backend=options.forced_backend(target),
        loader=options.loader,
        renderer_mod=bool(options.compat),
        shader_pack=bool(options.shader_pack) and options.iris(target),
    )


def run_target(target: Target, options: RunOptions, system: System) -> bool:
    """Runs one target, writes its output folder and tells whether it passed."""

    def say(message: str) -> None:
        print(f"[{target.name}] {message}", flush=True)

    suites = options.suites(target)
    harness = session.open_harness(target.name, script.compose(script.read_suites(suites), target), system)
    prepare_run_dir(target, options)
    launch = dev_run(target, harness, options)
    say("launching hidden")
    session.play(launch, harness, options.timeout, system, say)

    out = project.ROOT / options.out / target.name
    images = session.collect(harness, out)
    say(f"{len(images)} screenshots -> {out}")
    failures = verdict.judge(session.read_evidence(harness, launch.log, target.run_dir), expectations(target, options))
    for failure in failures:
        say(failure)
    result = "PASS" if not failures else "FAIL"
    lines = [result, f"target: {target.name}", f"commands: {', '.join(f'{suite}.txt' for suite in suites)}", f"screenshots: {len(images)}", *failures]
    (out / "result.txt").write_text("".join(f"{line}\n" for line in lines), encoding="ascii", errors="replace", newline="\n")
    say(result)
    return not failures


def parse(arguments: list[str]) -> tuple[list[Target], RunOptions]:
    parser = argparse.ArgumentParser(prog="python -m tools harness", description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("targets", nargs="*", metavar="TARGET", default=DEFAULT_TARGETS, help=f"default: {' '.join(DEFAULT_TARGETS)}")
    parser.add_argument("--suite", default=RunOptions.suite, help="the suite in tools/harness/suites that runs after the scene (default: shots)")
    parser.add_argument("--extra", nargs="+", default=[], metavar="SUITE", help="more suites, between the scene and the suite")
    parser.add_argument("--out", type=Path, default=RunOptions.out, help="the output folder, from the repository root (default: build/screenshots)")
    parser.add_argument("--timeout", type=int, default=RunOptions.timeout, metavar="SECONDS")
    parser.add_argument("--compat", default="", metavar="MOD", help="a dev-only renderer mod of a modern target: sodium, embeddium, rubidium or iris")
    parser.add_argument("--shader-pack", type=Path, help="a shader pack for --compat iris; without it, Iris has shaders off")
    parser.add_argument("--config", type=Path, help="a config.json that the game starts with in place of the default config")
    parser.add_argument("--option", action="append", default=[], metavar="KEY:VALUE", help="one more line of options.txt, for example useVbo:false")
    parser.add_argument("--gui-scale", type=int, default=0, help="0 is auto; 2 gives the settings screen room for the sky preview")
    parser.add_argument("--backend", choices=("opengl", "vulkan"), default="", help="the graphics backend of 26.2 and later")
    parser.add_argument("--vulkan-validation", action="store_true", help="the Vulkan validation layers of the game")
    parser.add_argument(
        "--loader",
        choices=("fabric", "quilt", "quilt-dev"),
        default="fabric",
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
    targets = [project.find_target(name) for name in parsed.targets]
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
