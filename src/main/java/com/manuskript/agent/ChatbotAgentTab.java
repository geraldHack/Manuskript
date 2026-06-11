package com.manuskript.agent;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Consumer;

import com.manuskript.CustomAlert;
import com.manuskript.CustomChatArea;
import com.manuskript.ResourceManager;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;

/**
 * Agent-Tab fuer Multi-Turn-Chat mit Sessions und Kontext-Pills.
 */
public class ChatbotAgentTab extends ScrollPane {

    private final AgentConfig config;
    private final VBox contentRoot;

    private final ComboBox<String> sessionCombo;
    private final FlowPane contextPills;
    private final HBox neighborSpinnersRow;
    private final Spinner<Integer> chaptersBeforeSpinner;
    private final Spinner<Integer> chaptersAfterSpinner;
    private final CustomChatArea chatArea;
    private final TextArea inputArea;
    private final Button sendButton;
    private final Button insertButton;
    private final Label statusLabel;

    private final ToggleButton toggleConfigButton;
    private final VBox configBox;
    private final TextArea promptArea;
    private final Slider temperatureSlider;
    private final Label temperatureValueLabel;
    private final CheckBox useParameterModelCheck;
    private final FilterableModelSelector modelSelector;
    private final ComboBox<ChatbotContextSize> contextSizeCombo;

    private Runnable onConfigChanged;
    private Consumer<String> onInsertClicked;
    private ChatMessageHandler messageHandler;
    private SessionProjectProvider projectProvider;

    private ChatbotSessionData currentSession;
    private ChatbotContextConfig contextConfig;
    private boolean sending = false;
    private boolean activityRegistered = false;
    private AgentActivityTracker activityTracker;
    private File projectDir;
    /** Name der Session, deren Q&A aktuell in {@link #chatArea} angezeigt wird. */
    private String loadedSessionName;

    public interface ChatMessageHandler {
        /**
         * @return null wenn Anfrage gestartet wurde, sonst Validierungsfehlermeldung
         */
        String sendMessage(
                String userMessage,
                List<CustomChatArea.QAPair> historyBeforeSend,
                ChatbotContextConfig contextConfig,
                ChatbotContextSize contextSize,
                boolean useParameterModel,
                String overrideModel,
                double temperature,
                Consumer<String> onComplete,
                Consumer<Throwable> onError);
    }

    public interface SessionProjectProvider {
        File getProjectDirectory();
    }

    public ChatbotAgentTab(AgentConfig config) {
        this.config = config;
        this.contextConfig = new ChatbotContextConfig();
        contextConfig.addSource(ChatbotContextSource.CURRENT_CHAPTER);

        contentRoot = new VBox(6);
        contentRoot.setPadding(new Insets(8));
        contentRoot.setMinHeight(0);
        contentRoot.getStyleClass().addAll("agent-tab", "chatbot-agent-tab");

        setMinHeight(0);
        AgentScrollPaneSupport.configureEntireTabScroll(this);
        setContent(contentRoot);

        sessionCombo = new ComboBox<>();
        sessionCombo.setMaxWidth(Double.MAX_VALUE);
        sessionCombo.setPromptText("Session wählen");
        sessionCombo.setOnAction(e -> switchSession());

        Button newSessionButton = new Button("Neu");
        newSessionButton.setOnAction(e -> createNewSession());
        Button deleteSessionButton = new Button("Löschen");
        deleteSessionButton.setOnAction(e -> deleteCurrentSession());
        Button clearButton = new Button("Leeren");
        clearButton.setOnAction(e -> clearChat());
        keepButtonReadable(newSessionButton, deleteSessionButton, clearButton);

        sessionCombo.setMinWidth(0);
        HBox sessionRow = new HBox(6, sessionCombo, newSessionButton, deleteSessionButton, clearButton);
        HBox.setHgrow(sessionCombo, Priority.ALWAYS);
        sessionRow.setAlignment(Pos.CENTER_LEFT);

        contextPills = new FlowPane(6, 4);
        contextPills.setPrefWrapLength(280);
        Button addContextButton = new Button("+ Kontext");
        addContextButton.setOnAction(e -> showAddContextMenu(addContextButton));

        chaptersBeforeSpinner = new Spinner<>(0, 50, 3);
        chaptersBeforeSpinner.setEditable(true);
        chaptersBeforeSpinner.setPrefWidth(70);
        chaptersAfterSpinner = new Spinner<>(0, 50, 3);
        chaptersAfterSpinner.setEditable(true);
        chaptersAfterSpinner.setPrefWidth(70);
        chaptersBeforeSpinner.valueProperty().addListener((obs, o, n) -> {
            contextConfig.setChaptersBefore(n);
            persistSessionSettings();
        });
        chaptersAfterSpinner.valueProperty().addListener((obs, o, n) -> {
            contextConfig.setChaptersAfter(n);
            persistSessionSettings();
        });
        neighborSpinnersRow = new HBox(8,
                new Label("Davor:"), chaptersBeforeSpinner,
                new Label("Danach:"), chaptersAfterSpinner);
        neighborSpinnersRow.setAlignment(Pos.CENTER_LEFT);
        updateNeighborSpinnersVisibility();

        chatArea = new CustomChatArea();
        VBox.setVgrow(chatArea, Priority.ALWAYS);
        chatArea.setMaxHeight(Double.MAX_VALUE);

        inputArea = new TextArea();
        inputArea.setPromptText("Frage eingeben…");
        inputArea.setPrefRowCount(3);
        inputArea.setWrapText(true);
        inputArea.setMaxWidth(Double.MAX_VALUE);
        inputArea.setOnKeyPressed(e -> {
            if (e.isShortcutDown() && e.getCode() == javafx.scene.input.KeyCode.ENTER) {
                sendMessage();
                e.consume();
            }
        });

        sendButton = new Button("Senden");
        sendButton.setMaxWidth(Double.MAX_VALUE);
        sendButton.getStyleClass().add("button primary");
        sendButton.setOnAction(e -> sendMessage());

        insertButton = new Button("Antwort einfügen");
        insertButton.setMaxWidth(Double.MAX_VALUE);
        insertButton.setOnAction(e -> {
            String answer = chatArea.getCurrentAnswer();
            if (answer != null && !answer.isBlank() && onInsertClicked != null) {
                onInsertClicked.accept(answer);
            }
        });

        statusLabel = new Label("Bereit");
        statusLabel.setWrapText(true);
        statusLabel.getStyleClass().add("agent-status-label");

        toggleConfigButton = new ToggleButton("⚙ Einstellungen");
        toggleConfigButton.setMaxWidth(Double.MAX_VALUE);

        configBox = new VBox(6);
        configBox.setPadding(new Insets(8));
        configBox.getStyleClass().add("agent-config-box");
        configBox.setVisible(false);
        configBox.setManaged(false);

        promptArea = new TextArea(config.getSystemPrompt());
        promptArea.setPrefRowCount(AgentTab.SYSTEM_PROMPT_VISIBLE_ROWS);
        promptArea.setWrapText(true);
        promptArea.setMaxWidth(Double.MAX_VALUE);
        promptArea.textProperty().addListener((obs, o, n) -> {
            config.setSystemPrompt(n);
            fireConfigChanged();
        });

        temperatureSlider = new Slider(0.0, 2.0, config.getTemperature());
        temperatureValueLabel = new Label(formatValue(config.getTemperature()));
        temperatureSlider.valueProperty().addListener((obs, old, val) -> {
            temperatureValueLabel.setText(formatValue(val.doubleValue()));
            config.setTemperature(val.doubleValue());
            persistSessionSettings();
            fireConfigChanged();
        });

        useParameterModelCheck = new CheckBox("Parameter-Modell verwenden");
        useParameterModelCheck.setSelected(true);
        modelSelector = new FilterableModelSelector(true);
        modelSelector.setSelectorDisabled(true);
        modelSelector.setOnLoad(this::loadModelsAsync);
        keepButtonReadable(modelSelector.getLoadButton());
        useParameterModelCheck.selectedProperty().addListener((obs, o, useParams) -> {
            if (!sending) {
                modelSelector.setSelectorDisabled(useParams);
            }
            persistSessionSettings();
        });

        contextSizeCombo = new ComboBox<>();
        contextSizeCombo.getItems().setAll(ChatbotContextSize.values());
        contextSizeCombo.setConverter(contextSizeConverter());
        contextSizeCombo.setButtonCell(contextSizeListCell());
        contextSizeCombo.setCellFactory(list -> contextSizeListCell());
        contextSizeCombo.setValue(ChatbotContextSize.defaultFromParameters());
        contextSizeCombo.valueProperty().addListener((obs, o, n) -> {
            if (n != null) {
                contextConfig.setContextSize(n);
                persistSessionSettings();
            }
        });

        modelSelector.getComboBox().setMinWidth(0);

        configBox.getChildren().addAll(
                new Label("System-Prompt:"), promptArea,
                createSliderRow("Temperatur:", temperatureSlider, temperatureValueLabel),
                useParameterModelCheck,
                new Label("Modell:"), modelSelector,
                new Label("Kontextgröße:"), contextSizeCombo
        );

        toggleConfigButton.selectedProperty().addListener((obs, o, sel) -> {
            configBox.setVisible(sel);
            configBox.setManaged(sel);
            AgentScrollPaneSupport.applyConfigExpandedLayout(this, contentRoot, chatArea, sel);
        });

        contentRoot.getChildren().addAll(
                sessionRow,
                addContextButton,
                contextPills,
                neighborSpinnersRow,
                chatArea,
                inputArea,
                sendButton,
                insertButton,
                statusLabel,
                toggleConfigButton,
                configBox
        );
        AgentScrollPaneSupport.applyConfigExpandedLayout(this, contentRoot, chatArea, false);

        refreshContextPills();
    }

    public void bindProject(File projectDirectory) {
        if (projectDirectory == null) {
            return;
        }
        String newPath = projectDirectory.getAbsolutePath();
        if (projectDir != null && newPath.equals(projectDir.getAbsolutePath())) {
            return;
        }
        if (currentSession != null && projectDir != null) {
            saveCurrentSession();
        }
        this.projectDir = projectDirectory;
        loadedSessionName = null;
        reloadSessions();
    }

    /** Projekt neu binden (z. B. nach Editor-Start), ohne unnötigen Reload. */
    public void refreshProjectBinding() {
        if (projectProvider == null) {
            return;
        }
        File dir = projectProvider.getProjectDirectory();
        if (dir == null) {
            return;
        }
        bindProject(dir);
    }

    private void ensureProjectBound() {
        if (projectDir == null) {
            refreshProjectBinding();
        }
    }

    private boolean ensureSessionReady() {
        ensureProjectBound();
        if (projectDir == null) {
            return false;
        }
        if (currentSession == null) {
            String name = sessionCombo.getValue();
            if (name == null || name.isBlank()) {
                name = ChatbotSessionStore.loadSelectedSessionName(projectDir);
            }
            currentSession = ChatbotSessionStore.load(projectDir, name);
            if (sessionCombo.getItems().isEmpty()) {
                sessionCombo.getItems().setAll(ChatbotSessionStore.listSessionNames(projectDir));
            }
            if (!sessionCombo.getItems().contains(name)) {
                sessionCombo.getItems().add(name);
            }
            sessionCombo.setValue(name);
        }
        return true;
    }

    public void reloadSessions() {
        if (projectDir == null) {
            return;
        }
        if (currentSession != null) {
            saveCurrentSession();
        }
        ChatbotSessionStore.ensureStandardSession(projectDir);
        List<String> names = ChatbotSessionStore.listSessionNames(projectDir);
        if (names.isEmpty()) {
            ChatbotSessionData standard = ChatbotSessionStore.newSession(ChatbotSessionStore.STANDARD_SESSION_NAME);
            ChatbotSessionStore.save(projectDir, standard);
            names = List.of(ChatbotSessionStore.STANDARD_SESSION_NAME);
        }
        String selected = ChatbotSessionStore.loadSelectedSessionName(projectDir);
        sessionCombo.getItems().setAll(names);
        if (names.contains(selected)) {
            sessionCombo.setValue(selected);
        } else {
            sessionCombo.setValue(ChatbotSessionStore.pickFallbackSessionName(names));
        }
        loadSessionData(sessionCombo.getValue());
    }

    private void switchSession() {
        String name = sessionCombo.getValue();
        if (name == null || projectDir == null) {
            return;
        }
        saveCurrentSession();
        ChatbotSessionStore.saveSelectedSessionName(projectDir, name);
        loadSessionData(name);
    }

    private void loadSessionData(String name) {
        if (projectDir == null || name == null) {
            return;
        }
        currentSession = ChatbotSessionStore.load(projectDir, name);
        contextConfig = currentSession.toContextConfig();
        List<CustomChatArea.QAPair> onDisk = currentSession.getQaPairs();
        if (onDisk == null) {
            onDisk = List.of();
        }

        boolean sameSession = name.equals(loadedSessionName);
        if (sameSession) {
            List<CustomChatArea.QAPair> inMemory = chatArea.getSessionHistory();
            if (inMemory.size() > onDisk.size()) {
                currentSession.setQaPairs(inMemory);
            } else {
                chatArea.loadSessionHistory(onDisk);
            }
        } else {
            chatArea.loadSessionHistory(onDisk);
            inputArea.clear();
        }
        loadedSessionName = name;

        contextSizeCombo.setValue(contextConfig.getContextSize());
        chaptersBeforeSpinner.getValueFactory().setValue(contextConfig.getChaptersBefore());
        chaptersAfterSpinner.getValueFactory().setValue(contextConfig.getChaptersAfter());
        useParameterModelCheck.setSelected(currentSession.isUseParameterModel());
        temperatureSlider.setValue(currentSession.getTemperature() > 0
                ? currentSession.getTemperature() : config.getTemperature());
        if (currentSession.getModel() != null && !currentSession.getModel().isBlank()) {
            modelSelector.setValue(currentSession.getModel());
        }
        refreshContextPills();
        updateNeighborSpinnersVisibility();
    }

    private void saveCurrentSession() {
        if (!ensureSessionReady()) {
            return;
        }
        currentSession.setQaPairs(chatArea.getSessionHistory());
        currentSession.applyContextConfig(contextConfig);
        currentSession.setContextSize(contextConfig.getContextSize().name());
        currentSession.setTemperature(temperatureSlider.getValue());
        currentSession.setUseParameterModel(useParameterModelCheck.isSelected());
        currentSession.setModel(modelSelector.getValue());
        ChatbotSessionStore.save(projectDir, currentSession);
    }

    private void persistSessionSettings() {
        saveCurrentSession();
    }

    private void createNewSession() {
        if (projectDir == null) {
            statusLabel.setText("Kein Projektordner — bitte Projekt öffnen.");
            return;
        }
        String defaultName = "Session " + (sessionCombo.getItems().size() + 1);
        TextField nameField = new TextField(defaultName);
        nameField.setPromptText("z. B. Brainstorm, Recherche, Szene …");
        nameField.setPrefWidth(320);
        nameField.selectAll();

        CustomAlert alert = new CustomAlert(Alert.AlertType.INFORMATION, "Neue Chat-Session");
        alert.setHeaderText("Session-Name eingeben");
        alert.setTextField(nameField);
        alert.setButtonTypes(ButtonType.OK, ButtonType.CANCEL);
        alert.applyTheme(chatArea.getThemeIndex());

        Window owner = getScene() != null ? getScene().getWindow() : null;
        if (owner != null) {
            alert.initOwner(owner);
        }

        alert.showAndWait(owner).ifPresent(result -> {
            if (result != ButtonType.OK) {
                return;
            }
            String name = nameField.getText().trim();
            if (name.isBlank()) {
                statusLabel.setText("Bitte einen Session-Namen eingeben.");
                return;
            }
            if (sessionCombo.getItems().stream().anyMatch(n -> n.equalsIgnoreCase(name))) {
                statusLabel.setText("Session \"" + name + "\" existiert bereits.");
                return;
            }
            saveCurrentSession();
            ChatbotSessionData data = ChatbotSessionStore.newSession(name);
            ChatbotSessionStore.save(projectDir, data);
            ChatbotSessionStore.saveSelectedSessionName(projectDir, name);
            sessionCombo.getItems().setAll(ChatbotSessionStore.listSessionNames(projectDir));
            sessionCombo.setValue(name);
            loadSessionData(name);
            statusLabel.setText("Session erstellt: " + name);
        });
    }

    private void deleteCurrentSession() {
        ensureProjectBound();
        String name = sessionCombo.getValue();
        if (name == null || projectDir == null || sessionCombo.getItems().size() <= 1) {
            statusLabel.setText("Mindestens eine Session muss bleiben.");
            return;
        }
        if (ChatbotSessionStore.isStandardSession(name)) {
            statusLabel.setText("Die Standard-Session kann nicht gelöscht werden.");
            return;
        }
        ChatbotSessionStore.delete(projectDir, name);
        if (name.equals(ChatbotSessionStore.loadSelectedSessionName(projectDir))) {
            List<String> remaining = ChatbotSessionStore.listSessionNames(projectDir);
            ChatbotSessionStore.ensureStandardSession(projectDir);
            remaining = ChatbotSessionStore.listSessionNames(projectDir);
            if (!remaining.isEmpty()) {
                ChatbotSessionStore.saveSelectedSessionName(
                        projectDir, ChatbotSessionStore.pickFallbackSessionName(remaining));
            }
        }
        // Gelöschte Session nicht erneut speichern (reloadSessions würde sonst die Datei wieder anlegen)
        currentSession = null;
        loadedSessionName = null;
        reloadSessions();
        statusLabel.setText("Session gelöscht: " + name);
    }

    private void clearChat() {
        chatArea.clearHistory();
        persistSessionSettings();
        statusLabel.setText("Chat geleert.");
    }

    public void bindActivityTracker(AgentActivityTracker tracker) {
        this.activityTracker = tracker;
    }

    private void registerActivity(String message) {
        if (activityTracker != null && !activityRegistered) {
            activityTracker.begin(message);
            activityRegistered = true;
        }
    }

    private void unregisterActivity() {
        if (activityTracker != null && activityRegistered) {
            activityTracker.end();
            activityRegistered = false;
        }
    }

    private void sendMessage() {
        if (sending) {
            return;
        }
        if (messageHandler == null) {
            statusLabel.setText("Chat nicht verbunden — Kapitel-Editor neu öffnen.");
            return;
        }
        if (!ensureSessionReady()) {
            statusLabel.setText("Kein Projektordner — bitte Projekt in Manuskript öffnen.");
            return;
        }
        String text = inputArea.getText();
        if (text == null || text.isBlank()) {
            statusLabel.setText("Bitte eine Frage eingeben.");
            return;
        }
        String question = text.trim();
        inputArea.clear();
        List<CustomChatArea.QAPair> history = chatArea.getSessionHistory();
        chatArea.clearAndShowNewQuestion(question);
        final int qaIndex = chatArea.getLastIndex();
        persistSessionSettings();
        sending = true;
        sendButton.setDisable(true);
        statusLabel.setText("Denke nach…");
        registerActivity(config.getName() + ": Chat-Anfrage läuft…");

        ChatbotContextSize size = contextSizeCombo.getValue();
        if (size == null) {
            size = ChatbotContextSize.COMPACT;
        }
        boolean useParams = useParameterModelCheck.isSelected();
        String model = modelSelector.getValue();
        double temp = temperatureSlider.getValue();

        String validationError = messageHandler.sendMessage(
                question,
                history,
                contextConfig,
                size,
                useParams,
                model,
                temp,
                answer -> Platform.runLater(() -> finishSend(qaIndex, answer)),
                err -> Platform.runLater(() -> {
                    sending = false;
                    sendButton.setDisable(false);
                    unregisterActivity();
                    chatArea.setAnswerAt(qaIndex, "Fehler: "
                            + (err.getMessage() != null ? err.getMessage() : err.toString()));
                    persistSessionSettings();
                    statusLabel.setText("Fehler beim Senden.");
                })
        );
        if (validationError != null) {
            sending = false;
            sendButton.setDisable(false);
            unregisterActivity();
            statusLabel.setText(validationError);
        }
    }

    private void finishSend(int qaIndex, String answer) {
        sending = false;
        sendButton.setDisable(false);
        unregisterActivity();
        String text = answer != null ? answer.trim() : "";
        if (text.isEmpty()) {
            text = "(Keine Textantwort vom Modell — ggf. anderes Modell wählen oder Kontext verkleinern.)";
            statusLabel.setText("Leere Antwort vom Modell.");
        } else {
            statusLabel.setText("Antwort erhalten.");
        }
        chatArea.setAnswerAt(qaIndex, text);
        persistSessionSettings();
    }

    private void showAddContextMenu(Button anchor) {
        ContextMenu menu = new ContextMenu();
        for (ChatbotContextSource source : ChatbotContextSource.values()) {
            if (contextConfig.hasSource(source)) {
                continue;
            }
            MenuItem item = new MenuItem(source.getLabel());
            item.setOnAction(e -> {
                contextConfig.addSource(source);
                refreshContextPills();
                updateNeighborSpinnersVisibility();
                persistSessionSettings();
            });
            menu.getItems().add(item);
        }
        if (menu.getItems().isEmpty()) {
            statusLabel.setText("Alle Kontext-Quellen aktiv.");
            return;
        }
        menu.show(anchor, javafx.geometry.Side.BOTTOM, 0, 0);
    }

    private void refreshContextPills() {
        contextPills.getChildren().clear();
        for (ChatbotContextSource source : contextConfig.getSources()) {
            Button pill = new Button(source.getLabel() + " ✕");
            pill.getStyleClass().add("chatbot-context-pill");
            pill.setTooltip(new Tooltip(source.getTooltip()));
            pill.setOnAction(e -> {
                contextConfig.removeSource(source);
                refreshContextPills();
                updateNeighborSpinnersVisibility();
                persistSessionSettings();
            });
            contextPills.getChildren().add(pill);
        }
    }

    private void updateNeighborSpinnersVisibility() {
        boolean show = contextConfig.hasSource(ChatbotContextSource.CHAPTERS_BEFORE)
                || contextConfig.hasSource(ChatbotContextSource.CHAPTERS_AFTER);
        neighborSpinnersRow.setVisible(show);
        neighborSpinnersRow.setManaged(show);
        chaptersBeforeSpinner.setDisable(!contextConfig.hasSource(ChatbotContextSource.CHAPTERS_BEFORE));
        chaptersAfterSpinner.setDisable(!contextConfig.hasSource(ChatbotContextSource.CHAPTERS_AFTER));
    }

    private void loadModelsAsync() {
        statusLabel.setText("Lade Modelle…");
        new Thread(() -> {
            try {
                AIBackend backend = createBackendForModelLoad();
                List<String> models = backend.getAvailableModels();
                Platform.runLater(() -> {
                    modelSelector.setModels(models);
                    if (!models.isEmpty() && modelSelector.getValue() == null) {
                        modelSelector.setValue(models.get(0));
                    }
                    statusLabel.setText(models.size() + " Modelle geladen.");
                });
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Modelle laden fehlgeschlagen: " + e.getMessage()));
            }
        }, "Chatbot-LoadModels").start();
    }

    private AIBackend createBackendForModelLoad() {
        String backendType = ResourceManager.getParameter("agent.backend", "Ollama");
        if ("OpenAI".equals(backendType)) {
            return new OpenAIBackend();
        }
        return new OllamaBackend(new com.manuskript.OllamaService());
    }

    public void setModels(List<String> models) {
        if (models != null) {
            modelSelector.setModels(models);
            String paramModel = config.getModel();
            if (paramModel != null && !paramModel.isBlank()) {
                modelSelector.setValue(paramModel);
            } else if (!models.isEmpty()) {
                modelSelector.setValue(models.get(0));
            }
        }
    }

    public AgentConfig getAgentConfig() {
        return config;
    }

    public String getAgentId() {
        return config.getId();
    }

    public void setOnConfigChanged(Runnable handler) {
        this.onConfigChanged = handler;
    }

    public void setOnInsertClicked(Consumer<String> handler) {
        this.onInsertClicked = handler;
    }

    public void setMessageHandler(ChatMessageHandler handler) {
        this.messageHandler = handler;
    }

    public void setProjectProvider(SessionProjectProvider provider) {
        this.projectProvider = provider;
        refreshProjectBinding();
    }

    public void applyFontSize(int size) {
        AgentFontSizeSupport.apply(this, size);
        chatArea.applyEditorFont(null, size);
        applyInputFont(null, size);
    }

    public void applyChatTheme(int themeIndex) {
        chatArea.setThemeIndex(themeIndex);
    }

    public void applyEditorFont(String fontFamily, int fontSizePx) {
        chatArea.applyEditorFont(fontFamily, fontSizePx);
        applyInputFont(fontFamily, fontSizePx);
    }

    private void applyInputFont(String fontFamily, int fontSizePx) {
        String familyCss = fontFamily != null && !fontFamily.isBlank()
                ? CustomChatArea.cssFontFamily(fontFamily.trim()) : null;
        int size = fontSizePx > 0 ? fontSizePx : 14;
        if (familyCss != null) {
            inputArea.setStyle(String.format("-fx-font-family: %s; -fx-font-size: %dpx;", familyCss, size));
        } else {
            inputArea.setStyle(String.format("-fx-font-size: %dpx;", size));
        }
    }

    private void fireConfigChanged() {
        if (onConfigChanged != null) {
            onConfigChanged.run();
        }
    }

    private static String formatValue(double v) {
        return String.format("%.2f", v);
    }

    /** Buttons neben einer wachsenden ComboBox: nicht unter die Textbreite schrumpfen. */
    private static void keepButtonReadable(Button... buttons) {
        for (Button button : buttons) {
            button.setMinWidth(Region.USE_PREF_SIZE);
            button.setMinHeight(Region.USE_PREF_SIZE);
            HBox.setHgrow(button, Priority.NEVER);
        }
    }

    private static HBox createSliderRow(String label, Slider slider, Label valueLabel) {
        HBox row = new HBox(8, new Label(label), slider, valueLabel);
        HBox.setHgrow(slider, Priority.ALWAYS);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static StringConverter<ChatbotContextSize> contextSizeConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(ChatbotContextSize value) {
                return value == null ? "" : value.getLabel();
            }

            @Override
            public ChatbotContextSize fromString(String string) {
                for (ChatbotContextSize size : ChatbotContextSize.values()) {
                    if (size.getLabel().equals(string)) {
                        return size;
                    }
                }
                return ChatbotContextSize.COMPACT;
            }
        };
    }

    private static ListCell<ChatbotContextSize> contextSizeListCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(ChatbotContextSize item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getLabel());
            }
        };
    }
}
