# Architecture

The goal is that almost everything is written once and never touches Minecraft, and that the per-version part is
small enough to port in an afternoon. The design is **ports and adapters** (hexagonal):

```
                ┌──────────────────────── core/ (plain Java 8, no Minecraft, unit-tested) ───────────────────────┐
                │  config: Config / Profile / CategorySettings / Rule / Appearance  ◄── ConfigCodec + Json       │
                │  Policy            every rendering decision, from one immutable config snapshot (thread-safe)   │
                │  TextureClassifier path → Category + id   (handles 1.8 "blocks/" and 1.13+ "block/")           │
                │  AtlasPaths        restores folders that modern atlases strip from sprite names               │
                │  PixelTransformer  solid / average / grayscale / texture / hidden, on a PixelBuffer            │
                │  BlockPainter      facade: init, processTexture, texture catalog, tick (keys, config watching) │
                │  ui/               the whole settings screen: an immediate-mode toolkit + SettingsEditor      │
                │  dev/Harness       file-driven test harness for hidden-desktop screenshots (dev only)         │
                │  Platform (port)   dirs, logging, reload, keys, images, open settings; Canvas (drawing port) │
                └──────────────────────────────────────────────▲──────────────────────────────────────────────┘
                                                               │ calls only these
          ┌────────────────────────────────────────────────────┴──────────────────────────────────────┐
  modern/common (Mojang names, Stonecutter)                                   legacy/ (MCP names)
  ModernPlatform, NativeImagePixels, ModernHooks,                             LegacyPlatform, BufferedImagePixels,
  SettingsScreen (Canvas over GuiGraphics), harness host                      LegacyHooks, GuiSettings, harness host
  + ~13 one-line mixins                                                       + 11 one-line mixins
          │                    │                                                       │
  fabric/ entrypoint     neoforge/ entrypoint                                 Forge @Mod class
  + Mod Menu button      + mod list "Config" button
```

**The settings screen is in the core too.** It is an immediate-mode UI (`core/ui`): every frame the editor calls
widget methods that draw themselves through a five-method `Canvas` port (`fill`, `text`, `textWidth`, size) and
report clicks, drags and typing queued by the adapter. Each version's screen class is ~100 lines of forwarding,
so the UI looks and behaves identically on 1.8.9 and 26.3, and new widgets never touch version code. The list of
textures it offers for per-texture overrides is the catalog the core records while processing textures, so it
needs no registry access and includes modded textures.

## Why these choices

**The core has zero Minecraft types.** Everything that decides *what* things look like (config, rules, matching,
colors, pixel math, presets, keys, hot reload) is ordinary Java 8 with unit tests. It compiles into every jar
from `core/src` and never changes for a new Minecraft version.

**Hooks are placed on the most stable seams.** Instead of rewriting renderers (which change constantly, e.g. 26.x's
new render API), the adapters intercept data at the few points that have survived a decade of refactors:

| Seam | Why it's stable | Modern hook | 1.8.9 hook |
|------|-----------------|-------------|------------|
| Atlas sprites, before mipmaps | every atlas is stitched in one place | `SpriteLoader.stitch` | `TextureAtlasSprite.loadSprite` |
| Stand-alone textures | single loader for `textures/**.png` | `TextureContents.load` / `SimpleTexture` | `SimpleTexture.loadTexture` |
| Biome colormaps | `init(int[])` unchanged since 1.13 | `GrassColor` / `FoliageColor` / `DryFoliageColor` | `ColorizerGrass` / `ColorizerFoliage` |
| Block tint call site | one per renderer | `ModelBlockRenderer` | `BlockModelRenderer` + Forge `BlockInfo` |
| Fullbright | the lightmap reads one brightness option | gamma read in `LightTexture` / 26.x `LightmapRenderStateExtractor` | gamma read in `EntityRenderer.updateLightmap` |
| Shading off: smooth lighting | one switch read while meshing | `Minecraft.useAmbientOcclusion` / 26.x `ModelBlockRenderer` constructor | `Minecraft.isAmbientOcclusionEnabled` |
| Shading off: face shade | one per-face brightness lookup | `ClientLevel.getShade` / 26.x `CardinalLighting.byFace` | `FaceBakery.getFaceBrightness` (baked into models) |
| Foliage off | no hook at all: plant textures get zero alpha, which cutout rendering skips | core `Foliage` + texture hooks | same |
| Water tint | every loader funnels here | `BiomeColors.getAverageWaterColor` | `BiomeColorHelper.getWaterColorAtPos` |
| Sky / fog / clear color | one sky pass, one fog color | `LevelRenderer` sky pass + `FogRenderer` | `RenderGlobal.renderSky` + `EntityRenderer.updateFogColor` |
| Tick | always exists | `Minecraft.tick` | `Minecraft.runTick` |

Because processing happens on decoded pixels, it doesn't matter how a texture got into the game (vanilla, a
resource pack, or a mod). Mixin handlers take only plain values (`int`, `int[]`, `NativeImage`) wherever possible,
so a class moving package doesn't break them.

**No loader APIs.** Ticking, keys and config are done with mixins and raw key polling, so Fabric needs no Fabric
API and the NeoForge and Fabric entrypoints are five lines each. Keys are resolved by Minecraft's own name
(`key.keyboard.f6`) because the code space changed: GLFW codes through 1.21.x, SDL scancodes in 26.x, and LWJGL 2
codes on 1.8.9.

## Build tooling

| Build | Versions | Tooling |
|-------|----------|---------|
| `modern/` | 1.21.1 – 26.3, Fabric + NeoForge | [Stonecutter](https://stonecutter.kikugie.dev) 0.9 (one source tree, comment-based version conditions) + Architectury Loom 1.17 (`loom` for obfuscated ≤1.21.11, `loom-no-remap` for unobfuscated 26.x), Gradle 9.8 |
| `legacy/` | 1.8.9 Forge | Essential's fork of Architectury Loom 0.10 + Mixin 0.7.11, Gradle 8.8 (the build itself runs on JDK 21 via `gradle-daemon-jvm.properties`; the game runs on Java 8) |

No single toolchain covers both 1.8.9 Forge and 26.x today, so there are two builds sharing `core/src`. A third
era (for example 1.12.2 Forge or 1.16–1.20 Fabric/Forge) would be another node in `modern/` if Architectury Loom
supports it, or another sibling build like `legacy/`.

Version differences inside `modern/` look like this, and are confined to the mixin and adapter files:

```java
//? if >=26.1 {
@ModifyExpressionValue(method = "computeTintColor", at = @At(value = "INVOKE", target = "...BlockTintSource;colorInWorld..."))
//?} else {
/*@ModifyExpressionValue(method = "putQuadData", at = @At(value = "INVOKE", target = "...BlockColors;getColor..."))
*///?}
```

## Adding a Minecraft version (modern)

1. Add `versions/<v>/gradle.properties` (and `neoforge/versions/<v>/gradle.properties` with `loom.platform=neoforge`)
   and list `<v>` in `settings.gradle`.
2. `./gradlew :fabric:<v>:build :neoforge:<v>:build`; compile errors point at exactly the hooks that moved.
   Fix them with a `//? if` branch. The condition boundaries between the built nodes (`>=1.21.2`, `>=1.21.4`,
   `>=1.21.6`, `>=1.21.9`, `>=26.1`) are best guesses and only verified at the built nodes, so check them when you
   add a version in between.
3. `./gradlew :fabric:<v>:runClient`. The log should show `Block Painter ready` and a `Flattened N textures` line.

## Known gaps / next steps

- **Sodium / Embeddium / OptiFine:** they replace chunk meshing, so the call-site tint hook doesn't run. The colormap
  hook still removes biome grass/foliage tints, but hard-coded tints (birch/spruce leaves, lily pads, swamp grass)
  stay. Fix: one optional compat mixin per renderer.
- **Clouds on 1.21.2+** support `hidden` and `original` only; recoloring needs a hook on the cloud color.
- **Player skins** are downloaded rather than loaded as resources, so the `players` category only affects the
  default skins for now.
- **Foliage** is matched by texture name (`core/texture/Foliage`), so a modded plant with an unusual name stays
  visible; add a rule with `"mode": "hidden"` for it.

## Testing

- `./gradlew :core:test` covers the config model and codec, classification, pixel transforms, and the settings
  screen itself: a fake `Canvas` records where labels are drawn, so tests click real labels and check the saved
  config, and a fuzz test clicks across the whole screen at five GUI sizes.
- `tools/screenshots.ps1` runs every version on a hidden desktop through the harness (`core/dev/Harness`) and
  captures the same scene for each profile into `docs/screenshots/<target>/`, so a visual regression on any
  version is one script away.
