package dev.albedo.core.texture;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChromaTimingTest {
    /** A GPU timer whose time comes when the test gives it. */
    private static final class FakeTimer implements ChromaTiming.GpuTimer {
        long nanos = -1;
        boolean began, ended, closed;

        @Override public void begin() { began = true; }

        @Override public void end() { ended = true; }

        @Override public long nanos() { return nanos; }

        @Override public void close() { closed = true; }
    }

    private final List<FakeTimer> timers = new ArrayList<FakeTimer>();
    private List<long[]> logged;
    private long loggedBytes;
    private String loggedAtlases;
    private final ChromaTiming timing = new ChromaTiming(() -> {
        FakeTimer t = new FakeTimer();
        timers.add(t);
        return t;
    }, (frames, bytes, atlases) -> {
        logged = new ArrayList<long[]>(frames);
        loggedBytes = bytes;
        loggedAtlases = atlases;
    });

    /** Runs one frame of the pass with these CPU times. */
    private void frame(long recolorCpu, long restoreCpu) {
        timing.frameBegin();
        timing.begin(ChromaTiming.Part.RECOLOR);
        timing.end(ChromaTiming.Part.RECOLOR);
        timing.recolorCpu(recolorCpu);
        timing.begin(ChromaTiming.Part.RESTORE);
        timing.end(ChromaTiming.Part.RESTORE);
        timing.frameEnd(restoreCpu);
        if (timing.collecting()) timing.collect(1024, "blocks");
    }

    @Test
    void aFrameThatIsNotMeasuredMakesNoTimer() {
        frame(5, 6);
        assertTrue(timers.isEmpty());
        assertFalse(timing.collecting());
    }

    @Test
    void theTimesGoToTheSinkAfterTheLastFrameHasItsGpuTimes() {
        timing.start(2);
        frame(10, 1);
        frame(20, 2);
        assertEquals(4, timers.size(), "two timers in each measured frame");
        assertTrue(timers.get(0).began && timers.get(0).ended && timers.get(1).began && timers.get(1).ended);
        assertTrue(timing.collecting(), "the GPU gave no time yet");

        timers.get(0).nanos = 100;
        timers.get(1).nanos = 200;
        frame(0, 0);
        assertNull(logged, "the second frame still waits for its GPU times");
        assertTrue(timers.get(0).closed && timers.get(1).closed && !timers.get(2).closed);

        timers.get(2).nanos = 300;
        timers.get(3).nanos = 400;
        frame(0, 0);
        assertEquals(4, timers.size(), "frames after the measurement are not measured");
        assertEquals(2, logged.size());
        assertArrayEquals(new long[]{11, 100, 200}, logged.get(0));
        assertArrayEquals(new long[]{22, 300, 400}, logged.get(1));
        assertEquals(1024, loggedBytes);
        assertEquals("blocks", loggedAtlases);
        assertFalse(timing.collecting());
    }

    @Test
    void aLateRecolorTimeHoldsTheFrame() {
        timing.start(1);
        frame(1, 1);
        timers.get(1).nanos = 7;
        frame(0, 0);
        assertNull(logged, "the recolor time did not come yet");
        timers.get(0).nanos = 3;
        frame(0, 0);
        assertArrayEquals(new long[]{2, 3, 7}, logged.get(0));
    }

    @Test
    void aNewMeasurementClosesTheTimersOfTheLastOne() {
        timing.start(3);
        frame(1, 1);
        timing.start(1);
        assertTrue(timers.get(0).closed && timers.get(1).closed);
        assertFalse(timing.collecting());
        frame(4, 4);
        timers.get(2).nanos = 1;
        timers.get(3).nanos = 2;
        frame(0, 0);
        assertEquals(1, logged.size());
        assertArrayEquals(new long[]{8, 1, 2}, logged.get(0));
    }
}
