# Architecture

Albedo uses ports and adapters. The core contains almost all of the code and has no Minecraft types. Each Minecraft era has a small adapter.

```
+----------------------------------------------------------------+
| core/  (plain Java 8, no Minecraft types, unit tests)          |
|   config/    Config, Profile, CategorySettings, Rule,          |
|              Appearance, ConfigCodec, Json, Presets            |
|   Policy             all render decisions, from one snapshot   |
|   texture/           TextureClassifier, AtlasPaths, Foliage,   |
|                      PixelTransformer, TextureStore            |
|   Albedo             init, processTexture, catalog, tick       |
|   ui/                settings screen (SettingsEditor, Ui)      |
|   dev/Harness        screenshot harness (development only)     |
|   Platform, Canvas   ports that the adapters implement         |
+----------------------------------------------------------------+
             ^                                  ^
             | calls only the ports             |
+-----------------------------+   +-----------------------------+
| modern/common               |   | legacy/                     |
| Mojang names, Stonecutter   |   | MCP names, 1.8.9 Forge      |
| ModernPlatform              |   | LegacyPlatform              |
| NativeImagePixels           |   | BufferedImagePixels         |
| ModernHooks, SettingsScreen |   | LegacyHooks, GuiSettings    |
| ModernHarnessHost           |   | LegacyHarnessHost           |
| TextureUploads              |   |                             |
| 17 mixins + 4 accessors     |   | 16 mixins, MixinPlugin      |
+-----------------------------+   | AlbedoForge (@Mod)          |
     |                  |         +-----------------------------+
 fabric/            neoforge/
 entrypoint,        entrypoint,
 Mod Menu button    mod list Config button
```

## Core

- The core is plain Java 8. It compiles into every jar from `core/src`. It does not change for a new Minecraft version.
- The core decides how everything looks: config, groups, matching, colors, pixel math, presets, keys and config reload.
- `Platform` is the port for the game: config folder, logging, resource reload, chunk rebuild, repaint end, messages, key state, PNG read, clipboard, the settings screen and the harness host.
- `Canvas` is the port for drawing: `width`, `height`, `fill`, `text` and `textWidth`.

The settings screen is also in the core. It is an immediate-mode UI in `core/ui`. In each frame, `SettingsEditor` draws through `Canvas` and reads the input events that the adapter puts in a queue. The screen class of each version only forwards calls. Thus the screen is the same on all versions, and a new widget does not change version code.

The Textures tab shows the catalog that the core records when it processes textures. Thus it needs no registry access, and it includes modded textures.

## Hooks

The adapters change data at stable points, not in the renderers. The core processes decoded pixels, so the source of a texture (vanilla, resource pack or mod) is not important. Mixin handlers use plain values (`int`, `int[]`, `NativeImage`) where possible.

| Point | Modern hook | 1.8.9 hook |
|-------|-------------|------------|
| Atlas sprites, before mipmaps | `SpriteLoader.stitch` | `TextureAtlasSprite.loadSprite` |
| Other textures | `TextureContents.load` (1.21.4+), `SimpleTexture.doLoad` | `SimpleTexture.loadTexture` |
| Biome colormaps | `GrassColor`, `FoliageColor`, `DryFoliageColor` `init` | `ColorizerGrass`, `ColorizerFoliage` |
| Block tint | `ModelBlockRenderer`. Sodium and Embeddium: tint index in their `BlockRenderer` | `BlockModelRenderer`, Forge `BlockInfo` |
| Water tint | `BiomeColors.getAverageWaterColor` | `BiomeColorHelper.getWaterColorAtPos` |
| Fullbright | gamma read in `LightTexture`, 26.x `LightmapRenderStateExtractor` | gamma read in `EntityRenderer.updateLightmap` |
| Shading: smooth lighting | `Minecraft.useAmbientOcclusion`, 26.x `ModelBlockRenderer` constructor and Sodium `LevelSlice.useAmbientOcclusion` | `Minecraft.isAmbientOcclusionEnabled` |
| Shading: face shade | `ClientLevel.getShade`, 26.x `CardinalLighting.byFace` | quads into the chunk mesh: `BlockModelRenderer` `addVertexData`, Forge `VertexLighterFlat.updateColor` |
| Full reload progress | - | `SimpleReloadableResourceManager.notifyReloadListeners` |
| Entity shadows | `EntityRenderer`, before 1.21.2 `EntityRenderDispatcher` | `Render.doRenderShadowAndFire` |
| Sky and fog color | `LevelRenderer` sky pass, `FogRenderer` | `RenderGlobal.renderSky`, `EntityRenderer.updateFogColor` |
| Clouds | `CloudRenderer.prepare` | - |
| Tick | `Minecraft.tick` | `Minecraft.runTick` |

The Sodium and Embeddium hooks are `@Pseudo` mixins with `remap = false`. Their targets contain no Minecraft types. If the renderer is not installed, Mixin skips them. Sodium uses the vanilla atlas, colormaps, `BiomeColors`, face shade and lightmap, so the other hooks also apply to Sodium. To test with Sodium or Embeddium, add `-Pcompat=sodium` or `-Pcompat=embeddium` to a `runClient` task. This adds the mod to the dev run only. The versions are in `modern/versions/<v>/gradle.properties`. `tools/screenshots.ps1` has the parameters `-Compat`, `-Extra scene-compat.txt` and `-OutDir`.

Foliage has no hook of its own. The texture hooks set the alpha of plant textures to zero, and cut-out rendering does not draw those pixels.

### Repaint on 1.8.9

The general steps are in "Settings changes" below. On 1.8.9:

- `SpriteUpload` (atlas sprites) calls `loadSprite` with the new strip and `generateMipmaps` with the mip levels of the atlas. Then it puts the current frame into the atlas at the origin of the sprite. The frame and tick counters stay, so water, lava and fire continue to animate. OptiFine does its own sprite work in `loadSprite` (transparent pixel color, the "Multitexture" copy), so the repaint gets it too.
- `SimpleTextureUpload` writes the pixels into the GL texture with the blur and clamp of the first upload. A downloaded skin is not kept: its default skin placeholder must not take the place of the real default skin.
- An upload returns false when a reload replaced the texture (stale handle). The store then forgets it.
- `TextureStore` keeps the textures by name, because some textures share a key (the OptiFine connected textures of a block).
- `rebuildChunks` marks the chunks in render distance for a new mesh (`markBlockRangeForRenderUpdate`). The old meshes stay on screen until the new ones are ready. `loadRenderers` is not used: with Patcher, Essential and OptiFine L5 the chunks sometimes stayed empty after it.
- The models always keep the face shade. With shading off, `LegacyHooks.addQuad` and `vertexColor` put white in place of the shade grays when the quad goes into the chunk mesh. Thus a shading change only rebuilds the chunks.
- A full reload occurs only when an upload fails (OptiFine shaders, an unknown `SimpleTexture` subclass) or the store is over its memory budget. `ReloadScreen` then draws a progress bar for each reload listener and texture.

OptiFine on 1.8.9 changes `BlockModelRenderer`, `BlockFluidRenderer` and `FaceBakery`, and each OptiFine version uses different method names. `MixinPlugin` finds OptiFine and selects the mixins: `*VanillaMixin` only without OptiFine, `OptiFine*Mixin` only with OptiFine. `OptiFineRenderMixin` has no members. Its plugin step (`OptiFineRenderPatch`) finds the calls to `Block.colorMultiplier`, `CustomColors.getColorMultiplier`, `CustomColors.getFluidColor` and `FaceBakery.getFaceBrightness` (the OptiFine fluid renderer), by target and descriptor, and sends each result through `LegacyHooks`. It also changes the calls to `WorldRenderer.addVertexData` in `BlockModelRenderer` to `LegacyHooks.addQuad`. The classifier gives OptiFine textures (`optifine/ctm/`, `mcpatcher/ctm/`, `cit/`, `sky/`, `random/entity/`) the category of the vanilla texture that they replace. `tools/optifine.ps1` runs the release jar with OptiFine in a production Forge client for tests.

The mod uses no loader API. Mixins do the tick, keys and config work, so Fabric does not need Fabric API.

## Settings changes

A settings change (screen, key, harness or config file) goes to `Albedo.apply`. It saves the config, makes a new `Policy` and calls `Albedo.refresh`:

1. Sky, fog, fullbright, entity shadows and clouds: the hooks read the policy in each frame. They need no other step.
2. Textures: when a texture loads, its hook gives the core the original pixels and a `TextureStore.Upload`. `TextureStore.repaint` makes new pixels for each texture whose look changed, from its original, and calls its upload.
3. `Platform.finishRepaint`: the adapter sends the uploads that it collected to the GPU, all at once.
4. Block tints, fluid tints and shading are in the chunk meshes. If they change, `Platform.rebuildChunks` rebuilds the chunks.
5. Only when the repaint cannot do the change, `Platform.reloadResources` reloads all resources: an upload fails, or the store is over its memory limit. A texture that loads on a worker thread while the policy changes is checked again on the next tick.

The log shows `Repainted N textures in X ms` for a repaint and `Reloaded resources in X ms` for a reload.

Modern uploads are in `TextureUploads`:

| Texture | Upload |
|---------|--------|
| Atlas sprite | The upload only examines the sprite and keeps the pixels. `finishRepaint` does the work for all sprites: in parallel, it writes the pixels into `SpriteContents.originalImage` (all animation frames) and makes the mip levels again with `increaseMipLevel`. Then 1.21.1 binds the atlas and calls `uploadFirstFrame`. On 1.21.11 and later, the atlas draws each sprite with a border of its edge pixels (the padding). Thus each still sprite gets this border on the CPU and goes into the atlas texture with `CommandEncoder.writeToTexture`, one write for each mip level. Each animated sprite gets a new `SpriteContents.AnimationState` with new frame textures. |
| Stand-alone texture | Calls `ReloadableTexture.doLoad` (1.21.4 and later) or `SimpleTexture.doLoad` (1.21.1) with the new image, the same as a reload. |
| Biome colormap | Writes into the array that the game keeps (core `TextureStore.intoArray`). |

A resource reload (a resource pack change or F3+T) replaces sprites and textures. Thus each upload first finds the current object: the sprite in the atlas (`TextureAtlas.getSprite`) or the texture in `TextureManager`. If it is not the object that the hook saw, the upload returns false and the store forgets the texture. The reload records the new objects.

Chunk rebuild: `LevelRenderer.allChanged` on 1.21.x, `LevelExtractor.allChanged` on 26.x. Sodium replaces the vanilla rebuild that these start with its own. Keys use the Minecraft key name (for example `key.keyboard.f6`), because the key codes are different in each era. The eras use LWJGL 2 on 1.8.9, GLFW on 1.21.x and SDL on 26.x.

## Builds

| Build | Versions | Tools |
|-------|----------|-------|
| `modern/` | 1.21.1, 1.21.11, 26.3 (Fabric and NeoForge) | [Stonecutter](https://stonecutter.kikugie.dev) 0.9, Architectury Loom 1.17 (`loom` for 1.21.11 and earlier, `loom-no-remap` for 26.x), Gradle 9.8 |
| `legacy/` | 1.8.9 (Forge) | Essential fork of Architectury Loom 0.10, Mixin 0.7.11, Gradle 8.8. Gradle runs on JDK 21. The game runs on Java 8. |

One tool chain cannot support both 1.8.9 Forge and 26.x, so there are two builds. Both use `core/src`.

In `modern/`, Stonecutter comments mark the version differences. They are only in mixin and adapter files:

```java
//? if >=26.1 {
@ModifyExpressionValue(method = "computeTintColor", at = @At(value = "INVOKE", target = "...BlockTintSource;colorInWorld..."))
//?} else {
/*@ModifyExpressionValue(method = "putQuadData", at = @At(value = "INVOKE", target = "...BlockColors;getColor..."))
*///?}
```

## Add a Minecraft version (modern)

1. Add `modern/versions/<v>/gradle.properties`. Add `modern/neoforge/versions/<v>/gradle.properties` with `loom.platform=neoforge`.
2. Add `<v>` to the `versions` list in `modern/settings.gradle`.
3. Run `./gradlew :fabric:<v>:build :neoforge:<v>:build` in `modern/`. The compile errors show the hooks that moved.
4. Fix each error with a `//? if` branch.
5. Examine the condition boundaries (`>=1.21.2`, `>=1.21.4`, `>=1.21.5`, `>=1.21.6`, `>=1.21.9`, `>=1.21.11`, `>=26.1`). The project tests them only on the built versions.
6. Run `./gradlew :fabric:<v>:runClient`. Make sure that the log shows `Albedo ready` and `Flattened N textures`.

## Testing

- `./gradlew :core:test` in `modern/` tests the config model and codec, texture classification, pixel transforms and the settings screen. A fake `Canvas` records the position of each label. The tests click the labels and examine the saved config. A fuzz test clicks across the full screen at five GUI sizes.
- `tools/screenshots.ps1` runs each version on a hidden desktop through `core/dev/Harness`. It saves the same scene for each profile to `docs/screenshots/<target>/`.
- `tools/harness/repaint-modern.txt` and `repaint-legacy.txt` change the settings in a running game and take a screenshot after each change. `repaint-reload.txt` does the same after a resource reload. Compare the screenshots with a run where each change reloads resources. Only animated textures, particles and clouds can differ.

## Known gaps

- The Sodium and Embeddium hooks use internal method names of these mods. A new version can rename them. Then the hook does not apply (`require = 0`) and fixed tints stay. Test each new version with `-Pcompat` and `tools/harness/scene-compat.txt`.
- 1.8.9 with OptiFine: the project tests only OptiFine HD U M6 pre2 (with `tools/optifine.ps1`). OptiFine shaders are not tested.
- Clouds on 1.21.2 and later support only `original` and `hidden`. A new color needs a hook on the cloud color.
- The game downloads player skins and does not load them as resources. Thus the `players` category changes only the default skins.
- `core/texture/Foliage` finds plants by texture name. A modded plant with a different name stays visible. To hide it, add a group with `"mode": "hidden"`.
