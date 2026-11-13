package dev.albedo.core.config;

import dev.albedo.core.Category;
import dev.albedo.core.sky.SkyPresets;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class SkyCodecTest {
    private static Config withSky(CategorySettings sky) {
        Config c = Config.defaults();
        Profile p = c.activeProfile().renamed("night").with(Category.SKY, sky);
        return c.withImportedProfile(p);
    }

    private static CategorySettings layers(SkyDesign d) {
        return new CategorySettings(Appearance.of(Appearance.Mode.LAYERS), false, false, Collections.<Rule>emptyList(), d);
    }

    private static String error(String sky) {
        String json = "{\"profiles\": {\"night\": {\"sky\": " + sky + "}}}";
        return assertThrows(IllegalArgumentException.class, () -> ConfigCodec.decode(json)).getMessage();
    }

    @Test
    void everyPresetGoesThroughTheFileUnchanged() {
        for (SkyPresets.Preset p : SkyPresets.all()) {
            SkyDesign d = p.design();
            Config c = withSky(layers(d));
            Config back = ConfigCodec.decode(ConfigCodec.encode(c));
            assertEquals(d, back.profiles().get("night").get(Category.SKY).sky());
            assertEquals(ConfigCodec.encode(c), ConfigCodec.encode(back));
        }
    }

    @Test
    void anOldGalaxyLayerLoadsWithTheDefaultsOfTheNewValues() {
        String json = "{\"profiles\": {\"night\": {\"sky\": {\"mode\": \"layers\", \"layers\": [{\"id\": 1, \"kind\": \"galaxy\","
                + " \"color1\": \"#9FB4FF\", \"color2\": \"#FFE7B0\", \"amount\": 55, \"seed\": 4}]}}}}";
        SkyLayer l = ConfigCodec.decode(json).profiles().get("night").get(Category.SKY).sky().layers().get(0);
        for (SkyLayer.Param p : new SkyLayer.Param[]{SkyLayer.Param.COLOR_3, SkyLayer.Param.TILT, SkyLayer.Param.DUST, SkyLayer.Param.JETS}) {
            assertEquals(SkyLayer.Kind.GALAXY.defaultValue(p), l.param(p), p.key);
        }
        assertEquals(55, l.param(SkyLayer.Param.AMOUNT));
        assertEquals(0, l.param(SkyLayer.Param.DETAIL), "a galaxy from before the detail stays plain");
        assertEquals(0, l.param(SkyLayer.Param.MOTION), "and still");
        assertTrue(error("{\"mode\": \"layers\", \"layers\": [{\"id\": 1, \"kind\": \"galaxy\", \"tilt\": 91}]}")
                .endsWith("tilt: use a whole number from 0 to 90"));
        assertTrue(error("{\"mode\": \"layers\", \"layers\": [{\"id\": 1, \"kind\": \"stars\", \"jets\": 50}]}")
                .contains("'jets' has no meaning for stars"));
    }

    @Test
    void aFileWithoutLayersIsWrittenAsBefore() {
        Config c = withSky(CategorySettings.UNTOUCHED.withAppearance(Appearance.solid(0xFF203040)));
        String text = ConfigCodec.encode(c);
        assertFalse(text.contains("layers") || text.contains("celestial") || text.contains("light\""), text);
        String expected = "      \"sky\": {\n        \"mode\": \"solid\",\n        \"color\": \"#203040\"\n      },\n";
        assertTrue(text.contains(expected), text);
        // Layers kept in memory for a later switch back are not written with another style.
        Config kept = withSky(CategorySettings.UNTOUCHED.withAppearance(Appearance.solid(0xFF203040)).withSky(SkyPresets.DUSK));
        assertEquals(text, ConfigCodec.encode(kept));
        Config vanilla = withSky(CategorySettings.UNTOUCHED.withSky(SkyPresets.DUSK));
        assertFalse(ConfigCodec.encode(vanilla).contains("\"sky\""));
    }

    @Test
    void theExtentIsWrittenOnlyForAFullSphere() {
        SkyDesign horizon = SkyDesign.NONE.withAdded(SkyLayer.Kind.STARS);
        assertFalse(ConfigCodec.encode(withSky(layers(horizon))).contains("extent"));
        SkyDesign sphere = horizon.withExtent(SkyDesign.Extent.SPHERE);
        Config c = withSky(layers(sphere));
        assertTrue(ConfigCodec.encode(c).contains("\"extent\": \"sphere\""));
        assertEquals(sphere, ConfigCodec.decode(ConfigCodec.encode(c)).profiles().get("night").get(Category.SKY).sky());
        assertEquals("night.sky.extent: use one of [horizon, sphere]", error("{\"mode\": \"layers\", \"extent\": \"cube\", \"layers\": []}"));
    }

    @Test
    void theRenderIsWrittenOnlyForTheFastSky() {
        SkyDesign sharp = SkyDesign.NONE.withAdded(SkyLayer.Kind.STARS);
        assertFalse(ConfigCodec.encode(withSky(layers(sharp))).contains("render"));
        SkyDesign fast = sharp.withRender(SkyDesign.Render.FAST);
        Config c = withSky(layers(fast));
        assertTrue(ConfigCodec.encode(c).contains("\"render\": \"fast\""));
        assertEquals(fast, ConfigCodec.decode(ConfigCodec.encode(c)).profiles().get("night").get(Category.SKY).sky());
        assertEquals("night.sky.render: use one of [sharp, fast]", error("{\"mode\": \"layers\", \"render\": \"slow\", \"layers\": []}"));
    }

    @Test
    void theDefaultTurningIsOneSlowTurnInRealTimeAndTheBakeReadsItAsStill() {
        assertEquals(new SkyTurning(100, 1, SkyTurning.Clock.REAL, 45, SkyTurning.Direction.NORMAL, 0), SkyTurning.DEFAULT);
        assertTrue(SkyTurning.DEFAULT.turns());
        assertFalse(SkyTurning.DEFAULT.showsTrails());
        assertFalse(SkyTurning.STILL.turns());
        assertEquals(SkyTurning.STILL, SkyTurning.DEFAULT.baked());
        for (SkyPresets.Preset p : SkyPresets.all()) {
            SkyDesign d = p.design();
            if (d == SkyPresets.DUSK || d == SkyPresets.AURORA) {
                assertEquals(SkyTurning.STILL, d.turning(), p.key() + " has layers along the horizon, which a turn would carry up");
            } else if (d != SkyPresets.TRAILS) {
                assertEquals(SkyTurning.DEFAULT, d.turning(), "the other presets but Trails have the default turning");
            }
        }
        SkyTurning trails = SkyPresets.TRAILS.turning();
        assertEquals(SkyTurning.DEFAULT.clock(), trails.clock(), "the Trails preset turns in real time");
        assertEquals(SkyTurning.DEFAULT.speed(), trails.speed(), "the Trails preset turns at 1x");
        assertEquals(SkyTurning.DEFAULT.amount(), trails.amount());
    }

    @Test
    void theTurningIsWrittenOnlyWhenItIsNotTheDefault() {
        SkyDesign stars = SkyDesign.NONE.withAdded(SkyLayer.Kind.STARS);
        assertEquals(SkyTurning.DEFAULT, stars.turning(), "a new sky turns");
        assertFalse(ConfigCodec.encode(withSky(layers(stars))).contains("turning"));
        SkyDesign missing = ConfigCodec.decode("{\"profiles\": {\"night\": {\"sky\": {\"mode\": \"layers\", \"layers\": "
                + "[{\"id\": 1, \"kind\": \"stars\"}]}}}}").profiles().get("night").get(Category.SKY).sky();
        assertEquals(SkyTurning.DEFAULT, missing.turning(), "a file with no turning loads with the default turning");

        SkyDesign turning = stars.withTurning(new SkyTurning(40, 3, SkyTurning.Clock.REAL, 45, SkyTurning.Direction.REVERSE, 270));
        Config c = withSky(layers(turning));
        String text = ConfigCodec.encode(c);
        assertTrue(text.contains("\"amount\": 40") && text.contains("\"clock\": \"real\"") && text.contains("\"pole\": 270"), text);
        assertEquals(turning, ConfigCodec.decode(text).profiles().get("night").get(Category.SKY).sky());
        String amountOnly = ConfigCodec.encode(withSky(layers(stars.withTurning(SkyTurning.STILL.withAmount(30)))));
        assertTrue(amountOnly.contains("\"turning\": {\n          \"amount\": 30\n        },"), "only the values that differ: " + amountOnly);
        SkyDesign world = ConfigCodec.decode(amountOnly).profiles().get("night").get(Category.SKY).sky();
        assertEquals(SkyTurning.Clock.WORLD, world.turning().clock(), "a turning with no clock follows the world time, as before");
        assertEquals(SkyTurning.STILL.withAmount(30), world.turning());
        SkyDesign oldStill = ConfigCodec.decode("{\"profiles\": {\"night\": {\"sky\": {\"mode\": \"layers\", \"turning\": {\"trails\": 20},"
                + " \"layers\": [{\"id\": 1, \"kind\": \"stars\"}]}}}}").profiles().get("night").get(Category.SKY).sky();
        assertEquals(SkyTurning.STILL.withTrails(20), oldStill.turning(), "a turning with no amount stays still, as before");

        SkyTurning[] all = {SkyTurning.DEFAULT, SkyTurning.STILL, SkyTurning.DEFAULT.withClock(SkyTurning.Clock.WORLD),
            SkyTurning.DEFAULT.withAmount(0), SkyTurning.DEFAULT.withSpeed(4).withTrails(30), SkyTurning.STILL.withClock(SkyTurning.Clock.REAL)};
        for (SkyTurning t : all) {
            Config d = withSky(layers(stars.withTurning(t)));
            Config back = ConfigCodec.decode(ConfigCodec.encode(d));
            assertEquals(t, back.profiles().get("night").get(Category.SKY).sky().turning(), "a round trip keeps " + ConfigCodec.encode(d));
            assertEquals(ConfigCodec.encode(d), ConfigCodec.encode(back));
        }

        assertEquals("night.sky.turning.height: use a whole number from 0 to 90",
                error("{\"mode\": \"layers\", \"turning\": {\"height\": 91}, \"layers\": []}"));
        assertEquals("night.sky.turning.clock: use one of [world, real]",
                error("{\"mode\": \"layers\", \"turning\": {\"clock\": \"moon\"}, \"layers\": []}"));
        assertTrue(error("{\"mode\": \"layers\", \"turning\": {\"tilt\": 3}, \"layers\": []}").contains("unknown setting 'tilt'"));
        assertEquals("night.sky: 'turning' works only with \"mode\": \"layers\"",
                error("{\"mode\": \"solid\", \"turning\": {\"amount\": 50}}"));
    }

    @Test
    void theTrailsAreOffByDefaultAndAreWrittenOnlyWhenTheyAreNotTheDefault() {
        assertEquals(0, SkyTurning.DEFAULT.trails());
        assertEquals(SkyTurning.TRAIL_LIGHT, SkyTurning.DEFAULT.trailLight());
        assertFalse(SkyTurning.STILL.withTrails(40).showsTrails(), "a still sky has no trails");
        assertTrue(SkyTurning.STILL.withAmount(10).withTrails(40).showsTrails());
        SkyDesign stars = SkyDesign.NONE.withAdded(SkyLayer.Kind.STARS);
        SkyTurning turning = new SkyTurning(100, 1, SkyTurning.Clock.WORLD, 45, SkyTurning.Direction.NORMAL, 0);
        String plain = ConfigCodec.encode(withSky(layers(stars.withTurning(turning))));
        assertFalse(plain.contains("trails") || plain.contains("trailLight"), "a turning with no trails is written as before: " + plain);
        SkyDesign old = ConfigCodec.decode("{\"profiles\": {\"night\": {\"sky\": {\"mode\": \"layers\", \"turning\": {\"amount\": 100},"
                + " \"layers\": [{\"id\": 1, \"kind\": \"stars\"}]}}}}").profiles().get("night").get(Category.SKY).sky();
        assertEquals(0, old.turning().trails(), "a file from before trails loads with no trails");
        assertEquals(SkyTurning.TRAIL_LIGHT, old.turning().trailLight());

        SkyDesign trails = stars.withTurning(turning.withTrails(45).withTrailLight(70));
        String text = ConfigCodec.encode(withSky(layers(trails)));
        assertTrue(text.contains("\"trails\": 45") && text.contains("\"trailLight\": 70"), text);
        assertEquals(trails, ConfigCodec.decode(text).profiles().get("night").get(Category.SKY).sky());
        String lengthOnly = ConfigCodec.encode(withSky(layers(stars.withTurning(turning.withTrails(30)))));
        assertTrue(lengthOnly.contains("\"trails\": 30") && !lengthOnly.contains("trailLight"), "only the values that differ: " + lengthOnly);

        assertEquals("night.sky.turning.trails: use a whole number from 0 to 90",
                error("{\"mode\": \"layers\", \"turning\": {\"trails\": 91}, \"layers\": []}"));
        assertEquals("night.sky.turning.trailLight: use a whole number from 0 to 100",
                error("{\"mode\": \"layers\", \"turning\": {\"trailLight\": -1}, \"layers\": []}"));
    }

    @Test
    void transformsAreWrittenOnlyForTheAxesOfTheKind() {
        SkyDesign d = SkyDesign.NONE.withAdded(SkyLayer.Kind.STARS);
        d = d.withLayer(0, d.layers().get(0).withTransform(new Transform(90, 20, 0, 150)));
        String text = ConfigCodec.encode(withSky(layers(d)));
        assertTrue(text.contains("\"yaw\": 90") && text.contains("\"scale\": 150") && !text.contains("\"roll\""), text);
        assertTrue(error("{\"mode\": \"layers\", \"layers\": [{\"id\": 1, \"kind\": \"blur\", \"transform\": {\"yaw\": 3}}]}")
                .startsWith("night.sky.layers[0].transform: 'yaw' has no meaning for blur"));
    }

    @Test
    void theSpeedIsAValueOnlyOfTheLayersThatMove() {
        SkyDesign d = SkyDesign.NONE.withAdded(SkyLayer.Kind.AURORA);
        d = d.withLayer(0, d.layers().get(0).withParam(SkyLayer.Param.SPEED, 80));
        Config c = withSky(layers(d));
        assertTrue(ConfigCodec.encode(c).contains("\"speed\": 80"));
        assertEquals(d, ConfigCodec.decode(ConfigCodec.encode(c)).profiles().get("night").get(Category.SKY).sky());
        assertTrue(error("{\"mode\": \"layers\", \"layers\": [{\"id\": 1, \"kind\": \"stars\", \"speed\": 3}]}")
                .startsWith("night.sky.layers[0]: 'speed' has no meaning for stars"));
        SkyDesign noSpeed = ConfigCodec.decode("{\"profiles\": {\"night\": {\"sky\": {\"mode\": \"layers\", \"layers\": "
                + "[{\"id\": 1, \"kind\": \"aurora\"}]}}}}").profiles().get("night").get(Category.SKY).sky();
        assertEquals(50, noSpeed.layers().get(0).param(SkyLayer.Param.SPEED), "a file with no speed takes the default");
    }

    @Test
    void theDetailAndTheMotionOfAGalaxyAreWrittenOnlyWhenTheyAreOn() {
        SkyDesign d = SkyDesign.NONE.withAdded(SkyLayer.Kind.GALAXY).withAdded(SkyLayer.Kind.DEEP_FIELD);
        String plain = ConfigCodec.encode(withSky(layers(d)));
        assertFalse(plain.contains("detail") || plain.contains("motion"), plain);
        assertTrue(plain.contains("\"kind\": \"deep_field\""), plain);
        d = d.withLayer(0, d.layers().get(0).withParam(SkyLayer.Param.DETAIL, 70).withParam(SkyLayer.Param.MOTION, 50));
        Config c = withSky(layers(d));
        assertTrue(ConfigCodec.encode(c).contains("\"detail\": 70") && ConfigCodec.encode(c).contains("\"motion\": 50"));
        assertEquals(d, ConfigCodec.decode(ConfigCodec.encode(c)).profiles().get("night").get(Category.SKY).sky());
        assertTrue(error("{\"mode\": \"layers\", \"layers\": [{\"id\": 1, \"kind\": \"deep_field\", \"detail\": 3}]}")
                .startsWith("night.sky.layers[0]: 'detail' has no meaning for deep_field"));
    }

    @Test
    void aTwinkleAndAPulseAreWrittenOnlyWhenTheyAreOn() {
        SkyDesign d = SkyDesign.NONE.withAdded(SkyLayer.Kind.STARS).withAdded(SkyLayer.Kind.FRACTAL);
        String still = ConfigCodec.encode(withSky(layers(d)));
        assertFalse(still.contains("twinkle") || still.contains("pulse"), still);
        d = d.withLayer(0, d.layers().get(0).withParam(SkyLayer.Param.TWINKLE, 40));
        d = d.withLayer(1, d.layers().get(1).withParam(SkyLayer.Param.PULSE, 70));
        Config c = withSky(layers(d));
        assertTrue(ConfigCodec.encode(c).contains("\"twinkle\": 40") && ConfigCodec.encode(c).contains("\"pulse\": 70"));
        assertEquals(d, ConfigCodec.decode(ConfigCodec.encode(c)).profiles().get("night").get(Category.SKY).sky());
        assertTrue(error("{\"mode\": \"layers\", \"layers\": [{\"id\": 1, \"kind\": \"gradient\", \"pulse\": 3}]}")
                .startsWith("night.sky.layers[0]: 'pulse' has no meaning for gradient"));
    }

    @Test
    void theReadmeExampleLoads() {
        String sky = "{\"mode\": \"layers\", \"celestial\": \"hide\", \"light\": \"day_night\", \"layers\": ["
                + "{\"id\": 1, \"kind\": \"gradient\", \"color1\": \"#F08A4B\", \"color2\": \"#1A1F4D\", \"height\": 22, \"amount\": 45},"
                + "{\"id\": 2, \"kind\": \"noise\", \"output\": \"mask_only\", \"amount\": 55, \"seed\": 12},"
                + "{\"id\": 3, \"kind\": \"stars\", \"blend\": \"add\", \"opacity\": 80, \"amount\": 60, \"seed\": 3,"
                + " \"transform\": {\"scale\": 150}},"
                + "{\"id\": 4, \"kind\": \"blur\", \"amount\": 25, \"mask\": {\"layer\": 2, \"mode\": \"light\"}}]}";
        Config c = ConfigCodec.decode("{\"profiles\": {\"night\": {\"sky\": " + sky + "}}}");
        SkyDesign d = c.profiles().get("night").get(Category.SKY).sky();
        assertEquals(4, d.layers().size());
        assertEquals(SkyDesign.Light.DAY_NIGHT, d.light());
        assertEquals(150, d.layers().get(2).transform().scale());
        assertEquals(SkyMask.of(2, SkyMask.Mode.LIGHT), d.layers().get(3).mask());
    }

    @Test
    void errorsNameThePlace() {
        assertEquals("night.sky.layers[1].mask: no layer below this one has id 7", error("{\"mode\": \"layers\", \"layers\": ["
                + "{\"id\": 1, \"kind\": \"gradient\"}, {\"id\": 2, \"kind\": \"blur\", \"mask\": {\"layer\": 7}}]}"));
        assertTrue(error("{\"mode\": \"layers\", \"layers\": [{\"id\": 1, \"kind\": \"rainbow\"}]}")
                .startsWith("night.sky.layers[0].kind: use one of [gradient, glow"));
        assertTrue(error("{\"mode\": \"layers\", \"layers\": [{\"id\": 1, \"kind\": \"gradient\", \"seed\": 4}]}")
                .startsWith("night.sky.layers[0]: 'seed' has no meaning for gradient"));
        assertEquals("night.sky.layers[0].opacity: use a whole number from 0 to 100",
                error("{\"mode\": \"layers\", \"layers\": [{\"id\": 1, \"kind\": \"stars\", \"opacity\": 120}]}"));
        assertEquals("night.sky.layers[1].id: another layer has id 1",
                error("{\"mode\": \"layers\", \"layers\": [{\"id\": 1, \"kind\": \"stars\"}, {\"id\": 1, \"kind\": \"blur\"}]}"));
        assertEquals("night.sky: 'layers' works only with \"mode\": \"layers\"", error("{\"mode\": \"solid\", \"layers\": []}"));
        StringBuilder nine = new StringBuilder("{\"mode\": \"layers\", \"layers\": [");
        for (int i = 1; i <= 9; i++) nine.append(i > 1 ? "," : "").append("{\"id\": ").append(i).append(", \"kind\": \"stars\"}");
        assertEquals("night.sky.layers: a sky has a maximum of 8 layers", error(nine + "]}"));
        assertTrue(error("{\"mode\": \"layers\", \"layers\": [], \"color1\": 3}").contains("unknown setting 'color1'"));
        String blocks = "{\"profiles\": {\"night\": {\"blocks\": {\"mode\": \"layers\"}}}}";
        assertThrows(IllegalArgumentException.class, () -> ConfigCodec.decode(blocks), "layers is only for the sky");
    }
}
