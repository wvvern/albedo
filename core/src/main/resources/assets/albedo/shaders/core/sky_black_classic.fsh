// Black on the sky sphere of sky_sharp_classic.vsh for the GL programs of 1.21.11, which resets only the pixels at the far depth.

#if __VERSION__ >= 130
in vec3 skyPosition;
#else
varying vec3 skyPosition;
#endif

// A core profile needs its own output, and GLSL 1.30 and earlier in a compatibility profile use gl_FragColor.
#if __VERSION__ >= 150
out vec4 fragColor;
#define ALBEDO_FRAG_COLOR fragColor
#else
#define ALBEDO_FRAG_COLOR gl_FragColor
#endif

void main() {
    ALBEDO_FRAG_COLOR = vec4(0.0);
}
