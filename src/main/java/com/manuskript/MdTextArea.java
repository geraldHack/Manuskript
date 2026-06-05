package com.manuskript;

import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;

import java.util.List;
import java.util.function.Consumer;

/**
 * Wiederverwendbare Markdown-Textfläche mit optionalem Minimal-Toolbar
 * auf Basis von {@link ManuskriptTextEditor}.
 */
public class MdTextArea extends VBox {

    private final MdTextAreaOptions options;
    private final ManuskriptTextEditor editor;
    private final StringProperty textProperty = new SimpleStringProperty("");
    private final VBox toolbarBox;
    private final MdTextAreaSearchSupport searchSupport;

    private ComboBox<Double> fontSizeCombo;
    private CheckBox hideMarkupCheckbox;

    public MdTextArea(MdTextAreaOptions options) {
        this.options = options == null ? MdTextAreaOptions.builder().build() : options;
        getStyleClass().add("md-text-area");
        setFocusTraversable(false);

        editor = new ManuskriptTextEditor();
        editor.registerDefaultFormatAutoRules();
        applyOptionsToEditor(this.options);
        editor.setOnTextChanged(text -> textProperty.set(text == null ? "" : text));

        searchSupport = new MdTextAreaSearchSupport(editor, this.options.onSearchStatus());
        toolbarBox = buildToolbar(this.options);
        if (toolbarBox != null) {
            getChildren().add(toolbarBox);
        }
        getChildren().add(editor);
        VBox.setVgrow(editor, Priority.ALWAYS);

        applyTheme(this.options.themeIndex());
    }

    private void applyOptionsToEditor(MdTextAreaOptions opts) {
        editor.setFontFamilyForAll(opts.fontFamily());
        editor.setFontSizeForAll(opts.fontSize());
        editor.setLineSpacing(opts.lineSpacing());
        editor.setParagraphSpacing(opts.paragraphSpacing());
        editor.setJustifyText(opts.justifyText());
        editor.setRenderMarkupHidden(opts.hideMarkup());
        editor.setShowLineNumbers(opts.showLineNumbers());
        editor.setEditable(opts.editable());
    }

    private VBox buildToolbar(MdTextAreaOptions opts) {
        if (!opts.showToolbar()) {
            return null;
        }
        boolean hasContent = opts.enableUndoRedo()
                || opts.enableFontControls()
                || opts.enableJustify()
                || opts.enableBasicFormatting()
                || opts.enableSearch()
                || opts.enableHideMarkupToggle();
        if (!hasContent) {
            return null;
        }

        VBox toolbar = new VBox(8);
        toolbar.getStyleClass().add("md-text-area-toolbar");
        toolbar.setPadding(new Insets(6, 8, 6, 8));

        if (opts.enableUndoRedo() || opts.enableFontControls() || opts.enableHideMarkupToggle()) {
            toolbar.getChildren().add(buildFontRow(opts));
        }
        if (opts.enableJustify() || opts.enableBasicFormatting()) {
            toolbar.getChildren().add(buildFormatRow(opts));
        }
        if (opts.enableSearch()) {
            toolbar.getChildren().add(searchSupport.buildSearchBlock(opts.enableReplace()));
        }

        var wrapLength = Bindings.max(220, widthProperty().subtract(16));
        for (var child : toolbar.getChildren()) {
            if (child instanceof FlowPane flowPane) {
                flowPane.prefWrapLengthProperty().bind(wrapLength);
            }
        }
        return toolbar;
    }

    private HBox buildFontRow(MdTextAreaOptions opts) {
        HBox row = new HBox(6);
        row.setAlignment(Pos.CENTER_LEFT);

        if (opts.enableUndoRedo()) {
            Button undo = toolbarButton("Rückgängig", "Rückgängig (Strg+Z)", () -> {
                editor.undo();
                editor.requestInputFocus();
            });
            Button redo = toolbarButton("Wiederholen", "Wiederholen (Strg+Y)", () -> {
                editor.redo();
                editor.requestInputFocus();
            });
            row.getChildren().addAll(undo, redo, toolbarSeparator());
        }

        if (opts.enableFontControls()) {
            fontSizeCombo = new ComboBox<>();
            fontSizeCombo.getItems().addAll(10.0, 11.0, 12.0, 14.0, 16.0, 18.0, 20.0, 24.0, 28.0, 32.0);
            double initialSize = opts.fontSize();
            if (!fontSizeCombo.getItems().contains(initialSize)) {
                fontSizeCombo.getItems().add(initialSize);
                fontSizeCombo.getItems().sort(Double::compare);
            }
            fontSizeCombo.setValue(initialSize);
            fontSizeCombo.setPrefWidth(80);

            Button fontLarger = toolbarButton("A+", "Schriftgröße erhöhen", () -> {
                double next = Math.min(96, fontSizeCombo.getValue() + 1);
                fontSizeCombo.setValue(next);
                editor.requestInputFocus();
            });
            Button fontSmaller = toolbarButton("A-", "Schriftgröße verringern", () -> {
                double next = Math.max(6, fontSizeCombo.getValue() - 1);
                fontSizeCombo.setValue(next);
                editor.requestInputFocus();
            });

            ComboBox<String> fontFamily = new ComboBox<>();
            fontFamily.getItems().addAll(Font.getFamilies());
            fontFamily.setValue(opts.fontFamily());
            fontFamily.setMinWidth(140);
            fontFamily.setPrefWidth(180);
            fontFamily.setMaxWidth(220);
            fontFamily.valueProperty().addListener((obs, oldValue, newValue) -> {
                if (newValue != null) {
                    editor.setFontFamilyForAll(newValue);
                    notifyConsumer(opts.onFontFamilyChanged(), newValue);
                }
            });

            fontSizeCombo.valueProperty().addListener((obs, oldValue, newValue) -> {
                if (newValue != null) {
                    editor.setFontSizeForAll(newValue);
                    notifyConsumer(opts.onFontSizeChanged(), newValue);
                }
            });

            ComboBox<Double> lineSpacing = new ComboBox<>();
            lineSpacing.getItems().addAll(1.0, 1.15, 1.2, 1.35, 1.5, 1.55, 1.8, 2.0, 2.5);
            double initialLineSpacing = opts.lineSpacing();
            if (!lineSpacing.getItems().contains(initialLineSpacing)) {
                lineSpacing.getItems().add(initialLineSpacing);
                lineSpacing.getItems().sort(Double::compare);
            }
            lineSpacing.setValue(initialLineSpacing);
            lineSpacing.setPrefWidth(72);
            lineSpacing.setTooltip(new Tooltip("Zeilenabstand (Faktor zur Schriftgröße)"));
            lineSpacing.valueProperty().addListener((obs, oldValue, newValue) -> {
                if (newValue != null) {
                    editor.setLineSpacing(newValue);
                    notifyConsumer(opts.onLineSpacingChanged(), newValue);
                }
            });

            ComboBox<Integer> paragraphSpacing = new ComboBox<>();
            paragraphSpacing.getItems().addAll(0, 4, 6, 8, 10, 12, 16, 20, 24, 32);
            int initialParagraphSpacing = (int) Math.round(opts.paragraphSpacing());
            if (!paragraphSpacing.getItems().contains(initialParagraphSpacing)) {
                paragraphSpacing.getItems().add(initialParagraphSpacing);
                paragraphSpacing.getItems().sort(Integer::compare);
            }
            paragraphSpacing.setValue(initialParagraphSpacing);
            paragraphSpacing.setPrefWidth(64);
            paragraphSpacing.setTooltip(new Tooltip(
                    "Mindesthöhe von Leerzeilen im WYSIWYG-Modus (Markdown ausblenden)"));
            paragraphSpacing.valueProperty().addListener((obs, oldValue, newValue) -> {
                if (newValue != null) {
                    editor.setParagraphSpacing(newValue);
                    notifyConsumer(opts.onParagraphSpacingChanged(), newValue);
                }
            });

            row.getChildren().addAll(
                    fontSmaller, fontLarger,
                    new Label("Font:"), fontFamily,
                    new Label("Größe:"), fontSizeCombo,
                    new Label("Zeilenabstand:"), lineSpacing,
                    new Label("Absatzabstand:"), paragraphSpacing);
        }

        if (opts.enableHideMarkupToggle()) {
            hideMarkupCheckbox = new CheckBox("Markdown ausblenden");
            hideMarkupCheckbox.setSelected(opts.hideMarkup());
            hideMarkupCheckbox.setTooltip(new Tooltip(
                    "Markdown ausblenden: Syntax verstecken, WYSIWYG. Aus = Markdown-Quelltext"));
            hideMarkupCheckbox.selectedProperty().addListener((obs, oldValue, newValue) -> {
                editor.setRenderMarkupHidden(newValue);
                notifyConsumer(opts.onHideMarkupChanged(), newValue);
            });
            row.getChildren().add(hideMarkupCheckbox);
        }
        return row;
    }

    private FlowPane buildFormatRow(MdTextAreaOptions opts) {
        FlowPane formatPane = new FlowPane(6, 4);
        formatPane.setAlignment(Pos.CENTER_LEFT);

        if (opts.enableJustify()) {
            CheckBox justifyText = new CheckBox("Blocksatz");
            justifyText.setSelected(opts.justifyText());
            justifyText.setTooltip(new Tooltip(
                    "Text im Blocksatz ausrichten (letzte Zeile eines Absatzes linksbündig)"));
            justifyText.selectedProperty().addListener((obs, oldValue, newValue) -> {
                editor.setJustifyText(newValue);
                notifyConsumer(opts.onJustifyChanged(), newValue);
            });
            formatPane.getChildren().add(justifyText);
        }

        if (opts.enableBasicFormatting()) {
            formatPane.getChildren().addAll(
                    toolbarButton("B", "Fett (Strg+B)", () -> {
                        editor.toggleBold();
                        editor.requestInputFocus();
                    }),
                    toolbarButton("I", "Kursiv (Strg+I)", () -> {
                        editor.toggleItalic();
                        editor.requestInputFocus();
                    }),
                    toolbarButton("U", "Unterstrichen (Strg+U)", () -> {
                        editor.toggleUnderline();
                        editor.requestInputFocus();
                    }),
                    toolbarButton("S", "Durchgestrichen (~~text~~)", () -> {
                        editor.toggleStrikethrough();
                        editor.requestInputFocus();
                    }));
        }
        return formatPane;
    }

    public ManuskriptTextEditor getEditor() {
        return editor;
    }

    public String getText() {
        return editor.getText();
    }

    public void setText(String value) {
        editor.setText(value == null ? "" : value);
        textProperty.set(value == null ? "" : value);
    }

    public void clear() {
        setText("");
    }

    public StringProperty textProperty() {
        return textProperty;
    }

    public void setEditable(boolean editable) {
        editor.setEditable(editable);
    }

    public boolean isEditable() {
        return editor.isEditable();
    }

    @Override
    public void requestFocus() {
        editor.requestInputFocus();
    }

    public void applyTheme(int themeIndex) {
        editor.applyTheme(themeIndex);
        if (toolbarBox != null) {
            applyThemeToNode(toolbarBox, themeIndex);
        }
    }

    public void focusSearchField() {
        searchSupport.focusSearchField();
    }

    public void focusReplaceField() {
        searchSupport.focusReplaceField();
    }

    public void findNextMatch() {
        searchSupport.findNextMatch();
    }

    public void findPreviousMatch() {
        searchSupport.findPreviousMatch();
    }

    public void replaceNextMatch() {
        searchSupport.replaceNextMatch();
    }

    public void handleSearchNavigationKey(KeyEvent event) {
        searchSupport.handleSearchNavigationKey(event);
    }

    public void positionCaret(int position) {
        int len = getText().length();
        int clamped = Math.max(0, Math.min(len, position));
        editor.selectRange(clamped, clamped);
        editor.revealMatchAt(clamped, clamped);
    }

    public void appendText(String addition) {
        if (addition == null || addition.isEmpty()) {
            return;
        }
        setText(getText() + addition);
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

    private static void applyThemeToNode(javafx.scene.Node node, int themeIndex) {
        if (node == null) {
            return;
        }
        node.getStyleClass().removeAll(
                "theme-dark", "theme-light", "blau-theme", "gruen-theme",
                "lila-theme", "weiss-theme", "pastell-theme");
        switch (themeIndex) {
            case 0 -> node.getStyleClass().add("weiss-theme");
            case 2 -> node.getStyleClass().add("pastell-theme");
            case 3 -> node.getStyleClass().addAll("theme-dark", "blau-theme");
            case 4 -> node.getStyleClass().addAll("theme-dark", "gruen-theme");
            case 5 -> node.getStyleClass().addAll("theme-dark", "lila-theme");
            default -> node.getStyleClass().add("theme-dark");
        }
    }

    private static <T> void notifyConsumer(Consumer<T> consumer, T value) {
        if (consumer != null) {
            consumer.accept(value);
        }
    }

    /**
     * Such-/Ersetzen-Logik für {@link MdTextArea}.
     */
    static final class MdTextAreaSearchSupport {

        private final ManuskriptTextEditor editor;
        private final Consumer<String> statusConsumer;

        private TextField searchField;
        private TextField replaceField;
        private CheckBox regexCheckbox;
        private ManuskriptTextEditor.MarkedArea searchMarks;
        private ManuskriptTextEditor.MarkedArea searchCurrentMark;
        private List<ManuskriptTextEditor.SearchMatch> cachedSearchMatches = List.of();
        private String lastSearchCacheKey;
        private int currentSearchMatchIndex = -1;

        MdTextAreaSearchSupport(ManuskriptTextEditor editor, Consumer<String> statusConsumer) {
            this.editor = editor;
            this.statusConsumer = statusConsumer;
        }

        VBox buildSearchBlock(boolean enableReplace) {
            searchField = new TextField();
            searchField.setPromptText("Suchen");
            searchField.setPrefWidth(160);
            searchField.setTooltip(new Tooltip("Suchen (Strg+F, Enter = weiter, F3 = weiter)"));

            replaceField = new TextField();
            replaceField.setPromptText("Ersetzen");
            replaceField.setPrefWidth(140);
            replaceField.setVisible(enableReplace);
            replaceField.setManaged(enableReplace);

            regexCheckbox = new CheckBox("Regex");
            setupSearchFieldShortcuts();

            Button find = toolbarButton("Weiter", "Nächster Treffer (Enter / F3)", this::findNextMatch);
            Button findPrevious = toolbarButton("Zurück", "Vorheriger Treffer (Umschalt+Enter / Umschalt+F3)",
                    this::findPreviousMatch);

            searchField.setMinWidth(80);
            searchField.setPrefWidth(140);
            searchField.setMaxWidth(Double.MAX_VALUE);
            replaceField.setMinWidth(80);
            replaceField.setPrefWidth(120);
            replaceField.setMaxWidth(Double.MAX_VALUE);

            HBox searchInputs = new HBox(6);
            searchInputs.setAlignment(Pos.CENTER_LEFT);
            searchInputs.getChildren().add(searchField);
            if (enableReplace) {
                searchInputs.getChildren().add(replaceField);
            }
            HBox.setHgrow(searchField, Priority.ALWAYS);
            if (enableReplace) {
                HBox.setHgrow(replaceField, Priority.ALWAYS);
            }

            FlowPane searchActions = new FlowPane(6, 4);
            searchActions.setAlignment(Pos.CENTER_LEFT);
            searchActions.getChildren().addAll(regexCheckbox, find, findPrevious);
            if (enableReplace) {
                Button replaceOne = toolbarButton("Ersetzen",
                        "Aktuellen Treffer ersetzen und weiter (Strg+H)", this::replaceNextMatch);
                Button replaceAll = toolbarButton("Alle ersetzen", "Alle Treffer ersetzen", () -> {
                    int count = editor.replaceAll(
                            searchField.getText(),
                            replaceField.getText() == null ? "" : replaceField.getText(),
                            regexCheckbox.isSelected(),
                            true);
                    invalidateSearchCache();
                    reportStatus(count + " Treffer ersetzt");
                });
                searchActions.getChildren().addAll(replaceOne, replaceAll);
            }

            return new VBox(4, searchInputs, searchActions);
        }

        void focusSearchField() {
            if (searchField == null) {
                return;
            }
            if (editor.hasTextSelection()) {
                searchField.setText(editor.getSelectedText());
            }
            searchField.requestFocus();
            searchField.selectAll();
        }

        void focusReplaceField() {
            if (searchField == null || replaceField == null) {
                return;
            }
            if (searchField.getText() == null || searchField.getText().isBlank()) {
                if (editor.hasTextSelection()) {
                    searchField.setText(editor.getSelectedText());
                }
            }
            searchField.requestFocus();
            replaceField.requestFocus();
            replaceField.selectAll();
        }

        void findNextMatch() {
            if (searchField == null) {
                return;
            }
            String query = searchField.getText();
            if (query == null || query.isBlank()) {
                focusSearchField();
                reportStatus("Suchbegriff eingeben", true);
                return;
            }
            refreshSearchCacheIfNeeded();
            if (cachedSearchMatches.isEmpty()) {
                clearSearchMarks();
                reportStatus("Keine Treffer", true);
                return;
            }
            int nextIndex = currentSearchMatchIndex < 0
                    ? 0
                    : (currentSearchMatchIndex + 1) % cachedSearchMatches.size();
            goToSearchMatch(nextIndex);
        }

        void findPreviousMatch() {
            if (searchField == null) {
                return;
            }
            String query = searchField.getText();
            if (query == null || query.isBlank()) {
                focusSearchField();
                reportStatus("Suchbegriff eingeben", true);
                return;
            }
            refreshSearchCacheIfNeeded();
            if (cachedSearchMatches.isEmpty()) {
                clearSearchMarks();
                reportStatus("Keine Treffer", true);
                return;
            }
            int previousIndex = currentSearchMatchIndex <= 0
                    ? cachedSearchMatches.size() - 1
                    : currentSearchMatchIndex - 1;
            goToSearchMatch(previousIndex);
        }

        void replaceNextMatch() {
            if (searchField == null || replaceField == null) {
                return;
            }
            String query = searchField.getText();
            if (query == null || query.isBlank()) {
                focusSearchField();
                reportStatus("Suchbegriff eingeben", true);
                return;
            }
            refreshSearchCacheIfNeeded();
            if (cachedSearchMatches.isEmpty()) {
                reportStatus("Keine Treffer", true);
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
                reportStatus("Ersetzt – keine weiteren Treffer");
                return;
            }
            int nextIndex = Math.min(index, cachedSearchMatches.size() - 1);
            goToSearchMatch(nextIndex);
            reportStatus("Ersetzt, Treffer " + (nextIndex + 1) + " von " + cachedSearchMatches.size());
        }

        void handleSearchNavigationKey(KeyEvent event) {
            if (searchField == null || event.getCode() != KeyCode.F3) {
                return;
            }
            event.consume();
            if (event.isShiftDown()) {
                findPreviousMatch();
            } else {
                findNextMatch();
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

        private void goToSearchMatch(int index) {
            ManuskriptTextEditor.SearchMatch match = cachedSearchMatches.get(index);
            currentSearchMatchIndex = index;
            markCurrentSearchMatch(index);
            editor.revealMatchAt(match.start(), match.end());
            reportStatus("Treffer " + (index + 1) + " von " + cachedSearchMatches.size());
        }

        private void reportStatus(String message) {
            reportStatus(message, false);
        }

        private void reportStatus(String message, boolean error) {
            if (statusConsumer != null) {
                statusConsumer.accept(message);
            }
        }
    }
}
