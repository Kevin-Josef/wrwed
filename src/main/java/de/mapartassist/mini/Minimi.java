package de.mapartassist.mini;

import com.mojang.authlib.GameProfile;
import de.mapartassist.config.ModConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Minimi: ein kleiner Begleiter (nur auf deinem Bildschirm), der dir hinterherlaeuft und einen eigenen Skin traegt.
 * Skins liegen in config/mapartassist/skins (64x64 oder altes 64x32 PNG).
 */
public final class Minimi {
    private Minimi() {}

    public static String status = "";

    private static MiniEntity entity;
    private static ClientWorld entityWorld;
    private static int nextId = -2_000_000;
    private static boolean mismatch = false;
    private static int rebuilds = 0;
    private static double lastScale = -1;

    // geladener Skin
    private static String loadedName = null;
    private static boolean loadFailed = false;
    private static AssetInfo.TextureAsset loadedBody = null;
    private static boolean loadedSlim = false;
    private static Identifier registered = null;
    private static int counter = 0;

    /** Kleine Entity-Klasse: ohne Namensschild, nicht anklickbar, bewegt sich innerhalb des Ticks (Lauf-Animation). */
    private static final class MiniEntity extends OtherClientPlayerEntity {
        double tx, ty, tz;
        float tyaw;
        boolean hasTarget = false;

        MiniEntity(ClientWorld world, GameProfile profile) {
            super(world, profile);
        }

        void setGoal(double x, double y, double z, float yaw) {
            tx = x; ty = y; tz = z; tyaw = yaw;
            hasTarget = true;
        }

        @Override
        public void tick() {
            if (hasTarget) {
                this.setPosition(tx, ty, tz);
                this.setYaw(tyaw);
                this.setHeadYaw(tyaw);
                this.setBodyYaw(tyaw);
                this.setPitch(0f);
            }
            super.tick();
        }

        public boolean shouldRenderName() { return false; }

        public boolean canHit() { return false; }
    }

    public static boolean isMini(Object o) { return o != null && o == entity; }

    // ------------------------------------------------------------------ Skin-Dateien

    public static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("mapartassist").resolve("skins");
    }

    public static List<String> list() {
        List<String> out = new ArrayList<>();
        try {
            Files.createDirectories(dir());
            try (Stream<Path> s = Files.list(dir())) {
                s.filter(Files::isRegularFile).map(p -> p.getFileName().toString())
                        .filter(n -> n.toLowerCase(Locale.ROOT).endsWith(".png"))
                        .sorted(String.CASE_INSENSITIVE_ORDER).forEach(out::add);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static void select(String name) {
        ModConfig.get().minimiSkin = name == null ? "" : name;
        ModConfig.save();
        loadedName = null; // Neuladen erzwingen
        mismatch = false;
        rebuilds = 0;
    }

    /** Kopiert eine per Drag & Drop abgelegte Skin-Datei in den Skin-Ordner und waehlt sie aus. */
    public static String importFile(Path src) {
        try {
            String n = src.getFileName().toString();
            if (!n.toLowerCase(Locale.ROOT).endsWith(".png")) return "Nur PNG-Skins werden unterstützt.";
            Files.createDirectories(dir());
            Path dst = dir().resolve(n);
            if (!src.toAbsolutePath().normalize().equals(dst.toAbsolutePath().normalize())) {
                Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
            }
            select(n);
            status = "Skin gesetzt: " + n;
            return null;
        } catch (Exception e) {
            return "Import fehlgeschlagen: " + e.getMessage();
        }
    }

    // ------------------------------------------------------------------ Skin laden

    /** Altes 64x32-Format auf 64x64 erweitern (linke Arme/Beine spiegeln, wie das Spiel es auch macht). */
    private static BufferedImage normalize(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        if (w != 64 || (h != 64 && h != 32)) {
            throw new IllegalArgumentException("Skin muss 64x64 (oder 64x32) sein, ist aber " + w + "x" + h);
        }
        BufferedImage out = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        if (h == 32) {
            int[][] r = {
                    {4, 16, 16, 32, 4, 4}, {8, 16, 16, 32, 4, 4}, {0, 20, 24, 32, 4, 12}, {4, 20, 16, 32, 4, 12},
                    {8, 20, 8, 32, 4, 12}, {12, 20, 16, 32, 4, 12},
                    {44, 16, -8, 32, 4, 4}, {48, 16, -8, 32, 4, 4}, {40, 20, 0, 32, 4, 12}, {44, 20, -8, 32, 4, 12},
                    {48, 20, -16, 32, 4, 12}, {52, 20, -8, 32, 4, 12}};
            for (int[] q : r) {
                for (int dy = 0; dy < q[5]; dy++) {
                    for (int dx = 0; dx < q[4]; dx++) {
                        int sx = q[0] + dx, sy = q[1] + dy;
                        int tx = q[0] + q[2] + (q[4] - 1 - dx), ty = q[1] + q[3] + dy;
                        out.setRGB(tx, ty, out.getRGB(sx, sy));
                    }
                }
            }
        }
        return out;
    }

    /** Schmale Arme (Alex): rechter Arm hat in der letzten Spalte durchsichtige Pixel. */
    private static boolean detectSlim(BufferedImage img, boolean legacy) {
        if (legacy) return false;
        for (int y = 20; y < 32; y++) {
            for (int x = 54; x < 56; x++) {
                if (((img.getRGB(x, y) >>> 24) & 0xFF) != 0) return false;
            }
        }
        return true;
    }

    private static void unregister() {
        if (registered != null) {
            try {
                MinecraftClient.getInstance().getTextureManager().destroyTexture(registered);
            } catch (Exception ignored) {
            }
            registered = null;
        }
        loadedBody = null;
    }

    private static void ensureLoaded() {
        String name = ModConfig.get().minimiSkin;
        if (name == null || name.isEmpty()) {
            if (loadedBody != null) unregister();
            loadedName = "";
            loadFailed = false;
            return;
        }
        if (name.equals(loadedName)) return;
        unregister();
        loadedName = name;
        loadFailed = true;
        try {
            Path file = dir().resolve(Path.of(name).getFileName().toString());
            BufferedImage raw = ImageIO.read(file.toFile());
            if (raw == null) throw new IllegalStateException("Kein gültiges Bild");
            boolean legacy = raw.getHeight() == 32;
            BufferedImage img = normalize(raw);
            loadedSlim = detectSlim(img, legacy);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", bos);
            NativeImage ni = NativeImage.read(new ByteArrayInputStream(bos.toByteArray()));
            String key = "minimi/skin_" + (counter++);
            Identifier id = Identifier.of("mapartassist", key);
            Identifier texturePath = Identifier.of("mapartassist", "textures/" + key + ".png");
            MinecraftClient.getInstance().getTextureManager().registerTexture(texturePath,
                    new NativeImageBackedTexture(() -> "mapartassist minimi " + name, ni));
            registered = texturePath;
            loadedBody = new AssetInfo.TextureAssetInfo(id, texturePath);
            loadFailed = false;
            status = "Skin geladen: " + name + (loadedSlim ? " (schmale Arme)" : "");
        } catch (Exception e) {
            status = "Skin konnte nicht geladen werden: " + e.getMessage();
        }
    }

    /** Wird vom Mixin fuer die Minimi-Entity aufgerufen und tauscht die Haut aus. */
    public static SkinTextures skinFor(SkinTextures base) {
        if (base == null) return base;
        ensureLoaded();
        if (loadedBody == null || loadFailed) return base;
        boolean baseSlim = String.valueOf(base.model()).toLowerCase(Locale.ROOT).contains("slim");
        if (baseSlim != loadedSlim) mismatch = true; // falsches Armmodell: Entity mit anderer UUID neu bauen
        return new SkinTextures(loadedBody, null, null, base.model(), base.secure());
    }

    // ------------------------------------------------------------------ Entity

    private static void remove() {
        if (entity != null) {
            try {
                entity.remove(Entity.RemovalReason.DISCARDED);
            } catch (Exception ignored) {
            }
            entity = null;
            entityWorld = null;
        }
    }

    private static void create(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        GameProfile gp = new GameProfile(UUID.randomUUID(), "Minimi");
        MiniEntity e = new MiniEntity(mc.world, gp);
        e.setId(nextId--);
        e.noClip = true;
        e.setNoGravity(true);
        e.setPosition(p.getX(), p.getY(), p.getZ());
        mc.world.addEntity(e);
        entity = e;
        entityWorld = mc.world;
        lastScale = -1;
    }

    public static void tick(MinecraftClient mc) {
        try {
            inner(mc);
        } catch (Throwable t) {
            status = "Minimi-Fehler: " + t;
            ModConfig.get().minimiEnabled = false;
            try {
                remove();
            } catch (Throwable ignored) {
            }
        }
    }

    private static void inner(MinecraftClient mc) {
        ModConfig cfg = ModConfig.get();
        ClientPlayerEntity p = mc.player;
        if (!cfg.minimiEnabled || p == null || mc.world == null) {
            if (entity != null) remove();
            return;
        }
        if (entity != null && (entityWorld != mc.world || entity.isRemoved())) {
            entity = null;
            entityWorld = null;
        }
        if (mismatch && entity != null && rebuilds < 25) {
            mismatch = false;
            rebuilds++;
            remove();
        }
        if (entity == null) create(mc);

        double scale = cfg.minimiScale / 100.0;
        if (scale != lastScale) {
            EntityAttributeInstance a = entity.getAttributeInstance(EntityAttributes.SCALE);
            if (a != null) a.setBaseValue(scale);
            lastScale = scale;
        }

        // Ziel: rechts neben und etwas hinter dem Spieler, sanft nachfolgen
        double yr = Math.toRadians(p.getYaw());
        double gx = p.getX() + (-Math.cos(yr)) * 1.0 + Math.sin(yr) * 0.8;
        double gz = p.getZ() + (-Math.sin(yr)) * 1.0 + (-Math.cos(yr)) * 0.8;
        double gy = p.getY();

        double cx = entity.getX(), cy = entity.getY(), cz = entity.getZ();
        double nx, ny, nz;
        if (Math.abs(gx - cx) + Math.abs(gz - cz) + Math.abs(gy - cy) > 16) {
            nx = gx; ny = gy; nz = gz;
        } else {
            nx = cx + (gx - cx) * 0.25;
            ny = cy + (gy - cy) * 0.35;
            nz = cz + (gz - cz) * 0.25;
        }
        double dx = nx - cx, dz = nz - cz;
        float target = Math.sqrt(dx * dx + dz * dz) > 0.03
                ? (float) (Math.atan2(-dx, dz) * 180.0 / Math.PI)
                : p.getYaw();
        float yaw = MathHelper.lerpAngleDegrees(0.3f, entity.getYaw(), target);
        entity.setGoal(nx, ny, nz, yaw);
    }
}
