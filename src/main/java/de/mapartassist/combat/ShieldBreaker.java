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
import net.minecraft.util.hit.EntityHitResult;

/**
 * Axt gegen Schild: Haelt der Spieler im Fadenkreuz sein Schild hoch, wechselt die Mod sofort (pro Frame) auf eine Axt
 * in der Hotbar, damit der Treffer das Schild deaktiviert. Hoert er auf zu blocken, geht es zurueck auf das vorherige Item.
 */
public final class ShieldBreaker {
    private ShieldBreaker() {}

    private static boolean switched = false;
    private static int prevSlot = -1, axeSlot = -1;
    private static long lastBlockMs = 0;

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

    private static void restore(ClientPlayerEntity p) {
        if (switched && p != null && p.getInventory().getSelectedSlot() == axeSlot && prevSlot >= 0) {
            p.getInventory().setSelectedSlot(prevSlot);
        }
        switched = false;
    }

    /** Pro Frame aufrufen. */
    public static void frame(MinecraftClient mc) {
        ModConfig cfg = ModConfig.get();
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null) {
            switched = false;
            return;
        }
        if (!cfg.shieldAxe || mc.currentScreen != null || Freecam.active()) {
            restore(p);
            return;
        }

        boolean blocking = false;
        if (mc.crosshairTarget instanceof EntityHitResult hit
                && hit.getEntity() instanceof PlayerEntity target && target != p) {
            blocking = isBlocking(target);
        }
        long now = System.currentTimeMillis();
        PlayerInventory inv = p.getInventory();

        if (switched) {
            if (inv.getSelectedSlot() != axeSlot) { // du hast selbst gewechselt: nichts mehr zuruecksetzen
                switched = false;
                return;
            }
            if (blocking) {
                lastBlockMs = now;
            } else if (now - lastBlockMs > 150) {
                inv.setSelectedSlot(prevSlot);
                switched = false;
            }
            return;
        }

        if (!blocking || p.isUsingItem()) return;
        if (p.getMainHandStack().isIn(ItemTags.AXES)) return; // schon eine Axt in der Hand
        int axe = findAxe(inv);
        if (axe < 0) return;
        prevSlot = inv.getSelectedSlot();
        axeSlot = axe;
        inv.setSelectedSlot(axe);
        switched = true;
        lastBlockMs = now;
    }
}
