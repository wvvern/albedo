package dev.albedo.core.dev;

import dev.albedo.core.Category;
import dev.albedo.core.config.Appearance;
import dev.albedo.core.config.Chroma;
import dev.albedo.core.config.Config;
import dev.albedo.core.config.Profile;
import dev.albedo.core.config.SkyDesign;
import dev.albedo.core.config.SkyLayer;
import dev.albedo.core.config.SkyTurning;
import dev.albedo.core.sky.SkyPresets;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.albedo.core.dev.HarnessConfigCommands.withEffects;
import static dev.albedo.core.dev.HarnessConfigCommands.withLook;
import static dev.albedo.core.dev.HarnessConfigCommands.withProfileJson;
import static dev.albedo.core.dev.HarnessConfigCommands.withSky;
import static dev.albedo.core.dev.HarnessConfigCommands.withSkyLayer;
import static dev.albedo.core.dev.HarnessConfigCommands.withSkyRender;
import static dev.albedo.core.dev.HarnessConfigCommands.withSkyTurning;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HarnessConfigCommandsTest {
    @Test
    void theSkyRenderCommandSetsTheRender() {
        Profile p = withSky(Config.defaults().activeProfile(), "dusk");
        assertEquals(SkyDesign.Render.FAST, withSkyRender(p, "fast").get(Category.SKY).sky().render());
        assertEquals(SkyDesign.Render.SHARP, withSkyRender(p, "Sharp").get(Category.SKY).sky().render());
    }

    @Test
    void theSkyTurnCommandSetsTheTurning() {
        Profile p = withSky(Config.defaults().activeProfile(), "galaxy");
        SkyTurning t = withSkyTurning(p, "60 height 30".split(" ")).get(Category.SKY).sky().turning();
        assertEquals(SkyTurning.STILL.withAmount(60).withPoleHeight(30), t);
        assertEquals(SkyTurning.STILL, p.get(Category.SKY).sky().turning(), "a preset with the default turning stands still");
        assertEquals(SkyPresets.TRAILS.turning(), withSky(p, "trails").get(Category.SKY).sky().turning(), "Trails keeps its own turning");
        assertEquals(SkyTurning.STILL, withSkyTurning(p, new String[]{"0"}).get(Category.SKY).sky().turning());
        assertEquals(SkyTurning.STILL.withAmount(100), withSkyTurning(p, new String[]{"100"}).get(Category.SKY).sky().turning());
        assertThrows(IllegalArgumentException.class, () -> withSkyTurning(p, "50 height".split(" ")));
        assertThrows(IllegalArgumentException.class, () -> withSkyTurning(p, "50 fast 2".split(" ")));
    }

    @Test
    void theEffectsCommandSetsTheEffectsOfACategoryAndTurnsOffTheOthers() {
        Profile p = Config.defaults().profiles().get("quake-white");
        Appearance before = p.get(Category.BLOCKS).appearance();
        p = withEffects(p, "blocks chroma 100 bevel 50".split(" "));
        assertEquals(before.withEffects(50, 0, Chroma.of(100)), p.get(Category.BLOCKS).appearance());
        p = withEffects(p, "blocks chroma 80 speed 100 saturation 70 brightness 40".split(" "));
        assertEquals(before.withEffects(0, 0, new Chroma(80, 100, 70, 40)), p.get(Category.BLOCKS).appearance());
        p = withEffects(p, "blocks outline 70".split(" "));
        assertEquals(before.withEffects(0, 70, Chroma.OFF), p.get(Category.BLOCKS).appearance());
        p = withEffects(p, "items chroma 60".split(" "));
        assertEquals(60, p.get(Category.ITEMS).appearance().chroma().strength());
        Profile defaults = Config.defaults().activeProfile();
        assertThrows(IllegalArgumentException.class, () -> withEffects(defaults, "blocks glow 5".split(" ")));
        assertThrows(IllegalArgumentException.class, () -> withEffects(defaults, "items bevel 5".split(" ")));
        assertThrows(IllegalArgumentException.class, () -> withEffects(defaults, "sky chroma 5".split(" ")));
        assertThrows(IllegalArgumentException.class, () -> withEffects(defaults, "blocks chroma 100 speed 200".split(" ")),
                "the next start would not read a speed of 200");
        assertThrows(IllegalArgumentException.class, () -> withEffects(defaults, "blocks bevel -5".split(" ")));
    }

    @Test
    void theLookCommandSetsOneColorForACategory() {
        Profile p = withLook(Config.defaults().activeProfile(), "entities solid #C83232".split(" "));
        assertEquals(Appearance.solid(0xFFC83232), p.get(Category.ENTITIES).appearance());
        assertThrows(IllegalArgumentException.class, () -> withLook(p, "entities solid red".split(" ")));
        assertThrows(IllegalArgumentException.class, () -> withLook(p, "mobs solid #C83232".split(" ")));
        assertThrows(IllegalArgumentException.class, () -> withLook(p, "entities average #C83232".split(" ")));
    }

    @Test
    void theProfileJsonCommandAddsAndSelectsAProfile() {
        Config c = withProfileJson(Config.defaults(), "{\"albedoProfile\": 1, \"name\": \"autumn\", \"profile\": "
                + "{\"blocks\": {\"groups\": [{\"name\": \"Leaves\", \"match\": \"*leaves\", \"mode\": \"recolor\", \"color\": \"#C8641E\"}]}}}");
        assertEquals("autumn", c.activeProfile().name());
        assertEquals(Appearance.Mode.RECOLOR, c.activeProfile().get(Category.BLOCKS).rules().get(0).lookIn(Appearance.ORIGINAL).mode());
        assertThrows(IllegalArgumentException.class, () -> withProfileJson(Config.defaults(), "{}"));
    }

    @Test
    void theSkyCommandSetsAPresetOrTheVanillaSky() {
        Profile p = withSky(Config.defaults().activeProfile(), "Nebula");
        assertEquals(Appearance.Mode.LAYERS, p.get(Category.SKY).appearance().mode());
        assertEquals(SkyPresets.NEBULA.withTurning(SkyTurning.STILL), p.get(Category.SKY).sky());
        assertEquals(Appearance.Mode.ORIGINAL, withSky(p, "off").get(Category.SKY).appearance().mode());
        assertThrows(IllegalArgumentException.class, () -> withSky(p, "rainbow"));
    }

    @Test
    void theSkyCommandCanKeepOnlyTheLowestLayers() {
        Profile p = withSky(Config.defaults().activeProfile(), "galaxy 4");
        assertEquals(SkyPresets.GALAXY.layers().subList(0, 4), p.get(Category.SKY).sky().layers());
        assertThrows(IllegalArgumentException.class, () -> withSky(p, "galaxy 0"));
        assertThrows(IllegalArgumentException.class, () -> withSky(p, "galaxy 9"));
    }

    @Test
    void theSkyAddCommandAddsALayerAtTheTop() {
        Profile p = withSkyLayer(withSky(Config.defaults().activeProfile(), "dusk"), "Posterize");
        List<SkyLayer> layers = p.get(Category.SKY).sky().layers();
        assertEquals(SkyPresets.DUSK.layers().size() + 1, layers.size());
        assertEquals(SkyLayer.Kind.POSTERIZE, layers.get(layers.size() - 1).kind());
        Profile full = withSky(Config.defaults().activeProfile(), "nebula");
        Profile eight = withSkyLayer(full, "vignette");
        assertThrows(IllegalArgumentException.class, () -> withSkyLayer(eight, "swirl"));
        assertThrows(IllegalArgumentException.class, () -> withSkyLayer(full, "rainbow"));
    }
}
