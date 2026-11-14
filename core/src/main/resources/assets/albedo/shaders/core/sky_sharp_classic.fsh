// The sky for each pixel of the sky sphere, for the classic GL programs.

#include <albedo:sky_layers.glsl>
#include <albedo:classic_transforms.glsl>
#include <albedo:classic_fragment.glsl>

#ifndef ALBEDO_TRANSFORM_BLOCKS
uniform vec4 ColorModulator;
#endif

ALBEDO_IN vec3 skyPosition;

void main() {
    ALBEDO_FRAG_COLOR = vec4(albedoSharpPixel(skyPosition, ivec2(gl_FragCoord.xy)) * ColorModulator.rgb, 1.0);
}
