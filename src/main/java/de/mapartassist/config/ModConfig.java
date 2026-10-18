package de.mapartassist.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Einstellungen, werden in config/mapartassist.json gespeichert. */
public class ModConfig {
    public static final int DEFAULT_ACCENT = 0x3FA7FF;
    public static final int DEFAULT_BG = 0x1E1E28;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ModConfig INSTANCE = new ModConfig();

    public boolean autoPlace = false;
    public boolean takeFromInventory = true;
    public int delayTicks = 4;
    /** true = place every rendered frame (fast). false = old tick-based click (slower, fallback). */
    public boolean frameMode = false;
    /** Frame mode only: max placements per tick (1 = smoothest, higher = faster but may stutter). */
    public int maxPerTick = 2;
    public int accentColor = DEFAULT_ACCENT; // RGB
    public int bgColor = DEFAULT_BG;         // RGB

    public static ModConfig get() {
        return INSTANCE;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("mapartassist.json");
    }

    public static void load() {
        Path p = path();
        if (!Files.exists(p)) {
            save();
            return;
        }
        try (Reader r = Files.newBufferedReader(p)) {
            ModConfig loaded = GSON.fromJson(r, ModConfig.class);
            if (loaded != null) INSTANCE = loaded;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        try (Writer w = Files.newBufferedWriter(path())) {
            GSON.toJson(INSTANCE, w);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
