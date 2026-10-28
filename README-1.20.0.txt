==== 1.20.0 ====
NEU: Crystal-Limit (Extras > Crystal-Limit)
- An/aus-Schalter und Regler "Zeitfenster" (1-5 s).
- Auf einem einzelnen Obsidian (auch Bedrock) werden hoechstens 2 End-Crystals pro Zeitfenster platziert.
  Ablauf: platzieren, Pop, platzieren, Pop - der dritte Rechtsklick wird verworfen, bis das Zeitfenster
  fuer das aelteste Crystal abgelaufen ist (gleitendes Fenster). Jeder Obsidian zaehlt einzeln.
- Greift fuer normale Rechtsklicks und fuer Auto-Platzieren (Mixin auf ClientPlayerInteractionManager.interactBlock).
- In der Actionbar steht "Crystal-Limit: noch X.X s", solange blockiert wird.
- Klicks, bei denen ohnehin nichts platziert wuerde (Platz belegt, schon ein Crystal da), zaehlen nicht mit.

NEU: Startmenue
- Die Kacheln sind weg. Stattdessen fuenf Kategorie-Kreise: Verkauf, Litematica, Automatik, Kosmetik, Extras.
- Klick auf einen Kreis: die Symbole klappen animiert rundherum auf (ein Ring um den Kreis). Nochmal klicken schliesst.
  Nur eine Kategorie ist gleichzeitig offen. Hover auf ein Symbol zeigt Name und Status, Klick oeffnet die Funktion.
- Gruener Punkt = etwas in der Kategorie bzw. am Symbol ist aktiv.
- Die Animation folgt dem Schalter "Uebergaenge" im Design-Menue (aus = sofort auf/zu).

HINWEIS
- Nicht gebaut/getestet (kein Gradle in dieser Umgebung). Bitte bauen und Fehlermeldungen schicken.
