"""Prints the CHANGELOG.md section of one version, without its heading. CI uses it as the release notes."""

import argparse

from tools import project
from tools.report import ToolError


def section(changelog: str, version: str) -> str:
    """The lines under the heading "## <version>", with no empty lines around them; empty when there is no such heading."""
    lines = []
    found = False
    for line in changelog.splitlines():
        if line.startswith("## "):
            if found:
                break
            found = line.split()[1:2] == [version]
        elif found:
            lines.append(line)
    return "\n".join(lines).strip("\n")


def main(arguments: list[str]) -> int:
    parser = argparse.ArgumentParser(prog="python -m tools release-notes", description=__doc__)
    parser.add_argument("version", help="the version or its tag, for example 1.2.0 or v1.2.0")
    version = parser.parse_args(arguments).version.removeprefix("v")
    notes = section((project.ROOT / "CHANGELOG.md").read_text(encoding="utf-8"), version)
    if not notes.strip():
        raise ToolError(f"CHANGELOG.md has no section '## {version}'. Write it before you push the tag (see docs/RELEASING.md).")
    print(notes)
    return 0
