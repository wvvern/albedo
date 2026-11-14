package dev.albedo.core.dev;

import dev.albedo.core.Category;
import dev.albedo.core.config.Appearance;
import dev.albedo.core.config.CategorySettings;
import dev.albedo.core.config.Chroma;
import dev.albedo.core.config.Config;
import dev.albedo.core.config.ConfigCodec;
import dev.albedo.core.config.Profile;
import dev.albedo.core.config.SkyDesign;
import dev.albedo.core.config.SkyLayer;
import dev.albedo.core.config.SkyTurning;
import dev.albedo.core.sky.SkyPresets;

import java.util.Locale;

/** The parsers of the harness commands that change the config. */
final class HarnessConfigCommands {
    private HarnessConfigCommands() {}

    /** Adds the profile of a profile file, written on one line, and makes it the active profile. */
    static Config withProfileJson(Config config, String json) {
        Profile p = ConfigCodec.decodeProfileFile(json);
        return config.withProfile(p).withActiveProfile(p.name()).withEnabled(true);
    }

    /** Sets the look of a category to one color from {@code <category> solid #RRGGBB}. */
    static Profile withLook(Profile profile, String[] args) {
        if (args.length != 3 || !args[1].equals("solid") || !args[2].matches("#[0-9A-Fa-f]{6}")) {
            throw new IllegalArgumentException("look <category> solid #RRGGBB");
        }
        Category category = Category.byKey(args[0]);
        if (category == null) throw new IllegalArgumentException("unknown category: " + args[0]);
        CategorySettings settings = profile.get(category);
        return profile.with(category, settings.withAppearance(Appearance.solid(0xFF000000 | Integer.parseInt(args[2].substring(1), 16))));
    }

    /**
     * Sets the effects of a category's look from
     * {@code <category> [bevel N] [outline N] [chroma N] [speed N] [saturation N] [brightness N] [edge N]}, where a
     * missing effect is off, a missing Chroma option has its default, and Edge is on for N above 0.
     */
    static Profile withEffects(Profile profile, String[] args) {
        Category category = Category.byKey(args[0]);
        if (category == null) throw new IllegalArgumentException("unknown category: " + args[0]);
        if (!category.hasChroma()) throw new IllegalArgumentException("this category has no effects");
        if (args.length % 2 == 0) {
            throw new IllegalArgumentException(
                    "effects <category> [bevel N] [outline N] [chroma N] [speed N] [saturation N] [brightness N] [edge N]");
        }
        int bevel = 0, outline = 0, chroma = 0, edge = 0;
        int speed = Chroma.DEFAULT_SPEED, saturation = Chroma.DEFAULT_SATURATION, brightness = Chroma.DEFAULT_BRIGHTNESS;
        for (int i = 1; i < args.length; i += 2) {
            int value = Integer.parseInt(args[i + 1]);
            // The config accepts only percents, and a speed above 100 has no place in the turns of the GPU pass.
            if (value < 0 || value > 100) throw new IllegalArgumentException(args[i] + " must be from 0 to 100: " + value);
            if (args[i].equals("bevel")) bevel = value;
            else if (args[i].equals("outline")) outline = value;
            else if (args[i].equals("chroma")) chroma = value;
            else if (args[i].equals("speed")) speed = value;
            else if (args[i].equals("saturation")) saturation = value;
            else if (args[i].equals("brightness")) brightness = value;
            else if (args[i].equals("edge")) edge = value;
            else throw new IllegalArgumentException("unknown effect: " + args[i]);
        }
        if (bevel > 0 && !category.hasBevel()) throw new IllegalArgumentException("only blocks and fluids have bevel");
        if ((outline > 0 || edge > 0) && !category.hasOutline()) {
            throw new IllegalArgumentException("only blocks, fluids and items have outline and edge");
        }
        CategorySettings settings = profile.get(category);
        Chroma c = new Chroma(chroma, speed, saturation, brightness, edge > 0);
        return profile.with(category, settings.withAppearance(settings.appearance().withEffects(bevel, outline, c)));
    }

    /**
     * Sets the sky to a preset of custom layers ({@code sky dusk}), to only its lowest layers ({@code sky galaxy 4}), or
     * back to the vanilla sky with {@code sky off}, where a preset with the default turning stands still until {@code skyturn}.
     */
    static Profile withSky(Profile profile, String arg) {
        CategorySettings sky = profile.get(Category.SKY);
        if (arg.equals("off")) return profile.with(Category.SKY, sky.withAppearance(Appearance.ORIGINAL));
        String[] parts = arg.trim().split("\\s+");
        SkyDesign design = SkyPresets.byName(parts[0]);
        if (design == null || parts.length > 2) {
            StringBuilder keys = new StringBuilder();
            for (SkyPresets.Preset p : SkyPresets.all()) keys.append(p.key()).append('|');
            throw new IllegalArgumentException("sky <" + keys + "off> [layers]");
        }
        // A still sky gives the same screenshots in each run, and Trails keeps the turning that draws its trails.
        if (design.turning().equals(SkyTurning.DEFAULT)) design = design.withTurning(SkyTurning.STILL);
        if (parts.length == 2) {
            int keep = Integer.parseInt(parts[1]);
            if (keep < 1 || keep > design.layers().size()) throw new IllegalArgumentException("layers: 1 to " + design.layers().size());
            design = design.withLayers(design.layers().subList(0, keep));
        }
        return profile.with(Category.SKY, sky.withAppearance(Appearance.of(Appearance.Mode.LAYERS)).withSky(design));
    }

    /** Adds a layer of a kind with its default values at the top of the custom sky ({@code skyadd posterize}). */
    static Profile withSkyLayer(Profile profile, String kind) {
        CategorySettings sky = profile.get(Category.SKY);
        if (sky.sky().layers().size() >= SkyDesign.MAX_LAYERS) {
            throw new IllegalArgumentException("the sky has " + SkyDesign.MAX_LAYERS + " layers");
        }
        SkyLayer.Kind k = SkyLayer.Kind.valueOf(kind.trim().toUpperCase(Locale.ROOT));
        return profile.with(Category.SKY, sky.withSky(sky.sky().withAdded(k)));
    }

    /** Sets the render of the custom sky to {@code sharp} or {@code fast}. */
    static Profile withSkyRender(Profile profile, String render) {
        CategorySettings sky = profile.get(Category.SKY);
        SkyDesign.Render r = SkyDesign.Render.valueOf(render.trim().toUpperCase(Locale.ROOT));
        return profile.with(Category.SKY, sky.withSky(sky.sky().withRender(r)));
    }

    /**
     * Sets the turning of the custom sky from {@code <percent> [height N]}, where a missing option keeps its value in
     * {@link SkyTurning#STILL} ({@code skyturn 100 height 75}).
     */
    static Profile withSkyTurning(Profile profile, String[] args) {
        SkyTurning t = SkyTurning.STILL.withAmount(Integer.parseInt(args[0]));
        for (int i = 1; i < args.length; i++) {
            if (i + 1 >= args.length) throw new IllegalArgumentException("skyturn <percent> [height N]");
            if (args[i].equals("height")) t = t.withPoleHeight(Integer.parseInt(args[++i]));
            else throw new IllegalArgumentException("unknown skyturn option: " + args[i]);
        }
        CategorySettings sky = profile.get(Category.SKY);
        return profile.with(Category.SKY, sky.withSky(sky.sky().withTurning(t)));
    }
}
