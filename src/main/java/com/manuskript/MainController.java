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
import javafx.scene.control.*;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    
    // Status
    @FXML private ProgressBar progressBar;
    @FXML private Label lblStatus;
    
    private Stage primaryStage;
    private ObservableList<DocxFile> allDocxFiles = FXCollections.observableArrayList();
    private ObservableList<DocxFile> originalDocxFiles = FXCollections.observableArrayList(); // Ursprüngliche Reihenfolge
    private ObservableList<DocxFile> selectedDocxFiles = FXCollections.observableArrayList();
    private FilteredList<DocxFile> availableFiles;
    private SortedList<DocxFile> sortedAvailableFiles;
    private DocxProcessor docxProcessor;
    private Preferences preferences;
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        preferences = Preferences.userNodeForPackage(MainController.class);
        setupUI();
        setupEventHandlers();
        setupDragAndDrop();
        docxProcessor = new DocxProcessor();
        loadLastDirectory();
        loadRecentRegexList();
    }
    
    private void setupUI() {
        // Tabellen-Setup für verfügbare Dateien
        colFileNameAvailable.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getFileName()));
        colFileSizeAvailable.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getFormattedSize()));
        colLastModifiedAvailable.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getFormattedLastModified()));
        
        // Tabellen-Setup für ausgewählte Dateien
        colFileNameSelected.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getFileName()));
        colFileSizeSelected.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getFormattedSize()));
        colLastModifiedSelected.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getFormattedLastModified()));
        
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
            progressBar.setProgress(-1);
            
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
            progressBar.setProgress(0);
            
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
        ObservableList<DocxFile> selectedFiles = tableViewSelected.getSelectionModel().getSelectedItems();
        if (selectedFiles.isEmpty()) {
            showWarning("Keine Dateien ausgewählt", "Bitte wählen Sie mindestens eine Datei aus der rechten Tabelle aus.");
            return;
        }
        
        processFiles(selectedFiles);
    }
    
    private void processAllFiles() {
        if (selectedDocxFiles.isEmpty()) {
            showWarning("Keine Dateien vorhanden", "Bitte fügen Sie zuerst Dateien zur rechten Tabelle hinzu.");
            return;
        }
        
        processFiles(selectedDocxFiles);
    }
    
    private void processFiles(ObservableList<DocxFile> files) {
        try {
            updateStatus("Verarbeite " + files.size() + " Dateien...");
            progressBar.setProgress(0);
            
            // Hole das ausgewählte Format
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
                
                String content = docxProcessor.processDocxFileContent(docxFile.getFile(), processed + 1, format);
                result.append(content).append("\n\n");
                
                processed++;
                progressBar.setProgress((double) processed / files.size());
            }
            
            // HTML-Footer nur einmal am Ende (falls HTML-Format)
            if (format == DocxProcessor.OutputFormat.HTML) {
                result.append("</body>\n");
                result.append("</html>");
            }
            
            // Öffne den Editor mit dem verarbeiteten Text
            // Verwende den Namen der ersten Datei als Basis für den Dateinamen
            String baseFileName = files.isEmpty() ? "manuskript" : files.get(0).getFileName();
            openEditor(result.toString(), baseFileName);
            updateStatus(processed + " Dateien erfolgreich verarbeitet - Editor geöffnet");
            
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
            
            // Erstelle eine virtuelle Datei mit dem ursprünglichen Namen
            File virtualFile = new File(baseFileName);
            editorController.setCurrentFile(virtualFile);
            
            // Setze das aktuelle Format für die Dateiendung
            DocxProcessor.OutputFormat currentFormat = cmbOutputFormat.getValue();
            if (currentFormat != null) {
                editorController.setOutputFormat(currentFormat);
            }
            
            // Übergebe den DocxProcessor für DOCX-Export
            editorController.setDocxProcessor(docxProcessor);
            
            Stage editorStage = new Stage();
            editorStage.setTitle("Manuskript Editor");
            editorStage.setScene(new Scene(root));
            editorStage.getScene().getStylesheets().add(getClass().getResource("/css/editor.css").toExternalForm());
            
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
        lblStatus.setText(message);
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
} 