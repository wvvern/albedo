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
// The values of the uniform block below, for the GL programs that set each part on its own (SkyProgram.Uniform).
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

#include <albedo:sky_noise.glsl>

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

#include <albedo:sky_stars.glsl>

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

#include <albedo:sky_galaxy.glsl>

#include <albedo:sky_deep_field.glsl>

#include <albedo:sky_meteors.glsl>

#include <albedo:sky_aurora.glsl>

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
