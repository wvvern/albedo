package dev.albedo.core.sky;

import dev.albedo.core.config.SkyDesign;
import dev.albedo.core.config.SkyLayer;
import dev.albedo.core.config.SkyMask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

/**
 * Bakes a {@link SkyDesign} into a {@link SkyImage} in one pass from the bottom layer up, in linear light, where each
 * generator works on directions (no seam, no pinch at the zenith) and each filter size is in degrees.
 */
public final class SkyGenerator {
    /** Runs an index from 0 to count - 1 for each row (or column) of a pass, maybe in parallel. */
    interface Rows {
        /** @return false when the bake is out of date and stops */
        boolean run(int count, IntConsumer row);
    }

    private static final float RANGE = SkyImage.TOP - SkyImage.BOTTOM;
    /** The distance from the galaxy center in galaxy units past which a galaxy has no light, out past its lobes. */
    static final float GALAXY_REACH = 3f;
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
    /** The distance in degrees below the horizon over which a sky that stops at the horizon goes into the horizon color. */
    private static final float FADE = 3f;
    /** The number of horizon sectors on each side that soften a sector, about 28 degrees. */
    private static final int RING_SOFTEN = 5;
    /** The periods in seconds of the three waves of a pulse, which divide the hour of the sky clock so that no pulse jumps. */
    private static final float[] PULSE_PERIODS = {4.5f, 5.625f, 7.2f};
    /** The largest change of the natural log of the brightness of a pulse at a pulse of 100 percent. */
    private static final float PULSE_LIGHT = 2.4f;
    /** The octaves of the cloud noise, of which the field image holds the coarse ones that stay smooth at its half size. */
    static final int CLOUD_OCTAVES = 6, CLOUD_FIELD_OCTAVES = 3;

    private final SkyDesign design;
    private final int w, h;
    private final Rows rows;
    private final float[] sinLon, cosLon, sinEl, cosEl, elevation;
    /** The number of rows from the top that the layers and filters fill, where a filter reads the last of them for each row below. */
    private final int used;
    /** The width of one texel at the horizon, in radians. */
    private final float texel;
    private final float[] img;
    private float[] tmp;
    /** The size of each term image, half the size of the image. */
    private final int termW, termH;
    /** The term images as half float RGBA, see {@link SkyImage#terms}. */
    private short[] terms;

    private SkyGenerator(SkyDesign design, int w, int h, Rows rows) {
        this.design = design;
        this.w = w;
        this.h = h;
        this.rows = rows;
        sinLon = new float[w];
        cosLon = new float[w];
        for (int x = 0; x < w; x++) {
            double lon = (x + 0.5) / w * 2 * Math.PI;
            sinLon[x] = (float) Math.sin(lon);
            cosLon[x] = (float) Math.cos(lon);
        }
        sinEl = new float[h];
        cosEl = new float[h];
        elevation = new float[h];
        for (int y = 0; y < h; y++) {
            elevation[y] = SkyImage.TOP - (y + 0.5f) / h * RANGE;
            double el = Math.toRadians(elevation[y]);
            sinEl[y] = (float) Math.sin(el);
            cosEl[y] = (float) Math.cos(el);
        }
        int filled = h;
        while (filled > 1 && elevation[filled - 1] < SkyImage.layersBottom(design.extent())) filled--;
        used = filled;
        texel = (float) (2 * Math.PI / w);
        img = new float[w * h * 4];
        termW = Math.max(1, w / 2);
        termH = Math.max(1, h / 2);
    }

    /** Bakes on the calling thread. */
    public static SkyImage bake(SkyDesign design, int w, int h) {
        return bake(design, w, h, (count, row) -> {
            for (int i = 0; i < count; i++) row.accept(i);
            return true;
        });
    }

    /** Bakes with {@code rows}, and returns null when the bake stops because it is out of date. */
    static SkyImage bake(SkyDesign design, int w, int h, Rows rows) {
        return new SkyGenerator(design, w, h, rows).run();
    }

    private SkyImage run() {
        for (int i = 3; i < img.length; i += 4) img[i] = 1f;
        List<SkyLayer> layers = design.layers();
        // For each mask plane (layer id and mode): the index of the last layer that reads it, so that it can go early.
        Map<Integer, Integer> lastUse = new HashMap<Integer, Integer>();
        for (int i = 0; i < layers.size(); i++) {
            SkyMask m = layers.get(i).mask();
            if (!m.isNone() && design.indexOf(m.layer()) >= 0 && design.indexOf(m.layer()) < i) lastUse.put(planeKey(m), i);
        }
        Map<Integer, float[]> planes = new HashMap<Integer, float[]>();
        // The layer numbers do not depend on the horizon colors, which are known only after the layers.
        SkyProgram layout = SkyProgram.of(design, new float[SkyImage.RING * 3]);
        float[] block = layout.block();
        terms = new short[termW * termH * layout.terms() * 4];
        for (int i = 0; i < layers.size(); i++) {
            SkyLayer layer = layers.get(i);
            Layer ctx = new Layer(layer, block, i);
            float[] light = lastUse.containsKey(layer.id() * 2) ? new float[w * h] : null;
            float[] dark = lastUse.containsKey(layer.id() * 2 + 1) ? new float[w * h] : null;
            float[] mask = layer.mask().isNone() ? null : planes.get(planeKey(layer.mask()));
            boolean show = layer.output() == SkyLayer.Output.SHOW;
            boolean ok;
            if (layer.kind().role() == SkyLayer.Role.GENERATOR) {
                ok = rows.run(used, y -> generatorRow(ctx, y, show, mask, light, dark));
                if (ok && ctx.termSlot >= 0) ok = rows.run(termH, ty -> fieldRow(ctx, ty));
            } else {
                if (tmp == null) tmp = new float[w * h * 4];
                ok = filter(ctx) && rows.run(used, y -> blendRow(ctx, y, tmp, show, mask, light, dark));
            }
            if (!ok) return null;
            if (light != null) planes.put(layer.id() * 2, light);
            if (dark != null) planes.put(layer.id() * 2 + 1, dark);
            for (Map.Entry<Integer, Integer> e : lastUse.entrySet()) if (e.getValue() == i) planes.remove(e.getKey());
        }
        tmp = null;
        float[] ring = ring();
        if (design.extent() == SkyDesign.Extent.HORIZON && !rows.run(h, y -> horizonRow(y, ring))) return null;
        int[] argb = new int[w * h];
        if (!rows.run(h, y -> encodeRow(y, argb))) return null;
        return new SkyImage(argb, w, h, design, ring, SkyProgram.of(design, ring), termW, termH, terms);
    }

    /** Mixes the sky below the horizon into the horizon color (the fog color) of each direction, for a sky that stops at the horizon. */
    private void horizonRow(int y, float[] ring) {
        float k = smooth(0, FADE, -elevation[y]);
        if (k <= 0) return;
        float[] c = new float[3];
        for (int x = 0; x < w; x++) {
            SkyImage.ringAt(ring, (x + 0.5) / w * 360, c);
            int i = (y * w + x) * 4;
            for (int ch = 0; ch < 3; ch++) img[i + ch] += (c[ch] - img[i + ch]) * k;
        }
    }

    private static int planeKey(SkyMask m) {
        return m.layer() * 2 + (m.mode() == SkyMask.Mode.DARK ? 1 : 0);
    }

    private void generatorRow(Layer l, int y, boolean show, float[] mask, float[] light, float[] dark) {
        float[] s = new float[4], local = new float[3];
        for (int x = 0; x < w; x++) {
            l.local(cosEl[y] * sinLon[x], sinEl[y], -cosEl[y] * cosLon[x], local);
            generate(l, local, s);
            int p = y * w + x;
            planes(s, p, light, dark);
            if (show) blend(l, p, s, mask == null ? 1f : mask[p]);
        }
    }

    private void blendRow(Layer l, int y, float[] src, boolean show, float[] mask, float[] light, float[] dark) {
        float[] s = new float[4];
        for (int x = 0; x < w; x++) {
            int p = y * w + x;
            System.arraycopy(src, p * 4, s, 0, 4);
            planes(s, p, light, dark);
            if (show) blend(l, p, s, mask == null ? 1f : mask[p]);
        }
    }

    /** Writes the light and dark mask values of one pixel of a layer: its OKLab lightness times its alpha, and the rest. */
    private static void planes(float[] s, int p, float[] light, float[] dark) {
        if (light == null && dark == null) return;
        float lightness = Math.max(0f, Math.min(1f, Oklab.lightness(clamp01(s[0]), clamp01(s[1]), clamp01(s[2]))));
        float a = clamp01(s[3]);
        if (light != null) light[p] = lightness * a;
        if (dark != null) dark[p] = (1 - lightness) * a;
    }

    private void blend(Layer l, int p, float[] s, float maskValue) {
        float k = l.opacity * maskValue * clamp01(s[3]);
        if (k <= 0) return;
        int i = p * 4;
        for (int c = 0; c < 3; c++) {
            float b = img[i + c], v = s[c];
            switch (l.layer.blend()) {
                case ADD: img[i + c] = b + v * k; break;
                case SCREEN: {
                    float bb = clamp01(b), vv = clamp01(v);
                    img[i + c] = b + (vv - bb * vv) * k;
                    break;
                }
                case MULTIPLY: img[i + c] = b + (b * v - b) * k; break;
                default: img[i + c] = b + (v - b) * k;
            }
        }
    }

    private void encodeRow(int y, int[] argb) {
        for (int x = 0; x < w; x++) {
            int p = y * w + x;
            // A hash dither of half a step hides the bands of slow gradients.
            float d = Noise.unit(Noise.hash(x, y, 0, 77)) - 0.5f;
            int r = channel(Oklab.toSrgb(img[p * 4]) + d);
            int g = channel(Oklab.toSrgb(img[p * 4 + 1]) + d);
            int b = channel(Oklab.toSrgb(img[p * 4 + 2]) + d);
            argb[p] = 0xFF000000 | r << 16 | g << 8 | b;
        }
    }

    private static int channel(float v) {
        return Math.max(0, Math.min(255, Math.round(v)));
    }

    /** The mean linear color of each azimuth sector from 0 to 6 degrees of elevation, softened across near sectors. */
    private float[] ring() {
        float[] ring = new float[SkyImage.RING * 3];
        int[] count = new int[SkyImage.RING];
        for (int y = 0; y < h; y++) {
            if (elevation[y] < 0 || elevation[y] > 6) continue;
            for (int x = 0; x < w; x++) {
                int s = x * SkyImage.RING / w, p = (y * w + x) * 4;
                for (int c = 0; c < 3; c++) ring[s * 3 + c] += img[p + c];
                count[s]++;
            }
        }
        for (int s = 0; s < SkyImage.RING; s++) for (int c = 0; c < 3; c++) ring[s * 3 + c] /= Math.max(1, count[s]);
        // A wide triangle of weights, so that the horizon across the view stays near the one fog color of the view direction.
        float[] soft = new float[ring.length];
        int reach = RING_SOFTEN;
        for (int s = 0; s < SkyImage.RING; s++) {
            for (int d = -reach; d <= reach; d++) {
                int t = Math.floorMod(s + d, SkyImage.RING);
                float weight = (reach + 1 - Math.abs(d)) / (float) ((reach + 1) * (reach + 1));
                for (int c = 0; c < 3; c++) soft[s * 3 + c] += ring[t * 3 + c] * weight;
            }
        }
        return soft;
    }

    /** What a layer needs for each pixel, computed once for each bake. */
    static final class Layer {
        final SkyLayer layer;
        final float opacity;
        /** The rotation from the layer frame to the sky, row-major. */
        final float[] r = new float[9];
        final float scale;
        final float[] color1, color2, lab1, lab2;
        final float alpha1, alpha2, amount, height;
        final float tilt, dust, jets, detail, motion;
        final float[] color3 = new float[3];
        final int seed;
        final int termSlot;
        /** The stars of a stars layer, else null. */
        final SkyStars stars;
        /** The program block and the index of the layer in it, for a generator that reads its numbers itself. */
        final float[] block;
        final int index;

        /** Reads the numbers of layer {@code index} from the program, the same numbers that the sky shader reads. */
        Layer(SkyLayer layer, float[] block, int index) {
            this.layer = layer;
            this.block = block;
            this.index = index;
            opacity = block[SkyProgram.at(index, SkyProgram.OPACITY)];
            for (int row = 0; row < 3; row++) {
                for (int c = 0; c < 3; c++) r[c * 3 + row] = block[SkyProgram.at(index, SkyProgram.LOCAL + row * 4 + c)];
            }
            scale = block[SkyProgram.at(index, SkyProgram.SCALE)];
            color1 = slice(block, SkyProgram.at(index, SkyProgram.COLOR_1));
            color2 = slice(block, SkyProgram.at(index, SkyProgram.COLOR_2));
            lab1 = slice(block, SkyProgram.at(index, SkyProgram.LAB_1));
            lab2 = slice(block, SkyProgram.at(index, SkyProgram.LAB_2));
            alpha1 = block[SkyProgram.at(index, SkyProgram.ALPHA_1)];
            alpha2 = block[SkyProgram.at(index, SkyProgram.ALPHA_2)];
            amount = block[SkyProgram.at(index, SkyProgram.AMOUNT)];
            height = block[SkyProgram.at(index, SkyProgram.HEIGHT)];
            tilt = block[SkyProgram.at(index, SkyProgram.TILT)];
            dust = block[SkyProgram.at(index, SkyProgram.DUST)];
            jets = block[SkyProgram.at(index, SkyProgram.JETS)];
            boolean galaxy = layer.kind() == SkyLayer.Kind.GALAXY;
            detail = galaxy ? block[SkyProgram.at(index, SkyProgram.DETAIL)] : 0;
            motion = galaxy ? block[SkyProgram.at(index, SkyProgram.MOTION)] : 0;
            for (int c = 0; c < 3; c++) color3[c] = block[SkyProgram.at(index, SkyProgram.COLOR_3 + c * 4)];
            seed = (int) block[SkyProgram.at(index, SkyProgram.SEED)];
            termSlot = (int) block[SkyProgram.at(index, SkyProgram.TERM_SLOT)];
            float[] up = new float[3];
            local(0, 1, 0, up);
            float[] axis = new float[3];
            for (int c = 0; c < 3; c++) axis[c] = block[SkyProgram.at(index, SkyProgram.TRAIL_AXIS + c * 4)];
            // The bake is a still sky, so the stars twinkle as at time 0.
            stars = layer.kind() == SkyLayer.Kind.STARS
                    ? new SkyStars(amount, height * 2, scale, seed, block[SkyProgram.at(index, SkyProgram.TWINKLE)], 0, up,
                            block[SkyProgram.at(index, SkyProgram.TRAIL)], block[SkyProgram.at(index, SkyProgram.TRAIL_LIGHT)], axis)
                    : null;
        }

        private static float[] slice(float[] block, int from) {
            float[] out = new float[3];
            System.arraycopy(block, from, out, 0, 3);
            return out;
        }

        /** The layer frame direction of a sky direction: the transposed rotation times it. */
        void local(float x, float y, float z, float[] out) {
            out[0] = r[0] * x + r[3] * y + r[6] * z;
            out[1] = r[1] * x + r[4] * y + r[7] * z;
            out[2] = r[2] * x + r[5] * y + r[8] * z;
        }

        /** The sky direction of a layer frame direction. */
        void world(float x, float y, float z, float[] out) {
            out[0] = r[0] * x + r[1] * y + r[2] * z;
            out[1] = r[3] * x + r[4] * y + r[5] * z;
            out[2] = r[6] * x + r[7] * y + r[8] * z;
        }

        /** The OKLab mix of the two colors at {@code t}, as linear RGB and alpha into {@code out}. */
        void mix(float t, float[] out) {
            t = clamp01(t);
            Oklab.toLinear(lab1[0] + (lab2[0] - lab1[0]) * t, lab1[1] + (lab2[1] - lab1[1]) * t, lab1[2] + (lab2[2] - lab1[2]) * t, out);
            out[3] = alpha1 + (alpha2 - alpha1) * t;
        }
    }

    /** The color and alpha of a generator in a direction of the layer frame. */
    private void generate(Layer l, float[] p, float[] out) {
        switch (l.layer.kind()) {
            case GRADIENT: gradient(l, p, out); break;
            case GLOW: glow(l, p, out); break;
            case NOISE: noise(l, p, out); break;
            case FRACTAL: fractal(l, p, out); break;
            // The bake is a still sky, so the galaxy is as at time 0.
            case GALAXY: galaxy(l, p, texel, 0, out); break;
            case STARS: stars(l, p, out); break;
            case DEEP_FIELD: DeepField.sample(l.block, l.index, p, texel, out); break;
            // The bake is a still sky that does not turn: it draws the aurora at clock 0, and only the sky shader draws the meteors.
            case AURORA: Aurora.sample(l.block, l.index, p, texel, out); break;
            default: out[3] = 0;
        }
    }

    /** COLOR_1 low and COLOR_2 high, with the middle at HEIGHT of the way up and AMOUNT of softness. */
    private static void gradient(Layer l, float[] p, float[] out) {
        float t = (float) (Math.asin(clamp(p[1], -1, 1)) / (Math.PI / 2));
        float soft = 0.03f + l.amount * 0.9f;
        l.mix(smooth(l.height - soft, l.height + soft, t), out);
    }

    /** A band of COLOR_1 along the horizon of the layer, strongest at its center and AMOUNT wide around the sky. */
    private static void glow(Layer l, float[] p, float[] out) {
        double el = Math.toDegrees(Math.asin(clamp(p[1], -1, 1)));
        double band = Math.max(1, l.height * 60);
        double v = el >= 0 ? el / band : -el / (band * 0.35);
        double across = Math.exp(-v * v);
        double along = 1;
        if (l.amount < 1) {
            double az = Math.atan2(p[0], -p[2]);
            double spread = 0.15 + l.amount * Math.PI;
            along = Math.exp(-(az / spread) * (az / spread));
        }
        System.arraycopy(l.color1, 0, out, 0, 3);
        out[3] = (float) (across * along) * l.alpha1;
    }

    /**
     * Writes one row of the field image of a cloud or fractal layer, the values of {@code albedoCloudsField} or
     * {@code albedoFractalField} at the texel centers, which the sky shader reads with linear filters in place of that noise.
     */
    private void fieldRow(Layer l, int ty) {
        double el = Math.toRadians(SkyImage.TOP - (ty + 0.5) / termH * RANGE);
        float cos = (float) Math.cos(el), sin = (float) Math.sin(el);
        float[] local = new float[3], f = new float[4];
        int base = (l.termSlot * termH + ty) * termW * 4;
        for (int tx = 0; tx < termW; tx++) {
            double lon = (tx + 0.5) / termW * 2 * Math.PI;
            l.local(cos * (float) Math.sin(lon), sin, -cos * (float) Math.cos(lon), local);
            field(l, local, f);
            for (int c = 0; c < 4; c++) terms[base + tx * 4 + c] = signedHalf(f[c]);
        }
    }

    /** The smooth values of a cloud layer (its value and its pulse noise) or a fractal layer (its bend and its pulse noise). */
    private static void field(Layer l, float[] p, float[] out) {
        float pulse = l.block[SkyProgram.at(l.index, SkyProgram.PULSE)] > 0 ? pulseNoise(l.scale, l.seed, p) : 0;
        out[3] = 0;
        if (l.layer.kind() == SkyLayer.Kind.NOISE) {
            float f = 2.5f / l.scale;
            // The fine octaves are too small for the texels, so the shader adds them for each pixel.
            out[0] = Noise.fbmPart(p[0] * f, p[1] * f, p[2] * f, l.seed, 0, CLOUD_FIELD_OCTAVES, CLOUD_OCTAVES) * 0.5f + 0.5f;
            out[1] = pulse;
            out[2] = 0;
            return;
        }
        float g = 1.6f / l.scale;
        float x = p[0] * g, y = p[1] * g, z = p[2] * g;
        out[0] = Noise.fbm(x + 5.2f, y + 1.3f, z + 2.8f, l.seed + 11, 4);
        out[1] = Noise.fbm(x + 1.7f, y + 9.2f, z + 4.4f, l.seed + 23, 4);
        out[2] = Noise.fbm(x + 8.3f, y + 2.8f, z + 7.1f, l.seed + 37, 4);
        out[3] = pulse;
    }

    /** The slow noise that sets the regions of a pulse and their phases. */
    private static float pulseNoise(float scale, int seed, float[] p) {
        float f = 2.2f / scale;
        return Noise.noise(p[0] * f, p[1] * f, p[2] * f, seed + 71);
    }

    /** Clouds of fractal noise from COLOR_1 to COLOR_2, where AMOUNT is the part of the sky they cover. */
    private static void noise(Layer l, float[] p, float[] out) {
        float f = 2.5f / l.scale;
        float v = Noise.fbm(p[0] * f, p[1] * f, p[2] * f, l.seed, CLOUD_OCTAVES) * 0.5f + 0.5f;
        l.mix(v, out);
        float edge = 1 - l.amount * 1.2f;
        out[3] *= smooth(edge - 0.12f, edge + 0.12f, v);
        pulse(l, p, v, out);
    }

    /** Multiplies the color of a cloud layer by its pulse, where the bake is a still sky at time 0. */
    private static void pulse(Layer l, float[] p, float v, float[] out) {
        brighten(pulse(l.block[SkyProgram.at(l.index, SkyProgram.PULSE)], l.scale, l.seed, p, v, 0), out);
    }

    /**
     * The factor of the color of a cloud layer that gets brighter and darker in some regions, each at its own phase, with
     * the same steps as {@code albedoPulse} in the shader.
     *
     * @param amount the pulse from 0 to 1
     * @param p the direction in the layer frame
     * @param v the cloud value from 0 to 1, high at the bright knots, which pulse more
     * @param time the seconds of the sky clock
     */
    static float pulse(float amount, float scale, int seed, float[] p, float v, float time) {
        if (amount <= 0) return 1;
        float n = pulseNoise(scale, seed, p);
        float region = smooth(-0.35f, 0f, n);
        if (region <= 0) return 1;
        // One slow noise sets the regions, their phases and the weights of the three waves, so that each region has its own rhythm.
        float phase = 1.5f * n, m = 0.5f + 0.5f * (float) Math.sin(9 * n);
        float wave = (1 - m) * (1 - m) * wave(time / PULSE_PERIODS[0] + phase) + 2 * m * (1 - m) * wave(time / PULSE_PERIODS[1] + phase * 1.3f)
                + m * m * wave(time / PULSE_PERIODS[2] + phase * 1.6f);
        // The square of the amount gives fine steps at a low pulse and a very strong pulse at 100 percent.
        return (float) Math.exp(amount * amount * PULSE_LIGHT * region * wave * (0.4f + 0.6f * v));
    }

    /**
     * Multiplies a linear color by a factor, where a color that gets brighter goes smoothly up to a peak channel of 1, so
     * that it keeps its hue, with the same steps as {@code albedoBrighten} in the shader.
     */
    static void brighten(float k, float[] rgb) {
        float peak = Math.max(rgb[0], Math.max(rgb[1], rgb[2]));
        if (k > 1 && peak > 0) {
            float room = Math.max(1 - peak, 1e-4f);
            k = (peak + room * (1 - (float) Math.exp(-peak * (k - 1) / room))) / peak;
        }
        for (int c = 0; c < 3; c++) rgb[c] *= k;
    }

    /** A sine wave from -1 to 1 at a number of turns, where only the part after the point counts, so that a large time keeps its precision. */
    static float wave(float turns) {
        return (float) Math.sin(2 * Math.PI * (turns - Math.floor(turns)));
    }

    /** Thin bright lines of ridged noise, bent by more noise, where AMOUNT is how much they bend. */
    private static void fractal(Layer l, float[] p, float[] out) {
        float f = 1.6f / l.scale;
        float x = p[0] * f, y = p[1] * f, z = p[2] * f;
        float warp = l.amount * 1.6f;
        float qx = Noise.fbm(x + 5.2f, y + 1.3f, z + 2.8f, l.seed + 11, 4);
        float qy = Noise.fbm(x + 1.7f, y + 9.2f, z + 4.4f, l.seed + 23, 4);
        float qz = Noise.fbm(x + 8.3f, y + 2.8f, z + 7.1f, l.seed + 37, 4);
        float v = Noise.ridged(x + warp * qx, y + warp * qy, z + warp * qz, l.seed, 6);
        v = v * v;
        l.mix(v, out);
        out[3] *= clamp01(v * 1.6f);
        pulse(l, p, v, out);
    }

    /**
     * An active galaxy at the layer center, modeled on Centaurus A and Hercules A: a COLOR_2 bulge in a large halo, a disc with
     * COLOR_1 arms at TILT and a lane of dust that absorbs their light, and two COLOR_3 jets along the axis of the disc that end
     * in red lobes with rings, where DETAIL adds the fine parts and MOTION moves the jets, the nucleus and the lobes with the time.
     *
     * @param texel the width of one pixel in radians
     * @param time the seconds of the sky clock
     */
    static void galaxy(Layer l, float[] p, float texel, float time, float[] out) {
        out[3] = 0;
        float zz = -p[2];
        if (zz < 0.2f) return;
        // The plane of the image in galaxy units, where 1 is about the radius of the disc.
        float size = 0.22f * l.scale;
        float x = p[0] / zz / size, y = p[1] / zz / size;
        float rr = (float) Math.sqrt(x * x + y * y);
        if (rr > GALAXY_REACH) return;
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

    /** A bell curve of height 1 at 0. */
    private static float gauss(float v) {
        return (float) Math.exp(-v * v);
    }

    /** The turns at a time of a motion that makes a whole number of turns in the hour of the sky clock, {@code perHour} of them at a motion of 1. */
    static float turns(float perHour, float motion, float time) {
        return (float) Math.floor(perHour * motion + 0.5f) * time / 3600f;
    }

    /**
     * Noise along a line at {@code u} that moves with the time: the line goes around a circle of radius {@link #LOOP}, and
     * one turn moves it once around, so that it comes back to its start.
     */
    static float loop(float u, float turns, float lane, int seed) {
        double a = u / LOOP - 2 * Math.PI * (turns - Math.floor(turns));
        return Noise.noise(LOOP * (float) Math.cos(a), LOOP * (float) Math.sin(a), lane, seed);
    }

    /** Adds the globular clusters of a galaxy: small soft points around its halo, more of them near the bulge. */
    private static void clusters(Layer l, float x, float y, float rr, float fp, float detail, float[] light) {
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
    private static void stream(Layer l, float x, float y, float fp, float detail, float[] light) {
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
    private static void supernova(Layer l, float x, float y, float qs, float fp, float motion, float time, float[] light) {
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

    /** Stars with the brightness spread and the colors of real stars, where the footprint of a texel widens only their thin parts. */
    private void stars(Layer l, float[] p, float[] out) {
        l.stars.shade(p, texel, l.color1, l.alpha1, out);
    }

    /** Runs a filter, which writes its result for all of {@code img} into {@code tmp}. */
    private boolean filter(Layer l) {
        boolean ok;
        switch (l.layer.kind()) {
            case BLUR:
                System.arraycopy(img, 0, tmp, 0, img.length);
                ok = blur(tmp, l.amount * 10f);
                break;
            case BLOOM: return bloom(l);
            case PIXELATE: ok = rows.run(used, y -> pixelateRow(l, y)); break;
            case CRYSTALLIZE: ok = rows.run(used, y -> crystallizeRow(l, y)); break;
            case POSTERIZE: ok = rows.run(used, y -> posterizeRow(l, y)); break;
            case HUE_SHIFT: ok = rows.run(used, y -> hueRow(l, y)); break;
            case SWIRL: ok = rows.run(used, y -> swirlRow(l, y)); break;
            case VIGNETTE: ok = rows.run(used, y -> vignetteRow(l, y)); break;
            default:
                System.arraycopy(img, 0, tmp, 0, img.length);
                ok = true;
        }
        // The term image of a filter other than a bloom is its full result.
        return ok && (l.termSlot < 0 || rows.run(termH, ty -> termRow(l.termSlot, ty, 1f)));
    }

    /** Three box passes in each direction, which is close to a Gaussian, with the width across a row wider toward the zenith. */
    private boolean blur(float[] buf, float degrees) {
        if (degrees <= 0) return true;
        return rows.run(used, y -> {
            int radius = Math.min(w / 2 - 1, Math.round(degrees / 360f * w / Math.max(0.05f, cosEl[y])));
            float[] line = new float[w * 4], out = new float[w * 4];
            System.arraycopy(buf, y * w * 4, line, 0, w * 4);
            for (int pass = 0; pass < 3; pass++) {
                boxWrap(line, out, w, radius);
                float[] t = line;
                line = out;
                out = t;
            }
            System.arraycopy(line, 0, buf, y * w * 4, w * 4);
        }) && rows.run(w, x -> {
            int radius = Math.round(degrees / RANGE * h);
            float[] line = new float[used * 4], out = new float[used * 4];
            for (int y = 0; y < used; y++) System.arraycopy(buf, (y * w + x) * 4, line, y * 4, 4);
            for (int pass = 0; pass < 3; pass++) {
                boxClamp(line, out, used, radius);
                float[] t = line;
                line = out;
                out = t;
            }
            for (int y = 0; y < used; y++) System.arraycopy(line, y * 4, buf, (y * w + x) * 4, 4);
        });
    }

    /** A running-sum box filter of RGBA values over a ring of {@code n}. */
    private static void boxWrap(float[] in, float[] out, int n, int radius) {
        if (radius <= 0) {
            System.arraycopy(in, 0, out, 0, n * 4);
            return;
        }
        float inv = 1f / (2 * radius + 1);
        for (int c = 0; c < 4; c++) {
            float sum = 0;
            for (int k = -radius; k <= radius; k++) sum += in[Math.floorMod(k, n) * 4 + c];
            for (int i = 0; i < n; i++) {
                out[i * 4 + c] = sum * inv;
                sum += in[Math.floorMod(i + radius + 1, n) * 4 + c] - in[Math.floorMod(i - radius, n) * 4 + c];
            }
        }
    }

    /** A running-sum box filter of RGBA values over a line of {@code n} whose ends repeat. */
    private static void boxClamp(float[] in, float[] out, int n, int radius) {
        if (radius <= 0) {
            System.arraycopy(in, 0, out, 0, n * 4);
            return;
        }
        float inv = 1f / (2 * radius + 1);
        for (int c = 0; c < 4; c++) {
            float sum = 0;
            for (int k = -radius; k <= radius; k++) sum += in[clampIndex(k, n) * 4 + c];
            for (int i = 0; i < n; i++) {
                out[i * 4 + c] = sum * inv;
                sum += in[clampIndex(i + radius + 1, n) * 4 + c] - in[clampIndex(i - radius, n) * 4 + c];
            }
        }
    }

    private static int clampIndex(int i, int n) {
        return i < 0 ? 0 : i >= n ? n - 1 : i;
    }

    /** Adds a wide blur of the parts lighter than HEIGHT, AMOUNT strong. */
    private boolean bloom(Layer l) {
        float threshold = l.height;
        boolean ok = rows.run(used, y -> {
            for (int x = 0; x < w; x++) {
                int i = (y * w + x) * 4;
                float lightness = Oklab.lightness(clamp01(img[i]), clamp01(img[i + 1]), clamp01(img[i + 2]));
                float k = clamp01((lightness - threshold) / Math.max(0.05f, 1 - threshold));
                for (int c = 0; c < 3; c++) tmp[i + c] = img[i + c] * k;
                tmp[i + 3] = 1;
            }
        });
        if (!ok || !blur(tmp, 6f)) return false;
        float strength = l.amount * 2.5f;
        if (l.termSlot >= 0 && !rows.run(termH, ty -> termRow(l.termSlot, ty, strength))) return false;
        return rows.run(used, y -> {
            for (int x = 0; x < w; x++) {
                int i = (y * w + x) * 4;
                for (int c = 0; c < 3; c++) tmp[i + c] = img[i + c] + tmp[i + c] * strength;
                tmp[i + 3] = 1;
            }
        });
    }

    /** Writes one row of a term image: the mean of 2 x 2 texels of {@code tmp} times {@code k}, where the unfilled rows are 0. */
    private void termRow(int slot, int ty, float k) {
        int base = (slot * termH + ty) * termW * 4;
        for (int tx = 0; tx < termW; tx++) {
            for (int c = 0; c < 3; c++) {
                float sum = 0;
                for (int y = 2 * ty; y <= 2 * ty + 1; y++) {
                    if (y >= used) continue;
                    for (int x = 2 * tx; x <= 2 * tx + 1; x++) sum += tmp[(y * w + Math.min(w - 1, x)) * 4 + c];
                }
                terms[base + tx * 4 + c] = half(sum / 4 * k);
            }
            terms[base + tx * 4 + 3] = half(1);
        }
    }

    /** The IEEE half float bits of a value that can be negative, limited to the range from -65504 to 65504. */
    static short signedHalf(float v) {
        return v < 0 ? (short) (half(-v) | 0x8000) : half(v);
    }

    /** The IEEE half float bits of a value, which is limited to the range from 0 to 65504 and rounded to the nearest half float. */
    static short half(float v) {
        // This test is also false for NaN, which then gives 0 as a negative value does.
        if (!(v > 0)) return 0;
        int bits = Float.floatToIntBits(Math.min(65504f, v));
        int exp = (bits >>> 23) - 127 + 15;
        int mantissa = bits & 0x7FFFFF;
        if (exp > 0) return (short) (((exp << 10) | (mantissa >> 13)) + ((mantissa >> 12) & 1));
        // A value below the smallest normal half float is a subnormal: its mantissa with the hidden bit, moved right.
        if (exp < -10) return 0;
        int shift = 14 - exp;
        return (short) (((mantissa | 0x800000) + (1 << (shift - 1))) >> shift);
    }

    /** Cells of equal size in degrees in the layer frame, in rows whose cell count follows the width of the sky at their height. */
    private void pixelateRow(Layer l, int y) {
        float size = (float) Math.toRadians(3f * l.scale);
        float[] p = new float[3], d = new float[3], s = new float[4];
        for (int x = 0; x < w; x++) {
            dir(x, y, d);
            l.local(d[0], d[1], d[2], p);
            double el = Math.asin(clamp(p[1], -1, 1));
            int band = (int) Math.floor((el + Math.PI / 2) / size);
            double center = -Math.PI / 2 + (band + 0.5) * size;
            int cells = Math.max(1, (int) Math.round(2 * Math.PI * Math.cos(center) / size));
            double lon = Math.atan2(p[0], -p[2]);
            double cellLon = (Math.floor((lon + Math.PI) / (2 * Math.PI) * cells) + 0.5) / cells * 2 * Math.PI - Math.PI;
            float cos = (float) Math.cos(center);
            l.world(cos * (float) Math.sin(cellLon), (float) Math.sin(center), -cos * (float) Math.cos(cellLon), d);
            sample(img, d[0], d[1], d[2], s);
            System.arraycopy(s, 0, tmp, (y * w + x) * 4, 4);
        }
    }

    /** Each pixel takes the color at the nearest point of a jittered 3D grid, which makes cells like crystals. */
    private void crystallizeRow(Layer l, int y) {
        float n = (float) (1 / Math.toRadians(4f * l.scale));
        float[] p = new float[3], d = new float[3], s = new float[4];
        for (int x = 0; x < w; x++) {
            dir(x, y, d);
            l.local(d[0], d[1], d[2], p);
            float px = p[0] * n, py = p[1] * n, pz = p[2] * n;
            int cx = (int) Math.floor(px), cy = (int) Math.floor(py), cz = (int) Math.floor(pz);
            float best = Float.MAX_VALUE, bx = px, by = py, bz = pz;
            for (int i = -1; i <= 1; i++) {
                for (int j = -1; j <= 1; j++) {
                    for (int k = -1; k <= 1; k++) {
                        int hx = cx + i, hy = cy + j, hz = cz + k;
                        float fx = hx + Noise.unit(Noise.hash(hx, hy, hz, l.seed + 11));
                        float fy = hy + Noise.unit(Noise.hash(hx, hy, hz, l.seed + 22));
                        float fz = hz + Noise.unit(Noise.hash(hx, hy, hz, l.seed + 33));
                        float dd = (px - fx) * (px - fx) + (py - fy) * (py - fy) + (pz - fz) * (pz - fz);
                        if (dd < best) {
                            best = dd;
                            bx = fx;
                            by = fy;
                            bz = fz;
                        }
                    }
                }
            }
            float len = (float) Math.sqrt(bx * bx + by * by + bz * bz);
            l.world(bx / len, by / len, bz / len, d);
            sample(img, d[0], d[1], d[2], s);
            System.arraycopy(s, 0, tmp, (y * w + x) * 4, 4);
        }
    }

    /** Fewer color steps: from 2 to 16 for each channel, as AMOUNT sets. */
    private void posterizeRow(Layer l, int y) {
        int levels = 2 + Math.round(l.amount * 14);
        for (int x = 0; x < w; x++) {
            int i = (y * w + x) * 4;
            for (int c = 0; c < 3; c++) {
                float s = Oklab.toSrgb(img[i + c]) / 255f;
                float q = Math.round(s * (levels - 1)) / (float) (levels - 1);
                tmp[i + c] = Oklab.toLinear(Math.round(q * 255));
            }
            tmp[i + 3] = img[i + 3];
        }
    }

    /** Turns the hue by AMOUNT of a full turn, in OKLab so that the lightness stays. */
    private void hueRow(Layer l, int y) {
        double angle = l.amount * 2 * Math.PI;
        float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
        float[] lab = new float[3], rgb = new float[3];
        for (int x = 0; x < w; x++) {
            int i = (y * w + x) * 4;
            Oklab.fromLinear(Math.max(0, img[i]), Math.max(0, img[i + 1]), Math.max(0, img[i + 2]), lab);
            Oklab.toLinear(lab[0], lab[1] * cos - lab[2] * sin, lab[1] * sin + lab[2] * cos, rgb);
            tmp[i] = Math.max(0, rgb[0]);
            tmp[i + 1] = Math.max(0, rgb[1]);
            tmp[i + 2] = Math.max(0, rgb[2]);
            tmp[i + 3] = img[i + 3];
        }
    }

    /** Turns the sky around the layer center, most at the center and none at the edge, which is 60 degrees times the size away. */
    private void swirlRow(Layer l, int y) {
        float[] c = new float[3], d = new float[3], s = new float[4];
        l.world(0, 0, -1, c);
        double radius = Math.toRadians(60 * l.scale), twist = l.amount * 3 * Math.PI;
        for (int x = 0; x < w; x++) {
            dir(x, y, d);
            double cos = clamp(c[0] * d[0] + c[1] * d[1] + c[2] * d[2], -1, 1);
            double dist = Math.acos(cos);
            if (dist < radius) {
                double t = 1 - dist / radius, a = twist * t * t;
                rotate(d, c, (float) a);
            }
            sample(img, d[0], d[1], d[2], s);
            System.arraycopy(s, 0, tmp, (y * w + x) * 4, 4);
        }
    }

    /** Mixes COLOR_1 in away from the layer center, from 30 percent of 90 degrees times the size out to all of it. */
    private void vignetteRow(Layer l, int y) {
        float[] c = new float[3], d = new float[3];
        l.world(0, 0, -1, c);
        double radius = Math.toRadians(90 * l.scale);
        for (int x = 0; x < w; x++) {
            dir(x, y, d);
            double dist = Math.acos(clamp(c[0] * d[0] + c[1] * d[1] + c[2] * d[2], -1, 1));
            float k = smooth((float) (radius * 0.3), (float) radius, (float) dist) * l.amount * l.alpha1;
            int i = (y * w + x) * 4;
            for (int ch = 0; ch < 3; ch++) tmp[i + ch] = img[i + ch] + (l.color1[ch] - img[i + ch]) * k;
            tmp[i + 3] = img[i + 3];
        }
    }

    private void dir(int x, int y, float[] out) {
        out[0] = cosEl[y] * sinLon[x];
        out[1] = sinEl[y];
        out[2] = -cosEl[y] * cosLon[x];
    }

    /** Rotates {@code v} in place about the unit axis {@code k} by {@code angle} radians. */
    private static void rotate(float[] v, float[] k, float angle) {
        float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
        float dot = k[0] * v[0] + k[1] * v[1] + k[2] * v[2];
        float cx = k[1] * v[2] - k[2] * v[1], cy = k[2] * v[0] - k[0] * v[2], cz = k[0] * v[1] - k[1] * v[0];
        float x = v[0] * cos + cx * sin + k[0] * dot * (1 - cos);
        float y = v[1] * cos + cy * sin + k[1] * dot * (1 - cos);
        float z = v[2] * cos + cz * sin + k[2] * dot * (1 - cos);
        v[0] = x;
        v[1] = y;
        v[2] = z;
    }

    /** The bilinear RGBA of an image in a sky direction, where the columns wrap and the rows stop at the first and the last used row. */
    private void sample(float[] src, float x, float y, float z, float[] out) {
        double lon = Math.atan2(x, -z);
        double el = Math.toDegrees(Math.asin(clamp(y, -1, 1)));
        double fx = (lon / (2 * Math.PI)) * w - 0.5;
        double fy = (SkyImage.TOP - el) / RANGE * h - 0.5;
        fy = Math.max(0, Math.min(used - 1, fy));
        int x0 = (int) Math.floor(fx), y0 = (int) Math.floor(fy);
        float tx = (float) (fx - x0), ty = (float) (fy - y0);
        int xa = Math.floorMod(x0, w), xb = Math.floorMod(x0 + 1, w), ya = y0, yb = Math.min(used - 1, y0 + 1);
        for (int c = 0; c < 4; c++) {
            float top = src[(ya * w + xa) * 4 + c] * (1 - tx) + src[(ya * w + xb) * 4 + c] * tx;
            float bottom = src[(yb * w + xa) * 4 + c] * (1 - tx) + src[(yb * w + xb) * 4 + c] * tx;
            out[c] = top * (1 - ty) + bottom * ty;
        }
    }

    /** The smoothstep of {@code t} from {@code a} to {@code b}, the same as {@code albedoSmooth} in the sky shader. */
    static float smooth(float a, float b, float t) {
        float x = clamp01((t - a) / (b - a));
        return x * x * (3 - 2 * x);
    }

    private static float clamp01(float v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : v > hi ? hi : v;
    }
}
