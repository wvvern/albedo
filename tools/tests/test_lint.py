import tempfile
import unittest
from pathlib import Path

from tools import lint
from tools.lint import Tree


def tree_of(files: dict[str, bytes]) -> Tree:
    root = Path(tempfile.mkdtemp())
    for name, data in files.items():
        (root / name).parent.mkdir(parents=True, exist_ok=True)
        (root / name).write_bytes(data)
    return Tree(root, tuple(files))


def messages(problems) -> list[str]:
    return [str(problem) for problem in problems]


class TextHygieneTest(unittest.TestCase):
    def test_a_clean_file_has_no_problem(self):
        self.assertEqual([], messages(lint.text_hygiene(tree_of({"a.txt": b"one\ntwo\n"}))))

    def test_each_rule_names_its_line(self):
        tree = tree_of({"a.txt": b"caf\xc3\xa9\n\tx\ny \nz"})
        self.assertEqual(
            [
                "a.txt:1: error: use only ASCII characters",
                "a.txt:2: error: use spaces, not tabs",
                "a.txt:3: error: remove the spaces at the end of the line",
                "a.txt: error: add a newline at the end of the file",
            ],
            messages(lint.text_hygiene(tree)),
        )

    def test_only_a_bat_file_keeps_crlf(self):
        tree = tree_of({"run.bat": b"echo\r\n", "run.sh": b"echo\r\n"})
        self.assertEqual(["run.sh:1: error: remove the spaces at the end of the line"], messages(lint.text_hygiene(tree)))

    def test_binary_and_empty_files_are_skipped(self):
        self.assertEqual([], messages(lint.text_hygiene(tree_of({"a.png": b"\x89PNG\0\t \xff", "empty": b""}))))


class HandlerNameTest(unittest.TestCase):
    def numbers(self, source: str) -> list[int]:
        return list(lint.unprefixed_handlers(source.splitlines()))

    def test_a_prefixed_handler_passes(self):
        self.assertEqual([], self.numbers('@Inject(method = "tick", at = @At("HEAD"))\nprivate void albedo$tick(CallbackInfo ci) {'))

    def test_an_unprefixed_handler_is_found_on_its_header_line(self):
        self.assertEqual([2], self.numbers('@Inject(method = "tick", at = @At("HEAD"))\nprivate void tick(CallbackInfo ci) {'))

    def test_the_annotation_can_take_more_lines_and_other_annotations_can_follow(self):
        source = '@Redirect(\n    method = "render(I)V",\n    at = @At(value = "INVOKE", target = "Lx;y()V")\n)\n@Unique\n\nprivate int render() {'
        self.assertEqual([7], self.numbers(source))

    def test_a_paren_in_a_string_does_not_count(self):
        self.assertEqual([2], self.numbers('@ModifyArg(method = "a(", at = @At("HEAD"))\nprivate int a(int value) {'))

    def test_an_inactive_stonecutter_branch_is_checked(self):
        source = '//?} else {\n/*@Inject(method = "a", at = @At("HEAD"))\n//? if >=1.20 {\nprivate void a(CallbackInfo ci) {\n*///?}'
        self.assertEqual([4], self.numbers(source))

    def test_an_annotation_with_its_package_is_an_injector(self):
        self.assertEqual([2], self.numbers('@org.spongepowered.asm.mixin.injection.ModifyVariable(method = "a")\nprivate int a(int value) {'))

    def test_a_shadow_member_keeps_its_name(self):
        self.assertEqual([], self.numbers("@Shadow\nprivate int width;\n@Accessor\nint getHeight();"))

    def test_only_mixin_files_are_checked(self):
        source = b'@Inject(method = "a")\nvoid a() {\n'
        tree = tree_of({"src/AMixin.java": source, "src/Hooks.java": source})
        self.assertEqual(["src/AMixin.java:2: error: a handler name must start with albedo$"], messages(lint.mixin_handler_names(tree)))


class ConditionVersionTest(unittest.TestCase):
    def test_a_version_outside_the_boundary_table_is_found_once(self):
        tree = tree_of(
            {
                "docs/ARCHITECTURE.md": b"| `1.21.5` | the frame graph |\n",
                "modern/A.java": b"//? if >=1.21.5 {\n//?} elif >=1.20.2 {\n//? if <1.20.2 {\n",
                "legacy/B.java": b"//? if >=9.9 {\n",
            }
        )
        problems = messages(lint.condition_versions(tree))
        self.assertEqual(1, len(problems))
        self.assertTrue(problems[0].startswith("modern/A.java:2: error: the version 1.20.2 "), problems[0])


NODE_FILES = {
    "modern/settings.gradle": b"def nodes = ['1.20.4', '26.1']\ndef forgeNodes = ['1.20.4']\ndef neoforgeNodes = ['26.1']\n",
    "modern/versions/1.20.4/gradle.properties": b"minecraft_version=1.20.4\n",
    "modern/versions/26.1/gradle.properties": b"minecraft_version=26.1.2\n",
    "modern/forge/versions/1.20.4/gradle.properties": b"loom.platform=forge\n",
    "modern/neoforge/versions/26.1/gradle.properties": b"loom.platform=neoforge\n",
}


class NodeFilesTest(unittest.TestCase):
    def problems(self, changes: dict[str, bytes]) -> list[str]:
        return messages(lint.node_files(tree_of(NODE_FILES | changes)))

    def test_matching_folders_pass(self):
        self.assertEqual([], self.problems({}))

    def test_a_folder_that_no_list_has_is_found(self):
        problems = self.problems({"modern/versions/26.2/gradle.properties": b"minecraft_version=26.2\n"})
        self.assertEqual(1, len(problems))
        self.assertIn("the node 26.2 must be in the list nodes", problems[0])

    def test_a_loader_node_needs_its_platform_file(self):
        self.assertEqual(
            ["modern/forge/versions/1.20.4/gradle.properties: error: a forge node needs this file with loom.platform=forge"],
            self.problems({"modern/forge/versions/1.20.4/gradle.properties": b"loom.platform=neoforge\n"}),
        )


class ModrinthVersionTest(unittest.TestCase):
    def problems(self, properties: str) -> list[str]:
        file = "modern/versions/1.21.1/gradle.properties"
        return messages(lint.modrinth_versions(tree_of({file: f"minecraft_range_fabric=>=1.21 <1.21.2\n{properties}".encode()})))

    def test_versions_in_the_fabric_range_pass(self):
        self.assertEqual([], self.problems(""))
        self.assertEqual([], self.problems("modrinth_versions=1.21 1.21.1\n"))

    def test_the_upper_bound_and_older_versions_are_outside(self):
        problems = self.problems("modrinth_versions=1.20.6 1.21\nmodrinth_versions_fabric=1.21.2\n")
        self.assertEqual(2, len(problems))
        self.assertIn("the version 1.20.6 of modrinth_versions is not in minecraft_range_fabric", problems[0])
        self.assertIn("the version 1.21.2 of modrinth_versions_fabric", problems[1])


class ActiveVersionTest(unittest.TestCase):
    def problems(self, active: str) -> list[str]:
        files = {"modern/settings.gradle": b"        vcsVersion = '26.3'\n", "modern/stonecutter.gradle": f"stonecutter.active '{active}'\n".encode()}
        return messages(lint.stonecutter_active_version(tree_of(files)))

    def test_the_active_version_is_the_vcs_version(self):
        self.assertEqual([], self.problems("26.3"))
        self.assertEqual(1, len(self.problems("26.2")))


if __name__ == "__main__":
    unittest.main()
