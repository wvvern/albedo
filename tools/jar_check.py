"""Examines release jars with no game: the mixin lists, the Java level and the loader metadata of the target of each jar.

Each check is a function from a jar and its target to messages, in MODERN_CHECKS and LEGACY_CHECKS.
"""

import argparse
import json
import tomllib
import zipfile
from collections.abc import Callable, Iterable, Iterator
from dataclasses import dataclass
from functools import cached_property
from pathlib import Path
from typing import IO, Any

from tools import project
from tools.project import Target
from tools.report import Problem, ToolError

# Mixin 0.8 knows no compatibility level above JAVA_21. The build side is compatibilityLevel in modern/gradle/shared.gradle.
NEWEST_MIXIN_LEVEL = 21
# The major version of a class file is its Java version plus this.
CLASS_VERSION_OFFSET = 44


class Jar:
    def __init__(self, file: Path | IO[bytes]):
        self.zip = zipfile.ZipFile(file)
        self.names = frozenset(self.zip.namelist())

    def __enter__(self) -> "Jar":
        return self

    def __exit__(self, *_: object) -> None:
        self.zip.close()

    def text(self, name: str) -> str:
        return self.zip.read(name).decode("utf-8")

    def json(self, name: str) -> Any:
        return json.loads(self.text(name))

    def children(self, folder: str) -> set[str]:
        """The names of the files and folders in a folder of the jar."""
        return {name[len(folder) :].split("/")[0] for name in self.names if name.startswith(folder) and name != folder}

    @cached_property
    def manifest(self) -> dict[str, str]:
        # A line that starts with a space continues the value of the line before it.
        lines = self.text("META-INF/MANIFEST.MF").replace("\r\n", "\n").replace("\n ", "").splitlines()
        return {key: value.strip() for key, _, value in (line.partition(":") for line in lines) if value}


@dataclass(frozen=True)
class Subject:
    """A jar with what its target must give it."""

    jar: Jar
    target: Target
    node_properties: dict[str, str]
    build: dict[str, str]

    @property
    def mixin_level(self) -> str:
        return f"JAVA_{min(self.target.java, NEWEST_MIXIN_LEVEL)}"


def mixin_config(subject: Subject, config: str, package: str) -> Iterator[str]:
    """The config lists each mixin class of its package and no other class, at the Java level of the target."""
    jar = subject.jar
    if config not in jar.names:
        yield f"the jar has no {config}"
        return
    content = jar.json(config)
    classes = {name.removesuffix(".class") for name in jar.children(package) if "$" not in name}
    if sorted(content.get("client", [])) != sorted(classes):
        yield f"the client list of {config} is not the list of the mixin classes in the jar"
    if content.get("compatibilityLevel") != subject.mixin_level:
        yield f"the compatibilityLevel of {config} is not {subject.mixin_level}"


def common_mixins(subject: Subject) -> Iterator[str]:
    yield from mixin_config(subject, "albedo.mixins.json", "dev/albedo/modern/mixin/")


def forge_mixins(subject: Subject) -> Iterator[str]:
    """Only a Forge jar of a version with the light pipeline of Forge (below 1.19, as in modern/forge/build.gradle) has a config for its
    own mixins, which the manifest names."""
    config = "albedo.forge.mixins.json"
    if subject.target.loader == "forge" and not subject.target.at_least("1.19"):
        yield from mixin_config(subject, config, "dev/albedo/forge/mixin/")
        if config in subject.jar.names and config not in subject.jar.manifest.get("MixinConfigs", ""):
            yield f"the manifest does not name {config}"
    elif config in subject.jar.names:
        yield f"the jar has {config}, which only a Forge jar below 1.19 needs"


def no_dev_mixin(subject: Subject) -> Iterator[str]:
    """No jar has the mixin of the dev run of Forge 1.16.5."""
    if "albedo.dev.mixins.json" in subject.jar.names or subject.jar.children("dev/albedo/forge/dev/"):
        yield "the jar has the mixin of the dev run"


def class_versions(subject: Subject) -> Iterator[str]:
    wanted = subject.target.java + CLASS_VERSION_OFFSET
    for name in sorted(subject.jar.names):
        if name.startswith("dev/") and name.endswith(".class"):
            with subject.jar.zip.open(name) as stream:
                major = int.from_bytes(stream.read(8)[6:8], "big")
            if major != wanted:
                yield f"{name} has the class version {major}, not {wanted}"
                return


def dependency_ranges(mods_toml: str) -> dict[str, str]:
    """The version range of each mod that a mods.toml depends on."""
    groups = tomllib.loads(mods_toml).get("dependencies", {}).values()
    return {dependency["modId"]: dependency["versionRange"] for group in groups for dependency in group}


def expect(file: str, what: str, actual: str | None, wanted: str) -> Iterator[str]:
    if actual != wanted:
        yield f"{file} has the {what} {actual}, not {wanted}"


def loader_metadata(subject: Subject) -> Iterator[str]:
    """The metadata of the loader asks for the version ranges of the node."""
    jar, loader, node = subject.jar, subject.target.loader, subject.node_properties
    if loader == "fabric":
        depends = jar.json("fabric.mod.json").get("depends", {})
        yield from expect("fabric.mod.json", "minecraft range", depends.get("minecraft"), node["minecraft_range_fabric"])
        yield from expect("fabric.mod.json", "fabricloader range", depends.get("fabricloader"), f">={subject.build['fabric_loader_min']}")
        return
    # NeoForge 1.20.4 still reads the file name of Forge.
    file = "META-INF/neoforge.mods.toml" if "META-INF/neoforge.mods.toml" in jar.names else "META-INF/mods.toml"
    ranges = dependency_ranges(jar.text(file))
    yield from expect(file, "minecraft range", ranges.get("minecraft"), node[f"minecraft_range_{loader}"])
    yield from expect(file, f"{loader} range", ranges.get(loader), node[f"{loader}_range"])


def legacy_metadata(subject: Subject) -> Iterator[str]:
    jar, minecraft = subject.jar, subject.target.minecraft
    mod = jar.json("mcmod.info")[0]
    yield from expect("mcmod.info", "mcversion", mod.get("mcversion"), minecraft)
    if not mod.get("version", "").endswith(f"+{minecraft}"):
        yield f"the version {mod.get('version')} of mcmod.info does not end with +{minecraft}"
    yield from expect("the manifest", "TweakClass", jar.manifest.get("TweakClass"), "org.spongepowered.asm.launch.MixinTweaker")
    yield from expect("the manifest", "MixinConfigs", jar.manifest.get("MixinConfigs"), "mixins.albedo.json")
    if "mixins.albedo.refmap.json" not in jar.names:
        yield "the jar has no mixins.albedo.refmap.json"


def legacy_mixins(subject: Subject) -> Iterator[str]:
    yield from mixin_config(subject, "mixins.albedo.json", "dev/albedo/legacy/mixin/")


Check = Callable[[Subject], Iterable[str]]
MODERN_CHECKS: tuple[Check, ...] = (common_mixins, forge_mixins, no_dev_mixin, class_versions, loader_metadata)
LEGACY_CHECKS: tuple[Check, ...] = (legacy_metadata, legacy_mixins, class_versions)


def examine(subject: Subject) -> list[str]:
    checks = LEGACY_CHECKS if subject.target.legacy else MODERN_CHECKS
    return [message for check in checks for message in check(subject)]


def examine_file(file: Path) -> list[str]:
    target = project.target_of_jar(file.name)
    try:
        jar = Jar(file)
    except (OSError, zipfile.BadZipFile):
        return ["cannot read the jar"]
    with jar:
        try:
            return examine(Subject(jar, target, target.properties(), project.build_properties()))
        except (KeyError, ValueError) as error:
            # zipfile gives a KeyError for a file that the jar does not have, and json and tomllib a ValueError for a file that they cannot read.
            return [f"a check cannot read the jar: {error}"]


def jar_files(arguments: list[Path]) -> list[Path]:
    """The jars that the arguments name, where a folder is all jars in it."""
    files = []
    for argument in arguments:
        found = sorted(argument.glob("*.jar")) if argument.is_dir() else [argument]
        if not found:
            raise ToolError(f"no jars in {argument}")
        files += found
    return files


def main(arguments: list[str]) -> int:
    parser = argparse.ArgumentParser(prog="python -m tools check-jar", description=__doc__.splitlines()[0])
    parser.add_argument("jars", nargs="+", type=Path, help="release jars, or folders with release jars")
    failed = False
    for file in jar_files(parser.parse_args(arguments).jars):
        messages = examine_file(file)
        failed = failed or bool(messages)
        for message in messages:
            print(Problem(file.as_posix(), message))
        if not messages:
            print(f"{file.as_posix()}: the static check passed")
    return 1 if failed else 0
