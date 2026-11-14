// Draws the sky into the equirectangular image of the CPU bake, or into a camera view, for the classic GL programs.

#include <albedo:sky_layers.glsl>

#if __VERSION__ >= 130
in vec2 skyUv;
in vec3 viewPosition;
#else
varying vec2 skyUv;
varying vec3 viewPosition;
#endif

#if __VERSION__ >= 150
out vec4 fragColor;
#define ALBEDO_FRAG_COLOR fragColor
#else
#define ALBEDO_FRAG_COLOR gl_FragColor
#endif

void main() {
    float lon = skyUv.x * 2.0 * ALBEDO_PI;
    float el = radians(90.0 - skyUv.y * 180.0);
    bool view = dot(viewPosition, viewPosition) > 0.0;
    vec3 dir = view ? normalize(viewPosition) : vec3(cos(el) * sin(lon), sin(el), -cos(el) * cos(lon));
    // The width of one pixel of the view, or of one texel at the horizon as the bake uses for its stars.
    float footprint = view ? max(length(dFdx(dir)), length(dFdy(dir))) : 2.0 * ALBEDO_PI * abs(dFdx(skyUv.x));
    ALBEDO_FRAG_COLOR = vec4(albedoEncode(albedoSky(dir, footprint), ivec2(gl_FragCoord.xy)), 1.0);
}
