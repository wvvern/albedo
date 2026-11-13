"""The targets of the two Gradle builds. No other module reads the layout of modern/ and legacy/ or the names of their jars and tasks."""

import re
from dataclasses import dataclass
from functools import cache
from pathlib import Path

from tools.report import ToolError

ROOT = Path(__file__).resolve().parent.parent
MODERN = ROOT / "modern"
LEGACY = ROOT / "legacy"

# The list in modern/settings.gradle of each loader, in the sequence of the loaders in a jar listing.
NODE_LISTS = {"fabric": "nodes", "forge": "forgeNodes", "neoforge": "neoforgeNodes"}
JAR_NAME = re.compile(r"albedo-(?P<loader>[a-z]+)-(?P<version>.+)\+(?P<minecraft>[^+]+)\.jar")


def parse_properties(text: str) -> dict[str, str]:
    properties = {}
    for line in text.splitlines():
        key, separator, value = line.partition("=")
        if separator and not line.lstrip().startswith("#"):
            properties[key.strip()] = value.strip()
    return properties


def read_properties(file: Path) -> dict[str, str]:
    return parse_properties(file.read_text(encoding="utf-8"))


def version_key(version: str) -> tuple[int, ...]:
    return tuple(int(part) for part in version.split("."))


@dataclass(frozen=True)
class Target:
    """One jar of a release: a loader on a node, where a node is a folder in versions/ of its build."""

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

    def jar_name(self, mod_version: str) -> str:
        return f"albedo-{self.loader}-{mod_version}+{self.minecraft}.jar"

    def jar(self, mod_version: str) -> Path:
        return self.project_dir / "build" / "libs" / self.jar_name(mod_version)

    def at_least(self, minecraft: str) -> bool:
        return version_key(self.minecraft) >= version_key(minecraft)

    def properties(self) -> dict[str, str]:
        """The gradle.properties of the node, which only a modern node has."""
        return node_properties(self.node)


def modern_node_lists(settings: str) -> dict[str, list[str]]:
    """The nodes of each loader, from the text of modern/settings.gradle."""
    lists = {}
    for loader, name in NODE_LISTS.items():
        match = re.search(rf"^def {name} = \[(.*)\]$", settings, re.MULTILINE)
        if not match:
            raise ToolError(f"modern/settings.gradle has no list {name} on one line")
        lists[loader] = re.findall(r"'([^']+)'", match.group(1))
    return lists


def legacy_versions(settings: str) -> list[str]:
    """The Minecraft versions of the legacy build, from the text of legacy/settings.gradle.kts."""
    match = re.search(r"listOf\(([^)]*)\)", settings)
    if not match:
        raise ToolError("legacy/settings.gradle.kts has no listOf(...) of its versions")
    return re.findall(r'"([^"]+)"', match.group(1))


@cache
def node_properties(node: str) -> dict[str, str]:
    file = MODERN / "versions" / node / "gradle.properties"
    if not file.is_file():
        raise ToolError(f"the node {node} has no {file.relative_to(ROOT).as_posix()}")
    return read_properties(file)


@cache
def targets() -> tuple[Target, ...]:
    """Each target, node by node in the sequence of the settings files, the modern build first."""
    lists = modern_node_lists((MODERN / "settings.gradle").read_text(encoding="utf-8"))
    found = []
    for node in lists["fabric"]:
        properties = node_properties(node)
        for loader in NODE_LISTS:
            if node in lists[loader]:
                found.append(Target(loader, node, properties["minecraft_version"], int(properties["java_version"]), legacy=False))
    for version in legacy_versions((LEGACY / "settings.gradle.kts").read_text(encoding="utf-8")):
        found.append(Target("forge", version, version, 8, legacy=True))
    return tuple(found)


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
    raise ToolError(f"{file_name} is not the jar of a target (albedo-<loader>-<mod version>+<minecraft version>.jar)")


@cache
def build_properties() -> dict[str, str]:
    """The properties that all modern nodes share."""
    return read_properties(MODERN / "gradle.properties")


def mod_version() -> str:
    return build_properties()["mod_version"]


def vcs_version(settings: str) -> str:
    """The node whose code is active in the source files that git has, from the text of modern/settings.gradle."""
    match = re.search(r"vcsVersion = '([^']+)'", settings)
    if not match:
        raise ToolError("modern/settings.gradle has no vcsVersion")
    return match.group(1)
