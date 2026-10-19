MapArtAssist – Auction Filter Auto Fix

Änderung:
- Wenn beim ersten Scan kein Angebot für die exakt konfigurierte Menge gefunden wird, klickt der Macro einmal per Rechtsklick auf den Stack-Filter der HugoSMP-Auktionsoberfläche und scannt erneut.
- Der Macro verwendet weiterhin nur Angebote mit exakt der Zielmenge; er rechnet keine fremde Stackgröße in einen geschätzten Preis um.
- Die Zielmenge bleibt auf die konfigurierte Menge, vorhandene Item-Anzahl und das Stacklimit begrenzt.

Wichtiger Testhinweis:
Die Zuordnung des Filter-Buttons zu Container-Slot 40 wurde aus dem Screenshot/GUI-Layout abgeleitet und muss im Spiel bestätigt werden. Die ZIP wurde hier nicht kompiliert oder auf HugoSMP getestet. Falls der Button-Slot in dieser Serveroberfläche anders liegt, kann der Filter-Klick einen anderen Slot treffen; im Zweifel Makro zuerst mit Testlauf verwenden.
