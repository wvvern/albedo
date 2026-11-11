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
- **Full, OpenGL only:** as Full on the OpenGL backend. With the Vulkan backend of 26.2 the sky draws from a baked image.

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
| Fabric | 26.2 | Full, OpenGL only | PASS | PASS | PASS | PASS | PASS | | 2026-11-10 | |
| Fabric | 26.3 | Full | PASS | PASS | PASS | PASS | PASS | | 2026-11-10 | |

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
| NeoForge | 26.2 | Full, OpenGL only | PASS | PASS | PASS | PASS | PASS | | 2026-11-10 | |
| NeoForge | 26.3 | Full | PASS | PASS | PASS | PASS | PASS | | 2026-11-10 | |

These runs passed on a second try: Chroma (`chromacompare`) on Fabric 1.17.1, Fabric 1.21.3 and Forge 1.8.9, and Typing on Forge 1.12.2.
