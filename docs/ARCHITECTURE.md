# Architecture

Block Painter uses ports and adapters. The core contains almost all of the code and has no Minecraft types. Each Minecraft era has a small adapter.

```
+----------------------------------------------------------------+
| core/  (plain Java 8, no Minecraft types, unit tests)          |
|   config/    Config, Profile, CategorySettings, Rule,          |
|              Appearance, ConfigCodec, Json, Presets            |
|   Policy             all render decisions, from one snapshot   |
|   texture/           TextureClassifier, AtlasPaths, Foliage,   |
|                      PixelTransformer                          |
|   BlockPainter       init, processTexture, catalog, tick       |
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
| 15 mixins + 1 accessor      |   | 14 mixins                   |
+-----------------------------+   | BlockPainterForge (@Mod)    |
     |                  |         +-----------------------------+
 fabric/            neoforge/
 entrypoint,        entrypoint,
 Mod Menu button    mod list Config button
```

## Core

- The core is plain Java 8. It compiles into every jar from `core/src`. It does not change for a new Minecraft version.
- The core decides how everything looks: config, groups, matching, colors, pixel math, presets, keys and config reload.
- `Platform` is the port for the game: config folder, logging, resource reload, messages, key state, PNG read, clipboard, the settings screen and the harness host.
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
| Block tint | `ModelBlockRenderer` | `BlockModelRenderer`, Forge `BlockInfo` |
| Water tint | `BiomeColors.getAverageWaterColor` | `BiomeColorHelper.getWaterColorAtPos` |
| Fullbright | gamma read in `LightTexture`, 26.x `LightmapRenderStateExtractor` | gamma read in `EntityRenderer.updateLightmap` |
| Shading: smooth lighting | `Minecraft.useAmbientOcclusion`, 26.x `ModelBlockRenderer` constructor | `Minecraft.isAmbientOcclusionEnabled` |
| Shading: face shade | `ClientLevel.getShade`, 26.x `CardinalLighting.byFace` | `FaceBakery.getFaceBrightness` |
| Entity shadows | `EntityRenderer`, before 1.21.2 `EntityRenderDispatcher` | `Render.doRenderShadowAndFire` |
| Sky and fog color | `LevelRenderer` sky pass, `FogRenderer` | `RenderGlobal.renderSky`, `EntityRenderer.updateFogColor` |
| Clouds | `CloudRenderer.prepare` | - |
| Tick | `Minecraft.tick` | `Minecraft.runTick` |

Foliage has no hook of its own. The texture hooks set the alpha of plant textures to zero, and cut-out rendering does not draw those pixels.

The mod uses no loader API. Mixins do the tick, keys and config work, so Fabric does not need Fabric API. Keys use the Minecraft key name (for example `key.keyboard.f6`), because the key codes are different in each era. The eras use LWJGL 2 on 1.8.9, GLFW on 1.21.x and SDL on 26.x.

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
6. Run `./gradlew :fabric:<v>:runClient`. Make sure that the log shows `Block Painter ready` and `Flattened N textures`.

## Testing

- `./gradlew :core:test` in `modern/` tests the config model and codec, texture classification, pixel transforms and the settings screen. A fake `Canvas` records the position of each label. The tests click the labels and examine the saved config. A fuzz test clicks across the full screen at five GUI sizes.
- `tools/screenshots.ps1` runs each version on a hidden desktop through `core/dev/Harness`. It saves the same scene for each profile to `docs/screenshots/<target>/`.

## Known gaps

- Sodium, Embeddium and OptiFine replace block meshing, so the block tint hook does not run. The mod still removes biome grass and foliage tint. Fixed tints stay (birch and spruce leaves, lily pads, swamp grass). A fix needs one optional mixin for each renderer.
- Clouds on 1.21.2 and later support only `original` and `hidden`. A new color needs a hook on the cloud color.
- The game downloads player skins and does not load them as resources. Thus the `players` category changes only the default skins.
- `core/texture/Foliage` finds plants by texture name. A modded plant with a different name stays visible. To hide it, add a group with `"mode": "hidden"`.
