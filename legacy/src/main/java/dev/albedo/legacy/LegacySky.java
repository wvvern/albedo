package dev.albedo.legacy;

import dev.albedo.core.Albedo;
import dev.albedo.core.config.SkyDesign;
import dev.albedo.core.dev.SkyCpuTime;
import dev.albedo.core.sky.SkyDayTime;
import dev.albedo.core.sky.SkyImage;
import dev.albedo.legacy.mixin.MinecraftAccessor;
import dev.albedo.core.sky.SkyLight;
import dev.albedo.core.sky.SkyTurn;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.nio.FloatBuffer;

/**
 * Draws the custom sky on a sphere in place of the 1.8.9 sky disc, with {@link LegacySharpSky} when it can, else from
 * one texture of a fixed size that is uploaded on the render thread only when the core has a new image.
 */
public final class LegacySky {
    private static final float RADIUS = 100f;
    private static DynamicTexture texture;
    private static long uploaded = -1;
    /** The display list of the sphere, or -1 before the first draw. */
    private static int sphere = -1;
    /** The matrix of a turning sky for the GL, which each frame fills again. */
    private static final FloatBuffer TURN = BufferUtils.createFloatBuffer(16);

    private LegacySky() {}

    /** The custom sky to draw, or null, also when OptiFine does not draw the sky (Sky off or a shader pack). */
    public static SkyImage image() {
        SkyImage img = Albedo.skyImage();
        return img == null || !LegacySkyPatch.hooked() || !OptiFine.drawsSky() ? null : img;
    }

    public static boolean active() {
        return image() != null;
    }

    public static boolean hidesStars() {
        SkyImage img = image();
        return img != null && img.design().celestial() == SkyDesign.Celestial.HIDE;
    }

    /** The factor of the sky and fog colors for the time of day and the weather. */
    static float brightness(SkyImage img) {
        World world = Minecraft.getMinecraft().theWorld;
        if (world == null) return 1f;
        return SkyLight.of(img.design(), world.getWorldTime(), world.getRainStrength(1f), world.getThunderStrength(1f));
    }

    /** The fog color {r, g, b} near the horizon where the camera looks, or null without a custom sky. */
    public static float[] fogRgb() {
        long start = SkyCpuTime.start();
        SkyImage img = image();
        Entity view = Minecraft.getMinecraft().getRenderViewEntity();
        World world = Minecraft.getMinecraft().theWorld;
        float[] rgb = img == null || view == null || world == null || !world.provider.isSurfaceWorld() ? null
                : img.fogRgb(turn(img), view.rotationYaw, brightness(img));
        SkyCpuTime.add(start);
        return rgb;
    }

    /** Records the day time at the end of each client tick, for the frames until the next tick. */
    static void tick() {
        World world = Minecraft.getMinecraft().theWorld;
        if (world != null) SkyDayTime.tick(world.getWorldTime());
    }

    /** The rotation of the custom sky in this frame, for the sky sphere and the fog. */
    private static SkyTurn turn(SkyImage img) {
        Minecraft mc = Minecraft.getMinecraft();
        float partialTick = ((MinecraftAccessor) mc).albedo$timer().renderPartialTicks;
        return SkyTurn.at(img.design().turning(), mc.theWorld == null ? 0 : SkyDayTime.at(partialTick));
    }

    /**
     * Draws the sky where vanilla draws its disc, which has texturing off and the sky color set.
     *
     * @return false when there is no custom sky, so that the vanilla disc draws
     */
    private static boolean draw() {
        long start = SkyCpuTime.start();
        try {
            return drawImage();
        } finally {
            SkyCpuTime.add(start);
        }
    }

    private static boolean drawImage() {
        SkyImage img = image();
        if (img == null) return false;
        prepare(img);
        LegacySharpSky.prepare(img);
        float k = brightness(img);
        SkyTurn turn = turn(img);
        GlStateManager.pushMatrix();
        if (!turn.still()) {
            TURN.clear();
            TURN.put(turn.matrix()).flip();
            GL11.glMultMatrix(TURN);
        }
        if (!LegacySharpSky.draw(img, sphere, k)) {
            GlStateManager.enableTexture2D();
            GlStateManager.disableFog();
            GlStateManager.disableCull();
            GlStateManager.color(k, k, k, 1f);
            GlStateManager.bindTexture(texture.getGlTextureId());
            GlStateManager.callList(sphere);
            GlStateManager.enableCull();
            GlStateManager.enableFog();
            GlStateManager.disableTexture2D();
            GlStateManager.color(1f, 1f, 1f, 1f);
        }
        GlStateManager.popMatrix();
        return true;
    }

    private static void prepare(SkyImage img) {
        if (texture == null || texture.getTextureData().length != img.argb().length) {
            if (texture != null) texture.deleteGlTexture();
            texture = new DynamicTexture(img.width(), img.height());
            uploaded = -1;
        }
        if (uploaded != img.serial()) {
            System.arraycopy(img.argb(), 0, texture.getTextureData(), 0, img.argb().length);
            texture.updateDynamicTexture();
            // The upload sets nearest filtering and repeat in both directions.
            GlStateManager.bindTexture(texture.getGlTextureId());
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            uploaded = img.serial();
        }
        if (sphere < 0) {
            sphere = GLAllocation.generateDisplayLists(1);
            GL11.glNewList(sphere, GL11.GL_COMPILE);
            float[] v = SkyImage.sphere(RADIUS);
            WorldRenderer r = Tessellator.getInstance().getWorldRenderer();
            r.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_TEX);
            for (int i = 0; i < v.length; i += 5) r.pos(v[i], v[i + 1], v[i + 2]).tex(v[i + 3], v[i + 4]).endVertex();
            Tessellator.getInstance().draw();
            GL11.glEndList();
        }
    }

    /** Draws the sky disc of renderSky, or the custom sky in its place. */
    public static void drawSky(VertexBuffer buffer, int mode) {
        // The custom sky draws from client memory, which GL refuses while the vanilla sky buffer is bound.
        buffer.unbindBuffer();
        boolean drawn = draw();
        buffer.bindBuffer();
        if (!drawn) buffer.drawArrays(mode);
    }

    public static void drawSky(int list) {
        if (!draw()) GlStateManager.callList(list);
    }

    public static void drawStars(VertexBuffer buffer, int mode) {
        if (!hidesStars()) buffer.drawArrays(mode);
    }

    public static void drawStars(int list) {
        if (!hidesStars()) GlStateManager.callList(list);
    }

    /** Draws a dark disc below the horizon, which the custom sky leaves out. */
    public static void drawDark(VertexBuffer buffer, int mode) {
        if (!active()) buffer.drawArrays(mode);
    }

    public static void drawDark(int list) {
        if (!active()) GlStateManager.callList(list);
    }

    /** Draws the sunrise fan or the box below the horizon, or ends the mesh when the custom sky leaves it out. */
    public static void drawMesh(Tessellator tessellator) {
        if (!active()) {
            tessellator.draw();
            return;
        }
        tessellator.getWorldRenderer().finishDrawing();
        tessellator.getWorldRenderer().reset();
    }

    /** Whether the sky of an OptiFine resource pack hides the stars, which it does not when the custom sky replaces it. */
    public static boolean customSkyLayers(boolean layers) {
        return layers && !active();
    }
}
