package com.manuskript.novelwizard;

import com.manuskript.CustomAlert;
import com.manuskript.CustomStage;
import com.manuskript.EditorDialogThemes;
import com.manuskript.HelpSystem;
import com.manuskript.MdTextArea;
import com.manuskript.MdTextAreaOptions;
import com.manuskript.PreferencesManager;
import com.manuskript.ResourceManager;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Window;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.prefs.Preferences;

public class NovelCreationWizardWindow {
    private static final Logger logger = LoggerFactory.getLogger(NovelCreationWizardWindow.class);

    /** Abstand zwischen UI-Elementen im Roman-Assistenten */
    private static final double GAP = 12;
    /** Horizontaler Abzug fuer Frage-/Hinweis-Umbruch (Panel-Padding + Rand) */
    private static final double QUESTION_WRAP_INSET = 40;
    /** Innen-Padding der Frage-Box (16 px links + 16 px rechts in CSS) */
    private static final double QUESTION_BOX_HORIZONTAL_PADDING = 32;
    /** Abstand vom Fensterrand (Client-Bereich unter der Titelleiste) */
    private static final double WINDOW_INSET = 22;
    /** Abstand zwischen Aktionsbuttons in einer Zeile */
    private static final double BUTTON_GAP = 8;

    private static final Preferences UI_PREFS = Preferences.userNodeForPackage(NovelCreationWizardWindow.class);
    private static final String WINDOW_PREFS_PREFIX = "novel_wizard_window";
    private static final String PREF_MAIN_SPLIT = "novel_wizard_main_split_divider";
    private static final String PREF_CONTENT_SPLIT = "novel_wizard_content_split_divider";
    private static final double DEFAULT_MAIN_SPLIT = 0.58;
    private static final double DEFAULT_CONTENT_SPLIT = 0.48;

    private final Window owner;
    private final Path projectDirectory;
    private final Runnable onProjectChanged;
    private final int themeIndex;
    private final WorldEditorMapper worldEditorMapper;
    private final NovelWizardSessionStore sessionStore;
    private final NovelWizardAiService aiService;

    private CustomStage stage;
    private NovelWizardSession session;
    private TextArea chatArea;
    private Label projectTitleLabel;
    private Label phaseHeadlineLabel;
    private Label progressCaptionLabel;
    private Label progressFractionLabel;
    private ProgressBar phaseProgressBar;
    private HBox phaseStepsBar;
    private ScrollPane phaseStepsScroll;
    private ComboBox<NovelWizardPhase> phaseComboBox;
    private Label statusLabel;
    private Text questionText;
    private VBox questionBox;
    private Text hintText;
    private VBox hintBox;
    private VBox optionsBox;
    private ScrollPane optionsScroll;
    private TextArea customAnswerArea;
    private TabPane previewTabs;
    private final Map<String, Tab> previewTabByFile = new LinkedHashMap<>();
    private final Map<String, ScrollPane> previewScrollByFile = new LinkedHashMap<>();
    private final Map<String, MdTextArea> previewEditorByFile = new HashMap<>();
    private final Map<String, String> previewContentByFile = new LinkedHashMap<>();
    private boolean previewTabsInitialized;
    private Button nextQuestionButton;
    private Button finishPhaseButton;
    private Button backQuestionButton;
    private SplitPane mainSplitPane;
    private SplitPane contentSplitPane;

    public NovelCreationWizardWindow(Window owner, Path projectDirectory, Runnable onProjectChanged, int themeIndex) {
        this.owner = owner;
        this.projectDirectory = projectDirectory;
        this.onProjectChanged = onProjectChanged;
        this.themeIndex = themeIndex;
        this.worldEditorMapper = new WorldEditorMapper(projectDirectory);
        this.sessionStore = new NovelWizardSessionStore(projectDirectory);
        this.aiService = new NovelWizardAiService(Path.of(ResourceManager.getConfigDirectory(), "novel-wizard-phases.json"));
    }

    public void show() {
        if (!prepareSession()) {
            return;
        }
        session.normalizeChatPhases();
        worldEditorMapper.ensureWorldFiles(projectDirectory.getFileName().toString());
        createUi();
        resyncPersistedPhasesFromSession();
        renderSession();
        if (session.getPendingTurn() != null) {
            renderTurn(session.getPendingTurn());
        } else {
            enterSelectedPhase();
        }
        stage.show();
    }

    private boolean isWizardComplete() {
        return session.getPhaseStatus().get(NovelWizardPhase.CHAPTERS) == NovelWizardPhaseStatus.COMPLETED;
    }

    /** Phase anzeigen: offene Frage, abgeschlossene Phase (nur lesen) oder KI starten. */
    private void enterSelectedPhase() {
        NovelWizardPhase phase = session.getCurrentPhase();
        refreshCompletionUi();
        restorePendingTurnForPhase(phase);
        selectPreviewTabForPhase(phase);
        if (session.getPendingTurn() != null) {
            renderTurn(session.getPendingTurn());
            statusLabel.setText("Phase " + phaseNumber(phase) + "/" + totalPhaseCount() + ": " + phase.getTitle());
            return;
        }
        NovelWizardPhaseStatus status = session.getPhaseStatus().get(phase);
        if (status == NovelWizardPhaseStatus.COMPLETED) {
            renderPhaseBrowsingState(phase);
            return;
        }
        requestNextTurn();
    }

    private void renderWizardCompleteState(int docxCount) {
        clearPendingInteraction();
        questionText.setText("Roman-Assistent abgeschlossen.");
        String hint = docxCount >= 0
                ? docxCount + " Kapitel-DOCX erzeugt. Oben eine Phase wählen, um Inhalte anzusehen oder zu überarbeiten."
                : "Alle Phasen erledigt. Oben eine Phase wählen, um Inhalte anzusehen oder zu überarbeiten.";
        hintText.setText(hint);
        hintBox.setManaged(true);
        hintBox.setVisible(true);
        clearOptions();
        addPhaseBrowseOptions(session.getCurrentPhase());
        refreshCompletionUi();
    }

    private void renderPhaseBrowsingState(NovelWizardPhase phase) {
        clearPendingInteraction();
        if (phase == NovelWizardPhase.CHAPTERS && isWizardComplete()) {
            questionText.setText("Phase 7: Kapitel (abgeschlossen)");
            hintText.setText("Vorschau rechts: Tab „Kapitel“. DOCX erzeugen oder Entwurf erneut anfordern.");
        } else {
            questionText.setText("Phase „" + phase.getTitle() + "“ (abgeschlossen)");
            hintText.setText("Inhalt in der Vorschau rechts. Unten KI erneut starten"
                    + (phase == NovelWizardPhase.CHAPTERS ? " oder DOCX aus chapter.txt erzeugen." : "."));
        }
        hintBox.setManaged(true);
        hintBox.setVisible(true);
        clearOptions();
        addPhaseBrowseOptions(phase);
        refreshCompletionUi();
    }

    private void addPhaseBrowseOptions(NovelWizardPhase phase) {
        if (isContentPhase(phase)) {
            addOption("Entwurf erneut anfordern", () -> startRevisitTurn(phase));
            if (phase == NovelWizardPhase.CHAPTERS) {
                addOption("DOCX aus chapter.txt erzeugen", this::createDocxFromChapterFile);
            }
        } else {
            addOption("Nächste KI-Frage", () -> startRevisitTurn(phase));
        }
    }

    private static boolean isContentPhase(NovelWizardPhase phase) {
        return phase == NovelWizardPhase.SYNOPSIS
                || phase == NovelWizardPhase.STRUCTURE
                || phase == NovelWizardPhase.CHAPTERS;
    }

    private void startRevisitTurn(NovelWizardPhase phase) {
        session.getPhaseStatus().put(phase, NovelWizardPhaseStatus.REVISITING);
        session.setCurrentPhase(phase);
        sessionStore.save(session);
        requestNextTurn(true);
    }

    private void refreshCompletionUi() {
        if (nextQuestionButton != null) {
            nextQuestionButton.setDisable(false);
        }
        if (finishPhaseButton != null) {
            finishPhaseButton.setDisable(false);
        }
    }

    private boolean prepareSession() {
        Optional<NovelWizardSession> existing = sessionStore.load();
        if (existing.isEmpty()) {
            session = NovelWizardSession.create("NEW");
            sessionStore.save(session);
            return true;
        }

        ButtonType resume = new ButtonType("Fortsetzen");
        ButtonType restart = new ButtonType("Neu starten (Backup)");
        ButtonType cancel = new ButtonType("Abbrechen", ButtonBar.ButtonData.CANCEL_CLOSE);
        CustomAlert alert = new CustomAlert(CustomAlert.AlertType.CONFIRMATION);
        alert.setTitle("Roman-Assistent fortsetzen");
        alert.setHeaderText("Wie moechtest du weitermachen?");
        alert.setContentText("Es gibt bereits eine Roman-Assistent-Session fuer dieses Projekt.\n\n"
                + "Zuletzt gespeichert: " + existing.get().getUpdatedAt());
        alert.getButtonTypes().setAll(resume, restart, cancel);
        alert.applyTheme(themeIndex);
        Optional<ButtonType> result = alert.showAndWait(owner);
        if (result.isEmpty() || result.get() == cancel) {
            return false;
        }
        if (result.get() == restart) {
            sessionStore.archiveCurrentSession();
            session = NovelWizardSession.create("NEW");
            sessionStore.save(session);
        } else {
            session = existing.get();
        }
        return true;
    }

    private void createUi() {
        stage = new CustomStage();
        stage.setCustomTitle("Roman-Assistent");
        stage.setWidth(1000);
        stage.setHeight(820);
        stage.setMinWidth(780);
        stage.setMinHeight(680);
        stage.setTitleBarTheme(themeIndex);
        if (owner != null) {
            stage.initOwner(owner);
        }

        BorderPane root = new BorderPane();
        root.getStyleClass().add("novel-wizard-root");
        EditorDialogThemes.applyToNode(root, themeIndex);

        String projectName = projectDirectory.getFileName().toString();
        projectTitleLabel = new Label("Roman-Assistent · " + projectName);
        projectTitleLabel.getStyleClass().add("novel-wizard-project-title");
        projectTitleLabel.setMaxWidth(Double.MAX_VALUE);

        phaseHeadlineLabel = new Label("Brainstorm");
        phaseHeadlineLabel.getStyleClass().add("novel-wizard-phase-headline");
        phaseHeadlineLabel.setMaxWidth(Double.MAX_VALUE);
        phaseHeadlineLabel.setFont(Font.font(null, FontWeight.BOLD, 24));

        progressCaptionLabel = new Label("Projektfortschritt");
        progressCaptionLabel.getStyleClass().add("novel-wizard-progress-caption");

        phaseProgressBar = new ProgressBar(0);
        phaseProgressBar.setMaxWidth(Double.MAX_VALUE);
        phaseProgressBar.setPrefHeight(14);
        phaseProgressBar.setMinHeight(14);
        phaseProgressBar.getStyleClass().add("novel-wizard-phase-progress");
        HBox.setHgrow(phaseProgressBar, Priority.ALWAYS);

        progressFractionLabel = new Label("0 von 7 (0 %)");
        progressFractionLabel.getStyleClass().add("novel-wizard-progress-fraction");
        progressFractionLabel.setAlignment(Pos.CENTER_RIGHT);

        HBox progressRow = new HBox(10, progressCaptionLabel, phaseProgressBar, progressFractionLabel);
        progressRow.setAlignment(Pos.CENTER_LEFT);
        progressRow.getStyleClass().add("novel-wizard-progress-row");
        progressRow.setMaxWidth(Double.MAX_VALUE);

        phaseStepsBar = new HBox(8);
        phaseStepsBar.setAlignment(Pos.CENTER_LEFT);
        phaseStepsBar.getStyleClass().add("novel-wizard-phase-track");
        phaseStepsScroll = new ScrollPane(phaseStepsBar);
        phaseStepsScroll.setFitToHeight(false);
        phaseStepsScroll.setFitToWidth(false);
        phaseStepsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        phaseStepsScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        phaseStepsScroll.setPrefHeight(42);
        phaseStepsScroll.setMinHeight(42);
        phaseStepsScroll.setMaxHeight(42);
        phaseStepsScroll.getStyleClass().add("novel-wizard-phase-steps-scroll");

        statusLabel = new Label();
        statusLabel.getStyleClass().addAll("status-label", "novel-wizard-status");
        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(Double.MAX_VALUE);

        phaseComboBox = new ComboBox<>();
        for (NovelWizardPhase phase : NovelWizardPhase.values()) {
            if (phase != NovelWizardPhase.BOOTSTRAP) {
                phaseComboBox.getItems().add(phase);
            }
        }
        phaseComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(NovelWizardPhase phase) {
                return phase == null ? "" : phaseNumber(phase) + ". " + phase.getTitle();
            }

            @Override
            public NovelWizardPhase fromString(String string) {
                return null;
            }
        });
        phaseComboBox.setPromptText("Phase wählen…");
        phaseComboBox.setPrefWidth(200);
        phaseComboBox.setMaxWidth(260);
        phaseComboBox.setOnAction(e -> jumpToPhase(phaseComboBox.getValue()));

        Label jumpLabel = new Label("Springen zu:");
        jumpLabel.getStyleClass().add("novel-wizard-jump-label");
        HBox jumpRow = new HBox(8, jumpLabel, phaseComboBox);
        jumpRow.setAlignment(Pos.CENTER_RIGHT);

        Region footerSpacer = new Region();
        HBox.setHgrow(footerSpacer, Priority.ALWAYS);
        HBox footerRow = new HBox(12, statusLabel, footerSpacer, jumpRow);
        footerRow.setAlignment(Pos.CENTER_LEFT);

        Button wizardHelpButton = HelpSystem.createHelpButton(
                "Hilfe zum Roman-Assistenten", "novel_wizard.html", "Hilfe - Roman-Assistent");
        HBox titleRow = new HBox(8, projectTitleLabel, wizardHelpButton);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(projectTitleLabel, Priority.ALWAYS);
        VBox titleBlock = new VBox(2, titleRow, phaseHeadlineLabel);
        VBox headerMain = new VBox(12, titleBlock, progressRow, phaseStepsScroll);
        headerMain.getStyleClass().add("novel-wizard-header-main");

        VBox headerBox = new VBox(10, headerMain, footerRow);
        headerBox.getStyleClass().add("novel-wizard-header");

        chatArea = new TextArea();
        chatArea.setEditable(false);
        chatArea.setWrapText(true);
        chatArea.getStyleClass().add("novel-wizard-chat");

        previewTabs = new TabPane();
        previewTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        previewTabs.getStyleClass().add("novel-wizard-preview-tabs");
        initializePreviewTabs();

        contentSplitPane = new SplitPane(chatArea, previewTabs);
        contentSplitPane.setOrientation(Orientation.HORIZONTAL);
        contentSplitPane.getStyleClass().add("novel-wizard-content-split");
        SplitPane.setResizableWithParent(chatArea, Boolean.TRUE);
        SplitPane.setResizableWithParent(previewTabs, Boolean.TRUE);

        questionText = new Text();
        questionText.getStyleClass().add("novel-wizard-question");
        questionText.setFont(Font.font(null, FontWeight.BOLD, 16));
        questionBox = new VBox(questionText);
        questionBox.getStyleClass().add("novel-wizard-question-box");
        questionBox.setMaxWidth(Double.MAX_VALUE);
        questionBox.setMinWidth(0);

        hintText = new Text();
        hintText.getStyleClass().add("novel-wizard-hint");
        hintBox = new VBox(hintText);
        hintBox.getStyleClass().add("novel-wizard-hint-box");
        hintBox.setMaxWidth(Double.MAX_VALUE);
        hintBox.setMinWidth(0);
        hintBox.setManaged(false);
        hintBox.setVisible(false);

        optionsBox = new VBox(GAP);
        optionsBox.getStyleClass().add("novel-wizard-options-box");
        optionsBox.setFillWidth(true);
        optionsBox.setPadding(new Insets(4, 2, 4, 2));

        optionsScroll = new ScrollPane(optionsBox);
        optionsScroll.setFitToWidth(true);
        optionsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        optionsScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        optionsScroll.getStyleClass().add("novel-wizard-options-scroll");
        optionsScroll.viewportBoundsProperty().addListener((obs, oldBounds, bounds) -> {
            double width = bounds.getWidth();
            if (width > 0) {
                optionsBox.setMinWidth(width);
                optionsBox.setPrefWidth(width);
            }
        });

        Label answerLabel = new Label("Eigene Antwort");
        answerLabel.getStyleClass().add("novel-wizard-section-label");

        customAnswerArea = new TextArea();
        customAnswerArea.setPromptText("Eigene Antwort, Überarbeitung oder Korrektur an die KI…");
        customAnswerArea.setWrapText(true);
        customAnswerArea.setPrefRowCount(3);
        customAnswerArea.setMinHeight(72);
        customAnswerArea.setMaxHeight(140);
        customAnswerArea.getStyleClass().add("novel-wizard-answer-area");
        VBox.setVgrow(customAnswerArea, Priority.NEVER);

        Button sendCustomButton = actionButton("Antwort senden", "novel-wizard-action-primary");
        sendCustomButton.setOnAction(e -> submitCustomAnswer());

        Button correctionButton = actionButton("KI-Korrektur", "novel-wizard-action-secondary");
        correctionButton.setOnAction(e -> submitCorrection());
        Tooltip.install(correctionButton, new Tooltip(
                "Verbindliche Korrektur an die KI (z. B. falsche Rolle einer Figur). "
                        + "Text zuerst ins Feld darunter schreiben."));

        HBox answerButtonRow = new HBox(BUTTON_GAP, sendCustomButton, correctionButton);
        answerButtonRow.setAlignment(Pos.CENTER);

        backQuestionButton = actionButton("Eine Frage zurück", "novel-wizard-action-secondary");
        backQuestionButton.setOnAction(e -> goBackOneQuestion());

        nextQuestionButton = actionButton("Nächste KI-Frage", "novel-wizard-action-secondary");
        nextQuestionButton.setOnAction(e -> requestNextTurn(true));

        finishPhaseButton = actionButton("Phase abschließen", "novel-wizard-action-primary");
        finishPhaseButton.setOnAction(e -> finishCurrentPhase(null));

        Button pauseButton = actionButton("Pausieren", "novel-wizard-action-secondary");
        pauseButton.setOnAction(e -> pause());

        HBox actionsRow = actionButtonRow(
                backQuestionButton,
                nextQuestionButton,
                pauseButton,
                finishPhaseButton);

        VBox interactionPanel = new VBox(GAP,
                questionBox,
                hintBox,
                optionsScroll,
                answerLabel,
                customAnswerArea,
                answerButtonRow,
                actionsRow);
        interactionPanel.getStyleClass().add("novel-wizard-interaction");
        interactionPanel.setFillWidth(true);
        interactionPanel.setMinWidth(0);
        interactionPanel.setMinHeight(300);
        interactionPanel.setPrefHeight(380);
        questionBox.prefWidthProperty().bind(interactionPanel.widthProperty());
        hintBox.prefWidthProperty().bind(interactionPanel.widthProperty());
        bindWrappingText(questionText, interactionPanel, QUESTION_BOX_HORIZONTAL_PADDING);
        bindWrappingText(hintText, interactionPanel, 0);
        VBox.setVgrow(optionsScroll, Priority.ALWAYS);
        VBox.setMargin(answerLabel, new Insets(6, 0, 2, 0));
        VBox.setMargin(actionsRow, new Insets(4, 0, 0, 0));

        mainSplitPane = new SplitPane(contentSplitPane, interactionPanel);
        mainSplitPane.setOrientation(Orientation.VERTICAL);
        mainSplitPane.getStyleClass().add("novel-wizard-main-split");
        SplitPane.setResizableWithParent(contentSplitPane, Boolean.TRUE);
        SplitPane.setResizableWithParent(interactionPanel, Boolean.TRUE);

        VBox shell = new VBox(GAP, headerBox, mainSplitPane);
        shell.setPadding(new Insets(WINDOW_INSET));
        shell.getStyleClass().add("novel-wizard-shell");
        VBox.setVgrow(mainSplitPane, Priority.ALWAYS);
        root.setCenter(shell);

        Scene scene = new Scene(root);
        String cssPath = ResourceManager.getCssResource("css/manuskript.css");
        if (cssPath != null) {
            scene.getStylesheets().add(cssPath);
        }
        java.net.URL wizardCss = NovelCreationWizardWindow.class.getResource("/css/novel-wizard.css");
        if (wizardCss != null) {
            scene.getStylesheets().add(wizardCss.toExternalForm());
        }
        stage.setSceneWithTitleBar(scene);
        stage.setFullTheme(themeIndex);
        loadAndApplyUiLayoutPreferences();
        bindUiLayoutPersistence();
        stage.setOnCloseRequest(e -> {
            saveUiLayoutPreferences();
            sessionStore.save(session);
        });
    }

    private void loadAndApplyUiLayoutPreferences() {
        Rectangle2D bounds = PreferencesManager.MultiMonitorValidator.loadAndValidateWindowProperties(
                UI_PREFS, WINDOW_PREFS_PREFIX, 1000, 820);
        PreferencesManager.MultiMonitorValidator.applyWindowProperties(stage, bounds);

        double mainPos = clampDivider(UI_PREFS.getDouble(PREF_MAIN_SPLIT, DEFAULT_MAIN_SPLIT));
        double contentPos = clampDivider(UI_PREFS.getDouble(PREF_CONTENT_SPLIT, DEFAULT_CONTENT_SPLIT));
        mainSplitPane.setDividerPositions(mainPos);
        contentSplitPane.setDividerPositions(contentPos);
        Platform.runLater(() -> {
            mainSplitPane.setDividerPositions(clampDivider(UI_PREFS.getDouble(PREF_MAIN_SPLIT, DEFAULT_MAIN_SPLIT)));
            contentSplitPane.setDividerPositions(clampDivider(UI_PREFS.getDouble(PREF_CONTENT_SPLIT, DEFAULT_CONTENT_SPLIT)));
        });
    }

    private void bindUiLayoutPersistence() {
        stage.xProperty().addListener((obs, oldVal, newVal) ->
                UI_PREFS.putDouble(WINDOW_PREFS_PREFIX + "_x", newVal.doubleValue()));
        stage.yProperty().addListener((obs, oldVal, newVal) ->
                UI_PREFS.putDouble(WINDOW_PREFS_PREFIX + "_y", newVal.doubleValue()));
        stage.widthProperty().addListener((obs, oldVal, newVal) ->
                UI_PREFS.putDouble(WINDOW_PREFS_PREFIX + "_width", newVal.doubleValue()));
        stage.heightProperty().addListener((obs, oldVal, newVal) ->
                UI_PREFS.putDouble(WINDOW_PREFS_PREFIX + "_height", newVal.doubleValue()));

        bindSplitDividerPersistence(mainSplitPane, PREF_MAIN_SPLIT);
        bindSplitDividerPersistence(contentSplitPane, PREF_CONTENT_SPLIT);
    }

    private void bindSplitDividerPersistence(SplitPane splitPane, String prefKey) {
        if (splitPane.getDividers().isEmpty()) {
            return;
        }
        splitPane.getDividers().getFirst().positionProperty().addListener((obs, oldVal, newVal) ->
                UI_PREFS.putDouble(prefKey, clampDivider(newVal.doubleValue())));
    }

    private void saveUiLayoutPreferences() {
        if (stage == null) {
            return;
        }
        UI_PREFS.putDouble(WINDOW_PREFS_PREFIX + "_x", stage.getX());
        UI_PREFS.putDouble(WINDOW_PREFS_PREFIX + "_y", stage.getY());
        UI_PREFS.putDouble(WINDOW_PREFS_PREFIX + "_width", stage.getWidth());
        UI_PREFS.putDouble(WINDOW_PREFS_PREFIX + "_height", stage.getHeight());
        saveSplitDivider(mainSplitPane, PREF_MAIN_SPLIT, DEFAULT_MAIN_SPLIT);
        saveSplitDivider(contentSplitPane, PREF_CONTENT_SPLIT, DEFAULT_CONTENT_SPLIT);
    }

    private void saveSplitDivider(SplitPane splitPane, String prefKey, double defaultValue) {
        if (splitPane == null || splitPane.getDividers().isEmpty()) {
            UI_PREFS.putDouble(prefKey, defaultValue);
            return;
        }
        UI_PREFS.putDouble(prefKey, clampDivider(splitPane.getDividers().getFirst().getPosition()));
    }

    private static double clampDivider(double position) {
        return Math.max(0.12, Math.min(0.88, position));
    }

    private Button actionButton(String text, String styleClass) {
        Button button = new Button(text);
        button.getStyleClass().addAll("novel-wizard-action-button", styleClass);
        return button;
    }

    /** Text-Umbruch an Panel-Breite koppeln (Label+wrapText bricht in VBox/SplitPane oft nicht um). */
    private static void bindWrappingText(Text text, Region widthSource, double extraInset) {
        text.wrappingWidthProperty().bind(Bindings.createDoubleBinding(() -> {
            double w = widthSource.getWidth();
            if (w <= 0) {
                return 400.0;
            }
            return Math.max(100.0, w - QUESTION_WRAP_INSET - extraInset);
        }, widthSource.widthProperty()));
    }

    private HBox actionButtonRow(Button... buttons) {
        HBox row = new HBox(BUTTON_GAP, buttons);
        row.setAlignment(Pos.CENTER);
        row.getStyleClass().add("novel-wizard-actions-bar");
        row.setMaxWidth(Double.MAX_VALUE);
        row.setPadding(new Insets(6, 0, 0, 0));
        return row;
    }

    private Button createOptionButton(String text, Runnable onSelect) {
        Button button = new Button(text);
        button.getStyleClass().add("novel-wizard-option-button");
        button.setWrapText(true);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setOnAction(e -> onSelect.run());
        button.maxWidthProperty().bind(optionsBox.widthProperty().subtract(2));
        button.prefWidthProperty().bind(optionsBox.widthProperty().subtract(2));

        MenuItem copyToAnswerItem = new MenuItem("In „Eigene Antwort“ übernehmen");
        copyToAnswerItem.setOnAction(e -> copyOptionToCustomAnswer(text));
        ContextMenu contextMenu = new ContextMenu(copyToAnswerItem);
        EditorDialogThemes.styleContextMenu(contextMenu, themeIndex);
        button.setContextMenu(contextMenu);
        button.setOnContextMenuRequested(e -> {
            copyOptionToCustomAnswer(text);
            customAnswerArea.requestFocus();
        });

        return button;
    }

    private void copyOptionToCustomAnswer(String text) {
        if (customAnswerArea == null || text == null) {
            return;
        }
        customAnswerArea.setText(text);
        customAnswerArea.positionCaret(customAnswerArea.getText().length());
    }

    private void clearOptions() {
        optionsBox.getChildren().clear();
    }

    /** Frage, Hinweis und Optionen leeren, solange ein neuer KI-Lauf laeuft. */
    private void clearPendingInteraction() {
        if (questionText != null) {
            questionText.setText("KI denkt …");
        }
        if (hintText != null) {
            hintText.setText("");
            hintBox.setManaged(false);
            hintBox.setVisible(false);
        }
        clearOptions();
        session.setPendingTurn(null);
    }

    private void addOption(String text, Runnable onSelect) {
        optionsBox.getChildren().add(createOptionButton(text, onSelect));
    }

    private void renderSession() {
        updatePhaseLabel();
        StringBuilder sb = new StringBuilder();
        NovelWizardPhase lastPhase = null;
        for (NovelWizardSession.ChatEntry entry : session.getChatHistory()) {
            if (entry.phase != null && entry.phase != lastPhase) {
                sb.append("=== Phase ")
                        .append(phaseNumber(entry.phase))
                        .append("/")
                        .append(totalPhaseCount())
                        .append(": ")
                        .append(entry.phase.getTitle())
                        .append(" ===\n\n");
                lastPhase = entry.phase;
            }
            if ("user".equals(entry.role)) {
                String choice = entry.choice == null ? "" : entry.choice;
                if (choice.startsWith(NovelWizardSession.CORRECTION_PREFIX)) {
                    sb.append("Korrektur: ")
                            .append(choice.substring(NovelWizardSession.CORRECTION_PREFIX.length()).trim())
                            .append("\n\n");
                } else {
                    sb.append("Du: ").append(choice).append("\n\n");
                }
            } else if (entry.parsed != null) {
                sb.append("Assistent: ");
                if (!entry.parsed.getQuestion().isBlank()) {
                    sb.append(entry.parsed.getQuestion());
                } else if (!entry.parsed.getContent().isBlank()) {
                    sb.append(entry.parsed.getContent());
                } else {
                    sb.append(entry.raw);
                }
                sb.append("\n\n");
            }
        }
        chatArea.setText(sb.toString());
        chatArea.positionCaret(chatArea.getText().length());
        refreshPreview();
    }

    private void requestNextTurn() {
        requestNextTurn(false);
    }

    private void requestNextTurn(boolean discardUnansweredPending) {
        if (discardUnansweredPending) {
            discardLastUnansweredAssistantTurn();
        }
        NovelWizardPhase phase = session.getCurrentPhase();
        if (session.getPhaseStatus().get(phase) == NovelWizardPhaseStatus.COMPLETED) {
            startRevisitTurn(phase);
            return;
        }
        clearPendingInteraction();
        setBusy(true, "KI denkt...");
        aiService.nextTurn(session, worldEditorMapper.readExistingContext())
                .whenComplete((turn, ex) -> Platform.runLater(() -> {
                    if (ex != null) {
                        logger.warn("Roman-Assistent KI-Aufruf fehlgeschlagen", ex);
                        NovelWizardTurn fallback = fallbackTurn(ex);
                        session.addAssistantTurn(session.getCurrentPhase(), ex.getMessage(), fallback);
                        renderTurn(fallback);
                    } else {
                        session.addAssistantTurn(session.getCurrentPhase(), "", turn);
                        renderTurn(turn);
                    }
                    sessionStore.save(session);
                    renderSession();
                    setBusy(false, "Gespeichert");
                }));
    }

    private NovelWizardTurn fallbackTurn(Throwable ex) {
        NovelWizardTurn turn = new NovelWizardTurn();
        turn.setQuestion("Die KI-Antwort konnte nicht geladen werden. Wie moechtest du fortfahren?");
        turn.setHint(ex.getMessage());
        turn.setOptions(java.util.List.of("Nochmal versuchen", "Phase spaeter fortsetzen"));
        return turn;
    }

    private void renderTurn(NovelWizardTurn turn) {
        NovelWizardPhase phase = session.getCurrentPhase();
        boolean contentPhase = isContentPhase(phase);
        if (contentPhase && !turn.getContent().isBlank()) {
            questionText.setText("Entwurf für „" + phase.getTitle() + "“ – prüfen, übernehmen oder überarbeiten.");
            String summary = turn.getSummary();
            String hint = summary == null || summary.isBlank()
                    ? "Der vollständige Text steht im Chat (rechts: Vorschau-Tab „" + previewTabHint(phase) + "“)."
                    : summary;
            hintText.setText(hint);
            hintBox.setManaged(true);
            hintBox.setVisible(true);
        } else if (contentPhase) {
            questionText.setText("„" + phase.getTitle() + "“ – die KI soll einen Entwurf schreiben, nicht nachfragen.");
            String hint = turn.getHint();
            hintText.setText(hint == null || hint.isBlank()
                    ? "Wenn du Plot-Optionen siehst, ignoriere sie – nutze „Entwurf erneut anfordern“."
                    : hint);
            hintBox.setManaged(true);
            hintBox.setVisible(true);
        } else {
            questionText.setText(turn.getQuestion().isBlank() ? " " : turn.getQuestion());
            String hint = turn.getHint();
            hintText.setText(hint == null || hint.isBlank() ? "" : hint);
            hintBox.setManaged(hint != null && !hint.isBlank());
            hintBox.setVisible(hint != null && !hint.isBlank());
        }
        clearOptions();
        if (!turn.getContent().isBlank()) {
            addOption("Vorschlag übernehmen", () -> finishCurrentPhase(turn.getContent()));
            addOption("Überarbeiten", () -> customAnswerArea.requestFocus());
            if (contentPhase) {
                addOption("Entwurf erneut anfordern", () -> requestNextTurn(true));
                if (phase == NovelWizardPhase.CHAPTERS) {
                    addOption("DOCX aus chapter.txt erzeugen", this::createDocxFromChapterFile);
                }
            }
        } else if (contentPhase) {
            addOption("Entwurf erneut anfordern", () -> requestNextTurn(true));
            addOption("DOCX aus chapter.txt erzeugen", this::createDocxFromChapterFile);
            addOption("Überarbeiten", () -> customAnswerArea.requestFocus());
        } else {
            for (String option : turn.getOptions()) {
                if (NovelWizardTurn.isRedundantFreeTextOption(option)) {
                    continue;
                }
                addOption(option, () -> submitAnswer(option, false));
            }
        }
        Platform.runLater(() -> {
            if (optionsScroll != null) {
                optionsScroll.setVvalue(0);
            }
        });
    }

    private void submitCustomAnswer() {
        String answer = customAnswerArea.getText().trim();
        if (answer.isEmpty()) {
            return;
        }
        submitAnswer(answer, true);
        customAnswerArea.clear();
    }

    /**
     * Verbindliche Korrektur: KI soll fruehere Annahmen verwerfen und die naechste Frage anpassen.
     */
    private void submitCorrection() {
        String text = customAnswerArea.getText().trim();
        if (text.isEmpty()) {
            statusLabel.setText("Korrektur bitte ins Feld „Eigene Antwort“ schreiben, dann „KI-Korrektur“.");
            customAnswerArea.requestFocus();
            return;
        }
        NovelWizardPhase phase = session.getCurrentPhase();
        String correction = NovelWizardSession.CORRECTION_PREFIX + text;
        session.addUserTurn(phase, correction, true);
        updateProjectSummary("Korrektur: " + text);
        persistCurrentPhaseDraft(phase);
        session.setPendingTurn(null);
        sessionStore.save(session);
        renderSession();
        statusLabel.setText("Korrektur gespeichert – KI passt die naechste Frage an …");
        customAnswerArea.clear();
        requestNextTurn();
    }

    private void submitAnswer(String answer, boolean custom) {
        NovelWizardPhase phase = session.getCurrentPhase();
        session.addUserTurn(phase, answer, custom);
        session.getCollected().put(phase.name().toLowerCase() + "." + session.getChatHistory().size(), answer);
        updateProjectSummary(answer);
        persistCurrentPhaseDraft(phase);
        sessionStore.save(session);
        renderSession();
        if ("Nochmal versuchen".equalsIgnoreCase(answer)) {
            requestNextTurn();
        } else if (!"Phase spaeter fortsetzen".equalsIgnoreCase(answer)) {
            requestNextTurn();
        }
    }

    private void createDocxFromChapterFile() {
        try {
            Path chapterFile = projectDirectory.resolve(WorldEditorMapper.CHAPTER_FILE);
            if (!java.nio.file.Files.exists(chapterFile)) {
                showDocxResultDialog(false, "Keine chapter.txt",
                        "Im Projektordner wurde keine Datei chapter.txt gefunden:\n" + chapterFile);
                setBusy(false, "chapter.txt fehlt.");
                return;
            }
            String chapterSource = worldEditorMapper.readChapterContentForDocx();
            if (chapterSource.isBlank()) {
                showDocxResultDialog(false, "chapter.txt ist leer",
                        "Die Datei chapter.txt enthält keinen Text.\n" + chapterFile);
                setBusy(false, "chapter.txt ist leer.");
                return;
            }
            List<String> titles = NovelWizardDocxFactory.extractChapterTitles(chapterSource);
            if (titles.isEmpty()) {
                showDocxResultDialog(false, "Keine Kapitel erkannt",
                        "In chapter.txt wurden keine Kapitel-Ueberschriften gefunden.\n\n"
                                + "Bitte pro Kapitel eine Zeile wie:\n"
                                + "  ## Kapitel 1: Titel\n"
                                + "  Kapitel 2: Titel\n"
                                + "schreiben.\n\nDatei: " + chapterFile);
                setBusy(false, "Keine Kapitel-Ueberschriften in chapter.txt.");
                return;
            }
            NovelWizardDocxResult result = NovelWizardDocxFactory.createChapterDocxFiles(projectDirectory, chapterSource);
            Platform.runLater(() -> {
                if (onProjectChanged != null) {
                    onProjectChanged.run();
                }
            });
            refreshPreview();
            StringBuilder body = new StringBuilder();
            body.append("Projektordner:\n").append(projectDirectory).append("\n\n");
            body.append("Erkannt: ").append(result.titles().size()).append(" Kapitel\n");
            body.append("Neu erstellt: ").append(result.created()).append("\n");
            body.append("Aktualisiert (Titel + Zusammenfassung): ").append(result.updatedExisting()).append("\n\n");
            for (int i = 0; i < result.titles().size(); i++) {
                body.append(String.format("%02d. %s → %s%n",
                        i + 1,
                        result.titles().get(i),
                        result.paths().get(i).getFileName()));
            }
            if (result.created() == 0 && result.updatedExisting() > 0) {
                body.append("\nHinweis: Bestehende DOCX wurden mit Zusammenfassungen aus chapter.txt ueberschrieben.");
            }
            showDocxResultDialog(true, "Kapitel-DOCX", body.toString());
            setBusy(false, result.created() + " neu, " + result.updatedExisting()
                    + " aktualisiert – " + result.total() + " Kapitel gesamt.");
        } catch (Exception e) {
            logger.warn("DOCX-Erzeugung aus chapter.txt fehlgeschlagen", e);
            showDocxResultDialog(false, "DOCX-Erzeugung fehlgeschlagen", e.getMessage());
            setBusy(false, "DOCX-Erzeugung fehlgeschlagen: " + e.getMessage());
        }
    }

    private void showDocxResultDialog(boolean success, String header, String content) {
        CustomAlert alert = new CustomAlert(success ? CustomAlert.AlertType.INFORMATION : CustomAlert.AlertType.ERROR);
        alert.setTitle("Kapitel-DOCX");
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.applyTheme(themeIndex);
        Window dialogOwner = stage != null ? stage : owner;
        alert.showAndWait(dialogOwner);
    }

    private void finishCurrentPhase(String contentOverride) {
        NovelWizardPhase phase = session.getCurrentPhase();
        if (phase == NovelWizardPhase.CHARACTERS
                && (contentOverride == null || contentOverride.isBlank())) {
            finishCharactersPhaseWithSheets();
            return;
        }
        completePhase(phase, contentOverride);
    }

    private void finishCharactersPhaseWithSheets() {
        setBusy(true, "Character Sheets werden erzeugt …");
        String dialogue = NovelWizardAiService.buildPhaseDialogue(session, NovelWizardPhase.CHARACTERS);
        aiService.generateCharacterSheets(session, worldEditorMapper.readExistingContext(), dialogue)
                .whenComplete((sheets, ex) -> Platform.runLater(() -> {
                    if (ex != null) {
                        logger.warn("Character Sheets konnten nicht erzeugt werden", ex);
                        setBusy(false, "Character Sheets fehlgeschlagen: " + ex.getMessage());
                        CustomAlert alert = new CustomAlert(CustomAlert.AlertType.ERROR);
                        alert.setTitle("Figuren-Phase");
                        alert.setHeaderText("Character Sheets konnten nicht erzeugt werden");
                        alert.setContentText(ex.getMessage());
                        alert.applyTheme(themeIndex);
                        alert.showAndWait(stage != null ? stage : owner);
                        return;
                    }
                    String content = sheets == null || sheets.isBlank()
                            ? buildPhaseSummary(NovelWizardPhase.CHARACTERS)
                            : sheets;
                    completePhase(NovelWizardPhase.CHARACTERS, content);
                }));
    }

    private void completePhase(NovelWizardPhase phase, String contentOverride) {
        try {
            String content = contentOverride;
            if (content == null || content.isBlank()) {
                content = buildPhaseSummary(phase);
            }
            worldEditorMapper.persistPhase(phase, content);
            if (phase == NovelWizardPhase.CHAPTERS) {
                String chapterSource = worldEditorMapper.readChapterContentForDocx();
                NovelWizardDocxResult docxResult = NovelWizardDocxFactory.createChapterDocxFiles(projectDirectory, chapterSource);
                if (onProjectChanged != null) {
                    onProjectChanged.run();
                }
                session.getPhaseStatus().put(phase, NovelWizardPhaseStatus.COMPLETED);
                session.setPendingTurn(null);
                sessionStore.save(session);
                renderSession();
                refreshPreview();
                renderWizardCompleteState(docxResult.total());
                showDocxResultDialog(true, "Kapitel-DOCX",
                        docxResult.created() + " neu erstellt, " + docxResult.updatedExisting()
                                + " aktualisiert (" + docxResult.total() + " Kapitel, mit Zusammenfassungen).\n\nProjektordner:\n"
                                + projectDirectory);
                setBusy(false, "Fertig: " + docxResult.total() + " Kapitel-DOCX.");
                return;
            }
            session.getPhaseStatus().put(phase, NovelWizardPhaseStatus.COMPLETED);
            if (phase != phase.next()) {
                session.setCurrentPhase(phase.next());
                statusLabel.setText("Phase gespeichert. Weiter mit Phase "
                        + phaseNumber(session.getCurrentPhase()) + "/" + totalPhaseCount()
                        + ": " + session.getCurrentPhase().getTitle());
            }
            session.setPendingTurn(null);
            sessionStore.save(session);
            renderSession();
            requestNextTurn();
        } catch (Exception e) {
            logger.warn("Roman-Assistent konnte Phase nicht speichern", e);
            setBusy(false, "Speichern fehlgeschlagen: " + e.getMessage());
        }
    }

    private void jumpToPhase(NovelWizardPhase targetPhase) {
        if (targetPhase == null) {
            return;
        }
        if (targetPhase == session.getCurrentPhase()) {
            enterSelectedPhase();
            return;
        }
        NovelWizardPhase leaving = session.getCurrentPhase();
        if (session.getPendingTurn() != null) {
            ButtonType jump = new ButtonType("Trotzdem springen");
            ButtonType cancel = new ButtonType("Abbrechen", ButtonBar.ButtonData.CANCEL_CLOSE);
            CustomAlert alert = new CustomAlert(CustomAlert.AlertType.CONFIRMATION);
            alert.setTitle("Phase wechseln");
            alert.setHeaderText("Zur Phase \"" + targetPhase.getTitle() + "\" springen?");
            alert.setContentText("Es gibt in der aktuellen Phase noch eine offene Frage. "
                    + "Wenn du springst, wird diese offene Frage verworfen; der Chat-Verlauf bleibt gespeichert.");
            alert.getButtonTypes().setAll(jump, cancel);
            alert.applyTheme(themeIndex);
            Window dialogOwner = stage != null ? stage : owner;
            Optional<ButtonType> result = alert.showAndWait(dialogOwner);
            if (result.isEmpty() || result.get() == cancel) {
                phaseComboBox.setValue(session.getCurrentPhase());
                return;
            }
        }

        persistCurrentPhaseDraft(leaving);

        session.setCurrentPhase(targetPhase);
        session.setPendingTurn(null);
        sessionStore.save(session);
        updatePhaseLabel();
        renderSession();
        selectPreviewTabForPhase(targetPhase);
        statusLabel.setText("Gesprungen zu Phase " + phaseNumber(targetPhase) + "/"
                + totalPhaseCount() + ": " + targetPhase.getTitle());
        enterSelectedPhase();
    }

    private void goBackOneQuestion() {
        if (session.getChatHistory().isEmpty()) {
            statusLabel.setText("Es gibt noch keine vorherige Frage.");
            return;
        }

        boolean removedUserAnswer = false;
        while (!session.getChatHistory().isEmpty()) {
            int lastIndex = session.getChatHistory().size() - 1;
            NovelWizardSession.ChatEntry last = session.getChatHistory().remove(lastIndex);
            if ("user".equals(last.role)) {
                removedUserAnswer = true;
                break;
            }
            // Assistant-Eintraege am Ende sind offene Folgefragen und werden beim Zurueckgehen verworfen.
        }

        if (!removedUserAnswer) {
            statusLabel.setText("Es gibt keine beantwortete Frage, zu der ich zurückspringen kann.");
            restoreLatestPendingTurn();
            renderSession();
            return;
        }

        rebuildCollectedAndSummary();
        restoreLatestPendingTurn();
        persistCurrentPhaseDraft(session.getCurrentPhase());
        sessionStore.save(session);
        renderSession();
        NovelWizardTurn pending = session.getPendingTurn();
        if (pending != null) {
            renderTurn(pending);
        }
        statusLabel.setText("Eine Antwort zurückgenommen. Du kannst die vorherige Frage neu beantworten.");
    }

    /** Schreibt Chat-Stand erneut in Welt-Dateien (z. B. Kurzprofil nach Code-Update). */
    private void resyncPersistedPhasesFromSession() {
        for (NovelWizardPhase phase : NovelWizardPhase.values()) {
            if (phase == NovelWizardPhase.BOOTSTRAP || phase == NovelWizardPhase.CHARACTERS) {
                continue;
            }
            if (hasPhaseUserAnswers(phase)) {
                persistCurrentPhaseDraft(phase);
            }
        }
    }

    private boolean hasPhaseUserAnswers(NovelWizardPhase phase) {
        for (NovelWizardSession.ChatEntry entry : session.getChatHistory()) {
            if (entry == null || entry.phase != phase || !"user".equals(entry.role)) {
                continue;
            }
            String choice = entry.choice;
            if (choice != null && !choice.isBlank()
                    && !choice.startsWith(NovelWizardSession.CORRECTION_PREFIX)) {
                return true;
            }
        }
        return false;
    }

    private void persistCurrentPhaseDraft(NovelWizardPhase phase) {
        if (phase == null || phase == NovelWizardPhase.BOOTSTRAP) {
            return;
        }
        // Figuren-Interview nur im Assistenten-Chat – characters.txt erhaelt beim Abschliessen Character Sheets.
        if (phase == NovelWizardPhase.CHARACTERS) {
            return;
        }
        try {
            worldEditorMapper.persistPhase(phase, buildPhaseSummary(phase));
            refreshPreview();
        } catch (Exception e) {
            logger.warn("Roman-Assistent konnte Arbeitsstand fuer Phase {} nicht speichern", phase, e);
            if (statusLabel != null) {
                statusLabel.setText("Arbeitsstand konnte nicht in den Welt-Editor geschrieben werden: " + e.getMessage());
            }
        }
    }

    private void discardLastUnansweredAssistantTurn() {
        if (session.getChatHistory().isEmpty()) {
            session.setPendingTurn(null);
            return;
        }
        int last = session.getChatHistory().size() - 1;
        NovelWizardSession.ChatEntry entry = session.getChatHistory().get(last);
        if ("assistant".equals(entry.role)) {
            session.getChatHistory().remove(last);
            session.setPendingTurn(null);
            restoreLatestPendingTurn();
        }
    }

    private void restorePendingTurnForPhase(NovelWizardPhase targetPhase) {
        NovelWizardTurn pending = null;
        for (int i = session.getChatHistory().size() - 1; i >= 0; i--) {
            NovelWizardSession.ChatEntry entry = session.getChatHistory().get(i);
            if (entry == null || entry.phase != targetPhase || !"assistant".equals(entry.role) || entry.parsed == null) {
                continue;
            }
            if (!entry.parsed.getQuestion().isBlank() || !entry.parsed.getContent().isBlank()) {
                pending = entry.parsed;
                break;
            }
        }
        session.setPendingTurn(pending);
    }

    private void selectPreviewTabForPhase(NovelWizardPhase phase) {
        if (previewTabs == null || phase == null || phase.getTargetFiles().isEmpty()) {
            return;
        }
        String label = WorldEditorMapper.tabLabel(phase.getTargetFiles().getFirst());
        for (Tab tab : previewTabs.getTabs()) {
            if (label.equals(tab.getText())) {
                previewTabs.getSelectionModel().select(tab);
                ensurePreviewEditorLoaded(tab);
                return;
            }
        }
    }

    private void restoreLatestPendingTurn() {
        NovelWizardTurn pending = null;
        NovelWizardPhase phase = session.getCurrentPhase();
        for (int i = session.getChatHistory().size() - 1; i >= 0; i--) {
            NovelWizardSession.ChatEntry entry = session.getChatHistory().get(i);
            if ("assistant".equals(entry.role) && entry.parsed != null
                    && (!entry.parsed.getQuestion().isBlank() || !entry.parsed.getContent().isBlank())) {
                pending = entry.parsed;
                phase = entry.phase == null ? phase : entry.phase;
                break;
            }
        }
        session.setCurrentPhase(phase);
        session.setPendingTurn(pending);
        if (pending == null) {
            questionText.setText("");
            hintText.setText("");
            hintBox.setManaged(false);
            hintBox.setVisible(false);
            clearOptions();
        }
    }

    private void rebuildCollectedAndSummary() {
        session.getCollected().clear();
        StringBuilder summary = new StringBuilder();
        int counter = 0;
        for (NovelWizardSession.ChatEntry entry : session.getChatHistory()) {
            if ("user".equals(entry.role)) {
                counter++;
                NovelWizardPhase phase = entry.phase == null ? NovelWizardPhase.BRAINSTORM : entry.phase;
                session.getCollected().put(phase.name().toLowerCase() + "." + counter, entry.choice);
                if (!summary.isEmpty()) {
                    summary.append("\n");
                }
                summary.append(phase.getTitle()).append(": ").append(entry.choice);
            }
        }
        String text = summary.toString();
        if (text.length() > 6000) {
            text = text.substring(text.length() - 6000);
        }
        session.setProjectSummary(text);
    }

    private String buildPhaseSummary(NovelWizardPhase phase) {
        StringBuilder sb = new StringBuilder();
        if (phase == NovelWizardPhase.BRAINSTORM) {
            String kurz = BrainstormGrunddaten.buildKurzprofil(session.getChatHistory());
            if (!kurz.isBlank()) {
                sb.append(kurz).append("\n\n");
            }
        }
        sb.append("### ").append(phase.getTitle()).append("\n\n");
        String lastQuestion = "";
        boolean hasAnswers = false;
        for (NovelWizardSession.ChatEntry entry : session.getChatHistory()) {
            if (entry == null || !phaseMatches(entry, phase)) {
                continue;
            }
            if ("assistant".equals(entry.role) && entry.parsed != null && !entry.parsed.getQuestion().isBlank()) {
                lastQuestion = entry.parsed.getQuestion();
            } else if ("user".equals(entry.role) && entry.choice != null && !entry.choice.isBlank()) {
                if (!lastQuestion.isBlank()) {
                    sb.append("**Frage:** ").append(lastQuestion).append("\n");
                    lastQuestion = "";
                }
                sb.append("- ").append(entry.choice.trim()).append("\n\n");
                hasAnswers = true;
            }
        }
        if (!hasAnswers) {
            appendCollectedAnswers(sb, phase);
        }
        if (phase == session.getCurrentPhase()) {
            NovelWizardTurn pending = session.getPendingTurn();
            if (pending != null) {
                if (!pending.getContent().isBlank()) {
                    sb.append("\n").append(pending.getContent().trim()).append("\n");
                } else if (!pending.getSummary().isBlank()) {
                    sb.append("\n").append(pending.getSummary().trim()).append("\n");
                }
            }
        }
        return sb.toString();
    }

    private boolean phaseMatches(NovelWizardSession.ChatEntry entry, NovelWizardPhase phase) {
        if (entry.phase == null) {
            return false;
        }
        return entry.phase == phase;
    }

    private void appendCollectedAnswers(StringBuilder sb, NovelWizardPhase phase) {
        String prefix = phase.name().toLowerCase() + ".";
        for (Map.Entry<String, String> entry : session.getCollected().entrySet()) {
            if (entry.getKey() != null
                    && entry.getKey().startsWith(prefix)
                    && entry.getValue() != null
                    && !entry.getValue().isBlank()) {
                sb.append("- ").append(entry.getValue().trim()).append("\n\n");
            }
        }
    }

    private void updateProjectSummary(String answer) {
        String prefix = session.getProjectSummary();
        String addition = session.getCurrentPhase().getTitle() + ": " + answer;
        String next = (prefix == null || prefix.isBlank()) ? addition : prefix + "\n" + addition;
        if (next.length() > 6000) {
            next = next.substring(next.length() - 6000);
        }
        session.setProjectSummary(next);
    }

    private void initializePreviewTabs() {
        if (previewTabs == null || previewTabsInitialized) {
            return;
        }
        previewTabsInitialized = true;
        for (Map.Entry<String, String> entry : worldEditorMapper.readWorldFiles().entrySet()) {
            String fileName = entry.getKey();
            previewContentByFile.put(fileName, entry.getValue());

            ScrollPane scroll = new ScrollPane();
            scroll.setFitToWidth(true);
            scroll.setFitToHeight(true);

            Tab tab = new Tab(WorldEditorMapper.tabLabel(fileName), scroll);
            tab.setUserData(fileName);
            previewTabs.getTabs().add(tab);
            previewTabByFile.put(fileName, tab);
            previewScrollByFile.put(fileName, scroll);
        }
        previewTabs.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab != null) {
                ensurePreviewEditorLoaded(newTab);
            }
        });
        Tab selected = previewTabs.getSelectionModel().getSelectedItem();
        if (selected != null) {
            ensurePreviewEditorLoaded(selected);
        }
    }

    private void ensurePreviewEditorLoaded(Tab tab) {
        if (tab == null) {
            return;
        }
        Object userData = tab.getUserData();
        if (!(userData instanceof String fileName)) {
            return;
        }
        MdTextArea existing = previewEditorByFile.get(fileName);
        String content = previewContentByFile.getOrDefault(fileName, "");
        if (existing != null) {
            if (!content.equals(existing.getText())) {
                existing.setText(content);
            }
            return;
        }
        MdTextArea area = new MdTextArea(MdTextAreaOptions.builder()
                .showToolbar(false)
                .editable(false)
                .hideMarkup(true)
                .themeIndex(themeIndex)
                .build());
        area.setText(content);
        ScrollPane scroll = previewScrollByFile.get(fileName);
        if (scroll != null) {
            scroll.setContent(area);
        }
        previewEditorByFile.put(fileName, area);
    }

    private void refreshPreview() {
        if (previewTabs == null) {
            return;
        }
        initializePreviewTabs();
        for (Map.Entry<String, String> entry : worldEditorMapper.readWorldFiles().entrySet()) {
            String fileName = entry.getKey();
            previewContentByFile.put(fileName, entry.getValue());
            MdTextArea editor = previewEditorByFile.get(fileName);
            if (editor != null) {
                editor.setText(entry.getValue());
            }
        }
        Tab selected = previewTabs.getSelectionModel().getSelectedItem();
        if (selected != null) {
            ensurePreviewEditorLoaded(selected);
        }
    }

    private void updatePhaseLabel() {
        NovelWizardPhase phase = session.getCurrentPhase();
        int number = phaseNumber(phase);
        int total = totalPhaseCount();
        int completed = countCompletedPhases();
        int percent = (int) Math.round(computePhaseProgress() * 100);

        phaseHeadlineLabel.setText(phase.getTitle());
        progressCaptionLabel.setText("Schritt " + number + " von " + total);
        progressFractionLabel.setText(completed + " erledigt · " + percent + " %");
        phaseProgressBar.setProgress(computePhaseProgress());
        rebuildPhaseSteps();
        if (phaseComboBox != null && phaseComboBox.getValue() != phase) {
            phaseComboBox.setValue(phase);
        }
        statusLabel.setText("Zuletzt gespeichert: " + session.getUpdatedAt());
        refreshCompletionUi();
    }

    private int countCompletedPhases() {
        int completed = 0;
        for (NovelWizardPhase p : NovelWizardPhase.values()) {
            if (p == NovelWizardPhase.BOOTSTRAP) {
                continue;
            }
            if (session.getPhaseStatus().get(p) == NovelWizardPhaseStatus.COMPLETED) {
                completed++;
            }
        }
        return completed;
    }

    private double computePhaseProgress() {
        int total = totalPhaseCount();
        if (total <= 0) {
            return 0;
        }
        int completed = 0;
        for (NovelWizardPhase phase : NovelWizardPhase.values()) {
            if (phase == NovelWizardPhase.BOOTSTRAP) {
                continue;
            }
            NovelWizardPhaseStatus status = session.getPhaseStatus().get(phase);
            if (status == NovelWizardPhaseStatus.COMPLETED) {
                completed++;
            }
        }
        NovelWizardPhase current = session.getCurrentPhase();
        NovelWizardPhaseStatus currentStatus = session.getPhaseStatus().get(current);
        double currentPart = 0;
        if (current != NovelWizardPhase.BOOTSTRAP
                && currentStatus != NovelWizardPhaseStatus.COMPLETED
                && currentStatus != NovelWizardPhaseStatus.NOT_STARTED) {
            currentPart = 0.5;
        }
        return Math.min(1.0, (completed + currentPart) / total);
    }

    private void rebuildPhaseSteps() {
        phaseStepsBar.getChildren().clear();
        for (NovelWizardPhase phase : NovelWizardPhase.values()) {
            if (phase == NovelWizardPhase.BOOTSTRAP) {
                continue;
            }
            String variant;
            String prefix;
            if (phase == session.getCurrentPhase()) {
                variant = "current";
                prefix = "● ";
            } else {
                NovelWizardPhaseStatus status = session.getPhaseStatus().get(phase);
                if (status == NovelWizardPhaseStatus.COMPLETED) {
                    variant = "done";
                    prefix = "✓ ";
                } else if (status == NovelWizardPhaseStatus.REVISITING
                        || status == NovelWizardPhaseStatus.IN_PROGRESS) {
                    variant = "in-progress";
                    prefix = status == NovelWizardPhaseStatus.REVISITING ? "↻ " : "◐ ";
                } else {
                    variant = "pending";
                    prefix = "○ ";
                }
            }
            Label chip = new Label(prefix + phaseNumber(phase) + "  " + phase.getTitle());
            chip.getStyleClass().addAll("novel-wizard-phase-chip", "novel-wizard-phase-chip-" + variant);
            chip.setMinHeight(32);
            chip.setAlignment(Pos.CENTER);
            applyPhaseChipInlineStyle(chip, variant);
            NovelWizardPhase jumpTarget = phase;
            chip.setOnMouseClicked(e -> {
                if (phaseComboBox != null) {
                    phaseComboBox.setValue(jumpTarget);
                }
                jumpToPhase(jumpTarget);
            });
            phaseStepsBar.getChildren().add(chip);
        }
    }

    /** Sichtbare Pillen auch wenn config/css/manuskript.css veraltet ist. */
    private void applyPhaseChipInlineStyle(Label chip, String variant) {
        String accent = accentColorForTheme();
        String text = EditorDialogThemes.color(themeIndex, 1);
        String surface = EditorDialogThemes.color(themeIndex, 2);
        String border = EditorDialogThemes.color(themeIndex, 3);
        String style = switch (variant) {
            case "current" -> String.format(
                    "-fx-background-color: %s; -fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-font-size: 13px;"
                            + "-fx-padding: 7 16; -fx-background-radius: 18; -fx-border-radius: 18;"
                            + "-fx-border-color: %s; -fx-border-width: 2;",
                    accent, accent);
            case "done" -> String.format(
                    "-fx-background-color: %s; -fx-text-fill: %s; -fx-font-weight: bold; -fx-font-size: 12px;"
                            + "-fx-padding: 6 14; -fx-background-radius: 18; -fx-border-radius: 18;"
                            + "-fx-border-color: %s; -fx-border-width: 2;",
                    accent, "#ffffff", accent);
            case "revisit", "in-progress" -> String.format(
                    "-fx-background-color: %s; -fx-text-fill: %s; -fx-font-weight: bold; -fx-font-size: 12px;"
                            + "-fx-padding: 6 14; -fx-background-radius: 18; -fx-border-radius: 18;"
                            + "-fx-border-color: %s; -fx-border-width: 2; -fx-opacity: 1;",
                    accent, "#ffffff", accent);
            default -> String.format(
                    "-fx-background-color: %s; -fx-text-fill: %s; -fx-opacity: 0.88; -fx-font-size: 12px;"
                            + "-fx-padding: 6 14; -fx-background-radius: 18; -fx-border-radius: 18;"
                            + "-fx-border-color: %s; -fx-border-width: 1.5;",
                    surface, text, border);
        };
        chip.setStyle(style);
    }

    private String accentColorForTheme() {
        return switch (Math.max(0, Math.min(5, themeIndex))) {
            case 3 -> "#3b82f6";
            case 4 -> "#10b981";
            case 5 -> "#7c3aed";
            case 2 -> "#9c27b0";
            case 1 -> "#5c9fd6";
            default -> "#4a6fa5";
        };
    }

    private static String previewTabHint(NovelWizardPhase phase) {
        if (phase.getTargetFiles().isEmpty()) {
            return phase.getTitle();
        }
        return WorldEditorMapper.tabLabel(phase.getTargetFiles().getFirst());
    }

    private int phaseNumber(NovelWizardPhase phase) {
        return Math.max(1, phase.ordinal());
    }

    private int totalPhaseCount() {
        return NovelWizardPhase.values().length - 1;
    }

    private void setBusy(boolean busy, String status) {
        statusLabel.setText(status);
        if (optionsScroll != null) {
            optionsScroll.setDisable(busy);
        }
        if (customAnswerArea != null) {
            customAnswerArea.setDisable(busy);
        }
        if (nextQuestionButton != null) {
            nextQuestionButton.setDisable(busy);
        }
        if (finishPhaseButton != null) {
            finishPhaseButton.setDisable(busy);
        }
        if (backQuestionButton != null) {
            backQuestionButton.setDisable(busy);
        }
        if (phaseComboBox != null) {
            phaseComboBox.setDisable(busy);
        }
        for (Node child : optionsBox.getChildren()) {
            child.setDisable(busy);
        }
        if (questionText != null) {
            questionText.setOpacity(busy ? 0.65 : 1.0);
        }
        if (!busy) {
            refreshCompletionUi();
        }
    }

    private void pause() {
        sessionStore.save(session);
        stage.close();
    }
}
