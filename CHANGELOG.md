# Changelog

## 0.7.2

- feat: Add Player armor on the World page, which hides the armor that players wear
- fix: Pick each server's profile or Albedo off from one list, so a server set to off can get a profile again
- fix: Show the Chroma Speed slider only where the colors can move, not for mobs and players
- fix: Keep the Chroma colors of armor trims on mobs still, like the rest of the mob

## 0.7.1

- fix: Fill the whole sky while a custom sky turns, in place of a tilted edge with a flat blurry color past it
- fix: Fill the sky below the horizon in the Dusk and Aurora skies
- fix: Keep the Dusk and Aurora skies still, as their cards show, also after a sky that turns such as Trails

## 0.7.0

- feat: Pick a built-in sky from a gallery with an image of each sky, in place of the preset buttons
- feat: Add the Deep field sky layer, with far galaxies and a cloud of stars
- feat: Add Detail to the Galaxy layer, for threads of dust, glowing gas, star clusters and finer jets
- feat: Add Animation to the Galaxy layer, which moves its jets, makes its core flicker and lets stars explode
- feat: Give the Galaxy sky far galaxies, a star cloud, wisps of gas, more detail and slow motion
- feat: Make the aurora larger, with tall soft curtains, lasting rays and a second fainter arc
- fix: Remove the hard bright line at the lower edge of the aurora
- fix: Close the settings with Esc after a change of the sky style on a layer page

## 0.6.2

- fix: Show the profile color of grass, leaves and vines with Sodium, Embeddium or Rubidium on 1.16.5 to 1.19.4
- fix: Show the profile color of water with Sodium or Rubidium on 1.20.1 to 1.20.6
- fix: Show the profile color of grass and leaves with Sodium on Fabric 1.20.6
- compat: Support Quilt on 1.16.5 to 1.21.11 with the Fabric jar

## 0.6.1

- fix: Stop the crash on 26.2 with the Vulkan graphics backend when Chroma colors move
- fix: Draw the sharp sky on 26.2 with the Vulkan graphics backend

## 0.6.0

- fix: Show the flat sky color on NeoForge 1.21.11 in the pastel and quake-white profiles
- fix: Show the water surface on 26.3 in the pastel profile
- compat: Add Fabric jars for Minecraft 1.16.5, 1.17.1, 1.18.2, 1.19.2 and 1.19.4
- compat: Add Fabric jars for Minecraft 1.20.1, 1.20.4, 1.20.6, 1.21.3 and 1.21.4
- compat: Add Fabric jars for Minecraft 1.21.5, 1.21.8, 1.21.10, 26.1 and 26.2
- compat: Add Forge jars for Minecraft 1.16.5, 1.17.1, 1.18.2, 1.19.2, 1.19.4, 1.20.1 and 1.20.4
- compat: Add NeoForge jars for Minecraft 1.20.4, 1.20.6, 1.21.3, 1.21.4 and 1.21.5
- compat: Add NeoForge jars for Minecraft 1.21.8, 1.21.10, 26.1 and 26.2
- compat: Add a Forge jar for Minecraft 1.12.2
- compat: Run the Fabric jars on the minor versions next to them, for example 1.19.1, 1.20 and 26.1.1
- compat: Let the Fabric jars run on Fabric Loader 0.15.0 and newer
- compat: Keep the vanilla sky and fog color on 1.21.1 when an Iris shader pack runs

## 0.5.1

- feat: Make the highest Chroma speed 5 times faster
- feat: Make the Chroma colors of the pastel profile move faster
- fix: Remove the checker of black pixels from blocks with moving Chroma colors on 1.21.11 and NeoForge 26.3
- fix: Make typing work in the text fields of the settings screen on 26.3
- fix: Let you type characters that need AltGr in the settings screen on 1.8.9
- fix: Replace all of the text when you type after Ctrl+A in the settings screen
- fix: Keep the vanilla fog color under water, in lava and in powder snow with a custom or flat sky
- fix: Hide the custom sky on 1.8.9 with blindness, under water and in lava, as the game hides its sky
- fix: Hide or show the clouds at once on 1.21.11 and 26.3 when you change the profile

## 0.5.0

- feat: Add the Chroma effect again, with colors that move smoothly in each frame on all versions
- feat: Set the Speed, Vivid and Bright of Chroma in the Effects section
- feat: Give the pastel profile soft Chroma colors that move slowly
- feat: Turn the custom sky slowly by default at 1x in real time, also in the Trails preset
- fix: Make the pulse of the Nebula clouds stronger and faster, so that you can see it
- fix: Give each aurora curtain one smooth lower edge and long tapered ends
- fix: Stop the jump of the aurora once each hour
- fix: Keep the meteors in flight at the start of each hour
- fix: Keep the meteors and the aurora above the horizon when the custom sky turns
- fix: Show the star trails on the layer pages of the sky editor
- fix: Name the Aurora colors Low and High, as in the Gradient layer
- fix: Say that the game draws its own sky on 26.3 when the sky texture shader fails
- fix: Stop the crash on 1.8.9 with a custom sky when the video setting Use VBOs is on
- perf: Draw the custom sky 2 to 4 times faster, and only where blocks do not cover it
- perf: Draw the star trails faster on a Stars layer with a small Size
- compat: Show still Chroma colors on 1.8.9 when OptiFine uses anisotropic filtering or antialiasing

## 0.4.1

- feat: Show sky stars with the colors of real stars, many faint stars and few bright stars
- feat: Give bright stars a sharp core, a soft glow and thin lines of light
- feat: Add a Spikes slider to the Stars layer to set the lines of light on bright stars
- feat: Keep the size of the stars when you change Count in a Stars layer
- feat: Change the Galaxy preset to a tilted galaxy with a dark dust lane and two blue jets into red lobes
- feat: Add Tilt, Dust, Jets and a jet color to the Galaxy layer
- feat: Add a Meteors sky layer with shooting stars, and a Meteors preset with a meteor shower
- feat: Add an Aurora sky layer with moving, folded curtains of northern light, and an Aurora preset
- feat: Make the bright stars twinkle, more near the horizon, with a Twinkle slider on the Stars layer
- feat: Make the Nebula clouds glow brighter and darker slowly, with a Pulse slider on the Noise and Fractal layers
- feat: Turn the custom sky around a pole like the real night sky, in the new Motion section
- feat: Set the speed, pole and direction of the turning sky, and follow the world time or real time
- feat: Add star trails that follow the turning sky, and a Trails preset with them
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
