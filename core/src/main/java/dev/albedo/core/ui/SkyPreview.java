package dev.albedo.core.ui;

import dev.albedo.core.config.SkyDesign;
import dev.albedo.core.config.SkyLayer;
import dev.albedo.core.config.SkyMask;
import dev.albedo.core.sky.SkyBaker;
import dev.albedo.core.sky.SkyClock;
import dev.albedo.core.sky.SkyImage;
import dev.albedo.core.sky.SkyTurn;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/** The sky preview strip of the sky editor: the bake, the shown image, and the lines and compass over it. */
final class SkyPreview {
    /** The size of the preview image, the same shape as the image in the world. */
    private static final int PREVIEW_W = 512, PREVIEW_H = 256;
    /** The width of the preview image of a turning sky, less than the bake so that it costs little to make again. */
    private static final int TURNED_W = 384;
    /** The seconds between two images of a turning preview. */
    private static final double TURN_STEP_S = 0.05;
    /** The colors of the lines and the label over the preview. */
    private static final int HORIZON = 0x99FFFFFF, SUN_PATH = 0xCCFFD24D, ZENITH_LINE = 0x66FFFFFF, ZENITH_TEXT = 0xCCFFFFFF;

    private final Ui ui;
    private final SkyBaker baker;
    private CanvasImage shown;
    /** What {@link #shown} was made from, or null before the first image. */
    private Shown key;
    private long shownSerial;

    /** The serial of the image that {@link #shown} comes from, whether it is turned, and the clock time when it was made. */
    private static final class Shown {
        final long from;
        final boolean turned;
        final double at;

        Shown(long from, boolean turned, double at) {
            this.from = from;
            this.turned = turned;
            this.at = at;
        }
    }

    /** @param errors receives a failure of a preview bake */
    SkyPreview(Ui ui, Consumer<Throwable> errors) {
        this.ui = ui;
        this.baker = new SkyBaker(PREVIEW_W, PREVIEW_H, errors);
    }

    /** Drops the requested design, so that its image does not show on the next screen while a new bake runs. */
    void clear() {
        baker.request(null);
    }

    /** The height a preview of width {@code w} takes, with its compass row. */
    static int height(int w) {
        return Math.min(72, w / 4) + 10;
    }

    /**
     * The design the preview bakes: the full sky, or one layer alone, also a layer that is only a mask, where the design keeps
     * its turning, so that a layer page shares the bake of the list and shows the trails.
     */
    private static SkyDesign bakedDesign(SkyDesign design, int index, SkyPreviewSource source) {
        if (index < 0 || source == SkyPreviewSource.RESULT) return design;
        SkyLayer layer = design.layers().get(index).withOutput(SkyLayer.Output.SHOW).withMask(SkyMask.NONE).withOpacity(100)
                .withBlend(SkyLayer.Blend.NORMAL);
        List<SkyLayer> layers = new ArrayList<SkyLayer>();
        if (layer.kind().role() == SkyLayer.Role.FILTER) {
            for (SkyLayer below : design.below(index)) if (below.output() == SkyLayer.Output.SHOW) layers.add(below);
        }
        layers.add(layer);
        return design.withLayers(layers);
    }

    /**
     * Draws the sky image with the horizon, the zenith, the compass and the sun path, and returns the height used.
     *
     * @param index the open layer of a layer page, or -1 on the other screens
     * @param source what a layer page shows
     */
    int draw(int x, int y, int w, SkyDesign design, int index, SkyPreviewSource source) {
        baker.request(bakedDesign(design, index, source));
        int ph = height(w) - 10;
        Rect strip = new Rect(x, y, w, ph);
        SkyImage img = baker.image();
        SkyDesign drawn = img == null ? design : img.design();
        if (img == null) {
            ui.fill(x, y, w, ph, Ui.PANEL);
            ui.textFit("Making the preview", x + 4, y + 4, w - 8, Ui.FAINT);
        } else {
            // A layer page shows the sky still, so that the place of the layer shows against the compass.
            showImage(img, drawn, index < 0 && drawn.turning().turns());
            ui.image("sky.preview", shown, x, y, w, ph);
        }
        int horizon = SkyPreviewGeometry.y(strip, drawn, 0);
        ui.fill(x, horizon, w, 1, HORIZON);
        // The vanilla sun rises in the east, passes through the zenith and sets in the west.
        for (int az : new int[]{SkyPreviewGeometry.SUNRISE, SkyPreviewGeometry.SUNSET}) {
            int sx = SkyPreviewGeometry.x(strip, az);
            for (int dy = y; dy < horizon; dy += 4) ui.fill(sx, dy, 1, Math.min(2, horizon - dy), SUN_PATH);
        }
        ui.fill(x, y, w, 1, ZENITH_LINE);
        ui.textFit("Zenith", x + w - ui.width("Zenith") - 3, y + 2, w, ZENITH_TEXT);
        ui.tip(x, y, w, ph, "White line: the horizon. Yellow: the sun path. The world sky changes after Apply or Done.");
        if (w >= 24) {
            for (int i = 0; i < SkyPreviewGeometry.COMPASS.length; i++) {
                int cx = SkyPreviewGeometry.x(strip, i * 90);
                String label = SkyPreviewGeometry.COMPASS[i];
                ui.fill(cx, y + ph, 1, 2, Ui.MUTED);
                ui.text(label, Math.max(x, Math.min(x + w - ui.width(label), cx - ui.width(label) / 2)), y + ph + 2, Ui.MUTED);
            }
        }
        return ph + 10;
    }

    /** Makes {@link #shown} from a new image, and again a short time later while the sky turns, so that the preview turns too. */
    private void showImage(SkyImage img, SkyDesign drawn, boolean turned) {
        double now = SkyClock.longSeconds();
        if (!stale(img, turned, now)) return;
        int rows = SkyPreviewGeometry.rows(drawn, img.height());
        if (turned) {
            int h = Math.max(1, rows * TURNED_W / img.width());
            shown = new CanvasImage(SkyTurn.preview(drawn.turning()).resample(img, rows, TURNED_W, h), TURNED_W, h, ++shownSerial);
        } else {
            shown = new CanvasImage(Arrays.copyOf(img.argb(), rows * img.width()), img.width(), rows, ++shownSerial);
        }
        key = new Shown(img.serial(), turned, now);
    }

    /** True when {@link #shown} is not the image of {@code img}, or is a turned image older than {@link #TURN_STEP_S}. */
    private boolean stale(SkyImage img, boolean turned, double now) {
        return key == null || key.from != img.serial() || key.turned != turned || (turned && Math.abs(now - key.at) >= TURN_STEP_S);
    }
}
