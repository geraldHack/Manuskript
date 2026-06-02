package com.manuskript;

import javafx.beans.binding.Bindings;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.util.Duration;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.ScrollPane;
import javafx.scene.Node;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.geometry.Orientation;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.stage.WindowEvent;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

/**
 * Prototyp-Fenster für den eigenen Editor mit Kapitel-Laden aus dem Hauptfenster.
 */
public class ManuskriptEditorTestWindow implements ChapterEditorHost {

    private static final String PREF_USE_CANVAS = "use_canvas_chapter_editor";

    private static final String PREF_FONT_FAMILY = "prototype_editor_font_family";
    private static final String PREF_FONT_SIZE = "prototype_editor_font_size";
    private static final String PREF_LAST_IMAGE_PATH = "prototype_editor_last_image_path";
    private static final String PREF_LAST_IMAGE_ALT = "prototype_editor_last_image_alt";
    private static final String PREF_LAST_IMAGE_DIRECTORY = "prototype_editor_last_image_directory";
    private static final String PREF_LAST_IMAGE_WIDTH = "prototype_editor_last_image_width";
    private static final String PREF_HIDE_MARKUP = "prototype_editor_hide_markup";
    private static final String PREF_SHOW_LINE_NUMBERS = "prototype_editor_show_line_numbers";
    private static final String PREF_LT_AUTO = "prototype_editor_languagetool_auto";
    private static final String PREF_SIDEBAR_EXPANDED = "prototype_editor_sidebar_expanded";
    private static final String PREF_SIDEBAR_DIVIDER = "prototype_editor_sidebar_divider";

    private final Window owner;
    private final MainController mainController;
    private final Preferences preferences = Preferences.userNodeForPackage(ManuskriptEditorTestWindow.class);
    private CustomStage stage;
    private ManuskriptTextEditor editor;
    private Label statusLabel;
    private Label lblSelectionCount;
    private Label lblLanguageToolStatus;
    private ManuskriptTextEditor.MarkedArea searchMarks;
    private ManuskriptTextEditor.MarkedArea searchCurrentMark;
    private TextField searchField;
    private TextField replaceField;
    private CheckBox regexCheckbox;
    private List<ManuskriptTextEditor.SearchMatch> cachedSearchMatches = List.of();
    private String lastSearchCacheKey = null;
    private int currentSearchMatchIndex = -1;
    private boolean editingShortcutsInstalled;
    private LanguageToolDictionary languageToolDictionary;
    private LanguageToolService languageToolService;
    private Timeline languageToolCheckTimeline;
    private boolean languageToolAutoEnabled;
    private long languageToolCheckGeneration;
    private boolean languageToolHasBeenChecked;
    private String originalContent = "";
    private ScheduledExecutorService statusClearExecutor;
    private ScheduledFuture<?> statusClearFuture;
    private final Object statusLock = new Object();
    private static final String STATUS_STYLE_READY =
            "-fx-text-fill: #28a745; -fx-font-weight: normal; -fx-background-color: #d4edda; -fx-padding: 2 6 2 6; -fx-background-radius: 3;";
    private static final String STATUS_STYLE_WARNING =
            "-fx-text-fill: #ff6b35; -fx-font-weight: bold; -fx-background-color: #fff3cd; -fx-padding: 2 6 2 6; -fx-background-radius: 3;";
    private int themeIndex;
    private File loadedChapterFile;
    private File loadedDocxFile;
    private File loadedProjectDirectory;
    private String loadedChapterName;
    private SplitPane mainSplitPane;
    private VBox sidebarContainer;
    private HBox sidebarHeader;
    private Label sidebarTitleLabel;
    private Label chapterListPlaceholder;
    private Button btnToggleSidebar;
    private ListView<DocxFile> chapterListView;
    private ChapterSidebarTheme chapterSidebarTheme;
    private boolean sidebarExpanded = true;
    private ListChangeListener<DocxFile> chapterListChangeListener;
    private boolean sidebarDividerListenerAttached;
    private boolean dirty;
    private boolean suppressDirty;
    private boolean quoteErrorsDialogShown;
    private ChapterAgentSupport chapterAgentSupport;
    private ChapterOnlineLektoratHelper lektoratHelper;
    private VBox lektoratPanelContainer;
    private ChapterLektoratPanel lektoratPanel;
    private SceneOutlineWindow sceneOutlineWindow;
    private boolean onlineLektoratMode;
    private javafx.stage.Stage featuresSetupStage;
    private ChapterTextAnalysisWindow textAnalysisWindow;
    private ChapterMacroWindow macroWindow;

    public ManuskriptEditorTestWindow(Window owner) {
        this(owner, null);
    }

    public ManuskriptEditorTestWindow(Window owner, MainController mainController) {
        this.owner = owner;
        this.mainController = mainController;
        createUI();
    }

    public void show() {
        stage.show();
        Platform.runLater(() -> {
            stage.toFront();
            stage.requestFocus();
            editor.requestInputFocus();
            updateChapterList();
            scheduleInitialLanguageToolCheck();
        });
    }

    private void createUI() {
        stage = StageManager.createStage("Prototyp: Eigener Editor");
        stage.setWindowPersistenceType("prototype_editor");
        if (owner instanceof javafx.stage.Stage ownerStage) {
            stage.initOwner(ownerStage);
        }
        stage.setMinWidth(900);
        stage.setMinHeight(650);

        editor = new ManuskriptTextEditor();
        languageToolDictionary = new LanguageToolDictionary();
        languageToolService = new LanguageToolService();
        languageToolAutoEnabled = preferences.getBoolean(PREF_LT_AUTO, false);
        editor.setLanguageToolDictionary(languageToolDictionary);
        editor.setOnLanguageToolMatchesChanged(() -> Platform.runLater(this::updateLanguageToolStatus));
        editor.setOnSelectionChanged(() -> Platform.runLater(this::updateSelectionCount));
        editor.setFontFamilyForAll(preferences.get(PREF_FONT_FAMILY, "Segoe UI"));
        editor.setFontSizeForAll(preferences.getDouble(PREF_FONT_SIZE, 16.0));
        editor.setQuoteStyleIndex(Preferences.userNodeForPackage(EditorWindow.class).getInt("quoteStyle", 0));
        editor.setText(sampleText());
        captureOriginalContent();
        editor.setOnTextChanged(text -> {
            if (!suppressDirty) {
                updateDirtyFromContent(text);
            }
            scheduleLanguageToolCheckDebounced();
        });
        editor.registerDefaultFormatAutoRules();
        boolean hideMarkup = preferences.getBoolean(PREF_HIDE_MARKUP, true);
        editor.setRenderMarkupHidden(hideMarkup);
        editor.setShowLineNumbers(preferences.getBoolean(PREF_SHOW_LINE_NUMBERS, true));
        themeIndex = Preferences.userNodeForPackage(MainController.class).getInt("main_window_theme", 0);
        editor.applyTheme(themeIndex);

        BorderPane root = new BorderPane();
        initializeStatusLabel();
        initializeSelectionLabel();
        root.setTop(createToolbar());
        root.setCenter(createEditorWithSidebar());
        root.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getTarget() == editor || editor.getBoundsInParent().contains(event.getX(), event.getY())) {
                Platform.runLater(editor::requestInputFocus);
            }
        });

        Scene scene = new Scene(root, 1100, 780);
        String cssPath = ResourceManager.getCssResource("css/manuskript.css");
        if (cssPath != null) {
            scene.getStylesheets().add(cssPath);
        }
        stage.setSceneWithTitleBar(scene);
        stage.setFullTheme(themeIndex);
        applyThemeToNode(root, themeIndex);
        stage.setOnShown(event -> {
            applyChapterSidebarTheme();
            stage.requestFocus();
            editor.requestInputFocus();
            ensureEditingShortcutsInstalled();
            scheduleInitialLanguageToolCheck();
        });

        PreferencesManager.MultiMonitorValidator.applyWindowProperties(
                stage,
                PreferencesManager.MultiMonitorValidator.loadAndValidateWindowProperties(
                        preferences, "prototype_editor_window", 1100.0, 780.0));
        setupWindowPersistence();
        stage.addEventHandler(WindowEvent.WINDOW_CLOSE_REQUEST, this::handleCloseRequest);

        updateStatus("Bereit");
        updateSelectionCount();

        initializeImageDirectories();
        initializeSidebar();
        applyChapterSidebarTheme();
        loadSidebarState();
        attachChapterListListener();
        setupChapterEditorFeatures();
    }

    private void setupChapterEditorFeatures() {
        lektoratHelper = new ChapterOnlineLektoratHelper(this, lektoratPanel);
        sceneOutlineWindow = new SceneOutlineWindow();
        if (!onlineLektoratMode) {
            chapterAgentSupport = new ChapterAgentSupport(this, mainSplitPane);
            chapterAgentSupport.setSceneOutlineWindow(sceneOutlineWindow);
            chapterAgentSupport.setupIfEnabled();
        }
        textAnalysisWindow = new ChapterTextAnalysisWindow(createTextAnalysisHost());
        macroWindow = new ChapterMacroWindow(createMacroHost());
    }

    private ChapterTextAnalysisWindow.Host createTextAnalysisHost() {
        return new ChapterTextAnalysisWindow.Host() {
            @Override
            public String getChapterText() {
                return editor.getText();
            }

            @Override
            public void applyAnalysisResult(TextAnalysisEngine.AnalysisResult result) {
                editor.applyTextAnalysisSpans(result.spans());
            }

            @Override
            public void clearAnalysisMarks() {
                editor.clearTextAnalysisMarks();
            }

            @Override
            public void revealAnalysisRange(int start, int end) {
                editor.selectRange(start, end);
                editor.revealMatchAt(start, end);
            }

            @Override
            public void updateStatus(String message) {
                ManuskriptEditorTestWindow.this.updateStatus(message);
            }

            @Override
            public void updateStatusError(String message) {
                ManuskriptEditorTestWindow.this.updateStatus(message, true);
            }

            @Override
            public Window getOwnerWindow() {
                return stage;
            }

            @Override
            public int getThemeIndex() {
                return themeIndex;
            }

            @Override
            public void applyThemeToNode(Node node, int themeIndex) {
                ManuskriptEditorTestWindow.this.applyThemeToNode(node, themeIndex);
            }
        };
    }

    public ManuskriptTextEditor getTextEditor() {
        return editor;
    }

    public File getLoadedChapterFile() {
        return loadedChapterFile;
    }

    private Node createEditorWithSidebar() {
        btnToggleSidebar = new Button("◀");
        btnToggleSidebar.setMaxWidth(30);
        btnToggleSidebar.setMinWidth(30);
        btnToggleSidebar.getStyleClass().add("sidebar-toggle-button");
        btnToggleSidebar.setTooltip(new Tooltip("Kapitel-Seitenleiste ein-/ausblenden"));
        btnToggleSidebar.setOnAction(e -> toggleSidebar());

        sidebarTitleLabel = new Label("Kapitel");
        sidebarTitleLabel.getStyleClass().add("sidebar-title");
        sidebarHeader = new HBox(sidebarTitleLabel);
        sidebarHeader.setAlignment(Pos.CENTER_LEFT);
        sidebarHeader.getStyleClass().add("sidebar-header");
        sidebarHeader.setPadding(new Insets(5, 10, 5, 10));

        chapterListView = new ListView<>();
        chapterListView.getStyleClass().add("chapter-list-view");
        chapterListPlaceholder = new Label("Keine Kapitel in der Auswahl");
        chapterListView.setPlaceholder(chapterListPlaceholder);
        VBox.setVgrow(chapterListView, Priority.ALWAYS);

        sidebarContainer = new VBox(sidebarHeader, chapterListView);
        sidebarContainer.setMinWidth(200);
        sidebarContainer.setPrefWidth(250);
        sidebarContainer.setMaxWidth(300);

        lektoratPanelContainer = new VBox();
        lektoratPanelContainer.setMinWidth(120);
        lektoratPanelContainer.setPrefWidth(320);
        lektoratPanelContainer.setPadding(new Insets(10));

        mainSplitPane = new SplitPane(sidebarContainer, editor);
        mainSplitPane.setDividerPositions(0.18);
        lektoratPanel = new ChapterLektoratPanel(
                lektoratPanelContainer, mainSplitPane, () -> themeIndex, this::applyThemeToNode);
        HBox.setHgrow(mainSplitPane, Priority.ALWAYS);

        HBox editorRow = new HBox(0, btnToggleSidebar, mainSplitPane);
        HBox.setHgrow(editorRow, Priority.ALWAYS);
        return editorRow;
    }

    private void initializeSidebar() {
        if (chapterListView == null) {
            return;
        }
        updateChapterList();
        chapterListView.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(DocxFile docxFile, boolean empty) {
                super.updateItem(docxFile, empty);
                styleChapterListCell(this, docxFile, empty);
            }
        });
        chapterListView.setOnMouseClicked(e -> {
            if (e.getClickCount() != 2) {
                return;
            }
            DocxFile selected = chapterListView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                navigateToChapter(selected);
            }
        });
    }

    private void attachChapterListListener() {
        if (mainController == null || chapterListView == null) {
            return;
        }
        ObservableList<DocxFile> chapters = mainController.getSelectedDocxFilesAsDocxFiles();
        if (chapterListChangeListener != null) {
            chapters.removeListener(chapterListChangeListener);
        }
        chapterListChangeListener = change -> Platform.runLater(() -> {
            updateChapterList();
            refreshChapterListAppearance();
        });
        chapters.addListener(chapterListChangeListener);
    }

    private void updateChapterList() {
        if (chapterListView == null) {
            return;
        }
        if (mainController == null) {
            if (!chapterListView.getItems().isEmpty()) {
                chapterListView.getItems().clear();
            }
            return;
        }
        ObservableList<DocxFile> selected = mainController.getSelectedDocxFilesAsDocxFiles();
        if (chapterListView.getItems() != selected) {
            chapterListView.setItems(selected);
        }
        selectLoadedChapterInList();
    }

    private void selectLoadedChapterInList() {
        if (chapterListView == null || loadedDocxFile == null) {
            return;
        }
        ObservableList<DocxFile> items = chapterListView.getItems();
        if (items == null) {
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getFile().equals(loadedDocxFile)) {
                chapterListView.getSelectionModel().select(i);
                return;
            }
        }
    }

    /** Zellen neu zeichnen (Markierungen), ohne die ObservableList zu verändern. */
    private void refreshChapterListAppearance() {
        if (chapterListView != null) {
            chapterListView.refresh();
        }
    }

    private void toggleSidebar() {
        if (mainSplitPane == null || sidebarContainer == null || btnToggleSidebar == null) {
            return;
        }
        sidebarExpanded = !sidebarExpanded;
        if (sidebarExpanded) {
            sidebarContainer.setVisible(true);
            sidebarContainer.setManaged(true);
            btnToggleSidebar.setText("◀");
            Platform.runLater(() -> {
                double saved = preferences.getDouble(PREF_SIDEBAR_DIVIDER, 0.18);
                mainSplitPane.setDividerPositions(saved);
            });
        } else {
            sidebarContainer.setVisible(false);
            sidebarContainer.setManaged(false);
            btnToggleSidebar.setText("▶");
            Platform.runLater(() -> mainSplitPane.setDividerPositions(0.0));
        }
        saveSidebarState();
    }

    private void loadSidebarState() {
        if (mainSplitPane == null || sidebarContainer == null || btnToggleSidebar == null) {
            return;
        }
        sidebarExpanded = preferences.getBoolean(PREF_SIDEBAR_EXPANDED, true);
        double divider = preferences.getDouble(PREF_SIDEBAR_DIVIDER, 0.18);
        Platform.runLater(() -> {
            if (sidebarExpanded) {
                sidebarContainer.setVisible(true);
                sidebarContainer.setManaged(true);
                btnToggleSidebar.setText("◀");
                mainSplitPane.setDividerPositions(divider);
            } else {
                sidebarContainer.setVisible(false);
                sidebarContainer.setManaged(false);
                btnToggleSidebar.setText("▶");
                mainSplitPane.setDividerPositions(0.0);
            }
            if (!sidebarDividerListenerAttached && !mainSplitPane.getDividers().isEmpty()) {
                sidebarDividerListenerAttached = true;
                mainSplitPane.getDividers().getFirst().positionProperty().addListener((obs, oldPos, newPos) -> {
                    if (sidebarExpanded && newPos.doubleValue() > 0.01) {
                        preferences.putDouble(PREF_SIDEBAR_DIVIDER, newPos.doubleValue());
                    }
                });
            }
        });
    }

    private void saveSidebarState() {
        preferences.putBoolean(PREF_SIDEBAR_EXPANDED, sidebarExpanded);
        if (mainSplitPane != null && sidebarExpanded && !mainSplitPane.getDividers().isEmpty()) {
            preferences.putDouble(PREF_SIDEBAR_DIVIDER, mainSplitPane.getDividers().getFirst().getPosition());
        }
    }

    private void navigateToChapter(DocxFile docxFile) {
        if (docxFile == null || mainController == null) {
            return;
        }
        if (loadedDocxFile != null && docxFile.getFile().equals(loadedDocxFile)) {
            return;
        }
        refreshDirtyState();
        if (dirty && !confirmLoseUnsavedChanges("Das gewählte Kapitel laden?")) {
            selectLoadedChapterInList();
            refreshChapterListAppearance();
            return;
        }
        MainController.PrototypeChapterContent chapter = mainController.loadChapterMarkdownForPrototype(docxFile);
        if (chapter == null) {
            updateStatus("Keine MD-Datei für „" + docxFile.getFileName() + "“ gefunden", true);
            return;
        }
        applyLoadedChapter(chapter, chapter.docxFile());
    }

    private void initializeImageDirectories() {
        File projectDirectory = resolveProjectDirectory();
        if (projectDirectory != null) {
            loadedProjectDirectory = projectDirectory;
            editor.setImageDirectories(resolveMdDirectory(), projectDirectory);
        }
    }

    private VBox createToolbar() {
        ComboBox<String> fontFamily = new ComboBox<>();
        fontFamily.getItems().addAll(Font.getFamilies());
        fontFamily.setValue(preferences.get(PREF_FONT_FAMILY, "Segoe UI"));
        fontFamily.setMinWidth(180);
        fontFamily.setPrefWidth(180);
        fontFamily.setMaxWidth(180);
        fontFamily.valueProperty().addListener((obs, oldValue, newValue) -> {
            editor.setFontFamilyForAll(newValue);
            preferences.put(PREF_FONT_FAMILY, newValue);
        });

        ComboBox<Double> fontSize = new ComboBox<>();
        fontSize.getItems().addAll(10.0, 11.0, 12.0, 14.0, 16.0, 18.0, 20.0, 24.0, 28.0, 32.0);
        fontSize.setValue(preferences.getDouble(PREF_FONT_SIZE, 16.0));
        fontSize.setPrefWidth(80);
        fontSize.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null) {
                editor.setFontSizeForAll(newValue);
                preferences.putDouble(PREF_FONT_SIZE, newValue);
            }
        });

        Button undo = new Button("Rückgängig");
        undo.setTooltip(new Tooltip("Rückgängig (Strg+Z)"));
        undo.setOnAction(e -> {
            editor.undo();
            editor.requestInputFocus();
        });

        Button redo = new Button("Wiederholen");
        redo.setTooltip(new Tooltip("Wiederholen (Strg+Y)"));
        redo.setOnAction(e -> {
            editor.redo();
            editor.requestInputFocus();
        });

        Button fontLarger = toolbarButton("A+", "Schriftgröße erhöhen", () -> {
            fontSize.setValue(Math.min(96, fontSize.getValue() + 1));
            editor.requestInputFocus();
        });
        Button fontSmaller = toolbarButton("A-", "Schriftgröße verringern", () -> {
            fontSize.setValue(Math.max(6, fontSize.getValue() - 1));
            editor.requestInputFocus();
        });

        Button bold = toolbarButton("B", "Fett (Strg+B)", () -> {
            editor.toggleBold();
            editor.requestInputFocus();
        });
        Button italic = toolbarButton("I", "Kursiv (Strg+I)", () -> {
            editor.toggleItalic();
            editor.requestInputFocus();
        });
        Button underline = toolbarButton("U", "Unterstrichen (Strg+U)", () -> {
            editor.toggleUnderline();
            editor.requestInputFocus();
        });
        Button strikethrough = toolbarButton("S", "Durchgestrichen (~~text~~)", () -> {
            editor.toggleStrikethrough();
            editor.requestInputFocus();
        });
        Button mark = toolbarButton("Mark", "Hervorheben (<mark>)", () -> {
            editor.toggleMark();
            editor.requestInputFocus();
        });
        Button center = toolbarButton("◉", "Text zentrieren (<center>)", () -> {
            editor.toggleCenter();
            editor.requestInputFocus();
        });
        Button blockquote = toolbarButton(">", "Zitat (> Zeile)", () -> {
            editor.toggleBlockquote();
            editor.requestInputFocus();
        });
        Button big = toolbarButton("Groß", "Größere Schrift (<big>)", () -> {
            editor.toggleBig();
            editor.requestInputFocus();
        });
        Button small = toolbarButton("Klein", "Kleinere Schrift (<small>)", () -> {
            editor.toggleSmall();
            editor.requestInputFocus();
        });
        Button superscript = toolbarButton("x²", "Hochgestellt (<sup>)", () -> {
            editor.toggleSuperscript();
            editor.requestInputFocus();
        });
        Button subscript = toolbarButton("x₂", "Tiefgestellt (<sub>)", () -> {
            editor.toggleSubscript();
            editor.requestInputFocus();
        });

        MenuButton colorMenu = new MenuButton("Farbe");
        colorMenu.setTooltip(new Tooltip("Textfarbe"));
        for (String[] entry : new String[][]{
                {"Rot", "red"}, {"Blau", "blue"}, {"Grün", "green"},
                {"Gelb", "yellow"}, {"Lila", "purple"}, {"Orange", "orange"}, {"Grau", "gray"}
        }) {
            String tag = entry[1];
            MenuItem item = new MenuItem(entry[0]);
            item.setOnAction(e -> {
                editor.wrapTextColor(tag);
                editor.requestInputFocus();
            });
            colorMenu.getItems().add(item);
        }

        Button lineBreak = new Button("↵");
        lineBreak.setTooltip(new Tooltip("Zeilenumbruch (<br>)"));
        lineBreak.setOnAction(e -> {
            editor.insertLineBreak();
            editor.requestInputFocus();
        });

        Button horizontalRule = new Button("━");
        horizontalRule.setTooltip(new Tooltip("Horizontale Linie (---)"));
        horizontalRule.setOnAction(e -> {
            editor.insertHorizontalRule();
            editor.requestInputFocus();
        });

        searchField = new TextField();
        searchField.setPromptText("Suchen");
        searchField.setPrefWidth(160);
        searchField.setTooltip(new Tooltip("Suchen (Strg+F, Enter = weiter, F3 = weiter)"));

        replaceField = new TextField();
        replaceField.setPromptText("Ersetzen");
        replaceField.setPrefWidth(140);
        regexCheckbox = new CheckBox("Regex");
        setupSearchFieldShortcuts();

        Button find = new Button("Weiter");
        find.setTooltip(new Tooltip("Nächster Treffer (Enter / F3)"));
        find.setOnAction(e -> findNextMatch());

        Button findPrevious = new Button("Zurück");
        findPrevious.setTooltip(new Tooltip("Vorheriger Treffer (Umschalt+Enter / Umschalt+F3)"));
        findPrevious.setOnAction(e -> findPreviousMatch());

        Button replaceOne = new Button("Ersetzen");
        replaceOne.setTooltip(new Tooltip("Aktuellen Treffer ersetzen und weiter (Strg+H)"));
        replaceOne.setOnAction(e -> replaceNextMatch());

        Button replaceAll = new Button("Alle ersetzen");
        replaceAll.setOnAction(e -> {
            int count = editor.replaceAll(searchField.getText(), replaceField.getText(), regexCheckbox.isSelected(), true);
            invalidateSearchCache();
            updateStatus(count + " Treffer ersetzt");
        });

        CheckBox hideMarkup = new CheckBox("Markup ausblenden");
        hideMarkup.setSelected(preferences.getBoolean(PREF_HIDE_MARKUP, true));
        hideMarkup.setTooltip(new Tooltip("Markup ausblenden: Syntax verstecken, WYSIWYG inkl. Bild-Vorschau. Aus = Markdown-Quelltext"));
        hideMarkup.selectedProperty().addListener((obs, oldValue, newValue) -> {
            editor.setRenderMarkupHidden(newValue);
            preferences.putBoolean(PREF_HIDE_MARKUP, newValue);
        });

        CheckBox showLineNumbers = new CheckBox("Zeilennummern");
        showLineNumbers.setSelected(preferences.getBoolean(PREF_SHOW_LINE_NUMBERS, true));
        showLineNumbers.setTooltip(new Tooltip("Zeilennummern-Spalte links ein- oder ausblenden"));
        showLineNumbers.selectedProperty().addListener((obs, oldValue, newValue) -> {
            editor.setShowLineNumbers(newValue);
            preferences.putBoolean(PREF_SHOW_LINE_NUMBERS, newValue);
        });

        ComboBox<String> quoteStyle = new ComboBox<>();
        for (String[] option : QuotationMarkSupport.STYLE_OPTIONS) {
            quoteStyle.getItems().add(option[0]);
        }
        int quoteStyleIndex = Preferences.userNodeForPackage(EditorWindow.class).getInt("quoteStyle", 0);
        if (quoteStyleIndex < 0 || quoteStyleIndex >= QuotationMarkSupport.STYLE_COUNT) {
            quoteStyleIndex = 0;
        }
        quoteStyle.setValue(QuotationMarkSupport.styleLabel(quoteStyleIndex));
        quoteStyle.setMinWidth(140);
        quoteStyle.setPrefWidth(180);
        quoteStyle.setMaxWidth(280);
        quoteStyle.setTooltip(new Tooltip("Stil für Anführungszeichen beim Tippen von \" und '"));
        quoteStyle.setOnAction(e -> {
            int selectedIndex = quoteStyle.getSelectionModel().getSelectedIndex();
            if (selectedIndex >= 0) {
                editor.setQuoteStyleIndex(selectedIndex);
                Preferences.userNodeForPackage(EditorWindow.class).putInt("quoteStyle", selectedIndex);
                convertAllQuotationMarksInText(selectedIndex);
            }
        });

        Button languageTool = new Button("LanguageTool");
        languageTool.setTooltip(new Tooltip("LanguageTool jetzt prüfen"));
        languageTool.setOnAction(e -> runLanguageToolCheck(true));

        CheckBox languageToolAuto = new CheckBox("LT automatisch");
        languageToolAuto.setSelected(languageToolAutoEnabled);
        languageToolAuto.setTooltip(new Tooltip("LanguageTool nach Textänderungen automatisch prüfen (500 ms Verzögerung)"));
        languageToolAuto.selectedProperty().addListener((obs, oldValue, newValue) -> {
            languageToolAutoEnabled = newValue;
            preferences.putBoolean(PREF_LT_AUTO, newValue);
            if (newValue) {
                scheduleLanguageToolCheckDebounced();
            } else {
                cancelLanguageToolChecks();
                languageToolHasBeenChecked = false;
                editor.clearLanguageToolMatches();
                updateLanguageToolStatus();
            }
        });

        lblLanguageToolStatus = new Label("");
        lblLanguageToolStatus.setTooltip(new Tooltip("LanguageTool Status"));
        lblLanguageToolStatus.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");
        updateLanguageToolStatus();

        Button loadSelectedChapter = new Button("Kapitel laden");
        loadSelectedChapter.setOnAction(e -> loadSelectedChapterFromMainWindow());

        Button saveChapter = new Button("Speichern");
        saveChapter.setTooltip(new Tooltip("Speichern (Strg+S)"));
        saveChapter.setOnAction(e -> saveLoadedChapter());

        Button insertImage = new Button("Bild einfügen");
        insertImage.setOnAction(e -> insertImage());

        Button editImage = new Button("Bild bearbeiten");
        editImage.setTooltip(new Tooltip("Breite und Beschriftung des Bildes am Cursor ändern"));
        editImage.setOnAction(e -> editImageAtCaret());

        Button deleteImage = new Button("Bild löschen");
        deleteImage.setOnAction(e -> {
            if (editor.deleteImageBlockAtCaret()) {
                updateStatus("Bild entfernt");
            } else {
                updateStatus("Kein Bild am Cursor – Bild anklicken oder Cursor im Bild platzieren", true);
            }
        });

        HBox statusRow = new HBox(8);
        Region statusSpacer = new Region();
        HBox.setHgrow(statusSpacer, Priority.ALWAYS);
        statusRow.getChildren().addAll(statusSpacer, saveChapter, lblSelectionCount, statusLabel);
        statusRow.setAlignment(Pos.CENTER_RIGHT);

        HBox fontRow = new HBox(6,
                undo, redo,
                toolbarSeparator(),
                fontSmaller, fontLarger,
                new Label("Font:"), fontFamily,
                new Label("Größe:"), fontSize,
                hideMarkup, showLineNumbers);
        fontRow.setAlignment(Pos.CENTER_LEFT);

        FlowPane formatPane = new FlowPane(6, 4);
        formatPane.setAlignment(Pos.CENTER_LEFT);
        formatPane.getChildren().addAll(
                bold, italic, underline, strikethrough, mark, blockquote, center,
                superscript, subscript,
                big, small, colorMenu, lineBreak, horizontalRule);

        searchField.setMinWidth(80);
        searchField.setPrefWidth(140);
        searchField.setMaxWidth(Double.MAX_VALUE);
        replaceField.setMinWidth(80);
        replaceField.setPrefWidth(120);
        replaceField.setMaxWidth(Double.MAX_VALUE);
        HBox searchInputs = new HBox(6, searchField, replaceField);
        HBox.setHgrow(searchField, Priority.ALWAYS);
        HBox.setHgrow(replaceField, Priority.ALWAYS);
        searchInputs.setAlignment(Pos.CENTER_LEFT);

        FlowPane searchActions = new FlowPane(6, 4, regexCheckbox, find, findPrevious, replaceOne, replaceAll);
        searchActions.setAlignment(Pos.CENTER_LEFT);

        VBox searchBlock = new VBox(4, searchInputs, searchActions);

        FlowPane toolsPane = new FlowPane(6, 4);
        toolsPane.setAlignment(Pos.CENTER_LEFT);
        Label quoteLabel = new Label("Anführungszeichen:");
        Button sceneOutline = toolbarButton("Outline", "Szenen-Outline für dieses Kapitel", this::toggleSceneOutlineWindow);
        Button textAnalysis = toolbarButton("Analyse", "Textanalyse-Fenster ein-/ausblenden", this::toggleTextAnalysisWindow);
        Button onlineLektorat = toolbarButton("Lektorat", "Online-Lektorat starten", () -> startOnlineLektorat(false));
        Button macrosBtn = toolbarButton("Makros", "Makro-Verwaltung ein-/ausblenden", this::toggleMacroWindow);
        Button copySudowrite = toolbarButton("Sudowrite", "Für Sudowrite kopieren (Zwischenablage)",
                this::copyForSudowrite);

        toolsPane.getChildren().addAll(
                quoteLabel, quoteStyle,
                languageTool, languageToolAuto, lblLanguageToolStatus,
                sceneOutline, textAnalysis, onlineLektorat, macrosBtn, copySudowrite,
                insertImage, editImage, deleteImage, loadSelectedChapter);

        VBox toolbar = new VBox(8, statusRow, fontRow, formatPane, searchBlock, toolsPane);
        toolbar.setPadding(new Insets(8));
        var wrapLength = Bindings.max(220, toolbar.widthProperty().subtract(16));
        formatPane.prefWrapLengthProperty().bind(wrapLength);
        searchActions.prefWrapLengthProperty().bind(wrapLength);
        toolsPane.prefWrapLengthProperty().bind(wrapLength);
        applyThemeToNode(toolbar, themeIndex);
        return toolbar;
    }

    private static Button toolbarButton(String label, String tooltip, Runnable action) {
        Button button = new Button(label);
        button.setTooltip(new Tooltip(tooltip));
        button.setOnAction(e -> action.run());
        return button;
    }

    private static Separator toolbarSeparator() {
        Separator separator = new Separator(Orientation.VERTICAL);
        separator.setPrefHeight(26);
        return separator;
    }

    public void openChapter(MainController.PrototypeChapterContent chapter, File docxFile) {
        if (chapter == null) {
            return;
        }
        applyLoadedChapter(chapter, docxFile);
        if (stage != null && !stage.isShowing()) {
            show();
        }
    }

    public void tryLoadSelectedChapter() {
        Platform.runLater(() -> {
            loadSelectedChapterFromMainWindow();
            scheduleInitialLanguageToolCheck();
        });
    }

    private void focusReplaceField() {
        if (searchField.getText() == null || searchField.getText().isBlank()) {
            if (editor.hasTextSelection()) {
                searchField.setText(editor.getSelectedText());
            }
        }
        searchField.requestFocus();
        replaceField.requestFocus();
        replaceField.selectAll();
    }

    private void replaceNextMatch() {
        String query = searchField.getText();
        if (query == null || query.isBlank()) {
            focusSearchField();
            updateStatus("Suchbegriff eingeben", true);
            return;
        }
        refreshSearchCacheIfNeeded();
        if (cachedSearchMatches.isEmpty()) {
            updateStatus("Keine Treffer", true);
            return;
        }
        if (currentSearchMatchIndex < 0) {
            currentSearchMatchIndex = 0;
        }
        int index = Math.min(currentSearchMatchIndex, cachedSearchMatches.size() - 1);
        String replacement = replaceField.getText() == null ? "" : replaceField.getText();
        editor.replaceOne(query, replacement, regexCheckbox.isSelected(), true, index);
        lastSearchCacheKey = null;
        currentSearchMatchIndex = -1;
        refreshSearchCacheIfNeeded();
        if (cachedSearchMatches.isEmpty()) {
            clearSearchMarks();
            updateStatus("Ersetzt – keine weiteren Treffer");
            return;
        }
        int nextIndex = Math.min(index, cachedSearchMatches.size() - 1);
        goToSearchMatch(nextIndex);
        updateStatus("Ersetzt, Treffer " + (nextIndex + 1) + " von " + cachedSearchMatches.size());
    }

    private void editImageAtCaret() {
        ManuskriptTextEditor.ImageBlockInfo info = editor.getImageBlockAtCaret();
        if (info == null) {
            updateStatus("Kein Bild am Cursor – Bild anklicken oder Cursor im Bild platzieren", true);
            return;
        }

        CustomAlert alert = new CustomAlert(CustomAlert.AlertType.INFORMATION);
        alert.setTitle("Bild bearbeiten");
        alert.setHeaderText("Bild bearbeiten");
        alert.initOwner(stage);

        VBox contentBox = new VBox(10);
        contentBox.setPadding(new Insets(10));

        Label fileLabel = new Label("Datei:");
        Label fileValue = new Label(info.imagePath());
        fileValue.setWrapText(true);

        Label textLabel = new Label("Beschriftung:");
        TextField textField = new TextField();
        textField.setPromptText("Optionale Bildbeschriftung");
        if (info.caption() != null) {
            textField.setText(info.caption());
        }

        Label sizeLabel = new Label("Breite (%):");
        Spinner<Integer> widthSpinner = new Spinner<>();
        widthSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(10, 100, info.widthPercent(), 5));
        widthSpinner.setEditable(true);
        widthSpinner.setPrefWidth(80);

        contentBox.getChildren().addAll(fileLabel, fileValue, textLabel, textField, sizeLabel, widthSpinner);
        alert.setCustomContent(contentBox);
        alert.applyTheme(themeIndex);
        alert.setButtonTypes(new ButtonType("Übernehmen"), new ButtonType("Abbrechen"));

        Optional<ButtonType> result = alert.showAndWait(stage);
        if (result.isEmpty() || result.get().getButtonData().isCancelButton()) {
            return;
        }

        int widthPercent = widthSpinner.getValue() == null ? info.widthPercent() : widthSpinner.getValue();
        if (editor.updateImageBlockAtCaret(textField.getText(), widthPercent)) {
            preferences.putInt(PREF_LAST_IMAGE_WIDTH, widthPercent);
            String caption = textField.getText();
            if (caption != null && !caption.isBlank()) {
                preferences.put(PREF_LAST_IMAGE_ALT, caption);
            }
            updateStatus("Bild aktualisiert");
        } else {
            updateStatusError("Bild konnte nicht aktualisiert werden");
        }
    }

    private void setupSearchFieldShortcuts() {
        searchField.setOnAction(event -> {
            editor.suppressEnterKey(4);
            findNextMatch();
        });
        searchField.addEventFilter(KeyEvent.KEY_TYPED, event -> {
            String character = event.getCharacter();
            if (character != null && !character.isEmpty() && character.charAt(0) < 32) {
                event.consume();
            }
        });
        searchField.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.isConsumed()) {
                return;
            }
            if (ManuskriptTextEditor.isEditingShortcutKey(event) && event.getCode() == KeyCode.F) {
                searchField.selectAll();
                event.consume();
                return;
            }
            if (event.getCode() == KeyCode.ESCAPE) {
                editor.requestInputFocus();
                event.consume();
                return;
            }
            if (event.getCode() == KeyCode.ENTER && event.isShiftDown()
                    && !ManuskriptTextEditor.isEditingShortcutKey(event)) {
                editor.suppressEnterKey(4);
                event.consume();
                findPreviousMatch();
            }
        });
        searchField.textProperty().addListener((obs, oldValue, newValue) -> {
            invalidateSearchCache();
            if (newValue == null || newValue.isBlank()) {
                clearSearchMarks();
            }
        });
        regexCheckbox.selectedProperty().addListener((obs, oldValue, newValue) -> {
            invalidateSearchCache();
            if (searchField.getText() == null || searchField.getText().isBlank()) {
                clearSearchMarks();
            }
        });
    }

    private void focusSearchField() {
        if (editor.hasTextSelection()) {
            searchField.setText(editor.getSelectedText());
        }
        searchField.requestFocus();
        searchField.selectAll();
    }

    private void invalidateSearchCache() {
        lastSearchCacheKey = null;
        currentSearchMatchIndex = -1;
    }

    private String searchCacheKey(String query, boolean regex) {
        return query + "\0" + regex;
    }

    private void refreshSearchCacheIfNeeded() {
        String query = searchField.getText();
        if (query == null) {
            query = "";
        }
        boolean regex = regexCheckbox.isSelected();
        String key = searchCacheKey(query, regex);
        if (key.equals(lastSearchCacheKey)) {
            return;
        }
        lastSearchCacheKey = key;
        currentSearchMatchIndex = -1;
        if (query.isBlank()) {
            clearSearchMarks();
            cachedSearchMatches = List.of();
            return;
        }
        cachedSearchMatches = editor.searchAll(query, regex, true);
        markSearchResults(query, regex);
    }

    private void markSearchResults(String query, boolean regex) {
        clearSearchMarks();
        if (cachedSearchMatches.isEmpty()) {
            return;
        }
        searchMarks = editor.newMarkedArea();
        searchMarks.markType(ManuskriptTextEditor.MarkedArea.Type.HIGHLIGHT)
                .markColor(ManuskriptTextEditor.MarkedArea.SEARCH_MATCH_COLOR);
        searchMarks.searchAll(query, regex);
    }

    private void markCurrentSearchMatch(int index) {
        if (searchCurrentMark != null) {
            searchCurrentMark.clear();
            searchCurrentMark = null;
        }
        if (index < 0 || index >= cachedSearchMatches.size()) {
            return;
        }
        ManuskriptTextEditor.SearchMatch match = cachedSearchMatches.get(index);
        searchCurrentMark = editor.newMarkedArea();
        searchCurrentMark.markType(ManuskriptTextEditor.MarkedArea.Type.HIGHLIGHT)
                .markColor(ManuskriptTextEditor.MarkedArea.SEARCH_CURRENT_MATCH_COLOR)
                .addRange(match.start(), match.end());
    }

    private void clearSearchMarks() {
        if (searchCurrentMark != null) {
            searchCurrentMark.clear();
            searchCurrentMark = null;
        }
        if (searchMarks != null) {
            searchMarks.clear();
            searchMarks = null;
        }
    }

    private void findNextMatch() {
        String query = searchField.getText();
        if (query == null || query.isBlank()) {
            focusSearchField();
            updateStatus("Suchbegriff eingeben", true);
            return;
        }
        refreshSearchCacheIfNeeded();
        if (cachedSearchMatches.isEmpty()) {
            clearSearchMarks();
            updateStatus("Keine Treffer", true);
            return;
        }
        int nextIndex = currentSearchMatchIndex < 0
                ? 0
                : (currentSearchMatchIndex + 1) % cachedSearchMatches.size();
        goToSearchMatch(nextIndex);
    }

    private void findPreviousMatch() {
        String query = searchField.getText();
        if (query == null || query.isBlank()) {
            focusSearchField();
            updateStatus("Suchbegriff eingeben", true);
            return;
        }
        refreshSearchCacheIfNeeded();
        if (cachedSearchMatches.isEmpty()) {
            clearSearchMarks();
            updateStatus("Keine Treffer", true);
            return;
        }
        int previousIndex = currentSearchMatchIndex <= 0
                ? cachedSearchMatches.size() - 1
                : currentSearchMatchIndex - 1;
        goToSearchMatch(previousIndex);
    }

    private void goToSearchMatch(int index) {
        ManuskriptTextEditor.SearchMatch match = cachedSearchMatches.get(index);
        currentSearchMatchIndex = index;
        markCurrentSearchMatch(index);
        editor.revealMatchAt(match.start(), match.end());
        updateStatus("Treffer " + (index + 1) + " von " + cachedSearchMatches.size());
    }

    private void ensureEditingShortcutsInstalled() {
        if (editingShortcutsInstalled) {
            return;
        }
        Scene scene = stage.getScene();
        if (scene == null) {
            return;
        }
        var accelerators = scene.getAccelerators();
        bindEditingAccelerator(accelerators, "Shortcut+Z", editor::undo, true);
        bindEditingAccelerator(accelerators, "Ctrl+Z", editor::undo, true);
        bindEditingAccelerator(accelerators, "Shortcut+Shift+Z", editor::redo, true);
        bindEditingAccelerator(accelerators, "Ctrl+Shift+Z", editor::redo, true);
        bindEditingAccelerator(accelerators, "Shortcut+Y", editor::redo, true);
        bindEditingAccelerator(accelerators, "Ctrl+Y", editor::redo, true);
        bindEditingAccelerator(accelerators, "Shortcut+B", editor::toggleBold, true);
        bindEditingAccelerator(accelerators, "Ctrl+B", editor::toggleBold, true);
        bindEditingAccelerator(accelerators, "Shortcut+I", editor::toggleItalic, true);
        bindEditingAccelerator(accelerators, "Ctrl+I", editor::toggleItalic, true);
        bindEditingAccelerator(accelerators, "Shortcut+U", editor::toggleUnderline, true);
        bindEditingAccelerator(accelerators, "Ctrl+U", editor::toggleUnderline, true);
        bindEditingAccelerator(accelerators, "Shortcut+F", this::focusSearchField, false);
        bindEditingAccelerator(accelerators, "Ctrl+F", this::focusSearchField, false);
        bindEditingAccelerator(accelerators, "Shortcut+S", this::saveLoadedChapter, true);
        bindEditingAccelerator(accelerators, "Ctrl+S", this::saveLoadedChapter, true);
        bindEditingAccelerator(accelerators, "Shortcut+H", this::replaceNextMatch, false);
        bindEditingAccelerator(accelerators, "Ctrl+H", this::replaceNextMatch, false);

        stage.addEventFilter(KeyEvent.KEY_PRESSED, this::handleSearchNavigationKey);
        editingShortcutsInstalled = true;
    }

    private void handleSearchNavigationKey(KeyEvent event) {
        if (event.getCode() == KeyCode.F3) {
            event.consume();
            if (event.isShiftDown()) {
                findPreviousMatch();
            } else {
                findNextMatch();
            }
        }
    }

    private void bindEditingAccelerator(javafx.collections.ObservableMap<KeyCombination, Runnable> accelerators,
                                        String combination, Runnable action, boolean refocusEditor) {
        accelerators.put(KeyCombination.valueOf(combination), () -> Platform.runLater(() -> {
            action.run();
            if (refocusEditor) {
                editor.requestInputFocus();
            }
        }));
    }

    private void initializeStatusLabel() {
        statusLabel = new Label("Bereit");
        statusLabel.getStyleClass().add("status-label");
        statusLabel.setMinWidth(280);
        statusLabel.setStyle(STATUS_STYLE_READY);
    }

    private void initializeSelectionLabel() {
        lblSelectionCount = new Label("Auswahl: 0 Zeichen, 0 Wörter");
        lblSelectionCount.getStyleClass().add("selection-label");
    }

    private void updateSelectionCount() {
        if (lblSelectionCount == null || editor == null) {
            return;
        }
        String selectedText = editor.getSelectedText();
        if (selectedText != null && !selectedText.isEmpty()) {
            lblSelectionCount.setText("Auswahl: " + selectedText.length() + " Zeichen, " + countWords(selectedText) + " Wörter");
        } else {
            lblSelectionCount.setText("Auswahl: 0 Zeichen, 0 Wörter");
        }
    }

    private static int countWords(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }

    private void scheduleInitialLanguageToolCheck() {
        if (!languageToolAutoEnabled) {
            return;
        }
        scheduleLanguageToolCheckDebounced();
    }

    private void scheduleLanguageToolCheckDebounced() {
        if (!languageToolAutoEnabled) {
            return;
        }
        if (languageToolCheckTimeline != null) {
            languageToolCheckTimeline.stop();
        }
        languageToolCheckTimeline = new Timeline(new KeyFrame(Duration.millis(500), event -> {
            languageToolCheckTimeline = null;
            runLanguageToolCheck(false);
        }));
        languageToolCheckTimeline.play();
    }

    private void cancelLanguageToolChecks() {
        languageToolCheckGeneration++;
        if (languageToolCheckTimeline != null) {
            languageToolCheckTimeline.stop();
            languageToolCheckTimeline = null;
        }
    }

    private void runLanguageToolCheck() {
        runLanguageToolCheck(true);
    }

    private void runLanguageToolCheck(boolean showRunningStatus) {
        if (showRunningStatus) {
            updateStatus("LanguageTool-Prüfung läuft...");
        }
        String editorText = editor.getText();
        if (editorText == null || editorText.isBlank()) {
            languageToolHasBeenChecked = true;
            editor.clearLanguageToolMatches();
            if (showRunningStatus) {
                updateStatus("LanguageTool: Kein Text zum Prüfen", true);
            }
            return;
        }

        LanguageToolTextMapping mapping = LanguageToolTextMapping.fromOriginal(editorText);
        final long checkGeneration = languageToolCheckGeneration;
        languageToolService.startServerIfNeeded()
                .thenCompose(running -> {
                    if (!running) {
                        return java.util.concurrent.CompletableFuture.completedFuture(null);
                    }
                    return languageToolService.checkText(mapping.cleanedText(), "de-DE");
                })
                .thenAccept(result -> Platform.runLater(() -> {
                    if (checkGeneration != languageToolCheckGeneration) {
                        return;
                    }
                    if (result == null) {
                        languageToolHasBeenChecked = true;
                        if (showRunningStatus) {
                            updateStatusError("LanguageTool Server nicht verfügbar");
                        }
                        updateLanguageToolStatus();
                        return;
                    }
                    List<LanguageToolService.Match> matches = mapping.mapMatchesToOriginal(result.getMatches());
                    matches = languageToolDictionary.filterMatches(matches, editor.getText());
                    languageToolHasBeenChecked = true;
                    editor.applyLanguageToolMatches(matches);
                    updateLanguageToolStatus();
                    if (showRunningStatus) {
                        updateStatus("LanguageTool: " + matches.size() + " Fehler gefunden");
                    }
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        if (checkGeneration == languageToolCheckGeneration && showRunningStatus) {
                            updateStatusError("LanguageTool fehlgeschlagen: " + ex.getMessage());
                        }
                    });
                    return null;
                });
    }

    @Override
    public void updateStatus(String message) {
        if (statusLabel == null) {
            return;
        }
        statusLabel.setText(message == null ? "" : message);
        statusLabel.setStyle(STATUS_STYLE_READY);
        scheduleStatusClear(5);
    }

    private void updateStatus(String message, boolean isError) {
        if (isError) {
            updateStatusError(message);
        } else {
            updateStatus(message);
        }
    }

    @Override
    public void updateStatusError(String message) {
        if (statusLabel == null) {
            return;
        }
        statusLabel.setText(message == null ? "" : message);
        statusLabel.setStyle(STATUS_STYLE_WARNING);
        scheduleStatusClear(5);
    }

    private void updateStatusDisplay() {
        if (statusLabel == null) {
            return;
        }
        if (dirty) {
            statusLabel.setText("⚠ Ungespeicherte Änderungen");
            statusLabel.setStyle(STATUS_STYLE_WARNING);
        } else {
            statusLabel.setText("Bereit");
            statusLabel.setStyle(STATUS_STYLE_READY);
        }
    }

    private void scheduleStatusClear(long delaySeconds) {
        synchronized (statusLock) {
            if (statusClearExecutor == null) {
                statusClearExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "PrototypeEditorStatusClear");
                    t.setDaemon(true);
                    return t;
                });
            }
            if (statusClearFuture != null && !statusClearFuture.isDone()) {
                statusClearFuture.cancel(false);
            }
            statusClearFuture = statusClearExecutor.schedule(() -> Platform.runLater(() -> {
                if (statusLabel != null) {
                    updateStatusDisplay();
                }
            }), delaySeconds, TimeUnit.SECONDS);
        }
    }

    private void updateLanguageToolStatus() {
        if (lblLanguageToolStatus == null) {
            return;
        }
        int count = editor.getLanguageToolMatchCount();
        if (!languageToolAutoEnabled && !languageToolHasBeenChecked) {
            lblLanguageToolStatus.setText("");
            lblLanguageToolStatus.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");
            lblLanguageToolStatus.setTooltip(new Tooltip("LanguageTool automatisch deaktiviert"));
        } else if (count == 0) {
            lblLanguageToolStatus.setText("✓");
            lblLanguageToolStatus.setStyle("-fx-text-fill: #4caf50; -fx-font-size: 11px;");
            lblLanguageToolStatus.setTooltip(new Tooltip("Keine Fehler gefunden"));
        } else {
            lblLanguageToolStatus.setText("⚠ " + count);
            lblLanguageToolStatus.setStyle("-fx-text-fill: #f44336; -fx-font-size: 11px;");
            lblLanguageToolStatus.setTooltip(new Tooltip(count + " Fehler gefunden"));
        }
    }

    private void loadSelectedChapterFromMainWindow() {
        if (mainController == null) {
            updateStatusError("Kein Hauptfenster angebunden");
            return;
        }
        MainController.PrototypeChapterContent chapter = mainController.loadSelectedChapterMarkdownForPrototype();
        if (chapter == null) {
            updateStatus("Kein Kapitel ausgewählt oder keine MD-Datei in data gefunden", true);
            return;
        }
        applyLoadedChapter(chapter, chapter.docxFile());
    }

    private void applyLoadedChapter(MainController.PrototypeChapterContent chapter, File docxFile) {
        suppressDirty = true;
        try {
            clearTransientMarks();
            loadedChapterFile = chapter.file();
            loadedDocxFile = docxFile;
            loadedProjectDirectory = chapter.file().getParentFile() != null
                    ? chapter.file().getParentFile().getParentFile()
                    : null;
            editor.loadDocument(chapter.content(), chapter.file().getParentFile(), loadedProjectDirectory);
            loadedChapterName = chapter.fileName();
            initializeImageDirectories();
            captureOriginalContent();
            setDirty(false);
            updateChapterList();
            refreshChapterListAppearance();
            updateStatus("Geladen: " + loadedChapterName);
            checkQuoteErrorsOnLoad(chapter.content());
            registerWithMainController();
            reloadSceneOutlineIfOpen();
        } finally {
            suppressDirty = false;
            scheduleInitialLanguageToolCheck();
        }
    }

    private void convertAllQuotationMarksInText(int styleIndex) {
        String currentText = editor.getText();
        if (currentText == null || currentText.isEmpty()) {
            return;
        }
        String convertedText = QuotationMarkSupport.convertTextToStyle(currentText, styleIndex);
        if (currentText.equals(convertedText)) {
            return;
        }
        suppressDirty = true;
        try {
            editor.replaceAllTextPreservingCaretAndViewport(convertedText);
            updateDirtyFromContent(convertedText);
        } finally {
            suppressDirty = false;
        }
        updateStatus("Anführungszeichen zu " + QuotationMarkSupport.styleLabel(styleIndex) + " konvertiert");
    }

    private void checkQuoteErrorsOnLoad(String text) {
        List<QuotationMarkSupport.QuoteError> errors = QuotationMarkSupport.findQuoteErrors(text);
        if (!errors.isEmpty()) {
            Platform.runLater(() -> showQuoteErrorsDialog(errors));
        }
    }

    private void showQuoteErrorsDialog(List<QuotationMarkSupport.QuoteError> errors) {
        if (errors == null || errors.isEmpty()) {
            updateStatus("Keine Anführungszeichen-Fehler gefunden.");
            return;
        }
        if (quoteErrorsDialogShown) {
            return;
        }
        quoteErrorsDialogShown = true;

        CustomStage errorStage = StageManager.createStage("Anführungszeichen-Fehler");
        if (stage != null) {
            errorStage.initOwner(stage);
        }
        errorStage.setMinWidth(700);
        errorStage.setMinHeight(500);

        VBox mainContainer = new VBox(16);
        mainContainer.setPadding(new Insets(20));

        Label titleLabel = new Label("Anführungszeichen-Fehler gefunden");
        Label descriptionLabel = new Label("Die folgenden Absätze haben eine ungerade Anzahl von Anführungszeichen:");

        ScrollPane scrollPane = new ScrollPane();
        VBox errorList = new VBox(10);
        for (QuotationMarkSupport.QuoteError error : errors) {
            VBox errorItem = new VBox(4);
            Label typeLabel = new Label(error.type() + " (" + error.count() + " Stück)");
            TextArea paragraphArea = new TextArea(error.paragraph());
            paragraphArea.setEditable(false);
            paragraphArea.setPrefRowCount(3);
            paragraphArea.setWrapText(true);
            paragraphArea.setTooltip(new Tooltip("Klicken zum Springen zum Absatz im Editor"));
            paragraphArea.setOnMouseClicked(e -> {
                jumpToQuoteError(error);
                editor.requestInputFocus();
            });
            errorItem.getChildren().addAll(typeLabel, paragraphArea);
            errorList.getChildren().add(errorItem);
        }
        scrollPane.setContent(errorList);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(360);

        Button closeButton = new Button("Schließen");
        closeButton.setOnAction(e -> {
            quoteErrorsDialogShown = false;
            errorStage.close();
        });
        HBox buttonBox = new HBox(closeButton);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        mainContainer.getChildren().addAll(titleLabel, descriptionLabel, scrollPane, buttonBox);
        Scene scene = new Scene(mainContainer, 780, 560);
        String cssPath = ResourceManager.getCssResource("css/manuskript.css");
        if (cssPath != null) {
            scene.getStylesheets().add(cssPath);
        }
        errorStage.setSceneWithTitleBar(scene);
        errorStage.setFullTheme(themeIndex);
        applyThemeToNode(mainContainer, themeIndex);
        errorStage.setOnHidden(e -> quoteErrorsDialogShown = false);
        errorStage.show();
        Platform.runLater(() -> {
            errorStage.toFront();
            errorStage.requestFocus();
        });
        updateStatus("⚠ " + errors.size() + " Anführungszeichen-Fehler gefunden", true);
    }

    private void jumpToQuoteError(QuotationMarkSupport.QuoteError error) {
        if (error == null) {
            return;
        }
        int offset = Math.max(0, Math.min(editor.getText().length(), error.textOffset()));
        editor.selectRange(offset, Math.min(editor.getText().length(), offset + error.paragraph().length()));
        editor.scrollToOffset(offset);
        editor.requestInputFocus();
    }

    private void clearTransientMarks() {
        clearSearchMarks();
        invalidateSearchCache();
        cancelLanguageToolChecks();
        languageToolHasBeenChecked = false;
        editor.clearLanguageToolMatches();
        updateLanguageToolStatus();
    }

    private void saveLoadedChapter() {
        if (loadedChapterFile == null) {
            updateStatusError("Keine geladene MD-Datei zum Speichern");
            return;
        }
        try {
            Files.writeString(loadedChapterFile.toPath(), normalizeMarkdownParagraphSpacing(editor.getText()), StandardCharsets.UTF_8);
            captureOriginalContent();
            setDirty(false);
            refreshChapterListAppearance();
            updateStatus("Gespeichert: " + loadedChapterFile.getName());
        } catch (IOException e) {
            updateStatusError("Speichern fehlgeschlagen: " + e.getMessage());
        }
    }

    private File resolveProjectDirectory() {
        if (loadedProjectDirectory != null && loadedProjectDirectory.isDirectory()) {
            return loadedProjectDirectory;
        }
        if (mainController != null) {
            String path = mainController.getCurrentDirectoryPath();
            if (path != null && !path.isBlank()) {
                File dir = new File(path.trim());
                if (dir.isDirectory()) {
                    return dir;
                }
            }
        }
        if (loadedChapterFile != null && loadedChapterFile.getParentFile() != null) {
            File parent = loadedChapterFile.getParentFile().getParentFile();
            if (parent != null && parent.isDirectory()) {
                return parent;
            }
        }
        return null;
    }

    private File resolveMdDirectory() {
        if (loadedChapterFile != null && loadedChapterFile.getParentFile() != null) {
            return loadedChapterFile.getParentFile();
        }
        File projectDir = resolveProjectDirectory();
        if (projectDir != null) {
            File dataDir = new File(projectDir, "data");
            if (dataDir.isDirectory()) {
                return dataDir;
            }
        }
        return null;
    }

    private void insertImage() {
        CustomAlert alert = new CustomAlert(CustomAlert.AlertType.INFORMATION);
        alert.setTitle("Bild einfügen");
        alert.setHeaderText("Bild einfügen");
        alert.initOwner(stage);

        VBox contentBox = new VBox(10);
        contentBox.setPadding(new Insets(10));

        Label pathLabel = new Label("Pfad:");
        TextField pathField = new TextField();
        pathField.setPromptText("Pfad zum Bild");
        HBox.setHgrow(pathField, Priority.ALWAYS);
        pathField.setText(preferences.get(PREF_LAST_IMAGE_PATH, ""));

        Label textLabel = new Label("Beschriftung:");
        TextField textField = new TextField();
        textField.setPromptText("Optionale Bildbeschriftung");
        HBox.setHgrow(textField, Priority.ALWAYS);
        textField.setText(preferences.get(PREF_LAST_IMAGE_ALT, ""));

        Label sizeLabel = new Label("Breite (%):");
        Spinner<Integer> widthSpinner = new Spinner<>();
        widthSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(10, 100, 80, 5));
        widthSpinner.setEditable(true);
        widthSpinner.setPrefWidth(80);
        int savedWidth = preferences.getInt(PREF_LAST_IMAGE_WIDTH, 80);
        widthSpinner.getValueFactory().setValue(Math.max(10, Math.min(100, savedWidth)));

        Button btnBrowse = new Button("Durchsuchen...");
        btnBrowse.setOnAction(e -> {
            e.consume();
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Bild auswählen");
            fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Bilddateien", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.webp"),
                    new FileChooser.ExtensionFilter("Alle Dateien", "*.*"));

            String lastDirectory = preferences.get(PREF_LAST_IMAGE_DIRECTORY, "");
            if (!lastDirectory.isBlank()) {
                File dir = new File(lastDirectory);
                if (dir.isDirectory()) {
                    fileChooser.setInitialDirectory(dir);
                }
            } else {
                File projectDir = resolveProjectDirectory();
                if (projectDir != null && projectDir.isDirectory()) {
                    fileChooser.setInitialDirectory(projectDir);
                }
            }

            File selectedFile = fileChooser.showOpenDialog(alert.getDialogWindow());
            if (selectedFile != null) {
                pathField.setText(selectedFile.getAbsolutePath());
                if (selectedFile.getParentFile() != null) {
                    preferences.put(PREF_LAST_IMAGE_DIRECTORY, selectedFile.getParentFile().getAbsolutePath());
                }
            }
        });

        HBox pathBox = new HBox(10, pathField, btnBrowse);
        HBox.setHgrow(pathField, Priority.ALWAYS);
        HBox sizeBox = new HBox(8, sizeLabel, widthSpinner);
        sizeBox.setAlignment(Pos.CENTER_LEFT);
        contentBox.getChildren().addAll(pathLabel, pathBox, textLabel, textField, sizeBox);
        alert.setCustomContent(contentBox);
        alert.applyTheme(themeIndex);
        ButtonType insertButton = new ButtonType("Einfügen");
        ButtonType cancelButton = new ButtonType("Abbrechen");
        alert.setButtonTypes(insertButton, cancelButton);

        Optional<ButtonType> result = alert.showAndWait(stage);
        if (result.isEmpty() || result.get() != insertButton) {
            return;
        }

        File projectDirectory = resolveProjectDirectory();
        if (projectDirectory == null) {
            CustomAlert error = new CustomAlert(CustomAlert.AlertType.WARNING);
            error.setTitle("Kein Arbeitsverzeichnis");
            error.setHeaderText("Kein Arbeitsverzeichnis");
            error.setContentText("Bitte im Hauptfenster ein Projektverzeichnis wählen oder ein Kapitel laden.");
            error.applyTheme(themeIndex);
            error.initOwner(stage);
            error.showAndWait(stage);
            return;
        }

        String imagePath = pathField.getText();
        String caption = textField.getText();
        if (imagePath == null || imagePath.isBlank()) {
            updateStatusError("Kein Bildpfad angegeben");
            return;
        }

        try {
            File sourceImage = new File(imagePath.trim());
            if (!sourceImage.isFile()) {
                updateStatusError("Bilddatei nicht gefunden");
                return;
            }

            loadedProjectDirectory = projectDirectory;
            File targetImage = MarkdownImageSupport.copyImageToProjectDirectory(sourceImage, projectDirectory);
            int widthPercent = widthSpinner.getValue() == null ? 80 : widthSpinner.getValue();
            String markdown = MarkdownImageSupport.buildMarkdown(targetImage.getName(), caption, widthPercent);
            editor.insertText("\n\n" + markdown + "\n\n");
            editor.setImageDirectories(resolveMdDirectory(), projectDirectory);

            preferences.put(PREF_LAST_IMAGE_PATH, sourceImage.getAbsolutePath());
            preferences.putInt(PREF_LAST_IMAGE_WIDTH, widthPercent);
            if (caption != null && !caption.isBlank()) {
                preferences.put(PREF_LAST_IMAGE_ALT, caption);
            }
            updateStatus("Bild eingefügt: " + targetImage.getName());
        } catch (IOException ex) {
            updateStatusError("Bild konnte nicht kopiert werden: " + ex.getMessage());
        }
    }

    private String normalizeMarkdownParagraphSpacing(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }

        String normalized = markdown.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder result = new StringBuilder(normalized.length() + 64);
        String[] lines = normalized.split("\n", -1);
        boolean previousWasNonEmpty = false;

        for (String line : lines) {
            boolean currentIsNonEmpty = !line.trim().isEmpty();
            if (previousWasNonEmpty && currentIsNonEmpty) {
                result.append('\n');
            }
            result.append(line).append('\n');
            previousWasNonEmpty = currentIsNonEmpty;
            if (!currentIsNonEmpty) {
                previousWasNonEmpty = false;
            }
        }

        return result.toString();
    }

    private void setDirty(boolean dirty) {
        this.dirty = dirty;
        String title = "Prototyp: Eigener Editor";
        if (loadedChapterName != null && !loadedChapterName.isBlank()) {
            title += " - " + loadedChapterName;
        }
        if (dirty) {
            title += " *";
        }
        if (stage != null) {
            stage.setCustomTitle(title);
        }
        updateStatusDisplay();
    }

    private void captureOriginalContent() {
        originalContent = cleanTextForComparison(editor.getText());
    }

    private void updateDirtyFromContent(String text) {
        setDirty(!cleanTextForComparison(text).equals(originalContent));
    }

    private static String cleanTextForComparison(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("¶", "");
    }

    private void handleCloseRequest(WindowEvent event) {
        refreshDirtyState();
        if (!dirty) {
            cancelLanguageToolChecks();
            return;
        }
        event.consume();
        if (confirmDiscardUnsavedChanges()) {
            cancelLanguageToolChecks();
            if (mainController != null && getEditorKey() != null) {
                mainController.unregisterChapterEditor(getEditorKey());
            }
            stage.close();
        }
    }

    private void refreshDirtyState() {
        if (editor != null) {
            updateDirtyFromContent(editor.getText());
        }
    }

    private boolean confirmDiscardUnsavedChanges() {
        return confirmLoseUnsavedChanges("Fenster trotzdem schließen?");
    }

    private boolean confirmLoseUnsavedChanges(String actionDescription) {
        refreshDirtyState();
        if (!dirty) {
            return true;
        }
        CustomAlert alert = new CustomAlert(CustomAlert.AlertType.CONFIRMATION);
        alert.setTitle("Ungespeicherte Änderungen");
        alert.setHeaderText("Ungespeicherte Änderungen");
        alert.setContentText("Der Prototyp-Editor enthält ungespeicherte Änderungen. "
                + (actionDescription == null || actionDescription.isBlank() ? "Fortfahren?" : actionDescription));
        alert.applyTheme(themeIndex);
        Optional<ButtonType> result = alert.showAndWait(stage);
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    private void applyChapterSidebarTheme() {
        if (sidebarContainer == null || chapterListView == null) {
            return;
        }
        chapterSidebarTheme = ChapterSidebarTheme.forThemeIndex(themeIndex);
        sidebarContainer.setStyle("-fx-background-color: " + chapterSidebarTheme.listBackground() + ";");
        sidebarHeader.setStyle(String.format(
                "-fx-background-color: %s; -fx-border-color: %s; -fx-border-width: 0 0 1 0;",
                chapterSidebarTheme.headerBackground(), chapterSidebarTheme.headerBorderColor()));
        sidebarTitleLabel.setStyle("-fx-text-fill: " + chapterSidebarTheme.titleColor()
                + "; -fx-font-size: 14px; -fx-font-weight: bold;");
        chapterListView.setStyle(String.format(
                "-fx-background-color: %s; -fx-control-inner-background: %s;",
                chapterSidebarTheme.listBackground(), chapterSidebarTheme.listBackground()));
        if (chapterListPlaceholder != null) {
            chapterListPlaceholder.setStyle("-fx-text-fill: " + chapterSidebarTheme.textColor() + ";");
        }
        applyThemeToNode(sidebarContainer, themeIndex);
        refreshChapterListAppearance();
    }

    private void styleChapterListCell(ListCell<DocxFile> cell, DocxFile docxFile, boolean empty) {
        if (chapterSidebarTheme == null) {
            chapterSidebarTheme = ChapterSidebarTheme.forThemeIndex(themeIndex);
        }
        if (empty || docxFile == null) {
            cell.setText(null);
            cell.setStyle("");
            cell.getStyleClass().removeAll("current-chapter", "changed-chapter");
            return;
        }
        cell.setText(docxFile.getDisplayFileName());
        cell.getStyleClass().removeAll("current-chapter", "changed-chapter");
        String textColor = chapterSidebarTheme.textColor();
        String background = "transparent";
        if (cell.isSelected()) {
            background = chapterSidebarTheme.selectedBackground();
        }
        if (loadedDocxFile != null && docxFile.getFile().equals(loadedDocxFile)) {
            cell.getStyleClass().add("current-chapter");
            background = chapterSidebarTheme.currentChapterBackground();
            textColor = chapterSidebarTheme.textColor();
        } else if (docxFile.isChanged()) {
            cell.getStyleClass().add("changed-chapter");
            textColor = chapterSidebarTheme.changedChapterText();
        }
        cell.setStyle(String.format(
                "-fx-text-fill: %s; -fx-background-color: %s; -fx-padding: 8 12 8 12;",
                textColor, background));
        if (cell.getStyleClass().contains("current-chapter")) {
            cell.setStyle(cell.getStyle() + String.format(
                    " -fx-border-color: %s; -fx-border-width: 0 0 0 3;",
                    chapterSidebarTheme.currentChapterBorder()));
        }
    }

    private record ChapterSidebarTheme(
            String listBackground,
            String textColor,
            String headerBackground,
            String headerBorderColor,
            String titleColor,
            String selectedBackground,
            String changedChapterText,
            String currentChapterBackground,
            String currentChapterBorder) {

        static ChapterSidebarTheme forThemeIndex(int themeIndex) {
            return switch (themeIndex) {
                case 0 -> new ChapterSidebarTheme(
                        "#ffffff", "#000000", "#ffffff", "#dddddd", "#000000",
                        "#e3f2fd", "#ff6b35", "#d4e8f7", "#4a90e2");
                case 2 -> new ChapterSidebarTheme(
                        "#f3e5f5", "#4a148c", "#f3e5f5", "#dddddd", "#4a148c",
                        "#e1bee7", "#ff6b35", "#e1d5f0", "#7e57c2");
                case 3 -> new ChapterSidebarTheme(
                        "#1e3a8a", "#ffffff", "#1e3a8a", "#3b82f6", "#ffffff",
                        "#1e40af", "#ff8c69", "#2563eb", "#60a5fa");
                case 4 -> new ChapterSidebarTheme(
                        "#064e3b", "#ffffff", "#064e3b", "#10b981", "#ffffff",
                        "#047857", "#ff8c69", "#0d9488", "#34d399");
                case 5 -> new ChapterSidebarTheme(
                        "#4c1d95", "#ffffff", "#4c1d95", "#8b5cf6", "#ffffff",
                        "#6d28d9", "#ff8c69", "#7c3aed", "#a78bfa");
                default -> new ChapterSidebarTheme(
                        "#1e1e1e", "#ffffff", "#2a2a2a", "#444444", "#ffffff",
                        "#1e3a5f", "#ff8c69", "#2a5080", "#4a90e2");
            };
        }
    }

    private void applyThemeToNode(Node node, int themeIndex) {
        if (node == null) {
            return;
        }
        node.getStyleClass().removeAll("theme-dark", "theme-light", "blau-theme", "gruen-theme", "lila-theme", "weiss-theme", "pastell-theme");
        switch (themeIndex) {
            case 0 -> node.getStyleClass().add("weiss-theme");
            case 2 -> node.getStyleClass().add("pastell-theme");
            case 3 -> node.getStyleClass().addAll("theme-dark", "blau-theme");
            case 4 -> node.getStyleClass().addAll("theme-dark", "gruen-theme");
            case 5 -> node.getStyleClass().addAll("theme-dark", "lila-theme");
            default -> node.getStyleClass().add("theme-dark");
        }
    }

    private void registerWithMainController() {
        if (mainController == null || loadedDocxFile == null) {
            return;
        }
        String key = getEditorKey();
        if (key != null) {
            mainController.registerChapterEditor(key, this);
        }
    }

    private void toggleTextAnalysisWindow() {
        if (textAnalysisWindow == null) {
            textAnalysisWindow = new ChapterTextAnalysisWindow(createTextAnalysisHost());
        }
        textAnalysisWindow.toggle();
    }

    private ChapterMacroWindow.Host createMacroHost() {
        return new ChapterMacroWindow.Host() {
            @Override
            public ChapterEditorHost getChapterEditor() {
                return ManuskriptEditorTestWindow.this;
            }

            @Override
            public void updateStatus(String message) {
                ManuskriptEditorTestWindow.this.updateStatus(message);
            }

            @Override
            public void updateStatusError(String message) {
                ManuskriptEditorTestWindow.this.updateStatus(message, true);
            }

            @Override
            public javafx.stage.Stage getOwnerStage() {
                return stage;
            }

            @Override
            public Window getOwnerWindow() {
                return stage;
            }

            @Override
            public int getThemeIndex() {
                return themeIndex;
            }

            @Override
            public void applyThemeToNode(Node node, int themeIndex) {
                ManuskriptEditorTestWindow.this.applyThemeToNode(node, themeIndex);
            }
        };
    }

    private void toggleMacroWindow() {
        if (macroWindow == null) {
            macroWindow = new ChapterMacroWindow(createMacroHost());
        }
        macroWindow.toggle();
    }

    private void toggleSceneOutlineWindow() {
        File docx = loadedDocxFile != null ? loadedDocxFile : loadedChapterFile;
        if (docx == null) {
            updateStatus("Bitte zuerst ein Kapitel laden.", true);
            return;
        }
        if (sceneOutlineWindow == null) {
            sceneOutlineWindow = new SceneOutlineWindow();
            if (chapterAgentSupport != null) {
                chapterAgentSupport.setSceneOutlineWindow(sceneOutlineWindow);
            }
        }
        String chapterName = loadedChapterName != null ? loadedChapterName : docx.getName();
        if (sceneOutlineWindow.isShowing()) {
            sceneOutlineWindow.hide();
            updateStatus("Szenen-Outline geschlossen");
        } else {
            sceneOutlineWindow.show(stage != null ? stage.getScene() : null, docx, chapterName, themeIndex);
            updateStatus("Szenen-Outline geöffnet");
        }
    }

    private void reloadSceneOutlineIfOpen() {
        if (sceneOutlineWindow == null || !sceneOutlineWindow.isShowing() || loadedDocxFile == null) {
            return;
        }
        String chapterName = loadedChapterName != null ? loadedChapterName : loadedDocxFile.getName();
        sceneOutlineWindow.reloadForChapter(stage != null ? stage.getScene() : null, loadedDocxFile, chapterName, themeIndex);
    }

    // --- ChapterEditorHost ---

    @Override
    public EditorKind getEditorKind() {
        return EditorKind.CANVAS;
    }

    @Override
    public javafx.stage.Stage getStage() {
        return stage;
    }

    @Override
    public File getOriginalDocxFile() {
        return loadedDocxFile;
    }

    @Override
    public void setOriginalDocxFile(File file) {
        loadedDocxFile = file;
    }

    @Override
    public String getEditorKey() {
        File docx = loadedDocxFile;
        if (docx == null && loadedChapterFile != null) {
            String name = loadedChapterFile.getName();
            if (name.toLowerCase().endsWith(".md")) {
                return name;
            }
            return null;
        }
        if (docx == null) {
            return null;
        }
        String name = docx.getName();
        if (name.toLowerCase().endsWith(".docx")) {
            name = name.substring(0, name.length() - 5);
        }
        return name + ".md";
    }

    @Override
    public String getText() {
        return editor.getText();
    }

    @Override
    public void setText(String text) {
        editor.setText(text);
    }

    /** Ersetzt Text nach Diff-Übernahme und markiert das Kapitel als geändert. */
    public void applyTextFromDiff(String text) {
        editor.setText(text);
        setDirty(true);
        updateStatus("Text wurde durch Diff geändert", true);
    }

    private void copyForSudowrite() {
        try {
            SudowriteClipboardHelper.copyForSudowrite(
                    editor.getText(),
                    msg -> updateStatus("In Zwischenablage kopiert (Sudowrite)"),
                    msg -> updateStatus(msg, true));
        } catch (Exception ex) {
            updateStatus("Fehler beim Kopieren: " + ex.getMessage(), true);
        }
    }

    @Override
    public void replaceRange(int start, int end, String replacement) {
        editor.replaceRange(start, end, replacement);
    }

    @Override
    public void revealRange(int start, int end) {
        editor.revealMatchAt(start, end);
    }

    @Override
    public int getCaretPosition() {
        return editor.getCaretPosition();
    }

    @Override
    public void selectRange(int start, int end) {
        editor.selectRange(start, end);
    }

    @Override
    public void requestEditorFocus() {
        editor.requestInputFocus();
    }

    @Override
    public boolean isDirty() {
        return dirty;
    }

    @Override
    public void markSaved() {
        setDirty(false);
    }

    @Override
    public void insertTextAtCaret(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        editor.insertText(text);
    }

    @Override
    public void jumpToQuote(String quote) {
        ChapterAgentQuoteActions.jumpToQuote(this, quote);
    }

    @Override
    public void setOnlineLektoratMode(boolean enabled) {
        onlineLektoratMode = enabled;
        if (enabled && chapterAgentSupport != null) {
            chapterAgentSupport.ensurePanelVisible(false);
        } else if (!enabled && chapterAgentSupport == null && mainSplitPane != null) {
            chapterAgentSupport = new ChapterAgentSupport(this, mainSplitPane);
            chapterAgentSupport.setSceneOutlineWindow(sceneOutlineWindow);
            chapterAgentSupport.setupIfEnabled();
        }
    }

    @Override
    public void startOnlineLektorat() {
        startOnlineLektorat(false);
    }

    @Override
    public void startOnlineLektorat(boolean enableAssessment) {
        if (lektoratHelper == null) {
            lektoratHelper = new ChapterOnlineLektoratHelper(this, lektoratPanel);
        }
        lektoratHelper.start(enableAssessment);
    }

    @Override
    public int getThemeIndex() {
        return themeIndex;
    }

    @Override
    public MainController getMainController() {
        return mainController;
    }

    @Override
    public boolean saveChapter() throws IOException {
        saveLoadedChapter();
        return true;
    }

    private void setupWindowPersistence() {
        stage.xProperty().addListener((obs, oldValue, newValue) ->
                PreferencesManager.putWindowPosition(preferences, "prototype_editor_window_x", newValue.doubleValue()));
        stage.yProperty().addListener((obs, oldValue, newValue) ->
                PreferencesManager.putWindowPosition(preferences, "prototype_editor_window_y", newValue.doubleValue()));
        stage.widthProperty().addListener((obs, oldValue, newValue) ->
                PreferencesManager.putWindowWidth(preferences, "prototype_editor_window_width", newValue.doubleValue()));
        stage.heightProperty().addListener((obs, oldValue, newValue) ->
                PreferencesManager.putWindowHeight(preferences, "prototype_editor_window_height", newValue.doubleValue()));
    }

    private static String sampleText() {
        return """
                # Kapitel 1 – Prototyp-Editor
                ## Ziele dieses Fensters
                ### Technische Eckpunkte

                Dies ist der erste Prototyp des eigenen Manuskript-Editors.

                Ziele dieses Fensters:
                - kein JavaFX TextArea-Control
                - kein RichTextFX
                - eigenes Textmodell
                - eigene Auswahl, Navigation, Undo/Redo
                - interne Suche und Ersetzen
                - externe Markierungen über MarkedArea

                Beispiel für automatische Regeln:
                **Dieser Text soll fett markiert werden**
                *Dieser Text soll kursiv markiert werden*
                ***fett und kursiv***
                ~~durchgestrichen~~
                <mark>hervorgehoben</mark>
                <u>Dieser Text soll unterstrichen werden</u>
                <big>größer</big> und <small>kleiner</small>
                Formel: E = mc<sup>2</sup>, chemisch: H<sub>2</sub>O
                <red>roter Text</red>

                > Dies ist ein Blockquote mit grauer kursiver Darstellung.

                <center>Zentrierter Absatz</center>

                Jorin geht durch den Regen. Jorina wartet am Tor.

                Langer Absatz für Zeilenumbruch und Viewport-Stabilität: Jorin betrachtet die alten Mauern der Stadt, während der Regen in langen silbernen Fäden über die Dächer zieht und **wichtige Begriffe fett** hervortreten, ohne dass die sichtbaren Marker den Umbruch verschieben sollen. Dieser Satz enthält außerdem *kursiv gesetzte Gedanken* und <u>unterstrichene Hinweise</u>, damit Hoch- und Runter-Navigation bei proportionaler Schrift ungefähr auf derselben sichtbaren Position bleibt.

                ExtremLangesWortOhneLeerzeichenDasHartUmbrechenMussDamitDerEditorNichtÜberDenRandHinausLäuftUndTrotzdemWeiterBedienbarBleibt.

                Nächste Schritte: Zeilenumbruch, stabile Viewport-Logik, Bilder, Link-Callbacks,
                LanguageTool-Hover und robustere Überlappungsregeln werden hier isoliert getestet.
                """;
    }

    private record LanguageToolTextMapping(String cleanedText, int[] cleanToOriginal) {
        static LanguageToolTextMapping fromOriginal(String original) {
            String source = original == null ? "" : original;
            StringBuilder cleaned = new StringBuilder(source.length());
            List<Integer> mapping = new ArrayList<>(source.length());

            for (int i = 0; i < source.length(); i++) {
                if (source.startsWith("**", i)) {
                    i++;
                    continue;
                }
                if (source.startsWith("<u>", i)) {
                    i += 2;
                    continue;
                }
                if (source.startsWith("</u>", i)) {
                    i += 3;
                    continue;
                }

                char c = source.charAt(i);
                if (c == '*') {
                    continue;
                }

                cleaned.append(normalizeForLanguageTool(c));
                mapping.add(i);
            }

            int[] offsets = new int[mapping.size()];
            for (int i = 0; i < mapping.size(); i++) {
                offsets[i] = mapping.get(i);
            }
            return new LanguageToolTextMapping(cleaned.toString(), offsets);
        }

        List<LanguageToolService.Match> mapMatchesToOriginal(List<LanguageToolService.Match> matches) {
            List<LanguageToolService.Match> mapped = new ArrayList<>();
            if (matches == null) {
                return mapped;
            }

            for (LanguageToolService.Match match : matches) {
                int cleanStart = match.getOffset();
                int cleanEndExclusive = cleanStart + match.getLength();
                if (cleanStart < 0 || cleanStart >= cleanToOriginal.length) {
                    continue;
                }
                int safeCleanEnd = Math.max(cleanStart + 1, Math.min(cleanEndExclusive, cleanToOriginal.length));
                int originalStart = cleanToOriginal[cleanStart];
                int originalEnd = cleanToOriginal[safeCleanEnd - 1] + 1;
                if (originalEnd <= originalStart) {
                    continue;
                }

                LanguageToolService.Match mappedMatch = new LanguageToolService.Match();
                mappedMatch.setOffset(originalStart);
                mappedMatch.setLength(originalEnd - originalStart);
                mappedMatch.setMessage(match.getMessage());
                mappedMatch.setShortMessage(match.getShortMessage());
                mappedMatch.setRuleId(match.getRuleId());
                mappedMatch.setRuleDescription(match.getRuleDescription());
                mappedMatch.setReplacements(match.getReplacements());
                mapped.add(mappedMatch);
            }
            return mapped;
        }

        private static char normalizeForLanguageTool(char c) {
            return switch (c) {
                case '»', '«', '„', '“', '”', '‟', '‹', '›' -> '"';
                case '‚', '‘', '’' -> '\'';
                default -> c;
            };
        }
    }
}
