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
| Hidden clouds | `Options.getCloudsType`, 26.x `getCloudStatus` | the clouds texture |
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
- A repaint of the block atlas takes about 20 ms, which is too slow for each frame. Moving Chroma colors use a GPU pass (see Animated Chroma).
- A sky edit does not repaint textures (`Policy.sameTextures`).
- Do not read the policy in a resource reload listener, for example `CloudRenderer.prepare`. A settings change does not reload resources, so the game shows the change only after the next reload.
- On 1.8.9, `rebuildChunks` uses `markBlockRangeForRenderUpdate`, not `loadRenderers`: with Patcher, Essential or OptiFine L5, chunks can stay empty after `loadRenderers`.

## Animated Chroma

Albedo does not own the block shader (vanilla, Sodium, Iris, OptiFine, the fixed pipeline of 1.8.9), but all renderers sample the atlas. Thus a GPU pass changes the colors of the atlas texture itself in each frame.

- The CPU paints a sprite with moving Chroma colors without the hue (`Effects.apply` with `gpuHue`). Only atlas sprites that the store keeps get this. All other textures get still Chroma colors at the time 0, and the Effects section tells when only some textures of a category move (`TextureStore.stillChroma`).
- `texture/ChromaTexels` gives each texel one RGBA8 texel: speed in alpha, hue in red and green, OKLab chroma in blue. `ChromaTexels.Atlas` places them in the atlas and makes the mip levels.
- The adapter (`ChromaAtlases` with `ChromaPass` on 26.x, `PrecompiledChromaPass` on 1.21.11, `GlChromaPass` on 1.21.1, `LegacyChroma` on 1.8.9) hooks the start and the end of each frame. At the start it copies each mip level into a pristine copy and draws the hue of the frame over it (`chroma.fsh`, `chroma_classic.fsh`, `include/chroma.glsl`). At the end it copies the pristine copy back. A texel with speed 0 is discarded.
- The turn of each speed comes from `Chroma.turns` with `SkyClock.longSeconds()` in double precision, so each frame has its own colors.
- At speed 100 a frame of 8 ms turns the hue by less than one 8-bit step. The pass adds a 16 x 16 ordered dither below one step at each texel, so that a part of a flat sprite moves in each frame.

Traps:

- Between frames the atlas has the painted colors, so the game and Albedo always write into them. A repaint or a reload in the middle of a frame first copies the pristine copy back (`restore`). If the end of the last frame did not run, the start first gives back the painted colors, so the hue never goes into the pristine copy.
- The texels are made again only when a texture with moving colors changes (`Albedo.texturePaints`) or an atlas gets a new sprite map (1.21.1 uploads a reloaded atlas of the same size into the same GL texture). A change of only the speed changes only the texels.
- 1.21.11 and later draw each item icon once into an item atlas. While colors move, each item icon counts as animated (`ItemIconMixin`).
- 1.8.9 with OptiFine Multitexture on draws blocks from a texture of each sprite, so the pass stops and the sprites get still colors.
- On 26.x the pipeline compiles on the render thread from the inlined text. On 1.21.11 never cast a GPU texture. On 1.21.1 and 1.8.9 the pass puts back each GL state that it changes. On 1.8.9 it pushes and pops the GL attributes while its own framebuffer is bound, because OptiFine Fast Render draws with no framebuffer and the draw buffer `GL_BACK` is not valid for a framebuffer. The memory is two times the atlas with its mip levels (42.6 MiB for the 2048 x 2048 block atlas).
- If the shader does not compile or a GPU call fails, the adapter logs one warning and calls `Albedo.chromaGpu(false)`. The core paints still Chroma colors again.

## Custom sky

`sky/SkyGenerator` bakes a `SkyDesign` into an equirectangular `SkyImage` on the CPU (`SkyBaker`, in the background). `sky/SkyProgram` puts the layer numbers into one block of floats. The sky shader (`core/src/main/resources/assets/albedo/shaders`) reads the same block and draws each screen pixel ("Sharp render"). The baked texture is the fallback.

- Each sky gets its own shader. `SkyShaderSource.layerSteps` writes `ALBEDO_LAYER_STEPS`: a direct call of the function of the kind of each layer, with its blend, mask and parts (twinkle, trails, spikes, pulse, dust, jets) as constants. The values stay in the block, so a shader compiles again only when the steps change. Each adapter keeps the shaders of the last skies (`SkyShaderCache`), and 1.21.11 keeps them all (see the traps).
- The sky shader draws only the pixels that the world does not hide. In the sky pass the adapter draws black on the sphere at the far depth with a depth test and no blend, so that only the pixels that still have the far depth of the clear become black (a portal mod that draws a second view of the level into the frame keeps the rest of the frame). The vanilla sun, moon and stars draw on the black. After the solid world, the sky sphere draws at the far depth with a depth test and adds its color to the frame. The sum is the same as a sky with the sun, the moon and the stars on it. The hook after the solid world is `SkyAfterSolidMixin` (26.x `LevelRenderer.executeSolid`, 1.21.11 `Lighting.setupFor` for the level, which follows the solid terrain also when Sodium draws it, 1.21.1 `LevelRenderer.renderSectionLayer`) and `RenderGlobal.renderBlockLayer` on 1.8.9. The baked texture always draws in the sky pass.
- `sky/SkyDefer` decides where the sphere draws. The sphere waits for the hook only when the hook ran in the frame before, else it draws in the sky pass. Thus a renderer mod that removes the hook only loses the speed, and a frame that skips the hook gives at most one frame of black sky. A sphere that waits goes at the start of each frame, so a frame with no sky pass (lava, powder snow, blindness, darkness) draws no sky after the solid world.
- The term texture also holds field images: the smooth noise of each cloud and fractal layer that the bake writes at the texel centers. A cloud field holds only the coarse octaves of the cloud value (`SkyGenerator.CLOUD_FIELD_OCTAVES`), because the fine octaves are smaller than a texel at the half size of the bake. The shader reads the fields with linear filters, and calculates the fine cloud octaves and the sharp ridges of a fractal for each pixel. A sky that cannot draw sharp gets no field images.

Invariants:

- The CPU bake and the GPU shader must give the same sky. They use the same integer hashes and noise. Java copies of the shader parts (`SkyStars`, `SkyMeteors`, `Aurora`) must change together with the GLSL. The harness command `skycompare` draws the shader sky into an image and logs `DIFFERENT` when its mean OKLab distance from the bake is at or above `Harness.COMPARE_LIMIT`. Run it after each sky change.
- `SkyGeneratorTest.thePresetsKeepTheirPixels` keeps a hash of the bake of each preset. If a change of the pixels is intentional, update the hashes in the same commit. Else, find the change.
- One shared clock, `sky/SkyClock`, drives all layers that move. `seconds()` wraps each hour, so a float keeps its precision; a wave must repeat a whole number of times in the hour. `longSeconds()` has no wrap, for the turning. The bake, the preview and `skycompare` draw a still sky (`SkyProgram.STILL`). Meteors draw only in the shader. The meteor hashes use the slot modulo 3600, so a meteor in flight goes on across the wrap.
- The aurora has its own clock (`Aurora.clock`): `longSeconds()` times 2 times its speed, in double precision on the CPU, with a wrap at `Aurora.PERIOD` (7200 s). Each aurora wave and the ray noise repeat a whole number of times in this period.
- The lower edge, the height and the presence of an aurora curtain are functions of the azimuth of the view, so each curtain has one smooth lower edge, also where its sheet folds. The folded sheet gives only the rays and their light.
- For each frame of a sky that moves or turns, the adapter writes the block from `SkyProgram.at` (time, aurora clocks and the turn in `SkyWorld`). A still sky that does not turn keeps `SkyProgram.block`, so `skycompare` and the bake agree.
- The meteors and the aurora are in the air, so their layer steps take the world direction (`world` in `albedoSky`) and they do not turn with the sky. The bake does not know the turn, so the baked texture turns its aurora with the sky.
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
- Known limit: other mods can draw in this gap, for example at the NeoForge stages `AFTER_SKY`, `AFTER_SOLID_BLOCKS` and `AFTER_CUTOUT_MIPPED_BLOCKS`. A translucent thing that such a mod draws there over the sky blends with black.
- 26.x: the sky pass prepares the custom sky only for the overworld skybox, the only one that it replaces. A pipeline that closes while it compiles frees its result on the render thread when the compile ends.
- 1.21.11: the device frees a compiled pipeline only when a resource reload empties its whole cache. Thus each sky keeps its pipeline and its id by its layer steps, and a sky that comes back compiles nothing.
- 1.21.1 and 1.8.9 draw the black sphere with the shader of the game at the depth range 1 to 1. The game never changes the depth range.
- On 1.8.9 with Use VBOs on, vanilla binds its sky buffer before the sky hook, and LWJGL refuses a draw from client memory while a buffer is bound. `LegacySky.drawSky` unbinds it while the custom sky draws. The dev run has VBOs off, so test this case with `useVbo:true` in `legacy/run/options.txt`.

## OptiFine on 1.8.9

- Each OptiFine version uses different method names in `BlockModelRenderer`, `BlockFluidRenderer` and `FaceBakery`. `MixinPlugin` finds OptiFine and selects the mixins: `*VanillaMixin` only without OptiFine, `OptiFine*Mixin` only with it. `OptiFineRenderPatch` finds the tint and brightness calls by target and descriptor, not by name.
- `LegacySkyPatch` finds each draw in `RenderGlobal.renderSky` by the field that it reads, not by order, because OptiFine adds draws. If it does not find all parts, it logs a warning and the sky stays vanilla.
- With OptiFine Sky off or an OptiFine shader pack, the sky stays vanilla.
- With OptiFine Fast Render, a `Framebuffer` of the game binds nothing (`OpenGlHelper.isFramebufferEnabled()` is false), so `skycompare` and `skytime` use a framebuffer of their own.
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
2. Add `<v>` to the `versions` list in `modern/settings.gradle` and a line to the `modern` matrix in `.github/workflows/ci.yml`.
3. Run `./gradlew :fabric:<v>:build :neoforge:<v>:build` in `modern/`. The compile errors show the hooks that moved. Fix each one with a `//? if` branch.
4. Examine the condition boundaries (`>=1.21.2`, `>=1.21.4`, ... `>=26.1`). The project tests them only on the built versions.
5. Run `./gradlew :fabric:<v>:runClient`. The log must show `Albedo ready` and `Flattened N textures`.

## Check and test

- Before you push: `./gradlew :core:check` in `modern/` (compile, Checkstyle, tests) and `bash tools/lint.sh` in the repository root. See [CODING_STANDARDS.md](CODING_STANDARDS.md).
- `tools/screenshots.ps1` runs each target on a hidden desktop through `core/dev/Harness`. Do not start a visible game for tests. Useful parameters: `-Targets`, `-Shots <file>` (a command file in `tools/harness/`), `-Extra`, `-OutDir`, `-Compat`, `-ShaderPack`, `-Config <file>` (a config.json for the start in place of the default config). A command file runs on all targets, except that 1.8.9 runs the `*-legacy.txt` copy of a `*-modern.txt` file.
- With no `-Shots`, the script writes `docs/screenshots/<target>/` from `shots.txt`: one image for each feature, to find a version where a feature does not work. Make them again when a change affects what a player sees.
- Key harness commands: `sky <preset> [layers]` (a preset with the default turning stands still until `skyturn`), `skycompare`, `skytime [width height [yaw pitch fov]]` (GPU time of the sharp sky in a view full of sky), `fps [seconds]` (frame rate), `window <width> <height>`, `skyclock <s>|run` and `skypartial` (hold the time for screenshots), `fov <degrees>`, `clouds shown|hidden` (logs `! clouds` when the next frame does not draw the clouds as expected; `clouds-modern.txt` tests each state of the config), `ui click <label>`, `ui slide <percent> <id>`, `quit`.
- Chroma harness commands (`tools/harness/chroma.txt`): `effects <category> [bevel N] [outline N] [chroma N] [speed N] [saturation N] [brightness N]`, `frames <count> <name>` (a screenshot at the end of each frame), `chromagpu off|on` (compare the GPU colors with the still CPU colors at the time 0) and `chromatime [frames]` (GPU and CPU time and GPU memory of the pass).
- `ui realclick` tests the input path of the adapter. Only the adapter knows the number of the left mouse button (26.x uses 1). 1.8.9 cannot do `realclick`, because LWJGL 2 reads the mouse from the system.
- After `quit`, the modern JVM stops when the game thread ends, because the Architectury dev run keeps non-daemon thread pools. Without this, 26.x writes a "Client shutdown from post-main" crash report.

## Known gaps

- Clouds on 1.21.2 and later support only `original` and `hidden`.
- `core/texture/Foliage` finds plants by texture name. A modded plant with a different name stays visible.
