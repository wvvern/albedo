#ifndef ALBEDO_CLASSIC_TRANSFORMS_GLSL
#define ALBEDO_CLASSIC_TRANSFORMS_GLSL

#ifdef ALBEDO_TRANSFORM_BLOCKS
// The block of dynamictransforms.glsl of the game, which a classic program gets with no include step.
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
#endif

#endif
