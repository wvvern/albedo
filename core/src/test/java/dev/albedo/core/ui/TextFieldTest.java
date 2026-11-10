package dev.albedo.core.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TextFieldTest {
    private final SettingsEditorTest.RecordingCanvas canvas = new SettingsEditorTest.RecordingCanvas(200, 40);
    private String clipboard = "";
    private final Ui ui = new Ui(() -> clipboard);
    private final TextField field = new TextField(8, TextField.ANY);

    /** One frame with the field at 10,10, 100 wide, and a click on it when {@code press}. */
    private boolean frame(boolean press) {
        if (press) ui.mousePressed();
        ui.begin(canvas, press ? 20 : -1, press ? 15 : -1);
        boolean changed = ui.textField("f", field, 10, 10, 100, Ui.H, "Name", null);
        ui.end();
        if (press) ui.mouseReleased();
        return changed;
    }

    private void type(String text) {
        for (char c : text.toCharArray()) ui.typed(c);
    }

    @Test
    void aClickFocusesTheFieldAndTypingGoesIn() {
        frame(true);
        type("ab1");
        assertTrue(frame(false));
        assertEquals("ab1", field.text);
        assertEquals(3, field.cursor);
    }

    @Test
    void keysAndTypingInOneFrameApplyInTheirOrder() {
        frame(true);
        type("abc");
        ui.key(UiKey.BACKSPACE);
        ui.key(UiKey.HOME);
        type("x");
        ui.key(UiKey.END);
        type("y");
        frame(false);
        assertEquals("xaby", field.text);
    }

    @Test
    void enterEndsTheInputAndTheTypingAfterItGoesNowhere() {
        frame(true);
        type("ok");
        ui.key(UiKey.ENTER);
        type("no");
        frame(false);
        assertEquals("ok", field.text);
        assertFalse(ui.isFocused("f"));
        assertNull(ui.textCursor(), "no field takes typing");
    }

    @Test
    void theCursorKeysMoveAndDeleteRemovesTheNextCharacter() {
        field.set("abcd");
        frame(true); // a click puts the cursor at the end
        ui.key(UiKey.LEFT);
        ui.key(UiKey.LEFT);
        ui.key(UiKey.DELETE);
        ui.key(UiKey.LEFT);
        ui.key(UiKey.BACKSPACE);
        ui.key(UiKey.RIGHT);
        ui.key(UiKey.RIGHT);
        ui.key(UiKey.RIGHT);
        frame(false);
        assertEquals("bd", field.text);
        assertEquals(2, field.cursor, "Right stops at the end");
    }

    @Test
    void selectAllMakesTheNextCharacterReplaceTheText() {
        field.set("old");
        frame(true);
        ui.key(UiKey.SELECT_ALL);
        type("n");
        frame(false);
        assertEquals("n", field.text);
        assertFalse(field.selectAll);
    }

    @Test
    void selectAllThenBackspaceEmptiesTheField() {
        field.set("old");
        frame(true);
        ui.key(UiKey.SELECT_ALL);
        ui.key(UiKey.BACKSPACE);
        frame(false);
        assertEquals("", field.text);
    }

    @Test
    void pasteInsertsTheClipboardAtTheCursorWithoutControlCharacters() {
        field.set("ad");
        clipboard = "b\tc";
        frame(true);
        ui.key(UiKey.LEFT);
        ui.key(UiKey.PASTE);
        frame(false);
        assertEquals("abcd", field.text);
    }

    @Test
    void typingStopsAtTheMaximumLength() {
        frame(true);
        type("123456789");
        frame(false);
        assertEquals("12345678", field.text);
    }

    @Test
    void theFilterDropsCharactersItDoesNotAccept() {
        TextField digits = new TextField(8, Character::isDigit);
        assertTrue(digits.insert("a1b2"));
        assertEquals("12", digits.text);
        assertFalse(digits.insert("x"), "nothing changes");
    }

    @Test
    void controlCharactersAreNotTyped() {
        frame(true);
        type("a\u0001\u0016\u007fb");
        frame(false);
        assertEquals("ab", field.text);
    }

    @Test
    void theTextCursorIsAtTheEndOfTheTextOfTheFocusedField() {
        frame(false);
        assertNull(ui.textCursor(), "no field has the focus");
        frame(true);
        type("ab");
        frame(false);
        Rect cursor = ui.textCursor();
        assertEquals(10 + 4 + canvas.textWidth("ab"), cursor.x);
        assertTrue(cursor.y >= 10 && cursor.bottom() <= 10 + Ui.H, "inside the field: " + cursor.y);
    }
}
