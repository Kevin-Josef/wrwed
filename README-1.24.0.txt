==== 1.24.0 ====
NEU: AUTO-RTP (Menue: Portal-Symbol oben rechts, oder Extras > Auto-RTP)
- Sendet /rtp, klickt im RTP-Menue die eingestellte Dimension (Overworld / Nether / End) an und wiederholt das,
  bis nach einem Teleport ein anderer Spieler in Reichweite des Spieler-ESP auftaucht (Reichweite: Tab Spieler-ESP).
- Dann stoppt es und meldet den Namen + Entfernung im Chat. Das Spieler-ESP muss dafuer nicht eingeschaltet sein.
- Bedienung: Menue-Schalter, Taste Numpad 3 (aenderbar), oder Befehl:
    /rtpmacro              startet mit der gespeicherten Dimension
    /rtpmacro overworld | nether | end   stellt die Dimension ein und startet
    /rtpmacro stop         stoppt
- Einstellungen: "Warten nach Teleport" (so lange wird nach Spielern gesucht, bevor wieder /rtp kommt) und
  "Pause nach Fehlversuch" (kein Menue wegen Cooldown, kein Teleport ...).
- Den Slot findet die Mod ueber den Item-Namen (Overworld/Nether/End), sonst ueber die Lage im Menue
  (3 Bloecke nebeneinander). Der Menue-Inhalt steht danach in config/mapartassist/rtp-dump.txt (Diagnose).
- Stoppt automatisch bei Tod, Verlassen des Servers oder 12 Fehlversuchen hintereinander.
- Nicht gebaut/getestet (kein Gradle hier). Bitte bauen und Fehlermeldungen schicken.

AUTO-RTP: UNSICHTBARKEIT BEIM FUND
- Sobald ein Spieler gefunden wird: kurzer Ping (immer) und Unsichtbarkeitstrank benutzen (Menue-Schalter, Standard an).
- Reihenfolge: Hotbar vor Inventar, Trank zum Trinken vor Wurftrank. Aus dem Inventar wird der Trank in den
  aktuellen Hotbar-Slot getauscht und danach zurueckgetauscht, der alte Slot wird wieder gewaehlt.
- Wurftrank wird nach unten auf die eigenen Fuesse geworfen (Blick wird danach zurueckgesetzt).
- Kein Trank dabei: nur Ping + Chatmeldung.

SPAWNER-SCHUTZ UEBERARBEITET
- Schleichen: Die Mod prueft jetzt, ob schon geschlichen wird (auch bei "Schleichen umschalten") und drueckt
  Shift nur, wenn es noetig ist. Vorher wurde im Umschalt-Modus jeden Tick neu umgeschaltet. Nach dem Abbau
  steht sie nur wieder auf, wenn sie selbst geschlichen ist.
- Zielen: Fuer jeden Spawner wird per Raycast ein Punkt mit freier Sicht gesucht (alle 6 Seiten, Gitter pro Seite).
  Verdeckte Spawner (z. B. diagonal im Kaefig) kommen dran, sobald die Sicht frei ist. Geht die Sicht verloren,
  wird zum naechsten Spawner gewechselt statt haengenzubleiben. Angegriffen wird nur, wenn das Fadenkreuz
  wirklich den Ziel-Spawner trifft.
- Tempo: Der Schleich-Schritt pro Spawner entfaellt. Nach dem letzten Abbau wird sofort verlassen, sobald alle
  Spawner im Inventar liegen (Zaehlung statt feste Wartezeit).
- Nicht gebaut/getestet. Bitte bauen und Fehlermeldungen schicken.

AUTO-RTP: SPIELER-ERKENNUNG
- Vorher wurde nur kurz direkt nach dem Teleport nach Spielern gesucht. Jetzt wird ab dem ersten Teleport in
  JEDER Phase (/rtp senden, Menue, Teleport, Warten) nach Spielern gesucht, mit denselben Regeln und demselben
  Radius wie das Spieler-ESP (Abstand Kamera -> Spieler, Spectator/Minimis ignoriert).
- Vor dem ersten Teleport wird nicht geprueft (am Start stehen oft schon andere herum).
- Beim Fund wird ein offenes RTP-Menue geschlossen.

ESP-FARBEN UND GLOW (neues Palette-Symbol oben rechts, Extras > ESP-Farben)
- Fuer Block-, Entity- und Spieler-ESP getrennt: eigene Umriss-Farbe, eigene Tracer-Farbe (sonst = Umriss-Farbe),
  Farbfeld + Hex + Voreinstellungen, "Standard" stellt die bisherigen Farben wieder her.
- Ohne "Eigene Farbe" bleiben die bisherigen Standardfarben (Bloecke/Entities je nach Typ, Spieler rot).
- Glow: an/aus und Breite 1-12 px, wirkt auf Umriss, Tracer und Punkte der jeweiligen ESP.
