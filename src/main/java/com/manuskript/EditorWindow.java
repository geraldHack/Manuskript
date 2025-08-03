package com.manuskript;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.scene.control.Label;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Modality;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.input.KeyCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// RichTextFX Imports
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.fxmisc.flowless.VirtualizedScrollPane;
import java.util.Collection;
import java.util.Collections;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.prefs.Preferences;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import javafx.scene.control.cell.PropertyValueFactory;
import java.util.Properties;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.concurrent.atomic.AtomicBoolean;
import org.fxmisc.richtext.model.StyleSpan;

// Eigene Klassen
import com.manuskript.Macro;
import com.manuskript.MacroStep;
import com.manuskript.DocxProcessor;

public class EditorWindow implements Initializable {
    
    private static final Logger logger = LoggerFactory.getLogger(EditorWindow.class);
    
    @FXML private VBox textAreaContainer;
    private CodeArea codeArea;
    @FXML private VBox mainContainer;
    @FXML private HBox searchReplaceContainer;
    @FXML private VBox searchReplacePanel;
    @FXML private VBox macroPanel;
    
    // Chapter-Editor Components
    @FXML private SplitPane mainSplitPane;
    @FXML private VBox chapterEditorPanel;
    @FXML private TextArea chapterEditorArea;
    @FXML private Button btnChapterEditor;
    @FXML private Button btnSaveChapter;
    
    // Such- und Ersetzungs-Controls
    @FXML private CheckBox chkRegexSearch;
    @FXML private CheckBox chkCaseSensitive;
    @FXML private CheckBox chkWholeWord;
    @FXML private ComboBox<String> cmbSearchHistory;
    @FXML private ComboBox<String> cmbReplaceHistory;
    @FXML private Button btnFind;
    @FXML private Button btnReplace;
    @FXML private Button btnReplaceAll;
    @FXML private Button btnSaveSearch;
    @FXML private Button btnSaveReplace;
    @FXML private Button btnDeleteSearch;
    @FXML private Button btnDeleteReplace;
    @FXML private Button btnFindNext;
    @FXML private Button btnFindPrevious;
    @FXML private Label lblStatus;
    @FXML private Label lblMatchCount;
    @FXML private Label lblWindowTitle;
    
    // Toolbar-Buttons
    @FXML private Button btnSave;
    @FXML private Button btnSaveAs;
    @FXML private Button btnExportRTF;
    @FXML private Button btnExportDOCX;
    @FXML private Button btnOpen;
    @FXML private Button btnNew;
    @FXML private Button btnToggleSearch;
    @FXML private Button btnToggleMacro;
    @FXML private Button btnTextAnalysis;
    @FXML private Button btnRegexHelp;
    @FXML private Button btnKIAssistant;
    
    // Font-Size Toolbar
    @FXML private ComboBox<String> cmbFontSize;
    @FXML private Button btnIncreaseFont;
    @FXML private Button btnDecreaseFont;
    @FXML private Button btnBold;
    @FXML private Button btnItalic;
    @FXML private Button btnThemeToggle;
    @FXML private Button btnMacroRegexHelp;
    
    // Makro-UI-Elemente (werden programmatisch erstellt)
    private ComboBox<String> cmbMacroList;
    private Button btnNewMacro;
    private Button btnDeleteMacro;
    private Button btnSaveMacro;
    private Button btnRunMacro;
    private VBox macroDetailsPanel;
    private TableView<MacroStep> tblMacroSteps;
    private TableColumn<MacroStep, Boolean> colEnabled;
    private TableColumn<MacroStep, Integer> colStepNumber;
    private TableColumn<MacroStep, String> colDescription;
    private TableColumn<MacroStep, String> colSearchText;
    private TableColumn<MacroStep, String> colReplaceText;
    private TableColumn<MacroStep, String> colOptions;
    private TableColumn<MacroStep, String> colStatus;
    private TableColumn<MacroStep, String> colActions;
    private Button btnAddStep;
    private Button btnRemoveStep;
    private Button btnMoveStepUp;
    private Button btnMoveStepDown;
    
    // Makro-Schritt-Eingabe
    private TextField txtMacroSearch;
    private TextField txtMacroReplace;
    private TextField txtMacroStepDescription;
    private CheckBox chkMacroRegex;
    private CheckBox chkMacroCaseSensitive;
    private CheckBox chkMacroWholeWord;
    
    private Stage stage;
    private Stage macroStage;
    private Stage textAnalysisStage;
    private OllamaWindow ollamaWindow;
    private Preferences preferences;
    private ObservableList<String> searchHistory = FXCollections.observableArrayList();
    private ObservableList<String> replaceHistory = FXCollections.observableArrayList();
    private ObservableList<String> searchOptions = FXCollections.observableArrayList();
    private ObservableList<String> replaceOptions = FXCollections.observableArrayList();
    private int currentMatchIndex = -1;
    private int totalMatches = 0;
    private String lastSearchText = "";
    private List<Integer> cachedMatchPositions = new ArrayList<>();
    private Pattern cachedPattern = null;
    private boolean searchPanelVisible = false;
    private boolean macroWindowVisible = false;
    private boolean textAnalysisWindowVisible = false;
    private boolean ollamaWindowVisible = false;
    private boolean chapterEditorVisible = false;
    private String originalChapterContent = "";
    private boolean chapterContentChanged = false;
    private File currentFile = null;
    private DocxProcessor.OutputFormat outputFormat = DocxProcessor.OutputFormat.HTML;
    private DocxProcessor docxProcessor;
    
    // Theme-Management
    private int currentThemeIndex = 0;
    private static final String[][] THEMES = {
        // Weißer Hintergrund / Schwarze Schrift
        {"#ffffff", "#000000", "#f8f9fa", "#e9ecef"},
        // Schwarzer Hintergrund / Weiße Schrift  
        {"#1a1a1a", "#ffffff", "#2d2d2d", "#404040"},
        // Kräftigeres Mauve mit schwarzer Schrift
        {"#f3e5f5", "#000000", "#e1bee7", "#ce93d8"},
        // Blau mit weißer Schrift
        {"#1e3a8a", "#ffffff", "#3b82f6", "#60a5fa"},
        // Grün mit weißer Schrift
        {"#064e3b", "#ffffff", "#059669", "#10b981"},
        // Lila mit weißer Schrift
        {"#581c87", "#ffffff", "#7c3aed", "#a855f7"}
    };
    
    // Makro-Management
    private ObservableList<Macro> macros = FXCollections.observableArrayList();
    private Macro currentMacro = null;
    
    // Undo/Redo-Funktionalität
    private static class UndoState {
        final String text;
        final int caretPosition;
        
        UndoState(String text, int caretPosition) {
            this.text = text;
            this.caretPosition = caretPosition;
        }
    }
    
    private List<UndoState> undoStack = new ArrayList<>();
    private List<UndoState> redoStack = new ArrayList<>();
    private static final int MAX_UNDO_STEPS = 50;
    private boolean isUndoRedoOperation = false;
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        preferences = Preferences.userNodeForPackage(EditorWindow.class);
        setupUI();
        setupEventHandlers();
        loadSearchReplaceHistory();
        setupSearchReplacePanel();
        setupMacroPanel();
        setupFontSizeComboBox();
        
        // Checkboxen explizit auf false setzen (nach FXML-Load)
        Platform.runLater(() -> {
            if (chkRegexSearch != null) {
                chkRegexSearch.setSelected(false);
                chkRegexSearch.setIndeterminate(false);
            }
            if (chkCaseSensitive != null) {
                chkCaseSensitive.setSelected(false);
                chkCaseSensitive.setIndeterminate(false);
            }
            if (chkWholeWord != null) {
                chkWholeWord.setSelected(false);
                chkWholeWord.setIndeterminate(false);
            }
            
            // WICHTIG: Theme und Font-Size aus Preferences laden (nach UI-Initialisierung)
            loadToolbarSettings();
            
            // Zusätzlicher Theme-Refresh für bessere Kompatibilität
            Platform.runLater(() -> {
                if (currentThemeIndex > 0) { // Nicht das Standard-weiße Theme
                    applyTheme(currentThemeIndex);
                    logger.info("Zusätzlicher Theme-Refresh durchgeführt für Theme: {}", currentThemeIndex);
                }
            });
        });
    }
    
    private void setupUI() {
        // Toolbar explizit sichtbar machen
        if (btnNew != null) {
            btnNew.setVisible(true);
            btnNew.setManaged(true);
        }
        if (btnOpen != null) {
            btnOpen.setVisible(true);
            btnOpen.setManaged(true);
        }
        if (btnSave != null) {
            btnSave.setVisible(true);
            btnSave.setManaged(true);
        }
        if (btnSaveAs != null) {
            btnSaveAs.setVisible(true);
            btnSaveAs.setManaged(true);
        }
        if (btnExportRTF != null) {
            btnExportRTF.setVisible(true);
            btnExportRTF.setManaged(true);
        }
        if (btnExportDOCX != null) {
            btnExportDOCX.setVisible(true);
            btnExportDOCX.setManaged(true);
        }
        if (btnToggleSearch != null) {
            btnToggleSearch.setVisible(true);
            btnToggleSearch.setManaged(true);
        }
        if (btnToggleMacro != null) {
            btnToggleMacro.setVisible(true);
            btnToggleMacro.setManaged(true);
        }
        if (lblStatus != null) {
            lblStatus.setVisible(true);
            lblStatus.setManaged(true);
        }
        
        // Checkboxen initial auf false setzen
        if (chkRegexSearch != null) {
            chkRegexSearch.setSelected(false);
        }
        if (chkCaseSensitive != null) {
            chkCaseSensitive.setSelected(false);
        }
        if (chkWholeWord != null) {
            chkWholeWord.setSelected(false);
        }
        
        // CodeArea initialisieren
        codeArea = new CodeArea();
        codeArea.setWrapText(true);
        codeArea.getStyleClass().add("code-area");
        codeArea.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 12px; -rtfx-background-color: #ffffff;");
        
        // CSS-Dateien für CodeArea laden
        // CSS mit ResourceManager laden
        String cssPath = ResourceManager.getCssResource("css/styles.css");
        String editorCssPath = ResourceManager.getCssResource("css/editor.css");
        codeArea.getStylesheets().add(cssPath);
        codeArea.getStylesheets().add(editorCssPath);
        
        // CSS auch für die gesamte Scene laden (für Chapter-Editor)
        Platform.runLater(() -> {
            if (stage != null && stage.getScene() != null) {
                stage.getScene().getStylesheets().add(cssPath);
                stage.getScene().getStylesheets().add(editorCssPath);
            }
        });
        
        Node caret = codeArea.lookup(".caret");
if (caret != null) {
    caret.setStyle("-fx-stroke: red; -fx-fill: red;");
}


        // Zeilennummern hinzufügen
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        
        // VirtualizedScrollPane für bessere Performance
        VirtualizedScrollPane<CodeArea> scrollPane = new VirtualizedScrollPane<>(codeArea);
        
        // CodeArea zum Container hinzufügen (im SplitPane)
        textAreaContainer.getChildren().add(scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        
        // VBox-Grow-Eigenschaften setzen
        VBox.setVgrow(chapterEditorPanel, Priority.NEVER);
        VBox.setVgrow(textAreaContainer, Priority.ALWAYS);
        
        // Chapter-Editor initial korrekt einrichten
        chapterEditorPanel.setManaged(false);
        chapterEditorPanel.setMinHeight(0);
        chapterEditorPanel.setPrefHeight(0);
        chapterEditorPanel.setMinWidth(200);
        chapterEditorPanel.setPrefWidth(300);
        
        // SplitPane initial konfigurieren
        mainSplitPane.setOrientation(Orientation.VERTICAL);
        mainSplitPane.setVisible(true);
        mainSplitPane.setManaged(true);
        
        // Divider-Position NACH dem FXML-Load setzen
        Platform.runLater(() -> {
            mainSplitPane.setDividerPositions(0.0); // Divider ganz nach oben
        });
        
        // Such- und Ersetzungs-Panel initial ausblenden
        searchReplacePanel.setVisible(false);
        searchReplacePanel.setManaged(false);
        
        // TextArea nimmt den gesamten verfügbaren Platz ein
        VBox.setVgrow(textAreaContainer, Priority.ALWAYS);
        
        // ComboBoxes editierbar machen
        cmbSearchHistory.setEditable(true);
        cmbReplaceHistory.setEditable(true);
        
        // CodeArea Change-Listener für Undo/Redo
        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            if (!isUndoRedoOperation) {
                saveStateForUndo();
            }
        });
        
        // Status-Label initialisieren
        updateStatus("Bereit");
        updateMatchCount(0, 0);
        
        // Toolbar-Einstellungen laden (Font-Size, Theme, etc.)
        loadToolbarSettings();
    }
    
    private void setupEventHandlers() {
        // Such- und Ersetzungs-Events - nur bei Enter oder Button-Klicks
        cmbSearchHistory.setOnAction(e -> {
            String searchText = cmbSearchHistory.getValue();
            if (searchText != null && !searchText.trim().isEmpty()) {
                // Lade die gespeicherten Optionen für diesen Eintrag
                int index = searchHistory.indexOf(searchText);
                if (index >= 0 && index < searchOptions.size()) {
                    String options = searchOptions.get(index);
                    applySearchOptions(options);
                }
                findNext(); // Suche zum nächsten Treffer, nicht zum ersten
            }
        });
        
        cmbReplaceHistory.setOnAction(e -> {
            String replaceText = cmbReplaceHistory.getValue();
            if (replaceText != null) {
                // Lade die gespeicherten Optionen für diesen Eintrag
                int index = replaceHistory.indexOf(replaceText);
                if (index >= 0 && index < replaceOptions.size()) {
                    String options = replaceOptions.get(index);
                    applySearchOptions(options);
                }
                replaceText();
            }
        });
        
        btnFind.setOnAction(e -> {
            String searchText = cmbSearchHistory.getValue();
            if (searchText != null && !searchText.trim().isEmpty()) {
                findNext(); // Suche zum nächsten Treffer, nicht zum ersten
            }
        });
        btnReplace.setOnAction(e -> {
            String searchText = cmbSearchHistory.getValue();
            String replaceText = cmbReplaceHistory.getValue();
            if (searchText != null && !searchText.trim().isEmpty()) {
                replaceText();
            }
        });
        btnReplaceAll.setOnAction(e -> {
            String searchText = cmbSearchHistory.getValue();
            String replaceText = cmbReplaceHistory.getValue();
            if (searchText != null && !searchText.trim().isEmpty()) {
                replaceAllText();
            }
        });
        
        // Speichern-Buttons
        btnSaveSearch.setOnAction(e -> {
            String searchText = cmbSearchHistory.getValue();
            if (searchText != null && !searchText.trim().isEmpty()) {
                addToSearchHistory(searchText.trim());
                updateStatus("Suchpattern gespeichert: " + searchText.trim());
            }
        });
        
        btnSaveReplace.setOnAction(e -> {
            String replaceText = cmbReplaceHistory.getValue();
            if (replaceText != null && !replaceText.trim().isEmpty()) {
                addToReplaceHistory(replaceText.trim());
                updateStatus("Ersetzungspattern gespeichert: " + replaceText.trim());
            }
        });
        
        // Löschen-Buttons
        btnDeleteSearch.setOnAction(e -> {
            String searchText = cmbSearchHistory.getValue();
            if (searchText != null && !searchText.trim().isEmpty()) {
                removeFromSearchHistory(searchText.trim());
                updateStatus("Suchpattern gelöscht: " + searchText.trim());
            }
        });
        
        btnDeleteReplace.setOnAction(e -> {
            String replaceText = cmbReplaceHistory.getValue();
            if (replaceText != null && !replaceText.trim().isEmpty()) {
                removeFromReplaceHistory(replaceText.trim());
                updateStatus("Ersetzungspattern gelöscht: " + replaceText.trim());
            }
        });
        
        // Font-Size Event-Handler
        btnIncreaseFont.setOnAction(e -> changeFontSize(2));
        btnDecreaseFont.setOnAction(e -> changeFontSize(-2));
        cmbFontSize.setOnAction(e -> changeFontSizeFromComboBox());
        
        // Text-Formatting Event-Handler
        btnBold.setOnAction(e -> formatTextBold());
        btnItalic.setOnAction(e -> formatTextItalic());
        btnThemeToggle.setOnAction(e -> toggleTheme());
        if (btnMacroRegexHelp != null) {
            btnMacroRegexHelp.setOnAction(e -> showRegexHelp());
        }
        
        // Textanalyse-Button
        btnTextAnalysis.setOnAction(e -> toggleTextAnalysisPanel());
        
        // KI-Assistent-Button
        btnKIAssistant.setOnAction(e -> toggleOllamaWindow());
        
        // Chapter-Editor Event-Handler
        btnChapterEditor.setOnAction(e -> toggleChapterEditor());
        btnSaveChapter.setOnAction(e -> saveChapterContent());
        
        // Chapter-Editor Text-Change Listener
        if (chapterEditorArea != null) {
            chapterEditorArea.textProperty().addListener((obs, oldText, newText) -> {
                if (chapterEditorVisible && !originalChapterContent.equals(newText)) {
                    chapterContentChanged = true;
                    // Rote Anzeige setzen
                    if (lblStatus != null) {
                        lblStatus.setText("Kapitelbeschreibung nicht gesichert");
                        lblStatus.setStyle("-fx-text-fill: #ff0000; -fx-font-size: 11px; -fx-font-weight: bold;");
                    }
                } else if (chapterEditorVisible && originalChapterContent.equals(newText)) {
                    chapterContentChanged = false;
                    // Normale Anzeige zurücksetzen
                    if (lblStatus != null) {
                        lblStatus.setText("Bereit");
                        lblStatus.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 11px;");
                    }
                }
            });
        }
        btnRegexHelp.setOnAction(e -> showRegexHelp());
        btnFindNext.setOnAction(e -> findNext());
        btnFindPrevious.setOnAction(e -> findPrevious());
        
        // Toolbar-Events
        btnSave.setOnAction(e -> saveFile());
        btnSaveAs.setOnAction(e -> saveFileAs());
        btnExportRTF.setOnAction(e -> exportAsRTF());
        btnExportDOCX.setOnAction(e -> exportAsDOCX());
        btnOpen.setOnAction(e -> openFile());
        btnNew.setOnAction(e -> newFile());
        btnToggleSearch.setOnAction(e -> toggleSearchPanel());
        btnToggleMacro.setOnAction(e -> toggleMacroPanel());
        
        // Keyboard-Shortcuts
        setupKeyboardShortcuts();
        
        // Text-Selektion Event-Listener für KI-Assistent
        codeArea.selectionProperty().addListener((obs, oldSelection, newSelection) -> {
            if (ollamaWindow != null && ollamaWindow.isShowing()) {
                String selectedText = codeArea.getSelectedText();
                if (selectedText != null && !selectedText.trim().isEmpty()) {
                    // Prüfe ob "Text umschreiben" aktiv ist
                    if ("Text umschreiben".equals(ollamaWindow.getCurrentFunction())) {
                        ollamaWindow.updateSelectedText(selectedText);
                    }
                }
            }
        });
    }
    
    private void setupKeyboardShortcuts() {
        // Keyboard-Shortcuts für den Editor
        codeArea.setOnKeyPressed(event -> {
            // Debug-Ausgabe für Keyboard-Events
            logger.debug("Key pressed: " + event.getCode() + ", Shift: " + event.isShiftDown() + ", Ctrl: " + event.isControlDown());
            
            if (event.isControlDown()) {
                switch (event.getCode()) {
                    case F:
                        toggleSearchPanel();
                        event.consume();
                        break;
                    case S:
                        saveFile();
                        event.consume();
                        break;
                    case O:
                        openFile();
                        event.consume();
                        break;
                    case N:
                        newFile();
                        event.consume();
                        break;
                    case Z:
                        undo();
                        event.consume();
                        break;
                    case Y:
                        redo();
                        event.consume();
                        break;
                }
            } else if (event.getCode() == KeyCode.F3) {
                // F3 und Shift+F3 für Suchen-Navigation
                if (event.isShiftDown()) {
                    logger.debug("Shift+F3 pressed - calling findPrevious()");
                    findPrevious();
                } else {
                    logger.debug("F3 pressed - calling findNext()");
                    findNext();
                }
                event.consume();
            }
        });
    }
    
    private void setupSearchReplacePanel() {
        // Elegantes Such-Panel mit Animation
        searchReplacePanel.setStyle("""
            -fx-background-color: linear-gradient(to bottom, #f8f9fa, #e9ecef);
            -fx-border-color: #dee2e6;
            -fx-border-width: 1px;
            -fx-border-radius: 6px;
            -fx-background-radius: 6px;
            -fx-padding: 10px;
            -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 2);
        """);
    }
    
    private void toggleSearchPanel() {
        searchPanelVisible = !searchPanelVisible;
        searchReplacePanel.setVisible(searchPanelVisible);
        searchReplacePanel.setManaged(searchPanelVisible);
        
        if (searchPanelVisible) {
            cmbSearchHistory.requestFocus();
            cmbSearchHistory.getEditor().selectAll();
        }
    }
    
    private void findText() {
        String searchText = cmbSearchHistory.getValue();
        if (searchText == null || searchText.trim().isEmpty()) return;
        
        try {
            Pattern pattern = createSearchPattern(searchText.trim());
            String content = codeArea.getText();
            
            Matcher matcher = pattern.matcher(content);
            
            totalMatches = 0;
            while (matcher.find()) {
                totalMatches++;
            }
            
            if (totalMatches > 0) {
                findNext();
                updateMatchCount(currentMatchIndex + 1, totalMatches);
                updateStatus(totalMatches + " Treffer gefunden");
            } else {
                updateMatchCount(0, 0);
                updateStatus("Keine Treffer gefunden");
                currentMatchIndex = -1;
                // Entferne alle Markierungen
                codeArea.deselect();
            }
            
        } catch (Exception e) {
            updateStatus("Ungültiges Regex-Pattern: " + e.getMessage());
            updateMatchCount(0, 0);
        }
    }
    
    private void findNext() {
        String searchText = cmbSearchHistory.getValue();
        if (searchText == null || searchText.trim().isEmpty()) return;
        
        try {
            Pattern pattern = createSearchPattern(searchText.trim());
            String content = codeArea.getText();
            
            // Wenn sich der Suchtext geändert hat, cache neu aufbauen
            if (!searchText.equals(lastSearchText)) {
                totalMatches = 0;
                currentMatchIndex = -1;
                lastSearchText = searchText;
                cachedPattern = pattern;
                cachedMatchPositions.clear();
                
                // Sammle alle Treffer-Positionen
                Matcher collectMatcher = pattern.matcher(content);
                while (collectMatcher.find()) {
                    cachedMatchPositions.add(collectMatcher.start());
                }
                totalMatches = cachedMatchPositions.size();
                
                // Markiere alle Treffer nur einmal
                if (totalMatches > 0) {
                    StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
                    int lastEnd = 0;
                    
                    for (int start : cachedMatchPositions) {
                        Matcher matchMatcher = pattern.matcher(content);
                        if (matchMatcher.find(start)) {
                            int end = matchMatcher.end();
                            
                            // Füge normalen Text vor dem Treffer hinzu
                            if (start > lastEnd) {
                                spansBuilder.add(Collections.emptyList(), start - lastEnd);
                            }
                            
                            // Füge markierten Text hinzu
                            spansBuilder.add(Collections.singleton("search-match-first"), end - start);
                            lastEnd = end;
                        }
                    }
                    
                    // Füge restlichen Text hinzu
                    if (lastEnd < content.length()) {
                        spansBuilder.add(Collections.emptyList(), content.length() - lastEnd);
                    }
                    
                    // Wende die Markierungen an
                    codeArea.setStyleSpans(0, spansBuilder.create());
                } else {
                    // Entferne alle Markierungen
                    codeArea.setStyleSpans(0, content.length(), StyleSpans.singleton(new ArrayList<>(), 0));
                }
            }
            
            if (totalMatches == 0) {
                updateMatchCount(0, 0);
                updateStatus("Keine Treffer gefunden");
                codeArea.deselect();
                return;
            }
            
            // Finde den nächsten Treffer aus dem Cache
            int currentPos = codeArea.getCaretPosition();
            int nextIndex = -1;
            
            // Suche den nächsten Treffer nach der aktuellen Position
            for (int i = 0; i < cachedMatchPositions.size(); i++) {
                if (cachedMatchPositions.get(i) > currentPos) {
                    nextIndex = i;
                    break;
                }
            }
            
            // Wenn kein Treffer nach der aktuellen Position, nimm den ersten (Wrap-around)
            if (nextIndex == -1) {
                nextIndex = 0;
            }
            
            // Markiere den gefundenen Treffer
            int matchStart = cachedMatchPositions.get(nextIndex);
            Matcher highlightMatcher = cachedPattern.matcher(content);
            if (highlightMatcher.find(matchStart)) {
                highlightText(highlightMatcher.start(), highlightMatcher.end());
                currentMatchIndex = nextIndex;
                updateMatchCount(currentMatchIndex + 1, totalMatches);
                updateStatus("Treffer " + (currentMatchIndex + 1) + " von " + totalMatches);
            }
            
        } catch (Exception e) {
            updateStatusError("Fehler beim Suchen: " + e.getMessage());
        }
    }
    
    private void findPrevious() {
        String searchText = cmbSearchHistory.getValue();
        if (searchText == null || searchText.trim().isEmpty()) return;
        
        try {
            // Verwende den Cache von findNext() - wenn noch nicht vorhanden, rufe findNext() auf
            if (!searchText.equals(lastSearchText) || cachedMatchPositions.isEmpty()) {
                findNext();
                return;
            }
            
            if (totalMatches == 0) {
                updateMatchCount(0, 0);
                updateStatus("Keine Treffer gefunden");
                codeArea.deselect();
                return;
            }
            
            // Finde den vorherigen Treffer aus dem Cache
            int previousIndex;
            if (currentMatchIndex <= 0) {
                // Wenn wir am Anfang sind oder noch keinen Index haben, gehe zum letzten Treffer
                previousIndex = cachedMatchPositions.size() - 1;
            } else {
                // Gehe zum vorherigen Treffer
                previousIndex = currentMatchIndex - 1;
            }
            
            // Markiere den gefundenen Treffer
            if (previousIndex >= 0 && previousIndex < cachedMatchPositions.size()) {
                int matchStart = cachedMatchPositions.get(previousIndex);
                String content = codeArea.getText();
                Matcher highlightMatcher = cachedPattern.matcher(content);
                if (highlightMatcher.find(matchStart)) {
                    highlightText(highlightMatcher.start(), highlightMatcher.end());
                    currentMatchIndex = previousIndex;
                    updateMatchCount(currentMatchIndex + 1, totalMatches);
                    updateStatus("Treffer " + (currentMatchIndex + 1) + " von " + totalMatches);
                }
            }
            
        } catch (Exception e) {
            updateStatusError("Fehler beim Suchen: " + e.getMessage());
        }
    }
    
    private void replaceText() {
        String searchText = cmbSearchHistory.getValue();
        String replaceText = cmbReplaceHistory.getValue();
        
        if (searchText == null || searchText.trim().isEmpty()) return;
        
        // Speichere Zustand für Undo
        saveStateForUndo();
        
        try {
            Pattern pattern = createSearchPattern(searchText.trim());
            String content = codeArea.getText();
            Matcher matcher = pattern.matcher(content);
            
            int start = codeArea.getCaretPosition();
            if (matcher.find(start)) {
                // Für Regex-Ersetzung müssen wir die Backreferences manuell ersetzen
                String replacement;
                if (chkRegexSearch.isSelected() && replaceText != null && replaceText.contains("\\")) {
                    // Java uses $1, $2, etc. for backreferences, not \1, \2
                    String replaceTextJava = replaceText.replace("\\1", "$1").replace("\\2", "$2").replace("\\3", "$3").replace("\\4", "$4").replace("\\5", "$5");
                    
                    // Erstelle einen neuen Matcher für die Ersetzung des gefundenen Teils
                    String foundText = content.substring(matcher.start(), matcher.end());
                    Pattern replacePattern = Pattern.compile(pattern.pattern(), pattern.flags());
                    Matcher replaceMatcher = replacePattern.matcher(foundText);
                    replacement = replaceMatcher.replaceFirst(replaceTextJava);
                    
                    // Debug: Zeige die Konvertierung
                    System.out.println("DEBUG REPLACE: Original: '" + replaceText + "' -> Java: '" + replaceTextJava + "' -> Found: '" + foundText + "' -> Result: '" + replacement + "'");
                } else {
                    replacement = replaceText != null ? replaceText : "";
                    System.out.println("DEBUG REPLACE: Direct replacement: '" + replacement + "'");
                }
                
                codeArea.replaceText(matcher.start(), matcher.end(), replacement);
                updateStatus("Ersetzt");
                
                // Nach dem Ersetzen müssen wir die Suche neu starten
                // Setze die Position auf den Anfang des ersetzten Texts
                codeArea.displaceCaret(matcher.start());
                codeArea.requestFollowCaret();
                
                // Suche nach dem nächsten Treffer
                findNext();
            }
            
        } catch (Exception e) {
            updateStatusError("Fehler beim Ersetzen: " + e.getMessage());
        }
    }
    
    private void replaceAllText() {
        String searchText = cmbSearchHistory.getValue();
        String replaceText = cmbReplaceHistory.getValue();
        
        if (searchText == null || searchText.trim().isEmpty()) return;
        
        // Speichere Zustand für Undo
        saveStateForUndo();
        
        try {
            String content = codeArea.getText();
            String replacement;
            
            // Debug: Zeige Status im Status-Feld
            boolean regexEnabled = chkRegexSearch.isSelected();
            lblStatus.setText("Regex: " + regexEnabled + " | Pattern: '" + searchText + "' | Replace: '" + replaceText + "'");
            
            // Immer Regex-Replace verwenden - das ist das Problem!
            // Wir verwenden immer matcher.replaceAll(), auch für normale Suche
            int flags = Pattern.MULTILINE;
            if (!chkCaseSensitive.isSelected()) {
                flags |= Pattern.CASE_INSENSITIVE;
            }
            
            String patternText = searchText.trim();
            if (chkWholeWord.isSelected()) {
                patternText = "\\b" + patternText + "\\b";
            } else if (!chkRegexSearch.isSelected()) {
                // Wenn Regex nicht aktiv ist, escapen wir den Text
                patternText = Pattern.quote(patternText);
            }
            
            Pattern pattern = Pattern.compile(patternText, flags);
            Matcher matcher = pattern.matcher(content);
            
            // Java verwendet $1, $2, etc. für Backreferences, nicht \1, \2
            String replaceTextJava = replaceText != null ? replaceText : "";
            if (replaceTextJava.contains("\\")) {
                replaceTextJava = replaceTextJava.replace("\\1", "$1").replace("\\2", "$2").replace("\\3", "$3").replace("\\4", "$4").replace("\\5", "$5");
            }
            replacement = matcher.replaceAll(replaceTextJava);
            
            // Debug: Zeige die Konvertierung
            System.out.println("DEBUG: Original: '" + replaceText + "' -> Java: '" + replaceTextJava + "' -> Result: '" + replacement.substring(0, Math.min(50, replacement.length())) + "'");
            
            codeArea.replaceText(replacement);
            lblStatus.setText("Alle Treffer ersetzt");
            updateMatchCount(0, 0);
            
        } catch (Exception e) {
            updateStatusError("Fehler beim Ersetzen: " + e.getMessage());
        }
    }
    
    private Pattern createSearchPattern(String searchText) {
        int flags = Pattern.MULTILINE;
        if (!chkCaseSensitive.isSelected()) {
            flags |= Pattern.CASE_INSENSITIVE;
        }
        
        if (chkWholeWord.isSelected()) {
            searchText = "\\b" + Pattern.quote(searchText) + "\\b";
        } else if (!chkRegexSearch.isSelected()) {
            searchText = Pattern.quote(searchText);
        }
        
        return Pattern.compile(searchText, flags);
    }
    
    private Pattern createReplacePattern(String searchText) {
        // Für Replace verwende immer das ursprüngliche Pattern ohne zusätzliche Escaping
        int flags = Pattern.MULTILINE;
        if (!chkCaseSensitive.isSelected()) {
            flags |= Pattern.CASE_INSENSITIVE;
        }
        
        if (chkWholeWord.isSelected()) {
            searchText = "\\b" + searchText + "\\b";
        } else if (!chkRegexSearch.isSelected()) {
            searchText = Pattern.quote(searchText);
        }
        
        return Pattern.compile(searchText, flags);
    }
    
    private int countMatchesBefore(String content, Pattern pattern, int position) {
        Matcher matcher = pattern.matcher(content);
        int count = 0;
        while (matcher.find() && matcher.start() < position) {
            count++;
        }
        return count;
    }
    
    private int getMatchIndex(String content, Pattern pattern, int position) {
        Matcher matcher = pattern.matcher(content);
        int index = 0;
        while (matcher.find()) {
            if (matcher.start() == position) {
                return index;
            }
            index++;
        }
        return 0; // Fallback
    }
    
    private void highlightText(int start, int end) {
        if (start >= 0 && end >= 0) {
            // Setze den Cursor an die Position und markiere den Text
            codeArea.displaceCaret(start);
            codeArea.selectRange(start, end);
            codeArea.requestFocus();
            
            // RichTextFX: Zeige Cursor und scrolle automatisch
            codeArea.requestFollowCaret();
            
            // Zusätzliches Scrollen für bessere Sichtbarkeit am unteren Rand
            Platform.runLater(() -> {
                // Zeige die aktuelle Zeile in der Mitte, aber mit etwas Abstand nach unten
                int currentParagraph = codeArea.getCurrentParagraph();
                codeArea.showParagraphAtCenter(currentParagraph + 2);
            });
        }
    }
    
    private void addToSearchHistory(String text) {
        if (!searchHistory.contains(text)) {
            searchHistory.add(0, text);
            // Speichere auch die aktuellen Optionen
            String options = String.format("regex:%s,case:%s,whole:%s", 
                chkRegexSearch.isSelected(), 
                chkCaseSensitive.isSelected(), 
                chkWholeWord.isSelected());
            searchOptions.add(0, options);
            
            while (searchHistory.size() > 20) {
                searchHistory.remove(searchHistory.size() - 1);
                searchOptions.remove(searchOptions.size() - 1);
            }
            saveSearchHistory();
        }
        // Stelle sicher, dass der aktuelle Wert im ComboBox bleibt
        cmbSearchHistory.setItems(searchHistory);
        cmbSearchHistory.setValue(text);
    }
    
    private void addToReplaceHistory(String text) {
        if (!replaceHistory.contains(text)) {
            replaceHistory.add(0, text);
            // Speichere auch die aktuellen Optionen
            String options = String.format("regex:%s,case:%s,whole:%s", 
                chkRegexSearch.isSelected(), 
                chkCaseSensitive.isSelected(), 
                chkWholeWord.isSelected());
            replaceOptions.add(0, options);
            
            while (replaceHistory.size() > 20) {
                replaceHistory.remove(replaceHistory.size() - 1);
                replaceOptions.remove(replaceOptions.size() - 1);
            }
            saveReplaceHistory();
        }
        // Stelle sicher, dass der aktuelle Wert im ComboBox bleibt
        cmbReplaceHistory.setItems(replaceHistory);
        cmbReplaceHistory.setValue(text);
    }
    
    private void removeFromSearchHistory(String text) {
        searchHistory.remove(text);
        saveSearchHistory();
        cmbSearchHistory.setValue("");
    }
    
    private void removeFromReplaceHistory(String text) {
        replaceHistory.remove(text);
        saveReplaceHistory();
        cmbReplaceHistory.setValue("");
    }
    
    private void loadSearchReplaceHistory() {
        try {
            // Lösche alte Daten und starte sauber
            searchHistory.clear();
            replaceHistory.clear();
            searchOptions.clear();
            replaceOptions.clear();
            
            String searchHistoryStr = preferences.get("searchHistory", "");
            String replaceHistoryStr = preferences.get("replaceHistory", "");
            String searchOptionsStr = preferences.get("searchOptions", "");
            String replaceOptionsStr = preferences.get("replaceOptions", "");
            
            if (!searchHistoryStr.isEmpty()) {
                String[] items = searchHistoryStr.split("\\|\\|\\|");
                for (String item : items) {
                    if (!item.trim().isEmpty()) {
                        // Base64-Dekodierung
                        try {
                            String decodedItem = new String(java.util.Base64.getDecoder().decode(item.trim()));
                            searchHistory.add(decodedItem);
                        } catch (Exception e) {
                            // Ignoriere ungültige Einträge
                            logger.debug("Ungültiger Base64-Eintrag in Search-Historie: {}", item);
                        }
                    }
                }
            }
            
            if (!replaceHistoryStr.isEmpty()) {
                String[] items = replaceHistoryStr.split("\\|\\|\\|");
                for (String item : items) {
                    if (!item.trim().isEmpty()) {
                        // Base64-Dekodierung
                        try {
                            String decodedItem = new String(java.util.Base64.getDecoder().decode(item.trim()));
                            replaceHistory.add(decodedItem);
                        } catch (Exception e) {
                            // Ignoriere ungültige Einträge
                            logger.debug("Ungültiger Base64-Eintrag in Replace-Historie: {}", item);
                        }
                    }
                }
            }
            
            // Lade Optionen
            if (!searchOptionsStr.isEmpty()) {
                String[] items = searchOptionsStr.split("\\|\\|\\|");
                for (String item : items) {
                    if (!item.trim().isEmpty()) {
                        try {
                            String decodedItem = new String(java.util.Base64.getDecoder().decode(item.trim()));
                            searchOptions.add(decodedItem);
                        } catch (Exception e) {
                            logger.debug("Ungültiger Base64-Eintrag in Search-Optionen: {}", item);
                        }
                    }
                }
            }
            
            if (!replaceOptionsStr.isEmpty()) {
                String[] items = replaceOptionsStr.split("\\|\\|\\|");
                for (String item : items) {
                    if (!item.trim().isEmpty()) {
                        try {
                            String decodedItem = new String(java.util.Base64.getDecoder().decode(item.trim()));
                            replaceOptions.add(decodedItem);
                        } catch (Exception e) {
                            logger.debug("Ungültiger Base64-Eintrag in Replace-Optionen: {}", item);
                        }
                    }
                }
            }
            
            cmbSearchHistory.setItems(searchHistory);
            cmbReplaceHistory.setItems(replaceHistory);
            
        } catch (Exception e) {
            logger.warn("Fehler beim Laden der Such-Historie: {}", e.getMessage());
        }
    }
    
    private void saveSearchHistory() {
        try {
            List<String> encodedItems = new ArrayList<>();
            for (String item : searchHistory) {
                // Base64-Kodierung für sicheres Speichern
                String encoded = java.util.Base64.getEncoder().encodeToString(item.getBytes());
                encodedItems.add(encoded);
            }
            String searchHistoryStr = String.join("|||", encodedItems);
            preferences.put("searchHistory", searchHistoryStr);
            
            // Speichere auch die Optionen
            List<String> encodedOptions = new ArrayList<>();
            for (String option : searchOptions) {
                String encoded = java.util.Base64.getEncoder().encodeToString(option.getBytes());
                encodedOptions.add(encoded);
            }
            String searchOptionsStr = String.join("|||", encodedOptions);
            preferences.put("searchOptions", searchOptionsStr);
        } catch (Exception e) {
            logger.warn("Fehler beim Speichern der Such-Historie: {}", e.getMessage());
        }
    }
    
    private void saveReplaceHistory() {
        try {
            List<String> encodedItems = new ArrayList<>();
            for (String item : replaceHistory) {
                // Base64-Kodierung für sicheres Speichern
                String encoded = java.util.Base64.getEncoder().encodeToString(item.getBytes());
                encodedItems.add(encoded);
            }
            String replaceHistoryStr = String.join("|||", encodedItems);
            preferences.put("replaceHistory", replaceHistoryStr);
            
            // Speichere auch die Optionen
            List<String> encodedOptions = new ArrayList<>();
            for (String option : replaceOptions) {
                String encoded = java.util.Base64.getEncoder().encodeToString(option.getBytes());
                encodedOptions.add(encoded);
            }
            String replaceOptionsStr = String.join("|||", encodedOptions);
            preferences.put("replaceOptions", replaceOptionsStr);
        } catch (Exception e) {
            logger.warn("Fehler beim Speichern der Ersetzungs-Historie: {}", e.getMessage());
        }
    }
    
    private void updateStatus(String message) {
        lblStatus.setText(message);
        // Normale Farbe setzen, außer wenn es "nicht gesichert" ist
        if (!message.contains("nicht gesichert")) {
            lblStatus.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 11px;"); // Normale Farbe
        }
        logger.info("Editor Status: {}", message);
    }
    
    private void updateStatusError(String message) {
        lblStatus.setText("❌ " + message);
        lblStatus.setStyle("-fx-text-fill: #dc3545; -fx-font-size: 11px; -fx-font-weight: bold;"); // Rote Farbe
        logger.error("Editor Fehler: {}", message);
    }
    
    private void updateMatchCount(int current, int total) {
        if (total > 0) {
            lblMatchCount.setText(current + " von " + total);
        } else {
            lblMatchCount.setText("");
        }
    }
    

    
    private void applySearchOptions(String options) {
        try {
            // Parse die Optionen: "regex:true,case:false,whole:true"
            String[] parts = options.split(",");
            for (String part : parts) {
                String[] keyValue = part.split(":");
                if (keyValue.length == 2) {
                    String key = keyValue[0].trim();
                    String value = keyValue[1].trim();
                    boolean boolValue = Boolean.parseBoolean(value);
                    
                    switch (key) {
                        case "regex":
                            if (chkRegexSearch != null) {
                                chkRegexSearch.setSelected(boolValue);
                            }
                            break;
                        case "case":
                            if (chkCaseSensitive != null) {
                                chkCaseSensitive.setSelected(boolValue);
                            }
                            break;
                        case "whole":
                            if (chkWholeWord != null) {
                                chkWholeWord.setSelected(boolValue);
                            }
                            break;
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Fehler beim Parsen der Search-Optionen: {}", options);
        }
    }
    
    // Undo/Redo-Funktionalität
    private void saveStateForUndo() {
        if (!isUndoRedoOperation) {
            String currentText = codeArea.getText();
            int currentCaretPosition = 0; // CodeArea handhabt Caret-Position anders
            undoStack.add(new UndoState(currentText, currentCaretPosition));
            
            // Begrenze die Anzahl der Undo-Schritte
            if (undoStack.size() > MAX_UNDO_STEPS) {
                undoStack.remove(0);
            }
            
            // Lösche Redo-Stack bei neuer Aktion
            redoStack.clear();
        }
    }
    
    private void undo() {
        if (!undoStack.isEmpty()) {
            isUndoRedoOperation = true;
            
            // Aktuellen Zustand für Redo speichern
            String currentText = codeArea.getText();
            int currentCaretPosition = 0; // CodeArea handhabt Caret-Position anders
            redoStack.add(new UndoState(currentText, currentCaretPosition));
            
            // Letzten Zustand wiederherstellen
            UndoState previousState = undoStack.remove(undoStack.size() - 1);
            codeArea.replaceText(previousState.text);
            // Caret-Position wird von CodeArea automatisch verwaltet
            
            isUndoRedoOperation = false;
            updateStatus("Undo ausgeführt");
        } else {
            updateStatus("Keine Undo-Aktionen verfügbar");
        }
    }
    
    private void redo() {
        if (!redoStack.isEmpty()) {
            isUndoRedoOperation = true;
            
            // Aktuellen Zustand für Undo speichern
            String currentText = codeArea.getText();
            int currentCaretPosition = 0; // CodeArea handhabt Caret-Position anders
            undoStack.add(new UndoState(currentText, currentCaretPosition));
            
            // Letzten Redo-Zustand wiederherstellen
            UndoState nextState = redoStack.remove(redoStack.size() - 1);
            codeArea.replaceText(nextState.text);
            // Caret-Position wird von CodeArea automatisch verwaltet
            
            isUndoRedoOperation = false;
            updateStatus("Redo ausgeführt");
        } else {
            updateStatus("Keine Redo-Aktionen verfügbar");
        }
    }
    
    private void clearUndoRedoStacks() {
        undoStack.clear();
        redoStack.clear();
        isUndoRedoOperation = false;
    }
    
    // Methode zum Löschen aller Preferences
    public void clearAllPreferences() {
        try {
            preferences.remove("searchHistory");
            preferences.remove("replaceHistory");
            preferences.remove("savedMacros");
            preferences.flush();
            
            searchHistory.clear();
            replaceHistory.clear();
            macros.clear();
            cmbSearchHistory.setItems(searchHistory);
            cmbReplaceHistory.setItems(replaceHistory);
            cmbMacroList.getItems().clear();
            macroDetailsPanel.setVisible(false);
            currentMacro = null;
            
            updateStatus("Alle Preferences und Makros gelöscht");
            System.out.println("Alle User Preferences und Makros gelöscht");
        } catch (Exception e) {
            updateStatusError("Fehler beim Löschen der Preferences: " + e.getMessage());
        }
    }
    
    // Methode zum Löschen nur der Search/Replace-Historie
    public void clearSearchReplaceHistory() {
        try {
            preferences.remove("searchHistory");
            preferences.remove("replaceHistory");
            preferences.flush();
            
            searchHistory.clear();
            replaceHistory.clear();
            cmbSearchHistory.setItems(searchHistory);
            cmbReplaceHistory.setItems(replaceHistory);
            
            updateStatus("Search/Replace-Historie gelöscht");
            System.out.println("Search/Replace-Historie gelöscht");
        } catch (Exception e) {
            updateStatusError("Fehler beim Löschen der Search/Replace-Historie: " + e.getMessage());
        }
    }
    
    // Datei-Operationen
    private void saveFile() {
        if (currentFile != null) {
            saveToFile(currentFile);
        } else {
            saveFileAs();
        }
    }
    
    private void saveFileAs() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Datei speichern");
        
        // Lade das letzte Verzeichnis aus den Einstellungen
        String lastDirectory = preferences.get("lastSaveDirectory", null);
        if (lastDirectory != null) {
            File dir = new File(lastDirectory);
            if (dir.exists() && dir.isDirectory()) {
                fileChooser.setInitialDirectory(dir);
            }
        }
        
        // Füge Format-spezifische Filter hinzu
        switch (outputFormat) {
            case MARKDOWN:
                fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Markdown-Dateien", "*.md")
                );
                break;
            case PLAIN_TEXT:
                fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Text-Dateien", "*.txt")
                );
                break;
            case HTML:
            default:
                fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("HTML-Dateien", "*.html")
                );
                break;
        }
        
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Alle Dateien", "*.*")
        );
        
        // Setze Standard-Dateinamen
        String initialFileName;
        if (currentFile != null) {
            // Verwende den ursprünglichen Dateinamen, aber mit korrekter Erweiterung
            String baseName = currentFile.getName();
            int lastDot = baseName.lastIndexOf('.');
            if (lastDot > 0) {
                baseName = baseName.substring(0, lastDot);
            }
            initialFileName = baseName + getDefaultExtension();
        } else {
            // Fallback für neue Dateien
            initialFileName = "manuskript" + getDefaultExtension();
        }
        fileChooser.setInitialFileName(initialFileName);
        
        File file = fileChooser.showSaveDialog(stage);
        if (file != null) {
            // Füge Standard-Erweiterung hinzu, falls keine angegeben
            if (!hasValidExtension(file.getName())) {
                file = new File(file.getAbsolutePath() + getDefaultExtension());
            }
            
            // Speichere das Verzeichnis für das nächste Mal
            String directory = file.getParent();
            if (directory != null) {
                preferences.put("lastSaveDirectory", directory);
            }
            
            currentFile = file;
            saveToFile(file);
        }
    }
    
    private String getDefaultExtension() {
        switch (outputFormat) {
            case MARKDOWN:
                return ".md";
            case PLAIN_TEXT:
                return ".txt";
            case HTML:
            default:
                return ".html";
        }
    }
    
    private String getExportExtension() {
        // Für Export: Markdown -> RTF, andere bleiben gleich
        if (outputFormat == DocxProcessor.OutputFormat.MARKDOWN) {
            return ".rtf";
        }
        return getDefaultExtension();
    }
    
    private boolean hasValidExtension(String fileName) {
        String lowerFileName = fileName.toLowerCase();
        switch (outputFormat) {
            case MARKDOWN:
                return lowerFileName.endsWith(".md") || lowerFileName.endsWith(".markdown");
            case PLAIN_TEXT:
                return lowerFileName.endsWith(".txt");
            case HTML:
            default:
                return lowerFileName.endsWith(".html") || lowerFileName.endsWith(".htm");
        }
    }
    
    private void saveToFile(File file) {
        try {
            Files.write(file.toPath(), codeArea.getText().getBytes());
            updateStatus("Datei gespeichert: " + file.getName());
        } catch (IOException e) {
            updateStatusError("Fehler beim Speichern: " + e.getMessage());
        }
    }
    
    private void exportAsRTF() {
        if (outputFormat != DocxProcessor.OutputFormat.MARKDOWN) {
            updateStatus("RTF-Export nur für Markdown-Dokumente verfügbar");
            return;
        }
        
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Als RTF exportieren");
        
        // Lade das letzte Verzeichnis aus den Einstellungen
        String lastDirectory = preferences.get("lastSaveDirectory", null);
        if (lastDirectory != null) {
            File dir = new File(lastDirectory);
            if (dir.exists() && dir.isDirectory()) {
                fileChooser.setInitialDirectory(dir);
            }
        }
        
        // Verwende die gleiche Logik wie saveFileAs für konsistente Dateinamen
        String initialFileName;
        if (currentFile != null) {
            // Verwende den ursprünglichen Dateinamen, aber mit RTF-Erweiterung
            String baseName = currentFile.getName();
            int lastDot = baseName.lastIndexOf('.');
            if (lastDot > 0) {
                baseName = baseName.substring(0, lastDot);
            }
            initialFileName = baseName + ".rtf";
        } else {
            // Fallback für neue Dateien
            initialFileName = "manuskript.rtf";
        }
        fileChooser.setInitialFileName(initialFileName);
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("RTF-Dateien", "*.rtf")
        );
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Alle Dateien", "*.*")
        );
        
        File file = fileChooser.showSaveDialog(stage);
        if (file != null) {
            try {
                // Speichere das Verzeichnis für das nächste Mal
                String directory = file.getParent();
                if (directory != null) {
                    preferences.put("lastSaveDirectory", directory);
                }
                
                String markdownContent = codeArea.getText();
                String rtfContent = convertMarkdownToRTF(markdownContent);
                
                // Verwende Windows-1252 Encoding für bessere RTF-Kompatibilität
                Files.write(file.toPath(), rtfContent.getBytes("Windows-1252"));
                updateStatus("Als RTF exportiert: " + file.getName());
                
            } catch (Exception e) {
                updateStatusError("Fehler beim RTF-Export: " + e.getMessage());
            }
        }
    }
    
    private String convertMarkdownToRTF(String markdown) {
        StringBuilder rtf = new StringBuilder();
        
        // RTF-Header mit ANSI (Windows-1252) für bessere Kompatibilität
        rtf.append("{\\rtf1\\ansi\\ansicpg1252\\deff0 {\\fonttbl {\\f0 Times New Roman;}}\n");
        rtf.append("\\f0\\fs24\n");
        
        String[] lines = markdown.split("\n");
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmedLine = line.trim();
            
            if (trimmedLine.isEmpty()) {
                rtf.append("\\par\n");
                continue;
            }
            
            // Horizontale Linie (Markdown-Trenner) - SONDERBEHANDLUNG
            if (trimmedLine.matches("^(\\*{3,}|-{3,}|_{3,})$")) {
                rtf.append("\\pard\\brdrb\\brdrs\\brdrw10\\brsp80 \\line\\par\\pard\\ql\n");
                rtf.append("\\par\n");
                rtf.append("\\par\n");
                continue;
            }
            
            // Überschriften
            if (trimmedLine.startsWith("# ")) {
                rtf.append("\\b\\fs32 ").append(trimmedLine.substring(2)).append("\\b0\\fs24\\par\n");
            } else if (trimmedLine.startsWith("## ")) {
                rtf.append("\\b\\fs28 ").append(trimmedLine.substring(3)).append("\\b0\\fs24\\par\n");
            } else if (trimmedLine.startsWith("### ")) {
                rtf.append("\\b\\fs26 ").append(trimmedLine.substring(4)).append("\\b0\\fs24\\par\n");
            } else {
                // Normaler Text mit Markdown-Formatierung
                String formattedLine = convertMarkdownInlineToRTF(trimmedLine);
                rtf.append(formattedLine).append("\\par\n");
            }
        }
        
        // RTF-Footer
        rtf.append("}");
        
        return rtf.toString();
    }
    
    private String convertMarkdownInlineToRTF(String text) {
        // Fett: **text** (muss VOR Kursiv verarbeitet werden)
        text = text.replaceAll("\\*\\*(.*?)\\*\\*", "\\\\b $1\\\\b0");
        
        // Unterstrichen: __text__ (muss VOR Kursiv verarbeitet werden)
        text = text.replaceAll("__(.*?)__", "\\\\ul $1\\\\ul0");
        
        // Unterstrichen: ~~text~~
        text = text.replaceAll("~~(.*?)~~", "\\\\ul $1\\\\ul0");
        
        // Kursiv: *text* oder _text_ (muss NACH Fett verarbeitet werden)
        text = text.replaceAll("\\*([^*]+)\\*", "\\\\i $1\\\\i0");
        text = text.replaceAll("_([^_]+)_", "\\\\i $1\\\\i0");
        
        // Escape RTF-spezielle Zeichen (nur { und })
        text = text.replace("{", "\\{");
        text = text.replace("}", "\\}");
        
        return text;
    }
    
    private void exportAsDOCX() {
        if (outputFormat != DocxProcessor.OutputFormat.MARKDOWN) {
            updateStatus("DOCX-Export nur für Markdown-Dokumente verfügbar");
            return;
        }
        
        if (docxProcessor == null) {
            updateStatus("DOCX-Processor nicht verfügbar");
            return;
        }
        
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Als DOCX exportieren");
        
        // Lade das letzte Verzeichnis aus den Einstellungen
        String lastDirectory = preferences.get("lastSaveDirectory", null);
        if (lastDirectory != null) {
            File dir = new File(lastDirectory);
            if (dir.exists() && dir.isDirectory()) {
                fileChooser.setInitialDirectory(dir);
            }
        }
        
        // Verwende die gleiche Logik wie saveFileAs für konsistente Dateinamen
        String initialFileName;
        if (currentFile != null) {
            // Verwende den ursprünglichen Dateinamen, aber mit DOCX-Erweiterung
            String baseName = currentFile.getName();
            int lastDot = baseName.lastIndexOf('.');
            if (lastDot > 0) {
                baseName = baseName.substring(0, lastDot);
            }
            initialFileName = baseName + ".docx";
        } else {
            // Fallback für neue Dateien
            initialFileName = "manuskript.docx";
        }
        fileChooser.setInitialFileName(initialFileName);
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("DOCX-Dateien", "*.docx")
        );
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Alle Dateien", "*.*")
        );
        
        File file = fileChooser.showSaveDialog(stage);
        if (file != null) {
            try {
                // Speichere das Verzeichnis für das nächste Mal
                String directory = file.getParent();
                if (directory != null) {
                    preferences.put("lastSaveDirectory", directory);
                }
                
                String markdownContent = codeArea.getText();
                docxProcessor.exportMarkdownToDocx(markdownContent, file);
                
                updateStatus("Als DOCX exportiert: " + file.getName());
                
            } catch (Exception e) {
                updateStatusError("Fehler beim DOCX-Export: " + e.getMessage());
                logger.error("Fehler beim DOCX-Export", e);
            }
        }
    }
    
    private void openFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Datei öffnen");
        
        // Lade das letzte Verzeichnis aus den Einstellungen
        String lastDirectory = preferences.get("lastOpenDirectory", null);
        if (lastDirectory != null) {
            File dir = new File(lastDirectory);
            if (dir.exists() && dir.isDirectory()) {
                fileChooser.setInitialDirectory(dir);
            }
        }
        
        // Extension-Filter basierend auf dem aktuellen Format
        switch (outputFormat) {
            case MARKDOWN:
                fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Markdown-Dateien", "*.md")
                );
                fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Text-Dateien", "*.txt")
                );
                break;
            case HTML:
                fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("HTML-Dateien", "*.html")
                );
                fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Text-Dateien", "*.txt")
                );
                break;
            case PLAIN_TEXT:
            default:
                fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Text-Dateien", "*.txt")
                );
                break;
        }
        
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Alle Dateien", "*.*")
        );
        
        File file = fileChooser.showOpenDialog(stage);
        if (file != null) {
            try {
                // Speichere das Verzeichnis für das nächste Mal
                String directory = file.getParent();
                if (directory != null) {
                    preferences.put("lastOpenDirectory", directory);
                }
                
                String content = new String(Files.readAllBytes(file.toPath()));
                codeArea.replaceText(content);
                // Cursor an den Anfang setzen
                codeArea.displaceCaret(0);
                codeArea.requestFollowCaret();
                currentFile = file;
                updateStatus("Datei geöffnet: " + file.getName());
            } catch (IOException e) {
                updateStatusError("Fehler beim Öffnen: " + e.getMessage());
            }
        }
    }
    
    private void newFile() {
        codeArea.clear();
        // Cursor an den Anfang setzen
        codeArea.displaceCaret(0);
        codeArea.requestFollowCaret();
        currentFile = null;
        // Lösche Undo/Redo-Stacks bei neuer Datei
        clearUndoRedoStacks();
        updateStatus("Neue Datei");
    }
    
    // Public Methoden für externe Verwendung
    public void setText(String text) {
        codeArea.replaceText(text);
        
        // Cursor an den Anfang setzen
        codeArea.displaceCaret(0);
        codeArea.requestFollowCaret();
        // Lösche Undo/Redo-Stacks beim Laden neuer Dateien
        clearUndoRedoStacks();
        updateStatus("Text geladen");
    }
    
    public void setCurrentFile(File file) {
        this.currentFile = file;
    }
    
    public File getCurrentFile() {
        return this.currentFile;
    }
    
    public String getText() {
        return codeArea.getText();
    }
    
    public void setStage(Stage stage) {
        this.stage = stage;
        
        // Fenster-Eigenschaften laden und anwenden
        loadWindowProperties();
        
        // Listener für Fenster-Änderungen hinzufügen
        stage.widthProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(oldVal)) {
                preferences.putDouble("window_width", newVal.doubleValue());
            }
        });
        
        stage.heightProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(oldVal)) {
                preferences.putDouble("window_height", newVal.doubleValue());
            }
        });
        
        stage.xProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(oldVal)) {
                preferences.putDouble("window_x", newVal.doubleValue());
            }
        });
        
        stage.yProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(oldVal)) {
                preferences.putDouble("window_y", newVal.doubleValue());
            }
        });
    }
    
    public void setOutputFormat(DocxProcessor.OutputFormat format) {
        this.outputFormat = format;
        
        // Export-Buttons nur für Markdown anzeigen
        boolean isMarkdown = (format == DocxProcessor.OutputFormat.MARKDOWN);
        btnExportRTF.setVisible(isMarkdown);
        btnExportDOCX.setVisible(isMarkdown);
    }
    
    public void setDocxProcessor(DocxProcessor docxProcessor) {
        this.docxProcessor = docxProcessor;
    }
    
    /**
     * Setzt den Titel des Editor-Fensters
     */
    public void setWindowTitle(String title) {
        if (stage != null) {
            stage.setTitle(title);
        }
        // NEU: Im Titelbalken-Label anzeigen
        if (lblWindowTitle != null) {
            lblWindowTitle.setText(title);
        }
        // Auch im Status-Label anzeigen
        if (lblStatus != null) {
            lblStatus.setText("Bereit");
        }
    }
    
    /**
     * Setzt das Theme vom Hauptfenster - für Synchronisation
     */
    public void setThemeFromMainWindow(int themeIndex) {
        // WICHTIG: Theme IMMER vom Hauptfenster übernehmen, nicht aus Preferences
        this.currentThemeIndex = themeIndex;
        // Theme sofort anwenden
        applyTheme(themeIndex);
        updateThemeButtonTooltip();
        
        // WICHTIG: Theme in Preferences speichern für Persistierung
        preferences.putInt("editor_theme", themeIndex);
        preferences.putInt("main_window_theme", themeIndex);
        
        logger.info("Theme vom Hauptfenster übernommen und gespeichert: {}", themeIndex);
        
        // Zusätzlicher verzögerter Theme-Refresh für bessere Kompatibilität
        Platform.runLater(() -> {
            Platform.runLater(() -> {
                applyTheme(themeIndex);
            });
        });
    }
    
    // ===== MAKRO-FUNKTIONALITÄT =====
    

    
    private void setupMacroPanel() {
        // Macro-Fenster erstellen
        createMacroWindow();
    }
    
    private void createMacroWindow() {
        macroStage = new Stage();
        macroStage.setTitle("Makro-Verwaltung");
        macroStage.setWidth(1200);
        macroStage.setHeight(800);
        macroStage.initModality(Modality.NONE);
        macroStage.initOwner(stage);
        
        // Makro-Panel programmatisch erstellen
        VBox macroPanel = createMacroPanel();
        
        Scene macroScene = new Scene(macroPanel);
        // CSS mit ResourceManager laden
        String cssPath = ResourceManager.getCssResource("css/editor.css");
        if (cssPath != null) {
            macroScene.getStylesheets().add(cssPath);
        }
        macroStage.setScene(macroScene);
        
        // Fenster-Position speichern/laden
        loadMacroWindowProperties();
        
        // Event-Handler für Fenster-Schließung
        macroStage.setOnCloseRequest(event -> {
            macroWindowVisible = false;
            event.consume(); // Verhindert das tatsächliche Schließen
            macroStage.hide();
        });
    }
    
    private VBox createMacroPanel() {
        VBox macroPanel = new VBox(10);
        macroPanel.getStyleClass().add("macro-panel");
        
        // Direkte Farbe basierend auf Theme setzen
        if (currentThemeIndex == 0) { // Weiß-Theme
            macroPanel.setStyle("-fx-background-color: #ffffff; -fx-border-color: #cccccc;");
        } else if (currentThemeIndex == 1) { // Schwarz-Theme
            macroPanel.setStyle("-fx-background-color: #1a1a1a; -fx-border-color: #333333;");
        } else if (currentThemeIndex == 2) { // Pastell-Theme
            macroPanel.setStyle("-fx-background-color: #f3e5f5; -fx-border-color: #e1bee7;");
        } else if (currentThemeIndex == 3) { // Blau-Theme
            macroPanel.setStyle("-fx-background-color: #1e3a8a; -fx-border-color: #3b82f6;");
        } else if (currentThemeIndex == 4) { // Grün-Theme
            macroPanel.setStyle("-fx-background-color: #166534; -fx-border-color: #22c55e;");
        } else if (currentThemeIndex == 5) { // Lila-Theme
            macroPanel.setStyle("-fx-background-color: #581c87; -fx-border-color: #a855f7;");
        }
        
        // Theme-Klassen für das Makro-Panel hinzufügen
        if (currentThemeIndex == 0) { // Weiß-Theme
            macroPanel.getStyleClass().add("weiss-theme");
        } else if (currentThemeIndex == 1) { // Schwarz-Theme
            macroPanel.getStyleClass().add("theme-dark");
        } else if (currentThemeIndex == 2) { // Pastell-Theme
            macroPanel.getStyleClass().add("pastell-theme");
        } else if (currentThemeIndex >= 3) { // Dunkle Themes: Blau (3), Grün (4), Lila (5)
            macroPanel.getStyleClass().add("theme-dark");
            if (currentThemeIndex == 3) {
                macroPanel.getStyleClass().add("blau-theme");
            } else if (currentThemeIndex == 4) {
                macroPanel.getStyleClass().add("gruen-theme");
            } else if (currentThemeIndex == 5) {
                macroPanel.getStyleClass().add("lila-theme");
            }
        }
        
        macroPanel.setPadding(new Insets(10));
        
        // Makro-Liste und Steuerung
        HBox macroControls = new HBox(10);
        macroControls.setAlignment(Pos.CENTER_LEFT);
        
        Label macroLabel = new Label("Makros:");
        macroLabel.setStyle("-fx-font-weight: bold;");
        
        ComboBox<String> cmbMacroList = new ComboBox<>();
        cmbMacroList.setPromptText("Makro auswählen...");
        cmbMacroList.setPrefWidth(200.0);
        
        Button btnNewMacro = new Button("Neues Makro");
        btnNewMacro.getStyleClass().addAll("button", "primary");
        
        Button btnDeleteMacro = new Button("Makro löschen");
        btnDeleteMacro.getStyleClass().addAll("button", "danger");
        
        Button btnSaveMacro = new Button("Makro speichern");
        btnSaveMacro.getStyleClass().addAll("button", "primary");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Button btnRunMacro = new Button("Makro ausführen");
        btnRunMacro.getStyleClass().addAll("button", "success");
        
        macroControls.getChildren().addAll(macroLabel, cmbMacroList, btnNewMacro, btnDeleteMacro, btnSaveMacro, spacer, btnRunMacro);
        
        // Makro-Details Panel
        VBox macroDetailsPanel = new VBox(5);
        macroDetailsPanel.setVisible(false);
        VBox.setVgrow(macroDetailsPanel, Priority.ALWAYS);
        
        Label stepsLabel = new Label("Makro-Schritte:");
        stepsLabel.setStyle("-fx-font-weight: bold;");
        
        // Makro-Schritt-Beschreibung
        HBox descriptionBox = new HBox(10);
        descriptionBox.setAlignment(Pos.CENTER_LEFT);
        
        Label descLabel = new Label("Schritt-Beschreibung:");
        descLabel.setStyle("-fx-font-weight: bold;");
        
        TextField txtMacroStepDescription = new TextField();
        txtMacroStepDescription.setPromptText("Beschreibung des Schritts...");
        txtMacroStepDescription.setPrefWidth(400.0);
        
        descriptionBox.getChildren().addAll(descLabel, txtMacroStepDescription);
        
        // Makro-Schritt-Eingabe
        HBox searchReplaceBox = new HBox(10);
        searchReplaceBox.setAlignment(Pos.CENTER_LEFT);
        
        Label searchLabel = new Label("Suchen:");
        searchLabel.setStyle("-fx-font-weight: bold;");
        
        TextField txtMacroSearch = new TextField();
        txtMacroSearch.setPromptText("Suchtext eingeben...");
        txtMacroSearch.setPrefWidth(200.0);
        
        Label replaceLabel = new Label("Ersetzen:");
        replaceLabel.setStyle("-fx-font-weight: bold;");
        
        TextField txtMacroReplace = new TextField();
        txtMacroReplace.setPromptText("Ersetzungstext eingeben...");
        txtMacroReplace.setPrefWidth(200.0);
        
        CheckBox chkMacroRegex = new CheckBox("Regex");
        CheckBox chkMacroCaseSensitive = new CheckBox("Case");
        CheckBox chkMacroWholeWord = new CheckBox("Word");
        
        Button btnMacroRegexHelp = new Button("?");
        btnMacroRegexHelp.getStyleClass().add("help-button");
        btnMacroRegexHelp.setStyle("-fx-min-width: 25px; -fx-max-width: 25px; -fx-min-height: 25px; -fx-max-height: 25px; -fx-font-weight: bold;");
        
        searchReplaceBox.getChildren().addAll(searchLabel, txtMacroSearch, replaceLabel, txtMacroReplace, 
                                             chkMacroRegex, chkMacroCaseSensitive, chkMacroWholeWord, btnMacroRegexHelp);
        
        // Makro-Schritte Tabelle
        TableView<MacroStep> tblMacroSteps = new TableView<>();
        VBox.setVgrow(tblMacroSteps, Priority.ALWAYS);
        
        TableColumn<MacroStep, Integer> colStepNumber = new TableColumn<>("Schritt");
        colStepNumber.setPrefWidth(50.0);
        colStepNumber.setCellValueFactory(new PropertyValueFactory<>("stepNumber"));
        
        TableColumn<MacroStep, Boolean> colEnabled = new TableColumn<>("Aktiv");
        colEnabled.setPrefWidth(50.0);
        colEnabled.setCellValueFactory(new PropertyValueFactory<>("enabled"));
        colEnabled.setCellFactory(param -> new TableCell<MacroStep, Boolean>() {
            private final CheckBox checkBox = new CheckBox();
            {
                checkBox.setOnAction(event -> {
                    MacroStep step = getTableView().getItems().get(getIndex());
                    if (step != null) {
                        step.setEnabled(checkBox.isSelected());
                    }
                });
            }
            
            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    boolean isSelected = item != null && item;
                    checkBox.setSelected(isSelected);
                    setGraphic(checkBox);
                }
            }
        });
        
        TableColumn<MacroStep, String> colDescription = new TableColumn<>("Beschreibung");
        colDescription.setPrefWidth(400.0);
        colDescription.setCellValueFactory(new PropertyValueFactory<>("description"));
        
        TableColumn<MacroStep, String> colSearchText = new TableColumn<>("Suchen");
        colSearchText.setPrefWidth(150.0);
        colSearchText.setCellValueFactory(new PropertyValueFactory<>("searchText"));
        
        TableColumn<MacroStep, String> colReplaceText = new TableColumn<>("Ersetzen");
        colReplaceText.setPrefWidth(150.0);
        colReplaceText.setCellValueFactory(new PropertyValueFactory<>("replaceText"));
        
        TableColumn<MacroStep, String> colOptions = new TableColumn<>("Optionen");
        colOptions.setPrefWidth(100.0);
        colOptions.setCellValueFactory(new PropertyValueFactory<>("optionsString"));
        
        TableColumn<MacroStep, String> colStatus = new TableColumn<>("Status");
        colStatus.setPrefWidth(120.0);
        colStatus.setCellValueFactory(param -> {
            MacroStep step = param.getValue();
            if (step == null) return new SimpleStringProperty("");
            
            SimpleStringProperty statusProperty = new SimpleStringProperty();
            Runnable updateStatus = () -> {
                if (!step.isEnabled()) {
                    statusProperty.set("Deaktiviert");
                } else {
                    int count = step.getReplacementCount();
                    String status = count == 0 ? "Keine Ersetzungen" : count + " Ersetzungen";
                    statusProperty.set(status);
                }
            };
            
            updateStatus.run();
            step.replacementCountProperty().addListener((obs, oldVal, newVal) -> updateStatus.run());
            step.enabledProperty().addListener((obs, oldVal, newVal) -> updateStatus.run());
            
            return statusProperty;
        });
        
        TableColumn<MacroStep, String> colActions = new TableColumn<>("Aktionen");
        colActions.setPrefWidth(100.0);
        colActions.setCellFactory(param -> new TableCell<MacroStep, String>() {
            private final Button editButton = new Button("Bearbeiten");
            {
                editButton.setOnAction(event -> {
                    MacroStep step = getTableView().getItems().get(getIndex());
                    editMacroStep(step);
                });
            }
            
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(editButton);
                }
            }
        });
        
        tblMacroSteps.getColumns().addAll(colStepNumber, colEnabled, colDescription, colSearchText, 
                                         colReplaceText, colOptions, colStatus, colActions);
        
        // Makro-Schritt-Buttons
        HBox stepButtons = new HBox(5);
        stepButtons.setAlignment(Pos.CENTER_LEFT);
        
        Button btnAddStep = new Button("Schritt hinzufügen");
        btnAddStep.getStyleClass().add("toolbar-button");
        
        Button btnRemoveStep = new Button("Schritt entfernen");
        btnRemoveStep.getStyleClass().add("toolbar-button");
        
        Button btnMoveStepUp = new Button("↑");
        btnMoveStepUp.getStyleClass().add("toolbar-button");
        
        Button btnMoveStepDown = new Button("↓");
        btnMoveStepDown.getStyleClass().add("toolbar-button");
        
        stepButtons.getChildren().addAll(btnAddStep, btnRemoveStep, btnMoveStepUp, btnMoveStepDown);
        
        macroDetailsPanel.getChildren().addAll(stepsLabel, descriptionBox, searchReplaceBox, tblMacroSteps, stepButtons);
        
        // Event-Handler für Makro-Buttons
        btnNewMacro.setOnAction(e -> createNewMacro());
        btnDeleteMacro.setOnAction(e -> deleteCurrentMacro());
        btnSaveMacro.setOnAction(e -> saveMacroToCSV());
        btnRunMacro.setOnAction(e -> runCurrentMacro());
        btnAddStep.setOnAction(e -> addMacroStep());
        btnRemoveStep.setOnAction(e -> removeMacroStep());
        btnMoveStepUp.setOnAction(e -> moveMacroStepUp());
        btnMoveStepDown.setOnAction(e -> moveMacroStepDown());
        btnMacroRegexHelp.setOnAction(e -> showRegexHelp());
        
        // Makro-Auswahl
        cmbMacroList.setOnAction(e -> selectMacro());
        
        // Referenzen für spätere Verwendung speichern
        this.cmbMacroList = cmbMacroList;
        this.btnNewMacro = btnNewMacro;
        this.btnDeleteMacro = btnDeleteMacro;
        this.btnSaveMacro = btnSaveMacro;
        this.btnRunMacro = btnRunMacro;
        this.macroDetailsPanel = macroDetailsPanel;
        this.tblMacroSteps = tblMacroSteps;
        this.txtMacroSearch = txtMacroSearch;
        this.txtMacroReplace = txtMacroReplace;
        this.txtMacroStepDescription = txtMacroStepDescription;
        this.chkMacroRegex = chkMacroRegex;
        this.chkMacroCaseSensitive = chkMacroCaseSensitive;
        this.chkMacroWholeWord = chkMacroWholeWord;
        this.btnAddStep = btnAddStep;
        this.btnRemoveStep = btnRemoveStep;
        this.btnMoveStepUp = btnMoveStepUp;
        this.btnMoveStepDown = btnMoveStepDown;
        this.btnMacroRegexHelp = btnMacroRegexHelp;
        
        // Makros laden
        loadMacros();
        
        macroPanel.getChildren().addAll(macroControls, macroDetailsPanel);
        return macroPanel;
    }
    
    private void loadMacroWindowProperties() {
        if (preferences != null) {
            double x = preferences.getDouble("macro_window_x", 100);
            double y = preferences.getDouble("macro_window_y", 100);
            double width = preferences.getDouble("macro_window_width", 1200);
            double height = preferences.getDouble("macro_window_height", 800);
            
            macroStage.setX(x);
            macroStage.setY(y);
            macroStage.setWidth(width);
            macroStage.setHeight(height);
            
            // Fenster-Position und Größe speichern
            macroStage.xProperty().addListener((obs, oldVal, newVal) -> 
                preferences.putDouble("macro_window_x", newVal.doubleValue()));
            macroStage.yProperty().addListener((obs, oldVal, newVal) -> 
                preferences.putDouble("macro_window_y", newVal.doubleValue()));
            macroStage.widthProperty().addListener((obs, oldVal, newVal) -> 
                preferences.putDouble("macro_window_width", newVal.doubleValue()));
            macroStage.heightProperty().addListener((obs, oldVal, newVal) -> 
                preferences.putDouble("macro_window_height", newVal.doubleValue()));
        }
    }
    
    private void toggleMacroPanel() {
        macroWindowVisible = !macroWindowVisible;
        
        if (macroWindowVisible) {
            // Makro-Fenster öffnen
            macroStage.show();
            macroStage.toFront();
            updateStatus("Makro-Fenster geöffnet");
        } else {
            // Makro-Fenster schließen
            macroStage.hide();
            updateStatus("Makro-Fenster geschlossen");
        }
    }
    
    private void toggleTextAnalysisPanel() {
        textAnalysisWindowVisible = !textAnalysisWindowVisible;
        
        if (textAnalysisWindowVisible) {
            // Textanalyse-Fenster öffnen
            if (textAnalysisStage == null) {
                createTextAnalysisWindow();
            }
            textAnalysisStage.show();
            textAnalysisStage.toFront();
            updateStatus("Textanalyse-Fenster geöffnet");
        } else {
            // Textanalyse-Fenster schließen
            if (textAnalysisStage != null) {
                textAnalysisStage.hide();
            }
            updateStatus("Textanalyse-Fenster geschlossen");
        }
    }
    
    private void createTextAnalysisWindow() {
        textAnalysisStage = new Stage();
        textAnalysisStage.setTitle("Textanalyse");
        textAnalysisStage.setWidth(800);
        textAnalysisStage.setHeight(600);
        textAnalysisStage.initModality(Modality.NONE);
        textAnalysisStage.initOwner(stage);
        
        // Textanalyse-Panel erstellen
        VBox textAnalysisPanel = createTextAnalysisPanel();
        
        Scene textAnalysisScene = new Scene(textAnalysisPanel);
        // CSS mit ResourceManager laden
        String cssPath = ResourceManager.getCssResource("css/editor.css");
        if (cssPath != null) {
            textAnalysisScene.getStylesheets().add(cssPath);
        }
        textAnalysisStage.setScene(textAnalysisScene);
        
        // Fenster-Position speichern/laden
        loadTextAnalysisWindowProperties();
        
        // Event-Handler für Fenster-Schließung
        textAnalysisStage.setOnCloseRequest(event -> {
            textAnalysisWindowVisible = false;
            event.consume(); // Verhindert das tatsächliche Schließen
            textAnalysisStage.hide();
        });
    }
    
    private VBox createTextAnalysisPanel() {
        VBox mainPanel = new VBox(10);
        mainPanel.setPadding(new Insets(15));
        mainPanel.getStyleClass().add("text-analysis-panel");
        
        // Korrekte Theme-Farben aus dem THEMES-Array verwenden
        String[] themeColors = THEMES[currentThemeIndex];
        String backgroundColor = themeColors[0];
        String textColor = themeColors[1];
        String accentColor = themeColors[2];
        String highlightColor = themeColors[3];
        
        // Hauptpanel mit korrekter Theme-Farbe
        mainPanel.setStyle(String.format("-fx-background-color: %s; -fx-border-color: %s;", backgroundColor, accentColor));
        
        // Titel
        Label titleLabel = new Label("Textanalyse");
        titleLabel.setStyle(String.format("-fx-font-size: 18px; -fx-font-weight: bold; -fx-padding: 0 0 10 0; -fx-text-fill: %s;", textColor));
        
        // Analyse-Buttons
        VBox analysisButtons = new VBox(8);
        
        // Sprechwörter finden
        HBox sprechwoerterBox = new HBox(10);
        Button btnSprechwoerter = new Button("Sprechwörter finden");
        btnSprechwoerter.getStyleClass().add("button");
        btnSprechwoerter.setPrefWidth(200);
        btnSprechwoerter.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: %s;", accentColor, textColor));
        Label lblSprechwoerter = new Label("Findet und markiert alle Sprechwörter (sagte, fragte, rief, etc.)");
        lblSprechwoerter.setWrapText(true);
        lblSprechwoerter.setStyle(String.format("-fx-text-fill: %s;", textColor));
        HBox.setHgrow(lblSprechwoerter, Priority.ALWAYS);
        sprechwoerterBox.getChildren().addAll(btnSprechwoerter, lblSprechwoerter);
        
        // Sprechantworten finden
        HBox sprechantwortenBox = new HBox(10);
        Button btnSprechantworten = new Button("Sprechantworten finden");
        btnSprechantworten.getStyleClass().add("button");
        btnSprechantworten.setPrefWidth(200);
        btnSprechantworten.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: %s;", accentColor, textColor));
        Label lblSprechantworten = new Label("Findet einfache Sprechantworten (sagte er., fragte sie., etc.)");
        lblSprechantworten.setWrapText(true);
        lblSprechantworten.setStyle(String.format("-fx-text-fill: %s;", textColor));
        HBox.setHgrow(lblSprechantworten, Priority.ALWAYS);
        sprechantwortenBox.getChildren().addAll(btnSprechantworten, lblSprechantworten);
        
        // Wortwiederholungen finden
        HBox wortwiederholungenBox = new HBox(10);
        Button btnWortwiederholungen = new Button("Wortwiederholungen finden");
        btnWortwiederholungen.getStyleClass().add("button");
        btnWortwiederholungen.setPrefWidth(200);
        btnWortwiederholungen.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: %s;", accentColor, textColor));
        
        VBox wortwiederholungenConfig = new VBox(5);
        Label lblWortwiederholungen = new Label("Findet Wörter, die sich in kurzem Abstand wiederholen");
        lblWortwiederholungen.setWrapText(true);
        lblWortwiederholungen.setStyle(String.format("-fx-text-fill: %s;", textColor));
        
        HBox abstandBox = new HBox(5);
        abstandBox.setAlignment(Pos.CENTER_LEFT);
        Label lblAbstand = new Label("Max. Abstand:");
        lblAbstand.setAlignment(Pos.CENTER_LEFT);
        lblAbstand.setStyle(String.format("-fx-text-fill: %s;", textColor));
        TextField txtAbstand = new TextField("10");
        txtAbstand.setPrefWidth(40);
        txtAbstand.setPromptText("10");
        txtAbstand.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: %s; -fx-prompt-text-fill: %s;", backgroundColor, textColor, highlightColor));
        Label lblAbstandUnit = new Label("Wörter");
        lblAbstandUnit.setAlignment(Pos.CENTER_LEFT);
        lblAbstandUnit.setStyle(String.format("-fx-text-fill: %s;", textColor));
        abstandBox.getChildren().addAll(lblAbstand, txtAbstand, lblAbstandUnit);
        
        wortwiederholungenConfig.getChildren().addAll(lblWortwiederholungen, abstandBox);
        HBox.setHgrow(wortwiederholungenConfig, Priority.ALWAYS);
        wortwiederholungenBox.getChildren().addAll(btnWortwiederholungen, wortwiederholungenConfig);
        
        // Wortwiederholung nah
        HBox wortwiederholungNahBox = new HBox(10);
        Button btnWortwiederholungNah = new Button("Wortwiederholung nah");
        btnWortwiederholungNah.getStyleClass().add("button");
        btnWortwiederholungNah.setPrefWidth(200);
        btnWortwiederholungNah.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: %s;", accentColor, textColor));
        Label lblWortwiederholungNah = new Label("Findet alle Wortwiederholungen im Abstand bis zu 5 Wörtern (ohne Ignore-Liste)");
        lblWortwiederholungNah.setWrapText(true);
        lblWortwiederholungNah.setStyle(String.format("-fx-text-fill: %s;", textColor));
        HBox.setHgrow(lblWortwiederholungNah, Priority.ALWAYS);
        wortwiederholungNahBox.getChildren().addAll(btnWortwiederholungNah, lblWortwiederholungNah);
        
        // Füllwörter finden
        HBox fuellwoerterBox = new HBox(10);
        Button btnFuellwoerter = new Button("Füllwörter finden");
        btnFuellwoerter.getStyleClass().add("button");
        btnFuellwoerter.setPrefWidth(200);
        btnFuellwoerter.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: %s;", accentColor, textColor));
        Label lblFuellwoerter = new Label("Findet typische Füllwörter (eigentlich, irgendwie, halt, etc.)");
        lblFuellwoerter.setWrapText(true);
        lblFuellwoerter.setStyle(String.format("-fx-text-fill: %s;", textColor));
        HBox.setHgrow(lblFuellwoerter, Priority.ALWAYS);
        fuellwoerterBox.getChildren().addAll(btnFuellwoerter, lblFuellwoerter);
        
        // Phrasen finden
        HBox phrasenBox = new HBox(10);
        Button btnPhrasen = new Button("Phrasen finden");
        btnPhrasen.getStyleClass().add("button");
        btnPhrasen.setPrefWidth(200);
        btnPhrasen.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: %s;", accentColor, textColor));
        Label lblPhrasen = new Label("Findet typische Phrasen (begann zu, sagte er, etc.)");
        lblPhrasen.setWrapText(true);
        lblPhrasen.setStyle(String.format("-fx-text-fill: %s;", textColor));
        HBox.setHgrow(lblPhrasen, Priority.ALWAYS);
        phrasenBox.getChildren().addAll(btnPhrasen, lblPhrasen);
        
        analysisButtons.getChildren().addAll(sprechwoerterBox, sprechantwortenBox, wortwiederholungenBox, wortwiederholungNahBox, fuellwoerterBox, phrasenBox);
        
        // Navigation-Buttons
        HBox navigationBox = new HBox(10);
        Button btnNext = new Button("↓ Nächster Treffer");
        btnNext.getStyleClass().add("button");
        btnNext.setPrefWidth(150);
        btnNext.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: %s;", accentColor, textColor));
        Button btnPrevious = new Button("↑ Vorheriger Treffer");
        btnPrevious.getStyleClass().add("button");
        btnPrevious.setPrefWidth(150);
        btnPrevious.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: %s;", accentColor, textColor));
        navigationBox.getChildren().addAll(btnNext, btnPrevious);
        
        // Status-Box
        TextArea statusArea = new TextArea();
        statusArea.setPrefRowCount(8);
        statusArea.setEditable(false);
        statusArea.setWrapText(true);
        statusArea.setPromptText("Analyse-Ergebnisse werden hier angezeigt...");
        statusArea.getStyleClass().add("status-area");
        statusArea.setStyle(String.format("-fx-background-color: %s !important; -fx-text-fill: %s !important; -fx-prompt-text-fill: %s !important; -fx-font-family: 'Consolas', 'Monaco', 'Courier New', monospace !important;", backgroundColor, textColor, highlightColor));
        
        // Content-Bereich später setzen, wenn die TextArea vollständig initialisiert ist
        Platform.runLater(() -> {
            Node content = statusArea.lookup(".content");
            if (content != null) {
                content.setStyle(String.format("-fx-background-color: %s !important;", backgroundColor));
            }
        });
        
        // Event-Handler
        btnSprechwoerter.setOnAction(e -> analyzeSprechwoerter(statusArea));
        btnSprechantworten.setOnAction(e -> analyzeSprechantworten(statusArea));
        btnWortwiederholungen.setOnAction(e -> {
            try {
                int abstand = Integer.parseInt(txtAbstand.getText());
                // boolean limitWords = chkLimitWords.isSelected();
                analyzeWortwiederholungen(statusArea, abstand, false);
            } catch (NumberFormatException ex) {
                statusArea.setText("Fehler: Bitte geben Sie eine gültige Zahl für den Abstand ein.");
            }
        });
        btnWortwiederholungNah.setOnAction(e -> analyzeWortwiederholungNah(statusArea));
        btnFuellwoerter.setOnAction(e -> analyzeFuellwoerter(statusArea));
        btnPhrasen.setOnAction(e -> analyzePhrasen(statusArea));
        btnNext.setOnAction(e -> findNext());
        btnPrevious.setOnAction(e -> findPrevious());
        
        mainPanel.getChildren().addAll(titleLabel, analysisButtons, navigationBox, statusArea);
        VBox.setVgrow(statusArea, Priority.ALWAYS);
        
        return mainPanel;
    }
    
    private void analyzeSprechwoerter(TextArea statusArea) {
        try {
            Properties props = loadTextAnalysisProperties();
            String sprechwoerter = props.getProperty("sprechwörter", "");
            String[] woerter = sprechwoerter.split(",");
            
            if (woerter.length == 0 || woerter[0].trim().isEmpty()) {
                statusArea.setText("Keine Sprechwörter in der Konfiguration gefunden.");
                return;
            }
            
            // Text aus dem Editor holen
            String text = codeArea.getText();
            
            // Häufigkeit jedes Sprechworts zählen
            Map<String, Integer> wordCount = new HashMap<>();
            for (String word : woerter) {
                String trimmedWord = word.trim();
                if (!trimmedWord.isEmpty()) {
                    // Regex-Pattern für das einzelne Wort
                    Pattern wordPattern = Pattern.compile("\\b" + Pattern.quote(trimmedWord) + "\\b", Pattern.CASE_INSENSITIVE);
                    Matcher matcher = wordPattern.matcher(text);
                    int count = 0;
                    while (matcher.find()) {
                        count++;
                    }
                    if (count > 0) {
                        wordCount.put(trimmedWord, count);
                    }
                }
            }
            
            // Regex-Pattern für alle Sprechwörter erstellen (für Markierung)
            StringBuilder patternBuilder = new StringBuilder();
            for (int i = 0; i < woerter.length; i++) {
                if (i > 0) patternBuilder.append("|");
                patternBuilder.append("\\b").append(woerter[i].trim()).append("\\b");
            }
            
            String pattern = patternBuilder.toString();
            
            // Suchtext in die Suchleiste setzen
            cmbSearchHistory.setValue(pattern);
            chkRegexSearch.setSelected(true);
            chkCaseSensitive.setSelected(false);
            chkWholeWord.setSelected(false);
            
            // Cache direkt aufbauen (ohne findText() zu verwenden)
            Pattern searchPattern = createSearchPattern(pattern);
            String content = codeArea.getText();
            
            // Cache zurücksetzen
            totalMatches = 0;
            currentMatchIndex = -1;
            lastSearchText = pattern;
            cachedPattern = searchPattern;
            cachedMatchPositions.clear();
            
            // Sammle alle Treffer-Positionen
            Matcher collectMatcher = searchPattern.matcher(content);
            while (collectMatcher.find()) {
                cachedMatchPositions.add(collectMatcher.start());
            }
            totalMatches = cachedMatchPositions.size();
            
            // Markiere alle Treffer
            if (totalMatches > 0) {
                StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
                int lastEnd = 0;
                
                for (int start : cachedMatchPositions) {
                    Matcher matchMatcher = searchPattern.matcher(content);
                    if (matchMatcher.find(start)) {
                        int end = matchMatcher.end();
                        
                        // Füge normalen Text vor dem Treffer hinzu
                        if (start > lastEnd) {
                            spansBuilder.add(Collections.emptyList(), start - lastEnd);
                        }
                        
                        // Füge markierten Text hinzu
                        spansBuilder.add(Collections.singleton("search-match-first"), end - start);
                        lastEnd = end;
                    }
                }
                
                // Füge restlichen Text hinzu
                if (lastEnd < content.length()) {
                    spansBuilder.add(Collections.emptyList(), content.length() - lastEnd);
                }
                
                // Wende die Markierungen an
                codeArea.setStyleSpans(0, spansBuilder.create());
                
                // Markiere den ersten Treffer
                if (!cachedMatchPositions.isEmpty()) {
                    int firstMatchStart = cachedMatchPositions.get(0);
                    Matcher highlightMatcher = searchPattern.matcher(content);
                    if (highlightMatcher.find(firstMatchStart)) {
                        highlightText(highlightMatcher.start(), highlightMatcher.end());
                        currentMatchIndex = 0;
                        updateMatchCount(currentMatchIndex + 1, totalMatches);
                    }
                }
            } else {
                // Entferne alle Markierungen
                codeArea.setStyleSpans(0, content.length(), StyleSpans.singleton(new ArrayList<>(), 0));
                updateMatchCount(0, 0);
            }
            
            // Ergebnisse anzeigen
            StringBuilder result = new StringBuilder();
            result.append("Sprechwörter-Analyse:\n");
            result.append("Pattern: ").append(pattern).append("\n");
            result.append("Gefundene Treffer: ").append(totalMatches).append("\n\n");
            
            if (!wordCount.isEmpty()) {
                result.append("Gefundene Sprechwörter:\n");
                result.append(String.format("%-20s %s\n", "Wort", "Häufigkeit"));
                result.append(String.format("%-20s %s\n", "----", "----------"));
                
                // Nach Häufigkeit sortieren (absteigend)
                wordCount.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .forEach(entry -> {
                        result.append(String.format("%-20s %d Mal\n", entry.getKey(), entry.getValue()));
                    });
                
                result.append("\nVerwende 'Nächster Treffer' und 'Vorheriger Treffer' um durch die Ergebnisse zu navigieren.\n");
                result.append("Gefundene Sprechwörter werden im Text markiert.\n");
            } else {
                result.append("Keine Sprechwörter im Text gefunden.\n");
            }
            
            statusArea.setText(result.toString());
            updateStatus("Sprechwörter-Analyse abgeschlossen: " + totalMatches + " Treffer");
            
        } catch (Exception e) {
            logger.error("Fehler bei Sprechwörter-Analyse", e);
            statusArea.setText("Fehler bei der Analyse: " + e.getMessage());
        }
    }
    
    private void analyzeSprechantworten(TextArea statusArea) {
        try {
            // Verwende ein einfaches, hart kodiertes Pattern für den Test
            String regex = "(sagte|fragte|rief|murmelte|flüsterte|antwortete|erklärte|berichtete|erzählte|bemerkte|kommentierte|behauptete|versicherte|warnte|vermutete|leugnete|versprach|schwor|informierte|mitteilte|diskutierte|debattierte|argumentierte|streitete|besprach|plauderte|schwatzte|raunte|brüllte|schrie|heulte|weinte|lachte|grinste|seufzte|stöhnte|ächzte|wimmerte|schluchzte|keuchte|stotterte|stammelte|fluchte|schimpfte|donnerte|knurrte|fauchte|zischte|brummte|summte|pfiff|trällerte|sang|deklamierte|rezitierte|sprach|redete|plapperte|schwadronierte|faselte|laberte|quasselte|schwätzte|quatschte|konversierte)\\s+\\w+\\.";
            
            // Debug: Pattern anzeigen
            logger.info("Sprechantworten-Pattern: " + regex);
            
            // Suchtext in die Suchleiste setzen
            cmbSearchHistory.setValue(regex);
            chkRegexSearch.setSelected(true);
            chkCaseSensitive.setSelected(false);
            chkWholeWord.setSelected(false);
            
            // Cache direkt aufbauen (ohne findText() zu verwenden)
            Pattern searchPattern = createSearchPattern(regex);
            String content = codeArea.getText();
            
            // Cache zurücksetzen
            totalMatches = 0;
            currentMatchIndex = -1;
            lastSearchText = regex;
            cachedPattern = searchPattern;
            cachedMatchPositions.clear();
            
            // Sammle alle Treffer-Positionen
            Matcher collectMatcher = searchPattern.matcher(content);
            while (collectMatcher.find()) {
                cachedMatchPositions.add(collectMatcher.start());
            }
            totalMatches = cachedMatchPositions.size();
            
            // Markiere alle Treffer
            if (totalMatches > 0) {
                StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
                int lastEnd = 0;
                
                for (int start : cachedMatchPositions) {
                    Matcher matchMatcher = searchPattern.matcher(content);
                    if (matchMatcher.find(start)) {
                        int end = matchMatcher.end();
                        
                        // Füge normalen Text vor dem Treffer hinzu
                        if (start > lastEnd) {
                            spansBuilder.add(Collections.emptyList(), start - lastEnd);
                        }
                        
                        // Füge markierten Text hinzu
                        spansBuilder.add(Collections.singleton("search-match-first"), end - start);
                        lastEnd = end;
                    }
                }
                
                // Füge restlichen Text hinzu
                if (lastEnd < content.length()) {
                    spansBuilder.add(Collections.emptyList(), content.length() - lastEnd);
                }
                
                // Wende die Markierungen an
                codeArea.setStyleSpans(0, spansBuilder.create());
                
                // Markiere den ersten Treffer
                if (!cachedMatchPositions.isEmpty()) {
                    int firstMatchStart = cachedMatchPositions.get(0);
                    Matcher highlightMatcher = searchPattern.matcher(content);
                    if (highlightMatcher.find(firstMatchStart)) {
                        highlightText(highlightMatcher.start(), highlightMatcher.end());
                        currentMatchIndex = 0;
                        updateMatchCount(currentMatchIndex + 1, totalMatches);
                    }
                }
            } else {
                // Entferne alle Markierungen
                codeArea.setStyleSpans(0, content.length(), StyleSpans.singleton(new ArrayList<>(), 0));
                updateMatchCount(0, 0);
            }
            
            // Ergebnisse anzeigen
            StringBuilder result = new StringBuilder();
            result.append("Sprechantworten-Analyse:\n");
            result.append("Pattern: ").append(regex).append("\n");
            result.append("Gefundene Treffer: ").append(totalMatches).append("\n\n");
            
            if (totalMatches > 0) {
                result.append("Verwende 'Nächster Treffer' und 'Vorheriger Treffer' um durch die Ergebnisse zu navigieren.\n");
            }
            
            statusArea.setText(result.toString());
            updateStatus("Sprechantworten-Analyse abgeschlossen: " + totalMatches + " Treffer");
            
        } catch (Exception e) {
            logger.error("Fehler bei Sprechantworten-Analyse", e);
            statusArea.setText("Fehler bei der Analyse: " + e.getMessage());
        }
    }
    
    private void analyzeWortwiederholungen(TextArea statusArea, int abstand, boolean limitWords) {
        try {
            Properties props = loadTextAnalysisProperties();
            int minLaenge = Integer.parseInt(props.getProperty("wortwiederholungen_min_laenge", "4"));
            String ignoreWordsStr = props.getProperty("wortwiederholungen_ignoriere_woerter", "");
            
            Set<String> ignoreWords = new HashSet<>();
            if (!ignoreWordsStr.isEmpty()) {
                for (String word : ignoreWordsStr.split(",")) {
                    ignoreWords.add(word.trim().toLowerCase());
                }
            }
            
            // Text aus dem Editor holen
            String text = codeArea.getText();
            
            // Wörter mit Regex finden (besser als split)
            Pattern wordPattern = Pattern.compile("\\b\\w+\\b", Pattern.CASE_INSENSITIVE);
            Matcher wordMatcher = wordPattern.matcher(text);
            
            // Wörter filtern (nur relevante Wörter)
            List<String> relevantWords = new ArrayList<>();
            List<Integer> wordPositions = new ArrayList<>();
            
            while (wordMatcher.find()) {
                String word = wordMatcher.group().toLowerCase();
                if (word.length() >= minLaenge && !ignoreWords.contains(word)) {
                    relevantWords.add(word);
                    wordPositions.add(wordMatcher.start());
                }
            }
            
            // Wortwiederholungen finden (echt optimiert)
            List<Wortwiederholung> wiederholungen = new ArrayList<>();
            
            // Verwende eine Map für O(1) Lookup
            Map<String, List<Integer>> wordOccurrences = new HashMap<>();
            
            // Sammle alle Vorkommen jedes Wortes (vom Textanfang)
            for (int i = 0; i < relevantWords.size(); i++) {
                String word = relevantWords.get(i);
                wordOccurrences.computeIfAbsent(word, k -> new ArrayList<>()).add(i);
            }
            
            // Finde Wiederholungen nur für Wörter mit mehreren Vorkommen
            for (Map.Entry<String, List<Integer>> entry : wordOccurrences.entrySet()) {
                String word = entry.getKey();
                List<Integer> positions = entry.getValue();
                
                // Nur prüfen wenn Wort mehrfach vorkommt
                if (positions.size() > 1) {
                    // Prüfe alle Paare innerhalb des Abstands
                    for (int i = 0; i < positions.size() - 1; i++) {
                        for (int j = i + 1; j < positions.size(); j++) {
                            int pos1 = positions.get(i);
                            int pos2 = positions.get(j);
                            int distance = pos2 - pos1;
                            
                            if (distance <= abstand) {
                                int charPos1 = wordPositions.get(pos1);
                                int charPos2 = wordPositions.get(pos2);
                                // Validiere, dass die Positionen gültig sind (nicht negativ)
                                if (charPos1 >= 0 && charPos2 >= 0) {
                                    wiederholungen.add(new Wortwiederholung(word, charPos1, charPos2, distance));
                                }
                            } else {
                                // Wenn der Abstand zu groß ist, können wir die innere Schleife abbrechen
                                break;
                            }
                        }
                    }
                }
            }
            
            // Ergebnisse sortieren (nach Abstand, dann nach Wort)
            wiederholungen.sort((a, b) -> {
                if (a.distance != b.distance) {
                    return Integer.compare(a.distance, b.distance);
                }
                return a.word.compareTo(b.word);
            });
            
            // Cache für Markierung aufbauen (nur spezifische Positionen die in Paaren vorkommen)
            if (!wiederholungen.isEmpty()) {
                String content = codeArea.getText();
                
                // Sammle nur die spezifischen Positionen, die in Paaren innerhalb des Abstands vorkommen
                Set<Integer> positionsToMark = new HashSet<>();
                for (Wortwiederholung w : wiederholungen) {
                    if (!ignoreWords.contains(w.word)) {
                        // Zusätzliche Validierung: Positionen müssen im gültigen Bereich liegen
                        if (w.pos1 >= 0 && w.pos1 < content.length()) {
                            positionsToMark.add(w.pos1);
                        } else {
                            logger.warn("Ungültige Position für Markierung (pos1): {} für Wort '{}', content.length={} ", w.pos1, w.word, content.length());
                        }
                        if (w.pos2 >= 0 && w.pos2 < content.length()) {
                            positionsToMark.add(w.pos2);
                        } else {
                            logger.warn("Ungültige Position für Markierung (pos2): {} für Wort '{}', content.length={} ", w.pos2, w.word, content.length());
                        }
                    }
                }
                
                AtomicBoolean skippedMarking = new AtomicBoolean(false);
                if (!positionsToMark.isEmpty()) {
                    StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
                    List<Integer> allPositions = new ArrayList<>();
                    
                    // Sortiere die Positionen
                    List<Integer> sortedPositions = new ArrayList<>(positionsToMark);
                    Collections.sort(sortedPositions);
                    
                    // Robuste StyleSpans-Berechnung
                    int currentPos = 0;
                    for (int i = 0; i < sortedPositions.size(); i++) {
                        int pos = sortedPositions.get(i);
                        if (pos < currentPos) continue; // Überschneidung überspringen

                        if (pos >= 0 && pos < content.length()) {
                            String wordAtPos = findWordAtPosition(content, pos);
                            if (wordAtPos != null && !wordAtPos.isEmpty()) {
                                int start = pos;
                                int end = pos + wordAtPos.length();
                                if (end <= content.length()) {
                                    allPositions.add(start);

                                    // Leeren Bereich vor dem Treffer markieren
                                    if (start > currentPos) {
                                        spansBuilder.add(Collections.emptyList(), start - currentPos);
                                    }

                                    // Markierten Bereich hinzufügen
                                    String styleClass = (i % 2 == 0) ? "search-match-first" : "search-match-second";
                                    spansBuilder.add(Collections.singleton(styleClass), end - start);
                                    currentPos = end;
                                }
                            }
                        }
                    }
                    // Restlichen Text als leer markieren
                    if (currentPos < content.length()) {
                        spansBuilder.add(Collections.emptyList(), content.length() - currentPos);
                    }
                    
                    // Wende die Markierungen an (im UI-Thread) - mit zusätzlicher Verzögerung für ersten Aufruf
                    final AtomicBoolean finalSkippedMarking = skippedMarking;
                    Platform.runLater(() -> {
                        // Zusätzliche Verzögerung für den ersten Aufruf (CSS-Styles laden)
                        Platform.runLater(() -> {
                            try {
                                StyleSpans<Collection<String>> spans = spansBuilder.create();
                                int totalSpanLength = 0;
                                for (StyleSpan<?> span : spans) {
                                    totalSpanLength += span.getLength();
                                }
                                if (totalSpanLength == content.length()) {
                                    codeArea.setStyleSpans(0, spans);
                                    // Cache für Navigation setzen
                                    totalMatches = allPositions.size();
                                    currentMatchIndex = -1;
                                    lastSearchText = "wortwiederholungen";
                                    cachedMatchPositions = new ArrayList<>(allPositions);
                                    if (finalSkippedMarking.get()) {
                                        updateStatusError("Warnung: Einige Markierungen wurden übersprungen (siehe Log)");
                                    }
                                } else {
                                    logger.error("StyleSpans-Länge stimmt nicht mit Textlänge überein! spans={}, text={}", totalSpanLength, content.length());
                                    updateStatusError("Fehler: Markierungen konnten nicht angewendet werden (siehe Log).");
                                }
                            } catch (Exception e) {
                                logger.error("Fehler beim Anwenden der Markierungen", e);
                            }
                        });
                    });
                    
                    // Setze den Suchtext in die Suchleiste für F3-Navigation
                    Platform.runLater(() -> {
                        cmbSearchHistory.setValue("wortwiederholungen");
                        chkRegexSearch.setSelected(true);
                        chkCaseSensitive.setSelected(false);
                        chkWholeWord.setSelected(false);
                        
                        // Pattern für Navigation erstellen (wie bei "Wortwiederholung nah")
                        Set<String> wordsToMark = new HashSet<>();
                        for (Wortwiederholung w : wiederholungen) {
                            if (!ignoreWords.contains(w.word)) {
                                wordsToMark.add(w.word);
                            }
                        }
                        
                        if (!wordsToMark.isEmpty()) {
                            StringBuilder patternBuilder = new StringBuilder();
                            for (String word : wordsToMark) {
                                if (patternBuilder.length() > 0) patternBuilder.append("|");
                                patternBuilder.append(Pattern.quote(word));
                            }
                            cachedPattern = Pattern.compile("\\b(" + patternBuilder.toString() + ")\\b", Pattern.CASE_INSENSITIVE);
                        }
                    });
                }
            }
            
            // Ergebnisse anzeigen (nur relevante Wörter, ohne ignorierten)
            StringBuilder result = new StringBuilder();
            result.append("Wortwiederholungen-Analyse:\n");
            result.append("Konfiguration: Abstand ≤ ").append(abstand).append(" Wörter, Mindestlänge ≥ ").append(minLaenge).append(" Zeichen\n");
            
            // Zähle nur relevante Wiederholungen (ohne ignorierten Wörter)
            long relevantWiederholungen = wiederholungen.stream()
                .filter(w -> !ignoreWords.contains(w.word))
                .count();
            
            result.append("Gefundene Wiederholungen: ").append(wiederholungen.size()).append(" (davon ").append(relevantWiederholungen).append(" relevante)\n\n");
            
            if (!wiederholungen.isEmpty()) {
                result.append("Relevante Wortwiederholungen (sortiert nach Abstand):\n");
                result.append("=".repeat(75)).append("\n");
                result.append(String.format("%-20s %8s %8s %8s\n", "Wort", "Pos 1", "Pos 2", "Abstand"));
                result.append("-".repeat(75)).append("\n");
                
                for (Wortwiederholung w : wiederholungen) {
                    // Nur relevante Wörter anzeigen (nicht ignorierten)
                    if (!ignoreWords.contains(w.word)) {
                        String word = w.word.length() > 18 ? w.word.substring(0, 15) + "..." : w.word;
                        result.append(String.format("%-20s %8d %8d %8d\n", word, w.pos1, w.pos2, w.distance));
                    }
                }
                
                result.append("=".repeat(75)).append("\n");
                
                result.append("\nVerwende 'Nächster Treffer' und 'Vorheriger Treffer' um durch die Ergebnisse zu navigieren.\n");
                result.append("Gefundene Wörter werden im Text markiert.\n");
            } else {
                result.append("Keine Wortwiederholungen im konfigurierten Abstand gefunden.\n");
            }
            
            statusArea.setText(result.toString());
            String limitText = " (alle Wörter)";
            updateStatus("Wortwiederholungen-Analyse abgeschlossen: " + wiederholungen.size() + " Wiederholungen" + limitText);
            
        } catch (Exception e) {
            logger.error("Fehler bei Wortwiederholungen-Analyse", e);
            statusArea.setText("Fehler bei der Analyse: " + e.getMessage());
        }
    }
    
    private void analyzeWortwiederholungNah(TextArea statusArea) {
        try {
            Properties props = loadTextAnalysisProperties();
            int minLaenge = Integer.parseInt(props.getProperty("wortwiederholungen_min_laenge", "4"));
            
            String content = codeArea.getText();
            if (content.isEmpty()) {
                statusArea.setText("Kein Text zum Analysieren vorhanden.");
                return;
            }
            
            // Tokenize text into words with positions (including German umlauts)
            Pattern wordPattern = Pattern.compile("\\b[a-zA-ZäöüßÄÖÜ]+\\b");
            Matcher wordMatcher = wordPattern.matcher(content);
            List<String> words = new ArrayList<>();
            List<Integer> wordPositions = new ArrayList<>();
            
            while (wordMatcher.find()) {
                String word = wordMatcher.group().toLowerCase();
                if (word.length() >= minLaenge) {  // Filter by length immediately
                    words.add(word);
                    wordPositions.add(wordMatcher.start());
                }
            }
            
            // Debug: Zeige die ersten 10 Wörter
            System.out.println("DEBUG: Erste 10 Wörter:");
            for (int i = 0; i < Math.min(10, words.size()); i++) {
                System.out.println("  Index " + i + ": '" + words.get(i) + "'");
            }
            
            // KOMPLETT NEUE LOGIK: Finde nur benachbarte Wiederholungen
            List<Wortwiederholung> wiederholungen = new ArrayList<>();
            int abstand = 5;
            
            // Gehe durch alle Wörter
            for (int i = 0; i < words.size() - 1; i++) {
                String currentWord = words.get(i);
                
                // Suche nur im nächsten Abstand-Bereich
                for (int j = i + 1; j <= Math.min(i + abstand + 1, words.size() - 1); j++) {
                    String nextWord = words.get(j);
                    
                    // Wenn das gleiche Wort gefunden wurde
                    if (currentWord.equals(nextWord)) {
                        int distance = j - i - 1; // Anzahl Wörter zwischen den Wiederholungen
                        
                        // Nur wenn der Abstand innerhalb des Limits liegt
                        if (distance >= 0 && distance <= abstand) {
                            int charPos1 = wordPositions.get(i);
                            int charPos2 = wordPositions.get(j);
                            
                            // Validate positions
                            if (charPos1 >= 0 && charPos1 < content.length() && 
                                charPos2 >= 0 && charPos2 < content.length()) {
                                
                                // Nur echte Wiederholungen: Überprüfe, ob die Wörter wirklich an den berechneten Positionen stehen
                                if (charPos1 >= 0 && charPos1 < content.length() && 
                                    charPos2 >= 0 && charPos2 < content.length() &&
                                    charPos1 != charPos2) { // Nicht die gleiche Position
                                    
                                    // Extrahiere das Wort an Position 1
                                    int start1 = charPos1;
                                    while (start1 > 0 && Character.isLetterOrDigit(content.charAt(start1 - 1))) {
                                        start1--;
                                    }
                                    int end1 = charPos1;
                                    while (end1 < content.length() && Character.isLetterOrDigit(content.charAt(end1))) {
                                        end1++;
                                    }
                                    String word1 = content.substring(start1, end1).toLowerCase();
                                    
                                    // Extrahiere das Wort an Position 2
                                    int start2 = charPos2;
                                    while (start2 > 0 && Character.isLetterOrDigit(content.charAt(start2 - 1))) {
                                        start2--;
                                    }
                                    int end2 = charPos2;
                                    while (end2 < content.length() && Character.isLetterOrDigit(content.charAt(end2))) {
                                        end2++;
                                    }
                                    String word2 = content.substring(start2, end2).toLowerCase();
                                    
                                    // Nur wenn beide Wörter exakt übereinstimmen
                                    if (currentWord.equals(word1) && currentWord.equals(word2)) {
                                        wiederholungen.add(new Wortwiederholung(currentWord, charPos1, charPos2, distance));
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // Sort results by distance, then by word
            wiederholungen.sort((a, b) -> {
                int distanceCompare = Integer.compare(a.distance, b.distance);
                return distanceCompare != 0 ? distanceCompare : a.word.compareTo(b.word);
            });
            
            // Apply marking
            if (!wiederholungen.isEmpty()) {
                String content2 = codeArea.getText();
                
                // Collect only specific positions to mark
                Set<Integer> positionsToMark = new HashSet<>();
                for (Wortwiederholung w : wiederholungen) {
                    positionsToMark.add(w.pos1);
                    positionsToMark.add(w.pos2);
                }
                
                if (!positionsToMark.isEmpty()) {
                    // Create a pattern for all words to be marked
                    StringBuilder patternBuilder = new StringBuilder();
                    Set<String> wordsToMark = new HashSet<>();
                    for (Wortwiederholung w : wiederholungen) {
                        wordsToMark.add(w.word);
                    }
                    
                    for (String word : wordsToMark) {
                        if (patternBuilder.length() > 0) patternBuilder.append("|");
                        patternBuilder.append(Pattern.quote(word));
                    }
                    
                    Pattern markPattern = Pattern.compile("\\b(" + patternBuilder.toString() + ")\\b", Pattern.CASE_INSENSITIVE);
                    Matcher markMatcher = markPattern.matcher(content2);
                    
                    StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
                    int lastEnd = 0;
                    
                    while (markMatcher.find()) {
                        int start = markMatcher.start();
                        int end = markMatcher.end();
                        String matchedWord = markMatcher.group(1).toLowerCase();
                        
                        // Check if this position should be marked
                        boolean shouldMark = false;
                        for (Wortwiederholung w : wiederholungen) {
                            if (w.word.equals(matchedWord) && 
                                ((start == w.pos1) || (start == w.pos2))) {
                                shouldMark = true;
                                break;
                            }
                        }
                        
                        if (shouldMark) {
                            // Count previous occurrences of the same word to determine style
                            int occurrenceCount = 0;
                            for (Wortwiederholung w : wiederholungen) {
                                if (w.word.equals(matchedWord) && w.pos1 < start) {
                                    occurrenceCount++;
                                }
                            }
                            
                            String styleClass = occurrenceCount == 0 ? "search-match-first" : "search-match-second";
                            
                            spansBuilder.add(Collections.emptyList(), start - lastEnd);
                            spansBuilder.add(Collections.singleton(styleClass), end - start);
                            lastEnd = end;
                        } else {
                            spansBuilder.add(Collections.emptyList(), end - lastEnd);
                            lastEnd = end;
                        }
                    }
                    
                    spansBuilder.add(Collections.emptyList(), content2.length() - lastEnd);
                    
                    // Apply styles on JavaFX Application Thread
                    Platform.runLater(() -> {
                        codeArea.setStyleSpans(0, spansBuilder.create());
                        
                        // Update cache for navigation
                        List<Integer> allPositions = new ArrayList<>();
                        for (Wortwiederholung w : wiederholungen) {
                            allPositions.add(w.pos1);
                            allPositions.add(w.pos2);
                        }
                        Collections.sort(allPositions);
                        cachedMatchPositions = new ArrayList<>(allPositions);
                        cachedPattern = markPattern;
                        totalMatches = cachedMatchPositions.size();
                        currentMatchIndex = -1;
                        lastSearchText = "wortwiederholung_nah";
                        
                        // Setze den Suchtext in die Suchleiste für F3-Navigation
                        cmbSearchHistory.setValue("wortwiederholung_nah");
                        chkRegexSearch.setSelected(true);
                        chkCaseSensitive.setSelected(false);
                        chkWholeWord.setSelected(false);
                    });
                }
            }
            
            // Display results - nur einzigartige Wortpaare
            StringBuilder result = new StringBuilder();
            result.append("=== WORTWIEDERHOLUNGEN NAH ===\n\n");
            
            if (!wiederholungen.isEmpty()) {
                // Sammle einzigartige Wortpaare (Wort + Abstand)
                Map<String, Integer> uniquePairs = new LinkedHashMap<>();
                for (Wortwiederholung w : wiederholungen) {
                    String pairKey = w.word + " (Abstand " + w.distance + ")";
                    uniquePairs.put(pairKey, uniquePairs.getOrDefault(pairKey, 0) + 1);
                }
                
                // Sortiere nach Häufigkeit, dann nach Wort
                List<Map.Entry<String, Integer>> sortedPairs = new ArrayList<>(uniquePairs.entrySet());
                sortedPairs.sort((a, b) -> {
                    int countCompare = Integer.compare(b.getValue(), a.getValue());
                    return countCompare != 0 ? countCompare : a.getKey().compareTo(b.getKey());
                });
                
                result.append("=".repeat(50)).append("\n");
                result.append(String.format("%-35s %s\n", "Wortpaar", "Anzahl"));
                result.append("-".repeat(50)).append("\n");
                
                for (Map.Entry<String, Integer> entry : sortedPairs) {
                    String pair = entry.getKey().length() > 32 ? entry.getKey().substring(0, 29) + "..." : entry.getKey();
                    result.append(String.format("%-35s %dx\n", pair, entry.getValue()));
                }
                
                result.append("=".repeat(50)).append("\n");
                
                result.append("\n==============================\n");
                result.append("GESAMT: ").append(wiederholungen.size()).append(" Wiederholungen gefunden\n");
                result.append("==============================\n\n");
                
                result.append("💡 Verwende F3 oder die Navigation-Buttons zum Durchsuchen der Treffer.");
            } else {
                result.append("Keine Wortwiederholungen in der Nähe gefunden.");
            }
            
            statusArea.setText(result.toString());
            logger.info("Editor Status: Wortwiederholung nah-Analyse abgeschlossen: {} Wiederholungen", wiederholungen.size());
            
        } catch (Exception e) {
            statusArea.setText("Fehler bei der Analyse: " + e.getMessage());
            logger.error("Fehler bei Wortwiederholung nah-Analyse", e);
        }
    }
    
    // Hilfsklasse für Wortwiederholungen
        private static class Wortwiederholung {
        final String word;
        final int pos1;
        final int pos2;
        final int distance;

        Wortwiederholung(String word, int pos1, int pos2, int distance) {
            this.word = word;
            this.pos1 = pos1;
            this.pos2 = pos2;
            this.distance = distance;
        }
    }
    
    private String findWordAtPosition(String content, int position) {
        if (position < 0 || position >= content.length()) {
            return null;
        }
        
        // Finde den Anfang des Wortes
        int start = position;
        while (start > 0 && Character.isLetterOrDigit(content.charAt(start - 1))) {
            start--;
        }
        
        // Finde das Ende des Wortes
        int end = position;
        while (end < content.length() && Character.isLetterOrDigit(content.charAt(end))) {
            end++;
        }
        
        if (start < end) {
            return content.substring(start, end);
        }
        
        return null;
    }
    
    private Properties loadTextAnalysisProperties() throws IOException {
        Properties props = new Properties();
        try (InputStream input = ResourceManager.getPropertiesResource("textanalysis.properties")) {
            if (input == null) {
                throw new IOException("textanalysis.properties nicht gefunden");
            }
            props.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        }
        return props;
    }
    
    private void analyzeFuellwoerter(TextArea statusArea) {
        try {
            Properties props = loadTextAnalysisProperties();
            String fuellwoerter = props.getProperty("fuellwoerter", "");
            String[] woerter = fuellwoerter.split(",");
            
            if (woerter.length == 0 || woerter[0].trim().isEmpty()) {
                statusArea.setText("Keine Füllwörter in der Konfiguration gefunden.");
                return;
            }
            
            String text = codeArea.getText();
            Map<String, Integer> wordCount = new HashMap<>();
            List<Integer> allPositions = new ArrayList<>();
            
            for (String word : woerter) {
                String trimmedWord = word.trim();
                if (!trimmedWord.isEmpty()) {
                    Pattern wordPattern = Pattern.compile("\\b" + Pattern.quote(trimmedWord) + "\\b", Pattern.CASE_INSENSITIVE);
                    Matcher matcher = wordPattern.matcher(text);
                    int count = 0;
                    while (matcher.find()) {
                        count++;
                        allPositions.add(matcher.start());
                    }
                    if (count > 0) {
                        wordCount.put(trimmedWord, count);
                    }
                }
            }
            
            // Markierungen setzen
            if (!allPositions.isEmpty()) {
                StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
                Collections.sort(allPositions);
                
                int currentPos = 0;
                for (int pos : allPositions) {
                    if (pos >= currentPos) {
                        // Finde das Wort an dieser Position
                        for (String word : woerter) {
                            String trimmedWord = word.trim();
                            if (!trimmedWord.isEmpty()) {
                                Pattern wordPattern = Pattern.compile("\\b" + Pattern.quote(trimmedWord) + "\\b", Pattern.CASE_INSENSITIVE);
                                Matcher matcher = wordPattern.matcher(text);
                                matcher.region(pos, text.length());
                                if (matcher.lookingAt()) {
                                    int start = matcher.start();
                                    int end = matcher.end();
                                    if (start >= currentPos) {
                                        spansBuilder.add(Collections.emptyList(), start - currentPos);
                                        spansBuilder.add(Collections.singleton("fuellwoerter"), end - start);
                                        currentPos = end;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
                spansBuilder.add(Collections.emptyList(), text.length() - currentPos);
                
                StyleSpans<Collection<String>> spans = spansBuilder.create();
                codeArea.setStyleSpans(0, spans);
            }
            
            // Suchtext in die Suchleiste setzen (für F3-Navigation)
            StringBuilder patternBuilder = new StringBuilder();
            for (int i = 0; i < woerter.length; i++) {
                if (i > 0) patternBuilder.append("|");
                patternBuilder.append("\\b").append(woerter[i].trim()).append("\\b");
            }
            
            String pattern = patternBuilder.toString();
            cmbSearchHistory.setValue(pattern);
            chkRegexSearch.setSelected(true);
            chkCaseSensitive.setSelected(false);
            chkWholeWord.setSelected(false);
            
            // Cache direkt aufbauen (ohne findText() zu verwenden)
            Pattern searchPattern = createSearchPattern(pattern);
            String content = codeArea.getText();
            
            // Cache zurücksetzen
            totalMatches = 0;
            currentMatchIndex = -1;
            lastSearchText = pattern;
            cachedPattern = searchPattern;
            cachedMatchPositions.clear();
            
            // Alle Treffer finden und cachen
            Matcher matcher = searchPattern.matcher(content);
            while (matcher.find()) {
                cachedMatchPositions.add(matcher.start());
                totalMatches++;
            }
            
            // Ergebnis anzeigen
            StringBuilder result = new StringBuilder();
            result.append("=== FÜLLWÖRTER-ANALYSE ===\n\n");
            
            // Sortiere nach Häufigkeit (absteigend)
            List<Map.Entry<String, Integer>> sortedEntries = new ArrayList<>(wordCount.entrySet());
            sortedEntries.sort((a, b) -> b.getValue().compareTo(a.getValue()));
            
            int totalCount = 0;
            for (Map.Entry<String, Integer> entry : sortedEntries) {
                String word = entry.getKey();
                int count = entry.getValue();
                result.append(String.format("%-20s %3dx\n", word, count));
                totalCount += count;
            }
            
            result.append("\n" + "=".repeat(30) + "\n");
            result.append(String.format("GESAMT: %d Füllwörter gefunden\n", totalCount));
            result.append("=".repeat(30) + "\n\n");
            result.append("💡 Verwende F3 oder die Navigation-Buttons zum Durchsuchen der Treffer.");
            statusArea.setText(result.toString());
            
            // Status aktualisieren
            updateMatchCount(0, totalMatches);
            updateStatus("Füllwörter-Analyse abgeschlossen: " + totalCount + " Füllwörter");
            
        } catch (Exception e) {
            statusArea.setText("Fehler bei der Füllwörter-Analyse: " + e.getMessage());
            logger.error("Fehler bei der Füllwörter-Analyse", e);
        }
    }
    
    private void analyzePhrasen(TextArea statusArea) {
        try {
            Properties props = loadTextAnalysisProperties();
            String text = codeArea.getText();
            Map<String, Integer> phraseCount = new HashMap<>();
            List<Integer> allPositions = new ArrayList<>();
            
            // Alle Phrasen-Kategorien durchgehen
            String[] categories = {"phrasen_begann", "phrasen_emotionen", "phrasen_dialog", "phrasen_denken", "phrasen_gefuehle", "phrasen_bewegung"};
            
            for (String category : categories) {
                String phrases = props.getProperty(category, "");
                String[] phraseArray = phrases.split(",");
                
                for (String phrase : phraseArray) {
                    String trimmedPhrase = phrase.trim();
                    if (!trimmedPhrase.isEmpty()) {
                        // Wildcard-Unterstützung: * durch .* ersetzen
                        String regexPattern = trimmedPhrase.replace("*", ".*");
                        Pattern pattern = Pattern.compile(regexPattern, Pattern.CASE_INSENSITIVE);
                        Matcher matcher = pattern.matcher(text);
                        
                        int count = 0;
                        while (matcher.find()) {
                            count++;
                            allPositions.add(matcher.start());
                        }
                        
                        if (count > 0) {
                            phraseCount.put(trimmedPhrase, count);
                        }
                    }
                }
            }
            
            // Markierungen setzen
            if (!allPositions.isEmpty()) {
                StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
                Collections.sort(allPositions);
                
                int currentPos = 0;
                for (int pos : allPositions) {
                    if (pos >= currentPos) {
                        // Finde die Phrase an dieser Position
                        for (String category : categories) {
                            String phrases = props.getProperty(category, "");
                            String[] phraseArray = phrases.split(",");
                            
                            for (String phrase : phraseArray) {
                                String trimmedPhrase = phrase.trim();
                                if (!trimmedPhrase.isEmpty()) {
                                    String regexPattern = trimmedPhrase.replace("*", ".*");
                                    Pattern pattern = Pattern.compile(regexPattern, Pattern.CASE_INSENSITIVE);
                                    Matcher matcher = pattern.matcher(text);
                                    matcher.region(pos, text.length());
                                    if (matcher.lookingAt()) {
                                        int start = matcher.start();
                                        int end = matcher.end();
                                        if (start >= currentPos) {
                                            spansBuilder.add(Collections.emptyList(), start - currentPos);
                                            spansBuilder.add(Collections.singleton("phrasen"), end - start);
                                            currentPos = end;
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                spansBuilder.add(Collections.emptyList(), text.length() - currentPos);
                
                StyleSpans<Collection<String>> spans = spansBuilder.create();
                codeArea.setStyleSpans(0, spans);
            }
            
            // Suchtext in die Suchleiste setzen (für F3-Navigation)
            StringBuilder patternBuilder = new StringBuilder();
            for (String category : categories) {
                String phrases = props.getProperty(category, "");
                String[] phraseArray = phrases.split(",");
                
                for (String phrase : phraseArray) {
                    String trimmedPhrase = phrase.trim();
                    if (!trimmedPhrase.isEmpty()) {
                        if (patternBuilder.length() > 0) patternBuilder.append("|");
                        // Wildcard-Unterstützung: * durch .* ersetzen
                        String regexPattern = trimmedPhrase.replace("*", ".*");
                        patternBuilder.append(regexPattern);
                    }
                }
            }
            
            String pattern = patternBuilder.toString();
            cmbSearchHistory.setValue(pattern);
            chkRegexSearch.setSelected(true);
            chkCaseSensitive.setSelected(false);
            chkWholeWord.setSelected(false);
            
            // Cache direkt aufbauen (ohne findText() zu verwenden)
            Pattern searchPattern = createSearchPattern(pattern);
            String content = codeArea.getText();
            
            // Cache zurücksetzen
            totalMatches = 0;
            currentMatchIndex = -1;
            lastSearchText = pattern;
            cachedPattern = searchPattern;
            cachedMatchPositions.clear();
            
            // Alle Treffer finden und cachen
            Matcher matcher = searchPattern.matcher(content);
            while (matcher.find()) {
                cachedMatchPositions.add(matcher.start());
                totalMatches++;
            }
            
            // Ergebnis anzeigen
            StringBuilder result = new StringBuilder();
            result.append("=== PHRASEN-ANALYSE ===\n\n");
            
            // Sortiere nach Häufigkeit (absteigend)
            List<Map.Entry<String, Integer>> sortedEntries = new ArrayList<>(phraseCount.entrySet());
            sortedEntries.sort((a, b) -> b.getValue().compareTo(a.getValue()));
            
            int totalCount = 0;
            for (Map.Entry<String, Integer> entry : sortedEntries) {
                String phrase = entry.getKey();
                int count = entry.getValue();
                result.append(String.format("%-30s %3dx\n", phrase, count));
                totalCount += count;
            }
            
            result.append("\n" + "=".repeat(40) + "\n");
            result.append(String.format("GESAMT: %d Phrasen gefunden\n", totalCount));
            result.append("=".repeat(40) + "\n\n");
            result.append("💡 Verwende F3 oder die Navigation-Buttons zum Durchsuchen der Treffer.");
            statusArea.setText(result.toString());
            
            // Status aktualisieren
            updateMatchCount(0, totalMatches);
            updateStatus("Phrasen-Analyse abgeschlossen: " + totalCount + " Phrasen");
            
        } catch (Exception e) {
            statusArea.setText("Fehler bei der Phrasen-Analyse: " + e.getMessage());
            logger.error("Fehler bei der Phrasen-Analyse", e);
        }
    }
    
    private void loadTextAnalysisWindowProperties() {
        // Fenster-Position und Größe laden
        double x = preferences.getDouble("textanalysis_window_x", 100);
        double y = preferences.getDouble("textanalysis_window_y", 100);
        double width = preferences.getDouble("textanalysis_window_width", 800);
        double height = preferences.getDouble("textanalysis_window_height", 600);
        
        textAnalysisStage.setX(x);
        textAnalysisStage.setY(y);
        textAnalysisStage.setWidth(width);
        textAnalysisStage.setHeight(height);
        
        // Event-Handler für Fenster-Änderungen
        textAnalysisStage.xProperty().addListener((obs, oldVal, newVal) -> 
            preferences.putDouble("textanalysis_window_x", newVal.doubleValue()));
        textAnalysisStage.yProperty().addListener((obs, oldVal, newVal) -> 
            preferences.putDouble("textanalysis_window_y", newVal.doubleValue()));
        textAnalysisStage.widthProperty().addListener((obs, oldVal, newVal) -> 
            preferences.putDouble("textanalysis_window_width", newVal.doubleValue()));
        textAnalysisStage.heightProperty().addListener((obs, oldVal, newVal) -> 
            preferences.putDouble("textanalysis_window_height", newVal.doubleValue()));
    }
    
    private void createNewMacro() {
        TextInputDialog dialog = new TextInputDialog("Neues Makro");
        dialog.setTitle("Neues Makro erstellen");
        dialog.setHeaderText("Makro-Name eingeben");
        dialog.setContentText("Name:");
        
        Optional<String> result = dialog.showAndWait();
        if (result.isPresent() && !result.get().trim().isEmpty()) {
            String macroName = result.get().trim();
            Macro newMacro = new Macro(macroName);
            macros.add(newMacro);
            cmbMacroList.getItems().add(macroName);
            cmbMacroList.setValue(macroName);
            currentMacro = newMacro;
            macroDetailsPanel.setVisible(true);
    
            tblMacroSteps.setItems(newMacro.getSteps());
            saveMacros();
            updateStatus("Neues Makro erstellt: " + macroName);
        }
    }
    
    private void deleteCurrentMacro() {
        if (currentMacro != null) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Makro löschen");
            alert.setHeaderText("Makro löschen bestätigen");
            alert.setContentText("Möchten Sie das Makro '" + currentMacro.getName() + "' wirklich löschen?");
            
            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                String macroName = currentMacro.getName();
                macros.remove(currentMacro);
                cmbMacroList.getItems().remove(macroName);
                cmbMacroList.setValue(null);
                currentMacro = null;
                macroDetailsPanel.setVisible(false);
                tblMacroSteps.setItems(null);
                saveMacros();
                updateStatus("Makro gelöscht: " + macroName);
            }
        }
    }
    
    private void selectMacro() {
        String selectedMacroName = cmbMacroList.getValue();
        if (selectedMacroName != null) {
            currentMacro = macros.stream()
                .filter(m -> m.getName().equals(selectedMacroName))
                .findFirst()
                .orElse(null);
            
            if (currentMacro != null) {
                macroDetailsPanel.setVisible(true);
        
                tblMacroSteps.setItems(currentMacro.getSteps());
                
                // Debug-Ausgabe für Makro-Schritte
                logger.info("Makro ausgewählt: " + currentMacro.getName() + " mit " + currentMacro.getSteps().size() + " Schritten");
                for (int i = 0; i < currentMacro.getSteps().size(); i++) {
                    MacroStep step = currentMacro.getSteps().get(i);
                    logger.info("Schritt " + (i+1) + ": " + step.getDescription() + " (enabled: " + step.isEnabled() + ")");
                }
                
                updateStatus("Makro ausgewählt: " + currentMacro.getName());
            }
        } else {
            currentMacro = null;
            macroDetailsPanel.setVisible(false);
            tblMacroSteps.setItems(null);
        }
    }
    
    private void addMacroStep() {
        if (currentMacro != null) {
            // Verwende die Makro-spezifischen Eingabefelder
            String searchText = txtMacroSearch.getText() != null ? txtMacroSearch.getText().trim() : "";
            String replaceText = txtMacroReplace.getText() != null ? txtMacroReplace.getText() : "";
            
            // Debug-Ausgabe für Leerzeichen-Problem
            System.out.println("DEBUG ADD STEP: ReplaceText length: " + replaceText.length() + 
                               " | Bytes: " + Arrays.toString(replaceText.getBytes()) +
                               " | Is empty: " + replaceText.isEmpty());
            
            if (searchText.isEmpty()) {
                updateStatus("Bitte Suchtext eingeben");
                return;
            }
            
            boolean useRegex = chkMacroRegex.isSelected();
            boolean caseSensitive = chkMacroCaseSensitive.isSelected();
            boolean wholeWord = chkMacroWholeWord.isSelected();
            
            String stepDescription = txtMacroStepDescription.getText();
            MacroStep step = new MacroStep(currentMacro.getSteps().size() + 1, 
                                         searchText, replaceText, stepDescription, useRegex, caseSensitive, wholeWord);
            currentMacro.addStep(step);
            
            // Felder leeren für nächsten Schritt
            txtMacroSearch.clear();
            txtMacroReplace.clear();
            txtMacroStepDescription.clear();
            chkMacroRegex.setSelected(false);
            chkMacroCaseSensitive.setSelected(false);
            chkMacroWholeWord.setSelected(false);
            
            saveMacros();
            updateStatus("Schritt zum Makro hinzugefügt: " + searchText);
        }
    }
    
    private void removeMacroStep() {
        if (currentMacro != null) {
            MacroStep selectedStep = tblMacroSteps.getSelectionModel().getSelectedItem();
            if (selectedStep != null) {
                currentMacro.removeStep(selectedStep);
                saveMacros();
                updateStatus("Schritt aus Makro entfernt");
            }
        }
    }
    
    private void moveMacroStepUp() {
        if (currentMacro != null) {
            MacroStep selectedStep = tblMacroSteps.getSelectionModel().getSelectedItem();
            if (selectedStep != null) {
                int currentIndex = currentMacro.getSteps().indexOf(selectedStep);
                currentMacro.moveStepUp(selectedStep);
                saveMacros();
                
                // Cursor zur neuen Position setzen
                Platform.runLater(() -> {
                    int newIndex = Math.max(0, currentIndex - 1);
                    tblMacroSteps.getSelectionModel().select(newIndex);
                    tblMacroSteps.scrollTo(newIndex);
                });
                
                updateStatus("Schritt nach oben verschoben");
            }
        }
    }
    
    private void moveMacroStepDown() {
        if (currentMacro != null) {
            MacroStep selectedStep = tblMacroSteps.getSelectionModel().getSelectedItem();
            if (selectedStep != null) {
                int currentIndex = currentMacro.getSteps().indexOf(selectedStep);
                currentMacro.moveStepDown(selectedStep);
                saveMacros();
                
                // Cursor zur neuen Position setzen
                Platform.runLater(() -> {
                    int newIndex = Math.min(currentMacro.getSteps().size() - 1, currentIndex + 1);
                    tblMacroSteps.getSelectionModel().select(newIndex);
                    tblMacroSteps.scrollTo(newIndex);
                });
                
                updateStatus("Schritt nach unten verschoben");
            }
        }
    }
    
    private void editMacroStep(MacroStep step) {
        if (step != null) {
            // Dialog zum Bearbeiten des Schritts
            Dialog<MacroStep> dialog = new Dialog<>();
            dialog.setTitle("Makro-Schritt bearbeiten");
            dialog.setHeaderText("Schritt " + step.getStepNumber() + " bearbeiten");
            dialog.setResizable(true);
            
            // Dialog-Buttons
            ButtonType saveButtonType = new ButtonType("Speichern", ButtonBar.ButtonData.OK_DONE);
            ButtonType deleteButtonType = new ButtonType("Löschen", ButtonBar.ButtonData.OTHER);
            ButtonType cancelButtonType = new ButtonType("Abbrechen", ButtonBar.ButtonData.CANCEL_CLOSE);
            dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, deleteButtonType, cancelButtonType);
            
            // Dialog-Inhalt
            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            grid.setPadding(new Insets(20, 150, 10, 10));
            
            TextField searchField = new TextField(step.getSearchText());
            searchField.setPromptText("Suchtext");
            searchField.setPrefWidth(300);
            
            TextField replaceField = new TextField(step.getReplaceText());
            replaceField.setPromptText("Ersetzungstext");
            replaceField.setPrefWidth(300);
            
            TextField descriptionField = new TextField(step.getDescription());
            descriptionField.setPromptText("Beschreibung (optional)");
            descriptionField.setPrefWidth(300);
            
            CheckBox regexCheckBox = new CheckBox("Regex");
            regexCheckBox.setSelected(step.isUseRegex());
            
            CheckBox caseCheckBox = new CheckBox("Case-Sensitive");
            caseCheckBox.setSelected(step.isCaseSensitive());
            
            CheckBox wordCheckBox = new CheckBox("Ganzes Wort");
            wordCheckBox.setSelected(step.isWholeWord());
            
            grid.add(new Label("Suchen:"), 0, 0);
            grid.add(searchField, 1, 0);
            grid.add(new Label("Ersetzen:"), 0, 1);
            grid.add(replaceField, 1, 1);
            grid.add(new Label("Beschreibung:"), 0, 2);
            grid.add(descriptionField, 1, 2);
            grid.add(new Label("Optionen:"), 0, 3);
            
            VBox optionsBox = new VBox(5);
            optionsBox.getChildren().addAll(regexCheckBox, caseCheckBox, wordCheckBox);
            grid.add(optionsBox, 1, 3);
            
            dialog.getDialogPane().setContent(grid);
            
            // Fokus auf Suchfeld setzen
            Platform.runLater(() -> searchField.requestFocus());
            
            // Ergebnis verarbeiten
            dialog.setResultConverter(dialogButton -> {
                if (dialogButton == saveButtonType) {
                    step.setSearchText(searchField.getText());
                    step.setReplaceText(replaceField.getText());
                    step.setDescription(descriptionField.getText());
                    step.setUseRegex(regexCheckBox.isSelected());
                    step.setCaseSensitive(caseCheckBox.isSelected());
                    step.setWholeWord(wordCheckBox.isSelected());
                    saveMacros();
                    updateStatus("Schritt " + step.getStepNumber() + " bearbeitet");
                    return step;
                } else if (dialogButton == deleteButtonType) {
                    // Schritt aus dem aktuellen Makro entfernen
                    if (currentMacro != null) {
                        currentMacro.removeStep(step);
                        saveMacros();
                        updateStatus("Schritt " + step.getStepNumber() + " gelöscht");
                    }
                    return null;
                }
                return null;
            });
            
            dialog.showAndWait();
        }
    }
    
    private void runCurrentMacro() {
        if (currentMacro != null && !currentMacro.getSteps().isEmpty()) {
            // Speichere Zustand für Undo
            saveStateForUndo();
            
            String content = codeArea.getText();
            
            // Nur aktivierte Schritte zählen
            List<MacroStep> enabledSteps = currentMacro.getSteps().stream()
                .filter(MacroStep::isEnabled)
                .collect(Collectors.toList());
            
            if (enabledSteps.isEmpty()) {
                updateStatus("Keine aktivierten Schritte im Makro");
                return;
            }
            
            int totalSteps = enabledSteps.size();
            int processedSteps = 0;
            
            // Alle Schritte zurücksetzen
            for (MacroStep step : currentMacro.getSteps()) {
                step.resetReplacementStats();
            }
            
            updateStatus("Führe Makro aus: " + currentMacro.getName() + " (" + totalSteps + " aktivierte Schritte)");
            
            for (MacroStep step : enabledSteps) {
                processedSteps++;
                updateStatus("Schritt " + processedSteps + "/" + totalSteps + ": " + step.getSearchText());
                
                // Status auf "Läuft..." setzen
                step.setRunning();
                
                // Debug-Ausgabe: Such- und Ersetzungstext mit sichtbaren Leerzeichen
                String debugSearch = step.getSearchText() == null ? "<null>" : step.getSearchText().replace(" ", "␣");
                String debugReplace = step.getReplaceText() == null ? "<null>" : step.getReplaceText().replace(" ", "␣");
                System.out.println("DEBUG MACRO: Suche: '" + debugSearch + "' | Ersetze: '" + debugReplace + "'");
                
                // Zusätzliche Debug-Info für Leerzeichen-Problem
                if (step.getReplaceText() != null) {
                    System.out.println("DEBUG REPLACE LENGTH: " + step.getReplaceText().length() + 
                               " | BYTES: " + Arrays.toString(step.getReplaceText().getBytes()) +
                               " | IS_EMPTY: " + step.getReplaceText().isEmpty() +
                               " | TRIM_LENGTH: " + step.getReplaceText().trim().length());
                }
                
                try {
                    // Erstelle Pattern basierend auf Schritt-Optionen
                    Pattern pattern = createPatternFromStep(step);
                    if (pattern != null) {
                        String replacement = step.getReplaceText() != null ? step.getReplaceText() : "";
                        // Unescape \n in der Ersetzung
                        replacement = replacement.replace("\\n", "\n");
                        
                        // Zähle Ersetzungen
                        Matcher matcher = pattern.matcher(content);
                        int replacements = 0;
                        StringBuffer sb = new StringBuffer();
                        while (matcher.find()) {
                            replacements++;
                            matcher.appendReplacement(sb, replacement);
                        }
                        matcher.appendTail(sb);
                        content = sb.toString();
                        
                        // Ersetzungsstatistik aktualisieren
                        step.addReplacements(replacements);
                        if (replacements > 0) {
                            step.setCompleted();
                        } else {
                            step.setCompleted(); // Auch wenn keine Ersetzungen, war es erfolgreich
                        }
                    } else {
                        step.setError("Ungültiges Pattern");
                    }
                } catch (Exception e) {
                    step.setError(e.getMessage());
                    updateStatusError("Fehler in Schritt " + processedSteps + ": " + e.getMessage());
                }
            }
            
            codeArea.replaceText(content);
            updateStatus("Makro erfolgreich ausgeführt: " + currentMacro.getName());
        } else {
            updateStatus("Kein Makro ausgewählt oder Makro ist leer");
        }
    }
    
    private Pattern createPatternFromStep(MacroStep step) {
        try {
            String searchText = step.getSearchText();
            if (searchText == null || searchText.trim().isEmpty()) {
                return null;
            }
            
            int flags = Pattern.MULTILINE;
            if (!step.isCaseSensitive()) {
                flags |= Pattern.CASE_INSENSITIVE;
            }
            
            if (step.isUseRegex()) {
                // Für Regex: \1 muss als $1 behandelt werden
                String processedSearch = searchText.replace("\\1", "$1").replace("\\2", "$2").replace("\\3", "$3");
                logger.debug("DEBUG: Original: '" + searchText + "' -> Java: '" + processedSearch + "'");
                return Pattern.compile(processedSearch, flags);
            } else {
                // Escape special regex characters for literal search
                String escapedSearch = Pattern.quote(searchText);
                if (step.isWholeWord()) {
                    escapedSearch = "\\b" + escapedSearch + "\\b";
                }
                return Pattern.compile(escapedSearch, flags);
            }
        } catch (Exception e) {
            updateStatus("Fehler beim Erstellen des Patterns: " + e.getMessage());
            return null;
        }
    }
    
    private void loadMacros() {
        // EINFACHE PERSISTIERUNG - Funktioniert garantiert
        logger.info("Lade Makros - EINFACHES FORMAT");
        
        String savedMacros = preferences.get("savedMacros", "");
        
        // Wenn alte Daten vorhanden sind oder alle CheckBoxen deaktiviert sind, lösche sie
        if (savedMacros.contains("|||") || savedMacros.contains("<<<MACRO>>>") || savedMacros.contains("ENABLED:0")) {
            logger.info("Alte Makro-Daten oder deaktivierte CheckBoxen gefunden - lösche alle Makros");
            try {
                preferences.remove("savedMacros");
                preferences.flush();
            } catch (Exception e) {
                logger.error("Fehler beim Löschen der Makro-Daten", e);
            }
            savedMacros = "";
        }
        
        if (savedMacros.isEmpty()) {
            logger.info("Keine gespeicherten Makros - erstelle Beispiel-Makros");
            createExampleMacros();
            updateMacroList();
            return;
        }
        
        // Lade Makros im neuen Format
        macros.clear();
        String[] lines = savedMacros.split("\n");
        
        Macro currentMacro = null;
        MacroStep currentStep = null;
        
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            
            if (line.startsWith("MACRO:")) {
                String macroName = line.substring(6);
                currentMacro = new Macro(macroName, "");
                macros.add(currentMacro);
                currentStep = null;
            } else if (line.startsWith("DESC:")) {
                if (currentMacro != null) {
                    currentMacro.setDescription(line.substring(5));
                }
            } else if (line.startsWith("STEP:")) {
                int stepNumber = Integer.parseInt(line.substring(5));
                currentStep = new MacroStep(stepNumber, "", "", "", false, false, false);
                if (currentMacro != null) {
                    currentMacro.addStep(currentStep);
                }
            } else if (line.startsWith("SEARCH:")) {
                if (currentStep != null) {
                    currentStep.setSearchText(line.substring(7));
                }
            } else if (line.startsWith("REPLACE:")) {
                // Alte Format - für Kompatibilität
                if (currentStep != null) {
                    String replaceText = line.substring(8);
                    System.out.println("DEBUG LOAD (OLD): ReplaceText length: " + replaceText.length() + 
                                       " | Bytes: " + Arrays.toString(replaceText.getBytes()) +
                                       " | Is empty: " + replaceText.isEmpty());
                    currentStep.setReplaceText(replaceText);
                }
            } else if (line.startsWith("REPLACE_B64:")) {
                // Neues Base64-Format
                if (currentStep != null) {
                    try {
                        String encodedText = line.substring(12);
                        String replaceText = new String(java.util.Base64.getDecoder().decode(encodedText), "UTF-8");
                        System.out.println("DEBUG LOAD (B64): ReplaceText length: " + replaceText.length() + 
                                           " | Bytes: " + Arrays.toString(replaceText.getBytes()) +
                                           " | Is empty: " + replaceText.isEmpty());
                        currentStep.setReplaceText(replaceText);
                    } catch (Exception e) {
                        System.err.println("Fehler beim Dekodieren des Ersetzungstexts: " + e.getMessage());
                        currentStep.setReplaceText("");
                    }
                }
            } else if (line.startsWith("REGEX:")) {
                if (currentStep != null) {
                    currentStep.setUseRegex("1".equals(line.substring(6)));
                }
            } else if (line.startsWith("CASE:")) {
                if (currentStep != null) {
                    currentStep.setCaseSensitive("1".equals(line.substring(5)));
                }
            } else if (line.startsWith("WORD:")) {
                if (currentStep != null) {
                    currentStep.setWholeWord("1".equals(line.substring(5)));
                }
            } else if (line.startsWith("ENABLED:")) {
                if (currentStep != null) {
                    currentStep.setEnabled("1".equals(line.substring(8)));
                }
            } else if (line.startsWith("STEPDESC:")) {
                if (currentStep != null) {
                    currentStep.setDescription(line.substring(9));
                }
            } else if (line.startsWith("REPLACECOUNT:")) {
                if (currentStep != null) {
                    try {
                        int count = Integer.parseInt(line.substring(13));
                        currentStep.setReplacementCount(count);
                    } catch (NumberFormatException e) {
                        currentStep.setReplacementCount(0);
                    }
                }
            } else if (line.equals("ENDMACRO")) {
                currentMacro = null;
                currentStep = null;
            }
        }
        
        logger.info("Makros geladen: " + macros.size() + " Makros");
        updateMacroList();
    }
    
    private void saveMacros() {
        // EINFACHE PERSISTIERUNG - Funktioniert garantiert
        try {
            StringBuilder sb = new StringBuilder();
            
            for (Macro macro : macros) {
                sb.append("MACRO:").append(macro.getName()).append("\n");
                sb.append("DESC:").append(macro.getDescription() != null ? macro.getDescription() : "").append("\n");
                
                for (MacroStep step : macro.getSteps()) {
                    String replaceText = step.getReplaceText() != null ? step.getReplaceText() : "";
                    System.out.println("DEBUG SAVE: ReplaceText length: " + replaceText.length() + 
                                       " | Bytes: " + Arrays.toString(replaceText.getBytes()) +
                                       " | Is empty: " + replaceText.isEmpty());
                    
                    // Base64-kodiere den Ersetzungstext um Leerzeichen zu erhalten
                    String encodedReplaceText = java.util.Base64.getEncoder().encodeToString(replaceText.getBytes("UTF-8"));
                    
                    sb.append("STEP:").append(step.getStepNumber()).append("\n");
                    sb.append("SEARCH:").append(step.getSearchText() != null ? step.getSearchText() : "").append("\n");
                    sb.append("REPLACE_B64:").append(encodedReplaceText).append("\n");
                    sb.append("REGEX:").append(step.isUseRegex() ? "1" : "0").append("\n");
                    sb.append("CASE:").append(step.isCaseSensitive() ? "1" : "0").append("\n");
                    sb.append("WORD:").append(step.isWholeWord() ? "1" : "0").append("\n");
                    sb.append("ENABLED:").append(step.isEnabled() ? "1" : "0").append("\n");
                    sb.append("STEPDESC:").append(step.getDescription() != null ? step.getDescription() : "").append("\n");
                    sb.append("REPLACECOUNT:").append(step.getReplacementCount()).append("\n");
                }
                sb.append("ENDMACRO\n");
            }
            
            String macroData = sb.toString();
            preferences.put("savedMacros", macroData);
            preferences.flush();
            logger.info("Makros gespeichert: " + macros.size() + " Makros");
        } catch (Exception e) {
            logger.error("Fehler beim Speichern der Makros", e);
        }
    }
    
    // ALTE METHODEN GELÖSCHT - Waren kaputt
    
    private void createExampleMacros() {
        // Standard-Makro: Text-Bereinigung
        Macro textCleanupMacro = new Macro("Text-Bereinigung", "Professionelle Textbereinigung mit 12 Schritten");
        textCleanupMacro.addStep(new MacroStep(1, "[ ]{2,}", " ", "Mehrfache Leerzeichen reduzieren", true, false, false));
        textCleanupMacro.addStep(new MacroStep(2, "\\n{2,}", "\\n", "Mehrfache Leerzeilen reduzieren", true, false, false));
        textCleanupMacro.addStep(new MacroStep(3, "„", "\"", "Gerade Anführungszeichen öffnen", false, false, false));
        textCleanupMacro.addStep(new MacroStep(4, "“", "\"", "Gerade Anführungszeichen schließen", false, false, false));
        textCleanupMacro.addStep(new MacroStep(5, ",\"", "\",", "Komma vor Anführungszeichen I", false, false, false));
        textCleanupMacro.addStep(new MacroStep(6, "'(.*?)'", "›$1‹", "Einfache Anführungszeichen Französisch", true, false, false));
        textCleanupMacro.addStep(new MacroStep(7, "\"(.*?)\"", "»$1«", "Anführungszeichen Französisch", true, false, false));
        textCleanupMacro.addStep(new MacroStep(8, "...", "…", "Auslassungszeichen", false, false, false));
        textCleanupMacro.addStep(new MacroStep(9, "([A-Za-zÄÖÜäöüß])…", "$1 ...", "Buchstabe direkt an Auslassungszeichen", true, false, false));
        textCleanupMacro.addStep(new MacroStep(10, "(?<=…)(?=\\p{L})", " ", "Buchstabe direkt nach Auslassungszeichen", true, false, false));
        textCleanupMacro.addStep(new MacroStep(11, "--", "—", "Gedankenstrich", false, false, false));
        textCleanupMacro.addStep(new MacroStep(12, ",\"«", "«,", "Komma vor Anführungszeichen", false, false, false));
        textCleanupMacro.addStep(new MacroStep(13, "'(.*?)' ", "›$1‹", "Einfache Anführungszeichen Französisch", true, false, false));
        macros.add(textCleanupMacro);
        
        // Neues Makro: Französische zu deutsche Anführungszeichen
        Macro frenchToGermanQuotes = new Macro("Französische → Deutsche Anführungszeichen", "Konvertiert französische zu deutschen Anführungszeichen");
        frenchToGermanQuotes.addStep(new MacroStep(1, "»(.*?)«", "\"$1\"", "Französische zu deutsche Anführungszeichen", true, false, false));
        frenchToGermanQuotes.addStep(new MacroStep(2, "›(.*?)‹", "'$1'", "Französische zu deutsche einfache Anführungszeichen", true, false, false));
        macros.add(frenchToGermanQuotes);
        
        // Neues Makro: Deutsche zu französische Anführungszeichen
        Macro germanToFrenchQuotes = new Macro("Deutsche → Französische Anführungszeichen", "Konvertiert deutsche zu französischen Anführungszeichen");
        germanToFrenchQuotes.addStep(new MacroStep(1, "\"(.*?)\"", "»$1«", "Deutsche zu französische Anführungszeichen", true, false, false));
        germanToFrenchQuotes.addStep(new MacroStep(2, "'(.*?)'", "›$1‹", "Deutsche zu französische einfache Anführungszeichen", true, false, false));
        macros.add(germanToFrenchQuotes);
        
        // Neues Makro: Apostrophe korrigieren
        Macro apostropheCorrection = new Macro("Apostrophe korrigieren", "Korrigiert verschiedene Apostrophe-Formen");
        apostropheCorrection.addStep(new MacroStep(1, "([A-Za-zÄÖÜäöüß])'([A-Za-zÄÖÜäöüß])", "$1'$2", "Apostrophe zwischen Buchstaben korrigieren", true, false, false));
        apostropheCorrection.addStep(new MacroStep(2, "([A-Za-zÄÖÜäöüß])`([A-Za-zÄÖÜäöüß])", "$1'$2", "Grave-Akzent zu Apostrophe", true, false, false));
        apostropheCorrection.addStep(new MacroStep(3, "([A-Za-zÄÖÜäöüß])´([A-Za-zÄÖÜäöüß])", "$1'$2", "Akut-Akzent zu Apostrophe", true, false, false));
        apostropheCorrection.addStep(new MacroStep(4, "([A-Za-zÄÖÜäöüß])'([A-Za-zÄÖÜäöüß])", "$1'$2", "Typografisches Apostrophe korrigieren", true, false, false));
        macros.add(apostropheCorrection);
        
        updateMacroList();
    }
    
    private void updateMacroList() {
        List<String> macroNames = macros.stream()
            .map(Macro::getName)
            .collect(Collectors.toList());
        cmbMacroList.getItems().clear();
        cmbMacroList.getItems().addAll(macroNames);
    }
    
    private void saveMacroToCSV() {
        if (currentMacro == null) {
            updateStatus("Kein Makro ausgewählt");
            return;
        }
        
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Makro als CSV speichern");
        fileChooser.setInitialFileName(currentMacro.getName() + ".csv");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("CSV-Dateien", "*.csv")
        );
        
        // Letztes Verzeichnis verwenden
        String lastDirectory = preferences.get("lastSaveDirectory", null);
        if (lastDirectory != null) {
            fileChooser.setInitialDirectory(new File(lastDirectory));
        }
        
        File file = fileChooser.showSaveDialog(stage);
        if (file == null) return;
        
        try {
            // Verzeichnis speichern
            preferences.put("lastSaveDirectory", file.getParent());
            
            // CSV-Datei erstellen
            StringBuilder csv = new StringBuilder();
            csv.append("Schritt,Aktiv,Beschreibung,Suchen,Ersetzen,Regex,Case,Word\n");
            
            for (MacroStep step : currentMacro.getSteps()) {
                csv.append(String.format("%d,%s,\"%s\",\"%s\",\"%s\",%s,%s,%s\n",
                    step.getStepNumber(),
                    step.isEnabled() ? "Ja" : "Nein",
                    step.getDescription().replace("\"", "\"\""),
                    step.getSearchText().replace("\"", "\"\""),
                    step.getReplaceText().replace("\"", "\"\""),
                    step.isUseRegex() ? "Ja" : "Nein",
                    step.isCaseSensitive() ? "Ja" : "Nein",
                    step.isWholeWord() ? "Ja" : "Nein"
                ));
            }
            
            // Datei schreiben
            Files.write(file.toPath(), csv.toString().getBytes("UTF-8"));
            
            updateStatus("Makro '" + currentMacro.getName() + "' als CSV gespeichert: " + file.getName());
            logger.info("Makro als CSV gespeichert: " + file.getAbsolutePath());
            
        } catch (Exception e) {
            logger.error("Fehler beim Speichern der CSV-Datei", e);
            updateStatus("Fehler beim Speichern: " + e.getMessage());
        }
    }
    
    private void changeFontSize(int delta) {
        try {
            String currentText = cmbFontSize.getValue();
            if (currentText == null || currentText.isEmpty()) {
                currentText = "12";
            }
            int currentSize = Integer.parseInt(currentText);
            int newSize = Math.max(8, Math.min(72, currentSize + delta));
            
            // Font-Size anwenden
            applyFontSize(newSize);
            
            // Event-Handler temporär entfernen
            EventHandler<ActionEvent> originalHandler = cmbFontSize.getOnAction();
            cmbFontSize.setOnAction(null);
            
            // ComboBox aktualisieren
            cmbFontSize.setValue(String.valueOf(newSize));
            
            // Event-Handler wieder hinzufügen
            cmbFontSize.setOnAction(originalHandler);
            
        } catch (NumberFormatException e) {
            logger.warn("Ungültige Schriftgröße: {}", cmbFontSize.getValue());
        }
    }
    
    private void changeFontSizeFromComboBox() {
        try {
            String sizeText = cmbFontSize.getValue();
            if (sizeText != null && !sizeText.isEmpty()) {
                int size = Integer.parseInt(sizeText);
                applyFontSize(size);
                
                // Font-Size in Preferences speichern
                preferences.putInt("fontSize", size);
                logger.info("Font-Size geändert und gespeichert: {}", size);
            }
        } catch (NumberFormatException e) {
            logger.warn("Ungültige Schriftgröße: {}", cmbFontSize.getValue());
        }
    }
    
    private void applyFontSize(int size) {
        if (codeArea != null) {
            // Theme neu anwenden mit neuer Schriftgröße
            applyTheme(currentThemeIndex);
            
            // Speichere in Preferences
            preferences.putInt("fontSize", size);
        }
    }
    
    // ===== PERSISTIERUNG VON TOOLBAR UND FENSTER-EIGENSCHAFTEN =====
    
    private void loadWindowProperties() {
        if (stage == null) return;
        
        // Fenster-Größe und Position laden
        double width = preferences.getDouble("window_width", 1200.0);
        double height = preferences.getDouble("window_height", 800.0);
        double x = preferences.getDouble("window_x", -1.0);
        double y = preferences.getDouble("window_y", -1.0);
        
        // NEU: Validierung der Fenster-Größe
        // Minimale und maximale Größen prüfen
        double minWidth = 800.0;
        double minHeight = 600.0;
        double maxWidth = 3000.0;
        double maxHeight = 2000.0;
        
        // Größe validieren und korrigieren
        if (width < minWidth || width > maxWidth || Double.isNaN(width) || Double.isInfinite(width)) {
            logger.warn("Ungültige Fenster-Breite: {} - verwende Standard: {}", width, minWidth);
            width = minWidth;
        }
        if (height < minHeight || height > maxHeight || Double.isNaN(height) || Double.isInfinite(height)) {
            logger.warn("Ungültige Fenster-Höhe: {} - verwende Standard: {}", height, minHeight);
            height = minHeight;
        }
        
        // Fenster-Größe setzen
        stage.setWidth(width);
        stage.setHeight(height);
        
        // NEU: Validierung der Fenster-Position
        // Prüfe, ob Position gültig ist und auf dem Bildschirm liegt
        if (x >= 0 && y >= 0 && !Double.isNaN(x) && !Double.isNaN(y) && 
            !Double.isInfinite(x) && !Double.isInfinite(y)) {
            
            // Grobe Prüfung: Position sollte nicht zu weit außerhalb des Bildschirms sein
            if (x < -1000 || y < -1000 || x > 5000 || y > 5000) {
                logger.warn("Fenster-Position außerhalb des Bildschirms: x={}, y={} - verwende zentriert", x, y);
                stage.centerOnScreen();
            } else {
                stage.setX(x);
                stage.setY(y);
            }
        } else {
            logger.info("Keine gültige Fenster-Position gefunden - zentriere Fenster");
            stage.centerOnScreen();
        }
        
        // NEU: Divider-Position nach Fenster-Größe setzen
        Platform.runLater(() -> {
            if (mainSplitPane != null) {
                // Prüfe, ob Chapter-Editor sichtbar ist
                if (chapterEditorVisible) {
                    double savedPosition = preferences.getDouble("chapter_editor_divider_position", 0.8);
                    
                    // NEU: Validierung der Divider-Position
                    if (savedPosition < 0.0 || savedPosition > 1.0 || 
                        Double.isNaN(savedPosition) || Double.isInfinite(savedPosition)) {
                        logger.warn("Ungültige Divider-Position: {} - verwende Standard: 0.8", savedPosition);
                        savedPosition = 0.8;
                    }
                    
                    mainSplitPane.setDividerPositions(savedPosition);
                } else {
                    mainSplitPane.setDividerPositions(0.0);
                }
            }
        });
        
        logger.info("Fenster-Eigenschaften geladen: Größe={}x{}, Position=({}, {})", width, height, x, y);
    }
    
    private void loadToolbarSettings() {
        // Font-Size laden und anwenden
        int fontSize = preferences.getInt("fontSize", 12);
        cmbFontSize.setValue(String.valueOf(fontSize));
        
        // Font-Size sofort anwenden
        applyFontSize(fontSize);
        
        // Theme laden - Priorität: main_window_theme > editor_theme > Standard (0)
        int mainWindowTheme = preferences.getInt("main_window_theme", -1);
        int editorTheme = preferences.getInt("editor_theme", -1);
        
        logger.info("Preferences gelesen: main_window_theme={}, editor_theme={}", mainWindowTheme, editorTheme);
        
        if (mainWindowTheme >= 0) {
            currentThemeIndex = mainWindowTheme;
            logger.info("Theme aus main_window_theme geladen: {}", currentThemeIndex);
        } else if (editorTheme >= 0) {
            currentThemeIndex = editorTheme;
            logger.info("Theme aus editor_theme geladen: {}", currentThemeIndex);
        } else {
            currentThemeIndex = 0; // Standard weißes Theme
            logger.info("Kein Theme in Preferences gefunden, verwende Standard: {}", currentThemeIndex);
        }
        
        // Theme anwenden
        applyTheme(currentThemeIndex);
        
        // Theme-Button Tooltip aktualisieren
        updateThemeButtonTooltip();
        
        logger.info("Toolbar-Einstellungen geladen: Font-Size={}, Theme={} (final)", fontSize, currentThemeIndex);
    }
    
    private void updateThemeButtonTooltip() {
        String[] themeNames = {"Weiß", "Schwarz", "Pastell", "Blau", "Grün", "Lila"};
        btnThemeToggle.setTooltip(new Tooltip("Theme: " + themeNames[currentThemeIndex] + " (Klick für nächstes)"));
    }
    
    private void setupFontSizeComboBox() {
        // Lade gespeicherte Schriftgröße
        int savedSize = preferences.getInt("fontSize", 12);
        cmbFontSize.setValue(String.valueOf(savedSize));
        
        // Füge Standard-Größen hinzu
        ObservableList<String> sizes = FXCollections.observableArrayList();
        for (int i = 8; i <= 72; i += 2) {
            sizes.add(String.valueOf(i));
        }
        cmbFontSize.setItems(sizes);
    }
    
    private void formatTextBold() {
        formatTextAtCursor("**", "**", "<b>", "</b>");
    }
    
    private void formatTextItalic() {
        formatTextAtCursor("*", "*", "<i>", "</i>");
    }
    
    private void toggleTheme() {
        currentThemeIndex = (currentThemeIndex + 1) % THEMES.length;
        applyTheme(currentThemeIndex);
        
        // Update Button-Tooltip
        updateThemeButtonTooltip();
        
        String[] themeNames = {"Weiß", "Schwarz", "Pastell", "Blau", "Grün", "Lila"};
        updateStatus("Theme gewechselt: " + themeNames[currentThemeIndex]);
        
        // WICHTIG: Theme in Preferences speichern
        preferences.putInt("editor_theme", currentThemeIndex);
        preferences.putInt("main_window_theme", currentThemeIndex);
        
        logger.info("Theme gewechselt und gespeichert: {} ({})", currentThemeIndex, themeNames[currentThemeIndex]);
    }
    
    private void applyTheme(int themeIndex) {
        if (codeArea == null) return;
        
        String[] theme = THEMES[themeIndex];
        String backgroundColor = theme[0];
        String textColor = theme[1];
        String selectionColor = theme[2];
        String caretColor = theme[3];
        
        // Aktuelle Schriftgröße sicher ermitteln
        int fontSize = 12; // Standard
        try {
            String fontSizeStr = cmbFontSize.getValue();
            if (fontSizeStr != null && !fontSizeStr.trim().isEmpty()) {
                fontSize = Integer.parseInt(fontSizeStr);
            } else {
                // Fallback: Gespeicherte Schriftgröße aus Preferences
                fontSize = preferences.getInt("fontSize", 12);
            }
        } catch (NumberFormatException e) {
            fontSize = preferences.getInt("fontSize", 12);
        }
        
        // RichTextFX CodeArea Theme anwenden - spezielle CSS-Eigenschaften
        String cssStyle = String.format(
            "-rtfx-background-color: %s;" +
            "-fx-highlight-fill: %s;" +
            "-fx-highlight-text-fill: %s;" +
            "-fx-caret-color: %s !important;" +
            "-fx-font-family: 'Consolas', 'Monaco', monospace;" +
            "-fx-font-size: %dpx;" +
            "-fx-background-color: %s;",
            backgroundColor, selectionColor, textColor, caretColor, fontSize, backgroundColor
        );
        
        codeArea.setStyle(cssStyle);
        
        // CSS-Klassen für Theme-spezifische Cursor-Farben
        codeArea.getStyleClass().removeAll("theme-dark", "theme-light", "weiss-theme", "pastell-theme");
        if (themeIndex == 0) { // Weiß-Theme
            codeArea.getStyleClass().add("weiss-theme");
        } else if (themeIndex == 1 || themeIndex >= 3) { // Dunkle Themes: Schwarz (1), Blau (3), Grün (4), Lila (5)
            codeArea.getStyleClass().add("theme-dark");
        } else if (themeIndex == 2) { // Pastell-Theme
            codeArea.getStyleClass().add("pastell-theme");
        }
        
        // Dark Theme für alle UI-Elemente anwenden
        Platform.runLater(() -> {
            // Root-Container (Hauptfenster) - WICHTIG: Das ist der Hauptcontainer!
            if (stage != null && stage.getScene() != null) {
                Node root = stage.getScene().getRoot();
                root.getStyleClass().removeAll("theme-dark", "theme-light", "weiss-theme", "pastell-theme");
                
                // Direkte inline Styles für Pastell-Theme
                if (themeIndex == 2) { // Pastell-Theme
                    root.setStyle("-fx-background-color: #f3e5f5; -fx-text-fill: #000000;");
                    mainContainer.setStyle("-fx-background-color: #f3e5f5; -fx-text-fill: #000000;");
                    logger.info("Pastell-Theme direkt angewendet (Editor)");
                } else {
                    root.setStyle(""); // Style zurücksetzen
                    mainContainer.setStyle(""); // Style zurücksetzen
                    
                    if (themeIndex == 0) { // Weiß-Theme
                        root.getStyleClass().add("weiss-theme");
                    } else if (themeIndex == 1 || themeIndex >= 3) { // Dunkle Themes: Schwarz (1), Blau (3), Grün (4), Lila (5)
                        root.getStyleClass().add("theme-dark");
                    }
                }
            }
            
            // Alle direkten UI-Elemente explizit anwenden
            applyThemeToNode(mainContainer, themeIndex);
            applyThemeToNode(searchReplacePanel, themeIndex);
            applyThemeToNode(macroPanel, themeIndex);
            applyThemeToNode(textAreaContainer, themeIndex);
            applyThemeToNode(searchReplaceContainer, themeIndex);
            
            // Alle Buttons
            applyThemeToNode(btnFind, themeIndex);
            applyThemeToNode(btnReplace, themeIndex);
            applyThemeToNode(btnReplaceAll, themeIndex);
            applyThemeToNode(btnSaveSearch, themeIndex);
            applyThemeToNode(btnSaveReplace, themeIndex);
            applyThemeToNode(btnDeleteSearch, themeIndex);
            applyThemeToNode(btnDeleteReplace, themeIndex);
            applyThemeToNode(btnFindNext, themeIndex);
            applyThemeToNode(btnFindPrevious, themeIndex);
            applyThemeToNode(btnSave, themeIndex);
            applyThemeToNode(btnSaveAs, themeIndex);
            applyThemeToNode(btnExportRTF, themeIndex);
            applyThemeToNode(btnExportDOCX, themeIndex);
            applyThemeToNode(btnOpen, themeIndex);
            applyThemeToNode(btnNew, themeIndex);
            applyThemeToNode(btnToggleSearch, themeIndex);
            applyThemeToNode(btnToggleMacro, themeIndex);
            applyThemeToNode(btnTextAnalysis, themeIndex);
            applyThemeToNode(btnRegexHelp, themeIndex);
            applyThemeToNode(btnKIAssistant, themeIndex);
            applyThemeToNode(btnIncreaseFont, themeIndex);
            applyThemeToNode(btnDecreaseFont, themeIndex);
            applyThemeToNode(btnBold, themeIndex);
            applyThemeToNode(btnItalic, themeIndex);
            applyThemeToNode(btnThemeToggle, themeIndex);
            applyThemeToNode(btnMacroRegexHelp, themeIndex);
            
            // Alle ComboBoxes
            applyThemeToNode(cmbSearchHistory, themeIndex);
            applyThemeToNode(cmbReplaceHistory, themeIndex);
            applyThemeToNode(cmbFontSize, themeIndex);
            
            // Alle CheckBoxes
            applyThemeToNode(chkRegexSearch, themeIndex);
            applyThemeToNode(chkCaseSensitive, themeIndex);
            applyThemeToNode(chkWholeWord, themeIndex);
            
            // Alle Labels
            applyThemeToNode(lblStatus, themeIndex);
            applyThemeToNode(lblMatchCount, themeIndex);
            
            // Chapter-Editor Elemente
            applyThemeToNode(chapterEditorPanel, themeIndex);
            applyThemeToNode(chapterEditorArea, themeIndex);
            applyThemeToNode(btnChapterEditor, themeIndex);
            applyThemeToNode(btnSaveChapter, themeIndex);
            applyThemeToNode(mainSplitPane, themeIndex);
            
            // Text Analysis Panel
            if (textAnalysisStage != null && textAnalysisStage.getScene() != null) {
                textAnalysisStage.getScene().getRoot().getStyleClass().removeAll("theme-dark", "theme-light");
                if (themeIndex == 1 || themeIndex >= 3) { // Dunkle Themes: Schwarz (1), Blau (3), Grün (4), Lila (5)
                    textAnalysisStage.getScene().getRoot().getStyleClass().add("theme-dark");
                } else if (themeIndex == 2) { // Pastell - Helles Theme
                    textAnalysisStage.getScene().getRoot().getStyleClass().add("pastell-theme");
                }
                // Weiß-Theme (Index 0) - Keine CSS-Klasse (verwendet .root)
            }
            
            // Macro Window
            if (macroStage != null && macroStage.getScene() != null) {
                applyThemeToNode(macroStage.getScene().getRoot(), themeIndex);
                
                // Makro-Panel direkt aktualisieren
                Node macroPanel = macroStage.getScene().lookup(".macro-panel");
                if (macroPanel != null) {
                    applyThemeToNode(macroPanel, themeIndex);
                }
            }
            
            // Ollama Window
            if (ollamaWindow != null && ollamaWindow.isShowing()) {
                ollamaWindow.setTheme(themeIndex);
            }
        });
        
        // Textfarbe über CSS-Stylesheet anwenden (RichTextFX-spezifisch)
        Platform.runLater(() -> {
            // CSS-Stylesheet für Textfarbe erstellen
            String textColorCSS = String.format(
                ".text { -fx-text-fill: %s; -fx-fill: %s; } " +
                ".content { -fx-text-fill: %s; -fx-fill: %s; } " +
                ".paragraph-box { -fx-text-fill: %s; -fx-fill: %s; } " +
                ".paragraph-text { -fx-text-fill: %s; -fx-fill: %s; }",
                textColor, textColor, textColor, textColor, textColor, textColor, textColor, textColor
            );
            
            // Stylesheet zur CodeArea hinzufügen
            codeArea.getStylesheets().clear();
            codeArea.getStylesheets().add("data:text/css," + textColorCSS);
            
            // Zeilennummern mit benutzerdefinierten Farben erstellen
            // Zeilennummern komplett entfernt - kein grauer Bereich mehr
            codeArea.setParagraphGraphicFactory(null);
        });
        
        // VirtualizedScrollPane Theme anpassen
        if (textAreaContainer.getChildren().size() > 0) {
            Node scrollPane = textAreaContainer.getChildren().get(0);
            if (scrollPane instanceof VirtualizedScrollPane) {
                scrollPane.setStyle(String.format(
                    "-fx-background-color: %s;" +
                    "-fx-control-inner-background: %s;",
                    backgroundColor, backgroundColor
                ));
            }
        }
        
        // Theme in Preferences speichern
        preferences.putInt("editor_theme", themeIndex);
        
        // WICHTIG: CSS-Refresh erzwingen
        if (stage != null && stage.getScene() != null) {
            stage.getScene().getStylesheets().clear();
            // CSS mit ResourceManager laden
            String cssPath = ResourceManager.getCssResource("css/editor.css");
            if (cssPath != null) {
                stage.getScene().getStylesheets().add(cssPath);
            }
            
            // Zusätzlich: Root-Container explizit das Theme geben
            Node root = stage.getScene().getRoot();
            root.getStyleClass().removeAll("theme-dark", "theme-light", "blau-theme", "gruen-theme", "lila-theme");
            if (themeIndex == 1 || themeIndex >= 3) { // Dunkle Themes: Schwarz (1), Blau (3), Grün (4), Lila (5)
                root.getStyleClass().add("theme-dark");
                // Spezifische Theme-Klassen für dunkle Themes
                if (themeIndex == 3) { // Blau
                    root.getStyleClass().add("blau-theme");
                } else if (themeIndex == 4) { // Grün
                    root.getStyleClass().add("gruen-theme");
                } else if (themeIndex == 5) { // Lila
                    root.getStyleClass().add("lila-theme");
                }
            } else if (themeIndex == 2) { // Pastell - Helles Theme
                root.getStyleClass().add("pastell-theme");
            }
            // Weiß-Theme (Index 0) - Keine CSS-Klasse (verwendet .root)
            
            // Zusätzlicher CSS-Refresh nach einer kurzen Verzögerung
            Platform.runLater(() -> {
                stage.getScene().getStylesheets().clear();
                // CSS mit ResourceManager laden
                String cssPathInner = ResourceManager.getCssResource("css/editor.css");
                if (cssPathInner != null) {
                    stage.getScene().getStylesheets().add(cssPathInner);
                }
                
                // Force layout refresh
                if (root instanceof Parent) {
                    ((Parent) root).requestLayout();
                }
                if (stage.getScene().getRoot() instanceof Parent) {
                    ((Parent) stage.getScene().getRoot()).requestLayout();
                }
            });
        }
        
        // Cursor-Farbe direkt über Node-Lookup setzen (funktioniert!)
        Platform.runLater(() -> {
            Node caret = codeArea.lookup(".caret");
            if (caret != null) {
                // Cursor-Farbe konträr zur Textfarbe setzen (immer sichtbar!)
                String cursorColor;
                if (textColor.equals("#ffffff")) { // Weißer Text
                    cursorColor = "#ff0000"; // Roter Cursor (immer sichtbar)
                } else { // Dunkler Text
                    cursorColor = "#00ff00"; // Grüner Cursor (immer sichtbar)
                }
                // Cursor-Style mit besserer Sichtbarkeit
                caret.setStyle("-fx-stroke: " + cursorColor + "; -fx-fill: " + cursorColor + "; -fx-stroke-width: 2;");
                logger.info("Cursor-Farbe gesetzt: {} für Textfarbe: {}", cursorColor, textColor);
            } else {
                logger.warn("Caret-Element nicht gefunden!");
            }
            
            // Zusätzlich: CodeArea-Padding anpassen für bessere Cursor-Sichtbarkeit
            codeArea.setStyle(codeArea.getStyle() + "; -fx-padding: 5px;");
        });
        
        // Debug-Ausgabe
        logger.info("Theme angewendet: Index={}, Hintergrund={}, Text={}", themeIndex, backgroundColor, textColor);
    }
    
    /**
     * Wendet das Theme auf ein einzelnes Node an
     */
    private void applyThemeToNode(Node node, int themeIndex) {
        if (node != null) {
            // Alle Theme-Klassen entfernen
            node.getStyleClass().removeAll("theme-dark", "theme-light", "blau-theme", "gruen-theme", "lila-theme", "weiss-theme", "pastell-theme");
            
            if (themeIndex == 0) { // Weiß-Theme
                node.getStyleClass().add("weiss-theme");
            } else if (themeIndex == 1) { // Schwarz-Theme
                node.getStyleClass().add("theme-dark");
            } else if (themeIndex == 2) { // Pastell-Theme
                node.getStyleClass().add("pastell-theme");
            } else if (themeIndex == 3) { // Blau-Theme
                node.getStyleClass().addAll("theme-dark", "blau-theme");
            } else if (themeIndex == 4) { // Grün-Theme
                node.getStyleClass().addAll("theme-dark", "gruen-theme");
            } else if (themeIndex == 5) { // Lila-Theme
                node.getStyleClass().addAll("theme-dark", "lila-theme");
            }
        }
    }
    
    private void formatTextAtCursor(String markdownStart, String markdownEnd, String htmlStart, String htmlEnd) {
        if (codeArea == null) return;
        
        String selectedText = codeArea.getSelectedText();
        int caretPosition = codeArea.getCaretPosition();
        
        if (selectedText != null && !selectedText.isEmpty()) {
            // Text ist ausgewählt - formatiere den ausgewählten Text
            int start = codeArea.getSelection().getStart();
            int end = codeArea.getSelection().getEnd();
            
            String formattedText;
            if (outputFormat == DocxProcessor.OutputFormat.MARKDOWN) {
                formattedText = markdownStart + selectedText + markdownEnd;
            } else {
                formattedText = htmlStart + selectedText + htmlEnd;
            }
            
            codeArea.replaceText(start, end, formattedText);
            
            // Markiere den formatierten Text
            codeArea.selectRange(start, start + formattedText.length());
        } else {
            // Kein Text ausgewählt - füge Formatierung an der Cursor-Position ein
            String formatText;
            if (outputFormat == DocxProcessor.OutputFormat.MARKDOWN) {
                formatText = markdownStart + markdownEnd;
            } else {
                formatText = htmlStart + htmlEnd;
            }
            
            codeArea.insertText(caretPosition, formatText);
            
            // Setze Cursor zwischen die Formatierung
            if (outputFormat == DocxProcessor.OutputFormat.MARKDOWN) {
                codeArea.displaceCaret(caretPosition + markdownStart.length());
            } else {
                codeArea.displaceCaret(caretPosition + htmlStart.length());
            }
        }
        
        codeArea.requestFocus();
    }
    
    private void showRegexHelp() {
        Stage helpStage = new Stage();
        helpStage.setTitle("Java Regex - Syntax-Hilfe");
        helpStage.setResizable(false);
        
        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #f8f9fa;");
        
        // Titel
        Label titleLabel = new Label("Java Regex - Syntax-Referenz");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
        
        // ScrollPane für den Inhalt
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(600);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        
        VBox content = new VBox(15);
        content.setPadding(new Insets(10));
        
        // Grundlegende Syntax
        content.getChildren().add(createSection("Grundlegende Zeichen", new String[][] {
            {".", "Beliebiges Zeichen (außer Zeilenumbruch)"},
            {"\\d", "Ziffer (0-9)"},
            {"\\w", "Wortzeichen (Buchstabe, Ziffer, Unterstrich)"},
            {"\\s", "Leerzeichen (Space, Tab, Zeilenumbruch)"},
            {"\\D", "Nicht-Ziffer"},
            {"\\W", "Nicht-Wortzeichen"},
            {"\\S", "Nicht-Leerzeichen"}
        }));
        
        // Quantifizierer
        content.getChildren().add(createSection("Quantifizierer", new String[][] {
            {"*", "0 oder mehr Wiederholungen"},
            {"+", "1 oder mehr Wiederholungen"},
            {"?", "0 oder 1 Wiederholung"},
            {"{n}", "Genau n Wiederholungen"},
            {"{n,}", "Mindestens n Wiederholungen"},
            {"{n,m}", "Zwischen n und m Wiederholungen"}
        }));
        
        // Zeichenklassen
        content.getChildren().add(createSection("Zeichenklassen", new String[][] {
            {"[abc]", "a, b oder c"},
            {"[^abc]", "Nicht a, b oder c"},
            {"[a-z]", "Buchstabe von a bis z"},
            {"[A-Z]", "Buchstabe von A bis Z"},
            {"[0-9]", "Ziffer von 0 bis 9"},
            {"[a-zA-Z]", "Beliebiger Buchstabe"}
        }));
        
        // Anker
        content.getChildren().add(createSection("Anker", new String[][] {
            {"^", "Zeilenanfang"},
            {"$", "Zeilenende"},
            {"\\b", "Wortgrenze"},
            {"\\B", "Nicht-Wortgrenze"}
        }));
        
        // Gruppen und Rückreferenzen
        content.getChildren().add(createSection("Gruppen und Rückreferenzen", new String[][] {
            {"(abc)", "Gruppe (kann referenziert werden)"},
            {"(?:abc)", "Nicht-erfassende Gruppe"},
            {"$1, $2, ...", "Rückreferenz im Ersetzungstext"},
            {"\\1, \\2, ...", "Rückreferenz im Suchmuster"}
        }));
        
        // Beispiele für literarische Texte
        content.getChildren().add(createExampleSectionWithButtons("Beispiele für literarische Texte", new String[][] {
            {"\\\"([^\\\"\\n]+)\\\"", "Gerade Anführungszeichen durch deutsche ersetzen", "\"Hallo Welt\" → „Hallo Welt\"", "„$1\""},
            {"\\n{2,}", "Mehrere Leerzeilen durch eine ersetzen", "Absatz 1\\n\\n\\nAbsatz 2 → Absatz 1\\nAbsatz 2", "\\n"},
            {"[ ]{2,}", "Mehrere Leerzeichen durch eines ersetzen", "Das  ist   ein  Test. → Das ist ein Test.", " "},
            {"[ ]{2,}", "Mehrere Leerzeichen durch eines ersetzen", "Das  ist   ein  Test. → Das ist ein Test.", " "},
            {",\\s*\\\"", "Komma und Anführungszeichen normalisieren", ",\"Hallo\" → , \"Hallo\"", ", \""},
            {",\\s*\\.", "Komma gefolgt von Punkt durch nur Punkt ersetzen", "Hallo,. Welt → Hallo. Welt", "."},
            {"\\b(\\w+)\\s+\\1\\b", "Doppelte Wörter finden", "der der Mann → Treffer: der der", "$1"},
            {"^-\\s*", "Dialogstriche am Zeilenanfang durch Gedankenstrich ersetzen", "- Hallo! → – Hallo!", "– "},
            {"\\s+([.,;:!?])", "Leerzeichen vor Satzzeichen entfernen", "Hallo , Welt ! → Hallo, Welt!", "$1"}
        }, helpStage));
        
        // Wichtiger Hinweis
        VBox warningBox = new VBox(5);
        warningBox.setStyle("-fx-background-color: #fff3cd; -fx-border-color: #ffeaa7; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 10px;");
        
        Label warningTitle = new Label("⚠️ Wichtiger Hinweis:");
        warningTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: #856404;");
        
        Label warningText = new Label("Im Ersetzungstext verwenden Sie $1, $2, ... (nicht \\1, \\2, ...)");
        warningText.setStyle("-fx-text-fill: #856404;");
        warningText.setWrapText(true);
        
        warningBox.getChildren().addAll(warningTitle, warningText);
        content.getChildren().add(warningBox);
        
        scrollPane.setContent(content);
        
        // Schließen-Button
        Button closeButton = new Button("Schließen");
        closeButton.setStyle("-fx-background-color: #6c757d; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8px 20px;");
        closeButton.setOnAction(e -> helpStage.close());
        
        HBox buttonBox = new HBox();
        buttonBox.setAlignment(javafx.geometry.Pos.CENTER);
        buttonBox.getChildren().add(closeButton);
        
        root.getChildren().addAll(titleLabel, scrollPane, buttonBox);
        
        Scene scene = new Scene(root, 700, 700);
        helpStage.setScene(scene);
        helpStage.show();
    }
    
    private VBox createSection(String title, String[][] items) {
        VBox section = new VBox(8);
        section.setStyle("-fx-background-color: white; -fx-border-color: #dee2e6; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 15px;");
        
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #495057;");
        
        VBox itemsBox = new VBox(5);
        for (String[] item : items) {
            HBox itemBox = new HBox(10);
            itemBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            
            Label patternLabel = new Label(item[0]);
            patternLabel.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-weight: bold; -fx-text-fill: #007bff; -fx-min-width: 120px;");
            
            Label descLabel = new Label(item[1]);
            descLabel.setStyle("-fx-text-fill: #6c757d;");
            
            itemBox.getChildren().addAll(patternLabel, descLabel);
            itemsBox.getChildren().add(itemBox);
        }
        
        section.getChildren().addAll(titleLabel, itemsBox);
        return section;
    }
    
    private VBox createExampleSection(String title, String[][] examples) {
        VBox section = new VBox(8);
        section.setStyle("-fx-background-color: white; -fx-border-color: #dee2e6; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 15px;");
        
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #495057;");
        
        VBox examplesBox = new VBox(10);
        for (String[] example : examples) {
            VBox exampleBox = new VBox(5);
            exampleBox.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #e9ecef; -fx-border-radius: 3px; -fx-background-radius: 3px; -fx-padding: 10px;");
            
            Label patternLabel = new Label("Muster: " + example[0]);
            patternLabel.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-weight: bold; -fx-text-fill: #007bff;");
            
            Label descLabel = new Label("Beschreibung: " + example[1]);
            descLabel.setStyle("-fx-text-fill: #495057;");
            
            Label resultLabel = new Label("Beispiel: " + example[2]);
            resultLabel.setStyle("-fx-font-family: 'Consolas', monospace; -fx-text-fill: #28a745;");
            resultLabel.setWrapText(true);
            
            exampleBox.getChildren().addAll(patternLabel, descLabel, resultLabel);
            examplesBox.getChildren().add(exampleBox);
        }
        
        section.getChildren().addAll(titleLabel, examplesBox);
        return section;
    }
    
    private VBox createExampleSectionWithButtons(String title, String[][] examples, Stage helpStage) {
        VBox section = new VBox(8);
        section.setStyle("-fx-background-color: white; -fx-border-color: #dee2e6; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 15px;");
        
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #495057;");
        
        VBox examplesBox = new VBox(10);
        for (String[] example : examples) {
            VBox exampleBox = new VBox(5);
            exampleBox.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #e9ecef; -fx-border-radius: 3px; -fx-background-radius: 3px; -fx-padding: 10px;");
            
            Label patternLabel = new Label("Muster: " + example[0]);
            patternLabel.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-weight: bold; -fx-text-fill: #007bff;");
            
            Label descLabel = new Label("Beschreibung: " + example[1]);
            descLabel.setStyle("-fx-text-fill: #495057;");
            
            Label resultLabel = new Label("Beispiel: " + example[2]);
            resultLabel.setStyle("-fx-font-family: 'Consolas', monospace; -fx-text-fill: #28a745;");
            resultLabel.setWrapText(true);
            
            // Zeige Ersetzungstext an (macht Leerzeichen sichtbar)
            String replaceTextDisplay = example[3].replace(" ", "␣");
            Label replaceLabel = new Label("Ersetzung: '" + replaceTextDisplay + "'");
            replaceLabel.setStyle("-fx-font-family: 'Consolas', monospace; -fx-text-fill: #dc3545; -fx-font-size: 11px;");
            
            // Buttons für Suchen/Ersetzen und Makros
            HBox buttonBox = new HBox(5);
            buttonBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            
            Button searchButton = new Button("In Suchen/Ersetzen");
            searchButton.setStyle("-fx-background-color: #007bff; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 5px 10px; -fx-font-size: 11px;");
            searchButton.setOnAction(e -> {
                // Setze Such- und Ersetzungstext in den Hauptfeldern
                if (cmbSearchHistory != null) {
                    cmbSearchHistory.setValue(example[0]);
                }
                if (cmbReplaceHistory != null) {
                    cmbReplaceHistory.setValue(example[3]);
                }
                // Aktiviere Regex
                if (chkRegexSearch != null) {
                    chkRegexSearch.setSelected(true);
                }
                helpStage.close();
            });
            
            Button macroButton = new Button("In Makro");
            macroButton.setStyle("-fx-background-color: #28a745; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 5px 10px; -fx-font-size: 11px;");
            macroButton.setOnAction(e -> {
                // Setze Such- und Ersetzungstext in den Makro-Feldern
                if (txtMacroSearch != null) {
                    txtMacroSearch.setText(example[0]);
                }
                if (txtMacroReplace != null) {
                    txtMacroReplace.setText(example[3]);
                }
                // Fülle die Schritt-Beschreibung
                if (txtMacroStepDescription != null) {
                    txtMacroStepDescription.setText(example[1]);
                }
                // Aktiviere Regex im Makro
                if (chkMacroRegex != null) {
                    chkMacroRegex.setSelected(true);
                }
                helpStage.close();
            });
            
            buttonBox.getChildren().addAll(searchButton, macroButton);
            
            exampleBox.getChildren().addAll(patternLabel, descLabel, resultLabel, replaceLabel, buttonBox);
            examplesBox.getChildren().add(exampleBox);
        }
        
        section.getChildren().addAll(titleLabel, examplesBox);
        return section;
    }
    
    /**
     * Toggle für das Ollama KI-Assistenten-Fenster
     */
    private void toggleOllamaWindow() {
        if (ollamaWindow == null) {
            ollamaWindow = new OllamaWindow();
            ollamaWindow.setEditorReference(this);
        }
        
        if (ollamaWindow.isShowing()) {
            ollamaWindow.hide();
            ollamaWindowVisible = false;
            updateStatus("KI-Assistent geschlossen");
        } else {
            ollamaWindow.show();
            ollamaWindowVisible = true;
            updateStatus("KI-Assistent geöffnet");
        }
    }
    
    /**
     * Methode zum Einfügen von Text aus dem KI-Assistenten
     */
    public void insertTextFromAI(String text) {
        if (codeArea != null && text != null && !text.trim().isEmpty()) {
            int caretPosition = codeArea.getCaretPosition();
            codeArea.insertText(caretPosition, text);
            updateStatus("Text vom KI-Assistenten eingefügt");
        }
    }
    
    /**
     * Gibt den aktuellen Theme-Index zurück
     */
    public int getCurrentThemeIndex() {
        return currentThemeIndex;
    }
    
    /**
     * Gibt den aktuell ausgewählten Text zurück
     */
    public String getSelectedText() {
        if (codeArea != null) {
            return codeArea.getSelectedText();
        }
        return null;
    }
    
    /**
     * Gibt den gesamten Text aus dem CodeArea zurück
     */
    public String getCodeAreaText() {
        if (codeArea != null) {
            return codeArea.getText();
        }
        return null;
    }
    
    /**
     * Toggle für den Chapter-Editor
     */
    private void toggleChapterEditor() {
        chapterEditorVisible = !chapterEditorVisible;
        
        if (chapterEditorVisible) {
            // Chapter-Editor anzeigen
            chapterEditorPanel.setVisible(true);
            chapterEditorPanel.setManaged(true);
            chapterEditorPanel.setMinHeight(150);
            chapterEditorPanel.setPrefHeight(200);
            chapterEditorArea.setVisible(true);
            chapterEditorArea.setManaged(true);
            btnChapterEditor.setText("📝 Kapitel [ON]");
            
            // Chapter-Inhalt laden
            loadChapterContent();
            
            // SplitPane-Position wiederherstellen und Style zurücksetzen
            double savedPosition = preferences.getDouble("chapter_editor_divider_position", 0.8);
            mainSplitPane.setDividerPositions(savedPosition);
            mainSplitPane.setStyle(""); // Style zurücksetzen
            
            updateStatus("Chapter-Editor geöffnet");
        } else {
            // Aktuelle Divider-Position speichern
            if (mainSplitPane.getDividerPositions().length > 0) {
                preferences.putDouble("chapter_editor_divider_position", mainSplitPane.getDividerPositions()[0]);
            }
            
            // Chapter-Editor ausblenden
            chapterEditorPanel.setVisible(false);
            chapterEditorPanel.setManaged(false);
            chapterEditorPanel.setMinHeight(0);
            chapterEditorPanel.setPrefHeight(0);
            chapterEditorArea.setVisible(false);
            chapterEditorArea.setManaged(false);
            btnChapterEditor.setText("📝 Kapitel");
            
            // Divider fest auf 0.0 setzen und visuell verstecken
            mainSplitPane.setDividerPositions(0.0);
            mainSplitPane.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
            
            updateStatus("Chapter-Editor geschlossen");
        }
    }
    
    /**
     * Lädt den Chapter-Inhalt aus der entsprechenden Datei
     */
    private void loadChapterContent() {
        if (currentFile != null) {
            String chapterContent = NovelManager.loadChapter(currentFile.getAbsolutePath());
            // Setze den gesamten Inhalt direkt
            chapterEditorArea.setText(chapterContent);
            originalChapterContent = chapterContent;
            chapterContentChanged = false;
            logger.info("Chapter-Inhalt geladen für: " + currentFile.getName());
        }
    }
    
    /**
     * Speichert den Chapter-Inhalt in die entsprechende Datei
     */
    private void saveChapterContent() {
        if (currentFile != null) {
            String content = chapterEditorArea.getText();
            NovelManager.saveChapter(currentFile.getAbsolutePath(), content);
            originalChapterContent = content;
            chapterContentChanged = false;
            // Normale Anzeige zurücksetzen
            if (lblStatus != null) {
                lblStatus.setText("Chapter-Inhalt gespeichert: " + currentFile.getName());
                lblStatus.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 11px;");
            }
            logger.info("Chapter-Inhalt gespeichert für: " + currentFile.getName());
        }
    }
} 