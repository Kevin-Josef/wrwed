package de.mapartassist.macro;

import de.mapartassist.config.ModConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
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
 *  1. /ah &lt;item&gt; sucht, 2. guenstigstes Angebot (Preis pro Stueck) aus dem Menue lesen,
 *  3. Item in die Hand, 4. /ah sell &lt;preis&gt; [menge] mit demselben Stueckpreis.
 * Standardmaessig im Testlauf (sendet nichts).
 */
public final class AuctionMacro {
    private AuctionMacro() {}

    private enum State { IDLE, NEXT, WAIT_GUI, CLOSING, SELECT, SELL, DELAY }

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

    public static String status = "";

    public static boolean running() { return state != State.IDLE; }

    // ------------------------------------------------------------------ Steuerung

    public static void start() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity p = mc.player;
        if (p == null) return;
        if (running()) { say("Makro läuft bereits. Stoppen: /ahmacro stop", Formatting.YELLOW); return; }

        ModConfig cfg = ModConfig.get();
        queue.clear();
        for (String id : cfg.sellItems) {
            Item it;
            try {
                it = Registries.ITEM.get(Identifier.of(id));
            } catch (Exception e) {
                continue;
            }
            if (count(p, it) > 0) queue.add(new Job(it, cfg.sellAmounts.getOrDefault(id, 0), cfg.sellPrices.getOrDefault(id, 0L)));
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
        if (p != null) p.sendMessage(Text.literal(msg).formatted(color), false);
    }

    // ------------------------------------------------------------------ Tick

    public static void tick(MinecraftClient client) {
        pollDump(client);
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
                if (count(p, cur.item()) == 0) return; // naechstes Item im naechsten Tick
                best = null;
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
                    Listing l = scan(p, cur.item());
                    if (l != null && timer >= 6) {
                        best = l;
                        p.closeHandledScreen();
                        state = State.CLOSING;
                        timer = 0;
                    } else if (timer > 70) {
                        p.closeHandledScreen();
                        say("Kein Angebot für " + cur.item().getName().getString() + " gefunden, überspringe.", Formatting.YELLOW);
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
                if (timer < 3) return; // Inventar-Wechsel setzen lassen
                int have = cfg.macroDryRun ? firstStackCount(p, cur.item()) : handCount(p, cur.item());
                if (have <= 0) {
                    finish("Item nicht in der Hand. Abgebrochen.", Formatting.RED);
                    return;
                }
                int amount = cur.amount() > 0 ? Math.min(cur.amount(), have) : have;
                long price = cur.manual() > 0 ? cur.manual() : (amount == best.count() ? best.price() : Math.round(best.unit() * amount));
                if (price < 1) {
                    finish("Berechneter Preis ist 0. Abgebrochen.", Formatting.RED);
                    return;
                }
                String cmd = "ah sell " + price + (amount < have ? " " + amount : "");
                String info = amount + "x " + cur.item().getName().getString()
                        + (cur.manual() > 0 ? " (manueller Preis)"
                        : " (günstigstes Angebot: " + best.price() + " für " + best.count() + ")");
                if (cfg.macroDryRun) {
                    say("[TESTLAUF, nicht gesendet] /" + cmd + "   " + info, Formatting.AQUA);
                } else {
                    countBeforeSell = count(p, cur.item());
                    sentSell = true;
                    p.networkHandler.sendChatCommand(cmd);
                    say("Gesendet: /" + cmd + "   " + info, Formatting.GREEN);
                }
                state = State.DELAY;
                timer = 0;
            }
            case DELAY -> {
                if (timer >= 25) {
                    // Wiederholen: Wurde etwas eingestellt und ist noch Ware da, dasselbe Item nochmal
                    // (Preis wird dabei in der Auktion neu gelesen).
                    if (sentSell && cur != null) {
                        sentSell = false;
                        int now = count(p, cur.item());
                        if (cfg.macroRepeat && now > 0) {
                            if (now < countBeforeSell) queue.addFirst(cur);
                            else say("Es wurde nichts eingestellt (Menge unverändert), überspringe "
                                    + cur.item().getName().getString() + ".", Formatting.YELLOW);
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

    /** Guenstigstes Angebot (nach Stueckpreis) fuer das Item im geoeffneten Menue. */
    private static Listing scan(ClientPlayerEntity p, Item item) {
        Listing bestL = null;
        for (Slot slot : p.currentScreenHandler.slots) {
            if (slot.inventory instanceof PlayerInventory) continue;
            ItemStack st = slot.getStack();
            if (st.isEmpty() || st.getItem() != item) continue;
            long price = readPrice(st);
            if (price <= 0) continue;
            Listing l = new Listing(price, st.getCount());
            if (bestL == null || l.unit() < bestL.unit()) bestL = l;
        }
        return bestL;
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

    private static int handCount(ClientPlayerEntity p, Item item) {
        ItemStack st = p.getMainHandStack();
        return st.getItem() == item ? st.getCount() : 0;
    }

    /** Hotbar zuerst, sonst aus dem Inventar in die Hotbar tauschen. */
    private static boolean selectItem(MinecraftClient client, ClientPlayerEntity p, Item item) {
        for (int i = 0; i < 9; i++) {
            if (p.getInventory().getStack(i).getItem() == item) {
                p.getInventory().setSelectedSlot(i);
                return true;
            }
        }
        for (int s = 9; s < 36; s++) {
            if (p.getInventory().getStack(s).getItem() != item) continue;
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
