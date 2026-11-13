# Building and testing

You need JDK 21 and JDK 25, and Python 3.12 or newer for the tools. Gradle downloads JDK 8 for 1.8.9, 1.12.2 and 1.16.5 if it is missing.

```bash
python -m tools build                                                       # all jars -> build/libs/
python -m tools build fabric-26.3 neoforge-26.3                             # the jars of some targets
cd modern && ./gradlew :fabric:26.3:releaseJar :neoforge:26.3:releaseJar    # the jars of one node
cd legacy && ./gradlew build                                                # Forge 1.8.9 and 1.12.2
```

Before you push: `./gradlew :core:check` in `modern/` and `python -m tools lint` in the root. See [CODING_STANDARDS.md](CODING_STANDARDS.md).

CI jobs and releases: [RELEASING.md](RELEASING.md).

## Modern build

- A node is a folder in `modern/versions/`. `modern/settings.gradle` lists them in `nodes` (common and Fabric), `forgeNodes` and `neoforgeNodes`.
- Build one node per Gradle run (`--configure-on-demand`, also set in `modern/gradle.properties`). When one run configures every node, Loom can give a Forge node another node's mappings and write a bad jar to the shared cache, where it stays.
  - Symptoms: `cannot access Screen` when compiling, or `NoClassDefFoundError: gjp$a` in the dev game.
  - Repair: stop all Gradle daemons, delete `~/.gradle/caches/fabric-loom/<mc>/loom.mappings.*forge-<mc>-<forge>*`, `~/.gradle/caches/fabric-loom/<mc>/forge/<mc>-<forge>` and `~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/forge-<mc>-<forge>-minecraft-merged*`, then build that node alone.
- `modern/common` compiles against the Mixin 0.8.5 annotations: 0.8.7 writes a redirect's `at` as an array, which MixinExtras before 0.5 (NeoForge 1.20.6, Fabric Loader 0.15, Forge packs) cannot read.
- `-Pmc_override=<version>` builds a node against another Minecraft version of its range. Only the dependency changes; the conditions keep the node's version.
- The mod version is `mod_version` in `modern/gradle.properties`; the legacy build reads it too (or `-PmodVersion`).

## Dev clients

- Modern: `./gradlew :fabric:26.3:runClient` in `modern/` (any loader and node). Legacy: `./gradlew --configure-on-demand :1.12.2:runClientDev` in `legacy/`.
- Forge 1.16.5 with `-Pharness` adds the mixin in `modern/forge/dev-1.16.5`, because Loom leaves SRG names on two Forge-patched calls in `Transformation` (`NoSuchMethodError: Transformation.func_227987_b_()` when models load). `python -m tools check-jar` fails a jar that contains it.
- `-Pquilt` runs the Fabric dev game on Quilt Loader.
- `-Pcompat=sodium|embeddium|rubidium|iris` adds that mod; see "Compat mods in the dev run" in [ARCHITECTURE.md](ARCHITECTURE.md).

## Add a Minecraft version

1. Add `modern/versions/<v>/gradle.properties`, and for Forge or NeoForge `modern/<loader>/versions/<v>/gradle.properties` with `loom.platform=forge` or `neoforge`.
2. Add `<v>` to `nodes` and to `forgeNodes` or `neoforgeNodes` in `modern/settings.gradle`. CI builds and uploads what these lists name; no workflow file changes.
3. For the Modrinth uploads, the node's `gradle.properties` can set `modrinth_versions` (the game versions of each jar, default `minecraft_version`), `modrinth_versions_fabric` (when the Fabric jar runs on more versions) and `modrinth_loaders_fabric=fabric quilt` (when the jar is tested on Quilt). Values are separated by spaces.
4. Build the node. The compile errors show the hooks that moved; fix each with a `//? if` on a boundary from ARCHITECTURE.md, adding a boundary if needed (`python -m tools boundaries` shows what changed).
5. Run `python -m tools harness fabric-<v>` and check that `result.txt` starts with `PASS`.

## Tools

`python -m tools <command>` runs a tool from the repository root; `python -m tools` lists them and `--help` shows a command's arguments. `python -m pip install -r tools/requirements.txt` adds Pillow and psutil, which only `image`, `harness` and `optifine` need.

| Command | What it does |
|---------|--------------|
| `build [TARGET...]` | Builds release jars into `build/libs/` (or `--out`), one Gradle run per node. CI builds with it. |
| `lint` | The repository checks that Checkstyle cannot do. |
| `check-jar JAR...` | Examines release jars (or a folder of them) with no game. |
| `compare-jars OLD NEW` | Compares built jars with a release, entry by entry; `tools/jar-changes.txt` lists the expected differences. |
| `release-notes X.Y.Z` | Prints the `CHANGELOG.md` section of a version. |
| `boundaries` | Prints the shape of each game member in `tools/boundaries.txt` on each node. |
| `ci-matrix` | Prints the build and upload rows that `ci.yml` reads. |
| `image frame\|rim\|split\|diff` | Makes the store page images, and compares the screenshots of two runs. |
| `harness TARGET...` | Runs a harness suite in the dev game on a hidden desktop (below); `all` is each target. |
| `optifine` | Runs the built 1.8.9 jar in a production client with OptiFine on a hidden desktop. |

A target is `<loader>-<node>`, for example `fabric-26.3` or `forge-1.8.9`.

Rules for the code in `tools/`:

- `tools/project.py` is the only reader of the build layout: the node lists, the node properties, the jar names, the run folders and the Gradle task names. A tool asks it for nodes and targets and never names a node or a version list itself.
- `tools/harness/protocol.py` is the only place with the file names and log lines of `core/dev/Harness.java`.
- Only `harness/desktop.py` and `harness/cursor.py` call Windows, only `harness/processes.py` uses psutil, and only `images.py` uses Pillow.
- A command module parses its arguments and prints; what it decides is in functions that take data and return data, with a test in `tools/tests/`. `lint` and `check-jar` are lists of such check functions.
- What CI runs (`lint`, `build`, `check-jar`, `release-notes`, `ci-matrix` and the tests) uses only the standard library.
- Check the tools with `python -m unittest discover -s tools/tests -t .` and `ruff check tools` / `ruff format tools` (settings in `pyproject.toml`).

## Game tests (harness)

`python -m tools harness <target>...` starts each target's dev client on a hidden desktop and drives it with a command file made from the suites in `tools/harness/suites/` through `core/dev/Harness`. It runs on Windows only. Never run test games on the visible desktop.

```bash
python -m tools harness fabric-26.3 forge-1.8.9 --suite sky-sharp --out build/run
```

- Suites: `shots` (each profile, mod on and off, hooks, window size, cursor), `sky-sharp` (`skycompare` on the presets), `chroma` with `pastel`, `typing`, `settings-pages`, `gallery` (the images in `modrinth/gallery`). The `scene-*` suites set up the world and run first.
- A suite is written for the newest version. `tools/harness/script.py` has the rules that change a line for an older version (the ground level before 1.18, block and entity names), and a suite line that starts with `modern:` or `legacy:` runs only in that build.
- Options: `--extra <suite>`, `--compat <mod>`, `--shader-pack <zip>`, `--config <config.json>`, `--option key:value` (for example `useVbo:true`), `--gui-scale <n>`, `--backend opengl|vulkan` (26.2+, add `--vulkan-validation` for the validation layers), `--loader quilt` (the node's release jar on Quilt Loader) or `quilt-dev`.
- Each target gets a `result.txt`: `PASS` or `FAIL` on the first line, then the reasons. Reasons are log lines starting with `!` (including `! unknown command` and `! no such control`), a screenshot that is not 1600 x 900, a `skycompare` without `same`, or a missing hook; `tools/harness/verdict.py` has all of them. The exit code is 1 if any target fails.
- `hooks` logs each optional hook's counter, for example `hooks: afterSolid=1807 tint=100562`. The command adds it before `quit` and fails a target where a required counter is 0.
- The harness folder of a run, with the game log and the harness log, is `%TEMP%\albedo-harness\<checkout folder>\<target>`. A run stops at the start when a game already uses that folder.
- The harness never grabs the mouse: a grab clips the cursor for the whole window station, including the visible desktop. `MouseGrabMixin` skips the grab while `-Dalbedo.harness` is set, and `cursorfree` checks the clip. A watch process (`tools/harness/watchdog.py`) frees a clip left by a killed game.
- Commands: `sky <preset>`, `skycompare`, `skytime [w h]`, `skyclock <s>|run`, `effects`, `chromacompare`, `chromatime`, `ui click <label>`, `type <text>`, `key <name>`, `hooks`, `quit`. `Harness.java` has the full list.
