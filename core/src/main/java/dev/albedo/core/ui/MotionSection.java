package dev.albedo.core.ui;

import dev.albedo.core.config.SkyDesign;
import dev.albedo.core.config.SkyTurning;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** The Motion section of the sky list: a heading that opens the Turning slider and the options of a sky that turns. */
final class MotionSection {
    private static final String ID = "sky.motion";
    /** The labels of the options under Turning, which show only while the sky turns. */
    private static final String[] TURNING_OPTIONS = {Labels.TURNING_SPEED, Labels.TURNING_FOLLOW, Labels.POLE_HEIGHT,
        Labels.DIRECTION, Labels.POLE, Labels.TRAILS, Labels.TRAIL_LIGHT};

    private final Ui ui;
    private final Runnable relayout;
    /** True while the section is open; only a click on its heading changes it. */
    private boolean open;

    /** @param relayout tells the screen that the layout changed, so that it draws the frame again */
    MotionSection(Ui ui, Runnable relayout) {
        this.ui = ui;
        this.relayout = relayout;
    }

    /** The height of the section: its heading, and when it is open the Turning row and, while the sky turns, its options. */
    int height(SkyDesign design) {
        return open ? Ui.H + 2 + SkyLayersEditor.ROW * rows(design) + SkyLayersEditor.GAP : Ui.H + SkyLayersEditor.GAP;
    }

    /** The rows under the open heading: Turning, and its options while the sky turns. */
    private static int rows(SkyDesign design) {
        return design.turning().turns() ? 1 + TURNING_OPTIONS.length : 1;
    }

    /**
     * The heading, and under it when it is open the Turning slider with its options indented under it while the sky turns,
     * which returns the y below the section.
     */
    int draw(int x, int y, int w, int bottom, SkyDesign design, Consumer<SkyDesign> set) {
        int row = SkyLayersEditor.ROW, gap = SkyLayersEditor.GAP;
        SkyTurning t = design.turning();
        boolean wasOpen = open;
        if (y + Ui.H > bottom) return y; // a very short window has no room for the section
        String tip = (wasOpen ? "Hide" : "Show") + " the turning of the sky";
        if (ui.sectionHeader(ID, x - 4, y, w + 4, "Motion", Labels.turning(t), wasOpen, tip) != wasOpen) {
            open = !wasOpen;
            relayout.run();
        }
        if (!wasOpen) return y + Ui.H + gap;
        int top = y + Ui.H + 2, count = rows(design);
        int h = Math.max(0, Math.min(count * row, bottom - top));
        int labelW = ui.width(Labels.TURNING) + Form.INDENT;
        for (String l : TURNING_OPTIONS) labelW = Math.max(labelW, ui.width(l) + 2 * Form.INDENT);
        // A short window scrolls the rows, with room for the scroll bar.
        Form form = new Form(ui, x, count * row > h ? w - 6 : w, labelW + 8), turning = form.indented(), options = turning.indented();
        List<RowList.Line> rows = new ArrayList<RowList.Line>();
        rows.add(ry -> {
            int amount = turning.slider("sky.turning", ry, Labels.TURNING, Labels.TURNING_TIP, t.amount());
            if (amount != t.amount()) set.accept(design.withTurning(t.withAmount(amount)));
        });
        if (t.turns()) rows.addAll(options(options, design, set));
        if (h < row) return top;
        RowList.draw(ui, "sky.motion.rows", x, top, w, h, rows.size(), row, (r, rx, ry, rw, rh) -> {
            form.guide(ry, row);
            if (r > 0) turning.guide(ry, row);
            rows.get(r).draw(ry);
            return false;
        });
        return top + h + gap;
    }

    /** The rows of the options of a sky that turns, indented under the Turning slider. */
    private List<RowList.Line> options(Form options, SkyDesign design, Consumer<SkyDesign> set) {
        SkyTurning t = design.turning();
        Consumer<SkyTurning> put = next -> set.accept(design.withTurning(next));
        List<RowList.Line> rows = new ArrayList<RowList.Line>();
        rows.add(y -> {
            int speed = options.range("sky.turning.speed", y, Labels.TURNING_SPEED, Labels.TURNING_SPEED_TIP, t.speed(), 1,
                    SkyTurning.MAX_SPEED, "x");
            if (speed != t.speed()) put.accept(t.withSpeed(speed));
        });
        rows.add(y -> {
            boolean follow = t.clock() == SkyTurning.Clock.WORLD;
            if (options.toggle("sky.turning.follow", y, Labels.TURNING_FOLLOW, Labels.TURNING_FOLLOW_TIP, follow) != follow) {
                put.accept(t.withClock(follow ? SkyTurning.Clock.REAL : SkyTurning.Clock.WORLD));
            }
        });
        rows.add(y -> {
            int height = options.range("sky.turning.height", y, Labels.POLE_HEIGHT, Labels.POLE_HEIGHT_TIP, t.poleHeight(), 0,
                    SkyTurning.MAX_POLE_HEIGHT, "");
            if (height != t.poleHeight()) put.accept(t.withPoleHeight(height));
        });
        rows.add(y -> {
            options.label(y, Ui.H, Labels.DIRECTION, null);
            SkyTurning.Direction d = ui.choice("sky.turning.direction", options.lx(), y, options.cw(), SkyTurning.Direction.values(),
                    t.direction(), Labels::direction, Labels::directionTip);
            if (d != t.direction()) put.accept(t.withDirection(d));
        });
        rows.add(y -> {
            int pole = options.range("sky.turning.pole", y, Labels.POLE, Labels.POLE_TIP, t.pole(), 0, SkyTurning.MAX_POLE, "");
            if (pole != t.pole()) put.accept(t.withPole(pole));
        });
        rows.add(y -> {
            int trails = options.range("sky.turning.trails", y, Labels.TRAILS, Labels.TRAILS_TIP, t.trails(), 0, SkyTurning.MAX_TRAILS,
                    "");
            if (trails != t.trails()) put.accept(t.withTrails(trails));
        });
        rows.add(y -> {
            int light = options.slider("sky.turning.light", y, Labels.TRAIL_LIGHT, Labels.TRAIL_LIGHT_TIP, t.trailLight());
            if (light != t.trailLight()) put.accept(t.withTrailLight(light));
        });
        return rows;
    }
}
