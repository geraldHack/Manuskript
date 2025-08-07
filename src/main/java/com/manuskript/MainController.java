package com.manuskript;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.geometry.Insets;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Optional;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.prefs.Preferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;

public class MainController implements Initializable {
    
    private static final Logger logger = LoggerFactory.getLogger(MainController.class);
    
    @FXML private VBox mainContainer;
    @FXML private Button btnSelectDirectory;
    @FXML private TextField txtDirectoryPath;
    
    // Tabellen für verfügbare Dateien (links)
    @FXML private TableView<DocxFile> tableViewAvailable;
    @FXML private TableColumn<DocxFile, String> colFileNameAvailable;
    @FXML private TableColumn<DocxFile, String> colFileSizeAvailable;
    @FXML private TableColumn<DocxFile, String> colLastModifiedAvailable;
    
    // Tabellen für ausgewählte Dateien (rechts)
    @FXML private TableView<DocxFile> tableViewSelected;
    @FXML private TableColumn<DocxFile, String> colFileNameSelected;
    @FXML private TableColumn<DocxFile, String> colFileSizeSelected;
    @FXML private TableColumn<DocxFile, String> colLastModifiedSelected;
    
    // Filter und Sortierung
    @FXML private ComboBox<String> cmbSortBy;
    @FXML private ComboBox<String> cmbRegexFilter;
    @FXML private CheckBox chkRegexMode;
    @FXML private ComboBox<String> cmbRegexSort;
    
    // Format-Auswahl
    @FXML private ComboBox<DocxProcessor.OutputFormat> cmbOutputFormat;
    
    // Buttons
    @FXML private Button btnAddToSelected;
    @FXML private Button btnRemoveFromSelected;

    @FXML private Button btnProcessSelected;
    @FXML private Button btnProcessAll;
    @FXML private Button btnThemeToggle;
    
    // Status
    // ProgressBar und lblStatus wurden entfernt
    
    private Stage primaryStage;
    private ObservableList<DocxFile> allDocxFiles = FXCollections.observableArrayList();
    private ObservableList<DocxFile> originalDocxFiles = FXCollections.observableArrayList(); // Ursprüngliche Reihenfolge
    private ObservableList<DocxFile> selectedDocxFiles = FXCollections.observableArrayList();
    private FilteredList<DocxFile> availableFiles;
    private SortedList<DocxFile> sortedAvailableFiles;
    private DocxProcessor docxProcessor;
    private Preferences preferences;
    
    // Theme-System
    private int currentThemeIndex = 0;
    private static final String[][] THEMES = {
        {"#ffffff", "#000000", "#e3f2fd", "#000000"}, // Weiß
        {"#1a1a1a", "#ffffff", "#2d2d2d", "#ffffff"}, // Schwarz
        {"#f3e5f5", "#000000", "#e1bee7", "#000000"}, // Pastell
        {"#1e3a8a", "#ffffff", "#3b82f6", "#ffffff"}, // Blau
        {"#064e3b", "#ffffff", "#10b981", "#ffffff"}, // Grün
        {"#581c87", "#ffffff", "#8b5cf6", "#ffffff"}  // Lila
    };
    private static final String[] THEME_NAMES = {"Weiß", "Schwarz", "Pastell", "Blau", "Grün", "Lila"};
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        preferences = Preferences.userNodeForPackage(MainController.class);
        setupUI();
        setupEventHandlers();
        setupDragAndDrop();
        docxProcessor = new DocxProcessor();
        loadLastDirectory();
        loadRecentRegexList();
        
        // CSS initial laden
        Platform.runLater(() -> {
            if (mainContainer != null && mainContainer.getScene() != null) {
                String cssPath = ResourceManager.getCssResource("css/editor.css");
                if (cssPath != null) {
                    mainContainer.getScene().getStylesheets().add(cssPath);
                }
                String stylesCssPath = ResourceManager.getCssResource("css/styles.css");
                if (stylesCssPath != null) {
                    mainContainer.getScene().getStylesheets().add(stylesCssPath);
                }
            }
        });
        
        // WICHTIG: Gespeichertes Theme laden und anwenden
        loadSavedTheme();
    }
    
    private void setupUI() {
        // Tabellen-Setup für verfügbare Dateien
        colFileNameAvailable.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getFileName()));
        colFileSizeAvailable.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getFormattedSize()));
        colLastModifiedAvailable.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getFormattedLastModified()));
        
        // Spaltenbreiten für verfügbare Dateien setzen
        colFileNameAvailable.setPrefWidth(260);
        colFileSizeAvailable.setPrefWidth(120);
        colLastModifiedAvailable.setPrefWidth(180);
        
        // Tabellen-Setup für ausgewählte Dateien
        colFileNameSelected.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getFileName()));
        colFileSizeSelected.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getFormattedSize()));
        colLastModifiedSelected.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getFormattedLastModified()));
        
        // Spaltenbreiten für ausgewählte Dateien setzen
        colFileNameSelected.setPrefWidth(260);
        colFileSizeSelected.setPrefWidth(120);
        colLastModifiedSelected.setPrefWidth(180);
        
        // Sortierung
        cmbSortBy.getItems().addAll("Dateiname", "Dateigröße", "Änderungsdatum");
        cmbSortBy.setValue("Dateiname");
        
        // Regex-Sortierung
        cmbRegexSort.getItems().addAll("Aufsteigend", "Absteigend");
        cmbRegexSort.setValue("Aufsteigend");
        
        // Format-Auswahl
        cmbOutputFormat.getItems().addAll(DocxProcessor.OutputFormat.values());
        cmbOutputFormat.setValue(DocxProcessor.OutputFormat.MARKDOWN);
        
        // Mehrfachauswahl aktivieren
        tableViewAvailable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        tableViewSelected.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        
        // Verfügbare Dateien (ohne ausgewählte)
        availableFiles = new FilteredList<>(allDocxFiles, p -> !selectedDocxFiles.contains(p));
        sortedAvailableFiles = new SortedList<>(availableFiles);
        tableViewAvailable.setItems(sortedAvailableFiles);
        
        // Ausgewählte Dateien
        tableViewSelected.setItems(selectedDocxFiles);
        
        // Sortierung
        sortedAvailableFiles.comparatorProperty().bind(tableViewAvailable.comparatorProperty());
        
        // TableViews direkt stylen - vor CSS-Ladung
        tableViewAvailable.setStyle("-fx-background-color: #ffffff; -fx-text-fill: #000000; -fx-border-color: #ba68c8;");
        tableViewSelected.setStyle("-fx-background-color: #ffffff; -fx-text-fill: #000000; -fx-border-color: #ba68c8;");
        
        // Status initialisieren
        updateStatus("Bereit - Wählen Sie ein Verzeichnis aus");
    }
    
    private void setupEventHandlers() {
        btnSelectDirectory.setOnAction(e -> selectDirectory());
        cmbRegexFilter.valueProperty().addListener((observable, oldValue, newValue) -> {
            applyFilters();
            // Füge zur Recent-Liste hinzu, wenn es ein neuer Wert ist
            if (newValue != null && !newValue.trim().isEmpty() && 
                (oldValue == null || !newValue.equals(oldValue))) {
                addToRecentRegex(newValue.trim());
            }
        });
        chkRegexMode.setOnAction(e -> applyFilters());
        cmbRegexSort.setOnAction(e -> applyFilters());
        cmbSortBy.setOnAction(e -> sortFiles());
        btnAddToSelected.setOnAction(e -> addSelectedToRight());
        btnRemoveFromSelected.setOnAction(e -> removeSelectedFromRight());
        

        
        btnProcessSelected.setOnAction(e -> processSelectedFiles());
        btnProcessAll.setOnAction(e -> processAllFiles());
        btnThemeToggle.setOnAction(e -> toggleTheme());
    }
    
    private void setupDragAndDrop() {
        // Drag von verfügbaren zu ausgewählten Dateien
        tableViewAvailable.setOnDragDetected(event -> {
            ObservableList<DocxFile> selectedItems = tableViewAvailable.getSelectionModel().getSelectedItems();
            if (!selectedItems.isEmpty()) {
                Dragboard db = tableViewAvailable.startDragAndDrop(TransferMode.MOVE);
                ClipboardContent content = new ClipboardContent();
                content.putString("left-to-right");
                db.setContent(content);
                event.consume();
            }
        });
        
        // Drop auf verfügbare Dateien (Entfernen)
        tableViewAvailable.setOnDragOver(event -> {
            if (event.getGestureSource() != tableViewAvailable && event.getDragboard().hasString()) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });
        
        tableViewAvailable.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasString() && "right-to-left".equals(db.getString())) {
                ObservableList<DocxFile> selectedItems = tableViewSelected.getSelectionModel().getSelectedItems();
                selectedDocxFiles.removeAll(selectedItems);
                success = true;
                updateStatus(selectedItems.size() + " Dateien aus der Auswahl entfernt");
            }
            event.setDropCompleted(success);
            event.consume();
        });
        
        // Drag & Drop Handler für tableViewSelected (nur extern)
        tableViewSelected.setOnDragDetected(event -> {
            ObservableList<DocxFile> selectedItems = tableViewSelected.getSelectionModel().getSelectedItems();
            if (!selectedItems.isEmpty()) {
                // Nur externer Drag (mit Modifier-Taste)
                if (event.isControlDown() || event.isShiftDown()) {
                    Dragboard db = tableViewSelected.startDragAndDrop(TransferMode.MOVE);
                    ClipboardContent content = new ClipboardContent();
                    content.putString("right-to-left");
                    db.setContent(content);
                    event.consume();
                }
            }
        });
        
        tableViewSelected.setOnDragOver(event -> {
            Dragboard db = event.getDragboard();
            if (db.hasString() && "left-to-right".equals(db.getString())) {
                // Nur externer Drop von links
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });
        
        tableViewSelected.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            
            if (db.hasString() && "left-to-right".equals(db.getString())) {
                // Externer Drop von links nach rechts
                ObservableList<DocxFile> selectedItems = tableViewAvailable.getSelectionModel().getSelectedItems();
                for (DocxFile file : selectedItems) {
                    if (!selectedDocxFiles.contains(file)) {
                        selectedDocxFiles.add(file);
                    }
                }
                success = true;
                updateStatus(selectedItems.size() + " Dateien zur Auswahl hinzugefügt");
            }
            
            event.setDropCompleted(success);
            event.consume();
        });
        
        // Doppelklick für schnelle Umsortierung
        // Tastatur-Pfeiltasten für interne Umsortierung (mit Modifier-Tasten)
        tableViewSelected.setOnKeyPressed(event -> {
            ObservableList<DocxFile> selectedItems = tableViewSelected.getSelectionModel().getSelectedItems();
            if (!selectedItems.isEmpty()) {
                DocxFile fileToMove = selectedItems.get(0);
                int currentIndex = selectedDocxFiles.indexOf(fileToMove);
                
                // Nur mit Strg-Taste für Umsortierung
                if (event.isControlDown()) {
                    switch (event.getCode()) {
                        case UP:
                            if (currentIndex > 0) {
                                // Tausche mit der vorherigen Datei
                                DocxFile previousFile = selectedDocxFiles.get(currentIndex - 1);
                                selectedDocxFiles.set(currentIndex - 1, fileToMove);
                                selectedDocxFiles.set(currentIndex, previousFile);
                                tableViewSelected.getSelectionModel().select(fileToMove);
                                updateStatus("Datei nach oben verschoben (Strg+↑)");
                                event.consume();
                            }
                            break;
                        case DOWN:
                            if (currentIndex < selectedDocxFiles.size() - 1) {
                                // Tausche mit der nächsten Datei
                                DocxFile nextFile = selectedDocxFiles.get(currentIndex + 1);
                                selectedDocxFiles.set(currentIndex + 1, fileToMove);
                                selectedDocxFiles.set(currentIndex, nextFile);
                                tableViewSelected.getSelectionModel().select(fileToMove);
                                updateStatus("Datei nach unten verschoben (Strg+↓)");
                                event.consume();
                            }
                            break;
                    }
                }
            }
        });
        
        tableViewSelected.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                // Doppelklick auf eine Zeile - verschiebe sie nach oben
                DocxFile clickedFile = tableViewSelected.getSelectionModel().getSelectedItem();
                if (clickedFile != null) {
                    int currentIndex = selectedDocxFiles.indexOf(clickedFile);
                    if (currentIndex > 0) {
                        selectedDocxFiles.remove(currentIndex);
                        selectedDocxFiles.add(0, clickedFile);
                        updateStatus("Datei nach oben verschoben");
                    }
                }
            }
        });
    }
    

    
    private void selectDirectory() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Verzeichnis mit DOCX-Dateien auswählen");
        
        // Verwende das gespeicherte Verzeichnis oder das aktuelle
        String lastDirectory = preferences.get("lastDirectory", "");
        if (!lastDirectory.isEmpty()) {
            File lastDir = new File(lastDirectory);
            if (lastDir.exists()) {
                directoryChooser.setInitialDirectory(lastDir);
            }
        } else if (txtDirectoryPath.getText() != null && !txtDirectoryPath.getText().isEmpty()) {
            File currentDir = new File(txtDirectoryPath.getText());
            if (currentDir.exists()) {
                directoryChooser.setInitialDirectory(currentDir);
            }
        }
        
        File selectedDirectory = directoryChooser.showDialog(primaryStage);
        if (selectedDirectory != null) {
            txtDirectoryPath.setText(selectedDirectory.getAbsolutePath());
            // Speichere das ausgewählte Verzeichnis
            preferences.put("lastDirectory", selectedDirectory.getAbsolutePath());
            loadDocxFiles(selectedDirectory);
            loadSelection(selectedDirectory);
        }
    }
    
    private void loadLastDirectory() {
        String lastDirectory = preferences.get("lastDirectory", "");
        if (!lastDirectory.isEmpty()) {
            File lastDir = new File(lastDirectory);
            if (lastDir.exists()) {
                txtDirectoryPath.setText(lastDirectory);
                loadDocxFiles(lastDir);
                loadSelection(lastDir);
            }
        }
    }
    
    private void loadDocxFiles(File directory) {
        try {
            updateStatus("Lade DOCX-Dateien...");
            // progressBar wurde entfernt
            
            List<File> files = java.nio.file.Files.walk(directory.toPath())
                    .filter(path -> path.toString().toLowerCase().endsWith(".docx"))
                    .map(java.nio.file.Path::toFile)
                    .collect(Collectors.toList());
            
            allDocxFiles.clear();
            originalDocxFiles.clear();
            selectedDocxFiles.clear();
            for (File file : files) {
                DocxFile docxFile = new DocxFile(file);
                allDocxFiles.add(docxFile);
                originalDocxFiles.add(docxFile); // Speichere ursprüngliche Reihenfolge
            }
            
            updateStatus(allDocxFiles.size() + " DOCX-Dateien gefunden");
            // progressBar wurde entfernt
            

            
        } catch (Exception e) {
            logger.error("Fehler beim Laden der DOCX-Dateien", e);
            showError("Fehler beim Laden der Dateien", e.getMessage());
            updateStatus("Fehler beim Laden der Dateien");
        }
    }
    

    
    private void applyFilters() {
        String regexFilter = cmbRegexFilter.getValue();
        boolean regexMode = chkRegexMode.isSelected();
        
        // Aktualisiere die verfügbaren Dateien (ohne ausgewählte)
        availableFiles.setPredicate(docxFile -> {
            // Datei ist bereits ausgewählt
            if (selectedDocxFiles.contains(docxFile)) {
                return false;
            }
            
            String fileName = docxFile.getFileName();
            
            // Regex-Filterung
            if (regexMode && regexFilter != null && !regexFilter.isEmpty()) {
                try {
                    Pattern pattern = Pattern.compile(regexFilter, Pattern.CASE_INSENSITIVE);
                    if (!pattern.matcher(fileName).find()) {
                        return false;
                    }
                } catch (Exception e) {
                    logger.warn("Ungültiges Regex-Pattern: {}", regexFilter);
                }
            }
            
            return true;
        });
        
        // Automatische Sortierung nach Regex-Filter
        if (regexMode && regexFilter != null && !regexFilter.isEmpty()) {
            sortFilteredResults();
        } else {
            // Wenn Regex-Filter ausgeschaltet ist, stelle die ursprüngliche Reihenfolge wieder her
            restoreOriginalOrder();
        }
        
        updateStatus("Filter angewendet - " + availableFiles.size() + " verfügbare Dateien");
    }
    
    private void sortFilteredResults() {
        try {
            String regexFilter = cmbRegexFilter.getValue();
            Pattern pattern = Pattern.compile(regexFilter, Pattern.CASE_INSENSITIVE);
            
            // Erstelle eine sortierte Liste der verfügbaren Dateien
            List<DocxFile> sortedList = new ArrayList<>(availableFiles);
            
            sortedList.sort((file1, file2) -> {
                String name1 = file1.getFileName();
                String name2 = file2.getFileName();
                
                Integer num1 = extractNumberFromPattern(name1, pattern);
                Integer num2 = extractNumberFromPattern(name2, pattern);
                
                if (num1 != null && num2 != null) {
                    int comparison = num1.compareTo(num2);
                    return "Absteigend".equals(cmbRegexSort.getValue()) ? -comparison : comparison;
                } else if (num1 != null) {
                    return -1;
                } else if (num2 != null) {
                    return 1;
                } else {
                    return name1.compareToIgnoreCase(name2);
                }
            });
            
            // Ersetze nur die verfügbaren Dateien mit der sortierten Version
            allDocxFiles.clear();
            allDocxFiles.addAll(sortedList);
            allDocxFiles.addAll(selectedDocxFiles);
            
            updateStatus("Gefilterte Ergebnisse automatisch sortiert");
            
        } catch (Exception e) {
            logger.warn("Fehler bei der automatischen Sortierung: {}", e.getMessage());
        }
    }
    
    private void restoreOriginalOrder() {
        try {
            // Stelle die ursprüngliche Reihenfolge wieder her
            allDocxFiles.clear();
            allDocxFiles.addAll(originalDocxFiles);
            
            updateStatus("Ursprüngliche Reihenfolge wiederhergestellt");
            
        } catch (Exception e) {
            logger.warn("Fehler beim Wiederherstellen der ursprünglichen Reihenfolge: {}", e.getMessage());
        }
    }
    
    private Integer extractNumberFromPattern(String fileName, Pattern pattern) {
        try {
            Matcher matcher = pattern.matcher(fileName);
            if (matcher.find()) {
                String matchedText = matcher.group();
                Pattern numberPattern = Pattern.compile("[0-9]+");
                Matcher numberMatcher = numberPattern.matcher(matchedText);
                
                if (numberMatcher.find()) {
                    return Integer.parseInt(numberMatcher.group());
                }
            }
        } catch (Exception e) {
            logger.debug("Konnte keine Zahl aus '{}' extrahieren: {}", fileName, e.getMessage());
        }
        return null;
    }
    

    
    private void sortFiles() {
        String sortBy = cmbSortBy.getValue();
        switch (sortBy) {
            case "Dateiname":
                tableViewAvailable.getSortOrder().clear();
                tableViewAvailable.getSortOrder().add(colFileNameAvailable);
                break;
            case "Dateigröße":
                tableViewAvailable.getSortOrder().clear();
                tableViewAvailable.getSortOrder().add(colFileSizeAvailable);
                break;
            case "Änderungsdatum":
                tableViewAvailable.getSortOrder().clear();
                tableViewAvailable.getSortOrder().add(colLastModifiedAvailable);
                break;
        }
    }
    
    private void addSelectedToRight() {
        ObservableList<DocxFile> selectedFiles = tableViewAvailable.getSelectionModel().getSelectedItems();
        if (selectedFiles.isEmpty()) {
            showWarning("Keine Dateien ausgewählt", "Bitte wählen Sie mindestens eine Datei aus.");
            return;
        }
        
        for (DocxFile file : selectedFiles) {
            if (!selectedDocxFiles.contains(file)) {
                selectedDocxFiles.add(file);
            }
        }
        
        updateStatus(selectedFiles.size() + " Dateien zur Auswahl hinzugefügt");
    }
    
    private void removeSelectedFromRight() {
        ObservableList<DocxFile> selectedFiles = tableViewSelected.getSelectionModel().getSelectedItems();
        if (selectedFiles.isEmpty()) {
            showWarning("Keine Dateien ausgewählt", "Bitte wählen Sie mindestens eine Datei aus.");
            return;
        }
        
        selectedDocxFiles.removeAll(selectedFiles);
        updateStatus(selectedFiles.size() + " Dateien aus der Auswahl entfernt");
    }
    

    
    private void processSelectedFiles() {
        // NEU: Gesamtdokument erstellen - ALLE Dateien aus der rechten Tabelle
        if (selectedDocxFiles.isEmpty()) {
            showWarning("Keine Dateien vorhanden", "Bitte fügen Sie zuerst Dateien zur rechten Tabelle hinzu.");
            return;
        }
        
        // Prüfe auf existierende .gesamt Dateien
        String directoryPath = selectedDocxFiles.get(0).getFile().getParent();
        File directory = new File(directoryPath);
        String directoryName = directory.getName();
        File[] existingGesamtFiles = directory.listFiles((dir, name) ->
            name.startsWith(directoryName + ".gesamt.") &&
            (name.endsWith(".md") || name.endsWith(".txt") || name.endsWith(".html"))
        );
        
        if (existingGesamtFiles != null && existingGesamtFiles.length > 0) {
            showGesamtFileDialog(existingGesamtFiles, selectedDocxFiles);
        } else {
            // Direkt Gesamtdokument erstellen für ALLE Dateien aus der rechten Tabelle
            processCompleteDocument(selectedDocxFiles);
        }
    }
    
    private void processAllFiles() {
        // NEU: Kapitel bearbeiten - alle Dateien aus der rechten Tabelle
        if (selectedDocxFiles.isEmpty()) {
            showWarning("Keine Dateien vorhanden", "Bitte fügen Sie zuerst Dateien zur rechten Tabelle hinzu.");
            return;
        }
        
        // NEU: Prüfe, ob eine Datei in der Tabelle ausgewählt ist
        ObservableList<DocxFile> selectedInTable = tableViewSelected.getSelectionModel().getSelectedItems();
        if (selectedInTable.isEmpty()) {
            // Keine Auswahl in der Tabelle - nimm die erste Datei
            processChaptersIndividually(selectedDocxFiles);
        } else {
            // Verwende die in der Tabelle ausgewählte Datei
            processChaptersIndividually(selectedInTable);
        }
    }
    
    private void processChaptersIndividually(ObservableList<DocxFile> files) {
        // NEU: Kapitel-basierte Bearbeitung - direkt aus der Tabelle
        try {
            // Speichere den Pfad zum DOCX-Verzeichnis
            if (!files.isEmpty()) {
                String docxDirectory = files.get(0).getFile().getParent();
                ResourceManager.saveParameter("ui.last_docx_directory", docxDirectory);
            }
            
            // Öffne direkt das erste ausgewählte Kapitel
            if (!files.isEmpty()) {
                openChapterEditor(files.get(0));
            }
            
        } catch (Exception e) {
            logger.error("Fehler bei der Kapitel-Verarbeitung", e);
            showError("Verarbeitungsfehler", e.getMessage());
            updateStatus("Fehler bei der Kapitel-Verarbeitung");
        }
    }
    
    private void openChapterEditor(DocxFile chapterFile) {
        try {
            // Verarbeite nur dieses eine Kapitel
            DocxProcessor.OutputFormat format = cmbOutputFormat.getValue();
            if (format == null) {
                format = DocxProcessor.OutputFormat.MARKDOWN; // Standard
            }
            
            String content = docxProcessor.processDocxFileContent(chapterFile.getFile(), 1, format);
            
            // Öffne Chapter-Editor für dieses Kapitel
            openChapterEditorWindow(content, chapterFile, format);
            
            updateStatus("Kapitel-Editor geöffnet: " + chapterFile.getFileName());
            
        } catch (Exception e) {
            logger.error("Fehler beim Öffnen des Kapitel-Editors", e);
            showError("Editor-Fehler", e.getMessage());
            updateStatus("Fehler beim Öffnen des Kapitel-Editors");
        }
    }
    
    private void openChapterEditorWindow(String text, DocxFile chapterFile, DocxProcessor.OutputFormat format) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/editor.fxml"));
            Parent root = loader.load();
            
            EditorWindow editorController = loader.getController();
            editorController.setText(text);
            editorController.setOutputFormat(format);
            
            // Erstelle Datei-Referenz für das Kapitel
            String chapterName = chapterFile.getFileName();
            if (chapterName.toLowerCase().endsWith(".docx")) {
                chapterName = chapterName.substring(0, chapterName.length() - 5);
            }
            
            // Füge Dateiendung basierend auf Format hinzu
            String fileExtension = "";
            switch (format) {
                case MARKDOWN:
                    fileExtension = ".md";
                    break;
                case PLAIN_TEXT:
                    fileExtension = ".txt";
                    break;
                case HTML:
                default:
                    fileExtension = ".html";
                    break;
            }
            
            String currentDirectory = txtDirectoryPath.getText();
            File chapterFileRef = new File(currentDirectory, chapterName + fileExtension);
            editorController.setCurrentFile(chapterFileRef);
            
            // Übergebe den DocxProcessor für DOCX-Export
            editorController.setDocxProcessor(docxProcessor);
            
            // WICHTIG: Setze die originale DOCX-Datei für Rückkonvertierung
            editorController.setOriginalDocxFile(chapterFile.getFile());
            
            // WICHTIG: Setze das aktuelle Theme vom Hauptfenster auf das Editorfenster
            editorController.setThemeFromMainWindow(currentThemeIndex);
            
            Stage editorStage = new Stage();
            editorStage.setTitle("Kapitel-Editor: " + chapterFile.getFileName());
            editorStage.setScene(new Scene(root));
            
            // NEU: Titelbalken mit Dateinamen setzen
            editorController.setWindowTitle("📄 " + chapterFile.getFileName());
            
            // NEU: Window-Preferences laden und anwenden
            loadEditorWindowProperties(editorStage);
            
            // CSS mit ResourceManager laden
            String cssPath = ResourceManager.getCssResource("css/editor.css");
            if (cssPath != null) {
                editorStage.getScene().getStylesheets().add(cssPath);
            }
            
            // Auch styles.css laden für vollständige Theme-Unterstützung
            String stylesPath = ResourceManager.getCssResource("css/styles.css");
            if (stylesPath != null) {
                editorStage.getScene().getStylesheets().add(stylesPath);
            }
            
            // WICHTIG: Stage setzen für Close-Request-Handler
            editorController.setStage(editorStage);
            
            editorStage.show();
            
        } catch (Exception e) {
            logger.error("Fehler beim Öffnen des Kapitel-Editor-Fensters", e);
            showError("Fenster-Fehler", e.getMessage());
        }
    }
    
    private void processCompleteDocument(ObservableList<DocxFile> files) {
        // ALT: Gesamtdokument erstellen (wie bisher)
        try {
            updateStatus("Verarbeite " + files.size() + " Dateien...");
            
            DocxProcessor.OutputFormat format = cmbOutputFormat.getValue();
            if (format == null) {
                format = DocxProcessor.OutputFormat.HTML;
            }
            
            StringBuilder result = new StringBuilder();
            int processed = 0;
            
            // HTML-Header nur einmal am Anfang (falls HTML-Format)
            if (format == DocxProcessor.OutputFormat.HTML) {
                result.append("<!DOCTYPE html>\n");
                result.append("<html lang=\"de\">\n");
                result.append("<head>\n");
                result.append("    <meta charset=\"UTF-8\">\n");
                result.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
                result.append("    <title>Manuskript</title>\n");
                result.append("    <style>\n");
                result.append("        body { font-family: Arial, sans-serif; line-height: 1.6; margin: 40px; }\n");
                result.append("        h1 { color: #333; border-bottom: 2px solid #333; padding-bottom: 10px; margin-bottom: 30px; }\n");
                result.append("        p { margin: 0 0 15px 0; text-align: justify; }\n");
                result.append("        hr { border: none; border-top: 1px solid #ccc; margin: 20px 0; }\n");
                result.append("        b, strong { font-weight: bold; }\n");
                result.append("        i, em { font-style: italic; }\n");
                result.append("        u { text-decoration: underline; }\n");
                result.append("    </style>\n");
                result.append("</head>\n");
                result.append("<body>\n");
            }
            
            for (DocxFile docxFile : files) {
                updateStatus("Verarbeite: " + docxFile.getFileName());
                
                // Erstelle Roman-Ordner und TXT-Dateien
                NovelManager.initializeNovelFolder(docxFile.getFile().getAbsolutePath());
                
                String content = docxProcessor.processDocxFileContent(docxFile.getFile(), processed + 1, format);
                result.append(content).append("\n\n");
                
                processed++;
            }
            
            // HTML-Footer nur einmal am Ende (falls HTML-Format)
            if (format == DocxProcessor.OutputFormat.HTML) {
                result.append("</body>\n");
                result.append("</html>");
            }
            
            // Öffne den Editor mit dem verarbeiteten Text
            String baseFileName;
            if (files.isEmpty()) {
                baseFileName = "manuskript";
            } else {
                String directoryPath = files.get(0).getFile().getParent();
                File directory = new File(directoryPath);
                baseFileName = directory.getName();
            }
            
            // Speichere den Pfad zum DOCX-Verzeichnis für den NovelManager
            if (!files.isEmpty()) {
                String docxDirectory = files.get(0).getFile().getParent();
                ResourceManager.saveParameter("ui.last_docx_directory", docxDirectory);
            }
            
            openEditor(result.toString(), baseFileName);
            updateStatus(processed + " Dateien erfolgreich verarbeitet - Gesamtdokument erstellt");
            
        } catch (Exception e) {
            logger.error("Fehler bei der Verarbeitung", e);
            showError("Verarbeitungsfehler", e.getMessage());
            updateStatus("Fehler bei der Verarbeitung");
        }
    }
    
    private void openEditor(String text, String baseFileName) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/editor.fxml"));
            Parent root = loader.load();
            
            EditorWindow editorController = loader.getController();
            editorController.setText(text);
            
            // Setze das aktuelle Format für die Dateiendung
            DocxProcessor.OutputFormat currentFormat = cmbOutputFormat.getValue();
            if (currentFormat != null) {
                editorController.setOutputFormat(currentFormat);
            }
            
            // Erstelle eine virtuelle Datei mit dem vollständigen Pfad
            String currentDirectory = txtDirectoryPath.getText();
            // Füge die korrekte Dateiendung basierend auf dem Format hinzu
            String fileExtension = "";
            if (currentFormat != null) {
                switch (currentFormat) {
                    case MARKDOWN:
                        fileExtension = ".md";
                        break;
                    case PLAIN_TEXT:
                        fileExtension = ".txt";
                        break;
                    case HTML:
                    default:
                        fileExtension = ".html";
                        break;
                }
            }
            File virtualFile = new File(currentDirectory, baseFileName + fileExtension);
            editorController.setCurrentFile(virtualFile);
            
            // Übergebe den DocxProcessor für DOCX-Export
            editorController.setDocxProcessor(docxProcessor);
            
            // WICHTIG: Setze die originale DOCX-Datei für Rückkonvertierung
            // Da es ein Gesamtdokument ist, verwenden wir die erste Datei als Referenz
            if (!selectedDocxFiles.isEmpty()) {
                File firstDocxFile = selectedDocxFiles.get(0).getFile();
                editorController.setOriginalDocxFile(firstDocxFile);
            }
            
            // WICHTIG: Setze das aktuelle Theme vom Hauptfenster auf das Editorfenster
            editorController.setThemeFromMainWindow(currentThemeIndex);
            
            // NEU: Titelbalken für Gesamtdokument setzen
            editorController.setWindowTitle("📚 Gesamtdokument: " + baseFileName);
            
            Stage editorStage = new Stage();
            editorStage.setTitle("Gesamtdokument: " + baseFileName);
            editorStage.setScene(new Scene(root));
            // CSS mit ResourceManager laden
            String cssPath = ResourceManager.getCssResource("css/editor.css");
            if (cssPath != null) {
                editorStage.getScene().getStylesheets().add(cssPath);
            }
            
            // Fenster-Größe und Position
            editorStage.setMinWidth(800);
            editorStage.setMinHeight(600);
            editorStage.setWidth(1200);
            editorStage.setHeight(800);
            
            // Zentriere das Fenster
            editorStage.centerOnScreen();
            
            editorController.setStage(editorStage);
            editorStage.show();
            
        } catch (Exception e) {
            logger.error("Fehler beim Öffnen des Editors", e);
            showError("Editor-Fehler", "Konnte den Editor nicht öffnen: " + e.getMessage());
        }
    }
    
    private void updateStatus(String message) {
        // lblStatus wurde entfernt - nur noch Logging
        logger.info("Status: {}", message);
    }
    
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    private void showWarning(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }
    
    // Recent Regex List Management
    private void loadRecentRegexList() {
        try {
            String recentRegexList = preferences.get("recentRegexList", "");
            if (!recentRegexList.isEmpty()) {
                String[] items = recentRegexList.split("\\|");
                ObservableList<String> recentItems = FXCollections.observableArrayList();
                for (String item : items) {
                    if (!item.trim().isEmpty()) {
                        recentItems.add(item.trim());
                    }
                }
                cmbRegexFilter.setItems(recentItems);
            }
        } catch (Exception e) {
            logger.warn("Fehler beim Laden der Recent Regex Liste: {}", e.getMessage());
        }
    }
    
    private void addToRecentRegex(String regex) {
        try {
            // Erstelle eine neue Liste basierend auf den aktuellen Items
            ObservableList<String> currentItems = FXCollections.observableArrayList(cmbRegexFilter.getItems());
            
            // Entferne den Eintrag, falls er bereits existiert
            currentItems.remove(regex);
            
            // Füge den neuen Eintrag am Anfang hinzu
            currentItems.add(0, regex);
            
            // Begrenze auf maximal 10 Einträge
            while (currentItems.size() > 10) {
                currentItems.remove(currentItems.size() - 1);
            }
            
            // Speichere in Preferences
            String recentRegexList = String.join("|", currentItems);
            preferences.put("recentRegexList", recentRegexList);
            
            // Aktualisiere das ComboBox mit der neuen Liste
            cmbRegexFilter.setItems(currentItems);
            
        } catch (Exception e) {
            logger.warn("Fehler beim Hinzufügen zur Recent Regex Liste: {}", e.getMessage());
        }
    }

    public void saveSelection(File directory) {
        try {
            if (directory == null) return;
            Path jsonPath = directory.toPath().resolve(".manuskript_selection.json");
            List<String> selectedNames = selectedDocxFiles.stream()
                .map(DocxFile::getFileName)
                .collect(Collectors.toList());
            String json = new Gson().toJson(selectedNames);
            Files.write(jsonPath, json.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            logger.info("Dateiauswahl gespeichert: {}", jsonPath);
            

            
        } catch (Exception e) {
            logger.warn("Fehler beim Speichern der Dateiauswahl", e);
        }
    }

    public TextField getTxtDirectoryPath() {
        return txtDirectoryPath;
    }

    private void loadSelection(File directory) {
        try {
            if (directory == null) return;
            Path jsonPath = directory.toPath().resolve(".manuskript_selection.json");
            if (!Files.exists(jsonPath)) return;
            String json = new String(Files.readAllBytes(jsonPath));
            Type listType = new TypeToken<List<String>>(){}.getType();
            List<String> selectedNames = new Gson().fromJson(json, listType);
            // Nur existierende Dateien übernehmen
            List<DocxFile> toSelect = new ArrayList<>();
            for (String name : selectedNames) {
                for (DocxFile docx : allDocxFiles) {
                    if (docx.getFileName().equals(name)) {
                        toSelect.add(docx);
                        break;
                    }
                }
            }
            selectedDocxFiles.clear();
            selectedDocxFiles.addAll(toSelect);
            // Entferne ausgewählte aus links
            availableFiles.setPredicate(docxFile -> !selectedDocxFiles.contains(docxFile));
            logger.info("Dateiauswahl geladen: {}", jsonPath);
        } catch (Exception e) {
            logger.warn("Fehler beim Laden der Dateiauswahl", e);
        }
    }
    

    
    /**
     * Wechselt das Theme
     */
    private void toggleTheme() {
        currentThemeIndex = (currentThemeIndex + 1) % THEMES.length;
        applyTheme(currentThemeIndex);
        updateThemeButtonIcon();
        updateStatus("Theme gewechselt: " + THEME_NAMES[currentThemeIndex]);
        
        // WICHTIG: Theme auch in Preferences speichern für Editorfenster-Synchronisation
        preferences.putInt("main_window_theme", currentThemeIndex);
    }
    
    /**
     * Wendet das Theme an
     */
    private void applyTheme(int themeIndex) {
        if (mainContainer == null) return;
        
        String[] theme = THEMES[themeIndex];
        String backgroundColor = theme[0];
        String textColor = theme[1];
        
        // Root-Container Theme wird jetzt im CSS-Refresh-Bereich angewendet
        
        // Alle UI-Elemente das Theme geben (direkt, ohne Platform.runLater)
        applyThemeToNode(mainContainer, themeIndex);
        applyThemeToNode(btnSelectDirectory, themeIndex);
        applyThemeToNode(txtDirectoryPath, themeIndex);
        applyThemeToNode(cmbSortBy, themeIndex);
        applyThemeToNode(cmbRegexFilter, themeIndex);
        applyThemeToNode(chkRegexMode, themeIndex);
        applyThemeToNode(cmbRegexSort, themeIndex);
        applyThemeToNode(cmbOutputFormat, themeIndex);
        applyThemeToNode(btnAddToSelected, themeIndex);
        applyThemeToNode(btnRemoveFromSelected, themeIndex);
        applyThemeToNode(btnProcessSelected, themeIndex);
        applyThemeToNode(btnProcessAll, themeIndex);
        applyThemeToNode(btnThemeToggle, themeIndex);
        
        // Tabellen
        applyThemeToNode(tableViewAvailable, themeIndex);
        applyThemeToNode(tableViewSelected, themeIndex);
        
        // Force CSS refresh - erst Theme-Klassen setzen, dann CSS laden
        if (mainContainer.getScene() != null) {
            // Erst alle Theme-Klassen entfernen und neue setzen
            Node root = mainContainer.getScene().getRoot();
            root.getStyleClass().removeAll("theme-dark", "theme-light", "blau-theme", "gruen-theme", "lila-theme", "weiss-theme", "pastell-theme");
            
            // Direkte inline Styles für Pastell-Theme
            if (themeIndex == 2) { // Pastell
                root.setStyle("-fx-background-color:rgb(115, 112, 115); -fx-text-fill: #000000;");
                mainContainer.setStyle("-fx-background-color: #f3e5f5; -fx-text-fill: #000000;");
                // TableViews direkt stylen
                Platform.runLater(() -> {
                    tableViewAvailable.setStyle("-fx-background-color: rgb(115, 112, 115);; -fx-text-fill: #000000; -fx-border-color: #ba68c8;");
                    tableViewSelected.setStyle("-fx-background-color: rgb(115, 112, 115);; -fx-text-fill: #000000; -fx-border-color: #ba68c8;");
                });
                logger.info("Pastell-Theme direkt angewendet");
            } else {
                root.setStyle(""); // Style zurücksetzen
                mainContainer.setStyle(""); // Style zurücksetzen

                
                if (themeIndex == 0) { // Weiß - Eigene CSS-Klasse
                    tableViewAvailable.setStyle("-fx-background-color: rgb(115, 112, 115);; -fx-text-fill: #000000; -fx-border-color: #ba68c8;");
                    tableViewSelected.setStyle("-fx-background-color: rgb(115, 112, 115);; -fx-text-fill: #000000; -fx-border-color: #ba68c8;");
                    root.getStyleClass().add("weiss-theme");
                } else if (themeIndex == 1) { // Schwarz
                    tableViewAvailable.setStyle("-fx-background-color: rgb(115, 112, 115);; -fx-text-fill: #000000; -fx-border-color: #ba68c8;");
                    tableViewSelected.setStyle("-fx-background-color: rgb(115, 112, 115); -fx-text-fill: #000000; -fx-border-color: #ba68c8;");
                    root.getStyleClass().add("theme-dark");
                } else if (themeIndex == 3) { // Blau
                    tableViewAvailable.setStyle("-fx-background-color: rgb(115, 112, 115); -fx-text-fill: #000000; -fx-border-color: #ba68c8;");
                    tableViewSelected.setStyle("-fx-background-color: rgb(115, 112, 115); -fx-text-fill: #000000; -fx-border-color: #ba68c8;");
                    root.getStyleClass().add("theme-dark");
                    root.getStyleClass().add("blau-theme");
                } else if (themeIndex == 4) { // Grün
                    tableViewAvailable.setStyle("-fx-background-color: rgb(115, 112, 115); -fx-text-fill: #000000; -fx-border-color: #ba68c8;");
                    tableViewSelected.setStyle("-fx-background-color: rgb(115, 112, 115); -fx-text-fill: #000000; -fx-border-color: #ba68c8;");
                    root.getStyleClass().add("theme-dark");
                    root.getStyleClass().add("gruen-theme");
                } else if (themeIndex == 5) { // Lila
                    tableViewAvailable.setStyle("-fx-background-color:rgb(115, 112, 115); -fx-text-fill: #000000; -fx-border-color: #ba68c8;");
                    tableViewSelected.setStyle("-fx-background-color: rgb(115, 112, 115); -fx-text-fill: #000000; -fx-border-color: #ba68c8;");
                    root.getStyleClass().add("theme-dark");
                    root.getStyleClass().add("lila-theme");
                }
            }
            
            // Dann CSS laden
            mainContainer.getScene().getStylesheets().clear();
            // CSS mit ResourceManager laden
            String cssPath = ResourceManager.getCssResource("css/editor.css");
            if (cssPath != null) {
                mainContainer.getScene().getStylesheets().add(cssPath);
                logger.info("CSS geladen: {}", cssPath);
            } else {
                logger.warn("CSS-Datei editor.css nicht gefunden!");
            }
            // Auch styles.css laden für vollständige Theme-Unterstützung
            String stylesCssPath = ResourceManager.getCssResource("css/styles.css");
            if (stylesCssPath != null) {
                mainContainer.getScene().getStylesheets().add(stylesCssPath);
                logger.info("CSS geladen: {}", stylesCssPath);
            } else {
                logger.warn("CSS-Datei styles.css nicht gefunden!");
            }
            
            // TableViews NACH CSS-Ladung stylen
            tableViewAvailable.setStyle("-fx-background-color:rgb(68, 101, 109); -fx-text-fill: #000000; -fx-border-color: #ba68c8;");
            tableViewSelected.setStyle("-fx-background-color:rgb(178, 36, 36); -fx-text-fill: #000000; -fx-border-color: #ba68c8;");
            
            // Debug: Aktuelle Stylesheets ausgeben
            logger.info("Aktuelle Stylesheets: {}", mainContainer.getScene().getStylesheets());
            logger.info("Root StyleClasses: {}", mainContainer.getScene().getRoot().getStyleClass());
        }
    }
    
    /**
     * Wendet das Theme auf ein einzelnes Node an
     */
    private void applyThemeToNode(javafx.scene.Node node, int themeIndex) {
        if (node != null) {
            node.getStyleClass().removeAll("theme-dark", "theme-light", "blau-theme", "gruen-theme", "lila-theme", "weiss-theme", "pastell-theme");
            if (themeIndex == 0) { // Weiß - Eigene CSS-Klasse
                node.getStyleClass().add("weiss-theme");
            } else if (themeIndex == 1) { // Schwarz
                node.getStyleClass().add("theme-dark");
            } else if (themeIndex == 2) { // Pastell - Eigene CSS-Klasse
                node.getStyleClass().add("pastell-theme");
            } else if (themeIndex == 3) { // Blau
                node.getStyleClass().add("theme-dark");
                node.getStyleClass().add("blau-theme");
            } else if (themeIndex == 4) { // Grün
                node.getStyleClass().add("theme-dark");
                node.getStyleClass().add("gruen-theme");
            } else if (themeIndex == 5) { // Lila
                node.getStyleClass().add("theme-dark");
                node.getStyleClass().add("lila-theme");
            }
        }
    }
    
    /**
     * Lädt das gespeicherte Theme aus Preferences
     */
    private void loadSavedTheme() {
        // Gespeichertes Theme aus Preferences laden
        int savedTheme = preferences.getInt("main_window_theme", 0);
        currentThemeIndex = savedTheme;
        
        // Theme anwenden
        applyTheme(currentThemeIndex);
        
        // Theme-Button Icon aktualisieren
        updateThemeButtonIcon();
        
        logger.info("Gespeichertes Theme geladen: {} ({})", currentThemeIndex, THEME_NAMES[currentThemeIndex]);
    }
    
    /**
     * Aktualisiert das Theme-Button Icon
     */
    private void updateThemeButtonIcon() {
        if (btnThemeToggle != null) {
            if (currentThemeIndex == 1 || currentThemeIndex >= 3) { // Dunkle Themes: Schwarz (1), Blau (3), Grün (4), Lila (5)
                btnThemeToggle.setText("☀"); // Sonne für dunkle Themes (einfaches Symbol)
                btnThemeToggle.setTooltip(new Tooltip("Zu hellem Theme wechseln"));
            } else { // Helle Themes: Weiß (0), Pastell (2)
                btnThemeToggle.setText("🌙"); // Mond für helle Themes
                btnThemeToggle.setTooltip(new Tooltip("Zu dunklem Theme wechseln"));
            }
        }
    }
    
    /**
     * Lädt die Editor-Window-Eigenschaften aus den Preferences
     */
    private void loadEditorWindowProperties(Stage editorStage) {
        // Fenster-Größe und Position laden
        double width = preferences.getDouble("editor_window_width", 1200.0);
        double height = preferences.getDouble("editor_window_height", 800.0);
        double x = preferences.getDouble("editor_window_x", -1.0);
        double y = preferences.getDouble("editor_window_y", -1.0);
        
        // NEU: Validierung der Fenster-Größe
        // Minimale und maximale Größen prüfen
        double minWidth = 1200.0;  // Größere Standard-Breite für Editor
        double minHeight = 800.0;  // Größere Standard-Höhe für Editor
        double maxWidth = 3000.0;
        double maxHeight = 2000.0;
        
        // Größe validieren und korrigieren
        if (width < minWidth || width > maxWidth || Double.isNaN(width) || Double.isInfinite(width)) {
            logger.warn("Ungültige Editor-Fenster-Breite: {} - verwende Standard: {}", width, minWidth);
            width = minWidth;
        }
        if (height < minHeight || height > maxHeight || Double.isNaN(height) || Double.isInfinite(height)) {
            logger.warn("Ungültige Editor-Fenster-Höhe: {} - verwende Standard: {}", height, minHeight);
            height = minHeight;
        }
        
        // Fenster-Größe setzen
        editorStage.setWidth(width);
        editorStage.setHeight(height);
        
        // NEU: Validierung der Fenster-Position
        // Prüfe, ob Position gültig ist und auf dem Bildschirm liegt
        if (x >= 0 && y >= 0 && !Double.isNaN(x) && !Double.isNaN(y) && 
            !Double.isInfinite(x) && !Double.isInfinite(y)) {
            
            // Grobe Prüfung: Position sollte nicht zu weit außerhalb des Bildschirms sein
            if (x < -1000 || y < -1000 || x > 5000 || y > 5000) {
                logger.warn("Editor-Fenster-Position außerhalb des Bildschirms: x={}, y={} - verwende zentriert", x, y);
                editorStage.centerOnScreen();
            } else {
                editorStage.setX(x);
                editorStage.setY(y);
            }
        } else {
            logger.info("Keine gültige Editor-Fenster-Position gefunden - zentriere Fenster");
            editorStage.centerOnScreen();
        }
        
        // Event-Handler für Fenster-Änderungen hinzufügen
        editorStage.widthProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(oldVal)) {
                preferences.putDouble("editor_window_width", newVal.doubleValue());
            }
        });
        
        editorStage.heightProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(oldVal)) {
                preferences.putDouble("editor_window_height", newVal.doubleValue());
            }
        });
        
        editorStage.xProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(oldVal)) {
                preferences.putDouble("editor_window_x", newVal.doubleValue());
            }
        });
        
        editorStage.yProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(oldVal)) {
                preferences.putDouble("editor_window_y", newVal.doubleValue());
            }
        });
        
        logger.info("Editor-Fenster-Eigenschaften geladen: Größe={}x{}, Position=({}, {})", width, height, x, y);
    }
    
    /**
     * Zeigt Dialog für existierende .gesamt Dateien
     */
    private void showGesamtFileDialog(File[] existingGesamtFiles, ObservableList<DocxFile> selectedDocxFiles) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Gesamtdokument existiert bereits");
        alert.setHeaderText("Es wurde bereits ein Gesamtdokument erstellt:");
        alert.setContentText("Möchten Sie das existierende Dokument laden oder ein neues erstellen?");
        
        ButtonType loadExistingButton = new ButtonType("Existierende laden");
        ButtonType createNewButton = new ButtonType("Neues erstellen");
        ButtonType cancelButton = new ButtonType("Abbrechen", ButtonBar.ButtonData.CANCEL_CLOSE);
        
        alert.getButtonTypes().setAll(loadExistingButton, createNewButton, cancelButton);
        
        // NEU: Theme direkt auf Dialog anwenden - INLINE STYLES
        DialogPane dialogPane = alert.getDialogPane();
        
        // Theme-4 (Grün) direkt anwenden
        if (currentThemeIndex == 4) {
            dialogPane.setStyle("-fx-background-color: #064e3b;");
            
            // Alle Buttons im Dialog finden und stylen
            Platform.runLater(() -> {
                for (Node node : dialogPane.lookupAll(".button")) {
                    if (node instanceof Button) {
                        Button button = (Button) node;
                        // Prüfe den Button-Text und setze unterschiedliche Breiten
                        String buttonText = button.getText();
                        if ("Existierende laden".equals(buttonText)) {
                            button.setStyle("-fx-background-color: #065f46; -fx-text-fill: #ffffff; -fx-border-color: #047857; -fx-min-width: 120px; -fx-pref-width: 120px;");
                        } else {
                            // Andere Buttons schmaler machen
                            button.setStyle("-fx-background-color: #065f46; -fx-text-fill: #ffffff; -fx-border-color: #047857; -fx-min-width: 80px; -fx-pref-width: 80px;");
                        }
                    }
                }
                // Header und Content stylen
                Node header = dialogPane.lookup(".header-panel");
                if (header != null) {
                    header.setStyle("-fx-background-color: #064e3b;");
                }
                Node content = dialogPane.lookup(".content");
                if (content != null) {
                    content.setStyle("-fx-background-color: #064e3b;");
                }
                // Labels stylen
                for (Node node : dialogPane.lookupAll(".label")) {
                    if (node instanceof Label) {
                        Label label = (Label) node;
                        label.setStyle("-fx-text-fill: #ffffff;");
                    }
                }
            });
        }
        
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent()) {
            if (result.get() == loadExistingButton) {
                loadExistingGesamtFile(existingGesamtFiles[0]);
            } else if (result.get() == createNewButton) {
                processCompleteDocument(selectedDocxFiles);
            }
        }
    }
    
    /**
     * Lädt eine existierende .gesamt Datei
     */
    private void loadExistingGesamtFile(File gesamtFile) {
        try {
            String content = new String(Files.readAllBytes(gesamtFile.toPath()), StandardCharsets.UTF_8);
            
            // Bestimme das Format aus der Dateiendung
            DocxProcessor.OutputFormat format = DocxProcessor.OutputFormat.HTML;
            String fileName = gesamtFile.getName().toLowerCase();
            if (fileName.endsWith(".md")) {
                format = DocxProcessor.OutputFormat.MARKDOWN;
            } else if (fileName.endsWith(".txt")) {
                format = DocxProcessor.OutputFormat.PLAIN_TEXT;
            }
            
            // Extrahiere den Basis-Namen
            String baseFileName = gesamtFile.getName();
            if (baseFileName.contains(".gesamt.")) {
                baseFileName = baseFileName.substring(0, baseFileName.indexOf(".gesamt."));
            }
            
            // Öffne den Editor mit dem geladenen Inhalt
            openEditor(content, baseFileName);
            updateStatus("Gesamtdokument geladen: " + gesamtFile.getName());
            
        } catch (Exception e) {
            logger.error("Fehler beim Laden der .gesamt Datei", e);
            showError("Ladefehler", "Fehler beim Laden der Datei: " + e.getMessage());
            updateStatus("Fehler beim Laden der .gesamt Datei");
        }
    }
    
} 