package com.manuskript;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.prefs.Preferences;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public class PandocExportWindow extends CustomStage {
    private static final Logger logger = LoggerFactory.getLogger(PandocExportWindow.class);
    
    // UI Components
    private ComboBox<String> formatComboBox;
    private ComboBox<String> templateComboBox;
    private TextArea templateDescription;
    private TextField titleField;
    private TextField subtitleField;
    private TextField authorField;
    private TextField rightsField;
    private TextField dateField;
    private TextArea abstractArea;
    private TextField outputDirectoryField;
    private TextField fileNameField;
    private Button browseButton;
    private Button exportButton;
    private Button cancelButton;
    
    // EPUB-spezifische Felder
    private HBox coverImageBox;
    private TextField coverImageField;
    private Button coverImageBrowseButton;
    
    // Template-Felder
    private HBox templateBox;
    
    // Data
    private File inputMarkdownFile;
    private List<File> referenceTemplates;
    private String projectName;
    private Preferences preferences;
    private int currentThemeIndex;
    private File pandocHome; // Ordner, in dem sich pandoc.exe befindet
    
    public PandocExportWindow(File inputMarkdownFile, String projectName) {
        super();
        this.inputMarkdownFile = inputMarkdownFile;
        this.projectName = projectName;
        this.preferences = Preferences.userNodeForPackage(this.getClass());
        this.currentThemeIndex = preferences.getInt("main_window_theme", 0);
        this.setCustomTitle("Manuskript - Buch exportieren");
        
        setTitle("Pandoc Export - " + projectName);
        setWidth(700);
        setHeight(900);
        setResizable(true);
        
        initializeUI();
        loadReferenceTemplates();
        loadProjectMetadata();
        loadWindowProperties();
        setupWindowListeners();
        
        // Initiale Format-spezifische Felder setzen (Template und Cover sind bereits im Layout)
        updateFormatSpecificFields();

        // Titel nochmal setzen nach der Initialisierung
        setTitle("Buch exportieren - " + projectName);
    }
    
    private void initializeUI() {
        VBox root = new VBox(20);
        root.setPadding(new Insets(20));
        root.getStyleClass().add("pandoc-export-dialog");
        
        // Format Selection
        HBox formatBox = new HBox(10);
        formatBox.setAlignment(Pos.CENTER_LEFT);
        Label formatLabel = new Label("Export-Format:");
        formatLabel.getStyleClass().add("dialog-label");
        
        formatComboBox = new ComboBox<>();
        formatComboBox.getItems().addAll("docx", "epub3", "html5", "rtf", "pdf", "latex");
        formatComboBox.setValue("docx");
        formatComboBox.getStyleClass().add("dialog-combobox");
        formatComboBox.setOnAction(e -> {
            updateFormatSpecificFields();
            updateFileNameExtension();
        });
        
        formatBox.getChildren().addAll(formatLabel, formatComboBox);
        
        // Template Selection
        templateBox = new HBox(10);
        templateBox.setAlignment(Pos.CENTER_LEFT);
        Label templateLabel = new Label("Vorlage:");
        templateLabel.getStyleClass().add("dialog-label");
        
        templateComboBox = new ComboBox<>();
        templateComboBox.getStyleClass().add("dialog-combobox");
        templateComboBox.setOnAction(e -> updateTemplateDescription());
        
        templateDescription = new TextArea();
        templateDescription.setPrefRowCount(15);
        templateDescription.setMinHeight(120); // Mindesthöhe setzen
        templateDescription.setEditable(false);
        templateDescription.getStyleClass().add("dialog-textarea");
        templateDescription.setVisible(false); // Initial ausgeblendet

        templateBox.getChildren().addAll(templateLabel, templateComboBox);
        
        // Cover Image für EPUB
        coverImageBox = new HBox(10);
        coverImageBox.setAlignment(Pos.CENTER_LEFT);
        Label coverImageLabel = new Label("Cover-Bild:");
        coverImageLabel.setPrefWidth(100);
        coverImageField = new TextField();
        coverImageField.setPromptText("Bild für EPUB-Cover");
        coverImageField.setPrefWidth(400);
        coverImageBrowseButton = new Button("📁");
        coverImageBrowseButton.getStyleClass().add("dialog-button-icon");
        coverImageBrowseButton.setOnAction(e -> browseCoverImage());
        coverImageBox.getChildren().addAll(coverImageLabel, coverImageField, coverImageBrowseButton);
        
        // Metadata Section
        VBox metadataBox = createMetadataSection();
        
        // Output Section
        VBox outputBox = createOutputSection();
        
        // Buttons
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        
        cancelButton = new Button("Abbrechen");
        cancelButton.getStyleClass().add("dialog-button-secondary");
        cancelButton.setOnAction(e -> close());
        
        exportButton = new Button("Export starten");
        exportButton.getStyleClass().add("dialog-button-primary");
        exportButton.setOnAction(e -> startExport());
        
        buttonBox.getChildren().addAll(cancelButton, exportButton);
        
        // Layout
        root.getChildren().addAll(
            formatBox,
            templateBox,
            templateDescription,
            coverImageBox,
            new Separator(),
            metadataBox,
            new Separator(),
            outputBox,
            buttonBox
        );
        
        setSceneWithTitleBar(new Scene(root));
        centerOnScreen();

        // Titel sicherstellen nach dem Erstellen der Szene
        setTitle("Buch exportieren - " + projectName);
        
        // CSS-Stylesheets laden
        String stylesCss = ResourceManager.getCssResource("css/styles.css");
        String editorCss = ResourceManager.getCssResource("css/editor.css");
        String manuskriptCss = ResourceManager.getCssResource("css/manuskript.css");
        
        if (stylesCss != null) {
            getScene().getStylesheets().add(stylesCss);
        }
        if (editorCss != null) {
            getScene().getStylesheets().add(editorCss);
        }
        if (manuskriptCss != null) {
            getScene().getStylesheets().add(manuskriptCss);
        }
    }
    
    private VBox createMetadataSection() {
        VBox metadataBox = new VBox(15);
        metadataBox.getStyleClass().add("metadata-section");
        
        Label metadataLabel = new Label("Metadaten");
        metadataLabel.getStyleClass().add("section-title");
        
        // Title
        HBox titleBox = new HBox(10);
        titleBox.setAlignment(Pos.CENTER_LEFT);
        Label titleLabel = new Label("Titel:");
        titleLabel.setPrefWidth(100);
        titleField = new TextField();
        titleField.setPromptText("Titel des Werks");
        titleField.setPrefWidth(400);
        titleBox.getChildren().addAll(titleLabel, titleField);
        
        // Subtitle
        HBox subtitleBox = new HBox(10);
        subtitleBox.setAlignment(Pos.CENTER_LEFT);
        Label subtitleLabel = new Label("Untertitel:");
        subtitleLabel.setPrefWidth(100);
        subtitleField = new TextField();
        subtitleField.setPromptText("Untertitel (optional)");
        subtitleField.setPrefWidth(400);
        subtitleBox.getChildren().addAll(subtitleLabel, subtitleField);
        
        // Author
        HBox authorBox = new HBox(10);
        authorBox.setAlignment(Pos.CENTER_LEFT);
        Label authorLabel = new Label("Autor:");
        authorLabel.setPrefWidth(100);
        authorField = new TextField();
        authorField.setPromptText("Name des Autors");
        authorField.setPrefWidth(400);
        authorBox.getChildren().addAll(authorLabel, authorField);
        
        // Rights
        HBox rightsBox = new HBox(10);
        rightsBox.setAlignment(Pos.CENTER_LEFT);
        Label rightsLabel = new Label("Rechte:");
        rightsLabel.setPrefWidth(100);
        rightsField = new TextField();
        rightsField.setPromptText("© 2025 Autor");
        rightsField.setPrefWidth(400);
        rightsBox.getChildren().addAll(rightsLabel, rightsField);
        
        // Date
        HBox dateBox = new HBox(10);
        dateBox.setAlignment(Pos.CENTER_LEFT);
        Label dateLabel = new Label("Datum:");
        dateLabel.setPrefWidth(100);
        dateField = new TextField();
        dateField.setPromptText("Oktober 2025");
        dateField.setPrefWidth(400);
        dateBox.getChildren().addAll(dateLabel, dateField);
        
        // Abstract
        VBox abstractBox = new VBox(5);
        Label abstractLabel = new Label("Abstract:");
        abstractArea = new TextArea();
        abstractArea.setPrefRowCount(20);
        abstractArea.setMinHeight(160); // Mindesthöhe setzen (20 Zeilen * 8px pro Zeile)
        abstractArea.setWrapText(true); // Umbruch aktivieren
        abstractArea.setPromptText("Kurze Beschreibung des Werks...");
        abstractBox.getChildren().addAll(abstractLabel, abstractArea);
        
        metadataBox.getChildren().addAll(
            metadataLabel,
            titleBox,
            subtitleBox,
            authorBox,
            rightsBox,
            dateBox,
            abstractBox
        );
        
        return metadataBox;
    }
    
    private VBox createOutputSection() {
        VBox outputBox = new VBox(15);
        outputBox.getStyleClass().add("output-section");
        
        Label outputLabel = new Label("Ausgabe");
        outputLabel.getStyleClass().add("section-title");
        
        // Output Directory
        HBox directoryBox = new HBox(10);
        directoryBox.setAlignment(Pos.CENTER_LEFT);
        Label directoryLabel = new Label("Zielverzeichnis:");
        directoryLabel.setPrefWidth(100);
        outputDirectoryField = new TextField();
        outputDirectoryField.setPromptText("Verzeichnis für die Ausgabedatei");
        outputDirectoryField.setPrefWidth(400);
        browseButton = new Button("📁");
        browseButton.getStyleClass().add("dialog-button-icon");
        browseButton.setOnAction(e -> browseOutputDirectory());
        directoryBox.getChildren().addAll(directoryLabel, outputDirectoryField, browseButton);
        
        // File Name
        HBox fileNameBox = new HBox(10);
        fileNameBox.setAlignment(Pos.CENTER_LEFT);
        Label fileNameLabel = new Label("Dateiname:");
        fileNameLabel.setPrefWidth(100);
        fileNameField = new TextField();
        fileNameField.setPromptText("dateiname.docx");
        fileNameField.setPrefWidth(400);
        fileNameBox.getChildren().addAll(fileNameLabel, fileNameField);
        
        outputBox.getChildren().addAll(outputLabel, directoryBox, fileNameBox);
        
        return outputBox;
    }
    
    private void loadReferenceTemplates() {
        // Stelle sicher, dass Pandoc vorhanden ist (entpacke ggf. pandoc.zip)
        ensurePandocAvailable();
        referenceTemplates = new ArrayList<>();
        File pandocDir = (pandocHome != null) ? pandocHome : new File("pandoc-3.8.1");
        
        if (pandocDir.exists() && pandocDir.isDirectory()) {
            File[] files = pandocDir.listFiles((dir, name) -> 
                name.toLowerCase(Locale.ROOT).startsWith("reference-") && 
                name.toLowerCase(Locale.ROOT).endsWith(".docx"));
            
            if (files != null) {
                for (File file : files) {
                    referenceTemplates.add(file);
                    String displayName = file.getName().replace("reference-", "").replace(".docx", "");
                    templateComboBox.getItems().add(displayName);
                }
            }
        }
        
        if (!templateComboBox.getItems().isEmpty()) {
            templateComboBox.setValue(templateComboBox.getItems().get(0));
            updateTemplateDescription();
        }
    }
    
    private void updateTemplateDescription() {
        String selectedTemplate = templateComboBox.getValue();
        if (selectedTemplate != null) {
            // Look for description file
            String descriptionFile = "pandoc-3.8.1/reference-" + selectedTemplate + ".txt";
            try {
                if (Files.exists(Paths.get(descriptionFile))) {
                    String description = Files.readString(Paths.get(descriptionFile));
                    templateDescription.setText(description);
                } else {
                    templateDescription.setText("Keine Beschreibung verfügbar für: " + selectedTemplate);
                }
            } catch (IOException e) {
                templateDescription.setText("Fehler beim Laden der Beschreibung: " + e.getMessage());
            }
        }
    }
    
    private void loadProjectMetadata() {
        // Load existing metadata from project session
        titleField.setText(preferences.get("pandoc_title", projectName));
        subtitleField.setText(preferences.get("pandoc_subtitle", ""));
        
        // Persistierte Werte laden
        authorField.setText(preferences.get("pandoc_author", "Gerald Leonard"));
        rightsField.setText(preferences.get("pandoc_rights", "© 2025 Gerald Leonard"));
        dateField.setText(preferences.get("pandoc_date", "Oktober 2025"));
        outputDirectoryField.setText(preferences.get("pandoc_output_directory", ""));
        
        // Abstract laden
        abstractArea.setText(preferences.get("pandoc_abstract", ""));
        
        // Cover-Bild laden
        coverImageField.setText(preferences.get("pandoc_cover_image", ""));
        
        // Format laden
        String savedFormat = preferences.get("pandoc_format", "docx");
        formatComboBox.setValue(savedFormat);
        
        // Template laden
        String savedTemplate = preferences.get("pandoc_template", "");
        if (!savedTemplate.isEmpty()) {
            templateComboBox.setValue(savedTemplate);
        }
        
        // Dateiname basierend auf Format setzen
        String fileName = projectName.replaceAll("[^a-zA-Z0-9\\s]", "").replaceAll("\\s+", "_");
        String extension = getFileExtensionForFormat(savedFormat);
        fileNameField.setText(fileName + "." + extension);
    }
    
    private void browseOutputDirectory() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Zielverzeichnis wählen");
        
        // Letztes Verzeichnis als Startverzeichnis verwenden
        String lastDirectory = preferences.get("pandoc_output_directory", "");
        if (!lastDirectory.isEmpty() && new File(lastDirectory).exists()) {
            chooser.setInitialDirectory(new File(lastDirectory));
        }
        
        File selectedDir = chooser.showDialog(this);
        if (selectedDir != null) {
            outputDirectoryField.setText(selectedDir.getAbsolutePath());
            // Verzeichnis für nächste Verwendung speichern
            preferences.put("pandoc_output_directory", selectedDir.getAbsolutePath());
        }
    }
    
    private void startExport() {
        // Validate inputs
        if (titleField.getText().trim().isEmpty()) {
            showAlert("Fehler", "Bitte geben Sie einen Titel ein.");
            return;
        }
        
        if (outputDirectoryField.getText().trim().isEmpty()) {
            showAlert("Fehler", "Bitte wählen Sie ein Zielverzeichnis.");
            return;
        }
        
        if (fileNameField.getText().trim().isEmpty()) {
            showAlert("Fehler", "Bitte geben Sie einen Dateinamen ein.");
            return;
        }
        
        try {
            // Export-Button deaktivieren während des Exports
            exportButton.setDisable(true);
            exportButton.setText("Export läuft...");
            
            // YAML-Metadaten direkt in Markdown-Datei einfügen
            File markdownWithMetadata = createMarkdownWithMetadata();
            if (markdownWithMetadata == null) {
                showAlert("Fehler", "Konnte Markdown-Datei mit Metadaten nicht erstellen.");
                return;
            }
            
            // Pandoc-Aufruf
            boolean success = runPandocExport(markdownWithMetadata);
            
            if (success) {
                showAlert("Erfolg", "Export erfolgreich abgeschlossen!");
                close();
            } else {
                showAlert("Fehler", "Export fehlgeschlagen. Siehe Logs für Details.");
            }
            
        } catch (Exception e) {
            logger.error("Fehler beim Export", e);
            showAlert("Fehler", "Export fehlgeschlagen: " + e.getMessage());
        } finally {
            // Export-Button wieder aktivieren
            exportButton.setDisable(false);
            exportButton.setText("Export starten");
        }
    }
    
    private void loadWindowProperties() {
        // Fenster-Position und -Größe laden
        double x = PreferencesManager.getWindowPosition(preferences, "pandoc_window_x", -1);
        double y = PreferencesManager.getWindowPosition(preferences, "pandoc_window_y", -1);
        double width = PreferencesManager.getWindowWidth(preferences, "pandoc_window_width", 700);
        double height = PreferencesManager.getWindowHeight(preferences, "pandoc_window_height", 900);
        
        if (x >= 0 && y >= 0) {
            setX(x);
            setY(y);
        }
        setWidth(width);
        setHeight(height);
    }
    
    private void setupWindowListeners() {
        // Fenster-Position speichern
        xProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(oldVal)) {
                PreferencesManager.putWindowPosition(preferences, "pandoc_window_x", newVal.doubleValue());
            }
        });
        
        yProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(oldVal)) {
                PreferencesManager.putWindowPosition(preferences, "pandoc_window_y", newVal.doubleValue());
            }
        });
        
        // Fenster-Größe speichern
        widthProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(oldVal)) {
                PreferencesManager.putWindowWidth(preferences, "pandoc_window_width", newVal.doubleValue());
            }
        });
        
        heightProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(oldVal)) {
                PreferencesManager.putWindowHeight(preferences, "pandoc_window_height", newVal.doubleValue());
            }
        });
        
        // Metadaten speichern bei Änderung
        authorField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.trim().isEmpty()) {
                preferences.put("pandoc_author", newVal.trim());
            }
        });
        
        rightsField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.trim().isEmpty()) {
                preferences.put("pandoc_rights", newVal.trim());
            }
        });
        
        dateField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.trim().isEmpty()) {
                preferences.put("pandoc_date", newVal.trim());
            }
        });
        
        // Titel und Untertitel speichern
        titleField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.trim().isEmpty()) {
                preferences.put("pandoc_title", newVal.trim());
            }
        });
        
        subtitleField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.trim().isEmpty()) {
                preferences.put("pandoc_subtitle", newVal.trim());
            }
        });
        
        // Abstract speichern
        abstractArea.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.trim().isEmpty()) {
                preferences.put("pandoc_abstract", newVal.trim());
            }
        });
        
        // Output-Verzeichnis speichern
        outputDirectoryField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.trim().isEmpty()) {
                preferences.put("pandoc_output_directory", newVal.trim());
            }
        });
        
        // Cover-Bild speichern
        coverImageField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.trim().isEmpty()) {
                preferences.put("pandoc_cover_image", newVal.trim());
            }
        });
        
        // Format speichern
        formatComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                preferences.put("pandoc_format", newVal);
            }
        });
        
        // Template speichern
        templateComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                preferences.put("pandoc_template", newVal);
            }
        });
    }
    
    private File createYamlFile() {
        try {
            // YAML-Datei im Arbeitsverzeichnis erstellen (nicht temporär)
            File yamlFile = new File("pandoc_metadata.yaml");
            if (yamlFile.exists()) {
                yamlFile.delete(); // Alte Datei löschen
            }
            
            try (PrintWriter writer = new PrintWriter(new FileWriter(yamlFile, StandardCharsets.UTF_8))) {
                writer.println("title: \"" + escapeYamlString(titleField.getText().trim()) + "\"");
                
                if (!subtitleField.getText().trim().isEmpty()) {
                    writer.println("subtitle: \"" + escapeYamlString(subtitleField.getText().trim()) + "\"");
                }
                
                if (!authorField.getText().trim().isEmpty()) {
                    writer.println("author: \"" + escapeYamlString(authorField.getText().trim()) + "\"");
                }
                
                if (!rightsField.getText().trim().isEmpty()) {
                    writer.println("rights: \"" + escapeYamlString(rightsField.getText().trim()) + "\"");
                }
                
                if (!dateField.getText().trim().isEmpty()) {
                    writer.println("date: \"" + escapeYamlString(dateField.getText().trim()) + "\"");
                }
                
                if (!abstractArea.getText().trim().isEmpty()) {
                    writer.println("abstract: |");
                    String[] lines = abstractArea.getText().trim().split("\n");
                    for (String line : lines) {
                        writer.println("  " + line);
                    }
                    writer.println("abstract-title: \"Zusammenfassung\"");
                }

                // Cover-Bild für HTML5 hinzufügen (falls vorhanden)
                if ("html5".equals(formatComboBox.getValue()) && !coverImageField.getText().trim().isEmpty()) {
                    File coverImageFile = new File(coverImageField.getText().trim());
                    if (coverImageFile.exists()) {
                        String baseName = fileNameField.getText().replace(".html", "");
                        File htmlDir = new File(outputDirectoryField.getText(), baseName + "_html");
                        String coverFileName = "cover." + getFileExtension(coverImageFile.getName());
                        File targetCover = new File(htmlDir, coverFileName);
                        writer.println("cover-image: \"" + targetCover.getName() + "\"");
                    }
                }
                
                // Leerzeile am Ende für korrektes YAML
                writer.println();
            }
            
            
            // YAML-Datei-Inhalt für Debug ausgeben
            try {
                String content = Files.readString(yamlFile.toPath());
                logger.debug("YAML-Inhalt:\n{}", content);
            } catch (IOException e) {
                logger.warn("Konnte YAML-Inhalt nicht lesen: {}", e.getMessage());
            }
            
            return yamlFile;
            
        } catch (IOException e) {
            logger.error("Fehler beim Erstellen der YAML-Datei", e);
            return null;
        }
    }
    
    private File createMarkdownWithMetadata() {
        try {
            // Temporäre Markdown-Datei mit YAML-Metadaten erstellen
            File tempMarkdownFile = File.createTempFile("manuskript_export_", ".md");
            tempMarkdownFile.deleteOnExit(); // Automatisch löschen nach Programmende
            
            try (PrintWriter writer = new PrintWriter(new FileWriter(tempMarkdownFile, StandardCharsets.UTF_8))) {
                // YAML-Frontmatter schreiben - korrekte YAML-Syntax
                writer.println("---");
                
                // Titel (immer erforderlich)
                String title = titleField.getText().trim();
                if (!title.isEmpty()) {
                    writer.println("title: \"" + escapeYamlString(title) + "\"");
                } else {
                    // Fallback-Titel falls leer
                    writer.println("title: \"Manuskript\"");
                }
                
                // Untertitel
                String subtitle = subtitleField.getText().trim();
                if (!subtitle.isEmpty()) {
                    writer.println("subtitle: \"" + escapeYamlString(subtitle) + "\"");
                }
                
                // Autor
                String author = authorField.getText().trim();
                if (!author.isEmpty()) {
                    writer.println("author: \"" + escapeYamlString(author) + "\"");
                }
                
                // Datum
                String date = dateField.getText().trim();
                if (!date.isEmpty()) {
                    writer.println("date: \"" + escapeYamlString(date) + "\"");
                }
                
                // Rechte
                String rights = rightsField.getText().trim();
                if (!rights.isEmpty()) {
                    writer.println("rights: \"" + escapeYamlString(rights) + "\"");
                }
                
                // PDF-spezifische Metadaten
                String format = formatComboBox.getValue();
                if ("pdf".equals(format)) {
                    writer.println("lang: de");
                    writer.println("mainfont: DejaVu Serif");
                    writer.println("sansfont: DejaVu Sans");
                    writer.println("monofont: DejaVu Sans Mono");
                    writer.println("toc: true");
                }
                
                // Abstract (mehrzeilig)
                String abstractText = abstractArea.getText().trim();
                if (!abstractText.isEmpty()) {
                    writer.println("abstract: |");
                    String[] lines = abstractText.split("\n");
                    for (String line : lines) {
                        writer.println("  " + line);
                    }
                }
                
                writer.println("---");
                writer.println(); // Leerzeile nach YAML
                
                // Original Markdown-Inhalt hinzufügen
                String originalContent = Files.readString(inputMarkdownFile.toPath());
                writer.print(originalContent);
            }
            
            // Debug-Ausgabe - zeige das komplette YAML-Frontmatter
            try {
                String content = Files.readString(tempMarkdownFile.toPath());
                // Finde das Ende des YAML-Frontmatters
                int yamlEnd = content.indexOf("---", 3); // Zweites "---" finden
                if (yamlEnd > 0) {
                    String yamlPart = content.substring(0, yamlEnd + 3);
                    logger.info("YAML-Frontmatter:\n{}", yamlPart);
                } else {
                    logger.warn("YAML-Frontmatter nicht gefunden! Erste 1000 Zeichen:\n{}", 
                        content.substring(0, Math.min(1000, content.length())));
                }
            } catch (IOException e) {
                logger.warn("Konnte Markdown-Inhalt nicht lesen: {}", e.getMessage());
            }
            
            return tempMarkdownFile;
            
        } catch (IOException e) {
            logger.error("Fehler beim Erstellen der Markdown-Datei mit Metadaten", e);
            return null;
        }
    }
    
    private String escapeYamlString(String input) {
        if (input == null) return "";
        // YAML-sichere Escaping für Strings in Anführungszeichen
        return input.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
                   // Doppelpunkte müssen nicht escaped werden, da sie in Anführungszeichen stehen
    }
    
    private boolean runPandocExport(File markdownFile) {
        try {
            // Sicherstellen, dass Pandoc verfügbar ist
            if (!ensurePandocAvailable()) {
                showAlert("Pandoc fehlt", "Pandoc konnte nicht gefunden oder installiert werden. Bitte stellen Sie sicher, dass 'pandoc.zip' im Programmverzeichnis liegt.");
                return false;
            }

            // Pandoc-Pfad
            File pandocExe = (pandocHome != null)
                ? new File(pandocHome, "pandoc.exe")
                : new File("pandoc-3.8.1", "pandoc.exe");
            
            // Ausgabedatei
            String outputDir = outputDirectoryField.getText().trim();
            String fileName = fileNameField.getText().trim();
            File outputFile = new File(outputDir, fileName);
            
            // Template-Datei
            String selectedTemplate = templateComboBox.getValue();
            File templateFile = null;
            if (selectedTemplate != null && !referenceTemplates.isEmpty()) {
                for (File template : referenceTemplates) {
                    if (template.getName().contains(selectedTemplate)) {
                        templateFile = template;
                        break;
                    }
                }
            }
            
            // Format ermitteln
            String format = formatComboBox.getValue();

            // Pandoc-Befehl zusammenbauen (wie von Hand erfolgreich verwendet)
            List<String> command = new ArrayList<>();
            command.add(pandocExe.getAbsolutePath());
            command.add("\"" + markdownFile.getAbsolutePath() + "\"");
            command.add("-o");

            // Für HTML5: Ausgabe ins Unterverzeichnis
            String finalOutputPath;
            if ("html5".equals(format)) {
                String baseName = fileNameField.getText().replace(".html", "");
                File htmlDir = new File(outputDirectoryField.getText(), baseName + "_html");
                File htmlFile = new File(htmlDir, fileNameField.getText());
                finalOutputPath = htmlFile.getAbsolutePath();
            } else {
                finalOutputPath = outputFile.getAbsolutePath();
            }

            command.add("\"" + finalOutputPath + "\"");

            // Grundlegende Optionen - YAML-Metadaten explizit aktivieren
            command.add("--from=markdown+yaml_metadata_block");
            command.add("--to=" + getOutputFormat());

            // Format-spezifische Optionen
            if ("epub3".equals(format)) {
                // EPUB3-spezifische Optionen (wie von Hand erfolgreich verwendet)
                command.add("--toc");
                command.add("--epub-chapter-level=1"); // Verwende References für Kapitel-Struktur
                command.add("--css=epub.css");

                // Cover-Bild für EPUB3
                if (!coverImageField.getText().trim().isEmpty()) {
                    File coverImageFile = new File(coverImageField.getText().trim());
                    if (coverImageFile.exists()) {
                        File pandocDir = new File("pandoc-3.8.1");
                        if (pandocDir.exists()) {
                            try {
                                String coverFileName = "cover." + getFileExtension(coverImageFile.getName());
                                File targetCover = new File(pandocDir, coverFileName);
                                Files.copy(coverImageFile.toPath(), targetCover.toPath(),
                                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                                command.add("--epub-cover-image=\"" + targetCover.getAbsolutePath() + "\"");
                            } catch (IOException e) {
                                logger.error("Fehler beim Kopieren des Cover-Bildes", e);
                            }
                        }
                    }
                }
            } else if ("docx".equals(format)) {
                // DOCX-spezifische Optionen für bessere Titelei
                if (templateFile != null) {
                    command.add("--reference-doc=\"" + templateFile.getAbsolutePath() + "\"");
                }
                command.add("--highlight-style=tango");
                command.add("--reference-links");
            } else if ("html5".equals(format)) {
                // HTML5-spezifische Optionen
                command.add("--toc"); // Inhaltsverzeichnis für HTML
                command.add("--standalone"); // Vollständiges HTML-Dokument

                // HTML-Unterverzeichnis erstellen
                String baseName = fileNameField.getText().replace(".html", "");
                File htmlDir = new File(outputDirectoryField.getText(), baseName + "_html");
                htmlDir.mkdirs();

                // HTML-Datei ins Unterverzeichnis legen
                String htmlFileName = fileNameField.getText();
                File htmlFile = new File(htmlDir, htmlFileName);

                        // CSS-Datei ins Unterverzeichnis kopieren
                        File sourceCss = new File("pandoc-3.8.1", "epub.css");
                        File targetCss = new File(htmlDir, "styles.css");
                        if (sourceCss.exists()) {
                            try {
                                Files.copy(sourceCss.toPath(), targetCss.toPath(), StandardCopyOption.REPLACE_EXISTING);
                                command.add("--css=styles.css"); // Lokale CSS-Datei verwenden
                            } catch (IOException e) {
                                logger.error("Fehler beim Kopieren der CSS-Datei für HTML", e);
                            }
                        }

                        // HTML-Template für Cover-Bild erstellen
                        String htmlTemplate = createHtmlTemplate(htmlDir, htmlFileName);
                        if (htmlTemplate != null) {
                            command.add("--template=" + htmlTemplate);
                        }

                // Cover-Bild ins Unterverzeichnis kopieren (falls vorhanden)
                if (!coverImageField.getText().trim().isEmpty()) {
                    File coverImageFile = new File(coverImageField.getText().trim());
                    if (coverImageFile.exists()) {
                        try {
                            String coverFileName = "cover." + getFileExtension(coverImageFile.getName());
                            File targetCover = new File(htmlDir, coverFileName);
                            Files.copy(coverImageFile.toPath(), targetCover.toPath(),
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                        } catch (IOException e) {
                            logger.error("Fehler beim Kopieren des Cover-Bildes für HTML", e);
                        }
                    }
                }
            } else if ("pdf".equals(format)) {
                // PDF-spezifische Optionen für professionelle Formatierung
                command.add("--pdf-engine=xelatex");
                command.add("--toc"); // Inhaltsverzeichnis für PDF
                
                // Template für PDF verwenden (vereinfachtes XeLaTeX-Template)
                File pdfTemplate = new File("pandoc-3.8.1", "simple-xelatex-template.tex");
                if (pdfTemplate.exists()) {
                    command.add("--template=" + pdfTemplate.getAbsolutePath());
                    logger.info("Verwende vereinfachtes XeLaTeX-Template: {}", pdfTemplate.getName());
                } else {
                    logger.info("Kein XeLaTeX-Template gefunden, verwende Standard-Template");
                }
                
                // XeLaTeX-spezifische Optionen für bessere Kompatibilität
                command.add("--variable=lang:de");
                
                // LaTeX-Optionen für bessere Kompatibilität (Template definiert bereits Fonts)
                command.add("--variable=geometry:margin=2.5cm");
                command.add("--variable=fontsize:12pt");
                command.add("--variable=documentclass:article");
                command.add("--variable=linestretch:1.2");
                
                // XeLaTeX-spezifische Engine-Optionen
                command.add("--pdf-engine-opt=-shell-escape");
                command.add("--pdf-engine-opt=-interaction=nonstopmode");
                
                // Lua-Filter für automatische Initialen
                File luaFilter = new File("pandoc-3.8.1", "dropcaps.lua");
                if (luaFilter.exists()) {
                    command.add("--lua-filter=" + luaFilter.getAbsolutePath());
                    logger.info("Verwende Lua-Filter für automatische Initialen: {}", luaFilter.getName());
                } else {
                    logger.warn("Lua-Filter für Initialen nicht gefunden: {}", luaFilter.getAbsolutePath());
                }
            }

            // Standalone für vollständiges Dokument (wenn nicht bereits hinzugefügt)
            if (!command.contains("--standalone")) {
                command.add("--standalone");
            }
            
            
            // Lösche die bestehende Ausgabedatei vor dem Export, um sicherzustellen, dass sie neu erstellt wird
            File fileToCheck = "html5".equals(format) ?
                new File(finalOutputPath) : outputFile;

            if (fileToCheck.exists()) {
                try {
                    Files.delete(fileToCheck.toPath());
                    logger.debug("Bestehende Ausgabedatei gelöscht: {}", fileToCheck.getName());
                } catch (IOException e) {
                    logger.warn("Konnte bestehende Ausgabedatei nicht löschen: {}", e.getMessage());
                }
            }

            // Prozess starten - Arbeitsverzeichnis auf Pandoc-Verzeichnis setzen
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(pandocHome != null ? pandocHome : new File("pandoc-3.8.1")); // Arbeitsverzeichnis auf Pandoc setzen
            pb.environment().put("PATH", System.getenv("PATH")); // PATH weitergeben
            
            // MiKTeX-Update-Warnungen deaktivieren
            pb.environment().put("MIKTEX_DISABLE_UPDATE_CHECK", "1");
            pb.environment().put("MIKTEX_DISABLE_INSTALLER", "1");
            pb.environment().put("MIKTEX_DISABLE_AUTO_INSTALL", "1");

            Process process = pb.start();

            // Standard-Output und Standard-Error auslesen für bessere Fehlerdiagnose
            StringBuilder output = new StringBuilder();
            StringBuilder error = new StringBuilder();
            
            // Threads für paralleles Auslesen von Output und Error
            Thread outputThread = new Thread(() -> {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                } catch (IOException e) {
                    logger.warn("Fehler beim Lesen des pandoc-Outputs: {}", e.getMessage());
                }
            });
            
            Thread errorThread = new Thread(() -> {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        error.append(line).append("\n");
                    }
                } catch (IOException e) {
                    logger.warn("Fehler beim Lesen des pandoc-Error-Streams: {}", e.getMessage());
                }
            });
            
            outputThread.start();
            errorThread.start();

            // Warten auf Beendigung
            int exitCode = process.waitFor();
            
            // Warten bis beide Threads fertig sind
            try {
                outputThread.join(5000); // Max 5 Sekunden warten
                errorThread.join(5000);
            } catch (InterruptedException e) {
                logger.warn("Threads für pandoc-Output konnten nicht beendet werden");
            }

            // Debug-Ausgabe der pandoc-Befehle und -Ausgabe
            logger.debug("Pandoc-Befehl: {}", String.join(" ", command));
            if (output.length() > 0) {
                logger.debug("Pandoc-Output:\n{}", output.toString());
            }
            if (error.length() > 0) {
                logger.error("Pandoc-Error:\n{}", error.toString());
            }

            // Prüfe ob die Ausgabedatei jetzt existiert und größer als 0 ist
            File resultFile = "html5".equals(format) ?
                new File(finalOutputPath) : outputFile;

            if (resultFile.exists() && resultFile.length() > 0) {
                logger.info("PDF erfolgreich erstellt: {}", resultFile.getAbsolutePath());
                return true;
            } else {
                logger.error("Pandoc-Export fehlgeschlagen - Datei nicht erstellt (Exit-Code: {})", exitCode);
                if (error.length() > 0) {
                    logger.error("Detaillierte Fehlermeldung:\n{}", error.toString());
                }
                
                // Fallback für PDF: Versuche alternative PDF-Engine
                if ("pdf".equals(format) && (exitCode == 43 || exitCode == 47)) {
                    logger.warn("XeLaTeX fehlgeschlagen (Exit-Code: {}), versuche Fallback mit pdflatex...", exitCode);
                    if (error.length() > 0) {
                        logger.warn("XeLaTeX-Fehlerdetails:\n{}", error.toString());
                    }
                    return tryPdfFallback(markdownFile, outputFile, command);
                }
                
                return false;
            }
            
        } catch (Exception e) {
            logger.error("Fehler beim Pandoc-Export", e);
            return false;
        }
    }
    
    /**
     * Fallback-Methode für PDF-Export bei XeLaTeX-Fehlern
     */
    private boolean tryPdfFallback(File markdownFile, File outputFile, List<String> originalCommand) {
        try {
            // Neuen Befehl mit pdflatex statt xelatex erstellen (ohne Template)
            List<String> fallbackCommand = new ArrayList<>();
            fallbackCommand.add(originalCommand.get(0)); // pandoc.exe
            fallbackCommand.add(originalCommand.get(1)); // input file
            fallbackCommand.add("-o");
            fallbackCommand.add("\"" + outputFile.getAbsolutePath() + "\"");
            fallbackCommand.add("--from=markdown-yaml_metadata_block");
            fallbackCommand.add("--to=pdf");
            fallbackCommand.add("--pdf-engine=pdflatex");
            fallbackCommand.add("--toc");
            fallbackCommand.add("--standalone");
            
            // Vereinfachte LaTeX-Optionen für bessere Kompatibilität (ohne XeLaTeX-spezifische Fonts)
            fallbackCommand.add("--variable=lang:de");
            fallbackCommand.add("--variable=geometry:margin=2.5cm");
            fallbackCommand.add("--variable=fontsize:11pt");
            fallbackCommand.add("--variable=documentclass:article");
            fallbackCommand.add("--variable=linestretch:1.2");
            
            // pdflatex-spezifische Optionen
            fallbackCommand.add("--pdf-engine-opt=-interaction=nonstopmode");
            
            logger.info("Versuche PDF-Fallback mit pdflatex (ohne Template)...");
            
            ProcessBuilder pb = new ProcessBuilder(fallbackCommand);
            pb.directory(pandocHome != null ? pandocHome : new File("pandoc-3.8.1"));
            pb.environment().put("PATH", System.getenv("PATH"));
            
            Process process = pb.start();
            int exitCode = process.waitFor();
            
            if (outputFile.exists() && outputFile.length() > 0) {
                logger.info("PDF-Fallback erfolgreich mit pdflatex erstellt: {}", outputFile.getAbsolutePath());
                return true;
            } else {
                logger.error("PDF-Fallback mit pdflatex ebenfalls fehlgeschlagen (Exit-Code: {})", exitCode);
                return false;
            }
            
        } catch (Exception e) {
            logger.error("Fehler beim PDF-Fallback", e);
            return false;
        }
    }
    
    /**
     * Prüft, ob pandoc.exe verfügbar ist. Wenn nicht, versucht es, die Datei pandoc.zip
     * aus dem Programmverzeichnis zu entpacken. Gibt true zurück, wenn pandoc.exe danach existiert.
     */
    private boolean ensurePandocAvailable() {
        try {
            // 1) Prüfe Standardpfad
            File pandocExe = new File("pandoc-3.8.1", "pandoc.exe");
            if (pandocExe.exists()) {
                pandocHome = pandocExe.getParentFile();
                return true;
            }

            // 2) Versuche, pandoc.zip in pandoc-3.8.1 zu finden und dort zu entpacken
            File zip = new File("pandoc-3.8.1", "pandoc.zip");
            if (!zip.exists()) {
                // Fallback: im Programmverzeichnis
                zip = new File("pandoc.zip");
            }
            if (!zip.exists()) {
                logger.warn("pandoc.zip nicht gefunden – kann Pandoc nicht automatisch installieren");
                return false;
            }

            // Zielordner ist pandoc-3.8.1
            File targetDir = new File("pandoc-3.8.1");
            if (!targetDir.exists()) targetDir.mkdirs();
            boolean ok = unzip(zip, targetDir);
            if (!ok) {
                logger.error("Entpacken von pandoc.zip fehlgeschlagen");
                return false;
            }

            // Nach dem Entpacken erneut prüfen
            pandocExe = new File("pandoc-3.8.1", "pandoc.exe");
            if (pandocExe.exists()) {
                pandocHome = pandocExe.getParentFile();
                logger.info("Pandoc erfolgreich entpackt: {}", pandocExe.getAbsolutePath());
                return true;
            }

            // Fallback: Manche ZIPs enthalten einen Unterordner – versuche zu finden
            File[] candidates = new File(".").listFiles((dir, name) -> name.toLowerCase().startsWith("pandoc"));
            if (candidates != null) {
                for (File c : candidates) {
                    File exe = new File(c, "pandoc.exe");
                    if (exe.exists()) {
                        pandocHome = c;
                        logger.info("Pandoc in '{}' gefunden", c.getAbsolutePath());
                        return true;
                    }
                }
            }

            return false;
        } catch (Exception e) {
            logger.error("Fehler beim Prüfen/Installieren von Pandoc", e);
            return false;
        }
    }

    private boolean unzip(File zipFile, File destDir) {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                File outFile = new File(destDir, entry.getName());
                if (entry.isDirectory()) {
                    if (!outFile.exists() && !outFile.mkdirs()) {
                        logger.warn("Konnte Verzeichnis nicht erstellen: {}", outFile.getAbsolutePath());
                    }
                } else {
                    File parent = outFile.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs()) {
                        logger.warn("Konnte Verzeichnis nicht erstellen: {}", parent.getAbsolutePath());
                    }
                    try (FileOutputStream fos = new FileOutputStream(outFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
            return true;
        } catch (Exception e) {
            logger.error("Fehler beim Entpacken von {}", zipFile.getName(), e);
            return false;
        }
    }
    
    private String getOutputFormat() {
        String format = formatComboBox.getValue();
        switch (format) {
            case "docx": return "docx";
            case "odt": return "odt";
            case "epub": return "epub";
            case "epub3": return "epub3";
            case "html": return "html";
            case "html5": return "html5";
            case "rtf": return "rtf";
            case "pdf": return "pdf";
            case "latex": return "latex";
            default: return "docx";
        }
    }
    
    private void updateFormatSpecificFields() {
        String format = formatComboBox.getValue();

        // Template-Felder für EPUB3 und HTML5 ausblenden
        boolean showTemplate = !format.equals("epub3") && !format.equals("html5");
        templateBox.setVisible(showTemplate);
        templateBox.setManaged(showTemplate);
        templateDescription.setVisible(showTemplate);
        templateDescription.setManaged(showTemplate);

        // Cover-Bild für EPUB3 und HTML5 anzeigen
        boolean showCover = format.equals("epub3") || format.equals("html5");
        coverImageBox.setVisible(showCover);
        coverImageBox.setManaged(showCover);
    }
    
    private void browseCoverImage() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Cover-Bild auswählen");
        chooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Bilddateien", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"),
            new FileChooser.ExtensionFilter("Alle Dateien", "*.*")
        );
        
        File selectedFile = chooser.showOpenDialog(this);
        if (selectedFile != null) {
            coverImageField.setText(selectedFile.getAbsolutePath());
        }
    }
    
    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0 && lastDot < fileName.length() - 1) {
            return fileName.substring(lastDot + 1).toLowerCase();
        }
        return "png"; // Fallback
    }
    
    private void updateFileNameExtension() {
        String format = formatComboBox.getValue();
        String currentFileName = fileNameField.getText();
        
        // Aktuelle Dateiendung entfernen
        String baseName = currentFileName;
        int lastDot = currentFileName.lastIndexOf('.');
        if (lastDot > 0) {
            baseName = currentFileName.substring(0, lastDot);
        }
        
        // Neue Endung hinzufügen
        String newExtension = getFileExtensionForFormat(format);
        fileNameField.setText(baseName + "." + newExtension);
    }
    
    private String getFileExtensionForFormat(String format) {
        switch (format) {
            case "docx": return "docx";
            case "epub3": return "epub";
            case "html5": return "html";
            case "rtf": return "rtf";
            case "pdf": return "pdf";
            case "latex": return "tex";
            default: return "docx";
        }
    }
    
    private void addCoverImageToYaml(File yamlFile, String coverFileName) {
        // Diese Methode wird nicht mehr verwendet, da wir --epub-cover-image verwenden
        // YAML wird nicht mehr für Cover-Bild verwendet
    }
    
    private String createHtmlTemplate(File htmlDir, String htmlFileName) {
        try {
            // HTML-Template-Datei erstellen
            File templateFile = new File(htmlDir, "template.html");

            // Basis-HTML-Template mit Cover-Bild-Unterstützung
            String template = """
                <!DOCTYPE html>
                <html xmlns="http://www.w3.org/1999/xhtml" lang="$lang$" xml:lang="$lang$">
                <head>
                  <meta charset="utf-8" />
                  <meta name="generator" content="pandoc" />
                  <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=yes" />
                  $for(author-meta)$
                  <meta name="author" content="$author-meta$" />
                  $endfor$
                  $if(date-meta)$
                  <meta name="dcterms.date" content="$date-meta$" />
                  $endif$
                  $if(keywords)$
                  <meta name="keywords" content="$for(keywords)$$keywords$$sep$, $endfor$" />
                  $endif$
                  $if(title)$
                  <title>$title$</title>
                  $endif$
                  $for(css)$
                  <link rel="stylesheet" href="$css$" />
                  $endfor$
                  $if(math)$
                  $math$
                  $endif$
                  <style>
                    .cover-image {
                      max-width: 100%;
                      height: auto;
                      display: block;
                      margin: 2em auto;
                      box-shadow: 0 4px 8px rgba(0,0,0,0.1);
                    }
                  </style>
                </head>
                <body>
                  $if(cover-image)$
                  <div style="text-align: center; margin: 2em 0;">
                    <img src="$cover-image$" alt="Cover" class="cover-image" />
                  </div>
                  $endif$
                  $body$
                </body>
                </html>
                """;

            // Template in Datei schreiben
            Files.write(templateFile.toPath(), template.getBytes(StandardCharsets.UTF_8));

            return templateFile.getAbsolutePath();

        } catch (IOException e) {
            logger.error("Fehler beim Erstellen des HTML-Templates", e);
            return null;
        }
    }

    private void showAlert(String title, String message) {
        CustomAlert alert = new CustomAlert(Alert.AlertType.INFORMATION, title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.applyTheme(currentThemeIndex);
        alert.initOwner(this);
        alert.showAndWait();
    }
}
