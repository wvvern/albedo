package dev.albedo.core.dev;

import dev.albedo.core.Category;
import dev.albedo.core.config.Appearance;
import dev.albedo.core.config.CategorySettings;
import dev.albedo.core.config.Config;
import dev.albedo.core.config.Profile;
import dev.albedo.core.config.Rule;
import dev.albedo.core.config.SkyDesign;
import dev.albedo.core.config.SkyLayer;
import dev.albedo.core.config.SkyTurning;
import dev.albedo.core.sky.SkyPresets;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HarnessTest {
    @Test
    void theSkyCompareMeasuresTheOklabDistance() {
        int[] gray = {0xFF808080, 0xFF808080, 0xFF808080, 0xFF808080};
        assertEquals(0, Harness.difference(gray, gray.clone())[0], 1e-9);
        int[] one = gray.clone();
        one[3] = 0xFFFFFFFF;
        double[] d = Harness.difference(gray, one);
        assertTrue(d[2] > 0.3 && d[2] < 0.5, "white is far from gray: " + d[2]);
        assertEquals(d[2] / 4, d[0], 1e-9, "the mean of one far pixel in four");
        assertThrows(IllegalArgumentException.class, () -> Harness.difference(gray, new int[2]));
    }

    @Test
    void theSkyRenderCommandSetsTheRender() {
        Profile p = Harness.withSky(Config.defaults().activeProfile(), "dusk");
        assertEquals(SkyDesign.Render.FAST, Harness.withSkyRender(p, "fast").get(Category.SKY).sky().render());
        assertEquals(SkyDesign.Render.SHARP, Harness.withSkyRender(p, "Sharp").get(Category.SKY).sky().render());
    }

    @Test
    void theSkyExtentCommandSetsTheExtent() {
        Profile p = Harness.withSky(Config.defaults().activeProfile(), "dusk");
        assertEquals(SkyDesign.Extent.SPHERE, Harness.withSkyExtent(p, "sphere").get(Category.SKY).sky().extent());
        assertEquals(SkyDesign.Extent.HORIZON, Harness.withSkyExtent(p, "Horizon").get(Category.SKY).sky().extent());
    }

    @Test
    void theSkyTurnCommandSetsTheTurning() {
        Profile p = Harness.withSky(Config.defaults().activeProfile(), "galaxy");
        SkyTurning t = Harness.withSkyTurning(p, "60 speed 4 height 30 pole 90 reverse real".split(" ")).get(Category.SKY).sky().turning();
        assertEquals(new SkyTurning(60, 4, SkyTurning.Clock.REAL, 30, SkyTurning.Direction.REVERSE, 90), t);
        assertEquals(SkyTurning.NONE.withAmount(100), Harness.withSkyTurning(p, new String[]{"100"}).get(Category.SKY).sky().turning());
        assertEquals(SkyTurning.NONE.withAmount(100).withTrails(45).withTrailLight(80),
                Harness.withSkyTurning(p, "100 trails 45 light 80".split(" ")).get(Category.SKY).sky().turning());
        assertThrows(IllegalArgumentException.class, () -> Harness.withSkyTurning(p, "50 speed".split(" ")));
        assertThrows(IllegalArgumentException.class, () -> Harness.withSkyTurning(p, "50 fast 2".split(" ")));
    }

    @Test
    void theGroupCommandSetsOrReplacesAGroupByName() {
        Profile p = Config.defaults().activeProfile();
        p = Harness.withGroup(p, "particles Fire #FF0000 flame lava".split(" "));
        p = Harness.withGroup(p, "particles Love hidden heart".split(" "));
        p = Harness.withGroup(p, "particles Love red heart".split(" "));
        CategorySettings love = p.get(Category.PARTICLES);
        assertEquals(Appearance.solid(0xFFFF0000), love.lookOf(love.rules().get(1)), "a color name");
        p = Harness.withGroup(p, "particles Love hidden heart".split(" "));
        p = Harness.withGroup(p, "particles Fire #00FF00 flame".split(" "));
        CategorySettings particles = p.get(Category.PARTICLES);
        List<Rule> rules = particles.rules();
        assertEquals(2, rules.size());
        assertEquals("Fire", rules.get(0).name());
        assertEquals(Arrays.asList("flame"), rules.get(0).patterns());
        assertEquals(Appearance.solid(0xFF00FF00), particles.lookOf(rules.get(0)));
        assertEquals(Appearance.Mode.HIDDEN, particles.lookOf(rules.get(1)).mode());
    }

    @Test
    void theEffectsCommandSetsTheEffectsOfACategoryAndTurnsOffTheOthers() {
        Profile p = Config.defaults().profiles().get("quake-white");
        Appearance before = p.get(Category.BLOCKS).appearance();
        p = Harness.withEffects(p, "blocks bevel 50".split(" "));
        assertEquals(before.withEffects(50, 0), p.get(Category.BLOCKS).appearance());
        p = Harness.withEffects(p, "blocks outline 70".split(" "));
        assertEquals(before.withEffects(0, 70), p.get(Category.BLOCKS).appearance());
        assertThrows(IllegalArgumentException.class, () -> Harness.withEffects(Config.defaults().activeProfile(), "blocks chroma 5".split(" ")));
        assertThrows(IllegalArgumentException.class, () -> Harness.withEffects(Config.defaults().activeProfile(), "items bevel 5".split(" ")));
    }

    @Test
    void theSkyCommandSetsAPresetOrTheVanillaSky() {
        Profile p = Harness.withSky(Config.defaults().activeProfile(), "Nebula");
        assertEquals(Appearance.Mode.LAYERS, p.get(Category.SKY).appearance().mode());
        assertEquals(SkyPresets.NEBULA, p.get(Category.SKY).sky());
        assertEquals(Appearance.Mode.ORIGINAL, Harness.withSky(p, "off").get(Category.SKY).appearance().mode());
        assertThrows(IllegalArgumentException.class, () -> Harness.withSky(p, "rainbow"));
    }

    @Test
    void theSkyCommandCanKeepOnlyTheLowestLayers() {
        Profile p = Harness.withSky(Config.defaults().activeProfile(), "galaxy 4");
        assertEquals(SkyPresets.GALAXY.layers().subList(0, 4), p.get(Category.SKY).sky().layers());
        assertThrows(IllegalArgumentException.class, () -> Harness.withSky(p, "galaxy 0"));
        assertThrows(IllegalArgumentException.class, () -> Harness.withSky(p, "galaxy 9"));
    }

    @Test
    void theSkyAddCommandAddsALayerAtTheTop() {
        Profile p = Harness.withSkyLayer(Harness.withSky(Config.defaults().activeProfile(), "dusk"), "Posterize");
        List<SkyLayer> layers = p.get(Category.SKY).sky().layers();
        assertEquals(SkyPresets.DUSK.layers().size() + 1, layers.size());
        assertEquals(SkyLayer.Kind.POSTERIZE, layers.get(layers.size() - 1).kind());
        Profile full = Harness.withSky(Config.defaults().activeProfile(), "nebula");
        Profile eight = Harness.withSkyLayer(full, "vignette");
        assertThrows(IllegalArgumentException.class, () -> Harness.withSkyLayer(eight, "swirl"));
        assertThrows(IllegalArgumentException.class, () -> Harness.withSkyLayer(full, "rainbow"));
    }

    @Test
    void theSkyParamCommandSetsOneSettingOfALayer() {
        Profile p = Harness.withSkyParam(Harness.withSky(Config.defaults().activeProfile(), "nebula"), "2 pulse 100".split(" "));
        List<SkyLayer> layers = p.get(Category.SKY).sky().layers();
        assertEquals(100, layers.get(1).param(SkyLayer.Param.PULSE));
        assertEquals(SkyPresets.NEBULA.layers().get(1).withParam(SkyLayer.Param.PULSE, 100), layers.get(1));
        assertEquals(SkyPresets.NEBULA.layers().get(2), layers.get(2));
    }

    @Test
    void aSkinHashIsHexAndDiffersForACape() {
        byte[] png = {1, 2, 3};
        String skin = Harness.skinHash(png, false);
        assertEquals(64, skin.length());
        assertTrue(skin.matches("[0-9a-f]+"));
        assertEquals(skin, Harness.skinHash(png, false));
        assertNotEquals(skin, Harness.skinHash(png, true));
    }
}
