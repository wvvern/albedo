package dev.albedo.core.config;

import dev.albedo.core.Category;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** A named look: settings for each category plus world-wide {@link Toggle}s. Immutable. */
public final class Profile {
    private final String name;
    private final Map<Category, CategorySettings> categories;
    private final Set<Toggle> on;

    public Profile(String name, Map<Category, CategorySettings> categories, Set<Toggle> on) {
        this.name = name;
        EnumMap<Category, CategorySettings> copy = new EnumMap<Category, CategorySettings>(Category.class);
        for (Map.Entry<Category, CategorySettings> e : categories.entrySet()) {
            if (!e.getValue().equals(CategorySettings.UNTOUCHED)) copy.put(e.getKey(), e.getValue());
        }
        this.categories = Collections.unmodifiableMap(copy);
        this.on = Collections.unmodifiableSet(on.isEmpty() ? EnumSet.noneOf(Toggle.class) : EnumSet.copyOf(on));
    }

    /** The toggles that are on by default. */
    public static Set<Toggle> defaultToggles() {
        Set<Toggle> s = EnumSet.noneOf(Toggle.class);
        for (Toggle t : Toggle.values()) if (t.defaultOn) s.add(t);
        return s;
    }

    public String name() { return name; }

    public CategorySettings get(Category c) {
        CategorySettings s = categories.get(c);
        return s == null ? CategorySettings.UNTOUCHED : s;
    }

    /** Only the categories that change something. */
    public Map<Category, CategorySettings> configured() { return categories; }

    public boolean is(Toggle t) { return on.contains(t); }

    public Profile with(Category c, CategorySettings s) {
        EnumMap<Category, CategorySettings> next = new EnumMap<Category, CategorySettings>(Category.class);
        next.putAll(categories);
        next.put(c, s);
        return new Profile(name, next, on);
    }

    public Profile with(Toggle t, boolean value) {
        Set<Toggle> next = EnumSet.noneOf(Toggle.class);
        next.addAll(on);
        if (value) next.add(t); else next.remove(t);
        return new Profile(name, categories, next);
    }

    public Profile renamed(String newName) { return new Profile(newName, categories, on); }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Profile)) return false;
        Profile p = (Profile) o;
        return p.name.equals(name) && p.on.equals(on) && p.categories.equals(categories);
    }

    @Override
    public int hashCode() { return name.hashCode() * 31 + categories.hashCode(); }
}
