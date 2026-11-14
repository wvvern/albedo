#ifndef ALBEDO_SKY_GALAXY_GLSL
#define ALBEDO_SKY_GALAXY_GLSL

// The twin of Galaxy, which sky_layers.glsl includes.

// The numbers of Galaxy, in galaxy units where 1 is about the radius of the disc.
const float ALBEDO_GALAXY_REACH = 3.0;
const float ALBEDO_GALAXY_JET_LENGTH = 1.4;
const float ALBEDO_GALAXY_KNOT_CELL = 0.05;
// How much more the dust absorbs of each linear channel than of green, so that the light through it gets redder.
const vec3 ALBEDO_GALAXY_DUST_REDDENING = vec3(0.75, 1.0, 1.3);
// The linear colors of the pink knots of hydrogen gas, of the red and magenta radio lobes and of the white nucleus.
const vec3 ALBEDO_GALAXY_HALPHA = vec3(1.0, 0.1441, 0.2705);
const vec3 ALBEDO_GALAXY_LOBE = vec3(0.9, 0.1, 0.35);
const vec3 ALBEDO_GALAXY_NUCLEUS = vec3(1.0, 0.9047, 0.7913);
// The light of the large halo of old stars, and the distance between the rings of a lobe for each unit of the length of its jet.
const float ALBEDO_GALAXY_HALO = 0.4;
const float ALBEDO_GALAXY_LOBE_RING = 0.16;
// The numbers of the detail of Galaxy: the radius of the circle of noise of the dust threads, the grid cells of
// the gas regions and of the clusters, and the radius of the circle that a line of moving noise goes around.
const float ALBEDO_GALAXY_DUST_THREADS = 1.3;
const float ALBEDO_GALAXY_GAS_CELL = 0.1;
const float ALBEDO_GALAXY_CLUSTER_CELL = 0.08;
const float ALBEDO_GALAXY_LOOP = 4.0;
// The linear colors of the detail: the end of a jet, the rim of a lobe, a hot spot, a bow shock, the ionized cones and the heart of a gas region.
const vec3 ALBEDO_GALAXY_JET_END = vec3(0.5, 0.38, 1.0);
const vec3 ALBEDO_GALAXY_LOBE_RIM = vec3(1.0, 0.3, 0.16);
const vec3 ALBEDO_GALAXY_HOT_SPOT = vec3(0.75, 0.88, 1.0);
const vec3 ALBEDO_GALAXY_BOW_SHOCK = vec3(1.0, 0.55, 0.75);
const vec3 ALBEDO_GALAXY_ION_CONE = vec3(0.1, 0.85, 0.6);
const vec3 ALBEDO_GALAXY_GAS_CORE = vec3(1.0, 0.62, 0.72);
// The factors of the core color at the center of the bulge and far out, and the part of the light that thin dust gives back.
const vec3 ALBEDO_GALAXY_BULGE_CORE = vec3(1.0, 1.1, 1.25);
const vec3 ALBEDO_GALAXY_BULGE_EDGE = vec3(1.0, 0.82, 0.62);
const vec3 ALBEDO_GALAXY_DUST_GLOW = vec3(1.0, 0.4, 0.14);
// The linear colors of a stream of stars and of a new star that explodes.
const vec3 ALBEDO_GALAXY_STREAM = vec3(0.75, 0.8, 1.0);
const vec3 ALBEDO_GALAXY_SUPERNOVA = vec3(0.8, 0.9, 1.0);
// The turn of the loop of the stream against the disc and the place of its dwarf galaxy on the loop, in radians, and the long axis of the loop.
const float ALBEDO_GALAXY_STREAM_TURN = 0.6;
const float ALBEDO_GALAXY_STREAM_HEAD = 3.6;
const float ALBEDO_GALAXY_STREAM_LONG = 1.8;

// SkyMath.gauss: a bell curve of height 1 at 0.
float albedoGauss(float v) {
    return exp(-v * v);
}

// Galaxy.turns: the turns at a time of a motion that makes a whole number of turns in the hour of the sky clock.
float albedoTurns(float perHour, float motion, float time) {
    return floor(perHour * motion + 0.5) * time / 3600.0;
}

// Galaxy.loop: noise along a line at u that goes around a circle, where one turn moves it once around, so that it comes back to its start.
float albedoLoop(float u, float turns, float lane, int seed) {
    float a = u / ALBEDO_GALAXY_LOOP - 2.0 * ALBEDO_PI * fract(turns);
    return albedoNoise(vec3(ALBEDO_GALAXY_LOOP * cos(a), ALBEDO_GALAXY_LOOP * sin(a), lane), seed);
}

// Galaxy.jitter: the offset of the grid place g from the jittered center of the cell h, times the cell size, with the hash
// salts seed and seed + 1.
vec2 albedoJitter(vec2 g, ivec3 h, int seed, float cell) {
    return vec2(g.x - float(h.x) - 0.2 - 0.6 * albedoUnit(albedoHash(h, seed)), g.y - float(h.y) - 0.2 - 0.6 * albedoUnit(albedoHash(h, seed + 1)))
            * cell;
}

// Galaxy.clusters: the globular clusters of a galaxy, small soft points around its halo, more of them near the bulge.
vec3 albedoGalaxyClusters(float x, float y, float fp, float detail, int seed) {
    if (fp >= 0.5 * ALBEDO_GALAXY_CLUSTER_CELL) return vec3(0.0);
    vec2 g = vec2(x, y) / ALBEDO_GALAXY_CLUSTER_CELL;
    ivec2 cell = ivec2(floor(g));
    float sum = 0.0;
    for (int i = -1; i <= 1; i++) {
        for (int j = -1; j <= 1; j++) {
            ivec3 h = ivec3(cell.x + i, cell.y + j, 0);
            float rc = length(vec2(h.xy) + 0.5) * ALBEDO_GALAXY_CLUSTER_CELL;
            if (rc < 0.22 || albedoUnit(albedoHash(h, seed + 941)) > 0.8 * exp(-rc / 0.7)) continue;
            vec2 d = albedoJitter(g, h, seed + 942, ALBEDO_GALAXY_CLUSTER_CELL);
            float wide = max(0.0035, 0.75 * fp), d2 = (d.x * d.x + d.y * d.y) / (wide * wide);
            sum += (0.1 + 0.3 * albedoUnit(albedoHash(h, seed + 944))) * (0.0035 / wide) * (0.0035 / wide)
                    * (exp(-d2) + 0.12 / (1.0 + d2 / 5.0) * exp(-d2 / 40.0));
        }
    }
    sum *= detail * (1.0 - albedoSmooth(0.25 * ALBEDO_GALAXY_CLUSTER_CELL, 0.5 * ALBEDO_GALAXY_CLUSTER_CELL, fp));
    return sum * ALBEDO_GALAXY_NUCLEUS;
}

// Galaxy.stream: a stream of stars on part of a loop around a galaxy, wider and fainter behind the dwarf galaxy that it comes from.
vec3 albedoGalaxyStream(float x, float y, float fp, float detail, int seed, vec3 color2) {
    float c = cos(ALBEDO_GALAXY_STREAM_TURN), s = sin(ALBEDO_GALAXY_STREAM_TURN);
    float ux = (c * x + s * y) / ALBEDO_GALAXY_STREAM_LONG, uy = -s * x + c * y;
    float e = sqrt(ux * ux + uy * uy);
    // The angle at the exact center is not defined in GLSL, and the stream has no light there.
    float th = e > 0.0 ? atan(uy, ux) : 0.0;
    float a = fract((th - ALBEDO_GALAXY_STREAM_HEAD) / (2.0 * ALBEDO_PI));
    float wide = 0.03 + 0.1 * a;
    float stream = detail * 0.048 * albedoGauss((e - 1.0) / wide) * (1.0 - albedoSmooth(0.05, 0.75, a)) * 0.03 / wide
            * (0.55 + 0.6 * albedoNoise(vec3(th * 5.0, e * 6.0, 2.0), seed + 821));
    float dx = (ux - cos(ALBEDO_GALAXY_STREAM_HEAD)) * ALBEDO_GALAXY_STREAM_LONG, dy = uy - sin(ALBEDO_GALAXY_STREAM_HEAD);
    float point = max(0.008, fp);
    float dwarf = detail * (0.16 * exp(-sqrt(dx * dx + 2.25 * dy * dy) / 0.035)
            + 0.3 * (0.008 / point) * (0.008 / point) * albedoGauss(sqrt(dx * dx + dy * dy) / point));
    return stream * ALBEDO_GALAXY_STREAM + dwarf * color2;
}

// Galaxy.supernova: the one new star that explodes in the disc in each slot of time, with a fast rise and a slow fall.
vec3 albedoGalaxySupernova(float x, float y, float qs, float fp, float motion, float time, int seed) {
    float slots = floor(8.0 + 72.0 * motion + 0.5), period = 3600.0 / slots;
    float slot = min(slots - 1.0, floor(time / period)), age = time - slot * period;
    float rise = albedoSmooth(0.0, 0.4, age) * exp(-max(age - 0.4, 0.0) / 1.6);
    if (rise < 1e-4) return vec3(0.0);
    ivec3 h = ivec3(int(slot), 3, 0);
    float a = 6.2831853 * albedoUnit(albedoHash(h, seed + 701));
    float r = 0.25 + 0.6 * albedoUnit(albedoHash(h, seed + 702));
    float dx = x - r * cos(a), dy = y - r * sin(a) * qs;
    float wide = max(0.006, fp), d2 = (dx * dx + dy * dy) / (wide * wide);
    return 2.5 * rise * (0.006 / wide) * (0.006 / wide) * (exp(-d2) + 0.04 / (1.0 + d2 / 9.0)) * ALBEDO_GALAXY_SUPERNOVA;
}

// Galaxy.Parts: the values of one pixel that the parts of a galaxy share, and the light of each part.
struct AlbedoGalaxyParts {
    int seed;
    float dust, jets;
    vec3 color1, color2, color3;
    // The place in galaxy units, its distance from the center, and the width of one pixel in galaxy units.
    float x, y, rr, fp;
    float detail, motion, time;
    // The cosine and sine of the tilt, the axis ratios of the stars and the dust, and the place and distance in each disc frame.
    float ci, si, qs, qd, bs, bd, rs, rd;
    float twist, warp, wave, arm, edge, clumps;
    float star, armMix, tau, knots, pink, gasRegions;
    float bulge, nucleus;
    float jet, clear, lobe, cool, gas;
    vec3 jetColor, lobeColor, extra;
};

// Galaxy.dust: the optical depth of the dust of the disc, a lane over the center, clumps along the arms and, with detail, threads.
void albedoGalaxyDust(inout AlbedoGalaxyParts g) {
    float thetaD = g.rd > 0.0 ? atan(g.bd, g.x) : 0.0;
    float lane = 0.5 + 0.5 * cos(2.0 * (thetaD - g.twist * log(g.rd + 0.05)) + 1.4 * g.warp + 0.7);
    lane *= lane;
    float clump = albedoFbmSoft(vec3(g.x * 6.0, g.bd * 6.0, 1.7), g.seed + 2, 5, g.fp / g.qd * 6.0) * 0.5 + 0.5;
    // A dense lane of dust in the plane of the disc over the center, and more dust in clumps along the inner edge of each arm.
    float density = (1.0 - albedoSmooth(0.9, 1.4, g.rd)) * (0.9 * exp(-g.rd / 0.45) * (0.5 + clump)
            + exp(-g.rd / 0.5) * 2.0 * lane * lane * max(0.0, 2.0 * clump - 0.8));
    g.tau = g.dust * 7.0 * density / sqrt(g.qd);
    if (g.detail > 0.0) {
        // Threads of dust from noise in a frame that turns with the arms, so that they run along the arms and make no web.
        float along = log(g.rd + 0.05), phi = thetaD - g.twist * along;
        float threads = albedoFbmSoft(vec3(ALBEDO_GALAXY_DUST_THREADS * cos(phi), ALBEDO_GALAXY_DUST_THREADS * sin(phi), 1.6 * along),
                g.seed + 31, 4, g.fp / g.qd * 5.0 * ALBEDO_GALAXY_DUST_THREADS / (g.rd + 0.1));
        float fine = albedoFbmSoft(vec3(g.x * 16.0, g.bd * 16.0, 5.1), g.seed + 32, 3, g.fp / g.qd * 16.0);
        g.tau *= max(0.2, 1.0 + g.detail * (0.8 * threads + 0.3 * fine));
    }
}

// Galaxy.knots: knots of young stars on a grid of the image plane, few of them bright, which show only on the arms.
void albedoGalaxyKnots(inout AlbedoGalaxyParts g) {
    vec2 grid = vec2(g.x, g.y) / ALBEDO_GALAXY_KNOT_CELL;
    ivec2 cell = ivec2(floor(grid));
    float knots = 0.0, pink = 0.0;
    for (int i = -1; i <= 1; i++) {
        for (int j = -1; j <= 1; j++) {
            ivec3 h = ivec3(cell.x + i, cell.y + j, 0);
            if (albedoUnit(albedoHash(h, g.seed + 77)) > 0.5) continue;
            vec2 d = albedoJitter(grid, h, g.seed + 78, ALBEDO_GALAXY_KNOT_CELL);
            float big = albedoUnit(albedoHash(h, g.seed + 80));
            float radius = 0.003 + 0.006 * big;
            float spread = max(radius, 0.8 * g.fp);
            float v = (0.15 + 2.5 * big * big * big) * radius / spread * (radius / spread) * exp(-(d.x * d.x + d.y * d.y) / (spread * spread));
            knots += v;
            if (albedoUnit(albedoHash(h, g.seed + 81)) < 0.35) pink += v;
        }
    }
    // The knots are in groups, and fade out before a pixel is half a cell wide, where the search of 3 x 3 cells would cut a knot.
    float group = albedoSmooth(-0.05, 0.35, albedoNoise(vec3(g.x * 5.0, g.bs * 5.0, 2.9), g.seed + 82));
    float k = g.arm * group * exp(-g.rs / 0.5) * g.edge * 1.5
            * (1.0 - albedoSmooth(0.25 * ALBEDO_GALAXY_KNOT_CELL, 0.5 * ALBEDO_GALAXY_KNOT_CELL, g.fp));
    g.knots = knots * k;
    g.pink = pink * k;
}

// Galaxy.gasRegions: large regions of glowing hydrogen where the arms make stars, each with a faint glow around it.
void albedoGalaxyGasRegions(inout AlbedoGalaxyParts g) {
    vec2 grid = vec2(g.x, g.y) / ALBEDO_GALAXY_GAS_CELL;
    ivec2 cell = ivec2(floor(grid));
    float gas = 0.0;
    for (int i = -1; i <= 1; i++) {
        for (int j = -1; j <= 1; j++) {
            ivec3 h = ivec3(cell.x + i, cell.y + j, 0);
            if (albedoUnit(albedoHash(h, g.seed + 90)) > 0.55) continue;
            vec2 d = albedoJitter(grid, h, g.seed + 91, ALBEDO_GALAXY_GAS_CELL);
            float big = albedoUnit(albedoHash(h, g.seed + 93));
            float radius = 0.006 + 0.014 * big * big, spread = max(radius, 0.8 * g.fp);
            float d2 = (d.x * d.x + d.y * d.y) / (spread * spread);
            gas += (0.3 + 1.2 * big) * radius / spread * (radius / spread) * (exp(-d2) + 0.25 * exp(-d2 / 9.0));
        }
    }
    gas *= g.detail * albedoSmooth(0.2, 0.7, g.arm) * exp(-g.rs / 0.75) * g.edge * 1.3
            * (1.0 - albedoSmooth(0.25 * ALBEDO_GALAXY_GAS_CELL, 0.5 * ALBEDO_GALAXY_GAS_CELL, g.fp));
    g.gasRegions = gas;
}

// Galaxy.disc: the disc of stars with its arms, its dust and its knots, and with detail its grain, the ring at its edge and its gas regions.
void albedoGalaxyDisc(inout AlbedoGalaxyParts g) {
    g.warp = albedoFbmSoft(vec3(g.x * 3.0, g.bs * 3.0, 0.37), g.seed + 1, 7, g.fp / g.qs * 3.0);
    // The angle at the exact center is not defined in GLSL, and the arms have no weight there.
    float theta = g.rs > 0.0 ? atan(g.bs, g.x) : 0.0;
    g.wave = 0.5 + 0.5 * cos(2.0 * (theta - g.twist * log(g.rs + 0.05)) + 1.4 * g.warp);
    // The arms start out of the smooth inner disc, so that the center has no curl.
    g.arm = g.wave * g.wave * g.wave * albedoSmooth(0.2, 0.6, g.rs);
    g.edge = 1.0 - albedoSmooth(1.1, 1.6, g.rs);
    g.clumps = albedoSmooth(-0.4, 0.5, g.warp);
    g.star = exp(-g.rs / 0.4) * (0.08 + 2.0 * g.arm * (0.2 + 0.8 * g.clumps)) * g.edge;
    g.armMix = clamp(g.arm * 2.5, 0.0, 1.0);
    if (g.dust > 0.0 && g.rd < 1.4) albedoGalaxyDust(g);
    if (g.fp < 0.5 * ALBEDO_GALAXY_KNOT_CELL) albedoGalaxyKnots(g);
    if (g.detail > 0.0) {
        // The grain of single bright stars in the arms, and a ring of young stars at the edge of the disc.
        g.star *= 1.0 + g.detail * 0.45 * albedoNoise(vec3(g.x * 46.0, g.bs * 46.0, 9.1), g.seed + 41)
                * (1.0 - albedoSmooth(0.25, 0.5, g.fp / g.qs * 46.0));
        float ring = albedoGauss((g.rs - 1.08) / 0.1) * (0.25 + g.clumps) * (0.5 + 0.5 * g.wave);
        g.star += g.detail * 0.09 * ring;
        g.armMix = max(g.armMix, clamp(g.detail * ring * 1.5, 0.0, 1.0));
        if (g.fp < 0.5 * ALBEDO_GALAXY_GAS_CELL) albedoGalaxyGasRegions(g);
    }
}

// Galaxy.shells: the light of the bulge in its halo, and with detail the shells of the halo, the globular clusters and the stream.
void albedoGalaxyShells(inout AlbedoGalaxyParts g) {
    float x = g.x, y = g.y;
    float rb = sqrt(x * x + y * y / 0.64);
    // A small bright bulge in a large smooth halo of old stars, slightly longer along the jets, as the elliptical galaxy of Centaurus A.
    float rh = sqrt(x * x / 0.72 + y * y);
    g.bulge = 1.8 * exp(-5.0 * sqrt(rb)) + ALBEDO_GALAXY_HALO * exp(-3.2 * sqrt(rh)) * (1.0 - albedoSmooth(1.5, 3.0, g.rr));
    if (g.detail <= 0.0) return;
    // Shells of stars in the halo on alternate sides of the disc, the rest of a small galaxy that fell in.
    float thH = g.rr > 0.0 ? atan(y, x) : 0.0;
    for (int k = 0; k < 3; k++) {
        float radius = 1.25 + 0.36 * float(k);
        float toward = albedoSmooth(0.45, 0.95, cos(thH - float(k % 2) * ALBEDO_PI - 0.3 * float(k)));
        float rsh = rh * (1.0 + 0.07 * albedoNoise(vec3(thH * 2.0, float(k), 1.0), g.seed + 50));
        float patchy = 0.6 + 0.6 * albedoNoise(vec3(x * 5.0, y * 5.0, float(k) + 3.0), g.seed + 51);
        g.bulge += g.detail * toward * patchy * exp(-max(radius - rsh, 0.0) / 0.1) * (1.0 - albedoSmooth(radius, radius + 0.05, rsh))
                * (0.011 - 0.003 * float(k));
    }
    g.extra += albedoGalaxyClusters(x, y, g.fp, g.detail, g.seed);
    g.extra += albedoGalaxyStream(x, y, g.fp, g.detail, g.seed, g.color2);
}

// Galaxy.nucleus: the light of the nucleus, which flickers with motion.
void albedoGalaxyNucleus(inout AlbedoGalaxyParts g) {
    float rr = g.rr, rn = max(0.006, g.fp);
    float nucleus = 2.5 * (0.006 / rn) * (0.006 / rn) * exp(-rr * rr / (rn * rn)) + 0.12 / (1.0 + rr * rr / 0.0009);
    nucleus *= 1.0 + 0.5 * g.detail;
    // The nucleus flickers at two frequencies that are whole numbers of turns in the hour of the sky clock.
    if (g.motion > 0.0) nucleus *= 1.0 + g.motion * (0.16 * albedoWave(1320.0 / 3600.0, 0.0, g.time) + 0.1 * albedoWave(3384.0 / 3600.0, 0.0, g.time));
    g.nucleus = nucleus;
}

// Galaxy.jetsAndLobes: the jets along the axis of the disc and the lobes at their ends, and with detail the hot spots, bow shocks and cones.
void albedoGalaxyJetsAndLobes(inout AlbedoGalaxyParts g) {
    g.jetColor = g.color3;
    g.lobeColor = ALBEDO_GALAXY_LOBE;
    g.jet = 0.0;
    g.clear = 0.0;
    g.lobe = 0.0;
    g.cool = 0.0;
    g.gas = 0.0;
    if (g.jets <= 0.0) return;
    float x = g.x, y = g.y, fp = g.fp, detail = g.detail, motion = g.motion, time = g.time;
    int seed = g.seed;
    float side = y < 0.0 ? -1.0 : 1.0;
    float reach = ALBEDO_GALAXY_JET_LENGTH * (0.3 + 0.7 * g.si);
    float t = abs(y) / reach;
    // The jets bend in opposite directions, as a precessing jet does, and the near jet is brighter from relativistic beaming.
    float dx = x - side * 0.12 * t * t - 0.03 * t * albedoNoise(vec3(t * 5.0, side * 3.1, 0.6), seed + 502);
    float beam = side > 0.0 ? 1.0 : 0.3 + 0.15 * detail;
    // The part of the jet light that the dust does not dim: the far jet is behind the disc, and the near jet starts in it.
    g.clear = side > 0.0 ? albedoSmooth(0.02, 0.12, t) : 0.0;
    // Each jet is a cone that opens out, and its light goes into the lobe.
    float width = 0.005 + 0.03 * t;
    float spread = max(width, 0.8 * fp);
    float fade = 1.0 - albedoSmooth(0.25, 0.5, 16.0 / reach * fp);
    // With motion the knots go out along the jet, on a line of noise that comes back to its start in the hour of the sky clock.
    float knotNoise = motion > 0.0 ? albedoLoop(t * 16.0, albedoTurns(130.0, motion, time), side * 7.3, seed + 501)
            : albedoNoise(vec3(t * 16.0, side * 7.3, 1.7), seed + 501);
    float knot = max(0.0, 0.55 + 0.7 * fade * knotNoise);
    float tip = 1.0 - albedoSmooth(0.6, 1.05, t);
    float core = exp(-dx * dx / (spread * spread));
    float jet = 0.0;
    if (detail > 0.0) {
        // The beam sways from side to side at no fixed step, a fainter strand crosses it, and shocks make some knots brighter.
        float sway = albedoLoop(t * 9.0, albedoTurns(73.0, motion, time), side * 4.7, seed + 505);
        float cross = albedoLoop(t * 13.0, albedoTurns(106.0, motion, time), side * 2.9, seed + 506);
        float shock = max(0.0, albedoLoop(t * 11.0, albedoTurns(89.0, motion, time), side * 1.9, seed + 507));
        float dm = dx - detail * 1.6 * width * sway * fade, ds = dx + detail * 2.2 * width * cross * fade;
        float thin = max(0.6 * width, 0.8 * fp);
        core = exp(-dm * dm / (spread * spread)) + detail * 0.45 * 0.6 * width / thin * exp(-ds * ds / (thin * thin));
        knot *= 1.0 + detail * 4.0 * shock * shock * fade * (1.0 - albedoSmooth(0.5, 0.95, t));
        // A faint sheath of gas around the beam.
        jet = detail * 0.12 * beam * exp(-dx * dx / (9.0 * spread * spread)) / (1.0 + 3.0 * t) * tip;
        // The beam is white where it is hot at its start and violet where it cools at its end.
        float start = 1.0 - albedoSmooth(0.0, 0.3, t), end = 0.6 * albedoSmooth(0.55, 1.0, t);
        vec3 hot = g.color3 + (1.0 - g.color3) * 0.7 * start;
        g.jetColor += detail * (hot + (ALBEDO_GALAXY_JET_END - hot) * end - g.color3);
    }
    jet += 1.4 * beam * width / spread * core * knot / (1.0 + 3.0 * t) * tip;
    // With motion the turbulence of the lobes changes slowly, back and forth along the third axis of its noise.
    float drift = motion > 0.0 ? 0.8 * albedoWave(1.0, 0.0, albedoTurns(10.0, motion, time)) : 0.0;
    float turb = albedoFbmSoft(vec3(x * 2.4, y * 2.4, side * 5.1 + drift), seed + 601, 7, fp * 2.4);
    float thread = 1.0 - abs(turb);
    thread *= thread;
    thread *= thread;
    // A lobe is longer along the jet than across it, and the turbulence moves its edge.
    float lx = x - side * 0.19, ly = (y - side * reach * 1.3) / 1.3;
    float dl = sqrt(lx * lx + ly * ly) / (0.4 * reach) + 0.4 * turb;
    // Broken arcs of shells around a point on the jet, as the rings in the lobes of Hercules A, which fade before they get too thin.
    float sx = x - side * 0.14, sy = y - side * reach * 0.85;
    float bend = albedoNoise(vec3(x * 3.0, y * 3.0, side * 1.7), seed + 603);
    float shell = 0.5 + 0.5 * cos(sqrt(sx * sx + sy * sy) / (ALBEDO_GALAXY_LOBE_RING * reach) * 6.2831853 + 2.5 * bend);
    shell *= shell * albedoSmooth(-0.15, 0.25, albedoNoise(vec3(x * 4.0, y * 4.0, side * 2.3), seed + 604))
            * (1.0 - albedoSmooth(0.12, 0.25, fp / (ALBEDO_GALAXY_LOBE_RING * reach)));
    float hx = x - side * 0.12, hy = y - side * reach;
    float hot = 0.25 * exp(-(hx * hx + hy * hy) / 0.0016);
    float body = max(0.08, 0.2 + 0.35 * turb + 0.6 * thread) * (0.5 + 1.6 * shell);
    float lobe = (exp(-2.0 * dl * dl) * body * 0.35 + hot) * g.jets;
    // The inner part of a lobe, where the jet ends, takes some of the jet color.
    g.cool = 0.4 * (1.0 - albedoSmooth(0.1, 0.9, dl));
    // Gas near the jet that its beam lights, less far from the axis.
    float gas = 0.08 * thread * exp(-abs(dx) / (0.08 + 0.15 * t)) * albedoSmooth(0.1, 0.4, t) * (1.0 - albedoSmooth(0.9, 1.3, t));
    jet *= g.jets;
    gas *= g.jets;
    if (detail > 0.0) {
        float strength = detail * g.jets * (side > 0.0 ? 1.0 : 0.6);
        // Filaments in the lobe, and a rim where it pushes into the gas around it, which glows as embers.
        float fil = 1.0 - abs(albedoFbmSoft(vec3(x * 3.2, y * 3.2, side * 4.2 + drift), seed + 611, 6, fp * 3.2));
        fil *= fil * fil;
        fil *= fil;
        lobe += detail * g.jets * (exp(-1.6 * dl * dl) * fil * 0.3 + albedoGauss((dl - 0.95) / 0.2) * (0.015 + 0.09 * thread));
        g.lobeColor += (ALBEDO_GALAXY_LOBE_RIM - ALBEDO_GALAXY_LOBE) * (detail * 0.55 * albedoSmooth(0.5, 1.1, dl));
        // The hot spot where the jet ends, and the thin bow shock ahead of it.
        float spot = 0.0005 + fp * fp, h2 = (hx * hx + hy * hy) / spot;
        float hotSpot = strength * (0.9 * 0.0005 / spot * exp(-h2) + 0.1 / (1.0 + h2 / 8.0));
        float bx = x - side * 0.12, by = y - side * (reach - 0.08), br = sqrt(bx * bx + by * by);
        float ahead = albedoSmooth(0.45, 0.95, by * side / max(br, 1e-4));
        float thin = max(0.014, 0.8 * fp);
        float bow = strength * 0.13 * 0.014 / thin
                * albedoGauss((br - 0.19 - 0.02 * albedoNoise(vec3(bx * 6.0, by * 6.0, side * 2.0), seed + 612)) / thin)
                * ahead * (0.5 + 0.7 * thread);
        // Cones of gas beside the nucleus that its light ionizes.
        float angle = abs(dx) / (abs(y) + 0.03);
        float cone = strength * 0.2 * (1.0 - albedoSmooth(0.28, 0.62, angle)) * exp(-abs(y) / 0.32)
                * albedoSmooth(0.04, 0.14, abs(y)) * (0.35 + 0.9 * thread);
        g.extra += hotSpot * ALBEDO_GALAXY_HOT_SPOT + bow * ALBEDO_GALAXY_BOW_SHOCK + cone * ALBEDO_GALAXY_ION_CONE;
    }
    g.jet = jet;
    g.lobe = lobe;
    g.gas = gas;
}

// Galaxy.sample: an active galaxy at the layer center, modeled on Centaurus A and Hercules A, with a COLOR_2 bulge in a halo,
// COLOR_1 arms at TILT, dust and COLOR_3 jets, where DETAIL adds the fine parts and MOTION moves the jets, the nucleus and the
// lobes with the time.
vec4 albedoGalaxy(int layer, vec3 p, float footprint, int flags) {
    float zz = -p.z;
    if (zz < 0.2) return vec4(0.0);
    vec4 values = albedoLayer(layer, 1);
    vec4 shape = albedoLayer(layer, 2);
    AlbedoGalaxyParts g;
    g.seed = int(shape.y);
    float tilt = albedoLayer(layer, 4).w;
    g.dust = (flags & ALBEDO_FLAG_DUST) != 0 ? albedoLayer(layer, 5).w : 0.0;
    g.jets = (flags & ALBEDO_FLAG_JETS) != 0 ? albedoLayer(layer, 6).w : 0.0;
    // The detail is in the float of the height and the motion in the first float of the first OKLab color, which a galaxy does not read.
    g.detail = (flags & ALBEDO_FLAG_DETAIL) != 0 ? values.w : 0.0;
    g.motion = (flags & ALBEDO_FLAG_MOTION) != 0 ? albedoLayer(layer, 5).x : 0.0;
    // The galaxy moves at the time of the header, and is as at time 0 in a still sky, as in the bake.
    g.time = max(SkyHeader.w, 0.0);
    g.color1 = albedoLayer(layer, 3).rgb;
    g.color2 = albedoLayer(layer, 4).rgb;
    g.color3 = vec3(albedoLayer(layer, 7).w, albedoLayer(layer, 8).w, albedoLayer(layer, 9).w);
    // The plane of the image in galaxy units, where 1 is about the radius of the disc.
    float size = 0.22 * shape.x;
    g.x = p.x / zz / size;
    g.y = p.y / zz / size;
    g.rr = sqrt(g.x * g.x + g.y * g.y);
    if (g.rr > ALBEDO_GALAXY_REACH) return vec4(0.0);
    // The width of one pixel in galaxy units, where the plane of the image stretches by 1 / zz squared.
    g.fp = footprint / (size * zz * zz);
    float incl = tilt * (ALBEDO_PI / 2.0);
    g.ci = cos(incl);
    g.si = sin(incl);
    // The axis ratios of the disc of stars and of the thinner layer of dust, each with its own thickness.
    g.qs = sqrt(g.ci * g.ci + 0.0225 * g.si * g.si);
    g.qd = sqrt(g.ci * g.ci + 0.0036 * g.si * g.si);
    g.bs = g.y / g.qs;
    g.bd = g.y / g.qd;
    g.rs = sqrt(g.x * g.x + g.bs * g.bs);
    g.rd = sqrt(g.x * g.x + g.bd * g.bd);
    g.twist = 1.5 + values.z * 4.0;
    g.star = 0.0;
    g.armMix = 0.0;
    g.tau = 0.0;
    g.knots = 0.0;
    g.pink = 0.0;
    g.gasRegions = 0.0;
    g.extra = vec3(0.0);
    if (g.rs < 1.6) albedoGalaxyDisc(g);
    albedoGalaxyShells(g);
    albedoGalaxyNucleus(g);
    albedoGalaxyJetsAndLobes(g);
    if (g.motion > 0.0) g.extra += albedoGalaxySupernova(g.x, g.y, g.qs, g.fp, g.motion, g.time, g.seed);
    // Galaxy.compose: the light of all parts through the dust.
    // The part of the bulge light behind the dust, more on the near side of the disc, which is below its center, and most at the center.
    float s = 0.8 - g.y * 6.0 * g.si / max(g.ci, 0.25);
    float behind = 0.5 + 0.5 * s / sqrt(1.0 + s * s);
    vec3 extinction = g.tau * ALBEDO_GALAXY_DUST_REDDENING;
    vec3 through = exp(-extinction), mixed = 1.0 / (1.0 + 0.5 * extinction);
    vec3 disc = g.color2 + (g.color1 - g.color2) * g.armMix;
    // With detail the bulge is pale at its core and amber far out, and the dust gives back a warm part of the light that it takes.
    float graded = albedoSmooth(0.03, 0.9, g.rr), back = g.detail * 0.55 * g.tau * exp(1.0 - g.tau);
    vec3 bulgeColor = g.color2 * (1.0 + g.detail * (ALBEDO_GALAXY_BULGE_CORE + (ALBEDO_GALAXY_BULGE_EDGE - ALBEDO_GALAXY_BULGE_CORE) * graded - 1.0));
    vec3 taken = g.bulge * bulgeColor * behind * (1.0 - through);
    vec3 gasColor = ALBEDO_GALAXY_HALPHA + (ALBEDO_GALAXY_GAS_CORE - ALBEDO_GALAXY_HALPHA) * clamp(g.gasRegions, 0.0, 1.0);
    vec3 light = g.bulge * bulgeColor * (1.0 - behind * (1.0 - through)) + 0.9 * g.star * disc * mixed
            + ((g.knots - g.pink) * g.color1 + g.pink * ALBEDO_GALAXY_HALPHA + g.gasRegions * gasColor) * mixed
            + g.nucleus * sqrt(through) * ALBEDO_GALAXY_NUCLEUS
            + g.jet * g.jetColor * (through + (1.0 - through) * g.clear) + g.lobe * (g.lobeColor + (g.color3 - g.lobeColor) * g.cool)
            + g.gas * g.color3 + taken * back * ALBEDO_GALAXY_DUST_GLOW + g.extra;
    light *= max(shape.z, shape.w);
    // The light as a color of at most 1 and an alpha, so that Add gives the full light and Normal stays near it.
    float a = min(1.0, max(light.r, max(light.g, light.b)));
    return vec4(light / max(a, 1e-6), a);
}

#endif
