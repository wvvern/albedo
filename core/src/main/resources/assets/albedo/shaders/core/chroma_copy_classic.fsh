// Copies one mip level of the atlas or of its pristine copy into the same mip level of the other texture, for PrecompiledChromaPass.

// The mip level to copy, as the base level of the texture.
uniform sampler2D AlbedoSource;

out vec4 fragColor;

void main() {
    fragColor = texelFetch(AlbedoSource, ivec2(gl_FragCoord.xy), 0);
}
