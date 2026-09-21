package com.manuskript;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.Cursor;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.prefs.Preferences;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Karten-Ansicht für {@code characters.txt}: blättern, suchen, strukturiert bearbeiten.
 */
public final class CharacterCardsEditor extends BorderPane implements WorldEditorTabContent {

    private static final Logger logger = LoggerFactory.getLogger(CharacterCardsEditor.class);

    @FunctionalInterface
    public interface SingleCharacterAiRequest {
        void request(CharacterSheetDocument.CharacterEntry entry,
                     CharacterCardAiOptions options,
                     Consumer<CharacterSheetDocument.CharacterEntry> onSuccess,
                     Consumer<Throwable> onError);
    }

    public interface CharacterAiDialogHost {
        Optional<CharacterCardAiOptions> promptAiOptions(String characterName);
    }

    /** Padding (6+6) + Border (1+1) am MdTextArea-Wrapper – für Höhen-/Breitenmessung. */
    private static final double FIELD_CHROME_HORIZONTAL = 14.0;
    private static final double FIELD_CHROME_VERTICAL = 14.0;
    private static final double FIELD_FONT_SIZE = 13.0;
    private static final Set<String> SHORT_FIELDS = Set.of(
            "Kurzname",
            "Andere Namen / Alias"
    );

    private static final List<String> DEFAULT_ROLE_SUGGESTIONS = List.of(
            "Protagonist",
            "Protagonistin",
            "Antagonist",
            "Antagonistin",
            "Nebenfigur",
            "Mentor",
            "Sidekick",
            "Liebesinteresse",
            "Rivale",
            "Elternteil",
            "Kind",
            "Anführer",
            "Komparse",
            "Gastrolle"
    );

    private final Window owner;
    private final File projectDirectory;
    private int themeIndex;
    private final Preferences imagePrefs;

    private String preamble = "";
    private final List<CharacterSheetDocument.Block> blocks = new ArrayList<>();
    private final ObservableList<CharacterSheetDocument.CharacterEntry> characters =
            FXCollections.observableArrayList();
    private final FilteredList<CharacterSheetDocument.CharacterEntry> filteredCharacters;
    private final ListView<CharacterSheetDocument.CharacterEntry> characterList = new ListView<>();
    private final TextField searchField = new TextField();
    private final ScrollPane detailScroll = new ScrollPane();
    private final VBox detailBox = new VBox(14);
    private final Label emptyLabel = new Label("Figur auswählen oder „Neue Figur“ anlegen.");
    private final StackPane detailContainer = new StackPane();
    private final BorderPane characterDetailPane = new BorderPane();

    private final TextField nameField = new TextField();
    private final ImageView portraitView = new ImageView();
    private final Label portraitPlaceholder = new Label("Kein Bild");
    private final Map<String, TextField> shortFieldEditors = new LinkedHashMap<>();
    private final Map<String, MdTextArea> fieldEditors = new LinkedHashMap<>();
    private ComboBox<String> roleComboBox;

    private SingleCharacterAiRequest aiRequest;
    private CharacterAiDialogHost aiDialogHost;
    private MarkdownImageLightbox imageLightbox;
    private ChangeListener<String> externalTextListener;
    private boolean suppressChangeEvents;
    private boolean suppressFieldRefresh;
    private boolean suppressSelectionEvents;
    private CharacterSheetDocument.CharacterEntry selectedEntry;

    public CharacterCardsEditor(Window owner, File projectDirectory, int themeIndex) {
        this.owner = owner;
        this.projectDirectory = projectDirectory;
        this.themeIndex = themeIndex;
        this.imagePrefs = Preferences.userNodeForPackage(ManuskriptEditorTestWindow.class);

        getStyleClass().add("character-cards-root");
        EditorDialogThemes.applyToNode(this, themeIndex);
        filteredCharacters = new FilteredList<>(characters, entry -> true);
        buildLayout();
        bindSearch();
        characterList.getSelectionModel().selectedItemProperty().addListener((obs, oldEntry, newEntry) -> {
            if (suppressFieldRefresh || suppressSelectionEvents) {
                return;
            }
            if (newEntry != null && selectedEntry != null && entryContentEquals(selectedEntry, newEntry)) {
                selectedEntry = newEntry;
                return;
            }
            showCharacter(newEntry);
        });
    }

    public void setSingleCharacterAiRequest(SingleCharacterAiRequest aiRequest) {
        this.aiRequest = aiRequest;
    }

    public void setCharacterAiDialogHost(CharacterAiDialogHost aiDialogHost) {
        this.aiDialogHost = aiDialogHost;
    }

    @Override
    public void attachImageLightbox(MarkdownImageLightbox lightbox) {
        this.imageLightbox = lightbox;
    }

    @Override
    public void applyTheme(int newThemeIndex) {
        this.themeIndex = newThemeIndex;
        EditorDialogThemes.applyToNode(this, newThemeIndex);
        for (MdTextArea area : fieldEditors.values()) {
            EditorDialogThemes.applyToNode(area, newThemeIndex);
            area.getEditor().applyEmbeddedFieldTheme(newThemeIndex);
        }
    }

    @Override
    public Node getView() {
        return this;
    }

    @Override
    public String getText() {
        commitVisibleFieldsToModel();
        return CharacterSheetDocument.serialize(buildDocument());
    }

    /** Liest die sichtbaren Editor-Widgets in das Modell — Absicherung vor Speichern. */
    private void commitVisibleFieldsToModel() {
        if (selectedEntry == null) {
            return;
        }
        boolean wasSuppressed = suppressFieldRefresh;
        suppressFieldRefresh = false;
        try {
            onNameChanged(nameField.getText());
            if (roleComboBox != null && roleComboBox.getEditor() != null) {
                updateSelectedField("Rolle", roleComboBox.getEditor().getText());
            }
            for (Map.Entry<String, TextField> entry : shortFieldEditors.entrySet()) {
                updateSelectedField(entry.getKey(), entry.getValue().getText());
            }
            for (Map.Entry<String, MdTextArea> entry : fieldEditors.entrySet()) {
                updateSelectedField(entry.getKey(), entry.getValue().getText());
            }
        } finally {
            suppressFieldRefresh = wasSuppressed;
        }
    }

    @Override
    public void setText(String text) {
        suppressChangeEvents = true;
        suppressFieldRefresh = true;
        try {
            loadDocument(CharacterSheetDocument.parse(text), true);
        } finally {
            suppressFieldRefresh = false;
            suppressChangeEvents = false;
        }
    }

    /** Extraktions-Ergebnis übernehmen und zur ersten neuen Figur springen. */
    public void applyExtractedMarkdown(String markdown) {
        int countBefore = characters.size();
        suppressChangeEvents = true;
        suppressFieldRefresh = true;
        try {
            loadDocument(CharacterSheetDocument.parse(markdown == null ? "" : markdown), false);
        } finally {
            suppressFieldRefresh = false;
            suppressChangeEvents = false;
        }
        if (characters.isEmpty()) {
            showCharacter(null);
            return;
        }
        int selectIndex = characters.size() > countBefore ? countBefore : 0;
        suppressSelectionEvents = true;
        try {
            characterList.getSelectionModel().select(selectIndex);
            characterList.scrollTo(selectIndex);
        } finally {
            suppressSelectionEvents = false;
        }
        showCharacter(characters.get(selectIndex));
    }

    @Override
    public void addTextChangeListener(ChangeListener<String> listener) {
        this.externalTextListener = listener;
    }

    @Override
    public void requestFocus() {
        if (selectedEntry != null) {
            nameField.requestFocus();
        } else if (!characters.isEmpty()) {
            characterList.requestFocus();
        } else {
            searchField.requestFocus();
        }
    }

    @Override
    public void navigateToSection(String heading) {
        if (heading == null || heading.isBlank()) {
            return;
        }
        String target = WorldbuildingTermIndex.normalizeCharacterHeading(heading.trim());
        for (CharacterSheetDocument.CharacterEntry entry : characters) {
            if (entry.name().equalsIgnoreCase(target)
                    || WorldbuildingTermIndex.normalizeKey(entry.name())
                    .equals(WorldbuildingTermIndex.normalizeKey(target))) {
                characterList.getSelectionModel().select(entry);
                characterList.scrollTo(entry);
                requestFocus();
                return;
            }
        }
    }

    public void addCharacter() {
        CharacterSheetDocument.CharacterEntry entry = new CharacterSheetDocument.CharacterEntry("Neue Figur");
        blocks.add(new CharacterSheetDocument.CharacterBlock(entry));
        refreshCharacterList();
        if (!characters.isEmpty()) {
            characterList.getSelectionModel().select(characters.get(characters.size() - 1));
            characterList.scrollTo(characters.size() - 1);
        }
        nameField.requestFocus();
        nameField.selectAll();
        notifyContentChanged();
    }

    private void buildLayout() {
        searchField.setPromptText("Figuren suchen …");
        searchField.getStyleClass().addAll("character-cards-search", "world-editor-textarea");
        installParentScrollForwarding(searchField);

        characterList.setItems(filteredCharacters);
        characterList.getStyleClass().add("character-card-list");
        characterList.setCellFactory(list -> new ListCell<>() {
            private final Label title = new Label();
            private final Label subtitle = new Label();
            private final VBox box = new VBox(2, title, subtitle);

            {
                box.getStyleClass().add("character-card-list-cell-content");
                box.setFillWidth(true);
                box.setMaxWidth(Double.MAX_VALUE);
                box.setBackground(Background.EMPTY);
                title.getStyleClass().add("character-card-list-title");
                title.setMaxWidth(Double.MAX_VALUE);
                title.setBackground(Background.EMPTY);
                subtitle.getStyleClass().add("character-card-list-subtitle");
                subtitle.setMaxWidth(Double.MAX_VALUE);
                subtitle.setBackground(Background.EMPTY);
            }

            @Override
            protected void updateItem(CharacterSheetDocument.CharacterEntry entry, boolean empty) {
                super.updateItem(entry, empty);
                if (empty || entry == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                title.setText(entry.name());
                String role = entry.field("Rolle");
                subtitle.setText(role == null || role.isBlank() ? "—" : role);
                subtitle.setVisible(!subtitle.getText().equals("—"));
                subtitle.setManaged(subtitle.isVisible());
                setGraphic(box);
                setText(null);
            }
        });

        Button addButton = new Button("+ Neue Figur");
        addButton.getStyleClass().add("character-cards-add-button");
        addButton.setOnAction(e -> addCharacter());

        VBox sidebar = new VBox(8, searchField, characterList, addButton);
        sidebar.getStyleClass().add("character-cards-sidebar");
        sidebar.setPadding(new Insets(8, 8, 8, 0));
        VBox.setVgrow(characterList, Priority.ALWAYS);
        setLeft(sidebar);

        detailScroll.setFitToWidth(true);
        detailScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        detailScroll.setPadding(new Insets(6));
        detailScroll.getStyleClass().add("character-card-detail-scroll");
        detailBox.getStyleClass().add("character-card-detail");
        detailBox.setPadding(new Insets(16, 20, 16, 12));
        installParentScrollForwarding(detailBox);
        detailScroll.setContent(detailBox);
        characterDetailPane.getStyleClass().add("character-detail-pane");
        characterDetailPane.setCenter(detailScroll);
        BorderPane.setMargin(detailScroll, Insets.EMPTY);

        emptyLabel.getStyleClass().add("character-card-empty-label");
        detailContainer.getChildren().addAll(emptyLabel, characterDetailPane);
        StackPane.setAlignment(emptyLabel, Pos.CENTER);
        setCenter(detailContainer);

        buildDetailPanel();
        showCharacter(null);
    }

    private void buildDetailPanel() {
        nameField.getStyleClass().addAll("character-card-name-field", "world-editor-textarea");
        nameField.setPromptText("Name der Figur");
        nameField.textProperty().addListener((obs, oldName, newName) -> onNameChanged(newName));

        Button deleteButton = new Button("Löschen");
        deleteButton.getStyleClass().add("character-cards-delete-button");
        deleteButton.setOnAction(e -> deleteSelected());

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox header = new HBox(10, nameField, headerSpacer, deleteButton);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("character-card-header");
        HBox.setHgrow(nameField, Priority.ALWAYS);

        portraitView.setFitWidth(148);
        portraitView.setPreserveRatio(true);
        portraitView.getStyleClass().add("character-card-portrait");
        portraitPlaceholder.getStyleClass().add("character-card-portrait-placeholder");

        StackPane portraitPane = new StackPane(portraitView, portraitPlaceholder);
        portraitPane.getStyleClass().add("character-card-portrait-pane");
        portraitPane.setMinSize(148, 148);
        portraitPane.setPrefSize(148, 168);
        portraitPane.setCursor(Cursor.HAND);
        portraitPane.setOnMouseClicked(event -> {
            if (event.getButton() != MouseButton.PRIMARY || imageLightbox == null) {
                return;
            }
            Image image = portraitView.getImage();
            if (image == null || selectedEntry == null) {
                return;
            }
            imageLightbox.toggle(image, selectedEntry.name(), portraitPreviewKey(selectedEntry));
            event.consume();
        });

        Button imageButton = new Button("Bild …");
        imageButton.setOnAction(e -> chooseImage());
        Button removeImageButton = new Button("Entfernen");
        removeImageButton.setOnAction(e -> clearImage());
        VBox portraitBox = new VBox(8, portraitPane, new HBox(6, imageButton, removeImageButton));
        portraitBox.setAlignment(Pos.TOP_CENTER);
        portraitBox.getStyleClass().add("character-card-portrait-column");

        VBox fieldsBox = new VBox(18);
        fieldsBox.getStyleClass().add("character-card-fields");
        for (String label : CharacterSheetDocument.STANDARD_FIELD_LABELS) {
            if ("Notizen".equals(label)) {
                continue;
            }
            fieldsBox.getChildren().add(createFieldSection(label));
        }
        fieldsBox.getChildren().add(createFieldSection("Notizen"));

        HBox body = new HBox(18, fieldsBox, portraitBox);
        body.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(fieldsBox, Priority.ALWAYS);

        detailBox.getChildren().addAll(header, body);
        detailBox.widthProperty().addListener((obs, oldWidth, newWidth) -> reflowAllFieldHeights());
    }

    /** KI-Ausfüllen für die aktuell ausgewählte Figur (Button in der Tab-Leiste unten). */
    public void requestAiFillForSelected() {
        requestAiForSelected();
    }

    public boolean hasSelectedCharacter() {
        return selectedEntry != null;
    }

    public int getCharacterCount() {
        return characters.size();
    }

    private void installParentScrollForwarding(Node node) {
        node.addEventFilter(ScrollEvent.SCROLL, event -> {
            scrollDetailBy(event.getDeltaY());
            event.consume();
        });
    }

    private void scrollDetailBy(double deltaY) {
        if (detailScroll.getContent() == null) {
            return;
        }
        double viewportH = detailScroll.getViewportBounds().getHeight();
        double contentH = detailScroll.getContent().getBoundsInLocal().getHeight();
        double max = Math.max(0, contentH - viewportH);
        if (max <= 0) {
            return;
        }
        double pixelOffset = detailScroll.getVvalue() * max;
        double next = Math.max(0, Math.min(max, pixelOffset - deltaY));
        detailScroll.setVvalue(next / max);
    }

    private void reflowAllFieldHeights() {
        for (Map.Entry<String, MdTextArea> entry : fieldEditors.entrySet()) {
            applyFieldEditorHeight(entry.getValue(), entry.getKey());
        }
    }

    private VBox createFieldSection(String label) {
        Label fieldLabel = new Label(label);
        fieldLabel.getStyleClass().add("character-card-field-label");
        if ("Rolle".equals(label)) {
            return createRoleFieldSection(fieldLabel);
        }
        if (isShortField(label)) {
            TextField field = createShortFieldEditor(label);
            shortFieldEditors.put(label, field);
            VBox section = new VBox(6, fieldLabel, field);
            section.getStyleClass().add("character-card-field");
            section.setPadding(new Insets(2, 2, 4, 2));
            return section;
        }
        MdTextArea area = createFieldEditor(label);
        fieldEditors.put(label, area);
        VBox section = new VBox(6, fieldLabel, area);
        section.getStyleClass().add("character-card-field");
        section.setPadding(new Insets(2, 2, 4, 2));
        return section;
    }

    private VBox createRoleFieldSection(Label fieldLabel) {
        roleComboBox = new ComboBox<>();
        roleComboBox.setEditable(true);
        roleComboBox.setMaxWidth(Double.MAX_VALUE);
        roleComboBox.getStyleClass().addAll("character-card-short-field", "character-card-role-combo", "world-editor-textarea");
        refreshRoleComboItems();
        roleComboBox.getEditor().textProperty().addListener((obs, oldVal, newVal) -> {
            if (suppressFieldRefresh || selectedEntry == null) {
                return;
            }
            updateSelectedField("Rolle", newVal);
        });
        VBox section = new VBox(6, fieldLabel, roleComboBox);
        section.getStyleClass().add("character-card-field");
        section.setPadding(new Insets(2, 2, 4, 2));
        return section;
    }

    private void refreshRoleComboItems() {
        if (roleComboBox == null) {
            return;
        }
        suppressFieldRefresh = true;
        try {
            String current = roleComboBox.getEditor().getText();
            LinkedHashSet<String> items = new LinkedHashSet<>(DEFAULT_ROLE_SUGGESTIONS);
            for (CharacterSheetDocument.CharacterEntry entry : characters) {
                String role = entry.field("Rolle");
                if (role != null && !role.isBlank()) {
                    items.add(role.trim());
                }
            }
            roleComboBox.getItems().setAll(items);
            if (current != null && !current.equals(roleComboBox.getEditor().getText())) {
                roleComboBox.getEditor().setText(current);
            }
        } finally {
            suppressFieldRefresh = false;
        }
    }

    private void setRoleFieldText(String value) {
        if (roleComboBox == null) {
            return;
        }
        suppressFieldRefresh = true;
        try {
            String role = value == null ? "" : value.trim();
            String current = roleComboBox.getEditor().getText();
            if (role.equals(current == null ? "" : current.trim())) {
                return;
            }
            if (!role.isBlank() && !roleComboBox.getItems().contains(role)) {
                roleComboBox.getItems().add(role);
            }
            if (!role.isBlank() && roleComboBox.getItems().contains(role)) {
                roleComboBox.setValue(role);
            } else {
                roleComboBox.setValue(null);
                roleComboBox.getEditor().setText(role);
            }
        } finally {
            suppressFieldRefresh = false;
        }
    }

    private static boolean isShortField(String label) {
        return SHORT_FIELDS.contains(label);
    }

    private TextField createShortFieldEditor(String label) {
        TextField field = new TextField();
        field.getStyleClass().addAll("character-card-short-field", "world-editor-textarea");
        field.textProperty().addListener((obs, oldVal, newVal) -> {
            if (suppressFieldRefresh || selectedEntry == null) {
                return;
            }
            updateSelectedField(label, newVal);
        });
        return field;
    }

    private MdTextArea createFieldEditor(String label) {
        MdTextArea area = new MdTextArea(MdTextAreaOptions.builder()
                .editable(true)
                .showToolbar(false)
                .enableUndoRedo(true)
                .enableBasicFormatting(true)
                .enableHideMarkupToggle(false)
                .hideMarkup(true)
                .showLineNumbers(false)
                .fontFamily("Consolas")
                .fontSize(FIELD_FONT_SIZE)
                .lineSpacing(1.45)
                .themeIndex(themeIndex)
                .build());
        area.getStyleClass().addAll("character-card-field-editor", "world-editor-textarea");
        VBox.setVgrow(area, Priority.NEVER);
        VBox.setVgrow(area.getEditor(), Priority.NEVER);
        area.setMinHeight(Region.USE_PREF_SIZE);
        area.setMaxHeight(Region.USE_PREF_SIZE);
        if (projectDirectory != null) {
            area.setImageDirectories(projectDirectory, projectDirectory);
        }
        area.textProperty().addListener((obs, oldVal, newVal) -> {
            if (suppressFieldRefresh || selectedEntry == null) {
                return;
            }
            updateSelectedField(label, newVal);
        });
        ManuskriptTextEditor editor = area.getEditor();
        editor.applyEmbeddedFieldTheme(themeIndex);
        // Höhe über embeddedField-Callback — niemals setOnTextChanged überschreiben
        // (sonst synct MdTextArea.textProperty nicht mehr → Speichern schreibt alte Daten).
        editor.setEmbeddedFieldMode(true, () -> {
            if (!suppressFieldRefresh) {
                applyFieldEditorHeight(area, label);
            }
        });
        bindFieldHeight(area);
        return area;
    }

    private void bindFieldHeight(MdTextArea area) {
        Runnable update = () -> {
            if (!suppressFieldRefresh) {
                applyFieldEditorHeight(area, null);
            }
        };
        area.widthProperty().addListener((obs, oldVal, newVal) -> Platform.runLater(update));
        Platform.runLater(update);
    }

    private void applyFieldEditorHeight(MdTextArea area, String label) {
        double outerWidth = resolveEditorOuterWidth(area);
        double contentWidth = Math.max(80, outerWidth - FIELD_CHROME_HORIZONTAL);
        ManuskriptTextEditor editor = area.getEditor();
        double measured = editor.measurePreferredHeightForEmbeddedField(contentWidth);
        double lineHeight = editor.estimatedLineHeight();
        double minContentHeight = lineHeight + 4;
        double contentHeight = Math.max(minContentHeight, measured);
        double outerHeight = contentHeight + FIELD_CHROME_VERTICAL;

        editor.setPrefHeight(contentHeight);
        editor.setMinHeight(contentHeight);
        editor.setMaxHeight(contentHeight);
        area.setPrefHeight(outerHeight);
        area.setMinHeight(Region.USE_PREF_SIZE);
        area.setMaxHeight(outerHeight);
        editor.layout();
        editor.clampEmbeddedFieldScrollTop();
        Platform.runLater(editor::requestLayout);
    }

    private static double resolveEditorOuterWidth(MdTextArea area) {
        double width = area.getWidth();
        if (width > FIELD_CHROME_HORIZONTAL + 1) {
            return width;
        }
        if (area.getParent() != null) {
            double parentWidth = area.getParent().getLayoutBounds().getWidth();
            if (parentWidth > FIELD_CHROME_HORIZONTAL + 1) {
                return parentWidth;
            }
        }
        return 520;
    }

    private void bindSearch() {
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            String query = newVal == null ? "" : newVal.trim().toLowerCase(Locale.GERMAN);
            filteredCharacters.setPredicate(entry -> {
                if (entry == null) {
                    return false;
                }
                if (query.isEmpty()) {
                    return true;
                }
                if (entry.name().toLowerCase(Locale.GERMAN).contains(query)) {
                    return true;
                }
                for (String value : entry.fields().values()) {
                    if (value != null && value.toLowerCase(Locale.GERMAN).contains(query)) {
                        return true;
                    }
                }
                return false;
            });
        });
    }

    private void loadDocument(CharacterSheetDocument.Document document) {
        loadDocument(document, true);
    }

    private void loadDocument(CharacterSheetDocument.Document document, boolean selectFirst) {
        preamble = document.preamble() == null ? "" : document.preamble();
        blocks.clear();
        blocks.addAll(document.blocks());
        refreshCharacterList();
        if (characters.isEmpty()) {
            showCharacter(null);
        } else if (selectFirst) {
            suppressSelectionEvents = true;
            try {
                characterList.getSelectionModel().selectFirst();
            } finally {
                suppressSelectionEvents = false;
            }
            showCharacter(characters.getFirst());
        }
    }

    private void refreshCharacterList() {
        characters.setAll(blocks.stream()
                .filter(CharacterSheetDocument.CharacterBlock.class::isInstance)
                .map(CharacterSheetDocument.CharacterBlock.class::cast)
                .map(CharacterSheetDocument.CharacterBlock::entry)
                .map(CharacterCardsEditor::copyEntry)
                .toList());
        refreshRoleComboItems();
    }

    private CharacterSheetDocument.Document buildDocument() {
        return new CharacterSheetDocument.Document(preamble, List.copyOf(blocks));
    }

    private void showCharacter(CharacterSheetDocument.CharacterEntry entry) {
        selectedEntry = entry;
        boolean hasSelection = entry != null;
        emptyLabel.setVisible(!hasSelection);
        emptyLabel.setManaged(!hasSelection);
        characterDetailPane.setVisible(hasSelection);
        characterDetailPane.setManaged(hasSelection);
        if (!hasSelection) {
            return;
        }

        suppressFieldRefresh = true;
        try {
            nameField.setText(entry.name());
            for (String label : CharacterSheetDocument.STANDARD_FIELD_LABELS) {
                String value = CharacterSheetDocument.sanitizeFieldText(entry.field(label));
                if ("Rolle".equals(label)) {
                    setRoleFieldText(value);
                } else if (isShortField(label)) {
                    TextField shortField = shortFieldEditors.get(label);
                    if (shortField != null) {
                        shortField.setText(value);
                    }
                } else {
                    MdTextArea editor = fieldEditors.get(label);
                    if (editor != null) {
                        editor.setText(value);
                        applyFieldEditorHeight(editor, label);
                    }
                }
            }
            refreshPortrait(entry.imageMarkdown());
            Platform.runLater(this::reflowAllFieldHeights);
        } finally {
            suppressFieldRefresh = false;
        }
    }

    private void onNameChanged(String newName) {
        if (suppressFieldRefresh || selectedEntry == null) {
            return;
        }
        String trimmed = newName == null ? "" : newName.trim();
        if (trimmed.isEmpty() || trimmed.equals(selectedEntry.name())) {
            return;
        }
        replaceSelectedEntry(selectedEntry.withName(trimmed));
    }

    private void updateSelectedField(String label, String value) {
        if (selectedEntry == null) {
            return;
        }
        String normalized = CharacterSheetDocument.sanitizeFieldText(value == null ? "" : value.trim());
        String current = selectedEntry.field(label);
        if (normalized.equals(current == null ? "" : current.trim())) {
            return;
        }
        replaceSelectedEntry(selectedEntry.withField(label, normalized));
        if ("Rolle".equals(label)) {
            refreshRoleComboItems();
        }
    }

    private void replaceSelectedEntry(CharacterSheetDocument.CharacterEntry updated) {
        int charIndex = characters.indexOf(selectedEntry);
        if (charIndex < 0 && selectedEntry != null) {
            // Fallback: nach Name suchen (selectedEntry kann nach Listen-Refresh verwaist sein)
            String wanted = selectedEntry.name();
            for (int i = 0; i < characters.size(); i++) {
                if (characters.get(i).name().equals(wanted)) {
                    charIndex = i;
                    break;
                }
            }
        }
        if (charIndex < 0) {
            logger.warn("Charakter-Änderung verworfen: keine Listenzuordnung für „{}“",
                    selectedEntry != null ? selectedEntry.name() : "?");
            return;
        }
        CharacterSheetDocument.CharacterEntry copied = copyEntry(updated);
        if (selectedEntry != null && entryContentEquals(selectedEntry, copied)) {
            return;
        }
        int blockIndex = blockIndexForCharacterIndex(charIndex);
        if (blockIndex >= 0) {
            blocks.set(blockIndex, new CharacterSheetDocument.CharacterBlock(copied));
        }
        selectedEntry = copied;
        characters.set(charIndex, copied);
        if (characterList.getSelectionModel().getSelectedIndex() != charIndex) {
            suppressSelectionEvents = true;
            try {
                characterList.getSelectionModel().select(charIndex);
            } finally {
                suppressSelectionEvents = false;
            }
        }
        notifyContentChanged();
    }

    private static boolean entryContentEquals(
            CharacterSheetDocument.CharacterEntry left,
            CharacterSheetDocument.CharacterEntry right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        if (!left.name().equals(right.name())) {
            return false;
        }
        String leftImage = left.imageMarkdown() == null ? "" : left.imageMarkdown().trim();
        String rightImage = right.imageMarkdown() == null ? "" : right.imageMarkdown().trim();
        if (!leftImage.equals(rightImage)) {
            return false;
        }
        for (String label : CharacterSheetDocument.STANDARD_FIELD_LABELS) {
            String a = left.field(label);
            String b = right.field(label);
            if (!(a == null ? "" : a.trim()).equals(b == null ? "" : b.trim())) {
                return false;
            }
        }
        return true;
    }

    private int blockIndexForCharacterIndex(int characterIndex) {
        int seen = 0;
        for (int i = 0; i < blocks.size(); i++) {
            if (blocks.get(i) instanceof CharacterSheetDocument.CharacterBlock) {
                if (seen == characterIndex) {
                    return i;
                }
                seen++;
            }
        }
        return -1;
    }

    private void notifyContentChanged() {
        if (suppressChangeEvents) {
            return;
        }
        if (externalTextListener != null) {
            externalTextListener.changed(new SimpleStringProperty(getText()), "", getText());
        }
    }

    private void deleteSelected() {
        CharacterSheetDocument.CharacterEntry entry = characterList.getSelectionModel().getSelectedItem();
        if (entry == null) {
            return;
        }
        int listIndex = characters.indexOf(entry);
        int blockIndex = blockIndexForCharacterIndex(listIndex);
        if (blockIndex >= 0) {
            blocks.remove(blockIndex);
        }
        refreshCharacterList();
        if (!characters.isEmpty()) {
            int next = Math.min(listIndex, characters.size() - 1);
            characterList.getSelectionModel().select(characters.get(next));
        } else {
            showCharacter(null);
        }
        notifyContentChanged();
    }

    private void requestAiForSelected() {
        if (aiRequest == null) {
            return;
        }
        CharacterSheetDocument.CharacterEntry entry = characterList.getSelectionModel().getSelectedItem();
        if (entry == null) {
            return;
        }
        CharacterCardAiOptions options = CharacterCardAiOptions.defaults();
        if (aiDialogHost != null) {
            Optional<CharacterCardAiOptions> chosen = aiDialogHost.promptAiOptions(entry.name());
            if (chosen.isEmpty()) {
                return;
            }
            options = chosen.get();
        }
        CharacterSheetDocument.CharacterEntry requestEntry = entry;
        CharacterCardAiOptions requestOptions = options;
        aiRequest.request(requestEntry, requestOptions, generated -> Platform.runLater(() -> {
            CharacterSheetDocument.CharacterEntry merged =
                    CharacterSheetDocument.mergeGenerated(requestEntry, generated, requestOptions);
            replaceSelectedEntry(merged);
            showCharacter(merged);
        }), error -> Platform.runLater(() -> {
            // Fehlerbehandlung erfolgt im Aufrufer (WorldEditorWindow)
        }));
    }

    private void chooseImage() {
        if (selectedEntry == null || projectDirectory == null) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Porträt wählen");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Bilddateien", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp"),
                new FileChooser.ExtensionFilter("Alle Dateien", "*.*"));
        String lastDirectory = imagePrefs.get(CanvasEditorPrefs.key("last_image_directory"), "");
        if (!lastDirectory.isBlank()) {
            File dir = new File(lastDirectory);
            if (dir.isDirectory()) {
                chooser.setInitialDirectory(dir);
            }
        } else if (projectDirectory.isDirectory()) {
            chooser.setInitialDirectory(projectDirectory);
        }
        File selected = chooser.showOpenDialog(owner);
        if (selected == null) {
            return;
        }
        try {
            File copied = MarkdownImageSupport.copyImageToProjectDirectory(selected, projectDirectory);
            String markdown = MarkdownImageSupport.buildMarkdown(copied.getName(), selectedEntry.name(), 100);
            replaceSelectedEntry(selectedEntry.withImageMarkdown(markdown));
            refreshPortrait(markdown);
            if (selected.getParentFile() != null) {
                imagePrefs.put(CanvasEditorPrefs.key("last_image_directory"),
                        selected.getParentFile().getAbsolutePath());
            }
        } catch (IOException ex) {
            replaceSelectedEntry(selectedEntry);
        }
    }

    private void clearImage() {
        if (selectedEntry == null) {
            return;
        }
        replaceSelectedEntry(selectedEntry.withImageMarkdown(""));
        refreshPortrait("");
    }

    private static int portraitPreviewKey(CharacterSheetDocument.CharacterEntry entry) {
        return ("portrait:" + entry.name()).hashCode();
    }

    private void refreshPortrait(String imageMarkdown) {
        portraitView.setImage(null);
        portraitPlaceholder.setVisible(true);
        if (imageMarkdown == null || imageMarkdown.isBlank() || projectDirectory == null) {
            return;
        }
        List<MarkdownImageSupport.ParsedBlock> blocks = MarkdownImageSupport.parseBlocks(imageMarkdown);
        if (blocks.isEmpty()) {
            return;
        }
        File imageFile = MarkdownImageSupport.resolveImageFile(
                blocks.get(0).imagePath(), projectDirectory, projectDirectory);
        Image image = MarkdownImageSupport.loadImage(imageFile);
        if (image != null) {
            portraitView.setImage(image);
            portraitPlaceholder.setVisible(false);
            portraitView.getParent().setCursor(Cursor.HAND);
        } else {
            if (portraitView.getParent() != null) {
                portraitView.getParent().setCursor(Cursor.DEFAULT);
            }
        }
    }

    private static CharacterSheetDocument.CharacterEntry copyEntry(CharacterSheetDocument.CharacterEntry entry) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        for (Map.Entry<String, String> field : entry.fields().entrySet()) {
            fields.put(field.getKey(), field.getValue());
        }
        for (String label : CharacterSheetDocument.STANDARD_FIELD_LABELS) {
            fields.putIfAbsent(label, "");
        }
        return new CharacterSheetDocument.CharacterEntry(entry.name(), entry.imageMarkdown(), fields);
    }
}
