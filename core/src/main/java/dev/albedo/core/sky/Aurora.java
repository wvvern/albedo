package dev.albedo.core.sky;

import static dev.albedo.core.sky.SkyGenerator.smooth;

/**
 * The aurora generator, which the sky shader repeats in {@code albedoAurora}: a near and a far ribbon of light high above
 * the ground toward the layer center, each a thin vertical sheet on a folded curve whose lower edge is sharp and brightest,
 * with rays of varied heights that fade up from COLOR_1 to COLOR_2, where each crossing of the view with a sheet adds light.
 */
final class Aurora {
    /** The radius of the earth in km, which makes a far ribbon sink to the horizon. */
    private static final float EARTH = 6371f;
    /** The altitude in km of the lower edge of the ribbons. */
    private static final float BASE = 100f;
    /** The distance in km of the near and of the far ribbon at the layer center. */
    private static final float[] DISTANCE = {280f, 620f};
    /** The light of the near and of the far ribbon. */
    private static final float[] GAIN = {1f, 0.15f};
    /** The frequency for each radian of azimuth, the size in radians and the speed in radians for each second of the folds. */
    private static final float[] FOLD_K = {9f, 31f, 70f}, FOLD_AMP = {0.09f, 0.03f, 0.012f}, FOLD_RATE = {0.02f, -0.05f, 0.11f};
    /** The frequency for each radian, the size in km and the speed of the waves that move the ribbon toward the viewer. */
    private static final float[] NEAR_K = {5f, 17f}, NEAR_AMP = {30f, 10f}, NEAR_RATE = {-0.015f, 0.04f};
    /** How far toward the viewer a fold moves the ribbon, for each km that it moves the ribbon along its line. */
    private static final float LOOP = 0.9f;
    /** The steps of the search for the crossings, over the azimuths that the folds can reach. */
    static final int STEPS = 14;
    /** The most crossings of the view with one ribbon that add light, which the shader keeps in one vec4. */
    static final int CROSSINGS = 4;
    /** The largest azimuth in radians from the layer center that the search starts from, where the ribbons are below the horizon. */
    private static final float MAX_AZIMUTH = 1.35f;
    /** How many km each second the rays move along the ribbon. */
    private static final float DRIFT = 0.6f;

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
        float el = (float) Math.asin(Math.max(-1, Math.min(1, p[1])));
        float cosEl = (float) Math.sqrt(Math.max(0, 1 - p[1] * p[1]));
        // The ribbons are never below 3 degrees under the horizon, and the pole of the layer has no azimuth.
        if (el < -0.05f || el > 1.4f || cosEl < 1e-4f) return;
        float a = (float) Math.atan2(p[0], -p[2]);
        if (Math.abs(a) > MAX_AZIMUTH) return;
        Ribbon view = new Ribbon(block, layer, a, el, footprint / Math.max(cosEl, 0.05f), footprint, time);
        float[] light = new float[2];
        for (int c = 0; c < DISTANCE.length; c++) view.add(c, light);
        // The thick air near the horizon dims the light, so that no bright edge lies along the horizon.
        float bright = block[SkyProgram.at(layer, SkyProgram.AMOUNT)] * 2 * smooth(-0.02f, 0.22f, el);
        float alpha1 = block[SkyProgram.at(layer, SkyProgram.ALPHA_1)], alpha2 = block[SkyProgram.at(layer, SkyProgram.ALPHA_2)];
        float[] e = new float[3];
        float m = 0;
        for (int c = 0; c < 3; c++) {
            e[c] = bright * (block[SkyProgram.at(layer, SkyProgram.COLOR_1) + c] * alpha1 * light[0]
                    + block[SkyProgram.at(layer, SkyProgram.COLOR_2) + c] * alpha2 * light[1]);
            m = Math.max(m, e[c]);
        }
        if (m <= 0) return;
        // A light brighter than the screen goes to white, as in a photograph.
        float hot = Math.max(0, m - 1) * 0.25f;
        float alpha = Math.min(1, m + hot);
        for (int c = 0; c < 3; c++) out[c] = (e[c] + hot) / alpha;
        out[3] = alpha;
    }

    /** The values of one view direction that both ribbons use. */
    private static final class Ribbon {
        final float a, el, wide, footprint, t, scale, height;
        final int seed;

        Ribbon(float[] block, int layer, float a, float el, float wide, float footprint, float time) {
            this.a = a;
            this.el = el;
            this.wide = wide;
            this.footprint = footprint;
            t = time * block[SkyProgram.at(layer, SkyProgram.SPEED)] * 2;
            scale = block[SkyProgram.at(layer, SkyProgram.SCALE)];
            height = 40 + 220 * block[SkyProgram.at(layer, SkyProgram.HEIGHT)];
            seed = (int) block[SkyProgram.at(layer, SkyProgram.SEED)];
        }

        /**
         * The azimuth of the ribbon {@code c} at {@code theta} minus the azimuth of the view, where {@code d} gets the slope of
         * the azimuth, and the move of the folds toward the viewer in km and its slope.
         */
        float offset(int c, float theta, float[] d) {
            float f = far(c);
            float phi = theta;
            d[0] = 1;
            d[1] = 0;
            d[2] = 0;
            for (int i = 0; i < FOLD_K.length; i++) {
                float k = FOLD_K[i] * f / scale, amp = FOLD_AMP[i] * scale / f;
                float arg = k * theta + 6.2831853f * Noise.unit(Noise.hash(i, c, 0, seed + 601)) + FOLD_RATE[i] * t;
                float sin = (float) Math.sin(arg), cos = (float) Math.cos(arg);
                phi += amp * sin;
                d[0] += amp * k * cos;
                // Each fold also moves the ribbon toward the viewer, so that a fold is a loop and its lower edge an S curve.
                float loop = LOOP * DISTANCE[c] * amp;
                d[1] -= loop * cos;
                d[2] += loop * k * sin;
            }
            return phi - a;
        }

        /** Adds the light of the near ({@code c} 0) or the far ribbon at each place where the view crosses it. */
        void add(int c, float[] light) {
            float reach = (FOLD_AMP[0] + FOLD_AMP[1] + FOLD_AMP[2]) * scale / far(c) * 1.02f;
            float step = 2 * reach / STEPS;
            float[] d = new float[3];
            float ta = a - reach, ga = offset(c, ta, d), da = d[0];
            int found = 0;
            for (int i = 1; i <= STEPS; i++) {
                float tb = a - reach + i * step, gb = offset(c, tb, d), db = d[0];
                if ((ga <= 0) != (gb <= 0)) {
                    if (found++ < CROSSINGS) sheet(c, ta + step * ga / (ga - gb), 1, light);
                } else if ((da < 0) != (db < 0)) {
                    // The tip of a fold is in this step: the view crosses the sheet twice when the tip is past it.
                    float tm = ta + step * da / (da - db), gm = offset(c, tm, d);
                    if ((gm <= 0) != (ga <= 0)) {
                        // The two crossings fade out over two pixels at the tip, where they meet and go.
                        float k = smooth(0, 2 * wide, Math.abs(gm));
                        if (found++ < CROSSINGS) sheet(c, ta + (tm - ta) * ga / (ga - gm), k, light);
                        if (found++ < CROSSINGS) sheet(c, tm + (tb - tm) * gm / (gm - gb), k, light);
                    }
                }
                ta = tb;
                ga = gb;
                da = db;
            }
        }

        /** Adds the light of the sheet of ribbon {@code c} where the view crosses it at {@code theta}, times {@code k}. */
        void sheet(int c, float theta, float k, float[] light) {
            float[] d = new float[3];
            offset(c, theta, d);
            float dPhi = d[0], th = Math.max(-1.5f, Math.min(1.5f, theta));
            float cos = (float) Math.cos(th), tan = (float) Math.tan(th);
            // The distance of a straight east-west line, moved toward and away from the viewer by the folds and by slow waves.
            float r = DISTANCE[c] / cos + d[1], dr = DISTANCE[c] * tan / cos + d[2];
            for (int i = 0; i < NEAR_K.length; i++) {
                float kk = NEAR_K[i] * far(c) / scale;
                float arg = kk * theta + 6.2831853f * Noise.unit(Noise.hash(i, c, 1, seed + 602)) + NEAR_RATE[i] * t;
                r += NEAR_AMP[i] * (float) Math.sin(arg);
                dr += NEAR_AMP[i] * kk * (float) Math.cos(arg);
            }
            // The sine of the angle between the view and the sheet: a sheet seen edge-on is brighter, as the view goes through more of it.
            float across = Math.abs(r * dPhi);
            float obl = across / (float) Math.sqrt(dr * dr + across * across);
            // The altitude of the view above the ground at the distance of the sheet, with the curve of the earth.
            float tilt = Math.min(el + r / (2 * EARTH), 1.45f);
            float alt = r * (float) Math.tan(tilt);
            float cosT = (float) Math.cos(tilt);
            // The km of one pixel up the sheet and along it.
            float pixelUp = footprint * r / (cosT * cosT), pixelAlong = wide * r / Math.max(obl, 0.08f) / scale;
            float s = DISTANCE[c] * tan / scale, sd = s + DRIFT * t;
            float h = alt - BASE - 4 * Noise.noise(sd / 70, t * 0.02f, 0.5f, seed + 11 + c);
            // The lower edge of the far ribbon is soft, as the haze of the far air hides its detail.
            float soft = Math.max(1.2f * far(c) * far(c), 0.6f * pixelUp);
            if (h < -soft) return;
            float r0 = octave(sd / 45, t * 0.03f, seed + 21 + c, pixelAlong / 45);
            float r1 = octave(sd / 14, t * 0.1f, seed + 22 + c, pixelAlong / 14);
            float r2 = octave(sd / 5, t * 0.25f, seed + 23 + c, pixelAlong / 5);
            float r3 = octave(sd / 1.8f, t * 0.5f, seed + 24 + c, pixelAlong / 1.8f);
            float tall = octave(sd / 14 + 31.7f, t * 0.08f, seed + 25 + c, pixelAlong / 14);
            // Wide bundles of rays, and in them thin rays whose edges are harder in a sharper view, but never harder than about one pixel.
            float bundle = smooth(0.3f, 0.85f, r0);
            float hard = Math.max(0.07f, Math.min(0.3f, pixelAlong / 1.8f * 0.5f));
            float fine = smooth(0.5f - 0.6f * hard, 0.5f + hard, 0.5f * r1 + 0.3f * r2 + 0.2f * r3);
            // The tall rays are also the bright rays.
            float rays = bundle * fine * (0.3f + 0.7f * tall);
            float top = height * (0.2f + 1.4f * tall * tall);
            float below = smooth(-soft, soft, h), up = Math.max(h, 0);
            // A thin bright band along the lower edge, and above it rays of many heights with dark sky between them.
            float green = below * ((float) Math.exp(-up / 9) * (0.2f + 1.2f * (0.4f * bundle + 0.6f * fine))
                    + 1.1f * rays * (float) Math.exp(-up / (0.4f * top)));
            float red = below * 0.2f * bundle * (0.3f + 0.7f * fine) * smooth(0.3f * top, 0.9f * top, up)
                    * (float) Math.exp(-Math.max(0, up - 0.9f * top) / (0.5f * top));
            // Parts of the ribbon are bright, faint or gaps of open sky, with wider gaps in the far ribbon and none at the center.
            float lit = value(s / 320, t * 0.004f, seed + 31 + c) + (c == 0 ? 0.35f * (float) Math.exp(-th * th / 0.25f) : 0);
            float strength = smooth(0.3f + 0.15f * c, 0.6f + 0.15f * c, lit) * (0.4f + 0.9f * value(s / 70, t * 0.01f, seed + 33 + c));
            float gain = k * GAIN[c] * strength * 0.8f / (float) Math.sqrt(obl * obl + 0.0144f);
            light[0] += gain * green;
            light[1] += gain * red;
        }

        /** How many times farther ribbon {@code c} is than the near ribbon, which makes its folds smaller in the sky. */
        private static float far(int c) { return DISTANCE[c] / DISTANCE[0]; }
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
