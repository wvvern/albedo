package dev.albedo.core.config;

/**
 * Where a sky layer sits on the sky sphere: a turn about the vertical axis, a tilt, a roll and a size, the same four
 * values for every kind of layer.
 */
public final class Transform {
    /** The parts of a transform that a kind of layer uses. */
    public enum Axis { YAW, PITCH, ROLL, SCALE }

    public static final int MIN_YAW = 0, MAX_YAW = 359;
    public static final int MIN_PITCH = -90, MAX_PITCH = 90, MIN_ROLL = -180, MAX_ROLL = 180, MIN_SCALE = 25, MAX_SCALE = 400;
    public static final Transform IDENTITY = new Transform(0, 0, 0, 100);

    private final int yaw, pitch, roll, scale;

    /**
     * @param yaw degrees east of north, taken modulo 360
     * @param pitch degrees above the horizon of the layer center
     * @param roll degrees about the layer center
     * @param scale size in percent
     */
    public Transform(int yaw, int pitch, int roll, int scale) {
        this.yaw = ((yaw % 360) + 360) % 360;
        this.pitch = Range.clamp(pitch, MIN_PITCH, MAX_PITCH);
        this.roll = Range.clamp(roll, MIN_ROLL, MAX_ROLL);
        this.scale = Range.clamp(scale, MIN_SCALE, MAX_SCALE);
    }

    public int scale() { return scale; }

    public int get(Axis a) {
        switch (a) {
            case YAW: return yaw;
            case PITCH: return pitch;
            case ROLL: return roll;
            default: return scale;
        }
    }

    public Transform with(Axis a, int value) {
        switch (a) {
            case YAW: return new Transform(value, pitch, roll, scale);
            case PITCH: return new Transform(yaw, value, roll, scale);
            case ROLL: return new Transform(yaw, pitch, value, scale);
            default: return new Transform(yaw, pitch, roll, value);
        }
    }

    /** The default value of an axis. */
    public static int identity(Axis a) {
        return IDENTITY.get(a);
    }

    public static int min(Axis a) {
        switch (a) {
            case YAW: return MIN_YAW;
            case PITCH: return MIN_PITCH;
            case ROLL: return MIN_ROLL;
            default: return MIN_SCALE;
        }
    }

    public static int max(Axis a) {
        switch (a) {
            case YAW: return MAX_YAW;
            case PITCH: return MAX_PITCH;
            case ROLL: return MAX_ROLL;
            default: return MAX_SCALE;
        }
    }

    /**
     * The row-major 3x3 rotation that takes the layer's own frame to the sky, where the layer's center (local north,
     * -Z) goes to the direction at {@link #yaw} and {@link #pitch}, with world axes east +X, up +Y and north -Z.
     */
    public double[] rotation() {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch), r = Math.toRadians(roll);
        double cy = Math.cos(y), sy = Math.sin(y), cp = Math.cos(p), sp = Math.sin(p), cr = Math.cos(r), sr = Math.sin(r);
        // Yaw about +Y (north toward east), then pitch about +X (north toward up), then roll about -Z.
        double[] yawM = {cy, 0, -sy, 0, 1, 0, sy, 0, cy};
        double[] pitchM = {1, 0, 0, 0, cp, -sp, 0, sp, cp};
        double[] rollM = {cr, sr, 0, -sr, cr, 0, 0, 0, 1};
        return multiply(multiply(yawM, pitchM), rollM);
    }

    private static double[] multiply(double[] a, double[] b) {
        double[] out = new double[9];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                out[i * 3 + j] = a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j];
            }
        }
        return out;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Transform)) return false;
        Transform t = (Transform) o;
        return t.yaw == yaw && t.pitch == pitch && t.roll == roll && t.scale == scale;
    }

    @Override
    public int hashCode() { return ((yaw * 31 + pitch) * 31 + roll) * 31 + scale; }
}
