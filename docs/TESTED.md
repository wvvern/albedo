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

## Renderer mods

Each row is a dev run of `tools/screenshots.ps1 -Compat <mod>` with the profile quake-white (2026-11-11). `Tint` is the `sodiumTint` counter of the `hooks` line: more than 0 means that the block tint hook ran, and the grass, leaves and vines of the shots have the color of the profile. `Water` is the `sodiumWater` counter, which only Sodium 0.5 and its forks need (an empty cell means that the water has no tint from BiomeColors). The Albedo setting "shading" (face shade and smooth lighting) made the wall of each shot as bright with it off as in the runs without a renderer mod. The runs without a renderer mod (Fabric 1.19.4, Fabric 1.21.1, Forge 1.20.1) passed and made the same shots as the runs with one.

| Loader | Minecraft | Renderer mod | Tint | Water | Notes |
|--------|-----------|--------------|------|-------|-------|
| Fabric | 1.16.5 | Sodium 0.2.0 | 278299 | | Before the hook 0 and green leaves in the shot. |
| Fabric | 1.17.1 | Sodium 0.3.4 | 267800 | | |
| Fabric | 1.18.2 | Sodium 0.4.1 | 282824 | | |
| Fabric | 1.19.2 | Sodium 0.4.4 | 283886 | | |
| Fabric | 1.19.4 | Sodium 0.4.10 | 284658 | | Before the hook 0 and green leaves in the shot. |
| Fabric | 1.20.1 | Sodium 0.5.13 | 639460 | 864 | Before the water hook the water was blue. |
| Fabric | 1.20.4 | Sodium 0.5.8 | 643139 | 960 | Before the water hook the water was blue. |
| Fabric | 1.20.6 | Sodium 0.5.11 | 647210 | 864 | Before the hook 0, green leaves and blue water in the shot. |
| Fabric | 1.21.1 | Sodium 0.8.13 | 582116 | | |
| Fabric | 1.21.3 | Sodium 0.6.13 | 580812 | | |
| Fabric | 1.21.5 | Sodium 0.6.13 | 595993 | | |
| Fabric | 1.21.8 | Sodium 0.7.3 | 594538 | | |
| Fabric | 26.1.2 | Sodium 0.9.2 | 1576472 | | `sodiumShade` 3149207. |
| Fabric | 26.3 | Sodium 0.9.2 | 1594396 | | `sodiumShade` 3185042. |
| Forge | 1.16.5 | Embeddium 0.3.18 | 283945 | | |
| Forge | 1.16.5 | Rubidium 0.2.13 | 274285 | | |
| Forge | 1.18.2 | Embeddium 0.3.18 | 292055 | | |
| Forge | 1.18.2 | Rubidium 0.5.6 | 282056 | | |
| Forge | 1.19.2 | Embeddium 0.3.18.1 | 288005 | | |
| Forge | 1.19.2 | Rubidium 0.6.2c | 282845 | | |
| Forge | 1.19.4 | Rubidium 0.6.4 | 281563 | | |
| Forge | 1.20.1 | Rubidium 0.7.1 | 565282 | 1056 | |
| NeoForge | 1.20.4 | Embeddium 0.3.25 | 639460 | | |
| NeoForge | 1.21.3 | Sodium 0.6.13 | 581788 | | |

Not run in this session: Fabric 1.21.4, 1.21.10, 1.21.11 and 26.2 (the hooks of Sodium 0.6.13, 0.7.3, 0.8.14 and 0.9.2 ran on the versions next to them), and all NeoForge nodes from 1.21.1 except 1.21.3. Not possible: Forge 1.17.1 and Forge 1.20.4 (no Embeddium or Rubidium exists for them), NeoForge 1.20.6 and 1.21.4 with Embeddium and Forge 1.20.1 with Embeddium 0.3.31 (a mixin of Embeddium does not start in the dev run of those nodes), and Embeddium on Fabric (not tried). The `getVertexColors` and `hasColor` targets of those builds are the same as in the builds above (checked with `javap`).

These runs passed on a second try: Chroma (`chromacompare`) on Fabric 1.17.1, Fabric 1.21.3 and Forge 1.8.9, and Typing on Forge 1.12.2.
