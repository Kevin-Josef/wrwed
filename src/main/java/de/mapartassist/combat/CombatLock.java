package de.mapartassist.combat;

import de.mapartassist.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

/**
 * HT1 Combat Lock: verlaesst den Server, sobald man Schaden nimmt.
 * Wird pro Frame UND pro Tick geprueft, damit die Reaktion sofort nach dem Treffer-Paket erfolgt.
 */
public final class CombatLock {
    private CombatLock() {}

    private static int lastHurt = 0;
    private static float lastHealth = -1f;

    public static String lastTrigger = "";

    public static void check(MinecraftClient client) {
        ModConfig cfg = ModConfig.get();
        ClientPlayerEntity p = client.player;
        if (!cfg.combatLock || p == null || client.world == null || client.getNetworkHandler() == null) {
            lastHurt = 0;
            lastHealth = -1f;
            return;
        }

        boolean hurt = (p.hurtTime > 0 && lastHurt == 0)
                || (lastHealth >= 0 && p.getHealth() < lastHealth - 0.01f);
        lastHurt = p.hurtTime;
        lastHealth = p.getHealth();
        if (!hurt) return;

        if (cfg.combatLockNeedsPlayer && !playerNearby(client, p)) return;

        lastTrigger = "Ausgelöst bei " + String.format("%.1f", p.getHealth()) + " Leben.";
        client.getNetworkHandler().getConnection().disconnect(Text.literal("HT1 Combat Lock: Server verlassen"));
    }

    private static boolean playerNearby(MinecraftClient client, ClientPlayerEntity p) {
        for (PlayerEntity other : client.world.getPlayers()) {
            if (other != p && p.squaredDistanceTo(other) <= 100.0) return true;
        }
        return false;
    }
}
