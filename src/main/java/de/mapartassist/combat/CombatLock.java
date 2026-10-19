package de.mapartassist.combat;

import de.mapartassist.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * HT1 Combat Lock: verlaesst den Server nur bei Schaden durch einen anderen Spieler (optional nach 0-19 Sekunden).
 * Wird pro Frame UND pro Tick geprueft, damit die Reaktion sofort nach dem Treffer-Paket erfolgt.
 */
public final class CombatLock {
    private CombatLock() {}

    private static int lastHurt = 0;
    private static float lastHealth = -1f;
    private static long triggerAt = -1;
    private static int lastShownSec = -1;

    public static String lastTrigger = "";

    /** Verbleibende Millisekunden bis zum Verlassen, -1 wenn nichts ansteht. */
    public static long remainingMs() {
        return triggerAt < 0 ? -1 : Math.max(0, triggerAt - System.currentTimeMillis());
    }

    private static void reset() {
        lastHurt = 0;
        lastHealth = -1f;
        triggerAt = -1;
        lastShownSec = -1;
    }

    public static void check(MinecraftClient client) {
        ModConfig cfg = ModConfig.get();
        ClientPlayerEntity p = client.player;
        if (!cfg.combatLock || p == null || client.world == null || client.getNetworkHandler() == null) {
            reset();
            return;
        }

        boolean hurt = (p.hurtTime > 0 && lastHurt == 0)
                || (lastHealth >= 0 && p.getHealth() < lastHealth - 0.01f);
        lastHurt = p.hurtTime;
        lastHealth = p.getHealth();

        // Combat Lock nur bei Schaden durch einen anderen Spieler ausloesen.
        // Fall-, Feuer-, Hunger- und Mob-Schaden ignorieren.
        DamageSource source = p.getRecentDamageSource();
        boolean playerHit = source != null && source.getAttacker() instanceof PlayerEntity attacker
                && attacker != p;
        if (!playerHit) hurt = false;
        long now = System.currentTimeMillis();

        // laufender Countdown
        if (triggerAt >= 0) {
            if (now >= triggerAt) {
                leave(client, p);
                return;
            }
            int sec = (int) Math.ceil((triggerAt - now) / 1000.0);
            if (sec != lastShownSec) {
                lastShownSec = sec;
                p.sendMessage(Text.literal("HT1 Combat Lock: Verlasse Server in " + sec + " s")
                        .formatted(Formatting.RED), true);
            }
            return;
        }

        if (!hurt) return;
        if (cfg.combatLockNeedsPlayer && !playerNearby(client, p)) return;

        long delayMs = Math.max(0, Math.min(19, cfg.combatLockDelaySec)) * 1000L;
        if (delayMs == 0) {
            leave(client, p);
        } else {
            triggerAt = now + delayMs; // spaetere Treffer setzen den Countdown nicht zurueck
            lastTrigger = "Getroffen bei " + String.format("%.1f", p.getHealth()) + " Leben.";
        }
    }

    private static void leave(MinecraftClient client, ClientPlayerEntity p) {
        lastTrigger = "Zuletzt verlassen bei " + String.format("%.1f", p.getHealth()) + " Leben.";
        reset();
        client.getNetworkHandler().getConnection().disconnect(Text.literal("HT1 Combat Lock: Server verlassen"));
    }

    private static boolean playerNearby(MinecraftClient client, ClientPlayerEntity p) {
        for (PlayerEntity other : client.world.getPlayers()) {
            if (other != p && p.squaredDistanceTo(other) <= 100.0) return true;
        }
        return false;
    }
}
