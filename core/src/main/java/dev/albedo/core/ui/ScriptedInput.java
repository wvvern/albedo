package dev.albedo.core.ui;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Dev harness input for the settings screen, one command per frame: click a label, type text, press a key, or move the pointer off. */
final class ScriptedInput {
    private static final Queue<String> QUEUE = new ConcurrentLinkedQueue<String>();

    /** The center of each label that the last frame drew, where a click command finds its label, or null before the first frame. */
    private Map<String, int[]> lastTexts;
    /** The scripted pointer position, or null while the real pointer is used. */
    private int[] pointer;
    private Recorder recorder;
    private boolean release;

    static void add(String command) {
        QUEUE.add(command);
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
        if (recorder == null) return;
        if (release) ui.mouseReleased();
        lastTexts = recorder.texts;
        recorder = null;
    }

    /** Returns true when the left button must be released after this frame. */
    private boolean run(String command, Ui ui) {
        if (command == null) return false;
        int space = command.indexOf(' ');
        String verb = space < 0 ? command : command.substring(0, space);
        String arg = space < 0 ? "" : command.substring(space + 1);
        switch (verb) {
            case "click": {
                int[] at = lastTexts.get(arg);
                if (at == null) {
                    for (Map.Entry<String, int[]> e : lastTexts.entrySet()) if (e.getKey().contains(arg)) at = e.getValue();
                }
                if (at == null) return false;
                pointer = at;
                ui.mousePressed();
                return true;
            }
            case "type":
                for (char ch : arg.toCharArray()) ui.typed(ch);
                return false;
            case "key":
                ui.key(UiKey.valueOf(arg), false);
                return false;
            case "pointer":
                pointer = new int[]{-1, -1};
                return false;
            default:
                return false;
        }
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

        @Override
        public void text(String text, int x, int y, int argb) {
            c.text(text, x, y, argb);
            if (!texts.containsKey(text)) texts.put(text, new int[]{x + c.textWidth(text) / 2, y + 4});
        }
    }
}
