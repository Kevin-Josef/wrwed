MapArt Assist 1.0.8 — Vanilla use-input test (Quellcode)

Änderungen:
- Auto-Platzieren queued einen einzelnen Druck auf die gebundene Minecraft-Use-Taste.
- Minecrafts normale Eingabeverarbeitung soll dadurch den üblichen Rechtsklick-Pfad verwenden.
- Die Mod ruft interactBlock nicht mehr direkt aus dem Client-Tick auf.
- Die vorherige Bewegungssperre und die Vorwärts-Sperrzone bleiben entfernt.
- Die Client-Vorhersage wird nicht sofort als Serverbestätigung gewertet; vor dem nächsten Versuch werden mindestens vier Ticks abgewartet.

Wichtige Einschränkungen:
- Quellcode, keine fertige JAR.
- Hier nicht kompiliert oder im Spiel getestet.
- Diese Änderung muss gegen Yarn/Minecraft 1.21.11 gebaut werden. Insbesondere die API von KeyBinding.onKeyPressed/getBoundKey muss beim Build bestätigt werden.
- Das ist ein gezielter Test der Eingabemethode, keine garantierte Reparatur für HugoSMP.
