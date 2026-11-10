package dev.albedo.core.ui;

import dev.albedo.core.dev.Harness;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Dev harness input for the settings screen, one command per frame: click a label, click it through the game's mouse
 * handler, press a slider at a percent of its track, turn the wheel over a label, compare the text of a field, or move the
 * pointer off; keys and typing go through the game's own keyboard path instead (see {@code Harness}).
 */
final class ScriptedInput {
    private static final Queue<String> QUEUE = new ConcurrentLinkedQueue<String>();
    /** The label position of the last {@code realclick}, which the harness takes and clicks through the game's mouse handler. */
    private static final AtomicReference<int[]> REAL_CLICK = new AtomicReference<int[]>();

    /** The center of each label that the last frame drew, where a click command finds its label, or null before the first frame. */
    private Map<String, int[]> lastTexts;
    /** The drag areas of the last recorded frame by widget id, where a slide command finds its slider. */
    private Map<String, Rect> lastAreas;
    /** The text of each text field of the last recorded frame by widget id, which an expect command compares. */
    private Map<String, String> lastFields;
    /** The scripted pointer position, or null while the real pointer is used. */
    private int[] pointer;
    private Recorder recorder;
    private boolean release;

    static void add(String command) {
        QUEUE.add(command);
    }

    static int[] takeRealClick() {
        return REAL_CLICK.getAndSet(null);
    }

    boolean active() {
        return !QUEUE.isEmpty() || pointer != null;
    }

    /** Runs the next command and returns the canvas to draw this frame on. */
    Canvas beginFrame(Canvas canvas, Ui ui) {
        release = lastTexts != null && run(QUEUE.poll(), ui);
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
        lastAreas = active() ? ui.dragAreas() : null;
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
                REAL_CLICK.set(at);
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
            case "slide": {
                // "slide 0 sky.turning": a press at 0 percent of the track of the slider with the widget id sky.turning.
                int split = arg.indexOf(' ');
                Rect area = split < 0 ? null : lastAreas.get(arg.substring(split + 1));
                if (area == null) {
                    Harness.log(noSuchControl(arg.substring(split + 1)));
                    return false;
                }
                float at = Integer.parseInt(arg.substring(0, split)) / 100f;
                pointer = new int[]{area.x + Math.round(at * (area.w - 1)), area.y + area.h / 2};
                ui.mousePressed();
                return true;
            }
            case "expect": {
                // "expect search oak log": the text field with the id search shows "oak log".
                int split = arg.indexOf(' ');
                String id = split < 0 ? arg : arg.substring(0, split), want = split < 0 ? "" : arg.substring(split + 1);
                String got = lastFields.get(id);
                if (want.equals(got)) Harness.log("PASS " + id + " = \"" + got + "\"");
                else Harness.log("! " + id + " = " + (got == null ? "not drawn" : "\"" + got + "\"") + ", expected \"" + want + "\"");
                return false;
            }
            case "pointer":
                pointer = new int[]{-1, -1};
                return false;
            default:
                return false;
        }
    }

    /** The center of the label drawn as {@code text}, or else of a label that contains it, or null after a failed harness line. */
    private int[] find(String text) {
        int[] at = lastTexts.get(text);
        if (at == null) {
            for (Map.Entry<String, int[]> e : lastTexts.entrySet()) if (e.getKey().contains(text)) at = e.getValue();
        }
        if (at == null) Harness.log(noSuchControl(text));
        return at;
    }

    /** The harness line for a command whose control the last frame did not draw, which fails a script that names an old label. */
    static String noSuchControl(String name) {
        return "! no such control: " + name;
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
