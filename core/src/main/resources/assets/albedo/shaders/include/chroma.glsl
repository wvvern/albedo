#ifndef ALBEDO_CHROMA_GLSL
#define ALBEDO_CHROMA_GLSL

// The Chroma color of an atlas texel, which is the pixel that Albedo painted with the hue of its kind turned on in each
// frame, with the same color math as Recolor.lab and Recolor.srgb of the core.

// The number of vec4 values that hold the turn of each Chroma speed from 0 to 100.
#define ALBEDO_CHROMA_VEC4S 26
// The OKLab chroma of an amount of 255, the same as ChromaTexels.MAX_AMOUNT.
#define ALBEDO_CHROMA_MAX_AMOUNT 0.26

float albedoChromaDecode(float c) {
    return c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4);
}

float albedoChromaEncode(float c) {
    return c <= 0.0031308 ? c * 12.92 : 1.055 * pow(c, 1.0 / 2.4) - 0.055;
}

// OKLab of a linear sRGB color.
vec3 albedoChromaLab(vec3 rgb) {
    float l = pow(max(0.4122214708 * rgb.r + 0.5363325363 * rgb.g + 0.0514459929 * rgb.b, 0.0), 1.0 / 3.0);
    float m = pow(max(0.2119034982 * rgb.r + 0.6806995451 * rgb.g + 0.1073969566 * rgb.b, 0.0), 1.0 / 3.0);
    float s = pow(max(0.0883024619 * rgb.r + 0.2817188376 * rgb.g + 0.6299787005 * rgb.b, 0.0), 1.0 / 3.0);
    return vec3(0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
                1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
                0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s);
}

// The linear sRGB color of an OKLab color, which can be out of gamut.
vec3 albedoChromaLinear(vec3 lab) {
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

bool albedoChromaInGamut(vec3 rgb) {
    return all(greaterThanEqual(rgb, vec3(-1e-6))) && all(lessThanEqual(rgb, vec3(1.0 + 1e-6)));
}

// The sRGB color of an OKLab color, with less chroma at the same lightness and hue when it is out of gamut.
vec3 albedoChromaSrgb(vec3 lab) {
    vec3 rgb = albedoChromaLinear(lab);
    if (!albedoChromaInGamut(rgb)) {
        float lo = 0.0;
        float hi = 1.0;
        for (int i = 0; i < 8; i++) {
            float mid = (lo + hi) * 0.5;
            if (albedoChromaInGamut(albedoChromaLinear(vec3(lab.x, lab.yz * mid)))) lo = mid;
            else hi = mid;
        }
        rgb = albedoChromaLinear(vec3(lab.x, lab.yz * lo));
    }
    rgb = clamp(rgb, 0.0, 1.0);
    return vec3(albedoChromaEncode(rgb.r), albedoChromaEncode(rgb.g), albedoChromaEncode(rgb.b));
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
    vec3 rgb = vec3(albedoChromaDecode(color.r), albedoChromaDecode(color.g), albedoChromaDecode(color.b));
    vec3 lab = albedoChromaLab(rgb);
    lab.yz += texel.b * ALBEDO_CHROMA_MAX_AMOUNT * vec2(cos(angle), sin(angle));
    return vec4(albedoChromaSrgb(lab), color.a);
}

#endif
