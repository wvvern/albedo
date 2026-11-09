package dev.albedo.core.ui;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** The open sections of a page, as an immutable value: {@link #toggled} gives a new one. */
final class Accordion {
    private final Set<String> open;

    private Accordion(Set<String> open) {
        this.open = Collections.unmodifiableSet(open);
    }

    /** The sections with these ids open and all others closed. */
    static Accordion of(String... open) {
        return new Accordion(new HashSet<String>(Arrays.asList(open)));
    }

    boolean isOpen(String id) { return open.contains(id); }

    /** The same sections with {@code id} closed when it was open, and open when it was closed. */
    Accordion toggled(String id) {
        Set<String> next = new HashSet<String>(open);
        if (!next.remove(id)) next.add(id);
        return new Accordion(next);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Accordion && ((Accordion) o).open.equals(open);
    }

    @Override
    public int hashCode() { return open.hashCode(); }

    @Override
    public String toString() { return "Accordion" + open; }
}
