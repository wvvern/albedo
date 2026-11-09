#version 330
#extension GL_ARB_separate_shader_objects : require

#include <albedo:sky_layers.glsl>

layout(location = 0) in vec2 skyUv;

layout(location = 0) out vec4 fragColor;

// Draws the sky into the equirectangular image of the CPU bake, so that a test can compare the two.
void main() {
    // The width of one texel at the horizon, as the bake uses for its stars.
    float footprint = 2.0 * ALBEDO_PI * abs(dFdx(skyUv.x));
    float lon = skyUv.x * 2.0 * ALBEDO_PI;
    float el = radians(90.0 - skyUv.y * 180.0);
    vec3 dir = vec3(cos(el) * sin(lon), sin(el), -cos(el) * cos(lon));
    fragColor = vec4(albedoEncode(albedoSky(dir, footprint), ivec2(gl_FragCoord.xy)), 1.0);
}
