
package com.manuskript;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.control.*;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Separator;
import javafx.scene.control.Alert;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.web.WebView;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.FileChooser;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.Modality;
import javafx.geometry.Rectangle2D;
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

import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

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
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import org.fxmisc.richtext.model.StyleSpan;
import org.fxmisc.richtext.model.TwoDimensional.Bias;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.animation.Animation;
import javafx.util.Duration;
import java.io.FileInputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ListView;

@SuppressWarnings("unchecked")
public class EditorWindow implements Initializable {
    
    private static final Logger logger = LoggerFactory.getLogger(EditorWindow.class);
    
    // Statische Map für Cursor-Positionen pro Kapitel (nur Session, nicht persistent)
    private static final Map<String, Integer> chapterCursorPositions = new HashMap<>();
    
    // Statische Map für Scroll-Positionen (vvalue 0.0-1.0) pro Kapitel
    private static final Map<String, Double> chapterScrollPositions = new HashMap<>();
    
    // Flag um zu verhindern, dass während des Ladens gespeichert wird
    private boolean isLoadingChapter = false;
    // Speichert den aktuell geladenen Text, um Race Conditions zu verhindern
    private String currentLoadingText = null;
    // Sequenznummer für Ladevorgänge, um alte Operationen zu ignorieren
    private volatile long loadingSequence = 0;
    
    // Set für Kapitel, die in dieser Sitzung bereits geladen wurden
    private static final Set<String> chaptersLoadedThisSession = new HashSet<>();
    
    // Referenz zum VirtualizedScrollPane für Scroll-Position
    private VirtualizedScrollPane<CodeArea> scrollPane;
    
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
    @FXML private Button btnExport;
    @FXML private Button btnCopySudowrite;

    @FXML private Button btnOpen;
    @FXML private Button btnNew;
    @FXML private Button btnToggleSearch;
    @FXML private Button btnToggleMacro;
    @FXML private Button btnTextAnalysis;
    @FXML private Button btnRegexHelp;
    @FXML private Button btnKIAssistant;
    @FXML private Button btnPreview;
    
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
    @FXML private Button btnStrikethrough;
    @FXML private Button btnSuperscript;
    @FXML private Button btnSubscript;
    @FXML private Button btnInsertImage;
    @FXML private Button btnInsertLineBreak;
    @FXML private Button btnInsertHorizontalLine;
    @FXML private Button btnCenter;
    @FXML private Button btnBig;
    @FXML private Button btnSmall;
    @FXML private Button btnMark;
    @FXML private MenuButton btnColorMenu;
    @FXML private Button btnThemeToggle;
    @FXML private ComboBox<String> cmbQuoteStyle;
    // Zeilenabstand-ComboBox entfernt - wird von RichTextFX nicht unterstützt
    @FXML private ComboBox<String> cmbParagraphSpacing;

    @FXML private Button btnPreviousChapter;
    @FXML private Button btnNextChapter;
    
    // Seitenleiste für Kapitel
    @FXML private SplitPane mainSplitPane;
    @FXML private VBox sidebarContainer;
    @FXML private Button btnToggleSidebar;
    @FXML private ListView<DocxFile> chapterListView;
    private boolean sidebarExpanded = true;
    
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
    private CustomStage previewStage;
    private OllamaWindow ollamaWindow;
    private WebView previewWebView; // Wird jetzt für Quill Editor verwendet
    private Button btnToggleJustify;
    private boolean previewJustifyEnabled = false;
    private boolean useQuillMode = true; // true = Quill Editor, false = normales WebView
    
    // Quill Font-Steuerung im Preview-Fenster
    private Label lblQuillFontSize;
    private Label lblQuillFontFamily;
    private Spinner<Integer> spnQuillFontSize;
    private ComboBox<String> cmbQuillFontFamily;
    private Preferences preferences;
    private ScheduledExecutorService previewUpdateExecutor;
    private ScheduledFuture<?> previewUpdateFuture;
    // Flag um zu verhindern, dass Preview-Window-Listener mehrfach hinzugefügt werden
    private boolean previewWindowListenersAdded = false;
    // Für Quill->Markdown Konvertierung (nicht im FX-Thread, um Hänger zu vermeiden)
    private ExecutorService quillConvertExecutor;
    private Future<?> quillConvertFuture;
    private String lastPreviewContent = null; // Cache für den letzten HTML-Content
    private javafx.beans.value.ChangeListener<javafx.concurrent.Worker.State> previewLoadListener = null; // Listener für LoadWorker-State
    private boolean isScrollingPreview = false;
    
    // Quill Editor Synchronisation
    private boolean isUpdatingFromCodeArea = false;
    private boolean isUpdatingFromQuill = false;
    private boolean isScrollingQuill = false;
    private boolean isTogglingViewMode = false; // Flag um Endlosschleifen beim Umschalten zu verhindern
    private QuillBridge quillBridge; // Java Bridge für JavaScript-Kommunikation
    private String lastQuillContent = null; // Cache für Quill Content
    private Timeline editorToQuillUpdateTimeline = null; // Debouncing-Timeline für Editor->Quill Updates
    private Timeline quillPollingTimeline = null; // Referenz zur Quill-Polling-Timeline, um mehrere Instanzen zu vermeiden
    // Letzte Quill-Änderung (vom Nutzer) – verhindert unmittelbares Überschreiben durch Editor->Quill Sync
    private volatile long lastQuillUserChangeTs = 0L;
    // Timer für Debouncing der Quill->Editor-Synchronisation (wartet bis Benutzer fertig getippt hat)
    private Timer quillToEditorSyncTimer = null;
    private String pendingQuillContent = null; // Zwischengespeicherter Content während Debounce
    
    // LanguageTool Integration
    private LanguageToolService languageToolService;
    private LanguageToolDictionary languageToolDictionary; // Wörterbuch für Eigennamen
    private boolean languageToolEnabled = false; // Wird aus Preferences geladen
    private Timeline languageToolCheckTimeline = null; // Debouncing für Fehlerprüfung
    private List<LanguageToolService.Match> currentLanguageToolMatches = new ArrayList<>();
    private ExecutorService languageToolExecutor;
    private Button btnToggleLanguageTool; // Toggle-Button für LanguageTool
    private Button btnLanguageToolSettings; // Einstellungs-Button
    private Label lblLanguageToolStatus; // Status-Indikator für Fehleranzahl
    private boolean isApplyingLanguageToolCorrection = false; // Flag um doppelte Prüfungen zu vermeiden
    
    // Timer für Markdown-Styling - kann während des Tippens pausiert werden
    private Timeline stylingTimer = null;
    private Timeline stylingTimerDebounce = null; // Debounce-Timer zum Fortsetzen des Styling-Timers
    private String lastStyledText = ""; // Letzter Text, für den Styling angewendet wurde
    private boolean isScrolling = false; // Flag, um zu prüfen, ob der Benutzer gerade scrollt
    
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
    private int originalQuoteStyleIndex = 0; // Speichert die ursprüngliche Einstellung
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
        {"\u201E", "\u201C"},        // Deutsch: U+201E („), U+201C (")
        {"\u00BB", "\u00AB"},       // Französisch: U+00BB (»), U+00AB («)
        {"\"", "\""},     // Englisch: U+0022 ("), U+0022 (")
        {"\u00AB", "\u00BB"}        // Schweizer: U+00AB («), U+00BB (»)
    };
    
    // Einfache Anführungszeichen-Mapping (öffnend, schließend)
    private static final String[][] SINGLE_QUOTE_MAPPING = {
        {"\u201A", Character.toString('\u2019')},       // Deutsch: U+201A (‚), U+2019 (') - direkt als char konvertiert
        {"\u203A", "\u2039"},       // Französisch: U+203A (›), U+2039 (‹)
        {"'", "'"},       // Englisch: U+0027 ('), U+0027 (')
        {"\u2039", "\u203A"}        // Schweizer: U+2039 (‹), U+203A (›)
    };
    
    // Makro-Management
    private ObservableList<Macro> macros = FXCollections.observableArrayList();
    private Macro currentMacro = null;
    
    private ScheduledExecutorService statusClearExecutor;
    private ScheduledFuture<?> statusClearFuture;
    private final Object statusLock = new Object();

    private void scheduleStatusClear(long delaySeconds, boolean skip) {
        if (skip) {
            synchronized (statusLock) {
                if (statusClearFuture != null && !statusClearFuture.isDone()) {
                    statusClearFuture.cancel(false);
                }
            }
            return;
        }
        synchronized (statusLock) {
            if (statusClearExecutor == null) {
                statusClearExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "StatusClearScheduler");
                    t.setDaemon(true);
                    return t;
                });
            }
            if (statusClearFuture != null && !statusClearFuture.isDone()) {
                statusClearFuture.cancel(false);
            }
            statusClearFuture = statusClearExecutor.schedule(() -> {
                Platform.runLater(() -> {
                    if (lblStatus != null) {
                        if (hasUnsavedChanges()) {
                            lblStatus.setText("⚠ Ungespeicherte Änderungen");
                            lblStatus.setStyle("-fx-text-fill: #ff6b35; -fx-font-weight: bold; -fx-background-color: #fff3cd; -fx-padding: 2 6 2 6; -fx-background-radius: 3;");
                        } else {
                            lblStatus.setText("Bereit");
                            lblStatus.setStyle("-fx-text-fill: #28a745; -fx-font-weight: normal; -fx-background-color: #d4edda; -fx-padding: 2 6 2 6; -fx-background-radius: 3;");
                        }
                    }
                });
            }, delaySeconds, TimeUnit.SECONDS);
        }
    }
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        preferences = Preferences.userNodeForPackage(EditorWindow.class);
        
        // LanguageTool Service initialisieren
        languageToolService = new LanguageToolService();
        languageToolDictionary = new LanguageToolDictionary();
        languageToolExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "LanguageToolWorker");
            t.setDaemon(true);
            return t;
        });
        
        // LanguageTool Einstellung aus Preferences laden
        languageToolEnabled = preferences.getBoolean("languagetool_enabled", false);
        
        // LanguageTool: Prüfe beim Start, ob bereits Text vorhanden ist
        Platform.runLater(() -> {
            // Button-Status aktualisieren, falls LanguageTool aktiviert ist
            if (languageToolEnabled) {
                updateLanguageToolButtonState();
            }
            
            if (languageToolEnabled && codeArea != null && codeArea.getText() != null && !codeArea.getText().trim().isEmpty()) {
                // Warte kurz, damit UI vollständig geladen ist
                Timeline delayTimeline = new Timeline(new KeyFrame(Duration.millis(1000), event -> {
                    checkLanguageToolErrors();
                }));
                delayTimeline.play();
            }
        });
        
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
        
        // Seitenleiste initialisieren
        initializeSidebar();
        
        // Lade gespeicherten Ausklappstatus und Divider-Position (nach initializeSidebar)
        Platform.runLater(() -> {
            loadSidebarState();
        });
        
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
                if (cssPath != null) {
                    if (!mainContainer.getScene().getStylesheets().contains(cssPath)) {
                        mainContainer.getScene().getStylesheets().add(cssPath);
                    }
                    // CSS bereits geladen ist kein Fehler, keine Warnung
                } else {
                    logger.warn("CSS-Datei konnte in initialize nicht geladen werden: css/manuskript.css");
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
                if (cssPath != null) {
                    if (!mainContainer.getScene().getStylesheets().contains(cssPath)) {
                        mainContainer.getScene().getStylesheets().add(cssPath);
                    }
                    // CSS bereits geladen ist kein Fehler, keine Warnung
                } else {
                    logger.warn("CSS-Datei konnte nicht geladen werden: css/manuskript.css");
                }
            }
        });
        
        // Toolbar explizit sichtbar machen
        // btnNew und btnOpen sind in FXML auf visible="false" managed="false" gesetzt
        if (btnSave != null) {
            btnSave.setVisible(true);
            btnSave.setManaged(true);
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
        // WICHTIG: Timer als Instanzvariable speichern, damit er während des Tippens pausiert werden kann
        // WICHTIG: Nur aufrufen, wenn sich der Text nicht geändert hat, um Viewport-Sprünge zu vermeiden
        lastStyledText = codeArea.getText();
        stylingTimer = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            Platform.runLater(() -> {
                // WICHTIG: Überspringe Styling komplett, wenn der Benutzer gerade scrollt
                // Oder wenn sich der Text geändert hat (dann wird Styling später durch Text-Change-Listener aufgerufen)
                if (isScrolling) {
                    return;
                }
                
                // Prüfe, ob sich der Text geändert hat
                String currentText = codeArea.getText();
                if (currentText != null && !currentText.equals(lastStyledText)) {
                    // Text hat sich geändert - aktualisiere lastStyledText, aber rufe applyCombinedStyling() NICHT auf
                    // Das Styling wird durch den Text-Change-Listener oder LanguageTool aufgerufen
                    lastStyledText = currentText;
                    return;
                }
                
                // Text hat sich nicht geändert UND Benutzer scrollt nicht
                // WICHTIG: Rufe applyCombinedStyling() NICHT auf, da es den Viewport zurücksetzen kann
                // Styling wird nur aufgerufen, wenn sich der Text ändert oder LanguageTool neue Matches findet
            });
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
        scrollPane = new VirtualizedScrollPane<>(codeArea);
        // WICHTIG: CSS-Klasse für VirtualizedScrollPane hinzufügen, damit Scrollbalken gestylt werden können
        scrollPane.getStyleClass().add("code-area-scroll-pane");
        
        // Padding für die ScrollPane (links 0, damit es am Rand klebt)
        scrollPane.setStyle("-fx-padding: 5px 5px 5px 0px;");
        
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
        
        // Kontextmenü einrichten
        setupContextMenu();
        
        // Toolbar-Einstellungen laden (Font-Size, Theme, etc.)
        loadToolbarSettings();
        
        // LanguageTool Buttons (nach vollständiger UI-Initialisierung)
        Platform.runLater(() -> {
            setupLanguageToolButtons();
        });
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
                    // WICHTIG: Ignoriere Änderungen, die von Quill kommen (isUpdatingFromQuill)
                    // Diese werden bereits verarbeitet und sollten nicht markAsChanged() auslösen
                    if (isUpdatingFromQuill) {
                        // Prüfe, ob der bereinigte Text gleich ist (dann wurde nichts geändert)
                        String cleanedNew = cleanTextForComparison(newText);
                        String cleanedOld = cleanTextForComparison(oldText);
                        if (cleanedNew.equals(cleanedOld)) {
                            // Keine semantische Änderung - ignoriere komplett
                            logger.debug("Text-Change-Listener: Ignoriere Quill-Update, bereinigter Text ist gleich");
                            return;
                        }
                        // WICHTIG: Auch wenn der bereinigte Text unterschiedlich ist, aber isUpdatingFromQuill true ist,
                        // sollte markAsChanged() nicht aufgerufen werden, da dies eine Quill-Synchronisation ist
                        logger.debug("Text-Change-Listener: Ignoriere Quill-Update, auch wenn Text unterschiedlich ist");
                        return;
                    }
                    
                    // WICHTIG: Pausiere den Styling-Timer während des Tippens, um Cursor-Sprünge zu vermeiden
                    // ABER: Nur wenn der Text sich tatsächlich geändert hat (nicht beim Scrollen)
                    // WICHTIG: Timer wird NICHT pausiert, damit Markierungen beim Scrollen sichtbar bleiben
                    // Stattdessen wird nur sichergestellt, dass applyCombinedStyling() den Viewport nicht ändert
                    // (Das wird bereits in applyCombinedStyling() gemacht)
                    
                    // LanguageTool Fehlerprüfung (wenn aktiviert)
                    // WICHTIG: Prüfe IMMER, auch bei manuellen Korrekturen
                    if (languageToolEnabled) {
                        if (!isApplyingLanguageToolCorrection) {
                            logger.debug("Text geändert (manuelle Korrektur) - lösche alte Matches und starte neue Prüfung");
                            // WICHTIG: Lösche alte Matches sofort, um falsche Unterstreichungen zu vermeiden
                            // Die Positionen der alten Matches sind nach Text-Änderung ungültig
                            int oldMatchCount;
                            synchronized (currentLanguageToolMatches) {
                                oldMatchCount = currentLanguageToolMatches.size();
                                currentLanguageToolMatches.clear();
                            }
                            logger.debug("Text geändert: " + oldMatchCount + " alte Matches gelöscht, starte neue Prüfung");
                            // WICHTIG: Rufe applyCombinedStyling() NICHT sofort auf, da es den Cursor zurücksetzen kann
                            // Die Markierungen werden automatisch entfernt, wenn die neue Prüfung fertig ist
                            // Starte neue Prüfung mit Debouncing
                            checkLanguageToolErrorsDebounced();
                        } else {
                            logger.debug("Text geändert während LanguageTool-Korrektur - überspringe Prüfung");
                        }
                    } else {
                        logger.debug("Text geändert - LanguageTool ist deaktiviert");
                    }
                    
                    // WICHTIG: Wenn der Benutzer im Editor Text ändert, aktualisiere Quill
                    // Verwende debouncing, um zu viele Updates zu vermeiden und UI-Hänger zu verhindern
                    // ABER: Wenn Quill gerade erst vom Nutzer geändert wurde (z.B. Align-Button),
                    // verhindere, dass wir sofort wieder zurücksyncen und die Formatierung verlieren.
                    long now = System.currentTimeMillis();
                    if (now - lastQuillUserChangeTs < 1000) {
                        logger.debug("Text-Change-Listener: Überspringe Editor->Quill Sync wegen frischem Quill-User-Change");
                        return;
                    }
                    if (previewWebView != null && previewStage != null && previewStage.isShowing() && useQuillMode) {
                        // Stoppe vorherige Timeline, falls vorhanden
                        if (editorToQuillUpdateTimeline != null) {
                            editorToQuillUpdateTimeline.stop();
                        }
                        
                        // Erstelle neue Timeline mit Debouncing (500ms für bessere Performance)
                        // WICHTIG: updateQuillContent() wird asynchron aufgerufen, um UI nicht zu blockieren
                        // WICHTIG: Editor → Quill Sync soll IMMER funktionieren (auch wenn Editor fokussiert ist)
                        editorToQuillUpdateTimeline = new Timeline(new KeyFrame(Duration.millis(500), event -> {
                            // Prüfe nochmal, ob Update noch nötig ist
                            if (!isUpdatingFromQuill && !isUpdatingFromCodeArea && 
                                previewWebView != null && previewStage != null && previewStage.isShowing() && useQuillMode) {
                                // Asynchron aufrufen, um UI nicht zu blockieren
                                Platform.runLater(() -> {
                                    updateQuillContent();
                                });
                            }
                            editorToQuillUpdateTimeline = null; // Zurücksetzen nach Ausführung
                        }));
                        editorToQuillUpdateTimeline.play();
                    }
                    
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
            try {
                // Sichere Text-Selektion mit Bounds-Check
                String selectedText = getSelectedTextSafely();
                if (selectedText != null && !selectedText.isEmpty()) {
                    int charCount = selectedText.length();
                    int wordCount = countWords(selectedText);
                    lblSelectionCount.setText("Auswahl: " + charCount + " Zeichen, " + wordCount + " Wörter");
                } else {
                    lblSelectionCount.setText("Auswahl: 0 Zeichen, 0 Wörter");
                }
            } catch (Exception e) {
                // Fehler beim Abrufen der Selektion - sicherer Fallback
                logger.warn("Fehler beim Aktualisieren der Selektion: " + e.getMessage());
                lblSelectionCount.setText("Auswahl: 0 Zeichen, 0 Wörter");
            }
        }
    }
    
    /**
     * Sichere Methode zum Abrufen des selektierten Textes
     */
    private String getSelectedTextSafely() {
        try {
            if (codeArea == null) return null;
            
            // Prüfe ob die Selektion gültig ist
            IndexRange selection = codeArea.getSelection();
            if (selection == null) return null;
            
            int start = selection.getStart();
            int end = selection.getEnd();
            int textLength = codeArea.getLength();
            
            // Bounds-Check
            if (start < 0 || end < 0 || start > textLength || end > textLength || start > end) {
                return null;
            }
            
            return codeArea.getSelectedText();
        } catch (Exception e) {
            logger.warn("Fehler beim sicheren Abrufen der Selektion: " + e.getMessage());
            return null;
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
        btnFind.setTooltip(new Tooltip("Suchen"));
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
        btnReplace.setTooltip(new Tooltip("Aktuellen Treffer ersetzen"));
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
        btnReplaceAll.setTooltip(new Tooltip("Alle Treffer ersetzen"));
        
        // Speichern-Buttons
        btnSaveSearch.setOnAction(e -> {
            String searchText = cmbSearchHistory.getValue();
            if (searchText != null && !searchText.trim().isEmpty()) {
                addToSearchHistory(searchText.trim());
                updateStatus("Suchpattern gespeichert: " + searchText.trim());
            }
        });
        btnSaveSearch.setTooltip(new Tooltip("Suchpattern speichern"));
        
        btnSaveReplace.setOnAction(e -> {
            String replaceText = cmbReplaceHistory.getValue();
            if (replaceText != null && !replaceText.trim().isEmpty()) {
                addToReplaceHistory(replaceText.trim());
                updateStatus("Ersetzungspattern gespeichert: " + replaceText.trim());
            }
        });
        btnSaveReplace.setTooltip(new Tooltip("Ersetzungspattern speichern"));
        
        // Löschen-Buttons
        btnDeleteSearch.setOnAction(e -> {
            String searchText = cmbSearchHistory.getValue();
            if (searchText != null && !searchText.trim().isEmpty()) {
                removeFromSearchHistory(searchText.trim());
                updateStatus("Suchpattern gelöscht: " + searchText.trim());
            }
        });
        btnDeleteSearch.setTooltip(new Tooltip("Suchpattern löschen"));
        
        btnDeleteReplace.setOnAction(e -> {
            String replaceText = cmbReplaceHistory.getValue();
            if (replaceText != null && !replaceText.trim().isEmpty()) {
                removeFromReplaceHistory(replaceText.trim());
                updateStatus("Ersetzungspattern gelöscht: " + replaceText.trim());
            }
        });
        btnDeleteReplace.setTooltip(new Tooltip("Ersetzungspattern löschen"));
        
        // Font-Size Event-Handler
        btnIncreaseFont.setOnAction(e -> changeFontSize(2));
        btnIncreaseFont.setTooltip(new Tooltip("Schriftgröße erhöhen"));
        btnDecreaseFont.setOnAction(e -> changeFontSize(-2));
        btnDecreaseFont.setTooltip(new Tooltip("Schriftgröße verringern"));
        cmbFontSize.setOnAction(e -> changeFontSizeFromComboBox());
        
        // Zeilenabstand-ComboBox entfernt - wird von RichTextFX nicht unterstützt
        cmbParagraphSpacing.setOnAction(e -> {
            changeParagraphSpacingFromComboBox();
        });
        
        // Text-Formatting Event-Handler
        btnBold.setOnAction(e -> formatTextBold());
        btnBold.setTooltip(new Tooltip("Fett (Bold) - **text** oder <b>text</b>"));
        btnItalic.setOnAction(e -> formatTextItalic());
        btnItalic.setTooltip(new Tooltip("Kursiv (Italic) - *text* oder <i>text</i>"));
        btnUnderline.setOnAction(e -> formatTextUnderline());
        btnUnderline.setTooltip(new Tooltip("Unterstrichen - <u>text</u>"));
        btnStrikethrough.setOnAction(e -> formatTextStrikethrough());
        btnStrikethrough.setTooltip(new Tooltip("Durchgestrichen - ~~text~~ oder <s>text</s>"));
        btnSuperscript.setOnAction(e -> formatTextSuperscript());
        btnSuperscript.setTooltip(new Tooltip("Hochgestellt - <sup>text</sup>"));
        btnSubscript.setOnAction(e -> formatTextSubscript());
        btnSubscript.setTooltip(new Tooltip("Tiefgestellt - <sub>text</sub>"));
        btnInsertImage.setOnAction(e -> insertImage());
        btnInsertImage.setTooltip(new Tooltip("Bild einfügen"));
        if (btnInsertLineBreak != null) {
            btnInsertLineBreak.setOnAction(e -> insertLineBreak());
            btnInsertLineBreak.setTooltip(new Tooltip("Zeilenumbruch einfügen (<br>)"));
        }
        if (btnInsertHorizontalLine != null) {
            btnInsertHorizontalLine.setOnAction(e -> insertHorizontalLine());
            btnInsertHorizontalLine.setTooltip(new Tooltip("Horizontale Linie einfügen (---)"));
        }
        if (btnCenter != null) {
            btnCenter.setOnAction(e -> formatTextCenter());
            btnCenter.setTooltip(new Tooltip("Text zentrieren (<center>)"));
        }
        if (btnBig != null) {
            btnBig.setOnAction(e -> formatTextBig());
            btnBig.setTooltip(new Tooltip("Große Schrift (<big>)"));
        }
        if (btnSmall != null) {
            btnSmall.setOnAction(e -> formatTextSmall());
            btnSmall.setTooltip(new Tooltip("Kleine Schrift (<small>)"));
        }
        if (btnMark != null) {
            btnMark.setOnAction(e -> formatTextMark());
            btnMark.setTooltip(new Tooltip("Text markieren (<mark>)"));
        }
        if (btnColorMenu != null) {
            btnColorMenu.setTooltip(new Tooltip("Textfarbe ändern"));
            btnColorMenu.setVisible(true);
            btnColorMenu.setManaged(true);
            // Stelle sicher, dass der Button breit genug ist für "Textfarbe"
            btnColorMenu.setMinWidth(100);
            btnColorMenu.setPrefWidth(100);
            btnColorMenu.setMaxWidth(100);
            // Dropdown nach unten öffnen (Standard)
            btnColorMenu.setPopupSide(javafx.geometry.Side.BOTTOM);
            // ID setzen für CSS-Selektor (Dropdown-Breite wird über CSS gesetzt)
            btnColorMenu.setId("btnColorMenu");
        } else {
            logger.warn("btnColorMenu ist null - Button wurde nicht aus FXML geladen!");
        }
        btnThemeToggle.setOnAction(e -> toggleTheme());
        
        // Abstandskonfiguration Event-Handler werden direkt in den Setup-Methoden gesetzt

        
            
        // btnMacroRegexHelp wurde entfernt
        
        // Textanalyse-Button
        btnTextAnalysis.setOnAction(e -> toggleTextAnalysisPanel());
        btnTextAnalysis.setTooltip(new Tooltip("Textanalyse-Panel ein-/ausblenden"));
        
        // KI-Assistent-Button
        btnKIAssistant.setOnAction(e -> toggleOllamaWindow());
        btnKIAssistant.setTooltip(new Tooltip("KI-Assistent öffnen/schließen"));
        
        // Preview-Button
        btnPreview.setOnAction(e -> togglePreviewWindow());
        btnPreview.setTooltip(new Tooltip("Vorschau-Fenster öffnen/schließen"));
        
        // Sudowrite-Button
        if (btnCopySudowrite != null) {
            btnCopySudowrite.setOnAction(e -> copyForSudowrite());
            btnCopySudowrite.setTooltip(new Tooltip("In Zwischenablage kopieren (Sudowrite-kompatibel)"));
        }
        
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
            btnHelpTools.setTooltip(new Tooltip("Hilfe zu Editor-Tools"));
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
        btnPreviousChapter.setTooltip(new Tooltip("Vorheriges Kapitel öffnen"));
        btnNextChapter.setOnAction(e -> navigateToNextChapter());
        btnNextChapter.setTooltip(new Tooltip("Nächstes Kapitel öffnen"));
        
        // Seitenleiste Event-Handler
        if (btnToggleSidebar != null) {
            btnToggleSidebar.setOnAction(e -> toggleSidebar());
            btnToggleSidebar.setTooltip(new Tooltip("Kapitel-Seitenleiste ein-/ausblenden"));
        }
        if (chapterListView != null) {
            // Auswahl zurücksetzen wenn auf leeren Bereich der ListView geklickt wird
            chapterListView.setOnMousePressed(e -> {
                javafx.scene.control.ListView<DocxFile> listView = (javafx.scene.control.ListView<DocxFile>) e.getSource();
                // Prüfe ob auf ein Item geklickt wurde
                Node target = (Node) e.getTarget();
                
                // Suche nach der ListCell im Parent-Baum
                Node node = target;
                while (node != null && !(node instanceof javafx.scene.control.ListCell)) {
                    node = node.getParent();
                }
                
                if (node == null || !(node instanceof javafx.scene.control.ListCell)) {
                    // Kein Item geklickt - Auswahl zurücksetzen
                    listView.getSelectionModel().clearSelection();
                }
            });
            
            chapterListView.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2) {
                    DocxFile selectedDocxFile = chapterListView.getSelectionModel().getSelectedItem();
                    if (selectedDocxFile != null) {
                        // Prüfe auf ungespeicherte Änderungen
                        if (hasUnsavedChanges()) {
                            if (!showSaveDialogForNavigation()) {
                                return; // Benutzer hat abgebrochen
                            }
                        }
                        navigateToChapter(selectedDocxFile.getFile());
                    }
                }
            });
        }
        
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
        btnRegexHelp.setTooltip(new Tooltip("Hilfe zu regulären Ausdrücken (Regex)"));
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
        btnFindNext.setTooltip(new Tooltip("Nächsten Treffer suchen"));
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
        btnFindPrevious.setTooltip(new Tooltip("Vorherigen Treffer suchen"));
        
        // Toolbar-Events
        btnSave.setOnAction(e -> {
            saveFile();
            markAsSaved();
        });
        btnSave.setTooltip(new Tooltip("Datei speichern (Strg+S)"));
        btnExport.setOnAction(e -> showExportDialog());
        btnExport.setTooltip(new Tooltip("Dokument exportieren (DOCX, PDF, EPUB, etc.)"));
        // btnOpen und btnNew Event-Handler entfernt - Buttons sind unsichtbar
        btnToggleSearch.setOnAction(e -> toggleSearchPanel());
        btnToggleSearch.setTooltip(new Tooltip("Suchen/Ersetzen ein-/ausblenden"));
        btnToggleMacro.setOnAction(e -> toggleMacroPanel());
        btnToggleMacro.setTooltip(new Tooltip("Makros ein-/ausblenden"));
        btnToggleParagraphMarking.setOnAction(e -> toggleParagraphMarking());
        
        // Undo/Redo Event-Handler
        btnUndo.setOnAction(e -> codeArea.undo());
        btnUndo.setTooltip(new Tooltip("Rückgängig (Strg+Z)"));
        btnRedo.setOnAction(e -> codeArea.redo());
        btnRedo.setTooltip(new Tooltip("Wiederholen (Strg+Y)"));
        
        // Keyboard-Shortcuts
        setupKeyboardShortcuts();
        
        // Text-Selektion Event-Listener für KI-Assistent
        codeArea.selectionProperty().addListener((obs, oldSelection, newSelection) -> {
            if (ollamaWindow != null && ollamaWindow.isShowing()) {
                try {
                    String selectedText = getSelectedTextSafely();
                    if (selectedText != null && !selectedText.trim().isEmpty()) {
                        // Automatisch selektierten Text in das Eingabefeld kopieren
                        ollamaWindow.updateSelectedText(selectedText);
                    }
                } catch (Exception e) {
                    logger.warn("Fehler beim Abrufen der Selektion für KI-Assistent: " + e.getMessage());
                }
            }
        });
        
        // Cursor-Position kontinuierlich speichern (nur wenn ein Kapitel geladen ist)
        codeArea.caretPositionProperty().addListener((obs, oldPos, newPos) -> {
            if (newPos != null && newPos.intValue() >= 0 && originalDocxFile != null) {
                // Speichere die Position nur wenn ein Kapitel geladen ist
                Platform.runLater(() -> {
                    saveCurrentCursorPosition();
                });
            }
        });
    }
    
    private void setupKeyboardShortcuts() {
        // Globaler Event-Filter für CTRL+F überall im Editor-Fenster
        if (mainContainer != null) {
            mainContainer.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                if (event.isControlDown() && event.getCode() == KeyCode.F) {
                    toggleSearchPanel();
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
                        // Ctrl+F wird bereits vom globalen Filter behandelt
                        // Hier nur andere Shortcuts
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
                    case I:
                        toggleItalic();
                        event.consume();
                        break;
                    case B:
                        toggleBold();
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
                    case U:
                        toggleUnderline();
                        event.consume();
                        break;
                    case M:
                        // Öffne Markdown-Hilfe
                        if (btnHelpMarkdown != null) {
                            HelpSystem.showHelpWindow("markdown_syntax.html");
                        }
                        event.consume();
                        break;
                    case LEFT:
                        // Ctrl + Pfeil links -> vorherige Datei
                        navigateToPreviousChapter();
                        event.consume();
                        break;
                    case RIGHT:
                        // Ctrl + Pfeil rechts -> nächste Datei
                        navigateToNextChapter();
                        event.consume();
                        break;
                    case X:
                        // Ctrl + X -> Aktuelle Zeile löschen, Cursor nach links (nur wenn kein Text markiert ist)
                        // Wenn Text markiert ist, wird das normale Ausschneiden-Verhalten verwendet
                        String selectedText = getSelectedTextSafely();
                        if (selectedText == null || selectedText.isEmpty()) {
                            deleteCurrentLine();
                            event.consume();
                        }
                        // Wenn Text markiert ist, lasse das Standard-Verhalten (Ausschneiden) zu
                        break;
                    case UP:
                        // Ctrl + Pfeil hoch -> zum Anfang des Dokuments
                        jumpToDocumentStart();
                        event.consume();
                        break;
                    case DOWN:
                        // Ctrl + Pfeil unten -> zum Ende des Dokuments
                        jumpToDocumentEnd();
                        event.consume();
                        break;
                    default:
                        // Alle anderen Tasten ignorieren
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
        
        // Event-Filter für Pos1/Ende (ohne Ctrl) -> zum Anfang/Ende des Dokuments
        codeArea.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (!event.isControlDown()) {
                if (event.getCode() == KeyCode.HOME) {
                    // Pos1 alleine -> Anfang des Dokuments
                    jumpToDocumentStart();
                    event.consume();
                } else if (event.getCode() == KeyCode.END) {
                    // Ende alleine -> Ende des Dokuments
                    jumpToDocumentEnd();
                    event.consume();
                }
            }
            // Ctrl + Pos1/Ende wird bereits im switch-Statement behandelt (falls nötig)
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
    
    /**
     * Toggle für kursiven Text (Ctrl+I)
     */
    private void toggleItalic() {
        String selectedText = getSelectedTextSafely();
        if (selectedText != null && !selectedText.isEmpty()) {
            // Text ist ausgewählt - umschließen mit *
            String newText = "*" + selectedText + "*";
            codeArea.replaceSelection(newText);
        } else {
            // Kein Text ausgewählt - * einfügen
            codeArea.insertText(codeArea.getCaretPosition(), "**");
            // Cursor zwischen die Sterne setzen
            codeArea.moveTo(codeArea.getCaretPosition() - 1);
        }
    }
    
    /**
     * Toggle für fetten Text (Ctrl+B)
     */
    private void toggleBold() {
        String selectedText = getSelectedTextSafely();
        if (selectedText != null && !selectedText.isEmpty()) {
            // Text ist ausgewählt - umschließen mit **
            String newText = "**" + selectedText + "**";
            codeArea.replaceSelection(newText);
        } else {
            // Kein Text ausgewählt - ** einfügen
            codeArea.insertText(codeArea.getCaretPosition(), "****");
            // Cursor zwischen die Sterne setzen
            codeArea.moveTo(codeArea.getCaretPosition() - 2);
        }
    }
    
    /**
     * Undo-Funktion (Ctrl+Z)
     */
    private void undo() {
        codeArea.undo();
    }
    
    /**
     * Redo-Funktion (Ctrl+Y)
     */
    private void redo() {
        codeArea.redo();
    }
    
    /**
     * Toggle für unterstrichenen Text (Ctrl+U)
     */
    private void toggleUnderline() {
        String selectedText = getSelectedTextSafely();
        if (selectedText != null && !selectedText.isEmpty()) {
            // Text ist ausgewählt - umschließen mit <u> tags
            String newText = "<u>" + selectedText + "</u>";
            codeArea.replaceSelection(newText);
        } else {
            // Kein Text ausgewählt - <u></u> einfügen
            codeArea.insertText(codeArea.getCaretPosition(), "<u></u>");
            // Cursor zwischen die Tags setzen
            codeArea.moveTo(codeArea.getCaretPosition() - 4);
        }
    }
    
    /**
     * Löscht die aktuelle Zeile und setzt den Cursor nach links (Ctrl+X)
     */
    private void deleteCurrentLine() {
        if (codeArea == null) {
            return;
        }
        
        String text = codeArea.getText();
        if (text == null || text.isEmpty()) {
            return;
        }
        
        int caretPosition = codeArea.getCaretPosition();
        
        // Finde den Anfang und das Ende der aktuellen Zeile
        int lineStart = caretPosition > 0 ? text.lastIndexOf('\n', caretPosition - 1) + 1 : 0;
        int lineEnd = text.indexOf('\n', caretPosition);
        if (lineEnd == -1) {
            lineEnd = text.length();
        }
        
        // Prüfe, ob die Zeile leer ist (kein Inhalt zwischen lineStart und lineEnd)
        boolean isEmptyLine = (lineStart == lineEnd);
        
        // Bestimme die zu löschende Range (inklusive Newline, wenn vorhanden)
        int deleteStart = lineStart;
        int deleteEnd;
        
        if (lineEnd < text.length()) {
            // Es gibt ein Newline-Zeichen nach der Zeile - lösche es mit
            deleteEnd = lineEnd + 1;
        } else {
            // Letzte Zeile - kein Newline danach
            deleteEnd = lineEnd;
        }
        
        // Wenn die Zeile leer ist und nicht die erste Zeile
        if (isEmptyLine && lineStart > 0) {
            // Leere Zeile - lösche das Newline-Zeichen davor (das die leere Zeile erzeugt)
            deleteStart = lineStart - 1;
            deleteEnd = lineStart;
        }
        
        // Lösche die Zeile
        if (deleteStart < deleteEnd && deleteEnd <= text.length()) {
            codeArea.deleteText(deleteStart, deleteEnd);
            
            // Cursor nach links setzen (an den Anfang der vorherigen Zeile)
            if (deleteStart > 0) {
                String newText = codeArea.getText();
                int newLineStart = newText.lastIndexOf('\n', deleteStart - 1) + 1;
                codeArea.moveTo(newLineStart);
            } else {
                codeArea.moveTo(0);
            }
        } else if (text.length() > 0) {
            // Fallback: Dokument komplett löschen
            codeArea.clear();
            codeArea.moveTo(0);
        }
    }
    
    /**
     * Gibt den Editor-Key für das aktuelle Kapitel zurück
     */
    private String getCurrentEditorKey() {
        if (originalDocxFile != null) {
            String chapterName = originalDocxFile.getName();
            if (chapterName.toLowerCase().endsWith(".docx")) {
                chapterName = chapterName.substring(0, chapterName.length() - 5);
            }
            return chapterName + ".md";
        }
        return null;
    }
    
    /**
     * Speichert die aktuelle Cursor-Position und Scroll-Position für das aktuelle Kapitel
     */
    private void saveCurrentCursorPosition() {
        // Nicht speichern während des Ladens
        if (isLoadingChapter) {
            return;
        }
        
        if (codeArea != null) {
            String editorKey = getCurrentEditorKey();
            if (editorKey != null) {
                // Cursor-Position speichern
                int position = codeArea.getCaretPosition();
                chapterCursorPositions.put(editorKey, position);
                
                // Scroll-Position speichern (relative Position: Paragraph-Index des Cursors)
                // Da estimatedScrollY read-only ist, speichern wir die Paragraph-Index
                // und versuchen beim Wiederherstellen, die Position zu approximieren
                try {
                    int currentParagraph = codeArea.getCurrentParagraph();
                    // Speichere die Paragraph-Index als Double
                    chapterScrollPositions.put(editorKey, (double) currentParagraph);
                    logger.debug("Cursor- und Scroll-Position gespeichert für {}: Position={}, Paragraph={}", editorKey, position, currentParagraph);
                } catch (Exception e) {
                    logger.debug("Fehler beim Speichern der Scroll-Position: " + e.getMessage());
                    // Fallback: Nur Cursor-Position speichern
                    logger.debug("Cursor-Position gespeichert für {}: {}", editorKey, position);
                }
            }
        }
    }
    
    /**
     * Stellt die gespeicherte Cursor-Position und Scroll-Position für das aktuelle Kapitel wieder her
     * Wenn keine Position gespeichert ist oder das Kapitel zum ersten Mal in dieser Sitzung geladen wird,
     * wird der Cursor an den Anfang gesetzt
     */
    public void restoreCursorPosition() {
        if (codeArea != null) {
            String editorKey = getCurrentEditorKey();
            if (editorKey != null) {
                String text = codeArea.getText();
                if (text != null) {
                    // Prüfe, ob dieses Kapitel bereits in dieser Sitzung geladen wurde
                    boolean isFirstLoadInSession = !chaptersLoadedThisSession.contains(editorKey);
                    
                    // Wenn es das erste Laden in dieser Sitzung ist, immer am Anfang positionieren
                    if (isFirstLoadInSession) {
                        chaptersLoadedThisSession.add(editorKey);
                        // Cursor am Anfang, Viewport am Anfang
                        codeArea.moveTo(0);
                        // Explizit zum Anfang scrollen mit mehreren Versuchen (Timeline für bessere Timing-Kontrolle)
                        Platform.runLater(() -> {
                            try {
                                int paragraphIndex = codeArea.offsetToPosition(0, Bias.Forward).getMajor();
                                codeArea.showParagraphInViewport(paragraphIndex);
                                codeArea.requestFollowCaret();
                                
                                // Zusätzliche Versuche mit Timeline für bessere Zuverlässigkeit
                                Timeline timeline = new Timeline(
                                    new KeyFrame(Duration.millis(50), event -> {
                                        try {
                                            codeArea.moveTo(0);
                                            int paraIndex = codeArea.offsetToPosition(0, Bias.Forward).getMajor();
                                            codeArea.showParagraphInViewport(paraIndex);
                                            codeArea.requestFollowCaret();
                                        } catch (Exception e) {
                                            logger.debug("Fehler beim Timeline-Scrollen: " + e.getMessage());
                                        }
                                    }),
                                    new KeyFrame(Duration.millis(100), event -> {
                                        try {
                                            codeArea.moveTo(0);
                                            int paraIndex = codeArea.offsetToPosition(0, Bias.Forward).getMajor();
                                            codeArea.showParagraphInViewport(paraIndex);
                                            codeArea.requestFollowCaret();
                                            codeArea.requestFocus();
                                            logger.debug("Kapitel {} zum ersten Mal in dieser Sitzung geladen, Cursor und Scrollposition am Anfang gesetzt", editorKey);
                                        } catch (Exception e) {
                                            logger.debug("Fehler beim finalen Scrollen: " + e.getMessage());
                                            codeArea.requestFollowCaret();
                                            codeArea.requestFocus();
                                        }
                                    })
                                );
                                timeline.play();
                            } catch (Exception e) {
                                logger.debug("Fehler beim Setzen der Anfangsposition: " + e.getMessage());
                                // Fallback: Nur requestFollowCaret
                                codeArea.requestFollowCaret();
                                codeArea.requestFocus();
                            }
                        });
                        return;
                    }
                    
                    final int position;
                    final Double savedScrollValue;
                    
                    if (chapterCursorPositions.containsKey(editorKey)) {
                        // Gespeicherte Position verwenden
                        int savedPos = chapterCursorPositions.get(editorKey);
                        // Sicherstellen, dass die Position gültig ist
                        if (savedPos < 0) {
                            position = 0;
                        } else if (savedPos > text.length()) {
                            position = text.length();
                        } else {
                            position = savedPos;
                        }
                        
                        // Gespeicherte Scroll-Position verwenden
                        if (chapterScrollPositions.containsKey(editorKey)) {
                            savedScrollValue = chapterScrollPositions.get(editorKey);
                            logger.debug("Cursor- und Scroll-Position wiederhergestellt für {}: Position={}, ScrollValue={}", editorKey, position, savedScrollValue);
                        } else {
                            // Keine gespeicherte Scroll-Position
                            savedScrollValue = null;
                            logger.debug("Cursor-Position wiederhergestellt für {}: Position={}, keine Scroll-Position", editorKey, position);
                        }
                    } else {
                        // Keine gespeicherte Position - Cursor an den Anfang
                        position = 0;
                        savedScrollValue = null;
                        logger.debug("Keine gespeicherte Position für {}, Cursor an den Anfang gesetzt", editorKey);
                    }
                    
                    // WICHTIG: Position zuerst setzen
                    codeArea.moveTo(position);
                    
                    // Prüfe, ob eine gespeicherte Position vorhanden ist
                    final boolean hasSavedPosition = chapterCursorPositions.containsKey(editorKey);
                    
                    // Dann Scroll-Position wiederherstellen
                    Platform.runLater(() -> {
                        try {
                            // Berechne die Paragraph des Cursors
                            final int cursorParagraph = codeArea.offsetToPosition(position, Bias.Forward).getMajor();
                            
                            if (hasSavedPosition) {
                                // Nur wenn eine Position gespeichert war: Cursor in die Mitte
                                codeArea.showParagraphAtCenter(cursorParagraph);
                                
                                // Finale Position setzen mit nochmaliger Verzögerung - Cursor in die Mitte
                                Platform.runLater(() -> {
                                    codeArea.moveTo(position);
                                    int currentCursorPara = codeArea.offsetToPosition(position, Bias.Forward).getMajor();
                                    // Verwende Timeline für bessere Timing-Kontrolle
                                    Timeline timeline = new Timeline(new KeyFrame(Duration.millis(50), event -> {
                                        codeArea.showParagraphAtCenter(currentCursorPara);
                                        codeArea.requestFocus(); // Fokus setzen, damit Cursor sichtbar ist
                                    }));
                                    timeline.play();
                                    logger.debug("Finale Cursor- und Scroll-Position gesetzt: Position={}", position);
                                });
                            } else {
                                // Keine gespeicherte Position: Cursor am Anfang, Viewport am Anfang
                                codeArea.moveTo(0);
                                codeArea.requestFollowCaret();
                                codeArea.requestFocus(); // Fokus setzen, damit Cursor sichtbar ist
                                logger.debug("Cursor am Anfang gesetzt (keine gespeicherte Position)");
                            }
                        } catch (Exception e) {
                            logger.debug("Fehler beim Wiederherstellen der Position: " + e.getMessage());
                            // Fallback: Cursor an den Anfang
                            codeArea.moveTo(0);
                            codeArea.requestFollowCaret();
                            codeArea.requestFocus(); // Fokus setzen, damit Cursor sichtbar ist
                        }
                    });
                }
            }
        }
    }
    
    /**
     * Springt zum Anfang des Dokuments (Pos1 oder Ctrl + Pfeil hoch)
     */
    private void jumpToDocumentStart() {
        if (codeArea != null) {
            codeArea.moveTo(0);
            // Scrolle zum Anfang
            Platform.runLater(() -> {
                try {
                    int paragraphIndex = codeArea.offsetToPosition(0, Bias.Forward).getMajor();
                    codeArea.showParagraphInViewport(paragraphIndex);
                    codeArea.requestFollowCaret();
                } catch (Exception e) {
                    logger.debug("Fehler beim Scrollen zum Anfang: " + e.getMessage());
                }
            });
            codeArea.requestFocus();
        }
    }
    
    /**
     * Springt zum Ende des Dokuments (Ende oder Ctrl + Pfeil unten)
     */
    private void jumpToDocumentEnd() {
        if (codeArea != null) {
            String text = codeArea.getText();
            if (text != null && text.length() > 0) {
                int endPosition = text.length();
                codeArea.moveTo(endPosition);
                // Scrolle zum Ende
                Platform.runLater(() -> {
                    try {
                        int paragraphIndex = codeArea.offsetToPosition(endPosition, Bias.Forward).getMajor();
                        codeArea.showParagraphInViewport(paragraphIndex);
                        codeArea.requestFollowCaret();
                    } catch (Exception e) {
                        logger.debug("Fehler beim Scrollen zum Ende: " + e.getMessage());
                    }
                });
                codeArea.requestFocus();
            }
        }
    }
    
    /**
     * Öffnet einen Dialog zum Einfügen eines Bildes
     */
    private void insertImage() {
        // CustomAlert erstellen
        CustomAlert alert = new CustomAlert(Alert.AlertType.INFORMATION, "Bild einfügen");
        alert.setHeaderText("Bild einfügen");
        
        // VBox für Custom Content
        VBox contentBox = new VBox(10);
        contentBox.setPadding(new Insets(10));
        
        // Pfad-Feld - zuletzt verwendeten Pfad laden
        Label pathLabel = new Label("Pfad:");
        TextField pathField = new TextField();
        pathField.setPromptText("Pfad zum Bild");
        HBox.setHgrow(pathField, Priority.ALWAYS);
        
        // Zuletzt verwendeten Pfad laden
        String lastImagePath = preferences != null ? preferences.get("lastImagePath", "") : "";
        if (lastImagePath != null && !lastImagePath.isEmpty()) {
            pathField.setText(lastImagePath);
        }
        
        // Text-Feld (Alt-Text) - zuletzt verwendeten Alt-Text laden
        Label textLabel = new Label("Alt-Text:");
        TextField textField = new TextField();
        textField.setPromptText("Alternativer Text für das Bild");
        HBox.setHgrow(textField, Priority.ALWAYS);
        
        // Zuletzt verwendeten Alt-Text laden
        String lastAltText = preferences != null ? preferences.get("lastImageAltText", "") : "";
        if (lastAltText != null && !lastAltText.isEmpty()) {
            textField.setText(lastAltText);
        }
        
        // FileChooser-Button
        Button btnBrowse = new Button("Durchsuchen...");
        btnBrowse.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Bild auswählen");
            fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Bilddateien", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.webp"),
                new FileChooser.ExtensionFilter("Alle Dateien", "*.*")
            );
            
            // Initial Directory setzen (Priorität: aktueller Pfad > zuletzt verwendetes Verzeichnis > aktuelles Dateiverzeichnis)
            String currentPath = pathField.getText();
            File initialDir = null;
            
            if (currentPath != null && !currentPath.isEmpty()) {
                File currentFile = new File(currentPath);
                if (currentFile.exists() && currentFile.getParentFile() != null) {
                    initialDir = currentFile.getParentFile();
                }
            }
            
            if (initialDir == null && preferences != null) {
                String lastImageDirectory = preferences.get("lastImageDirectory", null);
                if (lastImageDirectory != null && !lastImageDirectory.isEmpty()) {
                    File dir = new File(lastImageDirectory);
                    if (dir.exists() && dir.isDirectory()) {
                        initialDir = dir;
                    }
                }
            }
            
            if (initialDir == null) {
                // Fallback: Verzeichnis der aktuell geöffneten Datei verwenden
                if (currentFile != null && currentFile.getParentFile() != null) {
                    initialDir = currentFile.getParentFile();
                }
            }
            
            if (initialDir != null) {
                fileChooser.setInitialDirectory(initialDir);
            }
            
            File selectedFile = fileChooser.showOpenDialog(stage);
            if (selectedFile != null) {
                pathField.setText(selectedFile.getAbsolutePath());
                
                // Verzeichnis speichern
                if (preferences != null) {
                    File parentDir = selectedFile.getParentFile();
                    if (parentDir != null) {
                        preferences.put("lastImageDirectory", parentDir.getAbsolutePath());
                        try {
                            preferences.flush();
                        } catch (Exception ex) {
                            logger.debug("Konnte Bild-Verzeichnis nicht speichern: " + ex.getMessage());
                        }
                    }
                }
            }
        });
        
        // Layout für Pfad-Feld und Button
        HBox pathBox = new HBox(10);
        pathBox.getChildren().addAll(pathField, btnBrowse);
        HBox.setHgrow(pathField, Priority.ALWAYS);
        
        // Alles zusammenfügen
        contentBox.getChildren().addAll(pathLabel, pathBox, textLabel, textField);
        
        // Custom Content setzen
        alert.setCustomContent(contentBox);
        
        // Theme anwenden
        alert.applyTheme(currentThemeIndex);
        alert.initOwner(stage);
        
        // Buttons
        ButtonType insertButton = new ButtonType("Einfügen");
        ButtonType cancelButton = new ButtonType("Abbrechen");
        alert.setButtonTypes(insertButton, cancelButton);
        
        // Dialog anzeigen
        Optional<ButtonType> result = alert.showAndWait();
        
        if (result.isPresent() && result.get() == insertButton) {
            String imagePath = pathField.getText();
            String altText = textField.getText();
            
            if (imagePath != null && !imagePath.trim().isEmpty()) {
                // Prüfe ob der Pfad im Projektverzeichnis liegt
                // Wenn ja, verwende nur den Dateinamen (relativer Pfad)
                String finalImagePath = imagePath;
                // Verwende originalDocxFile.getParentFile() als Projektverzeichnis
                if (originalDocxFile != null && originalDocxFile.getParentFile() != null) {
                    try {
                        File imageFile = new File(imagePath);
                        if (!imageFile.exists() || !imageFile.isFile()) {
                            logger.warn("Bilddatei existiert nicht: {}", imagePath);
                        } else {
                            File projectDir = originalDocxFile.getParentFile();
                            String projectDirPath = projectDir.getCanonicalPath();
                            File imageDir = imageFile.getParentFile();
                            String imageDirPath = imageDir != null ? imageDir.getCanonicalPath() : null;
                            boolean imageInProject = imageDirPath != null && imageDirPath.equals(projectDirPath);
                            if (imageInProject) {
                                finalImagePath = imageFile.getName();
                                logger.debug("Bildpfad relativiert: {} -> {}", imagePath, finalImagePath);
                            } else {
                                // Bild liegt außerhalb: in Projektordner kopieren, damit es immer auffindbar bleibt
                                File targetFile = new File(projectDir, imageFile.getName());
                                if (targetFile.exists() && !targetFile.getCanonicalPath().equals(imageFile.getCanonicalPath())) {
                                    String base = imageFile.getName();
                                    int dot = base.lastIndexOf('.');
                                    String name = dot > 0 ? base.substring(0, dot) : base;
                                    String ext = dot > 0 ? base.substring(dot) : "";
                                    for (int i = 1; i < 1000; i++) {
                                        targetFile = new File(projectDir, name + "_" + i + ext);
                                        if (!targetFile.exists()) break;
                                    }
                                }
                                java.nio.file.Files.copy(imageFile.toPath(), targetFile.toPath(),
                                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                                finalImagePath = targetFile.getName();
                                logger.debug("Bild in Projektordner kopiert: {} -> {}", imagePath, targetFile.getAbsolutePath());
                            }
                        }
                    } catch (IOException e) {
                        logger.debug("Fehler beim Normalisieren/Kopieren des Bildpfads: " + e.getMessage());
                    }
                }
                
                // Markdown-Bild-Syntax erstellen
                String markdownImage;
                if (altText != null && !altText.trim().isEmpty()) {
                    // Mit Alt-Text: Bild ohne Alt-Text, dann Leerzeile, dann Blockquote mit zentriertem Alt-Text
                    markdownImage = "![](" + finalImagePath + ")\n\n><center>" + altText + "</center>";
                } else {
                    // Ohne Alt-Text: Nur das Bild
                    markdownImage = "![](" + finalImagePath + ")";
                }
                
                // Bild an aktueller Cursor-Position einfügen
                codeArea.insertText(codeArea.getCaretPosition(), markdownImage);
                
                // Pfad und Alt-Text für nächste Verwendung speichern
                if (preferences != null) {
                    preferences.put("lastImagePath", imagePath);
                    if (altText != null && !altText.trim().isEmpty()) {
                        preferences.put("lastImageAltText", altText);
                    }
                    try {
                        preferences.flush();
                    } catch (Exception ex) {
                        logger.debug("Konnte Bild-Einstellungen nicht speichern: " + ex.getMessage());
                    }
                }
            }
        }
    }
    
    private void handleQuoteReplacement(String inputQuote) {
        if (currentQuoteStyleIndex < 0 || currentQuoteStyleIndex >= QUOTE_MAPPING.length) {
            return;
        }
        
        String content = codeArea.getText();
        int caretPosition = codeArea.getCaretPosition();
        
        // INTELLIGENTE APOSTROPH vs. ANFÜHRUNGSZEICHEN ERKENNUNG
        // WICHTIG: Apostroph-Prüfung ZUERST, dann Anführungszeichen
        boolean isApostrophe = isApostropheContext(content, caretPosition);
        
        String replacement;
        
        if (isApostrophe) {
            // APOSTROPH-REGELN: IMMER ' verwenden (gerades Apostroph für alle Sprachen)
            replacement = "'";
            logger.debug("Apostroph erkannt: " + replacement);
        } else {
            // ANFÜHRUNGSZEICHEN-REGELN: Bei Zitaten und Hervorhebungen
            boolean shouldBeClosing = determineQuotationState(content, caretPosition, inputQuote.equals("'"));
            if (inputQuote.equals("\"")) {
                replacement = shouldBeClosing ? QUOTE_MAPPING[currentQuoteStyleIndex][1] : QUOTE_MAPPING[currentQuoteStyleIndex][0];
            } else {
                replacement = shouldBeClosing ? SINGLE_QUOTE_MAPPING[currentQuoteStyleIndex][1] : SINGLE_QUOTE_MAPPING[currentQuoteStyleIndex][0];
            }
            logger.debug("Anführungszeichen erkannt: " + replacement);
        }
        
        // Ersetze das Zeichen
        codeArea.insertText(caretPosition, replacement);
    }
    
    /**
     * Intelligente Apostroph-Erkennung basierend auf Kontext
     */
    private boolean isApostropheContext(String content, int position) {
        if (position <= 0) return false;
        
        char charBefore = content.charAt(position - 1);
        
        // Prüfe ob nach dem Cursor ein Zeichen kommt
        boolean hasCharAfter = position < content.length();
        char charAfter = hasCharAfter ? content.charAt(position) : '\0';
        
        // WICHTIG: Wenn VORHER ein Leerzeichen ist, ist es IMMER ein Anführungszeichen, kein Apostroph
        // Nach einem Leerzeichen steht niemals ein Apostroph!
        if (position > 1) {
            char charBeforeBefore = content.charAt(position - 2);
            if (charBeforeBefore == ' ' || charBeforeBefore == '\n' || charBeforeBefore == '\t') {
                // Vorher war ein Leerzeichen -> es ist IMMER ein Anführungszeichen, kein Apostroph
                return false;
            }
        }
        
        // KLARER APOSTROPH: Buchstabe davor UND Buchstabe dahinter (hab's, don't)
        if (Character.isLetter(charBefore) && hasCharAfter && Character.isLetter(charAfter)) {
            return true;
        }
        
        // APOSTROPH VOR ANFÜHRUNGSZEICHEN: Buchstabe davor, dann Anführungszeichen (Paleus'«)
        if (Character.isLetter(charBefore) && hasCharAfter && isQuotationMark(charAfter)) {
            return true;
        }
        
        // APOSTROPH AM WORTENDE: Buchstabe davor, dann Leerzeichen/Satzzeichen/Ende
        // ABER NICHT wenn vorher ein Leerzeichen war (dann ist es ein Anführungszeichen)
        if (Character.isLetter(charBefore) && 
            (!hasCharAfter || charAfter == ' ' || charAfter == '\n' || charAfter == '\t' ||
             charAfter == '.' || charAfter == ',' || charAfter == '!' || charAfter == '?' ||
             charAfter == ';' || charAfter == ':')) {
            
            // Prüfe, ob es ein öffnendes Anführungszeichen im vorherigen Kontext gibt
            // Suche rückwärts nach einem öffnenden einfachen Anführungszeichen
            // Suche bis zum Satzanfang oder bis zu einem doppelten Anführungszeichen
            boolean hasOpeningQuote = false;
            for (int i = position - 1; i >= 0; i--) {
                char c = content.charAt(i);
                
                // Prüfe auf öffnende einfache Anführungszeichen (abhängig vom aktuellen Stil)
                if (currentQuoteStyleIndex >= 0 && currentQuoteStyleIndex < SINGLE_QUOTE_MAPPING.length) {
                    String openingQuote = SINGLE_QUOTE_MAPPING[currentQuoteStyleIndex][0];
                    if (c == openingQuote.charAt(0)) {
                        hasOpeningQuote = true;
                        break;
                    }
                }
                
                // Stoppe bei Satzende (aber nicht bei Leerzeichen, da Zitate über mehrere Wörter gehen können)
                if (c == '.' || c == '!' || c == '?' || c == '\n') {
                    // Prüfe, ob es ein doppeltes Anführungszeichen gibt, das den Satz beginnt
                    // Wenn ja, könnte es ein neuer Satz sein, also weiter suchen
                    break;
                }
                
                // Stoppe bei doppelten Anführungszeichen (könnte ein neuer Abschnitt sein)
                if (c == '"' || c == '\u201E' || c == '\u201C' || c == '\u00AB' || c == '\u00BB') {
                    break;
                }
            }
            
            // Wenn ein öffnendes Anführungszeichen gefunden wurde, ist es wahrscheinlich ein schließendes Anführungszeichen, kein Apostroph
            if (hasOpeningQuote) {
                return false;
            }
            
            // Ansonsten ist es ein Apostroph
            return true;
        }
        
        // KEIN APOSTROPH: Alles andere ist ein Anführungszeichen
        return false;
    }
    
    /**
     * Intelligente Anführungszeichen-Erkennung basierend auf Kontext
     */
    private boolean isQuotationContext(String content, int position) {
        if (position <= 0 || position >= content.length()) return false;
        
        char charBefore = content.charAt(position - 1);
        char charAfter = content.charAt(position);
        
        // Anführungszeichen-Regeln (NUR bei echten Zitaten):
        // 1. Am Anfang/Ende des Textes
        // 2. Nach Leerzeichen/Zeilenumbruch
        // 3. Vor Leerzeichen/Zeilenumbruch
        // 4. Nach Satzzeichen (., !, ?, :, ;)
        // 5. Bei Zitaten: "Was ist ein 'Zonk'?"
        
        // ABER: NICHT bei Apostrophen! "Paleus'" = Apostroph, nicht Anführungszeichen
        boolean isQuotation = (position == 0 || position == content.length()) ||
                             (charBefore == ' ' || charBefore == '\n' || charBefore == '.' ||
                              charBefore == '!' || charBefore == '?' || charBefore == ',' ||
                              charBefore == ';' || charBefore == ':') ||
                             (charAfter == ' ' || charAfter == '\n' || charAfter == '.' ||
                              charAfter == '!' || charAfter == '?' || charAfter == ',' ||
                              charAfter == ';' || charAfter == ':');
        
        // WICHTIG: Prüfe auf Apostroph-Kontext und verhindere falsche Erkennung
        if (isQuotation) {
            // Prüfe ob es sich um einen Apostroph handelt
            boolean isApostrophe = isApostropheContext(content, position);
            if (isApostrophe) {
                logger.debug("Apostroph erkannt, kein Anführungszeichen");
                return false; // Apostroph, nicht Anführungszeichen
            }
        }
        
        return isQuotation;
    }
    
    /**
     * Bestimmt den Zustand der Anführungszeichen (öffnend/schließend)
     * @param content Der Textinhalt
     * @param position Die aktuelle Position
     * @param isSingleQuote true wenn es ein einfaches Anführungszeichen ist, false für doppelte
     */
    private boolean determineQuotationState(String content, int position, boolean isSingleQuote) {
        // Zähle die Anzahl der Anführungszeichen vor der aktuellen Position
        int quoteCount = 0;
        for (int i = 0; i < position; i++) {
            char c = content.charAt(i);
            
            if (isSingleQuote) {
                // Zähle einfache Anführungszeichen (abhängig vom aktuellen Stil)
                if (currentQuoteStyleIndex >= 0 && currentQuoteStyleIndex < SINGLE_QUOTE_MAPPING.length) {
                    String openingQuote = SINGLE_QUOTE_MAPPING[currentQuoteStyleIndex][0];
                    String closingQuote = SINGLE_QUOTE_MAPPING[currentQuoteStyleIndex][1];
                    if (c == openingQuote.charAt(0) || c == closingQuote.charAt(0) || 
                        (currentQuoteStyleIndex == 2 && c == '\'')) { // Englisch
                        quoteCount++;
                    }
                }
            } else {
                // Zähle doppelte Anführungszeichen (abhängig vom aktuellen Stil)
                if (currentQuoteStyleIndex >= 0 && currentQuoteStyleIndex < QUOTE_MAPPING.length) {
                    String openingQuote = QUOTE_MAPPING[currentQuoteStyleIndex][0];
                    String closingQuote = QUOTE_MAPPING[currentQuoteStyleIndex][1];
                    if (c == openingQuote.charAt(0) || c == closingQuote.charAt(0) || 
                        (currentQuoteStyleIndex == 2 && c == '"')) { // Englisch
                        quoteCount++;
                    }
                }
            }
        }
        
        // Wenn ungerade Anzahl -> öffnend, wenn gerade -> schließend
        return quoteCount % 2 == 1;
    }
    
    /**
     * Gibt das sprachspezifische Apostroph-Zeichen zurück
     */
    private String getApostropheForLanguage(int languageIndex) {
        // Apostrophe sollten IMMER das gerade Anführungszeichen ' (U+0027) sein,
        // nicht das typographische ' (U+2019)
        return "\u0027"; // ' - gerades Apostroph für alle Sprachen
    }
    
    /**
     * Prüft ob ein Zeichen ein Anführungszeichen ist
     */
    private boolean isQuotationMark(char c) {
        return c == '\u0022' || c == '\u00AB' || c == '\u00BB' || c == '\u201E' || c == '\u201C' || 
               c == '\u2039' || c == '\u203A' || c == '\u201A' || c == '\u2019' || c == '\u201B' || c == '\u201D';
    }
    /**
     * Dynamische Überprüfung für französische und schweizer Anführungszeichen
     * Konvertiert automatisch zwischen Anführungszeichen und Apostrophen basierend auf nachfolgenden Buchstaben
     */
    private void setupDynamicQuoteCheck() {
        // Event-Handler für kontinuierliche Überprüfung während des Tippens
        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            // Verhindere Aufruf während Normalisierung, um Undo-Historie-Probleme zu vermeiden
            if (isNormalizingHtmlTags || isCheckingQuotes) {
                return;
            }
            
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
     * Spielt das Notification-Sound ab
     */
    private void playNotificationSound() {
        try {
            URL soundUrl = getClass().getResource("/sound/pling.wav");
            if (soundUrl != null) {
                Media sound = new Media(soundUrl.toString());
                MediaPlayer mediaPlayer = new MediaPlayer(sound);
                mediaPlayer.play();
            }
        } catch (Exception e) {
            logger.warn("Fehler beim Abspielen des Notification-Sounds: {}", e.getMessage());
        }
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
        
        // Akustisches Signal abspielen
        playNotificationSound();
        
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
        
        // WICHTIG: Setze Owner auf das Editor-Fenster, damit es richtig positioniert wird
        if (stage != null) {
            errorStage.initOwner(stage);
        }
        
        // WICHTIG: KEINE Modality setzen - der Dialog soll nicht modal sein
        // Aber trotzdem im Vordergrund erscheinen
        
        // Fenster anzeigen
        errorStage.show();
        
        // WICHTIG: Fenster in den Vordergrund bringen - mit Platform.runLater für bessere Kompatibilität
        Platform.runLater(() -> {
            errorStage.setIconified(false);
            errorStage.toFront();
            errorStage.requestFocus();
            // Zusätzlich: Stelle sicher, dass das Fenster wirklich im Vordergrund ist
            errorStage.setAlwaysOnTop(true);
            // Nach kurzer Zeit wieder auf false setzen, damit es nicht immer im Vordergrund bleibt
            javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(javafx.util.Duration.millis(100));
            pause.setOnFinished(e -> errorStage.setAlwaysOnTop(false));
            pause.play();
        });
        
        // Status aktualisieren
        updateStatus("⚠️ " + quoteErrors.size() + " Anführungszeichen-Fehler gefunden und angezeigt.", true);
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
            
            // Dann zum Absatz scrollen - in die Mitte des Bildschirms
            final int finalParagraphIndex = codeArea.offsetToPosition(paragraphIndex, Bias.Forward).getMajor();
            Platform.runLater(() -> {
                try {
                    // Für VirtualizedScrollPane: Erst in Viewport bringen, dann zentrieren
                    codeArea.showParagraphInViewport(finalParagraphIndex);
                    
                    // Kleine Verzögerung für VirtualizedScrollPane-Update
                    Timeline timeline = new Timeline(new KeyFrame(Duration.millis(50), event -> {
                        try {
                            codeArea.showParagraphAtCenter(finalParagraphIndex);
                        } catch (Exception e) {
                            logger.warn("Fehler beim Zentrieren: " + e.getMessage());
                        }
                    }));
                    timeline.play();
                } catch (Exception e) {
                    logger.warn("Fehler beim Scrollen zum gefundenen Text: " + e.getMessage());
                    // Fallback: Einfaches Scrollen
                    codeArea.showParagraphInViewport(finalParagraphIndex);
                }
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
            updateStatus("⚠️ Absatz im Editor nicht gefunden", true);
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
        
        // Prüfe auf mehrzeilige Dialoge: Finde Dialog-Blöcke
        List<int[]> dialogBlocks = findDialogBlocks(paragraphs);
        
        logger.debug("Anführungszeichen-Check: " + dialogBlocks.size() + " Dialog-Blöcke gefunden");
        
        for (int i = 0; i < paragraphs.length; i++) {
            String paragraph = paragraphs[i];
            if (paragraph.trim().isEmpty()) continue;
            
            // Prüfe, ob dieser Absatz Teil eines Dialog-Blocks ist
            boolean isPartOfDialog = isPartOfDialogBlock(i, dialogBlocks);
            
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
            
            // Wenn ungerade Anzahl UND nicht Teil eines Dialog-Blocks -> Fehler sammeln für Anzeige
            if (doubleQuotes % 2 != 0 && !isPartOfDialog) {
                logger.debug("FEHLER: Ungerade doppelte Anführungszeichen in Absatz " + i);
                // Fehler für Anzeige sammeln
                collectQuoteErrors(paragraph, "Doppelte Anführungszeichen", doubleQuotes);
            }
            
            if (singleQuotes % 2 != 0 && !isPartOfDialog) {
                logger.debug("FEHLER: Ungerade einfache Anführungszeichen in Absatz " + i);
                // Fehler für Anzeige sammeln
                collectQuoteErrors(paragraph, "Einfache Anführungszeichen", singleQuotes);
            }
        }
    }
    
    /**
     * Prüft, ob es sich um einen mehrzeiligen Dialog handelt
     * Dialog: Erster Absatz beginnt mit Anführungszeichen, letzter Absatz endet mit Anführungszeichen
     */
    private boolean isMultiParagraphDialog(String[] paragraphs) {
        if (paragraphs.length < 2) return false;
        
        // Suche nach Dialog-Blöcken: Finde alle Absätze, die mit Anführungszeichen beginnen
        List<Integer> dialogStartIndices = new ArrayList<>();
        List<Integer> dialogEndIndices = new ArrayList<>();
        
        for (int i = 0; i < paragraphs.length; i++) {
            String paragraph = paragraphs[i].trim();
            if (paragraph.isEmpty()) continue;
            
            // Absatz beginnt mit Anführungszeichen -> möglicher Dialog-Start
            if (startsWithQuote(paragraph)) {
                dialogStartIndices.add(i);
            }
            
            // Absatz endet mit Anführungszeichen -> möglicher Dialog-Ende
            if (endsWithQuote(paragraph)) {
                dialogEndIndices.add(i);
            }
        }
        
        // Debug-Ausgabe
        logger.debug("Dialog-Erkennung:");
        logger.debug("  Dialog-Start-Indices: " + dialogStartIndices);
        logger.debug("  Dialog-Ende-Indices: " + dialogEndIndices);
        
        // Prüfe, ob es einen Dialog-Block gibt, der über mehrere Absätze geht
        for (int startIndex : dialogStartIndices) {
            for (int endIndex : dialogEndIndices) {
                if (endIndex > startIndex) {
                    // Gefunden: Dialog von startIndex bis endIndex
                    logger.debug("  Gefundener Dialog-Block: " + startIndex + " bis " + endIndex);
                    return true;
                }
            }
        }
        
        logger.debug("  Kein mehrzeiliger Dialog gefunden");
        return false;
    }
    
    /**
     * Findet alle Dialog-Blöcke im Text
     * Sequenzielle Prüfung: Absatz mit 1 Anführungszeichen -> suche nächsten mit Anführungszeichen
     */
    private List<int[]> findDialogBlocks(String[] paragraphs) {
        List<int[]> dialogBlocks = new ArrayList<>();
        
        logger.debug("Dialog-Erkennung: Sequenzielle Prüfung der Absätze");
        
        for (int i = 0; i < paragraphs.length; i++) {
            String paragraph = paragraphs[i].trim();
            if (paragraph.isEmpty()) continue;
            
            // Zähle doppelte Anführungszeichen in diesem Absatz
            int doubleQuotes = 0;
            for (char c : paragraph.toCharArray()) {
                if (c == '"' || c == '\u201E' || c == '\u201C' || c == '\u201D' || c == '\u00AB' || c == '\u00BB') {
                    doubleQuotes++;
                }
            }
            
            // Absatz hat ungerade Anzahl > 1 -> Fehler (wird später behandelt)
            if (doubleQuotes > 1 && doubleQuotes % 2 != 0) {
                logger.debug("    -> Ungerade Anzahl > 1: Fehler (wird später behandelt)");
                continue;
            }
            
            // Absatz hat genau 1 Anführungszeichen -> möglicher Multi-Start
            if (doubleQuotes == 1) {
                logger.debug("    -> Möglicher Multi-Start: Suche nächsten Absatz mit Anführungszeichen");
                
                // Suche nächsten Absatz mit Anführungszeichen
                for (int j = i + 1; j < paragraphs.length; j++) {
                    String nextParagraph = paragraphs[j].trim();
                    if (nextParagraph.isEmpty()) continue;
                    
                    // Zähle doppelte Anführungszeichen im nächsten Absatz
                    int nextDoubleQuotes = 0;
                    for (char c : nextParagraph.toCharArray()) {
                        if (c == '"' || c == '\u201E' || c == '\u201C' || c == '\u201D' || c == '\u00AB' || c == '\u00BB') {
                            nextDoubleQuotes++;
                        }
                    }
                    
                    if (nextDoubleQuotes > 0) {
                        
                        if (nextDoubleQuotes > 1) {
                            // Zahl > 1 -> Das war kein Multi! Fehler (wird später behandelt)
                            logger.debug("      -> Zahl > 1: Das war kein Multi! Fehler (wird später behandelt)");
                            break;
                        } else if (nextDoubleQuotes == 1 && endsWithQuote(nextParagraph)) {
                            // Zahl = 1 und abschließend -> Multi-Dialog gefunden
                            dialogBlocks.add(new int[]{i, j});
                            logger.debug("      -> Multi-Dialog gefunden: " + i + " bis " + j);
                            break;
                        }
                    }
                }
            }
        }
        
        return dialogBlocks;
    }
    
    /**
     * Prüft, ob ein Absatz Teil eines Dialog-Blocks ist
     */
    private boolean isPartOfDialogBlock(int paragraphIndex, List<int[]> dialogBlocks) {
        for (int[] block : dialogBlocks) {
            if (paragraphIndex >= block[0] && paragraphIndex <= block[1]) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Prüft, ob ein Text mit einem Anführungszeichen beginnt
     */
    private boolean startsWithQuote(String text) {
        if (text == null || text.trim().isEmpty()) return false;
        String trimmed = text.trim();
        char firstChar = trimmed.charAt(0);
        boolean result = firstChar == '"' || firstChar == '\u201E' || firstChar == '\u201C' || 
               firstChar == '\u201D' || firstChar == '\u00AB' || firstChar == '\u00BB';
        
        return result;
    }
    
    /**
     * Prüft, ob ein Text mit einem Anführungszeichen endet
     */
    private boolean endsWithQuote(String text) {
        if (text == null || text.trim().isEmpty()) return false;
        String trimmed = text.trim();
        char lastChar = trimmed.charAt(trimmed.length() - 1);
        boolean result = lastChar == '"' || lastChar == '\u201E' || lastChar == '\u201C' || 
               lastChar == '\u201D' || lastChar == '\u00AB' || lastChar == '\u00BB';
        
        return result;
    }
    
    /**
     * Überprüft den Text auf ungerade Anführungszeichen (Fehlererkennung).
     * Die Konvertierung von Anführungszeichen wird vollständig in QuotationMarkConverter durchgeführt.
     */
    // Flag um zu verhindern, dass checkAndConvertQuotes die Undo-Historie löscht
    private boolean isCheckingQuotes = false;
    // Flag um zu verhindern, dass normalizeHtmlTagsInText die Undo-Historie löscht
    private boolean isNormalizingHtmlTags = false;
    // Flag um zu verhindern, dass markEmptyLines die Undo-Historie löscht
    private boolean isMarkingEmptyLines = false;
    
    private void checkAndConvertQuotes(String text) {
        // Verhindere rekursive Aufrufe und Undo-Historie-Probleme
        if (isCheckingQuotes) {
            return;
        }
        if (text == null || text.isEmpty()) return;
        
        // Fehler-Liste für diese Überprüfung leeren
        quoteErrors.clear();
        
        // ZUVERLÄSSIGKEITS-CHECK: Überprüfe jeden Absatz auf ungerade Anführungszeichen (nur Warnung, keine automatische Korrektur)
        String[] paragraphs = text.split("\n");
        
        // Prüfe auf mehrzeilige Dialoge: Erster Absatz beginnt mit Anführungszeichen, letzter endet mit Anführungszeichen
        boolean isMultiParagraphDialog = isMultiParagraphDialog(paragraphs);
        
        for (int i = 0; i < paragraphs.length; i++) {
            String paragraph = paragraphs[i];
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
            
            // Wenn ungerade Anzahl UND nicht Teil eines mehrzeiligen Dialogs -> Fehler sammeln für Anzeige
            if (doubleQuotes % 2 != 0 && !isMultiParagraphDialog) {
                // Fehler für Anzeige sammeln
                collectQuoteErrors(paragraph, "Doppelte Anführungszeichen", doubleQuotes);
            }
            
            if (singleQuotes % 2 != 0 && !isMultiParagraphDialog) {
                // Fehler für Anzeige sammeln
                collectQuoteErrors(paragraph, "Einfache Anführungszeichen", singleQuotes);
            }
        }
        
        // HINWEIS: Die Konvertierung von Anführungszeichen wird vollständig in QuotationMarkConverter durchgeführt
        // Diese Methode dient nur noch zur Fehlererkennung (ungerade Anführungszeichen)
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
                updateStatus("Keine Treffer gefunden", true);
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
                    updateStatus("Keine Treffer gefunden", true);
                }
                return;
            }
            
            if (totalMatches == 0) {
                updateMatchCount(0, 0);
                updateStatus("Keine Treffer gefunden", true);
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
                updateStatus("Keine Treffer gefunden", true);
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
        int flags = Pattern.MULTILINE | Pattern.DOTALL;
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
        int flags = Pattern.MULTILINE | Pattern.DOTALL;
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
        highlightTextCentered(start, end);
    }
    
    /**
     * Markiert Text und scrollt mittig zum Viewport (für globale Suche)
     */
    private void highlightTextCentered(int start, int end) {
        if (start >= 0 && end >= 0) {
            // Setze den Cursor an die Position und markiere den Text
            codeArea.displaceCaret(start);
            codeArea.selectRange(start, end);
            
            // RichTextFX-spezifische Scroll-Logik mit VirtualizedScrollPane
            // WICHTIG: Berechne Paragraph basierend auf der tatsächlichen Position
            int paragraphValue;
            try {
                paragraphValue = codeArea.offsetToPosition(start, Bias.Forward).getMajor();
            } catch (Exception e) {
                // Fallback: Verwende getCurrentParagraph()
                paragraphValue = codeArea.getCurrentParagraph();
            }
            final int currentParagraph = paragraphValue;
            
            // Verwende Platform.runLater für bessere Scroll-Performance
            Platform.runLater(() -> {
                try {
                    // WICHTIG: Für globale Suche - direkt mittig im Viewport anzeigen
                    // Verwende scrollEditorToParagraphCentered für bessere Zentrierung
                    scrollEditorToParagraphCentered(currentParagraph);
                    
                    // Zusätzliche Versuche mit verschiedenen Verzögerungen für bessere Zuverlässigkeit
                    Timeline timeline1 = new Timeline(new KeyFrame(Duration.millis(100), event -> {
                        try {
                            // Nochmal mittig anzeigen
                            codeArea.showParagraphAtCenter(currentParagraph);
                            codeArea.requestFollowCaret();
                        } catch (Exception e) {
                            logger.debug("Fehler beim ersten Scroll-Versuch: " + e.getMessage());
                        }
                    }));
                    timeline1.play();
                    
                    Timeline timeline2 = new Timeline(new KeyFrame(Duration.millis(250), event -> {
                        try {
                            // Nochmal mittig anzeigen für maximale Zuverlässigkeit
                            codeArea.showParagraphAtCenter(currentParagraph);
                            codeArea.requestFollowCaret();
                            // Stelle sicher, dass der Cursor sichtbar ist
                            codeArea.requestFocus();
                        } catch (Exception e) {
                            logger.debug("Fehler beim zweiten Scroll-Versuch: " + e.getMessage());
                        }
                    }));
                    timeline2.play();
                    
                    // Zusätzlicher Versuch nach längerer Verzögerung für maximale Zuverlässigkeit
                    Timeline timeline3 = new Timeline(new KeyFrame(Duration.millis(400), event -> {
                        try {
                            // Nochmal mittig anzeigen, falls die vorherigen Versuche nicht funktioniert haben
                            codeArea.showParagraphAtCenter(currentParagraph);
                            codeArea.requestFollowCaret();
                        } catch (Exception e) {
                            logger.debug("Fehler beim dritten Scroll-Versuch: " + e.getMessage());
                        }
                    }));
                    timeline3.play();
                } catch (Exception e) {
                    logger.warn("Fehler beim Scrollen zum gefundenen Text: " + e.getMessage());
                    // Fallback: Einfaches Scrollen
                    try {
                        codeArea.showParagraphInViewport(currentParagraph);
                        codeArea.requestFollowCaret();
                    } catch (Exception e2) {
                        logger.warn("Fehler beim Fallback-Scrollen: " + e2.getMessage());
                    }
                }
            });
            
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
        // Suchtext aus Lektorat/KI immer als Literal suchen (nicht als Regex), sonst findet z. B. "Hast du geschlafen", fragte Kata leise. nicht
        if (chkRegexSearch != null && chkRegexSearch.isSelected()) {
            chkRegexSearch.setSelected(false);
        }
        if (chkWholeWord != null && chkWholeWord.isSelected()) {
            chkWholeWord.setSelected(false);
        }

        boolean found = applySearchTerm(trimmed);
        
        // Fallback 1: Erstes und letztes Zeichen sind beides Anführungszeichen → beide entfernen.
        if (!found && trimmed.length() > 2 && isQuotationMark(trimmed.charAt(0)) && isQuotationMark(trimmed.charAt(trimmed.length() - 1))) {
            found = applySearchTerm(trimmed.substring(1, trimmed.length() - 1));
            if (found) updateStatus("Text ohne umschließende Anführungszeichen gefunden und markiert.");
        }
        // Fallback 2: Nur erstes Zeichen ist Anführungszeichen (z. B. "Hast du geschlafen", fragte kata leise.)
        if (!found && trimmed.length() > 1 && isQuotationMark(trimmed.charAt(0))) {
            found = applySearchTerm(trimmed.substring(1));
            if (found) updateStatus("Text ohne einleitendes Anführungszeichen gefunden und markiert.");
        }
        // Fallback 3: Nur letztes Zeichen ist Anführungszeichen (KI setzt manchmal eines ans Ende).
        if (!found && trimmed.length() > 1 && isQuotationMark(trimmed.charAt(trimmed.length() - 1))) {
            found = applySearchTerm(trimmed.substring(0, trimmed.length() - 1));
            if (found) updateStatus("Text ohne abschließendes Anführungszeichen gefunden und markiert.");
        }
        // Fallback 4: Abschließenden Punkt weglassen (wird in HTML/WebView oft nicht mitselektiert oder ist anderes Zeichen).
        if (!found && trimmed.length() > 1 && trimmed.charAt(trimmed.length() - 1) == '.') {
            found = applySearchTerm(trimmed.substring(0, trimmed.length() - 1));
            if (found) updateStatus("Text ohne abschließenden Punkt gefunden und markiert.");
        }
        
        if (!found) {
            updateStatus("Der markierte Text wurde nicht im Editor gefunden.", true);
            // Debug: Ausgabe für Fehlersuche (Suchtext vs. Editor-Inhalt)
            if (codeArea != null) {
                String currentSearch = cmbSearchHistory != null ? cmbSearchHistory.getValue() : null;
                String content = codeArea.getText();
                if (currentSearch != null && content != null) {
                    int idx = content.indexOf(currentSearch);
                    StringBuilder sb = new StringBuilder();
                    sb.append("[Suche fehlgeschlagen] Länge: ").append(currentSearch.length());
                    sb.append(" | Regex: ").append(chkRegexSearch != null && chkRegexSearch.isSelected());
                    sb.append(" | WholeWord: ").append(chkWholeWord != null && chkWholeWord.isSelected());
                    sb.append(" | indexOf: ").append(idx >= 0 ? idx : "nicht gefunden");
                    if (!currentSearch.isEmpty()) {
                        sb.append(" | Erstes Zeichen: '").append(currentSearch.charAt(0)).append("' (U+").append(Integer.toHexString(currentSearch.charAt(0)).toUpperCase()).append(")");
                        if (currentSearch.length() > 1) {
                            sb.append(" | Letztes: '").append(currentSearch.charAt(currentSearch.length() - 1)).append("' (U+").append(Integer.toHexString(currentSearch.charAt(currentSearch.length() - 1)).toUpperCase()).append(")");
                        }
                    }
                    if (idx >= 0) {
                        sb.append(" | Hinweis: Text ist im Editor an Position ").append(idx).append(" vorhanden (indexOf), aber Pattern-Suche ergab 0 Treffer → ggf. Regex/WholeWord ausschalten.");
                    }
                    logger.info(sb.toString());
                    // Suchtext-Vorschau (für Log lesbar: Zeilenumbrüche als ¶, max. 60 Zeichen)
                    String preview = currentSearch.length() > 60 ? currentSearch.substring(0, 60) + "…" : currentSearch;
                    logger.info("[Suche] Suchtext: \"" + preview.replace("\\", "\\\\").replace("\n", "¶").replace("\r", "") + "\"");
                    if (logger.isDebugEnabled()) {
                        String snippet = content.length() > 300 ? content.substring(0, 300) + "..." : content;
                        logger.debug("[Suche] Editor-Anfang: " + snippet.replace("\n", "¶").replace("\r", ""));
                    }
                }
            }
        }

        if (!wasVisible) {
            Platform.runLater(() -> cmbSearchHistory.getEditor().selectAll());
        }
    }
    
    /**
     * Setzt Suchtext, Flags und führt Suche aus (für globale Suche)
     */
    public void setSearchAndExecute(String searchText, boolean regex, boolean caseSensitive, boolean wholeWord) {
        if (searchText == null || searchText.trim().isEmpty()) {
            return;
        }
        
        // Öffne Suchpanel
        if (!searchPanelVisible) {
            showSearchPanel();
        }
        
        // Setze Flags
        if (chkRegexSearch != null) {
            chkRegexSearch.setSelected(regex);
        }
        if (chkCaseSensitive != null) {
            chkCaseSensitive.setSelected(caseSensitive);
        }
        if (chkWholeWord != null) {
            chkWholeWord.setSelected(wholeWord);
        }
        
            // Setze Suchtext und führe Suche aus
        String trimmed = searchText.trim();
        if (cmbSearchHistory != null) {
            addToSearchHistory(trimmed);
            cmbSearchHistory.setValue(trimmed);
            // Führe Suche aus - mit Verzögerung, damit Editor vollständig geladen ist
            Platform.runLater(() -> {
                // Erhöhte Verzögerung für vollständiges Laden (besonders wenn Editor gerade geöffnet wurde)
                Timeline delayTimeline = new Timeline(new KeyFrame(Duration.millis(300), event -> {
                    findText();
                    // Nach der Suche explizit zum ersten Treffer scrollen
                    // findText() ruft bereits findNext() auf, das zum ersten Treffer springt,
                    // aber wir müssen nochmal explizit scrollen, um sicherzustellen, dass es sichtbar ist
                    Platform.runLater(() -> {
                        // Zusätzliche Verzögerung, damit der Editor vollständig gerendert ist
                        Timeline scrollTimeline = new Timeline(new KeyFrame(Duration.millis(100), e -> {
                            if (totalMatches > 0 && !cachedMatchPositions.isEmpty() && cachedPattern != null) {
                                int firstMatchStart = cachedMatchPositions.get(0);
                                // Berechne die tatsächliche End-Position des Treffers
                                Matcher matchMatcher = cachedPattern.matcher(codeArea.getText());
                                if (matchMatcher.find(firstMatchStart)) {
                                    int firstMatchEnd = matchMatcher.end();
                                    // Verwende highlightText mit verbesserter Scroll-Logik, die mittig im Viewport anzeigt
                                    highlightTextCentered(firstMatchStart, firstMatchEnd);
                                } else {
                                    // Fallback: verwende Suchtext-Länge
                                    highlightTextCentered(firstMatchStart, firstMatchStart + trimmed.length());
                                }
                            }
                        }));
                        scrollTimeline.play();
                    });
                }));
                delayTimeline.play();
            });
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
        if (lblStatus != null) {
        lblStatus.setText(message);
            lblStatus.setStyle("-fx-text-fill: #28a745; -fx-font-weight: normal; -fx-background-color: #d4edda; -fx-padding: 2 6 2 6; -fx-background-radius: 3;");
        }
        scheduleStatusClear(5, false);
    }

    public void updateStatus(String message, boolean isError) {
        if (isError) {
            updateStatusError(message);
            } else {
            updateStatus(message);
            }
    }
    
    public void updateStatusError(String message) {
        if (lblStatus != null) {
            lblStatus.setText(message);
            // Inline-Styling setzen - wird nach Theme-Apply gesetzt, um CSS zu überschreiben
            Platform.runLater(() -> {
                lblStatus.setStyle("-fx-text-fill: #ff6b35; -fx-font-weight: bold; -fx-background-color: #fff3cd; -fx-padding: 2 6 2 6; -fx-background-radius: 3;");
            });
        }
        scheduleStatusClear(5, false);
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
                alert.showAndWait(stage);
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
                String markdownContent = cleanTextForExport(codeArea.getText());
                Clipboard clipboard = Clipboard.getSystemClipboard();
                ClipboardContent content = new ClipboardContent();
                
                // Verwende Word-kompatibles HTML-Format für bessere Kompatibilität mit Word-ähnlichen Programmen
                String htmlContent = convertMarkdownToHTMLForClipboard(markdownContent);
                content.putHtml(htmlContent);
                // Fallback: Rohes Markdown als Text (enthält > und spezielle Zeilen unverändert)
                content.putString(markdownContent);
                clipboard.setContent(content);
                
                updateStatus("✅ Kapitel in Zwischenablage kopiert (Word-kompatibles Format)");
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
                // Statt <hr> verwenden wir einen Paragraph mit Bindestrichen, damit Sudowrite und andere Textverarbeitungen die Linie nicht entfernen
                html.append("<p style=\"text-align: center; border-top: 1px solid #ccc; margin: 20px 0; padding-top: 10px;\">─────────────────────────────────────────────</p>\n");
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
            
            // Prüfe auf <c> oder <center> Tags
            Pattern centerPattern = Pattern.compile("(?s)<(?:c|center)>(.*?)</(?:c|center)>");
            Matcher centerMatcher = centerPattern.matcher(line);
            if (centerMatcher.find()) {
                String centerText = centerMatcher.group(1);
                html.append("<div style=\"text-align: center;\">").append(convertInlineMarkdown(centerText)).append("</div>\n");
                continue;
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
        
        // Horizontale Linien beibehalten (als --- im Plain Text)
        // Wird bereits korrekt behandelt, da --- nicht durch die obigen Replacements entfernt wird
        
        return text;
    }
    
    private String convertInlineMarkdown(String text) {
        return text
            // Superscript und Subscript (müssen vor anderen Formatierungen behandelt werden)
            .replaceAll("<sup>(.*?)</sup>", "<sup>$1</sup>") // Behalte <sup> Tags
            .replaceAll("<sub>(.*?)</sub>", "<sub>$1</sub>") // Behalte <sub> Tags
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
    
    /**
     * Konvertiert Markdown zu HTML für die Zwischenablage mit Word-kompatiblen inline Styles
     * Diese Version verwendet inline Styles statt CSS-Klassen für bessere Kompatibilität mit Word-ähnlichen Programmen
     */
    private String convertMarkdownToHTMLForClipboard(String markdown) {
        // Minimal-HTML, das von vielen Web-Editoren (auch Sudowrite) eher akzeptiert wird:
        // - Keine Tabellen/Codeblocks mit Styles, nur grundlegende Tags
        // - Absätze als <p>, Zeilenumbrüche als <br>, Fett/Kursiv als <strong>/<em>
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html><head><meta charset=\"UTF-8\"></head><body>\n");
        String[] lines = markdown.split("\n", -1);
        boolean lastWasEmpty = false; // Verhindert mehrere aufeinanderfolgende <br>
        boolean lastWasParagraph = false; // Verhindert <br> direkt nach <p>
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();

            // Leere Zeile -> Absatzumbruch (aber nur wenn nicht direkt nach einem Absatz)
            // <p> Tags haben bereits einen Absatzumbruch, daher kein zusätzliches <br> nötig
            if (trimmed.isEmpty()) {
                // Nur <br> einfügen, wenn:
                // 1. Die vorherige Zeile NICHT leer war (verhindert mehrere <br>)
                // 2. Die vorherige Zeile KEIN Absatz war (verhindert <br> nach <p>)
                if (!lastWasEmpty && !lastWasParagraph) {
                    html.append("<br>\n");
                    lastWasEmpty = true;
                    lastWasParagraph = false;
                } else {
                    lastWasEmpty = true;
                    lastWasParagraph = false;
                }
                continue;
            }
            lastWasEmpty = false;
            lastWasParagraph = false; // Wird unten auf true gesetzt, wenn ein Absatz erzeugt wird

            // Blockquote: als wörtliches ">Text" ausgeben, ohne <p>-Wrapper (einige Tools strippen sonst den Inhalt)
            if (trimmed.startsWith(">")) {
                String quoteText = trimmed.substring(1).trim();
                // Nach dem '>' wird der restliche Text HTML-escaped, damit z.B. "<center>" sichtbar bleibt
                // Zusätzlich in einem <div>, damit viele Editoren die Zeile nicht droppen
                html.append("<div>&gt;").append(escapeHtml(quoteText)).append("</div>\n");
                continue;
            }

            // Überschriften auf <p><strong> … </strong></p> abbilden
            if (trimmed.startsWith("# ")) {
                html.append("<p><strong>").append(convertInlineMarkdownForClipboard(trimmed.substring(2))).append("</strong></p>\n");
                lastWasParagraph = true;
                continue;
            } else if (trimmed.startsWith("## ")) {
                html.append("<p><strong>").append(convertInlineMarkdownForClipboard(trimmed.substring(3))).append("</strong></p>\n");
                lastWasParagraph = true;
                continue;
            } else if (trimmed.startsWith("### ")) {
                html.append("<p><strong>").append(convertInlineMarkdownForClipboard(trimmed.substring(4))).append("</strong></p>\n");
                lastWasParagraph = true;
                continue;
            }

            // Listen: als einfache Bullet-Zeilen ausgeben
            if (trimmed.matches("^[-*+]\\s+.*")) {
                html.append("<p>&bull; ").append(convertInlineMarkdownForClipboard(trimmed.substring(trimmed.indexOf(' ') + 1))).append("</p>\n");
                lastWasParagraph = true;
                continue;
            } else if (trimmed.matches("^\\d+\\.\\s+.*")) {
                html.append("<p>").append(convertInlineMarkdownForClipboard(trimmed)).append("</p>\n");
                lastWasParagraph = true;
                continue;
            }

            // Horizontale Linie
            if (trimmed.matches("^[-*_]{3,}$")) {
                html.append("<p>──────────</p>\n");
                lastWasParagraph = true;
                continue;
            }

            // Codeblock-Markierungen ignorieren; Inhalt als normaler Text ausgeben
            if (trimmed.startsWith("```")) {
                continue;
            }

            // Standardabsatz
            html.append("<p>").append(convertInlineMarkdownForClipboard(line)).append("</p>\n");
            lastWasParagraph = true;
        }
        html.append("</body></html>");
        return html.toString();
    }

    /**
     * Kopiert den aktuellen Inhalt im Sudowrite-kompatiblen Format in die Zwischenablage.
     * Verwendet das minimalistische HTML plus rohes Markdown als Fallback.
     */
    private void copyForSudowrite() {
        try {
            String markdownContent = cleanTextForExport(codeArea.getText());
            
            // Entferne den Kapiteltitel (# Kapitel) am Anfang, falls vorhanden
            String[] lines = markdownContent.split("\n", -1);
            if (lines.length > 0 && lines[0].trim().startsWith("#")) {
                // Erste Zeile entfernen und wieder zusammenfügen
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i < lines.length; i++) {
                    if (i > 1) sb.append("\n");
                    sb.append(lines[i]);
                }
                markdownContent = sb.toString();
            }
            
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();

            String htmlContent = convertMarkdownToHTMLForClipboard(markdownContent);
            content.putHtml(htmlContent);
            // Fallback: Rohes Markdown als Text (enthält > und spezielle Zeilen unverändert)
            content.putString(markdownContent);

            clipboard.setContent(content);
            updateStatus("✅ In Zwischenablage kopiert (Sudowrite)");
        } catch (Exception ex) {
            updateStatusError("Fehler beim Kopieren: " + ex.getMessage());
            logger.error("Fehler beim Kopieren für Sudowrite", ex);
        }
    }
    
    /**
     * Konvertiert inline Markdown zu HTML mit inline Styles für Word-Kompatibilität
     */
    private String convertInlineMarkdownForClipboard(String text) {
        // Vereinfachte Inline-Formatierung mit HTML-Standard-Tags (strong/em), da einige Tools Styles strippen
        return text
            // Fett (zwei Sternchen)
            .replaceAll("\\*\\*(.*?)\\*\\*", "<strong>$1</strong>")
            // Kursiv (ein Sternchen)
            .replaceAll("\\*(.*?)\\*", "<em>$1</em>")
            // Inline-Code
            .replaceAll("`(.*?)`", "<code style=\"background-color: #f8f9fa; padding: 2px 4px; border-radius: 3px;\">$1</code>")
            // Links
            .replaceAll("\\[([^\\]]+)\\]\\(([^)]+)\\)", "<a href=\"$2\">$1</a>")
            // Durchgestrichen (zwei Tilden)
            .replaceAll("~~(.*?)~~", "<span style=\"text-decoration: line-through;\">$1</span>")
            // Hervorgehoben (zwei Gleichheitszeichen)
            .replaceAll("==(.*?)==", "<span style=\"background-color: yellow;\">$1</span>");
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
        // Prüfe ob englische Anführungszeichen für KI-Assistent aktiv sind
        boolean aiQuotesActive = (cmbQuoteStyle != null && cmbQuoteStyle.isDisabled() && currentQuoteStyleIndex == 2);
        boolean wasConverted = false;
        
        // Wenn ja, vor dem Speichern zurückkonvertieren zum ursprünglichen Format
        if (aiQuotesActive && originalQuoteStyleIndex >= 0 && originalQuoteStyleIndex != 2) {
            wasConverted = true;
            // Temporär zurückkonvertieren zum ursprünglichen Format
            convertAllQuotationMarksInText(QUOTE_STYLES[originalQuoteStyleIndex][0]);
        }
        
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
                    
                    // WICHTIG: Markiere als gespeichert (setzt hasUnsavedChanges = false)
                    markAsSaved();
                } catch (IOException e) {
                    updateStatusError("Fehler beim Speichern: " + e.getMessage());
                    // Wieder zu englisch konvertieren bei Fehler
                    if (wasConverted) {
                        convertAllQuotationMarksInText(QUOTE_STYLES[2][0]);
                    }
                    return;
                }
                
                // Dialog für DOCX-Export anzeigen
                showDocxExportDialog();
            }
            // Nach dem Speichern wieder zu englisch konvertieren, wenn wir konvertiert haben
            if (wasConverted) {
                convertAllQuotationMarksInText(QUOTE_STYLES[2][0]);
            }
            return;
        }
        
        // Bei neuen Dateien (currentFile = null) direkt Save As verwenden
        if (currentFile == null) {
            // Wieder zu englisch konvertieren, wenn wir konvertiert haben
            if (wasConverted) {
                convertAllQuotationMarksInText(QUOTE_STYLES[2][0]);
            }
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
            // saveToFile() kümmert sich selbst um die Rückkonvertierung
            saveToFile(target);
            currentFile = target; // künftige Saves gehen wieder hierhin
            originalContent = cleanTextForComparison(codeArea.getText());
        } else {
            // Wieder zu englisch konvertieren, wenn wir konvertiert haben
            if (wasConverted) {
                convertAllQuotationMarksInText(QUOTE_STYLES[2][0]);
            }
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
        // Prüfe ob englische Anführungszeichen für KI-Assistent aktiv sind
        boolean aiQuotesActive = (cmbQuoteStyle != null && cmbQuoteStyle.isDisabled() && currentQuoteStyleIndex == 2);
        boolean wasConverted = false;
        
        // Wenn ja, vor dem Speichern zurückkonvertieren zum ursprünglichen Format
        if (aiQuotesActive && originalQuoteStyleIndex >= 0 && originalQuoteStyleIndex != 2) {
            wasConverted = true;
            // Temporär zurückkonvertieren zum ursprünglichen Format
            convertAllQuotationMarksInText(QUOTE_STYLES[originalQuoteStyleIndex][0]);
        }
        
        try {
            String data = cleanTextForExport(codeArea.getText());
            if (data == null) data = "";
            
            // Spezielle Behandlung für Bücher
            if (isCompleteDocument) {
                // Für Bücher: Nur DOCX erstellen, kein MD speichern
                createDocxFile(file, data);
                // Nach dem Speichern wieder zu englisch konvertieren, wenn wir konvertiert haben
                if (wasConverted) {
                    convertAllQuotationMarksInText(QUOTE_STYLES[2][0]);
                }
                return;
            }
            
            // Normale Dateien: MD speichern
            // Nie löschen: leere Dateien bleiben bestehen
            Files.write(file.toPath(), data.getBytes(StandardCharsets.UTF_8));
            updateStatus("Datei gespeichert: " + file.getName());
            
            // WICHTIG: Markiere als gespeichert (setzt hasUnsavedChanges = false)
            markAsSaved();
            
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
        } finally {
            // Nach dem Speichern wieder zu englisch konvertieren, wenn wir konvertiert haben
            if (wasConverted) {
                convertAllQuotationMarksInText(QUOTE_STYLES[2][0]);
            }
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
                alert.showAndWait(stage);
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
        
        // Content setzen - verwende setCustomContent für CustomAlert
        alert.setCustomContent(content);
        
        ButtonType saveButton = new ButtonType("Speichern");
        ButtonType discardButton = new ButtonType("Verwerfen");
        ButtonType diffButton = new ButtonType("🔍 Diff anzeigen");
        ButtonType cancelButton = new ButtonType("Abbrechen");
        
        alert.setButtonTypes(saveButton, discardButton, diffButton, cancelButton);
        
        Optional<ButtonType> result = alert.showAndWait(stage);
        
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
                // Verwerfen und schließen - WICHTIG: hasUnsavedChanges zurücksetzen
                hasUnsavedChanges = false;
                stage.close();
            } else if (result.get() == diffButton) {
                // Diff anzeigen - verwende MainController Diff
                    if (mainController != null && originalDocxFile != null) {
                    File mdFile = deriveSidecarFileFor(originalDocxFile, outputFormat);
                    DocxFile docxFile = new DocxFile(originalDocxFile);
                    // WICHTIG: Übergib diesen Editor als aufrufenden Editor
                    mainController.showDetailedDiffDialog(docxFile, mdFile, null, outputFormat, this);
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
                alert.showAndWait(stage);
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
        
        // Content setzen - verwende setCustomContent für CustomAlert
        alert.setCustomContent(content);
        
        ButtonType saveButton = new ButtonType("Speichern & Weitermachen");
        ButtonType discardButton = new ButtonType("Verwerfen & Weitermachen");
        ButtonType diffButton = new ButtonType("🔍 Diff anzeigen");
        ButtonType cancelButton = new ButtonType("Abbrechen");
        
        alert.setButtonTypes(saveButton, discardButton, diffButton, cancelButton);
        
        Optional<ButtonType> result = alert.showAndWait(stage);
        
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
                // Verwerfen und Navigation fortsetzen - WICHTIG: hasUnsavedChanges zurücksetzen
                hasUnsavedChanges = false;
                return true; // Navigation fortsetzen
            } else if (result.get() == diffButton) {
                // Diff anzeigen - verwende MainController Diff
                if (mainController != null && originalDocxFile != null) {
                    File mdFile = deriveSidecarFileFor(originalDocxFile, outputFormat);
                    DocxFile docxFile = new DocxFile(originalDocxFile);
                    // WICHTIG: Übergib diesen Editor als aufrufenden Editor
                    mainController.showDetailedDiffDialog(docxFile, mdFile, null, outputFormat, this);
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
     * Normalisiert Satzzeichen: Entfernt Leerzeichen vor Satzzeichen und normalisiert mehrere Leerzeichen danach
     * Respektiert Code-Blöcke und andere spezielle Bereiche
     */
    private String normalizePunctuation(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        String[] lines = text.split("\n", -1);
        StringBuilder result = new StringBuilder();
        boolean inCodeBlock = false;
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmedLine = line.trim();
            
            // Prüfe auf Code-Blöcke
            if (trimmedLine.startsWith("```")) {
                inCodeBlock = !inCodeBlock;
                result.append(line);
                if (i < lines.length - 1) result.append("\n");
                continue;
            }
            
            // Normalisiere nur außerhalb von Code-Blöcken
            if (!inCodeBlock) {
                // Entferne Leerzeichen vor Satzzeichen: . ? ! ; : ,
                line = line.replaceAll("\\s+([.?!;:,])", "$1");
                
                // Normalisiere mehrere Leerzeichen nach Satzzeichen zu einem einzelnen Leerzeichen
                line = line.replaceAll("([.?!;:,])\\s{2,}", "$1 ");
            }
            
            result.append(line);
            if (i < lines.length - 1) result.append("\n");
        }
        
        return result.toString();
    }
    
    /**
     * Auto-Formatierung für Markdown: Konvertiert einfache Zeilenumbrüche zu doppelten,
     * aber respektiert Code-Blöcke, Tabellen und andere spezielle Bereiche
     */
    private String autoFormatMarkdown(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        // Normalisiere Satzzeichen zuerst
        text = normalizePunctuation(text);
        
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
                // Standard-Textladen über setText (ohne automatische Anführungszeichen-Konvertierung)
                setText(content);
                currentFile = file;
                // Cursor an den Anfang setzen
                codeArea.displaceCaret(0);
                codeArea.requestFollowCaret();
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
        setText(text, true); // Standard: Sequenz erhöhen
    }
    
    private void setText(String text, boolean incrementSequence) {
        // WICHTIG: Verhindere, dass null oder leerer Text den Editor leert
        if (text == null) {
            logger.warn("setText() aufgerufen mit null - wird ignoriert");
            return;
        }
        
        // WICHTIG: Verhindere, dass leerer Text den Editor leert (außer wenn explizit gewünscht)
        // Wenn text leer ist und codeArea bereits Inhalt hat, dann nicht leeren
        if (text.isEmpty() && codeArea != null && codeArea.getLength() > 0) {
            logger.debug("setText() aufgerufen mit leerem String, aber Editor hat bereits Inhalt - wird ignoriert");
            return;
        }
        
        // WICHTIG: Verhindere Race Conditions beim schnellen Blättern
        // Wenn incrementSequence=false, wird die Sequenz von loadChapterFile() verwaltet
        final long mySequence;
        if (incrementSequence) {
            mySequence = ++loadingSequence;
        } else {
            // Verwende aktuelle Sequenz (wird von loadChapterFile() verwaltet)
            // WICHTIG: Wenn loadingSequence 0 ist, bedeutet das, dass noch kein Kapitel geladen wurde
            // In diesem Fall sollten wir die Sequenz nicht prüfen, da es keine laufende Operation gibt
            mySequence = loadingSequence;
        }
        currentLoadingText = text;
        
        logger.debug("setText() aufgerufen: text.length()=" + text.length() + ", incrementSequence=" + incrementSequence + ", mySequence=" + mySequence + ", loadingSequence=" + loadingSequence);
        
        // Flag setzen, um zu verhindern, dass während des Ladens gespeichert wird
        isLoadingChapter = true;
        
        try {
            // Auto-Formatierung für Markdown anwenden
            String formattedText = autoFormatMarkdown(text);
            // WICHTIG: formattedText sollte nie null sein, aber zur Sicherheit prüfen
            final String finalFormattedText = (formattedText == null) ? "" : formattedText;
            boolean wasFormatted = !text.equals(finalFormattedText);
            
            // WICHTIG: Prüfe, ob diese Operation noch aktuell ist (nicht durch neueres Kapitel überschrieben)
            // Nur prüfen, wenn incrementSequence=false UND loadingSequence > 0 (es gibt eine laufende Operation)
            // Wenn incrementSequence=true, wurde die Sequenz gerade erhöht, also ist sie immer aktuell
            if (!incrementSequence && loadingSequence > 0 && mySequence != loadingSequence) {
                logger.debug("setText übersprungen - neueres Kapitel wird bereits geladen (Sequenz: " + mySequence + " vs " + loadingSequence + ")");
                isLoadingChapter = false;
                return;
            }
            
            // WICHTIG: Verwende replaceText mit Positionen, um Undo-Historie zu erhalten
            // replaceText(String) ohne Parameter kann die Undo-Historie löschen
            int currentLength = codeArea.getLength();
            if (currentLength > 0) {
                codeArea.replaceText(0, currentLength, finalFormattedText);
            } else {
                codeArea.replaceText(finalFormattedText);
            }
            
            // WICHTIG: Prüfe nochmal, ob diese Operation noch aktuell ist
            // Nur prüfen, wenn incrementSequence=false UND loadingSequence > 0 (es gibt eine laufende Operation)
            if (!incrementSequence && loadingSequence > 0 && mySequence != loadingSequence) {
                logger.debug("setText abgebrochen nach replaceText - neueres Kapitel wird bereits geladen");
                isLoadingChapter = false;
                return;
            }
            
            originalContent = cleanTextForComparison(codeArea.getText());
            
            markAsSaved();
            if (wasFormatted) {
                updateStatus("Text geladen (Automatische Formatierung angewendet)");
            } else {
                updateStatus("Text geladen");
            }
            
            // WICHTIG: Nach dem Laden die Undo-Historie zurücksetzen, damit der geladene Text
            // als Ausgangszustand gilt und nicht der leere Editor (verhindert leeren Editor bei Ctrl+Z)
            Platform.runLater(() -> {
                try {
                    if (codeArea.getUndoManager() != null) {
                        // Versuche die Undo-Historie zu leeren, damit der geladene Text der Ausgangszustand ist
                        java.lang.reflect.Method forgetHistory = codeArea.getUndoManager().getClass()
                            .getMethod("forgetHistory");
                        forgetHistory.invoke(codeArea.getUndoManager());
                    }
                } catch (Exception e) {
                    // Methode nicht verfügbar - versuche alternativen Ansatz
                    try {
                        // Alternativ: Versuche clear() oder reset()
                        java.lang.reflect.Method clear = codeArea.getUndoManager().getClass()
                            .getMethod("clear");
                        clear.invoke(codeArea.getUndoManager());
                    } catch (Exception e2) {
                        // Keine Methode gefunden - ignorieren
                        logger.debug("Konnte Undo-Historie nicht zurücksetzen: " + e2.getMessage());
                    }
                }
            });
            
            // Gespeicherte Cursor-Position wiederherstellen (falls vorhanden, sonst am Anfang)
            // Warte kurz, damit der Text vollständig geladen ist
            Platform.runLater(() -> {
                Platform.runLater(() -> {
                    // WICHTIG: Prüfe nochmal, ob diese Operation noch aktuell ist
                    // Nur prüfen, wenn incrementSequence=false UND loadingSequence > 0 (es gibt eine laufende Operation)
                    if (!incrementSequence && loadingSequence > 0 && mySequence != loadingSequence) {
                        logger.debug("setText abgebrochen vor restoreCursorPosition - neueres Kapitel wird bereits geladen");
                        isLoadingChapter = false;
                        return;
                    }
                    
                    restoreCursorPosition();
                    // Flag zurücksetzen nach dem Wiederherstellen
                    isLoadingChapter = false;
                    
                    // WICHTIG: Preview aktualisieren nach Kapitelwechsel (beide Modi)
                    // Prüfe nochmal, ob diese Operation noch aktuell ist
                    // Nur prüfen, wenn incrementSequence=false UND loadingSequence > 0
                    boolean isCurrent = incrementSequence || loadingSequence == 0 || mySequence == loadingSequence;
                    if (isCurrent && previewWebView != null && previewStage != null && previewStage.isShowing()) {
                        if (codeArea != null && codeArea.getLength() > 0) {
                            // Prüfe, ob der Text im Editor mit dem erwarteten Text übereinstimmt
                            String actualText = codeArea.getText();
                            if (actualText != null && actualText.length() > 0 && actualText.equals(finalFormattedText)) {
                        lastQuillContent = null;
                                updatePreviewContent();
                            } else {
                                logger.debug("Preview-Update übersprungen - Text stimmt nicht überein (Sequenz: " + mySequence + ")");
                            }
                        }
                    }
                });
            });
        } catch (Exception e) {
            // Bei Fehler Flag zurücksetzen
            isLoadingChapter = false;
            throw e;
        }
    }
    
    /**
     * Ersetzt den Text ohne originalContent zu ändern (für Diff-Änderungen)
     */
    public void replaceTextWithoutUpdatingOriginal(String text) {
        // Auto-Formatierung für Markdown anwenden (wie in setText)
        String formattedText = autoFormatMarkdown(text);
        boolean wasFormatted = !text.equals(formattedText);
        
        // WICHTIG: Verwende replaceText mit Positionen, um Undo-Historie zu erhalten
        int currentLength = codeArea.getLength();
        if (currentLength > 0) {
            codeArea.replaceText(0, currentLength, formattedText);
        } else {
            codeArea.replaceText(formattedText);
        }
        
        // Cursor an den Anfang setzen
        codeArea.displaceCaret(0);
        codeArea.requestFollowCaret();
        
        // WICHTIG: Markiere als geändert, damit "ungespeicherte Änderungen" angezeigt wird
        markAsChanged();
        
        // Überschreibe den Status mit der richtigen Farbe (nach markAsChanged)
        Platform.runLater(() -> {
            if (lblStatus != null) {
                if (wasFormatted) {
                    lblStatus.setText("⚠ Text wurde durch Diff geändert (Automatische Formatierung angewendet)");
                } else {
                    lblStatus.setText("⚠ Text wurde durch Diff geändert");
                }
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
            
            // Detaillierte Fehlermeldung in einem Alert-Dialog anzeigen
            Platform.runLater(() -> {
                CustomAlert alert = new CustomAlert(Alert.AlertType.ERROR, "DOCX-Export Fehler");
                alert.setHeaderText("Fehler beim Erstellen der DOCX-Datei");
                
                // Detaillierte Fehlermeldung zusammenstellen
                StringBuilder errorMessage = new StringBuilder();
                errorMessage.append("Die DOCX-Datei konnte nicht erstellt werden.\n\n");
                errorMessage.append("Zieldatei: ").append(docxFile.getAbsolutePath()).append("\n\n");
                errorMessage.append("Fehlertyp: ").append(e.getClass().getSimpleName()).append("\n");
                errorMessage.append("Fehlermeldung: ").append(e.getMessage()).append("\n\n");
                
                // Zusätzliche Informationen für häufige Fehler
                if (e.getMessage() != null) {
                    String msg = e.getMessage().toLowerCase();
                    if (msg.contains("permission") || msg.contains("zugriff") || msg.contains("access")) {
                        errorMessage.append("💡 Mögliche Ursache: Die Datei ist möglicherweise in Word oder einem anderen Programm geöffnet.\n");
                        errorMessage.append("   Bitte schließen Sie die Datei und versuchen Sie es erneut.\n\n");
                    } else if (msg.contains("disk") || msg.contains("space") || msg.contains("speicher")) {
                        errorMessage.append("💡 Mögliche Ursache: Nicht genügend Speicherplatz auf dem Datenträger.\n\n");
                    } else if (msg.contains("path") || msg.contains("pfad")) {
                        errorMessage.append("💡 Mögliche Ursache: Der Pfad ist ungültig oder das Verzeichnis existiert nicht.\n\n");
                    }
                }
                
                errorMessage.append("Für weitere Details siehe die Log-Datei.");
                
                alert.setContentText(errorMessage.toString());
                alert.applyTheme(currentThemeIndex);
                alert.initOwner(stage);
                alert.showAndWait();
            });
            
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
        File oldFile = this.originalDocxFile;
        this.originalDocxFile = docxFile;
        
        // WICHTIG: Aktualisiere den Fenstertitel, wenn sich die Datei ändert
        if (docxFile != null && (oldFile == null || !oldFile.equals(docxFile))) {
            setWindowTitle("📄 " + docxFile.getName());
        }
        
        // ListView-Zellen neu rendern, damit die Markierung aktualisiert wird
        refreshChapterListCells();
    }
    
    /**
     * Aktualisiert die ListView-Zellen, damit die Markierung korrekt angezeigt wird
     */
    private void refreshChapterListCells() {
        if (chapterListView != null) {
            Platform.runLater(() -> {
                // Zellen neu rendern lassen, indem wir die Items temporär entfernen und wieder hinzufügen
                ObservableList<DocxFile> items = chapterListView.getItems();
                if (!items.isEmpty()) {
                    // Temporär Items speichern
                    List<DocxFile> tempItems = new ArrayList<>(items);
                    // Items entfernen und wieder hinzufügen, um Zellen neu zu rendern
                    chapterListView.getItems().clear();
                    chapterListView.getItems().addAll(tempItems);
                    
                    // Aktuelle Auswahl wiederherstellen
                    if (originalDocxFile != null) {
                        for (int i = 0; i < tempItems.size(); i++) {
                            if (tempItems.get(i).getFile().equals(originalDocxFile)) {
                                chapterListView.getSelectionModel().select(i);
                                break;
                            }
                        }
                    }
                }
            });
        }
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
                if (cssPath != null) {
                    if (!stage.getScene().getStylesheets().contains(cssPath)) {
                        stage.getScene().getStylesheets().add(cssPath);
                    }
                    // CSS bereits geladen ist kein Fehler, keine Warnung
                } else {
                    logger.warn("CSS-Datei konnte in setStage nicht geladen werden: css/manuskript.css");
                }
            }
        });
            
        // WICHTIG: Fenster-Eigenschaften SOFORT laden (nicht in Platform.runLater)
        loadWindowProperties();
        
        // Close-Request-Handler für Speichern-Abfrage
        stage.setOnCloseRequest(event -> {
            // Speichere die Cursor-Position bevor das Fenster geschlossen wird
            saveCurrentCursorPosition();
            
            // Timer für Quill->Editor-Sync abbrechen
            if (quillToEditorSyncTimer != null) {
                quillToEditorSyncTimer.cancel();
                quillToEditorSyncTimer = null;
            }
            
            boolean hasChanges = hasUnsavedChanges();
            
            if (hasChanges) {
                event.consume(); // Verhindere Schließen
                showSaveDialog();
            } else {
                // Schließe alle abhängigen Fenster bevor der Editor geschlossen wird
                closeAllDependentWindows();
                
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
     * Schließt alle abhängigen Fenster (Preview, KI-Assistent, Makros, Textanalyse)
     */
    private void closeAllDependentWindows() {
        // Preview-Fenster schließen
        if (previewStage != null && previewStage.isShowing()) {
            savePreviewWindowProperties();
            previewStage.close();
        }
        
        // KI-Assistent (OllamaWindow) schließen
        if (ollamaWindow != null && ollamaWindow.isShowing()) {
            // Stelle die ursprünglichen Anführungszeichen wieder her
            restoreOriginalQuotes();
            ollamaWindow.hide();
            ollamaWindowVisible = false;
        }
        
        // Makro-Fenster schließen
        if (macroStage != null && macroStage.isShowing()) {
            macroStage.close();
            macroWindowVisible = false;
        }
        
        // Textanalyse-Fenster schließen
        if (textAnalysisStage != null && textAnalysisStage.isShowing()) {
            textAnalysisStage.close();
            textAnalysisWindowVisible = false;
        }
    }
    
    /**
     * Schließt das Editor-Fenster programmatisch
     */
    public void closeWindow() {
        // Schließe alle abhängigen Fenster bevor der Editor geschlossen wird
        closeAllDependentWindows();
        
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
        
        // Preview-Fenster Theme aktualisieren
        if (previewStage != null && previewStage.isShowing()) {
            previewStage.setFullTheme(themeIndex);
            previewStage.setTitleBarTheme(themeIndex);
            // Quill Editor Theme aktualisieren
            applyThemeToQuill(themeIndex);
            // Hintergrund-Farbe und Border aktualisieren
            String[] themeColors = {"#ffffff", "#1f2937", "#f3e5f5", "#0b1220", "#064e3b", "#581c87"};
            String[] borderColors = {"#cccccc", "#ffffff", "#d4a5d4", "#ffffff", "#ffffff", "#ffffff"};
            if (previewStage.getScene() != null && previewStage.getScene().getRoot() != null) {
                // Der Root ist der VBox mit Titelleiste, der originalRoot ist unser outerContainer
                Parent root = previewStage.getScene().getRoot();
                if (root instanceof VBox) {
                    VBox rootVBox = (VBox) root;
                    // Suche nach unserem outerContainer (zweites Child nach titleBar)
                    if (rootVBox.getChildren().size() > 1) {
                        Node contentNode = rootVBox.getChildren().get(1);
                        if (contentNode instanceof VBox) {
                            VBox outerContainer = (VBox) contentNode;
                            String borderStyle = "-fx-border-color: " + borderColors[themeIndex] + "; -fx-border-width: 2px; -fx-border-radius: 4px;";
                            outerContainer.setStyle("-fx-background-color: " + themeColors[themeIndex] + "; " + borderStyle);
                        }
                    }
                }
            }
            // Labels aktualisieren
            updatePreviewWindowLabels();
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
        
        // Listener für Änderungen in der selectedDocxFiles Liste hinzufügen
        if (mainController != null) {
            ObservableList<DocxFile> selectedDocxFiles = mainController.getSelectedDocxFilesAsDocxFiles();
            selectedDocxFiles.addListener((javafx.collections.ListChangeListener.Change<? extends DocxFile> change) -> {
                Platform.runLater(() -> {
                    updateChapterList();
                });
            });
        }
    }
    
    // ===== MAKRO-FUNKTIONALITÄT =====
    

    
    private void setupMacroPanel() {
        // Macro-Fenster erstellen
        createMacroWindow();
    }
    
    private void createMacroWindow() {
        macroStage = StageManager.createStage("Makros");
        macroStage.setTitle("Makro-Verwaltung");
        
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
        
        // WICHTIG: Fenster-Position NACH der Stage-Erstellung laden
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
            // Verwende die neue Multi-Monitor-Validierung
            Rectangle2D windowBounds = PreferencesManager.MultiMonitorValidator.loadAndValidateWindowProperties(
                preferences, "macro_window", 1200.0, 800.0);
            
            // Wende die validierten Eigenschaften an
            PreferencesManager.MultiMonitorValidator.applyWindowProperties(macroStage, windowBounds);
            
            
            // Fenster-Position und Größe speichern
            macroStage.xProperty().addListener((obs, oldVal, newVal) -> {
                preferences.putDouble("macro_window_x", newVal.doubleValue());
            });
            macroStage.yProperty().addListener((obs, oldVal, newVal) -> {
                preferences.putDouble("macro_window_y", newVal.doubleValue());
            });
            macroStage.widthProperty().addListener((obs, oldVal, newVal) -> {
                preferences.putDouble("macro_window_width", newVal.doubleValue());
            });
            macroStage.heightProperty().addListener((obs, oldVal, newVal) -> {
                preferences.putDouble("macro_window_height", newVal.doubleValue());
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
            
            // Multi-Monitor-Validierung wird bereits in loadMacroWindowProperties() durchgeführt
            
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
            // Alle Textanalyse-Markierungen zurücksetzen
            clearTextAnalysisMarkings();
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
        Label lblWortwiederholungNah = new Label("Findet Wortwiederholungen im Abstand zwischen 5 und 10 Wörtern (ohne Ignore-Liste)");
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
        
        // Satzlängen analysieren
        HBox satzlaengenBox = new HBox(10);
        Button btnSatzlaengen = new Button("Satzlängen analysieren");
        btnSatzlaengen.getStyleClass().add("button");
        btnSatzlaengen.setPrefWidth(200);
        btnSatzlaengen.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: %s;", accentColor, textColor));
        Label lblSatzlaengen = new Label("Analysiert Satzlängen und markiert Sätze nach Länge (kurz/mittel/lang)");
        lblSatzlaengen.setWrapText(true);
        lblSatzlaengen.setStyle(String.format("-fx-text-fill: %s;", textColor));
        HBox.setHgrow(lblSatzlaengen, Priority.ALWAYS);
        satzlaengenBox.getChildren().addAll(btnSatzlaengen, lblSatzlaengen);
        
        analysisButtons.getChildren().addAll(sprechwoerterBox, sprechantwortenBox, wortwiederholungenBox, wortwiederholungNahBox, fuellwoerterBox, phrasenBox, satzlaengenBox);
        
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
        btnSatzlaengen.setOnAction(e -> analyzeSatzlaengen(statusArea));
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
            int minAbstand = 5;
            int maxAbstand = 10;
            
            // Gehe durch alle Wörter
            for (int i = 0; i < words.size() - 1; i++) {
                String currentWord = words.get(i);
                
                // Suche nur im nächsten Abstand-Bereich (bis maxAbstand + 1)
                for (int j = i + 1; j <= Math.min(i + maxAbstand + 1, words.size() - 1); j++) {
                    String nextWord = words.get(j);
                    
                    // Wenn das gleiche Wort gefunden wurde
                    if (currentWord.equals(nextWord)) {
                        int distance = j - i - 1; // Anzahl Wörter zwischen den Wiederholungen
                        
                        // Nur wenn der Abstand zwischen 5 und 10 Wörtern liegt
                        if (distance >= minAbstand && distance <= maxAbstand) {
                            int charPos1 = wordPositions.get(i);
                            int charPos2 = wordPositions.get(j);
                            
                            // Validate positions
                            if (charPos1 >= 0 && charPos1 < content.length() && 
                                charPos2 >= 0 && charPos2 < content.length() &&
                                charPos1 != charPos2) {
                                // Die Wörter sind bereits validiert durch die wordPattern-Matching
                                wiederholungen.add(new Wortwiederholung(currentWord, charPos1, charPos2, distance));
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
            
            // Apply marking - Nur die spezifischen Positionen der gefundenen Paare markieren
            if (!wiederholungen.isEmpty()) {
                String content2 = codeArea.getText();
                
                // Sammle alle zu markierenden Wort-Start-Positionen direkt aus den Wiederholungen
                // pos1 und pos2 sind bereits die Start-Positionen der Wörter
                Set<Integer> positionsToMark = new HashSet<>();
                Map<Integer, Wortwiederholung> positionToWiederholung = new HashMap<>();
                
                for (Wortwiederholung w : wiederholungen) {
                    if (w.pos1 >= 0 && w.pos1 < content2.length()) {
                        positionsToMark.add(w.pos1);
                        positionToWiederholung.put(w.pos1, w);
                    }
                    if (w.pos2 >= 0 && w.pos2 < content2.length()) {
                        positionsToMark.add(w.pos2);
                        positionToWiederholung.put(w.pos2, w);
                    }
                }
                
                if (!positionsToMark.isEmpty()) {
                    // Erstelle StyleSpans für die Markierung
                    StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
                    int lastEnd = 0;
                    
                    // Gehe durch den gesamten Text und markiere nur die gefundenen Wörter
                    wordMatcher = wordPattern.matcher(content2);
                    while (wordMatcher.find()) {
                        int wordStart = wordMatcher.start();
                        int wordEnd = wordMatcher.end();
                        
                        if (positionsToMark.contains(wordStart)) {
                            // Dieses Wort soll markiert werden
                            Wortwiederholung w = positionToWiederholung.get(wordStart);
                            // Bestimme, ob es pos1 oder pos2 ist für die Farbzuweisung
                            boolean isFirst = (w.pos1 == wordStart);
                            String styleClass = isFirst ? "search-match-first" : "search-match-second";
                            
                            spansBuilder.add(Collections.emptyList(), wordStart - lastEnd);
                            spansBuilder.add(Collections.singleton(styleClass), wordEnd - wordStart);
                            lastEnd = wordEnd;
                        } else {
                            // Normales Wort, nicht markieren
                            spansBuilder.add(Collections.emptyList(), wordEnd - lastEnd);
                            lastEnd = wordEnd;
                        }
                    }
                    
                    spansBuilder.add(Collections.emptyList(), content2.length() - lastEnd);
                    
                    // Erstelle Pattern für Navigation (nur die markierten Wörter)
                    Set<String> markedWords = new HashSet<>();
                    for (Wortwiederholung w : wiederholungen) {
                        markedWords.add(w.word);
                    }
                    
                    StringBuilder patternBuilder = new StringBuilder();
                    for (String word : markedWords) {
                        if (patternBuilder.length() > 0) patternBuilder.append("|");
                        patternBuilder.append(Pattern.quote(word));
                    }
                    Pattern markPattern = Pattern.compile("\\b(" + patternBuilder.toString() + ")\\b", Pattern.CASE_INSENSITIVE);
                    
                    // Sammle nur die tatsächlich markierten Positionen für Navigation
                    List<Integer> navPositions = new ArrayList<>(positionsToMark);
                    Collections.sort(navPositions);
                    
                    // Apply styles on JavaFX Application Thread
                    Platform.runLater(() -> {
                        codeArea.setStyleSpans(0, spansBuilder.create());
                        
                        // Update cache for navigation - nur die markierten Positionen
                        cachedMatchPositions = new ArrayList<>(navPositions);
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
            // Verwende eine Map, um Start- und Endpositionen zu speichern (ohne Duplikate)
            Map<Integer, Integer> phraseRanges = new TreeMap<>(); // TreeMap sortiert automatisch
            
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
                            int start = matcher.start();
                            int end = matcher.end();
                            // Speichere nur wenn diese Position noch nicht existiert oder kürzer ist
                            if (!phraseRanges.containsKey(start) || (end - start) > (phraseRanges.get(start) - start)) {
                                phraseRanges.put(start, end);
                            }
                        }
                        
                        if (count > 0) {
                            phraseCount.put(trimmedPhrase, count);
                        }
                    }
                }
            }
            
            // Markierungen setzen - jetzt einfach durch die sortierte Map iterieren
            if (!phraseRanges.isEmpty()) {
                StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
                
                int currentPos = 0;
                for (Map.Entry<Integer, Integer> entry : phraseRanges.entrySet()) {
                    int start = entry.getKey();
                    int end = entry.getValue();
                    
                    // Nur markieren wenn nicht bereits überschrieben
                    if (start >= currentPos) {
                        spansBuilder.add(Collections.emptyList(), start - currentPos);
                        // Einheitliche Hervorhebung verwenden
                        spansBuilder.add(Collections.singleton("highlight-orange"), end - start);
                        currentPos = end;
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
            
            // Cache direkt aufbauen aus phraseRanges (schneller als Pattern-Matching)
            Pattern searchPattern = createSearchPattern(pattern);
            
            // Cache zurücksetzen
            totalMatches = phraseRanges.size();
            currentMatchIndex = -1;
            lastSearchText = pattern;
            cachedPattern = searchPattern;
            cachedMatchPositions.clear();
            
            // Positionen direkt aus phraseRanges übernehmen (bereits sortiert)
            cachedMatchPositions.addAll(phraseRanges.keySet());
            
            // Zum ersten Treffer springen und markieren
            if (!cachedMatchPositions.isEmpty() && !phraseRanges.isEmpty()) {
                int firstMatchStart = cachedMatchPositions.get(0);
                Integer firstMatchEnd = phraseRanges.get(firstMatchStart);
                if (firstMatchEnd != null) {
                    highlightText(firstMatchStart, firstMatchEnd);
                    currentMatchIndex = 0;
                    updateMatchCount(currentMatchIndex + 1, totalMatches);
                }
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
            if (totalMatches > 0) {
                updateStatus("Phrasen-Analyse abgeschlossen: " + totalCount + " Phrasen - Treffer " + (currentMatchIndex + 1) + " von " + totalMatches);
            } else {
                updateMatchCount(0, totalMatches);
                updateStatus("Phrasen-Analyse abgeschlossen: " + totalCount + " Phrasen");
            }
            
        } catch (Exception e) {
            statusArea.setText("Fehler bei der Phrasen-Analyse: " + e.getMessage());
            logger.error("Fehler bei der Phrasen-Analyse", e);
        }
    }
    
    private void analyzeSatzlaengen(TextArea statusArea) {
        try {
            String text = codeArea.getText();
            
            if (text == null || text.trim().isEmpty()) {
                statusArea.setText("Kein Text zum Analysieren vorhanden.");
                return;
            }
            
            // Absätze erkennen: Markdown-Absätze sind durch Leerzeilen (doppelte Zeilenumbrüche) getrennt
            // Split bei doppelten Zeilenumbrüchen oder einzelnen Leerzeilen
            String[] paragraphs = text.split("\\n\\s*\\n", -1);
            
            List<ParagraphInfo> paragraphInfos = new ArrayList<>();
            int currentPos = 0;
            
            for (String paragraphText : paragraphs) {
                if (paragraphText.trim().isEmpty()) {
                    // Leerzeile - überspringe
                    currentPos += paragraphText.length() + 2; // +2 für die Zeilenumbrüche
                    continue;
                }
                
                // Finde Startposition des Absatzes im Originaltext
                int paragraphStart = text.indexOf(paragraphText, currentPos);
                if (paragraphStart == -1) {
                    paragraphStart = currentPos;
                }
                int paragraphEnd = paragraphStart + paragraphText.length();
                
                // Analysiere Sätze innerhalb des Absatzes
                Pattern sentencePattern = Pattern.compile("([^.!?]+[.!?])(?=\\s+|$)", Pattern.MULTILINE);
                Matcher sentenceMatcher = sentencePattern.matcher(paragraphText);
                
                List<Integer> sentenceWordCounts = new ArrayList<>();
                int totalWords = 0;
                int maxSentenceLength = 0;
                int longSentences = 0; // > 25 Wörter
                int veryLongSentences = 0; // > 30 Wörter
                
                while (sentenceMatcher.find()) {
                    String sentenceText = sentenceMatcher.group(1).trim();
                    if (sentenceText.isEmpty()) {
                        continue;
                    }
                    
                    // Zähle Wörter im Satz
                    String[] words = sentenceText.split("\\s+");
                    int wordCount = 0;
                    for (String word : words) {
                        if (!word.trim().isEmpty()) {
                            wordCount++;
                        }
                    }
                    
                    if (wordCount > 0) {
                        sentenceWordCounts.add(wordCount);
                        totalWords += wordCount;
                        maxSentenceLength = Math.max(maxSentenceLength, wordCount);
                        
                        if (wordCount > 25) {
                            longSentences++;
                        }
                        if (wordCount > 30) {
                            veryLongSentences++;
                        }
                    }
                }
                
                // Wenn keine Sätze gefunden wurden, aber Text vorhanden ist
                if (sentenceWordCounts.isEmpty() && !paragraphText.trim().isEmpty()) {
                    String[] words = paragraphText.trim().split("\\s+");
                    int wordCount = 0;
                    for (String word : words) {
                        if (!word.trim().isEmpty()) {
                            wordCount++;
                        }
                    }
                    if (wordCount > 0) {
                        sentenceWordCounts.add(wordCount);
                        totalWords = wordCount;
                        maxSentenceLength = wordCount;
                        if (wordCount > 25) {
                            longSentences = 1;
                        }
                        if (wordCount > 30) {
                            veryLongSentences = 1;
                        }
                    }
                }
                
                // Kategorisiere Absatz basierend auf längstem Satz oder durchschnittlicher Satzlänge
                String category;
                if (sentenceWordCounts.isEmpty()) {
                    category = null; // Überspringe leere Absätze
                } else {
                    double avgLength = totalWords / (double) sentenceWordCounts.size();
                    
                    // Kategorisiere basierend auf längstem Satz (wichtiger für visuelle Markierung)
                    if (maxSentenceLength > 30) {
                        category = "sentence-long"; // Sehr lange Sätze
                    } else if (maxSentenceLength > 25) {
                        category = "sentence-long"; // Lange Sätze
                    } else if (avgLength > 20 || maxSentenceLength > 20) {
                        category = "sentence-medium"; // Mittlere Sätze
                    } else {
                        category = "sentence-short"; // Kurze Sätze
                    }
                }
                
                if (category != null) {
                    double avgLength = sentenceWordCounts.isEmpty() ? 0 : (totalWords / (double) sentenceWordCounts.size());
                    paragraphInfos.add(new ParagraphInfo(paragraphStart, paragraphEnd, 
                        sentenceWordCounts.size(), maxSentenceLength, 
                        (int)Math.round(avgLength), longSentences, veryLongSentences, category));
                }
                
                currentPos = paragraphEnd;
            }
            
            // Markiere Absätze im Editor
            if (!paragraphInfos.isEmpty()) {
                StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
                int currentPosMark = 0;
                
                for (ParagraphInfo paragraph : paragraphInfos) {
                    // Sicherstelle, dass keine Überlappungen auftreten
                    if (paragraph.start < currentPosMark) {
                        continue;
                    }
                    
                    // Füge normalen Text vor dem Absatz hinzu
                    if (paragraph.start > currentPosMark) {
                        spansBuilder.add(Collections.emptyList(), paragraph.start - currentPosMark);
                    }
                    
                    // Füge markierten Absatz hinzu
                    spansBuilder.add(Collections.singleton(paragraph.category), paragraph.end - paragraph.start);
                    currentPosMark = paragraph.end;
                }
                
                // Füge restlichen Text hinzu
                if (currentPosMark < text.length()) {
                    spansBuilder.add(Collections.emptyList(), text.length() - currentPosMark);
                }
                
                // Wende die Markierungen an
                StyleSpans<Collection<String>> spans = spansBuilder.create();
                codeArea.setStyleSpans(0, spans);
            } else {
                // Entferne alle Markierungen
                codeArea.setStyleSpans(0, text.length(), StyleSpans.singleton(new ArrayList<>(), 0));
            }
            
            // Bewertung erstellen
            StringBuilder result = new StringBuilder();
            result.append("=== SATZLÄNGEN-ANALYSE (NACH ABSÄTZEN) ===\n\n");
            
            int totalParagraphs = paragraphInfos.size();
            int totalSentences = 0;
            int shortParagraphs = 0;
            int mediumParagraphs = 0;
            int longParagraphs = 0;
            int totalVeryLongSentences = 0;
            int totalLongSentences = 0;
            
            for (ParagraphInfo para : paragraphInfos) {
                totalSentences += para.sentenceCount;
                totalVeryLongSentences += para.veryLongSentences;
                totalLongSentences += para.longSentences;
                
                if (para.category.equals("sentence-short")) {
                    shortParagraphs++;
                } else if (para.category.equals("sentence-medium")) {
                    mediumParagraphs++;
                } else {
                    longParagraphs++;
                }
            }
            
            result.append(String.format("Gesamtanzahl Absätze: %d\n", totalParagraphs));
            result.append(String.format("Gesamtanzahl Sätze: %d\n", totalSentences));
            result.append(String.format("Absätze mit kurzen Sätzen: %d (%.1f%%)\n", shortParagraphs,
                totalParagraphs > 0 ? (shortParagraphs * 100.0 / totalParagraphs) : 0));
            result.append(String.format("Absätze mit mittleren Sätzen: %d (%.1f%%)\n", mediumParagraphs,
                totalParagraphs > 0 ? (mediumParagraphs * 100.0 / totalParagraphs) : 0));
            result.append(String.format("Absätze mit langen Sätzen: %d (%.1f%%)\n", longParagraphs,
                totalParagraphs > 0 ? (longParagraphs * 100.0 / totalParagraphs) : 0));
            result.append(String.format("Sehr lange Sätze (>30 Wörter): %d\n", totalVeryLongSentences));
            result.append(String.format("Lange Sätze (>25 Wörter): %d\n\n", totalLongSentences));
            
            // Bewertung: Zu lange Sätze
            if (totalVeryLongSentences > 0) {
                result.append(String.format("⚠ %d überlange Sätze gefunden (>30 Wörter).\n", totalVeryLongSentences));
            }
            if (totalLongSentences > 0 && totalSentences > 0) {
                double longPercentage = (totalLongSentences * 100.0 / totalSentences);
                if (longPercentage > 20) {
                    result.append(String.format("⚠ %.1f%% der Sätze sind lang (>25 Wörter) - erwäge Kürzungen oder Aufteilungen.\n", longPercentage));
                }
            }
            
            // Bewertung: Wechsel zwischen Absätzen mit unterschiedlichen Satzlängen
            if (totalParagraphs > 1) {
                int goodTransitions = 0;
                int badTransitions = 0;
                
                for (int i = 0; i < paragraphInfos.size() - 1; i++) {
                    ParagraphInfo current = paragraphInfos.get(i);
                    ParagraphInfo next = paragraphInfos.get(i + 1);
                    
                    boolean currentIsLong = current.category.equals("sentence-long");
                    boolean nextIsLong = next.category.equals("sentence-long");
                    boolean currentIsShort = current.category.equals("sentence-short");
                    boolean nextIsShort = next.category.equals("sentence-short");
                    
                    // Guter Wechsel: lang → kurz oder kurz → lang
                    if ((currentIsLong && nextIsShort) || (currentIsShort && nextIsLong)) {
                        goodTransitions++;
                    }
                    // Schlechter Wechsel: zwei Absätze mit langen Sätzen hintereinander
                    else if (currentIsLong && nextIsLong) {
                        badTransitions++;
                    }
                }
                
                double transitionRatio = totalParagraphs > 1 ? (goodTransitions * 100.0 / (totalParagraphs - 1)) : 0;
                
                if (badTransitions > 0) {
                    result.append(String.format("⚠ %d mal folgen Absätze mit langen Sätzen direkt aufeinander - rhythmischer Wechsel ist mangelhaft.\n", badTransitions));
                }
                
                if (transitionRatio < 30 && totalParagraphs > 3) {
                    result.append("⚠ Geringer Wechsel zwischen Absätzen mit kurzen und langen Sätzen - Rhythmus könnte verbessert werden.\n");
                } else if (transitionRatio >= 30) {
                    result.append("✓ Guter Wechsel zwischen Absätzen mit kurzen und langen Sätzen vorhanden.\n");
                }
            }
            
            result.append("\n");
            result.append("💡 Markierungen im Text:\n");
            result.append("  Grün = Absätze mit kurzen Sätzen (≤20 Wörter durchschnittlich)\n");
            result.append("  Gelb = Absätze mit mittleren Sätzen (21-25 Wörter durchschnittlich)\n");
            result.append("  Rot = Absätze mit langen Sätzen (>25 Wörter)\n");
            
            statusArea.setText(result.toString());
            updateStatus("Satzlängen-Analyse abgeschlossen: " + totalSentences + " Sätze analysiert");
            
        } catch (Exception e) {
            statusArea.setText("Fehler bei der Satzlängen-Analyse: " + e.getMessage());
            logger.error("Fehler bei der Satzlängen-Analyse", e);
        }
    }
    
    // Hilfsklasse für Absatz-Informationen
    private static class ParagraphInfo {
        int start;
        int end;
        int sentenceCount;
        int maxSentenceLength;
        int avgSentenceLength;
        int longSentences;
        int veryLongSentences;
        String category;
        
        ParagraphInfo(int start, int end, int sentenceCount, int maxSentenceLength, 
                      int avgSentenceLength, int longSentences, int veryLongSentences, String category) {
            this.start = start;
            this.end = end;
            this.sentenceCount = sentenceCount;
            this.maxSentenceLength = maxSentenceLength;
            this.avgSentenceLength = avgSentenceLength;
            this.longSentences = longSentences;
            this.veryLongSentences = veryLongSentences;
            this.category = category;
        }
    }
    
    private void loadTextAnalysisWindowProperties() {
        // Verwende die neue Multi-Monitor-Validierung
        Rectangle2D windowBounds = PreferencesManager.MultiMonitorValidator.loadAndValidateWindowProperties(
            preferences, "textanalysis_window", 800.0, 600.0);
        
        // Wende die validierten Eigenschaften an
        PreferencesManager.MultiMonitorValidator.applyWindowProperties(textAnalysisStage, windowBounds);
        
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
    
    /**
     * Lädt die Preview-Fenster-Eigenschaften (Position und Größe)
     */
    private void loadPreviewWindowProperties() {
        if (previewStage == null || preferences == null) {
            logger.warn("Konnte Preview-Fenster-Eigenschaften nicht laden: previewStage oder preferences ist null");
            return;
        }
        
        // Lade Eigenschaften immer (auch wenn bereits gesetzt, um sicherzustellen, dass gespeicherte Werte verwendet werden)
        // Prüfe nur, ob es bereits gespeicherte Werte gibt
        try {
            double savedX = preferences.getDouble("preview_window_x", -1);
            double savedY = preferences.getDouble("preview_window_y", -1);
            double savedWidth = preferences.getDouble("preview_window_width", -1);
            double savedHeight = preferences.getDouble("preview_window_height", -1);
            
            // Wenn gespeicherte Werte vorhanden sind, verwende sie
            // WICHTIG: Prüfe auch, ob Breite/Höhe gespeichert sind (können auch ohne Position sein)
            if (savedX >= 0 && savedY >= 0) {
                // Position ist gespeichert
                if (savedWidth > 0 && savedHeight > 0) {
                    // Auch Größe ist gespeichert
                    logger.info("Lade Preview-Fenster-Eigenschaften aus Preferences: x={}, y={}, width={}, height={}", 
                               savedX, savedY, savedWidth, savedHeight);
                } else {
                    // Nur Position ist gespeichert, verwende Standard-Größe
                    logger.info("Lade Preview-Fenster-Position aus Preferences: x={}, y={}, verwende Standard-Größe", 
                               savedX, savedY);
                }
                
                // Verwende die neue Multi-Monitor-Validierung
                Rectangle2D windowBounds = PreferencesManager.MultiMonitorValidator.loadAndValidateWindowProperties(
                    preferences, "preview_window", 1000.0, 800.0);
                
                // Wende die validierten Eigenschaften an
                PreferencesManager.MultiMonitorValidator.applyWindowProperties(previewStage, windowBounds);
                
                logger.info("Preview-Fenster-Eigenschaften angewendet: x={}, y={}, width={}, height={}", 
                           windowBounds.getMinX(), windowBounds.getMinY(), windowBounds.getWidth(), windowBounds.getHeight());
            } else if (savedWidth > 0 && savedHeight > 0) {
                // Nur Größe ist gespeichert, Position nicht
                logger.info("Lade Preview-Fenster-Größe aus Preferences: width={}, height={}", savedWidth, savedHeight);
                previewStage.setWidth(savedWidth);
                previewStage.setHeight(savedHeight);
            } else {
                logger.info("Keine gespeicherten Preview-Fenster-Eigenschaften gefunden, verwende Standardwerte");
            }
        } catch (Exception e) {
            logger.error("Fehler beim Laden der Preview-Fenster-Eigenschaften: " + e.getMessage(), e);
        }
        
        // Event-Handler für Fenster-Änderungen (automatisches Speichern) - nur einmal hinzufügen
        if (!previewWindowListenersAdded) {
            previewWindowListenersAdded = true;
            
            // Flag um zu verhindern, dass beim Laden der Eigenschaften gespeichert wird
            final boolean[] isLoadingProperties = {true};
            
            // Setze Flag nach kurzer Verzögerung zurück, damit normale Änderungen gespeichert werden
            Platform.runLater(() -> {
                Platform.runLater(() -> {
                    isLoadingProperties[0] = false;
                    logger.debug("Preview-Window-Listener aktiviert - Änderungen werden jetzt gespeichert");
                });
            });
            
            previewStage.xProperty().addListener((obs, oldVal, newVal) -> {
                if (isLoadingProperties[0]) {
                    logger.debug("Ignoriere X-Änderung während des Ladens: {}", newVal);
                    return;
                }
                if (newVal != null && !newVal.equals(oldVal)) {
                    // WICHTIG: Kein flush() hier - wie bei Schriftart und PandocExportWindow
                    // Preferences werden automatisch gespeichert, flush() nur beim expliziten Speichern
                    preferences.putDouble("preview_window_x", newVal.doubleValue());
                    logger.debug("Preview-Fenster-X-Position gespeichert: {}", newVal);
                }
            });
            
            previewStage.yProperty().addListener((obs, oldVal, newVal) -> {
                if (isLoadingProperties[0]) {
                    logger.debug("Ignoriere Y-Änderung während des Ladens: {}", newVal);
                    return;
                }
                if (newVal != null && !newVal.equals(oldVal)) {
                    preferences.putDouble("preview_window_y", newVal.doubleValue());
                    logger.debug("Preview-Fenster-Y-Position gespeichert: {}", newVal);
                }
            });
            
            previewStage.widthProperty().addListener((obs, oldVal, newVal) -> {
                if (isLoadingProperties[0]) {
                    logger.debug("Ignoriere Width-Änderung während des Ladens: {}", newVal);
                    return;
                }
                if (newVal != null && !newVal.equals(oldVal) && newVal.doubleValue() >= 600) {
                    preferences.putDouble("preview_window_width", newVal.doubleValue());
                    logger.debug("Preview-Fenster-Breite gespeichert: {}", newVal);
                }
            });
            
            previewStage.heightProperty().addListener((obs, oldVal, newVal) -> {
                if (isLoadingProperties[0]) {
                    logger.debug("Ignoriere Height-Änderung während des Ladens: {}", newVal);
                    return;
                }
                if (newVal != null && !newVal.equals(oldVal) && newVal.doubleValue() >= 400) {
                    preferences.putDouble("preview_window_height", newVal.doubleValue());
                    logger.debug("Preview-Fenster-Höhe gespeichert: {}", newVal);
                }
            });
        } // Ende der Listener-Registrierung
    }
    
    /**
     * Speichert die Preview-Fenster-Eigenschaften (Position und Größe)
     */
    private void savePreviewWindowProperties() {
        if (previewStage == null || preferences == null) {
            logger.warn("Konnte Preview-Fenster-Eigenschaften nicht speichern: previewStage oder preferences ist null");
            return;
        }
        
        // WICHTIG: Speichere auch wenn Fenster versteckt ist (beim Verstecken/Beenden)
        // Die Prüfung auf isShowing() würde verhindern, dass beim Verstecken gespeichert wird
        
        try {
            double x = previewStage.getX();
            double y = previewStage.getY();
            double width = previewStage.getWidth();
            double height = previewStage.getHeight();
            
            // Validiere Werte bevor Speichern
            if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(width) || Double.isNaN(height) ||
                Double.isInfinite(x) || Double.isInfinite(y) || Double.isInfinite(width) || Double.isInfinite(height)) {
                logger.warn("Ungültige Preview-Fenster-Eigenschaften - speichere nicht: x={}, y={}, width={}, height={}", x, y, width, height);
                return;
            }
            
            // Prüfe ob Werte gültig sind (nicht 0 oder negativ für Größe)
            if (width <= 0 || height <= 0) {
                logger.warn("Ungültige Preview-Fenster-Größe - speichere nicht: width={}, height={}", width, height);
                return;
            }
            
            // Speichere Werte - verwende die gleiche Methode wie bei Schriftart (ohne flush in jedem Schritt)
            preferences.putDouble("preview_window_x", x);
            preferences.putDouble("preview_window_y", y);
            preferences.putDouble("preview_window_width", width);
            preferences.putDouble("preview_window_height", height);
            
            // WICHTIG: Flush explizit aufrufen, um sicherzustellen, dass die Werte gespeichert werden
            // (Schriftart verwendet kein flush, aber für Fenster-Eigenschaften ist es wichtig)
            try {
                preferences.flush();
                logger.info("Preview-Fenster-Eigenschaften gespeichert: x={}, y={}, width={}, height={}", x, y, width, height);
                
                // Verifiziere, dass die Werte tatsächlich gespeichert wurden
                double savedX = preferences.getDouble("preview_window_x", -1);
                double savedY = preferences.getDouble("preview_window_y", -1);
                double savedWidth = preferences.getDouble("preview_window_width", -1);
                double savedHeight = preferences.getDouble("preview_window_height", -1);
                logger.info("Verifizierung - Gespeicherte Werte: x={}, y={}, width={}, height={}", savedX, savedY, savedWidth, savedHeight);
            } catch (Exception flushException) {
                logger.error("Fehler beim Flush der Preview-Fenster-Eigenschaften: " + flushException.getMessage(), flushException);
            }
        } catch (Exception e) {
            logger.error("Fehler beim Speichern der Preview-Fenster-Eigenschaften: " + e.getMessage(), e);
        }
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
            
            Optional<ButtonType> result = alert.showAndWait(stage);
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
            
            // Cursor-Position speichern
            int caretPosition = codeArea.getCaretPosition();
            
            // Scroll-Position speichern (Paragraph des Cursors)
            int currentParagraph = codeArea.getCurrentParagraph();
            
            // Speichere den ursprünglichen Text für Undo (inkl. Absatz-Markierungen)
            String originalText = codeArea.getText();
            
            // Entferne Absatz-Markierungen direkt im String (nicht als separate Undo-Operation)
            String content = originalText.replace("¶", "");
            
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
            // WICHTIG: Wir ersetzen den gesamten Text in einer einzigen Operation
            // Dies stellt sicher, dass die gesamte Makro-Operation als eine einzige Undo-Operation behandelt wird
            if (!originalText.equals(normalizedContent)) {
                // Ersetze den gesamten Text in einer Operation - dies wird als eine einzige Undo-Operation behandelt
                codeArea.replaceText(0, codeArea.getLength(), normalizedContent);
                
                // WICHTIG: Stelle Cursor-Position SOFORT wieder her (vor Scroll-Position)
                // replaceText() setzt den Cursor möglicherweise zurück, daher müssen wir ihn sofort wiederherstellen
                int restoredCaretPosition = caretPosition;
                if (restoredCaretPosition > normalizedContent.length()) {
                    restoredCaretPosition = normalizedContent.length();
                }
                codeArea.moveTo(restoredCaretPosition);
                
                // WICHTIG: Stelle Scroll-Position SOFORT wieder her (synchron, nicht in Platform.runLater)
                // replaceText() setzt die Scroll-Position zurück, daher müssen wir sie danach sofort wiederherstellen
                try {
                    if (currentParagraph >= 0 && currentParagraph < codeArea.getParagraphs().size()) {
                        // Verwende showParagraphInViewport für sanfte Wiederherstellung
                        codeArea.showParagraphInViewport(currentParagraph);
                    }
                } catch (Exception e) {
                    logger.debug("Fehler beim Wiederherstellen der Scroll-Position nach replaceText: " + e.getMessage());
                }
                
                // Verhindere, dass die nächste Operation mit dieser zusammengeführt wird
                // Dies stellt sicher, dass die Makro-Operation als separate Undo-Operation bleibt
                if (codeArea.getUndoManager() != null) {
                    try {
                        // Versuche preventMergeNext() falls verfügbar, sonst ignorieren
                        java.lang.reflect.Method preventMerge = codeArea.getUndoManager().getClass()
                            .getMethod("preventMergeNext");
                        preventMerge.invoke(codeArea.getUndoManager());
                    } catch (Exception e) {
                        // Methode nicht verfügbar - ignorieren
                    }
                }
            } else {
                // Keine Textänderung - Cursor-Position trotzdem sicherstellen
                int restoredCaretPosition = caretPosition;
                if (restoredCaretPosition > normalizedContent.length()) {
                    restoredCaretPosition = normalizedContent.length();
                }
                codeArea.moveTo(restoredCaretPosition);
            }
            
            // Prüfe ob das Makro tatsächlich Änderungen vorgenommen hat
            // Vergleiche den ursprünglichen Inhalt mit dem normalisierten Inhalt
            if (!content.equals(normalizedContent)) {
                // Nur markieren als geändert, wenn tatsächlich Änderungen vorgenommen wurden
                markAsChanged();
                logger.debug("Makro hat Änderungen vorgenommen - markiere als geändert");
            } else {
                logger.debug("Makro hat keine Änderungen vorgenommen - nicht als geändert markieren");
            }
            
            // WICHTIG: Scroll-Position NICHT ändern - nur Fokus wiederherstellen
            // Cursor-Position wurde bereits oben wiederhergestellt
            Platform.runLater(() -> {
                // Fokus wiederherstellen
                codeArea.requestFocus();
                stage.requestFocus();
                codeArea.requestFocus();
                
                // WICHTIG: KEIN automatisches Scrollen zum Cursor, um die Scroll-Position zu erhalten
                // KEIN requestFollowCaret(), da dies den Cursor automatisch verfolgt und die Position ändert
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
            String originalLine = line;
            // Entferne nur \r am Ende (Windows-Zeilenumbrüche), aber behalte andere Leerzeichen
            if (originalLine.endsWith("\r")) {
                originalLine = originalLine.substring(0, originalLine.length() - 1);
            }
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
            } else if (originalLine.startsWith("REPLACE:")) {
                // Neues lesbares Format - originalLine verwenden, um Leerzeichen zu erhalten
                if (currentStep != null) {
                    String replaceText = originalLine.substring(8);
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
            String originalLine = line;
            // Entferne nur \r am Ende (Windows-Zeilenumbrüche), aber behalte andere Leerzeichen
            if (originalLine.endsWith("\r")) {
                originalLine = originalLine.substring(0, originalLine.length() - 1);
            }
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
            } else if (originalLine.startsWith("REPLACE:")) {
                // originalLine verwenden, um Leerzeichen zu erhalten
                if (currentStep != null) {
                    String replaceText = originalLine.substring(8);
                    currentStep.setReplaceText(replaceText);
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
            // WICHTIG: Schriftgröße IMMER direkt aus Preferences lesen (werden sofort von applyFontSize aktualisiert)
            // Dies verhindert Race Conditions bei schnellen Scrollevents (Mausrad)
            // Die Preferences werden in applyFontSize sofort gespeichert, sodass der nächste Event den neuen Wert liest
            int currentSize = preferences.getInt("fontSize", 12);
            
            int newSize = Math.max(8, Math.min(72, currentSize + delta));
            
            // Font-Size DIREKT anwenden mit expliziter Größe (keine ComboBox-Abhängigkeit)
            // applyFontSize speichert sofort in Preferences, sodass nächster Event den neuen Wert liest
            applyFontSize(newSize);
            
            // ComboBox NACH dem Anwenden aktualisieren (nur für UI-Darstellung)
            // Event-Handler temporär entfernen, damit setValue kein Event auslöst
            EventHandler<ActionEvent> originalHandler = cmbFontSize.getOnAction();
            cmbFontSize.setOnAction(null);
            cmbFontSize.setValue(String.valueOf(newSize));
            cmbFontSize.setOnAction(originalHandler);
            
        } catch (Exception e) {
            logger.warn("Fehler beim Ändern der Schriftgröße: {}", e.getMessage());
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
            // Theme neu anwenden mit expliziter Schriftgröße (verhindert Race Conditions beim Lesen aus ComboBox)
            applyTheme(currentThemeIndex, size);
            
            // Speichere in Preferences
            preferences.putInt("fontSize", size);
        }
        
        // WICHTIG: Quill-Fontgröße NICHT ändern - Editor und Quill haben unabhängige Fontgrößen!
        // Die Quill-Fontgröße wird nur über die Quill-Steuerelemente (A+/A-/Spinner) geändert.
    }
    
    /**
     * Setzt die globale Schriftgröße für Quill Editor
     */
    private void applyQuillGlobalFontSize(int fontSize) {
        if (previewWebView == null) return;
        
        // Speichere in Preferences
        preferences.putInt("quillFontSize", fontSize);
        
        javafx.scene.web.WebEngine engine = previewWebView.getEngine();
        if (engine.getLoadWorker().getState() == javafx.concurrent.Worker.State.SUCCEEDED) {
            Platform.runLater(() -> {
                String script = String.format("if (window.setQuillGlobalFontSize) { window.setQuillGlobalFontSize(%d); }", fontSize);
                engine.executeScript(script);
            });
        }
    }
    
    /**
     * Setzt die globale Schriftart für Quill Editor
     */
    private void applyQuillGlobalFontFamily(String fontFamily) {
        if (previewWebView == null) return;
        
        // Speichere in Preferences
        preferences.put("quillFontFamily", fontFamily);
        
        // Konvertiere zu CSS-kompatiblem Format
        final String cssFontFamily;
        if (fontFamily.equals("Consolas")) {
            cssFontFamily = "'Consolas', 'Monaco', monospace";
        } else if (fontFamily.equals("Times New Roman")) {
            cssFontFamily = "'Times New Roman', serif";
        } else if (fontFamily.equals("Courier New")) {
            cssFontFamily = "'Courier New', monospace";
        } else if (fontFamily.equals("Arial")) {
            cssFontFamily = "Arial, sans-serif";
        } else if (fontFamily.equals("Verdana")) {
            cssFontFamily = "Verdana, sans-serif";
        } else if (fontFamily.equals("Georgia")) {
            cssFontFamily = "Georgia, serif";
        } else {
            cssFontFamily = fontFamily;
        }
        
        javafx.scene.web.WebEngine engine = previewWebView.getEngine();
        if (engine.getLoadWorker().getState() == javafx.concurrent.Worker.State.SUCCEEDED) {
            Platform.runLater(() -> {
                // WICHTIG: Verwende setTimeout, damit Quill bereit ist und die Änderung nicht überschrieben wird
                String script = String.format(
                    "setTimeout(function() { " +
                    "  if (window.setQuillGlobalFontFamily) { " +
                    "    try { window.setQuillGlobalFontFamily(%s); } " +
                    "    catch(e) { console.error('Font family error:', e); } " +
                    "  } " +
                    "}, 50);", 
                    toJSString(cssFontFamily));
                engine.executeScript(script);
            });
        }
    }
    
    /**
     * Setzt die Schriftgröße für den normalen WebView-Modus
     */
    private void applyNormalViewFontSize(int fontSize) {
        if (previewWebView == null || codeArea == null || isTogglingViewMode) return;
        
        // Speichere in Preferences
        preferences.putInt("quillFontSize", fontSize);
        
        // Content neu laden mit neuer Font-Größe
        Platform.runLater(() -> {
            try {
                if (isTogglingViewMode) return; // Nochmal prüfen
                String markdownContent = codeArea.getText();
                String htmlContent = convertMarkdownToHTMLForPreview(markdownContent);
                previewWebView.getEngine().loadContent(htmlContent, "text/html");
            } catch (Exception e) {
                logger.error("Fehler beim Aktualisieren der Font-Größe im normalen Modus", e);
            }
        });
    }
    
    /**
     * Setzt die Schriftart für den normalen WebView-Modus
     */
    private void applyNormalViewFontFamily(String fontFamily) {
        if (previewWebView == null || codeArea == null || isTogglingViewMode) return;
        
        // Speichere in Preferences
        preferences.put("quillFontFamily", fontFamily);
        
        // Content neu laden mit neuer Font-Familie
        Platform.runLater(() -> {
            try {
                if (isTogglingViewMode) return; // Nochmal prüfen
                String markdownContent = codeArea.getText();
                String htmlContent = convertMarkdownToHTMLForPreview(markdownContent);
                previewWebView.getEngine().loadContent(htmlContent, "text/html");
            } catch (Exception e) {
                logger.error("Fehler beim Aktualisieren der Font-Familie im normalen Modus", e);
            }
        });
    }
    
    // ===== PERSISTIERUNG VON TOOLBAR UND FENSTER-EIGENSCHAFTEN =====
    
    private void loadWindowProperties() {
        if (stage == null) return;
        
        // Verwende die neue Multi-Monitor-Validierung
        Rectangle2D windowBounds = PreferencesManager.MultiMonitorValidator.loadAndValidateWindowProperties(
            preferences, "editor_window", PreferencesManager.DEFAULT_EDITOR_WIDTH, PreferencesManager.DEFAULT_EDITOR_HEIGHT);
        
        // Wende die validierten Eigenschaften an
        PreferencesManager.MultiMonitorValidator.applyWindowProperties(stage, windowBounds);

        // Mindestgrößen setzen, damit das Fenster nicht unter die Layout-Grenzen schrumpft
        stage.setMinWidth(1250);
        stage.setMinHeight(PreferencesManager.MIN_WINDOW_HEIGHT);
        
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
     * NEUE IMPLEMENTIERUNG: Von Grund auf neu
     */
    private void convertAllQuotationMarksInText(String selectedStyle) {
        if (codeArea == null) return;
        
        String currentText = codeArea.getText();
        if (currentText == null || currentText.isEmpty()) return;
        
        // Bestimme den Ziel-Stil basierend auf dem Dropdown
        int targetStyleIndex = -1;
        for (int i = 0; i < QUOTE_STYLES.length; i++) {
            if (QUOTE_STYLES[i][0].equals(selectedStyle)) {
                targetStyleIndex = i;
                break;
            }
        }
        
        if (targetStyleIndex == -1) return;
        
        logger.debug("Konvertiere Anführungszeichen zu Stil: " + selectedStyle + " (Index: " + targetStyleIndex + ")");
        logger.debug("Originaler Text: " + currentText.substring(0, Math.min(100, currentText.length())));
        
        // Konvertiere den Text - verwende QuotationMarkConverter statt eigene Implementierung
        String styleName = QUOTE_STYLES[targetStyleIndex][1]; // "deutsch", "französisch", etc.
        String convertedText = QuotationMarkConverter.convertQuotationMarks(currentText, styleName);
        
        logger.debug("Konvertierter Text: " + convertedText.substring(0, Math.min(100, convertedText.length())));
        logger.debug("Text geändert: " + !currentText.equals(convertedText));
        
        // Prüfe ob Änderungen vorgenommen wurden
        if (!currentText.equals(convertedText)) {
            // Speichere Cursor-Position
            int caretPosition = codeArea.getCaretPosition();
            
            // Ersetze den Text - VERBESSERTE METHODE
            codeArea.selectAll();
            codeArea.replaceSelection(convertedText);
            
            // Stelle Cursor-Position wieder her
            if (caretPosition <= convertedText.length()) {
                codeArea.moveTo(caretPosition);
            } else {
                codeArea.moveTo(convertedText.length());
            }
            
            // FORCE REFRESH
            codeArea.requestFocus();
            
            // Zeige Erfolgsmeldung
            updateStatus("✅ Anführungszeichen zu " + selectedStyle + " konvertiert");
            
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
     * Konvertiert typographische Anführungszeichen zu normalen " in HTML-Attributen
     */
    private void normalizeHtmlTagsInText() {
        // Verhindere rekursive Aufrufe, die die Undo-Historie löschen könnten
        if (isNormalizingHtmlTags) {
            return;
        }
        
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
            // Flag setzen, um rekursive Aufrufe zu verhindern
            isNormalizingHtmlTags = true;
            
            try {
                // Speichere Cursor-Position
                int caretPosition = codeArea.getCaretPosition();
                
                // Ersetze den Text - verwende replaceText mit Positionen, um Undo-Historie zu erhalten
                codeArea.replaceText(0, currentText.length(), normalizedText);
                
                // Stelle Cursor-Position wieder her
                if (caretPosition <= normalizedText.length()) {
                    codeArea.moveTo(caretPosition);
                } else {
                    codeArea.moveTo(normalizedText.length());
                }
            } finally {
                // Flag zurücksetzen
                isNormalizingHtmlTags = false;
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
    
    private void formatTextStrikethrough() {
        formatTextAtCursor("~~", "~~", "<s>", "</s>");
    }
    
    private void formatTextSuperscript() {
        formatTextAtCursor("<sup>", "</sup>", "<sup>", "</sup>");
    }
    
    private void formatTextSubscript() {
        formatTextAtCursor("<sub>", "</sub>", "<sub>", "</sub>");
    }
    
    private void insertLineBreak() {
        if (codeArea == null) return;
        int caretPosition = codeArea.getCaretPosition();
        // Füge <br> gefolgt von einem Zeilenumbruch ein
        String lineBreak = "<br>\n";
        codeArea.insertText(caretPosition, lineBreak);
        // Setze Cursor nach dem Zeilenumbruch
        codeArea.displaceCaret(caretPosition + lineBreak.length());
        codeArea.requestFocus();
    }
    
    private void insertHorizontalLine() {
        if (codeArea == null) return;
        int caretPosition = codeArea.getCaretPosition();
        String content = codeArea.getText();
        
        // Füge eine Leerzeile vor der horizontalen Linie ein, wenn nicht bereits vorhanden
        String lineBreak = "\n";
        if (caretPosition > 0 && content.charAt(caretPosition - 1) != '\n') {
            lineBreak = "\n";
        }
        
        String horizontalLine = lineBreak + "---" + "\n";
        codeArea.insertText(caretPosition, horizontalLine);
        codeArea.displaceCaret(caretPosition + horizontalLine.length());
        codeArea.requestFocus();
    }
    
    private void formatTextCenter() {
        // Für Markdown gibt es keine direkte Syntax, verwende HTML-Tags
        formatTextAtCursor("<c>", "</c>", "<center>", "</center>");
    }
    
    /**
     * Fügt HTML-Tags direkt ein, unabhängig vom Output-Format
     * (wird für Tags verwendet, die keine Markdown-Äquivalente haben)
     */
    private void insertHtmlTags(String htmlStart, String htmlEnd) {
        if (codeArea == null) return;
        
        String selectedText = getSelectedTextSafely();
        int caretPosition = codeArea.getCaretPosition();
        
        if (selectedText != null && !selectedText.isEmpty()) {
            // Text ist ausgewählt - prüfe ob bereits formatiert
            int start = codeArea.getSelection().getStart();
            int end = codeArea.getSelection().getEnd();
            
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
        } else {
            // Kein Text ausgewählt - finde und markiere das aktuelle Wort
            int[] wordBounds = findCurrentWordBounds(caretPosition);
            if (wordBounds != null) {
                int wordStart = wordBounds[0];
                int wordEnd = wordBounds[1];
                String wordText = codeArea.getText(wordStart, wordEnd);
                
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
            } else {
                // Kein Wort gefunden - füge Formatierung an der Cursor-Position ein
                String formatText = htmlStart + htmlEnd;
                codeArea.insertText(caretPosition, formatText);
                // Setze Cursor zwischen die Formatierung
                codeArea.displaceCaret(caretPosition + htmlStart.length());
            }
        }
        
        codeArea.requestFocus();
    }
    
    private void formatTextBig() {
        insertHtmlTags("<big>", "</big>");
    }
    
    private void formatTextSmall() {
        insertHtmlTags("<small>", "</small>");
    }
    
    private void formatTextMark() {
        // Für Markdown gibt es keine direkte Syntax, verwende HTML-Tags
        insertHtmlTags("<mark>", "</mark>");
    }
    
    // Farb-Formatierungsfunktionen
    @FXML
    private void formatTextColorRed() {
        insertHtmlTags("<red>", "</red>");
    }
    
    @FXML
    private void formatTextColorBlue() {
        insertHtmlTags("<blue>", "</blue>");
    }
    
    @FXML
    private void formatTextColorGreen() {
        insertHtmlTags("<green>", "</green>");
    }
    
    @FXML
    private void formatTextColorYellow() {
        insertHtmlTags("<yellow>", "</yellow>");
    }
    
    @FXML
    private void formatTextColorPurple() {
        insertHtmlTags("<purple>", "</purple>");
    }
    
    @FXML
    private void formatTextColorOrange() {
        insertHtmlTags("<orange>", "</orange>");
    }
    
    @FXML
    private void formatTextColorGray() {
        insertHtmlTags("<gray>", "</gray>");
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
        
        // Preview-Fenster Theme aktualisieren
        if (previewStage != null && previewStage.isShowing()) {
            previewStage.setFullTheme(currentThemeIndex);
            previewStage.setTitleBarTheme(currentThemeIndex);
            // Hintergrund-Farbe und Border aktualisieren
            String[] themeColors = {"#ffffff", "#1f2937", "#f3e5f5", "#0b1220", "#064e3b", "#581c87"};
            String[] borderColors = {"#cccccc", "#ffffff", "#d4a5d4", "#ffffff", "#ffffff", "#ffffff"};
            if (previewStage.getScene() != null && previewStage.getScene().getRoot() != null) {
                // Der Root ist der VBox mit Titelleiste, der originalRoot ist unser outerContainer
                Parent root = previewStage.getScene().getRoot();
                if (root instanceof VBox) {
                    VBox rootVBox = (VBox) root;
                    // Suche nach unserem outerContainer (zweites Child nach titleBar)
                    if (rootVBox.getChildren().size() > 1) {
                        Node contentNode = rootVBox.getChildren().get(1);
                        if (contentNode instanceof VBox) {
                            VBox outerContainer = (VBox) contentNode;
                            String borderStyle = "-fx-border-color: " + borderColors[currentThemeIndex] + "; -fx-border-width: 2px; -fx-border-radius: 4px;";
                            outerContainer.setStyle("-fx-background-color: " + themeColors[currentThemeIndex] + "; " + borderStyle);
                        }
                    }
                }
            }
            // Labels aktualisieren
            updatePreviewWindowLabels();
        }
        
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
        
        // Überladung mit expliziter Schriftgröße aufrufen
        applyTheme(themeIndex, fontSize);
    }
    
    private void applyTheme(int themeIndex, int fontSize) {
        if (codeArea == null) return;
        
        String[] theme = THEMES[themeIndex];
        String backgroundColor = theme[0];
        String textColor = theme[1];
        String selectionColor = theme[2];
        String caretColor = theme[3];
        
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
        
        // WICHTIG: Styles SOFORT setzen (ohne Platform.runLater) für bessere Responsivität bei schnellen Scrollevents
        // Dies verhindert das "Springen" beim Ändern der Schriftgröße mit dem Mausrad
        codeArea.setStyle("");
        codeArea.setStyle(cssStyle);
        
        // Zusätzlicher verzögerter Refresh nur für Theme-Wechsel (nicht für Schriftgrößenänderungen)
        // Bei schnellen Schriftgrößenänderungen könnte dies zu Race Conditions führen
        Platform.runLater(() -> {
            // Styles nochmal setzen für bessere Kompatibilität (nur wenn noch das gleiche Theme)
            if (codeArea != null) {
                codeArea.setStyle(cssStyle);
            }
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
            applyThemeToNode(btnUnderline, themeIndex);
            applyThemeToNode(btnStrikethrough, themeIndex);
            applyThemeToNode(btnSuperscript, themeIndex);
            applyThemeToNode(btnSubscript, themeIndex);
            if (btnInsertLineBreak != null) {
                applyThemeToNode(btnInsertLineBreak, themeIndex);
            }
            if (btnInsertHorizontalLine != null) {
                applyThemeToNode(btnInsertHorizontalLine, themeIndex);
            }
            if (btnCenter != null) {
                applyThemeToNode(btnCenter, themeIndex);
            }
            if (btnBig != null) {
                applyThemeToNode(btnBig, themeIndex);
            }
            if (btnSmall != null) {
                applyThemeToNode(btnSmall, themeIndex);
            }
            if (btnMark != null) {
                applyThemeToNode(btnMark, themeIndex);
            }
            // btnColorMenu wird NICHT mit applyThemeToNode behandelt (wird am Ende aufgerufen)
            applyThemeToNode(btnThemeToggle, themeIndex);
            // btnMacroRegexHelp wurde entfernt
            
            // Alle ComboBoxes
            applyThemeToNode(cmbSearchHistory, themeIndex);
            applyThemeToNode(cmbReplaceHistory, themeIndex);
            applyThemeToNode(cmbFontSize, themeIndex);
            
            // Kontextmenü aktualisieren
            if (codeArea != null && codeArea.getContextMenu() != null) {
                styleContextMenu(codeArea.getContextMenu(), themeIndex);
            }
        // cmbLineSpacing entfernt
        applyThemeToNode(cmbParagraphSpacing, themeIndex);
        applyThemeToNode(cmbQuoteStyle, themeIndex);
            
            // Zusätzliche Styles für ComboBoxes im Pastell-Theme
            if (themeIndex == 2) { // Pastell-Theme
                String[] pastellTheme = THEMES[themeIndex];
                String pastellTextColor = pastellTheme[1]; // #000000 für Pastell
                String pastellBackgroundColor = pastellTheme[2]; // #e1bee7 für Pastell
                
                String comboBoxStyle = String.format(
                    "-fx-background-color: %s !important; " +
                    "-fx-control-inner-background: %s !important; " +
                    "-fx-text-fill: %s !important;",
                    pastellBackgroundColor, pastellBackgroundColor, pastellTextColor
                );
                
                if (cmbSearchHistory != null) {
                    cmbSearchHistory.setStyle(comboBoxStyle);
                }
                if (cmbReplaceHistory != null) {
                    cmbReplaceHistory.setStyle(comboBoxStyle);
                }
                if (cmbFontSize != null) {
                    cmbFontSize.setStyle(comboBoxStyle);
                }
                if (cmbParagraphSpacing != null) {
                    cmbParagraphSpacing.setStyle(comboBoxStyle);
                }
                if (cmbQuoteStyle != null) {
                    cmbQuoteStyle.setStyle(comboBoxStyle);
                }
            }

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
        
        String selectedText = getSelectedTextSafely();
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
     * Gibt die CSS-Klasse für englische Farb-Tags zurück
     */
    private String getColorStyleClass(String colorTag) {
        switch (colorTag.toLowerCase()) {
            case "red": return "markdown-color-red";
            case "blue": return "markdown-color-blue";
            case "green": return "markdown-color-green";
            case "yellow": return "markdown-color-yellow";
            case "purple": return "markdown-color-purple";
            case "orange": return "markdown-color-orange";
            case "gray":
            case "grey": return "markdown-color-gray";
            default: return null;
        }
    }
    
    /**
     * Gibt die CSS-Klasse für deutsche Farb-Tags zurück
     */
    private String getColorStyleClassDe(String colorTag) {
        switch (colorTag.toLowerCase()) {
            case "rot": return "markdown-color-red";
            case "blau": return "markdown-color-blue";
            case "grün": return "markdown-color-green";
            case "gelb": return "markdown-color-yellow";
            case "lila": return "markdown-color-purple";
            case "grau": return "markdown-color-gray";
            default: return null;
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
        
        // WICHTIG: KEINE Viewport- oder Cursor-Position speichern oder wiederherstellen
        // Die Methode wendet nur Styles an, ohne den Viewport oder Cursor zu ändern
        
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
            
            // Markdown Strikethrough-Pattern: ~~text~~
            Pattern markdownStrikePattern = Pattern.compile("~~([\\s\\S]*?)~~", Pattern.DOTALL);
            Matcher markdownStrikeMatcher = markdownStrikePattern.matcher(content);
            
            while (markdownStrikeMatcher.find()) {
                int start = markdownStrikeMatcher.start(1); // Start der Gruppe 1 (der Text zwischen ~~)
                int end = markdownStrikeMatcher.end(1);     // Ende der Gruppe 1
                if (end > start) {
                    // Überprüfe, ob dieser Bereich bereits abgedeckt ist
                    boolean alreadyCovered = markdownMatches.stream().anyMatch(m -> 
                        (start >= m.start && start < m.end) || (end > m.start && end <= m.end) ||
                        (start <= m.start && end >= m.end));
                    if (!alreadyCovered) {
                        markdownMatches.add(new MarkdownMatch(start, end, "markdown-strikethrough"));
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
            
            // HTML Strike-through-Pattern: <s>text</s> oder <del>text</del>
            Pattern htmlStrikePattern = Pattern.compile("<(s|del)>([\\s\\S]*?)</(s|del)>", Pattern.DOTALL);
            Matcher htmlStrikeMatcher = htmlStrikePattern.matcher(content);
            
            while (htmlStrikeMatcher.find()) {
                int start = htmlStrikeMatcher.start(2); // Start der Gruppe 2 (der Text zwischen den Tags)
                int end = htmlStrikeMatcher.end(2);     // Ende der Gruppe 2
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
                // Verwende start(1) und end(1) für Gruppe 1 (Text zwischen den Tags)
                int start = htmlMarkMatcher.start(1); // Start des Textes zwischen den Tags
                int end = htmlMarkMatcher.end(1);     // Ende des Textes zwischen den Tags
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
                // Verwende start(2) und end(2) für Gruppe 2 (Text zwischen den Tags)
                int start = htmlSizeMatcher.start(2); // Start des Textes zwischen den Tags
                int end = htmlSizeMatcher.end(2);     // Ende des Textes zwischen den Tags
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
            
            // HTML Farb-Tags: <red>text</red>, <rot>text</rot>, etc.
            // Englische Tags
            Pattern htmlColorPattern = Pattern.compile("<(red|blue|green|yellow|purple|orange|gray|grey)>([\\s\\S]*?)</(red|blue|green|yellow|purple|orange|gray|grey)>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
            Matcher htmlColorMatcher = htmlColorPattern.matcher(content);
            
            while (htmlColorMatcher.find()) {
                int start = htmlColorMatcher.start(2);
                int end = htmlColorMatcher.end(2);
                if (end > start) {
                    String colorTag = htmlColorMatcher.group(1).toLowerCase();
                    String styleClass = getColorStyleClass(colorTag);
                    if (styleClass != null) {
                        boolean alreadyCovered = markdownMatches.stream().anyMatch(m -> 
                            (start >= m.start && start < m.end) || (end > m.start && end <= m.end) ||
                            (start <= m.start && end >= m.end));
                        if (!alreadyCovered) {
                            markdownMatches.add(new MarkdownMatch(start, end, styleClass));
                        }
                    }
                }
            }
            
            // Deutsche Farb-Tags: <rot>text</rot>, <blau>text</blau>, etc.
            Pattern htmlColorDePattern = Pattern.compile("<(rot|blau|grün|gelb|lila|grau)>([\\s\\S]*?)</(rot|blau|grün|gelb|lila|grau)>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
            Matcher htmlColorDeMatcher = htmlColorDePattern.matcher(content);
            
            while (htmlColorDeMatcher.find()) {
                int start = htmlColorDeMatcher.start(2);
                int end = htmlColorDeMatcher.end(2);
                if (end > start) {
                    String colorTag = htmlColorDeMatcher.group(1).toLowerCase();
                    String styleClass = getColorStyleClassDe(colorTag);
                    if (styleClass != null) {
                        boolean alreadyCovered = markdownMatches.stream().anyMatch(m -> 
                            (start >= m.start && start < m.end) || (end > m.start && end <= m.end) ||
                            (start <= m.start && end >= m.end));
                        if (!alreadyCovered) {
                            markdownMatches.add(new MarkdownMatch(start, end, styleClass));
                        }
                    }
                }
            }
            
            // HTML Span mit color-Style: <span style="color: red;">text</span>
            Pattern htmlSpanColorPattern = Pattern.compile("<span\\s+style\\s*=\\s*[\"']color:\\s*(red|blue|green|yellow|purple|orange|gray|grey)[\"']>([\\s\\S]*?)</span>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
            Matcher htmlSpanColorMatcher = htmlSpanColorPattern.matcher(content);
            
            while (htmlSpanColorMatcher.find()) {
                int start = htmlSpanColorMatcher.start(2);
                int end = htmlSpanColorMatcher.end(2);
                if (end > start) {
                    String color = htmlSpanColorMatcher.group(1).toLowerCase();
                    String styleClass = getColorStyleClass(color);
                    if (styleClass != null) {
                        boolean alreadyCovered = markdownMatches.stream().anyMatch(m -> 
                            (start >= m.start && start < m.end) || (end > m.start && end <= m.end) ||
                            (start <= m.start && end >= m.end));
                        if (!alreadyCovered) {
                            markdownMatches.add(new MarkdownMatch(start, end, styleClass));
                        }
                    }
                }
            }
            
            // Blockquote-Pattern: > Text (zeilenbasiert)
            Pattern blockquotePattern = Pattern.compile("^>\\s*(.+)$", Pattern.MULTILINE);
            Matcher blockquoteMatcher = blockquotePattern.matcher(content);
            
            while (blockquoteMatcher.find()) {
                int start = blockquoteMatcher.start(1); // Start des Textes nach >
                int end = blockquoteMatcher.end(1);     // Ende des Textes
                if (end > start) {
                    boolean alreadyCovered = markdownMatches.stream().anyMatch(m -> 
                        (start >= m.start && start < m.end) || (end > m.start && end <= m.end) ||
                        (start <= m.start && end >= m.end));
                    if (!alreadyCovered) {
                        markdownMatches.add(new MarkdownMatch(start, end, "markdown-blockquote"));
                    }
                }
            }
            
            // Sortiere Markdown-Matches nach Position
            markdownMatches.sort((a, b) -> Integer.compare(a.start, b.start));
            
            // Hole existierende StyleSpans (Textanalyse-Markierungen)
            StyleSpans<Collection<String>> existingSpans = codeArea.getStyleSpans(0, codeArea.getLength());
            
            // EINFACHE LÖSUNG: Sammle alle existierenden Styles und entferne Markdown-Styles
            StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
            int currentPos = 0;
            
            // Sammle alle existierenden Styles (Suchen, Textanalyse, etc.)
            // WICHTIG: Entferne dabei alle Markdown-Styles und LanguageTool-Markierungen, damit sie nicht mehr angezeigt werden
            Map<Integer, Set<String>> existingStyles = new HashMap<>();
            for (StyleSpan<Collection<String>> span : existingSpans) {
                for (int i = 0; i < span.getLength(); i++) {
                    int pos = currentPos + i;
                    Set<String> styles = new HashSet<>(span.getStyle());
                    // Entferne alle Markdown-Styles (werden später neu hinzugefügt, wenn Matches vorhanden sind)
                    styles.removeIf(style -> style.startsWith("markdown-") || 
                                        style.startsWith("heading-"));
                    // WICHTIG: Entferne auch LanguageTool-Markierungen (werden später neu hinzugefügt, wenn Matches vorhanden sind)
                    styles.remove("languagetool-error");
                    if (!styles.isEmpty()) {
                        existingStyles.put(pos, styles);
                    }
                }
                currentPos += span.getLength();
            }
            
            // Füge Markdown-Styles zu existierenden Styles hinzu (nur wenn es Matches gibt)
            for (MarkdownMatch match : markdownMatches) {
                for (int i = match.start; i < match.end; i++) {
                    existingStyles.computeIfAbsent(i, k -> new HashSet<>()).add(match.styleClass);
                }
            }
            
            // Füge LanguageTool-Fehler-Styles hinzu (wenn aktiviert)
            // WICHTIG: Erstelle eine Kopie der Liste, um sicherzustellen, dass wir die aktuelle Version verwenden
            // WICHTIG: Synchronisiere Zugriff auf currentLanguageToolMatches, um Race Conditions zu vermeiden
            List<LanguageToolService.Match> matchesToProcess;
            synchronized (currentLanguageToolMatches) {
                matchesToProcess = new ArrayList<>(currentLanguageToolMatches);
            }
            if (languageToolEnabled && !matchesToProcess.isEmpty()) {
                logger.debug("applyCombinedStyling: Verarbeite " + matchesToProcess.size() + " LanguageTool-Matches für Text der Länge " + content.length());
                int validMatches = 0;
                int invalidMatches = 0;
                for (LanguageToolService.Match match : matchesToProcess) {
                    int start = match.getOffset();
                    int end = start + match.getLength();
                    // Stelle sicher, dass die Positionen innerhalb des Textes liegen
                    if (start >= 0 && end <= content.length() && start < end) {
                        validMatches++;
                        for (int i = start; i < end; i++) {
                            existingStyles.computeIfAbsent(i, k -> new HashSet<>()).add("languagetool-error");
                        }
                    } else {
                        invalidMatches++;
                        logger.debug("applyCombinedStyling: Ungültiger Match - start=" + start + ", end=" + end + ", contentLength=" + content.length());
                    }
                }
                logger.debug("applyCombinedStyling: " + validMatches + " gültige Matches, " + invalidMatches + " ungültige Matches werden angewendet");
            } else if (languageToolEnabled && matchesToProcess.isEmpty()) {
                synchronized (currentLanguageToolMatches) {
                    logger.debug("applyCombinedStyling: LanguageTool aktiviert, aber keine Matches vorhanden (currentLanguageToolMatches.size()=" + currentLanguageToolMatches.size() + ")");
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
            
            // WICHTIG: Aktualisiere lastStyledText, damit der Timer weiß, dass Styling angewendet wurde
            lastStyledText = content;
            
            // WICHTIG: KEINE Viewport- oder Cursor-Wiederherstellung
            // Die Methode wendet nur Styles an, ohne den Viewport oder Cursor zu ändern
            
        } catch (Exception e) {
            logger.debug("Fehler beim kombinierten Styling: {}", e.getMessage());
        }
    }
    

    

    

    

    

    
    /**
     * Prüft LanguageTool-Fehler mit Debouncing
     */
    private void checkLanguageToolErrorsDebounced() {
        if (!languageToolEnabled || codeArea == null) {
            logger.debug("checkLanguageToolErrorsDebounced: LanguageTool deaktiviert oder CodeArea null");
            return;
        }
        
        logger.debug("checkLanguageToolErrorsDebounced: Starte debounced Prüfung");
        
        // Stoppe vorherige Timeline
        if (languageToolCheckTimeline != null) {
            languageToolCheckTimeline.stop();
        }
        
        // Starte neue Timeline mit 500ms Verzögerung
        languageToolCheckTimeline = new Timeline(new KeyFrame(Duration.millis(500), event -> {
            logger.debug("checkLanguageToolErrorsDebounced: Verzögerung abgelaufen, rufe checkLanguageToolErrors() auf");
            checkLanguageToolErrors();
            languageToolCheckTimeline = null;
        }));
        languageToolCheckTimeline.play();
    }
    
    /**
     * Bricht alle laufenden LanguageTool-Checks ab und löscht die Matches
     * Wird aufgerufen, wenn ein neues Kapitel geladen wird, um Race Conditions zu vermeiden
     */
    private void cancelLanguageToolChecks() {
        logger.debug("cancelLanguageToolChecks: Breche alle laufenden LanguageTool-Checks ab");
        
        // Stoppe die Debounce-Timeline
        if (languageToolCheckTimeline != null) {
            languageToolCheckTimeline.stop();
            languageToolCheckTimeline = null;
        }
        
        // Lösche alle Matches
        currentLanguageToolMatches.clear();
        
        // Aktualisiere das Styling, um alle Markierungen zu entfernen
        if (codeArea != null) {
            Platform.runLater(() -> {
                applyCombinedStyling();
                updateLanguageToolStatus();
            });
        }
    }
    
    /**
     * Prüft den aktuellen Text auf LanguageTool-Fehler
     */
    private void checkLanguageToolErrors() {
        if (!languageToolEnabled || codeArea == null || languageToolService == null) {
            logger.debug("checkLanguageToolErrors: Prüfung übersprungen - enabled=" + languageToolEnabled + 
                        ", codeArea=" + (codeArea != null) + ", service=" + (languageToolService != null));
            return;
        }
        
        String text = codeArea.getText();
        if (text == null || text.trim().isEmpty()) {
            logger.debug("checkLanguageToolErrors: Text ist leer, lösche Matches");
            currentLanguageToolMatches.clear();
            applyCombinedStyling();
            return;
        }
        
        logger.debug("checkLanguageToolErrors: Starte Prüfung für Text der Länge " + text.length());
        
        // WICHTIG: Speichere den aktuellen Text, um später zu prüfen, ob er sich geändert hat
        final String textAtStart = text;
        
        // Prüfe Server-Status und starte falls nötig
        languageToolService.startServerIfNeeded().thenCompose(serverReady -> {
            if (!serverReady) {
                Platform.runLater(() -> {
                    logger.warn("LanguageTool Server nicht verfügbar");
                    updateStatus("LanguageTool Server nicht verfügbar");
                });
                return CompletableFuture.<LanguageToolService.CheckResult>completedFuture(null);
            }
            
            // Prüfe Text
            return languageToolService.checkText(text, "de-DE");
        }).thenAccept(result -> {
            if (result != null) {
                Platform.runLater(() -> {
                    // WICHTIG: Prüfe, ob der Text sich seit dem Start der Prüfung geändert hat
                    // Wenn ja, ignoriere die Ergebnisse, da sie für einen anderen Text sind
                    String currentTextAtCheck = codeArea != null ? codeArea.getText() : "";
                    if (!textAtStart.equals(currentTextAtCheck)) {
                        logger.debug("checkLanguageToolErrors: Text hat sich während der Prüfung geändert - ignoriere Ergebnisse und starte neue Prüfung");
                        // Starte eine neue Prüfung mit dem aktuellen Text
                        checkLanguageToolErrorsDebounced();
                        return;
                    }
                    
                    logger.debug("checkLanguageToolErrors: Ergebnis erhalten mit " + result.getMatches().size() + " Matches");
                    // Filtere Matches mit Wörtern aus dem Wörterbuch
                    String editorText = codeArea != null ? codeArea.getText() : "";
                    List<LanguageToolService.Match> allMatches = result.getMatches();
                    List<LanguageToolService.Match> filteredMatches = languageToolDictionary.filterMatches(allMatches, editorText);
                    
                    // Filter: Entferne Matches mit ungültigen Positionen (z.B. nach Textänderungen)
                    filteredMatches = filteredMatches.stream()
                        .filter(match -> {
                            int start = match.getOffset();
                            int end = start + match.getLength();
                            // Stelle sicher, dass die Positionen innerhalb des Textes liegen
                            if (start < 0 || end > editorText.length() || start >= end) {
                                logger.debug("Entferne Match mit ungültiger Position: start=" + start + ", end=" + end + ", textLength=" + editorText.length());
                                return false;
                            }
                            return true;
                        })
                        .collect(Collectors.toList());
                    
                    // Filter: Ignoriere Guillemet-Fehler am Ende des Textes
                    // Wenn beide Anführungszeichen des letzten Satzes am Textende stehen, sind sie korrekt
                    filteredMatches = filteredMatches.stream()
                        .filter(match -> {
                            if (editorText.isEmpty()) return true;
                            
                            // Prüfe ob der Match am Ende des Textes ist (letzte 10 Zeichen)
                            int textEnd = editorText.length();
                            int matchEnd = match.getOffset() + match.getLength();
                            
                            if (matchEnd >= textEnd - 10) {
                                // Extrahiere den Text am Ende (letzte 50 Zeichen für Kontext)
                                int startContext = Math.max(0, textEnd - 50);
                                String endText = editorText.substring(startContext);
                                
                                // Prüfe ob es sich um Guillemets handelt (» und «)
                                String matchedText = "";
                                if (match.getOffset() < editorText.length()) {
                                    matchedText = editorText.substring(
                                        match.getOffset(), 
                                        Math.min(match.getOffset() + match.getLength(), editorText.length())
                                    );
                                }
                                
                                // Prüfe ob das Match ein Guillemet ist
                                boolean isGuillemet = matchedText.contains("\u00BB") || matchedText.contains("\u00AB") || 
                                                     matchedText.contains("»") || matchedText.contains("«");
                                
                                if (isGuillemet) {
                                    // Zähle Guillemets am Ende des Textes
                                    long guillemetCount = endText.chars()
                                        .filter(c -> c == '\u00BB' || c == '\u00AB' || c == '»' || c == '«')
                                        .count();
                                    
                                    // Wenn es genau 2 Guillemets am Ende gibt (öffnend + schließend), ignoriere beide Fehler
                                    if (guillemetCount == 2) {
                                        logger.debug("Ignoriere Guillemet-Fehler am Textende (beide Anführungszeichen des letzten Satzes): " + match.getMessage());
                                        return false;
                                    }
                                }
                            }
                            return true; // Behalte alle anderen Matches
                        })
                        .collect(Collectors.toList());
                    
                    // WICHTIG: Ersetze die Liste atomar, um Race Conditions zu vermeiden
                    synchronized (currentLanguageToolMatches) {
                        currentLanguageToolMatches = new ArrayList<>(filteredMatches);
                    }
                    
                    logger.debug("checkLanguageToolErrors: Nach Filterung " + currentLanguageToolMatches.size() + " Matches");
                    if (!currentLanguageToolMatches.isEmpty()) {
                        logger.debug("checkLanguageToolErrors: Erste Match-Position: " + currentLanguageToolMatches.get(0).getOffset() + ", Länge: " + currentLanguageToolMatches.get(0).getLength());
                        logger.debug("checkLanguageToolErrors: Text-Länge: " + editorText.length());
                    }
                    
                    // WICHTIG: Styling explizit aktualisieren, um Markierungen anzuzeigen
                    // Stelle sicher, dass wir im JavaFX-Thread sind (sind wir bereits durch Platform.runLater)
                    // WICHTIG: applyCombinedStyling() verändert NICHT Cursor-Position oder Viewport
                    applyCombinedStyling();
                    updateLanguageToolStatus();
                    if (!currentLanguageToolMatches.isEmpty()) {
                        updateStatus("LanguageTool: " + currentLanguageToolMatches.size() + " Fehler gefunden");
                        logger.debug("checkLanguageToolErrors: Styling aktualisiert, " + currentLanguageToolMatches.size() + " Matches sollten sichtbar sein");
                    } else {
                        logger.debug("checkLanguageToolErrors: Keine Fehler mehr gefunden");
                    }
                });
            } else {
                logger.debug("checkLanguageToolErrors: Ergebnis ist null");
            }
        }).exceptionally(e -> {
            logger.error("Fehler bei LanguageTool-Prüfung", e);
            Platform.runLater(() -> {
                updateStatus("LanguageTool Fehler: " + e.getMessage());
            });
            return null;
        });
    }
    
    /**
     * Erstellt die LanguageTool-Buttons und fügt sie zur Toolbar hinzu
     */
    private void setupLanguageToolButtons() {
        // Toggle-Button für LanguageTool
        btnToggleLanguageTool = new Button("LanguageTool");
        btnToggleLanguageTool.setTooltip(new Tooltip("LanguageTool Grammatikprüfung ein-/ausschalten"));
        btnToggleLanguageTool.setOnAction(e -> toggleLanguageTool());
        updateLanguageToolButtonState();
        
        // Einstellungs-Button
        btnLanguageToolSettings = new Button("⚙");
        btnLanguageToolSettings.setTooltip(new Tooltip("LanguageTool Einstellungen"));
        btnLanguageToolSettings.setOnAction(e -> showLanguageToolSettings());
        
        // Status-Label für Fehleranzahl
        lblLanguageToolStatus = new Label("");
        lblLanguageToolStatus.setTooltip(new Tooltip("LanguageTool Status"));
        lblLanguageToolStatus.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");
        
        // Buttons zur Toolbar hinzufügen (nach Preview-Button)
        if (btnPreview != null && btnPreview.getParent() != null) {
            Parent parent = btnPreview.getParent();
            if (parent instanceof HBox) {
                HBox toolbar = (HBox) parent;
                int previewIndex = toolbar.getChildren().indexOf(btnPreview);
                toolbar.getChildren().add(previewIndex + 1, new Separator());
                toolbar.getChildren().add(previewIndex + 2, btnToggleLanguageTool);
                toolbar.getChildren().add(previewIndex + 3, btnLanguageToolSettings);
                toolbar.getChildren().add(previewIndex + 4, lblLanguageToolStatus);
            }
        }
    }
    
    /**
     * Schaltet LanguageTool ein/aus
     */
    private void toggleLanguageTool() {
        languageToolEnabled = !languageToolEnabled;
        
        // Einstellung in Preferences speichern
        preferences.putBoolean("languagetool_enabled", languageToolEnabled);
        try {
            preferences.flush();
        } catch (Exception e) {
            logger.warn("Konnte LanguageTool-Einstellung nicht speichern: " + e.getMessage());
        }
        
        updateLanguageToolButtonState();
        
        if (languageToolEnabled) {
            // Starte Server und prüfe Text
            languageToolService.startServerIfNeeded().thenAccept(serverReady -> {
                Platform.runLater(() -> {
                    if (serverReady) {
                        checkLanguageToolErrors();
                        updateStatus("LanguageTool aktiviert");
                    } else {
                        languageToolEnabled = false;
                        updateLanguageToolButtonState();
                        updateStatus("LanguageTool Server nicht verfügbar");
                        Alert alert = new Alert(Alert.AlertType.WARNING);
                        alert.setTitle("LanguageTool Server");
                        alert.setHeaderText("Server nicht verfügbar");
                        alert.setContentText("Der LanguageTool Server konnte nicht gestartet werden.\nBitte prüfen Sie die Einstellungen.");
                        alert.showAndWait();
                    }
                });
            });
        } else {
            // Entferne Markierungen
            currentLanguageToolMatches.clear();
            applyCombinedStyling();
            updateLanguageToolStatus();
            updateStatus("LanguageTool deaktiviert");
        }
    }
    
    /**
     * Aktualisiert den Zustand des Toggle-Buttons
     */
    private void updateLanguageToolButtonState() {
        if (btnToggleLanguageTool != null) {
            if (languageToolEnabled) {
                btnToggleLanguageTool.setStyle("-fx-background-color: #4caf50; -fx-text-fill: white;");
                btnToggleLanguageTool.setText("LanguageTool ✓");
            } else {
                btnToggleLanguageTool.setStyle("");
                btnToggleLanguageTool.setText("LanguageTool");
            }
        }
    }
    
    /**
     * Aktualisiert den LanguageTool-Status-Indikator
     */
    private void updateLanguageToolStatus() {
        if (lblLanguageToolStatus != null) {
            if (!languageToolEnabled) {
                lblLanguageToolStatus.setText("");
                lblLanguageToolStatus.setTooltip(new Tooltip("LanguageTool deaktiviert"));
            } else if (currentLanguageToolMatches.isEmpty()) {
                lblLanguageToolStatus.setText("✓");
                lblLanguageToolStatus.setStyle("-fx-text-fill: #4caf50; -fx-font-size: 11px;");
                lblLanguageToolStatus.setTooltip(new Tooltip("Keine Fehler gefunden"));
            } else {
                lblLanguageToolStatus.setText("⚠ " + currentLanguageToolMatches.size());
                lblLanguageToolStatus.setStyle("-fx-text-fill: #f44336; -fx-font-size: 11px;");
                lblLanguageToolStatus.setTooltip(new Tooltip(currentLanguageToolMatches.size() + " Fehler gefunden"));
            }
        }
    }
    
    /**
     * Zeigt den LanguageTool-Einstellungsdialog
     */
    private void showLanguageToolSettings() {
        CustomStage settingsStage = StageManager.createModalStage("LanguageTool Einstellungen", stage);
        settingsStage.setTitle("⚙️ LanguageTool Einstellungen");
        settingsStage.setWidth(500);
        settingsStage.setHeight(400);
        settingsStage.setTitleBarTheme(currentThemeIndex);
        
        VBox mainContent = new VBox(15);
        mainContent.setPadding(new Insets(20));
        mainContent.getStyleClass().add("dialog-container");
        applyThemeToNode(mainContent, currentThemeIndex);
        
        // Titel
        Label titleLabel = new Label("LanguageTool Einstellungen");
        titleLabel.getStyleClass().add("dialog-title");
        applyThemeToNode(titleLabel, currentThemeIndex);
        
        // Server-URL
        Label urlLabel = new Label("Server-URL:");
        urlLabel.getStyleClass().add("dialog-label");
        applyThemeToNode(urlLabel, currentThemeIndex);
        TextField urlField = new TextField(languageToolService.getServerUrl());
        urlField.setPrefWidth(400);
        applyThemeToNode(urlField, currentThemeIndex);
        
        // Server-JAR-Pfad
        Label jarPathLabel = new Label("Server-JAR-Pfad:");
        jarPathLabel.getStyleClass().add("dialog-label");
        applyThemeToNode(jarPathLabel, currentThemeIndex);
        HBox jarPathBox = new HBox(10);
        TextField jarPathField = new TextField(languageToolService.getServerJarPath());
        jarPathField.setPrefWidth(350);
        applyThemeToNode(jarPathField, currentThemeIndex);
        Button btnBrowseJar = new Button("Durchsuchen...");
        btnBrowseJar.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("LanguageTool Server JAR auswählen");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JAR-Dateien", "*.jar"));
            File selectedFile = fileChooser.showOpenDialog(settingsStage);
            if (selectedFile != null) {
                // Relativen Pfad berechnen, falls möglich
                String userDir = System.getProperty("user.dir");
                if (userDir != null) {
                    try {
                        File userDirFile = new File(userDir);
                        String relativePath = userDirFile.toPath().relativize(selectedFile.toPath()).toString();
                        jarPathField.setText(relativePath.replace("\\", "/"));
                    } catch (Exception ex) {
                        jarPathField.setText(selectedFile.getAbsolutePath());
                    }
                } else {
                    jarPathField.setText(selectedFile.getAbsolutePath());
                }
            }
        });
        applyThemeToNode(btnBrowseJar, currentThemeIndex);
        jarPathBox.getChildren().addAll(jarPathField, btnBrowseJar);
        applyThemeToNode(jarPathBox, currentThemeIndex);
        
        // Auto-Start Checkbox
        CheckBox autoStartCheck = new CheckBox("Server automatisch starten");
        autoStartCheck.setSelected(languageToolService.isAutoStartEnabled());
        applyThemeToNode(autoStartCheck, currentThemeIndex);
        
        // Server-Status
        Label statusLabel = new Label("Server-Status: Prüfe...");
        applyThemeToNode(statusLabel, currentThemeIndex);
        
        // Buttons
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        Button btnSave = new Button("Speichern");
        btnSave.getStyleClass().add("button");
        btnSave.setOnAction(e -> {
            languageToolService.setServerUrl(urlField.getText());
            languageToolService.setServerJarPath(jarPathField.getText());
            languageToolService.setAutoStartEnabled(autoStartCheck.isSelected());
            settingsStage.close();
            updateStatus("LanguageTool Einstellungen gespeichert");
        });
        applyThemeToNode(btnSave, currentThemeIndex);
        Button btnCancel = new Button("Abbrechen");
        btnCancel.getStyleClass().add("button");
        btnCancel.setOnAction(e -> settingsStage.close());
        applyThemeToNode(btnCancel, currentThemeIndex);
        
        Button btnDictionary = new Button("Wörterbuch verwalten");
        btnDictionary.getStyleClass().add("button");
        btnDictionary.setOnAction(e -> {
            showLanguageToolDictionaryDialog(settingsStage);
        });
        applyThemeToNode(btnDictionary, currentThemeIndex);
        
        Button btnTestServer = new Button("Server testen");
        btnTestServer.getStyleClass().add("button");
        btnTestServer.setOnAction(e -> {
            btnTestServer.setDisable(true);
            statusLabel.setText("Server-Status: Prüfe...");
            languageToolService.setServerUrl(urlField.getText());
            languageToolService.setServerJarPath(jarPathField.getText());
            languageToolService.checkServerStatus().thenAccept(isRunning -> {
                Platform.runLater(() -> {
                    if (isRunning) {
                        statusLabel.setText("Server-Status: ✓ Läuft");
                        statusLabel.setStyle("-fx-text-fill: #4caf50;");
                    } else {
                        statusLabel.setText("Server-Status: ✗ Nicht erreichbar");
                        statusLabel.setStyle("-fx-text-fill: #f44336;");
                    }
                    btnTestServer.setDisable(false);
                });
            });
        });
        applyThemeToNode(btnTestServer, currentThemeIndex);
        
        buttonBox.getChildren().addAll(btnCancel, btnDictionary, btnTestServer, btnSave);
        applyThemeToNode(buttonBox, currentThemeIndex);
        
        mainContent.getChildren().addAll(
            titleLabel,
            new Separator(),
            urlLabel,
            urlField,
            jarPathLabel,
            jarPathBox,
            autoStartCheck,
            new Separator(),
            statusLabel,
            buttonBox
        );
        
        Scene scene = new Scene(mainContent);
        scene.setFill(javafx.scene.paint.Color.web(THEMES[currentThemeIndex][0]));
        String cssPath = ResourceManager.getCssResource("css/editor.css");
        if (cssPath != null) {
            scene.getStylesheets().add(cssPath);
        }
        // CSS auch für Theme-Klassen
        String manuskriptCssPath = ResourceManager.getCssResource("css/manuskript.css");
        if (manuskriptCssPath != null) {
            scene.getStylesheets().add(manuskriptCssPath);
        }
        
        settingsStage.setSceneWithTitleBar(scene);
        
        settingsStage.showAndWait();
    }
    
    /**
     * Richtet das Kontextmenü für den CodeArea ein
     */
    private void setupContextMenu() {
        if (codeArea == null) return;
        
        ContextMenu contextMenu = new ContextMenu();
        // WICHTIG: Setze maximale Breite für das Kontextmenü, damit lange Texte umgebrochen werden können
        contextMenu.setMaxWidth(600);
        styleContextMenu(contextMenu, currentThemeIndex);
        
        // WICHTIG: Setze Cursor beim Klick, damit Kontextmenü die richtige Position findet
        codeArea.setOnMousePressed(mouseEvent -> {
            if (mouseEvent.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                try {
                    double sceneX = mouseEvent.getSceneX();
                    double sceneY = mouseEvent.getSceneY();
                    Point2D localPoint = codeArea.sceneToLocal(sceneX, sceneY);
                    int clickPos = codeArea.hit(localPoint.getX(), localPoint.getY()).getInsertionIndex();
                    if (clickPos >= 0 && clickPos <= codeArea.getLength()) {
                        codeArea.displaceCaret(clickPos);
                    }
                } catch (Exception e) {
                    logger.debug("Fehler beim Setzen der Cursor-Position: " + e.getMessage());
                }
            }
        });
        
        // LanguageTool-Korrekturvorschläge (wenn auf Fehler geklickt wird)
        codeArea.setOnContextMenuRequested(contextEvent -> {
            contextMenu.getItems().clear();
            
            // WICHTIG: Setze zuerst den Cursor an die Klick-Position
            // Dann verwende die Cursor-Position für die Match-Suche
            int clickPos = -1;
            try {
                // Verwende Scene-Koordinaten aus dem Event (nicht Screen-Koordinaten)
                double sceneX = contextEvent.getSceneX();
                double sceneY = contextEvent.getSceneY();
                
                // Konvertiere Scene-Koordinaten zu lokalen CodeArea-Koordinaten
                Point2D localPoint = codeArea.sceneToLocal(sceneX, sceneY);
                
                // Verwende hit() um die Text-Position zu finden
                clickPos = codeArea.hit(localPoint.getX(), localPoint.getY()).getInsertionIndex();
            } catch (Exception e) {
                logger.debug("Fehler beim Ermitteln der Klick-Position: " + e.getMessage());
            }
            
            // Fallback: Verwende Cursor-Position
            if (clickPos < 0) {
                clickPos = codeArea.getCaretPosition();
            }
            
            // Cursor erst am Ende des Handlers setzen, damit die Selektion für "Kopieren" erhalten bleibt
            // und findMatchAtPosition(clickPos) nutzt die berechnete Position, nicht den Cursor
            
            // Debug-Logging
            logger.debug("Kontextmenü: Position=" + clickPos + ", Matches=" + currentLanguageToolMatches.size());
            
            LanguageToolService.Match clickedMatch = findMatchAtPosition(clickPos);
            
            // Debug-Logging
            if (clickedMatch != null) {
                logger.debug("Match gefunden: offset=" + clickedMatch.getOffset() + ", length=" + clickedMatch.getLength());
            } else {
                logger.debug("Kein Match gefunden an Position " + clickPos);
            }
            
            if (clickedMatch != null && languageToolEnabled) {
                // Fehler gefunden - zeige Korrekturvorschläge
                // Verwende ein Label mit wrapText, damit der Text umgebrochen wird
                String message = clickedMatch.getMessage();
                Label headerLabel = new Label("LanguageTool: " + message);
                headerLabel.setWrapText(true);
                // WICHTIG: Setze die Breite direkt am Label, nicht am Container
                headerLabel.setPrefWidth(600);
                headerLabel.setMaxWidth(600);
                headerLabel.setMinWidth(Region.USE_PREF_SIZE);
                headerLabel.setStyle("-fx-padding: 5px; -fx-alignment: center-left; -fx-text-alignment: left;");
                // Wende Theme-Styling auf das Label an
                applyThemeToNode(headerLabel, currentThemeIndex);
                
                // Packe das Label in einen Container mit fester Breite
                VBox container = new VBox(headerLabel);
                container.setAlignment(Pos.CENTER_LEFT);
                container.setPrefWidth(600);
                container.setMaxWidth(600);
                container.setMinWidth(600); // WICHTIG: Minimale Breite erzwingen
                container.setStyle("-fx-padding: 0px;");
                
                // Verwende CustomMenuItem statt MenuItem, um mehr Kontrolle zu haben
                // Der zweite Parameter (false) bedeutet, dass das Menü nicht automatisch schließt
                CustomMenuItem headerItem = new CustomMenuItem(container, false);
                headerItem.setDisable(true);
                // WICHTIG: Setze die Breite des CustomMenuItem explizit
                headerItem.setContent(container);
                // WICHTIG: Setze CSS-Klasse für zusätzliches Styling
                headerItem.getStyleClass().add("custom-menu-item");
                contextMenu.getItems().add(headerItem);
                contextMenu.getItems().add(new SeparatorMenuItem());
                
                if (!clickedMatch.getReplacements().isEmpty()) {
                    for (LanguageToolService.Replacement replacement : clickedMatch.getReplacements()) {
                        MenuItem replaceItem = new MenuItem("→ " + replacement.getValue());
                        replaceItem.setOnAction(replaceEvent -> {
                            applyLanguageToolCorrection(clickedMatch, replacement.getValue());
                        });
                        contextMenu.getItems().add(replaceItem);
                    }
                } else {
                    MenuItem noSuggestions = new MenuItem("Keine Vorschläge verfügbar");
                    noSuggestions.setDisable(true);
                    contextMenu.getItems().add(noSuggestions);
                }
                
                // Option: Zum Wörterbuch hinzufügen
                String matchedText = codeArea.getText().substring(
                    clickedMatch.getOffset(), 
                    clickedMatch.getOffset() + clickedMatch.getLength()
                ).trim();
                
                if (!matchedText.isEmpty() && !languageToolDictionary.containsWordOrVariant(matchedText)) {
                    contextMenu.getItems().add(new SeparatorMenuItem());
                    MenuItem addToDictItem = new MenuItem("Zum Wörterbuch hinzufügen: \"" + matchedText + "\"");
                    addToDictItem.setOnAction(addEvent -> {
                        logger.info("=== WORT ZUM WÖRTERBUCH HINZUFÜGEN ===");
                        logger.info("Wort: '" + matchedText + "'");
                        logger.info("Aktuelle Matches vor Hinzufügung: " + currentLanguageToolMatches.size());
                        
                        // 1. Füge das Wort zum Wörterbuch hinzu
                        languageToolDictionary.addWord(matchedText);
                        logger.info("Wort hinzugefügt. Prüfe ob im Wörterbuch: " + languageToolDictionary.containsWordOrVariant(matchedText));
                        
                        // 2. Baue die Liste komplett neu auf: Filtere alle Matches mit dem aktualisierten Wörterbuch
                        String editorText = codeArea != null ? codeArea.getText() : "";
                        logger.info("Editor-Text vorhanden: " + (editorText != null && !editorText.isEmpty()));
                        
                        if (editorText != null && !editorText.isEmpty()) {
                            logger.info("Vor Neuaufbau: " + currentLanguageToolMatches.size() + " Matches");
                            
                            // Filtere alle Matches mit dem aktualisierten Wörterbuch
                            List<LanguageToolService.Match> filteredMatches = languageToolDictionary.filterMatches(
                                new ArrayList<>(currentLanguageToolMatches), 
                                editorText
                            );
                            
                            logger.info("Nach Filterung: " + filteredMatches.size() + " Matches");
                            
                            // 3. Ersetze die Liste komplett - WICHTIG: Neue Liste erstellen!
                            currentLanguageToolMatches = new ArrayList<>(filteredMatches);
                            
                            logger.info("Liste ersetzt. Aktuelle Matches: " + currentLanguageToolMatches.size());
                            
                            // 4. Aktualisiere Styling SOFORT - WICHTIG: Explizit StyleSpans neu setzen
                            logger.info("Rufe applyCombinedStyling() auf...");
                            
                            // WICHTIG: Setze StyleSpans explizit neu, um sicherzustellen, dass Änderungen sichtbar werden
                            Platform.runLater(() -> {
                                logger.info("applyCombinedStyling() wird aufgerufen mit " + currentLanguageToolMatches.size() + " Matches");
                                applyCombinedStyling();
                                updateLanguageToolStatus();
                                
                                // Zusätzlich: Erzwinge ein Update des CodeArea
                                if (codeArea != null) {
                                    // Erzwinge ein Re-layout
                                    codeArea.requestLayout();
                                    
                                    // Setze StyleSpans nochmal explizit
                                    String content = codeArea.getText();
                                    if (content != null && !content.isEmpty()) {
                                        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
                                        spansBuilder.add(Collections.emptyList(), content.length());
                                        StyleSpans<Collection<String>> spans = spansBuilder.create();
                                        codeArea.setStyleSpans(0, spans);
                                        
                                        // Jetzt wieder mit den aktuellen Matches
                                        applyCombinedStyling();
                                    }
                                }
                                
                                logger.info("=== FERTIG. Verbleibende Matches: " + currentLanguageToolMatches.size() + " ===");
                            });
                        } else {
                            logger.warn("Editor-Text ist leer oder null!");
                        }
                        
                        updateStatus("Wort zum Wörterbuch hinzugefügt: " + matchedText);
                    });
                    contextMenu.getItems().add(addToDictItem);
                }
                
                contextMenu.getItems().add(new SeparatorMenuItem());
            }
            
            // Kopieren und Einfügen (in try-catch, damit eine Exception hier die LanguageTool-Vorschläge nicht verhindert)
            try {
                MenuItem itemKopieren = new MenuItem("Kopieren\tCtrl+C");
                String selectedForCopy = getSelectedTextSafely();
                itemKopieren.setDisable(selectedForCopy == null || selectedForCopy.isEmpty());
                itemKopieren.setOnAction(e -> {
                    if (codeArea != null) {
                        String sel = getSelectedTextSafely();
                        if (sel != null && !sel.isEmpty()) {
                            try {
                                Clipboard cb = Clipboard.getSystemClipboard();
                                ClipboardContent content = new ClipboardContent();
                                content.putString(sel);
                                cb.setContent(content);
                            } catch (Exception ex) {
                                logger.debug("Kopieren in Zwischenablage fehlgeschlagen: " + ex.getMessage());
                            }
                        }
                    }
                });
                
                MenuItem itemEinfuegen = new MenuItem("Einfügen\tCtrl+V");
                boolean hasClipboardText = false;
                try {
                    Clipboard clipboard = Clipboard.getSystemClipboard();
                    hasClipboardText = clipboard.hasString();
                } catch (Exception ex) {
                    logger.debug("Zwischenablage-Zugriff fehlgeschlagen: " + ex.getMessage());
                }
                itemEinfuegen.setDisable(!hasClipboardText);
                itemEinfuegen.setOnAction(e -> {
                    if (codeArea != null) {
                        try {
                            Clipboard clipboard = Clipboard.getSystemClipboard();
                            if (clipboard.hasString()) {
                                String text = clipboard.getString();
                                if (text != null && !text.isEmpty()) {
                                    int caretPos = codeArea.getCaretPosition();
                                    String sel = codeArea.getSelectedText();
                                    if (sel != null && !sel.isEmpty()) {
                                        codeArea.replaceSelection(text);
                                    } else {
                                        codeArea.insertText(caretPos, text);
                                    }
                                }
                            }
                        } catch (Exception ex) {
                            logger.debug("Einfügen aus Zwischenablage fehlgeschlagen: " + ex.getMessage());
                        }
                    }
                });
                
                contextMenu.getItems().addAll(itemKopieren, itemEinfuegen);
            } catch (Exception ex) {
                logger.warn("Kontextmenü Kopieren/Einfügen konnte nicht erstellt werden – LanguageTool-Vorschläge sollten trotzdem sichtbar sein: " + ex.getMessage());
                MenuItem itemKopieren = new MenuItem("Kopieren\tCtrl+C");
                MenuItem itemEinfuegen = new MenuItem("Einfügen\tCtrl+V");
                itemKopieren.setDisable(true);
                itemEinfuegen.setDisable(true);
                contextMenu.getItems().addAll(itemKopieren, itemEinfuegen);
            }
            contextMenu.getItems().add(new SeparatorMenuItem());
            
            // Standard-Menüpunkte
            MenuItem itemSprechantwort = new MenuItem("Sprechantwort korrigieren");
            itemSprechantwort.setOnAction(actionEvent -> handleSprechantwortKorrektur());
            
            MenuItem itemPhrase = new MenuItem("Phrase korrigieren");
            itemPhrase.setOnAction(actionEvent -> handlePhraseKorrektur());
            
            MenuItem itemAbsatz = new MenuItem("Absatz überarbeiten");
            itemAbsatz.setOnAction(actionEvent -> handleAbsatzUeberarbeitung());
            
            contextMenu.getItems().addAll(itemSprechantwort, itemPhrase, itemAbsatz);
            
            // Cursor an Rechtsklick-Position setzen (nach Menüaufbau, damit Selektion für "Kopieren" erhalten blieb)
            if (clickPos >= 0 && clickPos <= codeArea.getLength()) {
                codeArea.displaceCaret(clickPos);
            }
        });
        
        codeArea.setContextMenu(contextMenu);
    }
    
    /**
     * Findet einen LanguageTool-Match an der angegebenen Position
     */
    private LanguageToolService.Match findMatchAtPosition(int position) {
        if (position < 0 || currentLanguageToolMatches == null || currentLanguageToolMatches.isEmpty()) {
            return null;
        }
        
        for (LanguageToolService.Match match : currentLanguageToolMatches) {
            int start = match.getOffset();
            int end = start + match.getLength();
            // Prüfe ob Position innerhalb des Match-Bereichs liegt
            // Verwende >= start und < end für bessere Genauigkeit (nicht <= end)
            if (position >= start && position < end) {
                return match;
            }
        }
        return null;
    }
    
    /**
     * Wendet eine LanguageTool-Korrektur an
     */
    private void applyLanguageToolCorrection(LanguageToolService.Match match, String replacement) {
        if (codeArea == null || match == null) return;
        
        // Setze Flag, um doppelte Prüfungen zu vermeiden
        isApplyingLanguageToolCorrection = true;
        
        try {
            int start = match.getOffset();
            int length = match.getLength();
            
            // Ersetze Text
            codeArea.replaceText(start, start + length, replacement);
            
            // WICHTIG: Kompletter Reset nach Korrektur
            // 1. Lösche alle Matches sofort, da Positionen nach Text-Änderung ungültig sind
            currentLanguageToolMatches.clear();
            
            // 2. Aktualisiere Styling sofort (ohne Fehler-Markierungen)
            applyCombinedStyling();
            updateLanguageToolStatus();
            
            // 3. Starte eine vollständige Neuprüfung nach kurzer Verzögerung
            // (damit der Text-Change-Listener nicht interferiert)
            Platform.runLater(() -> {
                if (languageToolEnabled) {
                    // Kurze Verzögerung, damit der Text-Change-Listener nicht interferiert
                    new java.util.Timer().schedule(new java.util.TimerTask() {
                        @Override
                        public void run() {
                            Platform.runLater(() -> {
                                isApplyingLanguageToolCorrection = false; // Flag zurücksetzen
                                checkLanguageToolErrors(); // Vollständige Neuprüfung
                            });
                        }
                    }, 200); // 200ms Verzögerung für sichereren Reset
                } else {
                    isApplyingLanguageToolCorrection = false;
                }
            });
        } catch (Exception e) {
            logger.error("Fehler bei LanguageTool-Korrektur", e);
            isApplyingLanguageToolCorrection = false;
        }
    }
    
    /**
     * Zeigt den Dialog zur Verwaltung des LanguageTool-Wörterbuchs
     */
    private void showLanguageToolDictionaryDialog(Window owner) {
        CustomStage dictStage = StageManager.createModalStage("📖 LanguageTool Wörterbuch", owner);
        dictStage.setWidth(600);
        dictStage.setHeight(500);
        dictStage.setMinWidth(500);
        dictStage.setMinHeight(400);
        dictStage.setTitleBarTheme(currentThemeIndex);
        
        VBox mainContent = new VBox(15);
        mainContent.setPadding(new Insets(20));
        mainContent.setSpacing(15);
        mainContent.getStyleClass().add("dialog-container");
        applyThemeToNode(mainContent, currentThemeIndex);
        
        // Titel
        Label titleLabel = new Label("Benutzer-Wörterbuch");
        titleLabel.getStyleClass().add("dialog-title");
        applyThemeToNode(titleLabel, currentThemeIndex);
        
        // Info-Text
        Label infoLabel = new Label("Eigennamen und andere Wörter, die nicht als Fehler markiert werden sollen:");
        infoLabel.setWrapText(true);
        infoLabel.getStyleClass().add("dialog-label");
        applyThemeToNode(infoLabel, currentThemeIndex);
        
        // Liste der Wörter
        ListView<String> wordList = new ListView<>();
        wordList.setPrefHeight(250);
        wordList.getStyleClass().add("alternating-list");
        applyThemeToNode(wordList, currentThemeIndex);
        
        // Aktualisiere Liste
        Runnable refreshList = () -> {
            Set<String> words = languageToolDictionary.getAllWords();
            List<String> sortedWords = words.stream()
                .sorted()
                .collect(Collectors.toList());
            wordList.setItems(FXCollections.observableArrayList(sortedWords));
        };
        refreshList.run();
        
        // Eingabefeld für neues Wort
        HBox inputBox = new HBox(10);
        inputBox.setAlignment(Pos.CENTER_LEFT);
        TextField wordInput = new TextField();
        wordInput.setPromptText("Neues Wort eingeben...");
        wordInput.setPrefWidth(300);
        applyThemeToNode(wordInput, currentThemeIndex);
        
        Button btnAdd = new Button("Hinzufügen");
        btnAdd.getStyleClass().add("button");
        btnAdd.setOnAction(e -> {
            String word = wordInput.getText().trim();
            if (!word.isEmpty()) {
                languageToolDictionary.addWord(word);
                wordInput.clear();
                refreshList.run();
                // Prüfe erneut, um das Wort aus den Fehlern zu entfernen
                if (languageToolEnabled && codeArea != null) {
                    // WICHTIG: Filtere sofort die aktuellen Matches mit dem neuen Wörterbuch
                    String editorText = codeArea.getText();
                    if (editorText != null && !editorText.isEmpty()) {
                        // Erstelle eine komplett neue Liste mit gefilterten Matches
                        List<LanguageToolService.Match> filteredMatches = languageToolDictionary.filterMatches(
                            new ArrayList<>(currentLanguageToolMatches), 
                            editorText
                        );
                        
                        // WICHTIG: Ersetze die Liste komplett (nicht nur clear() + addAll())
                        currentLanguageToolMatches = new ArrayList<>(filteredMatches);
                        
                        // Aktualisiere Styling sofort
                        applyCombinedStyling();
                        updateLanguageToolStatus();
                    }
                    // Starte auch eine vollständige Neuprüfung im Hintergrund
                    checkLanguageToolErrors();
                }
                updateStatus("Wort zum Wörterbuch hinzugefügt: " + word);
            }
        });
        applyThemeToNode(btnAdd, currentThemeIndex);
        
        // Enter-Taste zum Hinzufügen
        wordInput.setOnAction(e -> btnAdd.fire());
        
        inputBox.getChildren().addAll(wordInput, btnAdd);
        applyThemeToNode(inputBox, currentThemeIndex);
        
        // Buttons
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        
        Button btnRemove = new Button("Entfernen");
        btnRemove.getStyleClass().add("button");
        btnRemove.setOnAction(e -> {
            String selected = wordList.getSelectionModel().getSelectedItem();
            if (selected != null) {
                languageToolDictionary.removeWord(selected);
                refreshList.run();
                // Prüfe erneut - Wort könnte jetzt wieder als Fehler markiert werden
                if (languageToolEnabled && codeArea != null) {
                    // Starte vollständige Neuprüfung
                    checkLanguageToolErrors();
                }
                updateStatus("Wort aus Wörterbuch entfernt: " + selected);
            }
        });
        applyThemeToNode(btnRemove, currentThemeIndex);
        
        Button btnClose = new Button("Schließen");
        btnClose.getStyleClass().add("button");
        btnClose.setOnAction(e -> dictStage.close());
        applyThemeToNode(btnClose, currentThemeIndex);
        
        buttonBox.getChildren().addAll(btnRemove, btnClose);
        applyThemeToNode(buttonBox, currentThemeIndex);
        
        // Status-Label
        Label statusLabel = new Label();
        statusLabel.textProperty().bind(
            javafx.beans.binding.Bindings.size(wordList.getItems())
                .asString("Anzahl Wörter: %d")
        );
        applyThemeToNode(statusLabel, currentThemeIndex);
        
        mainContent.getChildren().addAll(
            titleLabel,
            new Separator(),
            infoLabel,
            wordList,
            inputBox,
            statusLabel,
            buttonBox
        );
        
        Scene scene = new Scene(mainContent);
        scene.setFill(javafx.scene.paint.Color.web(THEMES[currentThemeIndex][0]));
        String cssPath = ResourceManager.getCssResource("css/editor.css");
        if (cssPath != null) {
            scene.getStylesheets().add(cssPath);
        }
        // CSS auch für Theme-Klassen
        String manuskriptCssPath = ResourceManager.getCssResource("css/manuskript.css");
        if (manuskriptCssPath != null) {
            scene.getStylesheets().add(manuskriptCssPath);
        }
        
        dictStage.setSceneWithTitleBar(scene);
        
        dictStage.show();
    }
    
    /**
     * Stylt ein Kontextmenü mit dem aktuellen Theme (nur CSS-Klassen, keine inline Styles)
     */
    private void styleContextMenu(ContextMenu contextMenu, int themeIndex) {
        if (contextMenu == null) return;
        
        // CSS-Klassen für Theme-spezifisches Styling
        contextMenu.getStyleClass().removeAll("theme-dark", "theme-light", "weiss-theme", "pastell-theme", "blau-theme", "gruen-theme", "lila-theme");
        if (themeIndex == 0) {
            contextMenu.getStyleClass().add("weiss-theme");
        } else if (themeIndex == 1) {
            contextMenu.getStyleClass().add("theme-dark");
        } else if (themeIndex == 2) {
            contextMenu.getStyleClass().add("pastell-theme");
        } else if (themeIndex == 3) {
            contextMenu.getStyleClass().addAll("theme-dark", "blau-theme");
        } else if (themeIndex == 4) {
            contextMenu.getStyleClass().addAll("theme-dark", "gruen-theme");
        } else if (themeIndex == 5) {
            contextMenu.getStyleClass().addAll("theme-dark", "lila-theme");
        }
    }
    
    /**
     * Findet die Grenzen des aktuellen Satzes an der Cursorposition
     */
    private int[] findCurrentSentenceBounds(int caretPosition) {
        if (codeArea == null) return null;
        
        String text = codeArea.getText();
        if (text == null || text.isEmpty()) return null;
        
        if (caretPosition >= text.length()) {
            caretPosition = text.length() - 1;
        }
        if (caretPosition < 0) {
            caretPosition = 0;
        }
        
        // Finde alle Anführungszeichen-Paare, um zu prüfen ob wir innerhalb von Anführungszeichen sind
        Pattern quotePattern = Pattern.compile("[\"\\u201E\\u201C\\u201D\\u00BB\\u00AB]");
        Matcher quoteMatcher = quotePattern.matcher(text);
        List<Integer> quotePositions = new ArrayList<>();
        while (quoteMatcher.find()) {
            quotePositions.add(quoteMatcher.start());
        }
        
        // Prüfe ob Cursor innerhalb von Anführungszeichen ist
        boolean insideQuotes = false;
        for (int i = 0; i < quotePositions.size() - 1; i += 2) {
            int startQuote = quotePositions.get(i);
            int endQuote = quotePositions.get(i + 1);
            if (caretPosition > startQuote && caretPosition <= endQuote) {
                insideQuotes = true;
                break;
            }
        }
        
        // Finde Satzanfang (rückwärts suchen nach Satzendezeichen)
        int sentenceStart = caretPosition;
        while (sentenceStart > 0) {
            char c = text.charAt(sentenceStart - 1);
            
            // Prüfe ob wir innerhalb von Anführungszeichen sind
            boolean charInsideQuotes = false;
            for (int i = 0; i < quotePositions.size() - 1; i += 2) {
                int startQuote = quotePositions.get(i);
                int endQuote = quotePositions.get(i + 1);
                if (sentenceStart - 1 > startQuote && sentenceStart - 1 <= endQuote) {
                    charInsideQuotes = true;
                    break;
                }
            }
            
            // Wenn innerhalb von Anführungszeichen, überspringe Satzendezeichen
            if (charInsideQuotes) {
                sentenceStart--;
                continue;
            }
            
            if (c == '.' || c == '!' || c == '?' || c == '\n') {
                // Prüfe ob es wirklich ein Satzende ist (nicht z.B. "Dr." oder "z.B.")
                if (sentenceStart > 1) {
                    char prev = text.charAt(sentenceStart - 2);
                    if (Character.isLetter(prev) && c == '.') {
                        // Möglicherweise Abkürzung, weiter suchen
                        sentenceStart--;
                        continue;
                    }
                }
                break;
            }
            sentenceStart--;
        }
        
        // Finde Satzende (vorwärts suchen)
        int sentenceEnd = caretPosition;
        while (sentenceEnd < text.length()) {
            char c = text.charAt(sentenceEnd);
            
            // Prüfe ob wir innerhalb von Anführungszeichen sind
            boolean charInsideQuotes = false;
            for (int i = 0; i < quotePositions.size() - 1; i += 2) {
                int startQuote = quotePositions.get(i);
                int endQuote = quotePositions.get(i + 1);
                if (sentenceEnd >= startQuote && sentenceEnd < endQuote) {
                    charInsideQuotes = true;
                    break;
                }
            }
            
            // Wenn innerhalb von Anführungszeichen, überspringe Satzendezeichen
            if (charInsideQuotes) {
                sentenceEnd++;
                continue;
            }
            
            if (c == '.' || c == '!' || c == '?' || c == '\n') {
                sentenceEnd++;
                break;
            }
            sentenceEnd++;
        }
        
        // Trimme Whitespace am Anfang
        while (sentenceStart < sentenceEnd && sentenceStart < text.length() && 
               Character.isWhitespace(text.charAt(sentenceStart))) {
            sentenceStart++;
        }
        
        if (sentenceStart < sentenceEnd && sentenceStart < text.length()) {
            return new int[]{sentenceStart, sentenceEnd};
        }
        
        return null;
    }
    
    /**
     * Findet die Grenzen des aktuellen Absatzes an der Cursorposition
     */
    private int[] findCurrentParagraphBounds(int caretPosition) {
        if (codeArea == null) return null;
        
        String text = codeArea.getText();
        if (text == null || text.isEmpty()) return null;
        
        if (caretPosition >= text.length()) {
            caretPosition = text.length() - 1;
        }
        if (caretPosition < 0) {
            caretPosition = 0;
        }
        
        // Finde Absatzanfang (rückwärts nach doppeltem Zeilenumbruch)
        int paragraphStart = caretPosition;
        while (paragraphStart > 0) {
            if (paragraphStart >= 2 && text.charAt(paragraphStart - 1) == '\n' && 
                text.charAt(paragraphStart - 2) == '\n') {
                paragraphStart--;
                break;
            }
            paragraphStart--;
        }
        
        // Finde Absatzende (vorwärts nach doppeltem Zeilenumbruch)
        int paragraphEnd = caretPosition;
        while (paragraphEnd < text.length()) {
            if (paragraphEnd < text.length() - 1 && text.charAt(paragraphEnd) == '\n' && 
                text.charAt(paragraphEnd + 1) == '\n') {
                break;
            }
            paragraphEnd++;
        }
        
        // Trimme Whitespace
        while (paragraphStart < paragraphEnd && paragraphStart < text.length() && 
               Character.isWhitespace(text.charAt(paragraphStart))) {
            paragraphStart++;
        }
        while (paragraphEnd > paragraphStart && paragraphEnd > 0 && 
               Character.isWhitespace(text.charAt(paragraphEnd - 1))) {
            paragraphEnd--;
        }
        
        if (paragraphStart < paragraphEnd && paragraphStart < text.length()) {
            return new int[]{paragraphStart, paragraphEnd};
        }
        
        return null;
    }
    
    /**
     * Sucht nach Sprechantworten im Text (sagte er, fragte sie, etc.)
     */
    private boolean containsSpeechTag(String text) {
        if (text == null || text.isEmpty()) return false;
        
        try {
            // Lade Sprechantworten aus textanalysis.properties
            Properties props = loadTextAnalysisProperties();
            String sprechwoerter = props.getProperty("sprechwörter", "");
            String phrasenDialog = props.getProperty("phrasen_dialog", "");
            
            // Kombiniere beide Listen
            List<String> allTags = new ArrayList<>();
            
            // Sprechwörter hinzufügen
            if (!sprechwoerter.isEmpty()) {
                String[] woerter = sprechwoerter.split(",");
                for (String wort : woerter) {
                    String trimmed = wort.trim();
                    if (!trimmed.isEmpty()) {
                        allTags.add(trimmed);
                        // Auch mit Pronomen kombinieren
                        allTags.add(trimmed + " er");
                        allTags.add(trimmed + " sie");
                        allTags.add(trimmed + " er.");
                        allTags.add(trimmed + " sie.");
                        allTags.add(trimmed + " er,");
                        allTags.add(trimmed + " sie,");
                    }
                }
            }
            
            // Phrasen-Dialog hinzufügen
            if (!phrasenDialog.isEmpty()) {
                String[] phrasen = phrasenDialog.split(",");
                for (String phrase : phrasen) {
                    String trimmed = phrase.trim();
                    if (!trimmed.isEmpty()) {
                        allTags.add(trimmed);
                        allTags.add(trimmed + ".");
                        allTags.add(trimmed + ",");
                    }
                }
            }
            
            // Fallback-Liste falls Properties leer sind
            if (allTags.isEmpty()) {
                allTags.add("sagte er");
                allTags.add("sagte sie");
                allTags.add("fragte er");
                allTags.add("fragte sie");
                allTags.add("sagte");
                allTags.add("fragte");
            }
            
            // Normalisiere Text für Suche
            String lowerText = text.toLowerCase();
            
            // Suche nach allen Tags
            for (String tag : allTags) {
                String lowerTag = tag.toLowerCase();
                // Einfache contains-Suche
                if (lowerText.contains(lowerTag)) {
                    return true;
                }
            }
            
            // Zusätzliche Regex-Suche für Muster wie "sagte er", "fragte sie" etc.
            Pattern speechPattern = Pattern.compile(
                "\\b(sagte|fragte|rief|antwortete|erwiderte|meinte|flüsterte|brüllte|stammelte|murmelte|erklärte|berichtete|erzählte|bemerkte|kommentierte|behauptete|versicherte|warnte|vermutete|leugnete|versprach|schwor|informierte|mitteilte|diskutierte|debattierte|argumentierte|streitete|besprach|plauderte|schwatzte|raunte|schrie|heulte|weinte|lachte|grinste|seufzte|stöhnte|ächzte|wimmerte|schluchzte|keuchte|stotterte|fluchte|schimpfte|donnerte|knurrte|fauchte|zischte|brummte|summte|pfiff|trällerte|sang|deklamierte|rezitierte|sprach|redete|plapperte|schwadronierte|faselte|laberte|quasselte|schwätzte|quatschte|konversierte)" +
                "\\s+(er|sie|es|ich|du|wir|ihr|sie|man|jemand|niemand)\\b",
                Pattern.CASE_INSENSITIVE
            );
            
            return speechPattern.matcher(text).find();
            
        } catch (Exception e) {
            logger.debug("Fehler beim Laden der Sprechantworten: {}", e.getMessage());
            // Fallback: einfache Suche
            String lowerText = text.toLowerCase();
            return lowerText.contains("sagte") || lowerText.contains("fragte") || 
                   lowerText.contains("rief") || lowerText.contains("antwortete") ||
                   lowerText.contains("erwiderte") || lowerText.contains("meinte");
        }
    }
    
    /**
     * Handler für "Sprechantwort korrigieren"
     */
    private void handleSprechantwortKorrektur() {
        if (codeArea == null) return;
        
        int caretPos = codeArea.getCaretPosition();
        int[] bounds = findCurrentSentenceBounds(caretPos);
        
        if (bounds == null) {
            updateStatus("Kein Satz an der Cursorposition gefunden.");
            return;
        }
        
        String sentence = codeArea.getText(bounds[0], bounds[1]);
        
        if (!containsSpeechTag(sentence)) {
            updateStatus("Kein Sprechantwort im aktuellen Satz gefunden.");
            return;
        }
        
        // Markiere den Satz
        codeArea.selectRange(bounds[0], bounds[1]);
        
        // Öffne Dialog
        showSprechantwortKorrekturDialog(sentence, bounds[0], bounds[1]);
    }
    
    /**
     * Zeigt den Dialog für Sprechantwort-Korrektur
     */
    private void showSprechantwortKorrekturDialog(String originalSentence, int startPos, int endPos) {
        CustomStage dialogStage = StageManager.createModalStage("Sprechantwort korrigieren", stage);
        dialogStage.setTitle("Sprechantwort korrigieren");
        dialogStage.setWidth(900);
        dialogStage.setHeight(700);
        dialogStage.setTitleBarTheme(currentThemeIndex);
        
        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.getStyleClass().add("dialog-container");
        applyThemeToNode(root, currentThemeIndex);
        
        // Original-Satz
        Label originalLabel = new Label("Original:");
        originalLabel.getStyleClass().add("dialog-label");
        applyThemeToNode(originalLabel, currentThemeIndex);
        
        TextArea originalArea = new TextArea(originalSentence);
        originalArea.setEditable(false);
        originalArea.setPrefRowCount(2);
        originalArea.setWrapText(true);
        originalArea.getStyleClass().add("dialog-text-area");
        applyThemeToNode(originalArea, currentThemeIndex);
        
        // Anweisungsfeld (optional)
        Label instructionLabel = new Label("Anweisung (optional):");
        instructionLabel.getStyleClass().add("dialog-label");
        applyThemeToNode(instructionLabel, currentThemeIndex);
        
        TextField instructionField = new TextField();
        instructionField.setPromptText("z.B. Kata spricht und ist angespannt.");
        applyThemeToNode(instructionField, currentThemeIndex);
        
        // Kreativitäts-Slider
        Label creativityLabel = new Label("Kreativität:");
        creativityLabel.getStyleClass().add("dialog-label");
        applyThemeToNode(creativityLabel, currentThemeIndex);
        
        Slider creativitySlider = new Slider(0.0, 1.0, 0.4);
        creativitySlider.setShowTickLabels(true);
        creativitySlider.setShowTickMarks(true);
        creativitySlider.setMajorTickUnit(0.25);
        creativitySlider.setMinorTickCount(0);
        creativitySlider.setSnapToTicks(false);
        creativitySlider.setPrefWidth(400);
        
        Label creativityValueLabel = new Label("0.40");
        creativityValueLabel.setMinWidth(40);
        creativityValueLabel.setStyle(String.format("-fx-text-fill: %s;", THEMES[currentThemeIndex][1]));
        
        creativitySlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            creativityValueLabel.setText(String.format("%.2f", newVal.doubleValue()));
        });
        
        HBox creativityBox = new HBox(10);
        creativityBox.setAlignment(Pos.CENTER_LEFT);
        creativityBox.getChildren().addAll(creativitySlider, creativityValueLabel);
        
        // Länge-Auswahl
        Label lengthLabel = new Label("Länge:");
        lengthLabel.getStyleClass().add("dialog-label");
        applyThemeToNode(lengthLabel, currentThemeIndex);
        
        ComboBox<String> lengthComboBox = new ComboBox<>();
        lengthComboBox.getItems().addAll("Kurz", "Ausführlich");
        lengthComboBox.setValue("Ausführlich");
        lengthComboBox.setPrefWidth(200);
        applyThemeToNode(lengthComboBox, currentThemeIndex);
        
        HBox lengthBox = new HBox(10);
        lengthBox.setAlignment(Pos.CENTER_LEFT);
        lengthBox.getChildren().addAll(lengthComboBox);
        
        // Antworten-Bereich
        Label answersLabel = new Label("Vorschläge:");
        answersLabel.getStyleClass().add("dialog-label");
        applyThemeToNode(answersLabel, currentThemeIndex);
        
        // Fortschrittsbalken (zunächst unsichtbar)
        ProgressBar progressBar = new ProgressBar();
        progressBar.setVisible(false);
        progressBar.setManaged(false); // Wird nur verwaltet wenn sichtbar
        progressBar.setPrefWidth(Double.MAX_VALUE);
        progressBar.setPrefHeight(20);
        progressBar.setMinHeight(20);
        progressBar.setMaxHeight(20);
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        applyThemeToNode(progressBar, currentThemeIndex);
        
        VBox answersBox = new VBox(10);
        ScrollPane scrollPane = new ScrollPane(answersBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(300);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        applyThemeToNode(scrollPane, currentThemeIndex);
        
        // Buttons
        HBox buttonBox = new HBox(10);
        Button btnGenerate = new Button("Generieren");
        btnGenerate.getStyleClass().add("button");
        applyThemeToNode(btnGenerate, currentThemeIndex);
        
        Button btnCancel = new Button("Abbrechen");
        btnCancel.getStyleClass().add("button");
        applyThemeToNode(btnCancel, currentThemeIndex);
        
        buttonBox.getChildren().addAll(btnGenerate, btnCancel);
        
        root.getChildren().addAll(originalLabel, originalArea, instructionLabel, instructionField, 
                                 creativityLabel, creativityBox, lengthLabel, lengthBox, answersLabel, progressBar, scrollPane, buttonBox);
        
        Scene scene = new Scene(root);
        scene.setFill(javafx.scene.paint.Color.web(THEMES[currentThemeIndex][0]));
        String cssPath = ResourceManager.getCssResource("css/editor.css");
        if (cssPath != null) {
            scene.getStylesheets().add(cssPath);
        }
        // CSS auch für Theme-Klassen
        String manuskriptCssPath = ResourceManager.getCssResource("css/manuskript.css");
        if (manuskriptCssPath != null) {
            scene.getStylesheets().add(manuskriptCssPath);
        }
        
        dialogStage.setSceneWithTitleBar(scene);
        
        // Generiere Antworten
        btnGenerate.setOnAction(e -> {
            btnGenerate.setDisable(true);
            btnGenerate.setText("Generiere...");
            progressBar.setVisible(true);
            progressBar.setManaged(true);
            answersBox.getChildren().clear();
            
            String instruction = instructionField.getText().trim();
            double creativity = creativitySlider.getValue();
            String length = lengthComboBox.getValue();
            generateSprechantwortAlternatives(originalSentence, instruction, creativity, length, answersBox, 
                                             btnGenerate, startPos, endPos, dialogStage, progressBar);
        });
        
        btnCancel.setOnAction(e -> dialogStage.close());
        
        dialogStage.showAndWait();
    }
    
    /**
     * Generiert alternative Absatz-Versionen mit Ollama
     */
    private void generateAbsatzAlternatives(String originalParagraph, String instruction, double creativity,
                                           VBox answersBox, Button btnGenerate,
                                           int startPos, int endPos, CustomStage dialogStage, ProgressBar progressBar) {
        // Lösche alte Antworten
        answersBox.getChildren().clear();
        
        // Lade Kontexte
        String characters = "";
        String synopsis = "";
        String outline = "";
        String worldbuilding = "";
        String style = "";
        String chapterText = "";
        
        if (originalDocxFile != null) {
            // Lade alle Dateien direkt aus dem Projektverzeichnis
            File projectDir = originalDocxFile.getParentFile();
            if (projectDir != null) {
                // Lade characters.txt
                File charactersFile = new File(projectDir, "characters.txt");
                if (charactersFile.exists()) {
                    try {
                        characters = new String(java.nio.file.Files.readAllBytes(charactersFile.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                    } catch (Exception e) {
                        logger.warn("Fehler beim Laden von characters.txt: " + e.getMessage());
                    }
                }
                
                // Lade synopsis.txt
                File synopsisFile = new File(projectDir, "synopsis.txt");
                if (synopsisFile.exists()) {
                    try {
                        synopsis = new String(java.nio.file.Files.readAllBytes(synopsisFile.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                    } catch (Exception e) {
                        logger.warn("Fehler beim Laden von synopsis.txt: " + e.getMessage());
                    }
                }
                
                // Lade outline.txt
                File outlineFile = new File(projectDir, "outline.txt");
                if (outlineFile.exists()) {
                    try {
                        outline = new String(java.nio.file.Files.readAllBytes(outlineFile.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                    } catch (Exception e) {
                        logger.warn("Fehler beim Laden von outline.txt: " + e.getMessage());
                    }
                }
                
                // Lade worldbuilding.txt
                File worldbuildingFile = new File(projectDir, "worldbuilding.txt");
                if (worldbuildingFile.exists()) {
                    try {
                        worldbuilding = new String(java.nio.file.Files.readAllBytes(worldbuildingFile.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                    } catch (Exception e) {
                        logger.warn("Fehler beim Laden von worldbuilding.txt: " + e.getMessage());
                    }
                }
                
                // Lade style.txt
                File styleFile = new File(projectDir, "style.txt");
                if (styleFile.exists()) {
                    try {
                        style = new String(java.nio.file.Files.readAllBytes(styleFile.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                    } catch (Exception e) {
                        logger.warn("Fehler beim Laden von style.txt: " + e.getMessage());
                    }
                }
            }
        }
        
        // Lade den gesamten Kapiteltext als Kontext
        if (codeArea != null) {
            chapterText = codeArea.getText();
            if (chapterText != null && chapterText.trim().isEmpty()) {
                chapterText = "";
            }
        }
        
        // Baue Kontext
        StringBuilder contextBuilder = new StringBuilder();
        if (characters != null && !characters.trim().isEmpty()) {
            contextBuilder.append("=== CHARAKTERE ===\n").append(characters).append("\n\n");
        }
        if (synopsis != null && !synopsis.trim().isEmpty()) {
            contextBuilder.append("=== SYNOPSIS ===\n").append(synopsis).append("\n\n");
        }
        if (outline != null && !outline.trim().isEmpty()) {
            contextBuilder.append("=== OUTLINE ===\n").append(outline).append("\n\n");
        }
        if (worldbuilding != null && !worldbuilding.trim().isEmpty()) {
            contextBuilder.append("=== WORLDBUILDING ===\n").append(worldbuilding).append("\n\n");
        }
        if (style != null && !style.trim().isEmpty()) {
            contextBuilder.append("=== STIL ===\n").append(style).append("\n\n");
        }
        if (chapterText != null && !chapterText.trim().isEmpty()) {
            contextBuilder.append("=== KAPITEL-TEXT ===\n").append(chapterText).append("\n\n");
        }
        
        String context = contextBuilder.toString();
        
        // Baue Prompt für Absatz-Überarbeitung - vereinfacht und fokussiert wie kritisches Lektorat
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("Du agierst als sehr erfahrener, kritischer deutscher Lektor. ");
        promptBuilder.append("Du analysierst den gegebenen Text ohne Schonung und nutzt alle Register eines professionellen Lektorats ");
        promptBuilder.append("(orthografische Präzision, stilistische Wirkung, Logikprüfung, Kohärenz, Tonalität, Figurenzeichnung, Tempo, Szenendramaturgie). ");
        promptBuilder.append("Arbeite strukturiert und detailliert.\n\n");
        
        promptBuilder.append("**Zu überarbeitender Absatz:**\n").append(originalParagraph).append("\n\n");
        
        // Anweisung ist PRIORITÄT - wenn vorhanden, wird sie explizit befolgt
        if (!instruction.isEmpty()) {
            promptBuilder.append("**WICHTIG - SPEZIFISCHE ANWEISUNG (diese hat PRIORITÄT):**\n");
            promptBuilder.append(instruction).append("\n\n");
            promptBuilder.append("**Aufgabe:**\n");
            promptBuilder.append("Überarbeite diesen Absatz GENAU nach der obigen spezifischen Anweisung. ");
            promptBuilder.append("Die Anweisung ist verbindlich und muss vollständig umgesetzt werden. ");
            promptBuilder.append("Zusätzlich muss der überarbeitete Absatz grammatisch korrekt, idiomatisch richtig und stilistisch hochwertig sein.\n\n");
        } else {
            promptBuilder.append("**Aufgabe:**\n");
            promptBuilder.append("Überarbeite diesen Absatz: Verbessere Stil, Klarheit, Lebendigkeit und Wirkung. ");
            promptBuilder.append("Der überarbeitete Absatz muss grammatisch korrekt, idiomatisch richtig und stilistisch hochwertig sein.\n\n");
        }
        
        promptBuilder.append("**Vorgehen:**\n");
        promptBuilder.append("Gib 3-5 alternative Versionen des überarbeiteten Absatzes, jede in einer eigenen Zeile.\n");
        promptBuilder.append("Jede Version muss grammatisch korrekt, idiomatisch richtig und stilistisch hochwertig sein.\n");
        if (!instruction.isEmpty()) {
            promptBuilder.append("Jede Version muss die spezifische Anweisung vollständig erfüllen.\n");
        }
        promptBuilder.append("Nur der überarbeitete Absatz, keine Erklärungen, keine Nummerierungen.");
        
        String prompt = promptBuilder.toString();
        
        // Ollama-Service verwenden
        OllamaService ollamaService = new OllamaService();
        
        // Prüfe verfügbare Modelle und verwende das gewünschte Modell oder ein Fallback
        String targetModel = "jobautomation/OpenEuroLLM-German";
        Label statusLabel = new Label("Lade verfügbare Modelle...");
        statusLabel.setStyle(String.format("-fx-text-fill: %s;", THEMES[currentThemeIndex][1]));
        answersBox.getChildren().add(statusLabel);
        
        ollamaService.getAvailableModels().thenAccept(models -> {
            Platform.runLater(() -> {
                String modelToUse = null;
                
                // Prüfe ob das gewünschte Modell verfügbar ist (exakte Übereinstimmung oder ähnlich)
                for (String model : models) {
                    if (model.equals(targetModel) || 
                        model.equalsIgnoreCase(targetModel) ||
                        model.toLowerCase().contains("openeurollm") ||
                        (model.toLowerCase().contains("german") && model.toLowerCase().contains("openeuro"))) {
                        modelToUse = model;
                        break;
                    }
                }
                
                // Falls nicht verfügbar, suche nach ähnlichen Modellnamen
                if (modelToUse == null && models.length > 0) {
                    // Suche nach Modellen mit "german" oder "openeuro"
                    for (String model : models) {
                        String lowerModel = model.toLowerCase();
                        if (lowerModel.contains("german") || lowerModel.contains("openeuro")) {
                            modelToUse = model;
                            logger.info("Verwende ähnliches Modell: '{}' statt '{}'", model, targetModel);
                            break;
                        }
                    }
                    
                    // Falls immer noch nichts gefunden, verwende das erste verfügbare Modell
                    if (modelToUse == null) {
                        modelToUse = models[0];
                        logger.warn("Modell '{}' nicht gefunden, verwende '{}'", targetModel, modelToUse);
                        updateStatus("Modell '" + targetModel + "' nicht gefunden, verwende '" + modelToUse + "'");
                    } else {
                        updateStatus("Verwende Modell: " + modelToUse);
                    }
                }
                
                if (modelToUse == null) {
                    answersBox.getChildren().clear();
                    Label errorLabel = new Label("Fehler: Keine Modelle verfügbar!");
                    errorLabel.setStyle(String.format("-fx-text-fill: red;"));
                    answersBox.getChildren().add(errorLabel);
                    btnGenerate.setDisable(false);
                    btnGenerate.setText("Generieren");
                    progressBar.setVisible(false);
                    progressBar.setManaged(false);
                    return;
                }
                
                ollamaService.setModel(modelToUse);
                answersBox.getChildren().clear();
                
                // Verwende Kreativitäts-Slider-Wert als Temperatur (0.0-1.0)
                double temperature = creativity;
                
                // Debug: Logge die verwendete Temperatur
                logger.debug("Verwende Temperatur: {}", temperature);
                
                ollamaService.generateText(prompt, context, temperature, ollamaService.getMaxTokens(), ollamaService.getTopP(), ollamaService.getRepeatPenalty())
                    .thenAccept(response -> {
                        Platform.runLater(() -> {
                            if (response != null && !response.trim().isEmpty()) {
                                // Teile die Antwort in Zeilen auf (jede Zeile ist eine Variante)
                                String[] variants = response.trim().split("\n");
                                
                                // Filtere leere Zeilen und nummerierte Listen
                                List<String> cleanVariants = new ArrayList<>();
                                for (String variant : variants) {
                                    String cleaned = variant.trim();
                                    // Entferne Nummerierungen wie "1. ", "2. ", "- ", etc.
                                    cleaned = cleaned.replaceAll("^\\d+\\.\\s*", "")
                                                     .replaceAll("^-\\s*", "")
                                                     .replaceAll("^\\*\\s*", "")
                                                     .trim();
                                    if (!cleaned.isEmpty() && cleaned.length() > 10) { // Mindestens 10 Zeichen für Absatz
                                        cleanVariants.add(cleaned);
                                    }
                                }
                                
                                // Falls keine Varianten gefunden, verwende die gesamte Antwort
                                if (cleanVariants.isEmpty()) {
                                    cleanVariants.add(response.trim());
                                }
                                
                                // Erstelle UI-Elemente für jede Variante
                                for (int i = 0; i < cleanVariants.size() && i < 5; i++) {
                                    final int index = i;
                                    String variant = cleanVariants.get(i);
                                    
                                    // Erstelle Button für diese Variante
                                    Button answerBtn = new Button("Variante " + (index + 1));
                                    answerBtn.setMaxWidth(Double.MAX_VALUE);
                                    answerBtn.setAlignment(Pos.CENTER_LEFT);
                                    answerBtn.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: %s;",
                                        THEMES[currentThemeIndex][2], THEMES[currentThemeIndex][1]));
                                    
                                    TextArea answerText = new TextArea(variant);
                                    answerText.setEditable(false);
                                    answerText.setWrapText(true);
                                    answerText.setPrefRowCount(6);
                                    answerText.getStyleClass().add("dialog-text-area");
                                    applyThemeToNode(answerText, currentThemeIndex);
                                    
                                    VBox variantBox = new VBox(5);
                                    variantBox.getChildren().addAll(answerBtn, answerText);
                                    applyThemeToNode(variantBox, currentThemeIndex);
                                    
                                    answerBtn.setOnAction(e -> {
                                        try {
                                            // Prüfe ob der Text sich geändert hat
                                            String currentText = codeArea.getText();
                                            if (currentText != null && startPos < currentText.length() && endPos <= currentText.length()) {
                                                String textAtPosition = currentText.substring(startPos, Math.min(endPos, currentText.length()));
                                                if (!textAtPosition.trim().equals(originalParagraph.trim())) {
                                                    // Text hat sich geändert, finde den Absatz neu
                                                    int caretPos = codeArea.getCaretPosition();
                                                    int[] newBounds = findCurrentParagraphBounds(caretPos);
                                                    
                                                    if (newBounds != null) {
                                                        String newParagraph = codeArea.getText(newBounds[0], newBounds[1]);
                                                        if (newParagraph.trim().equals(originalParagraph.trim())) {
                                                            // Verwende die neuen Positionen
                                                            codeArea.replaceText(newBounds[0], newBounds[1], variant);
                                                        } else {
                                                            updateStatus("Fehler: Absatz nicht mehr gefunden.");
                                                            return;
                                                        }
                                                    } else {
                                                        updateStatus("Fehler: Absatz nicht mehr gefunden.");
                                                        return;
                                                    }
                                                } else {
                                                    // Positionen sind noch korrekt, ersetze direkt
                                                    codeArea.replaceText(startPos, endPos, variant);
                                                }
                                                
                                                dialogStage.close();
                                                updateStatus("Absatz ersetzt.");
                                            }
                                        } catch (Exception ex) {
                                            logger.error("Fehler beim Ersetzen: {}", ex.getMessage());
                                            updateStatus("Fehler beim Ersetzen: " + ex.getMessage());
                                        }
                                    });
                                    
                                    answersBox.getChildren().add(variantBox);
                                }
                            }
                            
                            btnGenerate.setDisable(false);
                            btnGenerate.setText("Generieren");
                            progressBar.setVisible(false);
                            progressBar.setManaged(false);
                        });
                    })
                    .exceptionally(throwable -> {
                        Platform.runLater(() -> {
                            logger.error("Fehler bei Ollama-Generierung: {}", throwable.getMessage());
                            updateStatus("Fehler bei der Generierung: " + throwable.getMessage());
                            
                            Label errorLabel = new Label("Fehler: " + throwable.getMessage());
                            errorLabel.setStyle(String.format("-fx-text-fill: red;"));
                            answersBox.getChildren().add(errorLabel);
                            
                            btnGenerate.setDisable(false);
                            btnGenerate.setText("Generieren");
                            progressBar.setVisible(false);
                            progressBar.setManaged(false);
                        });
                        return null;
                    });
            });
        });
    }
    
    /**
     * Analysiert einen Satz und extrahiert die Struktur: Text vor, wörtliche Rede, Sprechantwort
     */
    private String[] analyzeSentenceStructure(String sentence) {
        // Struktur: [textBefore, quotedText, speechTag, textAfter]
        String[] result = new String[4];
        result[0] = ""; // Text vor
        result[1] = ""; // Wörtliche Rede (inkl. Anführungszeichen)
        result[2] = ""; // Sprechantwort
        result[3] = ""; // Text nach
        
        // Suche nach Anführungszeichen (verschiedene Typen)
        // Unicode: " = U+0022, „ = U+201E, " = U+201C, " = U+201D, » = U+00BB, « = U+00AB
        Pattern quotePattern = Pattern.compile("[\"\\u201E\\u201C\\u201D\\u00BB\\u00AB]");
        Matcher quoteMatcher = quotePattern.matcher(sentence);
        
        int firstQuotePos = -1;
        int secondQuotePos = -1;
        
        // Finde alle Anführungszeichen
        List<Integer> quotePositions = new ArrayList<>();
        while (quoteMatcher.find()) {
            quotePositions.add(quoteMatcher.start());
        }
        
        if (quotePositions.size() >= 2) {
            // Nimm das erste und das letzte Anführungszeichen
            firstQuotePos = quotePositions.get(0);
            secondQuotePos = quotePositions.get(quotePositions.size() - 1);
        } else if (quotePositions.size() == 1) {
            // Nur ein Anführungszeichen gefunden - verwende es als Start
            firstQuotePos = quotePositions.get(0);
        }
        
        if (firstQuotePos >= 0 && secondQuotePos > firstQuotePos) {
            // Wörtliche Rede gefunden - einfach alles zwischen den Anführungszeichen nehmen
            result[0] = sentence.substring(0, firstQuotePos).trim();
            result[1] = sentence.substring(firstQuotePos, secondQuotePos + 1); // Mit Anführungszeichen
            String afterQuote = sentence.substring(secondQuotePos + 1).trim();
            
            // Suche nach Sprechantwort nach der wörtlichen Rede (mit Pronomen ODER Namen)
            Pattern speechPattern = Pattern.compile(
                "\\b(sagte|fragte|rief|antwortete|erwiderte|meinte|flüsterte|brüllte|stammelte|murmelte|erklärte|berichtete|erzählte|bemerkte|kommentierte|behauptete|versicherte|warnte|vermutete|leugnete|versprach|schwor|informierte|mitteilte|diskutierte|debattierte|argumentierte|streitete|besprach|plauderte|schwatzte|raunte|schrie|heulte|weinte|lachte|grinste|seufzte|stöhnte|ächzte|wimmerte|schluchzte|keuchte|stotterte|fluchte|schimpfte|donnerte|knurrte|fauchte|zischte|brummte|summte|pfiff|trällerte|sang|deklamierte|rezitierte|sprach|redete|plapperte|schwadronierte|faselte|laberte|quasselte|schwätzte|quatschte|konversierte)" +
                "\\s+((?:er|sie|es|ich|du|wir|ihr|sie|man|jemand|niemand)\\b|[A-ZÄÖÜ][a-zäöüß]+)",
                Pattern.CASE_INSENSITIVE
            );
            
            // Entferne führendes Komma für die Suche
            String searchText = afterQuote.replaceFirst("^,\\s*", "").trim();
            
            Matcher speechMatcher = speechPattern.matcher(searchText);
            if (speechMatcher.find()) {
                int speechStart = speechMatcher.start();
                int speechEnd = speechMatcher.end();
                // Berechne die tatsächliche Position im Original-Text
                int actualStart = afterQuote.length() - searchText.length() + speechStart;
                int actualEnd = afterQuote.length() - searchText.length() + speechEnd;
                result[2] = afterQuote.substring(actualStart, actualEnd);
                result[3] = afterQuote.substring(actualEnd).trim();
            } else {
                // Fallback: suche ohne Wortgrenze (mit Pronomen ODER Namen)
                Pattern speechPatternNoBoundary = Pattern.compile(
                    "(sagte|fragte|rief|antwortete|erwiderte|meinte|flüsterte|brüllte|stammelte|murmelte|erklärte|berichtete|erzählte|bemerkte|kommentierte|behauptete|versicherte|warnte|vermutete|leugnete|versprach|schwor|informierte|mitteilte|diskutierte|debattierte|argumentierte|streitete|besprach|plauderte|schwatzte|raunte|schrie|heulte|weinte|lachte|grinste|seufzte|stöhnte|ächzte|wimmerte|schluchzte|keuchte|stotterte|fluchte|schimpfte|donnerte|knurrte|fauchte|zischte|brummte|summte|pfiff|trällerte|sang|deklamierte|rezitierte|sprach|redete|plapperte|schwadronierte|faselte|laberte|quasselte|schwätzte|quatschte|konversierte)" +
                    "\\s+((?:er|sie|es|ich|du|wir|ihr|sie|man|jemand|niemand)\\b|[A-ZÄÖÜ][a-zäöüß]+)",
                    Pattern.CASE_INSENSITIVE
                );
                Matcher noBoundaryMatcher = speechPatternNoBoundary.matcher(afterQuote);
                if (noBoundaryMatcher.find()) {
                    int speechStart = noBoundaryMatcher.start();
                    int speechEnd = noBoundaryMatcher.end();
                    result[2] = afterQuote.substring(speechStart, speechEnd);
                    result[3] = afterQuote.substring(speechEnd).trim();
                }
            }
        } else {
            // Keine wörtliche Rede gefunden, suche nur nach Sprechantwort (mit Pronomen ODER Namen)
            Pattern speechPattern = Pattern.compile(
                "\\b(sagte|fragte|rief|antwortete|erwiderte|meinte|flüsterte|brüllte|stammelte|murmelte|erklärte|berichtete|erzählte|bemerkte|kommentierte|behauptete|versicherte|warnte|vermutete|leugnete|versprach|schwor|informierte|mitteilte|diskutierte|debattierte|argumentierte|streitete|besprach|plauderte|schwatzte|raunte|schrie|heulte|weinte|lachte|grinste|seufzte|stöhnte|ächzte|wimmerte|schluchzte|keuchte|stotterte|fluchte|schimpfte|donnerte|knurrte|fauchte|zischte|brummte|summte|pfiff|trällerte|sang|deklamierte|rezitierte|sprach|redete|plapperte|schwadronierte|faselte|laberte|quasselte|schwätzte|quatschte|konversierte)" +
                "\\s+((?:er|sie|es|ich|du|wir|ihr|sie|man|jemand|niemand)\\b|[A-ZÄÖÜ][a-zäöüß]+)",
                Pattern.CASE_INSENSITIVE
            );
            
            Matcher speechMatcher = speechPattern.matcher(sentence);
            if (speechMatcher.find()) {
                int speechStart = speechMatcher.start();
                int speechEnd = speechMatcher.end();
                result[0] = sentence.substring(0, speechStart).trim();
                result[2] = sentence.substring(speechStart, speechEnd);
                result[3] = sentence.substring(speechEnd).trim();
            }
        }
        
        return result;
    }
    
    /**
     * Extrahiert den Namen einer Figur aus dem Sprechantwort (z.B. "fragte Kata" -> "Kata")
     */
    private String extractCharacterNameFromSpeechTag(String speechTag) {
        if (speechTag == null || speechTag.trim().isEmpty()) {
            logger.debug("Sprechantwort ist leer, kein Name extrahiert");
            return null;
        }
        
        String trimmedTag = speechTag.trim();
        logger.debug("Versuche Namen aus Sprechantwort zu extrahieren: '{}'", trimmedTag);
        
        // Pattern für Sprechverben gefolgt von einem Namen (Großbuchstabe)
        // Erweitert: auch nach Komma oder am Anfang
        Pattern namePattern = Pattern.compile(
            "(?:^|\\s|,)\\b(sagte|fragte|rief|antwortete|erwiderte|meinte|flüsterte|brüllte|stammelte|murmelte|erklärte|berichtete|erzählte|bemerkte|kommentierte|behauptete|versicherte|warnte|vermutete|leugnete|versprach|schwor|informierte|mitteilte|diskutierte|debattierte|argumentierte|streitete|besprach|plauderte|schwatzte|raunte|schrie|heulte|weinte|lachte|grinste|seufzte|stöhnte|ächzte|wimmerte|schluchzte|keuchte|stotterte|fluchte|schimpfte|donnerte|knurrte|fauchte|zischte|brummte|summte|pfiff|trällerte|sang|deklamierte|rezitierte|sprach|redete|plapperte|schwadronierte|faselte|laberte|quasselte|schwätzte|quatschte|konversierte)" +
            "\\s+([A-ZÄÖÜ][a-zäöüß]+(?:\\s+[A-ZÄÖÜ][a-zäöüß]+)?)\\b",
            Pattern.CASE_INSENSITIVE
        );
        
        Matcher matcher = namePattern.matcher(trimmedTag);
        if (matcher.find()) {
            String name = matcher.group(2);
            logger.debug("Name gefunden in Gruppe 2: '{}'", name);
            // Normalisiere den Namen (erster Buchstabe groß, Rest klein)
            if (name != null && name.length() > 0) {
                // Bei zusammengesetzten Namen (z.B. "von Berg") nur den ersten Teil nehmen
                String[] parts = name.trim().split("\\s+");
                if (parts.length > 0) {
                    String firstName = parts[0];
                    String normalized = firstName.substring(0, 1).toUpperCase() + firstName.substring(1).toLowerCase();
                    logger.debug("Normalisierter Name: '{}'", normalized);
                    return normalized;
                }
            }
        } else {
            logger.debug("Kein Name im Pattern gefunden für: '{}'", trimmedTag);
        }
        
        return null;
    }
    
    /**
     * Generiert alternative Sprechantwort-Versionen mit Ollama
     */
    private void generateSprechantwortAlternatives(String originalSentence, String instruction, double creativity, String length,
                                                   VBox answersBox, Button btnGenerate,
                                                   int startPos, int endPos, CustomStage dialogStage, ProgressBar progressBar) {
        // Lösche alte Antworten
        answersBox.getChildren().clear();
        
        // Analysiere Satzstruktur
        String[] structure = analyzeSentenceStructure(originalSentence);
        String textBefore = structure[0];
        String quotedText = structure[1];
        String speechTag = structure[2];
        String textAfter = structure[3];
        
        // Debug: Logge die Struktur
        logger.debug("Satzstruktur: textBefore='{}', quotedText='{}', speechTag='{}', textAfter='{}'", 
                     textBefore, quotedText, speechTag, textAfter);
        
        // Extrahiere den Namen aus dem Sprechantwort (falls vorhanden)
        String characterName = extractCharacterNameFromSpeechTag(speechTag);
        logger.debug("Extrahierten Namen aus Sprechantwort '{}': '{}'", speechTag, characterName);
        
        // Prüfe ob nach der wörtlichen Rede ein Komma kommt
        final boolean hasCommaAfterQuote;
        if (!quotedText.isEmpty()) {
            // Suche nach Komma direkt nach der wörtlichen Rede
            int quoteEndPos = originalSentence.indexOf(quotedText) + quotedText.length();
            if (quoteEndPos < originalSentence.length()) {
                String afterQuoteInOriginal = originalSentence.substring(quoteEndPos).trim();
                hasCommaAfterQuote = afterQuoteInOriginal.startsWith(",");
            } else {
                hasCommaAfterQuote = false;
            }
        } else {
            hasCommaAfterQuote = false;
        }
        
        // Lade Kontexte
        String characters = "";
        String synopsis = "";
        String outline = "";
        String worldbuilding = "";
        String style = "";
        String chapterText = "";
        
        if (originalDocxFile != null) {
            // Lade alle Dateien direkt aus dem Projektverzeichnis
            File projectDir = originalDocxFile.getParentFile();
            if (projectDir != null) {
                // Lade characters.txt
                File charactersFile = new File(projectDir, "characters.txt");
                if (charactersFile.exists()) {
                    try {
                        characters = new String(java.nio.file.Files.readAllBytes(charactersFile.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                    } catch (Exception e) {
                        logger.warn("Fehler beim Laden von characters.txt: " + e.getMessage());
                    }
                }
                
                // Lade synopsis.txt
                File synopsisFile = new File(projectDir, "synopsis.txt");
                if (synopsisFile.exists()) {
                    try {
                        synopsis = new String(java.nio.file.Files.readAllBytes(synopsisFile.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                    } catch (Exception e) {
                        logger.warn("Fehler beim Laden von synopsis.txt: " + e.getMessage());
                    }
                }
                
                // Lade outline.txt
                File outlineFile = new File(projectDir, "outline.txt");
                if (outlineFile.exists()) {
                    try {
                        outline = new String(java.nio.file.Files.readAllBytes(outlineFile.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                    } catch (Exception e) {
                        logger.warn("Fehler beim Laden von outline.txt: " + e.getMessage());
                    }
                }
                
                // Lade worldbuilding.txt
                File worldbuildingFile = new File(projectDir, "worldbuilding.txt");
                if (worldbuildingFile.exists()) {
                    try {
                        worldbuilding = new String(java.nio.file.Files.readAllBytes(worldbuildingFile.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                    } catch (Exception e) {
                        logger.warn("Fehler beim Laden von worldbuilding.txt: " + e.getMessage());
                    }
                }
                
                // Lade style.txt
                File styleFile = new File(projectDir, "style.txt");
                if (styleFile.exists()) {
                    try {
                        style = new String(java.nio.file.Files.readAllBytes(styleFile.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                    } catch (Exception e) {
                        logger.warn("Fehler beim Laden von style.txt: " + e.getMessage());
                    }
                }
            }
        }
        
        // Lade den gesamten Kapiteltext als Kontext
        if (codeArea != null) {
            chapterText = codeArea.getText();
            if (chapterText != null && chapterText.trim().isEmpty()) {
                chapterText = "";
            }
        }
        
        // Baue Kontext
        StringBuilder contextBuilder = new StringBuilder();
        if (characters != null && !characters.trim().isEmpty()) {
            contextBuilder.append("=== CHARAKTERE ===\n").append(characters).append("\n\n");
        }
        if (synopsis != null && !synopsis.trim().isEmpty()) {
            contextBuilder.append("=== SYNOPSIS ===\n").append(synopsis).append("\n\n");
        }
        if (outline != null && !outline.trim().isEmpty()) {
            contextBuilder.append("=== OUTLINE ===\n").append(outline).append("\n\n");
        }
        if (worldbuilding != null && !worldbuilding.trim().isEmpty()) {
            contextBuilder.append("=== WORLDBUILDING ===\n").append(worldbuilding).append("\n\n");
        }
        if (style != null && !style.trim().isEmpty()) {
            contextBuilder.append("=== STIL ===\n").append(style).append("\n\n");
        }
        if (chapterText != null && !chapterText.trim().isEmpty()) {
            contextBuilder.append("=== KAPITEL-TEXT ===\n").append(chapterText).append("\n\n");
        }
        
        String context = contextBuilder.toString();
        
        // Baue Prompt - nur für den Teil mit Sprechantwort
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("Du agierst als sehr erfahrener, kritischer deutscher Lektor. ");
        promptBuilder.append("Du analysierst den gegebenen Text ohne Schonung und nutzt alle Register eines professionellen Lektorats ");
        promptBuilder.append("(orthografische Präzision, stilistische Wirkung, Logikprüfung, Kohärenz, Tonalität, Figurenzeichnung, Tempo, Szenendramaturgie). ");
        promptBuilder.append("Arbeite strukturiert und detailliert.\n\n");
        
        promptBuilder.append("**WICHTIG - CHARAKTERE:**\n");
        promptBuilder.append("Im Kontext findest du die Datei '=== CHARAKTERE ===' mit Informationen zu allen Figuren. ");
        promptBuilder.append("IDENTIFIZIERE ZUERST den Namen der sprechenden Figur AUS DEM SPRECHANTWORT.\n\n");
        promptBuilder.append("ZWINGENDE REGEL FÜR DIE ERSETZUNG:\n");
        promptBuilder.append("- Wenn EIN NAME DER FIGUR IM SPRECHANTWORT steht (z.B. 'fragte Kata', 'sagte Jomar', 'erwiderte Dini', 'meinte Jaad'), ");
        promptBuilder.append("MUSS dieser Name GENAU SO AUCH in der Ersetzung verwendet werden (z.B. 'Kata hob eine Augenbraue', 'Jomar zuckte die Schultern', 'Dini lächelte', 'Jaad nickte')\n");
        promptBuilder.append("- Diese Regel gilt für ALLE Namen - wenn ein Name im Sprechantwort steht, verwende diesen Namen, NICHT ein Pronomen\n");
        promptBuilder.append("- Wenn KEIN Name im Sprechantwort steht (z.B. nur 'sagte sie', 'fragte er'), verwende die RICHTIGEN PRONOMEN (er/sie/ihr/sein) aus den Charakterinformationen\n");
        promptBuilder.append("- Die Beschreibung muss auch zu den Charaktereigenschaften der Figur passen.\n\n");
        
        promptBuilder.append("**WICHTIG - STIL:**\n");
        promptBuilder.append("Der Kapiteltext im Kontext zeigt dir den Schreibstil des Autors. ");
        promptBuilder.append("Übernimm diesen Stil GENAU: Verwende die gleichen Formulierungsmuster, Satzstrukturen und sprachlichen Eigenheiten. ");
        promptBuilder.append("Wenn der Autor Personalpronomen verwendet (Er/Sie/Ihre/Seine), verwende diese auch. ");
        promptBuilder.append("Vermeide Nominalisierungen wie 'Ein Herabsacken' oder unpersönliche Formulierungen ohne Pronomen wie 'Die Schulter sackte herab'. ");
        promptBuilder.append("Schreibe wie der Autor schreibt - im gleichen Stil, mit gleichen sprachlichen Mitteln.\n\n");
        
        promptBuilder.append("**Aufgabe:**\n");
        promptBuilder.append("Ersetze das folgende Sprechantwort durch eine 'Show Don't Tell' Beschreibung, die zur Stimmung und Emotion der Figur passt. ");
        promptBuilder.append("Die Ersetzung muss im Stil des Autors geschrieben sein (siehe Kapiteltext im Kontext).\n\n");
        
        promptBuilder.append("**Sprechantwort zu ersetzen:** ").append(speechTag).append("\n\n");
        
        // Wenn ein Name im Sprechantwort gefunden wurde, erwähne ihn explizit
        if (characterName != null && !characterName.isEmpty()) {
            promptBuilder.append("**ZWINGEND - NAME GEFUNDEN:**\n");
            promptBuilder.append("Im Sprechantwort wurde der Name '").append(characterName).append("' gefunden.\n");
            promptBuilder.append("DIE ERSETZUNG MUSS MIT '").append(characterName).append("' BEGINNEN!\n");
            promptBuilder.append("RICHTIG: '").append(characterName).append(" zuckte die Schultern.' oder '").append(characterName).append(" hob eine Augenbraue.'\n");
            promptBuilder.append("FALSCH: 'Zuckte die Schultern.' oder 'Sie zuckte die Schultern.' oder 'Er zuckte die Schultern.'\n");
            promptBuilder.append("DER NAME '").append(characterName).append("' MUSS AM ANFANG DER ERSETZUNG STEHEN - KEINE AUSNAHME!\n\n");
        } else {
            promptBuilder.append("**WICHTIG - KEIN NAME IM SPRECHANTWORT:**\n");
            promptBuilder.append("Im Sprechantwort steht KEIN Name. ");
            promptBuilder.append("VERWENDE EIN PRONOMEN (er/sie/ihr/sein) basierend auf den Charakterinformationen im Kontext. ");
            promptBuilder.append("VERWENDE KEINE Namen aus dem Kontext - nur Pronomen.\n\n");
        }
        
        if (!textBefore.isEmpty()) {
            promptBuilder.append("**Kontext:** Der Text davor lautet: \"").append(textBefore).append("\"\n\n");
        }
        
        if (!quotedText.isEmpty()) {
            promptBuilder.append("**Wörtliche Rede (muss unverändert bleiben):** ").append(quotedText).append("\n\n");
        }
        
        if (!instruction.isEmpty()) {
            promptBuilder.append("**WICHTIG - ZWINGENDE ANWEISUNG:** ").append(instruction).append("\n\n");
            promptBuilder.append("Diese Anweisung beschreibt die STIMMUNG und EMOTION der Figur. ");
            promptBuilder.append("Die Ersetzung MUSS diese Stimmung widerspiegeln. ");
            promptBuilder.append("Wenn die Figur z.B. melancholisch ist, darf sie NICHT Kopf schütteln oder Fäuste ballen. ");
            promptBuilder.append("Die Beschreibung MUSS zur angegebenen Stimmung passen.\n\n");
        }
        
        promptBuilder.append("**Regeln:**\n");
        if (characterName != null && !characterName.isEmpty()) {
            promptBuilder.append("- ZWINGEND: Die Ersetzung MUSS mit '").append(characterName).append("' beginnen (z.B. '").append(characterName).append(" zuckte die Schultern' NICHT 'Zuckte die Schultern' oder 'Sie zuckte die Schultern')\n");
            promptBuilder.append("- DER NAME '").append(characterName).append("' IST VERBINDLICH - KEINE ERSETZUNG OHNE DIESEN NAMEN AM ANFANG!\n");
        } else {
            promptBuilder.append("- ZWINGEND: Wenn EIN NAME im Sprechantwort steht, MUSS dieser Name GENAU SO am Anfang der Ersetzung verwendet werden\n");
            promptBuilder.append("- Wenn KEIN Name im Sprechantwort steht, verwende die RICHTIGEN PRONOMEN (er/sie/ihr/sein) aus den Charakterinformationen\n");
        }
        promptBuilder.append("- Wenn KEIN Name im Sprechantwort steht, verwende die RICHTIGEN PRONOMEN (er/sie/ihr/sein) aus den Charakterinformationen im Kontext\n");
        if ("Kurz".equals(length)) {
            promptBuilder.append("- WICHTIG: Die Ersetzung muss SEHR KURZ sein - maximal 3-10 Wörter \n");
            promptBuilder.append("- Keine ausführlichen Beschreibungen, keine langen Sätze, keine komplexen Formulierungen\n");
            promptBuilder.append("- Verwende einfache, prägnante Formulierungen im Stil des Autors\n");
        } else {
            promptBuilder.append("- Die Ersetzung kann ausführlicher sein, aber bleibe im Stil des Autors\n");
        }
        promptBuilder.append("- VERBOTEN: KEINE Nominalisierungen wie 'Ein Schulterzucken', 'Ein Lächeln', 'Ein Nicken' - verwende stattdessen Verben\n");
        promptBuilder.append("- VERBOTEN: KEINE Formulierungen die mit 'Ein ...' beginnen - verwende stattdessen Personalpronomen + Verb (z.B. 'Sie zuckte die Schultern' statt 'Ein Schulterzucken')\n");
        promptBuilder.append("- KEINE Sprechantworten verwenden (kein 'sagte', 'fragte', 'antwortete', 'erwiderte', 'meinte', etc.)\n");
        promptBuilder.append("- Zeige durch konkrete Handlungen, Gesten, Körpersprache, Mimik oder Gedanken, wer spricht\n");
        if (!instruction.isEmpty()) {
            promptBuilder.append("- Die Beschreibung MUSS zur Stimmung/Emotion aus der Anweisung passen\n");
        }
        promptBuilder.append("- Verwende indirekte Rede oder zeige die Emotion/Reaktion der Figur durch präzise, stimmige Beschreibungen\n");
        promptBuilder.append("- Der Ersatz soll lebendiger, visueller und glaubwürdiger werden\n");
        promptBuilder.append("- Bleibe sachlich, klar und nachvollziehbar\n");
        promptBuilder.append("- Verwende natürliche, realistische Formulierungen\n");
        promptBuilder.append("- Achte auf Kohärenz: Die Beschreibung muss logisch zur Situation und zur Stimmung passen\n");
        promptBuilder.append("- WICHTIG: Verwende VARIABLE Formulierungsstile - vermeide repetitive Muster\n");
        promptBuilder.append("- Verwende Personalpronomen (Er/Sie/Ihre/Seine) wie im Stil des Autors - vermeide Nominalisierungen und unpersönliche Formulierungen\n");
        promptBuilder.append("- Variiere die Satzstruktur, aber bleibe im Stil des Autors (siehe Kapiteltext im Kontext)\n");
        promptBuilder.append("- Jede Variante soll einen anderen Formulierungsstil haben, aber alle im Stil des Autors\n\n");
        
        promptBuilder.append("**Vorgehen:**\n");
        promptBuilder.append("Gib 3-5 alternative Ersetzungen für das Sprechantwort, jede in einer eigenen Zeile.\n");
        if (!instruction.isEmpty()) {
            promptBuilder.append("Jede Ersetzung muss zur angegebenen Stimmung passen. ");
        }
        promptBuilder.append("Jede Variante soll einen UNTERSCHIEDLICHEN Formulierungsstil verwenden - vermeide repetitive Muster. ");
        promptBuilder.append("Nur die Ersetzung selbst, keine vollständigen Sätze, keine Erklärungen, keine Nummerierungen.");
        
        String prompt = promptBuilder.toString();
        
        // Ollama-Service verwenden
        OllamaService ollamaService = new OllamaService();
        
        // Prüfe verfügbare Modelle und verwende das gewünschte Modell oder ein Fallback
        String targetModel = "jobautomation/OpenEuroLLM-German";
        Label statusLabel = new Label("Lade verfügbare Modelle...");
        statusLabel.setStyle(String.format("-fx-text-fill: %s;", THEMES[currentThemeIndex][1]));
        answersBox.getChildren().add(statusLabel);
        
        ollamaService.getAvailableModels().thenAccept(models -> {
            Platform.runLater(() -> {
                String modelToUse = null;
                
                // Prüfe ob das gewünschte Modell verfügbar ist (exakte Übereinstimmung oder ähnlich)
                for (String model : models) {
                    if (model.equals(targetModel) || 
                        model.equalsIgnoreCase(targetModel) ||
                        model.toLowerCase().contains("openeurollm") ||
                        (model.toLowerCase().contains("german") && model.toLowerCase().contains("openeuro"))) {
                        modelToUse = model;
                        break;
                    }
                }
                
                // Falls nicht verfügbar, suche nach ähnlichen Modellnamen
                if (modelToUse == null && models.length > 0) {
                    // Suche nach Modellen mit "german" oder "openeuro"
                    for (String model : models) {
                        String lowerModel = model.toLowerCase();
                        if (lowerModel.contains("german") || lowerModel.contains("openeuro")) {
                            modelToUse = model;
                            logger.info("Verwende ähnliches Modell: '{}' statt '{}'", model, targetModel);
                            break;
                        }
                    }
                    
                    // Falls immer noch nichts gefunden, verwende das erste verfügbare Modell
                    if (modelToUse == null) {
                        modelToUse = models[0];
                        logger.warn("Modell '{}' nicht gefunden, verwende '{}'", targetModel, modelToUse);
                        updateStatus("Modell '" + targetModel + "' nicht gefunden, verwende '" + modelToUse + "'");
                    } else {
                        updateStatus("Verwende Modell: " + modelToUse);
                    }
                }
                
                if (modelToUse == null) {
                    answersBox.getChildren().clear();
                    Label errorLabel = new Label("Fehler: Keine Modelle verfügbar!");
                    errorLabel.setStyle(String.format("-fx-text-fill: red;"));
                    answersBox.getChildren().add(errorLabel);
                    btnGenerate.setDisable(false);
                    btnGenerate.setText("Generieren");
                    progressBar.setVisible(false);
                    progressBar.setManaged(false);
                    return;
                }
                
                ollamaService.setModel(modelToUse);
                answersBox.getChildren().clear();
                
                // Generiere einmal mit mehreren Varianten im Prompt
                // Verwende Kreativitäts-Slider-Wert als Temperatur (0.0-1.0)
                double temperature = creativity;
                
                // Debug: Logge die verwendete Temperatur
                logger.debug("Verwende Temperatur: {}", temperature);
                
                // Speichere Struktur für späteres Zusammenbauen
                final String finalTextBefore = textBefore;
                final String finalQuotedText = quotedText;
                final String finalTextAfter = textAfter;
                final boolean finalHasCommaAfterQuote = hasCommaAfterQuote;
                
                ollamaService.generateText(prompt, context, temperature, ollamaService.getMaxTokens(), ollamaService.getTopP(), ollamaService.getRepeatPenalty())
                    .thenAccept(response -> {
                        Platform.runLater(() -> {
                            if (response != null && !response.trim().isEmpty()) {
                                // Teile die Antwort in Zeilen auf (jede Zeile ist eine Variante)
                                String[] variants = response.trim().split("\n");
                                
                                // Filtere leere Zeilen und nummerierte Listen
                                List<String> cleanVariants = new ArrayList<>();
                                for (String variant : variants) {
                                    String cleaned = variant.trim();
                                    // Entferne Nummerierungen wie "1. ", "2. ", "- ", etc.
                                    cleaned = cleaned.replaceAll("^\\d+\\.\\s*", "")
                                                     .replaceAll("^-\\s*", "")
                                                     .replaceAll("^\\*\\s*", "")
                                                     .trim();
                                    if (!cleaned.isEmpty() && cleaned.length() > 3) { // Mindestens 3 Zeichen
                                        cleanVariants.add(cleaned);
                                    }
                                }
                                
                                // Falls keine Varianten gefunden, verwende die gesamte Antwort
                                if (cleanVariants.isEmpty()) {
                                    cleanVariants.add(response.trim());
                                }
                                
                                // Erstelle UI-Elemente für jede Variante
                                for (int i = 0; i < cleanVariants.size() && i < 5; i++) {
                                    final int index = i;
                                    String replacement = cleanVariants.get(i);
                                    
                                    // Baue vollständigen Satz zusammen: Text vor + wörtliche Rede + Ersetzung + Text nach
                                    StringBuilder fullSentence = new StringBuilder();
                                    if (!finalTextBefore.isEmpty()) {
                                        fullSentence.append(finalTextBefore);
                                        if (!finalTextBefore.endsWith(" ") && !finalTextBefore.endsWith("\n")) {
                                            fullSentence.append(" ");
                                        }
                                    }
                                    if (!finalQuotedText.isEmpty()) {
                                        // Prüfe ob die Ersetzung ein vollständiger Satz ist (beginnt mit Großbuchstaben)
                                        boolean isFullSentence = !replacement.trim().isEmpty() && 
                                                                 Character.isUpperCase(replacement.trim().charAt(0));
                                        
                                        if (isFullSentence) {
                                            // Vollständiger Satz: Punkt vor das Anführungszeichen, kein Komma
                                            // Prüfe ob bereits ein Punkt vor dem schließenden Anführungszeichen ist
                                            String quotedTextWithoutClosingQuote = finalQuotedText;
                                            String closingQuote = "";
                                            
                                            // Finde das schließende Anführungszeichen (alle möglichen Typen)
                                            Pattern closingQuotePattern = Pattern.compile("([\"\\u201D\\u201C\\u00BB\\u00AB])$");
                                            Matcher closingQuoteMatcher = closingQuotePattern.matcher(finalQuotedText);
                                            if (closingQuoteMatcher.find()) {
                                                closingQuote = closingQuoteMatcher.group(1);
                                                quotedTextWithoutClosingQuote = finalQuotedText.substring(0, closingQuoteMatcher.start());
                                            }
                                            
                                            // Prüfe ob bereits ein Punkt am Ende ist
                                            if (!quotedTextWithoutClosingQuote.trim().endsWith(".") && 
                                                !quotedTextWithoutClosingQuote.trim().endsWith("!") && 
                                                !quotedTextWithoutClosingQuote.trim().endsWith("?")) {
                                                // Füge Punkt vor das Anführungszeichen hinzu
                                                fullSentence.append(quotedTextWithoutClosingQuote);
                                                fullSentence.append(".");
                                                fullSentence.append(closingQuote);
                                            } else {
                                                // Punkt ist bereits da
                                                fullSentence.append(finalQuotedText);
                                            }
                                            // Leerzeichen nach dem Anführungszeichen
                                            fullSentence.append(" ");
                                        } else {
                                            // Kein vollständiger Satz: Komma wie bisher
                                            fullSentence.append(finalQuotedText);
                                            if (finalHasCommaAfterQuote) {
                                                fullSentence.append(",");
                                            }
                                            // Füge Leerzeichen hinzu
                                            fullSentence.append(" ");
                                        }
                                    }
                                    fullSentence.append(replacement);
                                    if (!finalTextAfter.isEmpty()) {
                                        // Entferne führendes Komma aus textAfter, da es bereits hinzugefügt wurde
                                        String textAfterClean = finalTextAfter.replaceFirst("^,\\s*", "");
                                        if (!textAfterClean.isEmpty()) {
                                            // Füge Leerzeichen hinzu, wenn replacement nicht mit Leerzeichen endet
                                            if (!replacement.endsWith(" ") && !replacement.endsWith("\n")) {
                                                fullSentence.append(" ");
                                            }
                                            fullSentence.append(textAfterClean);
                                        }
                                    }
                                    
                                    final String finalVariant = fullSentence.toString().trim();
                                    
                                    // Erstelle Button für diese Variante
                                    Button answerBtn = new Button("Variante " + (index + 1));
                                    answerBtn.setMaxWidth(Double.MAX_VALUE);
                                    answerBtn.setAlignment(Pos.CENTER_LEFT);
                                    answerBtn.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: %s;",
                                        THEMES[currentThemeIndex][2], THEMES[currentThemeIndex][1]));
                                    
                                    TextArea answerText = new TextArea(finalVariant);
                                    answerText.setEditable(false);
                                    answerText.setWrapText(true);
                                    answerText.setPrefRowCount(2);
                                    answerText.getStyleClass().add("dialog-text-area");
                                    applyThemeToNode(answerText, currentThemeIndex);
                                    
                                    VBox answerBox = new VBox(5);
                                    answerBox.getChildren().addAll(answerBtn, answerText);
                                    answersBox.getChildren().add(answerBox);
                                    
                                    // Klick-Handler zum Ersetzen
                                    answerBtn.setOnAction(evt -> {
                                        // Validiere Positionen nochmal, um sicherzustellen, dass wir den richtigen Satz ersetzen
                                        Platform.runLater(() -> {
                                            try {
                                                String currentText = codeArea.getText();
                                                
                                                // Prüfe ob die Positionen noch gültig sind
                                                if (startPos < 0 || endPos > currentText.length() || startPos >= endPos) {
                                                    updateStatus("Fehler: Textpositionen ungültig.");
                                                    return;
                                                }
                                                
                                                // Prüfe ob der Text an den Positionen noch dem Original entspricht
                                                String textAtPosition = currentText.substring(startPos, Math.min(endPos, currentText.length()));
                                                if (!textAtPosition.trim().equals(originalSentence.trim())) {
                                                    // Text hat sich geändert, finde den Satz neu
                                                    int caretPos = codeArea.getCaretPosition();
                                                    int[] newBounds = findCurrentSentenceBounds(caretPos);
                                                    
                                                    if (newBounds != null) {
                                                        String newSentence = codeArea.getText(newBounds[0], newBounds[1]);
                                                        if (newSentence.trim().equals(originalSentence.trim())) {
                                                            // Verwende die neuen Positionen
                                                            codeArea.replaceText(newBounds[0], newBounds[1], finalVariant);
                                                        } else {
                                                            updateStatus("Fehler: Satz nicht mehr gefunden.");
                                                            return;
                                                        }
                                                    } else {
                                                        updateStatus("Fehler: Satz nicht mehr gefunden.");
                                                        return;
                                                    }
                                                } else {
                                                    // Positionen sind noch korrekt, ersetze direkt
                                                    codeArea.replaceText(startPos, endPos, finalVariant);
                                                }
                                                
                                                dialogStage.close();
                                                updateStatus("Satz ersetzt.");
                                            } catch (Exception ex) {
                                                logger.error("Fehler beim Ersetzen: {}", ex.getMessage());
                                                updateStatus("Fehler beim Ersetzen: " + ex.getMessage());
                                            }
                                        });
                                    });
                                }
                            }
                            
                            btnGenerate.setDisable(false);
                            btnGenerate.setText("Generieren");
                            progressBar.setVisible(false);
                            progressBar.setManaged(false);
                        });
                    })
                    .exceptionally(throwable -> {
                        Platform.runLater(() -> {
                            logger.error("Fehler bei Ollama-Generierung: {}", throwable.getMessage());
                            updateStatus("Fehler bei der Generierung: " + throwable.getMessage());
                            
                            Label errorLabel = new Label("Fehler: " + throwable.getMessage());
                            errorLabel.setStyle(String.format("-fx-text-fill: red;"));
                            answersBox.getChildren().add(errorLabel);
                            
                            btnGenerate.setDisable(false);
                            btnGenerate.setText("Generieren");
                            progressBar.setVisible(false);
                            progressBar.setManaged(false);
                        });
                        return null;
                    });
            });
        });
    }
    
    /**
     * Handler für "Phrase korrigieren"
     */
    private void handlePhraseKorrektur() {
        // Ähnlich wie handleSprechantwortKorrektur, aber für Phrasen
        updateStatus("Phrase korrigieren - noch nicht implementiert");
    }
    
    /**
     * Handler für "Absatz überarbeiten"
     */
    private void handleAbsatzUeberarbeitung() {
        if (codeArea == null) return;
        
        int caretPos = codeArea.getCaretPosition();
        int[] bounds = findCurrentParagraphBounds(caretPos);
        
        if (bounds == null) {
            updateStatus("Kein Absatz an der Cursorposition gefunden.");
            return;
        }
        
        String paragraph = codeArea.getText(bounds[0], bounds[1]);
        
        if (paragraph == null || paragraph.trim().isEmpty()) {
            updateStatus("Absatz ist leer.");
            return;
        }
        
        // Markiere den Absatz
        codeArea.selectRange(bounds[0], bounds[1]);
        
        // Öffne Dialog
        showAbsatzUeberarbeitungDialog(paragraph, bounds[0], bounds[1]);
    }
    
    /**
     * Zeigt den Dialog für Absatz-Überarbeitung
     */
    private void showAbsatzUeberarbeitungDialog(String originalParagraph, int startPos, int endPos) {
        CustomStage dialogStage = StageManager.createModalStage("Absatz überarbeiten", stage);
        dialogStage.setTitle("Absatz überarbeiten");
        dialogStage.setWidth(1000);
        dialogStage.setHeight(800);
        dialogStage.setTitleBarTheme(currentThemeIndex);
        
        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.getStyleClass().add("dialog-container");
        applyThemeToNode(root, currentThemeIndex);
        
        // Original-Absatz
        Label originalLabel = new Label("Original:");
        originalLabel.getStyleClass().add("dialog-label");
        applyThemeToNode(originalLabel, currentThemeIndex);
        
        TextArea originalArea = new TextArea(originalParagraph);
        originalArea.setEditable(false);
        originalArea.setPrefRowCount(6);
        originalArea.setWrapText(true);
        originalArea.getStyleClass().add("dialog-text-area");
        applyThemeToNode(originalArea, currentThemeIndex);
        
        // Anweisungsfeld (optional)
        Label instructionLabel = new Label("Anweisung (optional):");
        instructionLabel.getStyleClass().add("dialog-label");
        applyThemeToNode(instructionLabel, currentThemeIndex);
        
        TextField instructionField = new TextField();
        instructionField.setPromptText("z.B. Absatz soll spannender werden, mehr Show Don't Tell");
        applyThemeToNode(instructionField, currentThemeIndex);
        
        // Kreativitäts-Slider
        Label creativityLabel = new Label("Kreativität:");
        creativityLabel.getStyleClass().add("dialog-label");
        applyThemeToNode(creativityLabel, currentThemeIndex);
        
        Slider creativitySlider = new Slider(0.0, 1.0, 0.4);
        creativitySlider.setShowTickLabels(true);
        creativitySlider.setShowTickMarks(true);
        creativitySlider.setMajorTickUnit(0.25);
        creativitySlider.setMinorTickCount(0);
        creativitySlider.setSnapToTicks(false);
        creativitySlider.setPrefWidth(400);
        
        Label creativityValueLabel = new Label("0.40");
        creativityValueLabel.setMinWidth(40);
        creativityValueLabel.setStyle(String.format("-fx-text-fill: %s;", THEMES[currentThemeIndex][1]));
        
        creativitySlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            creativityValueLabel.setText(String.format("%.2f", newVal.doubleValue()));
        });
        
        HBox creativityBox = new HBox(10);
        creativityBox.setAlignment(Pos.CENTER_LEFT);
        creativityBox.getChildren().addAll(creativitySlider, creativityValueLabel);
        
        // Antworten-Bereich
        Label answersLabel = new Label("Vorschläge:");
        answersLabel.getStyleClass().add("dialog-label");
        applyThemeToNode(answersLabel, currentThemeIndex);
        
        // Fortschrittsbalken (zunächst unsichtbar)
        ProgressBar progressBar = new ProgressBar();
        progressBar.setVisible(false);
        progressBar.setManaged(false);
        progressBar.setPrefWidth(Double.MAX_VALUE);
        progressBar.setPrefHeight(20);
        progressBar.setMinHeight(20);
        progressBar.setMaxHeight(20);
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        applyThemeToNode(progressBar, currentThemeIndex);
        
        VBox answersBox = new VBox(10);
        ScrollPane scrollPane = new ScrollPane(answersBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(350);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        applyThemeToNode(scrollPane, currentThemeIndex);
        
        // Buttons
        HBox buttonBox = new HBox(10);
        Button btnGenerate = new Button("Generieren");
        btnGenerate.getStyleClass().add("button");
        applyThemeToNode(btnGenerate, currentThemeIndex);
        
        Button btnCancel = new Button("Abbrechen");
        btnCancel.getStyleClass().add("button");
        applyThemeToNode(btnCancel, currentThemeIndex);
        
        buttonBox.getChildren().addAll(btnGenerate, btnCancel);
        
        root.getChildren().addAll(originalLabel, originalArea, instructionLabel, instructionField, 
                                 creativityLabel, creativityBox, answersLabel, progressBar, scrollPane, buttonBox);
        
        Scene scene = new Scene(root);
        scene.setFill(javafx.scene.paint.Color.web(THEMES[currentThemeIndex][0]));
        String cssPath = ResourceManager.getCssResource("css/editor.css");
        if (cssPath != null) {
            scene.getStylesheets().add(cssPath);
        }
        // CSS auch für Theme-Klassen
        String manuskriptCssPath = ResourceManager.getCssResource("css/manuskript.css");
        if (manuskriptCssPath != null) {
            scene.getStylesheets().add(manuskriptCssPath);
        }
        
        dialogStage.setSceneWithTitleBar(scene);
        
        // Generiere Antworten
        btnGenerate.setOnAction(e -> {
            btnGenerate.setDisable(true);
            btnGenerate.setText("Generiere...");
            progressBar.setVisible(true);
            progressBar.setManaged(true);
            answersBox.getChildren().clear();
            
            String instruction = instructionField.getText().trim();
            double creativity = creativitySlider.getValue();
            generateAbsatzAlternatives(originalParagraph, instruction, creativity, answersBox, 
                                     btnGenerate, startPos, endPos, dialogStage, progressBar);
        });
        
        btnCancel.setOnAction(e -> dialogStage.close());
        
        dialogStage.showAndWait();
    }
    
    /**
     * Entfernt alle Textanalyse-Markierungen aus dem Editor, behält aber andere Styles (Markdown, Suche, etc.)
     */
    private void clearTextAnalysisMarkings() {
        if (codeArea == null) return;
        
        try {
            String content = codeArea.getText();
            if (content == null || content.isEmpty()) return;
            
            // Hole existierende StyleSpans
            StyleSpans<Collection<String>> existingSpans = codeArea.getStyleSpans(0, codeArea.getLength());
            
            // Liste der Textanalyse-Style-Klassen, die entfernt werden sollen
            Set<String> textAnalysisStyles = Set.of(
                "highlight-yellow",
                "highlight-orange",
                "search-match-first",
                "search-match-second",
                "sentence-short",
                "sentence-medium",
                "sentence-long"
            );
            
            // Sammle alle existierenden Styles und entferne Textanalyse-Styles
            StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
            int currentPos = 0;
            
            Map<Integer, Set<String>> existingStyles = new HashMap<>();
            for (StyleSpan<Collection<String>> span : existingSpans) {
                for (int i = 0; i < span.getLength(); i++) {
                    int pos = currentPos + i;
                    Set<String> styles = new HashSet<>(span.getStyle());
                    // Entferne alle Textanalyse-Styles
                    styles.removeAll(textAnalysisStyles);
                    if (!styles.isEmpty()) {
                        existingStyles.put(pos, styles);
                    }
                }
                currentPos += span.getLength();
            }
            
            // Baue neue StyleSpans ohne Textanalyse-Styles
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
            logger.debug("Fehler beim Zurücksetzen der Textanalyse-Markierungen: {}", e.getMessage());
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
            // Stelle die ursprünglichen Anführungszeichen wieder her
            restoreOriginalQuotes();
            updateStatus("KI-Assistent geschlossen");
        } else {
            // Aktiviere englische Anführungszeichen für KI-Assistent
            enableEnglishQuotesForAI();
            ollamaWindow.show();
            ollamaWindowVisible = true;
            updateStatus("KI-Assistent geöffnet");
        }
    }
    
    /**
     * Toggle für das Preview-Fenster
     */
    private void togglePreviewWindow() {
        if (previewStage == null || !previewStage.isShowing()) {
            createPreviewWindow();
            previewStage.show();
            
            // WICHTIG: Lade Eigenschaften NACH dem Anzeigen, um sicherzustellen, dass sie nicht überschrieben werden
            Platform.runLater(() -> {
                if (previewStage != null && preferences != null) {
                    try {
                        double savedX = preferences.getDouble("preview_window_x", -1);
                        double savedY = preferences.getDouble("preview_window_y", -1);
                        double savedWidth = preferences.getDouble("preview_window_width", -1);
                        double savedHeight = preferences.getDouble("preview_window_height", -1);
                        
                        if (savedX >= 0 && savedY >= 0 && savedWidth > 0 && savedHeight > 0) {
                            Rectangle2D windowBounds = PreferencesManager.MultiMonitorValidator.loadAndValidateWindowProperties(
                                preferences, "preview_window", 1000.0, 800.0);
                            PreferencesManager.MultiMonitorValidator.applyWindowProperties(previewStage, windowBounds);
                            logger.info("Preview-Fenster-Eigenschaften nach Anzeigen angewendet: x={}, y={}, width={}, height={}", 
                                       windowBounds.getMinX(), windowBounds.getMinY(), windowBounds.getWidth(), windowBounds.getHeight());
                        }
                    } catch (Exception e) {
                        logger.warn("Fehler beim Anwenden der Preview-Fenster-Eigenschaften nach Anzeigen: " + e.getMessage());
                    }
                }
            });
            
            updateStatus("Preview geöffnet");
        } else {
            // WICHTIG: Speichere Preferences bevor das Fenster versteckt wird
            savePreviewWindowProperties();
            previewStage.hide();
            updateStatus("Preview geschlossen");
        }
    }
    
    /**
     * Erstellt das Preview-Fenster mit Quill Editor
     */
    private void createPreviewWindow() {
        if (previewStage == null) {
            previewStage = StageManager.createStage("Quill Editor");
            previewStage.setResizable(true);
            previewStage.setMinWidth(600);
            previewStage.setMinHeight(400);
            
            // Setze Standardwerte - werden in loadPreviewWindowProperties() überschrieben falls vorhanden
            previewStage.setWidth(1000);
            previewStage.setHeight(800);
            
            // WebView für Quill Editor erstellen
            previewWebView = new WebView();
            previewWebView.setContextMenuEnabled(true);
            
            // Blocksatz-Toggle-Button (behalten für Kompatibilität)
            btnToggleJustify = new Button("Blocksatz");
            btnToggleJustify.getStyleClass().add("button");
            btnToggleJustify.setOnAction(e -> togglePreviewJustify());
            
            // Lade gespeicherten Blocksatz-Status
            if (preferences != null) {
                previewJustifyEnabled = preferences.getBoolean("preview_justify_enabled", false);
            }
            updateJustifyButtonStyle();
            
            // Layout erstellen mit VBox für besseres Resizing
            // Äußerer Container mit Padding und Border
            VBox outerContainer = new VBox();
            outerContainer.setPadding(new Insets(15));
            outerContainer.setFillWidth(true);
            
            // Theme-gerechter Hintergrund und Border
            String[] themeColors = {"#ffffff", "#1f2937", "#f3e5f5", "#0b1220", "#064e3b", "#581c87"};
            String[] borderColors = {"#cccccc", "#ffffff", "#d4a5d4", "#ffffff", "#ffffff", "#ffffff"};
            String borderStyle = "-fx-border-color: " + borderColors[currentThemeIndex] + "; -fx-border-width: 2px; -fx-border-radius: 4px;";
            outerContainer.setStyle("-fx-background-color: " + themeColors[currentThemeIndex] + "; " + borderStyle);
            
            // Innerer Container für Content
            VBox root = new VBox(5);
            root.setPadding(new Insets(10));
            root.setFillWidth(true);
            VBox.setVgrow(root, Priority.ALWAYS);
            
            // Button-Zeile
            HBox buttonBar = new HBox(10);
            buttonBar.setAlignment(Pos.CENTER_LEFT);
            buttonBar.getChildren().addAll(btnToggleJustify);
            
            // Quill Schriftgröße Steuerung
            lblQuillFontSize = new Label("Schriftgröße:");
            Button btnQuillFontDecrease = new Button("A-");
            btnQuillFontDecrease.getStyleClass().add("button");
            btnQuillFontDecrease.setStyle("-fx-min-width: 45px; -fx-max-width: 45px; -fx-min-height: 28px; -fx-max-height: 28px; -fx-font-size: 13px;");
            btnQuillFontDecrease.setTooltip(new Tooltip("Quill Schriftgröße verringern"));
            
            spnQuillFontSize = new Spinner<>();
            SpinnerValueFactory.IntegerSpinnerValueFactory fontSizeFactory = 
                new SpinnerValueFactory.IntegerSpinnerValueFactory(8, 72, 14, 2);
            spnQuillFontSize.setValueFactory(fontSizeFactory);
            spnQuillFontSize.setEditable(true);
            spnQuillFontSize.setStyle("-fx-min-width: 80px; -fx-max-width: 80px; -fx-min-height: 28px; -fx-max-height: 28px;");
            
            Button btnQuillFontIncrease = new Button("A+");
            btnQuillFontIncrease.getStyleClass().add("button");
            btnQuillFontIncrease.setStyle("-fx-min-width: 45px; -fx-max-width: 45px; -fx-min-height: 28px; -fx-max-height: 28px; -fx-font-size: 13px;");
            btnQuillFontIncrease.setTooltip(new Tooltip("Quill Schriftgröße erhöhen"));
            
            // Quill Schriftart Steuerung
            lblQuillFontFamily = new Label("Schriftart:");
            cmbQuillFontFamily = new ComboBox<>();
            cmbQuillFontFamily.getItems().addAll("Consolas", "Arial", "Times New Roman", "Courier New", "Verdana", "Georgia");
            cmbQuillFontFamily.setValue("Consolas");
            cmbQuillFontFamily.setPromptText("Schriftart");
            
            // Event Handler
            btnQuillFontDecrease.setOnAction(e -> {
                int currentSize = spnQuillFontSize.getValue();
                int newSize = Math.max(8, currentSize - 2);
                spnQuillFontSize.getValueFactory().setValue(newSize);
            });
            
            btnQuillFontIncrease.setOnAction(e -> {
                int currentSize = spnQuillFontSize.getValue();
                int newSize = Math.min(72, currentSize + 2);
                spnQuillFontSize.getValueFactory().setValue(newSize);
            });
            
            spnQuillFontSize.valueProperty().addListener((obs, oldVal, newVal) -> {
                // Verhindere Updates während des Umschaltens
                if (isTogglingViewMode || newVal == null || previewWebView == null) {
                    return;
                }
                if (useQuillMode) {
                    applyQuillGlobalFontSize(newVal);
                } else {
                    // Im normalen Modus: Content neu laden mit neuer Font-Größe
                    applyNormalViewFontSize(newVal);
                }
            });
            
            cmbQuillFontFamily.setOnAction(e -> {
                // Verhindere Updates während des Umschaltens
                if (isTogglingViewMode) {
                    return;
                }
                String selectedFont = cmbQuillFontFamily.getValue();
                if (selectedFont != null && previewWebView != null) {
                    if (useQuillMode) {
                    applyQuillGlobalFontFamily(selectedFont);
                    } else {
                        // Im normalen Modus: Content neu laden mit neuer Font-Familie
                        applyNormalViewFontFamily(selectedFont);
                    }
                }
            });
            
            // Zur buttonBar hinzufügen
            buttonBar.getChildren().addAll(
                new Separator(),
                lblQuillFontSize,
                btnQuillFontDecrease,
                spnQuillFontSize,
                btnQuillFontIncrease,
                new Separator(),
                lblQuillFontFamily,
                cmbQuillFontFamily
            );
            
            // Initialisiere Werte aus Preferences
            int savedFontSize = preferences.getInt("quillFontSize", 14);
            spnQuillFontSize.getValueFactory().setValue(savedFontSize);
            String savedFontFamily = preferences.get("quillFontFamily", "Consolas");
            cmbQuillFontFamily.setValue(savedFontFamily);
            
            // WebView soll den gesamten verfügbaren Platz einnehmen
            VBox.setVgrow(previewWebView, Priority.ALWAYS);
            HBox.setHgrow(previewWebView, Priority.ALWAYS);
            
            root.getChildren().addAll(buttonBar, previewWebView);
            outerContainer.getChildren().add(root);
            
            // Scene erstellen
            Scene scene = new Scene(outerContainer);
            
            // CSS mit ResourceManager laden
            String cssPath = ResourceManager.getCssResource("css/manuskript.css");
            if (cssPath != null) {
                scene.getStylesheets().add(cssPath);
            }
            
            // Scene mit Titelleiste setzen
            previewStage.setSceneWithTitleBar(scene);
            
            // Theme anwenden
            previewStage.setFullTheme(currentThemeIndex);
            previewStage.setTitleBarTheme(currentThemeIndex);
            
            // Labels stylen basierend auf Theme
            updatePreviewWindowLabels();
            
            // WICHTIG: Fenster-Eigenschaften NACH setSceneWithTitleBar laden
            // Verwende Platform.runLater, um sicherzustellen, dass die Scene vollständig geladen ist
            Platform.runLater(() -> {
                // Fenster-Position und Größe laden (Listener registrieren)
                loadPreviewWindowProperties();
                
                // WICHTIG: Stelle sicher, dass die Breite auch nach dem Setzen der Scene korrekt ist
                // (kann durch setSceneWithTitleBar überschrieben werden)
                Platform.runLater(() -> {
                    if (preferences != null && previewStage != null) {
                        try {
                            double savedWidth = preferences.getDouble("preview_window_width", -1);
                            double savedHeight = preferences.getDouble("preview_window_height", -1);
                            
                            if (savedWidth > 0 && savedHeight > 0) {
                                previewStage.setWidth(savedWidth);
                                previewStage.setHeight(savedHeight);
                                logger.info("Preview-Fenster-Größe nach Scene-Setzen angewendet: width={}, height={}", savedWidth, savedHeight);
                            }
                        } catch (Exception e) {
                            logger.warn("Fehler beim Anwenden der Preview-Fenster-Größe nach Scene-Setzen: " + e.getMessage());
                        }
                    }
                });
            });
            
            // Quill Editor initialisieren
            initializeQuillEditor();
            
            // Scroll-Synchronisation einrichten
            setupQuillScrollSync();
            
            // Text-Änderungen überwachen (ENTFERNT - Quill wird nur bei Scroll/Focus aktualisiert)
            setupQuillTextListener();
            
            // WICHTIG: Quill Content aktualisieren, wenn das Fenster den Focus bekommt (nur im Quill-Modus)
            previewStage.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
                // Verhindere Updates während des Umschaltens
                if (isTogglingViewMode) {
                    return;
                }
                if (isNowFocused && previewWebView != null && codeArea != null && !isUpdatingFromQuill && useQuillMode) {
                    // Aktualisiere Quill Content, wenn das Fenster den Focus bekommt
                    Platform.runLater(() -> {
                        updateQuillContent();
                    });
                } else if (isNowFocused && previewWebView != null && codeArea != null && !useQuillMode) {
                    // Im normalen Modus: Content aktualisieren
                    Platform.runLater(() -> {
                        String markdownContent = codeArea.getText();
                        String htmlContent = convertMarkdownToHTMLForPreview(markdownContent);
                        previewWebView.getEngine().loadContent(htmlContent, "text/html");
                    });
                }
            });
            
            // Fenster schließen-Handler
            previewStage.setOnCloseRequest(e -> {
                // Speichere Position und Größe beim Schließen
                savePreviewWindowProperties();
                previewStage.hide();
            });
            
            // WICHTIG: Auch beim Verstecken speichern (nicht nur beim expliziten Schließen)
            previewStage.setOnHidden(e -> {
                // Speichere Position und Größe beim Verstecken
                savePreviewWindowProperties();
            });
        } else {
            // Fenster existiert bereits - lade gespeicherte Eigenschaften und aktualisiere Theme
            // WICHTIG: Lade auch die gespeicherten Eigenschaften, wenn das Fenster wieder gezeigt wird
            loadPreviewWindowProperties();
            
            // Lade gespeicherten Blocksatz-Status
            if (preferences != null) {
                previewJustifyEnabled = preferences.getBoolean("preview_justify_enabled", false);
                updateJustifyButtonStyle();
            }
            
            previewStage.setFullTheme(currentThemeIndex);
            previewStage.setTitleBarTheme(currentThemeIndex);
            if (useQuillMode) {
            applyThemeToQuill(currentThemeIndex);
            updateQuillContent();
            } else {
                // Normaler Modus: Content aktualisieren
                if (codeArea != null) {
                    String markdownContent = codeArea.getText();
                    String htmlContent = convertMarkdownToHTMLForPreview(markdownContent);
                    previewWebView.getEngine().loadContent(htmlContent, "text/html");
                }
            }
            // Labels aktualisieren
            updatePreviewWindowLabels();
        }
    }
    
    /**
     * Initialisiert den Quill Editor im WebView
     */
    private void initializeQuillEditor() {
        if (previewWebView == null) {
            return;
        }
        
        try {
            // Quill HTML Template laden
            URL quillTemplate = getClass().getResource("/quill-editor.html");
            if (quillTemplate == null) {
                logger.error("Quill Editor Template nicht gefunden! Pfad: /quill-editor.html");
                // Versuche alternativen Pfad
                quillTemplate = EditorWindow.class.getResource("/quill-editor.html");
                if (quillTemplate == null) {
                    logger.error("Quill Editor Template auch mit alternativem Pfad nicht gefunden!");
                    Platform.runLater(() -> {
                        showErrorDialog("Quill Editor Fehler", 
                            "Das Quill Editor Template konnte nicht geladen werden.\n" +
                            "Bitte prüfen Sie, ob die Datei quill-editor.html im resources-Ordner existiert.");
                    });
                    return;
                }
            }
            logger.info("Quill Template geladen: " + quillTemplate.toExternalForm());
            
            // Java Bridge erstellen (falls noch nicht vorhanden)
            if (quillBridge == null) {
                quillBridge = new QuillBridge();
            }
            
            // WebEngine konfigurieren
            javafx.scene.web.WebEngine engine = previewWebView.getEngine();
            
            // Java Bridge registrieren wenn WebView geladen ist
            engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                    Platform.runLater(() -> {
                        try {
                            // Erstelle JavaScript-Wrapper für Java Bridge
                            // Da JSObject nicht direkt verfügbar ist, verwenden wir einen Workaround
                            // mit JavaScript-Funktionen, die Java-Methoden über executeScript aufrufen
                            
                            // Bridge-Script ohne Backslash-Escapes, klar zeilengetrennt
                            String bridgeScript = String.join("\n",
                                "window.javaApp = {",
                                "  onQuillContentChange: function(html, delta) {",
                                "    window.quillLastContent = html;",
                                "    window.quillLastDelta = delta;",
                                "    window.quillContentChanged = true;",
                                "  },",
                                "  onQuillSelectionChange: function(index, length) {",
                                "    window.quillLastSelection = {index: index, length: length};",
                                "    window.quillSelectionChanged = true;",
                                "  },",
                                "  onQuillScroll: function(scrollTop, scrollHeight) {",
                                "    window.quillLastScroll = {top: scrollTop, height: scrollHeight};",
                                "    window.quillScrollChanged = true;",
                                "  },",
                                "  log: function(level, message) {",
                                "    window.quillLogs = window.quillLogs || [];",
                                "    window.quillLogs.push({level: level, message: message, time: new Date().toISOString()});",
                                "    if (window.quillLogs.length > 100) window.quillLogs.shift();",
                                "  }",
                                "};"
                            );
                            
                            engine.executeScript(bridgeScript);
                            
                            // Starte Polling für Änderungen (da direkte Callbacks nicht funktionieren)
                            startQuillChangePolling(engine);
                            
                            // WICHTIG: Warte länger beim ersten Laden, damit JavaScript in HTML vollständig ausgeführt wird
                            // Beim ersten Laden braucht das JavaScript Zeit, um window.setQuillContent zu definieren
                            // Prüfe zuerst, ob es bereits bereit ist (beim zweiten Öffnen)
                            Timeline initDelay = new Timeline(new KeyFrame(Duration.millis(500), event -> {
                                // Prüfe sofort ob bereits bereit
                                try {
                                    Object quickCheck = engine.executeScript("(function() { return window.quillReady && window.quill && typeof window.setQuillContent === 'function'; })()");
                                    if (quickCheck != null && Boolean.TRUE.equals(quickCheck)) {
                                        logger.info("Quill bereits bereit beim ersten Check, lade sofort Content");
                                        waitForQuillReady(engine);
                                    } else {
                                        // Noch nicht bereit, starte normale Warteschleife
                                        logger.info("Quill noch nicht bereit, starte Warteschleife...");
                                        waitForQuillReady(engine);
                                    }
                                } catch (Exception e) {
                                    logger.debug("Fehler beim ersten Check, starte Warteschleife: " + e.getMessage());
                                    waitForQuillReady(engine);
                                }
                            }));
                            initDelay.play();
                        } catch (Exception e) {
                            logger.error("Fehler beim Registrieren der Java Bridge", e);
                        }
                    });
                }
            });
            
            // Quill Template laden
            String templateUrl = quillTemplate.toExternalForm();
            logger.info("Lade Quill Template von: " + templateUrl);
            engine.load(templateUrl);
            
            // Debug: Prüfe Load-Status
            engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                logger.debug("Quill Template Load State: " + newState);
                if (newState == javafx.concurrent.Worker.State.FAILED) {
                    logger.error("Fehler beim Laden des Quill Templates!");
                    Platform.runLater(() -> {
                        showErrorDialog("Quill Editor Fehler", 
                            "Das Quill Editor Template konnte nicht geladen werden.\n" +
                            "Fehler: " + engine.getLoadWorker().getException().getMessage());
                    });
                }
            });
            
        } catch (Exception e) {
            logger.error("Fehler beim Initialisieren des Quill Editors", e);
            Platform.runLater(() -> {
                showErrorDialog("Quill Editor Fehler", 
                    "Fehler beim Initialisieren des Quill Editors:\n" + e.getMessage());
            });
        }
    }
    
    /**
     * Wartet darauf, dass Quill bereit ist
     */
    private void waitForQuillReady(javafx.scene.web.WebEngine engine) {
        Platform.runLater(() -> {
            try {
                // Prüfe ob Quill bereit ist UND setQuillContent verfügbar ist
                Object quillReady = engine.executeScript("(function() { return window.quillReady; })()");
                Object quillExists = engine.executeScript("(function() { return typeof window.quill !== 'undefined'; })()");
                Object setQuillContentExists = engine.executeScript("(function() { return typeof window.setQuillContent === 'function'; })()");
                
                if (quillReady != null && Boolean.TRUE.equals(quillReady) && 
                    quillExists != null && Boolean.TRUE.equals(quillExists) &&
                    setQuillContentExists != null && Boolean.TRUE.equals(setQuillContentExists)) {
                    // Quill ist bereit - Theme anwenden und Content laden
                    logger.info("Quill ist bereit (ready: " + quillReady + ", quill: " + quillExists + ", setQuillContent: " + setQuillContentExists + "), wende Theme an und lade Content");
                    applyThemeToQuill(currentThemeIndex);
                    
                    // Warte kurz, damit Theme angewendet wird, dann Content laden
                    Platform.runLater(() -> {
                        // Erste Verzögerung für Theme - erhöht für zuverlässigere Initialisierung
                        Timeline themeTimeline = new Timeline(new KeyFrame(Duration.millis(300), event -> {
                            // Setze lastQuillContent auf null, damit Content definitiv geladen wird
                            lastQuillContent = null;
                            logger.info("Lade Quill Content nach Initialisierung...");
                            
                            // Rufe updateQuillContent direkt auf (ohne weitere Verzögerung)
                            updateQuillContent();
                            
                            // Zusätzliche Verifizierung nach kurzer Verzögerung
                            Timeline verifyTimeline = new Timeline(new KeyFrame(Duration.millis(500), verifyEvent -> {
                                try {
                                    javafx.scene.web.WebEngine verifyEngine = previewWebView.getEngine();
                                    if (verifyEngine.getLoadWorker().getState() == javafx.concurrent.Worker.State.SUCCEEDED) {
                                        Object quillReadyCheck = verifyEngine.executeScript("(function() { return window.quillReady && window.quill; })()");
                                        if (quillReadyCheck != null && Boolean.TRUE.equals(quillReadyCheck)) {
                                            Object contentLength = verifyEngine.executeScript(
                                                "(function() { return window.getQuillContent ? window.getQuillContent().length : 0; })()");
                                            logger.info("Verifizierung: Quill Content-Länge nach Initialisierung: " + contentLength);
                                            
                                            // Falls Content leer ist, versuche erneut
                                            if (contentLength instanceof Number && ((Number)contentLength).intValue() <= 10) {
                                                logger.warn("Quill Content ist leer oder sehr kurz, versuche erneut zu laden...");
                                                lastQuillContent = null;
                                                updateQuillContent();
                                                
                                                // Nochmalige Verifizierung nach 500ms
                                                Timeline retryVerify = new Timeline(new KeyFrame(Duration.millis(500), retryEvent -> {
                                                    try {
                                                        Object retryContentLength = verifyEngine.executeScript(
                                                            "window.getQuillContent ? window.getQuillContent().length : 0");
                                                        logger.info("Retry-Verifizierung: Quill Content-Länge: " + retryContentLength);
                                                    } catch (Exception e) {
                                                        logger.debug("Fehler bei Retry-Verifizierung: " + e.getMessage());
                                                    }
                                                }));
                                                retryVerify.play();
                                            }
                                        } else {
                                            logger.warn("Quill nicht bereit bei Verifizierung, versuche erneut...");
                                            lastQuillContent = null;
                                            Timeline retryTimeline = new Timeline(new KeyFrame(Duration.millis(500), retryEvent -> {
                                                updateQuillContent();
                                            }));
                                            retryTimeline.play();
                                        }
                                    }
                                } catch (Exception e) {
                                    logger.debug("Fehler bei Verifizierung: " + e.getMessage());
                                }
                            }));
                            verifyTimeline.play();
                        }));
                        themeTimeline.play();
                    });
                } else {
                    // Warte noch etwas
                    logger.debug("Quill noch nicht bereit, warte... (ready: " + quillReady + ", quill: " + quillExists + ", setQuillContent: " + setQuillContentExists + ")");
                    Timeline timeline = new Timeline(new KeyFrame(Duration.millis(100), event -> {
                        waitForQuillReady(engine);
                    }));
                    timeline.play();
                }
            } catch (Exception e) {
                logger.debug("Warte auf Quill Ready: " + e.getMessage());
                // Wiederhole nach kurzer Verzögerung
                Timeline timeline = new Timeline(new KeyFrame(Duration.millis(200), event -> {
                    waitForQuillReady(engine);
                }));
                timeline.play();
            }
        });
    }
    
    /**
     * Startet Polling für Quill-Änderungen (Workaround für fehlende direkte Callbacks)
     */
    private void startQuillChangePolling(javafx.scene.web.WebEngine engine) {
        // WICHTIG: Stoppe vorherige Timeline, falls vorhanden, um mehrere gleichzeitige Instanzen zu vermeiden
        if (quillPollingTimeline != null) {
            quillPollingTimeline.stop();
            quillPollingTimeline = null;
        }
        
        Timeline pollingTimeline = new Timeline(new KeyFrame(Duration.millis(100), event -> {
            try {
                if (engine.getLoadWorker().getState() != javafx.concurrent.Worker.State.SUCCEEDED) {
                    return;
                }
                
                // Prüfe auf Content-Änderungen
                Object contentChanged = engine.executeScript("window.quillContentChanged");
                if (Boolean.TRUE.equals(contentChanged)) {
                    // Reset Flag
                    engine.executeScript("window.quillContentChanged = false;");
                    
                    // Hole Content
                    Object htmlContent = engine.executeScript("window.quillLastContent");
                    Object deltaJson = engine.executeScript("window.quillLastDelta");
                    
                    if (htmlContent != null && htmlContent instanceof String) {
                        String html = (String) htmlContent;
                        String delta = deltaJson instanceof String ? (String) deltaJson : null;
                        
                        logger.debug("Quill Content Change erkannt via Polling, HTML-Länge: " + html.length());
                        
                        // Rufe Bridge-Methode auf
                        if (quillBridge != null) {
                            quillBridge.onQuillContentChange(html, delta);
                        } else {
                            logger.warn("QuillBridge ist null!");
                        }
                    } else {
                        logger.debug("Quill Content Change erkannt, aber HTML ist null oder kein String");
                    }
                }
                
                // Prüfe auf Logs von JavaScript
                try {
                    // Prüfe zuerst, ob quillLogs existiert und Einträge hat
                    Object logsCountObj = engine.executeScript("(function() { return (window.quillLogs || []).length; })()");
                    int logsCount = logsCountObj instanceof Number ? ((Number) logsCountObj).intValue() : 0;
                    if (logsCount > 0) {
                        logger.debug("Quill-Logs gefunden: {} Einträge", logsCount);
                        // Hole Logs und lösche sie gleichzeitig (atomare Operation)
                        String logScript = 
                            "(function() { " +
                            "  var logs = window.quillLogs || []; " +
                            "  window.quillLogs = []; " +
                            "  var result = []; " +
                            "  for (var i = 0; i < logs.length; i++) { " +
                            "    result.push(logs[i].level + '|' + logs[i].message); " +
                            "  } " +
                            "  return result.join('\\n'); " +
                            "})()";
                        Object logsObj = engine.executeScript(logScript);
                        if (logsObj instanceof String && !((String) logsObj).isEmpty()) {
                            String logs = (String) logsObj;
                            String[] logLines = logs.split("\n");
                            logger.debug("Quill-Logs verarbeitet: {} Zeilen", logLines.length);
                            for (String logLine : logLines) {
                                if (logLine.contains("|")) {
                                    String[] parts = logLine.split("\\|", 2);
                                    if (parts.length == 2) {
                                        String level = parts[0].trim();
                                        String message = parts[1].trim();
                                        switch (level.toUpperCase()) {
                                            case "ERROR":
                                                logger.error("[Quill] {}", message);
                                                break;
                                            case "WARN":
                                                logger.warn("[Quill] {}", message);
                                                break;
                                            case "DEBUG":
                                                logger.debug("[Quill] {}", message);
                                                break;
                                            default:
                                                logger.info("[Quill] {}", message);
                                                break;
                                        }
                                    }
                                }
                            }
                        } else {
                            logger.debug("Quill-Logs-String ist leer oder null");
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Fehler beim Abrufen der Quill-Logs: {}", e.getMessage());
                }
                
                // Prüfe auf Scroll-Änderungen
                // WICHTIG: Ignoriere Scroll-Änderungen, wenn gerade Editor → Quill synchronisiert wird
                // Das verhindert Rückkopplung und Sprünge
                Object scrollChanged = engine.executeScript("window.quillScrollChanged");
                if (Boolean.TRUE.equals(scrollChanged)) {
                    // WICHTIG: Überspringe Scroll-Synchronisation komplett, wenn CodeArea gerade aktualisiert wird
                    // oder wenn der Editor fokussiert ist (Benutzer tippt gerade)
                    if (isUpdatingFromCodeArea) {
                        engine.executeScript("window.quillScrollChanged = false;");
                        return;
                    }
                    
                    // Prüfe, ob der Editor gerade fokussiert ist - wenn ja, keine Synchronisation
                    // Das verhindert, dass der Editor während des Tippens wegscrollt
                    if (codeArea != null && codeArea.isFocused()) {
                        engine.executeScript("window.quillScrollChanged = false;");
                        return;
                    }
                    
                    // Prüfe, ob Quill gerade programmatisch gescrollt wird (Editor → Quill Sync)
                    try {
                        Object quillScrollingProgrammatically = engine.executeScript("(window.isQuillScrollingProgrammatically || false)");
                        if (Boolean.TRUE.equals(quillScrollingProgrammatically)) {
                            // Quill wird gerade programmatisch gescrollt (Editor → Quill), ignoriere dieses Event
                            engine.executeScript("window.quillScrollChanged = false;");
                            return;
                        }
                    } catch (Exception e) {
                        // Ignoriere Fehler
                    }
                    
                    engine.executeScript("window.quillScrollChanged = false;");
                    
                    Object scrollObj = engine.executeScript("window.quillLastScroll");
                    if (scrollObj != null) {
                        // Scroll-Informationen verarbeiten: Quill -> Editor (textbasiert)
                        try {
                            if (codeArea != null) {
                                // Textbasierte Zuordnung Quill -> Editor
                                Platform.runLater(() -> {
                                    try {
                                        isScrollingPreview = true;
                                        Object snippetObj = engine.executeScript("(window.getMiddleVisibleText ? window.getMiddleVisibleText() : '')");
                                        String snippet = snippetObj != null ? snippetObj.toString().trim() : "";
                                        if (snippet.length() > 3) {
                                            int idx = findParagraphBySnippet(snippet);
                                            if (idx >= 0) {
                                                scrollEditorToParagraphCentered(idx);
                                            }
                                        }
                                    } finally {
                                        isScrollingPreview = false;
                                    }
                                });
                            }
                        } catch (Exception e) {
                            logger.debug("Fehler bei Quill Scroll-Sync zu CodeArea: " + e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                logger.debug("Fehler beim Polling: " + e.getMessage());
            }
        }));
        pollingTimeline.setCycleCount(Timeline.INDEFINITE);
        quillPollingTimeline = pollingTimeline; // Speichere Referenz für späteres Stoppen
        pollingTimeline.play();
    }
    
    /**
     * Aktualisiert den Quill Editor Content
     */
    private void updateQuillContent() {
        if (previewWebView == null || codeArea == null || isUpdatingFromQuill || !useQuillMode) {
            return;
        }
        
        try {
            String markdownContent = codeArea.getText();
            String htmlContent = convertMarkdownToQuillHTML(markdownContent);
            
            // Nur aktualisieren, wenn sich der Content geändert hat
            // WICHTIG: Wenn lastQuillContent null ist, immer aktualisieren (erstes Laden)
            if (lastQuillContent != null && htmlContent.equals(lastQuillContent)) {
                logger.debug("Quill Content unverändert, keine Aktualisierung nötig");
                return; // Keine Änderung, kein Update nötig
            }
            
            logger.debug("Aktualisiere Quill Content (Länge: " + htmlContent.length() + ")");
            // WICHTIG: lastQuillContent wird erst nach erfolgreichem Laden gesetzt!
            
            javafx.scene.web.WebEngine engine = previewWebView.getEngine();
            if (engine.getLoadWorker().getState() == javafx.concurrent.Worker.State.SUCCEEDED) {
                // Prüfe ob Quill bereit ist
                Object quillReady = engine.executeScript("(function() { return window.quillReady; })()");
                Object quillExists = engine.executeScript("(function() { return typeof window.quill !== 'undefined' && window.quill !== null; })()");
                
                if (quillReady != null && Boolean.TRUE.equals(quillReady) && 
                    quillExists != null && Boolean.TRUE.equals(quillExists)) {
                    // Quill Content aktualisieren
                    Platform.runLater(() -> {
                        try {
                            // WICHTIG: Flag VOR dem JavaScript-Aufruf setzen, damit es sofort aktiv ist
                            isUpdatingFromCodeArea = true;
                            
                            // WICHTIG: Setze auch JavaScript-Flag SYNCHRON VOR setQuillContent, damit es sofort aktiv ist
                            // Verwende executeScript direkt (nicht in einem String), um sicherzustellen, dass es sofort ausgeführt wird
                            try {
                                engine.executeScript("window.isUpdatingFromCodeArea = true;");
                                logger.debug("JavaScript-Flag isUpdatingFromCodeArea auf true gesetzt");
                            } catch (Exception e) {
                                logger.warn("Konnte JavaScript-Flag nicht setzen: " + e.getMessage());
                            }
                            
                            logger.info("Setze Quill Content, HTML-Länge: " + htmlContent.length());
                            
                            // Prüfe nochmal ob Quill wirklich existiert (als IIFE)
                            Object quillCheck = engine.executeScript("(function() { return window.quill && typeof window.setQuillContent === 'function'; })()");
                            if (quillCheck == null || !Boolean.TRUE.equals(quillCheck)) {
                                logger.warn("Quill oder setQuillContent nicht verfügbar, warte...");
                                isUpdatingFromCodeArea = false;
                                // Setze auch JavaScript-Flag zurück
                                try {
                                    engine.executeScript("window.isUpdatingFromCodeArea = false;");
                                } catch (Exception e) {
                                    // Ignoriere Fehler
                                }
                                lastQuillContent = null; // WICHTIG: Zurücksetzen für Retry
                                Timeline retryTimeline = new Timeline(new KeyFrame(Duration.millis(500), event -> {
                                    updateQuillContent();
                                }));
                                retryTimeline.play();
                                return;
                            }
                            
                            // WICHTIG: Bei sehr großen Strings (>1MB) speichere Content in window-Property
                            // um Script-Größe zu vermeiden und Performance-Probleme zu verhindern
                            String script;
                            if (htmlContent.length() > 1000000) {
                                // Großer Content: Speichere in window-Property
                                engine.executeScript("window._quillPendingContent = " + toJSString(htmlContent) + ";");
                                script = 
                                    "(function() { " +
                                    "  try { " +
                                    "    if (window.setQuillContent && window.quill && window._quillPendingContent) { " +
                                    "      var content = window._quillPendingContent; " +
                                    "      delete window._quillPendingContent; " +
                                    "      var result = window.setQuillContent(content); " +
                                    "      console.log('Content gesetzt via setQuillContent (großer Content), Ergebnis:', result); " +
                                    "      return result || 'success'; " +
                                    "    } else { " +
                                    "      console.error('setQuillContent, quill oder _quillPendingContent nicht verfügbar'); " +
                                    "      return 'not_ready'; " +
                                    "    } " +
                                    "  } catch(e) { " +
                                    "    console.error('Fehler beim Setzen des Contents:', e); " +
                                    "    return 'error: ' + e.message; " +
                                    "  } " +
                                    "})()";
                            } else {
                                // Normaler Content: Direkt im Script
                                script = String.format(
                                    "(function() { " +
                                    "  try { " +
                                    "    if (window.setQuillContent && window.quill) { " +
                                    "      var result = window.setQuillContent(%s); " +
                                    "      console.log('Content gesetzt via setQuillContent, Ergebnis:', result); " +
                                    "      return result || 'success'; " +
                                    "    } else { " +
                                    "      console.error('setQuillContent oder quill nicht verfügbar'); " +
                                    "      return 'not_ready'; " +
                                    "    } " +
                                    "  } catch(e) { " +
                                    "    console.error('Fehler beim Setzen des Contents:', e); " +
                                    "    return 'error: ' + e.message; " +
                                    "  } " +
                                    "})()", 
                                    toJSString(htmlContent));
                            }
                            
                            Object result = engine.executeScript(script);
                            logger.info("Quill Content aktualisiert, Script-Ergebnis: " + result);
                            
                            // Prüfe Ergebnis und setze lastQuillContent nur bei Erfolg
                            boolean success = false;
                            if (result instanceof String) {
                                String resultStr = (String) result;
                                if (resultStr.contains("success")) {
                                    // ERFOLG: Content wurde erfolgreich gesetzt
                                    lastQuillContent = htmlContent;
                                    logger.info("Quill Content erfolgreich gesetzt, lastQuillContent aktualisiert");
                                    success = true;
                                } else if (resultStr.contains("error") || resultStr.contains("not_ready")) {
                                    // FEHLER: Retry mit null lastQuillContent
                                    logger.warn("Fehler beim Setzen des Contents (" + resultStr + "), versuche erneut in 500ms...");
                                    isUpdatingFromCodeArea = false;
                                    lastQuillContent = null; // WICHTIG: Zurücksetzen für Retry
                                    Timeline retryTimeline = new Timeline(new KeyFrame(Duration.millis(500), event -> {
                                        updateQuillContent();
                                    }));
                                    retryTimeline.play();
                                    return;
                                }
                            } else if (result == null) {
                                // Null bedeutet möglicherweise, dass die Funktion nichts zurückgegeben hat
                                // Versuche trotzdem als Erfolg zu behandeln, aber mit Verifizierung
                                logger.warn("setQuillContent hat null zurückgegeben, verifiziere Content...");
                            } else {
                                // Unbekanntes Ergebnis, versuche trotzdem als Erfolg zu behandeln
                                logger.warn("Unbekanntes Ergebnis von setQuillContent: " + result + ", behandle als Erfolg");
                                lastQuillContent = htmlContent;
                                success = true;
                            }
                            
                            // WICHTIG: Wenn erfolgreich, direkt Font-Einstellungen anwenden,
                            // damit die gewählte Schrift auch beim initialen Laden sichtbar ist
                            if (success) {
                                try {
                                    int savedFontSize = preferences.getInt("quillFontSize", 14);
                                    applyQuillGlobalFontSize(savedFontSize);
                                    String savedFontFamily = preferences.get("quillFontFamily", "Consolas");
                                    applyQuillGlobalFontFamily(savedFontFamily);
                                } catch (Exception e) {
                                    logger.warn("Konnte Quill Font-Einstellungen nicht anwenden: " + e.getMessage());
                                }
                            }
                            
                            // Wenn kein expliziter Erfolg, aber auch kein Fehler, setze lastQuillContent nach Verifizierung
                            if (!success && result == null) {
                                // Warte auf Verifizierung bevor wir lastQuillContent setzen
                                logger.info("Warte auf Verifizierung bevor lastQuillContent gesetzt wird...");
                            }
                            
                            // Zusätzlich: Prüfe ob Content wirklich gesetzt wurde
                            // WICHTIG: isUpdatingFromCodeArea muss sofort zurückgesetzt werden, damit Benutzer editieren kann
                            // Verifizierung kann im Hintergrund laufen, ohne isUpdatingFromCodeArea zu blockieren
                            Platform.runLater(() -> {
                                Timeline verifyTimeline = new Timeline(new KeyFrame(Duration.millis(300), event -> {
                                    try {
                                        Object currentContent = engine.executeScript("(function() { return window.getQuillContent ? window.getQuillContent() : ''; })()");
                                        int contentLength = currentContent instanceof String ? ((String)currentContent).length() : 0;
                                        logger.info("Verifizierung: Aktueller Quill Content-Länge: " + contentLength + ", erwartet: " + htmlContent.length());
                                        
                                        // Falls Content gesetzt wurde (auch wenn result null war), setze lastQuillContent
                                        if (contentLength > 10 || (contentLength > 0 && htmlContent.length() <= 10)) {
                                            if (lastQuillContent == null || !lastQuillContent.equals(htmlContent)) {
                                                lastQuillContent = htmlContent;
                                                logger.info("lastQuillContent nach erfolgreicher Verifizierung gesetzt");
                                            }
                                        } else if (contentLength <= 10 && htmlContent.length() > 10) {
                                            // Content ist leer, versuche erneut
                                            logger.warn("Content scheint nicht gesetzt worden zu sein, versuche erneut...");
                                            lastQuillContent = null;
                                            Timeline retryTimeline = new Timeline(new KeyFrame(Duration.millis(500), retryEvent -> {
                                                updateQuillContent();
                                            }));
                                            retryTimeline.play();
                                        }
                                    } catch (Exception e) {
                                        logger.debug("Fehler bei Verifizierung: " + e.getMessage());
                                    }
                                }));
                                verifyTimeline.play();
                            });
                            
                            // WICHTIG: isUpdatingFromCodeArea mit Verzögerung zurücksetzen, um sicherzustellen,
                            // dass alle DOM-Operationen in JavaScript abgeschlossen sind (verhindert Feedback-Schleife)
                            // Das JavaScript-Flag window.isUpdatingFromCodeArea blockiert bereits Events,
                            // aber wir halten das Java-Flag etwas länger, um sicherzugehen
                            // JavaScript setzt das Flag nach 250-400ms zurück, wir warten 500ms für Sicherheit
                            Timeline resetFlagTimeline = new Timeline(new KeyFrame(Duration.millis(500), event -> {
                                isUpdatingFromCodeArea = false;
                                // Setze auch JavaScript-Flag zurück (falls es noch gesetzt ist)
                                try {
                                    engine.executeScript("window.isUpdatingFromCodeArea = false;");
                                } catch (Exception e) {
                                    // Ignoriere Fehler
                                }
                                logger.debug("isUpdatingFromCodeArea zurückgesetzt nach Quill-Update");
                            }));
                            resetFlagTimeline.play();
                            
                        } catch (Exception e) {
                            logger.error("Fehler beim Aktualisieren des Quill Contents", e);
                            // WICHTIG: Auch bei Fehler isUpdatingFromCodeArea zurücksetzen
                            isUpdatingFromCodeArea = false;
                        }
                    });
                } else {
                    // Quill noch nicht bereit - warte und versuche erneut
                    logger.debug("Quill noch nicht bereit (ready: " + quillReady + ", exists: " + quillExists + "), warte...");
                    lastQuillContent = null; // WICHTIG: Zurücksetzen für Retry
                    Timeline retryTimeline = new Timeline(new KeyFrame(Duration.millis(500), event -> {
                        updateQuillContent();
                    }));
                    retryTimeline.play();
                }
            } else {
                // WebView noch nicht geladen - warte
                logger.debug("WebView noch nicht geladen, warte...");
                lastQuillContent = null; // WICHTIG: Zurücksetzen für Retry
                Timeline retryTimeline = new Timeline(new KeyFrame(Duration.millis(500), event -> {
                    updateQuillContent();
                }));
                retryTimeline.play();
            }
        } catch (Exception e) {
            logger.error("Fehler beim Aktualisieren des Quill Editors", e);
        }
    }
    
    /**
     * Konvertiert Markdown zu Quill-kompatiblem HTML
     */
    private String convertMarkdownToQuillHTML(String markdown) {
        // Nutze die bestehende Konvertierung, aber extrahiere nur den Body-Content
        String fullHTML = convertMarkdownToHTMLForPreview(markdown);
        
        // Extrahiere nur den Body-Inhalt (ohne HTML-Struktur)
        // Quill erwartet nur den Content, nicht das gesamte HTML-Dokument
        try {
            // Entferne HTML-Header und Body-Tags, behalte nur den Inhalt
            String bodyContent = fullHTML;
            int bodyStart = bodyContent.indexOf("<body>");
            int bodyEnd = bodyContent.indexOf("</body>");
            
            if (bodyStart >= 0 && bodyEnd >= 0) {
                bodyContent = bodyContent.substring(bodyStart + 6, bodyEnd);
            }
            
            return bodyContent.trim();
        } catch (Exception e) {
            logger.warn("Fehler beim Extrahieren des Body-Contents", e);
            return fullHTML;
        }
    }
    
    /**
     * Konvertiert Quill HTML zu Markdown (verbesserte Version mit vollständiger Formatierungsunterstützung)
     * @param html Das HTML von Quill
     * @param originalMarkdown Der ursprüngliche Markdown-Content (optional, für Bild-Pfad-Wiederherstellung)
     */
    private String convertQuillHTMLToMarkdown(String html, String originalMarkdown) {
        if (html == null || html.trim().isEmpty()) {
            return "";
        }
        
        try {
            
            // Schritt 0: Data-URI-Bilder behandeln - versuche ursprüngliche Pfade wiederherzustellen
            // Wenn originalMarkdown vorhanden ist, versuche die Bild-Pfade daraus zu extrahieren
            Map<String, String> dataUriToPath = new HashMap<>();
            if (originalMarkdown != null && !originalMarkdown.trim().isEmpty()) {
                // Finde alle Markdown-Bilder im ursprünglichen Content
                Pattern markdownImagePattern = Pattern.compile("!\\[([^\\]]*)\\]\\(([^)]+)\\)");
                Matcher markdownImageMatcher = markdownImagePattern.matcher(originalMarkdown);
                List<String> imagePaths = new ArrayList<>();
                while (markdownImageMatcher.find()) {
                    String imagePath = markdownImageMatcher.group(2);
                    imagePaths.add(imagePath);
                }
                
                // Finde alle Data-URI-Bilder im HTML und mappe sie zu den ursprünglichen Pfaden
                Pattern dataUriPattern = Pattern.compile("(?i)<img[^>]*src=\\\"data:image[^\\\"]*\\\"[^>]*(?:alt=\\\"([^\\\"]*)\\\")?[^>]*>");
                Matcher dataUriMatcher = dataUriPattern.matcher(html);
                int imageIndex = 0;
                while (dataUriMatcher.find() && imageIndex < imagePaths.size()) {
                    String altText = dataUriMatcher.group(1);
                    String originalPath = imagePaths.get(imageIndex);
                    String placeholder = "___DATAURI_IMG_" + imageIndex + "___";
                    dataUriToPath.put(placeholder, "![" + (altText != null ? altText : "") + "](" + originalPath + ")");
                    imageIndex++;
                }
            }
            
            // Ersetze Data-URI-Bilder durch Platzhalter oder entferne sie
            String cleaned = html;
            if (!dataUriToPath.isEmpty()) {
                // Ersetze durch Platzhalter für Wiederherstellung
                Pattern dataUriPattern = Pattern.compile("(?i)<img[^>]*src=\\\"data:image[^\\\"]*\\\"[^>]*(?:alt=\\\"([^\\\"]*)\\\")?[^>]*>");
                Matcher dataUriMatcher = dataUriPattern.matcher(cleaned);
                StringBuffer buffer = new StringBuffer();
                int imgIndex = 0;
                while (dataUriMatcher.find()) {
                    String placeholder = "___DATAURI_IMG_" + imgIndex + "___";
                    dataUriMatcher.appendReplacement(buffer, placeholder);
                    imgIndex++;
                }
                dataUriMatcher.appendTail(buffer);
                cleaned = buffer.toString();
            } else {
                // Keine ursprünglichen Pfade gefunden, entferne Base64-Bilder
                cleaned = cleaned.replaceAll("(?i)<img[^>]*src=\\\"data:image[^\\\"]*\\\"[^>]*>", "");
                cleaned = cleaned.replaceAll("(?i)<img[^>]*src='data:image[^']*'[^>]*>", "");
            }
            
            // Schritt 0.5: WICHTIG: Konvertiere Quill's script-Format zu <sub> und <sup> VOR dem Entfernen der class-Attribute
            // Quill verwendet <span class="ql-script" data-value="sub"> oder <span class="ql-script" data-value="super">
            // Oder auch nur class="ql-script" mit data-value
            // Verwende flexiblere Patterns, die auch mit verschiedenen Attribut-Reihenfolgen funktionieren
            cleaned = cleaned.replaceAll("(?i)<span[^>]*data-value=\"sub\"[^>]*class=\"[^\"]*ql-script[^\"]*\"[^>]*>(.*?)</span>", "<sub>$1</sub>");
            cleaned = cleaned.replaceAll("(?i)<span[^>]*class=\"[^\"]*ql-script[^\"]*\"[^>]*data-value=\"sub\"[^>]*>(.*?)</span>", "<sub>$1</sub>");
            cleaned = cleaned.replaceAll("(?i)<span[^>]*data-value=\"super\"[^>]*class=\"[^\"]*ql-script[^\"]*\"[^>]*>(.*?)</span>", "<sup>$1</sup>");
            cleaned = cleaned.replaceAll("(?i)<span[^>]*class=\"[^\"]*ql-script[^\"]*\"[^>]*data-value=\"super\"[^>]*>(.*?)</span>", "<sup>$1</sup>");
            
            // Schritt 0.6: WICHTIG: Konvertiere zentrierte Absätze zu <c> Tags VOR dem Entfernen der class/style-Attribute
            // WICHTIG: Blockquotes mit zentriertem Inhalt müssen ZUERST behandelt werden, damit das Blockquote erhalten bleibt
            // WICHTIG: Quill kann ql-align-center direkt im <blockquote> Tag setzen, nicht nur in <p> Tags!
            // Pattern: <blockquote> mit class="ql-align-center" oder style="text-align: center" direkt im Tag
            Pattern blockquoteWithCenterPattern = Pattern.compile("(?is)<blockquote[^>]*(?:class=\"[^\"]*ql-align-center[^\"]*\"|style=\"[^\"]*text-align\\s*:\\s*center[^\"]*\")[^>]*>(.*?)</blockquote>");
            Matcher blockquoteWithCenterMatcher = blockquoteWithCenterPattern.matcher(cleaned);
            StringBuffer blockquoteWithCenterBuffer = new StringBuffer();
            while (blockquoteWithCenterMatcher.find()) {
                String content = blockquoteWithCenterMatcher.group(1);
                // Extrahiere den Text - entferne HTML-Tags, behalte Text
                String text = content.replaceAll("<[^>]+>", "").trim();
                if (!text.isEmpty()) {
                    blockquoteWithCenterMatcher.appendReplacement(blockquoteWithCenterBuffer, "><c>" + text + "</c>");
                } else {
                    blockquoteWithCenterMatcher.appendReplacement(blockquoteWithCenterBuffer, "> ");
                }
            }
            blockquoteWithCenterMatcher.appendTail(blockquoteWithCenterBuffer);
            cleaned = blockquoteWithCenterBuffer.toString();
            
            // Pattern: <blockquote> enthält <p> mit ql-align-center oder text-align: center (Fallback für verschachtelte Zentrierung)
            cleaned = cleaned.replaceAll("(?is)<blockquote[^>]*>\\s*<p[^>]*class=\"[^\"]*ql-align-center[^\"]*\"[^>]*>(.*?)</p>\\s*</blockquote>", "><c>$1</c>");
            cleaned = cleaned.replaceAll("(?is)<blockquote[^>]*>\\s*<p[^>]*style=\"[^\"]*text-align:\\s*center[^\"]*\"[^>]*>(.*?)</p>\\s*</blockquote>", "><c>$1</c>");
            cleaned = cleaned.replaceAll("(?is)<blockquote[^>]*>\\s*<div[^>]*style=\"[^\"]*text-align:\\s*center[^\"]*\"[^>]*>(.*?)</div>\\s*</blockquote>", "><c>$1</c>");
            // Auch mit beiden Attributen (class und style) innerhalb von Blockquotes
            cleaned = cleaned.replaceAll("(?is)<blockquote[^>]*>\\s*<p[^>]*class=\"[^\"]*ql-align-center[^\"]*\"[^>]*style=\"[^\"]*\"[^>]*>(.*?)</p>\\s*</blockquote>", "><c>$1</c>");
            cleaned = cleaned.replaceAll("(?is)<blockquote[^>]*>\\s*<p[^>]*style=\"[^\"]*\"[^>]*class=\"[^\"]*ql-align-center[^\"]*\"[^>]*>(.*?)</p>\\s*</blockquote>", "><c>$1</c>");
            
            // #region agent log (debug-session)
            // Prüfe nach Schritt 0.6: wurden Blockquotes mit Zentrierung konvertiert?
            try {
                boolean afterStep06HasBlockquote = cleaned.toLowerCase().contains("<blockquote");
                boolean afterStep06HasCTag = cleaned.contains("><c>") || cleaned.contains("> <c>");
                String msg = "after step0.6: has blockquote=" + afterStep06HasBlockquote + ", has ><c>=" + afterStep06HasCTag + ", sample=" + (cleaned.length() > 200 ? cleaned.substring(0, 200) : cleaned);
                String safeMsg = msg.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
                String line = "{\"sessionId\":\"debug-session\",\"runId\":\"blockquote-center-fix\",\"hypothesisId\":\"H2\",\"location\":\"EditorWindow.java:convertQuillHTMLToMarkdown\",\"message\":\"" + safeMsg + "\",\"data\":{},\"timestamp\":" + System.currentTimeMillis() + "}\n";
                java.nio.file.Files.write(java.nio.file.Paths.get("g:\\\\workspace\\\\manuskript\\\\.cursor\\\\debug.log"), line.getBytes(java.nio.charset.StandardCharsets.UTF_8), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            } catch (Exception ignored) {}
            // #endregion
            
            // Dann: Normale zentrierte Absätze (ohne Blockquote)
            // Quill verwendet entweder:
            // 1. <p class="ql-align-center"> (Quill-Format)
            // 2. <p style="text-align: center;"> (HTML-Format)
            // 3. <div style="text-align: center;"> (HTML-Format)
            // Pattern muss flexibel sein für verschiedene Attribut-Reihenfolgen und auch andere style-Attribute
            // Verwende DOTALL-Modus für mehrzeilige Inhalte
            // WICHTIG: Als eigener Absatz ausgeben (mit Leerzeilen), sonst klebt </c> direkt am Folgetext
            // und der Rückweg Markdown->HTML->Quill kann die Zentrierung nicht sauber als Block abbilden.
            cleaned = cleaned.replaceAll("(?is)<p[^>]*class=\"[^\"]*ql-align-center[^\"]*\"[^>]*>(.*?)</p>", "\n\n<c>$1</c>\n\n");
            cleaned = cleaned.replaceAll("(?is)<p[^>]*style=\"[^\"]*text-align:\\s*center[^\"]*\"[^>]*>(.*?)</p>", "\n\n<c>$1</c>\n\n");
            cleaned = cleaned.replaceAll("(?is)<div[^>]*style=\"[^\"]*text-align:\\s*center[^\"]*\"[^>]*>(.*?)</div>", "\n\n<c>$1</c>\n\n");
            // Auch mit beiden Attributen (class und style)
            cleaned = cleaned.replaceAll("(?is)<p[^>]*class=\"[^\"]*ql-align-center[^\"]*\"[^>]*style=\"[^\"]*\"[^>]*>(.*?)</p>", "\n\n<c>$1</c>\n\n");
            cleaned = cleaned.replaceAll("(?is)<p[^>]*style=\"[^\"]*\"[^>]*class=\"[^\"]*ql-align-center[^\"]*\"[^>]*>(.*?)</p>", "\n\n<c>$1</c>\n\n");
            
            // Schritt 0.7: WICHTIG: Konvertiere Farb-Spans zu Farb-Tags VOR dem Entfernen der style-Attribute
            // Quill verwendet <span style="color: red">Text</span> oder Quill's eigene Format-Attribute
            // WICHTIG: Pattern muss flexibel sein für verschiedene Attribut-Reihenfolgen
            // Verwende DOTALL-Modus für mehrzeilige Inhalte und behandele auch verschachtelte Tags
            // Pattern: style kann verschiedene Formate haben (color: red, color:red, color: red; etc.)
            cleaned = cleaned.replaceAll("(?is)<span[^>]*style=\"[^\"]*color:\\s*red[^\"]*\"[^>]*>(.*?)</span>", "<red>$1</red>");
            cleaned = cleaned.replaceAll("(?is)<span[^>]*style=\"[^\"]*color:\\s*blue[^\"]*\"[^>]*>(.*?)</span>", "<blue>$1</blue>");
            cleaned = cleaned.replaceAll("(?is)<span[^>]*style=\"[^\"]*color:\\s*green[^\"]*\"[^>]*>(.*?)</span>", "<green>$1</green>");
            cleaned = cleaned.replaceAll("(?is)<span[^>]*style=\"[^\"]*color:\\s*yellow[^\"]*\"[^>]*>(.*?)</span>", "<yellow>$1</yellow>");
            cleaned = cleaned.replaceAll("(?is)<span[^>]*style=\"[^\"]*color:\\s*purple[^\"]*\"[^>]*>(.*?)</span>", "<purple>$1</purple>");
            cleaned = cleaned.replaceAll("(?is)<span[^>]*style=\"[^\"]*color:\\s*orange[^\"]*\"[^>]*>(.*?)</span>", "<orange>$1</orange>");
            cleaned = cleaned.replaceAll("(?is)<span[^>]*style=\"[^\"]*color:\\s*gray[^\"]*\"[^>]*>(.*?)</span>", "<gray>$1</gray>");
            cleaned = cleaned.replaceAll("(?is)<span[^>]*style=\"[^\"]*color:\\s*grey[^\"]*\"[^>]*>(.*?)</span>", "<gray>$1</gray>");
            
            // Schritt 0.8: WICHTIG: Konvertiere Mark-Tags (Highlight) zu Markdown-Syntax VOR dem Entfernen der style-Attribute
            // Quill verwendet <mark style="background-color: yellow">Text</mark> oder nur <mark>Text</mark>
            // Konvertiere zu ==Text== (Markdown Highlight-Syntax)
            // WICHTIG: Verwende DOTALL-Modus für mehrzeilige Inhalte
            cleaned = cleaned.replaceAll("(?is)<mark[^>]*>(.*?)</mark>", "==$1==");
            
            // Schritt 1: Entferne Quill-spezifische Attribute, behalte aber Struktur (bild-SRC bleibt für normale Pfade unverändert)
            // WICHTIG: style-Attribute werden entfernt, aber <c>, Farb-Tags und ==text== bleiben erhalten
            cleaned = cleaned
                .replaceAll("class=\"[^\"]*\"", "")
                .replaceAll("style=\"[^\"]*\"", "")
                .replaceAll("data-id=\"[^\"]*\"", "")
                .replaceAll("spellcheck=\"[^\"]*\"", "");

            
            // Schritt 2: Behandle verschachtelte Strukturen (Listen, Blockquotes)
            StringBuilder markdown = new StringBuilder();
            
            // Verwende einen Parser-Ansatz für bessere Behandlung von verschachtelten Elementen
            // Zuerst: Code-Blöcke (müssen zuerst behandelt werden, da sie andere Formatierungen enthalten können)
            Pattern codeBlockPattern = Pattern.compile("<pre[^>]*><code[^>]*>(.*?)</code></pre>", Pattern.DOTALL);
            Matcher codeBlockMatcher = codeBlockPattern.matcher(cleaned);
            StringBuffer codeBlockBuffer = new StringBuffer();
            while (codeBlockMatcher.find()) {
                String codeContent = codeBlockMatcher.group(1);
                // HTML Entities in Code-Blöcken decodieren
                codeContent = codeContent.replace("&lt;", "<")
                                        .replace("&gt;", ">")
                                        .replace("&amp;", "&");
                codeBlockMatcher.appendReplacement(codeBlockBuffer, "```\n" + codeContent + "\n```\n");
            }
            codeBlockMatcher.appendTail(codeBlockBuffer);
            cleaned = codeBlockBuffer.toString();
            
            // Schritt 3: Headings (müssen vor anderen Formatierungen behandelt werden)
            cleaned = cleaned.replaceAll("(?i)<h1[^>]*>(.*?)</h1>", "# $1\n");
            cleaned = cleaned.replaceAll("(?i)<h2[^>]*>(.*?)</h2>", "## $1\n");
            cleaned = cleaned.replaceAll("(?i)<h3[^>]*>(.*?)</h3>", "### $1\n");
            cleaned = cleaned.replaceAll("(?i)<h4[^>]*>(.*?)</h4>", "#### $1\n");
            cleaned = cleaned.replaceAll("(?i)<h5[^>]*>(.*?)</h5>", "##### $1\n");
            cleaned = cleaned.replaceAll("(?i)<h6[^>]*>(.*?)</h6>", "###### $1\n");
            
            // Schritt 4: Blockquotes
            // WICHTIG: Blockquotes mit zentriertem Inhalt wurden bereits in Schritt 0.6 zu ><c>Text</c> konvertiert
            // Diese sollten keine <blockquote> Tags mehr haben. Konvertiere nur noch verbleibende Blockquotes ohne Zentrierung
            // Pattern: Blockquote, das NICHT ql-align-center oder text-align: center enthält
            // Verwende Pattern und Matcher für komplexere Logik
            Pattern blockquotePattern = Pattern.compile("(?is)<blockquote[^>]*>(.*?)</blockquote>");
            Matcher blockquoteMatcher = blockquotePattern.matcher(cleaned);
            StringBuffer blockquoteBuffer = new StringBuffer();
            while (blockquoteMatcher.find()) {
                String inner = blockquoteMatcher.group(1);
                // Prüfe, ob der Inhalt ql-align-center oder text-align: center enthält
                // Wenn ja, wurde es bereits in Schritt 0.6 konvertiert, sollte aber nicht mehr hier sein
                // Falls es doch hier ist, bedeutet das, dass die Patterns in Schritt 0.6 es nicht erkannt haben
                // In diesem Fall sollten wir es trotzdem zu ><c>Text</c> konvertieren
                if (inner.matches("(?is).*(?:ql-align-center|text-align\\s*:\\s*center).*")) {
                    // Sollte bereits konvertiert sein, aber falls nicht, konvertiere es jetzt
                    // Extrahiere den Text aus dem <p> Tag
                    Pattern centerPattern = Pattern.compile("(?is)<p[^>]*>(.*?)</p>");
                    Matcher centerMatcher = centerPattern.matcher(inner);
                    if (centerMatcher.find()) {
                        String centerText = centerMatcher.group(1);
                        blockquoteMatcher.appendReplacement(blockquoteBuffer, "><c>" + centerText + "</c>");
                    } else {
                        // Kein <p> Tag gefunden, verwende den gesamten Inhalt
                        blockquoteMatcher.appendReplacement(blockquoteBuffer, "><c>" + inner + "</c>");
                    }
                } else {
                    // WICHTIG: Wenn der Inhalt nur ein einzelnes <p> Tag enthält (ohne Zentrierung),
                    // könnte es ursprünglich zentriert gewesen sein, aber Quill hat die Zentrierung verloren.
                    // Prüfe, ob es genau ein <p> Tag gibt
                    Pattern singlePPattern = Pattern.compile("(?is)^\\s*<p[^>]*>(.*?)</p>\\s*$");
                    Matcher singlePMatcher = singlePPattern.matcher(inner.trim());
                    if (singlePMatcher.matches()) {
                        // Einzelnes <p> Tag - könnte ursprünglich zentriert gewesen sein
                        // Konvertiere zu ><c>Text</c> um die Zentrierung zu erhalten
                        String text = singlePMatcher.group(1);
                        blockquoteMatcher.appendReplacement(blockquoteBuffer, "><c>" + text + "</c>");
                    } else {
                        // Nicht konvertiert, konvertiere zu > Text
                        blockquoteMatcher.appendReplacement(blockquoteBuffer, "> " + inner + "\n");
                    }
                }
            }
            blockquoteMatcher.appendTail(blockquoteBuffer);
            cleaned = blockquoteBuffer.toString();
            
            
            // Schritt 5: Listen (geordnet und ungeordnet)
            // Behandle <ol> und <ul> Container
            cleaned = cleaned.replaceAll("(?i)</?ol[^>]*>", "\n");
            cleaned = cleaned.replaceAll("(?i)</?ul[^>]*>", "\n");
            // List Items
            cleaned = cleaned.replaceAll("(?i)<li[^>]*>(.*?)</li>", "- $1\n");
            
            // Schritt 6: Inline-Formatierungen (müssen nach Block-Elementen behandelt werden)
            // Strikethrough (Quill verwendet <s> oder <strike>)
            cleaned = cleaned.replaceAll("(?i)<s[^>]*>(.*?)</s>", "~~$1~~");
            cleaned = cleaned.replaceAll("(?i)<strike[^>]*>(.*?)</strike>", "~~$1~~");
            
            // Bold und Italic (mehrfach verschachtelt möglich)
            // Zuerst Bold+Italic kombinierungen
            cleaned = cleaned.replaceAll("(?i)<strong[^>]*><em[^>]*>(.*?)</em></strong>", "***$1***");
            cleaned = cleaned.replaceAll("(?i)<em[^>]*><strong[^>]*>(.*?)</strong></em>", "***$1***");
            cleaned = cleaned.replaceAll("(?i)<b[^>]*><i[^>]*>(.*?)</i></b>", "***$1***");
            cleaned = cleaned.replaceAll("(?i)<i[^>]*><b[^>]*>(.*?)</b></i>", "***$1***");
            
            // Dann einzelne Formatierungen
            cleaned = cleaned.replaceAll("(?i)<strong[^>]*>(.*?)</strong>", "**$1**");
            cleaned = cleaned.replaceAll("(?i)<b[^>]*>(.*?)</b>", "**$1**");
            cleaned = cleaned.replaceAll("(?i)<em[^>]*>(.*?)</em>", "*$1*");
            cleaned = cleaned.replaceAll("(?i)<i[^>]*>(.*?)</i>", "*$1*");
            
            // Underline (Quill-spezifisch, wird als <u> dargestellt)
            cleaned = cleaned.replaceAll("(?i)<u[^>]*>(.*?)</u>", "$1"); // Markdown unterstützt kein Underline
            
            // Schritt 7: Links
            cleaned = cleaned.replaceAll("(?i)<a[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>", "[$2]($1)");
            
            // Schritt 8: Superscript und Subscript (bereits in Schritt 0.5 konvertiert, hier nur noch direkte Tags behalten)
            // <sub> und <sup> Tags bleiben erhalten (keine weitere Konvertierung nötig)
            
            // Schritt 9: Inline Code (muss nach anderen Formatierungen behandelt werden)
            cleaned = cleaned.replaceAll("(?i)<code[^>]*>(.*?)</code>", "`$1`");
            
            // Schritt 10: Images (doppelte und einfache Anführungszeichen)
            // WICHTIG: Konvertiere Bilder zu Markdown VOR dem Entfernen der HTML-Tags
            // Behandle auch Bilder mit verschiedenen Attribut-Reihenfolgen
            
            // Zuerst: Stelle Data-URI-Bilder wieder her (falls ursprüngliche Pfade gefunden wurden)
            for (Map.Entry<String, String> entry : dataUriToPath.entrySet()) {
                cleaned = cleaned.replace(entry.getKey(), entry.getValue());
            }
            
            // Dann: Normale Bilder (nicht Data-URI) zu Markdown konvertieren
            cleaned = cleaned.replaceAll("(?i)<img[^>]*src=\"([^\"]+)\"[^>]*(?:alt=\"([^\"]*)\")?[^>]*>", "![$2]($1)");
            cleaned = cleaned.replaceAll("(?i)<img[^>]*src='([^']+)'[^>]*(?:alt='([^']*)')?[^>]*>", "![$2]($1)");
            // Auch ohne alt-Attribut
            cleaned = cleaned.replaceAll("(?i)<img[^>]*src=\"([^\"]+)\"[^>]*>", "![]($1)");
            cleaned = cleaned.replaceAll("(?i)<img[^>]*src='([^']+)'[^>]*>", "![]($1)");
            
            // Schritt 11: Horizontale Linien
            // Konvertiere <hr> und <div> mit border-top (Quill-kompatible Darstellung) zu ---
            cleaned = cleaned.replaceAll("(?i)<div[^>]*style=\"[^\"]*border-top[^\"]*\"[^>]*></div>", "\n---\n");
            cleaned = cleaned.replaceAll("(?i)<p[^>]*>\\s*<hr[^>]*>\\s*</hr>\\s*</p>", "\n---\n");
            cleaned = cleaned.replaceAll("(?i)<p[^>]*>\\s*<hr[^>]*>\\s*</p>", "\n---\n");
            cleaned = cleaned.replaceAll("(?i)<hr[^>]*>", "\n---\n");
            
            // Schritt 12.5: WICHTIG: Schütze <sub>, <sup> und <c> Tags VOR dem Entfernen der <p> Tags
            // Temporäre Platzhalter verwenden
            cleaned = cleaned.replaceAll("(?i)<sub>", "___SUBTAG_START___");
            cleaned = cleaned.replaceAll("(?i)</sub>", "___SUBTAG_END___");
            cleaned = cleaned.replaceAll("(?i)<sup>", "___SUPTAG_START___");
            cleaned = cleaned.replaceAll("(?i)</sup>", "___SUPTAG_END___");
            // WICHTIG: Schütze <c> Tags VOR dem Entfernen der <p> Tags, damit sie nicht verloren gehen
            cleaned = cleaned.replaceAll("(?i)<c>", "___CTAG_START___");
            cleaned = cleaned.replaceAll("(?i)</c>", "___CTAG_END___");
            
            // Schritt 12: Paragraphs und Line Breaks (Absatz-Trenner als Doppel-NE behalten)
            // WICHTIG: <c> Tags wurden bereits geschützt, können jetzt sicher entfernt werden
            cleaned = cleaned.replaceAll("(?i)<p[^>]*>", "");
            cleaned = cleaned.replaceAll("(?i)</p>", "\n\n");
            cleaned = cleaned.replaceAll("(?i)<br\\s*/?>", "\n");
            cleaned = cleaned.replaceAll("(?i)<div[^>]*>", "");
            cleaned = cleaned.replaceAll("(?i)</div>", "\n\n");
            
            // Schritt 12.6: Entferne verbleibende HTML-Tags (aber nicht die Platzhalter)
            cleaned = cleaned.replaceAll("<[^>]+>", "");
            
            // Schritt 12.7: Stelle <sub>, <sup> und <c> Tags wieder her
            cleaned = cleaned.replaceAll("___SUBTAG_START___", "<sub>");
            cleaned = cleaned.replaceAll("___SUBTAG_END___", "</sub>");
            cleaned = cleaned.replaceAll("___SUPTAG_START___", "<sup>");
            cleaned = cleaned.replaceAll("___SUPTAG_END___", "</sup>");
            cleaned = cleaned.replaceAll("___CTAG_START___", "<c>");
            cleaned = cleaned.replaceAll("___CTAG_END___", "</c>");
            
            // WICHTIG: Stelle sicher, dass ><c>Tags</c> am Ende ein \n haben
            // Pattern: ><c> gefolgt von beliebigem Text (auch mit anderen Tags), dann </c>
            // Ersetze durch ><c>Text</c>\n wenn kein \n danach kommt
            // Verwende DOTALL-Modus für mehrzeilige Inhalte
            cleaned = cleaned.replaceAll("(><c>.*?</c>)(?=\\s*$|\\s*[^\\n\\r])", "$1\n");
            
            // Schritt 13: HTML Entities decodieren
            cleaned = cleaned.replace("&nbsp;", " ")
                           .replace("&lt;", "<")
                           .replace("&gt;", ">")
                           .replace("&amp;", "&")
                           .replace("&quot;", "\"")
                           .replace("&#39;", "'")
                           .replace("&apos;", "'");
            
            // Schritt 14: Entferne nur führende Leerzeilen, behalte abschließende \n
            // WICHTIG: trim() würde auch abschließende \n entfernen, was bei Blockquotes wie ><c>Text</c> problematisch ist
            // Entferne nur führende Whitespaces, aber behalte abschließende \n
            
            // WICHTIG: Stelle sicher, dass ><c>Tags</c> am Ende ein \n haben (auch nach anderen Operationen)
            // Prüfe, ob der Text mit ></c> endet (ohne \n danach)
            if (cleaned.endsWith("></c>") || (cleaned.contains("><c>") && cleaned.matches(".*></c>\\s*$") && !cleaned.endsWith("\n"))) {
                // Füge \n am Ende hinzu, wenn Text mit ></c> endet
                cleaned = cleaned + "\n";
            }
            
            cleaned = cleaned.replaceAll("^\\s+", ""); // Entferne führende Whitespaces
            
            // Entferne nur mehrfache abschließende Leerzeilen (mehr als 2), behalte einzelnes \n
            cleaned = cleaned.replaceAll("\\n{3,}$", "\n\n"); // Maximal 2 abschließende \n behalten
            
            return cleaned;
        } catch (Exception e) {
            logger.error("Fehler bei HTML zu Markdown Konvertierung", e);
            // Fallback: Entferne alle HTML-Tags
            return html.replaceAll("<[^>]+>", "").replaceAll("&nbsp;", " ")
                      .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
        }
    }
    
    /**
     * Hilfsmethode: Konvertiert String zu JavaScript-String
     */
    private String toJSString(String str) {
        if (str == null) return "null";
        return "\"" + str.replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r")
                        .replace("\t", "\\t") + "\"";
    }
    
    /**
     * Aktualisiert den Preview-Inhalt (Legacy-Methode - wird durch updateQuillContent ersetzt)
     */
    private void updatePreviewContent() {
        // Verhindere Updates während des Umschaltens
        if (isTogglingViewMode) {
            return;
        }
        if (useQuillMode) {
            // Quill-Modus: Quill Content aktualisieren
        updateQuillContent();
        } else {
            // Normaler Modus: Einfaches HTML laden
            if (previewWebView != null && codeArea != null) {
                try {
                    String markdownContent = codeArea.getText();
                    String htmlContent = convertMarkdownToHTMLForPreview(markdownContent);
                    previewWebView.getEngine().loadContent(htmlContent, "text/html");
                } catch (Exception e) {
                    logger.error("Fehler beim Aktualisieren des normalen Preview-Contents", e);
                }
            }
        }
    }
    
    /**
     * Legacy-Methode für Preview-Update (alte Implementierung entfernt)
     * Wird durch updateQuillContent ersetzt
     */
    private void updatePreviewContentLegacy() {
        if (previewWebView == null || codeArea == null) {
            return;
        }
        
        try {
            String markdownContent = codeArea.getText();
            String htmlContent = convertMarkdownToHTMLForPreview(markdownContent);
            
            // Nur aktualisieren, wenn sich der Content geändert hat
            if (htmlContent.equals(lastPreviewContent)) {
                return; // Keine Änderung, kein Update nötig
            }
            
            lastPreviewContent = htmlContent;
            
            // Aktuelle Scroll-Position speichern
            double savedScrollTop = 0.0;
            try {
                if (previewWebView.getEngine().getLoadWorker().getState() == javafx.concurrent.Worker.State.SUCCEEDED) {
                    Object scrollTopObj = previewWebView.getEngine().executeScript("window.pageYOffset || document.documentElement.scrollTop");
                    if (scrollTopObj instanceof Number) {
                        savedScrollTop = ((Number) scrollTopObj).doubleValue();
                    }
                }
            } catch (Exception e) {
                logger.debug("Fehler beim Speichern der Scroll-Position: " + e.getMessage());
            }
            
            final double scrollTopToRestore = savedScrollTop;
            
            // Entferne alten Listener falls vorhanden (vor dem Laden)
            if (previewLoadListener != null) {
                previewWebView.getEngine().getLoadWorker().stateProperty().removeListener(previewLoadListener);
            }
            
            // Neuer Listener für Scroll-Position (vor dem Laden hinzufügen)
            previewLoadListener = (obs, oldState, newState) -> {
                if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                    Platform.runLater(() -> {
                        try {
                            // Mehrfache Versuche mit Verzögerung, damit das Layout fertig ist
                            Timeline timeline = new Timeline(
                                new KeyFrame(Duration.millis(50), event -> {
                                    try {
                                        String script = String.format("window.scrollTo(0, %f);", scrollTopToRestore);
                                        previewWebView.getEngine().executeScript(script);
                                    } catch (Exception e) {
                                        logger.debug("Fehler beim Wiederherstellen der Scroll-Position (1. Versuch): " + e.getMessage());
                                    }
                                }),
                                new KeyFrame(Duration.millis(150), event -> {
                                    try {
                                        String script = String.format("window.scrollTo(0, %f);", scrollTopToRestore);
                                        previewWebView.getEngine().executeScript(script);
                                    } catch (Exception e) {
                                        logger.debug("Fehler beim Wiederherstellen der Scroll-Position (2. Versuch): " + e.getMessage());
                                    }
                                }),
                                new KeyFrame(Duration.millis(300), event -> {
                                    try {
                                        String script = String.format("window.scrollTo(0, %f);", scrollTopToRestore);
                                        previewWebView.getEngine().executeScript(script);
                                    } catch (Exception e) {
                                        logger.debug("Fehler beim Wiederherstellen der Scroll-Position (3. Versuch): " + e.getMessage());
                                    }
                                })
                            );
                            timeline.play();
                        } catch (Exception e) {
                            logger.debug("Fehler beim Wiederherstellen der Scroll-Position: " + e.getMessage());
                        }
                    });
                }
            };
            
            // Listener hinzufügen BEVOR loadContent() aufgerufen wird
            previewWebView.getEngine().getLoadWorker().stateProperty().addListener(previewLoadListener);
            
            // Content laden
            previewWebView.getEngine().loadContent(htmlContent, "text/html");
            
            // Falls der State bereits SUCCEEDED ist (sollte nicht passieren, aber sicherheitshalber)
            if (previewWebView.getEngine().getLoadWorker().getState() == javafx.concurrent.Worker.State.SUCCEEDED) {
                Platform.runLater(() -> {
                    try {
                        Timeline timeline = new Timeline(new KeyFrame(Duration.millis(100), event -> {
                            try {
                                String script = String.format("window.scrollTo(0, %f);", scrollTopToRestore);
                                previewWebView.getEngine().executeScript(script);
                            } catch (Exception e) {
                                logger.debug("Fehler beim Wiederherstellen der Scroll-Position (Fallback): " + e.getMessage());
                            }
                        }));
                        timeline.play();
                    } catch (Exception e) {
                        logger.debug("Fehler beim Wiederherstellen der Scroll-Position (Fallback): " + e.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            logger.error("Fehler beim Aktualisieren des Previews", e);
        }
    }
    
    /**
     * Konvertiert Markdown zu HTML für Preview mit verbessertem Leerzeilen-Handling und Bild-Pfad-Konvertierung
     */
    private String convertMarkdownToHTMLForPreview(String markdown) {
        StringBuilder html = new StringBuilder();
        
        // Font-Einstellungen aus Preferences oder UI-Elementen holen
        int fontSize = 14;
        String fontFamily = "Consolas";
        if (spnQuillFontSize != null && spnQuillFontSize.getValue() != null) {
            fontSize = spnQuillFontSize.getValue();
        } else if (preferences != null) {
            fontSize = preferences.getInt("quillFontSize", 14);
        }
        if (cmbQuillFontFamily != null && cmbQuillFontFamily.getValue() != null) {
            fontFamily = cmbQuillFontFamily.getValue();
        } else if (preferences != null) {
            fontFamily = preferences.get("quillFontFamily", "Consolas");
        }
        
        // CSS-kompatible Font-Familie
        String cssFontFamily;
        if (fontFamily.equals("Consolas")) {
            cssFontFamily = "'Consolas', 'Monaco', monospace";
        } else if (fontFamily.equals("Times New Roman")) {
            cssFontFamily = "'Times New Roman', serif";
        } else if (fontFamily.equals("Courier New")) {
            cssFontFamily = "'Courier New', monospace";
        } else if (fontFamily.equals("Arial")) {
            cssFontFamily = "Arial, sans-serif";
        } else if (fontFamily.equals("Verdana")) {
            cssFontFamily = "Verdana, sans-serif";
        } else if (fontFamily.equals("Georgia")) {
            cssFontFamily = "Georgia, serif";
        } else {
            cssFontFamily = fontFamily;
        }
        
        // HTML-Header
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"de\">\n");
        html.append("<head>\n");
        html.append("    <meta charset=\"UTF-8\">\n");
        html.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("    <title>Preview</title>\n");
        html.append("    <style>\n");
        String textAlign = previewJustifyEnabled ? "justify" : "left";
        html.append("        body { font-family: ").append(cssFontFamily).append("; font-size: ").append(fontSize).append("px; line-height: 1.6; margin: 0; padding: 2em 4em; max-width: 1200px; margin-left: auto; margin-right: auto; text-align: ").append(textAlign).append("; }\n");
        html.append("        p { text-align: ").append(textAlign).append("; }\n");
        html.append("        h1, h2, h3, h4 { color: #2c3e50; }\n");
        html.append("        h1 { border-bottom: 2px solid #3498db; padding-bottom: 10px; }\n");
        html.append("        h2 { border-bottom: 1px solid #bdc3c7; padding-bottom: 5px; }\n");
        html.append("        code { background-color: #f8f9fa; padding: 2px 4px; border-radius: 3px; }\n");
        html.append("        pre { background-color: #f8f9fa; padding: 15px; border-radius: 5px; overflow-x: auto; }\n");
        html.append("        blockquote { margin: 10px 0; padding-left: 30px; color: #999; font-style: italic; }\n");
        html.append("        hr { border: none; border-top: 2px solid #bdc3c7; margin: 20px 0; }\n");
        html.append("        ul, ol { margin: 10px 0; padding-left: 20px; }\n");
        html.append("        li { margin: 5px 0; }\n");
        html.append("        ol ol { list-style-type: lower-alpha; }\n");
        html.append("        ol ol ol { list-style-type: lower-roman; }\n");
        html.append("        ol ol ol ol { list-style-type: decimal; }\n");
        html.append("        table { border-collapse: collapse; width: 100%; margin: 15px 0; }\n");
        html.append("        th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }\n");
        html.append("        th { background-color: #f2f2f2; }\n");
        html.append("        .strikethrough { text-decoration: line-through; }\n");
        html.append("        .highlight { background-color: yellow; }\n");
        html.append("        img { max-width: 100%; height: auto; }\n");
        html.append("    </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        
        // WICHTIG: Ersetze <c> / <center> direkt vor der zeilenweisen Verarbeitung
        // DOTALL + case-insensitive, damit auch mehrzeilige Inhalte und Groß/Kleinschreibung funktionieren
        // WICHTIG: Blockquotes mit > am Zeilenanfang müssen VOR der <center>-Konvertierung erkannt werden
        // Pattern: Zeile beginnt mit > gefolgt von optionalen Leerzeichen und <center> oder <c>
        Pattern blockquoteCenterPattern = Pattern.compile("(?m)^(>\\s*)<\\s*(?:c|center)\\s*>(.*?)</\\s*(?:c|center)\\s*>");
        Matcher blockquoteCenterMatcher = blockquoteCenterPattern.matcher(markdown);
        StringBuffer blockquoteCenterBuffer = new StringBuffer();
        while (blockquoteCenterMatcher.find()) {
            // Ersetze Zeilenumbrüche im Inhalt durch Leerzeichen, damit sie als ein Absatz behandelt werden
            String inner = blockquoteCenterMatcher.group(2).replaceAll("\\s*\\n\\s*", " ").trim();
            // Behalte das > am Anfang und konvertiere <center> zu <p style="text-align: center;">
            String replacement = blockquoteCenterMatcher.group(1) + "<p style=\"text-align: center;\">" + inner + "</p>";
            blockquoteCenterMatcher.appendReplacement(blockquoteCenterBuffer, Matcher.quoteReplacement(replacement));
        }
        blockquoteCenterMatcher.appendTail(blockquoteCenterBuffer);
        markdown = blockquoteCenterBuffer.toString();
        
        // Dann: Normale <center>-Konvertierung (ohne Blockquote)
        Pattern centerPattern = Pattern.compile("(?is)<\\s*(?:c|center)\\s*>(.*?)</\\s*(?:c|center)\\s*>");
        Matcher centerMatcher = centerPattern.matcher(markdown);
        StringBuffer centerBuffer = new StringBuffer();
        while (centerMatcher.find()) {
            // Ersetze Zeilenumbrüche im Inhalt durch Leerzeichen, damit sie als ein Absatz behandelt werden
            String inner = centerMatcher.group(1).replaceAll("\\s*\\n\\s*", " ").trim();
            String replacement = "<p style=\"text-align: center;\">" + inner + "</p>";
            centerMatcher.appendReplacement(centerBuffer, Matcher.quoteReplacement(replacement));
        }
        centerMatcher.appendTail(centerBuffer);
        markdown = centerBuffer.toString();
        
        // Einheitliche Zeilenumbrüche (Windows \r\n, altes Mac \r -> \n) für zuverlässige Tabellen-Erkennung
        markdown = markdown.replace("\r\n", "\n").replace("\r", "\n");
        
        // Markdown zu HTML konvertieren
        String[] lines = markdown.split("\n");
        boolean inCodeBlock = false;
        boolean inBlockquote = false;
        boolean inTable = false;
        boolean inParagraph = false;
        StringBuilder tableContent = new StringBuilder();
        String[] tableAlignments = null; // Speichert die Ausrichtung für jede Spalte
        
        // Stack für verschachtelte Listen (0 = keine Liste, 1 = erste Ebene, etc.)
        List<Integer> listStack = new ArrayList<>(); // 1 = unordered, 2 = ordered
        List<Integer> indentStack = new ArrayList<>(); // Einrückungsebene
        boolean previousWasListItem = false; // Merkt, ob die vorherige Zeile ein Listenelement war
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmedLine = line.trim();
            
            // Horizontale Linien (---, ***, oder ___)
            // Pattern: Mindestens 3 Bindestriche, Sterne oder Unterstriche, optional mit Leerzeichen
            if (trimmedLine.matches("^[-*_]{3,}\\s*$")) {
                if (inParagraph) {
                    html.append("</p>\n");
                    inParagraph = false;
                }
                // Verwende <hr> Tag - wird durch benutzerdefiniertes Blot unterstützt
                html.append("<hr>\n");
                continue;
            }
            
            // Tabellen
            if (trimmedLine.contains("|") && !trimmedLine.startsWith("```")) {
                if (inParagraph) {
                    html.append("</p>\n");
                    inParagraph = false;
                }
                if (!inTable) {
                    inTable = true;
                    tableContent = new StringBuilder();
                    tableContent.append("<table>\n");
                    tableAlignments = null; // Zurücksetzen
                }
                
                // Separator-Zeile: enthält nur | und Zellen aus Strichen/Doppelpunkten/Leerzeichen (z. B. |----|----|)
                String sepNormalized = trimmedLine.replaceAll("^\\|", "").replaceAll("\\|$", "");
                String[] sepCells = sepNormalized.split("\\|", -1);
                boolean isSeparator = sepCells.length >= 1 && sepCells.length <= 1000;
                if (isSeparator) {
                    for (String sc : sepCells) {
                        String c = sc.trim();
                        if (!c.isEmpty() && !c.matches("^[-:\\s]+$")) {
                            isSeparator = false;
                            break;
                        }
                    }
                    if (isSeparator && sepCells.length > 0) {
                        boolean hasDashes = false;
                        for (String sc : sepCells) {
                            if (sc.contains("-")) { hasDashes = true; break; }
                        }
                        if (!hasDashes) isSeparator = false;
                    }
                }
                if (isSeparator) {
                    // Ausrichtung aus Separator-Zeile extrahieren
                    tableAlignments = new String[sepCells.length];
                    
                    for (int j = 0; j < sepCells.length; j++) {
                        String sep = sepCells[j].trim();
                        // Wichtig: Reihenfolge der Prüfungen ist kritisch!
                        // Zuerst center prüfen (beide Seiten haben :), dann right, dann left
                        if (sep.startsWith(":") && sep.endsWith(":")) {
                            // :---: = zentriert
                            tableAlignments[j] = "center";
                        } else if (sep.endsWith(":") && !sep.startsWith(":")) {
                            // ---: = rechtsbündig (nur rechts hat :)
                            tableAlignments[j] = "right";
                        } else if (sep.startsWith(":") && !sep.endsWith(":")) {
                            // :--- = linksbündig (nur links hat :)
                            tableAlignments[j] = "left";
                        } else {
                            // --- = Standard: linksbündig
                            tableAlignments[j] = "left";
                        }
                    }
                    continue;
                }
                
                // Header-Zeile: nächste Zeile ist eine Separator-Zeile (nur Striche/Doppelpunkte)
                boolean isHeaderRow = false;
                if (i + 1 < lines.length) {
                    String nextTrimmed = lines[i + 1].trim();
                    String nextNorm = nextTrimmed.replaceAll("^\\|", "").replaceAll("\\|$", "");
                    String[] nextCells = nextNorm.split("\\|", -1);
                    boolean nextIsSep = nextCells.length >= 1;
                    if (nextIsSep) {
                        for (String nc : nextCells) {
                            String c = nc.trim();
                            if (!c.isEmpty() && !c.matches("^[-:\\s]+$")) {
                                nextIsSep = false;
                                break;
                            }
                        }
                        if (nextIsSep) {
                            boolean hasDash = false;
                            for (String nc : nextCells) {
                                if (nc.contains("-")) { hasDash = true; break; }
                            }
                            if (!hasDash) nextIsSep = false;
                        }
                    }
                    isHeaderRow = nextIsSep;
                }
                
                String normalized = trimmedLine.replaceAll("^\\|", "").replaceAll("\\|$", "");
                String[] cells = normalized.split("\\|");
                
                tableContent.append("<tr>\n");
                for (int j = 0; j < cells.length; j++) {
                    String rawCell = cells[j];
                    String cell = rawCell.trim();
                    String alignment = (tableAlignments != null && j < tableAlignments.length) 
                        ? tableAlignments[j] 
                        : "left";
                    String style = " style=\"text-align: " + alignment + ";\"";
                    
                    if (isHeaderRow) {
                        tableContent.append("<th").append(style).append(">")
                            .append(convertInlineMarkdownForPreview(cell)).append("</th>\n");
                    } else {
                        tableContent.append("<td").append(style).append(">")
                            .append(convertInlineMarkdownForPreview(cell)).append("</td>\n");
                    }
                }
                tableContent.append("</tr>\n");
                continue;
            } else if (inTable) {
                inTable = false;
                tableContent.append("</table>\n");
                html.append(tableContent.toString());
                tableAlignments = null; // Zurücksetzen
            }
            
            // WICHTIG: Prüfe auf bereits konvertierte zentrierte Absätze (wurde VOR der Schleife konvertiert)
            // Die <c> Tags wurden bereits zu <p style="text-align: center;"> konvertiert
            // WICHTIG: Blockquotes mit zentriertem Inhalt müssen ZUERST behandelt werden
            if (trimmedLine.startsWith(">") && line.contains("<p style=\"text-align: center;\">")) {
                // Blockquote mit zentriertem Inhalt
                if (inParagraph) {
                    html.append("</p>\n");
                    inParagraph = false;
                }
                if (!inBlockquote) {
                    html.append("<blockquote>\n");
                    inBlockquote = true;
                }
                // Extrahiere den Inhalt (ohne das führende >)
                Pattern centerPPattern = Pattern.compile("<p style=\"text-align: center;\">(.*?)</p>");
                Matcher centerPMatcher = centerPPattern.matcher(line);
                if (centerPMatcher.find()) {
                    String centerContent = centerPMatcher.group(1);
                    html.append("<p style=\"text-align: center;\">").append(convertInlineMarkdownForPreview(centerContent)).append("</p>\n");
                    continue;
                }
            } else if (line.contains("<p style=\"text-align: center;\">")) {
                // Normale zentrierte Absätze (ohne Blockquote)
                if (inParagraph) {
                    html.append("</p>\n");
                    inParagraph = false;
                }
                // Extrahiere den Inhalt und konvertiere Inline-Markdown
                Pattern centerPPattern = Pattern.compile("<p style=\"text-align: center;\">(.*?)</p>");
                Matcher centerPMatcher = centerPPattern.matcher(line);
                if (centerPMatcher.find()) {
                    String centerContent = centerPMatcher.group(1);
                    html.append("<p style=\"text-align: center;\">").append(convertInlineMarkdownForPreview(centerContent)).append("</p>\n");
                    continue;
                }
            }
            
            // Leerzeilen: Absatz beenden, aber nicht mehrfache <br> einfügen
            if (trimmedLine.isEmpty()) {
                if (inParagraph) {
                    html.append("</p>\n");
                    inParagraph = false;
                }
                if (inBlockquote) {
                    html.append("</blockquote>\n");
                    inBlockquote = false;
                }
                // Schließe alle offenen Listen bei Leerzeile
                while (!listStack.isEmpty()) {
                    int listType = listStack.remove(listStack.size() - 1);
                    indentStack.remove(indentStack.size() - 1);
                    if (listType == 1) {
                        html.append("</ul>\n");
                    } else if (listType == 2) {
                        html.append("</ol>\n");
                    }
                }
                // Kein <br> mehr - Absätze werden durch </p> getrennt
                continue;
            }
            
            // Code-Blöcke
            if (trimmedLine.startsWith("```")) {
                if (inParagraph) {
                    html.append("</p>\n");
                    inParagraph = false;
                }
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
                if (inParagraph) {
                    html.append("</p>\n");
                    inParagraph = false;
                }
                if (!inBlockquote) {
                    html.append("<blockquote>\n");
                    inBlockquote = true;
                }
                String quoteText = trimmedLine.substring(1).trim();
                html.append("<p>").append(convertInlineMarkdownForPreview(quoteText)).append("</p>\n");
                continue;
            } else if (inBlockquote) {
                html.append("</blockquote>\n");
                inBlockquote = false;
            }
            
            // Listen mit verschachtelter Unterstützung
            // Pattern für eingerückte Listen: optional Leerzeichen/Tabs am Anfang
            boolean isUnorderedListItem = line.matches("^\\s*[-*+]\\s+.*");
            boolean isOrderedListItem = line.matches("^\\s*\\d+\\.\\s+.*");
            
            if (isUnorderedListItem || isOrderedListItem) {
                // Schließe vorheriges <li>, falls offen
                if (previousWasListItem) {
                    html.append("</li>\n");
                    previousWasListItem = false;
                }
                if (inParagraph) {
                    html.append("</p>\n");
                    inParagraph = false;
                }
                
                // Berechne Einrückungsebene (Anzahl der Leerzeichen/Tabs am Anfang)
                int indentLevel = 0;
                for (int j = 0; j < line.length(); j++) {
                    char c = line.charAt(j);
                    if (c == ' ') {
                        indentLevel++;
                    } else if (c == '\t') {
                        indentLevel += 4; // Tab = 4 Leerzeichen
                    } else {
                        break;
                    }
                }
                
                // Bestimme den Listen-Typ
                int newListType = isUnorderedListItem ? 1 : 2;
                
                // Schließe Listen auf tieferen Ebenen, wenn wir zurückgehen
                while (!indentStack.isEmpty() && indentLevel < indentStack.get(indentStack.size() - 1)) {
                    int listType = listStack.remove(listStack.size() - 1);
                    indentStack.remove(indentStack.size() - 1);
                    if (listType == 1) {
                        html.append("</ul>\n");
                    } else if (listType == 2) {
                        html.append("</ol>\n");
                    }
                }
                
                // Öffne neue Listen-Ebene falls nötig
                if (indentStack.isEmpty() || indentLevel > indentStack.get(indentStack.size() - 1)) {
                    // Neue Ebene öffnen (tiefer eingerückt oder erste Liste)
                    if (newListType == 1) {
                        html.append("<ul>\n");
                    } else {
                        html.append("<ol>\n");
                    }
                    listStack.add(newListType);
                    indentStack.add(indentLevel);
                } else if (!listStack.isEmpty() && indentLevel == indentStack.get(indentStack.size() - 1)) {
                    // Gleiche Ebene - prüfe ob Listen-Typ sich ändert
                    if (listStack.get(listStack.size() - 1) != newListType) {
                        // Listen-Typ ändert sich auf gleicher Ebene - alte Liste schließen, neue öffnen
                        int oldListType = listStack.remove(listStack.size() - 1);
                        indentStack.remove(indentStack.size() - 1);
                        if (oldListType == 1) {
                            html.append("</ul>\n");
                        } else if (oldListType == 2) {
                            html.append("</ol>\n");
                        }
                        if (newListType == 1) {
                            html.append("<ul>\n");
                        } else {
                            html.append("<ol>\n");
                        }
                        listStack.add(newListType);
                        indentStack.add(indentLevel);
                    }
                    // Wenn Listen-Typ gleich ist, bleibt die Liste offen - nichts zu tun!
                }
                
                // List-Item hinzufügen
                String listItem;
                if (isUnorderedListItem) {
                    listItem = trimmedLine.replaceFirst("^[-*+]\\s+", "");
                } else {
                    listItem = trimmedLine.replaceFirst("^\\d+\\.\\s+", "");
                }
                // Öffne <li> aber schließe es nicht sofort - könnte eingerückten Text geben
                html.append("<li>").append(convertInlineMarkdownForPreview(listItem));
                previousWasListItem = true;
                continue;
            } else {
                // Prüfe zuerst auf Leerzeile - Liste bleibt offen
                if (trimmedLine.isEmpty() && !listStack.isEmpty()) {
                    previousWasListItem = false;
                    continue; // Liste bleibt offen, keine Aktion nötig
                }
                
                // Prüfe, ob eingerückter Text innerhalb einer Liste ist
                // Laut Markdown-Spezifikation: Text mit mindestens 4 Leerzeichen Einrückung
                // nach einem Listenelement ist Teil dieses Listenelements
                if (!trimmedLine.isEmpty() && !listStack.isEmpty()) {
                    int indentLevel = 0;
                    for (int j = 0; j < line.length(); j++) {
                        char c = line.charAt(j);
                        if (c == ' ') {
                            indentLevel++;
                        } else if (c == '\t') {
                            indentLevel += 4; // Tab = 4 Leerzeichen
                        } else {
                            break;
                        }
                    }
                    
                    // Wenn der Text mindestens 4 Leerzeichen eingerückt ist,
                    // ist es Teil des Listenelements (Fortsetzung)
                    if (indentLevel >= 4) {
                        // Text als Absatz innerhalb des letzten <li> Elements hinzufügen
                        // Das <li> wurde noch nicht geschlossen, also können wir den Text direkt anhängen
                        html.append("<p style=\"margin: 0.5em 0 0 0;\">").append(convertInlineMarkdownForPreview(line.trim())).append("</p>");
                        previousWasListItem = true; // Immer noch im Listenelement
                        continue; // WICHTIG: continue, damit die Liste nicht geschlossen wird!
                    }
                }
                
                // Wenn wir hier ankommen und previousWasListItem true ist, schließe das <li>
                if (previousWasListItem) {
                    html.append("</li>\n");
                    previousWasListItem = false;
                }
                
                // Kein List-Item, keine Leerzeile, nicht eingerückt - schließe alle offenen Listen
                // Schließe zuerst offenes <li>, falls vorhanden
                if (previousWasListItem) {
                    html.append("</li>\n");
                    previousWasListItem = false;
                }
                while (!listStack.isEmpty()) {
                    int listType = listStack.remove(listStack.size() - 1);
                    indentStack.remove(indentStack.size() - 1);
                    if (listType == 1) {
                        html.append("</ul>\n");
                    } else if (listType == 2) {
                        html.append("</ol>\n");
                    }
                }
            }
            
            // Überschriften
            if (trimmedLine.startsWith("# ")) {
                if (inParagraph) {
                    html.append("</p>\n");
                    inParagraph = false;
                }
                html.append("<h1>").append(convertInlineMarkdownForPreview(trimmedLine.substring(2))).append("</h1>\n");
            } else if (trimmedLine.startsWith("## ")) {
                if (inParagraph) {
                    html.append("</p>\n");
                    inParagraph = false;
                }
                html.append("<h2>").append(convertInlineMarkdownForPreview(trimmedLine.substring(3))).append("</h2>\n");
            } else if (trimmedLine.startsWith("### ")) {
                if (inParagraph) {
                    html.append("</p>\n");
                    inParagraph = false;
                }
                html.append("<h3>").append(convertInlineMarkdownForPreview(trimmedLine.substring(4))).append("</h3>\n");
            } else if (trimmedLine.startsWith("#### ")) {
                if (inParagraph) {
                    html.append("</p>\n");
                    inParagraph = false;
                }
                html.append("<h4>").append(convertInlineMarkdownForPreview(trimmedLine.substring(5))).append("</h4>\n");
            } else {
                // Normaler Text - Absatz zusammenfassen
                if (!inParagraph) {
                    html.append("<p>");
                    inParagraph = true;
                } else {
                    html.append(" ");
                }
                html.append(convertInlineMarkdownForPreview(line));
            }
        }
        
        // Schließe offene Tags
        if (inParagraph) html.append("</p>\n");
        if (inBlockquote) html.append("</blockquote>\n");
        
        // Schließe alle offenen Listen
        // Schließe zuerst offenes <li>, falls vorhanden
        if (previousWasListItem) {
            html.append("</li>\n");
        }
        while (!listStack.isEmpty()) {
            int listType = listStack.remove(listStack.size() - 1);
            if (listType == 1) {
                html.append("</ul>\n");
            } else if (listType == 2) {
                html.append("</ol>\n");
            }
        }
        
        if (inTable) {
            tableContent.append("</table>\n");
            html.append(tableContent.toString());
        }
        
        html.append("</body>\n");
        html.append("</html>");
        
        return html.toString();
    }
    
    /**
     * Konvertiert Inline-Markdown zu HTML mit Bild-Pfad-Konvertierung
     */
    private String convertInlineMarkdownForPreview(String text) {
        // Zuerst Bilder konvertieren (vor anderen Konvertierungen)
        String result = text;
        
        // Pattern für Bilder: ![Alt-Text](Pfad)
        Pattern imagePattern = Pattern.compile("!\\[([^\\]]*)\\]\\(([^)]+)\\)");
        Matcher imageMatcher = imagePattern.matcher(result);
        StringBuffer imageBuffer = new StringBuffer();
        
        while (imageMatcher.find()) {
            String altText = imageMatcher.group(1);
            String imagePath = imageMatcher.group(2);
            
            // Konvertiere relativen Pfad zu absolutem Pfad oder Base64
            String imageSrc = convertImagePathForQuill(imagePath);
            
            String replacement = "<img src=\"" + escapeHtml(imageSrc) + "\" alt=\"" + escapeHtml(altText) + "\" style=\"max-width: 100%; height: auto;\">";
            imageMatcher.appendReplacement(imageBuffer, Matcher.quoteReplacement(replacement));
        }
        imageMatcher.appendTail(imageBuffer);
        result = imageBuffer.toString();
        
        // Dann andere Inline-Markdown-Elemente konvertieren
        result = result
            // Fett (zwei Sternchen)
            .replaceAll("\\*\\*(.*?)\\*\\*", "<strong>$1</strong>")
            // Kursiv (ein Sternchen) - aber nicht wenn es bereits fett ist
            .replaceAll("(?<!\\*)\\*(?!\\*)(.*?)(?<!\\*)\\*(?!\\*)", "<em>$1</em>")
            // Inline-Code (Backticks)
            .replaceAll("`(.*?)`", "<code>$1</code>")
            // Links
            .replaceAll("\\[([^\\]]+)\\]\\(([^)]+)\\)", "<a href=\"$2\">$1</a>")
            // Superscript und Subscript (müssen vor anderen Formatierungen behandelt werden)
            .replaceAll("<sup>(.*?)</sup>", "<sup>$1</sup>") // Behalte <sup> Tags
            .replaceAll("<sub>(.*?)</sub>", "<sub>$1</sub>") // Behalte <sub> Tags
            // Durchgestrichen (zwei Tilden) - Quill-kompatibel: <s> Tag
            .replaceAll("~~(.*?)~~", "<s>$1</s>")
            // Hervorgehoben (zwei Gleichheitszeichen) - Quill-kompatibel: <mark> Tag
            .replaceAll("==(.*?)==", "<mark>$1</mark>")
            // Superscript (^text^) - einzelne ^ Zeichen
            .replaceAll("(?<!\\^)\\^([^\\^\\n]+)\\^(?!\\^)", "<sup>$1</sup>")
            // Subscript (~text~) - einzelne ~ Zeichen, nicht ~~ (Strikethrough)
            .replaceAll("(?<!~)~([^~\\n]+)~(?!~)", "<sub>$1</sub>");
        
        // Farb-Tags zu HTML-Spans konvertieren (für Preview)
        // Englische Tags: <red>Text</red> -> <span style="color: red;">Text</span>
        result = result.replaceAll("(?i)<red>([^<]+)</red>", "<span style=\"color: red;\">$1</span>");
        result = result.replaceAll("(?i)<blue>([^<]+)</blue>", "<span style=\"color: blue;\">$1</span>");
        result = result.replaceAll("(?i)<green>([^<]+)</green>", "<span style=\"color: green;\">$1</span>");
        result = result.replaceAll("(?i)<yellow>([^<]+)</yellow>", "<span style=\"color: yellow;\">$1</span>");
        result = result.replaceAll("(?i)<purple>([^<]+)</purple>", "<span style=\"color: purple;\">$1</span>");
        result = result.replaceAll("(?i)<orange>([^<]+)</orange>", "<span style=\"color: orange;\">$1</span>");
        result = result.replaceAll("(?i)<gray>([^<]+)</gray>", "<span style=\"color: gray;\">$1</span>");
        result = result.replaceAll("(?i)<grey>([^<]+)</grey>", "<span style=\"color: gray;\">$1</span>");
        
        // Deutsche Tags: <rot>Text</rot> -> <span style="color: red;">Text</span>
        result = result.replaceAll("(?i)<rot>([^<]+)</rot>", "<span style=\"color: red;\">$1</span>");
        result = result.replaceAll("(?i)<blau>([^<]+)</blau>", "<span style=\"color: blue;\">$1</span>");
        result = result.replaceAll("(?i)<grün>([^<]+)</grün>", "<span style=\"color: green;\">$1</span>");
        result = result.replaceAll("(?i)<gelb>([^<]+)</gelb>", "<span style=\"color: yellow;\">$1</span>");
        result = result.replaceAll("(?i)<lila>([^<]+)</lila>", "<span style=\"color: purple;\">$1</span>");
        result = result.replaceAll("(?i)<grau>([^<]+)</grau>", "<span style=\"color: gray;\">$1</span>");
        
        return result;
    }
    
    /**
     * Toggle für Blocksatz im Preview
     */
    private void togglePreviewJustify() {
        previewJustifyEnabled = !previewJustifyEnabled;
        
        // Speichere Blocksatz-Status in Preferences
        if (preferences != null) {
            preferences.putBoolean("preview_justify_enabled", previewJustifyEnabled);
        }
        
        updateJustifyButtonStyle();
        // Quill-Theme mit neuer Ausrichtung aktualisieren
        // WICHTIG: Nur Theme anwenden, Fontgröße NICHT ändern
        applyThemeToQuill(currentThemeIndex, false); // false = Fontgröße nicht überschreiben
        updatePreviewContent();
    }
    
    /**
     * Aktualisiert den Stil des Blocksatz-Buttons
     */
    private void updateJustifyButtonStyle() {
        if (btnToggleJustify == null) {
            return;
        }
        
        if (previewJustifyEnabled) {
            btnToggleJustify.setText("Blocksatz ✓");
            btnToggleJustify.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;");
        } else {
            btnToggleJustify.setText("Blocksatz");
            btnToggleJustify.setStyle("");
        }
    }
    
    /**
     * Schaltet zwischen Quill Editor und normalem WebView um
     */
    
    /**
     * Aktualisiert die Textfarbe der Labels im Preview-Fenster basierend auf dem Theme
     */
    private void updatePreviewWindowLabels() {
        if (lblQuillFontSize == null || lblQuillFontFamily == null) {
            return;
        }
        
        // Textfarbe aus Theme holen
        String textColor = THEMES[currentThemeIndex][1];
        
        // Labels stylen
        lblQuillFontSize.setStyle("-fx-text-fill: " + textColor + "; -fx-font-size: 12px;");
        lblQuillFontFamily.setStyle("-fx-text-fill: " + textColor + "; -fx-font-size: 12px;");
        
        // Spinner und ComboBox Textfarbe
        if (spnQuillFontSize != null) {
            spnQuillFontSize.setStyle("-fx-min-width: 80px; -fx-max-width: 80px; -fx-min-height: 28px; -fx-max-height: 28px; -fx-font-size: 12px; -fx-text-fill: " + textColor + ";");
        }
        if (cmbQuillFontFamily != null) {
            cmbQuillFontFamily.setStyle("-fx-min-width: 120px; -fx-max-width: 120px; -fx-min-height: 28px; -fx-max-height: 28px; -fx-font-size: 12px; -fx-text-fill: " + textColor + ";");
        }
    }
    
    /**
     * Konvertiert einen Bild-Pfad zu einem absoluten file://-Pfad
     */
    private String convertImagePathToAbsolute(String imagePath) {
        if (imagePath == null || imagePath.trim().isEmpty()) {
            return "";
        }
        
        // Normalisiere den Pfad (entferne führende/trailing Leerzeichen)
        imagePath = imagePath.trim();
        
        // Wenn bereits absolute URL oder file://-Pfad
        if (imagePath.startsWith("http://") || imagePath.startsWith("https://") || imagePath.startsWith("file://")) {
            return imagePath;
        }
        
        // Versuche relativen Pfad zu absoluten Pfad zu konvertieren
        File imageFile = null;
        
        // 1. Versuch: Relativ zur aktuellen Datei
        if (currentFile != null && currentFile.exists()) {
            File parentDir = currentFile.getParentFile();
            if (parentDir != null) {
                imageFile = new File(parentDir, imagePath);
                if (imageFile.exists()) {
                    try {
                        return imageFile.toURI().toURL().toString();
                    } catch (Exception e) {
                        logger.debug("Fehler beim Konvertieren des Bild-Pfads: " + e.getMessage());
                    }
                }
            }
        }
        
        // 2. Versuch: Relativ zur Original-DOCX-Datei (falls vorhanden)
        if (originalDocxFile != null && originalDocxFile.exists()) {
            File parentDir = originalDocxFile.getParentFile();
            if (parentDir != null) {
                imageFile = new File(parentDir, imagePath);
                if (imageFile.exists()) {
                    try {
                        return imageFile.toURI().toURL().toString();
                    } catch (Exception e) {
                        logger.debug("Fehler beim Konvertieren des Bild-Pfads: " + e.getMessage());
                    }
                }
            }
        }
        
        // 3. Versuch: Als absoluter Pfad
        imageFile = new File(imagePath);
        if (imageFile.exists() && imageFile.isAbsolute()) {
            try {
                return imageFile.toURI().toURL().toString();
            } catch (Exception e) {
                logger.debug("Fehler beim Konvertieren des Bild-Pfads: " + e.getMessage());
            }
        }
        
        // 4. Versuch: Relativ zum Arbeitsverzeichnis
        imageFile = new File(System.getProperty("user.dir"), imagePath);
        if (imageFile.exists()) {
            try {
                return imageFile.toURI().toURL().toString();
            } catch (Exception e) {
                logger.debug("Fehler beim Konvertieren des Bild-Pfads: " + e.getMessage());
            }
        }
        
        // Fallback: Original-Pfad zurückgeben (wird in WebView nicht funktionieren, aber besser als nichts)
        logger.warn("Bild nicht gefunden: " + imagePath);
        return imagePath;
    }
    
    /**
     * Konvertiert Bild-Pfad für Quill Editor (als Base64 oder file:// URL)
     */
    private String convertImagePathForQuill(String imagePath) {
        if (imagePath == null || imagePath.trim().isEmpty()) {
            return "";
        }
        
        // Normalisiere den Pfad
        imagePath = imagePath.trim();
        
        // Wenn bereits HTTP/HTTPS URL, direkt zurückgeben
        if (imagePath.startsWith("http://") || imagePath.startsWith("https://")) {
            return imagePath;
        }
        
        // Wenn bereits file:// URL, direkt zurückgeben
        if (imagePath.startsWith("file://")) {
            return imagePath;
        }
        
        // Wenn bereits data: URL (Base64), direkt zurückgeben
        // WICHTIG: Base64-Bilder müssen erhalten bleiben, auch bei Kapitel-Wechsel
        if (imagePath.startsWith("data:")) {
            return imagePath;
        }
        
        // Versuche Bild zu finden und als Base64 zu konvertieren
        File imageFile = null;
        
        // 1. Versuch: Relativ zur aktuellen Datei
        if (currentFile != null && currentFile.exists()) {
            File parentDir = currentFile.getParentFile();
            if (parentDir != null) {
                imageFile = new File(parentDir, imagePath);
                if (imageFile.exists() && imageFile.isFile()) {
                    String base64 = convertImageToBase64(imageFile);
                    if (base64 != null && !base64.isEmpty()) {
                        return base64;
                    }
                }
            }
        }
        
        // 2. Versuch: Relativ zur Original-DOCX-Datei (wichtig bei Kapitel-Wechsel!)
        if (originalDocxFile != null && originalDocxFile.exists()) {
            File parentDir = originalDocxFile.getParentFile();
            if (parentDir != null) {
                imageFile = new File(parentDir, imagePath);
                if (imageFile.exists() && imageFile.isFile()) {
                    String base64 = convertImageToBase64(imageFile);
                    if (base64 != null && !base64.isEmpty()) {
                        return base64;
                    }
                }
            }
        }
        
        // 3. Versuch: Als absoluter Pfad
        imageFile = new File(imagePath);
        if (imageFile.exists() && imageFile.isFile() && imageFile.isAbsolute()) {
            String base64 = convertImageToBase64(imageFile);
            if (base64 != null && !base64.isEmpty()) {
                return base64;
            }
        }
        
        // 4. Versuch: Relativ zum Arbeitsverzeichnis
        imageFile = new File(System.getProperty("user.dir"), imagePath);
        if (imageFile.exists() && imageFile.isFile()) {
            String base64 = convertImageToBase64(imageFile);
            if (base64 != null && !base64.isEmpty()) {
                return base64;
            }
        }
        
        // 5. Versuch: Relativ zum data-Verzeichnis (falls vorhanden)
        if (originalDocxFile != null && originalDocxFile.exists()) {
            File parentDir = originalDocxFile.getParentFile();
            if (parentDir != null) {
                File dataDir = new File(parentDir, "data");
                if (dataDir.exists() && dataDir.isDirectory()) {
                    imageFile = new File(dataDir, imagePath);
                    if (imageFile.exists() && imageFile.isFile()) {
                        String base64 = convertImageToBase64(imageFile);
                        if (base64 != null && !base64.isEmpty()) {
                            return base64;
                        }
                    }
                }
            }
        }
        
        // Fallback: Versuche als file:// URL
        try {
            String absolutePath = convertImagePathToAbsolute(imagePath);
            return absolutePath;
        } catch (Exception e) {
            logger.warn("Konnte Bild-Pfad nicht konvertieren: " + imagePath);
            return imagePath;
        }
    }
    
    /**
     * Konvertiert Bild-Datei zu Base64 data URL
     */
    private String convertImageToBase64(File imageFile) {
        if (imageFile == null || !imageFile.exists() || !imageFile.isFile()) {
            logger.warn("Bild-Datei existiert nicht oder ist ungültig: " + (imageFile != null ? imageFile.getAbsolutePath() : "null"));
            return null;
        }
        
        try {
            byte[] imageBytes = Files.readAllBytes(imageFile.toPath());
            if (imageBytes.length == 0) {
                logger.warn("Bild-Datei ist leer: " + imageFile.getAbsolutePath());
                return null;
            }
            
            String base64 = java.util.Base64.getEncoder().encodeToString(imageBytes);
            
            // Bestimme MIME-Type basierend auf Dateiendung
            String mimeType = "image/png"; // Default
            String fileName = imageFile.getName().toLowerCase();
            if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
                mimeType = "image/jpeg";
            } else if (fileName.endsWith(".gif")) {
                mimeType = "image/gif";
            } else if (fileName.endsWith(".webp")) {
                mimeType = "image/webp";
            } else if (fileName.endsWith(".bmp")) {
                mimeType = "image/bmp";
            } else if (fileName.endsWith(".png")) {
                mimeType = "image/png";
            }
            
            return "data:" + mimeType + ";base64," + base64;
        } catch (Exception e) {
            logger.warn("Fehler beim Konvertieren des Bildes zu Base64: " + e.getMessage());
            // Fallback: file:// URL
            try {
                return imageFile.toURI().toURL().toString();
            } catch (Exception e2) {
                logger.warn("Auch file:// URL Konvertierung fehlgeschlagen: " + e2.getMessage());
                return "";
            }
        }
    }
    
    /**
     * Richtet die Scroll-Synchronisation zwischen Editor und Preview ein
     */
    private void setupPreviewScrollSync() {
        if (codeArea == null || previewWebView == null) {
            return;
        }
        
        // Tastatur-Synchronisation (Pfeiltasten) ist deaktiviert, damit der Cursor im Editor
        // nicht springt. Maus-Scrollen im Editor synchronisiert jedoch weiterhin mit Quill.
        
        // Mouse-Scroll-Rad: Erkenne Scrollen und synchronisiere Preview
        // Debounce-Timer für Scroll-Synchronisation
        final java.util.concurrent.atomic.AtomicReference<java.util.Timer> scrollSyncTimer = new java.util.concurrent.atomic.AtomicReference<>();
        
        // Event-Handler für Scroll-Synchronisation (nur Maus-Scrollen)
        EventHandler<ScrollEvent> scrollSyncHandler = event -> {
            if (!isScrollingPreview && previewWebView != null && previewStage != null && previewStage.isShowing()) {
                // Quill noch nicht bereit? Dann nichts tun, um Sprünge zu vermeiden
                try {
                    Object ready = previewWebView.getEngine().executeScript(
                        "(function(){ return !!(window.quillReady && document.querySelector('.ql-editor')); })()"
                    );
                    if (!(ready instanceof Boolean) || !((Boolean) ready)) {
                        return;
                    }
                } catch (Exception ignore) {
                    return;
                }
                
                // Alten Timer abbrechen falls vorhanden
                Timer oldTimer = scrollSyncTimer.getAndSet(null);
                if (oldTimer != null) {
                    oldTimer.cancel();
                }
                
                // Neuen Timer starten (Debounce: 30ms nach letztem Scroll-Event)
                Timer newTimer = new Timer(true); // Daemon-Thread
                scrollSyncTimer.set(newTimer);
                
                // Scroll-Delta extrahieren (negativ = nach oben, positiv = nach unten)
                final double deltaY = event.getDeltaY();
                
                newTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        Platform.runLater(() -> {
                            try {
                                // Prüfe, ob WebView geladen ist
                                if (previewWebView.getEngine().getLoadWorker().getState() == javafx.concurrent.Worker.State.SUCCEEDED) {
                                    // Wende Scroll-Delta direkt in JS auf die Quill-Editor-Scrollposition an
                                    String script = String.format(
                                        "(function() {\n" +
                                        "  var editor = document.querySelector('.ql-editor');\n" +
                                        "  var container = document.querySelector('.ql-container');\n" +
                                        "  var scroller = container || editor;\n" +
                                        "  if (!scroller) return;\n" +
                                        "  var max = Math.max(0, scroller.scrollHeight - scroller.clientHeight);\n" +
                                        "  if (max <= 0) return;\n" +
                                        "  var next = scroller.scrollTop - (%f);\n" +
                                        "  if (next < 0) next = 0;\n" +
                                        "  if (next > max) next = max;\n" +
                                        "  scroller.scrollTop = next;\n" +
                                        "})();",
                                        deltaY
                                    );
                                    previewWebView.getEngine().executeScript(script);
                                    
                                    logger.debug("Quill Preview gescrollt (Maus-Delta): {}", deltaY);
                                }
                            } catch (Exception e) {
                                logger.debug("Fehler beim Scrollen der Quill Preview: " + e.getMessage());
                            }
                        });
                    }
                }, 30); // 30ms Verzögerung für Debounce
            }
            // Event NICHT consumen - normales Scrollen soll weiterhin funktionieren
        };
        
        // Auf VirtualizedScrollPane registrieren (falls vorhanden)
        if (scrollPane != null) {
            try {
                scrollPane.addEventFilter(ScrollEvent.SCROLL, scrollSyncHandler);
                logger.debug("Scroll-Event-Filter auf VirtualizedScrollPane registriert (nur Maus-Scrollen)");
            } catch (Exception e) {
                logger.debug("Fehler beim Registrieren des Scroll-Event-Filters auf VirtualizedScrollPane: " + e.getMessage());
            }
        }
        
        // Auch auf CodeArea registrieren (für den Fall, dass das Event dort ankommt)
        if (codeArea != null) {
            try {
                codeArea.addEventFilter(ScrollEvent.SCROLL, scrollSyncHandler);
                logger.debug("Scroll-Event-Filter auf CodeArea registriert (nur Maus-Scrollen)");
            } catch (Exception e) {
                logger.debug("Fehler beim Registrieren des Scroll-Event-Filters auf CodeArea: " + e.getMessage());
            }
        }
    }
    
    /**
     * Scrollt im Preview zur aktuellen Editor-Zeile (mittig)
     */
    private void scrollToLineInPreview() {
        if (previewWebView == null || codeArea == null) {
            return;
        }
        
        try {
            // Aktuelle Zeile extrahieren
            int currentParagraph = codeArea.getCurrentParagraph();
            if (currentParagraph < 0 || currentParagraph >= codeArea.getParagraphs().size()) {
                return;
            }
            
            // Text aus dem Paragraph extrahieren
            // Berechne den Offset für den Anfang des Paragraphs
            int paragraphStart = 0;
            for (int i = 0; i < currentParagraph; i++) {
                paragraphStart += codeArea.getParagraphLength(i) + 1; // +1 für Zeilenumbruch
            }
            int paragraphEnd = paragraphStart + codeArea.getParagraphLength(currentParagraph);
            String currentLine = codeArea.getText(paragraphStart, paragraphEnd).trim();
            if (currentLine.isEmpty()) {
                return;
            }
            
            // Entferne Markdown-Syntax für die Suche (nur ersten Teil der Zeile verwenden)
            String searchText = currentLine;
            // Entferne Markdown-Formatierung für bessere Suche
            searchText = searchText.replaceAll("^#+\\s+", "")  // Überschriften
                                  .replaceAll("^[-*+]\\s+", "")  // Listen
                                  .replaceAll("^\\d+\\.\\s+", "")  // Nummerierte Listen
                                  .replaceAll("^>\\s+", "")  // Blockquotes
                                  .replaceAll("```.*```", "")  // Code-Blöcke (vereinfacht)
                                  .trim();
            
            // Verwende nur die ersten 100 Zeichen für die Suche
            if (searchText.length() > 100) {
                searchText = searchText.substring(0, 100);
            }
            
            if (searchText.isEmpty()) {
                return;
            }
            
            final String finalSearchText = searchText;
            
            // Suche im WebView und scroll mittig dorthin
            Platform.runLater(() -> {
                try {
                    // Warte bis WebView geladen ist
                    if (previewWebView.getEngine().getLoadWorker().getState() == javafx.concurrent.Worker.State.SUCCEEDED) {
                        scrollToTextInPreview(finalSearchText);
                    } else {
                        previewWebView.getEngine().getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                            if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                                scrollToTextInPreview(finalSearchText);
                            }
                        });
                    }
                } catch (Exception e) {
                    logger.debug("Fehler beim Scrollen zur Zeile im Preview: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            logger.debug("Fehler beim Extrahieren der Zeile: " + e.getMessage());
        }
    }
    
    /**
     * Sucht Text im Preview und scrollt mittig dorthin
     */
    private void scrollToTextInPreview(String searchText) {
        if (previewWebView == null || searchText == null || searchText.isEmpty()) {
            return;
        }
        
        try {
            // JavaScript: Suche Text im HTML und scroll mittig dorthin
            // Escapen von Sonderzeichen für JavaScript
            String escapedText = searchText.replace("\\", "\\\\")
                                          .replace("\"", "\\\"")
                                          .replace("\n", "\\n")
                                          .replace("\r", "\\r")
                                          .replace("'", "\\'");
            
            String script = String.format(
                "(function() {" +
                "  var searchText = %s;" +
                "  var walker = document.createTreeWalker(" +
                "    document.body," +
                "    NodeFilter.SHOW_TEXT," +
                "    null," +
                "    false" +
                "  );" +
                "  var node;" +
                "  var foundElement = null;" +
                "  while (node = walker.nextNode()) {" +
                "    if (node.textContent && node.textContent.trim().indexOf(searchText) !== -1) {" +
                "      foundElement = node.parentElement;" +
                "      break;" +
                "    }" +
                "  }" +
                "  if (foundElement) {" +
                "    var rect = foundElement.getBoundingClientRect();" +
                "    var scrollTop = window.pageYOffset || document.documentElement.scrollTop;" +
                "    var elementTop = rect.top + scrollTop;" +
                "    var windowHeight = window.innerHeight;" +
                "    var elementHeight = rect.height;" +
                "    var scrollTo = elementTop - (windowHeight / 2) + (elementHeight / 2);" +
                "    window.scrollTo({ top: scrollTo, behavior: 'smooth' });" +
                "    return true;" +
                "  }" +
                "  return false;" +
                "})();",
                "\"" + escapedText + "\""
            );
            
            Object result = previewWebView.getEngine().executeScript(script);
            
            // Falls nicht gefunden, versuche mit ersten 50 Zeichen
            if (result == null || !Boolean.TRUE.equals(result)) {
                if (searchText.length() > 50) {
                    scrollToTextInPreview(searchText.substring(0, 50));
                }
            }
        } catch (Exception e) {
            logger.debug("Fehler beim Suchen und Scrollen im Preview: " + e.getMessage());
        }
    }
    
    /**
     * Synchronisiert die Scroll-Position im Preview mit dem Editor
     */
    private void syncPreviewScroll(int paragraphIndex) {
        if (previewWebView == null || codeArea == null) {
            logger.debug("syncPreviewScroll: previewWebView oder codeArea ist null");
            return;
        }
        
        try {
            // Berechne Scroll-Position basierend auf Paragraph-Index
            int totalParagraphs = codeArea.getParagraphs().size();
            if (totalParagraphs == 0) {
                logger.debug("syncPreviewScroll: Keine Paragraphs vorhanden");
                return;
            }
            
            // Prüfe, ob die aktuelle Zeile leer ist - wenn ja, nichts tun
            if (paragraphIndex >= 0 && paragraphIndex < totalParagraphs) {
                int paragraphStart = 0;
                for (int i = 0; i < paragraphIndex; i++) {
                    paragraphStart += codeArea.getParagraphLength(i) + 1; // +1 für Zeilenumbruch
                }
                int paragraphEnd = paragraphStart + codeArea.getParagraphLength(paragraphIndex);
                String currentLine = codeArea.getText(paragraphStart, paragraphEnd).trim();
                if (currentLine.isEmpty()) {
                    // Leere Zeile - nichts tun
                    return;
                }
            }
            
            // Berechne Scroll-Position als Prozentsatz
            double scrollPercent = (double) paragraphIndex / totalParagraphs;
            logger.debug("syncPreviewScroll: Paragraph {}/{}, Scroll-Perzent: {}", paragraphIndex, totalParagraphs, scrollPercent);
            
            // Scroll-Funktion, die sowohl vom Listener als auch direkt aufgerufen werden kann
            Runnable scrollAction = () -> {
                try {
                    // Verwende explizite Formatierung ohne führende Nullen (JavaScript-Problem mit Oktalzahlen)
                    // Stelle sicher, dass die Zahl als normale Dezimalzahl formatiert wird
                    // Verwende DecimalFormat oder String.valueOf für sichere Formatierung
                    String scrollPercentStr = String.valueOf(scrollPercent);
                    // Falls die Zahl sehr klein ist und führende Nullen hat, entferne sie
                    // Aber eigentlich sollte String.valueOf() das nicht produzieren
                    // Verwende stattdessen eine explizite Formatierung mit Locale.ROOT
                    java.text.DecimalFormat df = new java.text.DecimalFormat("0.##########", 
                        new java.text.DecimalFormatSymbols(java.util.Locale.ROOT));
                    scrollPercentStr = df.format(scrollPercent);
                    
                    String script = String.format(
                        "var scrollHeight = document.body.scrollHeight - window.innerHeight; " +
                        "var scrollTop = scrollHeight * %s; " +
                        "window.scrollTo(0, scrollTop);",
                        scrollPercentStr
                    );
                    logger.debug("syncPreviewScroll: Führe JavaScript aus: {}", script);
                    Object result = previewWebView.getEngine().executeScript(script);
                    logger.debug("syncPreviewScroll: JavaScript-Ergebnis: {}", result);
                } catch (Exception e) {
                    logger.warn("Fehler beim Scrollen im Preview: " + e.getMessage(), e);
                }
            };
            
            // Prüfe, ob WebView bereits geladen ist
            javafx.concurrent.Worker.State currentState = previewWebView.getEngine().getLoadWorker().getState();
            logger.debug("syncPreviewScroll: WebView State: {}", currentState);
            
            if (currentState == javafx.concurrent.Worker.State.SUCCEEDED) {
                // WebView ist bereits geladen - direkt scrollen
                Platform.runLater(() -> {
                    scrollAction.run();
                });
            } else {
                // WebView ist noch nicht geladen - Listener hinzufügen
                Platform.runLater(() -> {
                    try {
                        // ONE-SHOT Listener, der sich selbst entfernt
                        javafx.beans.value.ChangeListener<javafx.concurrent.Worker.State> oneShotListener = new javafx.beans.value.ChangeListener<javafx.concurrent.Worker.State>() {
                            @Override
                            public void changed(javafx.beans.value.ObservableValue<? extends javafx.concurrent.Worker.State> obs, javafx.concurrent.Worker.State oldState, javafx.concurrent.Worker.State newState) {
                                if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                                    // Listener sofort entfernen, um Ansammlung zu verhindern
                                    previewWebView.getEngine().getLoadWorker().stateProperty().removeListener(this);
                                    scrollAction.run();
                                }
                            }
                        };
                        
                        // WICHTIG: Listener ZUERST hinzufügen, um Race Condition zu vermeiden
                        previewWebView.getEngine().getLoadWorker().stateProperty().addListener(oneShotListener);
                        
                        // DANN prüfen, ob State bereits SUCCEEDED ist (nach dem Hinzufügen des Listeners)
                        // Falls ja, sofort scrollen und Listener entfernen
                        if (previewWebView.getEngine().getLoadWorker().getState() == javafx.concurrent.Worker.State.SUCCEEDED) {
                            // Listener entfernen, da er nie ausgelöst wird
                            previewWebView.getEngine().getLoadWorker().stateProperty().removeListener(oneShotListener);
                            // Scroll-Logik direkt ausführen
                            scrollAction.run();
                        }
                        // Falls State noch nicht SUCCEEDED ist, wartet der Listener auf den State-Wechsel
                    } catch (Exception e) {
                        logger.warn("Fehler beim Scrollen im Preview: " + e.getMessage(), e);
                    }
                });
            }
        } catch (Exception e) {
            logger.warn("Fehler bei der Scroll-Synchronisation: " + e.getMessage(), e);
        }
    }
    
    /**
     * Richtet einen Listener für Text-Änderungen ein (mit Debounce) - für Quill Editor
     */
    private void setupQuillTextListener() {
        // ENTFERNT: Text-Property Listener - Quill wird jetzt nur bei Scroll oder Focus aktualisiert
        // Dies verhindert Feedback-Schleifen und Cursor-Position-Probleme
    }
    
    /**
     * Richtet einen Listener für Text-Änderungen ein (mit Debounce) - Legacy für Preview
     */
    private void setupPreviewTextListener() {
        // Für Kompatibilität: Rufe Quill-Version auf
        setupQuillTextListener();
    }
    
    /**
     * Richtet Scroll-Synchronisation für Quill Editor ein
     */
    private void setupQuillScrollSync() {
        if (codeArea == null || previewWebView == null) {
            return;
        }
        
        // ENTFERNT: currentParagraphProperty Listener - verursacht Cursor-Sprünge beim Klicken
        // Die Scroll-Synchronisation erfolgt nur über:
        // 1. Scroll-Events (Mausrad, Scrollbar)
        // 2. Pfeiltasten-Events (nur bei expliziter Navigation)
        // codeArea.currentParagraphProperty().addListener((obs, oldParagraph, newParagraph) -> {
        //     if (!isScrollingPreview && previewWebView != null && previewStage != null && previewStage.isShowing()) {
        //         syncQuillScroll(newParagraph.intValue());
        //     }
        // });
        
        // ENTFERNT: Klick-Handler, der die Cursorposition stört
        // Die Scroll-Synchronisation erfolgt bereits über:
        // 1. currentParagraphProperty Listener (Zeile 15805)
        // 2. Scroll-Events (Zeile 15837)
        // 3. Pfeiltasten-Events (Zeile 15819)
        // codeArea.setOnMouseClicked(event -> {
        //     if (previewWebView != null && previewStage != null && previewStage.isShowing()) {
        //         scrollToLineInQuill();
        //     }
        // });
        
        // Pfeiltasten: Suche im Quill und scroll dorthin
        codeArea.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (previewWebView != null && previewStage != null && previewStage.isShowing()) {
                KeyCode code = event.getCode();
                if (code == KeyCode.UP || code == KeyCode.DOWN || 
                    code == KeyCode.PAGE_UP || code == KeyCode.PAGE_DOWN ||
                    code == KeyCode.HOME || code == KeyCode.END) {
                    Platform.runLater(() -> {
                        Platform.runLater(() -> {
                            scrollToLineInQuill();
                        });
                    });
                }
            }
        });
        
        // Mouse-Scroll-Rad: Erkenne Scrollen und synchronisiere Quill
        final java.util.concurrent.atomic.AtomicReference<java.util.Timer> scrollSyncTimer = new java.util.concurrent.atomic.AtomicReference<>();
        
        EventHandler<ScrollEvent> scrollSyncHandler = event -> {
            // WICHTIG: Nur synchronisieren, wenn der Benutzer im Editor scrollt, nicht wenn Quill programmatisch scrollt
            // Prüfe zusätzlich, ob Quill gerade programmatisch gescrollt wird (verhindert Rückkopplung)
            if (!isScrollingPreview && previewWebView != null && previewStage != null && previewStage.isShowing() && useQuillMode) {
                javafx.scene.web.WebEngine engine = previewWebView.getEngine();
                if (engine.getLoadWorker().getState() == javafx.concurrent.Worker.State.SUCCEEDED) {
                    // Prüfe, ob Quill gerade programmatisch scrollt (verhindert Rückkopplung)
                    try {
                        Object quillScrollingProgrammatically = engine.executeScript("(window.isQuillScrollingProgrammatically || false)");
                        if (Boolean.TRUE.equals(quillScrollingProgrammatically)) {
                            // Quill scrollt gerade programmatisch, keine Synchronisation
                            return;
                        }
                    } catch (Exception e) {
                        // Ignoriere Fehler bei der Prüfung
                    }
                }
                
                Timer oldTimer = scrollSyncTimer.getAndSet(null);
                if (oldTimer != null) {
                    oldTimer.cancel();
                }
                
                Timer newTimer = new Timer(true);
                scrollSyncTimer.set(newTimer);
                
                newTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        Platform.runLater(() -> {
                            try {
                                // Prüfe nochmal, ob Quill gerade programmatisch scrollt
                                if (engine.getLoadWorker().getState() == javafx.concurrent.Worker.State.SUCCEEDED) {
                                    try {
                                        Object quillScrollingProgrammatically = engine.executeScript("(window.isQuillScrollingProgrammatically || false)");
                                        if (Boolean.TRUE.equals(quillScrollingProgrammatically)) {
                                            return; // Quill scrollt gerade programmatisch, keine Synchronisation
                                        }
                                    } catch (Exception e) {
                                        // Ignoriere Fehler
                                    }
                                    
                                    String anchor = getMiddleParagraphSnippet();
                                    if (anchor != null && anchor.trim().length() > 3) {
                                        // WICHTIG: scrollQuillToText setzt isQuillScrollingProgrammatically automatisch
                                        String script = String.format(
                                            "if (window.scrollQuillToText) { window.scrollQuillToText(%s); }",
                                            toJSString(anchor.trim()));
                                        engine.executeScript(script);
                                    }
                                }
                            } catch (Exception e) {
                                logger.debug("Fehler bei Quill Scroll-Synchronisation: " + e.getMessage());
                            }
                        });
                    }
                }, 50); // Erhöht auf 50ms für besseres Debouncing
            }
        };
        
        // Auf VirtualizedScrollPane registrieren
        if (scrollPane != null) {
            try {
                scrollPane.addEventFilter(ScrollEvent.SCROLL, scrollSyncHandler);
            } catch (Exception e) {
                logger.debug("Fehler beim Registrieren des Scroll-Event-Filters für Quill", e);
            }
        }
        
        // ENTFERNT: Polling-Mechanismus war zu aggressiv und hat Scroll-Position zurückgesetzt
        // Die Scroll-Synchronisation erfolgt bereits über:
        // 1. currentParagraphProperty Listener (Cursor-Bewegung)
        // 2. Scroll-Events (Mausrad, Scrollbar)
        // 3. Pfeiltasten-Events
        // Ein zusätzlicher Polling-Mechanismus würde die Scroll-Position stören, wenn der Benutzer in Quill scrollt
        
        // Auf CodeArea registrieren
        if (codeArea != null) {
            try {
                codeArea.addEventFilter(ScrollEvent.SCROLL, scrollSyncHandler);
            } catch (Exception e) {
                logger.debug("Fehler beim Registrieren des Scroll-Event-Filters für Quill", e);
            }
        }
    }
    
    /**
     * Richtet Scroll-Synchronisation für normalen WebView-Modus ein
     */
    private void setupNormalViewScrollSync() {
        if (codeArea == null || previewWebView == null) {
            return;
        }
        
        // Mouse-Scroll-Rad: Erkenne Scrollen und synchronisiere normalen WebView
        final java.util.concurrent.atomic.AtomicReference<java.util.Timer> scrollSyncTimer = new java.util.concurrent.atomic.AtomicReference<>();
        
        EventHandler<ScrollEvent> scrollSyncHandler = event -> {
            // Nur synchronisieren, wenn der Benutzer im Editor scrollt, nicht wenn WebView scrollt
            if (!isScrollingPreview && previewWebView != null && previewStage != null && previewStage.isShowing() && !useQuillMode) {
                Timer oldTimer = scrollSyncTimer.getAndSet(null);
                if (oldTimer != null) {
                    oldTimer.cancel();
                }
                
                Timer newTimer = new Timer(true);
                scrollSyncTimer.set(newTimer);
                
                newTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        Platform.runLater(() -> {
                            try {
                                javafx.scene.web.WebEngine engine = previewWebView.getEngine();
                                if (engine.getLoadWorker().getState() == javafx.concurrent.Worker.State.SUCCEEDED) {
                                    // Textbasierte Synchronisation: Finde sichtbaren Text in CodeArea
                                    String anchor = getMiddleParagraphSnippet();
                                    if (anchor != null && anchor.trim().length() > 3) {
                                        // Suche nach dem Text im HTML und scrolle dorthin
                                        String script = String.format(
                                            "(function() { " +
                                            "  var text = document.body.innerText || document.body.textContent || ''; " +
                                            "  var snippet = %s; " +
                                            "  var idx = text.indexOf(snippet); " +
                                            "  if (idx < 0) return false; " +
                                            "  var scrollHeight = document.body.scrollHeight - window.innerHeight; " +
                                            "  var total = text.length; " +
                                            "  if (total === 0 || scrollHeight <= 0) return false; " +
                                            "  var percent = idx / total; " +
                                            "  window.scrollTo(0, percent * scrollHeight); " +
                                            "  return true; " +
                                            "})();",
                                            toJSString(anchor.trim()));
                                        engine.executeScript(script);
                                    }
                                }
                            } catch (Exception e) {
                                logger.debug("Fehler bei normaler WebView Scroll-Synchronisation: " + e.getMessage());
                            }
                        });
                    }
                }, 50);
            }
        };
        
        // Pfeiltasten: Synchronisiere auch bei Tastatur-Navigation
        codeArea.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (previewWebView != null && previewStage != null && previewStage.isShowing() && !useQuillMode) {
                KeyCode code = event.getCode();
                if (code == KeyCode.UP || code == KeyCode.DOWN || 
                    code == KeyCode.PAGE_UP || code == KeyCode.PAGE_DOWN ||
                    code == KeyCode.HOME || code == KeyCode.END) {
                    Platform.runLater(() -> {
                        try {
                            String anchor = getMiddleParagraphSnippet();
                            if (anchor != null && anchor.trim().length() > 3) {
                                javafx.scene.web.WebEngine engine = previewWebView.getEngine();
                                if (engine.getLoadWorker().getState() == javafx.concurrent.Worker.State.SUCCEEDED) {
                                    String script = String.format(
                                        "(function() { " +
                                        "  var text = document.body.innerText || document.body.textContent || ''; " +
                                        "  var snippet = %s; " +
                                        "  var idx = text.indexOf(snippet); " +
                                        "  if (idx < 0) return false; " +
                                        "  var scrollHeight = document.body.scrollHeight - window.innerHeight; " +
                                        "  var total = text.length; " +
                                        "  if (total === 0 || scrollHeight <= 0) return false; " +
                                        "  var percent = idx / total; " +
                                        "  window.scrollTo(0, percent * scrollHeight); " +
                                        "  return true; " +
                                        "})();",
                                        toJSString(anchor.trim()));
                                    engine.executeScript(script);
                                }
                            }
                        } catch (Exception e) {
                            logger.debug("Fehler bei normaler WebView Scroll-Synchronisation (Tastatur)", e);
                        }
                    });
                }
            }
        });
        
        // Auf VirtualizedScrollPane registrieren
        if (scrollPane != null) {
            try {
                scrollPane.addEventFilter(ScrollEvent.SCROLL, scrollSyncHandler);
            } catch (Exception e) {
                logger.debug("Fehler beim Registrieren des Scroll-Event-Filters für normalen WebView", e);
            }
        }
        
        // Auf CodeArea registrieren
        if (codeArea != null) {
            try {
                codeArea.addEventFilter(ScrollEvent.SCROLL, scrollSyncHandler);
            } catch (Exception e) {
                logger.debug("Fehler beim Registrieren des Scroll-Event-Filters für normalen WebView", e);
            }
        }
        
        // Umgekehrte Synchronisation: WebView → CodeArea (Polling)
        startNormalViewChangePolling();
    }
    
    /**
     * Startet Polling für Scroll-Änderungen im normalen WebView-Modus
     */
    private void startNormalViewChangePolling() {
        if (previewWebView == null || codeArea == null) {
            return;
        }
        
        javafx.scene.web.WebEngine engine = previewWebView.getEngine();
        
        // JavaScript für Scroll-Erkennung im normalen WebView hinzufügen
        String scrollDetectionScript = 
            "(function() { " +
            "  var lastScrollTop = 0; " +
            "  var scrollTimeout = null; " +
            "  window.addEventListener('scroll', function() { " +
            "    clearTimeout(scrollTimeout); " +
            "    scrollTimeout = setTimeout(function() { " +
            "      var currentScrollTop = window.pageYOffset || document.documentElement.scrollTop; " +
            "      if (Math.abs(currentScrollTop - lastScrollTop) > 10) { " +
            "        window.normalViewScrollChanged = true; " +
            "        window.normalViewLastScroll = currentScrollTop; " +
            "        lastScrollTop = currentScrollTop; " +
            "      } " +
            "    }, 50); " +
            "  }); " +
            "  window.getMiddleVisibleTextNormal = function() { " +
            "    var scrollTop = window.pageYOffset || document.documentElement.scrollTop; " +
            "    var visibleHeight = window.innerHeight; " +
            "    var midPixel = scrollTop + visibleHeight / 2; " +
            "    var text = document.body.innerText || document.body.textContent || ''; " +
            "    if (!text) return ''; " +
            "    var scrollHeight = document.body.scrollHeight - visibleHeight; " +
            "    if (scrollHeight <= 0) { " +
            "      var midIdx = Math.floor(text.length / 2); " +
            "      return text.slice(Math.max(0, midIdx - 40), Math.min(text.length, midIdx + 40)).trim(); " +
            "    } " +
            "    var percent = midPixel / (document.body.scrollHeight); " +
            "    var charIdx = Math.min(text.length - 1, Math.max(0, Math.floor(percent * text.length))); " +
            "    return text.slice(Math.max(0, charIdx - 40), Math.min(text.length, charIdx + 40)).trim(); " +
            "  }; " +
            "})();";
        
        engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                try {
                    engine.executeScript(scrollDetectionScript);
                } catch (Exception e) {
                    logger.debug("Fehler beim Einrichten der Scroll-Erkennung für normalen WebView", e);
                }
            }
        });
        
        // Polling-Timeline für Scroll-Änderungen
        Timeline pollingTimeline = new Timeline(new KeyFrame(Duration.millis(100), event -> {
            try {
                if (engine.getLoadWorker().getState() != javafx.concurrent.Worker.State.SUCCEEDED) {
                    return;
                }
                if (useQuillMode) {
                    return; // Nur im normalen Modus
                }
                
                // Prüfe auf Scroll-Änderungen
                Object scrollChanged = engine.executeScript("(window.normalViewScrollChanged || false)");
                if (Boolean.TRUE.equals(scrollChanged)) {
                    engine.executeScript("window.normalViewScrollChanged = false;");
                    
                    // Scroll-Informationen verarbeiten: Normaler WebView → Editor (textbasiert)
                    try {
                        if (codeArea != null) {
                            Platform.runLater(() -> {
                                try {
                                    isScrollingPreview = true;
                                    Object snippetObj = engine.executeScript("(window.getMiddleVisibleTextNormal ? window.getMiddleVisibleTextNormal() : '')");
                                    String snippet = snippetObj != null ? snippetObj.toString().trim() : "";
                                    if (snippet.length() > 3) {
                                        int idx = findParagraphBySnippet(snippet);
                                        if (idx >= 0) {
                                            scrollEditorToParagraphCentered(idx);
                                        }
                                    }
                                } finally {
                                    isScrollingPreview = false;
                                }
                            });
                        }
                    } catch (Exception e) {
                        logger.debug("Fehler bei normaler WebView Scroll-Sync zu CodeArea: " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                logger.debug("Fehler beim Polling (normaler WebView): " + e.getMessage());
            }
        }));
        pollingTimeline.setCycleCount(Timeline.INDEFINITE);
        pollingTimeline.play();
    }
    
    /**
     * Synchronisiert Scroll-Position von CodeArea zu Quill (textbasiert)
     */
    private void syncQuillScroll(int paragraphIndex) {
        if (previewWebView == null || codeArea == null) {
            return;
        }
        
        try {
            String anchor = getMiddleParagraphSnippet();
            if (anchor != null && anchor.trim().length() > 3) {
                Platform.runLater(() -> {
                    try {
                        javafx.scene.web.WebEngine engine = previewWebView.getEngine();
                        if (engine.getLoadWorker().getState() == javafx.concurrent.Worker.State.SUCCEEDED) {
                            String script = String.format(
                                "if (window.scrollQuillToText) { window.scrollQuillToText(%s); }",
                                toJSString(anchor.trim()));
                            engine.executeScript(script);
                        }
                    } catch (Exception e) {
                        logger.debug("Fehler bei Quill Scroll-Synchronisation", e);
                    }
                });
            }
        } catch (Exception e) {
            logger.warn("Fehler bei Quill Scroll-Synchronisation", e);
        }
    }
    
    /**
     * Scrollt im Quill Editor zur aktuellen Editor-Zeile
     */
    private void scrollToLineInQuill() {
        if (previewWebView == null || codeArea == null) {
            return;
        }
        
        try {
            String anchor = getMiddleParagraphSnippet();
            if (anchor != null && anchor.trim().length() > 3) {
                Platform.runLater(() -> {
                    try {
                        javafx.scene.web.WebEngine engine = previewWebView.getEngine();
                        if (engine.getLoadWorker().getState() == javafx.concurrent.Worker.State.SUCCEEDED) {
                            String script = String.format(
                                "if (window.scrollQuillToText) { window.scrollQuillToText(%s); }",
                                toJSString(anchor.trim()));
                            engine.executeScript(script);
                        }
                    } catch (Exception e) {
                        logger.debug("Fehler beim Scrollen zu Text in Quill", e);
                    }
                });
            }
        } catch (Exception e) {
            logger.debug("Fehler beim Scrollen zu Zeile in Quill", e);
        }
    }
    
    /**
     * Extrahiert einen Text-Snippet aus dem tatsächlich sichtbaren Mittelpunkt des CodeArea-Viewports
     */
    private String getMiddleParagraphSnippet() {
        if (codeArea == null || scrollPane == null) return "";
        try {
            // Berechne die tatsächlich sichtbare Mitte des Viewports
            // VirtualizedScrollPane hat keine direkte getViewportBounds(), verwende stattdessen
            // die Bounds des scrollPane selbst und berechne die Mitte relativ zum CodeArea
            Bounds scrollPaneBounds = scrollPane.getBoundsInLocal();
            double viewportHeight = scrollPaneBounds.getHeight();
            double viewportMidY = scrollPaneBounds.getMinY() + viewportHeight / 2;
            
            // Konvertiere Y-Position zu Text-Position
            Point2D viewportPoint = new Point2D(scrollPaneBounds.getMinX() + scrollPaneBounds.getWidth() / 2, viewportMidY);
            Point2D scenePoint = scrollPane.localToScene(viewportPoint);
            Point2D codeAreaPoint = codeArea.sceneToLocal(scenePoint);
            
            // Finde den Absatz an dieser Position
            int charPosition = codeArea.hit(codeAreaPoint.getX(), codeAreaPoint.getY()).getInsertionIndex();
            int paragraphIndex = codeArea.offsetToPosition(charPosition, Bias.Forward).getMajor();
            
            int totalParagraphs = codeArea.getParagraphs().size();
            if (totalParagraphs == 0) return "";
            if (paragraphIndex < 0) paragraphIndex = 0;
            if (paragraphIndex >= totalParagraphs) paragraphIndex = totalParagraphs - 1;
            
            // Prüfe mehrere Absätze in der Nähe (aktueller, +1, -1, +2, -2, etc.)
            // um den besten Snippet zu finden
            for (int offset = 0; offset <= 3; offset++) {
                // Prüfe Absatz nach oben
                int para = paragraphIndex - offset;
                if (para >= 0 && para < totalParagraphs) {
                    String text = codeArea.getParagraph(para).getText();
                    if (text != null && text.trim().length() > 3) {
                        text = text.trim();
                        int mid = text.length() / 2;
                        int start = Math.max(0, mid - 40);
                        int end = Math.min(text.length(), mid + 40);
                        String snippet = text.substring(start, end).trim();
                        if (snippet.length() > 3) {
                            return snippet;
                        }
                    }
                }
                
                // Prüfe Absatz nach unten (nur wenn offset > 0)
                if (offset > 0) {
                    para = paragraphIndex + offset;
                    if (para >= 0 && para < totalParagraphs) {
                        String text = codeArea.getParagraph(para).getText();
                        if (text != null && text.trim().length() > 3) {
                            text = text.trim();
                            int mid = text.length() / 2;
                            int start = Math.max(0, mid - 40);
                            int end = Math.min(text.length(), mid + 40);
                            String snippet = text.substring(start, end).trim();
                            if (snippet.length() > 3) {
                                return snippet;
                            }
                        }
                    }
                }
            }
            
            return "";
        } catch (Exception e) {
            logger.debug("Fehler beim Extrahieren des mittleren Absatz-Snippets", e);
            // Fallback auf alte Methode
            return getMiddleParagraphSnippetFallback();
        }
    }
    
    /**
     * Fallback-Methode: Verwendet currentParagraph (alte Implementierung)
     */
    private String getMiddleParagraphSnippetFallback() {
        if (codeArea == null) return "";
        try {
            int totalParagraphs = codeArea.getParagraphs().size();
            if (totalParagraphs == 0) return "";
            
            int currentPara = codeArea.getCurrentParagraph();
            if (currentPara < 0) currentPara = 0;
            if (currentPara >= totalParagraphs) currentPara = totalParagraphs - 1;
            
            for (int offset = 0; offset <= 3; offset++) {
                int para = currentPara - offset;
                if (para >= 0 && para < totalParagraphs) {
                    String text = codeArea.getParagraph(para).getText();
                    if (text != null && text.trim().length() > 3) {
                        text = text.trim();
                        int mid = text.length() / 2;
                        int start = Math.max(0, mid - 40);
                        int end = Math.min(text.length(), mid + 40);
                        String snippet = text.substring(start, end).trim();
                        if (snippet.length() > 3) {
                            return snippet;
                        }
                    }
                }
                
                if (offset > 0) {
                    para = currentPara + offset;
                    if (para >= 0 && para < totalParagraphs) {
                        String text = codeArea.getParagraph(para).getText();
                        if (text != null && text.trim().length() > 3) {
                            text = text.trim();
                            int mid = text.length() / 2;
                            int start = Math.max(0, mid - 40);
                            int end = Math.min(text.length(), mid + 40);
                            String snippet = text.substring(start, end).trim();
                            if (snippet.length() > 3) {
                                return snippet;
                            }
                        }
                    }
                }
            }
            
            return "";
        } catch (Exception e) {
            logger.debug("Fehler beim Extrahieren des mittleren Absatz-Snippets (Fallback)", e);
            return "";
        }
    }
    
    /**
     * Findet einen eindeutigen Absatz im CodeArea, der den gegebenen Snippet enthält
     * @param snippet Der zu suchende Text-Snippet
     * @return Index des gefundenen Absatzes, oder -1 wenn kein eindeutiger Treffer gefunden wurde
     */
    private int findParagraphBySnippet(String snippet) {
        if (codeArea == null || snippet == null || snippet.trim().isEmpty()) return -1;
        try {
            String needle = snippet.trim();
            int foundIdx = -1;
            int matches = 0;
            for (int i = 0; i < codeArea.getParagraphs().size(); i++) {
                String p = codeArea.getParagraph(i).getText();
                if (p != null && p.contains(needle)) {
                    matches++;
                    foundIdx = i;
                    if (matches > 1) break; // Mehrere Treffer = nicht eindeutig
                }
            }
            return matches == 1 ? foundIdx : -1;
        } catch (Exception e) {
            logger.debug("Fehler beim Suchen nach Snippet", e);
            return -1;
        }
    }
    
    /**
     * Scrollt den CodeArea zu einem bestimmten Absatz und zentriert ihn wirklich in der Mitte
     * @param idx Index des Absatzes
     */
    private void scrollEditorToParagraphCentered(int idx) {
        if (codeArea == null || scrollPane == null || idx < 0 || idx >= codeArea.getParagraphs().size()) return;
        try {
            // Zeige den Absatz oben an, dann einmal mittig – reduziert "Schlackern"
            codeArea.showParagraphAtTop(idx);
            Platform.runLater(() -> {
                try {
                    codeArea.showParagraphAtCenter(idx);
                } catch (Exception e) {
                    logger.debug("Fehler beim Zentrieren: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            logger.debug("Fehler beim Scrollen zu Absatz", e);
            // Fallback: einfache Top-Positionierung
            try {
                codeArea.showParagraphAtTop(Math.max(0, idx - 1));
            } catch (Exception e2) {
                logger.debug("Fehler beim Fallback-Scrollen", e2);
            }
        }
    }
    
    /**
     * Wendet Theme auf Quill Editor an
     */
    private void applyThemeToQuill(int themeIndex) {
        applyThemeToQuill(themeIndex, true);
    }
    
    /**
     * Wendet Theme auf Quill Editor an
     * @param themeIndex Der Theme-Index
     * @param updateFontSize Ob die Fontgröße aktualisiert werden soll (true) oder beibehalten werden soll (false)
     */
    private void applyThemeToQuill(int themeIndex, boolean updateFontSize) {
        if (previewWebView == null) {
            return;
        }
        
        try {
            String[] theme = THEMES[themeIndex];
            String backgroundColor = theme[0];
            String textColor = theme[1];
            String selectionColor = theme[2];
            String caretColor = theme[3];
            
            // Erstelle CSS für Quill Theme
            String css = String.format(
                ".ql-editor { " +
                "  background-color: %s !important; " +
                "  color: %s !important; " +
                "  font-family: 'Consolas', 'Monaco', monospace; " +
                "} " +
                ".ql-container { " +
                "  background-color: %s !important; " +
                "} " +
                ".ql-toolbar { " +
                "  background-color: %s !important; " +
                "  border-color: %s !important; " +
                "} " +
                ".ql-toolbar .ql-stroke { " +
                "  stroke: %s !important; " +
                "} " +
                ".ql-toolbar .ql-fill { " +
                "  fill: %s !important; " +
                "} " +
                ".ql-toolbar button:hover, .ql-toolbar button.ql-active { " +
                "  background-color: %s !important; " +
                "} " +
                ".ql-editor.ql-blank::before { " +
                "  color: %s !important; " +
                "} " +
                ".ql-snow .ql-stroke { " +
                "  stroke: %s !important; " +
                "} " +
                ".ql-snow .ql-fill { " +
                "  fill: %s !important; " +
                "} " +
                ".ql-editor blockquote { " +
                "  border-left: none !important; " +
                "  padding-left: 2em !important; " +
                "  font-style: italic !important; " +
                "  color: #666666 !important; " +
                "  margin-left: 0 !important; " +
                "  margin-right: 0 !important; " +
                "}",
                backgroundColor, textColor, backgroundColor, backgroundColor, textColor,
                textColor, textColor, selectionColor, textColor, textColor, textColor
            );
            // Blocksatz-Ausrichtung in Quill erzwingen mit besserem Abstand
            String justifyAlign = previewJustifyEnabled ? "justify" : "left";
            css += String.format(" .ql-editor { text-align: %s !important; } p { text-align: %s !important; text-align-last: left !important; word-spacing: 0.1em; } ",
                    justifyAlign, justifyAlign);
            final String themeCss = css;
            final boolean finalUpdateFontSize = updateFontSize;
            
            javafx.scene.web.WebEngine engine = previewWebView.getEngine();
            if (engine.getLoadWorker().getState() == javafx.concurrent.Worker.State.SUCCEEDED) {
                String script = String.format("if (window.applyQuillTheme) { window.applyQuillTheme(%s); }", 
                    toJSString(themeCss));
                engine.executeScript(script);
                
                // Fontgröße nur setzen wenn updateFontSize true ist
                if (finalUpdateFontSize) {
                    // Lade aktuelle Schriftgröße für Quill (aus Quill-spezifischen Preferences)
                    int fontSize = preferences.getInt("quillFontSize", 0);
                    if (fontSize == 0) {
                        // Fallback auf Editor-Fontgröße nur beim ersten Mal
                        fontSize = preferences.getInt("fontSize", 14);
                        preferences.putInt("quillFontSize", fontSize);
                    }
                    String fontSizeScript = String.format("if (window.setQuillGlobalFontSize) { window.setQuillGlobalFontSize(%d); }", fontSize);
                    engine.executeScript(fontSizeScript);
                }
            } else {
                // Warte auf Load, dann Theme anwenden
                engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                    if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                        Platform.runLater(() -> {
                            String script = String.format("if (window.applyQuillTheme) { window.applyQuillTheme(%s); }", 
                                toJSString(themeCss));
                            engine.executeScript(script);
                            
                            // Fontgröße nur setzen wenn updateFontSize true ist
                            if (finalUpdateFontSize) {
                                // WICHTIG: Lade aktuelle Fontgröße aus Preferences, nicht überschreiben
                                int currentFontSize = preferences.getInt("quillFontSize", 0);
                                if (currentFontSize == 0) {
                                    currentFontSize = preferences.getInt("fontSize", 14);
                                    preferences.putInt("quillFontSize", currentFontSize);
                                }
                                String fontSizeScript = String.format("if (window.setQuillGlobalFontSize) { window.setQuillGlobalFontSize(%d); }", currentFontSize);
                                engine.executeScript(fontSizeScript);
                            }
                        });
                    }
                });
            }
        } catch (Exception e) {
            logger.error("Fehler beim Anwenden des Themes auf Quill", e);
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
        return getSelectedTextSafely();
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
     * Aktiviert englische Anführungszeichen für KI-Assistent
     */
    public void enableEnglishQuotesForAI() {
        if (cmbQuoteStyle != null) {
            // Speichere die aktuelle Einstellung (nur wenn Dropdown noch nicht disabled ist)
            // Das bedeutet, der KI-Assistent wurde gerade erst geöffnet
            if (!cmbQuoteStyle.isDisabled()) {
                originalQuoteStyleIndex = currentQuoteStyleIndex;
            }
            
            // Setze auf englische Anführungszeichen (Index 2) - IMMER
            boolean wasAlreadyEnglish = (currentQuoteStyleIndex == 2);
            currentQuoteStyleIndex = 2;
            cmbQuoteStyle.setValue(QUOTE_STYLES[2][0]);
            cmbQuoteStyle.setDisable(true); // IMMER auf disabled setzen, wenn KI-Assistent geöffnet ist
            
            // Speichere die Einstellung in den Preferences
            preferences.put("quoteStyle", String.valueOf(2));
            try {
                preferences.flush();
            } catch (java.util.prefs.BackingStoreException ex) {
                logger.warn("Konnte Quote-Style nicht speichern: " + ex.getMessage());
            }
            
            // Konvertiere alle Anführungszeichen im Text nur wenn noch nicht englisch
            if (!wasAlreadyEnglish) {
                convertAllQuotationMarksInText(QUOTE_STYLES[2][0]);
            }
            
            // Zeige Alert nur wenn nicht bereits englisch und nicht unterdrückt
            boolean showAlert = preferences.getBoolean("show_ai_quote_alert", true);
            if (!wasAlreadyEnglish && showAlert) {
                Platform.runLater(() -> {
                    // Checkbox für "Nicht mehr anzeigen"
                    CheckBox dontShowAgain = new CheckBox("Nicht mehr anzeigen");
                    dontShowAgain.setStyle("-fx-font-size: 11px;");
                    
                    VBox content = new VBox(10);
                    content.setPadding(new Insets(10));
                    content.getChildren().addAll(
                        new Label("Solange der KI-Assistent geöffnet ist, werden englische Anführungszeichen verwendet.\n Wird der KI-Assistent geschlossen, wird der vorherige Zustand wiederhergestellt."),
                        dontShowAgain
                    );
                    
                    CustomAlert alert = new CustomAlert(Alert.AlertType.INFORMATION, "KI-Assistent");
                    alert.setHeaderText(null);
                    alert.setCustomContent(content);
                    alert.applyTheme(currentThemeIndex);
                    alert.initOwner(stage);
                    
                    Optional<ButtonType> result = alert.showAndWait();
                    
                    // Speichere die Einstellung wenn Checkbox aktiviert
                    if (dontShowAgain.isSelected()) {
                        preferences.putBoolean("show_ai_quote_alert", false);
                        try {
                            preferences.flush();
                        } catch (java.util.prefs.BackingStoreException ex) {
                            logger.warn("Konnte Alert-Einstellung nicht speichern: " + ex.getMessage());
                        }
                    }
                });
            }
        }
    }
    /**
     * Prüft ob der KI-Assistent offen ist und konvertiert Anführungszeichen falls nötig
     */
    private void checkAndConvertQuotesForAI() {
        if (ollamaWindow != null && ollamaWindow.isShowing()) {
            // KI-Assistent ist offen - prüfe ob englische Anführungszeichen bereits aktiv sind
            if (cmbQuoteStyle != null && !cmbQuoteStyle.isDisabled()) {
                // Englische Anführungszeichen sind noch nicht aktiv - aktiviere sie
                enableEnglishQuotesForAI();
            } else if (cmbQuoteStyle != null && cmbQuoteStyle.isDisabled() && currentQuoteStyleIndex == 2) {
                // Englische Anführungszeichen sind bereits aktiv - konvertiere den Text
                convertAllQuotationMarksInText(QUOTE_STYLES[2][0]);
            }
        }
    }
    
    /**
     * Prüft ob ein OllamaWindow aktiv ist (für MainController)
     */
    public boolean hasActiveOllamaWindow() {
        return ollamaWindow != null && ollamaWindow.isShowing();
    }
    
    /**
     * Stellt die ursprünglichen Anführungszeichen-Einstellungen wieder her
     */
    public void restoreOriginalQuotes() {
        if (cmbQuoteStyle != null) {
            // Stelle die ursprüngliche Einstellung wieder her
            currentQuoteStyleIndex = originalQuoteStyleIndex;
            cmbQuoteStyle.setValue(QUOTE_STYLES[originalQuoteStyleIndex][0]);
            cmbQuoteStyle.setDisable(false);
            
            // Speichere die ursprüngliche Einstellung in den Preferences
            preferences.put("quoteStyle", String.valueOf(originalQuoteStyleIndex));
            try {
                preferences.flush();
            } catch (java.util.prefs.BackingStoreException ex) {
                logger.warn("Konnte Quote-Style nicht speichern: " + ex.getMessage());
            }
            
            // Konvertiere alle Anführungszeichen zurück zur ursprünglichen Einstellung
            convertAllQuotationMarksInText(QUOTE_STYLES[originalQuoteStyleIndex][0]);
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
        navigateToChapter(previousFile);
        
        updateStatus("Vorheriges Kapitel geladen: " + previousFile.getName());
        scheduleStatusClear(5, false);
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
        navigateToChapter(nextFile);
        
        updateStatus("Nächstes Kapitel geladen: " + nextFile.getName());
        scheduleStatusClear(5, false);
    }
    /**
     * Navigiert zu einem Kapitel - prüft auf bestehende Editoren
     */
    private void navigateToChapter(File targetFile) {
        if (mainController == null) {
            updateStatus("Navigation nicht verfügbar - MainController nicht gesetzt");
            return;
        }
        
        // Erstelle den Editor-Key für das Ziel-Kapitel
        String chapterName = targetFile.getName();
        if (chapterName.toLowerCase().endsWith(".docx")) {
            chapterName = chapterName.substring(0, chapterName.length() - 5);
        }
        String editorKey = chapterName + ".md";
        
        // Prüfe, ob bereits ein Editor für dieses Kapitel existiert
        EditorWindow existingEditor = mainController.findExistingEditor(editorKey);
        
        if (existingEditor != null && existingEditor != this) {
            // Speichere die aktuelle Cursor-Position bevor wir wechseln
            saveCurrentCursorPosition();
            
            // Editor existiert bereits UND ist nicht der aktuelle Editor - bringe ihn in den Vordergrund und schließe den aktuellen Editor
            Platform.runLater(() -> {
                if (existingEditor.getStage() != null && existingEditor.getStage().isShowing()) {
                    // Fenster in den Vordergrund bringen
                    existingEditor.getStage().setIconified(false); // Entminimieren falls minimiert
                    existingEditor.getStage().toFront(); // In den Vordergrund
                    existingEditor.getStage().requestFocus(); // Fokus setzen
                    
                    // Stelle die Cursor-Position für das bestehende Kapitel wieder her
                    existingEditor.restoreCursorPosition();
                }
            });
            updateStatus("Bestehender Editor für '" + targetFile.getName() + "' in den Vordergrund gebracht");
            
            // Schließe den aktuellen Editor
            Platform.runLater(() -> {
                if (stage != null && stage.isShowing()) {
                    stage.close();
                }
            });
        } else {
            // Speichere die aktuelle Cursor-Position bevor wir ein neues Kapitel laden
            saveCurrentCursorPosition();
            
            // Kein Editor existiert ODER es ist derselbe Editor - lade das Kapitel in den aktuellen Editor
            loadChapterFile(targetFile);
        }
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
        // WICHTIG: Verhindere Race Conditions beim schnellen Blättern
        // Erhöhe Sequenznummer, um laufende Ladevorgänge zu invalidieren
        final long mySequence = ++loadingSequence;
        logger.debug("Lade Kapitel: " + file.getName() + " (Sequenz: " + mySequence + ")");
        
        // WICHTIG: Breche alle laufenden LanguageTool-Checks ab, bevor ein neues Kapitel geladen wird
        cancelLanguageToolChecks();
        
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
                        
                        // Verwende if-else statt switch, um NoSuchMethodError zu vermeiden
                        if (decision == MainController.DocxChangeDecision.DIFF) {
                            // Diff-Fenster über MainController öffnen
                            if (mainController != null) {
                                File mdFile = deriveSidecarFileFor(file, outputFormat);
                                // WICHTIG: Übergib diesen Editor als aufrufenden Editor
                                mainController.showDetailedDiffDialog(docxFile, mdFile, null, outputFormat, this);
                            }
                            // Bei DIFF warten - kein weiterer Ladeprozess
                            return;
                        } else if (decision == MainController.DocxChangeDecision.DOCX) {
                            // DOCX-Inhalt laden und anzeigen
                            String docxContent = docxProcessor.processDocxFileContent(file, chapterNumber, outputFormat);
                            // WICHTIG: Setze originalDocxFile VOR setText
                            setOriginalDocxFile(file);
                            setText(docxContent, false); // Sequenz wird von loadChapterFile() verwaltet
                            setCurrentFile(deriveSidecarFileForCurrentFormat());
                            setWindowTitle("📄 " + file.getName());
                            originalContent = cleanTextForComparison(docxContent);
                            // WICHTIG: Navigation-Buttons erst NACH setCurrentFile() aktualisieren
                            Platform.runLater(() -> {
                                if (mySequence == loadingSequence) {
                            updateNavigationButtons();
                                }
                            });
                            // Bei DOCX-Übernahme normalen Ladeprozess fortsetzen
                        } else if (decision == MainController.DocxChangeDecision.CANCEL) {
                            // Bei CANCEL warten
                            return;
                        }
                        // IGNORE: Bestehenden Inhalt beibehalten - normalen Ladeprozess fortsetzen
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

            // WICHTIG: Prüfe, ob diese Operation noch aktuell ist (nicht durch neueres Kapitel überschrieben)
            if (mySequence != loadingSequence) {
                logger.debug("loadChapterFile abgebrochen vor setText - neueres Kapitel wird bereits geladen (Sequenz: " + mySequence + " vs " + loadingSequence + ")");
                return;
            }

            // WICHTIG: Setze originalDocxFile VOR setText, damit restoreCursorPosition den richtigen Key verwendet
            setOriginalDocxFile(file);
            
            // Setze den Inhalt und Dateireferenzen
            // WICHTIG: incrementSequence=false, da loadChapterFile() die Sequenz bereits verwaltet
            setText(content, false);
            
            // WICHTIG: Nach dem Laden des Kapitels den aktuellen Anführungszeichen-Stil anwenden,
            // damit Editor-Text und Preview denselben Stand haben
            if (cmbQuoteStyle != null && currentQuoteStyleIndex >= 0 && currentQuoteStyleIndex < QUOTE_STYLES.length) {
                // Englisch ist die Normalisierung, andere Stile werden explizit angewendet
                if (currentQuoteStyleIndex != 2) { // 2 = Englisch
                    String currentStyle = QUOTE_STYLES[currentQuoteStyleIndex][0];
                    convertAllQuotationMarksInText(currentStyle);
                    // Preview nach der Konvertierung explizit aktualisieren, falls sichtbar
                    if (previewWebView != null && previewStage != null && previewStage.isShowing()) {
                        lastQuillContent = null;
                        updatePreviewContent();
                    }
                }
            }
            
            // WICHTIG: Prüfe nochmal, ob diese Operation noch aktuell ist
            if (mySequence != loadingSequence) {
                logger.debug("loadChapterFile abgebrochen nach setText - neueres Kapitel wird bereits geladen");
                return;
            }
            // currentFile zeigt immer auf die Sidecar-Datei (auch wenn sie noch nicht existiert)
            setCurrentFile(sidecar != null ? sidecar : deriveSidecarFileForCurrentFormat());
            
            // Aktualisiere den Fenstertitel
            setWindowTitle("📄 " + file.getName());
            
            // Setze den ursprünglichen Inhalt für Change-Detection
            originalContent = cleanTextForComparison(content);
            
            // WICHTIG: Navigation-Buttons erst NACH setCurrentFile() aktualisieren
            // Verwende Platform.runLater() um sicherzustellen, dass alle UI-Updates abgeschlossen sind
            Platform.runLater(() -> {
                if (mySequence == loadingSequence) {
                    updateNavigationButtons();
                }
            });
            
            // Aktualisiere die Navigation-Buttons
            updateNavigationButtons();
            
            // Prüfe ob KI-Assistent offen ist und konvertiere Anführungszeichen falls nötig
            checkAndConvertQuotesForAI();
            
            // WICHTIG: Preview wird bereits in setText() aktualisiert, daher hier nicht nochmal aufrufen
            // (verhindert doppelte Updates und Timing-Probleme)
            
            // WICHTIG: Editor für das neue Kapitel in der openEditors Map registrieren
            if (mainController != null) {
                // Registriere den Editor für das neue Kapitel
                String chapterName = file.getName();
                if (chapterName.toLowerCase().endsWith(".docx")) {
                    chapterName = chapterName.substring(0, chapterName.length() - 5);
                }
                String editorKey = chapterName + ".md";
                mainController.registerEditor(editorKey, this);
            }
            
            // Anführungszeichen-Überprüfung beim Blättern zwischen Kapiteln
            Platform.runLater(() -> {
                String currentText = codeArea.getText();
                if (currentText != null && !currentText.isEmpty()) {
                    // Flag zurücksetzen, damit Dialog auch beim Blättern angezeigt wird
                    quoteErrorsDialogShown = false;
                    checkAndConvertQuotesOnLoad(currentText);
                    
                    // Automatisch Fehler-Dialog anzeigen, wenn Fehler gefunden wurden
                    if (!quoteErrors.isEmpty()) {
                        showQuoteErrorsDialog();
                    }
                }
            });
            
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
        alert.showAndWait(stage);
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
        
        // Aktualisiere auch die Kapitelliste in der Seitenleiste
        updateChapterList();
        // Zellen neu rendern, damit die Markierung aktualisiert wird
        refreshChapterListCells();
    }
    
    /**
     * Initialisiert die Seitenleiste
     */
    private void initializeSidebar() {
        if (mainSplitPane == null || sidebarContainer == null || chapterListView == null) {
            return;
        }
        
        // Lade Kapitelliste
        updateChapterList();
        
        // Setze Cell Factory für Kapitelnamen-Anzeige
        chapterListView.setCellFactory(listView -> new ListCell<DocxFile>() {
            @Override
            protected void updateItem(DocxFile docxFile, boolean empty) {
                super.updateItem(docxFile, empty);
                if (empty || docxFile == null) {
                    setText(null);
                    setStyle("");
                    getStyleClass().removeAll("current-chapter", "changed-chapter");
                } else {
                    // Verwende getDisplayFileName() für ! Markierungen
                    String displayName = docxFile.getDisplayFileName();
                    setText(displayName);
                    
                    // Entferne alle Markierungs-Klassen
                    getStyleClass().removeAll("current-chapter", "changed-chapter");
                    
                    // Markiere aktuelles Kapitel
                    if (originalDocxFile != null && docxFile.getFile().equals(originalDocxFile)) {
                        getStyleClass().add("current-chapter");
                        setStyle(""); // Keine inline Styles, damit CSS greift
                    } else if (docxFile.isChanged()) {
                        // Markiere geänderte Kapitel mit ! Symbol
                        getStyleClass().add("changed-chapter");
                        setStyle(""); // Keine inline Styles, damit CSS greift
                    } else {
                        setStyle("");
                    }
                }
            }
        });
        
        // Initiale Divider-Position wird in loadSidebarState() gesetzt
    }
    
    /**
     * Aktualisiert die Kapitelliste in der Seitenleiste
     */
    private void updateChapterList() {
        if (chapterListView == null || mainController == null) {
            return;
        }
        
        ObservableList<DocxFile> selectedDocxFiles = mainController.getSelectedDocxFilesAsDocxFiles();
        if (selectedDocxFiles.isEmpty()) {
            chapterListView.getItems().clear();
            return;
        }
        
        // Aktualisiere die Liste
        chapterListView.setItems(selectedDocxFiles);
        
        // Markiere aktuelles Kapitel
        if (originalDocxFile != null) {
            final int[] currentIndex = {-1};
            for (int i = 0; i < selectedDocxFiles.size(); i++) {
                if (selectedDocxFiles.get(i).getFile().equals(originalDocxFile)) {
                    currentIndex[0] = i;
                    break;
                }
            }
            if (currentIndex[0] >= 0) {
                final int finalIndex = currentIndex[0];
                Platform.runLater(() -> {
                    chapterListView.getSelectionModel().select(finalIndex);
                    // Kein automatisches Scrollen - nur Auswahl setzen
                });
            }
        }
    }
    
    /**
     * Klappt die Seitenleiste ein oder aus
     */
    private void toggleSidebar() {
        if (mainSplitPane == null || sidebarContainer == null || btnToggleSidebar == null) {
            return;
        }
        
        sidebarExpanded = !sidebarExpanded;
        
        if (sidebarExpanded) {
            // Seitenleiste einblenden
            sidebarContainer.setVisible(true);
            sidebarContainer.setManaged(true);
            btnToggleSidebar.setText("◀");
            // WICHTIG: Divider-Position muss nach setVisible gesetzt werden
            Platform.runLater(() -> {
                if (mainSplitPane.getItems().size() >= 2) {
                    // Lade gespeicherte Divider-Position oder verwende Standard (15%)
                    double savedPosition = preferences.getDouble("sidebar.dividerPosition", 0.15);
                    double[] positions = {savedPosition};
                    mainSplitPane.setDividerPositions(positions);
                }
            });
        } else {
            // Seitenleiste ausblenden
            sidebarContainer.setVisible(false);
            sidebarContainer.setManaged(false);
            btnToggleSidebar.setText("▶");
            Platform.runLater(() -> {
                if (mainSplitPane.getItems().size() >= 2) {
                    // Setze Divider auf 0 (vollständig eingeklappt)
                    double[] positions = {0.0};
                    mainSplitPane.setDividerPositions(positions);
                }
            });
        }
        
        // Speichere den Status
        saveSidebarState();
    }
    
    /**
     * Lädt den gespeicherten Ausklappstatus und die Divider-Position
     */
    private void loadSidebarState() {
        if (mainSplitPane == null || sidebarContainer == null || btnToggleSidebar == null) {
            return;
        }
        
        // Lade gespeicherten Status
        sidebarExpanded = preferences.getBoolean("sidebar.expanded", true);
        double savedDividerPosition = preferences.getDouble("sidebar.dividerPosition", 0.15);
        
        Platform.runLater(() -> {
            if (sidebarExpanded) {
                // Seitenleiste einblenden
                sidebarContainer.setVisible(true);
                sidebarContainer.setManaged(true);
                btnToggleSidebar.setText("◀");
                if (mainSplitPane.getItems().size() >= 2) {
                    double[] positions = {savedDividerPosition};
                    mainSplitPane.setDividerPositions(positions);
                }
            } else {
                // Seitenleiste ausblenden
                sidebarContainer.setVisible(false);
                sidebarContainer.setManaged(false);
                btnToggleSidebar.setText("▶");
                if (mainSplitPane.getItems().size() >= 2) {
                    double[] positions = {0.0};
                    mainSplitPane.setDividerPositions(positions);
                }
            }
            
            // Listener für Divider-Änderungen hinzufügen
            if (mainSplitPane.getItems().size() >= 2) {
                mainSplitPane.getDividers().get(0).positionProperty().addListener((obs, oldPos, newPos) -> {
                    if (sidebarExpanded && newPos.doubleValue() > 0.0) {
                        // Speichere nur wenn ausgeklappt und nicht 0
                        preferences.putDouble("sidebar.dividerPosition", newPos.doubleValue());
                    }
                });
                
                // Listener für Fenster-Resize: Halte Divider bei 0.0 wenn Sidebar eingeklappt ist
                mainSplitPane.widthProperty().addListener((obs, oldWidth, newWidth) -> {
                    if (!sidebarExpanded && mainSplitPane.getItems().size() >= 2) {
                        // Halte Divider bei 0.0 wenn Sidebar eingeklappt ist
                        Platform.runLater(() -> {
                            double[] positions = {0.0};
                            mainSplitPane.setDividerPositions(positions);
                        });
                    }
                });
            }
        });
    }
    
    /**
     * Speichert den aktuellen Ausklappstatus
     */
    private void saveSidebarState() {
        if (preferences != null) {
            preferences.putBoolean("sidebar.expanded", sidebarExpanded);
            if (mainSplitPane != null && mainSplitPane.getItems().size() >= 2 && sidebarExpanded) {
                double[] positions = mainSplitPane.getDividerPositions();
                if (positions.length > 0 && positions[0] > 0.0) {
                    preferences.putDouble("sidebar.dividerPosition", positions[0]);
                }
            }
        }
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
            // Verhindere Aufruf während markEmptyLines, um Undo-Historie-Probleme zu vermeiden
            if (isMarkingEmptyLines) {
                return;
            }
            
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
        // Verhindere rekursive Aufrufe, die die Undo-Historie löschen könnten
        if (isMarkingEmptyLines) {
            return;
        }
        
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
            // Flag setzen, um rekursive Aufrufe zu verhindern
            isMarkingEmptyLines = true;
            
            try {
                // Cursor-Position merken
                int caretPosition = codeArea.getCaretPosition();
                
                // Text aktualisieren - verwende replaceText mit Positionen, um Undo-Historie zu erhalten
                codeArea.replaceText(0, text.length(), newText.toString());
                
                // Cursor-Position wiederherstellen
                if (caretPosition <= newText.length()) {
                    codeArea.moveTo(caretPosition);
                }
            } finally {
                // Flag zurücksetzen
                isMarkingEmptyLines = false;
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
    
    /**
     * Java Bridge für Quill Editor Kommunikation
     */
    public class QuillBridge {
        public void onQuillContentChange(String htmlContent, String deltaJson) {
            // Merke Zeitpunkt der letzten Nutzer-Änderung in Quill, um Editor->Quill-Sync kurz zu pausieren
            lastQuillUserChangeTs = System.currentTimeMillis();
            if (codeArea == null) {
                logger.debug("CodeArea ist null, ignoriere Quill Content Change");
                return;
            }
            if (isUpdatingFromCodeArea) {
                logger.debug("Ignoriere Quill Content Change, da CodeArea gerade aktualisiert wird");
                return;
            }

            // WICHTIG: Debouncing - speichere Content und starte Timer neu
            // Synchronisation erfolgt nur, wenn Benutzer eine Pause beim Tippen macht (500ms)
            pendingQuillContent = htmlContent;
            
            // Alten Timer abbrechen falls vorhanden
            if (quillToEditorSyncTimer != null) {
                quillToEditorSyncTimer.cancel();
                quillToEditorSyncTimer = null;
            }
            
            // Neuen Timer starten (wartet 500ms nach letztem Tippen)
            quillToEditorSyncTimer = new Timer(true); // Daemon-Thread
            quillToEditorSyncTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    // Timer abgelaufen - Benutzer hat Pause gemacht, jetzt synchronisieren
                    if (pendingQuillContent != null) {
                        Platform.runLater(() -> {
                            // WICHTIG: Prüfe nochmal, ob CodeArea gerade aktualisiert wird (verhindert Feedback-Schleife)
                            if (isUpdatingFromCodeArea) {
                                logger.debug("Quill->Editor Sync übersprungen: CodeArea wird gerade aktualisiert (Timer-Callback)");
                                return;
                            }
                            performQuillToEditorSync(pendingQuillContent);
                            pendingQuillContent = null;
                        });
                    }
                }
            }, 500); // 500ms Verzögerung - wartet bis Benutzer fertig getippt hat
        }
        
        /**
         * Führt die eigentliche Synchronisation von Quill zu Editor durch
         */
        private void performQuillToEditorSync(String htmlContent) {
            if (codeArea == null || isUpdatingFromCodeArea) {
                return;
            }

            // WICHTIG: Hole die EXAKT sichtbare mittlere Zeile in Quill VOR der Text-Konvertierung
            // Dies ist die Position, die im Editor zentriert werden soll
            String quillVisibleSnippet = "";
            if (previewWebView != null) {
                javafx.scene.web.WebEngine engine = previewWebView.getEngine();
                if (engine.getLoadWorker().getState() == javafx.concurrent.Worker.State.SUCCEEDED) {
                    try {
                        Object snippetObj = engine.executeScript("(window.getMiddleVisibleText ? window.getMiddleVisibleText() : '')");
                        if (snippetObj != null) {
                            quillVisibleSnippet = snippetObj.toString().trim();
                            logger.debug("Quill sichtbare mittlere Zeile: " + (quillVisibleSnippet.length() > 50 ? quillVisibleSnippet.substring(0, 50) + "..." : quillVisibleSnippet));
                        }
                    } catch (Exception e) {
                        logger.debug("Fehler beim Abrufen der sichtbaren Quill-Position: " + e.getMessage());
                    }
                }
            }

            // Konvertierung nicht im FX-Thread durchführen (kann bei großen Bildern hängen)
            if (quillConvertExecutor == null || quillConvertExecutor.isShutdown()) {
                quillConvertExecutor = Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r, "QuillConvertWorker");
                    t.setDaemon(true);
                    return t;
                });
            }
            if (quillConvertFuture != null && !quillConvertFuture.isDone()) {
                quillConvertFuture.cancel(true);
            }

            final String htmlCopy = htmlContent;
            final String finalQuillVisibleSnippet = quillVisibleSnippet; // Final für Lambda
            // WICHTIG: Hole aktuellen Markdown-Content im FX-Thread, bevor Background-Thread startet
            final String originalMarkdown = codeArea.getText();
            
            quillConvertFuture = quillConvertExecutor.submit(() -> {
                try {
                    String markdown = convertQuillHTMLToMarkdown(htmlCopy, originalMarkdown);
                    Platform.runLater(() -> {
                        try {
                            isUpdatingFromQuill = true;

                            logger.debug("Quill Content Change empfangen, HTML-Länge: " +
                                (htmlCopy != null ? htmlCopy.length() : 0));
                            logger.debug("Konvertiert zu Markdown, Länge: " + markdown.length());

                            String currentMarkdownInFX = codeArea.getText();
                            
                            // WICHTIG: Verhindere, dass leerer Markdown den Editor leert
                            // Wenn markdown leer ist und der Editor bereits Inhalt hat, dann nicht aktualisieren
                            if (markdown != null && markdown.isEmpty() && 
                                currentMarkdownInFX != null && !currentMarkdownInFX.isEmpty()) {
                                logger.debug("Ignoriere leeren Markdown-Content von Quill, da Editor bereits Inhalt hat");
                                isUpdatingFromQuill = false;
                                return;
                            }
                            
                            // WICHTIG: Null-Check für currentMarkdownInFX
                            if (currentMarkdownInFX == null) {
                                currentMarkdownInFX = "";
                            }
                            
                            // WICHTIG: Verwende cleanTextForComparison() für Vergleich, um Whitespace-Unterschiede zu ignorieren
                            // Wenn der bereinigte Text gleich ist, dann wurde nichts geändert
                            String cleanedMarkdown = cleanTextForComparison(markdown);
                            String cleanedCurrent = cleanTextForComparison(currentMarkdownInFX);
                            
                            // WICHTIG: Prüfe ob CodeArea fokussiert ist - wenn ja, überspringe Text-Ersatz komplett
                            // Das verhindert das "Zucken" während des Tippens (replaceText setzt Scroll-Position zurück)
                            boolean codeAreaFocused = codeArea.isFocused();
                            if (codeAreaFocused) {
                                logger.debug("Quill->Editor Sync: Komplett übersprungen, da Editor fokussiert ist (verhindert Zucken durch replaceText)");
                                isUpdatingFromQuill = false;
                                return;
                            }
                            
                            // WICHTIG: Wenn der bereinigte Text gleich ist, dann nichts tun
                            // ABER: Scroll-Synchronisation sollte trotzdem funktionieren
                            if (cleanedMarkdown.equals(cleanedCurrent)) {
                                logger.debug("Quill Content Change: Bereinigter Text ist gleich, keine Text-Aktualisierung nötig");
                                // WICHTIG: isUpdatingFromQuill sofort zurücksetzen, damit Scroll-Synchronisation nicht blockiert wird
                                isUpdatingFromQuill = false;
                                return;
                            }
                            
                            // Nur wenn der bereinigte Text unterschiedlich ist, aktualisiere
                            if (!cleanedMarkdown.equals(cleanedCurrent)) {
                                
                                // Speichere Cursor-Position und Scroll-Position für Wiederherstellung
                                int caretPosition = codeArea.getCaretPosition();
                                int currentParagraph = codeArea.getCurrentParagraph();
                                String contextBefore = "";
                                String contextAfter = "";
                                int contextSize = 50;
                                
                                if (caretPosition > 0 && currentMarkdownInFX.length() > 0) {
                                    int start = Math.max(0, caretPosition - contextSize);
                                    int end = Math.min(currentMarkdownInFX.length(), caretPosition + contextSize);
                                    contextBefore = currentMarkdownInFX.substring(start, caretPosition);
                                    if (caretPosition < currentMarkdownInFX.length()) {
                                        contextAfter = currentMarkdownInFX.substring(caretPosition, end);
                                    }
                                }
                                
                                // WICHTIG: isUpdatingFromQuill ist bereits true (wurde oben gesetzt)
                                // Ersetze Text - dies wird den textProperty() Listener auslösen
                                // WICHTIG: replaceText setzt die Scroll-Position zurück, daher müssen wir sie danach sofort wiederherstellen
                                codeArea.replaceText(0, codeArea.getLength(), markdown);
                                
                                // WICHTIG: Stelle Scroll-Position SOFORT wieder her (synchron, nicht in Platform.runLater)
                                // Das verhindert das "Zucken" an den Anfang
                                try {
                                    if (currentParagraph >= 0 && currentParagraph < codeArea.getParagraphs().size()) {
                                        // Verwende showParagraphInViewport für sanfte Wiederherstellung
                                        codeArea.showParagraphInViewport(currentParagraph);
                                    }
                                } catch (Exception e) {
                                    logger.debug("Fehler beim Wiederherstellen der Scroll-Position: " + e.getMessage());
                                }
                                
                                // WICHTIG: Stelle Scroll-Position basierend auf Quill-sichtbarer Position wieder her
                                // Die EXAKT sichtbare mittlere Zeile in Quill muss auch im Editor zentriert sein
                                // ABER: Nur wenn der Editor NICHT fokussiert ist (verhindert Scrollen an den Anfang während des Tippens)
                                // UND nur wenn es KEIN programmatisches Update war (verhindert Feedback-Schleife)
                                if (!codeAreaFocused && !isUpdatingFromCodeArea && !finalQuillVisibleSnippet.isEmpty() && finalQuillVisibleSnippet.length() > 3) {
                                    try {
                                        // Suche nach dem Quill-sichtbaren Snippet im neuen Markdown
                                        // Verwende findParagraphBySnippet für zuverlässige Suche
                                        int paragraphIndex = findParagraphBySnippet(finalQuillVisibleSnippet);
                                        
                                        if (paragraphIndex >= 0) {
                                            // Paragraph gefunden - zentriere ihn im Editor
                                            scrollEditorToParagraphCentered(paragraphIndex);
                                            logger.debug("Quill-sichtbare Position synchronisiert: Paragraph " + paragraphIndex);
                                        } else {
                                            // Fallback: Suche direkt im Text
                                            int foundIndex = markdown.indexOf(finalQuillVisibleSnippet);
                                            if (foundIndex >= 0) {
                                                try {
                                                    int paraIdx = codeArea.offsetToPosition(foundIndex, Bias.Forward).getMajor();
                                                    scrollEditorToParagraphCentered(paraIdx);
                                                    logger.debug("Quill-sichtbare Position synchronisiert (Fallback): Paragraph " + paraIdx);
                                                } catch (Exception e) {
                                                    logger.debug("Fehler beim Finden der Paragraph-Position: " + e.getMessage());
                                                }
                                            } else {
                                                // Fallback 2: Suche nach Teilen des Snippets
                                                String searchSnippet = finalQuillVisibleSnippet.length() > 50 ? 
                                                    finalQuillVisibleSnippet.substring(0, 50) : finalQuillVisibleSnippet;
                                                foundIndex = markdown.indexOf(searchSnippet);
                                                if (foundIndex >= 0) {
                                                    try {
                                                        int paraIdx = codeArea.offsetToPosition(foundIndex, Bias.Forward).getMajor();
                                                        scrollEditorToParagraphCentered(paraIdx);
                                                        logger.debug("Quill-sichtbare Position synchronisiert (Fallback 2): Paragraph " + paraIdx);
                                                    } catch (Exception e) {
                                                        logger.debug("Fehler beim Finden der Paragraph-Position (Fallback 2): " + e.getMessage());
                                                    }
                                                }
                                            }
                                        }
                                    } catch (Exception e) {
                                        logger.debug("Fehler beim Synchronisieren der Quill-sichtbaren Position: " + e.getMessage());
                                    }
                                }
                                
                                // WICHTIG: Stelle Cursor-Position NUR wieder her, wenn CodeArea fokussiert war
                                // Wenn der Benutzer im Quill-Editor tippt, sollte der Cursor im CodeArea nicht springen
                                if (codeAreaFocused) {
                                    int newPosition = caretPosition; // Fallback: ursprüngliche Position
                                    
                                    // Versuche, exakte Position über Kontext zu finden
                                    if (!contextBefore.isEmpty() || !contextAfter.isEmpty()) {
                                        // Suche nach dem Kontext-Text im neuen Markdown
                                        String searchText = contextBefore + contextAfter;
                                        if (searchText.length() > 0) {
                                            // Suche von der ursprünglichen Position aus (für bessere Genauigkeit)
                                            int searchStart = Math.max(0, caretPosition - contextSize * 2);
                                            int searchEnd = Math.min(markdown.length(), caretPosition + contextSize * 2);
                                            if (searchStart < searchEnd) {
                                                String searchArea = markdown.substring(searchStart, searchEnd);
                                                int foundIndex = searchArea.indexOf(searchText);
                                                if (foundIndex >= 0) {
                                                    // Position innerhalb des gefundenen Texts
                                                    newPosition = searchStart + foundIndex + contextBefore.length();
                                                } else {
                                                    // Fallback: Suche nur nach contextBefore
                                                    if (!contextBefore.isEmpty()) {
                                                        foundIndex = searchArea.lastIndexOf(contextBefore);
                                                        if (foundIndex >= 0) {
                                                            newPosition = searchStart + foundIndex + contextBefore.length();
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    
                                    // Stelle sicher, dass Position gültig ist
                                    if (newPosition < 0) {
                                        newPosition = 0;
                                    } else if (newPosition > markdown.length()) {
                                        newPosition = markdown.length();
                                    }
                                    
                                    // Setze Cursor-Position EXAKT
                                    codeArea.moveTo(newPosition);
                                } else {
                                    // CodeArea ist nicht fokussiert - Cursor-Position nicht ändern
                                    // Die Scroll-Position wurde bereits wiederhergestellt
                                    codeArea.moveTo(markdown.length());
                                    logger.debug("CodeArea nicht fokussiert - Scroll-Position wiederhergestellt");
                                }
                                
                                // WICHTIG: Markiere als geändert, wenn der Text sich tatsächlich geändert hat
                                // Quill-Änderungen sind Benutzer-Änderungen und sollten als "ungespeichert" erkannt werden
                                // Prüfe, ob der neue Text vom Original abweicht
                                String newTextAfterSync = codeArea.getText();
                                if (!cleanTextForComparison(newTextAfterSync).equals(originalContent)) {
                                    markAsChanged();
                                    logger.debug("Quill-Änderung erkannt und als ungespeichert markiert");
                                }
                                
                                updateStatus("Quill Editor synchronisiert");
                            }
                            lastQuillContent = htmlCopy;
                        } catch (Exception e) {
                            logger.error("Fehler bei Quill Content Change (FX)", e);
                        } finally {
                            isUpdatingFromQuill = false;
                        }
                    });
                } catch (Exception e) {
                    logger.error("Fehler bei Quill Content Change (Konvertierung)", e);
                }
            });
        }
        
        public void onQuillSelectionChange(int index, int length) {
            // Optional: Cursor-Position synchronisieren
            // Kann später implementiert werden
        }
        
        public void onQuillScroll(double scrollTop, double scrollHeight) {
            if (isScrollingPreview || codeArea == null || scrollPane == null) {
                return;
            }
            
            // WICHTIG: Überspringe Scroll-Synchronisation komplett, wenn CodeArea gerade aktualisiert wird
            // Das verhindert das "Zucken" während Editor → Quill Updates
            if (isUpdatingFromCodeArea) {
                logger.debug("onQuillScroll übersprungen: CodeArea wird gerade aktualisiert (verhindert Zucken)");
                return;
            }
            
            // WICHTIG: Überspringe Scroll-Synchronisation, wenn der Editor fokussiert ist
            // Das verhindert, dass der Editor während des Tippens wegscrollt
            if (codeArea.isFocused()) {
                logger.debug("onQuillScroll übersprungen: Editor ist fokussiert (Benutzer tippt gerade)");
                return;
            }
            
            // WICHTIG: Prüfe, ob Quill gerade programmatisch gescrollt wird (Editor → Quill Sync)
            // Das verhindert Rückkopplung und Sprünge
            if (previewWebView != null) {
                javafx.scene.web.WebEngine engine = previewWebView.getEngine();
                if (engine.getLoadWorker().getState() == javafx.concurrent.Worker.State.SUCCEEDED) {
                    try {
                        Object quillScrollingProgrammatically = engine.executeScript("(window.isQuillScrollingProgrammatically || false)");
                        if (Boolean.TRUE.equals(quillScrollingProgrammatically)) {
                            // Quill wird gerade programmatisch gescrollt (Editor → Quill), ignoriere dieses Event
                            return;
                        }
                    } catch (Exception e) {
                        // Ignoriere Fehler
                    }
                }
            }
            
            // Synchronisiere Scroll-Position von Quill zu CodeArea (textbasiert)
            // Die eigentliche Synchronisation läuft über das Polling in startQuillChangePolling
            // Diese Methode wird als Fallback verwendet, falls direkt aufgerufen
            Platform.runLater(() -> {
                try {
                    if (previewWebView != null) {
                        javafx.scene.web.WebEngine engine = previewWebView.getEngine();
                        if (engine.getLoadWorker().getState() == javafx.concurrent.Worker.State.SUCCEEDED) {
                            isScrollingPreview = true;
                            Object snippetObj = engine.executeScript("(window.getMiddleVisibleText ? window.getMiddleVisibleText() : '')");
                            String snippet = snippetObj != null ? snippetObj.toString().trim() : "";
                            if (snippet.length() > 3) {
                                int idx = findParagraphBySnippet(snippet);
                                if (idx >= 0) {
                                    scrollEditorToParagraphCentered(idx);
                                }
                            }
                        }
                    }
                } finally {
                    isScrollingPreview = false;
                }
            });
        }
    }

}