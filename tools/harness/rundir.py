"""Writes the files that a game reads at its start into its game folder."""

import shutil
from pathlib import Path

from tools.harness import files

WORLD = "AlbedoTest"
# No sound, no narrator and no first-start screens, and a game that goes on when its window is not in front.
QUIET_OPTIONS = {
    "onboardAccessibility": "false",
    "narrator": "0",
    "soundCategory_master": "0.0",
    "pauseOnLostFocus": "false",
    "tutorialStep": "none",
    "skipMultiplayerWarning": "true",
    "joinedFirstServer": "true",
    "fullscreen": "false",
}


def mod_config(run_dir: Path) -> Path:
    return run_dir / "config" / "albedo" / "config.json"


def key_value(line: str) -> str:
    """A line "key:value" of a command line, where argparse gives the error for any other text."""
    if ":" not in line:
        raise ValueError(line)
    return line


def parse_option_lines(lines: list[str]) -> dict[str, str]:
    """The options of lines "key:value", as in options.txt."""
    return dict(line.split(":", 1) for line in lines if ":" in line)


def read_options(file: Path) -> dict[str, str]:
    return parse_option_lines(files.read_lines(file))


def write_options(file: Path, options: dict[str, str]) -> None:
    files.write_lines(file, (f"{key}:{value}" for key, value in options.items()))


def set_toml_key(file: Path, key: str, value: str) -> None:
    """Puts "key = value" at the top of a config file, where it is outside each table, in place of the old line of the key."""
    kept = [line for line in files.read_lines(file) if line.split("=")[0].strip() != key]
    files.write_lines(file, [f"{key} = {value}", *kept], encoding="utf-8")


def write_iris_properties(run_dir: Path, shader_pack: Path | None) -> None:
    """Iris reads config/iris.properties at its start, and the file stays for later runs, so each run writes it."""
    lines = ["disableUpdateMessage=true", f"enableShaders={'true' if shader_pack else 'false'}"]
    if shader_pack:
        copy = run_dir / "shaderpacks" / shader_pack.name
        copy.parent.mkdir(parents=True, exist_ok=True)
        # A new copy each run, so that a folder pack does not keep files from an old copy.
        if copy.is_dir():
            shutil.rmtree(copy)
        if shader_pack.is_dir():
            shutil.copytree(shader_pack, copy)
        else:
            shutil.copy2(shader_pack, copy)
        lines.append(f"shaderPack={shader_pack.name}")
    # Iris reads the file as ISO-8859-1, so no byte order mark.
    files.write_lines(run_dir / "config" / "iris.properties", lines)


def reset_mod_state(run_dir: Path, config: Path | None = None) -> None:
    """The default config, or a given one, and a new world, so that the screenshots are the same each run."""
    file = mod_config(run_dir)
    file.unlink(missing_ok=True)
    if config:
        file.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(config, file)
    shutil.rmtree(run_dir / "saves" / WORLD, ignore_errors=True)
