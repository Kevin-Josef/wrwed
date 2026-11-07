package de.mapartassist.hud;

import de.mapartassist.config.ModConfig;
import de.mapartassist.esp.BlockEsp;
import de.mapartassist.mini.Minimi;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Item-Anzeige: zeigt fuer ausgewaehlte Items Symbol + Anzahl.
 * - Eigene Anzeige: Zahlen aus dem eigenen Inventar (Position frei einstellbar).
 * - Ueber anderen Spielern: nur was der Client sehen kann (Haende + Ruestung), das Inventar anderer
 *   Spieler wird vom Server nicht gesendet.
 */
public final class ItemHud {
    private ItemHud() {}

    private static final EquipmentSlot[] OTHER_SLOTS = {
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final double[] PT = new double[2];

    // ------------------------------------------------------------------ Auswahl

    public static String idOf(Item it) { return Registries.ITEM.getId(it).toString(); }

    public static boolean isSelected(Item it) { return ModConfig.get().itemHudItems.contains(idOf(it)); }

    public static void toggle(Item it) {
        List<String> l = ModConfig.get().itemHudItems;
        String id = idOf(it);
        if (!l.remove(id)) l.add(id);
        ModConfig.save();
    }

    public static void clear() {
        ModConfig.get().itemHudItems.clear();
        ModConfig.save();
    }

    private static List<Item> selectedItems() {
        List<Item> out = new ArrayList<>();
        for (String id : ModConfig.get().itemHudItems) {
            try {
                Item it = Registries.ITEM.get(Identifier.of(id));
                if (it != null) out.add(it);
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ Zaehlen

    /** Alle Items im eigenen Inventar (Hotbar, Inventar, Offhand, Ruestung). */
    private static int countOwn(ClientPlayerEntity p, Item item) {
        int n = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.isOf(item)) n += s.getCount();
        }
        n += countSlot(p, EquipmentSlot.OFFHAND, item);
        n += countSlot(p, EquipmentSlot.HEAD, item);
        n += countSlot(p, EquipmentSlot.CHEST, item);
        n += countSlot(p, EquipmentSlot.LEGS, item);
        n += countSlot(p, EquipmentSlot.FEET, item);
        return n;
    }

    private static int countSlot(LivingEntity e, EquipmentSlot slot, Item item) {
        ItemStack s = e.getEquippedStack(slot);
        return s.isOf(item) ? s.getCount() : 0;
    }

    private static int countVisible(LivingEntity e, Item item) {
        int n = 0;
        for (EquipmentSlot slot : OTHER_SLOTS) n += countSlot(e, slot, item);
        return n;
    }

    // ------------------------------------------------------------------ Zeichnen

    public static void render(DrawContext g, MinecraftClient mc) {
        ModConfig cfg = ModConfig.get();
        if (!cfg.itemHudEnabled || mc.player == null || mc.world == null || mc.options == null) return;
        if (mc.options.hudHidden) return;
        List<Item> items = selectedItems();
        if (items.isEmpty()) return;

        if (cfg.itemHudSelf) drawOwn(g, mc, items);
        if (cfg.itemHudPlayers) drawPlayers(g, mc, items);
    }

    private static void drawOwn(DrawContext g, MinecraftClient mc, List<Item> items) {
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        ModConfig cfg = ModConfig.get();
        int rowH = 18;
        int maxW = 0;
        int[] counts = new int[items.size()];
        String[] texts = new String[items.size()];
        for (int i = 0; i < items.size(); i++) {
            counts[i] = countOwn(mc.player, items.get(i));
            texts[i] = String.valueOf(counts[i]);
            maxW = Math.max(maxW, mc.textRenderer.getWidth(texts[i]));
        }
        int boxW = 16 + 4 + maxW + 8, boxH = items.size() * rowH + 4;
        int x = (int) ((sw - boxW) * (cfg.itemHudX / 100.0));
        int y = (int) ((sh - boxH) * (cfg.itemHudY / 100.0));
        g.fill(x, y, x + boxW, y + boxH, 0x90000000);
        for (int i = 0; i < items.size(); i++) {
            int ry = y + 2 + i * rowH;
            g.drawItem(new ItemStack(items.get(i)), x + 4, ry);
            g.drawTextWithShadow(mc.textRenderer, texts[i], x + 4 + 16 + 4, ry + 5,
                    counts[i] > 0 ? 0xFFFFFFFF : 0xFF888888);
        }
    }

    private static void drawPlayers(DrawContext g, MinecraftClient mc, List<Item> items) {
        ModConfig cfg = ModConfig.get();
        double range2 = (double) cfg.itemHudRange * cfg.itemHudRange;
        float pt = BlockEsp.partial();
        int[] counts = new int[items.size()];
        for (PlayerEntity o : mc.world.getPlayers()) {
            if (o == mc.player || o.isSpectator() || o.isInvisible() || Minimi.isMini(o)) continue;
            if (mc.player.squaredDistanceTo(o) > range2) continue;
            if (!mc.player.canSee(o)) continue; // nur wenn auch der Name sichtbar waere

            int shown = 0, totalW = 0;
            for (int i = 0; i < items.size(); i++) {
                counts[i] = countVisible(o, items.get(i));
                if (counts[i] > 0) {
                    shown++;
                    totalW += 16 + 2 + mc.textRenderer.getWidth(String.valueOf(counts[i])) + 6;
                }
            }
            if (shown == 0) continue;

            Vec3d pos = o.getLerpedPos(pt);
            // ueber dem Namensschild (Name sitzt ca. 0,5 Bloecke ueber dem Kopf)
            if (!BlockEsp.project(mc, pos.x, pos.y + o.getHeight() + 0.85, pos.z, PT)) continue;
            int x = (int) PT[0] - (totalW - 6) / 2;
            int y = (int) PT[1] - 16;
            g.fill(x - 3, y - 2, x + totalW - 6 + 3, y + 18, 0x80000000);
            for (int i = 0; i < items.size(); i++) {
                if (counts[i] <= 0) continue;
                String t = String.valueOf(counts[i]);
                g.drawItem(new ItemStack(items.get(i)), x, y);
                g.drawTextWithShadow(mc.textRenderer, t, x + 18, y + 5, 0xFFFFFFFF);
                x += 16 + 2 + mc.textRenderer.getWidth(t) + 6;
            }
        }
    }
}
