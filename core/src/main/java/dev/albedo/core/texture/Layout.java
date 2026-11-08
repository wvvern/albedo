package dev.albedo.core.texture;

import dev.albedo.core.PixelBuffer;
import dev.albedo.core.Policy;
import dev.albedo.core.config.Appearance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A texture with its own key and optional named tiles, such as the 1.8.9 particle sheet, where each tile takes the
 * look of its own id and the pixels outside the tiles take the look of the texture.
 */
public final class Layout {
    /** A named rectangle in the cells of the grid of its texture. */
    public static final class Tile {
        final TextureKey key;
        final int x;
        final int y;
        final int w;
        final int h;

        Tile(TextureKey key, int x, int y, int w, int h) {
            this.key = key;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        public TextureKey key() { return key; }
    }

    private final TextureKey key;
    private final int cols;
    private final int rows;
    private final List<Tile> tiles;
    private final List<TextureKey> catalogKeys;
    /** True when the tiles, which do not overlap, fill the whole grid. */
    private final boolean covered;

    Layout(TextureKey key, int cols, int rows, List<Tile> tiles) {
        this.key = key;
        this.cols = cols;
        this.rows = rows;
        if (tiles.isEmpty()) {
            this.tiles = Collections.emptyList();
            this.catalogKeys = Collections.singletonList(key);
            this.covered = false;
            return;
        }
        this.tiles = Collections.unmodifiableList(new ArrayList<Tile>(tiles));
        List<TextureKey> keys = new ArrayList<TextureKey>();
        int cells = 0;
        for (Tile t : tiles) {
            keys.add(t.key);
            cells += t.w * t.h;
        }
        this.catalogKeys = Collections.unmodifiableList(keys);
        this.covered = cells == cols * rows;
    }

    /** The tiles of a known sheet, or a layout without tiles. */
    public static Layout of(TextureKey key) {
        Layout sheet = Sheets.get(key);
        return sheet != null ? sheet : new Layout(key, 1, 1, Collections.<Tile>emptyList());
    }

    public TextureKey key() { return key; }

    public List<Tile> tiles() { return tiles; }

    /** The keys that the texture list shows: the tiles, or the texture itself when it has no tiles. */
    public List<TextureKey> catalogKeys() { return catalogKeys; }

    /**
     * The look of the texture, then the look of each tile in order, where the texture has no look of its own when its
     * tiles cover it.
     */
    public List<Appearance> looks(Policy policy) {
        if (tiles.isEmpty()) return Collections.singletonList(policy.appearanceFor(key));
        List<Appearance> out = new ArrayList<Appearance>(tiles.size() + 1);
        out.add(covered ? Appearance.ORIGINAL : policy.appearanceFor(key));
        for (Tile t : tiles) out.add(policy.appearanceFor(t.key, key));
        return Collections.unmodifiableList(out);
    }

    /**
     * Gives the image the looks from {@link #looks}, where each tile changes from its original pixels.
     *
     * @return true if the image was changed
     */
    public boolean paint(List<Appearance> looks, PixelBuffer img, PixelTransformer.TextureSource textures) {
        if (tiles.isEmpty()) return PixelTransformer.apply(looks.get(0), img, textures);
        boolean sheetChanges = !looks.get(0).isOriginal();
        // A tile keeps its original pixels when neither the texture look nor its own look changes them.
        PixelBuffer.Heap[] parts = new PixelBuffer.Heap[tiles.size()];
        for (int i = 0; i < parts.length; i++) {
            if (sheetChanges || !looks.get(i + 1).isOriginal()) parts[i] = copy(img, tiles.get(i));
        }
        boolean changed = PixelTransformer.apply(looks.get(0), img, textures);
        for (int i = 0; i < parts.length; i++) {
            if (parts[i] == null) continue;
            boolean tileChanged = PixelTransformer.apply(looks.get(i + 1), parts[i], textures);
            if (changed || tileChanged) paste(parts[i], img, tiles.get(i));
            changed |= tileChanged;
        }
        return changed;
    }

    /** The original average color for each of the {@link #catalogKeys}. */
    public int[] averages(PixelBuffer original) {
        if (tiles.isEmpty()) return new int[]{PixelTransformer.average(original)};
        int[] out = new int[tiles.size()];
        for (int i = 0; i < out.length; i++) out[i] = PixelTransformer.average(copy(original, tiles.get(i)));
        return out;
    }

    /** The pixel bounds of a tile as {x0, y0, x1, y1}, in grid fractions so that resource packs of any size fit. */
    int[] bounds(Tile t, int width, int height) {
        return new int[]{t.x * width / cols, t.y * height / rows, (t.x + t.w) * width / cols, (t.y + t.h) * height / rows};
    }

    private PixelBuffer.Heap copy(PixelBuffer img, Tile t) {
        int[] b = bounds(t, img.width(), img.height());
        PixelBuffer.Heap out = new PixelBuffer.Heap(b[2] - b[0], b[3] - b[1]);
        for (int y = 0; y < out.height(); y++) {
            for (int x = 0; x < out.width(); x++) out.setArgb(x, y, img.getArgb(b[0] + x, b[1] + y));
        }
        return out;
    }

    private void paste(PixelBuffer.Heap part, PixelBuffer img, Tile t) {
        int[] b = bounds(t, img.width(), img.height());
        for (int y = 0; y < part.height(); y++) {
            for (int x = 0; x < part.width(); x++) img.setArgb(b[0] + x, b[1] + y, part.getArgb(x, y));
        }
    }
}
