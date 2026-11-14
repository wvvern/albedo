// A quad over the equirectangular image or a camera view, for the classic GL programs.

#include <albedo:classic_vertex.glsl>

// The place in the equirectangular image, from the top left corner.
ALBEDO_OUT vec2 skyUv;
// The sky direction of a corner of a camera view, or zero for the equirectangular image.
ALBEDO_OUT vec3 viewPosition;

#ifdef ALBEDO_FIXED_FUNCTION
void main() {
    skyUv = gl_MultiTexCoord0.xy;
    viewPosition = gl_Vertex.xyz;
    gl_Position = vec4(skyUv.x * 2.0 - 1.0, 1.0 - skyUv.y * 2.0, 0.0, 1.0);
}
#else
in vec3 Position;
in vec2 UV0;

void main() {
    gl_Position = vec4(UV0.x * 2.0 - 1.0, 1.0 - UV0.y * 2.0, 0.0, 1.0);
    skyUv = UV0;
    viewPosition = Position;
}
#endif
