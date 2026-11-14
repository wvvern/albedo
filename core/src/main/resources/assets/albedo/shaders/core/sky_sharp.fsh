#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <albedo:sky_layers.glsl>

layout(location = 0) in vec3 skyPosition;

layout(location = 0) out vec4 fragColor;

void main() {
    fragColor = vec4(albedoSharpPixel(skyPosition, ivec2(gl_FragCoord.xy)) * ColorModulator.rgb, 1.0);
}
