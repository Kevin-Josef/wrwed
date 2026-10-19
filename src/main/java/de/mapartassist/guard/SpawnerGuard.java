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

import java.util.HashSet;
import java.util.Set;

/**
 * Spawner-AFK-Schutz: Kommt ein anderer Spieler naeher als der eingestellte Radius, baut die Mod
 * alle Spawner in Reichweite mit einer Silk-Touch-Spitzhacke ab und verlaesst danach den Server.
 * Hat man keine Silk-Touch-Spitzhacke dabei, wird sofort verlassen (Spawner bleiben dann heil stehen).
 *
 * Abbau-Ablauf pro Spawner (Server-Regel): schleichen, einmal abbauen, ca. 1 s warten
 * (Bestaetigungsabfrage / "2FA"), denselben Spawner nochmal abbauen.
 */
public final class SpawnerGuard {
    private SpawnerGuard() {}

    private enum Phase { IDLE, MINING, LEAVING }

    /** Reichweite zum Abbauen (Abstand Auge -> Blockmitte). Bewusst etwas unter dem Server-Limit. */
    private static final double REACH = 4.4;
    private static final int SCAN = 5;
    private static final long TIMEOUT_MS = 60_000;
    /** Wartezeit zwischen erstem und zweitem Abbau (20 Ticks = 1 s). */
    private static final int CONFIRM_WAIT_TICKS = 20;
    /** Nach dem zweiten Abbau so lange auf das Verschwinden des Spawners warten. */
    private static final int VERIFY_TICKS = 20;
    private static final int MAX_ATTEMPTS = 2;

    private enum Step { SNEAK, BREAK1, WAIT, BREAK2, VERIFY }
    private static Step step = Step.SNEAK;
    private static int stepTicks;
    private static boolean breakStarted;
    private static int attempts;
    private static boolean sneakForced;
    private static final Set<BlockPos> skipped = new HashSet<>();
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
        releaseSneak(client);
        phase = Phase.IDLE;
        current = null;
        mined = 0;
        swapCooldown = 0;
    }

    private static void holdSneak(MinecraftClient client) {
        client.options.sneakKey.setPressed(true);
        sneakForced = true;
    }

    private static void releaseSneak(MinecraftClient client) {
        if (sneakForced && client != null && client.options != null) {
            client.options.sneakKey.setPressed(false);
        }
        sneakForced = false;
    }

    private static void nextSpawner() {
        current = null;
        step = Step.SNEAK;
        stepTicks = 0;
        breakStarted = false;
        attempts = 0;
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
                skipped.clear();
                nextSpawner();
                swapCooldown = 0;
                if (!cfg.spawnerGuardMine || !hasSilkPickaxe(p) || findSpawner(client, p) == null) {
                    status = !cfg.spawnerGuardMine ? "Spieler erkannt, verlasse Server."
                            : !hasSilkPickaxe(p) ? "Keine Silk-Touch-Spitzhacke! Verlasse Server."
                            : "Keine Spawner in Reichweite, verlasse Server.";
                    startLeaving(client);
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

    private static void startLeaving(MinecraftClient client) {
        releaseSneak(client);
        phase = Phase.LEAVING;
        leaveTicks = LEAVE_DELAY_TICKS;
    }

    private static void mine(MinecraftClient client, ClientPlayerEntity p) {
        if (System.currentTimeMillis() - startedAt > TIMEOUT_MS) {
            client.interactionManager.cancelBlockBreaking();
            status = "Zeitlimit erreicht, verlasse Server.";
            startLeaving(client);
            return;
        }
        if (swapCooldown > 0) { swapCooldown--; return; }

        if (!isSilkPick(p.getMainHandStack())) {
            if (!equipSilkPick(client, p)) {
                status = "Silk-Touch-Spitzhacke verschwunden! Verlasse Server.";
                client.interactionManager.cancelBlockBreaking();
                startLeaving(client);
                return;
            }
            swapCooldown = 2;
            return;
        }

        if (current == null) {
            current = findSpawner(client, p);
            if (current == null) {
                status = mined + " Spawner abgebaut, verlasse Server.";
                startLeaving(client);
                return;
            }
        }

        holdSneak(client); // Ohne Schleichen laesst der Server den Abbau nicht zu
        stepTicks++;
        boolean isSpawner = client.world.getBlockState(current).isOf(Blocks.SPAWNER);
        int n = mined + 1;

        switch (step) {
            case SNEAK -> {
                status = "Spawner " + n + ": schleiche ...";
                if (p.isSneaking() || stepTicks > 10) {
                    step = Step.BREAK1;
                    stepTicks = 0;
                    breakStarted = false;
                }
            }
            case BREAK1 -> {
                status = "Spawner " + n + ": 1. Abbau ...";
                if (breakTick(client, p)) {
                    step = Step.WAIT;
                    stepTicks = 0;
                }
            }
            case WAIT -> {
                status = "Spawner " + n + ": warte auf Bestätigung ...";
                if (stepTicks >= CONFIRM_WAIT_TICKS) {
                    step = isSpawner ? Step.BREAK2 : Step.VERIFY;
                    stepTicks = 0;
                    breakStarted = false;
                }
            }
            case BREAK2 -> {
                status = "Spawner " + n + ": 2. Abbau (Bestätigung) ...";
                if (!isSpawner || breakTick(client, p)) {
                    step = Step.VERIFY;
                    stepTicks = 0;
                }
            }
            case VERIFY -> {
                if (!isSpawner) {
                    mined++;
                    nextSpawner();
                } else if (stepTicks >= VERIFY_TICKS) {
                    if (++attempts >= MAX_ATTEMPTS) {
                        skipped.add(current); // klappt nicht, nicht endlos wiederholen
                        nextSpawner();
                    } else {
                        step = Step.BREAK1; // erneut versuchen (Bestaetigung evtl. abgelaufen)
                        stepTicks = 0;
                        breakStarted = false;
                    }
                }
            }
        }
    }

    /** Fuehrt einen Abbauvorgang aus. true, sobald der Block fertig abgebaut wurde. */
    private static boolean breakTick(MinecraftClient client, ClientPlayerEntity p) {
        if (breakStarted && !client.interactionManager.isBreakingBlock()) {
            return true; // Fortschritt erreicht 100 %, Vorgang beendet
        }
        Vec3d eye = p.getEyePos();
        Direction side = Direction.getFacing(
                eye.x - (current.getX() + 0.5), eye.y - (current.getY() + 0.5), eye.z - (current.getZ() + 0.5));
        client.interactionManager.updateBlockBreakingProgress(current, side);
        p.swingHand(Hand.MAIN_HAND);
        breakStarted = true;
        return false;
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
            if (!client.world.getBlockState(pos).isOf(Blocks.SPAWNER) || skipped.contains(pos)) continue;
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
