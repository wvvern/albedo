package dev.albedo.core.ui;

import dev.albedo.core.Albedo;
import dev.albedo.core.Category;
import dev.albedo.core.Keys;
import dev.albedo.core.PixelBuffer;
import dev.albedo.core.Platform;
import dev.albedo.core.RefreshResult;
import dev.albedo.core.config.Appearance;
import dev.albedo.core.config.CategorySettings;
import dev.albedo.core.config.Config;
import dev.albedo.core.config.ConfigCodec;
import dev.albedo.core.config.Profile;
import dev.albedo.core.config.Rule;
import dev.albedo.core.config.ServerRule;
import dev.albedo.core.config.Toggle;
import dev.albedo.core.dev.Harness;
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
        Appearance look = new Appearance(Appearance.Mode.RECOLOR, 0xFFC0A080, null, Appearance.Alpha.KEEP, 80, 0, 0);
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
            editor.mousePressed(0);
            for (int dx = 0; dx <= x; dx += 2) editor.render(canvas, x - dx, y); // through 60 on the way to 0
            editor.mouseReleased(0);
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
        Appearance look = new Appearance(Appearance.Mode.RECOLOR, 0xFFC0A080, null, Appearance.Alpha.KEEP, 80, 20, 0);
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
        for (String label : new String[]{"World", "Weather", "Vanilla", "Average", "Image", "Hide", "Done", "Apply"}) {
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
                    editor.mousePressed(0);
                    editor.render(canvas, x, y);
                    editor.mouseReleased(0);
                    editor.charTyped('a');
                    editor.keyPressed(UiKey.BACKSPACE, false);
                    editor.mouseScrolled(-1);
                    editor.render(canvas, x, y);
                    editor.render(canvas, x + 1, y); // a moved pointer shows the tooltip, which must stay on screen
                }
            }
        }
    }

    private static void click(SettingsEditor editor, RecordingCanvas canvas, String label) {
        int[] at = canvas.find(label);
        assertNotNull(at, "label not drawn: " + label + " (drawn: " + canvas.texts.keySet() + ")");
        editor.mousePressed(0);
        editor.render(canvas, at[0], at[1]);
        editor.mouseReleased(0);
        canvas.texts.clear();
        editor.render(canvas, at[0], at[1]);
    }

    static final class RecordingCanvas implements Canvas {
        final int w, h;
        final Map<String, int[]> texts = new LinkedHashMap<String, int[]>();

        RecordingCanvas(int w, int h) {
            this.w = w;
            this.h = h;
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
        @Override public PixelBuffer readImage(File png) { return null; }
        @Override public void openSettings() {}
        String clipboard = "#123456";

        @Override public String clipboard() { return clipboard; }
        @Override public void setClipboard(String text) { clipboard = text; }
        @Override public Harness.Host harnessHost() { return null; }
    }
}
