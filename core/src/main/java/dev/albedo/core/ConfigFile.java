package dev.albedo.core;

import dev.albedo.core.config.Config;
import dev.albedo.core.config.ConfigCodec;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** The config file in {@code <config dir>/albedo/config.json} and the profile files beside it. */
final class ConfigFile {
    private final Platform platform;
    private final File dir;
    private final File file;
    /** The time stamp of the file at the last read or write. */
    private long lastModified;

    ConfigFile(Platform platform) {
        this.platform = platform;
        this.dir = new File(platform.configDir(), Albedo.MOD_ID);
        this.file = new File(dir, "config.json");
    }

    /** The config in the file, or the defaults; a missing file gets the defaults, and an invalid file keeps a copy. */
    Config loadOrCreate() {
        if (!file.isFile()) {
            Config defaults = Config.defaults();
            save(defaults);
            return defaults;
        }
        try {
            return load();
        } catch (IOException | RuntimeException e) {
            warnInvalid(e);
            backUpInvalidFile();
            return Config.defaults();
        }
    }

    /** Reads the file; the time stamp moves also for an invalid file, so only the next change on disk reads it again. */
    Config load() throws IOException {
        lastModified = file.lastModified();
        return ConfigCodec.decode(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
    }

    /** Whether a program other than this one wrote the file since the last read or write. */
    boolean changedOnDisk() {
        return file.lastModified() != lastModified;
    }

    void warnInvalid(Exception e) {
        platform.warn("Config file " + file + " is not valid. The previous settings stay.", e);
    }

    void save(Config config) {
        try {
            dir.mkdirs();
            Files.write(file.toPath(), ConfigCodec.encode(config).getBytes(StandardCharsets.UTF_8));
            lastModified = file.lastModified();
        } catch (IOException e) {
            platform.warn("Cannot save " + file, e);
        }
    }

    /** Keeps a copy of a config file that does not load, because the next save writes over it. */
    private void backUpInvalidFile() {
        File copy = new File(dir, "config.invalid.json");
        try {
            Files.copy(file.toPath(), copy.toPath(), StandardCopyOption.REPLACE_EXISTING);
            platform.warn("The config file is not valid. A copy is in " + copy + ".", null);
        } catch (IOException e) {
            platform.warn("Cannot copy " + file, e);
        }
    }

    /** Writes the text of a profile file to {@code profiles/<name>.json}. */
    File exportProfile(String name, String json) throws IOException {
        File out = new File(new File(dir, "profiles"), name.replaceAll("[^A-Za-z0-9_.-]", "_") + ".json");
        Files.createDirectories(out.getParentFile().toPath());
        Files.write(out.toPath(), json.getBytes(StandardCharsets.UTF_8));
        return out;
    }
}
