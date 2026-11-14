"""Makes the command file of a run from the suite files, for the game version of a target.

The suites are written for the newest version. RULES has what an older version needs in a different way, and a line of a suite can
start with "modern:" or "legacy:" when only that build can run it.
"""

import re
from collections.abc import Callable, Iterable
from dataclasses import dataclass
from pathlib import Path

from tools.harness import protocol
from tools.project import Target
from tools.report import ToolError

SUITES = Path(__file__).with_name("suites")
BUILD_PREFIX = re.compile(r"^(modern|legacy):\s*")
# The word that has the height in a game command, after "run" and the command name.
HEIGHT_WORDS = {"fill": (3, 6), "setblock": (3,), "tp": (4,), "summon": (4,)}
OLD_GROUND_OFFSET = 64


@dataclass(frozen=True)
class Rule:
    """A change of each line for the targets that need it, where no line in place of a line removes it."""

    applies: Callable[[Target], bool]
    rewrite: Callable[[str], str | None]


def suite_file(name: str) -> Path:
    file = SUITES / f"{name.removesuffix('.txt')}.txt"
    if not file.is_file():
        raise ToolError(f"no suite {name}; the suites are: {' '.join(sorted(suite.stem for suite in SUITES.glob('*.txt')))}")
    return file


def scene_of(target: Target) -> str:
    return "scene-legacy" if target.legacy else "scene-modern"


def for_build(line: str, target: Target) -> str | None:
    prefix = BUILD_PREFIX.match(line)
    if not prefix:
        return line
    return line[prefix.end() :] if (prefix.group(1) == "legacy") == target.legacy else None


def legacy_click(line: str) -> str:
    return re.sub(r"^ui realclick\b", "ui click", line)


def entity_ids_of_1_12(line: str) -> str | None:
    """1.12.2 names an entity type by its id and has no achievement command."""
    if line.startswith("run achievement "):
        return None
    line = re.sub(r"\b(type=!?)([A-Z]\w*)", lambda match: match.group(1) + entity_id(match.group(2)), line)
    return re.sub(r"^(run summon )([A-Z]\w*)", lambda match: f"{match.group(1)}minecraft:{entity_id(match.group(2))}", line)


def entity_id(name: str) -> str:
    """The 1.12 id of a 1.8 entity name, for example armor_stand for ArmorStand."""
    return re.sub(r"(?<!^)(?=[A-Z])", "_", name).lower()


def without_elytra(line: str) -> str | None:
    """1.8 has no elytra, so a line that names it does not run."""
    return None if "elytra" in line else line


def old_grass_name(line: str) -> str:
    return re.sub(r"\bshort_grass\b", "grass", line)


def move_scene_up(line: str) -> str:
    """Superflat ground is y=-61 from 1.18 and y=3 before, so heights in [-64, 0) move up by 64."""
    words = line.split(" ")
    if words[0] != "run" or len(words) < 5:
        return line
    for index in HEIGHT_WORDS.get(words[1], ()):
        try:
            height = float(words[index])
        except (IndexError, ValueError):
            continue
        if -OLD_GROUND_OFFSET <= height < 0:
            words[index] = f"{height + OLD_GROUND_OFFSET:g}"
    return " ".join(words)


def tp_from_player(line: str) -> str:
    """The server stands at the world spawn, which a flat world before 1.18 puts away from 0 0, so a relative tp starts from the player there."""
    return re.sub(r"^run tp @p ~ ~ ~ ", "run execute as @p at @p run tp @p ~ ~ ~ ", line)


def old_replaceitem(line: str) -> str:
    """1.17 replaces the replaceitem command with item replace."""
    return re.sub(r"^run item replace entity (\S+) (\S+) with ", r"run replaceitem entity \1 \2 ", line)


RULES = (
    Rule(lambda target: target.legacy, legacy_click),
    Rule(lambda target: target.legacy and target.at_least("1.12"), entity_ids_of_1_12),
    Rule(lambda target: target.legacy and not target.at_least("1.9"), without_elytra),
    Rule(lambda target: not target.legacy and not target.at_least("1.20.3"), old_grass_name),
    Rule(lambda target: not target.legacy and not target.at_least("1.18"), move_scene_up),
    Rule(lambda target: not target.legacy and not target.at_least("1.18"), tp_from_player),
    Rule(lambda target: not target.legacy and not target.at_least("1.17"), old_replaceitem),
)


def compose(lines: Iterable[str], target: Target) -> list[str]:
    """The lines of the target's build after its rules, with 'hooks' before each 'quit' so that the verdict sees each hook count."""
    rules = [rule.rewrite for rule in RULES if rule.applies(target)]
    commands = []
    for line in lines:
        rewritten = for_build(line, target)
        for rewrite in rules:
            if rewritten is None:
                break
            rewritten = rewrite(rewritten)
        if rewritten is None:
            continue
        if rewritten.strip() == protocol.QUIT:
            commands.append(protocol.HOOKS)
        commands.append(rewritten)
    return commands


def read_suites(names: Iterable[str]) -> list[str]:
    return [line for name in names for line in suite_file(name).read_text(encoding="utf-8").splitlines()]
