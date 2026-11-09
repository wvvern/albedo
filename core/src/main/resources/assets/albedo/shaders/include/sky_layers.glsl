#ifndef ALBEDO_SKY_LAYERS_GLSL
#define ALBEDO_SKY_LAYERS_GLSL

// The custom sky for each pixel, a port of dev.albedo.core.sky.SkyGenerator that reads the block of SkyProgram.

#define ALBEDO_MAX_LAYERS 8
#define ALBEDO_LAYER_VEC4S 10
#define ALBEDO_RING 64

// The filters have the kind codes from ALBEDO_KIND_BLOOM to ALBEDO_KIND_VIGNETTE, and the generators have the other codes.
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
#define ALBEDO_KIND_METEORS 15
#define ALBEDO_KIND_AURORA 16

#define ALBEDO_BLEND_NORMAL 0
#define ALBEDO_BLEND_ADD 1
#define ALBEDO_BLEND_SCREEN 2
#define ALBEDO_BLEND_MULTIPLY 3

#define ALBEDO_USE_NONE 0
#define ALBEDO_USE_MASK 1
#define ALBEDO_USE_SHOW 2

// The parts of a layer that its values turn on, which SkyShaderSource puts in each layer step, so that the compiler leaves out the parts that are off.
#define ALBEDO_FLAG_TWINKLE 1
#define ALBEDO_FLAG_TRAILS 2
#define ALBEDO_FLAG_SPIKES 4
#define ALBEDO_FLAG_PULSE 8
#define ALBEDO_FLAG_DUST 16
#define ALBEDO_FLAG_JETS 32

#ifdef ALBEDO_PLAIN_UNIFORMS
// The values of the uniform block below, for the GL programs that set them as three arrays.
uniform vec4 SkyHeader;
uniform vec4 SkyLayers[ALBEDO_MAX_LAYERS * ALBEDO_LAYER_VEC4S];
uniform vec4 SkyRing[ALBEDO_RING];
#else
layout(std140) uniform AlbedoSky {
    // The layer count, the extent (1 for the full sphere), the term count and the time in seconds (negative for a still sky).
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

// SkyGenerator.wave: a sine wave from -1 to 1 at a frequency in hertz and a phase in turns.
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
                uint hj = albedoHash(h, seed * 31 + salt + 1);
                vec3 v = vec3(h) + (vec3(float(hj >> 22u), float((hj >> 12u) & 1023u), float((hj >> 2u) & 1023u)) + 0.5) / 1024.0;
                float len = length(v);
                if (len < 1e-3) continue;
                vec3 s = v / len;
                // The star is the point of the cell moved onto the sphere, and the cell keeps it only when it stays in the cell.
                if (ivec3(floor(s * n)) != h) continue;
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
                    uint hj = albedoHash(cell, hashSeed + 1);
                    vec3 v = vec3(cell) + (vec3(float(hj >> 22u), float((hj >> 12u) & 1023u), float((hj >> 2u) & 1023u)) + 0.5) / 1024.0;
                    float len = length(v);
                    if (len < 1e-3) continue;
                    vec3 s = v / len;
                    if (ivec3(floor(s * n)) != cell) continue;
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
    vec3 up = vec3(albedoLayer(layer, 7).y, albedoLayer(layer, 8).y, albedoLayer(layer, 9).y);
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
    light *= albedoLayer(layer, 3).rgb;
    float peak = max(light.r, max(light.g, light.b));
    // The alpha carries the light up to 1 and the color carries the rest, so that an added layer adds all of the light.
    float a = min(1.0, peak);
    return vec4(a > 0.0 ? light / a : albedoLayer(layer, 3).rgb, a * shape.z);
}

// SkyGenerator.pulse: the factor of the color of a cloud layer that gets brighter and darker in some regions, each at its own phase.
float albedoPulse(int layer, vec3 p, float v) {
    float amount = albedoLayer(layer, 4).w;
    if (amount <= 0.0) return 1.0;
    vec4 shape = albedoLayer(layer, 2);
    int seed = int(shape.y);
    float n = albedoNoise(p * (1.6 / shape.x), seed + 71);
    float region = albedoSmooth(-0.1, 0.25, n);
    if (region <= 0.0) return 1.0;
    // One slow noise sets the regions, their phases and the weights of the three waves, so that each region has its own rhythm.
    float phase = 1.5 * n, m = 0.5 + 0.5 * sin(9.0 * n);
    // The clouds pulse at the time of the header, and at time 0 in a still sky, as in the bake.
    float time = max(SkyHeader.w, 0.0);
    float wave = (1.0 - m) * (1.0 - m) * albedoWave(1.0 / 12.0, phase, time)
            + 2.0 * m * (1.0 - m) * albedoWave(1.0 / 18.0, phase * 1.3, time) + m * m * albedoWave(1.0 / 25.0, phase * 1.6, time);
    return 1.0 + amount * 0.7 * region * wave * (0.4 + 0.6 * v);
}

// Clouds of fractal noise from COLOR_1 to COLOR_2, where AMOUNT is the part of the sky they cover.
vec4 albedoClouds(int layer, vec3 p, int flags) {
    vec4 values = albedoLayer(layer, 1);
    vec4 shape = albedoLayer(layer, 2);
    float v = albedoFbm(p * (2.5 / shape.x), int(shape.y), 6) * 0.5 + 0.5;
    vec4 c = albedoMix(layer, v);
    float edge = 1.0 - values.z * 1.2;
    c.a *= albedoSmooth(edge - 0.12, edge + 0.12, v);
    if ((flags & ALBEDO_FLAG_PULSE) != 0) c.rgb *= albedoPulse(layer, p, v);
    return c;
}

// Thin bright lines of ridged noise, bent by more noise, where AMOUNT is how much they bend.
vec4 albedoFractal(int layer, vec3 p, int flags) {
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
    if ((flags & ALBEDO_FLAG_PULSE) != 0) c.rgb *= albedoPulse(layer, p, v);
    return c;
}

// The numbers of the galaxy of SkyGenerator, in galaxy units where 1 is about the radius of the disc.
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

// An active galaxy at the layer center, modeled on Centaurus A and Hercules A, with a COLOR_2 bulge in a halo, COLOR_1 arms at TILT, dust and COLOR_3 jets.
vec4 albedoGalaxy(int layer, vec3 p, float footprint, int flags) {
    float zz = -p.z;
    if (zz < 0.2) return vec4(0.0);
    vec4 values = albedoLayer(layer, 1);
    vec4 shape = albedoLayer(layer, 2);
    int seed = int(shape.y);
    float tilt = albedoLayer(layer, 4).w;
    float dust = (flags & ALBEDO_FLAG_DUST) != 0 ? albedoLayer(layer, 5).w : 0.0;
    float jets = (flags & ALBEDO_FLAG_JETS) != 0 ? albedoLayer(layer, 6).w : 0.0;
    vec3 color1 = albedoLayer(layer, 3).rgb, color2 = albedoLayer(layer, 4).rgb;
    vec3 color3 = vec3(albedoLayer(layer, 7).w, albedoLayer(layer, 8).w, albedoLayer(layer, 9).w);
    // The plane of the image in galaxy units, where 1 is about the radius of the disc.
    float size = 0.22 * shape.x;
    float x = p.x / zz / size, y = p.y / zz / size;
    float rr = sqrt(x * x + y * y);
    if (rr > ALBEDO_GALAXY_REACH) return vec4(0.0);
    // The width of one pixel in galaxy units, where the plane of the image stretches by 1 / zz squared.
    float fp = footprint / (size * zz * zz);
    float incl = tilt * (ALBEDO_PI / 2.0);
    float ci = cos(incl), si = sin(incl);
    // The axis ratios of the disc of stars and of the thinner layer of dust, each with its own thickness.
    float qs = sqrt(ci * ci + 0.0225 * si * si), qd = sqrt(ci * ci + 0.0036 * si * si);
    float bs = y / qs, bd = y / qd;
    float rs = sqrt(x * x + bs * bs), rd = sqrt(x * x + bd * bd);
    float twist = 1.5 + values.z * 4.0;
    float star = 0.0, armMix = 0.0, tau = 0.0, knots = 0.0, pink = 0.0;
    if (rs < 1.6) {
        float warp = albedoFbmSoft(vec3(x * 3.0, bs * 3.0, 0.37), seed + 1, 7, fp / qs * 3.0);
        // The angle at the exact center is not defined in GLSL, and the arms have no weight there.
        float theta = rs > 0.0 ? atan(bs, x) : 0.0;
        float wave = 0.5 + 0.5 * cos(2.0 * (theta - twist * log(rs + 0.05)) + 1.4 * warp);
        // The arms start out of the smooth inner disc, so that the center has no curl.
        float arm = wave * wave * wave * albedoSmooth(0.2, 0.6, rs);
        float edge = 1.0 - albedoSmooth(1.1, 1.6, rs);
        float clumps = albedoSmooth(-0.4, 0.5, warp);
        star = exp(-rs / 0.4) * (0.08 + 2.0 * arm * (0.2 + 0.8 * clumps)) * edge;
        armMix = clamp(arm * 2.5, 0.0, 1.0);
        if (dust > 0.0 && rd < 1.4) {
            float thetaD = rd > 0.0 ? atan(bd, x) : 0.0;
            float lane = 0.5 + 0.5 * cos(2.0 * (thetaD - twist * log(rd + 0.05)) + 1.4 * warp + 0.7);
            lane *= lane;
            float clump = albedoFbmSoft(vec3(x * 6.0, bd * 6.0, 1.7), seed + 2, 5, fp / qd * 6.0) * 0.5 + 0.5;
            // A dense lane of dust in the plane of the disc over the center, and more dust in clumps along the inner edge of each arm.
            float density = (1.0 - albedoSmooth(0.9, 1.4, rd)) * (0.9 * exp(-rd / 0.45) * (0.5 + clump)
                    + exp(-rd / 0.5) * 2.0 * lane * lane * max(0.0, 2.0 * clump - 0.8));
            tau = dust * 7.0 * density / sqrt(qd);
        }
        if (fp < 0.5 * ALBEDO_GALAXY_KNOT_CELL) {
            // Knots of young stars on a grid of the image plane, few of them bright, which show only on the arms.
            vec2 g = vec2(x, y) / ALBEDO_GALAXY_KNOT_CELL;
            ivec2 cell = ivec2(floor(g));
            for (int i = -1; i <= 1; i++) {
                for (int j = -1; j <= 1; j++) {
                    ivec3 h = ivec3(cell.x + i, cell.y + j, 0);
                    if (albedoUnit(albedoHash(h, seed + 77)) > 0.5) continue;
                    float dx = (g.x - float(h.x) - 0.2 - 0.6 * albedoUnit(albedoHash(h, seed + 78))) * ALBEDO_GALAXY_KNOT_CELL;
                    float dy = (g.y - float(h.y) - 0.2 - 0.6 * albedoUnit(albedoHash(h, seed + 79))) * ALBEDO_GALAXY_KNOT_CELL;
                    float big = albedoUnit(albedoHash(h, seed + 80));
                    float radius = 0.003 + 0.006 * big;
                    float spread = max(radius, 0.8 * fp);
                    float v = (0.15 + 2.5 * big * big * big) * radius / spread * (radius / spread) * exp(-(dx * dx + dy * dy) / (spread * spread));
                    knots += v;
                    if (albedoUnit(albedoHash(h, seed + 81)) < 0.35) pink += v;
                }
            }
            // The knots are in groups, and fade out before a pixel is half a cell wide, where the search of 3 x 3 cells would cut a knot.
            float group = albedoSmooth(-0.05, 0.35, albedoNoise(vec3(x * 5.0, bs * 5.0, 2.9), seed + 82));
            float k = arm * group * exp(-rs / 0.5) * edge * 1.5
                    * (1.0 - albedoSmooth(0.25 * ALBEDO_GALAXY_KNOT_CELL, 0.5 * ALBEDO_GALAXY_KNOT_CELL, fp));
            knots *= k;
            pink *= k;
        }
    }
    float rb = sqrt(x * x + y * y / 0.64);
    // A small bright bulge in a large smooth halo of old stars, slightly longer along the jets, as the elliptical galaxy of Centaurus A.
    float rh = sqrt(x * x / 0.72 + y * y);
    float bulge = 1.8 * exp(-5.0 * sqrt(rb)) + ALBEDO_GALAXY_HALO * exp(-3.2 * sqrt(rh)) * (1.0 - albedoSmooth(1.5, 3.0, rr));
    // The part of the bulge light behind the dust, more on the near side of the disc, which is below its center, and most at the center.
    float s = 0.8 - y * 6.0 * si / max(ci, 0.25);
    float behind = 0.5 + 0.5 * s / sqrt(1.0 + s * s);
    float rn = max(0.006, fp);
    float nucleus = 2.5 * (0.006 / rn) * (0.006 / rn) * exp(-rr * rr / (rn * rn)) + 0.12 / (1.0 + rr * rr / 0.0009);
    float jet = 0.0, clear = 0.0, lobe = 0.0, cool = 0.0, gas = 0.0;
    float side = y < 0.0 ? -1.0 : 1.0;
    if (jets > 0.0) {
        float reach = ALBEDO_GALAXY_JET_LENGTH * (0.3 + 0.7 * si);
        float t = abs(y) / reach;
        // The jets bend in opposite directions, as a precessing jet does, and the near jet is brighter from relativistic beaming.
        float dx = x - side * 0.12 * t * t - 0.03 * t * albedoNoise(vec3(t * 5.0, side * 3.1, 0.6), seed + 502);
        float beam = side > 0.0 ? 1.0 : 0.3;
        // The part of the jet light that the dust does not dim: the far jet is behind the disc, and the near jet starts in it.
        clear = side > 0.0 ? albedoSmooth(0.02, 0.12, t) : 0.0;
        // Each jet is a cone that opens out, and its light goes into the lobe.
        float width = 0.005 + 0.03 * t;
        float spread = max(width, 0.8 * fp);
        float fade = 1.0 - albedoSmooth(0.25, 0.5, 16.0 / reach * fp);
        float knot = max(0.0, 0.55 + 0.7 * fade * albedoNoise(vec3(t * 16.0, side * 7.3, 1.7), seed + 501));
        jet = 1.4 * beam * width / spread * exp(-dx * dx / (spread * spread)) * knot / (1.0 + 3.0 * t) * (1.0 - albedoSmooth(0.6, 1.05, t));
        float turb = albedoFbmSoft(vec3(x * 2.4, y * 2.4, side * 5.1), seed + 601, 7, fp * 2.4);
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
        lobe = (exp(-2.0 * dl * dl) * body * 0.35 + hot) * jets;
        // The inner part of a lobe, where the jet ends, takes some of the jet color.
        cool = 0.4 * (1.0 - albedoSmooth(0.1, 0.9, dl));
        // Gas near the jet that its beam lights, less far from the axis.
        gas = 0.08 * thread * exp(-abs(dx) / (0.08 + 0.15 * t)) * albedoSmooth(0.1, 0.4, t) * (1.0 - albedoSmooth(0.9, 1.3, t));
        jet *= jets;
        gas *= jets;
    }
    vec3 extinction = tau * ALBEDO_GALAXY_DUST_REDDENING;
    vec3 through = exp(-extinction), mixed = 1.0 / (1.0 + 0.5 * extinction);
    vec3 disc = color2 + (color1 - color2) * armMix;
    vec3 light = bulge * color2 * (1.0 - behind * (1.0 - through)) + 0.9 * star * disc * mixed
            + ((knots - pink) * color1 + pink * ALBEDO_GALAXY_HALPHA) * mixed + nucleus * sqrt(through) * ALBEDO_GALAXY_NUCLEUS
            + jet * color3 * (through + (1.0 - through) * clear) + lobe * (ALBEDO_GALAXY_LOBE + (color3 - ALBEDO_GALAXY_LOBE) * cool) + gas * color3;
    light *= max(shape.z, shape.w);
    // The light as a color of at most 1 and an alpha, so that Add gives the full light and Normal stays near it.
    float a = min(1.0, max(light.r, max(light.g, light.b)));
    return vec4(light / max(a, 1e-6), a);
}

// The slots of time of the meteors, as in SkyMeteors: the slot length, the meteors in each slot and the slots that can still show one.
const float ALBEDO_METEOR_SLOT = 1.0;
#define ALBEDO_METEOR_CHANNELS 3
#define ALBEDO_METEOR_SLOTS_BACK 2
// The part of its life after which the trail of a meteor is gone; the head goes out at 1.
const float ALBEDO_METEOR_TRAIL_END = 1.15;

float albedoMeteorUnit(int slot, int channel, int value, int seed) {
    return albedoUnit(albedoHash(ivec3(slot, channel, value), seed));
}

// A line across a width in radians, made wider by half a pixel and darker by the same ratio, so that a thin line stays crisp.
float albedoMeteorLine(float d, float width, float footprint) {
    float w = sqrt(width * width + 0.25 * footprint * footprint);
    return exp(-(d / w) * (d / w)) * width / w;
}

// The linear light of the meteor of a slot and a channel in the sky direction q, with a hot head and a trail that fades and cools.
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

// Shooting stars at the time of the header, with no meteors in a still sky; the light above 1 goes into the color.
vec4 albedoMeteors(int layer, vec3 p, float footprint) {
    if (SkyHeader.w < 0.0) return vec4(0.0);
    // The meteors start and fall in the sky, so they work on the sky direction and use the layer frame only for the radiant.
    vec3 q = p.x * albedoLayer(layer, 7).xyz + p.y * albedoLayer(layer, 8).xyz + p.z * albedoLayer(layer, 9).xyz;
    if (q.y <= 0.0) return vec4(0.0);
    int now = int(floor(SkyHeader.w / ALBEDO_METEOR_SLOT));
    vec3 light = vec3(0.0);
    for (int slot = now - ALBEDO_METEOR_SLOTS_BACK; slot <= now; slot++) {
        for (int channel = 0; channel < ALBEDO_METEOR_CHANNELS; channel++) light += albedoMeteor(layer, slot, channel, q, footprint);
    }
    float a = clamp(max(light.r, max(light.g, light.b)), 0.0, 1.0);
    return a > 0.0 ? vec4(light / a, a) : vec4(0.0);
}

// Aurora.value: smooth value noise in two dimensions, from 0 to 1.
float albedoValue(vec2 q, int seed) {
    vec2 i = floor(q);
    ivec3 c = ivec3(int(i.x), int(i.y), 0);
    vec2 f = q - i;
    vec2 u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
    float a = albedoUnit(albedoHash(c, seed)), b = albedoUnit(albedoHash(c + ivec3(1, 0, 0), seed));
    float d = albedoUnit(albedoHash(c + ivec3(0, 1, 0), seed)), e = albedoUnit(albedoHash(c + ivec3(1, 1, 0), seed));
    float top = albedoLerp(a, b, u.x);
    return albedoLerp(top, albedoLerp(d, e, u.x), u.y);
}

// Aurora.octave: one octave of rays, which goes to its mean 0.5 as a ray gets narrower than about three pixels.
float albedoRayOctave(vec2 q, int seed, float width) {
    float k = 1.0 - albedoSmooth(0.18, 0.45, width);
    return k <= 0.0 ? 0.5 : 0.5 + (albedoValue(q, seed) - 0.5) * k;
}

// The numbers of Aurora: the earth radius, the altitude of the lower edge, the drift of the rays and the reach of a sheet to each side, in km.
const float ALBEDO_AURORA_EARTH = 6371.0;
const float ALBEDO_AURORA_BASE = 100.0;
const float ALBEDO_AURORA_DRIFT = 0.6;
const float ALBEDO_AURORA_LOOP = 0.5;
const float ALBEDO_AURORA_THICK = 14.0;
#define ALBEDO_AURORA_STEPS 14
#define ALBEDO_AURORA_CROSSINGS 4

// The distance in km of the nearest place of the near (0) and of the far (1) ribbon.
float albedoRibbonDistance(int c) {
    return c == 0 ? 280.0 : 620.0;
}

// The azimuth from the line through the nearest place of ribbon c at theta, so that each ribbon slants across the sky.
float albedoRibbonAngle(int c, float theta) {
    return clamp(theta - (c == 0 ? 0.45 : -0.35), -1.5, 1.5);
}

// One fold wave of Aurora.Ribbon.offset: adds its azimuth to x, and to d the slope of the azimuth and the loop toward the viewer and its slope.
float albedoFoldWave(int i, int c, float k, float amp, float rate, float theta, float t, int seed, inout vec3 d) {
    float arg = k * theta + 6.2831853 * albedoUnit(albedoHash(ivec3(i, c, 0), seed + 601)) + rate * t;
    float s = sin(arg), co = cos(arg);
    float loop = ALBEDO_AURORA_LOOP * albedoRibbonDistance(c) * amp;
    d += vec3(amp * k * co, -loop * co, loop * k * s);
    return amp * s;
}

// Aurora.Ribbon.offset: the azimuth of ribbon c at theta minus the azimuth a of the view, with the slopes and the loop in d.
float albedoRibbonOffset(int c, float theta, float a, float t, float scale, int seed, out vec3 d) {
    float f = albedoRibbonDistance(c) / albedoRibbonDistance(0);
    d = vec3(1.0, 0.0, 0.0);
    float phi = theta + albedoFoldWave(0, c, 8.0 * f / scale, 0.08 * scale / f, 0.02, theta, t, seed, d);
    phi += albedoFoldWave(1, c, 21.0 * f / scale, 0.04 * scale / f, -0.05, theta, t, seed, d);
    phi += albedoFoldWave(2, c, 43.0 * f / scale, 0.019 * scale / f, 0.11, theta, t, seed, d);
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

// Puts a group of pieces at index i of the kept groups of a ribbon, with its place theta and its light k.
void albedoCrossing(int i, float theta, float k, inout vec4 thetas, inout vec4 ks) {
    if (i == 0) {
        thetas.x = theta;
        ks.x = k;
    } else if (i == 1) {
        thetas.y = theta;
        ks.y = k;
    } else if (i == 2) {
        thetas.z = theta;
        ks.z = k;
    } else if (i == 3) {
        thetas.w = theta;
        ks.w = k;
    }
}

// Aurora.moment: the first moment of the part of a triangle of width 2 around 0 that lies left of x.
float albedoTriangleMoment(float x) {
    float y = clamp(x, -1.0, 1.0);
    return y < 0.0 ? y * y * (0.5 + y / 3.0) - 1.0 / 6.0 : y * y * (0.5 - y / 3.0) - 1.0 / 6.0;
}

// Aurora.Ribbon.flush: keeps the group of pieces in place of the faintest kept group when all places are taken by brighter ones.
void albedoAuroraFlush(inout vec4 group, inout int found, inout vec4 thetas, inout vec4 ks) {
    if (group.x > 0.0) {
        int slot = found;
        if (found == ALBEDO_AURORA_CROSSINGS) {
            slot = 0;
            for (int i = 1; i < ALBEDO_AURORA_CROSSINGS; i++) {
                if (ks[i] < ks[slot]) slot = i;
            }
            if (ks[slot] >= group.x) slot = -1;
        } else {
            found++;
        }
        if (slot >= 0) albedoCrossing(slot, group.w > 0.0 ? group.z : group.y / group.x, group.x, thetas, ks);
    }
    group = vec4(0.0);
}

// Aurora.Ribbon.piece: adds the light of one straight piece of a ribbon from ta to tb, with the azimuths ga and gb from the view and the
// speeds sa and sb, to the group of pieces (its light, its light times the place of its light, where the view crosses it and 1 when it does).
void albedoAuroraPiece(float ta, float ga, float sa, float tb, float gb, float sb, float w, inout vec4 group, inout bool rising,
        inout int found, inout vec4 thetas, inout vec4 ks) {
    bool through = (ga <= 0.0) != (gb <= 0.0);
    float xa = ga / w, xb = gb / w, dx = xb - xa, k = 0.0, mid = 0.5;
    if (min(abs(xa), abs(xb)) < 1.0 || through) {
        // The light along the view is the length of the piece near it, so a piece seen edge-on is brighter.
        float part = albedoTriangleBelow(xb) - albedoTriangleBelow(xa);
        k = abs(dx) < 1e-3 ? max(0.0, 1.0 - abs(xa)) : part / dx;
        k *= abs(tb - ta) / w * 0.5 * (sa + sb);
        // The middle of the light of the piece, which moves smoothly with the view.
        if (abs(dx) >= 1e-3 && part != 0.0) {
            mid = clamp((albedoTriangleMoment(xb) - albedoTriangleMoment(xa) - xa * part) / (dx * part), 0.0, 1.0);
        }
    }
    // A group ends where the piece gets farther from the view and the next one nearer, or where a piece has no light.
    if (k <= 0.0 || rising && (through || abs(gb) < abs(ga))) albedoAuroraFlush(group, found, thetas, ks);
    rising = through || abs(gb) > abs(ga);
    if (k <= 0.0) return;
    group.x += k;
    group.y += k * (ta + (tb - ta) * mid);
    if (through) group.zw = vec2(ta + (tb - ta) * ga / (ga - gb), 1.0);
}

// Aurora.Ribbon.add: the groups of pieces of ribbon c near the view, found in steps over the azimuths of the folds, and their count.
int albedoCrossings(int c, float wide, float a, float t, float scale, int seed, out vec4 thetas, out vec4 ks) {
    thetas = vec4(0.0);
    ks = vec4(0.0);
    float w = max(3.0 * wide, ALBEDO_AURORA_THICK / albedoRibbonDistance(c));
    float reach = 0.139 * scale / (albedoRibbonDistance(c) / albedoRibbonDistance(0)) * 1.02 + w;
    float step = 2.0 * reach / float(ALBEDO_AURORA_STEPS);
    vec3 d;
    float ta = a - reach, ga = albedoRibbonOffset(c, ta, a, t, scale, seed, d), da = d.x, sa = albedoRibbonSpeed(c, ta, d);
    int found = 0;
    vec4 group = vec4(0.0);
    bool rising = false;
    for (int i = 1; i <= ALBEDO_AURORA_STEPS; i++) {
        float tb = a - reach + float(i) * step, gb = albedoRibbonOffset(c, tb, a, t, scale, seed, d), db = d.x;
        float sb = albedoRibbonSpeed(c, tb, d);
        if ((da < 0.0) != (db < 0.0)) {
            // The tip of a fold is in this step, which gets a corner there, found in two steps toward the zero of the slope.
            float tm = ta + step * da / (da - db);
            albedoRibbonOffset(c, tm, a, t, scale, seed, d);
            float dm = d.x;
            tm = (da < 0.0) != (dm < 0.0) ? ta + (tm - ta) * da / (da - dm) : tm + (tb - tm) * dm / (dm - db);
            float gm = albedoRibbonOffset(c, tm, a, t, scale, seed, d), sm = albedoRibbonSpeed(c, tm, d);
            albedoAuroraPiece(ta, ga, sa, tm, gm, sm, w, group, rising, found, thetas, ks);
            albedoAuroraPiece(tm, gm, sm, tb, gb, sb, w, group, rising, found, thetas, ks);
        } else {
            albedoAuroraPiece(ta, ga, sa, tb, gb, sb, w, group, rising, found, thetas, ks);
        }
        ta = tb;
        ga = gb;
        da = db;
        sa = sb;
    }
    albedoAuroraFlush(group, found, thetas, ks);
    return found;
}

// Aurora.Ribbon.sheet: the light of COLOR_1 and COLOR_2 of the sheet of ribbon c where the view crosses it at theta, times k.
vec2 albedoSheet(int c, float theta, float k, float el, float wide, float footprint, float a, float t, float scale, float height,
        int seed) {
    vec3 d;
    albedoRibbonOffset(c, theta, a, t, scale, seed, d);
    float dist = albedoRibbonDistance(c), f = dist / albedoRibbonDistance(0);
    float th = albedoRibbonAngle(c, theta);
    float co = cos(th), ta = tan(th);
    float r = dist / co + d.y, dr = dist * ta / co + d.z;
    for (int i = 0; i < 2; i++) {
        float kk = (i == 0 ? 5.0 : 17.0) * f / scale;
        float arg = kk * theta + 6.2831853 * albedoUnit(albedoHash(ivec3(i, c, 1), seed + 602)) + (i == 0 ? -0.015 : 0.04) * t;
        float amp = i == 0 ? 30.0 : 10.0;
        r += amp * sin(arg);
        dr += amp * kk * cos(arg);
    }
    // The sine of the angle between the view and the sheet, which makes the rays narrower in the sky.
    float across = abs(r * d.x);
    float obl = across / sqrt(dr * dr + across * across);
    float tilt = min(el + r / (2.0 * ALBEDO_AURORA_EARTH), 1.45);
    float alt = r * tan(tilt);
    float cosT = cos(tilt);
    float pixelUp = footprint * r / (cosT * cosT), pixelAlong = wide * r / max(obl, 0.02) / scale;
    float s = dist * ta / scale, sd = s + ALBEDO_AURORA_DRIFT * t;
    // The lower edge is nearly level, with gentle waves, as its kinks come from the folds.
    float h = alt - ALBEDO_AURORA_BASE - 2.0 * albedoNoise(vec3(sd / 40.0, t * 0.02, 0.5), seed + 11 + c)
            - 0.7 * (1.0 - albedoSmooth(0.15, 0.4, pixelAlong / 12.0)) * albedoNoise(vec3(sd / 12.0, t * 0.05, 2.5), seed + 13 + c);
    if (h < -4.0) return vec2(0.0);
    float r0 = albedoRayOctave(vec2(sd / 40.0, t * 0.03), seed + 21 + c, pixelAlong / 40.0);
    float r1 = albedoRayOctave(vec2(sd / 3.0, t * 0.1), seed + 22 + c, pixelAlong / 3.0);
    float r2 = albedoRayOctave(vec2(sd / 1.1, t * 0.25), seed + 23 + c, pixelAlong / 1.1);
    float r3 = albedoRayOctave(vec2(sd / 0.5, t * 0.6), seed + 24 + c, pixelAlong / 0.5);
    float tall = albedoRayOctave(vec2(sd / 2.0 + 31.7, t * 0.12), seed + 25 + c, pixelAlong / 2.0);
    // Wide bundles of thin rays with dark sky between them, whose edges are never harder than about one pixel.
    float bundle = 0.35 + 0.65 * albedoSmooth(0.2, 0.8, r0);
    float hard = clamp(pixelAlong / 0.5 * 0.5, 0.05, 0.3);
    float rays = bundle * albedoSmooth(0.55 - hard, 0.55 + hard, 0.3 * r1 + 0.38 * r2 + 0.32 * r3);
    // Each ray fades up over its own height, and the far ribbon is shorter, so that it is a low arc.
    float top = height * (0.07 + 0.45 * tall * tall) / (f * f);
    float soft = max(0.3 * f, 0.7 * pixelUp);
    float below = albedoSmooth(-2.0 * soft, soft, h), up = max(h, 0.0);
    // The brightest light is a thin band at the lower edge, and above it the rays dissolve into the open sky.
    float base = (0.15 + 0.4 * bundle + 0.8 * rays) * exp(-up / 3.5);
    float rise = up / top;
    float green = below * (base + 1.1 * rays * exp(-rise * (0.5 + 0.5 * rise)));
    float red = below * 0.25 * rays * tall * exp(-(rise - 1.3) * (rise - 1.3) * 2.0);
    // Parts of the ribbon are bright, faint or gaps of open sky, with wider gaps in the far ribbon and none at the center.
    float lit = albedoValue(vec2(s / 320.0, t * 0.004), seed + 31 + c) + (c == 0 ? 0.35 * exp(-th * th / 0.25) : 0.0);
    float strength = albedoSmooth(0.3 + 0.15 * float(c), 0.6 + 0.15 * float(c), lit)
            * (0.4 + 0.9 * albedoValue(vec2(s / 70.0, t * 0.01), seed + 33 + c));
    return k * (c == 0 ? 1.0 : 0.2) * strength * vec2(green, red);
}

// Aurora.sample: a near and a far folded ribbon toward the layer center, which move with the header time and stay at time 0 when still.
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
    float t = max(SkyHeader.w, 0.0) * albedoLayer(layer, 4).w * 2.0;
    float height = 40.0 + 220.0 * values.w;
    float wide = footprint / max(cosEl, 0.05);
    // The search finds the groups first and then lights them in one loop, so that the shader has one copy of the sheet.
    vec2 light = vec2(0.0);
    for (int c = 0; c < 2; c++) {
        // The far ribbon with its short rays is never more than about 15 degrees high.
        if (c == 1 && el >= 0.3) break;
        vec4 thetas, ks;
        int found = albedoCrossings(c, wide, a, t, shape.x, seed, thetas, ks);
        for (int i = 0; i < ALBEDO_AURORA_CROSSINGS; i++) {
            if (i >= found) break;
            light += albedoSheet(c, thetas[i], ks[i], el, wide, footprint, a, t, shape.x, height, seed);
        }
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

#endif
