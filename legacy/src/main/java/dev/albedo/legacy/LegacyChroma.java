package dev.albedo.legacy;

import dev.albedo.core.Albedo;
import dev.albedo.core.config.Chroma;
import dev.albedo.core.dev.Harness;
import dev.albedo.core.sky.SkyClock;
import dev.albedo.core.sky.SkyShaderSource;
import dev.albedo.core.texture.ChromaTexels;
import dev.albedo.legacy.mixin.TextureMapAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GLContext;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Moves the Chroma colors of the block atlas in each frame on 1.8.9 and 1.12.2 with an OpenGL 2.0 program and a framebuffer: at the
 * start of a frame the pass draws the hue of each moving texel into each mip level of the atlas, and at the end of the
 * frame the atlas gets back the colors that Albedo painted, so that the game and Albedo always write into those colors.
 */
public final class LegacyChroma {
    private enum Compile { NOT_STARTED, READY, FAILED }

    private static Compile state = Compile.NOT_STARTED;
    private static int program;
    private static int turnsUniform;
    private static int sizeUniform;
    private static int framebuffer;
    private static final FloatBuffer TURNS = BufferUtils.createFloatBuffer(Chroma.TURN_FLOATS);
    private static final float[] TURN_VALUES = new float[Chroma.TURN_FLOATS];
    /** The OptiFine Multitexture setting of the last frame, with which the blocks draw from a texture of each sprite. */
    private static boolean multiTexture;

    /** The GL texture of the atlas and its size, and the pristine copy and the texels, or 0 without moving texels. */
    private static int live;
    private static int[] size;
    private static int pristine;
    private static int texels;
    /** The texture paints of the core when the texels were made, so that a change makes them again. */
    private static int paints = Integer.MIN_VALUE;
    /** True from the pass at the start of a frame until the atlas gets back its painted colors. */
    private static boolean recolored;
    /** True after the pass failed, so that it does not run again in this game. */
    private static boolean broken;
    /** The clock time of the colors of this frame. */
    private static double seconds;

    /** The frames that the harness still measures, the GPU queries of the measured frames and their times. */
    private static int timedLeft;
    private static final List<int[]> PENDING = new ArrayList<int[]>();
    private static final List<long[]> TIMES = new ArrayList<long[]>();
    private static int[] frameQueries;
    private static long cpuStart;

    private LegacyChroma() {}

    /** Whether the pass adds the hue of the moving sprites of the block atlas. */
    static boolean gpuHue() {
        Albedo sc = Albedo.get();
        return !broken && sc != null && !sc.chromaStill();
    }

    /** Draws the colors of this frame into the atlas, before the frame draws anything. */
    public static void frameStart() {
        Albedo sc = Albedo.get();
        if (sc == null || broken) return;
        // The end of the last frame did not run when its render stopped early, so its colors must not go into the pristine copy.
        restore();
        // The pass cannot reach the texture of each sprite that OptiFine Multitexture draws, so Chroma is then off.
        boolean single = OptiFine.multiTexture();
        if (single != multiTexture) {
            multiTexture = single;
            sc.chromaGpu(!single);
        }
        if (sc.chromaStill()) {
            close();
            return;
        }
        long start = System.nanoTime();
        try {
            TextureMap atlas = Minecraft.getMinecraft().getTextureMapBlocks();
            if (atlas == null) return;
            int now = Albedo.texturePaints();
            if (now != paints || live != 0 && !Arrays.equals(size, sizeOf(atlas.getGlTextureId()))) build(atlas);
            paints = now;
            if (live == 0) return;
            seconds = SkyClock.longSeconds();
            Chroma.turns(seconds, TURN_VALUES);
            if (timedLeft > 0) frameQueries = new int[]{GL15.glGenQueries(), GL15.glGenQueries()};
            begin(0);
            recolor();
            end();
            recolored = true;
        } catch (RuntimeException e) {
            stop(e);
        }
        cpuStart = System.nanoTime() - start;
    }

    /** Gives the atlas back its painted colors at the end of a frame, after the harness saw the frame. */
    public static void frameEnd() {
        Albedo sc = Albedo.get();
        if (sc != null) sc.frameDrawn(recolored ? seconds : SkyClock.longSeconds());
        long start = System.nanoTime();
        begin(1);
        restore();
        end();
        if (frameQueries != null) {
            PENDING.add(frameQueries);
            TIMES.add(new long[]{cpuStart + System.nanoTime() - start});
            frameQueries = null;
            timedLeft--;
        }
        if (!PENDING.isEmpty()) collectTimes();
    }

    /** Gives the atlas back its painted colors before Albedo or the game writes new sprites into it in the middle of a frame. */
    public static void restore() {
        if (!recolored) return;
        recolored = false;
        try {
            withState(new Runnable() {
                @Override
                public void run() {
                    for (int level = 0; level < size[2]; level++) copy(pristine, live, level);
                }
            });
        } catch (RuntimeException e) {
            stop(e);
        }
    }

    /** Makes the texels of the atlas from its sprites, and keeps the GPU textures only while some texels move. */
    private static void build(TextureMap atlas) {
        int[] now = sizeOf(atlas.getGlTextureId());
        ChromaTexels.Atlas built = new ChromaTexels.Atlas(now[0], now[1]);
        for (TextureAtlasSprite sprite : ((TextureMapAccessor) atlas).albedo$sprites().values()) {
            built.sprite(sprite.getIconName(), sprite.getOriginX(), sprite.getOriginY(), sprite.getIconWidth(), sprite.getIconHeight(), 0);
        }
        if (!built.moving()) {
            close();
            return;
        }
        if (!compile()) throw new IllegalStateException("the Chroma shader does not compile");
        if (live != atlas.getGlTextureId() || !Arrays.equals(size, now)) {
            close();
            live = atlas.getGlTextureId();
            size = now;
            pristine = texture();
            texels = texture();
            Albedo.get().platform().info(String.format(Locale.ROOT, "Moves the Chroma colors of the block atlas on the GPU: %dx%d with %d mip levels,"
                    + " %.1f MiB", size[0], size[1], size[2], bytes() / 1048576.0));
        }
        final int[][] levels = built.levels(size[2]);
        withState(new Runnable() {
            @Override
            public void run() {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, texels);
                GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
                GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
                GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
                GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
                for (int level = 0; level < size[2]; level++) {
                    ByteBuffer bytes = BufferUtils.createByteBuffer(levels[level].length * 4);
                    ChromaTexels.rgba(levels[level], bytes);
                    GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, level, 0, 0, width(level), height(level), GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, bytes);
                }
            }
        });
    }

    /** Copies each mip level of the atlas into the pristine copy, then draws the colors of this frame into the atlas. */
    private static void recolor() {
        withState(new Runnable() {
            @Override
            public void run() {
                for (int level = 0; level < size[2]; level++) copy(live, pristine, level);
                GL11.glDisable(GL11.GL_BLEND);
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                GL11.glDisable(GL11.GL_ALPHA_TEST);
                GL11.glDisable(GL11.GL_CULL_FACE);
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
                GL11.glDisable(GL11.GL_STENCIL_TEST);
                GL11.glColorMask(true, true, true, true);
                GL20.glUseProgram(program);
                TURNS.clear();
                TURNS.put(TURN_VALUES).flip();
                GL20.glUniform4(turnsUniform, TURNS);
                OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, framebuffer);
                for (int level = 0; level < size[2]; level++) {
                    OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER, OpenGlHelper.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, live, level);
                    GL11.glViewport(0, 0, width(level), height(level));
                    GL20.glUniform2f(sizeUniform, width(level), height(level));
                    baseLevel(GL13.GL_TEXTURE1, texels, level);
                    baseLevel(GL13.GL_TEXTURE0, pristine, level);
                    GL11.glBegin(GL11.GL_TRIANGLES);
                    GL11.glVertex2f(-1, -1);
                    GL11.glVertex2f(3, -1);
                    GL11.glVertex2f(-1, 3);
                    GL11.glEnd();
                }
                OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER, OpenGlHelper.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, 0, 0);
                // A framebuffer cannot read a level below the base level, so the copies at the end of the frame need base level 0.
                baseLevel(GL13.GL_TEXTURE1, texels, 0);
                baseLevel(GL13.GL_TEXTURE0, pristine, 0);
            }
        });
    }

    /** Binds a texture to a unit with one level as its base level, which the shader reads as its level 0. */
    private static void baseLevel(int unit, int texture, int level) {
        GL13.glActiveTexture(unit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL, level);
    }

    /** Copies one mip level of a texture into the same level of a texture of the same size, through the framebuffer. */
    private static void copy(int from, int to, int level) {
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, framebuffer);
        OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER, OpenGlHelper.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, from, level);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, to);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, level, 0, 0, 0, 0, width(level), height(level));
        OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER, OpenGlHelper.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, 0, 0);
    }

    /**
     * Runs GL work and then puts back all GL state that it can change: the attribute stack holds the enables, the
     * viewport and the masks that GlStateManager keeps a copy of, and the textures of units 0 and 1 (the lightmap), the
     * active unit, the framebuffer and the program are put back by hand, because some drivers pop only the active unit.
     */
    private static void withState(Runnable work) {
        int framebufferBefore = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int programBefore = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int unitBefore = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int texture0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        int texture1 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL13.glActiveTexture(unitBefore);
        // The attribute stack keeps the draw and read buffers of the bound framebuffer, and GL_BACK of the window (OptiFine
        // Fast Render draws with no framebuffer) is not valid for the framebuffer of the pass, so the push and the pop use it.
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, framebuffer);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            work.run();
        } finally {
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, framebuffer);
            GL11.glPopAttrib();
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, framebufferBefore);
            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture1);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture0);
            GL13.glActiveTexture(unitBefore);
            GL20.glUseProgram(programBefore);
        }
    }

    /** A texture with the size and the mip levels of the atlas and no filter, whose base level each draw sets to the level it reads. */
    private static int texture() {
        final int[] out = new int[1];
        withState(new Runnable() {
            @Override
            public void run() {
                out[0] = GL11.glGenTextures();
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, out[0]);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, size[2] - 1);
                for (int level = 0; level < size[2]; level++) {
                    GL11.glTexImage2D(GL11.GL_TEXTURE_2D, level, GL11.GL_RGBA8, width(level), height(level), 0, GL11.GL_RGBA,
                            GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
                }
            }
        });
        return out[0];
    }

    private static int width(int level) {
        return Math.max(1, size[0] >> level);
    }

    private static int height(int level) {
        return Math.max(1, size[1] >> level);
    }

    private static long bytes() {
        long out = 0;
        for (int level = 0; level < size[2]; level++) out += ChromaTexels.levelBytes(width(level), height(level));
        return out;
    }

    /** The width, height and number of mip levels of the GL texture of the atlas. */
    private static int[] sizeOf(final int texture) {
        final int[] out = new int[3];
        withState(new Runnable() {
            @Override
            public void run() {
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
                out[0] = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
                out[1] = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
                out[2] = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL) + 1;
            }
        });
        // A texture with no mip levels can keep the default largest level of 1000.
        out[2] = Math.max(1, Math.min(out[2], 32 - Integer.numberOfLeadingZeros(Math.max(1, Math.max(out[0], out[1])))));
        return out;
    }

    /** Compiles the program on its first use: GLSL 1.30 with OpenGL 3.0, else GLSL 1.20, and returns true when it compiled. */
    private static boolean compile() {
        if (state != Compile.NOT_STARTED) return state == Compile.READY;
        ContextCapabilities caps = GLContext.getCapabilities();
        state = Compile.FAILED;
        if (!caps.OpenGL20 || !OpenGlHelper.framebufferSupported) {
            Albedo.get().platform().warn("The GPU has no OpenGL 2.0 or no framebuffers, so Chroma is off", null);
            return false;
        }
        SkyShaderSource.Dialect dialect = caps.OpenGL30 ? SkyShaderSource.Dialect.FIXED_130 : SkyShaderSource.Dialect.FIXED_120;
        int p = GL20.glCreateProgram();
        int vertex = 0;
        int fragment = 0;
        try {
            vertex = shader(GL20.GL_VERTEX_SHADER, SkyShaderSource.classic(SkyShaderSource.CHROMA, true, dialect));
            fragment = shader(GL20.GL_FRAGMENT_SHADER, SkyShaderSource.classic(SkyShaderSource.CHROMA, false, dialect));
            GL20.glAttachShader(p, vertex);
            GL20.glAttachShader(p, fragment);
            GL20.glLinkProgram(p);
            if (GL20.glGetProgrami(p, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) throw new IllegalStateException(GL20.glGetProgramInfoLog(p, 32768));
        } catch (IOException | IllegalStateException e) {
            GL20.glDeleteProgram(p);
            Albedo.get().platform().warn("Cannot compile the Chroma shader " + SkyShaderSource.CHROMA + ". Chroma is off.", e);
            return false;
        } finally {
            if (vertex != 0) GL20.glDeleteShader(vertex);
            if (fragment != 0) GL20.glDeleteShader(fragment);
        }
        program = p;
        turnsUniform = GL20.glGetUniformLocation(p, "ChromaTurns");
        sizeUniform = GL20.glGetUniformLocation(p, "ChromaSize");
        final int pristineUniform = GL20.glGetUniformLocation(p, "AlbedoPristine"), texelsUniform = GL20.glGetUniformLocation(p, "AlbedoTexels");
        withState(new Runnable() {
            @Override
            public void run() {
                GL20.glUseProgram(program);
                GL20.glUniform1i(pristineUniform, 0);
                GL20.glUniform1i(texelsUniform, 1);
            }
        });
        framebuffer = OpenGlHelper.glGenFramebuffers();
        state = Compile.READY;
        Albedo.get().platform().info("Compiled the Chroma shader " + SkyShaderSource.CHROMA + " as " + dialect);
        return true;
    }

    /** A compiled shader of a type, or an exception with the log of the GL. */
    private static int shader(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader, 32768);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException(log);
        }
        return shader;
    }

    /** Stops the pass for this game after an error, and lets the core paint the sprites again without Chroma. */
    private static void stop(RuntimeException e) {
        broken = true;
        recolored = false;
        Albedo.get().platform().warn("Cannot move the Chroma colors on the GPU. Chroma is off.", e);
        close();
        Albedo.get().chromaGpu(false);
    }

    private static void close() {
        restore();
        if (pristine != 0) GL11.glDeleteTextures(pristine);
        if (texels != 0) GL11.glDeleteTextures(texels);
        pristine = 0;
        texels = 0;
        live = 0;
        size = null;
        paints = Integer.MIN_VALUE;
    }

    /** Starts the GPU time query of one part of a measured frame. */
    private static void begin(int part) {
        if (frameQueries != null && timed()) GL15.glBeginQuery(GL33.GL_TIME_ELAPSED, frameQueries[part]);
    }

    private static void end() {
        if (frameQueries != null && timed()) GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
    }

    private static boolean timed() {
        ContextCapabilities caps = GLContext.getCapabilities();
        return caps.OpenGL33 || caps.GL_ARB_timer_query;
    }

    /** Measures the next frames for the harness command chromatime, and returns false when the pass does not run. */
    static boolean time(int frames) {
        if (live == 0 || broken) return false;
        // A measurement that did not end yet gives way to the new one, and its queries go.
        for (int[] q : PENDING) deleteQueries(q);
        if (frameQueries != null) deleteQueries(frameQueries);
        frameQueries = null;
        TIMES.clear();
        PENDING.clear();
        timedLeft = frames;
        return true;
    }

    private static void deleteQueries(int[] queries) {
        for (int q : queries) GL15.glDeleteQueries(q);
    }

    /** Takes the GPU times that the GPU gave, and logs all times when the last measured frame has its times. */
    private static void collectTimes() {
        int done = TIMES.size() - PENDING.size();
        for (Iterator<int[]> it = PENDING.iterator(); it.hasNext(); done++) {
            int[] q = it.next();
            long start = 0, end = 0;
            if (timed()) {
                if (GL15.glGetQueryObjecti(q[1], GL15.GL_QUERY_RESULT_AVAILABLE) == GL11.GL_FALSE) return;
                start = GL33.glGetQueryObjecti64(q[0], GL15.GL_QUERY_RESULT);
                end = GL33.glGetQueryObjecti64(q[1], GL15.GL_QUERY_RESULT);
            }
            long[] row = TIMES.get(done);
            TIMES.set(done, new long[]{row[0], start, end});
            deleteQueries(q);
            it.remove();
        }
        if (timedLeft > 0) return;
        Harness.logChromaTimes(TIMES, live == 0 ? 0 : bytes(), "the block atlas");
        TIMES.clear();
    }
}
