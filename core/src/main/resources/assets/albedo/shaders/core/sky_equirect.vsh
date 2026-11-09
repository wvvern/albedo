#version 330
#extension GL_ARB_separate_shader_objects : require

layout(location = 1) in vec2 UV0;

// The place in the equirectangular image, from the top left corner.
layout(location = 0) out vec2 skyUv;

void main() {
    gl_Position = vec4(UV0.x * 2.0 - 1.0, 1.0 - UV0.y * 2.0, 0.0, 1.0);
    skyUv = UV0;
}
