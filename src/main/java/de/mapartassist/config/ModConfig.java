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
    public static final String DEFAULT_TITLE = "Kevin_Josefs Mod-Menü";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ModConfig INSTANCE = new ModConfig();

    public boolean autoPlace = false;
    public boolean takeFromInventory = true;
    /** Map Art: Blöcke mit Ausrichtung (Piston, Dropper, Treppen ...) nur setzen, wenn sie in der Schematic-Rotation landen. */
    public boolean rotationCheck = true;
    public int delayTicks = 4;
    /** true = place every rendered frame (fast). false = old tick-based click (slower, fallback). */
    public boolean frameMode = false;
    /** Frame mode only: max placements per tick (1 = smoothest, higher = faster but may stutter). */
    public int maxPerTick = 2;
    /** Name des gewaehlten Capes aus der Bibliothek, leer = keins. */
    public String selectedCape = "";
    /** Veraltet (Titel ist jetzt fest). Bleibt nur, damit alte Config-Dateien noch lesbar sind. */
    public String menuTitle = DEFAULT_TITLE;
    /** /sell-Makro: Filterliste, Automatik und Intervall (Sekunden). */
    public List<String> cellItems = new ArrayList<>();
    public boolean cellAuto = false;
    /** /ah sell Auto-Preis: so viele Angebote zum guenstigsten Preis muessen da sein, bevor mein Preis sinkt. */
    public int undercutMin = 2;
    /** Auktions-Makro Tempo: 1 = sicher, 2 = schnell, 3 = sehr schnell (Kick-Gefahr durch Server-Spamschutz). */
    public int macroSpeed = 2;
    /** Zuletzt automatisch gesetzter Preis pro Item (Registry-ID). */
    public Map<String, Long> lastAutoPrice = new HashMap<>();
    /** AFK-Waechter: Entfernung zum AFK-Platz ab der "Spawn" gilt, und Wartezeit zwischen /afk-Versuchen (Sekunden). */
    public int afkRadius = 40;
    public int afkRetrySec = 3;
    /** Nach jedem Verkauf weitermachen, solange noch Items aus der Liste im Inventar sind. */
    public boolean cellRepeat = true;
    public int cellInterval = 30;
    /** Auto-Reparatur der Spitzhacke (XP-Flaschen aus /shop). */
    public boolean repairEnabled = false;
    public int repairThreshold = 30;
    public int repairStacks = 2;
    public boolean repairResume = true;
    public String repairCategory = "Ausrüstung";
    /** Freecam-Geschwindigkeit in Bloecken pro Sekunde. */
    public int freecamSpeed = 8;
    /** Bewegungs-Recorder: endlos wiederholen / Anzahl Wiederholungen / Blickrichtung relativ zum Start. */
    public boolean recEndless = false;
    public int recRepeats = 1;
    public boolean recRelativeYaw = true;
    public String selectedRecording = "";
    /** Schematic-Fortschritt: ausgeschlossene Bloecke (Registry-IDs). */
    public List<String> progressExcluded = new ArrayList<>();
    /** Ausgeschlossene Bloecke zaehlen als fertig (sonst werden sie aus der Rechnung genommen). */
    public boolean progressExcludedDone = true;
    /** Ausgeschlossene Bloecke nicht automatisch platzieren. */
    public boolean progressSkipPlacing = true;
    /** Prozentanzeige oben links im Spiel. */
    public boolean progressHud = false;
    /** Design: 0 Hex, 1 Sterne, 2 Regen, 3 Farbverlauf, 4 Raster, 5 Aurora, 6 Eigenes Bild, 7 Keine. */
    public int bgStyle = 0;
    /** Farbe der Hintergrund-Effekte folgt einem Regenbogen. */
    public boolean rainbowAccent = false;
    /** Rahmen: 0 schlicht, 1 pulsierend (Glow), 2 doppelt. */
    public int borderStyle = 0;
    /** Dateiname des eigenen Hintergrundbilds (liegt in config/mapartassist/backgrounds). */
    public String customBackground = "";
    /** Helligkeit des eigenen Bildes in Prozent. */
    public int customBgDim = 55;
    /** Makros: Items (Registry-IDs, z. B. minecraft:tnt), die verkauft werden sollen. */
    public List<String> sellItems = new ArrayList<>();
    /** Pro Item: wie viele pro Verkauf (0 = ganzer Stack). */
    public Map<String, Integer> sellAmounts = new HashMap<>();
    /** Pro Item: manueller Verkaufspreis (fuer die eingestellte Menge). Fehlt/0 = automatisch aus der Auktion. */
    public Map<String, Long> sellPrices = new HashMap<>();
    /** Pro Item: true = manuellen Preis nutzen, false = guenstigsten Auktionspreis suchen. Fehlt = manuell, falls Preis gesetzt. */
    public Map<String, Boolean> sellUseManual = new HashMap<>();
    /** Makro: nach jedem Verkauf Preis neu pruefen und erneut einstellen, bis das Item aus dem Inventar leer ist. */
    public boolean macroRepeat = true;
    /** HT1 Combat Lock: bei Schaden sofort den Server verlassen. */
    public boolean combatLock = false;
    /** Nur ausloesen, wenn ein anderer Spieler in der Naehe ist (ignoriert Fall-/Feuer-/Hungerschaden). */
    public boolean combatLockNeedsPlayer = true;
    /** Sekunden zwischen Treffer und Verlassen (0 = sofort, max. 19). */
    public int combatLockDelaySec = 0;
    /** Spawner-AFK-Schutz: bei fremdem Spieler im Radius Spawner abbauen und Server verlassen. */
    public boolean spawnerGuard = false;
    /** Radius in Bloecken, ab dem ein anderer Spieler den Schutz ausloest. */
    public int spawnerGuardRadius = 50;
    /** Spawner-Schutz nur in der Naehe der gespeicherten Base aktiv (am Spawn ruht er). */
    public boolean spawnerGuardHomeOnly = false;
    public boolean spawnerHomeSet = false;
    public double spawnerHomeX, spawnerHomeY, spawnerHomeZ;
    public String spawnerHomeDim = "";
    public int spawnerHomeRadius = 40;
    /** true = Spawner mit Silk-Touch-Spitzhacke abbauen, false = nur sofort verlassen. */
    public boolean spawnerGuardMine = true;
    /** Nach dem Abbau einen Befehl/Text senden (z. B. /spawn), statt (nur) den Server zu verlassen. */
    public boolean spawnerGuardCommand = true;
    /** Der Befehl bzw. Text. Beginnt er mit "/", wird er als Befehl gesendet, sonst als Chatnachricht. */
    public String spawnerGuardCommandText = "/spawn";
    /** Nach dem Befehl zusaetzlich den Server verlassen. */
    public boolean spawnerGuardLeaveAfter = false;
    /** Sekunden zwischen Befehl und Verlassen (damit der Teleport ankommt). */
    public int spawnerGuardLeaveDelaySec = 3;
    /** Menue-Einstellungen (Zahnrad oben rechts). */
    public int bgOpacity = 97;
    public boolean hexBackground = true;
    public boolean menuAnimations = true;
    /** Testlauf: Makro zeigt nur an, was es tun wuerde. */
    public boolean macroDryRun = false;
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
                // Dieses Makro soll echte Verkaufsbefehle senden; Testlauf nicht
                // durch einen alten gespeicherten true-Wert stillschweigend aktiv lassen.
                loaded.macroDryRun = false;
                if (loaded.spawnerGuardCommandText == null) loaded.spawnerGuardCommandText = "/spawn";
                // Titel ist fest und nicht mehr aenderbar
                loaded.menuTitle = DEFAULT_TITLE;
                loaded.macroSpeed = Math.max(1, Math.min(3, loaded.macroSpeed <= 0 ? 2 : loaded.macroSpeed));
                if (loaded.spawnerHomeDim == null) loaded.spawnerHomeDim = "";
                loaded.spawnerHomeRadius = Math.max(5, Math.min(300, loaded.spawnerHomeRadius <= 0 ? 40 : loaded.spawnerHomeRadius));
                if (loaded.lastAutoPrice == null) loaded.lastAutoPrice = new HashMap<>();
                loaded.undercutMin = Math.max(1, Math.min(10, loaded.undercutMin <= 0 ? 2 : loaded.undercutMin));
                loaded.afkRadius = Math.max(10, Math.min(300, loaded.afkRadius <= 0 ? 40 : loaded.afkRadius));
                loaded.afkRetrySec = Math.max(1, Math.min(15, loaded.afkRetrySec <= 0 ? 3 : loaded.afkRetrySec));
                if (loaded.cellItems == null) loaded.cellItems = new ArrayList<>();
                if (loaded.repairCategory == null || loaded.repairCategory.isBlank()) loaded.repairCategory = "Ausrüstung";
                loaded.cellInterval = Math.max(5, Math.min(300, loaded.cellInterval));
                loaded.repairThreshold = Math.max(1, Math.min(500, loaded.repairThreshold));
                loaded.repairStacks = Math.max(1, Math.min(4, loaded.repairStacks));
                if (loaded.progressExcluded == null) loaded.progressExcluded = new ArrayList<>();
                if (loaded.selectedRecording == null) loaded.selectedRecording = "";
                loaded.freecamSpeed = Math.max(1, Math.min(40, loaded.freecamSpeed));
                loaded.recRepeats = Math.max(1, Math.min(99, loaded.recRepeats));
                if (loaded.customBackground == null) loaded.customBackground = "";
                loaded.bgStyle = Math.max(0, Math.min(7, loaded.bgStyle));
                loaded.customBgDim = Math.max(0, Math.min(90, loaded.customBgDim));
                loaded.bgOpacity = Math.max(40, Math.min(100, loaded.bgOpacity));
                if (loaded.sellUseManual == null) loaded.sellUseManual = new HashMap<>();
                INSTANCE = loaded;
                save();
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
