MapArtAssist 1.12.1 - Chat-Koordinaten

Neu:
- Bei eingehenden signierten Spieler-Chatnachrichten wird eine zusätzliche Zeile mit X/Y/Z ergänzt, sofern der Absender in deiner lokal geladenen Welt gefunden wird.
- Wenn der Absender nicht lokal geladen ist, wird „Position unbekannt“ angezeigt. Die Client-Mod kann keine echten Koordinaten entfernter Spieler erraten; dafür wären Server-Unterstützung oder ein entsprechendes Plugin erforderlich.

Hinweis:
- Diese ZIP enthält die geänderten Projektquellen. Ein Build wurde in dieser Umgebung nicht ausgeführt, da Gradle nicht installiert/verfügbar war. Daher bitte erst in einer Testinstallation prüfen.
- Die Chat-Ergänzung reagiert auf Fabric ClientReceiveMessageEvents.CHAT.
