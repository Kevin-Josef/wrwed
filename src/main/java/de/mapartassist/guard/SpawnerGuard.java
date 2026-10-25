package de.mapartassist.guard;

import de.mapartassist.config.ModConfig;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.List;
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

    private enum Phase { IDLE, MINING, COLLECTING, LEAVING, AFTER_CMD }

    /** Reichweite zum Abbauen (Abstand Auge -> Blockmitte). Bewusst etwas unter dem Server-Limit. */
    private static final double REACH = 4.4;
    private static final int SCAN = 5;
    private static final long TIMEOUT_MS = 60_000;
    /** Wartezeit zwischen erstem und zweitem Abbau (20 Ticks = 1 s). */
    private static final int CONFIRM_WAIT_TICKS = 6;
    /** Nach dem zweiten Abbau so lange auf das Verschwinden des Spawners warten. */
    private static final int VERIFY_TICKS = 20;
    private static final int MAX_ATTEMPTS = 4;
    /** Maximale Dauer eines einzelnen Abbauvorgangs (8 s). */
    private static final int BREAK_MAX_TICKS = 160;
    /** Mindestwartezeit nach dem letzten Abbau, damit Drops erscheinen (1 s). */
    private static final int COLLECT_MIN_TICKS = 6;
    /** Maximale Wartezeit auf das Einsammeln (12 s). */
    private static final int COLLECT_MAX_TICKS = 100;
    /** Nur Spawner-Drops in diesem Umkreis zaehlen (Spawner stehen max. 3x3 Bloecke um den Spieler). */
    private static final double COLLECT_RADIUS = 3.5;
    private static int breakTicks;
    private static int collectTicks;

    private enum Step { SNEAK, BREAK1, WAIT, BREAK2, VERIFY }
    private static Step step = Step.SNEAK;
    private static int stepTicks;
    private static boolean breakStarted;
    private static int attempts;
    private static boolean sneakForced;
    private static final Set<BlockPos> skipped = new HashSet<>();
    /** Ticks zwischen letztem Abbau und Verlassen, damit das Server-Paket sicher ankommt. */
    private static final int LEAVE_DELAY_TICKS = 2;

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

    private static int joinGrace = 0;

    private static boolean baseMode(ModConfig cfg) {
        return cfg.spawnerGuardHomeOnly && cfg.spawnerHomeSet;
    }

    /** Steht der Spieler in der gespeicherten Base (gleiche Welt, innerhalb des Base-Radius)? */
    public static boolean atBase(MinecraftClient client) {
        ModConfig cfg = ModConfig.get();
        ClientPlayerEntity p = client.player;
        if (!cfg.spawnerHomeSet || p == null || client.world == null) return false;
        if (!client.world.getRegistryKey().getValue().toString().equals(cfg.spawnerHomeDim)) return false;
        double dx = p.getX() - cfg.spawnerHomeX, dy = p.getY() - cfg.spawnerHomeY, dz = p.getZ() - cfg.spawnerHomeZ;
        return Math.sqrt(dx * dx + dy * dy + dz * dz) <= cfg.spawnerHomeRadius;
    }

    /** Speichert die aktuelle Position als Base. */
    public static void saveBase(MinecraftClient client) {
        ClientPlayerEntity p = client.player;
        if (p == null || client.world == null) return;
        ModConfig cfg = ModConfig.get();
        cfg.spawnerHomeX = p.getX();
        cfg.spawnerHomeY = p.getY();
        cfg.spawnerHomeZ = p.getZ();
        cfg.spawnerHomeDim = client.world.getRegistryKey().getValue().toString();
        cfg.spawnerHomeSet = true;
        ModConfig.save();
        status = "Base gespeichert.";
    }

    public static String baseText() {
        ModConfig cfg = ModConfig.get();
        if (!cfg.spawnerHomeSet) return "Base: nicht gespeichert";
        return String.format(java.util.Locale.ROOT, "Base: %.0f / %.0f / %.0f", cfg.spawnerHomeX, cfg.spawnerHomeY, cfg.spawnerHomeZ);
    }

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

    private static boolean attackForced;
    private static boolean forwardForced;

    private static void pressAttack(MinecraftClient client, boolean down) {
        if (!down && !attackForced) return;
        client.options.attackKey.setPressed(down);
        attackForced = down;
    }

    private static void pressForward(MinecraftClient client, boolean down) {
        if (!down && !forwardForced) return;
        client.options.forwardKey.setPressed(down);
        forwardForced = down;
    }

    /** Gibt nur Tasten frei, die diese Funktion selbst gedrueckt hat (sonst wuerde Laufen blockiert). */
    private static void releaseKeys(MinecraftClient client) {
        if (client == null || client.options == null) return;
        pressAttack(client, false);
        pressForward(client, false);
    }

    private static void releaseSneak(MinecraftClient client) {
        releaseKeys(client);
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
        breakTicks = 0;
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
            joinGrace = 100; // nach dem (Wieder-)Beitritt 5 s abwarten, bevor der Schutz scharf ist
            return;
        }
        if (!cfg.spawnerGuard) {
            if (phase != Phase.IDLE) status = "Abgebrochen (Schutz ausgeschaltet).";
            reset(client);
            return;
        }

        switch (phase) {
            case IDLE -> {
                if (joinGrace > 0) {
                    joinGrace--;
                    status = "Schutz startet gleich ...";
                    return;
                }
                if (cfg.spawnerGuardHomeOnly) {
                    if (!cfg.spawnerHomeSet) {
                        status = "Base nicht gespeichert: Schutz gilt überall.";
                    } else if (!atBase(client)) {
                        status = "Außerhalb der Base: Schutz ruht.";
                        return;
                    }
                }
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
            case COLLECTING -> collect(client, p);
            case LEAVING -> {
                if (--leaveTicks <= 0) finish(client);
            }
            case AFTER_CMD -> {
                leaveTicks--;
                status = "Befehl gesendet, verlasse Server in " + (int) Math.ceil(Math.max(0, leaveTicks) / 20.0) + " s ...";
                if (leaveTicks <= 0) leave(client);
            }
        }
    }

    /**
     * Abschluss nach dem Abbau (oder wenn nichts abzubauen war): erst den eingestellten Befehl senden
     * (z. B. /spawn), danach je nach Einstellung den Server verlassen oder online bleiben.
     * Ohne gueltigen Befehl wird wie frueher immer verlassen.
     */
    private static void finish(MinecraftClient client) {
        ModConfig cfg = ModConfig.get();
        String cmd = cfg.spawnerGuardCommand && cfg.spawnerGuardCommandText != null
                ? cfg.spawnerGuardCommandText.trim() : "";
        if (cmd.isEmpty() || client.getNetworkHandler() == null) {
            leave(client);
            return;
        }
        if (cmd.startsWith("/")) {
            String c = cmd.substring(1).trim();
            if (c.isEmpty()) { leave(client); return; }
            client.getNetworkHandler().sendChatCommand(c);
        } else {
            client.getNetworkHandler().sendChatMessage(cmd);
        }
        if (cfg.spawnerGuardLeaveAfter) {
            phase = Phase.AFTER_CMD;
            leaveTicks = Math.max(0, cfg.spawnerGuardLeaveDelaySec) * 20;
            status = "Befehl gesendet: " + cmd;
        } else {
            phase = Phase.IDLE;
            if (baseMode(cfg)) {
                // Base-Modus: Schutz bleibt an. Am Spawn ruht er und wird an der Base von selbst wieder scharf.
                status = "Befehl gesendet: " + cmd + " (" + mined + " Spawner abgebaut). Schutz bleibt an (ruht außerhalb der Base).";
            } else {
                cfg.spawnerGuard = false; // sonst wuerde der Spieler im Radius sofort wieder ausloesen
                ModConfig.save();
                status = "Befehl gesendet: " + cmd + " (" + mined + " Spawner abgebaut). Schutz ausgeschaltet, Taste L = wieder an.";
                if (client.player != null) {
                    client.player.sendMessage(Text.literal("Spawner-Schutz: " + cmd + " ausgeführt, Schutz ausgeschaltet.")
                            .formatted(Formatting.YELLOW), false);
                }
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
                if (mined > 0) startCollecting(client);
                else { status = "Keine Spawner abgebaut, verlasse Server."; startLeaving(client); }
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
        breakTicks = 0;
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
                if (!isSpawner) {
                    step = Step.VERIFY; // Einzelner Spawner: schon nach dem 1. Abbau weg
                    stepTicks = 0;
                } else if (stepTicks >= CONFIRM_WAIT_TICKS) {
                    step = isSpawner ? Step.BREAK2 : Step.VERIFY;
                    stepTicks = 0;
                    breakStarted = false;
        breakTicks = 0;
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
        breakTicks = 0;
                    }
                }
            }
        }
    }

    /**
     * Haelt die Angriffstaste gedrueckt und zielt auf den Spawner, bis der Abbau fertig ist
     * (wie ein Spieler mit gehaltener linker Maustaste). true, sobald ein Abbau beendet wurde.
     * Ein einzelner Aufruf pro Tick reicht nicht: Minecraft bricht den Abbau sonst im naechsten
     * Tick selbst wieder ab (das war das "Antippen").
     */
    private static boolean breakTick(MinecraftClient client, ClientPlayerEntity p) {
        breakTicks++;
        lookAt(p, Vec3d.ofCenter(current));
        HitResult h = client.crosshairTarget;
        boolean aimed = h instanceof BlockHitResult bhr && bhr.getBlockPos().equals(current);
        pressAttack(client, aimed); // nie einen anderen Block treffen

        if (client.interactionManager.isBreakingBlock()) {
            breakStarted = true;
        } else if (breakStarted) {
            pressAttack(client, false);
            return true; // Fortschritt 100 %: dieser Abbau ist fertig
        }
        if (breakTicks > BREAK_MAX_TICKS) {
            pressAttack(client, false);
            return true; // Zeitlimit; VERIFY entscheidet, ob es nochmal versucht wird
        }
        return false;
    }

    private static void lookAt(ClientPlayerEntity p, Vec3d t) {
        Vec3d e = p.getEyePos();
        double dx = t.x - e.x, dy = t.y - e.y, dz = t.z - e.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        p.setYaw((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
        p.setPitch((float) -Math.toDegrees(Math.atan2(dy, h)));
    }

    private static void startCollecting(MinecraftClient client) {
        releaseKeys(client);
        phase = Phase.COLLECTING;
        collectTicks = 0;
        status = mined + " Spawner abgebaut, sammle Drops ein ...";
    }

    /** Wartet, bis alle gedroppten Spawner im Inventar sind (laeuft ggf. zu ihnen), erst dann Verlassen. */
    private static void collect(MinecraftClient client, ClientPlayerEntity p) {
        collectTicks++;
        holdSneak(client); // schleichend laufen: kein Absturz vom Rand
        List<ItemEntity> items = client.world.getEntitiesByClass(ItemEntity.class,
                p.getBoundingBox().expand(COLLECT_RADIUS), e -> e.getStack().isOf(Items.SPAWNER) && e.distanceTo(p) <= COLLECT_RADIUS);
        if (collectTicks >= COLLECT_MIN_TICKS && items.isEmpty()) {
            status = mined + " Spawner abgebaut und eingesammelt, verlasse Server.";
            startLeaving(client);
            return;
        }
        if (collectTicks > COLLECT_MAX_TICKS) {
            status = "Einsammeln dauerte zu lange (" + items.size() + " Drops übrig), verlasse Server.";
            startLeaving(client);
            return;
        }
        if (items.isEmpty()) { pressForward(client, false); return; }
        ItemEntity nearest = items.get(0);
        for (ItemEntity it : items) if (p.squaredDistanceTo(it) < p.squaredDistanceTo(nearest)) nearest = it;
        status = "Sammle Spawner ein (" + items.size() + " Drop(s) in der Nähe) ...";
        double dx = nearest.getX() - p.getX(), dz = nearest.getZ() - p.getZ();
        if (dx * dx + dz * dz > 0.6 * 0.6) {
            p.setYaw((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
            p.setPitch(20f);
            pressForward(client, true);
        } else {
            pressForward(client, false);
        }
    }

    private static void leave(MinecraftClient client) {
        if (client.getNetworkHandler() == null) { phase = Phase.IDLE; return; }
        status = "Zuletzt verlassen: " + reason + ", " + mined + " Spawner abgebaut.";
        phase = Phase.IDLE;
        if (!baseMode(ModConfig.get())) {
            ModConfig.get().spawnerGuard = false; // nach dem Wiederbeitritt nicht sofort erneut ausloesen
            ModConfig.save();
        }
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
