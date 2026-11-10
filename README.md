# Albedo

Albedo is a client-side Minecraft mod that lets you paint blocks, fluids, the sky, mobs and the rest of the world in any color. Give each part one flat color, or recolor its textures and keep their detail.

Albedo changes textures as they load, so it also works with modded blocks and resource packs.

![Vanilla, the minimal profile with the Galaxy sky, and quake-white](docs/gallery/01-split.png)

## Features

### Paint the world

- **Profiles:** start from `quake-white`, `quake-black`, `minimal` or `pastel`, or make your own. Export and import them as files.
- **Looks:** give each category (blocks, fluids, mobs, players, particles, sky and more) a flat color, a recolor, the average color, gray, or hide it.
- **Effects:** Bevel shades the edges of each block, Outline draws a line around it, and Chroma moves its colors through all hues.
- **Groups:** give a set of textures their own look, for example dirt, grass and gravel in one color.

### Build your sky

- **Layers:** stack gradients, glows, clouds, stars, galaxies, meteors and auroras, with filters such as blur and bloom.
- **Presets:** Dusk, Nebula, Galaxy, Meteors, Aurora and Trails.
- **Real stars:** star colors from real star classes, with soft glow, fine spikes and twinkle.
- **Motion:** the sky turns around a pole like the real night sky, with optional star trails.
- **Sharp:** your graphics card draws each pixel, so the sky stays sharp at all zoom levels.

### Control

- **Servers:** give each server its own profile, or turn the mod off there.
- **World options:** fullbright, shading, foliage and entity shadows.
- **Keys:** F9 opens the settings, F6 turns the mod on or off, and F7 selects the next profile.

## Install

Put the jar for your Minecraft version and loader in your `mods/` folder. On Fabric you need only Fabric Loader, not Fabric API.

| Minecraft | Fabric | NeoForge | Forge |
|-----------|:------:|:--------:|:-----:|
| 1.8.9     |        |          | Yes   |
| 1.21.1    | Yes    | Yes      |       |
| 1.21.11   | Yes    | Yes      |       |
| 26.3      | Yes    | Yes      |       |

## Config file

All settings are in `config/albedo/config.json`, including the keys. Albedo loads a change to the file within one second and shows any error in the game, with its place in the file.

```json
{
  "activeProfile": "my-profile",
  "servers": { "hypixel.net": "quake-black" },
  "profiles": {
    "my-profile": {
      "fullbright": true,
      "blocks": {
        "mode": "solid",
        "color": "#FFFFFF",
        "groups": [{ "name": "Ground", "match": ["dirt", "grass_block*"], "color": "#7A5C3A" }]
      },
      "clouds": { "mode": "hidden" }
    }
  }
}
```

## Compatibility

- **Sodium and Embeddium:** all features operate.
- **OptiFine on 1.8.9:** all features operate. The custom sky shows when the OptiFine setting Sky is on. With an OptiFine shader pack, the sky stays vanilla.
- **Iris:** with shaders off, all features operate. Many shader packs draw their own sky, light and shadows. On 1.21.1 with a shader pack, the sky stays vanilla.

## Known limits

- On 1.21.2 and later, you can only hide clouds, not color them.
- Hidden foliage also hides the items of those plants.

## Contributing

To build the mod or work on it, see [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## License

Apache License 2.0. See [LICENSE](LICENSE).
