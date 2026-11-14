import unittest

from tools.harness import rundir
from tools.tests.fixtures import temp_dir


class RunDirTest(unittest.TestCase):
    def setUp(self):
        self.dir = temp_dir(self)

    def test_an_option_of_the_command_line_needs_its_colon(self):
        self.assertEqual("useVbo:false", rundir.key_value("useVbo:false"))
        with self.assertRaises(ValueError):
            rundir.key_value("useVbo=false")

    def test_options_are_lines_of_key_and_value(self):
        file = self.dir / "run" / "options.txt"
        rundir.write_options(file, {"narrator": "0", "lastServer": "host:25565"})
        self.assertEqual("narrator:0\nlastServer:host:25565\n", file.read_text())
        self.assertEqual({"narrator": "0", "lastServer": "host:25565"}, rundir.read_options(file))
        self.assertEqual({}, rundir.read_options(self.dir / "none.txt"))

    def test_a_toml_key_goes_to_the_top_in_place_of_its_old_line(self):
        file = self.dir / "config" / "fml.toml"
        rundir.set_toml_key(file, "earlyWindowControl", "false")
        self.assertEqual("earlyWindowControl = false\n", file.read_text())
        file.write_text("earlyWindowControl=true\nearlyWindowControlFlag = 1\n[table]\nkey = 2\n")
        rundir.set_toml_key(file, "earlyWindowControl", "false")
        self.assertEqual("earlyWindowControl = false\nearlyWindowControlFlag = 1\n[table]\nkey = 2\n", file.read_text())

    def test_iris_gets_its_pack_and_its_switch(self):
        pack = self.dir / "pack.zip"
        pack.write_bytes(b"zip")
        run_dir = self.dir / "run"
        rundir.write_iris_properties(run_dir, pack)
        self.assertEqual("disableUpdateMessage=true\nenableShaders=true\nshaderPack=pack.zip\n", (run_dir / "config" / "iris.properties").read_text())
        self.assertTrue((run_dir / "shaderpacks" / "pack.zip").is_file())
        rundir.write_iris_properties(run_dir, None)
        self.assertEqual("disableUpdateMessage=true\nenableShaders=false\n", (run_dir / "config" / "iris.properties").read_text())

    def test_the_mod_starts_from_its_default_config_and_a_new_world(self):
        run_dir = self.dir / "run"
        config = run_dir / "config" / "albedo" / "config.json"
        (run_dir / "saves" / rundir.WORLD).mkdir(parents=True)
        config.parent.mkdir(parents=True)
        config.write_text("{}")
        rundir.reset_mod_state(run_dir)
        self.assertFalse(config.exists())
        self.assertFalse((run_dir / "saves" / rundir.WORLD).exists())
        given = self.dir / "given.json"
        given.write_text('{"profile": "quake-white"}')
        rundir.reset_mod_state(run_dir, given)
        self.assertEqual('{"profile": "quake-white"}', config.read_text())
