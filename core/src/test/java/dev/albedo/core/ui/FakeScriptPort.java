package dev.albedo.core.ui;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** The harness side of {@link ScriptPort} for the tests: a command queue, the logged lines and the last real click. */
final class FakeScriptPort implements ScriptPort {
    final Deque<String> commands = new ArrayDeque<String>();
    /** The logged lines, where a failure has the fail prefix of the harness log. */
    final List<String> lines = new ArrayList<String>();
    private int[] realClick;

    @Override public boolean waiting() { return !commands.isEmpty(); }

    @Override public String poll() { return commands.poll(); }

    @Override public void log(String line) { lines.add(line); }

    @Override public void fail(String reason) { lines.add("! " + reason); }

    @Override public void realClick(int[] at) { realClick = at; }

    /** Takes the position of the last real click, or null when none waits. */
    int[] takeRealClick() {
        int[] at = realClick;
        realClick = null;
        return at;
    }
}
