// The sky sphere with the baked sky image, for GlSky (gl_sky family).

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

in vec3 Position;
in vec2 UV0;

// The place in the baked image.
out vec2 skyUv;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    skyUv = UV0;
}
