package dev.albedo.core;

import dev.albedo.core.config.Appearance;
import dev.albedo.core.config.Config;
import dev.albedo.core.config.ConfigCodec;
import dev.albedo.core.config.Profile;
import dev.albedo.core.dev.Harness;
import dev.albedo.core.texture.PixelTransformer;
import dev.albedo.core.texture.TextureClassifier;
import dev.albedo.core.texture.TextureKey;
import dev.albedo.core.texture.TextureStore;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The single entry point adapters talk to. Holds the current config/policy, watches the config file, handles the
 * keys and transforms textures. Adapters call {@link #init} once, {@link #processTexture} for every loaded texture,
 * {@link #currentPolicy()} from render hooks and {@link #clientTick} every client tick.
 */
public final class Albedo {
    public static final String MOD_ID = "albedo";
    public static final String NAME = "Albedo";
    private static final int WATCH_INTERVAL_TICKS = 20;

    private static volatile Albedo instance;

    private final Platform platform;
    private final File dir;
    private final File configFile;
    private final Harness harness;
    private volatile Config config;
    private volatile Policy policy = Policy.DISABLED;
    private final Map<String, PixelBuffer> userTextures = new ConcurrentHashMap<String, PixelBuffer>();
    /** Every loaded texture with its original: what a settings change repaints and the settings screen lists. */
    private final TextureStore store = new TextureStore();
    private final PixelTransformer.TextureSource textureSource;

    private long lastModified;
    private int ticks;
    private final AtomicInteger transformedSinceSummary = new AtomicInteger();
    private final boolean[] keyWasDown = new boolean[3];

    private Albedo(Platform platform) {
        this.platform = platform;
        this.dir = new File(platform.configDir(), MOD_ID);
        this.configFile = new File(dir, "config.json");
        this.harness = Harness.fromSystemProperty(platform);
        this.textureSource = new PixelTransformer.TextureSource() {
            @Override
            public PixelBuffer load(String name) {
                return userTexture(name);
            }
        };
    }

    /** Idempotent. */
    public static synchronized Albedo init(Platform platform) {
        if (instance == null) {
            Albedo sc = new Albedo(platform);
            sc.loadOrCreate();
            instance = sc;
            platform.info(NAME + " ready on " + platform.describe() + ", profile '" + sc.config.activeProfileName()
                    + "', " + (sc.config.enabled() ? "enabled" : "disabled"));
        }
        return instance;
    }

    /** May be null before init. */
    public static Albedo get() {
        return instance;
    }

    /** Never null: a disabled policy before init. Safe to call from any thread, any time. */
    public static Policy currentPolicy() {
        Albedo sc = instance;
        return sc == null ? Policy.DISABLED : sc.policy;
    }

    public Config config() { return config; }

    public Platform platform() { return platform; }

    // ---------------------------------------------------------------- textures

    /**
     * Keeps the original of a freshly loaded texture, then transforms it in place. The {@code upload} puts other
     * pixels into the same texture later, when a settings change repaints it.
     *
     * @param location "minecraft:textures/block/stone.png" or a sprite name with its folder ("minecraft:block/stone")
     * @return true if the image was changed
     */
    public static boolean processTexture(String location, PixelBuffer image, TextureStore.Upload upload) {
        Albedo sc = instance;
        if (sc == null || sc.store.repainting()) return false;
        TextureKey key = TextureClassifier.classify(location);
        try {
            Appearance a = sc.policy.appearanceFor(key);
            sc.store.record(key, image, upload, a);
            boolean changed = PixelTransformer.apply(a, image, sc.textureSource);
            if (changed) sc.transformedSinceSummary.incrementAndGet();
            return changed;
        } catch (RuntimeException e) {
            sc.platform.warn("Cannot change texture " + key, e);
            return false;
        }
    }

    /**
     * A biome colormap ({@code minecraft:textures/colormap/grass.png}) as the game keeps it: an ARGB array that it
     * reads by reference. Changed in place, now and on each repaint.
     */
    public static void processColormap(String location, int[] pixels) {
        if (pixels != null && pixels.length == COLORMAP_SIZE * COLORMAP_SIZE) {
            processTexture(location, new PixelBuffer.Heap(COLORMAP_SIZE, COLORMAP_SIZE, pixels), TextureStore.intoArray(pixels));
        }
    }

    private static final int COLORMAP_SIZE = 256;

    /** Ids seen in a category, sorted, each with its original average color. Read-only; client thread. */
    public Map<String, Integer> catalog(Category category) {
        return store.catalog(category);
    }

    private volatile List<String> textureNames = Collections.emptyList();
    private long texturesModified = -1;

    /** PNG names in the user textures folder, for the TEXTURE mode picker (refreshed by the file watcher). */
    public List<String> userTextureNames() {
        return textureNames;
    }

    /** Re-reads the textures folder when it changed, and repaints the textures that use an image from it. */
    private void watchTexturesFolder() {
        File folder = texturesFolder();
        if (folder.lastModified() == texturesModified) return;
        boolean first = texturesModified == -1;
        texturesModified = folder.lastModified();
        File[] files = folder.listFiles();
        List<String> names = new ArrayList<String>();
        if (files != null) {
            for (File f : files) if (f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".png")) names.add(f.getName());
        }
        Collections.sort(names);
        textureNames = Collections.unmodifiableList(names);
        userTextures.clear();
        if (!first) refresh(policy, true);
    }

    public File texturesFolder() {
        return new File(dir, "textures");
    }

    private PixelBuffer userTexture(String name) {
        PixelBuffer cached = userTextures.get(name);
        if (cached != null) return cached;
        File folder = texturesFolder();
        File f = new File(folder, name);
        try {
            if (!f.getCanonicalPath().startsWith(folder.getCanonicalPath() + File.separator)) return null;
            PixelBuffer img = platform.readImage(f);
            userTextures.put(name, img);
            return img;
        } catch (IOException e) {
            platform.warn("Cannot read texture " + f + ". The mod uses the color instead.", e);
            return null;
        }
    }

    // ---------------------------------------------------------------- tick: keys, file watching, harness

    /** @param screenOpen true while a GUI is open (keys are ignored then so typing doesn't toggle). */
    public void clientTick(boolean screenOpen) {
        Config.KeyBinds keys = config.keys();
        if (pressed(0, keys.toggle, screenOpen)) apply(config.withEnabled(!config.enabled()));
        if (pressed(1, keys.cycleProfile, screenOpen)) apply(config.withActiveProfile(config.profileNameAt(1)).withEnabled(true));
        if (pressed(2, keys.settings, screenOpen)) platform.openSettings();

        if (++ticks % WATCH_INTERVAL_TICKS == 0) {
            watchTexturesFolder();
            int transformed = transformedSinceSummary.getAndSet(0);
            if (transformed > 0) {
                platform.info("Changed " + transformed + " textures with profile '" + config.activeProfileName() + "'");
            }
            if (configFile.lastModified() != lastModified) {
                Policy before = policy;
                if (load()) {
                    platform.showMessage(NAME + ": config reloaded");
                    refresh(before, false);
                }
            }
        }
        if (harness != null) harness.tick();
    }

    private boolean pressed(int slot, String keyName, boolean screenOpen) {
        Keys.Key key = Keys.byName(keyName);
        boolean down = !screenOpen && key != null && platform.isKeyDown(key);
        boolean edge = down && !keyWasDown[slot];
        keyWasDown[slot] = down;
        return edge;
    }

    /** Swap config, persist it, tell the player what changed, and show the change in the world. */
    public void apply(Config next) {
        Config before = config;
        if (next.equals(before)) return;
        Policy old = policy;
        setConfig(next);
        save();
        if (before.enabled() != next.enabled() || !before.activeProfileName().equals(next.activeProfileName())) {
            platform.showMessage(NAME + ": " + (next.enabled() ? next.activeProfileName() : "off"));
        }
        refresh(old, false);
    }

    /**
     * Shows the current policy in the world. Sky, fog, fullbright and shadows are read each frame. Textures are
     * repainted one by one. Tints and shading live in the chunk meshes, so a change to them rebuilds the chunks. Only
     * what the repaint cannot reach makes a full resource reload.
     */
    private void refresh(Policy old, boolean userImagesChanged) {
        Policy now = policy;
        boolean shading = old.shading() != now.shading();
        if ((shading && platform.shadingInModels()) || (!store.complete() && !old.sameTextures(now))) {
            platform.reloadResources();
            return;
        }
        long start = System.nanoTime();
        TextureStore.Result r = store.repaint(now, textureSource, userImagesChanged);
        if (r.failed > 0) {
            platform.warn(r.failed + " textures cannot be repainted. Resources reload instead.", null);
            platform.reloadResources();
            return;
        }
        boolean meshes = shading || old.removeTint(Category.BLOCKS) != now.removeTint(Category.BLOCKS)
                || old.removeTint(Category.FLUIDS) != now.removeTint(Category.FLUIDS);
        if (meshes) platform.rebuildChunks();
        if (r.repainted > 0 || meshes) {
            platform.info("Repainted " + r.repainted + " textures" + (meshes ? " and rebuilt chunks" : "") + " in "
                    + (System.nanoTime() - start) / 1000000 + " ms");
        }
    }

    // ---------------------------------------------------------------- persistence

    private void setConfig(Config c) {
        config = c;
        policy = Policy.of(c);
        userTextures.clear();
    }

    private void loadOrCreate() {
        if (configFile.isFile() && load()) return;
        setConfig(Config.defaults());
        if (!configFile.isFile()) {
            save();
            texturesFolder().mkdirs();
        }
    }

    /** @return false (keeping the old config) if the file is invalid */
    private boolean load() {
        lastModified = configFile.lastModified();
        try {
            setConfig(ConfigCodec.decode(read(configFile)));
            return true;
        } catch (Exception e) {
            platform.warn("Config file " + configFile + " is not valid. The previous settings stay.", e);
            if (config != null) platform.showMessage(NAME + ": config error - " + e.getMessage());
            return false;
        }
    }

    /** Writes the profile to {@code profiles/<name>.json} and copies the same text to the clipboard. */
    public File exportProfile(Profile p) throws IOException {
        String json = ConfigCodec.encodeProfileFile(p);
        File out = new File(new File(dir, "profiles"), p.name().replaceAll("[^A-Za-z0-9_.-]", "_") + ".json");
        Files.createDirectories(out.getParentFile().toPath());
        Files.write(out.toPath(), json.getBytes(StandardCharsets.UTF_8));
        platform.setClipboard(json);
        return out;
    }

    private void save() {
        try {
            dir.mkdirs();
            Files.write(configFile.toPath(), ConfigCodec.encode(config).getBytes(StandardCharsets.UTF_8));
            lastModified = configFile.lastModified();
        } catch (IOException e) {
            platform.warn("Cannot save " + configFile, e);
        }
    }

    private static String read(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }
}
