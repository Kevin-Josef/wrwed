Version 1.10.1 (nicht kompiliert/getestet)
- Spawner-Schutz: Abbau haelt jetzt wirklich die Angriffstaste gedrueckt und zielt auf den Spawner
  (vorher wurde der Abbau jeden Tick von Minecraft abgebrochen = nur "Antippen").
  Ablauf: schleichen -> halten bis 1. Abbau fertig -> 6 Ticks warten -> halten bis 2. Abbau fertig;
  bei gestackten Spawnern bis zu 4 Durchgaenge.
- Vor dem Verlassen wartet die Mod, bis alle gedroppten Spawner eingesammelt sind (laeuft zu den Drops).
- /ah sell-Makro: Verkauf wird erkannt, auch wenn das Item sofort wieder aufgesammelt wird
  (Minimum der Menge waehrend des Wartens). Stopp erst nach 10 Fehlversuchen in Folge.
