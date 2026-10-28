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

==== 1.15.4 ====
Verkaufs-Makro bricht nicht mehr ab: Oeffnet sich /sell nicht, schliesst sich das Menue oder nimmt es gerade nichts an,
wartet das Makro kurz (0,5 s, bei wiederholten Fehlern bis 5 s) und versucht es von selbst neu. Es endet nur, wenn
nichts mehr aus der Liste im Inventar ist, bei Stopp, oder wenn das Menue 12 Mal hintereinander gar nichts annimmt.

==== 1.15.5 ====
Verkaufs-Makro: "Dauerbetrieb (immer)" ersetzt Automatik + Intervall. Jeder Tick wird geprueft; sobald ein Item aus
der Liste im Inventar liegt, wird sofort /sell geoeffnet, alles hineingeschoben und geschlossen. Keine Wartezeiten
mehr (Fehlversuche werden nach 2 Ticks erneut probiert). Aus: Schalter oder "Stopp".

==== 1.16.0 ====
Map Art: "Nur richtige Rotation" (Menue Litematica > Map Art, Standard AN). Bloecke mit Ausrichtung (facing, axis,
rotation, half, face, orientation, hinge) werden nur gesetzt, wenn sie mit deinem aktuellen Blick und Klick genau
in der Rotation der Schematic landen wuerden. Sonst kommt der Hinweis "Falsche Ausrichtung". Bloecke ohne
Ausrichtung sind nicht betroffen.

==== 1.17.0 ====
- Chat-Koordinaten komplett entfernt (Code, Schalter und Config).
- /ah sell Auto-Preis: Regler "Preis senken ab X Angeboten" (Standard 2). Dein zuletzt gesetzter Auto-Preis bleibt,
  solange nur weniger als X Angebote zum guenstigsten (niedrigeren) Preis da sind. Erst ab X Angeboten wird auf diesen
  Preis gesenkt. Der letzte Preis wird pro Item gemerkt. Vergessen: /ahmacro reset. Manuelle Preise sind nicht betroffen.
- AFK-Waechter (Extras > AFK-Waechter): Beim Einschalten wird dein AFK-Platz gemerkt. Bist du spaeter weiter als X Bloecke
  davon weg (oder in einer anderen Welt), z. B. am Spawn, sendet die Mod /afk alle Y Sekunden, bis du wieder am Platz bist.
  Wird nur von Hand ein- und ausgeschaltet.
- Das Design-Menue ist weiter ueber das Symbol oben rechts erreichbar (die Kachel wurde durch AFK-Waechter ersetzt).

==== 1.17.2 ====
- Admin-Panel samt Passwort komplett entfernt. Die Freecam ist jetzt eine normale Kachel (Extras > Freecam), Taste F7 bleibt.
- Cape Creator: erreichbar ueber den Knopf "Creator" auf der Capes-Seite (die Kachel entfiel, damit Platz fuer Freecam ist).
- src/main/java/de/mapartassist/chat/ChatCoordinates.java ist nur noch eine leere Klasse, damit der GitHub-Upload die alte
  Version ueberschreibt. Du kannst die Datei in GitHub auch loeschen.

==== 1.17.3 ====
AFK-Waechter ohne festen Platz: Es gibt mehrere AFK-Areas, deshalb merkt die Mod keinen Ort mehr. Beim Einschalten gilt
"du bist in einer AFK-Area". Ein grosser Positionssprung oder eine neue Welt (Spawn) loest /afk-Senden aus, und der
naechste Sprung (= Ankunft in einer AFK-Area) beendet es. Nach 10 Versuchen ohne neuen Sprung nimmt die Mod an,
dass du schon in der Area stehst, und hoert auf. Knopf "Jetzt /afk senden", falls du schon am Spawn einschaltest.

==== 1.18.0 ====
- Auktions-Makro: Regler "Tempo" (1 sicher, 2 schnell = Standard, 3 sehr schnell). Die Wartezeiten zwischen den Schritten
  (Menue laden, schliessen, Slot-Sync, Pause nach /ah sell) sind je nach Stufe deutlich kuerzer. Stufe 3 kann zu Kicks durch den
  Spamschutz fuehren.
- Spawner-Schutz: "Nur an meiner Base" + "Base hier speichern". Am Spawn (ausserhalb des Base-Radius, Standard 40 Bloecke) ruht
  der Schutz und loest nicht aus. An der Base ist er von selbst wieder scharf. Im Base-Modus schaltet er sich nach dem Ausloesen
  nicht mehr aus (Rejoin-Schutz). Nach jedem Beitritt wartet er 5 s, bevor er scharf wird.

==== 1.19.0 ====
- Auktions-Makro: Zwischen /ah-Befehlen gilt ein Mindestabstand (Tempo 2: 1,0 s, Tempo 3: 0,6 s, Tempo 1: wie frueher). Bei
  Fehlschlaegen (Menge unveraendert) wird der Abstand automatisch groesser. Server-Meldungen werden im Chat mit angezeigt.
  Das war vermutlich der Grund, warum bei Tempo > 1 nichts verkauft wurde: Der Server verwirft zu schnelle Befehle.
- Minimi (Kosmetik > Minimi): kleiner Begleiter mit eigenem Skin. PNG-Skin (64x64 oder altes 64x32) ins Fenster ziehen; die Mod
  erkennt schmale Arme (Alex) automatisch. Nur bei dir sichtbar. Groesse einstellbar.
- Freecam: Symbol oben rechts neben Design (die Kachel entfiel, weil die Startseite einen Platz fuer Minimi brauchte).

==== 1.19.1 ====
- Auktions-Makro blaettert jetzt durch die Seiten des Auktionsmenues (Knopf "Naechste Seite"), bis ein voller Stack des
  gesuchten Items gefunden ist (max. 40 Seiten). Wechselt der Inhalt nach dem Klick nicht mehr, gilt das als letzte Seite.
- Preis-Zwischenspeicher: Ein frisch gesuchter Preis wird 20 s fuer dieselbe Sorte wiederverwendet (priceCacheSec in der Config,
  0 = immer neu suchen). Das spart bei wiederholten Verkaeufen den Such-Befehl: 1 statt 2 Befehle pro Verkauf.

==== 1.19.2 ====
Auktions-Makro Seiten-Blaettern: Nach einem Seitenwechsel wartet die Mod laenger (Server verschluckt sonst schnelle Klicks)
und klickt "Naechste Seite" bis zu 3x erneut, wenn sich der Inhalt nicht aendert, bevor sie von "letzter Seite" ausgeht.
Bricht die Suche nach mehreren Seiten ab, steht der Menue-Inhalt in config/mapartassist/ah-dump.txt.
