#version 330
#extension GL_ARB_separate_shader_objects : require

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;

// The place in the equirectangular image, from the top left corner.
layout(location = 0) out vec2 skyUv;
// The sky direction of a corner of a camera view, or zero for the equirectangular image.
layout(location = 1) out vec3 viewPosition;

void main() {
    gl_Position = vec4(UV0.x * 2.0 - 1.0, 1.0 - UV0.y * 2.0, 0.0, 1.0);
    skyUv = UV0;
    viewPosition = Position;
}
