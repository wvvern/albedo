package dev.albedo.legacy;

import dev.albedo.core.ui.ReloadProgress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;

/**
 * A progress bar while a full resource reload blocks the client thread, drawn without LoadingScreenRenderer because
 * Patcher can turn that off.
 */
final class ReloadScreen {
    private static final long FRAME_MS = 100;
    private static final int BAR_WIDTH = 100, BAR_HEIGHT = 2;
    private static final int TITLE_COLOR = 0xFFFFFF, DETAIL_COLOR = 0xA0A0A0, BAR_BACK = 0xFF808080, BAR_DONE = 0xFF80FF80;

    private static boolean active;
    private static ReloadProgress progress = new ReloadProgress();
    private static long lastFrame;

    private ReloadScreen() {}

    /** Runs {@code reload} on the client thread and shows the screen until it ends. */
    static void run(Runnable reload) {
        active = true;
        progress = new ReloadProgress();
        draw(true);
        try {
            reload.run();
        } finally {
            active = false;
        }
    }

    static void begin(int listeners) {
        progress.begin(listeners);
    }

    static void step() {
        progress.step();
        draw(true);
    }

    static void load() {
        progress.load();
        draw(false);
    }

    private static void draw(boolean force) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!active || !mc.isCallingFromMinecraftThread() || Game.font() == null) return;
        long now = Minecraft.getSystemTime();
        if (!force && now - lastFrame < FRAME_MS) return;
        lastFrame = now;

        Framebuffer fb = mc.getFramebuffer();
        fb.bindFramebuffer(true);
        ScaledResolution sr = new ScaledResolution(mc);
        int w = sr.getScaledWidth(), h = sr.getScaledHeight();
        GlStateManager.clearColor(0f, 0f, 0f, 1f);
        GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.loadIdentity();
        GlStateManager.ortho(0.0D, w, h, 0.0D, 1000.0D, 3000.0D);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.loadIdentity();
        GlStateManager.translate(0.0F, 0.0F, -2000.0F);
        GlStateManager.disableLighting();
        GlStateManager.disableFog();
        GlStateManager.disableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.color(1f, 1f, 1f, 1f);

        Game.drawBackground(w, h);

        FontRenderer font = Game.font();
        String title = progress.title();
        font.drawStringWithShadow(title, (w - font.getStringWidth(title)) / 2f, h / 2f - 20, TITLE_COLOR);
        String detail = progress.detail();
        font.drawStringWithShadow(detail, (w - font.getStringWidth(detail)) / 2f, h / 2f - 6, DETAIL_COLOR);
        int barX = w / 2 - BAR_WIDTH / 2, barY = h / 2 + 10;
        Gui.drawRect(barX, barY, barX + BAR_WIDTH, barY + BAR_HEIGHT, BAR_BACK);
        Gui.drawRect(barX, barY, barX + Math.round(BAR_WIDTH * progress.progress()), barY + BAR_HEIGHT, BAR_DONE);
        GlStateManager.enableDepth();

        fb.unbindFramebuffer();
        fb.framebufferRender(mc.displayWidth, mc.displayHeight);
        mc.updateDisplay();
    }
}
