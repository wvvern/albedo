package dev.albedo.core.sky;

import dev.albedo.core.config.SkyDesign;
import dev.albedo.core.config.SkyLayer;
import dev.albedo.core.config.SkyMask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
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
    /** The star grid density per radian at which the star sizes are set, between the Nebula and Galaxy counts. */
    private static final float STAR_CELLS = 36f;
    /** The elevation in degrees below which the sky goes smoothly into the horizon color, so that the fog meets it with no line. */
    private static final float HAZE = 3f;
    /** The number of horizon sectors on each side that soften a sector, about 28 degrees. */
    private static final int RING_SOFTEN = 5;

    private final SkyDesign design;
    private final int w, h;
    private final Rows rows;
    private final float[] sinLon, cosLon, sinEl, cosEl, elevation;
    /** The width of one texel at the horizon, in radians. */
    private final float texel;
    private final float[] img;
    private float[] tmp;

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
        texel = (float) (2 * Math.PI / w);
        img = new float[w * h * 4];
    }

    /** Bakes on the calling thread. */
    public static SkyImage bake(SkyDesign design, int w, int h) {
        return bake(design, w, h, serial(() -> false));
    }

    /** Bakes with {@code rows}, and returns null when the bake stops because it is out of date. */
    static SkyImage bake(SkyDesign design, int w, int h, Rows rows) {
        return new SkyGenerator(design, w, h, rows).run();
    }

    /** Runs all rows on the calling thread and checks {@code stale} before each row. */
    static Rows serial(BooleanSupplier stale) {
        return (count, row) -> {
            for (int i = 0; i < count; i++) {
                if (stale.getAsBoolean()) return false;
                row.accept(i);
            }
            return true;
        };
    }

    // ---------------------------------------------------------------- the pass over the layers

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
        for (int i = 0; i < layers.size(); i++) {
            SkyLayer layer = layers.get(i);
            Layer ctx = new Layer(layer);
            float[] light = lastUse.containsKey(layer.id() * 2) ? new float[w * h] : null;
            float[] dark = lastUse.containsKey(layer.id() * 2 + 1) ? new float[w * h] : null;
            float[] mask = layer.mask().isNone() ? null : planes.get(planeKey(layer.mask()));
            boolean show = layer.output() == SkyLayer.Output.SHOW;
            boolean ok;
            if (layer.kind().role() == SkyLayer.Role.GENERATOR) {
                ok = rows.run(h, y -> generatorRow(ctx, y, show, mask, light, dark));
            } else {
                if (tmp == null) tmp = new float[w * h * 4];
                ok = filter(ctx) && rows.run(h, y -> blendRow(ctx, y, tmp, show, mask, light, dark));
            }
            if (!ok) return null;
            if (light != null) planes.put(layer.id() * 2, light);
            if (dark != null) planes.put(layer.id() * 2 + 1, dark);
            for (Map.Entry<Integer, Integer> e : lastUse.entrySet()) if (e.getValue() == i) planes.remove(e.getKey());
        }
        tmp = null;
        float[] ring = ring();
        if (!rows.run(h, y -> horizonRow(y, ring))) return null;
        int[] argb = new int[w * h];
        if (!rows.run(h, y -> encodeRow(y, argb))) return null;
        return new SkyImage(argb, w, h, design, ring);
    }

    /** Mixes the sky near and below the horizon into the horizon color of each direction, which is also the fog color there. */
    private void horizonRow(int y, float[] ring) {
        float k = 1 - smooth(0, HAZE, elevation[y]);
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

    // ---------------------------------------------------------------- one layer's values

    /** What a layer needs for each pixel, computed once for each bake. */
    private static final class Layer {
        final SkyLayer layer;
        final float opacity;
        /** The rotation from the layer frame to the sky, row-major. */
        final float[] r = new float[9];
        final float scale;
        final float[] color1, color2, lab1, lab2;
        final float alpha1, alpha2, amount, height;
        final int seed;

        Layer(SkyLayer layer) {
            this.layer = layer;
            this.opacity = layer.opacity() / 100f;
            double[] rot = layer.transform().rotation();
            for (int i = 0; i < 9; i++) r[i] = (float) rot[i];
            scale = layer.transform().scale() / 100f;
            int c1 = layer.param(SkyLayer.Param.COLOR_1), c2 = layer.param(SkyLayer.Param.COLOR_2);
            color1 = Oklab.linear(c1);
            color2 = Oklab.linear(c2);
            alpha1 = (c1 >>> 24) / 255f;
            alpha2 = (c2 >>> 24) / 255f;
            lab1 = new float[3];
            lab2 = new float[3];
            Oklab.fromLinear(color1[0], color1[1], color1[2], lab1);
            Oklab.fromLinear(color2[0], color2[1], color2[2], lab2);
            amount = layer.param(SkyLayer.Param.AMOUNT) / 100f;
            height = layer.param(SkyLayer.Param.HEIGHT) / 100f;
            seed = layer.param(SkyLayer.Param.SEED);
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

    // ---------------------------------------------------------------- generators

    /** The color and alpha of a generator in a direction of the layer frame. */
    private void generate(Layer l, float[] p, float[] out) {
        switch (l.layer.kind()) {
            case GRADIENT: gradient(l, p, out); break;
            case GLOW: glow(l, p, out); break;
            case NOISE: noise(l, p, out); break;
            case FRACTAL: fractal(l, p, out); break;
            case GALAXY: galaxy(l, p, out); break;
            case STARS: stars(l, p, out); break;
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

    /** Clouds of fractal noise from COLOR_1 to COLOR_2, where AMOUNT is the part of the sky they cover. */
    private static void noise(Layer l, float[] p, float[] out) {
        float f = 2.5f / l.scale;
        float v = Noise.fbm(p[0] * f, p[1] * f, p[2] * f, l.seed, 6) * 0.5f + 0.5f;
        l.mix(v, out);
        float edge = 1 - l.amount * 1.2f;
        out[3] *= smooth(edge - 0.12f, edge + 0.12f, v);
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
    }

    /** A spiral galaxy at the layer center, with COLOR_1 arms around a COLOR_2 core, where AMOUNT turns the arms more. */
    private static void galaxy(Layer l, float[] p, float[] out) {
        out[3] = 0;
        if (p[2] >= -0.05f) return;
        float size = 0.45f * l.scale;
        float u = p[0] / -p[2] / size, v = p[1] / -p[2] / size / 0.5f;
        float r = (float) Math.sqrt(u * u + v * v);
        if (r > 2.5f) return;
        float theta = (float) Math.atan2(v, u);
        float twist = 2f + l.amount * 6f;
        float arm = (float) (0.5 + 0.5 * Math.cos(2 * (theta - twist * Math.log(r + 0.08))));
        arm = arm * arm * arm;
        float n = Noise.fbm(p[0] * 9, p[1] * 9, p[2] * 9, l.seed, 5) * 0.5f + 0.5f;
        float disk = (float) Math.exp(-r * 2.2) * arm * (0.4f + 1.2f * n);
        float core = (float) Math.exp(-(r / 0.16f) * (r / 0.16f));
        float haze = (float) Math.exp(-r * 3.5) * 0.35f;
        float coreWeight = clamp01(core * 1.5f + haze * 0.5f);
        l.mix(coreWeight, out);
        out[3] = clamp01(disk + core + haze) * Math.max(l.alpha1, l.alpha2);
    }

    /** Points in a jittered 3D grid on the layer sphere, where AMOUNT sets how many and the size sets how far apart and how large. */
    private void stars(Layer l, float[] p, float[] out) {
        float n = (10f + l.amount * 40f) / l.scale;
        float px = p[0] * n, py = p[1] * n, pz = p[2] * n;
        int cx = (int) Math.floor(px), cy = (int) Math.floor(py), cz = (int) Math.floor(pz);
        // The star radius in cells grows with the count, so that a star has the same size in degrees at each AMOUNT.
        float grow = n * l.scale / STAR_CELLS;
        // The smallest star is 1.5 texels across, so that it does not flicker between pixels.
        float minRadius = 1.5f * texel * n;
        float best = 0;
        float warm = 0;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                for (int k = -1; k <= 1; k++) {
                    int hx = cx + i, hy = cy + j, hz = cz + k;
                    int hash = Noise.hash(hx, hy, hz, l.seed * 31 + 7);
                    if (Noise.unit(hash) > 0.15f) continue;
                    float sx = hx + 0.15f + 0.7f * Noise.unit(Noise.hash(hx, hy, hz, l.seed + 101));
                    float sy = hy + 0.15f + 0.7f * Noise.unit(Noise.hash(hx, hy, hz, l.seed + 202));
                    float sz = hz + 0.15f + 0.7f * Noise.unit(Noise.hash(hx, hy, hz, l.seed + 303));
                    float len = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
                    if (len < 1e-3f) continue;
                    sx = sx / len * n;
                    sy = sy / len * n;
                    sz = sz / len * n;
                    float dx = px - sx, dy = py - sy, dz = pz - sz;
                    float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                    float size = Noise.unit(Noise.hash(hx, hy, hz, l.seed + 404));
                    float base = (0.05f + 0.22f * size * size * size) * grow;
                    float radius = Math.max(minRadius, base);
                    if (d >= radius) continue;
                    float bright = (0.15f + 0.85f * size * size) * Math.min(1f, 2f * base / radius);
                    float v = bright * (1 - smooth(radius * 0.35f, radius, d));
                    if (v > best) {
                        best = v;
                        warm = Noise.unit(Noise.hash(hx, hy, hz, l.seed + 505)) - 0.5f;
                    }
                }
            }
        }
        out[0] = l.color1[0] * (1 + warm * 0.3f);
        out[1] = l.color1[1];
        out[2] = l.color1[2] * (1 - warm * 0.3f);
        out[3] = clamp01(best) * l.alpha1;
    }

    // ---------------------------------------------------------------- filters: each writes its result for all of img into tmp

    private boolean filter(Layer l) {
        switch (l.layer.kind()) {
            case BLUR:
                System.arraycopy(img, 0, tmp, 0, img.length);
                return blur(tmp, l.amount * 10f);
            case BLOOM: return bloom(l);
            case PIXELATE: return rows.run(h, y -> pixelateRow(l, y));
            case CRYSTALLIZE: return rows.run(h, y -> crystallizeRow(l, y));
            case POSTERIZE: return rows.run(h, y -> posterizeRow(l, y));
            case HUE_SHIFT: return rows.run(h, y -> hueRow(l, y));
            case SWIRL: return rows.run(h, y -> swirlRow(l, y));
            case VIGNETTE: return rows.run(h, y -> vignetteRow(l, y));
            default:
                System.arraycopy(img, 0, tmp, 0, img.length);
                return true;
        }
    }

    /** Three box passes in each direction, which is close to a Gaussian, with the width across a row wider toward the zenith. */
    private boolean blur(float[] buf, float degrees) {
        if (degrees <= 0) return true;
        return rows.run(h, y -> {
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
            float[] line = new float[h * 4], out = new float[h * 4];
            for (int y = 0; y < h; y++) System.arraycopy(buf, (y * w + x) * 4, line, y * 4, 4);
            for (int pass = 0; pass < 3; pass++) {
                boxClamp(line, out, h, radius);
                float[] t = line;
                line = out;
                out = t;
            }
            for (int y = 0; y < h; y++) System.arraycopy(line, y * 4, buf, (y * w + x) * 4, 4);
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
        boolean ok = rows.run(h, y -> {
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
        return rows.run(h, y -> {
            for (int x = 0; x < w; x++) {
                int i = (y * w + x) * 4;
                for (int c = 0; c < 3; c++) tmp[i + c] = img[i + c] + tmp[i + c] * strength;
                tmp[i + 3] = 1;
            }
        });
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

    // ---------------------------------------------------------------- geometry

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

    /** The bilinear RGBA of an image in a sky direction, where the columns wrap and the rows stop at the ends. */
    private void sample(float[] src, float x, float y, float z, float[] out) {
        double lon = Math.atan2(x, -z);
        double el = Math.toDegrees(Math.asin(clamp(y, -1, 1)));
        double fx = (lon / (2 * Math.PI)) * w - 0.5;
        double fy = (SkyImage.TOP - el) / RANGE * h - 0.5;
        fy = Math.max(0, Math.min(h - 1, fy));
        int x0 = (int) Math.floor(fx), y0 = (int) Math.floor(fy);
        float tx = (float) (fx - x0), ty = (float) (fy - y0);
        int xa = Math.floorMod(x0, w), xb = Math.floorMod(x0 + 1, w), ya = y0, yb = Math.min(h - 1, y0 + 1);
        for (int c = 0; c < 4; c++) {
            float top = src[(ya * w + xa) * 4 + c] * (1 - tx) + src[(ya * w + xb) * 4 + c] * tx;
            float bottom = src[(yb * w + xa) * 4 + c] * (1 - tx) + src[(yb * w + xb) * 4 + c] * tx;
            out[c] = top * (1 - ty) + bottom * ty;
        }
    }

    private static float smooth(float a, float b, float t) {
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
