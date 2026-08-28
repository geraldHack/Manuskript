# Manuskript – Roman- und Buchproduktion mit Markdown-Editor

[![Java](https://img.shields.io/badge/Java-21+-orange.svg)](https://openjdk.java.net/)
[![JavaFX](https://img.shields.io/badge/JavaFX-21+-blue.svg)](https://openjdk.java.net/javafx/)
[![Maven](https://img.shields.io/badge/Maven-3.8+-green.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

> **JavaFX-Desktopanwendung für die professionelle Bearbeitung von DOCX-Kapiteln als Markdown – mit Agenten, Online-Lektorat, Export und Hörbuch-Produktion. Ideal im Zusammenspiel mit Sudowrite.**

> **Hinweis:** Die Benutzeroberfläche ist derzeit nur auf Deutsch verfügbar.

![Manuskript Hauptfenster](Screenshot.png)
*Hauptfenster mit Projektverwaltung, Kapitelauswahl und Toolbar*

## Highlights

- **Canvas-Kapitel-Editor** – Markdown mit Inline-Darstellung, „Markdown ausblenden“, Suche/Ersetzen, Zeilennummern
- **Agenten-Panel** – Analyse (Plothole, Dialog, Stil, Sprachentflechtung), Szene schreiben, Chat, Überarbeiten, eigene Agenten (auch Freeform)
- **Online-Lektorat** – Kapitelweises Lektorat über OpenAI-kompatible API
- **Welt-Editor** – Projekt-Kontextdateien (Charaktere, Outline, Worldbuilding, …) mit KI-Generierung
- **Roman-Assistent** – Interaktive Romanplanung mit KI und Session-Fortsetzung
- **Setup & Funktionspakete** – KI, Agenten, Lektorat, Diktat und Hörbuch einzeln ein- und ausschalten
- **JAR-Plugins** – eigene Werkzeuge im Manuskript-Look; Demo: OpenRouter- und Mammouth-Monitor
- **Makro-System** – Automatische Textbereinigung (Anführungszeichen, Gedankenstriche, Absätze)
- **Textanalyse** – Füllwörter, Phrasen, Wortwiederholungen, Sprechantworten
- **Downloads-Monitor** – Sudowrite-Integration mit automatischem DOCX-Import
- **Export** – RTF, DOCX, Markdown, HTML, EPUB, PDF, LaTeX
- **Hörbuch-Erstellung** – TTS-Editor mit ElevenLabs und lokaler KI (ComfyUI/Qwen), ACX-kompatible MP3
- **Diktat** – Whisper lokal oder über API, unabhängig vom KI-Hauptschalter

## Schnellstart

### Voraussetzungen

> **Hinweis:** macOS und Windows werden aktiv gepflegt. Linux (Cloud-VM) für Builds/Tests.

- **Java 21+** ([Download](https://adoptium.net/))
- **Maven 3.8+** ([Download](https://maven.apache.org/download.cgi))
- **Pandoc** (Export) – unter Windows im Projekt unter `pandoc/` enthalten
- **Optional – Agenten (lokal):** [Ollama](https://ollama.com) als Backend (`agent.ollama.*`)
- **Optional – Agenten & Lektorat (Cloud):** OpenAI-kompatibler API-Key (`agent.openai.*`, `api.lektorat.*`)
- **Optional – Rechtschreibung:** LanguageTool (localhost:8081)
- **Optional – PDF:** MiKTeX oder TeX Live
- **Optional – Hörbuch:** FFmpeg (im Projekt unter `ffmpeg/` oder im PATH)
- **Optional – Diktat:** Whisper (Windows: Setup lädt `whisper/`; macOS: Homebrew)

### Installation & Start

**Windows (Empfehlung):**

```bat
git clone https://codeberg.org/gehackb/Manuskript.git
cd Manuskript
run-developer.bat
```

App-Image bauen: `create-installer.bat` → Start über `installer-output\Manuskript\Manuskript.exe` (Ressourcen unter `app\`).

Mit Standardoption lädt das Skript ZIP oder Setup-EXE nach [spoteroxe.de/downloads.html](https://spoteroxe.de/downloads.html) (`create-installer.bat --no-upload` nur lokal). Setup-EXE braucht [WiX Toolset 3](https://wixtoolset.org/docs/wix3/).

**Allgemein (Maven):**

```bash
git clone https://codeberg.org/gehackb/Manuskript.git
cd Manuskript
mvn clean install
mvn javafx:run
```

> **Hinweis:** `mvn compile` aktualisiert nur `target/classes`. Für JAR/App-Image zuerst `mvn package` bzw. `create-installer.bat` ausführen.

### Erste Schritte

1. Beim ersten Start Projektstamm bestätigen (Willkommen / Setup)
2. Im **Setup** Funktionspakete wählen und optional Demo-Plugins aktivieren
3. Projektverzeichnis mit DOCX-Kapiteln wählen (oder Roman-Assistent für ein neues Projekt)
4. Kapitel in die rechte Tabelle legen und sortieren
5. **Kapitel bearbeiten** – öffnet den Canvas-Editor
6. Optional: Agenten, Online-Lektorat, Makros, Textanalyse, Diktat
7. Buch exportieren oder Hörbuch erzeugen

## Kernfunktionen

### Datei-Verwaltung

- Zwei-Tabellen-Ansicht (verfügbar / im Buch)
- Drag & Drop, Kapitel-Reihenfolge = Buchreihenfolge
- Diff & Merge beim Import neuer DOCX-Versionen

### Kapitel-Editor (Canvas)

Der Editor arbeitet intern mit Markdown; Formatierung über Toolbar-Buttons und Tastenkürzel.

- **Markdown-Toolbar:** Fett, Kursiv, Überschriften, Listen, Tabellen, Code, Links, Undo/Redo
- **„Markdown ausblenden“:** Formatierung wird inline dargestellt, Syntaxzeichen werden ausgeblendet
- **Host-Toolbar:** Zeilennummern, Mark/Zitat/Farbe, Anführungszeichen-Stil, LanguageTool
- **Werkzeuge:** Szenen-Outline, Textanalyse, Agenten-Panel, Online-Lektorat, Makros, Bilder
- **Speichern:** Markdown-Kopie im `data`-Verzeichnis; Diff bei ungespeicherten Änderungen

Ausführliche Hilfe: im Editor über die Hilfe-Buttons (?).

### Agenten & Online-Lektorat

**Agenten** (rechtes Panel im Editor):

- Analyse-Agenten (Plotlöcher, Dialog, Textstruktur, Show-don't-tell, Sprachentflechtung, …)
- Szene-schreiben-Agent mit Anweisungsfeld und Szenen-Kontext
- Chatbot mit Projektkontext
- Überarbeiten per Kontextmenü auf markiertem Text
- Eigene Agenten über **+** (schließenbar); optional **Freeform** = Antwort als Fließtext ohne `<PROBLEM>`-Parser
- Konfiguration in `config/agents.json` und pro Tab; Backend Ollama oder OpenAI-kompatibel (OpenRouter, Mammouth, …)

**Online-Lektorat** (Toolbar „Lektorat“):

- Ganzes Kapitel in Abschnitten an API senden
- Vorschläge im rechten Panel übernehmen oder ablehnen
- Parameter unter **Online-Lektorat** (`api.lektorat.*`)

### Welt-Editor & Roman-Assistent

- **Welt-Editor:** Bearbeitet `characters.txt`, `outline.txt`, `worldbuilding.txt` usw. im Projektordner; KI-Generierung und Extraktion aus Kapiteln
- **Roman-Assistent:** Geführte Planungsphasen mit KI; Session wird im Projekt gespeichert und kann fortgesetzt werden

### Downloads-Monitor & Sudowrite

- Überwacht den Downloads-Ordner auf neue DOCX/ZIP-Dateien
- Automatisches Matching und sicheres Ersetzen mit Backup

### Setup, Plugins & eigene Programme

Über den **Setup-Assistenten** (Toolbar / Erststart):

- **Funktionen:** Pakete ein- und ausschalten (KI insgesamt, Agenten, Roman-Assistent, Online-Lektorat, Diktat, Hörbuch). Ausgeschaltete Pakete blenden Buttons und Parameter-Tabs aus; Keys und Texte bleiben gespeichert.
- **Plugins:** mitgelieferte oder eigene JARs aktivieren. Katalog `plugin-catalog/` (inaktiv, mitgeliefert), geladen wird nur `plugins/` (Toolbar-Button). Im Setup an = Kopie nach `plugins/`, aus = wieder entfernen.
- **Eigene Programme:** fremde Apps als Extra-Prozess starten (`config/launchers.json`) – sicherer als ein JAR in derselben JVM.

**Demo-Plugins** (liegen unter `tools/`, JAR nach `plugin-catalog/`):

| Plugin | Zweck |
|--------|--------|
| [OpenRouter-Monitor](tools/openrouter-monitor/) | Credits und API-Logs für OpenRouter |
| [Mammouth-Monitor](tools/mammouth-monitor/) | Credits und Modellliste für Mammouth |

Beide laufen **in derselben JVM** wie Manuskript (Manuskript-Look, Theme). Zusätzlich standalone: `./run-openrouter-monitor.sh` bzw. `./run-mammouth-monitor.sh`.

Eigenes Plugin schreiben: [plugins/README.md](plugins/README.md) (API `plugin-api/`, ServiceLoader, kein `Platform.exit()`). JSON unter `config/plugins/` ist **Legacy** und wird nicht mehr geladen.

### Makros & Textanalyse

- Standard-Makro „Textbereinigung“ für typische Formatfehler
- Textanalyse: Füllwörter, Phrasen, Wortwiederholungen (konfigurierbar in `config/textanalysis.properties`)

## Export-Funktionen

| Format | Beschreibung | Formatierung |
|--------|-------------|--------------|
| **RTF** | Rich Text Format | Vollständig |
| **TXT** | Plain Text | Nur Text |
| **DOCX** | Microsoft Word | Vollständig |
| **Markdown** | Markdown | Strukturiert |
| **HTML5** | Web | Styling |
| **PDF** | PDF | Styling (MiKTeX/TeX Live) |
| **LaTeX** | LaTeX | Styling |
| **EPUB** | E-Book | Styling |

## Hörbuch-Erstellung

- Dedizierter TTS-Editor pro Kapitel mit Segment-Verwaltung
- **ElevenLabs API** oder **lokale KI über ComfyUI** (z. B. Qwen3-TTS)
- ACX-kompatible MP3-Exporte pro Kapitel (`001_Kapitelname.mp3`)
- FFmpeg-basierte Nachbearbeitung (Trimmen, Pausen, Bitrate)

## Konfiguration

Wichtige Parameter (über **Parameter**-Dialog oder `config/parameters.properties`):

```properties
# Agenten
agent.enabled=true
agent.backend=Ollama
agent.ollama.api_url=http://localhost:11434
agent.ollama.model=gemma3:4b
agent.openai.api_key=
agent.openai.model=gpt-4o-mini

# Online-Lektorat
api.lektorat.api_key=
api.lektorat.base_url=https://api.openai.com/v1
api.lektorat.model=gpt-4o-mini
api.lektorat.type=allgemein

# UI
ui.editor_font_size=16
main_window_theme=0
```

Agenten-Definitionen: `config/agents.json`

## Tastenkürzel (Hauptfenster)

| Kürzel | Funktion           |
|--------|--------------------|
| Strg+D | Debug Window       |
| Strg+R | Restore Windows    |



## Tastenkürzel (Editor)

| Kürzel | Funktion |
|--------|----------|
| Strg+S | Speichern |
| Strg+F | Suchen |
| Strg+H | Ersetzen |
| Strg+Z / Strg+Y | Rückgängig / Wiederholen |
| Strg+B / Strg+I | Fett / Kursiv |
| Strg+M | Markdown-Hilfe |
| Strg+Pfeil links/rechts | Vorheriges/nächstes Kapitel |

## Technologien

- **JavaFX 21** – Benutzeroberfläche
- **Canvas-Editor** (`ManuskriptTextEditor` / `MdTextArea`) – Markdown-Bearbeitung
- **Ollama / OpenAI-kompatible APIs** – Agenten und Lektorat (OpenRouter, Mammouth, …)
- **LanguageTool** – Rechtschreibprüfung
- **ElevenLabs / ComfyUI / Whisper** – Sprachsynthese und Diktat
- **FFmpeg** – Audio
- **Maven** – Build; schlanke Plugin-API (`plugin-api/`)

## Entwicklung

```bash
mvn compile
mvn test
mvn javafx:run
```

Demo-Plugins bauen (kopiert die JAR nach `plugin-catalog/`):

```bash
cd tools/openrouter-monitor && mvn package
cd ../mammouth-monitor && mvn package
```

Siehe auch `AGENTS.md` (Canvas-Editor als Standard) und [plugins/README.md](plugins/README.md) für die Plugin-API.

## Lizenz

MIT – siehe [LICENSE](LICENSE)

## Support

- [Issues](https://codeberg.org/gehackb/Manuskript/issues)
- [Diskussionen](https://codeberg.org/gehackb/Manuskript/discussions)

---

**Für Autoren, die Manuskripte professionell von der Kapiteldatei bis zum Export oder Hörbuch bearbeiten möchten.**
