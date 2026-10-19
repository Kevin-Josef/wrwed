package de.mapartassist.cape;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Vorschau-Texturen fuer das Menue: Aussen- und Innenseite des Capes in hoher Qualitaet
 * (bis 160x256 pro Seite, kleinere Capes bleiben pixelgenau).
 */
public final class CapePreview {
    private CapePreview() {}

    /** id = Textur, tw/th = Groesse einer Seite im Atlas (Aussen links, Innen rechts). */
    public record Data(Identifier id, int tw, int th) {
        public int texW() { return tw * 2; }
        public int texH() { return th; }
    }

    private static final Map<String, Data> cache = new HashMap<>();
    private static final Set<String> failed = new HashSet<>();
    private static final List<Identifier> registered = new ArrayList<>();
    private static int counter = 0;

    public static void clear() {
        MinecraftClient mc = MinecraftClient.getInstance();
        for (Identifier id : registered) {
            try {
                mc.getTextureManager().destroyTexture(id);
            } catch (Exception ignored) {
            }
        }
        registered.clear();
        cache.clear();
        failed.clear();
    }

    public static Data get(String name) {
        if (name == null || name.isEmpty()) return null;
        Data d = cache.get(name);
        if (d != null) return d;
        if (failed.contains(name)) return null;
        try {
            CapeManager.Entry e = CapeManager.find(name);
            if (e == null) throw new IllegalStateException("fehlt");
            BufferedImage img = CapeManager.readNormalized(e.cape());
            int k = img.getWidth() / 64;
            if (k < 1) throw new IllegalStateException("zu klein");

            int tw = Math.min(10 * k, 160);
            int th = tw * 16 / 10;

            BufferedImage atlas = new BufferedImage(tw * 2, th, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = atlas.createGraphics();
            g.drawImage(face(img, 1 * k, 1 * k, 10 * k, 16 * k, tw, th), 0, 0, null);
            g.drawImage(face(img, 12 * k, 1 * k, 10 * k, 16 * k, tw, th), tw, 0, null);
            g.dispose();

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(atlas, "png", bos);
            NativeImage ni = NativeImage.read(new ByteArrayInputStream(bos.toByteArray()));

            Identifier id = Identifier.of("mapartassist", "textures/preview/p" + (counter++) + ".png");
            MinecraftClient.getInstance().getTextureManager().registerTexture(id,
                    new NativeImageBackedTexture(() -> "mapartassist preview " + name, ni));
            registered.add(id);

            d = new Data(id, tw, th);
            cache.put(name, d);
            return d;
        } catch (Exception ex) {
            failed.add(name);
            return null;
        }
    }

    private static BufferedImage face(BufferedImage img, int fx, int fy, int fw, int fh, int tw, int th) {
        BufferedImage sub = img.getSubimage(fx, fy, fw, fh);
        if (fw == tw && fh == th) return sub;
        Image scaled = sub.getScaledInstance(tw, th, Image.SCALE_AREA_AVERAGING);
        BufferedImage out = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(scaled, 0, 0, null);
        g.dispose();
        return out;
    }
}
