import importlib.util
import unittest

PILLOW = importlib.util.find_spec("PIL") is not None


@unittest.skipUnless(PILLOW, "needs Pillow")
class ImagesTest(unittest.TestCase):
    def setUp(self):
        from PIL import Image

        from tools import images

        self.images = images
        self.new = Image.new

    def test_strips_come_from_the_same_place_with_a_line_between_them(self):
        red, blue = self.new("RGB", (16, 2), (255, 0, 0)), self.new("RGB", (16, 2), (0, 0, 255))
        strips = self.images.split([red, blue])
        self.assertEqual([(255, 0, 0), (0, 0, 0), (0, 0, 0), (0, 0, 255)], [strips.getpixel((x, 0)) for x in (5, 6, 9, 10)])

    def test_a_rim_adds_the_same_border_on_each_side(self):
        rimmed = self.images.rim(self.new("RGB", (40, 30), (255, 255, 255)))
        border = self.images.RIM_WIDTH + 2 * self.images.SHADOW_BLUR
        self.assertEqual((40 + 2 * border, 30 + 2 * border), rimmed.size)
        self.assertEqual((255, 255, 255, 255), rimmed.getpixel((border + 20, border + 15)))
