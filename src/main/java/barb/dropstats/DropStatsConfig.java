package barb.dropstats;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

final class DropStatsConfig {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("dropstats.properties");
    private static final Properties VALUES = new Properties();

    private DropStatsConfig() {}

    static void load() {
        if (!Files.isRegularFile(FILE)) return;
        try (InputStream input = Files.newInputStream(FILE)) {
            VALUES.load(input);
        } catch (IOException exception) {
            System.err.println("[DropStats] Could not load config: " + exception.getMessage());
        }
    }

    static DropStatsClient.DisplayFormat getFormat() {
        return "fraction".equalsIgnoreCase(VALUES.getProperty("defaultFormat"))
                ? DropStatsClient.DisplayFormat.FRACTION : DropStatsClient.DisplayFormat.PERCENT;
    }

    static long getAttempts() { return parseCount("attempts"); }
    static long getDrops() { return parseCount("drops"); }

    static void setFormat(DropStatsClient.DisplayFormat format) {
        VALUES.setProperty("defaultFormat", format.commandName());
        save();
    }

    static void setCounts(long attempts, long drops) {
        VALUES.setProperty("attempts", Long.toString(attempts));
        VALUES.setProperty("drops", Long.toString(drops));
        save();
    }

    private static long parseCount(String key) {
        try { return Math.max(0, Long.parseLong(VALUES.getProperty(key, "0"))); }
        catch (NumberFormatException ignored) { return 0; }
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream output = Files.newOutputStream(FILE)) {
                VALUES.store(output, "DropStats settings");
            }
        } catch (IOException exception) {
            System.err.println("[DropStats] Could not save config: " + exception.getMessage());
        }
    }
}
