package com.manuskript;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.util.Duration;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
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
import java.util.concurrent.CompletableFuture;
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
    private ToggleButton autoScrollToggle;
    
    // Gespeicherter vollständiger Content (unfiltered) – für Filter-Wechsel
    private String fullManuskriptContent = "";
    private String fullDebugContent = "";

    /** Puffer für unvollständige Zeile an Chunk-Grenze (Tail) */
    private String tailBufferManuskript = "";
    private String tailBufferDebug = "";

    /** Maximale Zeichen im Log-Puffer – verhindert Hänger bei sehr großen Logs */
    private static final int MAX_CONTENT_CHARS = 512 * 1024;

    // Log-Level Pattern - verwende find() statt matches() für bessere Erkennung
    // Pattern sucht nach Log-Level im Format: "2025-11-01 21:11:13.123 [thread] INFO logger - message"
    private static final Pattern PATTERN_ERROR = Pattern.compile("\\bERROR\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_WARN = Pattern.compile("\\bWARN\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_INFO = Pattern.compile("\\bINFO\\b", Pattern.CASE_INSENSITIVE);

    /**
     * Kürzt content auf die letzten maxChars Zeichen (Schnitt an Zeilengrenze).
     * Verhindert, dass sehr langer Text die UI blockiert.
     */
    private static String trimContentToMax(String content, int maxChars) {
        if (content == null || content.length() <= maxChars) return content;
        int start = content.length() - maxChars;
        int newline = content.indexOf('\n', start);
        if (newline > start && newline < content.length()) {
            start = newline + 1;
        }
        return "... [ältere Einträge gekürzt]\n" + content.substring(start);
    }

    /**
     * Entfernt am Anfang der TextArea Zeichen, wenn sie MAX_CONTENT_CHARS übersteigt (Schnitt an Zeilengrenze).
     */
    private void trimTextAreaIfNeeded(TextArea area) {
        if (area == null) return;
        String t = area.getText();
        if (t.length() <= MAX_CONTENT_CHARS) return;
        int toRemove = t.length() - MAX_CONTENT_CHARS;
        int cut = t.indexOf('\n', toRemove);
        if (cut > toRemove && cut < t.length()) toRemove = cut + 1;
        else if (toRemove < t.length() && t.charAt(toRemove) == '\n') toRemove++;
        area.deleteText(0, toRemove);
        String rest = area.getText();
        if (!rest.startsWith("... [ältere Einträge gekürzt]\n")) {
            area.insertText(0, "... [ältere Einträge gekürzt]\n");
        }
    }

    /**
     * Liefert gefilterte neue Zeilen zum Anhängen; aktualisiert den Tail-Puffer (nur vollständige Zeilen).
     */
    private String extractAndFilterNewLines(String newContent, boolean isManuskript) {
        String buf = isManuskript ? tailBufferManuskript : tailBufferDebug;
        String combined = buf + newContent;
        String[] parts = combined.split("\n", -1);
        boolean endsWithNewline = combined.endsWith("\n");
        String newBuf;
        String completeLines;
        if (endsWithNewline) {
            newBuf = "";
            completeLines = parts.length > 0 ? String.join("\n", Arrays.copyOf(parts, parts.length - 1)) + "\n" : "";
        } else {
            newBuf = parts[parts.length - 1];
            completeLines = parts.length > 1 ? String.join("\n", Arrays.copyOf(parts, parts.length - 1)) + "\n" : "";
        }
        if (isManuskript) tailBufferManuskript = newBuf;
        else tailBufferDebug = newBuf;
        if (completeLines.isEmpty()) return "";
        return filterLogContent(completeLines, isManuskript);
    }

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
        
        // Autoscroll-Toggle (ein-/ausschaltbar, wird auch durch manuelles Scrollen deaktiviert)
        autoScrollToggle = new ToggleButton("↓ Autoscroll");
        autoScrollToggle.setSelected(true);
        autoScrollToggle.setTooltip(new Tooltip("Autoscroll ein/aus. Wird auch deaktiviert, wenn Sie manuell nach oben scrollen."));
        boolean savedAutoScroll = preferences.getBoolean("debug_autoscroll", true);
        autoScrollManuskript.set(savedAutoScroll);
        autoScrollDebug.set(savedAutoScroll);
        autoScrollToggle.setSelected(savedAutoScroll);
        updateAutoScrollToggleText();
        autoScrollToggle.setOnAction(e -> {
            boolean on = autoScrollToggle.isSelected();
            autoScrollManuskript.set(on);
            autoScrollDebug.set(on);
            preferences.putBoolean("debug_autoscroll", on);
            updateAutoScrollToggleText();
            if (on) {
                scrollToBottom(true);
                scrollToBottom(false);
            }
        });
        
        HBox topRow = new HBox(10);
        topRow.setAlignment(Pos.CENTER_LEFT);
        topRow.getChildren().addAll(filterLabel, levelPillsBox, autoScrollToggle);
        container.getChildren().addAll(topRow, stringFilterBox);
        
        return container;
    }
    
    private void updateAutoScrollToggleText() {
        if (autoScrollToggle == null) return;
        if (autoScrollToggle.isSelected()) {
            autoScrollToggle.setText("↓ Autoscroll");
            autoScrollToggle.setStyle("-fx-base: #28a745; -fx-text-fill: white;");
        } else {
            autoScrollToggle.setText("Autoscroll aus");
            autoScrollToggle.setStyle("");
        }
    }
    
    /** Synchronisiert den Toggle-Button mit den Auto-Scroll-Flags (wenn Nutzer manuell scrollt). */
    private void syncAutoScrollToggleFromFlags() {
        if (autoScrollToggle == null) return;
        boolean on = autoScrollManuskript.get() && autoScrollDebug.get();
        if (autoScrollToggle.isSelected() != on) {
            autoScrollToggle.setSelected(on);
            updateAutoScrollToggleText();
        }
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
     * Filtert die Logs basierend auf aktiven Filtern.
     * Filterung läuft im Hintergrund, damit große Logs die UI nicht blockieren.
     */
    private void filterLogs() {
        final String fullManu = fullManuskriptContent;
        final String fullDeb = fullDebugContent;
        final boolean shouldScrollManuskript = isScrolledToBottom(true) && autoScrollManuskript.get();
        final boolean shouldScrollDebug = isScrolledToBottom(false) && autoScrollDebug.get();

        CompletableFuture.supplyAsync(() -> {
            String f1 = filterLogContent(fullManu, true);
            String f2 = filterLogContent(fullDeb, false);
            return new String[]{f1, f2};
        }).thenAccept(filtered -> Platform.runLater(() -> {
            if (manuskriptLogArea != null) {
                manuskriptLogArea.setText(filtered[0]);
            }
            if (debugLogArea != null) {
                debugLogArea.setText(filtered[1]);
            }
            if (shouldScrollManuskript) {
                scrollToBottom(true);
            }
            if (shouldScrollDebug) {
                scrollToBottom(false);
            }
        }));
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
            
            // Manuskript-Log Scroll-Listener – bei manuellem Scrollen Autoscroll ggf. abschalten
            manuskriptLogArea.scrollTopProperty().addListener((obs, oldVal, newVal) -> {
                Platform.runLater(() -> {
                    if (isScrolledToBottom(true)) {
                        autoScrollManuskript.set(true);
                        syncAutoScrollToggleFromFlags();
                    } else {
                        if (oldVal != null && newVal.doubleValue() < oldVal.doubleValue()) {
                            autoScrollManuskript.set(false);
                            syncAutoScrollToggleFromFlags();
                        }
                    }
                });
            });
            
            // Debug-Log Scroll-Listener
            debugLogArea.scrollTopProperty().addListener((obs, oldVal, newVal) -> {
                Platform.runLater(() -> {
                    if (isScrolledToBottom(false)) {
                        autoScrollDebug.set(true);
                        syncAutoScrollToggleFromFlags();
                    } else {
                        if (oldVal != null && newVal.doubleValue() < oldVal.doubleValue()) {
                            autoScrollDebug.set(false);
                            syncAutoScrollToggleFromFlags();
                        }
                    }
                });
            });
            
            // Mouse-Wheel – bei Scroll nach oben Autoscroll ausschalten
            manuskriptLogArea.addEventFilter(ScrollEvent.SCROLL, e -> {
                if (Math.abs(e.getDeltaY()) > 0.1 || Math.abs(e.getDeltaX()) > 0.1) {
                    Platform.runLater(() -> {
                        if (!isScrolledToBottom(true)) {
                            autoScrollManuskript.set(false);
                            syncAutoScrollToggleFromFlags();
                        }
                    });
                }
            });
            
            debugLogArea.addEventFilter(ScrollEvent.SCROLL, e -> {
                if (Math.abs(e.getDeltaY()) > 0.1 || Math.abs(e.getDeltaX()) > 0.1) {
                    Platform.runLater(() -> {
                        if (!isScrolledToBottom(false)) {
                            autoScrollDebug.set(false);
                            syncAutoScrollToggleFromFlags();
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
     * Scrollt TextArea zum Ende. setText() setzt die Ansicht auf den Anfang; das Layout
     * braucht etwas Zeit. Mehrere verzögerte Scroll-Schritte sorgen dafür, dass wir
     * zuverlässig unten landen, sobald das Layout fertig ist.
     */
    private void scrollToBottom(boolean isManuskript) {
        TextArea textArea = isManuskript ? manuskriptLogArea : debugLogArea;
        if (textArea == null) return;

        Runnable doScroll = () -> {
            int len = textArea.getText().length();
            if (len == 0) return;
            textArea.positionCaret(len);
            textArea.deselect();
            textArea.setScrollTop(Double.MAX_VALUE);
        };

        Platform.runLater(doScroll);
        scheduleScroll(doScroll, textArea, 80);
        scheduleScroll(doScroll, textArea, 200);
        scheduleScroll(doScroll, textArea, 400);
    }

    private void scheduleScroll(Runnable doScroll, TextArea textArea, int delayMs) {
        PauseTransition pause = new PauseTransition(Duration.millis(delayMs));
        pause.setOnFinished(e -> Platform.runLater(doScroll));
        pause.play();
    }
    
    /**
     * Lädt initial die Log-Dateien
     */
    private void loadInitialLogs() {
        loadLogFileInitial(manuskriptLogFile, manuskriptLogArea, lastReadPositionManuskript, true);
        loadLogFileInitial(debugLogFile, debugLogArea, lastReadPositionDebug, false);
    }
    
    /**
     * Lädt eine Log-Datei initial. Lese-Arbeit im Hintergrund, damit große Dateien
     * die FX-Thread nicht blockieren. Puffer wird auf MAX_CONTENT_CHARS begrenzt.
     */
    private void loadLogFileInitial(File logFile, TextArea textArea, AtomicLong lastPosition, boolean isManuskript) {
        if (!logFile.exists()) {
            Platform.runLater(() -> {
                if (textArea != null) textArea.setText("Log-Datei nicht gefunden: " + logFile.getPath());
            });
            return;
        }
        final boolean isManu = isManuskript;
        CompletableFuture.supplyAsync(() -> {
            try (RandomAccessFile raf = new RandomAccessFile(logFile, "r")) {
                long currentLength = logFile.length();
                if (currentLength <= 0) return new Object[]{ "", 0L };
                long toRead = currentLength;
                long startOffset = 0;
                if (currentLength > MAX_CONTENT_CHARS) {
                    startOffset = currentLength - MAX_CONTENT_CHARS;
                    toRead = MAX_CONTENT_CHARS;
                    raf.seek(startOffset);
                } else {
                    raf.seek(0);
                }
                byte[] buffer = new byte[(int) toRead];
                raf.readFully(buffer);
                String content = new String(buffer, StandardCharsets.UTF_8);
                if (currentLength > MAX_CONTENT_CHARS) {
                    int firstNewline = content.indexOf('\n');
                    if (firstNewline >= 0) content = content.substring(firstNewline + 1);
                    content = "... [Anfang der Datei gekürzt]\n" + content;
                }
                return new Object[]{ content, currentLength };
            } catch (IOException e) {
                logger.error("Fehler beim Lesen der Log-Datei: " + logFile.getPath(), e);
                return new Object[]{ "", 0L };
            }
        }).thenAccept(pair -> {
            String content = trimContentToMax((String) pair[0], MAX_CONTENT_CHARS);
            long pos = (Long) pair[1];
            Platform.runLater(() -> {
                if (isManu) {
                    fullManuskriptContent = content;
                } else {
                    fullDebugContent = content;
                }
                lastPosition.set(pos);
                if (isManu) tailBufferManuskript = "";
                else tailBufferDebug = "";
                filterLogs();
                AtomicBoolean scrollFlag = isManu ? autoScrollManuskript : autoScrollDebug;
                scrollFlag.set(true);
                scrollToBottom(isManu);
            });
        });
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
                    final String newPart = newContent;
                    final boolean isManu = isManuskript;
                    Platform.runLater(() -> {
                        if (isManu) {
                            fullManuskriptContent = trimContentToMax(fullManuskriptContent + newPart, MAX_CONTENT_CHARS);
                        } else {
                            fullDebugContent = trimContentToMax(fullDebugContent + newPart, MAX_CONTENT_CHARS);
                        }
                        String toAppend = extractAndFilterNewLines(newPart, isManu);
                        if (!toAppend.isEmpty()) {
                            TextArea area = isManu ? manuskriptLogArea : debugLogArea;
                            if (area != null) {
                                area.appendText(toAppend);
                                trimTextAreaIfNeeded(area);
                            }
                        }
                        if ((isManu ? autoScrollManuskript : autoScrollDebug).get()) {
                            scrollToBottom(isManu);
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

