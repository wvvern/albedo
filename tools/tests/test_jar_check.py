import io
import json
import unittest
import zipfile
from pathlib import Path

from tools.jar_check import Jar, Subject, examine, examine_file
from tools.project import Target
from tools.tests.fixtures import FABRIC_1_18_2 as FABRIC
from tools.tests.fixtures import FABRIC_26_3, FORGE_1_20_1, temp_dir
from tools.tests.fixtures import FORGE_1_18_2 as FORGE
from tools.tests.fixtures import LEGACY_1_8_9 as LEGACY
from tools.tests.fixtures import NEOFORGE_1_18_2 as NEOFORGE

NODE = {
    "minecraft_range_fabric": ">=1.18.2 <1.19",
    "minecraft_range_forge": "[1.18.2,1.19)",
    "forge_range": "[40,)",
    "minecraft_range_neoforge": "[1.18.2,1.19)",
    "neoforge_range": "[20,)",
}
BUILD = {"fabric_loader_min": "0.15.0"}
MODS_TOML = """
[[dependencies.albedo]]
modId = "{loader}"
versionRange = "{loader_range}"

[[dependencies.albedo]]
modId = "minecraft"
versionRange = "[1.18.2,1.19)"
"""


def class_file(java: int) -> bytes:
    return b"\xca\xfe\xba\xbe\x00\x00" + (java + 44).to_bytes(2, "big")


def mixins(names: list[str], level: str) -> str:
    return json.dumps({"compatibilityLevel": level, "client": names})


def write_jar(file: Path | io.BytesIO, entries: dict[str, str | bytes]) -> None:
    with zipfile.ZipFile(file, "w") as jar:
        for name, content in entries.items():
            jar.writestr(name, content)


def subject(target: Target, entries: dict[str, str | bytes]) -> Subject:
    data = io.BytesIO()
    write_jar(data, entries)
    return Subject(Jar(data), target, NODE, BUILD)


def fabric_jar(changes: dict[str, str | bytes] | None = None) -> dict[str, str | bytes]:
    entries = {
        "albedo.mixins.json": mixins(["AMixin", "BAccessor"], "JAVA_17"),
        "dev/albedo/modern/mixin/AMixin.class": class_file(17),
        "dev/albedo/modern/mixin/AMixin$1.class": class_file(17),
        "dev/albedo/modern/mixin/BAccessor.class": class_file(17),
        "fabric.mod.json": json.dumps({"depends": {"minecraft": ">=1.18.2 <1.19", "fabricloader": ">=0.15.0"}}),
    }
    return entries | (changes or {})


class ModernJarTest(unittest.TestCase):
    def test_a_good_fabric_jar_passes(self):
        self.assertEqual([], examine(subject(FABRIC, fabric_jar())))

    def test_a_mixin_class_that_the_config_does_not_list_fails(self):
        entries = fabric_jar({"dev/albedo/modern/mixin/CMixin.class": class_file(17)})
        self.assertEqual(["the client list of albedo.mixins.json is not the list of the mixin classes in the jar"], examine(subject(FABRIC, entries)))

    def test_the_wrong_mixin_level_fails(self):
        entries = fabric_jar({"albedo.mixins.json": mixins(["AMixin", "BAccessor"], "JAVA_21")})
        self.assertEqual(["the compatibilityLevel of albedo.mixins.json is not JAVA_17"], examine(subject(FABRIC, entries)))

    def test_the_mixin_level_stops_at_21(self):
        entries = {name: class_file(25) if name.endswith(".class") else content for name, content in fabric_jar().items()}
        entries["albedo.mixins.json"] = mixins(["AMixin", "BAccessor"], "JAVA_21")
        self.assertEqual([], examine(subject(FABRIC_26_3, entries)))

    def test_a_class_of_the_wrong_java_version_fails_once(self):
        entries = fabric_jar({"dev/albedo/core/A.class": class_file(21), "dev/albedo/core/B.class": class_file(21)})
        self.assertEqual(["dev/albedo/core/A.class has the class version 65, not 61"], examine(subject(FABRIC, entries)))

    def test_the_wrong_minecraft_range_fails(self):
        entries = fabric_jar({"fabric.mod.json": json.dumps({"depends": {"minecraft": ">=1.18", "fabricloader": ">=0.15.0"}})})
        self.assertEqual(["fabric.mod.json has the minecraft range >=1.18, not >=1.18.2 <1.19"], examine(subject(FABRIC, entries)))

    def test_a_new_forge_jar_must_not_have_the_forge_mixin_config(self):
        entries = self.forge_jar()
        self.assertEqual(["the jar has albedo.forge.mixins.json, which only a Forge jar below 1.19 needs"], examine(subject(FORGE_1_20_1, entries)))

    def test_the_mixin_of_the_dev_run_fails(self):
        entries = fabric_jar({"dev/albedo/forge/dev/TransformationMixin.class": class_file(17)})
        self.assertEqual(["the jar has the mixin of the dev run"], examine(subject(FABRIC, entries)))

    def forge_jar(self) -> dict[str, str | bytes]:
        entries = fabric_jar()
        del entries["fabric.mod.json"]
        entries["META-INF/mods.toml"] = MODS_TOML.format(loader="forge", loader_range="[40,)")
        entries["META-INF/MANIFEST.MF"] = "Manifest-Version: 1.0\r\nMixinConfigs: albedo.mixins.json,albedo.forge.mixi\r\n ns.json\r\n"
        entries["albedo.forge.mixins.json"] = mixins(["ForgeMixin"], "JAVA_17")
        entries["dev/albedo/forge/mixin/ForgeMixin.class"] = class_file(17)
        return entries

    def test_a_good_forge_jar_passes(self):
        self.assertEqual([], examine(subject(FORGE, self.forge_jar())))

    def test_an_old_forge_jar_needs_the_forge_mixin_config(self):
        entries = self.forge_jar()
        del entries["albedo.forge.mixins.json"]
        self.assertEqual(["the jar has no albedo.forge.mixins.json"], examine(subject(FORGE, entries)))

    def test_the_manifest_must_name_the_forge_mixin_config(self):
        entries = self.forge_jar() | {"META-INF/MANIFEST.MF": "MixinConfigs: albedo.mixins.json\r\n"}
        self.assertEqual(["the manifest does not name albedo.forge.mixins.json"], examine(subject(FORGE, entries)))

    def test_a_neoforge_jar_reads_either_metadata_file(self):
        toml = MODS_TOML.format(loader="neoforge", loader_range="[20,)")
        for name in ("META-INF/neoforge.mods.toml", "META-INF/mods.toml"):
            entries = fabric_jar({name: toml})
            del entries["fabric.mod.json"]
            self.assertEqual([], examine(subject(NEOFORGE, entries)))

    def test_the_wrong_loader_range_fails(self):
        entries = fabric_jar({"META-INF/neoforge.mods.toml": MODS_TOML.format(loader="neoforge", loader_range="[21,)")})
        self.assertEqual(["META-INF/neoforge.mods.toml has the neoforge range [21,), not [20,)"], examine(subject(NEOFORGE, entries)))


class LegacyJarTest(unittest.TestCase):
    def jar(self, changes: dict[str, str | bytes] | None = None) -> dict[str, str | bytes]:
        entries = {
            "mcmod.info": json.dumps([{"mcversion": "1.8.9", "version": "1.0.0+1.8.9"}]),
            "META-INF/MANIFEST.MF": "TweakClass: org.spongepowered.asm.launch.MixinTweaker\r\nMixinConfigs: mixins.albedo.json\r\n",
            "mixins.albedo.json": mixins(["AMixin"], "JAVA_8"),
            "mixins.albedo.refmap.json": "{}",
            "dev/albedo/legacy/mixin/AMixin.class": class_file(8),
        }
        return entries | (changes or {})

    def test_a_good_legacy_jar_passes(self):
        self.assertEqual([], examine(subject(LEGACY, self.jar())))

    def test_the_metadata_of_a_different_version_fails(self):
        entries = self.jar({"mcmod.info": json.dumps([{"mcversion": "1.12.2", "version": "1.0.0+1.12.2"}])})
        self.assertEqual(
            ["mcmod.info has the mcversion 1.12.2, not 1.8.9", "the version 1.0.0+1.12.2 of mcmod.info does not end with +1.8.9"],
            examine(subject(LEGACY, entries)),
        )

    def test_a_missing_refmap_and_a_new_class_version_fail(self):
        entries = self.jar({"dev/albedo/core/A.class": class_file(17)})
        del entries["mixins.albedo.refmap.json"]
        self.assertEqual(
            ["the jar has no mixins.albedo.refmap.json", "dev/albedo/core/A.class has the class version 61, not 52"],
            examine(subject(LEGACY, entries)),
        )


class JarFileTest(unittest.TestCase):
    """Jar files with the name of a target of this repository."""

    def jar_file(self) -> Path:
        return temp_dir(self) / LEGACY.jar_name("1.0.0")

    def problems(self, entries: dict[str, str | bytes]) -> list[str]:
        file = self.jar_file()
        write_jar(file, entries)
        return examine_file(file)

    def test_a_jar_with_no_metadata_file_is_a_problem_of_the_jar(self):
        problems = self.problems({"mixins.albedo.json": mixins([], "JAVA_8")})
        self.assertEqual(1, len(problems))
        self.assertTrue(problems[0].startswith("a check cannot read the jar: "), problems[0])
        self.assertIn("mcmod.info", problems[0])

    def test_a_metadata_file_that_is_not_json_is_a_problem_of_the_jar(self):
        problems = self.problems({"mcmod.info": "[{"})
        self.assertEqual(1, len(problems))
        self.assertTrue(problems[0].startswith("a check cannot read the jar: "), problems[0])

    def test_a_file_that_is_no_jar_is_a_problem(self):
        file = self.jar_file()
        file.write_bytes(b"not a zip")
        self.assertEqual(["cannot read the jar"], examine_file(file))
