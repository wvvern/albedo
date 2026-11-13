"""Prints the shape of each game member that a mixin or an adapter names, on each modern node.

javap reads the shapes from the Mojang-named jars that Loom keeps after a build of the node. Nodes that follow each other with the same
shape are one line. A line of the list file (tools/boundaries.txt) is "<class>[|<class>...][#<member>[><text>]]":

    <class>                   whether the first class that exists is there
    <class>#<member>          the declarations of the member ("<init>" is the constructor)
    <class>#<member>><text>   how many instructions in the member name <text>, for example a call or a field read
"""

import argparse
import re
import subprocess
from dataclasses import dataclass, field
from pathlib import Path

from tools import project

LIST = Path(__file__).with_name("boundaries.txt")
LOOM_MINECRAFT = Path.home() / ".gradle" / "caches" / "fabric-loom" / "minecraftMaven" / "net" / "minecraft"
NO_JAR = "no jar in the Loom cache"

CLASS_HEADER = re.compile(r"^[a-z].*\b(?:class|interface|enum) (\S+).*\{$")
MEMBER = re.compile(r"^  \S.*;$")
REFERENCE = re.compile(r"// (?:Method|InterfaceMethod|Field) (.*)")
SHORT_NAMES = (
    (re.compile(r"net\.minecraft\.([a-z0-9_]+\.)*"), ""),
    (re.compile(r"java\.(lang|util|io|nio)\.([a-z]+\.)*"), ""),
    (re.compile(r"com\.mojang\.blaze3d\.([a-z0-9_]+\.)*"), "b3d."),
    (re.compile(r"com\.mojang\.renderpearl\.api\.([a-z0-9_]+\.)*"), "rp."),
    (re.compile(r"com\.mojang\.math\."), "mojmath."),
    (re.compile(r"org\.joml\."), "joml."),
)


@dataclass(frozen=True)
class Query:
    """One line of the list file."""

    classes: tuple[str, ...]
    member: str
    text: str


@dataclass
class Disassembly:
    """What javap printed for the classes of a jar."""

    classes: set[str] = field(default_factory=set)
    declarations: dict[tuple[str, str], list[str]] = field(default_factory=dict)
    references: dict[tuple[str, str], list[str]] = field(default_factory=dict)


def parse_query(row: str) -> Query | None:
    if not row or row.startswith("#"):
        return None
    owner, _, member = row.partition("#")
    # The ">" of a constructor name does not start the text.
    cut = member.find(">", len("<init>") if member.startswith("<init>") else 0)
    text = ""
    if cut >= 0:
        member, text = member[:cut], member[cut + 1 :]
    return Query(tuple(owner.split("|")), member, text)


def short(declaration: str) -> str:
    for pattern, replacement in SHORT_NAMES:
        declaration = pattern.sub(replacement, declaration)
    return declaration


def parse_javap(output: str) -> Disassembly:
    """Reads the class headers, the member declarations and the method and field references of "javap -p -c"."""
    found = Disassembly()
    owner = member = ""
    for line in output.splitlines():
        header = CLASS_HEADER.match(line.split("<")[0] + "{") if line.endswith("{") else None
        if header:
            owner, member = header.group(1), ""
            found.classes.add(owner)
        elif MEMBER.match(line):
            declaration = line[2:-1].split(" throws ")[0]
            member = declaration.split("(")[0].split(" ")[-1]
            if member == owner:
                member = "<init>"
            found.declarations.setdefault((owner, member), []).append(short(declaration))
        elif reference := REFERENCE.search(line):
            found.references.setdefault((owner, member), []).append(reference.group(1).replace("/", "."))
    return found


def shape(query: Query, disassembly: Disassembly) -> str:
    owner = next((name for name in query.classes if name in disassembly.classes), None)
    if owner is None:
        return "no class"
    # A line with more than one class tells which of them the node has.
    where = re.sub(r"^(net\.minecraft|com\.mojang)\.", "", owner) + ": " if len(query.classes) > 1 else ""
    key = (owner, query.member)
    if not query.member:
        return where + "class"
    if key not in disassembly.declarations:
        return where + "no member"
    if not query.text:
        return where + " / ".join(disassembly.declarations[key])
    return where + str(sum(query.text in reference for reference in disassembly.references.get(key, [])))


def fold(versions: list[str], shapes: list[str]) -> list[str]:
    """One line for each run of versions with the same shape."""
    lines = []
    first = 0
    for index in range(len(versions)):
        if index + 1 < len(versions) and shapes[index + 1] == shapes[index]:
            continue
        span = versions[index] if first == index else f"{versions[first]} to {versions[index]}"
        lines.append(f"    {span}: {shapes[index]}")
        first = index + 1
    return lines


def newest_jar(cache: Path, version: str) -> Path | None:
    """The jar of a version: no remap from 26.1, else the jar with the layered Mojang mappings that Loom wrote last."""
    jars = [cache / "minecraft-merged-deobf" / version / f"minecraft-merged-deobf-{version}.jar"]
    jars += cache.glob(f"minecraft-merged/{version}-loom.mappings.*/*.jar")
    return max((jar for jar in jars if jar.is_file()), key=lambda jar: jar.stat().st_mtime, default=None)


def disassemble(jar: Path, classes: list[str]) -> Disassembly:
    # javap prints an error for a class that the jar does not have, and the classes that it has.
    result = subprocess.run(["javap", "-p", "-c", "-cp", str(jar), *classes], capture_output=True, text=True, encoding="utf-8", check=False)
    return parse_javap(result.stdout)


def report(rows: list[str], versions: list[str], cache: Path) -> list[str]:
    queries = [parse_query(row) for row in rows]
    classes = sorted({name for query in queries if query for name in query.classes})
    shapes_of_version = []
    for version in versions:
        jar = newest_jar(cache, version)
        disassembly = disassemble(jar, classes) if jar else None
        shapes_of_version.append([shape(query, disassembly) if disassembly else NO_JAR for query in queries if query])
    lines = []
    shapes_of_query = iter(zip(*shapes_of_version, strict=True))
    for row, query in zip(rows, queries, strict=True):
        lines.append(row)
        if query:
            lines += fold(versions, list(next(shapes_of_query)))
    return lines


def main(arguments: list[str]) -> int:
    parser = argparse.ArgumentParser(prog="python -m tools boundaries", description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("list", nargs="?", type=Path, default=LIST, help="the list file (default: tools/boundaries.txt)")
    parser.add_argument(
        "--versions", nargs="+", metavar="VERSION", help="Minecraft versions, for example a minor version (default: each modern node)"
    )
    parser.add_argument("--cache", type=Path, default=LOOM_MINECRAFT, help="the net/minecraft folder of the Loom cache")
    options = parser.parse_args(arguments)
    versions = options.versions or [target.minecraft for target in project.targets() if target.loader == "fabric"]
    print("\n".join(report(options.list.read_text(encoding="utf-8").splitlines(), versions, options.cache)))
    return 0
