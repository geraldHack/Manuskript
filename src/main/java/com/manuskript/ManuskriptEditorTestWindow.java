package com.manuskript;

import javafx.beans.binding.Bindings;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.util.Duration;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
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
import javafx.scene.control.ToggleButton;
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
 * Kapitel-Editor (Canvas) mit Kapitel-Laden aus dem Hauptfenster.
 */
public class ManuskriptEditorTestWindow implements ChapterEditorHost {

    private static final String PREF_USE_CANVAS = "use_canvas_chapter_editor";

    private static final String PREF_FONT_FAMILY = "prototype_editor_font_family";
    private static final String PREF_FONT_SIZE = "prototype_editor_font_size";
    private static final String PREF_LINE_SPACING = "prototype_editor_line_spacing";
    private static final String PREF_PARAGRAPH_SPACING = "prototype_editor_paragraph_spacing";
    private static final String PREF_JUSTIFY_TEXT = "prototype_editor_justify_text";
    private static final String PREF_LAST_IMAGE_PATH = "prototype_editor_last_image_path";
    private static final String PREF_LAST_IMAGE_ALT = "prototype_editor_last_image_alt";
    private static final String PREF_LAST_IMAGE_DIRECTORY = "prototype_editor_last_image_directory";
    private static final String PREF_LAST_IMAGE_WIDTH = "prototype_editor_last_image_width";
    private static final String PREF_HIDE_MARKUP = "prototype_editor_hide_markup";
    private static final String PREF_SHOW_LINE_NUMBERS = "prototype_editor_show_line_numbers";
    private static final String PREF_LT_AUTO = "prototype_editor_languagetool_auto";
    private static final String PREF_SIDEBAR_EXPANDED = "prototype_editor_sidebar_expanded";
    private static final String PREF_HOST_TOOLBAR_EXPANDED = "prototype_editor_host_toolbar_expanded";

    private final Window owner;
    private final MainController mainController;
    private final Preferences preferences = Preferences.userNodeForPackage(ManuskriptEditorTestWindow.class);
    private CustomStage stage;
    private MdTextArea mdTextArea;
    private ManuskriptTextEditor editor;
    private Label statusLabel;
    private Label lblSelectionCount;
    private Label lblLanguageToolStatus;
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
    private HBox sidebarColumn;
    private VBox sidebarContainer;
    private HBox sidebarHeader;
    private Label sidebarTitleLabel;
    private Label chapterListPlaceholder;
    private Button btnToggleSidebar;
    private Button btnToggleHostToolbar;
    private ToggleButton btnToggleAgents;
    private VBox hostToolbarCollapsibleSection;
    private VBox editorMdToolbar;
    private boolean hostToolbarExpanded = true;
    private ListView<DocxFile> chapterListView;
    private ChapterSidebarTheme chapterSidebarTheme;
    private boolean sidebarExpanded = true;
    private ListChangeListener<DocxFile> chapterListChangeListener;
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
        stage = StageManager.createStage("Kapitel-Editor");
        stage.setWindowPersistenceType("prototype_editor");
        if (owner instanceof javafx.stage.Stage ownerStage) {
            stage.initOwner(ownerStage);
        }
        stage.setMinWidth(900);
        stage.setMinHeight(650);

        themeIndex = Preferences.userNodeForPackage(MainController.class).getInt("main_window_theme", 0);

        mdTextArea = new MdTextArea(MdTextAreaOptions.builder()
                .fontFamily(preferences.get(PREF_FONT_FAMILY, "Segoe UI"))
                .fontSize(preferences.getDouble(PREF_FONT_SIZE, 16.0))
                .lineSpacing(loadInitialLineSpacing())
                .paragraphSpacing(loadInitialParagraphSpacing())
                .justifyText(preferences.getBoolean(PREF_JUSTIFY_TEXT, false))
                .hideMarkup(preferences.getBoolean(PREF_HIDE_MARKUP, true))
                .showLineNumbers(preferences.getBoolean(PREF_SHOW_LINE_NUMBERS, true))
                .themeIndex(themeIndex)
                .enableUndoRedo(true)
                .enableFontControls(true)
                .enableJustify(true)
                .enableBasicFormatting(true)
                .enableSearch(true)
                .enableReplace(true)
                .enableHideMarkupToggle(true)
                .onFontFamilyChanged(value -> {
                    preferences.put(PREF_FONT_FAMILY, value);
                    if (chapterAgentSupport != null) {
                        chapterAgentSupport.applyEditorAppearance();
                    }
                })
                .onFontSizeChanged(value -> {
                    preferences.putDouble(PREF_FONT_SIZE, value);
                    int sizePx = (int) Math.round(value);
                    if (chapterAgentSupport != null) {
                        chapterAgentSupport.applyEditorAppearance();
                    }
                    if (lektoratPanel != null) {
                        lektoratPanel.applyFontSize(sizePx);
                    }
                })
                .onLineSpacingChanged(value -> preferences.putDouble(PREF_LINE_SPACING, value))
                .onParagraphSpacingChanged(value -> preferences.putDouble(PREF_PARAGRAPH_SPACING, value))
                .onJustifyChanged(value -> preferences.putBoolean(PREF_JUSTIFY_TEXT, value))
                .onHideMarkupChanged(value -> preferences.putBoolean(PREF_HIDE_MARKUP, value))
                .onSearchStatus(message -> updateStatus(message))
                .build());
        editor = mdTextArea.getEditor();

        languageToolDictionary = new LanguageToolDictionary();
        languageToolService = new LanguageToolService();
        languageToolAutoEnabled = preferences.getBoolean(PREF_LT_AUTO, false);
        editor.setLanguageToolDictionary(languageToolDictionary);
        editor.setOnLanguageToolMatchesChanged(() -> Platform.runLater(this::updateLanguageToolStatus));
        editor.setOnSelectionChanged(() -> Platform.runLater(this::updateSelectionCount));
        editor.setContextMenuRewriteActions(
                new ChapterRewriteContextActions(this, stage, () -> themeIndex, preferences, loadedDocxFile),
                themeIndex);
        captureOriginalContent();
        editor.setOnTextChanged(text -> {
            if (!suppressDirty) {
                updateDirtyFromContent(text);
            }
            scheduleLanguageToolCheckDebounced();
        });
        editor.setQuoteStyleIndex(Preferences.userNodeForPackage(EditorWindow.class).getInt("quoteStyle", 0));

        BorderPane root = new BorderPane();
        initializeStatusLabel();
        initializeSelectionLabel();
        root.setTop(createHostToolbar());
        root.setCenter(createEditorWithSidebar());
        applyHostToolbarExpanded(hostToolbarExpanded);
        root.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            Object targetObj = event.getTarget();
            if (!(targetObj instanceof Node target)) {
                return;
            }
            if (isDescendantOf(target, mdTextArea.getEditor())) {
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
        mdTextArea.applyTheme(themeIndex);
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

    private static boolean isDescendantOf(Node target, Node ancestor) {
        if (target == null || ancestor == null) {
            return false;
        }
        Node current = target;
        while (current != null) {
            if (current == ancestor) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private void setupChapterEditorFeatures() {
        lektoratHelper = new ChapterOnlineLektoratHelper(this, lektoratPanel);
        sceneOutlineWindow = new SceneOutlineWindow();
        if (!onlineLektoratMode) {
            chapterAgentSupport = new ChapterAgentSupport(this, mainSplitPane);
            chapterAgentSupport.setSceneOutlineWindow(sceneOutlineWindow);
            chapterAgentSupport.setupIfEnabled();
            wireSelectionRevisionAgentAction();
        }
        textAnalysisWindow = new ChapterTextAnalysisWindow(createTextAnalysisHost());
        macroWindow = new ChapterMacroWindow(createMacroHost());
        syncAgentsToggleButton();
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
        btnToggleSidebar = new Button();
        btnToggleSidebar.setMaxWidth(30);
        btnToggleSidebar.setMinWidth(30);
        btnToggleSidebar.setMinHeight(Region.USE_PREF_SIZE);
        btnToggleSidebar.getStyleClass().add("sidebar-toggle-button");
        btnToggleSidebar.setOnAction(e -> toggleSidebar());
        SidebarToggleButtonSupport.updateAppearance(btnToggleSidebar, sidebarExpanded, themeIndex);

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

        sidebarColumn = new HBox(0, btnToggleSidebar, sidebarContainer);
        sidebarColumn.setFillHeight(true);
        sidebarColumn.setMinWidth(Region.USE_PREF_SIZE);
        sidebarColumn.setMaxWidth(Region.USE_PREF_SIZE);

        lektoratPanelContainer = new VBox();
        lektoratPanelContainer.setMinWidth(120);
        lektoratPanelContainer.setPrefWidth(320);
        lektoratPanelContainer.setPadding(new Insets(10));

        VBox editorContentColumn = new VBox(0);
        HBox.setHgrow(editorContentColumn, Priority.ALWAYS);
        editorMdToolbar = mdTextArea.getToolbarNode();
        if (editorMdToolbar != null) {
            mdTextArea.useExternalToolbarLayout(editorContentColumn.widthProperty());
            editorContentColumn.getChildren().add(editorMdToolbar);
        }

        mainSplitPane = new SplitPane(mdTextArea.getEditorNode());
        ChapterEditorSplitPreferences.bindPersistence(mainSplitPane, preferences);
        lektoratPanel = new ChapterLektoratPanel(
                lektoratPanelContainer, mainSplitPane, () -> themeIndex, this::applyThemeToNode,
                this::getEditorFontSizePx);
        VBox.setVgrow(mainSplitPane, Priority.ALWAYS);
        editorContentColumn.getChildren().add(mainSplitPane);

        HBox editorRow = new HBox(0, sidebarColumn, editorContentColumn);
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

    private double loadInitialLineSpacing() {
        double saved = preferences.getDouble(PREF_LINE_SPACING, Double.NaN);
        if (!Double.isNaN(saved) && saved >= 1.0 && saved <= 3.0) {
            return saved;
        }
        return ResourceManager.getDoubleParameter("editor.line-spacing", 1.55);
    }

    private double loadInitialParagraphSpacing() {
        double saved = preferences.getDouble(PREF_PARAGRAPH_SPACING, Double.NaN);
        if (!Double.isNaN(saved) && saved >= 0 && saved <= 48) {
            return saved;
        }
        return ResourceManager.getIntParameter("editor.paragraph-spacing", 10);
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
        } else {
            sidebarContainer.setVisible(false);
            sidebarContainer.setManaged(false);
        }
        SidebarToggleButtonSupport.updateAppearance(btnToggleSidebar, sidebarExpanded, themeIndex);
        saveSidebarState();
    }

    private void loadSidebarState() {
        if (mainSplitPane == null || sidebarContainer == null || btnToggleSidebar == null) {
            return;
        }
        sidebarExpanded = preferences.getBoolean(PREF_SIDEBAR_EXPANDED, true);
        Platform.runLater(() -> {
            if (sidebarExpanded) {
                sidebarContainer.setVisible(true);
                sidebarContainer.setManaged(true);
            } else {
                sidebarContainer.setVisible(false);
                sidebarContainer.setManaged(false);
            }
            SidebarToggleButtonSupport.updateAppearance(btnToggleSidebar, sidebarExpanded, themeIndex);
        });
    }

    private void saveSidebarState() {
        preferences.putBoolean(PREF_SIDEBAR_EXPANDED, sidebarExpanded);
    }

    private void toggleHostToolbar() {
        applyHostToolbarExpanded(!hostToolbarExpanded);
        preferences.putBoolean(PREF_HOST_TOOLBAR_EXPANDED, hostToolbarExpanded);
    }

    private void applyHostToolbarExpanded(boolean expanded) {
        hostToolbarExpanded = expanded;
        if (hostToolbarCollapsibleSection != null) {
            hostToolbarCollapsibleSection.setVisible(expanded);
            hostToolbarCollapsibleSection.setManaged(expanded);
        }
        if (editorMdToolbar != null) {
            editorMdToolbar.setVisible(expanded);
            editorMdToolbar.setManaged(expanded);
        }
        if (btnToggleHostToolbar != null) {
            HostToolbarToggleSupport.updateAppearance(btnToggleHostToolbar, expanded, themeIndex);
        }
    }

    private void expandHostToolbarIfCollapsed() {
        if (hostToolbarExpanded) {
            return;
        }
        applyHostToolbarExpanded(true);
        preferences.putBoolean(PREF_HOST_TOOLBAR_EXPANDED, true);
    }

    private void focusSearchField() {
        expandHostToolbarIfCollapsed();
        mdTextArea.focusSearchField();
    }

    private void replaceNextMatch() {
        expandHostToolbarIfCollapsed();
        mdTextArea.replaceNextMatch();
    }

    private void navigateToChapter(DocxFile docxFile) {
        if (docxFile == null || mainController == null) {
            return;
        }
        if (loadedDocxFile != null && docxFile.getFile().equals(loadedDocxFile)) {
            return;
        }
        refreshDirtyState();
        if (dirty && !showSaveDialogForNavigation()) {
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

    private VBox createHostToolbar() {
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

        Button saveChapter = new Button("Speichern");
        saveChapter.setTooltip(new Tooltip("Speichern (" + EditingShortcuts.acceleratorHint("S") + ")"));
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

        btnToggleHostToolbar = new Button();
        btnToggleHostToolbar.setMinWidth(36);
        btnToggleHostToolbar.setMaxWidth(36);
        btnToggleHostToolbar.setMinHeight(24);
        btnToggleHostToolbar.setPrefHeight(24);
        btnToggleHostToolbar.setMaxHeight(24);
        btnToggleHostToolbar.getStyleClass().add("host-toolbar-toggle-button");
        hostToolbarExpanded = preferences.getBoolean(PREF_HOST_TOOLBAR_EXPANDED, true);
        btnToggleHostToolbar.setOnAction(e -> toggleHostToolbar());

        HBox toggleRow = new HBox(btnToggleHostToolbar);
        toggleRow.setAlignment(Pos.CENTER);
        toggleRow.setPadding(new Insets(2, 0, 2, 0));

        statusRow.getChildren().addAll(statusSpacer, saveChapter, lblLanguageToolStatus, lblSelectionCount, statusLabel);
        statusRow.setAlignment(Pos.CENTER_RIGHT);

        FlowPane formatPane = new FlowPane(6, 4);
        formatPane.setAlignment(Pos.CENTER_LEFT);
        formatPane.getChildren().addAll(
                showLineNumbers,
                mark, blockquote, center,
                superscript, subscript,
                big, small, colorMenu, lineBreak, horizontalRule);

        FlowPane toolsPane = new FlowPane(6, 4);
        toolsPane.setAlignment(Pos.CENTER_LEFT);
        Label quoteLabel = new Label("Anführungszeichen:");
        Button sceneOutline = toolbarButton("Outline", "Szenen-Outline für dieses Kapitel", this::toggleSceneOutlineWindow);
        Button textAnalysis = toolbarButton("Analyse", "Textanalyse-Fenster ein-/ausblenden", this::toggleTextAnalysisWindow);
        if (Boolean.parseBoolean(ResourceManager.getParameter("agent.enabled", "true"))) {
            btnToggleAgents = new ToggleButton("Agenten");
            btnToggleAgents.setSelected(Preferences.userNodeForPackage(ChapterAgentSupport.class)
                    .getBoolean(ChapterAgentSupport.PREF_AGENT_PANEL_VISIBLE, true));
            btnToggleAgents.setTooltip(new Tooltip("Agenten-Panel ein- oder ausblenden"));
            btnToggleAgents.setOnAction(e -> onAgentsToggle());
        }
        Button onlineLektorat = toolbarButton("Lektorat",
                "Online-Lektorat starten (Typ: " + OnlineLektoratService.currentLektoratTypeLabel()
                        + "). " + OnlineLektoratService.SETTINGS_HINT,
                () -> startOnlineLektorat(false));
        Button macrosBtn = toolbarButton("Makros", "Makro-Verwaltung ein-/ausblenden", this::toggleMacroWindow);
        Button copySudowrite = toolbarButton("Sudowrite", "Für Sudowrite kopieren (Zwischenablage)",
                this::copyForSudowrite);

        toolsPane.getChildren().addAll(
                quoteLabel, quoteStyle,
                languageTool, languageToolAuto,
                sceneOutline, textAnalysis);
        if (btnToggleAgents != null) {
            toolsPane.getChildren().add(btnToggleAgents);
        }
        toolsPane.getChildren().addAll(
                onlineLektorat, macrosBtn, copySudowrite,
                insertImage, editImage, deleteImage);

        hostToolbarCollapsibleSection = new VBox(8, formatPane, toolsPane);
        VBox toolbar = new VBox(4, toggleRow, statusRow, hostToolbarCollapsibleSection);
        toolbar.setPadding(new Insets(4, 8, 8, 8));
        var wrapLength = Bindings.max(220, toolbar.widthProperty().subtract(16));
        formatPane.prefWrapLengthProperty().bind(wrapLength);
        toolsPane.prefWrapLengthProperty().bind(wrapLength);
        applyHostToolbarExpanded(hostToolbarExpanded);
        applyThemeToNode(toolbar, themeIndex);
        HostToolbarToggleSupport.updateAppearance(btnToggleHostToolbar, hostToolbarExpanded, themeIndex);
        return toolbar;
    }

    private void onAgentsToggle() {
        if (chapterAgentSupport == null || !chapterAgentSupport.isAvailable()) {
            return;
        }
        if (onlineLektoratMode) {
            syncAgentsToggleButton();
            return;
        }
        chapterAgentSupport.setPanelVisible(btnToggleAgents.isSelected(), true);
    }

    private void syncAgentsToggleButton() {
        if (btnToggleAgents == null) {
            return;
        }
        boolean visible = chapterAgentSupport != null && chapterAgentSupport.isPanelVisible();
        btnToggleAgents.setSelected(visible);
        btnToggleAgents.setDisable(onlineLektoratMode);
    }

    private static Button toolbarButton(String label, String tooltip, Runnable action) {
        Button button = new Button(label);
        button.setTooltip(new Tooltip(tooltip));
        button.setOnAction(e -> action.run());
        return button;
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

    private void ensureEditingShortcutsInstalled() {
        if (editingShortcutsInstalled) {
            return;
        }
        Scene scene = stage.getScene();
        if (scene == null) {
            return;
        }
        var accelerators = scene.getAccelerators();
        EditingShortcuts.bindPlatformAccelerators(accelerators, "S", () -> Platform.runLater(this::saveLoadedChapter));
        EditingShortcuts.bindPlatformAccelerators(accelerators, "F", () -> Platform.runLater(this::focusSearchField));
        EditingShortcuts.bindPlatformAccelerators(accelerators, "H", () -> Platform.runLater(this::replaceNextMatch));

        stage.addEventFilter(KeyEvent.KEY_PRESSED, mdTextArea::handleSearchNavigationKey);
        editingShortcutsInstalled = true;
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
        if (stage != null) {
            stage.setCustomTitle(buildWindowTitle());
        }
        updateStatusDisplay();
    }

    /** Wie Legacy-Editor: 📄 Dateiname, bei Änderungen ein Stern. */
    private String buildWindowTitle() {
        String base = (loadedChapterName != null && !loadedChapterName.isBlank())
                ? "📄 " + loadedChapterName
                : "Kapitel-Editor";
        return dirty ? base + " *" : base;
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
        showSaveDialog();
    }

    private void refreshDirtyState() {
        if (editor != null) {
            updateDirtyFromContent(editor.getText());
        }
    }

    private void showSaveDialog() {
        refreshDirtyState();
        if (!dirty) {
            finishCloseEditor();
            return;
        }
        Optional<UnsavedChangesChoice> result = promptUnsavedChangesDialog(
                "Möchten Sie die Änderungen speichern?",
                "Speichern",
                "Verwerfen");
        if (result.isEmpty()) {
            return;
        }
        UnsavedChangesChoice choice = result.get();
        if (choice.discard()) {
            setDirty(false);
            finishCloseEditor();
        } else if (choice.save() && performSave()) {
            finishCloseEditor();
        }
    }

    private boolean showSaveDialogForNavigation() {
        refreshDirtyState();
        if (!dirty) {
            return true;
        }
        Optional<UnsavedChangesChoice> result = promptUnsavedChangesDialog(
                "Möchten Sie die Änderungen speichern, bevor Sie zum nächsten Kapitel wechseln?",
                "Speichern & Weitermachen",
                "Verwerfen & Weitermachen");
        if (result.isEmpty()) {
            return false;
        }
        UnsavedChangesChoice choice = result.get();
        if (choice.discard()) {
            setDirty(false);
            return true;
        }
        if (choice.save()) {
            return performSave();
        }
        return false;
    }

    private record UnsavedChangesChoice(boolean save, boolean discard) {
    }

    private Optional<UnsavedChangesChoice> promptUnsavedChangesDialog(String contentHint, String saveLabel,
            String discardLabel) {
        CustomAlert alert = new CustomAlert(Alert.AlertType.CONFIRMATION, "Ungespeicherte Änderungen");
        alert.setHeaderText("Die Datei hat ungespeicherte Änderungen.");
        alert.setContentText(contentHint);
        alert.applyTheme(themeIndex);

        ButtonType saveButton = new ButtonType(saveLabel);
        ButtonType discardButton = new ButtonType(discardLabel);
        ButtonType diffButton = new ButtonType("🔍 Diff anzeigen");
        ButtonType cancelButton = new ButtonType("Abbrechen");
        alert.setButtonTypes(saveButton, discardButton, diffButton, cancelButton);

        Optional<ButtonType> result = alert.showAndWait(stage);
        if (result.isEmpty()) {
            return Optional.empty();
        }
        ButtonType chosen = result.get();
        if (chosen == diffButton) {
            showDiffForUnsavedChanges();
            return Optional.empty();
        }
        if (chosen == cancelButton) {
            return Optional.empty();
        }
        if (chosen == discardButton) {
            return Optional.of(new UnsavedChangesChoice(false, true));
        }
        if (chosen == saveButton) {
            return Optional.of(new UnsavedChangesChoice(true, false));
        }
        return Optional.empty();
    }

    private boolean performSave() {
        try {
            saveLoadedChapter();
            refreshDirtyState();
            return !dirty;
        } catch (Exception e) {
            updateStatusError("Speichern fehlgeschlagen: " + e.getMessage());
            return false;
        }
    }

    private void finishCloseEditor() {
        cancelLanguageToolChecks();
        if (mainController != null && getEditorKey() != null) {
            mainController.unregisterChapterEditor(getEditorKey());
        }
        stage.close();
    }

    private void showDiffForUnsavedChanges() {
        if (mainController == null || loadedDocxFile == null || loadedChapterFile == null) {
            updateStatusError("Diff nicht verfügbar (keine DOCX/MD-Datei)");
            return;
        }
        DocxFile chapterFile = new DocxFile(loadedDocxFile);
        mainController.showDetailedDiffDialog(
                chapterFile, loadedChapterFile, null, DocxProcessor.OutputFormat.MARKDOWN, this);
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
        if (btnToggleSidebar != null) {
            applyThemeToNode(btnToggleSidebar, themeIndex);
            SidebarToggleButtonSupport.updateAppearance(btnToggleSidebar, sidebarExpanded, themeIndex);
        }
        if (btnToggleHostToolbar != null) {
            applyThemeToNode(btnToggleHostToolbar, themeIndex);
            HostToolbarToggleSupport.updateAppearance(btnToggleHostToolbar, hostToolbarExpanded, themeIndex);
        }
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
    public int getEditorFontSizePx() {
        if (mdTextArea != null) {
            return (int) Math.round(mdTextArea.getEditorFontSize());
        }
        return (int) Math.round(preferences.getDouble(PREF_FONT_SIZE, 16.0));
    }

    @Override
    public String getEditorFontFamily() {
        return preferences.get(PREF_FONT_FAMILY, "Segoe UI");
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
    public int getSelectionStart() {
        return editor.getSelectionStart();
    }

    @Override
    public int getSelectionEnd() {
        return editor.getSelectionEnd();
    }

    @Override
    public boolean hasTextSelection() {
        return editor.hasTextSelection();
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
        editor.insertTextPreserveCaret(text);
    }

    private void wireSelectionRevisionAgentAction() {
        editor.setSelectionRevisionAgentAction(() -> {
            if (chapterAgentSupport != null && chapterAgentSupport.isAvailable()) {
                chapterAgentSupport.runSelectionRevisionFromContextMenu();
            }
        });
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
        } else if (!enabled && chapterAgentSupport != null) {
            chapterAgentSupport.restoreUserPanelVisibility();
        } else if (!enabled && chapterAgentSupport == null && mainSplitPane != null) {
            chapterAgentSupport = new ChapterAgentSupport(this, mainSplitPane);
            chapterAgentSupport.setSceneOutlineWindow(sceneOutlineWindow);
            chapterAgentSupport.setupIfEnabled();
            wireSelectionRevisionAgentAction();
        }
        syncAgentsToggleButton();
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
        stage.setOnHidden(event -> ChapterEditorSplitPreferences.save(mainSplitPane, preferences));
        stage.xProperty().addListener((obs, oldValue, newValue) ->
                PreferencesManager.putWindowPosition(preferences, "prototype_editor_window_x", newValue.doubleValue()));
        stage.yProperty().addListener((obs, oldValue, newValue) ->
                PreferencesManager.putWindowPosition(preferences, "prototype_editor_window_y", newValue.doubleValue()));
        stage.widthProperty().addListener((obs, oldValue, newValue) ->
                PreferencesManager.putWindowWidth(preferences, "prototype_editor_window_width", newValue.doubleValue()));
        stage.heightProperty().addListener((obs, oldValue, newValue) ->
                PreferencesManager.putWindowHeight(preferences, "prototype_editor_window_height", newValue.doubleValue()));
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
