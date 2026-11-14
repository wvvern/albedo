#ifndef ALBEDO_SKY_DEEP_FIELD_GLSL
#define ALBEDO_SKY_DEEP_FIELD_GLSL

// The twin of DeepField, which sky_layers.glsl includes.

// The numbers of DeepField: the grid cells of the far galaxies and of the stars of the cloud for each unit of the sphere at a layer
// size of 100, the half width and the half length of the cloud in radians, and its linear light that the eye cannot split into stars.
const float ALBEDO_FIELD_CELLS = 7.0;
const float ALBEDO_FIELD_CLOUD_CELLS = 190.0;
const float ALBEDO_FIELD_CLOUD_WIDTH = 0.22;
const float ALBEDO_FIELD_CLOUD_LENGTH = 1.1;
const vec3 ALBEDO_FIELD_CLOUD_GLOW = vec3(0.07, 0.063, 0.06);

// DeepField.galaxies: the far galaxies near p, discs at each tilt and angle, the old ones in COLOR_1 and the young ones, with arms, in COLOR_2.
vec3 albedoFieldGalaxies(int layer, vec3 p, float footprint, float scale, int seed) {
    vec3 light = vec3(0.0);
    vec3 warm = albedoLayer(layer, 3).rgb, blue = albedoLayer(layer, 4).rgb;
    float chance = 0.15 + 0.85 * albedoLayer(layer, 1).z;
    float n = ALBEDO_FIELD_CELLS / scale, reach = 0.5 * scale / ALBEDO_FIELD_CELLS;
    ivec3 base = ivec3(floor(p * n - 0.5));
    for (int i = 0; i <= 1; i++) {
        for (int j = 0; j <= 1; j++) {
            for (int k = 0; k <= 1; k++) {
                ivec3 h = base + ivec3(i, j, k);
                if (albedoUnit(albedoHash(h, seed)) >= chance) continue;
                vec3 s;
                if (!albedoCellPoint(h, albedoHash(h, seed + 1), n, s)) continue;
                vec3 d = p - s;
                float d2 = dot(d, d);
                if (d2 >= reach * reach) continue;
                uint hs = albedoHash(h, seed + 2), hc = albedoHash(h, seed + 3);
                // Few galaxies are large: the length over which the light of a disc falls is at most a fifth of the reach.
                float big = float(hs >> 22u) / 1024.0;
                float size = reach * (0.035 + 0.165 * big * big);
                float ratio = 0.2 + 0.8 * float((hs >> 12u) & 1023u) / 1024.0;
                float angle = ALBEDO_PI * float((hs >> 2u) & 1023u) / 1024.0;
                float young = float(hc >> 22u) / 1024.0;
                float lum = 0.05 + 0.2 * float((hc >> 12u) & 1023u) / 1024.0;
                // The tangent frame of the galaxy: east along the layer horizon and north toward the layer zenith.
                float ex = s.z, ez = -s.x, el = sqrt(ex * ex + ez * ez);
                if (el < 1e-4) {
                    ex = 1.0;
                    ez = 0.0;
                } else {
                    ex /= el;
                    ez /= el;
                }
                float east = d.x * ex + d.z * ez, north = d.x * s.y * ez + d.y * (s.z * ex - s.x * ez) - d.z * s.y * ex;
                float ca = cos(angle), sa = sin(angle);
                // A disc seen from its edge is not thinner than a pixel, and darker by the same ratio.
                float thin = max(ratio, 0.7 * footprint / size);
                float u = east * ca + north * sa, w = (north * ca - east * sa) / thin;
                float r = sqrt(u * u + w * w + 0.25 * footprint * footprint);
                float prof = exp(-r / size) * lum * ratio / thin;
                // The arms of a young galaxy show only when the pixels are small enough for them.
                // The angle at the exact center is not defined in GLSL, and the arms have the same light at each angle there.
                float turn = u != 0.0 || w != 0.0 ? atan(w, u) : 0.0;
                float wave = 0.5 + 0.5 * cos(2.0 * (turn - 2.6 * log(r / size + 0.25)));
                float arms = albedoSmooth(0.5, 0.6, young) * albedoSmooth(3.0 * footprint, 6.0 * footprint, size);
                prof *= 1.0 + arms * (0.9 * wave * wave - 0.45);
                prof *= 1.0 - albedoSmooth(0.8 * reach, reach, sqrt(d2));
                light += (warm + (blue - warm) * young) * prof;
            }
        }
    }
    return light;
}

// DeepField.stars: the faint stars of the cloud near p, where density is the chance that a cell has a star.
vec3 albedoFieldStars(vec3 p, float footprint, float scale, int seed, float density, float cloud) {
    vec3 light = vec3(0.0);
    float n = ALBEDO_FIELD_CLOUD_CELLS / scale, reach = 0.5 * scale / ALBEDO_FIELD_CLOUD_CELLS;
    float blur = ALBEDO_STAR_PIXEL * footprint, core = ALBEDO_STAR_CORE * scale, core2 = core * core + blur * blur;
    ivec3 base = ivec3(floor(p * n - 0.5));
    for (int i = 0; i <= 1; i++) {
        for (int j = 0; j <= 1; j++) {
            for (int k = 0; k <= 1; k++) {
                ivec3 h = base + ivec3(i, j, k);
                uint hb = albedoHash(h, seed);
                if (float(hb >> 24u) / 256.0 >= density) continue;
                vec3 s;
                if (!albedoCellPoint(h, albedoHash(h, seed + 1), n, s)) continue;
                vec3 d = p - s;
                float d2 = dot(d, d);
                if (d2 >= reach * reach) continue;
                float flux = min(0.7, 0.03 * pow((float((hb >> 12u) & 4095u) + 0.5) / 4096.0, -1.0 / 1.3));
                float glow = cloud * 2.0 * flux * core / sqrt(core2) * exp(-d2 / (2.0 * core2)) * (1.0 - albedoSmooth(0.8 * reach, reach, sqrt(d2)));
                light += albedoStarColor((float(hb & 4095u) + 0.5) / 4096.0) * glow;
            }
        }
    }
    return light;
}

// DeepField.cloud: the cloud of stars of our own galaxy, a band along the horizon of the layer that is brightest at the layer center,
// with a soft glow, a dark rift of dust through it and many faint stars.
vec3 albedoFieldCloud(vec3 p, float footprint, float scale, int seed, float cloud) {
    float lat = asin(clamp(p.y, -1.0, 1.0)) / ALBEDO_FIELD_CLOUD_WIDTH;
    float band = exp(-lat * lat);
    if (band < 0.003) return vec3(0.0);
    float az = atan(p.x, -p.z) / ALBEDO_FIELD_CLOUD_LENGTH;
    float shape = albedoFbm(p * 3.2, seed + 11, 5);
    float body = band * (0.3 + 0.7 * exp(-az * az)) * albedoSmooth(-0.45, 0.35, shape + 0.15);
    float rift = albedoSmooth(0.35, 0.8, albedoRidged(p * 3.6 + 0.3 * shape, seed + 12, 4));
    body *= 1.0 - 0.8 * rift;
    float glow = cloud * body * (0.7 + 0.5 * albedoFbmSoft(p * 14.0, seed + 13, 4, footprint * 14.0));
    return glow * ALBEDO_FIELD_CLOUD_GLOW + albedoFieldStars(p, footprint, scale, seed + 14, 0.95 * body, cloud);
}

// Far galaxies over the whole sky, where AMOUNT is their count, and a cloud of the stars of our own galaxy, where HEIGHT is its light.
vec4 albedoDeepField(int layer, vec3 p, float footprint, int flags) {
    vec4 shape = albedoLayer(layer, 2);
    int seed = int(shape.y) * 31 + 5;
    vec3 light = albedoFieldGalaxies(layer, p, footprint, shape.x, seed);
    if ((flags & ALBEDO_FLAG_CLOUD) != 0) light += albedoFieldCloud(p, footprint, shape.x, seed, albedoLayer(layer, 1).w);
    vec4 c = albedoLightAlpha(light, vec3(0.0));
    return vec4(c.rgb, c.a * max(shape.z, shape.w));
}

#endif
