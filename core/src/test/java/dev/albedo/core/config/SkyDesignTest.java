package dev.albedo.core.config;

import org.junit.jupiter.api.Test;

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
        assertEquals(0, blur.param(SkyLayer.Param.SEED), "a blur has no seed");
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
    void theBakeReadsTheTrailsButNotTheSpeedOrTheClock() {
        SkyDesign d = SkyDesign.NONE.withAdded(SkyLayer.Kind.STARS);
        SkyTurning turning = new SkyTurning(60, 3, SkyTurning.Clock.REAL, 30, SkyTurning.Direction.NORMAL, 10);
        assertEquals(d.baked(), d.withTurning(turning).baked(), "a turning with no trails does not bake again");
        SkyDesign trails = d.withTurning(turning.withTrails(40));
        assertEquals(trails.baked(), trails.withTurning(trails.turning().withSpeed(7).withAmount(100).withClock(SkyTurning.Clock.WORLD))
                .baked(), "the speed, the amount and the clock do not change the trails");
        assertNotEquals(trails.baked(), trails.withTurning(trails.turning().withPole(200)).baked(), "the pole moves the trails");
        assertNotEquals(trails.baked(), trails.withTurning(trails.turning().withTrailLight(90)).baked());
        assertEquals(d.withTurning(SkyTurning.STILL).baked(), trails.withTurning(trails.turning().withAmount(0)).baked(),
                "a still sky has no trails");
    }

    @Test
    void aSkyThatTurnsIsDrawnAndBakedAroundTheFullSphere() {
        SkyDesign still = SkyDesign.NONE.withAdded(SkyLayer.Kind.STARS).withTurning(SkyTurning.STILL);
        SkyDesign turning = still.withTurning(SkyTurning.DEFAULT);
        assertEquals(SkyDesign.Extent.HORIZON, still.drawnExtent());
        assertEquals(SkyDesign.Extent.SPHERE, turning.drawnExtent(), "the turn would bring the part below the horizon up");
        assertEquals(SkyDesign.Extent.HORIZON, turning.extent(), "the setting stays for a sky that stops turning");
        assertEquals(still.withExtent(SkyDesign.Extent.SPHERE).baked(), turning.baked(), "the bake fills the full sphere");
    }
}
