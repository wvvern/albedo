"""The Windows side of a run: the hidden desktop, the processes and the watch of the cursor."""

import os
import time
from pathlib import Path

from tools.harness import desktop, fit, processes, protocol, watchdog
from tools.harness.session import Launch
from tools.report import ToolError


class Windows:
    """The session.System of a real run."""

    def __init__(self):
        if os.name != "nt":
            raise ToolError("the game harness runs only on Windows, where it has a hidden desktop")
        # Made once here, before the targets start on threads of their own.
        desktop.hidden_desktop()

    def uses(self, harness: Path) -> bool:
        # The Gradle process of a dev run names the folder before its game does.
        return any(protocol.names_harness(line, harness) for line in processes.java_command_lines())

    def start(self, launch: Launch, harness: Path) -> int:
        pid = desktop.start_hidden(launch)
        watchdog.start(pid, harness, launch.log.with_name(f"{launch.log.name}.cursor"))
        fit.start(pid, harness, launch.log.with_name(f"{launch.log.name}.fit"))
        return pid

    def alive(self, pid: int) -> bool:
        return processes.alive(pid)

    def stop(self, pid: int, harness: Path) -> None:
        processes.kill_java(lambda command_line: protocol.names_harness(command_line, harness))
        processes.kill(pid)

    def sleep(self, seconds: float) -> None:
        time.sleep(seconds)

    def now(self) -> float:
        return time.monotonic()
