package dev.albedo.core.sky;

import dev.albedo.core.config.SkyDesign;

import static dev.albedo.core.sky.SkyMath.clamp;

/** The equirectangular pixels of a bake, with the sky direction of each pixel, built once for each bake. */
final class SkyGrid {
    /** The elevation in degrees from the top to the bottom row. */
    static final float RANGE = SkyImage.TOP - SkyImage.BOTTOM;

    final int w, h;
    final float[] sinLon, cosLon, sinEl, cosEl, elevation;
    /** The number of rows from the top that the layers and filters fill, where a filter reads the last of them for each row below. */
    final int used;
    /** The width of one texel at the horizon, in radians. */
    final float texel;
    /** The size of each term image, half the size of the image. */
    final int termW, termH;

    SkyGrid(SkyDesign design, int w, int h) {
        this.w = w;
        this.h = h;
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
        while (filled > 1 && elevation[filled - 1] < SkyImage.layersBottom(design.drawnExtent())) filled--;
        used = filled;
        texel = (float) (2 * Math.PI / w);
        termW = Math.max(1, w / 2);
        termH = Math.max(1, h / 2);
    }

    /** The unit sky direction of the center of a pixel. */
    void dir(int x, int y, float[] out) {
        out[0] = cosEl[y] * sinLon[x];
        out[1] = sinEl[y];
        out[2] = -cosEl[y] * cosLon[x];
    }

    /** The bilinear RGBA of an image in a sky direction, where the columns wrap and the rows stop at the first and the last used row. */
    void sample(float[] src, float x, float y, float z, float[] out) {
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
}
