package de.mapartassist.macro;

import de.mapartassist.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.Locale;

/**
 * AFK-Waechter: Merkt sich den AFK-Platz (Position + Welt), an dem man beim Einschalten steht.
 * Landet man weit davon entfernt (z. B. am Spawn), sendet die Mod /afk, bis man wieder am AFK-Platz ist.
 * Wird bewusst nur von Hand an- und ausgeschaltet.
 */
public final class AfkGuard {
    private AfkGuard() {}

    private static boolean active = false;
    private static Vec3d home;
    private static String homeDim = "";
    private static int awayTicks, cmdTimer, sent;
    private static boolean wasAway = false;
    public static String status = "";

    public static boolean active() { return active; }

    public static String homeText() {
        if (home == null) return "kein AFK-Platz gesetzt";
        return String.format(Locale.ROOT, "%.0f / %.0f / %.0f", home.x, home.y, home.z);
    }

    private static void say(String msg, Formatting f) {
        status = msg;
        ClientPlayerEntity p = MinecraftClient.getInstance().player;
        if (p != null) p.sendMessage(Text.literal("[AFK] " + msg).formatted(f), false);
    }

    public static void setHome(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) {
            status = "Kein Spieler/Welt.";
            return;
        }
        home = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        homeDim = mc.world.getRegistryKey().getValue().toString();
        awayTicks = 0;
        cmdTimer = 0;
        sent = 0;
        wasAway = false;
        status = "AFK-Platz gesetzt: " + homeText();
    }

    public static void set(MinecraftClient mc, boolean on) {
        if (on) {
            if (mc.player == null || mc.world == null) {
                status = "Du musst im Spiel sein.";
                return;
            }
            setHome(mc);
            active = true;
            say("Wächter AN. AFK-Platz: " + homeText(), Formatting.GREEN);
        } else {
            active = false;
            say("Wächter AUS.", Formatting.YELLOW);
        }
    }

    public static void tick(MinecraftClient mc) {
        if (!active || home == null) return;
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) return; // z. B. beim Neuladen: einfach weiter warten
        ModConfig cfg = ModConfig.get();
        String dim = mc.world.getRegistryKey().getValue().toString();
        boolean away = !dim.equals(homeDim) || new Vec3d(p.getX(), p.getY(), p.getZ()).distanceTo(home) > cfg.afkRadius;

        if (!away) {
            if (wasAway) {
                say("Wieder im AFK-Bereich (nach " + sent + " Versuch(en)).", Formatting.GREEN);
            }
            wasAway = false;
            awayTicks = 0;
            cmdTimer = 0;
            sent = 0;
            return;
        }

        if (!wasAway) {
            wasAway = true;
            sent = 0;
            say("Weg vom AFK-Platz (Spawn?). Sende /afk, bis es klappt.", Formatting.GOLD);
        }
        awayTicks++;
        if (awayTicks < 20) return; // 1 s: Teleport/Laden abwarten
        if (cmdTimer > 0) {
            cmdTimer--;
            return;
        }
        p.networkHandler.sendChatCommand("afk");
        sent++;
        cmdTimer = cfg.afkRetrySec * 20;
        status = "Sende /afk (Versuch " + sent + ") …";
    }
}
