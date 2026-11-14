import re
import unittest

from tools import ci_matrix, project
from tools.project import Node
from tools.tests.fixtures import FABRIC_26_1, LEGACY_1_8_9, NEOFORGE_26_1

MODERN = Node("26.1", False, (FABRIC_26_1, NEOFORGE_26_1))
LEGACY = Node("1.8.9", True, (LEGACY_1_8_9,))


class RowTest(unittest.TestCase):
    def test_a_node_with_no_modrinth_property_lists_its_minecraft_version(self):
        self.assertEqual(
            {
                "mc": "26.1.2",
                "java": 25,
                "gradle_java": 25,
                "targets": "fabric-26.1 neoforge-26.1",
                "loaders": ["fabric", "neoforge"],
                "cache": False,
                "game_versions": "26.1.2",
            },
            ci_matrix.row(MODERN, {}),
        )

    def test_each_modrinth_property_gives_one_value_in_each_line(self):
        properties = {
            "modrinth_versions": "26.1.1 26.1.2",
            "modrinth_versions_fabric": "26.1 26.1.1 26.1.2",
            "modrinth_loaders_fabric": "fabric quilt",
        }
        row = ci_matrix.row(MODERN, properties)
        self.assertEqual("26.1.1\n26.1.2", row["game_versions"])
        self.assertEqual("26.1\n26.1.1\n26.1.2", row["fabric_game_versions"])
        self.assertEqual("fabric\nquilt", row["fabric_loaders"])

    def test_a_legacy_node_builds_with_its_own_gradle_java_and_writes_the_cache(self):
        row = ci_matrix.row(LEGACY, {})
        self.assertEqual((8, 21, "forge-1.8.9", ["forge"], True), (row["java"], row["gradle_java"], row["targets"], row["loaders"], row["cache"]))


class RepositoryTest(unittest.TestCase):
    """The outputs for the build files of this repository."""

    def setUp(self):
        self.outputs = ci_matrix.outputs(project.nodes())

    def test_the_rows_build_each_jar_once(self):
        built = [name for row in self.outputs["nodes"] for name in row["targets"].split()]
        self.assertEqual([target.name for target in project.targets()], built)
        self.assertEqual(self.outputs["jars"], len(built))

    def test_the_check_job_has_the_core_tool_chain_and_the_gradle_java(self):
        workflow = (project.ROOT / ".github" / "workflows" / "ci.yml").read_text(encoding="utf-8")
        check_job = re.search(r"\n  check:\n(.*?)\n  \w+:\n", workflow, re.DOTALL).group(1)
        core = re.search(r"JavaLanguageVersion\.of\((\d+)\)", (project.ROOT / "core" / "build.gradle").read_text(encoding="utf-8")).group(1)
        java = re.search(r"java-version: \|\n\s+(\d+)\n\s+(\d+)\n", check_job).groups()
        self.assertEqual((core, str(project.MODERN_GRADLE_JAVA)), java)
        self.assertIn(f"JAVA_HOME_{core}_X64", check_job)

    def test_each_row_uploads_its_own_minecraft_version(self):
        for row in self.outputs["nodes"]:
            self.assertIn(row["mc"], row.get("fabric_game_versions", row["game_versions"]).split("\n"))
