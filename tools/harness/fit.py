"""Gives the window of a hidden game the size of the run on a screen that is smaller than that size.

Windows keeps a window with a frame inside the size of the screen, so on a narrow screen the game gets a smaller window and its
screenshots do not have the size that a run must have. A window with no frame has no such limit. This process runs on the hidden
desktop with the game, takes the frame off a game window that is too small and sets its size, and ends with the process of the run.

The modules that need psutil or Windows load only where the watch runs, so that the tests of the sizes and the styles run anywhere.
"""

import sys
import time
from pathlib import Path

from tools import project
from tools.harness import protocol
from tools.harness.session import Launch

Size = tuple[int, int]

GWL_STYLE = -16
WS_POPUP = 0x80000000
# The caption with its border, the frame that the mouse can drag, and the buttons of the caption.
FRAME = 0x00C00000 | 0x00040000 | 0x00080000 | 0x00020000 | 0x00010000
SWP_NOZORDER = 0x0004
SWP_NOACTIVATE = 0x0010
SWP_FRAMECHANGED = 0x0020
POLL_SECONDS = 0.5


def too_small(client: Size, wanted: Size) -> bool:
    """Whether the screen made a window smaller than the run wants, which a window with no pixels yet is not."""
    return client != (0, 0) and (client[0] < wanted[0] or client[1] < wanted[1])


def without_frame(style: int) -> int:
    return (style & ~FRAME) | WS_POPUP


def game_pids(harness: Path) -> set[int]:
    from tools.harness import processes

    return {pid for pid, line in processes.java_processes() if protocol.harness_game(line) and protocol.names_harness(line, harness)}


def windows_of(pids: set[int]) -> list[int]:
    """The visible top-level windows of some processes on the desktop of this thread."""
    import ctypes
    from ctypes import wintypes

    user32 = ctypes.WinDLL("user32", use_last_error=True)
    found: list[int] = []

    @ctypes.WINFUNCTYPE(wintypes.BOOL, wintypes.HWND, wintypes.LPARAM)
    def visit(window: int, _: int) -> bool:
        pid = wintypes.DWORD()
        user32.GetWindowThreadProcessId(wintypes.HWND(window), ctypes.byref(pid))
        if pid.value in pids and user32.IsWindowVisible(wintypes.HWND(window)):
            found.append(window)
        return True

    user32.EnumWindows(visit, 0)
    return found


def client_size(window: int) -> Size:
    import ctypes
    from ctypes import wintypes

    rect = wintypes.RECT()
    ctypes.WinDLL("user32", use_last_error=True).GetClientRect(wintypes.HWND(window), ctypes.byref(rect))
    return rect.right - rect.left, rect.bottom - rect.top


def fit(window: int, wanted: Size) -> None:
    import ctypes
    from ctypes import wintypes

    user32 = ctypes.WinDLL("user32", use_last_error=True)
    user32.GetWindowLongW.restype = ctypes.c_long
    style = user32.GetWindowLongW(wintypes.HWND(window), GWL_STYLE) & 0xFFFFFFFF
    user32.SetWindowLongW(wintypes.HWND(window), GWL_STYLE, ctypes.c_long(without_frame(style) - (1 << 32)))
    # A window with no frame is as large as its inside.
    user32.SetWindowPos(wintypes.HWND(window), None, 0, 0, wanted[0], wanted[1], SWP_NOZORDER | SWP_NOACTIVATE | SWP_FRAMECHANGED)


def watch(pid: int, harness: Path, log: Path) -> None:
    from tools.harness import processes

    while processes.alive(pid):
        for window in windows_of(game_pids(harness)):
            size = client_size(window)
            if too_small(size, protocol.WINDOW_SIZE):
                fit(window, protocol.WINDOW_SIZE)
                with log.open("a", encoding="ascii") as file:
                    file.write(f"a window of {size[0]} x {size[1]} got no frame and now has {' x '.join(map(str, client_size(window)))}\n")
        time.sleep(POLL_SECONDS)


def start(pid: int, harness: Path, log: Path) -> None:
    """Starts the watch on the hidden desktop, where the windows of the game are."""
    from tools.harness import desktop

    desktop.start_hidden(Launch(f'"{sys.executable}" -m tools.harness.fit {pid} "{harness}" "{log}"', project.ROOT, log.with_name(f"{log.name}.out")))


if __name__ == "__main__":
    watch(int(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3]))
