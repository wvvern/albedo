// A quad over the equirectangular image or a camera view, for the GL programs of 1.21.11, 1.21.1 and 1.8.9.

#ifdef ALBEDO_FIXED_FUNCTION
#if __VERSION__ >= 130
out vec2 skyUv;
out vec3 viewPosition;
#else
varying vec2 skyUv;
varying vec3 viewPosition;
#endif

void main() {
    skyUv = gl_MultiTexCoord0.xy;
    viewPosition = gl_Vertex.xyz;
    gl_Position = vec4(skyUv.x * 2.0 - 1.0, 1.0 - skyUv.y * 2.0, 0.0, 1.0);
}
#else
in vec3 Position;
in vec2 UV0;

// The place in the equirectangular image, from the top left corner.
out vec2 skyUv;
// The sky direction of a corner of a camera view, or zero for the equirectangular image.
out vec3 viewPosition;

void main() {
    gl_Position = vec4(UV0.x * 2.0 - 1.0, 1.0 - UV0.y * 2.0, 0.0, 1.0);
    skyUv = UV0;
    viewPosition = Position;
}
#endif
