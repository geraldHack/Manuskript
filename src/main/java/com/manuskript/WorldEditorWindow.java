package com.manuskript;

import com.manuskript.agent.AIBackend;
import com.manuskript.agent.OpenAIBackend;
import com.manuskript.agent.OllamaBackend;
import com.manuskript.CustomAlert;
import com.manuskript.StageManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Welt-Editor: Verwaltung von Projekt-Kontextdateien (chapter.txt, characters.txt, context.txt, outline.txt, style.txt, synopsis.txt, worldbuilding.txt).
 * Jede Datei hat einen Tab mit TextArea für manuelle Editierung und KI-Button für automatische Generierung.
 */
public class WorldEditorWindow {

    private static final Logger logger = LoggerFactory.getLogger(WorldEditorWindow.class);

    private CustomStage stage;
    private final Window owner;
    private final String projectDirectory;
    private final MainController mainController;
    private final Map<String, TextArea> fileToTextArea = new HashMap<>();
    private final Map<String, Button> fileToAiButton = new HashMap<>();
    private final Map<String, Button> fileToSaveButton = new HashMap<>();
    private Label statusLabel;
    private AIBackend aiBackend;
    private boolean suppressAutoSave = false;

    private static final String[] FILES = {
        "chapter.txt",
        "characters.txt",
        "context.txt",
        "outline.txt",
        "style.txt",
        "synopsis.txt",
        "worldbuilding.txt"
    };

    private static final String[] FILE_LABELS = {
        "Kapitel-Zusammenfassungen",
        "Charaktere",
        "Kontext",
        "Outline",
        "Schreibstil",
        "Synopsis",
        "Worldbuilding"
    };

    public WorldEditorWindow(Window owner, String projectDirectory, MainController mainController) {
        this.owner = owner;
        this.projectDirectory = projectDirectory;
        this.mainController = mainController;
        initBackend();
        createUI();
    }

    private void initBackend() {
        String backendType = ResourceManager.getParameter("agent.backend", "Ollama");
        logger.info("Backend-Typ: {}", backendType);
        if ("OpenAI".equals(backendType)) {
            aiBackend = new OpenAIBackend();
            logger.info("OpenAI-Backend initialisiert");
        } else {
            aiBackend = new OllamaBackend(new OllamaService());
            logger.info("Ollama-Backend initialisiert");
        }
        logger.info("Backend ist null: {}", aiBackend == null);
    }

    private void createUI() {
        stage = StageManager.createStage("Welt-Editor");
        if (owner != null && owner instanceof javafx.stage.Stage) {
            stage.initOwner(owner);
        }
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.setWidth(900);
        stage.setHeight(600);

        int theme = java.util.prefs.Preferences.userNodeForPackage(MainController.class).getInt("main_window_theme", 0);

        // Statuszeile oben rechts
        statusLabel = new Label("Bereit");
        statusLabel.getStyleClass().add("status-label");
        HBox statusBox = new HBox(statusLabel);
        statusBox.setAlignment(Pos.CENTER_RIGHT);
        statusBox.setPadding(new Insets(10, 15, 10, 10));

        TabPane tabPane = new TabPane();
        tabPane.getStyleClass().add("tab-pane");
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        for (int i = 0; i < FILES.length; i++) {
            String filename = FILES[i];
            String label = FILE_LABELS[i];
            Tab tab = new Tab(label);
            tab.setContent(createTabContent(filename));
            tabPane.getTabs().add(tab);
        }

        VBox root = new VBox();
        root.getStyleClass().addAll(getThemeStyleClasses(theme));
        root.getChildren().addAll(statusBox, tabPane);
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        Scene scene = new Scene(root);
        String cssPath = ResourceManager.getCssResource("css/manuskript.css");
        if (cssPath != null) scene.getStylesheets().add(cssPath);
        stage.setTitleBarTheme(theme);
        stage.setSceneWithTitleBar(scene);
        stage.setFullTheme(theme);

        // Window-Handler: Speichern beim Schließen
        stage.setOnCloseRequest(e -> {
            saveAllFiles();
        });
    }

    private static List<String> getThemeStyleClasses(int themeIndex) {
        switch (themeIndex) {
            case 0: return java.util.Collections.singletonList("weiss-theme");
            case 1: return java.util.Collections.singletonList("theme-dark");
            case 2: return java.util.Collections.singletonList("pastell-theme");
            case 3: return java.util.Collections.singletonList("blau-theme");
            case 4: return java.util.Collections.singletonList("gruen-theme");
            case 5: return java.util.Collections.singletonList("lila-theme");
            default: return java.util.Collections.singletonList("weiss-theme");
        }
    }

    private VBox createTabContent(String filename) {
        TextArea textArea = new TextArea();
        textArea.setWrapText(true);
        textArea.setPrefRowCount(20);

        // Datei laden
        loadFile(filename, textArea);
        fileToTextArea.put(filename, textArea);

        // Auto-Save bei Textänderungen
        textArea.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!suppressAutoSave) {
                saveFile(filename, textArea);
            }
        });

        Button saveButton = new Button("💾 Speichern");
        saveButton.setOnAction(e -> saveFile(filename, textArea));
        fileToSaveButton.put(filename, saveButton);

        Button aiButton = new Button("🤖 KI-Generierung");
        aiButton.setOnAction(e -> handleAiGeneration(filename, textArea));
        fileToAiButton.put(filename, aiButton);

        HBox buttonBox = new HBox(10, saveButton, aiButton);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new Insets(5, 0, 0, 0));

        VBox content = new VBox(5, textArea, buttonBox);
        VBox.setVgrow(textArea, Priority.ALWAYS);
        return content;
    }

    private void loadFile(String filename, TextArea textArea) {
        Path filePath = Paths.get(projectDirectory, filename);
        if (Files.exists(filePath)) {
            try {
                String content = Files.readString(filePath);
                textArea.setText(content);
            } catch (IOException e) {
                logger.error("Fehler beim Laden von {}: {}", filename, e.getMessage());
            }
        }
    }

    private void saveFile(String filename, TextArea textArea) {
        Path filePath = Paths.get(projectDirectory, filename);
        try {
            Files.writeString(filePath, textArea.getText());
            logger.info("Datei {} gespeichert", filename);
        } catch (IOException e) {
            logger.error("Fehler beim Speichern von {}: {}", filename, e.getMessage());
        }
    }

    private void saveAllFiles() {
        for (Map.Entry<String, TextArea> entry : fileToTextArea.entrySet()) {
            saveFile(entry.getKey(), entry.getValue());
        }
    }

    private void handleAiGeneration(String filename, TextArea textArea) {
        String currentContent = textArea.getText();
        boolean hasContent = currentContent != null && !currentContent.trim().isEmpty();

        if (hasContent) {
            int theme = java.util.prefs.Preferences.userNodeForPackage(MainController.class).getInt("main_window_theme", 0);
            CustomAlert alert = new CustomAlert(CustomAlert.AlertType.CONFIRMATION);
            alert.setHeaderText("Die Datei enthält bereits Inhalt.");
            alert.setContentText("Möchtest du nur neue Kapitel einfügen oder alles neu generieren?");
            
            // Custom ButtonTypes erstellen
            ButtonType appendButton = new ButtonType("Nur neue Kapitel");
            ButtonType replaceButton = new ButtonType("Alles neu");
            alert.setButtonTypes(appendButton, replaceButton);
            
            alert.applyTheme(theme);
            alert.initOwner(stage);
            alert.showAndWait().ifPresent(response -> {
                if (response == appendButton) {
                    generateWithAi(filename, textArea, true); // Anhängen
                } else if (response == replaceButton) {
                    generateWithAi(filename, textArea, false); // Ersetzen
                }
            });
        } else {
            generateWithAi(filename, textArea, false);
        }
    }

    private void generateWithAi(String filename, TextArea textArea, boolean append) {
        if (aiBackend == null) {
            logger.error("KI-Backend nicht initialisiert");
            int theme = java.util.prefs.Preferences.userNodeForPackage(MainController.class).getInt("main_window_theme", 0);
            Platform.runLater(() -> {
                CustomAlert alert = new CustomAlert(CustomAlert.AlertType.ERROR);
                alert.setHeaderText("KI-Backend nicht initialisiert");
                alert.setContentText("Bitte konfigurieren Sie das KI-Backend in den Parametern.");
                alert.applyTheme(theme);
                alert.initOwner(stage);
                alert.showAndWait();
            });
            return;
        }

        // Für chapter.txt: Prüfen, ob schon Zusammenfassungen existieren
        if ("chapter.txt".equals(filename)) {
            generateChapterSummaries(textArea);
            return;
        }

        String prompt = getPromptForFile(filename);
        String currentContent = textArea.getText();
        
        // Kontext aus Projekt-Kapiteln sammeln
        String projectContext = collectProjectContext();
        
        String fullPrompt = prompt + "\n\n" + projectContext;
        if (append && currentContent != null && !currentContent.trim().isEmpty()) {
            fullPrompt += "\n\nBereits existierender Inhalt:\n" + currentContent;
        }

        logger.info("KI-Generierung für {} gestartet", filename);
        
        // Button deaktivieren während Generierung
        Button aiButton = fileToAiButton.get(filename);
        if (aiButton != null) {
            aiButton.setDisable(true);
            aiButton.setText("🤖 Generiere...");
        }

        aiBackend.chat(
            "Du bist ein hilfreicher deutscher Assistent. Antworte bitte auf Deutsch.",
            fullPrompt,
            2000
        ).thenAccept(generatedContent -> {
            Platform.runLater(() -> {
                if (append) {
                    textArea.setText(currentContent + "\n\n" + generatedContent);
                } else {
                    textArea.setText(generatedContent);
                }
                saveFile(filename, textArea);
                logger.info("KI-Generierung für {} abgeschlossen", filename);
                
                // Button wieder aktivieren
                if (aiButton != null) {
                    aiButton.setDisable(false);
                    aiButton.setText("🤖 KI-Generierung");
                }
            });
        }).exceptionally(ex -> {
            logger.error("KI-Generierung für {} fehlgeschlagen: {}", filename, ex.getMessage());
            int theme = java.util.prefs.Preferences.userNodeForPackage(MainController.class).getInt("main_window_theme", 0);
            Platform.runLater(() -> {
                CustomAlert errorAlert = new CustomAlert(CustomAlert.AlertType.ERROR);
                errorAlert.setHeaderText("Fehler bei der KI-Generierung");
                errorAlert.setContentText(ex.getMessage());
                errorAlert.applyTheme(theme);
                errorAlert.initOwner(stage);
                errorAlert.showAndWait();
                
                // Button wieder aktivieren
                if (aiButton != null) {
                    aiButton.setDisable(false);
                    aiButton.setText("🤖 KI-Generierung");
                }
            });
            return null;
        });
    }

    private void generateChapterSummaries(TextArea textArea) {
        logger.info("generateChapterSummaries aufgerufen");
        String currentContent = textArea.getText();
        Set<String> existingSummaries = parseExistingSummaries(currentContent);
        logger.info("Existierende Zusammenfassungen: {}", existingSummaries);
        
        // Kapitel aus der rechten Tabelle holen
        List<String> mdFiles = mainController.getMarkdownFilesInOrder();
        logger.info("MD-Dateien aus rechter Tabelle: {}", mdFiles);
        
        // Button deaktivieren während Generierung
        Button aiButton = fileToAiButton.get("chapter.txt");
        if (aiButton != null) {
            aiButton.setDisable(true);
            aiButton.setText("🤖 Generiere...");
        }
        
        // Kapitel sequentiell generieren
        generateNextChapter(textArea, mdFiles, existingSummaries, 0, aiButton);
    }
    
    private void generateNextChapter(TextArea textArea, List<String> mdFiles, Set<String> existingSummaries, int index, Button aiButton) {
        if (index >= mdFiles.size()) {
            // Alle Kapitel verarbeitet
            Platform.runLater(() -> {
                statusLabel.setText("Alle Kapitel verarbeitet");
                if (aiButton != null) {
                    aiButton.setDisable(false);
                    aiButton.setText("🤖 KI-Generierung");
                }
            });
            return;
        }
        
        String mdFileName = mdFiles.get(index);
        String chapterName = mdFileName.replace(".md", "").trim(); // Leerzeichen am Ende entfernen
        
        if (existingSummaries.contains(chapterName)) {
            // Kapitel hat bereits Zusammenfassung, zum nächsten
            logger.info("Kapitel {} bereits vorhanden, überspringe", chapterName);
            generateNextChapter(textArea, mdFiles, existingSummaries, index + 1, aiButton);
            return;
        }
        
        Path mdPath = Paths.get(projectDirectory, "data", mdFileName);
        logger.info("Verarbeite Kapitel {}: {}", index, chapterName);
        logger.info("MD-Pfad: {}", mdPath);
        logger.info("MD-Datei existiert: {}", Files.exists(mdPath));
        
        if (!Files.exists(mdPath)) {
            // Datei existiert nicht, zum nächsten
            generateNextChapter(textArea, mdFiles, existingSummaries, index + 1, aiButton);
            return;
        }
        
        try {
            String chapterContent = readMdContent(mdPath);
            chapterContent = chapterContent.replaceAll("(?m)^---+$", "");
            int charCount = chapterContent.length();
            logger.info("Kapitelinhalt geladen, Länge: {}", charCount);
            
            // Aktuelles Modell loggen
            logger.info("Aktuelles KI-Modell: {}", aiBackend.getCurrentModel());
            
            // Statuszeile aktualisieren
            Platform.runLater(() -> {
                statusLabel.setText(String.format("Datei %d von %d. %s (%d Zeichen)", index + 1, mdFiles.size(), chapterName, charCount));
            });
            
            String prompt = "Fasse das folgende Kapitel auf Deutsch zusammen. Kein Vorgeplänkel, keine Floskeln, keine Bewertung. Nur die Zusammenfassung.\n" +
                "Wichtig: Erstelle genau EINE Zusammenfassung für das GESAMTE Kapitel. Unterteile es NICHT in Unterkapitel.\n\n" +
                "Gliedere deine Antwort exakt so:\n" +
                "## Kapitelname\n" +
                "Inhaltsangabe (3-5 Sätze)\n" +
                "**Ort:** ...\n" +
                "**Charaktere:** ...\n" +
                "**Themen:** ...\n" +
                "**Stimmung:** ...";
            String fullPrompt = prompt + "\n\nKapitelinhalt:\n" + chapterContent;
            logger.info("Prompt für KI: {}", prompt);
            logger.info("FullPrompt Länge: {}", fullPrompt.length());
            
            final String finalChapterName = chapterName;
            final int nextIndex = index + 1;
            logger.info("KI-Aufruf für Kapitel: {}", finalChapterName);
            
            // Summary-Header vorab ins TextArea schreiben
            Platform.runLater(() -> {
                String header = "\n\n## " + finalChapterName + "\n";
                textArea.appendText(header);
            });

            suppressAutoSave = true;
            StringBuilder streamingBuffer = new StringBuilder();
            OllamaBackend ollamaBackend = (OllamaBackend) aiBackend;
            ollamaBackend.chatStreaming(
                "Du bist ein hilfreicher deutscher Assistent. Antworte bitte auf Deutsch.",
                fullPrompt,
                chunk -> Platform.runLater(() -> {
                    streamingBuffer.append(chunk);
                    textArea.appendText(chunk);
                    //textArea.setScrollTop(Double.MAX_VALUE);
                    statusLabel.setText(String.format("Datei %d von %d. %s (%d Zeichen)", index + 1, mdFiles.size(), finalChapterName, streamingBuffer.length()));
                }),
                () -> {
                    logger.info("KI-Antwort erhalten für Kapitel: {}", finalChapterName);
                    logger.info("KI-Antwort Länge: {}", streamingBuffer.length());
                    Platform.runLater(() -> {
                        suppressAutoSave = false;
                        saveFile("chapter.txt", textArea);
                        logger.info("Zusammenfassung für {} generiert und gespeichert", finalChapterName);
                        generateNextChapter(textArea, mdFiles, existingSummaries, nextIndex, aiButton);
                    });
                },
                ex -> {
                    logger.error("Fehler bei der Generierung der Zusammenfassung für {}: {}", chapterName, ex.getMessage(), ex);
                    int theme = java.util.prefs.Preferences.userNodeForPackage(MainController.class).getInt("main_window_theme", 0);
                    Platform.runLater(() -> {
                        suppressAutoSave = false;
                        CustomAlert errorAlert = new CustomAlert(CustomAlert.AlertType.ERROR);
                        errorAlert.setHeaderText("Fehler bei der KI-Generierung");
                        errorAlert.setContentText(ex.getMessage());
                        errorAlert.applyTheme(theme);
                        errorAlert.initOwner(stage);
                        errorAlert.showAndWait();
                        generateNextChapter(textArea, mdFiles, existingSummaries, nextIndex, aiButton);
                    });
                }
            );
        } catch (Exception e) {
            logger.error("Fehler beim Lesen von {}: {}", mdFileName, e.getMessage(), e);
            // Zum nächsten Kapitel
            generateNextChapter(textArea, mdFiles, existingSummaries, index + 1, aiButton);
        }
    }
    
    private Set<String> parseExistingSummaries(String content) {
        Set<String> summaries = new HashSet<>();
        if (content == null || content.trim().isEmpty()) {
            logger.info("Kein Inhalt vorhanden, keine Zusammenfassungen gefunden");
            return summaries;
        }
        
        // Markdown-Überschriften parsen (## Kapitelname)
        String[] lines = content.split("\n");
        for (String line : lines) {
            if (line.startsWith("## ")) {
                String chapterName = line.substring(3).trim();
                summaries.add(chapterName);
                logger.info("Gefundene Zusammenfassung: {}", chapterName);
            }
        }
        logger.info("Insgesamt {} Zusammenfassungen gefunden", summaries.size());
        return summaries;
    }

    private String collectProjectContext() {
        // Zuerst versuchen, chapter.txt (Zusammenfassungen) als Kontext zu nutzen
        Path chapterTxtPath = Paths.get(projectDirectory, "chapter.txt");
        if (Files.exists(chapterTxtPath)) {
            try {
                String chapterContent = Files.readString(chapterTxtPath);
                if (chapterContent != null && !chapterContent.trim().isEmpty()) {
                    logger.info("Verwende chapter.txt Summaries als Kontext ({} Zeichen)", chapterContent.length());
                    return "Kapitel-Zusammenfassungen:\n" + chapterContent;
                }
            } catch (Exception e) {
                logger.warn("Konnte chapter.txt nicht lesen: {}", e.getMessage());
            }
        }

        // Fallback: Alle Kapitel-Inhalte sammeln
        logger.info("Keine chapter.txt Summaries gefunden, verwende Volltext");
        StringBuilder context = new StringBuilder();
        context.append("Projekt-Kapitel:\n");
        
        try {
            Path projectDir = Paths.get(projectDirectory);
            if (Files.exists(projectDir) && mainController != null) {
                List<String> mdFiles = mainController.getMarkdownFilesInOrder();
                
                for (String mdFileName : mdFiles) {
                    Path mdPath = projectDir.resolve("data").resolve(mdFileName);
                    if (Files.exists(mdPath)) {
                        try {
                            String chapterContent = readMdContent(mdPath);
                            String chapterName = mdFileName.replace(".md", "");
                            context.append("\n=== ").append(chapterName).append(" ===\n");
                            context.append(chapterContent);
                            context.append("\n");
                        } catch (Exception e) {
                            logger.error("Fehler beim Lesen von {}: {}", mdFileName, e.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Fehler beim Sammeln des Projekt-Kontexts: {}", e.getMessage());
        }
        
        return context.toString();
    }
    
    private String readMdContent(Path mdPath) throws IOException {
        try {
            String content = Files.readString(mdPath);
            return content;
        } catch (Exception e) {
            logger.error("Fehler beim Lesen von MD {}: {}", mdPath, e.getMessage());
            return "[Fehler beim Lesen des Kapitels]";
        }
    }

    private String getPromptForFile(String filename) {
        switch (filename) {
            case "chapter.txt":
                return "Erstelle Zusammenfassungen aller Kapitel des Buches. Nutze Markdown-Formatierung mit Überschriften für jedes Kapitel.";
            case "characters.txt":
                return "Erstelle Beschreibungen aller Charaktere im Buch. Kein Vorgeplänkel, keine Floskeln, keine Bewertung. Nutze Markdown-Formatierung mit Überschriften für jeden Charakter. Enthalte: Name, Rolle, Persönlichkeit, Hintergrund, wichtige Eigenschaften.";
            case "context.txt":
                return "Erstelle eine Liste wichtiger Details und Fakten über die Charaktere und die Welt. Nutze Markdown-Formatierung mit Kategorien.";
            case "outline.txt":
                return "Erstelle ein Outline mit Szenen für alle Kapitel. Nutze Markdown-Formatierung mit Kapitel-Nummern und Szenen-Beschreibungen.";
            case "style.txt":
                return "Erstelle eine Beschreibung des Schreibstils des Buches. Nutze Markdown-Formatierung.";
            case "synopsis.txt":
                return "Erstelle eine Synopsis des Buches. Nutze Markdown-Formatierung.";
            case "worldbuilding.txt":
                return "Erstelle eine Beschreibung der Welt und des Settings des Buches. Nutze Markdown-Formatierung mit Kategorien.";
            default:
                return "Erhalte Informationen über das Projekt.";
        }
    }

    public void show() {
        stage.show();
    }

    public void hide() {
        stage.hide();
    }
}
