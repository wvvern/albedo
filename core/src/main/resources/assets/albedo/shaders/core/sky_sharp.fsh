#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <albedo:sky_layers.glsl>

layout(location = 0) in vec3 skyPosition;

layout(location = 0) out vec4 fragColor;

void main() {
    vec3 dir = normalize(skyPosition);
    // The angle across one pixel, outside all branches so that the derivatives are defined.
    float footprint = max(length(dFdx(dir)), length(dFdy(dir)));
    vec3 color = albedoEncode(albedoSky(dir, footprint), ivec2(gl_FragCoord.xy));
    fragColor = vec4(color * ColorModulator.rgb, 1.0);
}
