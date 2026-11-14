// Black on the sky sphere of sky_sharp_classic.vsh, which resets only the pixels at the far depth.

#include <albedo:classic_fragment.glsl>

ALBEDO_IN vec3 skyPosition;

void main() {
    ALBEDO_FRAG_COLOR = vec4(0.0);
}
