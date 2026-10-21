Kevin_Josefs Mod-Menü 1.14.0

MENÜ
- Startseite mit Unterteilungen: Makros (Auktion, HT1, Spawner, Recorder), Kosmetik (Capes, Creator),
  Litematica (Map Art, Fortschritt), Extras (Rechner, Design). Keine Seitenleiste.

ADMIN-PANEL (Passwort 676767, Schloss oben rechts)
- Chat-Koordinaten (wie bisher).
- NEU Freecam: Kamera fliegt frei, der Spieler bleibt stehen. Schalter im Panel oder Taste F7.
  W/A/S/D bewegen, Leertaste hoch, Shift runter, Strg schneller. Geschwindigkeit per Regler.

BEWEGUNGS-RECORDER (Makros > Recorder)
- "Aufnahme starten" (oder Num 1): normal spielen, z. B. einen 2x1-Gang graben. Num 1 beendet und speichert.
- Aufnahmen liegen in config/mapartassist/recordings. Auswählen, "Abspielen" (oder Num 2), "Stopp".
- Wiederholungen oder endlos. "Blickrichtung anpassen": Die Aufnahme läuft relativ zu deiner aktuellen
  Blickrichtung ab, du kannst also in jede Richtung neu starten.
- Aufgezeichnet werden Tasten (laufen, springen, schleichen, sprinten, schlagen, benutzen) und Blick.
  Nicht aufgezeichnet: Hotbar-Wechsel. Die Wiedergabe stoppt bei Schaden oder wenn ein Menü aufgeht.

SCHEMATIC-FORTSCHRITT (Litematica > Fortschritt)
- Zeigt den Prozentstand der geladenen Schematic und pro Block-Art fertig/gesamt.
- Klick auf eine Block-Art = ausschließen. Wahlweise zählt sie dann als fertig oder fliegt aus der Rechnung
  ("Ausgeschlossene = fertig"). Optional setzt Auto-Platzieren ausgeschlossene Blöcke nicht mehr.
- "Prozent im Spiel zeigen" blendet den Stand oben links ein.
- Hinweis: Der Client kennt nur geladene Chunks. Was weit weg liegt, zählt als offen.
- Der Zugriff auf Litematica läuft über Reflection. Falls deine Litematica-Version die Platzierung
  anders ausgibt, steht im Fortschritts-Fenster eine Meldung statt Prozent.

HINWEIS
- Nicht gebaut/getestet (kein Gradle hier). Bitte bauen und Fehlermeldungen schicken.
- Prüfe die Regeln deines Servers: Freecam und Wiederhol-Makros sind auf manchen Servern verboten.

==== 1.15.0 ====
VERKAUFS-MAKRO (/sell) - Makros > Verkaufs-Makro
- Filterliste wie beim Auktions-Makro (Item aus der Hand hinzufuegen, mit X entfernen).
- "Jetzt verkaufen" oder /sellmacro: oeffnet /sell, schiebt alle Items aus der Liste hinein
  (Shift-Klick) und schliesst das Menue. Beim Schliessen wird verkauft.
- "Automatisch verkaufen" mit Intervall (5-300 s). Laeuft nicht, solange ein Menue offen ist oder der
  Recorder aufnimmt/abspielt.

AUTO-REPARATUR - Makros > Auto-Reparatur
- Erkennt, wenn die Spitzhacke in deiner Hand fast kaputt ist (Schwelle einstellbar).
- Oeffnet /shop, klickt die Kategorie (Name einstellbar, Standard "Ausruestung"), kauft XP-Flaschen
  (1-4 Stacks), legt sie in die Zweithand und wirft sie nach unten. Mending repariert die Spitzhacke.
- Danach wird die Blickrichtung/der Slot wiederhergestellt und eine laufende Aufnahme optional fortgesetzt.
- Wird die Spitzhacke nicht repariert (kein Mending), schaltet sich die Auto-Reparatur aus.
- Die Shop-Schritte sind allgemein gehalten (Suche nach Namen). Klappt etwas nicht, steht der
  Menue-Inhalt in config/mapartassist/shop-dump.txt. Test: /repairmacro, Stopp: /repairmacro stop.

==== 1.15.1 ====
Auto-Reparatur passend zum HugoSMP-Shop: /shop -> Totem (Ausruestung) -> XP-Flasche -> im Kaufen-Fenster
"=64" (gruenes Glas rechts) -> gruenes Glas unten (Bestaetigen). Wird pro Stack wiederholt, bis genug
Flaschen da sind (1-4 Stacks einstellbar).

==== 1.15.2 ====
Freecam + Recorder: Eine laufende Wiedergabe bleibt aktiv, wenn man die Freecam einschaltet. Waehrend der
Spiel-Logik ist der echte Spieler die Kamera (Abbauen/Zielen klappt), gezeigt wird die Freecam-Ansicht.
Die Freecam startet jetzt 3 Bloecke hinter dem Spieler, damit man ihn sofort sieht.

==== 1.15.3 ====
Verkaufs-Makro: Alle passenden Stacks werden in einem einzigen Tick per Shift-Klick ins /sell-Menue
geschoben (kein Einzelklicken mehr). Mit "Wiederholen, bis leer" laeuft das Makro Durchgang um Durchgang,
solange noch Items aus der Liste im Inventar sind (Stopp: Button oder /sellmacro stop). Bricht ab, wenn
das Verkaufsmenue nichts mehr annimmt.
"Auto-Pruefung alle X s": Bei "Automatisch starten" schaut die Mod alle X Sekunden nach, ob Items aus der
Liste im Inventar sind, und startet dann von selbst einen Verkauf.
