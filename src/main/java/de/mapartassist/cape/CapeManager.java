package de.mapartassist.cape;

import de.mapartassist.config.ModConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Cape-Bibliothek (nur fuer den eigenen Spieler sichtbar).
 * Dateien: config/mapartassist/capes/NAME.png (Cape) und optional NAME.elytra.png (Elytra).
 */
public final class CapeManager {
    private CapeManager() {}

    public record Entry(String name, Path cape, Path elytra) {
        public boolean hasElytra() { return elytra != null; }
    }

    private static final String ELYTRA_SUFFIX = ".elytra.png";

    /** Letzte Meldung fuer das Menue. */
    public static String status = "";

    private static List<Entry> entries = new ArrayList<>();
    private static boolean scanned = false;
    private static int counter = 0;

    // aktuell geladene Texturen
    private static String loadedFor = null;
    private static String failedFor = null;
    private static AssetInfo.TextureAsset loadedCape = null;
    private static AssetInfo.TextureAsset loadedElytra = null;
    private static final List<Identifier> registered = new ArrayList<>();
    private static SkinTextures lastBase = null;
    private static SkinTextures lastResult = null;

    public static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("mapartassist").resolve("capes");
    }

    // ------------------------------------------------------------------ Bibliothek

    public static List<Entry> list() {
        if (!scanned) scan();
        return entries;
    }

    public static Entry find(String name) {
        for (Entry e : list()) if (e.name().equals(name)) return e;
        return null;
    }

    public static void scan() {
        List<Entry> found = new ArrayList<>();
        try {
            Files.createDirectories(dir());
            try (Stream<Path> s = Files.list(dir())) {
                s.filter(p -> {
                    String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                    return n.endsWith(".png") && !n.endsWith(ELYTRA_SUFFIX);
                }).sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
                  .forEach(p -> {
                      String file = p.getFileName().toString();
                      String name = file.substring(0, file.length() - 4);
                      Path el = dir().resolve(name + ELYTRA_SUFFIX);
                      found.add(new Entry(name, p, Files.exists(el) ? el : null));
                  });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        entries = found;
        scanned = true;
    }

    /** Importiert eine PNG. asElytra=true haengt sie als Elytra an das gewaehlte Cape. Liefert Fehlertext oder null. */
    public static String importPng(Path src, boolean asElytra) {
        String file = src.getFileName().toString();
        if (!file.toLowerCase(Locale.ROOT).endsWith(".png")) return "Nur PNG-Dateien: " + file;

        Path tmp = null;
        try {
            // Altes 22x17-Format (auch vergroessert, z. B. 176x136) in das 64x32-Layout umwandeln.
            if (!asElytra) {
                BufferedImage bi = ImageIO.read(src.toFile());
                if (bi != null) {
                    int w = bi.getWidth(), h = bi.getHeight();
                    boolean already = w >= 64 && w % 64 == 0 && h * 2 == w;
                    if (!already && w % 22 == 0 && h == 17 * (w / 22)) {
                        int k = w / 22;
                        BufferedImage out = new BufferedImage(64 * k, 32 * k, BufferedImage.TYPE_INT_ARGB);
                        for (int y = 0; y < h; y++) {
                            for (int x = 0; x < w; x++) out.setRGB(x, y, bi.getRGB(x, y));
                        }
                        tmp = Files.createTempFile("mapartassist-cape", ".png");
                        ImageIO.write(out, "png", tmp.toFile());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            tmp = null;
        }
        Path source = tmp != null ? tmp : src;

        String err = validate(source);
        if (err != null) {
            deleteQuietly(tmp);
            return err;
        }

        try {
            Files.createDirectories(dir());
            ModConfig cfg = ModConfig.get();
            if (asElytra) {
                String sel = cfg.selectedCape;
                if (sel == null || sel.isEmpty() || find(sel) == null) return "Erst ein Cape auswählen.";
                Files.copy(source, dir().resolve(sel + ELYTRA_SUFFIX), StandardCopyOption.REPLACE_EXISTING);
                status = "Elytra für \"" + sel + "\" gespeichert.";
            } else {
                String name = sanitize(file.substring(0, file.length() - 4));
                if (name.isEmpty()) name = "cape";
                Files.copy(source, dir().resolve(name + ".png"), StandardCopyOption.REPLACE_EXISTING);
                cfg.selectedCape = name;
                ModConfig.save();
                status = "Cape \"" + name + "\" importiert.";
            }
            invalidate();
            scan();
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return "Fehler beim Kopieren: " + e.getMessage();
        } finally {
            deleteQuietly(tmp);
        }
    }

    private static void deleteQuietly(Path p) {
        if (p == null) return;
        try {
            Files.deleteIfExists(p);
        } catch (Exception ignored) {
        }
    }

    public static void delete(String name) {
        try {
            Files.deleteIfExists(dir().resolve(name + ".png"));
            Files.deleteIfExists(dir().resolve(name + ELYTRA_SUFFIX));
        } catch (Exception e) {
            e.printStackTrace();
        }
        ModConfig cfg = ModConfig.get();
        if (name.equals(cfg.selectedCape)) {
            cfg.selectedCape = "";
            ModConfig.save();
        }
        invalidate();
        scan();
        status = "\"" + name + "\" gelöscht.";
    }

    public static void select(String name) {
        ModConfig cfg = ModConfig.get();
        cfg.selectedCape = name == null ? "" : name;
        ModConfig.save();
        failedFor = null;
        status = "";
    }

    private static String sanitize(String s) {
        return s.replaceAll("[^A-Za-z0-9 _\\-]", "_").trim();
    }

    /** Cape/Elytra-Texturen sind 64x32 (oder ein Vielfaches davon, z. B. 128x64). */
    private static String validate(Path p) {
        try (InputStream in = Files.newInputStream(p); NativeImage img = NativeImage.read(in)) {
            int w = img.getWidth(), h = img.getHeight();
            if (w < 64 || w % 64 != 0 || h * 2 != w) {
                return "Falsche Größe " + w + "x" + h + " (erwartet 64x32, 128x64, ... oder 22x17)";
            }
            return null;
        } catch (Exception e) {
            return "Kein gültiges PNG: " + p.getFileName();
        }
    }

    // ------------------------------------------------------------------ Texturen

    /** Verwirft geladene Texturen (nach Aenderung an den Dateien). Nur im Render-Thread aufrufen. */
    public static void invalidate() {
        MinecraftClient mc = MinecraftClient.getInstance();
        for (Identifier id : registered) {
            try {
                mc.getTextureManager().destroyTexture(id);
            } catch (Exception ignored) {
            }
        }
        registered.clear();
        loadedFor = null;
        failedFor = null;
        loadedCape = null;
        loadedElytra = null;
        lastBase = null;
        lastResult = null;
    }

    private static AssetInfo.TextureAsset register(Path file, String name, String kind) throws Exception {
        NativeImage image;
        try (InputStream in = Files.newInputStream(file)) {
            image = NativeImage.read(in);
        }
        String safe = sanitize(name).toLowerCase(Locale.ROOT).replace(' ', '_')
                .replaceAll("[^a-z0-9_\\-]", "_");
        String key = kind + "/" + safe + "_" + Integer.toHexString(name.hashCode()) + "_" + (counter++);
        Identifier id = Identifier.of("mapartassist", key);
        Identifier texturePath = Identifier.of("mapartassist", "textures/" + key + ".png");
        MinecraftClient.getInstance().getTextureManager().registerTexture(texturePath,
                new NativeImageBackedTexture(() -> "mapartassist " + kind + " " + name, image));
        registered.add(texturePath);
        return new AssetInfo.TextureAssetInfo(id, texturePath);
    }

    private static void load(String name) {
        invalidate();
        Entry e = find(name);
        if (e == null) {
            failedFor = name;
            status = "Cape \"" + name + "\" nicht gefunden.";
            return;
        }
        try {
            AssetInfo.TextureAsset cape = register(e.cape(), name, "cape");
            AssetInfo.TextureAsset elytra = e.hasElytra() ? register(e.elytra(), name, "elytra") : cape;
            loadedCape = cape;
            loadedElytra = elytra;
            loadedFor = name;
        } catch (Exception ex) {
            ex.printStackTrace();
            failedFor = name;
            status = "Cape konnte nicht geladen werden.";
            invalidate();
            failedFor = name;
        }
    }

    /** Wird vom Mixin aufgerufen: liefert die Skin-Texturen mit eigenem Cape (falls gewaehlt). */
    public static SkinTextures apply(SkinTextures base) {
        String sel = ModConfig.get().selectedCape;
        if (base == null || sel == null || sel.isEmpty()) return base;

        if (!sel.equals(loadedFor)) {
            if (sel.equals(failedFor)) return base;
            load(sel);
            if (loadedCape == null) return base;
        }
        if (base == lastBase && lastResult != null) return lastResult;
        lastBase = base;
        lastResult = new SkinTextures(base.body(), loadedCape, loadedElytra, base.model(), base.secure());
        return lastResult;
    }
}
