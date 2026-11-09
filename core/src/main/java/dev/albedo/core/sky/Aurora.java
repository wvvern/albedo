package dev.albedo.core.sky;

import static dev.albedo.core.sky.SkyGenerator.smooth;

/**
 * The aurora generator, which the sky shader repeats in {@code albedoAurora}: a near and a far ribbon of light high above
 * the ground toward the layer center, each a thin vertical sheet on a folded curve whose lower edge is sharp and brightest,
 * with rays of varied heights that fade up from COLOR_1 to COLOR_2, where each part of a sheet near the view adds light.
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
    /** The frequency for each radian of azimuth, the size in radians and the speed in radians for each second of the folds. */
    private static final float[] FOLD_K = {8f, 21f, 43f}, FOLD_AMP = {0.08f, 0.04f, 0.019f}, FOLD_RATE = {0.02f, -0.05f, 0.11f};
    /** The frequency for each radian, the size in km and the speed of the waves that move the ribbon toward the viewer. */
    private static final float[] NEAR_K = {5f, 17f}, NEAR_AMP = {30f, 10f}, NEAR_RATE = {-0.015f, 0.04f};
    /** How far toward the viewer a fold moves the ribbon, for each km that it moves the ribbon along its line. */
    private static final float LOOP = 0.5f;
    /** The steps of the search for the parts of a ribbon near the view, over the azimuths that the folds can reach. */
    static final int STEPS = 10;
    /** The largest azimuth in radians from the layer center with aurora light, where the ribbons are low and faint. */
    private static final float MAX_AZIMUTH = 1.35f;
    /** How many km each second the rays move along the ribbon. */
    private static final float DRIFT = 0.6f;
    /** The azimuth in radians of the near ribbon before the tip of a fold over which a curtain fades out at its end. */
    private static final float TAPER = 0.06f;
    /** The km under the lower edge over which the faint glow of a curtain fades out. */
    private static final float SKIRT = 4f;

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
        view.add(0, light);
        // The far ribbon with its short rays is never more than about 15 degrees high.
        if (el < FAR_TOP) view.add(1, light);
        // The thick air near the horizon dims the light, so that no bright edge lies along it, and the light fades out before the search stops.
        float bright = block[SkyProgram.at(layer, SkyProgram.AMOUNT)] * 2 * smooth(-0.02f, 0.22f, el)
                * (1 - smooth(MAX_AZIMUTH - 0.2f, MAX_AZIMUTH, Math.abs(a)));
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
        /** The room for the slopes and the loop of {@link #offset}, which each caller reads before the next call. */
        private final float[] d = new float[3];
        /** The phases of the waves of the ribbon of the last {@link #add}. */
        private final float[] phases = new float[7];
        /** The values of the last {@link #rise}: the km of one pixel up the sheet and the km of the rays in one pixel. */
        private float pixelUp, pixelAlong;
        /**
         * The start of the search, the tips of the folds in their order along the ribbon and the end of the search: their
         * places, their azimuths from the view, and how much the curtain ends at each tip.
         */
        private final float[] tipThetas = new float[STEPS + 2], tipOffsets = new float[STEPS + 2], folds = new float[STEPS + 2];
        /** The light of each part of the ribbon between two tips: of its band at the lower edge, of its rays and of their red tops. */
        private final float[] bands = new float[STEPS + 1], beams = new float[STEPS + 1], crowns = new float[STEPS + 1];
        /** The light and the place where it is seen of the last {@link #aim}. */
        private float aimed, seen;

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
                // Each fold also moves the ribbon toward the viewer, so that a fold is a loop and its lower edge an S curve.
                float loop = LOOP * DISTANCE[c] * amp;
                d[1] -= loop * cos;
                d[2] += loop * k * sin;
            }
            return phi - a;
        }

        /** Adds the light of the near ({@code c} 0) or the far ribbon, as straight pieces between the steps near the view. */
        void add(int c, float[] light) {
            // The phases of the three fold waves, of the two waves toward the viewer and of the two waves of the lower edge.
            for (int i = 0; i < 3; i++) phases[i] = 6.2831853f * Noise.unit(Noise.hash(i, c, 0, seed + 601));
            for (int i = 0; i < 2; i++) {
                phases[3 + i] = 6.2831853f * Noise.unit(Noise.hash(i, c, 1, seed + 602));
                phases[5 + i] = 6.2831853f * Noise.unit(Noise.hash(i, c, 2, seed + 603));
            }
            float w = Math.max(3 * wide, THICK / DISTANCE[c]), taper = TAPER / far(c);
            // The search also reaches past the fade of each tip, so that a tip comes into the search with no light to change.
            float reach = (FOLD_AMP[0] + FOLD_AMP[1] + FOLD_AMP[2]) * scale / far(c) * 1.02f + Math.max(w, taper);
            float step = 2 * reach / STEPS;
            float ta = a - reach, ga = offset(c, ta), da = d[0], sa = speed(c, ta);
            tipThetas[0] = ta;
            tipOffsets[0] = ga;
            int tips = 1;
            clear(0);
            for (int i = 1; i <= STEPS; i++) {
                float tb = a - reach + i * step, gb = offset(c, tb), db = d[0], sb = speed(c, tb);
                if ((da < 0) != (db < 0)) {
                    // The tip of a fold is in this step, which gets a corner there, found in two steps toward the zero of the slope.
                    float tm = ta + step * da / (da - db);
                    offset(c, tm);
                    float dm = d[0];
                    tm = (da < 0) != (dm < 0) ? ta + (tm - ta) * da / (da - dm) : tm + (tb - tm) * dm / (dm - db);
                    float gm = offset(c, tm), sm = speed(c, tm);
                    piece(c, tips - 1, ta, ga, sa, tm, gm, sm, w);
                    tipThetas[tips] = tm;
                    tipOffsets[tips] = gm;
                    clear(tips++);
                    piece(c, tips - 1, tm, gm, sm, tb, gb, sb, w);
                } else {
                    piece(c, tips - 1, ta, ga, sa, tb, gb, sb, w);
                }
                ta = tb;
                ga = gb;
                da = db;
                sa = sb;
            }
            tipThetas[tips] = ta;
            tipOffsets[tips] = ga;
            folds(tips, taper);
            // Toward the end of a curtain the light gets fainter, and the rays more than the band, so that they look shorter.
            for (int b = 0; b < tips; b++) {
                if (bands[b] + beams[b] <= 0) continue;
                float end = end(b, tips, taper), gain = GAIN[c] * end;
                light[0] += gain * (bands[b] + end * beams[b]);
                light[1] += gain * end * crowns[b];
            }
        }

        /** Sets the light of the part {@code b} of the ribbon to 0. */
        private void clear(int b) {
            bands[b] = 0;
            beams[b] = 0;
            crowns[b] = 0;
        }

        /** Adds the light of the straight piece of ribbon {@code c} from {@code ta} to {@code tb} to part {@code b} of the ribbon. */
        private void piece(int c, int b, float ta, float ga, float sa, float tb, float gb, float sb, float w) {
            aim(ta, ga, sa, tb, gb, sb, w);
            if (aimed > 0) sheet(c, b, seen, aimed);
        }

        /**
         * Sets {@link #folds} for the tips from 1 to {@code last} - 1: how much the curtain ends at each tip, from how far the
         * ribbon turns back there before it comes past the tip again, so that a small kink of the ribbon stays bright and a
         * small kink near a large fold does not change it.
         */
        void folds(int last, float taper) {
            for (int i = 1; i < last; i++) {
                // The ribbon comes to a tip from a smaller azimuth when the tip is a largest azimuth.
                float turn = tipOffsets[i] > tipOffsets[i - 1] ? 1 : -1, peak = turn * tipOffsets[i], pass = -1e30f;
                for (int dir = -1; dir <= 1; dir += 2) {
                    float low = peak;
                    for (int k = i + dir; ; k += dir) {
                        float v = turn * tipOffsets[k];
                        low = Math.min(low, v);
                        if (v > peak || k == 0 || k == last) break;
                    }
                    pass = Math.max(pass, low);
                }
                folds[i] = smooth(0, taper, peak - pass);
            }
        }

        /**
         * How much light the part {@code b} of the ribbon between tips {@code b} and {@code b} + 1 keeps near the ends of its
         * curtain, which ends at the first large fold to each side: the light fades as the view comes near the tip and is
         * gone past it.
         */
        float end(int b, int last, float taper) {
            float keep = 1;
            for (int dir = -1; dir <= 1; dir += 2) {
                float pass = 1;
                for (int i = dir > 0 ? b + 1 : b; i > 0 && i < last; i += dir) {
                    // The azimuth from the view to the tip, which is less than 0 when the view is past the tip.
                    float g = tipOffsets[i], inside = g * Math.signum(g - tipOffsets[i - dir]);
                    keep *= 1 - pass * folds[i] * (1 - smooth(0, taper, inside));
                    pass *= 1 - folds[i];
                }
            }
            return keep;
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
            aimed = k * (1 - smooth(0, 1, Math.max(0, Math.max(Math.min(xa, xb), -Math.max(xa, xb)))));
            seen = ta + (tb - ta) * (mid - xm * dx / (dx * dx + 0.25f));
        }

        /**
         * Adds the light of a piece of ribbon {@code c} seen at {@code theta} with the light {@code k} to the part {@code b}
         * of the ribbon, where each piece has its own lower edge and rays, so that the light stays smooth where parts change.
         */
        void sheet(int c, int b, float theta, float k) {
            float th = angle(c, theta), along = DISTANCE[c] * (float) Math.tan(th) / scale, lit = k * strength(c, th, along);
            if (lit <= 0) return;
            float h = rise(c, theta, th, along);
            if (h < -6 * SKIRT) return;
            // A faint glow around the lower edge that also reaches under it, so that the sky under a curtain does not look like a dark hill.
            float glow = lit * 0.1f * (float) Math.exp(-Math.abs(h) / SKIRT);
            float soft = Math.max(0.3f * far(c), 0.7f * pixelUp), below = smooth(-2 * soft, soft, h);
            if (below <= 0) {
                bands[b] += glow;
                return;
            }
            below *= lit;
            float up = Math.max(h, 0), sd = along + DRIFT * t;
            float r0 = octave(sd / 40, t * 0.03f, seed + 21 + c, pixelAlong / 40);
            float r1 = octave(sd / 3, t * 0.1f, seed + 22 + c, pixelAlong / 3);
            float r2 = octave(sd / 1.1f, t * 0.25f, seed + 23 + c, pixelAlong / 1.1f);
            float r3 = octave(sd / 0.5f, t * 0.6f, seed + 24 + c, pixelAlong / 0.5f);
            float tall = octave(sd / 2 + 31.7f, t * 0.12f, seed + 25 + c, pixelAlong / 2);
            // Wide bundles of thin rays with dark sky between them, whose edges are never harder than about one pixel.
            float bundle = 0.35f + 0.65f * smooth(0.2f, 0.8f, r0);
            float hard = Math.max(0.05f, Math.min(0.3f, pixelAlong / 0.5f * 0.5f));
            float rays = bundle * smooth(0.55f - hard, 0.55f + hard, 0.3f * r1 + 0.38f * r2 + 0.32f * r3);
            // Each ray fades up over its own height, and the far ribbon is shorter, so that it is a low arc.
            float rise = up / (height * (0.07f + 0.45f * tall * tall) / (far(c) * far(c)));
            // The brightest light is a thin band at the lower edge, and above it the rays dissolve into the open sky.
            bands[b] += glow + below * (0.15f + 0.4f * bundle + 0.8f * rays) * (float) Math.exp(-up / 3.5f);
            beams[b] += below * 1.1f * rays * (float) Math.exp(-rise * (0.5f + 0.5f * rise));
            crowns[b] += below * 0.25f * rays * tall * (float) Math.exp(-(rise - 1.3f) * (rise - 1.3f) * 2);
        }

        /**
         * The km of the view above the lower edge of ribbon {@code c} at {@code theta}, with the azimuth {@code th} of
         * {@link #angle} and the place {@code along} of the rays there, which also sets {@link #pixelUp} and {@link #pixelAlong}.
         */
        float rise(int c, float theta, float th, float along) {
            offset(c, theta);
            float cos = (float) Math.cos(th);
            // The distance of a straight east-west line, moved toward and away from the viewer by the folds and by slow waves.
            float r = DISTANCE[c] / cos + d[1];
            for (int i = 0; i < NEAR_K.length; i++) {
                float kk = NEAR_K[i] * far(c) / scale;
                r += NEAR_AMP[i] * (float) Math.sin(kk * theta + phases[3 + i] + NEAR_RATE[i] * t);
            }
            // The altitude of the view above the ground at the distance of the sheet, with the curve of the earth.
            float tilt = Math.min(el + r / (2 * EARTH), 1.45f), cosT = (float) Math.cos(tilt);
            pixelUp = footprint * r / (cosT * cosT);
            // The km of the rays in one pixel, which is more where the view runs along the sheet, as near the tip of a fold.
            pixelAlong = wide * DISTANCE[c] / (cos * cos * Math.max(Math.abs(d[0]), 0.02f)) / scale;
            float sd = along + DRIFT * t;
            // The lower edge is nearly level, with gentle waves, as its kinks come from the folds.
            float edge = 1.3f * (float) Math.sin(sd / 23 + phases[5] + 0.02f * t) + 0.8f * (float) Math.sin(sd / 9 + phases[6] - 0.05f * t);
            return r * (float) Math.tan(tilt) - BASE - edge;
        }

        /**
         * How bright ribbon {@code c} is at the azimuth {@code th} of {@link #angle} with the place {@code along} of the rays:
         * parts of the ribbon are bright, faint or gaps of open sky, with wider gaps in the far ribbon and none at the center.
         */
        float strength(int c, float th, float along) {
            float part = value(along / 320, t * 0.004f, seed + 31 + c) + (c == 0 ? 0.35f * (float) Math.exp(-th * th / 0.25f) : 0);
            return smooth(0.3f + 0.15f * c, 0.6f + 0.15f * c, part) * (0.4f + 0.9f * value(along / 70, t * 0.01f, seed + 33 + c));
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
