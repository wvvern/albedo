#ifndef ALBEDO_SKY_AURORA_GLSL
#define ALBEDO_SKY_AURORA_GLSL

// The twin of Aurora, which sky_layers.glsl includes.

// Aurora.octave: one octave of rays, which goes to its mean 0.5 as a ray gets narrower than about three pixels.
float albedoRayOctave(vec2 q, ivec2 period, int seed, float width) {
    float k = 1.0 - albedoSmooth(0.18, 0.45, width);
    return k <= 0.0 ? 0.5 : 0.5 + (albedoValue(q, period, seed) - 0.5) * k;
}

// The numbers of Aurora: the earth radius, the altitude of the lower edge, the drift of the rays, the loop of a fold, the reach of a
// sheet to each side, the height that a curtain keeps at its end, the km over which its light comes in at the lower edge, the glow
// around that edge, the km over which the band at the edge fades up into the rays, how many times the height of the tallest rays
// above the edge the light ends, the most light of a piece seen edge-on and the azimuth of one cell of the noise of the gaps.
const float ALBEDO_AURORA_EARTH = 6371.0;
const float ALBEDO_AURORA_BASE = 100.0;
const float ALBEDO_AURORA_DRIFT = 0.6;
const float ALBEDO_AURORA_LOOP = 0.3;
const float ALBEDO_AURORA_THICK = 9.0;
const float ALBEDO_AURORA_SHORT = 0.15;
const float ALBEDO_AURORA_SOFT = 9.0;
const float ALBEDO_AURORA_SKIRT = 8.0;
const float ALBEDO_AURORA_BAND = 8.0;
const float ALBEDO_AURORA_TOP = 3.4;
const float ALBEDO_AURORA_EDGE_ON = 2.5;
const float ALBEDO_AURORA_GAP = 0.6;
#define ALBEDO_AURORA_STEPS 10
// Aurora.PERIOD: the aurora clock goes back to 0 after these seconds, in which each wave turns and each ray and noise row moves on
// a whole number of times, so that the wrap has no jump; the drift of the rays in it is ALBEDO_AURORA_DRIFT * PERIOD = 4320 km.
const float ALBEDO_AURORA_PERIOD = 7200.0;
// The angle in radians for each second of a wave that turns once in a period.
const float ALBEDO_AURORA_TURN = 6.2831853 / ALBEDO_AURORA_PERIOD;
// Aurora.DISTANCE, SKEW and GAIN: the distance in km and the azimuth in radians of the nearest place, and the light, of the near
// (1) and of the far (2) ribbon.
const float ALBEDO_AURORA_DISTANCE1 = 160.0;
const float ALBEDO_AURORA_DISTANCE2 = 430.0;
const float ALBEDO_AURORA_SKEW1 = 0.45;
const float ALBEDO_AURORA_SKEW2 = -0.35;
const float ALBEDO_AURORA_GAIN1 = 1.0;
const float ALBEDO_AURORA_GAIN2 = 0.2;
// Aurora.FAR_TOP, MAX_AZIMUTH and FOLD_REACH in radians: the top of the far ribbon, the largest azimuth with light, and the sum of
// the fold sizes; the light fades out from ALBEDO_AURORA_FADE_AZIMUTH to the largest azimuth.
const float ALBEDO_AURORA_FAR_TOP = 0.72;
const float ALBEDO_AURORA_MAX_AZIMUTH = 1.35;
const float ALBEDO_AURORA_FADE_AZIMUTH = 1.15;
const float ALBEDO_AURORA_FOLD_REACH = 0.139;
// Aurora.FOLD_K, FOLD_AMP and FOLD_RATE: the frequency for each radian of azimuth, the size in radians and the speed in radians for
// each second of each fold.
const float ALBEDO_AURORA_FOLD_K1 = 8.0;
const float ALBEDO_AURORA_FOLD_K2 = 21.0;
const float ALBEDO_AURORA_FOLD_K3 = 43.0;
const float ALBEDO_AURORA_FOLD_AMP1 = 0.08;
const float ALBEDO_AURORA_FOLD_AMP2 = 0.04;
const float ALBEDO_AURORA_FOLD_AMP3 = 0.019;
const float ALBEDO_AURORA_FOLD_RATE1 = 23.0 * ALBEDO_AURORA_TURN;
const float ALBEDO_AURORA_FOLD_RATE2 = -57.0 * ALBEDO_AURORA_TURN;
const float ALBEDO_AURORA_FOLD_RATE3 = 126.0 * ALBEDO_AURORA_TURN;
// Aurora.NEAR_K, NEAR_AMP and NEAR_RATE: the frequency for each radian, the size in km and the speed of each wave that moves the
// ribbon toward the viewer.
const float ALBEDO_AURORA_NEAR_K1 = 5.0;
const float ALBEDO_AURORA_NEAR_K2 = 17.0;
const float ALBEDO_AURORA_NEAR_AMP1 = 22.0;
const float ALBEDO_AURORA_NEAR_AMP2 = 5.0;
const float ALBEDO_AURORA_NEAR_RATE1 = -17.0 * ALBEDO_AURORA_TURN;
const float ALBEDO_AURORA_NEAR_RATE2 = 46.0 * ALBEDO_AURORA_TURN;
// Aurora.EDGE_K1 and EDGE_K2: the waves of the lower edge in radians for each km.
const float ALBEDO_AURORA_EDGE_K1 = 30.0 * ALBEDO_AURORA_TURN / ALBEDO_AURORA_DRIFT;
const float ALBEDO_AURORA_EDGE_K2 = 76.0 * ALBEDO_AURORA_TURN / ALBEDO_AURORA_DRIFT;
// Aurora.RAY_CELL, RAY_CELLS and RAY_ROWS: the width in km of the cells, the cells in the drift of a period and the noise rows in
// a period of each octave of rays.
const float ALBEDO_AURORA_RAY_CELL1 = 40.0;
const float ALBEDO_AURORA_RAY_CELL2 = 3.0;
const float ALBEDO_AURORA_RAY_CELL3 = 1.08;
const float ALBEDO_AURORA_RAY_CELL4 = 0.5;
const float ALBEDO_AURORA_RAY_CELL5 = 24.0;
const int ALBEDO_AURORA_RAY_CELLS1 = 108;
const int ALBEDO_AURORA_RAY_CELLS2 = 1440;
const int ALBEDO_AURORA_RAY_CELLS3 = 4000;
const int ALBEDO_AURORA_RAY_CELLS4 = 8640;
const int ALBEDO_AURORA_RAY_CELLS5 = 180;
const int ALBEDO_AURORA_RAY_ROWS1 = 216;
const int ALBEDO_AURORA_RAY_ROWS2 = 480;
const int ALBEDO_AURORA_RAY_ROWS3 = 900;
const int ALBEDO_AURORA_RAY_ROWS4 = 1800;
const int ALBEDO_AURORA_RAY_ROWS5 = 144;
// Aurora.PART_ROWS and LIGHT_ROWS: the noise rows in a period of the bright and faint parts of a ribbon and of their brightness.
const int ALBEDO_AURORA_PART_ROWS = 29;
const int ALBEDO_AURORA_LIGHT_ROWS = 72;

// The distance in km of the nearest place of the near (0) and of the far (1) ribbon.
float albedoRibbonDistance(int c) {
    return c == 0 ? ALBEDO_AURORA_DISTANCE1 : ALBEDO_AURORA_DISTANCE2;
}

// The azimuth from the line through the nearest place of ribbon c at theta, so that each ribbon slants across the sky.
float albedoRibbonAngle(int c, float theta) {
    return clamp(theta - (c == 0 ? ALBEDO_AURORA_SKEW1 : ALBEDO_AURORA_SKEW2), -1.5, 1.5);
}

// One fold wave of Aurora.Ribbon.offset with its phase: adds its azimuth to x, and to d the slope of the azimuth and the loop toward
// the viewer and its slope.
float albedoFoldWave(int c, float k, float amp, float rate, float phase, float theta, float t, inout vec3 d) {
    float arg = k * theta + phase + rate * t;
    float s = sin(arg), co = cos(arg);
    float loop = ALBEDO_AURORA_LOOP * albedoRibbonDistance(c) * amp;
    d += vec3(amp * k * co, -loop * co, loop * k * s);
    return amp * s;
}

// Aurora.Ribbon.phases: the phases of the three fold waves of ribbon c in xyz, and of its two waves toward the viewer and of the two waves
// of its lower edge in the second vector, which the search finds once for each ribbon.
vec3 albedoRibbonPhases(int c, int seed, out vec4 waves) {
    waves = 6.2831853 * vec4(albedoUnit(albedoHash(ivec3(0, c, 1), seed + 602)), albedoUnit(albedoHash(ivec3(1, c, 1), seed + 602)),
            albedoUnit(albedoHash(ivec3(0, c, 2), seed + 603)), albedoUnit(albedoHash(ivec3(1, c, 2), seed + 603)));
    return 6.2831853 * vec3(albedoUnit(albedoHash(ivec3(0, c, 0), seed + 601)), albedoUnit(albedoHash(ivec3(1, c, 0), seed + 601)),
            albedoUnit(albedoHash(ivec3(2, c, 0), seed + 601)));
}

// Aurora.Ribbon.offset: the azimuth of ribbon c at theta minus the azimuth a of the view, with the slopes and the loop in d.
float albedoRibbonOffset(int c, float theta, float a, float t, float scale, vec3 phase, out vec3 d) {
    float f = albedoRibbonDistance(c) / albedoRibbonDistance(0);
    d = vec3(1.0, 0.0, 0.0);
    float phi = theta + albedoFoldWave(c, ALBEDO_AURORA_FOLD_K1 * f / scale, ALBEDO_AURORA_FOLD_AMP1 * scale / f, ALBEDO_AURORA_FOLD_RATE1, phase.x, theta, t, d);
    phi += albedoFoldWave(c, ALBEDO_AURORA_FOLD_K2 * f / scale, ALBEDO_AURORA_FOLD_AMP2 * scale / f, ALBEDO_AURORA_FOLD_RATE2, phase.y, theta, t, d);
    phi += albedoFoldWave(c, ALBEDO_AURORA_FOLD_K3 * f / scale, ALBEDO_AURORA_FOLD_AMP3 * scale / f, ALBEDO_AURORA_FOLD_RATE3, phase.z, theta, t, d);
    return phi - a;
}

// Aurora.Ribbon.speed: the length of ribbon c at theta for each radian of theta, over its distance, from the values d of the offset there.
float albedoRibbonSpeed(int c, float theta, vec3 d) {
    float th = albedoRibbonAngle(c, theta);
    float co = cos(th), dist = albedoRibbonDistance(c);
    float o = (dist * tan(th) / co + d.z) / (dist / co + d.y);
    return sqrt(d.x * d.x + o * o);
}

// Aurora.cumulative: the part of a triangle of width 2 around 0 that lies left of x.
float albedoTriangleBelow(float x) {
    float y = clamp(x, -1.0, 1.0);
    return y < 0.0 ? 0.5 * (1.0 + y) * (1.0 + y) : 1.0 - 0.5 * (1.0 - y) * (1.0 - y);
}

// Aurora.moment: the first moment of the part of a triangle of width 2 around 0 that lies left of x.
float albedoTriangleMoment(float x) {
    float y = clamp(x, -1.0, 1.0);
    return y < 0.0 ? y * y * (0.5 + y / 3.0) - 1.0 / 6.0 : y * y * (0.5 - y / 3.0) - 1.0 / 6.0;
}

// Aurora.Ribbon.rise: the km of the view at elevation el and azimuth a above the lower edge of curtain c, with the azimuth th of
// albedoRibbonAngle of the view and the place along there, where pixelUp gets the km of one pixel up the curtain.
float albedoAuroraRise(int c, float th, float along, float el, float footprint, float a, float t, float scale, vec3 phase, vec4 waves,
        out float pixelUp) {
    vec3 d;
    albedoRibbonOffset(c, a, a, t, scale, phase, d);
    float dist = albedoRibbonDistance(c), f = dist / albedoRibbonDistance(0);
    // The distance of a straight east-west line, moved toward and away from the viewer by the folds and by slow waves.
    float r = dist / cos(th) + d.y + ALBEDO_AURORA_NEAR_AMP1 * sin(ALBEDO_AURORA_NEAR_K1 * f / scale * a + waves.x + ALBEDO_AURORA_NEAR_RATE1 * t)
            + ALBEDO_AURORA_NEAR_AMP2 * sin(ALBEDO_AURORA_NEAR_K2 * f / scale * a + waves.y + ALBEDO_AURORA_NEAR_RATE2 * t);
    // The altitude of the view above the ground at the distance of the curtain, with the curve of the earth.
    float tilt = min(el + r / (2.0 * ALBEDO_AURORA_EARTH), 1.45), cosT = cos(tilt);
    pixelUp = footprint * r / (cosT * cosT);
    float sd = along + ALBEDO_AURORA_DRIFT * t;
    // The lower edge is nearly level, with gentle waves of about 23 and 9 km that move along it.
    float edge = 1.3 * sin(sd * ALBEDO_AURORA_EDGE_K1 + waves.z + 23.0 * ALBEDO_AURORA_TURN * t)
            + 0.8 * sin(sd * ALBEDO_AURORA_EDGE_K2 + waves.w - 57.0 * ALBEDO_AURORA_TURN * t);
    return r * tan(tilt) - ALBEDO_AURORA_BASE - edge;
}

// Aurora.Ribbon.presence: how much of curtain c is there at the azimuth th of albedoRibbonAngle of the view: parts of a curtain are
// there, fade out or are gaps of open sky, with wider gaps in the far curtain and none at the center.
float albedoAuroraPresence(int c, float th, float t, int seed) {
    float part = albedoValue(vec2(th / ALBEDO_AURORA_GAP, t * (float(ALBEDO_AURORA_PART_ROWS) / ALBEDO_AURORA_PERIOD)), ivec2(0, ALBEDO_AURORA_PART_ROWS), seed + 31 + c)
            + (c == 0 ? 0.35 * exp(-th * th / 0.25) : 0.0);
    return albedoSmooth(0.25 + 0.15 * float(c), 0.7 + 0.15 * float(c), part);
}

// Aurora.Ribbon.aim: the light of the straight piece from p to q (each a place, an azimuth from the view and a speed) for a sheet whose
// light reaches w radians to each side, and the place where the piece is seen.
vec2 albedoAuroraAim(vec3 p, vec3 q, float w) {
    float xa = p.y / w, xb = q.y / w, dx = xb - xa;
    if (min(abs(xa), abs(xb)) >= 1.0 && (xa < 0.0) == (xb < 0.0)) return vec2(0.0);
    // The light along the view is the length of the piece near it, so a piece seen edge-on is brighter.
    float part = albedoTriangleBelow(xb) - albedoTriangleBelow(xa), mid = 0.5;
    float k = (abs(dx) < 1e-3 ? max(0.0, 1.0 - abs(xa)) : part / dx) * abs(q.x - p.x) / w * 0.5 * (p.z + q.z);
    if (k <= 0.0) return vec2(0.0);
    // The middle of the light of the piece, which moves smoothly with the view.
    if (abs(dx) >= 1e-3 && part != 0.0) {
        mid = clamp((albedoTriangleMoment(xb) - albedoTriangleMoment(xa) - xa * part) / (dx * part), 0.0, 1.0);
    }
    // The piece is seen where its line meets the view, so that the pieces around one crossing show one sharp sheet,
    // and a piece that runs along the view is seen where its light is.
    float xm = xa + dx * mid;
    // A piece that does not cross the view gives light only from the width of its sheet, which fades out faster as it gets
    // farther, so that the side of a part that runs along the view does not look like a straight cut.
    k *= 1.0 - albedoSmooth(0.0, 1.0, max(0.0, max(min(xa, xb), -max(xa, xb))));
    // A piece seen edge-on is brighter, but not as a pillar of light at the tip of each fold.
    return vec2(k / (1.0 + k / ALBEDO_AURORA_EDGE_ON), p.x + (q.x - p.x) * (mid - xm * dx / (dx * dx + 0.25)));
}

// Aurora.Ribbon.rays: the light of a piece of ribbon c seen at theta with the light k, whose azimuth changes by slope for each radian of
// theta, for a view h km above the lower edge (up is h but not under 0) of a curtain whose light comes in over soft km at that edge
// and whose tallest rays reach tallest km: all of it, of its band at the lower edge, of its rays and of their tops in COLOR_2, where
// a view far under the edge gets only the first.
vec4 albedoAuroraRays(int c, float theta, float k, float slope, float h, float up, float soft, float tallest, float wide, float t,
        float scale, int seed) {
    float th = albedoRibbonAngle(c, theta), along = albedoRibbonDistance(c) * tan(th) / scale, co = cos(th);
    float bright = k * (0.4 + 0.9 * albedoValue(vec2(along / 70.0, t * (float(ALBEDO_AURORA_LIGHT_ROWS) / ALBEDO_AURORA_PERIOD)), ivec2(0, ALBEDO_AURORA_LIGHT_ROWS), seed + 33 + c));
    if (h <= -2.0 * soft) return vec4(bright, 0.0, 0.0, 0.0);
    // The km of the rays in one pixel, which is more where the view runs along the sheet, as near the tip of a fold.
    float pixelAlong = wide * albedoRibbonDistance(c) / (co * co * max(abs(slope), 0.02)) / scale;
    float sd = along + ALBEDO_AURORA_DRIFT * t;
    // Each octave repeats after the drift of one period along the ribbon and after its rows of one period.
    float r0 = albedoRayOctave(vec2(sd / ALBEDO_AURORA_RAY_CELL1, t * (float(ALBEDO_AURORA_RAY_ROWS1) / ALBEDO_AURORA_PERIOD)),
            ivec2(ALBEDO_AURORA_RAY_CELLS1, ALBEDO_AURORA_RAY_ROWS1), seed + 21 + c, pixelAlong / ALBEDO_AURORA_RAY_CELL1);
    float r1 = albedoRayOctave(vec2(sd / ALBEDO_AURORA_RAY_CELL2, t * (float(ALBEDO_AURORA_RAY_ROWS2) / ALBEDO_AURORA_PERIOD)),
            ivec2(ALBEDO_AURORA_RAY_CELLS2, ALBEDO_AURORA_RAY_ROWS2), seed + 22 + c, pixelAlong / ALBEDO_AURORA_RAY_CELL2);
    float r2 = albedoRayOctave(vec2(sd / ALBEDO_AURORA_RAY_CELL3, t * (float(ALBEDO_AURORA_RAY_ROWS3) / ALBEDO_AURORA_PERIOD)),
            ivec2(ALBEDO_AURORA_RAY_CELLS3, ALBEDO_AURORA_RAY_ROWS3), seed + 23 + c, pixelAlong / ALBEDO_AURORA_RAY_CELL3);
    float r3 = albedoRayOctave(vec2(sd / ALBEDO_AURORA_RAY_CELL4, t * (float(ALBEDO_AURORA_RAY_ROWS4) / ALBEDO_AURORA_PERIOD)),
            ivec2(ALBEDO_AURORA_RAY_CELLS4, ALBEDO_AURORA_RAY_ROWS4), seed + 24 + c, pixelAlong / ALBEDO_AURORA_RAY_CELL4);
    float tall = albedoRayOctave(vec2(sd / ALBEDO_AURORA_RAY_CELL5 + 31.7, t * (float(ALBEDO_AURORA_RAY_ROWS5) / ALBEDO_AURORA_PERIOD)),
            ivec2(ALBEDO_AURORA_RAY_CELLS5, ALBEDO_AURORA_RAY_ROWS5), seed + 25 + c, pixelAlong / ALBEDO_AURORA_RAY_CELL5);
    // Wide bundles of thin rays, which are brighter lines in one sheet of light and have soft sides.
    float bundle = 0.35 + 0.65 * albedoSmooth(0.2, 0.8, r0);
    float hard = clamp(pixelAlong / 0.5 * 0.5, 0.2, 0.3);
    float rays = bundle * albedoSmooth(0.55 - hard, 0.55 + hard, 0.3 * r1 + 0.38 * r2 + 0.32 * r3);
    // The height of the rays changes slowly along the curtain, so that their tops are one soft line, and each thin ray is a little
    // taller or shorter than the rays next to it.
    float rise = up / (tallest * (0.3 + 0.5 * tall) * (0.85 + 0.3 * r2));
    // The light comes in over the soft km at the lower edge, which each bundle starts a little higher or lower, so that the edge is
    // neither a hard line nor a level one; it is 0 for a view more than 1.2 soft km under the edge, so that the stop at 2 soft km
    // above changes no light.
    float foot = albedoSmooth(-soft, soft, h + 0.4 * soft * (r0 - 0.5));
    foot *= foot;
    // The light of each bundle between its rays, so that a curtain is a sheet of light and not a row of needles.
    float sheet = 0.45 * bundle + 0.75 * rays;
    // The tops of the rays turn to COLOR_2 over a wide part of the curtain, so that the color changes slowly upward.
    return bright * vec4(1.0, foot * (0.12 + 0.3 * bundle + 0.6 * rays), foot * 0.65 * sheet * exp(-rise * (0.5 + 0.5 * rise)),
            foot * 0.3 * sheet * (0.5 + tall) * exp(-(rise - 1.1) * (rise - 1.1) * 1.2));
}

// Aurora.Ribbon.add: the light of COLOR_1 and COLOR_2 of curtain c: one lower edge, one height and one brightness for the azimuth of
// the view, so that they are smooth across the sky, times the rays of the straight pieces of the folded ribbon between the steps of a
// search over the azimuths of the folds.
vec2 albedoRibbonLight(int c, float el, float wide, float footprint, float a, float t, float scale, float height, int seed) {
    float f = albedoRibbonDistance(c) / albedoRibbonDistance(0);
    float th = albedoRibbonAngle(c, a), along = albedoRibbonDistance(c) * tan(th) / scale;
    float present = albedoAuroraPresence(c, th, t, seed);
    if (present <= 0.0) return vec2(0.0);
    vec3 d;
    vec4 waves;
    vec3 phase = albedoRibbonPhases(c, seed, waves);
    float pixelUp;
    float h = albedoAuroraRise(c, th, along, el, footprint, a, t, scale, phase, waves, pixelUp);
    // A curtain gets shorter as it gets fainter toward its end, so that it does not end at a wall, and the far one is a lower arc.
    float tallest = height * (ALBEDO_AURORA_SHORT + (1.0 - ALBEDO_AURORA_SHORT) * present) / f;
    float soft = max(ALBEDO_AURORA_SOFT * f, 0.7 * pixelUp), up = max(h, 0.0);
    if (h < -max(4.0 * ALBEDO_AURORA_SKIRT, 2.0 * soft) || h > ALBEDO_AURORA_TOP * tallest) return vec2(0.0);
    float w = max(3.0 * wide, ALBEDO_AURORA_THICK / albedoRibbonDistance(c));
    // The search reaches past the light of each sheet, so that a fold comes into the search with no light to change.
    float reach = ALBEDO_AURORA_FOLD_REACH * scale / f * 1.02 + w;
    float step = 2.0 * reach / float(ALBEDO_AURORA_STEPS);
    // The light of the pieces of the ribbon near the view: all of it, of the band at the lower edge, of the rays and of their red tops.
    vec4 sum = vec4(0.0);
    float ta = a - reach, ga = albedoRibbonOffset(c, ta, a, t, scale, phase, d), da = d.x, sa = albedoRibbonSpeed(c, ta, d);
    for (int i = 1; i <= ALBEDO_AURORA_STEPS; i++) {
        float tb = a - reach + float(i) * step, gb = albedoRibbonOffset(c, tb, a, t, scale, phase, d), db = d.x;
        float sb = albedoRibbonSpeed(c, tb, d);
        bool tip = (da < 0.0) != (db < 0.0);
        vec3 m = vec3(tb, gb, sb);
        if (tip) {
            // The tip of a fold is in this step, which gets a corner there, found in two steps toward the zero of the slope.
            float tm = ta + step * da / (da - db);
            albedoRibbonOffset(c, tm, a, t, scale, phase, d);
            float dm = d.x;
            tm = (da < 0.0) != (dm < 0.0) ? ta + (tm - ta) * da / (da - dm) : tm + (tb - tm) * dm / (dm - db);
            m.xy = vec2(tm, albedoRibbonOffset(c, tm, a, t, scale, phase, d));
            m.z = albedoRibbonSpeed(c, tm, d);
        }
        // One call for the piece up to the tip or the step, and one for the piece after the tip, so that the shader has one copy of it.
        for (int k = 0; k < 2; k++) {
            if (k == 1 && !tip) break;
            vec3 p = k == 0 ? vec3(ta, ga, sa) : m, q = k == 0 ? m : vec3(tb, gb, sb);
            vec2 aim = albedoAuroraAim(p, q, w);
            if (aim.x > 0.0) {
                sum += albedoAuroraRays(c, aim.y, aim.x, (q.y - p.y) / (q.x - p.x), h, up, soft, tallest, wide, t, scale, seed);
            }
        }
        ta = tb;
        ga = gb;
        da = db;
        sa = sb;
    }
    // A faint glow around the lower edge that also reaches under it, so that the sky under a curtain does not look like a dark hill,
    // with no crease at the edge.
    float glow = 0.1 * sum.x * exp(-(h / ALBEDO_AURORA_SKIRT) * (h / ALBEDO_AURORA_SKIRT));
    // The brightest light is a band just above the lower edge, wider in km on the far curtain so that it is no thin line there, and
    // above it the rays dissolve into the open sky.
    return (c == 0 ? ALBEDO_AURORA_GAIN1 : ALBEDO_AURORA_GAIN2) * present * vec2(glow + sum.y * exp(-up / (ALBEDO_AURORA_BAND * f)) + sum.z, sum.w);
}

// Aurora.sample: a near and a far folded ribbon toward the layer center in the world, which move with the aurora clock of the layer.
vec4 albedoAurora(int layer, vec3 p, float footprint) {
    float el = asin(clamp(p.y, -1.0, 1.0));
    float cosEl = sqrt(max(0.0, 1.0 - p.y * p.y));
    // The pole of the layer has no azimuth, and atan of two zeros has no defined value in GLSL.
    if (el < -0.05 || el > 1.4 || cosEl < 1e-4) return vec4(0.0);
    float a = atan(p.x, -p.z);
    if (abs(a) > ALBEDO_AURORA_MAX_AZIMUTH) return vec4(0.0);
    vec4 values = albedoLayer(layer, 1);
    vec4 shape = albedoLayer(layer, 2);
    int seed = int(shape.y);
    // The aurora clock in the free float of the meteor length, which the CPU sets for each frame and which is 0 in a still sky.
    float t = albedoLayer(layer, 5).w;
    float height = 70.0 + 260.0 * values.w;
    float wide = footprint / max(cosEl, 0.05);
    // One loop over the two ribbons, so that the shader has one copy of the search.
    vec2 light = vec2(0.0);
    for (int c = 0; c < 2; c++) {
        // The far ribbon with its shorter rays is never more than about 40 degrees high.
        if (c == 1 && el >= ALBEDO_AURORA_FAR_TOP) break;
        light += albedoRibbonLight(c, el, wide, footprint, a, t, shape.x, height, seed);
    }
    // The thick air near the horizon dims the light, so that no bright edge lies along the horizon, and the light fades out before the search stops.
    vec3 e = (albedoLayer(layer, 3).rgb * shape.z * light.x + albedoLayer(layer, 4).rgb * shape.w * light.y)
            * values.z * 2.0 * albedoSmooth(-0.02, 0.22, el) * (1.0 - albedoSmooth(ALBEDO_AURORA_FADE_AZIMUTH, ALBEDO_AURORA_MAX_AZIMUTH, abs(a)));
    float m = max(e.r, max(e.g, e.b));
    if (m <= 0.0) return vec4(0.0);
    // A light brighter than the screen goes to white, as in a photograph.
    float hot = max(0.0, m - 1.0) * 0.25;
    float alpha = min(1.0, m + hot);
    return vec4((e + hot) / alpha, alpha);
}

#endif
