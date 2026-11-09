package dev.albedo.modern;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import dev.albedo.core.Albedo;
import dev.albedo.core.ui.Canvas;
import dev.albedo.core.ui.CanvasImage;
import dev.albedo.core.ui.SettingsEditor;
import dev.albedo.core.ui.UiKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
//? if >=1.21.11 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}

/**
 * Hosts the version-agnostic {@link SettingsEditor} and holds everything version-specific about the settings screen,
 * such as input and drawing through a {@link Canvas}.
 */
public final class SettingsScreen extends Screen {
    private final Screen parent;
    private final SettingsEditor editor;
    private final Images images = new Images();

    public SettingsScreen(Screen parent) {
        super(Component.literal(Albedo.NAME));
        this.parent = parent;
        ModernHooks.bootstrap();
        this.editor = new SettingsEditor(Albedo.get());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        ModernPlatform.setScreen(parent);
    }

    //? if >=26.1 {
    @Override
    public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        draw(new GraphicsCanvas(g), mouseX, mouseY);
    }

    @Override
    public void extractBackground(net.minecraft.client.gui.GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // the editor draws its own backdrop and keeps the world visible behind it
    }
    //?} else {
    /*@Override
    public void render(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        draw(new GraphicsCanvas(g), mouseX, mouseY);
    }

    @Override
    public void renderBackground(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // the editor draws its own backdrop and keeps the world visible behind it
    }
    *///?}

    private void draw(Canvas canvas, int mouseX, int mouseY) {
        editor.render(canvas, mouseX, mouseY);
    }

    /** Closes the screen in the tick and not in the frame, because the GUI draws this frame's images after the frame's calls. */
    @Override
    public void tick() {
        if (editor.isClosed()) onClose();
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        editor.mouseScrolled(vertical);
        return true;
    }

    //? if >=1.21.9 {
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) editor.mousePressed();
        return true;
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) editor.mouseReleased();
        return true;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        return key(event.key(), event.hasControlDown());
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        for (char c : Character.toChars(event.codepoint())) editor.charTyped(c);
        return true;
    }
    //?} else {
    /*@Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button == InputConstants.MOUSE_BUTTON_LEFT) editor.mousePressed();
        return true;
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (button == InputConstants.MOUSE_BUTTON_LEFT) editor.mouseReleased();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return key(keyCode, Screen.hasControlDown());
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        editor.charTyped(c);
        return true;
    }
    *///?}

    private boolean key(int code, boolean ctrl) {
        UiKey key = code == InputConstants.KEY_BACKSPACE ? UiKey.BACKSPACE
                : code == InputConstants.KEY_DELETE ? UiKey.DELETE
                : code == InputConstants.KEY_LEFT ? UiKey.LEFT
                : code == InputConstants.KEY_RIGHT ? UiKey.RIGHT
                : code == InputConstants.KEY_UP ? UiKey.UP
                : code == InputConstants.KEY_DOWN ? UiKey.DOWN
                : code == InputConstants.KEY_HOME ? UiKey.HOME
                : code == InputConstants.KEY_END ? UiKey.END
                : code == InputConstants.KEY_RETURN || code == InputConstants.KEY_NUMPADENTER ? UiKey.ENTER
                : code == InputConstants.KEY_ESCAPE ? UiKey.ESCAPE
                : code == InputConstants.KEY_TAB ? UiKey.TAB
                : code == InputConstants.KEY_A ? UiKey.A
                : code == InputConstants.KEY_V ? UiKey.V
                : null;
        if (key != null) editor.keyPressed(key, ctrl);
        return true;
    }

    private final class GraphicsCanvas implements Canvas {
        //? if >=26.1 {
        private final net.minecraft.client.gui.GuiGraphicsExtractor g;

        GraphicsCanvas(net.minecraft.client.gui.GuiGraphicsExtractor g) {
            this.g = g;
        }

        @Override
        public void text(String text, int x, int y, int argb) {
            g.text(font, text, x, y, argb, false);
        }
        //?} else {
        /*private final net.minecraft.client.gui.GuiGraphics g;

        GraphicsCanvas(net.minecraft.client.gui.GuiGraphics g) {
            this.g = g;
        }

        @Override
        public void text(String text, int x, int y, int argb) {
            g.drawString(font, text, x, y, argb, false);
        }
        *///?}

        @Override public int width() { return SettingsScreen.this.width; }
        @Override public int height() { return SettingsScreen.this.height; }
        @Override public void fill(int x0, int y0, int x1, int y1, int argb) { g.fill(x0, y0, x1, y1, argb); }
        @Override public int textWidth(String text) { return font.width(text); }

        @Override
        public void image(String id, CanvasImage img, int x, int y, int w, int h) {
            var location = Images.id(images.prepare(id, img));
            //? if >=1.21.11 {
            g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, location, x, y, 0f, 0f, w, h,
                    img.width(), img.height(), img.width(), img.height());
            //?} else {
            /*g.blit(location, x, y, w, h, 0f, 0f, img.width(), img.height(), img.width(), img.height());
            *///?}
        }
    }

    @Override
    public void removed() {
        images.close();
        super.removed();
    }

    /**
     * The textures of the images that the editor draws, one for each id and size, which stay until the screen closes because
     * the game draws the GUI after this frame's calls.
     */
    private static final class Images {
        private final java.util.Map<String, DynamicTexture> textures = new java.util.HashMap<String, DynamicTexture>();
        private final java.util.Map<String, Long> serials = new java.util.HashMap<String, Long>();

        //? if >=1.21.11 {
        static Identifier id(String key) {
            return Identifier.fromNamespaceAndPath(Albedo.MOD_ID, "gui/" + key);
        }
        //?} else {
        /*static ResourceLocation id(String key) {
            return ResourceLocation.fromNamespaceAndPath(Albedo.MOD_ID, "gui/" + key);
        }
        *///?}

        /** Registers the texture of {@code id} with the pixels of {@code img}, and returns its key for {@link #id}. */
        String prepare(String id, CanvasImage img) {
            String key = (id + "_" + img.width() + "x" + img.height()).toLowerCase(java.util.Locale.ROOT);
            DynamicTexture texture = textures.get(key);
            if (texture == null) {
                NativeImage pixels = new NativeImage(img.width(), img.height(), false);
                //? if >=1.21.11 {
                texture = new DynamicTexture(() -> "Albedo " + key, pixels);
                //?} else {
                /*texture = new DynamicTexture(pixels);
                *///?}
                textures.put(key, texture);
                Minecraft.getInstance().getTextureManager().register(id(key), texture);
            }
            Long serial = serials.get(key);
            if (serial == null || serial != img.serial()) {
                NativeImagePixels out = new NativeImagePixels(texture.getPixels());
                int[] argb = img.argb();
                for (int py = 0; py < img.height(); py++) {
                    for (int px = 0; px < img.width(); px++) out.setArgb(px, py, argb[py * img.width() + px] | 0xFF000000);
                }
                texture.upload();
                serials.put(key, img.serial());
            }
            return key;
        }

        void close() {
            for (String key : textures.keySet()) Minecraft.getInstance().getTextureManager().release(id(key));
            textures.clear();
            serials.clear();
        }
    }
}
