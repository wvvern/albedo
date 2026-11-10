package dev.albedo.core.ui;

import dev.albedo.core.Albedo;
import dev.albedo.core.Category;
import dev.albedo.core.Keys;
import dev.albedo.core.PixelBuffer;
import dev.albedo.core.Platform;
import dev.albedo.core.RefreshResult;
import dev.albedo.core.config.Appearance;
import dev.albedo.core.config.CategorySettings;
import dev.albedo.core.config.Chroma;
import dev.albedo.core.config.Config;
import dev.albedo.core.config.ConfigCodec;
import dev.albedo.core.config.Profile;
import dev.albedo.core.config.Rule;
import dev.albedo.core.config.ServerRule;
import dev.albedo.core.config.SkyDesign;
import dev.albedo.core.config.SkyLayer;
import dev.albedo.core.config.SkyMask;
import dev.albedo.core.config.SkyTurning;
import dev.albedo.core.config.Toggle;
import dev.albedo.core.dev.Harness;
import dev.albedo.core.sky.SkyClock;
import dev.albedo.core.sky.SkyPresets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Drives the real settings screen headlessly: clicks on drawn labels, checks the saved config. */
class SettingsEditorTest {
    @TempDir
    static Path dir;
    static Albedo sc;

    @BeforeAll
    static void init() {
        sc = Albedo.init(new FakePlatform(dir.toFile()));
        load("minecraft:block/oak_leaves", 0xFF2E7D32);
        for (String id : new String[]{"stone", "dirt", "gravel", "grass_block_top", "grass_block_side", "grass_block_snow",
            "grass_block_side_overlay", "dirt_path_top", "dirt_path_side"}) {
            load("minecraft:block/" + id, 0xFF7F7F7F);
        }
    }

    private static void load(String location, int argb) {
        Albedo.processTexture(location, new PixelBuffer.Heap(1, 1, new int[]{argb}), pixels -> {
            ((FakePlatform) sc.platform()).uploads++;
            return true;
        });
    }

    /** The defaults plus the user's own profile "test", which is active. */
    private static Config withOwnProfile() {
        Config c = Config.defaults();
        return c.withImportedProfile(c.activeProfile().renamed("test"));
    }

    @Test
    void aChangeToABuiltInProfileMakesACopyAndSaysSo() {
        sc.apply(Config.defaults());
        SettingsEditor editor = new SettingsEditor(sc);
        RecordingCanvas canvas = new RecordingCanvas(533, 300);
        editor.render(canvas, 0, 0);
        assertNotNull(canvas.find("built-in"), "the header says the profile is built in: " + canvas.texts.keySet());
        click(editor, canvas, "Rename");
        assertNull(canvas.find("Save"), "a built-in profile cannot be renamed");
        click(editor, canvas, "Average");
        assertNotNull(canvas.find("is built in"), "says where the change went: " + canvas.texts.keySet());
        click(editor, canvas, "Done");
        assertEquals("quake-white-custom", sc.config().activeProfileName());
        assertEquals(Appearance.Mode.AVERAGE, sc.config().activeProfile().get(Category.BLOCKS).appearance().mode());
        assertEquals(Appearance.Mode.SOLID, sc.config().profiles().get("quake-white").get(Category.BLOCKS).appearance().mode());
    }

    @Test
    void theProfileListFiltersAsYouType() {
        sc.apply(Config.defaults());
        SettingsEditor editor = new SettingsEditor(sc);
        RecordingCanvas canvas = new RecordingCanvas(533, 300);
        editor.render(canvas, 0, 0);
        click(editor, canvas, "Profile: quake-white");
        assertNotNull(canvas.find("quake-black"), "the list shows all profiles: " + canvas.texts.keySet());
        type(editor, canvas, "mini");
        assertNull(canvas.find("quake-black"), "the search hides other profiles");
        editor.keyPressed(UiKey.ENTER, false);
        render(editor, canvas);
        click(editor, canvas, "Done");
        assertEquals("minimal", sc.config().activeProfileName());

        // A click on a row selects it too.
        editor = new SettingsEditor(sc);
        editor.render(canvas, 0, 0);
        click(editor, canvas, "Profile: minimal");
        click(editor, canvas, "quake-black");
        click(editor, canvas, "Done");
        assertEquals("quake-black", sc.config().activeProfileName());
    }

    @Test
    void theGroupInputNamesTheCategoryAndTakesRegularExpressions() {
        sc.apply(Config.defaults());
        load("minecraft:particle/flame", 0xFFFF8000);
        load("minecraft:particle/generic_0", 0xFF808080);
        load("minecraft:particle/generic_1", 0xFF808080);
        SettingsEditor editor = new SettingsEditor(sc);
        RecordingCanvas canvas = new RecordingCanvas(533, 300);
        editor.render(canvas, 0, 0);
        click(editor, canvas, "Particles");
        click(editor, canvas, "Groups");
        click(editor, canvas, "+ New group");
        type(editor, canvas, "gen");
        assertNotNull(canvas.find("generic*"), "frames of one particle are one suggestion: " + canvas.texts.keySet());
        for (int i = 0; i < 3; i++) editor.keyPressed(UiKey.BACKSPACE, false);
        type(editor, canvas, "/^fl");
        assertNotNull(canvas.find("/^fl/"), "an open regular expression is offered closed: " + canvas.texts.keySet());
        editor.keyPressed(UiKey.ENTER, false);
        render(editor, canvas);
        type(editor, canvas, "/(");
        assertNotNull(canvas.find("Not a valid regular expression"), "says why: " + canvas.texts.keySet());
        click(editor, canvas, "Done");
        CategorySettings particles = sc.config().activeProfile().get(Category.PARTICLES);
        assertEquals(Collections.singletonList("/^fl/"), particles.rules().get(0).patterns());
        assertNotNull(particles.ruleFor("minecraft:flame"));
    }

    @Test
    void settingsChangesRepaintInsteadOfReloading() {
        FakePlatform p = (FakePlatform) sc.platform();
        sc.apply(Config.defaults());
        p.reset();
        Config c = sc.config();

        // Fullbright and the sky color are read each frame: nothing to repaint.
        RefreshResult r = sc.apply(c.withProfile(c.activeProfile().with(Toggle.FULLBRIGHT, false)));
        assertEquals(RefreshResult.Outcome.UNCHANGED, r.outcome);
        assertEquals(0, p.uploads + p.reloads + p.rebuilds);

        // A new group repaints its blocks only.
        c = sc.config();
        Rule ground = new Rule("Ground", Collections.singletonList("dirt"), Appearance.solid(0xFFFF0000), false);
        CategorySettings blocks = c.activeProfile().get(Category.BLOCKS);
        List<Rule> groups = new ArrayList<Rule>(blocks.rules());
        groups.add(0, ground);
        r = sc.apply(c.withProfile(c.activeProfile().with(Category.BLOCKS, blocks.withRules(groups))));
        assertEquals(RefreshResult.Outcome.REPAINTED, r.outcome);
        assertEquals(1, r.repainted);
        assertEquals(1, p.uploads);

        // Shading and tints are in the chunk meshes: rebuild them, never reload.
        c = sc.config();
        r = sc.apply(c.withProfile(c.activeProfile().with(Toggle.SHADING, false)));
        assertEquals(RefreshResult.Outcome.REBUILT_CHUNKS, r.outcome);
        c = sc.config();
        sc.apply(c.withProfile(c.activeProfile().with(Category.BLOCKS, c.activeProfile().get(Category.BLOCKS).withRemoveTint(false))));
        assertEquals(2, p.rebuilds);

        // Off and on again: all textures repaint.
        p.reset();
        sc.apply(sc.config().withEnabled(false));
        assertEquals(10, p.uploads);
        assertEquals(0, p.reloads);
        sc.apply(Config.defaults());
    }

    @Test
    void aRepaintThatCannotReachTheGpuReloads() {
        FakePlatform p = (FakePlatform) sc.platform();
        sc.apply(Config.defaults());
        p.reset();
        p.flushFails = true;
        try {
            RefreshResult r = sc.apply(sc.config().withEnabled(false));
            assertEquals(RefreshResult.Outcome.RELOADED, r.outcome);
            assertNotNull(r.reason);
            assertEquals(1, p.reloads, "the core reloads, the adapter does not");
        } finally {
            p.flushFails = false;
            sc.apply(Config.defaults());
        }
    }

    @Test
    void anEditInAGroupOfABuiltInProfileKeepsTheGroupOpenAndEscWorks() {
        sc.apply(Config.defaults());
        SettingsEditor editor = new SettingsEditor(sc);
        RecordingCanvas canvas = new RecordingCanvas(533, 300);
        editor.render(canvas, 0, 0);
        click(editor, canvas, "Groups");
        click(editor, canvas, "Leaves");
        click(editor, canvas, "Type a name");
        type(editor, canvas, "stone");
        editor.keyPressed(UiKey.ENTER, false); // the first change makes quake-white-custom
        render(editor, canvas);
        assertNotNull(canvas.find("Leaves"), "the group stays open in the copy: " + canvas.texts.keySet());
        assertNotNull(canvas.find("stone"), "the chip shows: " + canvas.texts.keySet());

        editor.keyPressed(UiKey.ESCAPE, false); // the input ends
        render(editor, canvas);
        editor.keyPressed(UiKey.ESCAPE, false); // back to the groups
        render(editor, canvas);
        editor.keyPressed(UiKey.ESCAPE, false); // the screen closes
        render(editor, canvas);
        assertTrue(editor.isClosed());
        assertEquals("quake-white-custom", sc.config().activeProfileName());
        sc.apply(Config.defaults());
    }

    private static Config saved() throws Exception {
        return ConfigCodec.decode(new String(Files.readAllBytes(dir.resolve("albedo/config.json")), StandardCharsets.UTF_8));
    }

    private static void playOn(String server) {
        ((FakePlatform) sc.platform()).server = server;
        sc.clientTick(false);
    }

    @Test
    void aServerRuleSetsTheProfileOnlyWhileThePlayerIsThere() throws Exception {
        sc.apply(withOwnProfile().withServer(new ServerRule("hypixel.net", "quake-black")));
        try {
            playOn("mc.hypixel.net:25565");
            assertEquals("quake-black", sc.config().activeProfileName());
            assertEquals("test", saved().activeProfileName(), "the file keeps the player's own profile");

            sc.apply(sc.storedConfig().withServer(new ServerRule("example.org", null)));
            assertEquals("quake-black", sc.config().activeProfileName(), "an edit to the file keeps the rule");
            assertEquals("test", saved().activeProfileName());

            playOn(null);
            assertEquals("test", sc.config().activeProfileName());
        } finally {
            playOn(null);
            sc.apply(Config.defaults());
        }
    }

    @Test
    void aProfileChoiceOnAServerLastsOnlyForTheVisit() throws Exception {
        sc.apply(withOwnProfile().withServer(new ServerRule("hypixel.net", "quake-black")));
        try {
            playOn("mc.hypixel.net");
            sc.apply(sc.storedConfig(), new Config.State(true, "minimal")); // F7 and the profile list do this
            assertEquals("minimal", sc.config().activeProfileName());
            sc.toggle();
            assertFalse(sc.config().enabled(), "F6 also changes only the visit");
            assertEquals("test", saved().activeProfileName(), "the file keeps the player's own profile");
            assertTrue(saved().enabled());
            playOn(null);
            assertEquals("test", sc.config().activeProfileName());
            assertTrue(sc.config().enabled());
            playOn("mc.hypixel.net");
            assertEquals("quake-black", sc.config().activeProfileName(), "the rule applies again on the next visit");
        } finally {
            playOn(null);
            sc.apply(Config.defaults());
        }
    }

    @Test
    void aNewRuleForTheCurrentServerAppliesAtOnce() throws Exception {
        sc.apply(withOwnProfile());
        try {
            playOn("example.org");
            sc.apply(sc.storedConfig().withServer(new ServerRule("example.org", null)));
            assertFalse(sc.config().enabled(), "off on this server");
            assertTrue(saved().enabled(), "and on everywhere else");
        } finally {
            playOn(null);
            sc.apply(Config.defaults());
        }
    }

    @Test
    void theServersPageAddsTheCurrentServer() {
        sc.apply(Config.defaults());
        try {
            playOn("mc.hypixel.net");
            SettingsEditor editor = new SettingsEditor(sc);
            RecordingCanvas canvas = new RecordingCanvas(533, 300);
            editor.render(canvas, 0, 0);
            click(editor, canvas, "Servers");
            assertNotNull(canvas.find("This server: mc.hypixel.net"), "shows the server: " + canvas.texts.keySet());
            click(editor, canvas, "Use quake-white here");

            click(editor, canvas, "Server name");
            type(editor, canvas, "example.org");
            editor.keyPressed(UiKey.ENTER, false);
            render(editor, canvas);
            assertNotNull(canvas.find("example.org"), "Enter adds the typed server: " + canvas.texts.keySet());
            click(editor, canvas, "Off");
            click(editor, canvas, "Done");
            assertEquals("quake-white", sc.config().serverRule("mc.hypixel.net").profile());
            assertNull(sc.config().serverRule("example.org").profile(), "Off turns the mod off there");
        } finally {
            playOn(null);
            sc.apply(Config.defaults());
        }
    }

    @Test
    void aSliderDragDoesNotTakeGroupsAlong() {
        Config c = withOwnProfile();
        Appearance look = new Appearance(Appearance.Mode.RECOLOR, 0xFFC0A080, Appearance.Alpha.KEEP, 80, 0, 0);
        Rule own = new Rule("Own", Collections.singletonList("stone"), look.withRecolor(60, 0, 0), false);
        CategorySettings blocks = new CategorySettings(look, false, Collections.singletonList(own));
        sc.apply(c.withProfile(c.activeProfile().with(Category.BLOCKS, blocks)));
        try {
            SettingsEditor editor = new SettingsEditor(sc);
            RecordingCanvas canvas = new RecordingCanvas(533, 300);
            editor.render(canvas, 0, 0);
            assertNotNull(canvas.find("Detail"), "Recolor shows its sliders: " + canvas.texts.keySet());
            int[] value = canvas.find("80%");
            assertNotNull(value, "the Detail value shows: " + canvas.texts.keySet());
            int x = value[0] - 20, y = value[1];
            editor.mousePressed();
            for (int dx = 0; dx <= x; dx += 2) editor.render(canvas, x - dx, y); // through 60 on the way to 0
            editor.mouseReleased();
            render(editor, canvas);
            click(editor, canvas, "Done");
            CategorySettings saved = sc.config().activeProfile().get(Category.BLOCKS);
            assertEquals(0, saved.appearance().detail());
            assertEquals(60, saved.lookOf(saved.rules().get(0)).detail(), "the group keeps its own value");
        } finally {
            sc.apply(Config.defaults());
        }
    }

    @Test
    void theRecolorLookFitsSmallWindows() {
        Config c = withOwnProfile();
        Appearance look = new Appearance(Appearance.Mode.RECOLOR, 0xFFC0A080, Appearance.Alpha.KEEP, 80, 20, 0);
        sc.apply(c.withProfile(c.activeProfile().with(Category.BLOCKS, new CategorySettings(look, false, Collections.<Rule>emptyList()))));
        try {
            for (int[] size : new int[][]{{320, 200}, {320, 240}, {427, 240}}) {
                SettingsEditor editor = new SettingsEditor(sc);
                RecordingCanvas canvas = new RecordingCanvas(size[0], size[1]);
                editor.render(canvas, 0, 0); // the canvas fails the test for anything drawn outside the screen
                assertNotNull(canvas.find("Shape"), "the Shape row shows at " + size[0] + "x" + size[1] + ": " + canvas.texts.keySet());
            }
        } finally {
            sc.apply(Config.defaults());
        }
    }

    @Test
    void everyStyleHasItsOwnLabel() {
        Set<String> labels = new HashSet<String>();
        for (Appearance.Mode m : Appearance.Mode.values()) assertTrue(labels.add(Labels.mode(m)), "two styles show " + Labels.mode(m));
    }

    @Test
    void escClosesTheHeaderModeFirst() {
        sc.apply(withOwnProfile());
        SettingsEditor editor = new SettingsEditor(sc);
        RecordingCanvas canvas = new RecordingCanvas(533, 300);
        editor.render(canvas, 0, 0);

        click(editor, canvas, "Rename");
        type(editor, canvas, "zzz");
        editor.keyPressed(UiKey.ESCAPE, false);
        render(editor, canvas);
        assertFalse(editor.isClosed(), "Esc ends the rename, not the screen");
        assertNotNull(canvas.find("Profile: test"), "the old name stays: " + canvas.texts.keySet());

        click(editor, canvas, "Profile: test");
        assertNotNull(canvas.find("minimal"), "the list is open: " + canvas.texts.keySet());
        editor.keyPressed(UiKey.ESCAPE, false);
        render(editor, canvas);
        assertFalse(editor.isClosed(), "Esc closes the list, not the screen");
        assertNull(canvas.find("minimal"), "the list is closed: " + canvas.texts.keySet());

        editor.keyPressed(UiKey.ESCAPE, false);
        render(editor, canvas);
        assertTrue(editor.isClosed());
        assertEquals("test", sc.config().activeProfileName());
        sc.apply(Config.defaults());
    }

    @Test
    void groupWithTypedBlocks() {
        sc.apply(Config.defaults());
        SettingsEditor editor = new SettingsEditor(sc);
        RecordingCanvas canvas = new RecordingCanvas(533, 300);
        editor.render(canvas, 0, 0);
        click(editor, canvas, "Groups");
        click(editor, canvas, "+ New group");      // opens the group with the block input focused
        type(editor, canvas, "grass");
        assertNotNull(canvas.find("grass_block*"), "whole-block suggestion first: " + canvas.texts.keySet());
        editor.keyPressed(UiKey.ENTER, false);
        render(editor, canvas);
        type(editor, canvas, "dirt");
        editor.keyPressed(UiKey.ENTER, false);
        render(editor, canvas);
        click(editor, canvas, "Done");

        CategorySettings blocks = sc.config().activeProfile().get(Category.BLOCKS);
        Rule g = blocks.rules().get(blocks.rules().size() - 1);
        assertEquals(Arrays.asList("grass_block*", "dirt"), g.patterns());
        assertEquals(Appearance.Mode.SOLID, blocks.appearanceFor("minecraft:grass_block_snow").mode());
        assertNotEquals(blocks.appearance().color(), blocks.appearanceFor("minecraft:dirt").color(), "new groups get their own color");
    }

    @Test
    void scopeSwitchIsRefusedWhenItWouldChangeMeaning() {
        Config c = Config.defaults();
        Rule keep = new Rule("Keep", Collections.singletonList("stone"), Appearance.ORIGINAL, false);
        CategorySettings blocks = c.activeProfile().get(Category.BLOCKS).withRules(Collections.singletonList(keep));
        sc.apply(c.withProfile(c.activeProfile().with(Category.BLOCKS, blocks)));
        SettingsEditor editor = new SettingsEditor(sc);
        RecordingCanvas canvas = new RecordingCanvas(533, 300);
        editor.render(canvas, 0, 0);
        click(editor, canvas, "Groups");
        click(editor, canvas, "Only groups");
        assertNotNull(canvas.find("it first."), "explains why: " + canvas.texts.keySet());
        click(editor, canvas, "Done");
        assertFalse(sc.config().activeProfile().get(Category.BLOCKS).onlyGroups());
    }

    @Test
    void exportThenImportMakesAnEqualProfileUnderAFreeName() {
        sc.apply(Config.defaults());
        FakePlatform platform = (FakePlatform) sc.platform();
        SettingsEditor editor = new SettingsEditor(sc);
        RecordingCanvas canvas = new RecordingCanvas(533, 300);
        editor.render(canvas, 0, 0);
        click(editor, canvas, "Export");
        assertTrue(new File(dir.toFile(), "albedo/profiles/quake-white.json").isFile());
        assertTrue(platform.clipboard.contains("\"albedoProfile\""), platform.clipboard);
        click(editor, canvas, "Import");
        click(editor, canvas, "Done");

        Config c = sc.config();
        assertEquals("quake-white-2", c.activeProfileName());
        assertEquals(c.profiles().get("quake-white").renamed("quake-white-2"), c.activeProfile());

        platform.clipboard = "{\"enabled\": true}";
        editor = new SettingsEditor(sc);
        editor.render(canvas, 0, 0);
        click(editor, canvas, "Import");
        assertNotNull(canvas.find("Cannot import"), "shows the error: " + canvas.texts.keySet());
        assertEquals(c, sc.config());
    }

    private static void type(SettingsEditor editor, RecordingCanvas canvas, String text) {
        for (char ch : text.toCharArray()) editor.charTyped(ch);
        render(editor, canvas);
    }

    private static void render(SettingsEditor editor, RecordingCanvas canvas) {
        canvas.texts.clear();
        editor.render(canvas, 0, 0);
    }

    @Test
    void theFrameOfAStyleChangeAlreadyShowsTheNewLayout() {
        sc.apply(Config.defaults());
        SettingsEditor editor = new SettingsEditor(sc);
        RecordingCanvas canvas = new RecordingCanvas(533, 300);
        editor.render(canvas, 0, 0);
        int[] at = canvas.find("Recolor");
        editor.mousePressed();
        canvas.texts.clear();
        editor.render(canvas, at[0], at[1]);
        editor.mouseReleased();
        assertNotNull(canvas.find("Detail"), "the sliders show in the frame of the click");
        int tintY = canvas.find("Remove biome tint")[1];
        render(editor, canvas);
        assertEquals(tintY, canvas.find("Remove biome tint")[1], "the tint switch does not move after the click");
    }

    /** The user's own profile with a solid look with Chroma, Bevel and Outline for blocks, fluids and players. */
    private static Config withEffects() {
        Config c = withOwnProfile();
        Appearance look = new Appearance(Appearance.Mode.SOLID, 0xFFC0A080, Appearance.Alpha.KEEP, Appearance.DEFAULT_DETAIL,
                Appearance.DEFAULT_KEEP_COLORS, Appearance.DEFAULT_KEEP_LIGHT, 20, 30, Chroma.of(50));
        Profile p = c.activeProfile();
        for (Category category : new Category[]{Category.BLOCKS, Category.FLUIDS, Category.PLAYERS}) {
            p = p.with(category, new CategorySettings(look, false, Collections.<Rule>emptyList()));
        }
        return c.withProfile(p);
    }

    @Test
    void theEffectsSectionOpensAndClosesAndTheRowsBelowMoveInTheSameFrame() {
        sc.apply(withEffects());
        try {
            SettingsEditor editor = new SettingsEditor(sc);
            RecordingCanvas canvas = new RecordingCanvas(640, 360);
            editor.render(canvas, 0, 0);
            assertNull(canvas.texts.get("Bevel"), "the section starts closed: " + canvas.texts.keySet());
            assertNotNull(canvas.texts.get("Chroma 50%, Bevel 20%, Outline 30%"), "the heading names the effects: " + canvas.texts.keySet());
            int closedY = canvas.find("Remove biome tint")[1];

            int[] at = canvas.find("Effects");
            editor.mousePressed();
            canvas.texts.clear();
            editor.render(canvas, at[0], at[1]);
            editor.mouseReleased();
            assertNotNull(canvas.texts.get("Bevel"), "the sliders show in the frame of the click: " + canvas.texts.keySet());
            int openY = canvas.find("Remove biome tint")[1];
            assertTrue(openY > closedY, "the switch moves down under the sliders: " + openY + " <= " + closedY);
            render(editor, canvas);
            assertEquals(openY, canvas.find("Remove biome tint")[1], "the switch does not move after the click");
            assertEquals(at[1], canvas.find("Effects")[1], "the heading stays in its place");

            click(editor, canvas, "Effects");
            assertNull(canvas.texts.get("Bevel"), "a second click closes the section: " + canvas.texts.keySet());
            assertEquals(closedY, canvas.find("Remove biome tint")[1]);
        } finally {
            sc.apply(Config.defaults());
        }
    }

    @Test
    void theEffectsHeadingTakesNoKeysAndStaysOpenOnOtherPages() {
        sc.apply(withEffects());
        try {
            SettingsEditor editor = new SettingsEditor(sc);
            RecordingCanvas canvas = new RecordingCanvas(533, 300);
            editor.render(canvas, 0, 0);
            click(editor, canvas, "Effects");
            for (UiKey key : new UiKey[]{UiKey.ENTER, UiKey.TAB, UiKey.UP, UiKey.DOWN}) {
                editor.keyPressed(key, false);
                render(editor, canvas);
            }
            assertNotNull(canvas.texts.get("Speed"), "keys do not close the section: " + canvas.texts.keySet());
            click(editor, canvas, "Fluids");
            assertNotNull(canvas.texts.get("Speed"), "the section stays open on the next page: " + canvas.texts.keySet());
            click(editor, canvas, "Players");
            assertNotNull(canvas.texts.get("Chroma"), "the section stays open on the page of skins: " + canvas.texts.keySet());
            assertNull(canvas.texts.get("Bevel"), "skins have only Chroma: " + canvas.texts.keySet());
            editor.keyPressed(UiKey.ESCAPE, false);
            render(editor, canvas);
            assertTrue(editor.isClosed(), "Esc still closes the screen");
        } finally {
            sc.apply(Config.defaults());
        }
    }

    @Test
    void theChromaOptionsShowIndentedOnlyWhileChromaIsOn() {
        Config on = withEffects();
        Profile p = on.activeProfile();
        Appearance look = p.get(Category.BLOCKS).appearance();
        Config off = on.withProfile(p.with(Category.BLOCKS, p.get(Category.BLOCKS).withAppearance(look.withEffects(20, 0, Chroma.OFF))));
        sc.apply(off);
        try {
            SettingsEditor editor = new SettingsEditor(sc);
            RecordingCanvas canvas = new RecordingCanvas(960, 540);
            editor.render(canvas, 0, 0);
            click(editor, canvas, "Effects");
            assertNotNull(canvas.texts.get("Chroma"), canvas.texts.keySet().toString());
            assertNull(canvas.texts.get("Speed"), "no options while Chroma is off: " + canvas.texts.keySet());
            int bevelOff = canvas.find("Bevel")[1];

            sc.apply(on);
            editor = new SettingsEditor(sc);
            canvas.texts.clear();
            editor.render(canvas, 0, 0);
            click(editor, canvas, "Effects");
            for (String option : new String[]{"Speed", "Vivid", "Bright"}) {
                assertNotNull(canvas.texts.get(option), option + ": " + canvas.texts.keySet());
                assertTrue(canvas.find(option)[0] - canvas.textWidth(option) / 2 > canvas.find("Chroma")[0] - canvas.textWidth("Chroma") / 2,
                        option + " is indented under Chroma");
            }
            assertTrue(canvas.find("Bevel")[1] > bevelOff, "Bevel moves down under the options");
        } finally {
            sc.apply(Config.defaults());
        }
    }

    @Test
    void theSpeedSliderSaysStillAtZeroAndChangesTheSavedChroma() {
        sc.apply(withEffects());
        try {
            SettingsEditor editor = new SettingsEditor(sc);
            RecordingCanvas canvas = new RecordingCanvas(960, 540);
            editor.render(canvas, 0, 0);
            click(editor, canvas, "Effects");
            script(editor, canvas, "slide 0 effects.chromaSpeed");
            assertNotNull(canvas.texts.get("Still"), "speed 0 shows as Still: " + canvas.texts.keySet());
            script(editor, canvas, "click Apply");
            assertEquals(0, sc.storedConfig().activeProfile().get(Category.BLOCKS).appearance().chroma().speed());
        } finally {
            sc.apply(Config.defaults());
        }
    }

    /** Fails when a text of the page right of {@code left}, which is not in the footer, goes below the top of the footer line. */
    private static void assertAboveFooter(RecordingCanvas canvas, int[] size, int left) {
        int ph = Math.min(size[1] - 2 * (size[1] < 260 ? 4 : 10), 380), bodyBottom = (size[1] - ph) / 2 + ph - 24;
        for (Map.Entry<String, int[]> e : canvas.texts.entrySet()) {
            // An empty label belongs to a button with its own drawing.
            if (e.getKey().isEmpty() || e.getValue()[0] < left) continue;
            int y = e.getValue()[1] - 4;
            assertTrue(y + 9 <= bodyBottom || y >= bodyBottom + 5, "\"" + e.getKey() + "\" is on the footer line at " + size[0] + "x" + size[1]);
        }
    }

    @Test
    void theLookTabFitsAtEverySizeWithTheEffectsClosedAndOpen() {
        int[][] sizes = {{320, 200}, {320, 240}, {427, 240}, {533, 300}, {640, 360}, {960, 540}};
        Map<String, String[]> rows = new LinkedHashMap<String, String[]>();
        rows.put("Blocks", new String[]{"Style", "Color", "Shape", "Effects", "Chroma", "Speed", "Bevel", "Outline", "Remove biome tint"});
        rows.put("Fluids", new String[]{"Style", "Color", "Shape", "Effects", "Chroma", "Bevel", "Outline", "Remove biome tint"});
        rows.put("Players", new String[]{"Style", "Color", "Shape", "Effects", "Chroma"});
        for (int[] size : sizes) {
            for (Map.Entry<String, String[]> page : rows.entrySet()) {
                String where = page.getKey() + " at " + size[0] + "x" + size[1];
                sc.apply(withEffects());
                SettingsEditor editor = new SettingsEditor(sc);
                editor.ui.tipDelay = Integer.MAX_VALUE; // tooltips may cover the footer
                RecordingCanvas canvas = new RecordingCanvas(size[0], size[1]);
                editor.render(canvas, 0, 0);
                click(editor, canvas, page.getKey());
                int left = canvas.find("Style")[0] - canvas.textWidth("Style") / 2;
                assertAboveFooter(canvas, size, left);
                assertNotNull(canvas.texts.get("Shape"), "the Shape row shows on " + where + ": " + canvas.texts.keySet());
                assertNotNull(canvas.texts.get("Effects"), "the closed section shows on " + where + ": " + canvas.texts.keySet());

                click(editor, canvas, "Effects");
                assertAboveFooter(canvas, size, left);
                assertNotNull(canvas.texts.get("Color"), "the color picker still shows on " + where + ": " + canvas.texts.keySet());

                // One wheel step moves one row, so each row shows on the way to the end.
                Set<String> seen = new HashSet<String>(canvas.texts.keySet());
                int[] at = canvas.find("Style");
                for (int i = 0; i < 12; i++) {
                    editor.mouseScrolled(-1);
                    canvas.texts.clear();
                    editor.render(canvas, at[0] + 20, at[1]);
                    assertAboveFooter(canvas, size, left);
                    seen.addAll(canvas.texts.keySet());
                }
                for (String row : page.getValue()) assertTrue(seen.contains(row), row + " shows while scrolling on " + where + ": " + seen);
                String last = page.getValue()[page.getValue().length - 1];
                assertNotNull(canvas.texts.get(last), "the last row shows at the end on " + where + ": " + canvas.texts.keySet());
            }
        }
        sc.apply(Config.defaults());
    }

    @Test
    void choosingAStyleAndDoneSavesIt() {
        sc.apply(Config.defaults());
        SettingsEditor editor = new SettingsEditor(sc);
        RecordingCanvas canvas = new RecordingCanvas(533, 300);
        editor.render(canvas, 0, 0);
        click(editor, canvas, "Average");
        click(editor, canvas, "Done");
        assertTrue(editor.isClosed());
        assertEquals(Appearance.Mode.AVERAGE, sc.config().activeProfile().get(Category.BLOCKS).appearance().mode());
    }

    @Test
    void perTextureOverrideAndFullbright() {
        sc.apply(Config.defaults());
        SettingsEditor editor = new SettingsEditor(sc);
        RecordingCanvas canvas = new RecordingCanvas(533, 300);
        editor.render(canvas, 0, 0);
        click(editor, canvas, "Textures");
        click(editor, canvas, "stone");      // a row: starts a group with it
        click(editor, canvas, "Vanilla");    // the group's look: keep it vanilla
        editor.keyPressed(UiKey.ESCAPE, false); // back to the category
        render(editor, canvas);
        click(editor, canvas, "World");
        click(editor, canvas, "Fullbright");
        click(editor, canvas, "Shading");
        click(editor, canvas, "Foliage");
        click(editor, canvas, "Entity shadows");
        click(editor, canvas, "Done");
        Profile p = sc.config().activeProfile();
        assertTrue(p.get(Category.BLOCKS).appearanceFor("minecraft:stone").isOriginal());
        // quake presets start with fullbright, shading and shadows on and foliage hidden; each click flips one
        assertFalse(p.is(Toggle.FULLBRIGHT));
        assertFalse(p.is(Toggle.SHADING));
        assertTrue(p.is(Toggle.FOLIAGE));
        assertFalse(p.is(Toggle.SHADOWS));
    }

    @Test
    void escapeDiscardsNothingAndClosesCancelDiscards() {
        sc.apply(Config.defaults());
        SettingsEditor editor = new SettingsEditor(sc);
        RecordingCanvas canvas = new RecordingCanvas(427, 240);
        editor.render(canvas, 0, 0);
        click(editor, canvas, "Gray");
        click(editor, canvas, "Discard");
        assertTrue(editor.isClosed());
        assertEquals(Appearance.Mode.SOLID, sc.config().activeProfile().get(Category.BLOCKS).appearance().mode());
    }

    @Test
    void deletingAProfileNeedsASecondClickAndEscCancelsIt() {
        sc.apply(withOwnProfile());
        int before = sc.config().profiles().size();
        SettingsEditor editor = new SettingsEditor(sc);
        RecordingCanvas canvas = new RecordingCanvas(533, 300);
        editor.render(canvas, 0, 0);
        click(editor, canvas, "Delete");
        assertNotNull(canvas.find("Click again"), "says what the next click does: " + canvas.texts.keySet());
        editor.keyPressed(UiKey.ESCAPE, false);
        render(editor, canvas);
        assertFalse(editor.isClosed(), "Esc first cancels the pending delete");
        click(editor, canvas, "Done");
        assertEquals(before, sc.config().profiles().size(), "one click only arms the button");

        editor = new SettingsEditor(sc);
        editor.render(canvas, 0, 0);
        click(editor, canvas, "Delete");
        click(editor, canvas, "Delete");
        click(editor, canvas, "Done");
        assertEquals(before - 1, sc.config().profiles().size());
    }

    @Test
    void deletingAGroupNeedsASecondClick() {
        sc.apply(Config.defaults());
        int before = sc.config().activeProfile().get(Category.BLOCKS).rules().size();
        assertTrue(before > 0, "the default profile has a group");
        SettingsEditor editor = new SettingsEditor(sc);
        RecordingCanvas canvas = new RecordingCanvas(533, 300);
        editor.render(canvas, 0, 0);
        click(editor, canvas, "Groups");
        click(editor, canvas, "x");
        click(editor, canvas, "Groups");     // a click somewhere else disarms it
        click(editor, canvas, "x");
        click(editor, canvas, "Done");
        assertEquals(before, sc.config().activeProfile().get(Category.BLOCKS).rules().size());

        editor = new SettingsEditor(sc);
        editor.render(canvas, 0, 0);
        click(editor, canvas, "Groups");
        click(editor, canvas, "x");
        click(editor, canvas, "x");
        click(editor, canvas, "Done");
        assertEquals(before - 1, sc.config().activeProfile().get(Category.BLOCKS).rules().size());
    }

    @Test
    void renameSavesWithSaveAndCancelKeepsTheName() {
        sc.apply(withOwnProfile());
        String name = sc.config().activeProfileName();
        SettingsEditor editor = new SettingsEditor(sc);
        RecordingCanvas canvas = new RecordingCanvas(533, 300);
        editor.render(canvas, 0, 0);
        click(editor, canvas, "Rename");
        type(editor, canvas, "mine");          // the old name is selected, so typing replaces it
        click(editor, canvas, "Cancel");
        assertNotNull(canvas.find(name), "Cancel keeps the name: " + canvas.texts.keySet());
        click(editor, canvas, "Rename");
        type(editor, canvas, "mine");
        click(editor, canvas, "Save");
        click(editor, canvas, "Done");
        assertEquals("mine", sc.config().activeProfileName());
    }

    @Test
    void smallWindowStillShowsEveryPageEntryAndWholeStyleNames() {
        sc.apply(Config.defaults());
        SettingsEditor editor = new SettingsEditor(sc);
        RecordingCanvas canvas = new RecordingCanvas(320, 200);
        editor.render(canvas, 0, 0);
        for (String label : new String[]{"World", "Weather", "Vanilla", "Average", "Gray", "Hide", "Done", "Apply"}) {
            assertNotNull(canvas.texts.get(label), "drawn whole: " + label + " in " + canvas.texts.keySet());
        }
    }

    @Test
    void tooltipShowsInFullNearThePointer() {
        sc.apply(Config.defaults());
        SettingsEditor editor = new SettingsEditor(sc);
        editor.ui.tipDelay = 0;
        RecordingCanvas canvas = new RecordingCanvas(320, 200);
        editor.render(canvas, 0, 0);
        int[] at = canvas.find("Export");
        canvas.texts.clear();
        editor.render(canvas, at[0], at[1]);
        assertNotNull(canvas.find("Save this profile"), "tooltip: " + canvas.texts.keySet());
        assertNotNull(canvas.find("clipboard"), "wrapped, not cut: " + canvas.texts.keySet());
    }

    @Test
    void theStyleRowHasTheSamePlaceWithAndWithoutGroups() {
        sc.apply(Config.defaults());
        SettingsEditor editor = new SettingsEditor(sc);
        RecordingCanvas canvas = new RecordingCanvas(533, 300);
        editor.render(canvas, 0, 0);
        int blocks = canvas.find("Style")[1];
        click(editor, canvas, "Sky");
        assertNotNull(canvas.find("Look"), "a page without groups shows the Look tab: " + canvas.texts.keySet());
        assertNull(canvas.find("Groups"), "and no Groups tab: " + canvas.texts.keySet());
        assertEquals(blocks, canvas.find("Style")[1]);
        click(editor, canvas, "Weather");
        assertEquals(blocks, canvas.find("Style")[1]);
    }

    @Test
    void theToastDoesNotCoverTheOpenSuggestions() {
        sc.apply(Config.defaults());
        SettingsEditor editor = new SettingsEditor(sc);
        RecordingCanvas canvas = new RecordingCanvas(533, 300);
        editor.render(canvas, 0, 0);
        click(editor, canvas, "Groups");
        click(editor, canvas, "+ New group"); // the first change makes quake-white-custom and says so
        type(editor, canvas, "grass");
        int[] toast = canvas.find("is built in");
        assertNotNull(toast, "the toast shows: " + canvas.texts.keySet());
        int[] first = canvas.find("grass_block");
        assertNotNull(first, "the suggestions show: " + canvas.texts.keySet());
        assertTrue(toast[1] < canvas.find("Style")[1], "the toast moves above the dropdown: " + toast[1]);
    }

    @Test
    void anArmedDeleteTipStaysInThePanelAndOffTheButtons() {
        sc.apply(Config.defaults());
        SettingsEditor editor = new SettingsEditor(sc);
        RecordingCanvas canvas = new RecordingCanvas(533, 300);
        editor.render(canvas, 0, 0);
        click(editor, canvas, "Groups");
        int[] x = canvas.find("x");
        click(editor, canvas, "x");
        String tip = null;
        for (String t : canvas.texts.keySet()) if (t.startsWith("Click again")) tip = t;
        assertNotNull(tip, "the armed button explains the next click: " + canvas.texts.keySet());
        int[] at = canvas.texts.get(tip);
        int right = at[0] + canvas.textWidth(tip) / 2, panelRight = (533 - 513) / 2 + 513;
        assertTrue(right <= panelRight, "the tip stays in the panel: " + right + " > " + panelRight);
        assertTrue(right < x[0] - 3, "the tip does not cover the delete buttons: " + right + " vs " + x[0]);
    }

    @Test
    void clickingEverywhereAtEverySizeNeverFailsOrDrawsOutside() {
        int[][] sizes = {{320, 200}, {320, 240}, {427, 240}, {533, 300}, {640, 360}, {960, 540}};
        for (int[] size : sizes) {
            sc.apply(Config.defaults());
            SettingsEditor editor = new SettingsEditor(sc);
            editor.ui.tipDelay = 0;
            RecordingCanvas canvas = new RecordingCanvas(size[0], size[1]);
            for (int y = 0; y < size[1]; y += 9) {
                for (int x = 0; x < size[0]; x += 13) {
                    if (editor.isClosed()) {
                        editor = new SettingsEditor(sc);
                        editor.ui.tipDelay = 0;
                    }
                    editor.mousePressed();
                    editor.render(canvas, x, y);
                    editor.mouseReleased();
                    editor.charTyped('a');
                    editor.keyPressed(UiKey.BACKSPACE, false);
                    editor.mouseScrolled(-1);
                    editor.render(canvas, x, y);
                    editor.render(canvas, x + 1, y); // a moved pointer shows the tooltip, which must stay on screen
                }
            }
        }
    }

    /** The sky design of the active profile in the saved config. */
    private static SkyDesign savedSky() {
        return sc.config().activeProfile().get(Category.SKY).sky();
    }

    @Test
    void theSkyLayersEditorAddsMovesMasksAndDeletesLayers() {
        sc.apply(withOwnProfile());
        SettingsEditor editor = new SettingsEditor(sc);
        editor.ui.tipDelay = 0;
        RecordingCanvas canvas = new RecordingCanvas(640, 360);
        editor.render(canvas, 0, 0);
        click(editor, canvas, "Sky");
        click(editor, canvas, "Layers");
        assertNotNull(canvas.find("Gradient 1"), "a new custom sky starts from Dusk: " + canvas.texts.keySet());
        click(editor, canvas, "Apply");
        assertEquals(SkyPresets.DUSK, savedSky());
        assertEquals(Appearance.Mode.LAYERS, sc.config().activeProfile().get(Category.SKY).appearance().mode());

        click(editor, canvas, "+ Add layer");
        assertNotNull(canvas.find("Make"), "the add screen: " + canvas.texts.keySet());
        click(editor, canvas, "Blur");
        assertNotNull(canvas.find("Blur 5 of 5"), "the new layer opens: " + canvas.texts.keySet());
        click(editor, canvas, "None  >");
        assertNotNull(canvas.find("Light"), "a mask can use light or dark parts: " + canvas.texts.keySet());
        click(editor, canvas, "Dark");
        click(editor, canvas, "Place");
        assertNotNull(canvas.find("has no place"), "a blur changes the whole sky: " + canvas.texts.keySet());
        editor.keyPressed(UiKey.ESCAPE, false);
        render(editor, canvas);
        assertNotNull(canvas.find("masked by Gradient 1"), "the list names the mask: " + canvas.texts.keySet());
        click(editor, canvas, "Apply");
        SkyDesign d = savedSky();
        assertEquals(5, d.layers().size());
        assertEquals(SkyMask.of(1, SkyMask.Mode.DARK), d.layers().get(4).mask());

        // At the end of the list, the last "^" drawn is on the bottom row: the gradient moves over the glow, still below the blur it masks.
        int[] row = canvas.find("Glow 2");
        for (int i = 0; i < 5; i++) {
            editor.mouseScrolled(-1);
            canvas.texts.clear();
            editor.render(canvas, row[0], row[1]);
        }
        click(editor, canvas, "^");
        click(editor, canvas, "Apply");
        assertEquals(SkyLayer.Kind.GLOW, savedSky().layers().get(0).kind());
        assertEquals(SkyLayer.Kind.GRADIENT, savedSky().layers().get(1).kind());

        click(editor, canvas, "Dusk");
        assertNotEquals(SkyPresets.DUSK, sc.config().activeProfile().get(Category.SKY).sky(), "the first click only asks");
        click(editor, canvas, "Dusk");
        click(editor, canvas, "Apply");
        assertEquals(SkyPresets.DUSK, savedSky(), "a preset replaces the layers after a second click");
    }

    @Test
    void theMotionSectionTurnsTheSkyAndShowsTheOptionsOnlyWhileItTurns() {
        Config c = withOwnProfile();
        sc.apply(c.withProfile(c.activeProfile().with(Category.SKY, new CategorySettings(Appearance.of(Appearance.Mode.LAYERS), false,
                false, Collections.<Rule>emptyList(), SkyPresets.GALAXY.withTurning(SkyTurning.STILL)))));
        SettingsEditor editor = new SettingsEditor(sc);
        RecordingCanvas canvas = new RecordingCanvas(640, 360);
        editor.render(canvas, 0, 0);
        click(editor, canvas, "Sky");
        assertNotNull(canvas.texts.get("Off"), "the closed heading tells that the sky is still: " + canvas.texts.keySet());
        assertNull(canvas.texts.get("Turning"), "the section starts closed");
        int listY = canvas.find("Bloom 5")[1];

        click(editor, canvas, "Motion");
        assertNotNull(canvas.texts.get("Turning"), "the slider shows in the open section: " + canvas.texts.keySet());
        assertNull(canvas.texts.get("Speed"), "a still sky shows no options");
        assertTrue(canvas.find("Bloom 5") == null || canvas.find("Bloom 5")[1] > listY, "the layers move down");

        // A press near the right end of the track turns the sky, and the options show in the frame of the press.
        int[] value = canvas.texts.get("0%");
        editor.mousePressed();
        canvas.texts.clear();
        editor.render(canvas, value[0] - 20, value[1]);
        editor.mouseReleased();
        assertNotNull(canvas.texts.get("Speed"), "the options show while the sky turns: " + canvas.texts.keySet());
        assertNotNull(canvas.texts.get("Follow time"));
        click(editor, canvas, "Reverse");
        click(editor, canvas, "Apply");
        SkyTurning t = savedSky().turning();
        assertTrue(t.amount() > 80, "the sky turns: " + t.amount());
        assertEquals(SkyTurning.Direction.REVERSE, t.direction());
        assertEquals(SkyTurning.Clock.WORLD, t.clock(), "Follow time starts on");
        assertEquals(SkyPresets.GALAXY.layers(), savedSky().layers(), "the turning does not change the layers");

        // A click on the label flips the switch, as on the other switch rows.
        click(editor, canvas, "Follow time");
        click(editor, canvas, "Apply");
        assertEquals(SkyTurning.Clock.REAL, savedSky().turning().clock(), "a click on the label turns Follow time off");
        click(editor, canvas, "Follow time");
        click(editor, canvas, "Apply");
        assertEquals(SkyTurning.Clock.WORLD, savedSky().turning().clock());

        click(editor, canvas, "Nebula");
        click(editor, canvas, "Nebula");
        click(editor, canvas, "Apply");
        assertEquals(SkyPresets.NEBULA.layers(), savedSky().layers());
        assertEquals(t, savedSky().turning(), "a preset keeps the turning");

        // The harness presses the left end of the track by the id of the slider, which stops the sky and hides the options.
        script(editor, canvas, "slide 0 sky.turning");
        assertNull(canvas.texts.get("Speed"), "a still sky shows no options: " + canvas.texts.keySet());
        script(editor, canvas, "click Apply");
        assertEquals(0, savedSky().turning().amount());
        assertEquals(SkyTurning.Direction.REVERSE, savedSky().turning().direction(), "the other options stay");
    }

    /** Runs one harness command: a frame records the labels, the next runs the command and the last shows the result. */
    private static void script(SettingsEditor editor, RecordingCanvas canvas, String command) {
        SettingsEditor.script(command);
        for (int i = 0; i < 3; i++) render(editor, canvas);
    }

    @Test
    void thePreviewTurnsWithTheClock() throws InterruptedException {
        SkyTurning real = new SkyTurning(100, 10, SkyTurning.Clock.REAL, 45, SkyTurning.Direction.NORMAL, 0);
        Config c = withOwnProfile();
        sc.apply(c.withProfile(c.activeProfile().with(Category.SKY, new CategorySettings(Appearance.of(Appearance.Mode.LAYERS), false,
                false, Collections.<Rule>emptyList(), SkyPresets.GALAXY.withTurning(real)))));
        try {
            SettingsEditor editor = new SettingsEditor(sc);
            RecordingCanvas canvas = new RecordingCanvas(960, 540);
            editor.render(canvas, 0, 0);
            click(editor, canvas, "Sky");
            SkyClock.hold(0);
            for (int i = 0; i < 400 && canvas.images.get("sky.preview") == null; i++) {
                Thread.sleep(10);
                render(editor, canvas);
            }
            CanvasImage start = canvas.images.get("sky.preview");
            assertNotNull(start, "the preview is ready");
            render(editor, canvas);
            assertSame(start, canvas.images.get("sky.preview"), "a held clock makes no new image");
            SkyClock.hold(30);
            render(editor, canvas);
            CanvasImage quarter = canvas.images.get("sky.preview");
            assertNotEquals(start.serial(), quarter.serial());
            assertFalse(Arrays.equals(start.argb(), quarter.argb()), "a quarter turn later the preview shows another part of the sky");
        } finally {
            SkyClock.run();
            sc.apply(Config.defaults());
        }
    }

    @Test
    void theSkyLayersEditorFitsAtEverySize() {
        int[][] sizes = {{320, 200}, {320, 240}, {427, 240}, {533, 300}, {640, 360}};
        String[][] paths = {{}, {"+ Add layer"}, {"Gradient 1"}, {"Gradient 1", "Place"}, {"Galaxy"}, {"Motion"}};
        for (int[] size : sizes) {
            for (String[] path : paths) {
                Config c = withOwnProfile();
                Profile p = c.activeProfile().with(Category.SKY, new CategorySettings(Appearance.of(Appearance.Mode.LAYERS), false, false,
                        Collections.<Rule>emptyList(), SkyPresets.GALAXY.withTurning(SkyTurning.STILL.withAmount(50))));
                sc.apply(c.withProfile(p));
                SettingsEditor editor = new SettingsEditor(sc);
                editor.ui.tipDelay = 0;
                RecordingCanvas canvas = new RecordingCanvas(size[0], size[1]);
                editor.render(canvas, 0, 0);
                click(editor, canvas, "Sky");
                for (String label : path) if (canvas.find(label) != null) click(editor, canvas, label);
                int[] style = canvas.find("Style");
                if (style != null) assertAboveFooter(canvas, size, style[0] - 15);
                for (int y = 0; y < size[1]; y += 7) {
                    for (int x = 0; x < size[0]; x += 11) {
                        if (editor.isClosed()) break;
                        editor.mousePressed();
                        editor.render(canvas, x, y);
                        editor.mouseReleased();
                        editor.mouseScrolled(-1);
                        editor.render(canvas, x + 1, y);
                    }
                }
            }
        }
    }

    @Test
    void aRealClickGivesTheLabelPositionAndLeavesThePressToTheGame() {
        sc.apply(Config.defaults());
        SettingsEditor editor = new SettingsEditor(sc);
        RecordingCanvas canvas = new RecordingCanvas(533, 300);
        editor.render(canvas, 0, 0);
        int[] average = canvas.find("Average");
        SettingsEditor.script("realclick Average");
        editor.render(canvas, 0, 0); // records the labels
        editor.render(canvas, 0, 0); // runs the command
        int[] at = SettingsEditor.takeRealClick();
        assertArrayEquals(average, at);
        assertNull(SettingsEditor.takeRealClick(), "the harness takes a real click once");

        // The game sends the press with its own pointer position, which the editor uses from now on.
        editor.mousePressed();
        editor.render(canvas, at[0], at[1]);
        editor.mouseReleased();
        render(editor, canvas);
        click(editor, canvas, "Done");
        assertEquals(Appearance.Mode.AVERAGE, sc.config().activeProfile().get(Category.BLOCKS).appearance().mode());
    }

    @Test
    void theCanvasImageFallbackStaysInsideItsRectangle() {
        RecordingCanvas canvas = new RecordingCanvas(50, 20);
        int[] px = new int[8 * 2];
        java.util.Arrays.fill(px, 0xFF336699);
        canvas.image("t", new CanvasImage(px, 8, 2, 1), 3, 4, 45, 13);
        assertEquals(0, canvas.texts.size());
    }

    private static void click(SettingsEditor editor, RecordingCanvas canvas, String label) {
        int[] at = canvas.find(label);
        assertNotNull(at, "label not drawn: " + label + " (drawn: " + canvas.texts.keySet() + ")");
        editor.mousePressed();
        editor.render(canvas, at[0], at[1]);
        editor.mouseReleased();
        canvas.texts.clear();
        editor.render(canvas, at[0], at[1]);
    }

    static final class RecordingCanvas implements Canvas {
        final int w, h;
        final Map<String, int[]> texts = new LinkedHashMap<String, int[]>();
        /** The last image drawn with each id. */
        final Map<String, CanvasImage> images = new LinkedHashMap<String, CanvasImage>();

        RecordingCanvas(int w, int h) {
            this.w = w;
            this.h = h;
        }

        @Override
        public void image(String id, CanvasImage img, int x, int y, int w, int h) {
            images.put(id, img);
            Canvas.super.image(id, img, x, y, w, h);
        }

        int[] find(String label) {
            for (Map.Entry<String, int[]> e : texts.entrySet()) if (e.getKey().equals(label)) return e.getValue();
            for (Map.Entry<String, int[]> e : texts.entrySet()) if (e.getKey().contains(label)) return e.getValue();
            return null;
        }

        @Override public int width() { return w; }
        @Override public int height() { return h; }

        @Override
        public void fill(int x0, int y0, int x1, int y1, int argb) {
            assertTrue(x0 >= 0 && y0 >= 0 && x1 <= w && y1 <= h, "fill outside the screen: " + x0 + "," + y0 + " " + x1 + "," + y1);
        }

        @Override
        public void text(String text, int x, int y, int argb) {
            assertTrue(x >= 0 && x + textWidth(text) <= w + 1 && y >= 0 && y + 9 <= h, "text outside the screen: " + text);
            texts.put(text, new int[]{x + textWidth(text) / 2, y + 4});
        }

        /** Approximates Minecraft's font: most glyphs 5px + 1 spacing, a few narrow ones. */
        @Override
        public int textWidth(String text) {
            int w = 0;
            for (char c : text.toCharArray()) {
                w += "il!.,:;'|".indexOf(c) >= 0 ? 2 : "It[] ".indexOf(c) >= 0 ? 4 : c == 'f' || c == 'k' ? 5 : 6;
            }
            return w;
        }
    }

    static final class FakePlatform implements Platform {
        private final File dir;

        FakePlatform(File dir) { this.dir = dir; }

        @Override public String describe() { return "test"; }
        @Override public File configDir() { return dir; }
        @Override public void info(String message) {}
        @Override public void warn(String message, Throwable error) {}
        int reloads, rebuilds, uploads;
        boolean flushFails;
        String server;

        @Override public String currentServer() { return server; }

        void reset() { reloads = rebuilds = uploads = 0; }

        @Override public void reloadResources() { reloads++; }
        @Override public boolean finishRepaint() { return !flushFails; }
        @Override public void rebuildChunks() { rebuilds++; }
        @Override public void showMessage(String message) {}
        @Override public boolean isKeyDown(Keys.Key key) { return false; }
        @Override public void openSettings() {}
        String clipboard = "#123456";

        @Override public String clipboard() { return clipboard; }
        @Override public void setClipboard(String text) { clipboard = text; }
        @Override public Harness.Host harnessHost() { return null; }
    }
}
