"""The running processes that the harness asks about and stops. Needs psutil."""

from collections.abc import Iterator
from contextlib import suppress

import psutil

JAVA_NAMES = ("java.exe", "javaw.exe")


def java_processes() -> Iterator[tuple[int, str]]:
    """The process id and the command line of each running Java process that this user can read."""
    for process in psutil.process_iter(["name", "cmdline"]):
        if (process.info["name"] or "").lower() in JAVA_NAMES and process.info["cmdline"]:
            yield process.pid, " ".join(process.info["cmdline"])


def java_command_lines() -> list[str]:
    return [command_line for _, command_line in java_processes()]


def alive(pid: int) -> bool:
    return psutil.pid_exists(pid)


def wait(pid: int) -> None:
    with suppress(psutil.NoSuchProcess):
        psutil.Process(pid).wait()


def kill(pid: int) -> None:
    with suppress(psutil.NoSuchProcess):
        psutil.Process(pid).kill()


def kill_java_with(text: str) -> None:
    """Stops each Java process that has a text in its command line."""
    for pid, command_line in java_processes():
        if text in command_line:
            kill(pid)
