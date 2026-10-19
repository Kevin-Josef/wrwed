package de.mapartassist.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    /** Name des gewaehlten Capes aus der Bibliothek, leer = keins. */
    public String selectedCape = "";
    /** Titel im Menue-Kopf. Erstes Wort wird in der Akzentfarbe gezeigt. */
    public String menuTitle = "Kevin_Josefs Mod-Menü";
    /** Makros: Items (Registry-IDs, z. B. minecraft:tnt), die verkauft werden sollen. */
    public List<String> sellItems = new ArrayList<>();
    /** Pro Item: wie viele pro Verkauf (0 = ganzer Stack). */
    public Map<String, Integer> sellAmounts = new HashMap<>();
    /** Pro Item: manueller Verkaufspreis (fuer die eingestellte Menge). Fehlt/0 = automatisch aus der Auktion. */
    public Map<String, Long> sellPrices = new HashMap<>();
    /** Makro: nach jedem Verkauf Preis neu pruefen und erneut einstellen, bis das Item aus dem Inventar leer ist. */
    public boolean macroRepeat = true;
    /** HT1 Combat Lock: bei Schaden sofort den Server verlassen. */
    public boolean combatLock = false;
    /** Nur ausloesen, wenn ein anderer Spieler in der Naehe ist (ignoriert Fall-/Feuer-/Hungerschaden). */
    public boolean combatLockNeedsPlayer = true;
    /** Sekunden zwischen Treffer und Verlassen (0 = sofort, max. 19). */
    public int combatLockDelaySec = 0;
    /** Testlauf: Makro zeigt nur an, was es tun wuerde. */
    public boolean macroDryRun = true;
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
            if (loaded != null) {
                // alter Standardtitel (falsch geschrieben) wird automatisch korrigiert
                if ("Kevin_Josephs Mod-Menü".equals(loaded.menuTitle)) loaded.menuTitle = "Kevin_Josefs Mod-Menü";
                INSTANCE = loaded;
            }
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
