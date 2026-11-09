package dev.albedo.core.config;

import dev.albedo.core.Category;
import dev.albedo.core.Policy;
import dev.albedo.core.sky.SkyPresets;
import dev.albedo.core.texture.TextureKey;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SkyDesignTest {
    private static SkyDesign three() {
        SkyDesign d = SkyDesign.NONE.withAdded(SkyLayer.Kind.GRADIENT).withAdded(SkyLayer.Kind.NOISE).withAdded(SkyLayer.Kind.BLUR);
        return d.withLayer(2, d.layers().get(2).withMask(SkyMask.of(2, SkyMask.Mode.LIGHT)));
    }

    @Test
    void newLayersGetUniqueIds() {
        SkyDesign d = three();
        assertEquals(1, d.layers().get(0).id());
        assertEquals(3, d.layers().get(2).id());
        assertEquals(4, d.withoutLayer(0).nextId(), "the next id is one more than the largest");
        assertEquals(3, d.withoutLayer(0).withAdded(SkyLayer.Kind.STARS).layers().size());
    }

    @Test
    void deletingALayerClearsTheMasksThatUseIt() {
        SkyDesign d = three().withoutLayer(1);
        assertTrue(d.layers().get(1).mask().isNone());
        assertEquals(SkyLayer.Kind.BLUR, d.layers().get(1).kind());
    }

    @Test
    void aMoveThatPutsAMaskAboveItsLayerIsBlocked() {
        SkyDesign d = three();
        assertSame(d.layers().get(2), d.moveBlocker(2, -1), "blur is masked by the noise below it");
        assertSame(d.layers().get(2), d.moveBlocker(1, 1));
        assertNull(d.moveBlocker(0, 1));
        assertNull(d.moveBlocker(2, 1), "no layer above the top");
        assertEquals(SkyLayer.Kind.NOISE, d.withMoved(0, 1).layers().get(0).kind());
    }

    @Test
    void paramsKeepTheirRangeAndUnusedParamsAreDropped() {
        Map<SkyLayer.Param, Integer> p = new LinkedHashMap<SkyLayer.Param, Integer>();
        p.put(SkyLayer.Param.AMOUNT, 500);
        p.put(SkyLayer.Param.SEED, 3);
        SkyLayer blur = new SkyLayer(1, SkyLayer.Kind.BLUR, SkyLayer.Blend.NORMAL, 100, SkyLayer.Output.SHOW, SkyMask.NONE, p,
                Transform.IDENTITY);
        assertEquals(100, blur.param(SkyLayer.Param.AMOUNT));
        assertEquals(Collections.singleton(SkyLayer.Param.AMOUNT), blur.params().keySet());
    }

    @Test
    void sizeIsOnlyATransformAxis() {
        for (SkyLayer.Kind k : SkyLayer.Kind.values()) {
            for (SkyLayer.Param p : k.params()) assertTrue(p != null && k.uses(p));
        }
        assertTrue(SkyLayer.Kind.STARS.axes().contains(Transform.Axis.SCALE));
        assertTrue(SkyLayer.Kind.BLUR.axes().isEmpty());
    }

    @Test
    void skyEditsDoNotRepaintTexturesButTheSunSwitchDoes() {
        Profile base = Config.defaults().activeProfile();
        CategorySettings sky = new CategorySettings(Appearance.of(Appearance.Mode.LAYERS), false, false,
                Collections.<Rule>emptyList(), SkyPresets.DUSK);
        Policy a = Policy.of(base.with(Category.SKY, sky));
        Policy b = Policy.of(base.with(Category.SKY, sky.withSky(SkyPresets.DUSK.withLayer(0,
                SkyPresets.DUSK.layers().get(0).withOpacity(40)))));
        Policy c = Policy.of(base.with(Category.SKY, sky.withSky(SkyPresets.DUSK.withCelestial(SkyDesign.Celestial.HIDE))));
        Policy d = Policy.of(base.with(Category.SKY, sky.withSky(SkyPresets.DUSK.withExtent(SkyDesign.Extent.SPHERE))));
        assertTrue(a.sameTextures(b));
        assertTrue(a.sameTextures(d), "the extent changes no texture");
        assertFalse(a.sameTextures(c));
        TextureKey sun = new TextureKey(Category.SKY, "minecraft:textures/environment/sun.png");
        TextureKey end = new TextureKey(Category.SKY, "minecraft:textures/environment/end_sky.png");
        assertEquals(Appearance.Mode.ORIGINAL, a.appearanceFor(sun).mode());
        assertEquals(Appearance.Mode.HIDDEN, c.appearanceFor(sun).mode());
        assertEquals(Appearance.Mode.ORIGINAL, c.appearanceFor(end).mode());
        assertEquals(Policy.SkyStyle.LAYERS, a.skyStyle());
        assertNull(a.skyRgb(), "the flat sky color is only for Color");
        assertFalse(Appearance.of(Appearance.Mode.LAYERS).changesPixels());
    }

    @Test
    void theBakeReadsTheTrailsButNotTheSpeedOrTheClock() {
        SkyDesign d = SkyDesign.NONE.withAdded(SkyLayer.Kind.STARS);
        SkyTurning turning = new SkyTurning(60, 3, SkyTurning.Clock.REAL, 30, SkyTurning.Direction.NORMAL, 10);
        assertEquals(d.baked(), d.withTurning(turning).baked(), "a turning with no trails does not bake again");
        SkyDesign trails = d.withTurning(turning.withTrails(40));
        assertEquals(trails.baked(), trails.withTurning(trails.turning().withSpeed(7).withAmount(100).withClock(SkyTurning.Clock.WORLD))
                .baked(), "the speed, the amount and the clock do not change the trails");
        assertNotEquals(trails.baked(), trails.withTurning(trails.turning().withPole(200)).baked(), "the pole moves the trails");
        assertNotEquals(trails.baked(), trails.withTurning(trails.turning().withTrailLight(90)).baked());
        assertEquals(d.baked(), trails.withTurning(trails.turning().withAmount(0)).baked(), "a still sky has no trails");
    }

    @Test
    void yawWrapsAndTheRotationPointsTheCenter() {
        assertEquals(new Transform(0, 0, 0, 100), new Transform(360, 0, 0, 100));
        double[] r = new Transform(90, 30, 0, 100).rotation();
        // The local north (0, 0, -1) goes to the third column, negated.
        double x = -r[2], y = -r[5], z = -r[8];
        assertEquals(Math.cos(Math.toRadians(30)), x, 1e-9, "east");
        assertEquals(Math.sin(Math.toRadians(30)), y, 1e-9, "up");
        assertEquals(0, z, 1e-9);
        assertArrayEquals(new double[]{1, 0, 0, 0, 1, 0, 0, 0, 1}, Transform.IDENTITY.rotation(), 1e-12);
    }
}
