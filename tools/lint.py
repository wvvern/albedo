"""Repository checks that Checkstyle cannot do (docs/CODING_STANDARDS.md). CI runs them.

Each check is a function from the tracked files to its problems, in CHECKS.
"""

import argparse
import re
import subprocess
from collections.abc import Callable, Iterable, Iterator
from dataclasses import dataclass
from pathlib import Path

from tools import project
from tools.report import Problem, report

INJECTORS = (
    "Inject",
    "Redirect",
    "ModifyArg",
    "ModifyArgs",
    "ModifyVariable",
    "ModifyConstant",
    "ModifyExpressionValue",
    "ModifyReturnValue",
    "WrapOperation",
    "WrapWithCondition",
)
STRING_LITERAL = re.compile(r'"[^"]*"')
# The start of a line in an inactive Stonecutter branch is the start of a block comment.
LINE_START = re.compile(r"^[ \t]*(/[*^])?[ \t]*")
# An annotation can have its package, where the import would be unused in the other Stonecutter branch.
INJECTOR = re.compile(rf"@(?:[\w.]+\.)?({'|'.join(INJECTORS)})\(")
STONECUTTER_MARKER = re.compile(r"([*^]/)?//\?")
HANDLER_NAME = re.compile(r"albedo\$[A-Za-z0-9_]*[ \t]*\(")
CONDITION = re.compile(r"//\?.*(if|elif) ")
VERSION = re.compile(r"[0-9]+\.[0-9]+(\.[0-9]+)?")
BOUNDARY_TABLE = "docs/ARCHITECTURE.md"


@dataclass(frozen=True)
class Tree:
    """The tracked files of a repository."""

    root: Path
    files: tuple[str, ...]

    @classmethod
    def of_git(cls, root: Path) -> "Tree":
        listing = subprocess.run(["git", "ls-files", "-z"], cwd=root, check=True, capture_output=True).stdout
        return cls(root, tuple(name for name in listing.decode("utf-8").split("\0") if (root / name).is_file()))

    def text(self, file: str) -> str:
        return (self.root / file).read_text(encoding="utf-8")

    def lines(self, file: str) -> list[str]:
        return self.text(file).splitlines()

    def matching(self, pattern: str) -> Iterator[str]:
        return (file for file in self.files if Path(file).match(pattern))


def text_hygiene(tree: Tree) -> Iterator[Problem]:
    """Text files are ASCII, with spaces, no space at the end of a line and a newline at the end of the file."""
    for file in tree.files:
        data = (tree.root / file).read_bytes()
        # The test of git for a binary file.
        if not data or b"\0" in data[:8000]:
            continue
        crlf = file.endswith(".bat")
        for number, line in enumerate(data.split(b"\n"), 1):
            if not line.isascii():
                yield Problem(file, "use only ASCII characters", number)
            if b"\t" in line:
                yield Problem(file, "use spaces, not tabs", number)
            if line.endswith((b" ", b"\t")) or (line.endswith(b"\r") and not crlf):
                yield Problem(file, "remove the spaces at the end of the line", number)
        if not data.endswith(b"\n"):
            yield Problem(file, "add a newline at the end of the file")


def unprefixed_handlers(lines: Iterable[str]) -> Iterator[int]:
    """The line numbers of the mixin handlers whose names do not start with albedo$, also in inactive Stonecutter branches.

    After an injector annotation, the rest of it, other annotations, blank lines and Stonecutter markers are skipped; the next line is the
    handler header. So the handler name must be on the header line, and the annotation must not share a line with the header.
    """
    pending = False
    depth = 0
    for number, line in enumerate(lines, 1):
        code = STRING_LITERAL.sub("", line)
        text = LINE_START.sub("", code, count=1)
        opened = code.count("(") - code.count(")")
        if depth > 0:
            depth += opened
        elif INJECTOR.match(text):
            pending = True
            depth = opened
        elif not pending:
            continue
        elif text.startswith("@"):
            depth = opened
        elif text and not STONECUTTER_MARKER.match(text) and text != "*/":
            if not HANDLER_NAME.search(code):
                yield number
            pending = False


def mixin_handler_names(tree: Tree) -> Iterator[Problem]:
    for file in tree.files:
        if file.endswith("Mixin.java"):
            for number in unprefixed_handlers(tree.lines(file)):
                yield Problem(file, "a handler name must start with albedo$", number)


def stonecutter_active_version(tree: Tree) -> Iterator[Problem]:
    """A different active version changes the comment state of each version branch in src/."""
    file = "modern/stonecutter.gradle"
    wanted = f"stonecutter.active '{project.vcs_version(tree.text('modern/settings.gradle'))}'"
    if wanted not in tree.lines(file):
        yield Problem(file, f"the active version must be the vcsVersion of modern/settings.gradle: {wanted}")


def condition_versions(tree: Tree) -> Iterator[Problem]:
    """Each version in a Stonecutter condition is a boundary in the table of docs/ARCHITECTURE.md, so that no condition has a version that
    nobody examined."""
    table = tree.text(BOUNDARY_TABLE)
    reported = set()
    for file in tree.files:
        if not (file.startswith("modern/") and file.endswith(".java")):
            continue
        for number, line in enumerate(tree.lines(file), 1):
            if not CONDITION.search(line):
                continue
            for match in VERSION.finditer(line):
                version = match.group()
                if f"| `{version}` |" not in table and version not in reported:
                    reported.add(version)
                    yield Problem(file, f"the version {version} of a Stonecutter condition is not in the boundary table of {BOUNDARY_TABLE}", number)


def node_files(tree: Tree) -> Iterator[Problem]:
    """The node folders agree with the lists of modern/settings.gradle."""
    lists = project.modern_node_lists(tree.text("modern/settings.gradle"))
    nodes = lists["fabric"]
    folders = {Path(file).parent.name for file in tree.matching("modern/versions/*/gradle.properties")}
    for node in sorted(folders.symmetric_difference(nodes), key=project.version_key):
        yield Problem("modern/settings.gradle", f"the node {node} must be in the list nodes and have modern/versions/{node}/gradle.properties")
    for loader in ("forge", "neoforge"):
        for node in lists[loader]:
            file = f"modern/{loader}/versions/{node}/gradle.properties"
            if node not in nodes:
                yield Problem("modern/settings.gradle", f"the {loader} node {node} is not in the list nodes")
            elif file not in tree.files or f"loom.platform={loader}" not in tree.lines(file):
                yield Problem(file, f"a {loader} node needs this file with loom.platform={loader}")


def modrinth_versions(tree: Tree) -> Iterator[Problem]:
    """The game versions that a node lists on Modrinth are in the range of its Fabric jar, which is the widest range of the node."""
    for file in tree.matching("modern/versions/*/gradle.properties"):
        properties = project.parse_properties(tree.text(file))
        lower, upper = (project.version_key(bound.lstrip("<>=")) for bound in properties["minecraft_range_fabric"].split())
        for key in ("modrinth_versions", "modrinth_versions_fabric"):
            for version in properties.get(key, "").split():
                if not lower <= project.version_key(version) < upper:
                    yield Problem(file, f"the version {version} of {key} is not in minecraft_range_fabric")


CHECKS: tuple[Callable[[Tree], Iterable[Problem]], ...] = (
    text_hygiene,
    mixin_handler_names,
    stonecutter_active_version,
    condition_versions,
    node_files,
    modrinth_versions,
)


def main(arguments: list[str]) -> int:
    argparse.ArgumentParser(prog="python -m tools lint", description=__doc__.splitlines()[0]).parse_args(arguments)
    tree = Tree.of_git(project.ROOT)
    return report(problem for check in CHECKS for problem in check(tree))
