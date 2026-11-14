package dev.albedo.core.dev;

import dev.albedo.core.dev.HarnessQueue.Command;
import dev.albedo.core.dev.HarnessQueue.Outcome;
import dev.albedo.core.dev.HarnessQueue.Phase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HarnessQueueTest {
    @TempDir
    File dir;

    private final HarnessQueue queue = new HarnessQueue();
    private final FakeRunner runner = new FakeRunner();

    /** Runs the verbs of the queue: {@code wait}, {@code waitworld} and {@code frames} start their phase, {@code sky} retries. */
    private final class FakeRunner implements HarnessQueue.Runner {
        boolean inWorld;
        final List<String> ran = new ArrayList<String>();

        @Override
        public boolean inWorld() {
            return inWorld;
        }

        @Override
        public Outcome run(Command command) {
            ran.add(command.verb + (command.arg.isEmpty() ? "" : " " + command.arg));
            switch (command.verb) {
                case "wait": queue.waitTicks(Integer.parseInt(command.arg)); return Outcome.DONE;
                case "waitworld": queue.waitWorld(); return Outcome.DONE;
                case "frames": queue.burst(Integer.parseInt(command.arg), "f"); return Outcome.DONE;
                case "sky": command.tries(Integer.parseInt(command.arg)); return Outcome.RETRY;
                case "boom": throw new IllegalStateException("boom");
                default: return Outcome.DONE;
            }
        }
    }

    @BeforeEach
    void openLog() {
        HarnessLog.open(dir);
    }

    private List<String> log() throws IOException {
        File f = new File(dir, "log.txt");
        return f.isFile() ? Files.readAllLines(f.toPath(), StandardCharsets.UTF_8) : Collections.<String>emptyList();
    }

    private void ticks(int count) {
        for (int i = 0; i < count; i++) queue.advance(runner);
    }

    @Test
    void blankAndCommentLinesDoNotRun() throws IOException {
        queue.addAll(Arrays.asList("", "   ", "# note", "  a  x ", "b"));
        ticks(1);
        assertEquals(Arrays.asList("a x", "b"), runner.ran);
        assertEquals(Arrays.asList("> a  x", "> b"), log());
    }

    @Test
    void waitHoldsTheNextCommandsForItsTicks() {
        queue.addAll(Arrays.asList("wait 2", "a"));
        ticks(1);
        assertEquals(Phase.WAIT_TICKS, queue.phase());
        ticks(2);
        assertEquals(Collections.singletonList("wait 2"), runner.ran);
        ticks(1);
        assertEquals(Arrays.asList("wait 2", "a"), runner.ran);
        assertEquals(Phase.RUN, queue.phase());
    }

    @Test
    void waitZeroDoesNotHoldTheQueue() {
        queue.addAll(Arrays.asList("wait 0", "a"));
        ticks(1);
        assertEquals(Arrays.asList("wait 0", "a"), runner.ran);
    }

    @Test
    void aNegativeWaitHoldsTheQueueForGood() {
        queue.addAll(Arrays.asList("wait -1", "a"));
        ticks(100);
        assertEquals(Collections.singletonList("wait -1"), runner.ran);
        assertEquals(Phase.WAIT_TICKS, queue.phase());
    }

    @Test
    void waitWorldRunsTheNextCommandsInTheTickOfTheWorld() {
        queue.addAll(Arrays.asList("waitworld", "a"));
        ticks(3);
        assertEquals(Phase.WAIT_WORLD, queue.phase());
        runner.inWorld = true;
        ticks(1);
        assertEquals(Arrays.asList("waitworld", "a"), runner.ran);
    }

    @Test
    void aBurstHoldsTheQueueUntilItsLastFrame() {
        queue.addAll(Arrays.asList("frames 2", "a"));
        ticks(3);
        assertEquals(Phase.BURST, queue.phase());
        assertEquals("f-00.png", queue.nextFrame());
        ticks(1);
        assertEquals("f-01.png", queue.nextFrame());
        assertNull(queue.nextFrame());
        ticks(1);
        assertEquals(Arrays.asList("frames 2", "a"), runner.ran);
    }

    @Test
    void aBurstNeedsOneFrameOrMore() {
        assertThrows(IllegalArgumentException.class, () -> queue.burst(0, "f"));
        assertEquals(Phase.RUN, queue.phase());
    }

    @Test
    void aRetryRunsEachTickUntilNoTriesAreLeft() throws IOException {
        queue.addAll(Arrays.asList("sky 2", "a"));
        ticks(1);
        assertEquals(Phase.WAIT_TICKS, queue.phase());
        ticks(5);
        assertEquals(Arrays.asList("sky 2", "sky 2", "sky 2", "a"), runner.ran);
        assertEquals(Arrays.asList("> sky 2", "! sky: this version has no sharp sky shader, or it does not compile", "> a"), log());
    }

    @Test
    void anExceptionFailsItsCommandAndTheNextOnesRun() throws IOException {
        queue.addAll(Arrays.asList("boom", "a"));
        ticks(1);
        assertEquals(Arrays.asList("> boom", "! java.lang.IllegalStateException: boom", "> a"), log());
    }
}
