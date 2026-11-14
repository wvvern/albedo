#ifndef ALBEDO_SKY_NOISE_GLSL
#define ALBEDO_SKY_NOISE_GLSL

// The twin of Noise, which sky_layers.glsl includes.

// Noise.hash, where uint arithmetic wraps as Java int arithmetic does.
uint albedoHash(ivec3 p, int seed) {
    uint h = uint(seed) * 0x27D4EB2Du ^ uint(p.x) * 0x165667B1u ^ uint(p.y) * 0x1B873593u ^ uint(p.z) * 0x6C8E9CF5u;
    h ^= h >> 15u;
    h *= 0x85EBCA6Bu;
    h ^= h >> 13u;
    h *= 0xC2B2AE35u;
    h ^= h >> 16u;
    return h;
}

// Math.floorMod: the cell i of a row that repeats after n cells, with remainders of numbers that are not negative only.
int albedoRepeat(int i, int n) {
    return i < 0 ? n - 1 - (-1 - i) % n : i % n;
}

// Noise.unit: a hash as a float from 0 to 1.
float albedoUnit(uint h) {
    return float(h >> 8u) / 16777216.0;
}

// Noise.grad: the offset times one of 12 edge directions, where the codes 12 to 15 repeat the codes 0, 1, 9 and 11.
float albedoGrad(uint h, vec3 f) {
    int g = int(h & 15u);
    if (g >= 12) g = g == 12 ? 0 : g == 13 ? 1 : g == 14 ? 9 : 11;
    float u = g < 8 ? f.x : f.y;
    float v = g < 4 ? f.y : f.z;
    return ((g & 1) == 0 ? u : -u) + ((g & 2) == 0 ? v : -v);
}

// Noise.lerp, written out so that the GPU rounds as the CPU does.
float albedoLerp(float a, float b, float t) {
    return a + (b - a) * t;
}

// Noise.noise: gradient noise at a point, from about -1 to 1.
float albedoNoise(vec3 p, int seed) {
    vec3 i = floor(p);
    ivec3 c = ivec3(i);
    vec3 f = p - i;
    vec3 u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
    float x00 = albedoLerp(albedoGrad(albedoHash(c, seed), f), albedoGrad(albedoHash(c + ivec3(1, 0, 0), seed), f - vec3(1.0, 0.0, 0.0)), u.x);
    float x10 = albedoLerp(albedoGrad(albedoHash(c + ivec3(0, 1, 0), seed), f - vec3(0.0, 1.0, 0.0)),
            albedoGrad(albedoHash(c + ivec3(1, 1, 0), seed), f - vec3(1.0, 1.0, 0.0)), u.x);
    float x01 = albedoLerp(albedoGrad(albedoHash(c + ivec3(0, 0, 1), seed), f - vec3(0.0, 0.0, 1.0)),
            albedoGrad(albedoHash(c + ivec3(1, 0, 1), seed), f - vec3(1.0, 0.0, 1.0)), u.x);
    float x11 = albedoLerp(albedoGrad(albedoHash(c + ivec3(0, 1, 1), seed), f - vec3(0.0, 1.0, 1.0)),
            albedoGrad(albedoHash(c + ivec3(1, 1, 1), seed), f - vec3(1.0, 1.0, 1.0)), u.x);
    return albedoLerp(albedoLerp(x00, x10, u.y), albedoLerp(x01, x11, u.y), u.z);
}

// Noise.value: smooth value noise in two dimensions from 0 to 1, which repeats after the cells of period, or never on an axis of 0.
float albedoValue(vec2 q, ivec2 period, int seed) {
    vec2 i = floor(q);
    int x0 = int(i.x), y0 = int(i.y);
    int x1 = period.x > 0 ? albedoRepeat(x0 + 1, period.x) : x0 + 1, y1 = period.y > 0 ? albedoRepeat(y0 + 1, period.y) : y0 + 1;
    if (period.x > 0) x0 = albedoRepeat(x0, period.x);
    if (period.y > 0) y0 = albedoRepeat(y0, period.y);
    vec2 f = q - i;
    vec2 u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
    float a = albedoUnit(albedoHash(ivec3(x0, y0, 0), seed)), b = albedoUnit(albedoHash(ivec3(x1, y0, 0), seed));
    float d = albedoUnit(albedoHash(ivec3(x0, y1, 0), seed)), e = albedoUnit(albedoHash(ivec3(x1, y1, 0), seed));
    float top = albedoLerp(a, b, u.x);
    return albedoLerp(top, albedoLerp(d, e, u.x), u.y);
}

// Noise.cellPoint: the jittered point of a cell on the unit sphere, from the hash hj of the cell, with n cells for each unit.
// The point of the cell moved onto the sphere, and the cell keeps it only when it stays in the cell.
bool albedoCellPoint(ivec3 cell, uint hj, float n, out vec3 s) {
    vec3 v = vec3(cell) + (vec3(float(hj >> 22u), float((hj >> 12u) & 1023u), float((hj >> 2u) & 1023u)) + 0.5) / 1024.0;
    float len = length(v);
    if (len < 1e-3) {
        s = vec3(0.0);
        return false;
    }
    s = v / len;
    return ivec3(floor(s * n)) == cell;
}

// Noise.LACUNARITY, OCTAVE_SEED, FBM_GAIN, RIDGED_LACUNARITY and RIDGED_SEED: the frequency step and the seed step of the
// octaves, the gain of fbm, and the frequency step and the seed step of ridged noise.
const float ALBEDO_NOISE_LACUNARITY = 2.03;
const int ALBEDO_NOISE_OCTAVE_SEED = 1013;
const float ALBEDO_NOISE_FBM_GAIN = 1.6;
const float ALBEDO_NOISE_RIDGED_LACUNARITY = 2.1;
const int ALBEDO_NOISE_RIDGED_SEED = 7919;

// Noise.fbmSoft: fbm where each octave fades out as its cell comes near the size of a pixel, the footprint in noise units.
float albedoFbmSoft(vec3 p, int seed, int octaves, float footprint) {
    // The sum of the weights of all octaves, the same as in fbm, also when the fine octaves are gone.
    float sum = 0.0, amp = 1.0, norm = 2.0 - 2.0 * pow(0.5, float(octaves)), f = footprint;
    for (int o = 0; o < octaves; o++) {
        float t = clamp((f - 0.25) / 0.25, 0.0, 1.0);
        // Each finer octave has a larger footprint, so all octaves from here are gone.
        if (t >= 1.0) break;
        sum += albedoNoise(p, seed + o * ALBEDO_NOISE_OCTAVE_SEED) * amp * (1.0 - t * t * (3.0 - 2.0 * t));
        amp *= 0.5;
        p *= ALBEDO_NOISE_LACUNARITY;
        f *= ALBEDO_NOISE_LACUNARITY;
    }
    return sum / norm * ALBEDO_NOISE_FBM_GAIN;
}

// Noise.fbm: a sum of octaves at twice the frequency and half the weight each, from about -1 to 1.
float albedoFbm(vec3 p, int seed, int octaves) {
    return albedoFbmSoft(p, seed, octaves, 0.0);
}

// Noise.fbmPart: the octaves from first to last - 1 of albedoFbm with this many octaves.
float albedoFbmPart(vec3 p, int seed, int first, int last, int octaves) {
    float sum = 0.0, amp = 1.0;
    for (int o = 0; o < last; o++) {
        if (o >= first) sum += albedoNoise(p, seed + o * ALBEDO_NOISE_OCTAVE_SEED) * amp;
        amp *= 0.5;
        p *= ALBEDO_NOISE_LACUNARITY;
    }
    return sum / (2.0 - 2.0 * pow(0.5, float(octaves))) * ALBEDO_NOISE_FBM_GAIN;
}

// Noise.ridged: sharp bright lines where the noise crosses zero, from 0 to 1.
float albedoRidged(vec3 p, int seed, int octaves) {
    float sum = 0.0, amp = 0.5, norm = 0.0, prev = 1.0;
    for (int o = 0; o < octaves; o++) {
        float n = 1.0 - abs(albedoNoise(p, seed + o * ALBEDO_NOISE_RIDGED_SEED));
        n *= n;
        sum += n * amp * prev;
        norm += amp;
        prev = n;
        amp *= 0.5;
        p *= ALBEDO_NOISE_RIDGED_LACUNARITY;
    }
    return min(1.0, sum / norm);
}

#endif
