package de.mapartassist.gui;

import de.mapartassist.config.ModConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Eigenes Hintergrundbild fuer das Menue (PNG/JPG aus config/mapartassist/backgrounds). */
public final class BackgroundImage {
    private BackgroundImage() {}

    public record Data(Identifier id, int w, int h) {}

    private static final int MAX_W = 1280;

    private static Data current = null;
    private static String currentName = null;
    private static boolean failed = false;
    private static Identifier registered = null;
    private static int counter = 0;
    public static String status = "";

    public static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("mapartassist").resolve("backgrounds");
    }

    private static boolean isImage(String n) {
        String l = n.toLowerCase(Locale.ROOT);
        return l.endsWith(".png") || l.endsWith(".jpg") || l.endsWith(".jpeg");
    }

    public static List<String> list() {
        List<String> out = new ArrayList<>();
        try {
            Files.createDirectories(dir());
            try (Stream<Path> s = Files.list(dir())) {
                s.filter(Files::isRegularFile)
                        .map(p -> p.getFileName().toString())
                        .filter(BackgroundImage::isImage)
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .forEach(out::add);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /** Liefert die geladene Textur des gewaehlten Bildes oder null. */
    public static Data get() {
        String name = ModConfig.get().customBackground;
        if (name == null || name.isEmpty()) return null;
        if (name.equals(currentName)) return failed ? null : current;
        load(name);
        return failed ? null : current;
    }

    private static void unregister() {
        if (registered != null) {
            try {
                MinecraftClient.getInstance().getTextureManager().destroyTexture(registered);
            } catch (Exception ignored) {
            }
            registered = null;
        }
        current = null;
    }

    private static void load(String name) {
        unregister();
        currentName = name;
        failed = true;
        try {
            Path file = dir().resolve(Path.of(name).getFileName().toString());
            if (!Files.isRegularFile(file)) throw new IllegalStateException("Datei fehlt");
            BufferedImage src = ImageIO.read(file.toFile());
            if (src == null) throw new IllegalStateException("Kein gültiges Bild");

            int w = src.getWidth(), h = src.getHeight();
            if (w > MAX_W) {
                h = Math.max(1, Math.round(h * (MAX_W / (float) w)));
                w = MAX_W;
            }
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, w, h, null);
            g.dispose();

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", bos);
            NativeImage ni = NativeImage.read(new ByteArrayInputStream(bos.toByteArray()));

            Identifier id = Identifier.of("mapartassist", "textures/background/b" + (counter++) + ".png");
            MinecraftClient.getInstance().getTextureManager().registerTexture(id,
                    new NativeImageBackedTexture(() -> "mapartassist background " + name, ni));
            registered = id;
            current = new Data(id, w, h);
            failed = false;
        } catch (Exception e) {
            status = "Bild konnte nicht geladen werden: " + e.getMessage();
        }
    }

    public static void select(String name) {
        ModConfig.get().customBackground = name == null ? "" : name;
        ModConfig.save();
        currentName = null; // erzwingt Neuladen
    }

    /** Kopiert eine per Drag & Drop abgelegte Datei in den Bilder-Ordner und waehlt sie aus. */
    public static String importFile(Path src) {
        try {
            String n = src.getFileName().toString();
            if (!isImage(n)) return "Nur PNG oder JPG werden unterstützt.";
            Files.createDirectories(dir());
            Path dst = dir().resolve(n);
            if (!src.toAbsolutePath().normalize().equals(dst.toAbsolutePath().normalize())) {
                Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
            }
            select(n);
            status = "Hintergrund gesetzt: " + n;
            return null;
        } catch (Exception e) {
            return "Import fehlgeschlagen: " + e.getMessage();
        }
    }
}
