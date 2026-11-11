# Tested targets

This file has one row for each of the 39 jars. A row is filled by the validation run before a release. An empty cell means that the run did not record a result yet.

## Columns

- **Tier:** the features that the code of the target has (see below).
- **Dev run:** `PASS` or `FAIL` from `result.txt` of `tools/screenshots.ps1` for the target.
- **Release jar:** `started` when the release jar (not the dev client) started and opened the settings screen, or `not started`.
- **Date:** the date of the run, as `YYYY-MM-DD`.
- **Notes:** a fallback that the run saw, or a failure.

## Tiers

- **Full:** the sharp sky draws after the solid world, and the Chroma colors move on the GPU.
- **Sky pass:** as Full, but the sky draws in the sky pass because the version has no hook after the solid world. This costs speed and gives the same picture.
- **Full, OpenGL only:** as Full on the OpenGL backend. With the Vulkan backend of 26.2 the sky draws from a baked image.

## Fabric

| Loader | Minecraft | Tier | Dev run | Release jar | Date | Notes |
|--------|-----------|------|---------|-------------|------|-------|
| Fabric | 1.16.5 | Sky pass | | | | |
| Fabric | 1.17.1 | Sky pass | | | | |
| Fabric | 1.18.2 | Sky pass | | | | |
| Fabric | 1.19.2 | Sky pass | | | | |
| Fabric | 1.19.4 | Full | | | | |
| Fabric | 1.20.1 | Full | | | | |
| Fabric | 1.20.4 | Full | | | | |
| Fabric | 1.20.6 | Full | | | | |
| Fabric | 1.21.1 | Full | | | | |
| Fabric | 1.21.3 | Full | | | | |
| Fabric | 1.21.4 | Full | | | | |
| Fabric | 1.21.5 | Full | | | | |
| Fabric | 1.21.8 | Full | | | | |
| Fabric | 1.21.10 | Full | | | | |
| Fabric | 1.21.11 | Full | | | | |
| Fabric | 26.1.2 | Full | | | | |
| Fabric | 26.2 | Full, OpenGL only | | | | |
| Fabric | 26.3 | Full | | | | |

## Forge

| Loader | Minecraft | Tier | Dev run | Release jar | Date | Notes |
|--------|-----------|------|---------|-------------|------|-------|
| Forge | 1.8.9 | Full | | | | |
| Forge | 1.12.2 | Full | | | | |
| Forge | 1.16.5 | Sky pass | | | | |
| Forge | 1.17.1 | Sky pass | | | | |
| Forge | 1.18.2 | Sky pass | | | | |
| Forge | 1.19.2 | Sky pass | | | | |
| Forge | 1.19.4 | Full | | | | |
| Forge | 1.20.1 | Full | | | | |
| Forge | 1.20.4 | Full | | | | |

## NeoForge

| Loader | Minecraft | Tier | Dev run | Release jar | Date | Notes |
|--------|-----------|------|---------|-------------|------|-------|
| NeoForge | 1.20.4 | Full | | | | |
| NeoForge | 1.20.6 | Full | | | | |
| NeoForge | 1.21.1 | Full | | | | |
| NeoForge | 1.21.3 | Full | | | | |
| NeoForge | 1.21.4 | Full | | | | |
| NeoForge | 1.21.5 | Full | | | | |
| NeoForge | 1.21.8 | Full | | | | |
| NeoForge | 1.21.10 | Full | | | | |
| NeoForge | 1.21.11 | Full | | | | |
| NeoForge | 26.1.2 | Full | | | | |
| NeoForge | 26.2 | Full, OpenGL only | | | | |
| NeoForge | 26.3 | Full | | | | |
