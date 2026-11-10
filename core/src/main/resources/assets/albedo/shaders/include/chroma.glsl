#ifndef ALBEDO_CHROMA_GLSL
#define ALBEDO_CHROMA_GLSL

#include <albedo:oklab.glsl>

// The Chroma color of an atlas texel, which is the pixel that Albedo painted with the hue of its kind turned on in each
// frame, with the same color math as Recolor.lab and Recolor.srgb of the core.

// The number of vec4 values that hold the turn of each Chroma speed from 0 to 100.
#define ALBEDO_CHROMA_VEC4S 26
// The OKLab chroma of an amount of 255, the same as ChromaTexels.MAX_AMOUNT.
#define ALBEDO_CHROMA_MAX_AMOUNT 0.26

bool albedoChromaInGamut(vec3 rgb) {
    return all(greaterThanEqual(rgb, vec3(-1e-6))) && all(lessThanEqual(rgb, vec3(1.0 + 1e-6)));
}

// The sRGB color of an OKLab color, with less chroma at the same lightness and hue when it is out of gamut.
vec3 albedoChromaSrgb(vec3 lab) {
    vec3 rgb = albedoLabToLinear(lab);
    if (!albedoChromaInGamut(rgb)) {
        float lo = 0.0;
        float hi = 1.0;
        for (int i = 0; i < 8; i++) {
            float mid = (lo + hi) * 0.5;
            if (albedoChromaInGamut(albedoLabToLinear(vec3(lab.x, lab.yz * mid)))) lo = mid;
            else hi = mid;
        }
        rgb = albedoLabToLinear(vec3(lab.x, lab.yz * lo));
    }
    rgb = clamp(rgb, 0.0, 1.0);
    return vec3(albedoLinearToSrgb(rgb.r), albedoLinearToSrgb(rgb.g), albedoLinearToSrgb(rgb.b));
}

// The speed of a texel from 0 to 100, where 0 means that the pass leaves the pixel as it is.
int albedoChromaSpeed(vec4 texel) {
    return int(texel.a * 255.0 + 0.5);
}

// The part of one turn of a speed, from the vec4 that holds it.
float albedoChromaTurn(vec4 turns, int speed) {
    int i = speed - (speed / 4) * 4;
    return dot(turns, vec4(equal(ivec4(i), ivec4(0, 1, 2, 3))));
}

// The pixel of the pristine atlas with the hue of its kind at a turn added in OKLab, with the same alpha.
vec4 albedoChroma(vec4 color, vec4 texel, float turn) {
    if (color.a == 0.0) return color;
    float hue = (floor(texel.r * 255.0 + 0.5) * 256.0 + floor(texel.g * 255.0 + 0.5)) / 65536.0 + turn;
    float angle = 6.283185307 * fract(hue);
    vec3 rgb = vec3(albedoSrgbToLinear(color.r), albedoSrgbToLinear(color.g), albedoSrgbToLinear(color.b));
    vec3 lab = albedoLinearToLab(rgb);
    lab.yz += texel.b * ALBEDO_CHROMA_MAX_AMOUNT * vec2(cos(angle), sin(angle));
    return vec4(albedoChromaSrgb(lab), color.a);
}

#endif
