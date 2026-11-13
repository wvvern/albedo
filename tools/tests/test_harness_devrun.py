import tempfile
import unittest
from pathlib import Path
from unittest import mock

from tools import project
from tools.harness import devrun, rundir, verdict
from tools.harness.devrun import RunOptions
from tools.project import Target

FABRIC = Target("fabric", "26.3", "26.3", 25, legacy=False)
NEOFORGE = Target("neoforge", "26.3", "26.3", 25, legacy=False)
FORGE = Target("forge", "1.20.1", "1.20.1", 17, legacy=False)
LEGACY = Target("forge", "1.8.9", "1.8.9", 8, legacy=True)
HARNESS = Path("harness")


class OptionsTest(unittest.TestCase):
    def test_the_scene_of_the_build_runs_first_and_the_suite_last(self):
        options = RunOptions(suite="chroma", extra=("pastel",))
        self.assertEqual(["scene-modern", "pastel", "chroma"], options.suites(FABRIC))
        self.assertEqual(["scene-legacy", "pastel", "chroma"], options.suites(LEGACY))

    def test_only_a_game_with_a_choice_of_backend_gets_one(self):
        options = RunOptions(backend="vulkan")
        self.assertEqual("vulkan", options.forced_backend(FABRIC))
        self.assertEqual("", options.forced_backend(FORGE))
        self.assertEqual("", options.forced_backend(LEGACY))

    def test_iris_is_one_mod_of_a_list_on_a_modern_target(self):
        self.assertTrue(RunOptions(compat="sodium, iris").iris(FABRIC))
        self.assertFalse(RunOptions(compat="sodium").iris(FABRIC))
        self.assertFalse(RunOptions(compat="iris").iris(LEGACY))


class LaunchTest(unittest.TestCase):
    def test_the_gradle_properties_of_a_plain_run(self):
        properties = devrun.gradle_properties(FABRIC, HARNESS, RunOptions())
        self.assertEqual({"harness": "harness"}, {name: value for name, value in properties.items() if value})

    def test_quilt_runs_the_release_jar_and_quilt_dev_the_classes(self):
        quilt = devrun.gradle_properties(FABRIC, HARNESS, RunOptions(loader="quilt"))
        quilt_dev = devrun.gradle_properties(FABRIC, HARNESS, RunOptions(loader="quilt-dev"))
        self.assertEqual((True, True), (quilt["quilt"], quilt["quiltJar"]))
        self.assertEqual((True, False), (quilt_dev["quilt"], quilt_dev["quiltJar"]))

    def test_the_launch_runs_the_run_task_of_the_target_in_its_build(self):
        launch = devrun.launch(LEGACY, HARNESS, RunOptions(compat="sodium", backend="vulkan", vulkan_validation=True))
        self.assertEqual(project.LEGACY, launch.cwd)
        self.assertEqual(HARNESS / "gradle.log", launch.log)
        self.assertIn(' :1.8.9:runClientDev "-Pharness=harness" "-Pcompat=sodium" -PvulkanValidation', launch.command_line)
        self.assertNotIn("backend", launch.command_line)


class ExpectationsTest(unittest.TestCase):
    def test_a_renderer_mod_changes_the_hooks(self):
        self.assertEqual(verdict.MODERN_HOOKS, devrun.expectations(FABRIC, RunOptions()).hooks)
        expected = devrun.expectations(FABRIC, RunOptions(compat="sodium", backend="vulkan", loader="quilt"))
        self.assertEqual(
            (verdict.RENDERER_MOD_HOOKS, "vulkan", "quilt", True, False),
            (expected.hooks, expected.backend, expected.loader, expected.renderer_mod, expected.shader_pack),
        )

    def test_a_shader_pack_counts_only_with_iris(self):
        pack = Path("pack.zip")
        self.assertTrue(devrun.expectations(FABRIC, RunOptions(compat="iris", shader_pack=pack)).shader_pack)
        self.assertFalse(devrun.expectations(LEGACY, RunOptions(compat="iris", shader_pack=pack)).shader_pack)


class RunDirTest(unittest.TestCase):
    def test_the_config_of_each_loader(self):
        self.assertEqual({}, devrun.loader_config(FABRIC, renderer_mod=False))
        self.assertEqual({}, devrun.loader_config(LEGACY, renderer_mod=False))
        self.assertEqual({"fml.toml": ("earlyWindowControl", "false")}, devrun.loader_config(FORGE, renderer_mod=True))
        self.assertEqual(("enableB3DValidationLayer", "true"), devrun.loader_config(NEOFORGE, renderer_mod=False)["neoforge-client.toml"])
        self.assertEqual(("enableB3DValidationLayer", "false"), devrun.loader_config(NEOFORGE, renderer_mod=True)["neoforge-client.toml"])

    def test_the_game_folder_of_a_run(self):
        run_dir = Path(tempfile.mkdtemp())
        (run_dir / "mods").mkdir()
        (run_dir / "mods" / "albedo-fabric-0.6.2+26.3.jar").write_bytes(b"jar")
        (run_dir / "mods" / "sodium.jar").write_bytes(b"jar")
        rundir.mod_config(run_dir).parent.mkdir(parents=True)
        rundir.mod_config(run_dir).write_text("{}")
        with mock.patch.object(Target, "run_dir", run_dir):
            devrun.prepare_run_dir(NEOFORGE, RunOptions(gui_scale=2, option_lines=("useVbo:false", "narrator:1"), compat="iris"))
        options = rundir.read_options(run_dir / "options.txt")
        self.assertEqual(("2", "false", "1", "0.0"), (options["guiScale"], options["useVbo"], options["narrator"], options["soundCategory_master"]))
        self.assertEqual(["sodium.jar"], [jar.name for jar in (run_dir / "mods").iterdir()])
        self.assertEqual("earlyWindowControl = false\n", (run_dir / "config" / "fml.toml").read_text())
        self.assertEqual("enableB3DValidationLayer = false\n", (run_dir / "config" / "neoforge-client.toml").read_text())
        self.assertIn("enableShaders=false", (run_dir / "config" / "iris.properties").read_text())
        self.assertFalse(rundir.mod_config(run_dir).exists())
