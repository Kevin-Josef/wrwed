package de.mapartassist.combat;

import de.mapartassist.cam.Freecam;
import de.mapartassist.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;

/**
 * Axt gegen Schild: Haelt der Spieler im Fadenkreuz sein Schild hoch, wechselt die Mod im selben Frame auf eine Axt aus der
 * Hotbar, schlaegt einmal zu (das deaktiviert das Schild) und wechselt sofort zurueck auf das vorherige Item.
 */
public final class ShieldBreaker {
    private ShieldBreaker() {}

    private static final long MIN_GAP_MS = 350;
    private static long lastAttackMs = 0;

    private static boolean isBlocking(PlayerEntity target) {
        return target.isBlocking() || (target.isUsingItem() && target.getActiveItem().isOf(Items.SHIELD));
    }

    private static int findAxe(PlayerInventory inv) {
        for (int i = 0; i < 9; i++) {
            ItemStack st = inv.getStack(i);
            if (!st.isEmpty() && st.isIn(ItemTags.AXES)) return i;
        }
        return -1;
    }

    /** Pro Frame aufrufen. */
    public static void frame(MinecraftClient mc) {
        ModConfig cfg = ModConfig.get();
        ClientPlayerEntity p = mc.player;
        if (!cfg.shieldAxe || p == null || mc.world == null || mc.interactionManager == null
                || mc.currentScreen != null || Freecam.active()) {
            return;
        }
        if (!(mc.crosshairTarget instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof PlayerEntity target) || target == p) {
            return;
        }
        if (!isBlocking(target) || p.isUsingItem()) return;
        long now = System.currentTimeMillis();
        if (now - lastAttackMs < MIN_GAP_MS) return;
        // Axe schon in der Hand: der Spieler schlaegt selbst zu, nichts zu tun.
        if (p.getMainHandStack().isIn(ItemTags.AXES)) return;

        PlayerInventory inv = p.getInventory();
        int axe = findAxe(inv);
        if (axe < 0) return;

        int prev = inv.getSelectedSlot();
        inv.setSelectedSlot(axe);                       // auf die Axt
        mc.interactionManager.attackEntity(p, target);  // meldet den Slot-Wechsel und schlaegt zu
        p.swingHand(Hand.MAIN_HAND);
        inv.setSelectedSlot(prev);                      // sofort zurueck auf das vorherige Item
        lastAttackMs = now;
    }
}
