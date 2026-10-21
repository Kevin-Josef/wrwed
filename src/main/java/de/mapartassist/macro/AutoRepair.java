package de.mapartassist.macro;

import de.mapartassist.cam.Freecam;
import de.mapartassist.config.ModConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Auto-Reparatur: Wird die Spitzhacke in der Hand fast kaputt, kauft die Mod im /shop (Kategorie
 * "Ausruestung") XP-Flaschen und wirft sie nach unten, damit Mending die Spitzhacke repariert.
 * Danach geht es weiter (optional wird eine laufende Aufnahme wieder gestartet).
 *
 * Die Menue-Schritte im Shop sind allgemein gehalten (Suche nach Namen/Item). Klappt etwas nicht,
 * schreibt die Mod den Menue-Inhalt nach config/mapartassist/shop-dump.txt.
 */
public final class AutoRepair {
    private AutoRepair() {}

    private enum State { IDLE, OPEN_SHOP, BUY, CLOSE_SHOP, PREP, THROW, WAIT_ORBS }

    private static State state = State.IDLE;
    private static int timer, cooldown;
    private static int pickSel, bottleSlotId = -1, lastCount, stall, missing, reopens, sigBefore, startBottles, waitTicks;
    private static boolean inCategory, navPending, armed;
    private static float prevPitch;
    private static boolean resume;
    public static String status = "";

    public static boolean running() { return state != State.IDLE; }

    private static void say(String msg, Formatting color) {
        status = msg;
        ClientPlayerEntity p = MinecraftClient.getInstance().player;
        if (p != null) p.sendMessage(Text.literal("[Reparatur] " + msg).formatted(color), false);
    }

    // ------------------------------------------------------------------ Hilfen

    private static boolean screenOpen(MinecraftClient c, ClientPlayerEntity p) {
        return c.currentScreen instanceof HandledScreen<?> && p.currentScreenHandler != p.playerScreenHandler;
    }

    private static int bottles(ClientPlayerEntity p) {
        int n = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack st = p.getInventory().getStack(i);
            if (st.getItem() == Items.EXPERIENCE_BOTTLE) n += st.getCount();
        }
        ItemStack off = p.getOffHandStack();
        if (off.getItem() == Items.EXPERIENCE_BOTTLE) n += off.getCount();
        return n;
    }

    private static boolean lowPickaxe(ItemStack h) {
        return h.isIn(ItemTags.PICKAXES) && h.isDamageable()
                && h.getMaxDamage() - h.getDamage() <= ModConfig.get().repairThreshold;
    }

    private static int signature(ClientPlayerEntity p) {
        int h = p.currentScreenHandler.syncId;
        for (Slot s : p.currentScreenHandler.slots) {
            if (s.inventory instanceof PlayerInventory) continue;
            ItemStack st = s.getStack();
            h = h * 31 + (st.isEmpty() ? 0 : Registries.ITEM.getId(st.getItem()).hashCode() * 7 + st.getCount());
        }
        return h;
    }

    private static List<String> lore(ItemStack st) {
        List<String> out = new ArrayList<>();
        LoreComponent l = st.get(DataComponentTypes.LORE);
        if (l != null) for (Text t : l.lines()) out.add(t.getString());
        return out;
    }

    private static Slot findByName(ClientPlayerEntity p, String needle) {
        String n = needle.toLowerCase().trim();
        if (n.isEmpty()) return null;
        for (Slot s : p.currentScreenHandler.slots) {
            if (s.inventory instanceof PlayerInventory) continue;
            ItemStack st = s.getStack();
            if (st.isEmpty()) continue;
            if (st.getName().getString().toLowerCase().contains(n)) return s;
            for (String l : lore(st)) if (l.toLowerCase().contains(n)) return s;
        }
        return null;
    }

    /** Kategorie "Ausruestung" = Totem-Symbol im Hauptmenue (zuerst nach Namen, sonst das Totem). */
    private static Slot findCategory(ClientPlayerEntity p, String name) {
        Slot byName = findByName(p, name);
        if (byName != null) return byName;
        for (Slot s : p.currentScreenHandler.slots) {
            if (s.inventory instanceof PlayerInventory) continue;
            if (s.getStack().getItem() == Items.TOTEM_OF_UNDYING) return s;
        }
        return null;
    }

    private static boolean isPane(ItemStack st) {
        return Registries.ITEM.getId(st.getItem()).getPath().equals("lime_stained_glass_pane");
    }

    /** Kaufen-Fenster erkennen: Titel "Kaufen". */
    private static boolean isBuyDialog(MinecraftClient c) {
        return c.currentScreen != null && c.currentScreen.getTitle().getString().toLowerCase().contains("kaufen");
    }

    /** Gruener "=64"-Knopf (mittlere Reihe ganz rechts). */
    private static Slot find64(ClientPlayerEntity p) {
        Slot fallback = null;
        for (Slot s : p.currentScreenHandler.slots) {
            if (s.inventory instanceof PlayerInventory) continue;
            ItemStack st = s.getStack();
            if (st.isEmpty() || !isPane(st)) continue;
            if (st.getName().getString().contains("64")) return s;
            if (s.id == 17) fallback = s;
        }
        return fallback;
    }

    /** Kaufbestaetigung: gruenes Glas in der untersten Reihe. */
    private static Slot confirmSlot(ClientPlayerEntity p) {
        int top = 0;
        for (Slot s : p.currentScreenHandler.slots) if (!(s.inventory instanceof PlayerInventory)) top++;
        int lastRow = Math.max(0, top - 9);
        for (Slot s : p.currentScreenHandler.slots) {
            if (s.inventory instanceof PlayerInventory) continue;
            ItemStack st = s.getStack();
            if (s.id >= lastRow && !st.isEmpty() && isPane(st)) return s;
        }
        return findConfirm(p);
    }

    private static Slot findBottleTop(ClientPlayerEntity p) {
        for (Slot s : p.currentScreenHandler.slots) {
            if (s.inventory instanceof PlayerInventory) continue;
            if (s.getStack().getItem() == Items.EXPERIENCE_BOTTLE) return s;
        }
        return null;
    }

    private static Slot findConfirm(ClientPlayerEntity p) {
        String[] words = {"confirm", "bestätig", "bestaetig", "kaufen", "buy", "accept", "ja"};
        Slot green = null;
        for (Slot s : p.currentScreenHandler.slots) {
            if (s.inventory instanceof PlayerInventory) continue;
            ItemStack st = s.getStack();
            if (st.isEmpty()) continue;
            String n = st.getName().getString().toLowerCase();
            for (String w : words) if (n.contains(w)) return s;
            String id = Registries.ITEM.getId(st.getItem()).getPath();
            if (green == null && (id.startsWith("lime_") || id.startsWith("green_") || id.equals("emerald_block"))) green = s;
        }
        return green;
    }

    private static void dump(ClientPlayerEntity p, String why) {
        List<String> lines = new ArrayList<>();
        lines.add("Grund: " + why);
        for (Slot s : p.currentScreenHandler.slots) {
            if (s.inventory instanceof PlayerInventory) continue;
            ItemStack st = s.getStack();
            if (st.isEmpty()) continue;
            StringBuilder sb = new StringBuilder("slot " + s.id + ": " + Registries.ITEM.getId(st.getItem())
                    + " x" + st.getCount() + " | name=" + st.getName().getString());
            for (String l : lore(st)) sb.append(" | ").append(l);
            lines.add(sb.toString());
        }
        try {
            Path f = FabricLoader.getInstance().getConfigDir().resolve("mapartassist").resolve("shop-dump.txt");
            Files.createDirectories(f.getParent());
            Files.write(f, lines);
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------ Steuerung

    /** force = auch starten, wenn die Spitzhacke noch nicht fast kaputt ist (Test). */
    public static void start(boolean force) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity p = mc.player;
        if (p == null || running()) return;
        if (AuctionMacro.running() || SellMacro.running()) {
            say("Ein anderes Makro läuft noch.", Formatting.YELLOW);
            return;
        }
        if (mc.currentScreen != null) return;
        if (!p.getMainHandStack().isIn(ItemTags.PICKAXES)) {
            say("Nimm die Spitzhacke in die Hand.", Formatting.YELLOW);
            return;
        }
        resume = MovementRecorder.state == MovementRecorder.State.PLAYING;
        MovementRecorder.stopAll(mc);
        if (Freecam.active()) Freecam.stop(mc);

        ModConfig cfg = ModConfig.get();
        pickSel = p.getInventory().getSelectedSlot();
        prevPitch = p.getPitch();
        bottleSlotId = -1;
        startBottles = bottles(p);
        int need = cfg.repairStacks * 64;
        inCategory = false;
        navPending = false;
        armed = false;
        stall = 0;
        missing = 0;
        reopens = 0;
        lastCount = startBottles;
        say(force ? "Reparatur-Test startet." : "Spitzhacke fast kaputt, starte Reparatur.", Formatting.GOLD);
        if (startBottles >= need) {
            state = State.PREP;
        } else {
            p.networkHandler.sendChatCommand("shop");
            state = State.OPEN_SHOP;
        }
        timer = 0;
    }

    public static void stop() {
        if (!running()) return;
        abort("Gestoppt.", Formatting.YELLOW, 0);
    }

    private static void swapBack(MinecraftClient mc, ClientPlayerEntity p) {
        if (bottleSlotId >= 0 && p.getOffHandStack().getItem() == Items.EXPERIENCE_BOTTLE && mc.interactionManager != null) {
            mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, bottleSlotId, 40, SlotActionType.SWAP, p);
        }
        bottleSlotId = -1;
    }

    private static void abort(String msg, Formatting color, int cool) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity p = mc.player;
        if (p != null) {
            if (screenOpen(mc, p)) p.closeHandledScreen();
            swapBack(mc, p);
            p.setPitch(prevPitch);
            p.getInventory().setSelectedSlot(pickSel);
        }
        state = State.IDLE;
        cooldown = cool;
        say(msg, color);
    }

    private static void fail(ClientPlayerEntity p, String why) {
        if (screenOpen(MinecraftClient.getInstance(), p)) dump(p, why);
        abort(why + " (Menü-Inhalt: config/mapartassist/shop-dump.txt)", Formatting.RED, 1200);
    }

    // ------------------------------------------------------------------ Tick

    public static void tick(MinecraftClient client) {
        try {
            inner(client);
        } catch (Throwable t) {
            state = State.IDLE;
            cooldown = 1200;
            status = "FEHLER: " + t;
            if (client.player != null) client.player.sendMessage(Text.literal("[Reparatur] FEHLER: " + t).formatted(Formatting.RED), false);
        }
    }

    private static void inner(MinecraftClient client) {
        ClientPlayerEntity p = client.player;
        ModConfig cfg = ModConfig.get();
        if (state == State.IDLE) {
            if (cooldown > 0) cooldown--;
            if (!cfg.repairEnabled || cooldown > 0 || p == null || client.world == null || client.currentScreen != null) return;
            if (AuctionMacro.running() || SellMacro.running()) return;
            if (lowPickaxe(p.getMainHandStack())) start(false);
            return;
        }
        if (p == null || client.world == null || client.interactionManager == null) {
            state = State.IDLE;
            return;
        }
        timer++;
        int need = cfg.repairStacks * 64;

        switch (state) {
            case OPEN_SHOP -> {
                if (screenOpen(client, p) && timer >= 6) {
                    state = State.BUY;
                    timer = 0;
                    waitTicks = 0;
                    inCategory = false;
                    navPending = false;
                    armed = false;
                } else if (timer > 80) {
                    abort("/shop hat sich nicht geöffnet.", Formatting.RED, 1200);
                }
            }
            case BUY -> {
                if (timer < waitTicks) return;
                int cur = bottles(p);
                if (cur > lastCount) {
                    lastCount = cur;
                    stall = 0;
                }
                if (cur >= need) {
                    state = State.CLOSE_SHOP;
                    timer = 0;
                    return;
                }
                if (!screenOpen(client, p)) {
                    if (++reopens > 4) {
                        abort("Das Shop-Menü schließt sich ständig, XP-Flaschen kaufen klappt nicht.", Formatting.RED, 1200);
                        return;
                    }
                    p.networkHandler.sendChatCommand("shop");
                    state = State.OPEN_SHOP;
                    timer = 0;
                    return;
                }
                // Nach Navigations-Klick kurz auf neuen Menue-Inhalt warten
                if (navPending && signature(p) == sigBefore && timer < 30) return;
                navPending = false;
                if (++stall > 14) {
                    fail(p, "Keine XP-Flaschen erhalten (Geld? Anderes Menü?)");
                    return;
                }

                if (isBuyDialog(client)) {
                    if (!armed) {
                        Slot s64 = find64(p);
                        if (s64 == null) {
                            fail(p, "\"=64\"-Knopf im Kaufen-Fenster nicht gefunden");
                            return;
                        }
                        client.interactionManager.clickSlot(p.currentScreenHandler.syncId, s64.id, 0, SlotActionType.PICKUP, p);
                        armed = true;
                        waitTicks = 6;
                    } else {
                        Slot ok = confirmSlot(p);
                        if (ok == null) {
                            fail(p, "Kaufbestätigung nicht gefunden");
                            return;
                        }
                        client.interactionManager.clickSlot(p.currentScreenHandler.syncId, ok.id, 0, SlotActionType.PICKUP, p);
                        armed = false;
                        waitTicks = 12;
                    }
                    timer = 0;
                    return;
                }
                armed = false;
                if (inCategory) {
                    Slot b = findBottleTop(p);
                    if (b == null) {
                        if (++missing > 10) {
                            fail(p, "Keine XP-Flaschen in der Kategorie gefunden");
                            return;
                        }
                        waitTicks = 4;
                        timer = 0;
                        return;
                    }
                    sigBefore = signature(p);
                    client.interactionManager.clickSlot(p.currentScreenHandler.syncId, b.id, 0, SlotActionType.PICKUP, p);
                    navPending = true;
                } else {
                    Slot cat = findCategory(p, cfg.repairCategory);
                    if (cat == null) {
                        fail(p, "Kategorie (Totem) im /shop nicht gefunden");
                        return;
                    }
                    sigBefore = signature(p);
                    client.interactionManager.clickSlot(p.currentScreenHandler.syncId, cat.id, 0, SlotActionType.PICKUP, p);
                    inCategory = true;
                    navPending = true;
                }
                waitTicks = 6;
                timer = 0;
            }
            case CLOSE_SHOP -> {
                if (timer == 1) p.closeHandledScreen();
                if (timer >= 8 && client.currentScreen == null) {
                    state = State.PREP;
                    timer = 0;
                } else if (timer > 60) {
                    abort("Shop ließ sich nicht schließen.", Formatting.RED, 1200);
                }
            }
            case PREP -> {
                if (timer == 1) {
                    Slot src = null;
                    for (Slot s : p.playerScreenHandler.slots) {
                        if (!(s.inventory instanceof PlayerInventory) || s.getIndex() >= 36) continue;
                        if (s.getStack().getItem() == Items.EXPERIENCE_BOTTLE) { src = s; break; }
                    }
                    if (src == null) {
                        abort("Keine XP-Flaschen im Inventar.", Formatting.RED, 1200);
                        return;
                    }
                    bottleSlotId = src.id;
                    client.interactionManager.clickSlot(p.playerScreenHandler.syncId, src.id, 40, SlotActionType.SWAP, p);
                    p.getInventory().setSelectedSlot(pickSel);
                }
                if (timer >= 6) {
                    if (p.getOffHandStack().getItem() != Items.EXPERIENCE_BOTTLE) {
                        abort("XP-Flaschen ließen sich nicht in die Zweithand legen.", Formatting.RED, 1200);
                        return;
                    }
                    state = State.THROW;
                    timer = 0;
                }
            }
            case THROW -> {
                p.setPitch(90f);
                ItemStack pick = p.getMainHandStack();
                boolean repaired = pick.isDamageable() && pick.getDamage() == 0;
                boolean empty = p.getOffHandStack().getItem() != Items.EXPERIENCE_BOTTLE;
                if (repaired || empty) {
                    state = State.WAIT_ORBS;
                    timer = 0;
                    return;
                }
                if (timer % 3 == 0) {
                    client.interactionManager.interactItem(p, Hand.OFF_HAND);
                    p.swingHand(Hand.OFF_HAND);
                }
            }
            case WAIT_ORBS -> {
                p.setPitch(90f);
                if (timer < 30) return;
                swapBack(client, p);
                p.setPitch(prevPitch);
                p.getInventory().setSelectedSlot(pickSel);
                ItemStack pick = p.getMainHandStack();
                boolean ok = !pick.isDamageable() || pick.getMaxDamage() - pick.getDamage() > cfg.repairThreshold;
                state = State.IDLE;
                if (ok) {
                    cooldown = 100;
                    say("Fertig: Spitzhacke repariert.", Formatting.GREEN);
                    if (resume && cfg.repairResume) MovementRecorder.startPlaying(client);
                } else {
                    cfg.repairEnabled = false;
                    ModConfig.save();
                    cooldown = 1200;
                    say("Die Spitzhacke wurde nicht repariert (Mending drauf?). Auto-Reparatur ist jetzt aus.", Formatting.RED);
                }
            }
            default -> { }
        }
    }
}
