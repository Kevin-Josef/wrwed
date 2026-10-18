MapArt Assist 1.1.5 — Klick-Abbruch beim Wegschauen (Quellcode)

Aenderungen:
- Der Platzier-Klick wird nicht mehr 2 Ticks verzoegert, sondern sofort ausgeloest (schnelleres Platzieren).
- Neuer Guard (START_CLIENT_TICK): direkt bevor Minecraft den Klick verarbeitet, wird geprueft, ob
  Fadenkreuz-Block/-Seite, gehaltener Block und Schematic-Wunschblock noch passen. Wenn nicht, wird der Klick verworfen.
- Nicht gebaut/getestet (kein Fabric-Build moeglich).
