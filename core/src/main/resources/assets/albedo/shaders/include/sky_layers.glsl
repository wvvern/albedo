#ifndef ALBEDO_SKY_LAYERS_GLSL
#define ALBEDO_SKY_LAYERS_GLSL

// The custom sky for each pixel, a port of dev.albedo.core.sky.SkyGenerator that reads the block of SkyProgram.

#define ALBEDO_MAX_LAYERS 8
#define ALBEDO_LAYER_VEC4S 10
#define ALBEDO_RING 64

// The generators have the kind codes below ALBEDO_KIND_BLOOM, and the filters have the codes from it up.
#define ALBEDO_KIND_GRADIENT 1
#define ALBEDO_KIND_GLOW 2
#define ALBEDO_KIND_STARS 3
#define ALBEDO_KIND_NOISE 4
#define ALBEDO_KIND_FRACTAL 5
#define ALBEDO_KIND_GALAXY 6
#define ALBEDO_KIND_BLOOM 7
#define ALBEDO_KIND_BLUR 8
#define ALBEDO_KIND_PIXELATE 9
#define ALBEDO_KIND_CRYSTALLIZE 10
#define ALBEDO_KIND_SWIRL 11
#define ALBEDO_KIND_POSTERIZE 12
#define ALBEDO_KIND_HUE_SHIFT 13
#define ALBEDO_KIND_VIGNETTE 14

#define ALBEDO_BLEND_NORMAL 0
#define ALBEDO_BLEND_ADD 1
#define ALBEDO_BLEND_SCREEN 2
#define ALBEDO_BLEND_MULTIPLY 3

#define ALBEDO_USE_NONE 0
#define ALBEDO_USE_MASK 1
#define ALBEDO_USE_SHOW 2

#ifdef ALBEDO_PLAIN_UNIFORMS
// The values of the uniform block below, for the GL programs that set them as three arrays.
uniform vec4 SkyHeader;
uniform vec4 SkyLayers[ALBEDO_MAX_LAYERS * ALBEDO_LAYER_VEC4S];
uniform vec4 SkyRing[ALBEDO_RING];
#else
layout(std140) uniform AlbedoSky {
    // The layer count, the extent (1 for the full sphere) and the term count.
    vec4 SkyHeader;
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
// The distance in degrees from the horizon over which the sky goes into the horizon color.
const float ALBEDO_HAZE = 3.0;

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

// Noise.fbm: a sum of octaves at twice the frequency and half the weight each, from about -1 to 1.
float albedoFbm(vec3 p, int seed, int octaves) {
    float sum = 0.0, amp = 1.0, norm = 0.0;
    for (int o = 0; o < octaves; o++) {
        sum += albedoNoise(p, seed + o * 1013) * amp;
        norm += amp;
        amp *= 0.5;
        p *= 2.03;
    }
    return sum / norm * 1.6;
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

float albedoCbrt(float x) {
    return sign(x) * pow(abs(x), 1.0 / 3.0);
}

float albedoLightness(vec3 c) {
    float l = albedoCbrt(0.4122214708 * c.r + 0.5363325363 * c.g + 0.0514459929 * c.b);
    float m = albedoCbrt(0.2119034982 * c.r + 0.6806995451 * c.g + 0.1073969566 * c.b);
    float s = albedoCbrt(0.0883024619 * c.r + 0.2817188376 * c.g + 0.6299787005 * c.b);
    return 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s;
}

vec3 albedoLinearToLab(vec3 c) {
    float l = albedoCbrt(0.4122214708 * c.r + 0.5363325363 * c.g + 0.0514459929 * c.b);
    float m = albedoCbrt(0.2119034982 * c.r + 0.6806995451 * c.g + 0.1073969566 * c.b);
    float s = albedoCbrt(0.0883024619 * c.r + 0.2817188376 * c.g + 0.6299787005 * c.b);
    return vec3(0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s);
}

vec3 albedoLabToLinear(vec3 lab) {
    float l = lab.x + 0.3963377774 * lab.y + 0.2158037573 * lab.z;
    float m = lab.x - 0.1055613458 * lab.y - 0.0638541728 * lab.z;
    float s = lab.x - 0.0894841775 * lab.y - 1.2914855480 * lab.z;
    l = l * l * l;
    m = m * m * m;
    s = s * s * s;
    return vec3(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
            -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
            -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s);
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

// The light of the stars of one grid (SkyStars.Tier) at p, where each star stays in its cell and reaches at most half a cell.
vec3 albedoStarTier(vec3 p, float footprint, float cells, float minFlux, float maxFlux, float slope, int salt, bool bright,
        float density, float spikes, float scale, int seed) {
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
                uint hj = albedoHash(h, seed * 31 + salt + 1);
                vec3 v = vec3(h) + (vec3(float(hj >> 22u), float((hj >> 12u) & 1023u), float((hj >> 2u) & 1023u)) + 0.5) / 1024.0;
                float len = length(v);
                if (len < 1e-3) continue;
                vec3 s = v / len;
                // The star is the point of the cell moved onto the sphere, and the cell keeps it only when it stays in the cell.
                if (ivec3(floor(s * n)) != h) continue;
                // The sphere crosses more cells near a diagonal of the grid, so fewer of them keep a star there.
                if (chance >= density * (abs(s.x) + abs(s.y) + abs(s.z)) / 1.7320508) continue;
                vec3 d = p - s;
                float d2 = dot(d, d);
                if (d2 >= reach * reach) continue;
                float flux = min(maxFlux, minFlux * pow((float((hb >> 12u) & 4095u) + 0.5) / 4096.0, -1.0 / slope));
                float dist = sqrt(d2);
                // The core keeps its light when the footprint widens it, so that a star smaller than a pixel does not flicker.
                float core = ALBEDO_STAR_CORE * scale, core2 = core * core + blur2;
                float glow = flux * core / sqrt(core2) * exp(-d2 / (2.0 * core2));
                float halo = ALBEDO_STAR_HALO * scale * sqrt(flux), halo2 = halo * halo + blur2;
                float q = 1.0 + d2 / halo2;
                glow += flux * ALBEDO_STAR_HALO_LIGHT * halo * halo / halo2 / (q * q);
                vec3 detail = vec3(0.0);
                if (bright) {
                    detail = albedoStarRim(flux, dist, blur2, scale);
                    if (spikes > 0.0 && flux > ALBEDO_STAR_SPIKE_FLUX) detail += albedoSpikes(s, d, flux, maxFlux, spikes, blur2, scale);
                }
                vec3 color = albedoStarColor((float(hb & 4095u) + 0.5) / 4096.0);
                // The light goes to 0 before the edge of the reach, so that no star is cut.
                float fade = 1.0 - albedoSmooth(0.8 * reach, reach, dist);
                light += color * fade * (glow + detail);
            }
        }
    }
    return light;
}

// Stars with the brightness spread and the colors of real stars, where AMOUNT is the count and HEIGHT is the strength of the spikes.
vec4 albedoStars(int layer, vec3 p, float footprint) {
    vec4 values = albedoLayer(layer, 1);
    vec4 shape = albedoLayer(layer, 2);
    int seed = int(shape.y);
    float m = 0.2 + 0.8 * values.z;
    float density = m * m, spikes = values.w * 2.0;
    vec3 light = albedoStarTier(p, footprint, 88.0, 0.04, 0.9, 1.3, 7, false, density, spikes, shape.x, seed);
    light += albedoStarTier(p, footprint, 16.0, 0.9, 60.0, 1.2, 13, true, density, spikes, shape.x, seed);
    light *= albedoLayer(layer, 3).rgb;
    float peak = max(light.r, max(light.g, light.b));
    // The alpha carries the light up to 1 and the color carries the rest, so that an added layer adds all of the light.
    float a = min(1.0, peak);
    return vec4(a > 0.0 ? light / a : albedoLayer(layer, 3).rgb, a * shape.z);
}

// Clouds of fractal noise from COLOR_1 to COLOR_2, where AMOUNT is the part of the sky they cover.
vec4 albedoClouds(int layer, vec3 p) {
    vec4 values = albedoLayer(layer, 1);
    vec4 shape = albedoLayer(layer, 2);
    float v = albedoFbm(p * (2.5 / shape.x), int(shape.y), 6) * 0.5 + 0.5;
    vec4 c = albedoMix(layer, v);
    float edge = 1.0 - values.z * 1.2;
    c.a *= albedoSmooth(edge - 0.12, edge + 0.12, v);
    return c;
}

// Thin bright lines of ridged noise, bent by more noise, where AMOUNT is how much they bend.
vec4 albedoFractal(int layer, vec3 p) {
    vec4 values = albedoLayer(layer, 1);
    vec4 shape = albedoLayer(layer, 2);
    int seed = int(shape.y);
    vec3 q = p * (1.6 / shape.x);
    vec3 bend = vec3(albedoFbm(q + vec3(5.2, 1.3, 2.8), seed + 11, 4), albedoFbm(q + vec3(1.7, 9.2, 4.4), seed + 23, 4),
            albedoFbm(q + vec3(8.3, 2.8, 7.1), seed + 37, 4));
    float v = albedoRidged(q + values.z * 1.6 * bend, seed, 6);
    v = v * v;
    vec4 c = albedoMix(layer, v);
    c.a *= clamp(v * 1.6, 0.0, 1.0);
    return c;
}

// A spiral galaxy at the layer center, with COLOR_1 arms around a COLOR_2 core, where AMOUNT turns the arms more.
vec4 albedoGalaxy(int layer, vec3 p) {
    if (p.z >= -0.05) return vec4(0.0);
    vec4 values = albedoLayer(layer, 1);
    vec4 shape = albedoLayer(layer, 2);
    float size = 0.45 * shape.x;
    float u = p.x / -p.z / size;
    float v = p.y / -p.z / size / 0.5;
    float r = sqrt(u * u + v * v);
    if (r > 2.5) return vec4(0.0);
    // The angle at the exact center is not defined in GLSL, and the arms have no weight there.
    float theta = r > 0.0 ? atan(v, u) : 0.0;
    float twist = 2.0 + values.z * 6.0;
    float arm = 0.5 + 0.5 * cos(2.0 * (theta - twist * log(r + 0.08)));
    arm = arm * arm * arm;
    float n = albedoFbm(p * 9.0, int(shape.y), 5) * 0.5 + 0.5;
    float disk = exp(-r * 2.2) * arm * (0.4 + 1.2 * n);
    float core = exp(-(r / 0.16) * (r / 0.16));
    float haze = exp(-r * 3.5) * 0.35;
    vec4 c = albedoMix(layer, clamp(core * 1.5 + haze * 0.5, 0.0, 1.0));
    c.a = clamp(disk + core + haze, 0.0, 1.0) * max(shape.z, shape.w);
    return c;
}

vec4 albedoGenerate(int layer, int kind, vec3 p, float footprint) {
    if (kind == ALBEDO_KIND_GRADIENT) return albedoGradient(layer, p);
    if (kind == ALBEDO_KIND_GLOW) return albedoGlow(layer, p);
    if (kind == ALBEDO_KIND_STARS) return albedoStars(layer, p, footprint);
    if (kind == ALBEDO_KIND_NOISE) return albedoClouds(layer, p);
    if (kind == ALBEDO_KIND_FRACTAL) return albedoFractal(layer, p);
    if (kind == ALBEDO_KIND_GALAXY) return albedoGalaxy(layer, p);
    return vec4(0.0);
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

// The linear color of a term image in a sky direction, where the rows stop half a texel inside the image of the slot.
vec3 albedoTerm(int slot, vec3 dir) {
    float rows = ALBEDO_TERM_ROWS;
    float slotRows = rows / max(1.0, SkyHeader.z);
    float u = atan(dir.x, -dir.z) / (2.0 * ALBEDO_PI);
    float y = clamp((0.5 - asin(clamp(dir.y, -1.0, 1.0)) / ALBEDO_PI) * slotRows, 0.5, slotRows - 0.5);
    return ALBEDO_TERM_READ(vec2(u, (float(slot) * slotRows + y) / rows)).rgb;
}

// One linear channel in sRGB from 0 to 255, as Oklab.toSrgb.
float albedoToSrgb(float linear) {
    float c = clamp(linear, 0.0, 1.0);
    return (c <= 0.0031308 ? c * 12.92 : 1.055 * pow(c, 1.0 / 2.4) - 0.055) * 255.0;
}

// One sRGB channel from 0 to 255 in linear light, as Oklab.toLinear of a byte.
float albedoByteToLinear(float channel) {
    float c = channel / 255.0;
    return c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4);
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

// The color of a filter over the sky below it, where a filter that reads near pixels or other directions reads its term image.
vec4 albedoFilter(int layer, int kind, vec3 img, vec3 dir, vec3 p) {
    if (kind == ALBEDO_KIND_BLOOM) {
        // The term holds only the glow that the bloom adds to the sky below it.
        return vec4(img + albedoTerm(int(albedoLayer(layer, 3).w), dir), 1.0);
    }
    if (kind == ALBEDO_KIND_BLUR || kind == ALBEDO_KIND_PIXELATE || kind == ALBEDO_KIND_CRYSTALLIZE || kind == ALBEDO_KIND_SWIRL) {
        return vec4(albedoTerm(int(albedoLayer(layer, 3).w), dir), 1.0);
    }
    if (kind == ALBEDO_KIND_POSTERIZE) return vec4(albedoPosterize(layer, img), 1.0);
    if (kind == ALBEDO_KIND_HUE_SHIFT) return vec4(albedoHueShift(layer, img), 1.0);
    if (kind == ALBEDO_KIND_VIGNETTE) return vec4(albedoVignette(layer, img, p), 1.0);
    return vec4(img, 1.0);
}

// The linear color of the sky in a unit direction, where the footprint is the width of the pixel in radians.
vec3 albedoSky(vec3 dir, float footprint) {
    int count = int(SkyHeader.x);
    float el = degrees(asin(clamp(dir.y, -1.0, 1.0)));
    float from = SkyHeader.y > 0.5 ? abs(el) : el;
    float haze = 1.0 - albedoSmooth(0.0, ALBEDO_HAZE, from);
    vec3 img = vec3(0.0);
    if (haze < 1.0) {
        float maskLight[ALBEDO_MAX_LAYERS];
        float maskDark[ALBEDO_MAX_LAYERS];
        for (int i = 0; i < ALBEDO_MAX_LAYERS; i++) {
            maskLight[i] = 0.0;
            maskDark[i] = 0.0;
            if (i >= count) break;
            vec4 head = albedoLayer(i, 0);
            int use = int(head.z);
            if (use == ALBEDO_USE_NONE) continue;
            int kind = int(head.x);
            vec3 p = vec3(dot(albedoLayer(i, 7).xyz, dir), dot(albedoLayer(i, 8).xyz, dir), dot(albedoLayer(i, 9).xyz, dir));
            vec4 s = kind >= ALBEDO_KIND_BLOOM ? albedoFilter(i, kind, img, dir, p) : albedoGenerate(i, kind, p, footprint);
            float lightness = clamp(albedoLightness(clamp(s.rgb, 0.0, 1.0)), 0.0, 1.0);
            float a = clamp(s.a, 0.0, 1.0);
            maskLight[i] = lightness * a;
            maskDark[i] = (1.0 - lightness) * a;
            if (use != ALBEDO_USE_SHOW) continue;
            vec4 values = albedoLayer(i, 1);
            int maskIndex = int(head.w);
            float maskValue = maskIndex < 0 ? 1.0 : values.x > 0.5 ? maskDark[maskIndex] : maskLight[maskIndex];
            float k = values.y * maskValue * a;
            if (k > 0.0) img = albedoBlend(int(head.y), img, s.rgb, k);
        }
    }
    if (haze > 0.0) img += (albedoRing(degrees(atan(dir.x, -dir.z))) - img) * haze;
    return img;
}

// The sRGB color of a linear color with the hash dither of the bake, which hides the bands of slow gradients.
vec3 albedoEncode(vec3 linear, ivec2 pixel) {
    float d = albedoUnit(albedoHash(ivec3(pixel, 0), 77)) - 0.5;
    vec3 srgb = vec3(albedoToSrgb(linear.r), albedoToSrgb(linear.g), albedoToSrgb(linear.b)) + d;
    return clamp(floor(srgb + 0.5), 0.0, 255.0) / 255.0;
}

#endif
