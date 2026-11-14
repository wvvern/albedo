package dev.albedo.core.texture;

import dev.albedo.core.Category;
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
    static final class Tile {
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

        TextureKey key() { return key; }
    }

    private final TextureKey key;
    private final int cols;
    private final int rows;
    private final List<Tile> tiles;
    private final List<TextureKey> catalogKeys;
    /** True when the tiles, which do not overlap, fill the whole grid. */
    private final boolean covered;
    /** True for one piece of a connected texture, which gets no Bevel or Outline. */
    private final boolean connected;
    /** True for a player skin, whose looks always keep the transparent parts. */
    private final boolean skin;

    /** A texture without tiles. */
    private Layout(TextureKey key, boolean connected, boolean skin) {
        this.key = key;
        this.cols = 1;
        this.rows = 1;
        this.tiles = Collections.emptyList();
        this.catalogKeys = Collections.singletonList(key);
        this.covered = false;
        this.connected = connected;
        this.skin = skin;
    }

    /** A sheet with tiles in a grid of {@code cols} x {@code rows} cells. */
    Layout(TextureKey key, int cols, int rows, List<Tile> tiles) {
        this.key = key;
        this.cols = cols;
        this.rows = rows;
        this.tiles = Collections.unmodifiableList(new ArrayList<Tile>(tiles));
        List<TextureKey> keys = new ArrayList<TextureKey>();
        int cells = 0;
        for (Tile t : tiles) {
            keys.add(t.key);
            cells += t.w * t.h;
        }
        this.catalogKeys = Collections.unmodifiableList(keys);
        this.covered = cells == cols * rows;
        this.connected = false;
        this.skin = false;
    }

    /** The tiles of the known sheet at {@code location}, or a layout without tiles. */
    public static Layout of(String location) {
        TextureKey key = TextureClassifier.classify(location);
        Layout sheet = Sheets.get(key);
        return sheet != null ? sheet : new Layout(key, TextureClassifier.isConnected(location), TextureClassifier.isPlayerSkin(key));
    }

    /** The key of the sheet that has a tile with this key in the same category, or null. */
    public static TextureKey sheetOf(TextureKey tile) {
        return Sheets.sheetOf(tile);
    }

    TextureKey key() { return key; }

    List<Tile> tiles() { return tiles; }

    /** The keys that the texture list shows: the tiles, or the texture itself when it has no tiles. */
    List<TextureKey> catalogKeys() { return catalogKeys; }

    /**
     * The look of the texture, then the look of each tile in order, where the texture has no look of its own when its
     * tiles cover it.
     */
    public List<Appearance> looks(Policy policy) {
        // A skin has two layers in one image, and a filled second layer (hat, jacket) covers the first.
        if (skin) return Collections.singletonList(policy.appearanceFor(key).withAlpha(Appearance.Alpha.KEEP));
        if (tiles.isEmpty()) return Collections.singletonList(policy.appearanceFor(key));
        List<Appearance> out = new ArrayList<Appearance>(tiles.size() + 1);
        out.add(covered ? Appearance.ORIGINAL : policy.appearanceFor(key));
        for (Tile t : tiles) out.add(policy.appearanceFor(t.key));
        return Collections.unmodifiableList(out);
    }

    /**
     * Gives the image the looks from {@link #looks}, where each tile changes from its original pixels.
     *
     * @param gpuHue whether a GPU pass adds the hue of moving Chroma colors in each frame, which only a texture without tiles can have
     * @return true if the image was changed
     */
    public boolean paint(List<Appearance> looks, PixelBuffer img, boolean gpuHue) {
        boolean sheetChanges = !looks.get(0).isOriginal();
        // A tile keeps its original pixels when neither the texture look nor its own look changes them.
        PixelBuffer.Heap[] parts = new PixelBuffer.Heap[tiles.size()];
        for (int i = 0; i < parts.length; i++) {
            if (sheetChanges || !looks.get(i + 1).isOriginal()) parts[i] = copy(img, tiles.get(i));
        }
        // A sheet with tiles gets its edge effects on each tile, not around the whole sheet.
        boolean changed = look(looks.get(0), img, key, edges(), gpuHue && tiles.isEmpty());
        for (int i = 0; i < parts.length; i++) {
            if (parts[i] == null) continue;
            boolean tileChanged = look(looks.get(i + 1), parts[i], tiles.get(i).key, true, false);
            if (changed || tileChanged) paste(parts[i], img, tiles.get(i));
            changed |= tileChanged;
        }
        return changed;
    }

    /**
     * Puts the {@link ChromaTexels} of one frame of {@code w} by {@code h} pixels into {@code out}, for a texture that
     * {@link #paint} painted with {@code gpuHue} from {@code original}.
     *
     * @return false when the colors of the texture do not move, which leaves {@code out} as it is
     */
    boolean chromaTexels(List<Appearance> looks, PixelBuffer.Heap original, int w, int h, int[] out) {
        Appearance a = looks.get(0);
        if (!tiles.isEmpty() || !a.chromaMoves()) return false;
        boolean lined = (a.outline() > 0 || a.chroma().edge()) && edges() && key.category().hasOutline();
        return Effects.texels(a, key.id(), lined ? line(a, original, w, h) : null, w, h, out);
    }

    /**
     * The {@link Effects#edgeMask} of the image with the style of the look, sampled to {@code w} by {@code h} pixels so
     * that it fits the frame in the atlas, or null when a frame is too small for a line.
     */
    private boolean[] line(Appearance a, PixelBuffer.Heap original, int w, int h) {
        PixelBuffer.Heap styled = original.copy();
        PixelTransformer.apply(a, styled);
        int sw = styled.width(), sh = Effects.frameHeight(styled);
        if (sw < 4 || sh < 4 || w < 4 || h < 4) return null;
        boolean[] mask = Effects.edgeMask(styled, key.id(), sh), out = new boolean[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) out[y * w + x] = mask[y * sh / h * sw + x * sw / w];
        }
        return out;
    }

    /** Whether the texture itself can get edge effects, which a sheet with tiles and a connected texture cannot. */
    private boolean edges() {
        return tiles.isEmpty() && !connected;
    }

    /** The style of a look and then its effects, where only block and fluid tiles get Bevel, and items get Outline too. */
    private static boolean look(Appearance a, PixelBuffer img, TextureKey key, boolean edges, boolean gpuHue) {
        boolean changed = PixelTransformer.apply(a, img);
        Category c = key.category();
        return Effects.apply(a, img, key.id(), edges && c.hasBevel(), edges && c.hasOutline(), gpuHue) || changed;
    }

    /** The original average color for each of the {@link #catalogKeys}. */
    int[] averages(PixelBuffer original) {
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
