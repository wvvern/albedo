package dev.albedo.legacy;

import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import dev.albedo.core.dev.Harness;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundCategory;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.IntBuffer;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;

/** Development-only: what the test harness needs from 1.8.9 (see {@link Harness}). */
final class LegacyHarnessHost implements Harness.Host {
    @Override
    public boolean inWorld() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.theWorld != null && mc.thePlayer != null;
    }

    @Override
    public void openWorld(String name) {
        // Loads the save if it exists; the settings are only used to create it.
        WorldSettings settings = new WorldSettings(42L, WorldSettings.GameType.CREATIVE, false, false, WorldType.FLAT).enableCommands();
        Minecraft.getMinecraft().launchIntegratedServer(name, name, settings);
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
                List<EntityPlayerMP> players = server.getConfigurationManager().playerEntityList;
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
        if (OpenGlHelper.isFramebufferEnabled()) mc.getFramebuffer().bindFramebuffer(true);
        final IntBuffer pixels = BufferUtils.createIntBuffer(w * h);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 4);
        GL11.glReadPixels(0, 0, w, h, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, pixels);
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

    @Override
    public void quit() {
        Minecraft.getMinecraft().shutdown();
    }

    @Override
    public String loadSkin(File png, String hash, boolean cape) throws IOException {
        Minecraft mc = Minecraft.getMinecraft();
        File cache = ((LegacyHooks.SkinCache) mc.getSkinManager()).albedo$skinCacheDir();
        File file = new File(new File(cache, hash.substring(0, 2)), hash);
        Files.createDirectories(file.getParentFile().toPath());
        Files.copy(png.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        // The hash is the last part of the URL, which the game does not read because the cache file exists.
        MinecraftProfileTexture texture = new MinecraftProfileTexture("http://127.0.0.1:9/" + hash, null);
        return mc.getSkinManager().loadSkin(texture, cape ? MinecraftProfileTexture.Type.CAPE : MinecraftProfileTexture.Type.SKIN).toString();
    }

    @Override
    public void releaseTexture(String id) {
        Minecraft.getMinecraft().getTextureManager().deleteTexture(new ResourceLocation(id));
    }

    @Override
    public void showTexture(String id) {
        ResourceLocation texture = id.equals("steve") ? DefaultPlayerSkin.getDefaultSkinLegacy() : new ResourceLocation(id);
        Minecraft.getMinecraft().displayGuiScreen(new GuiTexture(texture));
    }

    @Override
    public void quiet() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.gameSettings.getSoundLevel(SoundCategory.MASTER) != 0f) mc.gameSettings.setSoundLevel(SoundCategory.MASTER, 0f);
        mc.gameSettings.pauseOnLostFocus = false;
        // The view bobbing of 1.8.9 tilts the camera of a falling player by up to 10 degrees, so that the camera does not look where the harness aims.
        mc.gameSettings.viewBobbing = false;
    }

    @Override
    public void setFov(int degrees) {
        Minecraft.getMinecraft().gameSettings.fovSetting = degrees;
    }

    @Override
    public void look(float yaw, float pitch) {
        net.minecraft.client.entity.EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player == null) return;
        player.rotationYaw = player.prevRotationYaw = yaw;
        player.rotationPitch = player.prevRotationPitch = pitch;
    }

    @Override
    public boolean drawSharpSky(dev.albedo.core.sky.SkyImage sky, float[] block, int width, int height, int repeat, float[] view, File png,
                                Harness.SkyDone done) {
        return LegacySharpSky.drawEquirect(sky, block, width, height, repeat, view, png, done);
    }

    @Override
    public int framesPerSecond() {
        return Minecraft.getDebugFPS();
    }

    @Override
    public void uncapFrameRate() {
        Minecraft mc = Minecraft.getMinecraft();
        // The option value 260 is "Unlimited".
        mc.gameSettings.limitFramerate = 260;
        mc.gameSettings.enableVsync = false;
        org.lwjgl.opengl.Display.setVSyncEnabled(false);
    }

    @Override
    public void resizeWindow(int width, int height) {
        Minecraft mc = Minecraft.getMinecraft();
        try {
            org.lwjgl.opengl.Display.setLocation(0, 0);
            org.lwjgl.opengl.Display.setDisplayMode(new org.lwjgl.opengl.DisplayMode(width, height));
        } catch (org.lwjgl.LWJGLException e) {
            Harness.log("! cannot resize the window: " + e);
        }
        ((dev.albedo.legacy.mixin.MinecraftAccessor) mc).albedo$resize(org.lwjgl.opengl.Display.getWidth(), org.lwjgl.opengl.Display.getHeight());
    }

    @Override
    public int[] frameSize() {
        Minecraft mc = Minecraft.getMinecraft();
        return new int[]{mc.displayWidth, mc.displayHeight};
    }

    /** Reads the clouds texture back from the GPU, because a hidden look makes all of its pixels transparent. */
    @Override
    public boolean cloudsDrawn() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.gameSettings.shouldRenderClouds() == 0) return false;
        net.minecraft.client.renderer.texture.ITextureObject clouds =
                mc.getTextureManager().getTexture(new ResourceLocation("textures/environment/clouds.png"));
        if (clouds == null) return false;
        net.minecraft.client.renderer.GlStateManager.bindTexture(clouds.getGlTextureId());
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
