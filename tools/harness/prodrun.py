"""What the production run of the 1.8.9 release jar needs: the files of the launcher, the game folder and the launch of the client."""

import json
import os
import shutil
import zipfile
from dataclasses import dataclass
from pathlib import Path

from tools import gradle, project
from tools.harness import protocol, rundir
from tools.harness.session import Launch
from tools.report import ToolError

MINECRAFT = "1.8.9"
WORK = project.LEGACY / "build" / "optifine"
GAME_OPTIONS = rundir.QUIET_OPTIONS | {"guiScale": "0", "fancyGraphics": "true", "ao": "2"}
QUAKE_OPTIFINE_OPTIONS = {
    "ofFastRender": "true",
    "ofCustomColors": "true",
    "ofConnectedTextures": "2",
    "ofNaturalTextures": "true",
    "ofClearWater": "true",
    "ofBetterGrass": "2",
    "ofDynamicLights": "3",
}
DUMP_CLASSES = "-Dlegacy.debugClassLoading=true -Dlegacy.debugClassLoadingSave=true -Dmixin.debug.verbose=true"
JAVA_FOLDERS = (Path.home() / ".jdks", Path(r"C:\Program Files\Eclipse Adoptium"), Path(r"C:\Program Files\Java"))
JAVA_8_FOLDER = ("jdk8", "jdk-8", "jre8", "jre-8", "jdk1.8", "jdk-1.8", "jre1.8", "jre-1.8")


@dataclass(frozen=True)
class Installation:
    """The folders of the launcher and of Gradle that have the files of a production client."""

    minecraft: Path
    gradle_caches: Path

    @classmethod
    def of_this_user(cls) -> "Installation":
        return cls(Path(os.environ["APPDATA"]) / ".minecraft", gradle.CACHES)

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
        jars = (self.minecraft / "libraries" / "net" / "minecraftforge" / "forge").rglob(f"forge-{MINECRAFT}-*.jar")
        newest = max(jars, key=lambda jar: jar.name, default=None)
        if newest is None:
            raise ToolError(f"Forge {MINECRAFT} is not installed in {self.minecraft}")
        return newest

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
    for root in JAVA_FOLDERS:
        for folder in sorted(root.glob("*")):
            java = folder / "bin" / "java.exe"
            if java.is_file() and ("1.8" in folder.name or folder.name.lower().startswith(JAVA_8_FOLDER)):
                return java
    raise ToolError("no Java 8 found; use --java <path to java.exe>")


def variant_options(variant: str) -> tuple[dict[str, str], dict[str, str]]:
    """The lines of options.txt and of optionsof.txt of a variant."""
    if not variant.startswith("quake"):
        return GAME_OPTIONS, {}
    game = GAME_OPTIONS | {"fancyGraphics": "true" if variant == "quake-fancy" else "false", "ao": "2" if variant == "quake-ao" else "0"}
    return game, QUAKE_OPTIFINE_OPTIONS


def prepare_game_dir(game: Path, release: Path, variant: str, mods: Path | None, option_lines: tuple[str, ...]) -> None:
    mod_dir = game / "mods"
    mod_dir.mkdir(parents=True, exist_ok=True)
    for jar in mod_dir.glob("*.jar"):
        jar.unlink()
    shutil.copy2(release, mod_dir)
    if variant != "none":
        optifine = min(WORK.glob(f"*OptiFine*{MINECRAFT}*.jar"), default=None)
        if optifine is None:
            raise ToolError(f"put an OptiFine {MINECRAFT} jar in {WORK}")
        shutil.copy2(optifine, mod_dir)
    for jar in mods.glob("*.jar") if mods else []:
        shutil.copy2(jar, mod_dir)
    game_options, optifine_options = variant_options(variant)
    options_file = game / "options.txt"
    rundir.write_options(options_file, rundir.read_options(options_file) | game_options | rundir.parse_option_lines(list(option_lines)))
    (game / "optionsof.txt").unlink(missing_ok=True)
    if optifine_options:
        rundir.write_options(game / "optionsof.txt", optifine_options)
    rundir.reset_mod_state(game)


def production_client(java: Path, installation: Installation, forge_jar: Path, game: Path, harness: Path, dump_classes: bool) -> Launch:
    assets, asset_index = installation.assets()
    width, height = protocol.WINDOW_SIZE
    arguments = [
        f'"{java}" -Xmx2G -Djava.library.path="{installation.natives()}" {protocol.JVM_PROPERTY}"{harness}"',
        DUMP_CLASSES if dump_classes else "",
        f"net.minecraft.launchwrapper.Launch --username Dev --version {MINECRAFT}",
        f'--gameDir "{game}" --assetsDir "{assets}" --assetIndex {asset_index}',
        "--uuid 0 --accessToken 0 --userProperties {} --userType legacy",
        f"--tweakClass net.minecraftforge.fml.common.launcher.FMLTweaker --width {width} --height {height}",
    ]
    # The class path is too long for cmd.exe, so it goes in the CLASSPATH variable of the game process.
    environment = (("CLASSPATH", installation.class_path(forge_jar)),)
    return Launch(" ".join(filter(None, arguments)), game, harness / "game.log", environment)
