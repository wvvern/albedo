package dev.albedo.core.sky;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkyVisibilityTest {
    @Test
    void lavaPowderSnowBlindnessAndDarknessSkipTheSkyPass() {
        assertTrue(SkyVisibility.skipped(CameraFluid.LAVA, false, false));
        assertTrue(SkyVisibility.skipped(CameraFluid.POWDER_SNOW, false, false));
        assertTrue(SkyVisibility.skipped(CameraFluid.NONE, true, false));
        assertTrue(SkyVisibility.skipped(CameraFluid.NONE, false, true));
    }

    @Test
    void waterAndClearAirKeepTheSkyPass() {
        assertFalse(SkyVisibility.skipped(CameraFluid.WATER, false, false), "the game draws the sky pass in water");
        assertFalse(SkyVisibility.skipped(CameraFluid.NONE, false, false));
    }

    @Test
    void theFogHidesTheSkyInWaterAndWhereTheSkyPassIsSkipped() {
        assertTrue(SkyVisibility.fogHidesSky(CameraFluid.WATER, false, false));
        for (CameraFluid fluid : CameraFluid.values()) {
            for (int states = 0; states < 4; states++) {
                boolean blind = (states & 1) != 0;
                boolean dark = (states & 2) != 0;
                if (SkyVisibility.skipped(fluid, blind, dark)) assertTrue(SkyVisibility.fogHidesSky(fluid, blind, dark));
            }
        }
        assertFalse(SkyVisibility.fogHidesSky(CameraFluid.NONE, false, false));
    }
}
