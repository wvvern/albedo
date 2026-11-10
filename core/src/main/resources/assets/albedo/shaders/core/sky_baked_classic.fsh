// The baked sky image on the sky sphere of sky_baked_classic.vsh, times the brightness of the sky.

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;

in vec2 skyUv;

out vec4 fragColor;

void main() {
    fragColor = texture(Sampler0, skyUv) * ColorModulator;
}
