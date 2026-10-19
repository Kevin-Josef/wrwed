package de.mapartassist.macro;

import de.mapartassist.config.ModConfig;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Auktions-Makro fuer HugoSMP:
 *  1. /ah &lt;item&gt; sucht, 2. guenstigstes Angebot fuer exakt die Zielmenge lesen,
 *  3. Item in die Hand, 4. /ah sell &lt;gesamtpreis&gt; &lt;menge&gt; senden.
 * Verkauft ausschließlich vollständige Stacks.
 */
public final class AuctionMacro {
    private AuctionMacro() {}

    private enum State { IDLE, NEXT, WAIT_GUI, CLOSING, SELECT, SELL, CONFIRM, DELAY }

    private record Job(Item item, int amount, long manual) {}
    private record Listing(long price, int count) {
        double unit() { return price / (double) count; }
    }

    private static final Pattern PRICE = Pattern.compile("([0-9][0-9.,]*)");
    private static final Pattern THOUSANDS = Pattern.compile("^\\d{1,3}([.,]\\d{3})+$");

    private static State state = State.IDLE;
    private static final Deque<Job> queue = new ArrayDeque<>();
    private static Job cur;
    private static Listing best;
    private static int timer;
    private static boolean f8Down = false;
    private static boolean sentSell = false;
    private static int countBeforeSell = 0;
    private static boolean filterToggleTried = false;

    private static int failedSells = 0;
    private static int watchChat = 0;
    private static String lastServerMsg = "";
    private static boolean chatHooked = false;
    private static boolean inChatHook = false;

    public static String status = "";

    /** Muss einmal beim Start aufgerufen werden: merkt sich Server-Antworten nach /ah sell. */
    public static void init() {
        if (chatHooked) return;
        chatHooked = true;
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (watchChat <= 0 || inChatHook) return;
            String t = message.getString();
            // Eigene Makro-Meldungen nie erneut verarbeiten (sonst Endlosschleife).
            if (t.isBlank() || t.startsWith("[Makro]")) return;
            lastServerMsg = t;
            inChatHook = true;
            try {
                ClientPlayerEntity pl = MinecraftClient.getInstance().player;
                if (pl != null) pl.sendMessage(Text.literal((overlay ? "[Makro] Server (Actionbar): " : "[Makro] Server: ") + t).formatted(Formatting.GOLD), false);
            } finally {
                inChatHook = false;
            }
        });
    }

    /** Manueller Preis nur, wenn fuer das Item eingeschaltet UND ein Betrag gesetzt ist. */
    public static boolean useManual(ModConfig cfg, String id) {
        long price = cfg.sellPrices.getOrDefault(id, 0L);
        return price > 0 && cfg.sellUseManual.getOrDefault(id, true);
    }

    public static boolean running() { return state != State.IDLE; }

    // ------------------------------------------------------------------ Steuerung

    public static void start() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity p = mc.player;
        if (p == null) return;
        if (running()) { say("Makro läuft bereits. Stoppen: /ahmacro stop", Formatting.YELLOW); return; }

        ModConfig cfg = ModConfig.get();
        queue.clear();
        failedSells = 0;
        init();
        for (String id : cfg.sellItems) {
            Item it;
            try {
                it = Registries.ITEM.get(Identifier.of(id));
            } catch (Exception e) {
                continue;
            }
            if (hasFullStack(p, it)) queue.add(new Job(it, it.getMaxCount(), useManual(cfg, id) ? cfg.sellPrices.getOrDefault(id, 0L) : 0L));
        }
        if (queue.isEmpty()) {
            say("Nichts zu verkaufen: Keins der Items aus der Liste ist im Inventar.", Formatting.YELLOW);
            return;
        }
        if (cfg.macroDryRun) {
            say("TESTLAUF IST AN: Es wird NICHTS eingestellt. Im Menü (Makros) den Testlauf ausschalten.", Formatting.RED);
        }
        say((cfg.macroDryRun ? "[Testlauf] " : "") + "Makro startet mit " + queue.size() + " Item(s).", Formatting.GREEN);
        state = State.NEXT;
        timer = 0;
    }

    public static void stop() {
        if (!running()) return;
        finish("Makro gestoppt.", Formatting.YELLOW);
    }

    private static void finish(String msg, Formatting color) {
        sentSell = false;
        state = State.IDLE;
        queue.clear();
        cur = null;
        best = null;
        say(msg, color);
    }

    private static void say(String msg, Formatting color) {
        status = msg;
        ClientPlayerEntity p = MinecraftClient.getInstance().player;
        if (p == null) return;
        boolean was = inChatHook;
        inChatHook = true; // eigene Meldung: nicht als Server-Antwort behandeln
        try {
            p.sendMessage(Text.literal(msg).formatted(color), false);
        } finally {
            inChatHook = was;
        }
    }

    // ------------------------------------------------------------------ Tick

    public static void tick(MinecraftClient client) {
        try {
            tickInner(client);
        } catch (Throwable t) {
            // Nie das Spiel crashen lassen: Makro stoppen, Fehler anzeigen und speichern.
            String where = t.getStackTrace().length > 0 ? t.getStackTrace()[0].toString() : "?";
            try {
                java.io.StringWriter sw = new java.io.StringWriter();
                t.printStackTrace(new java.io.PrintWriter(sw));
                Path f = FabricLoader.getInstance().getConfigDir().resolve("mapartassist").resolve("macro-error.txt");
                Files.createDirectories(f.getParent());
                Files.writeString(f, "Status beim Fehler: " + state + "\n" + sw);
            } catch (Exception ignored) { }
            String st = String.valueOf(state);
            state = State.IDLE;
            queue.clear();
            cur = null;
            best = null;
            sentSell = false;
            status = "FEHLER bei " + st + ": " + t;
            ClientPlayerEntity pl = MinecraftClient.getInstance().player;
            if (pl != null) {
                pl.sendMessage(Text.literal("[Makro] FEHLER im Schritt " + st + ": " + t).formatted(Formatting.RED), false);
                pl.sendMessage(Text.literal("[Makro] Stelle: " + where + " (Details: config/mapartassist/macro-error.txt)").formatted(Formatting.RED), false);
            }
        }
    }

    private static void tickInner(MinecraftClient client) {
        pollDump(client);
        if (watchChat > 0) watchChat--;
        if (state == State.IDLE) return;

        ClientPlayerEntity p = client.player;
        if (p == null || client.world == null || client.interactionManager == null) {
            finish("Makro abgebrochen.", Formatting.RED);
            return;
        }
        ModConfig cfg = ModConfig.get();
        timer++;

        switch (state) {
            case NEXT -> {
                cur = queue.poll();
                if (cur == null) { finish("Makro fertig.", Formatting.GREEN); return; }
                if (!hasFullStack(p, cur.item())) {
                    say("Kein voller Stack " + cur.item().getName().getString()
                            + " im Inventar; überspringe.", Formatting.YELLOW);
                    state = State.NEXT;
                    return;
                } // Nur volle Inventar-Stacks verkaufen
                best = null;
                filterToggleTried = false;
                if (cur.manual() > 0) { // manueller Preis: keine Auktionssuche
                    state = State.SELECT;
                    timer = 0;
                    return;
                }
                String search = Registries.ITEM.getId(cur.item()).getPath();
                say("Suche günstigstes Angebot: " + search, Formatting.GRAY);
                p.networkHandler.sendChatCommand("ah " + search);
                state = State.WAIT_GUI;
                timer = 0;
            }
            case WAIT_GUI -> {
                if (auctionOpen(client, p)) {
                    int targetAmount = targetAmount(p, cur);
                    Listing l = scan(p, cur.item(), targetAmount);
                    if (l != null && timer >= 8) {
                        best = l;
                        p.closeHandledScreen();
                        state = State.CLOSING;
                        timer = 0;
                    } else if (timer > 70) {
                        p.closeHandledScreen();
                        say("Kein Angebot für " + targetAmount(p, cur) + "x "
                                + cur.item().getName().getString() + " gefunden, überspringe.", Formatting.YELLOW);
                        state = State.DELAY;
                        timer = 0;
                    }
                } else if (timer > 80) {
                    finish("Auktionsmenü hat sich nicht geöffnet. Abgebrochen.", Formatting.RED);
                }
            }
            case CLOSING -> {
                if (timer >= 5 && client.currentScreen == null) {
                    state = State.SELECT;
                    timer = 0;
                } else if (timer > 40) {
                    finish("Menü ließ sich nicht schließen. Abgebrochen.", Formatting.RED);
                }
            }
            case SELECT -> {
                if (!cfg.macroDryRun && !selectItem(client, p, cur.item())) {
                    finish("Item nicht im Inventar. Abgebrochen.", Formatting.RED);
                    return;
                }
                state = State.SELL;
                timer = 0;
            }
            case SELL -> {
                if (timer == 1 && !cfg.macroDryRun) {
                    // Dem Server den gewaehlten Hotbar-Slot sofort mitteilen.
                    p.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(p.getInventory().getSelectedSlot()));
                }
                if (timer < 10) return; // Inventar-Wechsel + Slot-Sync beim Server setzen lassen
                int stackLimit = cur.item().getMaxCount();
                int have = cfg.macroDryRun ? firstFullStackCount(p, cur.item()) : handCount(p, cur.item());
                if (have < stackLimit) {
                    finish("Kein voller Stack " + cur.item().getName().getString()
                            + " in der Hand. Es wird nichts verkauft.", Formatting.YELLOW);
                    return;
                }
                int amount = stackLimit;
                if (cur.manual() <= 0 && best == null) {
                    finish("Kein gültiger Auktionspreis vorhanden. Abgebrochen.", Formatting.RED);
                    return;
                }
                // Verkaufsbefehl enthält sowohl den berechneten Gesamtpreis als auch
                // die im Menü konfigurierte Stückzahl.
                if (cur.manual() <= 0 && best.count() != amount) {
                    finish("Das gefundene Angebot passt nicht zur Verkaufsmenge (" + amount + "). Abgebrochen.", Formatting.RED);
                    return;
                }
                // Auto-Preis: nur den Gesamtpreis eines Angebots mit exakt dieser Menge nutzen.
                long price = cur.manual() > 0 ? cur.manual() : best.price();
                if (price < 1) {
                    finish("Berechneter Preis ist 0. Abgebrochen.", Formatting.RED);
                    return;
                }
                String cmd = "ah sell " + price + " " + amount;
                String info = amount + "x " + cur.item().getName().getString()
                        + (cur.manual() > 0 ? " (manueller Preis)"
                        : " (günstigstes Angebot: " + best.price() + " für " + best.count() + ")");
                if (cfg.macroDryRun) {
                    say("[TESTLAUF, nicht gesendet] /" + cmd + "   " + info, Formatting.AQUA);
                } else {
                    countBeforeSell = count(p, cur.item());
                    sentSell = true;
                    lastServerMsg = "";
                    watchChat = 100;
                    p.networkHandler.sendChatCommand(cmd);
                    say("Gesendet: /" + cmd + "   " + info, Formatting.GREEN);
                    say("Hand: " + p.getMainHandStack().getCount() + "x "
                            + p.getMainHandStack().getName().getString() + " (Hotbar-Slot "
                            + (p.getInventory().getSelectedSlot() + 1) + ")", Formatting.GRAY);
                    state = State.CONFIRM;
                    timer = 0;
                    return;
                }
                state = State.DELAY;
                timer = 0;
            }
            case CONFIRM -> {
                // Manche Auktions-Plugins oeffnen nach /ah sell ein Bestaetigungsmenue.
                if (auctionOpen(client, p)) {
                    if (timer < 4) return; // Inhalt vom Server laden lassen
                    Slot ok = findConfirmSlot(p);
                    dumpScreen(client);
                    if (ok == null) {
                        p.closeHandledScreen();
                        finish("Nach /ah sell öffnete sich ein Menü, aber ich fand keinen Bestätigen-Knopf. "
                                + "Inhalt steht in config/mapartassist/ah-dump.txt.", Formatting.RED);
                        return;
                    }
                    say("Bestätige Verkauf (Slot " + ok.id + ").", Formatting.GREEN);
                    client.interactionManager.clickSlot(p.currentScreenHandler.syncId, ok.id, 0, SlotActionType.PICKUP, p);
                    state = State.DELAY;
                    timer = 0;
                } else if (timer >= 20) {
                    state = State.DELAY; // kein Menue: Befehl wirkt direkt
                    timer = 0;
                }
            }
            case DELAY -> {
                if (client.currentScreen instanceof HandledScreen<?> && p.currentScreenHandler != p.playerScreenHandler && timer >= 10) {
                    p.closeHandledScreen();
                }
                if (timer >= 25) {
                    // Wiederholen: Wurde etwas eingestellt und ist noch Ware da, dasselbe Item nochmal
                    // (Preis wird dabei in der Auktion neu gelesen).
                    if (sentSell && cur != null) {
                        sentSell = false;
                        int now = count(p, cur.item());
                        say("Nach /ah sell: " + now + " im Inventar (vorher " + countBeforeSell + ")"
                                + (now < countBeforeSell ? " -> verkauft/eingestellt." : " -> unverändert."),
                                now < countBeforeSell ? Formatting.GREEN : Formatting.YELLOW);
                        if (now >= countBeforeSell) {
                            failedSells++;
                            if (failedSells >= 2) {
                                finish("Verkauf funktioniert nicht: Item ist nach /ah sell noch da (2 Versuche). "
                                        + (lastServerMsg.isEmpty() ? "Der Server hat nichts geantwortet."
                                        : "Server sagte: " + lastServerMsg), Formatting.RED);
                                return;
                            }
                        } else {
                            failedSells = 0;
                        }
                        if (cfg.macroRepeat && hasFullStack(p, cur.item())) {
                            // Die Inventarmenge ist kein sicherer Beleg dafuer, dass der Server
                            // den Verkaufsbefehl abgelehnt hat. Bei aktivierter Wiederholung das
                            // Item erneut einreihen; NEXT liest den Auktionspreis erneut und SELL
                            // sendet einen frischen /ah sell-Befehl.
                            if (now >= countBeforeSell) {
                                say("Inventarmenge unverändert; erneuter Versuch mit Preisprüfung für "
                                        + cur.item().getName().getString() + ".", Formatting.YELLOW);
                            }
                            queue.addFirst(cur);
                        }
                    }
                    state = State.NEXT;
                    timer = 0;
                }
            }
            default -> { }
        }
    }

    // ------------------------------------------------------------------ Auktions-Menue lesen

    /** Irgendein Kisten-Menue ist offen (der Titel der Auktion ist ein Resourcepack-Bild, daher keine Titelpruefung). */
    private static boolean auctionOpen(MinecraftClient client, ClientPlayerEntity p) {
        return client.currentScreen instanceof HandledScreen<?>
                && p.currentScreenHandler != p.playerScreenHandler;
    }

    /**
     * Günstigstes Angebot für einen vollständigen Stack des Items.
     * Angebote mit einer anderen Stackgröße werden nicht verwendet.
     */
    private static Listing scan(ClientPlayerEntity p, Item item, int targetAmount) {
        Listing bestL = null;
        if (targetAmount < 1) return null;
        for (Slot slot : p.currentScreenHandler.slots) {
            if (slot.inventory instanceof PlayerInventory) continue;
            ItemStack st = slot.getStack();
            if (st.isEmpty() || st.getItem() != item || st.getCount() != targetAmount) continue;
            long price = readPrice(st);
            if (price <= 0) continue;
            Listing l = new Listing(price, st.getCount());
            if (bestL == null || l.price() < bestL.price()) bestL = l;
        }
        return bestL;
    }

    /** Zielmenge ist immer ein vollständiger Stack; Teilstacks werden ignoriert. */
    private static int targetAmount(ClientPlayerEntity p, Job job) {
        return hasFullStack(p, job.item()) ? job.item().getMaxCount() : 0;
    }

    private static List<String> loreLines(ItemStack st) {
        List<String> out = new ArrayList<>();
        LoreComponent lore = st.get(DataComponentTypes.LORE);
        if (lore != null) for (Text t : lore.lines()) out.add(t.getString());
        return out;
    }

    /** Liest "Preis: $240.000" aus den Lore-Zeilen. -1 wenn nicht gefunden. */
    static long readPrice(ItemStack st) {
        for (String line : loreLines(st)) {
            int i = line.toLowerCase().indexOf("preis");
            if (i < 0) continue;
            Matcher m = PRICE.matcher(line.substring(i));
            if (m.find()) return parsePrice(m.group(1));
        }
        return -1;
    }

    /** "200k", "1.5m", "240.000", "240000" -> Zahl. -1 bei Fehler. */
    public static long parseMoney(String raw) {
        String t = raw.trim().toLowerCase().replace("$", "");
        long mult = 1;
        if (t.endsWith("k")) { mult = 1_000L; t = t.substring(0, t.length() - 1); }
        else if (t.endsWith("m")) { mult = 1_000_000L; t = t.substring(0, t.length() - 1); }
        else if (t.endsWith("b")) { mult = 1_000_000_000L; t = t.substring(0, t.length() - 1); }
        try {
            if (mult == 1) return parsePrice(t);
            return Math.round(Double.parseDouble(t.replace(',', '.')) * mult);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static void message(String msg, Formatting color) { say(msg, color); }

    static long parsePrice(String raw) {
        String s = raw.replaceAll("[.,]+$", "");
        try {
            if (THOUSANDS.matcher(s).matches()) return Long.parseLong(s.replaceAll("[.,]", ""));
            return Math.round(Double.parseDouble(s.replace(',', '.')));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // ------------------------------------------------------------------ Inventar

    private static int count(ClientPlayerEntity p, Item item) {
        int n = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack st = p.getInventory().getStack(i);
            if (st.getItem() == item) n += st.getCount();
        }
        return n;
    }

    private static int firstStackCount(ClientPlayerEntity p, Item item) {
        for (int i = 0; i < 36; i++) {
            ItemStack st = p.getInventory().getStack(i);
            if (st.getItem() == item) return st.getCount();
        }
        return 0;
    }

    /** True only when one inventory slot contains a complete stack. */
    private static boolean hasFullStack(ClientPlayerEntity p, Item item) {
        return firstFullStackCount(p, item) >= item.getMaxCount();
    }

    private static int firstFullStackCount(ClientPlayerEntity p, Item item) {
        for (int i = 0; i < 36; i++) {
            ItemStack st = p.getInventory().getStack(i);
            if (st.getItem() == item && st.getCount() >= item.getMaxCount()) return st.getCount();
        }
        return 0;
    }

    private static int handCount(ClientPlayerEntity p, Item item) {
        ItemStack st = p.getMainHandStack();
        return st.getItem() == item ? st.getCount() : 0;
    }

    /** Hotbar zuerst, sonst aus dem Inventar in die Hotbar tauschen. */
    private static boolean selectItem(MinecraftClient client, ClientPlayerEntity p, Item item) {
        int required = item.getMaxCount();
        for (int i = 0; i < 9; i++) {
            ItemStack st = p.getInventory().getStack(i);
            if (st.getItem() == item && st.getCount() >= required) {
                p.getInventory().setSelectedSlot(i);
                return true;
            }
        }
        for (int s = 9; s < 36; s++) {
            ItemStack source = p.getInventory().getStack(s);
            if (source.getItem() != item || source.getCount() < required) continue;
            int target = -1;
            for (int i = 0; i < 9; i++) {
                if (p.getInventory().getStack(i).isEmpty()) { target = i; break; }
            }
            if (target < 0) target = p.getInventory().getSelectedSlot();
            client.interactionManager.clickSlot(p.playerScreenHandler.syncId, s, target, SlotActionType.SWAP, p);
            p.getInventory().setSelectedSlot(target);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ Diagnose (F8 bei offenem Menue)

    private static void pollDump(MinecraftClient client) {
        boolean down = client.getWindow() != null && GLFW.glfwGetKey(
                client.getWindow().getHandle(), GLFW.GLFW_KEY_F8) == GLFW.GLFW_PRESS;
        boolean edge = down && !f8Down;
        f8Down = down;
        if (!edge || client.player == null) return;
        dumpScreen(client);
    }

    private static Slot findConfirmSlot(ClientPlayerEntity p) {
        String[] words = {"confirm", "bestätig", "bestaetig", "accept", "akzept", "bestätigen", "yes", "ja", "einstellen", "list"};
        Slot green = null;
        for (Slot slot : p.currentScreenHandler.slots) {
            if (slot.inventory instanceof PlayerInventory) continue;
            ItemStack st = slot.getStack();
            if (st.isEmpty()) continue;
            String n = st.getName().getString().toLowerCase();
            for (String w : words) if (n.contains(w)) return slot;
            String id = Registries.ITEM.getId(st.getItem()).getPath();
            if (green == null && (id.startsWith("lime_") || id.startsWith("green_") || id.equals("emerald_block"))) green = slot;
        }
        return green;
    }

    private static void dumpScreen(MinecraftClient client) {
        if (!(client.currentScreen instanceof HandledScreen<?> hs)) return;

        List<String> lines = new ArrayList<>();
        lines.add("Titel: " + hs.getTitle().getString());
        for (Slot slot : client.player.currentScreenHandler.slots) {
            if (slot.inventory instanceof PlayerInventory) continue;
            ItemStack st = slot.getStack();
            if (st.isEmpty()) continue;
            StringBuilder sb = new StringBuilder("slot " + slot.id + ": "
                    + Registries.ITEM.getId(st.getItem()) + " x" + st.getCount()
                    + " | name=" + st.getName().getString());
            for (String l : loreLines(st)) sb.append(" | ").append(l);
            lines.add(sb.toString());
        }
        try {
            Path f = FabricLoader.getInstance().getConfigDir().resolve("mapartassist").resolve("ah-dump.txt");
            Files.createDirectories(f.getParent());
            Files.write(f, lines);
            say("Diagnose gespeichert: config/mapartassist/ah-dump.txt (" + lines.size() + " Zeilen)", Formatting.AQUA);
        } catch (Exception e) {
            say("Diagnose konnte nicht gespeichert werden.", Formatting.RED);
        }
    }
}
