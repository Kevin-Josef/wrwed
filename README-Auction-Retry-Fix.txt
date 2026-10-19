MapArtAssist – Auction retry fix (Quellprojekt)
================================================

Änderung:
- Wenn „Makro wiederholen“ aktiv ist und nach /ah sell noch Items im Inventar sind,
  wird das Item wieder in die Warteschlange gesetzt – auch wenn die Inventarmenge
  unverändert ist.
- Der nächste Durchlauf öffnet /ah <item> erneut, liest den Preis erneut und sendet
  anschließend wieder /ah sell <preis> [menge].
- Die Meldung „Menge unverändert, überspringe“ wird durch eine Meldung über den
  erneuten Versuch ersetzt.

Wichtig:
- Dies ist ein Quellprojekt-ZIP, keine fertig gebaute JAR.
- Bei unverändertem Inventar kann der Makro-Wiederholungsmodus erneut verkaufen
  versuchen und dabei wiederholt Befehle senden. Aktiviere ihn nur, wenn das auf
  deinem Server erwünscht ist.
- Nicht kompiliert oder im Spiel getestet: In dieser Umgebung stehen Java/Gradle
  nicht zur Verfügung.
