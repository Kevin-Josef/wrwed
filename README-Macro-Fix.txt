MapArtAssist 1.9.2 – Macro-Fix (Quellprojekt)

Änderungen:
- Bei einer neuen Konfiguration ist der Testlauf standardmäßig ausgeschaltet.
- Wenn die konfigurierte Verkaufsmenge exakt der Menge des günstigsten Angebots entspricht,
  wird dessen angezeigter Gesamtpreis direkt verwendet. Bei abweichender Menge wird der
  Stückpreis weiterhin auf die gewünschte Menge umgerechnet.

Wichtig:
- Eine bereits vorhandene config/mapartassist.json behält ihre Einstellung macroDryRun.
  Wenn im Makro-Menü „Testlauf“ aktiviert ist, wird nichts verkauft; dort deaktivieren.
- Dies ist ein ZIP-Quellprojekt, keine installierbare .jar.
- In dieser Umgebung konnte kein Gradle-Build ausgeführt werden (Gradle ist nicht installiert).
  Daher ist die Änderung nicht kompiliert oder in Minecraft getestet.
