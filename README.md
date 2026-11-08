# Albedo

Albedo is a client-side Minecraft mod that paints the world in flat colors. Blocks, fluids, the sky, mobs and other parts of the world can each get one plain color, so anything that moves is easy to see.

Albedo changes textures as they load, so it also works with modded blocks and resource packs.

| Vanilla | quake-white | quake-black |
|---|---|---|
| ![vanilla](docs/screenshots/fabric-26.3/01-vanilla.png) | ![quake-white](docs/screenshots/fabric-26.3/02-quake-white.png) | ![quake-black](docs/screenshots/fabric-26.3/05-quake-black.png) |

## Install

1. Download the jar for your Minecraft version and loader.
2. Put it in your `mods/` folder.
3. Start the game.

| Minecraft | Fabric | NeoForge | Forge |
|-----------|:------:|:--------:|:-----:|
| 1.8.9     |        |          | Yes   |
| 1.21.1    | Yes    | Yes      |       |
| 1.21.11   | Yes    | Yes      |       |
| 26.3      | Yes    | Yes      |       |

On Fabric you need only Fabric Loader. You do not need Fabric API.

## Keys

| Key | What it does |
|-----|--------------|
| F9 | Opens the settings screen |
| F6 | Turns the mod on or off |
| F7 | Selects the next profile |

You can change these keys in the config file.

## Profiles

A profile is a full set of settings. Albedo comes with three:

- **quake-white** makes the world white, with fullbright on and clouds, weather and small plants hidden.
- **quake-black** is the same in near-black.
- **minimal** gives each block its own average color.

You cannot change the built-in profiles directly. When you edit one, Albedo puts your changes in a copy, such as `quake-white-custom`, and uses that copy from then on.

## Settings

Push F9 to open the settings screen. On Fabric you can also use the Config button in Mod Menu, and on NeoForge the Config button in the mod list.

The list on the left has one entry for each part of the world, such as Blocks, Mobs and entities, or Sky. Select one to change how it looks. Changes show in the world right away. Push Done to save them or Discard to drop them.

Keep the pointer on a control to see what it does.

### Styles

Each part of the world has a style:

- **Vanilla** keeps the normal textures.
- **Color** paints every texture one flat color.
- **Recolor** changes the color of each texture but keeps its detail. Use the Detail, Colors and Light sliders to tune how much of the original texture stays.
- **Average** gives each texture its own average color.
- **Gray** removes the color and keeps the detail.
- **Image** uses a PNG of your own from `config/albedo/textures/`.
- **Hide** makes the textures invisible.

### Groups

A group gives some textures a different look from the rest of their category. For example, a group can make grass and dirt green while all other blocks stay white.

To make a group, open the Groups tab and push New group. Then type the names of the textures you want in it. Albedo suggests matching names as you type. Push Enter to add one.

A few tips:

- `*` matches any text. `grass_block*` covers all sides of the grass block, and `*` alone covers the whole category.
- Text between two slashes is a regular expression, for example `/^(oak|birch)_/`.
- If a texture is in two groups, the higher group in the list wins. Use the arrows to move a group up or down.
- The Textures tab lists every texture in the category. Click one to put it in a group.

Groups work the same way on all versions, also for particles and paintings on 1.8.9.

### Servers

The Servers page lets you pick a profile for each multiplayer server, or turn Albedo off there. When you leave the server, your own profile comes back.

## Config file

All settings are saved in `config/albedo/config.json`. You can also edit this file by hand. Albedo loads your changes within one second and shows any errors in the game.

```json
{
  "enabled": true,
  "activeProfile": "my-profile",
  "keys": { "toggle": "F6", "cycleProfile": "F7", "settings": "F9" },
  "servers": { "hypixel.net": "quake-black", "example.org": false },
  "profiles": {
    "my-profile": {
      "fullbright": true,
      "shading": false,
      "blocks": {
        "mode": "solid",
        "color": "#FFFFFF",
        "groups": [
          { "name": "Ground", "match": ["dirt", "grass_block*"], "color": "#7A5C3A" },
          { "name": "Ores", "match": "*_ore", "mode": "original" }
        ]
      },
      "sky": { "mode": "solid", "color": "#202020" },
      "clouds": { "mode": "hidden" }
    }
  }
}
```

The parts of the world are `blocks`, `fluids`, `block_entities`, `decorations`, `items`, `entities`, `players`, `particles`, `block_breaking`, `sky`, `clouds` and `weather`. The styles are `original`, `solid`, `recolor`, `average`, `grayscale`, `texture` and `hidden`.

To share a profile, push Export in the settings screen. This saves it to `config/albedo/profiles/` and copies it to the clipboard. To add a profile that someone gave you, copy its text and push Import.

## Works with

- **Sodium and Embeddium:** everything works.
- **OptiFine on 1.8.9:** everything works. Shaders are not tested.
- **Iris:** everything works with shaders off. Many shader packs draw their own sky, clouds, light and shadows. With those packs, the sky, clouds, fullbright, shading and shadow settings have no effect.

## Known limits

- On 1.21.2 and later, clouds can only be hidden, not colored.
- Downloaded player skins do not change.
- When small plants are hidden, their items are hidden too.
- With OptiFine shaders or very large resource packs, a settings change reloads all resources, which can take a while.

## Contributing

To build the mod or work on it, see [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## License

MIT. See [LICENSE](LICENSE).
