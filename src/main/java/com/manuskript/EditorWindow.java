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
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import javafx.scene.control.cell.PropertyValueFactory;

public class EditorWindow implements Initializable {
    
    private static final Logger logger = LoggerFactory.getLogger(EditorWindow.class);
    
    @FXML private TextArea textArea;
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
    @FXML private Button btnOpen;
    @FXML private Button btnNew;
    @FXML private Button btnToggleSearch;
    @FXML private Button btnToggleMacro;
    @FXML private Button btnRegexHelp;
    @FXML private Button btnMacroRegexHelp;
    
    // Makro-UI-Elemente
    @FXML private ComboBox<String> cmbMacroList;
    @FXML private Button btnNewMacro;
    @FXML private Button btnDeleteMacro;
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
    private int currentMatchIndex = -1;
    private int totalMatches = 0;
    private String lastSearchText = "";
    private boolean searchPanelVisible = false;
    private boolean macroPanelVisible = false;
    private File currentFile = null;
    private DocxProcessor.OutputFormat outputFormat = DocxProcessor.OutputFormat.HTML;
    
    // Makro-Management
    private ObservableList<Macro> macros = FXCollections.observableArrayList();
    private Macro currentMacro = null;
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        preferences = Preferences.userNodeForPackage(EditorWindow.class);
        setupUI();
        setupEventHandlers();
        loadSearchReplaceHistory();
        setupSearchReplacePanel();
        setupMacroPanel();
    }
    
    private void setupUI() {
        // TextArea-Styling
        textArea.setWrapText(true);
        textArea.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 12px;");
        
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
        
        // Status-Label initialisieren
        updateStatus("Bereit");
        updateMatchCount(0, 0);
    }
    
    private void setupEventHandlers() {
        // Such- und Ersetzungs-Events - nur bei Enter oder Button-Klicks
        cmbSearchHistory.setOnAction(e -> {
            String searchText = cmbSearchHistory.getValue();
            if (searchText != null && !searchText.trim().isEmpty()) {
                findNext(); // Suche zum nächsten Treffer, nicht zum ersten
            }
        });
        
        cmbReplaceHistory.setOnAction(e -> {
            String replaceText = cmbReplaceHistory.getValue();
            if (replaceText != null) {
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
        
        // Debug-Button zum Löschen der Preferences
        btnToggleSearch.setOnAction(e -> toggleSearchPanel());
        btnToggleMacro.setOnAction(e -> toggleMacroPanel());
        btnRegexHelp.setOnAction(e -> showRegexHelp());
        btnMacroRegexHelp.setOnAction(e -> showRegexHelp());
        btnFindNext.setOnAction(e -> findNext());
        btnFindPrevious.setOnAction(e -> findPrevious());
        
        btnRegexHelp.setOnAction(e -> showRegexHelp());
        
        // Toolbar-Events
        btnSave.setOnAction(e -> saveFile());
        btnSaveAs.setOnAction(e -> saveFileAs());
        btnOpen.setOnAction(e -> openFile());
        btnNew.setOnAction(e -> newFile());
        btnToggleSearch.setOnAction(e -> toggleSearchPanel());
        
        // Keyboard-Shortcuts
        setupKeyboardShortcuts();
    }
    
    private void setupKeyboardShortcuts() {
        // Ctrl+F für Such-Panel
        textArea.setOnKeyPressed(event -> {
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
            String content = textArea.getText();
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
                textArea.deselect();
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
            String content = textArea.getText();
            
            // Wenn sich der Suchtext geändert hat, reset
            if (!searchText.equals(lastSearchText)) {
                totalMatches = 0;
                currentMatchIndex = -1;
                lastSearchText = searchText;
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
                textArea.deselect();
                return;
            }
            
            // Sammle alle Treffer-Positionen
            List<Integer> matchPositions = new ArrayList<>();
            Matcher collectMatcher = pattern.matcher(content);
            while (collectMatcher.find()) {
                matchPositions.add(collectMatcher.start());
            }
            
            // Finde den nächsten Treffer
            int currentPos = textArea.getCaretPosition();
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
            updateStatus("Fehler beim Suchen: " + e.getMessage());
        }
    }
    
    private void findPrevious() {
        String searchText = cmbSearchHistory.getValue();
        if (searchText == null || searchText.trim().isEmpty()) return;
        
        try {
            Pattern pattern = createSearchPattern(searchText.trim());
            String content = textArea.getText();
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
                textArea.deselect();
                return;
            }
            
            int start = textArea.getCaretPosition();
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
            updateStatus("Fehler beim Suchen: " + e.getMessage());
        }
    }
    
    private void replaceText() {
        String searchText = cmbSearchHistory.getValue();
        String replaceText = cmbReplaceHistory.getValue();
        
        if (searchText == null || searchText.trim().isEmpty()) return;
        
        try {
            Pattern pattern = createSearchPattern(searchText.trim());
            String content = textArea.getText();
            Matcher matcher = pattern.matcher(content);
            
            int start = textArea.getCaretPosition();
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
                
                textArea.replaceText(matcher.start(), matcher.end(), replacement);
                updateStatus("Ersetzt");
                
                // Nach dem Ersetzen müssen wir die Suche neu starten
                // Setze die Position auf den Anfang des ersetzten Texts
                textArea.positionCaret(matcher.start());
                
                // Suche nach dem nächsten Treffer
                findNext();
            }
            
        } catch (Exception e) {
            updateStatus("Fehler beim Ersetzen: " + e.getMessage());
        }
    }
    
    private void replaceAllText() {
        String searchText = cmbSearchHistory.getValue();
        String replaceText = cmbReplaceHistory.getValue();
        
        if (searchText == null || searchText.trim().isEmpty()) return;
        
        try {
            String content = textArea.getText();
            String replacement;
            
            // Debug: Zeige Status im Status-Feld
            boolean regexEnabled = chkRegexSearch.isSelected();
            lblStatus.setText("Regex: " + regexEnabled + " | Pattern: '" + searchText + "' | Replace: '" + replaceText + "'");
            
            // Immer Regex-Replace verwenden - das ist das Problem!
            // Wir verwenden immer matcher.replaceAll(), auch für normale Suche
            int flags = 0;
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
            
            textArea.setText(replacement);
            lblStatus.setText("Alle Treffer ersetzt");
            updateMatchCount(0, 0);
            
        } catch (Exception e) {
            lblStatus.setText("Fehler beim Ersetzen: " + e.getMessage());
        }
    }
    
    private Pattern createSearchPattern(String searchText) {
        int flags = 0;
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
        int flags = 0;
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
            textArea.selectRange(start, end);
            textArea.requestFocus();
            
            // Scroll zum gefundenen Text
            Platform.runLater(() -> {
                try {
                    // Berechne die Zeile des gefundenen Texts
                    String content = textArea.getText();
                    int lineStart = start;
                    while (lineStart > 0 && content.charAt(lineStart - 1) != '\n') {
                        lineStart--;
                    }
                    
                    // Setze den Cursor und scroll
                    textArea.positionCaret(start);
                    
                    // Stelle sicher, dass der Text sichtbar ist
                    textArea.setScrollTop(textArea.getScrollTop());
                } catch (Exception e) {
                    logger.warn("Fehler beim Scrollen: {}", e.getMessage());
                }
            });
        }
    }
    
    private void addToSearchHistory(String text) {
        if (!searchHistory.contains(text)) {
            searchHistory.add(0, text);
            while (searchHistory.size() > 20) {
                searchHistory.remove(searchHistory.size() - 1);
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
            while (replaceHistory.size() > 20) {
                replaceHistory.remove(replaceHistory.size() - 1);
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
            String searchHistoryStr = preferences.get("searchHistory", "");
            String replaceHistoryStr = preferences.get("replaceHistory", "");
            
            if (!searchHistoryStr.isEmpty()) {
                String[] items = searchHistoryStr.split("\\|");
                for (String item : items) {
                    if (!item.trim().isEmpty()) {
                        searchHistory.add(item.trim());
                    }
                }
            }
            
            if (!replaceHistoryStr.isEmpty()) {
                String[] items = replaceHistoryStr.split("\\|");
                for (String item : items) {
                    if (!item.trim().isEmpty()) {
                        replaceHistory.add(item.trim());
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
            String searchHistoryStr = String.join("|", searchHistory);
            preferences.put("searchHistory", searchHistoryStr);
        } catch (Exception e) {
            logger.warn("Fehler beim Speichern der Such-Historie: {}", e.getMessage());
        }
    }
    
    private void saveReplaceHistory() {
        try {
            String replaceHistoryStr = String.join("|", replaceHistory);
            preferences.put("replaceHistory", replaceHistoryStr);
        } catch (Exception e) {
            logger.warn("Fehler beim Speichern der Ersetzungs-Historie: {}", e.getMessage());
        }
    }
    
    private void updateStatus(String message) {
        lblStatus.setText(message);
        logger.info("Editor Status: {}", message);
    }
    
    private void updateMatchCount(int current, int total) {
        if (total > 0) {
            lblMatchCount.setText(current + " von " + total);
        } else {
            lblMatchCount.setText("");
        }
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
            updateStatus("Fehler beim Löschen der Preferences: " + e.getMessage());
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
        
        // Setze Standard-Dateiendung
        String defaultExtension = getDefaultExtension();
        fileChooser.setInitialFileName("manuskript" + defaultExtension);
        
        File file = fileChooser.showSaveDialog(stage);
        if (file != null) {
            // Füge Standard-Erweiterung hinzu, falls keine angegeben
            if (!hasValidExtension(file.getName())) {
                file = new File(file.getAbsolutePath() + defaultExtension);
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
            Files.write(file.toPath(), textArea.getText().getBytes());
            updateStatus("Datei gespeichert: " + file.getName());
        } catch (IOException e) {
            updateStatus("Fehler beim Speichern: " + e.getMessage());
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
        
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Text-Dateien", "*.txt")
        );
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
                textArea.setText(content);
                currentFile = file;
                updateStatus("Datei geöffnet: " + file.getName());
            } catch (IOException e) {
                updateStatus("Fehler beim Öffnen: " + e.getMessage());
            }
        }
    }
    
    private void newFile() {
        textArea.clear();
        currentFile = null;
        updateStatus("Neue Datei");
    }
    
    // Public Methoden für externe Verwendung
    public void setText(String text) {
        textArea.setText(text);
        updateStatus("Text geladen");
    }
    
    public String getText() {
        return textArea.getText();
    }
    
    public void setStage(Stage stage) {
        this.stage = stage;
    }
    
    public void setOutputFormat(DocxProcessor.OutputFormat format) {
        this.outputFormat = format;
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
                    }
                });
            }
            
            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    checkBox.setSelected(item != null && item);
                    setGraphic(checkBox);
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
                currentMacro.moveStepUp(selectedStep);
                saveMacros();
                updateStatus("Schritt nach oben verschoben");
            }
        }
    }
    
    private void moveMacroStepDown() {
        if (currentMacro != null) {
            MacroStep selectedStep = tblMacroSteps.getSelectionModel().getSelectedItem();
            if (selectedStep != null) {
                currentMacro.moveStepDown(selectedStep);
                saveMacros();
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
            String content = textArea.getText();
            
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
                    updateStatus("Fehler in Schritt " + processedSteps + ": " + e.getMessage());
                }
            }
            
            textArea.setText(content);
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
            
            int flags = 0;
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
        
        // Wenn alte Daten vorhanden sind, lösche sie
        if (savedMacros.contains("|||") || savedMacros.contains("<<<MACRO>>>")) {
            logger.info("Alte Makro-Daten gefunden - lösche alle Makros");
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
        Macro textCleanupMacro = new Macro("Text-Bereinigung", "Umfassende Textbereinigung mit 12 Schritten");
        textCleanupMacro.addStep(new MacroStep(1, "[ ]{2,}", " ", "Mehrfache Leerzeichen reduzieren", true, false, false));
        textCleanupMacro.addStep(new MacroStep(2, "\\n{3,}", "\\n\\n", "Mehrfache Leerzeilen auf 2 reduzieren", true, false, false));
        textCleanupMacro.addStep(new MacroStep(3, "\"", "\"", "Gerade Anführungszeichen öffnen", false, false, false));
        textCleanupMacro.addStep(new MacroStep(4, "\"", "\"", "Gerade Anführungszeichen schließen", false, false, false));
        textCleanupMacro.addStep(new MacroStep(5, "'", "'", "Gerade Apostrophe normalisieren", false, false, false));
        textCleanupMacro.addStep(new MacroStep(6, "\\s+$", "", "Leerzeichen am Zeilenende entfernen", true, false, false));
        textCleanupMacro.addStep(new MacroStep(7, "^\\s+", "", "Leerzeichen am Zeilenanfang entfernen", true, false, false));
        textCleanupMacro.addStep(new MacroStep(8, "\\t", "    ", "Tabs durch 4 Leerzeichen ersetzen", false, false, false));
        textCleanupMacro.addStep(new MacroStep(9, "\\s+\\n", "\\n", "Leerzeichen vor Zeilenumbrüchen entfernen", true, false, false));
        textCleanupMacro.addStep(new MacroStep(10, "\\n\\s+", "\\n", "Leerzeichen nach Zeilenumbrüchen entfernen", true, false, false));
        textCleanupMacro.addStep(new MacroStep(11, "\\s+", " ", "Alle mehrfachen Whitespaces auf ein Leerzeichen reduzieren", true, false, false));
        textCleanupMacro.addStep(new MacroStep(12, "^\\s*$", "", "Leere Zeilen entfernen", true, false, false));
        macros.add(textCleanupMacro);
        
        updateMacroList();
    }
    
    private void updateMacroList() {
        List<String> macroNames = macros.stream()
            .map(Macro::getName)
            .collect(Collectors.toList());
        cmbMacroList.getItems().clear();
        cmbMacroList.getItems().addAll(macroNames);
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