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
