package dev.albedo.core.sky;

/**
 * Color conversions between sRGB bytes, linear light and the OKLab space, the Java twin of oklab.glsl, with the double kernel
 * that the texture colors also use.
 */
public final class Oklab {
    private static final float[] TO_LINEAR = new float[256];

    static {
        for (int i = 0; i < 256; i++) {
            double c = i / 255.0;
            TO_LINEAR[i] = (float) (c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4));
        }
    }

    private Oklab() {}

    /** One sRGB channel byte in linear light. */
    public static float toLinear(int channel) {
        return TO_LINEAR[channel & 0xFF];
    }

    /** One linear channel as an sRGB value from 0 to 255, not rounded. */
    public static float toSrgb(float linear) {
        float c = Math.max(0f, Math.min(1f, linear));
        double s = c <= 0.0031308 ? c * 12.92 : 1.055 * Math.pow(c, 1 / 2.4) - 0.055;
        return (float) (s * 255);
    }

    /** The linear {r, g, b} of an ARGB color. */
    public static float[] linear(int argb) {
        return new float[]{toLinear(argb >> 16), toLinear(argb >> 8), toLinear(argb)};
    }

    /** Linear RGB to OKLab {L, a, b}, written into {@code out}. */
    public static void fromLinear(float r, float g, float b, float[] out) {
        double[] lab = new double[3];
        lab(r, g, b, lab);
        for (int c = 0; c < 3; c++) out[c] = (float) lab[c];
    }

    /** OKLab to linear RGB {r, g, b}, written into {@code out}. */
    public static void toLinear(float lightness, float a, float b, float[] out) {
        double[] rgb = new double[3];
        linear(lightness, a, b, rgb);
        for (int c = 0; c < 3; c++) out[c] = (float) rgb[c];
    }

    /** Linear RGB to OKLab {L, a, b} in double precision, written into {@code out}, the same as {@code albedoLinearToLab}. */
    public static void lab(double r, double g, double b, double[] out) {
        double l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
        double m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
        double s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
        out[0] = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s;
        out[1] = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s;
        out[2] = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s;
    }

    /** OKLab to linear RGB {r, g, b} in double precision, written into {@code out}, the same as {@code albedoLabToLinear}. */
    public static void linear(double lightness, double a, double b, double[] out) {
        double l = lightness + 0.3963377774 * a + 0.2158037573 * b;
        double m = lightness - 0.1055613458 * a - 0.0638541728 * b;
        double s = lightness - 0.0894841775 * a - 1.2914855480 * b;
        l = l * l * l;
        m = m * m * m;
        s = s * s * s;
        out[0] = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s;
        out[1] = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s;
        out[2] = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s;
    }

    /** The OKLab lightness of a linear color, from 0 to about 1. */
    public static float lightness(float r, float g, float b) {
        double l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
        double m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
        double s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
        return (float) (0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s);
    }
}
