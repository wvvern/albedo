#ifndef ALBEDO_CLASSIC_VERTEX_GLSL
#define ALBEDO_CLASSIC_VERTEX_GLSL

// The output word of a classic vertex shader for its GLSL version.
#if __VERSION__ >= 130
#define ALBEDO_OUT out
#else
#define ALBEDO_OUT varying
#endif

#endif
