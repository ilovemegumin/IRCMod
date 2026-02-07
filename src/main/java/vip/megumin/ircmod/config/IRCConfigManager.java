package vip.megumin.ircmod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

public final class IRCConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DIR_NAME = "irc";
    private static final String FILE_NAME = "config.json";

    private IRCConfigManager() {
    }

    public static Path getPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(DIR_NAME).resolve(FILE_NAME);
    }

    public static IRCConfig load() {
        Path path = getPath();
        ensureDir(path);
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                IRCConfig config = GSON.fromJson(reader, IRCConfig.class);
                if (config != null) {
                    if (config.prefix == null || config.prefix.isBlank()) {
                        config.prefix = "@";
                    }
                    if (config.openConfigKeyCode <= 0) {
                        config.openConfigKeyCode = 74;
                    }
                    return config;
                }
            } catch (IOException ignored) {
            }
        }
        IRCConfig config = new IRCConfig();
        save(config);
        return config;
    }

    public static void save(IRCConfig config) {
        Path path = getPath();
        ensureDir(path);
        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(config, writer);
        } catch (IOException ignored) {
        }
    }

    private static void ensureDir(Path path) {
        try {
            Files.createDirectories(path.getParent());
        } catch (IOException ignored) {
        }
    }
}
