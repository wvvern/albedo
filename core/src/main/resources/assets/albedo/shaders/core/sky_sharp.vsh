#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;

// The point on the sky sphere around the camera, whose direction is the direction of the sky.
layout(location = 0) out vec3 skyPosition;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    skyPosition = Position;
}
