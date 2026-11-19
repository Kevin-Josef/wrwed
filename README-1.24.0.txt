==== 1.24.0 ====
NEU: AUTO-RTP (Menue: Portal-Symbol oben rechts, oder Extras > Auto-RTP)
- Sendet /rtp, klickt im RTP-Menue die eingestellte Dimension (Overworld / Nether / End) an und wiederholt das,
  bis nach einem Teleport ein anderer Spieler in Reichweite des Spieler-ESP auftaucht (Reichweite: Tab Spieler-ESP).
- Dann stoppt es und meldet den Namen + Entfernung im Chat. Das Spieler-ESP muss dafuer nicht eingeschaltet sein.
- Bedienung: Menue-Schalter, Taste Numpad 3 (aenderbar), oder Befehl:
    /rtpmacro              startet mit der gespeicherten Dimension
    /rtpmacro overworld | nether | end   stellt die Dimension ein und startet
    /rtpmacro stop         stoppt
- Einstellungen: "Warten nach Teleport" (so lange wird nach Spielern gesucht, bevor wieder /rtp kommt) und
  "Pause nach Fehlversuch" (kein Menue wegen Cooldown, kein Teleport ...).
- Den Slot findet die Mod ueber den Item-Namen (Overworld/Nether/End), sonst ueber die Lage im Menue
  (3 Bloecke nebeneinander). Der Menue-Inhalt steht danach in config/mapartassist/rtp-dump.txt (Diagnose).
- Stoppt automatisch bei Tod, Verlassen des Servers oder 12 Fehlversuchen hintereinander.
- Nicht gebaut/getestet (kein Gradle hier). Bitte bauen und Fehlermeldungen schicken.
