"""The running processes that the harness asks about and stops. Needs psutil."""

import time
from collections.abc import Callable, Iterator
from contextlib import suppress
from pathlib import Path

import psutil

JAVA_NAMES = ("java.exe", "javaw.exe")
WAIT_SECONDS = 5


def tool_runs(root: Path, tool: str) -> bool:
    """Whether a `python -m tools <tool>` runs in the folder root."""
    for process in psutil.process_iter(["cmdline", "cwd"]):
        command = process.info["cmdline"] or []
        if command[1:4] == ["-m", "tools", tool] and process.info["cwd"] and Path(process.info["cwd"]) == root:
            return True
    return False


def wait_for_tool(root: Path, tool: str) -> None:
    """Waits while a `python -m tools <tool>` runs in the folder root, because the harness and the build write the same build output."""
    if not tool_runs(root, tool):
        return
    print(f"waiting for 'python -m tools {tool}' in {root} to end", flush=True)
    while tool_runs(root, tool):
        time.sleep(WAIT_SECONDS)


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


def kill_java(wanted: Callable[[str], bool]) -> None:
    """Stops each Java process whose command line is a wanted one."""
    for pid, command_line in java_processes():
        if wanted(command_line):
            kill(pid)
