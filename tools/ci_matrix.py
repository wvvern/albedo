"""Prints the outputs of the plan job of .github/workflows/ci.yml, one "name=value" line each.

nodes  a row for each node of the two builds: what its build job builds, and what its Modrinth uploads list
jars   how many jars a release has

The keys of a row:
  mc                    the Minecraft version, which is the end of the jar names
  java, gradle_java     the Java of the node and the Java that runs Gradle
  targets               the targets of "python -m tools build", with a space between them
  loaders               the loaders of the node
  cache                 whether the build job writes the Gradle cache; the entries of all modern nodes together are larger than
                        the cache limit of the repository
  game_versions         the released versions of the jars, from modrinth_versions of the node
  fabric_game_versions  the released versions of the Fabric jar, when modrinth_versions_fabric gives it more
  fabric_loaders        the Modrinth loaders of the Fabric jar, from modrinth_loaders_fabric

A value with more than one version or loader has one in each line, which is what mc-publish reads.
"""

import argparse
import json

from tools import project
from tools.project import Node


def lines(words: str) -> str:
    return "\n".join(words.split())


def row(node: Node, properties: dict[str, str]) -> dict:
    """The row of one node, where a key of the Fabric upload is there only when the node sets its property."""
    made = {
        "mc": node.minecraft,
        "java": node.java,
        "gradle_java": node.gradle_java,
        "targets": " ".join(target.name for target in node.targets),
        "loaders": [target.loader for target in node.targets],
        "cache": node.legacy,
        "game_versions": lines(properties.get("modrinth_versions", node.minecraft)),
    }
    for key, name in (("fabric_game_versions", "modrinth_versions_fabric"), ("fabric_loaders", "modrinth_loaders_fabric")):
        if name in properties:
            made[key] = lines(properties[name])
    return made


def outputs(nodes: tuple[Node, ...]) -> dict[str, object]:
    return {"nodes": [row(node, node.properties()) for node in nodes], "jars": sum(len(node.targets) for node in nodes)}


def main(arguments: list[str]) -> int:
    parser = argparse.ArgumentParser(prog="python -m tools ci-matrix", description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.parse_args(arguments)
    for name, value in outputs(project.nodes()).items():
        print(f"{name}={json.dumps(value, separators=(',', ':'))}")
    return 0
