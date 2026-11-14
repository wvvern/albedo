package dev.albedo.core.ui;

final class TextField {
    interface Filter {
        boolean accepts(char c);
    }

    static final Filter ANY = c -> true;

    private String text = "";
    private int cursor;
    /** After select-all the next edit replaces everything. */
    private boolean selectAll;
    private final int maxLength;
    private final Filter filter;

    TextField(int maxLength, Filter filter) {
        this.maxLength = maxLength;
        this.filter = filter;
    }

    String text() { return text; }

    int cursor() { return cursor; }

    boolean allSelected() { return selectAll; }

    /** Selects all text, so that the next edit replaces it. */
    void selectAll() { selectAll = true; }

    /** Clears the selection and puts the cursor at the end. */
    void focusAtEnd() {
        selectAll = false;
        cursor = text.length();
    }

    void set(String value) {
        text = value;
        focusAtEnd();
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

    boolean key(UiKey key) {
        if (key == UiKey.SELECT_ALL) {
            selectAll();
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
