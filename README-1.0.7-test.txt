MapArt Assist 1.0.7 — Bewegung wieder freigeben (Quellcode)

Änderungen gegenüber 1.0.6:
- Entfernt die Geschwindigkeits-Sperre, die Auto-Platzieren beim Laufen ausgebremst hat.
- Entfernt die spekulative Sperrzone vor dem Spieler, die gültige Platzierungen blockieren konnte.
- Behält die Prüfung der Serverbestätigung für eine Platzierung bei, damit fehlgeschlagene Versuche nicht sofort gespammt werden.
- Verhindert weiterhin Platzierungen direkt in der aktuellen Spieler-Kollisionsbox.

Wichtige Einschränkung:
Dies ist eine gezielte Rücknahme der beiden problematischen Bewegungs-Workarounds, aber keine im Spiel bestätigte Lösung für die Ruckler auf HugoSMP. Hier konnte kein Fabric-Build und kein Multiplayer-Test ausgeführt werden. Das Archiv enthält Quellcode, keine fertige JAR.

Build-Voraussetzungen laut Projekt: Java 21, Minecraft 1.21.11, Yarn 1.21.11+build.4, Fabric Loader 0.18.1, Fabric API 0.141.1+1.21.11 und Fabric Loom 1.14-SNAPSHOT.
