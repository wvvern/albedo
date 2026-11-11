# Tested targets

Results of the harness suites (`shots`, `sky-sharp`, `chroma`, `typing`, `settings-pages`) run with `tools/screenshots.ps1` before a release; see [BUILDING.md](BUILDING.md). **All** means all five passed; **Shots** means only `shots` was run and passed. Runs of 2026-11-10, Quilt and Vulkan of 2026-11-11.

| Minecraft | Fabric | Quilt | NeoForge | Forge |
|-----------|--------|-------|----------|-------|
| 1.8.9     |        |       |          | All   |
| 1.12.2    |        |       |          | All   |
| 1.16.5    | All    | Shots |          | All   |
| 1.17.1    | All    | Shots |          | All   |
| 1.18.2    | All    | All   |          | All   |
| 1.19.2    | All    | Shots |          | All   |
| 1.19.4    | All    | All   |          | All   |
| 1.20.1    | All    | All   |          | All   |
| 1.20.4    | All    | Shots | All      | All   |
| 1.20.6    | All    | Shots | All      |       |
| 1.21.1    | All    | All   | All      |       |
| 1.21.3    | All    | Shots | All      |       |
| 1.21.4    | All    | Shots | All      |       |
| 1.21.5    | All    | Shots | All      |       |
| 1.21.8    | All    | Shots | All      |       |
| 1.21.10   | All    | Shots | All      |       |
| 1.21.11   | All    | All   | All      |       |
| 26.1.2    | All    | Shots (not listed) | All |   |
| 26.2      | All    | Shots (not listed) | All |   |
| 26.3      | All    | All (not listed)   | All |   |

Notes:

- Vulkan backend: all five suites pass on 26.3 (Fabric and NeoForge). On 26.2, `skycompare` matches on both loaders and `chromacompare` on Fabric; the other suites were not run on Vulkan.
- Quilt runs use Quilt Loader 0.30.2 with the node's Fabric release jar (`-Loader quilt`). Quilt remaps that jar to the dev game's names, so it was not tested in a game with intermediary names. On 26.x, Quilt Loader does not call the jar's client entry point; Albedo still starts from its first hook during resource loading and everything works, but 26.x is not listed for Quilt because it depends on that fallback.
- Screenshots and `result.txt` of each target are in `docs/validation/<target>/`.
- Passed on a second try: Chroma on Fabric 1.17.1, Fabric 1.21.3 and Forge 1.8.9; Typing on Forge 1.12.2.

## Renderer mods

Dev runs of `tools/screenshots.ps1 -Compat <mod>` with the profile quake-white (2026-11-11). In every run the grass, leaves and vines have the profile color and the `sodiumTint` counter of the `hooks` line is above 0. Before the hook, the counter was 0 on 1.16.5, 1.19.4 and 1.20.6 and the leaves stayed green. `sodiumWater` is the water hook of Sodium 0.5 and its forks; before it the water stayed blue.

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
