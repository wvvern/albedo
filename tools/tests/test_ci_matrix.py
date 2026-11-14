import re
import unittest

from tools import ci_matrix, project
from tools.project import Node
from tools.tests.fixtures import FABRIC_26_1, LEGACY_1_8_9, NEOFORGE_26_1

MODERN = Node("26.1", False, (FABRIC_26_1, NEOFORGE_26_1))
LEGACY = Node("1.8.9", True, (LEGACY_1_8_9,))


class RowTest(unittest.TestCase):
    def test_a_modern_node_builds_its_targets_with_its_java(self):
        self.assertEqual(
            {"mc": "26.1.2", "java": 25, "gradle_java": 25, "targets": "fabric-26.1 neoforge-26.1", "cache": False},
            ci_matrix.row(MODERN),
        )

    def test_a_legacy_node_builds_with_its_own_gradle_java_and_writes_the_cache(self):
        row = ci_matrix.row(LEGACY)
        self.assertEqual((8, 21, "forge-1.8.9", True), (row["java"], row["gradle_java"], row["targets"], row["cache"]))


class UploadTest(unittest.TestCase):
    def test_a_jar_with_no_modrinth_property_lists_its_loader_and_minecraft_version(self):
        self.assertEqual(
            {"mc": "26.1.2", "loader": "neoforge", "modrinth_loaders": "neoforge", "game_versions": "26.1.2"},
            ci_matrix.upload(NEOFORGE_26_1, {}),
        )

    def test_the_fabric_jar_falls_back_to_the_node_versions_and_its_loader(self):
        upload = ci_matrix.upload(FABRIC_26_1, {"modrinth_versions": "26.1.1  26.1.2"})
        self.assertEqual(("fabric", "26.1.1 26.1.2"), (upload["modrinth_loaders"], upload["game_versions"]))

    def test_the_fabric_properties_apply_only_to_the_fabric_jar(self):
        properties = {
            "modrinth_versions": "26.1.1 26.1.2",
            "modrinth_versions_fabric": "26.1 26.1.1 26.1.2",
            "modrinth_loaders_fabric": "fabric quilt",
        }
        fabric = ci_matrix.upload(FABRIC_26_1, properties)
        neoforge = ci_matrix.upload(NEOFORGE_26_1, properties)
        self.assertEqual(("fabric quilt", "26.1 26.1.1 26.1.2"), (fabric["modrinth_loaders"], fabric["game_versions"]))
        self.assertEqual(("neoforge", "26.1.1 26.1.2"), (neoforge["modrinth_loaders"], neoforge["game_versions"]))


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

    def test_each_jar_has_one_upload(self):
        uploaded = [(row["loader"], row["mc"]) for row in self.outputs["uploads"]]
        self.assertEqual([(target.loader, target.minecraft) for target in project.targets()], uploaded)

    def test_each_upload_lists_its_own_minecraft_version_and_loader(self):
        for row in self.outputs["uploads"]:
            self.assertIn(row["mc"], row["game_versions"].split())
            self.assertIn(row["loader"], row["modrinth_loaders"].split())
