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
