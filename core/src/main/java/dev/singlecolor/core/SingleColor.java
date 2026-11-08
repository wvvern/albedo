package dev.singlecolor.core;

import dev.singlecolor.core.config.Appearance;
import dev.singlecolor.core.config.Config;
import dev.singlecolor.core.config.ConfigCodec;
import dev.singlecolor.core.dev.Harness;
import dev.singlecolor.core.texture.PixelTransformer;
import dev.singlecolor.core.texture.TextureClassifier;
import dev.singlecolor.core.texture.TextureKey;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The single entry point adapters talk to. Holds the current config/policy, watches the config file, handles the
 * keys and transforms textures. Adapters call {@link #init} once, {@link #processTexture} for every loaded texture,
 * {@link #currentPolicy()} from render hooks and {@link #clientTick} every client tick.
 */
public final class SingleColor {
    public static final String MOD_ID = "singlecolor";
    public static final String NAME = "Single Color";
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final int WATCH_INTERVAL_TICKS = 20;

    private static volatile SingleColor instance;

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
    private volatile int transformedSinceSummary;
    private final boolean[] keyWasDown = new boolean[3];

    private SingleColor(Platform platform) {
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
    public static synchronized SingleColor init(Platform platform) {
        if (instance == null) {
            SingleColor sc = new SingleColor(platform);
            sc.loadOrCreate();
            instance = sc;
            platform.info(NAME + " ready on " + platform.describe() + ", profile '" + sc.config.activeProfileName()
                    + "', " + (sc.config.enabled() ? "enabled" : "disabled"));
        }
        return instance;
    }

    /** May be null before init. */
    public static SingleColor get() {
        return instance;
    }

    /** Never null: a disabled policy before init. Safe to call from any thread, any time. */
    public static Policy currentPolicy() {
        SingleColor sc = instance;
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
        SingleColor sc = instance;
        if (sc == null) return false;
        TextureKey key = TextureClassifier.classify(location);
        sc.catalog.put(key, PixelTransformer.average(image));
        Appearance a = sc.policy.appearanceFor(key);
        if (a.isOriginal()) return false;
        try {
            boolean changed = PixelTransformer.apply(a, image, sc.textureSource);
            if (changed) sc.transformedSinceSummary++;
            return changed;
        } catch (RuntimeException e) {
            sc.platform.warn("Could not transform " + key, e);
            return false;
        }
    }

    /** Ids seen in a category, sorted, each with its original average color. */
    public Map<String, Integer> catalog(Category category) {
        Map<String, Integer> out = new TreeMap<String, Integer>();
        for (Map.Entry<TextureKey, Integer> e : catalog.entrySet()) {
            if (e.getKey().category() == category) out.put(e.getKey().id(), e.getValue());
        }
        return out;
    }

    /** PNG names in the user textures folder, for the TEXTURE mode picker. */
    public List<String> userTextureNames() {
        File[] files = new File(dir, "textures").listFiles();
        if (files == null) return Collections.emptyList();
        List<String> names = new ArrayList<String>();
        for (File f : files) if (f.isFile() && f.getName().toLowerCase().endsWith(".png")) names.add(f.getName());
        Collections.sort(names);
        return names;
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
            platform.warn("Could not read texture " + f + " (falling back to the color)", e);
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
            if (transformedSinceSummary > 0) {
                platform.info("Flattened " + transformedSinceSummary + " textures with profile '" + config.activeProfileName() + "'");
                transformedSinceSummary = 0;
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
            platform.warn("Invalid " + configFile + ", keeping the previous settings", e);
            if (config != null) platform.showMessage(NAME + ": config error - " + e.getMessage());
            return false;
        }
    }

    private void save() {
        try {
            dir.mkdirs();
            OutputStream out = new FileOutputStream(configFile);
            try {
                out.write(ConfigCodec.encode(config).getBytes(UTF8));
            } finally {
                out.close();
            }
            lastModified = configFile.lastModified();
        } catch (IOException e) {
            platform.warn("Could not save " + configFile, e);
        }
    }

    private static String read(File f) throws IOException {
        InputStream in = new FileInputStream(f);
        try {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) buf.write(b, 0, n);
            return new String(buf.toByteArray(), UTF8);
        } finally {
            in.close();
        }
    }
}
