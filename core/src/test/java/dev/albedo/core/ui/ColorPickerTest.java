package dev.albedo.core.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ColorPickerTest {
    /** The picker's top-left corner, with room around it for the knob of the square. */
    private static final int X = 10, Y = 10;
    private final SettingsEditorTest.RecordingCanvas canvas = new SettingsEditorTest.RecordingCanvas(220, 70);
    private final Ui ui = new Ui(() -> "");
    private final ColorPicker picker = new ColorPicker(ui);

    /** One frame of the full picker, 200 wide with a 44 high square, with the pointer at x,y and a click when {@code press}. */
    private int frame(int color, int x, int y, boolean press) {
        if (press) ui.mousePressed();
        canvas.texts.clear();
        ui.begin(canvas, X + x, Y + y);
        int next = picker.draw("look", X, Y, 200, 44, color);
        ui.end();
        if (press) ui.mouseReleased();
        return next;
    }

    private int compactFrame(int color) {
        canvas.texts.clear();
        ui.begin(canvas, -1, -1);
        int next = picker.compact("look", X, Y, 200, color);
        ui.end();
        return next;
    }

    @Test
    void aClickOnAPaletteColorReturnsItWithTheOldOpacity() {
        // The square is 66 wide, so the palette starts at x 96; the ninth color starts the second row.
        assertEquals(0x80E53935, frame(0x80FFFFFF, 100, 37, true));
    }

    @Test
    void aClickInTheSquareReturnsThatSaturationAndBrightness() {
        assertEquals(0xFFFFFFFF, frame(0xFF0000FF, 0, 0, true), "the top-left corner is white");
        assertEquals(0xFF000000, frame(0xFF0000FF, 30, 43, true), "the bottom row is black");
    }

    @Test
    void theCompactPickerKeepsTheTextTypedInTheFullOne() {
        frame(0xFFFFFFFF, 130, 8, true); // the hex input
        for (int i = 0; i < 9; i++) ui.key(UiKey.BACKSPACE, false);
        for (char c : "#12".toCharArray()) ui.typed(c);
        assertEquals(0xFFFFFFFF, frame(0xFFFFFFFF, 130, 8, false), "an incomplete color changes nothing");
        compactFrame(0xFFFFFFFF);
        assertNotNull(canvas.find("#12"), "the typed text stays: " + canvas.texts.keySet());
        ui.typed('3');
        assertEquals(0xFF112233, compactFrame(0xFFFFFFFF));
    }
}
