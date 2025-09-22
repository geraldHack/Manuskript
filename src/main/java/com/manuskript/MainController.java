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
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.FileChooser;
import javafx.stage.DirectoryChooser;
import javafx.scene.control.ListCell;
import javafx.scene.control.SplitPane;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.stage.Modality;
import javafx.stage.Window;
import javafx.scene.web.WebView;
import javafx.application.Platform;
import javafx.scene.image.ImageView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Optional;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
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
import com.manuskript.DocxSplitProcessor;
import com.manuskript.DocxSplitProcessor.Chapter;

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
    
    // Filter, Sortierung und Format-Auswahl entfernt - einfache Lösung
    
    // Buttons
    @FXML private Button btnAddToSelected;
    @FXML private Button btnRemoveFromSelected;

    @FXML private Button btnProcessSelected;
    @FXML private Button btnProcessAll;
    @FXML private Button btnThemeToggle;
    @FXML private Button btnSplit;
    @FXML private Button btnNewChapter;
    
    // Status
    // ProgressBar und lblStatus wurden entfernt
    
    private CustomStage primaryStage;
    private ObservableList<DocxFile> allDocxFiles = FXCollections.observableArrayList();
    private ObservableList<DocxFile> originalDocxFiles = FXCollections.observableArrayList(); // Ursprüngliche Reihenfolge
    private ObservableList<DocxFile> selectedDocxFiles = FXCollections.observableArrayList();
    // SortedList entfernt - einfache Lösung
    private DocxProcessor docxProcessor;
    private Preferences preferences;
    private java.nio.file.WatchService watchService;
    private Thread watchThread;
    private volatile boolean watchRunning = false;
    private volatile boolean suppressExternalChangeDialog = false;
    
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
        // loadRecentRegexList entfernt - einfache Lösung
        
                    // CSS initial laden und Theme-Klassen setzen, bevor wir Theme anwenden
            Platform.runLater(() -> {
                if (mainContainer != null && mainContainer.getScene() != null) {
                    String cssPath = ResourceManager.getCssResource("css/manuskript.css");
                    if (cssPath != null && !mainContainer.getScene().getStylesheets().contains(cssPath)) {
                        mainContainer.getScene().getStylesheets().add(cssPath);
                    }
                // Theme-Klassen auf Root vorab setzen, damit Pfeile etc. initial korrekt sind
                Node root = mainContainer.getScene().getRoot();
                root.getStyleClass().removeAll("theme-dark", "theme-light", "blau-theme", "gruen-theme", "lila-theme", "weiss-theme", "pastell-theme");
                int savedTheme = preferences.getInt("main_window_theme", 0);
                if (savedTheme == 0) {
                    root.getStyleClass().add("weiss-theme");
                } else if (savedTheme == 2) {
                    root.getStyleClass().add("pastell-theme");
                } else {
                    root.getStyleClass().add("theme-dark");
                    if (savedTheme == 3) root.getStyleClass().add("blau-theme");
                    if (savedTheme == 4) root.getStyleClass().add("gruen-theme");
                    if (savedTheme == 5) root.getStyleClass().add("lila-theme");
                }
                // Danach das gespeicherte Theme normal anwenden
                loadSavedTheme();
            } else {
                // Falls Scene noch nicht da, trotzdem Theme laden
        loadSavedTheme();
            }
        });
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
        colFileNameSelected.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getDisplayFileName()));
        colFileSizeSelected.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getFormattedSize()));
        colLastModifiedSelected.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getFormattedLastModified()));
        
        // Spaltenbreiten für ausgewählte Dateien setzen
        colFileNameSelected.setPrefWidth(260);
        colFileSizeSelected.setPrefWidth(120);
        colLastModifiedSelected.setPrefWidth(180);
        
        // Sortierung und Format-Auswahl entfernt - einfache Lösung
        
        // Mehrfachauswahl aktivieren
        tableViewAvailable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        tableViewSelected.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        
        // Direkte Verbindung: allDocxFiles → linke Tabelle
        tableViewAvailable.setItems(allDocxFiles);
        
        // Ausgewählte Dateien
        tableViewSelected.setItems(selectedDocxFiles);
        

        
        // Sortierung über TableView-Header (Standard-JavaFX)
        
        // TableViews nur Border stylen - Hintergrund über CSS
        tableViewAvailable.setStyle("-fx-border-color: #ba68c8;");
        tableViewSelected.setStyle("-fx-border-color: #ba68c8;");
        
        // Status initialisieren
        updateStatus("Bereit - Wählen Sie ein Verzeichnis aus");
    }
    
    private void setupEventHandlers() {
        btnSelectDirectory.setOnAction(e -> selectDirectory());
        // Filter, Sortierung und Format-Event-Handler entfernt - einfache Lösung
        btnAddToSelected.setOnAction(e -> addSelectedToRight());
        btnRemoveFromSelected.setOnAction(e -> removeSelectedFromRight());
        

        
        btnProcessSelected.setOnAction(e -> processSelectedFiles());
        btnProcessAll.setOnAction(e -> {
            processAllFiles();
        });
        btnThemeToggle.setOnAction(e -> toggleTheme());
        btnSplit.setOnAction(e -> openSplitStage());
        btnNewChapter.setOnAction(e -> createNewChapter());
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
                
                // Prüfe, ob Dateien MD-Dateien haben
                List<DocxFile> filesWithMd = new ArrayList<>();
                for (DocxFile file : selectedItems) {
                    File mdFile = deriveMdFileFor(file.getFile());
                    if (mdFile != null && mdFile.exists()) {
                        filesWithMd.add(file);
                    }
                }
                
                if (!filesWithMd.isEmpty()) {
                    // Dialog für Dateien mit MD-Dateien
                    boolean shouldDeleteMd = showMdDeletionDialog(filesWithMd);
                    
                    if (shouldDeleteMd) {
                        // MD-Dateien löschen und Dateien nach links verschieben
                        for (DocxFile file : filesWithMd) {
                            File mdFile = deriveMdFileFor(file.getFile());
                            if (mdFile != null && mdFile.exists()) {
                                try {
                                    mdFile.delete();
                                    logger.info("MD-Datei gelöscht: {}", mdFile.getName());
                                } catch (Exception e) {
                                    logger.error("Fehler beim Löschen der MD-Datei {}: {}", mdFile.getName(), e.getMessage());
                                }
                            }
                        }
                        
                        // Alle Dateien nach links verschieben
                selectedDocxFiles.removeAll(selectedItems);
                        for (DocxFile file : selectedItems) {
                            if (!allDocxFiles.contains(file)) {
                                allDocxFiles.add(file);
                            }
                        }
                        
                success = true;
                        updateStatus(selectedItems.size() + " Dateien nach links verschoben (MD-Dateien gelöscht)");
                    } else {
                        // MD-Dateien behalten - Dateien bleiben rechts
                        success = false;
                        updateStatus("Verschoben abgebrochen - MD-Dateien behalten");
                    }
                } else {
                    // Keine MD-Dateien - direkt nach links verschieben
                    selectedDocxFiles.removeAll(selectedItems);
                    for (DocxFile file : selectedItems) {
                        if (!allDocxFiles.contains(file)) {
                            allDocxFiles.add(file);
                        }
                    }
                    
                    success = true;
                    updateStatus(selectedItems.size() + " Dateien nach links verschoben");
                }
                
                // Keine Auswahl mehr speichern - MD-Erkennung ist ausreichend
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
                
                // Erstelle eine Kopie der ausgewählten Dateien für das Entfernen
                List<DocxFile> filesToMove = new ArrayList<>(selectedItems);
                
                for (DocxFile file : filesToMove) {
                    if (!selectedDocxFiles.contains(file)) {
                        selectedDocxFiles.add(file);
                        
                        // Automatisch MD-Datei erstellen, falls sie nicht existiert
                        File mdFile = deriveMdFileFor(file.getFile());
                        if (mdFile != null && !mdFile.exists()) {
                            try {
                                // DOCX zu MD konvertieren und speichern
                                String mdContent = docxProcessor.processDocxFileContent(file.getFile(), 1, DocxProcessor.OutputFormat.MARKDOWN);
                                java.nio.file.Files.write(mdFile.toPath(), mdContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                                logger.info("MD-Datei automatisch erstellt (Drag & Drop): {}", mdFile.getName());
                            } catch (Exception e) {
                                logger.error("Fehler beim Erstellen der MD-Datei für {}: {}", file.getFileName(), e.getMessage());
                            }
                        }
                    }
                }
                
                // Entferne die Dateien aus der linken Tabelle
                allDocxFiles.removeAll(filesToMove);
                
                // WICHTIG: Reihenfolge speichern
                String currentDir = preferences.get("lastDirectory", "");
                if (!currentDir.isEmpty()) {
                    saveSelection(new File(currentDir));
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
                                
                                // WICHTIG: Reihenfolge speichern
                                String currentDir = preferences.get("lastDirectory", "");
                                if (!currentDir.isEmpty()) {
                                    saveSelection(new File(currentDir));
                                }
                                
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
                                
                                // WICHTIG: Reihenfolge speichern
                                String currentDir = preferences.get("lastDirectory", "");
                                if (!currentDir.isEmpty()) {
                                    saveSelection(new File(currentDir));
                                }
                                
                                event.consume();
                            }
                            break;
                    }
                }
            }
        });
        
        // Doppelklick-Events für die rechte Tabelle wurden entfernt - keine automatische Sortierung
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
        }
    }
    
    private void loadLastDirectory() {
        String lastDirectory = preferences.get("lastDirectory", "");
        if (!lastDirectory.isEmpty()) {
            File lastDir = new File(lastDirectory);
            if (lastDir.exists()) {
                txtDirectoryPath.setText(lastDirectory);
                loadDocxFiles(lastDir);
            }
        }
    }
    
    private void loadDocxFiles(File directory) {
        try {
            updateStatus("Lade DOCX-Dateien...");
            
            logger.info("Scanne Verzeichnis für DOCX-Dateien: {}", directory.getAbsolutePath());
            
            // Alle DOCX-Dateien im Verzeichnis sammeln (nur flach, keine Unterverzeichnisse)
            Set<File> fileSet = java.nio.file.Files.list(directory.toPath())
                    .filter(path -> {
                        boolean isDocx = path.toString().toLowerCase().endsWith(".docx");
                        if (isDocx) {
                            logger.info("DOCX-Datei gefunden: {}", path.getFileName());
                        }
                        return isDocx;
                    })
                    .map(java.nio.file.Path::toFile)
                    .collect(Collectors.toSet()); // Set statt List für keine Duplikate
            
            List<File> files = new ArrayList<>(fileSet);
            
            logger.info("Insgesamt {} DOCX-Dateien gefunden", files.size());
            
            // Alle Listen leeren
            logger.info("=== DEBUG: Listen leeren ===");
            logger.info("allDocxFiles vor clear: {}", allDocxFiles.size());
            logger.info("originalDocxFiles vor clear: {}", originalDocxFiles.size());
            logger.info("selectedDocxFiles vor clear: {}", selectedDocxFiles.size());
            
            allDocxFiles.clear();
            originalDocxFiles.clear();
            selectedDocxFiles.clear();
            
            logger.info("allDocxFiles nach clear: {}", allDocxFiles.size());
            logger.info("originalDocxFiles nach clear: {}", originalDocxFiles.size());
            logger.info("selectedDocxFiles nach clear: {}", selectedDocxFiles.size());
            
            // Für jede Datei entscheiden: links oder rechts?
            logger.info("=== DEBUG: Dateien verarbeiten ===");
            for (File file : files) {
                DocxFile docxFile = new DocxFile(file);
                originalDocxFiles.add(docxFile);
                
                // Prüfe: Hat die Datei eine MD-Datei?
                File mdFile = deriveMdFileFor(docxFile.getFile());
                boolean hasMdFile = mdFile != null && mdFile.exists();
                
                if (hasMdFile) {
                    // Datei hat MD-Datei → nach rechts
                    selectedDocxFiles.add(docxFile);
                    logger.info("Datei nach rechts: {} (hat MD-Datei)", docxFile.getFileName());
                } else {
                    // Datei hat keine MD-Datei → nach links
                allDocxFiles.add(docxFile);
                    logger.info("Datei nach links: {} (keine MD-Datei)", docxFile.getFileName());
                }
            }
            
            logger.info("=== DEBUG: Finale Listen-Größen ===");
            logger.info("allDocxFiles: {} Dateien", allDocxFiles.size());
            logger.info("originalDocxFiles: {} Dateien", originalDocxFiles.size());
            logger.info("selectedDocxFiles: {} Dateien", selectedDocxFiles.size());
            
            // Debug: Alle Dateien in allDocxFiles auflisten
            logger.info("=== DEBUG: Dateien in allDocxFiles ===");
            for (DocxFile docxFile : allDocxFiles) {
                logger.info("  - {}", docxFile.getFileName());
            }
            
            // NEU: Hash-basierte Änderungsprüfung für alle geladenen Dateien
            checkAllDocxFilesForChanges();
            
            // WICHTIG: Gespeicherte Reihenfolge laden (falls vorhanden)
            loadSavedOrder(directory);
            
            // Status aktualisieren
            updateStatus(allDocxFiles.size() + " Dateien links, " + selectedDocxFiles.size() + " Dateien rechts");
            
            // Starte automatische Datei-Überwachung
            startFileWatcher(directory);
            
        } catch (Exception e) {
            logger.error("Fehler beim Laden der DOCX-Dateien", e);
            showError("Fehler beim Laden der Dateien", e.getMessage());
            updateStatus("Fehler beim Laden der Dateien");
        }
    }
    

    
    // Filter-Methoden entfernt - einfache Lösung
    
    private boolean showMdDeletionDialog(List<DocxFile> filesWithMd) {
        // Erstelle eine schöne Nachricht
        StringBuilder message = new StringBuilder();
        message.append("Die folgenden Dateien haben MD-Dateien:\n\n");
        
        for (DocxFile file : filesWithMd) {
            File mdFile = deriveMdFileFor(file.getFile());
            message.append("• ").append(file.getFileName()).append("\n");
            message.append("  → ").append(mdFile != null ? mdFile.getName() : "unbekannt").append("\n\n");
        }
        
        message.append("Wenn Sie die Dateien nach links verschieben möchten, müssen die MD-Dateien gelöscht werden.\n\n");
        message.append("Möchten Sie fortfahren?");
        
        // CustomAlert verwenden
        CustomAlert alert = new CustomAlert(Alert.AlertType.CONFIRMATION, "MD-Dateien löschen?");
        alert.setHeaderText("Dateien mit MD-Dateien verschieben");
        alert.setContentText(message.toString());
        
        // Theme anwenden
        alert.applyTheme(currentThemeIndex);
        
        // Owner setzen
        alert.initOwner(primaryStage);
        
        // Eigene Buttons mit deutschen Texten
        ButtonType deleteButton = new ButtonType("MD-Dateien löschen", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Abbrechen", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.setButtonTypes(deleteButton, cancelButton);
        
        // Dialog anzeigen und Ergebnis zurückgeben
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == deleteButton;
    }
    
    private void restoreOriginalOrder() {
        try {
            // Stelle die ursprüngliche Reihenfolge wieder her
            // Aber behalte die Trennung: Dateien mit MD rechts, ohne MD links
            allDocxFiles.clear();
            
            // Füge Dateien in ursprünglicher Reihenfolge hinzu
            for (DocxFile docxFile : originalDocxFiles) {
                File mdFile = deriveMdFileFor(docxFile.getFile());
                boolean hasMdFile = mdFile != null && mdFile.exists();
                
                if (hasMdFile) {
                    // Datei hat MD - nach rechts (egal ob geändert oder nicht)
                    if (!selectedDocxFiles.contains(docxFile)) {
                        selectedDocxFiles.add(docxFile);
                    }
                } else {
                    // Datei hat keine MD - nach links
                    allDocxFiles.add(docxFile);
                }
            }
            
            updateStatus("Ursprüngliche Reihenfolge wiederhergestellt");
            
                } catch (Exception e) {
            logger.warn("Fehler beim Wiederherstellen der ursprünglichen Reihenfolge: {}", e.getMessage());
        }
    }
    
    public void startFileWatcher(File directory) {
        try {
            // Stoppe vorherige Überwachung
            stopFileWatcher();
            
            // Erstelle neuen WatchService
            watchService = java.nio.file.FileSystems.getDefault().newWatchService();
            java.nio.file.Path dirPath = directory.toPath();
            
            // Registriere für Datei-Änderungen
            dirPath.register(watchService, 
                java.nio.file.StandardWatchEventKinds.ENTRY_CREATE,
                java.nio.file.StandardWatchEventKinds.ENTRY_DELETE,
                java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY);
            
            // Starte Überwachungs-Thread
            watchRunning = true;
            watchThread = new Thread(() -> {
                try {
                    while (watchRunning) {
                        java.nio.file.WatchKey key = watchService.take();
                        
                        for (java.nio.file.WatchEvent<?> event : key.pollEvents()) {
                            java.nio.file.WatchEvent.Kind<?> kind = event.kind();
                            
                            if (kind == java.nio.file.StandardWatchEventKinds.OVERFLOW) {
                                continue;
                            }
                            
                            @SuppressWarnings("unchecked")
                            java.nio.file.WatchEvent<java.nio.file.Path> ev = 
                                (java.nio.file.WatchEvent<java.nio.file.Path>) event;
                            java.nio.file.Path fileName = ev.context();
                            
                            // Nur auf DOCX-Dateien reagieren
                            if (fileName.toString().toLowerCase().endsWith(".docx")) {
                                logger.info("DOCX-Datei-Änderung erkannt: {} - {}", kind.name(), fileName);
                                
                                // Kurze Verzögerung für überschriebene Dateien
                                Thread.sleep(100);
                                
                                // Aktualisiere UI im JavaFX-Thread
                                javafx.application.Platform.runLater(() -> {
                                    refreshDocxFiles();
                                });
                                
                                // Nur einmal pro Änderung aktualisieren
                                break;
                            }
                        }
                        
                        if (!key.reset()) {
                            break;
                        }
                    }
                } catch (InterruptedException e) {
                    logger.info("Datei-Überwachung unterbrochen");
                } catch (Exception e) {
                    logger.error("Fehler in der Datei-Überwachung", e);
                }
            });
            
            watchThread.setDaemon(true);
            watchThread.start();
            
            logger.info("Automatische Datei-Überwachung gestartet für: {}", directory.getAbsolutePath());
            
        } catch (Exception e) {
            logger.error("Fehler beim Starten der Datei-Überwachung", e);
        }
    }
    
    public void stopFileWatcher() {
        logger.info("Stoppe File Watcher...");
        watchRunning = false;
        
        if (watchThread != null) {
            watchThread.interrupt();
            try {
                // Warte maximal 2 Sekunden auf Thread-Ende
                watchThread.join(2000);
                if (watchThread.isAlive()) {
                    logger.warn("WatchThread konnte nicht beendet werden - erzwinge Beendigung");
                }
            } catch (InterruptedException e) {
                logger.warn("Interrupted beim Warten auf WatchThread-Ende");
                Thread.currentThread().interrupt();
            }
            watchThread = null;
        }
        
        if (watchService != null) {
            try {
                watchService.close();
                logger.info("WatchService erfolgreich geschlossen");
            } catch (Exception e) {
                logger.error("Fehler beim Schließen des WatchService", e);
            }
            watchService = null;
        }
        
        logger.info("File Watcher gestoppt");
    }
    
    public void refreshDocxFiles() {
        try {
            String currentPath = txtDirectoryPath.getText();
            if (currentPath != null && !currentPath.isEmpty()) {
                File directory = new File(currentPath);
                if (directory.exists() && directory.isDirectory()) {
                    // NEU: Nur neue DOCX-Dateien hinzufügen, nicht alles neu laden
                    addNewDocxFiles(directory);
                    
                    // NEU: Hash-basierte Änderungsprüfung für ALLE DOCX-Dateien (links und rechts)
                    checkAllDocxFilesForChanges();
                    
                    // UI aktualisieren nach Hash-Erkennung
                    Platform.runLater(() -> {
                        // Aktualisiere die Tabellen
                        tableViewAvailable.refresh();
                        tableViewSelected.refresh();
                        
                        // Filter entfernt - einfache Lösung
                    });
                    
                    updateStatus("DOCX-Dateien automatisch aktualisiert");
                }
            }
        } catch (Exception e) {
            logger.error("Fehler beim automatischen Aktualisieren der DOCX-Dateien", e);
        }
    }
    
    private void addNewDocxFiles(File directory) {
        try {
            logger.info("Prüfe auf neue DOCX-Dateien in: {}", directory.getAbsolutePath());
            
            // Sammle alle DOCX-Dateien im Verzeichnis (nur flach)
            Set<File> currentFiles = java.nio.file.Files.list(directory.toPath())
                    .filter(path -> path.toString().toLowerCase().endsWith(".docx"))
                    .map(java.nio.file.Path::toFile)
                    .collect(Collectors.toSet());
            
            // Sammle alle bereits geladenen Dateien
            Set<File> existingFiles = new HashSet<>();
            for (DocxFile docxFile : allDocxFiles) {
                existingFiles.add(docxFile.getFile());
            }
            for (DocxFile docxFile : selectedDocxFiles) {
                existingFiles.add(docxFile.getFile());
            }
            
            // Finde neue Dateien
            Set<File> newFiles = new HashSet<>(currentFiles);
            newFiles.removeAll(existingFiles);
            
            if (!newFiles.isEmpty()) {
                logger.info("{} neue DOCX-Dateien gefunden", newFiles.size());
                
                // Füge neue Dateien hinzu
                for (File file : newFiles) {
                    DocxFile docxFile = new DocxFile(file);
                    originalDocxFiles.add(docxFile);
                    
                    // Prüfe: Hat die Datei eine MD-Datei?
                    File mdFile = deriveMdFileFor(docxFile.getFile());
                    boolean hasMdFile = mdFile != null && mdFile.exists();
                    
                    if (hasMdFile) {
                        // Datei hat MD-Datei → nach rechts
                        selectedDocxFiles.add(docxFile);
                        logger.info("Neue Datei nach rechts: {} (hat MD-Datei)", docxFile.getFileName());
                    } else {
                        // Datei hat keine MD-Datei → nach links
                        allDocxFiles.add(docxFile);
                        logger.info("Neue Datei nach links: {} (keine MD-Datei)", docxFile.getFileName());
                    }
                }
                
                updateStatus(newFiles.size() + " neue Dateien hinzugefügt");
            } else {
                logger.info("Keine neuen DOCX-Dateien gefunden");
            }
            
        } catch (Exception e) {
            logger.error("Fehler beim Hinzufügen neuer DOCX-Dateien", e);
        }
    }
    
    private void checkAllDocxFilesForChanges() {
        try {
            logger.info("Prüfe alle DOCX-Dateien auf Änderungen...");
            
            // Prüfe alle Dateien in beiden Listen
            List<DocxFile> allFiles = new ArrayList<>();
            allFiles.addAll(allDocxFiles);
            allFiles.addAll(selectedDocxFiles);
            
            for (DocxFile docxFile : allFiles) {
                String currentHash = calculateFileHash(docxFile.getFile());
                String savedHash = loadDocxHash(docxFile.getFile());
                
                if (currentHash != null && savedHash != null && !currentHash.equals(savedHash)) {
                    // Datei wurde geändert!
                    docxFile.setChanged(true);
                    logger.info("DOCX geändert erkannt: {} (Hash unterschiedlich)", docxFile.getFileName());
                    // NICHT den neuen Hash speichern - behalte den alten für Vergleich
                } else if (currentHash != null && savedHash == null) {
                    // Neue Datei - noch nie verarbeitet
                    docxFile.setChanged(true);
                    logger.info("DOCX neu erkannt: {} (kein gespeicherter Hash)", docxFile.getFileName());
                    // Hash speichern für erste Verarbeitung
                    saveDocxHash(docxFile.getFile(), currentHash);
                } else if (currentHash != null && savedHash != null && currentHash.equals(savedHash)) {
                    // Datei unverändert
                    docxFile.setChanged(false);
                    logger.info("DOCX unverändert: {} (Hash gleich)", docxFile.getFileName());
                    // Hash nur speichern wenn unverändert
                    saveDocxHash(docxFile.getFile(), currentHash);
                }
            }
            
            logger.info("Änderungsprüfung abgeschlossen");
            
        } catch (Exception e) {
            logger.error("Fehler bei der Änderungsprüfung", e);
        }
    }
    
    // checkAndMarkDocxChanges wurde in loadDocxFiles integriert
    
    /**
     * Erstellt das data-Verzeichnis im DOCX-Ordner falls es nicht existiert
     */
    private File getDataDirectory(File docxFile) {
        if (docxFile == null) return null;
        File dataDir = new File(docxFile.getParentFile(), "data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        return dataDir;
    }
    
    private File deriveMdFileFor(File docx) {
        if (docx == null) return null;
        String baseName = docx.getName();
        int idx = baseName.lastIndexOf('.');
        if (idx > 0) baseName = baseName.substring(0, idx);
        File dataDir = getDataDirectory(docx);
        return new File(dataDir, baseName + ".md");
    }
    
    private File deriveSidecarFileFor(File docx, DocxProcessor.OutputFormat format) {
        if (docx == null) return null;
        String baseName = docx.getName();
        int idx = baseName.lastIndexOf('.');
        if (idx > 0) baseName = baseName.substring(0, idx);
        String ext;
        switch (format) {
            case MARKDOWN: ext = ".md"; break;
            case PLAIN_TEXT: ext = ".txt"; break;
            case HTML: default: ext = ".html"; break;
        }
        File dataDir = getDataDirectory(docx);
        return new File(dataDir, baseName + ext);
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
    
    /**
     * Berechnet einen CRC32-Hash für eine Datei
     */
    private String calculateFileHash(File file) {
        long hash = DiffProcessor.calculateFileHash(file);
        return hash != -1 ? Long.toHexString(hash) : null;
    }
    
    /**
     * Speichert den Hash einer DOCX-Datei in einer .meta Datei
     */
    private void saveDocxHash(File docxFile, String hash) {
        try {
            File dataDir = getDataDirectory(docxFile);
            File metaFile = new File(dataDir, docxFile.getName() + ".meta");
            java.nio.file.Files.write(metaFile.toPath(), hash.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            logger.debug("Hash gespeichert für {}: {}", docxFile.getName(), hash);
        } catch (Exception e) {
            logger.error("Fehler beim Speichern des Hash für {}: {}", docxFile.getName(), e.getMessage());
        }
    }
    
    /**
     * Lädt den gespeicherten Hash einer DOCX-Datei
     */
    private String loadDocxHash(File docxFile) {
        try {
            File dataDir = getDataDirectory(docxFile);
            File metaFile = new File(dataDir, docxFile.getName() + ".meta");
            if (metaFile.exists()) {
                String hash = new String(java.nio.file.Files.readAllBytes(metaFile.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                logger.debug("Hash geladen für {}: {}", docxFile.getName(), hash);
                return hash;
            }
        } catch (Exception e) {
            logger.error("Fehler beim Laden des Hash für {}: {}", docxFile.getName(), e.getMessage());
        }
        return null;
    }
    
    /**
     * Aktualisiert den Hash einer DOCX-Datei nach erfolgreicher Übernahme
     */
    public void updateDocxHashAfterAccept(File docxFile) {
        try {
            String currentHash = calculateFileHash(docxFile);
            if (currentHash != null) {
                saveDocxHash(docxFile, currentHash);
                logger.info("Hash aktualisiert nach Übernahme für: {}", docxFile.getName());
            }
        } catch (Exception e) {
            logger.error("Fehler beim Aktualisieren des Hash für {}: {}", docxFile.getName(), e.getMessage());
        }
    }
    
    /**
     * Markiert eine DOCX-Datei als unverändert (entfernt das "!")
     */
    public void markDocxFileAsUnchanged(File docxFile) {
        try {
            // Finde die entsprechende DocxFile in beiden Listen
            boolean found = false;
            
            // Suche in allDocxFiles
            for (DocxFile file : allDocxFiles) {
                if (file.getFile().getAbsolutePath().equals(docxFile.getAbsolutePath())) {
                    file.setChanged(false);
                    logger.info("DOCX-Datei als unverändert markiert (allDocxFiles): {}", docxFile.getName());
                    found = true;
                    break;
                }
            }
            
            // Suche in selectedDocxFiles
            if (!found) {
                for (DocxFile file : selectedDocxFiles) {
                    if (file.getFile().getAbsolutePath().equals(docxFile.getAbsolutePath())) {
                        file.setChanged(false);
                        logger.info("DOCX-Datei als unverändert markiert (selectedDocxFiles): {}", docxFile.getName());
                        found = true;
                        break;
                    }
                }
            }
            
            // SOFORT die UI aktualisieren
            Platform.runLater(() -> {
                try {
                    // Beide Tabellen aktualisieren
                    if (tableViewAvailable != null) {
                        tableViewAvailable.refresh();
                    }
                    if (tableViewSelected != null) {
                        tableViewSelected.refresh();
                    }
                    
                    // Zusätzlich: Alle Zellen neu rendern
                    if (tableViewAvailable != null) {
                        tableViewAvailable.getColumns().forEach(col -> col.setVisible(false));
                        tableViewAvailable.getColumns().forEach(col -> col.setVisible(true));
                    }
                    if (tableViewSelected != null) {
                        tableViewSelected.getColumns().forEach(col -> col.setVisible(false));
                        tableViewSelected.getColumns().forEach(col -> col.setVisible(true));
                    }
                    
                    logger.info("UI sofort aktualisiert für: {}", docxFile.getName());
                } catch (Exception e) {
                    logger.error("Fehler beim Aktualisieren der UI: {}", e.getMessage());
                }
            });
            
        } catch (Exception e) {
            logger.error("Fehler beim Markieren der DOCX-Datei als unverändert: {}", e.getMessage());
        }
    }
    

    
    // sortFiles entfernt - einfache Lösung
    
    private void addSelectedToRight() {
        ObservableList<DocxFile> selectedFiles = tableViewAvailable.getSelectionModel().getSelectedItems();
        if (selectedFiles.isEmpty()) {
            showWarning("Keine Dateien ausgewählt", "Bitte wählen Sie mindestens eine Datei aus.");
            return;
        }
        
        // Sammle alle erfolgreich verarbeiteten Dateien
        List<DocxFile> successfullyProcessed = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        
        for (DocxFile file : selectedFiles) {
            if (!selectedDocxFiles.contains(file)) {
                // Prüfe, ob die DOCX-Datei existiert und lesbar ist
                if (!file.getFile().exists()) {
                    errors.add("Datei nicht gefunden: " + file.getFileName());
                    continue;
                }
                
                if (!file.getFile().canRead()) {
                    errors.add("Datei nicht lesbar: " + file.getFileName());
                    continue;
                }
                
                // Automatisch MD-Datei erstellen, falls sie nicht existiert
                File mdFile = deriveMdFileFor(file.getFile());
                if (mdFile != null && !mdFile.exists()) {
                    try {
                        // Stelle sicher, dass das data-Verzeichnis existiert
                        File dataDir = mdFile.getParentFile();
                        if (!dataDir.exists() && !dataDir.mkdirs()) {
                            errors.add("Konnte data-Verzeichnis nicht erstellen für: " + file.getFileName());
                            continue;
                        }
                        
                        // DOCX zu MD konvertieren und speichern
                        String mdContent = docxProcessor.processDocxFileContent(file.getFile(), 1, DocxProcessor.OutputFormat.MARKDOWN);
                        
                        // Validiere den Inhalt
                        if (mdContent == null || mdContent.trim().isEmpty()) {
                            errors.add("Konvertierung ergab leeren Inhalt für: " + file.getFileName());
                            continue;
                        }
                        
                        // Schreibe atomar (erst temp-Datei, dann umbenennen)
                        File tempFile = File.createTempFile("manuskript_md_", ".tmp", dataDir);
                        java.nio.file.Files.write(tempFile.toPath(), mdContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        java.nio.file.Files.move(tempFile.toPath(), mdFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        
                        logger.info("MD-Datei erfolgreich erstellt: {}", mdFile.getName());
                        
                    } catch (Exception e) {
                        String errorMsg = "Fehler beim Erstellen der MD-Datei für " + file.getFileName() + ": " + e.getMessage();
                        logger.error(errorMsg, e);
                        errors.add(errorMsg);
                        continue;
                    }
                }
                
                // Nur hinzufügen, wenn alles erfolgreich war
                selectedDocxFiles.add(file);
                successfullyProcessed.add(file);
            }
        }
        
        // Entferne nur die erfolgreich verarbeiteten Dateien aus der linken Tabelle
        allDocxFiles.removeAll(successfullyProcessed);
        
        // Keine Auswahl mehr speichern - MD-Erkennung ist ausreichend
        
        // Benutzer-Feedback
        if (!errors.isEmpty()) {
            String errorMessage = "Fehler bei " + errors.size() + " Datei(en):\n" + String.join("\n", errors);
            showError("Fehler beim Verarbeiten", errorMessage);
        }
        
        if (!successfullyProcessed.isEmpty()) {
            updateStatus(successfullyProcessed.size() + " Dateien erfolgreich zur Auswahl hinzugefügt");
        } else {
            updateStatus("Keine Dateien hinzugefügt - alle fehlgeschlagen");
        }
    }
    
    private void removeSelectedFromRight() {
        // NEU: Verwende direkte Indizes statt ObservableList
        ObservableList<Integer> selectedIndices = tableViewSelected.getSelectionModel().getSelectedIndices();
        if (selectedIndices.isEmpty()) {
            showWarning("Keine Dateien ausgewählt", "Bitte wählen Sie mindestens eine Datei aus.");
            return;
        }
        
        // Sammle die ausgewählten Dateien über Indizes
        List<DocxFile> selectedFiles = new ArrayList<>();
        for (Integer index : selectedIndices) {
            if (index >= 0 && index < selectedDocxFiles.size()) {
                DocxFile file = selectedDocxFiles.get(index);
                selectedFiles.add(file);
            }
        }
        
        if (selectedFiles.isEmpty()) {
            showWarning("Keine gültigen Dateien ausgewählt", "Bitte wählen Sie mindestens eine Datei aus.");
            return;
        }
        
        logger.info("=== DEBUG: removeSelectedFromRight START ===");
        logger.info("selectedFiles.size(): {}", selectedFiles.size());
        logger.info("selectedDocxFiles.size(): {}", selectedDocxFiles.size());
        logger.info("allDocxFiles.size(): {}", allDocxFiles.size());
        
        // Debug: Alle ausgewählten Dateien auflisten
        for (int i = 0; i < selectedFiles.size(); i++) {
            DocxFile file = selectedFiles.get(i);
            logger.info("selectedFiles[{}]: {} (File: {})", i, file.getFileName(), file.getFile().getAbsolutePath());
        }
        
        // Prüfe, ob Dateien MD-Dateien haben
        List<DocxFile> filesWithMd = new ArrayList<>();
        for (DocxFile file : selectedFiles) {
            File mdFile = deriveMdFileFor(file.getFile());
            if (mdFile != null && mdFile.exists()) {
                filesWithMd.add(file);
            }
        }
        
        if (!filesWithMd.isEmpty()) {
            // Dialog für Dateien mit MD-Dateien
            boolean shouldDeleteMd = showMdDeletionDialog(filesWithMd);
            
            if (shouldDeleteMd) {
                // MD-Dateien löschen und Dateien nach links verschieben
                for (DocxFile file : filesWithMd) {
                    File mdFile = deriveMdFileFor(file.getFile());
                    if (mdFile != null && mdFile.exists()) {
                        try {
                            mdFile.delete();
                            logger.info("MD-Datei gelöscht: {}", mdFile.getName());
                        } catch (Exception e) {
                            logger.error("Fehler beim Löschen der MD-Datei {}: {}", mdFile.getName(), e.getMessage());
                        }
                    }
                }
                
                // Alle Dateien nach links verschieben
                logger.info("=== DEBUG: Verschieben nach links ===");
                logger.info("Ausgewählte Dateien: {}", selectedFiles.size());
                for (DocxFile file : selectedFiles) {
                    logger.info("  - {}", file.getFileName());
        }
        
        selectedDocxFiles.removeAll(selectedFiles);
                logger.info("Nach removeAll: selectedDocxFiles = {}", selectedDocxFiles.size());
                
                for (DocxFile file : selectedFiles) {
                    if (!allDocxFiles.contains(file)) {
                        allDocxFiles.add(file);
                        logger.info("Hinzugefügt zu allDocxFiles: {}", file.getFileName());
                    } else {
                        logger.info("Datei bereits in allDocxFiles: {}", file.getFileName());
                    }
                }
                
                logger.info("Final: allDocxFiles = {}", allDocxFiles.size());
                logger.info("=== DEBUG: Ende Verschieben ===");
                
                // Tabellen explizit aktualisieren
                tableViewSelected.refresh();
                tableViewAvailable.refresh();
                
                updateStatus(selectedFiles.size() + " Dateien nach links verschoben (MD-Dateien gelöscht)");
            } else {
                // MD-Dateien behalten - Dateien bleiben rechts
                updateStatus("Verschoben abgebrochen - MD-Dateien behalten");
                return;
            }
        } else {
            // Keine MD-Dateien - direkt nach links verschieben
            logger.info("=== DEBUG: Verschieben nach links (keine MD) ===");
            logger.info("Ausgewählte Dateien: {}", selectedFiles.size());
            for (DocxFile file : selectedFiles) {
                logger.info("  - {}", file.getFileName());
            }
            
            selectedDocxFiles.removeAll(selectedFiles);
            logger.info("Nach removeAll: selectedDocxFiles = {}", selectedDocxFiles.size());
            
            for (DocxFile file : selectedFiles) {
                if (!allDocxFiles.contains(file)) {
                    allDocxFiles.add(file);
                    logger.info("Hinzugefügt zu allDocxFiles: {}", file.getFileName());
                } else {
                    logger.info("Datei bereits in allDocxFiles: {}", file.getFileName());
                }
            }
            
            logger.info("Final: allDocxFiles = {}", allDocxFiles.size());
            logger.info("=== DEBUG: Ende Verschieben (keine MD) ===");
            
            // Tabellen explizit aktualisieren
            tableViewSelected.refresh();
            tableViewAvailable.refresh();
            
            updateStatus(selectedFiles.size() + " Dateien nach links verschoben");
        }
        
        // Keine Auswahl mehr speichern - MD-Erkennung ist ausreichend
    }
    

    
    private void processSelectedFiles() {
        // NEU: Gesamtdokument erstellen - ALLE Dateien aus der rechten Tabelle
        if (selectedDocxFiles.isEmpty()) {
            showWarning("Keine Dateien vorhanden", "Bitte fügen Sie zuerst Dateien zur rechten Tabelle hinzu.");
            return;
        }
        
        // Prüfe auf existierende .gesamt Dateien im Hauptverzeichnis
        String directoryPath = selectedDocxFiles.get(0).getFile().getParent();
        File directory = new File(directoryPath);
        String directoryName = directory.getName();
        File[] existingGesamtFiles = directory.listFiles((dir, name) ->
            (name.startsWith(directoryName + ".gesamt.") || name.equals(directoryName + " Gesamtdokument.md")) &&
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
        logger.info("=== DEBUG: 'Kapitel bearbeiten' Button wurde gedrückt ===");

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
            // Verarbeite nur dieses eine Kapitel - nur noch MD
            DocxProcessor.OutputFormat format = DocxProcessor.OutputFormat.MARKDOWN;
            
            // Prüfe ob eine MD-Datei existiert
            File mdFile = deriveMdFileFor(chapterFile.getFile());
            
            if (mdFile != null && mdFile.exists()) {
                // MD-Datei existiert - PRÜFE OB DOCX EXTERN VERÄNDERT WURDE
                if (DiffProcessor.hasDocxChanged(chapterFile.getFile(), mdFile)) {
                    DocxChangeDecision decision = showDocxChangedDialogInMain(chapterFile);
                    switch (decision) {
                        case DIFF: {
                            try {
                                String docxContent = docxProcessor.processDocxFileContent(chapterFile.getFile(), 1, format);
                                String mdContent = new String(java.nio.file.Files.readAllBytes(mdFile.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                                DiffProcessor.DiffResult diff = DiffProcessor.createDiff(docxContent, mdContent);
                                showDetailedDiffDialog(chapterFile, mdFile, diff, format);
                            } catch (Exception e) {
                                logger.error("Fehler beim Anzeigen des DOCX/MD-Diffs", e);
                                showError("Fehler", "Diff konnte nicht angezeigt werden: " + e.getMessage());
                            }
                            return; // nach Diff kein Editor öffnen
                        }
                        case DOCX: {

                            try {
                                String docxContent = docxProcessor.processDocxFileContent(chapterFile.getFile(), 1, format);
                                openChapterEditorWindow(docxContent, chapterFile, format);
                                updateStatus("Kapitel-Editor geöffnet (DOCX übernommen): " + chapterFile.getFileName());
                                // Hash aktualisieren und "!" aus Tabelle entfernen
                                updateDocxHashAfterAccept(chapterFile.getFile());
                                markDocxFileAsUnchanged(chapterFile.getFile());
                            } catch (Exception e) {
                                logger.error("Fehler beim Übernehmen des DOCX-Inhalts", e);
                                showError("Fehler", "DOCX-Inhalt konnte nicht übernommen werden: " + e.getMessage());
                            }
                            return; // Editor bereits geöffnet
                        }
                        case IGNORE: {

                            // Hash aktualisieren und mit MD fortfahren
                            try {
                                updateDocxHashAfterAccept(chapterFile.getFile());
                                // "!" aus Tabelle entfernen
                                markDocxFileAsUnchanged(chapterFile.getFile());
                            } catch (Exception e) {
                                logger.warn("Konnte DOCX-Hash nicht aktualisieren", e);
                            }
                            break; // weiter unten MD laden
                        }
                        case CANCEL:
                        default:
                            logger.info("Aktion abgebrochen – kein Editor geöffnet");
                            return;
                    }
                } else {
                }

                // MD-Datei existiert - lade MD-Inhalt
                try {
                    String mdContent = new String(java.nio.file.Files.readAllBytes(mdFile.toPath()), java.nio.charset.StandardCharsets.UTF_8);

                    // Öffne Chapter-Editor mit MD-Inhalt
                    openChapterEditorWindow(mdContent, chapterFile, format);
                    updateStatus("Kapitel-Editor geöffnet (MD): " + chapterFile.getFileName());

                } catch (Exception e) {
                    logger.error("Fehler beim Laden der MD-Datei", e);
                    // Fallback: Lade DOCX-Inhalt
                    String content = docxProcessor.processDocxFileContent(chapterFile.getFile(), 1, format);
                    openChapterEditorWindow(content, chapterFile, format);
                    updateStatus("Kapitel-Editor geöffnet (DOCX-Fallback): " + chapterFile.getFileName());
                }
            } else {
                // Keine MD-Datei - konvertiere DOCX zu MD und speichere
                logger.info("Keine MD-Datei gefunden, konvertiere DOCX zu MD: {}", chapterFile.getFileName());
                String content = docxProcessor.processDocxFileContent(chapterFile.getFile(), 1, format);
                
                // Speichere als MD-Datei
                if (mdFile != null) {
                    try {
                        java.nio.file.Files.write(mdFile.toPath(), content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        logger.info("MD-Datei erstellt: {}", mdFile.getAbsolutePath());
                        updateStatus("MD-Datei erstellt: " + chapterFile.getFileName());
                    } catch (Exception e) {
                        logger.error("Fehler beim Speichern der MD-Datei", e);
                    }
                }
                
                // Öffne Chapter-Editor mit konvertiertem Inhalt
                openChapterEditorWindow(content, chapterFile, format);
                updateStatus("Kapitel-Editor geöffnet (DOCX→MD): " + chapterFile.getFileName());
            }
            
        } catch (Exception e) {
            logger.error("Fehler beim Öffnen des Kapitel-Editors", e);
            showError("Editor-Fehler", e.getMessage());
            updateStatus("Fehler beim Öffnen des Kapitel-Editors");
        }
    }
    

    
    /**
     * Wiederverwendbare Prozedur für Dialog-Styling (MainController) - EXAKT wie showSaveDialogForNavigation
     */
    private void applyThemeToDialog(Alert alert, String headerTextContains) {
        // Theme-Farben holen
        String backgroundColor = THEMES[currentThemeIndex][0];
        String textColor = THEMES[currentThemeIndex][1];
        
        // CSS-Styles für den Dialog direkt anwenden
        String dialogStyle = String.format(
            "-fx-background-color: %s; -fx-text-fill: %s; -fx-control-inner-background: %s;",
            backgroundColor, textColor, backgroundColor
        );
        
        alert.getDialogPane().setStyle(dialogStyle);
        
        // EXAKT das gleiche Pattern wie in showSaveDialogForNavigation
        alert.setOnShown(event -> {
            // Header-Text thematisieren
            Node headerLabel = alert.getDialogPane().lookup(".header-panel .label");
            if (headerLabel == null) {
                headerLabel = alert.getDialogPane().lookup(".header-panel");
            }
            if (headerLabel == null) {
                headerLabel = alert.getDialogPane().lookup(".dialog-pane .header-panel");
            }
            // Falls noch nicht gefunden: direkt Header-Region stylen
            Node headerRegion = alert.getDialogPane().lookup(".header-panel");
            if (headerLabel == null) {
                // Versuche alle Labels im Dialog zu finden
                for (Node node : alert.getDialogPane().lookupAll(".label")) {
                    if (node instanceof Label) {
                        Label label = (Label) node;
                        if (label.getText() != null && label.getText().contains(headerTextContains)) {
                            headerLabel = label;
                            break;
                        }
                    }
                }
            }
            
            if (headerLabel != null) {
                headerLabel.setStyle(String.format(
                    "-fx-background-color: %s; -fx-text-fill: %s;",
                    backgroundColor, textColor
                ));
            } else if (headerRegion != null) {
                headerRegion.setStyle(String.format(
                    "-fx-background-color: %s; -fx-text-fill: %s;",
                    backgroundColor, textColor
                ));
            }
            
            // Buttons thematisieren
            String buttonStyle = String.format(
                "-fx-background-color: %s; -fx-text-fill: %s; -fx-border-color: %s;",
                backgroundColor, textColor, textColor
            );
            
            for (ButtonType buttonType : alert.getButtonTypes()) {
                Button button = (Button) alert.getDialogPane().lookupButton(buttonType);
                if (button != null) {
                    button.setStyle(buttonStyle);
                }
            }
        });
    }
    
    /**
     * Entscheidungsmöglichkeiten bei geänderter DOCX
     */
    public enum DocxChangeDecision { DIFF, DOCX, IGNORE, CANCEL }

    /**
     * Zeigt Dialog wenn DOCX-Datei extern verändert wurde (für MainController)
     */
    public DocxChangeDecision showDocxChangedDialogInMain(DocxFile chapterFile) {
        // WICHTIG: Dialog unterdrücken wenn suppressExternalChangeDialog aktiv ist
        if (suppressExternalChangeDialog) {
            logger.info("External Change Dialog unterdrückt für: {}", chapterFile.getFileName());
            return DocxChangeDecision.IGNORE;
        }

        
        CustomAlert alert = new CustomAlert(Alert.AlertType.CONFIRMATION, "DOCX-Datei wurde extern verändert");
        alert.setHeaderText("Die DOCX-Datei '" + chapterFile.getFileName() + "' wurde extern verändert.");
        alert.setContentText("Was möchten Sie tun?");
        
        // Owner setzen
        alert.initOwner(primaryStage);

        ButtonType diffButton = new ButtonType("🔍 Diff anzeigen");
        ButtonType docxButton = new ButtonType("DOCX übernehmen");
        ButtonType ignoreButton = new ButtonType("Ignorieren");
        ButtonType cancelButton = new ButtonType("Abbrechen");

        alert.setButtonTypes(diffButton, docxButton, ignoreButton, cancelButton);

        // Theme explizit anwenden, bevor der Dialog angezeigt wird
        alert.applyTheme(currentThemeIndex);
            Optional<ButtonType> result = null;
            try {
         result = alert.showAndWait();

        } catch (Exception e) {
        }

        if (!result.isPresent()) return DocxChangeDecision.CANCEL;

        if (result.get() == diffButton) return DocxChangeDecision.DIFF;
        if (result.get() == docxButton) return DocxChangeDecision.DOCX;
        if (result.get() == ignoreButton) return DocxChangeDecision.IGNORE;
        return DocxChangeDecision.CANCEL;
    }
    
    // showDiffForDocxChangesInMain entfällt – Diff wird direkt im Aufrufer erstellt
    
    /**
     * Findet DocxFile anhand des Dateinamens
     */
    private DocxFile findDocxFileByName(String fileName) {
        for (DocxFile docxFile : selectedDocxFiles) {
            if (docxFile.getFile().getName().equals(fileName)) {
                return docxFile;
            }
        }
        return null;
    }
    
    public void showDetailedDiffDialog(DocxFile chapterFile, File mdFile, DiffProcessor.DiffResult diffResult, 
                                      DocxProcessor.OutputFormat format) {
        try {
            // Erstelle Diff-Fenster mit spezieller Diff-Stage
            CustomStage diffStage = StageManager.createDiffStage("Diff: " + chapterFile.getFileName(), primaryStage);
            
            VBox diffRoot = new VBox(10);
            diffRoot.setPadding(new Insets(15));
            diffRoot.setPrefWidth(1600);  // Angepasst an neue Diff-Fenster Größe
            diffRoot.setPrefHeight(900);
            
            // ECHTE THEME-FARBEN für Container
            String themeBgColor = THEMES[currentThemeIndex][0]; // Hauptfarbe
            String themeBorderColor = THEMES[currentThemeIndex][2]; // Akzentfarbe
            
            diffRoot.setStyle(String.format("-fx-background-color: %s; -fx-border-color: %s; -fx-border-width: 2px;", themeBgColor, themeBorderColor));
            
            Label titleLabel = new Label("Änderungen in " + chapterFile.getFileName());
            titleLabel.setStyle(String.format("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: %s;", THEMES[currentThemeIndex][1]));
            
            // Lade beide Versionen
            String mdContent = new String(java.nio.file.Files.readAllBytes(mdFile.toPath()), java.nio.charset.StandardCharsets.UTF_8);
            String docxContent = docxProcessor.processDocxFileContent(chapterFile.getFile(), 1, format);
            
            // Erstelle HBox für feste nebeneinander Anzeige (beide Seiten immer gleich breit)
            HBox contentBox = new HBox(10);
            contentBox.setPrefHeight(750);  // Angepasst an neue Diff-Fenster Größe
            contentBox.setStyle("-fx-background-color: transparent;");
            
            // Linke Seite: Aktuelle Version (MD) - feste Breite
            VBox leftBox = new VBox(5);
            leftBox.setPrefWidth(650); // Feste Breite
            leftBox.setMinWidth(650);
            leftBox.setMaxWidth(650);
            leftBox.setStyle(String.format("-fx-background-color: %s; -fx-border-color: %s; -fx-border-width: 2px;", themeBgColor, themeBorderColor));
            leftBox.setPadding(new Insets(10));
            
            Label leftLabel = new Label("📄 Aktuelle Version (MD)");
            leftLabel.setStyle(String.format("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: %s;", THEMES[currentThemeIndex][1]));
            
            ScrollPane leftScrollPane = new ScrollPane();
            leftScrollPane.setStyle(
                "-fx-fit-to-width: true; -fx-fit-to-height: false; -fx-pannable: true; " +
                "-fx-hbar-policy: as-needed; -fx-vbar-policy: always; -fx-background-color: transparent;"
            );

            // Scrollbar-Styling nach dem UI-Aufbau
            Platform.runLater(() -> {
                /* Viewport-Hintergrund */
                javafx.scene.Node vp = leftScrollPane.lookup(".viewport");
                if (vp instanceof javafx.scene.layout.Region r)
                    r.setStyle("-fx-background-color: transparent; -fx-background-radius: 6;");

                /* Scrollbars stylen (Track/Thumb etc.): */
                javafx.scene.control.ScrollBar vbar =
                    (javafx.scene.control.ScrollBar) leftScrollPane.lookup(".scroll-bar:vertical");
                if (vbar != null) {
                    vbar.setStyle("-fx-pref-width: 10;");                            // Breite
                    javafx.scene.layout.Region thumb = (javafx.scene.layout.Region) vbar.lookup(".thumb");
                    if (thumb != null) thumb.setStyle(String.format("-fx-background-color: %s; -fx-background-radius: 6;", THEMES[currentThemeIndex][2]));
                    javafx.scene.layout.Region track = (javafx.scene.layout.Region) vbar.lookup(".track");
                    if (track != null) track.setStyle("-fx-background-color: transparent;");
                }
            });
            VBox leftContentBox = new VBox(0);
            leftContentBox.setPadding(new Insets(5));
            leftContentBox.setStyle("-fx-background-color: transparent;");
            
            // Rechte Seite: Neue Version (DOCX) mit Checkboxen - breiter für bessere Checkbox-Sichtbarkeit
            VBox rightBox = new VBox(5);
            rightBox.setPrefWidth(750); // Breiter für Checkbox + Padding
            rightBox.setMinWidth(750);
            rightBox.setMaxWidth(750);
            rightBox.setStyle(String.format("-fx-background-color: %s; -fx-border-color: %s; -fx-border-width: 2px;", themeBgColor, themeBorderColor));
            rightBox.setPadding(new Insets(10));
            
            Label rightLabel = new Label("📝 Neue Version (DOCX) - Wähle Änderungen aus:");
            rightLabel.setStyle(String.format("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: %s;", THEMES[currentThemeIndex][1]));
            
            ScrollPane rightScrollPane = new ScrollPane();
            rightScrollPane.setStyle(
                "-fx-fit-to-width: true; -fx-fit-to-height: false; -fx-pannable: true; " +
                "-fx-hbar-policy: as-needed; -fx-vbar-policy: always; -fx-background-color: transparent;"
            );

            // Scrollbar-Styling nach dem UI-Aufbau
            Platform.runLater(() -> {
                /* Viewport-Hintergrund */
                javafx.scene.Node vp2 = rightScrollPane.lookup(".viewport");
                if (vp2 instanceof javafx.scene.layout.Region r2)
                    r2.setStyle("-fx-background-color: transparent; -fx-background-radius: 6;");

                /* Scrollbars stylen (Track/Thumb etc.): */
                javafx.scene.control.ScrollBar vbar2 =
                    (javafx.scene.control.ScrollBar) rightScrollPane.lookup(".scroll-bar:vertical");
                if (vbar2 != null) {
                    vbar2.setStyle("-fx-pref-width: 10;");                            // Breite
                    javafx.scene.layout.Region thumb2 = (javafx.scene.layout.Region) vbar2.lookup(".thumb");
                    if (thumb2 != null) thumb2.setStyle(String.format("-fx-background-color: %s; -fx-background-radius: 6;", THEMES[currentThemeIndex][2]));
                    javafx.scene.layout.Region track2 = (javafx.scene.layout.Region) vbar2.lookup(".track");
                    if (track2 != null) track2.setStyle("-fx-background-color: transparent;");
                }
            });
            VBox rightContentBox = new VBox(0);
            rightContentBox.setPadding(new Insets(5));
            rightContentBox.setStyle("-fx-background-color: transparent;");
            
            // Verwende echte DiffProcessor-Logik für intelligente Block-Erkennung
            List<CheckBox> blockCheckBoxes = new ArrayList<>();
            List<List<String>> blockTexts = new ArrayList<>();
            
            // Erstelle echten Diff mit Block-Erkennung
            DiffProcessor.DiffResult realDiff = DiffProcessor.createDiff(mdContent, docxContent);
            
            // Gruppiere zusammenhängende Änderungen zu Blöcken
            List<DiffBlock> blocks = groupIntoBlocks(realDiff.getDiffLines());
            
            // Erstelle synchronisierte Anzeige basierend auf Blöcken
            
            for (DiffBlock block : blocks) {
                // Checkbox nur für grüne Blöcke (ADDED)
                CheckBox blockCheckBox = null;
                if (block.getType() == DiffBlockType.ADDED) {
                    blockCheckBox = new CheckBox();
                    blockCheckBox.setSelected(false); // Standardmäßig ungecheckt
                    blockCheckBoxes.add(blockCheckBox);
                    
                    List<String> blockTextList = new ArrayList<>();
                    for (DiffProcessor.DiffLine line : block.getLines()) {
                        blockTextList.add(line.getNewText());
                    }
                    blockTexts.add(blockTextList);
                }
                
                // Erstelle Zeilen für diesen Block
                for (DiffProcessor.DiffLine diffLine : block.getLines()) {
                    HBox leftLineBox = new HBox(5);
                    HBox rightLineBox = new HBox(5);
                    
                    // Zeilennummern aus DiffLine verwenden
                    Label leftLineNum = new Label(String.format("%3d", diffLine.getLeftLineNumber()));
                    leftLineNum.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 10px; -fx-text-fill: #6c757d; -fx-min-width: 30px; -fx-alignment: center-right;");
                    
                    Label rightLineNum = new Label(String.format("%3d", diffLine.getRightLineNumber()));
                    rightLineNum.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 10px; -fx-text-fill: #6c757d; -fx-min-width: 30px; -fx-alignment: center-right;");
                    
                    // Linke Seite (MD)
                    Label leftLineLabel = new Label(diffLine.getOriginalText());
                    leftLineLabel.setWrapText(true);
                    leftLineLabel.setPrefWidth(620); // Gleiche Breite wie rechtes Label
                    leftLineLabel.setMinHeight(Region.USE_PREF_SIZE);
                    leftLineLabel.setMaxHeight(Region.USE_PREF_SIZE);
                    leftLineLabel.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 12px;");
                    
                    // Rechte Seite (DOCX) - Checkbox nur am Anfang des Blocks
                    Label rightLineLabel = new Label(diffLine.getNewText());
                    rightLineLabel.setWrapText(true);
                    rightLineLabel.setPrefWidth(620); // Reduziert für Checkbox-Platz
                    rightLineLabel.setMinHeight(Region.USE_PREF_SIZE);
                    rightLineLabel.setMaxHeight(Region.USE_PREF_SIZE);
                    rightLineLabel.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 12px;");
                    
                    // Synchronisiere die Höhen beider Labels
                    Platform.runLater(() -> {
                        double maxHeight = Math.max(leftLineLabel.getHeight(), rightLineLabel.getHeight());
                        leftLineLabel.setMinHeight(maxHeight);
                        leftLineLabel.setMaxHeight(maxHeight);
                        rightLineLabel.setMinHeight(maxHeight);
                        rightLineLabel.setMaxHeight(maxHeight);
                    });
                    
                    // Markiere basierend auf Block-Typ
                    switch (block.getType()) {
                        case ADDED:
                            // Neuer Block - nur rechts sichtbar, aber Zeilennummern auf beiden Seiten
                            leftLineLabel.setText("");
                            leftLineLabel.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 12px; -fx-background-color: #d4edda; -fx-text-fill: #155724;");
                            rightLineLabel.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 12px; -fx-background-color: #d4edda; -fx-text-fill: #155724; -fx-font-weight: bold;");
                            leftLineNum.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 10px; -fx-text-fill: #6c757d; -fx-min-width: 30px; -fx-alignment: center-right;");
                            rightLineNum.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 10px; -fx-text-fill: #28a745; -fx-min-width: 30px; -fx-alignment: center-right; -fx-font-weight: bold;");
                            break;
                            
                        case DELETED:
                            // Gelöschter Block - links rot, rechts leer aber sichtbar, Zeilennummern auf beiden Seiten
                            rightLineLabel.setText("");
                            leftLineLabel.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 12px; -fx-background-color: #f8d7da; -fx-text-fill: #721c24; -fx-font-weight: bold;");
                            leftLineNum.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 10px; -fx-text-fill: #dc3545; -fx-min-width: 30px; -fx-alignment: center-right; -fx-font-weight: bold;");
                            rightLineLabel.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 12px; -fx-background-color: #f8d7da; -fx-text-fill: #721c24;");
                            rightLineNum.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 10px; -fx-text-fill: #6c757d; -fx-min-width: 30px; -fx-alignment: center-right;");
                            break;
                            
                        case UNCHANGED:
                            // Unveränderter Block - viel heller und unaufdringlicher (aber Theme-Textfarbe für Konsistenz)
                            String lightOpacity = "0.4"; // Sehr transparent für unaufdringlichen Look
                            leftLineLabel.setStyle(String.format("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 12px; -fx-text-fill: %s; -fx-background-color: rgba(240,240,240,0.2); -fx-opacity: %s;", THEMES[currentThemeIndex][1], lightOpacity));
                            rightLineLabel.setStyle(String.format("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 12px; -fx-text-fill: %s; -fx-background-color: rgba(240,240,240,0.2); -fx-opacity: %s;", THEMES[currentThemeIndex][1], lightOpacity));
                            break;
                    }
                    
                    leftLineBox.getChildren().addAll(leftLineNum, leftLineLabel);
                    
                    // Checkbox RECHTS vertikal zentriert am Ende des Blocks
                    if (blockCheckBox != null && block.getLines().indexOf(diffLine) == block.getLines().size() - 1) {
                        // Container für vertikal zentrierte Checkbox
                        VBox checkboxContainer = new VBox();
                        checkboxContainer.setAlignment(Pos.CENTER);
                        checkboxContainer.setMinWidth(30);
                        checkboxContainer.setMaxWidth(30);
                        checkboxContainer.getChildren().add(blockCheckBox);
                        
                        rightLineBox.getChildren().addAll(rightLineNum, rightLineLabel, checkboxContainer);
                    } else {
                        rightLineBox.getChildren().addAll(rightLineNum, rightLineLabel);
                    }
                    
                    leftContentBox.getChildren().add(leftLineBox);
                    rightContentBox.getChildren().add(rightLineBox);
                }
            }
            
            // Synchronisiere Scrollbars
            leftScrollPane.vvalueProperty().bindBidirectional(rightScrollPane.vvalueProperty());
            leftScrollPane.hvalueProperty().bindBidirectional(rightScrollPane.hvalueProperty());
            
            leftScrollPane.setContent(leftContentBox);
            leftScrollPane.setFitToWidth(true);
            leftScrollPane.setPrefHeight(600);
            // leftScrollPane.setStyle("-fx-background-color: transparent;"); // Entfernt - überschreibt CSS
            
            rightScrollPane.setContent(rightContentBox);
            rightScrollPane.setFitToWidth(true);
            rightScrollPane.setPrefHeight(600);
            // rightScrollPane.setStyle("-fx-background-color: transparent;"); // Entfernt - überschreibt CSS
            
            leftBox.getChildren().addAll(leftLabel, leftScrollPane);
            rightBox.getChildren().addAll(rightLabel, rightScrollPane);
            
            contentBox.getChildren().addAll(leftBox, rightBox);
            
            // Button-Box
            HBox buttonBox = new HBox(15);
            buttonBox.setAlignment(Pos.CENTER);
            buttonBox.setPadding(new Insets(15, 0, 0, 0));
            
            Button btnApplySelected = new Button("✅ Ausgewählte Änderungen übernehmen");
            btnApplySelected.setStyle("-fx-background-color: rgba(40,167,69,0.8); -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8px 16px;");
            
            Button btnAcceptAll = new Button("🔄 docx übernehmen");
            btnAcceptAll.setStyle("-fx-background-color: rgba(0,123,255,0.8); -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8px 16px;");
            
            Button btnKeepCurrent = new Button("💾 Aktuelle Version behalten");
            btnKeepCurrent.setStyle("-fx-background-color: rgba(108,117,125,0.8); -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8px 16px;");
            
            Button btnCancel = new Button("❌ Abbrechen");
            btnCancel.setStyle("-fx-background-color: rgba(220,53,69,0.8); -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8px 16px;");
            
            btnApplySelected.setOnAction(e -> {
                try {
                    // WICHTIG: Baue den vollständigen Text aus ALLEN Blöcken zusammen
                    // Verwende die gleiche Logik wie beim Erstellen der Diff-Anzeige
                    StringBuilder newContent = new StringBuilder();
                    int checkboxIndex = 0;
                    
                    // Gehe durch alle Blöcke und baue den Text zusammen
                    for (DiffBlock block : blocks) {
                        switch (block.getType()) {
                            case ADDED:
                                // Neue Blöcke nur hinzufügen, wenn ausgewählt
                                if (checkboxIndex < blockCheckBoxes.size() && blockCheckBoxes.get(checkboxIndex).isSelected()) {
                                    for (DiffProcessor.DiffLine line : block.getLines()) {
                                        String text = line.getNewText();
                                        if (text != null && !text.isEmpty()) {
                                            newContent.append(text).append("\n");
                                        }
                                    }
                                }
                                checkboxIndex++;
                                break;
                            case DELETED:
                                // Gelöschte Blöcke überspringen (werden nicht hinzugefügt)
                                break;
                            case UNCHANGED:
                                // Unveränderte Blöcke immer hinzufügen
                                for (DiffProcessor.DiffLine line : block.getLines()) {
                                    String text = line.getNewText();
                                    if (text != null && !text.isEmpty()) {
                                        newContent.append(text).append("\n");
                                    }
                                }
                                break;
                        }
                    }
                    
                    // Keine MD-Datei speichern - nur den Inhalt verwenden
                    
                    // 1. EditorWindow mit Originaltext erstellen
                    EditorWindow editorController = openChapterEditorWindow(mdContent, chapterFile, format);
                    
                    // 2. Nach Diff-Auswahl den Text ersetzen (damit Änderungen erkannt werden)
                    if (editorController != null) {
                        Platform.runLater(() -> {
                            editorController.replaceTextWithoutUpdatingOriginal(newContent.toString());
                        });
                    }
                    
                    updateDocxHashAfterAccept(chapterFile.getFile());
                    diffStage.close();
                } catch (Exception ex) {
                    logger.error("Fehler beim Übernehmen der ausgewählten Änderungen", ex);
                    showError("Fehler", ex.getMessage());
                }
            });
            
            btnAcceptAll.setOnAction(e -> {
                try {
                    // Übernehme den DOCX-Inhalt direkt (wie vorher)
                    openChapterEditorWindow(docxContent, chapterFile, format);
                    chapterFile.setChanged(false);
                    updateDocxHashAfterAccept(chapterFile.getFile());
                    // WICHTIG: Das "!" aus der Tabelle entfernen
                    markDocxFileAsUnchanged(chapterFile.getFile());
                    diffStage.close();
                } catch (Exception ex) {
                    logger.error("Fehler beim Übernehmen aller Änderungen", ex);
                    showError("Fehler", ex.getMessage());
                }
            });
            
            btnKeepCurrent.setOnAction(e -> {
                try {
                    openChapterEditorWindow(mdContent, chapterFile, format);
                    chapterFile.setChanged(false);
                    updateDocxHashAfterAccept(chapterFile.getFile());
                    // WICHTIG: Das "!" aus der Tabelle entfernen
                    markDocxFileAsUnchanged(chapterFile.getFile());
                    diffStage.close();
                } catch (Exception ex) {
                    logger.error("Fehler beim Behalten der aktuellen Version", ex);
                    showError("Fehler", ex.getMessage());
                }
            });
            
            btnCancel.setOnAction(e -> diffStage.close());
            
            buttonBox.getChildren().addAll(btnApplySelected, btnAcceptAll, btnKeepCurrent, btnCancel);
            
            diffRoot.getChildren().addAll(titleLabel, contentBox, buttonBox);
            
            Scene diffScene = new Scene(diffRoot);
            // CSS wird über ResourceManager geladen
            diffScene.getStylesheets().add(ResourceManager.getCssResource("config/css/manuskript.css"));
             
            // Theme anwenden
            String currentTheme = ResourceManager.getParameter("ui.theme", "default");
            if ("weiss".equals(currentTheme)) {
                diffRoot.getStyleClass().add("weiss-theme");
            } else if ("pastell".equals(currentTheme)) {
                diffRoot.getStyleClass().add("pastell-theme");
            } else if ("blau".equals(currentTheme)) {
                diffRoot.getStyleClass().addAll("theme-dark", "blau-theme");
            } else if ("gruen".equals(currentTheme)) {
                diffRoot.getStyleClass().addAll("theme-dark", "gruen-theme");
            } else if ("lila".equals(currentTheme)) {
                diffRoot.getStyleClass().addAll("theme-dark", "lila-theme");
            } else {
                diffRoot.getStyleClass().add("theme-dark");
            }
          
            // WICHTIG: Setze Owner und Modality um "Popping" zu verhindern
            diffStage.initOwner(primaryStage);
            diffStage.initModality(Modality.WINDOW_MODAL);
            
            diffStage.setSceneWithTitleBar(diffScene);
            // Theme nach dem Setzen der Scene erneut anwenden, damit Titelleiste korrekte Textfarbe hat
            try {
                diffStage.setFullTheme(currentThemeIndex);
            } catch (Exception ignore) {}
            diffStage.showAndWait();
            
        } catch (Exception e) {
            logger.error("Fehler beim Anzeigen des detaillierten Diff-Dialogs", e);
            showError("Diff-Fehler", e.getMessage());
        }
    }
    
    /**
     * Gruppiert Diff-Linien zu zusammenhängenden Blöcken
     */
    private List<DiffBlock> groupIntoBlocks(List<DiffProcessor.DiffLine> diffLines) {
        List<DiffBlock> blocks = new ArrayList<>();
        if (diffLines.isEmpty()) return blocks;
        
        DiffBlock currentBlock = new DiffBlock(diffLines.get(0).getType());
        currentBlock.addLine(diffLines.get(0));
        
        for (int i = 1; i < diffLines.size(); i++) {
            DiffProcessor.DiffLine line = diffLines.get(i);
            
            // Wenn der Typ sich ändert, erstelle einen neuen Block
            if (line.getType() != convertToDiffType(currentBlock.getType())) {
                blocks.add(currentBlock);
                currentBlock = new DiffBlock(line.getType());
            }
            currentBlock.addLine(line);
        }
        
        blocks.add(currentBlock);
        return blocks;
    }
    
    /**
     * Repräsentiert einen zusammenhängenden Diff-Block
     */
    private static class DiffBlock {
        private final DiffBlockType type;
        private final List<DiffProcessor.DiffLine> lines = new ArrayList<>();
        
        public DiffBlock(DiffProcessor.DiffType type) {
            this.type = convertDiffType(type);
        }
        
        public void addLine(DiffProcessor.DiffLine line) {
            lines.add(line);
        }
        
        public DiffBlockType getType() {
            return type;
        }
        
        public List<DiffProcessor.DiffLine> getLines() {
            return lines;
        }
        
        private DiffBlockType convertDiffType(DiffProcessor.DiffType diffType) {
            switch (diffType) {
                case ADDED: return DiffBlockType.ADDED;
                case DELETED: return DiffBlockType.DELETED;
                case UNCHANGED: return DiffBlockType.UNCHANGED;
                default: return DiffBlockType.UNCHANGED;
            }
        }
    }
    
    /**
     * Block-Typen für Diff-Blöcke
     */
    private enum DiffBlockType {
        ADDED, DELETED, UNCHANGED
    }
    
    /**
     * Konvertiert DiffBlockType zu DiffProcessor.DiffType
     */
    private DiffProcessor.DiffType convertToDiffType(DiffBlockType blockType) {
        switch (blockType) {
            case ADDED: return DiffProcessor.DiffType.ADDED;
            case DELETED: return DiffProcessor.DiffType.DELETED;
            case UNCHANGED: return DiffProcessor.DiffType.UNCHANGED;
            default: return DiffProcessor.DiffType.UNCHANGED;
        }
    }
    
    private String getFormatExtension(DocxProcessor.OutputFormat format) {
        switch (format) {
            case MARKDOWN: return "md";
            case PLAIN_TEXT: return "txt";
            case HTML: default: return "html";
        }
    }
    
    // autoSortFiles wurde in loadDocxFiles integriert
    
    private EditorWindow openChapterEditorWindow(String text, DocxFile chapterFile, DocxProcessor.OutputFormat format) {
        try {
            logger.info("=== ÖFFNE EDITOR FENSTER START ===");
            

            
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/editor.fxml"));
            logger.info("=== FXML LOADER ERSTELLT ===");
            Parent root = loader.load();
            logger.info("=== FXML GELADEN ===");
            
            EditorWindow editorController = loader.getController();
            logger.info("=== EDITOR CONTROLLER ERHALTEN: " + (editorController != null ? "JA" : "NEIN") + " ===");
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
            File dataDir = new File(currentDirectory, "data");
            if (!dataDir.exists()) {
                dataDir.mkdirs();
            }
            File chapterFileRef = new File(dataDir, chapterName + fileExtension);
            editorController.setCurrentFile(chapterFileRef);
            
            // WICHTIG: Stelle sicher, dass die MD-Datei existiert
            editorController.ensureMdFileExists();
            
            // Übergebe den DocxProcessor für DOCX-Export
            editorController.setDocxProcessor(docxProcessor);
            
            // WICHTIG: Setze die originale DOCX-Datei für Rückkonvertierung
            editorController.setOriginalDocxFile(chapterFile.getFile());
            
            // WICHTIG: Setze das aktuelle Theme vom Hauptfenster auf das Editorfenster
            editorController.setThemeFromMainWindow(currentThemeIndex);
            
            // WICHTIG: Setze die Referenz zum MainController für Navigation
            editorController.setMainController(this);
            
            CustomStage editorStage = StageManager.createStage("Kapitel-Editor: " + chapterFile.getFileName());
            
            // NEU: Titelbalken mit Dateinamen setzen
            editorController.setWindowTitle("📄 " + chapterFile.getFileName());
            
            // NEU: Window-Preferences laden und anwenden
            loadEditorWindowProperties(editorStage);
            
            // Scene erstellen und mit CustomStage-Titelleiste setzen
            Scene scene = new Scene(root);
            editorStage.setSceneWithTitleBar(scene);
            
            // NEU: Mindestgrößen VOR loadEditorWindowProperties() setzen
            editorStage.setMinWidth(800);
            editorStage.setMinHeight(600);
            
            // NEU: Window-Preferences NACH setSceneWithTitleBar() laden und anwenden
            loadEditorWindowProperties(editorStage);
            
            // CSS mit ResourceManager laden
            String cssPath = ResourceManager.getCssResource("css/manuskript.css");
            if (cssPath != null) {
                scene.getStylesheets().add(cssPath);
            }
            
            // WICHTIG: Stage setzen für Close-Request-Handler
            editorController.setStage(editorStage);
            
            editorStage.show();
            
            return editorController;
            
        } catch (Exception e) {
            logger.error("Fehler beim Öffnen des Kapitel-Editor-Fensters", e);
            showError("Fenster-Fehler", e.getMessage());
            return null;
        }
    }
    
    private void processCompleteDocument(ObservableList<DocxFile> files) {
        // ALT: Gesamtdokument erstellen (wie bisher)
        try {
            updateStatus("Verarbeite " + files.size() + " Dateien...");
            
            // Nur noch MD-Format
            DocxProcessor.OutputFormat format = DocxProcessor.OutputFormat.MARKDOWN;
            
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
            
            // Erstelle das Gesamtdokument als echte Datei
            String currentDirectory = txtDirectoryPath.getText();
            File completeDocumentFile = new File(currentDirectory, baseFileName + " Gesamtdokument.md");
            
            try {
                // Schreibe das Gesamtdokument in die Datei
                Files.write(completeDocumentFile.toPath(), result.toString().getBytes(StandardCharsets.UTF_8));
                logger.info("Gesamtdokument erstellt: " + completeDocumentFile.getAbsolutePath());
                
                // Öffne den Editor mit der echten Datei
                openEditorWithFile(completeDocumentFile, true); // true = ist Gesamtdokument
                updateStatus(processed + " Dateien erfolgreich verarbeitet - Gesamtdokument erstellt: " + completeDocumentFile.getName());
                
            } catch (Exception e) {
                logger.error("Fehler beim Erstellen des Gesamtdokuments", e);
                showError("Fehler", "Konnte Gesamtdokument nicht erstellen: " + e.getMessage());
            }
            
        } catch (Exception e) {
            logger.error("Fehler bei der Verarbeitung", e);
            showError("Verarbeitungsfehler", e.getMessage());
            updateStatus("Fehler bei der Verarbeitung");
        }
    }
    
    private void openEditorWithFile(File file, boolean isCompleteDocument) {
        try {
            System.out.println("=== OPEN EDITOR WITH FILE ===");
            System.out.println("File: " + file.getAbsolutePath());
            System.out.println("isCompleteDocument: " + isCompleteDocument);
            System.out.println("File exists: " + file.exists());
            System.out.println("File size: " + file.length() + " bytes");
            
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/editor.fxml"));
            Parent root = loader.load();
            
            EditorWindow editorController = loader.getController();
            
            // Lade den Inhalt der Datei
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            System.out.println("Content loaded: " + content.length() + " characters");
            System.out.println("First 100 chars: " + content.substring(0, Math.min(100, content.length())));
            
            editorController.setText(content);
            
            // Setze die Datei-Informationen
            editorController.setCurrentFile(file);
            editorController.setIsCompleteDocument(isCompleteDocument);
            
            // Nur noch MD-Format
            DocxProcessor.OutputFormat currentFormat = DocxProcessor.OutputFormat.MARKDOWN;
            editorController.setOutputFormat(currentFormat);
            
            // Erstelle Stage
            Stage editorStage = new Stage();
            editorStage.setTitle("Editor - " + file.getName());
            editorStage.setScene(new Scene(root));
            editorStage.setWidth(1200);
            editorStage.setHeight(800);
            editorStage.setMinWidth(1000);
            editorStage.setMinHeight(700);
            editorStage.initModality(Modality.NONE);
            
            // Theme anwenden
            editorController.applyTheme(currentThemeIndex);
            
            // Zeige den Editor
            editorStage.show();
            
        } catch (Exception e) {
            logger.error("Fehler beim Öffnen des Editors", e);
            showError("Editor-Fehler", "Konnte Editor nicht öffnen: " + e.getMessage());
        }
    }
    
    private void openEditor(String text, String baseFileName) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/editor.fxml"));
            Parent root = loader.load();
            
            EditorWindow editorController = loader.getController();
            editorController.setText(text);
            
            // Nur noch MD-Format
            DocxProcessor.OutputFormat currentFormat = DocxProcessor.OutputFormat.MARKDOWN;
                editorController.setOutputFormat(currentFormat);
            
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
            File dataDir = new File(currentDirectory, "data");
            if (!dataDir.exists()) {
                dataDir.mkdirs();
            }
            File virtualFile = new File(dataDir, baseFileName + fileExtension);
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
            
            // WICHTIG: Setze die Referenz zum MainController für Navigation
            editorController.setMainController(this);
            
            // NEU: Titelbalken für Gesamtdokument setzen
            editorController.setWindowTitle("📚 Gesamtdokument: " + baseFileName);
            
            CustomStage editorStage = StageManager.createStage("Gesamtdokument: " + baseFileName);
            
            // Scene erstellen und mit CustomStage-Titelleiste setzen
            Scene scene = new Scene(root);
            editorStage.setSceneWithTitleBar(scene);
            
            // CSS mit ResourceManager laden
            String cssPath = ResourceManager.getCssResource("css/manuskript.css");
            if (cssPath != null) {
                scene.getStylesheets().add(cssPath);
            }
            
            // Fenster-Größe und Position (nur noch Breite und Höhe, Min-Werte sind bereits gesetzt)
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
        CustomAlert alert = new CustomAlert(Alert.AlertType.ERROR, title);
        alert.setContentText(message);
        // alert.setHeaderText(null); // ENTFERNT: Setzt 'null' String
        alert.applyTheme(currentThemeIndex);
        alert.initOwner(primaryStage);
        alert.showAndWait();
    }
    
    private void showWarning(String title, String message) {
        CustomAlert alert = new CustomAlert(Alert.AlertType.WARNING, title);
        alert.setContentText(message);
        // alert.setHeaderText(null); // ENTFERNT: Setzt 'null' String
        alert.applyTheme(currentThemeIndex);
        alert.initOwner(primaryStage);
        alert.showAndWait();
    }
    
    /**
     * Gibt den aktuellen Verzeichnispfad zurück
     */
    public String getCurrentDirectoryPath() {
        return txtDirectoryPath.getText();
    }
    
    /**
     * Setzt die Flag zum Unterdrücken des External Change Dialogs
     */
    public void setSuppressExternalChangeDialog(boolean suppress) {
        this.suppressExternalChangeDialog = suppress;
    }
    
    public void setPrimaryStage(CustomStage primaryStage) {
        this.primaryStage = primaryStage;
        
        // Hauptfenster-Properties laden und Event-Handler hinzufügen
        loadMainWindowProperties();
        
        // CustomStage Theme-Synchronisation
        if (primaryStage instanceof CustomStage) {
            CustomStage customStage = (CustomStage) primaryStage;
            // Theme aus Preferences laden und anwenden
            int savedTheme = preferences.getInt("main_window_theme", 0);
            customStage.setTitleBarTheme(savedTheme);
            currentThemeIndex = savedTheme;
        }
        
        // Stoppe WatchService beim Schließen und prüfe ob es das letzte Fenster ist
        primaryStage.setOnCloseRequest(event -> {
            // Keine Auswahl mehr speichern - MD-Erkennung ist ausreichend
            
            // Stoppe den File Watcher
            stopFileWatcher();
            
            // Prüfe ob noch andere Fenster offen sind
            boolean hasOtherWindows = false;
            for (Window window : Window.getWindows()) {
                if (window != primaryStage && window.isShowing()) {
                    hasOtherWindows = true;
                    break;
                }
            }
            
            // Wenn keine anderen Fenster offen sind, beende das Programm
            if (!hasOtherWindows) {
                logger.info("Letztes Fenster geschlossen - beende Programm");
                Platform.exit();
                System.exit(0);
            }
        });
    }
    
    // loadRecentRegexList entfernt - einfache Lösung
    
    // addToRecentRegex entfernt - einfache Lösung

    /**
     * Speichert die aktuelle Reihenfolge der ausgewählten Dateien
     */
    private void saveSelection(File directory) {
        try {
            if (directory == null || selectedDocxFiles.isEmpty()) return;
            
            File dataDir = new File(directory, "data");
            if (!dataDir.exists()) {
                dataDir.mkdirs();
            }
            
            // Extrahiere nur die Dateinamen in der aktuellen Reihenfolge
            List<String> fileNames = selectedDocxFiles.stream()
                .map(DocxFile::getFileName)
                .collect(Collectors.toList());
            
            // Als JSON speichern
            String json = new Gson().toJson(fileNames);
            Path jsonPath = dataDir.toPath().resolve(".manuskript_selection.json");
            Files.write(jsonPath, json.getBytes(StandardCharsets.UTF_8));
            
            logger.info("Reihenfolge gespeichert: {} Dateien", fileNames.size());
            
        } catch (Exception e) {
            logger.warn("Fehler beim Speichern der Reihenfolge", e);
        }
    }

    public TextField getTxtDirectoryPath() {
        return txtDirectoryPath;
    }
    
    /**
     * Gibt die Liste der ausgewählten DOCX-Dateien als File-Liste zurück
     */
    public List<File> getSelectedDocxFiles() {
        return selectedDocxFiles.stream()
            .map(DocxFile::getFile)
            .collect(Collectors.toList());
    }

    private void loadSavedOrder(File directory) {
        try {
            if (directory == null) return;
            File dataDir = new File(directory, "data");
            Path jsonPath = dataDir.toPath().resolve(".manuskript_selection.json");
            if (!Files.exists(jsonPath)) return;
            
            String json = new String(Files.readAllBytes(jsonPath));
            Type listType = new TypeToken<List<String>>(){}.getType();
            List<String> savedOrder = new Gson().fromJson(json, listType);
            
            // Stelle die gespeicherte Reihenfolge wieder her
            selectedDocxFiles.clear();
            
            // WICHTIG: Suche in ALLEN verfügbaren Dateien (originalDocxFiles)
            for (String fileName : savedOrder) {
                for (DocxFile docxFile : originalDocxFiles) {
                    if (docxFile.getFileName().equals(fileName)) {
                        // WICHTIG: Nur Dateien mit MD-Dateien zu selectedDocxFiles hinzufügen
                        File mdFile = deriveMdFileFor(docxFile.getFile());
                        boolean hasMdFile = mdFile != null && mdFile.exists();
                        
                        if (hasMdFile) {
                            // Datei hat MD-Datei → nach rechts (selectedDocxFiles)
                            selectedDocxFiles.add(docxFile);
                            logger.info("Gespeicherte Datei nach rechts: {} (hat MD-Datei)", docxFile.getFileName());
                        } else {
                            // Datei hat keine MD-Datei → nach links (allDocxFiles)
                            allDocxFiles.add(docxFile);
                            logger.info("Gespeicherte Datei nach links: {} (keine MD-Datei)", docxFile.getFileName());
                        }
                        
                        break;
                    }
                }
            }
            
            logger.info("Gespeicherte Reihenfolge geladen: {} Dateien", selectedDocxFiles.size());
            
        } catch (Exception e) {
            logger.warn("Fehler beim Laden der gespeicherten Reihenfolge", e);
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
        
        // CustomStage Theme aktualisieren
        if (primaryStage instanceof CustomStage) {
            CustomStage customStage = (CustomStage) primaryStage;
            customStage.setFullTheme(currentThemeIndex);
        }
        
        // WICHTIG: Alle anderen Stages aktualisieren
        StageManager.applyThemeToAllStages(currentThemeIndex);
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
        // Sortierung und Format-UI-Elemente entfernt - einfache Lösung
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
            
            // Pastell-Theme über CSS-Klasse anwenden
            if (themeIndex == 2) { // Pastell
                root.getStyleClass().add("pastell-theme");
                root.setStyle(""); // Inline Styles entfernen
                mainContainer.setStyle(""); // Inline Styles entfernen
                // Nur Border setzen - Rest über CSS
                Platform.runLater(() -> {
                    tableViewAvailable.setStyle("-fx-border-color: #ba68c8;");
                    tableViewSelected.setStyle("-fx-border-color: #ba68c8;");
                });
                logger.info("Pastell-Theme über CSS-Klasse angewendet");
                logger.info("DEBUG: Root StyleClasses: " + root.getStyleClass().toString());
                logger.info("DEBUG: MainContainer StyleClasses: " + mainContainer.getStyleClass().toString());
            } else {
                root.setStyle(""); // Style zurücksetzen
                mainContainer.setStyle(""); // Style zurücksetzen

                
                if (themeIndex == 0) { // Weiß - Eigene CSS-Klasse
                    root.getStyleClass().add("weiss-theme");
                } else if (themeIndex == 1) { // Schwarz
                    root.getStyleClass().add("theme-dark");
                } else if (themeIndex == 3) { // Blau
                    root.getStyleClass().add("theme-dark");
                    root.getStyleClass().add("blau-theme");
                } else if (themeIndex == 4) { // Grün
                    root.getStyleClass().add("theme-dark");
                    root.getStyleClass().add("gruen-theme");
                } else if (themeIndex == 5) { // Lila
                    root.getStyleClass().add("theme-dark");
                    root.getStyleClass().add("lila-theme");
                }
            }
            
            // Dann CSS laden - zusammengeführte CSS-Datei
            mainContainer.getScene().getStylesheets().clear();
            
            // Zusammengeführte CSS-Datei laden
            String cssPath = ResourceManager.getCssResource("css/manuskript.css");
            if (cssPath != null) {
                mainContainer.getScene().getStylesheets().add(cssPath);
                logger.info("CSS geladen: {}", cssPath);
            } else {
                logger.warn("CSS-Datei manuskript.css nicht gefunden!");
            }
            
            // TableViews über CSS stylen - nur Border setzen, Rest über CSS
            if (themeIndex != 2) { // Nicht für Pastell-Theme, das wird separat behandelt
                tableViewAvailable.setStyle("-fx-border-color: #ba68c8;");
                tableViewSelected.setStyle("-fx-border-color: #ba68c8;");
            }
            
            // TableViews auch Theme-Klassen geben
            applyThemeToNode(tableViewAvailable, themeIndex);
            applyThemeToNode(tableViewSelected, themeIndex);
            
            // WICHTIG: TableViews explizit aktualisieren für Theme-Änderungen
            Platform.runLater(() -> {
                tableViewAvailable.refresh();
                tableViewSelected.refresh();
            });
            
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
        
        // CustomStage Theme aktualisieren
        if (primaryStage instanceof CustomStage) {
            CustomStage customStage = (CustomStage) primaryStage;
            customStage.setFullTheme(currentThemeIndex);
        }
        
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
    private void loadMainWindowProperties() {
        if (primaryStage == null) {
            logger.warn("PrimaryStage ist null - kann Hauptfenster-Properties nicht laden");
            return;
        }
        
        // Fenster-Größe und Position laden
        double width = preferences.getDouble("main_window_width", 1400.0);
        double height = preferences.getDouble("main_window_height", 900.0);
        double x = preferences.getDouble("main_window_x", -1.0);
        double y = preferences.getDouble("main_window_y", -1.0);
        
        // Validierung der Fenster-Größe
        double minWidth = 1000.0;
        double minHeight = 600.0;
        double maxWidth = 3000.0;
        double maxHeight = 2000.0;
        
        // Größe validieren und korrigieren
        if (width < minWidth || width > maxWidth || Double.isNaN(width) || Double.isInfinite(width)) {
            logger.warn("Ungültige Hauptfenster-Breite: {} - verwende Standard: {}", width, minWidth);
            width = minWidth;
        }
        if (height < minHeight || height > maxHeight || Double.isNaN(height) || Double.isInfinite(height)) {
            logger.warn("Ungültige Hauptfenster-Höhe: {} - verwende Standard: {}", height, minHeight);
            height = minHeight;
        }
        
        // Fenster-Größe setzen
        primaryStage.setWidth(width);
        primaryStage.setHeight(height);
        
        // Validierung der Fenster-Position
        if (x >= 0 && y >= 0 && !Double.isNaN(x) && !Double.isNaN(y) && 
            !Double.isInfinite(x) && !Double.isInfinite(y)) {
            
            // Grobe Prüfung: Position sollte nicht zu weit außerhalb des Bildschirms sein
            if (x < -1000 || y < -1000 || x > 5000 || y > 5000) {
                logger.warn("Hauptfenster-Position außerhalb des Bildschirms: x={}, y={} - verwende zentriert", x, y);
                primaryStage.centerOnScreen();
            } else {
                primaryStage.setX(x);
                primaryStage.setY(y);
            }
        } else {
            logger.info("Keine gültige Hauptfenster-Position gefunden - zentriere Fenster");
            primaryStage.centerOnScreen();
        }
        
        // Event-Handler für Fenster-Änderungen hinzufügen
        primaryStage.widthProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(oldVal)) {
                preferences.putDouble("main_window_width", newVal.doubleValue());
            }
        });
        
        primaryStage.heightProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(oldVal)) {
                preferences.putDouble("main_window_height", newVal.doubleValue());
            }
        });
        
        primaryStage.xProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(oldVal)) {
                preferences.putDouble("main_window_x", newVal.doubleValue());
            }
        });
        
        primaryStage.yProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(oldVal)) {
                preferences.putDouble("main_window_y", newVal.doubleValue());
            }
        });
        
        logger.info("Hauptfenster-Eigenschaften geladen: Größe={}x{}, Position=({}, {})", width, height, x, y);
    }
    
    private void loadEditorWindowProperties(CustomStage editorStage) {
        // Fenster-Größe und Position mit robuster Validierung laden
        double width = PreferencesManager.getEditorWidth(preferences, "editor_window_width", PreferencesManager.DEFAULT_EDITOR_WIDTH);
        double height = PreferencesManager.getEditorHeight(preferences, "editor_window_height", PreferencesManager.DEFAULT_EDITOR_HEIGHT);
        double x = PreferencesManager.getWindowPosition(preferences, "editor_window_x", -1.0);
        double y = PreferencesManager.getWindowPosition(preferences, "editor_window_y", -1.0);
        
        // Mindestgrößen für Editor-Fenster
        double minWidth = PreferencesManager.MIN_EDITOR_WIDTH;
        double minHeight = PreferencesManager.MIN_EDITOR_HEIGHT;
        
        // Mindestgröße für CustomStage setzen
        editorStage.setMinWidth(minWidth);
        editorStage.setMinHeight(minHeight);
        
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
                PreferencesManager.putEditorWidth(preferences, "editor_window_width", newVal.doubleValue());
            }
        });
        
        editorStage.heightProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(oldVal)) {
                PreferencesManager.putEditorHeight(preferences, "editor_window_height", newVal.doubleValue());
            }
        });
        
        editorStage.xProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(oldVal)) {
                PreferencesManager.putWindowPosition(preferences, "editor_window_x", newVal.doubleValue());
            }
        });
        
        editorStage.yProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(oldVal)) {
                PreferencesManager.putWindowPosition(preferences, "editor_window_y", newVal.doubleValue());
            }
        });
        
        logger.info("Editor-Fenster-Eigenschaften geladen: Größe={}x{}, Position=({}, {})", width, height, x, y);
    }
    
    /**
     * Setzt alle Editor-Fenster-Preferences auf Standardwerte zurück
     */
    public void resetEditorWindowPreferences() {
        PreferencesManager.resetEditorWindowPreferences(preferences);
    }
    
    /**
     * Zeigt Dialog für existierende .gesamt Dateien
     */
    private void showGesamtFileDialog(File[] existingGesamtFiles, ObservableList<DocxFile> selectedDocxFiles) {
        CustomAlert alert = new CustomAlert(Alert.AlertType.CONFIRMATION, "Gesamtdokument existiert bereits");
        alert.setHeaderText("Es wurde bereits ein Gesamtdokument erstellt:");
        alert.setContentText("Möchten Sie das existierende Dokument laden oder ein neues erstellen?");
        
        // Theme anwenden
        alert.applyTheme(currentThemeIndex);
        
        // Owner setzen
        alert.initOwner(primaryStage);
        
        ButtonType loadExistingButton = new ButtonType("Existierende laden");
        ButtonType createNewButton = new ButtonType("Neues erstellen");
        ButtonType cancelButton = new ButtonType("Abbrechen", ButtonBar.ButtonData.CANCEL_CLOSE);
        
        alert.setButtonTypes(loadExistingButton, createNewButton, cancelButton);
        
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
            
            // Öffne den Spezialeditor für Gesamtdokumente
            openEditorWithFile(gesamtFile, true);
            updateStatus("Gesamtdokument geladen: " + gesamtFile.getName());
            
        } catch (Exception e) {
            logger.error("Fehler beim Laden der .gesamt Datei", e);
            showError("Ladefehler", "Fehler beim Laden der Datei: " + e.getMessage());
            updateStatus("Fehler beim Laden der .gesamt Datei");
        }
    }
    
    /**
     * Test-Methode für CustomAlert
     */
        private void testCustomAlert() {
        CustomAlert alert = new CustomAlert(Alert.AlertType.INFORMATION, "Dies ist ein Test-Content für die neue CustomAlert-Klasse mit eigener Titelzeile, Gradient-Hintergrund und vollständiger Theme-Integration.");

        // Titel und Header setzen
        alert.setTitle("Test CustomAlert");
        alert.setHeaderText("Dies ist ein Test");

        // Button-Typen setzen
        alert.setButtonTypes(ButtonType.OK, ButtonType.CANCEL);

        // Theme anwenden (aktuelles Theme)
        alert.applyTheme(currentThemeIndex);

        // Owner setzen
        alert.initOwner(primaryStage);

        // Alert anzeigen
        Optional<ButtonType> result = alert.showAndWait();

        if (result.isPresent()) {
            if (result.get() == ButtonType.OK) {
                logger.info("CustomAlert: OK gedrückt");
            } else {
                logger.info("CustomAlert: Abgebrochen");
            }
        }
    }
    
    /**
     * Erstellt ein neues Kapitel mit Dialog für Kapitelname
     */
    private void createNewChapter() {
        // Dialog für Kapitelname anzeigen
        CustomAlert alert = new CustomAlert(Alert.AlertType.CONFIRMATION, "Neues Kapitel erstellen");
        alert.setHeaderText("Geben Sie den Namen für das neue Kapitel ein:");
        
        // TextField für Kapitelname
        TextField chapterNameField = new TextField();
        chapterNameField.setPromptText("z.B. Kapitel 1, Einleitung, etc.");
        chapterNameField.setPrefWidth(300);
        
        VBox content = new VBox(10);
        content.getChildren().add(chapterNameField);
        content.setPadding(new Insets(10));
        
        alert.setCustomContent(content);
        alert.applyTheme(currentThemeIndex);
        alert.initOwner(primaryStage);
        
        ButtonType createButton = new ButtonType("Erstellen");
        ButtonType cancelButton = new ButtonType("Abbrechen");
        alert.setButtonTypes(createButton, cancelButton);
        
        Optional<ButtonType> result = alert.showAndWait();
        
        if (result.isPresent() && result.get() == createButton) {
            String chapterName = chapterNameField.getText().trim();
            if (chapterName.isEmpty()) {
                showError("Fehler", "Bitte geben Sie einen Kapitelnamen ein.");
                return;
            }
            
            // Prüfe ob Verzeichnis ausgewählt ist
            String directoryPath = txtDirectoryPath.getText();
            if (directoryPath == null || directoryPath.trim().isEmpty()) {
                showError("Fehler", "Bitte wählen Sie zuerst ein Verzeichnis aus.");
                return;
            }
            
            try {
                File docxFile = createChapterFiles(directoryPath, chapterName);
                
                // Füge das neue Kapitel automatisch zu den ausgewählten Dateien hinzu
                DocxFile newDocxFile = new DocxFile(docxFile);
                
                // Erstelle Hash für das neue Kapitel (damit es als "unverändert" markiert wird)
                try {
                    String hash = calculateFileHash(docxFile);
                    if (hash != null) {
                        saveDocxHash(docxFile, hash);
                        logger.info("Hash für neues Kapitel erstellt: {}", hash);
                    }
                } catch (Exception hashException) {
                    logger.warn("Konnte Hash für neues Kapitel nicht erstellen: {}", hashException.getMessage());
                }
                
                selectedDocxFiles.add(newDocxFile);
                updateStatus("Neues Kapitel '" + chapterName + "' erstellt und zur Bearbeitung hinzugefügt");
                
                // Speichere die neue Reihenfolge (damit das Kapitel beim Neustart erhalten bleibt)
                File directory = new File(directoryPath);
                saveSelection(directory);
                
                // Aktualisiere die Dateiliste (neues Kapitel wird NICHT in linker Liste erscheinen, da es bereits in rechter Liste ist)
                refreshDocxFiles();
            } catch (Exception e) {
                logger.error("Fehler beim Erstellen des neuen Kapitels", e);
                showError("Fehler", "Fehler beim Erstellen des Kapitels: " + e.getMessage());
            }
        }
    }
    
    /**
     * Erstellt DOCX und MD Dateien für ein neues Kapitel
     * @return Die erstellte DOCX-Datei
     */
    private File createChapterFiles(String directoryPath, String chapterName) throws Exception {
        File directory = new File(directoryPath);
        if (!directory.exists() || !directory.isDirectory()) {
            throw new Exception("Verzeichnis existiert nicht: " + directoryPath);
        }
        
        // Bereinige Kapitelname (entferne ungültige Zeichen)
        String cleanChapterName = chapterName.replaceAll("[<>:\"/\\\\|?*]", "_");
        
        // Erstelle DOCX-Datei
        File docxFile = new File(directory, cleanChapterName + ".docx");
        if (docxFile.exists()) {
            throw new Exception("Datei existiert bereits: " + docxFile.getName());
        }
        
        // Erstelle leere DOCX-Datei mit DocxProcessor
        if (docxProcessor == null) {
            docxProcessor = new DocxProcessor();
        }
        
        // Erstelle leeren Inhalt
        String emptyContent = "# " + chapterName + "\n\n";
        
        // Erstelle DOCX mit globalen Optionen
        DocxOptions options = new DocxOptions();
        options.loadFromPreferences(); // Lade gespeicherte Optionen
        docxProcessor.exportMarkdownToDocxWithOptions(emptyContent, docxFile, options);
        
        // Erstelle MD-Datei
        File mdFile = new File(directory, cleanChapterName + ".md");
        if (mdFile.exists()) {
            throw new Exception("Datei existiert bereits: " + mdFile.getName());
        }
        
        // Schreibe leeren Inhalt in MD-Datei
        java.nio.file.Files.write(mdFile.toPath(), emptyContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        
        logger.info("Neues Kapitel erstellt: {} (DOCX: {}, MD: {})", chapterName, docxFile.getName(), mdFile.getName());
        
        return docxFile; // DOCX-Datei zurückgeben
    }
    
    /**
     * Öffnet die Split-Stage (nach korrektem Muster wie Makros)
     */
    private void openSplitStage() {
        try {
            CustomStage splitStage = StageManager.createStage("Kapitel-Split");
            splitStage.setTitle("Kapitel-Split");
            splitStage.setWidth(1000);
            splitStage.setHeight(600);
            splitStage.initModality(Modality.NONE);
            splitStage.initOwner(primaryStage);
            
            // Split-Panel programmatisch erstellen (wie Makros!)
            VBox splitPanel = createSplitPanel();
            
            Scene splitScene = new Scene(splitPanel);
            // CSS mit ResourceManager laden (manuskript.css für alle Themes)
            String manuskriptCss = ResourceManager.getCssResource("css/manuskript.css");
            if (manuskriptCss != null) {
                splitScene.getStylesheets().add(manuskriptCss);
                logger.info("CSS geladen: {}", manuskriptCss);
            } else {
                logger.error("CSS konnte nicht geladen werden!");
            }
            splitStage.setSceneWithTitleBar(splitScene);
            
            splitStage.centerOnScreen();
            splitStage.show();
            
            logger.info("Split-Stage erfolgreich geöffnet");
            
        } catch (Exception e) {
            logger.error("Fehler beim Öffnen der Split-Stage: {}", e.getMessage(), e);
            showError("Fehler", "Konnte Split-Stage nicht öffnen: " + e.getMessage());
        }
    }
    
    /**
     * Erstellt das Split-Panel programmatisch (wie createMacroPanel)
     */
    private VBox createSplitPanel() {
        VBox splitPanel = new VBox(15);
        splitPanel.getStyleClass().add("split-panel");
        
        // CSS-Klassen verwenden statt inline Styles
        splitPanel.setStyle(""); // CSS-Klassen verwenden
        
        // Theme-Klassen für das Split-Panel hinzufügen
        if (currentThemeIndex == 0) { // Weiß-Theme
            splitPanel.getStyleClass().add("weiss-theme");
        } else if (currentThemeIndex == 1) { // Schwarz-Theme
            splitPanel.getStyleClass().add("theme-dark");
        } else if (currentThemeIndex == 2) { // Pastell-Theme
            splitPanel.getStyleClass().add("pastell-theme");
        } else if (currentThemeIndex >= 3) { // Dunkle Themes: Blau (3), Grün (4), Lila (5)
            splitPanel.getStyleClass().add("theme-dark");
            if (currentThemeIndex == 3) {
                splitPanel.getStyleClass().add("blau-theme");
            } else if (currentThemeIndex == 4) {
                splitPanel.getStyleClass().add("gruen-theme");
            } else if (currentThemeIndex == 5) {
                splitPanel.getStyleClass().add("lila-theme");
            }
        }
        
        splitPanel.setPadding(new Insets(20));
        
        // Header
        VBox headerBox = new VBox(5);
        Label headerLabel = new Label("Kapitel-Split");
        headerLabel.getStyleClass().add("section-header");
        Label infoLabel = new Label("Wähle eine DOCX-Datei aus, die mehrere Kapitel (ein ganzer Roman?) enthält und teile sie in einzelne DOCX-Dateien auf, die jeweils ein Kapitel enthalten.");
        infoLabel.getStyleClass().add("info-text");
        headerBox.getChildren().addAll(headerLabel, infoLabel);
        
        // Datei-Auswahl
        VBox fileBox = new VBox(10);
        Label fileLabel = new Label("1. Datei auswählen:");
        fileLabel.getStyleClass().add("section-title");
        
        HBox fileHBox = new HBox(10);
        fileHBox.setAlignment(Pos.CENTER_LEFT);
        
        final TextField txtFilePath = new TextField();
        txtFilePath.setPromptText("DOCX oder TXT-Datei auswählen...");
        txtFilePath.setPrefWidth(400);
        txtFilePath.setEditable(false);
        
        Button btnSelectFile = new Button("Datei auswählen");
        btnSelectFile.getStyleClass().addAll("button", "primary");
        
        fileHBox.getChildren().addAll(txtFilePath, btnSelectFile);
        fileBox.getChildren().addAll(fileLabel, fileHBox);
        
        // Ausgabe-Verzeichnis
        VBox outputBox = new VBox(10);
        Label outputLabel = new Label("2. Ausgabe-Verzeichnis:");
        outputLabel.getStyleClass().add("section-title");
        
        HBox outputHBox = new HBox(10);
        outputHBox.setAlignment(Pos.CENTER_LEFT);
        
        final TextField txtOutputPath = new TextField();
        txtOutputPath.setPromptText("Verzeichnis für Kapitel-Dateien...");
        txtOutputPath.setPrefWidth(400);
        txtOutputPath.setEditable(false);
        
        Button btnSelectOutput = new Button("Verzeichnis auswählen");
        btnSelectOutput.getStyleClass().addAll("button", "secondary");
        
        outputHBox.getChildren().addAll(txtOutputPath, btnSelectOutput);
        outputBox.getChildren().addAll(outputLabel, outputHBox);
        
        // Kapitel-Liste
        VBox listBox = new VBox(10);
        VBox.setVgrow(listBox, Priority.ALWAYS);
        Label listLabel = new Label("3. Gefundene Kapitel:");
        listLabel.getStyleClass().add("section-title");
        
        final ListView<CheckBox> listChapters = new ListView<>();
        listChapters.setPrefHeight(300);
        listChapters.getStyleClass().add("alternating-list"); // CSS-Klasse für alternierende Zeilen
        
        // Theme-Klassen auch auf die ListView anwenden
        if (currentThemeIndex == 0) { // Weiß-Theme
            listChapters.getStyleClass().add("weiss-theme");
        } else if (currentThemeIndex == 1) { // Schwarz-Theme
            listChapters.getStyleClass().add("theme-dark");
        } else if (currentThemeIndex == 2) { // Pastell-Theme
            listChapters.getStyleClass().add("pastell-theme");
        } else if (currentThemeIndex >= 3) { // Dunkle Themes: Blau (3), Grün (4), Lila (5)
            listChapters.getStyleClass().add("theme-dark");
            if (currentThemeIndex == 3) {
                listChapters.getStyleClass().add("blau-theme");
            } else if (currentThemeIndex == 4) {
                listChapters.getStyleClass().add("gruen-theme");
            } else if (currentThemeIndex == 5) {
                listChapters.getStyleClass().add("lila-theme");
            }
        }
        
        CheckBox noFileCheckBox = new CheckBox("Keine Datei ausgewählt");
        noFileCheckBox.setDisable(true);
        listChapters.getItems().add(noFileCheckBox);
        
        listBox.getChildren().addAll(listLabel, listChapters);
        
        // Buttons
        HBox buttonBox = new HBox(15);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        
        Region spacer = new Region();
        spacer.setStyle("-fx-background-color: transparent;");
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Button btnSplit = new Button("Kapitel aufteilen");
        btnSplit.getStyleClass().addAll("button", "success");
        btnSplit.setPrefWidth(150);
        btnSplit.setDisable(true);
        
        Button btnClose = new Button("Schließen");
        btnClose.getStyleClass().addAll("button", "danger");
        btnClose.setPrefWidth(100);
        
        buttonBox.getChildren().addAll(spacer, btnSplit, btnClose);
        
        // Event-Handler
        btnSelectFile.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("DOCX-Datei auswählen");
            fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("DOCX-Dateien", "*.docx"),
                new FileChooser.ExtensionFilter("Alle Dateien", "*.*")
            );
            
            // Letzten Pfad wiederherstellen
            String lastPath = ResourceManager.getLastFilePath();
            if (lastPath != null && !lastPath.isEmpty()) {
                File lastDir = new File(lastPath);
                if (lastDir.exists() && lastDir.isDirectory()) {
                    fileChooser.setInitialDirectory(lastDir);
                }
            }
            
            File selectedFile = fileChooser.showOpenDialog(splitPanel.getScene().getWindow());
            if (selectedFile != null) {
                txtFilePath.setText(selectedFile.getAbsolutePath());
                
                // Pfad für nächste Verwendung speichern
                ResourceManager.setLastFilePath(selectedFile.getParent());
                
                // DOCX-Datei analysieren und Kapitel extrahieren
                try {
                    DocxSplitProcessor processor = new DocxSplitProcessor();
                    List<Chapter> chapters = processor.analyzeDocument(selectedFile);
                    
                    listChapters.getItems().clear();
                    if (chapters.isEmpty()) {
                        CheckBox noChaptersCheckBox = new CheckBox("Keine Kapitel gefunden");
                        noChaptersCheckBox.setDisable(true);
                        listChapters.getItems().add(noChaptersCheckBox);
                        btnSplit.setDisable(true);
                    } else {
                        for (Chapter chapter : chapters) {
                            CheckBox chapterCheckBox = new CheckBox(chapter.toString());
                            chapterCheckBox.setSelected(true); // Standardmäßig ausgewählt
                            chapterCheckBox.setUserData(chapter); // Kapitel-Objekt speichern
                            listChapters.getItems().add(chapterCheckBox);
                        }
                        btnSplit.setDisable(false);
                        logger.info("{} Kapitel in DOCX-Datei gefunden", chapters.size());
                    }
                } catch (Exception ex) {
                    logger.error("Fehler beim Analysieren der DOCX-Datei: {}", ex.getMessage(), ex);
                    listChapters.getItems().clear();
                    CheckBox errorCheckBox = new CheckBox("Fehler beim Lesen der Datei: " + ex.getMessage());
                    errorCheckBox.setDisable(true);
                    listChapters.getItems().add(errorCheckBox);
                    btnSplit.setDisable(true);
                }
            }
        });
        
        btnSelectOutput.setOnAction(e -> {
            DirectoryChooser dirChooser = new DirectoryChooser();
            dirChooser.setTitle("Ausgabe-Verzeichnis auswählen");
            
            // Letzten Pfad wiederherstellen
            String lastPath = ResourceManager.getLastOutputPath();
            if (lastPath != null && !lastPath.isEmpty()) {
                File lastDir = new File(lastPath);
                if (lastDir.exists() && lastDir.isDirectory()) {
                    dirChooser.setInitialDirectory(lastDir);
                }
            }
            
            File selectedDir = dirChooser.showDialog(splitPanel.getScene().getWindow());
            if (selectedDir != null) {
                txtOutputPath.setText(selectedDir.getAbsolutePath());
                
                // Pfad für nächste Verwendung speichern
                ResourceManager.setLastOutputPath(selectedDir.getAbsolutePath());
            }
        });
        
        btnSplit.setOnAction(e -> {
            // Kapitel aufteilen und speichern
            try {
                String filePath = txtFilePath.getText();
                String outputPath = txtOutputPath.getText();
                
                if (filePath.isEmpty() || outputPath.isEmpty()) {
                    showError("Fehler", "Bitte wähle eine Datei und ein Ausgabe-Verzeichnis aus.");
                    return;
                }
                
                File docxFile = new File(filePath);
                File outputDir = new File(outputPath);
                
                if (!docxFile.exists()) {
                    showError("Fehler", "Die ausgewählte Datei existiert nicht.");
                    return;
                }
                
                // Split-Button deaktivieren während der Verarbeitung
                btnSplit.setDisable(true);
                btnSplit.setText("Verarbeite...");
                
                // Alle Kapitel mit Auswahl-Status sammeln
                List<Chapter> allChapters = new ArrayList<>();
                List<Boolean> selectionStatus = new ArrayList<>();
                
                for (CheckBox checkBox : listChapters.getItems()) {
                    if (checkBox.getUserData() instanceof Chapter) {
                        allChapters.add((Chapter) checkBox.getUserData());
                        selectionStatus.add(checkBox.isSelected());
                    }
                }
                
                if (allChapters.isEmpty()) {
                    showError("Fehler", "Keine Kapitel zum Verarbeiten gefunden.");
                    btnSplit.setDisable(false);
                    btnSplit.setText("Kapitel aufteilen");
                    return;
                }
                
                // DOCX-Split in separatem Thread ausführen (mit Text-Zusammenführung)
                new Thread(() -> {
                    try {
                        DocxSplitProcessor processor = new DocxSplitProcessor();
                        
                        // Ausgabe-Verzeichnis erstellen falls nicht vorhanden
                        if (!outputDir.exists()) {
                            outputDir.mkdirs();
                        }
                        
                        // Basis-Dateiname (ohne .docx)
                        String baseFileName = docxFile.getName().replaceFirst("\\.docx$", "");
                        
                        // Kapitel mit nicht-ausgewählten Inhalten zusammenführen
                        List<Chapter> mergedChapters = processor.mergeChaptersWithUnselectedContent(allChapters, selectionStatus);
                        
                        // Zusammengeführte Kapitel speichern
                        for (Chapter chapter : mergedChapters) {
                            processor.saveChapter(chapter, outputDir, baseFileName);
                        }
                        
                        // UI-Update im JavaFX-Thread
                        Platform.runLater(() -> {
                            btnSplit.setDisable(false);
                            btnSplit.setText("Kapitel aufteilen");
                            showError("Erfolg", String.format("%d finale Kapitel wurden erfolgreich aufgeteilt und gespeichert!", mergedChapters.size()));
                        });
                        
                    } catch (Exception ex) {
                        logger.error("Fehler beim DOCX-Split: {}", ex.getMessage(), ex);
                        
                        // UI-Update im JavaFX-Thread
                        Platform.runLater(() -> {
                            btnSplit.setDisable(false);
                            btnSplit.setText("Kapitel aufteilen");
                            showError("Fehler", "Fehler beim Aufteilen der Kapitel: " + ex.getMessage());
                        });
                    }
                }).start();
                
            } catch (Exception ex) {
                logger.error("Fehler beim Starten des DOCX-Splits: {}", ex.getMessage(), ex);
                showError("Fehler", "Fehler beim Starten des Splits: " + ex.getMessage());
            }
        });
        
        btnClose.setOnAction(e -> {
            // Stage schließen
            for (Window window : Window.getWindows()) {
                if (window instanceof CustomStage) {
                    CustomStage stage = (CustomStage) window;
                    if ("Kapitel-Split".equals(stage.getTitle())) {
                        stage.close();
                        break;
                    }
                }
            }
        });
        
        // Alles zusammen
        splitPanel.getChildren().addAll(headerBox, fileBox, outputBox, listBox, buttonBox);
        
        return splitPanel;
    }
    
} 