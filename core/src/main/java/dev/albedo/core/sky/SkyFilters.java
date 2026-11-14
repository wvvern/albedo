package dev.albedo.core.sky;

import static dev.albedo.core.sky.SkyMath.clamp;
import static dev.albedo.core.sky.SkyMath.clamp01;
import static dev.albedo.core.sky.SkyMath.smooth;

/** The filter layers of a bake, which read the sky below them in {@code img} and write their result for all of it into {@code tmp}. */
final class SkyFilters {
    private final SkyGrid grid;
    private final int w, h, used, termW, termH;
    private final float[] img, tmp;
    /** The term images as half float RGBA, see {@link SkyImage#terms}. */
    private final short[] terms;
    private final SkyGenerator.Rows rows;

    SkyFilters(SkyGrid grid, float[] img, float[] tmp, short[] terms, SkyGenerator.Rows rows) {
        this.grid = grid;
        this.img = img;
        this.tmp = tmp;
        this.terms = terms;
        this.rows = rows;
        w = grid.w;
        h = grid.h;
        used = grid.used;
        termW = grid.termW;
        termH = grid.termH;
    }

    /** Runs a filter, which writes its result for all of {@code img} into {@code tmp}. */
    boolean run(SkyGenerator.Layer l) {
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
            int radius = Math.min(w / 2 - 1, Math.round(degrees / 360f * w / Math.max(0.05f, grid.cosEl[y])));
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
            int radius = Math.round(degrees / SkyGrid.RANGE * h);
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
    private boolean bloom(SkyGenerator.Layer l) {
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
                terms[base + tx * 4 + c] = HalfFloat.encode(sum / 4 * k);
            }
            terms[base + tx * 4 + 3] = HalfFloat.encode(1);
        }
    }

    /** Cells of equal size in degrees in the layer frame, in rows whose cell count follows the width of the sky at their height. */
    private void pixelateRow(SkyGenerator.Layer l, int y) {
        float size = (float) Math.toRadians(3f * l.scale);
        float[] p = new float[3], d = new float[3], s = new float[4];
        for (int x = 0; x < w; x++) {
            grid.dir(x, y, d);
            l.local(d[0], d[1], d[2], p);
            double el = Math.asin(clamp(p[1], -1, 1));
            int band = (int) Math.floor((el + Math.PI / 2) / size);
            double center = -Math.PI / 2 + (band + 0.5) * size;
            int cells = Math.max(1, (int) Math.round(2 * Math.PI * Math.cos(center) / size));
            double lon = Math.atan2(p[0], -p[2]);
            double cellLon = (Math.floor((lon + Math.PI) / (2 * Math.PI) * cells) + 0.5) / cells * 2 * Math.PI - Math.PI;
            float cos = (float) Math.cos(center);
            l.world(cos * (float) Math.sin(cellLon), (float) Math.sin(center), -cos * (float) Math.cos(cellLon), d);
            grid.sample(img, d[0], d[1], d[2], s);
            System.arraycopy(s, 0, tmp, (y * w + x) * 4, 4);
        }
    }

    /** Each pixel takes the color at the nearest point of a jittered 3D grid, which makes cells like crystals. */
    private void crystallizeRow(SkyGenerator.Layer l, int y) {
        float n = (float) (1 / Math.toRadians(4f * l.scale));
        float[] p = new float[3], d = new float[3], s = new float[4];
        for (int x = 0; x < w; x++) {
            grid.dir(x, y, d);
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
            grid.sample(img, d[0], d[1], d[2], s);
            System.arraycopy(s, 0, tmp, (y * w + x) * 4, 4);
        }
    }

    /** Fewer color steps: from 2 to 16 for each channel, as AMOUNT sets. */
    private void posterizeRow(SkyGenerator.Layer l, int y) {
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
    private void hueRow(SkyGenerator.Layer l, int y) {
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
    private void swirlRow(SkyGenerator.Layer l, int y) {
        float[] c = new float[3], d = new float[3], s = new float[4];
        l.world(0, 0, -1, c);
        double radius = Math.toRadians(60 * l.scale), twist = l.amount * 3 * Math.PI;
        for (int x = 0; x < w; x++) {
            grid.dir(x, y, d);
            double cos = clamp(c[0] * d[0] + c[1] * d[1] + c[2] * d[2], -1, 1);
            double dist = Math.acos(cos);
            if (dist < radius) {
                double t = 1 - dist / radius, a = twist * t * t;
                rotate(d, c, (float) a);
            }
            grid.sample(img, d[0], d[1], d[2], s);
            System.arraycopy(s, 0, tmp, (y * w + x) * 4, 4);
        }
    }

    /** Mixes COLOR_1 in away from the layer center, from 30 percent of 90 degrees times the size out to all of it. */
    private void vignetteRow(SkyGenerator.Layer l, int y) {
        float[] c = new float[3], d = new float[3];
        l.world(0, 0, -1, c);
        double radius = Math.toRadians(90 * l.scale);
        for (int x = 0; x < w; x++) {
            grid.dir(x, y, d);
            double dist = Math.acos(clamp(c[0] * d[0] + c[1] * d[1] + c[2] * d[2], -1, 1));
            float k = smooth((float) (radius * 0.3), (float) radius, (float) dist) * l.amount * l.alpha1;
            int i = (y * w + x) * 4;
            for (int ch = 0; ch < 3; ch++) tmp[i + ch] = img[i + ch] + (l.color1[ch] - img[i + ch]) * k;
            tmp[i + 3] = img[i + 3];
        }
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
}
