import unittest
from pathlib import Path

from tools.harness import watchdog
from tools.harness.cursor import Clip
from tools.tests.fixtures import temp_dir

HARNESS = Path(r"C:\Temp\albedo-harness\fabric-26.3")
OWN_GAME = rf"java.exe -Dalbedo.harness={HARNESS} net.fabricmc.devlaunchinjector.Main"
PLAYER_GAME = r"javaw.exe -Xmx4G net.minecraft.client.main.Main --gameDir C:\Users\x\.minecraft"
CLIPPED = Clip(0, 0, 854, 480, 0, 0, 3840, 2160)
FREE = Clip(0, 0, 3840, 2160, 0, 0, 3840, 2160)


class FakeMachine(watchdog.Machine):
    def __init__(self, command_lines: list[list[str]], clip: Clip | None):
        self.command_lines = command_lines
        self.clip = clip
        self.freed = False
        self.slept = 0.0

    def wait(self, pid: int) -> None:
        pass

    def java_command_lines(self) -> list[str]:
        return self.command_lines.pop(0) if len(self.command_lines) > 1 else self.command_lines[0]

    def read_clip(self) -> Clip | None:
        return self.clip

    def free_clip(self) -> None:
        self.freed = True

    def sleep(self, seconds: float) -> None:
        self.slept += seconds


class WatchTest(unittest.TestCase):
    def setUp(self):
        self.log = temp_dir(self) / "watchdog.log"

    def watch(self, machine: FakeMachine) -> None:
        watchdog.watch(7, HARNESS, self.log, machine)

    def test_no_game_and_a_clip_frees_the_clip_with_one_log_line(self):
        machine = FakeMachine([[OWN_GAME], []], CLIPPED)
        self.watch(machine)
        self.assertTrue(machine.freed)
        lines = self.log.read_text(encoding="ascii").splitlines()
        self.assertEqual(1, len(lines))
        self.assertTrue(lines[0].endswith("freed the cursor clip (0,0,854,480) after the process 7 ended"), lines[0])

    def test_a_game_that_still_runs_keeps_its_clip(self):
        for game in (OWN_GAME, PLAYER_GAME):
            machine = FakeMachine([[game]], CLIPPED)
            self.watch(machine)
            self.assertFalse(machine.freed, game)
        self.assertFalse(self.log.exists())

    def test_the_shutdown_wait_ends_after_its_limit(self):
        machine = FakeMachine([[OWN_GAME]], CLIPPED)
        self.watch(machine)
        self.assertEqual(watchdog.SHUTDOWN_SECONDS + watchdog.SETTLE_SECONDS, machine.slept)

    def test_a_clip_of_the_whole_screen_or_no_clip_stays(self):
        for clip in (FREE, None):
            machine = FakeMachine([[]], clip)
            self.watch(machine)
            self.assertFalse(machine.freed)
