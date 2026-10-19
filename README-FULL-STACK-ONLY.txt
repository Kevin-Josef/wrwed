Full Stack Only

- Die Mengensteuerung wurde aus dem Makro-Menü entfernt.
- Das Makro verwendet immer item.getMaxCount(): bei TNT normalerweise 64.
- Es wird nur verkauft, wenn mindestens ein voller Stack in einem einzelnen Inventar-Slot liegt.
- Die Preissuche akzeptiert ausschließlich ein passendes Auktionsangebot mit exakt dieser Stackgröße.
- Nach dem Preislesen wird /ah sell <Preis> <Stacklimit> gesendet.

Nicht kompiliert oder im Spiel getestet. Wenn die Mod weiterhin nur wiederholt sucht, benötige ich ah-dump.txt aus config/mapartassist (im Auktionsmenü F8 drücken), um zu prüfen, ob der Preis aus der Lore erkannt wird.
