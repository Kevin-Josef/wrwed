package de.mapartassist.guard;

import de.mapartassist.config.ModConfig;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Spawner-AFK-Schutz: Kommt ein anderer Spieler naeher als der eingestellte Radius, baut die Mod
 * alle Spawner in Reichweite mit einer Silk-Touch-Spitzhacke ab und verlaesst danach den Server.
 * Hat man keine Silk-Touch-Spitzhacke dabei, wird sofort verlassen (Spawner bleiben dann heil stehen).
 */
public final class SpawnerGuard {
    private SpawnerGuard() {}

    private enum Phase { IDLE, MINING, LEAVING }

    /** Reichweite zum Abbauen (Abstand Auge -> Blockmitte). Bewusst etwas unter dem Server-Limit. */
    private static final double REACH = 4.4;
    private static final int SCAN = 5;
    private static final long TIMEOUT_MS = 20_000;
    /** Ticks zwischen letztem Abbau und Verlassen, damit das Server-Paket sicher ankommt. */
    private static final int LEAVE_DELAY_TICKS = 10;

    private static Phase phase = Phase.IDLE;
    private static long startedAt;
    private static int swapCooldown;
    private static int leaveTicks;
    private static String reason = "";
    private static BlockPos current;
    private static int mined;

    /** Wird im Menue angezeigt. */
    public static String status = "Bereit.";

    public static boolean active() { return phase != Phase.IDLE; }

    private static void reset(MinecraftClient client) {
        if (phase == Phase.MINING && client != null && client.interactionManager != null) {
            client.interactionManager.cancelBlockBreaking();
        }
        phase = Phase.IDLE;
        current = null;
        mined = 0;
        swapCooldown = 0;
    }

    public static boolean hasSilkPickaxe(ClientPlayerEntity p) {
        for (int i = 0; i < 36; i++) {
            if (isSilkPick(p.getInventory().getStack(i))) return true;
        }
        return false;
    }

    public static void tick(MinecraftClient client) {
        ModConfig cfg = ModConfig.get();
        ClientPlayerEntity p = client.player;
        if (p == null || client.world == null || client.interactionManager == null
                || client.getNetworkHandler() == null) {
            reset(client);
            return;
        }
        if (!cfg.spawnerGuard) {
            if (phase != Phase.IDLE) status = "Abgebrochen (Schutz ausgeschaltet).";
            reset(client);
            return;
        }

        switch (phase) {
            case IDLE -> {
                PlayerEntity other = nearestPlayer(client, p, cfg.spawnerGuardRadius);
                if (other == null) {
                    status = "Überwache Umgebung (" + cfg.spawnerGuardRadius + " Blöcke) ...";
                    return;
                }
                reason = other.getName().getString() + " (" + Math.round(p.distanceTo(other)) + " Blöcke)";
                p.sendMessage(Text.literal("Spawner-Schutz: " + reason + " erkannt!").formatted(Formatting.RED), true);
                startedAt = System.currentTimeMillis();
                mined = 0;
                current = null;
                swapCooldown = 0;
                if (!cfg.spawnerGuardMine || !hasSilkPickaxe(p) || findSpawner(client, p) == null) {
                    status = !cfg.spawnerGuardMine ? "Spieler erkannt, verlasse Server."
                            : !hasSilkPickaxe(p) ? "Keine Silk-Touch-Spitzhacke! Verlasse Server."
                            : "Keine Spawner in Reichweite, verlasse Server.";
                    startLeaving();
                } else {
                    phase = Phase.MINING;
                    status = "Spieler erkannt: baue Spawner ab ...";
                }
            }
            case MINING -> mine(client, p);
            case LEAVING -> {
                if (--leaveTicks <= 0) leave(client);
            }
        }
    }

    private static void startLeaving() {
        phase = Phase.LEAVING;
        leaveTicks = LEAVE_DELAY_TICKS;
    }

    private static void mine(MinecraftClient client, ClientPlayerEntity p) {
        if (System.currentTimeMillis() - startedAt > TIMEOUT_MS) {
            client.interactionManager.cancelBlockBreaking();
            status = "Zeitlimit erreicht, verlasse Server.";
            startLeaving();
            return;
        }
        if (swapCooldown > 0) { swapCooldown--; return; }

        if (!isSilkPick(p.getMainHandStack())) {
            if (!equipSilkPick(client, p)) {
                status = "Silk-Touch-Spitzhacke verschwunden! Verlasse Server.";
                client.interactionManager.cancelBlockBreaking();
                startLeaving();
                return;
            }
            swapCooldown = 2;
            return;
        }

        if (current != null && !client.world.getBlockState(current).isOf(Blocks.SPAWNER)) {
            mined++;
            current = null;
        }
        if (current == null) {
            current = findSpawner(client, p);
            if (current == null) {
                status = mined + " Spawner abgebaut, verlasse Server.";
                startLeaving();
                return;
            }
        }

        Vec3d eye = p.getEyePos();
        Direction side = Direction.getFacing(
                eye.x - (current.getX() + 0.5), eye.y - (current.getY() + 0.5), eye.z - (current.getZ() + 0.5));
        client.interactionManager.updateBlockBreakingProgress(current, side);
        p.swingHand(Hand.MAIN_HAND);
        status = "Baue Spawner ab (" + (mined + 1) + ") ...";
    }

    private static void leave(MinecraftClient client) {
        status = "Zuletzt verlassen: " + reason + ", " + mined + " Spawner abgebaut.";
        phase = Phase.IDLE;
        ModConfig.get().spawnerGuard = false; // nach dem Wiederbeitritt nicht sofort erneut ausloesen
        ModConfig.save();
        client.getNetworkHandler().getConnection().disconnect(Text.literal("Spawner-Schutz: Server verlassen (" + reason + ")"));
    }

    private static PlayerEntity nearestPlayer(MinecraftClient client, ClientPlayerEntity p, int radius) {
        double best = (double) radius * radius;
        PlayerEntity found = null;
        for (PlayerEntity other : client.world.getPlayers()) {
            if (other == p || other.isSpectator()) continue;
            double d = p.squaredDistanceTo(other);
            if (d <= best) { best = d; found = other; }
        }
        return found;
    }

    private static BlockPos findSpawner(MinecraftClient client, ClientPlayerEntity p) {
        Vec3d eye = p.getEyePos();
        BlockPos base = p.getBlockPos();
        BlockPos best = null;
        double bestD = REACH * REACH;
        for (BlockPos pos : BlockPos.iterate(base.add(-SCAN, -SCAN, -SCAN), base.add(SCAN, SCAN, SCAN))) {
            if (!client.world.getBlockState(pos).isOf(Blocks.SPAWNER)) continue;
            double d = eye.squaredDistanceTo(Vec3d.ofCenter(pos));
            if (d <= bestD) { bestD = d; best = pos.toImmutable(); }
        }
        return best;
    }

    private static boolean isSilkPick(ItemStack s) {
        if (s.isEmpty() || !s.isIn(ItemTags.PICKAXES)) return false;
        for (RegistryEntry<Enchantment> e : s.getEnchantments().getEnchantments()) {
            if (e.matchesKey(Enchantments.SILK_TOUCH)) return true;
        }
        return false;
    }

    /** Hotbar zuerst, sonst aus dem Inventar in die Hotbar tauschen. */
    private static boolean equipSilkPick(MinecraftClient client, ClientPlayerEntity p) {
        for (int i = 0; i < 9; i++) {
            if (isSilkPick(p.getInventory().getStack(i))) {
                p.getInventory().setSelectedSlot(i);
                return true;
            }
        }
        for (int s = 9; s < 36; s++) {
            if (!isSilkPick(p.getInventory().getStack(s))) continue;
            int target = p.getInventory().getSelectedSlot();
            client.interactionManager.clickSlot(p.playerScreenHandler.syncId, s, target, SlotActionType.SWAP, p);
            return true;
        }
        return false;
    }
}
