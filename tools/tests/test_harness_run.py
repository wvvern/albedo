import unittest
from pathlib import Path

from tools.harness import run
from tools.harness.verdict import Loader
from tools.report import ToolError


class ParseTest(unittest.TestCase):
    def test_one_suite_keeps_the_output_folder(self):
        targets, runs = run.parse(["fabric-26.3", "forge-1.8.9", "--suite", "edges", "--out", "build/x"])
        self.assertEqual(["fabric-26.3", "forge-1.8.9"], [target.name for target in targets])
        self.assertEqual([("edges", Path("build/x"))], [(options.suite, options.out) for options in runs])

    def test_each_suite_gets_a_run_and_a_folder_of_its_own(self):
        _, runs = run.parse(["fabric-26.3", "--suite", "shots", "edges", "--out", "build/x"])
        self.assertEqual([("shots", Path("build/x/shots")), ("edges", Path("build/x/edges"))], [(options.suite, options.out) for options in runs])
        self.assertTrue(all(options.own_folder for options in runs))

    def test_one_suite_runs_in_the_game_folder_of_the_node(self):
        _, runs = run.parse(["fabric-26.3", "--suite", "edges"])
        self.assertFalse(runs[0].own_folder)

    def test_the_games_that_run_at_the_same_time_fit_in_the_free_memory_and_the_threads(self):
        self.assertEqual(4, run.game_slots(112, 4 * run.GAME_BYTES + 1, 32))
        self.assertEqual(21, run.game_slots(112, 100 * run.GAME_BYTES, 32))
        self.assertEqual(3, run.game_slots(3, 100 * run.GAME_BYTES, 32))
        self.assertEqual(1, run.game_slots(5, 0, 32))

    def test_the_loader_is_fabric_unless_given(self):
        _, runs = run.parse(["fabric-26.3"])
        self.assertIs(Loader.FABRIC, runs[0].loader)
        _, runs = run.parse(["fabric-26.3", "--loader", "quilt-dev"])
        self.assertIs(Loader.QUILT_DEV, runs[0].loader)

    def test_quilt_runs_only_fabric_targets(self):
        with self.assertRaisesRegex(ToolError, "--loader quilt runs only fabric targets"):
            run.parse(["fabric-26.3", "forge-1.8.9", "--loader", "quilt"])

    def test_a_shader_pack_needs_iris(self):
        with self.assertRaisesRegex(ToolError, "--shader-pack needs --compat iris"):
            run.parse(["fabric-26.3", "--shader-pack", "pack.zip"])


if __name__ == "__main__":
    unittest.main()
