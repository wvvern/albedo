package dev.albedo.core.sky;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SkyStarsTest {
    private static final float DEGREE = (float) (Math.PI / 180);

    /** The light at {@code p} of all stars of all cells up to three cells away, with no limit on the search. */
    private static float[] allStars(SkyStars stars, float scale, float[] p, float footprint) {
        float[] light = new float[3], star = new float[SkyStars.STAR_FLOATS];
        for (SkyStars.Tier t : SkyStars.Tier.values()) {
            float n = t.cells / scale;
            int cx = (int) Math.floor(p[0] * n), cy = (int) Math.floor(p[1] * n), cz = (int) Math.floor(p[2] * n);
            for (int i = -3; i <= 3; i++) {
                for (int j = -3; j <= 3; j++) {
                    for (int k = -3; k <= 3; k++) if (stars.star(t, cx + i, cy + j, cz + k, star)) stars.add(t, star, p, footprint, light);
                }
            }
        }
        return light;
    }

    @Test
    void noStarIsCutAtACellBoundary() {
        int checked = 0, lit = 0;
        for (float scale : new float[] {0.5f, 1f, 2.5f}) {
            SkyStars stars = new SkyStars(1f, 2f, scale, 4);
            float[] star = new float[SkyStars.STAR_FLOATS];
            // Points all around the brightest stars of many large cells, where the halos and the spikes reach the farthest.
            for (int cell = 0; cell < 4000; cell++) {
                int hx = cell % 20 - 10, hy = cell / 20 % 20 - 10, hz = (int) (SkyStars.Tier.BRIGHT.cells / scale) - cell / 400;
                if (!stars.star(SkyStars.Tier.BRIGHT, hx, hy, hz, star) || star[3] < 8) continue;
                float reach = 0.5f * scale / SkyStars.Tier.BRIGHT.cells;
                for (int a = 0; a < 48; a++) {
                    float angle = a * (float) Math.PI / 24, r = reach * (a % 8 + 0.5f) / 8;
                    float[] p = offset(star, r * (float) Math.cos(angle), r * (float) Math.sin(angle));
                    for (float footprint : new float[] {0.003f * DEGREE, 0.05f * DEGREE, 0.7f * DEGREE}) {
                        float[] search = new float[3];
                        stars.light(p, footprint, search);
                        float[] all = allStars(stars, scale, p, footprint);
                        for (int c = 0; c < 3; c++) assertEquals(all[c], search[c], 1e-5f + all[c] * 1e-5f, "scale " + scale + " cell " + cell);
                        checked++;
                        if (all[0] > 0.001f) lit++;
                    }
                }
            }
        }
        assertTrue(checked > 3000, "checked " + checked);
        assertTrue(lit > checked / 3, "many points get light from the star: " + lit + " of " + checked);
    }

    /** A unit direction near the star direction {@code s}, moved by two angles across it. */
    private static float[] offset(float[] s, float a, float b) {
        float ex = s[2], ez = -s[0], el = (float) Math.sqrt(ex * ex + ez * ez);
        float nx = s[1] * ez / el, ny = (s[2] * ex - s[0] * ez) / el, nz = -s[1] * ex / el;
        float x = s[0] + a * ex / el + b * nx, y = s[1] + b * ny, z = s[2] + a * ez / el + b * nz;
        float len = (float) Math.sqrt(x * x + y * y + z * z);
        return new float[] {x / len, y / len, z / len};
    }

    @Test
    void manyStarsAreFaintAndFewAreBright() {
        SkyStars stars = new SkyStars(0.5f, 1f, 1f, 1);
        float[] star = new float[SkyStars.STAR_FLOATS];
        List<Float> field = new ArrayList<Float>(), bright = new ArrayList<Float>();
        // The stars of the two grids in the same cap of the sky, up to about 37 degrees from the z axis.
        for (int x = -56; x <= 56; x++) {
            for (int y = -56; y <= 56; y++) {
                for (int z = 69; z <= 88; z++) if (stars.star(SkyStars.Tier.FIELD, x, y, z, star) && star[2] >= 0.8f) field.add(star[3]);
            }
        }
        for (int x = -11; x <= 11; x++) {
            for (int y = -11; y <= 11; y++) {
                for (int z = 11; z <= 16; z++) if (stars.star(SkyStars.Tier.BRIGHT, x, y, z, star) && star[2] >= 0.8f) bright.add(star[3]);
            }
        }
        Collections.sort(field);
        Collections.sort(bright);
        assertTrue(field.size() > 20 * bright.size(), field.size() + " faint and " + bright.size() + " bright stars");
        assertTrue(field.get(field.size() / 2) < 0.12f, "most faint stars are near the faintest: " + field.get(field.size() / 2));
        int spiked = 0;
        for (float f : bright) if (f > 8) spiked++;
        assertTrue(spiked > 0 && spiked < bright.size() / 5, spiked + " of " + bright.size() + " bright stars are very bright");
    }

    @Test
    void theColorsGoFromBlueWhiteToOrangeWithSlightTints() {
        float[] c = new float[3];
        int blue = 0, red = 0;
        for (int i = 0; i < 1000; i++) {
            SkyStars.color((i + 0.5f) / 1000, c);
            if (c[2] > c[0] * 1.1f) blue++;
            if (c[0] > c[2] * 1.3f) red++;
            for (float v : c) assertTrue(v >= 0.3f && v <= 1f, "a slight tint: " + v);
        }
        assertTrue(blue > 200 && blue < 450, blue + " blue stars of 1000");
        assertTrue(red > 200 && red < 450, red + " orange and red stars of 1000");
    }

    @Test
    void theShaderHasTheSameTiers() throws IOException {
        String glsl;
        try (InputStream in = SkyStarsTest.class.getResourceAsStream("/assets/albedo/shaders/include/sky_layers.glsl")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0;) out.write(buf, 0, n);
            glsl = new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
        for (SkyStars.Tier t : SkyStars.Tier.values()) {
            String call = "albedoStarTier(p, footprint, " + t.cells + ", " + t.minFlux + ", " + t.maxFlux + ", " + t.slope + ", " + t.salt
                    + ", " + t.detail + ",";
            assertTrue(glsl.contains(call), call);
        }
    }
}
