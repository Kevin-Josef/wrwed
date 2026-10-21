package de.mapartassist.macro;

import de.mapartassist.config.ModConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * /sell-Makro: oeffnet das Verkaufsmenue (/sell), schiebt alle Items aus der Filterliste hinein
 * und schliesst das Menue wieder (dabei wird verkauft).
 */
public final class SellMacro {
    private SellMacro() {}

    private enum State { IDLE, OPEN, MOVE, CLOSE, DELAY }

    private static State state = State.IDLE;
    private static int timer;
    private static final Deque<Integer> queue = new ArrayDeque<>();
    private static int movedItems, cycles, totalBefore;
    private static int autoTimer = 0;
    public static String status = "";

    public static boolean running() { return state != State.IDLE; }

    private static void say(String msg, Formatting color) {
        status = msg;
        ClientPlayerEntity p = MinecraftClient.getInstance().player;
        if (p != null) p.sendMessage(Text.literal("[Verkauf] " + msg).formatted(color), false);
    }

    private static Set<Item> filter() {
        Set<Item> set = new HashSet<>();
        for (String id : ModConfig.get().cellItems) {
            try {
                Item it = Registries.ITEM.get(Identifier.of(id));
                set.add(it);
            } catch (Exception ignored) {
            }
        }
        return set;
    }

    public static int countSellable(ClientPlayerEntity p) {
        Set<Item> f = filter();
        int n = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack st = p.getInventory().getStack(i);
            if (!st.isEmpty() && f.contains(st.getItem())) n++;
        }
        return n;
    }

    /** Summe aller Items aus der Filterliste im Inventar (Stueckzahl). */
    private static int sellableTotal(ClientPlayerEntity p) {
        Set<Item> f = filter();
        int n = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack st = p.getInventory().getStack(i);
            if (!st.isEmpty() && f.contains(st.getItem())) n += st.getCount();
        }
        return n;
    }

    private static boolean screenOpen(MinecraftClient c, ClientPlayerEntity p) {
        return c.currentScreen instanceof HandledScreen<?> && p.currentScreenHandler != p.playerScreenHandler;
    }

    public static void start(boolean auto) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity p = mc.player;
        if (p == null || running()) return;
        if (ModConfig.get().cellItems.isEmpty()) {
            if (!auto) say("Die Verkaufsliste ist leer.", Formatting.YELLOW);
            return;
        }
        if (countSellable(p) == 0) {
            if (!auto) say("Nichts zu verkaufen: Keins der Items aus der Liste ist im Inventar.", Formatting.YELLOW);
            return;
        }
        if (mc.currentScreen != null) {
            if (!auto) say("Schließe erst das aktuelle Menü.", Formatting.YELLOW);
            return;
        }
        movedItems = 0;
        cycles = 0;
        queue.clear();
        p.networkHandler.sendChatCommand("sell");
        state = State.OPEN;
        timer = 0;
        status = "Öffne /sell …";
    }

    public static void stop() {
        if (!running()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && screenOpen(mc, mc.player)) mc.player.closeHandledScreen();
        state = State.IDLE;
        queue.clear();
        say("Gestoppt.", Formatting.YELLOW);
    }

    private static void fail(String msg) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && screenOpen(mc, mc.player)) mc.player.closeHandledScreen();
        state = State.IDLE;
        queue.clear();
        say(msg, Formatting.RED);
    }

    public static void tick(MinecraftClient client) {
        try {
            inner(client);
        } catch (Throwable t) {
            state = State.IDLE;
            queue.clear();
            status = "FEHLER: " + t;
            if (client.player != null) client.player.sendMessage(Text.literal("[Verkauf] FEHLER: " + t).formatted(Formatting.RED), false);
        }
    }

    private static void inner(MinecraftClient client) {
        ClientPlayerEntity p = client.player;
        ModConfig cfg = ModConfig.get();
        if (state == State.IDLE) {
            if (!cfg.cellAuto || p == null || client.world == null) {
                autoTimer = 0;
                return;
            }
            if (++autoTimer >= cfg.cellInterval * 20) {
                autoTimer = 0;
                boolean busy = AuctionMacro.running() || AutoRepair.running()
                        || MovementRecorder.state != MovementRecorder.State.IDLE
                        || client.currentScreen != null;
                if (!busy) start(true);
            }
            return;
        }
        if (p == null || client.world == null || client.interactionManager == null) {
            state = State.IDLE;
            return;
        }
        timer++;
        switch (state) {
            case OPEN -> {
                if (screenOpen(client, p) && timer >= 2) {
                    Set<Item> f = filter();
                    queue.clear();
                    for (Slot s : p.currentScreenHandler.slots) {
                        if (!(s.inventory instanceof PlayerInventory)) continue;
                        ItemStack st = s.getStack();
                        if (!st.isEmpty() && f.contains(st.getItem())) queue.add(s.id);
                    }
                    if (queue.isEmpty()) {
                        fail("Keine passenden Items gefunden.");
                        return;
                    }
                    totalBefore = sellableTotal(p);
                    state = State.MOVE;
                    timer = 0;
                } else if (timer > 80) {
                    fail("/sell hat sich nicht geöffnet. Abgebrochen.");
                }
            }
            case MOVE -> {
                if (!screenOpen(client, p)) {
                    fail("Das Menü wurde geschlossen. Abgebrochen.");
                    return;
                }
                // Alles in einem einzigen Tick hineinschieben (Shift-Klick auf jeden passenden Stack).
                int syncId = p.currentScreenHandler.syncId;
                while (!queue.isEmpty()) {
                    int id = queue.poll();
                    if (p.currentScreenHandler.slots.get(id).getStack().isEmpty()) continue;
                    client.interactionManager.clickSlot(syncId, id, 0, SlotActionType.QUICK_MOVE, p);
                }
                int moved = totalBefore - sellableTotal(p);
                if (moved <= 0) {
                    fail("Das Verkaufsmenü nimmt nichts mehr an (voll?). Abgebrochen.");
                    return;
                }
                movedItems += moved;
                cycles++;
                status = "Verkaufe … Durchgang " + cycles + ", bisher " + movedItems + " Items";
                state = State.CLOSE;
                timer = 0;
            }
            case CLOSE -> {
                if (timer >= 2) {
                    p.closeHandledScreen();
                    state = State.DELAY;
                    timer = 0;
                }
            }
            case DELAY -> {
                if (timer < 4) return;
                if (cfg.cellRepeat && client.currentScreen == null && countSellable(p) > 0) {
                    // Es liegt (wieder) etwas aus der Liste im Inventar: naechster Durchgang.
                    p.networkHandler.sendChatCommand("sell");
                    state = State.OPEN;
                    timer = 0;
                } else {
                    say("Fertig: " + movedItems + " Items in " + cycles + " Durchgang/Durchgängen verkauft.", Formatting.GREEN);
                    state = State.IDLE;
                }
            }
            default -> { }
        }
    }

    /** Schreibt den Inhalt des offenen Menues in config/mapartassist/sell-dump.txt (Diagnose). */
    public static void dump(MinecraftClient client) {
        if (client.player == null) return;
        List<String> lines = new ArrayList<>();
        for (Slot s : client.player.currentScreenHandler.slots) {
            ItemStack st = s.getStack();
            if (st.isEmpty()) continue;
            lines.add("slot " + s.id + ": " + Registries.ITEM.getId(st.getItem()) + " x" + st.getCount() + " | " + st.getName().getString());
        }
        try {
            Path f = FabricLoader.getInstance().getConfigDir().resolve("mapartassist").resolve("sell-dump.txt");
            Files.createDirectories(f.getParent());
            Files.write(f, lines);
        } catch (Exception ignored) {
        }
    }
}
