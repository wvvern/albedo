import unittest

from tools import ci_matrix, project
from tools.project import Target

FABRIC = Target("fabric", "26.1", "26.1.2", 25, legacy=False)
NEOFORGE = Target("neoforge", "26.1", "26.1.2", 25, legacy=False)


class ModernRowTest(unittest.TestCase):
    def test_a_node_with_no_modrinth_property_lists_its_minecraft_version(self):
        self.assertEqual(
            {"node": "26.1", "mc": "26.1.2", "java": 25, "loaders": ["fabric", "neoforge"], "game_versions": "26.1.2"},
            ci_matrix.modern_row([FABRIC, NEOFORGE], {}),
        )

    def test_each_modrinth_property_gives_one_value_in_each_line(self):
        properties = {
            "modrinth_versions": "26.1.1 26.1.2",
            "modrinth_versions_fabric": "26.1 26.1.1 26.1.2",
            "modrinth_loaders_fabric": "fabric quilt",
        }
        row = ci_matrix.modern_row([FABRIC], properties)
        self.assertEqual("26.1.1\n26.1.2", row["game_versions"])
        self.assertEqual("26.1\n26.1.1\n26.1.2", row["fabric_game_versions"])
        self.assertEqual("fabric\nquilt", row["fabric_loaders"])


class RepositoryTest(unittest.TestCase):
    """The outputs for the build files of this repository."""

    def setUp(self):
        self.outputs = ci_matrix.outputs(project.targets())

    def test_the_rows_build_each_jar_once(self):
        built = sum(len(row["loaders"]) for row in self.outputs["modern"]) + len(self.outputs["legacy"])
        self.assertEqual(self.outputs["jars"], built)
        self.assertEqual(len(project.targets()), built)

    def test_each_upload_row_has_its_jars_and_game_versions(self):
        rows = self.outputs["publish"]
        self.assertEqual(self.outputs["jars"], sum(len(row["loaders"]) for row in rows))
        for row in rows:
            self.assertIn(row["mc"], row.get("fabric_game_versions", row["game_versions"]).split("\n"))


if __name__ == "__main__":
    unittest.main()
