package com.manuskript;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
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
import java.util.List;
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
        return event.isControlDown() || event.isMetaDown();
    }

    private static final double GUTTER_WIDTH = 48;
    private static final double TEXT_LEFT = GUTTER_WIDTH + 8;
    private static final double CARET_WIDTH = 1.5;
    private static final String CARET_COLOR = "#ff3333";
    private static final double MIN_CHAR_WIDTH = 4.0;
    private static final int MAX_UNDO = 250;
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);
    /** Entspricht .heading-1 … .heading-6 in manuskript.css bei 16px Fließtext. */
    private static final double[] HEADING_FONT_SIZES_AT_16 = {24, 20, 18, 16, 15, 14};
    private static final double BODY_FONT_REFERENCE = 16.0;
    private static final String[] HEADING_COLORS_LIGHT = {"#2c3e50", "#34495e", "#7f8c8d", "#95a5a6", "#bdc3c7", "#bdc3c7"};
    private static final String[] HEADING_COLORS_DARK = {"#ffffff", "#e9ecef", "#ced4da", "#adb5bd", "#868e96", "#868e96"};

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
    private final List<AutoRule> autoRules = new ArrayList<>();
    private final List<HeadingRange> headingRanges = new ArrayList<>();
    private final Map<Integer, Integer> headingLevelByLineStart = new HashMap<>();
    private Color[] headingColors = new Color[6];
    private final ArrayDeque<Snapshot> undoStack = new ArrayDeque<>();
    private final ArrayDeque<Snapshot> redoStack = new ArrayDeque<>();
    private final PauseTransition autoRuleDelay = new PauseTransition(Duration.millis(350));
    private final PauseTransition widthLayoutDelay = new PauseTransition(Duration.millis(120));
    private final PauseTransition imageSyncDelay = new PauseTransition(Duration.millis(400));
    private final Text measuringText = new Text();
    private final Map<MeasureKey, Double> textWidthCache = new HashMap<>();
    private final Map<String, Image> imageCache = new HashMap<>();
    private final ContextMenu languageToolContextMenu = new ContextMenu();
    private final Popup languageToolHoverPopup = new Popup();
    private final Label languageToolHoverMessage = new Label();
    private final Label languageToolHoverSuggestion = new Label();
    private List<LanguageToolService.Match> currentLanguageToolMatches = new ArrayList<>();
    private LanguageToolDictionary languageToolDictionary;
    private Runnable onLanguageToolMatchesChanged;
    private LanguageToolService.Match hoveredLanguageToolMatch;
    private EventHandler<MouseEvent> languageToolContextMenuOutsideClickFilter;
    private Consumer<String> textChangeListener;
    private Runnable selectionChangeListener;
    private int lastNotifiedCaret = -1;
    private int lastNotifiedAnchor = -2;
    private boolean textChangeNotificationPending;

    private String fontFamily = "Segoe UI";
    private double fontSize = 16.0;
    private int caret = 0;
    private int anchor = 0;
    private double scrollTop = 0.0;
    private boolean wrapText = true;
    private boolean applyingSnapshot = false;
    private int suppressEnterCount = 0;
    private boolean renderMarkupHidden = true;
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
    private boolean cachedVisualLinesMarkupHidden;
    private double cachedVisualLinesFontSize = -1;

    public ManuskriptTextEditor() {
        initHeadingColors(false);
        setupKeyboardProxy();
        getChildren().addAll(keyboardProxy, canvas, verticalScrollBar);
        setFocusTraversable(false);
        setPadding(new Insets(0));

        canvas.setFocusTraversable(false);

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
            syncImagesFromMarkdown();
            refreshLayout();
        });

        setupLanguageToolHoverPopup();
        setupMouseHandling();

        autoRuleDelay.setOnFinished(e -> rebuildAutoMarks());
        imageSyncDelay.setOnFinished(e -> {
            syncImagesFromMarkdown();
            updateScrollBar();
            render();
        });
        setText("");
    }

    private void setupKeyboardProxy() {
        keyboardProxy.setFocusTraversable(true);
        keyboardProxy.setMouseTransparent(true);
        keyboardProxy.setMinSize(1, 1);
        keyboardProxy.setPrefSize(1, 1);
        keyboardProxy.setMaxSize(1, 1);
        keyboardProxy.setOpacity(0);
        keyboardProxy.addEventHandler(KeyEvent.KEY_PRESSED, this::handleKeyPressed);
        keyboardProxy.addEventHandler(KeyEvent.KEY_TYPED, this::handleKeyTyped);
        keyboardProxy.focusedProperty().addListener((obs, wasFocused, focused) -> render());
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
        keyboardProxy.requestFocus();
    }

    @Override
    protected void layoutChildren() {
        double width = Math.max(0, getWidth());
        double height = Math.max(0, getHeight());
        double barWidth = 14;
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
        caret = safeStart;
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
        invalidateLayoutCaches();
        autoRuleDelay.stop();
        autoRuleDelay.playFromStart();
        rebuildHeadingStyles();
        imageSyncDelay.stop();
        syncImagesFromMarkdown();
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
        syncImagesFromMarkdown();
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
        cachedVisualLinesFontSize = -1;
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

    public void replaceSelection(String replacement) {
        pushUndo();
        ViewportAnchor viewportAnchor = captureViewportAnchor();
        int start = selectionStart();
        int end = selectionEnd();
        text.replace(start, end, replacement == null ? "" : replacement);
        int inserted = replacement == null ? 0 : replacement.length();
        adjustRangesForReplace(start, end, inserted);
        caret = start + inserted;
        anchor = caret;
        preferredCaretX = Double.NaN;
        afterTextChanged(viewportAnchor, true);
    }

    public void replaceRange(int start, int end, String replacement) {
        int safeStart = Math.max(0, Math.min(text.length(), start));
        int safeEnd = Math.max(safeStart, Math.min(text.length(), end));
        pushUndo();
        ViewportAnchor viewportAnchor = captureViewportAnchor();
        text.replace(safeStart, safeEnd, replacement == null ? "" : replacement);
        adjustRangesForReplace(safeStart, safeEnd, replacement == null ? 0 : replacement.length());
        caret = safeStart + (replacement == null ? 0 : replacement.length());
        anchor = caret;
        preferredCaretX = Double.NaN;
        afterTextChanged(viewportAnchor, true);
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
        pushUndo();
        ViewportAnchor viewportAnchor = captureViewportAnchor();
        text.delete(caret - 1, caret);
        adjustRangesForReplace(caret - 1, caret, 0);
        caret--;
        anchor = caret;
        preferredCaretX = Double.NaN;
        afterTextChanged(viewportAnchor, true);
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
        pushUndo();
        ViewportAnchor viewportAnchor = captureViewportAnchor();
        text.delete(caret, caret + 1);
        adjustRangesForReplace(caret, caret + 1, 0);
        anchor = caret;
        preferredCaretX = Double.NaN;
        afterTextChanged(viewportAnchor, true);
    }

    public void undo() {
        if (undoStack.isEmpty()) {
            return;
        }
        redoStack.push(snapshot());
        applySnapshot(undoStack.pop());
    }

    public void redo() {
        if (redoStack.isEmpty()) {
            return;
        }
        undoStack.push(snapshot());
        applySnapshot(redoStack.pop());
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
        pushUndo();
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
        pushUndo();
        ViewportAnchor viewportAnchor = captureViewportAnchor();
        String value = replacement == null ? "" : replacement;
        text.replace(match.start(), match.end(), value);
        adjustRangesForReplace(match.start(), match.end(), value.length());
        caret = match.start() + value.length();
        anchor = caret;
        preferredCaretX = Double.NaN;
        afterTextChanged(viewportAnchor, true);
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
        autoRules.add(new AutoRule(regex, Math.max(0, groupIndex), styleFlags));
        rebuildAutoMarks();
    }

    public void clearAutoRules() {
        autoRules.clear();
        hiddenMarkupRanges.clear();
        rebuildAutoMarks();
    }

    public void setRenderMarkupHidden(boolean hidden) {
        renderMarkupHidden = hidden;
        textWidthCache.clear();
        invalidateLayoutCaches();
        updateScrollBar();
        render();
    }

    public boolean isRenderMarkupHidden() {
        return renderMarkupHidden;
    }

    public void applyTheme(int themeIndex) {
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
        initHeadingColors(isDarkBackground(background));
        updateLanguageToolHoverTheme();
        render();
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

    /** Umschließt die Auswahl mit {@code **} (wie Ctrl+B im Haupt-Editor). */
    public void toggleBold() {
        if (hasSelection()) {
            replaceSelection("**" + selectedText() + "**");
            return;
        }
        int pos = caret;
        replaceSelection("****");
        caret = pos + 2;
        anchor = caret;
        render();
    }

    /** Umschließt die Auswahl mit {@code *} (wie Ctrl+I im Haupt-Editor). */
    public void toggleItalic() {
        if (hasSelection()) {
            replaceSelection("*" + selectedText() + "*");
            return;
        }
        int pos = caret;
        replaceSelection("**");
        caret = pos + 1;
        anchor = caret;
        render();
    }

    /** Umschließt die Auswahl mit {@code <u></u>} (wie Ctrl+U im Haupt-Editor). */
    public void toggleUnderline() {
        if (hasSelection()) {
            replaceSelection("<u>" + selectedText() + "</u>");
            return;
        }
        int pos = caret;
        replaceSelection("<u></u>");
        caret = pos + 3;
        anchor = caret;
        render();
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

    private void handleKeyTyped(KeyEvent event) {
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
        if (shortcut && event.getCode() == KeyCode.X) {
            copySelection();
            if (hasSelection()) {
                replaceSelection("");
            }
            event.consume();
            return;
        }
        if (shortcut && event.getCode() == KeyCode.V) {
            if (Clipboard.getSystemClipboard().hasString()) {
                String pasted = Clipboard.getSystemClipboard().getString();
                if (pasted != null && !pasted.isEmpty()) {
                    insertText(pasted);
                }
            }
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
                    replaceSelection("\n");
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
        languageToolContextMenu.setOnShown(event -> installLanguageToolContextMenuOutsideClickHandler());
        languageToolContextMenu.setOnHidden(event -> removeLanguageToolContextMenuOutsideClickHandler());
        updateLanguageToolHoverTheme();
    }

    private void installLanguageToolContextMenuOutsideClickHandler() {
        Scene scene = canvas.getScene();
        if (scene == null) {
            return;
        }
        if (languageToolContextMenuOutsideClickFilter == null) {
            languageToolContextMenuOutsideClickFilter = event -> {
                if (!languageToolContextMenu.isShowing()) {
                    return;
                }
                if (isEventInsideLanguageToolContextMenu(event)) {
                    return;
                }
                languageToolContextMenu.hide();
            };
        }
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, languageToolContextMenuOutsideClickFilter);
    }

    private void removeLanguageToolContextMenuOutsideClickHandler() {
        Scene scene = canvas.getScene();
        if (scene == null || languageToolContextMenuOutsideClickFilter == null) {
            return;
        }
        scene.removeEventFilter(MouseEvent.MOUSE_PRESSED, languageToolContextMenuOutsideClickFilter);
    }

    private boolean isEventInsideLanguageToolContextMenu(MouseEvent event) {
        Scene menuScene = languageToolContextMenu.getScene();
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
            hideLanguageToolContextMenu();
            requestInputFocus();
            if (event.getButton() != MouseButton.PRIMARY) {
                return;
            }
            double contentY = event.getY() + scrollTop;
            ParsedImageBlock imageBlock = imageBlockAtContentY(event.getX(), contentY);
            if (imageBlock != null) {
                anchor = imageBlock.startOffset();
                caret = imageBlock.endOffset();
                preferredCaretX = Double.NaN;
                ensureCaretVisible();
                render();
                return;
            }
            int offset = offsetAt(event.getX(), contentY);
            caret = normalizeCaretOffset(offset, true);
            preferredCaretX = Double.NaN;
            if (!event.isShiftDown()) {
                anchor = caret;
            }
            ensureCaretVisible();
            render();
        });
        canvas.setOnMouseClicked(event -> {
            if (event.getButton() != MouseButton.PRIMARY) {
                return;
            }
            int offset = offsetAt(event.getX(), event.getY() + scrollTop);
            MarkedArea interactiveArea = interactiveAreaAt(offset);
            if (interactiveArea != null) {
                interactiveArea.fireClick();
                event.consume();
            }
        });
        canvas.setOnMouseDragged(event -> {
            caret = offsetAt(event.getX(), event.getY() + scrollTop);
            preferredCaretX = Double.NaN;
            ensureCaretVisible();
            render();
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
            int offset = offsetAt(event.getX(), event.getY() + scrollTop);
            LanguageToolService.Match match = findLanguageToolMatchAt(offset);
            if (match == null) {
                languageToolContextMenu.hide();
                return;
            }
            showLanguageToolContextMenu(match, event.getScreenX(), event.getScreenY());
            event.consume();
        });
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
        TextPosition current = positionForOffset(caret);
        List<VisualLine> lines = visualLines();
        int targetLine = findVerticalTargetLine(current.lineIndex, lineDelta);
        VisualLine line = lines.get(targetLine);
        if (Double.isNaN(preferredCaretX)) {
            VisualLine currentLine = lines.get(Math.max(0, Math.min(lines.size() - 1, current.lineIndex)));
            preferredCaretX = xForOffsetInLine(currentLine, caret) - TEXT_LEFT;
        }
        caret = normalizeCaretOffset(offsetAtLineX(line, preferredCaretX), lineDelta > 0);
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
            while (next >= 0 && next < lines.size() && isLineInsideImageBlock(next)) {
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

    private void copySelection() {
        if (!hasSelection()) {
            return;
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(selectedText());
        Clipboard.getSystemClipboard().setContent(content);
    }

    private void pushUndo() {
        if (applyingSnapshot) {
            return;
        }
        undoStack.push(snapshot());
        while (undoStack.size() > MAX_UNDO) {
            undoStack.removeLast();
        }
        redoStack.clear();
    }

    private Snapshot snapshot() {
        return new Snapshot(text.toString(), caret, anchor, new ArrayList<>(styles));
    }

    private void applySnapshot(Snapshot snapshot) {
        ViewportAnchor viewportAnchor = captureViewportAnchor();
        applyingSnapshot = true;
        try {
            text.setLength(0);
            text.append(snapshot.text());
            caret = snapshot.caret();
            anchor = snapshot.anchor();
            styles.clear();
            styles.addAll(snapshot.styles());
            afterTextChanged(viewportAnchor, true);
        } finally {
            applyingSnapshot = false;
        }
    }

    private void afterTextChanged() {
        afterTextChanged(null, true);
    }

    private void afterTextChanged(ViewportAnchor viewportAnchor, boolean keepCaretVisible) {
        invalidateLayoutCaches();
        caret = Math.max(0, Math.min(caret, text.length()));
        anchor = Math.max(0, Math.min(anchor, text.length()));
        scheduleImageSyncIfNeeded();
        autoRuleDelay.stop();
        autoRuleDelay.playFromStart();
        updateScrollBar();
        if (viewportAnchor != null) {
            restoreViewportAnchor(viewportAnchor);
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
        if (!imageBlocks.isEmpty() || !hiddenImageBlockRanges.isEmpty()) {
            imageBlocks.clear();
            hiddenImageBlockRanges.clear();
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

    private ViewportAnchor captureViewportAnchor() {
        List<VisualLine> lines = visualLines();
        if (lines.isEmpty()) {
            return new ViewportAnchor(0, 0);
        }
        int lineIndex = Math.max(0, Math.min(lines.size() - 1, lineIndexAtContentY(scrollTop)));
        double yOffset = scrollTop - contentYForLineStart(lineIndex);
        return new ViewportAnchor(lines.get(lineIndex).start, yOffset);
    }

    private void restoreViewportAnchor(ViewportAnchor anchor) {
        TextPosition pos = positionForOffset(Math.max(0, Math.min(text.length(), anchor.offset())));
        double target = contentYForLineStart(pos.lineIndex) + anchor.yOffset();
        double max = verticalScrollBar.getMax();
        double clamped = Math.max(0, Math.min(max, target));
        if (Math.abs(verticalScrollBar.getValue() - clamped) > 0.01) {
            verticalScrollBar.setValue(clamped);
        }
        scrollTop = verticalScrollBar.getValue();
    }

    private void rebuildAutoMarks() {
        ViewportAnchor viewportAnchor = captureViewportAnchor();
        markedAreas.removeIf(area -> area.autoRule);
        hiddenMarkupRanges.clear();
        rebuildHeadingStyles();
        for (AutoRule rule : autoRules) {
            MarkedArea area = new MarkedArea(this);
            area.autoRule = true;
            area.markStyle(rule.styleFlags());
            addAutoRuleRanges(area, rule);
            markedAreas.add(area);
        }
        invalidateLayoutCaches();
        updateScrollBar();
        restoreViewportAnchor(viewportAnchor);
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
            addHiddenMarkupRanges(matcher.start(), matcher.end(), contentStart, contentEnd);
        }
        textWidthCache.clear();
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
                    addHiddenMarkupRanges(matcher.start(), matcher.end(), start, end);
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
        shiftHeadingLineStarts(start, end, delta);
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

    private void syncImagesFromMarkdown() {
        imageBlocks.clear();
        hiddenImageBlockRanges.clear();
        if (text.isEmpty()) {
            invalidateLayoutCaches();
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

    private double availableContentWidth() {
        double width = canvas.getWidth() > 0 ? canvas.getWidth() : 800;
        return Math.max(80, width - GUTTER_WIDTH - 24);
    }

    private int lineIndexForOffset(int offset, List<VisualLine> lines) {
        int safeOffset = Math.max(0, Math.min(text.length(), offset));
        for (int i = 0; i < lines.size(); i++) {
            VisualLine line = lines.get(i);
            if (safeOffset >= line.start && safeOffset <= line.end) {
                return i;
            }
        }
        return Math.max(0, lines.size() - 1);
    }

    private double lineTopY(int lineIndex) {
        return contentYForLineStart(lineIndex) - scrollTop;
    }

    private double contentYForLineStart(int lineIndex) {
        double y = 0;
        List<VisualLine> lines = visualLines();
        int limit = Math.max(0, Math.min(lineIndex, lines.size()));
        for (int i = 0; i < limit; i++) {
            ParsedImageBlock blockStart = imageBlockStartingAtLine(i);
            if (blockStart != null) {
                y += blockStart.displayHeight();
                continue;
            }
            if (isLineInsideImageBlock(i)) {
                continue;
            }
            y += lineHeightForLineIndex(i);
        }
        return y;
    }

    private double totalContentHeight() {
        double y = 0;
        List<VisualLine> lines = visualLines();
        for (int i = 0; i < lines.size(); i++) {
            ParsedImageBlock blockStart = imageBlockStartingAtLine(i);
            if (blockStart != null) {
                y += blockStart.displayHeight();
                continue;
            }
            if (isLineInsideImageBlock(i)) {
                continue;
            }
            y += lineHeightForLineIndex(i);
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

    private ParsedImageBlock imageBlockAtContentY(double x, double contentY) {
        if (x < TEXT_LEFT) {
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
        List<VisualLine> lines = visualLines();
        if (lines.isEmpty()) {
            return 0;
        }
        double y = 0;
        for (int i = 0; i < lines.size(); i++) {
            ParsedImageBlock blockStart = imageBlockStartingAtLine(i);
            double segmentHeight;
            if (blockStart != null) {
                segmentHeight = blockStart.displayHeight();
            } else if (isLineInsideImageBlock(i)) {
                continue;
            } else {
                segmentHeight = lineHeight();
            }
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
        if (Math.abs(verticalScrollBar.getMax() - max) > 0.01) {
            verticalScrollBar.setMax(max);
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

    private void render() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        gc.setFill(editorBackgroundColor);
        gc.fillRect(0, 0, width, height);
        gc.setFill(gutterBackgroundColor);
        gc.fillRect(0, 0, GUTTER_WIDTH, height);

        List<VisualLine> lines = visualLines();
        int selectionStart = selectionStart();
        int selectionEnd = selectionEnd();
        int first = Math.max(0, lineIndexAtContentY(scrollTop) - 1);
        int last = Math.min(lines.size(), lineIndexAtContentY(scrollTop + height) + 2);

        for (int i = first; i < last; i++) {
            VisualLine line = lines.get(i);
            if (isLineInsideImageBlock(i)) {
                continue;
            }
            double y = lineTopY(i);
            gc.setFill(gutterTextColor);
            gc.fillText(String.valueOf(i + 1), 8, y + baselineForLine(i));
            paintLineBackgrounds(gc, line, y, selectionStart, selectionEnd);
        }

        paintImages(gc);

        for (int i = first; i < last; i++) {
            if (isLineInsideImageBlock(i)) {
                continue;
            }
            VisualLine line = lines.get(i);
            paintLineText(gc, line, lineTopY(i));
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

    private void paintLineBackgrounds(GraphicsContext gc, VisualLine line, double y, int selectionStart, int selectionEnd) {
        double rowHeight = lineHeightForVisualLine(line);
        for (int offset = line.start; offset <= line.end; offset++) {
            if (isHiddenOffset(offset)) {
                continue;
            }
            if (offset < line.end) {
                RenderStyle style = styleAt(offset);
                double x = xForOffsetInLine(line, offset);
                double nextX = xForOffsetInLine(line, offset + 1);
                if (style.background != null) {
                    gc.setFill(style.background);
                    gc.fillRect(x, y + 2, Math.max(CARET_WIDTH, nextX - x), rowHeight - 4);
                }
            }
            if (selectionStart != selectionEnd && offset >= selectionStart && offset < selectionEnd) {
                double x = xForOffsetInLine(line, offset);
                double nextX = xForOffsetInLine(line, offset + 1);
                gc.setFill(selectionColor);
                gc.fillRect(x, y + 2, Math.max(CARET_WIDTH, nextX - x), rowHeight - 4);
            }
        }
    }

    private void paintLineText(GraphicsContext gc, VisualLine line, double y) {
        Integer headingLevel = headingLevelForVisualLine(line);
        if (headingLevel != null && isFirstVisualLineOfLogicalLine(line)) {
            paintCenteredHeadingLine(gc, line, y, headingLevel);
            return;
        }
        paintLineTextSegments(gc, line, y);
    }

    private void paintCenteredHeadingLine(GraphicsContext gc, VisualLine line, double y, int level) {
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
        double centerX = TEXT_LEFT + availableLineWidth() / 2.0;
        gc.fillText(visible.toString(), centerX, y + baselineForVisualLine(line));
        gc.setTextAlign(TextAlignment.LEFT);
    }

    private void paintLineTextSegments(GraphicsContext gc, VisualLine line, double y) {
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
            double x = xForOffsetInLine(line, segmentStart);
            double segmentWidth = measureText(segment, style);
            gc.setFont(fontFor(style));
            gc.setFill(style.textColor != null ? style.textColor : editorTextColor);
            gc.fillText(segment, x, y + baselineForVisualLine(line));
            if (style.underline) {
                gc.setStroke(style.underlineColor == null ? editorTextColor : style.underlineColor);
                gc.strokeLine(x, y + baselineForVisualLine(line) + 2, x + segmentWidth, y + baselineForVisualLine(line) + 2);
            }
            segmentStart = segmentEnd;
        }
    }

    private boolean isLineInsideImageBlock(int lineIndex) {
        for (ParsedImageBlock block : imageBlocks) {
            if (lineIndex >= block.startLineIndex() && lineIndex <= block.endLineIndex()) {
                return true;
            }
        }
        return false;
    }

    private void paintImages(GraphicsContext gc) {
        if (imageBlocks.isEmpty()) {
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
            double x = GUTTER_WIDTH + 10 + Math.max(0, (contentWidth - maxWidth) / 2.0);
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
                gc.fillText(caption, GUTTER_WIDTH + 10 + contentWidth / 2.0, y + imageHeight + baseline());
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
        return keyboardProxy.isFocused() || isFocused();
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
        double x = xForOffsetInLine(line, caret);
        double y = lineTopY(pos.lineIndex) + 2;
        gc.setFill(caretColor);
        gc.fillRect(x, y, CARET_WIDTH, lineHeightForLineIndex(pos.lineIndex) - 4);
    }

    private Font fontFor(RenderStyle style) {
        FontWeight weight = (style.flags & MarkedArea.BOLD) != 0 ? FontWeight.BOLD : FontWeight.NORMAL;
        FontPosture posture = (style.flags & MarkedArea.ITALIC) != 0 ? FontPosture.ITALIC : FontPosture.REGULAR;
        return Font.font(style.fontFamily == null ? fontFamily : style.fontFamily, weight, posture,
                style.fontSize <= 0 ? fontSize : style.fontSize);
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
                && cachedVisualLinesMarkupHidden == renderMarkupHidden
                && Double.compare(cachedVisualLinesFontSize, fontSize) == 0
                && Double.compare(cachedVisualLinesWidth, lineWidth) == 0) {
            return cachedVisualLines;
        }
        cachedVisualLines = computeVisualLines();
        cachedVisualLinesWidth = lineWidth;
        cachedVisualLinesTextLength = text.length();
        cachedVisualLinesHiddenBlocks = hiddenBlocks;
        cachedVisualLinesHeadingCount = headingRanges.size();
        cachedVisualLinesMarkupHidden = renderMarkupHidden;
        cachedVisualLinesFontSize = fontSize;
        return cachedVisualLines;
    }

    private List<VisualLine> computeVisualLines() {
        List<VisualLine> lines = new ArrayList<>();
        int lineStart = 0;
        int i = 0;
        int lastBreakOffset = -1;
        double availableWidth = availableLineWidth();
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
                lastBreakOffset = -1;
                continue;
            }

            if (wrapText && isVisibleBreakOpportunity(i)) {
                lastBreakOffset = i;
            }

            if (wrapText && i > lineStart && measureRange(lineStart, i + 1) > availableWidth) {
                int breakOffset = lastBreakOffset >= lineStart ? lastBreakOffset + 1 : Math.max(lineStart + 1, i);
                if (breakOffset <= lineStart) {
                    breakOffset = Math.min(text.length(), lineStart + 1);
                }
                lines.add(new VisualLine(lineStart, breakOffset));
                lineStart = breakOffset;
                i = lineStart;
                lastBreakOffset = -1;
                continue;
            }

            i++;
        }
        if (lines.isEmpty()) {
            lines.add(new VisualLine(0, 0));
        }
        return lines;
    }

    private double availableLineWidth() {
        return Math.max(measureText("MMMMMMMM", new RenderStyle()), canvas.getWidth() - TEXT_LEFT - 10);
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
        double localX = Math.max(0, x - TEXT_LEFT);
        return normalizeCaretOffset(offsetAtLineX(line, localX), true);
    }

    private int normalizeCaretOffset(int offset, boolean forward) {
        for (TextRange range : hiddenImageBlockRanges) {
            if (range.contains(offset)) {
                return forward ? range.end : range.start;
            }
        }
        return offset;
    }

    private boolean isHiddenImageBlockOffset(int offset) {
        for (TextRange range : hiddenImageBlockRanges) {
            if (range.contains(offset)) {
                return true;
            }
        }
        return false;
    }

    private int offsetAtLineX(VisualLine line, double localX) {
        if (isFirstVisualLineOfLogicalLine(line) && headingLevelForVisualLine(line) != null) {
            double lineWidth = measureRange(line.start, line.end);
            double centerOffset = Math.max(0, (availableLineWidth() - lineWidth) / 2.0);
            localX = Math.max(0, localX - centerOffset);
        }
        for (int offset = line.start; offset < line.end; offset++) {
            if (isHiddenOffset(offset)) {
                continue;
            }
            double currentX = measureRange(line.start, offset);
            double nextX = measureRange(line.start, offset + 1);
            if (localX < (currentX + nextX) / 2.0) {
                return offset;
            }
        }
        return line.end;
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
        List<VisualLine> lines = visualLines();
        for (int i = 0; i < lines.size(); i++) {
            VisualLine line = lines.get(i);
            if (safeOffset >= line.start && safeOffset <= line.end) {
                return new TextPosition(i, safeOffset - line.start);
            }
        }
        VisualLine last = lines.get(lines.size() - 1);
        return new TextPosition(lines.size() - 1, last.end - last.start);
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
        ParsedImageBlock blockStart = imageBlockStartingAtLine(lineIndex);
        if (blockStart != null) {
            return blockStart.displayHeight();
        }
        if (isLineInsideImageBlock(lineIndex)) {
            return 0;
        }
        return lineHeightForLineIndex(lineIndex);
    }

    private double lineHeightForLineIndex(int lineIndex) {
        List<VisualLine> lines = visualLines();
        if (lineIndex < 0 || lineIndex >= lines.size()) {
            return lineHeight();
        }
        return lineHeightForVisualLine(lines.get(lineIndex));
    }

    private double lineHeightForVisualLine(VisualLine line) {
        double base = Math.ceil(maxFontSizeInLine(line) * 1.55);
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
        return Math.ceil(maxFontSizeInLine(line) * 1.15);
    }

    private double maxFontSizeInLine(VisualLine line) {
        double maxFont = fontSize;
        for (int offset = line.start; offset < line.end; offset++) {
            if (isHiddenOffset(offset)) {
                continue;
            }
            RenderStyle style = styleAt(offset);
            maxFont = Math.max(maxFont, style.fontSize <= 0 ? fontSize : style.fontSize);
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
        if (!isFirstVisualLineOfLogicalLine(line) || headingLevelForVisualLine(line) == null) {
            return 0;
        }
        double lineWidth = measureRange(line.start, line.end);
        return Math.max(0, (availableLineWidth() - lineWidth) / 2.0);
    }

    private int lineStart(int offset) {
        List<VisualLine> lines = visualLines();
        for (VisualLine line : lines) {
            if (offset >= line.start && offset <= line.end) {
                return line.start;
            }
        }
        return 0;
    }

    private int lineEnd(int offset) {
        List<VisualLine> lines = visualLines();
        for (VisualLine line : lines) {
            if (offset >= line.start && offset <= line.end) {
                return line.end;
            }
        }
        return text.length();
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

    private double xForOffsetInLine(VisualLine line, int offset) {
        int safeOffset = Math.max(line.start, Math.min(line.end, offset));
        return TEXT_LEFT + headingCenterOffset(line) + measureRange(line.start, safeOffset);
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
        if (!renderMarkupHidden) {
            for (TextRange range : hiddenImageBlockRanges) {
                if (range.contains(offset)) {
                    return true;
                }
            }
            return false;
        }
        for (TextRange range : hiddenImageBlockRanges) {
            if (range.contains(offset)) {
                return true;
            }
        }
        for (TextRange range : hiddenMarkupRanges) {
            if (range.contains(offset)) {
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
                (style.flags & (MarkedArea.BOLD | MarkedArea.ITALIC)));
        return textWidthCache.computeIfAbsent(key, ignored -> {
            measuringText.setText(value);
            measuringText.setFont(font);
            return Math.max(MIN_CHAR_WIDTH, measuringText.getLayoutBounds().getWidth());
        });
    }

    private boolean sameTextStyle(RenderStyle a, RenderStyle b) {
        return (a.flags & (MarkedArea.BOLD | MarkedArea.ITALIC | MarkedArea.UNDERLINE))
                == (b.flags & (MarkedArea.BOLD | MarkedArea.ITALIC | MarkedArea.UNDERLINE))
                && Objects.equals(a.fontFamily, b.fontFamily)
                && Double.compare(a.fontSize, b.fontSize) == 0
                && Objects.equals(a.background, b.background)
                && Objects.equals(a.textColor, b.textColor)
                && a.underline == b.underline
                && Objects.equals(a.underlineColor, b.underlineColor);
    }

    private double lineHeight() {
        return Math.ceil(fontSize * 1.55);
    }

    private double baseline() {
        return Math.ceil(fontSize * 1.15);
    }

    public record SearchMatch(int start, int end) {}
    private record VisualLine(int start, int end) {}
    private record TextPosition(int lineIndex, int column) {}
    private record ViewportAnchor(int offset, double yOffset) {}
    private record Snapshot(String text, int caret, int anchor, List<StyleRange> styles) {}
    private record AutoRule(String regex, int groupIndex, int styleFlags) {}
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
        Color textColor;
        Color background;
        int backgroundPriority = Integer.MIN_VALUE;
        boolean underline;
        Color underlineColor;
        int underlinePriority = Integer.MIN_VALUE;
        MarkedArea interactiveArea;
        int interactivePriority = Integer.MIN_VALUE;
    }

    public static final class MarkedArea {
        public static final boolean REGEX = true;
        public static final boolean TEXT = false;
        public static final int BOLD = 1;
        public static final int ITALIC = 2;
        public static final int UNDERLINE = 4;
        public static final String LIGHTGREY = "#d9d9d9";
        public static final String SEARCH_MATCH_COLOR = "#ffeb3b";
        public static final String SEARCH_CURRENT_MATCH_COLOR = "#ff9800";

        public enum Type {
            MARKDOWN(10),
            HIGHLIGHT(30),
            LINK(50),
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
        private Color underlineColor;
        private int styleFlags;
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
            ranges.add(new TextRange(start, end));
            editor.render();
            return this;
        }

        public MarkedArea markColor(String colorValue) {
            color = Color.web(colorValue);
            editor.render();
            return this;
        }

        public MarkedArea markType(Type type) {
            this.type = type == null ? Type.HIGHLIGHT : type;
            editor.render();
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
                        style.background = color;
                        style.backgroundPriority = priority;
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

        @Override
        public String toString() {
            return name == null ? "MarkedArea" : name;
        }
    }
}
