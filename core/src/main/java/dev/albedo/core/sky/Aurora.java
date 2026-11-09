package dev.albedo.core.sky;

import static dev.albedo.core.sky.SkyGenerator.smooth;

/**
 * The aurora generator, which the sky shader repeats in {@code albedoAurora}: two curtains of light along an arc around
 * the layer center, each with a sharp lower edge, rays that go up and fade from COLOR_1 to COLOR_2, a faint blue-violet
 * line under the edge, and folds that move along the arc with the time.
 */
final class Aurora {
    private static final float PI = (float) Math.PI;
    /** The elevation in degrees of the lower edge of the near curtain at the layer center. */
    private static final float EDGE = 7f;
    /** How much higher in degrees the far curtain is than the near curtain. */
    private static final float FAR_EDGE = 9f;
    /** The brightness of the far curtain, which is fainter because it is farther away. */
    private static final float FAR_GAIN = 0.3f;
    /** The smallest softness in degrees of the lower edge of the near and of the far curtain, which is a haze far away. */
    private static final float NEAR_SOFT = 0.25f, FAR_SOFT = 1.8f;
    /** The coarse rays across one radian of the arc at size 100. */
    private static final float RAYS = 34f;
    /** The frequency, the size and the speed in radians for each second of the three waves that fold a curtain. */
    private static final float[] FOLD_K = {2.3f, 5.1f, 9.7f}, FOLD_AMP = {0.2f, 0.08f, 0.035f}, FOLD_RATE = {0.03f, -0.05f, 0.08f};
    /** The sum of the fold sizes, which makes the move toward the viewer go from -1 to 1. */
    private static final float FOLD_SUM = FOLD_AMP[0] + FOLD_AMP[1] + FOLD_AMP[2];
    /** The linear color of the nitrogen glow under the lower edge. */
    private static final float[] VIOLET = {0.16f, 0.05f, 0.5f};

    private Aurora() {}

    /**
     * The color and alpha of the aurora of layer {@code layer} of a program block, where the alpha times the color is the
     * light that the aurora adds.
     *
     * @param p the direction in the layer frame
     * @param footprint the width of one pixel in radians
     * @param time the seconds of the shader clock, 0 for the bake
     */
    static void sample(float[] block, int layer, float[] p, float footprint, float time, float[] out) {
        out[0] = 0;
        out[1] = 0;
        out[2] = 0;
        out[3] = 0;
        float el = (float) Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, p[1]))));
        float cosEl = (float) Math.sqrt(Math.max(0, 1 - p[1] * p[1]));
        // The pole of the layer has no azimuth, and the aurora is never below 9 degrees under the horizon.
        if (el < -9 || cosEl < 1e-4f) return;
        float a = (float) Math.atan2(p[0], -p[2]);
        float t = time * block[SkyProgram.at(layer, SkyProgram.SPEED)] * 2;
        float height = 4 + 30 * block[SkyProgram.at(layer, SkyProgram.HEIGHT)];
        float scale = block[SkyProgram.at(layer, SkyProgram.SCALE)];
        int seed = (int) block[SkyProgram.at(layer, SkyProgram.SEED)];
        // The light of COLOR_1, of COLOR_2 and of the violet glow, summed over both curtains.
        float[] light = new float[3];
        View view = new View(a, el, cosEl, footprint, t, scale, height);
        view.curtain(seed, EDGE, 1f, NEAR_SOFT, light);
        view.curtain(seed + 1, EDGE + FAR_EDGE, FAR_GAIN, FAR_SOFT, light);
        float bright = block[SkyProgram.at(layer, SkyProgram.AMOUNT)] * 2;
        float alpha1 = block[SkyProgram.at(layer, SkyProgram.ALPHA_1)], alpha2 = block[SkyProgram.at(layer, SkyProgram.ALPHA_2)];
        float[] e = new float[3];
        float m = 0;
        for (int c = 0; c < 3; c++) {
            e[c] = bright * (block[SkyProgram.at(layer, SkyProgram.COLOR_1) + c] * alpha1 * light[0]
                    + block[SkyProgram.at(layer, SkyProgram.COLOR_2) + c] * alpha2 * light[1] + VIOLET[c] * light[2]);
            m = Math.max(m, e[c]);
        }
        if (m <= 0) return;
        // A light brighter than the screen goes to white, as in a photograph.
        float hot = Math.max(0, m - 1) * 0.25f;
        float alpha = Math.min(1, m + hot);
        for (int c = 0; c < 3; c++) out[c] = (e[c] + hot) / alpha;
        out[3] = alpha;
    }

    /** The values of one direction that both curtains use. */
    private static final class View {
        final float a, el, cosEl, footprint, t, scale, height;

        View(float a, float el, float cosEl, float footprint, float t, float scale, float height) {
            this.a = a;
            this.el = el;
            this.cosEl = cosEl;
            this.footprint = footprint;
            this.t = t;
            this.scale = scale;
            this.height = height;
        }

        /** Adds the three lights of the curtain of a seed whose lower edge is {@code edge} degrees high at the center. */
        void curtain(int seed, float edge, float gain, float edgeSoft, float[] light) {
            float u = a / scale;
            float env = (float) Math.exp(-(u / 1.25f) * (u / 1.25f)) * (1 - smooth(0.8f * PI, PI, Math.abs(a)));
            if (env < 0.002f) return;
            // The base of the curtain is a wave in the ground plane: it moves along the arc (fold) and toward the viewer (near).
            float fold = 0, slope = 0, near = 0;
            for (int i = 0; i < 3; i++) {
                float arg = FOLD_K[i] * u + 2 * PI * Noise.unit(Noise.hash(i, 0, 0, seed + 601)) + FOLD_RATE[i] * t;
                fold += FOLD_AMP[i] * (float) Math.sin(arg);
                slope += FOLD_AMP[i] * FOLD_K[i] * (float) Math.cos(arg);
                near += FOLD_AMP[i] * (float) Math.cos(arg);
            }
            near /= FOLD_SUM;
            float along = u + fold + 0.004f * t;
            // More of the curtain is in each degree where it is seen edge-on, so it is brighter and its rays are closer together.
            float stretch = Math.abs(1 + slope);
            float dense = Math.max(0.25f, Math.min(2.5f, stretch));
            float bottom = edge + 4 * near + 2 * Noise.noise(along * 3.1f, t * 0.02f, 0.5f, seed + 11)
                    + 0.8f * Noise.noise(along * 7.3f, t * 0.05f, 0.5f, seed + 12) - 9 * (1 - (float) Math.cos(Math.min(Math.abs(u), 1.5f)));
            float h = el - bottom;
            if (h < -3) return;
            // The width of one pixel across the rays, so that each octave of rays fades out before it is smaller than a pixel.
            float width = footprint / Math.max(cosEl, 0.05f) / scale * stretch * RAYS;
            float r0 = octave(along * RAYS, t * 0.05f, seed + 21, width);
            float r1 = octave(along * RAYS * 2.7f, t * 0.12f, seed + 22, width * 2.7f);
            float r2 = octave(along * RAYS * 7.3f, t * 0.3f, seed + 23, width * 7.3f);
            float r3 = octave(along * RAYS * 19.7f, t * 0.6f, seed + 24, width * 19.7f);
            // Wide soft bundles of rays, and in them thin rays whose edges are harder in a sharper view, but never harder than about one pixel.
            float bundle = smooth(0.15f, 0.85f, r0);
            float hard = Math.max(0.07f, Math.min(0.3f, width * 13));
            float rays = bundle * smooth(0.5f - hard * 0.6f, 0.5f + hard, 0.5f * r1 + 0.3f * r2 + 0.2f * r3);
            float top = height * (0.4f + 0.8f * r0 + 0.5f * r1) * (1 + 0.25f * near);
            float soft = Math.max(edgeSoft, (float) Math.toDegrees(footprint));
            float below = smooth(-soft, soft * 0.6f, h);
            float up = Math.max(h, 0);
            // A bright band at the lower edge, and above it rays that fade up with dark sky between them.
            float green = below * ((float) Math.exp(-up / (0.25f * top)) * (0.08f * bundle + 1.2f * rays)
                    + 0.9f * (float) Math.exp(-up / 1.2f) * (0.35f + 0.5f * bundle + 0.5f * rays));
            float rise = up / top;
            float red = 0.22f * below * smooth(0.15f, 0.6f, rise) * (float) Math.exp(-Math.max(0, rise - 0.6f) / 0.35f) * (0.3f + 0.8f * bundle);
            float glow = 0.06f * (float) Math.exp(-((h + 0.7f) / 0.6f) * ((h + 0.7f) / 0.6f)) * (0.3f + 0.9f * bundle);
            // Some parts of the arc are bright and some are almost dark.
            float strength = 0.08f + 1.3f * smooth(0.2f, 0.85f, value(along * 2.2f, t * 0.01f, seed + 31));
            float k = gain * env * dense * strength * (1 + 0.3f * near);
            light[0] += k * green;
            light[1] += k * red;
            light[2] += k * glow;
        }
    }

    /** One octave of rays from 0 to 1, which goes to its mean 0.5 as a ray gets narrower than about three pixels. */
    private static float octave(float x, float y, int seed, float width) {
        float k = 1 - smooth(0.18f, 0.45f, width);
        return k <= 0 ? 0.5f : 0.5f + (value(x, y, seed) - 0.5f) * k;
    }

    /** Smooth value noise in two dimensions, from 0 to 1. */
    static float value(float x, float y, int seed) {
        float fx = (float) Math.floor(x), fy = (float) Math.floor(y);
        int xi = (int) fx, yi = (int) fy;
        float u = Noise.fade(x - fx), v = Noise.fade(y - fy);
        float a = Noise.unit(Noise.hash(xi, yi, 0, seed)), b = Noise.unit(Noise.hash(xi + 1, yi, 0, seed));
        float c = Noise.unit(Noise.hash(xi, yi + 1, 0, seed)), d = Noise.unit(Noise.hash(xi + 1, yi + 1, 0, seed));
        float top = a + (b - a) * u, bottom = c + (d - c) * u;
        return top + (bottom - top) * v;
    }
}
