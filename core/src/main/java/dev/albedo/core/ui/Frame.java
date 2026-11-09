package dev.albedo.core.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** A canvas that keeps the draw calls of one frame, so that a frame whose input changed the layout can be drawn again before it shows. */
final class Frame implements Canvas {
    private static final int FILL = 0, TEXT = 1, IMAGE = 2, STRIDE = 6;

    private Canvas target;
    private final List<String> texts = new ArrayList<String>();
    private final List<CanvasImage> images = new ArrayList<CanvasImage>();
    /** The id of each image, at the same index as the image. */
    private final List<String> imageIds = new ArrayList<String>();
    /**
     * For each call: its kind, then x0, y0, x1, y1 and color for a fill, x, y, the text index, unused and color for a
     * text, or x, y, w, h and the image index for an image.
     */
    private int[] ops = new int[STRIDE * 512];
    private int size;

    /** Starts an empty frame for {@code target}. */
    Frame begin(Canvas target) {
        this.target = target;
        texts.clear();
        images.clear();
        imageIds.clear();
        size = 0;
        return this;
    }

    /** Sends the kept calls to the target in order. */
    void show() {
        for (int i = 0; i < size; i += STRIDE) {
            if (ops[i] == TEXT) {
                target.text(texts.get(ops[i + 3]), ops[i + 1], ops[i + 2], ops[i + 5]);
            } else if (ops[i] == IMAGE) {
                int k = ops[i + 5];
                target.image(imageIds.get(k), images.get(k), ops[i + 1], ops[i + 2], ops[i + 3], ops[i + 4]);
            } else {
                target.fill(ops[i + 1], ops[i + 2], ops[i + 3], ops[i + 4], ops[i + 5]);
            }
        }
    }

    @Override
    public void image(String id, CanvasImage img, int x, int y, int w, int h) {
        imageIds.add(id);
        images.add(img);
        add(IMAGE, x, y, w, h, images.size() - 1);
    }

    private void add(int kind, int a, int b, int c, int d, int argb) {
        if (size + STRIDE > ops.length) ops = Arrays.copyOf(ops, ops.length * 2);
        ops[size++] = kind;
        ops[size++] = a;
        ops[size++] = b;
        ops[size++] = c;
        ops[size++] = d;
        ops[size++] = argb;
    }

    @Override public int width() { return target.width(); }
    @Override public int height() { return target.height(); }
    @Override public int textWidth(String text) { return target.textWidth(text); }

    @Override
    public void fill(int x0, int y0, int x1, int y1, int argb) {
        add(FILL, x0, y0, x1, y1, argb);
    }

    @Override
    public void text(String text, int x, int y, int argb) {
        texts.add(text);
        add(TEXT, x, y, texts.size() - 1, 0, argb);
    }
}
