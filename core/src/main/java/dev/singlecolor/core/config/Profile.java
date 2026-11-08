package dev.singlecolor.core.config;

import dev.singlecolor.core.Category;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/** A named look: settings for each category. Immutable. */
public final class Profile {
    private final String name;
    private final Map<Category, CategorySettings> categories;

    public Profile(String name, Map<Category, CategorySettings> categories) {
        this.name = name;
        EnumMap<Category, CategorySettings> copy = new EnumMap<Category, CategorySettings>(Category.class);
        copy.putAll(categories);
        this.categories = Collections.unmodifiableMap(copy);
    }

    public String name() { return name; }

    public CategorySettings get(Category c) {
        CategorySettings s = categories.get(c);
        return s == null ? CategorySettings.UNTOUCHED : s;
    }

    /** Only the categories the user configured (others are untouched). */
    public Map<Category, CategorySettings> configured() { return categories; }
}
