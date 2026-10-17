MapArt Assist 1.0.6 – test source
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


1.0.3 experimental player-clearance patch: placement is skipped when the target is close to the player's feet/body, with a small expanded safety box intended to reduce collision corrections during carpet placement. Not compiled or tested in Minecraft.
\n\n1.0.4 experimental source patch: serializes placement attempts and waits up to 8 ticks for the world state to confirm each target block. If the server does not confirm, it pauses instead of immediately retrying. Not compiled or tested in Minecraft.\n\n\n1.0.5 experimental movement-buffer patch: avoids placing into the player's immediate projected horizontal movement corridor (about 0.85 blocks ahead) to reduce carpet step/correction jitter. May skip close targets while walking. Not compiled or tested in Minecraft.\n

Version 1.0.6 test change: auto-placement is paused while horizontal player speed exceeds 0.075 blocks/tick. This is a conservative workaround for multiplayer movement corrections, not a verified fix.
