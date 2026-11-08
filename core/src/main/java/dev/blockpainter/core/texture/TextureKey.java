package dev.blockpainter.core.texture;

import dev.blockpainter.core.Category;

/** A texture's category and its rule-matching id (e.g. BLOCKS + "minecraft:oak_leaves"). */
public final class TextureKey {
    private final Category category;
    private final String id;

    public TextureKey(Category category, String id) {
        this.category = category;
        this.id = id;
    }

    public Category category() { return category; }

    public String id() { return id; }

    @Override
    public boolean equals(Object o) {
        return o instanceof TextureKey && ((TextureKey) o).category == category && ((TextureKey) o).id.equals(id);
    }

    @Override
    public int hashCode() { return category.hashCode() * 31 + id.hashCode(); }

    @Override
    public String toString() { return category.key() + "/" + id; }
}
