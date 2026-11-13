import unittest

from tools.harness import fit, protocol

WANTED = protocol.WINDOW_SIZE
# A game window as Windows makes it: overlapped, with a caption, a frame to drag and its buttons, visible.
FRAMED = 0x14CF0000


class FitTest(unittest.TestCase):
    def test_a_window_that_a_narrow_screen_made_smaller_is_too_small(self):
        self.assertTrue(fit.too_small((1220, 900), WANTED))
        self.assertTrue(fit.too_small((1600, 700), WANTED))

    def test_a_window_of_the_size_of_the_run_or_larger_stays_as_it_is(self):
        self.assertFalse(fit.too_small(WANTED, WANTED))
        self.assertFalse(fit.too_small((1920, 1080), WANTED))

    def test_a_window_with_no_pixels_yet_is_not_fitted(self):
        self.assertFalse(fit.too_small((0, 0), WANTED))

    def test_the_frame_goes_and_the_other_styles_stay(self):
        style = fit.without_frame(FRAMED)
        self.assertEqual(0, style & fit.FRAME)
        self.assertEqual(fit.WS_POPUP, style & fit.WS_POPUP)
        self.assertEqual(0x10000000, style & 0x10000000, "the window stays visible")
        self.assertEqual(style, fit.without_frame(style), "a second time changes nothing")


if __name__ == "__main__":
    unittest.main()
