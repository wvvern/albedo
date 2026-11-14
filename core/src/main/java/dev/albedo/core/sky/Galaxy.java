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
    /** One {@link Parts} for each thread, so that a pixel of the bake makes no new arrays. */
    private static final ThreadLocal<Parts> PARTS = ThreadLocal.withInitial(Parts::new);

    /** The values of one pixel that the parts of a galaxy share, and the light of each part; twin of {@code AlbedoGalaxyParts}. */
    private static final class Parts {
        /** The place in galaxy units, its distance from the center, and the width of one pixel in galaxy units. */
        float x, y, rr, fp;
        float detail, motion, time;
        /** The cosine and sine of the tilt, the axis ratios of the stars and the dust, and the place and distance in each disc frame. */
        float ci, si, qs, qd, bs, bd, rs, rd;
        float twist, warp, wave, arm, edge, clumps;
        float star, armMix, tau, knots, pink, gasRegions;
        float bulge, nucleus;
        float jet, clear, lobe, cool, gas;
        final float[] jetColor = new float[3], lobeColor = new float[3], extra = new float[3];
        /** The offset of a point from a jittered cell center, see {@link #jitter}. */
        final float[] d = new float[2];
    }

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
        Parts g = PARTS.get();
        g.x = x;
        g.y = y;
        g.rr = rr;
        // The width of one pixel in galaxy units, where the plane of the image stretches by 1 / zz squared.
        g.fp = texel / (size * zz * zz);
        g.detail = l.detail;
        g.motion = l.motion;
        g.time = time;
        float incl = l.tilt * (float) (Math.PI / 2);
        g.ci = (float) Math.cos(incl);
        g.si = (float) Math.sin(incl);
        // The axis ratios of the disc of stars and of the thinner layer of dust, each with its own thickness.
        g.qs = (float) Math.sqrt(g.ci * g.ci + 0.0225f * g.si * g.si);
        g.qd = (float) Math.sqrt(g.ci * g.ci + 0.0036f * g.si * g.si);
        g.bs = y / g.qs;
        g.bd = y / g.qd;
        g.rs = (float) Math.sqrt(x * x + g.bs * g.bs);
        g.rd = (float) Math.sqrt(x * x + g.bd * g.bd);
        g.twist = 1.5f + l.amount * 4f;
        g.star = 0;
        g.armMix = 0;
        g.tau = 0;
        g.knots = 0;
        g.pink = 0;
        g.gasRegions = 0;
        for (int c = 0; c < 3; c++) g.extra[c] = 0;
        if (g.rs < 1.6f) disc(l, g);
        shells(l, g);
        nucleus(g);
        jetsAndLobes(l, g);
        if (g.motion > 0) supernova(l, g);
        compose(l, g, out);
    }

    /** The disc of stars with its arms, its dust and its knots, and with detail its grain, the ring at its edge and its gas regions. */
    private static void disc(SkyGenerator.Layer l, Parts g) {
        float x = g.x, bs = g.bs, rs = g.rs, fp = g.fp, qs = g.qs, detail = g.detail;
        g.warp = Noise.fbmSoft(x * 3f, bs * 3f, 0.37f, l.seed + 1, 7, fp / qs * 3f);
        float theta = rs > 0 ? (float) Math.atan2(bs, x) : 0f;
        g.wave = 0.5f + 0.5f * (float) Math.cos(2 * (theta - g.twist * (float) Math.log(rs + 0.05f)) + 1.4f * g.warp);
        // The arms start out of the smooth inner disc, so that the center has no curl.
        g.arm = g.wave * g.wave * g.wave * smooth(0.2f, 0.6f, rs);
        g.edge = 1 - smooth(1.1f, 1.6f, rs);
        g.clumps = smooth(-0.4f, 0.5f, g.warp);
        g.star = (float) Math.exp(-rs / 0.4f) * (0.08f + 2f * g.arm * (0.2f + 0.8f * g.clumps)) * g.edge;
        g.armMix = clamp01(g.arm * 2.5f);
        if (l.dust > 0 && g.rd < 1.4f) dust(l, g);
        if (fp < 0.5f * KNOT_CELL) knots(l, g);
        if (detail > 0) {
            // The grain of single bright stars in the arms, and a ring of young stars at the edge of the disc.
            g.star *= 1 + detail * 0.45f * Noise.noise(x * 46f, bs * 46f, 9.1f, l.seed + 41) * (1 - smooth(0.25f, 0.5f, fp / qs * 46f));
            float ring = gauss((rs - 1.08f) / 0.1f) * (0.25f + g.clumps) * (0.5f + 0.5f * g.wave);
            g.star += detail * 0.09f * ring;
            g.armMix = Math.max(g.armMix, clamp01(detail * ring * 1.5f));
            if (fp < 0.5f * GAS_CELL) gasRegions(l, g);
        }
    }

    /** The optical depth of the dust of the disc: a lane over the center, clumps along the arms and, with detail, threads. */
    private static void dust(SkyGenerator.Layer l, Parts g) {
        float x = g.x, bd = g.bd, rd = g.rd, fp = g.fp, qd = g.qd, twist = g.twist, detail = g.detail;
        float thetaD = rd > 0 ? (float) Math.atan2(bd, x) : 0f;
        float lane = 0.5f + 0.5f * (float) Math.cos(2 * (thetaD - twist * (float) Math.log(rd + 0.05f)) + 1.4f * g.warp + 0.7f);
        lane *= lane;
        float clump = Noise.fbmSoft(x * 6f, bd * 6f, 1.7f, l.seed + 2, 5, fp / qd * 6f) * 0.5f + 0.5f;
        // A dense lane of dust in the plane of the disc over the center, and more dust in clumps along the inner edge of each arm.
        float density = (1 - smooth(0.9f, 1.4f, rd)) * (0.9f * (float) Math.exp(-rd / 0.45f) * (0.5f + clump)
                + (float) Math.exp(-rd / 0.5f) * 2f * lane * lane * Math.max(0f, 2f * clump - 0.8f));
        g.tau = l.dust * 7f * density / (float) Math.sqrt(qd);
        if (detail > 0) {
            // Threads of dust from noise in a frame that turns with the arms, so that they run along the arms and make no web.
            float along = (float) Math.log(rd + 0.05f), phi = thetaD - twist * along;
            float threads = Noise.fbmSoft(DUST_THREADS * (float) Math.cos(phi), DUST_THREADS * (float) Math.sin(phi), 1.6f * along,
                    l.seed + 31, 4, fp / qd * 5f * DUST_THREADS / (rd + 0.1f));
            float fine = Noise.fbmSoft(x * 16f, bd * 16f, 5.1f, l.seed + 32, 3, fp / qd * 16f);
            g.tau *= Math.max(0.2f, 1 + detail * (0.8f * threads + 0.3f * fine));
        }
    }

    /** Knots of young stars on a grid of the image plane, few of them bright, which show only on the arms. */
    private static void knots(SkyGenerator.Layer l, Parts g) {
        float fp = g.fp;
        float gx = g.x / KNOT_CELL, gy = g.y / KNOT_CELL;
        int cx = (int) Math.floor(gx), cy = (int) Math.floor(gy);
        float knots = 0, pink = 0;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                int hx = cx + i, hy = cy + j;
                if (Noise.unit(Noise.hash(hx, hy, 0, l.seed + 77)) > 0.5f) continue;
                jitter(gx, gy, hx, hy, l.seed + 78, KNOT_CELL, g.d);
                float dx = g.d[0], dy = g.d[1];
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
        float group = smooth(-0.05f, 0.35f, Noise.noise(g.x * 5f, g.bs * 5f, 2.9f, l.seed + 82));
        float k = g.arm * group * (float) Math.exp(-g.rs / 0.5f) * g.edge * 1.5f * (1 - smooth(0.25f * KNOT_CELL, 0.5f * KNOT_CELL, fp));
        g.knots = knots * k;
        g.pink = pink * k;
    }

    /** Large regions of glowing hydrogen where the arms make stars, each with a faint glow around it. */
    private static void gasRegions(SkyGenerator.Layer l, Parts g) {
        float fp = g.fp;
        float gx = g.x / GAS_CELL, gy = g.y / GAS_CELL;
        int cx = (int) Math.floor(gx), cy = (int) Math.floor(gy);
        float gas = 0;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                int hx = cx + i, hy = cy + j;
                if (Noise.unit(Noise.hash(hx, hy, 0, l.seed + 90)) > 0.55f) continue;
                jitter(gx, gy, hx, hy, l.seed + 91, GAS_CELL, g.d);
                float dx = g.d[0], dy = g.d[1];
                float big = Noise.unit(Noise.hash(hx, hy, 0, l.seed + 93));
                float radius = 0.006f + 0.014f * big * big, spread = Math.max(radius, 0.8f * fp);
                float d2 = (dx * dx + dy * dy) / (spread * spread);
                gas += (0.3f + 1.2f * big) * radius / spread * (radius / spread) * ((float) Math.exp(-d2) + 0.25f * (float) Math.exp(-d2 / 9));
            }
        }
        gas *= g.detail * smooth(0.2f, 0.7f, g.arm) * (float) Math.exp(-g.rs / 0.75f) * g.edge * 1.3f
                * (1 - smooth(0.25f * GAS_CELL, 0.5f * GAS_CELL, fp));
        g.gasRegions = gas;
    }

    /**
     * The offset of the grid place {@code (gx, gy)} from the jittered center of the cell {@code (hx, hy)}, times the cell size,
     * with the hash salts {@code seed} and {@code seed + 1}.
     */
    private static void jitter(float gx, float gy, int hx, int hy, int seed, float cell, float[] out) {
        out[0] = (gx - hx - 0.2f - 0.6f * Noise.unit(Noise.hash(hx, hy, 0, seed))) * cell;
        out[1] = (gy - hy - 0.2f - 0.6f * Noise.unit(Noise.hash(hx, hy, 0, seed + 1))) * cell;
    }

    /** The light of the bulge in its halo, and with detail the shells of the halo, the globular clusters and the stream. */
    private static void shells(SkyGenerator.Layer l, Parts g) {
        float x = g.x, y = g.y, detail = g.detail;
        float rb = (float) Math.sqrt(x * x + y * y / 0.64f);
        // A small bright bulge in a large smooth halo of old stars, slightly longer along the jets, as the elliptical galaxy of Centaurus A.
        float rh = (float) Math.sqrt(x * x / 0.72f + y * y);
        g.bulge = 1.8f * (float) Math.exp(-5 * Math.sqrt(rb)) + HALO * (float) Math.exp(-3.2f * Math.sqrt(rh)) * (1 - smooth(1.5f, 3f, g.rr));
        if (detail <= 0) return;
        // Shells of stars in the halo on alternate sides of the disc, the rest of a small galaxy that fell in.
        float thH = (float) Math.atan2(y, x);
        for (int k = 0; k < 3; k++) {
            float radius = 1.25f + 0.36f * k;
            float toward = smooth(0.45f, 0.95f, (float) Math.cos(thH - (k % 2) * (float) Math.PI - 0.3f * k));
            float rsh = rh * (1 + 0.07f * Noise.noise(thH * 2f, k, 1f, l.seed + 50));
            float patchy = 0.6f + 0.6f * Noise.noise(x * 5f, y * 5f, k + 3f, l.seed + 51);
            g.bulge += detail * toward * patchy * (float) Math.exp(-Math.max(radius - rsh, 0f) / 0.1f) * (1 - smooth(radius, radius + 0.05f, rsh))
                    * (0.011f - 0.003f * k);
        }
        clusters(l, g);
        stream(l, g);
    }

    /** The light of the nucleus, which flickers with motion. */
    private static void nucleus(Parts g) {
        float rr = g.rr, rn = Math.max(0.006f, g.fp);
        float nucleus = 2.5f * (0.006f / rn) * (0.006f / rn) * (float) Math.exp(-rr * rr / (rn * rn)) + 0.12f / (1 + rr * rr / 0.0009f);
        nucleus *= 1 + 0.5f * g.detail;
        // The nucleus flickers at two frequencies that are whole numbers of turns in the hour of the sky clock.
        if (g.motion > 0) nucleus *= 1 + g.motion * (0.16f * wave(g.time * (1320f / 3600f)) + 0.1f * wave(g.time * (3384f / 3600f)));
        g.nucleus = nucleus;
    }

    /** The jets along the axis of the disc and the lobes at their ends, and with detail the hot spots, bow shocks and cones. */
    private static void jetsAndLobes(SkyGenerator.Layer l, Parts g) {
        float[] jetColor = g.jetColor, lobeColor = g.lobeColor;
        for (int c = 0; c < 3; c++) {
            jetColor[c] = l.color3[c];
            lobeColor[c] = LOBE[c];
        }
        g.jet = 0;
        g.clear = 0;
        g.lobe = 0;
        g.cool = 0;
        g.gas = 0;
        if (l.jets <= 0) return;
        float x = g.x, y = g.y, fp = g.fp, detail = g.detail, motion = g.motion, time = g.time;
        float side = y < 0 ? -1f : 1f;
        float reach = JET_LENGTH * (0.3f + 0.7f * g.si);
        float t = Math.abs(y) / reach;
        // The jets bend in opposite directions, as a precessing jet does, and the near jet is brighter from relativistic beaming.
        float dx = x - side * 0.12f * t * t - 0.03f * t * Noise.noise(t * 5f, side * 3.1f, 0.6f, l.seed + 502);
        float beam = side > 0 ? 1f : 0.3f + 0.15f * detail;
        // The part of the jet light that the dust does not dim: the far jet is behind the disc, and the near jet starts in it.
        g.clear = side > 0 ? smooth(0.02f, 0.12f, t) : 0f;
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
        float jet = 0;
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
        float lobe = ((float) Math.exp(-2 * dl * dl) * body * 0.35f + hot) * l.jets;
        // The inner part of a lobe, where the jet ends, takes some of the jet color.
        g.cool = 0.4f * (1 - smooth(0.1f, 0.9f, dl));
        // Gas near the jet that its beam lights, less far from the axis.
        float gas = 0.08f * thread * (float) Math.exp(-Math.abs(dx) / (0.08f + 0.15f * t)) * smooth(0.1f, 0.4f, t) * (1 - smooth(0.9f, 1.3f, t));
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
            for (int c = 0; c < 3; c++) g.extra[c] += hotSpot * HOT_SPOT[c] + bow * BOW_SHOCK[c] + cone * ION_CONE[c];
        }
        g.jet = jet;
        g.lobe = lobe;
        g.gas = gas;
    }

    /** The light of all parts through the dust, as a color of at most 1 and an alpha. */
    private static void compose(SkyGenerator.Layer l, Parts g, float[] out) {
        float detail = g.detail, tau = g.tau, bulge = g.bulge, gasRegions = g.gasRegions;
        // The part of the bulge light behind the dust, more on the near side of the disc, which is below its center, and most at the center.
        float s = 0.8f - g.y * 6f * g.si / Math.max(g.ci, 0.25f);
        float behind = 0.5f + 0.5f * s / (float) Math.sqrt(1 + s * s);
        float bright = Math.max(l.alpha1, l.alpha2), max = 0;
        float graded = smooth(0.03f, 0.9f, g.rr), back = detail * 0.55f * tau * (float) Math.exp(1 - tau);
        float pale = clamp01(gasRegions);
        for (int c = 0; c < 3; c++) {
            float extinction = tau * DUST_REDDENING[c];
            float through = (float) Math.exp(-extinction), mixed = 1 / (1 + 0.5f * extinction);
            float disc = l.color2[c] + (l.color1[c] - l.color2[c]) * g.armMix;
            // With detail the bulge is pale at its core and amber far out, and the dust gives back a warm part of the light that it takes.
            float bulgeColor = l.color2[c] * (1 + detail * (BULGE_CORE[c] + (BULGE_EDGE[c] - BULGE_CORE[c]) * graded - 1));
            float taken = bulge * bulgeColor * behind * (1 - through);
            float v = bulge * bulgeColor * (1 - behind * (1 - through)) + 0.9f * g.star * disc * mixed
                    + ((g.knots - g.pink) * l.color1[c] + g.pink * HALPHA[c] + gasRegions * (HALPHA[c] + (GAS_CORE[c] - HALPHA[c]) * pale)) * mixed
                    + g.nucleus * (float) Math.sqrt(through) * NUCLEUS[c]
                    + g.jet * g.jetColor[c] * (through + (1 - through) * g.clear)
                    + g.lobe * (g.lobeColor[c] + (l.color3[c] - g.lobeColor[c]) * g.cool)
                    + g.gas * l.color3[c] + taken * back * DUST_GLOW[c] + g.extra[c];
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
    private static void clusters(SkyGenerator.Layer l, Parts g) {
        float fp = g.fp;
        if (fp >= 0.5f * CLUSTER_CELL) return;
        float gx = g.x / CLUSTER_CELL, gy = g.y / CLUSTER_CELL;
        int cx = (int) Math.floor(gx), cy = (int) Math.floor(gy);
        float sum = 0;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                int hx = cx + i, hy = cy + j;
                float rc = (float) Math.sqrt((hx + 0.5f) * (hx + 0.5f) + (hy + 0.5f) * (hy + 0.5f)) * CLUSTER_CELL;
                if (rc < 0.22f || Noise.unit(Noise.hash(hx, hy, 0, l.seed + 941)) > 0.8f * (float) Math.exp(-rc / 0.7f)) continue;
                jitter(gx, gy, hx, hy, l.seed + 942, CLUSTER_CELL, g.d);
                float dx = g.d[0], dy = g.d[1];
                float wide = Math.max(0.0035f, 0.75f * fp), d2 = (dx * dx + dy * dy) / (wide * wide);
                sum += (0.1f + 0.3f * Noise.unit(Noise.hash(hx, hy, 0, l.seed + 944))) * (0.0035f / wide) * (0.0035f / wide)
                        * ((float) Math.exp(-d2) + 0.12f / (1 + d2 / 5) * (float) Math.exp(-d2 / 40));
            }
        }
        sum *= g.detail * (1 - smooth(0.25f * CLUSTER_CELL, 0.5f * CLUSTER_CELL, fp));
        for (int c = 0; c < 3; c++) g.extra[c] += sum * NUCLEUS[c];
    }

    /** Adds a stream of stars on part of a loop around a galaxy, which gets wider and fainter behind the dwarf galaxy that it comes from. */
    private static void stream(SkyGenerator.Layer l, Parts g) {
        float x = g.x, y = g.y, fp = g.fp, detail = g.detail;
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
        for (int c = 0; c < 3; c++) g.extra[c] += stream * STREAM[c] + dwarf * l.color2[c];
    }

    /**
     * Adds the one new star that explodes in the disc in each slot of time, with a fast rise and a slow fall, where the hour
     * of the sky clock has a whole number of slots, more of them with more motion.
     */
    private static void supernova(SkyGenerator.Layer l, Parts g) {
        float motion = g.motion, time = g.time;
        float slots = (float) Math.floor(8 + 72 * motion + 0.5f), period = 3600f / slots;
        float slot = Math.min(slots - 1, (float) Math.floor(time / period)), age = time - slot * period;
        float rise = smooth(0f, 0.4f, age) * (float) Math.exp(-Math.max(age - 0.4f, 0f) / 1.6f);
        if (rise < 1e-4f) return;
        float a = 6.2831853f * Noise.unit(Noise.hash((int) slot, 3, 0, l.seed + 701));
        float r = 0.25f + 0.6f * Noise.unit(Noise.hash((int) slot, 3, 0, l.seed + 702));
        float dx = g.x - r * (float) Math.cos(a), dy = g.y - r * (float) Math.sin(a) * g.qs;
        float wide = Math.max(0.006f, g.fp), d2 = (dx * dx + dy * dy) / (wide * wide);
        float v = 2.5f * rise * (0.006f / wide) * (0.006f / wide) * ((float) Math.exp(-d2) + 0.04f / (1 + d2 / 9));
        for (int c = 0; c < 3; c++) g.extra[c] += v * SUPERNOVA[c];
    }
}
