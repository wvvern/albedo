# Tested targets

This file has one row for each of the 39 jars. A row is filled by the validation run before a release. An empty cell means that the run did not record a result yet. The screenshots and `result.txt` of each target are in `docs/validation/<target>/`.

## Columns

- **Tier:** the features that the code of the target has (see below).
- **Shots, Sky sharp, Chroma, Typing, Settings pages:** `PASS` or `FAIL` from `result.txt` of `tools/screenshots.ps1` for the command files `shots.txt`, `sky-sharp.txt`, `chroma.txt`, `typing-modern.txt` (`typing-legacy.txt` on 1.8.9 and 1.12.2) and `settings-pages.txt`.
- **Release jar:** `started` when the release jar (not the dev client) started and opened the settings screen, or `not started`.
- **Date:** the date of the run, as `YYYY-MM-DD`.
- **Notes:** a fallback that the run saw, or a failure.

## Tiers

- **Full:** the sharp sky draws after the solid world, and the Chroma colors move on the GPU. All 39 targets logged both.

## Fabric

| Loader | Minecraft | Tier | Shots | Sky sharp | Chroma | Typing | Settings pages | Release jar | Date | Notes |
|--------|-----------|------|-------|-----------|--------|--------|----------------|-------------|------|-------|
| Fabric | 1.16.5 | Full | PASS | PASS | PASS | PASS | PASS | | 2026-11-10 | |
| Fabric | 1.17.1 | Full | PASS | PASS | PASS | PASS | PASS | | 2026-11-10 | |
| Fabric | 1.18.2 | Full | PASS | PASS | PASS | PASS | PASS | | 2026-11-10 | |
| Fabric | 1.19.2 | Full | PASS | PASS | PASS | PASS | PASS | | 2026-11-10 | |
| Fabric | 1.19.4 | Full | PASS | PASS | PASS | PASS | PASS | | 2026-11-10 | |
| Fabric | 1.20.1 | Full | PASS | PASS | PASS | PASS | PASS | | 2026-11-10 | |
| Fabric | 1.20.4 | Full | PASS | PASS | PASS | PASS | PASS | | 2026-11-10 | |
| Fabric | 1.20.6 | Full | PASS | PASS | PASS | PASS | PASS | | 2026-11-10 | |
| Fabric | 1.21.1 | Full | PASS | PASS | PASS | PASS | PASS | | 2026-11-10 | |
| Fabric | 1.21.3 | Full | PASS | PASS | PASS | PASS | PASS | | 2026-11-10 | |
| Fabric | 1.21.4 | Full | PASS | PASS | PASS | PASS | PASS | | 2026-11-10 | |
| Fabric | 1.21.5 | Full | PASS | PASS | PASS | PASS | PASS | | 2026-11-10 | |
| Fabric | 1.21.8 | Full | PASS | PASS | PASS | PASS | PASS | | 2026-11-10 | |
| Fabric | 1.21.10 | Full | PASS | PASS | PASS | PASS | PASS | | 2026-11-10 | |
| Fabric | 1.21.11 | Full | PASS | PASS | PASS | PASS | PASS | | 2026-11-10 | |
| Fabric | 26.1.2 | Full | PASS | PASS | PASS | PASS | PASS | | 2026-11-10 | |
| Fabric | 26.2 | Full | PASS | PASS | PASS | PASS | PASS | | 2026-11-10 | Vulkan backend: sharp sky and Chroma run, skycompare and chromacompare same (2026-11-11). |
| Fabric | 26.3 | Full | PASS | PASS | PASS | PASS | PASS | | 2026-11-10 | Vulkan backend: all five suites PASS (2026-11-11). |

## Quilt

The Fabric jars run on Quilt Loader 0.30.2 (the latest release on 2026-11-11), and no jar of its own is needed. The runs use `tools/screenshots.ps1 -Loader quilt`: the release jar of the node is a mod in the `mods` folder of a dev run, and the game log has the line `Loading Minecraft <version> with Quilt Loader 0.30.2` and the jar in the mod table. Quilt Loader reports Fabric Loader 0.19.5, so the `fabricloader` rule of the jar (`>=0.15.0`) passes on every node. On 26.1.2, 26.2 and 26.3, Quilt Loader 0.30.2 does not call the client entry point of the jar, so Albedo starts from its first hook during the resource load and uses the `config` folder of the game directory. All features work there, because the entry point does nothing else, but 26.x is not listed for Quilt, because the start depends on that fallback. Quilt remaps the intermediary jar to the names of the dev game when it starts, so the release jar was not tested in a game with intermediary names.

| Minecraft | Suites | Notes |
|-----------|--------|-------|
| 1.16.5, 1.17.1, 1.19.2, 1.20.4, 1.20.6, 1.21.3, 1.21.4, 1.21.5, 1.21.8, 1.21.10, 26.1.2, 26.2 | Shots PASS | |
| 1.18.2, 1.19.4, 1.20.1, 1.21.1, 1.21.11, 26.3 | all five PASS | |

A first run of 1.18.2, 1.19.4, 1.20.1 and 1.21.11 gave screenshots of 1220 x 900 pixels, and so a FAIL for the window size only. Fabric Loader on 1.21.11 gave the same size on the same day, and a second run of these nodes, one game at a time, gave PASS.

## Forge

| Loader | Minecraft | Tier | Shots | Sky sharp | Chroma | Typing | Settings pages | Release jar | Date | Notes |
|--------|-----------|------|-------|-----------|--------|--------|----------------|-------------|------|-------|
| Forge | 1.8.9 | Full | PASS | PASS | PASS | PASS | PASS | | 2026-11-10 | |
| Forge | 1.12.2 | Full | PASS | PASS | PASS | PASS | PASS | | 2026-11-10 | |
| Forge | 1.16.5 | Full | PASS | PASS | PASS | PASS | PASS | | 2026-11-10 | |
| Forge | 1.17.1 | Full | PASS | PASS | PASS | PASS | PASS | | 2026-11-10 | |
| Forge | 1.18.2 | Full | PASS | PASS | PASS | PASS | PASS | | 2026-11-10 | |
| Forge | 1.19.2 | Full | PASS | PASS | PASS | PASS | PASS | | 2026-11-10 | |
| Forge | 1.19.4 | Full | PASS | PASS | PASS | PASS | PASS | | 2026-11-10 | |
| Forge | 1.20.1 | Full | PASS | PASS | PASS | PASS | PASS | | 2026-11-10 | |
| Forge | 1.20.4 | Full | PASS | PASS | PASS | PASS | PASS | | 2026-11-10 | |

## NeoForge

| Loader | Minecraft | Tier | Shots | Sky sharp | Chroma | Typing | Settings pages | Release jar | Date | Notes |
|--------|-----------|------|-------|-----------|--------|--------|----------------|-------------|------|-------|
| NeoForge | 1.20.4 | Full | PASS | PASS | PASS | PASS | PASS | | 2026-11-10 | |
| NeoForge | 1.20.6 | Full | PASS | PASS | PASS | PASS | PASS | | 2026-11-10 | |
| NeoForge | 1.21.1 | Full | PASS | PASS | PASS | PASS | PASS | | 2026-11-10 | |
| NeoForge | 1.21.3 | Full | PASS | PASS | PASS | PASS | PASS | | 2026-11-10 | |
| NeoForge | 1.21.4 | Full | PASS | PASS | PASS | PASS | PASS | | 2026-11-10 | |
| NeoForge | 1.21.5 | Full | PASS | PASS | PASS | PASS | PASS | | 2026-11-10 | |
| NeoForge | 1.21.8 | Full | PASS | PASS | PASS | PASS | PASS | | 2026-11-10 | |
| NeoForge | 1.21.10 | Full | PASS | PASS | PASS | PASS | PASS | | 2026-11-10 | |
| NeoForge | 1.21.11 | Full | PASS | PASS | PASS | PASS | PASS | | 2026-11-10 | |
| NeoForge | 26.1.2 | Full | PASS | PASS | PASS | PASS | PASS | | 2026-11-10 | |
| NeoForge | 26.2 | Full | PASS | PASS | PASS | PASS | PASS | | 2026-11-10 | Vulkan backend: sharp sky runs, skycompare same (2026-11-11). |
| NeoForge | 26.3 | Full | PASS | PASS | PASS | PASS | PASS | | 2026-11-10 | Vulkan backend: all five suites PASS (2026-11-11). |

These runs passed on a second try: Chroma (`chromacompare`) on Fabric 1.17.1, Fabric 1.21.3 and Forge 1.8.9, and Typing on Forge 1.12.2.
