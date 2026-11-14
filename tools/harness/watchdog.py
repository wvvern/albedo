"""Safety net of a hidden game: waits until it ends, then frees the cursor clip that it can leave behind.

A game that grabs the mouse clips the cursor of the whole window station, so a game that is killed can leave the cursor of the player
in a small region. This process frees the clip only when it is smaller than the virtual screen and no harness game and no game of a
player runs. It never frees a clip while a game runs.
"""

import subprocess
import sys
import time
from datetime import datetime
from pathlib import Path

from tools import project
from tools.harness import cursor, protocol
from tools.harness.cursor import Clip

SHUTDOWN_SECONDS = 30
SETTLE_SECONDS = 2


class Machine:
    """The system calls of the watch. psutil loads only at a call, so that the tests run without it."""

    def wait(self, pid: int) -> None:
        from tools.harness import processes

        processes.wait(pid)

    def java_command_lines(self) -> list[str]:
        from tools.harness import processes

        return processes.java_command_lines()

    def read_clip(self) -> Clip | None:
        return cursor.read()

    def free_clip(self) -> None:
        cursor.free()

    def sleep(self, seconds: float) -> None:
        time.sleep(seconds)


def watch(pid: int, harness: Path, log: Path, machine: Machine | None = None) -> None:
    machine = machine or Machine()
    machine.wait(pid)
    # The process of the run ends first, and the game can still be shutting down for a few seconds.
    for _ in range(SHUTDOWN_SECONDS):
        if not any(protocol.runs_game_of(line, harness) for line in machine.java_command_lines()):
            break
        machine.sleep(1)
    machine.sleep(SETTLE_SECONDS)
    if protocol.game_runs(machine.java_command_lines()):
        return
    clip = machine.read_clip()
    if clip is None or not clip.clipped:
        return
    machine.free_clip()
    with log.open("a", encoding="ascii") as file:
        file.write(
            f"{datetime.now():%H:%M:%S} freed the cursor clip ({clip.left},{clip.top},{clip.right},{clip.bottom}) after the process {pid} ended\n"
        )


def start(pid: int, harness: Path, log: Path) -> None:
    """Starts the watch as a process of its own, which goes on when the command that started the game ends."""
    subprocess.Popen(
        [sys.executable, "-m", "tools.harness.watchdog", str(pid), str(harness), str(log)],
        cwd=project.ROOT,
        stdin=subprocess.DEVNULL,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        creationflags=subprocess.CREATE_NO_WINDOW | subprocess.CREATE_NEW_PROCESS_GROUP,
    )


if __name__ == "__main__":
    watch(int(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3]))
