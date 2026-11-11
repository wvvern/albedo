# Building and testing

You need JDK 21 and JDK 25. Gradle downloads JDK 8 for 1.8.9, 1.12.2 and 1.16.5 if it is missing.

```bash
pwsh tools/build-all.ps1                                                    # all 39 jars -> build/libs/
cd modern && ./gradlew :fabric:26.3:releaseJar :neoforge:26.3:releaseJar    # the jars of one node
cd legacy && ./gradlew build                                                # Forge 1.8.9 and 1.12.2
```

Before you push: `./gradlew :core:check` in `modern/` and `bash tools/lint.sh` in the root. See [CODING_STANDARDS.md](CODING_STANDARDS.md).

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
- Forge 1.16.5 with `-Pharness` adds the mixin in `modern/forge/dev-1.16.5`, because Loom leaves SRG names on two Forge-patched calls in `Transformation` (`NoSuchMethodError: Transformation.func_227987_b_()` when models load). `tools/check-jar.sh` fails a jar that contains it.
- `-Pquilt` runs the Fabric dev game on Quilt Loader.
- `-Pcompat=sodium|embeddium|iris` adds that mod; see "Compat mods in the dev run" in [ARCHITECTURE.md](ARCHITECTURE.md).

## Add a Minecraft version

1. Add `modern/versions/<v>/gradle.properties`, and for Forge or NeoForge `modern/<loader>/versions/<v>/gradle.properties` with `loom.platform=forge` or `neoforge`.
2. Add `<v>` to `nodes` and to `forgeNodes` or `neoforgeNodes` in `modern/settings.gradle`, and a row to the `modern` matrix in `.github/workflows/ci.yml`. `tools/lint.sh` checks the row against both.
3. Build the node. The compile errors show the hooks that moved; fix each with a `//? if` on a boundary from ARCHITECTURE.md, adding a boundary if needed (`tools/boundaries.sh` shows what changed).
4. Run `tools/screenshots.ps1 -Targets fabric-<v>` and check that `result.txt` starts with `PASS`.

## CI

`.github/workflows/ci.yml` has a `check` job (lint, `:core:check`), one `modern` job per node and one `legacy` job. Each job builds one node in one Gradle run and runs `tools/check-jar.sh` or `tools/check-legacy-jar.sh` on each jar. Node jobs only read the Gradle cache, because all nodes together exceed the repository's cache limit. Releases: [RELEASING.md](RELEASING.md).

## Game tests (harness)

`tools/screenshots.ps1` starts each target's dev client on a hidden desktop and drives it with a command file from `tools/harness/` through `core/dev/Harness`. Never run test games on the visible desktop.

```powershell
pwsh -Command "& ./tools/screenshots.ps1 -Targets fabric-26.3,forge-1.8.9 -Shots sky-sharp.txt -OutDir build\run"
```

- Suites: `shots.txt` (each profile, mod on and off, hooks, window size, cursor), `sky-sharp.txt` (`skycompare` on the presets), `chroma.txt` with `pastel.txt`, `typing-modern.txt` / `typing-legacy.txt`, `settings-pages.txt`, `gallery.txt` (the images in `docs/gallery`). The `scene-*.txt` files set up the world.
- 1.8.9 and 1.12.2 run the `*-legacy.txt` copy of a `*-modern.txt` file.
- Options: `-Compat <mod>`, `-ShaderPack <zip>`, `-Config <config.json>`, `-OptionLines key:value` (for example `useVbo:true`), `-Backend opengl|vulkan` (26.2+, add `-VulkanValidation` for the validation layers), `-Loader quilt` (the node's release jar on Quilt Loader) or `quilt-dev`.
- Each target gets a `result.txt`: `PASS` or `FAIL` on the first line, then the reasons. Reasons are log lines starting with `!` (including `! unknown command` and `! no such control`), a screenshot that is not 1600 x 900, a `skycompare` without `same`, or a missing hook. The exit code is 1 if any target fails.
- `hooks` logs each optional hook's counter, for example `hooks: afterSolid=1807 tint=100562`. The script adds it before `quit` and fails a target where a required counter is 0.
- The harness never grabs the mouse: a grab clips the cursor for the whole window station, including the visible desktop. `MouseGrabMixin` skips the grab while `-Dalbedo.harness` is set, and `cursorfree` checks the clip. `tools/free-cursor.ps1` (started by `tools/run-hidden.ps1`) frees a clip left by a killed game.
- Useful commands: `sky <preset>`, `skycompare`, `skytime [w h]`, `skyclock <s>|run`, `effects`, `chromacompare`, `chromatime`, `ui click <label>`, `type <text>`, `key <name>`, `hooks`, `quit`. `Harness.java` has the full list.
- `tools/optifine.ps1` runs the built 1.8.9 jar with OptiFine.
