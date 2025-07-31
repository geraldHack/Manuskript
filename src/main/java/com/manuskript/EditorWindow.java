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
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.scene.control.Label;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
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
    @FXML private SplitPane mainSplitPane;
    @FXML private VBox macroPanel;
    
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
    
    // Toolbar-Buttons
    @FXML private Button btnSave;
    @FXML private Button btnSaveAs;
    @FXML private Button btnExportRTF;
    @FXML private Button btnExportDOCX;
    @FXML private Button btnOpen;
    @FXML private Button btnNew;
    @FXML private Button btnToggleSearch;
    @FXML private Button btnToggleMacro;
    @FXML private Button btnRegexHelp;
    
    // Font-Size Toolbar
    @FXML private ComboBox<String> cmbFontSize;
    @FXML private Button btnIncreaseFont;
    @FXML private Button btnDecreaseFont;
    @FXML private Button btnBold;
    @FXML private Button btnItalic;
    @FXML private Button btnThemeToggle;
    @FXML private Button btnMacroRegexHelp;
    
    // Makro-UI-Elemente
    @FXML private ComboBox<String> cmbMacroList;
    @FXML private Button btnNewMacro;
    @FXML private Button btnDeleteMacro;
    @FXML private Button btnSaveMacro;
    @FXML private Button btnRunMacro;
    @FXML private VBox macroDetailsPanel;
    @FXML private TableView<MacroStep> tblMacroSteps;
    @FXML private TableColumn<MacroStep, Boolean> colEnabled;
    @FXML private TableColumn<MacroStep, Integer> colStepNumber;
    @FXML private TableColumn<MacroStep, String> colDescription;
    @FXML private TableColumn<MacroStep, String> colSearchText;
    @FXML private TableColumn<MacroStep, String> colReplaceText;
    @FXML private TableColumn<MacroStep, String> colOptions;
    @FXML private TableColumn<MacroStep, String> colStatus;
    @FXML private TableColumn<MacroStep, String> colActions;
    @FXML private Button btnAddStep;
    @FXML private Button btnRemoveStep;
    @FXML private Button btnMoveStepUp;
    @FXML private Button btnMoveStepDown;
    
    // Makro-Schritt-Eingabe
    @FXML private TextField txtMacroSearch;
    @FXML private TextField txtMacroReplace;

    @FXML private TextField txtMacroStepDescription;
    @FXML private CheckBox chkMacroRegex;
    @FXML private CheckBox chkMacroCaseSensitive;
    @FXML private CheckBox chkMacroWholeWord;
    
    private Stage stage;
    private Preferences preferences;
    private ObservableList<String> searchHistory = FXCollections.observableArrayList();
    private ObservableList<String> replaceHistory = FXCollections.observableArrayList();
    private ObservableList<String> searchOptions = FXCollections.observableArrayList();
    private ObservableList<String> replaceOptions = FXCollections.observableArrayList();
    private int currentMatchIndex = -1;
    private int totalMatches = 0;
    private String lastSearchText = "";
    private boolean searchPanelVisible = false;
    private boolean macroPanelVisible = false;
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
        codeArea.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 12px; -rtfx-background-color: #ffffff;");
        
        // Zeilennummern hinzufügen
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        
        // VirtualizedScrollPane für bessere Performance
        VirtualizedScrollPane<CodeArea> scrollPane = new VirtualizedScrollPane<>(codeArea);
        
        // CodeArea zum Container hinzufügen
        textAreaContainer.getChildren().add(scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        
        // Such- und Ersetzungs-Panel initial ausblenden
        searchReplacePanel.setVisible(false);
        searchReplacePanel.setManaged(false);
        
        // SplitPane initialisieren - EXPLIZIT vertikal setzen
        mainSplitPane.setOrientation(Orientation.VERTICAL);
        VBox.setVgrow(mainSplitPane, Priority.ALWAYS);
        
        // Makro-Panel initial aus dem SplitPane entfernen
        mainSplitPane.getItems().remove(macroPanel);
        
        // Initial auf 100% setzen (nur Text-Editor sichtbar, Makro-Panel versteckt)
        mainSplitPane.setDividerPositions(1.0);
        
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
        btnMacroRegexHelp.setOnAction(e -> showRegexHelp());
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
    }
    
    private void setupKeyboardShortcuts() {
        // Keyboard-Shortcuts für den Editor
        codeArea.setOnKeyPressed(event -> {
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
            } else {
                // F3 und Shift+F3 für Suchen-Navigation
                switch (event.getCode()) {
                    case F3:
                        if (event.isShiftDown()) {
                            findPrevious();
                        } else {
                            findNext();
                        }
                        event.consume();
                        break;
                }
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
            
            // Wenn sich der Suchtext geändert hat, reset
            if (!searchText.equals(lastSearchText)) {
                totalMatches = 0;
                currentMatchIndex = -1;
                lastSearchText = searchText;
                // Entferne alle bisherigen Markierungen
                codeArea.setStyleSpans(0, content.length(), StyleSpans.singleton(new ArrayList<>(), 0));
            }
            
            // Zähle alle Treffer wenn nötig
            if (totalMatches == 0) {
                Matcher countMatcher = pattern.matcher(content);
                while (countMatcher.find()) {
                    totalMatches++;
                }
            }
            
            if (totalMatches == 0) {
                updateMatchCount(0, 0);
                updateStatus("Keine Treffer gefunden");
                codeArea.deselect();
                return;
            }
            
            // Sammle alle Treffer-Positionen und markiere sie
            List<Integer> matchPositions = new ArrayList<>();
            StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
            Matcher collectMatcher = pattern.matcher(content);
            int lastEnd = 0;
            
            while (collectMatcher.find()) {
                int start = collectMatcher.start();
                int end = collectMatcher.end();
                matchPositions.add(start);
                
                // Füge normalen Text vor dem Treffer hinzu
                if (start > lastEnd) {
                    spansBuilder.add(Collections.emptyList(), start - lastEnd);
                }
                
                // Füge markierten Text hinzu
                spansBuilder.add(Collections.singleton("search-match"), end - start);
                lastEnd = end;
            }
            
            // Füge restlichen Text hinzu
            if (lastEnd < content.length()) {
                spansBuilder.add(Collections.emptyList(), content.length() - lastEnd);
            }
            
            // Wende die Markierungen an
            codeArea.setStyleSpans(0, spansBuilder.create());
            
            // Finde den nächsten Treffer
            int currentPos = codeArea.getCaretPosition();
            int nextIndex = -1;
            
            // Suche den nächsten Treffer nach der aktuellen Position
            for (int i = 0; i < matchPositions.size(); i++) {
                if (matchPositions.get(i) > currentPos) {
                    nextIndex = i;
                    break;
                }
            }
            
            // Wenn kein Treffer nach der aktuellen Position, nimm den ersten (Wrap-around)
            if (nextIndex == -1) {
                nextIndex = 0;
            }
            
            // Markiere den gefundenen Treffer
            int matchStart = matchPositions.get(nextIndex);
            Matcher highlightMatcher = pattern.matcher(content);
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
            Pattern pattern = createSearchPattern(searchText.trim());
            String content = codeArea.getText();
            Matcher matcher = pattern.matcher(content);
            
            // Wenn es die erste Suche ist oder sich der Suchtext geändert hat, zähle alle Treffer
            if (totalMatches == 0 || !searchText.equals(lastSearchText)) {
                totalMatches = 0;
                while (matcher.find()) {
                    totalMatches++;
                }
                lastSearchText = searchText;
                currentMatchIndex = -1; // Reset für neue Suche
            }
            
            if (totalMatches == 0) {
                updateMatchCount(0, 0);
                updateStatus("Keine Treffer gefunden");
                codeArea.deselect();
                return;
            }
            
            int start = codeArea.getCaretPosition();
            boolean found = false;
            
            // Suche rückwärts
            matcher.reset();
            int lastStart = -1;
            int lastEnd = -1;
            int matchCount = 0;
            
            while (matcher.find()) {
                if (matcher.start() < start) {
                    lastStart = matcher.start();
                    lastEnd = matcher.end();
                    currentMatchIndex = matchCount;
                } else {
                    break;
                }
                matchCount++;
            }
            
            if (lastStart != -1) {
                highlightText(lastStart, lastEnd);
                updateMatchCount(currentMatchIndex + 1, totalMatches);
                updateStatus("Treffer " + (currentMatchIndex + 1) + " von " + totalMatches);
                found = true;
            }
            
            if (!found) {
                // Suche vom Ende (Wrap-around)
                matcher.reset();
                if (matcher.find()) {
                    int lastMatchStart = -1;
                    int lastMatchEnd = -1;
                    int lastMatchIndex = 0;
                    int count = 0;
                    
                    while (matcher.find()) {
                        lastMatchStart = matcher.start();
                        lastMatchEnd = matcher.end();
                        lastMatchIndex = count;
                        count++;
                    }
                    
                    if (lastMatchStart != -1) {
                        highlightText(lastMatchStart, lastMatchEnd);
                        currentMatchIndex = lastMatchIndex;
                        updateMatchCount(currentMatchIndex + 1, totalMatches);
                        updateStatus("Treffer " + (currentMatchIndex + 1) + " von " + totalMatches);
                    }
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
        lblStatus.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 11px;"); // Normale Farbe
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
    
    // ===== MAKRO-FUNKTIONALITÄT =====
    

    
    private void setupMacroPanel() {
        // Macro-Panel initial ausblenden
        macroPanel.setVisible(false);
        macroPanel.setManaged(false);
        
        // Macro-Liste mit Namen füllen
        cmbMacroList.setItems(FXCollections.observableArrayList());
        
        // TableView für Macro-Schritte einrichten
        colEnabled.setCellValueFactory(new PropertyValueFactory<>("enabled"));
        colEnabled.setCellFactory(param -> new TableCell<MacroStep, Boolean>() {
            private final CheckBox checkBox = new CheckBox();
            {
                checkBox.setOnAction(event -> {
                    MacroStep step = getTableView().getItems().get(getIndex());
                    if (step != null) {
                        step.setEnabled(checkBox.isSelected());
                        logger.info("CheckBox für Schritt " + step.getStepNumber() + " geändert zu: " + checkBox.isSelected());
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
                    
                    // Debug-Ausgabe für CheckBox-Status
                    MacroStep step = getTableView().getItems().get(getIndex());
                    if (step != null) {
                        logger.debug("CheckBox für Schritt " + step.getStepNumber() + " gesetzt auf: " + isSelected);
                    }
                }
            }
        });
        
        colStepNumber.setCellValueFactory(new PropertyValueFactory<>("stepNumber"));
        colDescription.setCellValueFactory(new PropertyValueFactory<>("description"));
        colSearchText.setCellValueFactory(new PropertyValueFactory<>("searchText"));
        colReplaceText.setCellValueFactory(new PropertyValueFactory<>("replaceText"));
        colOptions.setCellValueFactory(new PropertyValueFactory<>("optionsString"));
        colStatus.setCellValueFactory(param -> {
            MacroStep step = param.getValue();
            if (step == null) return new SimpleStringProperty("");
            
            // Binde direkt an die replacementCount Property
            SimpleStringProperty statusProperty = new SimpleStringProperty();
            
            // Funktion zum Aktualisieren des Status
            Runnable updateStatus = () -> {
                if (!step.isEnabled()) {
                    statusProperty.set("Deaktiviert");
                } else {
                    int count = step.getReplacementCount();
                    String status = count == 0 ? "Keine Ersetzungen" : count + " Ersetzungen";
                    statusProperty.set(status);
                }
            };
            
            // Initialer Wert setzen
            updateStatus.run();
            
            // Listener für Änderungen der replacementCount
            step.replacementCountProperty().addListener((obs, oldVal, newVal) -> {
                updateStatus.run();
            });
            
            // Listener für Änderungen der enabled Eigenschaft
            step.enabledProperty().addListener((obs, oldVal, newVal) -> {
                updateStatus.run();
            });
            
            return statusProperty;
        });
        
        // Bearbeiten-Button für jede Zeile
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
        
        // Macro-Event-Handler
        btnNewMacro.setOnAction(e -> createNewMacro());
        btnDeleteMacro.setOnAction(e -> deleteCurrentMacro());
        btnSaveMacro.setOnAction(e -> saveMacroToCSV());
        btnRunMacro.setOnAction(e -> runCurrentMacro());
        btnAddStep.setOnAction(e -> addMacroStep());
        btnRemoveStep.setOnAction(e -> removeMacroStep());
        btnMoveStepUp.setOnAction(e -> moveMacroStepUp());
        btnMoveStepDown.setOnAction(e -> moveMacroStepDown());
        
        // Macro-Auswahl
        cmbMacroList.setOnAction(e -> selectMacro());
        
        // Makros laden
        loadMacros();
    }
    
    private void toggleMacroPanel() {
        macroPanelVisible = !macroPanelVisible;
        
        if (macroPanelVisible) {
            // Makro-Panel öffnen - Panel zum SplitPane hinzufügen
            if (!mainSplitPane.getItems().contains(macroPanel)) {
                mainSplitPane.getItems().add(0, macroPanel); // An erster Position (oben) hinzufügen
            }
            macroPanel.setVisible(true);
            macroPanel.setManaged(true);
            
            // Trenner auf 30% setzen (30% Makro-Panel oben, 70% Text-Editor unten)
            mainSplitPane.setDividerPositions(0.3);
            updateStatus("Makro-Panel geöffnet");
        } else {
            // Makro-Panel schließen - Panel aus SplitPane entfernen
            mainSplitPane.getItems().remove(macroPanel);
            updateStatus("Makro-Panel geschlossen");
        }
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
        textCleanupMacro.addStep(new MacroStep(13, "‘(.*?)’", "›$1‹", "Einfache Anführungszeichen Französisch", true, false, false));
        macros.add(textCleanupMacro);
        
        // Neues Makro: Französische zu deutsche Anführungszeichen
        Macro frenchToGermanQuotes = new Macro("Französische → Deutsche Anführungszeichen", "Konvertiert französische zu deutschen Anführungszeichen");
        frenchToGermanQuotes.addStep(new MacroStep(1, "»(.*?)«", "„$1“", "Französische zu deutsche Anführungszeichen", true, false, false));
        frenchToGermanQuotes.addStep(new MacroStep(2, "›(.*?)‹", "‚$1‘", "Französische zu deutsche einfache Anführungszeichen", true, false, false));
        macros.add(frenchToGermanQuotes);
        
        // Neues Makro: Deutsche zu französische Anführungszeichen
        Macro germanToFrenchQuotes = new Macro("Deutsche → Französische Anführungszeichen", "Konvertiert deutsche zu französischen Anführungszeichen");
        germanToFrenchQuotes.addStep(new MacroStep(1, "„(.*?)“", "»$1«", "Deutsche zu französische Anführungszeichen", true, false, false));
        germanToFrenchQuotes.addStep(new MacroStep(2, "‚(.*?)‘", "›$1‹", "Deutsche zu französische einfache Anführungszeichen", true, false, false));
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
        
        // Fenster-Größe setzen
        stage.setWidth(width);
        stage.setHeight(height);
        
        // Fenster-Position setzen (nur wenn gültige Werte vorhanden)
        if (x >= 0 && y >= 0) {
            stage.setX(x);
            stage.setY(y);
        }
        
        // Divider-Position laden und Listener hinzufügen
        Platform.runLater(() -> {
            double dividerPosition = preferences.getDouble("divider_position", 0.3);
            mainSplitPane.setDividerPositions(dividerPosition);
            
            // Divider-Listener hinzufügen (nur wenn Dividers vorhanden sind)
            if (!mainSplitPane.getDividers().isEmpty()) {
                mainSplitPane.getDividers().get(0).positionProperty().addListener((obs, oldVal, newVal) -> {
                    if (newVal != null && !newVal.equals(oldVal)) {
                        preferences.putDouble("divider_position", newVal.doubleValue());
                    }
                });
            }
        });
    }
    
    private void loadToolbarSettings() {
        // Font-Size laden
        int fontSize = preferences.getInt("fontSize", 12);
        cmbFontSize.setValue(String.valueOf(fontSize));
        
        // Theme laden
        currentThemeIndex = preferences.getInt("editor_theme", 0);
        applyTheme(currentThemeIndex);
        
        // Theme-Button Tooltip aktualisieren
        updateThemeButtonTooltip();
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
            "-fx-caret-color: %s;" +
            "-fx-font-family: 'Consolas', 'Monaco', monospace;" +
            "-fx-font-size: %dpx;" +
            "-fx-background-color: %s;",
            backgroundColor, selectionColor, textColor, caretColor, fontSize, backgroundColor
        );
        
        codeArea.setStyle(cssStyle);
        
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
        
        // Debug-Ausgabe
        logger.info("Theme angewendet: Index={}, Hintergrund={}, Text={}", themeIndex, backgroundColor, textColor);
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
} 