# AGENTS.md

## Cursor Cloud specific instructions

### Project Overview
Manuskript is a JavaFX 21 desktop application for manuscript editing with AI integration (Ollama), DOCX processing, and export capabilities. Built with Maven. German-language UI.

### Prerequisites (installed in VM snapshot)
- Java 21 (OpenJDK, pre-installed at `/usr/lib/jvm/java-21-openjdk-amd64`)
- Maven 3.8+ (installed via `apt-get install maven`)

### Build & Run Commands
| Task | Command |
|------|---------|
| Compile | `mvn compile` |
| Run tests | `mvn test` |
| Run application | `DISPLAY=:1 mvn javafx:run` |
| Package fat JAR | `mvn package` |
| Resolve dependencies | `mvn dependency:resolve` |
| OpenRouter Monitor (optional) | `cd tools/openrouter-monitor && mvn package` dann `./run-openrouter-monitor.sh` |

### Wichtig: Welcher Code laeuft?
- **`mvn compile`** schreibt nur nach `target/classes`. Die **JAR** (z.B. `target/manuskript-standalone.jar`) und das **App-Image** (z.B. `installer-output\Manuskript\`) werden dabei **nicht** aktualisiert.
- Wenn die App ueber **Manuskript.exe** / **Manuskript.app** (installer-output) oder eine **alte JAR** gestartet wird, laeuft der Stand des letzten **`mvn package`** bzw. **`create-installer.bat`** (Windows) / **`create-installer.sh`** (macOS arm64).
- Damit nach Aenderungen der **aktuelle Code** laeuft: App mit **`mvn javafx:run`** starten (oder `run-developer.bat` unter Windows), oder vor dem Start **`mvn package`** ausfuehren und danach die gebaute App starten.

### Windows (Entwicklung und App-Image)
- **JDK 21** erforderlich (z.B. Eclipse Adoptium). `find-java21.bat` sucht uebliche Installationspfade; optional einmal `.\set-java21-env.ps1` ausfuehren.
- **Dev-Start:** `run-developer.bat` (setzt JAVA_HOME und startet `mvn compile javafx:run`). Arbeitsverzeichnis = Projektwurzel (`config/`, `logs/`).
- **Installer/App-Image:** `create-installer.bat` (braucht `jpackage.exe` aus JDK 21). Setup-EXE braucht WiX 3; `deploy\windows\ensure-wix3.ps1` findet eine Installation oder lädt portable 3.14-Binaries nach `%LOCALAPPDATA%\Manuskript\wix3`. Windows-Icon: `deploy\windows\ensure-windows-icon.ps1` erzeugt `installer-assets\Manuskript.ico` aus der PNG; jpackage bekommt `--icon` für App-Image und Setup-EXE. Ressourcen (`config`, `ffmpeg`, `pandoc`, `language tool`, Demo) landen unter `installer-output\Manuskript\app\` — dort erwartet sie auch `ApplicationPaths`.
- **Start gebuendelt:** `installer-output\Manuskript\Manuskript.exe`. Nach Code-Aenderungen Installer neu bauen, sonst laeuft alter Stand.
- **JavaFX-SDK:** lokal `javafx-sdk-21.0.6\` (gitignored) fuer `javafx:run`; jmods laedt das Installer-Skript bei Bedarf.
- **Whisper (Diktat):** Unter Windows automatische Einrichtung (Download von `whisper-bin-x64.zip` nach `whisper/`); unter macOS weiterhin via Homebrew. Modell-Download plattformuebergreifend.

### Kapitel-Editor (nur Canvas – Legacy ignoriert)
- **Aktiver Editor:** `ManuskriptEditorTestWindow` / `ManuskriptTextEditor` / `MdTextArea`.
- **API für Features:** `ChapterEditorHost` – neue Editor-Funktionen nur im Canvas-Pfad umsetzen.
- **Legacy `EditorWindow` (RichTextFX/FXML):** nicht mehr Ziel für Entwicklung; keine Parität, keine Doppelpflege, nicht als Referenz nutzen.
- Standard: Canvas-Editor per Preference `use_canvas_chapter_editor` (siehe `MainController.openChapterEditor`).
- Im Canvas-Editor: Agenten-Panel, Makros, Textanalyse, Szenen-Outline, Online-Lektorat (Toolbar).

### Important Gotchas
- **Display**: The JavaFX app requires `DISPLAY=:1` (the VM desktop) to render. Do NOT use headless mode.
- **No Maven wrapper**: The repo does not include `mvnw`. System-installed Maven is required.
- **Deprecation warnings**: `OllamaWindow.java` and `MainController.java` produce compiler warnings (deprecated API, unchecked operations) — these are expected and non-blocking.
- **First launch**: Shows "Willkommen zu Manuskript" so the user can confirm the project root. Packaged default is `~/Documents/Manuskript` (Gott demo copied there once). Dev (`mvn javafx:run`) still suggests repo `Manuskripte/`.
- **Optional services**: Ollama (localhost:11434), LanguageTool (localhost:8081), Pandoc, FFmpeg are optional and not required for basic app functionality or tests.
- **OpenRouter Monitor** (`tools/openrouter-monitor/`): eigenständiges JavaFX-Hilfsprogramm für Credits und API-Logs; liest `agent.openai.api_key` und `agent.openai.api_url` aus Manuskript-Konfiguration. Nicht in die Haupt-App integriert.
- **Test suite**: 7 unit tests (JUnit 5) covering `QuotationMarkConverterTest` and `LoggingConfigurationTest`. All pass without a display server.
- **No linter configured**: The project has no dedicated linting tool (no Checkstyle, SpotBugs, etc.). Compiler warnings serve as the primary code quality check via `mvn compile`.
