package dev.albedo.core.sky;

import static dev.albedo.core.sky.SkyGenerator.smooth;

/**
 * The aurora generator, which the sky shader repeats in {@code albedoAurora}: a near and a far curtain of light high above
 * the ground toward the layer center, each with one smooth lower edge across the sky that is brightest, and above it the
 * rays of a thin sheet on a folded curve, of varied heights, that fade up from COLOR_1 to COLOR_2.
 */
final class Aurora {
    /** The radius of the earth in km, which makes a far ribbon sink to the horizon. */
    private static final float EARTH = 6371f;
    /** The altitude in km of the lower edge of the ribbons. */
    private static final float BASE = 100f;
    /** The distance in km of the nearest place of the near and of the far ribbon. */
    private static final float[] DISTANCE = {280f, 620f};
    /** The azimuth in radians of the nearest place of the near and of the far ribbon, so that each ribbon slants across the sky. */
    private static final float[] SKEW = {0.45f, -0.35f};
    /** The elevation in radians above which the far ribbon has no light. */
    private static final float FAR_TOP = 0.3f;
    /** The light of the near and of the far ribbon. */
    private static final float[] GAIN = {1f, 0.2f};
    /** The km to each side of a sheet that its light reaches, which makes a sheet seen edge-on brighter but not without end. */
    private static final float THICK = 14f;
    /**
     * The seconds of the aurora clock after which it goes back to 0, in which each wave turns and each ray and noise row
     * moves on a whole number of times, so that the wrap has no jump.
     */
    static final float PERIOD = 7200f;
    /** The angle in radians for each second of a wave that turns once in a {@link #PERIOD}. */
    private static final float TURN = (float) (2 * Math.PI / PERIOD);
    /** The frequency for each radian of azimuth, the size in radians and the speed in radians for each second of the folds. */
    private static final float[] FOLD_K = {8f, 21f, 43f}, FOLD_AMP = {0.08f, 0.04f, 0.019f}, FOLD_RATE = {23 * TURN, -57 * TURN, 126 * TURN};
    /** The frequency for each radian, the size in km and the speed of the waves that move the ribbon toward the viewer. */
    private static final float[] NEAR_K = {5f, 17f}, NEAR_AMP = {30f, 6f}, NEAR_RATE = {-17 * TURN, 46 * TURN};
    /** How far toward the viewer a fold moves the lower edge, for each km that it moves the ribbon along its line. */
    private static final float LOOP = 0.3f;
    /** The steps of the search for the parts of a ribbon near the view, over the azimuths that the folds can reach. */
    static final int STEPS = 10;
    /** The largest azimuth in radians from the layer center with aurora light, where the ribbons are low and faint. */
    private static final float MAX_AZIMUTH = 1.35f;
    /** How many km each second the rays move along the ribbon, which is 4320 km in a {@link #PERIOD}. */
    private static final float DRIFT = 0.6f;
    /** The waves of the lower edge of about 23 and 9 km in radians for each km, which the drift of a period turns a whole number of times. */
    private static final float EDGE_K1 = 30 * TURN / DRIFT, EDGE_K2 = 76 * TURN / DRIFT;
    /** The width in km of the cells of each octave of rays, so that the drift of a period is a whole number of cells of each. */
    private static final float[] RAY_CELL = {40f, 3f, 1.08f, 0.5f, 2f};
    /** The cells of each octave of rays in the drift of a period, and the noise rows that each octave moves through in a period. */
    private static final int[] RAY_CELLS = {108, 1440, 4000, 8640, 2160}, RAY_ROWS = {216, 720, 1800, 4320, 864};
    /** The noise rows in a period of the bright and faint parts of a ribbon and of their brightness. */
    private static final int PART_ROWS = 29, LIGHT_ROWS = 72;
    /** One {@link Ribbon} for each thread, so that a pixel of the bake makes no new arrays. */
    private static final ThreadLocal<Ribbon> VIEW = ThreadLocal.withInitial(Ribbon::new);
    /** The part of its full height that a curtain keeps where it ends. */
    private static final float SHORT = 0.15f;
    /** The km over which the light of the near curtain comes in at its lower edge. */
    private static final float SOFT = 1f;
    /** The km under the lower edge over which the faint glow of a curtain fades out. */
    private static final float SKIRT = 4f;
    /** The azimuth in radians of one cell of the noise of the gaps, so that a curtain ends over many degrees of the sky. */
    private static final float GAP = 0.6f;
    /** The most light of a piece of a sheet for the light of a sheet across the view, which a fold seen edge-on comes near. */
    private static final float EDGE_ON = 2.5f;

    private Aurora() {}

    /** The aurora clock at a time of the sky clock with no wrap, for an aurora at a speed from 0 to 1. */
    static float clock(double seconds, float speed) {
        double t = seconds * speed * 2;
        return (float) (t - PERIOD * Math.floor(t / PERIOD));
    }

    /**
     * The color and alpha of the aurora of layer {@code layer} of a program block at the aurora clock in the block, where
     * the alpha times the color is the light that the aurora adds.
     *
     * @param p the direction in the layer frame
     * @param footprint the width of one pixel in radians
     */
    static void sample(float[] block, int layer, float[] p, float footprint, float[] out) {
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
        Ribbon view = VIEW.get();
        view.set(block, layer, a, el, footprint / Math.max(cosEl, 0.05f), footprint);
        float[] light = view.light, e = view.color;
        light[0] = 0;
        light[1] = 0;
        view.add(0, light);
        // The far ribbon with its short rays is never more than about 15 degrees high.
        if (el < FAR_TOP) view.add(1, light);
        // The thick air near the horizon dims the light, so that no bright edge lies along it, and the light fades out before the search stops.
        float bright = block[SkyProgram.at(layer, SkyProgram.AMOUNT)] * 2 * smooth(-0.02f, 0.22f, el)
                * (1 - smooth(MAX_AZIMUTH - 0.2f, MAX_AZIMUTH, Math.abs(a)));
        float alpha1 = block[SkyProgram.at(layer, SkyProgram.ALPHA_1)], alpha2 = block[SkyProgram.at(layer, SkyProgram.ALPHA_2)];
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

    /** The values of one view direction that both ribbons use, which {@link #set} changes for each pixel. */
    private static final class Ribbon {
        float a, el, wide, footprint, t, scale, height;
        int seed;
        /** The light of COLOR_1 and COLOR_2 of the view, and its color. */
        final float[] light = new float[2], color = new float[3];
        /** The room for the slopes and the loop of {@link #offset}, which each caller reads before the next call. */
        private final float[] d = new float[3];
        /** The phases of the waves of the ribbon of the last {@link #add}. */
        private final float[] phases = new float[7];
        /** The km of one pixel up the curtain of the last {@link #rise}. */
        private float pixelUp;
        /** The km of the view above the lower edge, the km that the tallest rays reach, and true where the view is not under the edge. */
        private float up, tallest;
        private boolean above;
        /** The light of the pieces of the ribbon near the view: all of it, of the band at the lower edge, of the rays and of their red tops. */
        private float lit, band, beam, crown;
        /** How much of the near curtain is there at the view. */
        private float front;
        /** The light and the place where it is seen of the last {@link #aim}. */
        private float aimed, seen;

        /** Sets the view direction and the numbers of layer {@code layer} of a program block. */
        void set(float[] block, int layer, float a, float el, float wide, float footprint) {
            this.a = a;
            this.el = el;
            this.wide = wide;
            this.footprint = footprint;
            t = block[SkyProgram.at(layer, SkyProgram.CLOCK)];
            scale = block[SkyProgram.at(layer, SkyProgram.SCALE)];
            height = 40 + 220 * block[SkyProgram.at(layer, SkyProgram.HEIGHT)];
            seed = (int) block[SkyProgram.at(layer, SkyProgram.SEED)];
        }

        /**
         * The azimuth of the ribbon {@code c} at {@code theta} minus the azimuth of the view, where {@code d} gets the slope of
         * the azimuth, and the move of the folds toward the viewer in km and its slope.
         */
        float offset(int c, float theta) {
            float f = far(c);
            float phi = theta;
            d[0] = 1;
            d[1] = 0;
            d[2] = 0;
            for (int i = 0; i < FOLD_K.length; i++) {
                float k = FOLD_K[i] * f / scale, amp = FOLD_AMP[i] * scale / f;
                float arg = k * theta + phases[i] + FOLD_RATE[i] * t;
                float sin = (float) Math.sin(arg), cos = (float) Math.cos(arg);
                phi += amp * sin;
                d[0] += amp * k * cos;
                // Each fold also moves the ribbon toward the viewer, so that the lower edge goes up and down with the folds.
                float loop = LOOP * DISTANCE[c] * amp;
                d[1] -= loop * cos;
                d[2] += loop * k * sin;
            }
            return phi - a;
        }

        /**
         * Adds the light of the near ({@code c} 0) or the far curtain: one lower edge, one height and one brightness for the
         * azimuth of the view, so that they are smooth across the sky, times the rays of the straight pieces of the folded
         * ribbon between the steps near the view.
         */
        void add(int c, float[] light) {
            // The phases of the three fold waves, of the two waves toward the viewer and of the two waves of the lower edge.
            for (int i = 0; i < 3; i++) phases[i] = 6.2831853f * Noise.unit(Noise.hash(i, c, 0, seed + 601));
            for (int i = 0; i < 2; i++) {
                phases[3 + i] = 6.2831853f * Noise.unit(Noise.hash(i, c, 1, seed + 602));
                phases[5 + i] = 6.2831853f * Noise.unit(Noise.hash(i, c, 2, seed + 603));
            }
            float th = angle(c, a), along = DISTANCE[c] * (float) Math.tan(th) / scale, present = presence(c, th);
            if (c == 0) front = present;
            // The far curtain shows only in the gaps of the near curtain, so that the two lower edges never look like one edge with a step.
            else present *= 1 - smooth(0, 0.5f, front);
            if (present <= 0) return;
            float h = rise(c, th, along);
            // A curtain gets shorter as it gets fainter toward its end, so that it does not end at a wall.
            tallest = height * (SHORT + (1 - SHORT) * present) / (far(c) * far(c));
            if (h < -6 * SKIRT || h > 2.2f * tallest) return;
            float soft = Math.max(SOFT * far(c), 0.7f * pixelUp), below = smooth(-2 * soft, soft, h);
            up = Math.max(h, 0);
            above = below > 0;
            lit = 0;
            band = 0;
            beam = 0;
            crown = 0;
            float w = Math.max(3 * wide, THICK / DISTANCE[c]);
            // The search reaches past the light of each sheet, so that a fold comes into the search with no light to change.
            float reach = (FOLD_AMP[0] + FOLD_AMP[1] + FOLD_AMP[2]) * scale / far(c) * 1.02f + w;
            float step = 2 * reach / STEPS;
            float ta = a - reach, ga = offset(c, ta), da = d[0], sa = speed(c, ta);
            for (int i = 1; i <= STEPS; i++) {
                float tb = a - reach + i * step, gb = offset(c, tb), db = d[0], sb = speed(c, tb);
                if ((da < 0) != (db < 0)) {
                    // The tip of a fold is in this step, which gets a corner there, found in two steps toward the zero of the slope.
                    float tm = ta + step * da / (da - db);
                    offset(c, tm);
                    float dm = d[0];
                    tm = (da < 0) != (dm < 0) ? ta + (tm - ta) * da / (da - dm) : tm + (tb - tm) * dm / (dm - db);
                    float gm = offset(c, tm), sm = speed(c, tm);
                    piece(c, ta, ga, sa, tm, gm, sm, w);
                    piece(c, tm, gm, sm, tb, gb, sb, w);
                } else {
                    piece(c, ta, ga, sa, tb, gb, sb, w);
                }
                ta = tb;
                ga = gb;
                da = db;
                sa = sb;
            }
            // A faint glow around the lower edge that also reaches under it, so that the sky under a curtain does not look like a dark hill.
            float glow = 0.1f * lit * (float) Math.exp(-Math.abs(h) / SKIRT), gain = GAIN[c] * present;
            // The brightest light is a thin band at the lower edge, and above it the rays dissolve into the open sky.
            light[0] += gain * (glow + below * (band * (float) Math.exp(-up / 3.5f) + beam));
            light[1] += gain * below * crown;
        }

        /** Adds the light of the straight piece of ribbon {@code c} from {@code ta} to {@code tb} with its rays. */
        private void piece(int c, float ta, float ga, float sa, float tb, float gb, float sb, float w) {
            aim(ta, ga, sa, tb, gb, sb, w);
            if (aimed > 0) rays(c, seen, aimed, (gb - ga) / (tb - ta));
        }

        /**
         * How much of a curtain is there at the azimuth {@code th} of {@link #angle} of the view: parts of a curtain are
         * there, fade out or are gaps of open sky, with wider gaps in the far curtain and none at the center.
         */
        float presence(int c, float th) {
            float part = value(th / GAP, t * (PART_ROWS / PERIOD), 0, PART_ROWS, seed + 31 + c)
                    + (c == 0 ? 0.35f * (float) Math.exp(-th * th / 0.25f) : 0);
            return smooth(0.25f + 0.15f * c, 0.7f + 0.15f * c, part);
        }

        /**
         * The length of the ribbon {@code c} at {@code theta} for each radian of {@code theta}, over its distance, from the
         * values of the last {@link #offset} there.
         */
        float speed(int c, float theta) {
            float th = angle(c, theta);
            float cos = (float) Math.cos(th);
            float out = (DISTANCE[c] * (float) Math.tan(th) / cos + d[2]) / (DISTANCE[c] / cos + d[1]);
            return (float) Math.sqrt(d[0] * d[0] + out * out);
        }

        /**
         * Sets {@link #aimed} to the light of the straight piece from {@code ta} to {@code tb}, with the azimuths {@code ga}
         * and {@code gb} from the view and the speeds {@code sa} and {@code sb}, for a sheet whose light reaches {@code w}
         * radians to each side, and {@link #seen} to the place where the piece is seen.
         */
        void aim(float ta, float ga, float sa, float tb, float gb, float sb, float w) {
            float xa = ga / w, xb = gb / w, dx = xb - xa;
            aimed = 0;
            if (Math.min(Math.abs(xa), Math.abs(xb)) >= 1 && (xa < 0) == (xb < 0)) return;
            // The light along the view is the length of the piece near it, so a piece seen edge-on is brighter.
            float part = cumulative(xb) - cumulative(xa), mid = 0.5f;
            float k = (Math.abs(dx) < 1e-3f ? Math.max(0, 1 - Math.abs(xa)) : part / dx) * Math.abs(tb - ta) / w * 0.5f * (sa + sb);
            if (k <= 0) return;
            // The middle of the light of the piece, which moves smoothly with the view.
            if (Math.abs(dx) >= 1e-3f && part != 0) mid = Math.max(0, Math.min(1, (moment(xb) - moment(xa) - xa * part) / (dx * part)));
            // The piece is seen where its line meets the view, so that the pieces around one crossing show one sharp sheet,
            // and a piece that runs along the view is seen where its light is.
            float xm = xa + dx * mid;
            // A piece that does not cross the view gives light only from the width of its sheet, which fades out faster as
            // it gets farther, so that the side of a part that runs along the view does not look like a straight cut.
            k *= 1 - smooth(0, 1, Math.max(0, Math.max(Math.min(xa, xb), -Math.max(xa, xb))));
            // A piece seen edge-on is brighter, but not as a pillar of light at the tip of each fold.
            aimed = k / (1 + k / EDGE_ON);
            seen = ta + (tb - ta) * (mid - xm * dx / (dx * dx + 0.25f));
        }

        /**
         * Adds the light of a piece of ribbon {@code c} seen at {@code theta} with the light {@code k}, whose azimuth changes
         * by {@code slope} for each radian of {@code theta}, where each piece has its own brightness and rays.
         */
        void rays(int c, float theta, float k, float slope) {
            float th = angle(c, theta), along = DISTANCE[c] * (float) Math.tan(th) / scale, cos = (float) Math.cos(th);
            float bright = k * (0.4f + 0.9f * value(along / 70, t * (LIGHT_ROWS / PERIOD), 0, LIGHT_ROWS, seed + 33 + c));
            lit += bright;
            if (!above) return;
            // The km of the rays in one pixel, which is more where the view runs along the sheet, as near the tip of a fold.
            float pixelAlong = wide * DISTANCE[c] / (cos * cos * Math.max(Math.abs(slope), 0.02f)) / scale;
            float sd = along + DRIFT * t;
            float r0 = ray(0, sd, 0, seed + 21 + c, pixelAlong), r1 = ray(1, sd, 0, seed + 22 + c, pixelAlong);
            float r2 = ray(2, sd, 0, seed + 23 + c, pixelAlong), r3 = ray(3, sd, 0, seed + 24 + c, pixelAlong);
            float tall = ray(4, sd, 31.7f, seed + 25 + c, pixelAlong);
            // Wide bundles of thin rays with dark sky between them, whose edges are never harder than about one pixel.
            float bundle = 0.35f + 0.65f * smooth(0.2f, 0.8f, r0);
            float hard = Math.max(0.05f, Math.min(0.3f, pixelAlong / 0.5f * 0.5f));
            float rays = bundle * smooth(0.55f - hard, 0.55f + hard, 0.3f * r1 + 0.38f * r2 + 0.32f * r3);
            // Each ray fades up over its own height, and the far ribbon is shorter, so that it is a low arc.
            float rise = up / (tallest * (0.07f + 0.45f * tall * tall));
            band += bright * (0.15f + 0.4f * bundle + 0.8f * rays);
            beam += bright * 1.1f * rays * (float) Math.exp(-rise * (0.5f + 0.5f * rise));
            crown += bright * 0.25f * rays * tall * (float) Math.exp(-(rise - 1.3f) * (rise - 1.3f) * 2);
        }

        /**
         * The km of the view above the lower edge of curtain {@code c}, with the azimuth {@code th} of {@link #angle} of the
         * view and the place {@code along} there, which also sets {@link #pixelUp}.
         */
        float rise(int c, float th, float along) {
            offset(c, a);
            // The distance of a straight east-west line, moved toward and away from the viewer by the folds and by slow waves.
            float r = DISTANCE[c] / (float) Math.cos(th) + d[1];
            for (int i = 0; i < NEAR_K.length; i++) {
                float kk = NEAR_K[i] * far(c) / scale;
                r += NEAR_AMP[i] * (float) Math.sin(kk * a + phases[3 + i] + NEAR_RATE[i] * t);
            }
            // The altitude of the view above the ground at the distance of the curtain, with the curve of the earth.
            float tilt = Math.min(el + r / (2 * EARTH), 1.45f), cosT = (float) Math.cos(tilt);
            pixelUp = footprint * r / (cosT * cosT);
            float sd = along + DRIFT * t;
            // The lower edge is nearly level, with gentle waves that move along it.
            float edge = 1.3f * (float) Math.sin(sd * EDGE_K1 + phases[5] + 23 * TURN * t)
                    + 0.8f * (float) Math.sin(sd * EDGE_K2 + phases[6] - 57 * TURN * t);
            return r * (float) Math.tan(tilt) - BASE - edge;
        }

        /**
         * Octave {@code i} of the rays at the place {@code sd} along the ribbon with the drift, moved by {@code shift} cells,
         * which repeats after the drift and the rows of one period, for {@code pixelAlong} km of the rays in one pixel.
         */
        private float ray(int i, float sd, float shift, int raySeed, float pixelAlong) {
            return octave(sd / RAY_CELL[i] + shift, t * (RAY_ROWS[i] / PERIOD), RAY_CELLS[i], RAY_ROWS[i], raySeed, pixelAlong / RAY_CELL[i]);
        }

        /** The azimuth of {@code theta} from the nearest place of ribbon {@code c}, so that each ribbon slants across the sky. */
        private static float angle(int c, float theta) { return Math.max(-1.5f, Math.min(1.5f, theta - SKEW[c])); }

        /** How many times farther ribbon {@code c} is than the near ribbon, which makes its folds smaller in the sky. */
        private static float far(int c) { return DISTANCE[c] / DISTANCE[0]; }
    }

    /** The part of a triangle of width 2 around 0 that lies left of {@code x}. */
    private static float cumulative(float x) {
        float y = Math.max(-1, Math.min(1, x));
        return y < 0 ? 0.5f * (1 + y) * (1 + y) : 1 - 0.5f * (1 - y) * (1 - y);
    }

    /** The first moment of the part of a triangle of width 2 around 0 that lies left of {@code x}. */
    private static float moment(float x) {
        float y = Math.max(-1, Math.min(1, x));
        return y < 0 ? y * y * (0.5f + y / 3) - 1 / 6f : y * y * (0.5f - y / 3) - 1 / 6f;
    }

    /** One octave of rays from 0 to 1, which goes to its mean 0.5 as a ray gets narrower than about three pixels. */
    private static float octave(float x, float y, int periodX, int periodY, int seed, float width) {
        float k = 1 - smooth(0.18f, 0.45f, width);
        return k <= 0 ? 0.5f : 0.5f + (value(x, y, periodX, periodY, seed) - 0.5f) * k;
    }

    /** Smooth value noise in two dimensions from 0 to 1, which repeats after {@code periodX} and {@code periodY} cells, or never on an axis of 0. */
    static float value(float x, float y, int periodX, int periodY, int seed) {
        float fx = (float) Math.floor(x), fy = (float) Math.floor(y);
        int x0 = repeat((int) fx, periodX), x1 = repeat((int) fx + 1, periodX), y0 = repeat((int) fy, periodY), y1 = repeat((int) fy + 1, periodY);
        float u = Noise.fade(x - fx), v = Noise.fade(y - fy);
        float a = Noise.unit(Noise.hash(x0, y0, 0, seed)), b = Noise.unit(Noise.hash(x1, y0, 0, seed));
        float c = Noise.unit(Noise.hash(x0, y1, 0, seed)), d = Noise.unit(Noise.hash(x1, y1, 0, seed));
        float top = a + (b - a) * u, bottom = c + (d - c) * u;
        return top + (bottom - top) * v;
    }

    /** The cell {@code i} of a row that repeats after {@code n} cells, or {@code i} for an {@code n} of 0. */
    private static int repeat(int i, int n) {
        return n > 0 ? Math.floorMod(i, n) : i;
    }
}
