#version 330
#extension GL_ARB_separate_shader_objects : require

#include <albedo:sky_layers.glsl>

layout(location = 0) in vec2 skyUv;
layout(location = 1) in vec3 viewPosition;

layout(location = 0) out vec4 fragColor;

// Draws the sky into the equirectangular image of the CPU bake, so that a test can compare the two, or into a camera view.
void main() {
    float lon = skyUv.x * 2.0 * ALBEDO_PI;
    float el = radians(90.0 - skyUv.y * 180.0);
    bool view = dot(viewPosition, viewPosition) > 0.0;
    vec3 dir = view ? normalize(viewPosition) : vec3(cos(el) * sin(lon), sin(el), -cos(el) * cos(lon));
    // The width of one pixel of the view, or of one texel at the horizon as the bake uses for its stars.
    float footprint = view ? max(length(dFdx(dir)), length(dFdy(dir))) : 2.0 * ALBEDO_PI * abs(dFdx(skyUv.x));
    fragColor = vec4(albedoEncode(albedoSky(dir, footprint), ivec2(gl_FragCoord.xy)), 1.0);
}
