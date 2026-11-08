package dev.albedo.core.texture;

import dev.albedo.core.Category;

import java.util.Locale;

/**
 * Decides which {@link Category} a texture belongs to from its resource location alone.
 *
 * <p>This is what keeps the core version-agnostic: every Minecraft version since 1.8 names textures by
 * {@code namespace:textures/<kind>/<name>.png}. Only the folder names changed over time (1.8-1.12 used
 * plural {@code blocks/}/{@code items/}, 1.13+ singular), and both spellings are handled here. Atlas sprite ids
 * ({@code minecraft:block/stone}) and full paths ({@code minecraft:textures/block/stone.png}) are both accepted.
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

    static String normalise(String path) {
        String p = path.toLowerCase(Locale.ROOT).replace('\\', '/');
        if (p.startsWith("textures/")) p = p.substring("textures/".length());
        if (p.endsWith(".png")) p = p.substring(0, p.length() - 4);
        return p;
    }

    /**
     * OptiFine (and MCPatcher before it) keeps its textures in {@code optifine/} and {@code mcpatcher/}. Gives the
     * vanilla folder for each kind, so that these textures change with the textures they replace. A connected
     * texture ({@code ctm/}) gets the id of its block: the OptiFine defaults by number, a resource pack by its folder.
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
            return Category.BLOCKS;
        }
        if (folder.equals("item") || folder.equals("items")) return Category.ITEMS;
        if (folder.equals("particle") || folder.equals("particles")) return Category.PARTICLES;
        if (folder.equals("colormap")) return Category.COLORMAPS;
        if (folder.equals("painting")) return Category.BLOCK_ENTITIES;
        if (folder.equals("environment")) {
            if (rest.contains("cloud")) return Category.CLOUDS;
            if (rest.startsWith("rain") || rest.startsWith("snow")) return Category.WEATHER;
            return Category.SKY;
        }
        if (folder.equals("entity")) {
            if (isPlayer(rest)) return Category.PLAYERS;
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
            "chest/", "bed/", "signs/", "sign/", "banner/", "banner_base",
            "conduit/", "decorated_pot/", "bell/", "enchanting_table_book", "end_portal", "end_gateway",
            "beacon_beam", "lectern_book", "copper_golem_statue", "shelf/",
    };

    private static boolean isBlockEntity(String rest) {
        for (String f : BLOCK_ENTITY_FOLDERS) if (rest.startsWith(f)) return true;
        return false;
    }
}
