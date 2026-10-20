package de.mapartassist.macro;

import com.google.gson.Gson;
import de.mapartassist.cam.Freecam;
import de.mapartassist.config.ModConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Bewegungs-Recorder: zeichnet Tasten und Blickrichtung pro Tick auf und spielt sie wieder ab
 * (z. B. einen 2x1-Gang graben). Aufnahmen liegen in config/mapartassist/recordings.
 */
public final class MovementRecorder {
    private MovementRecorder() {}

    public enum State { IDLE, RECORDING, PLAYING }

    public static final class Rec {
        public String name;
        public float[] yaw;
        public float[] pitch;
        public int[] flags;

        public int size() { return flags == null ? 0 : flags.length; }
    }

    private static final int FWD = 1, BACK = 2, LEFT = 4, RIGHT = 8, JUMP = 16, SNEAK = 32, SPRINT = 64, ATTACK = 128, USE = 256;
    private static final int MIN_TICKS = 5;
    private static final Gson GSON = new Gson();

    public static State state = State.IDLE;
    public static String status = "";

    private static final List<Rec> recs = new ArrayList<>();
    private static boolean scanned = false;

    // Aufnahme
    private static final List<float[]> frames = new ArrayList<>(); // {yaw, pitch, flags}

    // Wiedergabe
    private static Rec playing;
    private static int idx, repeatsDone;
    private static float baseYaw;
    private static float lastHealth;

    // ------------------------------------------------------------------ Dateien

    public static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("mapartassist").resolve("recordings");
    }

    public static List<Rec> list() {
        if (!scanned) scan();
        return recs;
    }

    public static Rec find(String name) {
        if (name == null) return null;
        for (Rec r : list()) if (r.name.equals(name)) return r;
        return null;
    }

    public static void scan() {
        scanned = true;
        recs.clear();
        try {
            Files.createDirectories(dir());
            List<Path> files = new ArrayList<>();
            try (Stream<Path> s = Files.list(dir())) {
                s.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().forEach(files::add);
            }
            for (Path p : files) {
                try (Reader r = Files.newBufferedReader(p)) {
                    Rec rec = GSON.fromJson(r, Rec.class);
                    if (rec != null && rec.name != null && rec.flags != null && rec.yaw != null && rec.pitch != null
                            && rec.yaw.length == rec.flags.length && rec.pitch.length == rec.flags.length) {
                        recs.add(rec);
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static String fileName(String name) {
        return name.replaceAll("[^A-Za-z0-9_-]", "_") + ".json";
    }

    private static void save(Rec r) {
        try {
            Files.createDirectories(dir());
            try (Writer w = Files.newBufferedWriter(dir().resolve(fileName(r.name)))) {
                GSON.toJson(r, w);
            }
        } catch (Exception e) {
            status = "Speichern fehlgeschlagen: " + e.getMessage();
        }
    }

    public static void delete(String name) {
        Rec r = find(name);
        if (r == null) return;
        try {
            Files.deleteIfExists(dir().resolve(fileName(r.name)));
        } catch (Exception ignored) {
        }
        recs.remove(r);
        if (name.equals(ModConfig.get().selectedRecording)) {
            ModConfig.get().selectedRecording = recs.isEmpty() ? "" : recs.get(0).name;
            ModConfig.save();
        }
    }

    // ------------------------------------------------------------------ Steuerung

    private static void msg(MinecraftClient mc, String s, Formatting f) {
        status = s;
        if (mc.player != null) mc.player.sendMessage(Text.literal(s).formatted(f), true);
    }

    public static void stopAll(MinecraftClient mc) {
        if (state == State.RECORDING) stopRecording(mc);
        else if (state == State.PLAYING) stopPlaying(mc, "Wiedergabe gestoppt.");
    }

    public static void toggleRecord(MinecraftClient mc) {
        if (state == State.RECORDING) stopRecording(mc);
        else startRecording(mc);
    }

    public static void togglePlay(MinecraftClient mc) {
        if (state == State.PLAYING) stopPlaying(mc, "Wiedergabe gestoppt.");
        else startPlaying(mc);
    }

    public static void startRecording(MinecraftClient mc) {
        if (mc.player == null || state != State.IDLE) return;
        if (Freecam.active()) Freecam.stop(mc);
        frames.clear();
        state = State.RECORDING;
        msg(mc, "Aufnahme läuft … (Num 1 = beenden)", Formatting.RED);
    }

    public static void stopRecording(MinecraftClient mc) {
        if (state != State.RECORDING) return;
        state = State.IDLE;
        int n = frames.size();
        if (n < MIN_TICKS) {
            frames.clear();
            msg(mc, "Aufnahme zu kurz, nicht gespeichert.", Formatting.YELLOW);
            return;
        }
        Rec r = new Rec();
        int num = 1;
        while (find("Aufnahme " + num) != null) num++;
        r.name = "Aufnahme " + num;
        r.yaw = new float[n];
        r.pitch = new float[n];
        r.flags = new int[n];
        for (int i = 0; i < n; i++) {
            float[] f = frames.get(i);
            r.yaw[i] = f[0];
            r.pitch[i] = f[1];
            r.flags[i] = (int) f[2];
        }
        frames.clear();
        recs.add(r);
        save(r);
        ModConfig.get().selectedRecording = r.name;
        ModConfig.save();
        msg(mc, "Gespeichert: " + r.name + " (" + String.format(java.util.Locale.ROOT, "%.1f", n / 20f) + " s)", Formatting.GREEN);
    }

    public static void startPlaying(MinecraftClient mc) {
        if (mc.player == null || state != State.IDLE) return;
        Rec r = find(ModConfig.get().selectedRecording);
        if (r == null || r.size() == 0) {
            msg(mc, "Keine Aufnahme ausgewählt.", Formatting.YELLOW);
            return;
        }
        if (Freecam.active()) Freecam.stop(mc);
        playing = r;
        idx = 0;
        repeatsDone = 0;
        baseYaw = mc.player.getYaw();
        lastHealth = mc.player.getHealth();
        state = State.PLAYING;
        msg(mc, "Spiele ab: " + r.name + " (Num 2 = Stopp)", Formatting.GREEN);
    }

    private static void releaseKeys(MinecraftClient mc) {
        if (mc.options == null) return;
        GameOptions o = mc.options;
        o.forwardKey.setPressed(false);
        o.backKey.setPressed(false);
        o.leftKey.setPressed(false);
        o.rightKey.setPressed(false);
        o.jumpKey.setPressed(false);
        o.sneakKey.setPressed(false);
        o.sprintKey.setPressed(false);
        o.attackKey.setPressed(false);
        o.useKey.setPressed(false);
    }

    private static void stopPlaying(MinecraftClient mc, String why) {
        if (state != State.PLAYING) return;
        state = State.IDLE;
        playing = null;
        releaseKeys(mc);
        msg(mc, why, Formatting.YELLOW);
    }

    // ------------------------------------------------------------------ Ticks

    /** Am Anfang jedes Ticks (vor der Eingabe-Verarbeitung): Wiedergabe. */
    public static void tickStart(MinecraftClient mc) {
        if (state != State.PLAYING) return;
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || playing == null) {
            stopPlaying(mc, "Wiedergabe gestoppt.");
            return;
        }
        if (mc.currentScreen != null) {
            stopPlaying(mc, "Wiedergabe gestoppt (Menü geöffnet).");
            return;
        }
        if (p.getHealth() < lastHealth - 0.01f) {
            stopPlaying(mc, "Wiedergabe gestoppt: Schaden erhalten!");
            return;
        }
        lastHealth = p.getHealth();

        Rec r = playing;
        ModConfig cfg = ModConfig.get();
        float y = cfg.recRelativeYaw ? baseYaw + (r.yaw[idx] - r.yaw[0]) : r.yaw[idx];
        p.setYaw(y);
        p.setPitch(r.pitch[idx]);

        int f = r.flags[idx];
        GameOptions o = mc.options;
        o.forwardKey.setPressed((f & FWD) != 0);
        o.backKey.setPressed((f & BACK) != 0);
        o.leftKey.setPressed((f & LEFT) != 0);
        o.rightKey.setPressed((f & RIGHT) != 0);
        o.jumpKey.setPressed((f & JUMP) != 0);
        o.sneakKey.setPressed((f & SNEAK) != 0);
        o.sprintKey.setPressed((f & SPRINT) != 0);
        o.attackKey.setPressed((f & ATTACK) != 0);
        o.useKey.setPressed((f & USE) != 0);

        idx++;
        if (idx >= r.size()) {
            repeatsDone++;
            if (cfg.recEndless || repeatsDone < cfg.recRepeats) {
                idx = 0;
            } else {
                stopPlaying(mc, "Wiedergabe fertig.");
            }
        }
    }

    /** Am Ende jedes Ticks: Aufnahme. */
    public static void tickEnd(MinecraftClient mc) {
        if (state != State.RECORDING) return;
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) {
            stopRecording(mc);
            return;
        }
        int f = 0;
        if (mc.currentScreen == null) {
            GameOptions o = mc.options;
            if (o.forwardKey.isPressed()) f |= FWD;
            if (o.backKey.isPressed()) f |= BACK;
            if (o.leftKey.isPressed()) f |= LEFT;
            if (o.rightKey.isPressed()) f |= RIGHT;
            if (o.jumpKey.isPressed()) f |= JUMP;
            if (o.sneakKey.isPressed()) f |= SNEAK;
            if (o.sprintKey.isPressed()) f |= SPRINT;
            if (o.attackKey.isPressed()) f |= ATTACK;
            if (o.useKey.isPressed()) f |= USE;
        }
        frames.add(new float[]{p.getYaw(), p.getPitch(), f});
        if (frames.size() % 20 == 0) {
            p.sendMessage(Text.literal("● Aufnahme: " + frames.size() / 20 + " s").formatted(Formatting.RED), true);
        }
        if (frames.size() >= 20 * 60 * 10) stopRecording(mc); // max. 10 Minuten
    }
}
