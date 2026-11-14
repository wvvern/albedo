"""Runs the release jar of the 1.8.9 build in a production Forge client, with or without OptiFine, on a hidden desktop.

OptiFine patches the obfuscated game classes, so it does not run in the dev client, which has MCP names. This command starts the game
as the launcher does: launchwrapper, the Forge universal jar and the obfuscated 1.8.9 jar. The game runs offline, with no login.

Before you start:
1. Install Minecraft 1.8.9 and Forge 1.8.9 one time with the official launcher. The command reads the versions and libraries folders
   of %APPDATA%/.minecraft, and also finds libraries in the Gradle cache of legacy/.
2. Put an OptiFine 1.8.9 jar (for example HD U M6 pre2) in legacy/build/optifine/. Get it from optifine.net.
3. Run: python -m tools build forge-1.8.9

Screenshots and logs go to <out>/<variant>/. The variants are OptiFine settings:
  none         no OptiFine
  default      OptiFine with its default settings
  quake        Fast Render, smooth lighting off, Custom Colors, fancy Connected and Natural Textures, Fast graphics, Clear Water, fancy
               Better Grass, Dynamic Lights off
  quake-ao     quake with smooth lighting on
  quake-fancy  quake with Fancy graphics
"""

import argparse
import shutil
from dataclasses import dataclass
from pathlib import Path

from tools import project
from tools.harness import files, protocol, rundir, script, session
from tools.harness.devrun import RunOptions
from tools.harness.launch import Launch
from tools.harness.prodrun import MINECRAFT, WORK, Installation, find_java_8, prepare_game_dir, production_client
from tools.harness.session import System
from tools.project import Target
from tools.report import ToolError

VARIANTS = ("none", "default", "quake", "quake-ao", "quake-fancy")


@dataclass(frozen=True)
class OptiFineOptions:
    suite: str = RunOptions.suite
    out: Path = Path("build/compat-optifine")
    timeout: int = RunOptions.timeout
    mods: Path | None = None
    forge: Path | None = None
    dump_classes: bool = False
    option_lines: tuple[str, ...] = ()


def run_variant(variant: str, target: Target, release: Path, options: OptiFineOptions, java: Path, system: System) -> None:
    installation = Installation.of_this_user()
    game = WORK / ("game-mods" if options.mods else "game")

    def launch_of(harness: Path) -> Launch:
        prepare_game_dir(game, release, variant, options.mods, options.option_lines)
        return production_client(java, installation, options.forge or installation.forge_jar(), game, harness, options.dump_classes)

    commands = script.compose(script.read_suites([script.scene_of(target), options.suite]), target)
    out = project.ROOT / options.out / variant
    finished = session.run(variant, f"optifine-{MINECRAFT}-{variant}", commands, launch_of, out, options.timeout, system)
    # Essential saves screenshots later on its own thread, so the harness can move an empty file; the late copy is the full one.
    for late in (finished.harness / "screenshots").glob("*.png"):
        if late.stat().st_size > 0:
            shutil.copy2(late, out)
    harness_log = finished.harness / protocol.LOG
    for log in (finished.launch.log, harness_log):
        if log.is_file():
            shutil.copy2(log, out)
    for error in protocol.errors(files.read_lines(harness_log)):
        print(f"[{variant}] {error}", flush=True)


def parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="python -m tools optifine", description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--variants", nargs="+", choices=VARIANTS, default=VARIANTS, metavar="VARIANT")
    suite_help = "the suite in tools/harness/suites that runs after the scene (default: %(default)s)"
    parser.add_argument("--suite", default=OptiFineOptions.suite, help=suite_help)
    parser.add_argument("--out", type=Path, default=OptiFineOptions.out, help="the output folder, from the repository root")
    parser.add_argument("--java", type=Path, help="the java.exe of a Java 8 (default: one in the usual folders)")
    parser.add_argument("--mods", type=Path, help='a folder of jars to add, for example the mods of a player; with its own OptiFine jar, use "none"')
    parser.add_argument("--forge", type=Path, help="a Forge 1.8.9 universal jar in place of the installed one (some mods need 11.15.1.2318)")
    parser.add_argument("--dump-classes", action="store_true", help="the transformed classes go to CLASSLOADER_TEMP of the game folder")
    parser.add_argument("--timeout", type=int, default=OptiFineOptions.timeout, metavar="SECONDS")
    rundir.add_option_argument(parser, "useVbo:true")
    return parser


def options_of(parsed: argparse.Namespace) -> OptiFineOptions:
    return OptiFineOptions(
        suite=parsed.suite,
        out=parsed.out,
        timeout=parsed.timeout,
        mods=parsed.mods,
        forge=parsed.forge,
        dump_classes=parsed.dump_classes,
        option_lines=tuple(parsed.option),
    )


def main(arguments: list[str]) -> int:
    # Only a real run needs psutil and Windows, so the tests of the options run without them.
    from tools.harness.windows import Windows

    parsed = parser().parse_args(arguments)
    options = options_of(parsed)
    system = Windows()
    java = parsed.java or find_java_8()
    target = project.find_target(f"forge-{MINECRAFT}")
    release = target.jar(project.mod_version())
    if not release.is_file():
        raise ToolError(f"no {release.name}; run: python -m tools build {target.name}")
    for variant in parsed.variants:
        run_variant(variant, target, release, options, java, system)
    return 0
