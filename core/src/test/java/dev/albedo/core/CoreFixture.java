package dev.albedo.core;

import dev.albedo.core.config.Config;
import dev.albedo.core.config.ConfigCodec;
import dev.albedo.core.texture.TextureStore;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/** The one core of the test JVM, on a {@link FakePlatform}, with the test sprites loaded. */
public final class CoreFixture {
    // Albedo.init keeps the first instance for the JVM, so the config folder lives as long as the JVM: the folder of
    // one test class would be deleted while the core still writes to it.
    private static Albedo albedo;
    private static FakePlatform platform;
    private static Path dir;

    private CoreFixture() {}

    public static synchronized Albedo albedo() {
        if (albedo == null) {
            try {
                dir = Files.createTempDirectory("albedo-test");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            Runtime.getRuntime().addShutdownHook(new Thread(CoreFixture::deleteDir));
            platform = new FakePlatform(dir.toFile());
            albedo = Albedo.init(platform);
            load("minecraft:block/oak_leaves", 0xFF2E7D32);
            for (String id : new String[]{"stone", "dirt", "gravel", "grass_block_top", "grass_block_side", "grass_block_snow",
                "grass_block_side_overlay", "dirt_path_top", "dirt_path_side"}) {
                load("minecraft:block/" + id, 0xFF7F7F7F);
            }
            // Fluids and items are atlas sprites too, so that Chroma shows on their pages.
            load("minecraft:block/water_still", 0xFF3F76E4);
            load("minecraft:item/stick", 0xFF7F7F7F);
        }
        return albedo;
    }

    public static FakePlatform platform() {
        albedo();
        return platform;
    }

    /** The config folder of the platform. */
    public static File dir() {
        albedo();
        return dir.toFile();
    }

    /** Leaves the server, applies the defaults and resets the platform, for the start of each test. */
    public static void reset() {
        playOn(null);
        albedo().apply(Config.defaults());
        platform.reset();
    }

    /** Loads a block atlas sprite, whose Chroma colors the GPU pass moves as in the game. */
    public static void load(String location, int argb) {
        Albedo.processTexture(location, new PixelBuffer.Heap(1, 1, new int[]{argb}), new TextureStore.Upload() {
            @Override
            public boolean upload(PixelBuffer pixels) {
                platform.uploads++;
                return true;
            }

            @Override
            public boolean gpuHue() {
                return true;
            }
        });
    }

    /** Joins this server, or leaves the server when it is null, as the next client tick sees it. */
    public static void playOn(String server) {
        platform().server = server;
        albedo().clientTick(false);
    }

    /** The defaults plus the user's own profile "test", which is active. */
    public static Config withOwnProfile() {
        Config c = Config.defaults();
        return c.withImportedProfile(c.activeProfile().renamed("test"));
    }

    /** The config in the file. */
    public static Config saved() throws IOException {
        return ConfigCodec.decode(new String(Files.readAllBytes(dir.resolve("albedo/config.json")), StandardCharsets.UTF_8));
    }

    private static void deleteDir() {
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        } catch (IOException ignored) {
            // The system deletes temp files later.
        }
    }
}
