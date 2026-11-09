package dev.albedo.core.texture;

import dev.albedo.core.Category;
import dev.albedo.core.PixelBuffer;
import dev.albedo.core.Policy;
import dev.albedo.core.config.Appearance;
import dev.albedo.core.config.Chroma;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChromaAnimatorTest {
    /** A bit more than one color step at speed 100, whose period is 10 s. */
    private static final long STEP_NANOS = 210_000_000L;
    private static final long PAINT_NANOS = 300_000;

    /** A clock that moves only when a test or an upload moves it. */
    private static final class Clock {
        long nanos;
    }

    /** An in-place upload that takes {@link #PAINT_NANOS} of the clock and records the order of the textures. */
    private static TextureStore.Upload upload(Clock clock, List<String> order, String name) {
        return TextureStore.inPlace(pixels -> {
            clock.nanos += PAINT_NANOS;
            order.add(name);
            return true;
        });
    }

    private static Policy moving(int speed) {
        return LayoutTest.policy(Category.BLOCKS, Appearance.solid(0xFFFFFFFF).withEffects(0, 0, Chroma.of(100).withOptions(speed, 50, 50)));
    }

    private static void record(TextureStore store, String name, TextureStore.Upload upload, Policy policy) {
        Layout layout = Layout.of(name);
        store.record(name, layout, new PixelBuffer.Heap(1, 1, new int[]{0xFF808080}), upload, layout.looks(policy), 0);
    }

    @Test
    void theStepOfTheColorsComesFromTheTimeAndTheSpeed() {
        Chroma fast = Chroma.of(100).withOptions(100, 50, 50);
        assertEquals(10_000, fast.periodMs());
        assertEquals(120_000, fast.withOptions(1, 50, 50).periodMs());
        assertTrue(Chroma.of(100).periodMs() > 40_000 && Chroma.of(100).periodMs() < 60_000, "the default speed 30: " + Chroma.of(100).periodMs());
        assertEquals(0, fast.phase(0));
        assertEquals(0, fast.phase(208));
        assertEquals(1, fast.phase(209));
        assertEquals(Chroma.PHASES - 1, fast.phase(9_999));
        assertEquals(0, fast.phase(10_000), "one turn goes back to the start");
        assertEquals(Chroma.PHASES - 1, fast.phase(-1), "a negative clock is still a step");
        assertEquals(0, fast.withOptions(0, 50, 50).phase(5_000), "speed 0 is still");
        assertEquals(0, Chroma.OFF.withOptions(100, 50, 50).phase(5_000), "Chroma off does not move");
        assertEquals(0, Appearance.ORIGINAL.withEffects(0, 0, fast).chromaPhase(5_000), "Vanilla has no Chroma");
    }

    @Test
    void eachLookOfASheetHasItsOwnStep() {
        Appearance slow = Appearance.solid(0xFFFFFFFF).withEffects(0, 0, Chroma.of(100).withOptions(1, 50, 50));
        Appearance fast = slow.withEffects(0, 0, Chroma.of(100).withOptions(100, 50, 50));
        int[] phases = Layout.phases(Arrays.asList(slow, fast, Appearance.ORIGINAL), 5_000);
        assertEquals(2, phases[0]);
        assertEquals(24, phases[1]);
        assertEquals(0, phases[2]);
    }

    @Test
    void theAnimatorStopsAtItsBudgetAndContinuesWhereItStopped() {
        Clock clock = new Clock();
        TextureStore store = new TextureStore();
        List<String> order = new ArrayList<String>();
        Policy policy = moving(100);
        for (int i = 0; i < 20; i++) {
            String name = String.format("minecraft:block/b%02d", i);
            record(store, name, upload(clock, order, name), policy);
        }
        ChromaAnimator animator = new ChromaAnimator(() -> clock.nanos);
        AtomicInteger flushes = new AtomicInteger();

        assertEquals(0, animator.tick(store, policy, () -> flushes.incrementAndGet() > 0).repainted, "the colors are at their step");
        clock.nanos = STEP_NANOS;
        // Each repaint takes 0.3 ms and keeps 0.02 ms free for its upload, so 7 fit in the 2 ms budget.
        ChromaAnimator.Tick first = animator.tick(store, policy, () -> flushes.incrementAndGet() > 0);
        assertEquals(20, first.moving);
        assertEquals(7, first.repainted);
        assertEquals(1, flushes.get(), "one upload to the GPU for each tick that repainted");
        assertTrue(first.paintNanos <= ChromaAnimator.BUDGET_NANOS + PAINT_NANOS, "at most one repaint past the budget: " + first.paintNanos);
        List<String> firstNames = new ArrayList<String>(order);
        animator.tick(store, policy, () -> true);
        animator.tick(store, policy, () -> true);
        assertEquals(20, order.size(), "three ticks reach all textures once");
        assertEquals(20, new HashSet<String>(order).size(), "no texture twice before all had a turn");
        assertEquals(firstNames, order.subList(0, 7));
        assertEquals(0, animator.tick(store, policy, () -> true).repainted, "all textures show the new step");
    }

    @Test
    void theBudgetKeepsRoomForTheMeasuredUpload() {
        Clock clock = new Clock();
        TextureStore store = new TextureStore();
        List<String> order = new ArrayList<String>();
        Policy policy = moving(100);
        for (int i = 0; i < 20; i++) record(store, "minecraft:block/c" + i, upload(clock, order, "c" + i), policy);
        ChromaAnimator animator = new ChromaAnimator(() -> clock.nanos);
        clock.nanos = STEP_NANOS;
        // A slow upload of 1 ms for each texture makes the next ticks repaint fewer textures.
        int first = animator.tick(store, policy, () -> {
            clock.nanos += 7 * 1_000_000L;
            return true;
        }).repainted;
        int second = animator.tick(store, policy, () -> {
            clock.nanos += 1_000_000L;
            return true;
        }).repainted;
        assertEquals(7, first);
        assertTrue(second < first, "the upload time counts toward the budget: " + second);
    }

    @Test
    void aFailedUploadKeepsTheColorsOfThatTextureStill() {
        Clock clock = new Clock();
        TextureStore store = new TextureStore();
        Policy policy = moving(100);
        TextureStore.Upload broken = TextureStore.inPlace(pixels -> {
            throw new IllegalStateException("shaders");
        });
        record(store, "minecraft:block/stone", broken, policy);
        ChromaAnimator animator = new ChromaAnimator(() -> clock.nanos);
        clock.nanos = STEP_NANOS;
        ChromaAnimator.Tick t = animator.tick(store, policy, () -> true);
        assertEquals(1, t.failed);
        assertEquals(1, store.stillCount());
        clock.nanos = 2 * STEP_NANOS;
        assertEquals(0, animator.tick(store, policy, () -> true).moving, "it does not try again");
    }

    @Test
    void anUploadThatIsNotInPlaceDoesNotMove() {
        Clock clock = new Clock();
        TextureStore store = new TextureStore();
        Policy policy = moving(100);
        record(store, "minecraft:block/stone", new TextureStoreTest.FakeUpload(), policy);
        ChromaAnimator animator = new ChromaAnimator(() -> clock.nanos);
        clock.nanos = STEP_NANOS;
        assertEquals(0, animator.tick(store, policy, () -> true).moving);
        record(store, "minecraft:block/dirt", TextureStore.inPlace(new TextureStoreTest.FakeUpload()), moving(0));
        assertEquals(0, animator.tick(store, moving(0), () -> true).moving, "speed 0 does not move");
    }

    @Test
    void aFailedFlushStopsTheColorsUntilTheNextSettingsChange() {
        Clock clock = new Clock();
        TextureStore store = new TextureStore();
        List<String> order = new ArrayList<String>();
        Policy policy = moving(100);
        record(store, "minecraft:block/stone", upload(clock, order, "stone"), policy);
        ChromaAnimator animator = new ChromaAnimator(() -> clock.nanos);
        clock.nanos = STEP_NANOS;
        assertFalse(animator.tick(store, policy, () -> false).uploaded);
        clock.nanos = 2 * STEP_NANOS;
        assertEquals(0, animator.tick(store, policy, () -> true).repainted);
        Policy next = moving(99);
        TextureStoreTest.repaint(store, next);
        clock.nanos = 3 * STEP_NANOS;
        assertNotEquals(0, animator.tick(store, next, () -> true).moving);
    }

    @Test
    void aRepaintKeepsTheStepOfTexturesWhoseLookDidNotChange() {
        Clock clock = new Clock();
        TextureStore store = new TextureStore();
        List<String> order = new ArrayList<String>();
        Policy policy = moving(100);
        record(store, "minecraft:block/stone", upload(clock, order, "stone"), policy);
        assertEquals(0, store.repaint(policy, 5_000).repainted, "the animator, not the repaint, moves the colors");
        assertEquals(1, store.repaint(moving(50), 5_000).repainted);
    }

    @Test
    void theReportGivesTheTimesOfEachTick() {
        Clock clock = new Clock();
        TextureStore store = new TextureStore();
        List<String> order = new ArrayList<String>();
        Policy policy = moving(100);
        record(store, "minecraft:block/stone", upload(clock, order, "stone"), policy);
        ChromaAnimator animator = new ChromaAnimator(() -> clock.nanos);
        for (int i = 0; i < ChromaAnimator.REPORT_TICKS - 1; i++) {
            clock.nanos += STEP_NANOS;
            animator.tick(store, policy, () -> true);
        }
        assertNull(animator.report());
        animator.tick(store, policy, () -> true);
        String report = animator.report();
        assertNotNull(report);
        assertTrue(report.startsWith("Chroma tick: 1 moving textures") && report.contains("p95 0.30 ms"), report);
        assertNull(animator.report(), "each report covers new ticks");
    }
}
