package dev.albedo.core;

import dev.albedo.core.config.Appearance;
import dev.albedo.core.config.Config;
import dev.albedo.core.config.ConfigCodec;
import dev.albedo.core.config.Profile;
import dev.albedo.core.config.ServerRule;
import dev.albedo.core.dev.Harness;
import dev.albedo.core.sky.SkyBaker;
import dev.albedo.core.sky.SkyImage;
import dev.albedo.core.texture.Layout;
import dev.albedo.core.texture.PixelTransformer;
import dev.albedo.core.texture.TextureStore;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** The one entry point of the core for the adapters, which hold the config, watch its file, read the keys and change textures. */
public final class Albedo {
    public static final String MOD_ID = "albedo";
    public static final String NAME = "Albedo";
    private static final int WATCH_INTERVAL_TICKS = 20;

    private static volatile Albedo instance;

    private final Platform platform;
    private final File dir;
    private final File configFile;
    private final Harness harness;
    /** The config as the file stores it. */
    private volatile Config config;
    /** The config with the state of the current server, which {@link #update} keeps in step with its inputs. */
    private volatile Config effective;
    private volatile Policy policy = Policy.DISABLED;
    /** The host of the multiplayer server the player is on, or null. */
    private String server;
    /** The state the player chose on this server with F6, F7 or the settings, which lasts until the player leaves. */
    private Config.State visitState;
    private final Map<String, PixelBuffer> userTextures = new ConcurrentHashMap<String, PixelBuffer>();
    private final TextureStore store = new TextureStore();
    /** The sky in the world, which bakes only when a config with new layers is applied. */
    private final SkyBaker skyBaker;
    private final PixelTransformer.TextureSource textureSource = this::userTexture;

    private long lastModified;
    private int ticks;
    /** A worker thread recorded a texture while the policy changed, so the next client tick repaints it. */
    private volatile boolean stale;
    private final AtomicInteger transformedSinceSummary = new AtomicInteger();
    private final boolean[] keyWasDown = new boolean[3];

    private Albedo(Platform platform) {
        this.platform = platform;
        this.dir = new File(platform.configDir(), MOD_ID);
        this.configFile = new File(dir, "config.json");
        this.harness = Harness.fromSystemProperty(platform);
        this.skyBaker = new SkyBaker(SKY_WIDTH, SKY_HEIGHT, e -> platform.warn("Cannot make the custom sky", e));
    }

    /** The size of the sky image in the world: about 5.7 texels for each degree, from 15 degrees below the horizon to the zenith. */
    public static final int SKY_WIDTH = 2048, SKY_HEIGHT = 512;

    /** Starts the core once; later calls return the same instance. */
    public static synchronized Albedo init(Platform platform) {
        if (instance == null) {
            Albedo sc = new Albedo(platform);
            sc.loadOrCreate();
            instance = sc;
            platform.info(NAME + " ready on " + platform.describe() + ", " + sc.config.state());
        }
        return instance;
    }

    /** The core, or null before {@link #init}. */
    public static Albedo get() {
        return instance;
    }

    /** The policy for any thread at any time, which is the disabled policy before {@link #init}. */
    public static Policy currentPolicy() {
        Albedo sc = instance;
        return sc == null ? Policy.DISABLED : sc.policy;
    }

    /** The config in effect, which on a server has the rule's state or the state the player chose there. */
    public Config config() { return effective; }

    /** The config as the file stores it, which is what {@link #apply} takes. */
    public Config storedConfig() { return config; }

    /** The host of the server the player is on, or null in single player and the menus. */
    public String server() { return server; }

    /** Recomputes the config in effect and its policy after a change to the config, the server or the visit state. */
    private void update() {
        Config c = config;
        ServerRule rule = server == null ? null : c.serverRule(server);
        if (visitState != null && c.profiles().containsKey(visitState.profile)) c = c.withState(visitState);
        else if (rule != null && rule.profile() == null) c = c.withEnabled(false);
        else if (rule != null) c = c.withState(new Config.State(true, rule.profile()));
        effective = c;
        policy = Policy.of(c);
        skyBaker.request(policy.skyDesign());
    }

    /**
     * The custom sky to draw, or null for the vanilla or flat sky and until the first bake is done, which an adapter
     * reads on the render thread.
     */
    public static SkyImage skyImage() {
        Albedo sc = instance;
        return sc == null || sc.policy.skyStyle() != Policy.SkyStyle.LAYERS ? null : sc.skyBaker.image();
    }

    public Platform platform() { return platform; }

    // ---------------------------------------------------------------- textures

    /**
     * Keeps the original of a freshly loaded texture and changes the image in place.
     *
     * @param location "minecraft:textures/block/stone.png" or a sprite name with its folder ("minecraft:block/stone")
     * @param upload how a later repaint writes into the texture, or null for a copy that only gets changed
     * @return true if the image was changed
     */
    public static boolean processTexture(String location, PixelBuffer image, TextureStore.Upload upload) {
        Albedo sc = instance;
        if (sc == null || sc.store.repainting()) return false;
        try {
            Policy policy = sc.policy;
            Layout layout = Layout.of(location);
            List<Appearance> looks = layout.looks(policy);
            sc.store.record(location, layout, image, upload, looks);
            // The client thread can apply a new policy while a worker thread loads this texture with the old one.
            if (sc.policy != policy) {
                sc.store.invalidate(location);
                sc.stale = true;
            }
            boolean changed = layout.paint(looks, image, sc.textureSource);
            if (changed) sc.transformedSinceSummary.incrementAndGet();
            return changed;
        } catch (RuntimeException e) {
            sc.platform.warn("Cannot change texture " + location, e);
            return false;
        }
    }

    /** Changes a biome colormap in the ARGB array that the game reads by reference, now and on each repaint. */
    public static void processColormap(String location, int[] pixels) {
        if (pixels != null && pixels.length == COLORMAP_SIZE * COLORMAP_SIZE) {
            processTexture(location, new PixelBuffer.Heap(COLORMAP_SIZE, COLORMAP_SIZE, pixels), TextureStore.intoArray(pixels));
        }
    }

    private static final int COLORMAP_SIZE = 256;

    /** True on the thread of a running repaint, where load hooks must skip their work. */
    public static boolean repainting() {
        Albedo sc = instance;
        return sc != null && sc.store.repainting();
    }

    /** Ids seen in a category in name order, each with its original average color, for the client thread. */
    public Map<String, Integer> catalog(Category category) {
        return store.catalog(category);
    }

    private volatile List<String> textureNames = Collections.emptyList();
    private long texturesModified = -1;

    /** PNG names in the user textures folder, for the TEXTURE mode picker (refreshed by the file watcher). */
    public List<String> userTextureNames() {
        return textureNames;
    }

    private void watchTexturesFolder() {
        File folder = texturesFolder();
        File[] files = folder.listFiles();
        // The folder's own time changes when a file is added or removed; a file's time when it is saved again.
        long modified = folder.lastModified();
        if (files != null) for (File f : files) modified = Math.max(modified, f.lastModified());
        if (modified == texturesModified) return;
        boolean first = texturesModified == -1;
        texturesModified = modified;
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
        watchServer();
        Config.KeyBinds keys = config.keys();
        if (pressed(0, keys.toggle, screenOpen)) toggle();
        if (pressed(1, keys.cycleProfile, screenOpen)) cycleProfile();
        if (pressed(2, keys.settings, screenOpen)) platform.openSettings();

        if (++ticks % WATCH_INTERVAL_TICKS == 0) {
            watchTexturesFolder();
            int transformed = transformedSinceSummary.getAndSet(0);
            if (transformed > 0) {
                platform.info("Changed " + transformed + " textures with profile '" + config().activeProfileName() + "'");
            }
            if (configFile.lastModified() != lastModified) {
                Policy before = policy;
                if (load()) {
                    platform.showMessage(NAME + ": config reloaded");
                    refresh(before, false);
                }
            }
        }
        if (stale) refresh(policy, false);
        if (harness != null) harness.tick();
    }

    private boolean pressed(int slot, String keyName, boolean screenOpen) {
        Keys.Key key = Keys.byName(keyName);
        boolean down = !screenOpen && key != null && platform.isKeyDown(key);
        boolean edge = down && !keyWasDown[slot];
        keyWasDown[slot] = down;
        return edge;
    }

    public void toggle() {
        apply(config, new Config.State(!effective.enabled(), effective.activeProfileName()));
    }

    public void cycleProfile() {
        apply(config, new Config.State(true, effective.profileNameAt(1)));
    }

    /** Saves a config as the file stores it, and shows it in the world. */
    public RefreshResult apply(Config next) {
        return apply(next, null);
    }

    /**
     * Saves a config as the file stores it, with a state the player chose, which lasts only for the visit on a server
     * with a rule, and shows the result in the world.
     *
     * @param chosen the on or off state and profile that the player chose, or null to keep the state in effect
     */
    public RefreshResult apply(Config next, Config.State chosen) {
        Config before = effective, previous = config;
        Config saved = next;
        if (chosen != null) {
            if (server != null && next.serverRule(server) != null) visitState = chosen;
            else saved = next.withState(chosen);
        }
        Policy old = policy;
        setConfig(saved);
        if (effective.equals(before) && saved.equals(previous)) return RefreshResult.painted(0, false, 0);
        save();
        if (!effective.state().equals(before.state())) platform.showMessage(NAME + ": " + effective.state());
        return refresh(old, false);
    }

    /** Applies the rule of the server the player joins, and the stored state again when the player leaves. */
    private void watchServer() {
        String host = ServerRule.host(platform.currentServer());
        String now = host.isEmpty() ? null : host;
        if (Objects.equals(now, server)) return;
        Config.State before = effective.state();
        Policy old = policy;
        server = now;
        visitState = null;
        update();
        Config.State after = effective.state();
        if (after.equals(before)) return;
        // On leave there is no player to show a message to.
        if (now != null) platform.showMessage(NAME + ": " + after + " on " + now);
        else platform.info("Left the server, " + after);
        refresh(old, false);
    }

    /** Shows the current policy in the world by the cheapest path that reaches all that changed from {@code old}. */
    private RefreshResult refresh(Policy old, boolean userImagesChanged) {
        stale = false; // before the policy is read, so that a texture recorded after this point sets it again
        Policy now = policy;
        RefreshResult result = paint(old, now, userImagesChanged);
        String line = result.describe();
        if (result.outcome == RefreshResult.Outcome.RELOADED) {
            platform.warn(line, null);
            platform.reloadResources();
        } else if (line != null) {
            platform.info(line);
        }
        return result;
    }

    /** Repaints textures and rebuilds chunk meshes, which hold tints and shading, or asks for a reload. */
    private RefreshResult paint(Policy old, Policy now, boolean userImagesChanged) {
        if (!store.complete() && (userImagesChanged || !old.sameTextures(now))) {
            return RefreshResult.reloaded("the resource packs have more textures than the mod keeps");
        }
        long start = System.nanoTime();
        TextureStore.Result r;
        boolean flushed;
        try {
            r = store.repaint(now, textureSource, userImagesChanged);
        } finally {
            flushed = platform.finishRepaint();
        }
        if (r.needReload > 0) return RefreshResult.reloaded(r.needReload + " textures cannot be repainted");
        if (!flushed) return RefreshResult.reloaded("the repainted textures cannot be sent to the GPU");
        boolean meshes = old.shading() != now.shading() || old.removeTint(Category.BLOCKS) != now.removeTint(Category.BLOCKS)
                || old.removeTint(Category.FLUIDS) != now.removeTint(Category.FLUIDS);
        if (meshes) platform.rebuildChunks();
        return RefreshResult.painted(r.repainted, meshes, (System.nanoTime() - start) / 1000000);
    }

    // ---------------------------------------------------------------- persistence

    private void setConfig(Config c) {
        config = c;
        update();
        userTextures.clear();
    }

    private void loadOrCreate() {
        if (configFile.isFile() && load()) return;
        if (configFile.isFile()) backUpInvalidFile();
        setConfig(Config.defaults());
        if (!configFile.isFile()) {
            save();
            texturesFolder().mkdirs();
        }
    }

    /** Keeps a copy of a config file that does not load, because the next save writes over it. */
    private void backUpInvalidFile() {
        File copy = new File(dir, "config.invalid.json");
        try {
            Files.copy(configFile.toPath(), copy.toPath(), StandardCopyOption.REPLACE_EXISTING);
            platform.warn("The config file is not valid. A copy is in " + copy + ".", null);
        } catch (IOException e) {
            platform.warn("Cannot copy " + configFile, e);
        }
    }

    /** @return false if the file is not valid, which keeps the current config */
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
