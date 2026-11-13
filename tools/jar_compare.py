"""Compares the built jars with the jars of a release, entry by entry, with no game.

Each jar finds the jar with the same name in the other folder, apart from the mod version in the name. tools/jar-changes.txt lists the
entries that can be different, each with its reason. The exit code is 1 when an entry that is not in that list is different, added or
removed, or when a jar has no partner.

    gh release download v0.5.1 --repo wvvern/albedo --dir build/v0.5.1
    python -m tools build
    python -m tools compare-jars build/v0.5.1 build/libs
"""

import argparse
import hashlib
import zipfile
from dataclasses import dataclass
from fnmatch import fnmatchcase
from pathlib import Path

from tools import project
from tools.report import ToolError

EXPLAINED = Path(__file__).with_name("jar-changes.txt")


@dataclass(frozen=True)
class Difference:
    state: str
    entry: str
    explained: bool

    def __str__(self) -> str:
        return f"  {'explained' if self.explained else 'NOT EXPLAINED'} {self.state} {self.entry}"


def entry_hashes(jar: Path) -> dict[str, str]:
    with zipfile.ZipFile(jar) as archive:
        return {name: hashlib.sha256(archive.read(name)).hexdigest() for name in archive.namelist() if not name.endswith("/")}


def explained_patterns(text: str) -> list[str]:
    """The entry wildcards of the lines "<entry wildcard> <reason>", where "#" starts a comment line."""
    return [line.split(maxsplit=1)[0] for line in text.splitlines() if line.strip() and not line.startswith("#")]


def differences(old: dict[str, str], new: dict[str, str], patterns: list[str]) -> list[Difference]:
    found = []
    for entry in sorted(old.keys() | new.keys()):
        if entry not in new:
            state = "removed"
        elif entry not in old:
            state = "added"
        elif old[entry] != new[entry]:
            state = "changed"
        else:
            continue
        found.append(Difference(state, entry, any(fnmatchcase(entry, pattern) for pattern in patterns)))
    return found


def compare(old_dir: Path, new_dir: Path, patterns: list[str]) -> bool:
    """Prints the differences of each pair of jars and tells whether all of them are explained."""
    old_jars = {project.jar_key(jar.name): jar for jar in sorted(old_dir.glob("*.jar"))}
    new_jars = {project.jar_key(jar.name): jar for jar in sorted(new_dir.glob("*.jar"))}
    if not new_jars:
        raise ToolError(f"no jars in {new_dir}")
    passed = True
    for key, jar in new_jars.items():
        if key not in old_jars:
            print(f"{jar.name}: FAIL, no jar with this name in {old_dir}")
            passed = False
            continue
        new_hashes = entry_hashes(jar)
        found = differences(entry_hashes(old_jars[key]), new_hashes, patterns)
        unexplained = sum(not difference.explained for difference in found)
        passed = passed and unexplained == 0
        print(f"{jar.name}: {len(new_hashes)} entries, {len(found)} different, {unexplained} not explained")
        for difference in found:
            print(difference)
    for key in old_jars.keys() - new_jars.keys():
        print(f"{old_jars[key].name}: FAIL, no jar with this name in {new_dir}")
        passed = False
    return passed


def main(arguments: list[str]) -> int:
    parser = argparse.ArgumentParser(prog="python -m tools compare-jars", description=__doc__.splitlines()[0])
    parser.add_argument("old", type=Path, help="the folder with the jars of the release")
    parser.add_argument("new", type=Path, help="the folder with the built jars")
    parser.add_argument("--explained", type=Path, default=EXPLAINED, help="the list of the entries that can be different")
    options = parser.parse_args(arguments)
    passed = compare(options.old, options.new, explained_patterns(options.explained.read_text(encoding="utf-8")))
    print("PASS" if passed else "FAIL")
    return 0 if passed else 1
