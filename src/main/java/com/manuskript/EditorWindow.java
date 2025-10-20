package com.manuskript;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Separator;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.FileChooser;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.stage.Modality;
import javafx.stage.Window;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import java.util.Timer;
import java.util.TimerTask;
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
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.prefs.Preferences;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Collectors;
import javafx.scene.control.cell.PropertyValueFactory;
import java.util.Properties;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.fxmisc.richtext.model.StyleSpan;
import org.fxmisc.richtext.model.TwoDimensional.Bias;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.util.Duration;
import java.io.FileInputStream;

@SuppressWarnings("unchecked")
public class EditorWindow implements Initializable {
    
    private static final Logger logger = LoggerFactory.getLogger(EditorWindow.class);
    
    // Globale DOCX-Optionen für Export
    private DocxOptions globalDocxOptions = new DocxOptions();
    
    // Buch-Unterstützung
    private File currentFile;
    private boolean isCompleteDocument = false;
    
    // Ungespeicherte Änderungen
    private boolean hasUnsavedChanges = false;
    
    @FXML private VBox textAreaContainer;
    private CodeArea codeArea;
    @FXML private VBox mainContainer;
    @FXML private HBox searchReplaceContainer;
    @FXML private VBox searchReplacePanel;
    @FXML private VBox macroPanel;
    
    // Chapter-Editor Components (entfernt - Split Pane nicht mehr vorhanden)
    
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
    @FXML private Label lblSelectionCount;
    // @FXML private Label lblWindowTitle; // Entfernt - CustomStage hat eigene Titelleiste
    
    // Toolbar-Buttons
    @FXML private Button btnSave;
    @FXML private Button btnSaveAs;
    @FXML private Button btnExport;

    @FXML private Button btnOpen;
    @FXML private Button btnNew;
    @FXML private Button btnToggleSearch;
    @FXML private Button btnToggleMacro;
    @FXML private Button btnTextAnalysis;
    @FXML private Button btnRegexHelp;
    @FXML private Button btnKIAssistant;
    
    // Help-Buttons
    @FXML private Button btnHelpMain;
    @FXML private Button btnHelpMarkdown;
    @FXML private Button btnHelpTools;
    @FXML private Button btnToggleParagraphMarking;
    
    // Font-Size Toolbar
    @FXML private ComboBox<String> cmbFontSize;
    @FXML private Button btnIncreaseFont;
    @FXML private Button btnDecreaseFont;
    @FXML private Button btnBold;
    @FXML private Button btnItalic;
    @FXML private Button btnUnderline;
    @FXML private Button btnThemeToggle;
    @FXML private ComboBox<String> cmbQuoteStyle;
    // Zeilenabstand-ComboBox entfernt - wird von RichTextFX nicht unterstützt
    @FXML private ComboBox<String> cmbParagraphSpacing;

    @FXML private Button btnPreviousChapter;
    @FXML private Button btnNextChapter;
    
    // Undo/Redo Buttons
    @FXML private Button btnUndo;
    @FXML private Button btnRedo;
    
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
    
    private CustomStage stage;
    private CustomStage macroStage;
    private CustomStage textAnalysisStage;
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
    // Chapter-Editor-Variablen entfernt - Split Pane nicht mehr vorhanden
    private File originalDocxFile = null; // Originale DOCX-Datei
    private String originalContent = ""; // Kopie des ursprünglichen Inhalts für Vergleich
    private boolean paragraphMarkingEnabled = true; // Absatz-Markierung aktiviert
    private DocxProcessor.OutputFormat outputFormat = DocxProcessor.OutputFormat.HTML;
    private DocxProcessor docxProcessor;
    private MainController mainController; // Referenz zum MainController für Navigation
    
    // Theme-Management
    private int currentThemeIndex = 0;
    private int currentQuoteStyleIndex = 0;
    private boolean quoteToggleState = false; // false = öffnend, true = schließend
    private static final String[][] THEMES = {
        // Weißer Hintergrund / Schwarze Schrift
        {"#ffffff", "#000000", "#f8f9fa", "#e9ecef"},
        // Schwarzer Hintergrund / Weiße Schrift  
        {"#1a1a1a", "#ffffff", "#2d2d2d", "#404040"},
        // Pastell mit schwarzer Schrift
        {"#f3e5f5", "#000000", "#e1bee7", "#ce93d8"},
        // Blau mit weißer Schrift
        {"#1e3a8a", "#ffffff", "#3b82f6", "#60a5fa"},
        // Grün mit weißer Schrift
        {"#064e3b", "#ffffff", "#059669", "#10b981"},
        // Lila mit weißer Schrift
        {"#581c87", "#ffffff", "#7c3aed", "#a855f7"}
    };
    
    // Anführungszeichen-Styles
    private static final String[][] QUOTE_STYLES = {
        {"Deutsche Anführungszeichen", "deutsch"},
        {"Französische Anführungszeichen", "französisch"},
        {"Englische Anführungszeichen", "englisch"},
        {"Schweizer Anführungszeichen", "schweizer"}
    };
    
    // Anführungszeichen-Mapping (öffnend, schließend)
    private static final String[][] QUOTE_MAPPING = {
        {"\u201E", "\u201C"},        // Deutsch: U+201E, U+201C (Alt+0132, Alt+0147)
        {"\u00BB", "\u00AB"},       // Französisch: U+00BB, U+00AB
        {"\"", "\""},     // Englisch: U+0022, U+0022
        {"\u00AB", "\u00BB"}        // Schweizer: U+00AB, U+00BB
    };
    
    // Einfache Anführungszeichen-Mapping (öffnend, schließend)
    private static final String[][] SINGLE_QUOTE_MAPPING = {
        {"\u201A", "\u2019"},       // Deutsch: U+201A, U+2019
        {"\u203A", "\u2039"},       // Französisch: U+203A, U+2039
        {"'", "'"},       // Englisch: U+0027, U+0027
        {"\u2039", "\u203A"}        // Schweizer: U+2039, U+203A
    };
    
    // Makro-Management
    private ObservableList<Macro> macros = FXCollections.observableArrayList();
    private Macro currentMacro = null;
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        preferences = Preferences.userNodeForPackage(EditorWindow.class);
        
        // DOCX-Optionen aus User Preferences laden
        globalDocxOptions.loadFromPreferences();
        
        // DocxProcessor initialisieren
        if (docxProcessor == null) {
            docxProcessor = new DocxProcessor();
        }
        
        setupUI();
        loadSearchReplaceHistory();
        setupSearchReplacePanel();
        setupMacroPanel();
        setupFontSizeComboBox();
        setupQuoteStyleComboBox();
        setupDynamicQuoteCheck();
        
        // Help-Button-Verwaltung
        setupHelpButtons();
        
        // Absatz-Markierung initialisieren
        loadParagraphMarkingSetting();
        initializeParagraphMarkingButton();
        
        // ComboBox-Initialisierung direkt hier
        // setupLineSpacingComboBox entfernt - wird von RichTextFX nicht unterstützt
        setupParagraphSpacingComboBox();
        
        // Event-Handler setzen
        setupEventHandlers();
        
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
            
            // WICHTIG: CSS-Datei laden, wenn die Scene verfügbar ist
            if (mainContainer != null && mainContainer.getScene() != null) {
                String cssPath = ResourceManager.getCssResource("css/manuskript.css");
                if (cssPath != null && !mainContainer.getScene().getStylesheets().contains(cssPath)) {
                    mainContainer.getScene().getStylesheets().add(cssPath);
                } else {
                    logger.warn("CSS-Datei konnte in initialize nicht geladen werden: {}", cssPath);
                }
            }
            
            // WICHTIG: Zusätzlicher CSS-Load nach einer kurzen Verzögerung
            Platform.runLater(() -> {
                if (mainContainer != null && mainContainer.getScene() != null) {
                    String cssPath = ResourceManager.getCssResource("css/manuskript.css");
                    if (cssPath != null && !mainContainer.getScene().getStylesheets().contains(cssPath)) {
                        mainContainer.getScene().getStylesheets().add(cssPath);
                    }
                }
            });
            
            // WICHTIG: Theme und Font-Size aus Preferences laden (nach UI-Initialisierung)
            loadToolbarSettings();
            
            // Zusätzlicher Theme-Refresh für bessere Kompatibilität
            Platform.runLater(() -> {
                if (currentThemeIndex > 0) { // Nicht das Standard-weiße Theme
                    applyTheme(currentThemeIndex);
                }
                
                // Editor-Abstände beim Start anwenden
                applyEditorSpacing();
            });
        });
    }
    
    private void setupUI() {
        // WICHTIG: CSS-Datei laden, bevor die UI erstellt wird
        Platform.runLater(() -> {
            if (mainContainer != null && mainContainer.getScene() != null) {
                // CSS-Datei laden
                String cssPath = ResourceManager.getCssResource("css/manuskript.css");
                if (cssPath != null && !mainContainer.getScene().getStylesheets().contains(cssPath)) {
                    mainContainer.getScene().getStylesheets().add(cssPath);
                } else {
                    logger.warn("CSS-Datei konnte nicht geladen werden: {}", cssPath);
                }
            }
        });
        
        // Toolbar explizit sichtbar machen
        // btnNew und btnOpen sind in FXML auf visible="false" managed="false" gesetzt
        if (btnSave != null) {
            btnSave.setVisible(true);
            btnSave.setManaged(true);
        }
        if (btnSaveAs != null) {
            btnSaveAs.setVisible(true);
            btnSaveAs.setManaged(true);
        }
        if (btnExport != null) {
            btnExport.setVisible(true);
            btnExport.setManaged(true);
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
        
        // Timer für Markdown-Styling - IMMER anwenden aber existierende Styles bewahren
        Timeline stylingTimer = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            applyCombinedStyling();
        }));
        stylingTimer.setCycleCount(Timeline.INDEFINITE);
        stylingTimer.play();
        

        
        // CSS-Dateien für CodeArea laden
        // CSS mit ResourceManager laden
        String cssPath = ResourceManager.getCssResource("css/manuskript.css");
        codeArea.getStylesheets().add(cssPath);
        
        // CSS auch für die gesamte Scene laden (für Chapter-Editor)
        Platform.runLater(() -> {
            if (stage != null && stage.getScene() != null) {
                stage.getScene().getStylesheets().add(cssPath);
            }
        });
        
        Node caret = codeArea.lookup(".caret");
if (caret != null) {
    caret.setStyle("-fx-stroke: red; -fx-fill: red;");
}
        // Zeilennummern hinzufügen
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        
        // Leerzeilen-Markierung hinzufügen
        setupEmptyLineMarking();
        
        // VirtualizedScrollPane für bessere Performance
        VirtualizedScrollPane<CodeArea> scrollPane = new VirtualizedScrollPane<>(codeArea);
        scrollPane.getStyleClass().add("code-area");
        
        // 5px Padding für die ScrollPane
        scrollPane.setStyle("-fx-padding: 5px;");
        
        // CodeArea zum Container hinzufügen (im SplitPane)
        textAreaContainer.getChildren().add(scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        
        // VBox-Grow-Eigenschaften setzen
        VBox.setVgrow(textAreaContainer, Priority.ALWAYS);
        
        // Such- und Ersetzungs-Panel initial ausblenden
        searchReplacePanel.setVisible(false);
        searchReplacePanel.setManaged(false);
        
        // TextArea nimmt den gesamten verfügbaren Platz ein
        VBox.setVgrow(textAreaContainer, Priority.ALWAYS);
        
        // ComboBoxes editierbar machen
        cmbSearchHistory.setEditable(true);
        cmbReplaceHistory.setEditable(true);
        

        
        // Status-Label initialisieren
        updateStatus("Bereit");
        updateMatchCount(0, 0);
        
        // Selektion-Überwachung einrichten
        setupSelectionMonitoring();
        
        // Toolbar-Einstellungen laden (Font-Size, Theme, etc.)
        loadToolbarSettings();
    }
    
    private void setupSelectionMonitoring() {
        if (codeArea != null && lblSelectionCount != null) {
            // Überwache Änderungen in der Selektion
            codeArea.selectionProperty().addListener((obs, oldSelection, newSelection) -> {
                updateSelectionCount();
            });
            
            // Überwache Änderungen im Text (falls Selektion durch Textänderung beeinflusst wird)
            codeArea.textProperty().addListener((obs, oldText, newText) -> {
                updateSelectionCount();
                // Prüfe, ob Text sich geändert hat
                if (!newText.equals(oldText)) {
                    // Prüfe, ob Text wieder zum Originalzustand zurückgekehrt ist
                    if (cleanTextForComparison(newText).equals(originalContent)) {
                        markAsSaved();
                    } else {
                        markAsChanged();
                    }
                }
            });
            
            // Initiale Aktualisierung
            updateSelectionCount();
        }
    }
    
    private void updateSelectionCount() {
        if (codeArea != null && lblSelectionCount != null) {
            String selectedText = codeArea.getSelectedText();
            if (selectedText != null && !selectedText.isEmpty()) {
                int charCount = selectedText.length();
                int wordCount = countWords(selectedText);
                lblSelectionCount.setText("Auswahl: " + charCount + " Zeichen, " + wordCount + " Wörter");
            } else {
                lblSelectionCount.setText("Auswahl: 0 Zeichen, 0 Wörter");
            }
        }
    }
    
    private int countWords(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }
        // Teile den Text in Wörter auf (getrennt durch Whitespace)
        String[] words = text.trim().split("\\s+");
        return words.length;
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
        
        // ENTER-Taste im Suchfeld - macht dasselbe wie "Nächster" Button
        cmbSearchHistory.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                String searchText = cmbSearchHistory.getValue();
                if (searchText != null && !searchText.trim().isEmpty()) {
                    findNext(); // Genau wie der "Nächster" Button
                }
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
            // Absatz-Markierung temporär deaktivieren für Suche
            boolean wasParagraphMarkingEnabled = paragraphMarkingEnabled;
            paragraphMarkingEnabled = false;
            removeAllParagraphMarkings();
            
            String searchText = cmbSearchHistory.getValue();
            if (searchText != null && !searchText.trim().isEmpty()) {
                findNext(); // Suche zum nächsten Treffer, nicht zum ersten
            }
            
            // Absatz-Markierung wieder aktivieren, wenn sie vorher aktiviert war
            if (wasParagraphMarkingEnabled) {
                paragraphMarkingEnabled = true;
                markEmptyLines();
            }
        });
        btnReplace.setOnAction(e -> {
            // Absatz-Markierung temporär deaktivieren für Ersetzen
            boolean wasParagraphMarkingEnabled = paragraphMarkingEnabled;
            paragraphMarkingEnabled = false;
            removeAllParagraphMarkings();
            
            String searchText = cmbSearchHistory.getValue();
            String replaceText = cmbReplaceHistory.getValue();
            if (searchText != null && !searchText.trim().isEmpty()) {
                replaceText();
            }
            
            // Absatz-Markierung wieder aktivieren, wenn sie vorher aktiviert war
            if (wasParagraphMarkingEnabled) {
                paragraphMarkingEnabled = true;
                markEmptyLines();
            }
        });
        btnReplaceAll.setOnAction(e -> {
            // Absatz-Markierung temporär deaktivieren für Alle ersetzen
            boolean wasParagraphMarkingEnabled = paragraphMarkingEnabled;
            paragraphMarkingEnabled = false;
            removeAllParagraphMarkings();
            
            String searchText = cmbSearchHistory.getValue();
            String replaceText = cmbReplaceHistory.getValue();
            if (searchText != null && !searchText.trim().isEmpty()) {
                replaceAllText();
            }
            
            // Absatz-Markierung wieder aktivieren, wenn sie vorher aktiviert war
            if (wasParagraphMarkingEnabled) {
                paragraphMarkingEnabled = true;
                markEmptyLines();
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
        
        // Zeilenabstand-ComboBox entfernt - wird von RichTextFX nicht unterstützt
        cmbParagraphSpacing.setOnAction(e -> {
            changeParagraphSpacingFromComboBox();
        });
        
        // Text-Formatting Event-Handler
        btnBold.setOnAction(e -> formatTextBold());
        btnItalic.setOnAction(e -> formatTextItalic());
        btnUnderline.setOnAction(e -> formatTextUnderline());
        btnThemeToggle.setOnAction(e -> toggleTheme());
        
        // Abstandskonfiguration Event-Handler werden direkt in den Setup-Methoden gesetzt
        

        
            
        // btnMacroRegexHelp wurde entfernt
        
        // Textanalyse-Button
        btnTextAnalysis.setOnAction(e -> toggleTextAnalysisPanel());
        
        // KI-Assistent-Button
        btnKIAssistant.setOnAction(e -> toggleOllamaWindow());
        
        // Help-Buttons
        if (btnHelpMain != null) {
            btnHelpMain.setOnAction(e -> {
                logger.debug("Help-Button geklickt!");
                HelpSystem.showHelpWindow("chapter_editor.html");
            });
        }
        
        
        if (btnHelpMarkdown != null) {
            btnHelpMarkdown.setOnAction(e -> {
                logger.debug("Markdown-Help-Button geklickt!");
                HelpSystem.showHelpWindow("markdown_syntax.html");
            });
        }
        if (btnHelpTools != null) {
            btnHelpTools.setOnAction(e -> {
                logger.debug("Tools-Help-Button geklickt!");
                HelpSystem.showHelpWindow("chapter_editor_tools.html");
            });
        }
    }
    
    /**
     * Setup für Help-Buttons mit globalem Toggle
     */
    private void setupHelpButtons() {
        // Help-Button-Styling
        if (btnHelpMain != null) {
            btnHelpMain.setStyle(
                "-fx-background-color: #4A90E2 !important; " +
                "-fx-text-fill: white !important; " +
                "-fx-font-weight: bold !important; " +
                "-fx-font-size: 14px !important; " +
                "-fx-min-width: 24px !important; " +
                "-fx-min-height: 24px !important; " +
                "-fx-max-width: 24px !important; " +
                "-fx-max-height: 24px !important; " +
                "-fx-background-radius: 12px !important; " +
                "-fx-border-radius: 12px !important; " +
                "-fx-cursor: hand !important; " +
                "-fx-border: none !important; " +
                "-fx-padding: 0 !important;"
            );
            btnHelpMain.setTooltip(new Tooltip("Hilfe zu Haupt-Buttons"));
        }
        
        if (btnHelpMarkdown != null) {
            btnHelpMarkdown.setStyle(
                "-fx-background-color: #28A745 !important; " +
                "-fx-text-fill: white !important; " +
                "-fx-font-weight: bold !important; " +
                "-fx-font-size: 14px !important; " +
                "-fx-min-width: 24px !important; " +
                "-fx-min-height: 24px !important; " +
                "-fx-max-width: 24px !important; " +
                "-fx-max-height: 24px !important; " +
                "-fx-background-radius: 12px !important; " +
                "-fx-border-radius: 12px !important; " +
                "-fx-cursor: hand !important; " +
                "-fx-border: none !important; " +
                "-fx-padding: 0 !important;"
            );
            btnHelpMarkdown.setTooltip(new Tooltip("Markdown-Syntax Hilfe"));
        }
        
        // Globaler Help-Toggle
        updateHelpButtonVisibility();
    }
    
    /**
     * Aktualisiert die Sichtbarkeit der Help-Buttons basierend auf dem globalen Toggle
     */
    private void updateHelpButtonVisibility() {
        boolean helpEnabled = HelpSystem.isHelpEnabled();
        
        if (btnHelpMain != null) {
            btnHelpMain.setVisible(helpEnabled);
            btnHelpMain.setManaged(helpEnabled);
        }
        
        if (btnHelpMarkdown != null) {
            btnHelpMarkdown.setVisible(helpEnabled);
            btnHelpMarkdown.setManaged(helpEnabled);
        }
        
        if (btnHelpTools != null) {
            btnHelpTools.setVisible(helpEnabled);
            btnHelpTools.setManaged(helpEnabled);
        }
        
        // Kapitel-Navigation-Buttons
        btnPreviousChapter.setOnAction(e -> navigateToPreviousChapter());
        btnNextChapter.setOnAction(e -> navigateToNextChapter());
        
        // Chapter-Editor entfernt - keine Event-Handler mehr nötig
        
        // Chapter-Editor entfernt - keine Text-Change Listener mehr nötig
        if (btnRegexHelp != null) {
            // Sicherstellen, dass ein sichtbarer Inhalt vorhanden ist
            btnRegexHelp.setText("?");
            if (btnRegexHelp.getGraphic() == null) {
                btnRegexHelp.setGraphic(new Label("?"));
            }
            btnRegexHelp.setContentDisplay(javafx.scene.control.ContentDisplay.CENTER);
            btnRegexHelp.getStyleClass().add("help-button");
        btnRegexHelp.setOnAction(e -> showRegexHelp());
        }
        btnFindNext.setOnAction(e -> {
            // Absatz-Markierung temporär deaktivieren für Suche
            boolean wasParagraphMarkingEnabled = paragraphMarkingEnabled;
            paragraphMarkingEnabled = false;
            removeAllParagraphMarkings();
            
            findNext();
            
            // Absatz-Markierung wieder aktivieren, wenn sie vorher aktiviert war
            if (wasParagraphMarkingEnabled) {
                paragraphMarkingEnabled = true;
                markEmptyLines();
            }
        });
        btnFindPrevious.setOnAction(e -> {
            // Absatz-Markierung temporär deaktivieren für Suche
            boolean wasParagraphMarkingEnabled = paragraphMarkingEnabled;
            paragraphMarkingEnabled = false;
            removeAllParagraphMarkings();
            
            findPrevious();
            
            // Absatz-Markierung wieder aktivieren, wenn sie vorher aktiviert war
            if (wasParagraphMarkingEnabled) {
                paragraphMarkingEnabled = true;
                markEmptyLines();
            }
        });
        
        // Toolbar-Events
        btnSave.setOnAction(e -> {
            saveFile();
            markAsSaved();
        });
        btnSaveAs.setOnAction(e -> {
            saveFileAs();
            markAsSaved();
        });
                    btnExport.setOnAction(e -> showExportDialog());
        // btnOpen und btnNew Event-Handler entfernt - Buttons sind unsichtbar
        btnToggleSearch.setOnAction(e -> toggleSearchPanel());
        btnToggleMacro.setOnAction(e -> toggleMacroPanel());
        btnToggleParagraphMarking.setOnAction(e -> toggleParagraphMarking());
        
        // Undo/Redo Event-Handler
        btnUndo.setOnAction(e -> codeArea.undo());
        btnRedo.setOnAction(e -> codeArea.redo());
        
        // Keyboard-Shortcuts
        setupKeyboardShortcuts();
        
        // Text-Selektion Event-Listener für KI-Assistent
        codeArea.selectionProperty().addListener((obs, oldSelection, newSelection) -> {
            if (ollamaWindow != null && ollamaWindow.isShowing()) {
                String selectedText = codeArea.getSelectedText();
                if (selectedText != null && !selectedText.trim().isEmpty()) {
                    // Automatisch selektierten Text in das Eingabefeld kopieren
                    ollamaWindow.updateSelectedText(selectedText);
                }
            }
        });
    }
    
    private void setupKeyboardShortcuts() {
        // Globaler Event-Filter für CTRL+F überall im Editor-Fenster
        if (mainContainer != null) {
            mainContainer.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                if (event.isControlDown() && event.getCode() == KeyCode.F) {
                    showSearchPanel();
                    event.consume();
                }
            });
        }
        
        // Keyboard-Shortcuts für den Editor
        codeArea.setOnKeyPressed(event -> {
            // Debug-Ausgabe für Keyboard-Events
            
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
            } else if (event.getCode() == KeyCode.F3) {
                // F3 und Shift+F3 für Suchen-Navigation
                if (event.isShiftDown()) {
                    findPrevious();
                } else {
                    findNext();
                }
                event.consume();
            } else if (event.getCode() == KeyCode.DELETE) {
                // Delete-Taste: Standard-Verhalten beibehalten
                // RichTextFX macht das automatisch richtig
            }
        });
        
        // Mausrad-Event-Filter für Schriftgröße (Strg + Mausrad)
        codeArea.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (event.isControlDown()) {
                event.consume();
                
                double deltaY = event.getDeltaY();
                logger.debug("Mausrad-Event: deltaY={}, Strg gedrückt={}", deltaY, event.isControlDown());
                
                if (deltaY > 0) {
                    // Mausrad nach oben - Schriftgröße erhöhen
                    logger.debug("Schriftgröße erhöhen um 2");
                    changeFontSize(2);
                } else if (deltaY < 0) {
                    // Mausrad nach unten - Schriftgröße verringern
                    logger.debug("Schriftgröße verringern um 2");
                    changeFontSize(-2);
                }
            }
        });
        
        // Anführungszeichen-Ersetzung mit Event-Filter
        codeArea.addEventFilter(KeyEvent.KEY_TYPED, event -> {
            String character = event.getCharacter();
            if (character.equals("\"") || character.equals("'")) {
                event.consume();
                handleQuoteReplacement(character);
            }
        });
    }
    
    private void handleQuoteReplacement(String inputQuote) {
        if (currentQuoteStyleIndex < 0 || currentQuoteStyleIndex >= QUOTE_MAPPING.length) {
            return;
        }
        
        String content = codeArea.getText();
        int caretPosition = codeArea.getCaretPosition();
        
        // Bestimme ob öffnend oder schließend
        boolean shouldBeClosing = false;
        
        if (caretPosition > 0) {
            char charBefore = content.charAt(caretPosition - 1);
            // Buchstabe, Punkt oder Auslassungszeichen davor -> schließend
            if (Character.isLetterOrDigit(charBefore) || charBefore == '.' || charBefore == '…' || 
                charBefore == '!' || charBefore == '?' || charBefore == ',' || charBefore == ';' || 
                charBefore == ':' || charBefore == ')') {
                shouldBeClosing = true;
            }
        }
        
        // Einfache Toggle-Logik: Wenn Kontext unklar, toggle
        if (caretPosition == 0 || !shouldBeClosing) {
            // Am Anfang oder nach Leerzeichen/Zeilenumbruch -> öffnend
            quoteToggleState = false;
        } else {
            // Nach Buchstaben/Satzzeichen -> schließend
            quoteToggleState = true;
        }
        
        // Wähle das richtige Anführungszeichen
        String replacement;
        if (inputQuote.equals("\"")) {
            replacement = quoteToggleState ? QUOTE_MAPPING[currentQuoteStyleIndex][1] : QUOTE_MAPPING[currentQuoteStyleIndex][0];
        } else {
            replacement = quoteToggleState ? SINGLE_QUOTE_MAPPING[currentQuoteStyleIndex][1] : SINGLE_QUOTE_MAPPING[currentQuoteStyleIndex][0];
        }
        
        // Ersetze das Zeichen
        codeArea.insertText(caretPosition, replacement);
        
        // EINGEBAUTE UNDO-FUNKTIONALITÄT VERWENDEN - kein manueller Aufruf nötig
    }
    
    /**
     * Dynamische Überprüfung für französische und schweizer Anführungszeichen
     * Konvertiert automatisch zwischen Anführungszeichen und Apostrophen basierend auf nachfolgenden Buchstaben
     */
    private void setupDynamicQuoteCheck() {
        // Event-Handler für kontinuierliche Überprüfung während des Tippens
        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            // Nur für französische und schweizer Modi
            if (currentQuoteStyleIndex != 1 && currentQuoteStyleIndex != 3) { // 1 = französisch, 3 = schweizer
                return;
            }
            
            // Verhindere Endlosschleife: Nur wenn Text sich wirklich geändert hat
            if (oldText != null && oldText.equals(newText)) {
                return;
            }
            
            // Überprüfe alle französischen und schweizer Anführungszeichen
            checkAndConvertQuotes(newText);
        });
    }
    
    // Fehler-Sammlung für Anführungszeichen
    private List<QuoteError> quoteErrors = new ArrayList<>();
    private boolean quoteErrorsDialogShown = false; // Flag um mehrfaches Öffnen zu verhindern
    
    /**
     * Sammelt Anführungszeichen-Fehler für die Anzeige
     */
    private void collectQuoteErrors(String paragraph, String type, int count) {
        quoteErrors.add(new QuoteError(paragraph, type, count));
    }
    
    /**
     * Zeigt eine CustomStage mit den gefundenen Anführungszeichen-Fehlern an
     */
    private void showQuoteErrorsDialog() {
        if (quoteErrors.isEmpty()) {
            updateStatus("ℹ️ Keine Anführungszeichen-Fehler gefunden.");
            return;
        }
        
        // Verhindere mehrfaches Öffnen
        if (quoteErrorsDialogShown) {
            return;
        }
        quoteErrorsDialogShown = true;
        
        // CustomStage erstellen
        CustomStage errorStage = new CustomStage();
        errorStage.setCustomTitle("Anführungszeichen-Fehler");
        errorStage.setWidth(800);
        errorStage.setHeight(600);
        
        
        // Haupt-Container
        VBox mainContainer = new VBox(20);
        mainContainer.setPadding(new Insets(20));
        
        // Titel
        Label titleLabel = new Label("⚠️ Anführungszeichen-Fehler gefunden");
        titleLabel.getStyleClass().add("full-title");
        
        // Beschreibung
        Label descriptionLabel = new Label("Die folgenden Absätze haben eine ungerade Anzahl von Anführungszeichen:");
        descriptionLabel.getStyleClass().add("full-description");
        
        // ScrollPane für Fehler-Liste
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.getStyleClass().add("full-scroll-pane");
        VBox errorList = new VBox(10);
        errorList.getStyleClass().add("full-content");
        
        for (QuoteError error : quoteErrors) {
            VBox errorItem = new VBox(5);
            errorItem.getStyleClass().add("full-error-item");
            
            // Fehler-Typ
            Label typeLabel = new Label(error.getType() + " (" + error.getCount() + " Stück)");
            typeLabel.getStyleClass().add("full-error-type");
            
            // Absatz-Text mit Klick-Funktionalität
            TextArea paragraphArea = new TextArea(error.getParagraph());
            paragraphArea.setEditable(false);
            paragraphArea.setPrefRowCount(3);
            paragraphArea.setWrapText(true); // Text wrappen
            paragraphArea.getStyleClass().add("full-text-area");
            
            // Klick-Handler für Sprung zum Absatz im Editor
            paragraphArea.setOnMouseClicked(e -> {
                jumpToParagraphInEditor(error.getParagraph());
                // Dialog NICHT schließen - es gibt noch mehr Fehler!
            });
            
            // Tooltip für Benutzer-Hinweis
            paragraphArea.setTooltip(new Tooltip("Klicken zum Springen zum Absatz im Editor"));
            
            errorItem.getChildren().addAll(typeLabel, paragraphArea);
            errorList.getChildren().add(errorItem);
        }
        
        scrollPane.setContent(errorList);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(400);
        
        // Buttons
        HBox buttonBox = new HBox(10);
        Button closeButton = new Button("Schließen");
        closeButton.getStyleClass().add("full-button");
        closeButton.setOnAction(e -> {
            quoteErrors.clear(); // Fehler-Liste leeren
            quoteErrorsDialogShown = false; // Flag zurücksetzen
            errorStage.close();
        });
        
        Button clearButton = new Button("Fehler-Liste leeren");
        clearButton.getStyleClass().add("full-button-danger");
        clearButton.setOnAction(e -> {
            quoteErrors.clear();
            quoteErrorsDialogShown = false; // Flag zurücksetzen
            errorStage.close();
            updateStatus("✅ Fehler-Liste geleert.");
        });
        
        buttonBox.getChildren().addAll(closeButton, clearButton);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        
        // Alles zusammenfügen
        mainContainer.getChildren().addAll(titleLabel, descriptionLabel, scrollPane, buttonBox);
        
        // Scene erstellen und anzeigen
        Scene scene = new Scene(mainContainer);
        
        // CSS laden - EXAKT WIE ALLE ANDEREN STAGES
        String cssPath = ResourceManager.getCssResource("css/manuskript.css");
        if (cssPath != null) {
            scene.getStylesheets().add(cssPath);
        }
        
        errorStage.setSceneWithTitleBar(scene);
        
        // Theme anwenden - EXAKT WIE ALLE ANDEREN STAGES
        errorStage.setFullTheme(currentThemeIndex);
        
        // Fenster anzeigen
        errorStage.show();
        
        // Status aktualisieren
        updateStatus("⚠️ " + quoteErrors.size() + " Anführungszeichen-Fehler gefunden und angezeigt.");
    }
    
    /**
     * Springt zum angegebenen Absatz im Editor
     */
    private void jumpToParagraphInEditor(String targetParagraph) {
        if (targetParagraph == null || targetParagraph.trim().isEmpty()) {
            return;
        }
        
        String editorText = codeArea.getText();
        if (editorText == null || editorText.isEmpty()) {
            return;
        }
        
        // Suche nach dem Absatz im Editor-Text
        int paragraphIndex = editorText.indexOf(targetParagraph);
        if (paragraphIndex == -1) {
            // Fallback: Suche nach ähnlichem Text (erste 50 Zeichen)
            String searchText = targetParagraph.length() > 50 ? 
                targetParagraph.substring(0, 50) : targetParagraph;
            paragraphIndex = editorText.indexOf(searchText);
        }
        
        if (paragraphIndex != -1) {
            // Cursor setzen
            codeArea.moveTo(paragraphIndex);
            
            // Dann zum Absatz scrollen - ein bisschen weiter nach unten
            final int finalParagraphIndex = codeArea.offsetToPosition(paragraphIndex, Bias.Forward).getMajor();
            Platform.runLater(() -> {
                // Erst zum Absatz scrollen
                codeArea.showParagraphInViewport(finalParagraphIndex);
                // Dann noch ein bisschen weiter nach unten scrollen
                Platform.runLater(() -> {
                    codeArea.showParagraphInViewport(finalParagraphIndex + 10);
                });
            });
            
            // Fokus auf Editor setzen - verstärkt
            Platform.runLater(() -> {
                codeArea.requestFocus();
                stage.requestFocus(); // Hauptfenster-Fokus
                codeArea.requestFocus(); // Editor-Fokus verstärken
            });
            
            // Status-Meldung
            updateStatus("📍 Zu fehlerhaftem Absatz gesprungen");
        } else {
            updateStatus("⚠️ Absatz im Editor nicht gefunden");
        }
    }
    
    /**
     * Hilfsklasse für Anführungszeichen-Fehler
     */
    private static class QuoteError {
        private String paragraph;
        private String type;
        private int count;
        
        public QuoteError(String paragraph, String type, int count) {
            this.paragraph = paragraph;
            this.type = type;
            this.count = count;
        }
        
        public String getParagraph() { return paragraph; }
        public String getType() { return type; }
        public int getCount() { return count; }
    }
    /**
     * Überprüft Anführungszeichen beim Laden und sammelt Fehler für Dialog
     */
    private void checkAndConvertQuotesOnLoad(String text) {
        if (text == null || text.isEmpty()) return;
        
        // Fehler-Liste für diese Überprüfung leeren
        quoteErrors.clear();
        
        // ZUVERLÄSSIGKEITS-CHECK: Überprüfe jeden Absatz auf ungerade Anführungszeichen (nur Warnung, keine automatische Korrektur)
        String[] paragraphs = text.split("\n");
        for (String paragraph : paragraphs) {
            if (paragraph.trim().isEmpty()) continue;
            
            // Zähle doppelte Anführungszeichen
            int doubleQuotes = 0;
            for (char c : paragraph.toCharArray()) {
                if (c == '"' || c == '\u201E' || c == '\u201C' || c == '\u201D' || c == '\u00AB' || c == '\u00BB') { // " „ " " « »
                    doubleQuotes++;
                }
            }
            
            // Zähle einfache Anführungszeichen
            int singleQuotes = 0;
            for (char c : paragraph.toCharArray()) {
                if (c == '\u2039' || c == '\u203A') { // ‹ oder ›
                    singleQuotes++;
                }
            }
            
            // Wenn ungerade Anzahl -> Fehler sammeln für Anzeige
            if (doubleQuotes % 2 != 0) {
                // Fehler für Anzeige sammeln
                collectQuoteErrors(paragraph, "Doppelte Anführungszeichen", doubleQuotes);
            }
            
            if (singleQuotes % 2 != 0) {
                // Fehler für Anzeige sammeln
                collectQuoteErrors(paragraph, "Einfache Anführungszeichen", singleQuotes);
            }
        }
    }
    
    /**
     * Überprüft und konvertiert französische und schweizer Anführungszeichen zu Apostrophen und umgekehrt
     */
    private void checkAndConvertQuotes(String text) {
        if (text == null || text.isEmpty()) return;
        
        // Fehler-Liste für diese Überprüfung leeren
        quoteErrors.clear();
        
        StringBuilder result = new StringBuilder(text);
        boolean hasChanges = false;
        
        // ZUVERLÄSSIGKEITS-CHECK: Überprüfe jeden Absatz auf ungerade Anführungszeichen (nur Warnung, keine automatische Korrektur)
        String[] paragraphs = text.split("\n");
        for (String paragraph : paragraphs) {
            if (paragraph.trim().isEmpty()) continue;
            
            // Zähle doppelte Anführungszeichen
            int doubleQuotes = 0;
            for (char c : paragraph.toCharArray()) {
                if (c == '"' || c == '\u201E' || c == '\u201C' || c == '\u201D' || c == '\u00AB' || c == '\u00BB') { // " „ " " « »
                    doubleQuotes++;
                }
            }
            
            // Zähle einfache Anführungszeichen
            int singleQuotes = 0;
            for (char c : paragraph.toCharArray()) {
                if (c == '\u2039' || c == '\u203A') { // ‹ oder ›
                    singleQuotes++;
                }
            }
            
            // Wenn ungerade Anzahl -> Fehler sammeln für Anzeige
            if (doubleQuotes % 2 != 0) {
                // Fehler für Anzeige sammeln
                collectQuoteErrors(paragraph, "Doppelte Anführungszeichen", doubleQuotes);
            }
            
            if (singleQuotes % 2 != 0) {
                // Fehler für Anzeige sammeln
                collectQuoteErrors(paragraph, "Einfache Anführungszeichen", singleQuotes);
            }
        }
        
        // Für französischen Modus (Index 1): ›‹ und »«
        if (currentQuoteStyleIndex == 1) {
            // Überprüfe » (U+00BB) - französisches schließendes doppeltes Anführungszeichen
            for (int i = 0; i < result.length(); i++) {
                if (result.charAt(i) == '\u00BB') { // »
                    // Prüfe ob ein Buchstabe davor UND dahinter steht
                    boolean hasLetterBefore = i > 0 && Character.isLetter(result.charAt(i - 1));
                    boolean hasLetterAfter = i + 1 < result.length() && Character.isLetter(result.charAt(i + 1));
                    
                    if (hasLetterBefore && hasLetterAfter) {
                        // Buchstabe davor UND dahinter -> zu Apostroph konvertieren
                        result.setCharAt(i, '\'');
                        hasChanges = true;
                    }
                }
            }
            
            // Überprüfe « (U+00AB) - französisches öffnendes doppeltes Anführungszeichen
            for (int i = 0; i < result.length(); i++) {
                if (result.charAt(i) == '\u00AB') { // «
                    // Prüfe ob ein Buchstabe davor UND dahinter steht
                    boolean hasLetterBefore = i > 0 && Character.isLetter(result.charAt(i - 1));
                    boolean hasLetterAfter = i + 1 < result.length() && Character.isLetter(result.charAt(i + 1));
                    
                    if (hasLetterBefore && hasLetterAfter) {
                        // Buchstabe davor UND dahinter -> zu Apostroph konvertieren
                        result.setCharAt(i, '\'');
                        hasChanges = true;
                    }
                }
            }
            
            // Überprüfe › (U+203A) - französisches schließendes einfaches Anführungszeichen
            for (int i = 0; i < result.length(); i++) {
                if (result.charAt(i) == '\u203A') { // ›
                    // Prüfe ob ein Buchstabe davor UND dahinter steht
                    boolean hasLetterBefore = i > 0 && Character.isLetter(result.charAt(i - 1));
                    boolean hasLetterAfter = i + 1 < result.length() && Character.isLetter(result.charAt(i + 1));
                    
                    if (hasLetterBefore && hasLetterAfter) {
                        // Buchstabe davor UND dahinter -> zu Apostroph konvertieren
                        result.setCharAt(i, '\'');
                        hasChanges = true;
                    }
                }
            }
            
            // Überprüfe ‹ (U+2039) - französisches öffnendes einfaches Anführungszeichen
            for (int i = 0; i < result.length(); i++) {
                if (result.charAt(i) == '\u2039') { // ‹
                    // Prüfe ob ein Buchstabe davor UND dahinter steht
                    boolean hasLetterBefore = i > 0 && Character.isLetter(result.charAt(i - 1));
                    boolean hasLetterAfter = i + 1 < result.length() && Character.isLetter(result.charAt(i + 1));
                    
                    // Prüfe auf Possessivpronomen (Buchstabe davor, aber kein Buchstabe dahinter)
                    boolean isPossessiveEnd = hasLetterBefore && !hasLetterAfter && 
                        (i + 1 >= result.length() || Character.isWhitespace(result.charAt(i + 1)) || 
                         result.charAt(i + 1) == '.' || result.charAt(i + 1) == ',' || 
                         result.charAt(i + 1) == '!' || result.charAt(i + 1) == '?' || 
                         result.charAt(i + 1) == ';' || result.charAt(i + 1) == ':');
                    
                    if ((hasLetterBefore && hasLetterAfter) || isPossessiveEnd) {
                        // Buchstabe davor UND dahinter ODER Possessivpronomen -> zu Apostroph konvertieren
                        result.setCharAt(i, '\'');
                        hasChanges = true;
                    }
                }
            }
            
            // Überprüfe Apostrophe - konvertiere zu Anführungszeichen wenn NICHT Buchstabe davor UND dahinter
            for (int i = 0; i < result.length(); i++) {
                if (result.charAt(i) == '\'') {
                    // Prüfe ob ein Buchstabe davor UND dahinter steht
                    boolean hasLetterBefore = i > 0 && Character.isLetter(result.charAt(i - 1));
                    boolean hasLetterAfter = i + 1 < result.length() && Character.isLetter(result.charAt(i + 1));
                    
                    // Prüfe auf Possessivpronomen-Apostroph (Buchstabe davor, aber kein Buchstabe dahinter)
                    boolean isPossessiveApostrophe = hasLetterBefore && !hasLetterAfter && 
                        (i + 1 >= result.length() || Character.isWhitespace(result.charAt(i + 1)) || 
                         result.charAt(i + 1) == '.' || result.charAt(i + 1) == ',' || 
                         result.charAt(i + 1) == '!' || result.charAt(i + 1) == '?' || 
                         result.charAt(i + 1) == ';' || result.charAt(i + 1) == ':');
                    
                    // Prüfe ob es ein öffnendes Anführungszeichen im selben Satz gibt
                    boolean hasOpeningQuoteInSentence = false;
                    if (isPossessiveApostrophe) {
                        // Finde den Anfang des Satzes (zurück bis zum letzten Satzende oder Anfang)
                        int sentenceStart = i;
                        for (int j = i - 1; j >= 0; j--) {
                            char c = result.charAt(j);
                            if (c == '.' || c == '!' || c == '?' || c == '\n') {
                                sentenceStart = j + 1;
                                break;
                            }
                        }
                        
                        // Suche nach öffnenden Anführungszeichen im Satz
                        for (int j = sentenceStart; j < i; j++) {
                            char c = result.charAt(j);
                            if (c == '\u00AB' || c == '\u203A') { // « oder ›
                                hasOpeningQuoteInSentence = true;
                                break;
                            }
                        }
                    }
                    
                    if (!(hasLetterBefore && hasLetterAfter) && !(isPossessiveApostrophe && !hasOpeningQuoteInSentence)) {
                        // NICHT Buchstabe davor UND dahinter -> zu französischem Anführungszeichen konvertieren
                        // Bestimme ob öffnend oder schließend basierend auf Kontext
                        boolean shouldBeClosing = false;
                        if (i > 0) {
                            char charBefore = result.charAt(i - 1);
                            if (Character.isLetterOrDigit(charBefore) || charBefore == '.' || charBefore == '…' || 
                                charBefore == '!' || charBefore == '?' || charBefore == ',' || charBefore == ';' || 
                                charBefore == ':' || charBefore == ')') {
                                shouldBeClosing = true;
                            }
                        }
                        
                        // Französisch: Bestimme ob doppelt oder einfach basierend auf Kontext
                        // Wenn nach Leerzeichen oder Satzzeichen -> doppelt, sonst einfach
                        boolean shouldBeDouble = false;
                        if (i > 0) {
                            char charBefore = result.charAt(i - 1);
                            if (charBefore == ' ' || charBefore == '\n' || charBefore == '\t' || 
                                charBefore == '.' || charBefore == '!' || charBefore == '?' || 
                                charBefore == ',' || charBefore == ';' || charBefore == ':' || 
                                charBefore == ')' || charBefore == ']' || charBefore == '}') {
                                shouldBeDouble = true;
                            }
                        }
                        
                        char replacement;
                        if (shouldBeDouble) {
                            // Doppelte Anführungszeichen: » (schließend) oder « (öffnend)
                            replacement = shouldBeClosing ? '\u00BB' : '\u00AB'; // » oder «
                        } else {
                            // Einfache Anführungszeichen: › (öffnend) oder ‹ (schließend)
                            replacement = shouldBeClosing ? '\u2039' : '\u203A'; // ‹ oder ›
                        }
                        result.setCharAt(i, replacement);
                        hasChanges = true;
                    }
                }
            }
        }
        
        // Für schweizer Modus (Index 3): ‹ und ›
        if (currentQuoteStyleIndex == 3) {
            // Überprüfe ‹ (U+2039) - schweizer öffnendes einfaches Anführungszeichen
            for (int i = 0; i < result.length(); i++) {
                if (result.charAt(i) == '\u2039') { // ‹
                    // Prüfe ob ein Buchstabe davor UND dahinter steht
                    boolean hasLetterBefore = i > 0 && Character.isLetter(result.charAt(i - 1));
                    boolean hasLetterAfter = i + 1 < result.length() && Character.isLetter(result.charAt(i + 1));
                    
                    if (hasLetterBefore && hasLetterAfter) {
                        // Buchstabe davor UND dahinter -> zu Apostroph konvertieren
                        result.setCharAt(i, '\'');
                        hasChanges = true;
                    }
                }
            }
            
            // Überprüfe › (U+203A) - schweizer schließendes einfaches Anführungszeichen
            for (int i = 0; i < result.length(); i++) {
                if (result.charAt(i) == '\u203A') { // ›
                    // Prüfe ob ein Buchstabe davor UND dahinter steht
                    boolean hasLetterBefore = i > 0 && Character.isLetter(result.charAt(i - 1));
                    boolean hasLetterAfter = i + 1 < result.length() && Character.isLetter(result.charAt(i + 1));
                    
                    if (hasLetterBefore && hasLetterAfter) {
                        // Buchstabe davor UND dahinter -> zu Apostroph konvertieren
                        result.setCharAt(i, '\'');
                        hasChanges = true;
                    }
                }
            }
            
            // Überprüfe Apostrophe - konvertiere zu Anführungszeichen wenn NICHT Buchstabe davor UND dahinter
            for (int i = 0; i < result.length(); i++) {
                if (result.charAt(i) == '\'') {
                    // Prüfe ob ein Buchstabe davor UND dahinter steht
                    boolean hasLetterBefore = i > 0 && Character.isLetter(result.charAt(i - 1));
                    boolean hasLetterAfter = i + 1 < result.length() && Character.isLetter(result.charAt(i + 1));
                    
                    if (!(hasLetterBefore && hasLetterAfter)) {
                        // NICHT Buchstabe davor UND dahinter -> zu schweizer Anführungszeichen konvertieren
                        // Bestimme ob öffnend oder schließend basierend auf Kontext
                        boolean shouldBeClosing = false;
                        if (i > 0) {
                            char charBefore = result.charAt(i - 1);
                            if (Character.isLetterOrDigit(charBefore) || charBefore == '.' || charBefore == '…' || 
                                charBefore == '!' || charBefore == '?' || charBefore == ',' || charBefore == ';' || 
                                charBefore == ':' || charBefore == ')') {
                                shouldBeClosing = true;
                            }
                        }
                        
                        char replacement = shouldBeClosing ? '\u203A' : '\u2039'; // › oder ‹
                        result.setCharAt(i, replacement);
                        hasChanges = true;
                    }
                }
            }
        }
        
        // Wende Änderungen an wenn welche gefunden wurden
        if (hasChanges) {
            String newText = result.toString();
            if (!text.equals(newText)) {
                // Speichere Cursor-Position
                int caretPosition = codeArea.getCaretPosition();
                
                // Ersetze den Text
                codeArea.replaceText(newText);
                
                // Stelle Cursor-Position wieder her
                if (caretPosition <= newText.length()) {
                    codeArea.moveTo(caretPosition);
                } else {
                    codeArea.moveTo(newText.length());
                }
            }
        }
        
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
    
    private void showSearchPanel() {
        if (!searchPanelVisible) {
            searchPanelVisible = true;
            searchReplacePanel.setVisible(true);
            searchReplacePanel.setManaged(true);
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
            
            // Wenn sich der Suchtext geändert hat, cache neu aufbauen und von vorne anfangen
            if (!searchText.equals(lastSearchText)) {
                totalMatches = 0;
                currentMatchIndex = -1;
                lastSearchText = searchText;
                cachedPattern = pattern;
                cachedMatchPositions.clear();
                
                // Cursor an den Anfang setzen für neue Suche
                codeArea.displaceCaret(0);
                
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
                
                // Bei neuer Suche sofort zum ersten Treffer springen
                if (totalMatches > 0) {
                    // Springe zum ersten Treffer
                    int firstMatchStart = cachedMatchPositions.get(0);
                    Matcher firstMatcher = cachedPattern.matcher(content);
                    if (firstMatcher.find(firstMatchStart)) {
                        highlightText(firstMatcher.start(), firstMatcher.end());
                        currentMatchIndex = 0;
                        updateMatchCount(currentMatchIndex + 1, totalMatches);
                        updateStatus("Treffer " + (currentMatchIndex + 1) + " von " + totalMatches);
                    }
                } else {
                    updateMatchCount(0, 0);
                    updateStatus("Keine Treffer gefunden");
                }
                return;
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
        
        // EINGEBAUTE UNDO-FUNKTIONALITÄT VERWENDEN - kein manueller Aufruf nötig
        
        try {
            Pattern pattern = createSearchPattern(searchText.trim());
            String content = codeArea.getText();
            Matcher matcher = pattern.matcher(content);
            
            // Verwende die Position des aktuell markierten Treffers
            int matchStart = -1;
            int matchEnd = -1;
            
            if (currentMatchIndex >= 0 && currentMatchIndex < cachedMatchPositions.size()) {
                // Verwende die Position des aktuell markierten Treffers
                matchStart = cachedMatchPositions.get(currentMatchIndex);
                matcher.find(matchStart);
                matchEnd = matcher.end();
            } else {
                // Fallback: Suche ab der aktuellen Cursor-Position
            int start = codeArea.getCaretPosition();
            if (matcher.find(start)) {
                    matchStart = matcher.start();
                    matchEnd = matcher.end();
                }
            }
            
            if (matchStart >= 0) {
                // Für Regex-Ersetzung müssen wir die Backreferences manuell ersetzen
                String replacement;
                if (chkRegexSearch.isSelected() && replaceText != null && replaceText.contains("\\")) {
                    // Java uses $1, $2, etc. for backreferences, not \1, \2
                    String replaceTextJava = replaceText.replace("\\1", "$1").replace("\\2", "$2").replace("\\3", "$3").replace("\\4", "$4").replace("\\5", "$5");
                    
                    // Ersetze \n durch echte Zeilenumbrüche
                    replaceTextJava = replaceTextJava.replace("\\n", "\n");
                    
                    // Erstelle einen neuen Matcher für die Ersetzung des gefundenen Teils
                    String foundText = content.substring(matcher.start(), matcher.end());
                    Pattern replacePattern = Pattern.compile(pattern.pattern(), pattern.flags());
                    Matcher replaceMatcher = replacePattern.matcher(foundText);
                    replacement = replaceMatcher.replaceFirst(replaceTextJava);
                    
                    // Debug entfernt
                } else {
                    replacement = replaceText != null ? replaceText : "";
                }
                
                codeArea.replaceText(matchStart, matchEnd, replacement);
                updateStatus("Ersetzt");
                
                // Cache komplett zurücksetzen, da sich der Text geändert hat
                lastSearchText = null;
                cachedMatchPositions.clear();
                totalMatches = 0;
                currentMatchIndex = -1;
            }
            
        } catch (Exception e) {
            updateStatusError("Fehler beim Ersetzen: " + e.getMessage());
        }
    }
    
    private void replaceAllText() {
        String searchText = cmbSearchHistory.getValue();
        String replaceText = cmbReplaceHistory.getValue();
        
        if (searchText == null || searchText.trim().isEmpty()) return;
        
        // EINGEBAUTE UNDO-FUNKTIONALITÄT VERWENDEN - kein manueller Aufruf nötig
        
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
            
            // Ersetze \n durch echte Zeilenumbrüche
            replaceTextJava = replaceTextJava.replace("\\n", "\n");
            replacement = matcher.replaceAll(replaceTextJava);
            
            // Debug entfernt
            
            // Ersetze den gesamten Inhalt
            codeArea.clear();
            codeArea.appendText(replacement);
            
            // Visuelles Update erzwingen
            Platform.runLater(() -> {
                codeArea.requestLayout();
                codeArea.requestFocus();
            });
            
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
            
            // RichTextFX: Zeige den gefundenen Text in der Mitte des Fensters
            int currentParagraph = codeArea.getCurrentParagraph();
            codeArea.showParagraphAtCenter(currentParagraph);
            
            // Focus zurück zum Suchfeld, damit ENTER weiter funktioniert
            Platform.runLater(() -> {
                cmbSearchHistory.requestFocus();
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

    public void pushSearchTermAndHighlight(String text) {
        if (text == null) {
            return;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        boolean wasVisible = searchPanelVisible;
        if (!searchPanelVisible) {
            toggleSearchPanel();
        }

        boolean found = applySearchTerm(trimmed);
        if (!found) {
            updateStatus("Der markierte Text wurde nicht im Editor gefunden.");
        }

        if (!wasVisible) {
            Platform.runLater(() -> cmbSearchHistory.getEditor().selectAll());
        }
    }

    private boolean applySearchTerm(String term) {
        String trimmed = term.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        addToSearchHistory(trimmed);
        cmbSearchHistory.setValue(trimmed);
        findText();
        return totalMatches > 0;
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
    
    public void updateStatus(String message) {
        lblStatus.setText(message);
        // Normale Farbe setzen, außer wenn es "nicht gesichert" ist
        if (!message.contains("nicht gesichert")) {
            lblStatus.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 11px;"); // Normale Farbe
        }
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
        }
    }
    
    
    
    // Export-Dialog - EXAKT WIE FUNKTIONIERENDER TEST-DIALOG
    private void showExportDialog() {
        if (outputFormat != DocxProcessor.OutputFormat.MARKDOWN) {
            updateStatus("Export nur für Markdown-Dokumente verfügbar");
            return;
        }
        
        CustomStage exportStage = StageManager.createExportStage("Export", stage);
        exportStage.setTitle("📤 Exportieren");
        exportStage.initModality(Modality.APPLICATION_MODAL);
        exportStage.initOwner(stage);
        
        // CSS-Styles für den Dialog anwenden (nach der Scene-Initialisierung)
        Platform.runLater(() -> {
            try {
                String cssPath = ResourceManager.getCssResource("css/manuskript.css");
                if (exportStage.getScene() != null) {
                    exportStage.getScene().getStylesheets().add(cssPath);
                }
                
                // Theme für den Dialog setzen
                exportStage.setFullTheme(currentThemeIndex);
            } catch (Exception e) {
                logger.error("Fehler beim Anwenden der CSS-Styles für Export-Dialog", e);
            }
        });
        
        // Hauptcontainer
        VBox exportContent = new VBox(15);
        exportContent.setPadding(new Insets(20));
        
        // Export-Formate
        Label formatLabel = new Label("📄 Export-Formate (mehrere möglich):");
        formatLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        
        VBox formatOptions = new VBox(8);
        
        CheckBox rtfCheck = new CheckBox("📄 RTF (Rich Text Format) - Für Word, LibreOffice");
        
        // DOCX mit Optionen-Button
        HBox docxBox = new HBox(10);
        docxBox.setAlignment(Pos.CENTER_LEFT);
        CheckBox docxCheck = new CheckBox("📄 DOCX (Microsoft Word) - Für Word, LibreOffice");
        Button docxOptionsBtn = new Button("⚙️ DOCX Optionen");
        docxOptionsBtn.setStyle("-fx-font-size: 11px; -fx-padding: 4px 8px;");
        
        // Erst Button zum Layout hinzufügen
        docxBox.getChildren().addAll(docxCheck, docxOptionsBtn);
        
        // Dann Event setzen
        docxOptionsBtn.setOnAction(e -> {
            if (docxCheck.isSelected()) {
                showDocxOptionsDialog(docxCheck);
            } else {
                // Warnung anzeigen
                CustomAlert alert = new CustomAlert(Alert.AlertType.WARNING, "Warnung");
                alert.setHeaderText("DOCX nicht ausgewählt");
                alert.setContentText("Bitte aktivieren Sie zuerst die DOCX-Option, um die Einstellungen zu bearbeiten.");
                alert.applyTheme(currentThemeIndex);
                alert.initOwner(stage);
                alert.showAndWait();
            }
        });
        
        CheckBox htmlCheck = new CheckBox("🌐 HTML (Web-Format) - Für Browser, E-Mail");
        CheckBox txtCheck = new CheckBox("📝 TXT (Plain Text) - Einfacher Text");
        CheckBox mdCheck = new CheckBox("📝 MD (Markdown) - Für andere Markdown-Editoren");
        CheckBox pdfCheck = new CheckBox("📄 PDF (Portable Document) - Für Druck, Teilen");
        
        // Standard: DOCX
        docxCheck.setSelected(true);
        
        formatOptions.getChildren().addAll(rtfCheck, docxBox, htmlCheck, txtCheck, mdCheck, pdfCheck);
        
        // Verzeichnis-Optionen
        Label dirLabel = new Label("📁 Export-Verzeichnis:");
        dirLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        
        VBox dirOptions = new VBox(8);
        
        CheckBox createDirCheck = new CheckBox("Verzeichnis für Export anlegen (falls nicht vorhanden)");
        createDirCheck.setSelected(true);
        
        HBox dirSelection = new HBox(10);
        dirSelection.setAlignment(Pos.CENTER_LEFT);
        
        Label dirPathLabel = new Label("Unterverzeichnis:");
        dirPathLabel.setStyle("-fx-font-weight: bold;");
        
        TextField dirPathField = new TextField();
        dirPathField.setPromptText("Unterverzeichnis (optional) eingeben...");
        dirPathField.setEditable(true);
        dirPathField.setPrefWidth(400);
        
        dirSelection.getChildren().addAll(dirPathLabel, dirPathField);
        
        dirOptions.getChildren().addAll(createDirCheck, dirSelection);
        
        // Dateiname
        Label filenameLabel = new Label("📄 Dateiname:");
        filenameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        
        HBox filenameBox = new HBox(10);
        filenameBox.setAlignment(Pos.CENTER_LEFT);
        
        Label filenamePathLabel = new Label("Name:");
        filenamePathLabel.setStyle("-fx-font-weight: bold;");
        
        TextField filenameField = new TextField();
        filenameField.setPromptText("Dateiname eingeben...");
        filenameField.setPrefWidth(300);
        
        // Standard-Dateiname setzen
        if (currentFile != null) {
            String baseName = currentFile.getName();
            int lastDot = baseName.lastIndexOf('.');
            if (lastDot > 0) {
                baseName = baseName.substring(0, lastDot);
            }
            filenameField.setText(baseName);
        } else {
            filenameField.setText("manuskript");
        }
        
        filenameBox.getChildren().addAll(filenamePathLabel, filenameField);
        
        // Alles zusammenfügen
        exportContent.getChildren().addAll(
            formatLabel, formatOptions,
            new Separator(),
            dirLabel, dirOptions,
            new Separator(),
            filenameLabel, filenameBox
        );
        
        // Buttons für CustomStage
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new Insets(20, 0, 0, 0));
        buttonBox.getStyleClass().add("export-dialog-buttons");
        
        Button exportButton = new Button("📤 Exportieren");
        exportButton.setDefaultButton(true);
        Button cancelButton = new Button("❌ Abbrechen");
        cancelButton.setCancelButton(true);
        
        // Button für "In Zwischenablage kopieren" hinzufügen
        Button copyToClipboardButton = new Button("📋 In Zwischenablage kopieren");
        copyToClipboardButton.setOnAction(e -> {
            try {
                // Markdown zu HTML konvertieren
                String markdownContent = cleanTextForExport(codeArea.getText());
                String htmlContent = convertMarkdownToHTML(markdownContent);
                
                // HTML in Zwischenablage kopieren
                Clipboard clipboard = Clipboard.getSystemClipboard();
                ClipboardContent content = new ClipboardContent();
                content.putHtml(htmlContent);
                content.putString(convertMarkdownToPlainText(markdownContent)); // Fallback als Plain Text
                clipboard.setContent(content);
                
                updateStatus("✅ Kapitel in Zwischenablage kopiert (mit Formatierung)");
                exportStage.close();
                
            } catch (Exception ex) {
                updateStatusError("Fehler beim Kopieren in Zwischenablage: " + ex.getMessage());
                logger.error("Fehler beim Kopieren in Zwischenablage", ex);
            }
        });
        
        buttonBox.getChildren().addAll(cancelButton, copyToClipboardButton, exportButton);
        
        // Theme auf Button-Box anwenden
        applyThemeToNode(buttonBox, currentThemeIndex);
        
        // Content mit Buttons kombinieren
        VBox mainContent = new VBox(15);
        mainContent.getChildren().addAll(exportContent, buttonBox);
        mainContent.setPadding(new Insets(20));
        mainContent.getStyleClass().add("export-dialog-content");
        // Theme-Hintergrund direkt setzen
        String[] themeBackgrounds = {"#ffffff", "#1a1a1a", "#f3e5f5", "#1e3a8a", "#064e3b", "#581c87"};
        mainContent.setStyle("-fx-background-color: " + themeBackgrounds[currentThemeIndex] + ";");
        
        // Theme auf den Content anwenden
        applyThemeToNode(mainContent, currentThemeIndex);
        
        // Event-Handler für Buttons
        cancelButton.setOnAction(e -> {
            exportStage.close();
        });
        
        exportButton.setOnAction(e -> {
                // Alle ausgewählten Formate sammeln
                List<ExportFormat> selectedFormats = new ArrayList<>();
                if (rtfCheck.isSelected()) selectedFormats.add(ExportFormat.RTF);
                if (docxCheck.isSelected()) selectedFormats.add(ExportFormat.DOCX);
                if (htmlCheck.isSelected()) selectedFormats.add(ExportFormat.HTML);
                if (txtCheck.isSelected()) selectedFormats.add(ExportFormat.TXT);
                if (mdCheck.isSelected()) selectedFormats.add(ExportFormat.MD);
                if (pdfCheck.isSelected()) selectedFormats.add(ExportFormat.PDF);
                
            ExportResult result = new ExportResult(
            selectedFormats,
            dirPathField.getText(),
            filenameField.getText(),
            createDirCheck.isSelected(),
                globalDocxOptions
            );
            
            // Export direkt durchführen
            exportStage.close();
            performExport(result);
        });
        
        // SCENE SETZEN - EXAKT WIE FUNKTIONIERENDER TEST-DIALOG
        Scene scene = new Scene(mainContent);
        exportStage.setSceneWithTitleBar(scene);
        
        // CSS-Stylesheets laden - EXAKT WIE FUNKTIONIERENDER TEST-DIALOG
        String stylesCss = ResourceManager.getCssResource("css/styles.css");
        String editorCss = ResourceManager.getCssResource("css/editor.css");
        if (stylesCss != null && !scene.getStylesheets().contains(stylesCss)) scene.getStylesheets().add(stylesCss);
        if (editorCss != null && !scene.getStylesheets().contains(editorCss)) scene.getStylesheets().add(editorCss);
        
        // Theme auf die Stage anwenden - EXAKT WIE FUNKTIONIERENDER TEST-DIALOG
        applyThemeToNode(scene.getRoot(), currentThemeIndex);
        
        // DIALOG ANZEIGEN - EXAKT WIE FUNKTIONIERENDER TEST-DIALOG
        exportStage.showAndWait();
    }
    
    // Export-Format Enum
    private enum ExportFormat {
        RTF, DOCX, HTML, TXT, MD, PDF
    }
    
    // Export-Ergebnis Klasse
    private static class ExportResult {
        final List<ExportFormat> formats;
        final String directory;
        final String filename;
        final boolean createDirectory;
        final DocxOptions docxOptions; // Neue DOCX-Optionen
        
        ExportResult(List<ExportFormat> formats, String directory, String filename, 
                    boolean createDirectory, DocxOptions docxOptions) {
            this.formats = formats;
            this.directory = directory;
            this.filename = filename;
            this.createDirectory = createDirectory;
            this.docxOptions = docxOptions;
        }
    }
    

    
    // Export durchführen
    private void performExport(ExportResult result) {
        try {
            // DirectoryChooser öffnen
            DirectoryChooser dirChooser = new DirectoryChooser();
            dirChooser.setTitle("Export-Verzeichnis wählen");
            
            // Lade das letzte Verzeichnis
            String lastDirectory = preferences.get("lastExportDirectory", null);
            if (lastDirectory != null) {
                File dir = new File(lastDirectory);
                if (dir.exists() && dir.isDirectory()) {
                    dirChooser.setInitialDirectory(dir);
                }
            }
            
            File selectedDir = dirChooser.showDialog(stage);
            if (selectedDir == null) {
                updateStatus("Export abgebrochen");
                return;
            }
            
            // Verzeichnis für Export speichern
            preferences.put("lastExportDirectory", selectedDir.getAbsolutePath());
            
            // Zielverzeichnis bestimmen
            File exportDir;
            if (result.directory != null && !result.directory.trim().isEmpty()) {
                // Unterverzeichnis im gewählten Verzeichnis erstellen
                exportDir = new File(selectedDir, result.directory.trim());
                if (result.createDirectory && !exportDir.exists()) {
                    if (exportDir.mkdirs()) {
                        updateStatus("Unterverzeichnis erstellt: " + exportDir.getAbsolutePath());
        } else {
                        updateStatusError("Konnte Unterverzeichnis nicht erstellen: " + exportDir.getAbsolutePath());
                        return;
                    }
                }
            } else {
                // Direkt in das gewählte Verzeichnis exportieren
                exportDir = selectedDir;
            }
            
            // Export durchführen
            String markdownContent = cleanTextForExport(codeArea.getText());
            
            // Alle ausgewählten Formate exportieren
            for (ExportFormat format : result.formats) {
                String extension = getExtensionForFormat(format);
                String fullFilename = result.filename + "." + extension;
                File exportFile = new File(exportDir, fullFilename);
                
                switch (format) {
                    case RTF:
                        exportToRTF(markdownContent, exportFile, result);
                        break;
                    case DOCX:
                        exportToDOCX(markdownContent, exportFile, result);
                        break;
                    case HTML:
                        exportToHTML(markdownContent, exportFile, result);
                        break;
                    case TXT:
                        exportToTXT(markdownContent, exportFile, result);
                        break;
                    case MD:
                        exportToMD(markdownContent, exportFile, result);
                        break;
                    case PDF:
                        exportToPDF(markdownContent, exportFile, result);
                        break;
                }
            }
            
            updateStatus("Export erfolgreich: " + result.formats.size() + " Formate in " + exportDir.getAbsolutePath());
            
        } catch (Exception e) {
            updateStatusError("Fehler beim Export: " + e.getMessage());
            logger.error("Export-Fehler", e);
        }
    }
    
    // Hilfsmethoden für Export
    private String getExtensionForFormat(ExportFormat format) {
        switch (format) {
            case RTF: return "rtf";
            case DOCX: return "docx";
            case HTML: return "html";
            case TXT: return "txt";
            case MD: return "md";
            case PDF: return "pdf";
            default: return "txt";
        }
    }
    // DOCX-Optionen Dialog
    private void showDocxOptionsDialog(CheckBox docxCheck) {
        CustomStage optionsStage = StageManager.createModalStage("DocX-Optionen", stage);
        optionsStage.setTitle("⚙️ DOCX Export Optionen");
        optionsStage.initModality(Modality.APPLICATION_MODAL);
        optionsStage.initOwner(stage);
        
        // CSS-Styles für den Dialog anwenden (nach der Scene-Initialisierung)
        Platform.runLater(() -> {
            try {
                String cssPath = ResourceManager.getCssResource("css/manuskript.css");
                if (optionsStage.getScene() != null) {
                    optionsStage.getScene().getStylesheets().add(cssPath);
                }
                
                // Theme für den Dialog setzen
                optionsStage.setTitleBarTheme(currentThemeIndex);
            } catch (Exception e) {
                logger.error("Fehler beim Anwenden der CSS-Styles für DOCX-Options-Dialog", e);
            }
        });
        
        // Hauptcontainer mit ScrollPane
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefWidth(800);
        scrollPane.setPrefHeight(700);
        scrollPane.getStyleClass().add("docx-options-scroll-pane");
        
        VBox mainContent = new VBox(20);
        mainContent.setPadding(new Insets(20));
        mainContent.getStyleClass().add("docx-options-content");
        
        // DOCX-Optionen Objekt - verwende das globale Objekt
        DocxOptions options = globalDocxOptions;
        
        // === SCHRIFTARTEN ===
        VBox fontsSection = createSection("🔤 Schriftarten & Größen", "Schriftarten und Größen für verschiedene Textelemente");
        
        // Standard-Schriftart
        HBox defaultFontBox = new HBox(10);
        defaultFontBox.setAlignment(Pos.CENTER_LEFT);
        Label defaultFontLabel = new Label("Standard-Schriftart:");
        ComboBox<String> defaultFontCombo = new ComboBox<>();
        defaultFontCombo.getItems().addAll("Calibri", "Arial", "Times New Roman", "Cambria", "Segoe UI", "Verdana", "Georgia", "Tahoma");
        defaultFontCombo.setValue(options.defaultFont);
        defaultFontCombo.setOnAction(e -> options.defaultFont = defaultFontCombo.getValue());
        defaultFontBox.getChildren().addAll(defaultFontLabel, defaultFontCombo);
        
        // Überschriften-Schriftart
        HBox headingFontBox = new HBox(10);
        headingFontBox.setAlignment(Pos.CENTER_LEFT);
        Label headingFontLabel = new Label("Überschriften-Schriftart:");
        ComboBox<String> headingFontCombo = new ComboBox<>();
        headingFontCombo.getItems().addAll("Calibri", "Arial", "Times New Roman", "Cambria", "Segoe UI", "Verdana", "Georgia", "Tahoma");
        headingFontCombo.setValue(options.headingFont);
        headingFontCombo.setOnAction(e -> options.headingFont = headingFontCombo.getValue());
        headingFontBox.getChildren().addAll(headingFontLabel, headingFontCombo);
        
        // Code-Schriftart
        HBox codeFontBox = new HBox(10);
        codeFontBox.setAlignment(Pos.CENTER_LEFT);
        Label codeFontLabel = new Label("Code-Schriftart:");
        ComboBox<String> codeFontCombo = new ComboBox<>();
        codeFontCombo.getItems().addAll("Consolas", "Courier New", "Lucida Console", "Monaco", "Menlo", "Source Code Pro");
        codeFontCombo.setValue(options.codeFont);
        codeFontCombo.setOnAction(e -> options.codeFont = codeFontCombo.getValue());
        codeFontBox.getChildren().addAll(codeFontLabel, codeFontCombo);
        
        // Schriftgrößen
        HBox fontSizeBox = new HBox(20);
        fontSizeBox.setAlignment(Pos.CENTER_LEFT);
        
        VBox defaultSizeBox = new VBox(5);
        Label defaultSizeLabel = new Label("Standard:");
        Spinner<Integer> defaultSizeSpinner = new Spinner<>(8, 20, options.defaultFontSize);
        defaultSizeSpinner.setEditable(true);
        defaultSizeSpinner.valueProperty().addListener((obs, oldVal, newVal) -> options.defaultFontSize = newVal);
        defaultSizeBox.getChildren().addAll(defaultSizeLabel, defaultSizeSpinner);
        
        VBox h1SizeBox = new VBox(5);
        Label h1SizeLabel = new Label("H1:");
        Spinner<Integer> h1SizeSpinner = new Spinner<>(12, 36, options.heading1Size);
        h1SizeSpinner.setEditable(true);
        h1SizeSpinner.valueProperty().addListener((obs, oldVal, newVal) -> options.heading1Size = newVal);
        h1SizeBox.getChildren().addAll(h1SizeLabel, h1SizeSpinner);
        
        VBox h2SizeBox = new VBox(5);
        Label h2SizeLabel = new Label("H2:");
        Spinner<Integer> h2SizeSpinner = new Spinner<>(10, 32, options.heading2Size);
        h2SizeSpinner.setEditable(true);
        h2SizeSpinner.valueProperty().addListener((obs, oldVal, newVal) -> options.heading2Size = newVal);
        h2SizeBox.getChildren().addAll(h2SizeLabel, h2SizeSpinner);
        
        VBox h3SizeBox = new VBox(5);
        Label h3SizeLabel = new Label("H3:");
        Spinner<Integer> h3SizeSpinner = new Spinner<>(9, 28, options.heading3Size);
        h3SizeSpinner.setEditable(true);
        h3SizeSpinner.valueProperty().addListener((obs, oldVal, newVal) -> options.heading3Size = newVal);
        h3SizeBox.getChildren().addAll(h3SizeLabel, h3SizeSpinner);
        
        fontSizeBox.getChildren().addAll(defaultSizeBox, h1SizeBox, h2SizeBox, h3SizeBox);
        
        fontsSection.getChildren().addAll(defaultFontBox, headingFontBox, codeFontBox, fontSizeBox);
        
        // === ABSATZFORMATIERUNG ===
        VBox paragraphSection = createSection("📝 Absatzformatierung", "Formatierung von Absätzen und Text");
        
        CheckBox justifyCheck = new CheckBox("Blocksatz verwenden");
        justifyCheck.setSelected(options.justifyText);
        justifyCheck.setOnAction(e -> options.justifyText = justifyCheck.isSelected());
        
        CheckBox hyphenationCheck = new CheckBox("Silbentrennung aktivieren");
        hyphenationCheck.setSelected(options.enableHyphenation);
        hyphenationCheck.setOnAction(e -> options.enableHyphenation = hyphenationCheck.isSelected());
        
        CheckBox firstLineIndentCheck = new CheckBox("Erste Zeile einrücken");
        firstLineIndentCheck.setSelected(options.firstLineIndent);
        firstLineIndentCheck.setOnAction(e -> options.firstLineIndent = firstLineIndentCheck.isSelected());
        
        HBox spacingBox = new HBox(20);
        spacingBox.setAlignment(Pos.CENTER_LEFT);
        
        VBox lineSpacingBox = new VBox(5);
        Label lineSpacingLabel = new Label("Zeilenabstand:");
        Spinner<Double> lineSpacingSpinner = new Spinner<>(1.0, 3.0, options.lineSpacing, 0.05);
        lineSpacingSpinner.setEditable(true);
        lineSpacingSpinner.valueProperty().addListener((obs, oldVal, newVal) -> options.lineSpacing = newVal);
        lineSpacingBox.getChildren().addAll(lineSpacingLabel, lineSpacingSpinner);
        
        VBox paragraphSpacingBox = new VBox(5);
        Label paragraphSpacingLabel = new Label("Absatzabstand:");
        Spinner<Double> paragraphSpacingSpinner = new Spinner<>(0.5, 3.0, options.paragraphSpacing, 0.1);
        paragraphSpacingSpinner.setEditable(true);
        paragraphSpacingSpinner.valueProperty().addListener((obs, oldVal, newVal) -> options.paragraphSpacing = newVal);
        paragraphSpacingBox.getChildren().addAll(paragraphSpacingLabel, paragraphSpacingSpinner);
        
        VBox firstLineIndentBox = new VBox(5);
        Label firstLineIndentLabel = new Label("Einrückung (cm):");
        Spinner<Double> firstLineIndentSpinner = new Spinner<>(0.5, 3.0, options.firstLineIndentSize, 0.1);
        firstLineIndentSpinner.setEditable(true);
        firstLineIndentSpinner.valueProperty().addListener((obs, oldVal, newVal) -> options.firstLineIndentSize = newVal);
        firstLineIndentBox.getChildren().addAll(firstLineIndentLabel, firstLineIndentSpinner);
        
        spacingBox.getChildren().addAll(lineSpacingBox, paragraphSpacingBox, firstLineIndentBox);
        
        paragraphSection.getChildren().addAll(justifyCheck, hyphenationCheck, firstLineIndentCheck, spacingBox);
        
        // === ÜBERSCHRIFTEN ===
        VBox headingsSection = createSection("📋 Überschriften", "Formatierung und Verhalten von Überschriften");
        
        CheckBox centerHeadersCheck = new CheckBox("Überschriften zentrieren");
        centerHeadersCheck.setSelected(options.centerH1);
        centerHeadersCheck.setOnAction(e -> options.centerH1 = centerHeadersCheck.isSelected());
        
        CheckBox newPageH1Check = new CheckBox("Neue Seite vor H1");
        newPageH1Check.setSelected(options.newPageBeforeH1);
        newPageH1Check.setOnAction(e -> options.newPageBeforeH1 = newPageH1Check.isSelected());
        
        CheckBox newPageH2Check = new CheckBox("Neue Seite vor H2");
        newPageH2Check.setSelected(options.newPageBeforeH2);
        newPageH2Check.setOnAction(e -> options.newPageBeforeH2 = newPageH2Check.isSelected());
        
        CheckBox boldHeadingsCheck = new CheckBox("Überschriften fett");
        boldHeadingsCheck.setSelected(options.boldHeadings);
        boldHeadingsCheck.setOnAction(e -> options.boldHeadings = boldHeadingsCheck.isSelected());
        
        HBox headingColorBox = new HBox(10);
        headingColorBox.setAlignment(Pos.CENTER_LEFT);
        Label headingColorLabel = new Label("Überschriften-Farbe:");
        ColorPicker headingColorPicker = new ColorPicker(Color.web(options.headingColor));
        headingColorPicker.setOnAction(e -> options.headingColor = String.format("%02X%02X%02X", 
            (int)(headingColorPicker.getValue().getRed() * 255),
            (int)(headingColorPicker.getValue().getGreen() * 255),
            (int)(headingColorPicker.getValue().getBlue() * 255)));
        headingColorBox.getChildren().addAll(headingColorLabel, headingColorPicker);
        
        headingsSection.getChildren().addAll(centerHeadersCheck, newPageH1Check, newPageH2Check, boldHeadingsCheck, headingColorBox);
        
        // === SEITENFORMAT ===
        VBox pageSection = createSection("📄 Seitenformat", "Seitenränder und Seitenzahlen");
        
        HBox marginsBox = new HBox(20);
        marginsBox.setAlignment(Pos.CENTER_LEFT);
        
        VBox topMarginBox = new VBox(5);
        Label topMarginLabel = new Label("Oberer Rand (cm):");
        Spinner<Double> topMarginSpinner = new Spinner<>(1.0, 5.0, options.topMargin, 0.1);
        topMarginSpinner.setEditable(true);
        topMarginSpinner.valueProperty().addListener((obs, oldVal, newVal) -> options.topMargin = newVal);
        topMarginBox.getChildren().addAll(topMarginLabel, topMarginSpinner);
        
        VBox bottomMarginBox = new VBox(5);
        Label bottomMarginLabel = new Label("Unterer Rand (cm):");
        Spinner<Double> bottomMarginSpinner = new Spinner<>(1.0, 5.0, options.bottomMargin, 0.1);
        bottomMarginSpinner.setEditable(true);
        bottomMarginSpinner.valueProperty().addListener((obs, oldVal, newVal) -> options.bottomMargin = newVal);
        bottomMarginBox.getChildren().addAll(bottomMarginLabel, bottomMarginSpinner);
        
        VBox leftMarginBox = new VBox(5);
        Label leftMarginLabel = new Label("Linker Rand (cm):");
        Spinner<Double> leftMarginSpinner = new Spinner<>(1.0, 5.0, options.leftMargin, 0.1);
        leftMarginSpinner.setEditable(true);
        leftMarginSpinner.valueProperty().addListener((obs, oldVal, newVal) -> options.leftMargin = newVal);
        leftMarginBox.getChildren().addAll(leftMarginLabel, leftMarginSpinner);
        
        VBox rightMarginBox = new VBox(5);
        Label rightMarginLabel = new Label("Rechter Rand (cm):");
        Spinner<Double> rightMarginSpinner = new Spinner<>(1.0, 5.0, options.rightMargin, 0.1);
        rightMarginSpinner.setEditable(true);
        rightMarginSpinner.valueProperty().addListener((obs, oldVal, newVal) -> options.rightMargin = newVal);
        rightMarginBox.getChildren().addAll(rightMarginLabel, rightMarginSpinner);
        
        marginsBox.getChildren().addAll(topMarginBox, bottomMarginBox, leftMarginBox, rightMarginBox);
        
        CheckBox pageNumbersCheck = new CheckBox("Seitenzahlen einfügen");
        pageNumbersCheck.setSelected(options.includePageNumbers);
        pageNumbersCheck.setOnAction(e -> options.includePageNumbers = pageNumbersCheck.isSelected());
        
        HBox pageNumberPosBox = new HBox(10);
        pageNumberPosBox.setAlignment(Pos.CENTER_LEFT);
        Label pageNumberPosLabel = new Label("Position:");
        ComboBox<String> pageNumberPosCombo = new ComboBox<>();
        pageNumberPosCombo.getItems().addAll("links", "zentriert", "rechts");
        pageNumberPosCombo.setValue(options.pageNumberPosition);
        pageNumberPosCombo.setOnAction(e -> options.pageNumberPosition = pageNumberPosCombo.getValue());
        pageNumberPosBox.getChildren().addAll(pageNumberPosLabel, pageNumberPosCombo);
        
        pageSection.getChildren().addAll(marginsBox, pageNumbersCheck, pageNumberPosBox);
        
        // === TABELLEN ===
        VBox tableSection = createSection("📊 Tabellen", "Formatierung von Tabellen");
        
        CheckBox tableBordersCheck = new CheckBox("Tabellen-Rahmen");
        tableBordersCheck.setSelected(options.tableBorders);
        tableBordersCheck.setOnAction(e -> options.tableBorders = tableBordersCheck.isSelected());
        
        HBox tableColorsBox = new HBox(20);
        tableColorsBox.setAlignment(Pos.CENTER_LEFT);
        
        VBox headerColorBox = new VBox(5);
        Label headerColorLabel = new Label("Header-Hintergrund:");
        ColorPicker headerColorPicker = new ColorPicker(Color.web(options.tableHeaderColor));
        headerColorPicker.setOnAction(e -> options.tableHeaderColor = String.format("%02X%02X%02X", 
            (int)(headerColorPicker.getValue().getRed() * 255),
            (int)(headerColorPicker.getValue().getGreen() * 255),
            (int)(headerColorPicker.getValue().getBlue() * 255)));
        headerColorBox.getChildren().addAll(headerColorLabel, headerColorPicker);
        
        VBox borderColorBox = new VBox(5);
        Label borderColorLabel = new Label("Rahmen-Farbe:");
        ColorPicker borderColorPicker = new ColorPicker(Color.web(options.tableBorderColor));
        borderColorPicker.setOnAction(e -> options.tableBorderColor = String.format("%02X%02X%02X", 
            (int)(borderColorPicker.getValue().getRed() * 255),
            (int)(borderColorPicker.getValue().getGreen() * 255),
            (int)(borderColorPicker.getValue().getBlue() * 255)));
        borderColorBox.getChildren().addAll(borderColorLabel, borderColorPicker);
        
        tableColorsBox.getChildren().addAll(headerColorBox, borderColorBox);
        
        tableSection.getChildren().addAll(tableBordersCheck, tableColorsBox);
        
        // === CODE-BLÖCKE ===
        VBox codeSection = createSection("💻 Code-Blöcke", "Formatierung von Code-Blöcken");
        
        HBox codeColorsBox = new HBox(20);
        codeColorsBox.setAlignment(Pos.CENTER_LEFT);
        
        VBox codeBgColorBox = new VBox(5);
        Label codeBgColorLabel = new Label("Hintergrund:");
        ColorPicker codeBgColorPicker = new ColorPicker(Color.web(options.codeBackgroundColor));
        codeBgColorPicker.setOnAction(e -> options.codeBackgroundColor = String.format("%02X%02X%02X", 
            (int)(codeBgColorPicker.getValue().getRed() * 255),
            (int)(codeBgColorPicker.getValue().getGreen() * 255),
            (int)(codeBgColorPicker.getValue().getBlue() * 255)));
        codeBgColorBox.getChildren().addAll(codeBgColorLabel, codeBgColorPicker);
        
        VBox codeBorderColorBox = new VBox(5);
        Label codeBorderColorLabel = new Label("Rahmen:");
        ColorPicker codeBorderColorPicker = new ColorPicker(Color.web(options.codeBorderColor));
        codeBorderColorPicker.setOnAction(e -> options.codeBorderColor = String.format("%02X%02X%02X", 
            (int)(codeBorderColorPicker.getValue().getRed() * 255),
            (int)(codeBorderColorPicker.getValue().getGreen() * 255),
            (int)(codeBorderColorPicker.getValue().getBlue() * 255)));
        codeBorderColorBox.getChildren().addAll(codeBorderColorLabel, codeBorderColorPicker);
        
        codeColorsBox.getChildren().addAll(codeBgColorBox, codeBorderColorBox);
        
        CheckBox lineNumbersCheck = new CheckBox("Zeilennummern anzeigen");
        lineNumbersCheck.setSelected(options.codeLineNumbers);
        lineNumbersCheck.setOnAction(e -> options.codeLineNumbers = lineNumbersCheck.isSelected());
        
        codeSection.getChildren().addAll(codeColorsBox, lineNumbersCheck);
        
        // === BLOCKQUOTES ===
        VBox quoteSection = createSection("💬 Blockquotes", "Formatierung von Zitaten");
        
        HBox quoteColorsBox = new HBox(20);
        quoteColorsBox.setAlignment(Pos.CENTER_LEFT);
        
        VBox quoteBorderColorBox = new VBox(5);
        Label quoteBorderColorLabel = new Label("Rahmen-Farbe:");
        ColorPicker quoteBorderColorPicker = new ColorPicker(Color.web(options.quoteBorderColor));
        quoteBorderColorPicker.setOnAction(e -> options.quoteBorderColor = String.format("%02X%02X%02X", 
            (int)(quoteBorderColorPicker.getValue().getRed() * 255),
            (int)(quoteBorderColorPicker.getValue().getGreen() * 255),
            (int)(quoteBorderColorPicker.getValue().getBlue() * 255)));
        quoteBorderColorBox.getChildren().addAll(quoteBorderColorLabel, quoteBorderColorPicker);
        
        VBox quoteBgColorBox = new VBox(5);
        Label quoteBgColorLabel = new Label("Hintergrund:");
        ColorPicker quoteBgColorPicker = new ColorPicker(Color.web(options.quoteBackgroundColor));
        quoteBgColorPicker.setOnAction(e -> options.quoteBackgroundColor = String.format("%02X%02X%02X", 
            (int)(quoteBgColorPicker.getValue().getRed() * 255),
            (int)(quoteBgColorPicker.getValue().getGreen() * 255),
            (int)(quoteBgColorPicker.getValue().getBlue() * 255)));
        quoteBgColorBox.getChildren().addAll(quoteBgColorLabel, quoteBgColorPicker);
        
        quoteColorsBox.getChildren().addAll(quoteBorderColorBox, quoteBgColorBox);
        
        HBox quoteIndentBox = new HBox(10);
        quoteIndentBox.setAlignment(Pos.CENTER_LEFT);
        Label quoteIndentLabel = new Label("Einrückung (cm):");
        Spinner<Double> quoteIndentSpinner = new Spinner<>(0.0, 3.0, options.quoteIndent, 0.1);
        quoteIndentSpinner.setEditable(true);
        quoteIndentSpinner.valueProperty().addListener((obs, oldVal, newVal) -> options.quoteIndent = newVal);
        quoteIndentBox.getChildren().addAll(quoteIndentLabel, quoteIndentSpinner);
        
        quoteSection.getChildren().addAll(quoteColorsBox, quoteIndentBox);
        
        // === LISTEN ===
        VBox listSection = createSection("📋 Listen", "Formatierung von Aufzählungen");
        
        HBox bulletStyleBox = new HBox(10);
        bulletStyleBox.setAlignment(Pos.CENTER_LEFT);
        Label bulletStyleLabel = new Label("Bullet-Style:");
        ComboBox<String> bulletStyleCombo = new ComboBox<>();
        bulletStyleCombo.getItems().addAll("•", "-", "*", "◦", "▪", "▫", "▸", "▹");
        bulletStyleCombo.setValue(options.bulletStyle);
        bulletStyleCombo.setOnAction(e -> options.bulletStyle = bulletStyleCombo.getValue());
        bulletStyleBox.getChildren().addAll(bulletStyleLabel, bulletStyleCombo);
        
        CheckBox listIndentCheck = new CheckBox("Listen einrücken");
        listIndentCheck.setSelected(options.listIndentation);
        listIndentCheck.setOnAction(e -> options.listIndentation = listIndentCheck.isSelected());
        
        HBox listIndentSizeBox = new HBox(10);
        listIndentSizeBox.setAlignment(Pos.CENTER_LEFT);
        Label listIndentSizeLabel = new Label("Einrückung (cm):");
        Spinner<Double> listIndentSizeSpinner = new Spinner<>(0.1, 2.0, options.listIndentSize, 0.1);
        listIndentSizeSpinner.setEditable(true);
        listIndentSizeSpinner.valueProperty().addListener((obs, oldVal, newVal) -> options.listIndentSize = newVal);
        listIndentSizeBox.getChildren().addAll(listIndentSizeLabel, listIndentSizeSpinner);
        
        listSection.getChildren().addAll(bulletStyleBox, listIndentCheck, listIndentSizeBox);
        
        // === LINKS ===
        VBox linkSection = createSection("🔗 Links", "Formatierung von Hyperlinks");
        
        HBox linkColorBox = new HBox(10);
        linkColorBox.setAlignment(Pos.CENTER_LEFT);
        Label linkColorLabel = new Label("Link-Farbe:");
        ColorPicker linkColorPicker = new ColorPicker(Color.web(options.linkColor));
        linkColorPicker.setOnAction(e -> options.linkColor = String.format("%02X%02X%02X", 
            (int)(linkColorPicker.getValue().getRed() * 255),
            (int)(linkColorPicker.getValue().getGreen() * 255),
            (int)(linkColorPicker.getValue().getBlue() * 255)));
        linkColorBox.getChildren().addAll(linkColorLabel, linkColorPicker);
        
        CheckBox underlineLinksCheck = new CheckBox("Links unterstrichen");
        underlineLinksCheck.setSelected(options.underlineLinks);
        underlineLinksCheck.setOnAction(e -> options.underlineLinks = underlineLinksCheck.isSelected());
        
        linkSection.getChildren().addAll(linkColorBox, underlineLinksCheck);
        
        // === METADATEN ===
        VBox metadataSection = createSection("📋 Dokument-Metadaten", "Eigenschaften des Dokuments");
        
        HBox titleBox = new HBox(10);
        titleBox.setAlignment(Pos.CENTER_LEFT);
        Label titleLabel = new Label("Titel:");
        TextField titleField = new TextField(options.documentTitle);
        titleField.setPrefWidth(300);
        titleField.textProperty().addListener((obs, oldVal, newVal) -> options.documentTitle = newVal);
        titleBox.getChildren().addAll(titleLabel, titleField);
        
        HBox authorBox = new HBox(10);
        authorBox.setAlignment(Pos.CENTER_LEFT);
        Label authorLabel = new Label("Autor:");
        TextField authorField = new TextField(options.documentAuthor);
        authorField.setPrefWidth(300);
        authorField.textProperty().addListener((obs, oldVal, newVal) -> options.documentAuthor = newVal);
        authorBox.getChildren().addAll(authorLabel, authorField);
        
        HBox subjectBox = new HBox(10);
        subjectBox.setAlignment(Pos.CENTER_LEFT);
        Label subjectLabel = new Label("Betreff:");
        TextField subjectField = new TextField(options.documentSubject);
        subjectField.setPrefWidth(300);
        subjectField.textProperty().addListener((obs, oldVal, newVal) -> options.documentSubject = newVal);
        subjectBox.getChildren().addAll(subjectLabel, subjectField);
        
        HBox keywordsBox = new HBox(10);
        keywordsBox.setAlignment(Pos.CENTER_LEFT);
        Label keywordsLabel = new Label("Schlüsselwörter:");
        TextField keywordsField = new TextField(options.documentKeywords);
        keywordsField.setPrefWidth(300);
        keywordsField.textProperty().addListener((obs, oldVal, newVal) -> options.documentKeywords = newVal);
        keywordsBox.getChildren().addAll(keywordsLabel, keywordsField);
        
        HBox categoryBox = new HBox(10);
        categoryBox.setAlignment(Pos.CENTER_LEFT);
        Label categoryLabel = new Label("Kategorie:");
        TextField categoryField = new TextField(options.documentCategory);
        categoryField.setPrefWidth(300);
        categoryField.textProperty().addListener((obs, oldVal, newVal) -> options.documentCategory = newVal);
        categoryBox.getChildren().addAll(categoryLabel, categoryField);
        
        metadataSection.getChildren().addAll(titleBox, authorBox, subjectBox, keywordsBox, categoryBox);
        
        // === ERWEITERTE OPTIONEN ===
        VBox advancedSection = createSection("⚙️ Erweiterte Optionen", "Zusätzliche Funktionen");
        
        CheckBox tocCheck = new CheckBox("Inhaltsverzeichnis einfügen");
        tocCheck.setSelected(options.includeTableOfContents);
        tocCheck.setOnAction(e -> options.includeTableOfContents = tocCheck.isSelected());
        
        CheckBox autoNumberCheck = new CheckBox("Überschriften automatisch nummerieren");
        autoNumberCheck.setSelected(options.autoNumberHeadings);
        autoNumberCheck.setOnAction(e -> options.autoNumberHeadings = autoNumberCheck.isSelected());
        
        CheckBox protectCheck = new CheckBox("Dokument schützen");
        protectCheck.setSelected(options.protectDocument);
        protectCheck.setOnAction(e -> options.protectDocument = protectCheck.isSelected());
        
        HBox passwordBox = new HBox(10);
        passwordBox.setAlignment(Pos.CENTER_LEFT);
        Label passwordLabel = new Label("Schutz-Passwort:");
        PasswordField passwordField = new PasswordField();
        passwordField.setPrefWidth(200);
        passwordField.textProperty().addListener((obs, oldVal, newVal) -> options.protectionPassword = newVal);
        passwordBox.getChildren().addAll(passwordLabel, passwordField);
        
        CheckBox trackChangesCheck = new CheckBox("Änderungen verfolgen");
        trackChangesCheck.setSelected(options.trackChanges);
        trackChangesCheck.setOnAction(e -> options.trackChanges = trackChangesCheck.isSelected());
        
        CheckBox showHiddenCheck = new CheckBox("Versteckten Text anzeigen");
        showHiddenCheck.setSelected(options.showHiddenText);
        showHiddenCheck.setOnAction(e -> options.showHiddenText = showHiddenCheck.isSelected());
        
        CheckBox commentsCheck = new CheckBox("Kommentare einfügen");
        commentsCheck.setSelected(options.includeComments);
        commentsCheck.setOnAction(e -> options.includeComments = commentsCheck.isSelected());
        
        HBox languageBox = new HBox(10);
        languageBox.setAlignment(Pos.CENTER_LEFT);
        Label languageLabel = new Label("Sprache:");
        ComboBox<String> languageCombo = new ComboBox<>();
        languageCombo.getItems().addAll("de-DE", "en-US", "en-GB", "fr-FR", "es-ES", "it-IT");
        languageCombo.setValue(options.language);
        languageCombo.setOnAction(e -> options.language = languageCombo.getValue());
        languageBox.getChildren().addAll(languageLabel, languageCombo);
        
        HBox readingLevelBox = new HBox(10);
        readingLevelBox.setAlignment(Pos.CENTER_LEFT);
        Label readingLevelLabel = new Label("Leseniveau:");
        ComboBox<String> readingLevelCombo = new ComboBox<>();
        readingLevelCombo.getItems().addAll("standard", "simplified", "technical");
        readingLevelCombo.setValue(options.readingLevel);
        readingLevelCombo.setOnAction(e -> options.readingLevel = readingLevelCombo.getValue());
        readingLevelBox.getChildren().addAll(readingLevelLabel, readingLevelCombo);
        
        advancedSection.getChildren().addAll(tocCheck, autoNumberCheck, protectCheck, passwordBox, 
            trackChangesCheck, showHiddenCheck, commentsCheck, languageBox, readingLevelBox);
        
        // Alles zusammenfügen
        mainContent.getChildren().addAll(
            fontsSection,
            paragraphSection,
            headingsSection,
            pageSection,
            tableSection,
            codeSection,
            quoteSection,
            listSection,
            linkSection,
            metadataSection,
            advancedSection
        );
        
        scrollPane.setContent(mainContent);
        
        // Buttons
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new Insets(10));
        
        Button saveButton = new Button("💾 Speichern");
        saveButton.setStyle("-fx-font-weight: bold; -fx-background-color: #4CAF50; -fx-text-fill: white;");
        saveButton.setOnAction(e -> {
            // Optionen in User Preferences speichern
            globalDocxOptions.saveToPreferences();
            optionsStage.close();
        });
        
        Button cancelButton = new Button("❌ Abbrechen");
        cancelButton.setStyle("-fx-background-color: #f44336; -fx-text-fill: white;");
        cancelButton.setOnAction(e -> {
            // Optionen auf gespeicherte Werte zurücksetzen
            globalDocxOptions.loadFromPreferences();
            optionsStage.close();
        });
        
        Button resetButton = new Button("🔄 Zurücksetzen");
        resetButton.setStyle("-fx-background-color: #ff9800; -fx-text-fill: white;");
        resetButton.setOnAction(e -> {
            // Alle Optionen auf Standard zurücksetzen
            globalDocxOptions.resetToDefaults();
            
            // Dialog neu laden mit Standardwerten
            optionsStage.close();
            showDocxOptionsDialog(docxCheck);
        });
        
        buttonBox.getChildren().addAll(resetButton, cancelButton, saveButton);
        
        VBox root = new VBox();
        root.getChildren().addAll(scrollPane, buttonBox);
        
        Scene scene = new Scene(root);
        optionsStage.setSceneWithTitleBar(scene);
        
        // CSS-Stylesheets laden
        String stylesCss = ResourceManager.getCssResource("css/styles.css");
        String editorCss = ResourceManager.getCssResource("css/editor.css");
        if (stylesCss != null && !scene.getStylesheets().contains(stylesCss)) scene.getStylesheets().add(stylesCss);
        if (editorCss != null && !scene.getStylesheets().contains(editorCss)) scene.getStylesheets().add(editorCss);
        
        // Theme auf die Stage anwenden
        applyThemeToNode(scene.getRoot(), currentThemeIndex);
        
        // Dialog anzeigen
        optionsStage.showAndWait();

    }
    
    // Hilfsmethode zum Erstellen von Sektionen
    private VBox createSection(String title, String description) {
        VBox section = new VBox(10);
        section.setPadding(new Insets(15));
        section.getStyleClass().add("docx-options-section");
        
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("docx-options-section-title");
        
        Label descLabel = new Label(description);
        descLabel.getStyleClass().add("docx-options-section-description");
        
        section.getChildren().addAll(titleLabel, descLabel);
        return section;
    }
    
    private void exportToRTF(String content, File file, ExportResult result) throws IOException {
        String rtfContent = convertMarkdownToRTF(content);
        Files.write(file.toPath(), rtfContent.getBytes("Windows-1252"));
        updateStatus("Als RTF exportiert: " + file.getName());
    }
    
    private void exportToDOCX(String content, File file, ExportResult result) throws Exception {
        if (docxProcessor == null) {
            throw new Exception("DOCX-Processor nicht verfügbar");
        }
        
        // DOCX-Optionen verwenden, falls vorhanden
        if (result.docxOptions != null) {
            docxProcessor.exportMarkdownToDocxWithOptions(content, file, result.docxOptions);
        } else {
            // Standard-Export ohne Optionen
            docxProcessor.exportMarkdownToDocx(content, file);
        }
        
        updateStatus("Als DOCX exportiert: " + file.getName());
    }
    
    private void exportToHTML(String content, File file, ExportResult result) throws IOException {
        String htmlContent = convertMarkdownToHTML(content);
        Files.write(file.toPath(), htmlContent.getBytes(StandardCharsets.UTF_8));
        updateStatus("Als HTML exportiert: " + file.getName());
    }
    
    private void exportToTXT(String content, File file, ExportResult result) throws IOException {
        String plainText = convertMarkdownToPlainText(content);
        Files.write(file.toPath(), plainText.getBytes(StandardCharsets.UTF_8));
        updateStatus("Als TXT exportiert: " + file.getName());
    }
    
    private void exportToMD(String content, File file, ExportResult result) throws IOException {
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        updateStatus("Als MD exportiert: " + file.getName());
    }
    
        private void exportToPDF(String content, File file, ExportResult result) throws Exception {
        try {
            // OpenPDF verwenden für echten PDF-Export
            com.lowagie.text.Document document = new com.lowagie.text.Document();
            com.lowagie.text.pdf.PdfWriter.getInstance(document, new java.io.FileOutputStream(file));
            
            document.open();
            
            // Schriftarten definieren
            com.lowagie.text.Font normalFont = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 12);
            com.lowagie.text.Font boldFont = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 12, com.lowagie.text.Font.BOLD);
            com.lowagie.text.Font italicFont = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 12, com.lowagie.text.Font.ITALIC);
            com.lowagie.text.Font codeFont = new com.lowagie.text.Font(com.lowagie.text.Font.COURIER, 10);
            com.lowagie.text.Font h1Font = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 18, com.lowagie.text.Font.BOLD);
            com.lowagie.text.Font h2Font = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 16, com.lowagie.text.Font.BOLD);
            com.lowagie.text.Font h3Font = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 14, com.lowagie.text.Font.BOLD);
            com.lowagie.text.Font h4Font = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 13, com.lowagie.text.Font.BOLD);
            
            // Markdown zu PDF konvertieren
            String[] lines = content.split("\n");
            boolean inCodeBlock = false;
            boolean inBlockquote = false;
            boolean inUnorderedList = false;
            boolean inOrderedList = false;
            
            for (String line : lines) {
                String trimmedLine = line.trim();
                
                // Code-Blöcke
                if (trimmedLine.startsWith("```")) {
                    if (inCodeBlock) {
                        inCodeBlock = false;
                        document.add(new com.lowagie.text.Paragraph(" "));
                    } else {
                        inCodeBlock = true;
                        document.add(new com.lowagie.text.Paragraph(" "));
                    }
                    continue;
                }
                
                if (inCodeBlock) {
                    document.add(new com.lowagie.text.Paragraph(line, codeFont));
                    continue;
                }
                
                // Horizontale Linien
                if (trimmedLine.matches("^[-*_]{3,}$")) {
                    document.add(new com.lowagie.text.Paragraph("_".repeat(50), normalFont));
                    document.add(new com.lowagie.text.Paragraph(" "));
                    continue;
                }
                
                // Blockquotes
                if (trimmedLine.startsWith(">")) {
                    if (!inBlockquote) {
                        inBlockquote = true;
                    }
                    String quoteText = trimmedLine.substring(1).trim();
                    com.lowagie.text.Paragraph quote = new com.lowagie.text.Paragraph(quoteText, italicFont);
                    quote.setIndentationLeft(20);
                    document.add(quote);
                    continue;
                } else if (inBlockquote) {
                    inBlockquote = false;
                    document.add(new com.lowagie.text.Paragraph(" "));
                }
                
                // Listen
                if (trimmedLine.matches("^[-*+]\\s+.*")) {
                    if (!inUnorderedList) {
                        if (inOrderedList) {
                            inOrderedList = false;
                        }
                        inUnorderedList = true;
                    }
                    String listItem = trimmedLine.substring(trimmedLine.indexOf(" ") + 1);
                    com.lowagie.text.ListItem item = new com.lowagie.text.ListItem(listItem, normalFont);
                    com.lowagie.text.List list = new com.lowagie.text.List(com.lowagie.text.List.UNORDERED);
                    list.add(item);
                    document.add(list);
                    continue;
                } else if (trimmedLine.matches("^\\d+\\.\\s+.*")) {
                    if (!inOrderedList) {
                        if (inUnorderedList) {
                            inUnorderedList = false;
                        }
                        inOrderedList = true;
                    }
                    String listItem = trimmedLine.substring(trimmedLine.indexOf(" ") + 1);
                    com.lowagie.text.ListItem item = new com.lowagie.text.ListItem(listItem, normalFont);
                    com.lowagie.text.List list = new com.lowagie.text.List(com.lowagie.text.List.ORDERED);
                    list.add(item);
                    continue;
                } else {
                    if (inUnorderedList || inOrderedList) {
                        inUnorderedList = false;
                        inOrderedList = false;
                        document.add(new com.lowagie.text.Paragraph(" "));
                    }
                }
                
                // Überschriften
                if (trimmedLine.startsWith("# ")) {
                    document.add(new com.lowagie.text.Paragraph(trimmedLine.substring(2), h1Font));
                    document.add(new com.lowagie.text.Paragraph(" "));
                } else if (trimmedLine.startsWith("## ")) {
                    document.add(new com.lowagie.text.Paragraph(trimmedLine.substring(3), h2Font));
                    document.add(new com.lowagie.text.Paragraph(" "));
                } else if (trimmedLine.startsWith("### ")) {
                    document.add(new com.lowagie.text.Paragraph(trimmedLine.substring(4), h3Font));
                    document.add(new com.lowagie.text.Paragraph(" "));
                } else if (trimmedLine.startsWith("#### ")) {
                    document.add(new com.lowagie.text.Paragraph(trimmedLine.substring(5), h4Font));
                    document.add(new com.lowagie.text.Paragraph(" "));
                } else if (trimmedLine.isEmpty()) {
                    document.add(new com.lowagie.text.Paragraph(" "));
                } else if (trimmedLine.contains("|") && !trimmedLine.startsWith("```")) {
                    // Tabellen
                    if (trimmedLine.matches("^\\|.*\\|$")) {
                        String[] cells = trimmedLine.split("\\|");
                        int columnCount = cells.length - 2; // Minus 2 für leere Zellen am Anfang und Ende
                        
                        if (columnCount > 0) {
                            com.lowagie.text.pdf.PdfPTable table = new com.lowagie.text.pdf.PdfPTable(columnCount);
                            table.setWidthPercentage(100);
                            
                            for (int j = 1; j < cells.length - 1; j++) {
                                String cell = cells[j].trim();
                                com.lowagie.text.pdf.PdfPCell pdfCell = new com.lowagie.text.pdf.PdfPCell(new com.lowagie.text.Phrase(cell, normalFont));
                                pdfCell.setBorder(com.lowagie.text.Rectangle.BOX);
                                pdfCell.setPadding(5);
                                table.addCell(pdfCell);
                            }
                            document.add(table);
                        }
                    }
                } else {
                    // Normaler Text mit Markdown-Formatierung
                    com.lowagie.text.Paragraph paragraph = new com.lowagie.text.Paragraph();
                    addFormattedTextToParagraph(paragraph, line, normalFont, boldFont, italicFont, codeFont);
                    document.add(paragraph);
                }
            }
            
            document.close();
            updateStatus("PDF erfolgreich erstellt: " + file.getName());
            
        } catch (Exception e) {
            updateStatusError("Fehler beim PDF-Export: " + e.getMessage());
            logger.error("PDF Export error", e);
            throw e;
        }
    }
    private void addFormattedTextToParagraph(com.lowagie.text.Paragraph paragraph, String text, 
                                           com.lowagie.text.Font normalFont, com.lowagie.text.Font boldFont, 
                                           com.lowagie.text.Font italicFont, com.lowagie.text.Font codeFont) {
        // Einfache Markdown-Formatierung für PDF - direkte Verarbeitung
        int pos = 0;
        boolean isBold = false;
        boolean isItalic = false;
        boolean isCode = false;
        boolean isStrike = false;
        boolean isHighlight = false;
        
        while (pos < text.length()) {
            // Fett und kursiv (drei Sternchen)
            if (pos + 2 < text.length() && text.substring(pos, pos + 3).equals("***")) {
                if (isBold && isItalic) {
                    isBold = false;
                    isItalic = false;
                } else {
                    isBold = true;
                    isItalic = true;
                }
                pos += 3;
                continue;
            }
            
            // Fett (zwei Sternchen)
            if (pos + 1 < text.length() && text.substring(pos, pos + 2).equals("**")) {
                isBold = !isBold;
                pos += 2;
                continue;
            }
            
            // Kursiv (ein Sternchen)
            if (text.charAt(pos) == '*') {
                isItalic = !isItalic;
                pos += 1;
                continue;
            }
            
            // Inline-Code (Backticks)
            if (text.charAt(pos) == '`') {
                isCode = !isCode;
                pos += 1;
                continue;
            }
            
            // Durchgestrichen (zwei Tilden)
            if (pos + 1 < text.length() && text.substring(pos, pos + 2).equals("~~")) {
                isStrike = !isStrike;
                pos += 2;
                continue;
            }
            
            // Bilder ![Alt-Text](URL)
            if (text.charAt(pos) == '!' && pos + 1 < text.length() && text.charAt(pos + 1) == '[') {
                int altEnd = text.indexOf(']', pos + 1);
                if (altEnd != -1 && altEnd + 1 < text.length() && text.charAt(altEnd + 1) == '(') {
                    int urlEnd = text.indexOf(')', altEnd + 1);
                    if (urlEnd != -1) {
                        String altText = text.substring(pos + 2, altEnd);
                        String imageUrl = text.substring(altEnd + 2, urlEnd);
                        
                        // Bild als Text mit URL darstellen
                        com.lowagie.text.Font imageFont = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 10, com.lowagie.text.Font.ITALIC);
                        com.lowagie.text.Chunk imageChunk = new com.lowagie.text.Chunk("[BILD: " + altText + " (" + imageUrl + ")]", imageFont);
                        paragraph.add(imageChunk);
                        
                        pos = urlEnd + 1;
                        continue;
                    }
                }
            }
            
            // Links [Text](URL)
            if (text.charAt(pos) == '[') {
                int linkEnd = text.indexOf(']', pos);
                if (linkEnd != -1 && linkEnd + 1 < text.length() && text.charAt(linkEnd + 1) == '(') {
                    int urlEnd = text.indexOf(')', linkEnd + 1);
                    if (urlEnd != -1) {
                        String linkText = text.substring(pos + 1, linkEnd);
                        String url = text.substring(linkEnd + 2, urlEnd);
                        
                        // Link als unterstrichener Text darstellen
                        com.lowagie.text.Font linkFont = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 12, com.lowagie.text.Font.UNDERLINE);
                        com.lowagie.text.Chunk linkChunk = new com.lowagie.text.Chunk(linkText + " (" + url + ")", linkFont);
                        paragraph.add(linkChunk);
                        
                        pos = urlEnd + 1;
                        continue;
                    }
                }
            }
            
            // Hervorgehoben (zwei Gleichheitszeichen) - aber nicht bei chemischen Formeln
            if (pos + 1 < text.length() && text.substring(pos, pos + 2).equals("==")) {
                // Prüfe, ob es sich um eine chemische Formel handelt (z.B. H2O)
                boolean isChemicalFormula = false;
                if (pos > 0) {
                    char prevChar = text.charAt(pos - 1);
                    if (Character.isLetter(prevChar)) {
                        // Suche nach Zahlen nach dem Gleichheitszeichen
                        int nextPos = pos + 2;
                        while (nextPos < text.length() && Character.isDigit(text.charAt(nextPos))) {
                            nextPos++;
                        }
                        if (nextPos < text.length() && Character.isLetter(text.charAt(nextPos))) {
                            isChemicalFormula = true;
                        }
                    }
                }
                
                if (!isChemicalFormula) {
                    isHighlight = !isHighlight;
                    pos += 2;
                    continue;
                }
            }
            
            // Normaler Text - finde das Ende des aktuellen Textabschnitts
            int endPos = pos;
            while (endPos < text.length()) {
                char c = text.charAt(endPos);
                if (c == '*' || c == '`' || c == '~' || c == '=' || c == '[') {
                    // Prüfe auf Markdown-Syntax
                    if (c == '*' && endPos + 2 < text.length() && text.substring(endPos, endPos + 3).equals("***")) {
                        break;
                    }
                    if (c == '*' && endPos + 1 < text.length() && text.substring(endPos, endPos + 2).equals("**")) {
                        break;
                    }
                    if (c == '*' && endPos + 0 < text.length()) {
                        break;
                    }
                    if (c == '`' && endPos + 0 < text.length()) {
                        break;
                    }
                    if (c == '~' && endPos + 1 < text.length() && text.substring(endPos, endPos + 2).equals("~~")) {
                        break;
                    }
                    if (c == '=' && endPos + 1 < text.length() && text.substring(endPos, endPos + 2).equals("==")) {
                        break;
                    }
                    if (c == '[' && endPos + 0 < text.length()) {
                        break;
                    }
                }
                endPos++;
            }
            
            // Text extrahieren und formatieren
            String textPart = text.substring(pos, endPos);
            if (!textPart.isEmpty()) {
                // Font basierend auf Formatierung wählen
                com.lowagie.text.Font font = normalFont;
                if (isCode) {
                    font = codeFont;
                } else if (isBold && isItalic) {
                    font = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 12, com.lowagie.text.Font.BOLD | com.lowagie.text.Font.ITALIC);
                } else if (isBold) {
                    font = boldFont;
                } else if (isItalic) {
                    font = italicFont;
                }
                
                com.lowagie.text.Chunk chunk = new com.lowagie.text.Chunk(textPart, font);
                if (isStrike) {
                    // Durchgestrichen durch Linie durch den Text
                    chunk.setUnderline(0.1f, 6f);
                }
                if (isHighlight) {
                    // Hervorhebung durch Unterstreichung
                    chunk.setUnderline(0.2f, 0f);
                }
                paragraph.add(chunk);
            }
            
            pos = endPos;
        }
    }
    
    private String formatMarkdownForPDF(String text) {
        // Markdown-Formatierung für PDF konvertieren
        return text
            // Fett (zwei Sternchen) - für PDF nur Text ohne **
            .replaceAll("\\*\\*(.*?)\\*\\*", "$1")
            // Kursiv (ein Sternchen) - aber nicht wenn es bereits fett ist
            .replaceAll("(?<!\\*)\\*(?!\\*)(.*?)(?<!\\*)\\*(?!\\*)", "$1")
            // Inline-Code (Backticks)
            .replaceAll("`(.*?)`", "$1")
            // Links
            .replaceAll("\\[([^\\]]+)\\]\\(([^)]+)\\)", "$1 ($2)")
            // Bilder
            .replaceAll("!\\[([^\\]]*)\\]\\(([^)]+)\\)", "[Bild: $1]")
            // Durchgestrichen (zwei Tilden)
            .replaceAll("~~(.*?)~~", "$1")
            // Hervorgehoben (zwei Gleichheitszeichen)
            .replaceAll("==(.*?)==", "$1");
    }
    
    private void exportToEPUB(String content, File file, ExportResult result) throws Exception {
        // EPUB-Export (einfache Implementierung über HTML)
        String htmlContent = convertMarkdownToHTML(content);
        
        // Erstelle eine HTML-Datei (kann später zu EPUB konvertiert werden)
        File htmlFile = new File(file.getParentFile(), file.getName().replace(".epub", ".html"));
        Files.write(htmlFile.toPath(), htmlContent.getBytes(StandardCharsets.UTF_8));
        
        updateStatus("EPUB-Export: HTML-Datei erstellt (kann zu EPUB konvertiert werden): " + htmlFile.getName());
    }
    
    private String convertMarkdownToHTML(String markdown) {
        StringBuilder html = new StringBuilder();
        
        // HTML-Header
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"de\">\n");
        html.append("<head>\n");
        html.append("    <meta charset=\"UTF-8\">\n");
        html.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("    <title>Exportiertes Dokument</title>\n");
        html.append("    <style>\n");
        html.append("        body { font-family: Arial, sans-serif; line-height: 1.6; margin: 0; padding: 2em 4em; max-width: 1200px; margin-left: auto; margin-right: auto; }\n");
        html.append("        h1, h2, h3, h4 { color: #2c3e50; }\n");
        html.append("        h1 { border-bottom: 2px solid #3498db; padding-bottom: 10px; }\n");
        html.append("        h2 { border-bottom: 1px solid #bdc3c7; padding-bottom: 5px; }\n");
        html.append("        code { background-color: #f8f9fa; padding: 2px 4px; border-radius: 3px; }\n");
        html.append("        pre { background-color: #f8f9fa; padding: 15px; border-radius: 5px; overflow-x: auto; }\n");
        html.append("        blockquote { border-left: 4px solid #3498db; margin: 10px 0; padding-left: 20px; color: #7f8c8d; }\n");
        html.append("        hr { border: none; border-top: 2px solid #bdc3c7; margin: 20px 0; }\n");
        html.append("        ul, ol { margin: 10px 0; padding-left: 20px; }\n");
        html.append("        li { margin: 5px 0; }\n");
        html.append("        table { border-collapse: collapse; width: 100%; margin: 15px 0; }\n");
        html.append("        th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }\n");
        html.append("        th { background-color: #f2f2f2; }\n");
        html.append("        .strikethrough { text-decoration: line-through; }\n");
        html.append("        .highlight { background-color: yellow; }\n");
        html.append("    </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        
        // Markdown zu HTML konvertieren
        String[] lines = markdown.split("\n");
        boolean inCodeBlock = false;
        boolean inBlockquote = false;
        boolean inUnorderedList = false;
        boolean inOrderedList = false;
        boolean inTable = false;
        StringBuilder tableContent = new StringBuilder();
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmedLine = line.trim();
            
            // Horizontale Linien
            if (trimmedLine.matches("^[-*_]{3,}$")) {
                html.append("<hr>\n");
                continue;
            }
            
            // Tabellen
            if (trimmedLine.contains("|") && !trimmedLine.startsWith("```") ) {
                if (!inTable) {
                    inTable = true;
                    tableContent = new StringBuilder();
                    tableContent.append("<table>\n");
                }
                
                // Separator-Zeile (z. B. |---|:---:|---|) erkennen und überspringen
                boolean isSeparator = trimmedLine.matches("^\\s*\\|?\\s*(?::?-+\\s*\\|\\s*)+(?::?-+)\\s*\\|?\\s*$");
                if (isSeparator) {
                    continue;
                }
                
                // Header-Zeile: wenn die nächste Zeile ein Separator ist
                boolean isHeaderRow = false;
                if (i + 1 < lines.length) {
                    String nextTrimmed = lines[i + 1].trim();
                    isHeaderRow = nextTrimmed.matches("^\\s*\\|?\\s*(?::?-+\\s*\\|\\s*)+(?::?-+)\\s*\\|?\\s*$");
                }
                
                // Führende / nachlaufende Pipes entfernen, dann spaltenweise splitten
                String normalized = trimmedLine.replaceAll("^\\|", "").replaceAll("\\|$", "");
                String[] cells = normalized.split("\\|");
                
                tableContent.append("<tr>\n");
                for (String rawCell : cells) {
                    String cell = rawCell.trim();
                    if (isHeaderRow) {
                        tableContent.append("<th>").append(convertInlineMarkdown(cell)).append("</th>\n");
                    } else {
                        tableContent.append("<td>").append(convertInlineMarkdown(cell)).append("</td>\n");
                    }
                }
                tableContent.append("</tr>\n");
                continue;
            } else if (inTable) {
                inTable = false;
                tableContent.append("</table>\n");
                html.append(tableContent.toString());
            }
            
            if (trimmedLine.isEmpty()) {
                if (inBlockquote) {
                    html.append("</blockquote>\n");
                    inBlockquote = false;
                }
                if (inUnorderedList) {
                    html.append("</ul>\n");
                    inUnorderedList = false;
                }
                if (inOrderedList) {
                    html.append("</ol>\n");
                    inOrderedList = false;
                }
                html.append("<br>\n");
                continue;
            }
            
            // Code-Blöcke
            if (trimmedLine.startsWith("```")) {
                if (inCodeBlock) {
                    html.append("</pre></code>\n");
                    inCodeBlock = false;
                } else {
                    html.append("<pre><code>\n");
                    inCodeBlock = true;
                }
                continue;
            }
            
            if (inCodeBlock) {
                html.append(escapeHtml(line)).append("\n");
                continue;
            }
            
            // Blockquotes
            if (trimmedLine.startsWith(">")) {
                if (!inBlockquote) {
                    html.append("<blockquote>\n");
                    inBlockquote = true;
                }
                String quoteText = trimmedLine.substring(1).trim();
                html.append("<p>").append(convertInlineMarkdown(quoteText)).append("</p>\n");
                continue;
            } else if (inBlockquote) {
                html.append("</blockquote>\n");
                inBlockquote = false;
            }
            
            // Listen
            if (trimmedLine.matches("^[-*+]\\s+.*")) {
                if (!inUnorderedList) {
                    if (inOrderedList) {
                        html.append("</ol>\n");
                        inOrderedList = false;
                    }
                    html.append("<ul>\n");
                    inUnorderedList = true;
                }
                String listItem = trimmedLine.substring(trimmedLine.indexOf(" ") + 1);
                html.append("<li>").append(convertInlineMarkdown(listItem)).append("</li>\n");
                continue;
            } else if (trimmedLine.matches("^\\d+\\.\\s+.*")) {
                if (!inOrderedList) {
                    if (inUnorderedList) {
                        html.append("</ul>\n");
                        inUnorderedList = false;
                    }
                    html.append("<ol>\n");
                    inOrderedList = true;
                }
                String listItem = trimmedLine.substring(trimmedLine.indexOf(" ") + 1);
                html.append("<li>").append(convertInlineMarkdown(listItem)).append("</li>\n");
                continue;
            } else {
                if (inUnorderedList) {
                    html.append("</ul>\n");
                    inUnorderedList = false;
                }
                if (inOrderedList) {
                    html.append("</ol>\n");
                    inOrderedList = false;
                }
            }
            
            // Überschriften
            if (trimmedLine.startsWith("# ")) {
                html.append("<h1>").append(convertInlineMarkdown(trimmedLine.substring(2))).append("</h1>\n");
            } else if (trimmedLine.startsWith("## ")) {
                html.append("<h2>").append(convertInlineMarkdown(trimmedLine.substring(3))).append("</h2>\n");
            } else if (trimmedLine.startsWith("### ")) {
                html.append("<h3>").append(convertInlineMarkdown(trimmedLine.substring(4))).append("</h3>\n");
            } else if (trimmedLine.startsWith("#### ")) {
                html.append("<h4>").append(convertInlineMarkdown(trimmedLine.substring(5))).append("</h4>\n");
            } else {
                // Normaler Text mit Markdown-Formatierung
                html.append("<p>").append(convertInlineMarkdown(line)).append("</p>\n");
            }
        }
        
        // Schließe offene Tags
        if (inBlockquote) html.append("</blockquote>\n");
        if (inUnorderedList) html.append("</ul>\n");
        if (inOrderedList) html.append("</ol>\n");
        if (inTable) {
            tableContent.append("</table>\n");
            html.append(tableContent.toString());
        }
        
        html.append("</body>\n");
        html.append("</html>");
        
        return html.toString();
    }
    
    private String convertMarkdownToPlainText(String markdown) {
        // Markdown-Formatierung entfernen
        String text = markdown
            .replaceAll("^#+\\s+", "") // Überschriften
            .replaceAll("\\*\\*(.*?)\\*\\*", "$1") // Fett
            .replaceAll("\\*(.*?)\\*", "$1") // Kursiv
            .replaceAll("`(.*?)`", "$1") // Code
            .replaceAll("\\[([^\\]]+)\\]\\([^)]+\\)", "$1") // Links
            .replaceAll("^[-*+]\\s+", "• ") // Listen
            .replaceAll("^\\d+\\.\\s+", ""); // Nummerierte Listen
        
        return text;
    }
    
    private String convertInlineMarkdown(String text) {
        return text
            // Fett (zwei Sternchen)
            .replaceAll("\\*\\*(.*?)\\*\\*", "<strong>$1</strong>")
            // Kursiv (ein Sternchen) - aber nicht wenn es bereits fett ist
            .replaceAll("(?<!\\*)\\*(?!\\*)(.*?)(?<!\\*)\\*(?!\\*)", "<em>$1</em>")
            // Inline-Code (Backticks)
            .replaceAll("`(.*?)`", "<code>$1</code>")
            // Links
            .replaceAll("\\[([^\\]]+)\\]\\(([^)]+)\\)", "<a href=\"$2\">$1</a>")
            // Bilder
            .replaceAll("!\\[([^\\]]*)\\]\\(([^)]+)\\)", "<img src=\"$2\" alt=\"$1\">")
            // Durchgestrichen (zwei Tilden)
            .replaceAll("~~(.*?)~~", "<span class=\"strikethrough\">$1</span>")
            // Hervorgehoben (zwei Gleichheitszeichen)
            .replaceAll("==(.*?)==", "<span class=\"highlight\">$1</span>");
    }
    
    private String escapeHtml(String text) {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
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
        } catch (Exception e) {
            updateStatusError("Fehler beim Löschen der Search/Replace-Historie: " + e.getMessage());
        }
    }
    
    // Datei-Operationen
    public void saveFile() {
        
        // Spezielle Behandlung für Bücher
        if (isCompleteDocument) {
            // Für Bücher: MD speichern UND Dialog für DOCX anzeigen
            if (currentFile != null) {
                String data = cleanTextForExport(codeArea.getText());
                if (data == null) data = "";
                
                // MD-Datei speichern
                try {
                    Files.write(currentFile.toPath(), data.getBytes(StandardCharsets.UTF_8));
                    updateStatus("Buch gespeichert: " + currentFile.getName());
                } catch (IOException e) {
                    updateStatusError("Fehler beim Speichern: " + e.getMessage());
                    return;
                }
                
                // Dialog für DOCX-Export anzeigen
                showDocxExportDialog();
            }             return;
        }
        
        // Bei neuen Dateien (currentFile = null) direkt Save As verwenden
        if (currentFile == null) {
            saveFileAs();
            return;
        }
        
        // Niemals die originale DOCX-Datei mit Text überschreiben!
        File target = currentFile;
        if ((originalDocxFile != null && target.equals(originalDocxFile)) || isDocxFile(target)) {
            // In ein Sidecar-File mit passender Erweiterung speichern (gleiches Verzeichnis, gleicher Basename)
            target = deriveSidecarFileForCurrentFormat();
        }
        if (target != null) {
            saveToFile(target);
            currentFile = target; // künftige Saves gehen wieder hierhin
            originalContent = cleanTextForComparison(codeArea.getText());
        } else {
            // Fallback auf Save As
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
            
            // Falls versehentlich eine DOCX gewählt wurde: auf Sidecar umbiegen
            File target = isDocxFile(file) ? deriveSidecarFileForCurrentFormat() : file;
            currentFile = target;
            saveToFile(target);
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
            String data = cleanTextForExport(codeArea.getText());
            if (data == null) data = "";
            
            // Spezielle Behandlung für Bücher
            if (isCompleteDocument) {
                // Für Bücher: Nur DOCX erstellen, kein MD speichern
                createDocxFile(file, data);
                return;
            }
            
            // Normale Dateien: MD speichern
            // Nie löschen: leere Dateien bleiben bestehen
            Files.write(file.toPath(), data.getBytes(StandardCharsets.UTF_8));
            updateStatus("Datei gespeichert: " + file.getName());
            
            // Benachrichtige MainController über die Änderung (für Watcher)
            if (mainController != null) {
                mainController.refreshDocxFiles();
                
                // WICHTIG: Markiere DOCX als behandelt nach dem Speichern
                if (originalDocxFile != null) {
                    mainController.updateDocxHashAfterAccept(originalDocxFile);
                    mainController.markDocxFileAsUnchanged(originalDocxFile);
                    
                    // WICHTIG: Hash-Erkennung neu ausführen
                    mainController.checkAllDocxFilesForChanges();
                }
            }
        } catch (IOException e) {
            updateStatusError("Fehler beim Speichern: " + e.getMessage());
        }
    }

    private boolean isDocxFile(File f) {
        if (f == null) return false;
        String n = f.getName().toLowerCase();
        return n.endsWith(".docx") || n.endsWith(".doc");
    }

    private File deriveSidecarFileForCurrentFormat() {
        File base = (originalDocxFile != null) ? originalDocxFile : currentFile;
        if (base == null) return null;
        String baseName = base.getName();
        int idx = baseName.lastIndexOf('.');
        if (idx > 0) baseName = baseName.substring(0, idx);
        String ext = getDefaultExtension();
        
        // Verwende data-Verzeichnis für Sidecar-Dateien
        File dataDir = new File(base.getParentFile(), "data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        return new File(dataDir, baseName + ext);
    }

    private File deriveSidecarFileFor(File docx, DocxProcessor.OutputFormat format) {
        if (docx == null) return null;
        File base = docx;
        String baseName = base.getName();
        int idx = baseName.lastIndexOf('.');
        if (idx > 0) baseName = baseName.substring(0, idx);
        String ext;
        switch (format) {
            case MARKDOWN: ext = ".md"; break;
            case PLAIN_TEXT: ext = ".txt"; break;
            case HTML: default: ext = ".html"; break;
        }
        
        // Verwende data-Verzeichnis für Sidecar-Dateien
        File dataDir = new File(base.getParentFile(), "data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        return new File(dataDir, baseName + ext);
    }
    
    /**
     * Prüft ob es ungespeicherte Änderungen gibt
     */
    public boolean hasUnsavedChanges() {
        if (codeArea == null) {
            return false;
        }
        
        // WICHTIG: Verwende das hasUnsavedChanges Flag für bessere Erkennung
        // Das Flag wird bei markAsChanged() gesetzt und bei markAsSaved() zurückgesetzt
        return hasUnsavedChanges;
    }
    /**
     * Zeigt den Speichern-Dialog beim Schließen
     */
    private void showSaveDialog() {
        // Checkboxen für Speicheroptionen
        CheckBox saveCurrentFormat = new CheckBox("Als " + getFormatDisplayName() + " speichern");
        CheckBox saveOriginalDocx = new CheckBox("Originale DOCX-Datei überschreiben");
        
        // Default: Nur aktuelles Format
        saveCurrentFormat.setSelected(true);
        saveOriginalDocx.setSelected(false);
        
        // DOCX-Optionen Button
        Button docxOptionsBtn = new Button("DOCX-Optionen");
        docxOptionsBtn.setStyle("-fx-font-size: 11px; -fx-padding: 4px 8px;");
        docxOptionsBtn.setOnAction(e -> {
            if (saveOriginalDocx.isSelected()) {
                showDocxOptionsDialog(saveOriginalDocx);
            } else {
                // Warnung anzeigen
                CustomAlert alert = new CustomAlert(Alert.AlertType.WARNING, "Warnung");
                alert.setHeaderText("DOCX nicht ausgewählt");
                alert.setContentText("Bitte aktivieren Sie zuerst die DOCX-Option, um die Einstellungen zu bearbeiten.");
                alert.applyTheme(currentThemeIndex);
                alert.initOwner(stage);
                alert.showAndWait();
            }
        });
        
        // DOCX-Optionen in HBox mit Checkbox
        HBox docxBox = new HBox(10);
        docxBox.getChildren().addAll(saveOriginalDocx, docxOptionsBtn);
        
        VBox content = new VBox(10);
        content.getChildren().addAll(saveCurrentFormat, docxBox);
        content.setPadding(new Insets(10));
        
        // CustomAlert verwenden
        CustomAlert alert = new CustomAlert(Alert.AlertType.CONFIRMATION, "Ungespeicherte Änderungen");
        alert.setHeaderText("Die Datei hat ungespeicherte Änderungen.");
        alert.setContentText("Was möchten Sie speichern?");
        
        // Theme anwenden
        alert.applyTheme(currentThemeIndex);
        
        // Owner setzen
        alert.initOwner(stage);
        
        // Content setzen - verwende setCustomContent für CustomAlert
        alert.setCustomContent(content);
        
        ButtonType saveButton = new ButtonType("Speichern");
        ButtonType discardButton = new ButtonType("Verwerfen");
        ButtonType diffButton = new ButtonType("🔍 Diff anzeigen");
        ButtonType cancelButton = new ButtonType("Abbrechen");
        
        alert.setButtonTypes(saveButton, discardButton, diffButton, cancelButton);
        
        Optional<ButtonType> result = alert.showAndWait();
        
        if (result.isPresent()) {
            if (result.get() == saveButton) {
                // Speichern basierend auf Auswahl
                if (saveCurrentFormat.isSelected()) {
                    saveFile();
                }
                if (saveOriginalDocx.isSelected() && originalDocxFile != null) {
                    saveToOriginalDocx();
                }
                stage.close();
            } else if (result.get() == discardButton) {
                // Verwerfen und schließen
                stage.close();
            } else if (result.get() == diffButton) {
                // Diff anzeigen - verwende MainController Diff
                    if (mainController != null && originalDocxFile != null) {
                    File mdFile = deriveSidecarFileFor(originalDocxFile, outputFormat);
                    DocxFile docxFile = new DocxFile(originalDocxFile);
                    mainController.showDetailedDiffDialog(docxFile, mdFile, null, outputFormat);
                }
            }
            }
            // Bei Abbrechen nichts tun (Dialog schließt nicht)
    }
    
    // showDiffForUnsavedChanges() wurde entfernt - verwende MainController Diff
    
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

    // Entfernt ein einzelnes abschließendes \n oder \r\n aus Anzeigetexten
    private String stripTrailingEol(String text) {
        if (text == null || text.isEmpty()) return text;
        if (text.endsWith("\r\n")) return text.substring(0, text.length() - 2);
        if (text.endsWith("\n")) return text.substring(0, text.length() - 1);
        return text;
    }

    private String joinNewTexts(DiffBlock addedBlock) {
        StringBuilder sb = new StringBuilder();
        for (DiffProcessor.DiffLine line : addedBlock.getLines()) {
            String t = line.getNewText();
            if (t != null) sb.append(t);
        }
        return sb.toString().replaceAll("[\r\n]+$", "");
    }

    private String joinOriginalTexts(DiffBlock deletedBlock) {
        StringBuilder sb = new StringBuilder();
        for (DiffProcessor.DiffLine line : deletedBlock.getLines()) {
            String t = line.getOriginalText();
            if (t != null) sb.append(t);
        }
        return sb.toString().replaceAll("[\r\n]+$", "");
    }

    /**
     * Ultra-konservative Bereinigung von Leerzeilen in Diff-Blöcken.
     * Entfernt NUR 5+ aufeinanderfolgende Leerzeilen (sehr konservativ).
     */
    private List<DiffBlock> trimEmptyEdgesOfBlocks(List<DiffBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) return blocks;

        for (int i = 0; i < blocks.size(); i++) {
            DiffBlock current = blocks.get(i);

            // Ultra-konservative Leerzeilen-Behandlung
            if (current.getType() == DiffBlockType.DELETED) {
                // Bei DELETED-Blöcken: Entferne nur 5+ aufeinanderfolgende Leerzeilen am Ende
                List<DiffProcessor.DiffLine> lines = current.getLines();
                while (lines.size() >= 5) { // Mindestens 5 Zeilen für Redundanz-Check
                    DiffProcessor.DiffLine last = lines.get(lines.size() - 1);
                    DiffProcessor.DiffLine secondLast = lines.get(lines.size() - 2);
                    DiffProcessor.DiffLine thirdLast = lines.get(lines.size() - 3);
                    DiffProcessor.DiffLine fourthLast = lines.get(lines.size() - 4);
                    DiffProcessor.DiffLine fifthLast = lines.get(lines.size() - 5);
                    
                    String lastText = last.getOriginalText();
                    String secondLastText = secondLast.getOriginalText();
                    String thirdLastText = thirdLast.getOriginalText();
                    String fourthLastText = fourthLast.getOriginalText();
                    String fifthLastText = fifthLast.getOriginalText();
                    
                    // Nur entfernen wenn 5 aufeinanderfolgende Zeilen leer sind
                    if ((lastText == null || lastText.trim().isEmpty()) &&
                        (secondLastText == null || secondLastText.trim().isEmpty()) &&
                        (thirdLastText == null || thirdLastText.trim().isEmpty()) &&
                        (fourthLastText == null || fourthLastText.trim().isEmpty()) &&
                        (fifthLastText == null || fifthLastText.trim().isEmpty())) {
        
                        lines.remove(lines.size() - 1);
                    } else {
                        break;
                    }
                }
            } else if (current.getType() == DiffBlockType.ADDED) {
                // Bei ADDED-Blöcken: Entferne nur 5+ aufeinanderfolgende Leerzeilen am Anfang
                List<DiffProcessor.DiffLine> lines = current.getLines();
                while (lines.size() >= 5) { // Mindestens 5 Zeilen für Redundanz-Check
                    DiffProcessor.DiffLine first = lines.get(0);
                    DiffProcessor.DiffLine second = lines.get(1);
                    DiffProcessor.DiffLine third = lines.get(2);
                    DiffProcessor.DiffLine fourth = lines.get(3);
                    DiffProcessor.DiffLine fifth = lines.get(4);
                    
                    String firstText = first.getNewText();
                    String secondText = second.getNewText();
                    String thirdText = third.getNewText();
                    String fourthText = fourth.getNewText();
                    String fifthText = fifth.getNewText();
                    
                    // Nur entfernen wenn 5 aufeinanderfolgende Zeilen leer sind
                    if ((firstText == null || firstText.trim().isEmpty()) &&
                        (secondText == null || secondText.trim().isEmpty()) &&
                        (thirdText == null || thirdText.trim().isEmpty()) &&
                        (fourthText == null || fourthText.trim().isEmpty()) &&
                        (fifthText == null || fifthText.trim().isEmpty())) {
        
                        lines.remove(0);
                    } else {
                        break;
                    }
                }
            }

            // Entferne nur komplett leere Blöcke (alle Zeilen sind leer)
            boolean allLinesEmpty = true;
            for (DiffProcessor.DiffLine line : current.getLines()) {
                String text = (current.getType() == DiffBlockType.ADDED) ? 
                    line.getNewText() : line.getOriginalText();
                if (text != null && !text.trim().isEmpty()) {
                    allLinesEmpty = false;
                    break;
                }
            }
            
            if (allLinesEmpty && current.getLines().size() > 0) {

                blocks.remove(i);
                i--;
            }
        }

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
    
    /**
     * Zeigt den Speichern-Dialog für Navigation (ohne Fenster zu schließen)
     * @return true wenn Navigation fortgesetzt werden soll, false wenn abgebrochen
     */
    private boolean showSaveDialogForNavigation() {
        // Checkboxen für Speicheroptionen
        CheckBox saveCurrentFormat = new CheckBox("Als " + getFormatDisplayName() + " speichern");
        CheckBox saveOriginalDocx = new CheckBox("Originale DOCX-Datei überschreiben");
        
        // Default: Nur aktuelles Format
        saveCurrentFormat.setSelected(true);
        saveOriginalDocx.setSelected(false);
        
        // DOCX-Optionen Button
        Button docxOptionsBtn = new Button("DOCX-Optionen");
        docxOptionsBtn.setStyle("-fx-font-size: 11px; -fx-padding: 4px 8px;");
        docxOptionsBtn.setOnAction(e -> {
            if (saveOriginalDocx.isSelected()) {
                showDocxOptionsDialog(saveOriginalDocx);
            } else {
                // Warnung anzeigen
                CustomAlert alert = new CustomAlert(Alert.AlertType.WARNING, "Warnung");
                alert.setHeaderText("DOCX nicht ausgewählt");
                alert.setContentText("Bitte aktivieren Sie zuerst die DOCX-Option, um die Einstellungen zu bearbeiten.");
                alert.applyTheme(currentThemeIndex);
                alert.initOwner(stage);
                alert.showAndWait();
            }
        });
        
        // DOCX-Optionen in HBox mit Checkbox
        HBox docxBox = new HBox(10);
        docxBox.getChildren().addAll(saveOriginalDocx, docxOptionsBtn);
        
        VBox content = new VBox(10);
        content.getChildren().addAll(saveCurrentFormat, docxBox);
        content.setPadding(new Insets(10));
        
        // CustomAlert verwenden
        CustomAlert alert = new CustomAlert(Alert.AlertType.CONFIRMATION, "Ungespeicherte Änderungen");
        alert.setHeaderText("Die Datei hat ungespeicherte Änderungen.");
        alert.setContentText("Was möchten Sie speichern, bevor Sie zum nächsten Kapitel wechseln?");
        
        // Theme anwenden
        alert.applyTheme(currentThemeIndex);
        
        // Owner setzen
        alert.initOwner(stage);
        
        // Content setzen - verwende setCustomContent für CustomAlert
        alert.setCustomContent(content);
        
        ButtonType saveButton = new ButtonType("Speichern & Weitermachen");
        ButtonType discardButton = new ButtonType("Verwerfen & Weitermachen");
        ButtonType diffButton = new ButtonType("🔍 Diff anzeigen");
        ButtonType cancelButton = new ButtonType("Abbrechen");
        
        alert.setButtonTypes(saveButton, discardButton, diffButton, cancelButton);
        
        Optional<ButtonType> result = alert.showAndWait();
        
        if (result.isPresent()) {
            if (result.get() == saveButton) {
                try {
                // Speichern basierend auf Auswahl
                if (saveCurrentFormat.isSelected()) {
                    saveFile();
                }
                if (saveOriginalDocx.isSelected() && originalDocxFile != null) {
                    saveToOriginalDocx();
                }
                return true; // Navigation fortsetzen
                } catch (Exception e) {
                    logger.error("Fehler beim Speichern: {}", e.getMessage());
                    return false; // Navigation abbrechen bei Fehler
                }
            } else if (result.get() == discardButton) {
                // Verwerfen und Navigation fortsetzen
                return true; // Navigation fortsetzen
            } else if (result.get() == diffButton) {
                // Diff anzeigen - verwende MainController Diff
                if (mainController != null && originalDocxFile != null) {
                    File mdFile = deriveSidecarFileFor(originalDocxFile, outputFormat);
                    DocxFile docxFile = new DocxFile(originalDocxFile);
                    mainController.showDetailedDiffDialog(docxFile, mdFile, null, outputFormat);
                }
                return false; // Navigation abbrechen (Dialog bleibt offen)
            } else if (result.get() == cancelButton) {
                // Abbrechen - keine Navigation
                return false; // Navigation abbrechen
            }
        }
        
        return false; // Standard: Navigation abbrechen
    }
    
    /**
     * Gibt den Anzeigenamen des aktuellen Formats zurück
     */
    private String getFormatDisplayName() {
        switch (outputFormat) {
            case MARKDOWN: return "Markdown (.md)";
            case PLAIN_TEXT: return "Text (.txt)";
            case HTML: return "HTML (.html)";
            default: return "HTML (.html)";
        }
    }
    
    /**
     * Speichert den Inhalt zurück in die originale DOCX-Datei
     */
    private void saveToOriginalDocx() {
        if (originalDocxFile == null || docxProcessor == null) {
            updateStatusError("Originale DOCX-Datei nicht verfügbar");
            return;
        }
        
        try {
            // WICHTIG: File Watcher VOR dem DOCX-Export stoppen
            if (mainController != null) {
                mainController.stopFileWatcher();
                
                // KRITISCH: Dialog unterdrücken während DOCX-Export
                mainController.setSuppressExternalChangeDialog(true);
                
                // Zusätzlich: Verhindere refreshDocxFiles() Aufrufe
                try {
                    Thread.sleep(100); // Kurze Verzögerung
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            
            // Konvertiere den aktuellen Inhalt zurück zu DOCX
            String currentContent = codeArea.getText();
            
            // WICHTIG: Prüfe ob der Inhalt leer ist!
            if (currentContent == null || currentContent.trim().isEmpty()) {
                updateStatusError("Kann leere Datei nicht speichern - Inhalt ist leer!");
                logger.error("Versuch, leere Datei zu speichern: {}", originalDocxFile.getName());
                return;
            }
            
            // Konvertiere basierend auf dem aktuellen Format
            if (outputFormat == DocxProcessor.OutputFormat.MARKDOWN) {
                // Markdown kann direkt konvertiert werden - mit DOCX-Optionen
                if (globalDocxOptions != null) {
                    docxProcessor.exportMarkdownToDocxWithOptions(currentContent, originalDocxFile, globalDocxOptions);
                } else {
                docxProcessor.exportMarkdownToDocx(currentContent, originalDocxFile);
                }
            } else if (outputFormat == DocxProcessor.OutputFormat.HTML) {
                // HTML zu Markdown konvertieren, dann zu DOCX - mit DOCX-Optionen
                String markdownContent = convertHtmlToMarkdown(currentContent);
                if (globalDocxOptions != null) {
                    docxProcessor.exportMarkdownToDocxWithOptions(markdownContent, originalDocxFile, globalDocxOptions);
                } else {
                docxProcessor.exportMarkdownToDocx(markdownContent, originalDocxFile);
                }
            } else if (outputFormat == DocxProcessor.OutputFormat.PLAIN_TEXT) {
                // Text zu Markdown konvertieren, dann zu DOCX - mit DOCX-Optionen
                String markdownContent = convertTextToMarkdown(currentContent);
                if (globalDocxOptions != null) {
                    docxProcessor.exportMarkdownToDocxWithOptions(markdownContent, originalDocxFile, globalDocxOptions);
                } else {
                docxProcessor.exportMarkdownToDocx(markdownContent, originalDocxFile);
                }
            }
            
            updateStatus("DOCX-Überschreibung erfolgreich: " + originalDocxFile.getName());
            
            // KRITISCH: Hash NACH dem DOCX-Export aktualisieren
            if (mainController != null) {
                mainController.updateDocxHashAfterAccept(originalDocxFile);
                mainController.markDocxFileAsUnchanged(originalDocxFile);
            }
            
            // File Watcher nach kurzer Verzögerung wieder aktivieren
            if (mainController != null) {
                Platform.runLater(() -> {
                    try {
                        Thread.sleep(500); // Längere Verzögerung für File Watcher
                        // Dialog-Unterdrückung wieder deaktivieren
                        mainController.setSuppressExternalChangeDialog(false);
                        
                        // File Watcher wieder aktivieren
                        String currentPath = mainController.getCurrentDirectoryPath();
                        if (currentPath != null && !currentPath.isEmpty()) {
                            File directory = new File(currentPath);
                            if (directory.exists() && directory.isDirectory()) {
                                mainController.startFileWatcher(directory);
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
        } catch (Exception e) {
            updateStatusError("Fehler beim Überschreiben der DOCX: " + e.getMessage());
            logger.error("Fehler beim DOCX-Überschreiben", e);
        }
    }
    
    /**
     * Konvertiert HTML zu Markdown
     */
    private String convertHtmlToMarkdown(String htmlContent) {
        String markdown = htmlContent;
        
        // Entferne HTML-Tags und konvertiere zu Markdown
        markdown = markdown.replaceAll("<h1[^>]*>(.*?)</h1>", "# $1");
        markdown = markdown.replaceAll("<h2[^>]*>(.*?)</h2>", "## $1");
        markdown = markdown.replaceAll("<h3[^>]*>(.*?)</h3>", "### $1");
        markdown = markdown.replaceAll("<p[^>]*>(.*?)</p>", "$1\n\n");
        markdown = markdown.replaceAll("<br[^>]*>", "\n");
        markdown = markdown.replaceAll("<strong[^>]*>(.*?)</strong>", "**$1**");
        markdown = markdown.replaceAll("<b[^>]*>(.*?)</b>", "**$1**");
        markdown = markdown.replaceAll("<em[^>]*>(.*?)</em>", "*$1*");
        markdown = markdown.replaceAll("<i[^>]*>(.*?)</i>", "*$1*");
        markdown = markdown.replaceAll("<u[^>]*>(.*?)</u>", "__$1__");
        
        // Entferne alle anderen HTML-Tags
        markdown = markdown.replaceAll("<[^>]*>", "");
        
        // Entferne HTML-Header und Footer
        markdown = markdown.replaceAll("<!DOCTYPE[^>]*>", "");
        markdown = markdown.replaceAll("<html[^>]*>.*?</html>", markdown);
        markdown = markdown.replaceAll("<head>.*?</head>", "");
        markdown = markdown.replaceAll("<body[^>]*>", "");
        markdown = markdown.replaceAll("</body>", "");
        
        return markdown.trim();
    }
    
    /**
     * Konvertiert Text zu Markdown
     */
    private String convertTextToMarkdown(String textContent) {
        // Einfache Konvertierung: Jede Zeile wird zu einem Absatz
        String[] lines = textContent.split("\n");
        StringBuilder markdown = new StringBuilder();
        
        for (String line : lines) {
            line = line.trim();
            if (!line.isEmpty()) {
                markdown.append(line).append("\n\n");
            }
        }
        
        return markdown.toString().trim();
    }
    
    /**
     * Auto-Formatierung für Markdown: Konvertiert einfache Zeilenumbrüche zu doppelten,
     * aber respektiert Code-Blöcke, Tabellen und andere spezielle Bereiche
     */
    private String autoFormatMarkdown(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        // Debug: Zeige Eingabetext
        
        String[] lines = text.split("\n");
        StringBuilder result = new StringBuilder();
        
        boolean inCodeBlock = false;
        boolean inTable = false;
        boolean inBlockquote = false;
        boolean inList = false;
        boolean inHorizontalRule = false;
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmedLine = line.trim();
            
            // Prüfe auf Code-Blöcke
            if (trimmedLine.startsWith("```")) {
                inCodeBlock = !inCodeBlock;
                result.append(line).append("\n");
                continue;
            }
            
            // Prüfe auf Tabellen (Zeile mit |)
            if (!inCodeBlock && trimmedLine.contains("|") && !trimmedLine.startsWith("```")) {
                if (!inTable) {
                    inTable = true;
                }
                result.append(line).append("\n");
                continue;
            } else if (inTable && !trimmedLine.contains("|")) {
                inTable = false;
                // Füge Leerzeile nach Tabelle hinzu
                result.append("\n");
            }
            
            // Prüfe auf Blockquotes
            if (!inCodeBlock && !inTable && trimmedLine.startsWith(">")) {
                if (!inBlockquote) {
                    inBlockquote = true;
                }
                result.append(line).append("\n");
                continue;
            } else if (inBlockquote && !trimmedLine.startsWith(">") && !trimmedLine.isEmpty()) {
                inBlockquote = false;
            }
            
            // Prüfe auf Listen
            if (!inCodeBlock && !inTable && !inBlockquote && 
                (trimmedLine.startsWith("- ") || trimmedLine.startsWith("* ") || 
                 trimmedLine.startsWith("+ ") || trimmedLine.matches("^\\d+\\.\\s.*"))) {
                if (!inList) {
                    inList = true;
                }
                result.append(line).append("\n");
                continue;
            } else if (inList && !trimmedLine.startsWith("- ") && !trimmedLine.startsWith("* ") && 
                      !trimmedLine.startsWith("+ ") && !trimmedLine.matches("^\\d+\\.\\s.*") && 
                      !trimmedLine.isEmpty()) {
                inList = false;
            }
            
            // Prüfe auf horizontale Linien
            if (!inCodeBlock && !inTable && trimmedLine.matches("^[-*_]{3,}$")) {
                inHorizontalRule = true;
                result.append(line).append("\n");
                continue;
            } else if (inHorizontalRule && !trimmedLine.isEmpty()) {
                inHorizontalRule = false;
            }
            
            // Prüfe auf Überschriften
            if (!inCodeBlock && !inTable && !inBlockquote && !inList && 
                trimmedLine.startsWith("#")) {
                result.append(line).append("\n");
                continue;
            }
            
            // Normale Zeilen - füge doppelten Zeilenumbruch hinzu wenn nötig
            if (!inCodeBlock && !inTable && !inBlockquote && !inList && !inHorizontalRule) {
                if (trimmedLine.isEmpty()) {
                    result.append("\n");
                } else {
                    // Prüfe ob die nächste Zeile auch Inhalt hat
                    boolean nextLineHasContent = (i + 1 < lines.length) && !lines[i + 1].trim().isEmpty();
                    if (nextLineHasContent) {
                        // Füge doppelten Zeilenumbruch hinzu
                        result.append(line).append("\n\n");
                    } else {
                        result.append(line).append("\n");
                    }
                }
            } else {
                result.append(line).append("\n");
            }
        }
        
        return result.toString();
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
                
                String markdownContent = cleanTextForExport(codeArea.getText());
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
                // Normaler Text mit Markdown-Formatierung - verwende die ursprüngliche Zeile für korrekte Leerzeichen
                String formattedLine = convertMarkdownInlineToRTF(line);
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
                
                String markdownContent = cleanTextForExport(codeArea.getText());
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
                
                // EINGEBAUTE UNDO-FUNKTIONALITÄT VERWENDEN - kein manueller Aufruf nötig
                
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
        // WICHTIG: Setze originalContent auf leeren String für ungespeicherte Änderungen
        originalContent = "";
        // EINGEBAUTE UNDO-FUNKTIONALITÄT VERWENDEN - kein manueller Aufruf nötig
        updateStatus("Neue Datei");
    }
    
    // Public Methoden für externe Verwendung
    public void setText(String text) {
        // EINGEBAUTE UNDO-FUNKTIONALITÄT VERWENDEN - kein manueller Aufruf nötig
        
        // WICHTIG: Original-Content vor Auto-Formatierung speichern
        String originalTextBeforeFormatting = cleanTextForComparison(text);
        
        // Auto-Formatierung für Markdown anwenden
        String formattedText = autoFormatMarkdown(text);
        boolean wasFormatted = !text.equals(formattedText);
        
        
        codeArea.replaceText(formattedText);
        
        // WICHTIG: originalContent auf den ORIGINALEN Text setzen (vor Auto-Formatierung)
        // Nur wenn Auto-Formatierung angewendet wurde, sonst normal
        if (wasFormatted) {
            originalContent = originalTextBeforeFormatting; // Original vor Auto-Formatierung
        } else {
            originalContent = cleanTextForComparison(formattedText); // Normal
        }
        // Debug entfernt
        
        // Status zurücksetzen beim Laden neuer Inhalte
        if (wasFormatted) {
            markAsChanged(); // Markiere als ungespeichert wenn Auto-Formatierung angewendet wurde
            // WICHTIG: updateStatusDisplay() wird bereits von markAsChanged() aufgerufen
            // Zusätzlich: Status-Label mit korrekter Farbe setzen
            Platform.runLater(() -> {
                if (lblStatus != null) {
                    lblStatus.setText("⚠ Ungespeicherte Änderungen (auto-formatiert)");
                    lblStatus.setStyle("-fx-text-fill: #ff6b35; -fx-font-weight: bold; -fx-background-color: #fff3cd; -fx-padding: 2 6 2 6; -fx-background-radius: 3;");
                }
            });
        } else {
        markAsSaved();
            updateStatus("Text geladen");
        }
        
        // Cursor an den Anfang setzen
        codeArea.displaceCaret(0);
        codeArea.requestFollowCaret();
    }
    
    /**
     * Ersetzt den Text ohne originalContent zu ändern (für Diff-Änderungen)
     */
    public void replaceTextWithoutUpdatingOriginal(String text) {
        codeArea.replaceText(text);
        
        // Cursor an den Anfang setzen
        codeArea.displaceCaret(0);
        codeArea.requestFollowCaret();
        
        // WICHTIG: Markiere als geändert, damit "ungespeicherte Änderungen" angezeigt wird
        markAsChanged();
        
        // Überschreibe den Status mit der richtigen Farbe (nach markAsChanged)
        Platform.runLater(() -> {
            if (lblStatus != null) {
                lblStatus.setText("⚠ Text wurde durch Diff geändert");
                lblStatus.setStyle("-fx-text-fill: #ff6b35; -fx-font-weight: bold; -fx-background-color: #fff3cd; -fx-padding: 2 6 2 6; -fx-background-radius: 3;");
            }
        });
    }
    
    public void setCurrentFile(File file) {
        this.currentFile = file;
    }
    
    public void setIsCompleteDocument(boolean isCompleteDocument) {
        this.isCompleteDocument = isCompleteDocument;
        
        // Verstecke Navigation-Buttons für Bücher
        if (isCompleteDocument) {
            hideNavigationButtons();
        }
    }
    
    private void hideNavigationButtons() {
        if (btnPreviousChapter != null) {
            btnPreviousChapter.setVisible(false);
            btnPreviousChapter.setManaged(false);
        }
        if (btnNextChapter != null) {
            btnNextChapter.setVisible(false);
            btnNextChapter.setManaged(false);
        }
    }
    
    private void showDocxExportDialog() {
        // Erweiterter Dialog für Bücher mit DOCX-Optionen
        CustomStage exportStage = StageManager.createExportStage("DOCX-Export", stage);
        exportStage.setTitle("📤 DOCX-Export für Buch");
        exportStage.initModality(Modality.APPLICATION_MODAL);
        exportStage.initOwner(stage);
        
        // CSS-Styles für den Dialog anwenden
        Platform.runLater(() -> {
            try {
                String cssPath = ResourceManager.getCssResource("css/manuskript.css");
                if (exportStage.getScene() != null) {
                    exportStage.getScene().getStylesheets().add(cssPath);
                }
                
                // Theme für den Dialog setzen
                exportStage.setFullTheme(currentThemeIndex);
            } catch (Exception e) {
                logger.error("Fehler beim Anwenden der CSS-Styles für DOCX-Export-Dialog", e);
            }
        });
        
        // Hauptcontainer
        VBox exportContent = new VBox(15);
        exportContent.setPadding(new Insets(20));
        
        // Info-Text
        Label infoLabel = new Label("✅ Buch erfolgreich gespeichert!\n\nMöchten Sie auch eine DOCX-Datei erstellen?");
        infoLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #2e7d32;");
        infoLabel.setWrapText(true);
        
        // DOCX-Optionen
        Label optionsLabel = new Label("📄 DOCX-Optionen:");
        optionsLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        
        VBox optionsBox = new VBox(10);
        
        // DOCX-Optionen Button
        HBox docxOptionsBox = new HBox(10);
        docxOptionsBox.setAlignment(Pos.CENTER_LEFT);
        Button docxOptionsBtn = new Button("⚙️ DOCX-Optionen anpassen");
        docxOptionsBtn.setStyle("-fx-font-size: 12px; -fx-padding: 6px 12px;");
        
        docxOptionsBtn.setOnAction(e -> {
            showDocxOptionsDialog(null); // null da wir keine CheckBox haben
        });
        
        docxOptionsBox.getChildren().add(docxOptionsBtn);
        optionsBox.getChildren().add(docxOptionsBox);
        
        // Export-Verzeichnis
        Label dirLabel = new Label("📁 Export-Verzeichnis:");
        dirLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        
        VBox dirOptions = new VBox(8);
        
        HBox dirSelection = new HBox(10);
        dirSelection.setAlignment(Pos.CENTER_LEFT);
        
        Label dirPathLabel = new Label("Unterverzeichnis:");
        dirPathLabel.setStyle("-fx-font-weight: bold;");
        
        TextField dirPathField = new TextField();
        dirPathField.setPromptText("Unterverzeichnis (optional) eingeben...");
        dirPathField.setEditable(true);
        dirPathField.setPrefWidth(300);
        
        dirSelection.getChildren().addAll(dirPathLabel, dirPathField);
        dirOptions.getChildren().add(dirSelection);
        
        // Dateiname
        Label filenameLabel = new Label("📄 Dateiname:");
        filenameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        
        HBox filenameBox = new HBox(10);
        filenameBox.setAlignment(Pos.CENTER_LEFT);
        
        Label filenamePrefixLabel = new Label("Präfix:");
        filenamePrefixLabel.setStyle("-fx-font-weight: bold;");
        
        TextField filenameField = new TextField();
        filenameField.setPromptText("Dateiname (ohne Endung)...");
        filenameField.setEditable(true);
        filenameField.setPrefWidth(300);
        
        // Standard-Dateiname setzen
        if (currentFile != null) {
            String fileName = currentFile.getName();
            // Entferne .md und .gesamt
            String baseName = fileName.replace(".md", "").replace(".gesamt", "");
            // Nur " Buch" hinzufügen, wenn es noch nicht enthalten ist
            if (!baseName.contains("Buch")) {
                baseName = baseName + " Buch";
            }
            filenameField.setText(baseName);
        }
        
        filenameBox.getChildren().addAll(filenamePrefixLabel, filenameField);
        
        // Alle Inhalte zusammenfassen
        exportContent.getChildren().addAll(
            infoLabel,
            new Separator(),
            optionsLabel,
            optionsBox,
            new Separator(),
            dirLabel,
            dirOptions,
            new Separator(),
            filenameLabel,
            filenameBox
        );
        
        // Buttons
        HBox buttonBox = new HBox(15);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new Insets(20, 20, 0, 20));
        
        Button cancelButton = new Button("❌ Abbrechen");
        cancelButton.setStyle("-fx-padding: 8px 16px;");
        
        Button exportButton = new Button("📤 DOCX erstellen");
        exportButton.setStyle("-fx-padding: 8px 16px; -fx-background-color: #4caf50; -fx-text-fill: white;");
        
        // Content mit Buttons kombinieren
        VBox mainContent = new VBox(15);
        mainContent.getChildren().addAll(exportContent, buttonBox);
        mainContent.setPadding(new Insets(20));
        mainContent.getStyleClass().add("export-dialog-content");
        // Theme-Hintergrund direkt setzen
        String[] themeBackgrounds = {"#ffffff", "#1a1a1a", "#f3e5f5", "#1e3a8a", "#064e3b", "#581c87"};
        mainContent.setStyle("-fx-background-color: " + themeBackgrounds[currentThemeIndex] + ";");
        
        // Theme auf den Content anwenden
        applyThemeToNode(mainContent, currentThemeIndex);
        
        // Event-Handler für Buttons
        cancelButton.setOnAction(e -> {
            exportStage.close();
        });
        
        exportButton.setOnAction(e -> {
            
            // Erstelle DOCX-Datei mit den Optionen
            if (currentFile != null) {
                String data = cleanTextForExport(codeArea.getText());
                if (data == null) data = "";
                
                // Bestimme den Ausgabedateinamen
                String filename = filenameField.getText().trim();
                if (filename.isEmpty()) {
                    // Standard: Basis-Name + " Buch"
                    String baseName = currentFile.getName().replace(".md", "").replace(".gesamt", "");
                    filename = baseName + " Buch";
                }
                
                // Bestimme das Ausgabeverzeichnis - verwende das gleiche Verzeichnis wie die MD-Datei
                File outputDir = currentFile.getParentFile(); // Gleiches Verzeichnis wie die MD-Datei
                String subDir = dirPathField.getText().trim();
                if (!subDir.isEmpty()) {
                    outputDir = new File(outputDir, subDir);
                    if (!outputDir.exists()) {
                        outputDir.mkdirs();
                    }
                }
                
                File docxFile = new File(outputDir, filename + ".docx");
                
                
                // Erstelle DOCX-Datei
                createDocxFileWithOptions(docxFile, data);
            }
            
            exportStage.close();
        });
        
        buttonBox.getChildren().addAll(cancelButton, exportButton);
        
        Scene scene = new Scene(mainContent);
        exportStage.setSceneWithTitleBar(scene);
        
        // CSS-Stylesheets laden
        String stylesCss = ResourceManager.getCssResource("css/styles.css");
        String editorCss = ResourceManager.getCssResource("css/editor.css");
        if (stylesCss != null && !scene.getStylesheets().contains(stylesCss)) scene.getStylesheets().add(stylesCss);
        if (editorCss != null && !scene.getStylesheets().contains(editorCss)) scene.getStylesheets().add(editorCss);
        
        // Theme auf die Stage anwenden
        applyThemeToNode(scene.getRoot(), currentThemeIndex);
        
        // Dialog anzeigen
        exportStage.showAndWait();
    }
    
    private void createDocxFileWithOptions(File docxFile, String content) {
        // Erstelle DOCX-Datei mit den globalen DOCX-Optionen
        try {
            if (docxProcessor == null) {
                docxProcessor = new DocxProcessor();
            }
            
            // Stelle sicher, dass globalDocxOptions nicht null ist
            if (globalDocxOptions == null) {
                globalDocxOptions = new DocxOptions();
            }
            
            
            // Exportiere Markdown zu DOCX mit den globalen Optionen
            docxProcessor.exportMarkdownToDocxWithOptions(content, docxFile, globalDocxOptions);
            
            updateStatus("DOCX-Datei erstellt: " + docxFile.getName());
            
        } catch (Exception e) {
            logger.error("Fehler beim Erstellen der DOCX-Datei", e);
            updateStatusError("Fehler beim Erstellen der DOCX-Datei: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void createDocxFile(File mdFile, String content) {
        // Erstelle DOCX-Datei automatisch ohne nachzufragen
        String baseName = mdFile.getName().replace(".md", "");
        File docxFile = new File(mdFile.getParent(), baseName + ".docx");
        
        try {
            // Erstelle DocxProcessor falls nicht vorhanden
            if (docxProcessor == null) {
                docxProcessor = new DocxProcessor();
            }
            
            // Verwende den DocxProcessor für den Export
            docxProcessor.exportMarkdownToDocx(content, docxFile);
            updateStatus("DOCX-Datei erstellt: " + docxFile.getName());
            
        } catch (Exception e) {
            logger.error("Fehler beim DOCX-Export", e);
            updateStatusError("Fehler beim DOCX-Export: " + e.getMessage());
        }
    }
    
    /**
     * Stellt sicher, dass eine MD-Datei existiert, wenn der Editor aufgerufen wird
     * Legt die Datei sofort an, wenn sie noch nicht existiert
     */
    public void ensureMdFileExists() {
        if (currentFile != null && !currentFile.exists()) {
            try {
                // Erstelle die Datei mit dem aktuellen Editor-Inhalt
                String currentContent = getText();
                if (currentContent == null) {
                    currentContent = "";
                }
                
                // Stelle sicher, dass das Verzeichnis existiert
                File parentDir = currentFile.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs();
                }
                
                // Schreibe den aktuellen Inhalt in die Datei
                Files.write(currentFile.toPath(), currentContent.getBytes(StandardCharsets.UTF_8));
                
                // Aktualisiere originalContent für Change-Detection
                originalContent = cleanTextForComparison(currentContent);
                
                updateStatus("MD-Datei angelegt: " + currentFile.getName());
                
            } catch (IOException e) {
                logger.error("Fehler beim automatischen Anlegen der MD-Datei: {}", e.getMessage());
                updateStatusError("Fehler beim Anlegen der MD-Datei: " + e.getMessage());
            }
        }
    }
    
    /**
     * Setzt die originale DOCX-Datei für Rückkonvertierung
     */
    public void setOriginalDocxFile(File docxFile) {
        this.originalDocxFile = docxFile;
    }
    
    /**
     * Gibt die originale DOCX-Datei zurück
     */
    public File getOriginalDocxFile() {
        return this.originalDocxFile;
    }
    
    public File getCurrentFile() {
        return this.currentFile;
    }
    
    public String getText() {
        return codeArea.getText();
    }

    /**
     * Cursor-Position für Kontext-Erzeugung in KI-Funktionen
     */
    public int getCaretPosition() {
        return codeArea != null ? codeArea.getCaretPosition() : 0;
    }
    
    public CustomStage getStage() {
        return stage;
    }
    
    public void setStage(CustomStage stage) {
        this.stage = stage;
        
        // WICHTIG: CSS-Datei laden, wenn die Stage gesetzt wird
        Platform.runLater(() -> {
            if (stage.getScene() != null) {
                // CSS-Datei laden
                String cssPath = ResourceManager.getCssResource("css/manuskript.css");
                if (cssPath != null && !stage.getScene().getStylesheets().contains(cssPath)) {
                    stage.getScene().getStylesheets().add(cssPath);
                } else {
                    logger.warn("CSS-Datei konnte in setStage nicht geladen werden: {}", cssPath);
                }
            }
        });
            
        // WICHTIG: Fenster-Eigenschaften SOFORT laden (nicht in Platform.runLater)
        loadWindowProperties();
        
        // Close-Request-Handler für Speichern-Abfrage
        stage.setOnCloseRequest(event -> {
            boolean hasChanges = hasUnsavedChanges();
            
            if (hasChanges) {
                event.consume(); // Verhindere Schließen
                showSaveDialog();
            } else {
                // Keine Änderungen - Fenster schließen und prüfen ob es das letzte ist
                Platform.runLater(() -> {
                    // Prüfe ob noch andere Fenster offen sind
                    boolean hasOtherWindows = false;
                    for (Window window : Window.getWindows()) {
                        if (window != stage && window.isShowing()) {
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
        });
        
        // WICHTIG: Listener werden in addWindowPropertyListeners() hinzugefügt
    }
    
    public void setOutputFormat(DocxProcessor.OutputFormat format) {
        this.outputFormat = format;
        
        // Export-Buttons nur für Markdown anzeigen
        boolean isMarkdown = (format == DocxProcessor.OutputFormat.MARKDOWN);
                    btnExport.setVisible(isMarkdown);
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
        // CustomStage-Titel setzen (FXML-Titelleiste entfernt)
        if (stage instanceof CustomStage) {
            CustomStage customStage = (CustomStage) stage;
            customStage.setCustomTitle(title);
        }
        // Status aktualisieren
        updateStatusDisplay();
    }
    
    /**
     * Aktualisiert die Anzeige für ungespeicherte Änderungen
     */
    private void updateStatusDisplay() {
        // Aktualisiere den Fenstertitel mit ungespeicherten Änderungen
        if (stage != null) {
            String currentTitle = stage.getTitle();
            if (currentTitle != null) {
                // Entferne vorherige Änderungsanzeige
                String cleanTitle = currentTitle.replace(" ⚠", "");
                
                if (hasUnsavedChanges) {
                    stage.setTitle(cleanTitle + " ⚠");
                } else {
                    stage.setTitle(cleanTitle);
                }
                
                // Auch CustomStage aktualisieren
                if (stage instanceof CustomStage) {
                    CustomStage customStage = (CustomStage) stage;
                    customStage.setCustomTitle(hasUnsavedChanges ? cleanTitle + " ⚠" : cleanTitle);
                }
            }
        }
        
        // Zusätzlich: Farbige Anzeige im Status-Label (falls verfügbar)
        if (lblStatus != null) {
            if (hasUnsavedChanges) {
                lblStatus.setText("⚠ Ungespeicherte Änderungen");
                lblStatus.setStyle("-fx-text-fill: #ff6b35; -fx-font-weight: bold; -fx-background-color: #fff3cd; -fx-padding: 2 6 2 6; -fx-background-radius: 3;");
            } else {
            lblStatus.setText("Bereit");
                lblStatus.setStyle("-fx-text-fill: #28a745; -fx-font-weight: normal; -fx-background-color: #d4edda; -fx-padding: 2 6 2 6; -fx-background-radius: 3;");
            }
        }
    }
    
    /**
     * Markiert das Dokument als geändert
     */
    private void markAsChanged() {
        hasUnsavedChanges = true;
        updateStatusDisplay();
    }
    
    /**
     * Markiert das Dokument als gespeichert
     */
    private void markAsSaved() {
        hasUnsavedChanges = false;
        updateStatusDisplay();
    }
    
    /**
     * Markiert das Dokument als gespeichert (public für externe Aufrufe)
     */
    public void markAsSavedPublic() {
        markAsSaved();
    }
    
    /**
     * Schließt das Editor-Fenster programmatisch
     */
    public void closeWindow() {
        if (stage != null) {
            stage.close();
        }
    }
    
    /**
     * Setzt das Theme vom Hauptfenster - für Synchronisation
     */
    public void setThemeFromMainWindow(int themeIndex) {
        // WICHTIG: Theme IMMER vom Hauptfenster übernehmen, nicht aus Preferences
        this.currentThemeIndex = themeIndex;
        
        // WICHTIG: CustomStage Theme aktualisieren
        if (stage instanceof CustomStage) {
            CustomStage customStage = (CustomStage) stage;
            customStage.setFullTheme(themeIndex);
        }
        
        // WICHTIG: Theme in Preferences speichern für Persistierung
        preferences.putInt("editor_theme", themeIndex);
        preferences.putInt("main_window_theme", themeIndex);
        
        
        // WICHTIG: Theme sofort anwenden
        applyTheme(themeIndex);
        updateThemeButtonTooltip();
        
        // Zusätzlicher verzögerter Theme-Refresh für bessere Kompatibilität
        // Dies stellt sicher, dass die RichTextFX CodeArea korrekt aktualisiert wird
        Platform.runLater(() -> {
            applyTheme(themeIndex);
            Platform.runLater(() -> {
                applyTheme(themeIndex);
            });
        });
    }
    
    public void setMainController(MainController mainController) {
        this.mainController = mainController;
        
        // Aktualisiere die Navigation-Buttons nach dem Setzen des MainControllers
        Platform.runLater(() -> {
            updateNavigationButtons();
        });
    }
    
    // ===== MAKRO-FUNKTIONALITÄT =====
    

    
    private void setupMacroPanel() {
        // Macro-Fenster erstellen
        createMacroWindow();
    }
    
    private void createMacroWindow() {
        macroStage = StageManager.createStage("Makros");
        macroStage.setTitle("Makro-Verwaltung");
        macroStage.setWidth(1200);
        macroStage.setHeight(800);
        
        // WICHTIG: Theme sofort setzen
        macroStage.setTitleBarTheme(currentThemeIndex);
        macroStage.initModality(Modality.NONE);
        macroStage.initOwner(stage);
        
        // Makro-Panel programmatisch erstellen
        VBox macroPanel = createMacroPanel();
        
        Scene macroScene = new Scene(macroPanel);
        // CSS mit ResourceManager laden (manuskript.css für alle Themes)
        String manuskriptCss = ResourceManager.getCssResource("css/manuskript.css");
        if (manuskriptCss != null) {
            macroScene.getStylesheets().add(manuskriptCss);
        }
        macroStage.setSceneWithTitleBar(macroScene);
        
        // Fenster-Position speichern/laden
        loadMacroWindowProperties();
        
        // Event-Handler für Fenster-Schließung
        macroStage.setOnCloseRequest(event -> {
            // Position beim Schließen explizit speichern
            if (preferences != null) {
                preferences.putDouble("macro_window_x", macroStage.getX());
                preferences.putDouble("macro_window_y", macroStage.getY());
                preferences.putDouble("macro_window_width", macroStage.getWidth());
                preferences.putDouble("macro_window_height", macroStage.getHeight());
            }
            macroWindowVisible = false;
            event.consume(); // Verhindert das tatsächliche Schließen
            macroStage.hide();
        });
    }
    private VBox createMacroPanel() {
        VBox macroPanel = new VBox(10);
        macroPanel.getStyleClass().add("macro-panel");
        
        // CSS-Klassen verwenden statt inline Styles
            macroPanel.setStyle(""); // CSS-Klassen verwenden
        
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
        
        ComboBox<String> cmbMacroList = new ComboBox<>();
        cmbMacroList.setPromptText("Makro auswählen...");
        cmbMacroList.setPrefWidth(200.0);
        
        Button btnNewMacro = new Button("Neues Makro");
        btnNewMacro.getStyleClass().addAll("button", "primary");
        btnNewMacro.setMinHeight(34);
        
        Button btnDeleteMacro = new Button("Makro löschen");
        btnDeleteMacro.getStyleClass().addAll("button", "danger");
        btnDeleteMacro.setMinHeight(34);
        
        Button btnSaveMacro = new Button("Makro speichern");
        btnSaveMacro.getStyleClass().addAll("button", "primary");
        btnSaveMacro.setMinHeight(34);
        
        Region spacer = new Region();
spacer.setStyle("-fx-background-color: transparent;");
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Button btnRunMacro = new Button("Makro ausführen");
        btnRunMacro.getStyleClass().addAll("button", "success");
        btnRunMacro.setMinHeight(34);
        
        macroControls.getChildren().addAll(macroLabel, cmbMacroList, btnNewMacro, btnDeleteMacro, btnSaveMacro, spacer, btnRunMacro);
        
        // Makro-Details Panel
        VBox macroDetailsPanel = new VBox(5);
        macroDetailsPanel.setVisible(false);
        VBox.setVgrow(macroDetailsPanel, Priority.ALWAYS);
        
        Label stepsLabel = new Label("Makro-Schritte:");
        
        // Makro-Schritt-Beschreibung
        HBox descriptionBox = new HBox(10);
        descriptionBox.setAlignment(Pos.CENTER_LEFT);
        
        Label descLabel = new Label("Schritt-Beschreibung:");
        
        TextField txtMacroStepDescription = new TextField();
        txtMacroStepDescription.setPromptText("Beschreibung des Schritts...");
        txtMacroStepDescription.setPrefWidth(400.0);
        
        descriptionBox.getChildren().addAll(descLabel, txtMacroStepDescription);
        
        // Makro-Schritt-Eingabe
        HBox searchReplaceBox = new HBox(10);
        searchReplaceBox.setAlignment(Pos.CENTER_LEFT);
        
        Label searchLabel = new Label("Suchen:");
        
        TextField txtMacroSearch = new TextField();
        txtMacroSearch.setPromptText("Suchtext eingeben...");
        txtMacroSearch.setPrefWidth(200.0);
        
        Label replaceLabel = new Label("Ersetzen:");
        
        TextField txtMacroReplace = new TextField();
        txtMacroReplace.setPromptText("Ersetzungstext eingeben...");
        txtMacroReplace.setPrefWidth(200.0);
        
        CheckBox chkMacroRegex = new CheckBox("Regex");
        CheckBox chkMacroCaseSensitive = new CheckBox("Case");
        CheckBox chkMacroWholeWord = new CheckBox("Word");
        
        // btnMacroRegexHelp wurde entfernt
        
        searchReplaceBox.getChildren().addAll(searchLabel, txtMacroSearch, replaceLabel, txtMacroReplace, 
                                             chkMacroRegex, chkMacroCaseSensitive, chkMacroWholeWord);
        
        // Makro-Schritte Tabelle
        TableView<MacroStep> tblMacroSteps = new TableView<>();
        // Eigene Style-Klasse, damit wir die Hintergrundfarbe sicher und isoliert stylen können
        tblMacroSteps.getStyleClass().add("macro-table");
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
                    MacroStep step = ((TableView<MacroStep>) getTableView()).getItems().get(getIndex());
                    if (step != null) {
                        step.setEnabled(checkBox.isSelected());
                        // Automatisch speichern bei Änderung
                        saveMacros();
                        updateStatus("Schritt " + step.getStepNumber() + " " + (checkBox.isSelected() ? "aktiviert" : "deaktiviert"));
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
                    MacroStep step = ((TableView<MacroStep>) getTableView()).getItems().get(getIndex());
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
        btnAddStep.getStyleClass().add("button");
        btnAddStep.setMinHeight(32);
        btnAddStep.setPrefHeight(32);
        btnAddStep.setMinWidth(200);
        btnAddStep.setPrefWidth(220);
        
        Button btnRemoveStep = new Button("Schritt entfernen");
        btnRemoveStep.getStyleClass().add("button");
        btnRemoveStep.setMinHeight(32);
        btnRemoveStep.setPrefHeight(32);
        btnRemoveStep.setMinWidth(200);
        btnRemoveStep.setPrefWidth(220);
        
        Button btnMoveStepUp = new Button("↑");
        btnMoveStepUp.getStyleClass().add("button");
        btnMoveStepUp.setMinHeight(32);
        btnMoveStepUp.setPrefHeight(32);
        btnMoveStepUp.setMinWidth(56);
        btnMoveStepUp.setPrefWidth(56);
        
        Button btnMoveStepDown = new Button("↓");
        btnMoveStepDown.getStyleClass().add("button");
        btnMoveStepDown.setMinHeight(32);
        btnMoveStepDown.setPrefHeight(32);
        btnMoveStepDown.setMinWidth(56);
        btnMoveStepDown.setPrefWidth(56);
        
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
        // btnMacroRegexHelp wurde entfernt
        
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
        // btnMacroRegexHelp wurde entfernt
        
        // Makros laden
        loadMacros();
        
        macroPanel.getChildren().addAll(macroControls, macroDetailsPanel);
        return macroPanel;
    }
    
    private void loadMacroWindowProperties() {
        if (preferences != null) {
            // Robuste Validierung der Preferences mit sinnvollen Standardwerten
            double x = preferences.getDouble("macro_window_x", 100);
            double y = preferences.getDouble("macro_window_y", 100);
            double width = preferences.getDouble("macro_window_width", 1200);
            double height = preferences.getDouble("macro_window_height", 800);
            
            // Validierung: Position muss auf dem Bildschirm sein
            if (x < 0 || x > 3000 || y < 0 || y > 2000) {
                logger.warn("Ungültige Position ({},{}) für Makro-Fenster, setze Standard 100,100", x, y);
                x = 100;
                y = 100;
            }
            
            // Validierung: Größe muss sinnvoll sein
            if (width < 400 || width > 2000 || height < 300 || height > 1500) {
                logger.warn("Ungültige Größe ({}x{}) für Makro-Fenster, setze Standard 1200x800", width, height);
                width = 1200;
                height = 800;
            }
            
            macroStage.setX(x);
            macroStage.setY(y);
            macroStage.setWidth(width);
            macroStage.setHeight(height);
            
            
            // Fenster-Position und Größe speichern (nur wenn gültig)
            macroStage.xProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal.doubleValue() >= 0 && newVal.doubleValue() <= 3000) {
                    preferences.putDouble("macro_window_x", newVal.doubleValue());
                }
            });
            macroStage.yProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal.doubleValue() >= 0 && newVal.doubleValue() <= 2000) {
                    preferences.putDouble("macro_window_y", newVal.doubleValue());
                }
            });
            macroStage.widthProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal.doubleValue() >= 400 && newVal.doubleValue() <= 2000) {
                    preferences.putDouble("macro_window_width", newVal.doubleValue());
                }
            });
            macroStage.heightProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal.doubleValue() >= 300 && newVal.doubleValue() <= 1500) {
                    preferences.putDouble("macro_window_height", newVal.doubleValue());
                }
            });
        }
    }
    
    private void toggleMacroPanel() {
        
        // Makro-Fenster erstellen, falls es noch nicht existiert
        if (macroStage == null) {
            createMacroWindow();
        }
        
        macroWindowVisible = !macroWindowVisible;
        
        if (macroWindowVisible) {
            // Makro-Fenster öffnen
            
            // Position und Größe auf Bildschirm setzen, falls außerhalb oder zu klein
            if (macroStage.getX() < 0 || macroStage.getY() < 0 || 
                macroStage.getX() > 2000 || macroStage.getY() > 2000 ||
                macroStage.getWidth() < 800 || macroStage.getHeight() < 600) {
                logger.warn("Position/Größe außerhalb des Bildschirms, setze auf 100,100 mit 1200x800");
                macroStage.setX(100);
                macroStage.setY(100);
                macroStage.setWidth(1200);
                macroStage.setHeight(800);
            }
            
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
        textAnalysisStage = StageManager.createStage("Textanalyse");
        textAnalysisStage.setTitle("Textanalyse");
        textAnalysisStage.setWidth(800);
        textAnalysisStage.setHeight(600);
        
        // WICHTIG: Theme sofort setzen
        textAnalysisStage.setTitleBarTheme(currentThemeIndex);
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
        textAnalysisStage.setSceneWithTitleBar(textAnalysisScene);
        
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
                                // (da die Positionen sortiert sind, werden alle weiteren auch zu weit sein)
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

                                    // Markierten Bereich hinzufügen - einfach abwechselnd gelb/blau
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
            
            // Debug entfernt
            
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
            
            // Apply marking - ALLE Vorkommen der Wörter markieren, die in Wiederholungen gefunden wurden
            if (!wiederholungen.isEmpty()) {
                String content2 = codeArea.getText();
                
                // Sammle ALLE Wörter, die in Wiederholungen gefunden wurden
                Set<String> repeatedWords = new HashSet<>();
                for (Wortwiederholung w : wiederholungen) {
                    repeatedWords.add(w.word);
                }
                
                if (!repeatedWords.isEmpty()) {
                    // Create a pattern for all words to be marked
                    StringBuilder patternBuilder = new StringBuilder();
                    for (String word : repeatedWords) {
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
                        
                        // Markiere ALLE Vorkommen der Wörter, die in Wiederholungen gefunden wurden
                        if (repeatedWords.contains(matchedWord)) {
                            // Count previous occurrences of the same word to determine style
                            int occurrenceCount = 0;
                            Matcher countMatcher = markPattern.matcher(content2.substring(0, start));
                            while (countMatcher.find()) {
                                if (countMatcher.group(1).toLowerCase().equals(matchedWord)) {
                                    occurrenceCount++;
                                }
                            }
                            
                            // Erste Wiederholung eines Wortes = gelb, zweite = blau, dritte = gelb, etc.
                            String styleClass = (occurrenceCount % 2 == 0) ? "search-match-first" : "search-match-second";
                            
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
                        
                        // Update cache for navigation - ALLE Vorkommen der Wörter
                        List<Integer> allPositions = new ArrayList<>();
                        Matcher navMatcher = markPattern.matcher(content2);
                        while (navMatcher.find()) {
                            if (repeatedWords.contains(navMatcher.group(1).toLowerCase())) {
                                allPositions.add(navMatcher.start());
                            }
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
                                        // Verwende vorhandene Hervorhebungs-Klasse, damit Styles sicher greifen
                                        spansBuilder.add(Collections.singleton("highlight-yellow"), end - start);
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
                                            // Einheitliche Hervorhebung verwenden
                                            spansBuilder.add(Collections.singleton("highlight-orange"), end - start);
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
        // Dialog themen-konsistent stylen
        styleDialog(dialog);
        
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
            CustomAlert alert = new CustomAlert(Alert.AlertType.CONFIRMATION, "Makro löschen");
            alert.setHeaderText("Makro löschen bestätigen");
            alert.setContentText("Möchten Sie das Makro '" + currentMacro.getName() + "' wirklich löschen?");
            alert.applyTheme(currentThemeIndex);
            alert.initOwner(stage);
            
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

    private void styleDialog(Dialog<?> dialog) {
        if (dialog == null) return;
        DialogPane pane = dialog.getDialogPane();
        // CSS hinzufügen (styles.css + editor.css + manuskript.css), damit Theme greift
        String stylesCss = ResourceManager.getCssResource("css/styles.css");
        String editorCss = ResourceManager.getCssResource("css/editor.css");
        String manuskriptCss = ResourceManager.getCssResource("css/manuskript.css");
        if (stylesCss != null && !pane.getStylesheets().contains(stylesCss)) {
            pane.getStylesheets().add(stylesCss);
        }
        if (editorCss != null && !pane.getStylesheets().contains(editorCss)) {
            pane.getStylesheets().add(editorCss);
        }
        if (manuskriptCss != null && !pane.getStylesheets().contains(manuskriptCss)) {
            pane.getStylesheets().add(manuskriptCss);
        }

        // Theme-Klassen vom Hauptfenster übernehmen
        if (currentThemeIndex == 0) pane.getStyleClass().add("weiss-theme");
        else if (currentThemeIndex == 2) pane.getStyleClass().add("pastell-theme");
        else if (currentThemeIndex == 3) pane.getStyleClass().addAll("theme-dark", "blau-theme");
        else if (currentThemeIndex == 4) pane.getStyleClass().addAll("theme-dark", "gruen-theme");
        else if (currentThemeIndex == 5) pane.getStyleClass().addAll("theme-dark", "lila-theme");
        else pane.getStyleClass().add("theme-dark");

        // Zusätzlich: Header-Panel sicher inline stylen, sobald aufgebaut
        final String backgroundColor = THEMES[currentThemeIndex][0];
        final String textColor = THEMES[currentThemeIndex][1];
        dialog.setOnShown(ev -> {
            Node headerPanel = pane.lookup(".header-panel");
            if (headerPanel != null) {
                headerPanel.setStyle(String.format(
                        "-fx-background-color: %s; -fx-background-insets: 0; -fx-padding: 8 12;",
                        backgroundColor));
                Node headerLabel = headerPanel.lookup(".label");
                if (headerLabel instanceof Label) {
                    ((Label) headerLabel).setTextFill(javafx.scene.paint.Color.web(textColor));
                }
            }
        });
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
            updateStatus("Schritt zum Makro hinzugefügt");
        } else {
            updateStatus("Bitte zuerst ein Makro auswählen");
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
            regexCheckBox.getStyleClass().add("macro-checkbox");
            
            CheckBox caseCheckBox = new CheckBox("Case-Sensitive");
            caseCheckBox.setSelected(step.isCaseSensitive());
            caseCheckBox.getStyleClass().add("macro-checkbox");
            
            CheckBox wordCheckBox = new CheckBox("Ganzes Wort");
            wordCheckBox.setSelected(step.isWholeWord());
            wordCheckBox.getStyleClass().add("macro-checkbox");
            
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
            
            // WICHTIG: Dialog korrekt stylen
            styleDialog(dialog);
            
            // Theme-spezifische Styles für Dialog-Inhalte
            String backgroundColor = THEMES[currentThemeIndex][0];
            String textColor = THEMES[currentThemeIndex][1];
            String inputBgColor = THEMES[currentThemeIndex][2];
            String borderColor = THEMES[currentThemeIndex][3];
            
            // Grid-Container stylen
            grid.setStyle(String.format(
                "-fx-background-color: %s; -fx-text-fill: %s;",
                backgroundColor, textColor
            ));
            
            // Labels stylen
            for (javafx.scene.Node node : grid.getChildren()) {
                if (node instanceof Label) {
                    node.setStyle(String.format(
                        "-fx-text-fill: %s; -fx-font-weight: bold;",
                        textColor
                    ));
                }
            }
            
            // TextFields stylen
            String textFieldStyle = String.format(
                "-fx-background-color: %s; -fx-text-fill: %s; -fx-border-color: %s; -fx-border-radius: 4px; -fx-background-radius: 4px; -fx-padding: 6px 8px;",
                inputBgColor, textColor, borderColor
            );
            searchField.setStyle(textFieldStyle);
            replaceField.setStyle(textFieldStyle);
            descriptionField.setStyle(textFieldStyle);
            
            // CheckBoxes werden durch CSS gestylt - keine programmatischen Styles
            // regexCheckBox.setStyle(checkboxStyle);
            // caseCheckBox.setStyle(checkboxStyle);
            // wordCheckBox.setStyle(checkboxStyle);
            
            // OptionsBox stylen
            optionsBox.setStyle(String.format(
                "-fx-background-color: transparent; -fx-text-fill: %s;",
                textColor
            ));
            
            // Zusätzliche Theme-Anwendung nach dem Aufbau
            dialog.setOnShown(event -> {
                // Buttons im Dialog stylen
                String buttonStyle = String.format(
                    "-fx-background-color: %s; -fx-text-fill: %s; -fx-border-color: %s; -fx-border-radius: 4px; -fx-background-radius: 4px; -fx-padding: 8px 16px;",
                    inputBgColor, textColor, borderColor
                );
                
                for (ButtonType buttonType : dialog.getDialogPane().getButtonTypes()) {
                    Button button = (Button) dialog.getDialogPane().lookupButton(buttonType);
                    if (button != null) {
                        button.setStyle(buttonStyle);
                        
                        // Hover-Effekt
                        button.setOnMouseEntered(e -> button.setStyle(buttonStyle + " -fx-opacity: 0.8;"));
                        button.setOnMouseExited(e -> button.setStyle(buttonStyle));
                    }
                }
                
                // TextField-Fokus-Effekte
                String focusedStyle = String.format(
                    "-fx-background-color: %s; -fx-text-fill: %s; -fx-border-color: %s; -fx-border-width: 2px; -fx-border-radius: 4px; -fx-background-radius: 4px; -fx-padding: 6px 8px;",
                    inputBgColor, textColor, textColor
                );
                
                searchField.focusedProperty().addListener((obs, oldVal, newVal) -> {
                    searchField.setStyle(newVal ? focusedStyle : textFieldStyle);
                });
                replaceField.focusedProperty().addListener((obs, oldVal, newVal) -> {
                    replaceField.setStyle(newVal ? focusedStyle : textFieldStyle);
                });
                descriptionField.focusedProperty().addListener((obs, oldVal, newVal) -> {
                    descriptionField.setStyle(newVal ? focusedStyle : textFieldStyle);
                });
            });
            
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
            // Fokus vor Makro-Ausführung sicherstellen
            codeArea.requestFocus();
            
            // Absatz-Markierung temporär deaktivieren für Makro-Ausführung
            boolean wasParagraphMarkingEnabled = paragraphMarkingEnabled;
            paragraphMarkingEnabled = false;
            
            // Alle Absatz-Markierungen entfernen
            removeAllParagraphMarkings();
            
            // Cursor-Position NACH removeAllParagraphMarkings() speichern
            int caretPosition = codeArea.getCaretPosition();
            
            // EINGEBAUTE UNDO-FUNKTIONALITÄT VERWENDEN - kein manueller Aufruf nötig
            
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
                
                // Debug entfernt
                
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
            
            // HTML-Tag-Normalisierung nach Makro-Ausführung (wie beim Anführungszeichen-Dropdown)
            // WICHTIG: Vor codeArea.replaceText() aufrufen, damit es auf dem content-String arbeitet
            String normalizedContent = normalizeHtmlTagsInContent(content);
            
            // Text ersetzen mit spezifischen Positionen (wie in anderen Teilen des Codes)
            codeArea.replaceText(0, codeArea.getLength(), normalizedContent);
            
            // Markiere als geändert, da das Makro Änderungen vorgenommen hat
            markAsChanged();
            
            // Cursor-Position wiederherstellen
            if (caretPosition <= normalizedContent.length()) {
                codeArea.moveTo(caretPosition);
            } else {
                codeArea.moveTo(normalizedContent.length());
            }
            
            // Fokus wiederherstellen und zum Cursor scrollen
            Platform.runLater(() -> {
                // Fokus verstärken
                codeArea.requestFocus();
                stage.requestFocus();
                codeArea.requestFocus();
                
                // Zum Cursor scrollen - sanft in die Mitte des sichtbaren Bereichs
                int currentParagraph = codeArea.getCurrentParagraph();
                codeArea.showParagraphInViewport(currentParagraph);
                
            });
            
            updateStatus("Makro erfolgreich ausgeführt: " + currentMacro.getName());
            
            // Timer für Status-Reset nach 5 Sekunden
            Timer statusResetTimer = new Timer();
            statusResetTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    Platform.runLater(() -> {
                        // Prüfe ob es ungespeicherte Änderungen gibt
                        if (hasUnsavedChanges) {
                            updateStatusDisplay(); // Zeigt "⚠ Ungespeicherte Änderungen"
                        } else {
                            updateStatus("Bereit");
                        }
                    });
                }
            }, 5000); // 5 Sekunden
            
            // Absatz-Markierung wieder aktivieren, wenn sie vorher aktiviert war
            if (wasParagraphMarkingEnabled) {
                paragraphMarkingEnabled = true;
                markEmptyLines();
            }
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
        // Neue Persistenz: Datei im config/makros Ordner, mit Migration aus Preferences

        String savedMacros = "";
        java.io.File macrosFile = getMacrosFile();

        try {
            if (macrosFile.exists()) {
                savedMacros = new String(java.nio.file.Files.readAllBytes(macrosFile.toPath()), "UTF-8");
            } else {
                String legacy = preferences.get("savedMacros", "");
                if (legacy != null && !legacy.isEmpty()) {
                    savedMacros = legacy;
                    java.nio.file.Files.createDirectories(macrosFile.getParentFile().toPath());
                    java.nio.file.Files.writeString(macrosFile.toPath(), savedMacros, java.nio.charset.StandardCharsets.UTF_8);
                }
            }
            } catch (Exception e) {
            logger.error("Fehler beim Laden/Migrieren der Makros: ", e);
            }

        if (savedMacros == null) savedMacros = "";
        // Alte/inkompatible Formate abfangen (aber ENABLED:0/1 ist VALID!)
        if (savedMacros.contains("|||") || savedMacros.contains("<<<MACRO>>>")) {
            savedMacros = "";
        }
        
        if (savedMacros.isEmpty()) {
            // Keine gespeicherten Makros - lade Standard-Makros
            loadDefaultMacros();
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
                // Neues lesbares Format
                if (currentStep != null) {
                    String replaceText = line.substring(8);
                    currentStep.setReplaceText(replaceText);
                }
            } else if (line.startsWith("REPLACE_B64:")) {
                // Altes Base64-Format - für Kompatibilität
                if (currentStep != null) {
                    try {
                        String encodedText = line.substring(12);
                        String replaceText = new String(java.util.Base64.getDecoder().decode(encodedText), "UTF-8");
                        
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
        
        
        // Stelle sicher, dass Text-Bereinigung Makro vorhanden ist
        boolean hasTextBereinigung = macros.stream().anyMatch(macro -> "Text-Bereinigung".equals(macro.getName()));
        if (!hasTextBereinigung) {
            loadDefaultMacros();
        }
        
        // Text-Bereinigung Makro automatisch laden und anzeigen
        if (hasTextBereinigung) {
            for (Macro macro : macros) {
                if ("Text-Bereinigung".equals(macro.getName())) {
                    currentMacro = macro;
                    // Dropdown setzen und dann Makro anzeigen
                    cmbMacroList.setValue("Text-Bereinigung");
                    selectMacro();
                    break;
                }
            }
        }
        
        updateMacroList();
    }
    
    private void saveMacros() {
        // Primär in Datei, zusätzlich Preferences als Backup
        try {
            StringBuilder sb = new StringBuilder();
            for (Macro macro : macros) {
                sb.append("MACRO:").append(macro.getName()).append("\n");
                sb.append("DESC:").append(macro.getDescription() != null ? macro.getDescription() : "").append("\n");
                for (MacroStep step : macro.getSteps()) {
                    String replaceText = step.getReplaceText() != null ? step.getReplaceText() : "";
                    sb.append("STEP:").append(step.getStepNumber()).append("\n");
                    sb.append("SEARCH:").append(step.getSearchText() != null ? step.getSearchText() : "").append("\n");
                    sb.append("REPLACE:").append(replaceText).append("\n");
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

            // Datei speichern
            java.io.File macrosFile = getMacrosFile();
            try {
                java.nio.file.Files.createDirectories(macrosFile.getParentFile().toPath());
                java.nio.file.Files.writeString(macrosFile.toPath(), macroData, java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception fileError) {
                logger.error("Fehler beim Speichern in Datei: " + fileError.getMessage());
                // Fallback: Nur Preferences
            }

            // Preferences als Backup speichern
            try {
            preferences.put("savedMacros", macroData);
            preferences.flush();
            } catch (Exception prefError) {
                logger.error("Fehler beim Speichern in Preferences: " + prefError.getMessage());
            }

            updateStatus("Makros gespeichert: " + macros.size() + " Makros");
        } catch (Exception e) {
            logger.error("Fehler beim Speichern der Makros", e);
            updateStatus("Fehler beim Speichern der Makros: " + e.getMessage());
        }
    }

    private java.io.File getMacrosFile() {
        java.io.File dir = new java.io.File(com.manuskript.ResourceManager.getConfigDirectory(), "makros");
        if (!dir.exists()) dir.mkdirs();
        return new java.io.File(dir, "macros.txt");
    }
    
    /**
     * Lädt Standard-Makros (Text-Bereinigung)
     */
    private void loadDefaultMacros() {
        
        // Text-Bereinigung-Makro aus Datei laden
        java.io.File defaultMacroFile = new java.io.File(com.manuskript.ResourceManager.getConfigDirectory(), "makros/macros.txt");
        if (defaultMacroFile.exists()) {
            try {
                String macroContent = new String(java.nio.file.Files.readAllBytes(defaultMacroFile.toPath()), "UTF-8");
                parseMacroContent(macroContent);
            } catch (Exception e) {
                logger.error("Fehler beim Laden der Standard-Makros", e);
            }
        } else {
            logger.warn("Standard-Makro-Datei nicht gefunden: " + defaultMacroFile.getAbsolutePath());
        }
    }
    
    /**
     * Parst Makro-Inhalt und fügt sie zur Liste hinzu
     */
    private void parseMacroContent(String macroContent) {
        String[] lines = macroContent.split("\n");
        Macro currentMacro = null;
        MacroStep currentStep = null;
        
        for (String line : lines) {
            line = line.trim();
            
            if (line.startsWith("MACRO:")) {
                currentMacro = new Macro(line.substring(6));
                macros.add(currentMacro);
            } else if (line.startsWith("DESC:")) {
                if (currentMacro != null) {
                    currentMacro.setDescription(line.substring(5));
                }
            } else if (line.startsWith("STEP:")) {
                if (currentMacro != null) {
                    currentStep = new MacroStep();
                    currentMacro.addStep(currentStep);
                }
            } else if (line.startsWith("SEARCH:")) {
                if (currentStep != null) {
                    currentStep.setSearchText(line.substring(7));
                }
            } else if (line.startsWith("REPLACE:")) {
                if (currentStep != null) {
                    currentStep.setReplaceText(line.substring(8));
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
    }
   
    
    private void updateMacroList() {
        List<String> macroNames = macros.stream()
            .map(Macro::getName)
            .collect(Collectors.toList());
        cmbMacroList.getItems().clear();
        cmbMacroList.getItems().addAll(macroNames);
        
        // Text-Bereinigung Makro automatisch auswählen, falls vorhanden
        if (!macroNames.isEmpty()) {
            if (macroNames.contains("Text-Bereinigung")) {
                cmbMacroList.setValue("Text-Bereinigung");
                // currentMacro wurde bereits in loadMacros() gesetzt
            } else {
                // Fallback: Erstes Makro auswählen
                cmbMacroList.setValue(macroNames.get(0));
                currentMacro = macros.get(0);
            }
        }
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
        
        // Fenster-Größe und Position mit robuster Validierung laden
        double width = PreferencesManager.getEditorWidth(preferences, "editor_window_width", PreferencesManager.DEFAULT_EDITOR_WIDTH);
        double height = PreferencesManager.getEditorHeight(preferences, "editor_window_height", PreferencesManager.DEFAULT_EDITOR_HEIGHT);
        double x = PreferencesManager.getWindowPosition(preferences, "editor_window_x", -1.0);
        double y = PreferencesManager.getWindowPosition(preferences, "editor_window_y", -1.0);
        
        
        // Mindestgrößen für Editor-Fenster
        double minWidth = PreferencesManager.MIN_EDITOR_WIDTH;
        double minHeight = PreferencesManager.MIN_EDITOR_HEIGHT;
        
        // WICHTIG: Mindestgröße für CustomStage setzen
        stage.setMinWidth(minWidth);
        stage.setMinHeight(minHeight);
        
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
            stage.centerOnScreen();
        }
        
        // Split Pane entfernt - keine Divider-Position-Logik mehr nötig
        
        // WICHTIG: Listener für Fenster-Änderungen hinzufügen
        addWindowPropertyListeners();
        
    }
    
    private void addWindowPropertyListeners() {
        if (stage == null) return;
        
        // Listener für Fenster-Änderungen hinzufügen
        stage.widthProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(oldVal)) {
                PreferencesManager.putEditorWidth(preferences, "editor_window_width", newVal.doubleValue());
                try {
                    preferences.flush(); // Sofort speichern
                } catch (Exception e) {
                    logger.warn("Konnte Fenster-Breite nicht speichern: {}", e.getMessage());
                }
            }
        });
        
        stage.heightProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(oldVal)) {
                PreferencesManager.putEditorHeight(preferences, "editor_window_height", newVal.doubleValue());
                try {
                    preferences.flush(); // Sofort speichern
                } catch (Exception e) {
                    logger.warn("Konnte Fenster-Höhe nicht speichern: {}", e.getMessage());
                }
            }
        });
        
        stage.xProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(oldVal)) {
                PreferencesManager.putWindowPosition(preferences, "editor_window_x", newVal.doubleValue());
                try {
                    preferences.flush(); // Sofort speichern
                } catch (Exception e) {
                    logger.warn("Konnte Fenster-X-Position nicht speichern: {}", e.getMessage());
                }
            }
        });
        
        stage.yProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(oldVal)) {
                PreferencesManager.putWindowPosition(preferences, "editor_window_y", newVal.doubleValue());
                try {
                    preferences.flush(); // Sofort speichern
                } catch (Exception e) {
                    logger.warn("Konnte Fenster-Y-Position nicht speichern: {}", e.getMessage());
                }
            }
        });
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
        
        
        if (mainWindowTheme >= 0) {
            currentThemeIndex = mainWindowTheme;
        } else if (editorTheme >= 0) {
            currentThemeIndex = editorTheme;
        } else {
            currentThemeIndex = 0; // Standard weißes Theme
        }
        
        // Theme anwenden
        applyTheme(currentThemeIndex);
        
        // WICHTIG: CustomStage Theme aktualisieren
        if (stage instanceof CustomStage) {
            CustomStage customStage = (CustomStage) stage;
            customStage.setFullTheme(currentThemeIndex);
        }
        
        // Theme-Button Tooltip aktualisieren
        updateThemeButtonTooltip();
        
        // Anführungszeichen-Style laden
        currentQuoteStyleIndex = preferences.getInt("quoteStyle", 0);
        if (currentQuoteStyleIndex >= 0 && currentQuoteStyleIndex < QUOTE_STYLES.length) {
            cmbQuoteStyle.setValue(QUOTE_STYLES[currentQuoteStyleIndex][0]);
        } else {
            cmbQuoteStyle.setValue(QUOTE_STYLES[0][0]);
            currentQuoteStyleIndex = 0;
        }
        
        // Anführungszeichen-Überprüfung beim Laden
        Platform.runLater(() -> {
            String currentText = codeArea.getText();
            if (currentText != null && !currentText.isEmpty()) {
                checkAndConvertQuotesOnLoad(currentText);
                
                // Automatisch Fehler-Dialog anzeigen, wenn Fehler gefunden wurden
                if (!quoteErrors.isEmpty()) {
                    showQuoteErrorsDialog();
                }
            }
        });
        
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
    
    // setupLineSpacingComboBox entfernt - wird von RichTextFX nicht unterstützt
    
    private void setupParagraphSpacingComboBox() {
        if (cmbParagraphSpacing != null) {
            // Absatzabstand-Optionen hinzufügen
            ObservableList<String> spacings = FXCollections.observableArrayList();
            spacings.addAll("0", "5", "10", "15", "20", "25", "30", "40", "50");
            cmbParagraphSpacing.setItems(spacings);
            
            // Aktuellen Wert aus Konfiguration laden
            try {
                Properties config = new Properties();
                try (InputStream input = new FileInputStream("config/parameters.properties")) {
                    config.load(new InputStreamReader(input, StandardCharsets.UTF_8));
                }
                String currentSpacing = config.getProperty("editor.paragraph-spacing", "10");
                cmbParagraphSpacing.setValue(currentSpacing);
            } catch (Exception e) {
                cmbParagraphSpacing.setValue("10"); // Fallback
            }
            
        }
    }
    
    private void setupQuoteStyleComboBox() {
        if (cmbQuoteStyle != null) {
            // Anführungszeichen-Styles hinzufügen
            for (String[] style : QUOTE_STYLES) {
                cmbQuoteStyle.getItems().add(style[0]);
            }
            
            // Gespeicherten Wert laden
            currentQuoteStyleIndex = preferences.getInt("quoteStyle", 0);
            if (currentQuoteStyleIndex >= 0 && currentQuoteStyleIndex < QUOTE_STYLES.length) {
                cmbQuoteStyle.setValue(QUOTE_STYLES[currentQuoteStyleIndex][0]);
            } else {
                cmbQuoteStyle.setValue(QUOTE_STYLES[0][0]);
                currentQuoteStyleIndex = 0;
            }
            
            // Event-Handler für Änderungen
            cmbQuoteStyle.setOnAction(e -> {
                String selected = cmbQuoteStyle.getValue();
                for (int i = 0; i < QUOTE_STYLES.length; i++) {
                    if (QUOTE_STYLES[i][0].equals(selected)) {
                        currentQuoteStyleIndex = i;
                        preferences.put("quoteStyle", String.valueOf(i));
                        try {
                            preferences.flush();
                        } catch (java.util.prefs.BackingStoreException ex) {
                            logger.warn("Konnte Quote-Style nicht speichern: " + ex.getMessage());
                        }
                        
                        
                        // NEUE FUNKTIONALITÄT: Konvertiere alle Anführungszeichen im Text
                        convertAllQuotationMarksInText(selected);
                        break;
                    }
                }
            });
        }
    }
    
    /**
     * Konvertiert alle Anführungszeichen im Text zu einem einheitlichen Stil
     */
    private void convertAllQuotationMarksInText(String selectedStyle) {
        if (codeArea == null) return;
        
        String currentText = codeArea.getText();
        if (currentText == null || currentText.isEmpty()) return;
        
        // Bestimme den Ziel-Stil
        String targetStyle = getTargetStyleFromSelected(selectedStyle);
        if (targetStyle == null) return;
        
        // Finde alle Anführungszeichen und markiere sie
        List<QuotationMarkConverter.QuotationMark> marks = QuotationMarkConverter.findQuotationMarks(currentText);
        List<QuotationMarkConverter.Inconsistency> inconsistencies = QuotationMarkConverter.findInconsistencies(currentText);
        
        // Markiere alle gefundenen Anführungszeichen
        markQuotationMarks(marks, inconsistencies);
        
        // Konvertiere alle Anführungszeichen
        String convertedText = QuotationMarkConverter.convertQuotationMarks(currentText, targetStyle);
        
        // Prüfe ob Änderungen vorgenommen wurden
        if (!currentText.equals(convertedText)) {
            // Speichere Cursor-Position
            int caretPosition = codeArea.getCaretPosition();
            
            // Ersetze den Text
            codeArea.replaceText(0, currentText.length(), convertedText);
            
            // Stelle Cursor-Position wieder her
            if (caretPosition <= convertedText.length()) {
                codeArea.moveTo(caretPosition);
            } else {
                codeArea.moveTo(convertedText.length());
            }
            
            // Zeige Erfolgsmeldung
            
            // NORMALISIERE HTML-TAGS: Rufe nach der Anführungszeichen-Konvertierung auf
            normalizeHtmlTagsInText();
        }
    }
    
    /**
     * Normalisiert Anführungszeichen innerhalb von HTML-Tags in einem String
     * Konvertiert typographische Anführungszeichen in HTML-Tags zurück zu normal "
     */
    private String normalizeHtmlTagsInContent(String content) {
        
        if (content == null || content.isEmpty()) {
            return content;
        }
        
        
        // Finde alle HTML-Tags im Text
        java.util.regex.Pattern htmlTagPattern = java.util.regex.Pattern.compile("<[^>]*>");
        java.util.regex.Matcher matcher = htmlTagPattern.matcher(content);
        
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String htmlTag = matcher.group();
            String normalizedTag = normalizeQuotesInHtmlAttributes(htmlTag);
            matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(normalizedTag));
        }
        matcher.appendTail(result);
        
        String normalizedContent = result.toString();
        
        if (!content.equals(normalizedContent)) {
        } else {
            return content;
        }
        
        return normalizedContent;
    }
    
    /**
     * Normalisiert Anführungszeichen innerhalb von HTML-Tags
     * Konvertiert typographische Anführungszeichen in HTML-Attributen zu normalen "
     */
    private void normalizeHtmlTagsInText() {
        
        if (codeArea == null) {
            return;
        }
        
        String currentText = codeArea.getText();
        if (currentText == null || currentText.isEmpty()) {
            return;
        }
        
        
        // Finde alle HTML-Tags und normalisiere Anführungszeichen darin
        String normalizedText = normalizeQuotesInHtmlTags(currentText);
        
        
        // Prüfe ob Änderungen vorgenommen wurden
        if (!currentText.equals(normalizedText)) {
            
            // Speichere Cursor-Position
            int caretPosition = codeArea.getCaretPosition();
            
            // Ersetze den Text
            codeArea.replaceText(0, currentText.length(), normalizedText);
            
            // Stelle Cursor-Position wieder her
            if (caretPosition <= normalizedText.length()) {
                codeArea.moveTo(caretPosition);
            } else {
                codeArea.moveTo(normalizedText.length());
            }
            
        }
        
    }
    
    /**
     * Normalisiert Anführungszeichen innerhalb von HTML-Tags
     * Konvertiert typographische Anführungszeichen zu normalen " in HTML-Attributen
     */
    private String normalizeQuotesInHtmlTags(String text) {
        // Finde alle HTML-Tags mit Pattern und Matcher
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("<[^>]*>");
        java.util.regex.Matcher matcher = pattern.matcher(text);
        
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String tag = matcher.group();
            
            // Prüfe ob der Tag typographische Anführungszeichen enthält
            if (containsTypographicQuotes(tag)) {
                // Normalisiere Anführungszeichen in HTML-Attributen
                String normalizedTag = normalizeQuotesInHtmlAttributes(tag);
               
                
                matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(normalizedTag));
            } else {
                // Tag ist korrekt, nicht ändern
                matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(tag));
            }
        }
        matcher.appendTail(result);
        
        return result.toString();
    }
    /**
     * Prüft ob ein HTML-Tag typographische Anführungszeichen enthält
     */
    private boolean containsTypographicQuotes(String tag) {
        // Prüfe auf alle typographischen Anführungszeichen
        return tag.contains("\u2557") || tag.contains("\u00BD") || tag.contains("\u201E") || 
               tag.contains("\u201C") || tag.contains("\u201D") || tag.contains("\u2018") || 
               tag.contains("\u2019") || tag.contains("\u201A") || tag.contains("\u2019") ||
               tag.contains("\u00AB") || tag.contains("\u00BB") || tag.contains("\u2039") || 
               tag.contains("\u203A") || tag.contains("\u201A") || tag.contains("\u201B");
    }
    /**
     * Normalisiert Anführungszeichen in HTML-Attributen
     * Konvertiert typographische Anführungszeichen zu normalen " in Attributwerten
     */
    private String normalizeQuotesInHtmlAttributes(String htmlTag) {
        
        // Einfacher Ansatz: Normalisiere alle typographischen Anführungszeichen im gesamten Tag
        String normalizedTag = normalizeQuotesInText(htmlTag);
        
       
        return normalizedTag;
    }
    
    /**
     * Normalisiert Anführungszeichen in einem HTML-Attributwert
     */
    private String normalizeQuotesInAttributeValue(String attribute) {
        // Finde den Attributwert zwischen den Anführungszeichen
        String valuePattern = "=\\s*[\"']([^\"']*)[\"']";
        
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(valuePattern);
        java.util.regex.Matcher matcher = pattern.matcher(attribute);
        
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String fullMatch = matcher.group();
            String value = matcher.group(1);
            
            // Normalisiere Anführungszeichen im Wert
            String normalizedValue = normalizeQuotesInText(value);
            
            // Erstelle neuen Attributstring mit normalisierten Anführungszeichen
            String newAttribute = fullMatch.replace(value, normalizedValue);
            
            
            matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(newAttribute));
        }
        matcher.appendTail(result);
        
        return result.toString();
    }
    
    /**
     * Normalisiert typographische Anführungszeichen zu normalen Anführungszeichen
     */
    private String normalizeQuotesInText(String text) {
        String result = text;
        
        // Konvertiere alle typographischen Anführungszeichen zu normalen "
        // Doppelte Anführungszeichen
        result = result.replace("\u201E", "\""); // „ (deutsche öffnende)
        result = result.replace("\u201C", "\""); // " (deutsche schließende)
        result = result.replace("\u201D", "\""); // " (englische schließende)
        result = result.replace("\u201F", "\""); // ‟ (deutsche schließende)
        result = result.replace("\u00AB", "\""); // « (französische öffnende)
        result = result.replace("\u00BB", "\""); // » (französische schließende)
        result = result.replace("\u2557", "\""); // ╗ (Box-Drawing)
        result = result.replace("\u00BD", "\""); // ½ (Vulgar Fraction)
        
        // Konvertiere einfache Anführungszeichen zu normalen '
        result = result.replace("\u201A", "'"); // ‚ (deutsche öffnende)
        result = result.replace("\u2018", "'"); // ' (englische öffnende)
        result = result.replace("\u2019", "'"); // ' (englische schließende)
        result = result.replace("\u2039", "'"); // ‹ (französische öffnende)
        result = result.replace("\u203A", "'"); // › (französische schließende)
        
        return result;
    }
    
    /**
     * Markiert alle gefundenen Anführungszeichen im Editor
     */
    private void markQuotationMarks(List<QuotationMarkConverter.QuotationMark> marks, 
                                    List<QuotationMarkConverter.Inconsistency> inconsistencies) {
        if (codeArea == null || marks == null) return;
        
        // Erstelle eine Map für Inkonsistenzen für schnellen Zugriff
        Set<Integer> inconsistencyPositions = new HashSet<>();
        for (QuotationMarkConverter.Inconsistency inconsistency : inconsistencies) {
            inconsistencyPositions.add(inconsistency.mark.position);
        }
        
        // Erstelle StyleSpans für alle Markierungen
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        int lastEnd = 0;
        
        for (QuotationMarkConverter.QuotationMark mark : marks) {
            int position = mark.position;
            int length = mark.mark.length();
            int end = position + length;
            
            // Füge unmarkierten Text hinzu
            if (position > lastEnd) {
                spansBuilder.add(Collections.emptyList(), position - lastEnd);
            }
            
            // Bestimme den Style direkt
            Collection<String> style;
            if (inconsistencyPositions.contains(position)) {
                // Rote Markierung für Inkonsistenzen
                style = Collections.singleton("-rtfx-background-color: #ffcccc; -rtfx-border-color: #ff0000; -rtfx-border-width: 2px;");
            } else {
                // Blaue Markierung für normale Anführungszeichen
                style = Collections.singleton("-rtfx-background-color: #cceeff; -rtfx-border-color: #0066cc; -rtfx-border-width: 1px;");
            }
            
            // Füge markierten Text hinzu
            spansBuilder.add(style, length);
            lastEnd = end;
        }
        
        // Füge restlichen Text hinzu
        String text = codeArea.getText();
        if (lastEnd < text.length()) {
            spansBuilder.add(Collections.emptyList(), text.length() - lastEnd);
        }
        
        // Wende die Styles an
        StyleSpans<Collection<String>> styleSpans = spansBuilder.create();
        codeArea.setStyleSpans(0, styleSpans);
        
        // Zeige Zusammenfassung
    }
    
    
    /**
     * Bestimmt den Ziel-Stil basierend auf der ausgewählten Option
     */
    private String getTargetStyleFromSelected(String selected) {
        if (selected == null) return null;
        
        // Mappe die ausgewählten Stile zu den QuotationMarkConverter-Stilen
        if (selected.contains("Deutsch")) {
            return "deutsch";
        } else if (selected.contains("Französisch")) {
            return "französisch";
        } else if (selected.contains("Englisch")) {
            return "englisch";
        } else if (selected.contains("Schweizer")) {
            return "schweizer";
        }
        
        return null;
    }
    
    private void formatTextBold() {
        formatTextAtCursor("**", "**", "<b>", "</b>");
    }
    
    private void formatTextItalic() {
        formatTextAtCursor("*", "*", "<i>", "</i>");
    }
    
    private void formatTextUnderline() {
        formatTextAtCursor("<u>", "</u>", "<u>", "</u>");
    }
    
    private void toggleTheme() {
        currentThemeIndex = (currentThemeIndex + 1) % THEMES.length;
        
        // WICHTIG: CustomStage Titelleiste aktualisieren
        if (stage instanceof CustomStage) {
            CustomStage customStage = (CustomStage) stage;
            customStage.setTitleBarTheme(currentThemeIndex);
        }
        
        // WICHTIG: Alle anderen Stages aktualisieren
        StageManager.applyThemeToAllStages(currentThemeIndex);
        
        // WICHTIG: Theme in Preferences speichern
        preferences.putInt("editor_theme", currentThemeIndex);
        preferences.putInt("main_window_theme", currentThemeIndex);
        
        // WICHTIG: Theme sofort anwenden
        applyTheme(currentThemeIndex);
        
        // Update Button-Tooltip
        updateThemeButtonTooltip();
        
        // Absatz-Toggle optisch aktualisieren
        if (btnToggleParagraphMarking != null) {
            if (paragraphMarkingEnabled) {
                btnToggleParagraphMarking.setStyle("-fx-min-width: 35px; -fx-max-width: 35px; -fx-font-size: 16px;" + getParagraphToggleActiveStyle());
            } else {
                btnToggleParagraphMarking.setStyle("-fx-min-width: 35px; -fx-max-width: 35px; -fx-font-size: 16px;");
            }
        }
        
        String[] themeNames = {"Weiß", "Schwarz", "Pastell", "Blau", "Grün", "Lila"};
        updateStatus("Theme gewechselt: " + themeNames[currentThemeIndex]);
        
        
        // Zusätzlicher verzögerter Theme-Refresh für bessere Kompatibilität
        // Dies stellt sicher, dass die RichTextFX CodeArea korrekt aktualisiert wird
        Platform.runLater(() -> {
            applyTheme(currentThemeIndex);
        });
    }
    
    public void applyTheme(int themeIndex) {
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
        // WICHTIG: Alle RichTextFX-spezifischen Eigenschaften explizit setzen
        String cssStyle = String.format(
            "-rtfx-background-color: %s;" +
            "-fx-highlight-fill: %s;" +
            "-fx-highlight-text-fill: %s;" +
            "-fx-caret-color: %s !important;" +
            "-fx-font-family: 'Consolas', 'Monaco', monospace;" +
            "-fx-font-size: %dpx;" +
            "-fx-background-color: %s;" +
            "-fx-text-fill: %s !important;" +
            "-fx-control-inner-background: %s;",
            backgroundColor, selectionColor, textColor, caretColor, fontSize, backgroundColor, textColor, backgroundColor
        );
        
        // WICHTIG: Erst alle bestehenden Styles entfernen, dann neue setzen
        codeArea.setStyle("");
        
        // WICHTIG: Mehrfache Anwendung der Styles für bessere Kompatibilität
        Platform.runLater(() -> {
            // Erst alle Styles entfernen
            codeArea.setStyle("");
            
            // Dann neue Styles setzen
        codeArea.setStyle(cssStyle);
            
            // Zusätzlich: Explizit die Textfarbe über die RichTextFX API setzen
            // Dies stellt sicher, dass die Textfärbung korrekt angewendet wird
            codeArea.setStyle(cssStyle);
            
            // Zusätzlicher verzögerter Refresh für bessere Kompatibilität
            Platform.runLater(() -> {
                // Nochmal explizit setzen
                codeArea.setStyle(cssStyle);
                codeArea.setStyle(cssStyle);
            });
        });
        
        // CSS-Klassen für Theme-spezifische Cursor-Farben
        codeArea.getStyleClass().removeAll("theme-dark", "theme-light", "weiss-theme", "pastell-theme", "blau-theme", "gruen-theme", "lila-theme");
        if (themeIndex == 0) { // Weiß-Theme
            codeArea.getStyleClass().add("weiss-theme");
        } else if (themeIndex == 1) { // Schwarz-Theme
            codeArea.getStyleClass().add("theme-dark");
        } else if (themeIndex == 2) { // Pastell-Theme
            codeArea.getStyleClass().add("pastell-theme");
        } else if (themeIndex == 3) { // Blau-Theme
            codeArea.getStyleClass().add("theme-dark");
            codeArea.getStyleClass().add("blau-theme");
        } else if (themeIndex == 4) { // Grün-Theme
            codeArea.getStyleClass().add("theme-dark");
            codeArea.getStyleClass().add("gruen-theme");
        } else if (themeIndex == 5) { // Lila-Theme
            codeArea.getStyleClass().add("theme-dark");
            codeArea.getStyleClass().add("lila-theme");
        }
        
        // WICHTIG: Zusätzliche CSS-Klassen-Anwendung für bessere Kompatibilität
        Platform.runLater(() -> {
            // CSS-Klassen erneut anwenden
            codeArea.getStyleClass().removeAll("theme-dark", "theme-light", "weiss-theme", "pastell-theme", "blau-theme", "gruen-theme", "lila-theme");
            if (themeIndex == 0) { // Weiß-Theme
                codeArea.getStyleClass().add("weiss-theme");
            } else if (themeIndex == 1) { // Schwarz-Theme
                codeArea.getStyleClass().add("theme-dark");
            } else if (themeIndex == 2) { // Pastell-Theme
                codeArea.getStyleClass().add("pastell-theme");
            } else if (themeIndex == 3) { // Blau-Theme
                codeArea.getStyleClass().add("theme-dark");
                codeArea.getStyleClass().add("blau-theme");
            } else if (themeIndex == 4) { // Grün-Theme
                codeArea.getStyleClass().add("theme-dark");
                codeArea.getStyleClass().add("gruen-theme");
            } else if (themeIndex == 5) { // Lila-Theme
                codeArea.getStyleClass().add("theme-dark");
                codeArea.getStyleClass().add("lila-theme");
            }
        });
        
        // Dark Theme für alle UI-Elemente anwenden
        Platform.runLater(() -> {
            // Root-Container (Hauptfenster) - WICHTIG: Das ist der Hauptcontainer!
            if (stage != null && stage.getScene() != null) {
                Node root = stage.getScene().getRoot();
                root.getStyleClass().removeAll("theme-dark", "theme-light", "weiss-theme", "pastell-theme", "blau-theme", "gruen-theme", "lila-theme");
                
                // Direkte inline Styles für Pastell-Theme
                if (themeIndex == 2) { // Pastell-Theme
                    root.getStyleClass().add("pastell-theme");
                    mainContainer.getStyleClass().add("pastell-theme");
                    root.setStyle(""); // CSS-Klassen verwenden
                    mainContainer.setStyle(""); // CSS-Klassen verwenden
                } else {
                    root.setStyle(""); // Style zurücksetzen
                    mainContainer.setStyle(""); // Style zurücksetzen
                    
                    if (themeIndex == 0) { // Weiß-Theme
                        root.getStyleClass().add("weiss-theme");
                    } else if (themeIndex == 1 || themeIndex >= 3) { // Dunkle Themes
                        root.getStyleClass().add("theme-dark");
                        if (themeIndex == 3) root.getStyleClass().add("blau-theme");
                        if (themeIndex == 4) root.getStyleClass().add("gruen-theme");
                        if (themeIndex == 5) root.getStyleClass().add("lila-theme");
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
            applyThemeToNode(btnExport, themeIndex);
            // btnOpen und btnNew sind unsichtbar - keine Theme-Anwendung nötig
            applyThemeToNode(btnToggleSearch, themeIndex);
            applyThemeToNode(btnToggleMacro, themeIndex);
            applyThemeToNode(btnTextAnalysis, themeIndex);
            applyThemeToNode(btnRegexHelp, themeIndex);
            applyThemeToNode(btnKIAssistant, themeIndex);
            
            // Help-Buttons
            if (btnHelpMain != null) {
                applyThemeToNode(btnHelpMain, themeIndex);
            }
            if (btnHelpMarkdown != null) {
                applyThemeToNode(btnHelpMarkdown, themeIndex);
            }
            applyThemeToNode(btnIncreaseFont, themeIndex);
            applyThemeToNode(btnDecreaseFont, themeIndex);
            applyThemeToNode(btnBold, themeIndex);
            applyThemeToNode(btnItalic, themeIndex);
            applyThemeToNode(btnThemeToggle, themeIndex);
            // btnMacroRegexHelp wurde entfernt
            
            // Alle ComboBoxes
            applyThemeToNode(cmbSearchHistory, themeIndex);
            applyThemeToNode(cmbReplaceHistory, themeIndex);
            applyThemeToNode(cmbFontSize, themeIndex);
        // cmbLineSpacing entfernt
        applyThemeToNode(cmbParagraphSpacing, themeIndex);

            // Entfernt: Code-Seitiges Erzwingen – zurück zu reiner CSS-Steuerung
            
            // Alle CheckBoxes
            applyThemeToNode(chkRegexSearch, themeIndex);
            applyThemeToNode(chkCaseSensitive, themeIndex);
            applyThemeToNode(chkWholeWord, themeIndex);
            
            // Alle Labels
            applyThemeToNode(lblStatus, themeIndex);
            applyThemeToNode(lblMatchCount, themeIndex);
            
            // Chapter-Editor Elemente entfernt - keine Theme-Anwendung mehr nötig
            
            // Chapter-Editor-Titel explizit thematisieren
            if (stage != null && stage.getScene() != null) {
                Node chapterTitle = stage.getScene().lookup(".chapter-editor-title");
                if (chapterTitle != null) {
                    applyThemeToNode(chapterTitle, themeIndex);
                }
            }
            
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
            
            // Stylesheet zur CodeArea hinzufügen (OHNE clear() - das löscht unsere Markdown-Styles!)
            // codeArea.getStylesheets().clear(); // ENTFERNT - interferiert mit Markdown-Styling
            
            // Nur den dynamischen Textfarben-CSS hinzufügen
            codeArea.getStylesheets().removeIf(css -> css.startsWith("data:text/css,"));
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
                String cssPathRefresh = ResourceManager.getCssResource("css/manuskript.css");
                if (cssPathRefresh != null) {
                    stage.getScene().getStylesheets().add(cssPathRefresh);
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
                String cssPathInner = ResourceManager.getCssResource("css/manuskript.css");
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
            } else {
                logger.warn("Caret-Element nicht gefunden!");
            }
            
            // Zusätzlich: CodeArea-Padding anpassen für bessere Cursor-Sichtbarkeit
            codeArea.setStyle(codeArea.getStyle() + "; -fx-padding: 5px;");
        });
        
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

    // Entfernt: Code-seitige Popup-/Pfeilfarb-Erzwingung (zurück zu CSS)
    
    private void formatTextAtCursor(String markdownStart, String markdownEnd, String htmlStart, String htmlEnd) {
        if (codeArea == null) return;
        
        String selectedText = codeArea.getSelectedText();
        int caretPosition = codeArea.getCaretPosition();
        
        if (selectedText != null && !selectedText.isEmpty()) {
            // Text ist ausgewählt - prüfe ob bereits formatiert
            int start = codeArea.getSelection().getStart();
            int end = codeArea.getSelection().getEnd();
            
            if (outputFormat == DocxProcessor.OutputFormat.MARKDOWN) {
                if (isTextFormatted(selectedText, markdownStart, markdownEnd)) {
                    // Formatierung entfernen
                    String unformattedText = removeFormatting(selectedText, markdownStart, markdownEnd);
                    codeArea.replaceText(start, end, unformattedText);
                    codeArea.selectRange(start, start + unformattedText.length());
                } else {
                    // Formatierung hinzufügen
                    String formattedText = markdownStart + selectedText + markdownEnd;
                    codeArea.replaceText(start, end, formattedText);
                    codeArea.selectRange(start, start + formattedText.length());
                }
            } else {
                if (isTextFormatted(selectedText, htmlStart, htmlEnd)) {
                    // Formatierung entfernen
                    String unformattedText = removeFormatting(selectedText, htmlStart, htmlEnd);
                    codeArea.replaceText(start, end, unformattedText);
                    codeArea.selectRange(start, start + unformattedText.length());
                } else {
                    // Formatierung hinzufügen
                    String formattedText = htmlStart + selectedText + htmlEnd;
                    codeArea.replaceText(start, end, formattedText);
                    codeArea.selectRange(start, start + formattedText.length());
                }
            }
        } else {
            // Kein Text ausgewählt - finde und markiere das aktuelle Wort
            int[] wordBounds = findCurrentWordBounds(caretPosition);
            if (wordBounds != null) {
                int wordStart = wordBounds[0];
                int wordEnd = wordBounds[1];
                String wordText = codeArea.getText(wordStart, wordEnd);
                
                // Prüfe ob das Wort bereits formatiert ist
                if (outputFormat == DocxProcessor.OutputFormat.MARKDOWN) {
                    if (isTextFormatted(wordText, markdownStart, markdownEnd)) {
                        // Formatierung entfernen
                        String unformattedText = removeFormatting(wordText, markdownStart, markdownEnd);
                        codeArea.replaceText(wordStart, wordEnd, unformattedText);
                        codeArea.selectRange(wordStart, wordStart + unformattedText.length());
                    } else {
                        // Formatierung hinzufügen
                        String formattedText = markdownStart + wordText + markdownEnd;
                        codeArea.replaceText(wordStart, wordEnd, formattedText);
                        codeArea.selectRange(wordStart, wordStart + formattedText.length());
                    }
                } else {
                    if (isTextFormatted(wordText, htmlStart, htmlEnd)) {
                        // Formatierung entfernen
                        String unformattedText = removeFormatting(wordText, htmlStart, htmlEnd);
                        codeArea.replaceText(wordStart, wordEnd, unformattedText);
                        codeArea.selectRange(wordStart, wordStart + unformattedText.length());
                    } else {
                        // Formatierung hinzufügen
                        String formattedText = htmlStart + wordText + htmlEnd;
                        codeArea.replaceText(wordStart, wordEnd, formattedText);
                        codeArea.selectRange(wordStart, wordStart + formattedText.length());
                    }
                }
            } else {
                // Kein Wort gefunden - füge Formatierung an der Cursor-Position ein
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
        }
        
        codeArea.requestFocus();
    }
    
    /**
     * Findet die Grenzen des aktuellen Wortes an der Cursorposition.
     * @param caretPosition Die aktuelle Cursorposition
     * @return Array mit [start, end] Positionen des Wortes, oder null wenn kein Wort gefunden
     */
    private int[] findCurrentWordBounds(int caretPosition) {
        if (codeArea == null) return null;
        
        String text = codeArea.getText();
        if (text == null || text.isEmpty()) return null;
        
        // Wenn Cursor am Ende des Textes steht
        if (caretPosition >= text.length()) {
            caretPosition = text.length() - 1;
        }
        
        // Wenn Cursor am Anfang steht
        if (caretPosition < 0) {
            caretPosition = 0;
        }
        
        // Finde Wortanfang (rückwärts suchen)
        int wordStart = caretPosition;
        while (wordStart > 0 && isWordCharacter(text.charAt(wordStart - 1))) {
            wordStart--;
        }
        
        // Finde Wortende (vorwärts suchen)
        int wordEnd = caretPosition;
        while (wordEnd < text.length() && isWordCharacter(text.charAt(wordEnd))) {
            wordEnd++;
        }
        
        // Prüfe ob ein Wort gefunden wurde (mindestens 1 Zeichen)
        if (wordStart < wordEnd) {
            return new int[]{wordStart, wordEnd};
        }
        
        return null;
    }
    
    /**
     * Prüft ob ein Zeichen Teil eines Wortes ist.
     * @param c Das zu prüfende Zeichen
     * @return true wenn das Zeichen Teil eines Wortes ist
     */
    private boolean isWordCharacter(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '-';
    }
    
    /**
     * Prüft ob ein Text bereits mit der angegebenen Formatierung versehen ist.
     * @param text Der zu prüfende Text
     * @param startTag Das Start-Tag der Formatierung
     * @param endTag Das End-Tag der Formatierung
     * @return true wenn der Text bereits formatiert ist
     */
    private boolean isTextFormatted(String text, String startTag, String endTag) {
        if (text == null || text.isEmpty() || startTag == null || endTag == null) {
            return false;
        }
        
        return text.startsWith(startTag) && text.endsWith(endTag);
    }
    
    /**
     * Entfernt die Formatierung von einem Text.
     * @param text Der formatierte Text
     * @param startTag Das Start-Tag der Formatierung
     * @param endTag Das End-Tag der Formatierung
     * @return Der Text ohne Formatierung
     */
    private String removeFormatting(String text, String startTag, String endTag) {
        if (text == null || text.isEmpty() || startTag == null || endTag == null) {
            return text;
        }
        
        if (isTextFormatted(text, startTag, endTag)) {
            return text.substring(startTag.length(), text.length() - endTag.length());
        }
        
        return text;
    }
    
    /**
     * Hilfsklasse für Markdown-Matches
     */
    private static class MarkdownMatch {
        final int start;
        final int end;
        final String styleClass;
        
        MarkdownMatch(int start, int end, String styleClass) {
            this.start = start;
            this.end = end;
            this.styleClass = styleClass;
        }
    }
    /**
     * Einfache Timer-basierte Markdown-Styling-Methode
     * Verwendet setStyleSpans() wie bei der Suche
     */
    private void applyCombinedStyling() {
        if (codeArea == null) {
            return;
        }
        
        try {
        String content = codeArea.getText();
            if (content.isEmpty()) {
            return;
        }
        
            // Sammle Markdown-Matches
            List<MarkdownMatch> markdownMatches = new ArrayList<>();
            
            // Heading-Pattern: # ## ### #### #####
            Pattern headingPattern = Pattern.compile("^(#{1,5})\\s+(.+)$", Pattern.MULTILINE);
            Matcher headingMatcher = headingPattern.matcher(content);
            
            while (headingMatcher.find()) {
                String hashes = headingMatcher.group(1);
                int headingLevel = hashes.length();
                if (headingLevel >= 1 && headingLevel <= 5) {
                    int start = headingMatcher.start(2); // Nach den # und Leerzeichen
                    int end = headingMatcher.end(2);     // Ende des Textes
                    if (end > start) {
                        markdownMatches.add(new MarkdownMatch(start, end, "heading-" + headingLevel));
                        
                        // Zentrierung wird über CSS-Klassen gehandhabt
                        // Die CSS-Klassen .heading-1 bis .heading-5 haben bereits -fx-alignment: center
                    }
                }
            }
            
            // Bold-Italic-Pattern: ***text***
            Pattern boldItalicPattern = Pattern.compile("\\*\\*\\*([\\s\\S]*?)\\*\\*\\*", Pattern.DOTALL);
            Matcher boldItalicMatcher = boldItalicPattern.matcher(content);
            
            while (boldItalicMatcher.find()) {
                int start = boldItalicMatcher.start() + 3; // Nach ***
                int end = boldItalicMatcher.end() - 3;     // Vor ***
                if (end > start) {
                    markdownMatches.add(new MarkdownMatch(start, end, "markdown-bold-italic"));
                }
            }
            
            // Bold-Pattern: **text** (aber nicht ***text***)
            Pattern boldPattern = Pattern.compile("(?<!\\*)\\*\\*(?!\\*)([\\s\\S]*?)(?<!\\*)\\*\\*(?!\\*)", Pattern.DOTALL);
            Matcher boldMatcher = boldPattern.matcher(content);
            
            while (boldMatcher.find()) {
                int start = boldMatcher.start() + 2; // Nach **
                int end = boldMatcher.end() - 2;     // Vor **
                if (end > start) {
                    // Überprüfe, ob dieser Bereich bereits von bold-italic abgedeckt ist
                    boolean alreadyCovered = markdownMatches.stream().anyMatch(m -> 
                        (start >= m.start && start < m.end) || (end > m.start && end <= m.end) ||
                        (start <= m.start && end >= m.end));
                    if (!alreadyCovered) {
                        markdownMatches.add(new MarkdownMatch(start, end, "markdown-bold"));
                    }
                }
            }
            
            // Italic-Pattern: *text* (aber nicht **text** oder ***text***)
            Pattern italicPattern = Pattern.compile("(?<!\\*)\\*(?!\\*)([\\s\\S]*?)(?<!\\*)\\*(?!\\*)", Pattern.DOTALL);
            Matcher italicMatcher = italicPattern.matcher(content);
            
            while (italicMatcher.find()) {
                int start = italicMatcher.start() + 1; // Nach *
                int end = italicMatcher.end() - 1;     // Vor *
                if (end > start) {
                    // Überprüfe, ob dieser Bereich bereits abgedeckt ist
                    boolean alreadyCovered = markdownMatches.stream().anyMatch(m -> 
                        (start >= m.start && start < m.end) || (end > m.start && end <= m.end) ||
                        (start <= m.start && end >= m.end));
                    if (!alreadyCovered) {
                        markdownMatches.add(new MarkdownMatch(start, end, "markdown-italic"));
                    }
                }
            }
            
            // Underline-Pattern: <u>text</u>
            Pattern underlinePattern = Pattern.compile("<u>([\\s\\S]*?)</u>", Pattern.DOTALL);
            Matcher underlineMatcher = underlinePattern.matcher(content);
            
            while (underlineMatcher.find()) {
                int start = underlineMatcher.start() + 3; // Nach <u>
                int end = underlineMatcher.end() - 4;     // Vor </u>
                if (end > start) {
                    // Überprüfe, ob dieser Bereich bereits abgedeckt ist
                    boolean alreadyCovered = markdownMatches.stream().anyMatch(m -> 
                        (start >= m.start && start < m.end) || (end > m.start && end <= m.end) ||
                        (start <= m.start && end >= m.end));
                    if (!alreadyCovered) {
                        markdownMatches.add(new MarkdownMatch(start, end, "markdown-underline"));
                    }
                }
            }
            
            // HTML Bold/Strong-Pattern: <b>text</b> oder <strong>text</strong>
            Pattern htmlBoldPattern = Pattern.compile("<(b|strong)>([\\s\\S]*?)</(b|strong)>", Pattern.DOTALL);
            Matcher htmlBoldMatcher = htmlBoldPattern.matcher(content);
            
            while (htmlBoldMatcher.find()) {
                int start = htmlBoldMatcher.start() + 3; // Nach <b> oder <strong>
                int end = htmlBoldMatcher.end() - 4;     // Vor </b> oder </strong>
                if (end > start) {
                    boolean alreadyCovered = markdownMatches.stream().anyMatch(m -> 
                        (start >= m.start && start < m.end) || (end > m.start && end <= m.end) ||
                        (start <= m.start && end >= m.end));
                    if (!alreadyCovered) {
                        markdownMatches.add(new MarkdownMatch(start, end, "markdown-bold"));
                    }
                }
            }
            
            // HTML Italic/Em-Pattern: <i>text</i> oder <em>text</em>
            Pattern htmlItalicPattern = Pattern.compile("<(i|em)>([\\s\\S]*?)</(i|em)>", Pattern.DOTALL);
            Matcher htmlItalicMatcher = htmlItalicPattern.matcher(content);
            
            while (htmlItalicMatcher.find()) {
                int start = htmlItalicMatcher.start() + 3; // Nach <i> oder <em>
                int end = htmlItalicMatcher.end() - 4;     // Vor </i> oder </em>
                if (end > start) {
                    boolean alreadyCovered = markdownMatches.stream().anyMatch(m -> 
                        (start >= m.start && start < m.end) || (end > m.start && end <= m.end) ||
                        (start <= m.start && end >= m.end));
                    if (!alreadyCovered) {
                        markdownMatches.add(new MarkdownMatch(start, end, "markdown-italic"));
                    }
                }
            }
            
            // HTML Strike-through-Pattern:  text  oder <del>text</del>
            Pattern htmlStrikePattern = Pattern.compile("<(s|del)>([\\s\\S]*?)</(s|del)>", Pattern.DOTALL);
            Matcher htmlStrikeMatcher = htmlStrikePattern.matcher(content);
            
            while (htmlStrikeMatcher.find()) {
                int start = htmlStrikeMatcher.start() + 3; // Nach   oder <del>
                int end = htmlStrikeMatcher.end() - 4;     // Vor   oder </del>
                if (end > start) {
                    boolean alreadyCovered = markdownMatches.stream().anyMatch(m -> 
                        (start >= m.start && start < m.end) || (end > m.start && end <= m.end) ||
                        (start <= m.start && end >= m.end));
                    if (!alreadyCovered) {
                        markdownMatches.add(new MarkdownMatch(start, end, "markdown-strikethrough"));
                    }
                }
            }
            
            // HTML Mark/Highlight-Pattern: <mark>text</mark>
            Pattern htmlMarkPattern = Pattern.compile("<mark>([\\s\\S]*?)</mark>", Pattern.DOTALL);
            Matcher htmlMarkMatcher = htmlMarkPattern.matcher(content);
            
            while (htmlMarkMatcher.find()) {
                int start = htmlMarkMatcher.start() + 6; // Nach <mark>
                int end = htmlMarkMatcher.end() - 7;     // Vor </mark>
                if (end > start) {
                    boolean alreadyCovered = markdownMatches.stream().anyMatch(m -> 
                        (start >= m.start && start < m.end) || (end > m.start && end <= m.end) ||
                        (start <= m.start && end >= m.end));
                    if (!alreadyCovered) {
                        markdownMatches.add(new MarkdownMatch(start, end, "markdown-highlight"));
                    }
                }
            }
            
            // HTML Small/Big-Pattern: <small>text</small> oder <big>text</big>
            Pattern htmlSizePattern = Pattern.compile("<(small|big)>([\\s\\S]*?)</(small|big)>", Pattern.DOTALL);
            Matcher htmlSizeMatcher = htmlSizePattern.matcher(content);
            
            while (htmlSizeMatcher.find()) {
                int start = htmlSizeMatcher.start() + 7; // Nach <small> oder <big>
                int end = htmlSizeMatcher.end() - 8;     // Vor </small> oder </big>
                if (end > start) {
                    String tag = htmlSizeMatcher.group(1);
                    boolean alreadyCovered = markdownMatches.stream().anyMatch(m -> 
                        (start >= m.start && start < m.end) || (end > m.start && end <= m.end) ||
                        (start <= m.start && end >= m.end));
                    if (!alreadyCovered) {
                        markdownMatches.add(new MarkdownMatch(start, end, "markdown-" + tag));
                    }
                }
            }
            
            // Sortiere Markdown-Matches nach Position
            markdownMatches.sort((a, b) -> Integer.compare(a.start, b.start));
            
            // Wenn keine Markdown-Matches vorhanden sind, nichts tun
            if (markdownMatches.isEmpty()) {
                return; // Keine Markdown-Matches = keine Änderungen
            }
            
            // Hole existierende StyleSpans (Textanalyse-Markierungen)
            StyleSpans<Collection<String>> existingSpans = codeArea.getStyleSpans(0, codeArea.getLength());
            
            // Prüfe ob Textanalyse-Markierungen vorhanden sind
            boolean hasTextAnalysis = false;
            for (StyleSpan<Collection<String>> span : existingSpans) {
                for (String style : span.getStyle()) {
                    if (style.startsWith("search-") || style.startsWith("highlight-") || 
                        style.equals("word-repetition") || style.startsWith("analysis-")) {
                        hasTextAnalysis = true;
                        break;
                    }
                }
                if (hasTextAnalysis) break;
            }
            
            // EINFACHE LÖSUNG: Sammle alle existierenden Styles und füge Markdown hinzu
            StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
            int currentPos = 0;
            
            // Sammle alle existierenden Styles (Suchen, Textanalyse, etc.)
            Map<Integer, Set<String>> existingStyles = new HashMap<>();
            for (StyleSpan<Collection<String>> span : existingSpans) {
                for (int i = 0; i < span.getLength(); i++) {
                    int pos = currentPos + i;
                    existingStyles.put(pos, new HashSet<>(span.getStyle()));
                }
                currentPos += span.getLength();
            }
            
            // Füge Markdown-Styles zu existierenden Styles hinzu
            for (MarkdownMatch match : markdownMatches) {
                for (int i = match.start; i < match.end; i++) {
                    existingStyles.computeIfAbsent(i, k -> new HashSet<>()).add(match.styleClass);
                }
            }
            
            // Baue neue StyleSpans mit allen Styles
            currentPos = 0;
            Set<String> currentStyles = new HashSet<>();
            
            for (int i = 0; i < content.length(); i++) {
                Set<String> stylesAtPos = existingStyles.getOrDefault(i, new HashSet<>());
                
                if (!stylesAtPos.equals(currentStyles)) {
                    // Style-Änderung - füge bisherige Styles hinzu
                    if (!currentStyles.isEmpty()) {
                        spansBuilder.add(currentStyles, i - currentPos);
                    } else {
                        spansBuilder.add(Collections.emptyList(), i - currentPos);
                    }
                    currentPos = i;
                    currentStyles = new HashSet<>(stylesAtPos);
                }
            }
            
            // Letzte Styles hinzufügen
            if (!currentStyles.isEmpty()) {
                spansBuilder.add(currentStyles, content.length() - currentPos);
            } else {
                spansBuilder.add(Collections.emptyList(), content.length() - currentPos);
            }
            
            // Anwenden
            StyleSpans<Collection<String>> spans = spansBuilder.create();
            codeArea.setStyleSpans(0, spans);
            
        } catch (Exception e) {
            logger.debug("Fehler beim kombinierten Styling: {}", e.getMessage());
        }
    }
    

    

    

    

    

    
    private void showRegexHelp() {
        CustomStage helpStage = StageManager.createModalStage("Hilfe", stage);
        helpStage.setTitle("Java Regex - Syntax-Hilfe");
        helpStage.setResizable(false);
        
        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        // Theme anwenden
        applyThemeToNode(root, currentThemeIndex);
        
        // Titel
        Label titleLabel = new Label("Java Regex - Syntax-Referenz");
        titleLabel.getStyleClass().add("regex-help-title");
        
        // ScrollPane für den Inhalt
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(600);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        
        VBox content = new VBox(15);
        content.setPadding(new Insets(10));
        content.getStyleClass().add("regex-help");
        
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
        warningBox.setPadding(new Insets(10));
        
        Label warningTitle = new Label("⚠️ Wichtiger Hinweis:");
        warningTitle.setStyle("-fx-font-weight: bold;");
        
        Label warningText = new Label("Im Ersetzungstext verwenden Sie $1, $2, ... (nicht \\1, \\2, ...)");
        warningText.setWrapText(true);
        
        warningBox.getChildren().addAll(warningTitle, warningText);
        content.getChildren().add(warningBox);
        
        scrollPane.setContent(content);
        
        // Schließen-Button
        Button closeButton = new Button("Schließen");
        applyThemeToNode(closeButton, currentThemeIndex);
        closeButton.setOnAction(e -> helpStage.close());
        
        HBox buttonBox = new HBox();
        buttonBox.setAlignment(javafx.geometry.Pos.CENTER);
        buttonBox.getChildren().add(closeButton);
        
        root.getChildren().addAll(titleLabel, scrollPane, buttonBox);
        
        Scene scene = new Scene(root, 700, 700);
        // CSS-Dateien anhängen, damit Styles sicher greifen
        String stylesCss = ResourceManager.getCssResource("css/styles.css");
        String editorCss = ResourceManager.getCssResource("css/editor.css");
        if (stylesCss != null && !scene.getStylesheets().contains(stylesCss)) scene.getStylesheets().add(stylesCss);
        if (editorCss != null && !scene.getStylesheets().contains(editorCss)) scene.getStylesheets().add(editorCss);
        helpStage.setSceneWithTitleBar(scene);
        // Root-Theme-Klasse ergänzen, damit CSS sicher greift
        applyThemeToNode(scene.getRoot(), currentThemeIndex);
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
        if (codeArea == null || text == null || text.trim().isEmpty()) {
            return;
        }
        // Wenn etwas markiert ist: Auswahl ersetzen, sonst am Cursor einfügen
        int selStart = codeArea.getSelection() != null ? codeArea.getSelection().getStart() : codeArea.getCaretPosition();
        int selEnd = codeArea.getSelection() != null ? codeArea.getSelection().getEnd() : codeArea.getCaretPosition();
        if (selEnd > selStart) {
            codeArea.replaceText(selStart, selEnd, text);
            codeArea.moveTo(selStart + text.length());
        } else {
            int caretPosition = codeArea.getCaretPosition();
            codeArea.insertText(caretPosition, text);
            codeArea.moveTo(caretPosition + text.length());
        }
        codeArea.requestFocus();
            updateStatus("Text vom KI-Assistenten eingefügt");
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
     * Ersetzt den aktuell ausgewählten Text mit dem gegebenen Text
     */
    public void replaceSelectedText(String newText) {
        if (codeArea != null && codeArea.getSelection() != null) {
            int start = codeArea.getSelection().getStart();
            int end = codeArea.getSelection().getEnd();
            codeArea.replaceText(start, end, newText);
            // Neue Auswahl setzen
            codeArea.selectRange(start, start + newText.length());
            codeArea.requestFocus();
        }
    }
    
    /**
     * Fügt Text an der aktuellen Cursor-Position ein
     */
    public void insertTextAtCursor(String text) {
        if (codeArea != null && text != null) {
            int caretPosition = codeArea.getCaretPosition();
            codeArea.insertText(caretPosition, text);
            // Cursor nach dem eingefügten Text positionieren
            codeArea.moveTo(caretPosition + text.length());
            codeArea.requestFocus();
        }
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
    
    
    // Chapter-Editor-Methoden entfernt - Split Pane nicht mehr vorhanden
    
    /**
     * Navigation zum vorherigen Kapitel
     */
    private void navigateToPreviousChapter() {
        if (mainController == null) {
            updateStatus("Navigation nicht verfügbar - MainController nicht gesetzt");
            return;
        }
        
        // Prüfe auf ungespeicherte Änderungen
        if (hasUnsavedChanges()) {
            if (!showSaveDialogForNavigation()) {
                return; // Benutzer hat abgebrochen
            }
        }
        
        // Hole die aktuelle Dateiliste vom MainController (aus der rechten Tabelle)
        List<File> selectedFiles = mainController.getSelectedDocxFiles();
        if (selectedFiles.isEmpty()) {
            updateStatus("Keine Dateien für Navigation verfügbar");
            return;
        }
        
        // Finde den aktuellen Index in der ausgewählten Dateiliste
        int currentIndex = findCurrentFileIndex(selectedFiles);
        
        // Berechne den vorherigen Index
        int previousIndex = (currentIndex > 0) ? currentIndex - 1 : selectedFiles.size() - 1;
        
        // Lade das vorherige Kapitel
        File previousFile = selectedFiles.get(previousIndex);
        loadChapterFile(previousFile);
        
        updateStatus("Vorheriges Kapitel geladen: " + previousFile.getName());
    }
    
    /**
     * Navigation zum nächsten Kapitel
     */
    private void navigateToNextChapter() {
        if (mainController == null) {
            updateStatus("Navigation nicht verfügbar - MainController nicht gesetzt");
            return;
        }
        
        // Prüfe auf ungespeicherte Änderungen
        if (hasUnsavedChanges()) {
            if (!showSaveDialogForNavigation()) {
                return; // Benutzer hat abgebrochen
            }
        }
        
        // Hole die aktuelle Dateiliste vom MainController (aus der rechten Tabelle)
        List<File> selectedFiles = mainController.getSelectedDocxFiles();
        if (selectedFiles.isEmpty()) {
            updateStatus("Keine Dateien für Navigation verfügbar");
            return;
        }
        
        // Finde den aktuellen Index in der ausgewählten Dateiliste
        int currentIndex = findCurrentFileIndex(selectedFiles);
        
        // Berechne den nächsten Index (ohne Wrap-Around)
        int nextIndex = currentIndex + 1;
        
        // Prüfe, ob wir nicht beim letzten Kapitel sind
        if (nextIndex >= selectedFiles.size()) {
            updateStatus("Kein nächstes Kapitel verfügbar");
            return;
        }
        
        // Lade das nächste Kapitel
        File nextFile = selectedFiles.get(nextIndex);
        loadChapterFile(nextFile);
        
        updateStatus("Nächstes Kapitel geladen: " + nextFile.getName());
    }
    
    /**
     * Findet den Index der aktuellen Datei in der ausgewählten Dateiliste
     */
    private int findCurrentFileIndex(List<File> selectedFiles) {
        if (currentFile == null || selectedFiles.isEmpty()) {
            return 0; // Fallback auf ersten Index
        }
        
        // Methode 1: Direkter Vergleich mit originalDocxFile
        if (originalDocxFile != null) {
            for (int i = 0; i < selectedFiles.size(); i++) {
                if (selectedFiles.get(i).equals(originalDocxFile)) {
                    return i;
                }
            }
        }
        
        // Methode 2: Vergleich der Basisnamen (ohne Endungen)
        String currentBaseName = getBaseFileName(currentFile.getName());
        for (int i = 0; i < selectedFiles.size(); i++) {
            String selectedBaseName = getBaseFileName(selectedFiles.get(i).getName());
            if (currentBaseName.equals(selectedBaseName)) {
                return i;
            }
        }
        
        // Methode 3: Fallback - verwende den ersten Index
        logger.warn("Aktuelle Datei nicht in ausgewählten Dateien gefunden, verwende Index 0");
        return 0;
    }
    
    /**
     * Entfernt Dateiendungen und gibt den Basisnamen zurück
     */
    private String getBaseFileName(String fileName) {
        String baseName = fileName;
        
        // Entferne bekannte Dateiendungen
        if (baseName.toLowerCase().endsWith(".docx")) {
            baseName = baseName.substring(0, baseName.length() - 5);
        } else if (baseName.toLowerCase().endsWith(".md")) {
            baseName = baseName.substring(0, baseName.length() - 3);
        } else if (baseName.toLowerCase().endsWith(".html")) {
            baseName = baseName.substring(0, baseName.length() - 5);
        } else if (baseName.toLowerCase().endsWith(".txt")) {
            baseName = baseName.substring(0, baseName.length() - 4);
        }
        
        return baseName;
    }
    /**
     * Lädt eine Kapitel-Datei in den Editor
     */
    private void loadChapterFile(File file) {
        try {
            // Bestimme die Kapitelnummer basierend auf der Position in der Dateiliste
            int chapterNumber = 1; // Standard für das erste Kapitel (Buch)
            
            if (mainController != null) {
                List<File> selectedFiles = mainController.getSelectedDocxFiles();
                for (int i = 0; i < selectedFiles.size(); i++) {
                    if (selectedFiles.get(i).equals(file)) {
                        chapterNumber = i + 1; // Kapitelnummer ist 1-basiert
                        break;
                    }
                }
            }
            
            // Bevorzugt Sidecar-Datei entsprechend aktuellem Format
            File sidecar = deriveSidecarFileFor(file, outputFormat);
            
            // Prüfe ob DOCX geändert wurde - aber zeige nur Info, kein Zwangsdiff
            if (DiffProcessor.hasDocxChanged(file, sidecar)) {
                
                // WICHTIG: Dialog für externe Änderungen anzeigen (auch beim Weiterblättern)
                // Wir müssen den Dialog synchron machen, damit wir das Ergebnis verarbeiten können
                try {
                    // Dialog über MainController anzeigen und Entscheidung verarbeiten
                    if (mainController != null) {
                        // File zu DocxFile konvertieren
                        DocxFile docxFile = new DocxFile(file);
                        MainController.DocxChangeDecision decision = mainController.showDocxChangedDialogInMain(docxFile);
                        
                        
                        switch (decision) {
                            case DIFF:
                                // Diff-Fenster über MainController öffnen
                                if (mainController != null) {
                                    File mdFile = deriveSidecarFileFor(file, outputFormat);
                                    mainController.showDetailedDiffDialog(docxFile, mdFile, null, outputFormat);
                                }
                                // Bei DIFF warten - kein weiterer Ladeprozess
                                return;
                            case DOCX:
                                // DOCX-Inhalt laden und anzeigen
                                String docxContent = docxProcessor.processDocxFileContent(file, chapterNumber, outputFormat);
                                setText(docxContent);
                                setOriginalDocxFile(file);
                                setCurrentFile(deriveSidecarFileForCurrentFormat());
                                setWindowTitle("📄 " + file.getName());
                                originalContent = cleanTextForComparison(docxContent);
                                updateNavigationButtons();
                                // Bei DOCX-Übernahme normalen Ladeprozess fortsetzen
                                break;
                            case IGNORE:
                                // Bestehenden Inhalt beibehalten - normalen Ladeprozess fortsetzen
                                break;
                            case CANCEL:
                                // Bei CANCEL warten
                                return;
                        }
                    }
                } catch (Exception e) {
                    logger.error("Fehler beim Anzeigen des DOCX-Änderungs-Dialogs", e);
                }
            }
            
            String content;
            if (sidecar != null && sidecar.exists()) {
                content = new String(java.nio.file.Files.readAllBytes(sidecar.toPath()), java.nio.charset.StandardCharsets.UTF_8);
            } else {
                // Fallback: aus DOCX extrahieren
                content = docxProcessor.processDocxFileContent(file, chapterNumber, outputFormat);
            }

            // Setze den Inhalt und Dateireferenzen
            setText(content);
            setOriginalDocxFile(file);
            // currentFile zeigt immer auf die Sidecar-Datei (auch wenn sie noch nicht existiert)
            setCurrentFile(sidecar != null ? sidecar : deriveSidecarFileForCurrentFormat());
            
            // Aktualisiere den Fenstertitel
            setWindowTitle("📄 " + file.getName());
            
            // Setze den ursprünglichen Inhalt für Change-Detection
            originalContent = cleanTextForComparison(content);
            
            // Aktualisiere die Navigation-Buttons
            updateNavigationButtons();
            
            
        } catch (Exception e) {
            updateStatusError("Fehler beim Laden des Kapitels: " + e.getMessage());
            logger.error("Fehler beim Laden des Kapitels: " + file.getName(), e);
        }
    }
    

    

    
    private void showErrorDialog(String title, String message) {
        CustomAlert alert = new CustomAlert(Alert.AlertType.ERROR, title);
        alert.setContentText(message);
        // alert.setHeaderText(null); // ENTFERNT: Setzt 'null' String
        alert.applyTheme(currentThemeIndex);
        alert.initOwner(stage);
        alert.showAndWait();
    }
    
    /**
     * Aktualisiert die Navigation-Buttons basierend auf der aktuellen Position
     */
    private void updateNavigationButtons() {
        if (mainController == null) {
            btnPreviousChapter.setDisable(true);
            btnNextChapter.setDisable(true);
            return;
        }
        
        List<File> selectedFiles = mainController.getSelectedDocxFiles();
        if (selectedFiles.isEmpty()) {
            btnPreviousChapter.setDisable(true);
            btnNextChapter.setDisable(true);
            return;
        }
        
        int currentIndex = findCurrentFileIndex(selectedFiles);
        
        // Deaktiviere "Vorheriges Kapitel" wenn beim ersten Kapitel
        btnPreviousChapter.setDisable(currentIndex <= 0);
        
        // Deaktiviere "Nächstes Kapitel" wenn beim letzten Kapitel
        btnNextChapter.setDisable(currentIndex >= selectedFiles.size() - 1);
        
    }
    
    /**
     * Ändert den Zeilenabstand basierend auf ComboBox-Auswahl
     */
    // changeLineSpacingFromComboBox entfernt - wird von RichTextFX nicht unterstützt
    
    /**
     * Ändert den Absatzabstand basierend auf ComboBox-Auswahl
     */
    private void changeParagraphSpacingFromComboBox() {
        if (cmbParagraphSpacing.getValue() != null && !cmbParagraphSpacing.getValue().trim().isEmpty()) {
            try {
                int newSpacing = Integer.parseInt(cmbParagraphSpacing.getValue());
                
                // Speichere neue Konfiguration
                Properties config = new Properties();
                try (InputStream input = new FileInputStream("config/parameters.properties")) {
                    config.load(new InputStreamReader(input, StandardCharsets.UTF_8));
                }
                config.setProperty("editor.paragraph-spacing", String.valueOf(newSpacing));
                try (java.io.FileOutputStream output = new java.io.FileOutputStream("config/parameters.properties")) {
                    config.store(output, "Editor-Konfiguration");
                }
                
                // Wende neue Konfiguration an
                applyEditorSpacing();
                updateStatus("Absatzabstand auf " + newSpacing + "px gesetzt");
                
            } catch (NumberFormatException e) {
                logger.error("Ungültiger Absatzabstand: " + cmbParagraphSpacing.getValue());
                cmbParagraphSpacing.setValue("10"); // Fallback
            } catch (Exception e) {
                logger.error("Fehler beim Ändern des Absatzabstands", e);
            }
        }
    }
    
    /**
     * Wendet die konfigurierten Editor-Abstände an
     */
    private void applyEditorSpacing() {
        if (codeArea == null) {
            logger.warn("applyEditorSpacing: codeArea ist null!");
            return;
        }

        try {
            
            // Lade Konfiguration aus parameters.properties
            Properties config = new Properties();
            try (InputStream input = new FileInputStream("config/parameters.properties")) {
                config.load(new InputStreamReader(input, StandardCharsets.UTF_8));
            }

            // Hole konfigurierte Werte (mit Fallbacks)
            double lineSpacing = Double.parseDouble(config.getProperty("editor.line-spacing", "1.5"));
            int paragraphSpacing = Integer.parseInt(config.getProperty("editor.paragraph-spacing", "10"));
            

            // Wende Abstände an
            Platform.runLater(() -> {
                try {
                    // Sicherheitscheck: codeArea und Scene müssen verfügbar sein
                    if (codeArea == null || codeArea.getScene() == null) {
                        logger.warn("applyEditorSpacing: codeArea oder Scene ist null - überspringe Anwendung");
                        return;
                    }
                    // Debug-Text entfernt - nur noch CSS-Styling
                 
                    // Inline-Style für RichTextFX setzen
                    String currentStyle = codeArea.getStyle();
                    
                    // Entferne alte Spacing-Eigenschaften aus dem Style
                    String cleanStyle = currentStyle
                        .replaceAll("-fx-line-spacing:\\s*[^;]*;", "")
                        .replaceAll("line-height:\\s*[^;]*;", "")
                        .replaceAll("padding-bottom:\\s*[^;]*;", "")
                        .trim();
                    
                    // RICHTIGE LÖSUNG: CSS-Stylesheet verwenden statt Inline-Style
                    
                    // WORKING SOLUTION: Nur Absatzabstand funktioniert mit RichTextFX
                    // Zeilenabstand ist ein bekanntes Problem mit RichTextFX CodeArea
                    String cssContent = String.format(
                        ".code-area .paragraph-box { -fx-padding: 0 0 %dpx 0; }",
                        paragraphSpacing);
                    
                    
                    // Füge CSS als Stylesheet hinzu
                    try {
                        // Erstelle temporäre CSS-Datei
                        java.io.File tempCssFile = java.io.File.createTempFile("line-spacing", ".css");
                        tempCssFile.deleteOnExit();
                        
                        try (java.io.FileWriter writer = new java.io.FileWriter(tempCssFile)) {
                            writer.write(cssContent);
                        }
                        
                        // Füge Stylesheet zur CodeArea hinzu
                        codeArea.getStylesheets().add(tempCssFile.toURI().toString());
                        
                        
                    } catch (Exception e) {
                        logger.error("Fehler beim Erstellen des CSS-Stylesheets", e);
                    } catch (Throwable t) {
                        logger.error("Unerwarteter Fehler beim CSS-Stylesheet", t);
                    }
                    
                    // Force Layout-Update für sofortige Anzeige
                    codeArea.requestLayout();
                    
                } catch (Exception e) {
                    logger.error("Fehler beim Anwenden der Abstände in Platform.runLater", e);
                }
            });

        } catch (Exception e) {
            logger.error("Fehler beim Anwenden der Editor-Abstände", e);
        }
    }
    
    /**
     * Markiert Leerzeilen mit unsichtbaren Zeichen für bessere Sichtbarkeit
     */
    private void setupEmptyLineMarking() {
        // Event-Handler für Textänderungen
        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            Platform.runLater(() -> {
                try {
                    if (paragraphMarkingEnabled) {
                        markEmptyLines();
                    }
                } catch (Exception e) {
                    logger.debug("Fehler beim Markieren von Leerzeilen: {}", e.getMessage());
                }
            });
        });
        
        // Event-Handler für Cursor-Position - entfernt ¶ wenn Benutzer zu tippen beginnt
        codeArea.caretPositionProperty().addListener((obs, oldPos, newPos) -> {
            if (paragraphMarkingEnabled && newPos != null) {
                Platform.runLater(() -> {
                    try {
                        String text = codeArea.getText();
                        if (text != null && newPos.intValue() < text.length()) {
                            // Prüfe ob Cursor in einer Zeile mit ¶ ist
                            int lineStart = text.lastIndexOf('\n', newPos.intValue() - 1) + 1;
                            int lineEnd = text.indexOf('\n', newPos.intValue());
                            if (lineEnd == -1) lineEnd = text.length();
                            
                            String currentLine = text.substring(lineStart, lineEnd);
                            if (currentLine.contains("¶") && !currentLine.trim().equals("¶")) {
                                // Zeile enthält ¶ und anderen Text - entferne ¶
                                String cleanedLine = currentLine.replace("¶", "");
                                
                                // Speichere Cursor-Position relativ zum Zeilenanfang VOR dem Ersetzen
                                int relativeCursorPos = newPos.intValue() - lineStart;
                                
                                // Ersetze nur die betroffene Zeile (erhält Undo-Historie)
                                codeArea.replaceText(lineStart, lineEnd, cleanedLine);
                                
                                // Cursor-Position korrigieren
                                if (relativeCursorPos == 0) {
                                    // Wir waren ganz vorne - Cursor nach dem getippten Zeichen setzen
                                    Platform.runLater(() -> {
                                        codeArea.moveTo(lineStart + 1);
                                    });
                                }
                                // Wenn relativeCursorPos > 0: Mache NICHTS mit der Cursor-Position
                            }
                        }
                    } catch (Exception e) {
                        logger.debug("Fehler beim Entfernen von ¶: {}", e.getMessage());
                    }
                });
            }
        });
        
        // Initiale Markierung
        Platform.runLater(() -> {
            if (paragraphMarkingEnabled) {
                markEmptyLines();
            }
        });
    }
    
    /**
     * Markiert alle Leerzeilen mit einem unsichtbaren Absatz-Symbol
     */
    private void markEmptyLines() {
        String text = codeArea.getText();
        if (text == null || text.isEmpty()) return;
        
        String[] lines = text.split("\n", -1);
        StringBuilder newText = new StringBuilder();
        boolean textChanged = false;
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            
            // Leerzeile erkannt (nur Whitespace oder komplett leer)
            if (line.trim().isEmpty() && !line.contains("¶")) {
                // Füge unsichtbares Absatz-Symbol hinzu
                newText.append("¶");
                textChanged = true;
            } else {
                newText.append(line);
            }
            
            // Zeilenumbruch hinzufügen (außer bei der letzten Zeile)
            if (i < lines.length - 1) {
                newText.append("\n");
            }
        }
        
        // Text nur aktualisieren, wenn sich etwas geändert hat
        if (textChanged && !newText.toString().equals(text)) {
            // Cursor-Position merken
            int caretPosition = codeArea.getCaretPosition();
            
            // Text aktualisieren
            codeArea.replaceText(0, text.length(), newText.toString());
            
            // Cursor-Position wiederherstellen
            if (caretPosition <= newText.length()) {
                codeArea.moveTo(caretPosition);
            }
        }
    }
    
    /**
     * Entfernt alle Absatz-Markierungen vor dem Speichern/Export
     */
    public String cleanTextForExport(String text) {
        if (text == null) return "";
        return text.replace("¶", "");
    }
    
    /**
     * Bereinigt Text für Vergleich (entfernt Absatz-Markierungen)
     * Wird verwendet, um zu bestimmen, ob sich der Text wirklich geändert hat
     */
    private String cleanTextForComparison(String text) {
        if (text == null) return "";
        return text.replace("¶", "");
    }
    
    /**
     * Toggle für Absatz-Markierung
     */
    private void toggleParagraphMarking() {
        paragraphMarkingEnabled = !paragraphMarkingEnabled;
        
        // Button-Text und Styling aktualisieren
        if (paragraphMarkingEnabled) {
            btnToggleParagraphMarking.setText("¶");
            btnToggleParagraphMarking.setStyle("-fx-min-width: 35px; -fx-max-width: 35px; -fx-font-size: 16px;" + getParagraphToggleActiveStyle());
            btnToggleParagraphMarking.setTooltip(new Tooltip("Absatz-Markierung aktiviert"));
            
            // Alle Leerzeilen markieren
            markEmptyLines();
        } else {
            btnToggleParagraphMarking.setText("¶");
            btnToggleParagraphMarking.setStyle("-fx-min-width: 35px; -fx-max-width: 35px; -fx-font-size: 16px;");
            btnToggleParagraphMarking.setTooltip(new Tooltip("Absatz-Markierung deaktiviert"));
            
            // Alle Absatz-Markierungen entfernen
            removeAllParagraphMarkings();
        }
        
        // Einstellung speichern
        saveParagraphMarkingSetting();
        
        updateStatus(paragraphMarkingEnabled ? "Absatz-Markierung aktiviert" : "Absatz-Markierung deaktiviert");
    }

    /**
     * Liefert theme-abhängigen Stil für den aktiven Absatz-Toggle
     */
    private String getParagraphToggleActiveStyle() {
        // deutlichere Hinterlegung passend zum Theme
        switch (currentThemeIndex) {
            case 1: // Schwarz / Dark
                return " -fx-background-color: rgba(255,255,255,0.3);";
            case 2: // Pastell
                return " -fx-background-color: rgba(100,100,255,0.4);";
            case 3: // Blau
                return " -fx-background-color: rgba(58,123,213,0.4);";
            case 4: // Grün
                return " -fx-background-color: rgba(46,204,113,0.4);";
            case 5: // Lila
                return " -fx-background-color: rgba(155,89,182,0.4);";
            default: // Weiß / Light
                return " -fx-background-color: rgba(0,0,0,0.15);";
        }
    }
    
    /**
     * Entfernt alle Absatz-Markierungen aus dem Text
     */
    private void removeAllParagraphMarkings() {
        String text = codeArea.getText();
        if (text == null || text.isEmpty()) return;
        
        String cleanedText = text.replace("¶", "");
        if (!cleanedText.equals(text)) {
            int caretPosition = codeArea.getCaretPosition();
            codeArea.replaceText(0, text.length(), cleanedText);
            if (caretPosition <= cleanedText.length()) {
                codeArea.moveTo(caretPosition);
            }
        }
    }
    
    /**
     * Speichert die Absatz-Markierung-Einstellung
     */
    private void saveParagraphMarkingSetting() {
        try {
            Preferences preferences = Preferences.userNodeForPackage(MainController.class);
            preferences.putBoolean("paragraph_marking_enabled", paragraphMarkingEnabled);
        } catch (Exception e) {
            logger.warn("Konnte Absatz-Markierung-Einstellung nicht speichern: {}", e.getMessage());
        }
    }
    
    /**
     * Lädt die Absatz-Markierung-Einstellung
     */
    private void loadParagraphMarkingSetting() {
        try {
            Preferences preferences = Preferences.userNodeForPackage(MainController.class);
            paragraphMarkingEnabled = preferences.getBoolean("paragraph_marking_enabled", true);
        } catch (Exception e) {
            logger.warn("Konnte Absatz-Markierung-Einstellung nicht laden: {}", e.getMessage());
            paragraphMarkingEnabled = true; // Standard: aktiviert
        }
    }
    
    /**
     * Initialisiert den Absatz-Markierung-Button
     */
    private void initializeParagraphMarkingButton() {
        if (btnToggleParagraphMarking != null) {
            btnToggleParagraphMarking.setText("¶");
            btnToggleParagraphMarking.setStyle("-fx-min-width: 35px; -fx-max-width: 35px; -fx-font-size: 16px;" + (paragraphMarkingEnabled ? getParagraphToggleActiveStyle() : ""));
            if (paragraphMarkingEnabled) {
                btnToggleParagraphMarking.setTooltip(new Tooltip("Absatz-Markierung aktiviert"));
            } else {
                btnToggleParagraphMarking.setTooltip(new Tooltip("Absatz-Markierung deaktiviert"));
            }
        }
    }

}