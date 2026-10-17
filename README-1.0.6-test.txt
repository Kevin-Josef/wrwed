MapArt Assist 1.0.6 — Bewegungspause-Test (Quellcode)

Änderung:
- Neue Platzierungen werden zurückgestellt, solange die horizontale Spielergeschwindigkeit über 0,075 Blöcken/Tick liegt.
- Bereits angenommene Platzierungen werden weiterhin auf Server-Bestätigung geprüft.
- Ziel: prüfen, ob die kurzen Bewegungsruckler durch Platzierungen während des Laufens ausgelöst werden.

Wichtig:
- Dies ist ein Test-Workaround, keine bestätigte Lösung für HugoSMP.
- Beim normalen Laufen kann die automatische Platzierung pausieren und erst wieder einsetzen, wenn du langsamer wirst/anhältst.
- Das Archiv enthält Quellcode, keine fertige JAR. In dieser Umgebung konnte ich den Minecraft/Fabric-Build nicht ausführen oder im Spiel testen.
- Baue es mit Java 21 und Gradle/Fabric Loom passend zu den Versionen in gradle.properties.
