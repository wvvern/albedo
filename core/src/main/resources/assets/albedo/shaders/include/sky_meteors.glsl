#ifndef ALBEDO_SKY_METEORS_GLSL
#define ALBEDO_SKY_METEORS_GLSL

// The twin of SkyMeteors, which sky_layers.glsl includes.

// The slots of time of the meteors, as in SkyMeteors: the slot length, the meteors in each slot and the slots that can still show one.
const float ALBEDO_METEOR_SLOT = 1.0;
#define ALBEDO_METEOR_CHANNELS 3
#define ALBEDO_METEOR_SLOTS_BACK 2
// The slots in one period of SkyClock, after which the hashes repeat, so that the meteors in the sky go on across the wrap of the time.
#define ALBEDO_METEOR_PERIOD_SLOTS 3600
// The part of its life after which the trail of a meteor is gone; the head goes out at 1.
const float ALBEDO_METEOR_TRAIL_END = 1.15;
// SkyMeteors.FIREBALLS, FIREBALL_LIFE and FIREBALL_TRAIL: the part of the meteors that are fireballs, and the factors of the life
// and of the trail length of a fireball.
const float ALBEDO_METEOR_FIREBALLS = 0.05;
const float ALBEDO_METEOR_FIREBALL_LIFE = 1.4;
const float ALBEDO_METEOR_FIREBALL_TRAIL = 1.5;

float albedoMeteorUnit(int slot, int channel, int value, int seed) {
    return albedoUnit(albedoHash(ivec3(albedoRepeat(slot, ALBEDO_METEOR_PERIOD_SLOTS), channel, value), seed));
}

// A line across a width in radians, made wider by half a pixel and darker by the same ratio, so that a thin line stays crisp.
float albedoMeteorLine(float d, float width, float footprint) {
    float w = sqrt(width * width + 0.25 * footprint * footprint);
    return exp(-(d / w) * (d / w)) * width / w;
}

// SkyMeteors.Meteor: one meteor in world directions, with the seconds that its head moves, the speed of its head in radians for
// each second, the length of its full trail in radians, and the unit directions of its start and of its motion there.
struct AlbedoMeteor {
    float birth;
    float duration;
    float speed;
    float trail;
    float brightness;
    bool fireball;
    vec3 start;
    vec3 motion;
};

// SkyMeteors.Meteor.of: the meteor of a slot and a channel of a layer into m, or false when none starts there.
bool albedoMeteorOf(int layer, int slot, int channel, out AlbedoMeteor m) {
    int seed = int(albedoLayer(layer, 2).y) * 7 + 13;
    float amount = albedoLayer(layer, 1).z;
    float chance = (2.0 + 118.0 * amount * amount) * ALBEDO_METEOR_SLOT / 60.0 / float(ALBEDO_METEOR_CHANNELS);
    if (albedoMeteorUnit(slot, channel, 0, seed) >= chance) return false;
    m.birth = float(slot) * ALBEDO_METEOR_SLOT + albedoMeteorUnit(slot, channel, 1, seed) * ALBEDO_METEOR_SLOT;
    m.fireball = albedoMeteorUnit(slot, channel, 2, seed) < ALBEDO_METEOR_FIREBALLS;
    m.duration = (0.3 + 0.7 * albedoMeteorUnit(slot, channel, 3, seed)) * (m.fireball ? ALBEDO_METEOR_FIREBALL_LIFE : 1.0);
    m.speed = radians(8.0 + 32.0 * albedoLayer(layer, 4).w) * (0.7 + 0.6 * albedoMeteorUnit(slot, channel, 4, seed));
    m.trail = radians(3.0 + 7.0 * albedoLayer(layer, 5).w) * (0.75 + 0.5 * albedoMeteorUnit(slot, channel, 5, seed))
            * (m.fireball ? ALBEDO_METEOR_FIREBALL_TRAIL : 1.0);
    float b = albedoMeteorUnit(slot, channel, 6, seed);
    m.brightness = m.fireball ? 2.5 : 0.5 + 0.5 * b * b;
    float azimuth = 2.0 * ALBEDO_PI * albedoMeteorUnit(slot, channel, 7, seed);
    float low = sin(radians(20.0)), high = sin(radians(75.0));
    float y = low + (high - low) * albedoMeteorUnit(slot, channel, 8, seed);
    float c = sqrt(1.0 - y * y);
    vec3 start = vec3(c * sin(azimuth), y, -c * cos(azimuth));
    m.start = start;
    // A shower meteor moves away from the radiant, the layer center: the -z axis of the layer frame.
    vec3 radiant = -albedoLayer(layer, 9).xyz;
    vec3 away = start * dot(start, radiant) - radiant;
    if (albedoMeteorUnit(slot, channel, 9, seed) < albedoLayer(layer, 6).w && length(away) > 1e-3) {
        m.motion = normalize(away);
    } else {
        float turn = 2.0 * ALBEDO_PI * albedoMeteorUnit(slot, channel, 10, seed);
        vec3 east = vec3(start.z, 0.0, -start.x) / c;
        m.motion = east * cos(turn) + cross(start, east) * sin(turn);
    }
    return true;
}

// The linear light of the meteor of a slot and a channel in the world direction q, with a hot head and a trail that fades and cools.
vec3 albedoMeteor(int layer, int slot, int channel, vec3 q, float footprint) {
    AlbedoMeteor m;
    if (!albedoMeteorOf(layer, slot, channel, m)) return vec3(0.0);
    float age = SkyHeader.w - m.birth;
    float life = age / m.duration;
    if (life < 0.0 || life > ALBEDO_METEOR_TRAIL_END) return vec3(0.0);
    bool fireball = m.fireball;
    float speed = m.speed, duration = m.duration, trail = m.trail, brightness = m.brightness;
    vec3 start = m.start, motion = m.motion;
    float scale = fireball ? 1.6 : 1.0;
    float headWidth = radians(0.04) * scale;
    float trailWidth = radians(0.022) * scale;
    float halo = 4.0 * sqrt(headWidth * headWidth + 0.25 * footprint * footprint);
    float across = dot(q, cross(start, motion));
    if (abs(across) > 2.0 * halo) return vec3(0.0);
    float angle = atan(dot(q, motion), dot(q, start));
    float head = speed * min(age, duration);
    float behind = head - angle;
    if (behind < -2.0 * halo || behind > trail + 2.0 * halo) return vec3(0.0);
    float rise = albedoSmooth(0.0, 0.12, life);
    // The trail is white and brightest behind the head, and goes part of the way to the trail color and to nothing at its full length.
    float t = clamp(behind / trail, 0.0, 1.0);
    float soft = trailWidth + footprint;
    float along = albedoSmooth(-soft, soft, behind) * albedoSmooth(-soft, soft, angle) * (1.0 - t) * (1.0 - t);
    vec4 mixed = albedoMix(layer, 0.8 * albedoSmooth(0.1, 1.0, t));
    vec3 tint = (fireball ? mix(mixed.rgb, vec3(1.0, 0.42, 0.1), 0.45 * t) : mixed.rgb) * mixed.a;
    float trailLight = rise * (1.0 - albedoSmooth(0.7, ALBEDO_METEOR_TRAIL_END, life)) * brightness * 2.5;
    vec3 light = tint * trailLight * along * albedoMeteorLine(across, trailWidth, footprint);
    if (life < 1.0) {
        // The head gets brighter until it goes out, and its light above 1 makes its center white.
        float headLight = rise * (1.0 - albedoSmooth(0.8, 1.0, life)) * (0.7 + 0.5 * life) * brightness;
        float r = length(vec2(across, behind));
        float shape = 3.0 * albedoMeteorLine(r, headWidth, footprint) + 0.12 * exp(-(r / halo) * (r / halo));
        light += albedoLayer(layer, 3).rgb * albedoLayer(layer, 2).z * headLight * shape;
    }
    // The meteors go out at the horizon.
    return light * albedoSmooth(0.0, 0.09, q.y);
}

// Shooting stars in the world direction q at the time of the header, with no meteors in a still sky; the light above 1 goes into the color.
vec4 albedoMeteors(int layer, vec3 q, float footprint) {
    if (SkyHeader.w < 0.0 || q.y <= 0.0) return vec4(0.0);
    int now = int(floor(SkyHeader.w / ALBEDO_METEOR_SLOT));
    vec3 light = vec3(0.0);
    for (int slot = now - ALBEDO_METEOR_SLOTS_BACK; slot <= now; slot++) {
        for (int channel = 0; channel < ALBEDO_METEOR_CHANNELS; channel++) light += albedoMeteor(layer, slot, channel, q, footprint);
    }
    float a = clamp(max(light.r, max(light.g, light.b)), 0.0, 1.0);
    return a > 0.0 ? vec4(light / a, a) : vec4(0.0);
}

#endif
