# Tested targets

Results of the harness suites (`shots`, `sky-sharp`, `chroma`, `typing`, `settings-pages`) run with `python -m tools harness` before a release; see [BUILDING.md](BUILDING.md). **All** means all five passed; **Shots** means only `shots` was run and passed. Runs of 2026-11-13 with the code of 0.7.0.

| Minecraft | Fabric | Quilt | NeoForge | Forge |
|-----------|--------|-------|----------|-------|
| 1.8.9     |        |       |          | All   |
| 1.12.2    |        |       |          | All   |
| 1.16.5    | All    | Shots |          | All   |
| 1.17.1    | All    | Shots |          | All   |
| 1.18.2    | All    | Shots |          | All   |
| 1.19.2    | All    | Shots |          | All   |
| 1.19.4    | All    | Shots |          | All   |
| 1.20.1    | All    | Shots |          | All   |
| 1.20.4    | All    | Shots | All      | All   |
| 1.20.6    | All    | Shots | All      |       |
| 1.21.1    | All    | Shots | All      |       |
| 1.21.3    | All    | Shots | All      |       |
| 1.21.4    | All    | Shots | All      |       |
| 1.21.5    | All    | Shots | All      |       |
| 1.21.8    | All    | Shots | All      |       |
| 1.21.10   | All    | Shots | All      |       |
| 1.21.11   | All    | Shots | All      |       |
| 26.1.2    | All    | Shots (not listed) | All |   |
| 26.2      | All    | Shots (not listed) | All |   |
| 26.3      | All    | Shots (not listed) | All |   |

Notes:

- 0.7.1 changes only the custom sky (`SkyDesign.drawnExtent` and the Dusk and Aurora presets). For it, `sky-turned` passed on Forge 1.8.9 and Fabric 26.3 with `skycompare` the same, and ran on 1.8.9 with OptiFine HD U M6 pre2; the table is still from 0.7.0.

- Vulkan backend: all five suites pass on 26.3 (Fabric and NeoForge). On 26.2, `sky-sharp` and `chroma` pass on both loaders; the other suites were not run on Vulkan.
- Quilt runs use Quilt Loader 0.30.2 with the node's Fabric release jar (`--loader quilt`). Quilt remaps that jar to the dev game's names, so it was not tested in a game with intermediary names. On 26.x, Quilt Loader does not call the jar's client entry point; Albedo still starts from its first hook during resource loading and everything works, but 26.x is not listed for Quilt because it depends on that fallback.
- On Quilt 1.18.2, 1.19.4, 1.20.1, 1.21.1, 1.21.11 and 26.3, `sky-sharp` also passed; `chroma` and `typing` were not run on Quilt.
- The 1.8.9 release jar passed `shots` and `sky-sharp` in a production Forge client with no OptiFine (`python -m tools optifine --variants none`). It was not run with OptiFine.
- The screenshots in `docs/validation/<target>/` are of the run of 2026-11-10 for 0.6.0.
- Passed on a second try: `sky-sharp` on Forge 1.16.5, after a fix of the window size of a hidden game on a narrow screen.

## Renderer mods

Dev runs of `python -m tools harness --compat <mod>` with the profile quake-white (2026-11-11). In every run the grass, leaves and vines have the profile color and the `sodiumTint` counter of the `hooks` line is above 0. `sodiumWater` is the water hook of Sodium 0.5 and its forks.

| Loader | Minecraft | Renderer mod | Tint | Water |
|--------|-----------|--------------|------|-------|
| Fabric | 1.16.5 | Sodium 0.2.0 | 278299 | |
| Fabric | 1.17.1 | Sodium 0.3.4 | 267800 | |
| Fabric | 1.18.2 | Sodium 0.4.1 | 282824 | |
| Fabric | 1.19.2 | Sodium 0.4.4 | 283886 | |
| Fabric | 1.19.4 | Sodium 0.4.10 | 284658 | |
| Fabric | 1.20.1 | Sodium 0.5.13 | 639460 | 864 |
| Fabric | 1.20.4 | Sodium 0.5.8 | 643139 | 960 |
| Fabric | 1.20.6 | Sodium 0.5.11 | 647210 | 864 |
| Fabric | 1.21.1 | Sodium 0.8.13 | 582116 | |
| Fabric | 1.21.3 | Sodium 0.6.13 | 580812 | |
| Fabric | 1.21.5 | Sodium 0.6.13 | 595993 | |
| Fabric | 1.21.8 | Sodium 0.7.3 | 594538 | |
| Fabric | 26.1.2 | Sodium 0.9.2 | 1576472 | |
| Fabric | 26.3 | Sodium 0.9.2 | 1594396 | |
| Forge | 1.16.5 | Embeddium 0.3.18 | 283945 | |
| Forge | 1.16.5 | Rubidium 0.2.13 | 274285 | |
| Forge | 1.18.2 | Embeddium 0.3.18 | 292055 | |
| Forge | 1.18.2 | Rubidium 0.5.6 | 282056 | |
| Forge | 1.19.2 | Embeddium 0.3.18.1 | 288005 | |
| Forge | 1.19.2 | Rubidium 0.6.2c | 282845 | |
| Forge | 1.19.4 | Rubidium 0.6.4 | 281563 | |
| Forge | 1.20.1 | Rubidium 0.7.1 | 565282 | 1056 |
| NeoForge | 1.20.4 | Embeddium 0.3.25 | 639460 | |
| NeoForge | 1.21.3 | Sodium 0.6.13 | 581788 | |

Not run: Fabric 1.21.4, 1.21.10, 1.21.11 and 26.2, and NeoForge from 1.21.1 except 1.21.3 (the hooks ran on the versions next to them). Not possible: Forge 1.17.1 and 1.20.4 (no Embeddium or Rubidium), NeoForge 1.20.6 and 1.21.4 with Embeddium and Forge 1.20.1 with Embeddium 0.3.31 (a mixin of Embeddium does not start in the dev run), and Embeddium on Fabric (not tried).
