import json
import unittest
import zipfile
from pathlib import Path

from tools.harness import optifine, prodrun
from tools.harness.optifine import OptiFineOptions
from tools.harness.prodrun import MINECRAFT, Installation
from tools.report import ToolError
from tools.tests.fixtures import temp_dir


def library(name: str, **fields: object) -> dict[str, object]:
    return {"name": name, **fields}


class InstallationTest(unittest.TestCase):
    def setUp(self):
        root = temp_dir(self)
        self.installation = Installation(root / "minecraft", root / "caches")

    def install(self, group: str, artifact: str, version: str) -> Path:
        jar = self.installation.minecraft / "libraries" / Path(*group.split(".")) / artifact / version / f"{artifact}-{version}.jar"
        jar.parent.mkdir(parents=True)
        jar.touch()
        return jar

    def cache(self, group: str, artifact: str, version: str) -> Path:
        folder = self.installation.gradle_caches / "modules-2" / "files-2.1" / group / artifact / version / "0123abcd"
        folder.mkdir(parents=True)
        jar = folder / f"{artifact}-{version}.jar"
        jar.touch()
        return jar

    def forge_jar(self, libraries: list[dict[str, object]]) -> Path:
        jar = self.install("net.minecraftforge", "forge", f"{MINECRAFT}-11.15.1.2318")
        with zipfile.ZipFile(jar, "w") as archive:
            archive.writestr("version.json", json.dumps({"libraries": libraries}))
        return jar

    def vanilla(self, libraries: list[dict[str, object]]) -> None:
        file = self.installation.minecraft / "versions" / MINECRAFT / f"{MINECRAFT}.json"
        file.parent.mkdir(parents=True)
        file.write_text(json.dumps({"libraries": libraries}), encoding="utf-8")

    def test_a_library_of_the_launcher_comes_before_the_gradle_cache(self):
        installed = self.install("org.ow2.asm", "asm", "5.0.3")
        self.cache("org.ow2.asm", "asm", "5.0.3")
        self.assertEqual(installed, self.installation.library("org.ow2.asm:asm:5.0.3"))

    def test_a_library_that_only_gradle_has_comes_from_its_cache(self):
        cached = self.cache("org.ow2.asm", "asm", "5.0.3")
        self.assertEqual(cached, self.installation.library("org.ow2.asm:asm:5.0.3"))

    def test_a_missing_library_is_an_error(self):
        with self.assertRaises(ToolError):
            self.installation.library("org.ow2.asm:asm:5.0.3")

    def test_the_newest_forge_jar_is_the_one(self):
        self.install("net.minecraftforge", "forge", f"{MINECRAFT}-11.15.1.1722")
        newest = self.install("net.minecraftforge", "forge", f"{MINECRAFT}-11.15.1.2318")
        self.assertEqual(newest, self.installation.forge_jar())

    def test_no_forge_jar_is_an_error(self):
        with self.assertRaises(ToolError):
            self.installation.forge_jar()

    def test_the_class_path_has_each_allowed_library_once_then_forge_and_the_game(self):
        launchwrapper = self.install("net.minecraft", "launchwrapper", "1.12")
        asm = self.cache("org.ow2.asm", "asm-all", "5.0.3")
        lwjgl = self.install("org.lwjgl.lwjgl", "lwjgl", "2.9.4")
        forge = self.forge_jar(
            [
                library("net.minecraftforge:forge:1.8.9-11.15.1.2318"),
                library("net.minecraft:launchwrapper:1.12"),
                library("org.ow2.asm:asm-all:5.0.3", rules=[{"action": "allow"}]),
                library("tv.twitch:twitch-platform:6.5", natives={"windows": "natives-windows"}),
                library("ca.weblite:java-objc-bridge:1.0.0", rules=[{"action": "allow", "os": {"name": "osx"}}]),
            ]
        )
        self.vanilla([library("net.minecraft:launchwrapper:1.11"), library("org.lwjgl.lwjgl:lwjgl:2.9.4")])
        game = self.installation.minecraft / "versions" / MINECRAFT / f"{MINECRAFT}.jar"
        expected = [launchwrapper, asm, lwjgl, forge, game]
        self.assertEqual(";".join(map(str, expected)), self.installation.class_path(forge))


class VariantTest(unittest.TestCase):
    def test_a_variant_without_quake_has_the_game_options_only(self):
        for variant in ("none", "default"):
            self.assertEqual((prodrun.GAME_OPTIONS, {}), prodrun.variant_options(variant))

    def test_the_quake_variants_set_graphics_and_smooth_lighting(self):
        cases = {"quake": ("false", "0"), "quake-ao": ("false", "2"), "quake-fancy": ("true", "0")}
        for variant, (fancy, ao) in cases.items():
            game, optifine_options = prodrun.variant_options(variant)
            self.assertEqual((fancy, ao), (game["fancyGraphics"], game["ao"]), variant)
            self.assertEqual(prodrun.QUAKE_OPTIFINE_OPTIONS, optifine_options)


class OptionsTest(unittest.TestCase):
    def test_no_arguments_give_the_default_options(self):
        self.assertEqual(OptiFineOptions(), optifine.options_of(optifine.parser().parse_args([])))

    def test_the_arguments_go_to_the_options(self):
        parsed = optifine.parser().parse_args(["--suite", "chroma", "--option", "useVbo:true", "--dump-classes", "--timeout", "60"])
        options = optifine.options_of(parsed)
        self.assertEqual(("chroma", ("useVbo:true",), True, 60), (options.suite, options.option_lines, options.dump_classes, options.timeout))
