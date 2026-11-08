package dev.plainview.core.ui;

/** State of a single-line text input. */
final class TextField {
    interface Filter {
        boolean accepts(char c);
    }

    static final Filter ANY = new Filter() {
        @Override
        public boolean accepts(char c) { return true; }
    };

    String text = "";
    int cursor;
    /** After Ctrl+A the next edit replaces everything. */
    boolean selectAll;
    private final int maxLength;
    private final Filter filter;

    TextField(int maxLength, Filter filter) {
        this.maxLength = maxLength;
        this.filter = filter;
    }

    void set(String value) {
        text = value;
        cursor = value.length();
        selectAll = false;
    }

    boolean insert(String s) {
        StringBuilder accepted = new StringBuilder();
        for (char ch : s.toCharArray()) if (ch >= ' ' && filter.accepts(ch)) accepted.append(ch);
        if (accepted.length() == 0) return false;
        String before = selectAll ? "" : text.substring(0, cursor);
        String after = selectAll ? "" : text.substring(cursor);
        String next = before + accepted + after;
        if (next.length() > maxLength) return false; // nothing changes, including a pending select-all
        selectAll = false;
        text = next;
        cursor = before.length() + accepted.length();
        return true;
    }

    boolean key(UiKey key, boolean ctrl) {
        if (ctrl && key == UiKey.A) {
            selectAll = true;
            return false;
        }
        boolean wasAll = selectAll;
        selectAll = false;
        switch (key) {
            case BACKSPACE:
                if (wasAll) { set(""); return true; }
                if (cursor == 0) return false;
                text = text.substring(0, cursor - 1) + text.substring(cursor);
                cursor--;
                return true;
            case DELETE:
                if (wasAll) { set(""); return true; }
                if (cursor >= text.length()) return false;
                text = text.substring(0, cursor) + text.substring(cursor + 1);
                return true;
            case LEFT: cursor = Math.max(0, cursor - 1); return false;
            case RIGHT: cursor = Math.min(text.length(), cursor + 1); return false;
            case HOME: cursor = 0; return false;
            case END: cursor = text.length(); return false;
            default: return false;
        }
    }
}
