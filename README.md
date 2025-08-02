# Manuskript - DOCX Verarbeitung & Text-Editor

**Hauptzweck:** JavaFX-Anwendung zum Zusammenführen mehrerer DOCX-Dateien zu einem Manuskript mit integriertem Text-Editor und automatischer Nachbearbeitung. Gut geeignet für **Sudowrite** Projekt-Exporte

Eine moderne JavaFX-Anwendung zur Verarbeitung und automatischen Nachbearbeitung von DOCX-Dateien zu einem zusammenhängenden Textdokument mit professionellem Text-Editor.

## 🎯 Kernfunktionen

### 📁 Datei-Verwaltung
- **Verzeichnis-Auswahl:** Verzeichnis mit DOCX-Dateien laden
- **Intelligente Filterung:** Einfache Textsuche und Regex-Filterung
- **Zwei-Tabellen-Ansicht:** Verfügbare Dateien links, ausgewählte Dateien rechts
- **Drag & Drop:** Intuitive Datei-Auswahl zwischen Tabellen
- **Automatische Sortierung:** Zahlen in Dateinamen werden erkannt und sortiert

### 📝 Text-Editor
- **Vollwertiger Editor:** Syntax-Highlighting, Zeilennummern, Themes
- **Such- und Ersetzungsfunktionen:** Mit Regex-Unterstützung und Historie
- **Datei-Operationen:** Öffnen, Speichern, Speichern als
- **Export-Funktionen:** RTF/DOCX (nur Markdown), Markdown, HTML, TXT
- **Keyboard-Shortcuts:** Professionelle Tastenkombinationen

### 🔧 Makro-System
- **Automatische Text-Bereinigung:** 13 vordefinierte Schritte
- **Anführungszeichen-Konvertierung:** Französische ↔ Deutsche Anführungszeichen
- **Apostrophe-Korrektur:** Verschiedene Apostrophe-Formen korrigieren
- **Vollständig anpassbar:** Eigene Makros erstellen und bearbeiten
- **CSV-Export:** Makros können exportiert und geteilt werden

## 🚀 Funktionen im Detail

### Datei-Verarbeitung
- **DOCX-Extraktion:** Konvertiert DOCX-Dateien in lesbaren Text
- **Regex-Filterung:** Erweiterte Filterung mit regulären Ausdrücken
- **Verzeichnis-Memory:** Letztes Verzeichnis wird automatisch gespeichert
- **Mehrfachauswahl:** Einzelne oder alle Dateien zur Verarbeitung auswählen

### Text-Editor Features
- **Syntax-Highlighting:** Für Markdown und andere Formate
- **Theme-System:** Hell/Dunkel-Modi und weitere Themes
- **Font-Größe:** Dynamische Schriftgrößen-Anpassung
- **Formatierung:** Fett, Kursiv und weitere Formatierungen
- **Undo/Redo:** Vollständige Rückgängig-Funktion
- **Status-Anzeige:** Zeilen, Wörter, Zeichen zählen
- **Export-Funktionen:** 
  - **RTF:** Nur für Markdown-Dokumente (mit Formatierung)
  - **DOCX:** Nur für Markdown-Dokumente (mit Formatierung)
  - **Markdown, HTML, TXT:** Für alle Formate

### Such- und Ersetzungsfunktionen
- **Regex-Unterstützung:** Erweiterte Suche mit regulären Ausdrücken
- **Such-Historie:** Letzte 20 Such- und Ersetzungs-Patterns
- **Pattern-Speicherung:** Such- und Ersetzungs-Patterns können gespeichert werden
- **Optionen:** Case-Sensitive, Ganzes Wort, Regex
- **Navigation:** Vor/Zurück durch Suchergebnisse
- **Ersetzen:** Einzeln oder Alle ersetzen

### Makro-System
- **Text-Bereinigung:** 13 Schritte zur professionellen Nachbearbeitung
- **Anführungszeichen:** Französische ↔ Deutsche Konvertierung
- **Apostrophe:** Korrektur verschiedener Apostrophe-Formen
- **Makro-Editor:** Übersichtliche Verwaltung und Bearbeitung
- **Schritt-für-Schritt:** Einzelne Schritte aktivieren/deaktivieren
- **Cursor-Navigation:** Automatisches Folgen verschobener Schritte

## 📦 Voraussetzungen

- **Java:** 17 oder höher
- **Maven:** 3.6 oder höher
- **Betriebssystem:** Windows, macOS, Linux

## 🛠️ Installation und Ausführung

### 1. Projekt klonen
```bash
git clone https://github.com/geraldHack/Manuskript.git
cd Manuskript
```

### 2. Maven-Abhängigkeiten installieren
```bash
mvn clean install
```

### 3. Anwendung starten
```bash
mvn javafx:run
```

## 📖 Verwendung

### Schritt 1: Verzeichnis auswählen
1. Klicken Sie auf "Verzeichnis auswählen"
2. Wählen Sie ein Verzeichnis mit DOCX-Dateien
3. Das letzte Verzeichnis wird automatisch vorgeschlagen

### Schritt 2: Dateien filtern und auswählen
1. **Einfache Suche:** Verwenden Sie das Suchfeld für Textsuche
2. **Regex-Filterung:** Aktivieren Sie "Regex aktiv" für erweiterte Filterung
3. **Dateien auswählen:** Drag & Drop zwischen den Tabellen
4. **Sortierung:** Wählen Sie "Aufsteigend" oder "Absteigend"

### Schritt 3: Verarbeitung starten
1. Wählen Sie Dateien aus der rechten Tabelle
2. Klicken Sie auf "Ausgewählte verarbeiten" oder "Alle verarbeiten"
3. Das Ergebnis wird im Text-Editor angezeigt

### Schritt 4: Text bearbeiten
1. **Suchen/Ersetzen:** Ctrl+F oder Button "Suchen/Ersetzen"
2. **Makros anwenden:** Button "Makros" für automatische Bereinigung
3. **Datei speichern:** Ctrl+S oder Button "Speichern"
4. **Exportieren:** 
   - **RTF:** Nur für Markdown-Dokumente verfügbar
   - **DOCX:** Nur für Markdown-Dokumente verfügbar
   - **Markdown, HTML, TXT:** Für alle Formate verfügbar

## 🔍 Regex-Filterung

### Beispiele für Datei-Filterung
- `*[0-9][0-9]*` - Dateien mit zwei aufeinanderfolgenden Ziffern
- `.*kapitel.*` - Dateien mit "kapitel" im Namen (case-insensitive)
- `^[A-Z].*` - Dateien, die mit einem Großbuchstaben beginnen
- `[0-9]{2,3}` - Dateien mit 2-3 Ziffern

### Beispiele für Text-Suche
- `\b[A-Z][a-z]+` - Wörter, die mit Großbuchstaben beginnen
- `[.!?]{2,}` - Mehrfache Satzzeichen
- `\s{2,}` - Mehrfache Leerzeichen
- `[""''„"‚']` - Verschiedene Anführungszeichen

## 🎨 Makro-System

### Vordefinierte Makros

#### Text-Bereinigung (13 Schritte)
1. Mehrfache Leerzeichen reduzieren
2. Mehrfache Leerzeilen reduzieren
3. Gerade Anführungszeichen öffnen
4. Gerade Anführungszeichen schließen
5. Komma vor Anführungszeichen I
6. Einfache Anführungszeichen Französisch
7. Anführungszeichen Französisch
8. Auslassungszeichen
9. Buchstabe direkt an Auslassungszeichen
10. Buchstabe direkt nach Auslassungszeichen
11. Gedankenstrich
12. Komma vor Anführungszeichen
13. Einfache Anführungszeichen Französisch

#### Französische → Deutsche Anführungszeichen (2 Schritte)
1. Französische zu deutsche Anführungszeichen
2. Französische zu deutsche einfache Anführungszeichen

#### Apostrophe korrigieren (4 Schritte)
1. Apostrophe zwischen Buchstaben korrigieren
2. Grave-Akzent zu Apostrophe
3. Akut-Akzent zu Apostrophe
4. Typografisches Apostrophe korrigieren

### Makro-Verwaltung
- **Makros erstellen:** Eigene Makros mit benutzerdefinierten Schritten
- **Schritte bearbeiten:** Einzelne Schritte anpassen oder löschen
- **Schritte verschieben:** Reihenfolge mit Drag & Drop ändern
- **CSV-Export:** Makros können exportiert und geteilt werden

## ⌨️ Keyboard-Shortcuts

### Text-Editor
- `Ctrl+F` - Suchen/Ersetzen-Panel öffnen/schließen
- `F3` - Nächstes Suchergebnis
- `Shift+F3` - Vorheriges Suchergebnis
- `Ctrl+S` - Speichern
- `Ctrl+O` - Datei öffnen
- `Ctrl+N` - Neue Datei
- `Ctrl+Z` - Rückgängig
- `Ctrl+Y` - Wiederholen

### Makros
- **Button "Makros"** - Makro-Panel öffnen/schließen
- **Button "Makro ausführen"** - Aktuelles Makro ausführen

## 🏗️ Projektstruktur

```
src/
├── main/
│   ├── java/com/manuskript/
│   │   ├── Main.java              # Hauptklasse
│   │   ├── MainController.java    # Datei-Verwaltung Controller
│   │   ├── EditorWindow.java      # Text-Editor Controller
│   │   ├── DocxFile.java          # Datenmodell für DOCX-Dateien
│   │   ├── DocxProcessor.java     # DOCX-Verarbeitung
│   │   ├── Macro.java             # Makro-Datenmodell
│   │   └── MacroStep.java         # Makro-Schritt-Datenmodell
│   ├── resources/
│   │   ├── fxml/
│   │   │   ├── main.fxml          # Hauptfenster-Layout
│   │   │   └── editor.fxml        # Editor-Fenster-Layout
│   │   ├── css/
│   │   │   ├── styles.css         # Hauptfenster-Styling
│   │   │   └── editor.css         # Editor-Styling
│   │   └── logback.xml            # Logging-Konfiguration
└── test/                          # Unit-Tests (optional)
```

## 🛠️ Technologien

- **JavaFX:** Moderne Benutzeroberfläche
- **Apache POI:** DOCX-Datei-Verarbeitung
- **RichTextFX:** Erweiterter Text-Editor
- **Maven:** Build-Management
- **SLF4J/Logback:** Logging
- **Java Preferences API:** Einstellungen speichern

## 📝 Changelog

### Version 1.0 (Aktuell)
- ✅ Vollwertiger Text-Editor mit Syntax-Highlighting
- ✅ Makro-System für automatische Text-Bereinigung
- ✅ Drag & Drop Datei-Verwaltung
- ✅ Regex-Filterung und -Suche
- ✅ Theme-System (Hell/Dunkel)
- ✅ Export-Funktionen (RTF/DOCX nur für Markdown, Markdown, HTML, TXT)
- ✅ Keyboard-Shortcuts
- ✅ Undo/Redo-System
- ✅ Cursor-Navigation in Makro-Tabelle
- ✅ Pattern-Speicherung für Such- und Ersetzungs-Patterns

## 🤝 Beitragen

1. Fork das Repository
2. Erstelle einen Feature-Branch (`git checkout -b feature/AmazingFeature`)
3. Committe deine Änderungen (`git commit -m 'Add some AmazingFeature'`)
4. Push zum Branch (`git push origin feature/AmazingFeature`)
5. Öffne einen Pull Request

## 📄 Lizenz

Dieses Projekt steht unter der MIT-Lizenz. Siehe die [LICENSE](LICENSE) Datei für Details.

## 🙏 Danksagungen

- **Apache POI** für DOCX-Verarbeitung
- **RichTextFX** für den erweiterten Text-Editor
- **JavaFX** für die moderne Benutzeroberfläche
- **Maven** für das Build-Management

---

**Entwickelt für Autoren, die mehrere DOCX-Kapitel zu einem professionellen Manuskript zusammenführen möchten.** 📚✨
