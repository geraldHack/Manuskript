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

// Apache POI für DOCX-Bearbeitung
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.util.Units;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import com.manuskript.HelpSystem;

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
    private CheckBox initialsCheckBox;
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
        
        // Nur Padding für root, Border kommt auf outerContainer
        root.setStyle("-fx-padding: 5px;");
        
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

        // Hilfebutton für Template-System
        Button templateHelpButton = HelpSystem.createHelpButton(
            "Was ist das Template-System?",
            "template_system.html"
        );
        
        templateBox.getChildren().addAll(templateLabel, templateComboBox, templateHelpButton);
        
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
        
        // Initialen-Checkbox (nur für DOCX) - separater Bereich
        initialsCheckBox = new CheckBox("Initialen hinzufügen");
        initialsCheckBox.setSelected(true); // Standardmäßig aktiviert
        initialsCheckBox.setTooltip(new Tooltip("Fügt Initialen zu den ersten Absätzen nach Headern hinzu"));
        
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
            initialsCheckBox,
            new Separator(),
            metadataBox,
            new Separator(),
            outputBox,
            buttonBox
        );
        
        // Wrapper mit Padding für äußeren Abstand
        StackPane wrapper = new StackPane();
        wrapper.setPadding(new Insets(10)); // 10px Abstand zum Fensterrand
        wrapper.setStyle("-fx-padding: 10;"); // CSS-Überschreibung verhindern
        
        // Border auf den root setzen
        root.setStyle("-fx-border-width: 1px; -fx-border-radius: 5px;");
        
        wrapper.getChildren().add(root);
        
        setSceneWithTitleBar(new Scene(wrapper));
        centerOnScreen();

        // Titel sicherstellen nach dem Erstellen der Szene
        setTitle("Buch exportieren - " + projectName);
        
        // Theme-spezifische Border-Farbe setzen
        applyThemeBorder(root);
        
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
    
    /**
     * Wendet eine theme-spezifische Border-Farbe auf den Root-Container an
     */
    private void applyThemeBorder(VBox root) {
        String borderColor;
        switch (currentThemeIndex) {
            case 0: // Weiß
                borderColor = "#cccccc";
                break;
            case 1: // Schwarz
                borderColor = "#404040";
                break;
            case 2: // Pastell
                borderColor = "#ba68c8";
                break;
            case 3: // Blau
                borderColor = "#1d4ed8";
                break;
            case 4: // Grün
                borderColor = "#047857";
                break;
            case 5: // Lila
                borderColor = "#6d28d9";
                break;
            default:
                borderColor = "#404040";
        }
        
        String currentStyle = root.getStyle();
        if (currentStyle == null) {
            currentStyle = "";
        }
        root.setStyle(currentStyle + " -fx-border-color: " + borderColor + ";");
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
        // Hilfebutton für YAML-Metadaten
        Button titleHelpButton = HelpSystem.createHelpButton(
            "Was sind YAML-Metadaten?",
            "yaml_metadata.html"
        );
        
        titleBox.getChildren().addAll(titleLabel, titleField, titleHelpButton);
        
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
                // Fehler-Dialog mit Hilfebutton
                showErrorWithHelp("Export fehlgeschlagen. Siehe Logs für Details.");
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
                
                // Original Markdown-Inhalt hinzufügen
                String originalContent = Files.readString(inputMarkdownFile.toPath());
                // Schreibe den Inhalt direkt, ohne zusätzliche Zeilenumbrüche
                writer.print(originalContent);
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
    
    /**
     * Ersetzt HTML-Tags in der Markdown-Datei durch format-spezifische Befehle
     */
    private void replaceHtmlTagsInMarkdown(File markdownFile, String format) {
        try {
            String content = Files.readString(markdownFile.toPath(), StandardCharsets.UTF_8);
            String originalContent = content;
            
            // HTML-Tags ersetzen basierend auf dem Ausgabeformat
            if ("pdf".equals(format)) {
                // Für PDF: LaTeX-Befehle verwenden
                content = content.replaceAll("<u>([^<]+)</u>", "\\\\underline{$1}");
                content = content.replaceAll("<b>([^<]+)</b>", "\\\\textbf{$1}");
                content = content.replaceAll("<i>([^<]+)</i>", "\\\\textit{$1}");
                content = content.replaceAll("<strong>([^<]+)</strong>", "\\\\textbf{$1}");
                content = content.replaceAll("<em>([^<]+)</em>", "\\\\textit{$1}");
                content = content.replaceAll("<s>([^<]+)</s>", "\\\\sout{$1}");
                content = content.replaceAll("<del>([^<]+)</del>", "\\\\sout{$1}");
                content = content.replaceAll("<mark>([^<]+)</mark>", "\\\\hl{$1}");
                content = content.replaceAll("<small>([^<]+)</small>", "\\\\small $1");
                content = content.replaceAll("<big>([^<]+)</big>", "\\\\large $1");
                
                // Markdown-Kursiv zu LaTeX-Kursiv konvertieren (wichtig für PDF!)
                // Pandoc macht das automatisch: *text* → \emph{text}
                // Das ist korrekt! \emph{} ist die richtige LaTeX-Formatierung für Kursiv
            } else if ("docx".equals(format)) {
                // Für DOCX: Pandoc-native Befehle verwenden
                content = content.replaceAll("<u>([^<]+)</u>", "[$1]{.underline}");
                content = content.replaceAll("<b>([^<]+)</b>", "**$1**");
                content = content.replaceAll("<i>([^<]+)</i>", "*$1*");
                content = content.replaceAll("<strong>([^<]+)</strong>", "**$1**");
                content = content.replaceAll("<em>([^<]+)</em>", "*$1*");
                content = content.replaceAll("<s>([^<]+)</s>", "~~$1~~");
                content = content.replaceAll("<del>([^<]+)</del>", "~~$1~~");
                content = content.replaceAll("<mark>([^<]+)</mark>", "[$1]{.highlight}");
                content = content.replaceAll("<small>([^<]+)</small>", "[$1]{.small}");
                content = content.replaceAll("<big>([^<]+)</big>", "[$1]{.large}");
            } else if ("epub3".equals(format) || "html5".equals(format) || "epub".equals(format) || "html".equals(format)) {
                // Für EPUB/HTML: HTML-Tags beibehalten
                // Keine Ersetzung nötig
            }
            
            // Nur schreiben wenn sich etwas geändert hat
            if (!content.equals(originalContent)) {
                Files.write(markdownFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            logger.error("Fehler beim Ersetzen von HTML-Tags: {}", e.getMessage());
        }
    }
    
    private boolean runPandocExport(File markdownFile) {
        try {
            // Sicherstellen, dass Pandoc verfügbar ist
            if (!ensurePandocAvailable()) {
                showAlert("Pandoc fehlt", "Pandoc konnte nicht gefunden oder installiert werden. Bitte stellen Sie sicher, dass 'pandoc.zip' im Programmverzeichnis liegt.");
                return false;
            }
            
            // HTML-Tags durch format-spezifische Befehle ersetzen
            String format = formatComboBox.getValue();
            if (format != null) {
                replaceHtmlTagsInMarkdown(markdownFile, format);
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
            // Reference-DOC temporär deaktiviert, da es Probleme verursacht
            // if (templateFile != null) {
                command.add("--reference-doc=\"" + templateFile.getAbsolutePath() + "\"");
            // }
                command.add("--highlight-style=tango");
                command.add("--reference-links");
                
                // Cover-Bild für DOCX hinzufügen (falls vorhanden)
                if (!coverImageField.getText().trim().isEmpty()) {
                    File coverImageFile = new File(coverImageField.getText().trim());
                    if (coverImageFile.exists()) {
                        try {
                            // Cover-Bild ins pandoc-Verzeichnis kopieren
                            String coverFileName = "cover." + getFileExtension(coverImageFile.getName());
                            File pandocDir = new File("pandoc-3.8.1");
                            File targetCover = new File(pandocDir, coverFileName);
                            Files.copy(coverImageFile.toPath(), targetCover.toPath(),
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            
                        // Cover-Bild wird im Post-Processing hinzugefügt, nicht über Pandoc
                            
                        } catch (IOException e) {
                            logger.error("Fehler beim Kopieren des Cover-Bildes für DOCX", e);
                        }
                    }
                }
                
                // Post-Processing für DOCX: Abstract-Titel ersetzen
                // Das wird nach dem Pandoc-Export durchgeführt
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
                            
                            // Cover-Bild als Pandoc-Variable übergeben (relativer Pfad für HTML)
                            command.add("--variable=cover-image:" + targetCover.getName());

                        } catch (IOException e) {
                            logger.error("Fehler beim Kopieren des Cover-Bildes für HTML", e);
                        }
                    } else {
                        logger.warn("Cover-Bild existiert nicht: {}", coverImageFile.getAbsolutePath());
                    }
                }
            } else if ("pdf".equals(format)) {
                // PDF-spezifische Optionen für professionelle Formatierung
                command.add("--pdf-engine=xelatex");
                command.add("--toc"); // Inhaltsverzeichnis für PDF
                
                // Markdown-Formatierung explizit aktivieren
                command.add("--from=markdown+yaml_metadata_block+smart");
                command.add("--to=latex");
                
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
                
                // Cover-Bild für PDF hinzufügen (falls vorhanden)
                if (!coverImageField.getText().trim().isEmpty()) {
                    File coverImageFile = new File(coverImageField.getText().trim());
                    if (coverImageFile.exists()) {
                        try {
                            // Cover-Bild ins pandoc-Verzeichnis kopieren
                            String coverFileName = "cover." + getFileExtension(coverImageFile.getName());
                            File pandocDir = new File("pandoc-3.8.1");
                            File targetCover = new File(pandocDir, coverFileName);
                            Files.copy(coverImageFile.toPath(), targetCover.toPath(),
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            
                            // Cover-Bild als LaTeX-Variable übergeben
                            command.add("--variable=cover-image:" + targetCover.getName());
                            
                        } catch (IOException e) {
                            logger.error("Fehler beim Kopieren des Cover-Bildes für PDF", e);
                        }
                    }
                }
                
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

            if (error.length() > 0) {
                logger.error("Pandoc-Error:\n{}", error.toString());
            }

            // Prüfe ob die Ausgabedatei jetzt existiert und größer als 0 ist
            File resultFile = "html5".equals(format) ?
                new File(finalOutputPath) : outputFile;

            if (resultFile.exists() && resultFile.length() > 0) {
                logger.info("Export erfolgreich erstellt: {}", resultFile.getAbsolutePath());
                
                // Post-Processing für DOCX: Abstract-Titel ersetzen und Cover-Bild hinzufügen
                if ("docx".equals(format)) {
                    postProcessDocx(resultFile);
                }
                
                
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

        // Template-Felder für EPUB3, HTML5 und PDF ausblenden
        boolean showTemplate = !format.equals("epub3") && !format.equals("html5") && !format.equals("pdf");
        templateBox.setVisible(showTemplate);
        templateBox.setManaged(showTemplate);
        templateDescription.setVisible(showTemplate);
        templateDescription.setManaged(showTemplate);

        // Cover-Bild für EPUB3 und HTML5 anzeigen
        boolean showCover = format.equals("epub3") || format.equals("html5");
        coverImageBox.setVisible(showCover);
        coverImageBox.setManaged(showCover);
        
        // Initialen-Checkbox nur für DOCX anzeigen
        updateInitialsVisibility();
    }
    
    private void updateInitialsVisibility() {
        String format = formatComboBox.getValue();
        boolean showInitials = "docx".equals(format);
        initialsCheckBox.setVisible(showInitials);
        initialsCheckBox.setManaged(showInitials);
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
                    /* Text-Offset für bessere Lesbarkeit */
                    body {
                      margin: 0;
                      padding: 2em 4em;
                      max-width: 1200px;
                      margin-left: auto;
                      margin-right: auto;
                    }
                    
                    .cover-image {
                      max-width: 100%;
                      height: auto;
                      display: block;
                      margin: 2em auto;
                      box-shadow: 0 4px 8px rgba(0,0,0,0.1);
                    }
                    
                    /* Schöne Tabellen-Styles */
                    table {
                      border-collapse: collapse;
                      width: auto;
                      margin: 1em 0;
                      font-family: Arial, sans-serif;
                      box-shadow: 0 2px 4px rgba(0,0,0,0.1);
                      max-width: 80%;
                    }
                    
                    th, td {
                      border: 1px solid #ddd;
                      padding: 12px;
                      text-align: left;
                    }
                    
                    th {
                      background-color: #f8f9fa;
                      font-weight: bold;
                      color: #333;
                    }
                    
                    tr:nth-child(even) {
                      background-color: #f8f9fa;
                    }
                    
                    tr:hover {
                      background-color: #e9ecef;
                    }
                    
                    /* Responsive Tabellen */
                    @media (max-width: 768px) {
                      table {
                        font-size: 14px;
                      }
                      th, td {
                        padding: 8px;
                      }
                    }
                    
                    /* Titelseite Styles */
                    .title-page {
                      text-align: center;
                      margin: 4em 0;
                      padding: 2em;
                      border-bottom: 2px solid #ddd;
                    }
                    
                    .title {
                      font-size: 2.5em;
                      font-weight: bold;
                      color: #333;
                      margin-bottom: 0.5em;
                      line-height: 1.2;
                    }
                    
                    .subtitle {
                      font-size: 1.5em;
                      color: #666;
                      margin-bottom: 1em;
                      font-style: italic;
                    }
                    
                    .author {
                      font-size: 1.2em;
                      color: #555;
                      margin-bottom: 0.5em;
                      text-align: center;
                    }
                    
                    .date {
                      font-size: 1em;
                      color: #777;
                      margin-bottom: 0;
                      text-align: center;
                    }
                    
                    .abstract {
                      margin-top: 2em;
                      padding: 1.5em;
                      background-color: #f8f9fa;
                      border-left: 4px solid #007bff;
                      text-align: left;
                      max-width: 600px;
                      margin-left: auto;
                      margin-right: auto;
                    }
                    
                    .abstract h3 {
                      margin-top: 0;
                      margin-bottom: 1em;
                      color: #333;
                      font-size: 1.2em;
                    }
                    
                    .abstract p {
                      margin: 0;
                      line-height: 1.6;
                      color: #555;
                    }
                  </style>
                </head>
                <body>
                  $if(cover-image)$
                  <div style="text-align: center; margin: 2em 0;">
                    <img src="$cover-image$" alt="Cover" class="cover-image" />
                  </div>
                  $endif$
                  
                  <!-- Titelseite -->
                  <div class="title-page">
                    $if(title)$
                    <h1 class="title">$title$</h1>
                    $endif$
                    $if(subtitle)$
                    <h2 class="subtitle">$subtitle$</h2>
                    $endif$
                    $if(author)$
                    <p class="author">$for(author)$$author$$sep$, $endfor$</p>
                    $endif$
                    $if(date)$
                    <p class="date">$date$</p>
                    $endif$
                    $if(abstract)$
                    <div class="abstract">
                      <h3>Zusammenfassung</h3>
                      <p>$abstract$</p>
                    </div>
                    $endif$
                  </div>
                  
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
    
    private void showErrorWithHelp(String message) {
        CustomAlert alert = new CustomAlert(Alert.AlertType.ERROR, "Fehler");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.applyTheme(currentThemeIndex);
        alert.initOwner(this);
        
        // Hilfebutton hinzufügen
        ButtonType helpButtonType = new ButtonType("Hilfe", ButtonBar.ButtonData.HELP);
        alert.getButtonTypes().add(helpButtonType);
        
        alert.showAndWait().ifPresent(buttonType -> {
            if (buttonType == helpButtonType) {
                HelpSystem.showHelpWindow("pdf_export_failed.html");
            }
        });
    }
    
    /**
     * Post-Processing für DOCX: Ersetzt "Abstract" durch "Zusammenfassung" und fügt Cover-Bild hinzu
     */
    
    private void postProcessDocx(File docxFile) {
        try {
            logger.info("Post-Processing für DOCX: {}", docxFile.getName());
            
            // DOCX mit Apache POI öffnen und bearbeiten
            try (FileInputStream fis = new FileInputStream(docxFile);
                 XWPFDocument document = new XWPFDocument(fis)) {
                
                // ZUERST: "Abstract" durch "Zusammenfassung" ersetzen
                for (XWPFParagraph paragraph : document.getParagraphs()) {
                    for (XWPFRun run : paragraph.getRuns()) {
                        String text = run.getText(0);
                        if (text != null && text.contains("Abstract")) {
                            String newText = text.replace("Abstract", "Zusammenfassung");
                            run.setText(newText, 0);
                        }
                    }
                }
                
                // Alle Tabellen durchgehen
                for (XWPFTable table : document.getTables()) {
                    for (XWPFTableRow row : table.getRows()) {
                        for (XWPFTableCell cell : row.getTableCells()) {
                            for (XWPFParagraph paragraph : cell.getParagraphs()) {
                                for (XWPFRun run : paragraph.getRuns()) {
                                    String text = run.getText(0);
                                    if (text != null && text.contains("Abstract")) {
                                        String newText = text.replace("Abstract", "Zusammenfassung");
                                        run.setText(newText, 0);
                                    }
                                }
                            }
                        }
                    }
                }
                
                // DANN: Initialen für "First Paragraph" Absätze hinzufügen (nur wenn aktiviert)
                if (initialsCheckBox != null && initialsCheckBox.isSelected()) {
                    logger.debug("Initialen-Checkbox ist aktiviert - füge Initialen hinzu");
                    addInitialsToFirstParagraphs(document);
                } else {
                    logger.debug("Initialen-Checkbox ist deaktiviert - überspringe Initialen");
                }
                
                // DANN: Cover-Bild hinzufügen (falls vorhanden)
                if (!coverImageField.getText().trim().isEmpty()) {
                    File coverImageFile = new File(coverImageField.getText().trim());
                    if (coverImageFile.exists()) {
                        try {
                            // Cover-Bild am Anfang einfügen (ohne Dokument neu aufzubauen)
                            // Cursor an den Anfang des Dokuments setzen
                            org.apache.xmlbeans.XmlCursor cursor = document.getDocument().getBody().newCursor();
                            cursor.toFirstChild();
                            
                            // Neuen Absatz am Anfang einfügen
                            XWPFParagraph coverParagraph = document.insertNewParagraph(cursor);
                            coverParagraph.setAlignment(ParagraphAlignment.CENTER);
                            
                            XWPFRun coverRun = coverParagraph.createRun();
                            
                            // Bildtyp basierend auf Dateiendung bestimmen
                            int pictureType = XWPFDocument.PICTURE_TYPE_PNG;
                            String fileName = coverImageFile.getName().toLowerCase();
                            if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
                                pictureType = XWPFDocument.PICTURE_TYPE_JPEG;
                            } else if (fileName.endsWith(".gif")) {
                                pictureType = XWPFDocument.PICTURE_TYPE_GIF;
                            }
                            
                            // Bild einfügen - bessere Proportionen (nicht gequetscht)
                            coverRun.addPicture(
                                new FileInputStream(coverImageFile),
                                pictureType,
                                coverImageFile.getName(),
                                Units.toEMU(400), // Breite - nicht zu schmal
                                Units.toEMU(600)  // Höhe - höher für bessere Proportionen
                            );
                            
                            // Seitenumbruch NUR nach dem Cover-Bild (ohne Kapitel-Formatierung zu beeinträchtigen)
                            coverRun.addBreak();
                            coverRun.addBreak(); // Zusätzlicher Umbruch für Seitenumbruch
                            
                            // Leerzeile nach Cover-Bild
                            XWPFParagraph spacerParagraph = document.insertNewParagraph(cursor);
                            spacerParagraph.createRun().addBreak();
                            
                            // Cursor schließen
                            cursor.dispose();
                            
                            logger.info("Cover-Bild in DOCX eingefügt: {}", coverImageFile.getName());
                            
                        } catch (Exception e) {
                            logger.warn("Fehler beim Einfügen des Cover-Bildes: {}", e.getMessage());
                        }
                    }
                }
                
                // Dokument speichern
                try (FileOutputStream fos = new FileOutputStream(docxFile)) {
                    document.write(fos);
                }
            }
            
            logger.info("DOCX Post-Processing abgeschlossen - 'Abstract' durch 'Zusammenfassung' ersetzt und Cover-Bild hinzugefügt");
            
        } catch (Exception e) {
            logger.warn("DOCX Post-Processing fehlgeschlagen: {}", e.getMessage());
        }
    }
    
    /**
     * Fügt Initialen (Drop Caps) zu Absätzen mit "First Paragraph" Format hinzu
     */
    private void addInitialsToFirstParagraphs(XWPFDocument document) {
        try {
            logger.info("Suche nach Headern in DOCX und füge Initialen zu den ersten Absätzen hinzu...");
            
            int processedCount = 0;
            boolean foundHeader = false;
            boolean isFirstHeader = true; // Flag für den ersten Header (Titel)
            
            // Alle Absätze durchgehen
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String text = paragraph.getText();
                if (text != null && !text.trim().isEmpty()) {
                    String firstLine = text.split("\n")[0].trim();
                    
                    // Prüfe ob es ein Header ist (Word-Formatierung)
                    // Header haben meist Heading-Styles oder sind fett formatiert
                    boolean isHeader = false;
                    
                    // Prüfe auf Heading-Style
                    String styleName = paragraph.getStyle();
                    if (styleName != null && (styleName.contains("Heading") || styleName.contains("Title"))) {
                        isHeader = true;
                    }
                    
                    // Prüfe auf fett formatierte kurze Zeilen (wahrscheinlich Header)
                    if (!isHeader && firstLine.length() < 100) {
                        for (XWPFRun run : paragraph.getRuns()) {
                            if (run.isBold() && run.getText(0) != null && !run.getText(0).trim().isEmpty()) {
                                isHeader = true;
                                break;
                            }
                        }
                    }
                    
                    if (isHeader) {
                        if (isFirstHeader) {
                            logger.info("Erster Header (Titel) gefunden - überspringe: '{}' (Style: {})", firstLine, styleName);
                            isFirstHeader = false; // Nach dem ersten Header sind alle anderen gültig
                        } else {
                            foundHeader = true;
                            logger.info("Header gefunden: '{}' (Style: {})", firstLine, styleName);
                        }
                        continue; // Überspringe den Header selbst
                    }
                    
                    // Wenn wir nach einem Header sind, prüfe auf den ersten Absatz
                    if (foundHeader) {
                        // Einfache Prüfung: Ist es ein normaler Absatz
                        if (firstLine.length() > 20) {
                            logger.info("Erster Absatz nach Header gefunden - füge Initialen hinzu: '{}'", firstLine.substring(0, Math.min(50, firstLine.length())));
                            addInitialsToParagraph(paragraph);
                            processedCount++;
                            foundHeader = false; // Nur den ersten Absatz nach Header
                        }
                    }
                }
            }
            
            logger.info("Initialen-Verarbeitung abgeschlossen - {} Absätze bearbeitet", processedCount);
            
        } catch (Exception e) {
            logger.warn("Fehler beim Hinzufügen von Initialen: {}", e.getMessage());
        }
    }
    
    /**
     * Prüft ob ein Absatz ein guter Kandidat für Initialen ist
     */
    private boolean isGoodInitialsCandidate(XWPFParagraph paragraph) {
        try {
            String text = paragraph.getText();
            if (text == null || text.trim().isEmpty()) {
                return false;
            }
            
            // Erste Zeile des Absatzes
            String firstLine = text.split("\n")[0].trim();
            if (firstLine.length() < 10) {
                return false; // Zu kurz für Initialen
            }
            
            // Einfache Kriterien für Initialen-Kandidaten:
            // 1. Beginnt mit Großbuchstaben
            // 2. Ist nicht eine Liste oder spezielle Formatierung
            // 3. Ist lang genug für einen echten Absatz
            if (Character.isUpperCase(firstLine.charAt(0))) {
                if (!firstLine.startsWith("*") &&
                    !firstLine.startsWith("-") &&
                    !firstLine.startsWith(">") &&
                    !firstLine.startsWith("|") &&
                    !firstLine.matches("^\\d+\\.") &&
                    firstLine.length() > 20) { // Mindestens 20 Zeichen für bessere Erkennung
                    
                    logger.debug("Guter Initialen-Kandidat: '{}'", firstLine.substring(0, Math.min(40, firstLine.length())));
                    return true;
                }
            }
            
            return false;
        } catch (Exception e) {
            logger.debug("Fehler beim Prüfen des Absatzes: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Fügt Initialen zu einem Absatz hinzu
     */
    private void addInitialsToParagraph(XWPFParagraph paragraph) {
        try {
            String text = paragraph.getText();
            if (text == null || text.trim().isEmpty()) {
                logger.debug("Absatz ist leer - überspringe Initialen");
                return;
            }
            
            // Erste Zeile des Absatzes
            String firstLine = text.split("\n")[0].trim();
            if (firstLine.length() == 0) {
                logger.debug("Erste Zeile ist leer - überspringe Initialen");
                return;
            }
            
            logger.debug("Verarbeite Absatz für Initialen: '{}'", firstLine.substring(0, Math.min(30, firstLine.length())));
            
            // Ersten Buchstaben extrahieren
            char firstChar = firstLine.charAt(0);
            String restOfText = firstLine.substring(1);
            
            // Bestehende Runs löschen - sicherere Methode
            try {
                // Alle bestehenden Runs durchgehen und Text löschen
                for (XWPFRun run : paragraph.getRuns()) {
                    run.setText("", 0);
                }
            } catch (Exception e) {
                logger.debug("Fehler beim Löschen bestehender Runs: {}", e.getMessage());
            }
            
            // Initiale (großer Buchstabe) erstellen
            XWPFRun initialRun = paragraph.createRun();
            if (initialRun != null) {
                initialRun.setText(String.valueOf(firstChar));
                initialRun.setFontSize(48); // Größere Schrift für bessere Sichtbarkeit
                initialRun.setBold(true);
                initialRun.setColor("000000"); // Schwarz für maximale Sichtbarkeit
                initialRun.setFontFamily("Times New Roman"); // Serifenschrift für Initiale
                initialRun.setItalic(false);
            }
            
            // Rest des Textes
            if (!restOfText.isEmpty()) {
                XWPFRun restRun = paragraph.createRun();
                if (restRun != null) {
                    restRun.setText(restOfText);
                    restRun.setFontSize(12); // Normale Schriftgröße
                    restRun.setFontFamily("Times New Roman");
                }
            }
            
            // Absatz-Formatierung für Initialen
            paragraph.setSpacingAfter(200); // Abstand nach dem Absatz
            paragraph.setSpacingBefore(0);
            
            logger.info("Initialen erfolgreich hinzugefügt für Absatz: '{}'", firstLine.substring(0, Math.min(50, firstLine.length())));
            
        } catch (Exception e) {
            logger.warn("Fehler beim Hinzufügen von Initialen zu Absatz: {}", e.getMessage());
            if (e.getCause() != null) {
                logger.warn("Ursache: {}", e.getCause().getMessage());
            }
        }
    }
}
