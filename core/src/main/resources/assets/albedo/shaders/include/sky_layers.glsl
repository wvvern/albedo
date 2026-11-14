#ifndef ALBEDO_SKY_LAYERS_GLSL
#define ALBEDO_SKY_LAYERS_GLSL

#include <albedo:oklab.glsl>

// The custom sky for each pixel, a port of dev.albedo.core.sky.SkyGenerator that reads the block of SkyProgram.

#define ALBEDO_MAX_LAYERS 8
#define ALBEDO_LAYER_VEC4S 10
#define ALBEDO_RING 64

#define ALBEDO_BLEND_ADD 1
#define ALBEDO_BLEND_SCREEN 2
#define ALBEDO_BLEND_MULTIPLY 3

// The parts of a layer that its values turn on, which SkyShaderSource puts in each layer step, so that the compiler leaves out the parts that are off.
#define ALBEDO_FLAG_TWINKLE 1
#define ALBEDO_FLAG_TRAILS 2
#define ALBEDO_FLAG_SPIKES 4
#define ALBEDO_FLAG_PULSE 8
#define ALBEDO_FLAG_DUST 16
#define ALBEDO_FLAG_JETS 32
#define ALBEDO_FLAG_DETAIL 64
#define ALBEDO_FLAG_MOTION 128
#define ALBEDO_FLAG_CLOUD 256

// SkyGenerator.CLOUD_OCTAVES and CLOUD_FIELD_OCTAVES: the octaves of the cloud noise and the coarse octaves that a field image holds.
#define ALBEDO_CLOUD_OCTAVES 6
#define ALBEDO_CLOUD_FIELD_OCTAVES 3

#ifdef ALBEDO_PLAIN_UNIFORMS
// The values of the uniform block below, for the GL programs that set them as three arrays.
uniform vec4 SkyHeader;
uniform vec4 SkyWorld[3];
uniform vec4 SkyLayers[ALBEDO_MAX_LAYERS * ALBEDO_LAYER_VEC4S];
uniform vec4 SkyRing[ALBEDO_RING];
#else
layout(std140) uniform AlbedoSky {
    // The layer count, the extent (1 for the full sphere), the term count and the time in seconds (negative for a still sky).
    vec4 SkyHeader;
    // The rows of the turn from sky directions to world directions, so that the second row is the world zenith in sky directions.
    vec4 SkyWorld[3];
    vec4 SkyLayers[ALBEDO_MAX_LAYERS * ALBEDO_LAYER_VEC4S];
    // The linear horizon color of each azimuth sector, from north toward east.
    vec4 SkyRing[ALBEDO_RING];
};
#endif

// The equirectangular term images of the CPU bake in linear light, one under the other in the order of their slots.
uniform sampler2D AlbedoTerms;

// GLSL 1.20 with EXT_gpu_shader4 has other names for the texture size and read, and the term texture has no mip levels.
#if __VERSION__ >= 130
#define ALBEDO_TERM_ROWS float(textureSize(AlbedoTerms, 0).y)
#define ALBEDO_TERM_READ(uv) textureLod(AlbedoTerms, uv, 0.0)
#else
#define ALBEDO_TERM_ROWS float(textureSize2D(AlbedoTerms, 0).y)
#define ALBEDO_TERM_READ(uv) texture2D(AlbedoTerms, uv)
#endif

const float ALBEDO_PI = 3.14159265358979;
// The distance in degrees below the horizon over which a sky that stops at the horizon goes into the horizon color.
const float ALBEDO_FADE = 3.0;

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

// Noise.fbmSoft: fbm where each octave fades out as its cell comes near the size of a pixel, the footprint in noise units.
float albedoFbmSoft(vec3 p, int seed, int octaves, float footprint) {
    // The sum of the weights of all octaves, the same as in fbm, also when the fine octaves are gone.
    float sum = 0.0, amp = 1.0, norm = 2.0 - 2.0 * pow(0.5, float(octaves)), f = footprint;
    for (int o = 0; o < octaves; o++) {
        float t = clamp((f - 0.25) / 0.25, 0.0, 1.0);
        // Each finer octave has a larger footprint, so all octaves from here are gone.
        if (t >= 1.0) break;
        sum += albedoNoise(p, seed + o * 1013) * amp * (1.0 - t * t * (3.0 - 2.0 * t));
        amp *= 0.5;
        p *= 2.03;
        f *= 2.03;
    }
    return sum / norm * 1.6;
}

// Noise.fbm: a sum of octaves at twice the frequency and half the weight each, from about -1 to 1.
float albedoFbm(vec3 p, int seed, int octaves) {
    return albedoFbmSoft(p, seed, octaves, 0.0);
}

// Noise.fbmPart: the octaves from first to last - 1 of albedoFbm with this many octaves.
float albedoFbmPart(vec3 p, int seed, int first, int last, int octaves) {
    float sum = 0.0, amp = 1.0;
    for (int o = 0; o < last; o++) {
        if (o >= first) sum += albedoNoise(p, seed + o * 1013) * amp;
        amp *= 0.5;
        p *= 2.03;
    }
    return sum / (2.0 - 2.0 * pow(0.5, float(octaves))) * 1.6;
}

// Noise.ridged: sharp bright lines where the noise crosses zero, from 0 to 1.
float albedoRidged(vec3 p, int seed, int octaves) {
    float sum = 0.0, amp = 0.5, norm = 0.0, prev = 1.0;
    for (int o = 0; o < octaves; o++) {
        float n = 1.0 - abs(albedoNoise(p, seed + o * 7919));
        n *= n;
        sum += n * amp * prev;
        norm += amp;
        prev = n;
        amp *= 0.5;
        p *= 2.1;
    }
    return min(1.0, sum / norm);
}

float albedoSmooth(float a, float b, float t) {
    float x = clamp((t - a) / (b - a), 0.0, 1.0);
    return x * x * (3.0 - 2.0 * x);
}

float albedoLightness(vec3 c) {
    return albedoLinearToLab(c).x;
}

// SkyGenerator.lightAlpha: the alpha carries the light up to 1 and the color carries the rest, so that an added layer adds all
// of the light; empty is the color where there is no light.
vec4 albedoLightAlpha(vec3 light, vec3 empty) {
    float a = min(1.0, max(light.r, max(light.g, light.b)));
    return vec4(a > 0.0 ? light / a : empty, a);
}

vec4 albedoLayer(int layer, int slot) {
    return SkyLayers[layer * ALBEDO_LAYER_VEC4S + slot];
}

// The OKLab mix of the two colors of a layer at t, as linear RGB and alpha.
vec4 albedoMix(int layer, float t) {
    t = clamp(t, 0.0, 1.0);
    vec4 alphas = albedoLayer(layer, 2);
    vec3 lab = mix(albedoLayer(layer, 5).xyz, albedoLayer(layer, 6).xyz, t);
    return vec4(albedoLabToLinear(lab), alphas.z + (alphas.w - alphas.z) * t);
}

// COLOR_1 low and COLOR_2 high, with the middle at HEIGHT of the way up and AMOUNT of softness.
vec4 albedoGradient(int layer, vec3 p) {
    vec4 values = albedoLayer(layer, 1);
    float t = asin(clamp(p.y, -1.0, 1.0)) / (ALBEDO_PI / 2.0);
    float soft = 0.03 + values.z * 0.9;
    return albedoMix(layer, albedoSmooth(values.w - soft, values.w + soft, t));
}

// A band of COLOR_1 along the horizon of the layer, strongest at its center and AMOUNT wide around the sky.
vec4 albedoGlow(int layer, vec3 p) {
    vec4 values = albedoLayer(layer, 1);
    float el = degrees(asin(clamp(p.y, -1.0, 1.0)));
    float band = max(1.0, values.w * 60.0);
    float v = el >= 0.0 ? el / band : -el / (band * 0.35);
    float along = 1.0;
    if (values.z < 1.0) {
        float az = atan(p.x, -p.z);
        float spread = 0.15 + values.z * ALBEDO_PI;
        along = exp(-(az / spread) * (az / spread));
    }
    return vec4(albedoLayer(layer, 3).rgb, exp(-v * v) * along * albedoLayer(layer, 2).z);
}

// The numbers of SkyStars: angles in radians, where the core, the halo, the rim and the spikes are at a layer size of 100.
const float ALBEDO_STAR_CORE = 0.009 * ALBEDO_PI / 180.0;
const float ALBEDO_STAR_PIXEL = 0.42;
const float ALBEDO_STAR_HALO = 0.03 * ALBEDO_PI / 180.0;
const float ALBEDO_STAR_HALO_LIGHT = 0.025;
const float ALBEDO_STAR_RIM = 0.016 * ALBEDO_PI / 180.0;
const float ALBEDO_STAR_RIM_WIDTH = 0.006 * ALBEDO_PI / 180.0;
const float ALBEDO_STAR_RIM_LIGHT = 0.03;
const float ALBEDO_STAR_SPIKE_FLUX = 2.0;
const float ALBEDO_STAR_SPIKE_LENGTH = 1.4 * ALBEDO_PI / 180.0;
const float ALBEDO_STAR_SPIKE_WIDTH = 0.004 * ALBEDO_PI / 180.0;
const float ALBEDO_STAR_SPIKE_LIGHT = 0.45;
const vec3 ALBEDO_STAR_RIM_WAVE = vec3(1.08, 1.0, 0.9);
const vec3 ALBEDO_STAR_SPIKE_WAVE = vec3(1.04, 1.0, 0.95);
const float ALBEDO_STAR_TINT = 0.7;
const float ALBEDO_STAR_TWINKLE_ZENITH = 0.1;
const float ALBEDO_STAR_TWINKLE_HORIZON = 0.3;
const float ALBEDO_STAR_TWINKLE_COLOR = 0.5;
const float ALBEDO_STAR_TRAIL_PIXEL = 0.6;
const float ALBEDO_STAR_TRAIL_REACH = 4.0;
const float ALBEDO_STAR_TRAIL_GAIN = 1.0;
const float ALBEDO_STAR_FIELD_SPREAD = 1.5 * ALBEDO_PI / 180.0;

// The part of the stars of class i (O, B, A, F, G, K, M) among the stars that the eye sees.
float albedoClassShare(int i) {
    if (i == 0) return 0.02;
    if (i == 1) return 0.12;
    if (i == 2) return 0.2;
    if (i == 3) return 0.17;
    if (i == 4) return 0.16;
    if (i == 5) return 0.25;
    return 0.08;
}

// The linear color of a black body at boundary i of the classes, from 40000 K to 2600 K.
vec3 albedoClassColor(int i) {
    if (i == 0) return vec3(0.3278, 0.4342, 1.0);
    if (i == 1) return vec3(0.4020, 0.5210, 1.0);
    if (i == 2) return vec3(0.5906, 0.6795, 1.0);
    if (i == 3) return vec3(0.9387, 0.9301, 1.0);
    if (i == 4) return vec3(1.0, 0.9047, 0.8228);
    if (i == 5) return vec3(1.0, 0.6445, 0.3564);
    if (i == 6) return vec3(1.0, 0.4969, 0.1981);
    return vec3(1.0, 0.3515, 0.0802);
}

// The linear color of a star at u along the classes, a mix of the two boundary colors of its class with white.
vec3 albedoStarColor(float u) {
    int i = 0;
    while (i < 6 && u >= albedoClassShare(i)) {
        u -= albedoClassShare(i);
        i++;
    }
    float f = min(1.0, u / albedoClassShare(i));
    vec3 a = albedoClassColor(i);
    return 1.0 - ALBEDO_STAR_TINT + ALBEDO_STAR_TINT * (a + (albedoClassColor(i + 1) - a) * f);
}

// The light of the thin rim of a star at the distance dist from it, for each channel, with red outside and blue inside.
vec3 albedoStarRim(float flux, float dist, float blur2, float scale) {
    float width = ALBEDO_STAR_RIM_WIDTH * scale, wide = sqrt(width * width + blur2);
    float radius = ALBEDO_STAR_RIM * scale * sqrt(flux);
    vec3 r = (dist - radius * ALBEDO_STAR_RIM_WAVE) / wide;
    return flux * ALBEDO_STAR_RIM_LIGHT * width / wide * exp(-0.5 * r * r);
}

// The light of one spike line along the unit direction dir of the tangent plane, for each channel.
vec3 albedoSpikeLine(vec2 uv, vec2 dir, float span, float gain, float blur2, float scale) {
    float along = abs(uv.x * dir.x + uv.y * dir.y);
    float across = uv.y * dir.x - uv.x * dir.y;
    vec3 out3 = vec3(0.0);
    for (int c = 0; c < 3; c++) {
        float reach = span * ALBEDO_STAR_SPIKE_WAVE[c];
        if (along >= reach) continue;
        float taper = 1.0 - along / reach;
        float width = ALBEDO_STAR_SPIKE_WIDTH * scale * ALBEDO_STAR_SPIKE_WAVE[c];
        float wide = sqrt(width * width + blur2);
        float x = across / wide;
        out3[c] = gain * taper * taper * taper * width / wide * exp(-0.5 * x * x);
    }
    return out3;
}

// The light of the spikes of a bright star s at the offset d from it: three lines at 60 degrees and a faint horizontal line.
vec3 albedoSpikes(vec3 s, vec3 d, float flux, float maxFlux, float spikes, float blur2, float scale) {
    float span = ALBEDO_STAR_SPIKE_LENGTH * scale * sqrt((flux - ALBEDO_STAR_SPIKE_FLUX) / (maxFlux - ALBEDO_STAR_SPIKE_FLUX));
    // The tangent frame of the star: east along the layer horizon and north toward the layer zenith, so the spikes do not turn.
    float ex = s.z, ez = -s.x, el = sqrt(ex * ex + ez * ez);
    if (el < 1e-4) {
        ex = 1.0;
        ez = 0.0;
    } else {
        ex /= el;
        ez /= el;
    }
    vec3 north = vec3(s.y * ez, s.z * ex - s.x * ez, -s.y * ex);
    vec2 uv = vec2(d.x * ex + d.z * ez, d.x * north.x + d.y * north.y + d.z * north.z);
    float gain = spikes * ALBEDO_STAR_SPIKE_LIGHT * sqrt(flux);
    vec3 light = albedoSpikeLine(uv, vec2(0.0, 1.0), span, gain, blur2, scale);
    light += albedoSpikeLine(uv, vec2(0.8660254, 0.5), span, gain, blur2, scale);
    light += albedoSpikeLine(uv, vec2(-0.8660254, 0.5), span, gain, blur2, scale);
    light += albedoSpikeLine(uv, vec2(1.0, 0.0), span * 0.7, gain * 0.5, blur2, scale);
    return light;
}

// SkyMath.wave: a sine wave from -1 to 1 at a frequency in hertz and a phase in turns.
float albedoWave(float hertz, float phase, float time) {
    return sin(2.0 * ALBEDO_PI * fract(hertz * time + phase));
}

// The twinkle of SkyStars.add: the factor of the light of a bright star and the shift of its red against its blue, from waves of the cell hash.
vec2 albedoTwinkle(ivec3 cell, int seed, float flux, float height, float twinkle, float time) {
    uint hk = albedoHash(cell, seed);
    // The frequencies are whole numbers of turns in the hour of the sky clock, so that no star jumps when the clock starts again.
    vec2 hertz = vec2(0.5 + float(hk >> 24u) / 225.0, 1.3 + float((hk >> 16u) & 255u) / 150.0);
    vec2 phase = vec2(float((hk >> 8u) & 255u), float(hk & 255u)) / 256.0;
    float wave = 0.6 * albedoWave(hertz.x, phase.x, time) + 0.4 * albedoWave(hertz.y, phase.y, time);
    // The light goes through more air near the horizon, and a bright star shows its twinkle more.
    float air = 1.0 - albedoSmooth(0.05, 0.75, height);
    float change = twinkle * (ALBEDO_STAR_TWINKLE_ZENITH + ALBEDO_STAR_TWINKLE_HORIZON * air) * (0.35 + 0.65 * albedoSmooth(0.2, 6.0, flux));
    float tint = ALBEDO_STAR_TWINKLE_COLOR * change * air * albedoWave(hertz.y - hertz.x, phase.x + phase.y, time);
    return vec2(1.0 + change * wave, tint);
}

// SkyStars.spread: the part of its light that a faint star keeps in a sky with trails, because its light spreads along its arc.
float albedoSpread(vec3 s, vec4 trail, float scale) {
    float h = dot(s, trail.xyz);
    float arc = trail.w * sqrt(max(0.0, 1.0 - h * h)) / (ALBEDO_STAR_FIELD_SPREAD * scale);
    return 1.0 / (1.0 + arc * arc);
}

// The light of the stars of one grid (SkyStars.Tier) at p, where each star stays in its cell and reaches at most half a cell.
vec3 albedoStarTier(vec3 p, float footprint, float cells, float minFlux, float maxFlux, float slope, int salt, bool bright,
        float density, float spikes, float scale, int seed, float twinkle, float time, vec3 up, vec4 trail) {
    vec3 light = vec3(0.0);
    float n = cells / scale;
    float reach = 0.5 * scale / cells;
    float blur = ALBEDO_STAR_PIXEL * footprint, blur2 = blur * blur;
    // The 2 x 2 x 2 cells nearest to the point, because no star reaches into a cell that is not next to its own.
    ivec3 base = ivec3(floor(p * n - 0.5));
    for (int i = 0; i <= 1; i++) {
        for (int j = 0; j <= 1; j++) {
            for (int k = 0; k <= 1; k++) {
                ivec3 h = base + ivec3(i, j, k);
                uint hb = albedoHash(h, seed * 31 + salt);
                float chance = float(hb >> 24u) / 256.0;
                if (chance >= density) continue;
                vec3 s;
                if (!albedoCellPoint(h, albedoHash(h, seed * 31 + salt + 1), n, s)) continue;
                vec3 d = p - s;
                float d2 = dot(d, d);
                if (d2 >= reach * reach) continue;
                float flux = min(maxFlux, minFlux * pow((float((hb >> 12u) & 4095u) + 0.5) / 4096.0, -1.0 / slope));
                if (trail.w > 0.0 && !bright) flux *= albedoSpread(s, trail, scale);
                float tint = 0.0, still = flux;
                if (twinkle > 0.0) {
                    vec2 change = albedoTwinkle(h, seed * 31 + salt + 2, flux, dot(s, up), twinkle, time);
                    flux *= change.x;
                    tint = change.y;
                }
                float dist = sqrt(d2);
                // The footprint widens the core, and its peak falls with the width, not the area, so that a star smaller than a pixel stays visible.
                float core = ALBEDO_STAR_CORE * scale, core2 = core * core + blur2;
                float glow = flux * core / sqrt(core2) * exp(-d2 / (2.0 * core2));
                float halo = ALBEDO_STAR_HALO * scale * sqrt(flux), halo2 = halo * halo + blur2;
                float q = 1.0 + d2 / halo2;
                glow += flux * ALBEDO_STAR_HALO_LIGHT * halo * halo / halo2 / (q * q);
                vec3 detail = vec3(0.0);
                if (bright) {
                    detail = albedoStarRim(flux, dist, blur2, scale);
                    // The spikes keep the length of the still star and change with the root of the twinkle, so that they never flicker on and off.
                    if (spikes > 0.0 && still > ALBEDO_STAR_SPIKE_FLUX) {
                        detail += albedoSpikes(s, d, still, maxFlux, spikes * sqrt(flux / still), blur2, scale);
                    }
                }
                vec3 color = albedoStarColor((float(hb & 4095u) + 0.5) / 4096.0) * vec3(1.0 + tint, 1.0, 1.0 - tint);
                // The light goes to 0 before the edge of the reach, so that no star is cut.
                float fade = 1.0 - albedoSmooth(0.8 * reach, reach, dist);
                light += color * fade * (glow + detail);
            }
        }
    }
    return light;
}

// SkyStars.trails: the light of the trails of the stars of one grid at p, each the arc of its star around the axis trail.xyz,
// which fades from the star to its end at the angle trail.w.
vec3 albedoStarTrails(vec3 p, float footprint, float cells, float minFlux, float maxFlux, float slope, int salt, float density,
        float scale, int seed, vec4 trail, float trailLight) {
    vec3 light = vec3(0.0);
    vec3 axis = trail.xyz;
    float n = cells / scale;
    // The point as its height along the axis and its offset across it, the radius of its circle around the axis.
    float h = dot(p, axis);
    vec3 r = p - axis * h;
    float radius = length(r);
    if (radius < 1e-5) return light;
    // The side vector goes along the circle, so that the point turned back by an angle a is h axis + cos(a) r - sin(a) side.
    vec3 side = cross(axis, r);
    float core = ALBEDO_STAR_CORE * scale, blur = ALBEDO_STAR_TRAIL_PIXEL * footprint;
    float width2 = core * core + blur * blur, width = sqrt(width2), cut = ALBEDO_STAR_TRAIL_REACH * width;
    float angle = atan(radius, h);
    // Each part of the arc is at most one cell long, so that the box of cells around its chord is small.
    int parts = int(ceil(trail.w * radius * n));
    if (parts < 1) parts = 1;
    float step = trail.w / float(parts);
    float margin = (cut + radius * (1.0 - cos(step * 0.5))) * n + 0.01;
    float gain = trailLight * ALBEDO_STAR_TRAIL_GAIN * core / width;
    int hashSeed = seed * 31 + salt;
    vec3 last = p * n;
    for (int part = 0; part < parts; part++) {
        float a = float(part + 1) * step;
        vec3 q = (axis * h + r * cos(a) - side * sin(a)) * n;
        ivec3 lo = ivec3(floor(min(last, q) - margin)), hi = ivec3(floor(max(last, q) + margin));
        for (int x = lo.x; x <= hi.x; x++) {
            for (int y = lo.y; y <= hi.y; y++) {
                for (int z = lo.z; z <= hi.z; z++) {
                    ivec3 cell = ivec3(x, y, z);
                    uint hb = albedoHash(cell, hashSeed);
                    if (float(hb >> 24u) / 256.0 >= density) continue;
                    vec3 s;
                    if (!albedoCellPoint(cell, albedoHash(cell, hashSeed + 1), n, s)) continue;
                    // The height difference is never more than the angle difference, so a star that it puts far away is far away.
                    float sh = dot(s, axis);
                    if (abs(sh - h) >= cut) continue;
                    float back = atan(-dot(s, side), dot(s, r));
                    if (back <= 0.0 || back >= trail.w) continue;
                    // Only the part whose piece of the arc holds the star adds it, so that no star adds its trail twice.
                    int owner = int(back / step);
                    if (owner > parts - 1) owner = parts - 1;
                    if (owner != part) continue;
                    float flux = min(maxFlux, minFlux * pow((float((hb >> 12u) & 4095u) + 0.5) / 4096.0, -1.0 / slope));
                    float d = atan(sqrt(max(0.0, 1.0 - sh * sh)), sh) - angle;
                    float across = exp(-d * d / (2.0 * width2)) * (1.0 - albedoSmooth(0.75 * cut, cut, abs(d)));
                    float x1 = 1.0 - back / trail.w;
                    // The trail starts in the core of the star and fades to nothing at its end.
                    float start = albedoSmooth(0.0, 3.0 * width, back * radius);
                    light += albedoStarColor((float(hb & 4095u) + 0.5) / 4096.0) * (gain * sqrt(flux) * across * x1 * x1 * start);
                }
            }
        }
        last = q;
    }
    return light;
}

// Stars with the brightness spread and the colors of real stars, where AMOUNT is the count and HEIGHT is the strength of the spikes.
vec4 albedoStars(int layer, vec3 p, float footprint, int flags) {
    vec4 values = albedoLayer(layer, 1);
    vec4 shape = albedoLayer(layer, 2);
    int seed = int(shape.y);
    float m = 0.2 + 0.8 * values.z;
    float density = m * m, spikes = (flags & ALBEDO_FLAG_SPIKES) != 0 ? values.w * 2.0 : 0.0;
    // The stars twinkle at the time of the header, and at time 0 in a still sky, as in the bake.
    float twinkle = (flags & ALBEDO_FLAG_TWINKLE) != 0 ? albedoLayer(layer, 4).w : 0.0, time = max(SkyHeader.w, 0.0);
    // The world zenith in the layer frame, because the stars twinkle most in the thick air near the world horizon.
    vec3 zenith = SkyWorld[1].xyz;
    vec3 up = vec3(dot(albedoLayer(layer, 7).xyz, zenith), dot(albedoLayer(layer, 8).xyz, zenith), dot(albedoLayer(layer, 9).xyz, zenith));
    // The trail axis in the free last floats of the matrix rows, and the trail arc in radians, 0 for no trails.
    vec4 trail = (flags & ALBEDO_FLAG_TRAILS) != 0
            ? vec4(albedoLayer(layer, 7).w, albedoLayer(layer, 8).w, albedoLayer(layer, 9).w, albedoLayer(layer, 5).w) : vec4(0.0);
    // Only the bright stars twinkle, because the twinkle of a faint star is too small to see.
    vec3 light = albedoStarTier(p, footprint, 88.0, 0.04, 0.9, 1.3, 7, false, density, spikes, shape.x, seed, 0.0, time, up, trail);
    light += albedoStarTier(p, footprint, 16.0, 0.9, 60.0, 1.2, 13, true, density, spikes, shape.x, seed, twinkle, time, up, trail);
    // Only the bright stars draw trails, because the trail of a faint star is too faint to see.
    if (trail.w > 0.0) {
        light += albedoStarTrails(p, footprint, 16.0, 0.9, 60.0, 1.2, 13, density, shape.x, seed, trail, albedoLayer(layer, 6).w);
    }
    vec4 c = albedoLightAlpha(light * albedoLayer(layer, 3).rgb, albedoLayer(layer, 3).rgb);
    return vec4(c.rgb, c.a * shape.z);
}

// The slow noise of SkyGenerator.pulse that sets the regions of a cloud layer and their phases.
float albedoPulseNoise(int layer, vec3 p) {
    vec4 shape = albedoLayer(layer, 2);
    return albedoNoise(p * (2.2 / shape.x), int(shape.y) + 71);
}

// SkyGenerator.pulse: the factor of the color of a cloud layer that gets brighter and darker in some regions, each at its own phase,
// from the pulse noise n.
float albedoPulse(int layer, float n, float v) {
    float amount = albedoLayer(layer, 4).w;
    if (amount <= 0.0) return 1.0;
    float region = albedoSmooth(-0.35, 0.0, n);
    if (region <= 0.0) return 1.0;
    // One slow noise sets the regions, their phases and the weights of the three waves, so that each region has its own rhythm.
    float phase = 1.5 * n, m = 0.5 + 0.5 * sin(9.0 * n);
    // The clouds pulse at the time of the header, and at time 0 in a still sky, as in the bake.
    float time = max(SkyHeader.w, 0.0);
    float wave = (1.0 - m) * (1.0 - m) * albedoWave(1.0 / 4.5, phase, time)
            + 2.0 * m * (1.0 - m) * albedoWave(1.0 / 5.625, phase * 1.3, time) + m * m * albedoWave(1.0 / 7.2, phase * 1.6, time);
    // The square of the amount gives fine steps at a low pulse and a very strong pulse at 100 percent.
    return exp(amount * amount * 2.4 * region * wave * (0.4 + 0.6 * v));
}

// SkyGenerator.brighten: a linear color times a factor, where a color that gets brighter goes smoothly up to a peak channel of 1, so that it keeps its hue.
vec3 albedoBrighten(vec3 c, float k) {
    float peak = max(c.r, max(c.g, c.b));
    if (k > 1.0 && peak > 0.0) {
        float room = max(1.0 - peak, 1e-4);
        k = (peak + room * (1.0 - exp(-peak * (k - 1.0) / room))) / peak;
    }
    return c * k;
}

// The smooth values of a cloud layer that do not change in time, which its field image holds when it has one: the cloud value of
// the coarse octaves and the pulse noise.
vec4 albedoCloudsField(int layer, vec3 p, int flags) {
    vec4 shape = albedoLayer(layer, 2);
    float v = albedoFbmPart(p * (2.5 / shape.x), int(shape.y), 0, ALBEDO_CLOUD_FIELD_OCTAVES, ALBEDO_CLOUD_OCTAVES) * 0.5 + 0.5;
    return vec4(v, (flags & ALBEDO_FLAG_PULSE) != 0 ? albedoPulseNoise(layer, p) : 0.0, 0.0, 0.0);
}

// Clouds of fractal noise from COLOR_1 to COLOR_2, where AMOUNT is the part of the sky they cover, with the fine octaves for each
// pixel added to albedoCloudsField.
vec4 albedoClouds(int layer, vec3 p, vec4 field, int flags) {
    vec4 values = albedoLayer(layer, 1);
    vec4 shape = albedoLayer(layer, 2);
    float v = field.x + albedoFbmPart(p * (2.5 / shape.x), int(shape.y), ALBEDO_CLOUD_FIELD_OCTAVES, ALBEDO_CLOUD_OCTAVES,
            ALBEDO_CLOUD_OCTAVES) * 0.5;
    vec4 c = albedoMix(layer, v);
    float edge = 1.0 - values.z * 1.2;
    c.a *= albedoSmooth(edge - 0.12, edge + 0.12, v);
    if ((flags & ALBEDO_FLAG_PULSE) != 0) c.rgb = albedoBrighten(c.rgb, albedoPulse(layer, field.y, v));
    return c;
}

// The smooth values of a fractal layer, which its field image holds when it has one: the bend of the ridges and the pulse noise.
vec4 albedoFractalField(int layer, vec3 p, int flags) {
    vec4 shape = albedoLayer(layer, 2);
    int seed = int(shape.y);
    vec3 q = p * (1.6 / shape.x);
    vec3 bend = vec3(albedoFbm(q + vec3(5.2, 1.3, 2.8), seed + 11, 4), albedoFbm(q + vec3(1.7, 9.2, 4.4), seed + 23, 4),
            albedoFbm(q + vec3(8.3, 2.8, 7.1), seed + 37, 4));
    return vec4(bend, (flags & ALBEDO_FLAG_PULSE) != 0 ? albedoPulseNoise(layer, p) : 0.0);
}

// Thin bright lines of ridged noise, bent by more noise, where AMOUNT is how much they bend, with the sharp ridges for each pixel
// and the smooth values from albedoFractalField.
vec4 albedoFractal(int layer, vec3 p, vec4 field, int flags) {
    vec4 values = albedoLayer(layer, 1);
    vec4 shape = albedoLayer(layer, 2);
    vec3 q = p * (1.6 / shape.x);
    float v = albedoRidged(q + values.z * 1.6 * field.xyz, int(shape.y), 6);
    v = v * v;
    vec4 c = albedoMix(layer, v);
    c.a *= clamp(v * 1.6, 0.0, 1.0);
    if ((flags & ALBEDO_FLAG_PULSE) != 0) c.rgb = albedoBrighten(c.rgb, albedoPulse(layer, field.w, v));
    return c;
}

// The numbers of Galaxy, in galaxy units where 1 is about the radius of the disc.
const float ALBEDO_GALAXY_REACH = 3.0;
const float ALBEDO_GALAXY_JET_LENGTH = 1.4;
const float ALBEDO_GALAXY_KNOT_CELL = 0.05;
// How much more the dust absorbs of each linear channel than of green, so that the light through it gets redder.
const vec3 ALBEDO_GALAXY_DUST_REDDENING = vec3(0.75, 1.0, 1.3);
// The linear colors of the pink knots of hydrogen gas, of the red and magenta radio lobes and of the white nucleus.
const vec3 ALBEDO_GALAXY_HALPHA = vec3(1.0, 0.1441, 0.2705);
const vec3 ALBEDO_GALAXY_LOBE = vec3(0.9, 0.1, 0.35);
const vec3 ALBEDO_GALAXY_NUCLEUS = vec3(1.0, 0.9047, 0.7913);
// The light of the large halo of old stars, and the distance between the rings of a lobe for each unit of the length of its jet.
const float ALBEDO_GALAXY_HALO = 0.4;
const float ALBEDO_GALAXY_LOBE_RING = 0.16;
// The numbers of the detail of Galaxy: the radius of the circle of noise of the dust threads, the grid cells of
// the gas regions and of the clusters, and the radius of the circle that a line of moving noise goes around.
const float ALBEDO_GALAXY_DUST_THREADS = 1.3;
const float ALBEDO_GALAXY_GAS_CELL = 0.1;
const float ALBEDO_GALAXY_CLUSTER_CELL = 0.08;
const float ALBEDO_GALAXY_LOOP = 4.0;
// The linear colors of the detail: the end of a jet, the rim of a lobe, a hot spot, a bow shock, the ionized cones and the heart of a gas region.
const vec3 ALBEDO_GALAXY_JET_END = vec3(0.5, 0.38, 1.0);
const vec3 ALBEDO_GALAXY_LOBE_RIM = vec3(1.0, 0.3, 0.16);
const vec3 ALBEDO_GALAXY_HOT_SPOT = vec3(0.75, 0.88, 1.0);
const vec3 ALBEDO_GALAXY_BOW_SHOCK = vec3(1.0, 0.55, 0.75);
const vec3 ALBEDO_GALAXY_ION_CONE = vec3(0.1, 0.85, 0.6);
const vec3 ALBEDO_GALAXY_GAS_CORE = vec3(1.0, 0.62, 0.72);
// The factors of the core color at the center of the bulge and far out, and the part of the light that thin dust gives back.
const vec3 ALBEDO_GALAXY_BULGE_CORE = vec3(1.0, 1.1, 1.25);
const vec3 ALBEDO_GALAXY_BULGE_EDGE = vec3(1.0, 0.82, 0.62);
const vec3 ALBEDO_GALAXY_DUST_GLOW = vec3(1.0, 0.4, 0.14);
// The linear colors of a stream of stars and of a new star that explodes.
const vec3 ALBEDO_GALAXY_STREAM = vec3(0.75, 0.8, 1.0);
const vec3 ALBEDO_GALAXY_SUPERNOVA = vec3(0.8, 0.9, 1.0);
// The turn of the loop of the stream against the disc and the place of its dwarf galaxy on the loop, in radians, and the long axis of the loop.
const float ALBEDO_GALAXY_STREAM_TURN = 0.6;
const float ALBEDO_GALAXY_STREAM_HEAD = 3.6;
const float ALBEDO_GALAXY_STREAM_LONG = 1.8;

// SkyMath.gauss: a bell curve of height 1 at 0.
float albedoGauss(float v) {
    return exp(-v * v);
}

// Galaxy.turns: the turns at a time of a motion that makes a whole number of turns in the hour of the sky clock.
float albedoTurns(float perHour, float motion, float time) {
    return floor(perHour * motion + 0.5) * time / 3600.0;
}

// Galaxy.loop: noise along a line at u that goes around a circle, where one turn moves it once around, so that it comes back to its start.
float albedoLoop(float u, float turns, float lane, int seed) {
    float a = u / ALBEDO_GALAXY_LOOP - 2.0 * ALBEDO_PI * fract(turns);
    return albedoNoise(vec3(ALBEDO_GALAXY_LOOP * cos(a), ALBEDO_GALAXY_LOOP * sin(a), lane), seed);
}

// Galaxy.jitter: the offset of the grid place g from the jittered center of the cell h, times the cell size, with the hash
// salts seed and seed + 1.
vec2 albedoJitter(vec2 g, ivec3 h, int seed, float cell) {
    return vec2(g.x - float(h.x) - 0.2 - 0.6 * albedoUnit(albedoHash(h, seed)), g.y - float(h.y) - 0.2 - 0.6 * albedoUnit(albedoHash(h, seed + 1)))
            * cell;
}

// Galaxy.clusters: the globular clusters of a galaxy, small soft points around its halo, more of them near the bulge.
vec3 albedoGalaxyClusters(float x, float y, float fp, float detail, int seed) {
    if (fp >= 0.5 * ALBEDO_GALAXY_CLUSTER_CELL) return vec3(0.0);
    vec2 g = vec2(x, y) / ALBEDO_GALAXY_CLUSTER_CELL;
    ivec2 cell = ivec2(floor(g));
    float sum = 0.0;
    for (int i = -1; i <= 1; i++) {
        for (int j = -1; j <= 1; j++) {
            ivec3 h = ivec3(cell.x + i, cell.y + j, 0);
            float rc = length(vec2(h.xy) + 0.5) * ALBEDO_GALAXY_CLUSTER_CELL;
            if (rc < 0.22 || albedoUnit(albedoHash(h, seed + 941)) > 0.8 * exp(-rc / 0.7)) continue;
            vec2 d = albedoJitter(g, h, seed + 942, ALBEDO_GALAXY_CLUSTER_CELL);
            float wide = max(0.0035, 0.75 * fp), d2 = (d.x * d.x + d.y * d.y) / (wide * wide);
            sum += (0.1 + 0.3 * albedoUnit(albedoHash(h, seed + 944))) * (0.0035 / wide) * (0.0035 / wide)
                    * (exp(-d2) + 0.12 / (1.0 + d2 / 5.0) * exp(-d2 / 40.0));
        }
    }
    sum *= detail * (1.0 - albedoSmooth(0.25 * ALBEDO_GALAXY_CLUSTER_CELL, 0.5 * ALBEDO_GALAXY_CLUSTER_CELL, fp));
    return sum * ALBEDO_GALAXY_NUCLEUS;
}

// Galaxy.stream: a stream of stars on part of a loop around a galaxy, wider and fainter behind the dwarf galaxy that it comes from.
vec3 albedoGalaxyStream(float x, float y, float fp, float detail, int seed, vec3 color2) {
    float c = cos(ALBEDO_GALAXY_STREAM_TURN), s = sin(ALBEDO_GALAXY_STREAM_TURN);
    float ux = (c * x + s * y) / ALBEDO_GALAXY_STREAM_LONG, uy = -s * x + c * y;
    float e = sqrt(ux * ux + uy * uy);
    // The angle at the exact center is not defined in GLSL, and the stream has no light there.
    float th = e > 0.0 ? atan(uy, ux) : 0.0;
    float a = fract((th - ALBEDO_GALAXY_STREAM_HEAD) / (2.0 * ALBEDO_PI));
    float wide = 0.03 + 0.1 * a;
    float stream = detail * 0.048 * albedoGauss((e - 1.0) / wide) * (1.0 - albedoSmooth(0.05, 0.75, a)) * 0.03 / wide
            * (0.55 + 0.6 * albedoNoise(vec3(th * 5.0, e * 6.0, 2.0), seed + 821));
    float dx = (ux - cos(ALBEDO_GALAXY_STREAM_HEAD)) * ALBEDO_GALAXY_STREAM_LONG, dy = uy - sin(ALBEDO_GALAXY_STREAM_HEAD);
    float point = max(0.008, fp);
    float dwarf = detail * (0.16 * exp(-sqrt(dx * dx + 2.25 * dy * dy) / 0.035)
            + 0.3 * (0.008 / point) * (0.008 / point) * albedoGauss(sqrt(dx * dx + dy * dy) / point));
    return stream * ALBEDO_GALAXY_STREAM + dwarf * color2;
}

// Galaxy.supernova: the one new star that explodes in the disc in each slot of time, with a fast rise and a slow fall.
vec3 albedoGalaxySupernova(float x, float y, float qs, float fp, float motion, float time, int seed) {
    float slots = floor(8.0 + 72.0 * motion + 0.5), period = 3600.0 / slots;
    float slot = min(slots - 1.0, floor(time / period)), age = time - slot * period;
    float rise = albedoSmooth(0.0, 0.4, age) * exp(-max(age - 0.4, 0.0) / 1.6);
    if (rise < 1e-4) return vec3(0.0);
    ivec3 h = ivec3(int(slot), 3, 0);
    float a = 6.2831853 * albedoUnit(albedoHash(h, seed + 701));
    float r = 0.25 + 0.6 * albedoUnit(albedoHash(h, seed + 702));
    float dx = x - r * cos(a), dy = y - r * sin(a) * qs;
    float wide = max(0.006, fp), d2 = (dx * dx + dy * dy) / (wide * wide);
    return 2.5 * rise * (0.006 / wide) * (0.006 / wide) * (exp(-d2) + 0.04 / (1.0 + d2 / 9.0)) * ALBEDO_GALAXY_SUPERNOVA;
}

// Galaxy.Parts: the values of one pixel that the parts of a galaxy share, and the light of each part.
struct AlbedoGalaxyParts {
    int seed;
    float dust, jets;
    vec3 color1, color2, color3;
    // The place in galaxy units, its distance from the center, and the width of one pixel in galaxy units.
    float x, y, rr, fp;
    float detail, motion, time;
    // The cosine and sine of the tilt, the axis ratios of the stars and the dust, and the place and distance in each disc frame.
    float ci, si, qs, qd, bs, bd, rs, rd;
    float twist, warp, wave, arm, edge, clumps;
    float star, armMix, tau, knots, pink, gasRegions;
    float bulge, nucleus;
    float jet, clear, lobe, cool, gas;
    vec3 jetColor, lobeColor, extra;
};

// Galaxy.dust: the optical depth of the dust of the disc, a lane over the center, clumps along the arms and, with detail, threads.
void albedoGalaxyDust(inout AlbedoGalaxyParts g) {
    float thetaD = g.rd > 0.0 ? atan(g.bd, g.x) : 0.0;
    float lane = 0.5 + 0.5 * cos(2.0 * (thetaD - g.twist * log(g.rd + 0.05)) + 1.4 * g.warp + 0.7);
    lane *= lane;
    float clump = albedoFbmSoft(vec3(g.x * 6.0, g.bd * 6.0, 1.7), g.seed + 2, 5, g.fp / g.qd * 6.0) * 0.5 + 0.5;
    // A dense lane of dust in the plane of the disc over the center, and more dust in clumps along the inner edge of each arm.
    float density = (1.0 - albedoSmooth(0.9, 1.4, g.rd)) * (0.9 * exp(-g.rd / 0.45) * (0.5 + clump)
            + exp(-g.rd / 0.5) * 2.0 * lane * lane * max(0.0, 2.0 * clump - 0.8));
    g.tau = g.dust * 7.0 * density / sqrt(g.qd);
    if (g.detail > 0.0) {
        // Threads of dust from noise in a frame that turns with the arms, so that they run along the arms and make no web.
        float along = log(g.rd + 0.05), phi = thetaD - g.twist * along;
        float threads = albedoFbmSoft(vec3(ALBEDO_GALAXY_DUST_THREADS * cos(phi), ALBEDO_GALAXY_DUST_THREADS * sin(phi), 1.6 * along),
                g.seed + 31, 4, g.fp / g.qd * 5.0 * ALBEDO_GALAXY_DUST_THREADS / (g.rd + 0.1));
        float fine = albedoFbmSoft(vec3(g.x * 16.0, g.bd * 16.0, 5.1), g.seed + 32, 3, g.fp / g.qd * 16.0);
        g.tau *= max(0.2, 1.0 + g.detail * (0.8 * threads + 0.3 * fine));
    }
}

// Galaxy.knots: knots of young stars on a grid of the image plane, few of them bright, which show only on the arms.
void albedoGalaxyKnots(inout AlbedoGalaxyParts g) {
    vec2 grid = vec2(g.x, g.y) / ALBEDO_GALAXY_KNOT_CELL;
    ivec2 cell = ivec2(floor(grid));
    float knots = 0.0, pink = 0.0;
    for (int i = -1; i <= 1; i++) {
        for (int j = -1; j <= 1; j++) {
            ivec3 h = ivec3(cell.x + i, cell.y + j, 0);
            if (albedoUnit(albedoHash(h, g.seed + 77)) > 0.5) continue;
            vec2 d = albedoJitter(grid, h, g.seed + 78, ALBEDO_GALAXY_KNOT_CELL);
            float big = albedoUnit(albedoHash(h, g.seed + 80));
            float radius = 0.003 + 0.006 * big;
            float spread = max(radius, 0.8 * g.fp);
            float v = (0.15 + 2.5 * big * big * big) * radius / spread * (radius / spread) * exp(-(d.x * d.x + d.y * d.y) / (spread * spread));
            knots += v;
            if (albedoUnit(albedoHash(h, g.seed + 81)) < 0.35) pink += v;
        }
    }
    // The knots are in groups, and fade out before a pixel is half a cell wide, where the search of 3 x 3 cells would cut a knot.
    float group = albedoSmooth(-0.05, 0.35, albedoNoise(vec3(g.x * 5.0, g.bs * 5.0, 2.9), g.seed + 82));
    float k = g.arm * group * exp(-g.rs / 0.5) * g.edge * 1.5
            * (1.0 - albedoSmooth(0.25 * ALBEDO_GALAXY_KNOT_CELL, 0.5 * ALBEDO_GALAXY_KNOT_CELL, g.fp));
    g.knots = knots * k;
    g.pink = pink * k;
}

// Galaxy.gasRegions: large regions of glowing hydrogen where the arms make stars, each with a faint glow around it.
void albedoGalaxyGasRegions(inout AlbedoGalaxyParts g) {
    vec2 grid = vec2(g.x, g.y) / ALBEDO_GALAXY_GAS_CELL;
    ivec2 cell = ivec2(floor(grid));
    float gas = 0.0;
    for (int i = -1; i <= 1; i++) {
        for (int j = -1; j <= 1; j++) {
            ivec3 h = ivec3(cell.x + i, cell.y + j, 0);
            if (albedoUnit(albedoHash(h, g.seed + 90)) > 0.55) continue;
            vec2 d = albedoJitter(grid, h, g.seed + 91, ALBEDO_GALAXY_GAS_CELL);
            float big = albedoUnit(albedoHash(h, g.seed + 93));
            float radius = 0.006 + 0.014 * big * big, spread = max(radius, 0.8 * g.fp);
            float d2 = (d.x * d.x + d.y * d.y) / (spread * spread);
            gas += (0.3 + 1.2 * big) * radius / spread * (radius / spread) * (exp(-d2) + 0.25 * exp(-d2 / 9.0));
        }
    }
    gas *= g.detail * albedoSmooth(0.2, 0.7, g.arm) * exp(-g.rs / 0.75) * g.edge * 1.3
            * (1.0 - albedoSmooth(0.25 * ALBEDO_GALAXY_GAS_CELL, 0.5 * ALBEDO_GALAXY_GAS_CELL, g.fp));
    g.gasRegions = gas;
}

// Galaxy.disc: the disc of stars with its arms, its dust and its knots, and with detail its grain, the ring at its edge and its gas regions.
void albedoGalaxyDisc(inout AlbedoGalaxyParts g) {
    g.warp = albedoFbmSoft(vec3(g.x * 3.0, g.bs * 3.0, 0.37), g.seed + 1, 7, g.fp / g.qs * 3.0);
    // The angle at the exact center is not defined in GLSL, and the arms have no weight there.
    float theta = g.rs > 0.0 ? atan(g.bs, g.x) : 0.0;
    g.wave = 0.5 + 0.5 * cos(2.0 * (theta - g.twist * log(g.rs + 0.05)) + 1.4 * g.warp);
    // The arms start out of the smooth inner disc, so that the center has no curl.
    g.arm = g.wave * g.wave * g.wave * albedoSmooth(0.2, 0.6, g.rs);
    g.edge = 1.0 - albedoSmooth(1.1, 1.6, g.rs);
    g.clumps = albedoSmooth(-0.4, 0.5, g.warp);
    g.star = exp(-g.rs / 0.4) * (0.08 + 2.0 * g.arm * (0.2 + 0.8 * g.clumps)) * g.edge;
    g.armMix = clamp(g.arm * 2.5, 0.0, 1.0);
    if (g.dust > 0.0 && g.rd < 1.4) albedoGalaxyDust(g);
    if (g.fp < 0.5 * ALBEDO_GALAXY_KNOT_CELL) albedoGalaxyKnots(g);
    if (g.detail > 0.0) {
        // The grain of single bright stars in the arms, and a ring of young stars at the edge of the disc.
        g.star *= 1.0 + g.detail * 0.45 * albedoNoise(vec3(g.x * 46.0, g.bs * 46.0, 9.1), g.seed + 41)
                * (1.0 - albedoSmooth(0.25, 0.5, g.fp / g.qs * 46.0));
        float ring = albedoGauss((g.rs - 1.08) / 0.1) * (0.25 + g.clumps) * (0.5 + 0.5 * g.wave);
        g.star += g.detail * 0.09 * ring;
        g.armMix = max(g.armMix, clamp(g.detail * ring * 1.5, 0.0, 1.0));
        if (g.fp < 0.5 * ALBEDO_GALAXY_GAS_CELL) albedoGalaxyGasRegions(g);
    }
}

// Galaxy.shells: the light of the bulge in its halo, and with detail the shells of the halo, the globular clusters and the stream.
void albedoGalaxyShells(inout AlbedoGalaxyParts g) {
    float x = g.x, y = g.y;
    float rb = sqrt(x * x + y * y / 0.64);
    // A small bright bulge in a large smooth halo of old stars, slightly longer along the jets, as the elliptical galaxy of Centaurus A.
    float rh = sqrt(x * x / 0.72 + y * y);
    g.bulge = 1.8 * exp(-5.0 * sqrt(rb)) + ALBEDO_GALAXY_HALO * exp(-3.2 * sqrt(rh)) * (1.0 - albedoSmooth(1.5, 3.0, g.rr));
    if (g.detail <= 0.0) return;
    // Shells of stars in the halo on alternate sides of the disc, the rest of a small galaxy that fell in.
    float thH = g.rr > 0.0 ? atan(y, x) : 0.0;
    for (int k = 0; k < 3; k++) {
        float radius = 1.25 + 0.36 * float(k);
        float toward = albedoSmooth(0.45, 0.95, cos(thH - float(k % 2) * ALBEDO_PI - 0.3 * float(k)));
        float rsh = rh * (1.0 + 0.07 * albedoNoise(vec3(thH * 2.0, float(k), 1.0), g.seed + 50));
        float patchy = 0.6 + 0.6 * albedoNoise(vec3(x * 5.0, y * 5.0, float(k) + 3.0), g.seed + 51);
        g.bulge += g.detail * toward * patchy * exp(-max(radius - rsh, 0.0) / 0.1) * (1.0 - albedoSmooth(radius, radius + 0.05, rsh))
                * (0.011 - 0.003 * float(k));
    }
    g.extra += albedoGalaxyClusters(x, y, g.fp, g.detail, g.seed);
    g.extra += albedoGalaxyStream(x, y, g.fp, g.detail, g.seed, g.color2);
}

// Galaxy.nucleus: the light of the nucleus, which flickers with motion.
void albedoGalaxyNucleus(inout AlbedoGalaxyParts g) {
    float rr = g.rr, rn = max(0.006, g.fp);
    float nucleus = 2.5 * (0.006 / rn) * (0.006 / rn) * exp(-rr * rr / (rn * rn)) + 0.12 / (1.0 + rr * rr / 0.0009);
    nucleus *= 1.0 + 0.5 * g.detail;
    // The nucleus flickers at two frequencies that are whole numbers of turns in the hour of the sky clock.
    if (g.motion > 0.0) nucleus *= 1.0 + g.motion * (0.16 * albedoWave(1320.0 / 3600.0, 0.0, g.time) + 0.1 * albedoWave(3384.0 / 3600.0, 0.0, g.time));
    g.nucleus = nucleus;
}

// Galaxy.jetsAndLobes: the jets along the axis of the disc and the lobes at their ends, and with detail the hot spots, bow shocks and cones.
void albedoGalaxyJetsAndLobes(inout AlbedoGalaxyParts g) {
    g.jetColor = g.color3;
    g.lobeColor = ALBEDO_GALAXY_LOBE;
    g.jet = 0.0;
    g.clear = 0.0;
    g.lobe = 0.0;
    g.cool = 0.0;
    g.gas = 0.0;
    if (g.jets <= 0.0) return;
    float x = g.x, y = g.y, fp = g.fp, detail = g.detail, motion = g.motion, time = g.time;
    int seed = g.seed;
    float side = y < 0.0 ? -1.0 : 1.0;
    float reach = ALBEDO_GALAXY_JET_LENGTH * (0.3 + 0.7 * g.si);
    float t = abs(y) / reach;
    // The jets bend in opposite directions, as a precessing jet does, and the near jet is brighter from relativistic beaming.
    float dx = x - side * 0.12 * t * t - 0.03 * t * albedoNoise(vec3(t * 5.0, side * 3.1, 0.6), seed + 502);
    float beam = side > 0.0 ? 1.0 : 0.3 + 0.15 * detail;
    // The part of the jet light that the dust does not dim: the far jet is behind the disc, and the near jet starts in it.
    g.clear = side > 0.0 ? albedoSmooth(0.02, 0.12, t) : 0.0;
    // Each jet is a cone that opens out, and its light goes into the lobe.
    float width = 0.005 + 0.03 * t;
    float spread = max(width, 0.8 * fp);
    float fade = 1.0 - albedoSmooth(0.25, 0.5, 16.0 / reach * fp);
    // With motion the knots go out along the jet, on a line of noise that comes back to its start in the hour of the sky clock.
    float knotNoise = motion > 0.0 ? albedoLoop(t * 16.0, albedoTurns(130.0, motion, time), side * 7.3, seed + 501)
            : albedoNoise(vec3(t * 16.0, side * 7.3, 1.7), seed + 501);
    float knot = max(0.0, 0.55 + 0.7 * fade * knotNoise);
    float tip = 1.0 - albedoSmooth(0.6, 1.05, t);
    float core = exp(-dx * dx / (spread * spread));
    float jet = 0.0;
    if (detail > 0.0) {
        // The beam sways from side to side at no fixed step, a fainter strand crosses it, and shocks make some knots brighter.
        float sway = albedoLoop(t * 9.0, albedoTurns(73.0, motion, time), side * 4.7, seed + 505);
        float cross = albedoLoop(t * 13.0, albedoTurns(106.0, motion, time), side * 2.9, seed + 506);
        float shock = max(0.0, albedoLoop(t * 11.0, albedoTurns(89.0, motion, time), side * 1.9, seed + 507));
        float dm = dx - detail * 1.6 * width * sway * fade, ds = dx + detail * 2.2 * width * cross * fade;
        float thin = max(0.6 * width, 0.8 * fp);
        core = exp(-dm * dm / (spread * spread)) + detail * 0.45 * 0.6 * width / thin * exp(-ds * ds / (thin * thin));
        knot *= 1.0 + detail * 4.0 * shock * shock * fade * (1.0 - albedoSmooth(0.5, 0.95, t));
        // A faint sheath of gas around the beam.
        jet = detail * 0.12 * beam * exp(-dx * dx / (9.0 * spread * spread)) / (1.0 + 3.0 * t) * tip;
        // The beam is white where it is hot at its start and violet where it cools at its end.
        float start = 1.0 - albedoSmooth(0.0, 0.3, t), end = 0.6 * albedoSmooth(0.55, 1.0, t);
        vec3 hot = g.color3 + (1.0 - g.color3) * 0.7 * start;
        g.jetColor += detail * (hot + (ALBEDO_GALAXY_JET_END - hot) * end - g.color3);
    }
    jet += 1.4 * beam * width / spread * core * knot / (1.0 + 3.0 * t) * tip;
    // With motion the turbulence of the lobes changes slowly, back and forth along the third axis of its noise.
    float drift = motion > 0.0 ? 0.8 * albedoWave(1.0, 0.0, albedoTurns(10.0, motion, time)) : 0.0;
    float turb = albedoFbmSoft(vec3(x * 2.4, y * 2.4, side * 5.1 + drift), seed + 601, 7, fp * 2.4);
    float thread = 1.0 - abs(turb);
    thread *= thread;
    thread *= thread;
    // A lobe is longer along the jet than across it, and the turbulence moves its edge.
    float lx = x - side * 0.19, ly = (y - side * reach * 1.3) / 1.3;
    float dl = sqrt(lx * lx + ly * ly) / (0.4 * reach) + 0.4 * turb;
    // Broken arcs of shells around a point on the jet, as the rings in the lobes of Hercules A, which fade before they get too thin.
    float sx = x - side * 0.14, sy = y - side * reach * 0.85;
    float bend = albedoNoise(vec3(x * 3.0, y * 3.0, side * 1.7), seed + 603);
    float shell = 0.5 + 0.5 * cos(sqrt(sx * sx + sy * sy) / (ALBEDO_GALAXY_LOBE_RING * reach) * 6.2831853 + 2.5 * bend);
    shell *= shell * albedoSmooth(-0.15, 0.25, albedoNoise(vec3(x * 4.0, y * 4.0, side * 2.3), seed + 604))
            * (1.0 - albedoSmooth(0.12, 0.25, fp / (ALBEDO_GALAXY_LOBE_RING * reach)));
    float hx = x - side * 0.12, hy = y - side * reach;
    float hot = 0.25 * exp(-(hx * hx + hy * hy) / 0.0016);
    float body = max(0.08, 0.2 + 0.35 * turb + 0.6 * thread) * (0.5 + 1.6 * shell);
    float lobe = (exp(-2.0 * dl * dl) * body * 0.35 + hot) * g.jets;
    // The inner part of a lobe, where the jet ends, takes some of the jet color.
    g.cool = 0.4 * (1.0 - albedoSmooth(0.1, 0.9, dl));
    // Gas near the jet that its beam lights, less far from the axis.
    float gas = 0.08 * thread * exp(-abs(dx) / (0.08 + 0.15 * t)) * albedoSmooth(0.1, 0.4, t) * (1.0 - albedoSmooth(0.9, 1.3, t));
    jet *= g.jets;
    gas *= g.jets;
    if (detail > 0.0) {
        float strength = detail * g.jets * (side > 0.0 ? 1.0 : 0.6);
        // Filaments in the lobe, and a rim where it pushes into the gas around it, which glows as embers.
        float fil = 1.0 - abs(albedoFbmSoft(vec3(x * 3.2, y * 3.2, side * 4.2 + drift), seed + 611, 6, fp * 3.2));
        fil *= fil * fil;
        fil *= fil;
        lobe += detail * g.jets * (exp(-1.6 * dl * dl) * fil * 0.3 + albedoGauss((dl - 0.95) / 0.2) * (0.015 + 0.09 * thread));
        g.lobeColor += (ALBEDO_GALAXY_LOBE_RIM - ALBEDO_GALAXY_LOBE) * (detail * 0.55 * albedoSmooth(0.5, 1.1, dl));
        // The hot spot where the jet ends, and the thin bow shock ahead of it.
        float spot = 0.0005 + fp * fp, h2 = (hx * hx + hy * hy) / spot;
        float hotSpot = strength * (0.9 * 0.0005 / spot * exp(-h2) + 0.1 / (1.0 + h2 / 8.0));
        float bx = x - side * 0.12, by = y - side * (reach - 0.08), br = sqrt(bx * bx + by * by);
        float ahead = albedoSmooth(0.45, 0.95, by * side / max(br, 1e-4));
        float thin = max(0.014, 0.8 * fp);
        float bow = strength * 0.13 * 0.014 / thin
                * albedoGauss((br - 0.19 - 0.02 * albedoNoise(vec3(bx * 6.0, by * 6.0, side * 2.0), seed + 612)) / thin)
                * ahead * (0.5 + 0.7 * thread);
        // Cones of gas beside the nucleus that its light ionizes.
        float angle = abs(dx) / (abs(y) + 0.03);
        float cone = strength * 0.2 * (1.0 - albedoSmooth(0.28, 0.62, angle)) * exp(-abs(y) / 0.32)
                * albedoSmooth(0.04, 0.14, abs(y)) * (0.35 + 0.9 * thread);
        g.extra += hotSpot * ALBEDO_GALAXY_HOT_SPOT + bow * ALBEDO_GALAXY_BOW_SHOCK + cone * ALBEDO_GALAXY_ION_CONE;
    }
    g.jet = jet;
    g.lobe = lobe;
    g.gas = gas;
}

// Galaxy.sample: an active galaxy at the layer center, modeled on Centaurus A and Hercules A, with a COLOR_2 bulge in a halo,
// COLOR_1 arms at TILT, dust and COLOR_3 jets, where DETAIL adds the fine parts and MOTION moves the jets, the nucleus and the
// lobes with the time.
vec4 albedoGalaxy(int layer, vec3 p, float footprint, int flags) {
    float zz = -p.z;
    if (zz < 0.2) return vec4(0.0);
    vec4 values = albedoLayer(layer, 1);
    vec4 shape = albedoLayer(layer, 2);
    AlbedoGalaxyParts g;
    g.seed = int(shape.y);
    float tilt = albedoLayer(layer, 4).w;
    g.dust = (flags & ALBEDO_FLAG_DUST) != 0 ? albedoLayer(layer, 5).w : 0.0;
    g.jets = (flags & ALBEDO_FLAG_JETS) != 0 ? albedoLayer(layer, 6).w : 0.0;
    // The detail is in the float of the height and the motion in the first float of the first OKLab color, which a galaxy does not read.
    g.detail = (flags & ALBEDO_FLAG_DETAIL) != 0 ? values.w : 0.0;
    g.motion = (flags & ALBEDO_FLAG_MOTION) != 0 ? albedoLayer(layer, 5).x : 0.0;
    // The galaxy moves at the time of the header, and is as at time 0 in a still sky, as in the bake.
    g.time = max(SkyHeader.w, 0.0);
    g.color1 = albedoLayer(layer, 3).rgb;
    g.color2 = albedoLayer(layer, 4).rgb;
    g.color3 = vec3(albedoLayer(layer, 7).w, albedoLayer(layer, 8).w, albedoLayer(layer, 9).w);
    // The plane of the image in galaxy units, where 1 is about the radius of the disc.
    float size = 0.22 * shape.x;
    g.x = p.x / zz / size;
    g.y = p.y / zz / size;
    g.rr = sqrt(g.x * g.x + g.y * g.y);
    if (g.rr > ALBEDO_GALAXY_REACH) return vec4(0.0);
    // The width of one pixel in galaxy units, where the plane of the image stretches by 1 / zz squared.
    g.fp = footprint / (size * zz * zz);
    float incl = tilt * (ALBEDO_PI / 2.0);
    g.ci = cos(incl);
    g.si = sin(incl);
    // The axis ratios of the disc of stars and of the thinner layer of dust, each with its own thickness.
    g.qs = sqrt(g.ci * g.ci + 0.0225 * g.si * g.si);
    g.qd = sqrt(g.ci * g.ci + 0.0036 * g.si * g.si);
    g.bs = g.y / g.qs;
    g.bd = g.y / g.qd;
    g.rs = sqrt(g.x * g.x + g.bs * g.bs);
    g.rd = sqrt(g.x * g.x + g.bd * g.bd);
    g.twist = 1.5 + values.z * 4.0;
    g.star = 0.0;
    g.armMix = 0.0;
    g.tau = 0.0;
    g.knots = 0.0;
    g.pink = 0.0;
    g.gasRegions = 0.0;
    g.extra = vec3(0.0);
    if (g.rs < 1.6) albedoGalaxyDisc(g);
    albedoGalaxyShells(g);
    albedoGalaxyNucleus(g);
    albedoGalaxyJetsAndLobes(g);
    if (g.motion > 0.0) g.extra += albedoGalaxySupernova(g.x, g.y, g.qs, g.fp, g.motion, g.time, g.seed);
    // Galaxy.compose: the light of all parts through the dust.
    // The part of the bulge light behind the dust, more on the near side of the disc, which is below its center, and most at the center.
    float s = 0.8 - g.y * 6.0 * g.si / max(g.ci, 0.25);
    float behind = 0.5 + 0.5 * s / sqrt(1.0 + s * s);
    vec3 extinction = g.tau * ALBEDO_GALAXY_DUST_REDDENING;
    vec3 through = exp(-extinction), mixed = 1.0 / (1.0 + 0.5 * extinction);
    vec3 disc = g.color2 + (g.color1 - g.color2) * g.armMix;
    // With detail the bulge is pale at its core and amber far out, and the dust gives back a warm part of the light that it takes.
    float graded = albedoSmooth(0.03, 0.9, g.rr), back = g.detail * 0.55 * g.tau * exp(1.0 - g.tau);
    vec3 bulgeColor = g.color2 * (1.0 + g.detail * (ALBEDO_GALAXY_BULGE_CORE + (ALBEDO_GALAXY_BULGE_EDGE - ALBEDO_GALAXY_BULGE_CORE) * graded - 1.0));
    vec3 taken = g.bulge * bulgeColor * behind * (1.0 - through);
    vec3 gasColor = ALBEDO_GALAXY_HALPHA + (ALBEDO_GALAXY_GAS_CORE - ALBEDO_GALAXY_HALPHA) * clamp(g.gasRegions, 0.0, 1.0);
    vec3 light = g.bulge * bulgeColor * (1.0 - behind * (1.0 - through)) + 0.9 * g.star * disc * mixed
            + ((g.knots - g.pink) * g.color1 + g.pink * ALBEDO_GALAXY_HALPHA + g.gasRegions * gasColor) * mixed
            + g.nucleus * sqrt(through) * ALBEDO_GALAXY_NUCLEUS
            + g.jet * g.jetColor * (through + (1.0 - through) * g.clear) + g.lobe * (g.lobeColor + (g.color3 - g.lobeColor) * g.cool)
            + g.gas * g.color3 + taken * back * ALBEDO_GALAXY_DUST_GLOW + g.extra;
    light *= max(shape.z, shape.w);
    // The light as a color of at most 1 and an alpha, so that Add gives the full light and Normal stays near it.
    float a = min(1.0, max(light.r, max(light.g, light.b)));
    return vec4(light / max(a, 1e-6), a);
}

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

// The slots of time of the meteors, as in SkyMeteors: the slot length, the meteors in each slot and the slots that can still show one.
const float ALBEDO_METEOR_SLOT = 1.0;
#define ALBEDO_METEOR_CHANNELS 3
#define ALBEDO_METEOR_SLOTS_BACK 2
// The slots in one period of SkyClock, after which the hashes repeat, so that the meteors in the sky go on across the wrap of the time.
#define ALBEDO_METEOR_PERIOD_SLOTS 3600
// The part of its life after which the trail of a meteor is gone; the head goes out at 1.
const float ALBEDO_METEOR_TRAIL_END = 1.15;

float albedoMeteorUnit(int slot, int channel, int value, int seed) {
    return albedoUnit(albedoHash(ivec3(albedoRepeat(slot, ALBEDO_METEOR_PERIOD_SLOTS), channel, value), seed));
}

// A line across a width in radians, made wider by half a pixel and darker by the same ratio, so that a thin line stays crisp.
float albedoMeteorLine(float d, float width, float footprint) {
    float w = sqrt(width * width + 0.25 * footprint * footprint);
    return exp(-(d / w) * (d / w)) * width / w;
}

// The linear light of the meteor of a slot and a channel in the world direction q, with a hot head and a trail that fades and cools.
vec3 albedoMeteor(int layer, int slot, int channel, vec3 q, float footprint) {
    int seed = int(albedoLayer(layer, 2).y) * 7 + 13;
    float amount = albedoLayer(layer, 1).z;
    float chance = (2.0 + 118.0 * amount * amount) * ALBEDO_METEOR_SLOT / 60.0 / float(ALBEDO_METEOR_CHANNELS);
    if (albedoMeteorUnit(slot, channel, 0, seed) >= chance) return vec3(0.0);
    float birth = float(slot) * ALBEDO_METEOR_SLOT + albedoMeteorUnit(slot, channel, 1, seed) * ALBEDO_METEOR_SLOT;
    bool fireball = albedoMeteorUnit(slot, channel, 2, seed) < 0.05;
    float duration = (0.3 + 0.7 * albedoMeteorUnit(slot, channel, 3, seed)) * (fireball ? 1.4 : 1.0);
    float age = SkyHeader.w - birth;
    float life = age / duration;
    if (life < 0.0 || life > ALBEDO_METEOR_TRAIL_END) return vec3(0.0);
    float speed = radians(8.0 + 32.0 * albedoLayer(layer, 4).w) * (0.7 + 0.6 * albedoMeteorUnit(slot, channel, 4, seed));
    float trail = radians(3.0 + 7.0 * albedoLayer(layer, 5).w) * (0.75 + 0.5 * albedoMeteorUnit(slot, channel, 5, seed))
            * (fireball ? 1.5 : 1.0);
    float b = albedoMeteorUnit(slot, channel, 6, seed);
    float brightness = fireball ? 2.5 : 0.5 + 0.5 * b * b;
    float azimuth = 2.0 * ALBEDO_PI * albedoMeteorUnit(slot, channel, 7, seed);
    float low = sin(radians(20.0)), high = sin(radians(75.0));
    float y = low + (high - low) * albedoMeteorUnit(slot, channel, 8, seed);
    float c = sqrt(1.0 - y * y);
    vec3 start = vec3(c * sin(azimuth), y, -c * cos(azimuth));
    // A shower meteor moves away from the radiant, the layer center: the -z axis of the layer frame.
    vec3 radiant = -albedoLayer(layer, 9).xyz;
    vec3 away = start * dot(start, radiant) - radiant;
    vec3 motion;
    if (albedoMeteorUnit(slot, channel, 9, seed) < albedoLayer(layer, 6).w && length(away) > 1e-3) {
        motion = normalize(away);
    } else {
        float turn = 2.0 * ALBEDO_PI * albedoMeteorUnit(slot, channel, 10, seed);
        vec3 east = vec3(start.z, 0.0, -start.x) / c;
        motion = east * cos(turn) + cross(start, east) * sin(turn);
    }
    float scale = fireball ? 1.6 : 1.0;
    float headWidth = radians(0.04) * scale;
    float trailWidth = radians(0.022) * scale;
    float halo = 4.0 * sqrt(headWidth * headWidth + 0.25 * footprint * footprint);
    float across = dot(q, cross(start, motion));
    if (abs(across) > 2.0 * halo) return vec3(0.0);
    float angle = atan(dot(q, motion), dot(q, start));
    float head = speed * min(age, duration);
    float behind = head - angle;
    if (behind < -2.0 * halo || behind > trail + 2.0 * halo) return vec3(0.0);
    float rise = albedoSmooth(0.0, 0.12, life);
    // The trail is white and brightest behind the head, and goes part of the way to the trail color and to nothing at its full length.
    float t = clamp(behind / trail, 0.0, 1.0);
    float soft = trailWidth + footprint;
    float along = albedoSmooth(-soft, soft, behind) * albedoSmooth(-soft, soft, angle) * (1.0 - t) * (1.0 - t);
    vec4 mixed = albedoMix(layer, 0.8 * albedoSmooth(0.1, 1.0, t));
    vec3 tint = (fireball ? mix(mixed.rgb, vec3(1.0, 0.42, 0.1), 0.45 * t) : mixed.rgb) * mixed.a;
    float trailLight = rise * (1.0 - albedoSmooth(0.7, ALBEDO_METEOR_TRAIL_END, life)) * brightness * 2.5;
    vec3 light = tint * trailLight * along * albedoMeteorLine(across, trailWidth, footprint);
    if (life < 1.0) {
        // The head gets brighter until it goes out, and its light above 1 makes its center white.
        float headLight = rise * (1.0 - albedoSmooth(0.8, 1.0, life)) * (0.7 + 0.5 * life) * brightness;
        float r = length(vec2(across, behind));
        float shape = 3.0 * albedoMeteorLine(r, headWidth, footprint) + 0.12 * exp(-(r / halo) * (r / halo));
        light += albedoLayer(layer, 3).rgb * albedoLayer(layer, 2).z * headLight * shape;
    }
    // The meteors go out at the horizon.
    return light * albedoSmooth(0.0, 0.09, q.y);
}

// Shooting stars in the world direction q at the time of the header, with no meteors in a still sky; the light above 1 goes into the color.
vec4 albedoMeteors(int layer, vec3 q, float footprint) {
    if (SkyHeader.w < 0.0 || q.y <= 0.0) return vec4(0.0);
    int now = int(floor(SkyHeader.w / ALBEDO_METEOR_SLOT));
    vec3 light = vec3(0.0);
    for (int slot = now - ALBEDO_METEOR_SLOTS_BACK; slot <= now; slot++) {
        for (int channel = 0; channel < ALBEDO_METEOR_CHANNELS; channel++) light += albedoMeteor(layer, slot, channel, q, footprint);
    }
    float a = clamp(max(light.r, max(light.g, light.b)), 0.0, 1.0);
    return a > 0.0 ? vec4(light / a, a) : vec4(0.0);
}

// Aurora.octave: one octave of rays, which goes to its mean 0.5 as a ray gets narrower than about three pixels.
float albedoRayOctave(vec2 q, ivec2 period, int seed, float width) {
    float k = 1.0 - albedoSmooth(0.18, 0.45, width);
    return k <= 0.0 ? 0.5 : 0.5 + (albedoValue(q, period, seed) - 0.5) * k;
}

// The numbers of Aurora: the earth radius, the altitude of the lower edge, the drift of the rays, the loop of a fold, the reach of a
// sheet to each side, the height that a curtain keeps at its end, the km over which its light comes in at the lower edge, the glow
// around that edge, the km over which the band at the edge fades up into the rays, how many times the height of the tallest rays
// above the edge the light ends, the most light of a piece seen edge-on and the azimuth of one cell of the noise of the gaps.
const float ALBEDO_AURORA_EARTH = 6371.0;
const float ALBEDO_AURORA_BASE = 100.0;
const float ALBEDO_AURORA_DRIFT = 0.6;
const float ALBEDO_AURORA_LOOP = 0.3;
const float ALBEDO_AURORA_THICK = 9.0;
const float ALBEDO_AURORA_SHORT = 0.15;
const float ALBEDO_AURORA_SOFT = 9.0;
const float ALBEDO_AURORA_SKIRT = 8.0;
const float ALBEDO_AURORA_BAND = 8.0;
const float ALBEDO_AURORA_TOP = 3.4;
const float ALBEDO_AURORA_EDGE_ON = 2.5;
const float ALBEDO_AURORA_GAP = 0.6;
#define ALBEDO_AURORA_STEPS 10
// Aurora.PERIOD: the aurora clock goes back to 0 after these seconds, in which each wave turns and each ray and noise row moves on
// a whole number of times, so that the wrap has no jump; the drift of the rays in it is ALBEDO_AURORA_DRIFT * PERIOD = 4320 km.
const float ALBEDO_AURORA_PERIOD = 7200.0;
// The angle in radians for each second of a wave that turns once in a period.
const float ALBEDO_AURORA_TURN = 6.2831853 / ALBEDO_AURORA_PERIOD;

// The distance in km of the nearest place of the near (0) and of the far (1) ribbon.
float albedoRibbonDistance(int c) {
    return c == 0 ? 160.0 : 430.0;
}

// The azimuth from the line through the nearest place of ribbon c at theta, so that each ribbon slants across the sky.
float albedoRibbonAngle(int c, float theta) {
    return clamp(theta - (c == 0 ? 0.45 : -0.35), -1.5, 1.5);
}

// One fold wave of Aurora.Ribbon.offset with its phase: adds its azimuth to x, and to d the slope of the azimuth and the loop toward
// the viewer and its slope.
float albedoFoldWave(int c, float k, float amp, float rate, float phase, float theta, float t, inout vec3 d) {
    float arg = k * theta + phase + rate * t;
    float s = sin(arg), co = cos(arg);
    float loop = ALBEDO_AURORA_LOOP * albedoRibbonDistance(c) * amp;
    d += vec3(amp * k * co, -loop * co, loop * k * s);
    return amp * s;
}

// Aurora.Ribbon.phases: the phases of the three fold waves of ribbon c in xyz, and of its two waves toward the viewer and of the two waves
// of its lower edge in the second vector, which the search finds once for each ribbon.
vec3 albedoRibbonPhases(int c, int seed, out vec4 waves) {
    waves = 6.2831853 * vec4(albedoUnit(albedoHash(ivec3(0, c, 1), seed + 602)), albedoUnit(albedoHash(ivec3(1, c, 1), seed + 602)),
            albedoUnit(albedoHash(ivec3(0, c, 2), seed + 603)), albedoUnit(albedoHash(ivec3(1, c, 2), seed + 603)));
    return 6.2831853 * vec3(albedoUnit(albedoHash(ivec3(0, c, 0), seed + 601)), albedoUnit(albedoHash(ivec3(1, c, 0), seed + 601)),
            albedoUnit(albedoHash(ivec3(2, c, 0), seed + 601)));
}

// Aurora.Ribbon.offset: the azimuth of ribbon c at theta minus the azimuth a of the view, with the slopes and the loop in d.
float albedoRibbonOffset(int c, float theta, float a, float t, float scale, vec3 phase, out vec3 d) {
    float f = albedoRibbonDistance(c) / albedoRibbonDistance(0);
    d = vec3(1.0, 0.0, 0.0);
    float phi = theta + albedoFoldWave(c, 8.0 * f / scale, 0.08 * scale / f, 23.0 * ALBEDO_AURORA_TURN, phase.x, theta, t, d);
    phi += albedoFoldWave(c, 21.0 * f / scale, 0.04 * scale / f, -57.0 * ALBEDO_AURORA_TURN, phase.y, theta, t, d);
    phi += albedoFoldWave(c, 43.0 * f / scale, 0.019 * scale / f, 126.0 * ALBEDO_AURORA_TURN, phase.z, theta, t, d);
    return phi - a;
}

// Aurora.Ribbon.speed: the length of ribbon c at theta for each radian of theta, over its distance, from the values d of the offset there.
float albedoRibbonSpeed(int c, float theta, vec3 d) {
    float th = albedoRibbonAngle(c, theta);
    float co = cos(th), dist = albedoRibbonDistance(c);
    float o = (dist * tan(th) / co + d.z) / (dist / co + d.y);
    return sqrt(d.x * d.x + o * o);
}

// Aurora.cumulative: the part of a triangle of width 2 around 0 that lies left of x.
float albedoTriangleBelow(float x) {
    float y = clamp(x, -1.0, 1.0);
    return y < 0.0 ? 0.5 * (1.0 + y) * (1.0 + y) : 1.0 - 0.5 * (1.0 - y) * (1.0 - y);
}

// Aurora.moment: the first moment of the part of a triangle of width 2 around 0 that lies left of x.
float albedoTriangleMoment(float x) {
    float y = clamp(x, -1.0, 1.0);
    return y < 0.0 ? y * y * (0.5 + y / 3.0) - 1.0 / 6.0 : y * y * (0.5 - y / 3.0) - 1.0 / 6.0;
}

// Aurora.Ribbon.rise: the km of the view at elevation el and azimuth a above the lower edge of curtain c, with the azimuth th of
// albedoRibbonAngle of the view and the place along there, where pixelUp gets the km of one pixel up the curtain.
float albedoAuroraRise(int c, float th, float along, float el, float footprint, float a, float t, float scale, vec3 phase, vec4 waves,
        out float pixelUp) {
    vec3 d;
    albedoRibbonOffset(c, a, a, t, scale, phase, d);
    float dist = albedoRibbonDistance(c), f = dist / albedoRibbonDistance(0);
    // The distance of a straight east-west line, moved toward and away from the viewer by the folds and by slow waves.
    float r = dist / cos(th) + d.y + 22.0 * sin(5.0 * f / scale * a + waves.x - 17.0 * ALBEDO_AURORA_TURN * t)
            + 5.0 * sin(17.0 * f / scale * a + waves.y + 46.0 * ALBEDO_AURORA_TURN * t);
    // The altitude of the view above the ground at the distance of the curtain, with the curve of the earth.
    float tilt = min(el + r / (2.0 * ALBEDO_AURORA_EARTH), 1.45), cosT = cos(tilt);
    pixelUp = footprint * r / (cosT * cosT);
    float sd = along + ALBEDO_AURORA_DRIFT * t;
    // The lower edge is nearly level, with gentle waves of about 23 and 9 km that move along it.
    float edge = 1.3 * sin(sd * (30.0 * ALBEDO_AURORA_TURN / ALBEDO_AURORA_DRIFT) + waves.z + 23.0 * ALBEDO_AURORA_TURN * t)
            + 0.8 * sin(sd * (76.0 * ALBEDO_AURORA_TURN / ALBEDO_AURORA_DRIFT) + waves.w - 57.0 * ALBEDO_AURORA_TURN * t);
    return r * tan(tilt) - ALBEDO_AURORA_BASE - edge;
}

// Aurora.Ribbon.presence: how much of curtain c is there at the azimuth th of albedoRibbonAngle of the view: parts of a curtain are
// there, fade out or are gaps of open sky, with wider gaps in the far curtain and none at the center.
float albedoAuroraPresence(int c, float th, float t, int seed) {
    float part = albedoValue(vec2(th / ALBEDO_AURORA_GAP, t * (29.0 / ALBEDO_AURORA_PERIOD)), ivec2(0, 29), seed + 31 + c)
            + (c == 0 ? 0.35 * exp(-th * th / 0.25) : 0.0);
    return albedoSmooth(0.25 + 0.15 * float(c), 0.7 + 0.15 * float(c), part);
}

// Aurora.Ribbon.aim: the light of the straight piece from p to q (each a place, an azimuth from the view and a speed) for a sheet whose
// light reaches w radians to each side, and the place where the piece is seen.
vec2 albedoAuroraAim(vec3 p, vec3 q, float w) {
    float xa = p.y / w, xb = q.y / w, dx = xb - xa;
    if (min(abs(xa), abs(xb)) >= 1.0 && (xa < 0.0) == (xb < 0.0)) return vec2(0.0);
    // The light along the view is the length of the piece near it, so a piece seen edge-on is brighter.
    float part = albedoTriangleBelow(xb) - albedoTriangleBelow(xa), mid = 0.5;
    float k = (abs(dx) < 1e-3 ? max(0.0, 1.0 - abs(xa)) : part / dx) * abs(q.x - p.x) / w * 0.5 * (p.z + q.z);
    if (k <= 0.0) return vec2(0.0);
    // The middle of the light of the piece, which moves smoothly with the view.
    if (abs(dx) >= 1e-3 && part != 0.0) {
        mid = clamp((albedoTriangleMoment(xb) - albedoTriangleMoment(xa) - xa * part) / (dx * part), 0.0, 1.0);
    }
    // The piece is seen where its line meets the view, so that the pieces around one crossing show one sharp sheet,
    // and a piece that runs along the view is seen where its light is.
    float xm = xa + dx * mid;
    // A piece that does not cross the view gives light only from the width of its sheet, which fades out faster as it gets
    // farther, so that the side of a part that runs along the view does not look like a straight cut.
    k *= 1.0 - albedoSmooth(0.0, 1.0, max(0.0, max(min(xa, xb), -max(xa, xb))));
    // A piece seen edge-on is brighter, but not as a pillar of light at the tip of each fold.
    return vec2(k / (1.0 + k / ALBEDO_AURORA_EDGE_ON), p.x + (q.x - p.x) * (mid - xm * dx / (dx * dx + 0.25)));
}

// Aurora.Ribbon.rays: the light of a piece of ribbon c seen at theta with the light k, whose azimuth changes by slope for each radian of
// theta, for a view h km above the lower edge (up is h but not under 0) of a curtain whose light comes in over soft km at that edge
// and whose tallest rays reach tallest km: all of it, of its band at the lower edge, of its rays and of their tops in COLOR_2, where
// a view far under the edge gets only the first.
vec4 albedoAuroraRays(int c, float theta, float k, float slope, float h, float up, float soft, float tallest, float wide, float t,
        float scale, int seed) {
    float th = albedoRibbonAngle(c, theta), along = albedoRibbonDistance(c) * tan(th) / scale, co = cos(th);
    float bright = k * (0.4 + 0.9 * albedoValue(vec2(along / 70.0, t * (72.0 / ALBEDO_AURORA_PERIOD)), ivec2(0, 72), seed + 33 + c));
    if (h <= -2.0 * soft) return vec4(bright, 0.0, 0.0, 0.0);
    // The km of the rays in one pixel, which is more where the view runs along the sheet, as near the tip of a fold.
    float pixelAlong = wide * albedoRibbonDistance(c) / (co * co * max(abs(slope), 0.02)) / scale;
    float sd = along + ALBEDO_AURORA_DRIFT * t;
    // Each octave repeats after the drift of one period along the ribbon and after its rows of one period.
    float r0 = albedoRayOctave(vec2(sd / 40.0, t * (216.0 / ALBEDO_AURORA_PERIOD)), ivec2(108, 216), seed + 21 + c, pixelAlong / 40.0);
    float r1 = albedoRayOctave(vec2(sd / 3.0, t * (480.0 / ALBEDO_AURORA_PERIOD)), ivec2(1440, 480), seed + 22 + c, pixelAlong / 3.0);
    float r2 = albedoRayOctave(vec2(sd / 1.08, t * (900.0 / ALBEDO_AURORA_PERIOD)), ivec2(4000, 900), seed + 23 + c, pixelAlong / 1.08);
    float r3 = albedoRayOctave(vec2(sd / 0.5, t * (1800.0 / ALBEDO_AURORA_PERIOD)), ivec2(8640, 1800), seed + 24 + c, pixelAlong / 0.5);
    float tall = albedoRayOctave(vec2(sd / 24.0 + 31.7, t * (144.0 / ALBEDO_AURORA_PERIOD)), ivec2(180, 144), seed + 25 + c, pixelAlong / 24.0);
    // Wide bundles of thin rays, which are brighter lines in one sheet of light and have soft sides.
    float bundle = 0.35 + 0.65 * albedoSmooth(0.2, 0.8, r0);
    float hard = clamp(pixelAlong / 0.5 * 0.5, 0.2, 0.3);
    float rays = bundle * albedoSmooth(0.55 - hard, 0.55 + hard, 0.3 * r1 + 0.38 * r2 + 0.32 * r3);
    // The height of the rays changes slowly along the curtain, so that their tops are one soft line, and each thin ray is a little
    // taller or shorter than the rays next to it.
    float rise = up / (tallest * (0.3 + 0.5 * tall) * (0.85 + 0.3 * r2));
    // The light comes in over the soft km at the lower edge, which each bundle starts a little higher or lower, so that the edge is
    // neither a hard line nor a level one; it is 0 for a view more than 1.2 soft km under the edge, so that the stop at 2 soft km
    // above changes no light.
    float foot = albedoSmooth(-soft, soft, h + 0.4 * soft * (r0 - 0.5));
    foot *= foot;
    // The light of each bundle between its rays, so that a curtain is a sheet of light and not a row of needles.
    float sheet = 0.45 * bundle + 0.75 * rays;
    // The tops of the rays turn to COLOR_2 over a wide part of the curtain, so that the color changes slowly upward.
    return bright * vec4(1.0, foot * (0.12 + 0.3 * bundle + 0.6 * rays), foot * 0.65 * sheet * exp(-rise * (0.5 + 0.5 * rise)),
            foot * 0.3 * sheet * (0.5 + tall) * exp(-(rise - 1.1) * (rise - 1.1) * 1.2));
}

// Aurora.Ribbon.add: the light of COLOR_1 and COLOR_2 of curtain c: one lower edge, one height and one brightness for the azimuth of
// the view, so that they are smooth across the sky, times the rays of the straight pieces of the folded ribbon between the steps of a
// search over the azimuths of the folds.
vec2 albedoRibbonLight(int c, float el, float wide, float footprint, float a, float t, float scale, float height, int seed) {
    float f = albedoRibbonDistance(c) / albedoRibbonDistance(0);
    float th = albedoRibbonAngle(c, a), along = albedoRibbonDistance(c) * tan(th) / scale;
    float present = albedoAuroraPresence(c, th, t, seed);
    if (present <= 0.0) return vec2(0.0);
    vec3 d;
    vec4 waves;
    vec3 phase = albedoRibbonPhases(c, seed, waves);
    float pixelUp;
    float h = albedoAuroraRise(c, th, along, el, footprint, a, t, scale, phase, waves, pixelUp);
    // A curtain gets shorter as it gets fainter toward its end, so that it does not end at a wall, and the far one is a lower arc.
    float tallest = height * (ALBEDO_AURORA_SHORT + (1.0 - ALBEDO_AURORA_SHORT) * present) / f;
    float soft = max(ALBEDO_AURORA_SOFT * f, 0.7 * pixelUp), up = max(h, 0.0);
    if (h < -max(4.0 * ALBEDO_AURORA_SKIRT, 2.0 * soft) || h > ALBEDO_AURORA_TOP * tallest) return vec2(0.0);
    float w = max(3.0 * wide, ALBEDO_AURORA_THICK / albedoRibbonDistance(c));
    // The search reaches past the light of each sheet, so that a fold comes into the search with no light to change.
    float reach = 0.139 * scale / f * 1.02 + w;
    float step = 2.0 * reach / float(ALBEDO_AURORA_STEPS);
    // The light of the pieces of the ribbon near the view: all of it, of the band at the lower edge, of the rays and of their red tops.
    vec4 sum = vec4(0.0);
    float ta = a - reach, ga = albedoRibbonOffset(c, ta, a, t, scale, phase, d), da = d.x, sa = albedoRibbonSpeed(c, ta, d);
    for (int i = 1; i <= ALBEDO_AURORA_STEPS; i++) {
        float tb = a - reach + float(i) * step, gb = albedoRibbonOffset(c, tb, a, t, scale, phase, d), db = d.x;
        float sb = albedoRibbonSpeed(c, tb, d);
        bool tip = (da < 0.0) != (db < 0.0);
        vec3 m = vec3(tb, gb, sb);
        if (tip) {
            // The tip of a fold is in this step, which gets a corner there, found in two steps toward the zero of the slope.
            float tm = ta + step * da / (da - db);
            albedoRibbonOffset(c, tm, a, t, scale, phase, d);
            float dm = d.x;
            tm = (da < 0.0) != (dm < 0.0) ? ta + (tm - ta) * da / (da - dm) : tm + (tb - tm) * dm / (dm - db);
            m.xy = vec2(tm, albedoRibbonOffset(c, tm, a, t, scale, phase, d));
            m.z = albedoRibbonSpeed(c, tm, d);
        }
        // One call for the piece up to the tip or the step, and one for the piece after the tip, so that the shader has one copy of it.
        for (int k = 0; k < 2; k++) {
            if (k == 1 && !tip) break;
            vec3 p = k == 0 ? vec3(ta, ga, sa) : m, q = k == 0 ? m : vec3(tb, gb, sb);
            vec2 aim = albedoAuroraAim(p, q, w);
            if (aim.x > 0.0) {
                sum += albedoAuroraRays(c, aim.y, aim.x, (q.y - p.y) / (q.x - p.x), h, up, soft, tallest, wide, t, scale, seed);
            }
        }
        ta = tb;
        ga = gb;
        da = db;
        sa = sb;
    }
    // A faint glow around the lower edge that also reaches under it, so that the sky under a curtain does not look like a dark hill,
    // with no crease at the edge.
    float glow = 0.1 * sum.x * exp(-(h / ALBEDO_AURORA_SKIRT) * (h / ALBEDO_AURORA_SKIRT));
    // The brightest light is a band just above the lower edge, wider in km on the far curtain so that it is no thin line there, and
    // above it the rays dissolve into the open sky.
    return (c == 0 ? 1.0 : 0.2) * present * vec2(glow + sum.y * exp(-up / (ALBEDO_AURORA_BAND * f)) + sum.z, sum.w);
}

// Aurora.sample: a near and a far folded ribbon toward the layer center in the world, which move with the aurora clock of the layer.
vec4 albedoAurora(int layer, vec3 p, float footprint) {
    float el = asin(clamp(p.y, -1.0, 1.0));
    float cosEl = sqrt(max(0.0, 1.0 - p.y * p.y));
    // The pole of the layer has no azimuth, and atan of two zeros has no defined value in GLSL.
    if (el < -0.05 || el > 1.4 || cosEl < 1e-4) return vec4(0.0);
    float a = atan(p.x, -p.z);
    if (abs(a) > 1.35) return vec4(0.0);
    vec4 values = albedoLayer(layer, 1);
    vec4 shape = albedoLayer(layer, 2);
    int seed = int(shape.y);
    // The aurora clock in the free float of the meteor length, which the CPU sets for each frame and which is 0 in a still sky.
    float t = albedoLayer(layer, 5).w;
    float height = 70.0 + 260.0 * values.w;
    float wide = footprint / max(cosEl, 0.05);
    // One loop over the two ribbons, so that the shader has one copy of the search.
    vec2 light = vec2(0.0);
    for (int c = 0; c < 2; c++) {
        // The far ribbon with its shorter rays is never more than about 40 degrees high.
        if (c == 1 && el >= 0.72) break;
        light += albedoRibbonLight(c, el, wide, footprint, a, t, shape.x, height, seed);
    }
    // The thick air near the horizon dims the light, so that no bright edge lies along the horizon, and the light fades out before the search stops.
    vec3 e = (albedoLayer(layer, 3).rgb * shape.z * light.x + albedoLayer(layer, 4).rgb * shape.w * light.y)
            * values.z * 2.0 * albedoSmooth(-0.02, 0.22, el) * (1.0 - albedoSmooth(1.15, 1.35, abs(a)));
    float m = max(e.r, max(e.g, e.b));
    if (m <= 0.0) return vec4(0.0);
    // A light brighter than the screen goes to white, as in a photograph.
    float hot = max(0.0, m - 1.0) * 0.25;
    float alpha = min(1.0, m + hot);
    return vec4((e + hot) / alpha, alpha);
}

vec3 albedoBlend(int blend, vec3 b, vec3 v, float k) {
    if (blend == ALBEDO_BLEND_ADD) return b + v * k;
    if (blend == ALBEDO_BLEND_SCREEN) {
        vec3 bb = clamp(b, 0.0, 1.0);
        vec3 vv = clamp(v, 0.0, 1.0);
        return b + (vv - bb * vv) * k;
    }
    if (blend == ALBEDO_BLEND_MULTIPLY) return b + (b * v - b) * k;
    return b + (v - b) * k;
}

// The linear horizon color at an azimuth in degrees, mixed between the two nearest sector centers.
vec3 albedoRing(float azimuth) {
    float at = mod(azimuth, 360.0) / 360.0 * float(ALBEDO_RING) - 0.5;
    float base = floor(at);
    int i0 = int(mod(base, float(ALBEDO_RING)));
    int i1 = int(mod(base + 1.0, float(ALBEDO_RING)));
    return mix(SkyRing[i0].rgb, SkyRing[i1].rgb, at - base);
}

// The texels of the term or field image of a slot in a sky direction, where the rows stop half a texel inside the image of the slot.
vec4 albedoField(int slot, vec3 dir) {
    float rows = ALBEDO_TERM_ROWS;
    float slotRows = rows / max(1.0, SkyHeader.z);
    float u = atan(dir.x, -dir.z) / (2.0 * ALBEDO_PI);
    float y = clamp((0.5 - asin(clamp(dir.y, -1.0, 1.0)) / ALBEDO_PI) * slotRows, 0.5, slotRows - 0.5);
    return ALBEDO_TERM_READ(vec2(u, (float(slot) * slotRows + y) / rows));
}

vec3 albedoTerm(int slot, vec3 dir) {
    return albedoField(slot, dir).rgb;
}

// One linear channel in sRGB from 0 to 255, as Oklab.toSrgb.
float albedoToSrgb(float linear) {
    return albedoLinearToSrgb(clamp(linear, 0.0, 1.0)) * 255.0;
}

// One sRGB channel from 0 to 255 in linear light, as Oklab.toLinear of a byte.
float albedoByteToLinear(float channel) {
    return albedoSrgbToLinear(channel / 255.0);
}

// Fewer color steps: from 2 to 16 for each channel, as AMOUNT sets.
vec3 albedoPosterize(int layer, vec3 img) {
    float steps = floor(2.0 + albedoLayer(layer, 1).z * 14.0 + 0.5) - 1.0;
    vec3 result = vec3(0.0);
    for (int c = 0; c < 3; c++) {
        float q = floor(albedoToSrgb(img[c]) / 255.0 * steps + 0.5) / steps;
        result[c] = albedoByteToLinear(floor(q * 255.0 + 0.5));
    }
    return result;
}

// Turns the hue by AMOUNT of a full turn, in OKLab so that the lightness stays.
vec3 albedoHueShift(int layer, vec3 img) {
    float angle = albedoLayer(layer, 1).z * 2.0 * ALBEDO_PI;
    float c = cos(angle), s = sin(angle);
    vec3 lab = albedoLinearToLab(max(img, 0.0));
    return max(albedoLabToLinear(vec3(lab.x, lab.y * c - lab.z * s, lab.y * s + lab.z * c)), 0.0);
}

// Mixes COLOR_1 in away from the layer center, the -z axis of the layer frame, from 30 percent of 90 degrees times the size out.
vec3 albedoVignette(int layer, vec3 img, vec3 p) {
    float radius = radians(90.0 * albedoLayer(layer, 2).x);
    float dist = acos(clamp(-p.z, -1.0, 1.0));
    float k = albedoSmooth(radius * 0.3, radius, dist) * albedoLayer(layer, 1).z * albedoLayer(layer, 2).z;
    return img + (albedoLayer(layer, 3).rgb - img) * k;
}

// The glow that a bloom adds to the sky below it, from its term image.
vec4 albedoBloom(int layer, vec3 img, vec3 dir) {
    return vec4(img + albedoTerm(int(albedoLayer(layer, 3).w), dir), 1.0);
}

// The result of a blur, pixelate, crystallize or swirl, which reads near pixels or other directions, from its term image.
vec4 albedoTermFilter(int layer, vec3 dir) {
    return vec4(albedoTerm(int(albedoLayer(layer, 3).w), dir), 1.0);
}

// The point of a sky direction in the frame of a layer.
vec3 albedoFrame(int layer, vec3 dir) {
    return vec3(dot(albedoLayer(layer, 7).xyz, dir), dot(albedoLayer(layer, 8).xyz, dir), dot(albedoLayer(layer, 9).xyz, dir));
}

// The lightness times the alpha, and the darkness times the alpha, of each layer that a higher layer uses as its mask.
float albedoMaskLight[ALBEDO_MAX_LAYERS];
float albedoMaskDark[ALBEDO_MAX_LAYERS];

// Keeps the mask values of a layer s for the higher layers that use it as a mask.
void albedoMaskSource(int layer, vec4 s) {
    float a = clamp(s.a, 0.0, 1.0);
    float lightness = clamp(albedoLightness(clamp(s.rgb, 0.0, 1.0)), 0.0, 1.0);
    albedoMaskLight[layer] = lightness * a;
    albedoMaskDark[layer] = (1.0 - lightness) * a;
}

// The sky below a layer with the layer s over it, through the mask of a lower layer when maskIndex is not -1.
vec3 albedoShow(vec3 img, vec4 s, int layer, int blend, int maskIndex, bool maskDark) {
    float maskValue = maskIndex < 0 ? 1.0 : maskDark ? albedoMaskDark[maskIndex] : albedoMaskLight[maskIndex];
    float k = albedoLayer(layer, 1).y * maskValue * clamp(s.a, 0.0, 1.0);
    return k > 0.0 ? albedoBlend(blend, img, s.rgb, k) : img;
}

// The linear color of the sky in a unit direction, where the footprint is the width of the pixel in radians, and
// ALBEDO_LAYER_STEPS from SkyShaderSource.layerSteps calls the function of the kind of each layer that shows or masks,
// with the structure of the layer as constants, so that the shader has only the code that the sky uses.
vec3 albedoSky(vec3 dir, float footprint) {
    float el = degrees(asin(clamp(dir.y, -1.0, 1.0)));
    float fade = SkyHeader.y > 0.5 ? 0.0 : albedoSmooth(0.0, ALBEDO_FADE, -el);
    vec3 img = vec3(0.0);
    if (fade < 1.0) {
        vec4 s;
        // The meteors and the aurora are in the air, so their steps take this world direction and they stay in the world when the sky turns.
        vec3 world = vec3(dot(SkyWorld[0].xyz, dir), dot(SkyWorld[1].xyz, dir), dot(SkyWorld[2].xyz, dir));
        ALBEDO_LAYER_STEPS
    }
    if (fade > 0.0) img += (albedoRing(degrees(atan(dir.x, -dir.z))) - img) * fade;
    return img;
}

// The sRGB color of a linear color with the hash dither of the bake, which hides the bands of slow gradients.
vec3 albedoEncode(vec3 linear, ivec2 pixel) {
    float d = albedoUnit(albedoHash(ivec3(pixel, 0), 77)) - 0.5;
    vec3 srgb = vec3(albedoToSrgb(linear.r), albedoToSrgb(linear.g), albedoToSrgb(linear.b)) + d;
    return clamp(floor(srgb + 0.5), 0.0, 255.0) / 255.0;
}

// The sRGB color of a pixel of the equirectangular image at uv, or of a camera view when corner is not zero.
vec3 albedoEquirectPixel(vec2 uv, vec3 corner, ivec2 pixel) {
    float lon = uv.x * 2.0 * ALBEDO_PI;
    float el = radians(90.0 - uv.y * 180.0);
    bool view = dot(corner, corner) > 0.0;
    vec3 dir = view ? normalize(corner) : vec3(cos(el) * sin(lon), sin(el), -cos(el) * cos(lon));
    // The width of one pixel of the view, or of one texel at the horizon as the bake uses for its stars.
    float footprint = view ? max(length(dFdx(dir)), length(dFdy(dir))) : 2.0 * ALBEDO_PI * abs(dFdx(uv.x));
    return albedoEncode(albedoSky(dir, footprint), pixel);
}

// The sRGB color of a pixel of the sky sphere at a point around the camera.
vec3 albedoSharpPixel(vec3 position, ivec2 pixel) {
    vec3 dir = normalize(position);
    // The angle across one pixel, outside all branches so that the derivatives are defined.
    float footprint = max(length(dFdx(dir)), length(dFdy(dir)));
    return albedoEncode(albedoSky(dir, footprint), pixel);
}

#endif
