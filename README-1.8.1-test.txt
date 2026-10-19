MapArt Assist 1.2.0 - Neues Hauptmenue (Quellcode, ungetestet)

- Menue (Right-Shift): Reiter links (Map Art, Aussehen, Capes), Inhalt rechts.
- Map Art: alle bisherigen Einstellungen aus 1.1.8 (inkl. Frame-Modus + Max pro Tick).
- Aussehen: Farbfeld + Farbton-Leiste + Hex-Feld + Vorschau statt RGB-Regler.
- Capes: Platzhalter.
- Neue Features = neuer Eintrag im Tab-Enum in MenuScreen.java.

1.3.0: Capes (lokal, nur fuer dich sichtbar)
- Reiter "Capes": Bibliothek, Cape-PNG (64x32 oder 128x64) per Drag-and-drop ins Menue-Fenster ziehen.
- Elytra optional: Schalter umstellen, dann PNG ziehen. Ohne eigene Elytra nutzt sie das Cape.
- Dateien liegen in config/mapartassist/capes/ (NAME.png, NAME.elytra.png).
- Neu: Mixin (AbstractClientPlayerEntity#getSkin), mapartassist.mixins.json.

1.3.1: Altes 22x17-Cape-Format (auch vergroessert, z. B. 176x136) wird beim Import automatisch in 64x32-Layout umgewandelt.

1.3.2: Hat ein Cape keinen Elytra-Bereich (z. B. umgewandeltes 22x17), bleibt die normale Elytra sichtbar statt unsichtbar zu werden.

1.3.3: Umwandlung 22x17 -> 64x32 passiert jetzt beim LADEN jedes Capes (auch Dateien, die direkt im capes-Ordner liegen), nicht nur beim Import.

1.4.0: Cape-Creator (neuer Reiter). Bild (PNG/JPG) hineinziehen -> Cape im 64x32-Layout in Originalaufloesung, Optionen: Zuschnitt (zuschneiden/einpassen/strecken), Innenseite (Spiegelbild/Randfarbe/abgedunkelt). Elytra bleibt normal.

1.5.0: Neues Menue-Design (eigene animierte Bedienelemente: gleitender Reiter, Hover-Effekte, Schalter, Regler),
Mini-Vorschau jedes Capes in der Liste + drehende Vorschau-Karte (Aussen-/Innenseite) in Capes und Cape-Creator.

1.6.0: Cape-Vorschau jetzt mit echten Texturen (bis 160x256 pro Seite, kleine Capes pixelgenau), Navigation oben statt seitlich,
animiertes Sechseck-Muster im Hintergrund, gleitender Reiter-Indikator, Ueberblenden beim Reiterwechsel, Karten-Layout.

1.7.0: Menue-Titel 'Kevin_Josefs Mod-Menue' (config: menuTitle), neuer Reiter Makros (Verkaufsliste mit Items aus der Hand, Testlauf-Schalter). Der Verkaufs-Makro selbst folgt nach Kalibrierung auf HugoSMP.

1.7.1: Titel korrigiert: Kevin_Josefs Mod-Menue (mit f).

1.8.0: Auktions-Makro (/ahmacro, /ahmacro stop). Liest guenstigsten Stueckpreis aus dem /ah-Menue (Lore 'Preis: $240.000'), stellt mit /ah sell <preis> [menge] ein.
Pro Item Menge (Stack/1/4/8/16/32). Testlauf standardmaessig AN. F8 bei offenem Auktionsmenue schreibt config/mapartassist/ah-dump.txt.

1.8.1: Fix: Auktionsmenue wurde nicht erkannt (Titel ist ein Resourcepack-Bild) -> keine Titelpruefung mehr. Neu: fester Preis pro Item per /ahmacro preis <item> <betrag|auto> (z. B. 200k).
