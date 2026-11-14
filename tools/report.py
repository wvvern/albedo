"""How every command reports: one line for each problem, and one error type for a failure of the command itself."""

import sys
from collections.abc import Iterable
from dataclasses import dataclass


def say(label: str, message: str) -> None:
    """Prints one line for a target or node, in one write, so that the lines of threads that run at the same time do not mix."""
    sys.stdout.write(f"[{label}] {message}\n")
    sys.stdout.flush()


class ToolError(Exception):
    """A failure whose message is for the person who ran the command, so it prints with no traceback."""


@dataclass(frozen=True)
class Problem:
    where: str
    message: str
    line: int | None = None

    def __str__(self) -> str:
        place = f"{self.where}:{self.line}" if self.line else self.where
        return f"{place}: error: {self.message}"


def report(problems: Iterable[Problem]) -> int:
    """Prints each problem and returns the exit code of the command."""
    count = 0
    for problem in problems:
        print(problem)
        count += 1
    return 1 if count else 0
