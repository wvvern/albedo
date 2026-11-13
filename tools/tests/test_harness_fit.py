import unittest

from tools.harness import fit, protocol

WANTED = protocol.WINDOW_SIZE
# A screen that is narrower than the window of a run, and taller.
NARROW = (1216, 2581)
# A game window as Windows makes it: overlapped, with a caption, a frame to drag and its buttons, visible.
FRAMED = 0x14CF0000


class FitTest(unittest.TestCase):
    def test_a_window_that_fills_a_narrow_screen_is_held_by_it(self):
        self.assertTrue(fit.held_by_screen((1220, 900), (1236, 939), WANTED, NARROW))

    def test_a_small_window_that_the_game_has_not_sized_yet_is_left_alone(self):
        self.assertFalse(fit.held_by_screen((854, 480), (870, 519), WANTED, NARROW))
        self.assertFalse(fit.held_by_screen((0, 0), (0, 0), WANTED, NARROW))

    def test_a_window_of_the_size_of_the_run_stays_as_it_is(self):
        self.assertFalse(fit.held_by_screen(WANTED, (1616, 939), WANTED, (3840, 2160)))
        self.assertFalse(fit.held_by_screen(WANTED, WANTED, WANTED, NARROW), "a window that already has no frame")

    def test_a_window_on_a_low_screen_is_held_by_its_height(self):
        self.assertTrue(fit.held_by_screen((1600, 729), (1616, 768), WANTED, (1920, 768)))

    def test_the_frame_goes_and_the_other_styles_stay(self):
        style = fit.without_frame(FRAMED)
        self.assertEqual(0, style & fit.FRAME)
        self.assertEqual(fit.WS_POPUP, style & fit.WS_POPUP)
        self.assertEqual(0x10000000, style & 0x10000000, "the window stays visible")
        self.assertEqual(style, fit.without_frame(style), "a second time changes nothing")


if __name__ == "__main__":
    unittest.main()
