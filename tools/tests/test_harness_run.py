import unittest
from pathlib import Path

from tools.harness import run


class ParseTest(unittest.TestCase):
    def test_one_suite_keeps_the_output_folder(self):
        targets, runs = run.parse(["fabric-26.3", "forge-1.8.9", "--suite", "edges", "--out", "build/x"])
        self.assertEqual(["fabric-26.3", "forge-1.8.9"], [target.name for target in targets])
        self.assertEqual([("edges", Path("build/x"))], [(options.suite, options.out) for options in runs])

    def test_each_suite_gets_a_run_and_a_folder_of_its_own(self):
        _, runs = run.parse(["fabric-26.3", "--suite", "shots", "edges", "--out", "build/x"])
        self.assertEqual([("shots", Path("build/x/shots")), ("edges", Path("build/x/edges"))], [(options.suite, options.out) for options in runs])


if __name__ == "__main__":
    unittest.main()
