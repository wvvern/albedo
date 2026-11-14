package dev.albedo.core.sky;

/** The camera states in which the game hides the sky, so that the custom sky and its fog color stay away. */
public final class SkyVisibility {
    private SkyVisibility() {}

    /** True when the sky pass of the game draws no sky: in lava, in powder snow, with blindness and with darkness. */
    public static boolean skipped(CameraFluid fluid, boolean blind, boolean dark) {
        return fluid == CameraFluid.LAVA || fluid == CameraFluid.POWDER_SNOW || blind || dark;
    }

    /** True when the fog of the camera hides the sky: in water, and in each state where the sky pass draws no sky. */
    public static boolean fogHidesSky(CameraFluid fluid, boolean blind, boolean dark) {
        return fluid == CameraFluid.WATER || skipped(fluid, blind, dark);
    }
}
