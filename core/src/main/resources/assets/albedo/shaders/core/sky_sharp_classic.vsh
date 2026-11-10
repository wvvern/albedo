// The sky sphere for the GL programs of 1.21.11, 1.21.1 and 1.8.9, where SkyShaderSource puts the version line and the defines on top.

#ifdef ALBEDO_FIXED_FUNCTION
#if __VERSION__ >= 130
out vec3 skyPosition;
#else
varying vec3 skyPosition;
#endif

void main() {
    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
    // The far depth, so that a depth test hides the sky behind the world.
    gl_Position.z = gl_Position.w;
    skyPosition = gl_Vertex.xyz;
}
#else
#ifdef ALBEDO_TRANSFORM_BLOCKS
// The blocks of dynamictransforms.glsl and projection.glsl of the game, which this program gets with no include step.
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};
#else
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
#endif

in vec3 Position;

// The point on the sky sphere around the camera, whose direction is the direction of the sky.
out vec3 skyPosition;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    // The far depth, so that a depth test hides the sky behind the world.
#ifdef ALBEDO_REVERSED_DEPTH
    // The far depth is 0 in a reversed depth buffer, which is the clip z 0 when the clip volume starts at 0, else -w.
#ifdef ALBEDO_DEPTH_ZERO_TO_ONE
    gl_Position.z = 0.0;
#else
    gl_Position.z = -gl_Position.w;
#endif
#else
    gl_Position.z = gl_Position.w;
#endif
    skyPosition = Position;
}
#endif
