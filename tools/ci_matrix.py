"""Prints the outputs of the plan job of .github/workflows/ci.yml, one "name=value" line each.

modern   a row for each modern node: the build of its jars and their uploads to Modrinth
legacy   the Minecraft versions of the legacy build
publish  the rows of all Modrinth uploads: the modern rows and a row for each legacy version
jars     how many jars a release has

A value with more than one version or loader has one in each line, which is what mc-publish reads.
"""

import argparse
import json
from collections.abc import Iterable
from itertools import groupby

from tools import project
from tools.project import Target


def lines(words: str) -> str:
    return "\n".join(words.split())


def modern_row(loaders: list[Target], properties: dict[str, str]) -> dict:
    """The row of one node, where a key of the Fabric upload is there only when the node sets its property."""
    first = loaders[0]
    row = {
        "node": first.node,
        "mc": first.minecraft,
        "java": first.java,
        "loaders": [target.loader for target in loaders],
        "game_versions": lines(properties.get("modrinth_versions", first.minecraft)),
    }
    for key, name in (("fabric_game_versions", "modrinth_versions_fabric"), ("fabric_loaders", "modrinth_loaders_fabric")):
        if name in properties:
            row[key] = lines(properties[name])
    return row


def legacy_row(target: Target) -> dict:
    return {"mc": target.minecraft, "loaders": [target.loader], "game_versions": target.minecraft}


def outputs(targets: Iterable[Target]) -> dict[str, object]:
    targets = list(targets)
    nodes = groupby((target for target in targets if not target.legacy), key=lambda target: target.node)
    modern = [modern_row(loaders := list(group), loaders[0].properties()) for _, group in nodes]
    legacy = [target for target in targets if target.legacy]
    return {
        "modern": modern,
        "legacy": [target.minecraft for target in legacy],
        "publish": modern + [legacy_row(target) for target in legacy],
        "jars": len(targets),
    }


def main(arguments: list[str]) -> int:
    argparse.ArgumentParser(prog="python -m tools ci-matrix", description=__doc__.splitlines()[0]).parse_args(arguments)
    for name, value in outputs(project.targets()).items():
        print(f"{name}={json.dumps(value, separators=(',', ':'))}")
    return 0
