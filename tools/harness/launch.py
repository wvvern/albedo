"""The command line of a game run, with no tools imports, so that the fit process and the Win32 shim load only this."""

from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class Launch:
    """A command line to run in a folder, with its output in a log file."""

    command_line: str
    cwd: Path
    log: Path
    # Variables for the process on top of the ones of this process.
    environment: tuple[tuple[str, str], ...] = ()
