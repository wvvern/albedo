#ifndef ALBEDO_SKY_STARS_GLSL
#define ALBEDO_SKY_STARS_GLSL

// The twin of SkyStars, which sky_layers.glsl includes.

// The numbers of SkyStars: angles in radians, where the core, the halo, the rim and the spikes are at a layer size of 100.
const float ALBEDO_STAR_CORE = 0.009 * ALBEDO_PI / 180.0;
const float ALBEDO_STAR_PIXEL = 0.42;
const float ALBEDO_STAR_HALO = 0.03 * ALBEDO_PI / 180.0;
const float ALBEDO_STAR_HALO_LIGHT = 0.025;
const float ALBEDO_STAR_RIM = 0.016 * ALBEDO_PI / 180.0;
const float ALBEDO_STAR_RIM_WIDTH = 0.006 * ALBEDO_PI / 180.0;
const float ALBEDO_STAR_RIM_LIGHT = 0.03;
const float ALBEDO_STAR_SPIKE_FLUX = 2.0;
const float ALBEDO_STAR_SPIKE_LENGTH = 1.4 * ALBEDO_PI / 180.0;
const float ALBEDO_STAR_SPIKE_WIDTH = 0.004 * ALBEDO_PI / 180.0;
const float ALBEDO_STAR_SPIKE_LIGHT = 0.45;
const vec3 ALBEDO_STAR_RIM_WAVE = vec3(1.08, 1.0, 0.9);
const vec3 ALBEDO_STAR_SPIKE_WAVE = vec3(1.04, 1.0, 0.95);
const float ALBEDO_STAR_TINT = 0.7;
const float ALBEDO_STAR_TWINKLE_ZENITH = 0.1;
const float ALBEDO_STAR_TWINKLE_HORIZON = 0.3;
const float ALBEDO_STAR_TWINKLE_COLOR = 0.5;
const float ALBEDO_STAR_TRAIL_PIXEL = 0.6;
const float ALBEDO_STAR_TRAIL_REACH = 4.0;
const float ALBEDO_STAR_TRAIL_GAIN = 1.0;
const float ALBEDO_STAR_FIELD_SPREAD = 1.5 * ALBEDO_PI / 180.0;

// The part of the stars of class i (O, B, A, F, G, K, M) among the stars that the eye sees.
float albedoClassShare(int i) {
    if (i == 0) return 0.02;
    if (i == 1) return 0.12;
    if (i == 2) return 0.2;
    if (i == 3) return 0.17;
    if (i == 4) return 0.16;
    if (i == 5) return 0.25;
    return 0.08;
}

// The linear color of a black body at boundary i of the classes, from 40000 K to 2600 K.
vec3 albedoClassColor(int i) {
    if (i == 0) return vec3(0.3278, 0.4342, 1.0);
    if (i == 1) return vec3(0.4020, 0.5210, 1.0);
    if (i == 2) return vec3(0.5906, 0.6795, 1.0);
    if (i == 3) return vec3(0.9387, 0.9301, 1.0);
    if (i == 4) return vec3(1.0, 0.9047, 0.8228);
    if (i == 5) return vec3(1.0, 0.6445, 0.3564);
    if (i == 6) return vec3(1.0, 0.4969, 0.1981);
    return vec3(1.0, 0.3515, 0.0802);
}

// The linear color of a star at u along the classes, a mix of the two boundary colors of its class with white.
vec3 albedoStarColor(float u) {
    int i = 0;
    while (i < 6 && u >= albedoClassShare(i)) {
        u -= albedoClassShare(i);
        i++;
    }
    float f = min(1.0, u / albedoClassShare(i));
    vec3 a = albedoClassColor(i);
    return 1.0 - ALBEDO_STAR_TINT + ALBEDO_STAR_TINT * (a + (albedoClassColor(i + 1) - a) * f);
}

// The light of the thin rim of a star at the distance dist from it, for each channel, with red outside and blue inside.
vec3 albedoStarRim(float flux, float dist, float blur2, float scale) {
    float width = ALBEDO_STAR_RIM_WIDTH * scale, wide = sqrt(width * width + blur2);
    float radius = ALBEDO_STAR_RIM * scale * sqrt(flux);
    vec3 r = (dist - radius * ALBEDO_STAR_RIM_WAVE) / wide;
    return flux * ALBEDO_STAR_RIM_LIGHT * width / wide * exp(-0.5 * r * r);
}

// The light of one spike line along the unit direction dir of the tangent plane, for each channel.
vec3 albedoSpikeLine(vec2 uv, vec2 dir, float span, float gain, float blur2, float scale) {
    float along = abs(uv.x * dir.x + uv.y * dir.y);
    float across = uv.y * dir.x - uv.x * dir.y;
    vec3 out3 = vec3(0.0);
    for (int c = 0; c < 3; c++) {
        float reach = span * ALBEDO_STAR_SPIKE_WAVE[c];
        if (along >= reach) continue;
        float taper = 1.0 - along / reach;
        float width = ALBEDO_STAR_SPIKE_WIDTH * scale * ALBEDO_STAR_SPIKE_WAVE[c];
        float wide = sqrt(width * width + blur2);
        float x = across / wide;
        out3[c] = gain * taper * taper * taper * width / wide * exp(-0.5 * x * x);
    }
    return out3;
}

// The light of the spikes of a bright star s at the offset d from it: three lines at 60 degrees and a faint horizontal line.
vec3 albedoSpikes(vec3 s, vec3 d, float flux, float maxFlux, float spikes, float blur2, float scale) {
    float span = ALBEDO_STAR_SPIKE_LENGTH * scale * sqrt((flux - ALBEDO_STAR_SPIKE_FLUX) / (maxFlux - ALBEDO_STAR_SPIKE_FLUX));
    // The tangent frame of the star: east along the layer horizon and north toward the layer zenith, so the spikes do not turn.
    float ex = s.z, ez = -s.x, el = sqrt(ex * ex + ez * ez);
    if (el < 1e-4) {
        ex = 1.0;
        ez = 0.0;
    } else {
        ex /= el;
        ez /= el;
    }
    vec3 north = vec3(s.y * ez, s.z * ex - s.x * ez, -s.y * ex);
    vec2 uv = vec2(d.x * ex + d.z * ez, d.x * north.x + d.y * north.y + d.z * north.z);
    float gain = spikes * ALBEDO_STAR_SPIKE_LIGHT * sqrt(flux);
    vec3 light = albedoSpikeLine(uv, vec2(0.0, 1.0), span, gain, blur2, scale);
    light += albedoSpikeLine(uv, vec2(0.8660254, 0.5), span, gain, blur2, scale);
    light += albedoSpikeLine(uv, vec2(-0.8660254, 0.5), span, gain, blur2, scale);
    light += albedoSpikeLine(uv, vec2(1.0, 0.0), span * 0.7, gain * 0.5, blur2, scale);
    return light;
}

// SkyMath.wave: a sine wave from -1 to 1 at a frequency in hertz and a phase in turns.
float albedoWave(float hertz, float phase, float time) {
    return sin(2.0 * ALBEDO_PI * fract(hertz * time + phase));
}

// The twinkle of SkyStars.add: the factor of the light of a bright star and the shift of its red against its blue, from waves of the cell hash.
vec2 albedoTwinkle(ivec3 cell, int seed, float flux, float height, float twinkle, float time) {
    uint hk = albedoHash(cell, seed);
    // The frequencies are whole numbers of turns in the hour of the sky clock, so that no star jumps when the clock starts again.
    vec2 hertz = vec2(0.5 + float(hk >> 24u) / 225.0, 1.3 + float((hk >> 16u) & 255u) / 150.0);
    vec2 phase = vec2(float((hk >> 8u) & 255u), float(hk & 255u)) / 256.0;
    float wave = 0.6 * albedoWave(hertz.x, phase.x, time) + 0.4 * albedoWave(hertz.y, phase.y, time);
    // The light goes through more air near the horizon, and a bright star shows its twinkle more.
    float air = 1.0 - albedoSmooth(0.05, 0.75, height);
    float change = twinkle * (ALBEDO_STAR_TWINKLE_ZENITH + ALBEDO_STAR_TWINKLE_HORIZON * air) * (0.35 + 0.65 * albedoSmooth(0.2, 6.0, flux));
    float tint = ALBEDO_STAR_TWINKLE_COLOR * change * air * albedoWave(hertz.y - hertz.x, phase.x + phase.y, time);
    return vec2(1.0 + change * wave, tint);
}

// SkyStars.spread: the part of its light that a faint star keeps in a sky with trails, because its light spreads along its arc.
float albedoSpread(vec3 s, vec4 trail, float scale) {
    float h = dot(s, trail.xyz);
    float arc = trail.w * sqrt(max(0.0, 1.0 - h * h)) / (ALBEDO_STAR_FIELD_SPREAD * scale);
    return 1.0 / (1.0 + arc * arc);
}

// SkyStars.Tier: one grid of stars, with its cells for each radian at a layer size of 100, the light of its faintest and its
// brightest star, the power of its brightness spread, its hash salt, and whether its stars have a rim and spikes.
struct AlbedoTier {
    float cells;
    float minFlux;
    float maxFlux;
    float slope;
    int salt;
    bool detail;
};
const AlbedoTier ALBEDO_TIER_FIELD = AlbedoTier(88.0, 0.04, 0.9, 1.3, 7, false);
const AlbedoTier ALBEDO_TIER_BRIGHT = AlbedoTier(16.0, 0.9, 60.0, 1.2, 13, true);

// The light of the stars of one tier at p, where each star stays in its cell and reaches at most half a cell.
vec3 albedoStarTier(vec3 p, float footprint, AlbedoTier tier, float density, float spikes, float scale, int seed, float twinkle,
        float time, vec3 up, vec4 trail) {
    float cells = tier.cells, minFlux = tier.minFlux, maxFlux = tier.maxFlux, slope = tier.slope;
    int salt = tier.salt;
    bool bright = tier.detail;
    vec3 light = vec3(0.0);
    float n = cells / scale;
    float reach = 0.5 * scale / cells;
    float blur = ALBEDO_STAR_PIXEL * footprint, blur2 = blur * blur;
    // The 2 x 2 x 2 cells nearest to the point, because no star reaches into a cell that is not next to its own.
    ivec3 base = ivec3(floor(p * n - 0.5));
    for (int i = 0; i <= 1; i++) {
        for (int j = 0; j <= 1; j++) {
            for (int k = 0; k <= 1; k++) {
                ivec3 h = base + ivec3(i, j, k);
                uint hb = albedoHash(h, seed * 31 + salt);
                float chance = float(hb >> 24u) / 256.0;
                if (chance >= density) continue;
                vec3 s;
                if (!albedoCellPoint(h, albedoHash(h, seed * 31 + salt + 1), n, s)) continue;
                vec3 d = p - s;
                float d2 = dot(d, d);
                if (d2 >= reach * reach) continue;
                float flux = min(maxFlux, minFlux * pow((float((hb >> 12u) & 4095u) + 0.5) / 4096.0, -1.0 / slope));
                if (trail.w > 0.0 && !bright) flux *= albedoSpread(s, trail, scale);
                float tint = 0.0, still = flux;
                if (twinkle > 0.0) {
                    vec2 change = albedoTwinkle(h, seed * 31 + salt + 2, flux, dot(s, up), twinkle, time);
                    flux *= change.x;
                    tint = change.y;
                }
                float dist = sqrt(d2);
                // The footprint widens the core, and its peak falls with the width, not the area, so that a star smaller than a pixel stays visible.
                float core = ALBEDO_STAR_CORE * scale, core2 = core * core + blur2;
                float glow = flux * core / sqrt(core2) * exp(-d2 / (2.0 * core2));
                float halo = ALBEDO_STAR_HALO * scale * sqrt(flux), halo2 = halo * halo + blur2;
                float q = 1.0 + d2 / halo2;
                glow += flux * ALBEDO_STAR_HALO_LIGHT * halo * halo / halo2 / (q * q);
                vec3 detail = vec3(0.0);
                if (bright) {
                    detail = albedoStarRim(flux, dist, blur2, scale);
                    // The spikes keep the length of the still star and change with the root of the twinkle, so that they never flicker on and off.
                    if (spikes > 0.0 && still > ALBEDO_STAR_SPIKE_FLUX) {
                        detail += albedoSpikes(s, d, still, maxFlux, spikes * sqrt(flux / still), blur2, scale);
                    }
                }
                vec3 color = albedoStarColor((float(hb & 4095u) + 0.5) / 4096.0) * vec3(1.0 + tint, 1.0, 1.0 - tint);
                // The light goes to 0 before the edge of the reach, so that no star is cut.
                float fade = 1.0 - albedoSmooth(0.8 * reach, reach, dist);
                light += color * fade * (glow + detail);
            }
        }
    }
    return light;
}

// SkyStars.trails: the light of the trails of the stars of one grid at p, each the arc of its star around the axis trail.xyz,
// which fades from the star to its end at the angle trail.w.
vec3 albedoStarTrails(vec3 p, float footprint, AlbedoTier tier, float density, float scale, int seed, vec4 trail, float trailLight) {
    float cells = tier.cells, minFlux = tier.minFlux, maxFlux = tier.maxFlux, slope = tier.slope;
    int salt = tier.salt;
    vec3 light = vec3(0.0);
    vec3 axis = trail.xyz;
    float n = cells / scale;
    // The point as its height along the axis and its offset across it, the radius of its circle around the axis.
    float h = dot(p, axis);
    vec3 r = p - axis * h;
    float radius = length(r);
    if (radius < 1e-5) return light;
    // The side vector goes along the circle, so that the point turned back by an angle a is h axis + cos(a) r - sin(a) side.
    vec3 side = cross(axis, r);
    float core = ALBEDO_STAR_CORE * scale, blur = ALBEDO_STAR_TRAIL_PIXEL * footprint;
    float width2 = core * core + blur * blur, width = sqrt(width2), cut = ALBEDO_STAR_TRAIL_REACH * width;
    float angle = atan(radius, h);
    // Each part of the arc is at most one cell long, so that the box of cells around its chord is small.
    int parts = int(ceil(trail.w * radius * n));
    if (parts < 1) parts = 1;
    float step = trail.w / float(parts);
    float margin = (cut + radius * (1.0 - cos(step * 0.5))) * n + 0.01;
    float gain = trailLight * ALBEDO_STAR_TRAIL_GAIN * core / width;
    int hashSeed = seed * 31 + salt;
    vec3 last = p * n;
    for (int part = 0; part < parts; part++) {
        float a = float(part + 1) * step;
        vec3 q = (axis * h + r * cos(a) - side * sin(a)) * n;
        ivec3 lo = ivec3(floor(min(last, q) - margin)), hi = ivec3(floor(max(last, q) + margin));
        for (int x = lo.x; x <= hi.x; x++) {
            for (int y = lo.y; y <= hi.y; y++) {
                for (int z = lo.z; z <= hi.z; z++) {
                    ivec3 cell = ivec3(x, y, z);
                    uint hb = albedoHash(cell, hashSeed);
                    if (float(hb >> 24u) / 256.0 >= density) continue;
                    vec3 s;
                    if (!albedoCellPoint(cell, albedoHash(cell, hashSeed + 1), n, s)) continue;
                    // The height difference is never more than the angle difference, so a star that it puts far away is far away.
                    float sh = dot(s, axis);
                    if (abs(sh - h) >= cut) continue;
                    float back = atan(-dot(s, side), dot(s, r));
                    if (back <= 0.0 || back >= trail.w) continue;
                    // Only the part whose piece of the arc holds the star adds it, so that no star adds its trail twice.
                    int owner = int(back / step);
                    if (owner > parts - 1) owner = parts - 1;
                    if (owner != part) continue;
                    float flux = min(maxFlux, minFlux * pow((float((hb >> 12u) & 4095u) + 0.5) / 4096.0, -1.0 / slope));
                    float d = atan(sqrt(max(0.0, 1.0 - sh * sh)), sh) - angle;
                    float across = exp(-d * d / (2.0 * width2)) * (1.0 - albedoSmooth(0.75 * cut, cut, abs(d)));
                    float x1 = 1.0 - back / trail.w;
                    // The trail starts in the core of the star and fades to nothing at its end.
                    float start = albedoSmooth(0.0, 3.0 * width, back * radius);
                    light += albedoStarColor((float(hb & 4095u) + 0.5) / 4096.0) * (gain * sqrt(flux) * across * x1 * x1 * start);
                }
            }
        }
        last = q;
    }
    return light;
}

// Stars with the brightness spread and the colors of real stars, where AMOUNT is the count and HEIGHT is the strength of the spikes.
vec4 albedoStars(int layer, vec3 p, float footprint, int flags) {
    vec4 values = albedoLayer(layer, 1);
    vec4 shape = albedoLayer(layer, 2);
    int seed = int(shape.y);
    float m = 0.2 + 0.8 * values.z;
    float density = m * m, spikes = (flags & ALBEDO_FLAG_SPIKES) != 0 ? values.w * 2.0 : 0.0;
    // The stars twinkle at the time of the header, and at time 0 in a still sky, as in the bake.
    float twinkle = (flags & ALBEDO_FLAG_TWINKLE) != 0 ? albedoLayer(layer, 4).w : 0.0, time = max(SkyHeader.w, 0.0);
    // The world zenith in the layer frame, because the stars twinkle most in the thick air near the world horizon.
    vec3 zenith = SkyWorld[1].xyz;
    vec3 up = vec3(dot(albedoLayer(layer, 7).xyz, zenith), dot(albedoLayer(layer, 8).xyz, zenith), dot(albedoLayer(layer, 9).xyz, zenith));
    // The trail axis in the free last floats of the matrix rows, and the trail arc in radians, 0 for no trails.
    vec4 trail = (flags & ALBEDO_FLAG_TRAILS) != 0
            ? vec4(albedoLayer(layer, 7).w, albedoLayer(layer, 8).w, albedoLayer(layer, 9).w, albedoLayer(layer, 5).w) : vec4(0.0);
    // Only the bright stars twinkle, because the twinkle of a faint star is too small to see.
    vec3 light = albedoStarTier(p, footprint, ALBEDO_TIER_FIELD, density, spikes, shape.x, seed, 0.0, time, up, trail);
    light += albedoStarTier(p, footprint, ALBEDO_TIER_BRIGHT, density, spikes, shape.x, seed, twinkle, time, up, trail);
    // Only the bright stars draw trails, because the trail of a faint star is too faint to see.
    if (trail.w > 0.0) {
        light += albedoStarTrails(p, footprint, ALBEDO_TIER_BRIGHT, density, shape.x, seed, trail, albedoLayer(layer, 6).w);
    }
    vec4 c = albedoLightAlpha(light * albedoLayer(layer, 3).rgb, albedoLayer(layer, 3).rgb);
    return vec4(c.rgb, c.a * shape.z);
}

#endif
