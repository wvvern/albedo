import unittest

from tools.harness import script
from tools.harness.script import compose
from tools.tests.fixtures import FABRIC_1_16_5, FABRIC_1_20_1, FABRIC_26_3, FORGE_1_17_1, LEGACY_1_8_9, LEGACY_1_12_2

NEWEST = FABRIC_26_3
BEFORE_NEW_GRASS = FABRIC_1_20_1
BEFORE_NEW_GROUND = FORGE_1_17_1


class ComposeTest(unittest.TestCase):
    def test_the_newest_version_runs_the_lines_as_they_are(self):
        lines = ["# a comment", "run setblock 8 -60 -4 short_grass", "ui realclick Copy", "wait 5"]
        self.assertEqual(lines, compose(lines, NEWEST))

    def test_the_hook_counters_are_logged_before_each_quit(self):
        self.assertEqual(["wait 5", "hooks", "quit"], compose(["wait 5", "quit"], NEWEST))
        self.assertEqual(["hooks", "quit "], compose(["quit "], LEGACY_1_8_9))

    def test_a_line_of_one_build_runs_only_there(self):
        lines = ["modern: key F6", "legacy: key F9", "key A"]
        self.assertEqual(["key F6", "key A"], compose(lines, NEWEST))
        self.assertEqual(["key F9", "key A"], compose(lines, LEGACY_1_8_9))

    def test_a_legacy_target_clicks_with_no_real_click(self):
        self.assertEqual(["ui click + New group"], compose(["ui realclick + New group"], LEGACY_1_12_2))

    def test_short_grass_has_its_old_name_before_1_20_3(self):
        self.assertEqual(["run setblock 8 -60 -4 grass"], compose(["run setblock 8 -60 -4 short_grass"], BEFORE_NEW_GRASS))
        self.assertEqual(["run setblock 0 3 0 short_grass_block"], compose(["run setblock 0 3 0 short_grass_block"], BEFORE_NEW_GRASS))

    def test_the_scene_moves_up_before_1_18(self):
        self.assertEqual(
            [
                "run fill -8 3 -8 8 4 8 stone",
                "run setblock 7 4.5 -6 grass",
                "run tp @p 0.5 4 0.5 180 0",
                "run summon cow 2 4 -3 {NoAI:1b}",
                "run setblock 0 64 0 stone",
                "run time set 6000",
            ],
            compose(
                [
                    "run fill -8 -61 -8 8 -60 8 stone",
                    "run setblock 7 -59.5 -6 short_grass",
                    "run tp @p 0.5 -60 0.5 180 0",
                    "run summon cow 2 -60 -3 {NoAI:1b}",
                    "run setblock 0 64 0 stone",
                    "run time set 6000",
                ],
                BEFORE_NEW_GROUND,
            ),
        )

    def test_a_relative_tp_starts_from_the_player_before_1_18(self):
        self.assertEqual(["run execute as @p at @p run tp @p ~ ~ ~ 90 0"], compose(["run tp @p ~ ~ ~ 90 0"], BEFORE_NEW_GROUND))
        self.assertEqual(["run tp @p ~ ~ ~ 90 0"], compose(["run tp @p ~ ~ ~ 90 0"], NEWEST))

    def test_item_replace_is_replaceitem_before_1_17(self):
        line = "run item replace entity @p armor.chest with minecraft:diamond_chestplate"
        self.assertEqual(["run replaceitem entity @p armor.chest minecraft:diamond_chestplate"], compose([line], FABRIC_1_16_5))
        self.assertEqual([line], compose([line], BEFORE_NEW_GROUND))

    def test_1_12_names_an_entity_by_its_id_and_has_no_achievements(self):
        lines = ["run achievement give * @p", "run summon Sheep 2 4 -3", "run kill @e[type=!Player]"]
        self.assertEqual(["run summon minecraft:sheep 2 4 -3", "run kill @e[type=!player]"], compose(lines, LEGACY_1_12_2))
        self.assertEqual(lines, compose(lines, LEGACY_1_8_9))

    def test_1_12_names_an_entity_of_two_words_with_an_underscore(self):
        lines = ["run summon ArmorStand 2 4 -3 {Rotation:[180f,0f]}", "run replaceitem entity @e[type=ArmorStand] slot.armor.chest x"]
        self.assertEqual(
            ["run summon minecraft:armor_stand 2 4 -3 {Rotation:[180f,0f]}", "run replaceitem entity @e[type=armor_stand] slot.armor.chest x"],
            compose(lines, LEGACY_1_12_2),
        )

    def test_1_8_has_no_elytra(self):
        lines = ["run replaceitem entity @p slot.armor.chest minecraft:elytra", "keepframe", "framechanged elytra"]
        self.assertEqual(["keepframe"], compose(lines, LEGACY_1_8_9))
        self.assertEqual(lines, compose(lines, LEGACY_1_12_2))


class SuiteFilesTest(unittest.TestCase):
    def test_each_target_has_a_scene(self):
        for target in (NEWEST, LEGACY_1_8_9):
            self.assertTrue(script.suite_file(script.scene_of(target)).is_file())

    def test_a_suite_has_its_name_with_or_without_the_file_type(self):
        self.assertEqual(script.suite_file("shots"), script.suite_file("shots.txt"))

    def test_an_unknown_suite_is_an_error(self):
        from tools.report import ToolError

        with self.assertRaises(ToolError):
            script.suite_file("no-such-suite")

    def test_each_suite_composes_for_each_build(self):
        for suite in script.SUITES.glob("*.txt"):
            for target in (NEWEST, BEFORE_NEW_GROUND, LEGACY_1_12_2):
                commands = compose(script.read_suites([suite.stem]), target)
                self.assertFalse([line for line in commands if script.BUILD_PREFIX.match(line)], suite.name)
