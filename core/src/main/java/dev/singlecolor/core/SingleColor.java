package dev.singlecolor.core;

import dev.singlecolor.core.config.Appearance;
import dev.singlecolor.core.config.Config;
import dev.singlecolor.core.config.ConfigCodec;
import dev.singlecolor.core.config.Presets;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The single entry point adapters talk to. Holds the current config/policy, watches the config file, handles the
 * toggle keys and transforms textures. Adapters call:
 * <ul>
 *   <li>{@link #init(Platform)} once at client start;</li>
 *   <li>{@link #processTexture} whenever a texture image is loaded;</li>
 *   <li>{@link #policy()} from render hooks (tint, sky);</li>
 *   <li>{@link #clientTick(boolean)} every client tick.</li>
 * </ul>
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
    private volatile Config config;
    private volatile Policy policy = Policy.DISABLED;
    private final Map<String, PixelBuffer> userTextures = new ConcurrentHashMap<String, PixelBuffer>();
    private final PixelTransformer.TextureSource textureSource;

    private long lastModified;
    private int ticks;
    private boolean toggleWasDown;
    private boolean cycleWasDown;

    private SingleColor(Platform platform) {
        this.platform = platform;
        this.dir = new File(platform.configDir(), MOD_ID);
        this.configFile = new File(dir, "config.json");
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

    /** May be null before init (e.g. textures loaded very early); callers then leave things untouched. */
    public static SingleColor get() {
        return instance;
    }

    /** Never null: a disabled policy before init. Safe to call from any thread, any time. */
    public static Policy currentPolicy() {
        SingleColor sc = instance;
        return sc == null ? Policy.DISABLED : sc.policy;
    }

    public Policy policy() { return policy; }

    public Config config() { return config; }

    // ---------------------------------------------------------------- textures

    /**
     * Transforms a freshly loaded texture in place.
     *
     * @param namespace resource namespace ("minecraft")
     * @param path      either "textures/block/stone.png" or an atlas sprite name such as "block/stone"
     * @return true if the image was changed
     */
    public static boolean processTexture(String namespace, String path, PixelBuffer image) {
        SingleColor sc = instance;
        if (sc == null) return false;
        Policy p = sc.policy;
        if (!p.enabled()) return false;
        TextureKey key = TextureClassifier.classify(namespace, path);
        Appearance a = p.appearanceFor(key);
        if (a.isOriginal()) return false;
        try {
            boolean changed = PixelTransformer.apply(a, image, sc.textureSource);
            if (changed) sc.countTransformed(key.category());
            return changed;
        } catch (RuntimeException e) {
            sc.platform.warn("Could not transform " + key, e);
            return false;
        }
    }

    /** Same as {@link #processTexture(String, String, PixelBuffer)} for "namespace:path" strings. */
    public static boolean processTexture(String location, PixelBuffer image) {
        int colon = location.indexOf(':');
        return colon < 0 ? processTexture("minecraft", location, image)
                : processTexture(location.substring(0, colon), location.substring(colon + 1), image);
    }

    // Per-category counts since the last summary; logged once loading settles so users can see what was changed.
    private final Map<Category, java.util.concurrent.atomic.AtomicInteger> transformed =
            new ConcurrentHashMap<Category, java.util.concurrent.atomic.AtomicInteger>();
    private volatile boolean pendingSummary;

    private void countTransformed(Category c) {
        java.util.concurrent.atomic.AtomicInteger n = transformed.get(c);
        if (n == null) {
            transformed.putIfAbsent(c, new java.util.concurrent.atomic.AtomicInteger());
            n = transformed.get(c);
        }
        n.incrementAndGet();
        pendingSummary = true;
    }

    private void logSummary() {
        if (!pendingSummary) return;
        pendingSummary = false;
        StringBuilder sb = new StringBuilder();
        int total = 0;
        for (Category c : Category.values()) {
            java.util.concurrent.atomic.AtomicInteger n = transformed.remove(c);
            if (n == null) continue;
            total += n.get();
            sb.append(sb.length() == 0 ? "" : ", ").append(c.key()).append(' ').append(n.get());
        }
        platform.info("Flattened " + total + " textures (" + sb + ") with profile '" + config.activeProfileName() + "'");
    }

    private PixelBuffer userTexture(String name) {
        PixelBuffer cached = userTextures.get(name);
        if (cached != null) return cached;
        File f = new File(new File(dir, "textures"), name);
        try {
            if (!f.getCanonicalPath().startsWith(new File(dir, "textures").getCanonicalPath())) return null;
            PixelBuffer img = PixelBuffer.Heap.copyOf(platform.readImage(f));
            userTextures.put(name, img);
            return img;
        } catch (IOException e) {
            platform.warn("Could not read texture " + f + " (falling back to the color)", e);
            return null;
        }
    }

    // ---------------------------------------------------------------- tick: keys + file watching

    /** @param screenOpen true while a GUI is open (keys are ignored then so typing doesn't toggle). */
    public void clientTick(boolean screenOpen) {
        boolean toggleDown = !screenOpen && isDown(config.toggleKey());
        boolean cycleDown = !screenOpen && isDown(config.cycleKey());
        if (toggleDown && !toggleWasDown) toggle();
        if (cycleDown && !cycleWasDown) cycleProfile();
        toggleWasDown = toggleDown;
        cycleWasDown = cycleDown;

        if (ticks % WATCH_INTERVAL_TICKS == 0) logSummary();
        if (++ticks % WATCH_INTERVAL_TICKS == 0 && configFile.lastModified() != lastModified) {
            Config before = config;
            if (load()) {
                platform.showMessage(NAME + ": config reloaded");
                if (before.enabled() || config.enabled()) platform.reloadResources();
            }
        }
    }

    private boolean isDown(String keyName) {
        Keys.Key key = Keys.byName(keyName);
        return key != null && platform.isKeyDown(key);
    }

    public void toggle() {
        apply(config.withEnabled(!config.enabled()));
        platform.showMessage(NAME + ": " + (config.enabled() ? "on (" + config.activeProfileName() + ")" : "off"));
    }

    public void cycleProfile() {
        apply(config.withActiveProfile(config.nextProfileName()).withEnabled(true));
        platform.showMessage(NAME + ": profile " + config.activeProfileName());
    }

    /** Swap config, persist it, and reload textures. */
    public void apply(Config next) {
        setConfig(next);
        save();
        platform.reloadResources();
    }

    // ---------------------------------------------------------------- persistence

    private void setConfig(Config c) {
        config = c;
        policy = Policy.of(c);
        userTextures.clear();
    }

    private void loadOrCreate() {
        if (!configFile.isFile()) {
            setConfig(new Config(true, "quake-white", "F6", "F7", Presets.all()));
            save();
            new File(dir, "textures").mkdirs();
            return;
        }
        if (!load()) setConfig(new Config(true, "quake-white", "F6", "F7", Presets.all()));
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
