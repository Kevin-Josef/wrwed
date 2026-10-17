MapArt Assist 1.0.1 – test source
=================================

This is source code, not a compiled JAR.

Main change:
- Removes the wide 3x3x3 search corridor.
- Only considers the adjacent schematic block for Minecraft's real crosshair block hit.
- Reuses the real vanilla BlockHitResult instead of inventing a support-face hit.
- Keeps a player-hitbox guard and inventory swap cooldown.

Build:
- Requires Java 21 and Gradle/Fabric Loom as declared in build.gradle.
- Run `gradle build` from the MapArtAssist folder.
- The output JAR should be under `build/libs/`.

Important:
This version has not been compiled or tested in Minecraft in this environment.
Because it uses the real crosshair target, you must aim at a supporting block face
next to the schematic position you want to fill. This intentionally favors reliable
server-valid placement over auto-detecting arbitrary nearby blocks.


Experimental smooth-placement patch (1.0.2 source): minimum 3-tick placement interval, default delay 4 ticks, and swing animation only after an accepted interaction. This source has not been compiled or tested in Minecraft.
