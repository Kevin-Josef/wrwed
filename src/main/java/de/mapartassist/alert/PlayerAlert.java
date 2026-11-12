package de.mapartassist.alert;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import de.mapartassist.config.ModConfig;
import de.mapartassist.mini.Minimi;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Spieler-Alarm: Naehert sich ein anderer Spieler innerhalb des Radius, sendet die Mod automatisch
 * "/msg <Empfaenger> <Name des Spielers>" (z. B. vom AFK-Account an den Hauptaccount).
 * Zusaetzlich wird ein Verlauf aller privaten Nachrichten gefuehrt (empfangene und selbst gesendete).
 */
public final class PlayerAlert {
    private PlayerAlert() {}

    /** Ein Eintrag im MSG-Verlauf. */
    public static final class Msg {
        public long time;
        public boolean outgoing;
        public String name = "";
        public String text = "";
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_HISTORY = 200;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    // Gaengige Formate fuer private Nachrichten (Vanilla + haeufige Plugin-Formate).
    private static final Pattern[] IN_PATTERNS = {
            Pattern.compile("^(\\S{2,16}) whispers to you: (.*)$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(\\S{2,16}) fl\u00FCstert dir zu: (.*)$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\W*(?:(?:msg|pm|dm|privat)\\W+)?\\[?(?:von\\s+|from\\s+)?([A-Za-z0-9_]{2,16})\\]?\\s*"
                    + "(?:->|\u2192|\u00BB|>>|\u2794|\u27A4|\u279C)\\s*\\[?(?:me|you|du|dir|ich|mir)\\b\\]?\\W*(.*)$",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\W*(?:from|von)\\s+([A-Za-z0-9_]{2,16})\\s*[:\u00BB>\\-]+\\s*(.*)$", Pattern.CASE_INSENSITIVE),
    };

    private static final List<Msg> history = new ArrayList<>();
    private static final Set<UUID> inside = new HashSet<>();
    private static final Map<UUID, Long> lastSent = new HashMap<>();
    private static final ArrayDeque<String> queue = new ArrayDeque<>();
    private static int joinGrace = 40;
    private static int sendDelay = 0;
    private static int statusHold = 0;
    private static boolean hooked = false;
    private static String lastLine = "";
    private static long lastLineAt = 0;

    /** Wird im Menue angezeigt. */
    public static String status = "Bereit.";

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("mapartassist").resolve("msg-history.json");
    }

    /** Einmal beim Start aufrufen: laedt den Verlauf und haengt sich in den Chat ein. */
    public static void init() {
        if (hooked) return;
        hooked = true;
        load();
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) onText(message.getString());
        });
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, ts) -> onText(message.getString()));
    }

    public static List<Msg> history() { return history; }

    public static void clearHistory() {
        history.clear();
        save();
    }

    private static void load() {
        Path p = file();
        if (!Files.exists(p)) return;
        try (Reader r = Files.newBufferedReader(p)) {
            List<Msg> l = GSON.fromJson(r, new TypeToken<List<Msg>>() {}.getType());
            if (l != null) {
                history.clear();
                history.addAll(l);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void save() {
        try {
            Files.createDirectories(file().getParent());
            try (Writer w = Files.newBufferedWriter(file())) {
                GSON.toJson(history, w);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void add(boolean outgoing, String name, String text) {
        Msg m = new Msg();
        m.time = System.currentTimeMillis();
        m.outgoing = outgoing;
        m.name = name == null ? "" : name;
        m.text = text == null ? "" : text;
        history.add(m);
        while (history.size() > MAX_HISTORY) history.remove(0);
        save();
    }

    public static String timeOf(Msg m) {
        return LocalTime.ofInstant(java.time.Instant.ofEpochMilli(m.time), java.time.ZoneId.systemDefault()).format(TIME);
    }

    /** Prueft jede Chatzeile, ob sie eine private Nachricht an uns ist. */
    private static void onText(String raw) {
        if (raw == null) return;
        String t = raw.strip();
        if (t.isEmpty()) return;
        long now = System.currentTimeMillis();
        if (t.equals(lastLine) && now - lastLineAt < 250) return; // dieselbe Zeile ueber zwei Events
        lastLine = t;
        lastLineAt = now;
        for (Pattern p : IN_PATTERNS) {
            Matcher m = p.matcher(t);
            if (m.find()) {
                add(false, m.group(1), m.group(2) == null ? "" : m.group(2).strip());
                return;
            }
        }
    }

    public static void tick(MinecraftClient mc) {
        ModConfig cfg = ModConfig.get();
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.getNetworkHandler() == null) {
            inside.clear();
            queue.clear();
            joinGrace = 40; // nach dem Beitritt kurz warten
            return;
        }
        if (!cfg.alertEnabled) {
            if (!inside.isEmpty()) inside.clear();
            queue.clear();
            status = "Ausgeschaltet.";
            return;
        }
        if (joinGrace > 0) {
            joinGrace--;
            status = "Startet gleich ...";
            return;
        }
        String target = cfg.alertTarget == null ? "" : cfg.alertTarget.trim();
        if (target.isEmpty()) {
            status = "Kein Empfänger eingetragen.";
            return;
        }

        double r = Math.max(10, cfg.alertRadius);
        double r2 = r * r;
        long ms = System.currentTimeMillis();
        long cooldownMs = Math.max(5, cfg.alertCooldownSec) * 1000L;
        Set<UUID> now = new HashSet<>();
        for (PlayerEntity o : mc.world.getPlayers()) {
            if (o == p || o.isSpectator() || Minimi.isMini(o)) continue;
            if (p.squaredDistanceTo(o) > r2) continue;
            UUID id = o.getUuid();
            now.add(id);
            if (inside.contains(id)) continue; // war schon im Radius
            String name = o.getName().getString();
            if (name.equalsIgnoreCase(target)) continue; // den Empfaenger selbst nicht melden
            Long last = lastSent.get(id);
            if (last != null && ms - last < cooldownMs) continue;
            lastSent.put(id, ms);
            queue.add(name);
        }
        inside.clear();
        inside.addAll(now);

        if (sendDelay > 0) sendDelay--;
        if (statusHold > 0) statusHold--;
        if (lastSent.size() > 300) lastSent.entrySet().removeIf(e -> ms - e.getValue() > cooldownMs);
        if (sendDelay <= 0 && !queue.isEmpty()) {
            String name = queue.poll();
            String text = (cfg.alertMessage == null || cfg.alertMessage.isBlank() ? "{player}" : cfg.alertMessage)
                    .replace("{player}", name);
            String cmd = cfg.alertCommand == null ? "msg" : cfg.alertCommand.trim();
            if (cmd.startsWith("/")) cmd = cmd.substring(1).trim();
            if (cmd.isEmpty()) cmd = "msg";
            mc.getNetworkHandler().sendChatCommand(cmd + " " + target + " " + text);
            add(true, target, text);
            status = "Gemeldet: " + name + " (an " + target + ")";
            statusHold = 200; // Meldung ca. 10 s stehen lassen
            sendDelay = 30; // Spamschutz: hoechstens eine Nachricht pro 1,5 s
        } else if (queue.isEmpty() && statusHold <= 0) {
            status = "Überwache Umgebung (" + (int) r + " Blöcke) ...";
        }
    }
}
