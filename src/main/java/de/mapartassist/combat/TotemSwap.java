package de.mapartassist.combat;

import de.mapartassist.config.ModConfig;
import de.mapartassist.mini.Minimi;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Totem-Wechsel: Haelt ein Spieler direkt vor dir (Reichweite einstellbar) Endkristalle, TNT-Loren
 * oder (nur Nether/End) Betten in der Hand, waehlt die Mod ein weiteres Totem aus deiner Hotbar aus.
 * Pro Gefahr wird nur einmal gewechselt, damit du danach selbst frei umschalten kannst.
 */
public final class TotemSwap {
    private TotemSwap() {}

    /** Wird im Menue angezeigt. */
    public static String status = "";

    private static int prevSlot = -1;
    private static int swapSlot = -1;
    private static int calm = 0;
    private static boolean swapped = false;

    public static void tick(MinecraftClient mc) {
        ModConfig cfg = ModConfig.get();
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || !cfg.totemSwap) {
            prevSlot = -1;
            swapSlot = -1;
            swapped = false;
            calm = 0;
            return;
        }
        if (mc.currentScreen != null) return; // nicht waehrend Inventar/Menue

        PlayerInventory inv = p.getInventory();
        String why = threat(mc, p, cfg);
        if (why != null) {
            calm = 0;
            if (!swapped) {
                swapped = true;
                int sel = inv.getSelectedSlot();
                if (inv.getStack(sel).isOf(Items.TOTEM_OF_UNDYING)) {
                    status = "Gefahr (" + why + "): Totem schon in der Hand.";
                    return;
                }
                int slot = -1;
                for (int i = 0; i < 9; i++) {
                    if (i != sel && inv.getStack(i).isOf(Items.TOTEM_OF_UNDYING)) {
                        slot = i;
                        break;
                    }
                }
                if (slot >= 0) {
                    prevSlot = sel;
                    swapSlot = slot;
                    inv.setSelectedSlot(slot);
                    status = "Gewechselt auf Totem (" + why + ").";
                } else {
                    status = "Gefahr (" + why + "), aber kein weiteres Totem in der Hotbar.";
                }
            }
        } else {
            if (calm < 100) calm++;
            if (calm >= 20) { // 1 Sekunde Ruhe
                swapped = false;
                if (prevSlot >= 0) {
                    // nur zurueck, wenn du nicht selbst schon woanders hingeschaltet hast
                    if (cfg.totemSwapBack && inv.getSelectedSlot() == swapSlot) inv.setSelectedSlot(prevSlot);
                    prevSlot = -1;
                    swapSlot = -1;
                }
            }
        }
    }

    /** Liefert den Grund (z. B. "Endkristall"), wenn ein Spieler vor dir eine Gefahr in der Hand haelt. */
    private static String threat(MinecraftClient mc, ClientPlayerEntity p, ModConfig cfg) {
        double range = Math.max(1, Math.min(6, cfg.totemSwapRange));
        boolean netherOrEnd = mc.world.getRegistryKey() == World.NETHER || mc.world.getRegistryKey() == World.END;
        Vec3d look = p.getRotationVec(1.0f);
        double lh = Math.hypot(look.x, look.z);
        for (PlayerEntity o : mc.world.getPlayers()) {
            if (o == p || o.isSpectator() || Minimi.isMini(o)) continue;
            if (p.distanceTo(o) > range) continue;
            double dx = o.getX() - p.getX(), dz = o.getZ() - p.getZ();
            double len = Math.hypot(dx, dz);
            if (len > 0.3 && lh > 0.05) { // "vor mir": ca. 70 Grad nach links/rechts
                double dot = (look.x * dx + look.z * dz) / (lh * len);
                if (dot < 0.35) continue;
            }
            String why = holds(o.getMainHandStack(), cfg, netherOrEnd);
            if (why == null) why = holds(o.getOffHandStack(), cfg, netherOrEnd);
            if (why != null) return o.getName().getString() + ": " + why;
        }
        return null;
    }

    private static String holds(ItemStack s, ModConfig cfg, boolean netherOrEnd) {
        if (s.isEmpty()) return null;
        if (cfg.totemSwapCrystal && s.isOf(Items.END_CRYSTAL)) return "Endkristall";
        if (cfg.totemSwapMinecart && s.isOf(Items.TNT_MINECART)) return "TNT-Lore";
        if (cfg.totemSwapBed && netherOrEnd && s.isIn(ItemTags.BEDS)) return "Bett";
        return null;
    }
}
