import unittest

from tools.harness import protocol


class GameRunsTest(unittest.TestCase):
    def test_a_harness_game_or_a_game_of_a_player_runs(self):
        harness = r"java.exe -Dalbedo.harness=C:\Temp\albedo-harness\fabric-26.3 net.fabricmc.devlaunchinjector.Main"
        player = r"javaw.exe -Xmx4G net.minecraft.client.main.Main --gameDir C:\Users\x\.minecraft"
        gradle = r"java.exe -Xmx6G org.gradle.launcher.daemon.bootstrap.GradleDaemon 9.0"
        self.assertTrue(protocol.game_runs([gradle, harness]))
        self.assertTrue(protocol.game_runs([player]))
        self.assertFalse(protocol.game_runs([gradle]))
        self.assertTrue(protocol.harness_game(harness))
        self.assertFalse(protocol.harness_game(gradle))

    def test_a_command_line_names_a_harness_folder_only_by_its_whole_name(self):
        folder = r"C:\Temp\albedo-harness\fabric-1.21.1"
        self.assertTrue(protocol.names_harness(rf"java.exe -Dalbedo.harness={folder} Main", folder))
        self.assertTrue(protocol.names_harness(rf'java.exe "-Pharness={folder}"', folder))
        self.assertTrue(protocol.names_harness(rf"java.exe -Pharness={folder}", folder))
        self.assertFalse(protocol.names_harness(rf"java.exe -Dalbedo.harness={folder}1 Main", folder))
        self.assertFalse(protocol.names_harness(rf"java.exe -Dalbedo.harness={folder}0 Main", folder))
        self.assertFalse(protocol.names_harness(r"java.exe -Dalbedo.harness=C:\Temp\albedo-harness\forge-1.8.9", folder))
