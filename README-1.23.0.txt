==== 1.23.0 ====
ESP: GLATTE LINIEN (Block-, Spieler- und Entity-ESP)
- Linien werden nicht mehr als Pixeltreppen in GUI-Pixeln gezeichnet, sondern als weich auslaufende Streifen
  in voller Bildschirmaufloesung (unabhaengig von der GUI-Skalierung). Ecken, Tracer und Punkte sind sauber.
- Dicke ändern: esp/EspDraw.java, Konstante HALF (6 = Standard, 4 = dünner, 9 = dicker).

ESP BLEIBT ZENTRIERT BEI ZOOM UND FREE-LOOK
- Blickrichtung und Position kommen jetzt von der tatsächlich gerenderten Kamera (nicht mehr vom Spieler-Entity).
  Dadurch passt die ESP auch bei Free-Look-Mods, F5 und Freecam.
- Das echte Render-FOV wird per kleinem Mixin (GameRendererMixin) mitgelesen. Dadurch passt die ESP auch mit
  Zoomify / Ok Zoomer / Sprint-FOV. Findet der Mixin die Methode nicht, läuft die Mod trotzdem (altes FOV).
- Nicht gebaut/getestet (kein Gradle hier). Bitte bauen und Fehlermeldungen schicken.
