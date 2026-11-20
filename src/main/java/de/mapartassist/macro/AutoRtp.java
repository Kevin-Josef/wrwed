package de.mapartassist.macro;

import de.mapartassist.config.ModConfig;
import de.mapartassist.mini.Minimi;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Auto-RTP: sendet /rtp, klickt im RTP-Menue die eingestellte Dimension (Overworld / Nether / End) an und
 * wiederholt das, bis nach einem Teleport ein anderer Spieler im Radius des Spieler-ESP auftaucht.
 *
 * Ablauf: SEND -> WAIT_MENU -> WAIT_TP -> SCAN (Spieler suchen) -> SEND ...
 * Schlaegt ein Schritt fehl (kein Menue, Cooldown, kein Teleport): DELAY und neu versuchen.
 */
public final class AutoRtp {
    private AutoRtp() {}

    private enum State { IDLE, SEND, WAIT_MENU, WAIT_TP, SCAN, DELAY, POP }

    private static final int MAX_FAILS_IN_ROW = 12;

    private static State state = State.IDLE;
    private static int timer, menuTicks, attempts, teleports, fails;
    private static boolean dumped;
    private static ClientWorld startWorld;
    private static double sx, sy, sz;
    public static String status = "";

    // ---- Unsichtbarkeit beim Fund
    private static int popSlot = -1;        // Hotbar-Slot, in dem der Trank beim Benutzen liegt
    private static int popFromInv = -1;     // Inventar-Slot (9-35), falls der Trank erst hereingeholt wurde
    private static boolean popSplash;
    private static int popPrevSel;
    private static float popPrevPitch;
    private static boolean popUsed;

    private static boolean isInvisPotion(ItemStack st, boolean splash) {
        if (st.isEmpty() || st.getItem() != (splash ? Items.SPLASH_POTION : Items.POTION)) return false;
        PotionContentsComponent c = st.get(DataComponentTypes.POTION_CONTENTS);
        if (c == null) return false;
        for (StatusEffectInstance e : c.getEffects()) {
            if (e.getEffectType().equals(StatusEffects.INVISIBILITY)) return true;
        }
        return false;
    }

    /** Kurzer Ping, damit man aufmerksam wird (kommt immer, auch ohne Trank). */
    private static void ping(MinecraftClient mc) {
        if (mc.player == null) return;
        try {
            // Normale Entity-Methode (Sound, Lautstaerke, Tonhoehe): volle Lautstaerke, hoher Ping
            mc.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.0f, 1.8f);
        } catch (Throwable ignored) {
        }
    }

    /** Sucht einen Unsichtbarkeitstrank (Hotbar zuerst, trinken vor werfen) und holt ihn in die Hand. */
    private static boolean preparePop(MinecraftClient mc, ClientPlayerEntity p) {
        popSlot = -1;
        popFromInv = -1;
        int[][] order = {{0, 9, 0}, {0, 9, 1}, {9, 36, 0}, {9, 36, 1}}; // von, bis, werfen?
        for (int[] o : order) {
            for (int i = o[0]; i < o[1]; i++) {
                if (!isInvisPotion(p.getInventory().getStack(i), o[2] == 1)) continue;
                popSplash = o[2] == 1;
                popPrevSel = p.getInventory().getSelectedSlot();
                if (i < 9) {
                    popSlot = i;
                    p.getInventory().setSelectedSlot(i);
                } else {
                    popSlot = popPrevSel;
                    popFromInv = i;
                    mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, i, popPrevSel, SlotActionType.SWAP, p);
                }
                popPrevPitch = p.getPitch();
                popUsed = false;
                return true;
            }
        }
        return false;
    }

    private static void finishPop(MinecraftClient mc, ClientPlayerEntity p, String msg) {
        mc.options.useKey.setPressed(false);
        if (popSplash) p.setPitch(popPrevPitch);
        if (popFromInv >= 0) {
            mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, popFromInv, popSlot, SlotActionType.SWAP, p);
        }
        p.getInventory().setSelectedSlot(popPrevSel);
        state = State.IDLE;
        say(msg, Formatting.AQUA);
    }

    public static boolean running() { return state != State.IDLE; }

    public static String dimName(int dim) {
        return switch (dim) {
            case 1 -> "Nether";
            case 2 -> "End";
            default -> "Overworld";
        };
    }

    private static void say(String msg, Formatting color) {
        status = msg;
        ClientPlayerEntity p = MinecraftClient.getInstance().player;
        if (p != null) p.sendMessage(Text.literal("[Auto-RTP] " + msg).formatted(color), false);
    }

    public static void set(MinecraftClient mc, boolean on) {
        if (on) start(mc); else stop();
    }

    public static void toggle(MinecraftClient mc) {
        if (running()) stop(); else start(mc);
    }

    public static void start(MinecraftClient mc) {
        if (running()) return;
        if (mc.player == null || mc.world == null) {
            status = "Du musst im Spiel sein.";
            return;
        }
        attempts = 0;
        teleports = 0;
        fails = 0;
        dumped = false;
        state = State.SEND;
        timer = 0;
        say("Gestartet (" + dimName(ModConfig.get().rtpDimension) + "). Stoppt, sobald ein Spieler in Reichweite ist.",
                Formatting.GREEN);
    }

    public static void stop() {
        if (!running()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (state == State.POP && mc.player != null && mc.interactionManager != null) {
            finishPop(mc, mc.player, "Gestoppt.");
            return;
        }
        if (mc.player != null && screenOpen(mc, mc.player)) mc.player.closeHandledScreen();
        state = State.IDLE;
        say("Gestoppt.", Formatting.YELLOW);
    }

    private static boolean screenOpen(MinecraftClient c, ClientPlayerEntity p) {
        return c.currentScreen instanceof HandledScreen<?> && p.currentScreenHandler != p.playerScreenHandler;
    }

    private static void fail(String why) {
        fails++;
        if (fails >= MAX_FAILS_IN_ROW) {
            state = State.IDLE;
            say(why + " - " + MAX_FAILS_IN_ROW + " Fehlversuche hintereinander, ich höre auf.", Formatting.RED);
            return;
        }
        int sec = Math.max(1, ModConfig.get().rtpRetrySec);
        state = State.DELAY;
        timer = 0;
        status = why + " - neuer Versuch in " + sec + " s";
    }

    /** Anderer Spieler im Radius des Spieler-ESP (gleiche Regeln wie dort). */
    private static PlayerEntity findPlayer(MinecraftClient mc, ClientPlayerEntity p, ModConfig cfg) {
        double r = Math.max(16, cfg.playerEspRange);
        double r2 = r * r;
        for (PlayerEntity o : mc.world.getPlayers()) {
            if (o == p || o.isSpectator() || Minimi.isMini(o)) continue;
            if (p.squaredDistanceTo(o) > r2) continue;
            return o;
        }
        return null;
    }

    private static boolean teleported(MinecraftClient mc, ClientPlayerEntity p) {
        if (mc.world != startWorld) return true; // andere Dimension = neue Welt
        double dx = p.getX() - sx, dy = p.getY() - sy, dz = p.getZ() - sz;
        return dx * dx + dy * dy + dz * dz > 32 * 32;
    }

    private static boolean nameMatches(String name, int dim) {
        String n = name.toLowerCase(Locale.ROOT);
        return switch (dim) {
            case 0 -> n.contains("overworld") || n.contains("oberwelt");
            case 1 -> n.contains("nether");
            default -> n.matches(".*\\b(end|ende)\\b.*");
        };
    }

    /**
     * Slot der gewuenschten Dimension im RTP-Menue. Erst nach dem Item-Namen, sonst nach der Lage:
     * Das Menue hat drei Bloecke nebeneinander (je 3 Spalten breit): Overworld | Nether | End.
     */
    private static int pickSlot(ClientPlayerEntity p, int dim) {
        List<Slot> cont = new ArrayList<>();
        for (Slot s : p.currentScreenHandler.slots) {
            if (!(s.inventory instanceof PlayerInventory)) cont.add(s);
        }
        if (cont.isEmpty()) return -1;
        for (Slot s : cont) {
            ItemStack st = s.getStack();
            if (!st.isEmpty() && nameMatches(st.getName().getString(), dim)) return s.id;
        }
        List<Slot> band = new ArrayList<>();
        for (int i = 0; i < cont.size(); i++) {
            if (i % 9 / 3 == dim && !cont.get(i).getStack().isEmpty()) band.add(cont.get(i));
        }
        if (!band.isEmpty()) return band.get(band.size() / 2).id;
        int rows = Math.max(1, cont.size() / 9);
        int idx = (rows / 2) * 9 + 1 + 3 * dim;
        return idx < cont.size() ? cont.get(idx).id : -1;
    }

    /** Schreibt den Inhalt des RTP-Menues in config/mapartassist/rtp-dump.txt (Diagnose, einmal pro Start). */
    private static void dump(ClientPlayerEntity p, int chosen) {
        List<String> lines = new ArrayList<>();
        lines.add("gewaehlter Slot: " + chosen);
        for (Slot s : p.currentScreenHandler.slots) {
            if (s.inventory instanceof PlayerInventory) continue;
            ItemStack st = s.getStack();
            lines.add("slot " + s.id + ": " + (st.isEmpty() ? "(leer)"
                    : Registries.ITEM.getId(st.getItem()) + " x" + st.getCount() + " | " + st.getName().getString()));
        }
        try {
            Path f = FabricLoader.getInstance().getConfigDir().resolve("mapartassist").resolve("rtp-dump.txt");
            Files.createDirectories(f.getParent());
            Files.write(f, lines);
        } catch (Exception ignored) {
        }
    }

    public static void tick(MinecraftClient mc) {
        if (state == State.IDLE) return;
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.getNetworkHandler() == null || mc.interactionManager == null) {
            state = State.IDLE;
            status = "Gestoppt (nicht mehr im Spiel).";
            return;
        }
        if (p.getHealth() <= 0) {
            state = State.IDLE;
            say("Gestoppt: Du bist gestorben.", Formatting.RED);
            return;
        }
        ModConfig cfg = ModConfig.get();
        timer++;

        switch (state) {
            case SEND -> {
                if (screenOpen(mc, p)) { // Rest eines frueheren Versuchs
                    p.closeHandledScreen();
                    return;
                }
                if (mc.currentScreen != null) return; // Chat/Menue offen: warten
                startWorld = mc.world;
                sx = p.getX();
                sy = p.getY();
                sz = p.getZ();
                attempts++;
                p.networkHandler.sendChatCommand("rtp");
                state = State.WAIT_MENU;
                timer = 0;
                menuTicks = 0;
                status = "Versuch " + attempts + ": /rtp gesendet ...";
            }
            case WAIT_MENU -> {
                if (screenOpen(mc, p)) {
                    // Die Slot-Inhalte kommen einen Moment nach dem Oeffnen an.
                    if (++menuTicks < 3) return;
                    int id = pickSlot(p, cfg.rtpDimension);
                    if (!dumped) {
                        dumped = true;
                        dump(p, id);
                    }
                    if (id < 0) {
                        p.closeHandledScreen();
                        fail("Im RTP-Menü wurde kein passendes Feld gefunden");
                        return;
                    }
                    mc.interactionManager.clickSlot(p.currentScreenHandler.syncId, id, 0, SlotActionType.PICKUP, p);
                    state = State.WAIT_TP;
                    timer = 0;
                    status = "Versuch " + attempts + ": " + dimName(cfg.rtpDimension) + " angeklickt, warte auf Teleport ...";
                } else {
                    menuTicks = 0;
                    if (timer > 60) fail("Kein RTP-Menü geöffnet (Cooldown?)");
                }
            }
            case WAIT_TP -> {
                if (teleported(mc, p)) {
                    teleports++;
                    fails = 0;
                    state = State.SCAN;
                    timer = 0;
                    status = "Teleport #" + teleports + " - suche Spieler ...";
                } else if (timer > 400) { // 20 s: z. B. Teleport-Countdown abgebrochen
                    if (screenOpen(mc, p)) p.closeHandledScreen();
                    fail("Kein Teleport erkannt");
                }
            }
            case SCAN -> {
                PlayerEntity f = findPlayer(mc, p, cfg);
                if (f != null) {
                    long d = Math.round(Math.sqrt(p.squaredDistanceTo(f)));
                    state = State.IDLE;
                    say("Spieler gefunden: " + f.getName().getString() + " (" + d + " m) nach " + teleports
                            + " Teleport(s). Gestoppt.", Formatting.GREEN);
                    p.sendMessage(Text.literal("Spieler gefunden: " + f.getName().getString() + " (" + d + " m)")
                            .formatted(Formatting.GREEN), true);
                    ping(mc);
                    if (cfg.rtpInvis) {
                        if (preparePop(mc, p)) {
                            state = State.POP;
                            timer = 0;
                            status = "Spieler gefunden - benutze Unsichtbarkeit ...";
                        } else {
                            say("Kein Unsichtbarkeitstrank dabei.", Formatting.YELLOW);
                        }
                    }
                    return;
                }
                if (timer >= Math.max(1, cfg.rtpWaitSec) * 20) {
                    state = State.SEND;
                    timer = 0;
                }
            }
            case POP -> {
                // Platz zum Synchronisieren, falls der Trank erst aus dem Inventar geholt wurde
                if (timer < 3) return;
                if (!popUsed) {
                    popUsed = true;
                    if (popSplash) {
                        p.setPitch(90f); // auf die eigenen Fuesse werfen
                        mc.interactionManager.interactItem(p, Hand.MAIN_HAND);
                        p.swingHand(Hand.MAIN_HAND);
                    } else {
                        mc.options.useKey.setPressed(true); // gedrueckt halten, bis fertig getrunken
                    }
                    timer = 0;
                    return;
                }
                if (popSplash) {
                    if (timer >= 3) finishPop(mc, p, "Unsichtbarkeit geworfen.");
                } else if (timer >= 6 && !p.isUsingItem()) {
                    finishPop(mc, p, "Unsichtbarkeit getrunken.");
                } else if (timer > 80) {
                    finishPop(mc, p, "Trinken hat zu lange gedauert, abgebrochen.");
                }
            }
            case DELAY -> {
                if (timer >= Math.max(1, cfg.rtpRetrySec) * 20) {
                    state = State.SEND;
                    timer = 0;
                }
            }
            default -> { }
        }
    }
}
