package dev.albedo.core.texture;

import dev.albedo.core.Category;

import java.util.Locale;

/**
 * Decides which {@link Category} a texture belongs to from its resource location alone, which works on every version
 * since 1.8 because only the folder names differ (plural {@code blocks/} and {@code items/} before 1.13).
 */
public final class TextureClassifier {
    private TextureClassifier() {}

    public static TextureKey classify(String namespace, String path) {
        String p = optiFine(normalise(path));
        int slash = p.indexOf('/');
        String folder = slash < 0 ? p : p.substring(0, slash);
        String rest = slash < 0 ? p : p.substring(slash + 1);
        String ns = namespace == null || namespace.isEmpty() ? "minecraft" : namespace.toLowerCase(Locale.ROOT);
        return new TextureKey(category(folder, rest), ns + ":" + rest);
    }

    /** Accepts "minecraft:textures/block/stone.png" style strings too. */
    public static TextureKey classify(String location) {
        int colon = location.indexOf(':');
        return colon < 0 ? classify("minecraft", location) : classify(location.substring(0, colon), location.substring(colon + 1));
    }

    /** Whether a texture is one piece of a connected texture (OptiFine, MCPatcher or Continuity), which Bevel and Outline leave alone. */
    public static boolean isConnected(String location) {
        return normalise(location.substring(location.indexOf(':') + 1)).contains("/ctm/");
    }

    static String normalise(String path) {
        String p = path.toLowerCase(Locale.ROOT).replace('\\', '/');
        if (p.startsWith("textures/")) p = p.substring("textures/".length());
        if (p.endsWith(".png")) p = p.substring(0, p.length() - 4);
        return p;
    }

    /**
     * Gives the vanilla folder for each kind of OptiFine and MCPatcher texture ({@code optifine/}, {@code mcpatcher/}),
     * so that these textures change with the textures they replace.
     */
    static String optiFine(String path) {
        String p;
        if (path.startsWith("optifine/")) p = path.substring("optifine/".length());
        else if (path.startsWith("mcpatcher/")) p = path.substring("mcpatcher/".length());
        else return path;
        if (p.startsWith("ctm/")) return "block/" + ctmBlock(p.substring("ctm/".length()));
        if (p.startsWith("cit/")) return "item/" + p.substring("cit/".length());
        if (p.startsWith("sky/")) return "environment/" + p;
        if (p.startsWith("colormap/")) return p;
        if (p.startsWith("random/entity/")) return p.substring("random/".length());
        if (p.startsWith("mob/")) return "entity/" + p.substring("mob/".length());
        return path;
    }

    /** The block id of a connected texture: the OptiFine defaults by number, a resource pack by its folder. */
    private static String ctmBlock(String rest) {
        String[] parts = rest.split("/");
        if (parts.length == 1) return "ctm";
        if (!parts[0].equals("default")) return parts[0];
        if (parts.length > 2) return parts[1].replaceFirst("^[0-9]+_", ""); // default/0_glass_white/12
        if (parts[1].matches("1[2-5]")) return "bookshelf";
        return parts[1].equals("66") ? "sandstone_normal" : "glass";
    }

    private static Category category(String folder, String rest) {
        if (folder.equals("block") || folder.equals("blocks")) {
            if (rest.startsWith("destroy_stage")) return Category.BLOCK_BREAKING;
            if (isFluid(rest)) return Category.FLUIDS;
            if (rest.startsWith("item_frame") || rest.startsWith("glow_item_frame") || rest.startsWith("itemframe")) {
                return Category.DECORATIONS;
            }
            return Category.BLOCKS;
        }
        if (folder.equals("item") || folder.equals("items")) return Category.ITEMS;
        if (folder.equals("particle") || folder.equals("particles")) return Category.PARTICLES;
        if (folder.equals("colormap")) return Category.COLORMAPS;
        if (folder.equals("painting") || folder.equals("map")) return Category.DECORATIONS;
        if (folder.equals("environment")) {
            if (rest.contains("cloud")) return Category.CLOUDS;
            if (rest.startsWith("rain") || rest.startsWith("snow")) return Category.WEATHER;
            return Category.SKY;
        }
        if (folder.equals("entity")) {
            if (isPlayer(rest)) return Category.PLAYERS;
            if (isDecoration(rest)) return Category.DECORATIONS;
            if (isBlockEntity(rest)) return Category.BLOCK_ENTITIES;
            return Category.ENTITIES;
        }
        if (folder.equals("models")) return Category.ENTITIES; // armor layers
        if (folder.equals("trims")) return Category.ENTITIES;
        return Category.OTHER;
    }

    private static boolean isFluid(String name) {
        return name.startsWith("water_") || name.startsWith("lava_") || name.equals("water_overlay")
                || name.startsWith("fluid/") || name.contains("/fluid/") || name.endsWith("_still") || name.endsWith("_flow")
                || name.endsWith("_flowing");
    }

    private static boolean isPlayer(String rest) {
        return rest.startsWith("player/") || rest.equals("steve") || rest.equals("alex");
    }

    private static final String[] BLOCK_ENTITY_FOLDERS = {
            // entity/shulker/ is deliberately absent: the same texture is used by the shulker mob.
            "chest/", "bed/", "conduit/", "decorated_pot/", "bell/", "enchanting_table_book", "end_portal", "end_gateway",
            "beacon_beam", "lectern_book", "copper_golem_statue", "shelf/",
    };

    /** Signs (1.8.9 "sign", later "signs/oak" and "signs/hanging/oak") and banners. */
    private static boolean isDecoration(String rest) {
        return rest.equals("sign") || rest.startsWith("signs/") || rest.startsWith("banner/") || rest.startsWith("banner_base");
    }

    private static boolean isBlockEntity(String rest) {
        for (String f : BLOCK_ENTITY_FOLDERS) if (rest.startsWith(f)) return true;
        return false;
    }
}
