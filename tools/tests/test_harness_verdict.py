import unittest
from dataclasses import replace

from tools.harness import protocol, verdict
from tools.harness.verdict import Evidence, Expectations, judge
from tools.tests.fixtures import FABRIC_26_3, LEGACY_1_8_9

SIZE = protocol.WINDOW_SIZE
GOOD = Evidence(
    harness_log=[
        "> cursorfree",
        "cursorfree: ok, clip 0,0,3840,2160 (3840 x 2160), screen 3840 x 2160",
        "> screenshot 01-vanilla",
        "> skycompare",
        "skycompare galaxy: same",
        "> hooks",
        "hooks: afterSolid=1807 tint=100562 fullbright=3 shadow=2 skyPass=900 mouseGrab=2",
        "> quit",
    ],
    game_log="[main] Albedo ready\n[main] Changed 812 textures\n",
    taken_commands=["wait 60", "cursorfree", "screenshot 01-vanilla", "skycompare", "frames 2 trail", "hooks", "quit"],
    image_sizes={"01-vanilla.png": SIZE, "trail-0.png": SIZE, "trail-1.png": SIZE},
    has_config=True,
)
EXPECTED = Expectations(hooks=verdict.MODERN_HOOKS)


def with_log(*lines: str) -> Evidence:
    return replace(GOOD, harness_log=[*GOOD.harness_log, *lines])


class JudgeTest(unittest.TestCase):
    def test_a_good_run_passes(self):
        self.assertEqual([], judge(GOOD, EXPECTED))

    def test_a_run_that_left_nothing_fails_for_each_missing_part(self):
        empty = Evidence(harness_log=[], game_log="", taken_commands=[], image_sizes={}, has_config=False)
        self.assertEqual(
            [
                "the run folder does not have config/albedo/config.json",
                "the game did not get to quit",
                "the game log does not have 'Albedo ready'",
                "the game log does not have 'Changed N textures'",
                "the harness log does not have a hooks line",
            ],
            judge(empty, EXPECTED),
        )

    def test_an_error_line_and_a_failed_compare_fail(self):
        failures = judge(with_log("! unknown command: skyy", "skycompare dusk: DIFFERENT mean 4.1"), EXPECTED)
        self.assertEqual(["harness error: ! unknown command: skyy", "harness compare: skycompare dusk: DIFFERENT mean 4.1"], failures)

    def test_a_hook_that_did_not_run_fails(self):
        log = [line.replace("tint=100562", "tint=0") for line in GOOD.harness_log]
        failures = judge(replace(GOOD, harness_log=log), EXPECTED)
        self.assertEqual(1, len(failures))
        self.assertTrue(failures[0].startswith("the hook tint did not run: hooks:"), failures[0])

    def test_a_renderer_mod_needs_its_own_tint_hook(self):
        failures = judge(GOOD, replace(EXPECTED, hooks=verdict.RENDERER_MOD_HOOKS, renderer_mod=True))
        self.assertEqual(1, len(failures))
        self.assertTrue(failures[0].startswith("the hook sodiumTint did not run"), failures[0])

    def test_a_death_message_fails_and_command_feedback_does_not(self):
        chat = (
            "[Client thread/INFO]: [CHAT] Player187 fell out of the world\n[CHAT] [Server: Teleported Player655 to 0.5, -60, 4.5]\n"
            "[CHAT] Dev has made the advancement [Acquire Hardware]\n[CHAT] Player484 has just earned the achievement [Taking Inventory]\n"
        )
        failures = judge(replace(GOOD, game_log=GOOD.game_log + chat), EXPECTED)
        self.assertEqual(["the player died: Player187 fell out of the world"], failures)

    def test_each_reload_reason_fails_once_with_no_renderer_mod(self):
        evidence = replace(GOOD, game_log=GOOD.game_log + "x cannot be sent to the GPU\ny cannot be sent to the GPU\n")
        self.assertEqual(["the game log has a reload reason: cannot be sent to the GPU"], judge(evidence, EXPECTED))
        hooks = [line.replace("tint=100562", "sodiumTint=5") for line in GOOD.harness_log]
        with_mod = replace(EXPECTED, hooks=verdict.RENDERER_MOD_HOOKS, renderer_mod=True)
        self.assertEqual([], judge(replace(evidence, harness_log=hooks), with_mod))

    def test_missing_and_small_images_fail(self):
        sizes = {"trail-0.png": SIZE, "trail-1.png": (854, 480)}
        self.assertEqual(
            ["no image for: screenshot 01-vanilla", "trail-1.png is 854 x 480, expected 1600 x 900"],
            judge(replace(GOOD, image_sizes=sizes), EXPECTED),
        )
        self.assertEqual(
            ["not all images for: frames 2 trail"],
            judge(replace(GOOD, image_sizes={"01-vanilla.png": SIZE, "trail-0.png": SIZE}), EXPECTED),
        )
        self.assertEqual(["not all images for: frames 2 trail"], judge(replace(GOOD, image_sizes={"01-vanilla.png": SIZE}), EXPECTED))
        self.assertEqual(
            ["01-vanilla.png is not a PNG, expected 1600 x 900"],
            judge(replace(GOOD, image_sizes=GOOD.image_sizes | {"01-vanilla.png": None}), EXPECTED),
        )

    def test_a_command_with_no_result_fails(self):
        commands = [*GOOD.taken_commands, "skycompare", "framechanged", "cursorfree"]
        self.assertEqual(
            [
                "2 skycompare commands gave only 1 results with 'same'",
                "1 framechanged commands gave only 0 results",
                "2 cursorfree commands gave only 1 results with 'ok'",
            ],
            judge(replace(GOOD, taken_commands=commands), EXPECTED),
        )

    def test_a_clipped_cursor_fails(self):
        log = [line.replace("cursorfree: ok", "! cursorfree: the cursor is clipped") for line in GOOD.harness_log]
        failures = judge(replace(GOOD, harness_log=log), EXPECTED)
        self.assertEqual(2, len(failures))
        self.assertTrue(failures[0].startswith("harness error: ! cursorfree: the cursor is clipped"))
        self.assertEqual("1 cursorfree commands gave only 0 results with 'ok'", failures[1])

    def test_a_forced_backend_must_be_the_one_that_runs(self):
        expected = replace(EXPECTED, backend="vulkan")
        self.assertEqual(["the game log does not have 'Using graphics backend vulkan'"], judge(GOOD, expected))
        self.assertEqual([], judge(replace(GOOD, game_log=GOOD.game_log + "Using graphics backend Vulkan\n"), expected))

    def test_quilt_must_show_its_version_and_the_release_jar(self):
        self.assertEqual(
            ["the game log does not have the Quilt Loader version line", "the mod table of the game log does not have the release jar of Albedo"],
            judge(GOOD, replace(EXPECTED, loader="quilt")),
        )
        game = GOOD.game_log + "Loading Minecraft 26.3 with Quilt Loader 0.30.2\n| albedo | <mods>\\albedo-fabric-0.6.2+26.3.jar |\n"
        self.assertEqual([], judge(replace(GOOD, game_log=game), replace(EXPECTED, loader="quilt")))
        self.assertEqual([], judge(replace(GOOD, game_log=GOOD.game_log + "with Quilt Loader 0.30.2\n"), replace(EXPECTED, loader="quilt-dev")))

    def test_a_shader_pack_that_did_not_load_fails(self):
        expected = replace(EXPECTED, shader_pack=True)
        self.assertEqual(["Iris did not load the shader pack"], judge(GOOD, expected))
        self.assertEqual([], judge(replace(GOOD, game_log=GOOD.game_log + "Using shaderpack: pack.zip\n"), expected))


class RequiredHooksTest(unittest.TestCase):
    def test_the_hooks_of_each_kind_of_run(self):
        self.assertEqual(verdict.MODERN_HOOKS, verdict.required_hooks(FABRIC_26_3, renderer_mod=False))
        self.assertEqual(verdict.RENDERER_MOD_HOOKS, verdict.required_hooks(FABRIC_26_3, renderer_mod=True))
        self.assertEqual(verdict.LEGACY_HOOKS, verdict.required_hooks(LEGACY_1_8_9, renderer_mod=True))
