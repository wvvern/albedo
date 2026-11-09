package dev.albedo.legacy;

import dev.albedo.core.Albedo;
import dev.albedo.core.config.SkyDesign;
import dev.albedo.core.sky.SkyImage;
import dev.albedo.core.sky.SkyLight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

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

    private LegacySky() {}

    /** The custom sky to draw, or null; OptiFine changes the sky renderer, so the custom sky is off with it. */
    public static SkyImage image() {
        return MixinPlugin.optiFine() ? null : Albedo.skyImage();
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
        SkyImage img = image();
        Entity view = Minecraft.getMinecraft().getRenderViewEntity();
        World world = Minecraft.getMinecraft().theWorld;
        if (img == null || view == null || world == null || !world.provider.isSurfaceWorld()) return null;
        return img.fogRgb(view.rotationYaw, brightness(img));
    }

    /**
     * Draws the sky where vanilla draws its disc, which has texturing off and the sky color set.
     *
     * @return false when there is no custom sky, so that the vanilla disc draws
     */
    public static boolean draw() {
        SkyImage img = image();
        if (img == null) return false;
        prepare(img);
        LegacySharpSky.prepare(img);
        float k = brightness(img);
        if (LegacySharpSky.draw(img, sphere, k)) return true;
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

    /** Ends a mesh that vanilla built for a draw that the custom sky leaves out, so that the next mesh can start. */
    public static void discard(Tessellator tessellator) {
        tessellator.getWorldRenderer().finishDrawing();
        tessellator.getWorldRenderer().reset();
    }
}
