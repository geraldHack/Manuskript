# Mindmap-Plugin

Editierbare Plot-Mindmap im WebView (vis.js): Knoten ziehen, hinzufügen, bearbeiten, löschen.

- Speichert `data/mindmap.json`
- **KI Erstlesen** — baut Graph aus Welt-Editor + Kapiteln (Agenten-Einstellungen)
- **KI Aktualisieren** — ergänzt neue Figuren/Kapitel/Orte ohne Duplikate

```bash
cd tools/mindmap
mvn package
```

JAR: `plugin-catalog/mindmap.jar` — unter Setup → Plugins aktivieren.

**Hinweis:** vis.js ist im JAR gebündelt. KI liest alle Welt-Dateien + Kapitel-Anfänge aus der Buchauswahl (Budget ~10k Zeichen). Voller Kapiteltext wäre oft 200k+ — für Beziehungen reichen Outline, Figuren und Kapitelanfang; sonst wird es langsam ohne Mehrwert.
