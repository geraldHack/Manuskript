package com.manuskript.novelwizard;

import com.manuskript.CustomStage;
import com.manuskript.ResourceManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

public class NovelCreationWizardWindow {
    private static final Logger logger = LoggerFactory.getLogger(NovelCreationWizardWindow.class);

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
    private Label phaseLabel;
    private ComboBox<NovelWizardPhase> phaseComboBox;
    private Label phaseOverviewLabel;
    private Label statusLabel;
    private Label questionLabel;
    private Label hintLabel;
    private FlowPane optionPane;
    private TextField customAnswerField;
    private TabPane previewTabs;
    private Button nextQuestionButton;
    private Button finishPhaseButton;
    private Button backQuestionButton;

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
        worldEditorMapper.ensureWorldFiles(projectDirectory.getFileName().toString());
        createUi();
        renderSession();
        if (session.getPendingTurn() == null) {
            requestNextTurn();
        } else {
            renderTurn(session.getPendingTurn());
        }
        stage.show();
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
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Es gibt bereits eine Roman-Assistent-Session fuer dieses Projekt.\n\n"
                        + "Zuletzt gespeichert: " + existing.get().getUpdatedAt(),
                resume, restart, cancel);
        alert.setTitle("Roman-Assistent fortsetzen");
        alert.setHeaderText("Wie moechtest du weitermachen?");
        if (owner != null) {
            alert.initOwner(owner);
        }
        Optional<ButtonType> result = alert.showAndWait();
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
        stage.setWidth(1180);
        stage.setHeight(760);
        stage.setMinWidth(940);
        stage.setMinHeight(620);
        stage.setTitleBarTheme(themeIndex);
        if (owner != null) {
            stage.initOwner(owner);
        }

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(14));

        phaseLabel = new Label();
        phaseLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
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
        phaseComboBox.setPromptText("Zu Phase springen");
        phaseComboBox.setOnAction(e -> jumpToPhase(phaseComboBox.getValue()));
        phaseOverviewLabel = new Label();
        phaseOverviewLabel.setWrapText(true);
        phaseOverviewLabel.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 12px;");
        statusLabel = new Label();
        Label jumpLabel = new Label("Springen:");
        HBox top = new HBox(12, phaseLabel, jumpLabel, phaseComboBox, statusLabel);
        top.setAlignment(Pos.CENTER_LEFT);
        VBox topBox = new VBox(4, top, phaseOverviewLabel);
        root.setTop(topBox);

        chatArea = new TextArea();
        chatArea.setEditable(false);
        chatArea.setWrapText(true);
        chatArea.setPrefWidth(560);
        chatArea.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace;");

        previewTabs = new TabPane();
        previewTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        HBox center = new HBox(12, chatArea, previewTabs);
        HBox.setHgrow(chatArea, Priority.ALWAYS);
        HBox.setHgrow(previewTabs, Priority.ALWAYS);
        root.setCenter(center);

        questionLabel = new Label();
        questionLabel.setWrapText(true);
        questionLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        hintLabel = new Label();
        hintLabel.setWrapText(true);
        hintLabel.setStyle("-fx-opacity: 0.75;");

        optionPane = new FlowPane(8, 8);
        customAnswerField = new TextField();
        customAnswerField.setPromptText("Eigene Antwort oder Ueberarbeitungswunsch...");
        HBox.setHgrow(customAnswerField, Priority.ALWAYS);
        Button sendCustomButton = new Button("Senden");
        sendCustomButton.setOnAction(e -> submitCustomAnswer());

        nextQuestionButton = new Button("Naechste KI-Frage");
        nextQuestionButton.setOnAction(e -> requestNextTurn());
        finishPhaseButton = new Button("Phase speichern & naechste Phase starten");
        finishPhaseButton.setOnAction(e -> finishCurrentPhase(null));
        backQuestionButton = new Button("Eine Frage zurück");
        backQuestionButton.setOnAction(e -> goBackOneQuestion());
        Button pauseButton = new Button("Pausieren");
        pauseButton.setOnAction(e -> pause());

        HBox inputRow = new HBox(8, customAnswerField, sendCustomButton, backQuestionButton,
                nextQuestionButton, finishPhaseButton, pauseButton);
        inputRow.setAlignment(Pos.CENTER_LEFT);

        VBox bottom = new VBox(8, questionLabel, hintLabel, optionPane, inputRow);
        bottom.setPadding(new Insets(12, 0, 0, 0));
        root.setBottom(bottom);

        Scene scene = new Scene(root);
        String cssPath = ResourceManager.getCssResource("css/manuskript.css");
        if (cssPath != null) {
            scene.getStylesheets().add(cssPath);
        }
        stage.setSceneWithTitleBar(scene);
        stage.setFullTheme(themeIndex);
        stage.setOnCloseRequest(e -> sessionStore.save(session));
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
                sb.append("Du: ").append(entry.choice).append("\n\n");
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
        turn.setOptions(java.util.List.of("Nochmal versuchen", "Eigene Antwort eingeben", "Phase spaeter fortsetzen"));
        return turn;
    }

    private void renderTurn(NovelWizardTurn turn) {
        questionLabel.setText(turn.getQuestion());
        hintLabel.setText(turn.getHint());
        optionPane.getChildren().clear();
        if (!turn.getContent().isBlank()) {
            Button accept = new Button("Vorschlag uebernehmen");
            accept.setOnAction(e -> finishCurrentPhase(turn.getContent()));
            Button revise = new Button("Ueberarbeiten");
            revise.setOnAction(e -> customAnswerField.requestFocus());
            optionPane.getChildren().addAll(accept, revise);
        }
        for (String option : turn.getOptions()) {
            Button button = new Button(option);
            button.setWrapText(true);
            button.setOnAction(e -> submitAnswer(option, false));
            optionPane.getChildren().add(button);
        }
    }

    private void submitCustomAnswer() {
        String answer = customAnswerField.getText().trim();
        if (answer.isEmpty()) {
            return;
        }
        submitAnswer(answer, true);
        customAnswerField.clear();
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

    private void finishCurrentPhase(String contentOverride) {
        try {
            NovelWizardPhase phase = session.getCurrentPhase();
            String content = contentOverride;
            if (content == null || content.isBlank()) {
                content = buildPhaseSummary(phase);
            }
            worldEditorMapper.persistPhase(phase, content);
            if (phase == NovelWizardPhase.CHAPTERS) {
                NovelWizardDocxFactory.createChapterDocxFiles(projectDirectory, content);
                if (onProjectChanged != null) {
                    onProjectChanged.run();
                }
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
        if (targetPhase == null || targetPhase == session.getCurrentPhase()) {
            return;
        }
        if (session.getPendingTurn() != null) {
            ButtonType jump = new ButtonType("Trotzdem springen");
            ButtonType cancel = new ButtonType("Abbrechen", ButtonBar.ButtonData.CANCEL_CLOSE);
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                    "Es gibt in der aktuellen Phase noch eine offene Frage. "
                            + "Wenn du springst, wird diese offene Frage verworfen; der Chat-Verlauf bleibt gespeichert.",
                    jump, cancel);
            alert.setTitle("Phase wechseln");
            alert.setHeaderText("Zur Phase \"" + targetPhase.getTitle() + "\" springen?");
            if (stage != null) {
                alert.initOwner(stage);
            }
            Optional<ButtonType> result = alert.showAndWait();
            if (result.isEmpty() || result.get() == cancel) {
                phaseComboBox.setValue(session.getCurrentPhase());
                return;
            }
        }

        NovelWizardPhaseStatus targetStatus = session.getPhaseStatus().get(targetPhase);
        if (targetStatus == NovelWizardPhaseStatus.COMPLETED) {
            session.getPhaseStatus().put(targetPhase, NovelWizardPhaseStatus.REVISITING);
        }
        session.setCurrentPhase(targetPhase);
        session.setPendingTurn(null);
        sessionStore.save(session);
        renderSession();
        statusLabel.setText("Gesprungen zu Phase " + phaseNumber(targetPhase) + "/"
                + totalPhaseCount() + ": " + targetPhase.getTitle());
        requestNextTurn();
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

    private void persistCurrentPhaseDraft(NovelWizardPhase phase) {
        if (phase == null || phase == NovelWizardPhase.BOOTSTRAP) {
            return;
        }
        try {
            worldEditorMapper.persistPhase(phase, buildPhaseSummary(phase));
        } catch (Exception e) {
            logger.warn("Roman-Assistent konnte Arbeitsstand fuer Phase {} nicht speichern", phase, e);
            statusLabel.setText("Arbeitsstand konnte nicht in den Welt-Editor geschrieben werden: " + e.getMessage());
        }
    }

    private void restoreLatestPendingTurn() {
        NovelWizardTurn pending = null;
        NovelWizardPhase phase = session.getCurrentPhase();
        for (int i = session.getChatHistory().size() - 1; i >= 0; i--) {
            NovelWizardSession.ChatEntry entry = session.getChatHistory().get(i);
            if ("assistant".equals(entry.role) && entry.parsed != null && !entry.parsed.getQuestion().isBlank()) {
                pending = entry.parsed;
                phase = entry.phase == null ? phase : entry.phase;
                break;
            }
        }
        session.setCurrentPhase(phase);
        session.setPendingTurn(pending);
        if (pending == null) {
            questionLabel.setText("");
            hintLabel.setText("");
            optionPane.getChildren().clear();
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
        sb.append("### ").append(phase.getTitle()).append("\n\n");
        String lastQuestion = "";
        for (NovelWizardSession.ChatEntry entry : session.getChatHistory()) {
            if (entry.phase != phase) {
                continue;
            }
            if ("assistant".equals(entry.role) && entry.parsed != null && !entry.parsed.getQuestion().isBlank()) {
                lastQuestion = entry.parsed.getQuestion();
            } else if ("user".equals(entry.role)) {
                if (!lastQuestion.isBlank()) {
                    sb.append("**Frage:** ").append(lastQuestion).append("\n");
                }
                sb.append("- ").append(entry.choice).append("\n\n");
            }
        }
        NovelWizardTurn pending = session.getPendingTurn();
        if (pending != null && !pending.getContent().isBlank()) {
            sb.append("\n").append(pending.getContent()).append("\n");
        }
        return sb.toString();
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

    private void refreshPreview() {
        if (previewTabs == null) {
            return;
        }
        previewTabs.getTabs().clear();
        for (Map.Entry<String, String> entry : worldEditorMapper.readWorldFiles().entrySet()) {
            TextArea area = new TextArea(entry.getValue());
            area.setEditable(false);
            area.setWrapText(true);
            ScrollPane scroll = new ScrollPane(area);
            scroll.setFitToWidth(true);
            scroll.setFitToHeight(true);
            previewTabs.getTabs().add(new Tab(entry.getKey(), scroll));
        }
    }

    private void updatePhaseLabel() {
        NovelWizardPhase phase = session.getCurrentPhase();
        phaseLabel.setText("Roman-Assistent - Phase " + phaseNumber(phase) + "/"
                + totalPhaseCount() + ": " + phase.getTitle());
        if (phaseComboBox != null && phaseComboBox.getValue() != phase) {
            phaseComboBox.setValue(phase);
        }
        phaseOverviewLabel.setText(buildPhaseOverview());
        statusLabel.setText("Zuletzt gespeichert: " + session.getUpdatedAt());
    }

    private String buildPhaseOverview() {
        StringBuilder sb = new StringBuilder();
        for (NovelWizardPhase phase : NovelWizardPhase.values()) {
            if (phase == NovelWizardPhase.BOOTSTRAP) {
                continue;
            }
            NovelWizardPhaseStatus status = session.getPhaseStatus().get(phase);
            String marker;
            if (phase == session.getCurrentPhase()) {
                marker = "[>]";
            } else if (status == NovelWizardPhaseStatus.COMPLETED) {
                marker = "[x]";
            } else {
                marker = "[ ]";
            }
            if (!sb.isEmpty()) {
                sb.append("  ");
            }
            sb.append(marker)
                    .append(" ")
                    .append(phaseNumber(phase))
                    .append(". ")
                    .append(phase.getTitle());
        }
        return sb.toString();
    }

    private int phaseNumber(NovelWizardPhase phase) {
        return Math.max(1, phase.ordinal());
    }

    private int totalPhaseCount() {
        return NovelWizardPhase.values().length - 1;
    }

    private void setBusy(boolean busy, String status) {
        statusLabel.setText(status);
        optionPane.setDisable(busy);
        customAnswerField.setDisable(busy);
        nextQuestionButton.setDisable(busy);
        finishPhaseButton.setDisable(busy);
        if (backQuestionButton != null) {
            backQuestionButton.setDisable(busy);
        }
        if (phaseComboBox != null) {
            phaseComboBox.setDisable(busy);
        }
    }

    private void pause() {
        sessionStore.save(session);
        stage.close();
    }
}
