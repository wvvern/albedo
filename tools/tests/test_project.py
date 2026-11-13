import unittest

from tools import project
from tools.report import ToolError

SETTINGS = """
def nodes = ['1.20.4', '26.1']
def forgeNodes = ['1.20.4']
def neoforgeNodes = ['1.20.4', '26.1']
"""


class NodeListsTest(unittest.TestCase):
    def test_reads_the_list_of_each_loader(self):
        lists = project.modern_node_lists(SETTINGS)
        self.assertEqual({"fabric": ["1.20.4", "26.1"], "forge": ["1.20.4"], "neoforge": ["1.20.4", "26.1"]}, lists)

    def test_a_missing_list_is_an_error(self):
        with self.assertRaises(ToolError):
            project.modern_node_lists("def nodes = ['26.1']")

    def test_reads_the_legacy_versions(self):
        self.assertEqual(["1.8.9", "1.12.2"], project.legacy_versions('for (mc in listOf("1.8.9", "1.12.2")) {'))


class TargetTest(unittest.TestCase):
    def test_a_modern_target_names_its_tasks_and_jar(self):
        target = project.Target("neoforge", "26.1", "26.1.2", 25, legacy=False)
        self.assertEqual("neoforge-26.1", target.name)
        self.assertEqual(":neoforge:26.1:releaseJar", target.release_task)
        self.assertEqual(":neoforge:26.1:runClient", target.run_task)
        self.assertEqual("albedo-neoforge-1.0.0+26.1.2.jar", target.jar_name("1.0.0"))
        self.assertEqual(project.MODERN / "neoforge" / "versions" / "26.1" / "run", target.run_dir)

    def test_a_legacy_target_names_its_tasks_and_jar(self):
        target = project.Target("forge", "1.8.9", "1.8.9", 8, legacy=True)
        self.assertEqual(":1.8.9:build", target.release_task)
        self.assertEqual(":1.8.9:runClientDev", target.run_task)
        self.assertEqual(project.LEGACY / "versions" / "1.8.9" / "build" / "libs" / "albedo-forge-1.0.0+1.8.9.jar", target.jar("1.0.0"))

    def test_versions_compare_by_number(self):
        target = project.Target("fabric", "1.21.10", "1.21.10", 21, legacy=False)
        self.assertTrue(target.at_least("1.21.9"))
        self.assertTrue(target.at_least("1.21.10"))
        self.assertFalse(target.at_least("26.1"))


class RepositoryTest(unittest.TestCase):
    """The model against the build files of this repository."""

    def test_each_target_has_a_unique_name_and_jar(self):
        targets = project.targets()
        self.assertEqual(len(targets), len({target.name for target in targets}))
        self.assertEqual(len(targets), len({target.jar_name("0") for target in targets}))

    def test_a_jar_name_gives_its_target(self):
        for target in project.targets():
            self.assertEqual(target, project.target_of_jar(target.jar_name("1.2.3")))

    def test_a_jar_of_no_target_is_an_error(self):
        for name in ("albedo-fabric-1.0.0+0.0.1.jar", "albedo-quilt-1.0.0+26.3.jar", "other.jar"):
            with self.assertRaises(ToolError):
                project.target_of_jar(name)

    def test_the_legacy_targets_come_last(self):
        flags = [target.legacy for target in project.targets()]
        self.assertEqual(sorted(flags), flags)
        self.assertTrue(flags[-1])

    def test_an_unknown_target_is_an_error(self):
        with self.assertRaises(ToolError):
            project.find_target("fabric-0.0.1")


if __name__ == "__main__":
    unittest.main()
