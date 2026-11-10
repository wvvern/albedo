#version 330
#extension GL_ARB_separate_shader_objects : require

// One mip level of the atlas or of its pristine copy.
uniform sampler2D AlbedoSource;

layout(location = 0) out vec4 fragColor;

// Copies each texel of a mip level as it is into the same mip level of the other texture.
void main() {
    fragColor = texelFetch(AlbedoSource, ivec2(gl_FragCoord.xy), 0);
}
