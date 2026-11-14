import unittest
from pathlib import Path

from tools import gradle


class HiddenCommandLineTest(unittest.TestCase):
    def test_each_property_that_is_set_is_a_flag(self):
        properties = {"harness": r"C:\Temp\h", "compat": "", "backend": "vulkan", "vulkanValidation": False, "quilt": True}
        line = gradle.hidden_command_line(Path("modern"), ":fabric:26.3:runClient", properties)
        wrapper = gradle.wrapper(Path("modern"))
        self.assertEqual(
            f'"{wrapper}" --no-daemon --configure-on-demand :fabric:26.3:runClient "-Pharness=C:\\Temp\\h" "-Pbackend=vulkan" -Pquilt', line
        )
