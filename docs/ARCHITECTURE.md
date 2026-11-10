# Architecture

Albedo uses ports and adapters. The core has almost all of the code and no Minecraft types. Each Minecraft era has a small adapter.

| Module | Contents |
|--------|----------|
| `core/` | Plain Java 8, no Minecraft types. Config, `Policy`, texture classification and pixel math, the custom sky (bake and shaders), the settings screen (`ui/`) and the harness (`dev/Harness`). It compiles into every jar. It has unit tests. |
| `modern/` | 1.21.1, 1.21.11 and 26.3, Fabric and NeoForge, with Mojang names. Stonecutter 0.9, Architectury Loom 1.17, Gradle 9.8. `common/` has the adapter and the mixins. `fabric/` and `neoforge/` have only the entrypoint and the config button. |
| `legacy/` | 1.8.9 Forge, with MCP names. Essential Loom 0.10, Mixin 0.7.11, Gradle 8.8. Gradle runs on JDK 21, the game on Java 8. |

One tool chain cannot build both 1.8.9 Forge and 26.x, so there are two builds. Both compile `core/src`.

## Ports and adapters

- The core makes all decisions: config, groups, matching, colors, pixel math, presets, keys and config reload. `Policy` holds all render decisions from one config snapshot.
- `Platform` is the port for the game: config folder, log, resource reload, chunk rebuild, end of a repaint, messages, key state, clipboard, settings screen and harness host.
- `ui/Canvas` is the port for drawing: `width`, `height`, `fill`, `text`, `textWidth` and `image`. The settings screen is an immediate-mode UI in the core. The screen class of each version only forwards draws and input.
- Each adapter (`ModernPlatform`, `LegacyPlatform`) implements the ports, puts pixels in a `PixelBuffer` (`NativeImagePixels`, `BufferedImagePixels`) and sends plain values from the mixins to `ModernHooks` or `LegacyHooks`. A mixin handler has no game logic.
- The mod uses no loader API. Mixins do the tick, keys and config work, so Fabric does not need Fabric API.

## Hooks

The adapters change data at stable points, not in the renderers. The core processes decoded pixels, so the source of a texture (vanilla, resource pack or mod) is not important.

| Point | Modern hook | 1.8.9 hook |
|-------|-------------|------------|
| Atlas sprites, before mipmaps | `SpriteLoader.stitch` | `TextureAtlasSprite.loadSprite` |
| Other textures | `TextureContents.load` (1.21.4+), `SimpleTexture.doLoad` | `SimpleTexture.loadTexture` |
| Downloaded skins, capes, elytra | `TextureManager.register` of a `DynamicTexture` (1.21.4+), `HttpTexture.load` | `ThreadDownloadImageData.setBufferedImage` |
| Biome colormaps | `GrassColor`, `FoliageColor`, `DryFoliageColor` `init` | `ColorizerGrass`, `ColorizerFoliage` |
| Block tint | `ModelBlockRenderer`; Sodium and Embeddium `BlockRenderer` | `BlockModelRenderer`, Forge `BlockInfo` |
| Water tint | `BiomeColors.getAverageWaterColor` | `BiomeColorHelper.getWaterColorAtPos` |
| Fullbright | gamma in `LightTexture`, 26.x `LightmapRenderStateExtractor` | gamma in `EntityRenderer.updateLightmap` |
| Smooth lighting | `Minecraft.useAmbientOcclusion`, 26.x `ModelBlockRenderer`, Sodium `LevelSlice` | `Minecraft.isAmbientOcclusionEnabled` |
| Face shade | `ClientLevel.getShade`, 26.x `CardinalLighting.byFace` | quads into the chunk mesh (`LegacyHooks.addQuad`, `vertexColor`) |
| Entity shadows | `EntityRenderer`, before 1.21.2 `EntityRenderDispatcher` | `Render.doRenderShadowAndFire` |
| Sky and fog color | `LevelRenderer` sky pass, `FogRenderer` | `RenderGlobal.renderSky`, `EntityRenderer.updateFogColor` |
| Custom sky | 1.21.11 and 26.x `SkyRenderer`; 1.21.1 `LevelRenderer.renderSky` | `RenderGlobal.renderSky` (`LegacySkyPatch`) |
| Clouds | `CloudRenderer.prepare` | - |
| Tick | `Minecraft.tick` | `Minecraft.runTick` |

- The Sodium and Embeddium hooks are `@Pseudo` mixins with `remap = false` and `require = 0`. They use internal names of these mods, so a new version can silently skip them. Test each new version with `-Pcompat`.
- Foliage has no hook. The texture hooks set the alpha of plant textures to zero.

## Settings changes

A change (screen, key, harness or config file) goes to `Albedo.apply`. It saves the config, makes a new `Policy` and calls `Albedo.refresh`:

1. Sky, fog, fullbright, shadows and clouds read the policy in each frame.
2. `TextureStore.repaint` makes new pixels from the original of each texture whose look changed, and calls its `Upload`.
3. `Platform.finishRepaint` sends the collected uploads to the GPU.
4. If tints or shading change, `Platform.rebuildChunks` rebuilds the chunk meshes.
5. Only if an upload fails or the store is over its memory budget, `Platform.reloadResources` reloads all resources.

Traps:

- A resource reload replaces sprites and textures. Each upload first finds the current object. If it is not the object that the hook saw, the upload returns false and the store forgets the texture.
- `TextureStore` keeps textures by name, because some textures share a key (OptiFine connected textures).
- Downloaded skins do not count toward the memory budget, and a failed upload does not reload resources, because a reload cannot load them again.
- A look gives still textures only. A repaint of the block atlas takes about 20 ms, which is too slow for each frame.
- A sky edit does not repaint textures (`Policy.sameTextures`).
- On 1.8.9, `rebuildChunks` uses `markBlockRangeForRenderUpdate`, not `loadRenderers`: with Patcher, Essential or OptiFine L5, chunks can stay empty after `loadRenderers`.

## Custom sky

`sky/SkyGenerator` bakes a `SkyDesign` into an equirectangular `SkyImage` on the CPU (`SkyBaker`, in the background). `sky/SkyProgram` puts the layer numbers into one block of floats. The sky shader (`core/src/main/resources/assets/albedo/shaders`) reads the same block and draws each screen pixel ("Sharp render"). The baked texture is the fallback.

- Each sky gets its own shader. `SkyShaderSource.layerSteps` writes `ALBEDO_LAYER_STEPS`: a direct call of the function of the kind of each layer, with its blend, mask and parts (twinkle, trails, spikes, pulse, dust, jets) as constants. The values stay in the block, so a shader compiles again only when the steps change. Each adapter keeps the shaders of the last skies (`SkyShaderCache`).
- The sky shader draws only the pixels that the world does not hide. In the sky pass the adapter clears the frame to black, and the vanilla sun, moon and stars draw on it. After the solid world, the sky sphere draws at the far depth with a depth test and adds its color to the frame. The sum is the same as a sky with the sun, the moon and the stars on it. The hook after the solid world is `SkyAfterSolidMixin` (26.x `LevelRenderer.executeSolid`, 1.21.11 `ChunkSectionsToRender.renderGroup`, 1.21.1 `LevelRenderer.renderSectionLayer`) and `RenderGlobal.renderBlockLayer` on 1.8.9. Until this hook runs for the first time, the sphere draws in the sky pass, so a renderer mod that removes the hook only loses the speed. The baked texture always draws in the sky pass.
- The term texture also holds field images: the smooth noise of each cloud and fractal layer (cloud value, bend of the ridges, pulse noise) that the bake writes at the texel centers. The shader reads them with linear filters, and calculates only the sharp ridges of a fractal for each pixel.

Invariants:

- The CPU bake and the GPU shader must give the same sky. They use the same integer hashes and noise. Java copies of the shader parts (`SkyStars`, `SkyMeteors`, `Aurora`) must change together with the GLSL. The harness command `skycompare` draws the shader sky into an image and logs `DIFFERENT` when its mean OKLab distance from the bake is at or above `Harness.COMPARE_LIMIT`. Run it after each sky change.
- `SkyGeneratorTest.thePresetsKeepTheirPixels` keeps a hash of the bake of each preset. If a change of the pixels is intentional, update the hashes in the same commit. Else, find the change.
- One shared clock, `sky/SkyClock`, drives all layers that move. `seconds()` wraps each hour, so a float keeps its precision; a wave must repeat a whole number of times in the hour. `longSeconds()` has no wrap, for the turning. The bake, the preview and `skycompare` use the time 0 (`SkyProgram.STILL`). Meteors draw only in the shader.
- A design has a maximum of `SkyProgram.MAX_TERMS` term filters (blur, bloom, pixelate, crystallize, swirl). A design with more draws from the baked image. Field images use the free slots up to `SkyProgram.MAX_SLOTS`; a cloud or fractal layer with no slot calculates its noise for each pixel.
- Do not let a layer step dispatch on its kind at run time. The NVIDIA compiler inlines before it removes the dead kinds, and with seven or more such steps it stops to inline and the sky gets about 60 percent slower. Do not index a constant array with a value that changes for each pixel: the compiler puts the array in local memory for each pixel. Measure a shader change with `skytime 3840 2160` on 26.3; the shader of 26.x goes through SPIR-V, so its speed can differ from the GL programs of the other versions.
- A change of only the render mode or the turning does not bake again (`SkyDesign.baked`), except the star trails, which the bake draws.

Traps per version:

- 26.x: a texture write is not allowed in a render pass, so uploads go before the sky pass.
- 26.x: the shader source is read and its includes are inlined on the render thread (`SkyShaderSource.inline`) before the compile goes to a background thread. If shaderc resolves an include, it calls back into Java. On Windows with Java 25 that call can write below the guard page of the deep worker stack, and the JVM stops with no `hs_err` file. The baked texture pipeline compiles on the render thread.
- 26.x: `SharpSky` has its own shader source, because Fabric without Fabric API does not load mod assets.
- 1.21.11: `PrecompiledSharpSky` precompiles before each sky pass, because a resource reload empties the pipeline cache. The game has no half float format, so the term texture is written again through the GL after a one-texel `writeToTexture` that binds it. Do not cast a `GpuTexture` to the GL class: the NeoForge validation layer gives a wrapper.
- 1.21.1 (`GlSharpSky`) and 1.8.9 (`LegacySharpSky`) compile their own GL program. 1.8.9 uses GLSL 1.30, else GLSL 1.20 with EXT_gpu_shader4, else the baked image. A failed compile logs one warning and draws the baked image.
- 1.8.9: below the cloud height the clouds draw before the world and blend with the sky, so the sky sphere draws before them (`RenderGlobal.renderClouds`) and the world hides nothing from the shader. With clouds off, or above the cloud height, the sphere draws after the solid world.
- Do not draw a thing that blends with the sky, or that has no depth test, between the sky pass and the solid world: it gets a black background.
- On 1.8.9 with Use VBOs on, vanilla binds its sky buffer before the sky hook, and LWJGL refuses a draw from client memory while a buffer is bound. `LegacySky.drawSky` unbinds it while the custom sky draws. The dev run has VBOs off, so test this case with `useVbo:true` in `legacy/run/options.txt`.

## OptiFine on 1.8.9

- Each OptiFine version uses different method names in `BlockModelRenderer`, `BlockFluidRenderer` and `FaceBakery`. `MixinPlugin` finds OptiFine and selects the mixins: `*VanillaMixin` only without OptiFine, `OptiFine*Mixin` only with it. `OptiFineRenderPatch` finds the tint and brightness calls by target and descriptor, not by name.
- `LegacySkyPatch` finds each draw in `RenderGlobal.renderSky` by the field that it reads, not by order, because OptiFine adds draws. If it does not find all parts, it logs a warning and the sky stays vanilla.
- With OptiFine Sky off or an OptiFine shader pack, the sky stays vanilla.
- The classifier gives OptiFine textures (`optifine/ctm/`, `mcpatcher/ctm/`, `cit/`, `sky/`, `random/entity/`) the category of the vanilla texture that they replace.
- Tested only with OptiFine HD U M6 pre2 and HD U L5 (`tools/optifine.ps1`). OptiFine shaders are not tested.

## Build

You need JDK 21 and JDK 25. If JDK 8 is missing, Gradle gets it for 1.8.9.

```bash
cd modern && ./gradlew collectJars   # Fabric and NeoForge jars -> modern/build/libs/
cd legacy && ./gradlew build         # 1.8.9 Forge jar -> legacy/build/libs/
```

- Dev client: `./gradlew :fabric:26.3:runClient` in `modern/` (any loader or version), `./gradlew runClientDev` in `legacy/`.
- Add `-Pcompat=sodium`, `embeddium` or `iris` to a modern `runClient` to add that mod to the dev run. The versions are in `modern/versions/<v>/gradle.properties`. Iris works only on Fabric 26.3 and Fabric 1.21.11; on the other targets you get only Sodium.
- In `modern/`, Stonecutter comments (`//? if >=26.1 {` ... `//?} else {` ... `//?}`) mark the version differences. They are only in mixin and adapter files, never in the core.
- Releases: see [RELEASING.md](RELEASING.md).

## Add a Minecraft version (modern)

1. Add `modern/versions/<v>/gradle.properties`, and `modern/neoforge/versions/<v>/gradle.properties` with `loom.platform=neoforge`.
2. Add `<v>` to the `versions` list in `modern/settings.gradle`.
3. Run `./gradlew :fabric:<v>:build :neoforge:<v>:build` in `modern/`. The compile errors show the hooks that moved. Fix each one with a `//? if` branch.
4. Examine the condition boundaries (`>=1.21.2`, `>=1.21.4`, ... `>=26.1`). The project tests them only on the built versions.
5. Run `./gradlew :fabric:<v>:runClient`. The log must show `Albedo ready` and `Flattened N textures`.

## Check and test

- Before you push: `./gradlew :core:check` in `modern/` (compile, Checkstyle, tests) and `bash tools/lint.sh` in the repository root. See [CODING_STANDARDS.md](CODING_STANDARDS.md).
- `tools/screenshots.ps1` runs each target on a hidden desktop through `core/dev/Harness`. Do not start a visible game for tests. Useful parameters: `-Targets`, `-Shots <file>` (a command file in `tools/harness/`), `-Extra`, `-OutDir`, `-Compat`, `-ShaderPack`. A `*-modern.txt` file has a `*-legacy.txt` copy for 1.8.9, which the script takes.
- Key harness commands: `sky <preset> [layers]`, `skycompare`, `skytime [width height [yaw pitch fov]]` (GPU time of the sharp sky in a view full of sky), `fps [seconds]` (frame rate and CPU time of the sky), `window <width> <height>`, `skyclock <s>|run` and `skypartial` (hold the time for screenshots), `fov <degrees>`, `ui click <label>`, `ui slide <percent> <id>`, `quit`.
- `ui realclick` tests the input path of the adapter. Only the adapter knows the number of the left mouse button (26.x uses 1). 1.8.9 cannot do `realclick`, because LWJGL 2 reads the mouse from the system.
- After `quit`, the modern JVM stops when the game thread ends, because the Architectury dev run keeps non-daemon thread pools. Without this, 26.x writes a "Client shutdown from post-main" crash report.

## Known gaps

- Clouds on 1.21.2 and later support only `original` and `hidden`.
- `core/texture/Foliage` finds plants by texture name. A modded plant with a different name stays visible.
