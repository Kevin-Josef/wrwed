==== 1.22.0 ====
SPIELER-ALARM (Menü: Spieler > Spieler-Alarm) - eigene Kategorie, getrennt vom Spawner-Schutz
- Nähert sich ein anderer Spieler innerhalb des Radius (10-100 Blöcke), sendet die Mod automatisch
  /msg <Empfänger> <Name des Spielers>. Empfänger = Feld "Empfänger" (z. B. dein Hauptaccount).
- Feld "Nachricht": Standard {player} (= nur der Name). Beispiel: "Achtung: {player} ist bei mir".
- Jeder Spieler wird nur einmal gemeldet, solange er im Radius bleibt. Verlässt er den Radius und kommt
  nach der eingestellten "Pause pro Spieler" zurück, wird er erneut gemeldet.
- Schalter "Spieler-Alarm aktiv" zum An-/Ausschalten. Minimi, Zuschauer und der Empfänger selbst werden ignoriert.
- Server mit /w oder /tell statt /msg: in config/mapartassist.json "alertCommand" ändern (ohne Slash).

MSG-VERLAUF (rechte Hälfte im selben Fenster)
- Zeigt alle privaten Nachrichten, die du bekommen hast (grün), und die automatisch gesendeten (gelb, mit Pfeil).
- Neueste oben, scrollbar, "Verlauf leeren". Wird gespeichert (config/mapartassist/msg-history.json, max. 200).
- Erkannt werden Vanilla ("X whispers to you: ...") und gängige Plugin-Formate wie "[X -> me] ...", "X » Du: ...",
  "Von X: ...". Wenn dein Server ein anderes Format nutzt und eine Nachricht fehlt: Beispielzeile schicken.

ITEM-ANZEIGE (Menü: Spieler > Item-Anzeige)
- Links: alle Items mit Suche, Klick = an/aus. Rechts: Schalter und Position.
- "Eigene Anzahl (HUD)": Symbol + Zahl aus deinem Inventar (z. B. Totem + 6, Dirt + 300). Position über zwei
  Regler (Prozent der Bildschirmgröße).
- "Über Spielern anzeigen": Symbol + Zahl über dem Namen anderer Spieler. WICHTIG: Der Server sendet das
  Inventar anderer Spieler nicht an den Client. Gezählt wird nur, was sichtbar ist: Haupthand, Offhand, Rüstung.
  Ein Spieler mit 6 Totems im Rucksack zeigt also keine 6, nur z. B. 1 Totem in der Hand. Nur Ego-Ansicht.

SONSTIGES
- Startseite: neue Gruppe "Spieler", das Fenster ist dafür etwas höher.
- Nicht gebaut/getestet (kein Gradle hier). Bitte bauen und Fehlermeldungen schicken.

SPIELER-ESP (Menü: Spieler > Spieler-ESP)
- Umrissbox um andere Spieler durch Wände, optional Linien (Tracer) und Name + Entfernung (unter den Füßen).
- Reichweite 16-256 Blöcke (mehr sendet der Server dem Client meist nicht). Nur Ego-Ansicht, wie die Block-ESP.

VERFOLGER-KAMERA (Menü: Extras > Freecam, rechte Karte)
- Die Kamera folgt dir hinter/über dir (Abstand 2-30, Höhe 0-20), du steuerst und baust normal weiter ab.
- Blickrichtung bleibt beim Spieler. Schaltet sich gegenseitig mit der normalen Freecam aus.
