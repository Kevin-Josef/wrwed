package de.mapartassist.crystal;

import de.mapartassist.config.ModConfig;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Crystal-Limit: Auf einem einzelnen Obsidian (bzw. Bedrock) werden innerhalb des eingestellten Zeitfensters
 * hoechstens {@link #MAX} Crystals platziert. Jeder weitere Rechtsklick wird verworfen, bis das Fenster
 * fuer die aelteste Platzierung abgelaufen ist (gleitendes Fenster).
 */
public final class CrystalLimit {
    private CrystalLimit() {}

    /** Maximale Crystals pro Obsidian im Zeitfenster. */
    public static final int MAX = 2;

    private static final Map<BlockPos, ArrayDeque<Long>> PLACEMENTS = new HashMap<>();
    private static long lastHint = 0;

    /** Wird vom Mixin vor jedem Block-Rechtsklick aufgerufen. true = Klick verwerfen. */
    public static boolean shouldBlock(ClientPlayerEntity player, Hand hand, BlockHitResult hit) {
        ModConfig cfg = ModConfig.get();
        if (!cfg.crystalLimit) {
            if (!PLACEMENTS.isEmpty()) PLACEMENTS.clear();
            return false;
        }
        ClientWorld world = MinecraftClient.getInstance().world;
        if (player == null || world == null || hit == null) return false;

        ItemStack held = player.getStackInHand(hand);
        if (!held.isOf(Items.END_CRYSTAL)) return false;

        BlockPos pos = hit.getBlockPos();
        if (!world.getBlockState(pos).isOf(Blocks.OBSIDIAN) && !world.getBlockState(pos).isOf(Blocks.BEDROCK)) return false;

        // Wuerde sowieso nichts platziert (Platz belegt oder schon ein Crystal da)? Dann nicht mitzaehlen.
        BlockPos above = pos.up();
        if (!world.isAir(above)) return false;
        if (!world.getEntitiesByClass(EndCrystalEntity.class, new Box(above), e -> true).isEmpty()) return false;

        long now = System.currentTimeMillis();
        long windowMs = Math.max(1, Math.min(5, cfg.crystalLimitSec)) * 1000L;
        purge(now, windowMs);

        ArrayDeque<Long> q = PLACEMENTS.computeIfAbsent(pos.toImmutable(), k -> new ArrayDeque<>());
        if (q.size() >= MAX) {
            long waitMs = q.peekFirst() + windowMs - now;
            if (now - lastHint > 250) {
                lastHint = now;
                player.sendMessage(Text.literal(String.format(java.util.Locale.ROOT,
                        "Crystal-Limit: noch %.1f s", Math.max(0, waitMs) / 1000.0)).formatted(Formatting.YELLOW), true);
            }
            return true;
        }
        q.addLast(now);
        return false;
    }

    private static void purge(long now, long windowMs) {
        Iterator<Map.Entry<BlockPos, ArrayDeque<Long>>> it = PLACEMENTS.entrySet().iterator();
        while (it.hasNext()) {
            ArrayDeque<Long> q = it.next().getValue();
            while (!q.isEmpty() && now - q.peekFirst() >= windowMs) q.removeFirst();
            if (q.isEmpty()) it.remove();
        }
    }
}
