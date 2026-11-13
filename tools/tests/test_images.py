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

    def test_the_same_image_has_no_difference(self):
        image = self.new("RGB", (4, 2), (10, 20, 30))
        self.assertEqual((0.0, 0.0, 0), self.images.difference(image, image, 16))

    def test_the_difference_of_one_pixel(self):
        before = self.new("RGBA", (4, 1), (0, 0, 0, 255))
        after = before.copy()
        after.putpixel((0, 0), (30, 0, 6, 255))
        after.putpixel((1, 0), (0, 16, 0, 255))
        mean, percent, largest = self.images.difference(before, after, 16)
        self.assertAlmostEqual((30 + 6 + 16) / 12, mean)
        self.assertEqual(25.0, percent)
        self.assertEqual(30, largest)

    def test_strips_come_from_the_same_place_with_a_line_between_them(self):
        red, blue = self.new("RGB", (16, 2), (255, 0, 0)), self.new("RGB", (16, 2), (0, 0, 255))
        strips = self.images.split([red, blue])
        self.assertEqual([(255, 0, 0), (0, 0, 0), (0, 0, 0), (0, 0, 255)], [strips.getpixel((x, 0)) for x in (5, 6, 9, 10)])

    def test_a_rim_adds_the_same_border_on_each_side(self):
        rimmed = self.images.rim(self.new("RGB", (40, 30), (255, 255, 255)))
        border = self.images.RIM_WIDTH + 2 * self.images.SHADOW_BLUR
        self.assertEqual((40 + 2 * border, 30 + 2 * border), rimmed.size)
        self.assertEqual((255, 255, 255, 255), rimmed.getpixel((border + 20, border + 15)))
