package dev.albedo.core.ui;

import dev.albedo.core.Albedo;
import dev.albedo.core.Category;
import dev.albedo.core.CoreFixture;
import dev.albedo.core.config.Appearance;
import dev.albedo.core.config.Config;
import dev.albedo.core.config.ConfigFixtures;
import dev.albedo.core.config.SkyDesign;
import dev.albedo.core.config.SkyLayer;
import dev.albedo.core.config.SkyMask;
import dev.albedo.core.config.SkyTurning;
import dev.albedo.core.sky.SkyClock;
import dev.albedo.core.sky.SkyPresets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static dev.albedo.core.CoreFixture.withOwnProfile;
import static org.junit.jupiter.api.Assertions.*;

/** The sky page of the settings screen: the layer list, the gallery of skies, the preview and the motion section. */
class SkyLayersEditorTest {
    private final Albedo albedo = CoreFixture.albedo();

    @BeforeEach
    void defaults() {
        CoreFixture.reset();
    }

    /** The user's own profile with this sky in the style "layers". */
    private static Config withSky(SkyDesign sky) {
        Config c = withOwnProfile();
        return c.withProfile(c.activeProfile().with(Category.SKY, ConfigFixtures.layers(sky)));
    }

    /** The sky design of the active profile in the saved config. */
    private SkyDesign savedSky() {
        return albedo.config().activeProfile().get(Category.SKY).sky();
    }

    @Test
    void theSkyLayersEditorAddsMovesMasksAndDeletesLayers() {
        albedo.apply(withOwnProfile());
        EditorDriver d = EditorDriver.open(640, 360, 0);
        d.click("Sky");
        d.click("Layers");
        assertNotNull(d.find("Gradient 1"), "a new custom sky starts from Dusk: " + d.labels());
        d.click("Apply");
        assertEquals(SkyPresets.DUSK, savedSky());
        assertEquals(Appearance.Mode.LAYERS, albedo.config().activeProfile().get(Category.SKY).appearance().mode());

        d.click("+ Add layer");
        assertNotNull(d.find("Make"), "the add screen: " + d.labels());
        d.click("Blur");
        assertNotNull(d.find("Blur 5 of 5"), "the new layer opens: " + d.labels());
        d.click("None  >");
        assertNotNull(d.find("Light"), "a mask can use light or dark parts: " + d.labels());
        d.click("Dark");
        d.click("Place");
        assertNotNull(d.find("has no place"), "a blur changes the whole sky: " + d.labels());
        d.key(UiKey.ESCAPE);
        assertNotNull(d.find("masked by Gradient 1"), "the list names the mask: " + d.labels());
        d.click("Apply");
        SkyDesign design = savedSky();
        assertEquals(5, design.layers().size());
        assertEquals(SkyMask.of(1, SkyMask.Mode.DARK), design.layers().get(4).mask());

        // At the end of the list, the last "^" drawn is on the bottom row: the gradient moves over the glow, still below the blur it masks.
        int[] row = d.find("Glow 2");
        for (int i = 0; i < 5; i++) d.scrollAt(row[0], row[1]);
        d.click("^");
        d.click("Apply");
        assertEquals(SkyLayer.Kind.GLOW, savedSky().layers().get(0).kind());
        assertEquals(SkyLayer.Kind.GRADIENT, savedSky().layers().get(1).kind());

        SkyDesign own = savedSky();
        d.click("Skies");
        d.click("Use this sky");
        assertNotNull(d.find("Start from a sky"), "the button does nothing before a card is chosen");
        d.click("Dusk");
        d.click("Apply");
        assertEquals(own, savedSky(), "a click on a card only chooses it");
        d.key(UiKey.ESCAPE);
        assertNotNull(d.find("+ Add layer"), "Esc goes back to the layers: " + d.labels());
        d.click("Apply");
        assertEquals(own, savedSky(), "Esc leaves the layers as they are");

        d.click("Skies");
        d.click("Dusk");
        d.click("Use this sky");
        assertNotNull(d.find("+ Add layer"), "the layers show again: " + d.labels());
        d.click("Apply");
        assertEquals(SkyPresets.DUSK, savedSky(), "the chosen sky replaces the layers");
    }

    @Test
    void eachCardOfTheGalleryGetsItsImage() throws InterruptedException {
        albedo.apply(withSky(SkyPresets.DUSK));
        EditorDriver d = EditorDriver.open(960, 540);
        d.click("Sky");
        d.click("Skies");
        for (SkyPresets.Preset p : SkyPresets.all()) {
            assertNotNull(d.find(p.name()), "each sky has a card: " + d.labels());
            CanvasImage img = d.awaitImage("sky.gallery." + p.key(), shown -> true);
            assertNotNull(img, "the card of " + p.name() + " has an image: " + d.canvas.images.keySet());
            assertTrue(img.height() > img.width() / 4 && img.height() < img.width() / 2, "the image ends a little below the horizon");
        }

        // The aurora is in the north, at the two ends of a bake, and its card shows it in the middle.
        CanvasImage aurora = d.canvas.images.get("sky.gallery.aurora");
        long middle = 0, ends = 0;
        for (int i = 0; i < aurora.argb().length; i++) {
            int x = i % aurora.width(), p = aurora.argb()[i];
            // The green that a pixel has over its red and blue, which the stars and the night sky do not have.
            int green = Math.max(0, (p >> 8 & 0xFF) - Math.max(p >> 16 & 0xFF, p & 0xFF));
            if (x >= aurora.width() / 4 && x < aurora.width() * 3 / 4) middle += green;
            else ends += green;
        }
        assertTrue(middle > 2 * ends, "the card of the aurora looks north: " + middle + " and " + ends);

        // The clock stands still, so that only the click on a card gives the large preview a new image.
        SkyClock.hold(0);
        try {
            // An image from before the hold is made again once, for the time 0.
            d.render();
            CanvasImage own = newPreview(d, null);
            d.render();
            assertSame(own, d.canvas.images.get("sky.preview"), "the preview stays while no card is chosen");
            d.click("Galaxy");
            assertNotNull(newPreview(d, own), "the large preview shows the sky of the chosen card");
            d.click("Apply");
            assertEquals(SkyPresets.DUSK, savedSky(), "the preview does not change the profile");
        } finally {
            SkyClock.run();
        }

        // A change of the style leaves the gallery, so that Esc closes the screen and is not lost on a hidden page.
        d.click("Color");
        d.key(UiKey.ESCAPE);
        assertTrue(d.editor.isClosed(), "Esc closes the screen after a style change");
    }

    /** The large sky preview once it shows other pixels than {@code old}, or null after ten seconds. */
    private static CanvasImage newPreview(EditorDriver d, CanvasImage old) throws InterruptedException {
        return d.awaitImage("sky.preview", shown -> old == null || !Arrays.equals(shown.argb(), old.argb()));
    }

    @Test
    void theCardsScrollInAShortWindowAndAClickKeepsTheirPlace() {
        albedo.apply(withSky(SkyPresets.DUSK));
        EditorDriver d = EditorDriver.open(320, 200);
        d.click("Sky");
        d.click("Skies");
        String last = SkyPresets.all().get(SkyPresets.all().size() - 1).name();
        assertNull(d.find(last), "the last card is below the window: " + d.labels());
        int[] first = d.find("Dusk");
        for (int i = 0; i < 10 && d.find(last) == null; i++) d.scrollAt(first[0], first[1]);
        assertNotNull(d.find(last), "the wheel shows the last card: " + d.labels());
        d.click(last);
        assertNotNull(d.find(last), "a click on a card does not move the cards: " + d.labels());
        d.click("Use this sky");
        d.click("Apply");
        assertEquals(SkyPresets.TRAILS, savedSky(), "a sky with its own turning brings it");
    }

    @Test
    void theMotionSectionTurnsTheSkyAndShowsTheOptionsOnlyWhileItTurns() {
        albedo.apply(withSky(SkyPresets.GALAXY.withTurning(SkyTurning.STILL)));
        EditorDriver d = EditorDriver.open(640, 360);
        d.click("Sky");
        assertNotNull(d.canvas.texts.get("Off"), "the closed heading tells that the sky is still: " + d.labels());
        assertNull(d.canvas.texts.get("Turning"), "the section starts closed");
        int listY = d.find("Bloom 7")[1];

        d.click("Motion");
        assertNotNull(d.canvas.texts.get("Turning"), "the slider shows in the open section: " + d.labels());
        assertNull(d.canvas.texts.get("Speed"), "a still sky shows no options");
        assertTrue(d.find("Bloom 7") == null || d.find("Bloom 7")[1] > listY, "the layers move down");

        // A press near the right end of the track turns the sky, and the options show in the frame of the press.
        int[] value = d.canvas.texts.get("0%");
        d.pressAt(value[0] - 20, value[1]);
        assertNotNull(d.canvas.texts.get("Speed"), "the options show while the sky turns: " + d.labels());
        assertNotNull(d.canvas.texts.get("Follow time"));
        d.click("Reverse");
        d.click("Apply");
        SkyTurning t = savedSky().turning();
        assertTrue(t.amount() > 80, "the sky turns: " + t.amount());
        assertEquals(SkyTurning.Direction.REVERSE, t.direction());
        assertEquals(SkyTurning.Clock.WORLD, t.clock(), "Follow time starts on");
        assertEquals(SkyPresets.GALAXY.layers(), savedSky().layers(), "the turning does not change the layers");

        // A click on the label flips the switch, as on the other switch rows.
        d.click("Follow time");
        d.click("Apply");
        assertEquals(SkyTurning.Clock.REAL, savedSky().turning().clock(), "a click on the label turns Follow time off");
        d.click("Follow time");
        d.click("Apply");
        assertEquals(SkyTurning.Clock.WORLD, savedSky().turning().clock());

        d.click("Skies");
        d.click("Nebula");
        d.click("Use this sky");
        d.click("Apply");
        assertEquals(SkyPresets.NEBULA.layers(), savedSky().layers());
        assertEquals(t, savedSky().turning(), "a preset keeps the turning");

        // A press at the left end of the turning track stops the sky and hides the options.
        d.pressTrackStart("sky.turning");
        assertNull(d.canvas.texts.get("Speed"), "a still sky shows no options: " + d.labels());
        d.script("click Apply");
        assertEquals(0, savedSky().turning().amount());
        assertEquals(SkyTurning.Direction.REVERSE, savedSky().turning().direction(), "the other options stay");
    }

    @Test
    void thePreviewTurnsWithTheClock() throws InterruptedException {
        SkyTurning real = new SkyTurning(100, 10, SkyTurning.Clock.REAL, 45, SkyTurning.Direction.NORMAL, 0);
        albedo.apply(withSky(SkyPresets.GALAXY.withTurning(real)));
        try {
            EditorDriver d = EditorDriver.open(960, 540);
            d.click("Sky");
            SkyClock.hold(0);
            CanvasImage start = d.awaitImage("sky.preview", shown -> true);
            assertNotNull(start, "the preview is ready");
            d.render();
            assertSame(start, d.canvas.images.get("sky.preview"), "a held clock makes no new image");
            SkyClock.hold(30);
            d.render();
            CanvasImage quarter = d.canvas.images.get("sky.preview");
            assertNotEquals(start.serial(), quarter.serial());
            assertFalse(Arrays.equals(start.argb(), quarter.argb()), "a quarter turn later the preview shows another part of the sky");
        } finally {
            SkyClock.run();
        }
    }

    @Test
    void theAddPageScrollsToEveryKindOnASmallScreen() {
        List<SkyLayer> one = Collections.singletonList(SkyLayer.of(1, SkyLayer.Kind.GRADIENT));
        albedo.apply(withSky(SkyPresets.GALAXY.withLayers(one)));
        EditorDriver d = EditorDriver.open(320, 200);
        d.click("Sky");
        d.click("+ Add layer");
        int[] first = d.find("Gradient");
        assertNotNull(first, "the first kind shows: " + d.labels());
        assertNull(d.find("Vignette"), "the last kind is below the page at this size");
        for (int i = 0; i < 12; i++) d.scrollAt(first[0], first[1]);
        assertNotNull(d.find("Vignette"), "the page scrolls down to the last kind: " + d.labels());
        assertNotNull(d.find("Change"), "the heading of the filters moves with them");
        d.click("Vignette");
        d.click("Apply");
        assertEquals(SkyLayer.Kind.VIGNETTE, savedSky().layers().get(1).kind());
    }

    @Test
    void theSkyLayersEditorFitsAtEverySize() {
        int[][] sizes = {{320, 200}, {320, 240}, {427, 240}, {533, 300}, {640, 360}};
        String[][] paths = {{}, {"+ Add layer"}, {"Gradient 1"}, {"Gradient 1", "Place"}, {"Skies"}, {"Skies", "Dusk"}, {"Motion"}};
        for (int[] size : sizes) {
            for (String[] path : paths) {
                albedo.apply(withSky(SkyPresets.GALAXY.withTurning(SkyTurning.STILL.withAmount(50))));
                EditorDriver d = EditorDriver.open(size[0], size[1], 0);
                d.click("Sky");
                for (String label : path) if (d.find(label) != null) d.click(label);
                int[] style = d.find("Style");
                if (style != null) d.assertAboveFooter(style[0] - 15);
                SettingsEditor editor = d.editor;
                for (int y = 0; y < size[1]; y += 7) {
                    for (int x = 0; x < size[0]; x += 11) {
                        if (editor.isClosed()) break;
                        editor.mousePressed();
                        editor.render(d.canvas, x, y);
                        editor.mouseReleased();
                        editor.mouseScrolled(-1);
                        editor.render(d.canvas, x + 1, y);
                    }
                }
            }
        }
    }
}
