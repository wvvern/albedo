# Block Painter

Block Painter is a client-side Minecraft mod. It changes blocks, fluids, sky, mobs and other parts of the world to one flat color.

It works like the flat "Quake" texture packs: things that move are easy to see. The mod changes textures as they load, so it also works with modded blocks and other resource packs.

| Vanilla | Quake white | Quake white, shading off |
|---|---|---|
| ![vanilla](docs/screenshots/fabric-26.3/01-vanilla.png) | ![quake white](docs/screenshots/fabric-26.3/02-quake-white.png) | ![no shading](docs/screenshots/fabric-26.3/03-quake-white-no-shading.png) |

| Quake white, shadows off | Quake black | Minimal |
|---|---|---|
| ![no shadows](docs/screenshots/fabric-26.3/04-quake-white-no-shadows.png) | ![quake black](docs/screenshots/fabric-26.3/05-quake-black.png) | ![minimal](docs/screenshots/fabric-26.3/06-minimal.png) |

| Night, vanilla | Night, quake black and fullbright | Settings screen |
|---|---|---|
| ![night](docs/screenshots/fabric-26.3/07-night-vanilla.png) | ![night fullbright](docs/screenshots/fabric-26.3/08-night-quake-black-fullbright.png) | ![settings](docs/screenshots/fabric-26.3/09-settings.png) |

The screenshots show Fabric 26.3. Screenshots for the other versions are in [docs/screenshots](docs/screenshots).

## Supported versions

| Minecraft | Fabric | NeoForge | Forge |
|-----------|:------:|:--------:|:-----:|
| 1.8.9     |        |          | Yes   |
| 1.21.1    | Yes    | Yes      |       |
| 1.21.11   | Yes    | Yes      |       |
| 26.3      | Yes    | Yes      |       |

The Fabric jar needs only Fabric Loader. It does not need Fabric API.

## Install and use

1. Put the jar for your loader and Minecraft version in the `mods/` folder.
2. Start the game.
3. Push F9 to open the settings screen.

| Key | Action |
|-----|--------|
| F9 | Open the settings screen. The Config button in Mod Menu or in the NeoForge mod list also opens it. |
| F6 | Turn the mod on or off. |
| F7 | Select the next profile. |

You can change the keys in the config file.

### Profiles

| Profile | Result |
|---------|--------|
| `quake-white` | Blocks, block entities and sky are white. Leaves are solid. Fluids are light gray. The profile removes biome tint and hides foliage, clouds and weather. Fullbright is on. |
| `quake-black` | The same as `quake-white`, in near-black. |
| `minimal` | Each block becomes its own average color. |

### Settings screen

- **Profile bar:** select, copy, rename or delete a profile.
- **Export and Import:** Export saves the profile to `config/blockpainter/profiles/<name>.json` and copies it to the clipboard. To add a profile, copy the text of a profile file and push Import. Import never replaces a profile. If the name is in use, it adds a number.
- **Categories (left list):** each category has a color square that shows its current color.
- **World page:** Fullbright, Shading (smooth lighting and darker block sides), Foliage (grass, flowers, ferns, vines and other small plants) and Entity shadows.
- **Look tab:** style (Vanilla, Color, Average, Gray, Image, Hide), color picker with hex field and palette, shape (Keep, Cut-out, Solid), and Remove biome tint for blocks and fluids.
- **Textures tab:** all loaded textures of the category. Click a texture to put it in a group.
- **Groups tab:** a group is a named set of blocks with its own look.

To add blocks to a group, type a block name. The input shows matching textures, with whole blocks first (for example, `grass_block*` covers all faces). Push Up or Down to select a texture. Push Enter to add it. Push Tab to complete the name. A `*` matches any text. Each block shows as a chip with a remove button.

If a block is in two groups, the first group in the list wins. Each category has a scope:

- **Everything:** the category look applies to all blocks, and groups change their blocks. A group set to Vanilla keeps its blocks unchanged.
- **Only groups:** only blocks in groups change. A group set to Global uses the category look.

Apply saves and shows the result. Done or Esc saves and closes the screen. Fullbright, shadows and sky color change immediately. Texture, shading and foliage changes reload resources.

## Config file

The settings are in `config/blockpainter/config.json`. You can edit this file by hand. The mod reloads it within one second and shows errors in game with their location. Unknown keys are errors.

```json
{
  "enabled": true,
  "activeProfile": "my-profile",
  "keys": { "toggle": "F6", "cycleProfile": "F7", "settings": "F9" },
  "profiles": {
    "my-profile": {
      "fullbright": true,
      "shading": false,
      "foliage": false,
      "shadows": true,
      "blocks": {
        "mode": "solid",
        "color": "#FFFFFF",
        "removeTint": true,
        "groups": [
          { "name": "Ground", "match": ["dirt", "grass_block*", "gravel"], "color": "#7A5C3A" },
          { "name": "Leaves", "match": "*leaves*", "alpha": "opaque" },
          { "name": "Ores", "match": "*_ore", "mode": "original" }
        ]
      },
      "fluids": { "mode": "solid", "color": "#D0D0D0", "removeTint": true },
      "sky": { "mode": "solid", "color": "#202020" },
      "clouds": { "mode": "hidden" }
    }
  }
}
```

- **Profile toggles:** `fullbright`, `shading`, `foliage`, `shadows`.
- **Category keys:** `blocks`, `fluids`, `block_entities`, `items`, `entities`, `players`, `particles`, `block_breaking`, `sky`, `clouds`, `weather`.
- **Category fields:** `mode`, `color`, `texture`, `alpha`, `removeTint`, `onlyGroups`, `groups`.
- **`mode`:** `original`, `solid`, `average`, `grayscale`, `texture` or `hidden`. Sky supports `original` and `solid`. Clouds support `original` and `hidden`. Weather supports `original`, `solid` and `hidden`.
- **`color`:** `#RGB`, `#RRGGBB`, `#AARRGGBB` or a name such as `white`.
- **`texture`:** a PNG file name in `config/blockpainter/textures/`.
- **`alpha`:** `keep`, `binary` or `opaque`.
- **Group fields:** `name`, `match` (a pattern or a list of patterns), and `mode`, `color`, `texture`, `alpha`. A field that the group does not set comes from the category. A group with no look fields uses the category look.

## Building

You need JDK 21 and JDK 25. If JDK 8 is missing, Gradle gets it for 1.8.9.

```bash
cd modern && ./gradlew collectJars   # Fabric and NeoForge jars -> modern/build/libs/
cd legacy && ./gradlew build         # 1.8.9 Forge jar -> legacy/build/libs/
cd modern && ./gradlew :core:test    # tests
```

To start a dev client, run `./gradlew :fabric:26.3:runClient` in `modern/` (any loader or version) or `./gradlew runClientDev` in `legacy/`.

For design details, see [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Releases

1. Push a tag such as `v1.0.0`.
2. CI builds all jars and makes a GitHub release.
3. CI uploads the jars to Modrinth (slug `blockpainter`) if the repository variable `MODRINTH_PROJECT_ID` and the secret `MODRINTH_TOKEN` exist.

## Screenshots

`tools/screenshots.ps1` (Windows) starts each version on a hidden desktop with sound and narrator off. It builds a test scene and saves the screenshots to `docs/screenshots/<target>/`.

## Known limits

- Sodium, Embeddium and OptiFine replace block meshing. Fixed tints (birch and spruce leaves, lily pads, swamp grass) do not change. The mod still removes biome grass and foliage tint.
- On 1.21.2 and later, you can only hide clouds.
- Downloaded player skins do not change.
- Hidden foliage also hides the items of those plants.
