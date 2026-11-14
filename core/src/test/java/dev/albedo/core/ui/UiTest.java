package dev.albedo.core.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiTest {
    private final RecordingCanvas canvas = new RecordingCanvas(200, 40);
    private final Ui ui = new Ui(() -> "");
    private final TextField field = new TextField(8, TextField.ANY);

    @Test
    void anOverlayHidesThePointerOnlyFromTheWidgetsDrawnBeforeIt() {
        Rect area = new Rect(0, 0, 100, 40);
        ui.begin(canvas, 20, 15);
        assertTrue(ui.hover(10, 10, 50, 20));
        ui.reserveOverlay(area);
        assertFalse(ui.hover(10, 10, 50, 20), "reserved");
        assertTrue(ui.pointerIn(10, 10, 50, 20));
        ui.beginOverlay();
        assertTrue(ui.hover(10, 10, 50, 20), "the overlay itself");
        ui.endOverlay();
        assertTrue(ui.hover(10, 10, 50, 20), "after the overlay");
        assertSame(area, ui.overlayDrawn(), "the area stays known after the overlay ends");
        ui.end();
        assertSame(area, ui.overlayDrawn(), "until the next frame");
        ui.begin(canvas, 20, 15);
        assertNull(ui.overlayDrawn());
        ui.end();
    }

    @Test
    void endingAFrameWithNoOverlayGivesNoArea() {
        ui.begin(canvas, 20, 15);
        ui.endOverlay();
        ui.beginOverlay();
        assertNull(ui.overlayDrawn());
        ui.end();
        assertNull(ui.overlayDrawn());
    }

    @Test
    void aFocusThatNoTextFieldDrawsInAFrameIsDropped() {
        ui.begin(canvas, -1, -1);
        ui.focus("f");
        ui.end();
        assertTrue(ui.isFocused("f"), "a focus set in the frame stays for the next frame");
        ui.begin(canvas, -1, -1);
        ui.textField("f", field, 10, 10, 100, Ui.H, "Name", null);
        ui.end();
        assertTrue(ui.isFocused("f"), "the field drew");
        ui.begin(canvas, -1, -1);
        ui.end();
        assertFalse(ui.isFocused("f"));
        assertNull(ui.textCursor());
    }
}
