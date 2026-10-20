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
