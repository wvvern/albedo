package dev.blockpainter.core.ui;

import dev.blockpainter.core.Category;
import dev.blockpainter.core.Keys;
import dev.blockpainter.core.PixelBuffer;
import dev.blockpainter.core.Platform;
import dev.blockpainter.core.BlockPainter;
import dev.blockpainter.core.config.Appearance;
import dev.blockpainter.core.config.Config;
import dev.blockpainter.core.config.CategorySettings;
import dev.blockpainter.core.config.Profile;
import dev.blockpainter.core.config.Rule;
import dev.blockpainter.core.config.Toggle;
import dev.blockpainter.core.dev.Harness;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Drives the real settings screen headlessly: clicks on drawn labels, checks the saved config. */
class SettingsEditorTest {
    @TempDir
    static Path dir;
    static BlockPainter sc;

    @BeforeAll
    static void init() {
        sc = BlockPainter.init(new FakePlatform(dir.toFile()));
        BlockPainter.processTexture("minecraft:block/oak_leaves", new PixelBuffer.Heap(1, 1, new int[]{0xFF2E7D32}));
        for (String id : new String[]{"stone", "dirt", "gravel", "grass_block_top", "grass_block_side", "grass_block_snow"}) {
            BlockPainter.processTexture("minecraft:block/" + id, new PixelBuffer.Heap(1, 1, new int[]{0xFF7F7F7F}));
        }
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
        assertNotNull(canvas.find("Change it first"), "explains why: " + canvas.texts.keySet());
        click(editor, canvas, "Done");
        assertFalse(sc.config().activeProfile().get(Category.BLOCKS).onlyGroups());
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
        click(editor, canvas, "Textures");   // the texture browser tab
        click(editor, canvas, "stone");      // a row: starts a group with it
        click(editor, canvas, "Vanilla");    // the group's look: keep it vanilla
        editor.keyPressed(UiKey.ESCAPE, false); // back to the category
        render(editor, canvas);
        click(editor, canvas, "World");      // the world page
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
    void clickingEverywhereAtEverySizeNeverFailsOrDrawsOutside() {
        int[][] sizes = {{320, 240}, {427, 240}, {533, 300}, {640, 360}, {960, 540}};
        for (int[] size : sizes) {
            sc.apply(Config.defaults());
            SettingsEditor editor = new SettingsEditor(sc);
            RecordingCanvas canvas = new RecordingCanvas(size[0], size[1]);
            for (int y = 0; y < size[1]; y += 9) {
                for (int x = 0; x < size[0]; x += 13) {
                    if (editor.isClosed()) editor = new SettingsEditor(sc);
                    editor.mousePressed(x, y, 0);
                    editor.render(canvas, x, y);
                    editor.mouseReleased(x, y, 0);
                    editor.charTyped('a');
                    editor.keyPressed(UiKey.BACKSPACE, false);
                    editor.mouseScrolled(-1);
                    editor.render(canvas, x, y);
                }
            }
        }
    }

    private static void click(SettingsEditor editor, RecordingCanvas canvas, String label) {
        int[] at = canvas.find(label);
        assertNotNull(at, "label not drawn: " + label + " (drawn: " + canvas.texts.keySet() + ")");
        editor.mousePressed(at[0], at[1], 0);
        editor.render(canvas, at[0], at[1]);
        editor.mouseReleased(at[0], at[1], 0);
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
        @Override public void reloadResources() {}
        @Override public void showMessage(String message) {}
        @Override public boolean isKeyDown(Keys.Key key) { return false; }
        @Override public PixelBuffer readImage(File png) { return null; }
        @Override public void openSettings() {}
        @Override public String clipboard() { return "#123456"; }
        @Override public Harness.Host harnessHost() { return null; }
    }
}
