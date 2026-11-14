#version 330
#extension GL_ARB_separate_shader_objects : require

#include <albedo:sky_layers.glsl>

layout(location = 0) in vec2 skyUv;
layout(location = 1) in vec3 viewPosition;

layout(location = 0) out vec4 fragColor;

// Draws the sky into the equirectangular image of the CPU bake, so that a test can compare the two, or into a camera view.
void main() {
    fragColor = vec4(albedoEquirectPixel(skyUv, viewPosition, ivec2(gl_FragCoord.xy)), 1.0);
}
