# Single Color

A client-side Minecraft mod that flattens chosen parts of the world (blocks, fluids, sky, clouds, particles,
entities...) to one flat color, each texture's own average color, or your own texture. It works like the flat white
or black "Quake" packs, so anything moving stands out against a calm background. Because it's applied in code
rather than shipped as replacement images, it doesn't break the way a resource pack can:

- **Every texture is covered**, including modded blocks and ones a pack forgot, because textures are rewritten as
  they load.
- **Biome tints are removed.** Grass and leaves stop being green and water stops being blue.
- **The sky really is flat.** The sky pass is skipped and fog uses the same color, so the horizon disappears.
- **Fullbright** is built in, so caves and night look like noon.
- **Shading can be switched off** (smooth lighting and darker sides), so surfaces are perfectly flat.
- **Foliage can be hidden**: grass, flowers, ferns, vines, and other small plants.
- **Clouds and weather can be hidden.**
- **Everything is configurable in game** and switches live.

| Vanilla | Quake white | Quake white, shading off | Quake black |
|---|---|---|---|
| ![vanilla](docs/screenshots/fabric-26.3/01-vanilla.png) | ![quake white](docs/screenshots/fabric-26.3/02-quake-white.png) | ![no shading](docs/screenshots/fabric-26.3/03-quake-white-no-shading.png) | ![quake black](docs/screenshots/fabric-26.3/04-quake-black.png) |

| Minimal | Night, vanilla | Night, quake black + fullbright | Settings screen |
|---|---|---|---|
| ![minimal](docs/screenshots/fabric-26.3/05-minimal.png) | ![night](docs/screenshots/fabric-26.3/06-night-vanilla.png) | ![night fullbright](docs/screenshots/fabric-26.3/07-night-quake-black-fullbright.png) | ![settings](docs/screenshots/fabric-26.3/08-settings.png) |

These are real in-game screenshots taken automatically by [`tools/screenshots.ps1`](tools/screenshots.ps1) (26.3
Fabric shown; every supported version is captured in [`docs/screenshots/`](docs/screenshots)).

## Supported versions

| Minecraft | Fabric | NeoForge | Forge |
|-----------|:------:|:--------:|:-----:|
| 1.8.9     |        |          | ✅    |
| 1.21.1    | ✅     | ✅       |       |
| 1.21.11   | ✅     | ✅       |       |
| 26.3      | ✅     | ✅       |       |

The Fabric build needs only Fabric Loader (no Fabric API). Adding more versions is mostly configuration; see
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Using it

Drop the jar for your loader and version into `mods/`. Three profiles come built in:

| Profile       | What it does |
|---------------|--------------|
| `quake-white` | Blocks, block entities, and sky are white; leaves are solid; fluids are light gray; biome tints are removed; foliage, clouds, and weather are hidden; fullbright. Mobs, players, items, and particles keep their textures. |
| `quake-black` | The same, in near-black. |
| `minimal`     | Each block becomes its own average color: detail is gone but blocks stay recognizable. |

| Key | Action |
|-----|--------|
| **F9** | Open the settings screen (also the Config button in Mod Menu or NeoForge's mod list) |
| **F6** | Turn the mod on or off |
| **F7** | Switch to the next profile |

### Settings screen

- **Profiles** (top bar): switch, copy, rename, or delete them; the switch at the top left turns the mod on or off.
- **Categories** (left): blocks, fluids, block entities (chests, signs, beds, paintings), items, mobs, players,
  particles, mining cracks, sky, clouds, and weather. The square next to each shows its current color.
- **World** (left, below the categories): *Fullbright*, *Shading* (smooth lighting and darker sides), and
  *Foliage* (grass, flowers, ferns, vines...).
- **Style:** *Vanilla*, *Color* (one flat color), *Average* (each texture's own average color), *Gray*,
  *Image* (your PNG from `config/singlecolor/textures/`), or *Hide*.
- **Color:** a saturation/brightness square, hue and opacity bars, a hex field (paste works), and a palette.
- **Shape:** *Keep* see-through parts, make them a hard *Cut-out*, or fill them in (*Solid*, e.g. solid leaves).
- **Remove biome tint** for blocks and fluids.
- **Textures:** every texture the game has loaded in that category, searchable. Each row shows the original color
  and the result. Click one to give that block its own style, or *Reset* it to follow its category.

*Apply* saves and shows the result; *Done* (or Esc) saves and closes. Fullbright and sky color apply instantly;
texture, shading, and foliage changes reload resources for a moment.

### Config file

Everything is stored in `config/singlecolor/config.json`, which you can also edit by hand; changes apply within a
second, and mistakes are reported in game with the line and column.

```jsonc
{
  "enabled": true,
  "activeProfile": "my-profile",
  "keys": { "toggle": "F6", "cycleProfile": "F7", "settings": "F9" },   // "NONE" disables a key
  "profiles": {
    "my-profile": {
      "fullbright": true,
      "shading": false,                                  // no smooth lighting or darker sides
      "foliage": false,                                  // hide grass, flowers, ferns, vines...
      "blocks": {
        "mode": "solid", "color": "#FFFFFF", "removeTint": true,
        "rules": [                                       // first match wins; unset fields follow the category
          { "match": "*leaves*", "alpha": "opaque" },
          { "match": ["*glass*", "*ice"], "color": "#80FFFFFF" },
          { "match": "*_ore", "mode": "original" },
          { "match": "mymod:*", "mode": "average" }
        ]
      },
      "fluids": { "mode": "solid", "color": "#D0D0D0", "removeTint": true },
      "sky":    { "mode": "solid", "color": "#202020" },
      "clouds": { "mode": "hidden" }
    }
  }
}
```

- **Category keys:** `blocks`, `fluids`, `block_entities`, `items`, `entities`, `players`, `particles`,
  `block_breaking`, `sky`, `clouds`, `weather`. Categories you leave out are untouched.
- **`mode`:** `original`, `solid`, `average`, `grayscale`, `texture` (with `"texture": "file.png"`), or `hidden`.
- **`alpha`:** `keep` (default), `binary`, or `opaque`.
- **Colors:** `#RGB`, `#RRGGBB`, `#AARRGGBB`, or a name such as `white`, `black`, or `gray`.
- **`match`:** glob patterns over texture ids like `minecraft:oak_leaves`. A pattern without a namespace matches
  any mod. The settings screen's texture list shows the ids.

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
cd modern && ./gradlew :core:test       # unit tests, including headless tests of the settings screen
```

To run a dev client: `./gradlew :fabric:26.3:runClient` (any loader or version) in `modern/`, or
`./gradlew runClientDev` in `legacy/`.

### Screenshots and the test harness

`tools/screenshots.ps1` (Windows) starts each version on an invisible desktop, so nothing appears on screen, with
sound and the narrator off. A file-driven test harness in the mod (enabled only by `-Pharness=<dir>`) creates a
superflat world, builds the test scene from `tools/harness/`, and captures the screenshots above for every profile.
See `core/src/main/java/dev/singlecolor/core/dev/Harness.java` for the commands.
