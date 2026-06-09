package com.manuskript;

import com.manuskript.agent.SelectionRevisionSupport;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
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

import java.io.File;
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
    private Color[] headingColors = new Color[6];
    private final ArrayDeque<Snapshot> undoStack = new ArrayDeque<>();
    private final ArrayDeque<Snapshot> redoStack = new ArrayDeque<>();
    private UndoEditKind lastUndoEditKind;
    private long lastUndoEditTimeMs;
    private final PauseTransition autoRuleDelay = new PauseTransition(Duration.millis(350));
    private final PauseTransition widthLayoutDelay = new PauseTransition(Duration.millis(120));
    private final PauseTransition imageSyncDelay = new PauseTransition(Duration.millis(400));
    private final Text measuringText = new Text();
    private final Map<MeasureKey, Double> textWidthCache = new HashMap<>();
    private final Map<String, Image> imageCache = new HashMap<>();
    private final ContextMenu languageToolContextMenu = new ContextMenu();
    private final ContextMenu editorContextMenu = new ContextMenu();
    private ChapterRewriteContextActions contextMenuRewriteActions;
    private Runnable selectionRevisionAgentAction;
    private int contextMenuThemeIndex;
    private final Popup languageToolHoverPopup = new Popup();
    private final Label languageToolHoverMessage = new Label();
    private final Label languageToolHoverSuggestion = new Label();
    private List<LanguageToolService.Match> currentLanguageToolMatches = new ArrayList<>();
    private LanguageToolDictionary languageToolDictionary;
    private Runnable onLanguageToolMatchesChanged;
    private LanguageToolService.Match hoveredLanguageToolMatch;
    private EventHandler<MouseEvent> contextMenuOutsideClickFilter;
    private Consumer<String> textChangeListener;
    private Runnable selectionChangeListener;
    private int lastNotifiedCaret = -1;
    private int lastNotifiedAnchor = -2;
    private boolean textChangeNotificationPending;

    private String fontFamily = "Segoe UI";
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
        canvas.setWidth(Math.max(0, width - barWidth));
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
        ensureCaretVisible();
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
        autoRuleDelay.stop();
        autoRuleDelay.playFromStart();
        rebuildHeadingStyles();
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
        ViewportAnchor viewportAnchor = captureCaretViewportAnchor(start);
        text.replace(start, end, value);
        int inserted = value.length();
        adjustRangesForReplace(start, end, inserted);
        caret = normalizeCaretOffset(start, true);
        anchor = caret;
        preferredCaretX = Double.NaN;
        afterTextChanged(viewportAnchor, caret, true);
    }

    public void replaceSelection(String replacement) {
        pushUndoCoalesced(classifyUndoKind(replacement), replacement);
        int start = selectionStart();
        int end = selectionEnd();
        ViewportAnchor viewportAnchor = captureCaretViewportAnchor(start);
        text.replace(start, end, replacement == null ? "" : replacement);
        int inserted = replacement == null ? 0 : replacement.length();
        adjustRangesForReplace(start, end, inserted);
        caret = normalizeCaretOffset(start + inserted, true);
        anchor = caret;
        preferredCaretX = Double.NaN;
        afterTextChanged(viewportAnchor, caret, true);
    }

    public void replaceRange(int start, int end, String replacement) {
        replaceRange(start, end, replacement, false);
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
        afterTextChanged(viewportAnchor, mappedCaret, false);
    }

    public void replaceRange(int start, int end, String replacement, boolean preserveCaretAndViewport) {
        int safeStart = Math.max(0, Math.min(text.length(), start));
        int safeEnd = Math.max(safeStart, Math.min(text.length(), end));
        String oldContent = text.toString();
        String newSegment = replacement == null ? "" : replacement;
        ViewportAnchor viewportAnchor = preserveCaretAndViewport ? captureReadingViewportAnchor() : null;
        int mappedCaret = preserveCaretAndViewport
                ? mapOffsetThroughTextChange(oldContent, buildTextAfterReplace(oldContent, safeStart, safeEnd, newSegment), caret)
                : safeStart + newSegment.length();
        int mappedAnchor = preserveCaretAndViewport
                ? mapOffsetThroughTextChange(oldContent, buildTextAfterReplace(oldContent, safeStart, safeEnd, newSegment), anchor)
                : mappedCaret;
        pushUndoCoalesced(UndoEditKind.OTHER, newSegment);
        text.replace(safeStart, safeEnd, newSegment);
        adjustRangesForReplace(safeStart, safeEnd, newSegment.length());
        caret = normalizeCaretOffset(mappedCaret, true);
        anchor = normalizeCaretOffset(mappedAnchor, true);
        preferredCaretX = Double.NaN;
        afterTextChanged(viewportAnchor, caret, !preserveCaretAndViewport);
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
        if (before == null || after == null) {
            return Math.max(0, offset);
        }
        if (before.equals(after)) {
            return Math.max(0, Math.min(offset, after.length()));
        }
        int beforeLength = before.length();
        int afterLength = after.length();
        int safeOffset = Math.max(0, Math.min(offset, beforeLength));
        if (safeOffset == 0) {
            return 0;
        }
        if (safeOffset >= beforeLength) {
            return afterLength;
        }

        int prefix = 0;
        int prefixLimit = Math.min(Math.min(safeOffset, beforeLength), afterLength);
        while (prefix < prefixLimit && before.charAt(prefix) == after.charAt(prefix)) {
            prefix++;
        }
        if (safeOffset <= prefix) {
            return safeOffset;
        }

        int suffix = 0;
        while (suffix < beforeLength - prefix
                && suffix < afterLength - prefix
                && before.charAt(beforeLength - 1 - suffix) == after.charAt(afterLength - 1 - suffix)) {
            suffix++;
        }

        int beforeMiddleLength = beforeLength - prefix - suffix;
        int afterMiddleLength = afterLength - prefix - suffix;
        int offsetInMiddle = safeOffset - prefix;
        if (offsetInMiddle >= beforeMiddleLength) {
            return afterLength - (beforeLength - safeOffset);
        }
        if (beforeMiddleLength <= 0) {
            return prefix;
        }
        int mappedMiddle = (int) Math.round(offsetInMiddle * (afterMiddleLength / (double) beforeMiddleLength));
        return prefix + Math.max(0, Math.min(afterMiddleLength, mappedMiddle));
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
        rebuildAutoMarks();
    }

    private void addAutoRuleEntry(String regex, int groupIndex, int styleFlags) {
        addAutoRuleEntry(regex, groupIndex, styleFlags, null, null, 0);
    }

    private void addAutoRuleEntry(String regex, int groupIndex, int styleFlags,
                                  Color textColor, Color backgroundColor, double fontSizeScale) {
        autoRules.add(new AutoRule(regex, Math.max(0, groupIndex), styleFlags,
                textColor, backgroundColor, fontSizeScale));
    }

    /** Standard-Formatregeln wie im Haupt-Editor ({@link EditorWindow#applyCombinedStyling}). */
    public void registerDefaultFormatAutoRules() {
        autoRules.clear();
        addAutoRuleEntry("\\*\\*\\*([\\s\\S]+?)\\*\\*\\*", 1, MarkedArea.BOLD | MarkedArea.ITALIC);
        addAutoRuleEntry("\\*\\*(.+?)\\*\\*", 1, MarkedArea.BOLD);
        addAutoRuleEntry("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)", 1, MarkedArea.ITALIC);
        addAutoRuleEntry("~~([\\s\\S]+?)~~", 1, MarkedArea.STRIKETHROUGH);
        addAutoRuleEntry("<mark>([\\s\\S]+?)</mark>", 1, 0, null, MARK_HIGHLIGHT_COLOR, 0);
        addAutoRuleEntry("<(b|strong)>([\\s\\S]+?)</(b|strong)>", 2, MarkedArea.BOLD);
        addAutoRuleEntry("<(i|em)>([\\s\\S]+?)</(i|em)>", 2, MarkedArea.ITALIC);
        addAutoRuleEntry("<(s|del)>([\\s\\S]+?)</(s|del)>", 2, MarkedArea.STRIKETHROUGH);
        addAutoRuleEntry("<u>([\\s\\S]+?)</u>", 1, MarkedArea.UNDERLINE);
        addAutoRuleEntry("<sup>([\\s\\S]+?)</sup>", 1, MarkedArea.SUPERSCRIPT);
        addAutoRuleEntry("<sub>([\\s\\S]+?)</sub>", 1, MarkedArea.SUBSCRIPT);
        addAutoRuleEntry("<big>([\\s\\S]+?)</big>", 1, 0, null, null, BIG_FONT_SCALE);
        addAutoRuleEntry("<small>([\\s\\S]+?)</small>", 1, 0, null, null, SMALL_FONT_SCALE);
        addColorAutoRuleEntries();
        rebuildAutoMarks();
    }

    private void addColorAutoRuleEntries() {
        addAutoRuleEntry("<red>([\\s\\S]+?)</red>", 1, 0, Color.web("#dc3545"), null, 0);
        addAutoRuleEntry("<blue>([\\s\\S]+?)</blue>", 1, 0, Color.web("#007bff"), null, 0);
        addAutoRuleEntry("<green>([\\s\\S]+?)</green>", 1, 0, Color.web("#28a745"), null, 0);
        addAutoRuleEntry("<yellow>([\\s\\S]+?)</yellow>", 1, 0, Color.web("#ffc107"), null, 0);
        addAutoRuleEntry("<purple>([\\s\\S]+?)</purple>", 1, 0, Color.web("#6f42c1"), null, 0);
        addAutoRuleEntry("<orange>([\\s\\S]+?)</orange>", 1, 0, Color.web("#fd7e14"), null, 0);
        addAutoRuleEntry("<(?:gray|grey)>([\\s\\S]+?)</(?:gray|grey)>", 1, 0, Color.web("#6c757d"), null, 0);
        addAutoRuleEntry("<rot>([\\s\\S]+?)</rot>", 1, 0, Color.web("#dc3545"), null, 0);
        addAutoRuleEntry("<blau>([\\s\\S]+?)</blau>", 1, 0, Color.web("#007bff"), null, 0);
        addAutoRuleEntry("<grün>([\\s\\S]+?)</grün>", 1, 0, Color.web("#28a745"), null, 0);
        addAutoRuleEntry("<gelb>([\\s\\S]+?)</gelb>", 1, 0, Color.web("#ffc107"), null, 0);
        addAutoRuleEntry("<lila>([\\s\\S]+?)</lila>", 1, 0, Color.web("#6f42c1"), null, 0);
        addAutoRuleEntry("<grau>([\\s\\S]+?)</grau>", 1, 0, Color.web("#6c757d"), null, 0);
    }

    public void clearAutoRules() {
        autoRules.clear();
        hiddenMarkupRanges.clear();
        rebuildAutoMarks();
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
            case 1 -> setEditorTheme("#1a1a1a", "#2d2d2d", "#9ca3af", "#ffffff", "#375a7f");
            case 2 -> setEditorTheme("#f3e5f5", "#e1bee7", "#6b4f6b", "#000000", "#c7a6d8");
            case 3 -> setEditorTheme("#1e3a8a", "#172554", "#bfdbfe", "#ffffff", "#3b82f6");
            case 4 -> setEditorTheme("#064e3b", "#022c22", "#bbf7d0", "#ffffff", "#10b981");
            case 5 -> setEditorTheme("#581c87", "#3b0764", "#e9d5ff", "#ffffff", "#8b5cf6");
            default -> setEditorTheme("#ffffff", "#f5f5f5", "#9e9e9e", "#1f1f1f", "#9ec9ff");
        }
    }

    private void setEditorTheme(String background, String gutter, String gutterText,
                                String textColor, String selection) {
        editorBackgroundColor = Color.web(background);
        gutterBackgroundColor = Color.web(gutter);
        gutterTextColor = Color.web(gutterText);
        editorTextColor = Color.web(textColor);
        selectionColor = Color.web(selection);
        caretColor = Color.web(CARET_COLOR);
        boolean dark = isDarkBackground(background);
        initHeadingColors(dark);
        updateMarkPalette(dark);
        updateLanguageToolHoverTheme();
        render();
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
        List<TextRange> paintRanges = new ArrayList<>();
        for (LektoratMatch match : matches) {
            int[] span = resolveLektoratMatchSpan(match);
            if (span == null) {
                continue;
            }
            int start = span[0];
            int end = span[1];
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
     * Sucht den Originaltext im Editor und aktualisiert Offset/Länge am Match (wie Legacy-Editor).
     * Verhindert optisch verschobene Markierungen nach übernommenen Ersetzungen.
     */
    private int[] resolveLektoratMatchSpan(LektoratMatch match) {
        if (match == null) {
            return null;
        }
        String original = match.getOriginal();
        if (original == null || original.isEmpty()) {
            return null;
        }
        int hint = match.getOffset();
        int len = match.getLength() > 0 ? match.getLength() : original.length();
        int start = hint;
        int end = start + len;
        if (start >= 0 && end <= text.length() && text.substring(start, end).equals(original)) {
            match.setLength(original.length());
            return new int[]{start, start + original.length()};
        }
        int located = locateLektoratOriginalNear(original, hint);
        if (located < 0) {
            return null;
        }
        match.setOffset(located);
        match.setLength(original.length());
        return new int[]{located, located + original.length()};
    }

    /** Nächstes Vorkommen von {@code original} nahe der erwarteten Position (nicht immer das erste im Kapitel). */
    private int locateLektoratOriginalNear(String original, int hint) {
        if (original.isEmpty()) {
            return -1;
        }
        int best = -1;
        int bestDistance = Integer.MAX_VALUE;
        int from = 0;
        while (from < text.length()) {
            int idx = text.indexOf(original, from);
            if (idx < 0) {
                break;
            }
            int distance = hint >= 0 ? Math.abs(idx - hint) : idx;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = idx;
            }
            from = idx + 1;
        }
        return best;
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
        toggleWrappedFormat("", "", "<s>", "</s>");
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
            caret = normalizeCaretOffset(caret, true);
            if (!hasSelection()) {
                anchor = caret;
            }
            String replacement = QuotationMarkSupport.resolveTypedQuote(
                    text.toString(), caret, character, quoteStyleIndex);
            replaceSelection(replacement);
            event.consume();
            return;
        }
        caret = normalizeCaretOffset(caret, true);
        if (!hasSelection()) {
            anchor = caret;
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
                case RIGHT -> moveCaret(caret + 1, event.isShiftDown());
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
                    replaceSelection(newlinesForEnter(insertAt));
                }
            }
            case LEFT -> moveCaret(caret - 1, event.isShiftDown());
            case RIGHT -> moveCaret(caret + 1, event.isShiftDown());
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
            ensureInputFocusAfterMouseEvent();
        });
        canvas.setOnMouseReleased(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
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
            placeCaretAt(event.getX(), event.getY() + scrollTop, true);
            ensureInputFocusAfterMouseEvent();
        });
        canvas.setOnScroll(event -> {
            hideLanguageToolHover();
            verticalScrollBar.setValue(Math.max(0, Math.min(verticalScrollBar.getMax(), verticalScrollBar.getValue() - event.getDeltaY())));
            event.consume();
        });
        canvas.setOnMouseMoved(event -> updateLanguageToolHover(event.getX(), event.getY() + scrollTop, event.getScreenX(), event.getScreenY()));
        canvas.setOnMouseExited(event -> hideLanguageToolHover());
        canvas.setOnContextMenuRequested(event -> {
            hideLanguageToolHover();
            hideLanguageToolContextMenu();
            showEditorContextMenu(event);
            event.consume();
        });
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
            insertText(pasted);
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
        autoRuleDelay.stop();
        autoRuleDelay.playFromStart();
        updateScrollBar();
        if (viewportAnchor != null) {
            restoreViewportAnchor(viewportAnchor, scrollSyncOffset);
        }
        if (keepCaretVisible) {
            ensureCaretVisible();
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
        return text.indexOf("![") >= 0 || !imageBlocks.isEmpty() || !hiddenImageBlockRanges.isEmpty()
                || text.indexOf("---") >= 0 || !horizontalRules.isEmpty();
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

    /** Lese-Position: erste sichtbare Zeile (z. B. nach Auto-Markup-Rebuild). */
    private ViewportAnchor captureReadingViewportAnchor() {
        List<VisualLine> lines = visualLines();
        if (lines.isEmpty()) {
            return new ViewportAnchor(0, 0);
        }
        int lineIndex = Math.max(0, Math.min(lines.size() - 1, lineIndexAtContentY(scrollTop)));
        double lineTop = contentYForLineStart(lineIndex);
        return new ViewportAnchor(lines.get(lineIndex).start, lineTop - scrollTop);
    }

    /** Caret-Zeile soll beim Tippen an derselben Viewport-Y-Position bleiben. */
    private ViewportAnchor captureCaretViewportAnchor(int offset) {
        int safe = Math.max(0, Math.min(text.length(), offset));
        TextPosition pos = positionForOffset(safe);
        double lineTop = contentYForLineStart(pos.lineIndex);
        return new ViewportAnchor(safe, lineTop - scrollTop);
    }

    private void restoreViewportAnchor(ViewportAnchor anchor, int syncOffset) {
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
        ViewportAnchor viewportAnchor = captureReadingViewportAnchor();
        markedAreas.removeIf(area -> area.autoRule);
        hiddenMarkupRanges.clear();
        rebuildHeadingStyles();
        rebuildBlockquoteAndCenterStyles();
        for (AutoRule rule : autoRules) {
            MarkedArea area = new MarkedArea(this);
            area.autoRule = true;
            area.markStyle(rule.styleFlags());
            if (rule.textColor() != null) {
                area.markTextColor(rule.textColor());
            }
            if (rule.backgroundColor() != null) {
                area.markColor(toWebHex(rule.backgroundColor()));
            }
            if (rule.fontSizeScale() > 0) {
                area.markFontSizeScale(rule.fontSizeScale());
            }
            addAutoRuleRanges(area, rule);
            markedAreas.add(area);
        }
        hiddenMarkupRanges.sort(Comparator.comparingInt(range -> range.start));
        invalidateLayoutCaches();
        updateScrollBar();
        restoreViewportAnchor(viewportAnchor, viewportAnchor.offset());
        render();
    }

    private void rebuildHeadingStyles() {
        headingRanges.clear();
        headingLevelByLineStart.clear();
        Matcher matcher = HEADING_PATTERN.matcher(text.toString());
        while (matcher.find()) {
            int level = Math.min(6, Math.max(1, matcher.group(1).length()));
            int contentStart = matcher.start(2);
            int contentEnd = matcher.end(2);
            while (contentEnd > contentStart && text.charAt(contentEnd - 1) == '\r') {
                contentEnd--;
            }
            if (contentEnd > contentStart) {
                headingRanges.add(new HeadingRange(contentStart, contentEnd, level));
            }
            headingLevelByLineStart.put(matcher.start(), level);
            addHiddenMarkupRangesIfEnabled(matcher.start(), matcher.end(), contentStart, contentEnd);
        }
        textWidthCache.clear();
    }

    private void rebuildBlockquoteAndCenterStyles() {
        centerLineStarts.clear();
        blockquoteLineStarts.clear();
        Matcher blockquoteMatcher = BLOCKQUOTE_PATTERN.matcher(text.toString());
        while (blockquoteMatcher.find()) {
            int lineStart = logicalLineStartForOffset(blockquoteMatcher.start());
            blockquoteLineStarts.add(lineStart);
            int contentStart = blockquoteMatcher.start(1);
            int contentEnd = blockquoteMatcher.end(1);
            if (contentEnd > contentStart) {
                MarkedArea area = new MarkedArea(this);
                area.autoRule = true;
                area.markStyle(MarkedArea.ITALIC);
                area.markTextColor(BLOCKQUOTE_TEXT_COLOR);
                area.ranges.add(new TextRange(contentStart, contentEnd));
                markedAreas.add(area);
                addHiddenMarkupRangesIfEnabled(blockquoteMatcher.start(), blockquoteMatcher.end(), contentStart, contentEnd);
            }
        }
        Matcher centerMatcher = CENTER_TAG_PATTERN.matcher(text.toString());
        while (centerMatcher.find()) {
            centerLineStarts.add(logicalLineStartForOffset(centerMatcher.start()));
            int contentStart = centerMatcher.start(1);
            int contentEnd = centerMatcher.end(1);
            addHiddenMarkupRangesIfEnabled(centerMatcher.start(), centerMatcher.end(), contentStart, contentEnd);
        }
        rebuildHiddenBrTags();
    }

    private void rebuildHiddenBrTags() {
        if (!renderMarkupHidden) {
            return;
        }
        Matcher matcher = BR_TAG_PATTERN.matcher(text);
        while (matcher.find()) {
            hiddenMarkupRanges.add(new TextRange(matcher.start(), matcher.end()));
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

    private void addAutoRuleRanges(MarkedArea area, AutoRule rule) {
        try {
            Pattern pattern = Pattern.compile(rule.regex());
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                int groupIndex = rule.groupIndex();
                if (groupIndex > matcher.groupCount()) {
                    continue;
                }
                int start = matcher.start(groupIndex);
                int end = matcher.end(groupIndex);
                if (start >= 0 && end > start) {
                    area.ranges.add(new TextRange(start, end));
                    addHiddenMarkupRangesIfEnabled(matcher.start(), matcher.end(), start, end);
                }
            }
        } catch (PatternSyntaxException ignored) {
            // Ungültige Auto-Regeln deaktivieren sich still, damit Tippen nicht unterbrochen wird.
        }
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
        syncImagesFromMarkdown();
        syncHorizontalRulesFromMarkdown();
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
            String lineText = text.substring(lineStart, lineEnd).trim();
            if (HORIZONTAL_RULE_LINE.matcher(lineText).matches()) {
                int startLineIndex = lineIndexForOffset(lineStart, lines);
                horizontalRules.add(new ParsedHorizontalRule(lineStart, lineEnd, startLineIndex, ruleHeight));
                hiddenHorizontalRuleRanges.add(new TextRange(lineStart, lineEnd));
            }
            lineStart = i + 1;
        }
        invalidateLayoutCaches();
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

    private double availableContentWidth() {
        double width = canvas.getWidth() > 0 ? canvas.getWidth() : 800;
        return Math.max(80, width - gutterWidth() - 24);
    }

    private int lineIndexForOffset(int offset, List<VisualLine> lines) {
        int safeOffset = Math.max(0, Math.min(text.length(), offset));
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
            if (oldMax > 0.01 && max > 0.01) {
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
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double width = canvas.getWidth();
        double height = canvas.getHeight();
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
            paintLineText(gc, line, i, lineTopY(i), selectionStart, selectionEnd);
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

    private void paintLineText(GraphicsContext gc, VisualLine line, int lineIndex, double y,
                               int selectionStart, int selectionEnd) {
        if (isFirstVisualLineOfLogicalLine(line)) {
            Integer headingLevel = headingLevelForVisualLine(line);
            if (headingLevel != null) {
                paintCenteredLine(gc, line, y);
                return;
            }
            if (centerLineStarts.contains(logicalLineStartForOffset(line.start))) {
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
        double top = y + 1;
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
        double top = y + 1;
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
        double x = textLeft() + headingCenterOffset(line) + blockquoteIndentOffset(line);
        double top = y + 1;
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
        double bandHeight = paintBandHeight(lineIndex);
        if (bandHeight <= 0) {
            return;
        }
        double top = y + 1;
        double height = bandHeight;
        int segmentStart = line.start;
        while (segmentStart < line.end) {
            if (isHiddenOffset(segmentStart)) {
                segmentStart++;
                continue;
            }
            RenderStyle style = styleAt(segmentStart);
            int segmentEnd = segmentStart + 1;
            while (segmentEnd < line.end && !isHiddenOffset(segmentEnd) && sameTextStyle(style, styleAt(segmentEnd))) {
                segmentEnd++;
            }

            String segment = text.substring(segmentStart, segmentEnd);
            double x = xForOffsetInLine(line, lineIndex, segmentStart);
            double layoutWidth = xForOffsetInLine(line, lineIndex, segmentEnd) - x;
            double segmentWidth = Math.max(measureText(segment, style), layoutWidth);
            if (style.background != null && !usesLayoutPaintedBackground(style)) {
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
        gc.fillRect(x0, top, Math.max(CARET_WIDTH, x1 - x0), height);
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
        result.baselineShift = baselineShiftFor(result);
        if ((result.flags & MarkedArea.STRIKETHROUGH) != 0) {
            result.strikethrough = true;
        }
        return result;
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
        double target = contentWidthForLine(line);
        double natural = measureRange(line.start, line.end);
        if (natural >= target - 0.5) {
            return 0;
        }
        return (target - natural) / (words.size() - 1);
    }

    private double contentWidthForLine(VisualLine line) {
        return Math.max(MIN_CHAR_WIDTH, availableLineWidth() - headingCenterOffset(line) - blockquoteIndentOffset(line));
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
                lines.add(new VisualLine(lineStart, i));
                break;
            }

            if (text.charAt(i) == '\n') {
                lines.add(new VisualLine(lineStart, i));
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
            if (blockquoteLineStarts.contains(logicalLineStartForOffset(lineStart))) {
                wrapWidth = Math.max(MIN_CHAR_WIDTH, wrapWidth - BLOCKQUOTE_INDENT_PX);
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
        return Math.max(measureText("MMMMMMMM", new RenderStyle()), canvas.getWidth() - textLeft() - 10);
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
        localX = Math.max(0, localX - headingCenterOffset(line) - blockquoteIndentOffset(line));
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
                        if (gapEnd > gapStart) {
                            return offsetAtRangeX(gapStart, gapEnd, x, localX);
                        }
                        return localX < x + gapWidth / 2.0 ? gapStart : gapEnd;
                    }
                    x += gapWidth;
                }
            }
            int lastWordEnd = words.get(words.size() - 1)[1];
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
        int newline = text.toString().lastIndexOf('\n', offset - 1);
        return newline < 0 ? 0 : newline + 1;
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
        int safeOffset = Math.max(0, Math.min(text.length(), offset));
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
            if (renderMarkupHidden && isWhitespaceOnlyVisualLine(line, lineIndex)) {
                return Math.max(lineHeightForVisualLine(line), paragraphSpacingPx);
            }
            return lineHeightForVisualLine(line);
        }
        return lineHeight();
    }

    /** Innenhöhe für Text-/Markierungsband; muss zur Layout-Zeilenhöhe passen (Absatz-Lücke, kollabierte Leerzeile). */
    private double paintBandHeight(int lineIndex) {
        double segmentHeight = lineHeightForLineIndex(lineIndex);
        if (segmentHeight <= 0) {
            return 0;
        }
        return Math.max(1, segmentHeight - 2);
    }

    private double lineHeightForVisualLine(VisualLine line) {
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
        return isBlockquoteVisualLine(line) ? BLOCKQUOTE_INDENT_PX : 0;
    }

    /**
     * Enter erzeugt markdowngerechte Absätze: zwischen Absätzen muss eine Leerzeile in der Quelle stehen
     * ({@code \n\n}), sonst werden sie zu einem Absatz zusammengezogen.
     */
    private String newlinesForEnter(int offset) {
        int safe = Math.max(0, Math.min(text.length(), offset));
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
        int newline = text.indexOf("\n", start);
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
        return textLeft() + headingCenterOffset(line) + blockquoteIndentOffset(line)
                + measureRange(line.start, safeOffset) + justifyExtraBefore(line, lineIndex, safeOffset);
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
        if (!renderMarkupHidden || hiddenMarkupRanges.isEmpty()) {
            return false;
        }
        return hiddenMarkupRangeContains(offset);
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
    private record AutoRule(String regex, int groupIndex, int styleFlags,
                            Color textColor, Color backgroundColor, double fontSizeScale) {
        AutoRule(String regex, int groupIndex, int styleFlags) {
            this(regex, groupIndex, styleFlags, null, null, 0);
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
        private boolean underline;
        private boolean autoRule;
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
            for (TextRange range : ranges) {
                if (range.contains(offset)) {
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
                    return;
                }
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
