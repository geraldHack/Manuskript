package com.manuskript;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.concurrent.Task;
import javafx.application.Platform;
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
import java.util.HashMap;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

public class PandocExportWindow extends CustomStage {
    private static final Logger logger = LoggerFactory.getLogger(PandocExportWindow.class);
    
    // Flag für Export-Status (verhindert, dass CustomStage den Cursor überschreibt)
    private boolean isExporting = false;
    
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
    
    // DOCX-spezifische Felder
    private HBox imageSizeBox;
    private Slider imageSizeSlider;
    private Label imageSizeLabel;
    
    // Template-Felder
    private HBox templateBox;
    
    // Data
    private File inputMarkdownFile;
    private List<File> referenceTemplates;
    private String projectName;
    private Preferences preferences;
    private int currentThemeIndex;
    private File pandocHome; // Ordner, in dem sich pandoc.exe befindet
    private File projectDirectory; // Projekt-Verzeichnis für Metadaten-Speicherung
    private String lastExportError; // Letzte Fehlermeldung vom Export
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    public PandocExportWindow(File inputMarkdownFile, String projectName) {
        super();
        this.inputMarkdownFile = inputMarkdownFile;
        this.projectName = projectName;
        this.projectDirectory = inputMarkdownFile != null ? inputMarkdownFile.getParentFile() : null;
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
        
        // Cover Image für EPUB, HTML und DOCX
        coverImageBox = new HBox(10);
        coverImageBox.setAlignment(Pos.CENTER_LEFT);
        Label coverImageLabel = new Label("Cover-Bild:");
        coverImageLabel.setPrefWidth(100);
        coverImageField = new TextField();
        coverImageField.setPromptText("Pfad zum Cover-Bild");
        coverImageField.setPrefWidth(400);
        coverImageBrowseButton = new Button("📁");
        coverImageBrowseButton.getStyleClass().add("dialog-button-icon");
        coverImageBrowseButton.setOnAction(e -> browseCoverImage());
        coverImageBox.getChildren().addAll(coverImageLabel, coverImageField, coverImageBrowseButton);
        
        // Initialen-Checkbox (nur für DOCX) - separater Bereich
        initialsCheckBox = new CheckBox("Initialen hinzufügen");
        initialsCheckBox.setSelected(true); // Standardmäßig aktiviert
        initialsCheckBox.setTooltip(new Tooltip("Fügt Initialen zu den ersten Absätzen nach Headern hinzu"));
        
        // Bildgröße für DOCX (50-100%)
        imageSizeBox = new HBox(10);
        imageSizeBox.setAlignment(Pos.CENTER_LEFT);
        Label imageSizeLabelText = new Label("Bildgröße:");
        imageSizeLabelText.setPrefWidth(100);
        imageSizeSlider = new Slider(50, 100, 50);
        imageSizeSlider.setShowTickLabels(true);
        imageSizeSlider.setShowTickMarks(true);
        imageSizeSlider.setMajorTickUnit(10);
        imageSizeSlider.setMinorTickCount(5);
        imageSizeSlider.setSnapToTicks(false);
        imageSizeSlider.setPrefWidth(300);
        imageSizeLabel = new Label("50%");
        imageSizeLabel.setPrefWidth(50);
        imageSizeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            int value = (int) Math.round(newVal.doubleValue());
            imageSizeLabel.setText(value + "%");
            // Metadaten speichern bei Änderung
            saveProjectMetadata();
        });
        imageSizeBox.getChildren().addAll(imageSizeLabelText, imageSizeSlider, imageSizeLabel);
        imageSizeBox.setVisible(false); // Initial ausgeblendet, wird bei DOCX angezeigt
        
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
            imageSizeBox,
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
        abstractArea.getStyleClass().add("dialog-textarea");
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
        File pandocDir = (pandocHome != null) ? pandocHome : new File("pandoc");
        
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
            String descriptionFile = "pandoc/reference-" + selectedTemplate + ".txt";
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
    
    /**
     * Gibt das data-Verzeichnis im Projektverzeichnis zurück
     */
    private File getDataDirectory() {
        if (projectDirectory == null) {
            return null;
        }
        File dataDir = new File(projectDirectory, "data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        return dataDir;
    }
    
    /**
     * Gibt den Pfad zur Metadaten-Datei im data-Verzeichnis zurück
     */
    private File getProjectMetadataFile() {
        File dataDir = getDataDirectory();
        if (dataDir == null) {
            return null;
        }
        return new File(dataDir, "pandoc_metadata.json");
    }
    
    /**
     * Lädt Metadaten aus der projekt-spezifischen JSON-Datei
     */
    private void loadProjectMetadata() {
        File metadataFile = getProjectMetadataFile();
        Map<String, String> metadata = new HashMap<>();
        
        // Versuche zuerst aus projekt-spezifischer Datei zu laden
        if (metadataFile != null && metadataFile.exists()) {
            try {
                String json = Files.readString(metadataFile.toPath(), StandardCharsets.UTF_8);
                TypeToken<Map<String, String>> typeToken = new TypeToken<Map<String, String>>(){};
                metadata = gson.fromJson(json, typeToken.getType());
            } catch (IOException e) {
                logger.warn("Fehler beim Laden der Projekt-Metadaten, verwende Fallback: {}", e.getMessage());
            }
        }
        
        // Fallback auf globale Preferences für Migration/Kompatibilität
        titleField.setText(metadata.getOrDefault("title", preferences.get("pandoc_title", projectName)));
        subtitleField.setText(metadata.getOrDefault("subtitle", preferences.get("pandoc_subtitle", "")));
        authorField.setText(metadata.getOrDefault("author", preferences.get("pandoc_author", "Gerald Leonard")));
        rightsField.setText(metadata.getOrDefault("rights", preferences.get("pandoc_rights", "© 2025 Gerald Leonard")));
        dateField.setText(metadata.getOrDefault("date", preferences.get("pandoc_date", "Oktober 2025")));
        outputDirectoryField.setText(metadata.getOrDefault("outputDirectory", preferences.get("pandoc_output_directory", "")));
        abstractArea.setText(metadata.getOrDefault("abstract", preferences.get("pandoc_abstract", "")));
        
        // Cover-Bild laden
        String savedCoverImage = metadata.getOrDefault("coverImage", preferences.get("pandoc_cover_image", ""));
        if (savedCoverImage.isEmpty()) {
            // Automatisch Cover-Bild aus Projektverzeichnis setzen
            if (projectDirectory != null) {
                File projectCover = new File(projectDirectory, "cover_image.png");
                if (projectCover.exists()) {
                    coverImageField.setText(projectCover.getAbsolutePath());
                    logger.debug("Cover-Bild automatisch gesetzt: {}", projectCover.getAbsolutePath());
                } else {
                    coverImageField.setText("");
                }
            } else {
                coverImageField.setText("");
            }
        } else {
            coverImageField.setText(savedCoverImage);
        }
        
        // Format laden
        String savedFormat = metadata.getOrDefault("format", preferences.get("pandoc_format", "docx"));
        formatComboBox.setValue(savedFormat);
        
        // Bildgröße laden (nur für DOCX)
        double savedImageSize = Double.parseDouble(metadata.getOrDefault("imageSize", preferences.get("pandoc_image_size", "50")));
        imageSizeSlider.setValue(savedImageSize);
        imageSizeLabel.setText((int) Math.round(savedImageSize) + "%");
        
        // Template laden
        String savedTemplate = metadata.getOrDefault("template", preferences.get("pandoc_template", ""));
        if (!savedTemplate.isEmpty()) {
            templateComboBox.setValue(savedTemplate);
        }
        
        // Dateiname basierend auf Format setzen
        String fileName = projectName.replaceAll("[^a-zA-Z0-9\\s]", "").replaceAll("\\s+", "_");
        String extension = getFileExtensionForFormat(savedFormat);
        fileNameField.setText(fileName + "." + extension);
    }
    
    /**
     * Speichert Metadaten in die projekt-spezifische JSON-Datei
     */
    private void saveProjectMetadata() {
        File metadataFile = getProjectMetadataFile();
        if (metadataFile == null) {
            return;
        }
        
        try {
            Map<String, String> metadata = new HashMap<>();
            metadata.put("title", titleField.getText().trim());
            metadata.put("subtitle", subtitleField.getText().trim());
            metadata.put("author", authorField.getText().trim());
            metadata.put("rights", rightsField.getText().trim());
            metadata.put("date", dateField.getText().trim());
            metadata.put("outputDirectory", outputDirectoryField.getText().trim());
            metadata.put("abstract", abstractArea.getText().trim());
            metadata.put("coverImage", coverImageField.getText().trim());
            metadata.put("format", formatComboBox.getValue() != null ? formatComboBox.getValue() : "docx");
            metadata.put("template", templateComboBox.getValue() != null ? templateComboBox.getValue() : "");
            metadata.put("imageSize", String.valueOf((int) Math.round(imageSizeSlider.getValue())));
            
            String json = gson.toJson(metadata);
            Files.writeString(metadataFile.toPath(), json, StandardCharsets.UTF_8);
            logger.debug("Metadaten in Projekt-Datei gespeichert: {}", metadataFile.getAbsolutePath());
            
            // Auch in globale Preferences speichern für Fallback
            preferences.put("pandoc_image_size", String.valueOf((int) Math.round(imageSizeSlider.getValue())));
        } catch (IOException e) {
            logger.warn("Fehler beim Speichern der Projekt-Metadaten: {}", e.getMessage());
        }
    }
    
    private void browseOutputDirectory() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Zielverzeichnis wählen");
        
        // Letztes Verzeichnis als Startverzeichnis verwenden (aus Projekt oder global)
        String lastDirectory = "";
        File metadataFile = getProjectMetadataFile();
        if (metadataFile != null && metadataFile.exists()) {
            try {
                String json = Files.readString(metadataFile.toPath(), StandardCharsets.UTF_8);
                TypeToken<Map<String, String>> typeToken = new TypeToken<Map<String, String>>(){};
                Map<String, String> metadata = gson.fromJson(json, typeToken.getType());
                lastDirectory = metadata.getOrDefault("outputDirectory", "");
            } catch (IOException e) {
                // Ignoriere Fehler
            }
        }
        
        // Fallback auf globale Präferenz
        if (lastDirectory.isEmpty()) {
            lastDirectory = preferences.get("pandoc_output_directory", "");
        }
        
        if (!lastDirectory.isEmpty() && new File(lastDirectory).exists()) {
            chooser.setInitialDirectory(new File(lastDirectory));
        }
        
        File selectedDir = chooser.showDialog(this);
        if (selectedDir != null) {
            outputDirectoryField.setText(selectedDir.getAbsolutePath());
            // Verzeichnis für nächste Verwendung speichern (global für Verzeichnis-Wahl)
            preferences.put("pandoc_output_directory", selectedDir.getAbsolutePath());
            // Auch in Projekt-Metadaten speichern
            saveProjectMetadata();
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
        
        // Export-Flag setzen (verhindert, dass CustomStage den Cursor überschreibt)
        isExporting = true;
        
        // Cursor in CustomStage sperren, damit er nicht überschrieben wird
        setCursorLocked(true);
        
        // UI-Updates sofort anzeigen (synchron, da wir bereits im JavaFX-Thread sind)
        if (getScene() != null) {
            getScene().setCursor(Cursor.WAIT);
        }
        exportButton.setDisable(true);
        exportButton.setText("Export läuft...");
        
        // Export in separatem Thread ausführen, damit UI nicht blockiert wird
        Task<Boolean> exportTask = new Task<Boolean>() {
            @Override
            protected Boolean call() throws Exception {
                try {
                    // YAML-Metadaten direkt in Markdown-Datei einfügen
                    File markdownWithMetadata = createMarkdownWithMetadata();
                    if (markdownWithMetadata == null) {
                        Platform.runLater(() -> {
                            isExporting = false;
                            setCursorLocked(false);
                            if (getScene() != null) {
                                getScene().setCursor(Cursor.DEFAULT);
                            }
                            exportButton.setDisable(false);
                            exportButton.setText("Export starten");
                            showAlert("Fehler", "Konnte Markdown-Datei mit Metadaten nicht erstellen.");
                        });
                        return false;
                    }
                    
                    // Pandoc-Aufruf
                    return runPandocExport(markdownWithMetadata);
                } catch (Exception e) {
                    logger.error("Fehler beim Export", e);
                    lastExportError = "Export fehlgeschlagen: " + (e.getMessage() != null ? e.getMessage() : "Unbekannter Fehler");
                    return false;
                }
            }
        };
        
        // Erfolg/Fehler-Handler (werden automatisch im JavaFX-Thread aufgerufen)
        exportTask.setOnSucceeded(e -> {
            try {
                boolean success = exportTask.getValue();
                
                // Export-Flag zurücksetzen
                isExporting = false;
                setCursorLocked(false);
                
                // UI zurücksetzen
                if (getScene() != null) {
                    getScene().setCursor(Cursor.DEFAULT);
                }
                exportButton.setDisable(false);
                exportButton.setText("Export starten");
                
                if (success) {
                    // Metadaten vor dem Schließen speichern
                    saveProjectMetadata();
                    showAlert("Erfolg", "Export erfolgreich abgeschlossen!");
                    close();
                } else {
                    // Fehler-Dialog mit detaillierter Fehlermeldung
                    String errorMessage = "Export fehlgeschlagen.";
                    if (lastExportError != null && !lastExportError.trim().isEmpty()) {
                        errorMessage = "Export fehlgeschlagen.\n\n" + lastExportError;
                    } else {
                        errorMessage = "Export fehlgeschlagen. Siehe Logs für Details.";
                    }
                    showErrorWithHelp(errorMessage);
                }
            } catch (Exception ex) {
                logger.error("Fehler im Erfolgs-Handler", ex);
            }
        });
        
        exportTask.setOnFailed(e -> {
            try {
                // Export-Flag zurücksetzen
                isExporting = false;
                setCursorLocked(false);
                
                // UI zurücksetzen
                if (getScene() != null) {
                    getScene().setCursor(Cursor.DEFAULT);
                }
                exportButton.setDisable(false);
                exportButton.setText("Export starten");
                
                // Fehler-Dialog
                String errorMessage = "Export fehlgeschlagen: " + 
                    (exportTask.getException() != null ? exportTask.getException().getMessage() : "Unbekannter Fehler");
                if (lastExportError != null && !lastExportError.trim().isEmpty()) {
                    errorMessage = lastExportError;
                }
                showErrorWithHelp(errorMessage);
            } catch (Exception ex) {
                logger.error("Fehler im Fehler-Handler", ex);
            }
        });
        
        // Task starten
        new Thread(exportTask).start();
    }
    
    private void loadWindowProperties() {
        // Verwende die neue Multi-Monitor-Validierung
        Rectangle2D windowBounds = PreferencesManager.MultiMonitorValidator.loadAndValidateWindowProperties(
            preferences, "pandoc_window", 700.0, 900.0);
        
        // Wende die validierten Eigenschaften an
        PreferencesManager.MultiMonitorValidator.applyWindowProperties(this, windowBounds);
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
        
        // Metadaten speichern bei Änderung (projekt-spezifisch)
        authorField.textProperty().addListener((obs, oldVal, newVal) -> {
            saveProjectMetadata();
        });
        
        rightsField.textProperty().addListener((obs, oldVal, newVal) -> {
            saveProjectMetadata();
        });
        
        dateField.textProperty().addListener((obs, oldVal, newVal) -> {
            saveProjectMetadata();
        });
        
        // Titel und Untertitel speichern
        titleField.textProperty().addListener((obs, oldVal, newVal) -> {
            saveProjectMetadata();
        });
        
        subtitleField.textProperty().addListener((obs, oldVal, newVal) -> {
            saveProjectMetadata();
        });
        
        // Abstract speichern
        abstractArea.textProperty().addListener((obs, oldVal, newVal) -> {
            saveProjectMetadata();
        });
        
        // Output-Verzeichnis speichern (globale Präferenz für Verzeichnis-Wahl)
        outputDirectoryField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.trim().isEmpty()) {
                preferences.put("pandoc_output_directory", newVal.trim());
            }
            saveProjectMetadata();
        });
        
        // Cover-Bild speichern
        coverImageField.textProperty().addListener((obs, oldVal, newVal) -> {
            saveProjectMetadata();
        });
        
        // Format speichern
        formatComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            saveProjectMetadata();
        });
        
        // Template speichern
        templateComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            saveProjectMetadata();
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

                // Cover-Bild für HTML5 und PDF hinzufügen (falls vorhanden)
                String format = formatComboBox.getValue();
                if (("html5".equals(format) || "pdf".equals(format)) && !coverImageField.getText().trim().isEmpty()) {
                    File coverImageFile = new File(coverImageField.getText().trim());
                    if (coverImageFile.exists()) {
                        if ("html5".equals(format)) {
                            String baseName = fileNameField.getText().replace(".html", "");
                            File htmlDir = new File(outputDirectoryField.getText(), baseName + "_html");
                            String coverFileName = getCoverFileName(coverImageFile);
                            File targetCover = new File(htmlDir, coverFileName);
                            writer.println("cover-image: \"" + targetCover.getName() + "\"");
                        } else if ("pdf".equals(format)) {
                            // Für PDF: Verwende den Dateinamen des Cover-Bildes
                            String coverFileName = getCoverFileName(coverImageFile);
                            writer.println("cover-image: \"" + coverFileName + "\"");
                        }
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
            
            // Lese zuerst die Original-Datei, um die Zeilenumbruch-Konvention zu bestimmen
            String originalContent = Files.readString(inputMarkdownFile.toPath(), StandardCharsets.UTF_8);
            
            // Bestimme die Zeilenumbruch-Konvention der Original-Datei
            String lineSeparator = "\n";
            if (originalContent.contains("\r\n")) {
                lineSeparator = "\r\n";
            } else if (originalContent.contains("\r")) {
                lineSeparator = "\r";
            }
            
            // Schreibe YAML-Header und Original-Inhalt zeilenweise mit BufferedWriter
            // Das stellt sicher, dass Zeilenumbrüche erhalten bleiben
            // Schreibe Original-Inhalt direkt - behält originale Zeilenumbrüche und Unicode-Zeichen
            // Gedankenstriche (em-dash, en-dash) bleiben erhalten - Pandoc konvertiert sie korrekt
            String normalizedContent = originalContent;
                // <br> Tags werden NICHT hier ersetzt - Pandoc unterstützt HTML in Markdown
            
            // Zusätzliche Leerzeilen zwischen Tabellen-Zeilen entfernen
            normalizedContent = normalizedContent.replaceAll(
                "(?m)(\\|[^\\n]*\\|)\\R+(?=\\|)",
                "$1" + lineSeparator
            );
            
            // Sicherstellen, dass vor Tabellen-Zeilen eine Leerzeile steht
            normalizedContent = normalizedContent.replaceAll(
                "(?m)([^\\n])\\n(\\|[^\\n]*\\|)",
                "$1" + lineSeparator + lineSeparator + "$2"
            );
            
            try (java.io.BufferedWriter writer = Files.newBufferedWriter(
                    tempMarkdownFile.toPath(), 
                    StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                    java.nio.file.StandardOpenOption.WRITE)) {
                
                // Schreibe YAML-Header
                writer.write("---");
                writer.write(lineSeparator);
                
                // Titel
                String title = titleField.getText().trim();
                if (!title.isEmpty()) {
                    writer.write("title: \"" + escapeYamlString(title) + "\"");
                } else {
                    writer.write("title: \"Manuskript\"");
                }
                writer.write(lineSeparator);
                
                // Untertitel
                String subtitle = subtitleField.getText().trim();
                if (!subtitle.isEmpty()) {
                    writer.write("subtitle: \"" + escapeYamlString(subtitle) + "\"");
                    writer.write(lineSeparator);
                }
                
                // Autor
                String author = authorField.getText().trim();
                if (!author.isEmpty()) {
                    writer.write("author: \"" + escapeYamlString(author) + "\"");
                    writer.write(lineSeparator);
                }
                
                // Datum
                String date = dateField.getText().trim();
                if (!date.isEmpty()) {
                    writer.write("date: \"" + escapeYamlString(date) + "\"");
                    writer.write(lineSeparator);
                }
                
                // Rechte
                String rights = rightsField.getText().trim();
                if (!rights.isEmpty()) {
                    writer.write("rights: \"" + escapeYamlString(rights) + "\"");
                    writer.write(lineSeparator);
                }
                
                // PDF-spezifische Metadaten
                String format = formatComboBox.getValue();
                if ("pdf".equals(format)) {
                    // Nur toc hinzufügen, keine Fonts oder lang (werden nicht benötigt)
                    writer.write("toc: true");
                    writer.write(lineSeparator);
                    
                    // Cover-Bild für PDF hinzufügen (falls vorhanden)
                    if (!coverImageField.getText().trim().isEmpty()) {
                        File coverImageFile = new File(coverImageField.getText().trim());
                        if (coverImageFile.exists()) {
                            String coverFileName = getCoverFileName(coverImageFile);
                            writer.write("cover-image: \"" + coverFileName + "\"");
                            writer.write(lineSeparator);
                        }
                    }
                }
                
                // Abstract
                String abstractText = abstractArea.getText().trim();
                if (!abstractText.isEmpty()) {
                    writer.write("abstract: |");
                    writer.write(lineSeparator);
                    String[] lines = abstractText.split("\r?\n");
                    for (String line : lines) {
                        writer.write("  " + line);
                        writer.write(lineSeparator);
                    }
                    if ("epub3".equals(format) || "pdf".equals(format)) {
                        writer.write("abstract-title: \"Zusammenfassung\"");
                        writer.write(lineSeparator);
                    }
                }
                
                writer.write("---");
                writer.write(lineSeparator);
                // WICHTIG: Leerzeile nach YAML-Header, damit Pandoc das Frontmatter korrekt erkennt
                // und kein <hr> vor der Titelei einfügt
                writer.write(lineSeparator);
                
                writer.write(normalizedContent);
            }
            
            // Debug-Markdown zur Analyse ablegen
            try {
                Files.copy(
                    tempMarkdownFile.toPath(),
                    Paths.get("pandoc", "debug-export.md"),
                    StandardCopyOption.REPLACE_EXISTING
                );
                logger.info("Debug-Markdown geschrieben nach pandoc\\debug-export.md");
                
                int tableIndex = normalizedContent.indexOf("|---");
                if (tableIndex >= 0) {
                    int start = Math.max(0, tableIndex - 80);
                    int end = Math.min(normalizedContent.length(), tableIndex + 200);
                    String snippet = normalizedContent.substring(start, end)
                        .replace("\r", "\\r")
                        .replace("\n", "\\n");
                    logger.info("Markdown-Ausschnitt rund um Tabelle: {}", snippet);
                } else {
                    logger.info("Kein '|' Tabellenmuster im Debug-Markdown gefunden.");
                }
            } catch (IOException copyEx) {
                logger.warn("Konnte Debug-Markdown nicht schreiben: {}", copyEx.getMessage());
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
     * und stellt sicher, dass Bilder vor Überschriften ihre Position behalten
     */
    private void replaceHtmlTagsInMarkdown(File markdownFile, String format) {
        try {
            String content = Files.readString(markdownFile.toPath(), StandardCharsets.UTF_8);
            
            // Gedankenstriche (em-dash, en-dash) bleiben erhalten - Pandoc konvertiert sie korrekt zu DOCX
            // Keine Konvertierung zu normalen Bindestrichen mehr nötig
            
            String newline = content.contains("\r\n") ? "\r\n" : "\n";
            
            // Sicherstellen, dass vor Tabellen-Zeilen eine Leerzeile steht
            content = content.replaceAll(
                "(?m)([^\\n])\\n(\\|[^\\n]*\\|)",
                "$1" + newline + newline + "$2"
            );
            
            // Zusätzliche Leerzeilen zwischen Tabellen-Zeilen entfernen
            content = content.replaceAll(
                "(?m)(\\|[^\\n]*\\|)\\R+(?=\\|)",
                "$1" + newline
            );
            
            // <br> Tags werden NICHT hier ersetzt - Pandoc unterstützt HTML in Markdown
            // Sie werden später format-spezifisch konvertiert (siehe unten)
            
            // originalContent NACH allen Änderungen setzen, damit <br> Konvertierung erkannt wird
            String originalContent = content;
            
            // Für EPUB3: Ähnliche Fixes wie für DOCX
            if ("epub3".equals(format) || "epub".equals(format)) {
                // <br> Tags für EPUB: Pandoc unterstützt HTML in Markdown, verarbeitet <br> automatisch
                // Keine Konvertierung nötig - Pandoc konvertiert <br> zu Zeilenumbrüchen in EPUB
                
                // ZUERST: Reihenfolge korrigieren - wenn Überschrift vor Bild steht, tauschen wir sie im Markdown
                // Pattern: # Überschrift\n\n![alt](path) -> ![alt](path)\n\n# Überschrift
                content = content.replaceAll(
                    "(?m)^(#{1,6}\\s+.+?)\\s*\\n\\s*\\n(!\\[[^\\]]*\\]\\([^\\)]+\\))$",
                    "$2\n\n$1"
                );
                // Auch ohne Leerzeile
                content = content.replaceAll(
                    "(?m)^(#{1,6}\\s+.+?)\\s*\\n(!\\[[^\\]]*\\]\\([^\\)]+\\))$",
                    "$2\n\n$1"
                );
                
                // Alt-Text entfernen (wird sonst als Bildunterschrift angezeigt)
                // Pattern: ![alt](path) -> ![](path)
                content = content.replaceAll(
                    "(?m)!\\[([^\\]]+)\\]\\(([^\\)]+)\\)",
                    "![]($2)"
                );
            }
            
            // Für DOCX: Bilder vor Überschriften in Container packen, um Reihenfolge zu erhalten
            if ("docx".equals(format)) {
                // WICHTIG: Listen-Einrückungen normalisieren
                // Pandoc erkennt verschachtelte Listen nur mit 4 Leerzeichen pro Ebene
                // Konvertiere 2 Leerzeichen -> 4 Leerzeichen für jede Ebene
                content = normalizeListIndentation(content);
                
                // Bildgröße aus Slider lesen
                int imageSizePercent = (int) Math.round(imageSizeSlider.getValue());
                
                // ZUERST: Reihenfolge korrigieren - wenn Überschrift vor Bild steht, tauschen wir sie im Markdown
                // Pattern: # Überschrift\n\n![alt](path) -> ![alt](path)\n\n# Überschrift
                content = content.replaceAll(
                    "(?m)^(#{1,6}\\s+.+?)\\s*\\n\\s*\\n(!\\[[^\\]]*\\]\\([^\\)]+\\))$",
                    "$2\n\n$1"
                );
                // Auch ohne Leerzeile
                content = content.replaceAll(
                    "(?m)^(#{1,6}\\s+.+?)\\s*\\n(!\\[[^\\]]*\\]\\([^\\)]+\\))$",
                    "$2\n\n$1"
                );
                
                // Dann: Alle Bilder kleiner machen (parametrisierbare Größe)
                // Pattern: ![alt](path) -> ![alt](path){ width=X% }
                // Nur wenn noch keine Größenangabe vorhanden ist
                content = content.replaceAll(
                    "(?m)(!\\[[^\\]]*\\]\\([^\\)]+\\))(?!\\s*\\{[^}]*width)",
                    "$1{ width=" + imageSizePercent + "% }"
                );
                
                // Alt-Text entfernen (wird sonst als Bildunterschrift angezeigt)
                // Pattern: ![alt](path){ width=X% } -> ![](path){ width=X% }
                content = content.replaceAll(
                    "(?m)!\\[([^\\]]+)\\]\\(([^\\)]+)\\)\\{ width=" + imageSizePercent + "% \\}",
                    "![]($2){ width=" + imageSizePercent + "% }"
                );
                
                // Dann: Alle Bilder in zentrierte HTML-Divs packen (mehrzeilig für bessere Kompatibilität)
                // Pattern: ![](path){ width=X% } -> <div align="center">\n![](path){ width=X% }\n</div>
                // WICHTIG: Verwende den lineSeparator der Datei, nicht hartcodiertes \n
                String lineSep = content.contains("\r\n") ? "\r\n" : "\n";
                content = content.replaceAll(
                    "(?m)(!\\[\\]\\([^\\)]+\\)\\{ width=" + imageSizePercent + "% \\})(?!</div>)",
                    "<div align=\"center\">" + lineSep + "$1" + lineSep + "</div>"
                );
                
                // Horizontale Linien (---, ***, ___) durch zentrierte ◆◆◆ mit Leerzeilen ersetzen
                // Pattern: Zeile mit nur ---, *** oder ___ (mindestens 3 Zeichen)
                // WICHTIG: YAML-Header-Zeilen (--- am Anfang, gefolgt von YAML-Content bis zum nächsten ---) ausschließen
                // Ersetze durch: Leerzeilen + <br> + zentrierte ◆◆◆ (mit Div) + <br> + Leerzeilen
                // Kombiniere <br> Tags für Zeilenumbrüche und <div align="center"> für Zentrierung
                
                // Zuerst: YAML-Header-Bereich schützen (von erstem --- bis zweitem --- gefolgt von Leerzeile)
                // Verwende einen eindeutigen Marker, der nicht im Content vorkommt
                String yamlMarker = "___YAML_HEADER_PROTECTED___";
                
                // Prüfe, ob die Datei mit YAML-Header beginnt
                if (content.startsWith("---" + lineSep)) {
                    // Finde das Ende des YAML-Headers (zweites --- gefolgt von Leerzeile)
                    // Pattern: --- am Anfang, dann beliebiger Content, dann --- gefolgt von Leerzeile
                    int yamlEndIndex = content.indexOf(lineSep + "---" + lineSep + lineSep);
                    if (yamlEndIndex > 0) {
                        // YAML-Header gefunden - ersetze ihn temporär
                        String yamlHeader = content.substring(0, yamlEndIndex + lineSep.length() + 3 + lineSep.length() + lineSep.length());
                        String restContent = content.substring(yamlEndIndex + lineSep.length() + 3 + lineSep.length() + lineSep.length());
                        
                        // Ersetze YAML-Header-Zeilen im Header-Bereich
                        String protectedYaml = yamlHeader.replaceAll(
                            "(?m)^([ \t]*)(---)([ \t]*)$",
                            "$1" + yamlMarker + "$3"
                        );
                        
                        // Dann: Horizontale Linien im Rest-Content ersetzen
                        String processedRest = restContent.replaceAll(
                            "(?m)^([ \t]*)([-*_]{3,})([ \t]*)$",
                            lineSep + lineSep + lineSep + lineSep + 
                            "<br>" + lineSep + lineSep + 
                            "<div align=\"center\">◆◆◆</div>" + 
                            lineSep + lineSep + 
                            "<br>" + 
                            lineSep + lineSep + lineSep + lineSep
                        );
                        
                        // YAML-Header wiederherstellen und zusammenfügen
                        protectedYaml = protectedYaml.replace(yamlMarker, "---");
                        content = protectedYaml + processedRest;
                    } else {
                        // Kein YAML-Header-Ende gefunden, normal verarbeiten
                        content = content.replaceAll(
                            "(?m)^([ \t]*)([-*_]{3,})([ \t]*)$",
                            lineSep + lineSep + lineSep + lineSep + 
                            "<br>" + lineSep + lineSep + 
                            "<div align=\"center\">◆◆◆</div>" + 
                            lineSep + lineSep + 
                            "<br>" + 
                            lineSep + lineSep + lineSep + lineSep
                        );
                    }
                } else {
                    // Kein YAML-Header am Anfang, normal verarbeiten
                    content = content.replaceAll(
                        "(?m)^([ \t]*)([-*_]{3,})([ \t]*)$",
                        lineSep + lineSep + lineSep + lineSep + 
                        "<br>" + lineSep + lineSep + 
                        "<div align=\"center\">◆◆◆</div>" + 
                        lineSep + lineSep + 
                        "<br>" + 
                        lineSep + lineSep + lineSep + lineSep
                    );
                }
            }
            
            // HTML-Tags ersetzen basierend auf dem Ausgabeformat
            if ("pdf".equals(format)) {
                // Für PDF: LaTeX-Befehle verwenden
                // <c> und <center> Tags zu LaTeX-Zentrierung konvertieren
                content = content.replaceAll("(?s)<(?:c|center)>(.*?)</(?:c|center)>", "\\\\begin{center}$1\\\\end{center}");
                content = content.replaceAll("<u>([^<]+)</u>", "\\\\underline{$1}");
                content = content.replaceAll("<b>([^<]+)</b>", "\\\\textbf{$1}");
                content = content.replaceAll("<i>([^<]+)</i>", "\\\\textit{$1}");
                content = content.replaceAll("<strong>([^<]+)</strong>", "\\\\textbf{$1}");
                content = content.replaceAll("<em>([^<]+)</em>", "\\\\textit{$1}");
                // Strikethrough: <s> zu Markdown ~~ (funktioniert in PDF/LaTeX)
                content = content.replaceAll("<s>([^<]+)</s>", "~~$1~~");
                content = content.replaceAll("<del>([^<]+)</del>", "~~$1~~");
                content = content.replaceAll("<mark>([^<]+)</mark>", "\\\\hl{$1}");
                content = content.replaceAll("<small>([^<]+)</small>", "\\\\small $1");
                content = content.replaceAll("<big>([^<]+)</big>", "\\\\large $1");
                // Subscript und Superscript für PDF - verwende Markdown-Syntax
                // ~text~ für Subscript, ^text^ für Superscript
                // Leerzeichen müssen mit Backslash escaped werden (laut Pandoc-Dokumentation)
                java.util.regex.Pattern subPattern = java.util.regex.Pattern.compile("<sub>([^<]+)</sub>");
                java.util.regex.Matcher subMatcher = subPattern.matcher(content);
                List<java.util.Map.Entry<java.util.Map.Entry<Integer, Integer>, String>> subReplacements = new ArrayList<>();
                while (subMatcher.find()) {
                    String text = subMatcher.group(1);
                    // Leerzeichen mit Backslash escapen (für Pandoc)
                    text = text.replace(" ", "\\ ");
                    String replacement = "~" + text + "~";
                    subReplacements.add(new java.util.AbstractMap.SimpleEntry<>(
                        new java.util.AbstractMap.SimpleEntry<>(subMatcher.start(), subMatcher.end()),
                        replacement));
                }
                // Ersetze rückwärts, um Indizes nicht zu verschieben
                for (int i = subReplacements.size() - 1; i >= 0; i--) {
                    java.util.Map.Entry<java.util.Map.Entry<Integer, Integer>, String> entry = subReplacements.get(i);
                    int start = entry.getKey().getKey();
                    int end = entry.getKey().getValue();
                    content = content.substring(0, start) + entry.getValue() + content.substring(end);
                }
                
                java.util.regex.Pattern supPattern = java.util.regex.Pattern.compile("<sup>([^<]+)</sup>");
                java.util.regex.Matcher supMatcher = supPattern.matcher(content);
                List<java.util.Map.Entry<java.util.Map.Entry<Integer, Integer>, String>> supReplacements = new ArrayList<>();
                while (supMatcher.find()) {
                    String text = supMatcher.group(1);
                    // Leerzeichen mit Backslash escapen (für Pandoc)
                    text = text.replace(" ", "\\ ");
                    String replacement = "^" + text + "^";
                    supReplacements.add(new java.util.AbstractMap.SimpleEntry<>(
                        new java.util.AbstractMap.SimpleEntry<>(supMatcher.start(), supMatcher.end()),
                        replacement));
                }
                // Ersetze rückwärts, um Indizes nicht zu verschieben
                for (int i = supReplacements.size() - 1; i >= 0; i--) {
                    java.util.Map.Entry<java.util.Map.Entry<Integer, Integer>, String> entry = supReplacements.get(i);
                    int start = entry.getKey().getKey();
                    int end = entry.getKey().getValue();
                    content = content.substring(0, start) + entry.getValue() + content.substring(end);
                }
                
                // <br> Tags für PDF: Konvertiere direkt zu LaTeX-Befehlen für Absatzumbruch
                // \vspace{\baselineskip} erzeugt eine Leerzeile (eine Zeilenhöhe)
                // \par erzeugt einen Absatzumbruch
                // Kombination: \par\vspace{\baselineskip}\par für einen sichtbaren Absatzumbruch
                // Konvertiere alle Varianten von <br> Tags zu LaTeX-Befehlen
                content = content.replaceAll("(?i)<br\\s*/?>", "\\\\par\\\\vspace\\{\\\\baselineskip\\}\\\\par");
                content = content.replaceAll("(?i)<br>", "\\\\par\\\\vspace\\{\\\\baselineskip\\}\\\\par");
                content = content.replaceAll("(?i)<br\\s+/>", "\\\\par\\\\vspace\\{\\\\baselineskip\\}\\\\par");
                
                // Markdown-Kursiv zu LaTeX-Kursiv konvertieren (wichtig für PDF!)
                // Pandoc macht das automatisch: *text* → \emph{text}
                // Das ist korrekt! \emph{} ist die richtige LaTeX-Formatierung für Kursiv
            } else if ("docx".equals(format)) {
                // <br> Tags für DOCX: Pandoc unterstützt HTML in Markdown, verarbeitet <br> automatisch
                // Keine Konvertierung nötig - Pandoc konvertiert <br> zu Zeilenumbrüchen in DOCX
                
                // Zentrierte Absätze sammeln und für Post-Processing vorbereiten
                // Pandoc konvertiert zentrierte Tags nicht automatisch zu zentrierten Absätzen in DOCX
                // Daher sammeln wir die Texte und zentrieren sie im Post-Processing
                centeredParagraphs.clear();
                
                // Pattern für <c>Text</c> und <center>Text</center> (einfache, saubere Syntax)
                java.util.regex.Pattern centerPattern = java.util.regex.Pattern.compile(
                    "(?s)<(?:c|center)>(.*?)</(?:c|center)>"
                );
                java.util.regex.Matcher centerMatcher = centerPattern.matcher(content);
                java.util.List<java.util.Map.Entry<Integer, Integer>> centerReplacements = new java.util.ArrayList<>();
                while (centerMatcher.find()) {
                    String htmlContent = centerMatcher.group(1);
                    // Entferne HTML-Tags aus dem Text (behält nur den reinen Text)
                    String centeredText = htmlContent.replaceAll("<[^>]+>", "").trim();
                    if (!centeredText.isEmpty()) {
                        centeredParagraphs.add(centeredText);
                        centerReplacements.add(new java.util.AbstractMap.SimpleEntry<>(
                            centerMatcher.start(), centerMatcher.end()
                        ));
                    }
                }
                // Ersetze rückwärts, um Indizes nicht zu verschieben
                for (int i = centerReplacements.size() - 1; i >= 0; i--) {
                    java.util.Map.Entry<Integer, Integer> entry = centerReplacements.get(i);
                    int start = entry.getKey();
                    int end = entry.getValue();
                    // Finde den Matcher für diese Position neu
                    java.util.regex.Matcher m = centerPattern.matcher(content);
                    if (m.find(start)) {
                        String htmlContent = m.group(1);
                        String centeredText = htmlContent.replaceAll("<[^>]+>", "").trim();
                        content = content.substring(0, start) + centeredText + content.substring(end);
                    }
                }
                
                
                // Für DOCX: Pandoc-native Befehle verwenden
                content = content.replaceAll("<u>([^<]+)</u>", "[$1]{.underline}");
                content = content.replaceAll("<b>([^<]+)</b>", "**$1**");
                content = content.replaceAll("<i>([^<]+)</i>", "*$1*");
                content = content.replaceAll("<strong>([^<]+)</strong>", "**$1**");
                content = content.replaceAll("<em>([^<]+)</em>", "*$1*");
                content = content.replaceAll("<s>([^<]+)</s>", "~~$1~~");
                content = content.replaceAll("<del>([^<]+)</del>", "~~$1~~");
                content = content.replaceAll("<mark>([^<]+)</mark>", "[$1]{.mark}");
                
                // Farbige Spans zu Custom-Styles konvertieren
                // Pattern: <span style="color: red;">Text</span> -> [Text]{custom-style="RedText"}
                // Unterstützte Farben: rot, blau, grün, gelb, lila, orange, grau
                // Case-insensitive Matching für verschiedene Schreibweisen
                // WICHTIG: Custom-Styles müssen als ZEICHENSTILE (Character Styles) in der Reference-DOC definiert sein!
                // Spans verwenden Zeichenstile, nicht Absatzstile. Für Absatzstile müssten Divs verwendet werden.
                content = content.replaceAll(
                    "(?i)<span\\s+style\\s*=\\s*[\"']color:\\s*red[\"']>([^<]+)</span>", 
                    "[$1]{custom-style=\"RedText\"}"
                );
                content = content.replaceAll(
                    "(?i)<span\\s+style\\s*=\\s*[\"']color:\\s*blue[\"']>([^<]+)</span>", 
                    "[$1]{custom-style=\"BlueText\"}"
                );
                content = content.replaceAll(
                    "(?i)<span\\s+style\\s*=\\s*[\"']color:\\s*green[\"']>([^<]+)</span>", 
                    "[$1]{custom-style=\"GreenText\"}"
                );
                content = content.replaceAll(
                    "(?i)<span\\s+style\\s*=\\s*[\"']color:\\s*yellow[\"']>([^<]+)</span>", 
                    "[$1]{custom-style=\"YellowText\"}"
                );
                content = content.replaceAll(
                    "(?i)<span\\s+style\\s*=\\s*[\"']color:\\s*purple[\"']>([^<]+)</span>", 
                    "[$1]{custom-style=\"PurpleText\"}"
                );
                content = content.replaceAll(
                    "(?i)<span\\s+style\\s*=\\s*[\"']color:\\s*orange[\"']>([^<]+)</span>", 
                    "[$1]{custom-style=\"OrangeText\"}"
                );
                content = content.replaceAll(
                    "(?i)<span\\s+style\\s*=\\s*[\"']color:\\s*gray[\"']>([^<]+)</span>", 
                    "[$1]{custom-style=\"GrayText\"}"
                );
                content = content.replaceAll(
                    "(?i)<span\\s+style\\s*=\\s*[\"']color:\\s*grey[\"']>([^<]+)</span>", 
                    "[$1]{custom-style=\"GrayText\"}"
                );
                
                // Kurze Farb-Tags zu Custom-Styles konvertieren
                // Englische Tags: <red>Text</red> -> [Text]{custom-style="RedText"}
                content = content.replaceAll("(?i)<red>([^<]+)</red>", "[$1]{custom-style=\"RedText\"}");
                content = content.replaceAll("(?i)<blue>([^<]+)</blue>", "[$1]{custom-style=\"BlueText\"}");
                content = content.replaceAll("(?i)<green>([^<]+)</green>", "[$1]{custom-style=\"GreenText\"}");
                content = content.replaceAll("(?i)<yellow>([^<]+)</yellow>", "[$1]{custom-style=\"YellowText\"}");
                content = content.replaceAll("(?i)<purple>([^<]+)</purple>", "[$1]{custom-style=\"PurpleText\"}");
                content = content.replaceAll("(?i)<orange>([^<]+)</orange>", "[$1]{custom-style=\"OrangeText\"}");
                content = content.replaceAll("(?i)<gray>([^<]+)</gray>", "[$1]{custom-style=\"GrayText\"}");
                content = content.replaceAll("(?i)<grey>([^<]+)</grey>", "[$1]{custom-style=\"GrayText\"}");
                
                // Deutsche Tags: <rot>Text</rot> -> [Text]{custom-style="RedText"}
                content = content.replaceAll("(?i)<rot>([^<]+)</rot>", "[$1]{custom-style=\"RedText\"}");
                content = content.replaceAll("(?i)<blau>([^<]+)</blau>", "[$1]{custom-style=\"BlueText\"}");
                content = content.replaceAll("(?i)<grün>([^<]+)</grün>", "[$1]{custom-style=\"GreenText\"}");
                content = content.replaceAll("(?i)<gelb>([^<]+)</gelb>", "[$1]{custom-style=\"YellowText\"}");
                content = content.replaceAll("(?i)<lila>([^<]+)</lila>", "[$1]{custom-style=\"PurpleText\"}");
                content = content.replaceAll("(?i)<orange>([^<]+)</orange>", "[$1]{custom-style=\"OrangeText\"}");
                content = content.replaceAll("(?i)<grau>([^<]+)</grau>", "[$1]{custom-style=\"GrayText\"}");
                
                // Code-Tags: <code>Text</code> -> [Text]{custom-style="Code"}
                content = content.replaceAll("(?i)<code>([^<]+)</code>", "[$1]{custom-style=\"Code\"}");
                
                //small und big werden nicht unterstützt in DOCX 
                content = content.replaceAll("<small>([^<]+)</small>", "[$1]{custom-style=\"Small\"}");
                content = content.replaceAll("<big>([^<]+)</big>", "[$1]{custom-style=\"Large\"}");
                // Subscript und Superscript für DOCX - verwende Markdown-Syntax
                // ~text~ für Subscript, ^text^ für Superscript
                // Leerzeichen müssen mit Backslash escaped werden (laut Pandoc-Dokumentation)
                java.util.regex.Pattern subPattern = java.util.regex.Pattern.compile("<sub>([^<]+)</sub>");
                java.util.regex.Matcher subMatcher = subPattern.matcher(content);
                List<java.util.Map.Entry<java.util.Map.Entry<Integer, Integer>, String>> subReplacements = new ArrayList<>();
                while (subMatcher.find()) {
                    String text = subMatcher.group(1);
                    // Leerzeichen mit Backslash escapen (für Pandoc)
                    text = text.replace(" ", "\\ ");
                    String replacement = "~" + text + "~";
                    subReplacements.add(new java.util.AbstractMap.SimpleEntry<>(
                        new java.util.AbstractMap.SimpleEntry<>(subMatcher.start(), subMatcher.end()),
                        replacement));
                }
                // Ersetze rückwärts, um Indizes nicht zu verschieben
                for (int i = subReplacements.size() - 1; i >= 0; i--) {
                    java.util.Map.Entry<java.util.Map.Entry<Integer, Integer>, String> entry = subReplacements.get(i);
                    int start = entry.getKey().getKey();
                    int end = entry.getKey().getValue();
                    content = content.substring(0, start) + entry.getValue() + content.substring(end);
                }
                
                java.util.regex.Pattern supPattern = java.util.regex.Pattern.compile("<sup>([^<]+)</sup>");
                java.util.regex.Matcher supMatcher = supPattern.matcher(content);
                List<java.util.Map.Entry<java.util.Map.Entry<Integer, Integer>, String>> supReplacements = new ArrayList<>();
                while (supMatcher.find()) {
                    String text = supMatcher.group(1);
                    // Leerzeichen mit Backslash escapen (für Pandoc)
                    text = text.replace(" ", "\\ ");
                    String replacement = "^" + text + "^";
                    supReplacements.add(new java.util.AbstractMap.SimpleEntry<>(
                        new java.util.AbstractMap.SimpleEntry<>(supMatcher.start(), supMatcher.end()),
                        replacement));
                }
                // Ersetze rückwärts, um Indizes nicht zu verschieben
                for (int i = supReplacements.size() - 1; i >= 0; i--) {
                    java.util.Map.Entry<java.util.Map.Entry<Integer, Integer>, String> entry = supReplacements.get(i);
                    int start = entry.getKey().getKey();
                    int end = entry.getKey().getValue();
                    content = content.substring(0, start) + entry.getValue() + content.substring(end);
                }
            } else if ("epub3".equals(format) || "html5".equals(format) || "epub".equals(format) || "html".equals(format)) {
                // Für EPUB/HTML: <c> Tags zu <center> konvertieren (HTML-Standard)
                content = content.replaceAll("(?s)<c>(.*?)</c>", "<center>$1</center>");
                // <center> Tags bleiben erhalten (HTML-Standard)
                
                // Kurze Farb-Tags zu HTML-Spans konvertieren
                // Englische Tags: <red>Text</red> -> <span style="color: red;">Text</span>
                content = content.replaceAll("(?i)<red>([^<]+)</red>", "<span style=\"color: red;\">$1</span>");
                content = content.replaceAll("(?i)<blue>([^<]+)</blue>", "<span style=\"color: blue;\">$1</span>");
                content = content.replaceAll("(?i)<green>([^<]+)</green>", "<span style=\"color: green;\">$1</span>");
                content = content.replaceAll("(?i)<yellow>([^<]+)</yellow>", "<span style=\"color: yellow;\">$1</span>");
                content = content.replaceAll("(?i)<purple>([^<]+)</purple>", "<span style=\"color: purple;\">$1</span>");
                content = content.replaceAll("(?i)<orange>([^<]+)</orange>", "<span style=\"color: orange;\">$1</span>");
                content = content.replaceAll("(?i)<gray>([^<]+)</gray>", "<span style=\"color: gray;\">$1</span>");
                content = content.replaceAll("(?i)<grey>([^<]+)</grey>", "<span style=\"color: gray;\">$1</span>");
                
                // Deutsche Tags: <rot>Text</rot> -> <span style="color: red;">Text</span>
                content = content.replaceAll("(?i)<rot>([^<]+)</rot>", "<span style=\"color: red;\">$1</span>");
                content = content.replaceAll("(?i)<blau>([^<]+)</blau>", "<span style=\"color: blue;\">$1</span>");
                content = content.replaceAll("(?i)<grün>([^<]+)</grün>", "<span style=\"color: green;\">$1</span>");
                content = content.replaceAll("(?i)<gelb>([^<]+)</gelb>", "<span style=\"color: yellow;\">$1</span>");
                content = content.replaceAll("(?i)<lila>([^<]+)</lila>", "<span style=\"color: purple;\">$1</span>");
                content = content.replaceAll("(?i)<orange>([^<]+)</orange>", "<span style=\"color: orange;\">$1</span>");
                content = content.replaceAll("(?i)<grau>([^<]+)</grau>", "<span style=\"color: gray;\">$1</span>");
            }
            
            // Nur schreiben wenn sich etwas geändert hat
            if (!content.equals(originalContent)) {
                Files.write(markdownFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
                logger.debug("Markdown-Datei wurde modifiziert für Format: {}", format);
                
                // Debug: Prüfe ob Tabellen-Zeilen noch vorhanden sind
                String[] lines = content.split("\r?\n");
                int tableLineCount = 0;
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("|") && trimmed.contains("|")) {
                        tableLineCount++;
                    }
                }
                logger.debug("Anzahl Tabellen-Zeilen nach Verarbeitung: {}", tableLineCount);
            }
        } catch (IOException e) {
            logger.error("Fehler beim Ersetzen von HTML-Tags: {}", e.getMessage());
        }
    }
    
    /**
     * Normalisiert Listen-Einrückungen für Pandoc
     * Pandoc erkennt verschachtelte Listen nur mit 4 Leerzeichen pro Ebene
     * Konvertiert Einrückungen zu Vielfachen von 4, BEHÄLT ABER die relative Einrückung bei
     * 
     * Strategie: Finde die minimale Einrückung und normalisiere relativ dazu
     */
    private String normalizeListIndentation(String content) {
        String[] lines = content.split("\r?\n", -1);
        StringBuilder result = new StringBuilder();
        
        int index = 0;
        while (index < lines.length) {
            String line = lines[index];
            if (isListItem(line)) {
                int blockStart = index;
                int blockEnd = index;
                int minIndent = Integer.MAX_VALUE;
                
                while (blockEnd < lines.length) {
                    String current = lines[blockEnd];
                    if (current.trim().isEmpty()) {
                        blockEnd++;
                        continue;
                    }
                    if (!isListItem(current)) {
                        break;
                    }
                    int leading = countLeadingSpaces(current);
                    if (leading < minIndent) {
                        minIndent = leading;
                    }
                    blockEnd++;
                }
                
                if (minIndent == Integer.MAX_VALUE) {
                    minIndent = 0;
                }
                
                for (int i = blockStart; i < blockEnd; i++) {
                    String current = lines[i];
                    if (isListItem(current)) {
                        int leading = countLeadingSpaces(current);
                        int relative = Math.max(0, leading - minIndent);
                        int level = relative / 4;
                        if (relative % 4 >= 2) {
                            level++; // zur nächsten Ebene aufrunden
                        }
                        int normalizedSpaces = Math.max(0, level * 4);
                        String indent = " ".repeat(normalizedSpaces);
                        result.append(indent).append(current.substring(leading));
                    } else {
                        result.append(current);
                    }
                    if (i < lines.length - 1) {
                        result.append("\n");
                    }
                }
                index = blockEnd;
            } else {
                result.append(line);
                if (index < lines.length - 1) {
                    result.append("\n");
                }
                index++;
            }
        }
        
        return result.toString();
    }
    
    private boolean isListItem(String line) {
        return line.matches("^\\s*([-*+]|\\d+\\.)\\s+.*");
    }
    
    private int countLeadingSpaces(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') {
            count++;
        }
        return count;
    }
    
    private boolean runPandocExport(File markdownFile) {
        // Listen zurücksetzen
        copiedImageFiles.clear();
        centeredParagraphs.clear();
        
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
                : new File("pandoc", "pandoc.exe");
            
            // Ausgabedatei
            String outputDirPath = outputDirectoryField.getText().trim();
            String fileName = fileNameField.getText().trim();
            File outputFile = new File(outputDirPath, fileName);
            File outputDir = new File(outputDirPath);
            
            // HTML-Verzeichnis für HTML5 (wird später verwendet)
            File htmlDir = null;
            
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
                File tempHtmlDir = new File(outputDirectoryField.getText(), baseName + "_html");
                File htmlFile = new File(tempHtmlDir, fileNameField.getText());
                finalOutputPath = htmlFile.getAbsolutePath();
            } else {
                finalOutputPath = outputFile.getAbsolutePath();
            }

            command.add("\"" + finalOutputPath + "\"");

            // Grundlegende Optionen - YAML-Metadaten explizit aktivieren
            // Superscript, Subscript, Tabellen und Listen-Erweiterungen aktivieren
            // Auch wenn viele Extensions standardmäßig aktiviert sind, werden sie
            // hier explizit angegeben, um sicherzustellen, dass sie verwendet werden
            // pipe_tables, simple_tables, grid_tables: Tabellen-Unterstützung
            // fancy_lists, startnum, task_lists: Verschachtelte Listen-Unterstützung
            command.add("--from=markdown+yaml_metadata_block+raw_html+superscript+subscript+pipe_tables+simple_tables+grid_tables+fancy_lists+startnum+task_lists-smart");
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
                        File pandocDir = new File("pandoc");
                        if (pandocDir.exists()) {
                            try {
                                String coverFileName = getCoverFileName(coverImageFile);
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
                
                // Markdown-Bilder ins Pandoc-Verzeichnis kopieren (wie für PDF)
                File markdownDir = markdownFile.getParentFile();
                File pandocDir = new File("pandoc");
                copyMarkdownImagesToPandocDir(markdownFile, pandocDir);
                
                // Resource-Path für EPUB3 setzen, damit Pandoc die Bilder findet
                String resourcePath = pandocDir.getAbsolutePath();
                if (markdownDir != null) {
                    resourcePath += ";" + markdownDir.getAbsolutePath();
                }
                if (outputDir.exists()) {
                    resourcePath += ";" + outputDir.getAbsolutePath();
                }
                if (projectDirectory != null) {
                    resourcePath += ";" + projectDirectory.getAbsolutePath();
                }
                command.add("--resource-path=" + resourcePath);
                logger.debug("Resource-Path für EPUB3-Export: {}", resourcePath);
            } else if ("docx".equals(format)) {
            // DOCX-spezifische Optionen für bessere Titelei
            // WICHTIG: Reference-DOC kann Tabellen- und Listen-Styles überschreiben!
            // Daher wird sie nur verwendet, wenn explizit eine Vorlage ausgewählt wurde
            // und der Benutzer die Formatierung aus der Vorlage wünscht
            if (templateFile != null) {
                // Validierung der Reference-DOC vor Verwendung
                boolean isValidReferenceDoc = validateReferenceDoc(templateFile);
                if (isValidReferenceDoc) {
                    // ProcessBuilder behandelt Pfade mit Leerzeichen automatisch korrekt
                    // Keine manuellen Anführungszeichen nötig - ProcessBuilder escaped automatisch
                    command.add("--reference-doc=" + templateFile.getAbsolutePath());
                    logger.info("Verwende Reference-DOC: {}", templateFile.getAbsolutePath());
                } else {
                    logger.warn("Reference-DOC konnte nicht validiert werden: {}. Export ohne Reference-DOC.", templateFile.getAbsolutePath());
                    logger.warn("Mögliche Ursachen: Datei ist gesperrt (von Word geöffnet?), beschädigt oder nicht lesbar.");
                }
            } else {
                logger.info("Keine Reference-DOC verwendet - Tabellen und Listen sollten korrekt funktionieren");
            }
                command.add("--syntax-highlighting=tango");
                command.add("--reference-links");
                // Reihenfolge der Elemente beibehalten (kein Wrapping)
                command.add("--wrap=none");
                // Tabellen und Listen werden durch die aktivierten Extensions unterstützt:
                // pipe_tables, simple_tables, grid_tables für Tabellen
                // fancy_lists, startnum für verschachtelte Listen
                // Hinweis: Wenn Tabellen/Listen nicht funktionieren, könnte die Reference-DOC
                // die Styles überschreiben. Versuchen Sie den Export ohne Reference-DOC.
                
                // Cover-Bild für DOCX hinzufügen (falls vorhanden)
                if (!coverImageField.getText().trim().isEmpty()) {
                    File coverImageFile = new File(coverImageField.getText().trim());
                    if (coverImageFile.exists()) {
                        try {
                            // Cover-Bild ins pandoc-Verzeichnis kopieren
                            String coverFileName = getCoverFileName(coverImageFile);
                            File pandocDir = new File("pandoc");
                            File targetCover = new File(pandocDir, coverFileName);
                            Files.copy(coverImageFile.toPath(), targetCover.toPath(),
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            
                        // Cover-Bild wird im Post-Processing hinzugefügt, nicht über Pandoc
                            
                        } catch (IOException e) {
                            logger.error("Fehler beim Kopieren des Cover-Bildes für DOCX", e);
                        }
                    }
                }
                
                // Markdown-Bilder ins Pandoc-Verzeichnis kopieren (wie für EPUB3)
                File markdownDir = markdownFile.getParentFile();
                File pandocDir = new File("pandoc");
                copyMarkdownImagesToPandocDir(markdownFile, pandocDir);
                
                // Resource-Path für DOCX setzen, damit Pandoc die Bilder findet
                // Bilder werden ins pandoc-Verzeichnis kopiert, aber Pandoc muss auch im Projektverzeichnis suchen
                String resourcePath = pandocDir.getAbsolutePath();
                if (markdownDir != null) {
                    resourcePath += ";" + markdownDir.getAbsolutePath();
                }
                if (outputDir.exists()) {
                    resourcePath += ";" + outputDir.getAbsolutePath();
                }
                if (projectDirectory != null) {
                    resourcePath += ";" + projectDirectory.getAbsolutePath();
                }
                command.add("--resource-path=" + resourcePath);
                logger.debug("Resource-Path für DOCX-Export: {}", resourcePath);
                
                // Post-Processing für DOCX: Abstract-Titel ersetzen
                // Das wird nach dem Pandoc-Export durchgeführt
            } else if ("html5".equals(format)) {
                // HTML5-spezifische Optionen
                command.add("--toc"); // Inhaltsverzeichnis für HTML
                command.add("--standalone"); // Vollständiges HTML-Dokument

                // HTML-Unterverzeichnis erstellen
                String baseName = fileNameField.getText().replace(".html", "");
                htmlDir = new File(outputDirectoryField.getText(), baseName + "_html");
                htmlDir.mkdirs();

                // HTML-Datei ins Unterverzeichnis legen
                String htmlFileName = fileNameField.getText();
                File htmlFile = new File(htmlDir, htmlFileName);

                        // CSS-Datei ins Unterverzeichnis kopieren
                        File sourceCss = new File("pandoc", "epub.css");
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
                            String coverFileName = getCoverFileName(coverImageFile);
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
                
                // Markdown-Bilder ins HTML-Verzeichnis kopieren und Pfade anpassen
                copyMarkdownImagesToHtmlDir(markdownFile, htmlDir);
            } else if ("pdf".equals(format)) {
                // PDF-spezifische Optionen für professionelle Formatierung
                command.add("--pdf-engine=xelatex");
                command.add("--toc"); // Inhaltsverzeichnis für PDF
                
                // Markdown-Formatierung explizit aktivieren
                // +raw_html aktiviert HTML-Tags in Markdown (für <br> Unterstützung)
                // -implicit_figures deaktiviert automatische figure-Umgebungen mit Captions
                command.add("--from=markdown+yaml_metadata_block+raw_html+superscript+subscript-implicit_figures-smart");
                command.add("--to=latex");
                
                // Template für PDF verwenden (vereinfachtes XeLaTeX-Template)
                File pdfTemplate = new File("pandoc", "simple-xelatex-template.tex");
                if (pdfTemplate.exists()) {
                    command.add("--template=" + pdfTemplate.getAbsolutePath());
                    logger.debug("Verwende vereinfachtes XeLaTeX-Template: {}", pdfTemplate.getName());
                } else {
                    logger.debug("Kein XeLaTeX-Template gefunden, verwende Standard-Template");
                }
                
                // XeLaTeX-spezifische Optionen für bessere Kompatibilität
                // lang wird vom Template gesetzt (polyglossia), nicht überschreiben
                
                // LaTeX-Optionen für bessere Kompatibilität (Template definiert bereits Fonts und documentclass=book)
                command.add("--variable=geometry:margin=2.5cm");
                command.add("--variable=fontsize:12pt");
                // documentclass wird vom Template gesetzt (book), nicht überschreiben
                command.add("--variable=linestretch:1.2");
                command.add("--variable=numbersections:false"); // Kapitelnummerierung deaktivieren
                
                // Cover-Bild für PDF hinzufügen (falls vorhanden)
                if (!coverImageField.getText().trim().isEmpty()) {
                    File coverImageFile = new File(coverImageField.getText().trim());
                    if (coverImageFile.exists()) {
                        try {
                            // Cover-Bild ins Ausgabeverzeichnis kopieren (XeLaTeX kompiliert dort)
                            // UND ins pandoc-Verzeichnis (als Backup)
                            String coverFileName = getCoverFileName(coverImageFile);
                            if (!outputDir.exists()) {
                                outputDir.mkdirs();
                            }
                            File pandocDir = new File("pandoc");
                            
                            // Kopiere ins Ausgabeverzeichnis
                            File targetCoverOutput = new File(outputDir, coverFileName);
                            Files.copy(coverImageFile.toPath(), targetCoverOutput.toPath(),
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            
                            // Kopiere auch ins pandoc-Verzeichnis
                            File targetCoverPandoc = new File(pandocDir, coverFileName);
                            Files.copy(coverImageFile.toPath(), targetCoverPandoc.toPath(),
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            
                            // Verwende relativen Pfad für LaTeX (relativ zum Ausgabeverzeichnis)
                            // XeLaTeX kompiliert im Ausgabeverzeichnis, daher funktioniert relativer Pfad besser
                            String latexPath = coverFileName;
                            // Falls absoluter Pfad benötigt wird, verwende Forward-Slashes und escape Leerzeichen
                            // String latexPath = targetCoverOutput.getAbsolutePath().replace("\\", "/").replace(" ", "\\ ");
                            command.add("--variable=cover-image:" + latexPath);
                            
                            logger.debug("Cover-Bild-Pfad für LaTeX: {}", latexPath);
                            logger.debug("Cover-Bild für PDF kopiert: {} -> {} und {}", 
                                coverImageFile.getAbsolutePath(), targetCoverOutput.getAbsolutePath(), 
                                targetCoverPandoc.getAbsolutePath());
                            
                        } catch (IOException e) {
                            logger.error("Fehler beim Kopieren des Cover-Bildes für PDF", e);
                        }
                    }
                }
                
                // Markdown-Bilder ins Ausgabeverzeichnis kopieren (XeLaTeX kompiliert dort)
                // UND ins pandoc-Verzeichnis (als Backup)
                File markdownDir = markdownFile.getParentFile();
                File pandocDir = new File("pandoc");
                if (!outputDir.exists()) {
                    outputDir.mkdirs();
                }
                
                // Kopiere Bilder in alle drei Verzeichnisse
                copyMarkdownImagesForPdfExport(markdownFile, markdownDir, pandocDir, outputDir);
                
                // --resource-path setzen: Ausgabe, Markdown UND pandoc-Verzeichnis
                // Auf Windows werden Pfade mit ; getrennt
                String resourcePath = outputDir.getAbsolutePath() + ";" + 
                    markdownDir.getAbsolutePath() + ";" + pandocDir.getAbsolutePath();
                if (pandocHome != null && !pandocHome.getAbsolutePath().equals(pandocDir.getAbsolutePath())) {
                    resourcePath += ";" + pandocHome.getAbsolutePath();
                }
                command.add("--resource-path=" + resourcePath);
                logger.debug("Resource-Path für PDF-Export: {}", resourcePath);
                
                // XeLaTeX-spezifische Engine-Optionen
                command.add("--pdf-engine-opt=-shell-escape");
                command.add("--pdf-engine-opt=-interaction=nonstopmode");
                
                // Lua-Filter für automatische Initialen
                File luaFilter = new File("pandoc", "dropcaps.lua");
                if (luaFilter.exists()) {
                    command.add("--lua-filter=" + luaFilter.getAbsolutePath());
                    logger.debug("Verwende Lua-Filter für automatische Initialen: {}", luaFilter.getName());
                } else {
                    logger.warn("Lua-Filter für Initialen nicht gefunden: {}", luaFilter.getAbsolutePath());
                }
            } else if ("latex".equals(format)) {
                // LaTeX-spezifische Optionen
                command.add("--toc"); // Inhaltsverzeichnis für LaTeX
                
                // Markdown-Formatierung explizit aktivieren
                // +raw_html aktiviert HTML-Tags in Markdown (für <br> Unterstützung)
                // -implicit_figures deaktiviert automatische figure-Umgebungen mit Captions
                command.add("--from=markdown+yaml_metadata_block+raw_html+superscript+subscript-implicit_figures-smart");
                command.add("--to=latex");
                
                // Template für LaTeX verwenden (vereinfachtes XeLaTeX-Template)
                File latexTemplate = new File("pandoc", "simple-xelatex-template.tex");
                if (latexTemplate.exists()) {
                    command.add("--template=" + latexTemplate.getAbsolutePath());
                    logger.debug("Verwende vereinfachtes XeLaTeX-Template: {}", latexTemplate.getName());
                } else {
                    logger.debug("Kein XeLaTeX-Template gefunden, verwende Standard-Template");
                }
                
                // LaTeX-Optionen
                // documentclass wird vom Template gesetzt (book), nicht überschreiben
                command.add("--variable=geometry:margin=2.5cm");
                command.add("--variable=fontsize:12pt");
                command.add("--variable=linestretch:1.2");
                command.add("--variable=numbersections:false"); // Kapitelnummerierung deaktivieren
                
                // Cover-Bild für LaTeX hinzufügen (falls vorhanden)
                if (!coverImageField.getText().trim().isEmpty()) {
                    File coverImageFile = new File(coverImageField.getText().trim());
                    if (coverImageFile.exists()) {
                        try {
                            // Cover-Bild ins pandoc-Verzeichnis kopieren
                            String coverFileName = getCoverFileName(coverImageFile);
                            File pandocDir = new File("pandoc");
                            File targetCover = new File(pandocDir, coverFileName);
                            Files.copy(coverImageFile.toPath(), targetCover.toPath(),
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            
                            // Verwende absoluten Pfad für LaTeX (mit Forward-Slashes)
                            String latexPath = targetCover.getAbsolutePath().replace("\\", "/");
                            command.add("--variable=cover-image:" + latexPath);
                            
                        } catch (IOException e) {
                            logger.error("Fehler beim Kopieren des Cover-Bildes für LaTeX", e);
                        }
                    }
                }
                
                // Markdown-Bilder ins Ausgabeverzeichnis UND pandoc-Verzeichnis kopieren
                // LaTeX-Datei wird im Ausgabeverzeichnis erstellt, daher müssen Bilder dort sein
                File pandocDirLatex = new File("pandoc");
                if (!outputDir.exists()) {
                    outputDir.mkdirs();
                }
                copyMarkdownImagesForLatexExport(markdownFile, outputDir, pandocDirLatex);
            }

            // Standalone für vollständiges Dokument (wenn nicht bereits hinzugefügt)
            if (!command.contains("--standalone")) {
                command.add("--standalone");
            }
            
            File resultFile = "html5".equals(format) ?
                new File(finalOutputPath) : outputFile;
            long previousTimestamp = resultFile.exists() ? resultFile.lastModified() : -1;
            long previousLength = resultFile.exists() ? resultFile.length() : -1;
            boolean deleteFailed = false;

            if (resultFile.exists()) {
                try {
                    Files.delete(resultFile.toPath());
                } catch (IOException e) {
                    deleteFailed = true;
                    logger.warn("Konnte bestehende Ausgabedatei nicht löschen: {}", e.getMessage());
                }
            }
            
            if (deleteFailed) {
                StringBuilder userError = new StringBuilder();
                userError.append("Die Zieldatei \"").append(resultFile.getAbsolutePath())
                        .append("\" konnte nicht überschrieben werden.\n")
                        .append("Bitte schließen Sie die Datei in anderen Programmen und versuchen Sie es erneut.");
                lastExportError = userError.toString();
                return false;
            }

            // Prozess starten - Arbeitsverzeichnis setzen
            ProcessBuilder pb = new ProcessBuilder(command);
            // Für PDF: Arbeitsverzeichnis auf Ausgabeverzeichnis setzen (XeLaTeX kompiliert dort)
            // Für andere Formate: Arbeitsverzeichnis auf Pandoc-Verzeichnis setzen
            if ("pdf".equals(format)) {
                if (outputDir.exists()) {
                    pb.directory(outputDir);
                    logger.debug("Arbeitsverzeichnis für PDF-Export: {}", outputDir.getAbsolutePath());
                } else {
                    pb.directory(pandocHome != null ? pandocHome : new File("pandoc"));
                }
            } else {
                pb.directory(pandocHome != null ? pandocHome : new File("pandoc")); // Arbeitsverzeichnis auf Pandoc setzen
            }
            pb.environment().put("PATH", System.getenv("PATH")); // PATH weitergeben
            
            // MiKTeX-Update-Warnungen deaktivieren
            pb.environment().put("MIKTEX_DISABLE_UPDATE_CHECK", "1");
            pb.environment().put("MIKTEX_DISABLE_INSTALLER", "1");
            pb.environment().put("MIKTEX_DISABLE_AUTO_INSTALL", "1");

            // Logge den vollständigen Pandoc-Befehl für Debugging
            logger.info("Starte Pandoc mit Befehl: {}", String.join(" ", command));
            logger.debug("Arbeitsverzeichnis: {}", pb.directory() != null ? pb.directory().getAbsolutePath() : "null");

            Process process = pb.start();
            logger.info("Pandoc-Prozess gestartet, PID: {}", process.pid());

            // Standard-Output und Standard-Error auslesen für bessere Fehlerdiagnose
            StringBuilder output = new StringBuilder();
            StringBuilder error = new StringBuilder();
            
            // Threads für paralleles Auslesen von Output und Error mit UTF-8 Kodierung
            Thread outputThread = new Thread(() -> {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                } catch (IOException e) {
                    logger.warn("Fehler beim Lesen des pandoc-Outputs: {}", e.getMessage());
                }
            });
            
            Thread errorThread = new Thread(() -> {
                try {
                    // Lese die Bytes zuerst, um verschiedene Kodierungen zu probieren
                    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = process.getErrorStream().read(buffer)) != -1) {
                        baos.write(buffer, 0, bytesRead);
                    }
                    byte[] errorBytes = baos.toByteArray();
                    
                    if (errorBytes.length == 0) {
                        return;
                    }
                    
                    // Probiere verschiedene Kodierungen in der Reihenfolge ihrer Wahrscheinlichkeit
                    java.util.List<String> encodingList = new java.util.ArrayList<>();
                    encodingList.add("Windows-1252");  // Windows-Standard für deutsche Umlaute
                    encodingList.add("CP850");          // DOS-Kodierung (oft auf Windows verwendet)
                    encodingList.add("ISO-8859-1");     // Latin-1
                    encodingList.add("UTF-8");          // UTF-8
                    encodingList.add("Windows-1250");   // Mittel-/Osteuropa
                    
                    // Füge System-Standard-Kodierung hinzu, falls verfügbar
                    try {
                        String defaultEncoding = java.nio.charset.Charset.defaultCharset().name();
                        if (!encodingList.contains(defaultEncoding)) {
                            encodingList.add(defaultEncoding);
                        }
                    } catch (Exception e) {
                        // Ignoriere Fehler
                    }
                    
                    String[] encodings = encodingList.toArray(new String[0]);
                    
                    String bestErrorText = null;
                    int bestScore = -1;
                    
                    for (String encodingName : encodings) {
                        try {
                            java.nio.charset.Charset charset = java.nio.charset.Charset.forName(encodingName);
                            String testText = new String(errorBytes, charset);
                            
                            // Bewerte die Kodierung: Prüfe auf gültige deutsche Zeichen und vermeide "?"
                            int score = 0;
                            boolean hasValidUmlauts = false;
                            int questionMarkCount = 0;
                            
                            for (char c : testText.toCharArray()) {
                                // Prüfe auf deutsche Umlaute
                                if (c == 'ä' || c == 'ö' || c == 'ü' || c == 'Ä' || c == 'Ö' || c == 'Ü' || c == 'ß') {
                                    hasValidUmlauts = true;
                                    score += 10; // Umlaute sind sehr wertvoll
                                } else if (c == '?') {
                                    questionMarkCount++;
                                    score -= 5; // "?" deutet auf falsche Kodierung hin
                                } else if (c >= 32 && c < 127) {
                                    score++; // Normale ASCII-Zeichen
                                } else if (c >= 160 && c <= 255) {
                                    score += 2; // Erweiterte Zeichen
                                }
                            }
                            
                            // Bonus für Kodierungen mit gültigen Umlauten und wenigen "?"
                            if (hasValidUmlauts && questionMarkCount == 0) {
                                score += 100; // Sehr hoher Bonus
                            }
                            
                            // Wenn diese Kodierung besser ist, verwende sie
                            if (score > bestScore) {
                                bestScore = score;
                                bestErrorText = testText;
                            }
                        } catch (Exception e) {
                            // Diese Kodierung funktioniert nicht, probiere nächste
                            continue;
                        }
                    }
                    
                    // Verwende die beste gefundene Kodierung oder Fallback
                    if (bestErrorText != null) {
                        error.append(bestErrorText);
                    } else {
                        // Letzter Fallback: System-Standard
                        error.append(new String(errorBytes));
                    }
                } catch (IOException e) {
                    logger.warn("Fehler beim Lesen des pandoc-Error-Streams: {}", e.getMessage());
                }
            });
            
            outputThread.start();
            errorThread.start();

            // Warten auf Beendigung mit Timeout (1 Minute)
            // Wenn Pandoc beim Verarbeiten der Reference-DOC hängen bleibt, wird der Prozess nach Timeout beendet
            logger.info("Warte auf Pandoc-Prozess-Beendigung (Timeout: 1 Minute)...");
            int exitCode = -1;
            boolean processCompleted = false;
            long startTime = System.currentTimeMillis();
            try {
                processCompleted = process.waitFor(1, java.util.concurrent.TimeUnit.MINUTES);
                long duration = System.currentTimeMillis() - startTime;
                if (processCompleted) {
                    exitCode = process.exitValue();
                    logger.info("Pandoc-Prozess beendet nach {} ms mit Exit-Code: {}", duration, exitCode);
                } else {
                    logger.error("Pandoc-Prozess hat das Timeout überschritten (1 Minute, {} ms). Prozess wird beendet.", duration);
                    process.destroyForcibly();
                    exitCode = -1;
                }
            } catch (InterruptedException e) {
                long duration = System.currentTimeMillis() - startTime;
                logger.error("Warten auf Pandoc-Prozess wurde nach {} ms unterbrochen", duration);
                process.destroyForcibly();
                Thread.currentThread().interrupt();
                exitCode = -1;
            }
            
            // Warten bis beide Threads fertig sind (mit Timeout)
            try {
                outputThread.join(10000); // Max 10 Sekunden warten
                if (outputThread.isAlive()) {
                    logger.warn("Output-Thread konnte nicht beendet werden, unterbreche Thread");
                    outputThread.interrupt(); // Thread explizit unterbrechen
                }
            } catch (InterruptedException e) {
                logger.warn("Warten auf Output-Thread unterbrochen");
                outputThread.interrupt(); // Thread explizit unterbrechen
                Thread.currentThread().interrupt();
            }
            
            try {
                errorThread.join(10000); // Max 10 Sekunden warten
                if (errorThread.isAlive()) {
                    logger.warn("Error-Thread konnte nicht beendet werden, unterbreche Thread");
                    errorThread.interrupt(); // Thread explizit unterbrechen
                }
            } catch (InterruptedException e) {
                logger.warn("Warten auf Error-Thread unterbrochen");
                errorThread.interrupt(); // Thread explizit unterbrechen
                Thread.currentThread().interrupt();
            }

            if (error.length() > 0) {
                logger.error("Pandoc-Error:\n{}", error.toString());
                // Detailliertes Logging für Bildprobleme
                if (error.toString().contains("Unable to load picture") || 
                    error.toString().contains("File") && error.toString().contains("not found")) {
                    logger.error("=== BILDPROBLEM ERKANNT ===");
                    logger.error("Error-Output: {}", error.toString());
                    logger.error("Output-Output: {}", output.toString());
                }
            }
            
            if (output.length() > 0 && "pdf".equals(format)) {
                logger.debug("Pandoc-Output:\n{}", output.toString());
            }

            boolean success = exitCode == 0
                && resultFile.exists()
                && resultFile.length() > 0
                && (previousTimestamp == -1 || resultFile.lastModified() > previousTimestamp || resultFile.length() != previousLength);

            if (success) {
                logger.debug("Export erfolgreich erstellt: {}", resultFile.getAbsolutePath());
                
                // Cleanup: Lösche kopierte Bilder aus dem pandoc-Verzeichnis nach erfolgreichem Export
                cleanupCopiedImages();
                
                // Post-Processing in separaten Threads ausführen, damit der Export sofort zurückgegeben wird
                final File finalResultFile = resultFile;
                
                // Post-Processing für DOCX: Abstract-Titel ersetzen und Cover-Bild hinzufügen
                if ("docx".equals(format)) {
                    Thread docxPostProcessThread = new Thread(() -> {
                        try {
                            Thread.sleep(200); // Kurze Wartezeit, damit die Datei nicht mehr gesperrt ist
                            postProcessDocx(finalResultFile);
                        } catch (Exception e) {
                            logger.warn("DOCX Post-Processing fehlgeschlagen: {}", e.getMessage());
                        }
                    });
                    docxPostProcessThread.setDaemon(true);
                    docxPostProcessThread.start();
                }
                
                // Post-Processing für EPUB: Abstract-Titel ersetzen
                if ("epub3".equals(format) || "epub".equals(format)) {
                    Thread epubPostProcessThread = new Thread(() -> {
                        try {
                            Thread.sleep(200); // Kurze Wartezeit, damit die Datei nicht mehr gesperrt ist
                            
                            // Prüfe ob Thread unterbrochen wurde
                            if (Thread.currentThread().isInterrupted()) {
                                logger.debug("EPUB Post-Processing wurde unterbrochen");
                                return;
                            }
                            
                            postProcessEpub(finalResultFile);
                            
                            logger.debug("EPUB Post-Processing erfolgreich abgeschlossen");
                        } catch (InterruptedException e) {
                            logger.debug("EPUB Post-Processing wurde unterbrochen");
                            Thread.currentThread().interrupt();
                        } catch (Exception e) {
                            logger.warn("EPUB Post-Processing fehlgeschlagen: {}", e.getMessage());
                        }
                    });
                    epubPostProcessThread.setDaemon(true);
                    epubPostProcessThread.setName("EPUB-PostProcessing");
                    epubPostProcessThread.start();
                    
                    // Timeout-Thread: Beendet den Post-Processing-Thread nach 5 Minuten
                    Thread timeoutThread = new Thread(() -> {
                        try {
                            Thread.sleep(5 * 60 * 1000); // 5 Minuten
                            if (epubPostProcessThread.isAlive()) {
                                logger.warn("EPUB Post-Processing dauert zu lange, unterbreche Thread");
                                epubPostProcessThread.interrupt();
                            }
                        } catch (InterruptedException e) {
                            // Timeout-Thread wurde unterbrochen, ignorieren
                        }
                    });
                    timeoutThread.setDaemon(true);
                    timeoutThread.setName("EPUB-PostProcessing-Timeout");
                    timeoutThread.start();
                }
                
                // Export sofort als erfolgreich zurückgeben, Post-Processing läuft im Hintergrund
                return true;
            } else {
                logger.error("Pandoc-Export fehlgeschlagen - Datei nicht erstellt oder nicht überschrieben (Exit-Code: {})", exitCode);
                
                // Fehlermeldung für Benutzer zusammenstellen
                StringBuilder userErrorMessage = new StringBuilder();
                userErrorMessage.append("Pandoc-Export fehlgeschlagen (Exit-Code: ").append(exitCode).append(")\n\n");
                
                if (previousTimestamp != -1 && resultFile.exists() && resultFile.lastModified() == previousTimestamp) {
                    userErrorMessage.append("Die bestehende Zieldatei konnte nicht überschrieben werden. ")
                        .append("Bitte schließen Sie die Datei in anderen Programmen und versuchen Sie es erneut.\n\n");
                }
                
                if (error.length() > 0) {
                    logger.error("Detaillierte Fehlermeldung:\n{}", error.toString());
                    
                    // Fehlermeldung direkt verwenden (bereits UTF-8 durch InputStreamReader)
                    String errorText = error.toString().trim();
                    
                    // Wichtige Fehlermeldungen extrahieren und formatieren
                    if (errorText.contains("Unable to load picture") || errorText.contains("File") && errorText.contains("not found")) {
                        userErrorMessage.append("⚠️ Bildproblem erkannt:\n");
                        userErrorMessage.append("Ein Bild konnte nicht geladen werden.\n");
                        userErrorMessage.append("Bitte überprüfen Sie die Bildpfade in Ihrem Markdown-Dokument.\n\n");
                    }
                    
                    // Die letzten Zeilen der Fehlermeldung anzeigen (meist die wichtigsten)
                    String[] errorLines = errorText.split("\n");
                    int linesToShow = Math.min(10, errorLines.length); // Maximal 10 Zeilen
                    if (errorLines.length > linesToShow) {
                        userErrorMessage.append("Letzte ").append(linesToShow).append(" Zeilen der Fehlermeldung:\n");
                        for (int i = errorLines.length - linesToShow; i < errorLines.length; i++) {
                            if (errorLines[i].trim().length() > 0) {
                                userErrorMessage.append(errorLines[i]).append("\n");
                            }
                        }
                    } else {
                        userErrorMessage.append("Fehlermeldung:\n").append(errorText);
                    }
                } else {
                    userErrorMessage.append("Keine detaillierte Fehlermeldung verfügbar.\n");
                    userErrorMessage.append("Bitte überprüfen Sie die Log-Datei für weitere Informationen.");
                }
                
                // Fehlermeldung speichern für Anzeige im Dialog
                lastExportError = userErrorMessage.toString();
                
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
            
            // Fehlermeldung für Benutzer zusammenstellen
            StringBuilder userErrorMessage = new StringBuilder();
            userErrorMessage.append("Unerwarteter Fehler beim Export:\n\n");
            userErrorMessage.append("Fehlertyp: ").append(e.getClass().getSimpleName()).append("\n");
            userErrorMessage.append("Fehlermeldung: ").append(e.getMessage() != null ? e.getMessage() : "Keine Details verfügbar").append("\n\n");
            
            // Zusätzliche Informationen für häufige Fehler
            if (e.getMessage() != null) {
                String msg = e.getMessage().toLowerCase();
                if (msg.contains("permission") || msg.contains("zugriff") || msg.contains("access")) {
                    userErrorMessage.append("💡 Mögliche Ursache: Die Datei ist möglicherweise in einem anderen Programm geöffnet.\n");
                    userErrorMessage.append("   Bitte schließen Sie die Datei und versuchen Sie es erneut.\n\n");
                } else if (msg.contains("disk") || msg.contains("space") || msg.contains("speicher")) {
                    userErrorMessage.append("💡 Mögliche Ursache: Nicht genügend Speicherplatz auf dem Datenträger.\n\n");
                } else if (msg.contains("path") || msg.contains("pfad") || msg.contains("not found")) {
                    userErrorMessage.append("💡 Mögliche Ursache: Ein Pfad ist ungültig oder eine Datei/Verzeichnis existiert nicht.\n\n");
                }
            }
            
            userErrorMessage.append("Für weitere Details siehe die Log-Datei.");
            
            // Fehlermeldung speichern für Anzeige im Dialog
            lastExportError = userErrorMessage.toString();
            
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
            fallbackCommand.add("--from=markdown-yaml_metadata_block-smart");
            fallbackCommand.add("--to=pdf");
            fallbackCommand.add("--pdf-engine=pdflatex");
            fallbackCommand.add("--toc");
            fallbackCommand.add("--standalone");
            
            // Vereinfachte LaTeX-Optionen für bessere Kompatibilität (ohne XeLaTeX-spezifische Fonts)
            // lang wird vom Template gesetzt, nicht überschreiben
            fallbackCommand.add("--variable=geometry:margin=2.5cm");
            fallbackCommand.add("--variable=fontsize:11pt");
            // documentclass wird vom Template gesetzt (book), nicht überschreiben
            fallbackCommand.add("--variable=linestretch:1.2");
            fallbackCommand.add("--variable=numbersections:false"); // Kapitelnummerierung deaktivieren
            
            // pdflatex-spezifische Optionen
            fallbackCommand.add("--pdf-engine-opt=-interaction=nonstopmode");
            
            logger.debug("Versuche PDF-Fallback mit pdflatex (ohne Template)...");
            
            ProcessBuilder pb = new ProcessBuilder(fallbackCommand);
            pb.directory(pandocHome != null ? pandocHome : new File("pandoc"));
            pb.environment().put("PATH", System.getenv("PATH"));
            
            Process process = pb.start();
            int exitCode = process.waitFor();
            
            if (outputFile.exists() && outputFile.length() > 0) {
                logger.debug("PDF-Fallback erfolgreich mit pdflatex erstellt: {}", outputFile.getAbsolutePath());
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
            File pandocExe = new File("pandoc", "pandoc.exe");
            if (pandocExe.exists()) {
                pandocHome = pandocExe.getParentFile();
                return true;
            }

            // 2) Versuche, pandoc.zip in pandoc zu finden und dort zu entpacken
            File zip = new File("pandoc", "pandoc.zip");
            if (!zip.exists()) {
                // Fallback: im Programmverzeichnis
                zip = new File("pandoc.zip");
            }
            if (!zip.exists()) {
                logger.warn("pandoc.zip nicht gefunden – kann Pandoc nicht automatisch installieren");
                return false;
            }

            // Zielordner ist pandoc
            File targetDir = new File("pandoc");
            if (!targetDir.exists()) targetDir.mkdirs();
            boolean ok = unzip(zip, targetDir);
            if (!ok) {
                logger.error("Entpacken von pandoc.zip fehlgeschlagen");
                return false;
            }

            // Nach dem Entpacken erneut prüfen
            pandocExe = new File("pandoc", "pandoc.exe");
            if (pandocExe.exists()) {
                pandocHome = pandocExe.getParentFile();
                logger.debug("Pandoc erfolgreich entpackt: {}", pandocExe.getAbsolutePath());
                return true;
            }

            // Fallback: Manche ZIPs enthalten einen Unterordner – versuche zu finden
            File[] candidates = new File(".").listFiles((dir, name) -> name.toLowerCase().startsWith("pandoc"));
            if (candidates != null) {
                for (File c : candidates) {
                    File exe = new File(c, "pandoc.exe");
                    if (exe.exists()) {
                        pandocHome = c;
                        logger.debug("Pandoc in '{}' gefunden", c.getAbsolutePath());
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

        // Cover-Bild für EPUB3, HTML5 und DOCX anzeigen
        boolean showCover = format.equals("epub3") || format.equals("html5") || format.equals("docx");
        coverImageBox.setVisible(showCover);
        coverImageBox.setManaged(showCover);
        
        // Initialen-Checkbox nur für DOCX anzeigen
        updateInitialsVisibility();
        
        // Bildgröße nur für DOCX anzeigen
        boolean showImageSize = "docx".equals(format);
        imageSizeBox.setVisible(showImageSize);
        imageSizeBox.setManaged(showImageSize);
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
    
    /**
     * Generiert den Dateinamen für das Cover-Bild.
     * Behält den ursprünglichen Dateinamen bei, wenn er bereits "cover" enthält.
     */
    private String getCoverFileName(File coverImageFile) {
        String originalName = coverImageFile.getName().toLowerCase();
        if (originalName.contains("cover") && originalName.contains(".")) {
            // Verwende den ursprünglichen Dateinamen (z.B. cover_image.png)
            return coverImageFile.getName();
        } else {
            // Erstelle neuen Dateinamen mit "cover." + Extension
            return "cover." + getFileExtension(coverImageFile.getName());
        }
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
        // showAndWait mit Owner für Zentrierung über dem Export-Fenster
        alert.showAndWait(this);
    }
    
    private void showErrorWithHelp(String message) {
        CustomAlert alert = new CustomAlert(Alert.AlertType.ERROR, "Export Fehler");
        alert.setHeaderText("Export fehlgeschlagen");
        
        // VBox für Fehlermeldungen
        VBox contentBox = new VBox();
        contentBox.setPadding(new Insets(10));
        
        // TextArea erstellen - markierbar, aber nicht editierbar
        TextArea contentTextArea = new TextArea(message);
        contentTextArea.setEditable(false); // Nicht editierbar, aber markierbar
        contentTextArea.setWrapText(true);
        // Font mit guter UTF-8 Unterstützung für korrekte Zeichendarstellung
        // Verwende System-Fonts, die UTF-8 gut unterstützen (Arial, Segoe UI statt Monospace)
        contentTextArea.setStyle("-fx-font-family: 'Arial', 'Segoe UI', 'Tahoma', sans-serif; -fx-font-size: 11px;");
        contentTextArea.setPrefWidth(600);
        contentTextArea.setPrefHeight(400);
        
        contentBox.getChildren().add(contentTextArea);
        alert.setCustomContent(contentBox);
        alert.applyTheme(currentThemeIndex);
        
        // Hilfebutton hinzufügen
        ButtonType helpButtonType = new ButtonType("Hilfe", ButtonBar.ButtonData.HELP);
        alert.getButtonTypes().add(helpButtonType);
        
        // showAndWait mit Owner für Zentrierung über dem Export-Fenster
        alert.showAndWait(this).ifPresent(buttonType -> {
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
            logger.debug("Post-Processing für DOCX: {}", docxFile.getName());
            
            // DOCX mit Apache POI öffnen und bearbeiten
            try (FileInputStream fis = new FileInputStream(docxFile);
                 XWPFDocument document = new XWPFDocument(fis)) {
                
                // ZUERST: "Abstract" durch "Zusammenfassung" ersetzen und Zusammenfassung im Blocksatz formatieren
                boolean inAbstractSection = false;
                for (XWPFParagraph paragraph : document.getParagraphs()) {
                    String paragraphText = paragraph.getText();
                    boolean isAbstractTitle = false;
                    
                    // Prüfe ob dieser Absatz der Titel "Abstract" oder "Zusammenfassung" ist
                    if (paragraphText != null) {
                        String trimmedText = paragraphText.trim();
                        if (trimmedText.equals("Abstract") || trimmedText.equals("Zusammenfassung")) {
                            isAbstractTitle = true;
                            inAbstractSection = true;
                            
                            // Ersetze "Abstract" durch "Zusammenfassung"
                            for (XWPFRun run : paragraph.getRuns()) {
                                String text = run.getText(0);
                                if (text != null && text.contains("Abstract")) {
                                    String newText = text.replace("Abstract", "Zusammenfassung");
                                    run.setText(newText, 0);
                                }
                            }
                        } else if (inAbstractSection) {
                            // Prüfe ob wir eine neue Überschrift erreicht haben (beginnt mit # oder ist fett/größer)
                            // Wenn der Absatz leer ist oder eine Überschrift, beende den Abstract-Bereich
                            if (trimmedText.isEmpty()) {
                                // Leere Absätze ignorieren
                            } else if (paragraph.getStyle() != null && 
                                      (paragraph.getStyle().contains("Heading") || 
                                       paragraph.getStyle().contains("heading"))) {
                                // Neue Überschrift gefunden - Abstract-Bereich beenden
                                inAbstractSection = false;
                            } else {
                                // Dies ist ein Abstract-Absatz - formatiere im Blocksatz
                                paragraph.setAlignment(ParagraphAlignment.BOTH); // Blocksatz
                            }
                        }
                    }
                    
                    // Ersetze "Abstract" in allen Runs (für den Fall, dass es nicht im Titel steht)
                    if (!isAbstractTitle) {
                        for (XWPFRun run : paragraph.getRuns()) {
                            String text = run.getText(0);
                            if (text != null && text.contains("Abstract")) {
                                String newText = text.replace("Abstract", "Zusammenfassung");
                                run.setText(newText, 0);
                            }
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
                
                // Bilder zentrieren
                for (XWPFParagraph paragraph : document.getParagraphs()) {
                    boolean hasImage = false;
                    for (XWPFRun run : paragraph.getRuns()) {
                        if (run.getEmbeddedPictures().size() > 0) {
                            hasImage = true;
                            break;
                        }
                    }
                    if (hasImage) {
                        paragraph.setAlignment(ParagraphAlignment.CENTER);
                    }
                }
                
                // Zentrierte Absätze zentrieren (aus replaceHtmlTagsInMarkdown gesammelt)
                for (XWPFParagraph paragraph : document.getParagraphs()) {
                    String text = paragraph.getText();
                    if (text != null) {
                        String trimmedText = text.trim();
                        // Prüfe ob dieser Absatz in der Liste der zentrierten Absätze ist
                        for (String centeredText : centeredParagraphs) {
                            // Normalisiere beide Texte für Vergleich (Leerzeichen, Zeilenumbrüche)
                            String normalizedParagraph = trimmedText.replaceAll("\\s+", " ").trim();
                            String normalizedCentered = centeredText.replaceAll("\\s+", " ").trim();
                            if (normalizedParagraph.equals(normalizedCentered)) {
                                paragraph.setAlignment(ParagraphAlignment.CENTER);
                                logger.debug("Absatz zentriert: '{}'", trimmedText);
                                break;
                            }
                        }
                    }
                }
                
                // Zentrierte Absätze auch in Tabellen zentrieren
                for (XWPFTable table : document.getTables()) {
                    for (XWPFTableRow row : table.getRows()) {
                        for (XWPFTableCell cell : row.getTableCells()) {
                            for (XWPFParagraph paragraph : cell.getParagraphs()) {
                                String text = paragraph.getText();
                                if (text != null) {
                                    String trimmedText = text.trim();
                                    for (String centeredText : centeredParagraphs) {
                                        String normalizedParagraph = trimmedText.replaceAll("\\s+", " ").trim();
                                        String normalizedCentered = centeredText.replaceAll("\\s+", " ").trim();
                                        if (normalizedParagraph.equals(normalizedCentered)) {
                                            paragraph.setAlignment(ParagraphAlignment.CENTER);
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                // ◆◆◆ Absätze zentrieren und hellblau färben (Trenner)
                for (XWPFParagraph paragraph : document.getParagraphs()) {
                    String text = paragraph.getText();
                    if (text != null && text.trim().equals("◆◆◆")) {
                        paragraph.setAlignment(ParagraphAlignment.CENTER);
                        // Hellblau färben (#87CEEB = Sky Blue)
                        for (XWPFRun run : paragraph.getRuns()) {
                            run.setColor("87CEEB");
                        }
                    }
                }
                
                // ◆◆◆ auch in Tabellen zentrieren und hellblau färben
                for (XWPFTable table : document.getTables()) {
                    for (XWPFTableRow row : table.getRows()) {
                        for (XWPFTableCell cell : row.getTableCells()) {
                            for (XWPFParagraph paragraph : cell.getParagraphs()) {
                                String text = paragraph.getText();
                                if (text != null && text.trim().equals("◆◆◆")) {
                                    paragraph.setAlignment(ParagraphAlignment.CENTER);
                                    // Hellblau färben
                                    for (XWPFRun run : paragraph.getRuns()) {
                                        run.setColor("87CEEB");
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Alt-Text aus Bildunterschriften entfernen (DEAKTIVIERT)
                // WICHTIG: Diese Logik wurde deaktiviert, da sie fälschlicherweise legitime Bildunterschriften
                // und einzeilige Absätze nach Bildern entfernt hat.
                // Alt-Text wird bereits im Markdown entfernt (siehe replaceHtmlTagsInMarkdown)
                
                // DANN: Hierarchische Nummerierung für Listen aktivieren
                logger.info("Aktiviere hierarchische Nummerierung für Listen");
                enableHierarchicalListNumbering(document);
                
                // DANN: Initialen für "First Paragraph" Absätze hinzufügen (nur wenn aktiviert)
                if (initialsCheckBox != null && initialsCheckBox.isSelected()) {
                    logger.debug("Initialen-Checkbox ist aktiviert - füge Initialen hinzu");
                    addInitialsToFirstParagraphs(document);
                } else {
                    logger.debug("Initialen-Checkbox ist deaktiviert - überspringe Initialen");
                }
                
                // DANN: Blockquotes mit Word-Absatzformat "Zitat" formatieren
                logger.debug("Formatiere Blockquotes mit Word-Absatzformat 'Zitat'");
                formatBlockquotesAsQuoteStyle(document);
                
                // DANN: Tabellen formatieren (Breite und Rahmen)
                logger.debug("Formatiere Tabellen mit Breite und Rahmen");
                formatTables(document);
                
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
                            
                            logger.debug("Cover-Bild in DOCX eingefügt: {}", coverImageFile.getName());
                            
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
            
            logger.debug("DOCX Post-Processing abgeschlossen - 'Abstract' durch 'Zusammenfassung' ersetzt und Cover-Bild hinzugefügt");
            
        } catch (Exception e) {
            logger.warn("DOCX Post-Processing fehlgeschlagen: {}", e.getMessage());
        }
    }
    
    /**
     * Post-Processing für EPUB: Ersetzt "Abstract" durch "Zusammenfassung"
     */
    private void postProcessEpub(File epubFile) {
        try {
            logger.debug("Post-Processing für EPUB: {}", epubFile.getName());
            
            // EPUB ist eine ZIP-Datei - entpacken, bearbeiten und neu packen
            File tempDir = new File("temp_epub_processing");
            if (tempDir.exists()) {
                deleteDirectory(tempDir);
            }
            tempDir.mkdirs();
            
            // EPUB entpacken
            try (java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(epubFile)) {
                java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zipFile.entries();
                
                while (entries.hasMoreElements()) {
                    // Prüfe ob Thread unterbrochen wurde
                    if (Thread.currentThread().isInterrupted()) {
                        logger.warn("EPUB Post-Processing wurde während des Entpackens unterbrochen");
                        throw new InterruptedException("Thread wurde unterbrochen");
                    }
                    
                    java.util.zip.ZipEntry entry = entries.nextElement();
                    File entryFile = new File(tempDir, entry.getName());
                    
                    // Verzeichnis erstellen falls nötig
                    File parentDir = entryFile.getParentFile();
                    if (parentDir != null && !parentDir.exists()) {
                        parentDir.mkdirs();
                    }
                    
                    if (!entry.isDirectory()) {
                        try (java.io.InputStream is = zipFile.getInputStream(entry);
                             java.io.FileOutputStream fos = new java.io.FileOutputStream(entryFile)) {
                            
                            byte[] buffer = new byte[1024];
                            int length;
                            while ((length = is.read(buffer)) > 0) {
                                // Prüfe ob Thread unterbrochen wurde
                                if (Thread.currentThread().isInterrupted()) {
                                    throw new InterruptedException("Thread wurde unterbrochen");
                                }
                                fos.write(buffer, 0, length);
                            }
                        }
                    }
                }
            } // ZipFile wird hier geschlossen
            
            // Kurze Wartezeit, damit die Datei definitiv geschlossen ist
            Thread.sleep(100);
            
            // Prüfe ob Thread unterbrochen wurde
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Thread wurde unterbrochen");
            }
            
            // HTML-Dateien bearbeiten
            processHtmlFilesInDirectory(tempDir);
            
            // Prüfe ob Thread unterbrochen wurde
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Thread wurde unterbrochen");
            }
            
            // TOC-Dateien bearbeiten (nav.xhtml und toc.ncx)
            processTocFiles(tempDir);
            
            // Prüfe ob Thread unterbrochen wurde
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Thread wurde unterbrochen");
            }
            
            // EPUB neu erstellen - erst temporäre Datei, dann umbenennen (atomar)
            File tempEpubFile = new File(epubFile.getParentFile(), epubFile.getName() + ".tmp");
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tempEpubFile);
                 java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(fos)) {
                
                addDirectoryToZip(tempDir, tempDir, zos);
            }
            
            // Alte Datei löschen und temporäre Datei umbenennen (atomar)
            if (epubFile.exists()) {
                if (!epubFile.delete()) {
                    logger.warn("Konnte alte EPUB-Datei nicht löschen, versuche trotzdem fortzufahren");
                }
            }
            if (!tempEpubFile.renameTo(epubFile)) {
                // Fallback: Kopieren statt Umbenennen
                try {
                    Files.copy(tempEpubFile.toPath(), epubFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    tempEpubFile.delete();
                } catch (IOException e) {
                    logger.error("Fehler beim Ersetzen der EPUB-Datei: {}", e.getMessage());
                    throw e;
                }
            }
            
            // Temporäres Verzeichnis löschen
            deleteDirectory(tempDir);
            
            logger.debug("EPUB Post-Processing abgeschlossen - 'Abstract' durch 'Zusammenfassung' ersetzt, Alt-Texte entfernt und Reihenfolge korrigiert");
            
        } catch (Exception e) {
            logger.warn("EPUB Post-Processing fehlgeschlagen: {}", e.getMessage());
        }
    }
    
    /**
     * Verarbeitet alle HTML-Dateien in einem Verzeichnis rekursiv
     */
    private void processHtmlFilesInDirectory(File directory) {
        processHtmlFilesInDirectory(directory, new java.util.HashSet<>());
    }
    
    private void processHtmlFilesInDirectory(File directory, java.util.Set<String> visitedPaths) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            return;
        }
        
        // Prüfe ob Thread unterbrochen wurde
        if (Thread.currentThread().isInterrupted()) {
            return;
        }
        
        // Schutz gegen zyklische Verweise
        try {
            String canonicalPath = directory.getCanonicalPath();
            if (visitedPaths.contains(canonicalPath)) {
                logger.warn("Zyklischer Verweis erkannt, überspringe: {}", canonicalPath);
                return;
            }
            visitedPaths.add(canonicalPath);
        } catch (IOException e) {
            logger.warn("Fehler beim Ermitteln des kanonischen Pfads: {}", e.getMessage());
            return;
        }
        
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                // Prüfe ob Thread unterbrochen wurde
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                
                if (file.isDirectory()) {
                    processHtmlFilesInDirectory(file, visitedPaths);
                } else if (file.getName().toLowerCase().endsWith(".html") || 
                          file.getName().toLowerCase().endsWith(".xhtml") ||
                          file.getName().toLowerCase().endsWith(".opf") ||
                          file.getName().toLowerCase().endsWith(".ncx")) {
                    processHtmlFile(file);
                }
            }
        }
    }
    
    /**
     * Verarbeitet eine einzelne HTML-Datei und ersetzt "Abstract" durch "Zusammenfassung",
     * entfernt Alt-Text aus Bildern und korrigiert die Reihenfolge (Bild vor Überschrift)
     */
    private void processHtmlFile(File htmlFile) {
        try {
            String content = Files.readString(htmlFile.toPath(), StandardCharsets.UTF_8);
            String originalContent = content;
            
            // "Abstract" durch "Zusammenfassung" ersetzen (alle Vorkommen)
            content = content.replaceAll("(?i)Abstract", "Zusammenfassung");
            
            // Alt-Text aus Bildern entfernen (alt-Attribut entfernen oder leeren)
            // Pattern: <img alt="..." src="..."> -> <img src="...">
            content = content.replaceAll("(<img[^>]*)\\s+alt=\"[^\"]*\"([^>]*>)", "$1$2");
            content = content.replaceAll("(<img[^>]*)\\s+alt='[^']*'([^>]*>)", "$1$2");
            
            // Alt-Text aus Bildunterschriften entfernen (falls als Text nach Bildern)
            // Pattern: <img ...><p>Alt-Text</p> -> <img ...>
            content = content.replaceAll("(<img[^>]*>)\\s*<p[^>]*>([^<]{1,100})</p>", "$1");
            
            // Doppelte Überschriften entfernen (falls Pandoc den Titel noch einmal einfügt)
            // Pattern: <img ...><h1>Titel</h1>...<h1>Titel</h1> -> <img ...><h1>Titel</h1>...
            // Entfernt identische Überschriften, die direkt nach einem Bild kommen
            content = content.replaceAll(
                "(?s)(<img[^>]*>\\s*<h[1-6][^>]*>([^<]+)</h[1-6]>)\\s*.*?<h[1-6][^>]*>\\2</h[1-6]>",
                "$1"
            );
            
            // Auch ohne Bild: Doppelte identische Überschriften direkt nacheinander entfernen
            content = content.replaceAll(
                "(?s)(<h[1-6][^>]*>([^<]+)</h[1-6]>)\\s*(?:<p[^>]*>.*?</p>\\s*)*<h[1-6][^>]*>\\2</h[1-6]>",
                "$1"
            );
            
            // Nur schreiben wenn sich etwas geändert hat
            if (!content.equals(originalContent)) {
                Files.write(htmlFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
                logger.debug("HTML-Datei bearbeitet: {}", htmlFile.getName());
            }
            
        } catch (IOException e) {
            logger.warn("Fehler beim Bearbeiten der HTML-Datei {}: {}", htmlFile.getName(), e.getMessage());
        }
    }
    
    /**
     * Bearbeitet die TOC-Dateien (nav.xhtml und toc.ncx), um den Buchtitel aus dem TOC zu entfernen
     * und "Abstract" durch "Zusammenfassung" zu ersetzen
     */
    private void processTocFiles(File tempDir) {
        try {
            // nav.xhtml bearbeiten
            File navFile = findFileRecursive(tempDir, "nav.xhtml");
            
            if (navFile != null && navFile.exists()) {
                String navContent = Files.readString(navFile.toPath(), StandardCharsets.UTF_8);
                String originalNavContent = navContent;
                
                // "Abstract" durch "Zusammenfassung" ersetzen
                navContent = navContent.replaceAll("(?i)Abstract", "Zusammenfassung");
                
                // Ersten TOC-Eintrag entfernen (Buchtitel als erstes Kapitel)
                // Entfernt den ersten <li> Eintrag nach <ol class="toc">
                // Verwende String-Manipulation statt Regex für mehr Zuverlässigkeit
                String beforeReplace = navContent;
                
                // Finde die Position von <ol class="toc">
                int olStart = navContent.indexOf("<ol");
                if (olStart >= 0) {
                    // Prüfe ob es class="toc" enthält
                    String olTag = navContent.substring(olStart, Math.min(olStart + 50, navContent.length()));
                    if (olTag.contains("class=\"toc\"") || olTag.contains("class='toc'")) {
                        // Finde das Ende des <ol> Tags
                        int olEnd = navContent.indexOf(">", olStart);
                        if (olEnd >= 0) {
                            // Finde den ersten <li> nach dem <ol> Tag
                            int liStart = navContent.indexOf("<li", olEnd);
                            if (liStart >= 0) {
                                // Finde das Ende des ersten </li> Tags
                                int liEnd = navContent.indexOf("</li>", liStart);
                                if (liEnd >= 0) {
                                    // Entferne den ersten <li> Eintrag
                                    navContent = navContent.substring(0, liStart) + navContent.substring(liEnd + 5);
                                    logger.debug("nav.xhtml: Erster Eintrag (Buchtitel) entfernt via String-Manipulation");
                                }
                            }
                        }
                    }
                }
                
                // Fallback: Falls String-Manipulation nicht funktioniert hat, versuche Regex
                if (navContent.equals(beforeReplace)) {
                    // Pattern: Suche nach toc-li-1 oder ch001
                    navContent = navContent.replaceAll(
                        "(?s)(<ol[^>]*class=\"toc\"[^>]*>)\\s*<li[^>]*id=\"toc-li-1\"[^>]*>.*?</li>",
                        "$1"
                    );
                    if (navContent.equals(beforeReplace)) {
                        navContent = navContent.replaceAll(
                            "(?s)(<ol[^>]*class=\"toc\"[^>]*>)\\s*<li[^>]*>.*?<a[^>]*href=\"[^\"]*ch001[^\"]*\"[^>]*>.*?</a>.*?</li>",
                            "$1"
                        );
                    }
                    if (!navContent.equals(beforeReplace)) {
                        logger.debug("nav.xhtml: Erster Eintrag (Buchtitel) entfernt via Regex-Fallback");
                    } else {
                        logger.warn("nav.xhtml: Konnte ersten Eintrag nicht entfernen - möglicherweise andere Struktur");
                    }
                }
                
                if (!navContent.equals(originalNavContent)) {
                    Files.write(navFile.toPath(), navContent.getBytes(StandardCharsets.UTF_8));
                    logger.debug("nav.xhtml bearbeitet: Buchtitel aus TOC entfernt");
                }
            }
            
            // toc.ncx bearbeiten
            File ncxFile = findFileRecursive(tempDir, "toc.ncx");
            
            if (ncxFile != null && ncxFile.exists()) {
                String ncxContent = Files.readString(ncxFile.toPath(), StandardCharsets.UTF_8);
                String originalNcxContent = ncxContent;
                
                // "Abstract" durch "Zusammenfassung" ersetzen
                ncxContent = ncxContent.replaceAll("(?i)Abstract", "Zusammenfassung");
                
                // Ersten navPoint entfernen (Buchtitel als erstes Kapitel)
                // Entfernt den navPoint mit id="navPoint-1", der auf ch001 verweist (der Buchtitel)
                // Verwende String-Manipulation statt Regex für mehr Zuverlässigkeit
                String beforeReplaceNcx = ncxContent;
                
                // Finde die Position von <navMap>
                int navMapStart = ncxContent.indexOf("<navMap>");
                if (navMapStart >= 0) {
                    // Finde den ersten <navPoint> nach <navMap>
                    // Überspringe navPoint-0 (title_page) und suche nach navPoint-1
                    int navPoint1Start = ncxContent.indexOf("<navPoint", navMapStart);
                    if (navPoint1Start >= 0) {
                        // Prüfe ob es navPoint-1 ist (oder der erste nach navPoint-0)
                        String navPointTag = ncxContent.substring(navPoint1Start, Math.min(navPoint1Start + 100, ncxContent.length()));
                        // Suche nach dem ersten navPoint, der ch001 enthält oder id="navPoint-1" hat
                        int searchStart = navMapStart;
                        int maxIterations = 100; // Sicherheit gegen Endlosschleifen
                        int iterationCount = 0;
                        while (iterationCount < maxIterations) {
                            iterationCount++;
                            int navPointStart = ncxContent.indexOf("<navPoint", searchStart);
                            if (navPointStart < 0) break;
                            
                            int navPointEnd = ncxContent.indexOf("</navPoint>", navPointStart);
                            if (navPointEnd < 0) break;
                            
                            String navPointContent = ncxContent.substring(navPointStart, navPointEnd + 11);
                            // Prüfe ob dieser navPoint ch001 enthält oder id="navPoint-1" hat
                            if (navPointContent.contains("ch001") || navPointContent.contains("id=\"navPoint-1\"")) {
                                // Entferne diesen navPoint
                                ncxContent = ncxContent.substring(0, navPointStart) + ncxContent.substring(navPointEnd + 11);
                                logger.debug("toc.ncx: Erster navPoint (Buchtitel) entfernt via String-Manipulation");
                                break;
                            }
                            
                            // Prüfe ob wir am Ende des Dokuments angekommen sind
                            if (navPointEnd + 11 >= ncxContent.length()) {
                                break;
                            }
                            
                            searchStart = navPointEnd + 11;
                            
                            // Prüfe ob sich searchStart nicht mehr ändert (Sicherheit gegen Endlosschleifen)
                            if (searchStart <= navPointStart) {
                                logger.warn("toc.ncx: searchStart hat sich nicht geändert - breche Schleife ab");
                                break;
                            }
                        }
                        
                        if (iterationCount >= maxIterations) {
                            logger.warn("toc.ncx: Maximale Iterationen erreicht - mögliche Endlosschleife verhindert");
                        }
                    }
                }
                
                // Fallback: Falls String-Manipulation nicht funktioniert hat, versuche Regex
                if (ncxContent.equals(beforeReplaceNcx)) {
                    ncxContent = ncxContent.replaceAll(
                        "(?s)(<navMap>)\\s*<navPoint[^>]*id=\"navPoint-1\"[^>]*>.*?</navPoint>",
                        "$1"
                    );
                    if (ncxContent.equals(beforeReplaceNcx)) {
                        ncxContent = ncxContent.replaceAll(
                            "(?s)(<navMap>)\\s*<navPoint[^>]*>.*?<content[^>]*src=\"[^\"]*ch001[^\"]*\"[^>]*/>.*?</navPoint>",
                            "$1"
                        );
                    }
                    if (!ncxContent.equals(beforeReplaceNcx)) {
                        logger.debug("toc.ncx: Erster navPoint (Buchtitel) entfernt via Regex-Fallback");
                    } else {
                        logger.warn("toc.ncx: Konnte ersten navPoint nicht entfernen - möglicherweise andere Struktur");
                    }
                }
                
                if (!ncxContent.equals(originalNcxContent)) {
                    Files.write(ncxFile.toPath(), ncxContent.getBytes(StandardCharsets.UTF_8));
                    logger.debug("toc.ncx bearbeitet: Buchtitel aus TOC entfernt");
                }
            }
            
        } catch (Exception e) {
            logger.warn("Fehler beim Bearbeiten der TOC-Dateien: {}", e.getMessage());
        }
    }
    
    /**
     * Sucht rekursiv nach einer Datei
     */
    private File findFileRecursive(File directory, String fileName) {
        return findFileRecursive(directory, fileName, new java.util.HashSet<>());
    }
    
    private File findFileRecursive(File directory, String fileName, java.util.Set<String> visitedPaths) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            return null;
        }
        
        // Schutz gegen zyklische Verweise
        try {
            String canonicalPath = directory.getCanonicalPath();
            if (visitedPaths.contains(canonicalPath)) {
                logger.warn("Zyklischer Verweis erkannt, überspringe: {}", canonicalPath);
                return null;
            }
            visitedPaths.add(canonicalPath);
        } catch (IOException e) {
            logger.warn("Fehler beim Ermitteln des kanonischen Pfads: {}", e.getMessage());
            return null;
        }
        
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    File found = findFileRecursive(file, fileName, visitedPaths);
                    if (found != null) {
                        return found;
                    }
                } else if (file.getName().equals(fileName)) {
                    return file;
                }
            }
        }
        return null;
    }
    
    /**
     * Fügt ein Verzeichnis rekursiv zu einer ZIP-Datei hinzu
     */
    private void addDirectoryToZip(File rootDir, File currentDir, java.util.zip.ZipOutputStream zos) throws IOException {
        // Sicherheit gegen zyklische Verzeichnisstrukturen
        if (currentDir == null || !currentDir.exists() || !currentDir.isDirectory()) {
            return;
        }
        
        // Prüfe ob Thread unterbrochen wurde
        if (Thread.currentThread().isInterrupted()) {
            throw new IOException("Thread wurde unterbrochen");
        }
        
        // Prüfe ob wir im rootDir sind (verhindert zyklische Verweise)
        try {
            if (!currentDir.getCanonicalPath().startsWith(rootDir.getCanonicalPath())) {
                logger.warn("Verzeichnis außerhalb des Root-Verzeichnisses übersprungen: {}", currentDir.getAbsolutePath());
                return;
            }
        } catch (IOException e) {
            logger.warn("Fehler beim Prüfen des Verzeichnispfads: {}", e.getMessage());
            return;
        }
        
        File[] files = currentDir.listFiles();
        if (files != null) {
            for (File file : files) {
                // Prüfe ob Thread unterbrochen wurde
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("Thread wurde unterbrochen");
                }
                
                if (file.isDirectory()) {
                    // Rekursiver Aufruf mit Sicherheitsprüfung
                    addDirectoryToZip(rootDir, file, zos);
                } else if (file.isFile()) {
                    // Nur Dateien hinzufügen, keine symbolischen Links oder andere spezielle Dateien
                    try {
                        String relativePath = rootDir.toPath().relativize(file.toPath()).toString().replace("\\", "/");
                        java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(relativePath);
                        zos.putNextEntry(entry);
                        
                        try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
                            byte[] buffer = new byte[8192]; // Größerer Buffer für bessere Performance
                            int length;
                            while ((length = fis.read(buffer)) > 0) {
                                // Prüfe ob Thread unterbrochen wurde
                                if (Thread.currentThread().isInterrupted()) {
                                    throw new IOException("Thread wurde unterbrochen");
                                }
                                zos.write(buffer, 0, length);
                            }
                        }
                        
                        zos.closeEntry();
                    } catch (IOException e) {
                        if (Thread.currentThread().isInterrupted()) {
                            throw e; // Re-throw wenn unterbrochen
                        }
                        logger.warn("Fehler beim Hinzufügen der Datei {} zur ZIP: {}", file.getName(), e.getMessage());
                        // Weiter mit nächster Datei
                    }
                }
            }
        }
    }
    
    /**
     * Löscht ein Verzeichnis rekursiv
     */
    private void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }
    
    /**
     * Aktiviert hierarchische Nummerierung für Listen im DOCX (3.1, 3.2.1, etc.)
     * Passt die Nummerierungs-Definitionen an, damit verschachtelte Listen hierarchisch nummeriert werden
     */
    private void enableHierarchicalListNumbering(XWPFDocument document) {
        try {
            logger.info("Starte enableHierarchicalListNumbering");
            // Apache POI XWPF unterstützt keine direkte Manipulation der Nummerierungs-Definitionen
            // Wir müssen auf die zugrunde liegenden XML-Strukturen zugreifen
            org.apache.poi.ooxml.POIXMLDocumentPart numberingPart = document.getNumbering();
            if (numberingPart == null) {
                logger.warn("Keine Nummerierungs-Definitionen gefunden - kann hierarchische Nummerierung nicht aktivieren");
                return;
            }
            
            logger.info("Nummerierungs-Part gefunden, versuche zu lesen");
            
            // Versuche die Nummerierungs-Definitionen zu lesen und anzupassen
            try (java.io.InputStream is = numberingPart.getPackagePart().getInputStream()) {
                // Parse die Nummerierungs-XML
                org.openxmlformats.schemas.wordprocessingml.x2006.main.NumberingDocument numberingDoc = 
                    org.openxmlformats.schemas.wordprocessingml.x2006.main.NumberingDocument.Factory.parse(is);
                org.openxmlformats.schemas.wordprocessingml.x2006.main.CTNumbering ctNumbering = numberingDoc.getNumbering();
                
                if (ctNumbering == null) {
                    logger.warn("Nummerierungs-XML konnte nicht geparst werden");
                    return;
                }
                
                logger.info("Nummerierungs-XML geparst, suche AbstractNum-Definitionen");
                
                int modifiedCount = 0;
                
                // Durchlaufe alle AbstractNum-Definitionen (die Basis-Definitionen)
                if (ctNumbering.getAbstractNumArray() != null) {
                    logger.info("Gefunden: {} AbstractNum-Definitionen", ctNumbering.getAbstractNumArray().length);
                    
                    for (org.openxmlformats.schemas.wordprocessingml.x2006.main.CTAbstractNum abstractNum : ctNumbering.getAbstractNumArray()) {
                        if (abstractNum.getLvlArray() != null) {
                            logger.info("AbstractNum hat {} Ebenen", abstractNum.getLvlArray().length);
                            
                            // Für jede Ebene: Setze hierarchische Nummerierung
                            for (int i = 0; i < abstractNum.getLvlArray().length; i++) {
                                org.openxmlformats.schemas.wordprocessingml.x2006.main.CTLvl level = abstractNum.getLvlArray(i);
                                
                                // Setze die Nummerierungs-Formatierung für hierarchische Nummerierung
                                // Ebene 0: %1. (nur eigene Nummer)
                                // Ebene 1: %1.%2. (übergeordnete Nummer + eigene Nummer)
                                // Ebene 2: %1.%2.%3. (alle übergeordneten Nummern + eigene Nummer)
                                // etc.
                                
                                if (level.getNumFmt() != null) {
                                    org.openxmlformats.schemas.wordprocessingml.x2006.main.STNumberFormat.Enum numFmt = level.getNumFmt().getVal();
                                    logger.info("Ebene {}: NumFmt = {}", i, numFmt);
                                    
                                    if (numFmt == org.openxmlformats.schemas.wordprocessingml.x2006.main.STNumberFormat.DECIMAL) {
                                        // Setze lvlText für hierarchische Nummerierung
                                        if (level.getLvlText() == null) {
                                            level.addNewLvlText();
                                        }
                                        
                                        // Baue den hierarchischen Nummerierungs-String
                                        StringBuilder lvlText = new StringBuilder();
                                        for (int j = 0; j <= i; j++) {
                                            if (j > 0) {
                                                lvlText.append(".");
                                            }
                                            lvlText.append("%").append(j + 1);
                                        }
                                        lvlText.append(".");
                                        
                                        String oldLvlText = level.getLvlText().getVal();
                                        level.getLvlText().setVal(lvlText.toString());
                                        logger.info("Ebene {}: lvlText geändert von '{}' zu '{}'", i, oldLvlText, lvlText.toString());
                                        modifiedCount++;
                                        
                                        // Wichtig: Setze auch die Startwerte für jede Ebene
                                        if (level.getStart() == null) {
                                            level.addNewStart();
                                        }
                                        level.getStart().setVal(java.math.BigInteger.valueOf(1));
                                    }
                                }
                            }
                        }
                    }
                } else {
                    logger.warn("Keine AbstractNum-Definitionen gefunden");
                }
                
                if (modifiedCount > 0) {
                    // WICHTIG: Speichere die geänderten Nummerierungs-Definitionen zurück
                    // Wir müssen den numberingPart neu schreiben, damit die Änderungen erhalten bleiben
                    try (java.io.OutputStream os = numberingPart.getPackagePart().getOutputStream()) {
                        numberingDoc.save(os);
                        os.flush();
                    }
                    
                    logger.info("Hierarchische Nummerierung für {} Ebenen aktiviert und gespeichert", modifiedCount);
                    
                    // DEBUG: Prüfe welche Nummerierungs-IDs tatsächlich im Dokument verwendet werden
                    int numParagraphsWithNumbering = 0;
                    for (XWPFParagraph paragraph : document.getParagraphs()) {
                        if (paragraph.getNumID() != null) {
                            numParagraphsWithNumbering++;
                            if (numParagraphsWithNumbering <= 5) { // Nur die ersten 5 loggen
                                logger.info("Absatz mit Nummerierung gefunden: NumID = {}, Ilvl = {}", 
                                    paragraph.getNumID(), paragraph.getNumIlvl());
                            }
                        }
                    }
                    logger.info("Gesamt: {} Absätze mit Nummerierung gefunden", numParagraphsWithNumbering);
                } else {
                    logger.warn("Keine Ebenen wurden modifiziert - möglicherweise keine nummerierten Listen gefunden");
                }
                
            } catch (Exception e) {
                logger.error("Fehler beim Anpassen der Nummerierungs-Definitionen: {}", e.getMessage(), e);
            }
            
        } catch (Exception e) {
            logger.error("Fehler beim Aktivieren der hierarchischen Nummerierung: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Fügt Initialen (Drop Caps) zu Absätzen mit "First Paragraph" Format hinzu
     */
    private void addInitialsToFirstParagraphs(XWPFDocument document) {
        try {
            logger.debug("Suche nach Headern in DOCX und füge Initialen zu den ersten Absätzen hinzu...");
            
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
                            logger.debug("Erster Header (Titel) gefunden - überspringe: '{}' (Style: {})", firstLine, styleName);
                            isFirstHeader = false; // Nach dem ersten Header sind alle anderen gültig
                        } else {
                            foundHeader = true;
                            logger.debug("Header gefunden: '{}' (Style: {})", firstLine, styleName);
                        }
                        continue; // Überspringe den Header selbst
                    }
                    
                    // Wenn wir nach einem Header sind, prüfe auf den ersten Absatz
                    if (foundHeader) {
                        // Einfache Prüfung: Ist es ein normaler Absatz
                        if (firstLine.length() > 20) {
                            processedCount++;
                            foundHeader = false; // Nur den ersten Absatz nach Header
                        }
                    }
                }
            }
            
            logger.debug("Initialen-Verarbeitung abgeschlossen - {} Absätze bearbeitet", processedCount);
            
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
            
            logger.debug("Initialen erfolgreich hinzugefügt für Absatz: '{}'", firstLine.substring(0, Math.min(50, firstLine.length())));
            
        } catch (Exception e) {
            logger.warn("Fehler beim Hinzufügen von Initialen zu Absatz: {}", e.getMessage());
            if (e.getCause() != null) {
                logger.warn("Ursache: {}", e.getCause().getMessage());
            }
        }
    }
    
    /**
     * Formatiert Blockquotes mit dem Word-Absatzformat "Zitat"
     * Erkennt Blockquotes anhand ihrer Einrückung (Pandoc erstellt Blockquotes mit Einrückung)
     */
    private void formatBlockquotesAsQuoteStyle(XWPFDocument document) {
        try {
            int blockquoteCount = 0;
            
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                // Überspringe leere Absätze
                String text = paragraph.getText();
                if (text == null || text.trim().isEmpty()) {
                    continue;
                }
                
                // Überspringe Überschriften
                String style = paragraph.getStyle();
                if (style != null && (style.contains("Heading") || style.contains("heading"))) {
                    continue;
                }
                
                // Prüfe auf Einrückung (Pandoc erstellt Blockquotes mit Einrückung)
                // Blockquotes haben normalerweise eine linke Einrückung
                org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr ppr = paragraph.getCTP().getPPr();
                if (ppr != null && ppr.getInd() != null) {
                    org.openxmlformats.schemas.wordprocessingml.x2006.main.CTInd ind = ppr.getInd();
                    // Wenn linke Einrückung vorhanden ist (typisch für Blockquotes)
                    Object leftObj = ind.getLeft();
                    if (leftObj instanceof java.math.BigInteger) {
                        java.math.BigInteger leftIndent = (java.math.BigInteger) leftObj;
                        if (leftIndent.intValue() > 0) {
                        // Setze das Word-Absatzformat "Zitat"
                        if (ppr.getPStyle() == null) {
                            ppr.addNewPStyle();
                        }
                        ppr.getPStyle().setVal("Zitat");
                        blockquoteCount++;
                        }
                    }
                }
            }
            
            // Auch in Tabellen prüfen
            for (XWPFTable table : document.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        for (XWPFParagraph paragraph : cell.getParagraphs()) {
                            String text = paragraph.getText();
                            if (text == null || text.trim().isEmpty()) {
                                continue;
                            }
                            
                            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr ppr = paragraph.getCTP().getPPr();
                            if (ppr != null && ppr.getInd() != null) {
                                org.openxmlformats.schemas.wordprocessingml.x2006.main.CTInd ind = ppr.getInd();
                                Object leftObj = ind.getLeft();
                                if (leftObj instanceof java.math.BigInteger) {
                                    java.math.BigInteger leftIndent = (java.math.BigInteger) leftObj;
                                    if (leftIndent.intValue() > 0) {
                                    if (ppr.getPStyle() == null) {
                                        ppr.addNewPStyle();
                                    }
                                    ppr.getPStyle().setVal("Zitat");
                                    blockquoteCount++;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            logger.debug("Blockquotes mit Word-Absatzformat 'Zitat' formatiert: {}", blockquoteCount);
            
        } catch (Exception e) {
            logger.warn("Fehler beim Formatieren von Blockquotes: {}", e.getMessage());
        }
    }
    
    /**
     * Formatiert alle Tabellen im Dokument: Setzt Breite auf 100% und fügt Rahmen hinzu
     */
    private void formatTables(XWPFDocument document) {
        try {
            int tableCount = 0;
            
            // Berechne Tabellenbreite einmal für alle Tabellen
            // A4 Breite: 21cm = 11906 Twips
            // Standard-Ränder: 2.5cm links + 2.5cm rechts = 5cm = 2835 Twips
            // Verfügbare Breite: 11906 - 2835 = 9071 Twips
            // Wir verwenden etwas weniger (90% der verfügbaren Breite) für Sicherheit: ca. 8164 Twips
            long pageWidthTwips = 11906L; // A4 Breite
            long marginsTwips = 2835L; // Links + Rechts Ränder
            long availableWidth = pageWidthTwips - marginsTwips;
            long tableWidth = (long)(availableWidth * 0.90); // 90% der verfügbaren Breite für Sicherheit
            
            for (XWPFTable table : document.getTables()) {
                tableCount++;
                
                // Tabellen-Eigenschaften abrufen oder erstellen
                org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr tblPr = table.getCTTbl().getTblPr();
                if (tblPr == null) {
                    tblPr = table.getCTTbl().addNewTblPr();
                }
                
                // Tabellen-Layout auf "fest" setzen, damit die Breite respektiert wird
                org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblLayoutType layoutType = tblPr.getTblLayout();
                if (layoutType == null) {
                    layoutType = tblPr.addNewTblLayout();
                }
                layoutType.setType(org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblLayoutType.FIXED);
                
                // Tabellenbreite setzen
                org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth tblWidth = tblPr.getTblW();
                if (tblWidth == null) {
                    tblWidth = tblPr.addNewTblW();
                }
                tblWidth.setType(org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth.DXA);
                tblWidth.setW(java.math.BigInteger.valueOf(tableWidth));
                
                // Tabellen-Rahmen hinzufügen
                org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblBorders borders = tblPr.getTblBorders();
                if (borders == null) {
                    borders = tblPr.addNewTblBorders();
                }
                
                // Rahmen-Stil: Einfache Linie, Größe 12 (1.5pt), Farbe schwarz (größer für bessere Sichtbarkeit)
                // Erstelle Border-Objekte für alle Seiten
                org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder topBorder = borders.isSetTop() ? borders.getTop() : borders.addNewTop();
                topBorder.setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.SINGLE);
                topBorder.setSz(java.math.BigInteger.valueOf(12)); // 1.5pt
                topBorder.setColor("000000"); // Schwarz
                
                org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder bottomBorder = borders.isSetBottom() ? borders.getBottom() : borders.addNewBottom();
                bottomBorder.setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.SINGLE);
                bottomBorder.setSz(java.math.BigInteger.valueOf(12));
                bottomBorder.setColor("000000");
                
                org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder leftBorder = borders.isSetLeft() ? borders.getLeft() : borders.addNewLeft();
                leftBorder.setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.SINGLE);
                leftBorder.setSz(java.math.BigInteger.valueOf(12));
                leftBorder.setColor("000000");
                
                org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder rightBorder = borders.isSetRight() ? borders.getRight() : borders.addNewRight();
                rightBorder.setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.SINGLE);
                rightBorder.setSz(java.math.BigInteger.valueOf(12));
                rightBorder.setColor("000000");
                
                org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder insideHBorder = borders.isSetInsideH() ? borders.getInsideH() : borders.addNewInsideH();
                insideHBorder.setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.SINGLE);
                insideHBorder.setSz(java.math.BigInteger.valueOf(12));
                insideHBorder.setColor("000000");
                
                org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder insideVBorder = borders.isSetInsideV() ? borders.getInsideV() : borders.addNewInsideV();
                insideVBorder.setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.SINGLE);
                insideVBorder.setSz(java.math.BigInteger.valueOf(12));
                insideVBorder.setColor("000000");
                
                // Spaltenbreiten setzen - gleichmäßig verteilen
                int columnCount = 0;
                if (table.getRows().size() > 0) {
                    columnCount = table.getRow(0).getTableCells().size();
                }
                
                if (columnCount > 0) {
                    // Für jede Spalte die Breite setzen
                    for (XWPFTableRow row : table.getRows()) {
                        for (int i = 0; i < row.getTableCells().size() && i < columnCount; i++) {
                            XWPFTableCell cell = row.getTableCells().get(i);
                            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr tcPr = cell.getCTTc().getTcPr();
                            if (tcPr == null) {
                                tcPr = cell.getCTTc().addNewTcPr();
                            }
                            
                            // Spaltenbreite setzen (gleichmäßige Verteilung über die Tabellenbreite)
                            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth cellWidth = tcPr.getTcW();
                            if (cellWidth == null) {
                                cellWidth = tcPr.addNewTcW();
                            }
                            // Verwende die bereits berechnete Tabellenbreite
                            long columnWidth = tableWidth / columnCount; // Gleichmäßig auf Spalten verteilen
                            cellWidth.setType(org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth.DXA);
                            cellWidth.setW(java.math.BigInteger.valueOf(columnWidth));
                        }
                    }
                }
            }
            
            logger.debug("Tabellen formatiert: {} Tabellen mit Breite 90% der verfügbaren Seitenbreite, Rahmen und Spaltenbreiten versehen", tableCount);
            
        } catch (Exception e) {
            logger.warn("Fehler beim Formatieren von Tabellen: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Kopiert alle Markdown-Bilder ins HTML-Verzeichnis und passt die Pfade in der Markdown-Datei an
     */
    private void copyMarkdownImagesToHtmlDir(File markdownFile, File htmlDir) {
        try {
            // Markdown-Datei lesen
            String content = Files.readString(markdownFile.toPath(), StandardCharsets.UTF_8);
            String originalContent = content;
            
            // Pattern für Markdown-Bilder: ![Alt-Text](Pfad)
            java.util.regex.Pattern imagePattern = java.util.regex.Pattern.compile(
                "!\\[([^\\]]*)\\]\\(([^)]+)\\)", 
                java.util.regex.Pattern.MULTILINE
            );
            
            java.util.regex.Matcher matcher = imagePattern.matcher(content);
            java.util.List<java.util.Map.Entry<Integer, Integer>> matchPositions = new java.util.ArrayList<>();
            java.util.List<String> replacements = new java.util.ArrayList<>();
            
            // Zuerst alle Matches finden und Positionen speichern
            while (matcher.find()) {
                matchPositions.add(new java.util.AbstractMap.SimpleEntry<>(matcher.start(), matcher.end()));
            }
            
            // Dann alle Matches verarbeiten (rückwärts, um Indizes nicht zu verschieben)
            matcher = imagePattern.matcher(content);
            int matchIndex = 0;
            while (matcher.find()) {
                if (matchIndex >= matchPositions.size()) break;
                
                String altText = matcher.group(1);
                String imagePath = matcher.group(2);
                
                // Entferne Anführungszeichen falls vorhanden
                imagePath = imagePath.trim();
                if ((imagePath.startsWith("\"") && imagePath.endsWith("\"")) || 
                    (imagePath.startsWith("'") && imagePath.endsWith("'"))) {
                    imagePath = imagePath.substring(1, imagePath.length() - 1);
                }
                
                // Prüfe ob es eine URL ist (http/https) - dann nicht kopieren
                if (imagePath.startsWith("http://") || imagePath.startsWith("https://")) {
                    logger.debug("Bild ist eine URL, wird nicht kopiert: {}", imagePath);
                    replacements.add(null); // Keine Ersetzung für URLs
                    matchIndex++;
                    continue;
                }
                
                // Versuche Bild-Datei zu finden
                File imageFile = null;
                
                // Absoluter Pfad
                if (new File(imagePath).isAbsolute()) {
                    imageFile = new File(imagePath);
                } else {
                    // Relativer Pfad - versuche relativ zur Markdown-Datei
                    File markdownDir = markdownFile.getParentFile();
                    if (markdownDir != null) {
                        imageFile = new File(markdownDir, imagePath);
                    }
                    
                    // Falls nicht gefunden, versuche im System-Temp-Verzeichnis (für Bilder, die dort liegen)
                    if ((imageFile == null || !imageFile.exists())) {
                        String tempDir = System.getProperty("java.io.tmpdir");
                        if (tempDir != null) {
                            File tempFile = new File(tempDir, imagePath);
                            if (tempFile.exists()) {
                                imageFile = tempFile;
                            }
                        }
                    }
                    
                    // Falls nicht gefunden, versuche relativ zum Projekt-Verzeichnis (data-Verzeichnis)
                    if ((imageFile == null || !imageFile.exists()) && projectDirectory != null) {
                        imageFile = new File(projectDirectory, imagePath);
                    }
                    
                    // Falls nicht gefunden, versuche im Hauptverzeichnis (ein Verzeichnis höher vom Projekt-Verzeichnis)
                    // Das ist nötig, weil Bilder oft im Hauptverzeichnis liegen, nicht im data-Verzeichnis
                    if ((imageFile == null || !imageFile.exists()) && projectDirectory != null) {
                        File parentDir = projectDirectory.getParentFile();
                        if (parentDir != null) {
                            File parentImageFile = new File(parentDir, imagePath);
                            if (parentImageFile.exists()) {
                                imageFile = parentImageFile;
                            }
                        }
                    }
                    
                    // Falls immer noch nicht gefunden, versuche als absoluter Pfad
                    if (imageFile == null || !imageFile.exists()) {
                        imageFile = new File(imagePath);
                    }
                }
                
                if (imageFile != null && imageFile.exists()) {
                    try {
                        // Dateiname für das kopierte Bild
                        String imageFileName = imageFile.getName();
                        // Falls Dateiname leer oder ungültig, verwende Alt-Text oder generiere Namen
                        if (imageFileName == null || imageFileName.isEmpty() || 
                            !imageFileName.contains(".")) {
                            String extension = getFileExtension(imageFile.getName());
                            if (extension.isEmpty()) {
                                extension = "png"; // Fallback
                            }
                            imageFileName = (altText != null && !altText.isEmpty()) 
                                ? altText.replaceAll("[^a-zA-Z0-9]", "_") + "." + extension
                                : "image_" + System.currentTimeMillis() + "." + extension;
                        }
                        
                        File targetImage = new File(htmlDir, imageFileName);
                        
                        // Kopiere Bild ins HTML-Verzeichnis
                        Files.copy(imageFile.toPath(), targetImage.toPath(), 
                            StandardCopyOption.REPLACE_EXISTING);
                        
                        // Ersetze den Pfad in der Markdown-Datei durch relativen Pfad
                        String newPath = imageFileName; // Relativer Pfad zum HTML-Verzeichnis
                        String replacement = "![" + altText + "](" + newPath + ")";
                        replacements.add(replacement);
                        
                        logger.debug("Bild kopiert: {} -> {}", imageFile.getAbsolutePath(), 
                            targetImage.getAbsolutePath());
                        
                    } catch (IOException e) {
                        logger.warn("Fehler beim Kopieren des Bildes {}: {}", imagePath, e.getMessage());
                    }
                } else {
                    logger.warn("Bilddatei nicht gefunden: {} (versucht: {})", imagePath, 
                        imageFile != null ? imageFile.getAbsolutePath() : "null");
                    // Keine Ersetzung für nicht gefundene Bilder
                    replacements.add(null);
                }
                matchIndex++;
            }
            
            // Ersetze alle gefundenen Bild-Pfade in der Markdown-Datei (rückwärts, um Indizes nicht zu verschieben)
            for (int i = matchPositions.size() - 1; i >= 0; i--) {
                if (i < replacements.size() && replacements.get(i) != null) {
                    java.util.Map.Entry<Integer, Integer> match = matchPositions.get(i);
                    int start = match.getKey();
                    int end = match.getValue();
                    String replacement = replacements.get(i);
                    content = content.substring(0, start) + replacement + content.substring(end);
                }
            }
            
            // Nur schreiben wenn sich etwas geändert hat
            if (!content.equals(originalContent)) {
                Files.write(markdownFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
                long processedCount = replacements.stream().filter(r -> r != null).count();
                logger.debug("Markdown-Datei aktualisiert: {} Bilder verarbeitet", processedCount);
            }
            
        } catch (IOException e) {
            logger.error("Fehler beim Kopieren der Markdown-Bilder: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Kopiert alle Markdown-Bilder für PDF-Export in beide Verzeichnisse (Ausgabe und pandoc)
     * und passt die Pfade in der Markdown-Datei an (verwendet relative Pfade)
     */
    private void copyMarkdownImagesForPdf(File markdownFile, File outputDir, File pandocDir) {
        try {
            // Markdown-Datei lesen
            String content = Files.readString(markdownFile.toPath(), StandardCharsets.UTF_8);
            String originalContent = content;
            
            // Pattern für Markdown-Bilder: ![Alt-Text](Pfad)
            java.util.regex.Pattern imagePattern = java.util.regex.Pattern.compile(
                "!\\[([^\\]]*)\\]\\(([^)]+)\\)", 
                java.util.regex.Pattern.MULTILINE
            );
            
            java.util.regex.Matcher matcher = imagePattern.matcher(content);
            java.util.List<java.util.Map.Entry<Integer, Integer>> matchPositions = new java.util.ArrayList<>();
            java.util.List<String> replacements = new java.util.ArrayList<>();
            
            // Zuerst alle Matches finden und Positionen speichern
            while (matcher.find()) {
                matchPositions.add(new java.util.AbstractMap.SimpleEntry<>(matcher.start(), matcher.end()));
            }
            
            // Dann alle Matches verarbeiten
            matcher = imagePattern.matcher(content);
            int matchIndex = 0;
            while (matcher.find()) {
                if (matchIndex >= matchPositions.size()) break;
                
                String altText = matcher.group(1);
                String imagePath = matcher.group(2);
                
                // Entferne Anführungszeichen falls vorhanden
                imagePath = imagePath.trim();
                if ((imagePath.startsWith("\"") && imagePath.endsWith("\"")) || 
                    (imagePath.startsWith("'") && imagePath.endsWith("'"))) {
                    imagePath = imagePath.substring(1, imagePath.length() - 1);
                }
                
                // Prüfe ob es eine URL ist (http/https) - dann nicht kopieren
                if (imagePath.startsWith("http://") || imagePath.startsWith("https://")) {
                    logger.debug("Bild ist eine URL, wird nicht kopiert: {}", imagePath);
                    replacements.add(null); // Keine Ersetzung für URLs
                    matchIndex++;
                    continue;
                }
                
                // Versuche Bild-Datei zu finden
                File imageFile = null;
                
                // Absoluter Pfad
                if (new File(imagePath).isAbsolute()) {
                    imageFile = new File(imagePath);
                } else {
                    // Relativer Pfad - versuche relativ zur Markdown-Datei
                    File markdownDir = markdownFile.getParentFile();
                    if (markdownDir != null) {
                        imageFile = new File(markdownDir, imagePath);
                    }
                    
                    // Falls nicht gefunden, versuche relativ zum Projekt-Verzeichnis (data-Verzeichnis)
                    if ((imageFile == null || !imageFile.exists()) && projectDirectory != null) {
                        imageFile = new File(projectDirectory, imagePath);
                    }
                    
                    // Falls nicht gefunden, versuche im Hauptverzeichnis (ein Verzeichnis höher vom Projekt-Verzeichnis)
                    // Das ist nötig, weil Bilder oft im Hauptverzeichnis liegen, nicht im data-Verzeichnis
                    if ((imageFile == null || !imageFile.exists()) && projectDirectory != null) {
                        File parentDir = projectDirectory.getParentFile();
                        if (parentDir != null) {
                            File parentImageFile = new File(parentDir, imagePath);
                            if (parentImageFile.exists()) {
                                imageFile = parentImageFile;
                            }
                        }
                    }
                    
                    // Falls immer noch nicht gefunden, versuche als absoluten Pfad
                    if (imageFile == null || !imageFile.exists()) {
                        imageFile = new File(imagePath);
                    }
                }
                
                if (imageFile != null && imageFile.exists()) {
                    try {
                        // Dateiname für das kopierte Bild
                        String imageFileName = imageFile.getName();
                        // Falls Dateiname leer oder ungültig, verwende Alt-Text oder generiere Namen
                        if (imageFileName == null || imageFileName.isEmpty() || 
                            !imageFileName.contains(".")) {
                            String extension = getFileExtension(imageFile.getName());
                            if (extension.isEmpty()) {
                                extension = "png"; // Fallback
                            }
                            imageFileName = (altText != null && !altText.isEmpty()) 
                                ? altText.replaceAll("[^a-zA-Z0-9]", "_") + "." + extension
                                : "image_" + System.currentTimeMillis() + "." + extension;
                        }
                        
                        // Kopiere Bild ins Ausgabeverzeichnis
                        File targetImageOutput = new File(outputDir, imageFileName);
                        Files.copy(imageFile.toPath(), targetImageOutput.toPath(), 
                            StandardCopyOption.REPLACE_EXISTING);
                        
                        // Kopiere Bild auch ins pandoc-Verzeichnis (als Backup)
                        File targetImagePandoc = new File(pandocDir, imageFileName);
                        Files.copy(imageFile.toPath(), targetImagePandoc.toPath(), 
                            StandardCopyOption.REPLACE_EXISTING);
                        
                        // Verwende nur den Dateinamen (relativ zum Arbeitsverzeichnis)
                        // Pandoc/XeLaTeX sucht im Arbeitsverzeichnis nach Bildern
                        String replacement = "![" + altText + "](" + imageFileName + ")";
                        replacements.add(replacement);
                        
                        logger.debug("Bild für PDF kopiert: {} -> {} und {}", imageFile.getAbsolutePath(), 
                            targetImageOutput.getAbsolutePath(), targetImagePandoc.getAbsolutePath());
                        
                    } catch (IOException e) {
                        logger.warn("Fehler beim Kopieren des Bildes {}: {}", imagePath, e.getMessage());
                        replacements.add(null);
                    }
                } else {
                    logger.warn("Bilddatei nicht gefunden: {} (versucht: {})", imagePath, 
                        imageFile != null ? imageFile.getAbsolutePath() : "null");
                    // Keine Ersetzung für nicht gefundene Bilder
                    replacements.add(null);
                }
                matchIndex++;
            }
            
            // Ersetze alle gefundenen Bild-Pfade in der Markdown-Datei (rückwärts, um Indizes nicht zu verschieben)
            for (int i = matchPositions.size() - 1; i >= 0; i--) {
                if (i < replacements.size() && replacements.get(i) != null) {
                    java.util.Map.Entry<Integer, Integer> match = matchPositions.get(i);
                    int start = match.getKey();
                    int end = match.getValue();
                    String replacement = replacements.get(i);
                    content = content.substring(0, start) + replacement + content.substring(end);
                }
            }
            
            // Nur schreiben wenn sich etwas geändert hat
            if (!content.equals(originalContent)) {
                Files.write(markdownFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
                long processedCount = replacements.stream().filter(r -> r != null).count();
                logger.debug("Markdown-Datei für PDF aktualisiert: {} Bilder verarbeitet", processedCount);
            }
            
        } catch (IOException e) {
            logger.error("Fehler beim Kopieren der Markdown-Bilder für PDF: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Kopiert alle Markdown-Bilder für PDF-Export in alle relevanten Verzeichnisse
     * und passt die Pfade in der Markdown-Datei an (verwendet nur Dateinamen, da resource-path gesetzt ist)
     */
    private void copyMarkdownImagesForPdfExport(File markdownFile, File markdownDir, File pandocDir, File outputDir) {
        try {
            // Markdown-Datei lesen
            String content = Files.readString(markdownFile.toPath(), StandardCharsets.UTF_8);
            
            // WICHTIG: <br> Tags für PDF konvertieren (falls noch vorhanden)
            // Die Bildverarbeitung wird nach replaceHtmlTagsInMarkdown aufgerufen,
            // aber falls die Datei nicht korrekt geschrieben wurde, konvertieren wir hier nochmal
            // Konvertiere direkt zu LaTeX-Befehlen für Absatzumbruch
            content = content.replaceAll("(?i)<br\\s*/?>", "\\\\par\\\\vspace\\{\\\\baselineskip\\}\\\\par");
            content = content.replaceAll("(?i)<br>", "\\\\par\\\\vspace\\{\\\\baselineskip\\}\\\\par");
            content = content.replaceAll("(?i)<br\\s+/>", "\\\\par\\\\vspace\\{\\\\baselineskip\\}\\\\par");
            String originalContent = content;
            
            // Pattern für Markdown-Bilder: ![Alt-Text](Pfad) oder ![Alt-Text](Pfad){ width=... }
            java.util.regex.Pattern imagePattern = java.util.regex.Pattern.compile(
                "!\\[([^\\]]*)\\]\\(([^)]+)\\)(?:\\s*\\{[^}]*width[^}]*\\})?", 
                java.util.regex.Pattern.MULTILINE
            );
            
            java.util.regex.Matcher matcher = imagePattern.matcher(content);
            java.util.List<java.util.Map.Entry<Integer, Integer>> matchPositions = new java.util.ArrayList<>();
            java.util.List<String> replacements = new java.util.ArrayList<>();
            
            // Zuerst alle Matches finden und Positionen speichern
            while (matcher.find()) {
                matchPositions.add(new java.util.AbstractMap.SimpleEntry<>(matcher.start(), matcher.end()));
            }
            
            // Dann alle Matches verarbeiten
            matcher = imagePattern.matcher(content);
            int matchIndex = 0;
            while (matcher.find()) {
                if (matchIndex >= matchPositions.size()) break;
                
                String altText = matcher.group(1);
                String imagePath = matcher.group(2);
                String fullMatch = matcher.group(0);
                
                // Entferne Anführungszeichen falls vorhanden
                imagePath = imagePath.trim();
                if ((imagePath.startsWith("\"") && imagePath.endsWith("\"")) || 
                    (imagePath.startsWith("'") && imagePath.endsWith("'"))) {
                    imagePath = imagePath.substring(1, imagePath.length() - 1);
                }
                
                // Prüfe ob es eine URL ist (http/https) - dann nicht kopieren
                if (imagePath.startsWith("http://") || imagePath.startsWith("https://")) {
                    logger.debug("Bild ist eine URL, wird nicht kopiert: {}", imagePath);
                    replacements.add(null); // Keine Ersetzung für URLs
                    matchIndex++;
                    continue;
                }
                
                // Versuche Bild-Datei zu finden
                File imageFile = null;
                
                // Absoluter Pfad
                if (new File(imagePath).isAbsolute()) {
                    imageFile = new File(imagePath);
                } else {
                    // Relativer Pfad - versuche relativ zur Markdown-Datei
                    if (markdownDir != null) {
                        imageFile = new File(markdownDir, imagePath);
                    }
                    
                    // Falls nicht gefunden, versuche relativ zum Projekt-Verzeichnis (data-Verzeichnis)
                    if ((imageFile == null || !imageFile.exists()) && projectDirectory != null) {
                        imageFile = new File(projectDirectory, imagePath);
                    }
                    
                    // Falls nicht gefunden, versuche im Hauptverzeichnis (ein Verzeichnis höher vom Projekt-Verzeichnis)
                    // Das ist nötig, weil Bilder oft im Hauptverzeichnis liegen, nicht im data-Verzeichnis
                    if ((imageFile == null || !imageFile.exists()) && projectDirectory != null) {
                        File parentDir = projectDirectory.getParentFile();
                        if (parentDir != null) {
                            File parentImageFile = new File(parentDir, imagePath);
                            if (parentImageFile.exists()) {
                                imageFile = parentImageFile;
                            }
                        }
                    }
                    
                    // Falls immer noch nicht gefunden, versuche als absoluten Pfad
                    if (imageFile == null || !imageFile.exists()) {
                        imageFile = new File(imagePath);
                    }
                }
                
                if (imageFile != null && imageFile.exists()) {
                    try {
                        // Dateiname für das kopierte Bild
                        String imageFileName = imageFile.getName();
                        // Falls Dateiname leer oder ungültig, verwende Alt-Text oder generiere Namen
                        if (imageFileName == null || imageFileName.isEmpty() || 
                            !imageFileName.contains(".")) {
                            String extension = getFileExtension(imageFile.getName());
                            if (extension.isEmpty()) {
                                extension = "png"; // Fallback
                            }
                            imageFileName = (altText != null && !altText.isEmpty()) 
                                ? altText.replaceAll("[^a-zA-Z0-9]", "_") + "." + extension
                                : "image_" + System.currentTimeMillis() + "." + extension;
                        }
                        
                        // Kopiere Bild ins Ausgabeverzeichnis (XeLaTeX kompiliert dort)
                        File targetImageOutput = new File(outputDir, imageFileName);
                        Files.copy(imageFile.toPath(), targetImageOutput.toPath(), 
                            StandardCopyOption.REPLACE_EXISTING);
                        logger.debug("Bild ins Ausgabeverzeichnis kopiert: {}", targetImageOutput.getAbsolutePath());
                        
                        // Kopiere Bild ins Markdown-Verzeichnis
                        if (markdownDir != null) {
                            File targetImageMarkdown = new File(markdownDir, imageFileName);
                            Files.copy(imageFile.toPath(), targetImageMarkdown.toPath(), 
                                StandardCopyOption.REPLACE_EXISTING);
                            logger.debug("Bild ins Markdown-Verzeichnis kopiert: {}", targetImageMarkdown.getAbsolutePath());
                        }
                        
                        // Kopiere Bild auch ins pandoc-Verzeichnis (als Backup)
                        File targetImagePandoc = new File(pandocDir, imageFileName);
                        Files.copy(imageFile.toPath(), targetImagePandoc.toPath(), 
                            StandardCopyOption.REPLACE_EXISTING);
                        logger.debug("Bild ins pandoc-Verzeichnis kopiert: {}", targetImagePandoc.getAbsolutePath());
                        
                        // Ersetze den Pfad in der Markdown-Datei durch nur den Dateinamen
                        // XeLaTeX kompiliert im Ausgabeverzeichnis, wo die Bilder auch sind
                        // Daher funktioniert relativer Pfad (nur Dateiname) am besten
                        // Füge Bildgröße hinzu, damit Bilder nicht zu groß werden (nur wenn noch keine vorhanden)
                        String replacement;
                        if (fullMatch.contains("{") && fullMatch.contains("width")) {
                            // Bereits eine Größenangabe vorhanden, behalte sie
                            replacement = "![" + altText + "](" + imageFileName + ")" + 
                                fullMatch.substring(fullMatch.indexOf("{"));
                        } else {
                            // Keine Größenangabe, füge 80% hinzu
                            replacement = "![" + altText + "](" + imageFileName + "){ width=80% }";
                        }
                        replacements.add(replacement);
                        logger.debug("Bild-Pfad in Markdown geändert zu: {} (relativ zum Ausgabeverzeichnis, width=80%)", imageFileName);
                        
                    } catch (IOException e) {
                        logger.warn("Fehler beim Kopieren des Bildes {}: {}", imagePath, e.getMessage());
                        replacements.add(null);
                    }
                } else {
                    logger.warn("Bilddatei nicht gefunden: {} (versucht: {})", imagePath, 
                        imageFile != null ? imageFile.getAbsolutePath() : "null");
                    // Keine Ersetzung für nicht gefundene Bilder
                    replacements.add(null);
                }
                matchIndex++;
            }
            
            // Ersetze alle gefundenen Bild-Pfade in der Markdown-Datei (rückwärts, um Indizes nicht zu verschieben)
            for (int i = matchPositions.size() - 1; i >= 0; i--) {
                if (i < replacements.size() && replacements.get(i) != null) {
                    java.util.Map.Entry<Integer, Integer> match = matchPositions.get(i);
                    int start = match.getKey();
                    int end = match.getValue();
                    String replacement = replacements.get(i);
                    content = content.substring(0, start) + replacement + content.substring(end);
                }
            }
            
            // Nur schreiben wenn sich etwas geändert hat
            if (!content.equals(originalContent)) {
                Files.write(markdownFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
                long processedCount = replacements.stream().filter(r -> r != null).count();
                logger.debug("Markdown-Datei für PDF aktualisiert: {} Bilder verarbeitet", processedCount);
            }
            
        } catch (IOException e) {
            logger.error("Fehler beim Kopieren der Markdown-Bilder für PDF: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Kopiert alle Markdown-Bilder ins Ausgabeverzeichnis UND pandoc-Verzeichnis für LaTeX-Export
     * und passt die Pfade in der Markdown-Datei an (verwendet relative Pfade zum Ausgabeverzeichnis)
     */
    private void copyMarkdownImagesForLatexExport(File markdownFile, File outputDir, File pandocDir) {
        try {
            // Markdown-Datei lesen
            String content = Files.readString(markdownFile.toPath(), StandardCharsets.UTF_8);
            String originalContent = content;
            
            // Pattern für Markdown-Bilder: ![Alt-Text](Pfad) oder ![Alt-Text](Pfad){ width=... }
            java.util.regex.Pattern imagePattern = java.util.regex.Pattern.compile(
                "!\\[([^\\]]*)\\]\\(([^)]+)\\)(?:\\s*\\{[^}]*width[^}]*\\})?", 
                java.util.regex.Pattern.MULTILINE
            );
            
            java.util.regex.Matcher matcher = imagePattern.matcher(content);
            java.util.List<java.util.Map.Entry<Integer, Integer>> matchPositions = new java.util.ArrayList<>();
            java.util.List<String> replacements = new java.util.ArrayList<>();
            
            // Zuerst alle Matches finden und Positionen speichern
            while (matcher.find()) {
                matchPositions.add(new java.util.AbstractMap.SimpleEntry<>(matcher.start(), matcher.end()));
            }
            
            // Dann alle Matches verarbeiten
            matcher = imagePattern.matcher(content);
            int matchIndex = 0;
            while (matcher.find()) {
                if (matchIndex >= matchPositions.size()) break;
                
                String altText = matcher.group(1);
                String imagePath = matcher.group(2);
                String fullMatch = matcher.group(0);
                
                // Entferne Anführungszeichen falls vorhanden
                imagePath = imagePath.trim();
                if ((imagePath.startsWith("\"") && imagePath.endsWith("\"")) || 
                    (imagePath.startsWith("'") && imagePath.endsWith("'"))) {
                    imagePath = imagePath.substring(1, imagePath.length() - 1);
                }
                
                // Prüfe ob es eine URL ist (http/https) - dann nicht kopieren
                if (imagePath.startsWith("http://") || imagePath.startsWith("https://")) {
                    logger.debug("Bild ist eine URL, wird nicht kopiert: {}", imagePath);
                    replacements.add(null); // Keine Ersetzung für URLs
                    matchIndex++;
                    continue;
                }
                
                // Versuche Bild-Datei zu finden
                File imageFile = null;
                
                // Absoluter Pfad
                if (new File(imagePath).isAbsolute()) {
                    imageFile = new File(imagePath);
                } else {
                    // Relativer Pfad - versuche relativ zur Markdown-Datei
                    File markdownDir = markdownFile.getParentFile();
                    if (markdownDir != null) {
                        imageFile = new File(markdownDir, imagePath);
                    }
                    
                    // Falls nicht gefunden, versuche relativ zum Projekt-Verzeichnis (data-Verzeichnis)
                    if ((imageFile == null || !imageFile.exists()) && projectDirectory != null) {
                        imageFile = new File(projectDirectory, imagePath);
                    }
                    
                    // Falls nicht gefunden, versuche im Hauptverzeichnis (ein Verzeichnis höher vom Projekt-Verzeichnis)
                    // Das ist nötig, weil Bilder oft im Hauptverzeichnis liegen, nicht im data-Verzeichnis
                    if ((imageFile == null || !imageFile.exists()) && projectDirectory != null) {
                        File parentDir = projectDirectory.getParentFile();
                        if (parentDir != null) {
                            File parentImageFile = new File(parentDir, imagePath);
                            if (parentImageFile.exists()) {
                                imageFile = parentImageFile;
                            }
                        }
                    }
                    
                    // Falls immer noch nicht gefunden, versuche als absoluten Pfad
                    if (imageFile == null || !imageFile.exists()) {
                        imageFile = new File(imagePath);
                    }
                }
                
                if (imageFile != null && imageFile.exists()) {
                    try {
                        // Dateiname für das kopierte Bild
                        String imageFileName = imageFile.getName();
                        // Falls Dateiname leer oder ungültig, verwende Alt-Text oder generiere Namen
                        if (imageFileName == null || imageFileName.isEmpty() || 
                            !imageFileName.contains(".")) {
                            String extension = getFileExtension(imageFile.getName());
                            if (extension.isEmpty()) {
                                extension = "png"; // Fallback
                            }
                            imageFileName = (altText != null && !altText.isEmpty()) 
                                ? altText.replaceAll("[^a-zA-Z0-9]", "_") + "." + extension
                                : "image_" + System.currentTimeMillis() + "." + extension;
                        }
                        
                        // Kopiere Bild ins Ausgabeverzeichnis (wichtig für LaTeX!)
                        File targetImageOutput = new File(outputDir, imageFileName);
                        Files.copy(imageFile.toPath(), targetImageOutput.toPath(), 
                            StandardCopyOption.REPLACE_EXISTING);
                        logger.debug("Bild für LaTeX ins Ausgabeverzeichnis kopiert: {} -> {}", 
                            imageFile.getAbsolutePath(), targetImageOutput.getAbsolutePath());
                        
                        // Kopiere Bild auch ins pandoc-Verzeichnis (als Backup)
                        File targetImagePandoc = new File(pandocDir, imageFileName);
                        Files.copy(imageFile.toPath(), targetImagePandoc.toPath(), 
                            StandardCopyOption.REPLACE_EXISTING);
                        logger.debug("Bild für LaTeX ins pandoc-Verzeichnis kopiert: {}", 
                            targetImagePandoc.getAbsolutePath());
                        
                        // Ersetze den Pfad in der Markdown-Datei durch nur den Dateinamen
                        // LaTeX-Datei wird im Ausgabeverzeichnis erstellt, daher funktioniert relativer Pfad
                        // Füge Bildgröße hinzu, damit Bilder nicht zu groß werden (nur wenn noch keine vorhanden)
                        String replacement;
                        if (fullMatch.contains("{") && fullMatch.contains("width")) {
                            // Bereits eine Größenangabe vorhanden, behalte sie
                            replacement = "![" + altText + "](" + imageFileName + ")" + 
                                fullMatch.substring(fullMatch.indexOf("{"));
                        } else {
                            // Keine Größenangabe, füge 80% hinzu
                            replacement = "![" + altText + "](" + imageFileName + "){ width=80% }";
                        }
                        replacements.add(replacement);
                        logger.debug("Bild-Pfad in Markdown geändert zu: {} (relativ zum Ausgabeverzeichnis)", imageFileName);
                        
                    } catch (IOException e) {
                        logger.warn("Fehler beim Kopieren des Bildes {}: {}", imagePath, e.getMessage());
                        replacements.add(null);
                    }
                } else {
                    logger.warn("Bilddatei nicht gefunden: {} (versucht: {})", imagePath, 
                        imageFile != null ? imageFile.getAbsolutePath() : "null");
                    // Keine Ersetzung für nicht gefundene Bilder
                    replacements.add(null);
                }
                matchIndex++;
            }
            
            // Ersetze alle gefundenen Bild-Pfade in der Markdown-Datei (rückwärts, um Indizes nicht zu verschieben)
            for (int i = matchPositions.size() - 1; i >= 0; i--) {
                if (i < replacements.size() && replacements.get(i) != null) {
                    java.util.Map.Entry<Integer, Integer> match = matchPositions.get(i);
                    int start = match.getKey();
                    int end = match.getValue();
                    String replacement = replacements.get(i);
                    content = content.substring(0, start) + replacement + content.substring(end);
                }
            }
            
            // Nur schreiben wenn sich etwas geändert hat
            if (!content.equals(originalContent)) {
                Files.write(markdownFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
                long processedCount = replacements.stream().filter(r -> r != null).count();
                logger.debug("Markdown-Datei für LaTeX aktualisiert: {} Bilder verarbeitet", processedCount);
            }
            
        } catch (IOException e) {
            logger.error("Fehler beim Kopieren der Markdown-Bilder für LaTeX: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Kopiert alle Markdown-Bilder ins pandoc-Verzeichnis und passt die Pfade in der Markdown-Datei an
     * (für PDF/LaTeX-Export mit absoluten Pfaden)
     */
    /**
     * Speichert die Liste der kopierten Bilder für späteres Cleanup
     */
    private java.util.List<String> copiedImageFiles = new java.util.ArrayList<>();
    private java.util.List<String> centeredParagraphs = new java.util.ArrayList<>(); // Liste der zentrierten Absätze
    
    private void copyMarkdownImagesToPandocDir(File markdownFile, File pandocDir) {
        try {
            // Markdown-Datei lesen
            String content = Files.readString(markdownFile.toPath(), StandardCharsets.UTF_8);
            String originalContent = content;
            
            // Pattern für Markdown-Bilder: ![Alt-Text](Pfad)
            java.util.regex.Pattern imagePattern = java.util.regex.Pattern.compile(
                "!\\[([^\\]]*)\\]\\(([^)]+)\\)", 
                java.util.regex.Pattern.MULTILINE
            );
            
            java.util.regex.Matcher matcher = imagePattern.matcher(content);
            java.util.List<java.util.Map.Entry<Integer, Integer>> matchPositions = new java.util.ArrayList<>();
            java.util.List<String> replacements = new java.util.ArrayList<>();
            
            // Zuerst alle Matches finden und Positionen speichern
            while (matcher.find()) {
                matchPositions.add(new java.util.AbstractMap.SimpleEntry<>(matcher.start(), matcher.end()));
            }
            
            // Dann alle Matches verarbeiten
            matcher = imagePattern.matcher(content);
            int matchIndex = 0;
            while (matcher.find()) {
                if (matchIndex >= matchPositions.size()) break;
                
                String altText = matcher.group(1);
                String imagePath = matcher.group(2);
                
                // Entferne Anführungszeichen falls vorhanden
                imagePath = imagePath.trim();
                if ((imagePath.startsWith("\"") && imagePath.endsWith("\"")) || 
                    (imagePath.startsWith("'") && imagePath.endsWith("'"))) {
                    imagePath = imagePath.substring(1, imagePath.length() - 1);
                }
                
                // Prüfe ob es eine URL ist (http/https) - dann nicht kopieren
                if (imagePath.startsWith("http://") || imagePath.startsWith("https://")) {
                    logger.debug("Bild ist eine URL, wird nicht kopiert: {}", imagePath);
                    replacements.add(null); // Keine Ersetzung für URLs
                    matchIndex++;
                    continue;
                }
                
                // Versuche Bild-Datei zu finden
                File imageFile = null;
                
                // Absoluter Pfad
                if (new File(imagePath).isAbsolute()) {
                    imageFile = new File(imagePath);
                } else {
                    // Relativer Pfad - versuche relativ zur Markdown-Datei
                    File markdownDir = markdownFile.getParentFile();
                    if (markdownDir != null) {
                        imageFile = new File(markdownDir, imagePath);
                    }
                    
                    // Falls nicht gefunden, versuche relativ zum Projekt-Verzeichnis (data-Verzeichnis)
                    if ((imageFile == null || !imageFile.exists()) && projectDirectory != null) {
                        imageFile = new File(projectDirectory, imagePath);
                    }
                    
                    // Falls nicht gefunden, versuche im Hauptverzeichnis (ein Verzeichnis höher vom Projekt-Verzeichnis)
                    // Das ist nötig, weil Bilder oft im Hauptverzeichnis liegen, nicht im data-Verzeichnis
                    if ((imageFile == null || !imageFile.exists()) && projectDirectory != null) {
                        File parentDir = projectDirectory.getParentFile();
                        if (parentDir != null) {
                            File parentImageFile = new File(parentDir, imagePath);
                            if (parentImageFile.exists()) {
                                imageFile = parentImageFile;
                            }
                        }
                    }
                    
                    // Falls immer noch nicht gefunden, versuche als absoluten Pfad
                    if (imageFile == null || !imageFile.exists()) {
                        imageFile = new File(imagePath);
                    }
                }
                
                if (imageFile != null && imageFile.exists()) {
                    try {
                        // Dateiname für das kopierte Bild
                        String imageFileName = imageFile.getName();
                        // Falls Dateiname leer oder ungültig, verwende Alt-Text oder generiere Namen
                        if (imageFileName == null || imageFileName.isEmpty() || 
                            !imageFileName.contains(".")) {
                            String extension = getFileExtension(imageFile.getName());
                            if (extension.isEmpty()) {
                                extension = "png"; // Fallback
                            }
                            imageFileName = (altText != null && !altText.isEmpty()) 
                                ? altText.replaceAll("[^a-zA-Z0-9]", "_") + "." + extension
                                : "image_" + System.currentTimeMillis() + "." + extension;
                        }
                        
                        File targetImage = new File(pandocDir, imageFileName);
                        
                        // Kopiere Bild ins pandoc-Verzeichnis
                        Files.copy(imageFile.toPath(), targetImage.toPath(), 
                            StandardCopyOption.REPLACE_EXISTING);
                        
                        // Speichere kopiertes Bild für späteres Cleanup
                        copiedImageFiles.add(imageFileName);
                        
                        // Ersetze den Pfad in der Markdown-Datei durch nur den Dateinamen
                        // Pandoc findet die Bilder über --resource-path
                        String replacement = "![" + altText + "](" + imageFileName + ")";
                        replacements.add(replacement);
                        
                        logger.info("Bild kopiert: {} -> {} (Alt-Text: '{}', Ersetzung: {})", imageFile.getAbsolutePath(), 
                            targetImage.getAbsolutePath(), altText, replacement);
                        
                    } catch (IOException e) {
                        logger.warn("Fehler beim Kopieren des Bildes {}: {}", imagePath, e.getMessage());
                        replacements.add(null);
                    }
                } else {
                    logger.warn("Bilddatei nicht gefunden: {} (versucht: {})", imagePath, 
                        imageFile != null ? imageFile.getAbsolutePath() : "null");
                    // Keine Ersetzung für nicht gefundene Bilder
                    replacements.add(null);
                }
                matchIndex++;
            }
            
            // Ersetze alle gefundenen Bild-Pfade in der Markdown-Datei (rückwärts, um Indizes nicht zu verschieben)
            for (int i = matchPositions.size() - 1; i >= 0; i--) {
                if (i < replacements.size() && replacements.get(i) != null) {
                    java.util.Map.Entry<Integer, Integer> match = matchPositions.get(i);
                    int start = match.getKey();
                    int end = match.getValue();
                    String replacement = replacements.get(i);
                    content = content.substring(0, start) + replacement + content.substring(end);
                }
            }
            
            // Nur schreiben wenn sich etwas geändert hat
            if (!content.equals(originalContent)) {
                Files.write(markdownFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
                long processedCount = replacements.stream().filter(r -> r != null).count();
                logger.debug("Markdown-Datei für PDF aktualisiert: {} Bilder verarbeitet", processedCount);
            }
            
        } catch (IOException e) {
            logger.error("Fehler beim Kopieren der Markdown-Bilder für PDF: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Löscht alle während des Exports kopierten Bilder aus dem pandoc-Verzeichnis
     */
    private void cleanupCopiedImages() {
        if (copiedImageFiles.isEmpty()) {
            return;
        }
        
        File pandocDir = new File("pandoc");
        if (!pandocDir.exists() || !pandocDir.isDirectory()) {
            return;
        }
        
        int deletedCount = 0;
        for (String imageFileName : copiedImageFiles) {
            try {
                File imageFile = new File(pandocDir, imageFileName);
                if (imageFile.exists() && imageFile.isFile()) {
                    // Prüfe, ob es wirklich ein Bild ist (nur löschen, wenn es eine Bilddatei ist)
                    String lowerName = imageFileName.toLowerCase();
                    if (lowerName.endsWith(".png") || lowerName.endsWith(".jpg") || 
                        lowerName.endsWith(".jpeg") || lowerName.endsWith(".gif") || 
                        lowerName.endsWith(".bmp") || lowerName.endsWith(".svg") ||
                        lowerName.endsWith(".webp")) {
                        if (Files.deleteIfExists(imageFile.toPath())) {
                            deletedCount++;
                            logger.debug("Bild aus pandoc-Verzeichnis gelöscht: {}", imageFileName);
                        }
                    }
                }
            } catch (IOException e) {
                logger.warn("Fehler beim Löschen des Bildes {}: {}", imageFileName, e.getMessage());
            }
        }
        
        if (deletedCount > 0) {
            logger.info("{} kopierte Bilder aus pandoc-Verzeichnis gelöscht", deletedCount);
        }
        
        // Liste zurücksetzen
        copiedImageFiles.clear();
    }
    
    /**
     * Validiert eine Reference-DOC-Datei vor der Verwendung
     * Prüft ob die Datei existiert, lesbar ist und nicht gesperrt ist
     * @param referenceDoc Die zu validierende Reference-DOC-Datei
     * @return true wenn die Datei gültig ist, false sonst
     */
    private boolean validateReferenceDoc(File referenceDoc) {
        if (referenceDoc == null) {
            logger.warn("Reference-DOC ist null");
            return false;
        }
        
        if (!referenceDoc.exists()) {
            logger.warn("Reference-DOC existiert nicht: {}", referenceDoc.getAbsolutePath());
            return false;
        }
        
        if (!referenceDoc.canRead()) {
            logger.warn("Reference-DOC ist nicht lesbar: {}", referenceDoc.getAbsolutePath());
            return false;
        }
        
        if (referenceDoc.length() == 0) {
            logger.warn("Reference-DOC ist leer: {}", referenceDoc.getAbsolutePath());
            return false;
        }
        
        // Prüfe ob die Datei gesperrt ist (z.B. von Word geöffnet)
        // Versuche die Datei zu öffnen und zu lesen
        try {
            // Versuche die Datei zu öffnen und zu lesen
            try (java.io.FileInputStream fis = new java.io.FileInputStream(referenceDoc)) {
                byte[] buffer = new byte[1024];
                int bytesRead = fis.read(buffer);
                if (bytesRead == -1) {
                    logger.warn("Reference-DOC konnte nicht gelesen werden (Datei leer oder gesperrt): {}", referenceDoc.getAbsolutePath());
                    return false;
                }
                
                // Prüfe DOCX-Signatur (ZIP-Format: beginnt mit PK)
                if (bytesRead >= 2 && buffer[0] == 0x50 && buffer[1] == 0x4B) {
                    logger.debug("Reference-DOC ist gültig (DOCX-Signatur erkannt): {}", referenceDoc.getAbsolutePath());
                    return true;
                } else {
                    logger.warn("Reference-DOC hat keine gültige DOCX-Signatur: {}", referenceDoc.getAbsolutePath());
                    return false;
                }
            }
        } catch (java.io.FileNotFoundException e) {
            logger.warn("Reference-DOC ist gesperrt oder nicht zugänglich: {} - {}", referenceDoc.getAbsolutePath(), e.getMessage());
            return false;
        } catch (java.io.IOException e) {
            logger.warn("Fehler beim Lesen der Reference-DOC: {} - {}", referenceDoc.getAbsolutePath(), e.getMessage());
            return false;
        } catch (Exception e) {
            logger.warn("Unerwarteter Fehler bei Reference-DOC-Validierung: {} - {}", referenceDoc.getAbsolutePath(), e.getMessage());
            return false;
        }
    }
}
