package dev.albedo.core.sky;

/** The meshes that draw a {@link SkyImage}, as x, y, z, u and v for each vertex. */
public final class SkyMesh {
    private SkyMesh() {}

    /**
     * Two triangles over a whole image, with texture coordinates from the top left corner, at the position 0 or at the sky
     * direction of each corner of a camera view.
     *
     * @param view the sky directions of the four corners of a camera view, or null
     */
    public static float[] quad(float[] view) {
        float[][] uv = {{0, 0}, {0, 1}, {1, 1}, {0, 0}, {1, 1}, {1, 0}};
        int[] corner = {0, 1, 2, 0, 2, 3};
        float[] out = new float[uv.length * 5];
        for (int i = 0; i < uv.length; i++) {
            if (view != null) System.arraycopy(view, corner[i] * 3, out, i * 5, 3);
            out[i * 5 + 3] = uv[i][0];
            out[i * 5 + 4] = uv[i][1];
        }
        return out;
    }

    /**
     * A sphere of triangles around the camera, with texture coordinates into a {@link SkyImage}.
     *
     * @param radius the distance of the vertices from the camera
     */
    public static float[] sphere(float radius) {
        int columns = 64, bands = 56;
        // Rows of equal height, so that v is linear in elevation across each triangle.
        float[] elevations = new float[bands + 1];
        for (int i = 0; i <= bands; i++) elevations[i] = SkyImage.TOP - (SkyImage.TOP - SkyImage.BOTTOM) * i / bands;
        float[] out = new float[(elevations.length - 1) * columns * 6 * 5];
        int k = 0;
        for (int r = 0; r < elevations.length - 1; r++) {
            for (int c = 0; c < columns; c++) {
                float u0 = c / (float) columns, u1 = (c + 1) / (float) columns;
                float e0 = elevations[r], e1 = elevations[r + 1];
                k = vertex(out, k, radius, u0, e0);
                k = vertex(out, k, radius, u0, e1);
                k = vertex(out, k, radius, u1, e1);
                k = vertex(out, k, radius, u0, e0);
                k = vertex(out, k, radius, u1, e1);
                k = vertex(out, k, radius, u1, e0);
            }
        }
        return out;
    }

    private static int vertex(float[] out, int k, float radius, float u, float elevation) {
        double lon = u * 2 * Math.PI, el = Math.toRadians(elevation);
        out[k] = (float) (Math.cos(el) * Math.sin(lon) * radius);
        out[k + 1] = (float) (Math.sin(el) * radius);
        out[k + 2] = (float) (-Math.cos(el) * Math.cos(lon) * radius);
        out[k + 3] = u;
        out[k + 4] = v(elevation);
        return k + 5;
    }

    /** The texture coordinate v of an elevation in degrees, from 0 at the top row to 1 at the last row. */
    private static float v(double elevation) {
        return (float) Math.max(0, Math.min(1, (SkyImage.TOP - elevation) / (SkyImage.TOP - SkyImage.BOTTOM)));
    }
}
