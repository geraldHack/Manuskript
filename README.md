# Manuskript - DOCX Verarbeitung

Eine JavaFX-Anwendung zur Verarbeitung und automatischen Nachbearbeitung von DOCX-Dateien zu einem zusammenhängenden Textdokument.

## Funktionen

- **Verzeichnis-Auswahl**: Wählen Sie ein Verzeichnis mit DOCX-Dateien aus
- **Datei-Übersicht**: Tabellarische Darstellung aller gefundenen DOCX-Dateien
- **Filterung**: Durchsuchen Sie die Dateien nach Namen
- **Regex-Filterung**: Erweiterte Filterung mit regulären Ausdrücken (z.B. `*[0-9][0-9]*`)
- **Recent-Liste**: Speichert die letzten 10 verwendeten Regex-Patterns als editierbares Dropdown
- **Automatische Regex-Sortierung**: Gefilterte Ergebnisse werden automatisch nach Zahlen sortiert
- **Sortierung**: Sortieren Sie nach Dateiname, Größe oder Änderungsdatum
- **Zwei-Tabellen-Ansicht**: Verfügbare Dateien links, ausgewählte Dateien rechts
- **Elegante Filterung**: Gefilterte Dateien verschwinden automatisch aus der linken Tabelle
- **Drag & Drop**: Ziehen Sie Dateien zwischen den Tabellen
- **Interne Umsortierung**: Alt+↑↓ für präzise Kontrolle
- **Externe Verschiebung**: Drag & Drop mit Strg/Shift-Taste von rechts nach links
- **Intuitive Auswahl**: Einfaches Hinzufügen/Entfernen von Dateien
- **Mehrfachauswahl**: Wählen Sie einzelne oder alle Dateien zur Verarbeitung aus
- **Verzeichnis-Memory**: Das letzte verwendete Verzeichnis wird automatisch gespeichert
- **Regex-Memory**: Die letzten 10 verwendeten Regex-Patterns werden automatisch gespeichert
- **Export mit Dateinamen als Titel**: Beim Export (Markdown, HTML, TXT) wird immer der Dateiname (ohne Endung) als Titel verwendet – keine automatische Kapitel-Erkennung mehr
- **Text-Extraktion**: Konvertiert DOCX-Dateien in lesbaren Text
- **Automatische Nachbearbeitung mit Makros**: Siehe unten
- **Eleganter Text-Editor**: Vollwertiger Editor mit Such- und Ersetzungsfunktionen, RTF- und DOCX-Export
- **Regex-Suche und -Ersetzung**: Erweiterte Textbearbeitung mit regulären Ausdrücken
- **Such-Historie**: Speichert die letzten 20 Such- und Ersetzungs-Patterns
- **Datei-Operationen**: Öffnen, Speichern und Speichern als
- **Keyboard-Shortcuts**: Ctrl+F (Suchen), Ctrl+S (Speichern), Ctrl+O (Öffnen), Ctrl+N (Neu)
- **Fortschrittsanzeige**: Zeigt den Verarbeitungsfortschritt an

## Automatische Nachbearbeitung mit Makros

Die Anwendung bietet eine leistungsfähige Makro-Funktion zur automatischen Text-Bereinigung und Nachbearbeitung. 

- **Makro "Text-Bereinigung"**: Enthält 12 typische Schritte zur professionellen Nachbearbeitung, z.B.:
  - Mehrfache Leerzeichen/Leerzeilen reduzieren
  - Gerade und französische Anführungszeichen ersetzen
  - Auslassungszeichen und Gedankenstriche korrigieren
  - Kommas und Sonderzeichen bereinigen
  - Regex-basierte Suchen/Ersetzungen (z.B. für Zitate, Satzzeichen, etc.)
- **Makro "Französische → Deutsche Anführungszeichen"**: Konvertiert `»text«` zu `„text"` und `›text‹` zu `‚text'`
- **Makro "Deutsche → Französische Anführungszeichen"**: Konvertiert `„text"` zu `»text«` und `‚text'` zu `›text‹`
- **Makro "Apostrophe korrigieren"**: Korrigiert verschiedene Apostrophe-Formen (`, `, ´, ') zu korrekten Apostrophen (')
- **Makros können als CSV exportiert werden**
- **Makros sind vollständig anpassbar**: Sie können eigene Schritte hinzufügen, entfernen oder bearbeiten
- **Makros können auf beliebige Texte angewendet werden**
- **Makro-Editor**: Übersichtliche Oberfläche zur Verwaltung und Bearbeitung der Makros

## Export: Immer Dateiname als Titel

Beim Export (Markdown, HTML, TXT) wird **immer der Dateiname (ohne Endung) als Titel** verwendet:
- **Markdown**: `# Dateiname`
- **HTML**: `<h1>Dateiname</h1>`
- **Plain Text**: `Dateiname` (mit Leerzeile danach)

Es gibt **keine automatische Kapitel- oder Überschriften-Erkennung** mehr. Der Titel ist immer eindeutig und nachvollziehbar.

## Voraussetzungen

- Java 17 oder höher
- Maven 3.6 oder höher

## Installation und Ausführung

1. **Projekt klonen oder herunterladen**
2. **Maven-Abhängigkeiten installieren**:
   ```bash
   mvn clean install
   ```
3. **Anwendung starten**:
   ```bash
   mvn javafx:run
   ```

## Verwendung

1. **Verzeichnis auswählen**: 
   - Klicken Sie auf "Verzeichnis auswählen" und wählen Sie ein Verzeichnis mit DOCX-Dateien
   - Das letzte verwendete Verzeichnis wird automatisch vorgeschlagen
   - Bei Programmstart wird das letzte Verzeichnis automatisch geladen
2. **Dateien filtern**: 
   - Verwenden Sie das Suchfeld für einfache Textsuche
   - Aktivieren Sie "Regex aktiv" und verwenden Sie reguläre Ausdrücke für erweiterte Filterung
   - **Recent-Liste**: Wählen Sie aus den letzten 10 verwendeten Regex-Patterns oder geben Sie neue ein
   - Wählen Sie "Aufsteigend" oder "Absteigend" für die automatische Sortierung
3. **Dateien auswählen**: 
   - **Drag & Drop**: Ziehen Sie Dateien von links nach rechts (oder umgekehrt)
   - **Pfeil-Buttons**: Verwenden Sie → und ← Buttons zwischen den Tabellen
   - **Mehrfachauswahl**: Wählen Sie mehrere Dateien gleichzeitig aus
4. **Verarbeitung**: Wählen Sie aus der rechten Tabelle die zu verarbeitenden Dateien aus
5. **Verarbeitung starten**: Klicken Sie auf "Ausgewählte verarbeiten" oder "Alle verarbeiten"
6. **Ergebnis anzeigen**: Das verarbeitete Ergebnis wird im eleganten Text-Editor angezeigt
7. **Text bearbeiten**: 
   - **Suchen/Ersetzen**: Ctrl+F oder Button "Suchen/Ersetzen"
   - **Regex-Patterns**: Aktivieren Sie "Regex" für erweiterte Suche
   - **Such-Historie**: Wählen Sie aus den letzten 20 Such-Patterns
   - **Datei speichern**: Ctrl+S oder Button "Speichern"
   - **RTF-Export**: Button "Als RTF exportieren" für Markdown-Dokumente
   - **DOCX-Export**: Button "Als DOCX exportieren" für Markdown-Dokumente mit Formatierung

## Regex-Filterung

Die Anwendung unterstützt erweiterte Filterung mit regulären Ausdrücken:

**Beispiele:**
- `*[0-9][0-9]*` - Dateien mit zwei aufeinanderfolgenden Ziffern
- `.*kapitel.*` - Dateien mit "kapitel" im Namen (case-insensitive)
- `^[A-Z].*` - Dateien, die mit einem Großbuchstaben beginnen
- `.*\.docx$` - Alle DOCX-Dateien (Standard)
- `[0-9]{2,3}` - Dateien mit 2-3 Ziffern

**Tipp:** Aktivieren Sie "Regex aktiv" und testen Sie Ihre Patterns im Suchfeld.

**Automatische Sortierung:** Wenn Sie ein Regex-Pattern mit Zahlen verwenden, werden die gefilterten Ergebnisse automatisch nach diesen Zahlen sortiert. Wählen Sie "Aufsteigend" oder "Absteigend" für die Sortierrichtung.

**Elegante Filterung:**
- **Automatische Ausblendung**: Gefilterte Dateien verschwinden automatisch aus der linken Tabelle
- **Keine Duplikate**: Ausgewählte Dateien werden in der linken Tabelle nicht mehr angezeigt
- **Saubere Trennung**: Klare Unterscheidung zwischen verfügbaren und ausgewählten Dateien

**Zwei-Tabellen-Ansicht:** 
- **Linke Tabelle**: Zeigt verfügbare DOCX-Dateien (ohne ausgewählte)
- **Rechte Tabelle**: Zeigt die für die Verarbeitung ausgewählten Dateien
- **Drag & Drop**: Ziehen Sie Dateien zwischen den Tabellen für einfache Auswahl
- **Interne Umsortierung**: Alt+↑↓ für präzise Kontrolle
- **Externe Verschiebung**: Drag & Drop mit Strg/Shift-Taste von rechts nach links
- **Schnelle Verschiebung**: Doppelklick verschiebt Datei nach oben
- **Pfeil-Buttons**: Alternative zu Drag & Drop für präzise Kontrolle

## Projektstruktur

```
src/
├── main/
│   ├── java/com/manuskript/
│   │   ├── Main.java              # Hauptklasse
│   │   ├── MainController.java    # UI-Controller
│   │   ├── DocxFile.java          # Datenmodell für DOCX-Dateien
│   │   └── DocxProcessor.java     # DOCX-Verarbeitung
│   ├── resources/
│   │   ├── fxml/
│   │   │   └── main.fxml          # UI-Layout
│   │   └── css/
│   │       └── styles.css         # Styling
└── test/                          # Unit-Tests (optional)
```

## Technologien

- **JavaFX**: Benutzeroberfläche
- **Apache POI**: DOCX-Datei-Verarbeitung
- **Maven**: Build-Management
- **SLF4J/Logback**: Logging

## Lizenz

Dieses Projekt steht unter der MIT-Lizenz.
