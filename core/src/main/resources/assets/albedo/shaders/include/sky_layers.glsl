#ifndef ALBEDO_SKY_LAYERS_GLSL
#define ALBEDO_SKY_LAYERS_GLSL

// The custom sky for each pixel, a port of dev.albedo.core.sky.SkyGenerator that reads the block of SkyProgram.

#define ALBEDO_MAX_LAYERS 8
#define ALBEDO_LAYER_VEC4S 10
#define ALBEDO_RING 64

#define ALBEDO_KIND_GRADIENT 1
#define ALBEDO_KIND_GLOW 2
#define ALBEDO_KIND_STARS 3

#define ALBEDO_BLEND_NORMAL 0
#define ALBEDO_BLEND_ADD 1
#define ALBEDO_BLEND_SCREEN 2
#define ALBEDO_BLEND_MULTIPLY 3

#define ALBEDO_USE_NONE 0
#define ALBEDO_USE_MASK 1
#define ALBEDO_USE_SHOW 2

layout(std140) uniform AlbedoSky {
    // The layer count and the extent (1 for the full sphere).
    vec4 SkyHeader;
    vec4 SkyLayers[ALBEDO_MAX_LAYERS * ALBEDO_LAYER_VEC4S];
    // The linear horizon color of each azimuth sector, from north toward east.
    vec4 SkyRing[ALBEDO_RING];
};

const float ALBEDO_PI = 3.14159265358979;
// The star grid density per radian at which the star sizes are set.
const float ALBEDO_STAR_CELLS = 36.0;
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

// Points in a jittered 3D grid on the layer sphere, where the smallest star is 1.5 footprints across.
vec4 albedoStars(int layer, vec3 p, float footprint) {
    vec4 values = albedoLayer(layer, 1);
    vec4 shape = albedoLayer(layer, 2);
    int seed = int(shape.y);
    float n = (10.0 + values.z * 40.0) / shape.x;
    vec3 q = p * n;
    ivec3 cell = ivec3(floor(q));
    float grow = n * shape.x / ALBEDO_STAR_CELLS;
    float minRadius = 1.5 * footprint * n;
    float best = 0.0;
    float warm = 0.0;
    for (int i = -1; i <= 1; i++) {
        for (int j = -1; j <= 1; j++) {
            for (int k = -1; k <= 1; k++) {
                ivec3 h = cell + ivec3(i, j, k);
                if (albedoUnit(albedoHash(h, seed * 31 + 7)) > 0.15) continue;
                vec3 s = vec3(h) + 0.15 + 0.7 * vec3(albedoUnit(albedoHash(h, seed + 101)), albedoUnit(albedoHash(h, seed + 202)),
                        albedoUnit(albedoHash(h, seed + 303)));
                float len = length(s);
                if (len < 1e-3) continue;
                s = s / len * n;
                float d = length(q - s);
                float size = albedoUnit(albedoHash(h, seed + 404));
                float base = (0.05 + 0.22 * size * size * size) * grow;
                float radius = max(minRadius, base);
                if (d >= radius) continue;
                float bright = (0.15 + 0.85 * size * size) * min(1.0, 2.0 * base / radius);
                float v = bright * (1.0 - albedoSmooth(radius * 0.35, radius, d));
                if (v > best) {
                    best = v;
                    warm = albedoUnit(albedoHash(h, seed + 505)) - 0.5;
                }
            }
        }
    }
    vec3 c = albedoLayer(layer, 3).rgb;
    return vec4(c.r * (1.0 + warm * 0.3), c.g, c.b * (1.0 - warm * 0.3), clamp(best, 0.0, 1.0) * shape.z);
}

vec4 albedoGenerate(int layer, int kind, vec3 p, float footprint) {
    if (kind == ALBEDO_KIND_GRADIENT) return albedoGradient(layer, p);
    if (kind == ALBEDO_KIND_GLOW) return albedoGlow(layer, p);
    if (kind == ALBEDO_KIND_STARS) return albedoStars(layer, p, footprint);
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
            vec3 p = vec3(dot(albedoLayer(i, 7).xyz, dir), dot(albedoLayer(i, 8).xyz, dir), dot(albedoLayer(i, 9).xyz, dir));
            vec4 s = albedoGenerate(i, int(head.x), p, footprint);
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

// One linear channel in sRGB from 0 to 255, as Oklab.toSrgb.
float albedoToSrgb(float linear) {
    float c = clamp(linear, 0.0, 1.0);
    return (c <= 0.0031308 ? c * 12.92 : 1.055 * pow(c, 1.0 / 2.4) - 0.055) * 255.0;
}

// The sRGB color of a linear color with the hash dither of the bake, which hides the bands of slow gradients.
vec3 albedoEncode(vec3 linear, ivec2 pixel) {
    float d = albedoUnit(albedoHash(ivec3(pixel, 0), 77)) - 0.5;
    vec3 srgb = vec3(albedoToSrgb(linear.r), albedoToSrgb(linear.g), albedoToSrgb(linear.b)) + d;
    return clamp(floor(srgb + 0.5), 0.0, 255.0) / 255.0;
}

#endif
