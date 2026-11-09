// A quad over the equirectangular image, for the GL programs of 1.21.11, 1.21.1 and 1.8.9.

#ifdef ALBEDO_FIXED_FUNCTION
#if __VERSION__ >= 130
out vec2 skyUv;
#else
varying vec2 skyUv;
#endif

void main() {
    skyUv = gl_MultiTexCoord0.xy;
    gl_Position = vec4(skyUv.x * 2.0 - 1.0, 1.0 - skyUv.y * 2.0, 0.0, 1.0);
}
#else
in vec2 UV0;

// The place in the equirectangular image, from the top left corner.
out vec2 skyUv;

void main() {
    gl_Position = vec4(UV0.x * 2.0 - 1.0, 1.0 - UV0.y * 2.0, 0.0, 1.0);
    skyUv = UV0;
}
#endif
