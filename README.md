# Single Color

A client-side Minecraft mod that flattens chosen parts of the world (blocks, fluids, sky, clouds, particles,
entities...) to one flat color, a per-texture average, or your own texture. It works like the flat white or black
"Quake" texture packs, but is applied in code, so it doesn't break the way a resource pack can:

- **Every texture is covered**, including modded blocks and ones a pack forgot, because textures are rewritten as
  they load rather than replaced file by file.
- **Biome tints are removed.** Grass and leaves stop being green and water stops being blue (packs can't do this).
- **The sky really is flat.** The sky pass is skipped and the fog/clear color is set, so the horizon blends in.
- **Clouds and weather can be hidden.**
- **It toggles live.** F6 switches it on or off, F7 cycles profiles, and edits to the config file apply within a second.

## Supported versions

| Minecraft | Fabric | NeoForge | Forge |
|-----------|:------:|:--------:|:-----:|
| 1.8.9     |        |          | ✅    |
| 1.21.1    | ✅     | ✅       |       |
| 1.21.11   | ✅     | ✅       |       |
| 26.3      | ✅     | ✅       |       |

Every jar has been started in a real dev client and flattens textures at startup. The Fabric build needs only
Fabric Loader (no Fabric API). Adding more versions is mostly configuration; see
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Using it

Drop the jar for your loader and version into `mods/`. On first launch the mod writes
`config/singlecolor/config.json` with three profiles:

| Profile       | What it does |
|---------------|--------------|
| `quake-white` | Blocks, block entities, and sky are white; fluids are light gray; tints are removed; clouds and weather are hidden. Mobs, players, items, and particles keep their textures. |
| `quake-black` | The same, in near-black. |
| `minimal`     | Each block becomes its own average color: detail is gone but blocks stay recognizable. |

Keys (configurable, raw keys so they work the same on every version): **F6** toggles the mod, **F7** cycles profiles.

### Config

```jsonc
{
  "enabled": true,
  "activeProfile": "my-profile",
  "keys": { "toggle": "F6", "cycleProfile": "F7" },   // "NONE" disables a key
  "profiles": {
    "my-profile": {
      "blocks": {
        "mode": "solid", "color": "#FFFFFF", "removeTint": true,
        "rules": [                                       // first match wins; unset fields inherit
          { "match": ["*glass*", "*ice"], "color": "#80FFFFFF" },
          { "match": "*_ore", "mode": "original" },
          { "match": "mymod:*", "mode": "average" }
        ]
      },
      "fluids":    { "mode": "solid", "color": "#D0D0D0", "removeTint": true },
      "sky":       { "mode": "solid", "color": "#202020" },
      "clouds":    { "mode": "hidden" },
      "particles": { "mode": "solid", "color": "#FF0000", "alpha": "binary" },
      "entities":  { "mode": "texture", "texture": "checker.png" }
    }
  }
}
```

- **Categories:** `blocks`, `fluids`, `block_entities` (chests, beds, signs, banners, paintings), `colormaps`,
  `block_breaking`, `items`, `entities`, `players`, `particles`, `sky`, `clouds`, `weather`, `other`.
  Categories you leave out are untouched.
- **Modes:** `original`, `solid` (needs `color`), `average`, `grayscale`, `texture` (a PNG in
  `config/singlecolor/textures/`), `hidden`.
- **`alpha`:** `keep` (default; glass stays see-through and leaves keep their holes), `binary`, or `opaque`.
- **Colors:** `#RGB`, `#RRGGBB`, `#AARRGGBB`, or a name such as `white`, `black`, or `gray`.
- **`match`:** glob patterns over ids like `minecraft:oak_leaves`. A pattern without a namespace matches any mod.
- **`sky`:** `solid` sets the sky and fog to that color and hides the sun, moon, and stars.

Mistakes in the file are reported in-game with the line and column, and the previous settings stay active.

## Building

You need JDK 21 and 25 installed; Gradle downloads JDK 8 for 1.8.9 if it's missing. The project has three
independent Gradle builds:

```bash
cd modern && ./gradlew collectJars      # all Fabric/NeoForge jars -> modern/build/libs/
```

```bash
cd legacy && ./gradlew build            # 1.8.9 Forge jar -> legacy/build/libs/
```

```bash
cd modern && ./gradlew :core:test       # unit tests for the version-agnostic core
```

To run a dev client: `./gradlew :fabric:26.3:runClient` (any loader or version) in `modern/`, or
`./gradlew runClientDev` in `legacy/`.
