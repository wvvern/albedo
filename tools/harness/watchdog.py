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
from tools.harness import cursor, processes, protocol

SHUTDOWN_SECONDS = 30


def watch(pid: int, harness: Path, log: Path) -> None:
    processes.wait(pid)
    # The process of the run ends first, and the game can still be shutting down for a few seconds.
    for _ in range(SHUTDOWN_SECONDS):
        if not any(protocol.harness_game(line, harness) for line in processes.java_command_lines()):
            break
        time.sleep(1)
    time.sleep(2)
    if protocol.game_runs(processes.java_command_lines()):
        return
    clip = cursor.read()
    if clip is None or not clip.clipped:
        return
    cursor.free()
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
