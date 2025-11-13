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
import com.manuskript.HelpSystem;
import com.manuskript.CustomAlert;

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

                // Cover-Bild für HTML5 hinzufügen (falls vorhanden)
                if ("html5".equals(formatComboBox.getValue()) && !coverImageField.getText().trim().isEmpty()) {
                    File coverImageFile = new File(coverImageField.getText().trim());
                    if (coverImageFile.exists()) {
                        String baseName = fileNameField.getText().replace(".html", "");
                        File htmlDir = new File(outputDirectoryField.getText(), baseName + "_html");
                        String coverFileName = getCoverFileName(coverImageFile);
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
                    // Abstract-Titel für EPUB und PDF setzen
                    if ("epub3".equals(format) || "pdf".equals(format)) {
                        writer.println("abstract-title: \"Zusammenfassung\"");
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
     * und stellt sicher, dass Bilder vor Überschriften ihre Position behalten
     */
    private void replaceHtmlTagsInMarkdown(File markdownFile, String format) {
        try {
            String content = Files.readString(markdownFile.toPath(), StandardCharsets.UTF_8);
            String originalContent = content;
            
            // Für EPUB3: Ähnliche Fixes wie für DOCX
            if ("epub3".equals(format) || "epub".equals(format)) {
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
                content = content.replaceAll(
                    "(?m)(!\\[\\]\\([^\\)]+\\)\\{ width=" + imageSizePercent + "% \\})(?!</div>)",
                    "<div align=\"center\">\n$1\n</div>"
                );
            }
            
            // HTML-Tags ersetzen basierend auf dem Ausgabeformat
            if ("pdf".equals(format)) {
                // Für PDF: LaTeX-Befehle verwenden
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
            String outputDirPath = outputDirectoryField.getText().trim();
            String fileName = fileNameField.getText().trim();
            File outputFile = new File(outputDirPath, fileName);
            File outputDir = new File(outputDirPath);
            
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
            // Superscript und Subscript Erweiterungen für alle Formate aktivieren
            command.add("--from=markdown+yaml_metadata_block+superscript+subscript");
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
            } else if ("docx".equals(format)) {
            // DOCX-spezifische Optionen für bessere Titelei
            // Reference-DOC temporär deaktiviert, da es Probleme verursacht
            // if (templateFile != null) {
                command.add("--reference-doc=\"" + templateFile.getAbsolutePath() + "\"");
            // }
                command.add("--highlight-style=tango");
                command.add("--reference-links");
                // Reihenfolge der Elemente beibehalten (kein Wrapping)
                command.add("--wrap=none");
                
                // Cover-Bild für DOCX hinzufügen (falls vorhanden)
                if (!coverImageField.getText().trim().isEmpty()) {
                    File coverImageFile = new File(coverImageField.getText().trim());
                    if (coverImageFile.exists()) {
                        try {
                            // Cover-Bild ins pandoc-Verzeichnis kopieren
                            String coverFileName = getCoverFileName(coverImageFile);
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
                command.add("--from=markdown+yaml_metadata_block+smart+superscript+subscript");
                command.add("--to=latex");
                
                // Template für PDF verwenden (vereinfachtes XeLaTeX-Template)
                File pdfTemplate = new File("pandoc-3.8.1", "simple-xelatex-template.tex");
                if (pdfTemplate.exists()) {
                    command.add("--template=" + pdfTemplate.getAbsolutePath());
                    logger.debug("Verwende vereinfachtes XeLaTeX-Template: {}", pdfTemplate.getName());
                } else {
                    logger.debug("Kein XeLaTeX-Template gefunden, verwende Standard-Template");
                }
                
                // XeLaTeX-spezifische Optionen für bessere Kompatibilität
                command.add("--variable=lang:de");
                
                // LaTeX-Optionen für bessere Kompatibilität (Template definiert bereits Fonts)
                command.add("--variable=geometry:margin=2.5cm");
                command.add("--variable=fontsize:12pt");
                command.add("--variable=documentclass:article");
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
                            File pandocDir = new File("pandoc-3.8.1");
                            
                            // Kopiere ins Ausgabeverzeichnis
                            File targetCoverOutput = new File(outputDir, coverFileName);
                            Files.copy(coverImageFile.toPath(), targetCoverOutput.toPath(),
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            
                            // Kopiere auch ins pandoc-Verzeichnis
                            File targetCoverPandoc = new File(pandocDir, coverFileName);
                            Files.copy(coverImageFile.toPath(), targetCoverPandoc.toPath(),
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            
                            // Verwende absoluten Pfad für LaTeX (mit Forward-Slashes)
                            // Moderne LaTeX-Distributionen (XeLaTeX) unterstützen Windows-Pfade mit Forward-Slashes
                            String latexPath = targetCoverOutput.getAbsolutePath().replace("\\", "/");
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
                File pandocDir = new File("pandoc-3.8.1");
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
                File luaFilter = new File("pandoc-3.8.1", "dropcaps.lua");
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
                command.add("--from=markdown+yaml_metadata_block+smart+superscript+subscript");
                command.add("--to=latex");
                
                // Template für LaTeX verwenden (vereinfachtes XeLaTeX-Template)
                File latexTemplate = new File("pandoc-3.8.1", "simple-xelatex-template.tex");
                if (latexTemplate.exists()) {
                    command.add("--template=" + latexTemplate.getAbsolutePath());
                    logger.debug("Verwende vereinfachtes XeLaTeX-Template: {}", latexTemplate.getName());
                } else {
                    logger.debug("Kein XeLaTeX-Template gefunden, verwende Standard-Template");
                }
                
                // LaTeX-Optionen
                command.add("--variable=lang:de");
                command.add("--variable=geometry:margin=2.5cm");
                command.add("--variable=fontsize:12pt");
                command.add("--variable=documentclass:article");
                command.add("--variable=linestretch:1.2");
                command.add("--variable=numbersections:false"); // Kapitelnummerierung deaktivieren
                
                // Cover-Bild für LaTeX hinzufügen (falls vorhanden)
                if (!coverImageField.getText().trim().isEmpty()) {
                    File coverImageFile = new File(coverImageField.getText().trim());
                    if (coverImageFile.exists()) {
                        try {
                            // Cover-Bild ins pandoc-Verzeichnis kopieren
                            String coverFileName = getCoverFileName(coverImageFile);
                            File pandocDir = new File("pandoc-3.8.1");
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
                
                // Markdown-Bilder ins pandoc-Verzeichnis kopieren und Pfade anpassen
                File pandocDirLatex = new File("pandoc-3.8.1");
                copyMarkdownImagesToPandocDir(markdownFile, pandocDirLatex);
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

            // Prozess starten - Arbeitsverzeichnis setzen
            ProcessBuilder pb = new ProcessBuilder(command);
            // Für PDF: Arbeitsverzeichnis auf Ausgabeverzeichnis setzen (XeLaTeX kompiliert dort)
            // Für andere Formate: Arbeitsverzeichnis auf Pandoc-Verzeichnis setzen
            if ("pdf".equals(format)) {
                if (outputDir.exists()) {
                    pb.directory(outputDir);
                    logger.debug("Arbeitsverzeichnis für PDF-Export: {}", outputDir.getAbsolutePath());
                } else {
                    pb.directory(pandocHome != null ? pandocHome : new File("pandoc-3.8.1"));
                }
            } else {
                pb.directory(pandocHome != null ? pandocHome : new File("pandoc-3.8.1")); // Arbeitsverzeichnis auf Pandoc setzen
            }
            pb.environment().put("PATH", System.getenv("PATH")); // PATH weitergeben
            
            // MiKTeX-Update-Warnungen deaktivieren
            pb.environment().put("MIKTEX_DISABLE_UPDATE_CHECK", "1");
            pb.environment().put("MIKTEX_DISABLE_INSTALLER", "1");
            pb.environment().put("MIKTEX_DISABLE_AUTO_INSTALL", "1");

            Process process = pb.start();

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

            // Warten auf Beendigung
            int exitCode = process.waitFor();
            
            // Warten bis beide Threads fertig sind (mit Timeout)
            try {
                outputThread.join(10000); // Max 10 Sekunden warten
                if (outputThread.isAlive()) {
                    logger.warn("Output-Thread konnte nicht beendet werden, fahre fort");
                }
            } catch (InterruptedException e) {
                logger.warn("Warten auf Output-Thread unterbrochen");
                Thread.currentThread().interrupt();
            }
            
            try {
                errorThread.join(10000); // Max 10 Sekunden warten
                if (errorThread.isAlive()) {
                    logger.warn("Error-Thread konnte nicht beendet werden, fahre fort");
                }
            } catch (InterruptedException e) {
                logger.warn("Warten auf Error-Thread unterbrochen");
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

            // Prüfe ob die Ausgabedatei jetzt existiert und größer als 0 ist
            File resultFile = "html5".equals(format) ?
                new File(finalOutputPath) : outputFile;

            if (resultFile.exists() && resultFile.length() > 0) {
                logger.debug("Export erfolgreich erstellt: {}", resultFile.getAbsolutePath());
                
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
                            postProcessEpub(finalResultFile);
                        } catch (Exception e) {
                            logger.warn("EPUB Post-Processing fehlgeschlagen: {}", e.getMessage());
                        }
                    });
                    epubPostProcessThread.setDaemon(true);
                    epubPostProcessThread.start();
                }
                
                // Export sofort als erfolgreich zurückgeben, Post-Processing läuft im Hintergrund
                return true;
            } else {
                logger.error("Pandoc-Export fehlgeschlagen - Datei nicht erstellt (Exit-Code: {})", exitCode);
                
                // Fehlermeldung für Benutzer zusammenstellen
                StringBuilder userErrorMessage = new StringBuilder();
                userErrorMessage.append("Pandoc-Export fehlgeschlagen (Exit-Code: ").append(exitCode).append(")\n\n");
                
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
            fallbackCommand.add("--variable=numbersections:false"); // Kapitelnummerierung deaktivieren
            
            // pdflatex-spezifische Optionen
            fallbackCommand.add("--pdf-engine-opt=-interaction=nonstopmode");
            
            logger.debug("Versuche PDF-Fallback mit pdflatex (ohne Template)...");
            
            ProcessBuilder pb = new ProcessBuilder(fallbackCommand);
            pb.directory(pandocHome != null ? pandocHome : new File("pandoc-3.8.1"));
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
        
        // ScrollPane für lange Fehlermeldungen in VBox packen
        VBox contentBox = new VBox();
        contentBox.setPadding(new Insets(10));
        
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefWidth(600);
        scrollPane.setPrefHeight(400);
        scrollPane.setStyle("-fx-background-color: transparent;");
        
        // Label erstellen - die Kodierung wurde bereits beim Lesen korrigiert
        Label contentLabel = new Label(message);
        contentLabel.setWrapText(true);
        // Font mit guter UTF-8 Unterstützung für korrekte Zeichendarstellung
        // Verwende System-Fonts, die UTF-8 gut unterstützen (Arial, Segoe UI statt Monospace)
        contentLabel.setStyle("-fx-font-family: 'Arial', 'Segoe UI', 'Tahoma', sans-serif; -fx-font-size: 11px;");
        scrollPane.setContent(contentLabel);
        
        contentBox.getChildren().add(scrollPane);
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
                
                // Alt-Text aus Bildunterschriften entfernen (falls vorhanden)
                for (XWPFParagraph paragraph : document.getParagraphs()) {
                    String text = paragraph.getText();
                    // Wenn der Absatz nur aus dem Alt-Text besteht und der vorherige Absatz ein Bild hat
                    if (text != null && !text.trim().isEmpty()) {
                        // Prüfe ob der vorherige Absatz ein Bild enthält
                        int index = document.getParagraphs().indexOf(paragraph);
                        if (index > 0) {
                            XWPFParagraph prevParagraph = document.getParagraphs().get(index - 1);
                            boolean prevHasImage = false;
                            for (XWPFRun run : prevParagraph.getRuns()) {
                                if (run.getEmbeddedPictures().size() > 0) {
                                    prevHasImage = true;
                                    break;
                                }
                            }
                            // Wenn der vorherige Absatz ein Bild hat und dieser Absatz nur Text ist,
                            // könnte es der Alt-Text sein - entfernen
                            if (prevHasImage && text.length() < 100) { // Nur kurze Texte entfernen
                                // Entferne den Text aus allen Runs
                                for (XWPFRun run : paragraph.getRuns()) {
                                    run.setText("", 0);
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
                                fos.write(buffer, 0, length);
                            }
                        }
                    }
                }
            }
            
            // HTML-Dateien bearbeiten
            processHtmlFilesInDirectory(tempDir);
            
            // TOC-Dateien bearbeiten (nav.xhtml und toc.ncx)
            processTocFiles(tempDir);
            
            // EPUB neu erstellen
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(epubFile);
                 java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(fos)) {
                
                addDirectoryToZip(tempDir, tempDir, zos);
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
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    processHtmlFilesInDirectory(file);
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
                        while (true) {
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
                            searchStart = navPointEnd + 11;
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
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            return null;
        }
        
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    File found = findFileRecursive(file, fileName);
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
        File[] files = currentDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    addDirectoryToZip(rootDir, file, zos);
                } else {
                    String relativePath = rootDir.toPath().relativize(file.toPath()).toString().replace("\\", "/");
                    java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(relativePath);
                    zos.putNextEntry(entry);
                    
                    try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = fis.read(buffer)) > 0) {
                            zos.write(buffer, 0, length);
                        }
                    }
                    
                    zos.closeEntry();
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
                    
                    // Falls nicht gefunden, versuche relativ zum Projekt-Verzeichnis
                    if ((imageFile == null || !imageFile.exists()) && projectDirectory != null) {
                        imageFile = new File(projectDirectory, imagePath);
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
                    
                    // Falls nicht gefunden, versuche relativ zum Projekt-Verzeichnis
                    if ((imageFile == null || !imageFile.exists()) && projectDirectory != null) {
                        imageFile = new File(projectDirectory, imagePath);
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
                    if (markdownDir != null) {
                        imageFile = new File(markdownDir, imagePath);
                    }
                    
                    // Falls nicht gefunden, versuche relativ zum Projekt-Verzeichnis
                    if ((imageFile == null || !imageFile.exists()) && projectDirectory != null) {
                        imageFile = new File(projectDirectory, imagePath);
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
                        
                        // Ersetze den Pfad in der Markdown-Datei durch absoluten Pfad zum Ausgabeverzeichnis
                        // XeLaTeX kompiliert im Ausgabeverzeichnis, daher muss der Pfad dort sein
                        String absolutePath = targetImageOutput.getAbsolutePath().replace("\\", "/");
                        String replacement = "![" + altText + "](" + absolutePath + ")";
                        replacements.add(replacement);
                        logger.debug("Bild-Pfad in Markdown geändert zu: {}", absolutePath);
                        
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
     * Kopiert alle Markdown-Bilder ins pandoc-Verzeichnis und passt die Pfade in der Markdown-Datei an
     * (für PDF/LaTeX-Export mit absoluten Pfaden)
     */
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
                    
                    // Falls nicht gefunden, versuche relativ zum Projekt-Verzeichnis
                    if ((imageFile == null || !imageFile.exists()) && projectDirectory != null) {
                        imageFile = new File(projectDirectory, imagePath);
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
                        
                        // Ersetze den Pfad in der Markdown-Datei durch nur den Dateinamen
                        // Pandoc findet die Bilder über --resource-path
                        String replacement = "![" + altText + "](" + imageFileName + ")";
                        replacements.add(replacement);
                        
                        logger.debug("Bild für PDF kopiert: {} -> {}", imageFile.getAbsolutePath(), 
                            targetImage.getAbsolutePath());
                        
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
}
