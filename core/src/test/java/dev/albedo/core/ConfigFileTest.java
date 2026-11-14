package dev.albedo.core;

import dev.albedo.core.config.Config;
import dev.albedo.core.config.ConfigCodec;
import dev.albedo.core.config.Profile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** The config file: its first write, the copy of an invalid file and the export of a profile. */
class ConfigFileTest {
    @TempDir
    Path dir;

    private ConfigFile file() {
        return new ConfigFile(new FakePlatform(dir.toFile()));
    }

    private Path configJson() {
        return dir.resolve("albedo/config.json");
    }

    @Test
    void aMissingFileGetsTheDefaults() throws IOException {
        ConfigFile file = file();
        assertEquals(Config.defaults(), file.loadOrCreate());
        assertEquals(Config.defaults(), ConfigCodec.decode(read(configJson())));
        assertFalse(file.changedOnDisk());
    }

    @Test
    void anInvalidFileKeepsACopyAndStaysAsItIs() throws IOException {
        Files.createDirectories(configJson().getParent());
        Files.write(configJson(), "{ not json".getBytes(StandardCharsets.UTF_8));
        assertEquals(Config.defaults(), file().loadOrCreate());
        assertEquals("{ not json", read(dir.resolve("albedo/config.invalid.json")));
        assertEquals("{ not json", read(configJson()), "the next save writes over it");
    }

    @Test
    void aSavedConfigLoadsAgain() throws IOException {
        ConfigFile file = file();
        Config c = Config.defaults().withEnabled(false);
        file.save(c);
        assertFalse(file.changedOnDisk());
        assertEquals(c, file.load());
        assertTrue(configJson().toFile().setLastModified(configJson().toFile().lastModified() - 10000));
        assertTrue(file.changedOnDisk());
    }

    @Test
    void anExportedProfileGetsAFileName() throws IOException {
        Profile p = Config.defaults().activeProfile().renamed("my profile/1");
        File out = file().exportProfile(p.name(), "{}");
        assertEquals(dir.resolve("albedo/profiles/my_profile_1.json").toFile(), out);
        assertEquals("{}", read(out.toPath()));
    }

    private static String read(Path p) throws IOException {
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }
}
