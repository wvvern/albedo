package dev.albedo.core.texture;

import dev.albedo.core.shader.ShaderState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChromaFramesTest {
    /** An atlas of the fake game: its GPU texture is a number, and a reload gives a new one. */
    private static final class Atlas {
        int texture;
        ChromaFrames.Size size = new ChromaFrames.Size(4, 4, 1);

        Atlas(int texture) {
            this.texture = texture;
        }
    }

    private final List<String> events = new ArrayList<String>();
    private final Atlas atlas = new Atlas(1);
    private final Map<Object, Atlas> atlases = new LinkedHashMap<Object, Atlas>();
    private boolean running = true;
    private int paints;
    private int stops;
    private int warnings;
    /** The texture whose recolor throws, or 0. */
    private int failTexture;
    private final List<long[]> logged = new ArrayList<long[]>();

    private final ChromaFrames.Host host = new ChromaFrames.Host() {
        @Override public boolean running() { return running; }

        @Override public void stopGpu() { stops++; }

        @Override public void frameDrawn(double seconds) { events.add("drawn"); }

        @Override public int texturePaints() { return paints; }

        @Override public void info(String message) {}

        @Override public void warn(String message, Throwable error) { warnings++; }

        @Override public boolean texels(String location, int w, int h, int[] out) {
            Arrays.fill(out, 0, w * h, 0x01000000);
            return true;
        }
    };

    private final class Target implements ChromaFrames.Target<Atlas> {
        final int texture;

        Target(int texture) {
            this.texture = texture;
        }

        @Override public boolean sameTexture(Atlas a) { return a.texture == texture; }

        @Override public void texels(int[][] levels) { events.add("texels " + texture); }

        @Override public void recolor(float[] turns) {
            if (texture == failTexture) throw new IllegalStateException("lost device");
            events.add("recolor " + texture);
        }

        @Override public void restore() { events.add("restore " + texture); }

        @Override public long bytes() { return 8; }

        @Override public void close() { events.add("close " + texture); }
    }

    private final ChromaFrames.Backend<Atlas> backend = new ChromaFrames.Backend<Atlas>() {
        @Override public ShaderState compile() { return ShaderState.READY; }

        @Override public ChromaFrames.Size size(Atlas a) { return a.size; }

        @Override public ChromaFrames.Target<Atlas> create(Atlas a, ChromaFrames.Size size) {
            events.add("create " + a.texture);
            return new Target(a.texture);
        }

        @Override public ChromaTiming.GpuTimer timer() {
            return new ChromaTiming.GpuTimer() {
                @Override public void begin() {}

                @Override public void end() {}

                @Override public long nanos() { return 0; }

                @Override public void close() {}
            };
        }

        @Override public Object layout(Atlas a) { return a; }

        @Override public void sprites(String atlasName, Atlas a, ChromaTexels.Atlas texels) {
            texels.sprite("block/stone", 0, 0, 2, 2, 0);
        }
    };

    private final ChromaFrames<Atlas> frames = new ChromaFrames<Atlas>(backend, host, (rows, bytes, names) -> logged.addAll(rows));

    ChromaFramesTest() {
        atlases.put("blocks", atlas);
    }

    private void start() {
        frames.frameStart(() -> atlases);
    }

    /** Runs one whole frame and forgets its events. */
    private void frame() {
        start();
        frames.frameEnd();
        events.clear();
    }

    @Test
    void aFrameRecolorsAtTheStartAndRestoresAtTheEnd() {
        start();
        frames.frameEnd();
        assertEquals(Arrays.asList("create 1", "texels 1", "recolor 1", "drawn", "restore 1"), events);
        assertTrue(frames.moving());
    }

    @Test
    void aStartRestoresTheLastFrameBeforeItRecolors() {
        start();
        events.clear();
        start();
        assertEquals(Arrays.asList("restore 1", "recolor 1"), events);
    }

    @Test
    void aFailureLatchesTheBrokenPassAndStopsTheCoreOnce() {
        frame();
        failTexture = 1;
        start();
        assertTrue(frames.broken());
        assertFalse(frames.gpuHue());
        assertFalse(frames.moving());
        assertEquals(1, stops);
        assertEquals(1, warnings);
        events.clear();
        failTexture = 0;
        start();
        frames.frameEnd();
        assertEquals(Collections.singletonList("drawn"), events);
        assertEquals(1, stops);
        assertFalse(frames.time(3));
    }

    @Test
    void aFailedRecolorRestoresTheTargetsThatItRecolored() {
        atlases.put("items", new Atlas(2));
        frame();
        failTexture = 2;
        start();
        assertEquals(Arrays.asList("restore 1", "close 1", "close 2"), events.subList(1, events.size()));
        assertEquals("recolor 1", events.get(0));
    }

    @Test
    void aPaintChangeSendsTheTexelsAgainIntoTheSameTarget() {
        frame();
        paints++;
        start();
        assertEquals(Arrays.asList("texels 1", "recolor 1"), events);
    }

    @Test
    void aNewTextureMakesANewTarget() {
        frame();
        atlas.texture = 2;
        start();
        assertEquals(Arrays.asList("create 2", "texels 2", "close 1", "recolor 2"), events);
    }

    @Test
    void anAtlasWithNoSizeBuildsInTheNextFrame() {
        ChromaFrames.Size size = atlas.size;
        atlas.size = null;
        start();
        assertFalse(frames.moving());
        atlas.size = size;
        start();
        assertEquals(Arrays.asList("create 1", "texels 1", "recolor 1"), events);
    }

    @Test
    void aMissingAtlasClosesItsTarget() {
        frame();
        atlases.clear();
        start();
        assertEquals(Collections.singletonList("close 1"), events);
        assertFalse(frames.moving());
    }

    @Test
    void aStoppedPassClosesItsTargetsAndBuildsAgainWhenItRuns() {
        frame();
        running = false;
        start();
        assertEquals(Collections.singletonList("close 1"), events);
        assertFalse(frames.gpuHue());
        running = true;
        events.clear();
        start();
        assertEquals(Arrays.asList("create 1", "texels 1", "recolor 1"), events);
    }

    @Test
    void aTimedRunGivesItsRowsAfterTheLastFrame() {
        assertFalse(frames.time(2));
        frame();
        assertTrue(frames.time(2));
        frame();
        assertTrue(logged.isEmpty());
        frame();
        assertEquals(2, logged.size());
    }
}
