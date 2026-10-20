package de.mapartassist;

import net.minecraft.world.World;

import net.minecraft.util.math.BlockPos;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.lang.reflect.Modifier;

/**
 * Liest die geladene Litematica-Schematic per Reflection aus.
 * So braucht die Mod Litematica nicht zum Bauen, nur zum Spielen.
 */
public final class LitematicaBridge {
    private static Method getter;
    private static Object instance;
    private static boolean failed = false;

    private LitematicaBridge() {}

    public static boolean isMissing() {
        return failed;
    }

    /** Liefert die Schematic-Welt oder null (keine Schematic geladen / Litematica fehlt). */
    public static World getSchematicWorld() {
        if (failed) return null;
        try {
            if (getter == null) {
                Class<?> c = Class.forName("fi.dy.masa.litematica.world.SchematicWorldHandler");
                getter = c.getMethod("getSchematicWorld");
                if (!Modifier.isStatic(getter.getModifiers())) {
                    instance = c.getMethod("getInstance").invoke(null);
                }
            }
            Object o = getter.invoke(instance);
            return o instanceof World w ? w : null;
        } catch (Throwable t) {
            failed = true;
            return null;
        }
    }

    // ------------------------------------------------------------------ Platzierungs-Bereiche

    private static Object call(Object o, String name) throws Exception {
        Method m = o.getClass().getMethod(name);
        m.setAccessible(true);
        return m.invoke(o);
    }

    private static Object tryCall(Object o, String... names) {
        for (String n : names) {
            try {
                return call(o, n);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Number num(Object o, String field) {
        try {
            Method m = o.getClass().getMethod("get" + Character.toUpperCase(field.charAt(0)) + field.substring(1));
            m.setAccessible(true);
            Object v = m.invoke(o);
            if (v instanceof Number n) return n;
        } catch (Throwable ignored) {
        }
        try {
            Field f = o.getClass().getField(field);
            f.setAccessible(true);
            Object v = f.get(o);
            if (v instanceof Number n) return n;
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** Liest {minX,minY,minZ,maxX,maxY,maxZ} (inklusive) aus einer Litematica-Box oder einer Minecraft-Box. */
    private static int[] readBox(Object box) {
        if (box == null) return null;
        String[] k = {"minX", "minY", "minZ", "maxX", "maxY", "maxZ"};
        Number[] v = new Number[6];
        boolean all = true;
        for (int i = 0; i < 6; i++) {
            v[i] = num(box, k[i]);
            if (v[i] == null) all = false;
        }
        if (all) {
            int[] r = new int[6];
            for (int i = 0; i < 6; i++) {
                boolean dbl = v[i] instanceof Double || v[i] instanceof Float;
                r[i] = dbl ? (i < 3 ? (int) Math.floor(v[i].doubleValue()) : (int) Math.ceil(v[i].doubleValue()) - 1)
                        : v[i].intValue();
            }
            return r;
        }
        Object p1 = tryCall(box, "getPos1"), p2 = tryCall(box, "getPos2");
        if (p1 instanceof BlockPos a && p2 instanceof BlockPos b) {
            return new int[]{Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                    Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ())};
        }
        return null;
    }

    /** Bereiche (Bounding Boxes) aller aktiven Schematic-Platzierungen oder null, wenn nicht lesbar. */
    public static List<int[]> getPlacementBoxes() {
        try {
            Class<?> dm = Class.forName("fi.dy.masa.litematica.data.DataManager");
            Object mgr = dm.getMethod("getSchematicPlacementManager").invoke(null);
            Object all = call(mgr, "getAllSchematicsPlacements");
            if (!(all instanceof Collection<?> col)) return null;
            List<int[]> out = new ArrayList<>();
            for (Object pl : col) {
                Object en = tryCall(pl, "isEnabled");
                if (en instanceof Boolean b && !b) continue;
                int[] box = readBox(tryCall(pl, "getEclosingBox", "getEnclosingBox"));
                if (box != null) out.add(box);
            }
            return out;
        } catch (Throwable t) {
            return null;
        }
    }
}
