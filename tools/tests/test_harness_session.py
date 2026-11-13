import struct
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from tools import gradle
from tools.harness import protocol, rundir, session
from tools.harness.desktop import Launch
from tools.report import ToolError


class FakeSystem:
    """A game that writes its log lines at given times and ends at a given time, or never."""

    def __init__(self, harness: Path, launch: Launch, ends_at: float | None, harness_log: dict[float, str] | None = None, game_log: str = ""):
        self.harness, self.launch, self.ends_at = harness, launch, ends_at
        self.harness_log = harness_log or {}
        self.game_log = game_log
        self.time = 0.0
        self.stopped = False

    def uses(self, harness: Path) -> bool:
        return harness.name == "busy"

    def start(self, launch: Launch, harness: Path) -> int:
        launch.log.write_text(self.game_log, encoding="utf-8")
        return 1

    def alive(self, pid: int) -> bool:
        return not self.stopped and (self.ends_at is None or self.time < self.ends_at)

    def stop(self, pid: int, harness: Path) -> None:
        self.stopped = True

    def sleep(self, seconds: float) -> None:
        self.time += seconds
        lines = [line for at, line in sorted(self.harness_log.items()) if at <= self.time]
        (self.harness / protocol.LOG).write_text("".join(f"{line}\n" for line in lines), encoding="utf-8")

    def now(self) -> float:
        return self.time


class PlayTest(unittest.TestCase):
    def setUp(self):
        self.harness = Path(tempfile.mkdtemp())
        self.launch = Launch("game", self.harness, self.harness / "game.log")
        self.said: list[str] = []

    def play(self, system: FakeSystem, timeout: float = 600) -> FakeSystem:
        session.play(self.launch, self.harness, timeout, system, self.said.append)
        return system

    def system(self, ends_at: float | None, **more) -> FakeSystem:
        return FakeSystem(self.harness, self.launch, ends_at, **more)

    def test_a_game_that_ends_after_quit_is_not_stopped(self):
        system = self.play(self.system(ends_at=40, harness_log={30: "> quit"}))
        self.assertFalse(system.stopped)
        self.assertEqual([], self.said)
        self.assertEqual(40, system.time)

    def test_a_game_that_stays_after_quit_is_stopped_after_the_exit_time(self):
        system = self.play(self.system(ends_at=None, harness_log={30: "> quit"}))
        self.assertTrue(system.stopped)
        self.assertEqual(["still running; stopping it"], self.said)
        self.assertEqual(30 + session.EXIT_SECONDS, system.time)

    def test_a_game_that_never_quits_is_stopped_at_the_timeout(self):
        system = self.play(self.system(ends_at=None), timeout=60)
        self.assertTrue(system.stopped)
        self.assertEqual(60, system.time)

    def test_a_game_that_ends_with_no_quit_is_not_waited_for(self):
        system = self.play(self.system(ends_at=9))
        self.assertFalse(system.stopped)
        self.assertEqual(9, system.time)

    def test_a_loader_that_did_not_start_is_stopped_at_once(self):
        system = self.play(self.system(ends_at=None, game_log='Uncaught exception in thread "main"\n'))
        self.assertTrue(system.stopped)
        self.assertEqual(["the loader did not start", "still running; stopping it"], self.said)
        self.assertEqual(session.POLL_SECONDS, system.time)


class OpenHarnessTest(unittest.TestCase):
    def setUp(self):
        self.root = Path(tempfile.mkdtemp())
        self.system = FakeSystem(self.root, Launch("game", self.root, self.root / "game.log"), ends_at=None)
        patch = mock.patch.object(session, "HARNESS_ROOT", self.root)
        patch.start()
        self.addCleanup(patch.stop)

    def test_a_new_folder_has_the_commands_and_the_clip_probe_and_nothing_of_an_old_run(self):
        (self.root / "fabric-26.3").mkdir()
        (self.root / "fabric-26.3" / "old.png").write_bytes(b"old")
        harness = session.open_harness("fabric-26.3", ["wait 5", "quit"], self.system)
        self.assertEqual([protocol.CLIP_PROBE, protocol.COMMANDS], sorted(file.name for file in harness.iterdir()))
        self.assertEqual("wait 5\nquit\n", (harness / protocol.COMMANDS).read_text())
        probe = (harness / protocol.CLIP_PROBE).read_text().splitlines()
        self.assertEqual(3, len(probe))
        self.assertTrue(Path(probe[-1]).is_file(), probe[-1])

    def test_a_folder_that_a_game_uses_is_left_alone(self):
        (self.root / "busy").mkdir()
        (self.root / "busy" / "log.txt").write_text("> wait 5\n")
        with self.assertRaises(ToolError):
            session.open_harness("busy", ["quit"], self.system)
        self.assertTrue((self.root / "busy" / "log.txt").is_file())


class FilesTest(unittest.TestCase):
    def setUp(self):
        self.dir = Path(tempfile.mkdtemp())

    def png(self, name: str, width: int, height: int) -> Path:
        file = self.dir / name
        file.write_bytes(b"\x89PNG\r\n\x1a\n" + struct.pack(">I4sII", 13, b"IHDR", width, height))
        return file

    def test_the_size_of_a_png(self):
        self.assertEqual((1600, 900), session.png_size(self.png("a.png", 1600, 900)))
        (self.dir / "empty.png").write_bytes(b"")
        self.assertIsNone(session.png_size(self.dir / "empty.png"))

    def test_the_evidence_of_a_run(self):
        self.png("01-vanilla.png", 1600, 900)
        (self.dir / protocol.LOG).write_text("> quit\n", encoding="utf-8")
        (self.dir / protocol.TAKEN).write_text("screenshot 01-vanilla\nquit\n", encoding="utf-8")
        evidence = session.read_evidence(self.dir, self.dir / "no.log", self.dir)
        self.assertEqual(["> quit"], evidence.harness_log)
        self.assertEqual("", evidence.game_log)
        self.assertEqual(["screenshot 01-vanilla", "quit"], evidence.taken_commands)
        self.assertEqual({"01-vanilla.png": (1600, 900)}, evidence.image_sizes)
        self.assertFalse(evidence.has_config)

    def test_collect_copies_the_screenshots_to_a_new_folder(self):
        self.png("b.png", 1, 1)
        self.png("a.png", 1, 1)
        out = self.dir / "out"
        out.mkdir()
        (out / "old.png").write_bytes(b"old")
        self.assertEqual(["a.png", "b.png"], [image.name for image in session.collect(self.dir, out)])
        self.assertEqual(["a.png", "b.png"], sorted(file.name for file in out.iterdir()))

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


class GradleCommandLineTest(unittest.TestCase):
    def test_each_property_that_is_set_is_a_flag(self):
        properties = {"harness": r"C:\Temp\h", "compat": "", "backend": "vulkan", "vulkanValidation": False, "quilt": True}
        line = gradle.hidden_command_line(Path("modern"), ":fabric:26.3:runClient", properties)
        wrapper = gradle.wrapper(Path("modern"))
        self.assertEqual(
            f'"{wrapper}" --no-daemon --configure-on-demand :fabric:26.3:runClient "-Pharness=C:\\Temp\\h" "-Pbackend=vulkan" -Pquilt', line
        )


if __name__ == "__main__":
    unittest.main()
