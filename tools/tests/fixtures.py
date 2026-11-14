"""Targets and temporary folders that the tool tests share."""

import tempfile
import unittest
from pathlib import Path

from tools.project import LEGACY_JAVA, Target


def modern(loader: str, minecraft: str, java: int, node: str | None = None) -> Target:
    """A modern target. The node is the Minecraft version unless given."""
    return Target(loader, node or minecraft, minecraft, java, legacy=False)


def legacy(minecraft: str) -> Target:
    return Target("forge", minecraft, minecraft, LEGACY_JAVA, legacy=True)


def temp_dir(test: unittest.TestCase) -> Path:
    """A new empty folder that is deleted after the test."""
    folder = tempfile.TemporaryDirectory()
    test.addCleanup(folder.cleanup)
    return Path(folder.name)


LEGACY_1_8_9 = legacy("1.8.9")
LEGACY_1_12_2 = legacy("1.12.2")
FABRIC_26_3 = modern("fabric", "26.3", 25)
NEOFORGE_26_3 = modern("neoforge", "26.3", 25)
FABRIC_26_1 = modern("fabric", "26.1.2", 25, node="26.1")
NEOFORGE_26_1 = modern("neoforge", "26.1.2", 25, node="26.1")
FABRIC_1_20_1 = modern("fabric", "1.20.1", 17)
FORGE_1_20_1 = modern("forge", "1.20.1", 17)
FABRIC_1_18_2 = modern("fabric", "1.18.2", 17)
FORGE_1_18_2 = modern("forge", "1.18.2", 17)
NEOFORGE_1_18_2 = modern("neoforge", "1.18.2", 17)
FORGE_1_17_1 = modern("forge", "1.17.1", 16)
FABRIC_1_16_5 = modern("fabric", "1.16.5", 8)
