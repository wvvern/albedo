# Architecture

Albedo uses ports and adapters. The core has almost all of the code and no Minecraft types. Each Minecraft era has a small adapter.

| Module | Contents |
|--------|----------|
| `core/` | Plain Java 8, no Minecraft types. Config, `Policy`, texture classification and pixel math, the custom sky (bake and shaders), the settings screen (`ui/`) and the harness (`dev/Harness`). It compiles into every jar. It has unit tests. |
| `modern/` | The nodes in `modern/settings.gradle` (1.20.1 to 26.3), Fabric and NeoForge (and Forge on 1.20.1 and 1.20.4), with Mojang names. Stonecutter 0.9, Architectury Loom 1.17, Gradle 9.8. `common/` has the adapter and the mixins. `fabric/` and `neoforge/` have only the entrypoint and the config button. |
| `legacy/` | 1.8.9 and 1.12.2 Forge, with MCP names. Essential Loom 0.10, Mixin 0.7.11, Gradle 8.8. Gradle runs on JDK 21, the game on Java 8. |

One tool chain cannot build both 1.8.9 Forge and 26.x, so there are two builds. Both compile `core/src`.

The legacy versions share `legacy/src/main/java`. Each version has its own `legacy/versions/<mc>/src/main/java` with:

- `Game`: the same static methods on each version for the game members whose names changed (world, player, font, tessellator draws, chat message, fog of the camera, world creation).
- The classes whose target or descriptor changed: `RenderGlobalMixin` (`BlockRenderLayer`, `renderClouds(FIDDD)V` on 1.12.2), `BlockModelRendererVanillaMixin` (`renderQuadsSmooth`, `renderQuadsFlat` and `BlockColors.colorMultiplier` on 1.12.2), `TextureAtlasSpriteMixin` and `SpriteUpload`.
- The atlas sprite hook: 1.8.9 changes the strip in `loadSprite(BufferedImage[], AnimationMetadataSection)`. 1.12.2 reads the pixels in `loadSpriteFrames` (`loadSprite` reads only the size), so its hook redirects `TextureUtil.readBufferedImage` there and gives the whole strip to the core, as 1.8.9 does. A repaint on 1.12.2 cuts the strip into frames at the places that `loadSpriteFrames` uses, then calls `generateMipmaps`.
- The build writes the `client` list of `mixins.albedo.json` from the mixin classes of the shared source and of the version, in ASCII order.

## Ports and adapters

- The core makes all decisions: config, groups, matching, colors, pixel math, presets, keys and config reload. `Policy` holds all render decisions from one config snapshot.
- `Platform` is the port for the game: config folder, log, resource reload, chunk rebuild, end of a repaint, messages, key state, clipboard, settings screen and harness host.
- `ui/Canvas` is the port for drawing: `width`, `height`, `fill`, `text`, `textWidth` and `image`. The settings screen is an immediate-mode UI in the core. The screen class of each version only forwards draws and input.
- Each adapter (`ModernPlatform`, `LegacyPlatform`) implements the ports, puts pixels in a `PixelBuffer` (`NativeImagePixels`, `BufferedImagePixels`) and sends plain values from the mixins to `ModernHooks` or `LegacyHooks`. A mixin handler has no game logic.
- The mod uses no loader API. Mixins do the tick, keys and config work, so Fabric does not need Fabric API.

## Hooks

The adapters change data at stable points, not in the renderers. The core processes decoded pixels, so the source of a texture (vanilla, resource pack or mod) is not important.

| Point | Modern hook | 1.8.9 and 1.12.2 hook |
|-------|-------------|------------|
| Atlas sprites, before mipmaps | `SpriteLoader.stitch` | `TextureAtlasSprite.loadSprite`, 1.12.2 `loadSpriteFrames` |
| Other textures | `TextureContents.load` (1.21.4+), `SimpleTexture.doLoad` | `SimpleTexture.loadTexture` |
| Downloaded skins, capes, elytra | `TextureManager.register` of a `DynamicTexture` (1.21.4+), `HttpTexture.load` | `ThreadDownloadImageData.setBufferedImage` |
| Biome colormaps | `GrassColor`, `FoliageColor`, `DryFoliageColor` `init` | `ColorizerGrass`, `ColorizerFoliage` |
| Block tint | `ModelBlockRenderer`; Sodium and Embeddium `BlockRenderer` | `BlockModelRenderer` (1.12.2 `BlockColors.colorMultiplier` in it), Forge `BlockInfo` |
| Water tint | `BiomeColors.getAverageWaterColor` | `BiomeColorHelper.getWaterColorAtPos` |
| Fullbright | gamma in `LightTexture`, 26.x `LightmapRenderStateExtractor` | gamma in `EntityRenderer.updateLightmap` |
| Smooth lighting | `Minecraft.useAmbientOcclusion`, 26.x `ModelBlockRenderer`, Sodium `LevelSlice` | `Minecraft.isAmbientOcclusionEnabled` |
| Face shade | `ClientLevel.getShade`, 26.x `CardinalLighting.byFace` | quads into the chunk mesh (`Game.addQuad`, `LegacyHooks.vertexColor`) |
| Entity shadows | `EntityRenderer`, before 1.21.9 `EntityRenderDispatcher` | `Render.doRenderShadowAndFire` |
| Sky and fog color | `LevelRenderer` sky pass, `FogRenderer` (1.21.2 to 1.21.5 the static `computeFogColor`, `StaticFogMixin`) | `RenderGlobal.renderSky`, `EntityRenderer.updateFogColor` |
| Custom sky | `SkyRenderer` from 1.21.2 (`GlSkyLayersMixin` to 1.21.5, `SkyLayersMixin` from 1.21.6); 1.21.1 `LevelRenderer.renderSky` | `RenderGlobal.renderSky` (`LegacySkyPatch`) |
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
- The adapter (`ChromaAtlases` with `ChromaPass` on 26.3, `PrecompiledChromaPass` from 1.21.11 to 26.2, `GlChromaPass` from 1.20.1 to 1.21.10, `LegacyChroma` on 1.8.9) hooks the start and the end of each frame. At the start it copies each mip level into a pristine copy and draws the hue of the frame over it (`chroma.fsh`, `chroma_classic.fsh`, `include/chroma.glsl`). At the end it copies the pristine copy back. A texel with speed 0 is discarded.
- The turn of each speed comes from `Chroma.turns` with `SkyClock.longSeconds()` in double precision, so each frame has its own colors.
- At speed 100 a frame of 8 ms turns the hue by less than one 8-bit step. The pass adds a 16 x 16 ordered dither below one step at each texel, so that a part of a flat sprite moves in each frame.

Traps:

- Between frames the atlas has the painted colors, so the game and Albedo always write into them. A repaint or a reload in the middle of a frame first copies the pristine copy back (`restore`). If the end of the last frame did not run, the start first gives back the painted colors, so the hue never goes into the pristine copy.
- The texels are made again only when a texture with moving colors changes (`Albedo.texturePaints`) or an atlas gets a new sprite map (1.21.1 uploads a reloaded atlas of the same size into the same GL texture). A change of only the speed changes only the texels.
- 1.21.8 and later draw each item icon once into an item atlas. While colors move, each item icon counts as animated (`ItemIconMixin`).
- 1.8.9 with OptiFine Multitexture on draws blocks from a texture of each sprite, so the pass stops and the sprites get still colors.
- On 1.21.11 and NeoForge 26.3, `CommandEncoder.copyTextureToTexture` copies only the mip level 0, whatever level it gets. Thus the pass copies the other mip levels with a draw (`chroma_copy.fsh`, `chroma_copy_classic.fsh`). Without it, the hue goes into the pristine copy in each frame and the sprites in the top left of the atlas get a checker of extreme colors.
- On 26.x the pipeline compiles on the render thread from the inlined text. On 1.21.11 never cast a GPU texture. On 1.21.1 and 1.8.9 the pass puts back each GL state that it changes. On 1.8.9 it pushes and pops the GL attributes while its own framebuffer is bound, because OptiFine Fast Render draws with no framebuffer and the draw buffer `GL_BACK` is not valid for a framebuffer. The memory is two times the atlas with its mip levels (42.6 MiB for the 2048 x 2048 block atlas).
- If the shader does not compile or a GPU call fails, the adapter logs one warning and calls `Albedo.chromaGpu(false)`. The core paints still Chroma colors again.

## Custom sky

`sky/SkyGenerator` bakes a `SkyDesign` into an equirectangular `SkyImage` on the CPU (`SkyBaker`, in the background). `sky/SkyProgram` puts the layer numbers into one block of floats. The sky shader (`core/src/main/resources/assets/albedo/shaders`) reads the same block and draws each screen pixel ("Sharp render"). The baked texture is the fallback.

- Each sky gets its own shader. `SkyShaderSource.layerSteps` writes `ALBEDO_LAYER_STEPS`: a direct call of the function of the kind of each layer, with its blend, mask and parts (twinkle, trails, spikes, pulse, dust, jets) as constants. The values stay in the block, so a shader compiles again only when the steps change. Each adapter keeps the shaders of the last skies (`SkyShaderCache`), and 1.21.11 keeps them all (see the traps).
- The sky shader draws only the pixels that the world does not hide. In the sky pass the adapter draws black on the sphere at the far depth with a depth test and no blend, so that only the pixels that still have the far depth of the clear become black (a portal mod that draws a second view of the level into the frame keeps the rest of the frame). The vanilla sun, moon and stars draw on the black. After the solid world, the sky sphere draws at the far depth with a depth test and adds its color to the frame. The sum is the same as a sky with the sun, the moon and the stars on it. The hook after the solid world is `SkyAfterSolidMixin` (26.x `LevelRenderer.executeSolid`, 1.21.8 to 26.2 `Lighting.setupFor` for the level, which follows the solid terrain also when Sodium draws it, 1.20.5 to 1.21.5 `LevelRenderer.renderSectionLayer`, and `SkyAfterChunkLayerMixin` before 1.20.5) and `RenderGlobal.renderBlockLayer` on 1.8.9. The baked texture always draws in the sky pass.
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
- 1.21.8 to 26.2: `PrecompiledSharpSky` precompiles before each sky pass, because a resource reload empties the pipeline cache. The game has no half float format before 26.2, so the term texture is written again through the GL after a one-texel `writeToTexture` that binds it. Do not cast a `GpuTexture` to the GL class: the NeoForge validation layer gives a wrapper. `DeviceApi` has the device calls that changed at 1.21.10, 1.21.11, 26.1 and 26.2; before 1.21.11 the filters are on the texture, not in a sampler, and `skytime` uses a GL time query.
- 26.2: the term texture has the half float format, but the GL device sends the bytes of a write as unsigned bytes, so the GL sends them again as half floats after the write that binds the texture. For that the sharp sky needs the OpenGL backend (26.2 also has a Vulkan one); with another backend the baked image draws. The depth buffer is reversed, so the pipelines test `GREATER_THAN_OR_EQUAL` against the far depth 0 and `DeviceApi.dialect()` gives the shader text with the far clip z `-w`, or 0 when the clip volume starts at 0 (`ALBEDO_REVERSED_DEPTH`).
- 1.21.5 to 1.21.10: a render pass draws only into the mip level 0 of a texture, also through a view of a different level, and the atlas texture cannot be a render target. Thus the Chroma pass of these versions is `GlChromaPass` with the GL name of the atlas texture; it takes the real texture under the wrapper of the NeoForge validation layer (`getRealTexture`).
- 1.20.1 to 1.21.5 (`GlSharpSky`) and 1.8.9 (`LegacySharpSky`) compile their own GL program. 1.8.9 uses GLSL 1.30, else GLSL 1.20 with EXT_gpu_shader4, else the baked image. A failed compile logs one warning and draws the baked image.
- 1.8.9: below the cloud height the clouds draw before the world and blend with the sky, so the sky sphere draws before them (`RenderGlobal.renderClouds`) and the world hides nothing from the shader. With clouds off, or above the cloud height, the sphere draws after the solid world.
- Do not draw a thing that blends with the sky, or that has no depth test, between the sky pass and the solid world: it gets a black background.
- Known limit: other mods can draw in this gap, for example at the NeoForge stages `AFTER_SKY`, `AFTER_SOLID_BLOCKS` and `AFTER_CUTOUT_MIPPED_BLOCKS`. A translucent thing that such a mod draws there over the sky blends with black.
- 26.x: the sky pass prepares the custom sky only for the overworld skybox, the only one that it replaces. A pipeline that closes while it compiles frees its result on the render thread when the compile ends.
- 1.21.11: the device frees a compiled pipeline only when a resource reload empties its whole cache. Thus each sky keeps its pipeline and its id by its layer steps, and a sky that comes back compiles nothing.
- 1.8.9 draws the black sphere with the fixed pipeline of the game at the depth range 1 to 1. The game never changes the depth range.
- 1.20.1 to 1.21.5 use no vertex or shader class of the game. `GlSky` draws the baked image and `GlSharpSky` draws the sharp sky and the black sphere, each with a vertex array (`GlMesh`) and programs (`GlProgram`) of the mod. The vertex shader of the black sphere writes the far depth, so the depth range does not change.
- 1.21.2 to 1.21.5 draw the sky in the sky pass of the frame graph: `GlSky.drawDisc` takes the model view and projection matrices of `RenderSystem`, which the game also gives its sky disc. The `SkyRenderer` handlers of 1.21.2 to 1.21.11 take no parameters, because the parameters change between these versions.
- 1.21.5 uses the same GL classes as 1.21.1. The game leaves no framebuffer bound between its render passes, so `GlSky` opens an empty render pass of the device, which binds the main target, for its draws in the sky pass and after the solid world.
- 1.20.1 to 1.21.5 with an Iris shader pack: the sky stays vanilla, because the framebuffer of the pack needs the programs of the pack. When `GlSky.draw` returns false, the game draws its sky disc, dark disc, sunrise and stars. `ModernSky.vanillaWithPack` is the rule for the sky and for the fog color, so the fog stays vanilla with the sky.
- Each raw GL draw of `GlSky`, `GlSharpSky` and `GlChromaPass` reads the state that it changes with `glGet` and puts it back (`GlState`). `GlState.ofDraw` holds the program, the vertex array, the active texture unit, the texture of unit 0, the depth test, function and mask, the blend state and the cull state. `GlState.ofAll` adds the array buffer, the two framebuffers, the viewport, the texture of unit 1 and the scissor test. The restore goes through `GlStateManager`, so that the copy of the state in the game stays correct.
- On 1.8.9 with Use VBOs on, vanilla binds its sky buffer before the sky hook, and LWJGL refuses a draw from client memory while a buffer is bound. `LegacySky.drawSky` unbinds it while the custom sky draws. VBOs are off by default on 1.8.9 and on by default on 1.12.2, so test the other case with `-OptionLines useVbo:true` or `-OptionLines useVbo:false` (`tools/screenshots.ps1`, `tools/optifine.ps1`).

## OptiFine on 1.8.9

- Each OptiFine version uses different method names in `BlockModelRenderer`, `BlockFluidRenderer` and `FaceBakery`. `MixinPlugin` finds OptiFine and selects the mixins: `*VanillaMixin` only without OptiFine, `OptiFine*Mixin` only with it. `OptiFineRenderPatch` finds the tint and brightness calls by target and descriptor, not by name.
- `LegacySkyPatch` finds each draw in `RenderGlobal.renderSky` by the field that it reads, not by order, because OptiFine adds draws. If it does not find all parts, it logs a warning and the sky stays vanilla.
- With OptiFine Sky off or an OptiFine shader pack, the sky stays vanilla.
- With OptiFine Fast Render, a `Framebuffer` of the game binds nothing (`OpenGlHelper.isFramebufferEnabled()` is false), so `skycompare` and `skytime` use a framebuffer of their own.
- The classifier gives OptiFine textures (`optifine/ctm/`, `mcpatcher/ctm/`, `cit/`, `sky/`, `random/entity/`) the category of the vanilla texture that they replace.
- Tested only with OptiFine HD U M6 pre2 and HD U L5 (`tools/optifine.ps1`). OptiFine shaders are not tested.
- OptiFine on 1.12.2 is not supported and has no test. The same `MixinPlugin` rule applies, so with OptiFine the vanilla tint and shade mixins do not apply.

## Build

You need JDK 21 and JDK 25. If JDK 8 is missing, Gradle gets it for 1.8.9.

```bash
pwsh tools/build-all.ps1                                                    # all jars -> build/libs/
cd modern && ./gradlew :fabric:26.3:releaseJar :neoforge:26.3:releaseJar    # the jars of one node
cd legacy && ./gradlew build                                                # Forge jars -> legacy/versions/<mc>/build/libs/
```

- A node is one folder in `modern/versions/`. `modern/settings.gradle` has three node lists: `nodes` (common and Fabric), `forgeNodes` and `neoforgeNodes`.
- Forge nodes (1.20.1, 1.20.4) use `modern/forge/build.gradle`, and NeoForge 1.20.4 uses the `mods.toml` branch of `modern/neoforge/build.gradle`. The dev run of Forge 1.20.4 takes the common jar from the transformed copy only, because its bootstrap makes a module of each jar on the class path.
- `modern/common/build.gradle` compiles the common code against the annotations of Mixin 0.8.5. Mixin 0.8.7 writes the `at` of a redirect as an array, and MixinExtras before 0.5 (NeoForge 1.20.6, Fabric Loader 0.15, Forge packs) cannot read that.
- `ModernMixinPlugin` leaves out the sky mixins (`SkyLayers*`, `SkySunrise*`) when OptiFine is on the class path, because OptiFine changes the sky code and a required sky mixin that does not match would stop the game. The repaint mixins stay.
- Build only one node in each Gradle run of `modern/`. `modern/gradle.properties` sets configure on demand, and the scripts also give `--configure-on-demand`. When one run configures all nodes, Loom can give a Forge node the mappings of a different node and write a bad jar to the shared cache. Loom does not repair it.
  - Symptoms: `cannot access Screen` in the compile, or `NoClassDefFoundError: gjp$a` in the dev game.
  - Repair: stop all Gradle processes. Delete `~/.gradle/caches/fabric-loom/<mc>/loom.mappings.*forge-<mc>-<forge>*`, `~/.gradle/caches/fabric-loom/<mc>/forge/<mc>-<forge>` and `~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/forge-<mc>-<forge>-minecraft-merged*`. Then build that node alone.
- The build writes the `client` list of `albedo.mixins.json` from the compiled mixin classes of the node, and `compatibilityLevel` from its Java version (`JAVA_21` at most). Do not write mixin names in the file. The list has the classes of the package `mixin` whose names end in `Mixin`, `Accessor` or `Invoker`. Do not put a different class in that package: `tools/check-jar.sh` fails for it.
- `-Pmc_override=<version>` builds a node for a different Minecraft version, to examine a minor version of its range. It changes only the dependency: the Stonecutter swap `minecraft` and the conditions keep the version of the node.
- Dev client: `./gradlew :fabric:26.3:runClient` in `modern/` (any loader or version), `./gradlew --configure-on-demand :1.12.2:runClientDev` in `legacy/` (or `:1.8.9:`).
- Add `-Pcompat=sodium`, `embeddium` or `iris` to a modern `runClient` to add that mod to the dev run. The versions are in `modern/versions/<v>/gradle.properties`. Iris works only on Fabric 26.3, 26.2, 26.1, 1.21.11, 1.21.5 and 1.21.4 (Sodium 0.6.13 and Iris 1.8.8, the versions of the owner instances on 1.21.4); on the other targets you get only Sodium, and Sodium 0.6.13 does not start in the NeoForge 1.21.5 dev run, because it needs the Fabric rendering API. On 1.20.1, Sodium 0.5.13 (Fabric) and Embeddium 0.3.31 (Forge) have the package `me.jellysquid.mods.sodium`, and `SodiumTintRedirectMixin` hooks their `getVertexColors`; the property `lwjgl_version` puts the LWJGL version of the game in the dev run, because Sodium 0.5 starts only with that version. NeoForge 1.21.1 gets Sodium 0.6.13, the version of the owner's pack, because its dev run loads the 0.8.13 jar only as a library. NeoForge from 1.20.5 and 26.x get the mod jar with no remap, because a remap drops the jar-in-jar list from which Sodium loads its mod. With `-Compat`, `tools/screenshots.ps1` turns off the Blaze3D check of the NeoForge dev run (`enableB3DValidationLayer`), because Sodium 0.8.14 on NeoForge 1.21.11 casts a texture that the check wraps.
- In `modern/`, Stonecutter comments (`//? if >=26.1 {` ... `//?} else {` ... `//?}`) mark the version differences. They are only in mixin and adapter files, never in the core.
- A class that only one rendering family can compile has a condition around the whole file. `modern/stonecutter.gradle` names the families: `gl_fixed` (before 1.17), `gl_core` (1.17 to 1.21.1), `frame_graph` (1.21.2 to 1.21.4), `gpu_device` (1.21.5 to 26.2) and `renderpearl` (26.3). The shared class calls the family class: `ModernSky` calls `PearlSky`, `PrecompiledSky` or `GlSky`, `TextureUploads` calls `DeviceAtlasUpload` or `GlAtlasUpload`, and `SettingsScreen` draws on an `ExtractorCanvas` or a `GraphicsCanvas`. The raw GL classes go past their families: the sky classes `Gl*` to 1.21.5 (`>=1.17 && <1.21.6`), and `GlChromaPass`, `GlState`, `GlProgram` and `GlAtlasUpload` to 1.21.10 (`>=1.17 && <1.21.11`): before 1.21.11 the GPU device has no render pass into a mip level above 0, and each sprite uploads its own pixels.
- `legacy/` has one subproject for each Minecraft version in `legacy/versions/<mc>`, and `legacy/version.gradle.kts` builds each of them. The mod version comes from `mod_version` in `modern/gradle.properties`, or from `-PmodVersion`.
- Releases: see [RELEASING.md](RELEASING.md).

## Version boundaries (modern)

A Stonecutter condition uses only the versions of this table. `tools/boundaries.sh` reads each member with `javap` from the Mojang-named jar of each node, and `tools/boundaries.txt` lists the members. Where the mark says "minor open", the two nodes on each side are read, but no jar of the minor version itself. `tools/lint.sh` fails when a condition has a version that is not in this table.

| Boundary | API change | Files with the condition | Mark |
|----------|------------|--------------------------|------|
| `1.17` | `ShaderInstance`, `VertexBuffer.drawWithShader`, `Screenshot.takeScreenshot(RenderTarget)`, vertex array calls in `GlStateManager`, `renderChunkLayer` with a `Matrix4f` | `GlProgram`, `GlState`, `GlChromaPass`, `GlAtlasUpload` (first version of the file) | javap |
| `1.18` | `renderSky` with a `Camera` | none | javap, minor open |
| `1.19` | `OptionInstance`, `Component.literal`, `performPrefixedCommand`, `WorldOpenFlows` | `ModernHooks`, `FullbrightRedirectMixin`, `ShadowRedirectMixin` | javap, minor open |
| `1.19.3` | `SpriteContents`, `SpriteLoader`, JOML in all signatures | `SpriteAccess` | javap, minor open |
| `1.20` | `GuiGraphics` | none | javap, minor open |
| `1.20.2` | `mouseScrolled` with 4 numbers, `Screen.render` draws the background, `DefaultPlayerSkin.getDefaultTexture` | `SettingsScreen`, `TextureScreen`, `HarnessSkins` | javap, minor open |
| `1.20.3` | `renderSectionLayer` with a `PoseStack`, `WorldOpenFlows.checkForBackupAndLoad`, `createFreshLevel` with a parent screen | `SkyAfterChunkLayerMixin`, `HarnessWorlds` | javap, minor open |
| `1.20.5` | `renderSectionLayer` with two matrices, `WorldOpenFlows.openWorld`, `@Mod(dist)` and a `ModContainer` on NeoForge, MixinExtras on all loaders | `BlockTintMixin`, `FullbrightMixin`, `ShadowMixin`, `SodiumTintMixin` and their `*RedirectMixin` classes, `SkyAfterSolidMixin`, `HarnessWorlds`, `AlbedoNeoForge` | javap, minor open |
| `1.21` | `MeshData`, `ResourceLocation.fromNamespaceAndPath`, `GameRenderer.render(DeltaTracker, boolean)`, `Minecraft.getTimer` | `SkySunriseMixin`, `CanvasImages`, `HarnessSkins`, `ModernSky` | javap, minor open |
| `1.21.2` | `SkyRenderer`, `LevelRenderer.addSkyPass`, `CloudRenderer`, `LevelRenderer.getCloudRenderer`, `NativeImage.getPixel` and `setPixel`, `Options.inactivityFpsLimit`, `Minecraft.getDeltaTracker()`, `GameRules(FeatureFlagSet)`, static `FogRenderer.computeFogColor`, `blit` with a `RenderType` function | `SkyMixin`, `SkyLayersMixin`, `GlSkyLayersMixin`, `FogMixin`, `StaticFogMixin`, `NativeImagePixels`, `ModernHarnessHost`, `ModernSky`, `HarnessWorlds`, `GraphicsCanvas`, `TextureScreen` | javap, minor open |
| `1.21.4` | `TextureContents.load`, `ReloadableTexture.doLoad(NativeImage, boolean, boolean)`, static `SkinTextureDownloader.downloadAndRegisterSkin`, no `SimpleTexture.doLoad`, no `HttpTexture` | `TextureLoadMixin`, `SkinTextureMixin`, `TextureManagerMixin`, `TextureUploadInvoker`, `ModernHooks`, `TextureUploads`, `HarnessSkins` | javap |
| `1.21.5` | `GpuDevice`, `RenderPass`, `RenderPipeline`, no `VertexBuffer`, `uploadFirstFrame(GpuTexture)`, `Screenshot.takeScreenshot(RenderTarget, Consumer)`, `DryFoliageColor`, `DynamicTexture(Supplier<String>, NativeImage)`, `CommandEncoder.writeToTexture(GpuTexture, NativeImage)`, `GlStateManager` in `com.mojang.blaze3d.opengl` with no `_glDeleteVertexArrays`, `GlTexture.glId()`, no framebuffer bound between the render passes | `HarnessShots`, `CanvasImages`, `TextureUploads`, `GlAtlasUpload`, `GlState`, `GlChromaPass`, `GlSky`, `GlSharpSky`, `GlMesh` | javap |
| `1.21.6` | `GpuTextureView`, `fog.FogRenderer`, `Lighting.setupFor(Entry)`, no `renderSectionLayer`, `ItemStackRenderState.isAnimated`, `blit` with a `RenderPipeline`, uniform buffers (`DynamicUniforms`), `SkyRenderer` methods whose parameters change at 1.21.10 and 1.21.11. The `FogMixin` and `SkyLayersMixin` handlers take no parameters of the target, because 1.21.8 and 1.21.10 have one more `boolean` in `computeFogColor` | `FogMixin`, `StaticFogMixin`, `SkyLayersMixin`, `GlSkyLayersMixin`, `SkyAfterSolidMixin`, `GlSky`, `GlSharpSky`, `GlMesh` (last version of the file), `PrecompiledSky`, `PrecompiledSharpSky`, `DeviceApi`, `ModernSky`, `ModernHarnessHost`, `GraphicsCanvas`, `TextureScreen` | javap, minor open |
| `1.21.9` | `EntityRenderer.extractShadow`, `MouseButtonEvent`, `KeyEvent`, `CharacterEvent`, `MouseHandler.onButton`, `KeyboardHandler.keyPress` and `charTyped` with an event, `InputConstants.isKeyDown(Window, int)`, `Window.handle()`, no `Minecraft.ON_OSX`, `SkinTextureDownloader` is an object, not static | `ShadowMixin`, `MouseHandlerInvoker`, `KeyboardHandlerInvoker`, `SettingsScreen`, `HarnessMouse`, `HarnessKeyboard`, `HarnessWindow`, `HarnessSkins`, `ModernPlatform` | javap, minor open |
| `1.21.10` | `writeToTexture` with a `ByteBuffer` | `DeviceApi` | javap |
| `1.21.11` | `Identifier`, `GpuSampler`, `precompilePipeline(RenderPipeline, ShaderSource)`, `timerQueryBegin`, `ReloadableTexture.doLoad(NativeImage)`, `uploadFirstFrame(GpuTexture, int)`, `Camera.entity()`, `writeTransform` with 4 parameters, `gamerules.GameRules`, the atlas sprites with padding and an animation texture of their own, a render pass into a mip level above 0 | most adapter files, among them `DeviceApi`, `ChromaAtlases`, `GlChromaPass`, `DeviceAtlasUpload`, `GlAtlasUpload`, `TextureUploads`, `TextureUploadInvoker`, `SkinTextureMixin`, `HarnessWorlds` | javap |
| `26.1` | `LightmapRenderStateExtractor`, `ModelBlockRenderer(boolean, boolean, BlockColors)`, `computeTintColor`, `CardinalLighting.byFace`, `computeFogColor` with a `Vector4f` parameter, `Screen.extractRenderState`, `Level.getOverworldClockTime`, `LocalPlayer.sendOverlayMessage`, `Options.getCloudStatus`, `CharacterEvent(int)`, `withColorTargetState` and `withDepthStencilState` in the pipeline builder in place of `withBlend`, `withoutBlend`, `withDepthWrite` and `withDepthTestFunction` | `AmbientOcclusionMixin`, `BlockTintMixin`, `FaceShadeMixin`, `FogMixin`, `FullbrightMixin`, `CloudsMixin`, `ModernSky`, `ModernPlatform`, `ModernHarnessHost`, `HarnessWorlds`, `HarnessKeyboard`, `SettingsScreen`, `ExtractorCanvas`, `GraphicsCanvas`, `TextureScreen`, `DeviceApi` | javap |
| `26.2` | `Gui.screen()` and `Gui.setScreen`, `GameRenderer.mainRenderTarget()` and `mainCamera()`, `LevelRenderer.cloudRenderer()`, `Minecraft.levelExtractor`, `Hud`, `writeToTexture` with 4 numbers, `withBindGroupLayout`, `withVertexBinding`, `withPrimitiveTopology`, `GpuQueryPool`, `GpuFormat`, `PrimitiveTopology`, `Optional<Vector4fc>` for the clear color of a pass, `setVertexBuffer(int, GpuBufferSlice)`, `draw` with 4 numbers, a reversed depth buffer (`DepthStencilState.DEFAULT` passes `GREATER_THAN_OR_EQUAL`), no `DefaultVertexFormat.EMPTY`, `getModelViewMatrixCopy` | `ModernPlatform`, `ModernHarnessHost`, `HarnessShots`, `ModernSky`, `DeviceAtlasUpload`, `DeviceApi`, `PrecompiledSharpSky` | javap |
| `26.3` | `com.mojang.renderpearl.api`, `SkyRenderer.render` with a `RenderPass`, `LevelRenderer.executeSolid`, `MouseHandler.onMove` with 5 parameters, `InputConstants.isKeyDown(int)`, `KeyboardHandler.textInput`, `TextInputManager.startTextInput(Object)`, the SDL window | `SharpSky`, `ChromaPass`, `ChromaAtlases`, `ModernSky`, `SkyLayersMixin`, `SkyAfterSolidMixin`, `MouseHandlerInvoker`, `KeyboardHandlerInvoker`, `HarnessMouse`, `HarnessKeyboard`, `HarnessWindow`, `SettingsScreen`, `ModernPlatform`, `ModernHarnessHost` | javap |

The conditions `>=1.21.11` in the code of today are not all true boundaries, because 1.21.1 and 1.21.11 are the only nodes on each side. Examine each one with `tools/boundaries.sh` when you add a node between them.

## Add a Minecraft version (modern)

1. Add `modern/versions/<v>/gradle.properties`. For Forge or NeoForge, also add `modern/<loader>/versions/<v>/gradle.properties` with `loom.platform=forge` or `loom.platform=neoforge`.
2. Add `<v>` to `nodes` in `modern/settings.gradle`, and to `forgeNodes` or `neoforgeNodes`. Add a row to the `modern` matrix in `.github/workflows/ci.yml`. `tools/lint.sh` compares the row with the lists and with the node file.
3. Run `./gradlew :fabric:<v>:build :neoforge:<v>:build` in `modern/`. The compile errors show the hooks that moved. Fix each one with a `//? if` branch.
4. Examine the condition boundaries (`>=1.21.2`, `>=1.21.4`, ... `>=26.1`). The project tests them only on the built versions.
5. Run `tools/screenshots.ps1 -Targets fabric-<v>`. Its `result.txt` must start with `PASS`.

## Check and test

- Before you push: `./gradlew :core:check` in `modern/` (compile, Checkstyle, tests) and `bash tools/lint.sh` in the repository root. See [CODING_STANDARDS.md](CODING_STANDARDS.md).
- `tools/screenshots.ps1` runs each target on a hidden desktop through `core/dev/Harness`. Do not start a visible game for tests. Useful parameters: `-Targets`, `-Shots <file>` (a command file in `tools/harness/`), `-Extra`, `-OutDir`, `-Compat`, `-ShaderPack`, `-Config <file>` (a config.json for the start in place of the default config). A command file runs on all targets, except that 1.8.9 and 1.12.2 run the `*-legacy.txt` copy of a `*-modern.txt` file. The legacy files use the commands of 1.8.9; for 1.12.2 the script writes `summon` with the entity id in lower case, `type=!player`, and leaves out `achievement`. `-OptionLines <key:value>` adds lines to `options.txt`.
- The script writes `result.txt` for each target, next to the screenshots. The first line is `PASS` or `FAIL`, and the next lines give the reasons for a `FAIL`. A line of the harness log that starts with `!` is a reason, also `! unknown command` for an error in the command file and `! no such control` for a `ui` command whose label or slider the screen does not show. A screenshot that is not 1600 x 900 (or the size of the last `window` command) is a reason. A `skycompare` with no line `same` in the log is a reason, and the script stops when it cannot delete the harness folder of an earlier run. The exit code is 1 when a target fails.
- `tools/compare-jars.ps1 -Old <folder> -New build\libs` compares each entry of the built jars with reference jars, with no game. The reference is the 7 jars of the tag `v0.5.1`, the last release: build them from a worktree of that tag (`./gradlew --configure-on-demand :fabric:<v>:releaseJar :neoforge:<v>:releaseJar` for each version, and `./gradlew build` in `legacy/`). `tools/jar-changes.txt` gives the reason for each entry that can be different. The script fails for all other differences, and for a jar that is in only one of the two folders.
- `bash tools/check-jar.sh <jar> <loader> <node>` examines the mixin list, the Java level and the version ranges of a modern jar. CI runs it for each jar.
- With no `-Shots`, the script writes `docs/screenshots/<target>/` from `shots.txt`: one image for each feature, to find a version where a feature does not work. Make them again when a change affects what a player sees.
- Key harness commands: `sky <preset> [layers]` (a preset with the default turning stands still until `skyturn`), `skycompare`, `skytime [width height [yaw pitch fov]]` (GPU time of the sharp sky in a view full of sky), `fps [seconds]` (frame rate), `window <width> <height>`, `skyclock <s>|run` and `skypartial` (hold the time for screenshots), `fov <degrees>`, `clouds shown|hidden` (logs `! clouds` when the next frame does not draw the clouds as expected; `clouds-modern.txt` tests each state of the config), `ui click <label>`, `ui slide <percent> <id>`, `keepframe` and `framechanged <name>` (logs `! framechanged` when the middle fifth of the frame is the same as at `keepframe`; `shots.txt` uses it to find water that does not show), `hooks`, `quit`.
- `hooks` logs how many times each optional hook (`require = 0`) ran, for example `hooks: afterSolid=1807 tint=100562`. An optional hook with a wrong target name does not stop the game, so its counter stays 0. `tools/screenshots.ps1` adds `hooks` before `quit` in each run. It fails a target when the log has no `hooks` line or a hook of the game did not run: `afterSolid`, `tint`, `fullbright`, `shadow` and `skyPass` on a modern target, `fullbright`, `shadow`, `skyPass` and `sodiumTint` with `-Compat`, and `afterSolid`, `beforeClouds`, `tint`, `shade`, `reloadStep` and `textureUpload` on 1.8.9 and 1.12.2. On 1.8.9 and 1.12.2, `tint` and `shade` count the hooks of the light pipeline that draws the blocks (Forge or vanilla), not the OptiFine hooks. `skyPass` counts the calls of the flat sky hook `SkyMixin`, which goes into each `addSkyPass` overload because NeoForge 1.21.11 adds one and calls only that one. The other counters depend on the version: `clouds` counts the reads of the cloud option, `dryFoliage` runs from 1.21.5 and `itemIcon` from 1.21.6 with an item in the hotbar and the HUD on.
- Chroma harness commands (`tools/harness/chroma.txt`): `effects <category> [bevel N] [outline N] [chroma N] [speed N] [saturation N] [brightness N]`, `frames <count> <name>` (a screenshot at the end of each frame), `chromagpu off|on` (compare the GPU colors with the still CPU colors at the time 0), `chromacompare` (logs `! chromacompare` when a frame with the GPU colors at the time 0 is not the same as a frame with the still CPU colors) and `chromatime [frames]` (GPU and CPU time and GPU memory of the pass).
- `ui realclick` tests the input path of the adapter. Only the adapter knows the number of the left mouse button (26.x uses 1). 1.8.9 cannot do `realclick`, because LWJGL 2 reads the mouse from the system.
- Keyboard commands go through the input path of the adapter, as the window sends a key: `type <text>`, `key <name>`, `keydown <name>` and `keyup <name>` (names as in the config, for example `BACKSPACE`, `LEFT_CONTROL`, `F9`). Modern targets call the entry points of `KeyboardHandler` that the window events call. On 26.x, `type` logs `!` when SDL would drop the text, because SDL sends text only after the game starts text input. 1.8.9 calls the handlers of the key messages of the LWJGL 2 window. `ui expect <field id> <text>` and `expect open|closed` log `PASS` or `!`. `typing-modern.txt` types into each text field of the settings screen.
- After `quit`, the modern JVM stops when the game thread ends, because the Architectury dev run keeps non-daemon thread pools. Without this, 26.x writes a "Client shutdown from post-main" crash report.

## Known gaps

- Clouds on 1.21.2 and later support only `original` and `hidden`.
- `core/texture/Foliage` finds plants by texture name. A modded plant with a different name stays visible.
