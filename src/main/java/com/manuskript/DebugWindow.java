package com.manuskript;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;

/**
 * Debug-Fenster zum Anzeigen von Log-Dateien mit Tail-Funktionalität
 */
public class DebugWindow {
    
    private static final Logger logger = LoggerFactory.getLogger(DebugWindow.class);
    
    private static DebugWindow instance;
    private CustomStage stage;
    private TextArea manuskriptLogArea;
    private TextArea debugLogArea;
    private SplitPane splitPane;
    private VBox filterContainer;
    private Set<String> activeFilters;
    private Set<String> stringFilters; // String-Filter (nicht Log-Level)
    private TextField stringFilterInput;
    private HBox stringFilterPillsBox;
    private AtomicBoolean autoScrollManuskript = new AtomicBoolean(true);
    private AtomicBoolean autoScrollDebug = new AtomicBoolean(true);
    private AtomicLong lastReadPositionManuskript = new AtomicLong(0);
    private AtomicLong lastReadPositionDebug = new AtomicLong(0);
    private File manuskriptLogFile;
    private File debugLogFile;
    private Timer watchTimer;
    private Preferences preferences;
    
    // Gespeicherter vollständiger Content (unfiltered)
    private String fullManuskriptContent = "";
    private String fullDebugContent = "";
    
    // Log-Level Pattern - verwende find() statt matches() für bessere Erkennung
    // Pattern sucht nach Log-Level im Format: "2025-11-01 21:11:13.123 [thread] INFO logger - message"
    private static final Pattern PATTERN_ERROR = Pattern.compile("\\bERROR\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_WARN = Pattern.compile("\\bWARN\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_INFO = Pattern.compile("\\bINFO\\b", Pattern.CASE_INSENSITIVE);
    
    private DebugWindow(Window owner) {
        this.preferences = Preferences.userNodeForPackage(DebugWindow.class);
        this.activeFilters = new HashSet<>();
        this.activeFilters.addAll(Arrays.asList("ERROR", "WARN", "INFO")); // DEBUG entfernt
        this.stringFilters = new HashSet<>(); // String-Filter initialisieren
        
        // Log-Dateien-Pfade
        this.manuskriptLogFile = new File("logs/manuskript.log");
        this.debugLogFile = new File("logs/debug.log");
        
        initializeWindow(owner);
        setupLogWatchers();
    }
    
    /**
     * Öffnet oder zeigt das Debug-Fenster
     */
    public static void show(Window owner) {
        if (instance == null || instance.stage == null || !instance.stage.isShowing()) {
            instance = new DebugWindow(owner);
        } else {
            instance.stage.toFront();
            instance.stage.requestFocus();
        }
    }
    
    /**
     * Initialisiert das Fenster
     */
    private void initializeWindow(Window owner) {
        stage = StageManager.createStage("Debug-Fenster");
        stage.setMinWidth(1200);
        stage.setMinHeight(700);
        stage.setWidth(1400);
        stage.setHeight(900);
        
        // Preferences für Fenster-Größe laden
        loadWindowProperties();
        
        // Hauptcontainer
        VBox root = new VBox(10);
        root.setPadding(new Insets(15));
        root.getStyleClass().add("debug-window");
        
        // Filter-Bereich mit Pills
        filterContainer = createFilterPills();
        
        // SplitPane für beide Log-Dateien (nebeneinander)
        splitPane = new SplitPane();
        splitPane.setDividerPositions(0.5);
        
        // Manuskript-Log
        VBox manuskriptContainer = createLogContainer("Manuskript Log", true);
        manuskriptLogArea = (TextArea) manuskriptContainer.getChildren().get(1);
        
        // Debug-Log
        VBox debugContainer = createLogContainer("Debug Log", false);
        debugLogArea = (TextArea) debugContainer.getChildren().get(1);
        
        splitPane.getItems().addAll(manuskriptContainer, debugContainer);
        VBox.setVgrow(splitPane, Priority.ALWAYS);
        
        root.getChildren().addAll(filterContainer, splitPane);
        
        // Scene erstellen
        Scene scene = new Scene(root);
        
        // CSS laden
        String cssPath = ResourceManager.getCssResource("css/manuskript.css");
        if (cssPath != null) {
            scene.getStylesheets().add(cssPath);
        }
        
        // Scene setzen
        stage.setSceneWithTitleBar(scene);
        
        // Theme anwenden
        int currentTheme = preferences.getInt("main_window_theme", 
            Preferences.userNodeForPackage(MainController.class).getInt("main_window_theme", 0));
        stage.setFullTheme(currentTheme);
        
        // Initiales Laden der Logs - mit Verzögerung damit UI bereit ist
        Platform.runLater(() -> {
            setupScrollListeners();
            // Kurze Verzögerung, damit ScrollPanes vollständig initialisiert sind
            Platform.runLater(() -> {
                loadInitialLogs();
            });
        });
        
        // Fenster-Listener für Speichern der Eigenschaften
        setupWindowListeners();
        
        // Close-Handler
        stage.setOnCloseRequest(e -> {
            cleanup();
        });
        
        stage.show();
    }
    
    /**
     * Erstellt Filter-Pills
     */
    private VBox createFilterPills() {
        VBox container = new VBox(10);
        container.setPadding(new Insets(5));
        container.getStyleClass().add("filter-container");
        
        // Log-Level Filter
        HBox levelPillsBox = new HBox(10);
        levelPillsBox.setAlignment(Pos.CENTER_LEFT);
        levelPillsBox.getStyleClass().add("filter-pills");
        
        Label filterLabel = new Label("Filter:");
        filterLabel.getStyleClass().add("filter-label");
        
        // Pills für Log-Level (DEBUG entfernt)
        String[] levels = {"ERROR", "WARN", "INFO"};
        String[] colors = {"#dc3545", "#ffc107", "#0d6efd"}; // Rot, Gelb, Blau
        
        for (int i = 0; i < levels.length; i++) {
            String level = levels[i];
            String color = colors[i];
            
            Button pill = new Button(level);
            pill.getStyleClass().add("filter-pill");
            pill.getStyleClass().add("filter-pill-active");
            pill.setStyle(String.format(
                "-fx-background-color: %s; -fx-text-fill: white; -fx-padding: 5 12; " +
                "-fx-background-radius: 15; -fx-border-radius: 15; -fx-font-size: 11px;",
                color
            ));
            
            final String levelKey = level;
            pill.setOnAction(e -> toggleFilter(levelKey, pill));
            
            levelPillsBox.getChildren().add(pill);
        }
        
        // String-Filter Bereich
        HBox stringFilterBox = new HBox(10);
        stringFilterBox.setAlignment(Pos.CENTER_LEFT);
        stringFilterBox.getStyleClass().add("string-filter-box");
        
        Label stringFilterLabel = new Label("Text-Filter:");
        stringFilterLabel.getStyleClass().add("filter-label");
        
        // Eingabefeld für String-Filter
        stringFilterInput = new TextField();
        stringFilterInput.setPromptText("Text zum Filtern eingeben...");
        stringFilterInput.setPrefWidth(200);
        stringFilterInput.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                addStringFilter();
            }
        });
        
        Button addButton = new Button("+");
        addButton.setOnAction(e -> addStringFilter());
        addButton.setStyle("-fx-padding: 5 10; -fx-font-size: 12px;");
        
        // Box für String-Filter-Pills
        stringFilterPillsBox = new HBox(5);
        stringFilterPillsBox.setAlignment(Pos.CENTER_LEFT);
        
        stringFilterBox.getChildren().addAll(stringFilterLabel, stringFilterInput, addButton, stringFilterPillsBox);
        
        container.getChildren().addAll(
            new HBox(10, filterLabel, levelPillsBox),
            stringFilterBox
        );
        
        return container;
    }
    
    /**
     * Fügt einen String-Filter hinzu
     */
    private void addStringFilter() {
        String filterText = stringFilterInput.getText().trim();
        if (filterText.isEmpty()) {
            return;
        }
        
        // Duplikate vermeiden
        if (stringFilters.contains(filterText)) {
            stringFilterInput.clear();
            return;
        }
        
        stringFilters.add(filterText);
        logger.debug("String-Filter hinzugefügt: '{}'. Aktive String-Filter: {}", filterText, stringFilters);
        createStringFilterPill(filterText);
        stringFilterInput.clear();
        
        // Logs neu filtern
        filterLogs();
    }
    
    /**
     * Erstellt eine Pill für String-Filter mit X-Button
     */
    private void createStringFilterPill(String filterText) {
        HBox pill = new HBox(5);
        pill.setAlignment(Pos.CENTER_LEFT);
        pill.setStyle(
            "-fx-background-color: #6c757d; -fx-background-radius: 15; " +
            "-fx-border-radius: 15; -fx-padding: 5 8 5 12;"
        );
        pill.getStyleClass().add("string-filter-pill");
        
        Label textLabel = new Label(filterText);
        textLabel.setStyle("-fx-text-fill: white; -fx-font-size: 11px;");
        
        Button removeButton = new Button("×");
        removeButton.setStyle(
            "-fx-background-color: transparent; -fx-text-fill: white; " +
            "-fx-font-size: 16px; -fx-font-weight: bold; -fx-padding: 0 2 0 5; " +
            "-fx-cursor: hand;"
        );
        removeButton.setOnAction(e -> removeStringFilter(filterText, pill));
        
        pill.getChildren().addAll(textLabel, removeButton);
        stringFilterPillsBox.getChildren().add(pill);
    }
    
    /**
     * Entfernt einen String-Filter
     */
    private void removeStringFilter(String filterText, HBox pill) {
        stringFilters.remove(filterText);
        stringFilterPillsBox.getChildren().remove(pill);
        
        // Logs neu filtern
        filterLogs();
    }
    
    /**
     * Toggle für Filter-Pill
     */
    private void toggleFilter(String level, Button pill) {
        if (activeFilters.contains(level)) {
            activeFilters.remove(level);
            pill.getStyleClass().remove("filter-pill-active");
            pill.setStyle(pill.getStyle().replace("-fx-opacity: 1.0", "-fx-opacity: 0.5"));
            pill.setStyle(pill.getStyle() + " -fx-opacity: 0.5;");
        } else {
            activeFilters.add(level);
            pill.getStyleClass().add("filter-pill-active");
            pill.setStyle(pill.getStyle().replace("-fx-opacity: 0.5", "-fx-opacity: 1.0"));
        }
        
        // Logs neu filtern
        filterLogs();
    }
    
    /**
     * Filtert die Logs basierend auf aktiven Filtern
     */
    private void filterLogs() {
        // Filter für Manuskript-Log
        String filteredManuskript = filterLogContent(fullManuskriptContent, true);
        boolean wasScrolledToBottomManuskript = isScrolledToBottom(true);
        boolean shouldScrollManuskript = wasScrolledToBottomManuskript && autoScrollManuskript.get();
        
        // Filter für Debug-Log
        String filteredDebug = filterLogContent(fullDebugContent, false);
        boolean wasScrolledToBottomDebug = isScrolledToBottom(false);
        boolean shouldScrollDebug = wasScrolledToBottomDebug && autoScrollDebug.get();
        
        // Text IMMER setzen (auch wenn unverändert, um sicherzustellen dass Filter angewendet wird)
        Platform.runLater(() -> {
            if (manuskriptLogArea != null) {
                manuskriptLogArea.setText(filteredManuskript);
            }
            if (debugLogArea != null) {
                debugLogArea.setText(filteredDebug);
            }
            
            // Nach Text-Updates scrollen (wenn Autoscroll aktiv)
            if (shouldScrollManuskript) {
                Platform.runLater(() -> {
                    Platform.runLater(() -> {
                        scrollToBottom(true);
                    });
                });
            }
            
            if (shouldScrollDebug) {
                Platform.runLater(() -> {
                    Platform.runLater(() -> {
                        scrollToBottom(false);
                    });
                });
            }
        });
    }
    
    /**
     * Filtert Log-Content basierend auf aktiven Filtern
     * @param content Der zu filternde Content
     * @param filterDebug wird nicht mehr verwendet (beide Logs werden gleich gefiltert)
     */
    private String filterLogContent(String content, boolean filterDebug) {
        // DEBUG-Pattern für explizites Ausfiltern
        Pattern PATTERN_DEBUG = Pattern.compile("\\bDEBUG\\b", Pattern.CASE_INSENSITIVE);
        
        // Wenn keine Filter aktiv sind: nichts anzeigen
        if (activeFilters.isEmpty() && stringFilters.isEmpty()) {
            return "";
        }
        
        StringBuilder filtered = new StringBuilder();
        String[] lines = content.split("\n");
        
        for (String line : lines) {
            if (line == null || line.trim().isEmpty()) {
                continue;
            }
            
            boolean isDebugLine = PATTERN_DEBUG.matcher(line).find();
            
            boolean shouldInclude = false;
            
            // Prüfe String-Filter ZUERST (völlig unabhängig von Log-Level-Filtern)
            // Wenn String-Filter aktiv ist, wird nur angezeigt wenn String-Filter matcht
            boolean matchesStringFilter = false;
            if (!stringFilters.isEmpty()) {
                String lineLower = line.toLowerCase();
                for (String stringFilter : stringFilters) {
                    String filterLower = stringFilter.toLowerCase().trim();
                    if (!filterLower.isEmpty()) {
                        if (lineLower.contains(filterLower)) {
                            matchesStringFilter = true;
                            shouldInclude = true;
                            break;
                        }
                    }
                }
                // Wenn String-Filter aktiv aber NICHT matcht: Zeile nicht anzeigen (außer DEBUG)
                // (wird unten bei DEBUG geprüft)
            }
            
            // Prüfe Log-Level-Filter (Prioritätsreihenfolge: ERROR, WARN, INFO)
            // Nur wenn String-Filter nicht aktiv ist ODER wenn String-Filter matcht hat
            if (!activeFilters.isEmpty() && (stringFilters.isEmpty() || matchesStringFilter)) {
                if (activeFilters.contains("ERROR") && PATTERN_ERROR.matcher(line).find()) {
                    shouldInclude = true;
                } else if (activeFilters.contains("WARN") && PATTERN_WARN.matcher(line).find()) {
                    shouldInclude = true;
                } else if (activeFilters.contains("INFO") && PATTERN_INFO.matcher(line).find()) {
                    shouldInclude = true;
                }
            }
            
            // DEBUG-Zeilen: IMMER anzeigen, AUSSER wenn String-Filter aktiv ist und NICHT matcht
            // Gilt für BEIDE Logs (manuskript.log und debug.log)
            if (isDebugLine) {
                if (!stringFilters.isEmpty()) {
                    // String-Filter aktiv: Nur anzeigen wenn String-Filter matcht
                    if (!matchesStringFilter) {
                        continue; // Skip DEBUG-Zeile wenn String-Filter NICHT matcht
                    }
                }
                // Wenn kein String-Filter aktiv: DEBUG-Zeile IMMER anzeigen
                shouldInclude = true;
            }
            
            // Zeile nur anzeigen wenn shouldInclude=true
            // ACHTUNG: Wenn nur String-Filter aktiv ist, zeige nur Zeilen die String-Filter matchen
            if (!stringFilters.isEmpty() && activeFilters.isEmpty() && !matchesStringFilter && !isDebugLine) {
                // Nur String-Filter aktiv, aber Zeile matcht nicht: nicht anzeigen
                continue;
            }
            
            if (shouldInclude) {
                filtered.append(line).append("\n");
            }
        }
        
        return filtered.toString();
    }
    
    /**
     * Erstellt einen Log-Container mit Label und TextArea
     */
    private VBox createLogContainer(String title, boolean isManuskript) {
        VBox container = new VBox(5);
        container.getStyleClass().add("log-container");
        
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("log-title");
        
        TextArea textArea = new TextArea();
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.getStyleClass().add("log-area");
        textArea.setStyle("-fx-font-family: 'Consolas', 'Monaco', 'Courier New', monospace; -fx-font-size: 11px;");
        
        // TextArea hat bereits eingebautes ScrollPane - kein zusätzliches ScrollPane nötig
        VBox.setVgrow(textArea, Priority.ALWAYS);
        
        container.getChildren().addAll(titleLabel, textArea);
        return container;
    }
    
    /**
     * Setzt Scroll-Listener für Autoscroll-Verwaltung
     * TextArea hat eingebautes ScrollPane, daher scrollTopProperty verwenden
     */
    private void setupScrollListeners() {
        Platform.runLater(() -> {
            if (manuskriptLogArea == null || debugLogArea == null) {
                // Nochmal versuchen nach kurzer Verzögerung
                new Timer("SetupScrollListeners", true).schedule(new TimerTask() {
                    @Override
                    public void run() {
                        Platform.runLater(() -> setupScrollListeners());
                    }
                }, 100);
                return;
            }
            
            // Manuskript-Log Scroll-Listener - scrollTopProperty für TextArea
            manuskriptLogArea.scrollTopProperty().addListener((obs, oldVal, newVal) -> {
                Platform.runLater(() -> {
                    if (isScrolledToBottom(true)) {
                        autoScrollManuskript.set(true);
                    } else {
                        // Prüfe ob nach oben gescrollt wurde
                        if (oldVal != null && newVal.doubleValue() < oldVal.doubleValue()) {
                            autoScrollManuskript.set(false);
                        }
                    }
                });
            });
            
            // Debug-Log Scroll-Listener
            debugLogArea.scrollTopProperty().addListener((obs, oldVal, newVal) -> {
                Platform.runLater(() -> {
                    if (isScrolledToBottom(false)) {
                        autoScrollDebug.set(true);
                    } else {
                        // Prüfe ob nach oben gescrollt wurde
                        if (oldVal != null && newVal.doubleValue() < oldVal.doubleValue()) {
                            autoScrollDebug.set(false);
                        }
                    }
                });
            });
            
            // Mouse-Wheel-Events überwachen
            manuskriptLogArea.addEventFilter(ScrollEvent.SCROLL, e -> {
                if (Math.abs(e.getDeltaY()) > 0.1 || Math.abs(e.getDeltaX()) > 0.1) {
                    Platform.runLater(() -> {
                        if (!isScrolledToBottom(true)) {
                            autoScrollManuskript.set(false);
                        }
                    });
                }
            });
            
            debugLogArea.addEventFilter(ScrollEvent.SCROLL, e -> {
                if (Math.abs(e.getDeltaY()) > 0.1 || Math.abs(e.getDeltaX()) > 0.1) {
                    Platform.runLater(() -> {
                        if (!isScrolledToBottom(false)) {
                            autoScrollDebug.set(false);
                        }
                    });
                }
            });
        });
    }
    
    /**
     * Prüft ob ganz unten gescrollt wurde
     */
    private boolean isScrolledToBottom(boolean isManuskript) {
        TextArea textArea = isManuskript ? manuskriptLogArea : debugLogArea;
        if (textArea == null) return true;
        
        // TextArea hat eingebautes ScrollPane
        // Berechne ob wir am Ende sind: Caret-Position vs Text-Länge
        int caretPos = textArea.getCaretPosition();
        int textLength = textArea.getText().length();
        
        // Wenn Caret am Ende (10 Zeichen Toleranz)
        if (caretPos >= textLength - 10) {
            // Zusätzlich prüfen: ScrollTop vs maximale Scroll-Position
            double scrollTop = textArea.getScrollTop();
            double height = textArea.getHeight();
            
            // Geschätzte maximale Scroll-Position basierend auf Text-Länge
            // TextArea scrollt basierend auf Zeilen, nicht Pixel
            // Wenn scrollTop sehr groß ist, sind wir wahrscheinlich unten
            // Oder wir prüfen ob der letzte Text sichtbar ist
            return true; // Wenn Caret am Ende, sind wir unten
        }
        
        return false;
    }
    
    /**
     * Scrollt TextArea zum Ende - korrekte Methode für TextArea
     * TextArea hat eingebautes ScrollPane, daher positionCaret verwenden
     */
    private void scrollToBottom(boolean isManuskript) {
        TextArea textArea = isManuskript ? manuskriptLogArea : debugLogArea;
        if (textArea == null) return;
        
        int textLength = textArea.getText().length();
        if (textLength == 0) return;
        
        Platform.runLater(() -> {
            // Methode 1: Setze Caret ans Ende - das scrollt automatisch
            textArea.positionCaret(textLength);
            textArea.deselect();
            
            // Methode 2: Setze ScrollTop auf großen Wert (wird automatisch begrenzt)
            double currentScrollTop = textArea.getScrollTop();
            double largeScrollTop = currentScrollTop + 50000; // Sehr großer Wert
            textArea.setScrollTop(largeScrollTop);
            
            // Methode 3: Nochmal positionCaret für Sicherheit
            Platform.runLater(() -> {
                textArea.positionCaret(textLength);
                textArea.deselect();
                
                // Final: Nochmal ScrollTop setzen mit noch größerem Wert
                double finalScrollTop = textArea.getScrollTop();
                textArea.setScrollTop(finalScrollTop + 50000);
            });
        });
    }
    
    /**
     * Lädt initial die Log-Dateien
     */
    private void loadInitialLogs() {
        loadLogFileInitial(manuskriptLogFile, manuskriptLogArea, lastReadPositionManuskript, true);
        loadLogFileInitial(debugLogFile, debugLogArea, lastReadPositionDebug, false);
    }
    
    /**
     * Lädt eine Log-Datei initial (komplett)
     */
    private void loadLogFileInitial(File logFile, TextArea textArea, AtomicLong lastPosition, boolean isManuskript) {
        if (!logFile.exists()) {
            Platform.runLater(() -> {
                textArea.setText("Log-Datei nicht gefunden: " + logFile.getPath());
            });
            return;
        }
        
        try (RandomAccessFile raf = new RandomAccessFile(logFile, "r")) {
            long currentLength = logFile.length();
            
            if (currentLength > 0) {
                raf.seek(0);
                byte[] buffer = new byte[(int) currentLength];
                raf.readFully(buffer);
                
                String content = new String(buffer, StandardCharsets.UTF_8);
                
                Platform.runLater(() -> {
                    // Vollständigen Content speichern
                    if (isManuskript) {
                        fullManuskriptContent = content;
                    } else {
                        fullDebugContent = content;
                    }
                    
                    lastPosition.set(currentLength);
                    
                    // Filter anwenden
                    filterLogs();
                    
                    // Scroll-Flag setzen
                    AtomicBoolean scrollFlag = isManuskript ? autoScrollManuskript : autoScrollDebug;
                    scrollFlag.set(true);
                    
                    // Zum Ende scrollen
                    scrollToBottom(isManuskript);
                });
            }
        } catch (IOException e) {
            logger.error("Fehler beim Lesen der Log-Datei: " + logFile.getPath(), e);
        }
    }
    
    /**
     * Lädt eine Log-Datei (nur neue Zeilen)
     */
    private void loadLogFile(File logFile, AtomicLong lastPosition, boolean isManuskript) {
        if (!logFile.exists()) {
            return;
        }
        
        try (RandomAccessFile raf = new RandomAccessFile(logFile, "r")) {
            // Nur neue Zeilen lesen
            long currentLength = logFile.length();
            long position = lastPosition.get();
            
            if (position > currentLength) {
                position = 0; // Datei wurde zurückgesetzt
            }
            
            if (position < currentLength) {
                raf.seek(position);
                byte[] buffer = new byte[(int) (currentLength - position)];
                raf.readFully(buffer);
                
                String newContent = new String(buffer, StandardCharsets.UTF_8);
                
                if (!newContent.trim().isEmpty()) {
                    // Neuen Content hinzufügen
                    Platform.runLater(() -> {
                        // Vollständigen Content aktualisieren
                        if (isManuskript) {
                            fullManuskriptContent += newContent;
                        } else {
                            fullDebugContent += newContent;
                        }
                        
                        // Scrollen wenn Autoscroll aktiv
                        AtomicBoolean shouldScroll = isManuskript 
                            ? autoScrollManuskript : autoScrollDebug;
                        
                        boolean needToScroll = shouldScroll.get();
                        
                        // Filter anwenden (aktualisiert die Anzeige) - das setzt den Text neu
                        filterLogs();
                        
                        // WICHTIG: Nach filterLogs() ist der Text neu gesetzt worden
                        // Daher müssen wir hier nochmal explizit scrollen, wenn Autoscroll aktiv ist
                        if (needToScroll) {
                            // Mehrere Platform.runLater für zuverlässiges Scrollen nach Text-Update
                            Platform.runLater(() -> {
                                Platform.runLater(() -> {
                                    Platform.runLater(() -> {
                                        scrollToBottom(isManuskript);
                                    });
                                });
                            });
                        }
                    });
                }
                
                lastPosition.set(currentLength);
            }
        } catch (IOException e) {
            logger.error("Fehler beim Lesen der Log-Datei: " + logFile.getPath(), e);
        }
    }
    
    /**
     * Setzt Log-Watcher auf
     */
    private void setupLogWatchers() {
        watchTimer = new Timer("DebugWindow-LogWatcher", true);
        
        // TimerTask für regelmäßiges Polling (alle 500ms)
        TimerTask watchTask = new TimerTask() {
            @Override
            public void run() {
                loadLogFile(manuskriptLogFile, lastReadPositionManuskript, true);
                loadLogFile(debugLogFile, lastReadPositionDebug, false);
            }
        };
        
        watchTimer.scheduleAtFixedRate(watchTask, 0, 500);
    }
    
    /**
     * Setzt Fenster-Listener für Preferences
     */
    private void setupWindowListeners() {
        stage.widthProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.doubleValue() > 0) {
                preferences.putDouble("debug_window_width", newVal.doubleValue());
            }
        });
        
        stage.heightProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.doubleValue() > 0) {
                preferences.putDouble("debug_window_height", newVal.doubleValue());
            }
        });
        
        stage.xProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.doubleValue() >= 0) {
                preferences.putDouble("debug_window_x", newVal.doubleValue());
            }
        });
        
        stage.yProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.doubleValue() >= 0) {
                preferences.putDouble("debug_window_y", newVal.doubleValue());
            }
        });
        
        // SplitPane Divider-Position speichern
        splitPane.getDividers().get(0).positionProperty().addListener((obs, oldVal, newVal) -> {
            preferences.putDouble("debug_window_divider", newVal.doubleValue());
        });
    }
    
    /**
     * Lädt Fenster-Properties aus Preferences
     */
    private void loadWindowProperties() {
        double width = preferences.getDouble("debug_window_width", 1400);
        double height = preferences.getDouble("debug_window_height", 900);
        double x = preferences.getDouble("debug_window_x", -1);
        double y = preferences.getDouble("debug_window_y", -1);
        double divider = preferences.getDouble("debug_window_divider", 0.5);
        
        if (width > 0) stage.setWidth(width);
        if (height > 0) stage.setHeight(height);
        if (x >= 0) stage.setX(x);
        if (y >= 0) stage.setY(y);
        
        Platform.runLater(() -> {
            if (divider > 0 && divider < 1) {
                splitPane.setDividerPositions(divider);
            }
        });
    }
    
    /**
     * Bereinigt Ressourcen
     */
    private void cleanup() {
        if (watchTimer != null) {
            watchTimer.cancel();
            watchTimer = null;
        }
        instance = null;
    }
}

