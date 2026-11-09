package dev.albedo.legacy;

import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import dev.albedo.core.dev.Harness;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundCategory;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;

import java.io.File;
import java.io.IOException;
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

    @Override
    public void screenshot(File png) {
        Minecraft mc = Minecraft.getMinecraft();
        // saveScreenshot writes <dir>/screenshots/<name>; move it next to the other harness output.
        File dir = png.getParentFile();
        ScreenShotHelper.saveScreenshot(dir, png.getName(), mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
        File written = new File(new File(dir, "screenshots"), png.getName());
        png.delete();
        Harness.log(written.renameTo(png) ? "saved " + png.getName() : "! screenshot failed: " + png.getName());
    }

    @Override
    public void setHudVisible(boolean visible) {
        Minecraft.getMinecraft().gameSettings.hideGUI = !visible;
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
    }

    @Override
    public void setFov(int degrees) {
        Minecraft.getMinecraft().gameSettings.fovSetting = degrees;
    }

    @Override
    public boolean drawSharpSky(dev.albedo.core.sky.SkyImage sky, float time, int width, int height, int repeat, File png, Harness.SkyDone done) {
        return LegacySharpSky.drawEquirect(sky, time, width, height, repeat, png, done);
    }
}
