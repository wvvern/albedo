package dev.albedo.core.dev;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HarnessTest {
    @Test
    void theCloudsCommandMarksCloudsInTheWrongState() {
        assertEquals("clouds hidden", Harness.cloudsLine(false, "hidden"));
        assertEquals("clouds shown", Harness.cloudsLine(true, "shown"));
        assertEquals("! clouds shown, expected hidden", Harness.cloudsLine(true, "hidden"));
        assertEquals("! clouds hidden, expected shown", Harness.cloudsLine(false, "shown"));
        assertThrows(IllegalArgumentException.class, () -> Harness.cloudsLine(true, "off"));
    }

    @Test
    void theFrameChangedLineFailsBelowItsLimit() {
        assertEquals("framechanged water: mean 20.0 steps (limit 8.0)", Harness.frameChangedLine("water", 20));
        assertEquals("! framechanged water: mean 0.3 steps (limit 8.0): the frame did not change", Harness.frameChangedLine("water", 0.3));
    }

    @Test
    void theHooksLineShowsTheRunsOfEachCounter() {
        HookCounter ran = new HookCounter("afterSolid", true), missed = new HookCounter("tint", true);
        ran.run();
        ran.run();
        assertEquals("hooks: afterSolid=2 tint=0", Harness.hooksLine(Arrays.asList(ran, missed)));
    }

    @Test
    void aCounterDoesNotCountWithoutTheHarness() {
        HookCounter counter = new HookCounter("tint");
        counter.run();
        assertEquals(0, counter.runs());
    }
}
