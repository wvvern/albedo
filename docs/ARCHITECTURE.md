# Architecture

Albedo is ports and adapters. The core holds almost all the code and no Minecraft types; each Minecraft era has a small adapter. To build and test, see [BUILDING.md](BUILDING.md).

| Module | Contents |
|--------|----------|
| `core/` | Plain Java 8. Config, `Policy`, texture classification and pixel math, the custom sky (bake and shaders), the settings screen (`ui/`) and the test harness (`dev/Harness`). Compiled into every jar. Unit tests. |
| `modern/` | 1.16.5 to 26.3 on Fabric, NeoForge and Forge (up to 1.20.4), Mojang names, Stonecutter. `common/` has the adapter and the mixins; the loader folders have only the entrypoint and the config button. |
| `legacy/` | 1.8.9 and 1.12.2 on Forge, MCP names. Shared code in `legacy/src`; per version, `legacy/versions/<mc>/` holds `Game` (static wrappers for renamed game members), `SpriteUpload` and the mixins whose targets changed. |

No single toolchain builds both 1.8.9 Forge and 26.x, so there are two Gradle builds. Both compile `core/src`.

## Ports and adapters

- The core makes every decision: config, groups, matching, colors, pixel math, presets, keys, config reload. `Policy` holds all render decisions for one config snapshot.
- `Platform` is the port to the game (config folder, log, resource reload, chunk rebuild, repaint upload, messages, keys, clipboard, settings screen, harness host).
- `ui/Canvas` is the port for drawing (`fill`, `text`, `image`, sizes). The settings screen is an immediate-mode UI in core; each version's screen class only forwards draws and input.
- Each adapter (`ModernPlatform`, `LegacyPlatform`) implements the ports, wraps pixels in a `PixelBuffer`, and its mixins call `ModernHooks` or `LegacyHooks`.
- No loader API is used. Mixins drive tick, keys and config, so Fabric API is not needed.
- The build generates the mixin list of each jar from the compiled classes in the `mixin` package whose names end in `Mixin`, `Accessor` or `Invoker`. Do not list mixins by hand, and do not put other classes in that package; `tools/check-jar.sh` fails the jar.

## Hooks

The adapters change data at stable points, not inside the renderers. The core works on decoded pixels, so it does not matter whether a texture comes from vanilla, a resource pack or a mod.

| Point | Modern hook | 1.8.9 and 1.12.2 hook |
|-------|-------------|------------|
| Atlas sprites, before mipmaps | `SpriteLoader.stitch`, 1.16.5 to 1.19.2 the `TextureAtlasSprite` constructor call in `TextureAtlas.load` | `TextureAtlasSprite.loadSprite`, 1.12.2 `loadSpriteFrames` |
| Other textures | `TextureContents.load` (1.21.4+), `SimpleTexture.doLoad` | `SimpleTexture.loadTexture` |
| Downloaded skins, capes, elytra | `TextureManager.register` of a `DynamicTexture` (1.21.4+), `HttpTexture.load` | `ThreadDownloadImageData.setBufferedImage` |
| Biome colormaps | `GrassColor`, `FoliageColor`, `DryFoliageColor` `init` | `ColorizerGrass`, `ColorizerFoliage` |
| Block tint | `ModelBlockRenderer`; Forge 1.16.5 `BlockInfo`; Sodium and Embeddium `BlockRenderer` | `BlockModelRenderer` (1.12.2 `BlockColors.colorMultiplier` in it), Forge `BlockInfo` |
| Water tint | `BiomeColors.getAverageWaterColor` | `BiomeColorHelper.getWaterColorAtPos` |
| Fullbright | gamma in `LightTexture`, 26.x `LightmapRenderStateExtractor` | gamma in `EntityRenderer.updateLightmap` |
| Smooth lighting | `Minecraft.useAmbientOcclusion`, 26.x `ModelBlockRenderer`, Sodium `LevelSlice` | `Minecraft.isAmbientOcclusionEnabled` |
| Face shade | `ClientLevel.getShade`, Forge 1.16.5 `LightUtil.diffuseLight`, 26.x `CardinalLighting.byFace` | quads into the chunk mesh (`Game.addQuad`, `LegacyHooks.vertexColor`) |
| Entity shadows | `EntityRenderer`, before 1.21.9 `EntityRenderDispatcher` | `Render.doRenderShadowAndFire` |
| Sky and fog color | `LevelRenderer` sky pass, `FogRenderer` (1.21.2 to 1.21.5 the static `computeFogColor`, `StaticFogMixin`) | `RenderGlobal.renderSky`, `EntityRenderer.updateFogColor` |
| Custom sky | `SkyRenderer` from 1.21.2 (`GlSkyLayersMixin` to 1.21.5, `SkyLayersMixin` from 1.21.6); 1.21.1 `LevelRenderer.renderSky`; 1.16.5 `LevelRenderer.renderSky` (`FixedSkyMixin`) | `RenderGlobal.renderSky` (`LegacySkyPatch`) |
| Hidden clouds | `Options.getCloudsType`, 26.x `getCloudStatus` | the clouds texture |
| Tick | `Minecraft.tick` | `Minecraft.runTick` |

- The Sodium and Embeddium hooks are `@Pseudo` mixins with `remap = false` and `require = 0`. They use internal names of these mods, so a new version can silently skip them. Test each new version with `-Pcompat`.
- Foliage has no hook: the texture hooks make plant textures transparent. `core/texture/Foliage` finds plants by texture name, so a modded plant with another name stays visible.
- An optional (`require = 0`) hook that misses its target fails silently. `HookCounter` counts each one, and the harness `hooks` check turns a zero count into a FAIL.
- 1.12.2 reads sprite pixels in `loadSpriteFrames`, not `loadSprite`, so its hook redirects `TextureUtil.readBufferedImage` there and passes the whole strip to the core like 1.8.9. A repaint cuts the strip into frames where `loadSpriteFrames` does, then calls `generateMipmaps`.
- Forge 1.16.5 has an optional light pipeline (`experimentalForgeLightPipelineEnabled` in `forge-client.toml`, off by default) that calls neither `BlockColors.getColor` nor `ClientLevel.getShade`. `ForgeBlockInfoMixin` and `ForgeLightUtilMixin` cover it, from their own config `albedo.forge.mixins.json` in the Forge jars before 1.19. Test with the option on; without them the `tint` counter stays 0.

## Settings changes

Every change (screen, key, harness, config file) goes to `Albedo.apply`, which saves the config, builds a new `Policy` and calls `Albedo.refresh`:

1. Sky, fog, fullbright, shadows and clouds read the policy each frame.
2. `TextureStore.repaint` repaints each texture whose look changed from its kept original.
3. `Platform.finishRepaint` uploads the results.
4. If tints or shading changed, `Platform.rebuildChunks` rebuilds the chunk meshes.
5. Only if an upload fails or the store is over its memory budget does `Platform.reloadResources` reload everything.

Traps:

- A resource reload replaces sprites and textures. Each upload first looks up the current object; if it is not the one the hook saw, the upload returns false and the store drops the texture.
- `TextureStore` keys textures by name, because some textures share a key (OptiFine connected textures).
- Downloaded skins do not count toward the memory budget, and their failed upload does not trigger a reload, because a reload cannot fetch them again.
- A sky edit does not repaint textures (`Policy.sameTextures`).
- Never read the policy in a resource reload listener (for example `CloudRenderer.prepare`): settings changes do not reload resources, so the change would show only after the next reload.
- On 1.8.9, `rebuildChunks` uses `markBlockRangeForRenderUpdate`, not `loadRenderers`: with Patcher, Essential or OptiFine L5, chunks can stay empty after `loadRenderers`.

## Animated Chroma

Albedo does not own the block shader (vanilla, Sodium, Iris, OptiFine, the 1.8.9 fixed pipeline), but every renderer samples the atlas. So a GPU pass recolors the atlas texture itself each frame. Repainting the block atlas on the CPU takes about 20 ms, too slow per frame.

- The CPU paints moving-Chroma sprites without the hue (`Effects.apply` with `gpuHue`). Only atlas sprites the store keeps get this; everything else gets still Chroma colors at time 0 (`TextureStore.stillChroma` tells the Effects section which categories are affected).
- `ChromaTexels` stores one RGBA8 texel per atlas texel: speed in alpha, hue in red and green, OKLab chroma in blue, with mip levels.
- The adapter pass (`ChromaAtlases` with `ChromaPass` on 26.3, `PrecompiledChromaPass` 1.21.11 to 26.2, `GlChromaPass` 1.17.1 to 1.21.10, `FixedChromaPass` 1.16.5, `LegacyChroma` 1.8.9) copies each mip level to a pristine copy at frame start and draws the hue over it (`chroma.fsh`, `include/chroma.glsl`); at frame end it copies the pristine copy back. Speed 0 texels are discarded.
- The hue comes from `Chroma.turns` on `SkyClock.longSeconds()` in double precision. An ordered 16 x 16 dither below one 8-bit step keeps slow hues moving smoothly.
- Memory cost is twice the atlas with mips (42.6 MiB for a 2048 x 2048 block atlas).

Traps:

- Between frames the atlas holds the painted colors, so the game and Albedo always write into those. A repaint or reload mid-frame restores the pristine copy first (`restore`). If the previous frame's end did not run, the next start restores first, so the hue never leaks into the pristine copy.
- Texels are rebuilt only when a moving texture changes (`Albedo.texturePaints`) or an atlas gets a new sprite map (1.21.1 uploads a reloaded atlas of the same size into the same GL texture).
- 1.21.8 and later bake item icons into an item atlas once; while colors move, each icon is marked animated (`ItemIconMixin`).
- 1.8.9 with OptiFine Multitexture draws blocks from per-sprite textures, so the pass stops and sprites get still colors.
- On 1.21.11 and NeoForge 26.3, `CommandEncoder.copyTextureToTexture` copies only mip level 0 whatever level it is given, so the other levels are copied with a draw (`chroma_copy.fsh`). Without it, sprites at the top left of the atlas turn into a checker of extreme colors.
- 1.21.5 to 1.21.10: a render pass draws only into mip level 0 and the atlas cannot be a render target, so these versions use `GlChromaPass` on the atlas GL name, unwrapping the NeoForge validation layer (`getRealTexture`).
- On 1.8.9 the pass pushes and pops GL attributes while its own framebuffer is bound: OptiFine Fast Render draws with no framebuffer, and `GL_BACK` is not a valid draw buffer for one.
- If the shader fails to compile or a GPU call fails, the adapter logs one warning and calls `Albedo.chromaGpu(false)`; the core goes back to still colors.

## Custom sky

`SkyGenerator` bakes a `SkyDesign` into an equirectangular `SkyImage` on the CPU, in the background (`SkyBaker`). `SkyProgram` packs the layer parameters into one float block. The sky shader (`core/src/main/resources/assets/albedo/shaders`) reads the same block and draws every screen pixel ("Sharp"); the baked texture is the fallback.

- Each sky compiles its own shader: `SkyShaderSource.layerSteps` emits `ALBEDO_LAYER_STEPS`, a direct call per layer with its blend, mask and parts as constants. Values stay in the block, so a shader recompiles only when the steps change. `SkyShaderCache` keeps recent shaders.
- The sharp sky draws only where the world does not cover it. In the sky pass the adapter draws black on the far-depth pixels (depth test, no blend), and vanilla sun, moon and stars draw on the black. After the solid world, the sky sphere draws at far depth and adds its color. The result equals a sky with the sun, moon and stars on it. Only pixels still at the clear depth turn black, so a portal mod's second view survives.
- The after-solid hook: 26.3 `LevelRenderer.executeSolid`; 1.21.6 to 26.2 `Lighting.setupFor` for the level (also fires when Sodium draws the terrain); 1.20.5 to 1.21.5 `renderSectionLayer`; 1.19.4 to 1.20.4 `SkyAfterChunkLayerMixin`; 1.16.5 to 1.19.2 `renderChunkLayer` after the cutout layer (`FixedSkyMixin`, `MojangMathSkyMixin`); legacy `RenderGlobal.renderBlockLayer`.
- `SkyDefer` makes the sphere wait for that hook only if the hook ran in the previous frame; otherwise it draws in the sky pass. A renderer mod that removes the hook costs speed, not the sky, and a skipped hook gives at most one black frame.
- The term texture also holds field images: the coarse cloud and fractal noise baked at texel centers (`SkyGenerator.CLOUD_FIELD_OCTAVES`). The shader filters them linearly and adds the fine octaves per pixel.
- At most `SkyProgram.MAX_TERMS` term filters (blur, bloom, pixelate, crystallize, swirl); more than that draws the baked image. Field images take the free slots up to `MAX_SLOTS`; a layer without one computes its noise per pixel.

Invariants:

- The CPU bake and the shader must produce the same sky: same integer hashes and noise. The Java twins of shader code (`SkyStars`, `SkyMeteors`, `Aurora`) change together with the GLSL. Harness `skycompare` checks it; run it after every sky change.
- `SkyGeneratorTest.thePresetsKeepTheirPixels` pins a hash of each preset's bake. Update it in the same commit when a pixel change is intended.
- `SkyClock.seconds()` wraps every hour to keep float precision, so every wave must repeat a whole number of times per hour. `longSeconds()` does not wrap and drives the turning. Meteor hashes use the slot modulo 3600, so meteors in flight cross the wrap. `Aurora.clock` wraps at `Aurora.PERIOD` (7200 s) instead.
- The bake, the preview and `skycompare` draw a still sky (`SkyProgram.STILL`); meteors exist only in the shader. A moving or turning sky writes the block each frame from `SkyProgram.at`.
- Meteors and aurora live in world space and do not turn with the sky. The bake does not know the turn, so the baked texture turns its aurora with the sky.
- A render mode or turning change does not rebake (`SkyDesign.baked`), except star trails, which the bake draws.
- Never dispatch on the layer kind at run time in a layer step: the NVIDIA compiler stops inlining at about seven such steps and the sky gets about 60 percent slower. Never index a constant array with a per-pixel value: it moves to local memory. Measure shader changes with `skytime 3840 2160` on 26.3 (and note that 26.x goes through SPIR-V).

Traps by version:

- 26.x: no texture writes inside a render pass, so uploads happen before the sky pass. The sky pass prepares only the overworld skybox.
- 26.x: shader includes are inlined on the render thread (`SkyShaderSource.inline`) before compiling on a worker. If shaderc resolves an include itself it calls back into Java, which on Windows with Java 25 can overrun the worker's stack guard and kill the JVM without an `hs_err` file. `SharpSky` carries its own shader source because Fabric without Fabric API does not load mod assets.
- 1.21.8 to 26.2: `PrecompiledSharpSky` precompiles before each sky pass, because a resource reload empties the pipeline cache. Never cast a `GpuTexture` to the GL class: the NeoForge validation layer hands out a wrapper. `DeviceApi` absorbs the device API changes of 1.21.10, 1.21.11, 26.1 and 26.2.
- The device API has no half-float format before 26.2, so on the device path the term texture is rewritten through GL after a one-texel `writeToTexture` that binds it. On 26.2 with OpenGL the device still sends half-float writes as bytes, so the same GL rewrite applies; on Vulkan the bytes copy correctly and `DeviceApi.isGl()` keeps raw GL away.
- 26.2 reverses the depth buffer: pipelines test `GREATER_THAN_OR_EQUAL` against far depth 0, and `DeviceApi.dialect()` sets `ALBEDO_REVERSED_DEPTH`.
- 26.2 on Vulkan: a pipeline with a depth state used in a pass with no depth view crashes the driver, so the Chroma pass and the `skycompare` image use `DeviceApi.colorOnly`. Vulkan also rejects a pipeline that does not declare every sampler its shader declares, so the sharp sky always declares `AlbedoTerms`.
- 1.21.11: the device frees a compiled pipeline only when a reload empties the whole cache, so each sky keeps its pipeline by its layer steps and a returning sky compiles nothing.
- 1.17.1 to 1.21.5 (`GlSky`, `GlSharpSky`, `GlMesh`, `GlProgram`) and 1.8.9 (`LegacySharpSky`) compile their own GL programs: GLSL 1.30, else 1.20 with `EXT_gpu_shader4`, else the baked image. Each raw GL draw saves and restores the state it touches through `GlState`, which goes through `GlStateManager` so the game's state cache stays right.
- 1.21.2 to 1.21.5 draw in the frame-graph sky pass with the matrices from `RenderSystem`. The `SkyRenderer` handlers of 1.21.2 to 1.21.11 take no parameters because the signatures keep changing. 1.21.5 leaves no framebuffer bound between passes, so `GlSky` opens an empty device render pass to bind the main target.
- 1.17.1 to 1.21.5 with an Iris shader pack: the pack's framebuffer needs the pack's programs, so the sky and fog color stay vanilla (`ModernSky.vanillaWithPack`).
- Lava, powder snow, blindness and darkness: from 1.18 the game skips the sky pass. 1.17 does not, so `GlSky` checks `ModernHooks.skySkipped`; 1.16.5 uses `ModernHooks.fogHidesSky`, which adds water. The fog color follows the same rule.
- 1.16.5 uses the fixed pipeline like 1.8.9, through ports of the legacy classes (`FixedSky`, `FixedSharpSky`, `FixedChromaPass`); they cannot be shared because legacy uses MCP names, LWJGL 2 and the `Game` shim. `FixedSkyMixin` redirects the three `VertexBuffer.draw` calls of `renderSky` to get the sky disc's camera matrix, and the sunrise fan's `BufferUploader.end`.
- 1.8.9: below cloud height the clouds draw before the world and blend with the sky, so the sphere draws before them (`RenderGlobal.renderClouds`). The black sphere uses the depth range 1 to 1.
- 1.8.9 with Use VBOs (off by default; on by default on 1.12.2): vanilla binds its sky buffer before the hook and LWJGL refuses client-memory draws while it is bound, so `LegacySky.drawSky` unbinds it. Test both settings.
- Nothing that blends with the sky or skips the depth test may draw between the sky pass and the solid world: it gets a black background. Other mods can draw there (for example NeoForge `AFTER_SKY`), and their translucent pixels blend with black. This is a known limit.

## OptiFine on 1.8.9

- Every OptiFine version renames methods in `BlockModelRenderer`, `BlockFluidRenderer` and `FaceBakery`. `MixinPlugin` detects OptiFine and applies either the `*VanillaMixin` or the `OptiFine*Mixin` set. `OptiFineRenderPatch` finds the tint and brightness calls by target and descriptor, not by name.
- `LegacySkyPatch` finds each draw in `RenderGlobal.renderSky` by the field it reads, not by order, because OptiFine adds draws. If a part is missing, it logs a warning and the sky stays vanilla.
- With OptiFine Sky off or a shader pack, the sky stays vanilla. With Fast Render, game framebuffers bind nothing, so the harness uses its own.
- The classifier maps OptiFine textures (`optifine/ctm/`, `mcpatcher/ctm/`, `cit/`, `sky/`, `random/entity/`) to the category of the texture they replace.
- Tested with HD U M6 pre2 and HD U L5 (`tools/optifine.ps1`). OptiFine on 1.12.2 is unsupported: `MixinPlugin` still turns off the vanilla mixins there, and `OptiFineRenderPatch` targets 1.8.9 names.

## Version conditions (modern)

`//? if` comments mark version differences in `modern/` only, never in core. A class that only one rendering family can compile has a condition around the whole file. The families are constants in `modern/stonecutter.gradle`: `gl_fixed` (before 1.17), `gl_core` (1.17 to 1.21.1), `frame_graph` (1.21.2 to 1.21.4), `gpu_device` (1.21.5 to 26.2), `renderpearl` (26.3), plus `gl_sky` (raw GL sky to 1.21.5) and `gl_chroma` (raw GL Chroma to 1.21.10). Shared classes call the family class: `ModernSky` calls `PearlSky`, `PrecompiledSky`, `FixedSky` or `GlSky`; `TextureUploads` calls `DeviceAtlasUpload` or `GlAtlasUpload`.

A condition may only use a version from this table (`tools/lint.sh` checks). `tools/boundaries.sh` prints the shape of every member in `tools/boundaries.txt` on every node, from the Mojang-named jars; use it to place a boundary. Only the nodes are read, so a boundary between two nodes is not checked against the minor versions in between. With only 1.21.1 and 1.21.11 as nodes on either side, not every `>=1.21.11` is a true boundary; recheck when adding a node there. To add a Minecraft version, see "Add a Minecraft version" in [BUILDING.md](BUILDING.md).

| Boundary | Main API change |
|----------|-----------------|
| `1.17` | Shader pipeline (`ShaderInstance`, `drawWithShader`), vertex arrays; before it the fixed pipeline |
| `1.18` | `renderSky` takes a `Camera`; Forge `fmllegacy`/`fmlclient` packages renamed; SLF4J logging |
| `1.19` | `OptionInstance`, `Component.literal`, `ConfigScreenHandler` |
| `1.19.3` | `SpriteContents`, `SpriteLoader`; JOML replaces `com.mojang.math` |
| `1.20` | `GuiGraphics` |
| `1.20.2` | `mouseScrolled` with 4 numbers; `Screen.render` draws the background |
| `1.20.3` | `renderSectionLayer` with a `PoseStack` |
| `1.20.5` | `renderSectionLayer` with two matrices; MixinExtras on all loaders |
| `1.21` | `MeshData`, `ResourceLocation.fromNamespaceAndPath`, `DeltaTracker` |
| `1.21.2` | `SkyRenderer`, frame graph, `CloudRenderer`, static `computeFogColor` |
| `1.21.4` | `TextureContents.load`, `ReloadableTexture`; no `SimpleTexture.doLoad` or `HttpTexture` |
| `1.21.5` | `GpuDevice`, `RenderPass`, `RenderPipeline`; no `VertexBuffer` |
| `1.21.6` | `GpuTextureView`, `fog.FogRenderer`, `Lighting.setupFor`, uniform buffers |
| `1.21.9` | Input event objects (`KeyEvent`, `MouseButtonEvent`), `extractShadow` |
| `1.21.10` | `writeToTexture` with a `ByteBuffer`; `grabOrReleaseMouse` takes a `Window` |
| `1.21.11` | `Identifier`, `GpuSampler`, render pass into mip levels above 0, padded atlas sprites |
| `26.1` | Render state extraction, `CardinalLighting`, new pipeline builder states |
| `26.2` | `Gui.screen()`, `GpuFormat`, `GpuQueryPool`, reversed depth buffer |
| `26.3` | `com.mojang.renderpearl.api`, `executeSolid`, SDL window and input |

## Compat mods in the dev run

- Add `-Pcompat=sodium`, `embeddium` or `iris` to a modern `runClient` to add that mod to the dev run. The versions are in `modern/versions/<v>/gradle.properties`.
  - Iris works only on Fabric 26.3, 26.2, 26.1, 1.21.11, 1.21.5 and 1.21.4. On 1.21.4 it is Sodium 0.6.13 and Iris 1.8.8, the versions of the owner instances. On the other targets you get only Sodium.
  - Sodium 0.6.13 does not start in the NeoForge 1.21.5 dev run, because it needs the Fabric rendering API.
  - On 1.20.1, Sodium 0.5.13 (Fabric) and Embeddium 0.3.31 (Forge) have the package `me.jellysquid.mods.sodium`. `SodiumTintRedirectMixin` hooks their `getVertexColors`.
  - The property `lwjgl_version` puts the LWJGL version of the game in the dev run, because Sodium 0.5 starts only with that version.
  - NeoForge 1.21.1 gets Sodium 0.6.13, the version of the owner's pack, because its dev run loads the 0.8.13 jar only as a library.
  - NeoForge from 1.20.5 and 26.x get the mod jar with no remap, because a remap drops the jar-in-jar list from which Sodium loads its mod.
  - With `-Compat`, `tools/screenshots.ps1` turns off the Blaze3D check of the NeoForge dev run (`enableB3DValidationLayer`). Sodium 0.8.14 on NeoForge 1.21.11 casts a texture that the check wraps.
