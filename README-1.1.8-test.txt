MapArt Assist 1.1.6 — Echte 0-Tick-Geschwindigkeit (Quellcode)

Aenderungen:
- Der Platzier-Klick wird nicht mehr 2 Ticks verzoegert, sondern sofort ausgeloest (schnelleres Platzieren).
- Neuer Guard (START_CLIENT_TICK): direkt bevor Minecraft den Klick verarbeitet, wird geprueft, ob
  Fadenkreuz-Block/-Seite, gehaltener Block und Schematic-Wunschblock noch passen. Wenn nicht, wird der Klick verworfen.
- Nicht gebaut/getestet (kein Fabric-Build moeglich).

1.1.6: Delay-Regler korrigiert. Der Cooldown wurde doppelt heruntergezaehlt und hatte ein Minimum von 1.
Jetzt: Regler = Ticks zwischen zwei Platzierungen, 0 (und 1) = jeden Tick.

1.1.7: Platzieren pro Frame statt pro Tick (direkter interactBlock mit frisch geprueftem Fadenkreuz).
Delay-Regler 0 = jeder Frame. Fallback: in config/mapartassist.json "frameMode": false.

1.1.8: frameMode standardmaessig AUS (glatter Vanilla-Klick wie 1.1.6). Neu: maxPerTick (nur Frame-Modus).
