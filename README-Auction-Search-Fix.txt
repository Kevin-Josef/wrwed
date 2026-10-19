MapArtAssist - Auction Search Fix

Aenderung in AuctionMacro.java:
- Entfernt den blinden Rechtsklick auf einen fest angenommenen Filter-Slot (Slot 40).
  Der Slot kann je nach Menue-/Serverlayout eine andere Funktion haben.
- Die Mod sucht weiterhin nach Angeboten mit exakt der konfigurierten Menge und verwendet
  nur deren tatsaechlichen Gesamtpreis.
- Wenn kein passendes Angebot sichtbar ist, wird nicht automatisch ein GUI-Element
  angeklickt, sondern weiter geprueft und danach sauber uebersprungen.

Hinweis:
Quellprojekt-ZIP, keine fertige JAR. Nicht in Minecraft/auf HugoSMP getestet.
