package com.manuskript;

import com.manuskript.agent.IdiomReviewSupport;
import com.manuskript.agent.SelectionRevisionSupport;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.image.Image;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import javafx.stage.Popup;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.util.Duration;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Experimenteller Manuskript-Editor ohne TextArea/RichTextFX.
 * Rendering, Textmodell, Selektion und Undo/Redo liegen hier komplett in eigener Hand.
 */
public class ManuskriptTextEditor extends Region {

    /** Strg (Windows/Linux) bzw. Strg oder Cmd (macOS) – wie {@link EditorWindow}. */
    public static boolean isEditingShortcutKey(KeyEvent event) {
        return EditingShortcuts.isShortcutDown(event);
    }

    private static final double GUTTER_WIDTH = 48;
    private static final double TEXT_PADDING = 8;
    /** Rechter Textabstand (zusammen mit {@link #TEXT_PADDING} = 24 px Gesamtrand wie Bilder/HR). */
    private static final double TEXT_RIGHT_PADDING = 14;
    private static final double CARET_WIDTH = 1.5;
    private static final String CARET_COLOR = "#ff3333";
    private static final double MIN_CHAR_WIDTH = 4.0;
    /** Toleranz für Klick-Hit-Tests (Canvas-Glyphen können etwas breiter als gemessen sein). */
    private static final double CHAR_HIT_SLOP = 2.0;
    private static final double DEFAULT_LINE_SPACING = 1.55;
    /** Verhältnis Baseline zu Zeilenhöhe bei Standard-Abstand 1,55. */
    private static final double BASELINE_TO_LINE_RATIO = 1.15 / DEFAULT_LINE_SPACING;
    /** Leichter Überhang, damit Canvas-Glyphen nicht neben dem Hintergrund enden. */
    private static final double BACKGROUND_BLEED = 1.5;
    private static final int MAX_UNDO = 250;
    /** Tipp-/Lösch-Bursts werden zu einem Undo-Schritt zusammengefasst (Pause oder Wortgrenze beendet). */
    private static final long UNDO_COALESCE_MS = 500;
    /** Inline-Markup erst nach Tipp-Pause neu parsen (entlastet UI bei langen Kapiteln). */
    private static final Duration AUTO_RULE_DELAY = Duration.millis(550);
    private static final Duration AUTO_RULE_DELAY_LARGE_DOC = Duration.millis(900);
    private static final int LARGE_DOC_AUTO_MARK_THRESHOLD = 20_000;
    private static final int MAX_INCREMENTAL_AUTO_MARK_SPAN = 6_000;
    /** Inline-Formatierung pro Zeile statt über den ganzen Roman. */
    private static final String INLINE_NO_NL = "[^\\n]+?";

    private enum UndoEditKind {
        TYPE, DELETE, OTHER
    }
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);
    /** Entspricht .heading-1 … .heading-6 in manuskript.css bei 16px Fließtext. */
    private static final double[] HEADING_FONT_SIZES_AT_16 = {24, 20, 18, 16, 15, 14};
    private static final double BODY_FONT_REFERENCE = 16.0;
    private static final String[] HEADING_COLORS_LIGHT = {"#2c3e50", "#34495e", "#7f8c8d", "#95a5a6", "#bdc3c7", "#bdc3c7"};
    private static final String[] HEADING_COLORS_DARK = {"#ffffff", "#e9ecef", "#ced4da", "#adb5bd", "#868e96", "#868e96"};
    private static final Pattern BLOCKQUOTE_PATTERN = Pattern.compile("^>\\s*(.*)$", Pattern.MULTILINE);
    /** Sichtbarer Einzug für Zitatzeilen (wie padding-left in der HTML-Vorschau). */
    private static final double BLOCKQUOTE_INDENT_PX = 28.0;
    private static final double LIST_INDENT_PX = 22.0;
    /** Markdown-Quell-Einzug pro Ebene (2 Leerzeichen, wie DOCX-Export). */
    private static final String SOURCE_LIST_INDENT = "  ";
    private static final double TABLE_CELL_PADDING_PX = 10.0;
    private static final double TABLE_SEPARATOR_LINE_HEIGHT = 8.0;
    private static final Color LINK_TEXT_COLOR = Color.web("#007bff");
    private static final Pattern LINK_PATTERN = Pattern.compile("\\[([^\\]\\n]+)\\]\\(([^)\\s]+)\\)");
    private static final Pattern CENTER_TAG_PATTERN = Pattern.compile(
            "(?is)<(?:c|center)>([\\s\\S]*?)</(?:c|center)>");
    /** Zeilenumbruch-Tags (mit/ohne Schrägstrich, optional Leerzeichen). */
    private static final Pattern BR_TAG_PATTERN = Pattern.compile("(?i)<br\\s*/?>");
    /** Horizontale Trennlinie: ---, ***, ___ (eigene Zeile). */
    private static final Pattern HORIZONTAL_RULE_LINE = Pattern.compile("^(?:\\*{3,}|-{3,}|_{3,})\\s*$");
    private static final double HORIZONTAL_RULE_MIN_HEIGHT = 28.0;
    private static final Color MARK_HIGHLIGHT_COLOR = Color.web("#ffeb3b");
    private static final Color BLOCKQUOTE_TEXT_COLOR = Color.web("#999999");
    private static final double BIG_FONT_SCALE = 1.2;
    private static final double SMALL_FONT_SCALE = 0.8;
    /** Hoch-/Tiefstellung typografisch kleiner (≈ 75 % der Grundschrift). */
    private static final double SUP_SUB_FONT_SCALE = 0.75;
    /** Breite der vertikalen Scrollbar (CSS: editor-scrollbar.css). */
    private static final double SCROLLBAR_WIDTH = 18;

    private final Canvas canvas = new Canvas();
    private final ScrollBar verticalScrollBar = new ScrollBar();
    /** Unsichtbarer Fokus-Anker – Canvas-Regionen bekommen sonst oft keine Tastatur-Events. */
    private final Region keyboardProxy = new Region();
    private final StringBuilder text = new StringBuilder();
    private final List<StyleRange> styles = new ArrayList<>();
    private final List<MarkedArea> markedAreas = new ArrayList<>();
    private final List<TextRange> hiddenMarkupRanges = new ArrayList<>();
    private final List<TextRange> blockStructureHiddenRanges = new ArrayList<>();
    private final List<TextRange> hiddenImageBlockRanges = new ArrayList<>();
    private final List<ParsedImageBlock> imageBlocks = new ArrayList<>();
    private final List<ParsedHorizontalRule> horizontalRules = new ArrayList<>();
    private final List<TextRange> hiddenHorizontalRuleRanges = new ArrayList<>();
    private boolean editable = true;
    private final List<AutoRule> autoRules = new ArrayList<>();
    private final List<HeadingRange> headingRanges = new ArrayList<>();
    private final Map<Integer, Integer> headingLevelByLineStart = new HashMap<>();
    private final Set<Integer> centerLineStarts = new HashSet<>();
    private final Set<Integer> blockquoteLineStarts = new HashSet<>();
    private final Map<Integer, MarkdownBlockSupport.ListLineInfo> listLineInfoByStart = new HashMap<>();
    private final Map<Integer, Integer> orderedDisplayNumberByLineStart = new HashMap<>();
    private final Map<Integer, MarkdownBlockSupport.TableRow> tableRowByLineStart = new HashMap<>();
    private final Map<Integer, Integer> tableBlockIdByLineStart = new HashMap<>();
    private final Map<Integer, double[]> tableColumnWidthsByBlockId = new HashMap<>();
    private final Set<Integer> tableLineStarts = new HashSet<>();
    private final Set<Integer> tableSeparatorLineStarts = new HashSet<>();
    private final List<MarkdownBlockSupport.TableBlock> tableBlocks = new ArrayList<>();
    private final List<MarkdownBlockSupport.CodeFenceBlock> codeFenceBlocks = new ArrayList<>();
    private final Set<Integer> codeFenceContentLineStarts = new HashSet<>();
    private Color[] headingColors = new Color[6];
    private final ArrayDeque<Snapshot> undoStack = new ArrayDeque<>();
    private final ArrayDeque<Snapshot> redoStack = new ArrayDeque<>();
    private UndoEditKind lastUndoEditKind;
    private long lastUndoEditTimeMs;
    private final PauseTransition autoRuleDelay = new PauseTransition(AUTO_RULE_DELAY);
    private final PauseTransition widthLayoutDelay = new PauseTransition(Duration.millis(120));
    private final PauseTransition imageSyncDelay = new PauseTransition(Duration.millis(400));
    private final Text measuringText = new Text();
    private final Map<MeasureKey, Double> textWidthCache = new HashMap<>();
    private final Map<String, Image> imageCache = new HashMap<>();
    private final ContextMenu languageToolContextMenu = new ContextMenu();
    private final ContextMenu editorContextMenu = new ContextMenu();
    private ChapterRewriteContextActions contextMenuRewriteActions;
    private Runnable selectionRevisionAgentAction;
    private Runnable idiomReviewAgentAction;
    private int contextMenuThemeIndex;
    private final Popup languageToolHoverPopup = new Popup();
    private final Label languageToolHoverMessage = new Label();
    private final Label languageToolHoverSuggestion = new Label();
    private List<LanguageToolService.Match> currentLanguageToolMatches = new ArrayList<>();
    private LanguageToolDictionary languageToolDictionary;
    private Runnable onLanguageToolMatchesChanged;
    private LanguageToolService.Match hoveredLanguageToolMatch;
    private EventHandler<MouseEvent> contextMenuOutsideClickFilter;
    private EventHandler<MouseEvent> sceneSelectionDragFilter;
    private EventHandler<MouseEvent> sceneSelectionReleaseFilter;
    private Timeline selectionAutoScrollTimeline;
    private boolean mouseSelectionDragActive;
    private double lastSelectionDragViewportX = Double.NaN;
    private double lastSelectionDragViewportY = Double.NaN;
    private int selectionAutoScrollDirection;
    private Consumer<String> textChangeListener;
    private Runnable selectionChangeListener;
    private int lastNotifiedCaret = -1;
    private int lastNotifiedAnchor = -2;
    private boolean textChangeNotificationPending;
    private boolean forceFullAutoMarkRebuild = true;
    private boolean preserveReadingViewportOnNextRebuild;
    private int autoMarkDirtyStart = Integer.MAX_VALUE;
    private int autoMarkDirtyEnd = 0;

    private String fontFamily = "Segoe UI";
    private String monospaceFontFamily = "Monospace";
    private double fontSize = 16.0;
    private double lineSpacing = DEFAULT_LINE_SPACING;
    /** Zusätzliche Höhe für ausgeblendete einzeilige Leerzeile zwischen Absätzen (WYSIWYG). */
    private double paragraphSpacingPx = 10;
    private boolean justifyText = false;
    private int caret = 0;
    private int anchor = 0;
    private double scrollTop = 0.0;
    private boolean wrapText = true;
    private boolean applyingSnapshot = false;
    private int suppressEnterCount = 0;
    private boolean renderMarkupHidden = true;
    private boolean showLineNumbers = true;
    private int quoteStyleIndex = 0;
    private Color editorBackgroundColor = Color.WHITE;
    private Color codeBlockBackgroundColor = Color.web("#f0f0f0");
    private Color tableBlockBackgroundColor = Color.web("#e8ecf0");
    private Color inlineCodeBackgroundColor = Color.web("#f4f4f4");
    private Color tableBorderColor = Color.web("#ced4da");
    private Color gutterBackgroundColor = Color.web("#f5f5f5");
    private Color gutterTextColor = Color.web("#9e9e9e");
    private Color editorTextColor = Color.web("#1f1f1f");
    private Color selectionColor = Color.web("#9ec9ff");
    private Color caretColor = Color.web(CARET_COLOR);
    private double preferredCaretX = Double.NaN;
    private File imageMdDirectory;
    private File imageProjectDirectory;
    private int bulkUpdateDepth = 0;
    private List<VisualLine> cachedVisualLines;
    private double cachedVisualLinesWidth = -1;
    private int cachedVisualLinesTextLength = -1;
    private int cachedVisualLinesHiddenBlocks = -1;
    private int cachedVisualLinesHeadingCount = -1;
    private int cachedVisualLinesHorizontalRules = -1;
    private int cachedVisualLinesBlockquotes = -1;
    private int cachedVisualLinesLists = -1;
    private int cachedVisualLinesTables = -1;
    private int cachedVisualLinesCodeFences = -1;
    private boolean cachedVisualLinesMarkupHidden;
    private double cachedVisualLinesFontSize = -1;
    private double cachedVisualLinesLineSpacing = -1;
    private double cachedVisualLinesParagraphSpacing = -1;
    private boolean cachedVisualLinesJustifyText;
    /** Letzte Zeile jeder Leerzeilen-Gruppe (n Zeilen → n−1 sichtbare Abstände). */
    private boolean[] collapsedBlankLineAtRunEnd;
    /** Einzeilige Leerzeile zwischen zwei Textabsätzen (Markdown {@code \\n\\n}). */
    private boolean[] paragraphGapLine;
    /** Zusatzbreite pro Wortzwischenraum je Zeile (0 = kein Blocksatz). */
    private double[] justifyExtraPerGap;
    /** Vertikale Metriken zum schnellen Scrollen/Hit-Testing (mit {@link #cachedVisualLines}). */
    private double[] cachedLineContentY;
    private double[] cachedLineSegmentHeight;
    private double cachedTotalContentHeight = -1;

    private static final double SELECTION_AUTO_SCROLL_EDGE_PX = 20;
    private static final double SELECTION_AUTO_SCROLL_STEP_PX = 16;
    private static final Duration SELECTION_AUTO_SCROLL_INTERVAL = Duration.millis(45);

    private boolean sceneAcceleratorsInstalled;

    public ManuskriptTextEditor() {
        getStyleClass().add("manuskript-text-editor");
        installBundledScrollbarStyles();
        initHeadingColors(false);
        setupKeyboardProxy();
        getChildren().addAll(keyboardProxy, canvas, verticalScrollBar);
        setFocusTraversable(false);
        setPadding(new Insets(0));

        verticalScrollBar.setPrefWidth(SCROLLBAR_WIDTH);
        verticalScrollBar.setMinWidth(SCROLLBAR_WIDTH);
        verticalScrollBar.setMaxWidth(SCROLLBAR_WIDTH);

        canvas.setFocusTraversable(true);
        canvas.addEventFilter(KeyEvent.KEY_PRESSED, this::handleKeyPressed);
        canvas.addEventFilter(KeyEvent.KEY_TYPED, this::handleKeyTyped);
        canvas.focusedProperty().addListener((obs, wasFocused, focused) -> render());
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                installSceneAccelerators(newScene);
            }
        });

        verticalScrollBar.setOrientation(javafx.geometry.Orientation.VERTICAL);
        verticalScrollBar.valueProperty().addListener((obs, oldValue, newValue) -> {
            scrollTop = newValue.doubleValue();
            render();
        });

        widthProperty().addListener((obs, oldValue, newValue) -> {
            if (isBulkUpdating()) {
                return;
            }
            if (Math.abs(newValue.doubleValue() - oldValue.doubleValue()) < 0.5) {
                return;
            }
            invalidateLayoutCaches();
            widthLayoutDelay.playFromStart();
        });
        heightProperty().addListener((obs, oldValue, newValue) -> {
            if (!isBulkUpdating()) {
                render();
            }
        });

        widthLayoutDelay.setOnFinished(e -> {
            if (isBulkUpdating()) {
                return;
            }
            syncBlockLayoutFromMarkdown();
            refreshLayout();
        });

        setupLanguageToolHoverPopup();
        setupMouseHandling();

        monospaceFontFamily = resolveMonospaceFontFamily();
        autoRuleDelay.setOnFinished(e -> rebuildAutoMarks());
        imageSyncDelay.setOnFinished(e -> {
            syncBlockLayoutFromMarkdown();
            updateScrollBar();
            render();
        });
        setText("");
    }

    private void setupKeyboardProxy() {
        keyboardProxy.setFocusTraversable(false);
        keyboardProxy.setMouseTransparent(true);
        keyboardProxy.setMinSize(1, 1);
        keyboardProxy.setPrefSize(1, 1);
        keyboardProxy.setMaxSize(1, 1);
        keyboardProxy.setOpacity(0);
        verticalScrollBar.focusedProperty().addListener((obs, wasFocused, focused) -> {
            if (focused) {
                requestInputFocus();
            }
        });
    }

    /** Verhindert kurzzeitig Enter/Zeilenumbruch (z. B. nach Suche im Suchfeld). */
    public void suppressEnterKey(int eventCount) {
        suppressEnterCount = Math.max(suppressEnterCount, Math.max(1, eventCount));
    }

    public void requestInputFocus() {
        canvas.requestFocus();
    }

    private void ensureInputFocusAfterMouseEvent() {
        Platform.runLater(() -> {
            requestInputFocus();
            render();
        });
    }

    private void placeCaretAt(double x, double contentY, boolean extendSelection) {
        int offset = offsetAt(x, contentY);
        caret = normalizeCaretOffset(offset, true);
        preferredCaretX = Double.NaN;
        if (!extendSelection) {
            anchor = caret;
        }
        ensureCaretVisible();
        render();
    }

    public void setEditable(boolean editable) {
        this.editable = editable;
    }

    public boolean isEditable() {
        return editable;
    }

    @Override
    protected void layoutChildren() {
        double width = Math.max(0, getWidth());
        double height = Math.max(0, getHeight());
        double barWidth = SCROLLBAR_WIDTH;
        canvas.setLayoutX(0);
        canvas.setLayoutY(0);
        double newCanvasWidth = Math.max(0, width - barWidth);
        if (Math.abs(canvas.getWidth() - newCanvasWidth) > 0.5) {
            invalidateLayoutCaches();
        }
        canvas.setWidth(newCanvasWidth);
        canvas.setHeight(height);
        keyboardProxy.resizeRelocate(0, 0, 1, 1);
        verticalScrollBar.resizeRelocate(Math.max(0, width - barWidth), 0, barWidth, height);
        updateScrollBar();
        render();
    }

    public String getText() {
        return text.toString();
    }

    public boolean hasTextSelection() {
        return hasSelection();
    }

    public String getSelectedText() {
        return selectedText();
    }

    public int getCaretPosition() {
        return caret;
    }

    public void selectRange(int start, int end) {
        int length = text.length();
        int safeStart = Math.max(0, Math.min(length, start));
        int safeEnd = Math.max(safeStart, Math.min(length, end));
        anchor = safeStart;
        caret = safeEnd;
        preferredCaretX = Double.NaN;
        ensureCaretVisibleAfterInsert();
        render();
    }

    /** Setzt den Cursor auf {@code offset} und scrollt den Treffer möglichst in die Bildschirmmitte. */
    public void revealMatchAt(int start, int end) {
        int length = text.length();
        int safeStart = Math.max(0, Math.min(length, start));
        int safeEnd = Math.max(safeStart, Math.min(length, end));
        anchor = safeStart;
        caret = safeEnd;
        preferredCaretX = Double.NaN;
        updateScrollBar();
        scrollRangeToViewportCenter(safeStart, safeEnd);
        render();
    }

    public void setOnTextChanged(Consumer<String> listener) {
        textChangeListener = listener;
    }

    public void setOnSelectionChanged(Runnable listener) {
        selectionChangeListener = listener;
    }

    public void setContextMenuRewriteActions(ChapterRewriteContextActions actions, int themeIndex) {
        contextMenuRewriteActions = actions;
        contextMenuThemeIndex = themeIndex;
        EditorDialogThemes.styleContextMenu(editorContextMenu, themeIndex);
    }

    public void setSelectionRevisionAgentAction(Runnable action) {
        selectionRevisionAgentAction = action;
    }

    public void setIdiomReviewAgentAction(Runnable action) {
        idiomReviewAgentAction = action;
    }

    public int getSelectionStart() {
        return selectionStart();
    }

    public int getSelectionEnd() {
        return selectionEnd();
    }

    public void setQuoteStyleIndex(int styleIndex) {
        if (styleIndex >= 0 && styleIndex < QuotationMarkSupport.STYLE_COUNT) {
            quoteStyleIndex = styleIndex;
        }
    }

    public int getQuoteStyleIndex() {
        return quoteStyleIndex;
    }

    public void setText(String value) {
        beginBulkUpdate();
        try {
            applyDocumentContent(value, false);
        } finally {
            endBulkUpdate();
        }
    }

    public void loadDocument(String content, File mdDirectory, File projectDirectory) {
        beginBulkUpdate();
        try {
            imageMdDirectory = mdDirectory;
            imageProjectDirectory = projectDirectory;
            applyDocumentContent(content, true);
        } finally {
            endBulkUpdate();
        }
    }

    private void applyDocumentContent(String value, boolean resetScroll) {
        hideLanguageToolHover();
        text.setLength(0);
        text.append(value == null ? "" : value);
        caret = 0;
        anchor = 0;
        styles.clear();
        undoStack.clear();
        redoStack.clear();
        resetUndoCoalesceState();
        invalidateLayoutCaches();
        forceFullAutoMarkRebuild = true;
        preserveReadingViewportOnNextRebuild = true;
        rebuildStructuralMarkdownNow();
        scheduleAutoRuleRebuild();
        imageSyncDelay.stop();
        syncBlockLayoutFromMarkdown();
        if (resetScroll) {
            scrollTop = 0;
            if (Math.abs(verticalScrollBar.getValue()) > 0.01) {
                verticalScrollBar.setValue(0);
            } else {
                scrollTop = 0;
            }
        }
        refreshLayout();
    }

    public void setImageDirectories(File mdDirectory, File projectDirectory) {
        imageMdDirectory = mdDirectory;
        imageProjectDirectory = projectDirectory;
        imageCache.clear();
        if (isBulkUpdating()) {
            return;
        }
        syncBlockLayoutFromMarkdown();
        refreshLayout();
    }

    private void beginBulkUpdate() {
        bulkUpdateDepth++;
    }

    private void endBulkUpdate() {
        bulkUpdateDepth = Math.max(0, bulkUpdateDepth - 1);
        if (bulkUpdateDepth == 0) {
            notifyTextChanged();
        }
    }

    private boolean isBulkUpdating() {
        return bulkUpdateDepth > 0;
    }

    private void invalidateLayoutCaches() {
        cachedVisualLines = null;
        cachedVisualLinesWidth = -1;
        cachedVisualLinesTextLength = -1;
        cachedVisualLinesHiddenBlocks = -1;
        cachedVisualLinesHeadingCount = -1;
        cachedVisualLinesBlockquotes = -1;
        cachedVisualLinesFontSize = -1;
        cachedVisualLinesLineSpacing = -1;
        cachedVisualLinesParagraphSpacing = -1;
        collapsedBlankLineAtRunEnd = null;
        paragraphGapLine = null;
        justifyExtraPerGap = null;
        cachedLineContentY = null;
        cachedLineSegmentHeight = null;
        cachedTotalContentHeight = -1;
    }

    private void refreshLayout() {
        updateScrollBar();
        render();
    }

    public void insertText(String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        replaceSelection(value);
    }

    /** Fügt Text an der Caret-/Selektionsposition ein, ohne den Cursor zu verschieben. */
    public void insertTextPreserveCaret(String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        pushUndoCoalesced(classifyUndoKind(value), value);
        int start = selectionStart();
        int end = selectionEnd();
        ViewportAnchor viewportAnchor = captureReadingViewportAnchor();
        String beforeContent = text.toString();
        text.replace(start, end, value);
        int inserted = value.length();
        adjustRangesForReplace(start, end, inserted);
        caret = normalizeCaretOffset(start, true);
        anchor = caret;
        preferredCaretX = Double.NaN;
        int scrollSync = mapOffsetThroughTextChange(beforeContent, text.toString(), viewportAnchor.offset());
        afterTextChanged(viewportAnchor, scrollSync, false);
    }

    public void replaceSelection(String replacement) {
        pushUndoCoalesced(classifyUndoKind(replacement), replacement);
        int start = selectionStart();
        int end = selectionEnd();
        ViewportAnchor viewportAnchor = viewportAnchorForReplacement(replacement, start);
        text.replace(start, end, replacement == null ? "" : replacement);
        int inserted = replacement == null ? 0 : replacement.length();
        adjustRangesForReplace(start, end, inserted);
        caret = normalizeCaretOffset(start + inserted, true);
        anchor = caret;
        preferredCaretX = Double.NaN;
        afterTextChanged(viewportAnchor, caret, true);
    }

    public void replaceRange(int start, int end, String replacement) {
        replaceRange(start, end, replacement, true);
    }

    /**
     * Ersetzt einen Textbereich und mappt Caret/Anker sowie Scroll-Position durch die Änderung.
     */
    public void replaceRange(int start, int end, String replacement, boolean preserveCaretAndViewport) {
        int safeStart = Math.max(0, Math.min(text.length(), start));
        int safeEnd = Math.max(safeStart, Math.min(text.length(), end));
        String oldContent = text.toString();
        String newSegment = replacement == null ? "" : replacement;
        String afterContent = buildTextAfterReplace(oldContent, safeStart, safeEnd, newSegment);

        ViewportAnchor viewportAnchor = preserveCaretAndViewport ? captureReadingViewportAnchor() : null;
        int scrollSyncOffset = preserveCaretAndViewport
                ? mapOffsetThroughTextChange(oldContent, afterContent, viewportAnchor.offset())
                : safeStart + newSegment.length();
        int mappedCaret = preserveCaretAndViewport
                ? mapOffsetThroughTextChange(oldContent, afterContent, caret)
                : safeStart + newSegment.length();
        int mappedAnchor = preserveCaretAndViewport
                ? mapOffsetThroughTextChange(oldContent, afterContent, anchor)
                : mappedCaret;

        pushUndoCoalesced(UndoEditKind.OTHER, newSegment);
        text.replace(safeStart, safeEnd, newSegment);
        adjustRangesForReplace(safeStart, safeEnd, newSegment.length());
        caret = normalizeCaretOffset(mappedCaret, true);
        anchor = normalizeCaretOffset(mappedAnchor, true);
        preferredCaretX = Double.NaN;
        if (preserveCaretAndViewport) {
            preserveReadingViewportOnNextRebuild = true;
        }
        afterTextChanged(viewportAnchor, scrollSyncOffset, false);
    }

    /**
     * Ersetzt den gesamten Dokumenttext, behält Caret-Position (inhaltlich gemappt) und Viewport bei.
     * Für z. B. Anführungszeichen-Konvertierung ohne Scroll-Sprung.
     */
    public void replaceAllTextPreservingCaretAndViewport(String newContent) {
        String oldContent = text.toString();
        String replacement = newContent == null ? "" : newContent;
        if (oldContent.equals(replacement)) {
            return;
        }
        ViewportAnchor viewportAnchor = captureReadingViewportAnchor();
        int mappedCaret = mapOffsetThroughTextChange(oldContent, replacement, caret);
        int mappedAnchor = mapOffsetThroughTextChange(oldContent, replacement, anchor);
        pushUndoCoalesced(UndoEditKind.OTHER, replacement);
        text.replace(0, oldContent.length(), replacement);
        adjustRangesForReplace(0, oldContent.length(), replacement.length());
        caret = normalizeCaretOffset(mappedCaret, true);
        anchor = normalizeCaretOffset(mappedAnchor, true);
        preferredCaretX = Double.NaN;
        int scrollSync = mapOffsetThroughTextChange(oldContent, replacement, viewportAnchor.offset());
        preserveReadingViewportOnNextRebuild = true;
        afterTextChanged(viewportAnchor, scrollSync, false);
    }

    private static UndoEditKind classifyUndoKind(String replacement) {
        if (replacement == null || replacement.isEmpty()) {
            return UndoEditKind.DELETE;
        }
        if (replacement.length() > 1) {
            return UndoEditKind.OTHER;
        }
        char c = replacement.charAt(0);
        if (c == '\n' || c == '\r' || c == '\t') {
            return UndoEditKind.OTHER;
        }
        return UndoEditKind.TYPE;
    }

    private static String buildTextAfterReplace(String content, int start, int end, String replacement) {
        return content.substring(0, start) + replacement + content.substring(end);
    }

    static int mapOffsetThroughTextChange(String before, String after, int offset) {
        return TextChangeOffsetMapper.mapOffsetThroughTextChange(before, after, offset);
    }

    public void deleteSelectionOrPrevious() {
        if (hasSelection()) {
            if (deleteImageBlockAtCaret()) {
                return;
            }
            replaceSelection("");
            return;
        }
        if (deleteImageBlockAtCaret()) {
            return;
        }
        if (caret <= 0) {
            return;
        }
        pushUndoCoalesced(UndoEditKind.DELETE, "");
        int caretBefore = caret;
        ViewportAnchor viewportAnchor = captureCaretViewportAnchor(caretBefore);
        text.delete(caret - 1, caret);
        adjustRangesForReplace(caret - 1, caret, 0);
        caret--;
        anchor = caret;
        preferredCaretX = Double.NaN;
        afterTextChanged(viewportAnchor, caret, true);
    }

    public void deleteSelectionOrNext() {
        if (hasSelection()) {
            if (deleteImageBlockAtCaret()) {
                return;
            }
            replaceSelection("");
            return;
        }
        if (deleteImageBlockAtCaret()) {
            return;
        }
        if (caret >= text.length()) {
            return;
        }
        pushUndoCoalesced(UndoEditKind.DELETE, "");
        int caretBefore = caret;
        ViewportAnchor viewportAnchor = captureCaretViewportAnchor(caretBefore);
        text.delete(caret, caret + 1);
        adjustRangesForReplace(caret, caret + 1, 0);
        anchor = caret;
        preferredCaretX = Double.NaN;
        afterTextChanged(viewportAnchor, caret, true);
    }

    public void undo() {
        if (undoStack.isEmpty()) {
            return;
        }
        redoStack.push(snapshot());
        applySnapshot(undoStack.pop());
        resetUndoCoalesceState();
    }

    public void redo() {
        if (redoStack.isEmpty()) {
            return;
        }
        undoStack.push(snapshot());
        applySnapshot(redoStack.pop());
        resetUndoCoalesceState();
    }

    public void selectAll() {
        anchor = 0;
        caret = text.length();
        ensureCaretVisible();
        render();
    }

    public List<SearchMatch> searchAll(String query, boolean regex, boolean caseSensitive) {
        List<SearchMatch> result = new ArrayList<>();
        if (query == null || query.isEmpty()) {
            return result;
        }
        if (regex) {
            try {
                Pattern pattern = Pattern.compile(query, caseSensitive ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                Matcher matcher = pattern.matcher(text);
                while (matcher.find()) {
                    result.add(new SearchMatch(matcher.start(), matcher.end()));
                }
            } catch (PatternSyntaxException ignored) {
                return result;
            }
            return result;
        }

        String haystack = caseSensitive ? text.toString() : text.toString().toLowerCase(Locale.ROOT);
        String needle = caseSensitive ? query : query.toLowerCase(Locale.ROOT);
        int pos = 0;
        while ((pos = haystack.indexOf(needle, pos)) >= 0) {
            result.add(new SearchMatch(pos, pos + needle.length()));
            pos += Math.max(1, needle.length());
        }
        return result;
    }

    public int replaceAll(String query, String replacement, boolean regex, boolean caseSensitive) {
        List<SearchMatch> matches = searchAll(query, regex, caseSensitive);
        if (matches.isEmpty()) {
            return 0;
        }
        pushUndoCoalesced(UndoEditKind.OTHER, replacement);
        for (int i = matches.size() - 1; i >= 0; i--) {
            SearchMatch match = matches.get(i);
            text.replace(match.start(), match.end(), replacement == null ? "" : replacement);
            adjustRangesForReplace(match.start(), match.end(), replacement == null ? 0 : replacement.length());
        }
        caret = Math.min(caret, text.length());
        anchor = caret;
        afterTextChanged();
        return matches.size();
    }

    /** Ersetzt genau einen Treffer und liefert dessen Startoffset, oder {@code -1}. */
    public int replaceOne(String query, String replacement, boolean regex, boolean caseSensitive, int matchIndex) {
        List<SearchMatch> matches = searchAll(query, regex, caseSensitive);
        if (matchIndex < 0 || matchIndex >= matches.size()) {
            return -1;
        }
        SearchMatch match = matches.get(matchIndex);
        String value = replacement == null ? "" : replacement;
        pushUndoCoalesced(UndoEditKind.OTHER, value);
        int caretBefore = caret;
        ViewportAnchor viewportAnchor = captureCaretViewportAnchor(caretBefore);
        text.replace(match.start(), match.end(), value);
        adjustRangesForReplace(match.start(), match.end(), value.length());
        caret = match.start() + value.length();
        anchor = caret;
        preferredCaretX = Double.NaN;
        afterTextChanged(viewportAnchor, caret, true);
        return match.start();
    }

    public MarkedArea newMarkedArea() {
        MarkedArea area = new MarkedArea(this);
        markedAreas.add(area);
        render();
        return area;
    }

    public void clearMarkedArea(MarkedArea area) {
        markedAreas.remove(area);
        render();
    }

    public void setLanguageToolDictionary(LanguageToolDictionary dictionary) {
        languageToolDictionary = dictionary;
    }

    public void addAutoRegexStyleRule(String regex, int styleFlags) {
        addAutoRegexStyleRule(regex, 0, styleFlags);
    }

    public void addAutoRegexStyleRule(String regex, int groupIndex, int styleFlags) {
        addAutoRegexStyleRule(regex, groupIndex, styleFlags, null, null, 0);
    }

    public void addAutoRegexStyleRule(String regex, int groupIndex, int styleFlags,
                                      Color textColor, Color backgroundColor, double fontSizeScale) {
        autoRules.add(new AutoRule(regex, Math.max(0, groupIndex), styleFlags,
                textColor, backgroundColor, fontSizeScale));
        forceFullAutoMarkRebuild = true;
        scheduleAutoRuleRebuild();
    }

    private void addAutoRuleEntry(String regex, int groupIndex, int styleFlags) {
        addAutoRuleEntry(regex, groupIndex, styleFlags, null, null, 0);
    }

    private void addAutoRuleEntry(String regex, int groupIndex, int styleFlags,
                                  Color textColor, Color backgroundColor, double fontSizeScale) {
        addAutoRuleEntry(regex, groupIndex, styleFlags, textColor, backgroundColor, fontSizeScale, null);
    }

    private void addAutoRuleEntry(String regex, int groupIndex, int styleFlags,
                                  Color textColor, Color backgroundColor, double fontSizeScale,
                                  String fontFamilyOverride) {
        autoRules.add(new AutoRule(regex, Math.max(0, groupIndex), styleFlags,
                textColor, backgroundColor, fontSizeScale, fontFamilyOverride));
    }

    /** Standard-Formatregeln für den Canvas-Editor (zeilenbasiert, performance-sicher). */
    public void registerDefaultFormatAutoRules() {
        autoRules.clear();
        addAutoRuleEntry("\\*\\*\\*(" + INLINE_NO_NL + ")\\*\\*\\*", 1, MarkedArea.BOLD | MarkedArea.ITALIC);
        addAutoRuleEntry("\\*\\*(" + INLINE_NO_NL + ")\\*\\*", 1, MarkedArea.BOLD);
        addAutoRuleEntry("(?<!\\*)\\*(?!\\*)(" + INLINE_NO_NL + ")(?<!\\*)\\*(?!\\*)", 1, MarkedArea.ITALIC);
        addAutoRuleEntry("__(" + INLINE_NO_NL + ")__", 1, MarkedArea.BOLD);
        addAutoRuleEntry("(?<![_\\w])_(" + INLINE_NO_NL + ")_(?![_\\w])", 1, MarkedArea.ITALIC);
        addAutoRuleEntry("~~(" + INLINE_NO_NL + ")~~", 1, MarkedArea.STRIKETHROUGH);
        addAutoRuleEntry("==(" + INLINE_NO_NL + ")==", 1, 0, null, MARK_HIGHLIGHT_COLOR, 0);
        addAutoRuleEntry("`([^`\\n]+)`", 1, 0, null, inlineCodeBackgroundColor, 0, monospaceFontFamily);
        addAutoRuleEntry("<mark>(" + INLINE_NO_NL + ")</mark>", 1, 0, null, MARK_HIGHLIGHT_COLOR, 0);
        addAutoRuleEntry("<(b|strong)>(" + INLINE_NO_NL + ")</(b|strong)>", 2, MarkedArea.BOLD);
        addAutoRuleEntry("<(i|em)>(" + INLINE_NO_NL + ")</(i|em)>", 2, MarkedArea.ITALIC);
        addAutoRuleEntry("<(s|del)>(" + INLINE_NO_NL + ")</(s|del)>", 2, MarkedArea.STRIKETHROUGH);
        addAutoRuleEntry("<u>(" + INLINE_NO_NL + ")</u>", 1, MarkedArea.UNDERLINE);
        addAutoRuleEntry("<sup>(" + INLINE_NO_NL + ")</sup>", 1, MarkedArea.SUPERSCRIPT);
        addAutoRuleEntry("<sub>(" + INLINE_NO_NL + ")</sub>", 1, MarkedArea.SUBSCRIPT);
        addAutoRuleEntry("<big>(" + INLINE_NO_NL + ")</big>", 1, 0, null, null, BIG_FONT_SCALE);
        addAutoRuleEntry("<small>(" + INLINE_NO_NL + ")</small>", 1, 0, null, null, SMALL_FONT_SCALE);
        addColorAutoRuleEntries();
        forceFullAutoMarkRebuild = true;
        scheduleAutoRuleRebuild();
    }

    private void addColorAutoRuleEntries() {
        addAutoRuleEntry("<red>(" + INLINE_NO_NL + ")</red>", 1, 0, Color.web("#dc3545"), null, 0);
        addAutoRuleEntry("<blue>(" + INLINE_NO_NL + ")</blue>", 1, 0, Color.web("#007bff"), null, 0);
        addAutoRuleEntry("<green>(" + INLINE_NO_NL + ")</green>", 1, 0, Color.web("#28a745"), null, 0);
        addAutoRuleEntry("<yellow>(" + INLINE_NO_NL + ")</yellow>", 1, 0, Color.web("#ffc107"), null, 0);
        addAutoRuleEntry("<purple>(" + INLINE_NO_NL + ")</purple>", 1, 0, Color.web("#6f42c1"), null, 0);
        addAutoRuleEntry("<orange>(" + INLINE_NO_NL + ")</orange>", 1, 0, Color.web("#fd7e14"), null, 0);
        addAutoRuleEntry("<(?:gray|grey)>(" + INLINE_NO_NL + ")</(?:gray|grey)>", 1, 0, Color.web("#6c757d"), null, 0);
        addAutoRuleEntry("<rot>(" + INLINE_NO_NL + ")</rot>", 1, 0, Color.web("#dc3545"), null, 0);
        addAutoRuleEntry("<blau>(" + INLINE_NO_NL + ")</blau>", 1, 0, Color.web("#007bff"), null, 0);
        addAutoRuleEntry("<grün>(" + INLINE_NO_NL + ")</grün>", 1, 0, Color.web("#28a745"), null, 0);
        addAutoRuleEntry("<gelb>(" + INLINE_NO_NL + ")</gelb>", 1, 0, Color.web("#ffc107"), null, 0);
        addAutoRuleEntry("<lila>(" + INLINE_NO_NL + ")</lila>", 1, 0, Color.web("#6f42c1"), null, 0);
        addAutoRuleEntry("<grau>(" + INLINE_NO_NL + ")</grau>", 1, 0, Color.web("#6c757d"), null, 0);
    }

    public void clearAutoRules() {
        autoRules.clear();
        hiddenMarkupRanges.clear();
        forceFullAutoMarkRebuild = true;
        scheduleAutoRuleRebuild();
    }

    public void setShowLineNumbers(boolean showLineNumbers) {
        if (this.showLineNumbers == showLineNumbers) {
            return;
        }
        this.showLineNumbers = showLineNumbers;
        invalidateLayoutCaches();
        preferredCaretX = Double.NaN;
        updateScrollBar();
        render();
    }

    public boolean isShowLineNumbers() {
        return showLineNumbers;
    }

    public void setRenderMarkupHidden(boolean hidden) {
        renderMarkupHidden = hidden;
        textWidthCache.clear();
        invalidateLayoutCaches();
        syncBlockLayoutFromMarkdown();
        forceFullAutoMarkRebuild = true;
        preserveReadingViewportOnNextRebuild = true;
        rebuildStructuralMarkdownNow();
        autoRuleDelay.stop();
        rebuildAutoMarks();
        updateScrollBar();
        render();
    }

    public boolean isRenderMarkupHidden() {
        return renderMarkupHidden;
    }

    private String lektoratMarkBackground = "#fff3e0";
    private boolean darkMarkPalette;

    public void applyTheme(int themeIndex) {
        getStyleClass().removeAll(
                "theme-dark", "theme-light", "blau-theme", "gruen-theme", "lila-theme", "weiss-theme", "pastell-theme");
        switch (themeIndex) {
            case 0 -> getStyleClass().add("weiss-theme");
            case 2 -> getStyleClass().add("pastell-theme");
            case 3 -> getStyleClass().addAll("theme-dark", "blau-theme");
            case 4 -> getStyleClass().addAll("theme-dark", "gruen-theme");
            case 5 -> getStyleClass().addAll("theme-dark", "lila-theme");
            default -> getStyleClass().add("theme-dark");
        }
        switch (themeIndex) {
            case 1 -> setEditorTheme(themeIndex, "#1a1a1a", "#2d2d2d", "#9ca3af", "#ffffff", "#375a7f");
            case 2 -> setEditorTheme(themeIndex, "#f3e5f5", "#e1bee7", "#6b4f6b", "#000000", "#c7a6d8");
            case 3 -> setEditorTheme(themeIndex, "#1e3a8a", "#172554", "#bfdbfe", "#ffffff", "#3b82f6");
            case 4 -> setEditorTheme(themeIndex, "#064e3b", "#022c22", "#bbf7d0", "#ffffff", "#10b981");
            case 5 -> setEditorTheme(themeIndex, "#581c87", "#3b0764", "#e9d5ff", "#ffffff", "#8b5cf6");
            default -> setEditorTheme(themeIndex, "#ffffff", "#f5f5f5", "#9e9e9e", "#1f1f1f", "#9ec9ff");
        }
    }

    private void setEditorTheme(int themeIndex, String background, String gutter, String gutterText,
                                String textColor, String selection) {
        editorBackgroundColor = Color.web(background);
        gutterBackgroundColor = Color.web(gutter);
        gutterTextColor = Color.web(gutterText);
        editorTextColor = Color.web(textColor);
        selectionColor = Color.web(selection);
        caretColor = Color.web(CARET_COLOR);
        boolean dark = isDarkBackground(background);
        initHeadingColors(dark);
        updateBlockStructureColors(themeIndex);
        updateMarkPalette(dark);
        updateLanguageToolHoverTheme();
        registerDefaultFormatAutoRules();
        forceFullAutoMarkRebuild = true;
        scheduleAutoRuleRebuild();
        render();
    }

    /** Code-/Tabellen-Hintergrund und Rahmen passend zum gewählten Theme. */
    private void updateBlockStructureColors(int themeIndex) {
        if (themeIndex == 0 || themeIndex == 2) {
            codeBlockBackgroundColor = Color.web("#f0f0f0");
            tableBlockBackgroundColor = Color.web("#e8ecf0");
            inlineCodeBackgroundColor = Color.web("#f4f4f4");
            tableBorderColor = Color.web("#ced4da");
            return;
        }
        codeBlockBackgroundColor = editorBackgroundColor.interpolate(Color.WHITE, 0.14);
        tableBlockBackgroundColor = editorBackgroundColor.interpolate(Color.WHITE, 0.09);
        inlineCodeBackgroundColor = editorBackgroundColor.interpolate(Color.WHITE, 0.18);
        tableBorderColor = gutterTextColor.interpolate(editorBackgroundColor, 0.55);
    }

    private void updateMarkPalette(boolean dark) {
        darkMarkPalette = dark;
        if (dark) {
            lektoratMarkBackground = "#4a3728";
        } else {
            lektoratMarkBackground = "#fff3e0";
        }
    }

    private static boolean isDarkBackground(String background) {
        Color color = Color.web(background);
        return (color.getRed() + color.getGreen() + color.getBlue()) / 3.0 < 0.45;
    }

    private void initHeadingColors(boolean dark) {
        String[] palette = dark ? HEADING_COLORS_DARK : HEADING_COLORS_LIGHT;
        for (int i = 0; i < headingColors.length; i++) {
            headingColors[i] = Color.web(palette[Math.min(i, palette.length - 1)]);
        }
    }

    public void applyLanguageToolMatches(List<LanguageToolService.Match> matches) {
        currentLanguageToolMatches = matches == null ? new ArrayList<>() : new ArrayList<>(matches);
        markedAreas.removeIf(area -> area.type == MarkedArea.Type.LANGUAGE_TOOL);
        MarkedArea area = newMarkedArea();
        area.name = "LanguageTool";
        area.markType(MarkedArea.Type.LANGUAGE_TOOL);
        if (currentLanguageToolMatches != null) {
            for (LanguageToolService.Match match : currentLanguageToolMatches) {
                area.ranges.add(new TextRange(match.getOffset(), match.getOffset() + match.getLength()));
            }
        }
        area.markUnderline(true);
        area.markUnderlineColor("#d32f2f");
        render();
        notifyLanguageToolMatchesChanged();
    }

    public int getLanguageToolMatchCount() {
        return currentLanguageToolMatches == null ? 0 : currentLanguageToolMatches.size();
    }

    public void setOnLanguageToolMatchesChanged(Runnable listener) {
        onLanguageToolMatchesChanged = listener;
    }

    private void notifyLanguageToolMatchesChanged() {
        if (onLanguageToolMatchesChanged != null) {
            onLanguageToolMatchesChanged.run();
        }
    }

    public void clearLanguageToolMatches() {
        hideLanguageToolHover();
        currentLanguageToolMatches.clear();
        markedAreas.removeIf(area -> area.type == MarkedArea.Type.LANGUAGE_TOOL);
        render();
        notifyLanguageToolMatchesChanged();
    }

    public void clearLektoratMatches() {
        markedAreas.removeIf(area -> area.type == MarkedArea.Type.LEKTORAT);
        render();
    }

    public void applyLektoratMatches(List<LektoratMatch> matches, java.util.function.Consumer<LektoratMatch> onSelect) {
        clearLektoratMatches();
        if (matches == null || matches.isEmpty()) {
            return;
        }
        List<LektoratMatch> ordered = new ArrayList<>(matches);
        ordered.sort(Comparator.comparingInt(LektoratMatch::getOffset));
        List<TextRange> paintRanges = new ArrayList<>();
        List<int[]> occupied = new ArrayList<>();
        for (LektoratMatch match : ordered) {
            int[] span = resolveLektoratMatchSpan(match, occupied);
            if (span == null) {
                continue;
            }
            int start = span[0];
            int end = span[1];
            occupied.add(span);
            paintRanges.add(new TextRange(start, end));
            if (onSelect != null) {
                MarkedArea clickArea = new MarkedArea(this);
                clickArea.markTypeSilent(MarkedArea.Type.LEKTORAT);
                clickArea.addRangeSilent(start, end);
                LektoratMatch captured = match;
                clickArea.onClick(() -> onSelect.accept(captured));
                markedAreas.add(clickArea);
            }
        }
        if (!paintRanges.isEmpty()) {
            MarkedArea paintArea = new MarkedArea(this);
            paintArea.markTypeSilent(MarkedArea.Type.LEKTORAT);
            paintArea.markColorSilent(lektoratMarkBackground);
            for (TextRange range : mergePaintRangesForContinuousHighlight(paintRanges)) {
                paintArea.addRangeSilent(range.start, range.end);
            }
            markedAreas.add(paintArea);
        }
        render();
    }

    /**
     * Scrollt zur aktuellen Position des Treffers (Offsets werden vorher neu aufgelöst).
     */
    public void revealLektoratMatch(LektoratMatch match) {
        int[] span = resolveLektoratMatchSpan(match);
        if (span == null) {
            return;
        }
        revealMatchAt(span[0], span[1]);
    }

    /**
     * Sucht den Originaltext im Editor und aktualisiert Offset/Länge am Match (wie Legacy-Editor).
     * Verhindert optisch verschobene Markierungen nach übernommenen Ersetzungen.
     */
    private int[] resolveLektoratMatchSpan(LektoratMatch match) {
        return resolveLektoratMatchSpan(match, List.of());
    }

    private int[] resolveLektoratMatchSpan(LektoratMatch match, List<int[]> occupiedRanges) {
        int[] span = LektoratMatchLocator.resolveSpan(text.toString(), match, occupiedRanges);
        if (span == null) {
            return null;
        }
        match.setOffset(span[0]);
        match.setLength(span[1] - span[0]);
        return span;
    }

    /**
     * Fasst Markierungen zusammen, die nur durch Leerzeichen oder ausgeblendetes Markup getrennt sind,
     * damit der Hintergrund ohne Lücken gezeichnet werden kann.
     */
    private List<TextRange> mergePaintRangesForContinuousHighlight(List<TextRange> ranges) {
        if (ranges.isEmpty()) {
            return ranges;
        }
        ranges.sort(Comparator.comparingInt(r -> r.start));
        List<TextRange> merged = new ArrayList<>();
        TextRange current = ranges.get(0);
        for (int i = 1; i < ranges.size(); i++) {
            TextRange next = ranges.get(i);
            if (next.start >= current.end && isIgnorableHighlightGap(current.end, next.start)) {
                current = new TextRange(current.start, next.end);
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return merged;
    }

    private boolean isIgnorableHighlightGap(int from, int to) {
        if (from >= to) {
            return true;
        }
        if (to > text.length()) {
            return false;
        }
        for (int i = from; i < to; i++) {
            if (isHiddenOffset(i)) {
                continue;
            }
            if (!Character.isWhitespace(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public void clearTextAnalysisMarks() {
        markedAreas.removeIf(area -> area.type == MarkedArea.Type.TEXT_ANALYSIS);
        render();
    }

    public void applyTextAnalysisSpans(List<TextAnalysisEngine.AnalysisSpan> spans) {
        clearTextAnalysisMarks();
        if (spans == null || spans.isEmpty()) {
            return;
        }
        java.util.Map<String, MarkedArea> areasByStyle = new java.util.LinkedHashMap<>();
        for (TextAnalysisEngine.AnalysisSpan span : spans) {
            if (span.end() <= span.start()) {
                continue;
            }
            MarkedArea area = areasByStyle.computeIfAbsent(span.styleId(), styleId -> {
                MarkedArea created = new MarkedArea(this);
                created.markTypeSilent(MarkedArea.Type.TEXT_ANALYSIS);
                created.markColorSilent(colorForAnalysisStyle(styleId, darkMarkPalette));
                markedAreas.add(created);
                return created;
            });
            area.addRangeSilent(span.start(), span.end());
        }
        render();
    }

    private static String colorForAnalysisStyle(String styleId, boolean dark) {
        if (styleId == null) {
            return dark ? "#4a4228" : "#fff9c4";
        }
        if (dark) {
            return switch (styleId) {
                case "highlight-orange" -> "#5c3d1f";
                case "search-match-first" -> "#7a4f1a";
                case "search-match-second" -> "#1a4a6e";
                case "sentence-short" -> "#66bb6a";
                case "sentence-medium" -> "#f9a825";
                case "sentence-long" -> "#ef5350";
                default -> "#4a4228";
            };
        }
        return switch (styleId) {
            case "highlight-orange" -> "#ffcc80";
            case "search-match-first" -> "#ff9800";
            case "search-match-second" -> "#2196f3";
            case "sentence-short" -> "#4caf50";
            case "sentence-medium" -> "#f9a825";
            case "sentence-long" -> "#f44336";
            default -> "#fff9c4";
        };
    }

    public void setFontFamilyForAll(String family) {
        fontFamily = family == null || family.isBlank() ? fontFamily : family;
        textWidthCache.clear();
        render();
    }

    public void setFontSizeForAll(double size) {
        if (size < 6 || size > 96) {
            return;
        }
        fontSize = size;
        textWidthCache.clear();
        invalidateLayoutCaches();
        updateScrollBar();
        render();
    }

    public double getFontSizeForAll() {
        return fontSize;
    }

    public double getLineSpacing() {
        return lineSpacing;
    }

    public void setLineSpacing(double spacing) {
        if (spacing < 1.0 || spacing > 3.0 || Double.compare(lineSpacing, spacing) == 0) {
            return;
        }
        lineSpacing = spacing;
        invalidateLayoutCaches();
        updateScrollBar();
        render();
    }

    public double getParagraphSpacing() {
        return paragraphSpacingPx;
    }

    public void setParagraphSpacing(double spacingPx) {
        double clamped = Math.max(0, Math.min(48, spacingPx));
        if (Double.compare(paragraphSpacingPx, clamped) == 0) {
            return;
        }
        paragraphSpacingPx = clamped;
        invalidateLayoutCaches();
        updateScrollBar();
        render();
    }

    public boolean isJustifyText() {
        return justifyText;
    }

    public void setJustifyText(boolean justify) {
        if (justifyText == justify) {
            return;
        }
        justifyText = justify;
        invalidateLayoutCaches();
        preferredCaretX = Double.NaN;
        render();
    }

    public void setFontFamilyForSelection(String family) {
        if (hasSelection() && family != null && !family.isBlank()) {
            styles.add(StyleRange.fontFamily(selectionStart(), selectionEnd(), family));
            render();
        }
    }

    public void setFontSizeForSelection(double size) {
        if (hasSelection() && size >= 6 && size <= 96) {
            styles.add(StyleRange.fontSize(selectionStart(), selectionEnd(), size));
            render();
        }
    }

    public void markSelectionStyle(int styleFlags) {
        if (hasSelection()) {
            styles.add(StyleRange.flags(selectionStart(), selectionEnd(), styleFlags));
            render();
        }
    }

    /** Schaltet Fett ({@code **}) ein oder aus. */
    public void toggleBold() {
        toggleInlineFormat("**", "**");
    }

    /** Schaltet Kursiv ({@code *}) ein oder aus. */
    public void toggleItalic() {
        toggleInlineFormat("*", "*");
    }

    /** Schaltet Unterstrichen ({@code <u>}) ein oder aus. */
    public void toggleUnderline() {
        toggleInlineFormat("<u>", "</u>");
    }

    public void toggleStrikethrough() {
        toggleInlineFormat("~~", "~~");
    }

    /** Überschrift umschalten: normal → H1 → … → H6 → normal. */
    public void toggleHeading() {
        int savedLineIndex = lineIndexForOffset(caret);
        int savedColumn = contentColumnForOffset(caret);
        int savedAnchorLine = lineIndexForOffset(anchor);
        int savedAnchorColumn = contentColumnForOffset(anchor);
        boolean hadSelection = hasSelection();
        int lineStart = logicalLineStartForOffset(selectionStart());
        int lineEnd = logicalLineEndIndex(lineStart);
        String line = text.substring(lineStart, lineEnd);
        Matcher matcher = Pattern.compile("^(#{1,6})\\s+(.*)$").matcher(line);
        String replacement;
        if (matcher.matches()) {
            int level = matcher.group(1).length();
            String content = matcher.group(2);
            if (level >= 6) {
                replacement = content;
            } else {
                replacement = "#".repeat(level + 1) + " " + content;
            }
        } else {
            replacement = "# " + line;
        }
        replaceRange(lineStart, lineEnd, replacement);
        if (hadSelection) {
            restoreCaretToLineContent(savedAnchorLine, savedAnchorColumn);
            int anchorPos = caret;
            restoreCaretToLineContent(savedLineIndex, savedColumn);
            anchor = anchorPos;
        } else {
            restoreCaretToLineContent(savedLineIndex, savedColumn);
            anchor = caret;
        }
        render();
    }

    public void toggleUnorderedList() {
        toggleListPrefix("- ");
    }

    public void toggleOrderedList() {
        toggleListPrefix(nextOrderedListPrefix());
    }

    private String nextOrderedListPrefix() {
        int lineStart = logicalLineStartForOffset(selectionStart());
        int lineEnd = logicalLineEndIndex(lineStart);
        String line = text.substring(lineStart, lineEnd);
        int nestLevel = 0;
        int i = 0;
        while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) {
            i++;
        }
        if (i > 0) {
            nestLevel = MarkdownBlockSupport.leadingIndentWidth(line.substring(0, i)) / 2;
        }
        String source = text.toString();
        List<MarkdownBlockSupport.ListLineInfo> listLines = MarkdownBlockSupport.parseListLines(source);
        Map<Integer, Integer> displayNumbers = MarkdownBlockSupport.computeOrderedDisplayNumbers(source, listLines);
        int maxNumber = 0;
        for (MarkdownBlockSupport.ListLineInfo info : listLines) {
            if (info.kind() != MarkdownBlockSupport.ListKind.ORDERED || info.lineStart() >= lineStart) {
                continue;
            }
            if (info.nestLevel() != nestLevel) {
                continue;
            }
            Integer number = displayNumbers.get(info.lineStart());
            if (number != null) {
                maxNumber = Math.max(maxNumber, number);
            }
        }
        return (maxNumber + 1) + ". ";
    }

    private void toggleListPrefix(String prefix) {
        int savedLineIndex = lineIndexForOffset(caret);
        int savedColumn = contentColumnForOffset(caret);
        int savedAnchorLine = lineIndexForOffset(anchor);
        int savedAnchorColumn = contentColumnForOffset(anchor);
        boolean hadSelection = hasSelection();
        int lineStart = logicalLineStartForOffset(selectionStart());
        int lineEnd = logicalLineEndIndex(lineStart);
        String line = text.substring(lineStart, lineEnd);
        String trimmed = line.stripLeading();
        if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ")
                || trimmed.matches("^\\d+\\.\\s+.*")) {
            String without = trimmed.replaceFirst("^([-+*]|\\d+\\.)\\s+", "");
            int leading = line.length() - trimmed.length();
            replaceRange(lineStart, lineEnd, line.substring(0, leading) + without);
        } else {
            int leading = line.length() - trimmed.length();
            replaceRange(lineStart, lineEnd, line.substring(0, leading) + prefix + trimmed);
        }
        if (hadSelection) {
            restoreCaretToLineContent(savedAnchorLine, savedAnchorColumn);
            int anchorPos = caret;
            restoreCaretToLineContent(savedLineIndex, savedColumn);
            anchor = anchorPos;
        } else {
            restoreCaretToLineContent(savedLineIndex, savedColumn);
            anchor = caret;
        }
        render();
    }

    public void insertLinkPlaceholder() {
        if (hasSelection()) {
            String selected = text.substring(selectionStart(), selectionEnd());
            replaceSelection("[" + selected + "](https://)");
        } else {
            replaceSelection("[Link-Text](https://)");
        }
    }

    public void wrapInlineCode() {
        if (hasSelection()) {
            toggleInlineFormat("`", "`");
            return;
        }
        int lineStart = logicalLineStartForOffset(caret);
        int lineEnd = logicalLineEndIndex(lineStart);
        if (lineEnd - lineStart > 1) {
            replaceRange(lineStart, lineEnd, "```\n" + text.substring(lineStart, lineEnd) + "\n```");
        } else {
            int insertAt = selectionStart();
            replaceSelection("```\n\n```");
            caret = normalizeCaretOffset(insertAt + 4, true);
            anchor = caret;
            preferredCaretX = Double.NaN;
            ensureCaretVisible();
            render();
        }
    }

    public void toggleMark() {
        toggleWrappedFormat("", "", "<mark>", "</mark>");
    }

    public void toggleCenter() {
        toggleWrappedFormat("", "", "<center>", "</center>");
    }

    /** Zeilenweise Markdown-Zitat: {@code > Text} (wie im Haupt-Editor). */
    public void toggleBlockquote() {
        int rangeStart = hasSelection() ? selectionStart() : logicalLineStartForOffset(caret);
        int rangeEnd = hasSelection() ? selectionEnd() : caret;
        int firstLineStart = logicalLineStartForOffset(rangeStart);
        int lastLineStart = logicalLineStartForOffset(Math.max(0, rangeEnd > 0 ? rangeEnd - 1 : 0));

        List<Integer> lineStarts = new ArrayList<>();
        for (int pos = firstLineStart; pos <= lastLineStart && pos < text.length(); ) {
            lineStarts.add(pos);
            int newline = text.indexOf("\n", pos);
            if (newline < 0) {
                break;
            }
            pos = newline + 1;
        }
        if (lineStarts.isEmpty()) {
            lineStarts.add(0);
        }

        boolean removePrefix = lineStarts.stream().allMatch(this::isBlockquoteLineStart);

        StringBuilder block = new StringBuilder();
        for (int i = 0; i < lineStarts.size(); i++) {
            int lineStart = lineStarts.get(i);
            int lineEnd = i + 1 < lineStarts.size()
                    ? lineStarts.get(i + 1) - 1
                    : (text.indexOf("\n", lineStart) < 0 ? text.length() : text.indexOf("\n", lineStart));
            String line = text.substring(lineStart, lineEnd);
            if (removePrefix) {
                int prefixLen = blockquotePrefixLength(lineStart);
                if (prefixLen > 0 && line.length() >= prefixLen) {
                    line = line.substring(prefixLen);
                }
            } else if (!isBlockquoteLineStart(lineStart)) {
                line = "> " + line;
            }
            block.append(line);
            if (lineEnd < text.length()) {
                block.append('\n');
            }
        }

        int blockStart = lineStarts.get(0);
        int blockEnd = lineStarts.get(lineStarts.size() - 1);
        blockEnd = text.indexOf("\n", blockEnd);
        if (blockEnd < 0) {
            blockEnd = text.length();
        } else {
            blockEnd++;
        }
        replaceRange(blockStart, blockEnd, block.toString());
    }

    private void handleTabKey(boolean shiftDown) {
        ViewportAnchor viewport = captureReadingViewportAnchor();
        int savedLineIndex = lineIndexForOffset(caret);
        int savedContentColumn = contentColumnForOffset(caret);
        if (shiftDown) {
            outdentAffectedLines();
        } else {
            indentAffectedLines();
        }
        syncOrderedListSourceNumbers();
        restoreCaretToLineContent(savedLineIndex, savedContentColumn);
        anchor = caret;
        restoreViewportAnchor(viewport, viewport.offset());
        preferredCaretX = Double.NaN;
        render();
    }

    private int lineIndexForOffset(int offset) {
        int index = 0;
        int pos = 0;
        int safe = Math.max(0, Math.min(text.length(), offset));
        while (pos < safe) {
            int newline = text.indexOf("\n", pos);
            if (newline < 0 || newline >= safe) {
                break;
            }
            index++;
            pos = newline + 1;
        }
        return index;
    }

    private int lineStartForLineIndex(int lineIndex) {
        int pos = 0;
        for (int i = 0; i < lineIndex; i++) {
            int newline = text.indexOf("\n", pos);
            if (newline < 0) {
                return text.length();
            }
            pos = newline + 1;
        }
        return Math.min(pos, text.length());
    }

    private int contentStartOffsetForLine(int lineStart) {
        MarkdownBlockSupport.ListLineInfo info = listLineInfoByStart.get(lineStart);
        if (info != null) {
            return info.prefixEnd();
        }
        int blockquotePrefix = blockquotePrefixLength(lineStart);
        if (blockquotePrefix > 0) {
            return lineStart + blockquotePrefix;
        }
        return lineStart;
    }

    private int contentColumnForOffset(int offset) {
        int lineStart = logicalLineStartForOffset(offset);
        return Math.max(0, offset - contentStartOffsetForLine(lineStart));
    }

    private void restoreCaretToLineContent(int lineIndex, int columnInContent) {
        if (lineIndex < 0) {
            return;
        }
        int lineStart = lineStartForLineIndex(lineIndex);
        int contentStart = contentStartOffsetForLine(lineStart);
        int target = Math.min(text.length(), contentStart + Math.max(0, columnInContent));
        caret = normalizeCaretOffset(target, true);
        anchor = caret;
    }

    private void syncOrderedListSourceNumbers() {
        String before = text.toString();
        String after = MarkdownBlockSupport.renumberOrderedListMarkers(before);
        if (before.equals(after)) {
            return;
        }
        int mappedCaret = mapOffsetThroughTextChange(before, after, caret);
        int mappedAnchor = mapOffsetThroughTextChange(before, after, anchor);
        int oldLength = before.length();
        pushUndoCoalesced(UndoEditKind.OTHER, after);
        text.setLength(0);
        text.append(after);
        adjustRangesForReplace(0, oldLength, after.length());
        caret = normalizeCaretOffset(mappedCaret, true);
        anchor = normalizeCaretOffset(mappedAnchor, true);
        invalidateLayoutCaches();
        rebuildStructuralMarkdownNow();
        scheduleAutoRuleRebuild();
        updateScrollBar();
        scheduleTextChangeNotification();
    }

    private void indentAffectedLines() {
        List<Integer> lineStarts = collectAffectedLogicalLineStarts().stream()
                .filter(this::canModifyLineIndent)
                .toList();
        if (lineStarts.isEmpty()) {
            return;
        }
        if (lineStarts.size() == 1 && !hasSelection() && !shouldIndentWholeLine(lineStarts.get(0))) {
            int insertAt = normalizeCaretOffset(caret, true);
            replaceRange(insertAt, insertAt, SOURCE_LIST_INDENT);
            return;
        }
        applyLineStartIndent(lineStarts, SOURCE_LIST_INDENT);
    }

    private void outdentAffectedLines() {
        List<Integer> lineStarts = collectAffectedLogicalLineStarts().stream()
                .filter(this::canModifyLineIndent)
                .toList();
        if (lineStarts.isEmpty()) {
            return;
        }
        applyLineStartOutdent(lineStarts);
    }

    private List<Integer> collectAffectedLogicalLineStarts() {
        int rangeStart = hasSelection() ? selectionStart() : logicalLineStartForOffset(caret);
        int rangeEnd = hasSelection() ? selectionEnd() : logicalLineEndIndex(logicalLineStartForOffset(caret));
        int firstLineStart = logicalLineStartForOffset(rangeStart);
        int lastLineStart = logicalLineStartForOffset(Math.max(0, rangeEnd > 0 ? rangeEnd - 1 : 0));

        List<Integer> lineStarts = new ArrayList<>();
        for (int pos = firstLineStart; pos <= lastLineStart && pos < text.length(); ) {
            lineStarts.add(pos);
            int newline = text.indexOf("\n", pos);
            if (newline < 0) {
                break;
            }
            pos = newline + 1;
        }
        if (lineStarts.isEmpty()) {
            lineStarts.add(0);
        }
        return lineStarts;
    }

    private boolean shouldIndentWholeLine(int lineStart) {
        return listLineInfoByStart.containsKey(lineStart) || isBlockquoteLineStart(lineStart);
    }

    private boolean canModifyLineIndent(int lineStart) {
        int lineEnd = logicalLineEndIndex(lineStart);
        String line = text.substring(lineStart, lineEnd);
        String trimmed = line.trim();
        if (trimmed.startsWith("```")) {
            return false;
        }
        return !MarkdownBlockSupport.isTableMarkdownLine(line);
    }

    private void applyLineStartIndent(List<Integer> lineStarts, String prefix) {
        if (lineStarts.isEmpty()) {
            return;
        }
        int blockStart = lineStarts.get(0);
        int blockEnd = blockEndForLineStarts(lineStarts);

        StringBuilder block = new StringBuilder();
        for (int i = 0; i < lineStarts.size(); i++) {
            int lineStart = lineStarts.get(i);
            int lineEnd = i + 1 < lineStarts.size()
                    ? lineStarts.get(i + 1) - 1
                    : logicalLineEndIndex(lineStart);
            block.append(prefix).append(text.substring(lineStart, lineEnd));
            if (lineEnd < text.length()) {
                block.append('\n');
            }
        }
        replaceLineBlock(blockStart, blockEnd, block.toString());
    }

    private void applyLineStartOutdent(List<Integer> lineStarts) {
        if (lineStarts.isEmpty()) {
            return;
        }
        int blockStart = lineStarts.get(0);
        int blockEnd = blockEndForLineStarts(lineStarts);

        StringBuilder block = new StringBuilder();
        for (int i = 0; i < lineStarts.size(); i++) {
            int lineStart = lineStarts.get(i);
            int lineEnd = i + 1 < lineStarts.size()
                    ? lineStarts.get(i + 1) - 1
                    : logicalLineEndIndex(lineStart);
            String line = text.substring(lineStart, lineEnd);
            int removed = leadingIndentRemovableLength(line);
            block.append(line.substring(removed));
            if (lineEnd < text.length()) {
                block.append('\n');
            }
        }
        replaceLineBlock(blockStart, blockEnd, block.toString());
    }

    private int blockEndForLineStarts(List<Integer> lineStarts) {
        int lastLineStart = lineStarts.get(lineStarts.size() - 1);
        int blockEnd = text.indexOf("\n", lastLineStart);
        if (blockEnd < 0) {
            return text.length();
        }
        return blockEnd + 1;
    }

    private static int leadingIndentRemovableLength(String line) {
        if (line.startsWith(SOURCE_LIST_INDENT)) {
            return SOURCE_LIST_INDENT.length();
        }
        if (line.startsWith("\t")) {
            return 1;
        }
        if (line.startsWith(" ")) {
            return 1;
        }
        return 0;
    }

    private void replaceLineBlock(int blockStart, int blockEnd, String replacement) {
        int safeStart = Math.max(0, Math.min(text.length(), blockStart));
        int safeEnd = Math.max(safeStart, Math.min(text.length(), blockEnd));
        String oldContent = text.toString();
        String newSegment = replacement == null ? "" : replacement;
        String newContent = buildTextAfterReplace(oldContent, safeStart, safeEnd, newSegment);
        pushUndoCoalesced(UndoEditKind.OTHER, newSegment);
        text.replace(safeStart, safeEnd, newSegment);
        adjustRangesForReplace(safeStart, safeEnd, newSegment.length());
        caret = normalizeCaretOffset(mapOffsetThroughTextChange(oldContent, newContent, caret), true);
        anchor = normalizeCaretOffset(mapOffsetThroughTextChange(oldContent, newContent, anchor), true);
        afterTextChanged(null, caret, false);
    }

    public void toggleBig() {
        toggleWrappedFormat("", "", "<big>", "</big>");
    }

    public void toggleSmall() {
        toggleWrappedFormat("", "", "<small>", "</small>");
    }

    public void toggleSuperscript() {
        toggleWrappedFormat("", "", "<sup>", "</sup>");
    }

    public void toggleSubscript() {
        toggleWrappedFormat("", "", "<sub>", "</sub>");
    }

    public void wrapTextColor(String colorTag) {
        if (colorTag == null || colorTag.isBlank()) {
            return;
        }
        String tag = colorTag.trim().toLowerCase(Locale.ROOT);
        toggleInlineFormat("<" + tag + ">", "</" + tag + ">");
    }

    public void insertLineBreak() {
        replaceSelection("<br>\n");
    }

    public void insertHorizontalRule() {
        String prefix = caret > 0 && text.charAt(caret - 1) != '\n' ? "\n" : "";
        replaceSelection(prefix + "---\n");
    }

    public void increaseFontSize() {
        setFontSizeForAll(Math.min(96, fontSize + 1));
    }

    public void decreaseFontSize() {
        setFontSizeForAll(Math.max(6, fontSize - 1));
    }

    private void toggleWrappedFormat(String mdStart, String mdEnd, String htmlStart, String htmlEnd) {
        if (mdStart != null && !mdStart.isEmpty()) {
            toggleInlineFormat(mdStart, mdEnd);
        } else {
            toggleInlineFormat(htmlStart, htmlEnd);
        }
    }

    private void toggleInlineFormat(String startTag, String endTag) {
        if (startTag == null || endTag == null) {
            return;
        }

        if (hasSelection()) {
            int start = selectionStart();
            int end = selectionEnd();
            String selected = selectedText();
            if (isTextFormatted(selected, startTag, endTag)) {
                String unformatted = removeFormatting(selected, startTag, endTag);
                replaceRange(start, end, unformatted);
                selectRange(start, start + unformatted.length());
            } else if (hasSurroundingFormat(start, end, startTag, endTag)) {
                int tagStart = surroundingFormatStart(start, startTag);
                int tagEnd = surroundingFormatEnd(end, endTag);
                replaceRange(tagStart, tagEnd, selected);
                selectRange(tagStart, tagStart + selected.length());
            } else {
                String formatted = startTag + selected + endTag;
                replaceRange(start, end, formatted);
                selectRange(start, start + formatted.length());
            }
            return;
        }

        int[] wordBounds = findWordBoundsAt(caret);
        if (wordBounds != null) {
            int wordStart = wordBounds[0];
            int wordEnd = wordBounds[1];
            String wordText = text.substring(wordStart, wordEnd);
            if (isTextFormatted(wordText, startTag, endTag)) {
                String unformatted = removeFormatting(wordText, startTag, endTag);
                replaceRange(wordStart, wordEnd, unformatted);
                selectRange(wordStart, wordStart + unformatted.length());
            } else if (hasSurroundingFormat(wordStart, wordEnd, startTag, endTag)) {
                int tagStart = surroundingFormatStart(wordStart, startTag);
                int tagEnd = surroundingFormatEnd(wordEnd, endTag);
                replaceRange(tagStart, tagEnd, wordText);
                selectRange(tagStart, tagStart + wordText.length());
            } else {
                String formatted = startTag + wordText + endTag;
                replaceRange(wordStart, wordEnd, formatted);
                selectRange(wordStart, wordStart + formatted.length());
            }
            return;
        }

        int pos = caret;
        replaceSelection(startTag + endTag);
        selectRange(pos + startTag.length(), pos + startTag.length());
    }

    private int[] findWordBoundsAt(int offset) {
        if (text.isEmpty()) {
            return null;
        }
        int safe = Math.max(0, Math.min(text.length(), offset));
        if (safe >= text.length() && safe > 0) {
            safe--;
        }
        int wordStart = safe;
        while (wordStart > 0 && isWordCharacter(text.charAt(wordStart - 1))) {
            wordStart--;
        }
        int wordEnd = safe;
        while (wordEnd < text.length() && isWordCharacter(text.charAt(wordEnd))) {
            wordEnd++;
        }
        return wordStart < wordEnd ? new int[]{wordStart, wordEnd} : null;
    }

    private static boolean isTextFormatted(String value, String startTag, String endTag) {
        return value != null
                && !value.isEmpty()
                && value.startsWith(startTag)
                && value.endsWith(endTag);
    }

    private static String removeFormatting(String value, String startTag, String endTag) {
        if (isTextFormatted(value, startTag, endTag)) {
            return value.substring(startTag.length(), value.length() - endTag.length());
        }
        return value;
    }

    private boolean hasSurroundingFormat(int contentStart, int contentEnd, String startTag, String endTag) {
        if ("**".equals(startTag) && "**".equals(endTag)) {
            return contentStart >= 2
                    && contentEnd + 2 <= text.length()
                    && text.substring(contentStart - 2, contentStart).equals("**")
                    && text.substring(contentEnd, contentEnd + 2).equals("**");
        }
        if ("*".equals(startTag) && "*".equals(endTag)) {
            return hasSingleStarSurrounding(contentStart, contentEnd);
        }
        return contentStart >= startTag.length()
                && contentEnd + endTag.length() <= text.length()
                && text.substring(contentStart - startTag.length(), contentStart).equals(startTag)
                && text.substring(contentEnd, contentEnd + endTag.length()).equals(endTag);
    }

    private boolean hasSingleStarSurrounding(int contentStart, int contentEnd) {
        if (contentStart < 1 || contentEnd >= text.length() || text.charAt(contentStart - 1) != '*') {
            return false;
        }
        if (contentStart >= 2 && text.charAt(contentStart - 2) == '*') {
            return false;
        }
        if (text.charAt(contentEnd) != '*') {
            return false;
        }
        return contentEnd + 1 >= text.length() || text.charAt(contentEnd + 1) != '*';
    }

    private static int surroundingFormatStart(int contentStart, String startTag) {
        if ("**".equals(startTag)) {
            return contentStart - 2;
        }
        if ("*".equals(startTag)) {
            return contentStart - 1;
        }
        return contentStart - startTag.length();
    }

    private static int surroundingFormatEnd(int contentEnd, String endTag) {
        if ("**".equals(endTag)) {
            return contentEnd + 2;
        }
        if ("*".equals(endTag)) {
            return contentEnd + 1;
        }
        return contentEnd + endTag.length();
    }

    public void scrollToRatio(double ratio) {
        double max = verticalScrollBar.getMax();
        verticalScrollBar.setValue(Math.max(0, Math.min(max, max * ratio)));
    }

    public double getScrollRatio() {
        double max = verticalScrollBar.getMax();
        return max <= 0 ? 0 : verticalScrollBar.getValue() / max;
    }

    public void scrollToOffset(int offset) {
        TextPosition pos = positionForOffset(offset);
        verticalScrollBar.setValue(Math.max(0, pos.lineIndex * lineHeight() - lineHeight()));
    }

    public DoubleProperty scrollTopProperty() {
        return verticalScrollBar.valueProperty();
    }

    private void installSceneAccelerators(javafx.scene.Scene scene) {
        if (sceneAcceleratorsInstalled || scene == null) {
            return;
        }
        var accelerators = scene.getAccelerators();
        Runnable refocus = () -> javafx.application.Platform.runLater(this::requestInputFocus);
        EditingShortcuts.bindPlatformAccelerators(accelerators, "C", () -> {
            copySelection();
            refocus.run();
        });
        EditingShortcuts.bindPlatformAccelerators(accelerators, "X", () -> {
            cutSelection();
            refocus.run();
        });
        EditingShortcuts.bindPlatformAccelerators(accelerators, "V", () -> {
            pasteFromClipboard();
            refocus.run();
        });
        EditingShortcuts.bindPlatformAccelerators(accelerators, "A", () -> {
            selectAll();
            refocus.run();
        });
        EditingShortcuts.bindPlatformAccelerators(accelerators, "Z", () -> {
            undo();
            refocus.run();
        });
        EditingShortcuts.bindPlatformAccelerators(accelerators, "Shift+Z", () -> {
            redo();
            refocus.run();
        });
        EditingShortcuts.bindPlatformAccelerators(accelerators, "Y", () -> {
            redo();
            refocus.run();
        });
        EditingShortcuts.bindPlatformAccelerators(accelerators, "B", () -> {
            toggleBold();
            refocus.run();
        });
        EditingShortcuts.bindPlatformAccelerators(accelerators, "I", () -> {
            toggleItalic();
            refocus.run();
        });
        EditingShortcuts.bindPlatformAccelerators(accelerators, "U", () -> {
            toggleUnderline();
            refocus.run();
        });
        sceneAcceleratorsInstalled = true;
    }

    private void handleKeyTyped(KeyEvent event) {
        if (isEditingShortcutKey(event)) {
            event.consume();
            return;
        }
        if (!editable) {
            event.consume();
            return;
        }
        if (suppressEnterCount > 0) {
            suppressEnterCount--;
            event.consume();
            return;
        }
        String character = event.getCharacter();
        if (character == null || character.isEmpty() || character.charAt(0) < 32) {
            return;
        }
        if ("\"".equals(character) || "'".equals(character)) {
            int insertPos = hasSelection() ? selectionStart() : normalizeCaretOffset(caret, true);
            String replacement = QuotationMarkSupport.resolveTypedQuote(
                    text.toString(), insertPos, character, quoteStyleIndex);
            replaceSelection(replacement);
            event.consume();
            return;
        }
        replaceSelection(character);
        event.consume();
    }

    private void handleKeyPressed(KeyEvent event) {
        boolean shortcut = isEditingShortcutKey(event);
        if (shortcut && event.getCode() == KeyCode.A) {
            anchor = 0;
            caret = text.length();
            ensureCaretVisible();
            render();
            event.consume();
            return;
        }
        if (shortcut && event.getCode() == KeyCode.C) {
            copySelection();
            event.consume();
            return;
        }
        if (!editable) {
            switch (event.getCode()) {
                case LEFT -> moveCaret(caret - 1, event.isShiftDown());
                case RIGHT -> moveCaretForwardOne(event.isShiftDown());
                case UP -> moveVertical(-1, event.isShiftDown());
                case DOWN -> moveVertical(1, event.isShiftDown());
                case HOME -> moveCaret(lineStart(caret), event.isShiftDown());
                case END -> moveCaret(lineEnd(caret), event.isShiftDown());
                case PAGE_UP -> {
                    verticalScrollBar.setValue(Math.max(0, verticalScrollBar.getValue() - canvas.getHeight()));
                    moveVertical(-(int) Math.max(1, canvas.getHeight() / lineHeight()), event.isShiftDown());
                }
                case PAGE_DOWN -> {
                    verticalScrollBar.setValue(Math.min(verticalScrollBar.getMax(), verticalScrollBar.getValue() + canvas.getHeight()));
                    moveVertical((int) Math.max(1, canvas.getHeight() / lineHeight()), event.isShiftDown());
                }
                default -> {
                    return;
                }
            }
            event.consume();
            return;
        }
        if (shortcut && event.getCode() == KeyCode.Z) {
            undo();
            event.consume();
            return;
        }
        if (shortcut && (event.getCode() == KeyCode.Y || (event.isShiftDown() && event.getCode() == KeyCode.Z))) {
            redo();
            event.consume();
            return;
        }
        if (shortcut && event.getCode() == KeyCode.X) {
            cutSelection();
            event.consume();
            return;
        }
        if (shortcut && event.getCode() == KeyCode.V) {
            pasteFromClipboard();
            event.consume();
            return;
        }
        if (shortcut && event.getCode() == KeyCode.B) {
            toggleBold();
            event.consume();
            return;
        }
        if (shortcut && event.getCode() == KeyCode.I) {
            toggleItalic();
            event.consume();
            return;
        }
        if (shortcut && event.getCode() == KeyCode.U) {
            toggleUnderline();
            event.consume();
            return;
        }

        switch (event.getCode()) {
            case BACK_SPACE -> deleteSelectionOrPrevious();
            case DELETE -> deleteSelectionOrNext();
            case ENTER -> {
                if (suppressEnterCount > 0) {
                    suppressEnterCount--;
                } else {
                    int insertAt = normalizeCaretOffset(caret, true);
                    if (!hasSelection()) {
                        anchor = insertAt;
                    }
                    caret = insertAt;
                    String insertion = newlinesForEnter(insertAt);
                    boolean continuedList = listContinuationForEnter(insertAt) != null;
                    replaceSelection(insertion);
                    if (continuedList) {
                        syncOrderedListSourceNumbers();
                    }
                }
            }
            case LEFT -> moveCaret(caret - 1, event.isShiftDown());
            case RIGHT -> moveCaretForwardOne(event.isShiftDown());
            case UP -> moveVertical(-1, event.isShiftDown());
            case DOWN -> moveVertical(1, event.isShiftDown());
            case HOME -> moveCaret(lineStart(caret), event.isShiftDown());
            case END -> moveCaret(lineEnd(caret), event.isShiftDown());
            case PAGE_UP -> {
                verticalScrollBar.setValue(Math.max(0, verticalScrollBar.getValue() - canvas.getHeight()));
                moveVertical(-(int) Math.max(1, canvas.getHeight() / lineHeight()), event.isShiftDown());
            }
            case PAGE_DOWN -> {
                verticalScrollBar.setValue(Math.min(verticalScrollBar.getMax(), verticalScrollBar.getValue() + canvas.getHeight()));
                moveVertical((int) Math.max(1, canvas.getHeight() / lineHeight()), event.isShiftDown());
            }
            case TAB -> handleTabKey(event.isShiftDown());
            default -> {
                return;
            }
        }
        event.consume();
    }

    private void setupLanguageToolHoverPopup() {
        languageToolHoverMessage.setWrapText(true);
        languageToolHoverMessage.setMaxWidth(420);
        languageToolHoverSuggestion.setWrapText(true);
        languageToolHoverSuggestion.setMaxWidth(420);

        VBox container = new VBox(4, languageToolHoverMessage, languageToolHoverSuggestion);
        container.setAlignment(Pos.TOP_LEFT);
        container.setPadding(new Insets(8, 10, 8, 10));
        languageToolHoverPopup.getContent().add(container);
        languageToolHoverPopup.setAutoHide(true);
        languageToolHoverPopup.setAutoFix(true);
        languageToolContextMenu.setAutoHide(true);
        languageToolContextMenu.setOnShown(event -> installContextMenuOutsideClickHandler());
        languageToolContextMenu.setOnHidden(event -> removeContextMenuOutsideClickHandler());

        editorContextMenu.setAutoHide(true);
        editorContextMenu.setOnShown(event -> installContextMenuOutsideClickHandler());
        editorContextMenu.setOnHidden(event -> removeContextMenuOutsideClickHandler());
        updateLanguageToolHoverTheme();
    }

    private void installContextMenuOutsideClickHandler() {
        Scene scene = canvas.getScene();
        if (scene == null || contextMenuOutsideClickFilter != null) {
            return;
        }
        contextMenuOutsideClickFilter = event -> {
            if (languageToolContextMenu.isShowing()
                    && !isEventInsideContextMenu(event, languageToolContextMenu)) {
                languageToolContextMenu.hide();
            }
            if (editorContextMenu.isShowing()
                    && !isEventInsideContextMenu(event, editorContextMenu)) {
                editorContextMenu.hide();
            }
        };
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, contextMenuOutsideClickFilter);
    }

    private void removeContextMenuOutsideClickHandler() {
        if (languageToolContextMenu.isShowing() || editorContextMenu.isShowing()) {
            return;
        }
        Scene scene = canvas.getScene();
        if (scene == null || contextMenuOutsideClickFilter == null) {
            return;
        }
        scene.removeEventFilter(MouseEvent.MOUSE_PRESSED, contextMenuOutsideClickFilter);
        contextMenuOutsideClickFilter = null;
    }

    private boolean isEventInsideContextMenu(MouseEvent event, ContextMenu menu) {
        Scene menuScene = menu.getScene();
        if (menuScene == null || !(event.getTarget() instanceof Node target)) {
            return false;
        }
        Node current = target;
        while (current != null) {
            if (current.getScene() == menuScene) {
                return true;
            }
            current = current.getParent() instanceof Node parent ? parent : null;
        }
        return false;
    }

    private void hideEditorContextMenu() {
        if (editorContextMenu.isShowing()) {
            editorContextMenu.hide();
        }
    }

    private void hideAllContextMenus() {
        hideLanguageToolContextMenu();
        hideEditorContextMenu();
    }

    private void hideLanguageToolContextMenu() {
        if (languageToolContextMenu.isShowing()) {
            languageToolContextMenu.hide();
        }
    }

    private void updateLanguageToolHoverTheme() {
        String background = toWebColor(editorBackgroundColor);
        String textColor = toWebColor(editorTextColor);
        String border = toWebColor(gutterBackgroundColor);
        String suggestionColor = toWebColor(gutterTextColor);

        String containerStyle = String.format(
                "-fx-background-color: %s; -fx-border-color: %s; -fx-border-width: 1; -fx-background-radius: 4; -fx-border-radius: 4;",
                background, border);
        String messageStyle = String.format("-fx-text-fill: %s; -fx-font-size: 12px;", textColor);
        String suggestionStyle = String.format("-fx-text-fill: %s; -fx-font-size: 11px;", suggestionColor);

        if (!languageToolHoverPopup.getContent().isEmpty()) {
            languageToolHoverPopup.getContent().get(0).setStyle(containerStyle);
        }
        languageToolHoverMessage.setStyle(messageStyle);
        languageToolHoverSuggestion.setStyle(suggestionStyle);
    }

    private static String toWebColor(Color color) {
        return String.format("#%02x%02x%02x",
                (int) Math.round(color.getRed() * 255),
                (int) Math.round(color.getGreen() * 255),
                (int) Math.round(color.getBlue() * 255));
    }

    private void setupMouseHandling() {
        canvas.setOnMousePressed(event -> {
            hideLanguageToolHover();
            hideAllContextMenus();
            if (event.getButton() != MouseButton.PRIMARY) {
                requestInputFocus();
                return;
            }
            double contentY = event.getY() + scrollTop;
            if (shouldRenderImagePreview()) {
                ParsedImageBlock imageBlock = imageBlockAtContentY(event.getX(), contentY);
                if (imageBlock != null) {
                    anchor = imageBlock.startOffset();
                    caret = imageBlock.endOffset();
                    preferredCaretX = Double.NaN;
                    ensureCaretVisible();
                    render();
                    ensureInputFocusAfterMouseEvent();
                    return;
                }
            }
            placeCaretAt(event.getX(), contentY, event.isShiftDown());
            beginMouseSelectionDrag(event.getX(), event.getY());
            ensureInputFocusAfterMouseEvent();
        });
        canvas.setOnMouseReleased(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                endMouseSelectionDrag();
                ensureInputFocusAfterMouseEvent();
            }
        });
        canvas.setOnMouseClicked(event -> {
            if (event.getButton() != MouseButton.PRIMARY) {
                return;
            }
            double contentY = event.getY() + scrollTop;
            int offset = offsetAt(event.getX(), contentY);
            int clickCount = event.getClickCount();
            if (clickCount >= 3) {
                selectParagraphAt(offset);
                event.consume();
                return;
            }
            if (clickCount == 2) {
                selectWordAt(offset);
                event.consume();
                return;
            }
            MarkedArea interactiveArea = interactiveAreaAt(offset);
            if (interactiveArea != null && interactiveArea.hasClickCallback()) {
                interactiveArea.fireClick();
                event.consume();
            }
        });
        canvas.setOnMouseDragged(event -> {
            if (!event.isPrimaryButtonDown()) {
                return;
            }
            updateSelectionFromDrag(event.getX(), event.getY());
            ensureInputFocusAfterMouseEvent();
        });
        canvas.setOnScroll(event -> {
            hideLanguageToolHover();
            verticalScrollBar.setValue(Math.max(0, Math.min(verticalScrollBar.getMax(), verticalScrollBar.getValue() - event.getDeltaY())));
            event.consume();
        });
        canvas.setOnMouseMoved(event -> updateLanguageToolHover(event.getX(), event.getY() + scrollTop, event.getScreenX(), event.getScreenY()));
        canvas.setOnMouseExited(event -> {
            hideLanguageToolHover();
            if (mouseSelectionDragActive && event.isPrimaryButtonDown()) {
                updateSelectionAutoScroll(lastSelectionDragViewportY);
            }
        });
        canvas.setOnContextMenuRequested(event -> {
            hideLanguageToolHover();
            hideLanguageToolContextMenu();
            showEditorContextMenu(event);
            event.consume();
        });
    }

    private void beginMouseSelectionDrag(double viewportX, double viewportY) {
        mouseSelectionDragActive = true;
        lastSelectionDragViewportX = viewportX;
        lastSelectionDragViewportY = viewportY;
        installSceneSelectionDragHandlers();
    }

    private void endMouseSelectionDrag() {
        if (!mouseSelectionDragActive) {
            return;
        }
        mouseSelectionDragActive = false;
        stopSelectionAutoScroll();
        removeSceneSelectionDragHandlers();
    }

    private void installSceneSelectionDragHandlers() {
        Scene scene = canvas.getScene();
        if (scene == null) {
            return;
        }
        if (sceneSelectionDragFilter == null) {
            sceneSelectionDragFilter = event -> {
                if (!mouseSelectionDragActive || !event.isPrimaryButtonDown()) {
                    return;
                }
                Point2D local = canvas.sceneToLocal(event.getSceneX(), event.getSceneY());
                updateSelectionFromDrag(local.getX(), local.getY());
            };
            sceneSelectionReleaseFilter = event -> endMouseSelectionDrag();
        }
        scene.removeEventFilter(MouseEvent.MOUSE_DRAGGED, sceneSelectionDragFilter);
        scene.removeEventFilter(MouseEvent.MOUSE_RELEASED, sceneSelectionReleaseFilter);
        scene.addEventFilter(MouseEvent.MOUSE_DRAGGED, sceneSelectionDragFilter);
        scene.addEventFilter(MouseEvent.MOUSE_RELEASED, sceneSelectionReleaseFilter);
    }

    private void removeSceneSelectionDragHandlers() {
        Scene scene = canvas.getScene();
        if (scene == null || sceneSelectionDragFilter == null) {
            return;
        }
        scene.removeEventFilter(MouseEvent.MOUSE_DRAGGED, sceneSelectionDragFilter);
        scene.removeEventFilter(MouseEvent.MOUSE_RELEASED, sceneSelectionReleaseFilter);
    }

    private void updateSelectionFromDrag(double viewportX, double viewportY) {
        lastSelectionDragViewportX = viewportX;
        lastSelectionDragViewportY = viewportY;
        double contentY = viewportY + scrollTop;
        placeCaretAt(viewportX, contentY, true);
        updateSelectionAutoScroll(viewportY);
    }

    private void updateSelectionAutoScroll(double viewportY) {
        if (!mouseSelectionDragActive || canvas.getHeight() <= 0) {
            stopSelectionAutoScroll();
            return;
        }
        if (viewportY > canvas.getHeight()) {
            startSelectionAutoScroll(1);
        } else if (viewportY < 0) {
            startSelectionAutoScroll(-1);
        } else if (viewportY >= canvas.getHeight() - SELECTION_AUTO_SCROLL_EDGE_PX) {
            startSelectionAutoScroll(1);
        } else if (viewportY <= SELECTION_AUTO_SCROLL_EDGE_PX) {
            startSelectionAutoScroll(-1);
        } else {
            stopSelectionAutoScroll();
        }
    }

    private void startSelectionAutoScroll(int direction) {
        if (selectionAutoScrollTimeline != null
                && selectionAutoScrollTimeline.getStatus() == javafx.animation.Animation.Status.RUNNING
                && selectionAutoScrollDirection == direction) {
            return;
        }
        stopSelectionAutoScroll();
        selectionAutoScrollDirection = direction;
        selectionAutoScrollTimeline = new Timeline(new KeyFrame(SELECTION_AUTO_SCROLL_INTERVAL, event -> tickSelectionAutoScroll()));
        selectionAutoScrollTimeline.setCycleCount(Timeline.INDEFINITE);
        selectionAutoScrollTimeline.play();
    }

    private void tickSelectionAutoScroll() {
        if (!mouseSelectionDragActive) {
            stopSelectionAutoScroll();
            return;
        }
        double max = verticalScrollBar.getMax();
        double step = selectionAutoScrollDirection * SELECTION_AUTO_SCROLL_STEP_PX;
        double nextScroll = Math.max(0, Math.min(max, verticalScrollBar.getValue() + step));
        if (Math.abs(nextScroll - verticalScrollBar.getValue()) < 0.01) {
            return;
        }
        verticalScrollBar.setValue(nextScroll);
        scrollTop = nextScroll;
        double dragX = Double.isNaN(lastSelectionDragViewportX) ? textLeft() : lastSelectionDragViewportX;
        double contentY = selectionAutoScrollDirection > 0
                ? scrollTop + Math.max(0, canvas.getHeight() - 1)
                : scrollTop;
        placeCaretAt(dragX, contentY, true);
    }

    private void stopSelectionAutoScroll() {
        selectionAutoScrollDirection = 0;
        if (selectionAutoScrollTimeline != null) {
            selectionAutoScrollTimeline.stop();
            selectionAutoScrollTimeline = null;
        }
    }

    private void showEditorContextMenu(javafx.scene.input.ContextMenuEvent event) {
        double contentY = event.getY() + scrollTop;
        int clickPos = offsetAt(event.getX(), contentY);
        LanguageToolService.Match match = findLanguageToolMatchAt(clickPos);

        editorContextMenu.getItems().clear();
        EditorDialogThemes.styleContextMenu(editorContextMenu, contextMenuThemeIndex);
        editorContextMenu.setMaxWidth(600);

        if (match != null) {
            appendLanguageToolMenuItems(match);
            editorContextMenu.getItems().add(new SeparatorMenuItem());
        }

        String selected = hasSelection() ? selectedText() : "";
        MenuItem copyItem = new MenuItem("Kopieren\t" + EditingShortcuts.acceleratorHint("C"));
        copyItem.setDisable(selected.isEmpty());
        copyItem.setOnAction(e -> copySelection());

        MenuItem cutItem = new MenuItem("Ausschneiden\t" + EditingShortcuts.acceleratorHint("X"));
        cutItem.setDisable(selected.isEmpty());
        cutItem.setOnAction(e -> cutSelection());

        boolean hasClipboard = Clipboard.getSystemClipboard().hasString();
        MenuItem pasteItem = new MenuItem("Einfügen\t" + EditingShortcuts.acceleratorHint("V"));
        pasteItem.setDisable(!hasClipboard);
        pasteItem.setOnAction(e -> pasteFromClipboard());

        editorContextMenu.getItems().addAll(copyItem, cutItem, pasteItem);

        if (contextMenuRewriteActions != null) {
            editorContextMenu.getItems().add(new SeparatorMenuItem());
            MenuItem sprechItem = new MenuItem("Sprechantwort korrigieren");
            sprechItem.setOnAction(e -> contextMenuRewriteActions.handleSprechantwortKorrektur());
            MenuItem phraseItem = new MenuItem("Phrase korrigieren");
            phraseItem.setOnAction(e -> contextMenuRewriteActions.handlePhraseKorrektur());
            MenuItem selectionItem = new MenuItem("Selektion überarbeiten");
            selectionItem.setOnAction(e -> contextMenuRewriteActions.handleSelektionUeberarbeitung());
            editorContextMenu.getItems().addAll(sprechItem, phraseItem, selectionItem);

            if (selectionRevisionAgentAction != null) {
                int selLen = hasSelection() ? selectionEnd() - selectionStart() : 0;
                int maxChars = SelectionRevisionSupport.maxSelectionChars();
                MenuItem revisionAgentItem = new MenuItem("Überarbeiten (Agent)");
                if (selLen <= 0 || selLen > maxChars) {
                    revisionAgentItem.setDisable(true);
                    if (selLen > maxChars) {
                        revisionAgentItem.setText("Überarbeiten (Agent) — max. " + maxChars + " Zeichen");
                    }
                } else {
                    revisionAgentItem.setOnAction(e -> selectionRevisionAgentAction.run());
                }
                editorContextMenu.getItems().add(revisionAgentItem);
            }

            if (idiomReviewAgentAction != null) {
                int selLen = hasSelection() ? selectionEnd() - selectionStart() : 0;
                int maxChars = IdiomReviewSupport.maxSelectionChars();
                MenuItem idiomItem = new MenuItem("Sprachentflechtung");
                if (selLen <= 0 || selLen > maxChars) {
                    idiomItem.setDisable(true);
                    if (selLen > maxChars) {
                        idiomItem.setText("Sprachentflechtung — max. " + maxChars + " Zeichen");
                    }
                } else {
                    idiomItem.setOnAction(e -> idiomReviewAgentAction.run());
                }
                editorContextMenu.getItems().add(idiomItem);
            }
        }

        editorContextMenu.show(canvas, event.getScreenX(), event.getScreenY());

        if (!hasSelection()) {
            caret = normalizeCaretOffset(clickPos, true);
            anchor = caret;
            preferredCaretX = Double.NaN;
            render();
        }
    }

    private void appendLanguageToolMenuItems(LanguageToolService.Match match) {
        MenuItem header = new MenuItem("LanguageTool: " + safeMessage(match));
        header.setDisable(true);
        editorContextMenu.getItems().add(header);
        editorContextMenu.getItems().add(new SeparatorMenuItem());

        if (match.getReplacements() != null && !match.getReplacements().isEmpty()) {
            for (LanguageToolService.Replacement replacement : match.getReplacements()) {
                MenuItem item = new MenuItem("→ " + replacement.getValue());
                item.setOnAction(e -> applyLanguageToolCorrection(match, replacement.getValue()));
                editorContextMenu.getItems().add(item);
            }
        } else {
            MenuItem noSuggestions = new MenuItem("Keine Vorschläge verfügbar");
            noSuggestions.setDisable(true);
            editorContextMenu.getItems().add(noSuggestions);
        }

        String matchedText = matchedText(match).trim();
        if (languageToolDictionary != null && !matchedText.isEmpty()
                && !languageToolDictionary.containsWordOrVariant(matchedText)) {
            editorContextMenu.getItems().add(new SeparatorMenuItem());
            MenuItem addToDictionary = new MenuItem("Zum Wörterbuch hinzufügen: \"" + matchedText + "\"");
            addToDictionary.setOnAction(e -> addLanguageToolWordToDictionary(matchedText));
            editorContextMenu.getItems().add(addToDictionary);
        }
    }

    private void updateLanguageToolHover(double localX, double localY, double screenX, double screenY) {
        if (languageToolContextMenu.isShowing()) {
            hideLanguageToolHover();
            return;
        }
        int offset = offsetAt(localX, localY);
        LanguageToolService.Match match = findLanguageToolMatchAt(offset);
        if (match == null) {
            hideLanguageToolHover();
            return;
        }
        if (match == hoveredLanguageToolMatch && languageToolHoverPopup.isShowing()) {
            languageToolHoverPopup.setX(screenX + 12);
            languageToolHoverPopup.setY(screenY + 18);
            return;
        }
        hoveredLanguageToolMatch = match;
        languageToolHoverMessage.setText(safeMessage(match));

        if (match.getReplacements() != null && !match.getReplacements().isEmpty()) {
            languageToolHoverSuggestion.setText("Vorschlag: " + match.getReplacements().get(0).getValue());
            languageToolHoverSuggestion.setVisible(true);
            languageToolHoverSuggestion.setManaged(true);
        } else {
            languageToolHoverSuggestion.setText("");
            languageToolHoverSuggestion.setVisible(false);
            languageToolHoverSuggestion.setManaged(false);
        }

        if (!languageToolHoverPopup.isShowing()) {
            languageToolHoverPopup.show(canvas, screenX + 12, screenY + 18);
        }
    }

    private void hideLanguageToolHover() {
        hoveredLanguageToolMatch = null;
        languageToolHoverPopup.hide();
    }

    private LanguageToolService.Match findLanguageToolMatchAt(int offset) {
        if (offset < 0 || currentLanguageToolMatches == null || currentLanguageToolMatches.isEmpty()) {
            return null;
        }
        for (LanguageToolService.Match match : currentLanguageToolMatches) {
            int start = match.getOffset();
            int end = start + match.getLength();
            if (offset >= start && offset < end) {
                return match;
            }
        }
        return null;
    }

    private void showLanguageToolContextMenu(LanguageToolService.Match match, double screenX, double screenY) {
        languageToolContextMenu.getItems().clear();

        MenuItem header = new MenuItem("LanguageTool: " + safeMessage(match));
        header.setDisable(true);
        languageToolContextMenu.getItems().add(header);
        languageToolContextMenu.getItems().add(new SeparatorMenuItem());

        if (match.getReplacements() != null && !match.getReplacements().isEmpty()) {
            for (LanguageToolService.Replacement replacement : match.getReplacements()) {
                MenuItem item = new MenuItem("-> " + replacement.getValue());
                item.setOnAction(e -> applyLanguageToolCorrection(match, replacement.getValue()));
                languageToolContextMenu.getItems().add(item);
            }
        } else {
            MenuItem noSuggestions = new MenuItem("Keine Vorschläge verfügbar");
            noSuggestions.setDisable(true);
            languageToolContextMenu.getItems().add(noSuggestions);
        }

        String matchedText = matchedText(match).trim();
        if (languageToolDictionary != null && !matchedText.isEmpty()
                && !languageToolDictionary.containsWordOrVariant(matchedText)) {
            languageToolContextMenu.getItems().add(new SeparatorMenuItem());
            MenuItem addToDictionary = new MenuItem("Zum Wörterbuch hinzufügen: \"" + matchedText + "\"");
            addToDictionary.setOnAction(e -> addLanguageToolWordToDictionary(matchedText));
            languageToolContextMenu.getItems().add(addToDictionary);
        }

        languageToolContextMenu.show(canvas, screenX, screenY);
    }

    private String safeMessage(LanguageToolService.Match match) {
        if (match.getMessage() != null && !match.getMessage().isBlank()) {
            return match.getMessage();
        }
        if (match.getShortMessage() != null && !match.getShortMessage().isBlank()) {
            return match.getShortMessage();
        }
        return "Hinweis";
    }

    private String matchedText(LanguageToolService.Match match) {
        int start = Math.max(0, Math.min(text.length(), match.getOffset()));
        int end = Math.max(start, Math.min(text.length(), match.getOffset() + match.getLength()));
        return text.substring(start, end);
    }

    private void applyLanguageToolCorrection(LanguageToolService.Match match, String replacement) {
        hideLanguageToolHover();
        int start = match.getOffset();
        int end = start + match.getLength();
        int delta = (replacement == null ? 0 : replacement.length()) - match.getLength();
        replaceRange(start, end, replacement == null ? "" : replacement);

        List<LanguageToolService.Match> updatedMatches = new ArrayList<>();
        for (LanguageToolService.Match other : currentLanguageToolMatches) {
            if (other == match) {
                continue;
            }
            int otherStart = other.getOffset();
            int otherEnd = otherStart + other.getLength();
            if (otherStart < end && otherEnd > start) {
                continue;
            }
            if (otherStart >= end && delta != 0) {
                other.setOffset(otherStart + delta);
            }
            updatedMatches.add(other);
        }
        applyLanguageToolMatches(updatedMatches);
    }

    private void addLanguageToolWordToDictionary(String word) {
        if (languageToolDictionary == null || word == null || word.isBlank()) {
            return;
        }
        languageToolDictionary.addWord(word);
        applyLanguageToolMatches(languageToolDictionary.filterMatches(currentLanguageToolMatches, getText()));
    }

    private void moveCaret(int target, boolean extendSelection) {
        boolean forward = target >= caret;
        caret = normalizeCaretOffset(Math.max(0, Math.min(text.length(), target)), forward);
        preferredCaretX = Double.NaN;
        if (!extendSelection) {
            anchor = caret;
        }
        ensureCaretVisible();
        render();
    }

    /**
     * Pfeil rechts: bei Blocksatz-Zeilen zuerst das letzte sichtbare Zeichen anfahren,
     * bevor verstecktes Markup oder der Zeilenumbruch übersprungen wird.
     */
    private void moveCaretForwardOne(boolean extendSelection) {
        int next = caret + 1;
        if (next > text.length()) {
            moveCaret(text.length(), extendSelection);
            return;
        }
        if (!isHiddenOffset(next)) {
            moveCaret(next, extendSelection);
            return;
        }
        int lineIdx = visualLineIndexForOffset(caret);
        VisualLine line = visualLines().get(lineIdx);
        int lastChar = lastMeaningfulCharOffsetInLine(line);
        if (caret < lastChar) {
            boolean onlyHiddenBeforeLastChar = true;
            for (int offset = caret + 1; offset < lastChar; offset++) {
                if (!isHiddenOffset(offset)) {
                    onlyHiddenBeforeLastChar = false;
                    break;
                }
            }
            if (onlyHiddenBeforeLastChar) {
                moveCaret(lastChar, extendSelection);
                return;
            }
        }
        moveCaret(next, extendSelection);
    }

    private int lastMeaningfulCharOffsetInLine(VisualLine line) {
        for (int offset = line.end - 1; offset >= line.start; offset--) {
            if (!isHiddenOffset(offset) && !Character.isWhitespace(text.charAt(offset))) {
                return offset;
            }
        }
        for (int offset = line.end - 1; offset >= line.start; offset--) {
            if (!isHiddenOffset(offset)) {
                return offset;
            }
        }
        return Math.max(line.start, line.end - 1);
    }

    private void moveVertical(int lineDelta, boolean extendSelection) {
        boolean forward = lineDelta > 0;
        int previousCaret = normalizeCaretOffset(caret, forward);
        TextPosition current = positionForOffset(previousCaret);
        List<VisualLine> lines = visualLines();
        int targetLine = findVerticalTargetLine(current.lineIndex, lineDelta);
        VisualLine line = lines.get(targetLine);
        if (Double.isNaN(preferredCaretX)) {
            VisualLine currentLine = lines.get(Math.max(0, Math.min(lines.size() - 1, current.lineIndex)));
            preferredCaretX = xForOffsetInLine(currentLine, current.lineIndex, previousCaret) - textLeft();
        }
        int nextCaret = normalizeCaretOffset(offsetAtLineX(line, targetLine, preferredCaretX), forward);
        if (forward && targetLine > current.lineIndex && nextCaret <= previousCaret) {
            nextCaret = normalizeCaretOffset(firstVisibleOffsetInLine(line), true);
        } else if (!forward && targetLine < current.lineIndex && nextCaret >= previousCaret) {
            nextCaret = normalizeCaretOffset(lastVisibleOffsetInLine(line), false);
        }
        caret = nextCaret;
        if (!extendSelection) {
            anchor = caret;
        }
        ensureCaretVisible();
        render();
    }

    private int findVerticalTargetLine(int startLine, int lineDelta) {
        List<VisualLine> lines = visualLines();
        if (lines.isEmpty()) {
            return 0;
        }
        if (lineDelta == 0) {
            return Math.max(0, Math.min(lines.size() - 1, startLine));
        }
        int direction = lineDelta > 0 ? 1 : -1;
        int steps = Math.abs(lineDelta);
        int line = Math.max(0, Math.min(lines.size() - 1, startLine));
        for (int step = 0; step < steps; step++) {
            int next = line + direction;
            while (next >= 0 && next < lines.size()
                    && (isLineInsideImageBlock(next) || isLineInsideHorizontalRule(next))) {
                next += direction;
            }
            if (next < 0) {
                return 0;
            }
            if (next >= lines.size()) {
                return lines.size() - 1;
            }
            line = next;
        }
        return line;
    }

    private String selectedText() {
        if (!hasSelection()) {
            return "";
        }
        return text.substring(selectionStart(), selectionEnd());
    }

    public void copySelection() {
        if (!hasSelection()) {
            return;
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(selectedText());
        Clipboard.getSystemClipboard().setContent(content);
    }

    public void cutSelection() {
        copySelection();
        if (hasSelection()) {
            replaceSelection("");
        }
    }

    public void pasteFromClipboard() {
        if (!Clipboard.getSystemClipboard().hasString()) {
            return;
        }
        String pasted = Clipboard.getSystemClipboard().getString();
        if (pasted != null && !pasted.isEmpty()) {
            insertTextPreserveCaret(pasted);
        }
    }

    private void pushUndoCoalesced(UndoEditKind kind, String incoming) {
        if (applyingSnapshot) {
            return;
        }
        long now = System.currentTimeMillis();
        if (shouldCoalesceUndo(kind, incoming, now)) {
            lastUndoEditTimeMs = now;
            return;
        }
        undoStack.push(snapshot());
        while (undoStack.size() > MAX_UNDO) {
            undoStack.removeLast();
        }
        redoStack.clear();
        lastUndoEditKind = kind;
        lastUndoEditTimeMs = now;
    }

    private boolean shouldCoalesceUndo(UndoEditKind kind, String incoming, long now) {
        if (undoStack.isEmpty() || lastUndoEditKind == null) {
            return false;
        }
        if (kind != lastUndoEditKind) {
            return false;
        }
        if (kind != UndoEditKind.TYPE && kind != UndoEditKind.DELETE) {
            return false;
        }
        if (now - lastUndoEditTimeMs > UNDO_COALESCE_MS) {
            return false;
        }
        if (kind == UndoEditKind.TYPE && incoming != null) {
            if (incoming.indexOf('\n') >= 0 || incoming.indexOf('\r') >= 0) {
                return false;
            }
            if (incoming.length() == 1 && Character.isWhitespace(incoming.charAt(0))) {
                return false;
            }
        }
        return true;
    }

    private void resetUndoCoalesceState() {
        lastUndoEditKind = null;
        lastUndoEditTimeMs = 0;
    }

    private Snapshot snapshot() {
        return new Snapshot(text.toString(), caret, anchor, new ArrayList<>(styles));
    }

    private void applySnapshot(Snapshot snapshot) {
        ViewportAnchor viewportAnchor = captureCaretViewportAnchor(caret);
        applyingSnapshot = true;
        try {
            text.setLength(0);
            text.append(snapshot.text());
            caret = snapshot.caret();
            anchor = snapshot.anchor();
            styles.clear();
            styles.addAll(snapshot.styles());
            preferredCaretX = Double.NaN;
            afterTextChanged(viewportAnchor, caret, true);
            resetUndoCoalesceState();
        } finally {
            applyingSnapshot = false;
        }
    }

    private void afterTextChanged() {
        afterTextChanged(null, caret, true);
    }

    private void afterTextChanged(ViewportAnchor viewportAnchor, boolean keepCaretVisible) {
        afterTextChanged(viewportAnchor, caret, keepCaretVisible);
    }

    private void afterTextChanged(ViewportAnchor viewportAnchor, int scrollSyncOffset, boolean keepCaretVisible) {
        invalidateLayoutCaches();
        caret = Math.max(0, Math.min(caret, text.length()));
        anchor = Math.max(0, Math.min(anchor, text.length()));
        scheduleImageSyncIfNeeded();
        rebuildStructuralMarkdownNow();
        scheduleAutoRuleRebuild();
        updateScrollBar();
        if (viewportAnchor != null) {
            restoreViewportAnchor(viewportAnchor, scrollSyncOffset);
        }
        if (keepCaretVisible) {
            if (viewportAnchor == null) {
                ensureCaretVisibleAfterInsert();
            } else {
                ensureCaretVisible();
            }
        }
        render();
        scheduleTextChangeNotification();
    }

    private void scheduleImageSyncIfNeeded() {
        if (textMightContainImages()) {
            imageSyncDelay.stop();
            imageSyncDelay.playFromStart();
            return;
        }
        if (!imageBlocks.isEmpty() || !hiddenImageBlockRanges.isEmpty()
                || !horizontalRules.isEmpty() || !hiddenHorizontalRuleRanges.isEmpty()) {
            imageBlocks.clear();
            hiddenImageBlockRanges.clear();
            horizontalRules.clear();
            hiddenHorizontalRuleRanges.clear();
            invalidateLayoutCaches();
        }
    }

    private boolean textMightContainImages() {
        return text.indexOf("![") >= 0 || !imageBlocks.isEmpty() || !hiddenImageBlockRanges.isEmpty();
    }

    private void scheduleTextChangeNotification() {
        if (textChangeNotificationPending) {
            return;
        }
        textChangeNotificationPending = true;
        Platform.runLater(() -> {
            textChangeNotificationPending = false;
            notifyTextChanged();
        });
    }

    private void notifyTextChanged() {
        if (textChangeListener != null) {
            textChangeListener.accept(getText());
        }
    }

    /** Lese-Position: erste sichtbare Zeile (für „Viewport beibehalten“ bei Fernersetzung). */
    private ViewportAnchor captureReadingViewportAnchor() {
        List<VisualLine> lines = visualLines();
        if (lines.isEmpty()) {
            return new ViewportAnchor(0, 0);
        }
        int lineIndex = Math.max(0, Math.min(lines.size() - 1, lineIndexAtContentY(scrollTop)));
        double lineTop = contentYForLineStart(lineIndex);
        return new ViewportAnchor(lines.get(lineIndex).start, lineTop - scrollTop);
    }

    /**
     * Nach Zeilenumbrüchen kein Viewport-Restore – Layout springt (Absatzlücke/WYSIWYG),
     * stattdessen {@link #ensureCaretVisible()}.
     */
    private ViewportAnchor viewportAnchorForReplacement(String replacement, int offset) {
        if (replacement != null && replacement.indexOf('\n') >= 0) {
            return null;
        }
        return captureCaretViewportAnchor(offset);
    }

    /** Caret-Zeile soll beim Tippen an derselben Viewport-Y-Position bleiben. */
    private ViewportAnchor captureCaretViewportAnchor(int offset) {
        int safe = Math.max(0, Math.min(text.length(), offset));
        TextPosition pos = positionForOffset(safe);
        double lineTop = contentYForLineStart(pos.lineIndex);
        return new ViewportAnchor(safe, lineTop - scrollTop);
    }

    private void restoreViewportAnchor(ViewportAnchor anchor, int syncOffset) {
        // syncOffset = Textoffset der Zeile, die an derselben Viewport-Y bleiben soll (Leseanker, nicht Caret).
        int safe = Math.max(0, Math.min(text.length(), syncOffset));
        TextPosition pos = positionForOffset(safe);
        double target = contentYForLineStart(pos.lineIndex) - anchor.viewportLineTop();
        double max = verticalScrollBar.getMax();
        double clamped = Math.max(0, Math.min(max, target));
        if (Math.abs(verticalScrollBar.getValue() - clamped) > 0.01) {
            verticalScrollBar.setValue(clamped);
        }
        scrollTop = verticalScrollBar.getValue();
    }

    private void rebuildAutoMarks() {
        if (text.isEmpty()) {
            markedAreas.removeIf(area -> area.autoRule);
            hiddenMarkupRanges.clear();
            headingRanges.clear();
            headingLevelByLineStart.clear();
            listLineInfoByStart.clear();
            orderedDisplayNumberByLineStart.clear();
            tableRowByLineStart.clear();
            tableBlockIdByLineStart.clear();
            tableColumnWidthsByBlockId.clear();
            tableLineStarts.clear();
            tableSeparatorLineStarts.clear();
            tableBlocks.clear();
            codeFenceBlocks.clear();
            codeFenceContentLineStarts.clear();
            blockStructureHiddenRanges.clear();
            blockquoteLineStarts.clear();
            centerLineStarts.clear();
            forceFullAutoMarkRebuild = true;
            autoMarkDirtyStart = Integer.MAX_VALUE;
            autoMarkDirtyEnd = 0;
            invalidateLayoutCaches();
            render();
            return;
        }
        boolean incremental = canRebuildAutoMarksIncrementally();
        boolean restoreReading = preserveReadingViewportOnNextRebuild;
        if (incremental) {
            rebuildAutoMarksIncremental(autoMarkDirtyStart, autoMarkDirtyEnd);
            finishAutoMarkRebuild(restoreReading);
        } else {
            rebuildAutoMarksFull();
            finishAutoMarkRebuild(restoreReading);
        }
        preserveReadingViewportOnNextRebuild = false;
        forceFullAutoMarkRebuild = false;
        autoMarkDirtyStart = Integer.MAX_VALUE;
        autoMarkDirtyEnd = 0;
    }

    private boolean canRebuildAutoMarksIncrementally() {
        if (forceFullAutoMarkRebuild || text.length() < LARGE_DOC_AUTO_MARK_THRESHOLD) {
            return false;
        }
        if (autoMarkDirtyStart > autoMarkDirtyEnd) {
            return false;
        }
        int span = autoMarkDirtyEnd - autoMarkDirtyStart;
        if (span > MAX_INCREMENTAL_AUTO_MARK_SPAN) {
            return false;
        }
        return !dirtyRegionTouchesBlockStructures(autoMarkDirtyStart, autoMarkDirtyEnd);
    }

    private boolean dirtyRegionTouchesBlockStructures(int dirtyStart, int dirtyEnd) {
        int from = Math.max(0, dirtyStart);
        int to = Math.min(text.length(), dirtyEnd);
        for (int i = from; i < to; i++) {
            char c = text.charAt(i);
            if (c == '`' || c == '|') {
                return true;
            }
            if (c == '\n' && i + 1 < to) {
                int lineEnd = indexOfNewline(i + 1);
                if (lineEnd < 0) {
                    lineEnd = text.length();
                }
                String line = text.substring(i + 1, Math.min(lineEnd, to));
                if (line.startsWith("- ") || line.startsWith("* ") || line.startsWith("+ ")
                        || line.startsWith("- [") || line.startsWith("* [")
                        || ORDERED_LIST_PREFIX.matcher(line).lookingAt()) {
                    return true;
                }
            }
        }
        return from == 0 && (textStartsWith("- ") || textStartsWith("* ") || textStartsWith("+ ")
                || textStartsWith("- [") || ORDERED_LIST_PREFIX.matcher(text).lookingAt());
    }

    private static final Pattern ORDERED_LIST_PREFIX = Pattern.compile("^\\d+\\.\\s");

    private void rebuildAutoMarksFull() {
        markedAreas.removeIf(area -> area.autoRule && area.autoRuleId >= 0);
        hiddenMarkupRanges.clear();
        String source = text.toString();
        applyAllAutoRules(source, 0, text.length());
        if (source.indexOf("](") >= 0) {
            rebuildLinkStyles(0, text.length());
        }
    }

    private void rebuildAutoMarksIncremental(int dirtyStart, int dirtyEnd) {
        int scanStart = logicalLineStartForOffset(Math.max(0, dirtyStart));
        int scanEnd = logicalLineEndIndex(Math.max(0, Math.min(text.length(), dirtyEnd)));
        if (scanEnd < scanStart) {
            scanEnd = scanStart;
        }
        removeAutoRuleContentInRange(scanStart, scanEnd);
        removeHiddenMarkupInRange(scanStart, scanEnd);
        String source = text.toString();
        applyAllAutoRules(source, scanStart, scanEnd);
        if (source.indexOf("](", scanStart) >= 0) {
            rebuildLinkStyles(scanStart, scanEnd);
        }
    }

    private void rebuildMarkdownBlockStylesIfNeeded(String source) {
        if (source == null || source.isEmpty()) {
            listLineInfoByStart.clear();
            orderedDisplayNumberByLineStart.clear();
            tableRowByLineStart.clear();
            tableBlockIdByLineStart.clear();
            tableColumnWidthsByBlockId.clear();
            tableLineStarts.clear();
            tableSeparatorLineStarts.clear();
            tableBlocks.clear();
            codeFenceBlocks.clear();
            codeFenceContentLineStarts.clear();
            blockStructureHiddenRanges.clear();
            return;
        }
        rebuildMarkdownBlockStyles(source);
    }

    /** Überschriften, Zitate, Listen, Tabellen, Code-Fences – sofort (nicht verzögert). */
    private void rebuildStructuralMarkdownNow() {
        if (text.isEmpty()) {
            rebuildMarkdownBlockStylesIfNeeded("");
            headingRanges.clear();
            headingLevelByLineStart.clear();
            centerLineStarts.clear();
            blockquoteLineStarts.clear();
            markedAreas.removeIf(area -> area.autoRule && area.isBlockquoteArea());
            return;
        }
        blockStructureHiddenRanges.clear();
        markedAreas.removeIf(area -> area.autoRule && area.isBlockquoteArea());
        rebuildHeadingStyles();
        rebuildBlockquoteAndCenterStyles();
        rebuildMarkdownBlockStyles(text.toString());
        sortBlockStructureHiddenRanges();
    }

    private void sortBlockStructureHiddenRanges() {
        if (blockStructureHiddenRanges.size() < 2) {
            return;
        }
        blockStructureHiddenRanges.sort(Comparator.comparingInt(range -> range.start));
    }

    private String resolveMonospaceFontFamily() {
        Set<String> available = new HashSet<>(Font.getFamilies());
        for (String candidate : new String[]{
                "SF Mono", "Menlo", "Monaco", "Consolas", "Courier New",
                "DejaVu Sans Mono", "Liberation Mono", "Monospaced"
        }) {
            if (available.contains(candidate)) {
                return candidate;
            }
        }
        for (String family : Font.getFamilies()) {
            String lower = family.toLowerCase(Locale.ROOT);
            if (lower.contains("mono") || lower.contains("courier") || lower.contains("consolas")) {
                return family;
            }
        }
        return "Monospaced";
    }

    private void removeAutoRuleContentInRange(int start, int end) {
        markedAreas.removeIf(area -> {
            if (!area.autoRule) {
                return false;
            }
            area.ranges.removeIf(range -> range.overlaps(start, end));
            return area.ranges.isEmpty() && area.clickCallback == null;
        });
    }

    private void removeHiddenMarkupInRange(int start, int end) {
        hiddenMarkupRanges.removeIf(range -> range.overlaps(start, end));
    }

    private boolean lineRangeMightHaveHeadings(int start, int end) {
        int lineStart = logicalLineStartForOffset(start);
        while (lineStart <= end && lineStart < text.length()) {
            if (text.charAt(lineStart) == '#') {
                return true;
            }
            int next = indexOfNewline(lineStart);
            if (next < 0) {
                break;
            }
            lineStart = next + 1;
        }
        return false;
    }

    private boolean lineRangeMightHaveBlockquotesOrCenter(int start, int end) {
        String slice = text.substring(start, Math.min(text.length(), end));
        return slice.indexOf('>') >= 0
                || slice.contains("<center")
                || slice.contains("</center")
                || slice.contains("<c>")
                || slice.contains("</c>");
    }

    private void applyAllAutoRules(String source, int scanStart, int scanEnd) {
        for (int i = 0; i < autoRules.size(); i++) {
            AutoRule rule = autoRules.get(i);
            MarkedArea area = findOrCreateAutoRuleArea(i, rule);
            addAutoRuleRanges(area, rule, scanStart, scanEnd);
        }
        for (MarkedArea area : markedAreas) {
            if (area.autoRule) {
                area.sortRanges();
            }
        }
    }

    private MarkedArea findOrCreateAutoRuleArea(int ruleIndex, AutoRule rule) {
        for (MarkedArea area : markedAreas) {
            if (area.autoRule && area.matchesAutoRule(ruleIndex)) {
                return area;
            }
        }
        MarkedArea area = new MarkedArea(this);
        area.autoRule = true;
        area.bindAutoRule(ruleIndex, rule);
        markedAreas.add(area);
        return area;
    }

    private void finishAutoMarkRebuild(boolean restoreReadingViewport) {
        mergeHiddenMarkupRanges();
        invalidateLayoutCaches();
        updateScrollBar();
        if (restoreReadingViewport) {
            ViewportAnchor anchor = captureReadingViewportAnchor();
            restoreViewportAnchor(anchor, anchor.offset());
        } else {
            ensureCaretVisibleAfterInsert();
        }
        render();
    }

    private void rebuildMarkdownBlockStyles(String source) {
        listLineInfoByStart.clear();
        orderedDisplayNumberByLineStart.clear();
        tableRowByLineStart.clear();
        tableBlockIdByLineStart.clear();
        tableColumnWidthsByBlockId.clear();
        tableLineStarts.clear();
        tableSeparatorLineStarts.clear();
        tableBlocks.clear();
        codeFenceBlocks.clear();
        codeFenceContentLineStarts.clear();
        List<MarkdownBlockSupport.ListLineInfo> parsedListLines = MarkdownBlockSupport.parseListLines(source);
        for (MarkdownBlockSupport.ListLineInfo info : parsedListLines) {
            listLineInfoByStart.put(info.lineStart(), info);
            if (renderMarkupHidden && info.prefixEnd() > info.lineStart()) {
                blockStructureHiddenRanges.add(new TextRange(info.lineStart(), info.prefixEnd()));
            }
        }
        orderedDisplayNumberByLineStart.putAll(
                MarkdownBlockSupport.computeOrderedDisplayNumbers(source, parsedListLines));
        int blockId = 0;
        for (MarkdownBlockSupport.TableBlock block : MarkdownBlockSupport.parseTableBlocks(source)) {
            tableBlocks.add(block);
            double[] columnWidths = computeTableColumnWidths(block);
            tableColumnWidthsByBlockId.put(blockId, columnWidths);
            for (MarkdownBlockSupport.TableRow row : block.rows()) {
                tableRowByLineStart.put(row.lineStart(), row);
                tableBlockIdByLineStart.put(row.lineStart(), blockId);
                if (row.separator()) {
                    tableSeparatorLineStarts.add(row.lineStart());
                } else {
                    tableLineStarts.add(row.lineStart());
                }
                if (renderMarkupHidden && row.lineEnd() > row.lineStart()) {
                    blockStructureHiddenRanges.add(new TextRange(row.lineStart(), row.lineEnd()));
                }
            }
            blockId++;
        }
        codeFenceBlocks.addAll(MarkdownBlockSupport.parseCodeFences(source));
        indexCodeFenceContentLineStarts(source);
        if (renderMarkupHidden) {
            for (MarkdownBlockSupport.CodeFenceBlock block : codeFenceBlocks) {
                int openLineEnd = source.indexOf('\n', block.start());
                if (openLineEnd < 0) {
                    openLineEnd = block.end();
                } else {
                    openLineEnd++;
                }
                if (openLineEnd > block.start()) {
                    blockStructureHiddenRanges.add(new TextRange(block.start(), Math.min(openLineEnd, block.end())));
                }
                if (block.contentEnd() < block.end()) {
                    int closeEnd = block.end();
                    if (closeEnd < source.length() && source.charAt(closeEnd) == '\n') {
                        closeEnd++;
                    }
                    blockStructureHiddenRanges.add(new TextRange(block.contentEnd(), closeEnd));
                }
            }
        }
        sortBlockStructureHiddenRanges();
        textWidthCache.clear();
        syncHorizontalRulesFromMarkdown();
    }

    private void indexCodeFenceContentLineStarts(String source) {
        for (MarkdownBlockSupport.CodeFenceBlock block : codeFenceBlocks) {
            if (block.contentEnd() <= block.contentStart()) {
                continue;
            }
            int scan = block.contentStart();
            while (scan < block.contentEnd()) {
                int lineStart = source.lastIndexOf('\n', scan - 1) + 1;
                codeFenceContentLineStarts.add(lineStart);
                int next = source.indexOf('\n', scan);
                if (next < 0 || next >= block.contentEnd()) {
                    break;
                }
                scan = next + 1;
            }
        }
    }

    private double[] computeTableColumnWidths(MarkdownBlockSupport.TableBlock block) {
        int columnCount = 0;
        for (MarkdownBlockSupport.TableRow row : block.rows()) {
            if (!row.separator()) {
                columnCount = Math.max(columnCount, row.cells().size());
            }
        }
        if (columnCount == 0) {
            return new double[0];
        }
        double[] widths = new double[columnCount];
        RenderStyle monoStyle = new RenderStyle();
        monoStyle.fontFamily = monospaceFontFamily;
        for (MarkdownBlockSupport.TableRow row : block.rows()) {
            if (row.separator()) {
                continue;
            }
            for (int c = 0; c < row.cells().size() && c < columnCount; c++) {
                String cellText = row.cells().get(c).text();
                double width = measureText(cellText.isEmpty() ? " " : cellText, monoStyle) + TABLE_CELL_PADDING_PX * 2;
                widths[c] = Math.max(widths[c], width);
            }
        }
        for (int c = 0; c < columnCount; c++) {
            if (widths[c] <= 0) {
                widths[c] = measureText(" ", monoStyle) + TABLE_CELL_PADDING_PX * 2;
            }
        }
        return widths;
    }

    private void rebuildLinkStyles(int scanStart, int scanEnd) {
        if (scanStart > 0 || scanEnd < text.length()) {
            markedAreas.removeIf(area -> area.autoRule && area.isLinkArea()
                    && area.intersectsRange(scanStart, scanEnd));
            removeHiddenMarkupInRange(scanStart, scanEnd);
        }
        int lineStart = logicalLineStartForOffset(scanStart);
        int limit = Math.min(text.length(), Math.max(scanEnd, lineStart));
        while (lineStart <= limit) {
            int lineEnd = indexOfNewline(lineStart);
            if (lineEnd < 0) {
                lineEnd = text.length();
            }
            if (lineEnd > scanEnd && lineStart > scanStart) {
                break;
            }
            CharSequence line = text.subSequence(lineStart, lineEnd);
            Matcher matcher = LINK_PATTERN.matcher(line);
            while (matcher.find()) {
                int textStart = lineStart + matcher.start(1);
                int textEnd = lineStart + matcher.end(1);
                String url = matcher.group(2);
                if (textStart >= textEnd || url == null || url.isBlank()) {
                    continue;
                }
                MarkedArea linkArea = new MarkedArea(this);
                linkArea.autoRule = true;
                linkArea.markTypeSilent(MarkedArea.Type.LINK);
                linkArea.markTextColor(LINK_TEXT_COLOR);
                linkArea.markUnderline(true);
                linkArea.addRangeSilent(textStart, textEnd);
                String linkUrl = url.trim();
                linkArea.onClick(() -> openExternalLink(linkUrl));
                markedAreas.add(linkArea);
                addHiddenMarkupRangesIfEnabled(lineStart + matcher.start(), lineStart + matcher.end(),
                        textStart, textEnd);
            }
            if (lineEnd >= text.length()) {
                break;
            }
            lineStart = lineEnd + 1;
        }
    }

    private static void openExternalLink(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (Exception ignored) {
            // Link öffnen ist optional – Editor bleibt benutzbar.
        }
    }

    private void mergeHiddenMarkupRanges() {
        if (hiddenMarkupRanges.size() < 2) {
            return;
        }
        hiddenMarkupRanges.sort(Comparator.comparingInt(range -> range.start));
        List<TextRange> merged = new ArrayList<>(hiddenMarkupRanges.size());
        TextRange current = hiddenMarkupRanges.get(0);
        for (int i = 1; i < hiddenMarkupRanges.size(); i++) {
            TextRange next = hiddenMarkupRanges.get(i);
            if (next.start <= current.end) {
                current.end = Math.max(current.end, next.end);
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        hiddenMarkupRanges.clear();
        hiddenMarkupRanges.addAll(merged);
    }

    private void rebuildHeadingStyles() {
        headingRanges.clear();
        headingLevelByLineStart.clear();
        int lineStart = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (i < text.length() && text.charAt(i) != '\n') {
                continue;
            }
            int lineEnd = i;
            if (lineEnd > lineStart && text.charAt(lineStart) == '#') {
                int hashes = 0;
                while (lineStart + hashes < lineEnd && text.charAt(lineStart + hashes) == '#') {
                    hashes++;
                }
                if (hashes > 0 && hashes <= 6 && lineStart + hashes < lineEnd
                        && text.charAt(lineStart + hashes) == ' ') {
                    int level = hashes;
                    int contentStart = lineStart + hashes + 1;
                    int contentEnd = lineEnd;
                    while (contentEnd > contentStart && text.charAt(contentEnd - 1) == '\r') {
                        contentEnd--;
                    }
                    if (contentEnd > contentStart) {
                        headingRanges.add(new HeadingRange(contentStart, contentEnd, level));
                    }
                    headingLevelByLineStart.put(lineStart, level);
                    addStructuralHiddenMarkupRangesIfEnabled(lineStart, lineEnd, contentStart, contentEnd);
                }
            }
            lineStart = i + 1;
        }
        textWidthCache.clear();
    }

    private void rebuildBlockquoteAndCenterStyles() {
        markedAreas.removeIf(area -> area.autoRule && area.isBlockquoteArea());
        centerLineStarts.clear();
        blockquoteLineStarts.clear();
        int lineStart = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (i < text.length() && text.charAt(i) != '\n') {
                continue;
            }
            int lineEnd = i;
            if (lineEnd > lineStart && text.charAt(lineStart) == '>' && (lineStart + 1 >= lineEnd
                    || text.charAt(lineStart + 1) == ' ')) {
                int contentStart = lineStart + 1;
                if (contentStart < lineEnd && text.charAt(contentStart) == ' ') {
                    contentStart++;
                }
                blockquoteLineStarts.add(lineStart);
                if (lineEnd > contentStart) {
                    MarkedArea area = new MarkedArea(this);
                    area.autoRule = true;
                    area.markBlockquoteArea();
                    area.markStyle(MarkedArea.ITALIC);
                    area.markTextColor(BLOCKQUOTE_TEXT_COLOR);
                    area.addRangeSilent(contentStart, lineEnd);
                    markedAreas.add(area);
                    addStructuralHiddenMarkupRangesIfEnabled(lineStart, lineEnd, contentStart, lineEnd);
                }
            }
            lineStart = i + 1;
        }
        String source = text.toString();
        if (source.contains("<center") || source.contains("<c>")) {
            Matcher centerMatcher = CENTER_TAG_PATTERN.matcher(source);
            while (centerMatcher.find()) {
                centerLineStarts.add(logicalLineStartForOffset(centerMatcher.start()));
                int contentStart = centerMatcher.start(1);
                int contentEnd = centerMatcher.end(1);
                addStructuralHiddenMarkupRangesIfEnabled(centerMatcher.start(), centerMatcher.end(), contentStart, contentEnd);
            }
        }
        rebuildHiddenBrTags();
    }

    private void rebuildHiddenBrTags() {
        if (!renderMarkupHidden) {
            return;
        }
        Matcher matcher = BR_TAG_PATTERN.matcher(text);
        while (matcher.find()) {
            blockStructureHiddenRanges.add(new TextRange(matcher.start(), matcher.end()));
        }
    }

    private void addStructuralHiddenMarkupRangesIfEnabled(int matchStart, int matchEnd, int contentStart, int contentEnd) {
        if (!renderMarkupHidden) {
            return;
        }
        if (matchStart < contentStart) {
            blockStructureHiddenRanges.add(new TextRange(matchStart, contentStart));
        }
        if (contentEnd < matchEnd) {
            blockStructureHiddenRanges.add(new TextRange(contentEnd, matchEnd));
        }
    }

    private void addHiddenMarkupRangesIfEnabled(int matchStart, int matchEnd, int contentStart, int contentEnd) {
        if (!renderMarkupHidden) {
            return;
        }
        addHiddenMarkupRanges(matchStart, matchEnd, contentStart, contentEnd);
    }

    private static String toWebHex(Color color) {
        return String.format("#%02x%02x%02x",
                (int) Math.round(color.getRed() * 255),
                (int) Math.round(color.getGreen() * 255),
                (int) Math.round(color.getBlue() * 255));
    }

    private void addAutoRuleRanges(MarkedArea area, AutoRule rule, int scanStart, int scanEnd) {
        Pattern pattern = rule.pattern();
        if (pattern == null) {
            return;
        }
        int lineStart = logicalLineStartForOffset(scanStart);
        int limit = Math.min(text.length(), Math.max(scanEnd, lineStart));
        while (lineStart <= limit) {
            int lineEnd = indexOfNewline(lineStart);
            if (lineEnd < 0) {
                lineEnd = text.length();
            }
            CharSequence line = text.subSequence(lineStart, lineEnd);
            if (lineMightMatchAutoRule(line, rule)) {
                Matcher matcher = pattern.matcher(line);
                while (matcher.find()) {
                    int groupIndex = rule.groupIndex();
                    if (groupIndex > matcher.groupCount()) {
                        continue;
                    }
                    int start = lineStart + matcher.start(groupIndex);
                    int end = lineStart + matcher.end(groupIndex);
                    if (start >= 0 && end > start) {
                        area.addRangeSilent(start, end);
                        addHiddenMarkupRangesIfEnabled(
                                lineStart + matcher.start(),
                                lineStart + matcher.end(),
                                start,
                                end);
                    }
                }
            }
            if (lineEnd >= text.length()) {
                break;
            }
            lineStart = lineEnd + 1;
        }
    }

    private static boolean lineMightMatchAutoRule(CharSequence line, AutoRule rule) {
        if (line.isEmpty()) {
            return false;
        }
        if (rule.quickChar() != 0) {
            for (int i = 0; i < line.length(); i++) {
                if (line.charAt(i) == rule.quickChar()) {
                    return true;
                }
            }
            return false;
        }
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '*' || c == '_' || c == '`' || c == '<' || c == '~' || c == '=') {
                return true;
            }
        }
        return false;
    }

    private void addHiddenMarkupRanges(int matchStart, int matchEnd, int contentStart, int contentEnd) {
        if (matchStart < contentStart) {
            hiddenMarkupRanges.add(new TextRange(matchStart, contentStart));
        }
        if (contentEnd < matchEnd) {
            hiddenMarkupRanges.add(new TextRange(contentEnd, matchEnd));
        }
    }

    private void adjustRangesForReplace(int start, int end, int insertedLength) {
        int removed = end - start;
        int delta = insertedLength - removed;
        for (StyleRange range : styles) {
            range.shiftAfterReplace(start, end, delta);
        }
        for (MarkedArea area : markedAreas) {
            area.shiftAfterReplace(start, end, delta);
        }
        for (TextRange range : hiddenMarkupRanges) {
            range.shiftAfterReplace(start, end, delta);
        }
        for (TextRange range : blockStructureHiddenRanges) {
            range.shiftAfterReplace(start, end, delta);
        }
        for (HeadingRange range : headingRanges) {
            range.shiftAfterReplace(start, end, delta);
        }
        for (TextRange range : hiddenHorizontalRuleRanges) {
            range.shiftAfterReplace(start, end, delta);
        }
        for (ParsedHorizontalRule rule : horizontalRules) {
            rule.shiftAfterReplace(start, end, delta);
        }
        shiftHeadingLineStarts(start, end, delta);
        shiftCenterLineStarts(start, end, delta);
        shiftBlockquoteLineStarts(start, end, delta);
        markAutoMarkDirty(start, end);
    }

    private void markAutoMarkDirty(int start, int end) {
        if (forceFullAutoMarkRebuild) {
            return;
        }
        autoMarkDirtyStart = Math.min(autoMarkDirtyStart, Math.max(0, start));
        autoMarkDirtyEnd = Math.max(autoMarkDirtyEnd, Math.min(text.length(), end));
    }

    private void scheduleAutoRuleRebuild() {
        autoRuleDelay.stop();
        autoRuleDelay.setDuration(text.length() >= LARGE_DOC_AUTO_MARK_THRESHOLD
                ? AUTO_RULE_DELAY_LARGE_DOC
                : AUTO_RULE_DELAY);
        autoRuleDelay.playFromStart();
    }

    private void shiftListLineStarts(int replaceStart, int replaceEnd, int delta) {
        if (listLineInfoByStart.isEmpty()) {
            return;
        }
        Map<Integer, MarkdownBlockSupport.ListLineInfo> shifted = new HashMap<>();
        for (Map.Entry<Integer, MarkdownBlockSupport.ListLineInfo> entry : listLineInfoByStart.entrySet()) {
            int offset = entry.getKey();
            if (offset >= replaceEnd) {
                shifted.put(offset + delta, entry.getValue());
            } else if (offset < replaceStart) {
                shifted.put(offset, entry.getValue());
            }
        }
        listLineInfoByStart.clear();
        listLineInfoByStart.putAll(shifted);
    }

    private void shiftTableLineStarts(int replaceStart, int replaceEnd, int delta) {
        if (tableLineStarts.isEmpty() && tableSeparatorLineStarts.isEmpty()) {
            return;
        }
        Set<Integer> shiftedTables = new HashSet<>();
        for (int offset : tableLineStarts) {
            if (offset >= replaceEnd) {
                shiftedTables.add(offset + delta);
            } else if (offset < replaceStart) {
                shiftedTables.add(offset);
            }
        }
        tableLineStarts.clear();
        tableLineStarts.addAll(shiftedTables);
        Set<Integer> shiftedSeparators = new HashSet<>();
        for (int offset : tableSeparatorLineStarts) {
            if (offset >= replaceEnd) {
                shiftedSeparators.add(offset + delta);
            } else if (offset < replaceStart) {
                shiftedSeparators.add(offset);
            }
        }
        tableSeparatorLineStarts.clear();
        tableSeparatorLineStarts.addAll(shiftedSeparators);
    }

    private void shiftCodeFenceBlocks(int replaceStart, int replaceEnd, int delta) {
        if (codeFenceBlocks.isEmpty()) {
            return;
        }
        List<MarkdownBlockSupport.CodeFenceBlock> shifted = new ArrayList<>();
        for (MarkdownBlockSupport.CodeFenceBlock block : codeFenceBlocks) {
            if (block.start() >= replaceEnd) {
                shifted.add(new MarkdownBlockSupport.CodeFenceBlock(
                        block.start() + delta, block.end() + delta,
                        block.contentStart() + delta, block.contentEnd() + delta));
            } else if (block.end() <= replaceStart) {
                shifted.add(block);
            }
        }
        codeFenceBlocks.clear();
        codeFenceBlocks.addAll(shifted);
    }

    private void shiftBlockquoteLineStarts(int replaceStart, int replaceEnd, int delta) {
        if (blockquoteLineStarts.isEmpty()) {
            return;
        }
        Set<Integer> shifted = new HashSet<>();
        for (int offset : blockquoteLineStarts) {
            if (offset >= replaceEnd) {
                shifted.add(offset + delta);
            } else if (offset < replaceStart) {
                shifted.add(offset);
            }
        }
        blockquoteLineStarts.clear();
        blockquoteLineStarts.addAll(shifted);
    }

    private void shiftCenterLineStarts(int replaceStart, int replaceEnd, int delta) {
        if (centerLineStarts.isEmpty()) {
            return;
        }
        Set<Integer> shifted = new HashSet<>();
        for (int offset : centerLineStarts) {
            if (offset >= replaceEnd) {
                shifted.add(offset + delta);
            } else if (offset < replaceStart) {
                shifted.add(offset);
            }
        }
        centerLineStarts.clear();
        centerLineStarts.addAll(shifted);
    }

    private void shiftHeadingLineStarts(int replaceStart, int replaceEnd, int delta) {
        if (headingLevelByLineStart.isEmpty()) {
            return;
        }
        Map<Integer, Integer> shifted = new HashMap<>();
        for (Map.Entry<Integer, Integer> entry : headingLevelByLineStart.entrySet()) {
            int offset = entry.getKey();
            if (offset >= replaceEnd) {
                shifted.put(offset + delta, entry.getValue());
            } else if (offset < replaceStart) {
                shifted.put(offset, entry.getValue());
            }
        }
        headingLevelByLineStart.clear();
        headingLevelByLineStart.putAll(shifted);
    }

    private void syncBlockLayoutFromMarkdown() {
        if (text.indexOf("![") >= 0) {
            syncImagesFromMarkdown();
        } else if (!imageBlocks.isEmpty() || !hiddenImageBlockRanges.isEmpty()) {
            imageBlocks.clear();
            hiddenImageBlockRanges.clear();
            invalidateLayoutCaches();
        }
        if (MarkdownBlockSupport.mightHaveHorizontalRules(text.toString())) {
            syncHorizontalRulesFromMarkdown();
        } else if (!horizontalRules.isEmpty() || !hiddenHorizontalRuleRanges.isEmpty()) {
            horizontalRules.clear();
            hiddenHorizontalRuleRanges.clear();
            invalidateLayoutCaches();
        }
    }

    private void syncImagesFromMarkdown() {
        imageBlocks.clear();
        hiddenImageBlockRanges.clear();
        if (text.isEmpty()) {
            return;
        }
        if (!shouldRenderImagePreview()) {
            return;
        }

        List<VisualLine> lines = visualLines();
        for (MarkdownImageSupport.ParsedBlock block : MarkdownImageSupport.parseBlocks(text.toString())) {
            File imageFile = MarkdownImageSupport.resolveImageFile(
                    block.imagePath(), imageMdDirectory, imageProjectDirectory);
            if (imageFile == null) {
                continue;
            }
            Image image = loadCachedImage(imageFile);
            if (image == null || image.getWidth() <= 0) {
                continue;
            }

            hiddenImageBlockRanges.add(new TextRange(block.start(), block.end()));

            int startLineIndex = lineIndexForOffset(block.start(), lines);
            int endLineIndex = lineIndexForOffset(Math.max(block.start(), block.end() - 1), lines);
            double widthPercent = block.widthPercent() > 0 ? block.widthPercent() : 80.0;
            double maxWidth = Math.max(80, availableContentWidth() * widthPercent / 100.0);
            double imageHeight = image.getHeight() * (maxWidth / image.getWidth());
            double displayHeight = imageHeight;
            String caption = block.caption();
            if (caption != null && !caption.isBlank()) {
                displayHeight += lineHeight();
            }
            imageBlocks.add(new ParsedImageBlock(
                    block.start(),
                    block.end(),
                    startLineIndex,
                    endLineIndex,
                    image,
                    widthPercent,
                    caption,
                    imageHeight,
                    displayHeight));
        }
    }

    private void syncHorizontalRulesFromMarkdown() {
        horizontalRules.clear();
        hiddenHorizontalRuleRanges.clear();
        if (text.isEmpty() || !renderMarkupHidden) {
            invalidateLayoutCaches();
            return;
        }
        List<VisualLine> lines = visualLines();
        double ruleHeight = Math.max(HORIZONTAL_RULE_MIN_HEIGHT, lineHeight() * 1.35);
        int lineStart = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (i < text.length() && text.charAt(i) != '\n') {
                continue;
            }
            int lineEnd = i;
            String lineText = MarkdownBlockSupport.normalizeTableLine(text.substring(lineStart, lineEnd)).trim();
            if (isTableMarkdownLine(lineStart, lineEnd)) {
                lineStart = i + 1;
                continue;
            }
            if (HORIZONTAL_RULE_LINE.matcher(lineText).matches()) {
                int startLineIndex = lineIndexForOffset(lineStart, lines);
                horizontalRules.add(new ParsedHorizontalRule(lineStart, lineEnd, startLineIndex, ruleHeight));
                hiddenHorizontalRuleRanges.add(new TextRange(lineStart, lineEnd));
            }
            lineStart = i + 1;
        }
        invalidateLayoutCaches();
    }

    private boolean isTableMarkdownLine(int lineStart, int lineEnd) {
        if (tableRowByLineStart.containsKey(lineStart)) {
            return true;
        }
        if (lineEnd <= lineStart) {
            return false;
        }
        return MarkdownBlockSupport.isTableMarkdownLine(text.substring(lineStart, lineEnd));
    }

    /** Tabellen-Trennzeile (|---|---|) erzeugt im WYSIWYG-Modus keine eigene Layout-Zeile. */
    private boolean shouldOmitTableSeparatorVisualLine(int lineStart) {
        if (!renderMarkupHidden) {
            return false;
        }
        if (tableSeparatorLineStarts.contains(lineStart)) {
            return true;
        }
        MarkdownBlockSupport.TableRow row = tableRowByLineStart.get(lineStart);
        return row != null && row.separator();
    }

    /** Leerzeile innerhalb einer Tabelle (z. B. nach DOCX-Laden) ausblenden. */
    private boolean shouldOmitTableGapVisualLine(int lineStart, int lineEnd) {
        if (!renderMarkupHidden || lineEnd < lineStart || tableRowByLineStart.containsKey(lineStart)) {
            return false;
        }
        if (!MarkdownBlockSupport.normalizeTableLine(text.substring(lineStart, lineEnd)).trim().isEmpty()) {
            return false;
        }
        for (MarkdownBlockSupport.TableBlock block : tableBlocks) {
            if (lineStart > block.startLine() && lineStart <= block.endLine()) {
                return true;
            }
        }
        return false;
    }

    private boolean shouldOmitTableStructureVisualLine(int lineStart, int lineEnd) {
        return shouldOmitTableSeparatorVisualLine(lineStart) || shouldOmitTableGapVisualLine(lineStart, lineEnd);
    }

    private int offsetAfterCollapsedTableSeparator(int offset) {
        int logicalStart = logicalLineStartForOffset(offset);
        if (!shouldOmitTableSeparatorVisualLine(logicalStart)) {
            return offset;
        }
        int lineEnd = logicalLineEndIndex(logicalStart);
        if (lineEnd < text.length() && text.charAt(lineEnd) == '\n') {
            return lineEnd + 1;
        }
        return Math.max(0, logicalStart);
    }

    private Image loadCachedImage(File imageFile) {
        String cacheKey = imageFile.getAbsolutePath();
        Image cached = imageCache.get(cacheKey);
        if (cached != null && !cached.isError() && cached.getWidth() > 0) {
            return cached;
        }
        Image loaded = MarkdownImageSupport.loadImage(imageFile);
        if (loaded != null) {
            imageCache.put(cacheKey, loaded);
        }
        return loaded;
    }

    private double gutterWidth() {
        return showLineNumbers ? GUTTER_WIDTH : 0;
    }

    private double textLeft() {
        return gutterWidth() + TEXT_PADDING;
    }

    private double textRight() {
        double width = canvas.getWidth() > 0 ? canvas.getWidth() : 800;
        return width - TEXT_RIGHT_PADDING;
    }

    private double availableContentWidth() {
        return Math.max(80, textRight() - gutterWidth());
    }

    private int lineIndexForOffset(int offset, List<VisualLine> lines) {
        int safeOffset = offsetAfterCollapsedTableSeparator(Math.max(0, Math.min(text.length(), offset)));
        for (int i = 0; i < lines.size(); i++) {
            VisualLine line = lines.get(i);
            if (safeOffset >= line.start && safeOffset < line.end) {
                return i;
            }
        }
        for (int i = 0; i < lines.size(); i++) {
            VisualLine line = lines.get(i);
            if (safeOffset == line.end
                    && line.end < text.length()
                    && text.charAt(line.end) == '\n') {
                return i;
            }
        }
        return Math.max(0, lines.size() - 1);
    }

    private double lineTopY(int lineIndex) {
        return contentYForLineStart(lineIndex) - scrollTop;
    }

    private double contentYForLineStart(int lineIndex) {
        if (cachedLineContentY != null && lineIndex >= 0 && lineIndex < cachedLineContentY.length) {
            return cachedLineContentY[lineIndex];
        }
        double y = 0;
        List<VisualLine> lines = visualLines();
        int limit = Math.max(0, Math.min(lineIndex, lines.size()));
        for (int i = 0; i < limit; i++) {
            y += segmentHeightForLineIndex(i, lines);
        }
        return y;
    }

    private double totalContentHeight() {
        if (cachedTotalContentHeight >= 0) {
            return cachedTotalContentHeight;
        }
        double y = 0;
        List<VisualLine> lines = visualLines();
        for (int i = 0; i < lines.size(); i++) {
            y += segmentHeightForLineIndex(i, lines);
        }
        return Math.max(canvas.getHeight(), y + 12);
    }

    private ParsedImageBlock imageBlockStartingAtLine(int lineIndex) {
        for (ParsedImageBlock block : imageBlocks) {
            if (block.startLineIndex() == lineIndex) {
                return block;
            }
        }
        return null;
    }

    private ParsedHorizontalRule horizontalRuleStartingAtLine(int lineIndex) {
        for (ParsedHorizontalRule rule : horizontalRules) {
            if (rule.startLineIndex == lineIndex) {
                return rule;
            }
        }
        return null;
    }

    private boolean isLineInsideHorizontalRule(int lineIndex) {
        if (lineIndex < 0) {
            return false;
        }
        List<VisualLine> lines = cachedVisualLines;
        if (lines != null && lineIndex < lines.size()) {
            int logicalStart = logicalLineStartForOffset(lines.get(lineIndex).start);
            if (tableSeparatorLineStarts.contains(logicalStart)) {
                return false;
            }
        }
        return horizontalRuleStartingAtLine(lineIndex) != null;
    }

    private ParsedImageBlock imageBlockAtContentY(double x, double contentY) {
        if (x < textLeft()) {
            return null;
        }
        for (ParsedImageBlock block : imageBlocks) {
            double top = contentYForLineStart(block.startLineIndex());
            double bottom = top + block.displayHeight();
            if (contentY >= top && contentY < bottom) {
                return block;
            }
        }
        return null;
    }

    public record ImageBlockInfo(int startOffset, int endOffset, String imagePath, int widthPercent, String caption) {
    }

    public ImageBlockInfo getImageBlockAtCaret() {
        ParsedImageBlock block = findImageBlockAtCaret();
        if (block == null) {
            return null;
        }
        String blockText = text.substring(block.startOffset(), block.endOffset());
        for (MarkdownImageSupport.ParsedBlock parsed : MarkdownImageSupport.parseBlocks(blockText)) {
            return new ImageBlockInfo(
                    block.startOffset(),
                    block.endOffset(),
                    parsed.imagePath(),
                    parsed.widthPercent() > 0 ? parsed.widthPercent() : 80,
                    parsed.caption());
        }
        return null;
    }

    public boolean updateImageBlockAtCaret(String caption, int widthPercent) {
        ImageBlockInfo info = getImageBlockAtCaret();
        if (info == null) {
            return false;
        }
        String fileName = info.imagePath();
        int slash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        if (slash >= 0) {
            fileName = fileName.substring(slash + 1);
        }
        String markdown = MarkdownImageSupport.buildMarkdown(fileName, caption, widthPercent);
        replaceRange(info.startOffset(), info.endOffset(), markdown);
        return true;
    }

    private ParsedImageBlock findImageBlockAtCaret() {
        if (hasSelection()) {
            int start = selectionStart();
            int end = selectionEnd();
            for (ParsedImageBlock block : imageBlocks) {
                if (start <= block.startOffset() && end >= block.endOffset()) {
                    return block;
                }
            }
        }
        for (ParsedImageBlock block : imageBlocks) {
            if (caret > block.startOffset() && caret <= block.endOffset()) {
                return block;
            }
        }
        return null;
    }

    public boolean deleteImageBlockAtCaret() {
        ImageBlockInfo info = getImageBlockAtCaret();
        if (info == null) {
            return false;
        }
        replaceRange(info.startOffset(), info.endOffset(), "");
        return true;
    }

    private int lineIndexAtContentY(double contentY) {
        if (cachedLineContentY != null && cachedLineSegmentHeight != null && cachedLineContentY.length > 0) {
            for (int i = 0; i < cachedLineContentY.length; i++) {
                if (contentY < cachedLineContentY[i] + cachedLineSegmentHeight[i]) {
                    return i;
                }
            }
            return cachedLineContentY.length - 1;
        }
        List<VisualLine> lines = visualLines();
        if (lines.isEmpty()) {
            return 0;
        }
        double y = 0;
        for (int i = 0; i < lines.size(); i++) {
            double segmentHeight = segmentHeightForLineIndex(i, lines);
            if (contentY < y + segmentHeight) {
                return i;
            }
            y += segmentHeight;
        }
        return lines.size() - 1;
    }

    private void updateScrollBar() {
        double contentHeight = totalContentHeight();
        double max = Math.max(0, contentHeight - canvas.getHeight());
        double oldMax = verticalScrollBar.getMax();
        double oldValue = verticalScrollBar.getValue();
        if (Math.abs(oldMax - max) > 0.01) {
            verticalScrollBar.setMax(max);
            if (max > oldMax) {
                // Inhalt gewachsen (z. B. Enter): absolute Position beibehalten, nicht proportional
                // nach oben schieben – danach folgt ensureCaretVisible bei Bedarf nach unten.
                verticalScrollBar.setValue(Math.min(oldValue, max));
            } else if (oldMax > 0.01 && max > 0.01) {
                double scaled = oldValue * (max / oldMax);
                verticalScrollBar.setValue(Math.max(0, Math.min(max, scaled)));
            }
        }
        verticalScrollBar.setMin(0);
        verticalScrollBar.setVisibleAmount(canvas.getHeight());
        verticalScrollBar.setBlockIncrement(Math.max(lineHeight(), canvas.getHeight() * 0.85));
        verticalScrollBar.setUnitIncrement(lineHeight());
        double clamped = Math.max(0, Math.min(max, verticalScrollBar.getValue()));
        if (Math.abs(verticalScrollBar.getValue() - clamped) > 0.01) {
            verticalScrollBar.setValue(clamped);
        }
        scrollTop = verticalScrollBar.getValue();
    }

    /** Lädt Scrollbar-CSS aus dem Classpath (config/css kann veraltet sein). */
    private void installBundledScrollbarStyles() {
        java.net.URL url = ManuskriptTextEditor.class.getResource("/css/editor-scrollbar.css");
        if (url == null) {
            return;
        }
        String uri = url.toExternalForm();
        if (!getStylesheets().contains(uri)) {
            getStylesheets().add(uri);
        }
    }

    private void render() {
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(editorBackgroundColor);
        gc.fillRect(0, 0, width, height);
        if (showLineNumbers) {
            gc.setFill(gutterBackgroundColor);
            gc.fillRect(0, 0, GUTTER_WIDTH, height);
        }

        List<VisualLine> lines = visualLines();
        int selectionStart = selectionStart();
        int selectionEnd = selectionEnd();
        int first = Math.max(0, lineIndexAtContentY(scrollTop) - 1);
        int last = Math.min(lines.size(), lineIndexAtContentY(scrollTop + height) + 2);

        for (int i = first; i < last; i++) {
            VisualLine line = lines.get(i);
            if (isLineInsideImageBlock(i) || isLineInsideHorizontalRule(i)) {
                continue;
            }
            if (showLineNumbers) {
                double y = lineTopY(i);
                gc.setFill(gutterTextColor);
                gc.fillText(String.valueOf(i + 1), 8, y + baselineForLine(i));
            }
        }

        paintImages(gc);
        paintHorizontalRules(gc);

        for (int i = first; i < last; i++) {
            if (isLineInsideImageBlock(i) || isLineInsideHorizontalRule(i)) {
                continue;
            }
            if (lineHeightForLineIndex(i) <= 0) {
                continue;
            }
            VisualLine line = lines.get(i);
            double lineY = lineTopY(i);
            paintListMarker(gc, line, i, lineY);
            paintBlockStructureLineBackground(gc, line, i, lineY);
            paintLineText(gc, line, i, lineY, selectionStart, selectionEnd);
        }

        paintCaret(gc, lines);
        maybeNotifySelectionChanged();
    }

    private void maybeNotifySelectionChanged() {
        if (caret == lastNotifiedCaret && anchor == lastNotifiedAnchor) {
            return;
        }
        lastNotifiedCaret = caret;
        lastNotifiedAnchor = anchor;
        if (selectionChangeListener != null) {
            selectionChangeListener.run();
        }
    }

    private void paintBlockStructureLineBackground(GraphicsContext gc, VisualLine line, int lineIndex, double y) {
        double bandHeight = paintBandHeight(lineIndex);
        if (bandHeight <= 0) {
            return;
        }
        int logicalStart = logicalLineStartForOffset(line.start);
        if (renderMarkupHidden
                && (tableLineStarts.contains(logicalStart) || tableSeparatorLineStarts.contains(logicalStart))) {
            return;
        }
        Color background = null;
        if (tableLineStarts.contains(logicalStart) || tableSeparatorLineStarts.contains(logicalStart)) {
            background = tableBlockBackgroundColor;
        } else if (codeFenceContentLineStarts.contains(logicalStart)) {
            background = codeBlockBackgroundColor;
        }
        if (background == null) {
            return;
        }
        double top = paintBandTop(y);
        double x = textLeft() + headingCenterOffset(line) + blockIndentOffset(line);
        double width = Math.max(MIN_CHAR_WIDTH, availableLineWidth() - headingCenterOffset(line) - blockIndentOffset(line));
        fillBackgroundRect(gc, x, top, width, bandHeight, background);
    }

    private void paintTableRowBackground(GraphicsContext gc, VisualLine line, int lineIndex, double y,
                                         MarkdownBlockSupport.TableRow tableRow) {
        double bandHeight = paintBandHeight(lineIndex);
        if (bandHeight <= 0) {
            return;
        }
        double top = paintBandTop(y);
        double baseX = textLeft() + headingCenterOffset(line) + blockIndentOffset(line);
        double rowWidth = Math.max(MIN_CHAR_WIDTH, availableLineWidth() - headingCenterOffset(line) - blockIndentOffset(line));
        Integer blockId = tableBlockIdByLineStart.get(tableRow.lineStart());
        double[] columnWidths = blockId == null ? null : tableColumnWidthsByBlockId.get(blockId);
        double tableWidth = columnWidths == null ? rowWidth : sumColumnWidths(columnWidths);

        fillBackgroundRect(gc, baseX, top, rowWidth, bandHeight, tableBlockBackgroundColor);
        gc.setStroke(tableBorderColor);
        gc.setLineWidth(1.0);

        if (tableRow.separator()) {
            return;
        }

        if (columnWidths == null || columnWidths.length == 0) {
            return;
        }

        if (isFirstVisibleTableRow(tableRow, blockId)) {
            gc.strokeLine(baseX, top, baseX + tableWidth, top);
        }
        gc.strokeLine(baseX, top, baseX, top + bandHeight);
        double x = baseX;
        for (int c = 0; c < tableRow.cells().size() && c < columnWidths.length; c++) {
            x += columnWidths[c];
            gc.strokeLine(x, top, x, top + bandHeight);
        }
        double bottomY = top + bandHeight;
        gc.strokeLine(baseX, bottomY, baseX + tableWidth, bottomY);
    }

    private void paintTableRowText(GraphicsContext gc, VisualLine line, int lineIndex, double y,
                                   MarkdownBlockSupport.TableRow tableRow) {
        if (tableRow.separator()) {
            return;
        }
        double bandHeight = paintBandHeight(lineIndex);
        if (bandHeight <= 0) {
            return;
        }
        double baseX = textLeft() + headingCenterOffset(line) + blockIndentOffset(line);
        Integer blockId = tableBlockIdByLineStart.get(tableRow.lineStart());
        double[] columnWidths = blockId == null ? null : tableColumnWidthsByBlockId.get(blockId);
        if (columnWidths == null || columnWidths.length == 0) {
            return;
        }

        RenderStyle cellStyle = new RenderStyle();
        cellStyle.fontFamily = monospaceFontFamily;
        gc.setFont(fontFor(cellStyle));
        gc.setFill(editorTextColor);
        double x = baseX;
        double baselineY = y + baselineForVisualLine(line);
        for (int c = 0; c < tableRow.cells().size() && c < columnWidths.length; c++) {
            x += TABLE_CELL_PADDING_PX;
            String cellText = tableRow.cells().get(c).text();
            if (!cellText.isEmpty()) {
                gc.fillText(cellText, x, baselineY);
            }
            x += Math.max(MIN_CHAR_WIDTH, columnWidths[c] - TABLE_CELL_PADDING_PX * 2);
            x += TABLE_CELL_PADDING_PX;
        }
    }

    /** Selektion in WYSIWYG-Tabellen: Zellenoffsets liegen in blockStructureHiddenRanges. */
    private void paintTableRowSelectionBackground(GraphicsContext gc, VisualLine line, int lineIndex, double y,
                                                  MarkdownBlockSupport.TableRow tableRow,
                                                  int selectionStart, int selectionEnd) {
        if (selectionStart >= selectionEnd || tableRow.separator()) {
            return;
        }
        double bandHeight = paintBandHeight(lineIndex);
        if (bandHeight <= 0) {
            return;
        }
        double top = paintBandTop(y);
        double height = bandHeight;
        double baseX = textLeft() + headingCenterOffset(line) + blockIndentOffset(line);
        Integer blockId = tableBlockIdByLineStart.get(tableRow.lineStart());
        double[] columnWidths = blockId == null ? null : tableColumnWidthsByBlockId.get(blockId);
        if (columnWidths == null || columnWidths.length == 0) {
            return;
        }
        RenderStyle cellStyle = new RenderStyle();
        cellStyle.fontFamily = monospaceFontFamily;
        double x = baseX;
        for (int c = 0; c < tableRow.cells().size() && c < columnWidths.length; c++) {
            MarkdownBlockSupport.TableCell cell = tableRow.cells().get(c);
            x += TABLE_CELL_PADDING_PX;
            double cellTextWidth = Math.max(MIN_CHAR_WIDTH, columnWidths[c] - TABLE_CELL_PADDING_PX * 2);
            int displayStart = tableCellDisplayStart(cell);
            int displayEnd = displayStart + cell.text().length();
            int overlapStart = Math.max(selectionStart, displayStart);
            int overlapEnd = Math.min(selectionEnd, displayEnd);
            if (overlapStart < overlapEnd) {
                String before = text.substring(displayStart, overlapStart);
                String selected = text.substring(overlapStart, overlapEnd);
                double selX = x + measureText(before, cellStyle);
                double selWidth = measureText(selected, cellStyle);
                if (selWidth > 0) {
                    fillBackgroundRect(gc, selX, top, selWidth, height, selectionColor);
                }
            }
            x += cellTextWidth;
            x += TABLE_CELL_PADDING_PX;
        }
    }

    private int tableCellDisplayStart(MarkdownBlockSupport.TableCell cell) {
        int start = cell.sourceStart();
        int end = cell.sourceEnd();
        while (start < end && Character.isWhitespace(text.charAt(start))) {
            start++;
        }
        return start;
    }

    private static double sumColumnWidths(double[] columnWidths) {
        double tableWidth = 0;
        for (double columnWidth : columnWidths) {
            tableWidth += columnWidth;
        }
        return tableWidth;
    }

    private boolean isFirstVisibleTableRow(MarkdownBlockSupport.TableRow row, Integer blockId) {
        if (blockId == null || blockId < 0 || blockId >= tableBlocks.size()) {
            return false;
        }
        for (MarkdownBlockSupport.TableRow candidate : tableBlocks.get(blockId).rows()) {
            if (!candidate.separator()) {
                return candidate.lineStart() == row.lineStart();
            }
        }
        return false;
    }

    private double tableSeparatorLineHeight() {
        return renderMarkupHidden ? 0.0 : TABLE_SEPARATOR_LINE_HEIGHT;
    }

    private void paintListMarker(GraphicsContext gc, VisualLine line, int lineIndex, double y) {
        if (!renderMarkupHidden || !isFirstVisualLineOfLogicalLine(line)) {
            return;
        }
        MarkdownBlockSupport.ListLineInfo info = listInfoForVisualLine(line);
        if (info == null) {
            return;
        }
        String marker;
        if (info.kind() == MarkdownBlockSupport.ListKind.TASK) {
            marker = info.taskChecked() ? "☑" : "☐";
        } else if (info.kind() == MarkdownBlockSupport.ListKind.ORDERED) {
            int displayNumber = orderedDisplayNumberByLineStart.getOrDefault(
                    info.lineStart(), info.orderNumber());
            marker = displayNumber + ".";
        } else {
            marker = info.bulletChar() == '*' ? "•" : info.bulletChar() == '+' ? "⊕" : "•";
        }
        double x = textLeft() + blockquoteIndentOffset(line) + info.nestLevel() * LIST_INDENT_PX + 2;
        gc.setFill(gutterTextColor);
        gc.setFont(Font.font(fontFamily, fontSize * 0.95));
        gc.fillText(marker, x, y + baselineForVisualLine(line));
    }

    private void paintLineText(GraphicsContext gc, VisualLine line, int lineIndex, double y,
                               int selectionStart, int selectionEnd) {
        int logicalStart = logicalLineStartForOffset(line.start);
        MarkdownBlockSupport.TableRow tableRow = tableRowByLineStart.get(logicalStart);
        if (tableRow != null && renderMarkupHidden) {
            paintTableRowBackground(gc, line, lineIndex, y, tableRow);
            paintTableRowSelectionBackground(gc, line, lineIndex, y, tableRow, selectionStart, selectionEnd);
            paintTableRowText(gc, line, lineIndex, y, tableRow);
            return;
        }
        if (codeFenceContentLineStarts.contains(logicalStart)) {
            paintLineSelectionBackground(gc, line, lineIndex, y, selectionStart, selectionEnd);
            paintLineMarkedBackgrounds(gc, line, lineIndex, y);
            paintLineTextSegments(gc, line, lineIndex, y, true);
            return;
        }
        if (isFirstVisualLineOfLogicalLine(line)) {
            Integer headingLevel = headingLevelForVisualLine(line);
            if (headingLevel != null) {
                paintLineSelectionBackground(gc, line, lineIndex, y, selectionStart, selectionEnd);
                paintLineMarkedBackgrounds(gc, line, lineIndex, y);
                paintCenteredLine(gc, line, y);
                return;
            }
            if (centerLineStarts.contains(logicalLineStartForOffset(line.start))) {
                paintLineSelectionBackground(gc, line, lineIndex, y, selectionStart, selectionEnd);
                paintLineMarkedBackgrounds(gc, line, lineIndex, y);
                paintCenteredLine(gc, line, y);
                return;
            }
        }
        paintLineSelectionBackground(gc, line, lineIndex, y, selectionStart, selectionEnd);
        paintLineMarkedBackgrounds(gc, line, lineIndex, y);
        if (justifyExtraPerGap != null && lineIndex < justifyExtraPerGap.length && justifyExtraPerGap[lineIndex] > 0) {
            paintLineTextJustified(gc, line, lineIndex, y, justifyExtraPerGap[lineIndex]);
            return;
        }
        paintLineTextSegments(gc, line, lineIndex, y);
    }

    /** Hintergrund für Lektorat/Textanalyse layoutgenau (ohne Lücken zwischen Wörtern). */
    private void paintLineMarkedBackgrounds(GraphicsContext gc, VisualLine line, int lineIndex, double y) {
        double bandHeight = paintBandHeight(lineIndex);
        if (bandHeight <= 0) {
            return;
        }
        double top = paintBandTop(y);
        double height = bandHeight;
        for (MarkedArea area : markedAreas) {
            if (area.color == null) {
                continue;
            }
            if (area.type != MarkedArea.Type.LEKTORAT && area.type != MarkedArea.Type.TEXT_ANALYSIS) {
                continue;
            }
            for (TextRange range : area.ranges) {
                paintVisibleRangeBackground(gc, line, lineIndex, top, height, range.start, range.end, area.color);
            }
        }
    }

    private void paintVisibleRangeBackground(GraphicsContext gc, VisualLine line, int lineIndex,
                                             double top, double height, int rangeStart, int rangeEnd, Color color) {
        int overlapStart = Math.max(line.start, rangeStart);
        int overlapEnd = Math.min(line.end, rangeEnd);
        if (overlapStart >= overlapEnd) {
            return;
        }
        int offset = overlapStart;
        while (offset < overlapEnd) {
            if (isHiddenOffset(offset)) {
                offset++;
                continue;
            }
            int runStart = offset;
            while (offset < overlapEnd && !isHiddenOffset(offset)) {
                offset++;
            }
            double x1 = xForOffsetInLine(line, lineIndex, runStart);
            double x2 = xForOffsetInLine(line, lineIndex, offset);
            if (x2 > x1) {
                fillBackgroundRect(gc, x1, top, x2 - x1, height, color);
            }
        }
    }

    private void paintLineSelectionBackground(GraphicsContext gc, VisualLine line, int lineIndex, double y,
                                              int selectionStart, int selectionEnd) {
        if (selectionStart >= selectionEnd) {
            return;
        }
        int overlapStart = Math.max(line.start, selectionStart);
        int overlapEnd = Math.min(line.end, selectionEnd);
        if (overlapStart >= overlapEnd) {
            return;
        }
        double bandHeight = paintBandHeight(lineIndex);
        if (bandHeight <= 0) {
            return;
        }
        double top = paintBandTop(y);
        double height = bandHeight;
        int offset = overlapStart;
        while (offset < overlapEnd) {
            if (isHiddenOffset(offset)) {
                offset++;
                continue;
            }
            int runStart = offset;
            while (offset < overlapEnd && !isHiddenOffset(offset)) {
                offset++;
            }
            double x1 = xForOffsetInLine(line, lineIndex, runStart);
            double x2 = xForOffsetInLine(line, lineIndex, offset);
            if (x2 > x1) {
                fillBackgroundRect(gc, x1, top, x2 - x1, height, selectionColor);
            }
        }
    }

    private void paintLineTextJustified(GraphicsContext gc, VisualLine line, int lineIndex, double y,
                                        double extraPerGap) {
        List<int[]> words = wordRangesOnLine(line);
        if (words.isEmpty()) {
            return;
        }
        double bandHeight = paintBandHeight(lineIndex);
        if (bandHeight <= 0) {
            return;
        }
        double x = textLeft() + headingCenterOffset(line) + blockIndentOffset(line);
        double top = paintBandTop(y);
        double height = bandHeight;
        for (int w = 0; w < words.size(); w++) {
            int wordStart = words.get(w)[0];
            int wordEnd = words.get(w)[1];
            paintStyledRange(gc, line, y, top, height, wordStart, wordEnd, x);
            x += measureRange(wordStart, wordEnd);
            if (w < words.size() - 1) {
                int gapStart = wordEnd;
                int gapEnd = words.get(w + 1)[0];
                x += measureRange(gapStart, gapEnd) + extraPerGap;
            }
        }
    }

    private void paintStyledRange(GraphicsContext gc, VisualLine line, double y, double top, double height,
                                  int rangeStart, int rangeEnd, double startX) {
        int segmentStart = rangeStart;
        double x = startX;
        while (segmentStart < rangeEnd) {
            if (isHiddenOffset(segmentStart)) {
                segmentStart++;
                continue;
            }
            RenderStyle style = styleAt(segmentStart);
            int segmentEnd = segmentStart + 1;
            while (segmentEnd < rangeEnd && !isHiddenOffset(segmentEnd) && sameTextStyle(style, styleAt(segmentEnd))) {
                segmentEnd++;
            }
            String segment = text.substring(segmentStart, segmentEnd);
            double segmentWidth = measureText(segment, style);
            if (style.background != null && !usesLayoutPaintedBackground(style)) {
                fillBackgroundRect(gc, x, top, segmentWidth, height, style.background);
            }
            gc.setFont(fontFor(style));
            gc.setFill(style.textColor != null ? style.textColor : editorTextColor);
            double baselineY = y + baselineForVisualLine(line) + style.baselineShift;
            gc.fillText(segment, x, baselineY);
            if (style.strikethrough) {
                gc.setStroke(style.textColor != null ? style.textColor : editorTextColor);
                gc.strokeLine(x, baselineY - 2, x + segmentWidth, baselineY - 2);
            }
            if (style.underline) {
                gc.setStroke(style.underlineColor == null ? editorTextColor : style.underlineColor);
                gc.strokeLine(x, baselineY + 2, x + segmentWidth, baselineY + 2);
            }
            x += segmentWidth;
            segmentStart = segmentEnd;
        }
    }

    private static boolean usesLayoutPaintedBackground(RenderStyle style) {
        return style.backgroundPriority == MarkedArea.Type.LEKTORAT.priority
                || style.backgroundPriority == MarkedArea.Type.TEXT_ANALYSIS.priority;
    }

    private void paintCenteredLine(GraphicsContext gc, VisualLine line, double y) {
        StringBuilder visible = new StringBuilder();
        int firstVisible = -1;
        for (int offset = line.start; offset < line.end; offset++) {
            if (isHiddenOffset(offset)) {
                continue;
            }
            if (firstVisible < 0) {
                firstVisible = offset;
            }
            visible.append(text.charAt(offset));
        }
        if (visible.isEmpty()) {
            return;
        }
        RenderStyle style = styleAt(firstVisible);
        gc.setFont(fontFor(style));
        gc.setFill(style.textColor != null ? style.textColor : editorTextColor);
        gc.setTextAlign(TextAlignment.CENTER);
        double centerX = textLeft() + availableLineWidth() / 2.0;
        gc.fillText(visible.toString(), centerX, y + baselineForVisualLine(line));
        gc.setTextAlign(TextAlignment.LEFT);
    }

    private void paintLineTextSegments(GraphicsContext gc, VisualLine line, int lineIndex, double y) {
        paintLineTextSegments(gc, line, lineIndex, y, false);
    }

    private void paintLineTextSegments(GraphicsContext gc, VisualLine line, int lineIndex, double y,
                                       boolean forceCodeBlockStyle) {
        double bandHeight = paintBandHeight(lineIndex);
        if (bandHeight <= 0) {
            return;
        }
        double top = paintBandTop(y);
        double height = bandHeight;
        int segmentStart = line.start;
        while (segmentStart < line.end) {
            if (isHiddenOffset(segmentStart)) {
                segmentStart++;
                continue;
            }
            RenderStyle style = styleAt(segmentStart);
            if (forceCodeBlockStyle) {
                applyCodeBlockStyle(style);
            }
            int segmentEnd = segmentStart + 1;
            while (segmentEnd < line.end && !isHiddenOffset(segmentEnd)) {
                RenderStyle nextStyle = styleAt(segmentEnd);
                if (forceCodeBlockStyle) {
                    applyCodeBlockStyle(nextStyle);
                }
                if (!sameTextStyle(style, nextStyle)) {
                    break;
                }
                segmentEnd++;
            }

            String segment = text.substring(segmentStart, segmentEnd);
            double x = xForOffsetInLine(line, lineIndex, segmentStart);
            double layoutWidth = xForOffsetInLine(line, lineIndex, segmentEnd) - x;
            double segmentWidth = Math.max(measureText(segment, style), layoutWidth);
            if (style.background != null && !usesLayoutPaintedBackground(style) && !forceCodeBlockStyle) {
                double bgWidth = Math.max(layoutWidth, segmentWidth);
                fillBackgroundRect(gc, x, top, bgWidth, height, style.background);
            }
            gc.setFont(fontFor(style));
            gc.setFill(style.textColor != null ? style.textColor : editorTextColor);
            double baselineY = y + baselineForVisualLine(line) + style.baselineShift;
            gc.fillText(segment, x, baselineY);
            if (style.strikethrough) {
                gc.setStroke(style.textColor != null ? style.textColor : editorTextColor);
                gc.strokeLine(x, baselineY - 2, x + segmentWidth, baselineY - 2);
            }
            if (style.underline) {
                gc.setStroke(style.underlineColor == null ? editorTextColor : style.underlineColor);
                gc.strokeLine(x, baselineY + 2, x + segmentWidth, baselineY + 2);
            }
            segmentStart = segmentEnd;
        }
    }

    private static void fillBackgroundRect(GraphicsContext gc, double x, double top, double width, double height,
                                           Color color) {
        if (width <= 0 || color == null) {
            return;
        }
        gc.setFill(color);
        double x0 = Math.floor(x);
        double x1 = Math.ceil(x + width + BACKGROUND_BLEED);
        double y0 = top - BACKGROUND_BLEED * 0.5;
        double h = height + BACKGROUND_BLEED;
        gc.fillRect(x0, y0, Math.max(CARET_WIDTH, x1 - x0), h);
    }

    private boolean shouldRenderImagePreview() {
        return renderMarkupHidden;
    }

    private boolean isLineInsideImageBlock(int lineIndex) {
        if (!shouldRenderImagePreview()) {
            return false;
        }
        for (ParsedImageBlock block : imageBlocks) {
            if (lineIndex >= block.startLineIndex() && lineIndex <= block.endLineIndex()) {
                return true;
            }
        }
        return false;
    }

    private void paintHorizontalRules(GraphicsContext gc) {
        if (!renderMarkupHidden || horizontalRules.isEmpty()) {
            return;
        }
        double contentWidth = availableContentWidth();
        double x1 = gutterWidth() + 10;
        double x2 = x1 + contentWidth;
        double viewportTop = scrollTop;
        double viewportBottom = scrollTop + canvas.getHeight();
        for (ParsedHorizontalRule rule : horizontalRules) {
            double top = contentYForLineStart(rule.startLineIndex);
            double bottom = top + rule.displayHeight;
            if (bottom < viewportTop || top > viewportBottom) {
                continue;
            }
            double y = top - scrollTop + rule.displayHeight / 2.0;
            gc.setStroke(gutterTextColor.deriveColor(0, 1, 1, 0.55));
            gc.setLineWidth(1);
            gc.strokeLine(x1, y, x2, y);
        }
        gc.setLineWidth(1);
    }

    private void paintImages(GraphicsContext gc) {
        if (!shouldRenderImagePreview() || imageBlocks.isEmpty()) {
            return;
        }
        double viewportTop = scrollTop;
        double viewportBottom = scrollTop + canvas.getHeight();
        double contentWidth = availableContentWidth();
        ParsedImageBlock highlightedBlock = hasInputFocus() ? findImageBlockAtCaret() : null;
        for (ParsedImageBlock block : imageBlocks) {
            double top = contentYForLineStart(block.startLineIndex());
            double bottom = top + block.displayHeight();
            if (bottom < viewportTop || top > viewportBottom) {
                continue;
            }
            double y = top - scrollTop;
            double maxWidth = Math.max(80, contentWidth * block.widthPercent() / 100.0);
            double imageHeight = block.imageHeight();
            double x = gutterWidth() + 10 + Math.max(0, (contentWidth - maxWidth) / 2.0);
            boolean selected = highlightedBlock != null && highlightedBlock.startOffset() == block.startOffset();
            if (selected) {
                paintImageSelectionBackground(gc, x, y, maxWidth, block.displayHeight());
            }
            gc.drawImage(block.image(), x, y, maxWidth, imageHeight);

            String caption = block.caption();
            if (caption != null && !caption.isBlank()) {
                gc.setFill(gutterTextColor);
                gc.setFont(Font.font(fontFamily, fontSize * 0.9));
                gc.setTextAlign(TextAlignment.CENTER);
                gc.fillText(caption, gutterWidth() + 10 + contentWidth / 2.0, y + imageHeight + baseline());
                gc.setTextAlign(TextAlignment.LEFT);
            }
            if (selected) {
                paintImageSelectionBorder(gc, x, y, maxWidth, block.displayHeight());
            }
        }
    }

    private void paintImageSelectionBackground(GraphicsContext gc, double x, double y, double width, double height) {
        double padding = 4;
        gc.setGlobalAlpha(0.28);
        gc.setFill(selectionColor);
        gc.fillRoundRect(x - padding, y - padding, width + padding * 2, height + padding * 2, 6, 6);
        gc.setGlobalAlpha(1.0);
    }

    private void paintImageSelectionBorder(GraphicsContext gc, double x, double y, double width, double height) {
        double padding = 4;
        gc.setStroke(caretColor);
        gc.setLineWidth(2);
        gc.strokeRoundRect(x - padding + 1, y - padding + 1, width + padding * 2 - 2, height + padding * 2 - 2, 6, 6);
    }

    private boolean hasInputFocus() {
        return canvas.isFocused() || isFocused() || keyboardProxy.isFocused();
    }

    private void paintCaret(GraphicsContext gc, List<VisualLine> lines) {
        if (!hasInputFocus() || lines.isEmpty()) {
            return;
        }
        if (findImageBlockAtCaret() != null) {
            return;
        }
        int safeCaret = normalizeCaretOffset(caret, true);
        if (safeCaret != caret) {
            caret = safeCaret;
            if (caret == anchor) {
                anchor = safeCaret;
            }
        }
        TextPosition pos = positionForOffset(caret);
        VisualLine line = lines.get(Math.max(0, Math.min(lines.size() - 1, pos.lineIndex)));
        double x = xForOffsetInLine(line, pos.lineIndex, caret);
        double y = lineTopY(pos.lineIndex) + 2;
        double lineH = lineHeightForLineIndex(pos.lineIndex);
        double caretH = lineH > 1 ? lineH - 4 : Math.max(4, lineHeight() - 4);
        gc.setFill(caretColor);
        gc.fillRect(x, y, CARET_WIDTH, caretH);
    }

    private Font fontFor(RenderStyle style) {
        FontWeight weight = (style.flags & MarkedArea.BOLD) != 0 ? FontWeight.BOLD : FontWeight.NORMAL;
        FontPosture posture = (style.flags & MarkedArea.ITALIC) != 0 ? FontPosture.ITALIC : FontPosture.REGULAR;
        return Font.font(style.fontFamily == null ? fontFamily : style.fontFamily, weight, posture,
                effectiveFontSize(style));
    }

    private double effectiveFontSize(RenderStyle style) {
        double size;
        if (style.fontSize > 0) {
            size = style.fontSize;
        } else if (style.fontSizeScale > 0) {
            size = fontSize * style.fontSizeScale;
        } else {
            size = fontSize;
        }
        if ((style.flags & (MarkedArea.SUPERSCRIPT | MarkedArea.SUBSCRIPT)) != 0) {
            size *= SUP_SUB_FONT_SCALE;
        }
        return size;
    }

    private double baselineShiftFor(RenderStyle style) {
        double size = effectiveFontSize(style);
        if ((style.flags & MarkedArea.SUPERSCRIPT) != 0) {
            return -size * 0.35;
        }
        if ((style.flags & MarkedArea.SUBSCRIPT) != 0) {
            return size * 0.2;
        }
        return 0;
    }

    private RenderStyle styleAt(int offset) {
        RenderStyle result = new RenderStyle();
        for (StyleRange range : styles) {
            if (range.contains(offset)) {
                range.applyTo(result);
            }
        }
        for (MarkedArea area : markedAreas) {
            area.applyTo(offset, result);
        }
        for (HeadingRange range : headingRanges) {
            if (range.contains(offset)) {
                result.fontSize = headingFontSize(range.level);
                result.flags |= MarkedArea.BOLD;
                result.textColor = headingColor(range.level);
            }
        }
        applyBlockStructureStyle(offset, result);
        result.baselineShift = baselineShiftFor(result);
        if ((result.flags & MarkedArea.STRIKETHROUGH) != 0) {
            result.strikethrough = true;
        }
        return result;
    }

    private void applyCodeBlockStyle(RenderStyle style) {
        style.fontFamily = monospaceFontFamily;
        if (style.background == null) {
            style.background = codeBlockBackgroundColor;
        }
    }

    private void applyBlockStructureStyle(int offset, RenderStyle result) {
        int lineStart = logicalLineStartForOffset(offset);
        if (codeFenceContentLineStarts.contains(lineStart)) {
            applyCodeBlockStyle(result);
            return;
        }
        if (tableLineStarts.contains(lineStart) || tableSeparatorLineStarts.contains(lineStart)) {
            result.fontFamily = monospaceFontFamily;
            if (tableSeparatorLineStarts.contains(lineStart)) {
                result.fontSizeScale = 0.92;
            }
            if (result.background == null) {
                result.background = tableBlockBackgroundColor;
            }
        }
    }

    private MarkdownBlockSupport.CodeFenceBlock codeFenceAt(int offset) {
        if (codeFenceBlocks.isEmpty()) {
            return null;
        }
        int lo = 0;
        int hi = codeFenceBlocks.size() - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            MarkdownBlockSupport.CodeFenceBlock block = codeFenceBlocks.get(mid);
            if (offset < block.start()) {
                hi = mid - 1;
            } else if (offset >= block.end()) {
                lo = mid + 1;
            } else {
                return block;
            }
        }
        return null;
    }

    private MarkedArea interactiveAreaAt(int offset) {
        if (offset < 0 || offset >= text.length() || isHiddenOffset(offset)) {
            return null;
        }
        return styleAt(offset).interactiveArea;
    }

    private List<VisualLine> visualLines() {
        double lineWidth = availableLineWidth();
        int hiddenBlocks = hiddenImageBlockRanges.size();
        if (cachedVisualLines != null
                && cachedVisualLinesTextLength == text.length()
                && cachedVisualLinesHiddenBlocks == hiddenBlocks
                && cachedVisualLinesHeadingCount == headingRanges.size()
                && cachedVisualLinesHorizontalRules == horizontalRules.size()
                && cachedVisualLinesBlockquotes == blockquoteLineStarts.size()
                && cachedVisualLinesLists == listLineInfoByStart.size()
                && cachedVisualLinesTables == tableLineStarts.size() + tableSeparatorLineStarts.size()
                && cachedVisualLinesCodeFences == codeFenceBlocks.size()
                && cachedVisualLinesMarkupHidden == renderMarkupHidden
                && Double.compare(cachedVisualLinesFontSize, fontSize) == 0
                && Double.compare(cachedVisualLinesLineSpacing, lineSpacing) == 0
                && Double.compare(cachedVisualLinesParagraphSpacing, paragraphSpacingPx) == 0
                && cachedVisualLinesJustifyText == justifyText
                && Double.compare(cachedVisualLinesWidth, lineWidth) == 0) {
            return cachedVisualLines;
        }
        cachedVisualLines = computeVisualLines();
        // Leerzeilen bleiben sichtbar; „Markup ausblenden“ betrifft nur Tags, nicht Zeilenumbrüche.
        collapsedBlankLineAtRunEnd = null;
        paragraphGapLine = null;
        if (justifyText) {
            rebuildJustifyExtraPerGap(cachedVisualLines);
        } else {
            justifyExtraPerGap = null;
        }
        rebuildLineVerticalMetrics(cachedVisualLines);
        cachedVisualLinesWidth = lineWidth;
        cachedVisualLinesTextLength = text.length();
        cachedVisualLinesHiddenBlocks = hiddenBlocks;
        cachedVisualLinesHeadingCount = headingRanges.size();
        cachedVisualLinesHorizontalRules = horizontalRules.size();
        cachedVisualLinesBlockquotes = blockquoteLineStarts.size();
        cachedVisualLinesLists = listLineInfoByStart.size();
        cachedVisualLinesTables = tableLineStarts.size() + tableSeparatorLineStarts.size();
        cachedVisualLinesCodeFences = codeFenceBlocks.size();
        cachedVisualLinesMarkupHidden = renderMarkupHidden;
        cachedVisualLinesFontSize = fontSize;
        cachedVisualLinesLineSpacing = lineSpacing;
        cachedVisualLinesParagraphSpacing = paragraphSpacingPx;
        cachedVisualLinesJustifyText = justifyText;
        return cachedVisualLines;
    }

    private void rebuildJustifyExtraPerGap(List<VisualLine> lines) {
        justifyExtraPerGap = new double[lines.size()];
        if (!justifyText || lines.isEmpty()) {
            return;
        }
        for (int i = 0; i < lines.size(); i++) {
            justifyExtraPerGap[i] = computeJustifyExtraPerGap(lines.get(i), i);
        }
    }

    private double computeJustifyExtraPerGap(VisualLine line, int lineIndex) {
        if (!canJustifyVisualLine(line, lineIndex)) {
            return 0;
        }
        List<int[]> words = wordRangesOnLine(line);
        if (words.size() < 2) {
            return 0;
        }
        double target = contentWidthForLine(line) - justifyInkSafetyPx(line);
        double natural = measureRange(line.start, line.end);
        if (natural >= target - 0.5) {
            return 0;
        }
        return (target - natural) / (words.size() - 1);
    }

    /**
     * Canvas-{@code fillText} zeichnet Glyphen oft etwas breiter als {@link #measureText};
     * Reserve skaliert mit der Zeilenlänge (breite Fenster → längere Zeilen).
     */
    private double justifyInkSafetyPx(VisualLine line) {
        int visibleChars = 0;
        for (int offset = line.start; offset < line.end; offset++) {
            if (!isHiddenOffset(offset) && !Character.isWhitespace(text.charAt(offset))) {
                visibleChars++;
            }
        }
        return Math.min(10.0, 1.0 + visibleChars * 0.05);
    }

    private double contentWidthForLine(VisualLine line) {
        return Math.max(MIN_CHAR_WIDTH, availableLineWidth() - headingCenterOffset(line) - blockIndentOffset(line));
    }

    private boolean canJustifyVisualLine(VisualLine line, int lineIndex) {
        if (!justifyText || isLineInsideImageBlock(lineIndex) || isLineInsideHorizontalRule(lineIndex)) {
            return false;
        }
        if (isWhitespaceOnlyVisualLine(line, lineIndex)) {
            return false;
        }
        if (headingLevelForVisualLine(line) != null && isFirstVisualLineOfLogicalLine(line)) {
            return false;
        }
        if (centerLineStarts.contains(logicalLineStartForOffset(line.start))) {
            return false;
        }
        if (isLastVisualLineOfLogicalParagraph(line)) {
            return false;
        }
        return wordRangesOnLine(line).size() >= 2;
    }

    private boolean isLastVisualLineOfLogicalParagraph(VisualLine line) {
        return line.end >= logicalLineEndIndex(line.start);
    }

    private List<int[]> wordRangesOnLine(VisualLine line) {
        List<int[]> words = new ArrayList<>();
        int index = line.start;
        while (index < line.end) {
            while (index < line.end && (isHiddenOffset(index) || Character.isWhitespace(text.charAt(index)))) {
                index++;
            }
            if (index >= line.end) {
                break;
            }
            int wordStart = index;
            while (index < line.end && !isHiddenOffset(index) && !Character.isWhitespace(text.charAt(index))) {
                index++;
            }
            words.add(new int[]{wordStart, index});
        }
        return words;
    }

    private int countJustifyGapsBefore(VisualLine line, int offset) {
        List<int[]> words = wordRangesOnLine(line);
        if (words.size() < 2) {
            return 0;
        }
        int gaps = 0;
        int safeOffset = Math.max(line.start, Math.min(line.end, offset));
        for (int w = 0; w < words.size() - 1; w++) {
            int gapStart = words.get(w)[1];
            if (safeOffset <= gapStart) {
                break;
            }
            if (safeOffset > words.get(w + 1)[0]) {
                gaps++;
            }
        }
        return gaps;
    }

    private double justifyExtraBefore(VisualLine line, int lineIndex, int offset) {
        if (justifyExtraPerGap == null || lineIndex < 0 || lineIndex >= justifyExtraPerGap.length) {
            return 0;
        }
        double extra = justifyExtraPerGap[lineIndex];
        if (extra <= 0) {
            return 0;
        }
        return countJustifyGapsBefore(line, offset) * extra;
    }

    /**
     * Im WYSIWYG-Modus: einzelne Leerzeile ausblenden; bei n aufeinanderfolgenden Leerzeilen
     * bleiben n−1 sichtbare Zeilenabstände (die letzte Zeile jeder Gruppe hat Höhe 0).
     */
    private void rebuildCollapsedBlankLineAtRunEnd(List<VisualLine> lines) {
        collapsedBlankLineAtRunEnd = new boolean[lines.size()];
        if (!renderMarkupHidden || lines.isEmpty()) {
            return;
        }
        int i = 0;
        while (i < lines.size()) {
            if (!isCollapsibleBlankVisualLine(lines.get(i), i)) {
                i++;
                continue;
            }
            while (i < lines.size() && isCollapsibleBlankVisualLine(lines.get(i), i)) {
                i++;
            }
            collapsedBlankLineAtRunEnd[i - 1] = true;
        }
    }

    /**
     * Einzeilige Leerzeile zwischen zwei nicht-leeren Zeilen (typischer Markdown-Absatzumbruch).
     */
    private void rebuildParagraphGapLines(List<VisualLine> lines) {
        paragraphGapLine = new boolean[lines.size()];
        if (lines.isEmpty()) {
            return;
        }
        boolean[] collapsible = new boolean[lines.size()];
        for (int i = 0; i < lines.size(); i++) {
            collapsible[i] = isCollapsibleBlankVisualLine(lines.get(i), i);
        }
        int i = 0;
        while (i < lines.size()) {
            if (!collapsible[i]) {
                i++;
                continue;
            }
            int runStart = i;
            while (i < lines.size() && collapsible[i]) {
                i++;
            }
            int runEnd = i - 1;
            if (runEnd == runStart
                    && hasNonEmptyVisualLineBefore(lines, collapsible, runStart)
                    && hasNonEmptyVisualLineAfter(lines, collapsible, runEnd)) {
                paragraphGapLine[runStart] = true;
            }
        }
    }

    private boolean hasNonEmptyVisualLineBefore(List<VisualLine> lines, boolean[] collapsible, int lineIndex) {
        for (int i = lineIndex - 1; i >= 0; i--) {
            if (isLineInsideImageBlock(i) || isLineInsideHorizontalRule(i)) {
                continue;
            }
            return !collapsible[i];
        }
        return false;
    }

    private boolean hasNonEmptyVisualLineAfter(List<VisualLine> lines, boolean[] collapsible, int lineIndex) {
        for (int i = lineIndex + 1; i < lines.size(); i++) {
            if (isLineInsideImageBlock(i) || isLineInsideHorizontalRule(i)) {
                continue;
            }
            return !collapsible[i];
        }
        return false;
    }

    private void rebuildLineVerticalMetrics(List<VisualLine> lines) {
        int count = lines.size();
        cachedLineContentY = new double[count];
        cachedLineSegmentHeight = new double[count];
        double y = 0;
        for (int i = 0; i < count; i++) {
            cachedLineContentY[i] = y;
            double segmentHeight = segmentHeightForLineIndex(i, lines);
            cachedLineSegmentHeight[i] = segmentHeight;
            y += segmentHeight;
        }
        cachedTotalContentHeight = Math.max(canvas.getHeight(), y + 12);
    }

    private double segmentHeightForLineIndex(int lineIndex, List<VisualLine> lines) {
        ParsedImageBlock blockStart = imageBlockStartingAtLine(lineIndex);
        if (blockStart != null) {
            return blockStart.displayHeight();
        }
        if (isLineInsideImageBlock(lineIndex) || isLineInsideHorizontalRule(lineIndex)) {
            return 0;
        }
        ParsedHorizontalRule ruleStart = horizontalRuleStartingAtLine(lineIndex);
        if (ruleStart != null) {
            return ruleStart.displayHeight;
        }
        if (lineIndex < 0 || lineIndex >= lines.size()) {
            return lineHeight();
        }
        VisualLine line = lines.get(lineIndex);
        if (shouldOmitTableStructureVisualLine(line.start, line.end)) {
            return 0;
        }
        if (renderMarkupHidden && isWhitespaceOnlyVisualLine(line, lineIndex)) {
            return Math.max(lineHeightForVisualLine(line), paragraphSpacingPx);
        }
        return lineHeightForVisualLine(line);
    }

    private boolean isParagraphGapLine(int lineIndex) {
        return paragraphGapLine != null
                && lineIndex >= 0
                && lineIndex < paragraphGapLine.length
                && paragraphGapLine[lineIndex];
    }

    /** Leerzeile (nur Leerzeichen und/oder ausgeblendetes Markup wie {@code <br>}). */
    private boolean isWhitespaceOnlyVisualLine(VisualLine line, int lineIndex) {
        if (isLineInsideImageBlock(lineIndex) || isLineInsideHorizontalRule(lineIndex)) {
            return false;
        }
        int logicalStart = logicalLineStartForOffset(line.start);
        if (tableLineStarts.contains(logicalStart) || tableSeparatorLineStarts.contains(logicalStart)) {
            return false;
        }
        if (codeFenceContentLineStarts.contains(logicalStart)) {
            return false;
        }
        if (headingLevelForVisualLine(line) != null) {
            return false;
        }
        if (horizontalRuleStartingAtLine(lineIndex) != null) {
            return false;
        }
        boolean hasVisibleContent = false;
        for (int offset = line.start; offset < line.end; offset++) {
            if (isHiddenOffset(offset)) {
                continue;
            }
            if (!Character.isWhitespace(text.charAt(offset))) {
                hasVisibleContent = true;
                break;
            }
        }
        if (hasVisibleContent) {
            return false;
        }
        if (line.end > line.start) {
            return true;
        }
        return line.start < text.length() && text.charAt(line.start) == '\n';
    }

    private boolean isCollapsibleBlankVisualLine(VisualLine line, int lineIndex) {
        return renderMarkupHidden && isWhitespaceOnlyVisualLine(line, lineIndex);
    }

    private boolean isBlankLineCollapsed(int lineIndex) {
        return collapsedBlankLineAtRunEnd != null
                && lineIndex >= 0
                && lineIndex < collapsedBlankLineAtRunEnd.length
                && collapsedBlankLineAtRunEnd[lineIndex];
    }

    private double measureOffsetWidth(int offset) {
        if (offset < 0 || offset >= text.length() || isHiddenOffset(offset)) {
            return 0;
        }
        return measureRange(offset, offset + 1);
    }

    private List<VisualLine> computeVisualLines() {
        List<VisualLine> lines = new ArrayList<>();
        int lineStart = 0;
        int i = 0;
        int lastBreakOffset = -1;
        double lineWidthSoFar = 0;
        double defaultWrapWidth = availableLineWidth();
        int guard = 0;
        while (i <= text.length()) {
            if (++guard > text.length() + 1024) {
                break;
            }
            if (i == text.length()) {
                if (!shouldOmitTableStructureVisualLine(lineStart, i)) {
                    lines.add(new VisualLine(lineStart, i));
                }
                break;
            }

            if (text.charAt(i) == '\n') {
                if (!shouldOmitTableStructureVisualLine(lineStart, i)) {
                    lines.add(new VisualLine(lineStart, i));
                }
                lineStart = i + 1;
                i++;
                lineWidthSoFar = 0;
                lastBreakOffset = -1;
                continue;
            }

            if (wrapText && isVisibleBreakOpportunity(i)) {
                lastBreakOffset = i;
            }

            double wrapWidth = defaultWrapWidth;
            VisualLine tempLine = new VisualLine(lineStart, lineStart);
            double indent = blockIndentOffset(tempLine);
            if (indent > 0) {
                wrapWidth = Math.max(MIN_CHAR_WIDTH, wrapWidth - indent);
            }

            double widthIncludingI = lineWidthSoFar + measureOffsetWidth(i);
            if (wrapText && i > lineStart && widthIncludingI > wrapWidth) {
                int breakOffset = lastBreakOffset >= lineStart ? lastBreakOffset + 1 : Math.max(lineStart + 1, i);
                if (breakOffset <= lineStart) {
                    breakOffset = Math.min(text.length(), lineStart + 1);
                }
                lines.add(new VisualLine(lineStart, breakOffset));
                lineStart = breakOffset;
                lineWidthSoFar = 0;
                i = lineStart;
                lastBreakOffset = -1;
                continue;
            }

            lineWidthSoFar = widthIncludingI;
            i++;
        }
        if (lines.isEmpty()) {
            lines.add(new VisualLine(0, 0));
        }
        return lines;
    }

    private double availableLineWidth() {
        return Math.max(measureText("MMMMMMMM", new RenderStyle()), textRight() - textLeft());
    }

    private boolean isVisibleBreakOpportunity(int offset) {
        return offset >= 0
                && offset < text.length()
                && !isHiddenOffset(offset)
                && Character.isWhitespace(text.charAt(offset));
    }

    private int offsetAt(double x, double yInContent) {
        List<VisualLine> lines = visualLines();
        int lineIndex = lineIndexAtContentY(yInContent);
        VisualLine line = lines.get(lineIndex);
        double localX = Math.max(0, x - textLeft());
        return normalizeCaretOffset(offsetAtLineX(line, lineIndex, localX), true);
    }

    private void selectWordAt(int offset) {
        int safe = normalizeCaretOffset(Math.max(0, Math.min(text.length(), offset)), true);
        int wordStart = safe;
        while (wordStart > 0 && isWordCharacter(text.charAt(wordStart - 1))) {
            wordStart--;
        }
        int wordEnd = safe;
        while (wordEnd < text.length() && isWordCharacter(text.charAt(wordEnd))) {
            wordEnd++;
        }
        if (wordStart >= wordEnd) {
            anchor = safe;
            caret = safe;
        } else {
            anchor = wordStart;
            caret = wordEnd;
        }
        preferredCaretX = Double.NaN;
        ensureCaretVisible();
        render();
    }

    private void selectParagraphAt(int offset) {
        int safe = Math.max(0, Math.min(text.length(), offset));
        anchor = findParagraphStart(safe);
        caret = findParagraphEnd(safe);
        preferredCaretX = Double.NaN;
        ensureCaretVisible();
        render();
    }

    private int findParagraphStart(int pos) {
        int searchPos = Math.max(0, Math.min(text.length(), pos));
        while (searchPos > 0) {
            if (searchPos >= 2 && text.charAt(searchPos - 1) == '\n' && text.charAt(searchPos - 2) == '\n') {
                return searchPos;
            }
            searchPos--;
        }
        return 0;
    }

    private int findParagraphEnd(int pos) {
        int searchPos = Math.max(0, Math.min(text.length(), pos));
        while (searchPos < text.length()) {
            if (searchPos + 1 < text.length()
                    && text.charAt(searchPos) == '\n'
                    && text.charAt(searchPos + 1) == '\n') {
                return searchPos + 2;
            }
            searchPos++;
        }
        return text.length();
    }

    private static boolean isWordCharacter(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '-';
    }

    private int normalizeCaretOffset(int offset, boolean forward) {
        int safe = Math.max(0, Math.min(text.length(), offset));
        if (text.isEmpty()) {
            return 0;
        }
        int guard = 0;
        while (guard++ <= text.length() + 2 && isHiddenOffset(safe)) {
            boolean snapped = false;
            if (shouldRenderImagePreview()) {
                for (TextRange range : hiddenImageBlockRanges) {
                    if (range.contains(safe)) {
                        safe = forward ? range.end : range.start;
                        snapped = true;
                        break;
                    }
                }
            }
            if (!snapped && renderMarkupHidden) {
                for (TextRange range : hiddenMarkupRanges) {
                    if (range.contains(safe)) {
                        safe = forward ? range.end : range.start;
                        snapped = true;
                        break;
                    }
                }
            }
            if (!snapped && renderMarkupHidden) {
                for (TextRange range : hiddenHorizontalRuleRanges) {
                    if (range.contains(safe)) {
                        safe = forward ? range.end : range.start;
                        snapped = true;
                        break;
                    }
                }
            }
            if (!snapped && renderMarkupHidden) {
                int lineStart = logicalLineStartForOffset(safe);
                if (shouldOmitTableSeparatorVisualLine(lineStart)) {
                    int lineEnd = logicalLineEndIndex(lineStart);
                    if (forward) {
                        safe = lineEnd < text.length() && text.charAt(lineEnd) == '\n' ? lineEnd + 1 : lineEnd;
                    } else {
                        safe = lineStart;
                    }
                    snapped = true;
                }
            }
            if (!snapped) {
                if (forward) {
                    if (safe >= text.length()) {
                        break;
                    }
                    safe++;
                } else {
                    if (safe <= 0) {
                        break;
                    }
                    safe--;
                }
            }
        }
        return Math.max(0, Math.min(text.length(), safe));
    }

    private boolean isHiddenImageBlockOffset(int offset) {
        for (TextRange range : hiddenImageBlockRanges) {
            if (range.contains(offset)) {
                return true;
            }
        }
        return false;
    }

    private int offsetAtLineX(VisualLine line, int lineIndex, double localX) {
        localX = Math.max(0, localX - headingCenterOffset(line) - blockIndentOffset(line));
        double extraPerGap = justifyExtraPerGap != null && lineIndex >= 0 && lineIndex < justifyExtraPerGap.length
                ? justifyExtraPerGap[lineIndex] : 0;
        if (extraPerGap > 0) {
            List<int[]> words = wordRangesOnLine(line);
            if (words.isEmpty()) {
                return normalizeCaretOffset(line.start, true);
            }
            double x = 0;
            for (int w = 0; w < words.size(); w++) {
                int wordStart = words.get(w)[0];
                int wordEnd = words.get(w)[1];
                double wordWidth = measureRange(wordStart, wordEnd);
                if (localX < x + wordWidth) {
                    return offsetAtRangeX(wordStart, wordEnd, x, localX);
                }
                x += wordWidth;
                if (w < words.size() - 1) {
                    int gapStart = wordEnd;
                    int gapEnd = words.get(w + 1)[0];
                    double gapWidth = measureRange(gapStart, gapEnd) + extraPerGap;
                    if (localX < x + gapWidth) {
                        return offsetAtJustifiedGapX(gapStart, gapEnd, x, gapWidth, localX);
                    }
                    x += gapWidth;
                }
            }
            int lastWordEnd = words.get(words.size() - 1)[1];
            if (localX >= x - CHAR_HIT_SLOP) {
                int lastChar = lastWordEnd - 1;
                while (lastChar >= line.start
                        && (isHiddenOffset(lastChar)
                        || Character.isWhitespace(text.charAt(lastChar)))) {
                    lastChar--;
                }
                if (lastChar >= line.start) {
                    double lastCharX = localXForOffsetInJustifiedLine(line, lastChar, extraPerGap);
                    if (localX < (lastCharX + x) / 2.0) {
                        return lastChar;
                    }
                }
                if (lastWordEnd < line.end) {
                    return offsetAtJustifiedTrailingX(line, lastWordEnd, x, localX);
                }
            }
            return Math.min(line.end, lastWordEnd);
        }
        int lastVisible = -1;
        for (int offset = line.start; offset < line.end; offset++) {
            if (isHiddenOffset(offset)) {
                continue;
            }
            lastVisible = offset;
            double nextX = measureRange(line.start, offset + 1) + justifyExtraBefore(line, lineIndex, offset + 1);
            if (localX < nextX + CHAR_HIT_SLOP) {
                return offset;
            }
        }
        if (lastVisible >= 0) {
            return Math.min(line.end, lastVisible + 1);
        }
        return normalizeCaretOffset(line.start, true);
    }

    private int offsetAtJustifiedGapX(int gapStart, int gapEnd, double gapStartX, double gapWidth, double localX) {
        if (gapEnd <= gapStart) {
            return localX < gapStartX + gapWidth / 2.0 ? gapStart : gapEnd;
        }
        double naturalGap = measureRange(gapStart, gapEnd);
        if (naturalGap <= 0) {
            return localX < gapStartX + gapWidth / 2.0 ? gapStart : gapEnd;
        }
        double posInGap = Math.max(0, Math.min(gapWidth, localX - gapStartX));
        double naturalPos = posInGap / gapWidth * naturalGap;
        return offsetAtRangeX(gapStart, gapEnd, gapStartX, gapStartX + naturalPos);
    }

    /** Caret in nachgelagertem Leerraum einer Blocksatz-Zeile (nicht mitgemalter Umbruch). */
    private int offsetAtJustifiedTrailingX(VisualLine line, int lastWordEnd, double contentEndX, double localX) {
        if (localX < contentEndX - CHAR_HIT_SLOP) {
            return Math.min(line.end, lastWordEnd);
        }
        for (int offset = lastWordEnd; offset < line.end; offset++) {
            if (isHiddenOffset(offset)) {
                continue;
            }
            return offset;
        }
        return Math.min(line.end, lastWordEnd);
    }

    /**
     * Horizontale Caret-Position in einer Blocksatz-Zeile — gleiche Wort-/Lücken-Logik wie
     * {@link #paintLineTextJustified}.
     */
    private double localXForOffsetInJustifiedLine(VisualLine line, int offset, double extraPerGap) {
        int safeOffset = Math.max(line.start, Math.min(line.end, offset));
        List<int[]> words = wordRangesOnLine(line);
        if (words.isEmpty()) {
            return 0;
        }
        double x = 0;
        for (int w = 0; w < words.size(); w++) {
            int wordStart = words.get(w)[0];
            int wordEnd = words.get(w)[1];
            if (safeOffset <= wordStart) {
                return x;
            }
            if (safeOffset < wordEnd) {
                return x + measureRange(wordStart, safeOffset);
            }
            x += measureRange(wordStart, wordEnd);
            if (w < words.size() - 1) {
                int gapStart = wordEnd;
                int gapEnd = words.get(w + 1)[0];
                double gapNatural = measureRange(gapStart, gapEnd);
                double gapWidth = gapNatural + extraPerGap;
                if (safeOffset < gapEnd) {
                    if (gapNatural > 0) {
                        double inGap = measureRange(gapStart, safeOffset);
                        return x + inGap / gapNatural * gapWidth;
                    }
                    return x;
                }
                x += gapWidth;
            }
        }
        return x;
    }

    /** Zeichengenauer Treffer innerhalb eines sichtbaren Bereichs (ohne Blocksatz-Zusatz). */
    private int offsetAtRangeX(int rangeStart, int rangeEnd, double rangeStartX, double localX) {
        int lastVisible = -1;
        for (int offset = rangeStart; offset < rangeEnd; offset++) {
            if (isHiddenOffset(offset)) {
                continue;
            }
            lastVisible = offset;
            double nextX = rangeStartX + measureRange(rangeStart, offset + 1);
            if (localX < nextX + CHAR_HIT_SLOP) {
                return offset;
            }
        }
        if (lastVisible >= 0) {
            return Math.min(rangeEnd, lastVisible + 1);
        }
        return rangeStart;
    }

    private int logicalLineStartForOffset(int offset) {
        if (offset <= 0) {
            return 0;
        }
        int newline = lastIndexOfNewlineBefore(offset - 1);
        return newline < 0 ? 0 : newline + 1;
    }

    private int indexOfNewline(int fromIndex) {
        for (int i = Math.max(0, fromIndex); i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                return i;
            }
        }
        return -1;
    }

    private int lastIndexOfNewlineBefore(int offset) {
        for (int i = Math.min(offset, text.length() - 1); i >= 0; i--) {
            if (text.charAt(i) == '\n') {
                return i;
            }
        }
        return -1;
    }

    private boolean textStartsWith(String prefix) {
        if (prefix.length() > text.length()) {
            return false;
        }
        for (int i = 0; i < prefix.length(); i++) {
            if (text.charAt(i) != prefix.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private boolean isFirstVisualLineOfLogicalLine(VisualLine line) {
        return line.start == logicalLineStartForOffset(line.start);
    }

    private Integer headingLevelForVisualLine(VisualLine line) {
        return headingLevelByLineStart.get(logicalLineStartForOffset(line.start));
    }

    private TextPosition positionForOffset(int offset) {
        int safeOffset = Math.max(0, Math.min(text.length(), offset));
        int lineIndex = visualLineIndexForOffset(safeOffset);
        VisualLine line = visualLines().get(lineIndex);
        return new TextPosition(lineIndex, safeOffset - line.start);
    }

    /**
     * Ermittelt die visuelle Zeile für einen Offset.
     * {@link VisualLine#end} ist bei Umbrüchen exklusiv, bei {@code \n}-Zeilen inklusiv (am Newline).
     */
    private int visualLineIndexForOffset(int offset) {
        int safeOffset = offsetAfterCollapsedTableSeparator(Math.max(0, Math.min(text.length(), offset)));
        List<VisualLine> lines = visualLines();
        for (int i = 0; i < lines.size(); i++) {
            VisualLine line = lines.get(i);
            if (safeOffset >= line.start && safeOffset < line.end) {
                return i;
            }
        }
        for (int i = 0; i < lines.size(); i++) {
            VisualLine line = lines.get(i);
            if (safeOffset == line.end
                    && line.end < text.length()
                    && text.charAt(line.end) == '\n') {
                return i;
            }
        }
        return Math.max(0, lines.size() - 1);
    }

    private int firstVisibleOffsetInLine(VisualLine line) {
        for (int offset = line.start; offset < line.end; offset++) {
            if (!isHiddenOffset(offset)) {
                return offset;
            }
        }
        return normalizeCaretOffset(line.start, true);
    }

    private int lastVisibleOffsetInLine(VisualLine line) {
        int lastVisible = -1;
        for (int offset = line.start; offset < line.end; offset++) {
            if (!isHiddenOffset(offset)) {
                lastVisible = offset;
            }
        }
        if (lastVisible >= 0) {
            return Math.min(line.end, lastVisible + 1);
        }
        return normalizeCaretOffset(line.start, false);
    }

    private void ensureCaretVisible() {
        TextPosition pos = positionForOffset(caret);
        double y = contentYForLineStart(pos.lineIndex);
        double lineBottom = y + segmentHeightForLine(pos.lineIndex);
        if (y < scrollTop) {
            verticalScrollBar.setValue(y);
        } else if (lineBottom > scrollTop + canvas.getHeight()) {
            verticalScrollBar.setValue(lineBottom - canvas.getHeight());
        }
    }

    /** Nach Zeilenumbruch: nur nach unten scrollen, wenn der Caret unterhalb des Viewports liegt. */
    private void ensureCaretVisibleAfterInsert() {
        TextPosition pos = positionForOffset(caret);
        double y = contentYForLineStart(pos.lineIndex);
        double lineBottom = y + segmentHeightForLine(pos.lineIndex);
        double viewportBottom = scrollTop + canvas.getHeight();
        if (lineBottom > viewportBottom) {
            verticalScrollBar.setValue(lineBottom - canvas.getHeight());
        } else if (y < scrollTop) {
            verticalScrollBar.setValue(y);
        }
    }

    private void scrollRangeToViewportCenter(int start, int end) {
        double viewportHeight = canvas.getHeight();
        if (viewportHeight <= 0) {
            return;
        }
        TextPosition startPos = positionForOffset(start);
        int endOffset = end > start ? end - 1 : start;
        TextPosition endPos = positionForOffset(endOffset);
        double top = contentYForLineStart(startPos.lineIndex);
        double bottom = contentYForLineStart(endPos.lineIndex) + segmentHeightForLine(endPos.lineIndex);
        double centerY = (top + bottom) / 2.0;
        double target = centerY - viewportHeight / 2.0;
        double max = verticalScrollBar.getMax();
        verticalScrollBar.setValue(Math.max(0, Math.min(max, target)));
        scrollTop = verticalScrollBar.getValue();
    }

    private double segmentHeightForLine(int lineIndex) {
        if (cachedLineSegmentHeight != null && lineIndex >= 0 && lineIndex < cachedLineSegmentHeight.length) {
            return cachedLineSegmentHeight[lineIndex];
        }
        return segmentHeightForLineIndex(lineIndex, visualLines());
    }

    private double lineHeightForLineIndex(int lineIndex) {
        List<VisualLine> lines = visualLines();
        if (lineIndex < 0 || lineIndex >= lines.size()) {
            return lineHeight();
        }
        if (lineIndex >= 0 && lineIndex < lines.size()) {
            VisualLine line = lines.get(lineIndex);
            if (shouldOmitTableStructureVisualLine(line.start, line.end)) {
                return 0;
            }
            if (renderMarkupHidden && isWhitespaceOnlyVisualLine(line, lineIndex)) {
                return Math.max(lineHeightForVisualLine(line), paragraphSpacingPx);
            }
            return lineHeightForVisualLine(line);
        }
        return lineHeight();
    }

    /** Oberkante für Selektion/Markierungs-Hintergrund (volle Zeilenhöhe). */
    private double paintBandTop(double lineY) {
        return lineY;
    }

    /** Höhe für Selektion/Markierungsband; entspricht der Layout-Zeilenhöhe. */
    private double paintBandHeight(int lineIndex) {
        double segmentHeight = lineHeightForLineIndex(lineIndex);
        if (segmentHeight <= 0) {
            return 0;
        }
        return segmentHeight;
    }

    private double lineHeightForVisualLine(VisualLine line) {
        int logicalStart = logicalLineStartForOffset(line.start);
        if (tableSeparatorLineStarts.contains(logicalStart)) {
            return tableSeparatorLineHeight();
        }
        double base = Math.ceil(maxFontSizeInLine(line) * lineSpacing);
        Integer level = headingLevelForVisualLine(line);
        if (level != null && isFirstVisualLineOfLogicalLine(line) && level == 1) {
            return base + fontSize * 0.4;
        }
        return base;
    }

    private double baselineForLine(int lineIndex) {
        List<VisualLine> lines = visualLines();
        if (lineIndex < 0 || lineIndex >= lines.size()) {
            return baseline();
        }
        return baselineForVisualLine(lines.get(lineIndex));
    }

    private double baselineForVisualLine(VisualLine line) {
        return Math.ceil(maxFontSizeInLine(line) * lineSpacing * BASELINE_TO_LINE_RATIO);
    }

    private double maxFontSizeInLine(VisualLine line) {
        double maxFont = fontSize;
        for (int offset = line.start; offset < line.end; offset++) {
            if (isHiddenOffset(offset)) {
                continue;
            }
            maxFont = Math.max(maxFont, effectiveFontSize(styleAt(offset)));
        }
        return maxFont;
    }

    private double headingFontSize(int level) {
        int index = Math.max(1, Math.min(6, level)) - 1;
        double scale = fontSize / BODY_FONT_REFERENCE;
        return HEADING_FONT_SIZES_AT_16[index] * scale;
    }

    private Color headingColor(int level) {
        int index = Math.max(1, Math.min(6, level)) - 1;
        return headingColors[index];
    }

    private double headingCenterOffset(VisualLine line) {
        if (!isFirstVisualLineOfLogicalLine(line)) {
            return 0;
        }
        if (headingLevelForVisualLine(line) != null
                || centerLineStarts.contains(logicalLineStartForOffset(line.start))) {
            double lineWidth = measureRange(line.start, line.end);
            return Math.max(0, (availableLineWidth() - lineWidth) / 2.0);
        }
        return 0;
    }

    private boolean isBlockquoteLineStart(int lineStart) {
        return blockquotePrefixLength(lineStart) > 0;
    }

    private int blockquotePrefixLength(int lineStart) {
        if (lineStart >= text.length() || text.charAt(lineStart) != '>') {
            return 0;
        }
        if (lineStart + 1 < text.length() && text.charAt(lineStart + 1) == ' ') {
            return 2;
        }
        return 1;
    }

    private boolean isBlockquoteVisualLine(VisualLine line) {
        return blockquoteLineStarts.contains(logicalLineStartForOffset(line.start));
    }

    private double blockquoteIndentOffset(VisualLine line) {
        if (!renderMarkupHidden) {
            return 0;
        }
        return isBlockquoteVisualLine(line) ? BLOCKQUOTE_INDENT_PX : 0;
    }

    private MarkdownBlockSupport.ListLineInfo listInfoForVisualLine(VisualLine line) {
        return listLineInfoByStart.get(logicalLineStartForOffset(line.start));
    }

    private double listIndentOffset(VisualLine line) {
        if (!renderMarkupHidden) {
            return 0;
        }
        MarkdownBlockSupport.ListLineInfo info = listInfoForVisualLine(line);
        if (info == null) {
            return 0;
        }
        return (info.nestLevel() + 1) * LIST_INDENT_PX;
    }

    private double blockIndentOffset(VisualLine line) {
        return blockquoteIndentOffset(line) + listIndentOffset(line);
    }

    /**
     * Enter erzeugt markdowngerechte Absätze: zwischen Absätzen muss eine Leerzeile in der Quelle stehen
     * ({@code \n\n}), sonst werden sie zu einem Absatz zusammengezogen.
     * In Listenzeilen: nächster Eintrag auf der folgenden Zeile (gleiche Ebene).
     */
    private String newlinesForEnter(int offset) {
        int safe = Math.max(0, Math.min(text.length(), offset));
        String listContinuation = listContinuationForEnter(safe);
        if (listContinuation != null) {
            return listContinuation;
        }
        if (!isAtEndOfLogicalLineContent(safe)) {
            return "\n\n";
        }
        int logicalEnd = logicalLineEndIndex(safe);
        if (countBlankLinesAfterLogicalLine(logicalEnd) >= 1) {
            return "\n";
        }
        boolean lineAlreadyEnded = logicalEnd < text.length() && text.charAt(logicalEnd) == '\n';
        return lineAlreadyEnded ? "\n" : "\n\n";
    }

    /**
     * {@code null} = keine Listenfortsetzung (leerer Eintrag beendet die Liste).
     */
    private String listContinuationForEnter(int offset) {
        int lineStart = logicalLineStartForOffset(offset);
        MarkdownBlockSupport.ListLineInfo info = listLineInfoByStart.get(lineStart);
        if (info == null) {
            return null;
        }
        int lineEnd = logicalLineEndIndex(offset);
        if (text.substring(info.contentStart(), lineEnd).trim().isEmpty()) {
            return null;
        }
        String indent = SOURCE_LIST_INDENT.repeat(info.nestLevel());
        return switch (info.kind()) {
            case ORDERED -> {
                int current = orderedDisplayNumberByLineStart.getOrDefault(lineStart, info.orderNumber());
                yield "\n" + indent + (current + 1) + ". ";
            }
            case UNORDERED -> "\n" + indent + info.bulletChar() + " ";
            case TASK -> "\n" + indent + "- [ ] ";
        };
    }

    private boolean isAtEndOfLogicalLineContent(int offset) {
        int lineEnd = logicalLineEndIndex(offset);
        for (int i = offset; i < lineEnd; i++) {
            if (!Character.isWhitespace(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private int logicalLineEndIndex(int offset) {
        int start = logicalLineStartForOffset(offset);
        int newline = indexOfNewline(start);
        return newline < 0 ? text.length() : newline;
    }

    private int countBlankLinesAfterLogicalLine(int logicalLineEndIndex) {
        int pos = logicalLineEndIndex;
        if (pos < text.length() && text.charAt(pos) == '\n') {
            pos++;
        } else {
            return 0;
        }
        int count = 0;
        while (pos <= text.length()) {
            int nextNewline = text.indexOf("\n", pos);
            int lineContentEnd = nextNewline < 0 ? text.length() : nextNewline;
            if (!isBlankLineContent(pos, lineContentEnd)) {
                break;
            }
            count++;
            if (nextNewline < 0) {
                break;
            }
            pos = nextNewline + 1;
        }
        return count;
    }

    private boolean isBlankLineContent(int start, int end) {
        for (int i = start; i < end; i++) {
            if (isHiddenOffset(i)) {
                continue;
            }
            if (!Character.isWhitespace(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private int lineStart(int offset) {
        return visualLines().get(visualLineIndexForOffset(offset)).start;
    }

    private int lineEnd(int offset) {
        return visualLines().get(visualLineIndexForOffset(offset)).end;
    }

    private boolean hasSelection() {
        return caret != anchor;
    }

    private int selectionStart() {
        return Math.min(caret, anchor);
    }

    private int selectionEnd() {
        return Math.max(caret, anchor);
    }

    private double xForOffsetInLine(VisualLine line, int lineIndex, int offset) {
        int safeOffset = Math.max(line.start, Math.min(line.end, offset));
        double baseX = textLeft() + headingCenterOffset(line) + blockIndentOffset(line);
        double extraPerGap = justifyExtraPerGap != null && lineIndex >= 0 && lineIndex < justifyExtraPerGap.length
                ? justifyExtraPerGap[lineIndex] : 0;
        if (extraPerGap > 0) {
            return baseX + localXForOffsetInJustifiedLine(line, safeOffset, extraPerGap);
        }
        return baseX + measureRange(line.start, safeOffset) + justifyExtraBefore(line, lineIndex, safeOffset);
    }

    private double measureRange(int start, int end) {
        int safeStart = Math.max(0, Math.min(text.length(), start));
        int safeEnd = Math.max(safeStart, Math.min(text.length(), end));
        double width = 0.0;
        int segmentStart = safeStart;
        while (segmentStart < safeEnd) {
            if (isHiddenOffset(segmentStart)) {
                segmentStart++;
                continue;
            }
            RenderStyle style = styleAt(segmentStart);
            int segmentEnd = segmentStart + 1;
            while (segmentEnd < safeEnd && !isHiddenOffset(segmentEnd) && sameTextStyle(style, styleAt(segmentEnd))) {
                segmentEnd++;
            }
            width += measureText(text.substring(segmentStart, segmentEnd), style);
            segmentStart = segmentEnd;
        }
        return width;
    }

    private boolean isHiddenOffset(int offset) {
        if (offset < 0 || offset >= text.length()) {
            return false;
        }
        for (TextRange range : hiddenImageBlockRanges) {
            if (range.contains(offset)) {
                return true;
            }
        }
        for (TextRange range : hiddenHorizontalRuleRanges) {
            if (range.contains(offset)) {
                return true;
            }
        }
        if (!renderMarkupHidden) {
            return false;
        }
        if (!hiddenMarkupRanges.isEmpty() && hiddenMarkupRangeContains(offset)) {
            return true;
        }
        return blockStructureHiddenRangeContains(offset);
    }

    private boolean blockStructureHiddenRangeContains(int offset) {
        if (blockStructureHiddenRanges.isEmpty()) {
            return false;
        }
        int lo = 0;
        int hi = blockStructureHiddenRanges.size() - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            TextRange range = blockStructureHiddenRanges.get(mid);
            if (offset < range.start) {
                hi = mid - 1;
            } else if (offset >= range.end) {
                lo = mid + 1;
            } else {
                return true;
            }
        }
        return false;
    }

    private boolean hiddenMarkupRangeContains(int offset) {
        int lo = 0;
        int hi = hiddenMarkupRanges.size() - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            TextRange range = hiddenMarkupRanges.get(mid);
            if (offset < range.start) {
                hi = mid - 1;
            } else if (offset >= range.end) {
                lo = mid + 1;
            } else {
                return true;
            }
        }
        return false;
    }

    private double measureText(String value, RenderStyle style) {
        if (value == null || value.isEmpty()) {
            return 0.0;
        }
        Font font = fontFor(style);
        MeasureKey key = new MeasureKey(value, font.getFamily(), font.getSize(),
                (style.flags & (MarkedArea.BOLD | MarkedArea.ITALIC | MarkedArea.SUPERSCRIPT | MarkedArea.SUBSCRIPT)));
        return textWidthCache.computeIfAbsent(key, ignored -> {
            measuringText.setText(value);
            measuringText.setFont(font);
            double pref = measuringText.prefWidth(-1);
            Bounds visual = measuringText.getBoundsInLocal();
            double visualWidth = visual.getMaxX() - visual.getMinX();
            return Math.max(MIN_CHAR_WIDTH, Math.max(pref, Math.max(visualWidth, measuringText.getLayoutBounds().getWidth())));
        });
    }

    private boolean sameTextStyle(RenderStyle a, RenderStyle b) {
        return (a.flags & (MarkedArea.BOLD | MarkedArea.ITALIC | MarkedArea.UNDERLINE
                | MarkedArea.STRIKETHROUGH | MarkedArea.SUPERSCRIPT | MarkedArea.SUBSCRIPT))
                == (b.flags & (MarkedArea.BOLD | MarkedArea.ITALIC | MarkedArea.UNDERLINE
                | MarkedArea.STRIKETHROUGH | MarkedArea.SUPERSCRIPT | MarkedArea.SUBSCRIPT))
                && Objects.equals(a.fontFamily, b.fontFamily)
                && Double.compare(a.fontSize, b.fontSize) == 0
                && Double.compare(a.fontSizeScale, b.fontSizeScale) == 0
                && Objects.equals(a.background, b.background)
                && Objects.equals(a.textColor, b.textColor)
                && a.underline == b.underline
                && a.strikethrough == b.strikethrough
                && Double.compare(a.baselineShift, b.baselineShift) == 0
                && Objects.equals(a.underlineColor, b.underlineColor);
    }

    private double lineHeight() {
        return Math.ceil(fontSize * lineSpacing);
    }

    private double baseline() {
        return Math.ceil(fontSize * lineSpacing * BASELINE_TO_LINE_RATIO);
    }

    public record SearchMatch(int start, int end) {}
    private record VisualLine(int start, int end) {}
    private record TextPosition(int lineIndex, int column) {}
    /** {@code viewportLineTop}: Abstand Zeilenanfang → Viewport-Oberkante (vor Layout-Änderung). */
    private record ViewportAnchor(int offset, double viewportLineTop) {}
    private record Snapshot(String text, int caret, int anchor, List<StyleRange> styles) {}
    private static final class AutoRule {
        private final Pattern pattern;
        private final int groupIndex;
        private final int styleFlags;
        private final Color textColor;
        private final Color backgroundColor;
        private final double fontSizeScale;
        private final String fontFamily;
        private final char quickChar;

        AutoRule(String regex, int groupIndex, int styleFlags) {
            this(regex, groupIndex, styleFlags, null, null, 0, null);
        }

        AutoRule(String regex, int groupIndex, int styleFlags,
                 Color textColor, Color backgroundColor, double fontSizeScale) {
            this(regex, groupIndex, styleFlags, textColor, backgroundColor, fontSizeScale, null);
        }

        AutoRule(String regex, int groupIndex, int styleFlags,
                 Color textColor, Color backgroundColor, double fontSizeScale,
                 String fontFamily) {
            pattern = compilePattern(regex);
            this.groupIndex = Math.max(0, groupIndex);
            this.styleFlags = styleFlags;
            this.textColor = textColor;
            this.backgroundColor = backgroundColor;
            this.fontSizeScale = fontSizeScale;
            this.fontFamily = fontFamily;
            quickChar = detectQuickChar(regex);
        }

        private static Pattern compilePattern(String regex) {
            try {
                return Pattern.compile(regex);
            } catch (PatternSyntaxException ignored) {
                return null;
            }
        }

        private static char detectQuickChar(String regex) {
            if (regex.startsWith("\\*\\*\\*")) {
                return '*';
            }
            if (regex.startsWith("\\*\\*")) {
                return '*';
            }
            if (regex.startsWith("~~")) {
                return '~';
            }
            if (regex.startsWith("==")) {
                return '=';
            }
            if (regex.startsWith("__")) {
                return '_';
            }
            if (regex.startsWith("`")) {
                return '`';
            }
            if (regex.startsWith("<")) {
                return '<';
            }
            return 0;
        }

        Pattern pattern() {
            return pattern;
        }

        int groupIndex() {
            return groupIndex;
        }

        int styleFlags() {
            return styleFlags;
        }

        Color textColor() {
            return textColor;
        }

        Color backgroundColor() {
            return backgroundColor;
        }

        double fontSizeScale() {
            return fontSizeScale;
        }

        String fontFamily() {
            return fontFamily;
        }

        char quickChar() {
            return quickChar;
        }
    }
    private static final class HeadingRange extends TextRange {
        final int level;

        HeadingRange(int start, int end, int level) {
            super(start, end);
            this.level = level;
        }
    }
    private record ParsedImageBlock(int startOffset, int endOffset, int startLineIndex, int endLineIndex,
                                    Image image, double widthPercent, String caption,
                                    double imageHeight, double displayHeight) {}

    private static final class ParsedHorizontalRule {
        int startOffset;
        int endOffset;
        int startLineIndex;
        final double displayHeight;

        ParsedHorizontalRule(int startOffset, int endOffset, int startLineIndex, double displayHeight) {
            this.startOffset = startOffset;
            this.endOffset = endOffset;
            this.startLineIndex = startLineIndex;
            this.displayHeight = displayHeight;
        }

        void shiftAfterReplace(int replaceStart, int replaceEnd, int delta) {
            if (endOffset <= replaceStart) {
                return;
            }
            if (startOffset >= replaceEnd) {
                startOffset += delta;
                endOffset += delta;
                return;
            }
            endOffset = Math.max(startOffset, endOffset + delta);
        }
    }
    private record MeasureKey(String text, String fontFamily, double fontSize, int styleFlags) {}

    private static class TextRange {
        int start;
        int end;

        TextRange(int start, int end) {
            this.start = Math.max(0, start);
            this.end = Math.max(this.start, end);
        }

        boolean contains(int offset) {
            return offset >= start && offset < end;
        }

        boolean overlaps(int otherStart, int otherEnd) {
            return start < otherEnd && end > otherStart;
        }

        void shiftAfterReplace(int replaceStart, int replaceEnd, int delta) {
            if (end <= replaceStart) {
                return;
            }
            if (start >= replaceEnd) {
                start += delta;
                end += delta;
                return;
            }
            end = Math.max(start, end + delta);
        }
    }

    private static final class StyleRange extends TextRange {
        Integer flags;
        String fontFamily;
        Double fontSize;

        StyleRange(int start, int end) {
            super(start, end);
        }

        static StyleRange flags(int start, int end, int flags) {
            StyleRange range = new StyleRange(start, end);
            range.flags = flags;
            return range;
        }

        static StyleRange fontFamily(int start, int end, String family) {
            StyleRange range = new StyleRange(start, end);
            range.fontFamily = family;
            return range;
        }

        static StyleRange fontSize(int start, int end, double size) {
            StyleRange range = new StyleRange(start, end);
            range.fontSize = size;
            return range;
        }

        void applyTo(RenderStyle style) {
            if (flags != null) {
                style.flags |= flags;
                if ((flags & MarkedArea.UNDERLINE) != 0) {
                    style.underline = true;
                }
            }
            if (fontFamily != null) {
                style.fontFamily = fontFamily;
            }
            if (fontSize != null) {
                style.fontSize = fontSize;
            }
        }
    }

    private static final class RenderStyle {
        int flags = 0;
        String fontFamily;
        double fontSize;
        double fontSizeScale;
        Color textColor;
        int textColorPriority = Integer.MIN_VALUE;
        Color background;
        int backgroundPriority = Integer.MIN_VALUE;
        boolean underline;
        Color underlineColor;
        int underlinePriority = Integer.MIN_VALUE;
        boolean strikethrough;
        double baselineShift;
        MarkedArea interactiveArea;
        int interactivePriority = Integer.MIN_VALUE;
    }

    public static final class MarkedArea {
        public static final boolean REGEX = true;
        public static final boolean TEXT = false;
        public static final int BOLD = 1;
        public static final int ITALIC = 2;
        public static final int UNDERLINE = 4;
        public static final int STRIKETHROUGH = 8;
        public static final int SUPERSCRIPT = 16;
        public static final int SUBSCRIPT = 32;
        public static final String LIGHTGREY = "#d9d9d9";
        public static final String SEARCH_MATCH_COLOR = "#ffeb3b";
        public static final String SEARCH_CURRENT_MATCH_COLOR = "#ff9800";

        public enum Type {
            MARKDOWN(10),
            TEXT_ANALYSIS(25),
            HIGHLIGHT(30),
            LINK(50),
            LEKTORAT(70),
            LANGUAGE_TOOL(80),
            SELECTION(100);

            private final int priority;

            Type(int priority) {
                this.priority = priority;
            }
        }

        private final ManuskriptTextEditor editor;
        private final List<TextRange> ranges = new ArrayList<>();
        private Color color;
        private Color textColor;
        private Color underlineColor;
        private int styleFlags;
        private double fontSizeScale;
        private String fontFamilyOverride;
        private boolean underline;
        private boolean autoRule;
        private int autoRuleId = -1;
        private boolean blockquoteArea;
        private String name;
        private Type type = Type.HIGHLIGHT;
        private Runnable clickCallback;

        private MarkedArea(ManuskriptTextEditor editor) {
            this.editor = Objects.requireNonNull(editor);
        }

        public MarkedArea searchAll(String query, boolean regex) {
            ranges.clear();
            for (SearchMatch match : editor.searchAll(query, regex, true)) {
                ranges.add(new TextRange(match.start(), match.end()));
            }
            editor.render();
            return this;
        }

        public MarkedArea addRange(int start, int end) {
            addRangeSilent(start, end);
            editor.render();
            return this;
        }

        private void addRangeSilent(int start, int end) {
            ranges.add(new TextRange(start, end));
        }

        private void sortRanges() {
            if (ranges.size() < 2) {
                return;
            }
            ranges.sort(Comparator.comparingInt(range -> range.start));
        }

        private void bindAutoRule(int ruleIndex, AutoRule rule) {
            autoRuleId = ruleIndex;
            markStyleSilent(rule.styleFlags());
            if (rule.textColor() != null) {
                textColor = rule.textColor();
            }
            if (rule.backgroundColor() != null) {
                color = rule.backgroundColor();
            }
            if (rule.fontSizeScale() > 0) {
                fontSizeScale = rule.fontSizeScale();
            }
            if (rule.fontFamily() != null) {
                fontFamilyOverride = rule.fontFamily();
            }
        }

        private void markStyleSilent(int styleFlags) {
            this.styleFlags |= styleFlags;
            if ((styleFlags & UNDERLINE) != 0) {
                underline = true;
            }
        }

        private boolean matchesAutoRule(int ruleIndex) {
            return autoRuleId == ruleIndex;
        }

        private boolean isLinkArea() {
            return type == Type.LINK;
        }

        private void markBlockquoteArea() {
            blockquoteArea = true;
        }

        private boolean isBlockquoteArea() {
            return blockquoteArea;
        }

        private boolean intersectsRange(int start, int end) {
            for (TextRange range : ranges) {
                if (range.overlaps(start, end)) {
                    return true;
                }
            }
            return false;
        }

        private MarkedArea markColorSilent(String colorValue) {
            color = Color.web(colorValue);
            return this;
        }

        public MarkedArea markColor(String colorValue) {
            color = Color.web(colorValue);
            editor.render();
            return this;
        }

        public MarkedArea markTextColor(Color value) {
            textColor = value;
            editor.render();
            return this;
        }

        public MarkedArea markFontSizeScale(double scale) {
            fontSizeScale = scale;
            editor.render();
            return this;
        }

        public MarkedArea markType(Type type) {
            this.type = type == null ? Type.HIGHLIGHT : type;
            editor.render();
            return this;
        }

        private MarkedArea markTypeSilent(Type type) {
            this.type = type == null ? Type.HIGHLIGHT : type;
            return this;
        }

        private MarkedArea markFontFamilySilent(String family) {
            this.fontFamilyOverride = family;
            return this;
        }

        public MarkedArea onClick(Runnable callback) {
            clickCallback = callback;
            editor.render();
            return this;
        }

        public MarkedArea markStyle(int styleFlags) {
            this.styleFlags |= styleFlags;
            if ((styleFlags & UNDERLINE) != 0) {
                underline = true;
            }
            editor.render();
            return this;
        }

        public MarkedArea markUnderline(boolean enabled) {
            underline = enabled;
            editor.render();
            return this;
        }

        public MarkedArea markUnderlineColor(String colorValue) {
            underlineColor = Color.web(colorValue);
            underline = true;
            editor.render();
            return this;
        }

        public void clear() {
            ranges.clear();
            editor.clearMarkedArea(this);
        }

        private void applyTo(int offset, RenderStyle style) {
            if (ranges.isEmpty()) {
                return;
            }
            int lo = 0;
            int hi = ranges.size() - 1;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                TextRange range = ranges.get(mid);
                if (offset < range.start) {
                    hi = mid - 1;
                } else if (offset >= range.end) {
                    lo = mid + 1;
                } else {
                    applyStyleAtOffset(style);
                    return;
                }
            }
        }

        private void applyStyleAtOffset(RenderStyle style) {
            style.flags |= styleFlags;
            int priority = type.priority;
            if ((clickCallback != null || type == Type.LANGUAGE_TOOL) && priority >= style.interactivePriority) {
                style.interactiveArea = this;
                style.interactivePriority = priority;
            }
            if (underline && priority >= style.underlinePriority) {
                style.underline = true;
                style.underlinePriority = priority;
            }
            if (underlineColor != null) {
                if (priority >= style.underlinePriority) {
                    style.underlineColor = underlineColor;
                    style.underlinePriority = priority;
                }
            }
            if (color != null && priority >= style.backgroundPriority) {
                if (type != Type.LEKTORAT && type != Type.TEXT_ANALYSIS) {
                    style.background = color;
                    style.backgroundPriority = priority;
                } else {
                    style.backgroundPriority = priority;
                }
            }
            if (textColor != null && priority >= style.textColorPriority) {
                style.textColor = textColor;
                style.textColorPriority = priority;
            }
            if (fontSizeScale > 0) {
                style.fontSizeScale = fontSizeScale;
            }
            if (fontFamilyOverride != null) {
                style.fontFamily = fontFamilyOverride;
            }
        }

        private void shiftAfterReplace(int start, int end, int delta) {
            for (TextRange range : ranges) {
                range.shiftAfterReplace(start, end, delta);
            }
        }

        private void fireClick() {
            if (clickCallback != null) {
                clickCallback.run();
            }
        }

        private boolean hasClickCallback() {
            return clickCallback != null;
        }

        @Override
        public String toString() {
            return name == null ? "MarkedArea" : name;
        }
    }
}
