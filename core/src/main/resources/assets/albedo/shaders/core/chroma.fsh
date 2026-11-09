#version 330
#extension GL_ARB_separate_shader_objects : require

#include <albedo:chroma.glsl>

// The atlas as Albedo painted it and the Chroma texels, both at the mip level of the pass.
uniform sampler2D AlbedoPristine;
uniform sampler2D AlbedoTexels;

layout(std140) uniform AlbedoChroma {
    vec4 ChromaTurns[ALBEDO_CHROMA_VEC4S];
};

layout(location = 0) out vec4 fragColor;

// Writes the Chroma color of each moving texel into the atlas, and leaves the other texels as they are.
void main() {
    ivec2 at = ivec2(gl_FragCoord.xy);
    vec4 texel = texelFetch(AlbedoTexels, at, 0);
    int speed = albedoChromaSpeed(texel);
    if (speed == 0) discard;
    fragColor = albedoChroma(texelFetch(AlbedoPristine, at, 0), texel, albedoChromaTurn(ChromaTurns[speed / 4], speed));
}
