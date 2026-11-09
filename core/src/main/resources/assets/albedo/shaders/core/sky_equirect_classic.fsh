// Draws the sky into the equirectangular image of the CPU bake for the GL programs of 1.21.11, 1.21.1 and 1.8.9.

#include <albedo:sky_layers.glsl>

#if __VERSION__ >= 130
in vec2 skyUv;
#else
varying vec2 skyUv;
#endif

#if __VERSION__ >= 150
out vec4 fragColor;
#define ALBEDO_FRAG_COLOR fragColor
#else
#define ALBEDO_FRAG_COLOR gl_FragColor
#endif

void main() {
    // The width of one texel at the horizon, as the bake uses for its stars.
    float footprint = 2.0 * ALBEDO_PI * abs(dFdx(skyUv.x));
    float lon = skyUv.x * 2.0 * ALBEDO_PI;
    float el = radians(90.0 - skyUv.y * 180.0);
    vec3 dir = vec3(cos(el) * sin(lon), sin(el), -cos(el) * cos(lon));
    ALBEDO_FRAG_COLOR = vec4(albedoEncode(albedoSky(dir, footprint), ivec2(gl_FragCoord.xy)), 1.0);
}
