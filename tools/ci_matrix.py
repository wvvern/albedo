"""Prints the outputs of the plan job of .github/workflows/ci.yml, one "name=value" line each.

nodes    a row for each node of the two builds, for its build job
uploads  a row for each jar, for its Modrinth upload with .github/workflows/modrinth.yml
jars     how many jars a release has

The keys of a nodes row:
  mc                the Minecraft version, which is the end of the jar names
  java, gradle_java the Java of the node and the Java that runs Gradle
  targets           the targets of "python -m tools build", with a space between them
  cache             whether the build job writes the Gradle cache; the entries of all modern nodes together are larger than the
                    cache limit of the repository

The keys of an uploads row, which are the inputs of modrinth.yml:
  mc                the Minecraft version of the jar
  loader            the loader of the jar
  modrinth_loaders  the Modrinth loaders of the jar; for Fabric, modrinth_loaders_fabric of the node when it is set
  game_versions     the released versions of the jar, from modrinth_versions of the node; for Fabric, modrinth_versions_fabric
                    when it is set

A value with more than one version or loader has a space between them.
"""

import argparse
import json

from tools import project
from tools.project import Node, Target


def row(node: Node) -> dict:
    """The build row of one node."""
    return {
        "mc": node.minecraft,
        "java": node.java,
        "gradle_java": node.gradle_java,
        "targets": " ".join(target.name for target in node.targets),
        "cache": node.legacy,
    }


def upload(target: Target, properties: dict[str, str]) -> dict:
    """The upload row of one jar, from the properties of its node."""
    versions = properties.get("modrinth_versions", target.minecraft)
    loaders = target.loader
    if target.loader == "fabric":
        versions = properties.get("modrinth_versions_fabric", versions)
        loaders = properties.get("modrinth_loaders_fabric", loaders)
    return {
        "mc": target.minecraft,
        "loader": target.loader,
        "modrinth_loaders": " ".join(loaders.split()),
        "game_versions": " ".join(versions.split()),
    }


def outputs(nodes: tuple[Node, ...]) -> dict[str, object]:
    uploads = [upload(target, node.properties()) for node in nodes for target in node.targets]
    return {"nodes": [row(node) for node in nodes], "uploads": uploads, "jars": len(uploads)}


def main(arguments: list[str]) -> int:
    parser = argparse.ArgumentParser(prog="python -m tools ci-matrix", description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.parse_args(arguments)
    for name, value in outputs(project.nodes()).items():
        print(f"{name}={json.dumps(value, separators=(',', ':'))}")
    return 0
