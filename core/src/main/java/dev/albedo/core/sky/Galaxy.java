package dev.albedo.core.sky;

import static dev.albedo.core.sky.SkyMath.clamp01;
import static dev.albedo.core.sky.SkyMath.gauss;
import static dev.albedo.core.sky.SkyMath.smooth;
import static dev.albedo.core.sky.SkyMath.wave;

/** Java twin of {@code albedoGalaxy}, in galaxy units where 1 is about the radius of the disc. */
final class Galaxy {
    /** The distance from the galaxy center in galaxy units past which a galaxy has no light, out past its lobes. */
    static final float REACH = 3f;
    /** The length of a jet of a disc seen from its edge, in galaxy units. */
    static final float JET_LENGTH = 1.4f;
    /** The size of the grid cells of the knots of young stars, in galaxy units. */
    static final float KNOT_CELL = 0.05f;
    /** How much more the dust absorbs of each linear channel than of green, so that the light through it gets redder. */
    static final float[] DUST_REDDENING = {0.75f, 1f, 1.3f};
    /** The linear colors of the pink knots of hydrogen gas, of the red and magenta radio lobes and of the white nucleus. */
    static final float[] HALPHA = {1f, 0.1441f, 0.2705f}, LOBE = {0.9f, 0.1f, 0.35f}, NUCLEUS = {1f, 0.9047f, 0.7913f};
    /** The light of the large halo of old stars at the galaxy center. */
    static final float HALO = 0.4f;
    /** The distance between the rings of a lobe, for each galaxy unit of the length of its jet. */
    static final float LOBE_RING = 0.16f;
    /** The radius of the circle of noise that sets how many threads of dust go around the disc, about 6 for each unit. */
    static final float DUST_THREADS = 1.3f;
    /** The size of the grid cells of the regions of glowing gas and of the clusters, in galaxy units. */
    static final float GAS_CELL = 0.1f, CLUSTER_CELL = 0.08f;
    /** The radius of the circle of noise that a line of moving noise goes around, so that it comes back to its start. */
    static final float LOOP = 4f;
    /** The linear colors of the detail: the end of a jet, the rim of a lobe, a hot spot, a bow shock, the ionized cones, a gas region's heart. */
    static final float[] JET_END = {0.5f, 0.38f, 1f}, LOBE_RIM = {1f, 0.3f, 0.16f}, HOT_SPOT = {0.75f, 0.88f, 1f},
            BOW_SHOCK = {1f, 0.55f, 0.75f}, ION_CONE = {0.1f, 0.85f, 0.6f}, GAS_CORE = {1f, 0.62f, 0.72f};
    /** For each channel: the factors of the core color at the center of the bulge and far out, and the part of the light that dust gives back. */
    static final float[] BULGE_CORE = {1f, 1.1f, 1.25f}, BULGE_EDGE = {1f, 0.82f, 0.62f}, DUST_GLOW = {1f, 0.4f, 0.14f};
    /** The linear colors of a stream of stars and of a new star that explodes. */
    static final float[] STREAM = {0.75f, 0.8f, 1f}, SUPERNOVA = {0.8f, 0.9f, 1f};
    /** The turn of the loop of the stream against the disc and the place of its dwarf galaxy on the loop, in radians, and the axes of the loop. */
    static final float STREAM_TURN = 0.6f, STREAM_HEAD = 3.6f, STREAM_LONG = 1.8f;

    private Galaxy() {}

    /**
     * An active galaxy at the layer center, modeled on Centaurus A and Hercules A: a COLOR_2 bulge in a large halo, a disc with
     * COLOR_1 arms at TILT and a lane of dust that absorbs their light, and two COLOR_3 jets along the axis of the disc that end
     * in red lobes with rings, where DETAIL adds the fine parts and MOTION moves the jets, the nucleus and the lobes with the time.
     *
     * @param texel the width of one pixel in radians
     * @param time the seconds of the sky clock
     */
    static void sample(SkyGenerator.Layer l, float[] p, float texel, float time, float[] out) {
        out[3] = 0;
        float zz = -p[2];
        if (zz < 0.2f) return;
        // The plane of the image in galaxy units, where 1 is about the radius of the disc.
        float size = 0.22f * l.scale;
        float x = p[0] / zz / size, y = p[1] / zz / size;
        float rr = (float) Math.sqrt(x * x + y * y);
        if (rr > REACH) return;
        // The width of one pixel in galaxy units, where the plane of the image stretches by 1 / zz squared.
        float fp = texel / (size * zz * zz);
        float detail = l.detail, motion = l.motion;
        float incl = l.tilt * (float) (Math.PI / 2);
        float ci = (float) Math.cos(incl), si = (float) Math.sin(incl);
        // The axis ratios of the disc of stars and of the thinner layer of dust, each with its own thickness.
        float qs = (float) Math.sqrt(ci * ci + 0.0225f * si * si), qd = (float) Math.sqrt(ci * ci + 0.0036f * si * si);
        float bs = y / qs, bd = y / qd;
        float rs = (float) Math.sqrt(x * x + bs * bs), rd = (float) Math.sqrt(x * x + bd * bd);
        float twist = 1.5f + l.amount * 4f;
        float star = 0, armMix = 0, tau = 0, knots = 0, pink = 0, gasRegions = 0;
        if (rs < 1.6f) {
            float warp = Noise.fbmSoft(x * 3f, bs * 3f, 0.37f, l.seed + 1, 7, fp / qs * 3f);
            float theta = rs > 0 ? (float) Math.atan2(bs, x) : 0f;
            float wave = 0.5f + 0.5f * (float) Math.cos(2 * (theta - twist * (float) Math.log(rs + 0.05f)) + 1.4f * warp);
            // The arms start out of the smooth inner disc, so that the center has no curl.
            float arm = wave * wave * wave * smooth(0.2f, 0.6f, rs);
            float edge = 1 - smooth(1.1f, 1.6f, rs);
            float clumps = smooth(-0.4f, 0.5f, warp);
            star = (float) Math.exp(-rs / 0.4f) * (0.08f + 2f * arm * (0.2f + 0.8f * clumps)) * edge;
            armMix = clamp01(arm * 2.5f);
            if (l.dust > 0 && rd < 1.4f) {
                float thetaD = rd > 0 ? (float) Math.atan2(bd, x) : 0f;
                float lane = 0.5f + 0.5f * (float) Math.cos(2 * (thetaD - twist * (float) Math.log(rd + 0.05f)) + 1.4f * warp + 0.7f);
                lane *= lane;
                float clump = Noise.fbmSoft(x * 6f, bd * 6f, 1.7f, l.seed + 2, 5, fp / qd * 6f) * 0.5f + 0.5f;
                // A dense lane of dust in the plane of the disc over the center, and more dust in clumps along the inner edge of each arm.
                float density = (1 - smooth(0.9f, 1.4f, rd)) * (0.9f * (float) Math.exp(-rd / 0.45f) * (0.5f + clump)
                        + (float) Math.exp(-rd / 0.5f) * 2f * lane * lane * Math.max(0f, 2f * clump - 0.8f));
                tau = l.dust * 7f * density / (float) Math.sqrt(qd);
                if (detail > 0) {
                    // Threads of dust from noise in a frame that turns with the arms, so that they run along the arms and make no web.
                    float along = (float) Math.log(rd + 0.05f), phi = thetaD - twist * along;
                    float threads = Noise.fbmSoft(DUST_THREADS * (float) Math.cos(phi), DUST_THREADS * (float) Math.sin(phi), 1.6f * along,
                            l.seed + 31, 4, fp / qd * 5f * DUST_THREADS / (rd + 0.1f));
                    float fine = Noise.fbmSoft(x * 16f, bd * 16f, 5.1f, l.seed + 32, 3, fp / qd * 16f);
                    tau *= Math.max(0.2f, 1 + detail * (0.8f * threads + 0.3f * fine));
                }
            }
            if (fp < 0.5f * KNOT_CELL) {
                // Knots of young stars on a grid of the image plane, few of them bright, which show only on the arms.
                float gx = x / KNOT_CELL, gy = y / KNOT_CELL;
                int cx = (int) Math.floor(gx), cy = (int) Math.floor(gy);
                for (int i = -1; i <= 1; i++) {
                    for (int j = -1; j <= 1; j++) {
                        int hx = cx + i, hy = cy + j;
                        if (Noise.unit(Noise.hash(hx, hy, 0, l.seed + 77)) > 0.5f) continue;
                        float dx = (gx - hx - 0.2f - 0.6f * Noise.unit(Noise.hash(hx, hy, 0, l.seed + 78))) * KNOT_CELL;
                        float dy = (gy - hy - 0.2f - 0.6f * Noise.unit(Noise.hash(hx, hy, 0, l.seed + 79))) * KNOT_CELL;
                        float big = Noise.unit(Noise.hash(hx, hy, 0, l.seed + 80));
                        float radius = 0.003f + 0.006f * big;
                        float spread = Math.max(radius, 0.8f * fp);
                        float v = (0.15f + 2.5f * big * big * big) * radius / spread * (radius / spread)
                                * (float) Math.exp(-(dx * dx + dy * dy) / (spread * spread));
                        knots += v;
                        if (Noise.unit(Noise.hash(hx, hy, 0, l.seed + 81)) < 0.35f) pink += v;
                    }
                }
                // The knots are in groups, and fade out before a pixel is half a cell wide, where the search of 3 x 3 cells would cut a knot.
                float group = smooth(-0.05f, 0.35f, Noise.noise(x * 5f, bs * 5f, 2.9f, l.seed + 82));
                float k = arm * group * (float) Math.exp(-rs / 0.5f) * edge * 1.5f * (1 - smooth(0.25f * KNOT_CELL, 0.5f * KNOT_CELL, fp));
                knots *= k;
                pink *= k;
            }
            if (detail > 0) {
                // The grain of single bright stars in the arms, and a ring of young stars at the edge of the disc.
                star *= 1 + detail * 0.45f * Noise.noise(x * 46f, bs * 46f, 9.1f, l.seed + 41) * (1 - smooth(0.25f, 0.5f, fp / qs * 46f));
                float ring = gauss((rs - 1.08f) / 0.1f) * (0.25f + clumps) * (0.5f + 0.5f * wave);
                star += detail * 0.09f * ring;
                armMix = Math.max(armMix, clamp01(detail * ring * 1.5f));
                if (fp < 0.5f * GAS_CELL) {
                    // Large regions of glowing hydrogen where the arms make stars, each with a faint glow around it.
                    float gx = x / GAS_CELL, gy = y / GAS_CELL;
                    int cx = (int) Math.floor(gx), cy = (int) Math.floor(gy);
                    for (int i = -1; i <= 1; i++) {
                        for (int j = -1; j <= 1; j++) {
                            int hx = cx + i, hy = cy + j;
                            if (Noise.unit(Noise.hash(hx, hy, 0, l.seed + 90)) > 0.55f) continue;
                            float dx = (gx - hx - 0.2f - 0.6f * Noise.unit(Noise.hash(hx, hy, 0, l.seed + 91))) * GAS_CELL;
                            float dy = (gy - hy - 0.2f - 0.6f * Noise.unit(Noise.hash(hx, hy, 0, l.seed + 92))) * GAS_CELL;
                            float big = Noise.unit(Noise.hash(hx, hy, 0, l.seed + 93));
                            float radius = 0.006f + 0.014f * big * big, spread = Math.max(radius, 0.8f * fp);
                            float d2 = (dx * dx + dy * dy) / (spread * spread);
                            gasRegions += (0.3f + 1.2f * big) * radius / spread * (radius / spread)
                                    * ((float) Math.exp(-d2) + 0.25f * (float) Math.exp(-d2 / 9));
                        }
                    }
                    gasRegions *= detail * smooth(0.2f, 0.7f, arm) * (float) Math.exp(-rs / 0.75f) * edge * 1.3f
                            * (1 - smooth(0.25f * GAS_CELL, 0.5f * GAS_CELL, fp));
                }
            }
        }
        float rb = (float) Math.sqrt(x * x + y * y / 0.64f);
        // A small bright bulge in a large smooth halo of old stars, slightly longer along the jets, as the elliptical galaxy of Centaurus A.
        float rh = (float) Math.sqrt(x * x / 0.72f + y * y);
        float bulge = 1.8f * (float) Math.exp(-5 * Math.sqrt(rb)) + HALO * (float) Math.exp(-3.2f * Math.sqrt(rh)) * (1 - smooth(1.5f, 3f, rr));
        float[] extra = {0, 0, 0};
        if (detail > 0) {
            // Shells of stars in the halo on alternate sides of the disc, the rest of a small galaxy that fell in.
            float thH = (float) Math.atan2(y, x);
            for (int k = 0; k < 3; k++) {
                float radius = 1.25f + 0.36f * k;
                float toward = smooth(0.45f, 0.95f, (float) Math.cos(thH - (k % 2) * (float) Math.PI - 0.3f * k));
                float rsh = rh * (1 + 0.07f * Noise.noise(thH * 2f, k, 1f, l.seed + 50));
                float patchy = 0.6f + 0.6f * Noise.noise(x * 5f, y * 5f, k + 3f, l.seed + 51);
                bulge += detail * toward * patchy * (float) Math.exp(-Math.max(radius - rsh, 0f) / 0.1f) * (1 - smooth(radius, radius + 0.05f, rsh))
                        * (0.011f - 0.003f * k);
            }
            clusters(l, x, y, rr, fp, detail, extra);
            stream(l, x, y, fp, detail, extra);
        }
        // The part of the bulge light behind the dust, more on the near side of the disc, which is below its center, and most at the center.
        float s = 0.8f - y * 6f * si / Math.max(ci, 0.25f);
        float behind = 0.5f + 0.5f * s / (float) Math.sqrt(1 + s * s);
        float rn = Math.max(0.006f, fp);
        float nucleus = 2.5f * (0.006f / rn) * (0.006f / rn) * (float) Math.exp(-rr * rr / (rn * rn)) + 0.12f / (1 + rr * rr / 0.0009f);
        nucleus *= 1 + 0.5f * detail;
        // The nucleus flickers at two frequencies that are whole numbers of turns in the hour of the sky clock.
        if (motion > 0) nucleus *= 1 + motion * (0.16f * wave(time * (1320f / 3600f)) + 0.1f * wave(time * (3384f / 3600f)));
        float jet = 0, clear = 0, lobe = 0, cool = 0, gas = 0;
        float[] jetColor = l.color3.clone(), lobeColor = LOBE.clone();
        float side = y < 0 ? -1f : 1f;
        if (l.jets > 0) {
            float reach = JET_LENGTH * (0.3f + 0.7f * si);
            float t = Math.abs(y) / reach;
            // The jets bend in opposite directions, as a precessing jet does, and the near jet is brighter from relativistic beaming.
            float dx = x - side * 0.12f * t * t - 0.03f * t * Noise.noise(t * 5f, side * 3.1f, 0.6f, l.seed + 502);
            float beam = side > 0 ? 1f : 0.3f + 0.15f * detail;
            // The part of the jet light that the dust does not dim: the far jet is behind the disc, and the near jet starts in it.
            clear = side > 0 ? smooth(0.02f, 0.12f, t) : 0f;
            // Each jet is a cone that opens out, and its light goes into the lobe.
            float width = 0.005f + 0.03f * t;
            float spread = Math.max(width, 0.8f * fp);
            float fade = 1 - smooth(0.25f, 0.5f, 16f / reach * fp);
            // With motion the knots go out along the jet, on a line of noise that comes back to its start in the hour of the sky clock.
            float knotNoise = motion > 0 ? loop(t * 16f, turns(130, motion, time), side * 7.3f, l.seed + 501)
                    : Noise.noise(t * 16f, side * 7.3f, 1.7f, l.seed + 501);
            float knot = Math.max(0f, 0.55f + 0.7f * fade * knotNoise);
            float tip = 1 - smooth(0.6f, 1.05f, t);
            float core = (float) Math.exp(-dx * dx / (spread * spread));
            if (detail > 0) {
                // The beam sways from side to side at no fixed step, a fainter strand crosses it, and shocks make some knots brighter.
                float sway = loop(t * 9f, turns(73, motion, time), side * 4.7f, l.seed + 505);
                float cross = loop(t * 13f, turns(106, motion, time), side * 2.9f, l.seed + 506);
                float shock = Math.max(0f, loop(t * 11f, turns(89, motion, time), side * 1.9f, l.seed + 507));
                float dm = dx - detail * 1.6f * width * sway * fade, ds = dx + detail * 2.2f * width * cross * fade;
                float thin = Math.max(0.6f * width, 0.8f * fp);
                core = (float) Math.exp(-dm * dm / (spread * spread))
                        + detail * 0.45f * 0.6f * width / thin * (float) Math.exp(-ds * ds / (thin * thin));
                knot *= 1 + detail * 4f * shock * shock * fade * (1 - smooth(0.5f, 0.95f, t));
                // A faint sheath of gas around the beam.
                jet = detail * 0.12f * beam * (float) Math.exp(-dx * dx / (9 * spread * spread)) / (1 + 3 * t) * tip;
                // The beam is white where it is hot at its start and violet where it cools at its end.
                float start = 1 - smooth(0f, 0.3f, t), end = 0.6f * smooth(0.55f, 1f, t);
                for (int c = 0; c < 3; c++) {
                    float hot = l.color3[c] + (1 - l.color3[c]) * 0.7f * start;
                    jetColor[c] += detail * (hot + (JET_END[c] - hot) * end - l.color3[c]);
                }
            }
            jet += 1.4f * beam * width / spread * core * knot / (1 + 3 * t) * tip;
            // With motion the turbulence of the lobes changes slowly, back and forth along the third axis of its noise.
            float drift = motion > 0 ? 0.8f * wave(turns(10, motion, time)) : 0f;
            float turb = Noise.fbmSoft(x * 2.4f, y * 2.4f, side * 5.1f + drift, l.seed + 601, 7, fp * 2.4f);
            float thread = 1 - Math.abs(turb);
            thread *= thread;
            thread *= thread;
            // A lobe is longer along the jet than across it, and the turbulence moves its edge.
            float lx = x - side * 0.19f, ly = (y - side * reach * 1.3f) / 1.3f;
            float dl = (float) Math.sqrt(lx * lx + ly * ly) / (0.4f * reach) + 0.4f * turb;
            // Broken arcs of shells around a point on the jet, as the rings in the lobes of Hercules A, which fade before they get too thin.
            float sx = x - side * 0.14f, sy = y - side * reach * 0.85f;
            float bend = Noise.noise(x * 3f, y * 3f, side * 1.7f, l.seed + 603);
            float shell = 0.5f + 0.5f * (float) Math.cos((float) Math.sqrt(sx * sx + sy * sy) / (LOBE_RING * reach) * 6.2831853f + 2.5f * bend);
            shell *= shell * smooth(-0.15f, 0.25f, Noise.noise(x * 4f, y * 4f, side * 2.3f, l.seed + 604))
                    * (1 - smooth(0.12f, 0.25f, fp / (LOBE_RING * reach)));
            float hx = x - side * 0.12f, hy = y - side * reach;
            float hot = 0.25f * (float) Math.exp(-(hx * hx + hy * hy) / 0.0016f);
            float body = Math.max(0.08f, 0.2f + 0.35f * turb + 0.6f * thread) * (0.5f + 1.6f * shell);
            lobe = ((float) Math.exp(-2 * dl * dl) * body * 0.35f + hot) * l.jets;
            // The inner part of a lobe, where the jet ends, takes some of the jet color.
            cool = 0.4f * (1 - smooth(0.1f, 0.9f, dl));
            // Gas near the jet that its beam lights, less far from the axis.
            gas = 0.08f * thread * (float) Math.exp(-Math.abs(dx) / (0.08f + 0.15f * t)) * smooth(0.1f, 0.4f, t) * (1 - smooth(0.9f, 1.3f, t));
            jet *= l.jets;
            gas *= l.jets;
            if (detail > 0) {
                float far = side > 0 ? 1f : 0.6f, strength = detail * l.jets * far;
                // Filaments in the lobe, and a rim where it pushes into the gas around it, which glows as embers.
                float fil = 1 - Math.abs(Noise.fbmSoft(x * 3.2f, y * 3.2f, side * 4.2f + drift, l.seed + 611, 6, fp * 3.2f));
                fil *= fil * fil;
                fil *= fil;
                lobe += detail * l.jets * ((float) Math.exp(-1.6f * dl * dl) * fil * 0.3f + gauss((dl - 0.95f) / 0.2f) * (0.015f + 0.09f * thread));
                float ember = detail * 0.55f * smooth(0.5f, 1.1f, dl);
                for (int c = 0; c < 3; c++) lobeColor[c] += (LOBE_RIM[c] - LOBE[c]) * ember;
                // The hot spot where the jet ends, and the thin bow shock ahead of it.
                float spot = 0.0005f + fp * fp, h2 = (hx * hx + hy * hy) / spot;
                float hotSpot = strength * (0.9f * 0.0005f / spot * (float) Math.exp(-h2) + 0.1f / (1 + h2 / 8));
                float bx = x - side * 0.12f, by = y - side * (reach - 0.08f), br = (float) Math.sqrt(bx * bx + by * by);
                float ahead = smooth(0.45f, 0.95f, by * side / Math.max(br, 1e-4f));
                float thin = Math.max(0.014f, 0.8f * fp);
                float bow = strength * 0.13f * 0.014f / thin
                        * gauss((br - 0.19f - 0.02f * Noise.noise(bx * 6f, by * 6f, side * 2f, l.seed + 612)) / thin)
                        * ahead * (0.5f + 0.7f * thread);
                // Cones of gas beside the nucleus that its light ionizes.
                float angle = Math.abs(dx) / (Math.abs(y) + 0.03f);
                float cone = strength * 0.2f * (1 - smooth(0.28f, 0.62f, angle)) * (float) Math.exp(-Math.abs(y) / 0.32f)
                        * smooth(0.04f, 0.14f, Math.abs(y)) * (0.35f + 0.9f * thread);
                for (int c = 0; c < 3; c++) extra[c] += hotSpot * HOT_SPOT[c] + bow * BOW_SHOCK[c] + cone * ION_CONE[c];
            }
        }
        if (motion > 0) supernova(l, x, y, qs, fp, motion, time, extra);
        float bright = Math.max(l.alpha1, l.alpha2), max = 0;
        float graded = smooth(0.03f, 0.9f, rr), back = detail * 0.55f * tau * (float) Math.exp(1 - tau);
        float pale = clamp01(gasRegions);
        for (int c = 0; c < 3; c++) {
            float extinction = tau * DUST_REDDENING[c];
            float through = (float) Math.exp(-extinction), mixed = 1 / (1 + 0.5f * extinction);
            float disc = l.color2[c] + (l.color1[c] - l.color2[c]) * armMix;
            // With detail the bulge is pale at its core and amber far out, and the dust gives back a warm part of the light that it takes.
            float bulgeColor = l.color2[c] * (1 + detail * (BULGE_CORE[c] + (BULGE_EDGE[c] - BULGE_CORE[c]) * graded - 1));
            float taken = bulge * bulgeColor * behind * (1 - through);
            float v = bulge * bulgeColor * (1 - behind * (1 - through)) + 0.9f * star * disc * mixed
                    + ((knots - pink) * l.color1[c] + pink * HALPHA[c] + gasRegions * (HALPHA[c] + (GAS_CORE[c] - HALPHA[c]) * pale)) * mixed
                    + nucleus * (float) Math.sqrt(through) * NUCLEUS[c]
                    + jet * jetColor[c] * (through + (1 - through) * clear) + lobe * (lobeColor[c] + (l.color3[c] - lobeColor[c]) * cool)
                    + gas * l.color3[c] + taken * back * DUST_GLOW[c] + extra[c];
            out[c] = v * bright;
            max = Math.max(max, out[c]);
        }
        // The light as a color of at most 1 and an alpha, so that Add gives the full light and Normal stays near it.
        float a = Math.min(1f, max);
        for (int c = 0; c < 3; c++) out[c] /= Math.max(a, 1e-6f);
        out[3] = a;
    }

    /** The turns at a time of a motion that makes a whole number of turns in the hour of the sky clock, {@code perHour} of them at a motion of 1. */
    private static float turns(float perHour, float motion, float time) {
        return (float) Math.floor(perHour * motion + 0.5f) * time / 3600f;
    }

    /**
     * Noise along a line at {@code u} that moves with the time: the line goes around a circle of radius {@link #LOOP}, and
     * one turn moves it once around, so that it comes back to its start.
     */
    private static float loop(float u, float turns, float lane, int seed) {
        double a = u / LOOP - 2 * Math.PI * (turns - Math.floor(turns));
        return Noise.noise(LOOP * (float) Math.cos(a), LOOP * (float) Math.sin(a), lane, seed);
    }

    /** Adds the globular clusters of a galaxy: small soft points around its halo, more of them near the bulge. */
    private static void clusters(SkyGenerator.Layer l, float x, float y, float rr, float fp, float detail, float[] light) {
        if (fp >= 0.5f * CLUSTER_CELL) return;
        float gx = x / CLUSTER_CELL, gy = y / CLUSTER_CELL;
        int cx = (int) Math.floor(gx), cy = (int) Math.floor(gy);
        float sum = 0;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                int hx = cx + i, hy = cy + j;
                float rc = (float) Math.sqrt((hx + 0.5f) * (hx + 0.5f) + (hy + 0.5f) * (hy + 0.5f)) * CLUSTER_CELL;
                if (rc < 0.22f || Noise.unit(Noise.hash(hx, hy, 0, l.seed + 941)) > 0.8f * (float) Math.exp(-rc / 0.7f)) continue;
                float dx = (gx - hx - 0.2f - 0.6f * Noise.unit(Noise.hash(hx, hy, 0, l.seed + 942))) * CLUSTER_CELL;
                float dy = (gy - hy - 0.2f - 0.6f * Noise.unit(Noise.hash(hx, hy, 0, l.seed + 943))) * CLUSTER_CELL;
                float wide = Math.max(0.0035f, 0.75f * fp), d2 = (dx * dx + dy * dy) / (wide * wide);
                sum += (0.1f + 0.3f * Noise.unit(Noise.hash(hx, hy, 0, l.seed + 944))) * (0.0035f / wide) * (0.0035f / wide)
                        * ((float) Math.exp(-d2) + 0.12f / (1 + d2 / 5) * (float) Math.exp(-d2 / 40));
            }
        }
        sum *= detail * (1 - smooth(0.25f * CLUSTER_CELL, 0.5f * CLUSTER_CELL, fp));
        for (int c = 0; c < 3; c++) light[c] += sum * NUCLEUS[c];
    }

    /** Adds a stream of stars on part of a loop around a galaxy, which gets wider and fainter behind the dwarf galaxy that it comes from. */
    private static void stream(SkyGenerator.Layer l, float x, float y, float fp, float detail, float[] light) {
        float cos = (float) Math.cos(STREAM_TURN), sin = (float) Math.sin(STREAM_TURN);
        float ux = (cos * x + sin * y) / STREAM_LONG, uy = -sin * x + cos * y;
        float e = (float) Math.sqrt(ux * ux + uy * uy), th = (float) Math.atan2(uy, ux);
        float a = (th - STREAM_HEAD) / (float) (2 * Math.PI);
        a -= (float) Math.floor(a);
        float wide = 0.03f + 0.1f * a;
        float stream = detail * 0.048f * gauss((e - 1) / wide) * (1 - smooth(0.05f, 0.75f, a)) * 0.03f / wide
                * (0.55f + 0.6f * Noise.noise(th * 5f, e * 6f, 2f, l.seed + 821));
        float dx = (ux - (float) Math.cos(STREAM_HEAD)) * STREAM_LONG, dy = uy - (float) Math.sin(STREAM_HEAD);
        float point = Math.max(0.008f, fp);
        float dwarf = detail * (0.16f * (float) Math.exp(-Math.sqrt(dx * dx + 2.25f * dy * dy) / 0.035f)
                + 0.3f * (0.008f / point) * (0.008f / point) * gauss((float) Math.sqrt(dx * dx + dy * dy) / point));
        for (int c = 0; c < 3; c++) light[c] += stream * STREAM[c] + dwarf * l.color2[c];
    }

    /**
     * Adds the one new star that explodes in the disc in each slot of time, with a fast rise and a slow fall, where the hour
     * of the sky clock has a whole number of slots, more of them with more motion.
     */
    private static void supernova(SkyGenerator.Layer l, float x, float y, float qs, float fp, float motion, float time, float[] light) {
        float slots = (float) Math.floor(8 + 72 * motion + 0.5f), period = 3600f / slots;
        float slot = Math.min(slots - 1, (float) Math.floor(time / period)), age = time - slot * period;
        float rise = smooth(0f, 0.4f, age) * (float) Math.exp(-Math.max(age - 0.4f, 0f) / 1.6f);
        if (rise < 1e-4f) return;
        float a = 6.2831853f * Noise.unit(Noise.hash((int) slot, 3, 0, l.seed + 701));
        float r = 0.25f + 0.6f * Noise.unit(Noise.hash((int) slot, 3, 0, l.seed + 702));
        float dx = x - r * (float) Math.cos(a), dy = y - r * (float) Math.sin(a) * qs;
        float wide = Math.max(0.006f, fp), d2 = (dx * dx + dy * dy) / (wide * wide);
        float v = 2.5f * rise * (0.006f / wide) * (0.006f / wide) * ((float) Math.exp(-d2) + 0.04f / (1 + d2 / 9));
        for (int c = 0; c < 3; c++) light[c] += v * SUPERNOVA[c];
    }
}
