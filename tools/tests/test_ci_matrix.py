import unittest

from tools import ci_matrix, project
from tools.project import Node, Target

FABRIC = Target("fabric", "26.1", "26.1.2", 25, legacy=False)
NEOFORGE = Target("neoforge", "26.1", "26.1.2", 25, legacy=False)
MODERN = Node("26.1", False, (FABRIC, NEOFORGE))
LEGACY = Node("1.8.9", True, (Target("forge", "1.8.9", "1.8.9", 8, legacy=True),))


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

    def test_each_row_uploads_its_own_minecraft_version(self):
        for row in self.outputs["nodes"]:
            self.assertIn(row["mc"], row.get("fabric_game_versions", row["game_versions"]).split("\n"))
