package de.mapartassist.macro;

import de.mapartassist.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

/**
 * AFK-Waechter: Es gibt mehrere AFK-Areas (auch auf anderen Servern), deshalb kennt die Mod keinen festen Platz.
 * Beim Einschalten gilt: Du stehst in einer AFK-Area. Wirst du danach weggesetzt (grosser Positionssprung oder neue
 * Welt, z. B. Spawn), sendet die Mod /afk, bis der naechste Sprung kommt (= du bist wieder in einer AFK-Area).
 * Wird nur von Hand an- und ausgeschaltet.
 */
public final class AfkGuard {
    private AfkGuard() {}

    private enum Mode { AFK, AWAY }

    private static final int MAX_TRIES = 10;
    private static final int SETTLE_TICKS = 100;

    private static boolean active = false;
    private static Mode mode = Mode.AFK;
    private static Vec3d lastPos;
    private static ClientWorld lastWorld;
    private static int settle, awayTicks, cmdTimer, sent;
    public static String status = "";

    public static boolean active() { return active; }

    public static String modeText() {
        if (!active) return "Wächter ist aus";
        return mode == Mode.AFK ? "Du bist in einer AFK-Area" : "Am Spawn: sende /afk (Versuch " + sent + ")";
    }

    private static void say(String msg, Formatting f) {
        status = msg;
        ClientPlayerEntity p = MinecraftClient.getInstance().player;
        if (p != null) p.sendMessage(Text.literal("[AFK] " + msg).formatted(f), false);
    }

    public static void set(MinecraftClient mc, boolean on) {
        if (on) {
            if (mc.player == null || mc.world == null) {
                status = "Du musst im Spiel sein.";
                return;
            }
            mode = Mode.AFK;
            lastPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
            lastWorld = mc.world;
            settle = 0;
            sent = 0;
            active = true;
            say("Wächter AN. Ich gehe davon aus, dass du gerade in einer AFK-Area stehst.", Formatting.GREEN);
        } else {
            active = false;
            say("Wächter AUS.", Formatting.YELLOW);
        }
    }

    /** Von Hand: "Ich stehe am Spawn", sofort /afk senden, bis der naechste Sprung kommt. */
    public static void forceAway() {
        if (!active) return;
        mode = Mode.AWAY;
        sent = 0;
        awayTicks = 20;
        cmdTimer = 0;
        say("Sende /afk, bis du in einer AFK-Area bist.", Formatting.GOLD);
    }

    public static void tick(MinecraftClient mc) {
        if (!active) return;
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) return; // Neuladen/Serverwechsel: warten
        ModConfig cfg = ModConfig.get();
        Vec3d cur = new Vec3d(p.getX(), p.getY(), p.getZ());

        boolean event = false;
        if (lastWorld != null && mc.world != lastWorld) event = true;
        else if (lastPos != null && cur.distanceTo(lastPos) > cfg.afkRadius) event = true;
        lastWorld = mc.world;
        lastPos = cur;
        if (settle > 0) {
            settle--;
            event = false; // direkt nach der Ankunft nachruckelnde Teleports ignorieren
        }

        if (event) {
            if (mode == Mode.AFK) {
                mode = Mode.AWAY;
                sent = 0;
                awayTicks = 0;
                cmdTimer = 0;
                say("Versetzt worden (Spawn?). Sende /afk, bis du wieder in einer AFK-Area bist.", Formatting.GOLD);
            } else if (sent > 0) {
                mode = Mode.AFK;
                settle = SETTLE_TICKS;
                say("Wieder in einer AFK-Area (nach " + sent + " Versuch(en)).", Formatting.GREEN);
                sent = 0;
                return;
            }
        }

        if (mode != Mode.AWAY) return;
        awayTicks++;
        if (awayTicks < 20) return; // 1 s: Teleport/Laden abwarten
        if (cmdTimer > 0) {
            cmdTimer--;
            return;
        }
        if (sent >= MAX_TRIES) {
            mode = Mode.AFK;
            settle = SETTLE_TICKS;
            say("Nach " + MAX_TRIES + " Versuchen kein weiterer Sprung erkannt: Ich gehe davon aus, dass du in einer AFK-Area bist.",
                    Formatting.YELLOW);
            sent = 0;
            return;
        }
        p.networkHandler.sendChatCommand("afk");
        sent++;
        cmdTimer = cfg.afkRetrySec * 20;
        status = "Sende /afk (Versuch " + sent + ") …";
    }
}
