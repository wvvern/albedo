import unittest

from tools.jar_compare import differences, explained_patterns, jar_key

PATTERNS = ["fabric.mod.json", "dev/albedo/core/sky/Sky*.class"]


class JarCompareTest(unittest.TestCase):
    def test_jars_of_two_releases_have_the_same_key(self):
        self.assertEqual(jar_key("albedo-fabric-0.5.1+26.1.2.jar"), jar_key("albedo-fabric-0.6.0+26.1.2.jar"))
        self.assertNotEqual(jar_key("albedo-fabric-0.5.1+26.2.jar"), jar_key("albedo-fabric-0.5.1+26.3.jar"))

    def test_the_list_gives_the_first_word_of_each_line(self):
        text = "# The entries\n\nfabric.mod.json The loader version.\ndev/albedo/A*.class A new class.\n"
        self.assertEqual(["fabric.mod.json", "dev/albedo/A*.class"], explained_patterns(text))

    def test_each_entry_that_differs_has_its_state(self):
        old = {"same": "1", "changed": "1", "removed": "1"}
        new = {"same": "1", "changed": "2", "added": "1"}
        self.assertEqual(
            ["  NOT EXPLAINED added added", "  NOT EXPLAINED changed changed", "  NOT EXPLAINED removed removed"],
            [str(difference) for difference in differences(old, new, PATTERNS)],
        )

    def test_a_wildcard_explains_an_entry(self):
        found = differences({"fabric.mod.json": "1"}, {"fabric.mod.json": "2", "dev/albedo/core/sky/SkyBox$1.class": "1"}, PATTERNS)
        self.assertEqual([True, True], [difference.explained for difference in found])
        self.assertFalse(differences({}, {"dev/albedo/core/Sky.class": "1"}, PATTERNS)[0].explained)


if __name__ == "__main__":
    unittest.main()
