// The sky for each pixel of the sky sphere, for the classic GL programs.

#include <albedo:sky_layers.glsl>

#ifdef ALBEDO_TRANSFORM_BLOCKS
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
#else
uniform vec4 ColorModulator;
#endif

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
    vec3 dir = normalize(skyPosition);
    // The angle across one pixel, outside all branches so that the derivatives are defined.
    float footprint = max(length(dFdx(dir)), length(dFdy(dir)));
    vec3 color = albedoEncode(albedoSky(dir, footprint), ivec2(gl_FragCoord.xy));
    ALBEDO_FRAG_COLOR = vec4(color * ColorModulator.rgb, 1.0);
}
