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
import javafx.scene.control.Alert.AlertType;
import javafx.scene.Cursor;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.MouseButton;
import javafx.event.Event;
import javafx.scene.input.DragEvent;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.stage.DirectoryChooser;
import javafx.stage.Screen;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.ListCell;
import javafx.scene.control.SplitPane;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.stage.Modality;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import javafx.scene.web.WebView;
import javafx.application.Platform;
import javafx.scene.image.ImageView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Optional;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.ResourceBundle;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.prefs.Preferences;
import com.manuskript.HelpSystem;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import com.manuskript.DocxSplitProcessor;
import com.manuskript.DocxSplitProcessor.Chapter;
import com.manuskript.RtfSplitProcessor;
import java.util.LinkedHashMap;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;

public class MainController implements Initializable {
    
    private static final Logger logger = LoggerFactory.getLogger(MainController.class);
    
    // Liste der gesperrten Dateien mit Zeitstempel
    private static final Map<String, Long> lockedFiles = new ConcurrentHashMap<>();
    private static final long LOCK_TIMEOUT = 1 * 60 * 1000; // 5 Minuten
    
    @FXML private BorderPane mainContainer;
    private ImageView coverImageView;
    @FXML private Button btnSelectDirectory;
    @FXML private TextField txtDirectoryPath;
    private Label projectTitleLabel;
    
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
    @FXML private Button btnDeleteFile;
    @FXML private CheckBox chkDownloadsMonitor;
    // Help-Toggle Button (programmatisch erstellt)
    private Button btnHelpToggle;
    
    // Status
    // ProgressBar und lblStatus wurden entfernt
    
    private CustomStage primaryStage;
    private FlowPane currentProjectFlow;
    private CustomStage currentProjectStage;
    private ObservableList<DocxFile> allDocxFiles = FXCollections.observableArrayList();
    private ObservableList<DocxFile> originalDocxFiles = FXCollections.observableArrayList(); // Ursprüngliche Reihenfolge
    private ObservableList<DocxFile> selectedDocxFiles = FXCollections.observableArrayList();
    private List<ProjectDisplayItem> projectItems = new ArrayList<>();
    private ProjectDisplayItem draggingProjectItem;
    private File draggingSeriesBook;
    private File projectRootDirectory;
    // SortedList entfernt - einfache Lösung
    private DocxProcessor docxProcessor;
    private Preferences preferences;
    private java.nio.file.WatchService watchService;
    private Thread watchThread;
    private volatile boolean watchRunning = false;
    private volatile boolean suppressExternalChangeDialog = false;
    
    // Map zur Verfolgung geöffneter Editoren
    private static final Map<String, EditorWindow> openEditors = new HashMap<>();
    
    // Downloads-Monitor
    private Timer downloadsMonitorTimer;
    private File downloadsDirectory;
    private File backupDirectory;
    private AtomicBoolean isMonitoring = new AtomicBoolean(false);
    
    // Theme-System
    private int currentThemeIndex = 0;
    
    // Initialisierungs-Flag
    private boolean isInitializing = false;
    
    // Help-System
    private boolean helpEnabled = true;
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
        
        // Migration von parameters.properties zu User Preferences
        ResourceManager.migrateParametersToPreferences();
        
        // Standardwerte wiederherstellen, falls Parameter fehlen
        ResourceManager.restoreDefaultParameters();
        
        // Flag setzen, dass Initialisierung läuft
        isInitializing = true;
        
        setupUI();
        setupEventHandlers();
        setupDragAndDrop();
        docxProcessor = new DocxProcessor();
        
        // loadLastDirectory() nur aufrufen, wenn nicht initialisiert wird
        if (!isInitializing) {
            loadLastDirectory();
        }
        
        loadDownloadsMonitorSettings();
        
        // Alte Hilfe-Dateien bereinigen
        HelpSystem.cleanupOldHelpFiles();
        
        // Help-Einstellungen laden
        loadHelpSettings();
        
        // Erstelle ImageView programmatisch und füge es direkt hinzu
        coverImageView = new ImageView();
        coverImageView.setFitWidth(200);
        coverImageView.setFitHeight(250);
        coverImageView.setPreserveRatio(true);
        coverImageView.setOpacity(1.0);
        coverImageView.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 15, 0, 0, 0);");
        
        // Label für Projekttitel vorbereiten
        if (projectTitleLabel == null) {
            projectTitleLabel = new Label("");
            projectTitleLabel.getStyleClass().add("project-name");
            projectTitleLabel.setAlignment(Pos.CENTER);
            projectTitleLabel.setMaxWidth(Double.MAX_VALUE);
            projectTitleLabel.setWrapText(false);
        }
        
        // Erstelle Zurück-Button mit Pfeil-Symbol
        Button backButton = new Button("← Zurück");
        backButton.setId("backButton");
        backButton.setPrefSize(120, 40);
        backButton.getStyleClass().add("back-button");
        
        // Zurück-Funktionalität: Hauptfenster nicht ausblenden
        backButton.setOnAction(e -> {
            showProjectSelectionMenu();
        });
        
        
        // Erstelle BorderPane: Button links, Bild center, Dummy rechts
        BorderPane imageContainer = new BorderPane();
        imageContainer.setPrefHeight(300); // Noch größere Höhe für den Container-Bereich
        
        // Button links
        imageContainer.setLeft(backButton);
        
        // Bild in der Mitte mit Projekttitel darüber
        VBox centerBox = new VBox(5);
        centerBox.setAlignment(Pos.CENTER);
        centerBox.getChildren().addAll(projectTitleLabel, coverImageView);
        imageContainer.setCenter(centerBox);
        
        // Dummy rechts (genauso breit wie Button)
        HBox dummyBox = new HBox();
        dummyBox.setPrefWidth(120); // Gleiche Breite wie der Button
        dummyBox.setMinWidth(120);
        dummyBox.setMaxWidth(120);
        imageContainer.setRight(dummyBox);
        
        // Feste Größe für das Cover-Bild (keine Property-Bindings)
        // coverImageView.fitWidthProperty().bind(imageContainer.widthProperty().subtract(40));
        // coverImageView.fitHeightProperty().bind(imageContainer.heightProperty().subtract(40));
        
        // Wichtig: Container muss wachsen können!
        imageContainer.setMaxHeight(Double.MAX_VALUE);
        imageContainer.setMinHeight(300);
        
        // Füge ImageView-Container zur BorderPane hinzu
        if (mainContainer instanceof BorderPane) {
            ((BorderPane) mainContainer).setTop(imageContainer);
        }
        
        
        // Debug: Prüfe ob ImageView korrekt erstellt wurde
        if (coverImageView != null) {
            
            // Prüfe beim Start, ob Root-Verzeichnis konfiguriert ist (nur während Initialisierung)
            if (isInitializing) {
                String rootDir = ResourceManager.getParameter("project.root.directory", "");
                if (rootDir == null || rootDir.trim().isEmpty()) {
                    // Root-Verzeichnis nicht gesetzt - Benutzer fragen
                    logger.debug("Root-Verzeichnis nicht gesetzt - zeige Welcome Screen");
                    showRootDirectoryChooser();
                    
                    // Nach dem Dialog automatisch Projektauswahl öffnen
                    Platform.runLater(() -> {
                        primaryStage.hide();
                        showProjectSelectionMenu();
                    });
                } else {
                    // Root-Verzeichnis ist gesetzt - prüfe ob es existiert
                    File rootDirFile = new File(rootDir);
                    if (!rootDirFile.exists()) {
                        logger.debug("Root-Verzeichnis existiert nicht: " + rootDir + " - zeige Welcome Screen");
                        showRootDirectoryChooser();
                        
                        // Nach dem Dialog automatisch Projektauswahl öffnen
                        Platform.runLater(() -> {
                            primaryStage.hide();
                            showProjectSelectionMenu();
                        });
                    } else {
                        logger.debug("Root-Verzeichnis gefunden: " + rootDir);
                    }
                }
                
                // Initialisierung abgeschlossen
                isInitializing = false;
                
                // Jetzt loadLastDirectory() aufrufen, falls kein Welcome Screen gezeigt wurde
                if (rootDir != null && !rootDir.trim().isEmpty()) {
                    File rootDirFile = new File(rootDir);
                    if (rootDirFile.exists()) {
                        // Root-Verzeichnis ist korrekt - lade letztes Verzeichnis
                        loadLastDirectory();
                        
                        // Nach loadLastDirectory() das Cover-Bild laden
                        loadCoverImageFromCurrentDirectory();
                    }
                }
            }
            
            // Prüfe beim Start, ob ein cover_image.png im aktuellen Verzeichnis vorhanden ist
            // WICHTIG: Nur aufrufen, wenn txtDirectoryPath bereits gesetzt ist
            if (txtDirectoryPath.getText() != null && !txtDirectoryPath.getText().trim().isEmpty()) {
                loadCoverImageFromCurrentDirectory();
            }
            
            // Erstelle das Layout basierend auf dem Bildstatus
            if (mainContainer instanceof BorderPane) {
                if (coverImageView.getImage() == null) {
                    // Kein Bild - zeige nur Zurück-Button (kleine Höhe)
                    BorderPane startImageContainer = new BorderPane();
                    startImageContainer.setPrefHeight(60); // Nur so hoch wie der Button
                    
                    // Erstelle Zurück-Button
                    Button startBackButton = new Button("← Zurück1");
                    startBackButton.setId("backButton");
                    startBackButton.setPrefSize(120, 40);
                    startBackButton.getStyleClass().add("back-button");
                    startBackButton.setOnAction(e -> {
                        showProjectSelectionMenu();
                    });
                    
                    startImageContainer.setLeft(startBackButton);
                    startImageContainer.setCenter(null); // Kein Bild
                    HBox startDummyBox = new HBox();
                    startDummyBox.setPrefWidth(120);
                    startImageContainer.setRight(startDummyBox);
                    
                    ((BorderPane) mainContainer).setTop(startImageContainer);
                } else {
                    // Bild vorhanden - zeige normales Layout
                    BorderPane startImageContainer = new BorderPane();
                    startImageContainer.setPrefHeight(300);
                    
                    // Erstelle Zurück-Button
                    Button startBackButton = new Button("← Projektauswahl");
                    startBackButton.setId("backButton");
                    startBackButton.setPrefSize(150, 40);
                    startBackButton.getStyleClass().add("back-button");
                    startBackButton.setOnAction(e -> {
                        showProjectSelectionMenu();
                    });
                    
                    // HBox für Abstand um den Button
                    HBox buttonContainer = new HBox();
                    buttonContainer.setPadding(new Insets(10, 10, 10, 10)); // Abstand um den Button
                    buttonContainer.getChildren().add(startBackButton);
                    
                    startImageContainer.setLeft(buttonContainer);
                    // Bild mit Projekttitel darüber
                    VBox startCenterBox = new VBox(5);
                    startCenterBox.setAlignment(Pos.CENTER);
                    startCenterBox.getChildren().addAll(projectTitleLabel, coverImageView);
                    startImageContainer.setCenter(startCenterBox);
                    HBox startDummyBox = new HBox();
                    startDummyBox.setPrefWidth(120);
                    startImageContainer.setRight(startDummyBox);
                    
                    ((BorderPane) mainContainer).setTop(startImageContainer);
                }
            }
        } else {
            logger.error("ImageView konnte nicht erstellt werden!");
        }
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
        
        // Sortierung für rechte Tabelle deaktivieren
        tableViewSelected.setSortPolicy(null);
        colFileNameSelected.setSortable(false);
        colFileSizeSelected.setSortable(false);
        colLastModifiedSelected.setSortable(false);
        
        // Tabelle fokussierbar machen für KeyEvents, aber Sortierung verhindern
        tableViewSelected.setFocusTraversable(true);
        // Verhindere Sortierung durch Klick auf Spalten-Header
        tableViewSelected.setOnSort(event -> event.consume());
        
        // CSS für sichtbare Selektion auch ohne Focus - explizit für rechte Tabelle
        tableViewSelected.setStyle("-fx-border-color: #ba68c8; -fx-selection-bar: #ba68c8; -fx-selection-bar-non-focused: #ba68c8; -fx-selection-bar-non-focused: #ba68c8; -fx-selection-bar-non-focused: #ba68c8;");
        
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
        tableViewAvailable.setStyle("-fx-border-color: #ba68c8; -fx-selection-bar: #ba68c8; -fx-selection-bar-non-focused: #ba68c8;");
        tableViewSelected.setStyle("-fx-border-color: #ba68c8; -fx-selection-bar: #ba68c8; -fx-selection-bar-non-focused: #ba68c8;");
        
        // Status initialisieren
        updateStatus("Bereit - Wählen Sie ein Verzeichnis aus");
        
        // Help-Toggle-Button programmatisch erstellen (nach UI-Setup)
        Platform.runLater(() -> createHelpToggleButton());
    }
    
    private void setupEventHandlers() {
        btnSelectDirectory.setOnAction(e -> selectDirectory());
        // Filter, Sortierung und Format-Event-Handler entfernt - einfache Lösung
        btnAddToSelected.setOnAction(e -> addSelectedToRight());
        
        // Downloads-Monitor Event-Handler
        chkDownloadsMonitor.setOnAction(e -> toggleDownloadsMonitor());
        
        btnRemoveFromSelected.setOnAction(e -> removeSelectedFromRight());
        

        
        btnProcessSelected.setOnAction(e -> processSelectedFiles());
        btnProcessAll.setOnAction(e -> {
            processAllFiles();
        });
        btnThemeToggle.setOnAction(e -> toggleTheme());
        btnSplit.setOnAction(e -> openSplitStage());
        btnNewChapter.setOnAction(e -> createNewChapter());
        btnDeleteFile.setOnAction(e -> deleteSelectedFile());
        // btnHelpToggle Event-Handler wird in createHelpToggleButton() gesetzt
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
        
        // Mouse-Click-Handler für sofortige Selektion
        tableViewSelected.setOnMouseClicked(event -> {
            tableViewSelected.refresh();
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
                                tableViewSelected.refresh();
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
                                tableViewSelected.refresh();
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
        // Erstelle einen benutzerdefinierten Dialog für Verzeichnis + Cover-Bild
        CustomAlert directoryAlert = new CustomAlert(CustomAlert.AlertType.INFORMATION);
        directoryAlert.setTitle("Verzeichnis und Cover-Bild auswählen");
        directoryAlert.setHeaderText("Wählen Sie ein Verzeichnis mit DOCX-Dateien und optional ein Cover-Bild");
        
        // Layout für den Dialog
        VBox contentBox = new VBox(15);
        contentBox.setPadding(new Insets(20));
        
        // Verzeichnis-Auswahl
        HBox directoryBox = new HBox(10);
        directoryBox.setAlignment(Pos.CENTER_LEFT);
        
        final int LABEL_COL_WIDTH = 150; // gleiche Spaltenbreite für Labels
        
        // Help-Button für Verzeichnis-Auswahl
        Button dirHelpButton = HelpSystem.createHelpButton(
            "Hilfe zur Verzeichnis-Auswahl",
            "directory_selection.html"
        );
        
        Label dirLabel = new Label("Verzeichnis:");
        dirLabel.setMinWidth(LABEL_COL_WIDTH);
        dirLabel.setPrefWidth(LABEL_COL_WIDTH);
        dirLabel.setAlignment(Pos.CENTER_RIGHT);
        TextField dirField = new TextField();
        dirField.setPromptText("Wählen Sie ein Verzeichnis mit DOCX-Dateien");
        dirField.setPrefWidth(500);
        
        Button btnBrowseDir = new Button("Durchsuchen");
        btnBrowseDir.setMinWidth(110);
        btnBrowseDir.setPrefWidth(110);
        btnBrowseDir.setOnAction(e -> {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Verzeichnis mit DOCX-Dateien auswählen");
        
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
                dirField.setText(selectedDirectory.getAbsolutePath());
            }
        });
        
        directoryBox.getChildren().addAll(dirHelpButton, dirLabel, dirField, btnBrowseDir);
        
        // Cover-Bild-Auswahl
        HBox coverBox = new HBox(10);
        coverBox.setAlignment(Pos.CENTER_LEFT);
        
        // Help-Button für Cover-Bild-Auswahl
        Button coverHelpButton = HelpSystem.createHelpButton(
            "Hilfe zur Cover-Bild-Auswahl",
            "directory_selection.html"
        );
        
        Label coverLabel = new Label("Cover-Bild (optional):");
        coverLabel.setMinWidth(LABEL_COL_WIDTH);
        coverLabel.setPrefWidth(LABEL_COL_WIDTH);
        coverLabel.setAlignment(Pos.CENTER_RIGHT);
        TextField coverField = new TextField();
        coverField.setPromptText("Pfad zum Cover-Bild");
        coverField.setPrefWidth(500);
        
        Button btnBrowseCover = new Button("Durchsuchen");
        btnBrowseCover.setMinWidth(110);
        btnBrowseCover.setPrefWidth(110);
        btnBrowseCover.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Cover-Bild auswählen");
            fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Bilddateien", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"),
                new FileChooser.ExtensionFilter("Alle Dateien", "*.*")
            );
            
            String lastCoverPath = ResourceManager.getParameter("ui.cover_image_path", "");
            if (!lastCoverPath.isEmpty()) {
                File lastCoverDir = new File(lastCoverPath).getParentFile();
                if (lastCoverDir != null && lastCoverDir.exists()) {
                    fileChooser.setInitialDirectory(lastCoverDir);
                }
            }
            
            File selectedFile = fileChooser.showOpenDialog(primaryStage);
            if (selectedFile != null) {
                coverField.setText(selectedFile.getAbsolutePath());
            }
        });
        
        coverBox.getChildren().addAll(coverHelpButton, coverLabel, coverField, btnBrowseCover);
        
        // Lade vorherige Werte
        String lastDir = preferences.get("lastDirectory", "");
        if (!lastDir.isEmpty()) {
            dirField.setText(lastDir);
        }
        
        String lastCover = ResourceManager.getParameter("ui.cover_image_path", "");
        if (!lastCover.isEmpty()) {
            coverField.setText(lastCover);
        }
        
        contentBox.getChildren().addAll(directoryBox, coverBox);
        directoryAlert.setCustomContent(contentBox);
        
        // Theme anwenden (wie andere CustomAlert-Dialoge)
        directoryAlert.applyTheme(currentThemeIndex);
        directoryAlert.initOwner(primaryStage);
        
        // Dialog anzeigen
        Optional<ButtonType> result = directoryAlert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            String selectedDir = dirField.getText();
            String selectedCover = coverField.getText();
            
            if (!selectedDir.isEmpty()) {
                File directory = new File(selectedDir);
                if (directory.exists() && directory.isDirectory()) {
                    txtDirectoryPath.setText(selectedDir);
                    updateProjectTitleFromCurrentPath();
                    loadDocxFiles(directory);
                    
                    // Speichere den Pfad in den Einstellungen
                    preferences.put("lastDirectory", selectedDir);
                    
                    // WICHTIG: Lade das Cover-Bild für das neue Verzeichnis
                    loadCoverImageFromCurrentDirectory();
                }
            }
            
            // Cover-Bild behandeln
            if (!selectedCover.isEmpty()) {
                // Neues Cover-Bild ausgewählt
                File coverFile = new File(selectedCover);
                if (coverFile.exists()) {
                    loadCoverImage(coverFile);
                    // Speichere als cover_image.png im aktuellen Verzeichnis
                    if (!selectedDir.isEmpty()) {
                        saveCoverImageAsPng(coverFile);
                    }
                }
            } else {
                // Kein neues Cover-Bild - entferne das aktuelle nur wenn Verzeichnis gewechselt wurde
                if (!selectedDir.isEmpty()) {
                    setPlaceholderImage();
                }
            }
            
        }
    }
    
    private void loadCoverImage(File coverFile) {
        try {
            Image coverImage = new Image(coverFile.toURI().toString());
            coverImageView.setImage(coverImage);
            coverImageView.setVisible(true);
            coverImageView.setOpacity(1.0);
            coverImageView.getStyleClass().add("cover-image");
            
            // Entferne grünlichen Farbstich durch explizite Farbkorrektur
            coverImageView.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 15, 0, 0, 0); -fx-color: white;");
            
        } catch (Exception e) {
            logger.error("Fehler beim Laden des Cover-Bildes", e);
            coverImageView.setVisible(false);
            showError("Cover-Bild Fehler", "Das Cover-Bild konnte nicht geladen werden: " + e.getMessage());
        }
    }
    
    /**
     * Kopiert ein Bild als cover_image.png in das aktuelle Verzeichnis
     */
    private void saveCoverImageAsPng(File sourceImage) {
        try {
            String currentDir = txtDirectoryPath.getText();
            if (currentDir != null && !currentDir.isEmpty()) {
                File targetDir = new File(currentDir);
                if (targetDir.exists() && targetDir.isDirectory()) {
                    File targetFile = new File(targetDir, "cover_image.png");
                    
                    // Kopiere die Datei
                    java.nio.file.Files.copy(sourceImage.toPath(), targetFile.toPath(), 
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    
                    // Aktualisiere den gespeicherten Pfad
                    
                }
            }
        } catch (Exception e) {
            logger.error("Fehler beim Speichern des Cover-Bildes als PNG", e);
            showError("Cover-Bild Fehler", "Das Cover-Bild konnte nicht als cover_image.png gespeichert werden: " + e.getMessage());
        }
    }
    
    private void loadLastCoverImage() {
        // PRIORITÄT: Suche zuerst nach cover_image.png im aktuellen Verzeichnis
        String currentDir = txtDirectoryPath.getText();
        if (currentDir != null && !currentDir.isEmpty()) {
            File currentDirectory = new File(currentDir);
            if (currentDirectory.exists() && currentDirectory.isDirectory()) {
                File coverImagePng = new File(currentDirectory, "cover_image.png");
                if (coverImagePng.exists()) {
                    loadCoverImage(coverImagePng);
                    return;
                }
            }
        }
        
        // Fallback: Lade das gespeicherte Cover-Bild
        String lastCoverPath = ResourceManager.getParameter("ui.cover_image_path", "");
        if (!lastCoverPath.isEmpty()) {
            File coverFile = new File(lastCoverPath);
            if (coverFile.exists()) {
                loadCoverImage(coverFile);
            } else {
                logger.warn("Cover-Bild nicht gefunden: {}", lastCoverPath);
                // Setze ein Platzhalter-Bild wenn die Datei nicht existiert
                setPlaceholderImage();
            }
        } else {
            // Setze ein Platzhalter-Bild wenn kein Pfad gespeichert ist
            setPlaceholderImage();
        }
    }
    
    private void setPlaceholderImage() {
        // Entferne das ImageView komplett wenn kein Bild vorhanden ist
        if (coverImageView.getParent() != null) {
            if (coverImageView.getParent() instanceof HBox) {
                HBox parentHBox = (HBox) coverImageView.getParent();
                parentHBox.getChildren().remove(coverImageView);
                
                // Entferne auch den HBox-Container aus der BorderPane
                if (parentHBox.getParent() instanceof BorderPane) {
                    BorderPane borderPane = (BorderPane) parentHBox.getParent();
                    borderPane.setTop(null);
                }
            }
        }
    }

    private void updateProjectTitleFromCurrentPath() {
        if (projectTitleLabel == null) return;
        String currentDir = txtDirectoryPath != null ? txtDirectoryPath.getText() : null;
        String name = "";
        if (currentDir != null && !currentDir.trim().isEmpty()) {
            File dir = new File(currentDir);
            name = dir.getName();
        }
        projectTitleLabel.setText(name);
    }
    
    private void loadLastDirectory() {
        String lastDirectory = preferences.get("lastDirectory", "");
        if (lastDirectory == null || lastDirectory.isEmpty()) {
            Platform.runLater(() -> {
                showWarning("Kein Arbeitsverzeichnis", "Bitte wählen Sie ein Verzeichnis mit DOCX-Dateien.");
                showProjectSelectionMenu();
            });
            return;
        }

        File lastDir = new File(lastDirectory);
        if (!lastDir.exists() || !lastDir.isDirectory()) {
            Platform.runLater(() -> {
                showWarning("Arbeitsverzeichnis nicht gefunden", "Das zuletzt verwendete Verzeichnis existiert nicht mehr. Bitte wählen Sie ein neues.");
                showProjectSelectionMenu();
            });
            return;
        }

        txtDirectoryPath.setText(lastDirectory);
        updateProjectTitleFromCurrentPath();
        loadDocxFiles(lastDir);
    }
    
    /**
     * Überwacht Downloads-Verzeichnis auf Sudowrite-ZIP-Dateien und importiert sie automatisch
     */
    private void checkAndImportSudowriteFiles(File projectDirectory) {
        try {
            
            // Verwende das bereits konfigurierte Downloads-Verzeichnis
            if (downloadsDirectory == null || !downloadsDirectory.exists()) {
                logger.warn("Downloads-Verzeichnis nicht konfiguriert oder nicht gefunden");
                logger.warn("downloadsDirectory == null: {}", downloadsDirectory == null);
                if (downloadsDirectory != null) {
                    logger.warn("downloadsDirectory.exists(): {}", downloadsDirectory.exists());
                }
                return;
            }
            
            // Projektname aus Verzeichnis ableiten
            String projectName = projectDirectory.getName();
            
            // Alle ZIP-Dateien im Downloads-Verzeichnis durchsuchen
            File[] zipFiles = downloadsDirectory.listFiles((dir, name) -> 
                name.toLowerCase().endsWith(".zip") && 
                (name.contains(projectName) || name.contains(projectName.replaceAll("\\s+", "_")))
            );
            
            if (zipFiles != null && zipFiles.length > 0) {
                importSudowriteZip(zipFiles[0], projectDirectory);
            } 
            
            
        } catch (Exception e) {
            logger.error("Fehler beim Überwachen der Sudowrite-Dateien", e);
        }
    }
    
    /**
     * Importiert eine Sudowrite-ZIP-Datei ins Projektverzeichnis
     */
    private void importSudowriteZip(File zipFile, File projectDirectory) {
        try {
            
            // ZIP entpacken
            java.util.zip.ZipFile zip = new java.util.zip.ZipFile(zipFile);
            java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zip.entries();
            
            int importedCount = 0;
            while (entries.hasMoreElements()) {
                java.util.zip.ZipEntry entry = entries.nextElement();
                
                // Nur DOCX-Dateien importieren
                if (entry.getName().toLowerCase().endsWith(".docx") && !entry.isDirectory()) {
                    String fileName = new File(entry.getName()).getName();
                    File targetFile = new File(projectDirectory, fileName);
                    
                    // Prüfe ob Zieldatei gesperrt ist (z.B. in Word geöffnet)
                    if (targetFile.exists()) {
                        try {
                            // Versuche die Datei zu löschen um zu prüfen ob sie gesperrt ist
                            if (!targetFile.delete()) {
                                // Datei kann nicht gelöscht werden = gesperrt
                                Platform.runLater(() -> {
                                    CustomAlert alert = new CustomAlert(CustomAlert.AlertType.ERROR);
                                    alert.setTitle("Datei gesperrt");
                                    alert.setHeaderText("Die Zieldatei ist gesperrt");
                                    alert.setContentText("Die Datei '" + targetFile.getName() + "' ist möglicherweise in Word oder einem anderen Programm geöffnet.\n\nBitte schließen Sie die Datei und versuchen Sie es erneut.");
                                    alert.applyTheme(currentThemeIndex);
                                    alert.initOwner(primaryStage);
                                    alert.showAndWait();
                                });
                                continue; // Überspringe diese Datei
                            }
                        } catch (Exception e) {
                            // Datei ist gesperrt
                            Platform.runLater(() -> {
                                CustomAlert alert = new CustomAlert(CustomAlert.AlertType.ERROR);
                                alert.setTitle("Datei gesperrt");
                                alert.setHeaderText("Die Zieldatei ist gesperrt");
                                alert.setContentText("Die Datei '" + targetFile.getName() + "' ist möglicherweise in Word oder einem anderen Programm geöffnet.\n\nBitte schließen Sie die Datei und versuchen Sie es erneut.");
                                alert.applyTheme(currentThemeIndex);
                                alert.initOwner(primaryStage);
                                alert.showAndWait();
                            });
                            continue; // Überspringe diese Datei
                        }
                    }
                    
                    // Datei extrahieren
                    try (java.io.InputStream is = zip.getInputStream(entry);
                         java.io.FileOutputStream fos = new java.io.FileOutputStream(targetFile)) {
                        
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = is.read(buffer)) > 0) {
                            fos.write(buffer, 0, length);
                        }
                        
                        importedCount++;
                    }
                }
            }
            zip.close();
            
            
            // Projekt neu laden
            if (importedCount > 0) {
                // WICHTIG: Keine loadDocxFiles() hier - das verursacht Endlosschleifen!
                updateStatus("Sudowrite-Import abgeschlossen: " + importedCount + " Dateien importiert");
                
                // Benutzer benachrichtigen
                showInfo("Sudowrite-Import", 
                    "Erfolgreich " + importedCount + " DOCX-Dateien aus Sudowrite importiert!");
            }
            
        } catch (Exception e) {
            logger.error("Fehler beim Importieren der Sudowrite-ZIP", e);
            showError("Sudowrite-Import Fehler", 
                "Fehler beim Importieren der ZIP-Datei: " + e.getMessage());
        }
    }
    
    private void loadDocxFiles(File directory) {
        try {
            if (directory == null || !directory.exists() || !directory.isDirectory()) {
                Platform.runLater(() -> {
                    showWarning("Ungültiges Verzeichnis", "Das Arbeitsverzeichnis ist ungültig oder nicht erreichbar. Bitte wählen Sie ein anderes.");
                    showProjectSelectionMenu();
                });
                return;
            }
            updateStatus("Lade DOCX-Dateien...");
            
            
            // Alle DOCX-Dateien im Verzeichnis sammeln (nur flach, keine Unterverzeichnisse)
            Set<File> fileSet = java.nio.file.Files.list(directory.toPath())
                    .filter(path -> {
                        boolean isDocx = path.toString().toLowerCase().endsWith(".docx");
                        return isDocx;
                    })
                    .map(java.nio.file.Path::toFile)
                    .collect(Collectors.toSet()); // Set statt List für keine Duplikate
            
            // Debug: Alle gefundenen DOCX-Dateien ausgeben
            logger.debug("Gefundene DOCX-Dateien in {}: {}", directory.getAbsolutePath(), 
                fileSet.stream().map(File::getName).collect(Collectors.toList()));
            
            List<File> files = new ArrayList<>(fileSet);
            
            
            // Sudowrite-ZIP-Dateien überprüfen und importieren (nur wenn Downloads-Monitor aktiv)
            if (downloadsDirectory != null && downloadsDirectory.exists()) {
                checkAndImportSudowriteFiles(directory);
            }
            
            // Alle Listen leeren
            
            allDocxFiles.clear();
            originalDocxFiles.clear();
            selectedDocxFiles.clear();
            
            // Für jede Datei entscheiden: links oder rechts?
            for (File file : files) {
                DocxFile docxFile = new DocxFile(file);
                originalDocxFiles.add(docxFile);
                
                // Prüfe: Hat die Datei eine MD-Datei?
                File mdFile = deriveMdFileFor(docxFile.getFile());
                boolean hasMdFile = mdFile != null && mdFile.exists();
                
                // Debug: MD-Datei-Status für jede Datei
                logger.debug("Datei: {} - MD-Datei: {} - Existiert: {}", 
                    docxFile.getFileName(), 
                    mdFile != null ? mdFile.getAbsolutePath() : "null", 
                    hasMdFile);
                
                if (hasMdFile) {
                    // Datei hat MD-Datei → nach rechts
                    selectedDocxFiles.add(docxFile);
                } else {
                    // Datei hat keine MD-Datei → nach links
                allDocxFiles.add(docxFile);
                }
            }
            
            
            // Debug: Alle Dateien in allDocxFiles auflisten
            
            // WICHTIG: Hash-basierte Änderungsprüfung für alle geladenen Dateien
            checkAllDocxFilesForChanges();
            
            // WICHTIG: Gespeicherte Reihenfolge laden (falls vorhanden)
            loadSavedOrder(directory);
            
            // WICHTIG: Alle Dateien mit MD-Dateien in die rechte Tabelle laden (auch neue)
            for (DocxFile docxFile : originalDocxFiles) {
                File mdFile = deriveMdFileFor(docxFile.getFile());
                boolean hasMdFile = mdFile != null && mdFile.exists();
                
                if (hasMdFile && !selectedDocxFiles.contains(docxFile)) {
                    selectedDocxFiles.add(docxFile);
                    logger.debug("Neue Datei mit MD-Datei hinzugefügt: {}", docxFile.getFileName());
                }
            }
            
              
          
            
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
                } catch (Exception e) {
                    logger.error("Fehler in der Datei-Überwachung", e);
                }
            });
            
            watchThread.setDaemon(true);
            watchThread.start();
            
        } catch (Exception e) {
            logger.error("Fehler beim Starten der Datei-Überwachung", e);
        }
    }
    
    public void stopFileWatcher() {
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
            } catch (Exception e) {
                logger.error("Fehler beim Schließen des WatchService", e);
            }
            watchService = null;
        }
        
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
                    } else {
                        // Datei hat keine MD-Datei → nach links
                        allDocxFiles.add(docxFile);
                    }
                }
                
                updateStatus(newFiles.size() + " neue Dateien hinzugefügt");
            } else {
            }
            
        } catch (Exception e) {
            logger.error("Fehler beim Hinzufügen neuer DOCX-Dateien", e);
        }
    }
    
    public void checkAllDocxFilesForChanges() {
        try {
            
            // Prüfe alle Dateien in beiden Listen
            List<DocxFile> allFiles = new ArrayList<>();
            allFiles.addAll(allDocxFiles);
            allFiles.addAll(selectedDocxFiles);
            
            // Entferne gelöschte DOCX-Dateien aus beiden Listen
            allDocxFiles.removeIf(docxFile -> !docxFile.getFile().exists());
            selectedDocxFiles.removeIf(docxFile -> !docxFile.getFile().exists());
            
            for (DocxFile docxFile : allFiles) {
                String currentHash = calculateFileHash(docxFile.getFile());
                String savedHash = loadDocxHash(docxFile.getFile());
                
                if (currentHash != null && savedHash != null && !currentHash.equals(savedHash)) {
                    // Datei wurde geändert!
                    docxFile.setChanged(true);
                    // Hash NICHT automatisch aktualisieren - nur manuell beim Speichern
                } else if (currentHash != null && savedHash == null) {
                    // Neue Datei - noch nie verarbeitet
                    docxFile.setChanged(true);
                    // Hash speichern für erste Verarbeitung
                    saveDocxHash(docxFile.getFile(), currentHash);
                } else if (currentHash != null && savedHash != null && currentHash.equals(savedHash)) {
                    // Datei unverändert
                    docxFile.setChanged(false);
                    // Hash nur speichern wenn unverändert
                    saveDocxHash(docxFile.getFile(), currentHash);
                }
            }
            
            
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
                    found = true;
                    break;
                }
            }
            
            // Suche in selectedDocxFiles
            if (!found) {
                for (DocxFile file : selectedDocxFiles) {
                    if (file.getFile().getAbsolutePath().equals(docxFile.getAbsolutePath())) {
                        file.setChanged(false);
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
        
        
        // Debug: Alle ausgewählten Dateien auflisten
        for (int i = 0; i < selectedFiles.size(); i++) {
            DocxFile file = selectedFiles.get(i);
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
                        } catch (Exception e) {
                            logger.error("Fehler beim Löschen der MD-Datei {}: {}", mdFile.getName(), e.getMessage());
                        }
                    }
                }
                
                // Alle Dateien nach links verschieben
        
        selectedDocxFiles.removeAll(selectedFiles);
                
                for (DocxFile file : selectedFiles) {
                    if (!allDocxFiles.contains(file)) {
                        allDocxFiles.add(file);
                    }
                }
                
                
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
            
            selectedDocxFiles.removeAll(selectedFiles);
            
            for (DocxFile file : selectedFiles) {
                if (!allDocxFiles.contains(file)) {
                    allDocxFiles.add(file);
                }
            }
            
            
            // Tabellen explizit aktualisieren
            tableViewSelected.refresh();
            tableViewAvailable.refresh();
            
            updateStatus(selectedFiles.size() + " Dateien nach links verschoben");
        }
        
        // Keine Auswahl mehr speichern - MD-Erkennung ist ausreichend
    }
    

    
    private void processSelectedFiles() {
        // NEU: Buch exportieren - ALLE Dateien aus der rechten Tabelle
        if (selectedDocxFiles.isEmpty()) {
            showWarning("Keine Dateien vorhanden", "Bitte fügen Sie zuerst Dateien zur rechten Tabelle hinzu.");
            return;
        }
        
        // Prüfe auf existierende .gesamt Dateien im Hauptverzeichnis
        String directoryPath = selectedDocxFiles.get(0).getFile().getParent();
        File directory = new File(directoryPath);
        String directoryName = directory.getName();
        File[] existingGesamtFiles = directory.listFiles((dir, name) ->
            (name.startsWith(directoryName + ".gesamt.") || name.equals(directoryName + " Buch.md")) &&
            (name.endsWith(".md") || name.endsWith(".txt") || name.endsWith(".html"))
        );
        
        if (existingGesamtFiles != null && existingGesamtFiles.length > 0) {
            showGesamtFileDialog(existingGesamtFiles, selectedDocxFiles);
        } else {
            // Direkt Buch exportieren für ALLE Dateien aus der rechten Tabelle
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
                                
                                EditorWindow editorController = openChapterEditorWindow(docxContent, chapterFile, format);
                                
                                if (editorController != null) {
                                    try {
                                        String editorContent = editorController.getText();
                                        File mdFileToSave = deriveMdFileFor(chapterFile.getFile());
                                        if (mdFileToSave != null) {
                                            java.nio.file.Files.write(mdFileToSave.toPath(), editorContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                                        } else {
                                            logger.error("=== DEBUG: MD-Datei Pfad ist NULL!");
                                        }
                                    } catch (Exception saveException) {
                                        logger.error("=== DEBUG: Fehler beim Speichern der MD-Datei", saveException);
                                    }
                                } else {
                                    logger.error("=== DEBUG: Editor-Controller ist NULL!");
                                }
                                
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
                String content = docxProcessor.processDocxFileContent(chapterFile.getFile(), 1, format);
                
                // Speichere als MD-Datei
                if (mdFile != null) {
                    try {
                        java.nio.file.Files.write(mdFile.toPath(), content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
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
    
    /**
     * Findet den aktuell geöffneten Editor für ein Kapitel
     */
    private EditorWindow findCurrentEditorForChapter(DocxFile chapterFile) {
        
        String chapterName = chapterFile.getFileName();
        if (chapterName.toLowerCase().endsWith(".docx")) {
            chapterName = chapterName.substring(0, chapterName.length() - 5);
        }
        
        String editorKey = chapterName + ".md";
        EditorWindow editor = openEditors.get(editorKey);
        
        if (editor != null) {
            return editor;
        } else {
            return null;
        }
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
            String mdContent;
            String docxContent = docxProcessor.processDocxFileContent(chapterFile.getFile(), 1, format);
            
            // Prüfe ob ein Editor für dieses Kapitel geöffnet ist
            EditorWindow currentEditor = findCurrentEditorForChapter(chapterFile);
            if (currentEditor != null) {
                // Verwende den aktuellen Editor-Inhalt statt der gespeicherten Datei
                mdContent = currentEditor.getText();
            } else {
                // Fallback: Verwende die gespeicherte Datei
                mdContent = new String(java.nio.file.Files.readAllBytes(mdFile.toPath()), java.nio.charset.StandardCharsets.UTF_8);
            }
            
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
            // Hover-Vorschau: kleines undekoriertes Fenster mit WebView pro Dialog
            final javafx.stage.Stage hoverPreviewStage = new javafx.stage.Stage(javafx.stage.StageStyle.UNDECORATED);
            hoverPreviewStage.initOwner(diffStage);
            hoverPreviewStage.setAlwaysOnTop(true);
            javafx.scene.web.WebView hoverWebView = new javafx.scene.web.WebView();
            hoverWebView.setPrefSize(500, 300);
            javafx.scene.Scene hoverScene = new javafx.scene.Scene(hoverWebView);
            
            // Theme-Styling für das Hover-Fenster
            try {
                String cssPath = ResourceManager.getCssResource("css/manuskript.css");
                if (cssPath != null && !cssPath.isEmpty()) {
                    hoverScene.getStylesheets().add(cssPath);
                }
            } catch (Exception ignored) {}
            
            hoverPreviewStage.setScene(hoverScene);
            javafx.animation.PauseTransition hoverHideTimer = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(3));
            hoverHideTimer.setOnFinished(ev -> hoverPreviewStage.hide());

            diffStage.setOnCloseRequest(e -> {
                hoverHideTimer.stop();
                hoverPreviewStage.hide();
            });
            
            for (int i1=0; i1<blocks.size(); i1++) {
                DiffBlock block = blocks.get(i1);

                
                // Checkbox nur für grüne Blöcke (ADDED)
                CheckBox blockCheckBox = null;
                if (block.getType() == DiffBlockType.ADDED) {
                    blockCheckBox = new CheckBox();
                    // kleinere Checkbox für rechte grüne Blöcke
                    blockCheckBox.getStyleClass().add("diff-green-checkbox");
                    blockCheckBox.setStyle("-fx-padding: 0;");
                    blockCheckBox.setPadding(Insets.EMPTY);
                    blockCheckBox.setMinSize(12, 12);
                    blockCheckBox.setPrefSize(12, 12);
                    blockCheckBox.setMaxSize(12, 12);
                    blockCheckBox.setScaleX(0.8);
                    blockCheckBox.setScaleY(0.8);
                    blockCheckBox.setSelected(false); // Standardmäßig ungecheckt
                    blockCheckBoxes.add(blockCheckBox);

                    List<String> blockTextList = new ArrayList<>();
                    for (DiffProcessor.DiffLine line : block.getLines()) {
                        blockTextList.add(line.getNewText());
                    }
                    blockTexts.add(blockTextList);
                }
                
                // Gesamten Text des Blocks für Hover-Vorschau vorbereiten (nur für grüne Blöcke)
                final String combinedBlockText;
                final String pairedDeletedText;
                if (block.getType() == DiffBlockType.ADDED) {
                    StringBuilder sb = new StringBuilder();
                    for (DiffProcessor.DiffLine l : block.getLines()) {
                        String t = l.getNewText();
                        if (t != null) sb.append(t).append("\n");
                    }
                    combinedBlockText = sb.toString();

                    // Suche den korrespondierenden roten Block links (gleicher Index in der Blockliste)
                    StringBuilder del = new StringBuilder();
                    
                    // Debug entfernt
                    
                    // Suche den korrespondierenden DELETED Block basierend auf der Position
                    // Der grüne Block ist an Index i1, suche den DELETED Block davor
                    for (int scan = i1 - 1; scan >= 0; scan--) {
                        DiffBlock prevBlock = blocks.get(scan);
                        if (prevBlock.getType() == DiffBlockType.DELETED) {
                            // Debug entfernt
                            for (DiffProcessor.DiffLine l : prevBlock.getLines()) {
                                String t = l.getOriginalText();
                                if (t != null) del.append(t).append("\n");
                            }
                            break;
                        }
                    }
                    
                    // Debug entfernt
                    
                    pairedDeletedText = del.toString();
                    
                    // Popupgröße dynamisch an den Text anpassen
                    final int[] sz = computePopupSize((pairedDeletedText != null && !pairedDeletedText.trim().isEmpty())
                            ? (pairedDeletedText + "\n" + combinedBlockText)
                            : combinedBlockText);
                } else {
                    combinedBlockText = null;
                    pairedDeletedText = null;
                }
                
                // Erstelle Zeilen für diesen Block

                // Falls aktueller Block DELETED ist und der nächste ADDED: gepaarte, minimale Höhe rendern
                if (block.getType() == DiffBlockType.DELETED && i1 + 1 < blocks.size() && blocks.get(i1 + 1).getType() == DiffBlockType.ADDED) {
                    DiffBlock deletedBlock = block;
                    DiffBlock addedBlock = blocks.get(i1 + 1);
                    int dSize = deletedBlock.getLines().size();
                    int aSize = addedBlock.getLines().size();
                    int maxSize = Math.max(dSize, aSize);
                    // Kombinierte Texte für Hover-Popup vorbereiten (wie bei grünen Blöcken)
                    StringBuilder delSb = new StringBuilder();
                    for (int k = 0; k < dSize; k++) {
                        String t = deletedBlock.getLines().get(k).getOriginalText();
                        if (t != null) delSb.append(t).append("\n");
                    }
                    StringBuilder addSb = new StringBuilder();
                    for (int k = 0; k < aSize; k++) {
                        String t = addedBlock.getLines().get(k).getNewText();
                        if (t != null) addSb.append(t).append("\n");
                    }
                    final String pairedDeletedTextPaired = delSb.toString();
                    final String combinedBlockTextPaired = addSb.toString();
                    final String hoverHtmlPaired = (pairedDeletedTextPaired != null && !pairedDeletedTextPaired.trim().isEmpty())
                            ? buildHtmlDiffPreview(pairedDeletedTextPaired, combinedBlockTextPaired)
                            : buildHtmlPreview(combinedBlockTextPaired);
                    CheckBox pairedBlockCheckBox = new CheckBox();
                    pairedBlockCheckBox.getStyleClass().add("diff-green-checkbox");
                    pairedBlockCheckBox.setStyle("-fx-padding: 0;");
                    pairedBlockCheckBox.setPadding(Insets.EMPTY);
                    pairedBlockCheckBox.setMinSize(12, 12);
                    pairedBlockCheckBox.setPrefSize(12, 12);
                    pairedBlockCheckBox.setMaxSize(12, 12);
                    pairedBlockCheckBox.setScaleX(0.8);
                    pairedBlockCheckBox.setScaleY(0.8);
                    pairedBlockCheckBox.setSelected(false);
                    blockCheckBoxes.add(pairedBlockCheckBox);

                    List<String> pairedBlockTextList = new ArrayList<>();
                    for (DiffProcessor.DiffLine line : addedBlock.getLines()) {
                        pairedBlockTextList.add(line.getNewText());
                    }
                    blockTexts.add(pairedBlockTextList);

                    final CheckBox checkboxForPairing = pairedBlockCheckBox;

                    for (int i = 0; i < maxSize; i++) {
                        DiffProcessor.DiffLine dLine = i < dSize ? deletedBlock.getLines().get(i) : null;
                        DiffProcessor.DiffLine aLine = i < aSize ? addedBlock.getLines().get(i) : null;

                        HBox leftLineBox = new HBox(5);
                        HBox rightLineBox = new HBox(5);

                        Label leftLineNum = new Label(String.format("%3d", dLine != null ? dLine.getLeftLineNumber() : (i > 0 && dSize > 0 ? deletedBlock.getLines().get(dSize - 1).getLeftLineNumber() + i - dSize + 1 : 0)));
                        leftLineNum.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 10px; -fx-text-fill: #6c757d; -fx-min-width: 30px; -fx-alignment: center-right;");

                        Label rightLineNum = new Label(String.format("%3d", aLine != null ? aLine.getRightLineNumber() : (i > 0 && aSize > 0 ? addedBlock.getLines().get(aSize - 1).getRightLineNumber() + i - aSize + 1 : 0)));
                        rightLineNum.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 10px; -fx-text-fill: #6c757d; -fx-min-width: 30px; -fx-alignment: center-right;");

                        Label leftLineLabel = new Label(dLine != null ? dLine.getOriginalText() : "");
                        leftLineLabel.setWrapText(true);
                        leftLineLabel.setPrefWidth(620);
                        leftLineLabel.setMinHeight(Region.USE_PREF_SIZE);
                        leftLineLabel.setMaxHeight(Region.USE_PREF_SIZE);
                        leftLineLabel.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 12px;");

                        Label rightLineLabel = new Label(aLine != null ? aLine.getNewText() : "");
                        rightLineLabel.setWrapText(true);
                        rightLineLabel.setPrefWidth(620);
                        rightLineLabel.setMinHeight(Region.USE_PREF_SIZE);
                        rightLineLabel.setMaxHeight(Region.USE_PREF_SIZE);
                        rightLineLabel.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 12px;");

                        // Styles je Seite setzen, wenn Inhalt existiert
                        if (dLine != null) {
                            leftLineLabel.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 12px; -fx-background-color: #f8d7da; -fx-text-fill: #721c24; -fx-font-weight: bold;");
                            leftLineNum.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 10px; -fx-text-fill: #dc3545; -fx-min-width: 30px; -fx-alignment: center-right; -fx-font-weight: bold;");
                        }
                        if (aLine != null) {
                            rightLineLabel.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 12px; -fx-background-color: #d4edda; -fx-text-fill: #155724; -fx-font-weight: bold;");
                            rightLineNum.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 10px; -fx-text-fill: #28a745; -fx-min-width: 30px; -fx-alignment: center-right; -fx-font-weight: bold;");
                            // Hover-Vorschau für die gepaarte grüne Seite aktivieren
                            javafx.event.EventHandler<javafx.scene.input.MouseEvent> enterHandler = evt -> {
                                hoverHideTimer.stop();
                                int[] ps = computePopupSize(pairedDeletedTextPaired + "\n" + combinedBlockTextPaired);
                                hoverWebView.setPrefSize(ps[0], ps[1]);
                                hoverWebView.getEngine().loadContent(hoverHtmlPaired, "text/html");
                                double x = evt.getScreenX() + 12;
                                double y = evt.getScreenY() + 12;
                                hoverPreviewStage.setX(x);
                                hoverPreviewStage.setY(y);
                                if (!hoverPreviewStage.isShowing()) hoverPreviewStage.show();
                            };
                            javafx.event.EventHandler<javafx.scene.input.MouseEvent> exitHandler = evt -> hoverPreviewStage.hide();
                            rightLineBox.setOnMouseEntered(enterHandler);
                            rightLineBox.setOnMouseExited(exitHandler);
                            rightLineLabel.setOnMouseEntered(enterHandler);
                            rightLineLabel.setOnMouseExited(exitHandler);
                        }

                        Platform.runLater(() -> {
                            double maxHeight = Math.max(leftLineLabel.getHeight(), rightLineLabel.getHeight());
                            leftLineLabel.setMinHeight(maxHeight);
                            leftLineLabel.setMaxHeight(maxHeight);
                            rightLineLabel.setMinHeight(maxHeight);
                            rightLineLabel.setMaxHeight(maxHeight);
                        });

                        leftLineBox.getChildren().addAll(leftLineNum, leftLineLabel);
                        
                        // Checkbox für gepaarte ADDED-Blöcke hinzufügen
                        if (aLine != null && i == 0 && checkboxForPairing != null) { // Nur bei der ersten Zeile des ADDED-Blocks
                            VBox checkboxContainer = new VBox();
                            checkboxContainer.setAlignment(Pos.CENTER);
                            checkboxContainer.setSpacing(0);
                            checkboxContainer.setPadding(Insets.EMPTY);
                            checkboxContainer.setMinWidth(16);
                            checkboxContainer.setMaxWidth(16);
                            checkboxContainer.getChildren().add(checkboxForPairing);

                            rightLineBox.getChildren().addAll(rightLineNum, rightLineLabel, checkboxContainer);
                        } else {
                            rightLineBox.getChildren().addAll(rightLineNum, rightLineLabel);
                        }

                        leftContentBox.getChildren().add(leftLineBox);
                        rightContentBox.getChildren().add(rightLineBox);
                    }
                    // Überspringe den nächsten (ADDED), da bereits gepaart gerendert
                    i1++;
                    continue;
                }

                // Erstelle Zeilen für diesen Block (Standard)
                for (int i = 0; i < block.getLines().size(); i++) {
                    DiffProcessor.DiffLine diffLine = block.getLines().get(i);
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
                        checkboxContainer.setSpacing(0);
                        checkboxContainer.setPadding(Insets.EMPTY);
                        checkboxContainer.setMinWidth(16);
                        checkboxContainer.setMaxWidth(16);
                        checkboxContainer.getChildren().add(blockCheckBox);

                        rightLineBox.getChildren().addAll(rightLineNum, rightLineLabel, checkboxContainer);
                    } else {
                        rightLineBox.getChildren().addAll(rightLineNum, rightLineLabel);
                    }

                    // Hover-Vorschau nur für GRÜNE Blöcke (rechte Seite) aktivieren
                    if (block.getType() == DiffBlockType.ADDED && combinedBlockText != null && !combinedBlockText.trim().isEmpty()) {
                        final String html = (pairedDeletedText != null && !pairedDeletedText.trim().isEmpty())
                                ? buildHtmlDiffPreview(pairedDeletedText, combinedBlockText)
                                : buildHtmlPreview(combinedBlockText);
                        final int[] sz = computePopupSize((pairedDeletedText != null && !pairedDeletedText.trim().isEmpty())
                                ? (pairedDeletedText + "\n" + combinedBlockText)
                                : combinedBlockText);
                        javafx.event.EventHandler<javafx.scene.input.MouseEvent> enterHandler = evt -> {
                            hoverHideTimer.stop();
                            hoverWebView.setPrefSize(sz[0], sz[1]);
                            hoverWebView.getEngine().loadContent(html, "text/html");
                            double x = evt.getScreenX() + 12;
                            double y = evt.getScreenY() + 12;
                            hoverPreviewStage.setX(x);
                            hoverPreviewStage.setY(y);
                            if (!hoverPreviewStage.isShowing()) hoverPreviewStage.show();
                        };
                        javafx.event.EventHandler<javafx.scene.input.MouseEvent> exitHandler = evt -> hoverPreviewStage.hide();
                        rightLineBox.setOnMouseEntered(enterHandler);
                        rightLineBox.setOnMouseExited(exitHandler);
                        rightLineLabel.setOnMouseEntered(enterHandler);
                        rightLineLabel.setOnMouseExited(exitHandler);
                    }
                    
                    leftContentBox.getChildren().add(leftLineBox);
                    rightContentBox.getChildren().add(rightLineBox);
                }
            }
            
            // Tausche leere rote Boxen mit nachfolgenden grünen Boxen für bessere Lesbarkeit
            optimizeRightContentOrder(rightContentBox);
            
            
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
            
            Button btnKeepCurrent = new Button("💾 Aktuellen Zustand beibehalten");
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
                    for (int i = 0; i < blocks.size(); i++) {
                        DiffBlock block = blocks.get(i);

                        if (block.getType() == DiffBlockType.DELETED && i + 1 < blocks.size()
                                && blocks.get(i + 1).getType() == DiffBlockType.ADDED) {
                            DiffBlock addedBlock = blocks.get(i + 1);
                            boolean isSelected = checkboxIndex < blockCheckBoxes.size()
                                    && blockCheckBoxes.get(checkboxIndex).isSelected();

                            if (isSelected) {
                                for (DiffProcessor.DiffLine line : addedBlock.getLines()) {
                                    String text = line.getNewText();
                                    if (text != null) {
                                        newContent.append(text).append("\n");
                                    }
                                }
                            } else {
                                for (DiffProcessor.DiffLine line : block.getLines()) {
                                    String text = line.getOriginalText();
                                    if (text != null) {
                                        newContent.append(text).append("\n");
                                    }
                                }
                            }

                            checkboxIndex++;
                            i++; // ADDED-Block wurde mitverarbeitet
                            continue;
                        }

                        if (block.getType() == DiffBlockType.ADDED) {
                            boolean isSelected = checkboxIndex < blockCheckBoxes.size()
                                    && blockCheckBoxes.get(checkboxIndex).isSelected();
                            if (isSelected) {
                                for (DiffProcessor.DiffLine line : block.getLines()) {
                                    String text = line.getNewText();
                                    if (text != null) {
                                        newContent.append(text).append("\n");
                                    }
                                }
                            }
                            checkboxIndex++;
                            continue;
                        }

                        if (block.getType() == DiffBlockType.DELETED) {
                            for (DiffProcessor.DiffLine line : block.getLines()) {
                                String text = line.getOriginalText();
                                if (text != null) {
                                    newContent.append(text).append("\n");
                                }
                            }
                            continue;
                        }

                        // UNCHANGED
                        for (DiffProcessor.DiffLine line : block.getLines()) {
                            String text = line.getNewText();
                            if (text != null) {
                                newContent.append(text).append("\n");
                            }
                        }
                    }
                    
                    // Entferne überflüssige Leerzeilen am Ende
                    String finalContent = newContent.toString();
                    // Entferne alle Leerzeilen am Ende
                    while (finalContent.endsWith("\n")) {
                        finalContent = finalContent.substring(0, finalContent.length() - 1);
                    }
                    
                    // Füge Leerzeilen zwischen Absätzen hinzu, wenn nötig
                    finalContent = addParagraphSpacing(finalContent);
                    
                    // Keine MD-Datei speichern - nur den Inhalt verwenden
                    
                    // Prüfe ob bereits ein Editor für dieses Kapitel geöffnet ist
                    String chapterName = chapterFile.getFileName();
                    if (chapterName.toLowerCase().endsWith(".docx")) {
                        chapterName = chapterName.substring(0, chapterName.length() - 5);
                    }
                    String editorKey = chapterName + ".md";
                    EditorWindow existingEditor = openEditors.get(editorKey);
                    
                    if (existingEditor != null) {
                        // Bestehenden Editor aktualisieren - KEIN neues Fenster öffnen
                        final String finalContentForLambda = finalContent;
                        Platform.runLater(() -> {
                            existingEditor.replaceTextWithoutUpdatingOriginal(finalContentForLambda);
                            // Editor in den Vordergrund bringen
                            for (Window window : Window.getWindows()) {
                                if (window instanceof CustomStage && window.isShowing()) {
                                    CustomStage customStage = (CustomStage) window;
                                    if (customStage.getTitle().contains(chapterFile.getFileName())) {
                                        customStage.toFront();
                                        customStage.requestFocus();
                                        break;
                                    }
                                }
                            }
                        });
                    } else {
                        // Nur wenn KEIN bestehender Editor existiert - dann neuen erstellen
                        EditorWindow editorController = openChapterEditorWindow(mdContent, chapterFile, format);
                        
                        // Nach Diff-Auswahl den Text ersetzen
                        if (editorController != null) {
                            final String finalContentForLambda = finalContent;
                            Platform.runLater(() -> {
                                editorController.replaceTextWithoutUpdatingOriginal(finalContentForLambda);
                                // Editor in den Vordergrund bringen
                                for (Window window : Window.getWindows()) {
                                    if (window instanceof CustomStage && window.isShowing()) {
                                        CustomStage customStage = (CustomStage) window;
                                        if (customStage.getTitle().contains(chapterFile.getFileName())) {
                                            customStage.toFront();
                                            customStage.requestFocus();
                                            break;
                                        }
                                    }
                                }
                            });
                        }
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
                    // Prüfe ob bereits ein Editor für dieses Kapitel geöffnet ist
                    String chapterName = chapterFile.getFileName();
                    if (chapterName.toLowerCase().endsWith(".docx")) {
                        chapterName = chapterName.substring(0, chapterName.length() - 5);
                    }
                    String editorKey = chapterName + ".md";
                    EditorWindow existingEditor = openEditors.get(editorKey);
                    
                    EditorWindow editorController;
                    if (existingEditor != null) {
                        // Bestehenden Editor aktualisieren - KEIN neues Fenster öffnen
                        editorController = existingEditor;
                        editorController.setText(docxContent);
                    } else {
                        // Nur wenn KEIN bestehender Editor existiert - dann neuen erstellen
                        // WICHTIG: Immer über openChapterEditorWindow gehen für Dialog-Logik
                        editorController = openChapterEditorWindow(docxContent, chapterFile, format);
                    }
                    
                    // WICHTIG: Editor-Inhalt SOFORT als MD speichern, da wir "DOCX übernehmen" gesagt haben
                    if (editorController != null) {
                        try {
                            String editorContent = editorController.getText();
                            File mdFileToSave = deriveMdFileFor(chapterFile.getFile());
                            if (mdFileToSave != null) {
                                java.nio.file.Files.write(mdFileToSave.toPath(), editorContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                                
                                // Hash aktualisieren, damit keine "extern geändert" Dialoge mehr kommen
                                updateDocxHashAfterAccept(chapterFile.getFile());
                                markDocxFileAsUnchanged(chapterFile.getFile());
                            } else {
                                logger.error("=== DEBUG: MD-Datei Pfad ist NULL!");
                            }
                        } catch (Exception saveException) {
                            logger.error("=== DEBUG: Fehler beim Speichern der MD-Datei", saveException);
                        }
                    } else {
                        logger.error("=== DEBUG: Editor-Controller ist NULL!");
                    }
                    
                    // WICHTIG: Editor in den Vordergrund bringen
                    Platform.runLater(() -> {
                        for (Window window : Window.getWindows()) {
                            if (window instanceof CustomStage && window.isShowing()) {
                                CustomStage customStage = (CustomStage) window;
                                if (customStage.getTitle().contains(chapterFile.getFileName())) {
                                    customStage.toFront();
                                    customStage.requestFocus();
                                    break;
                                }
                            }
                        }
                    });
                    
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
                    // Prüfe ob bereits ein Editor für dieses Kapitel geöffnet ist
                    String chapterName = chapterFile.getFileName();
                    if (chapterName.toLowerCase().endsWith(".docx")) {
                        chapterName = chapterName.substring(0, chapterName.length() - 5);
                    }
                    String editorKey = chapterName + ".md";
                    EditorWindow existingEditor = openEditors.get(editorKey);
                    
                    if (existingEditor != null) {
                        // Bestehenden Editor in den Vordergrund bringen
                        Platform.runLater(() -> {
                            for (Window window : Window.getWindows()) {
                                if (window instanceof CustomStage && window.isShowing()) {
                                    CustomStage customStage = (CustomStage) window;
                                    if (customStage.getTitle().contains(chapterFile.getFileName())) {
                                        customStage.toFront();
                                        customStage.requestFocus();
                                        break;
                                    }
                                }
                            }
                        });
                    } else {
                        // Neuen Editor erstellen
                        EditorWindow newEditor = openChapterEditorWindow(mdContent, chapterFile, format);
                        
                        // WICHTIG: Editor in den Vordergrund bringen
                        Platform.runLater(() -> {
                            for (Window window : Window.getWindows()) {
                                if (window instanceof CustomStage && window.isShowing()) {
                                    CustomStage customStage = (CustomStage) window;
                                    if (customStage.getTitle().contains(chapterFile.getFileName())) {
                                        customStage.toFront();
                                        customStage.requestFocus();
                                        break;
                                    }
                                }
                            }
                        });
                    }
                    
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
            diffScene.getStylesheets().add(ResourceManager.getCssResource("css/manuskript.css"));
             
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

    // Baut simples HTML für die Hover-Vorschau
    private String buildHtmlPreview(String text) {
        String escaped = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\n", "<br>");
        return "<html><head><meta charset=\"UTF-8\"></head><body style=\"font-family:Segoe UI,Arial,sans-serif;font-size:13px;margin:8px;\">"
                + escaped + "</body></html>";
    }

    // HTML Diff-Vorschau: gelöscht rot durchgestrichen, hinzugefügt grün unterstrichen
    private String buildHtmlDiffPreview(String originalText, String newText) {
        List<String> a = tokenizeWords(originalText);
        List<String> b = tokenizeWords(newText);
        List<DiffOp> ops = diff(a, b);
        StringBuilder sb = new StringBuilder();
        
        // Theme-Hintergrundfarbe für das Hover-Fenster
        String themeBgColor = THEMES[currentThemeIndex][0];
        String themeTextColor = THEMES[currentThemeIndex][1];
        
        sb.append("<html><head><meta charset=\"UTF-8\"></head><body style=\"")
          .append("font-family:Segoe UI,Arial,sans-serif;font-size:13px;margin:8px;line-height:1.4;")
          .append("background-color:").append(themeBgColor).append(";")
          .append("color:").append(themeTextColor).append(";\">");
        
        for (DiffOp op : ops) {
            switch (op.type) {
                case EQUAL:
                    sb.append(escape(op.token));
                    break;
                case DELETE:
                    sb.append("<span style=\"color:#c0392b;text-decoration:line-through;\">")
                      .append(escape(op.token)).append("</span>");
                    break;
                case INSERT:
                    sb.append("<span style=\"color:#2e7d32;text-decoration:underline;\">")
                      .append(escape(op.token)).append("</span>");
                    break;
            }
        }
        sb.append("</body></html>");
        return sb.toString();
    }

    // Schätzt eine sinnvolle Popupgröße auf Basis der Zeilenanzahl und maximaler Zeilenlänge
    private int[] computePopupSize(String text) {
        if (text == null) return new int[]{400, 240};
        String[] lines = text.split("\n", -1);
        int maxLen = 0;
        for (String l : lines) {
            if (l.length() > maxLen) maxLen = l.length();
        }
        int width = Math.min(800, Math.max(300, maxLen * 7)); // grobe Schätzung 7px/Zeichen
        int height = Math.min(600, Math.max(160, lines.length * 18 + 20)); // grob 18px/Zeile + Padding
        return new int[]{width, height};
    }

    private String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
    }

    private static class DiffOp {
        enum Type { EQUAL, INSERT, DELETE }
        final Type type; final String token;
        DiffOp(Type t, String tok) { this.type = t; this.token = tok; }
    }

    // Sehr einfache Wort-Tokenisierung (Whitespace-sensitiv, Zeilenumbrüche erhalten)
    private List<String> tokenizeWords(String s) {
        List<String> tokens = new ArrayList<>();
        if (s == null || s.isEmpty()) return tokens;
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (Character.isWhitespace(ch)) {
                if (cur.length() > 0) { tokens.add(cur.toString()); cur.setLength(0); }
                tokens.add(String.valueOf(ch));
            } else {
                cur.append(ch);
            }
        }
        if (cur.length() > 0) tokens.add(cur.toString());
        return tokens;
    }

    // LCS-basierter Diff auf Token-Ebene (einfach, ausreichend für Vorschau)
    private List<DiffOp> diff(List<String> a, List<String> b) {
        int n = a.size(), m = b.size();
        int[][] dp = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                if (a.get(i).equals(b.get(j))) dp[i][j] = dp[i + 1][j + 1] + 1;
                else dp[i][j] = Math.max(dp[i + 1][j], dp[i][j + 1]);
            }
        }
        List<DiffOp> ops = new ArrayList<>();
        int i = 0, j = 0;
        while (i < n && j < m) {
            if (a.get(i).equals(b.get(j))) {
                ops.add(new DiffOp(DiffOp.Type.EQUAL, a.get(i++)));
                j++;
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                ops.add(new DiffOp(DiffOp.Type.DELETE, a.get(i++)));
            } else {
                ops.add(new DiffOp(DiffOp.Type.INSERT, b.get(j++)));
            }
        }
        while (i < n) ops.add(new DiffOp(DiffOp.Type.DELETE, a.get(i++)));
        while (j < m) ops.add(new DiffOp(DiffOp.Type.INSERT, b.get(j++)));
        return ops;
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
            // Zeile nur zum aktuellen Block hinzufügen
            currentBlock.addLine(line);
        }
        
        blocks.add(currentBlock);
        return blocks;
    }
    
    /**
     * Debug: Zeigt nur ROT und GRÜN Blöcke mit ihren Zeilennummern
     */
    private void optimizeRightContentOrder(VBox rightContentBox) {
        if (rightContentBox.getChildren().size() < 2) return;
        
        boolean changed;
        do {
            changed = false;
            int i = 0;
            while (i < rightContentBox.getChildren().size()) {
                HBox box = (HBox) rightContentBox.getChildren().get(i);
                if (box.getChildren().size() < 2) { i++; continue; }
                Label label = (Label) box.getChildren().get(1);
                boolean isRed = label.getText().isEmpty();
                boolean isGreen = !label.getText().isEmpty() && label.getStyle().contains("d4edda");
                
                if (!isRed) { i++; continue; }
                
                int rStart = i;
                int rEnd = i;
                // erweitere ROT-Lauf
                while (rEnd + 1 < rightContentBox.getChildren().size()) {
                    HBox nextBox = (HBox) rightContentBox.getChildren().get(rEnd + 1);
                    if (nextBox.getChildren().size() < 2) break;
                    Label nextLabel = (Label) nextBox.getChildren().get(1);
                    if (nextLabel.getText().isEmpty()) {
                        rEnd++;
                    } else {
                        break;
                    }
                }
                
                int gStart = rEnd + 1;
                if (gStart >= rightContentBox.getChildren().size()) { i = rEnd + 1; continue; }
                HBox gStartBox = (HBox) rightContentBox.getChildren().get(gStart);
                if (gStartBox.getChildren().size() < 2) { i = rEnd + 1; continue; }
                Label gStartLabel = (Label) gStartBox.getChildren().get(1);
                if (!( !gStartLabel.getText().isEmpty() && gStartLabel.getStyle().contains("d4edda"))) { i = rEnd + 1; continue; }
                
                int gEnd = gStart;
                while (gEnd + 1 < rightContentBox.getChildren().size()) {
                    HBox nb = (HBox) rightContentBox.getChildren().get(gEnd + 1);
                    if (nb.getChildren().size() < 2) break;
                    Label nl = (Label) nb.getChildren().get(1);
                    if (!nl.getText().isEmpty() && nl.getStyle().contains("d4edda")) {
                        gEnd++;
                    } else {
                        break;
                    }
                }
                
                // swap [rStart..rEnd][gStart..gEnd] -> [gStart..gEnd][rStart..rEnd]
                List<HBox> greenBlocks = new ArrayList<>();
                for (int idx = gStart; idx <= gEnd; idx++) {
                    greenBlocks.add((HBox) rightContentBox.getChildren().get(idx));
                }
                // entferne GRÜN-Blöcke (von hinten nach vorne)
                for (int idx = gEnd; idx >= gStart; idx--) {
                    rightContentBox.getChildren().remove(idx);
                }
                // füge GRÜN-Blöcke an rStart ein
                rightContentBox.getChildren().addAll(rStart, greenBlocks);
                
                int redsCount = rEnd - rStart + 1;
                int greensCount = greenBlocks.size();
                i = rStart + redsCount + greensCount;
                changed = true;
            }
        } while (changed);
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
            
            // WICHTIG: Prüfe ob Editor bereits geöffnet ist
            String chapterName = chapterFile.getFileName();
            if (chapterName.toLowerCase().endsWith(".docx")) {
                chapterName = chapterName.substring(0, chapterName.length() - 5);
            }
            String editorKey = chapterName + ".md";
            EditorWindow existingEditor = openEditors.get(editorKey);
            
            if (existingEditor != null) {
                // Editor bereits geöffnet - prüfe auf ungespeicherte Änderungen
                if (existingEditor.hasUnsavedChanges()) {
                    // Dialog: Was tun mit ungespeicherten Änderungen?
                    CustomAlert editorDialog = new CustomAlert(Alert.AlertType.CONFIRMATION, "Editor bereits geöffnet");
                    editorDialog.setHeaderText("Der Editor für '" + chapterFile.getFileName() + "' ist bereits geöffnet.");
                    editorDialog.setContentText("Es gibt ungespeicherte Änderungen. Was möchten Sie tun?");
                    
                    ButtonType btnSaveAndReopen = new ButtonType("Speichern und neu öffnen");
                    ButtonType btnDiscardAndReopen = new ButtonType("Verwerfen und neu öffnen");
                    ButtonType btnJustFocus = new ButtonType("Nur fokussieren");
                    ButtonType btnCancel = new ButtonType("Abbrechen", ButtonBar.ButtonData.CANCEL_CLOSE);
                    
                    editorDialog.setButtonTypes(btnSaveAndReopen, btnDiscardAndReopen, btnJustFocus, btnCancel);
                    
                    // Theme anwenden
                    editorDialog.applyTheme(currentThemeIndex);
                    editorDialog.initOwner(primaryStage);
                    
                    Optional<ButtonType> result = editorDialog.showAndWait();
                    
                    if (result.isPresent()) {
                        switch (result.get().getText()) {
                            case "Speichern und neu öffnen":
                                // Speichern und dann neu öffnen
                                existingEditor.saveFile();
                                // Editor schließen und neu öffnen
                                existingEditor.closeWindow();
                                openEditors.remove(editorKey);
                                // WICHTIG: Neuen Editor mit gespeichertem Inhalt öffnen
                                // Lade den gespeicherten Inhalt aus der MD-Datei
                                try {
                                    File mdFile = deriveMdFileFor(chapterFile.getFile());
                                    if (mdFile != null && mdFile.exists()) {
                                        String savedContent = new String(java.nio.file.Files.readAllBytes(mdFile.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                                        return openChapterEditorWindow(savedContent, chapterFile, format);
                                    }
                                } catch (Exception e) {
                                    logger.error("Fehler beim Laden des gespeicherten Inhalts: {}", e.getMessage());
                                }
                                // Fallback: Neuen Editor mit ursprünglichem Inhalt
                                return openChapterEditorWindow(text, chapterFile, format);
                            case "Verwerfen und neu öffnen":
                                // Editor schließen ohne zu speichern
                                existingEditor.closeWindow();
                                openEditors.remove(editorKey);
                                break;
                            case "Nur fokussieren":
                                // Bestehenden Editor in den Vordergrund bringen
                                Platform.runLater(() -> {
                                    for (Window window : Window.getWindows()) {
                                        if (window instanceof CustomStage && window.isShowing()) {
                                            CustomStage customStage = (CustomStage) window;
                                            if (customStage.getTitle().contains(chapterFile.getFileName())) {
                                                customStage.toFront();
                                                customStage.requestFocus();
                                                break;
                                            }
                                        }
                                    }
                                });
                                return existingEditor; // Bestehenden Editor zurückgeben
                            case "Abbrechen":
                            default:
                                return existingEditor; // Bestehenden Editor zurückgeben
                        }
                    } else {
                        return existingEditor; // Bestehenden Editor zurückgeben
                    }
                } else {
                    // Keine ungespeicherten Änderungen - alten Editor einfach schließen und neuen erstellen
                    existingEditor.closeWindow();
                    openEditors.remove(editorKey);
                    // Weiter mit der normalen Editor-Erstellung
                }
            }
            
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/editor.fxml"));
            Parent root = loader.load();
            
            EditorWindow editorController = loader.getController();
            editorController.setText(text);
            editorController.setOutputFormat(format);
            
            // Erstelle Datei-Referenz für das Kapitel
            String chapterNameForFile = chapterFile.getFileName();
            if (chapterNameForFile.toLowerCase().endsWith(".docx")) {
                chapterNameForFile = chapterNameForFile.substring(0, chapterNameForFile.length() - 5);
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
            File chapterFileRef = new File(dataDir, chapterNameForFile + fileExtension);
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
            
            // Scene erstellen und mit CustomStage-Titelleiste setzen
            Scene scene = new Scene(root);
            editorStage.setSceneWithTitleBar(scene);
            
            // WICHTIG: Theme sofort setzen für Border
            editorStage.setTitleBarTheme(currentThemeIndex);
            
            // CSS-Klasse für Border auf innerem Container hinzufügen
            // Suche nach dem Haupt-Container (meist VBox oder BorderPane)
            if (root instanceof javafx.scene.layout.VBox) {
                root.getStyleClass().add("editor-container");
                root.getStyleClass().add("theme-" + currentThemeIndex);
            } else {
                // Falls es ein anderes Layout ist, suche nach dem ersten Container
                for (javafx.scene.Node child : root.getChildrenUnmodifiable()) {
                    if (child instanceof javafx.scene.Parent) {
                        child.getStyleClass().add("editor-container");
                        child.getStyleClass().add("theme-" + currentThemeIndex);
                        break;
                    }
                }
            }
            
            // EditorWindow-Instanz mit UserData verknüpfen für spätere Suche
            root.setUserData(editorController);
            
            // Editor in Map speichern für spätere Suche
            String editorKeyForMap = chapterName + ".md";
            openEditors.put(editorKeyForMap, editorController);
            
            // WICHTIG: EditorWindow übernimmt die Fenster-Eigenschaften
            // EditorWindow.loadWindowProperties() wird automatisch aufgerufen
            
            // CSS mit ResourceManager laden
            String cssPath = ResourceManager.getCssResource("css/manuskript.css");
            if (cssPath != null) {
                scene.getStylesheets().add(cssPath);
            }
            
            // WICHTIG: Stage setzen für Close-Request-Handler
            editorController.setStage(editorStage);
            
            // Cleanup-Handler für Editor-Map - NACH setStage, damit der Speichern-Handler funktioniert
            final String finalEditorKey = chapterName + ".md";
            // Verwende addEventHandler statt setOnCloseRequest, um den bestehenden Handler nicht zu überschreiben
            editorStage.addEventHandler(WindowEvent.WINDOW_CLOSE_REQUEST, e -> {
                // Nur aus Map entfernen, wenn das Fenster wirklich geschlossen wird
                // (nicht wenn der Speichern-Dialog das Schließen verhindert)
                Platform.runLater(() -> {
                    if (!editorStage.isShowing()) {
                        openEditors.remove(finalEditorKey);
                    }
                });
            });
            
            editorStage.show();
            
            return editorController;
            
        } catch (Exception e) {
            logger.error("Fehler beim Öffnen des Kapitel-Editor-Fensters", e);
            showError("Fenster-Fehler", e.getMessage());
            return null;
        }
    }
    
    private void processCompleteDocument(ObservableList<DocxFile> files) {
        // Buch exportieren (wie bisher)
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
                result.append("        body { font-family: Arial, sans-serif; line-height: 1.6; margin: 0; padding: 2em 4em; max-width: 1200px; margin-left: auto; margin-right: auto; }\n");
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
                
                // Finde die entsprechende MD-Datei
                File mdFile = deriveMdFileFor(docxFile.getFile());
                if (mdFile != null && mdFile.exists()) {
                    try {
                        // Lade MD-Inhalt
                        String mdContent = Files.readString(mdFile.toPath(), StandardCharsets.UTF_8);
                        
                        // Prüfe ob MD-Datei bereits mit Überschrift beginnt
                        String trimmedContent = mdContent.trim();
                        boolean hasHeading = trimmedContent.startsWith("#");
                        
                        if (!hasHeading) {
                            // Erstelle Kapitel-Überschrift nur wenn keine vorhanden
                            String chapterName = docxFile.getFileName().replace(".docx", "");
                            result.append("## ").append(chapterName).append("\n\n");
                        }
                        
                        // MD-Inhalt formatieren (Leerzeilen zwischen Absätzen sicherstellen)
                        String formattedContent = formatMarkdownParagraphs(mdContent);

                        // Füge MD-Inhalt hinzu
                        result.append(formattedContent).append("\n\n");
                
                processed++;
                        
                    } catch (Exception e) {
                        logger.error("Fehler beim Laden der MD-Datei: {}", mdFile.getName(), e);
                        // Fallback: Konvertiere DOCX zu MD
                        String content = docxProcessor.processDocxFileContent(docxFile.getFile(), processed + 1, format);
                        String formattedContent = formatMarkdownParagraphs(content);
                        result.append("## ").append(docxFile.getFileName().replace(".docx", "")).append("\n\n");
                        result.append(formattedContent).append("\n\n");
                processed++;
                    }
                } else {
                    logger.warn("Keine MD-Datei gefunden für: {}", docxFile.getFileName());
                    // Fallback: Konvertiere DOCX zu MD
                    String content = docxProcessor.processDocxFileContent(docxFile.getFile(), processed + 1, format);
                    String formattedContent = formatMarkdownParagraphs(content);
                    result.append("## ").append(docxFile.getFileName().replace(".docx", "")).append("\n\n");
                    result.append(formattedContent).append("\n\n");
                    processed++;
                }
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
            
            // Erstelle das Buch als echte Datei
            String currentDirectory = txtDirectoryPath.getText();
            File completeDocumentFile = new File(currentDirectory, baseFileName + " Buch.md");
            
            
            try {
                // Schreibe das Buch in die Datei
                Files.write(completeDocumentFile.toPath(), result.toString().getBytes(StandardCharsets.UTF_8));
                
                updateStatus(processed + " Dateien erfolgreich verarbeitet - Buch erstellt: " + completeDocumentFile.getName());

                // Pandoc Export Dialog anbieten (ohne Editor zu öffnen)
                showPandocExportDialog(completeDocumentFile, baseFileName);
                
            } catch (Exception e) {
                logger.error("Fehler beim Erstellen des Buches", e);
                showError("Fehler", "Konnte Buch nicht erstellen: " + e.getMessage());
            }
            
        } catch (Exception e) {
            logger.error("Fehler bei der Verarbeitung", e);
            showError("Verarbeitungsfehler", e.getMessage());
            updateStatus("Fehler bei der Verarbeitung");
        }
    }
    
    private void openEditorWithFile(File file, boolean isCompleteDocument) {
        try {
            // Debug entfernt
            
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/editor.fxml"));
            Parent root = loader.load();
            
            EditorWindow editorController = loader.getController();
            
            // Lade den Inhalt der Datei
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            // Debug entfernt
            
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
            Scene scene = new Scene(root);
            editorStage.setScene(scene);
            
            // EditorWindow-Instanz mit UserData verknüpfen für spätere Suche
            root.setUserData(editorController);
            editorStage.setWidth(1200);
            editorStage.setHeight(800);
            editorStage.setMinWidth(1000);
            editorStage.setMinHeight(700);
            editorStage.initModality(Modality.NONE);
            
            // Theme anwenden
            editorController.applyTheme(currentThemeIndex);
            
            // Zeige den Editor
            editorStage.show();
            
            // Status wird bereits in der setText-Methode gesetzt
            
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
            // Da es ein Buch ist, verwenden wir die erste Datei als Referenz
            if (!selectedDocxFiles.isEmpty()) {
                File firstDocxFile = selectedDocxFiles.get(0).getFile();
                editorController.setOriginalDocxFile(firstDocxFile);
            }
            
            // WICHTIG: Setze das aktuelle Theme vom Hauptfenster auf das Editorfenster
            editorController.setThemeFromMainWindow(currentThemeIndex);
            
            // WICHTIG: Setze die Referenz zum MainController für Navigation
            editorController.setMainController(this);
            
            // NEU: Titelbalken für Buch setzen
            editorController.setWindowTitle("📚 Buch: " + baseFileName);
            
            CustomStage editorStage = StageManager.createStage("Buch: " + baseFileName);
            
            // Scene erstellen und mit CustomStage-Titelleiste setzen
            Scene scene = new Scene(root);
            editorStage.setSceneWithTitleBar(scene);
            
            // WICHTIG: Theme sofort setzen für Border
            editorStage.setTitleBarTheme(currentThemeIndex);
            
            // CSS-Klasse für Border auf innerem Container hinzufügen
            // Suche nach dem Haupt-Container (meist VBox oder BorderPane)
            if (root instanceof javafx.scene.layout.VBox) {
                root.getStyleClass().add("editor-container");
                root.getStyleClass().add("theme-" + currentThemeIndex);
            } else {
                // Falls es ein anderes Layout ist, suche nach dem ersten Container
                for (javafx.scene.Node child : root.getChildrenUnmodifiable()) {
                    if (child instanceof javafx.scene.Parent) {
                        child.getStyleClass().add("editor-container");
                        child.getStyleClass().add("theme-" + currentThemeIndex);
                        break;
                    }
                }
            }
            
            // EditorWindow-Instanz mit UserData verknüpfen für spätere Suche
            root.setUserData(editorController);
            
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
    }
    
    private void showError(String title, String message) {
        CustomAlert alert = new CustomAlert(Alert.AlertType.ERROR, title);
        alert.setContentText(message);
        // alert.setHeaderText(null); // ENTFERNT: Setzt 'null' String
        alert.applyTheme(currentThemeIndex);
        alert.initOwner(primaryStage);
        alert.showAndWait();
    }
    
    private void showInfo(String title, String message) {
        CustomAlert alert = new CustomAlert(Alert.AlertType.INFORMATION, title);
        alert.setContentText(message);
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
    
    /**
     * Schaltet den Downloads-Monitor ein/aus
     */
    private void toggleDownloadsMonitor() {
        if (chkDownloadsMonitor.isSelected()) {
            // Downloads-Monitor aktivieren
            if (downloadsDirectory == null) {
                // Erstes Mal - Downloads-Verzeichnis auswählen
                showDownloadsDirectoryDialog();
            } else {
                // Verzeichnis bereits gesetzt - Dialog trotzdem anzeigen für Änderung
                showDownloadsDirectoryDialog();
            }
            
            if (downloadsDirectory != null) {
                startDownloadsMonitor();
                updateStatus("Downloads-Monitor aktiviert: " + downloadsDirectory.getAbsolutePath());
            } else {
                // Benutzer hat abgebrochen
                chkDownloadsMonitor.setSelected(false);
            }
        } else {
            // Downloads-Monitor deaktivieren
            stopDownloadsMonitor();
            updateStatus("Downloads-Monitor deaktiviert");
        }
    }
    
    /**
     * Zeigt Dialog zur Auswahl des Downloads-Verzeichnisses
     */
    private void showDownloadsDirectoryDialog() {
        // Checkbox erstellen
        CheckBox chkCopyAllDocx = new CheckBox("Alle DOCX-Dateien kopieren (ohne Namensvergleich)");
        chkCopyAllDocx.setStyle("-fx-font-size: 12px; -fx-padding: 5px;");
        
        // VBox mit Text und Checkbox erstellen
        VBox customContentBox = new VBox(10);
        Label textLabel = new Label(
            "Der Downloads-Monitor überwacht automatisch das Downloads-Verzeichnis auf neue Dateien.\n\n" +
            "📄 DOCX-Dateien:\n" +
            "• Neue Dateien werden automatisch erkannt\n" +
            "• Passende Dateien werden automatisch ersetzt\n" +
            "• Alte Dateien werden als Backup gesichert\n\n" +
            "📦 Sudowrite-ZIP-Export (export as xx docs):\n" +
            "• ZIP-Dateien mit Projektnamen werden automatisch erkannt\n" +
            "• Beispiel: 'Mein_Projekt.zip' für Projekt 'Mein Projekt'\n" +
            "• ZIP wird entpackt und DOCX-Dateien ins Projekt kopiert\n" +
            "• Original ZIP wird nach Import gelöscht\n\n" +
            "Bitte wählen Sie das Downloads-Verzeichnis aus:"
        );
        textLabel.setWrapText(true);
        textLabel.setStyle("-fx-font-size: 12px;");
        
        customContentBox.getChildren().addAll(textLabel, chkCopyAllDocx);
        
        // Dialog mit Custom Content
        CustomAlert infoAlert = new CustomAlert(Alert.AlertType.CONFIRMATION, "📥 Downloads-Monitor");
        infoAlert.setHeaderText("Downloads-Monitor einrichten");
        
        // Custom Content setzen (das ist der Trick!)
        infoAlert.setCustomContent(customContentBox);
        infoAlert.applyTheme(currentThemeIndex);
        infoAlert.initOwner(primaryStage);
        
        // Warnung bei "Alle DOCX kopieren"
        chkCopyAllDocx.setOnAction(e -> {
            if (chkCopyAllDocx.isSelected()) {
                CustomAlert warningAlert = new CustomAlert(Alert.AlertType.WARNING, "⚠️ Warnung");
                warningAlert.setHeaderText("Alle DOCX-Dateien werden kopiert");
                warningAlert.setContentText(
                    "ACHTUNG: Wenn diese Option aktiviert ist, werden ALLE DOCX-Dateien aus dem Downloads-Verzeichnis " +
                    "in Ihr Projektverzeichnis kopiert - OHNE Namensvergleich!\n\n" +
                    "• Alle DOCX-Dateien werden verschoben\n" +
                    "• Keine Überprüfung auf passende Namen\n" +
                    "• Möglicherweise unerwünschte Dateien\n\n" +
                    "Sind Sie sicher, dass Sie fortfahren möchten?"
                );
                warningAlert.applyTheme(currentThemeIndex);
                warningAlert.initOwner(primaryStage);
                Optional<ButtonType> warningResult = warningAlert.showAndWait();
                if (warningResult.isEmpty() || warningResult.get() != ButtonType.OK) {
                    chkCopyAllDocx.setSelected(false);
                }
            }
        });
        
        Optional<ButtonType> result = infoAlert.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            // Abgebrochen - Checkbox wieder deaktivieren
            chkDownloadsMonitor.setSelected(false);
            updateStatus("Downloads-Monitor deaktiviert");
            return;
        }
        
        // DirectoryChooser mit besserem Styling
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("📁 Downloads-Verzeichnis auswählen");
        
        // Vorheriges Downloads-Verzeichnis vorausfüllen falls vorhanden
        String lastDownloadsDir = preferences.get("downloads_directory", null);
        if (lastDownloadsDir != null) {
            File lastDir = new File(lastDownloadsDir);
            if (lastDir.exists()) {
                directoryChooser.setInitialDirectory(lastDir);
            } else {
                // Fallback auf Standard-Downloads-Verzeichnis
                String userHome = System.getProperty("user.home");
                File defaultDownloads = new File(userHome, "Downloads");
                if (defaultDownloads.exists()) {
                    directoryChooser.setInitialDirectory(defaultDownloads);
                }
            }
        } else {
            // Standard-Downloads-Verzeichnis vorschlagen
            String userHome = System.getProperty("user.home");
            File defaultDownloads = new File(userHome, "Downloads");
            if (defaultDownloads.exists()) {
                directoryChooser.setInitialDirectory(defaultDownloads);
            }
        }
        
        File selectedDirectory = directoryChooser.showDialog(primaryStage);
        if (selectedDirectory != null) {
            downloadsDirectory = selectedDirectory;
            // Backup-Verzeichnis im aktuellen Arbeitsverzeichnis erstellen
            backupDirectory = new File(txtDirectoryPath.getText(), "backup");
            if (!backupDirectory.exists()) {
                backupDirectory.mkdirs();
            }
            
            // Einstellungen speichern (Checkbox-Wert aus dem Dialog)
            preferences.put("downloads_directory", downloadsDirectory.getAbsolutePath());
            preferences.put("backup_directory", backupDirectory.getAbsolutePath());
            preferences.put("copy_all_docx", String.valueOf(chkCopyAllDocx.isSelected()));
            try {
                preferences.flush();
            } catch (Exception e) {
                logger.warn("Konnte Downloads-Einstellungen nicht speichern: {}", e.getMessage());
            }
            
            // Erfolgs-Dialog mit Bestätigung
            CustomAlert successAlert = new CustomAlert(Alert.AlertType.CONFIRMATION, "✅ Downloads-Monitor aktiviert");
            successAlert.setHeaderText("Downloads-Monitor erfolgreich eingerichtet");
            successAlert.setContentText(
                "Downloads-Verzeichnis: " + downloadsDirectory.getAbsolutePath() + "\n" +
                "Backup-Verzeichnis: " + backupDirectory.getAbsolutePath() + "\n\n" +
                "Der Monitor überwacht jetzt alle 5 Sekunden auf neue DOCX-Dateien."
            );
            successAlert.applyTheme(currentThemeIndex);
            successAlert.initOwner(primaryStage);
            successAlert.showAndWait();
        } else {
            // Benutzer hat abgebrochen
            CustomAlert cancelAlert = new CustomAlert(Alert.AlertType.WARNING, "❌ Downloads-Monitor abgebrochen");
            cancelAlert.setHeaderText("Downloads-Monitor nicht aktiviert");
            cancelAlert.setContentText("Sie können den Downloads-Monitor jederzeit über die Checkbox aktivieren.");
            cancelAlert.applyTheme(currentThemeIndex);
            cancelAlert.initOwner(primaryStage);
            cancelAlert.showAndWait();
        }
    }
    
    /**
     * Startet den Downloads-Monitor
     */
    private void startDownloadsMonitor() {
        if (isMonitoring.get()) {
            logger.warn("Downloads-Monitor bereits aktiv");
            return; // Bereits aktiv
        }
        
        isMonitoring.set(true);
        downloadsMonitorTimer = new Timer("DownloadsMonitor", true);
        
        // Alle 5 Sekunden prüfen
        downloadsMonitorTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                checkForNewFiles();
            }
        }, 0, 5000); // Sofort starten, dann alle 5 Sekunden
        
    }
    
    /**
     * Stoppt den Downloads-Monitor
     */
    private void stopDownloadsMonitor() {
        if (downloadsMonitorTimer != null) {
            downloadsMonitorTimer.cancel();
            downloadsMonitorTimer = null;
        }
        isMonitoring.set(false);
    }
    
    /**
     * Prüft auf neue Dateien im Downloads-Verzeichnis
     */
    private void checkForNewFiles() {
        
        if (!isMonitoring.get() || downloadsDirectory == null) {
            logger.warn("Downloads-Monitor nicht aktiv oder Verzeichnis null");
            logger.warn("isMonitoring: {}, downloadsDirectory: {}", isMonitoring.get(), downloadsDirectory);
            return;
        }
        
        // Sudowrite-ZIP-Import prüfen
        String currentDirPath = txtDirectoryPath.getText();
        if (currentDirPath != null && !currentDirPath.isEmpty()) {
            File projectDir = new File(currentDirPath);
            if (projectDir.exists()) {
                checkAndImportSudowriteFiles(projectDir);
            }
        }
        
        // WICHTIG: Nur neue Dateien hinzufügen, nicht alles neu laden
        addNewDocxFiles(new File(txtDirectoryPath.getText()));
        
        try {
            
            File[] files = downloadsDirectory.listFiles((dir, name) -> 
                name.toLowerCase().endsWith(".docx") && new File(dir, name).isFile());
            
            
            if (files == null) {
                logger.warn("Keine Dateien im Downloads-Verzeichnis - listFiles() returned null");
                return;
            }
            
            if (files.length == 0) {
                return;
            }
            
            // Prüfe ob "Alle DOCX kopieren" aktiviert ist
            boolean copyAllDocx = Boolean.parseBoolean(preferences.get("copy_all_docx", "false"));
            
            for (int i = 0; i < files.length; i++) {
                File downloadFile = files[i];
                
                // Prüfe ob diese Datei gesperrt ist (ignoriere sie)
                String downloadFileName = downloadFile.getName();
                Long lockTime = lockedFiles.get(downloadFileName);
                if (lockTime != null) {
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lockTime < LOCK_TIMEOUT) {
                        continue;
                    } else {
                        // Sperre ist abgelaufen - entferne sie
                        lockedFiles.remove(downloadFileName);
                    }
                }
                
                // Prüfe ob Datei vollständig ist (nicht mehr geschrieben wird)
                if (isFileComplete(downloadFile)) {
                    
                    if (copyAllDocx) {
                        // Alle DOCX-Dateien kopieren ohne Namensvergleich
                        Platform.runLater(() -> {
                            try {
                                copyAllDocxFile(downloadFile);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        });
                    } else {
                        // Normale Logik mit Namensvergleich
                        String fileName = downloadFile.getName();
                        String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
                        
                        // Suche in ALLEN Dateien im Verzeichnis (nicht nur in allDocxFiles)
                        boolean found = false;
                        File currentDir = new File(txtDirectoryPath.getText());
                        if (currentDir.exists()) {
                            File[] allFiles = currentDir.listFiles((dir, name) -> 
                                name.toLowerCase().endsWith(".docx") && new File(dir, name).isFile());
                            
                            if (allFiles != null) {
                                for (File existingFile : allFiles) {
                                    String existingName = existingFile.getName();
                                    if (existingName.toLowerCase().endsWith(".docx")) {
                                        existingName = existingName.substring(0, existingName.lastIndexOf('.'));
                                    }
                                    
                                    
                                    // Name-Vergleich (ignoriere Groß-/Kleinschreibung und normalisiere Leerzeichen und Unterstriche)
                                    String normalizedBaseName = baseName.trim().replaceAll("[\\s_]+", " ");
                                    String normalizedExistingName = existingName.trim().replaceAll("[\\s_]+", " ");
                                    if (normalizedBaseName.equalsIgnoreCase(normalizedExistingName)) {
                                        // Datei gefunden - verschieben
                                        Platform.runLater(() -> replaceFileWithDownload(existingFile, downloadFile));
                                        found = true;
                                        break;
                                    }
                                }
                            }
                        }
                        
                        }
                }
            }
        } catch (Exception e) {
            logger.error("Fehler beim Prüfen der Downloads: {}", e.getMessage(), e);
        }
        
    }
    
    /**
     * Prüft ob eine Datei vollständig ist (nicht mehr geschrieben wird)
     */
    private boolean isFileComplete(File file) {
        try {
            long size1 = file.length();
            // Vereinfachte Prüfung: nur Größe > 0
            boolean isComplete = size1 > 0;
            return isComplete;
        } catch (Exception e) {
            logger.error("Fehler beim Prüfen der Datei-Vollständigkeit: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Lädt die Downloads-Monitor-Einstellungen
     */
    private void loadDownloadsMonitorSettings() {
        String downloadsDir = preferences.get("downloads_directory", null);
        String backupDir = preferences.get("backup_directory", null);
        
        if (downloadsDir != null && backupDir != null) {
            downloadsDirectory = new File(downloadsDir);
            backupDirectory = new File(backupDir);
            
            // Prüfe ob Verzeichnisse noch existieren
            if (downloadsDirectory.exists() && backupDirectory.exists()) {
                // Automatisch aktivieren wenn Verzeichnisse noch existieren
                chkDownloadsMonitor.setSelected(true);
                startDownloadsMonitor();
                updateStatus("Downloads-Monitor automatisch aktiviert");
            } else {
                // Verzeichnisse nicht mehr vorhanden - Einstellungen zurücksetzen
                preferences.remove("downloads_directory");
                preferences.remove("backup_directory");
                try {
                    preferences.flush();
                } catch (Exception e) {
                    logger.warn("Konnte Downloads-Einstellungen nicht zurücksetzen: {}", e.getMessage());
                }
            }
        }
    }
    
    /**
     * Kopiert alle DOCX-Dateien ohne Namensvergleich
     */
    private void copyAllDocxFile(File downloadFile) {
        try {
            File targetFile = new File(txtDirectoryPath.getText(), downloadFile.getName());
            
            
            // Prüfe ob Datei bereits existiert (nach Sperrprüfung)
            if (targetFile.exists()) {
                // Backup der alten Datei erstellen
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
                String backupFileName = targetFile.getName().replace(".docx", "_" + timestamp + ".docx");
                File backupFile = new File(backupDirectory, backupFileName);
                
                // Alte Datei nach Backup verschieben
                try {
                    Files.move(targetFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception backupException) {
                    // Datei ist gesperrt - kann nicht verschoben werden
                    Platform.runLater(() -> {
                        CustomAlert alert = new CustomAlert(CustomAlert.AlertType.ERROR);
                        alert.setTitle("Datei gesperrt");
                        alert.setHeaderText("Die Zieldatei ist gesperrt");
                        alert.setContentText("Die Datei '" + targetFile.getName() + "' ist möglicherweise in Word oder einem anderen Programm geöffnet.\n\nBitte schließen Sie die Datei und versuchen Sie es erneut.");
                        alert.applyTheme(currentThemeIndex);
                        alert.initOwner(primaryStage);
                        alert.showAndWait();
                    });
                    
                    // Import fehlgeschlagen - Datei zur gesperrten Liste hinzufügen
                    String lockedFileName = targetFile.getName();
                    lockedFiles.put(lockedFileName, System.currentTimeMillis());
                    return;
                }
            }
            
            // Download-Datei an Zielort verschieben
            try {
                Files.move(downloadFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception moveException) {
                // Prüfe ob es ein Sperrfehler ist
                if (moveException.getMessage().contains("being used by another process") || 
                    moveException.getMessage().contains("cannot access") ||
                    moveException.getMessage().contains("Access is denied")) {
                    Platform.runLater(() -> {
                        CustomAlert alert = new CustomAlert(CustomAlert.AlertType.ERROR);
                        alert.setTitle("Datei gesperrt");
                        alert.setHeaderText("Die Zieldatei ist gesperrt");
                        alert.setContentText("Die Datei '" + targetFile.getName() + "' ist möglicherweise in Word oder einem anderen Programm geöffnet.\n\nBitte schließen Sie die Datei und versuchen Sie es erneut.");
                        alert.applyTheme(currentThemeIndex);
                        alert.initOwner(primaryStage);
                        alert.showAndWait();
                    });
                    return;
                } else {
                    // Anderer Fehler - weiterwerfen
                    throw moveException;
                }
            }
            
            // Datei-Liste aktualisieren
            Platform.runLater(() -> {
                File currentDir = new File(txtDirectoryPath.getText());
                if (currentDir.exists()) {
                    loadDocxFiles(currentDir);
                }
                updateStatus("Alle DOCX kopiert: " + downloadFile.getName());
            });
            
            // WICHTIG: Lösche die Download-Datei um Endlosschleife zu vermeiden
            if (downloadFile.exists()) {
                downloadFile.delete();
            }
            
        } catch (Exception e) {
            logger.error("Fehler beim Kopieren aller DOCX: {}", e.getMessage(), e);
            Platform.runLater(() -> updateStatus("Fehler beim Kopieren: " + e.getMessage()));
        }
    }
    
    /**
     * Ersetzt eine vorhandene Datei mit der Download-Datei
     */
    private void replaceFileWithDownload(File existingFile, File downloadFile) {
        try {
            File targetFile = existingFile;
            
            // Backup der alten Datei erstellen
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            String backupFileName = targetFile.getName().replace(".docx", "_" + timestamp + ".docx");
            File backupFile = new File(backupDirectory, backupFileName);
            
            // Alte Datei nach Backup verschieben
            Files.move(targetFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            
            // Download-Datei an Zielort verschieben
            Files.move(downloadFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            
            // Datei-Liste aktualisieren
            Platform.runLater(() -> {
                File currentDir = new File(txtDirectoryPath.getText());
                if (currentDir.exists()) {
                    loadDocxFiles(currentDir);
                }
                updateStatus("Datei ersetzt: " + targetFile.getName() + " (Backup: " + backupFileName + ")");
            });
            
                
        } catch (Exception e) {
            logger.error("Fehler beim Ersetzen der Datei: {}", e.getMessage());
            Platform.runLater(() -> {
                updateStatus("Fehler beim Ersetzen der Datei: " + e.getMessage());
            });
        }
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
            
            // Stoppe den Downloads-Monitor
            stopDownloadsMonitor();
            
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
                        } else {
                            // Datei hat keine MD-Datei → nach links (allDocxFiles)
                            allDocxFiles.add(docxFile);
                        }
                        
                        break;
                    }
                }
            }
            
            
        } catch (Exception e) {
            logger.warn("Fehler beim Laden der gespeicherten Reihenfolge", e);
        }
    }
    

    
    /**
     * Wechselt das Help-System ein/aus
     */
    private void toggleHelp() {
        helpEnabled = !helpEnabled;
        
        // In Preferences speichern
        preferences.putBoolean("help_enabled", helpEnabled);
        
        // Button-Text aktualisieren
        updateHelpButtonIcon();
        
        // Help-Buttons in allen Fenstern aktualisieren
        HelpSystem.setHelpEnabled(helpEnabled);
        
        updateStatus("Hilfe " + (helpEnabled ? "eingeschaltet" : "ausgeschaltet"));
    }
    
    /**
     * Aktualisiert das Help-Button Icon
     */
    private void updateHelpButtonIcon() {
        if (btnHelpToggle != null) {
            if (helpEnabled) {
                btnHelpToggle.setText("❓");
                btnHelpToggle.setTooltip(new Tooltip("Hilfe ausschalten"));
            } else {
                btnHelpToggle.setText("❌");
                btnHelpToggle.setTooltip(new Tooltip("Hilfe einschalten"));
            }
        }
    }
    
    /**
     * Lädt Help-Einstellung aus Preferences
     */
    private void loadHelpSettings() {
        helpEnabled = preferences.getBoolean("help_enabled", true);
        updateHelpButtonIcon();
        HelpSystem.setHelpEnabled(helpEnabled);
    }
    
    /**
     * Erstellt den Help-Toggle-Button programmatisch
     */
    private void createHelpToggleButton() {
        btnHelpToggle = new Button("❓");
        btnHelpToggle.setPrefSize(40, 40);
        btnHelpToggle.getStyleClass().add("help-toggle-button");
        btnHelpToggle.setOnAction(e -> toggleHelp());
        
        // Button zum Layout hinzufügen (neben Theme-Button)
        if (btnThemeToggle != null && btnThemeToggle.getParent() != null) {
            HBox parentBox = (HBox) btnThemeToggle.getParent();
            parentBox.getChildren().add(btnHelpToggle);
        }
        
        updateHelpButtonIcon();
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
                    tableViewAvailable.setStyle("-fx-border-color: #ba68c8; -fx-selection-bar: #ba68c8; -fx-selection-bar-non-focused: #ba68c8;");
                    tableViewSelected.setStyle("-fx-border-color: #ba68c8; -fx-selection-bar: #ba68c8; -fx-selection-bar-non-focused: #ba68c8;");
                });
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
            } else {
                logger.warn("CSS-Datei manuskript.css nicht gefunden!");
            }
            
            // TableViews über CSS stylen - nur Border setzen, Rest über CSS
            if (themeIndex != 2) { // Nicht für Pastell-Theme, das wird separat behandelt
                tableViewAvailable.setStyle("-fx-border-color: #ba68c8; -fx-selection-bar: #ba68c8; -fx-selection-bar-non-focused: #ba68c8;");
                tableViewSelected.setStyle("-fx-border-color: #ba68c8; -fx-selection-bar: #ba68c8; -fx-selection-bar-non-focused: #ba68c8;");
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
        
        // Validierung der Fenster-Größe (nur Mindestgrößen)
        double minWidth = 1000.0;
        double minHeight = 600.0;
        // KEINE maxWidth/maxHeight Begrenzung für ultrawide Monitore
        
        // Größe validieren und korrigieren (nur Mindestgrößen)
        if (width < minWidth || Double.isNaN(width) || Double.isInfinite(width)) {
            logger.warn("Ungültige Hauptfenster-Breite: {} - verwende Standard: {}", width, minWidth);
            width = minWidth;
        }
        if (height < minHeight || Double.isNaN(height) || Double.isInfinite(height)) {
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
        
    }
    
    
    /**
     * Setzt alle Editor-Fenster-Preferences auf Standardwerte zurück
     */
    public void resetEditorWindowPreferences() {
        PreferencesManager.resetEditorWindowPreferences(preferences);
    }
    
    /**
     * Zeigt Dialog für existierende Buch-Dateien
     * ENTFERNT: Dialog wird nicht mehr angezeigt, Buch wird direkt erstellt
     */
    private void showGesamtFileDialog(File[] existingGesamtFiles, ObservableList<DocxFile> selectedDocxFiles) {
        // Dialog entfernt - Buch wird direkt erstellt
        processCompleteDocument(selectedDocxFiles);
    }
    
    /**
     * Zeigt den Pandoc Export Dialog
     */
    private void showPandocExportDialog(File markdownFile, String projectName) {
        try {
            PandocExportWindow exportWindow = new PandocExportWindow(markdownFile, projectName);
            exportWindow.setFullTheme(currentThemeIndex);
            exportWindow.show();
        } catch (Exception e) {
            logger.error("Fehler beim Öffnen des Pandoc Export Dialogs", e);
            showError("Fehler", "Konnte Pandoc Export Dialog nicht öffnen: " + e.getMessage());
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
            
            // Öffne den Spezialeditor für Bücher
            openEditorWithFile(gesamtFile, true);
            updateStatus("Buch geladen: " + gesamtFile.getName());
            
        } catch (Exception e) {
            logger.error("Fehler beim Laden des Buches", e);
            showError("Ladefehler", "Fehler beim Laden der Datei: " + e.getMessage());
            updateStatus("Fehler beim Laden des Buches");
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
            
            // WICHTIG: Theme sofort setzen
            splitStage.setTitleBarTheme(currentThemeIndex);
            splitStage.initModality(Modality.NONE);
            splitStage.initOwner(primaryStage);
            
            // Split-Panel programmatisch erstellen (wie Makros!)
            VBox splitPanel = createSplitPanel();
            
            Scene splitScene = new Scene(splitPanel);
            // CSS mit ResourceManager laden (manuskript.css für alle Themes)
            String manuskriptCss = ResourceManager.getCssResource("css/manuskript.css");
            if (manuskriptCss != null) {
                splitScene.getStylesheets().add(manuskriptCss);
            } else {
                logger.error("CSS konnte nicht geladen werden!");
            }
            splitStage.setSceneWithTitleBar(splitScene);
            
            splitStage.centerOnScreen();
            splitStage.show();
            
            
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
        
        // Help-Button für Datei-Auswahl
        Button fileHelpButton = HelpSystem.createHelpButton(
            "Hilfe zur Datei-Auswahl",
            "chapter_split.html"
        );
        
        final TextField txtFilePath = new TextField();
        txtFilePath.setPromptText("DOCX oder TXT-Datei auswählen...");
        txtFilePath.setPrefWidth(400);
        txtFilePath.setEditable(false);
        
        Button btnSelectFile = new Button("Datei auswählen");
        btnSelectFile.getStyleClass().addAll("button", "primary");
        
        fileHBox.getChildren().addAll(fileHelpButton, txtFilePath, btnSelectFile);
        fileBox.getChildren().addAll(fileLabel, fileHBox);
        
        // Ausgabe-Verzeichnis
        VBox outputBox = new VBox(10);
        Label outputLabel = new Label("2. Ausgabe-Verzeichnis:");
        outputLabel.getStyleClass().add("section-title");
        
        HBox outputHBox = new HBox(10);
        outputHBox.setAlignment(Pos.CENTER_LEFT);
        
        // Help-Button für Ausgabe-Verzeichnis
        Button outputHelpButton = HelpSystem.createHelpButton(
            "Hilfe zum Ausgabe-Verzeichnis",
            "chapter_split.html"
        );
        
        final TextField txtOutputPath = new TextField();
        txtOutputPath.setPromptText("Verzeichnis für Kapitel-Dateien...");
        txtOutputPath.setPrefWidth(400);
        txtOutputPath.setEditable(false);
        
        Button btnSelectOutput = new Button("Verzeichnis auswählen");
        btnSelectOutput.getStyleClass().addAll("button", "secondary");
        
        outputHBox.getChildren().addAll(outputHelpButton, txtOutputPath, btnSelectOutput);
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
            fileChooser.setTitle("Datei auswählen (DOCX oder RTF)");
            fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("DOCX-Dateien", "*.docx"),
                new FileChooser.ExtensionFilter("RTF-Dateien", "*.rtf"),
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
                
                // Datei analysieren und Kapitel extrahieren (DOCX oder RTF)
                try {
                    listChapters.getItems().clear();
                    
                    String fileName = selectedFile.getName().toLowerCase();
                    if (fileName.endsWith(".docx")) {
                        // DOCX-Datei
                    DocxSplitProcessor processor = new DocxSplitProcessor();
                        processor.setSourceDocxFile(selectedFile);
                    List<Chapter> chapters = processor.analyzeDocument(selectedFile);
                    
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
                        }
                    } else if (fileName.endsWith(".rtf")) {
                        // RTF-Datei
                        RtfSplitProcessor processor = new RtfSplitProcessor();
                        List<RtfSplitProcessor.Chapter> chapters = processor.analyzeDocument(selectedFile);
                        
                        if (chapters.isEmpty()) {
                            CheckBox noChaptersCheckBox = new CheckBox("Keine Kapitel gefunden");
                            noChaptersCheckBox.setDisable(true);
                            listChapters.getItems().add(noChaptersCheckBox);
                            btnSplit.setDisable(true);
                        } else {
                            for (RtfSplitProcessor.Chapter chapter : chapters) {
                                CheckBox chapterCheckBox = new CheckBox(chapter.toString());
                                chapterCheckBox.setSelected(true); // Standardmäßig ausgewählt
                                chapterCheckBox.setUserData(chapter); // Kapitel-Objekt speichern
                                listChapters.getItems().add(chapterCheckBox);
                            }
                            btnSplit.setDisable(false);
                        }
                    } else {
                        CheckBox errorCheckBox = new CheckBox("Unsupported file format. Please select DOCX or RTF.");
                        errorCheckBox.setDisable(true);
                        listChapters.getItems().add(errorCheckBox);
                        btnSplit.setDisable(true);
                    }
                } catch (Exception ex) {
                    logger.error("Fehler beim Analysieren der Datei: {}", ex.getMessage(), ex);
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
                
                File inputFile = new File(filePath);
                File outputDir = new File(outputPath);
                
                if (!inputFile.exists()) {
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
                
                // Split in separatem Thread ausführen
                new Thread(() -> {
                    try {
                        String fileName = inputFile.getName().toLowerCase();
                        if (fileName.endsWith(".rtf")) {
                            // RTF-Split
                            RtfSplitProcessor processor = new RtfSplitProcessor();
                            processor.splitDocument(inputFile, outputDir);
                            
                            // UI-Update im JavaFX-Thread
                            Platform.runLater(() -> {
                                btnSplit.setDisable(false);
                                btnSplit.setText("Kapitel aufteilen");
                                showError("Erfolg", "RTF-Datei wurde erfolgreich in Kapitel aufgeteilt!");
                            });
                        } else {
                            // DOCX-Split
                        DocxSplitProcessor processor = new DocxSplitProcessor();
                            processor.setSourceDocxFile(inputFile);
                        
                        // Ausgabe-Verzeichnis erstellen falls nicht vorhanden
                        if (!outputDir.exists()) {
                            outputDir.mkdirs();
                        }
                        
                        // Basis-Dateiname (ohne .docx)
                            String baseFileName = inputFile.getName().replaceFirst("\\.docx$", "");
                        
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
                        }
                        
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
    
    /**
     * Lädt die Projektfenster-Eigenschaften aus den Preferences
     */
    private void loadProjectWindowProperties(CustomStage projectStage) {
        if (preferences != null) {
            // Bildschirmabmessungen abfragen
            Screen primaryScreen = Screen.getPrimary();
            Rectangle2D screenBounds = primaryScreen.getBounds();
            double screenWidth = screenBounds.getWidth();
            double screenHeight = screenBounds.getHeight();
            
            
            // Robuste Validierung der Preferences mit sinnvollen Standardwerten
            double x = preferences.getDouble("project_window_x", 100);
            double y = preferences.getDouble("project_window_y", 100);
            double width = preferences.getDouble("project_window_width", 1200);  // KEINE Begrenzung
            double height = preferences.getDouble("project_window_height", 800);  // KEINE Begrenzung
            
            
            // Validierung: Position muss auf dem Bildschirm sein (lockerer)
            if (x < -100 || x > screenWidth + 100 || y < -100 || y > screenHeight + 100) {
                logger.warn("Ungültige Position ({},{}) für Projekt-Fenster, setze Standard 100,100", x, y);
                x = 100;
                y = 100;
            }
            
            // Validierung: Größe muss sinnvoll sein (nur Mindestgrößen prüfen)
            if (width < 400 || height < 300) {
                logger.warn("Ungültige Größe ({}x{}) für Projekt-Fenster, setze Standard 1200x800", width, height);
                width = 1200;  // KEINE Breitenbegrenzung für ultrawide Monitore
                height = 800;  // KEINE Höhenbegrenzung für ultrawide Monitore
            }
            
            // KEINE Höhenbegrenzung - erlaube auch größere Fenster (für ultrawide Monitore)
            
            // Debug: Warum wird die Größe als illegal betrachtet?
            
            projectStage.setX(x);
            projectStage.setY(y);
            projectStage.setWidth(width);
            projectStage.setHeight(height);
            projectStage.setMinWidth(800);
            projectStage.setMinHeight(600);
            projectStage.setResizable(true);
            
            
            // Listener werden in addProjectWindowListeners() hinzugefügt
        }
    }
    
    /**
     * Fügt Listener für Projekt-Fenster hinzu
     */
    private void addProjectWindowListeners(CustomStage projectStage) {
        Preferences preferences = Preferences.userNodeForPackage(MainController.class);
        Screen primaryScreen = Screen.getPrimary();
        Rectangle2D screenBounds = primaryScreen.getBounds();
        double screenWidth = screenBounds.getWidth();
        double screenHeight = screenBounds.getHeight();
        
        
        // Fenster-Position und Größe speichern (lockere Validierung für große Bildschirme)
        projectStage.xProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.doubleValue() >= -100 && newVal.doubleValue() <= screenWidth + 100) {
                preferences.putDouble("project_window_x", newVal.doubleValue());
            }
        });
        projectStage.yProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.doubleValue() >= -100 && newVal.doubleValue() <= screenHeight + 100) {
                preferences.putDouble("project_window_y", newVal.doubleValue());
            }
        });
        projectStage.widthProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.doubleValue() >= 800) {
                preferences.putDouble("project_window_width", newVal.doubleValue());
            }
        });
        projectStage.heightProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.doubleValue() >= 600) {
                preferences.putDouble("project_window_height", newVal.doubleValue());
            }
        });
        projectStage.maximizedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                preferences.putDouble("project_window_width", screenWidth);
                preferences.putDouble("project_window_height", screenHeight);
            }
        });
    }
    
    /**
     * Zeigt das übergeordnete Projektauswahl-Menü
     */
    private void showProjectSelectionMenu() {
        try {
            // Erstelle CustomStage für Projektauswahl
            CustomStage projectStage = new CustomStage();
            projectStage.setCustomTitle("Projektauswahl");
            
            // WICHTIG: Theme sofort setzen (vollständiges Theme)
            projectStage.setFullTheme(currentThemeIndex);
            
            // Lade Fenster-Eigenschaften aus Preferences
            loadProjectWindowProperties(projectStage);
            
            // Haupt-Layout
            VBox mainLayout = new VBox(10);
            mainLayout.setPadding(new Insets(20));
            mainLayout.getStyleClass().add("project-selection-container");
            
            // Titel
            Label titleLabel = new Label("📚 Wähle ein Projekt");
            titleLabel.getStyleClass().add("project-title");
            titleLabel.setTextAlignment(TextAlignment.CENTER);
            titleLabel.setAlignment(Pos.CENTER);
            
            // Projekt-FlowPane in ScrollPane
            FlowPane projectFlow = new FlowPane();
            projectFlow.setHgap(5);
            projectFlow.setVgap(16);
            projectFlow.setAlignment(Pos.CENTER);
            projectFlow.setPrefWrapLength(Double.MAX_VALUE); // Keine Größenbeschränkung
            projectFlow.getStyleClass().add("project-grid");
            
            // ScrollPane für das gesamte Projekt-FlowPane
            ScrollPane mainScrollPane = new ScrollPane();
            mainScrollPane.setContent(projectFlow);
            mainScrollPane.setFitToWidth(true);
            mainScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            mainScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            mainScrollPane.getStyleClass().add("scroll-pane"); // CSS-Styling hinzufügen
            // Keine feste Größe - soll sich ausdehnen
            
            // Lade verfügbare Projekte
            currentProjectFlow = projectFlow;
            currentProjectStage = projectStage;

            loadAndDisplayProjects(projectFlow, projectStage);
            
            // Abbrechen-Button
            Button cancelButton = new Button("❌ Abbrechen");
            cancelButton.getStyleClass().add("cancel-button");
            cancelButton.setPrefSize(150, 40);
            cancelButton.setOnAction(e -> projectStage.close());
            
            // Abbrechen-Button nach unten rechts
            HBox buttonContainer = new HBox();
            buttonContainer.setAlignment(Pos.BOTTOM_RIGHT);
            buttonContainer.setPrefHeight(50); // Minimale Höhe
            buttonContainer.getStyleClass().add("button-container"); // CSS-Styling hinzufügen
            buttonContainer.getChildren().add(cancelButton);
            
            // Layout zusammenbauen (ohne Spacer)
            mainLayout.getChildren().addAll(titleLabel, mainScrollPane, buttonContainer);
            
            // ScrollPane soll sich ausdehnen
            VBox.setVgrow(mainScrollPane, Priority.ALWAYS);
            
            // Scene erstellen
            Scene scene = new Scene(mainLayout);
            
            // CSS laden
            String cssPath = ResourceManager.getCssResource("css/manuskript.css");
            if (cssPath != null) {
                scene.getStylesheets().add(cssPath);
            }
            
            // Theme-Klassen auf Scene-Root setzen
            Node root = scene.getRoot();
            root.getStyleClass().removeAll("theme-dark", "theme-light", "blau-theme", "gruen-theme", "lila-theme", "weiss-theme", "pastell-theme");
            
            if (currentThemeIndex == 0) { // Weiß
                root.getStyleClass().add("weiss-theme");
            } else if (currentThemeIndex == 1) { // Schwarz
                root.getStyleClass().add("theme-dark");
            } else if (currentThemeIndex == 2) { // Pastell
                root.getStyleClass().add("pastell-theme");
            } else if (currentThemeIndex == 3) { // Blau
                root.getStyleClass().add("theme-dark");
                root.getStyleClass().add("blau-theme");
            } else if (currentThemeIndex == 4) { // Grün
                root.getStyleClass().add("theme-dark");
                root.getStyleClass().add("gruen-theme");
            } else if (currentThemeIndex == 5) { // Lila
                root.getStyleClass().add("theme-dark");
                root.getStyleClass().add("lila-theme");
            }
            
            projectStage.setSceneWithTitleBar(scene);
            
            // WICHTIG: Listener NACH setSceneWithTitleBar hinzufügen
            addProjectWindowListeners(projectStage);
            
            // CustomStage Theme anwenden NACH setSceneWithTitleBar
            projectStage.setFullTheme(currentThemeIndex);
            projectStage.initOwner(primaryStage);
            projectStage.showAndWait();
            
        } catch (Exception e) {
            logger.error("Fehler beim Öffnen der Projektauswahl", e);
            showError("Fehler", "Projektauswahl konnte nicht geöffnet werden: " + e.getMessage());
        }
    }
    
    /**
     * Lädt und zeigt verfügbare Projekte im Grid an
     */
    private void loadAndDisplayProjects(FlowPane projectFlow, CustomStage projectStage) {
        try {
            // Prüfe ob Root-Verzeichnis konfiguriert ist
            String rootDir = ResourceManager.getParameter("project.root.directory", "");
            if (rootDir == null || rootDir.isEmpty()) {
                // Sinnvolle Reaktion: Verzeichnis wählen lassen
                // Vorab-Hinweis anzeigen, damit klar ist, was zu tun ist
                Platform.runLater(() -> showInfo(
                    "Projektverzeichnis wählen",
                    "Bitte wählen Sie das Projektwurzel-Verzeichnis.\n\nDarin werden die einzelnen Projekte (Unterordner mit DOCX-Dateien) gesucht."));
                DirectoryChooser chooser = new DirectoryChooser();
                chooser.setTitle("Projektwurzel auswählen");
                File chosen = chooser.showDialog(projectStage);
                if (chosen == null) {
                    Platform.runLater(() -> showWarning("Kein Verzeichnis ausgewählt", "Ohne Projektverzeichnis können keine Projekte angezeigt werden."));
                    // Stelle sicher, dass das Hauptfenster sichtbar bleibt
                    if (primaryStage != null) {
                        primaryStage.show();
                    }
                    return;
                }
                ResourceManager.saveParameter("project.root.directory", chosen.getAbsolutePath());
                rootDir = chosen.getAbsolutePath();
            }
            
            File searchDir = new File(rootDir);
            if (!searchDir.exists()) {
                // Verzeichnis existiert nicht mehr → erneut wählen lassen
                Platform.runLater(() -> showWarning(
                    "Projektwurzel nicht gefunden",
                    "Das gespeicherte Projektverzeichnis existiert nicht mehr. Bitte wählen Sie ein neues."));
                DirectoryChooser chooser = new DirectoryChooser();
                chooser.setTitle("Projektwurzel nicht gefunden – bitte erneut auswählen");
                File chosen = chooser.showDialog(projectStage);
                if (chosen == null) {
                    Platform.runLater(() -> showWarning("Projektverzeichnis fehlt", "Bitte wählen Sie später unter Projekteinstellungen eine gültige Projektwurzel."));
                    // Stelle sicher, dass das Hauptfenster sichtbar bleibt
                    if (primaryStage != null) {
                        primaryStage.show();
                    }
                    return;
                }
                ResourceManager.saveParameter("project.root.directory", chosen.getAbsolutePath());
                searchDir = chosen;
            }

            projectRootDirectory = searchDir;
            loadProjectOrder(searchDir);
            
            File[] directories = searchDir.listFiles(File::isDirectory);
            if (directories == null) {
                directories = new File[0];
            }

            Map<String, ProjectDisplayItem> existingItems = projectItems.stream()
                .collect(Collectors.toMap(ProjectDisplayItem::getId, item -> item, (a, b) -> a, LinkedHashMap::new));
            projectItems = new ArrayList<>(existingItems.values());

            Set<String> seenIds = new HashSet<>(existingItems.keySet());
            
            for (File dir : directories) {
                String id = dir.getAbsolutePath();
                if (seenIds.contains(id)) {
                    continue;
                }
                
                // Ausnahmeliste für Verzeichnisse, die nicht als Projekte angezeigt werden sollen
                String dirName = dir.getName().toLowerCase();
                if (dirName.equals("data") || dirName.equals("backup") || 
                    dirName.equals("config") || dirName.equals("logs") || 
                    dirName.equals("export") || dirName.equals("target") ||
                    dirName.equals("src") || dirName.equals("node_modules") ||
                    dirName.equals(".git") || dirName.equals(".idea") ||
                    dirName.equals("__pycache__") || dirName.equals(".vscode")) {
                    continue;
                }

                // Prüfe ob Verzeichnis eine Serie ist (enthält Unterordner mit DOCX-Dateien)
                File[] subDirs = dir.listFiles(File::isDirectory);
                List<File> seriesBooks = new ArrayList<>();
                
                if (subDirs != null && subDirs.length > 0) {
                    // Prüfe ob es eine Serie ist (Unterordner enthalten DOCX-Dateien)
                    for (File subDir : subDirs) {
                        // Ausnahmeliste auch für Unterordner in Serien anwenden
                        String subDirName = subDir.getName().toLowerCase();
                        if (subDirName.equals("data") || subDirName.equals("backup") || 
                            subDirName.equals("config") || subDirName.equals("logs") || 
                            subDirName.equals("export") || subDirName.equals("target") ||
                            subDirName.equals("src") || subDirName.equals("node_modules") ||
                            subDirName.equals(".git") || subDirName.equals(".idea") ||
                            subDirName.equals("__pycache__") || subDirName.equals(".vscode")) {
                            continue;
                        }
                        
                        File[] docxFiles = subDir.listFiles((d, name) -> name.toLowerCase().endsWith(".docx"));
                        if (docxFiles != null && docxFiles.length > 0) {
                            seriesBooks.add(subDir);
                        }
                    }
                }
                
                // Prüfe auch direkte DOCX-Dateien im Verzeichnis
                File[] directDocxFiles = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".docx"));
                
                if (!seriesBooks.isEmpty()) {
                    projectItems.add(new ProjectDisplayItem(dir, seriesBooks));
                } else if (directDocxFiles != null && directDocxFiles.length > 0) {
                    projectItems.add(new ProjectDisplayItem(dir, directDocxFiles));
                }
            }

            renderProjectItems(projectFlow, projectStage);
            setupFlowPaneDragHandlers(projectFlow, projectStage);
            
            if (projectFlow.getChildren().isEmpty()) {
                // Keine Projekte gefunden
                Label noProjectsLabel = new Label("Keine Projekte gefunden");
                noProjectsLabel.getStyleClass().add("no-projects-label");
                projectFlow.getChildren().add(noProjectsLabel);
            }
            
        } catch (Exception e) {
            logger.error("Fehler beim Laden der Projekte", e);
        }
    }
    
    /**
     * Erstellt eine Serien-Karte für die Projektauswahl
     */
    private VBox createSeriesCard(ProjectDisplayItem item, CustomStage projectStage) {
        File seriesDir = item.getDirectory();
        VBox card = new VBox(5); // Weniger Abstand zwischen Elementen
        card.getStyleClass().add("project-card");
        card.setAlignment(Pos.CENTER);
        card.setUserData(item);
        attachDragHandlers(card, null);
        
        // Serien-Name (vollständig)
        Label seriesName = new Label("📚 " + seriesDir.getName());
        seriesName.setWrapText(false); // KEIN Umbrechen
        seriesName.setAlignment(Pos.CENTER);
        seriesName.setFont(Font.font(seriesName.getFont().getFamily(), FontWeight.BOLD, seriesName.getFont().getSize() + 4));
        // Keine CSS-Klassen, keine Inline-Styles - nur Java-Eigenschaften
        
        // Bücher horizontal scrollbar
        ScrollPane booksScrollPane = new ScrollPane();
        booksScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        booksScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        booksScrollPane.getStyleClass().add("series-scroll-pane"); // CSS-Styling für Serien-ScrollPane
        booksScrollPane.setOnDragOver(event -> {
            if (draggingSeriesBook != null && event.getGestureSource() != booksScrollPane) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });
        HBox booksContainer = new HBox(20);
        booksContainer.setPadding(new Insets(5)); // Weniger Padding
        booksContainer.setPickOnBounds(true);

        populateSeriesBooks(booksContainer, item, projectStage);
        setupSeriesContainerDragHandlers(booksContainer, item, projectStage);
        
        booksScrollPane.setContent(booksContainer);

        booksScrollPane.setOnDragDropped(event -> {
            setupSeriesDrop(booksContainer, item, projectStage, event);
        });
        
        // Dynamische Größenberechnung
        int bookCount = item.getSeriesBooks().size();
        int bookWidth = 300; // Breite einer Buch-Karte
        int bookSpacing = 20; // Abstand zwischen Büchern
        int padding = 60; // Padding links/rechts (etwas größer, damit 3 Bücher sicher passen)
        
        // Berechne benötigte Breite
        int totalBookWidth = (bookCount * bookWidth) + (Math.max(0, bookCount - 1) * bookSpacing);
        int cardWidth = Math.max(600, totalBookWidth + padding); // Mindestens 600px breit für bessere Proportionen
        int cardHeight = 440; // Etwas kompakter: ScrollPane + Serien-Name + Info
        
        // Setze dynamische Größen
        card.setPrefSize(cardWidth, cardHeight);
        card.setMinSize(cardWidth, cardHeight);
        card.setMaxSize(cardWidth, cardHeight);
        
        // ScrollPane-Größe - Angepasst für 300x250 Buch-Karten
        booksScrollPane.setFitToWidth(false);
        booksScrollPane.setFitToHeight(false);
        booksScrollPane.setPrefViewportWidth(cardWidth - 60); // Breite der Serien-Karte minus Padding
        booksScrollPane.setPrefViewportHeight(320); // Eingekürzt passend zur geringeren Kartenhöhe
        
        // Serien-Info
        Label seriesInfo = new Label(item.getSeriesBooks().size() + " Bücher in der Serie");
        seriesInfo.getStyleClass().add("project-info");
        seriesInfo.setAlignment(Pos.CENTER);
        
        // Layout zusammenbauen
        card.getChildren().addAll(seriesName, booksScrollPane, seriesInfo);
        
        return card;
    }

    private void populateSeriesBooks(HBox container, ProjectDisplayItem item, CustomStage projectStage) {
        container.getChildren().clear();
        for (File bookDir : item.getSeriesBooks()) {
            File[] docxFiles = bookDir.listFiles((d, name) -> name.toLowerCase().endsWith(".docx"));
            if (docxFiles != null && docxFiles.length > 0) {
                VBox bookCard = createProjectCard(bookDir, docxFiles, projectStage);
                bookCard.setUserData(bookDir);
                bookCard.setCursor(Cursor.OPEN_HAND);
                setupSeriesBookDragHandlers(bookCard, container, item, projectStage);
                container.getChildren().add(bookCard);
            }
        }
    }

    private void setupSeriesContainerDragHandlers(HBox container, ProjectDisplayItem item, CustomStage projectStage) {
        container.setOnDragOver(event -> {
            if (draggingSeriesBook != null && event.getGestureSource() != container) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });

        container.setOnDragDropped(event -> {
            setupSeriesDrop(container, item, projectStage, event);
        });
    }

    private void setupSeriesDrop(HBox container, ProjectDisplayItem item, CustomStage projectStage, DragEvent event) {
        if (draggingSeriesBook == null) {
            return;
        }

        List<File> books = item.getSeriesBooks();
        int fromIndex = books.indexOf(draggingSeriesBook);
        if (fromIndex < 0) {
            return;
        }

        Point2D localPoint = container.sceneToLocal(event.getSceneX(), event.getSceneY());
        double dropX = localPoint.getX();

        List<Node> bookNodes = container.getChildren().stream()
                .filter(child -> child instanceof VBox)
                .collect(Collectors.toList());

        int toIndex = books.size();
        for (int i = 0; i < bookNodes.size(); i++) {
            VBox card = (VBox) bookNodes.get(i);
            Bounds bounds = card.getBoundsInParent();

            double gap = 15;
            double hitLeft = bounds.getMinX() - gap;
            double hitRight = bounds.getMaxX() + gap;

            if (dropX < hitLeft) {
                toIndex = Math.max(0, i);
                break;
            }

            if (dropX <= hitRight) {
                double cardCenter = bounds.getMinX() + bounds.getWidth() / 2;
                if (dropX < cardCenter) {
                    toIndex = i;
                } else {
                    toIndex = i + 1;
                }
                break;
            }
        }

        // Grenzen überprüfen
        if (toIndex < 0) {
            toIndex = 0;
        }
        if (toIndex > books.size()) {
            toIndex = books.size();
        }

        if (fromIndex == toIndex) {
            event.setDropCompleted(true);
            event.consume();
            return;
        }

        books.remove(fromIndex);
        if (fromIndex < toIndex) {
            toIndex--;
        }
        books.add(toIndex, draggingSeriesBook);

        populateSeriesBooks(container, item, projectStage);
        saveProjectOrder();
        event.setDropCompleted(true);
        event.consume();
    }

    private void setupSeriesDragHandlers(Node card, Parent container, ProjectDisplayItem item, CustomStage projectStage) {
        if (card instanceof VBox vbox && container instanceof HBox hbox) {
            setupSeriesBookDragHandlers(vbox, hbox, item, projectStage);
        }
    }

    private void setupSeriesBookDragHandlers(VBox bookCard, HBox container, ProjectDisplayItem item, CustomStage projectStage) {
        // Mouse Pressed - Start Drag
        bookCard.setOnMousePressed(event -> {
            if (event.isPrimaryButtonDown()) {
                File targetBook = (File) bookCard.getUserData();
                if (targetBook != null) {
                    draggingSeriesBook = targetBook;
                    bookCard.setCursor(Cursor.CLOSED_HAND);
                    bookCard.setOpacity(0.7);
                }
            }
        });

        // Mouse Released - End Drag
        bookCard.setOnMouseReleased(event -> {
            bookCard.setCursor(Cursor.OPEN_HAND);
            bookCard.setOpacity(1.0);
            draggingSeriesBook = null;
        });

        // Drag Detected - Start Drag and Drop
        bookCard.setOnDragDetected(event -> {
            File targetBook = (File) bookCard.getUserData();
            if (targetBook != null && draggingSeriesBook != null) {
                Dragboard dragboard = bookCard.startDragAndDrop(TransferMode.MOVE);
                ClipboardContent content = new ClipboardContent();
                content.putString("series-book:" + targetBook.getAbsolutePath());
                dragboard.setContent(content);
                bookCard.setOpacity(0.4);
                event.consume();
            }
        });

        // Drag Over - Accept drops
        bookCard.setOnDragOver(event -> {
            if (draggingSeriesBook != null && event.getGestureSource() != bookCard) {
                event.acceptTransferModes(TransferMode.MOVE);
                event.consume();
            }
        });

        // Drag Dropped - Handle drop
        bookCard.setOnDragDropped(event -> {
            if (draggingSeriesBook != null) {
                setupSeriesDrop(container, item, projectStage, event);
            }
        });

        // Drag Done - Cleanup
        bookCard.setOnDragDone(event -> {
            bookCard.setOpacity(1.0);
            bookCard.setCursor(Cursor.OPEN_HAND);
            draggingSeriesBook = null;
            event.consume();
        });
    }

    private void propagateSeriesDragHandlers(Node root, VBox bookCard) {
        if (!(root instanceof Parent parent)) {
            return;
        }
        for (Node child : parent.getChildrenUnmodifiable()) {
            if (isWithinInteractiveControl(child)) {
                continue;
            }
            child.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
                MouseEvent redirected = event.copyFor(bookCard, bookCard);
                Event.fireEvent(bookCard, redirected);
            });
            propagateSeriesDragHandlers(child, bookCard);
        }
    }

    private boolean isWithinInteractiveControl(Node node) {
        Node current = node;
        while (current != null) {
            if (current instanceof ButtonBase || current instanceof TextInputControl) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private File findBookForNode(Node node) {
        Node current = node;
        while (current != null) {
            Object data = current.getUserData();
            if (data instanceof File file) {
                return file;
            }
            Parent parent = current.getParent();
            current = parent;
        }
        return null;
    }

    private Node findCardNode(Node node) {
        Node current = node;
        while (current != null && !(current instanceof VBox)) {
            Parent parent = current.getParent();
            current = parent;
        }
        return current;
    }
    
    private Region createDebugSpacer(double targetHeight) {
        double height = Double.isNaN(targetHeight) || targetHeight <= 0 ? 250 : targetHeight;
        Region spacer = new Region();
        spacer.setPrefSize(6, height);
        spacer.setMinSize(6, height);
        spacer.setMaxSize(6, height);
        spacer.getStyleClass().add("project-card-debug-spacer");
        return spacer;
    }
    
    /**
     * Erstellt ein Dummy-Buch-Bild für Projekte ohne Cover
     */
    private void createDummyBookImage(ImageView imageView, String projectName) {
        try {
            // Erstelle ein schmales Canvas (120x150 - schmal wie ein Buch)
            javafx.scene.canvas.Canvas canvas = new javafx.scene.canvas.Canvas(120, 150);
            javafx.scene.canvas.GraphicsContext gc = canvas.getGraphicsContext2D();
            
            // Hintergrund
            gc.setFill(javafx.scene.paint.Color.LIGHTGRAY);
            gc.fillRoundRect(0, 0, 120, 150, 8, 8);
            
            // Rand
            gc.setStroke(javafx.scene.paint.Color.GRAY);
            gc.setLineWidth(2);
            gc.strokeRoundRect(1, 1, 118, 148, 8, 8);
            
            // Buch-Icon (viel größer, füllt fast das ganze Bild)
            gc.setFill(javafx.scene.paint.Color.WHITE);
            gc.fillRect(5, 10, 110, 130);
            gc.setStroke(javafx.scene.paint.Color.BLACK);
            gc.setLineWidth(2);
            gc.strokeRect(5, 10, 110, 130);
            
            // Linien für Buchseiten
            gc.setStroke(javafx.scene.paint.Color.LIGHTGRAY);
            for (int i = 0; i < 6; i++) {
                gc.strokeLine(15, 20 + i * 18, 105, 20 + i * 18);
            }
            
            // Text (doppelt so groß und zentriert)
            gc.setFill(javafx.scene.paint.Color.BLACK);
            gc.setFont(javafx.scene.text.Font.font("Arial", 48));
            gc.fillText("📚", 36, 80); // Horizontal zentriert für größeres Icon
            
            // Canvas zu Image konvertieren
            javafx.scene.image.WritableImage image = new javafx.scene.image.WritableImage(120, 150);
            canvas.snapshot(null, image);
            imageView.setImage(image);
            
        } catch (Exception e) {
            logger.warn("Fehler beim Erstellen des Dummy-Bildes: " + e.getMessage());
            // Fallback: Einfaches Icon
            imageView.setStyle("-fx-background-color: #f0f0f0; -fx-border-color: #ccc; -fx-border-width: 1px;");
        }
    }

    private Image loadRandomDefaultCover() {
        try {
            File defaultCoverDir = new File("config/defaultCovers");
            if (!defaultCoverDir.exists() || !defaultCoverDir.isDirectory()) {
                return null;
            }

            File[] imageFiles = defaultCoverDir.listFiles((dir, name) -> {
                String lowerName = name.toLowerCase(Locale.ROOT);
                return lowerName.endsWith(".png") || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || lowerName.endsWith(".gif") || lowerName.endsWith(".bmp");
            });

            if (imageFiles == null || imageFiles.length == 0) {
                return null;
            }

            int index = new Random().nextInt(imageFiles.length);
            File selected = imageFiles[index];
            return new Image(selected.toURI().toString());
        } catch (Exception ex) {
            logger.warn("Fehler beim Laden eines Default-Covers", ex);
            return null;
        }
    }
    
    /**
     * Zeigt die Bücher-Auswahl für eine Serie
     */
    private void showSeriesBookSelection(File seriesDir, List<File> seriesBooks, CustomStage projectStage) {
        try {
            // Erstelle CustomStage für Bücher-Auswahl
            CustomStage booksStage = new CustomStage();
            booksStage.setCustomTitle("Bücher-Auswahl: " + seriesDir.getName());
            booksStage.setMinWidth(800);
            // KEINE setMaxWidth - erlaube große Fenster
            
            // WICHTIG: Theme sofort setzen (vollständiges Theme)
            booksStage.setFullTheme(currentThemeIndex);
            booksStage.setMinHeight(600);
            // KEINE setMaxHeight - erlaube große Fenster
            booksStage.setWidth(800);
            booksStage.setHeight(600);
            booksStage.setResizable(false);
            
            // Layout
            VBox mainLayout = new VBox(20);
            mainLayout.setPadding(new Insets(20));
            mainLayout.setAlignment(Pos.CENTER);
            
            // Titel
            Label titleLabel = new Label("📚 " + seriesDir.getName());
            titleLabel.getStyleClass().add("project-title");
            
            // Bücher-FlowPane
            FlowPane booksFlow = new FlowPane();
            booksFlow.setHgap(20);
            booksFlow.setVgap(20);
            booksFlow.setAlignment(Pos.CENTER);
            booksFlow.getStyleClass().add("project-grid");
            
            for (File book : seriesBooks) {
                File[] docxFiles = book.listFiles((d, name) -> name.toLowerCase().endsWith(".docx"));
                if (docxFiles != null && docxFiles.length > 0) {
                    VBox bookCard = createProjectCard(book, docxFiles, booksStage);
                    setupSeriesDragHandlers(bookCard, booksFlow, new ProjectDisplayItem(book, docxFiles), booksStage);
                    booksFlow.getChildren().add(bookCard);
                }
            }
            
            // Abbrechen-Button
            Button cancelButton = new Button("❌ Abbrechen");
            cancelButton.getStyleClass().add("cancel-button");
            cancelButton.setPrefSize(150, 40);
            cancelButton.setOnAction(e -> booksStage.close());
            
            // Layout zusammenbauen
            mainLayout.getChildren().addAll(titleLabel, booksFlow, cancelButton);
            
            // Scene erstellen
            Scene scene = new Scene(mainLayout);
            
            // CSS laden
            String cssPath = ResourceManager.getCssResource("css/manuskript.css");
            if (cssPath != null) {
                scene.getStylesheets().add(cssPath);
            }
            
            booksStage.setSceneWithTitleBar(scene);
            booksStage.setFullTheme(currentThemeIndex);
            booksStage.initOwner(projectStage);
            booksStage.showAndWait();
            
        } catch (Exception e) {
            logger.error("Fehler beim Öffnen der Bücher-Auswahl", e);
            showError("Fehler", "Bücher-Auswahl konnte nicht geöffnet werden: " + e.getMessage());
        }
    }
    
    /**
     * Erstellt eine Projekt-Karte für die Auswahl
     */
    private VBox createProjectCard(File projectDir, File[] docxFiles, CustomStage projectStage) {
        VBox card = new VBox(10);
        card.getStyleClass().add("project-card");
        card.setPrefSize(300, 250);
        card.setAlignment(Pos.CENTER);
        
        // Projekt-Bild
        ImageView projectImage = new ImageView();
        projectImage.setFitWidth(200);
        projectImage.setFitHeight(150);
        projectImage.setPreserveRatio(true);
        projectImage.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 5, 0, 2, 2);");
        
        // Suche nach cover_image.png im Projekt
        File coverImageFile = new File(projectDir, "cover_image.png");
        if (coverImageFile.exists()) {
            try {
                Image image = new Image(coverImageFile.toURI().toString());
                projectImage.setImage(image);
            } catch (Exception e) {
                logger.warn("Fehler beim Laden des Projekt-Bildes: {}", coverImageFile.getName());
                projectImage.setImage(loadRandomDefaultCover());
            }
        }

        if (projectImage.getImage() == null) {
            Image fallbackImage = loadRandomDefaultCover();
            if (fallbackImage != null) {
                projectImage.setImage(fallbackImage);
            }
        }
        
        // Projekt-Name
        Label projectName = new Label(projectDir.getName());
        projectName.getStyleClass().add("project-name");
        projectName.setWrapText(false); // KEIN Umbrechen
        // Keine setMaxWidth - Label nutzt die volle Kartenbreite
        projectName.setAlignment(Pos.CENTER);
        // Blauer Hintergrund entfernt - Problem gelöst
        
        // Projekt-Info
        Label projectInfo = new Label(docxFiles.length + " Dokument(e)");
        projectInfo.getStyleClass().add("project-info");
        projectInfo.setAlignment(Pos.CENTER);
        
        // Auswählen-Button
        Button selectButton = new Button("📂 Öffnen");
        selectButton.getStyleClass().add("select-project-button");
        selectButton.setPrefSize(120, 35);
        selectButton.setOnAction(e -> {
            // Projekt auswählen
            selectProject(projectDir, projectStage);
        });
        
        // Layout zusammenbauen
        card.getChildren().addAll(projectImage, projectName, projectInfo, selectButton);
        
        return card;
    }
    
    /**
     * Wählt ein Projekt aus und lädt es
     */
    private void selectProject(File projectDir, CustomStage projectStage) {
        try {
            // Lade das ausgewählte Projekt
            txtDirectoryPath.setText(projectDir.getAbsolutePath());
            loadDocxFiles(projectDir);
            updateProjectTitleFromCurrentPath();
            
            // Speichere den Pfad
            preferences.put("lastDirectory", projectDir.getAbsolutePath());
            
            // Lade das Cover-Bild für das neue Projekt (nur cover_image.png aus dem aktuellen Verzeichnis)
            loadCoverImageFromCurrentDirectory();
            
            // Schließe die Projektauswahl
            projectStage.close();
            
            // Zeige das Hauptfenster wieder an
            primaryStage.show();
            
            
        } catch (Exception e) {
            logger.error("Fehler beim Laden des Projekts", e);
            showError("Fehler", "Projekt konnte nicht geladen werden: " + e.getMessage());
        }
    }
    
    
    /**
     * Lädt das Cover-Bild nur aus dem aktuellen Verzeichnis (cover_image.png)
     */
    private void loadCoverImageFromCurrentDirectory() {
        try {
            String currentDir = txtDirectoryPath.getText();
            if (currentDir != null && !currentDir.isEmpty()) {
                File currentDirectory = new File(currentDir);
                if (currentDirectory.exists() && currentDirectory.isDirectory()) {
                    // Titel bei jedem Bild-Ladevorgang aktualisieren
                    updateProjectTitleFromCurrentPath();
                    File coverImageFile = new File(currentDirectory, "cover_image.png");
                    if (coverImageFile.exists()) {
                        Image image = new Image(coverImageFile.toURI().toString());
                        coverImageView.setImage(image);
                    } else {
                        Image fallbackImage = loadRandomDefaultCover();
                        if (fallbackImage != null) {
                            coverImageView.setImage(fallbackImage);
                        } else {
                            coverImageView.setImage(null);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Fehler beim Laden des Cover-Bildes aus aktuellem Verzeichnis", e);
            coverImageView.setImage(null);
        }
    }
    
    /**
     * Zeigt einen Dialog zur Auswahl des Root-Verzeichnisses für Projekte
     */
    private void showRootDirectoryChooser() {
        try {
            // Erstelle CustomStage für Root-Verzeichnis-Auswahl
            CustomStage chooserStage = new CustomStage();
            chooserStage.setCustomTitle("Projekt-Root-Verzeichnis auswählen");
            chooserStage.setMinWidth(600);
            // KEINE setMaxWidth - erlaube große Fenster
            
            // WICHTIG: Theme sofort setzen
            chooserStage.setTitleBarTheme(currentThemeIndex);
            chooserStage.setMinHeight(400);
            // KEINE setMaxHeight - erlaube große Fenster
            chooserStage.setWidth(600);
            chooserStage.setHeight(400);
            chooserStage.setResizable(false);
            
            // Layout
            VBox mainLayout = new VBox(20);
            mainLayout.setPadding(new Insets(20));
            mainLayout.setAlignment(Pos.CENTER);
            
            // Willkommen-Titel
            Label titleLabel = new Label("🎭 Willkommen zu Manuskript");
            titleLabel.getStyleClass().add("project-title");
            titleLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
            
            // Beeindruckende Beschreibung
            Label descLabel = new Label("Das Tool für das Importieren, Editieren, Fehler suchen, Lektorieren und Exportieren von Prosa-Texten.\n\n" +
                "Wähle das Root-Verzeichnis für deine Manuskript-Projekte. Serien werden als Unterordner erkannt und " +
                "automatisch organisiert. Jedes Projekt kann mehrere Kapitel enthalten und wird intelligent verwaltet.");
            descLabel.getStyleClass().add("project-info");
            descLabel.setWrapText(true);
            descLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #34495e; -fx-line-spacing: 2px;");
            
            // Verzeichnis-Auswahl
            HBox dirBox = new HBox(10);
            dirBox.setAlignment(Pos.CENTER);
            
            TextField dirField = new TextField("manuskripte");
            dirField.setPrefWidth(300);
            dirField.setPromptText("Verzeichnisname (z.B. 'manuskripte')");
            
            Button browseButton = new Button("📂 Durchsuchen");
            browseButton.getStyleClass().add("select-project-button");
            browseButton.setOnAction(e -> {
                DirectoryChooser chooser = new DirectoryChooser();
                chooser.setTitle("Root-Verzeichnis auswählen");
                File selectedDir = chooser.showDialog(chooserStage);
                if (selectedDir != null) {
                    dirField.setText(selectedDir.getAbsolutePath());
                }
            });
            
            dirBox.getChildren().addAll(dirField, browseButton);
            
            // Buttons
            HBox buttonBox = new HBox(20);
            buttonBox.setAlignment(Pos.CENTER);
            
            Button okButton = new Button("🚀 Los geht's!");
            okButton.getStyleClass().add("select-project-button");
            okButton.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-padding: 10px 20px;");
            okButton.setDisable(true); // Initial deaktiviert
            
            // Text-Change-Listener für Button-Aktivierung
            dirField.textProperty().addListener((observable, oldValue, newValue) -> {
                String path = newValue.trim();
                boolean isValid = !path.isEmpty() && new File(path).exists() && new File(path).isDirectory();
                okButton.setDisable(!isValid);
                
                // Visuelles Feedback
                if (isValid) {
                    dirField.setStyle("-fx-border-color: #27ae60; -fx-border-width: 2px;");
                } else if (!path.isEmpty()) {
                    dirField.setStyle("-fx-border-color: #e74c3c; -fx-border-width: 2px;");
                } else {
                    dirField.setStyle("-fx-border-color: #bdc3c7; -fx-border-width: 1px;");
                }
            });
            
            okButton.setOnAction(e -> {
                String selectedPath = dirField.getText().trim();
                if (!selectedPath.isEmpty()) {
                    // Speichere das Root-Verzeichnis
                    ResourceManager.saveParameter("project.root.directory", selectedPath);
                    chooserStage.close();
                } else {
                    showError("Fehler", "Bitte wähle ein Verzeichnis aus.");
                }
            });
            
            Button cancelButton = new Button("❌ Abbrechen");
            cancelButton.getStyleClass().add("cancel-button");
            cancelButton.setOnAction(e -> chooserStage.close());
            
            buttonBox.getChildren().addAll(okButton, cancelButton);
            
            // Layout zusammenbauen
            mainLayout.getChildren().addAll(titleLabel, descLabel, dirBox, buttonBox);
            
            // Scene erstellen
            Scene scene = new Scene(mainLayout);
            
            // CSS laden
            String cssPath = ResourceManager.getCssResource("css/manuskript.css");
            if (cssPath != null) {
                scene.getStylesheets().add(cssPath);
            }
            
            chooserStage.setSceneWithTitleBar(scene);
            chooserStage.setFullTheme(currentThemeIndex);
            chooserStage.initOwner(primaryStage);
            chooserStage.showAndWait();
            
        } catch (Exception e) {
            logger.error("Fehler beim Öffnen des Root-Verzeichnis-Chooser", e);
            showError("Fehler", "Root-Verzeichnis-Chooser konnte nicht geöffnet werden: " + e.getMessage());
        }
    }
    
    /**
     * TEST: Zeigt eine einfache CustomStage mit nur Text
     */
    private void showTestCustomStage() {
        try {
            // Erstelle CustomStage
            CustomStage testStage = new CustomStage();
            testStage.setCustomTitle("Test CustomStage");
            testStage.setMinWidth(400);
            testStage.setMinHeight(300);
            testStage.setWidth(500);
            testStage.setHeight(400);
            
            // Einfacher Text
            Label testLabel = new Label("Das ist ein Test-Text");
            testLabel.setStyle("-fx-font-size: 16px; -fx-padding: 20px;");
            
            VBox testLayout = new VBox(20);
            testLayout.setPadding(new Insets(20));
            testLayout.getChildren().add(testLabel);
            
            // Scene erstellen
            Scene testScene = new Scene(testLayout);
            
            // CSS laden
            String cssPath = ResourceManager.getCssResource("css/manuskript.css");
            if (cssPath != null) {
                testScene.getStylesheets().add(cssPath);
            }
            
            testStage.setSceneWithTitleBar(testScene);
            
            // CustomStage Theme anwenden NACH setSceneWithTitleBar
            testStage.setFullTheme(currentThemeIndex);
            testStage.initOwner(primaryStage);
            testStage.showAndWait();
            
        } catch (Exception e) {
            logger.error("Fehler beim Öffnen der Test-CustomStage", e);
        }
    }
    
    private void setupFlowPaneDragHandlers(FlowPane projectFlow, CustomStage projectStage) {
        projectFlow.setOnDragOver(event -> {
            if (draggingProjectItem != null && event.getGestureSource() != projectFlow) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });

        projectFlow.setOnDragDropped(event -> {
            if (draggingProjectItem == null) {
                return;
            }

            Node targetNode = event.getPickResult().getIntersectedNode();
            if (targetNode == null) {
                return;
            }

            while (targetNode != projectFlow && (targetNode.getUserData() == null || !(targetNode.getUserData() instanceof ProjectDisplayItem))) {
                targetNode = targetNode.getParent();
            }

            if (targetNode == null || targetNode == projectFlow) {
                return;
            }

            ProjectDisplayItem targetItem = (ProjectDisplayItem) targetNode.getUserData();
            if (targetItem == null || targetItem == draggingProjectItem) {
                return;
            }

            int fromIndex = projectItems.indexOf(draggingProjectItem);
            int toIndex = projectItems.indexOf(targetItem);

            if (fromIndex >= 0 && toIndex >= 0 && fromIndex != toIndex) {
                // Berechne die tatsächliche Drop-Position basierend auf der Maus-Position
                Point2D localPoint = projectFlow.sceneToLocal(event.getSceneX(), event.getSceneY());
                double dropX = localPoint.getX();
                double dropY = localPoint.getY();
                
                // Finde die beste Position basierend auf der Maus-Position
                int calculatedToIndex = calculateDropIndex(projectFlow, dropX, dropY, targetItem);
                if (calculatedToIndex >= 0) {
                    toIndex = calculatedToIndex;
                }
                
                projectItems.remove(fromIndex);
                if (fromIndex < toIndex) {
                    toIndex--;
                }
                if (toIndex < 0) {
                    toIndex = 0;
                }
                if (toIndex > projectItems.size()) {
                    toIndex = projectItems.size();
                }
                projectItems.add(toIndex, draggingProjectItem);
                renderProjectItems(projectFlow, currentProjectStage);
                saveProjectOrder();
            }

            event.setDropCompleted(true);
            event.consume();
        });
    }

    private int calculateDropIndex(FlowPane projectFlow, double dropX, double dropY, ProjectDisplayItem targetItem) {
        List<Node> projectNodes = projectFlow.getChildren().stream()
                .filter(child -> child instanceof VBox)
                .collect(Collectors.toList());
        
        int targetIndex = projectItems.indexOf(targetItem);
        if (targetIndex < 0) {
            return -1;
        }
        
        // Finde die beste Position basierend auf der Maus-Position
        for (int i = 0; i < projectNodes.size(); i++) {
            VBox card = (VBox) projectNodes.get(i);
            Bounds bounds = card.getBoundsInParent();
            
            double gap = 20;
            double hitLeft = bounds.getMinX() - gap;
            double hitRight = bounds.getMaxX() + gap;
            double hitTop = bounds.getMinY() - gap;
            double hitBottom = bounds.getMaxY() + gap;
            
            if (dropX >= hitLeft && dropX <= hitRight && dropY >= hitTop && dropY <= hitBottom) {
                // Maus ist über dieser Karte
                double cardCenterX = bounds.getMinX() + bounds.getWidth() / 2;
                double cardCenterY = bounds.getMinY() + bounds.getHeight() / 2;
                
                if (dropX < cardCenterX) {
                    // Links von der Mitte - vor diese Karte einfügen
                    return i;
                } else {
                    // Rechts von der Mitte - nach dieser Karte einfügen
                    return i + 1;
                }
            }
        }
        
        // Fallback: Am Ende einfügen
        return projectItems.size();
    }

    private void attachDragHandlers(Node card, FlowPane projectFlow) {
        card.addEventFilter(MouseEvent.DRAG_DETECTED, event -> {
            if (!(card instanceof VBox)) {
                return;
            }
            ProjectDisplayItem item = (ProjectDisplayItem) card.getUserData();
            if (item == null) {
                return;
            }
            draggingProjectItem = item;
            card.setCursor(Cursor.CLOSED_HAND);
            Dragboard dragboard = card.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(item.getDirectory().getAbsolutePath());
            dragboard.setContent(content);
            card.setOpacity(0.4);
            event.consume();
        });

        card.addEventFilter(DragEvent.DRAG_DONE, event -> {
            card.setOpacity(1.0);
            card.setCursor(Cursor.DEFAULT);
            draggingProjectItem = null;
            event.consume();
        });
    }

    private static class ProjectDisplayItem {
        private final File directory;
        private final List<File> seriesBooks;
        private final File[] docxFiles;

        ProjectDisplayItem(File directory, List<File> seriesBooks) {
            this.directory = directory;
            this.seriesBooks = new ArrayList<>(seriesBooks);
            this.docxFiles = null;
        }

        ProjectDisplayItem(File directory, File[] docxFiles) {
            this.directory = directory;
            this.docxFiles = docxFiles.clone();
            this.seriesBooks = null;
        }

        boolean isSeries() {
            return seriesBooks != null;
        }

        File getDirectory() {
            return directory;
        }

        List<File> getSeriesBooks() {
            if (seriesBooks == null) {
                return new ArrayList<>();
            }
            return seriesBooks;
        }

        File[] getDocxFiles() {
            return docxFiles;
        }

        String getId() {
            return directory.getAbsolutePath();
        }
    }

    private void renderProjectItems(FlowPane projectFlow, CustomStage projectStage) {
        projectFlow.getChildren().clear();
        final double targetCardHeight = 400;


        for (ProjectDisplayItem item : projectItems) {
            if (item.isSeries()) {
                VBox seriesCard = createSeriesCard(item, projectStage);
                seriesCard.setUserData(item);
                seriesCard.setCursor(Cursor.OPEN_HAND);
                attachDragHandlers(seriesCard, projectFlow);
                projectFlow.getChildren().add(seriesCard);
            } else {
                VBox projectCard = createProjectCard(item.getDirectory(), item.getDocxFiles(), projectStage);
                Region spacerBefore = createDebugSpacer(targetCardHeight);
                Region spacerAfter = createDebugSpacer(targetCardHeight);
                projectCard.setUserData(item);
                projectCard.setCursor(Cursor.OPEN_HAND);
                attachDragHandlers(projectCard, projectFlow);
                projectFlow.getChildren().addAll(spacerBefore, projectCard, spacerAfter);
            }
        }
    }

    private void saveProjectOrder(File baseDirectory) {
        if (baseDirectory == null) {
            return;
        }
        try {
            List<Map<String, Object>> serialized = new ArrayList<>();
            for (ProjectDisplayItem item : projectItems) {
                Map<String, Object> entry = new HashMap<>();
                entry.put("directory", item.getDirectory().getAbsolutePath());
                if (item.isSeries()) {
                    entry.put("type", "series");
                    List<String> bookPaths = item.getSeriesBooks().stream()
                            .map(File::getAbsolutePath)
                            .collect(Collectors.toList());
                    entry.put("books", bookPaths);
                } else {
                    entry.put("type", "single");
                }
                serialized.add(entry);
            }
            String json = new Gson().toJson(serialized);
            Path orderFile = getProjectOrderPath(baseDirectory);
            Files.createDirectories(orderFile.getParent());
            Files.writeString(orderFile, json, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            logger.warn("Konnte Projekt-Reihenfolge nicht speichern: {}", e.getMessage());
        }
    }

    private void saveProjectOrder() {
        if (projectRootDirectory != null) {
            saveProjectOrder(projectRootDirectory);
        }
    }

    private void loadProjectOrder(File searchDir) {
        try {
            Path orderFile = getProjectOrderPath(searchDir);
            if (!Files.exists(orderFile)) {
                projectItems = new ArrayList<>();
                return;
            }
            String json = Files.readString(orderFile, StandardCharsets.UTF_8);
            Type listType = new TypeToken<List<Map<String, Object>>>(){}.getType();
            List<Map<String, Object>> serialized = new Gson().fromJson(json, listType);
            projectItems = new ArrayList<>();
            for (Map<String, Object> entry : serialized) {
                String directoryPath = (String) entry.get("directory");
                File directory = new File(directoryPath);
                if (!directory.exists() || !directory.isDirectory()) {
                    continue;
                }
                String type = (String) entry.get("type");
                if ("series".equals(type)) {
                    List<String> bookPaths = (List<String>) entry.get("books");
                    List<File> seriesBooks = new ArrayList<>();
                    if (bookPaths != null) {
                        for (String path : bookPaths) {
                            File bookDir = new File(path);
                            if (bookDir.exists() && bookDir.isDirectory()) {
                                seriesBooks.add(bookDir);
                            }
                        }
                    }
                    projectItems.add(new ProjectDisplayItem(directory, seriesBooks));
                } else {
                    File[] docxFiles = directory.listFiles((d, name) -> name.toLowerCase().endsWith(".docx"));
                    if (docxFiles != null && docxFiles.length > 0) {
                        projectItems.add(new ProjectDisplayItem(directory, docxFiles));
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Konnte Projekt-Reihenfolge nicht laden: {}", e.getMessage());
            projectItems = new ArrayList<>();
        }
    }

    private Path getProjectOrderPath(File baseDirectory) {
        Path configDir = Path.of(ResourceManager.getConfigDirectory(), "sessions");
        String sanitized = baseDirectory.getAbsolutePath()
                .replace(':', '_')
                .replace('\\', '_')
                .replace('/', '_');
        return configDir.resolve("project_order_" + sanitized + ".json");
    }

    public File getProjectRootDirectory() {
        return projectRootDirectory;
    }

    /**
     * Formatiert Markdown-Inhalt, um sicherzustellen, dass zwischen Absätzen Leerzeilen stehen.
     * Einfache Version für bessere EPUB-Darstellung auf Apple-Geräten.
     */
    private String formatMarkdownParagraphs(String content) {
        if (content == null || content.trim().isEmpty()) {
            return content;
        }

        try {
            // Schütze Tabellen vor Leerzeilen-Einfügung
            // Teile den Inhalt in Tabellen und Nicht-Tabellen-Bereiche
            String[] lines = content.split("\n");
            StringBuilder result = new StringBuilder();
            boolean inTable = false;
            
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                
                // Prüfe, ob wir in einer Tabelle sind
                if (line.trim().startsWith("|") && line.trim().endsWith("|")) {
                    inTable = true;
                    result.append(line).append("\n");
                } else if (inTable && (line.trim().isEmpty() || !line.trim().startsWith("|"))) {
                    // Tabelle ist zu Ende
                    inTable = false;
                    result.append("\n").append(line).append("\n");
                } else if (!inTable) {
                    // Normaler Text - füge Leerzeilen zwischen Absätzen hinzu
                    if (i > 0 && !lines[i-1].trim().isEmpty() && !line.trim().isEmpty() && 
                        !line.trim().startsWith("#") && !lines[i-1].trim().startsWith("#")) {
                        result.append("\n");
                    }
                    result.append(line).append("\n");
                } else {
                    // In Tabelle - keine Leerzeilen einfügen
                    result.append(line).append("\n");
                }
            }
            
            String formatted = result.toString();

            return formatted;

        } catch (Exception e) {
            logger.warn("Fehler bei der Markdown-Formatierung: {}", e.getMessage());
            // Bei Fehler gib den Original-Inhalt zurück
            return content;
        }
    }
    
    /**
     * Fügt Leerzeilen zwischen Absätzen hinzu, wenn nötig
     * Erkennt Absätze anhand von Satzendezeichen und fügt eine Leerzeile hinzu
     */
    private String addParagraphSpacing(String text) {
        if (text == null || text.isEmpty()) return text;
        
        String[] lines = text.split("\n");
        StringBuilder result = new StringBuilder();
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            result.append(line);
            
            // Füge Leerzeile hinzu, wenn:
            // 1. Die Zeile mit Satzendezeichen endet (. ! ?)
            // 2. Es ist nicht die letzte Zeile
            // 3. Die nächste Zeile ist nicht leer
            // 4. Die nächste Zeile beginnt mit Großbuchstaben
            if (i < lines.length - 1 && 
                !line.trim().isEmpty() && 
                (line.trim().endsWith(".") || line.trim().endsWith("!") || line.trim().endsWith("?")) &&
                !lines[i + 1].trim().isEmpty() &&
                lines[i + 1].trim().length() > 0 &&
                Character.isUpperCase(lines[i + 1].trim().charAt(0))) {
                
                result.append("\n"); // Leerzeile zwischen Absätzen
            }
            
            // Füge normale Zeilenumbruch hinzu (außer bei der letzten Zeile)
            if (i < lines.length - 1) {
                result.append("\n");
            }
        }
        
        return result.toString();
    }
    
    private void deleteSelectedFile() {
        // Prüfen, ob eine Datei in der linken Tabelle ausgewählt ist
        DocxFile selectedFile = tableViewAvailable.getSelectionModel().getSelectedItem();
        if (selectedFile == null) {
            CustomAlert alert = new CustomAlert(Alert.AlertType.WARNING, "Keine Datei ausgewählt");
            alert.setHeaderText("Bitte wählen Sie eine Datei aus der linken Tabelle aus, die archiviert werden soll.");
            alert.applyTheme(currentThemeIndex);
            alert.initOwner(primaryStage);
            alert.showAndWait();
            return;
        }
        
        // Bestätigungsdialog
        CustomAlert confirmAlert = new CustomAlert(Alert.AlertType.CONFIRMATION, "Datei archivieren");
        confirmAlert.setHeaderText("Möchten Sie die Datei ins Archiv verschieben?");
        confirmAlert.setContentText("Datei: " + selectedFile.getFileName() + "\n\nDie Datei wird in das 'archiv' Verzeichnis verschoben.");
        confirmAlert.applyTheme(currentThemeIndex);
        confirmAlert.initOwner(primaryStage);
        
        if (confirmAlert.showAndWait().orElse(null) == ButtonType.OK) {
            try {
                File sourceFile = selectedFile.getFile();
                if (sourceFile.exists()) {
                    // Archiv-Verzeichnis erstellen
                    File projectDir = new File(txtDirectoryPath.getText());
                    File archiveDir = new File(projectDir, "archiv");
                    if (!archiveDir.exists()) {
                        archiveDir.mkdirs();
                    }
                    
                    // Ziel-Datei im Archiv
                    File targetFile = new File(archiveDir, sourceFile.getName());
                    
                    // Falls Datei bereits existiert, umbenennen
                    int counter = 1;
                    String baseName = sourceFile.getName();
                    String extension = "";
                    int dotIndex = baseName.lastIndexOf('.');
                    if (dotIndex > 0) {
                        extension = baseName.substring(dotIndex);
                        baseName = baseName.substring(0, dotIndex);
                    }
                    
                    while (targetFile.exists()) {
                        targetFile = new File(archiveDir, baseName + "_" + counter + extension);
                        counter++;
                    }
                    
                    // Datei verschieben
                    if (sourceFile.renameTo(targetFile)) {
                        // Datei erfolgreich archiviert - aus der Tabelle entfernen
                        tableViewAvailable.getItems().remove(selectedFile);
                        
                        // Erfolgsmeldung
                        CustomAlert successAlert = new CustomAlert(Alert.AlertType.INFORMATION, "Datei archiviert");
                        successAlert.setHeaderText("Die Datei wurde erfolgreich archiviert.");
                        successAlert.setContentText("Ziel: " + targetFile.getName());
                        successAlert.applyTheme(currentThemeIndex);
                        successAlert.initOwner(primaryStage);
                        successAlert.showAndWait();
                        
                        // Tabellen aktualisieren
                        tableViewAvailable.refresh();
                        tableViewSelected.refresh();
                    } else {
                        CustomAlert errorAlert = new CustomAlert(Alert.AlertType.ERROR, "Fehler beim Archivieren");
                        errorAlert.setHeaderText("Die Datei konnte nicht archiviert werden.");
                        errorAlert.setContentText("Möglicherweise ist die Datei gesperrt oder Sie haben keine Berechtigung.");
                        errorAlert.applyTheme(currentThemeIndex);
                        errorAlert.initOwner(primaryStage);
                        errorAlert.showAndWait();
                    }
                } else {
                    CustomAlert errorAlert = new CustomAlert(Alert.AlertType.ERROR, "Datei nicht gefunden");
                    errorAlert.setHeaderText("Die Datei existiert nicht mehr.");
                    errorAlert.applyTheme(currentThemeIndex);
                    errorAlert.initOwner(primaryStage);
                    errorAlert.showAndWait();
                }
            } catch (Exception e) {
                CustomAlert errorAlert = new CustomAlert(Alert.AlertType.ERROR, "Fehler beim Archivieren");
                errorAlert.setHeaderText("Ein Fehler ist aufgetreten:");
                errorAlert.setContentText(e.getMessage());
                errorAlert.applyTheme(currentThemeIndex);
                errorAlert.initOwner(primaryStage);
                errorAlert.showAndWait();
            }
        }
    }
}
