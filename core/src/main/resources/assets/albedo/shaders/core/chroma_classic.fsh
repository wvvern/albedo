// Writes the Chroma color of each moving texel into the atlas for the GL programs of 1.21.11, 1.21.1 and 1.8.9.

#include <albedo:chroma.glsl>

// The atlas as Albedo painted it and the Chroma texels, both with the mip level of the pass as their base level.
uniform sampler2D AlbedoPristine;
uniform sampler2D AlbedoTexels;

#ifdef ALBEDO_PLAIN_UNIFORMS
uniform vec4 ChromaTurns[ALBEDO_CHROMA_VEC4S];
#else
layout(std140) uniform AlbedoChroma {
    vec4 ChromaTurns[ALBEDO_CHROMA_VEC4S];
};
#endif

#if __VERSION__ >= 130
#define ALBEDO_FETCH(s) texelFetch(s, ivec2(gl_FragCoord.xy), 0)
#else
// The size of the mip level of the pass, because GLSL 1.20 has no texelFetch.
uniform vec2 ChromaSize;
#define ALBEDO_FETCH(s) texture2D(s, gl_FragCoord.xy / ChromaSize)
#endif

#if __VERSION__ >= 150
out vec4 fragColor;
#define ALBEDO_FRAG_COLOR fragColor
#else
#define ALBEDO_FRAG_COLOR gl_FragColor
#endif

void main() {
    vec4 texel = ALBEDO_FETCH(AlbedoTexels);
    int speed = albedoChromaSpeed(texel);
    if (speed == 0) discard;
    ALBEDO_FRAG_COLOR = albedoChroma(ALBEDO_FETCH(AlbedoPristine), texel, albedoChromaTurn(ChromaTurns[speed / 4], speed), gl_FragCoord.xy);
}
