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
- **Stars:** the colors of real stars, from blue-white to orange, with a soft glow, fine spikes and twinkle.
- **Motion:** the sky turns around a pole like the real night sky, with optional star trails.
- **Sharp:** your graphics card draws each pixel, so the sky stays sharp at all zoom levels.

### Control

- **Servers:** give each server its own profile, or turn the mod off there.
- **World options:** fullbright, shading, foliage and entity shadows.
- **Keys:** F9 opens the settings, F6 turns the mod on or off, and F7 selects the next profile.

## Install

Put the jar for your Minecraft version and loader in your `mods/` folder. Fabric API is not needed. The Fabric jar is also the Quilt jar.

| Minecraft | Fabric | Quilt | NeoForge | Forge |
|-----------|--------|:-----:|:--------:|:-----:|
| 1.8.9     |        |       |          | Yes   |
| 1.12.2    |        |       |          | Yes   |
| 1.16.5    | Yes    | Yes   |          | Yes   |
| 1.17.1    | Yes    | Yes   |          | Yes   |
| 1.18.2    | Yes    | Yes   |          | Yes   |
| 1.19.2    | Yes, also 1.19 and 1.19.1 | Yes |  | Yes   |
| 1.19.4    | Yes, also 1.19.3 | Yes |        | Yes   |
| 1.20.1    | Yes, also 1.20 | Yes |          | Yes   |
| 1.20.4    | Yes, also 1.20.3 | Yes | Yes    | Yes   |
| 1.20.6    | Yes, also 1.20.5 | Yes | Yes    |       |
| 1.21.1    | Yes, also 1.21 | Yes | Yes, also 1.21 |  |
| 1.21.3    | Yes, also 1.21.2 | Yes | Yes    |       |
| 1.21.4    | Yes    | Yes   | Yes      |       |
| 1.21.5    | Yes    | Yes   | Yes      |       |
| 1.21.8    | Yes, also 1.21.6 and 1.21.7 | Yes | Yes |  |
| 1.21.10   | Yes, also 1.21.9 | Yes | Yes    |       |
| 1.21.11   | Yes    | Yes   | Yes      |       |
| 26.1.2    | Yes, also 26.1 and 26.1.1 |  | Yes |   |
| 26.2      | Yes    |       | Yes      |       |
| 26.3      | Yes    |       | Yes      |       |

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

- **Sodium and Embeddium:** all features operate on 1.20.1 and on 1.21.1 and later. On the other versions Albedo has no hook for them, so some block tints can stay.
- **Graphics backend on 26.2 and later:** all features operate with the OpenGL backend and with the Vulkan backend.
- **OptiFine on 1.8.9:** all features operate. The custom sky shows when the OptiFine setting Sky is on. With an OptiFine shader pack, the sky stays vanilla.
- **OptiFine on other versions:** not supported.
- **Iris:** with shaders off, all features operate. Many shader packs draw their own sky, light and shadows. On 1.17.1 to 1.21.5 with a shader pack, the custom sky and its fog color stay vanilla.

## Known limits

- On 1.21.2 and later, you can only hide clouds, not color them.
- Hidden foliage also hides the items of those plants.

## Contributing

To build the mod or work on it, see [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## License

Apache License 2.0. See [LICENSE](LICENSE).
