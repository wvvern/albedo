#version 330
#extension GL_ARB_separate_shader_objects : require

// Black on the sky sphere of sky_sharp.vsh, which resets only the pixels at the far depth before the sun, the moon and the stars.

layout(location = 0) in vec3 skyPosition;

layout(location = 0) out vec4 fragColor;

void main() {
    fragColor = vec4(0.0);
}
