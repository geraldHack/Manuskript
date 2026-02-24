# AGENTS.md

## Cursor Cloud specific instructions

### Project Overview
Manuskript is a JavaFX 21 desktop application for manuscript editing with AI integration (Ollama), DOCX processing, and export capabilities. Built with Maven. German-language UI.

### Prerequisites (installed in VM snapshot)
- Java 21 (OpenJDK, pre-installed at `/usr/lib/jvm/java-21-openjdk-amd64`)
- Maven 3.8+ (installed via `apt-get install maven`)
- Xvfb is available but the VM desktop at `DISPLAY=:1` can be used directly for GUI testing

### Build & Run Commands
| Task | Command |
|------|---------|
| Compile | `mvn compile` |
| Run tests | `mvn test` |
| Run application | `DISPLAY=:1 mvn javafx:run` |
| Package fat JAR | `mvn package` |
| Resolve dependencies | `mvn dependency:resolve` |

### Important Gotchas
- **Display**: The JavaFX app requires `DISPLAY=:1` (the VM desktop) to render. Do NOT use headless mode.
- **No Maven wrapper**: The repo does not include `mvnw`. System-installed Maven is required.
- **Deprecation warnings**: `OllamaWindow.java` and `MainController.java` produce compiler warnings (deprecated API, unchecked operations) — these are expected and non-blocking.
- **First launch**: The app shows a "Willkommen zu Manuskript" dialog asking for a project root directory. Without DOCX project files, the project selection screen will show "Keine Projekte gefunden".
- **Optional services**: Ollama (localhost:11434), LanguageTool (localhost:8081), Pandoc, FFmpeg are optional and not required for basic app functionality or tests.
- **Test suite**: 7 unit tests (JUnit 5) covering `QuotationMarkConverterTest` and `LoggingConfigurationTest`. All pass without a display server.
- **No linter configured**: The project has no dedicated linting tool (no Checkstyle, SpotBugs, etc.). Compiler warnings serve as the primary code quality check via `mvn compile`.
