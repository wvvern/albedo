"""What the dev run of a target needs from the options of a harness run: the game folder, the Gradle launch and what the run must show."""

from dataclasses import dataclass
from pathlib import Path

from tools import gradle, project
from tools.harness import protocol, rundir, script, verdict
from tools.harness.launch import Launch
from tools.harness.verdict import Expectations, Loader
from tools.project import Target

# The first version with the launch argument for the graphics backend; an older game has only OpenGL and no backend line to check.
FIRST_BACKEND_CHOICE = "26.2"


@dataclass(frozen=True)
class RunOptions:
    suite: str = "shots"
    extra: tuple[str, ...] = ()
    out: Path = Path("build/screenshots")
    timeout: int = 600
    compat: str = ""
    shader_pack: Path | None = None
    config: Path | None = None
    option_lines: tuple[str, ...] = ()
    gui_scale: int = 0
    backend: str = ""
    vulkan_validation: bool = False
    loader: Loader = Loader.FABRIC
    # With several suites, each suite of a target gets its own game folder and harness folder, so all suites run at the same time.
    own_folder: bool = False

    def iris(self, target: Target) -> bool:
        return not target.legacy and "iris" in [mod.strip() for mod in self.compat.split(",")]

    def forced_backend(self, target: Target) -> str:
        return self.backend if not target.legacy and target.at_least(FIRST_BACKEND_CHOICE) else ""

    def suites(self, target: Target) -> list[str]:
        return [script.scene_of(target), *self.extra, self.suite]

    def run_folder(self) -> str:
        """The game folder, from the folder of the node. The Gradle run task gets it as -PrunDir."""
        return f"runs/{self.suite}" if self.own_folder else "run"

    def run_dir(self, target: Target) -> Path:
        return target.project_dir / self.run_folder() if self.own_folder else target.run_dir

    def harness_name(self, target: Target) -> str:
        return f"{target.name}-{self.suite}" if self.own_folder else target.name


def loader_config(target: Target, renderer_mod: bool) -> dict[str, tuple[str, str]]:
    """The key and the value that a loader needs in a file of its config folder for a hidden run."""
    if target.legacy or target.loader == "fabric":
        return {}
    # The early loading window of Forge and NeoForge does not open on the hidden desktop, so the game makes its window itself.
    config = {"fml.toml": ("earlyWindowControl", "false")}
    if target.loader == "neoforge":
        # The NeoForge dev run checks each Blaze3D call, but Sodium 0.8.14 casts a texture that this check wraps, so a renderer mod stops it.
        config["neoforge-client.toml"] = ("enableB3DValidationLayer", "false" if renderer_mod else "true")
    return config


def prepare_run_dir(target: Target, options: RunOptions) -> None:
    run_dir = options.run_dir(target)
    # A new options file each run, so that no option of an earlier run changes the screenshots.
    game_options = rundir.QUIET_OPTIONS | {"guiScale": str(options.gui_scale)} | rundir.parse_option_lines(list(options.option_lines))
    rundir.write_options(run_dir / "options.txt", game_options)
    # A release jar that an earlier run on Quilt Loader left here would load next to the classes of the dev run.
    for jar in (run_dir / "mods").glob(project.JAR_GLOB):
        jar.unlink()
    for file, (key, value) in loader_config(target, bool(options.compat)).items():
        rundir.set_toml_key(run_dir / "config" / file, key, value)
    if options.iris(target):
        rundir.write_iris_properties(run_dir, options.shader_pack)
    rundir.reset_mod_state(run_dir, options.config)


def gradle_properties(target: Target, harness: Path, options: RunOptions) -> dict[str, str | bool]:
    """The properties of the run task, which the Gradle scripts of modern/gradle, modern/fabric and legacy/version.gradle.kts read."""
    return {
        "harness": str(harness),
        "compat": options.compat,
        "backend": options.forced_backend(target),
        "vulkanValidation": options.vulkan_validation,
        "quilt": options.loader.on_quilt,
        # The release jar of the node as a mod, not the classes of the dev run.
        "quiltJar": options.loader.release_jar,
        "harness_window": protocol.WINDOW_PROPERTY,
        "runDir": options.run_folder() if options.own_folder else "",
    }


def launch(target: Target, harness: Path, options: RunOptions) -> Launch:
    command_line = gradle.hidden_command_line(target.build_dir, target.run_task, gradle_properties(target, harness, options))
    return Launch(command_line, target.build_dir, harness / "gradle.log")


def expectations(target: Target, options: RunOptions) -> Expectations:
    return Expectations(
        hooks=verdict.required_hooks(target, bool(options.compat)),
        backend=options.forced_backend(target),
        loader=options.loader,
        renderer_mod=bool(options.compat),
        shader_pack=bool(options.shader_pack) and options.iris(target),
    )
