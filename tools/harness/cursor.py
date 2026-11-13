"""The cursor clip of the window station: a game that grabs the mouse clips the cursor of all desktops, also the one of the player.

Run as a file, it is the clip probe of the harness command cursorfree, and prints the clip rectangle and the virtual screen as
"left top right bottom virtualLeft virtualTop virtualWidth virtualHeight", or "unreadable". It only reads the clip.

This file imports no other file of the tools, because the game runs it as a plain script.
"""

import ctypes
import sys
from ctypes import wintypes
from dataclasses import dataclass

SM_XVIRTUALSCREEN = 76
SM_YVIRTUALSCREEN = 77
SM_CXVIRTUALSCREEN = 78
SM_CYVIRTUALSCREEN = 79


@dataclass(frozen=True)
class Clip:
    left: int
    top: int
    right: int
    bottom: int
    screen_left: int
    screen_top: int
    screen_width: int
    screen_height: int

    @property
    def clipped(self) -> bool:
        """Whether the cursor cannot reach all of the virtual screen."""
        return self.right - self.left < self.screen_width or self.bottom - self.top < self.screen_height

    def __str__(self) -> str:
        return " ".join(
            str(number)
            for number in (self.left, self.top, self.right, self.bottom, self.screen_left, self.screen_top, self.screen_width, self.screen_height)
        )


def user32():
    library = ctypes.WinDLL("user32", use_last_error=True)
    # Without this, a scaled display gives the clip and the screen in different units.
    library.SetProcessDPIAware()
    return library


def read() -> Clip | None:
    """The clip now, or None when Windows does not tell it."""
    library = user32()
    rectangle = wintypes.RECT()
    if not library.GetClipCursor(ctypes.byref(rectangle)):
        return None
    screen = (library.GetSystemMetrics(index) for index in (SM_XVIRTUALSCREEN, SM_YVIRTUALSCREEN, SM_CXVIRTUALSCREEN, SM_CYVIRTUALSCREEN))
    return Clip(rectangle.left, rectangle.top, rectangle.right, rectangle.bottom, *screen)


def free() -> None:
    user32().ClipCursor(None)


if __name__ == "__main__":
    print(read() or "unreadable")
    sys.exit(0)
