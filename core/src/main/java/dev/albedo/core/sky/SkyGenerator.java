package dev.albedo.core.sky;

import dev.albedo.core.config.SkyDesign;
import dev.albedo.core.config.SkyLayer;
import dev.albedo.core.config.SkyMask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

import static dev.albedo.core.sky.SkyMath.clamp;
import static dev.albedo.core.sky.SkyMath.clamp01;
import static dev.albedo.core.sky.SkyMath.smooth;
import static dev.albedo.core.sky.SkyMath.wave;

/**
 * Bakes a {@link SkyDesign} into a {@link SkyImage} in one pass from the bottom layer up, in linear light, where each
 * generator works on directions (no seam, no pinch at the zenith) and each filter size is in degrees, as a still sky at
 * time 0 that does not turn.
 */
public final class SkyGenerator {
    /** Runs an index from 0 to count - 1 for each row or column of a pass, on one or more threads. */
    interface Rows {
        /** @return false when the bake is out of date and stops */
        boolean run(int count, IntConsumer row);
    }

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
    private final SkyGrid grid;
    private final float[] img;
    /** The result of each filter, from the first filter of the bake on. */
    private float[] tmp;
    private SkyFilters filters;
    /** The term images as half float RGBA, see {@link SkyImage#terms}. */
    private short[] terms;

    private SkyGenerator(SkyDesign design, int w, int h, Rows rows) {
        this.design = design;
        this.w = w;
        this.h = h;
        this.rows = rows;
        grid = new SkyGrid(design, w, h);
        img = new float[w * h * 4];
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
        terms = new short[grid.termW * grid.termH * layout.terms() * 4];
        for (int i = 0; i < layers.size(); i++) {
            SkyLayer layer = layers.get(i);
            Layer ctx = new Layer(layer, block, i);
            int lightKey = planeKey(layer.id(), SkyMask.Mode.LIGHT), darkKey = planeKey(layer.id(), SkyMask.Mode.DARK);
            float[] light = lastUse.containsKey(lightKey) ? new float[w * h] : null;
            float[] dark = lastUse.containsKey(darkKey) ? new float[w * h] : null;
            float[] mask = layer.mask().isNone() ? null : planes.get(planeKey(layer.mask()));
            boolean show = layer.output() == SkyLayer.Output.SHOW;
            boolean ok;
            if (layer.kind().role() == SkyLayer.Role.GENERATOR) {
                ok = rows.run(grid.used, y -> generatorRow(ctx, y, show, mask, light, dark));
                if (ok && ctx.termSlot >= 0) ok = rows.run(grid.termH, ty -> fieldRow(ctx, ty));
            } else {
                if (filters == null) {
                    tmp = new float[w * h * 4];
                    filters = new SkyFilters(grid, img, tmp, terms, rows);
                }
                ok = filters.run(ctx) && rows.run(grid.used, y -> blendRow(ctx, y, tmp, show, mask, light, dark));
            }
            if (!ok) return null;
            if (light != null) planes.put(lightKey, light);
            if (dark != null) planes.put(darkKey, dark);
            for (Map.Entry<Integer, Integer> e : lastUse.entrySet()) if (e.getValue() == i) planes.remove(e.getKey());
        }
        tmp = null;
        filters = null;
        float[] ring = ring();
        if (design.drawnExtent() == SkyDesign.Extent.HORIZON && !rows.run(h, y -> horizonRow(y, ring))) return null;
        int[] argb = new int[w * h];
        if (!rows.run(h, y -> encodeRow(y, argb))) return null;
        return new SkyImage(argb, w, h, design, ring, SkyProgram.of(design, ring), grid.termW, grid.termH, terms);
    }

    /** Mixes the sky below the horizon into the horizon color (the fog color) of each direction, for a sky that stops at the horizon. */
    private void horizonRow(int y, float[] ring) {
        float k = smooth(0, FADE, -grid.elevation[y]);
        if (k <= 0) return;
        float[] c = new float[3];
        for (int x = 0; x < w; x++) {
            SkyImage.ringAt(ring, (x + 0.5) / w * 360, c);
            int i = (y * w + x) * 4;
            for (int ch = 0; ch < 3; ch++) img[i + ch] += (c[ch] - img[i + ch]) * k;
        }
    }

    private static int planeKey(SkyMask m) {
        return planeKey(m.layer(), m.mode());
    }

    /** The key of the light or dark mask plane of a layer. */
    private static int planeKey(int layerId, SkyMask.Mode mode) {
        return layerId * 2 + (mode == SkyMask.Mode.DARK ? 1 : 0);
    }

    private void generatorRow(Layer l, int y, boolean show, float[] mask, float[] light, float[] dark) {
        float[] s = new float[4], d = new float[3], local = new float[3];
        for (int x = 0; x < w; x++) {
            grid.dir(x, y, d);
            l.local(d[0], d[1], d[2], local);
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

    /** The {@link SkyImage} ring of the baked pixels, softened across near sectors. */
    private float[] ring() {
        float[] ring = new float[SkyImage.RING * 3];
        int[] count = new int[SkyImage.RING];
        for (int y = 0; y < h; y++) {
            if (grid.elevation[y] < 0 || grid.elevation[y] > 6) continue;
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
            opacity = block[SkyProgram.index(index, SkyProgram.OPACITY)];
            for (int row = 0; row < 3; row++) {
                for (int c = 0; c < 3; c++) r[c * 3 + row] = block[SkyProgram.index(index, SkyProgram.LOCAL + row * 4 + c)];
            }
            scale = block[SkyProgram.index(index, SkyProgram.SCALE)];
            color1 = slice(block, SkyProgram.index(index, SkyProgram.COLOR_1));
            color2 = slice(block, SkyProgram.index(index, SkyProgram.COLOR_2));
            lab1 = slice(block, SkyProgram.index(index, SkyProgram.LAB_1));
            lab2 = slice(block, SkyProgram.index(index, SkyProgram.LAB_2));
            alpha1 = block[SkyProgram.index(index, SkyProgram.ALPHA_1)];
            alpha2 = block[SkyProgram.index(index, SkyProgram.ALPHA_2)];
            amount = block[SkyProgram.index(index, SkyProgram.AMOUNT)];
            height = block[SkyProgram.index(index, SkyProgram.HEIGHT)];
            tilt = block[SkyProgram.index(index, SkyProgram.TILT)];
            dust = block[SkyProgram.index(index, SkyProgram.DUST)];
            jets = block[SkyProgram.index(index, SkyProgram.JETS)];
            boolean galaxy = layer.kind() == SkyLayer.Kind.GALAXY;
            detail = galaxy ? block[SkyProgram.index(index, SkyProgram.DETAIL)] : 0;
            motion = galaxy ? block[SkyProgram.index(index, SkyProgram.MOTION)] : 0;
            for (int c = 0; c < 3; c++) color3[c] = block[SkyProgram.index(index, SkyProgram.COLOR_3 + c * 4)];
            seed = (int) block[SkyProgram.index(index, SkyProgram.SEED)];
            termSlot = (int) block[SkyProgram.index(index, SkyProgram.TERM_SLOT)];
            float[] up = new float[3];
            local(0, 1, 0, up);
            stars = layer.kind() == SkyLayer.Kind.STARS ? SkyStars.of(block, index, up, 0) : null;
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
            case GALAXY: Galaxy.sample(l, p, grid.texel, 0, out); break;
            case STARS: stars(l, p, out); break;
            case DEEP_FIELD: DeepField.sample(l.block, l.index, p, grid.texel, out); break;
            case AURORA: Aurora.sample(l.block, l.index, p, grid.texel, out); break;
            default: out[3] = 0; // only the sky shader draws the meteors
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
        int termW = grid.termW, termH = grid.termH;
        double el = Math.toRadians(SkyImage.TOP - (ty + 0.5) / termH * SkyGrid.RANGE);
        float cos = (float) Math.cos(el), sin = (float) Math.sin(el);
        float[] local = new float[3], f = new float[4];
        int base = (l.termSlot * termH + ty) * termW * 4;
        for (int tx = 0; tx < termW; tx++) {
            double lon = (tx + 0.5) / termW * 2 * Math.PI;
            l.local(cos * (float) Math.sin(lon), sin, -cos * (float) Math.cos(lon), local);
            field(l, local, f);
            for (int c = 0; c < 4; c++) terms[base + tx * 4 + c] = HalfFloat.encodeSigned(f[c]);
        }
    }

    /** The smooth values of a cloud layer (its value and its pulse noise) or a fractal layer (its bend and its pulse noise). */
    private static void field(Layer l, float[] p, float[] out) {
        float pulse = l.block[SkyProgram.index(l.index, SkyProgram.PULSE)] > 0 ? pulseNoise(l.scale, l.seed, p) : 0;
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

    /** Multiplies the color of a cloud layer by its pulse at time 0. */
    private static void pulse(Layer l, float[] p, float v, float[] out) {
        brighten(pulse(l.block[SkyProgram.index(l.index, SkyProgram.PULSE)], l.scale, l.seed, p, v, 0), out);
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

    /**
     * Splits linear light into a color and an alpha as {@code albedoLightAlpha} in the sky shader does, where the alpha carries
     * the light up to 1 and the color carries the rest, so that an added layer adds all of the light.
     *
     * @param empty the color where there is no light
     * @param alpha the factor of the alpha
     */
    static void lightAlpha(float[] light, float[] empty, float alpha, float[] out) {
        float a = Math.min(1f, Math.max(light[0], Math.max(light[1], light[2])));
        for (int c = 0; c < 3; c++) out[c] = a > 0 ? light[c] / a : empty[c];
        out[3] = a * alpha;
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

    private void stars(Layer l, float[] p, float[] out) {
        l.stars.shade(p, grid.texel, l.color1, l.alpha1, out);
    }
}
