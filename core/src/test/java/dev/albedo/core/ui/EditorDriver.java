package dev.albedo.core.ui;

import dev.albedo.core.CoreFixture;

import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Drives a settings screen on a recording canvas the way a player does: each input is followed by the frame that shows it. */
final class EditorDriver {
    final RecordingCanvas canvas;
    /** The tooltip delay of each new screen, or null for the default. */
    private final Integer tipDelay;
    SettingsEditor editor;

    private EditorDriver(int w, int h, Integer tipDelay) {
        this.canvas = new RecordingCanvas(w, h);
        this.tipDelay = tipDelay;
        reopen();
    }

    /** Opens the screen on a w by h canvas and draws its first frame. */
    static EditorDriver open(int w, int h) {
        return new EditorDriver(w, h, null);
    }

    /** Like {@link #open(int, int)}, with tooltips after {@code tipDelay}. */
    static EditorDriver open(int w, int h, int tipDelay) {
        return new EditorDriver(w, h, tipDelay);
    }

    /** Opens a new screen on the same canvas and draws its first frame. */
    void reopen() {
        editor = new SettingsEditor(CoreFixture.albedo());
        if (tipDelay != null) editor.ui.tipDelay = tipDelay;
        render();
    }

    /** Draws a frame with the pointer at 0,0. */
    void render() {
        renderAt(0, 0);
    }

    /** Draws a frame with the pointer at x,y; the canvas keeps only the texts of this frame. */
    void renderAt(int x, int y) {
        canvas.texts.clear();
        editor.render(canvas, x, y);
    }

    int[] find(String label) {
        return canvas.find(label);
    }

    /** The drawn texts, for assert messages. */
    Set<String> labels() {
        return canvas.texts.keySet();
    }

    /** Presses on the drawn label, releases, and draws the frame after the release with the pointer still there. */
    void click(String label) {
        int[] at = find(label);
        assertNotNull(at, "label not drawn: " + label + " (drawn: " + labels() + ")");
        editor.mousePressed();
        editor.render(canvas, at[0], at[1]);
        editor.mouseReleased();
        renderAt(at[0], at[1]);
    }

    /** Presses at x,y and releases after one frame, so that the canvas holds the frame of the press. */
    void pressAt(int x, int y) {
        editor.mousePressed();
        renderAt(x, y);
        editor.mouseReleased();
    }

    /** Presses the left end of the track of the slider with this widget id and releases it. */
    void pressTrackStart(String id) {
        Rect track = editor.ui.dragAreas().get(id);
        assertNotNull(track, "slider not drawn: " + id);
        int y = track.y + track.h / 2;
        editor.mousePressed();
        editor.render(canvas, track.x, y);
        editor.mouseReleased();
        renderAt(track.x, y);
    }

    /** Turns the wheel one step down with the pointer at x,y. */
    void scrollAt(int x, int y) {
        editor.mouseScrolled(-1);
        renderAt(x, y);
    }

    void type(String text) {
        for (char ch : text.toCharArray()) editor.charTyped(ch);
        render();
    }

    void key(UiKey key) {
        editor.keyPressed(key);
        render();
    }

    /** Fails when a text of the page right of {@code left}, which is not in the footer, goes below the top of the footer line. */
    void assertAboveFooter(int left) {
        int w = canvas.w, h = canvas.h;
        int ph = Math.min(h - 2 * (h < 260 ? 4 : 10), 380), bodyBottom = (h - ph) / 2 + ph - 24;
        for (Map.Entry<String, int[]> e : canvas.texts.entrySet()) {
            // An empty label belongs to a button with its own drawing.
            if (e.getKey().isEmpty() || e.getValue()[0] < left) continue;
            int y = e.getValue()[1] - 4;
            assertTrue(y + 9 <= bodyBottom || y >= bodyBottom + 5, "\"" + e.getKey() + "\" is on the footer line at " + w + "x" + h);
        }
    }

    /** Runs one harness command: a frame records the labels, the next runs the command and the last shows the result. */
    void script(String command) {
        SettingsEditor.script(command);
        for (int i = 0; i < 3; i++) render();
    }

    /** Renders until the image with this id passes {@code test}, and returns it, or null after ten seconds. */
    CanvasImage awaitImage(String id, Predicate<CanvasImage> test) throws InterruptedException {
        for (int i = 0; i < 1000; i++) {
            CanvasImage shown = canvas.images.get(id);
            if (shown != null && test.test(shown)) return shown;
            Thread.sleep(10);
            render();
        }
        return null;
    }
}
