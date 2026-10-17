package de.mapartassist;

import net.minecraft.world.World;

import java.lang.reflect.Method;
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
}
