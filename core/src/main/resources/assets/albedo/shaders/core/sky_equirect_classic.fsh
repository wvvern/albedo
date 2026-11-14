// Draws the sky into the equirectangular image of the CPU bake, or into a camera view, for the classic GL programs.

#include <albedo:sky_layers.glsl>
#include <albedo:classic_fragment.glsl>

ALBEDO_IN vec2 skyUv;
ALBEDO_IN vec3 viewPosition;

void main() {
    ALBEDO_FRAG_COLOR = vec4(albedoEquirectPixel(skyUv, viewPosition, ivec2(gl_FragCoord.xy)), 1.0);
}
