package dev.albedo.core.ui;

import dev.albedo.core.Albedo;
import dev.albedo.core.Category;
import dev.albedo.core.CoreFixture;
import dev.albedo.core.FakePlatform;
import dev.albedo.core.config.Appearance;
import dev.albedo.core.config.CategorySettings;
import dev.albedo.core.config.Chroma;
import dev.albedo.core.config.Config;
import dev.albedo.core.config.Profile;
import dev.albedo.core.config.Rule;
import dev.albedo.core.config.ServerRule;
import dev.albedo.core.config.Toggle;
import dev.albedo.core.dev.Harness;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.albedo.core.CoreFixture.playOn;
import static dev.albedo.core.CoreFixture.saved;
import static dev.albedo.core.CoreFixture.withOwnProfile;
import static org.junit.jupiter.api.Assertions.*;

/** Drives the real settings screen headlessly: clicks on drawn labels, checks the saved config. */
class SettingsEditorTest {
    private final Albedo albedo = CoreFixture.albedo();
    private final FakePlatform platform = CoreFixture.platform();

    @BeforeEach
    void defaults() {
        CoreFixture.reset();
    }

    private static EditorDriver open() {
        return EditorDriver.open(533, 300);
    }

    @Test
    void aChangeToABuiltInProfileMakesACopyAndSaysSo() {
        EditorDriver d = open();
        assertNotNull(d.find("built-in"), "the header says the profile is built in: " + d.labels());
        d.click("Rename");
        assertNull(d.find("Save"), "a built-in profile cannot be renamed");
        d.click("Average");
        assertNotNull(d.find("is built in"), "says where the change went: " + d.labels());
        d.click("Done");
        assertEquals("quake-white-custom", albedo.config().activeProfileName());
        assertEquals(Appearance.Mode.AVERAGE, albedo.config().activeProfile().get(Category.BLOCKS).appearance().mode());
        assertEquals(Appearance.Mode.SOLID, albedo.config().profiles().get("quake-white").get(Category.BLOCKS).appearance().mode());
    }

    @Test
    void aClickOnTheOnTextTurnsAlbedoOff() throws Exception {
        EditorDriver d = open();
        d.click("On");
        assertNotNull(d.find("Off"), "the switch shows Off: " + d.labels());
        d.click("Done");
        assertFalse(albedo.config().enabled());
        assertFalse(saved().enabled());
    }

    @Test
    void theProfileListFiltersAsYouType() {
        EditorDriver d = open();
        d.click("Profile: quake-white");
        assertNotNull(d.find("quake-black"), "the list shows all profiles: " + d.labels());
        d.type("mini");
        assertNull(d.find("quake-black"), "the search hides other profiles");
        d.key(UiKey.ENTER);
        d.click("Done");
        assertEquals("minimal", albedo.config().activeProfileName());

        // A click on a row selects it too.
        d.reopen();
        d.click("Profile: minimal");
        d.click("quake-black");
        d.click("Done");
        assertEquals("quake-black", albedo.config().activeProfileName());
    }

    @Test
    void theGroupInputNamesTheCategoryAndTakesRegularExpressions() {
        CoreFixture.load("minecraft:particle/flame", 0xFFFF8000);
        CoreFixture.load("minecraft:particle/generic_0", 0xFF808080);
        CoreFixture.load("minecraft:particle/generic_1", 0xFF808080);
        EditorDriver d = open();
        d.click("Particles");
        d.click("Groups");
        d.click("+ New group");
        d.type("gen");
        assertNotNull(d.find("generic*"), "frames of one particle are one suggestion: " + d.labels());
        for (int i = 0; i < 3; i++) d.editor.keyPressed(UiKey.BACKSPACE);
        d.type("/^fl");
        assertNotNull(d.find("/^fl/"), "an open regular expression is offered closed: " + d.labels());
        d.key(UiKey.ENTER);
        d.type("/(");
        assertNotNull(d.find("Not a valid regular expression"), "says why: " + d.labels());
        d.click("Done");
        CategorySettings particles = albedo.config().activeProfile().get(Category.PARTICLES);
        assertEquals(Collections.singletonList("/^fl/"), particles.rules().get(0).patterns());
        assertNotNull(particles.ruleFor("minecraft:flame"));
    }

    @Test
    void anEditInAGroupOfABuiltInProfileKeepsTheGroupOpenAndEscWorks() {
        EditorDriver d = open();
        d.click("Groups");
        d.click("Leaves");
        d.click("Type a name");
        d.type("stone");
        d.key(UiKey.ENTER); // the first change makes quake-white-custom
        assertNotNull(d.find("Leaves"), "the group stays open in the copy: " + d.labels());
        assertNotNull(d.find("stone"), "the chip shows: " + d.labels());

        d.key(UiKey.ESCAPE); // the input ends
        d.key(UiKey.ESCAPE); // back to the groups
        d.key(UiKey.ESCAPE); // the screen closes
        assertTrue(d.editor.isClosed());
        assertEquals("quake-white-custom", albedo.config().activeProfileName());
    }

    @Test
    void theServersPageAddsTheCurrentServer() {
        playOn("mc.hypixel.net");
        EditorDriver d = open();
        d.click("Servers");
        assertNotNull(d.find("This server: mc.hypixel.net"), "shows the server: " + d.labels());
        d.click("Add this server");
        assertNull(d.find("This server: "), "the server is in the list now: " + d.labels());
        assertNotNull(d.canvas.texts.get("this server"), "its row says so: " + d.labels());

        d.click("Server address");
        d.type("example.org");
        d.key(UiKey.ENTER);
        assertNotNull(d.find("example.org"), "Enter adds the typed server: " + d.labels());

        // The profile of the last row: Albedo off, then back on with another profile.
        d.click("quake-white");
        d.click("Albedo off");
        assertNotNull(d.canvas.texts.get("Albedo off"), "the row shows Albedo off: " + d.labels());
        d.click("Albedo off");
        d.click("minimal");
        assertNull(d.canvas.texts.get("Albedo off"), "the list is closed and the row is on again: " + d.labels());
        d.click("Done");
        assertEquals("quake-white", albedo.config().serverRule("mc.hypixel.net").profile());
        assertEquals("minimal", albedo.config().serverRule("example.org").profile(), "a server set to off can use a profile again");
    }

    @Test
    void theProfileListOfAServerTurnsAlbedoOffThere() {
        albedo.apply(Config.defaults().withServer(new ServerRule("example.org", "minimal")));
        EditorDriver d = open();
        d.click("Servers");
        assertNull(d.find("This server"), "no server line in single player: " + d.labels());
        d.click("minimal");
        d.click("Albedo off");
        d.click("Done");
        assertNull(albedo.config().serverRule("example.org").profile());
    }

    @Test
    void aSliderDragDoesNotTakeGroupsAlong() {
        Config c = withOwnProfile();
        Appearance look = new Appearance(Appearance.Mode.RECOLOR, 0xFFC0A080, Appearance.Alpha.KEEP, 80, 0, 0);
        Rule own = new Rule("Own", Collections.singletonList("stone"), look.withRecolor(60, 0, 0), false);
        CategorySettings blocks = new CategorySettings(look, false, Collections.singletonList(own));
        albedo.apply(c.withProfile(c.activeProfile().with(Category.BLOCKS, blocks)));
        EditorDriver d = open();
        assertNotNull(d.find("Detail"), "Recolor shows its sliders: " + d.labels());
        int[] value = d.find("80%");
        assertNotNull(value, "the Detail value shows: " + d.labels());
        int x = value[0] - 20, y = value[1];
        d.editor.mousePressed();
        for (int dx = 0; dx <= x; dx += 2) d.editor.render(d.canvas, x - dx, y); // through 60 on the way to 0
        d.editor.mouseReleased();
        d.render();
        d.click("Done");
        CategorySettings saved = albedo.config().activeProfile().get(Category.BLOCKS);
        assertEquals(0, saved.appearance().detail());
        assertEquals(60, saved.lookOf(saved.rules().get(0)).detail(), "the group keeps its own value");
    }

    @Test
    void theRecolorLookFitsSmallWindows() {
        Config c = withOwnProfile();
        Appearance look = new Appearance(Appearance.Mode.RECOLOR, 0xFFC0A080, Appearance.Alpha.KEEP, 80, 20, 0);
        albedo.apply(c.withProfile(c.activeProfile().with(Category.BLOCKS, new CategorySettings(look, false, Collections.<Rule>emptyList()))));
        for (int[] size : new int[][]{{320, 200}, {320, 240}, {427, 240}}) {
            EditorDriver d = EditorDriver.open(size[0], size[1]); // the canvas fails the test for anything drawn outside the screen
            assertNotNull(d.find("Shape"), "the Shape row shows at " + size[0] + "x" + size[1] + ": " + d.labels());
        }
    }

    @Test
    void escClosesTheHeaderModeFirst() {
        albedo.apply(withOwnProfile());
        EditorDriver d = open();

        d.click("Rename");
        d.type("zzz");
        d.key(UiKey.ESCAPE);
        assertFalse(d.editor.isClosed(), "Esc ends the rename, not the screen");
        assertNotNull(d.find("Profile: test"), "the old name stays: " + d.labels());

        d.click("Profile: test");
        assertNotNull(d.find("minimal"), "the list is open: " + d.labels());
        d.key(UiKey.ESCAPE);
        assertFalse(d.editor.isClosed(), "Esc closes the list, not the screen");
        assertNull(d.find("minimal"), "the list is closed: " + d.labels());

        d.key(UiKey.ESCAPE);
        assertTrue(d.editor.isClosed());
        assertEquals("test", albedo.config().activeProfileName());
    }

    @Test
    void typedNamesAndAWholeBlockSuggestionMakeAGroup() {
        EditorDriver d = open();
        d.click("Groups");
        d.click("+ New group");      // opens the group with the block input focused
        d.type("grass");
        assertNotNull(d.find("grass_block*"), "whole-block suggestion first: " + d.labels());
        d.key(UiKey.ENTER);
        d.type("dirt");
        d.key(UiKey.ENTER);
        d.click("Done");

        CategorySettings blocks = albedo.config().activeProfile().get(Category.BLOCKS);
        Rule g = blocks.rules().get(blocks.rules().size() - 1);
        assertEquals(Arrays.asList("grass_block*", "dirt"), g.patterns());
        assertEquals(Appearance.Mode.SOLID, blocks.lookOf(blocks.ruleFor("minecraft:grass_block_snow")).mode());
        assertNotEquals(blocks.appearance().color(), blocks.lookOf(blocks.ruleFor("minecraft:dirt")).color(), "new groups get their own color");
    }

    @Test
    void scopeSwitchIsRefusedWhenItWouldChangeMeaning() {
        Config c = Config.defaults();
        Rule keep = new Rule("Keep", Collections.singletonList("stone"), Appearance.ORIGINAL, false);
        CategorySettings blocks = c.activeProfile().get(Category.BLOCKS).withRules(Collections.singletonList(keep));
        albedo.apply(c.withProfile(c.activeProfile().with(Category.BLOCKS, blocks)));
        EditorDriver d = open();
        d.click("Groups");
        d.click("Only groups");
        assertNotNull(d.find("it first."), "explains why: " + d.labels());
        d.click("Done");
        assertFalse(albedo.config().activeProfile().get(Category.BLOCKS).onlyGroups());
    }

    @Test
    void exportThenImportMakesAnEqualProfileUnderAFreeName() {
        EditorDriver d = open();
        d.click("Export");
        assertTrue(new File(CoreFixture.dir(), "albedo/profiles/quake-white.json").isFile());
        assertTrue(platform.clipboard.contains("\"albedoProfile\""), platform.clipboard);
        d.click("Import");
        d.click("Done");

        Config c = albedo.config();
        assertEquals("quake-white-2", c.activeProfileName());
        assertEquals(c.profiles().get("quake-white").renamed("quake-white-2"), c.activeProfile());

        platform.clipboard = "{\"enabled\": true}";
        d.reopen();
        d.click("Import");
        assertNotNull(d.find("Cannot import"), "shows the error: " + d.labels());
        assertEquals(c, albedo.config());
    }

    @Test
    void theFrameOfAStyleChangeAlreadyShowsTheNewLayout() {
        EditorDriver d = open();
        int[] at = d.find("Recolor");
        d.pressAt(at[0], at[1]);
        assertNotNull(d.find("Detail"), "the sliders show in the frame of the click");
        int tintY = d.find("Remove biome tint")[1];
        d.render();
        assertEquals(tintY, d.find("Remove biome tint")[1], "the tint switch does not move after the click");
    }

    /** The user's own profile with a solid look with Chroma, Bevel and Outline for blocks, fluids, items and players. */
    private static Config withEffects() {
        Config c = withOwnProfile();
        Appearance look = new Appearance(Appearance.Mode.SOLID, 0xFFC0A080, Appearance.Alpha.KEEP, Appearance.DEFAULT_DETAIL,
                Appearance.DEFAULT_KEEP_COLORS, Appearance.DEFAULT_KEEP_LIGHT, 20, 30, Chroma.of(50));
        Profile p = c.activeProfile();
        for (Category category : new Category[]{Category.BLOCKS, Category.FLUIDS, Category.ITEMS, Category.PLAYERS}) {
            p = p.with(category, new CategorySettings(look, false, Collections.<Rule>emptyList()));
        }
        return c.withProfile(p);
    }

    @Test
    void theEffectsSectionOpensAndClosesAndTheRowsBelowMoveInTheSameFrame() {
        albedo.apply(withEffects());
        EditorDriver d = EditorDriver.open(640, 360);
        assertNull(d.canvas.texts.get("Bevel"), "the section starts closed: " + d.labels());
        assertNotNull(d.canvas.texts.get("Chroma 50%, Bevel 20%, Outline 30%"), "the heading names the effects: " + d.labels());
        int closedY = d.find("Remove biome tint")[1];

        int[] at = d.find("Effects");
        d.pressAt(at[0], at[1]);
        assertNotNull(d.canvas.texts.get("Bevel"), "the sliders show in the frame of the click: " + d.labels());
        int openY = d.find("Remove biome tint")[1];
        assertTrue(openY > closedY, "the switch moves down under the sliders: " + openY + " <= " + closedY);
        d.render();
        assertEquals(openY, d.find("Remove biome tint")[1], "the switch does not move after the click");
        assertEquals(at[1], d.find("Effects")[1], "the heading stays in its place");

        d.click("Effects");
        assertNull(d.canvas.texts.get("Bevel"), "a second click closes the section: " + d.labels());
        assertEquals(closedY, d.find("Remove biome tint")[1]);
    }

    @Test
    void theEffectsHeadingTakesNoKeysAndStaysOpenOnOtherPages() {
        albedo.apply(withEffects());
        EditorDriver d = open();
        d.click("Effects");
        for (UiKey key : new UiKey[]{UiKey.ENTER, UiKey.TAB, UiKey.UP, UiKey.DOWN}) d.key(key);
        assertNotNull(d.canvas.texts.get("Speed"), "keys do not close the section: " + d.labels());
        d.click("Fluids");
        assertNotNull(d.canvas.texts.get("Vivid"), "the section stays open on the next page: " + d.labels());
        d.click("Players");
        assertNull(d.canvas.texts.get("Effects"), "skins have no effects, because their colors cannot move: " + d.labels());
        assertNull(d.canvas.texts.get("Chroma"), d.labels().toString());
        d.key(UiKey.ESCAPE);
        assertTrue(d.editor.isClosed(), "Esc still closes the screen");
    }

    @Test
    void theChromaOptionsShowIndentedOnlyWhileChromaIsOn() {
        Config on = withEffects();
        Profile p = on.activeProfile();
        Appearance look = p.get(Category.BLOCKS).appearance();
        Config off = on.withProfile(p.with(Category.BLOCKS, p.get(Category.BLOCKS).withAppearance(look.withEffects(20, 0, Chroma.OFF))));
        albedo.apply(off);
        EditorDriver d = EditorDriver.open(960, 540);
        d.click("Effects");
        assertNotNull(d.canvas.texts.get("Chroma"), d.labels().toString());
        assertNull(d.canvas.texts.get("Speed"), "no options while Chroma is off: " + d.labels());
        int bevelOff = d.find("Bevel")[1];

        albedo.apply(on);
        d.reopen();
        d.click("Effects");
        RecordingCanvas canvas = d.canvas;
        for (String option : new String[]{"Speed", "Vivid", "Bright"}) {
            assertNotNull(canvas.texts.get(option), option + ": " + d.labels());
            assertTrue(canvas.find(option)[0] - canvas.textWidth(option) / 2 > canvas.find("Chroma")[0] - canvas.textWidth("Chroma") / 2,
                    option + " is indented under Chroma");
        }
        assertTrue(d.find("Bevel")[1] > bevelOff, "Bevel moves down under the options");
    }

    @Test
    void theSpeedSliderSaysStillAtZeroAndChangesTheSavedChroma() {
        albedo.apply(withEffects());
        EditorDriver d = EditorDriver.open(960, 540);
        d.click("Effects");
        d.pressTrackStart("effects.chromaSpeed");
        assertNotNull(d.canvas.texts.get("Still"), "speed 0 shows as Still: " + d.labels());
        d.script("click Apply");
        assertEquals(0, albedo.storedConfig().activeProfile().get(Category.BLOCKS).appearance().chroma().speed());
    }

    @Test
    void theLookTabFitsAtEverySizeWithTheEffectsClosedAndOpen() {
        int[][] sizes = {{320, 200}, {320, 240}, {427, 240}, {533, 300}, {640, 360}, {960, 540}};
        Map<String, String[]> rows = new LinkedHashMap<String, String[]>();
        rows.put("Blocks", new String[]{"Style", "Color", "Shape", "Effects", "Chroma", "Speed", "Edge", "Bevel", "Outline", "Remove biome tint"});
        rows.put("Fluids", new String[]{"Style", "Color", "Shape", "Effects", "Chroma", "Speed", "Edge", "Bevel", "Outline", "Remove biome tint"});
        rows.put("Items", new String[]{"Style", "Color", "Shape", "Effects", "Chroma", "Speed", "Edge", "Outline"});
        for (int[] size : sizes) {
            for (Map.Entry<String, String[]> page : rows.entrySet()) {
                String where = page.getKey() + " at " + size[0] + "x" + size[1];
                albedo.apply(withEffects());
                EditorDriver d = EditorDriver.open(size[0], size[1], Integer.MAX_VALUE); // tooltips may cover the footer
                d.click(page.getKey());
                int left = d.find("Style")[0] - d.canvas.textWidth("Style") / 2;
                d.assertAboveFooter(left);
                assertNotNull(d.canvas.texts.get("Shape"), "the Shape row shows on " + where + ": " + d.labels());
                assertNotNull(d.canvas.texts.get("Effects"), "the closed section shows on " + where + ": " + d.labels());

                d.click("Effects");
                d.assertAboveFooter(left);
                assertNotNull(d.canvas.texts.get("Color"), "the color picker still shows on " + where + ": " + d.labels());

                // One wheel step moves one row, so each row shows on the way to the end.
                Set<String> seen = new HashSet<String>(d.labels());
                int[] at = d.find("Style");
                for (int i = 0; i < 12; i++) {
                    d.scrollAt(at[0] + 20, at[1]);
                    d.assertAboveFooter(left);
                    seen.addAll(d.labels());
                }
                for (String row : page.getValue()) assertTrue(seen.contains(row), row + " shows while scrolling on " + where + ": " + seen);
                for (String option : new String[]{"Bevel", "Edge"}) {
                    boolean row = Arrays.asList(page.getValue()).contains(option);
                    assertEquals(row, seen.contains(option), option + " only where it is a row on " + where);
                }
                String last = page.getValue()[page.getValue().length - 1];
                assertNotNull(d.canvas.texts.get(last), "the last row shows at the end on " + where + ": " + d.labels());
            }
        }
    }

    @Test
    void choosingAStyleAndDoneSavesIt() {
        EditorDriver d = open();
        d.click("Average");
        d.click("Done");
        assertTrue(d.editor.isClosed());
        assertEquals(Appearance.Mode.AVERAGE, albedo.config().activeProfile().get(Category.BLOCKS).appearance().mode());
    }

    @Test
    void theEditorGivesATextCursorOnlyWhileAFieldTakesTyping() {
        EditorDriver d = open();
        assertNull(d.editor.textCursor());
        d.click("Textures");
        d.click("Search textures");
        int[] empty = d.editor.textCursor();
        assertNotNull(empty, "the search field has the focus");
        d.type("oak");
        assertTrue(d.editor.textCursor()[0] > empty[0], "the cursor moves with the text");
        assertNotNull(d.find("oak"));
        d.key(UiKey.ESCAPE);
        assertNull(d.editor.textCursor(), "Esc ends the input");
        assertFalse(d.editor.isClosed(), "the first Esc does not close the screen");
        d.key(UiKey.ESCAPE);
        assertTrue(d.editor.isClosed());
    }

    @Test
    void aClickOnATextureStartsAGroupWithIt() {
        EditorDriver d = open();
        d.click("Textures");
        d.click("stone");      // a row: starts a group with it
        d.click("Vanilla");    // the group's look: keep it vanilla
        d.key(UiKey.ESCAPE);   // back to the category
        d.click("Done");
        CategorySettings blocks = albedo.config().activeProfile().get(Category.BLOCKS);
        assertTrue(blocks.lookOf(blocks.ruleFor("minecraft:stone")).isOriginal());
    }

    @Test
    void eachWorldSwitchFlipsItsToggle() {
        EditorDriver d = open();
        d.click("World");
        for (String label : new String[]{"Fullbright", "Shading", "Foliage", "Entity shadows", "Player armor", "Player cape"}) d.click(label);
        d.click("Done");
        Profile p = albedo.config().activeProfile();
        // quake presets start with fullbright, shading, shadows, armor and capes on and foliage hidden; each click flips one
        assertFalse(p.is(Toggle.FULLBRIGHT));
        assertFalse(p.is(Toggle.SHADING));
        assertTrue(p.is(Toggle.FOLIAGE));
        assertFalse(p.is(Toggle.SHADOWS));
        assertFalse(p.is(Toggle.ARMOR));
        assertFalse(p.is(Toggle.CAPE));
    }

    @Test
    void escapeDiscardsNothingAndClosesCancelDiscards() {
        EditorDriver d = EditorDriver.open(427, 240);
        d.click("Gray");
        d.click("Discard");
        assertTrue(d.editor.isClosed());
        assertEquals(Appearance.Mode.SOLID, albedo.config().activeProfile().get(Category.BLOCKS).appearance().mode());
    }

    @Test
    void deletingAProfileNeedsASecondClickAndEscCancelsIt() {
        albedo.apply(withOwnProfile());
        int before = albedo.config().profiles().size();
        EditorDriver d = open();
        d.click("Delete");
        assertNotNull(d.find("Click again"), "says what the next click does: " + d.labels());
        d.key(UiKey.ESCAPE);
        assertFalse(d.editor.isClosed(), "Esc first cancels the pending delete");
        d.click("Done");
        assertEquals(before, albedo.config().profiles().size(), "one click only arms the button");

        d.reopen();
        d.click("Delete");
        d.click("Delete");
        d.click("Done");
        assertEquals(before - 1, albedo.config().profiles().size());
    }

    @Test
    void deletingAGroupNeedsASecondClick() {
        int before = albedo.config().activeProfile().get(Category.BLOCKS).rules().size();
        assertTrue(before > 0, "the default profile has a group");
        EditorDriver d = open();
        d.click("Groups");
        d.click("x");
        d.click("Groups");     // a click somewhere else disarms it
        d.click("x");
        d.click("Done");
        assertEquals(before, albedo.config().activeProfile().get(Category.BLOCKS).rules().size());

        d.reopen();
        d.click("Groups");
        d.click("x");
        d.click("x");
        d.click("Done");
        assertEquals(before - 1, albedo.config().activeProfile().get(Category.BLOCKS).rules().size());
    }

    @Test
    void renameSavesWithSaveAndCancelKeepsTheName() {
        albedo.apply(withOwnProfile());
        String name = albedo.config().activeProfileName();
        EditorDriver d = open();
        d.click("Rename");
        d.type("mine");          // the old name is selected, so typing replaces it
        d.click("Cancel");
        assertNotNull(d.find(name), "Cancel keeps the name: " + d.labels());
        d.click("Rename");
        d.type("mine");
        d.click("Save");
        d.click("Done");
        assertEquals("mine", albedo.config().activeProfileName());
    }

    @Test
    void smallWindowStillShowsEveryPageEntryAndWholeStyleNames() {
        EditorDriver d = EditorDriver.open(320, 200);
        for (String label : new String[]{"World", "Weather", "Vanilla", "Average", "Gray", "Hide", "Done", "Apply"}) {
            assertNotNull(d.canvas.texts.get(label), "drawn whole: " + label + " in " + d.labels());
        }
    }

    @Test
    void tooltipShowsInFullNearThePointer() {
        EditorDriver d = EditorDriver.open(320, 200, 0);
        int[] at = d.find("Export");
        d.renderAt(at[0], at[1]);
        assertNotNull(d.find("Save this profile"), "tooltip: " + d.labels());
        assertNotNull(d.find("clipboard"), "wrapped, not cut: " + d.labels());
    }

    @Test
    void theStyleRowHasTheSamePlaceWithAndWithoutGroups() {
        EditorDriver d = open();
        int blocks = d.find("Style")[1];
        d.click("Sky");
        assertNotNull(d.find("Look"), "a page without groups shows the Look tab: " + d.labels());
        assertNull(d.find("Groups"), "and no Groups tab: " + d.labels());
        assertEquals(blocks, d.find("Style")[1]);
        d.click("Weather");
        assertEquals(blocks, d.find("Style")[1]);
    }

    @Test
    void theToastDoesNotCoverTheOpenSuggestions() {
        EditorDriver d = open();
        d.click("Groups");
        d.click("+ New group"); // the first change makes quake-white-custom and says so
        d.type("grass");
        int[] toast = d.find("is built in");
        assertNotNull(toast, "the toast shows: " + d.labels());
        int[] first = d.find("grass_block");
        assertNotNull(first, "the suggestions show: " + d.labels());
        assertTrue(toast[1] < d.find("Style")[1], "the toast moves above the dropdown: " + toast[1]);
    }

    @Test
    void anArmedDeleteTipStaysInThePanelAndOffTheButtons() {
        EditorDriver d = open();
        d.click("Groups");
        int[] x = d.find("x");
        d.click("x");
        String tip = null;
        for (String t : d.labels()) if (t.startsWith("Click again")) tip = t;
        assertNotNull(tip, "the armed button explains the next click: " + d.labels());
        int[] at = d.canvas.texts.get(tip);
        int right = at[0] + d.canvas.textWidth(tip) / 2, panelRight = (533 - 513) / 2 + 513;
        assertTrue(right <= panelRight, "the tip stays in the panel: " + right + " > " + panelRight);
        assertTrue(right < x[0] - 3, "the tip does not cover the delete buttons: " + right + " vs " + x[0]);
    }

    @Test
    void clickingEverywhereAtEverySizeNeverFailsOrDrawsOutside() {
        int[][] sizes = {{320, 200}, {320, 240}, {427, 240}, {533, 300}, {640, 360}, {960, 540}};
        for (int[] size : sizes) {
            albedo.apply(Config.defaults());
            EditorDriver d = EditorDriver.open(size[0], size[1], 0);
            for (int y = 0; y < size[1]; y += 9) {
                for (int x = 0; x < size[0]; x += 13) {
                    if (d.editor.isClosed()) d.reopen();
                    SettingsEditor editor = d.editor;
                    editor.mousePressed();
                    editor.render(d.canvas, x, y);
                    editor.mouseReleased();
                    editor.charTyped('a');
                    editor.keyPressed(UiKey.BACKSPACE);
                    editor.mouseScrolled(-1);
                    editor.render(d.canvas, x, y);
                    editor.render(d.canvas, x + 1, y); // a moved pointer shows the tooltip, which must stay on screen
                }
            }
        }
    }

    @Test
    void aScriptedClickOnALabelThatIsNotDrawnFailsTheRun() throws Exception {
        File harness = new File(CoreFixture.dir(), "harness");
        System.setProperty("albedo.harness", harness.getPath());
        try {
            Harness.fromSystemProperty(albedo.platform());
        } finally {
            System.clearProperty("albedo.harness");
        }
        EditorDriver d = open();
        d.script("click No such label");
        d.script("realclick No such label");
        d.script("click Average");
        List<String> log = Files.readAllLines(new File(harness, "log.txt").toPath(), StandardCharsets.UTF_8);
        assertEquals(Arrays.asList("! no such control: No such label", "! no such control: No such label"), log,
                "a drawn label logs nothing");
        assertNull(SettingsEditor.takeRealClick());
    }

    @Test
    void aRealClickGivesTheLabelPositionAndLeavesThePressToTheGame() {
        EditorDriver d = open();
        int[] average = d.find("Average");
        SettingsEditor.script("realclick Average");
        d.render(); // records the labels
        d.render(); // runs the command
        int[] at = SettingsEditor.takeRealClick();
        assertArrayEquals(average, at);
        assertNull(SettingsEditor.takeRealClick(), "the harness takes a real click once");

        // The game sends the press with its own pointer position, which the editor uses from now on.
        d.pressAt(at[0], at[1]);
        d.render();
        d.click("Done");
        assertEquals(Appearance.Mode.AVERAGE, albedo.config().activeProfile().get(Category.BLOCKS).appearance().mode());
    }
}
