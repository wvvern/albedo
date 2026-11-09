package dev.albedo.core.sky;

import dev.albedo.core.config.SkyTurning;

/**
 * The rotation of a turning custom sky at one moment, from the sky directions of the design to the directions in the
 * world, which each adapter applies to the sky sphere so that the baked and the sharp sky turn the same.
 */
public final class SkyTurn {
    /** The real seconds of one Minecraft day. */
    static final double DAY_SECONDS = 1200;
    public static final SkyTurn STILL = new SkyTurn(new double[]{1, 0, 0, 0, 1, 0, 0, 0, 1});

    /** The rotation matrix row by row: world = m * design, with x to the east, y up and z to the south. */
    private final double[] m;

    private SkyTurn(double[] m) {
        this.m = m;
    }

    /**
     * The rotation of a turning in the world.
     *
     * @param dayTime the day time of the world in ticks with no wrap, where 0 is sunrise
     */
    public static SkyTurn at(SkyTurning t, double dayTime) {
        if (!t.turns()) return STILL;
        double days = t.clock() == SkyTurning.Clock.WORLD ? sunTurns(dayTime) : SkyClock.longSeconds() / DAY_SECONDS;
        return of(t, t.turnsPerDay() * days);
    }

    /** The rotation of a turning in real time, also for the world clock, so that the editor preview, which has no world, shows the move. */
    public static SkyTurn preview(SkyTurning t) {
        return t.turns() ? of(t, t.turnsPerDay() * SkyClock.longSeconds() / DAY_SECONDS) : STILL;
    }

    /**
     * The turns of the vanilla sun since midnight with no wrap, so that a turning of one turn each day goes with the sun
     * and shows the design as it is at midnight.
     */
    static double sunTurns(double dayTime) {
        return Math.floor(dayTime / 24000.0 - 0.25) + SkyLight.sunAngle(dayTime) - 0.5;
    }

    /** The rotation after some turns around the pole of a turning, in its direction. */
    static SkyTurn of(SkyTurning t, double turns) {
        double frac = turns - Math.floor(turns);
        // A negative angle around the pole moves the east side up, as the sun goes.
        double angle = -2 * Math.PI * frac * (t.direction() == SkyTurning.Direction.REVERSE ? -1 : 1);
        double az = Math.toRadians(t.pole()), el = Math.toRadians(t.poleHeight());
        double kx = Math.sin(az) * Math.cos(el), ky = Math.sin(el), kz = -Math.cos(az) * Math.cos(el);
        double c = Math.cos(angle), s = Math.sin(angle), r = 1 - c;
        return new SkyTurn(new double[]{
            c + kx * kx * r, kx * ky * r - kz * s, kx * kz * r + ky * s,
            ky * kx * r + kz * s, c + ky * ky * r, ky * kz * r - kx * s,
            kz * kx * r - ky * s, kz * ky * r + kx * s, c + kz * kz * r});
    }

    public boolean still() { return this == STILL; }

    /** The rotation as a 4x4 matrix in column order, for the model view matrix of the sky sphere. */
    public float[] matrix() {
        float[] out = new float[16];
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) out[col * 4 + row] = (float) m[row * 3 + col];
        }
        out[15] = 1;
        return out;
    }

    /** Writes into {@code out} the design direction that shows at a world direction. */
    void toDesign(double x, double y, double z, double[] out) {
        out[0] = m[0] * x + m[3] * y + m[6] * z;
        out[1] = m[1] * x + m[4] * y + m[7] * z;
        out[2] = m[2] * x + m[5] * y + m[8] * z;
    }

    /**
     * The camera yaw that looks at the same part of the design in a still sky, for the fog color near the horizon.
     *
     * @param cameraYaw the Minecraft yaw in degrees, where 0 looks south and 90 looks west
     */
    public float yaw(float cameraYaw) {
        if (still()) return cameraYaw;
        double az = Math.toRadians(cameraYaw + 180);
        double[] d = new double[3];
        toDesign(Math.sin(az), 0, -Math.cos(az), d);
        return (float) (Math.toDegrees(Math.atan2(d[0], -d[2])) - 180);
    }

    /**
     * The top rows of an image as the turned sky shows them, scaled to a new size, for the editor preview.
     *
     * @param rows the rows from the top of the image that the result shows
     */
    public int[] resample(SkyImage img, int rows, int outW, int outH) {
        int w = img.width(), h = img.height();
        int[] src = img.argb(), out = new int[outW * outH];
        double[] sinAz = new double[outW], cosAz = new double[outW];
        for (int x = 0; x < outW; x++) {
            double az = (x + 0.5) / outW * 2 * Math.PI;
            sinAz[x] = Math.sin(az);
            cosAz[x] = Math.cos(az);
        }
        double[] d = new double[3];
        for (int y = 0; y < outH; y++) {
            double el = Math.PI / 2 - (y + 0.5) / outH * rows / h * Math.PI;
            double ce = Math.cos(el), se = Math.sin(el);
            for (int x = 0; x < outW; x++) {
                toDesign(ce * sinAz[x], se, -ce * cosAz[x], d);
                double u = Math.atan2(d[0], -d[2]) / (2 * Math.PI) * w - 0.5;
                double v = (0.5 - Math.asin(Math.max(-1, Math.min(1, d[1]))) / Math.PI) * h - 0.5;
                out[y * outW + x] = bilinear(src, w, h, u, v);
            }
        }
        return out;
    }

    /** The pixel at a fractional place, mixed from the four nearest, with a wrap across the sides and a clamp at the top and bottom. */
    private static int bilinear(int[] src, int w, int h, double u, double v) {
        int x0 = (int) Math.floor(u), y0 = (int) Math.floor(v);
        double fx = u - x0, fy = v - y0;
        int xa = Math.floorMod(x0, w), xb = Math.floorMod(x0 + 1, w);
        int ya = Math.max(0, Math.min(h - 1, y0)), yb = Math.max(0, Math.min(h - 1, y0 + 1));
        int p00 = src[ya * w + xa], p10 = src[ya * w + xb], p01 = src[yb * w + xa], p11 = src[yb * w + xb];
        int out = 0xFF000000;
        for (int shift = 0; shift < 24; shift += 8) {
            double top = (p00 >> shift & 0xFF) * (1 - fx) + (p10 >> shift & 0xFF) * fx;
            double bottom = (p01 >> shift & 0xFF) * (1 - fx) + (p11 >> shift & 0xFF) * fx;
            out |= (int) Math.round(top * (1 - fy) + bottom * fy) << shift;
        }
        return out;
    }
}
