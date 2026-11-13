"""The nodes and targets of the two Gradle builds. No other module reads the layout of modern/ and legacy/ or the names of their jars and tasks."""

import re
from dataclasses import dataclass
from functools import cache
from pathlib import Path

from tools.report import ToolError

ROOT = Path(__file__).resolve().parent.parent
MODERN = ROOT / "modern"
LEGACY = ROOT / "legacy"

# The files of the layout, from the repository root.
SETTINGS = "modern/settings.gradle"
LEGACY_SETTINGS = "legacy/settings.gradle.kts"
BUILD_PROPERTIES = "modern/gradle.properties"
STONECUTTER = "modern/stonecutter.gradle"

# The list in modern/settings.gradle of each loader, in the sequence of the loaders in a jar listing.
NODE_LISTS = {"fabric": "nodes", "forge": "forgeNodes", "neoforge": "neoforgeNodes"}
ARCHIVE = "albedo"
JAR_GLOB = f"{ARCHIVE}-*.jar"
JAR_NAME = re.compile(rf"{ARCHIVE}-(?P<loader>[a-z]+)-(?P<version>.+)\+(?P<minecraft>[^+]+)\.jar")
# The Java that runs Gradle in each build, which is not the Java of a node.
MODERN_GRADLE_JAVA = 25
LEGACY_GRADLE_JAVA = 21


def node_properties_file(node: str) -> str:
    return f"modern/versions/{node}/gradle.properties"


def loader_properties_file(loader: str, node: str) -> str:
    return f"modern/{loader}/versions/{node}/gradle.properties"


def parse_properties(text: str) -> dict[str, str]:
    properties = {}
    for line in text.splitlines():
        key, separator, value = line.partition("=")
        if separator and not line.lstrip().startswith("#"):
            properties[key.strip()] = value.strip()
    return properties


def read_properties(file: str) -> dict[str, str]:
    return parse_properties((ROOT / file).read_text(encoding="utf-8"))


def version_key(version: str) -> tuple[int, ...]:
    return tuple(int(part) for part in version.split("."))


@dataclass(frozen=True)
class Target:
    """One jar of a release: a loader on a node."""

    loader: str
    node: str
    minecraft: str
    java: int
    legacy: bool

    @property
    def name(self) -> str:
        return f"{self.loader}-{self.node}"

    @property
    def build_dir(self) -> Path:
        return LEGACY if self.legacy else MODERN

    @property
    def project_dir(self) -> Path:
        return LEGACY / "versions" / self.node if self.legacy else MODERN / self.loader / "versions" / self.node

    @property
    def run_dir(self) -> Path:
        return self.project_dir / "run"

    @property
    def release_task(self) -> str:
        return f":{self.node}:build" if self.legacy else f":{self.loader}:{self.node}:releaseJar"

    @property
    def run_task(self) -> str:
        return f":{self.node}:runClientDev" if self.legacy else f":{self.loader}:{self.node}:runClient"

    @property
    def mod_version_property(self) -> str:
        """The Gradle property that gives the build a mod version other than the one of modern/gradle.properties."""
        return "modVersion" if self.legacy else "mod_version"

    def jar_name(self, mod_version: str) -> str:
        return f"{ARCHIVE}-{self.loader}-{mod_version}+{self.minecraft}.jar"

    def jar(self, mod_version: str) -> Path:
        return self.project_dir / "build" / "libs" / self.jar_name(mod_version)

    def at_least(self, minecraft: str) -> bool:
        return version_key(self.minecraft) >= version_key(minecraft)

    def properties(self) -> dict[str, str]:
        """The gradle.properties of the node, which is empty for a legacy node."""
        return {} if self.legacy else node_properties(self.node)


@dataclass(frozen=True)
class Node:
    """A folder in versions/ of one build, which one Gradle run builds, with a target for each of its loaders."""

    name: str
    legacy: bool
    targets: tuple[Target, ...]

    @property
    def minecraft(self) -> str:
        return self.targets[0].minecraft

    @property
    def java(self) -> int:
        return self.targets[0].java

    @property
    def gradle_java(self) -> int:
        return LEGACY_GRADLE_JAVA if self.legacy else MODERN_GRADLE_JAVA

    @property
    def build_dir(self) -> Path:
        return self.targets[0].build_dir

    def properties(self) -> dict[str, str]:
        return self.targets[0].properties()


def modern_node_lists(settings: str) -> dict[str, list[str]]:
    """The nodes of each loader, from the text of modern/settings.gradle."""
    lists = {}
    for loader, name in NODE_LISTS.items():
        match = re.search(rf"^def {name} = \[(.*)\]$", settings, re.MULTILINE)
        if not match:
            raise ToolError(f"{SETTINGS} has no list {name} on one line")
        lists[loader] = re.findall(r"'([^']+)'", match.group(1))
    return lists


def legacy_versions(settings: str) -> list[str]:
    """The Minecraft versions of the legacy build, from the text of legacy/settings.gradle.kts."""
    match = re.search(r"listOf\(([^)]*)\)", settings)
    if not match:
        raise ToolError(f"{LEGACY_SETTINGS} has no listOf(...) of its versions")
    return re.findall(r'"([^"]+)"', match.group(1))


@cache
def node_properties(node: str) -> dict[str, str]:
    file = node_properties_file(node)
    if not (ROOT / file).is_file():
        raise ToolError(f"the node {node} has no {file}")
    return read_properties(file)


@cache
def nodes() -> tuple[Node, ...]:
    """Each node in the sequence of the settings files, the modern build first."""
    lists = modern_node_lists((ROOT / SETTINGS).read_text(encoding="utf-8"))
    found = []
    for name in lists["fabric"]:
        properties = node_properties(name)
        minecraft, java = properties["minecraft_version"], int(properties["java_version"])
        loaders = [loader for loader in NODE_LISTS if name in lists[loader]]
        found.append(Node(name, False, tuple(Target(loader, name, minecraft, java, legacy=False) for loader in loaders)))
    for version in legacy_versions((ROOT / LEGACY_SETTINGS).read_text(encoding="utf-8")):
        found.append(Node(version, True, (Target("forge", version, version, 8, legacy=True),)))
    return tuple(found)


def targets() -> tuple[Target, ...]:
    return tuple(target for node in nodes() for target in node.targets)


def find_target(name: str) -> Target:
    for target in targets():
        if target.name == name:
            return target
    raise ToolError(f"no target {name}; the targets are: {' '.join(target.name for target in targets())}")


def target_of_jar(file_name: str) -> Target:
    """The target that builds a release jar with this file name, for each mod version."""
    match = JAR_NAME.fullmatch(file_name)
    if match:
        for target in targets():
            if (target.loader, target.minecraft) == (match["loader"], match["minecraft"]):
                return target
    raise ToolError(f"{file_name} is not the jar of a target ({ARCHIVE}-<loader>-<mod version>+<minecraft version>.jar)")


def jar_key(file_name: str) -> str:
    """The name of a jar without its mod version, so that the jars of two releases pair up."""
    match = JAR_NAME.fullmatch(file_name)
    return f"{match['loader']}+{match['minecraft']}" if match else file_name


@cache
def build_properties() -> dict[str, str]:
    """The properties that all modern nodes share."""
    return read_properties(BUILD_PROPERTIES)


def mod_version() -> str:
    return build_properties()["mod_version"]


def vcs_version(settings: str) -> str:
    """The node whose code is active in the source files that git has, from the text of modern/settings.gradle."""
    match = re.search(r"vcsVersion = '([^']+)'", settings)
    if not match:
        raise ToolError(f"{SETTINGS} has no vcsVersion")
    return match.group(1)
