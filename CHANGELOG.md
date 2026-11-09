# Changelog

## 0.3.0

- feat: Add the sky style Layers to build your own sky from gradients, glows, noise, fractals, galaxies and stars
- feat: Stack sky filters such as blur, crystallize and bloom, and show a layer only where another layer is light or dark
- feat: Turn, tilt, roll and scale each sky layer, and see the result in a preview with the horizon and the compass
- feat: Add the sky presets Dusk, Nebula and Galaxy
- feat: Add the effects Chroma, Bevel and Outline, which stack on top of a style
- feat: Add the built-in profile pastel, a soft world where each kind of block has its own color
- feat: Repaint downloaded player skins, capes and elytra on all versions
- fix: Keep the default player skin repainting on 1.21.1 after a downloaded skin loads
- fix: Show the new rows at once when you change a style, so the settings screen does not jump
- fix: Keep the Style row in the same place for every category
- fix: Show the name of each slider when a small window puts the sliders on one row
- compat: Show the vanilla sky for the Layers style with OptiFine on 1.8.9 and with shader packs
- config!: Remove the Image style and its textures folder, so use a resource pack for your own textures
- config: Write the sky layers and the effects to the file only where you use them, so that older versions read other files

## 0.2.0

- feat: Add a Servers page that gives each multiplayer server its own profile or turns the mod off there
- feat: Give back your own profile when you leave a server
- feat: Keep a change with F6 or F7 on a server only until you leave it
- feat: Show the reason in the message when Apply reloads all resources
- feat: Add a Recolor style that changes the color of a texture and keeps its detail
- feat: Add Detail, Colors and Light sliders to tune a recolor
- feat: Make particle groups work on 1.8.9 with the particle names of newer versions
- feat: Give each painting and explosion frame on 1.8.9 its own name for groups
- fix: Keep a copy of a config file with an error, so that a save cannot delete your profiles
- fix: Keep the open group when your change to a built-in profile makes a new copy
- fix: Show the new look on a texture that loads during a settings change
- fix: Keep a group's own setting when you drag or type a new value for its category
- compat: Use the Particles look for the explosion and the Entities look for the fishing hook on 1.8.9

## 0.1.0

First release.

- feat: Change blocks, fluids, block entities, decorations, items, mobs, players, particles and mining cracks to one flat color, their average color, gray, your own PNG, or hide them
- feat: Change the sky and fog to one color, and hide clouds and weather
- feat: Add the built-in profiles quake-white, quake-black and minimal
- feat: Put your changes to a built-in profile in a new copy
- feat: Add groups that give a set of textures their own look, with name patterns and regular expressions
- feat: Add a settings screen (F9) with a searchable profile list, a color picker and texture search
- feat: Add fullbright, shading, foliage and entity shadow options
- feat: Export a profile to a file and import a profile from the clipboard
- feat: Add keys to turn the mod on or off (F6) and to select the next profile (F7)
- perf: Show a settings change in less than 0.1 seconds, without a resource reload
- compat: Support Minecraft 1.8.9 (Forge), 1.21.1, 1.21.11 and 26.3 (Fabric and NeoForge)
- compat: Support Sodium, Embeddium and OptiFine
