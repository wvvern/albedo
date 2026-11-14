package dev.albedo.core.ui;

import java.util.HashMap;
import java.util.Map;

/**
 * Dev harness input for the settings screen, one command per frame: click a label, click it through the game's mouse
 * handler, turn the wheel over a label, compare the text of a field, or move the
 * pointer off; keys and typing go through the game's own keyboard path instead (see {@code Harness}).
 */
final class ScriptedInput {
    /** The harness, or null in a game with no harness. */
    private final ScriptPort port;
    /** The center of each label that the last frame drew, where a click command finds its label, or null before the first frame. */
    private Map<String, int[]> lastTexts;
    /** The text of each text field of the last recorded frame by widget id, which an expect command compares. */
    private Map<String, String> lastFields;
    /** The scripted pointer position, or null while the real pointer is used. */
    private int[] pointer;
    private Recorder recorder;
    private boolean release;

    ScriptedInput(ScriptPort port) {
        this.port = port;
    }

    boolean active() {
        return port != null && port.waiting() || pointer != null;
    }

    /** Runs the next command and returns the canvas to draw this frame on. */
    Canvas beginFrame(Canvas canvas, Ui ui) {
        release = lastTexts != null && port != null && run(port.poll(), ui);
        recorder = new Recorder(canvas);
        return recorder;
    }

    int x(int mouseX) { return pointer == null ? mouseX : pointer[0]; }

    int y(int mouseY) { return pointer == null ? mouseY : pointer[1]; }

    /** A scripted click is one frame long: the button goes up after the frame that took it. */
    void endFrame(Ui ui) {
        if (release) ui.mouseReleased();
        // Frames that are not recorded can move the labels, so the next command waits for a recorded frame.
        lastTexts = active() ? recorder.texts : null;
        lastFields = active() ? ui.fieldTexts() : null;
    }

    /** Returns true when the left button must be released after this frame. */
    private boolean run(String command, Ui ui) {
        if (command == null) return false;
        int space = command.indexOf(' ');
        String verb = space < 0 ? command : command.substring(0, space);
        String arg = space < 0 ? "" : command.substring(space + 1);
        switch (verb) {
            case "click": {
                int[] at = find(arg);
                if (at == null) return false;
                pointer = at;
                ui.mousePressed();
                return true;
            }
            case "realclick": {
                // The game moves the real pointer and sends the press, so that the adapter's input path runs too.
                int[] at = find(arg);
                if (at == null) return false;
                pointer = null;
                port.realClick(at);
                return false;
            }
            case "scroll": {
                // "scroll -3 label": the wheel turns three steps down over the label.
                int split = arg.indexOf(' ');
                int[] at = split < 0 ? null : find(arg.substring(split + 1));
                if (at == null) return false;
                pointer = at;
                ui.scrolled(Integer.parseInt(arg.substring(0, split)));
                return false;
            }
            case "expect": {
                // "expect search oak log": the text field with the id search shows "oak log".
                int split = arg.indexOf(' ');
                String id = split < 0 ? arg : arg.substring(0, split), want = split < 0 ? "" : arg.substring(split + 1);
                String got = lastFields.get(id);
                if (want.equals(got)) port.log("PASS " + id + " = \"" + got + "\"");
                else port.fail(id + " = " + (got == null ? "not drawn" : "\"" + got + "\"") + ", expected \"" + want + "\"");
                return false;
            }
            case "pointer":
                pointer = new int[]{-1, -1};
                return false;
            default:
                return false;
        }
    }

    /**
     * The center of the label drawn as {@code text}, or else of a label that contains it, or null after a failed harness
     * line, which fails a script that names an old label.
     */
    private int[] find(String text) {
        int[] at = lastTexts.get(text);
        if (at == null) {
            for (Map.Entry<String, int[]> e : lastTexts.entrySet()) if (e.getKey().contains(text)) at = e.getValue();
        }
        if (at == null) port.fail("no such control: " + text);
        return at;
    }

    /** Passes drawing through and keeps where each text was drawn. */
    private static final class Recorder implements Canvas {
        final Canvas c;
        final Map<String, int[]> texts = new HashMap<String, int[]>();

        Recorder(Canvas c) { this.c = c; }

        @Override public int width() { return c.width(); }

        @Override public int height() { return c.height(); }

        @Override public void fill(int x0, int y0, int x1, int y1, int argb) { c.fill(x0, y0, x1, y1, argb); }

        @Override public int textWidth(String text) { return c.textWidth(text); }

        @Override public void image(String id, CanvasImage img, int x, int y, int w, int h) { c.image(id, img, x, y, w, h); }

        @Override
        public void text(String text, int x, int y, int argb) {
            c.text(text, x, y, argb);
            if (!texts.containsKey(text)) texts.put(text, new int[]{x + c.textWidth(text) / 2, y + 4});
        }
    }
}
