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
import json
import os
import shutil
import zipfile
from dataclasses import dataclass
from pathlib import Path

from tools import project
from tools.harness import protocol, rundir, script, session
from tools.harness.desktop import Launch
from tools.harness.windows import Windows
from tools.report import ToolError

VARIANTS = ("none", "default", "quake", "quake-ao", "quake-fancy")
MINECRAFT = "1.8.9"
WORK = project.LEGACY / "build" / "optifine"
GAME_OPTIONS = {
    "narrator": "0",
    "soundCategory_master": "0.0",
    "pauseOnLostFocus": "false",
    "fullscreen": "false",
    "guiScale": "0",
    "fancyGraphics": "true",
    "ao": "2",
}
QUAKE_OPTIFINE_OPTIONS = {
    "ofFastRender": "true",
    "ofCustomColors": "true",
    "ofConnectedTextures": "2",
    "ofNaturalTextures": "true",
    "ofClearWater": "true",
    "ofBetterGrass": "2",
    "ofDynamicLights": "3",
}
DUMP_CLASSES = ("-Dlegacy.debugClassLoading=true", "-Dlegacy.debugClassLoadingSave=true", "-Dmixin.debug.verbose=true")
JAVA_8_FOLDER = ("jdk8", "jdk-8", "jre8", "jre-8", "jdk1.8", "jdk-1.8", "jre1.8", "jre-1.8")


@dataclass(frozen=True)
class Installation:
    """The folders of the launcher and of Gradle that have the files of a production client."""

    minecraft: Path
    gradle_caches: Path

    @classmethod
    def of_this_user(cls) -> "Installation":
        return cls(Path(os.environ["APPDATA"]) / ".minecraft", Path.home() / ".gradle" / "caches")

    def library(self, coordinate: str) -> Path:
        group, artifact, version = coordinate.split(":")[:3]
        file = f"{artifact}-{version}.jar"
        installed = self.minecraft / "libraries" / Path(*group.split(".")) / artifact / version / file
        if installed.is_file():
            return installed
        cached = next((self.gradle_caches / "modules-2" / "files-2.1" / group / artifact / version).rglob(file), None)
        if cached is None:
            raise ToolError(f"library not found: {coordinate}")
        return cached

    def forge_jar(self) -> Path:
        jars = sorted((self.minecraft / "libraries" / "net" / "minecraftforge" / "forge").rglob(f"forge-{MINECRAFT}-*.jar"), key=lambda jar: jar.name)
        if not jars:
            raise ToolError(f"Forge {MINECRAFT} is not installed in {self.minecraft}")
        return jars[-1]

    def class_path(self, forge_jar: Path) -> str:
        """The libraries of Forge and of the game, each one time, then Forge and the game."""
        with zipfile.ZipFile(forge_jar) as archive:
            forge = json.loads(archive.read("version.json"))
        vanilla = json.loads((self.minecraft / "versions" / MINECRAFT / f"{MINECRAFT}.json").read_text(encoding="utf-8"))
        jars: dict[str, Path] = {}
        for library in forge["libraries"] + vanilla["libraries"]:
            name = library["name"]
            key = ":".join(name.split(":")[:2])
            allowed = "rules" not in library or any(rule["action"] == "allow" and "os" not in rule for rule in library["rules"])
            if key not in jars and allowed and "natives" not in library and not name.startswith("net.minecraftforge:forge:"):
                jars[key] = self.library(name)
        return ";".join(str(jar) for jar in [*jars.values(), forge_jar, self.minecraft / "versions" / MINECRAFT / f"{MINECRAFT}.jar"])

    def assets(self) -> tuple[Path, str]:
        """The assets folder and the asset index: the ones of the legacy build, or the ones of the launcher."""
        loom = self.gradle_caches / "essential-loom" / "assets"
        if (loom / "indexes" / f"{MINECRAFT}-1.8.json").is_file():
            return loom, f"{MINECRAFT}-1.8"
        return self.minecraft / "assets", "1.8"

    def natives(self) -> Path:
        return self.gradle_caches / "essential-loom" / MINECRAFT / "natives"


def find_java_8() -> Path:
    roots = [Path.home() / ".jdks", Path(r"C:\Program Files\Eclipse Adoptium"), Path(r"C:\Program Files\Java")]
    for root in roots:
        for folder in sorted(root.glob("*")) if root.is_dir() else []:
            java = folder / "bin" / "java.exe"
            if java.is_file() and ("1.8" in folder.name or folder.name.lower().startswith(JAVA_8_FOLDER)):
                return java
    raise ToolError("no Java 8 found; use --java <path to java.exe>")


def variant_options(variant: str) -> tuple[dict[str, str], dict[str, str]]:
    """The lines of options.txt and of optionsof.txt of a variant."""
    if not variant.startswith("quake"):
        return dict(GAME_OPTIONS), {}
    game = GAME_OPTIONS | {"fancyGraphics": "true" if variant == "quake-fancy" else "false", "ao": "2" if variant == "quake-ao" else "0"}
    return game, dict(QUAKE_OPTIFINE_OPTIONS)


def prepare_game_dir(game: Path, variant: str, mods: Path | None, option_lines: list[str]) -> None:
    mod_dir = game / "mods"
    mod_dir.mkdir(parents=True, exist_ok=True)
    for jar in mod_dir.glob("*.jar"):
        jar.unlink()
    target = project.find_target(f"forge-{MINECRAFT}")
    release = target.jar(project.mod_version())
    if not release.is_file():
        raise ToolError(f"no {release.name}; run: python -m tools build {target.name}")
    shutil.copy2(release, mod_dir)
    if variant != "none":
        optifine = next(iter(sorted(WORK.glob(f"*OptiFine*{MINECRAFT}*.jar"))), None)
        if optifine is None:
            raise ToolError(f"put an OptiFine {MINECRAFT} jar in {WORK}")
        shutil.copy2(optifine, mod_dir)
    for jar in mods.glob("*.jar") if mods else []:
        shutil.copy2(jar, mod_dir)
    game_options, optifine_options = variant_options(variant)
    options_file = game / "options.txt"
    rundir.write_options(options_file, rundir.read_options(options_file) | game_options | rundir.parse_option_lines(option_lines))
    (game / "optionsof.txt").unlink(missing_ok=True)
    if optifine_options:
        rundir.write_options(game / "optionsof.txt", optifine_options)
    rundir.reset_mod_state(game)


def production_client(java: Path, installation: Installation, forge_jar: Path, game: Path, harness: Path, dump_classes: bool) -> Launch:
    assets, asset_index = installation.assets()
    width, height = protocol.WINDOW_SIZE
    arguments = [
        "-Xmx2G",
        f'-Djava.library.path="{installation.natives()}"',
        f'{protocol.JVM_PROPERTY}"{harness}"',
        *(DUMP_CLASSES if dump_classes else ()),
        "net.minecraft.launchwrapper.Launch",
        *("--username", "Dev", "--version", MINECRAFT, "--gameDir", f'"{game}"', "--assetsDir", f'"{assets}"', "--assetIndex", asset_index),
        *("--uuid", "0", "--accessToken", "0", "--userProperties", "{}", "--userType", "legacy"),
        *("--tweakClass", "net.minecraftforge.fml.common.launcher.FMLTweaker", "--width", str(width), "--height", str(height)),
    ]
    # The class path is too long for cmd.exe, so it goes in the CLASSPATH variable of the game process.
    environment = (("CLASSPATH", installation.class_path(forge_jar)),)
    return Launch(f'"{java}" {" ".join(arguments)}', game, harness / "game.log", environment)


def run_variant(variant: str, options: argparse.Namespace, java: Path, installation: Installation, system: Windows) -> None:
    def say(message: str) -> None:
        print(f"[{variant}] {message}", flush=True)

    target = project.find_target(f"forge-{MINECRAFT}")
    commands = script.compose(script.read_suites([script.scene_of(target), options.suite]), target)
    harness = session.open_harness(f"optifine-{MINECRAFT}-{variant}", commands, system)
    game = WORK / ("game-mods" if options.mods else "game")
    prepare_game_dir(game, variant, options.mods, options.option)
    launch = production_client(java, installation, options.forge or installation.forge_jar(), game, harness, options.dump_classes)
    say("launching hidden")
    session.play(launch, harness, options.timeout, system, say)

    out = project.ROOT / options.out / variant
    images = session.collect(harness, out)
    # Essential saves screenshots later on its own thread, so the harness can move an empty file; the late copy is the full one.
    for late in (harness / "screenshots").glob("*.png"):
        if late.stat().st_size > 0:
            shutil.copy2(late, out)
    for log in (launch.log, harness / protocol.LOG):
        if log.is_file():
            shutil.copy2(log, out)
    say(f"{len(images)} screenshots -> {out}")
    for error in protocol.errors(session.read_text(harness / protocol.LOG).splitlines()):
        say(error)


def main(arguments: list[str]) -> int:
    parser = argparse.ArgumentParser(prog="python -m tools optifine", description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--variants", nargs="+", choices=VARIANTS, default=VARIANTS, metavar="VARIANT")
    parser.add_argument("--suite", default="shots", help="the suite in tools/harness/suites that runs after the scene (default: shots)")
    parser.add_argument("--out", type=Path, default=Path("build/compat-optifine"), help="the output folder, from the repository root")
    parser.add_argument("--java", type=Path, help="the java.exe of a Java 8 (default: one in the usual folders)")
    parser.add_argument("--mods", type=Path, help='a folder of jars to add, for example the mods of a player; with its own OptiFine jar, use "none"')
    parser.add_argument("--forge", type=Path, help="a Forge 1.8.9 universal jar in place of the installed one (some mods need 11.15.1.2318)")
    parser.add_argument("--dump-classes", action="store_true", help="the transformed classes go to CLASSLOADER_TEMP of the game folder")
    parser.add_argument("--timeout", type=int, default=600, metavar="SECONDS")
    parser.add_argument("--option", action="append", default=[], metavar="KEY:VALUE", help="one more line of options.txt, for example useVbo:true")
    options = parser.parse_args(arguments)
    system = Windows()
    java = options.java or find_java_8()
    installation = Installation.of_this_user()
    for variant in options.variants:
        run_variant(variant, options, java, installation, system)
    return 0
