package dev.blockpainter.core;

import dev.blockpainter.core.config.Appearance;
import dev.blockpainter.core.config.Config;
import dev.blockpainter.core.config.ConfigCodec;
import dev.blockpainter.core.dev.Harness;
import dev.blockpainter.core.texture.PixelTransformer;
import dev.blockpainter.core.texture.TextureClassifier;
import dev.blockpainter.core.texture.TextureKey;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The single entry point adapters talk to. Holds the current config/policy, watches the config file, handles the
 * keys and transforms textures. Adapters call {@link #init} once, {@link #processTexture} for every loaded texture,
 * {@link #currentPolicy()} from render hooks and {@link #clientTick} every client tick.
 */
public final class BlockPainter {
    public static final String MOD_ID = "blockpainter";
    public static final String NAME = "Block Painter";
    private static final int WATCH_INTERVAL_TICKS = 20;

    private static volatile BlockPainter instance;

    private final Platform platform;
    private final File dir;
    private final File configFile;
    private final Harness harness;
    private volatile Config config;
    private volatile Policy policy = Policy.DISABLED;
    private final Map<String, PixelBuffer> userTextures = new ConcurrentHashMap<String, PixelBuffer>();
    /** Every texture seen since start, with its original average color: what the settings screen lists. */
    private final Map<TextureKey, Integer> catalog = new ConcurrentHashMap<TextureKey, Integer>();
    private final PixelTransformer.TextureSource textureSource;

    private long lastModified;
    private int ticks;
    private final AtomicInteger transformedSinceSummary = new AtomicInteger();
    private final boolean[] keyWasDown = new boolean[3];

    private BlockPainter(Platform platform) {
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
    public static synchronized BlockPainter init(Platform platform) {
        if (instance == null) {
            BlockPainter sc = new BlockPainter(platform);
            sc.loadOrCreate();
            instance = sc;
            platform.info(NAME + " ready on " + platform.describe() + ", profile '" + sc.config.activeProfileName()
                    + "', " + (sc.config.enabled() ? "enabled" : "disabled"));
        }
        return instance;
    }

    /** May be null before init. */
    public static BlockPainter get() {
        return instance;
    }

    /** Never null: a disabled policy before init. Safe to call from any thread, any time. */
    public static Policy currentPolicy() {
        BlockPainter sc = instance;
        return sc == null ? Policy.DISABLED : sc.policy;
    }

    public Config config() { return config; }

    public Platform platform() { return platform; }

    // ---------------------------------------------------------------- textures

    /**
     * Records and transforms a freshly loaded texture in place.
     *
     * @param location "minecraft:textures/block/stone.png" or a sprite name with its folder ("minecraft:block/stone")
     * @return true if the image was changed
     */
    public static boolean processTexture(String location, PixelBuffer image) {
        BlockPainter sc = instance;
        if (sc == null) return false;
        TextureKey key = TextureClassifier.classify(location);
        try {
            if (key.category().hasGroups() && key.category() != Category.COLORMAPS && key.category() != Category.OTHER) {
                // Only what the settings screen can list is recorded; the average is its preview color.
                Integer average = PixelTransformer.average(image);
                if (!average.equals(sc.catalog.put(key, average))) sc.catalogVersion.incrementAndGet();
            }
            Appearance a = sc.policy.appearanceFor(key);
            if (a.isOriginal()) return false;
            boolean changed = PixelTransformer.apply(a, image, sc.textureSource);
            if (changed) sc.transformedSinceSummary.incrementAndGet();
            return changed;
        } catch (RuntimeException e) {
            sc.platform.warn("Cannot change texture " + key, e);
            return false;
        }
    }

    // Sorted per-category views of the catalog, rebuilt only when a texture was added or its color changed.
    private final AtomicInteger catalogVersion = new AtomicInteger();
    private final Map<Category, Map<String, Integer>> catalogViews = new EnumMap<Category, Map<String, Integer>>(Category.class);
    private int catalogViewsVersion = -1;

    /** Ids seen in a category, sorted, each with its original average color. Read-only; client thread. */
    public Map<String, Integer> catalog(Category category) {
        int version = catalogVersion.get();
        if (version != catalogViewsVersion) {
            catalogViews.clear();
            catalogViewsVersion = version;
        }
        Map<String, Integer> view = catalogViews.get(category);
        if (view == null) {
            Map<String, Integer> out = new TreeMap<String, Integer>();
            for (Map.Entry<TextureKey, Integer> e : catalog.entrySet()) {
                if (e.getKey().category() == category) out.put(e.getKey().id(), e.getValue());
            }
            view = Collections.unmodifiableMap(out);
            catalogViews.put(category, view);
        }
        return view;
    }

    private volatile List<String> textureNames = Collections.emptyList();
    private long texturesModified = -1;

    /** PNG names in the user textures folder, for the TEXTURE mode picker (refreshed by the file watcher). */
    public List<String> userTextureNames() {
        return textureNames;
    }

    /** Re-reads the textures folder when it changed; dropping cached images makes edited PNGs apply on next reload. */
    private void watchTexturesFolder() {
        File folder = texturesFolder();
        if (folder.lastModified() == texturesModified) return;
        texturesModified = folder.lastModified();
        File[] files = folder.listFiles();
        List<String> names = new ArrayList<String>();
        if (files != null) {
            for (File f : files) if (f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".png")) names.add(f.getName());
        }
        Collections.sort(names);
        textureNames = Collections.unmodifiableList(names);
        userTextures.clear();
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
                Config before = config;
                if (load()) {
                    platform.showMessage(NAME + ": config reloaded");
                    if (!before.sameRendering(config)) platform.reloadResources();
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

    /**
     * Swap config, persist it, tell the player what changed, and reload resources if textures, shading or foliage
     * changed (they're baked into textures and chunk meshes). Sky color and fullbright apply immediately.
     */
    public void apply(Config next) {
        Config before = config;
        if (next.equals(before)) return;
        setConfig(next);
        save();
        if (before.enabled() != next.enabled() || !before.activeProfileName().equals(next.activeProfileName())) {
            platform.showMessage(NAME + ": " + (next.enabled() ? next.activeProfileName() : "off"));
        }
        if (!before.sameRendering(next)) platform.reloadResources();
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
