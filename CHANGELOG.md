# Changelog

## 0.4.2

- feat: Add the Chroma effect again, with colors that move smoothly in each frame on all versions
- feat: Set Chroma with the Speed, Vivid and Bright sliders in the Effects section, with Bevel and Outline on top
- config: Read the keys chroma, chromaSpeed, chromaSaturation and chromaBrightness of the config file again

## 0.4.1

- feat: Show sky stars with the colors of real stars, many faint stars and few bright stars
- feat: Give bright stars a sharp core, a soft glow and thin lines of light
- feat: Add a Spikes slider to the Stars layer to set the lines of light on bright stars
- feat: Keep the size of the stars when you change Count in a Stars layer
- feat: Change the Galaxy preset to a tilted galaxy in a large halo, with a dark dust lane across its center and two blue jets into red lobes
- feat: Add Tilt, Dust, Jets and a jet color to the Galaxy layer
- feat: Add a Meteors sky layer with shooting stars, and a Meteors preset with a meteor shower
- feat: Add an Aurora sky layer with moving curtains of northern light, and an Aurora preset
- feat: Show the aurora as folded curtains with a bright lower edge, rays and open night sky below them
- fix: Remove the dark shape of a mountain under the aurora curtains
- fix: Let a click on the Follow time label set the switch in the Motion section
- feat: Make the bright stars twinkle, more near the horizon, with a Twinkle slider on the Stars layer
- feat: Make the Nebula clouds glow brighter and darker slowly, with a Pulse slider on the Noise and Fractal layers
- feat: Turn the custom sky around a pole as the real night sky does, with the Motion section of the sky editor
- feat: Set the speed, the pole and the direction of the turning sky, and turn it with the time of the world or in real time
- feat: Show the Nebula stars sharp in all areas of the sky
- fix: Stop the cut edges on large stars in the sharp sky
- fix: Remove the blurred band of horizon color across the custom sky at the horizon
- fix: Stop the crash on 26.3 when you turn on a custom sky
- fix: Stop the crash on NeoForge 1.21.11 when the sharp sky starts
- compat: Show the custom sky with OptiFine on 1.8.9 when the OptiFine Sky setting is on
- config!: Remove the Chroma effect, and give the pastel profile a pale tint of the block colors

## 0.4.0

- feat: Draw the custom sky sharp at all zoom levels, with stars and clouds that stay smooth on all versions
- feat: Add the Sharp render switch to the sky editor, so a slow graphics card can use the baked sky
- feat: Add a Full sphere switch, so that the custom sky continues below the horizon
- feat: Move the Chroma colors over time, and set them with the Speed, Vivid and Bright sliders
- feat: Put Chroma, Bevel and Outline in an Effects section that opens and closes
- fix: Make mouse clicks work in the settings screen on 26.3
- config: Chroma from an older config file moves at speed 30, so set Speed to 0 to keep it still

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
