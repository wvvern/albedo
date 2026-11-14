package dev.albedo.legacy;

import dev.albedo.core.Keys;
import dev.albedo.core.dev.Harness;
import dev.albedo.core.dev.HookCounter;
import dev.albedo.core.sky.SkyImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.IntBuffer;
import javax.imageio.ImageIO;
import java.util.List;

/** Development-only: what the test harness needs from the game (see {@link Harness}). */
final class LegacyHarnessHost implements Harness.Host {
    /** The Windows virtual key of a typed character that has no key of its own. */
    private static final int VK_PACKET = 0xE7;
    /** Parts of the lParam of a Windows key message: repeat count 1, the extended key, the bits of a key that goes up, the scancode. */
    private static final long REPEAT_ONE = 1L, EXTENDED_KEY = 1L << 24, KEY_UP_BITS = 3L << 30;
    private static final int SCANCODE_SHIFT = 16;

    @Override
    public boolean inWorld() {
        return Game.world() != null && Game.player() != null;
    }

    @Override
    public void openWorld(String name) {
        Game.openFlatWorld(name);
    }

    @Override
    public void runCommand(final String command) {
        final MinecraftServer server = Minecraft.getMinecraft().getIntegratedServer();
        if (server == null) {
            Harness.log("! no singleplayer server");
            return;
        }
        server.addScheduledTask(new Runnable() {
            @Override
            public void run() {
                List<EntityPlayerMP> players = Game.players(server);
                if (players.isEmpty()) Harness.log("! no player for: " + command);
                else server.getCommandManager().executeCommand(players.get(0), command);
            }
        });
    }

    /** Reads the last frame now and writes the PNG on a thread of its own, so that screenshots of frames in a row stay one frame apart. */
    @Override
    public void screenshot(final File png) {
        Minecraft mc = Minecraft.getMinecraft();
        final int w = mc.displayWidth, h = mc.displayHeight;
        final IntBuffer pixels = readFrame(w, h);
        new Thread(new Runnable() {
            @Override
            public void run() {
                BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                // The GL rows go from the bottom up.
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) image.setRGB(x, y, pixels.get((h - 1 - y) * w + x));
                }
                try {
                    ImageIO.write(image, "png", png);
                    Harness.log("saved " + png.getName());
                } catch (IOException e) {
                    Harness.log("! screenshot failed: " + png.getName() + ": " + e);
                }
            }
        }, "albedo-screenshot").start();
    }

    @Override
    public void framePixels(Harness.FramePixels done) {
        Minecraft mc = Minecraft.getMinecraft();
        int w = mc.displayWidth, h = mc.displayHeight;
        IntBuffer pixels = readFrame(w, h);
        int[] argb = new int[w * h];
        // The GL rows go from the bottom up.
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) argb[y * w + x] = pixels.get((h - 1 - y) * w + x) | 0xFF000000;
        }
        done.accept(w, h, argb);
    }

    /** The BGRA pixels of the frame of the game, from the bottom row up. */
    private static IntBuffer readFrame(int w, int h) {
        Minecraft mc = Minecraft.getMinecraft();
        if (OpenGlHelper.isFramebufferEnabled()) mc.getFramebuffer().bindFramebuffer(true);
        IntBuffer pixels = BufferUtils.createIntBuffer(w * h);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 4);
        GL11.glReadPixels(0, 0, w, h, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, pixels);
        return pixels;
    }

    @Override
    public void setHudVisible(boolean visible) {
        Minecraft.getMinecraft().gameSettings.hideGUI = !visible;
    }

    @Override
    public boolean timeChroma(int frames) {
        return LegacyChroma.time(frames);
    }

    @Override
    public void closeScreen() {
        Minecraft.getMinecraft().displayGuiScreen(null);
    }

    /** LWJGL 2 reads the mouse from the system with no queue that the harness can add to, so it cannot click as a real mouse. */
    @Override
    public boolean mouseButton(int guiX, int guiY, boolean down) {
        return false;
    }

    /**
     * Calls the handler of the key messages of the LWJGL 2 window, as Windows does, where LWJGL keeps the state of
     * Control itself; the LWJGL 2 key code is the scancode, with 0x80 for an extended key.
     */
    @Override
    public boolean key(Keys.Key key, boolean down, boolean ctrl) {
        int virtualKey = virtualKey(key.lwjgl2);
        if (virtualKey < 0) return false;
        long lParam = REPEAT_ONE | (long) (key.lwjgl2 & 0x7F) << SCANCODE_SHIFT | (key.lwjgl2 >= 0x80 ? EXTENDED_KEY : 0L)
                | (down ? 0L : KEY_UP_BITS);
        return windowMessage("handleKeyButton", virtualKey, lParam);
    }

    /**
     * Calls the handler of the character messages of the LWJGL 2 window, as Windows does after a key goes down; Windows
     * sends a character with no key between a press and a release of VK_PACKET.
     */
    @Override
    public boolean character(char c, boolean afterKey) {
        if (!afterKey && !windowMessage("handleKeyButton", VK_PACKET, REPEAT_ONE)) return false;
        boolean sent = windowMessage("handleChar", c, REPEAT_ONE);
        if (!afterKey) windowMessage("handleKeyButton", VK_PACKET, REPEAT_ONE | KEY_UP_BITS);
        return sent;
    }

    @Override
    public boolean settingsOpen() {
        return Minecraft.getMinecraft().currentScreen instanceof GuiSettings;
    }

    private static boolean windowMessage(String handler, long wParam, long lParam) {
        try {
            Field field = Display.class.getDeclaredField("display_impl");
            field.setAccessible(true);
            Object display = field.get(null);
            Method method = display.getClass().getDeclaredMethod(handler, long.class, long.class, long.class);
            method.setAccessible(true);
            method.invoke(display, wParam, lParam, Minecraft.getSystemTime());
            return true;
        } catch (ReflectiveOperationException e) {
            Harness.log("! the LWJGL 2 window has no Windows key messages: " + e);
            return false;
        }
    }

    /** The Windows virtual key that LWJGL 2 maps to the key code, or -1. */
    private static int virtualKey(int lwjglCode) {
        try {
            Method map = Class.forName("org.lwjgl.opengl.WindowsKeycodes").getDeclaredMethod("mapVirtualKeyToLWJGLCode", int.class);
            map.setAccessible(true);
            for (int vk = 1; vk < 256; vk++) if ((Integer) map.invoke(null, vk) == lwjglCode) return vk;
        } catch (ReflectiveOperationException e) {
            Harness.log("! LWJGL 2 has no Windows key codes: " + e);
        }
        return -1;
    }

    @Override
    public void quit() {
        Minecraft.getMinecraft().shutdown();
    }

    @Override
    public void quiet() {
        Minecraft mc = Minecraft.getMinecraft();
        Game.mute();
        mc.gameSettings.pauseOnLostFocus = false;
        // The view bobbing of 1.8.9 tilts the camera of a falling player by up to 10 degrees, so that the camera does not look where the harness aims.
        mc.gameSettings.viewBobbing = false;
    }

    @Override
    public void setFov(int degrees) {
        Minecraft.getMinecraft().gameSettings.fovSetting = degrees;
    }

    @Override
    public boolean drawSharpSky(SkyImage sky, float[] block, int width, int height, int repeat, float[] view, File png,
                                Harness.SkyDone done) {
        return LegacySharpSky.drawEquirect(sky, block, width, height, repeat, view, png, done);
    }

    @Override
    public List<HookCounter> hookCounters() {
        return LegacyHooks.COUNTERS;
    }

    /** Reads the clouds texture back from the GPU, because a hidden look makes all of its pixels transparent. */
    @Override
    public boolean cloudsDrawn() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.gameSettings.shouldRenderClouds() == 0) return false;
        ITextureObject clouds = mc.getTextureManager().getTexture(new ResourceLocation("textures/environment/clouds.png"));
        if (clouds == null) return false;
        GlStateManager.bindTexture(clouds.getGlTextureId());
        int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        IntBuffer pixels = BufferUtils.createIntBuffer(width * height);
        GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, pixels);
        for (int i = 0; i < width * height; i++) {
            if ((pixels.get(i) >>> 24) != 0) return true;
        }
        return false;
    }
}
