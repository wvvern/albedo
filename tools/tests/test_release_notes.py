import unittest

from tools.release_notes import section

CHANGELOG = """# Changelog

## 1.2.0

- feat: Add a sky

- fix: Keep the sheep red

## 1.1.0
- fix: Show water
"""


class SectionTest(unittest.TestCase):
    def test_gives_the_lines_of_one_version_with_no_empty_lines_around_them(self):
        self.assertEqual("- feat: Add a sky\n\n- fix: Keep the sheep red", section(CHANGELOG, "1.2.0"))

    def test_the_last_section_ends_with_the_file(self):
        self.assertEqual("- fix: Show water", section(CHANGELOG, "1.1.0"))

    def test_a_version_with_no_section_gives_nothing(self):
        self.assertEqual("", section(CHANGELOG, "1.2"))
        self.assertEqual("", section(CHANGELOG, "9.9.9"))


if __name__ == "__main__":
    unittest.main()
