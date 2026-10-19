MapArtAssist – Exact Amount Price Search

Änderung:
- Die Auktion wird nach dem günstigsten Angebot für exakt die eingestellte Zielmenge durchsucht.
- Angebote mit anderer Stackgröße werden nicht mehr auf die Zielmenge hoch- oder heruntergerechnet.
- Der Verkaufsbefehl verwendet den Gesamtpreis des exakt passenden Angebots und sendet `/ah sell <preis> <menge>`.
- Die Zielmenge wird auf 1..Stacklimit und den ersten verfügbaren Inventarstack begrenzt; damit werden z. B. bei Items mit Stacklimit 16 keine 32/64 angefordert.

Wichtig:
- Gibt es kein Angebot mit exakt passender Menge, wird kein automatisch berechneter Preis erfunden; die Suche läuft aus und meldet, dass kein passendes Angebot gefunden wurde.
- Das ist eine Quellprojekt-ZIP, keine fertige JAR. In dieser Umgebung konnte sie nicht kompiliert oder auf dem Server getestet werden.
