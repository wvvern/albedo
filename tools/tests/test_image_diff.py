import importlib.util
import unittest

PILLOW = importlib.util.find_spec("PIL") is not None


@unittest.skipUnless(PILLOW, "needs Pillow")
class ImageDiffTest(unittest.TestCase):
    def setUp(self):
        from PIL import Image

        from tools import image_diff

        self.image_diff = image_diff
        self.new = Image.new

    def test_the_same_image_has_no_difference(self):
        image = self.new("RGB", (4, 2), (10, 20, 30))
        self.assertEqual((0.0, 0.0, 0), self.image_diff.difference(image, image, 16))

    def test_the_difference_of_one_pixel(self):
        before = self.new("RGBA", (4, 1), (0, 0, 0, 255))
        after = before.copy()
        after.putpixel((0, 0), (30, 0, 6, 255))
        after.putpixel((1, 0), (0, 16, 0, 255))
        mean, percent, largest = self.image_diff.difference(before, after, 16)
        self.assertAlmostEqual((30 + 6 + 16) / 12, mean)
        self.assertEqual(25.0, percent)
        self.assertEqual(30, largest)
