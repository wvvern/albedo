#ifndef ALBEDO_CLASSIC_FRAGMENT_GLSL
#define ALBEDO_CLASSIC_FRAGMENT_GLSL

// The output and the input word of a classic fragment shader for its GLSL version.

// A core profile needs its own output, and GLSL 1.30 and earlier in a compatibility profile use gl_FragColor.
#if __VERSION__ >= 150
out vec4 fragColor;
#define ALBEDO_FRAG_COLOR fragColor
#else
#define ALBEDO_FRAG_COLOR gl_FragColor
#endif

#if __VERSION__ >= 130
#define ALBEDO_IN in
#else
#define ALBEDO_IN varying
#endif

#endif
