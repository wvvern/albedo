"""Decides whether a harness run passed, from what the run left behind. No function here reads a file or a process."""

import re
from dataclasses import dataclass

from tools import project
from tools.harness import protocol
from tools.project import Target

RELOAD_REASON = re.compile(r"textures cannot be repainted|more textures than the mod keeps|cannot be sent to the GPU")
# A chat line that starts with the name of the dev player is a death message or an advancement; command feedback starts otherwise.
DEATH = re.compile(r"\[CHAT\] ((?:Player\d+|Dev) (?!has (?:made|just earned|reached|completed) ).*)")
LEGACY_HOOKS = ("afterSolid", "beforeClouds", "tint", "shade", "reloadStep", "textureUpload", "mouseGrab")
MODERN_HOOKS = ("afterSolid", "tint", "fullbright", "shadow", "skyPass", "mouseGrab")
# A renderer mod replaces the terrain draw, so its own tint hook must run in place of the tint and afterSolid hooks of the game.
RENDERER_MOD_HOOKS = ("fullbright", "shadow", "skyPass", "sodiumTint", "mouseGrab")


@dataclass(frozen=True)
class Evidence:
    """What a run left in the harness folder, the game log and the run folder."""

    harness_log: list[str]
    game_log: str
    taken_commands: list[str]
    # The size of each PNG file in the harness folder, or None for a file that is not a PNG.
    image_sizes: dict[str, tuple[int, int] | None]
    has_config: bool


@dataclass(frozen=True)
class Expectations:
    hooks: tuple[str, ...]
    # The graphics backend that the run forced, or "" for the default one.
    backend: str = ""
    loader: str = "fabric"
    renderer_mod: bool = False
    shader_pack: bool = False


def required_hooks(target: Target, renderer_mod: bool) -> tuple[str, ...]:
    """The hook mouseGrab counts the grab calls that the harness skips, so a game that grabs the mouse, and clips the cursor, fails."""
    if target.legacy:
        return LEGACY_HOOKS
    return RENDERER_MOD_HOOKS if renderer_mod else MODERN_HOOKS


def size_text(size: tuple[int, int] | None) -> str:
    return f"{size[0]} x {size[1]}" if size else "not a PNG"


def judge(evidence: Evidence, expected: Expectations) -> list[str]:
    """The reasons why the run is not PASS."""
    log, game = evidence.harness_log, evidence.game_log
    failures = []
    if not evidence.has_config:
        failures.append("the run folder does not have config/albedo/config.json")
    if not protocol.ran_quit(log):
        failures.append("the game did not get to quit")
    # The game falls back to the other backend when the forced one does not start, which would hide the test of the wrong backend.
    if expected.backend and not re.search(rf"(?i)Using graphics backend {expected.backend}\b", game):
        failures.append(f"the game log does not have 'Using graphics backend {expected.backend}'")
    if expected.loader != "fabric" and not re.search(r"with Quilt Loader \d", game):
        failures.append("the game log does not have the Quilt Loader version line")
    # The mod table of Quilt Loader names the file of each mod; the release jar of the node is in the mods folder of the run.
    if expected.loader == "quilt" and not re.search(rf"<mods>\\{project.ARCHIVE}-fabric-\S+\.jar", game):
        failures.append("the mod table of the game log does not have the release jar of Albedo")
    if "Albedo ready" not in game:
        failures.append("the game log does not have 'Albedo ready'")
    if not re.search(r"Changed \d+ textures", game):
        failures.append("the game log does not have 'Changed N textures'")
    # With no other mod, each texture repaints in place; a failed upload reloads all resources, which hides the failure in the images.
    if not expected.renderer_mod:
        failures += [f"the game log has a reload reason: {reason}" for reason in dict.fromkeys(RELOAD_REASON.findall(game))]
    # A dead player shows the death screen in each later screenshot.
    failures += [f"the player died: {message}" for message in dict.fromkeys(DEATH.findall(game))]
    failures += [f"harness error: {line}" for line in protocol.errors(log)]
    failures += [f"harness compare: {line}" for line in protocol.failed_compares(log)]
    failures += hook_failures(log, expected.hooks)
    failures += image_failures(evidence)
    failures += result_failures(evidence)
    # Iris turns shaders off when the pack does not load, and the run continues.
    if expected.shader_pack and ("Using shaderpack" not in game or "disabling shaders" in game):
        failures.append("Iris did not load the shader pack")
    return failures


def hook_failures(log: list[str], hooks: tuple[str, ...]) -> list[str]:
    """An optional hook with a wrong target name never runs, so each hook of the game must have run."""
    lines = protocol.hook_lines(log)
    if not lines:
        return ["the harness log does not have a hooks line"]
    return [f"the hook {hook} did not run: {line}" for line in lines for hook in hooks if not protocol.hook_ran(line, hook)]


def image_failures(evidence: Evidence) -> list[str]:
    """A game window of the wrong size gives screenshots that do not compare with the reference."""
    failures = []
    for command in evidence.taken_commands:
        images, complete = protocol.images_of(command, evidence.image_sizes)
        if not complete:
            missing = "no image" if protocol.verb(command) == protocol.SCREENSHOT else "not all images"
            failures.append(f"{missing} for: {command}")
        for image in images:
            size = evidence.image_sizes[image]
            if size != protocol.WINDOW_SIZE:
                failures.append(f"{image} is {size_text(size)}, expected {size_text(protocol.WINDOW_SIZE)}")
    return failures


def result_failures(evidence: Evidence) -> list[str]:
    """A command that logs its result in a later frame can leave no result when the run gets to quit first."""
    verbs = [protocol.verb(command) for command in evidence.taken_commands]
    failures = []
    for verb, good_result, what in protocol.RESULTS:
        ran = verbs.count(verb)
        logged = sum(good_result.search(line) is not None for line in evidence.harness_log)
        if logged < ran:
            failures.append(f"{ran} {verb} commands gave only {logged} {what}")
    return failures
