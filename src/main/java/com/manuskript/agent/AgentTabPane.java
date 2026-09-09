package com.manuskript.agent;

import com.manuskript.EditorDialogThemes;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;

/**
 * Agenten-Leiste: umbrechende Auswahl der Agenten plus fest sichtbarer „+“-Button.
 * Die native TabPane-Kopfzeile bleibt unsichtbar, damit Namen nicht abgeschnitten werden.
 */
public class AgentTabPane extends BorderPane {

    private final TabPane tabPane = new TabPane();
    private final FlowPane selectorChips = new FlowPane(4, 4);
    private final Text addButtonMark = new Text("+");
    private final Button addButton = new Button();
    private final ToggleGroup selectorGroup = new ToggleGroup();
    private int currentThemeIndex;
    private final List<AgentTab> agentTabs = new ArrayList<>();
    private final List<SceneWritingAgentTab> sceneWritingTabs = new ArrayList<>();
    private final List<ChatbotAgentTab> chatbotTabs = new ArrayList<>();
    private AgentActivityTracker activityTracker;
    private Consumer<AgentTab> onAnalysisTabCreated;
    private boolean selectorRebuildScheduled;
    private boolean syncingSelector;

    public AgentTabPane() {
        getStyleClass().add("agent-tab-host");
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.getStyleClass().add("agent-tab-pane");
        tabPane.setTabMinHeight(0);
        tabPane.setTabMaxHeight(0);
        tabPane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        hideNativeTabHeader();
        tabPane.skinProperty().addListener((obs, oldSkin, newSkin) -> hideNativeTabHeader());

        selectorChips.getStyleClass().add("agent-selector-chips");
        selectorChips.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(selectorChips, Priority.ALWAYS);

        addButton.getStyleClass().add("agent-add-button");
        addButton.setGraphic(addButtonMark);
        addButton.setText(null);
        addButton.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        addButton.setTooltip(new Tooltip(AgentTabTooltipSupport.addTabTooltip()));
        addButton.setFocusTraversable(false);
        addButton.setMinSize(34, 34);
        addButton.setPrefSize(34, 34);
        addButton.setMaxSize(34, 34);
        addButton.setOnAction(e -> addNewAgent());
        styleAgentChrome(0);

        HBox selectorBar = new HBox(6, selectorChips, addButton);
        selectorBar.getStyleClass().add("agent-selector-bar");
        selectorBar.setAlignment(Pos.TOP_LEFT);
        selectorBar.setMinHeight(Region.USE_PREF_SIZE);
        selectorChips.prefWrapLengthProperty().bind(
                selectorBar.widthProperty().subtract(addButton.widthProperty()).subtract(16));

        setTop(selectorBar);
        setCenter(tabPane);

        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) ->
                syncSelectorSelection());
        tabPane.getTabs().addListener((ListChangeListener<Tab>) change -> scheduleSelectorRebuild());
        tabPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                Platform.runLater(this::rebuildSelector);
            }
        });
    }

    private void hideNativeTabHeader() {
        Platform.runLater(() -> {
            Node header = tabPane.lookup(".tab-header-area");
            if (header != null) {
                header.setVisible(false);
                header.setManaged(false);
            }
        });
    }

    public void setOnAnalysisTabCreated(Consumer<AgentTab> callback) {
        this.onAnalysisTabCreated = callback;
    }

    public void setActivityTracker(AgentActivityTracker tracker) {
        this.activityTracker = tracker;
        wireActivityTracking();
    }

    private void wireActivityTracking() {
        if (activityTracker == null) {
            return;
        }
        for (AgentTab tab : agentTabs) {
            tab.bindActivityTracker(activityTracker);
        }
        for (SceneWritingAgentTab tab : sceneWritingTabs) {
            tab.bindActivityTracker(activityTracker);
        }
        for (ChatbotAgentTab tab : chatbotTabs) {
            tab.bindActivityTracker(activityTracker);
        }
    }

    public void loadFromConfig() {
        List<AgentConfig> configs = AgentConfigManager.loadConfigs();
        for (AgentConfig config : configs) {
            addAgentTab(config, false);
        }
        int agentTabCount = tabPane.getTabs().size();
        if (agentTabCount < configs.size()) {
            org.slf4j.LoggerFactory.getLogger(AgentTabPane.class).warn(
                    "Nur {}/{} Agenten-Tabs erzeugt", agentTabCount, configs.size());
        }
        selectDefaultAgentTab();
        wireActivityTracking();
        scheduleSelectorRebuild();
    }

    public AgentTab addAgentTab(AgentConfig config, boolean saveConfig) {
        if (config.isSceneWritingAgent()) {
            return addSceneWritingTab(config, saveConfig);
        }
        if (config.isChatbotAgent()) {
            return addChatbotTab(config, saveConfig);
        }
        return addAnalysisTab(config, saveConfig);
    }

    private AgentTab addAnalysisTab(AgentConfig config, boolean saveConfig) {
        AgentTab agentTab = new AgentTab(config);
        agentTab.setOnConfigChanged(this::saveAllConfigs);

        Tab tab = new Tab(config.getName());
        tab.setContent(agentTab);
        tab.getStyleClass().add("agent-tab-item");
        tab.setClosable(config.isUserDefined());
        tab.setOnCloseRequest(e -> handleTabClose(agentTab, e));

        agentTab.setOnConfigChanged(() -> {
            tab.setText(agentTab.getAgentConfig().getName());
            saveAllConfigs();
            scheduleSelectorRebuild();
        });

        applyTabTooltip(tab, config);

        tabPane.getTabs().add(tab);
        agentTabs.add(agentTab);
        if (activityTracker != null) {
            agentTab.bindActivityTracker(activityTracker);
        }

        if (saveConfig) {
            saveAllConfigs();
            if (onAnalysisTabCreated != null) {
                onAnalysisTabCreated.accept(agentTab);
            }
            revealTab(tab);
        }
        return agentTab;
    }

    private AgentTab addSceneWritingTab(AgentConfig config, boolean saveConfig) {
        SceneWritingAgentTab sceneTab = new SceneWritingAgentTab(config);
        sceneTab.setOnConfigChanged(this::saveAllConfigs);

        Tab tab = new Tab(config.getName());
        tab.setContent(sceneTab);
        tab.getStyleClass().add("agent-tab-item");
        tab.setClosable(false);
        tab.setOnCloseRequest(e -> e.consume());

        sceneTab.setOnConfigChanged(() -> {
            tab.setText(sceneTab.getAgentConfig().getName());
            saveAllConfigs();
            scheduleSelectorRebuild();
        });

        applyTabTooltip(tab, config);

        tabPane.getTabs().add(tab);
        sceneWritingTabs.add(sceneTab);
        if (activityTracker != null) {
            sceneTab.bindActivityTracker(activityTracker);
        }

        if (saveConfig) {
            saveAllConfigs();
        }
        return null;
    }

    private AgentTab addChatbotTab(AgentConfig config, boolean saveConfig) {
        ChatbotAgentTab chatTab = new ChatbotAgentTab(config);
        chatTab.setOnConfigChanged(this::saveAllConfigs);

        Tab tab = new Tab(config.getName());
        tab.setContent(chatTab);
        tab.getStyleClass().add("agent-tab-item");
        tab.setClosable(false);
        tab.setOnCloseRequest(e -> e.consume());

        chatTab.setOnConfigChanged(() -> {
            tab.setText(chatTab.getAgentConfig().getName());
            saveAllConfigs();
            scheduleSelectorRebuild();
        });

        applyTabTooltip(tab, config);

        tabPane.getTabs().add(tab);
        chatbotTabs.add(chatTab);
        if (activityTracker != null) {
            chatTab.bindActivityTracker(activityTracker);
        }

        if (saveConfig) {
            saveAllConfigs();
        }
        return null;
    }

    private void handleTabClose(AgentTab agentTab, javafx.event.Event e) {
        if (!agentTab.getAgentConfig().isUserDefined()) {
            e.consume();
            return;
        }
        agentTabs.remove(agentTab);
        saveAllConfigs();
    }

    private void applyTabTooltip(Tab tab, AgentConfig config) {
        String text = AgentTabTooltipSupport.tooltipFor(config);
        if (text == null || text.isBlank()) {
            tab.getProperties().remove("agentTabTooltip");
        } else {
            tab.getProperties().put("agentTabTooltip", text);
        }
        scheduleSelectorRebuild();
    }

    private void scheduleSelectorRebuild() {
        if (selectorRebuildScheduled) {
            return;
        }
        selectorRebuildScheduled = true;
        Platform.runLater(() -> {
            selectorRebuildScheduled = false;
            rebuildSelector();
        });
    }

    private void rebuildSelector() {
        selectorChips.getChildren().clear();
        selectorGroup.getToggles().clear();
        Tab selected = tabPane.getSelectionModel().getSelectedItem();
        for (Tab tab : tabPane.getTabs()) {
            selectorChips.getChildren().add(createSelectorChip(tab, tab == selected));
        }
        styleAgentChrome(currentThemeIndex);
    }

    private Node createSelectorChip(Tab tab, boolean selected) {
        ToggleButton chip = new ToggleButton(tab.getText());
        chip.getStyleClass().add("agent-selector-chip");
        chip.setToggleGroup(selectorGroup);
        chip.setSelected(selected);
        chip.setFocusTraversable(false);
        chip.setMnemonicParsing(false);
        chip.setMinHeight(28);
        chip.setMaxHeight(32);
        chip.setWrapText(false);
        Object tipText = tab.getProperties().get("agentTabTooltip");
        if (tipText instanceof String text && !text.isBlank()) {
            Tooltip tip = new Tooltip(text);
            tip.setWrapText(true);
            tip.setMaxWidth(420);
            chip.setTooltip(tip);
        }
        chip.setOnAction(e -> {
            if (syncingSelector) {
                return;
            }
            tabPane.getSelectionModel().select(tab);
            chip.setSelected(true);
        });

        if (!tab.isClosable()) {
            return chip;
        }

        Text closeMark = new Text("×");
        Button close = new Button();
        close.getStyleClass().add("agent-selector-close");
        close.setGraphic(closeMark);
        close.setText(null);
        close.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        close.setFocusTraversable(false);
        close.setMinSize(18, 18);
        close.setPrefSize(20, 20);
        close.setMaxSize(20, 20);
        close.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
            e.consume();
            requestClose(tab);
        });
        styleCloseButton(close, closeMark, currentThemeIndex);

        HBox wrap = new HBox(0, chip, close);
        wrap.getStyleClass().add("agent-selector-chip-wrap");
        wrap.setAlignment(Pos.CENTER_LEFT);
        return wrap;
    }

    private void requestClose(Tab tab) {
        javafx.event.Event event = new javafx.event.Event(Tab.TAB_CLOSE_REQUEST_EVENT);
        if (tab.getOnCloseRequest() != null) {
            tab.getOnCloseRequest().handle(event);
        }
        if (!event.isConsumed()) {
            tabPane.getTabs().remove(tab);
        }
    }

    private void syncSelectorSelection() {
        syncingSelector = true;
        try {
            Tab selected = tabPane.getSelectionModel().getSelectedItem();
            int index = 0;
            for (Tab tab : tabPane.getTabs()) {
                if (index >= selectorChips.getChildren().size()) {
                    break;
                }
                Node node = selectorChips.getChildren().get(index);
                ToggleButton chip = findChip(node);
                if (chip != null) {
                    chip.setSelected(tab == selected);
                }
                index++;
            }
        } finally {
            syncingSelector = false;
        }
    }

    private static ToggleButton findChip(Node node) {
        if (node instanceof ToggleButton chip) {
            return chip;
        }
        if (node instanceof HBox box) {
            for (Node child : box.getChildren()) {
                if (child instanceof ToggleButton chip) {
                    return chip;
                }
            }
        }
        return null;
    }

    private void addNewAgent() {
        String backend = com.manuskript.ResourceManager.getParameter("agent.backend", "Ollama");
        String model;
        if ("OpenAI".equals(backend)) {
            model = com.manuskript.ResourceManager.getParameter("agent.openai.model", "gpt-4o-mini");
        } else {
            model = com.manuskript.ResourceManager.getParameter("agent.ollama.model",
                    com.manuskript.ParameterRegistry.DEFAULT_OLLAMA_MODEL);
        }
        AgentConfig defaultConfig = new AgentConfig(
            "Neuer Agent",
            backend,
            AgentConfigManager.getDefaultPlotholePrompt(),
            model,
            0.3, 2048, 0.7, 1.3
        );
        defaultConfig.setUserDefined(true);
        addAnalysisTab(defaultConfig, true);
    }

    /**
     * Speichert in der sichtbaren Tab-Reihenfolge, damit neue Agenten hinten bleiben.
     */
    private void saveAllConfigs() {
        List<AgentConfig> configs = new ArrayList<>();
        for (Tab tab : tabPane.getTabs()) {
            AgentConfig config = configFromTab(tab);
            if (config != null) {
                configs.add(config);
            }
        }
        AgentConfigManager.saveConfigs(configs);
    }

    private static AgentConfig configFromTab(Tab tab) {
        if (tab == null) {
            return null;
        }
        Node content = tab.getContent();
        if (content instanceof AgentTab analysis) {
            return analysis.getAgentConfig();
        }
        if (content instanceof SceneWritingAgentTab scene) {
            return scene.getAgentConfig();
        }
        if (content instanceof ChatbotAgentTab chat) {
            return chat.getAgentConfig();
        }
        return null;
    }

    public AgentTab getActiveTab() {
        Tab selected = tabPane.getSelectionModel().getSelectedItem();
        if (selected != null && selected.getContent() instanceof AgentTab) {
            return (AgentTab) selected.getContent();
        }
        return agentTabs.isEmpty() ? null : agentTabs.get(0);
    }

    public AgentTab findTabByAgentId(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return null;
        }
        for (AgentTab tab : agentTabs) {
            if (agentId.equals(tab.getAgentId())) {
                return tab;
            }
        }
        return null;
    }

    public void selectTab(AgentTab agentTab) {
        if (agentTab == null) {
            return;
        }
        for (Tab tab : tabPane.getTabs()) {
            if (tab.getContent() == agentTab) {
                revealTab(tab);
                return;
            }
        }
    }

    private void revealTab(Tab tab) {
        tabPane.getSelectionModel().select(tab);
        Platform.runLater(() -> {
            tabPane.getSelectionModel().select(tab);
            tabPane.requestLayout();
            syncSelectorSelection();
        });
    }

    private void selectDefaultAgentTab() {
        Tab preferred = findDefaultAgentTab();
        if (preferred == null) {
            return;
        }
        tabPane.getSelectionModel().select(preferred);
        Platform.runLater(() -> {
            tabPane.getSelectionModel().select(preferred);
            tabPane.requestLayout();
            syncSelectorSelection();
        });
    }

    private Tab findDefaultAgentTab() {
        Tab firstAnalysis = null;
        for (Tab tab : tabPane.getTabs()) {
            AgentConfig config = configFromTab(tab);
            if (config == null) {
                continue;
            }
            if (AgentConfigManager.PLOTHOLE_AGENT_ID.equals(config.getId())
                    || "Plothole-Agent".equals(config.getName())) {
                return tab;
            }
            if (firstAnalysis == null && tab.getContent() instanceof AgentTab) {
                firstAnalysis = tab;
            }
        }
        if (firstAnalysis != null) {
            return firstAnalysis;
        }
        return tabPane.getTabs().isEmpty() ? null : tabPane.getTabs().get(0);
    }

    public List<AgentTab> getAgentTabs() {
        return new ArrayList<>(agentTabs);
    }

    public List<SceneWritingAgentTab> getSceneWritingTabs() {
        return new ArrayList<>(sceneWritingTabs);
    }

    public List<ChatbotAgentTab> getChatbotTabs() {
        return new ArrayList<>(chatbotTabs);
    }

    public void applyFontSize(int size) {
        applyFontSize(size, -1);
    }

    public void applyFontSize(int size, int themeIndex) {
        applyEditorAppearance(size, themeIndex, null);
    }

    public void applyEditorAppearance(int fontSizePx, int themeIndex, String fontFamily) {
        if (themeIndex >= 0) {
            EditorDialogThemes.applyToNode(this, themeIndex);
            styleAgentChrome(themeIndex);
        }
        for (AgentTab tab : agentTabs) {
            tab.applyEditorFont(fontFamily, fontSizePx);
            if (themeIndex >= 0) {
                tab.applyAnswerTheme(themeIndex);
            }
        }
        for (SceneWritingAgentTab tab : sceneWritingTabs) {
            tab.applyEditorFont(fontFamily, fontSizePx);
            if (themeIndex >= 0) {
                tab.applyAnswerTheme(themeIndex);
            }
        }
        for (ChatbotAgentTab tab : chatbotTabs) {
            tab.applyFontSize(fontSizePx);
            if (themeIndex >= 0) {
                tab.applyChatTheme(themeIndex);
            }
            tab.applyEditorFont(fontFamily, fontSizePx);
        }
    }

    private void styleAgentChrome(int themeIndex) {
        currentThemeIndex = themeIndex;
        String text = EditorDialogThemes.color(themeIndex, 1);
        String surface = EditorDialogThemes.color(themeIndex, 2);
        String border = EditorDialogThemes.color(themeIndex, 3);
        addButton.setStyle(String.format(
                "-fx-background-color:%s;-fx-border-color:%s;-fx-border-width:1px;-fx-border-radius:4px;-fx-background-radius:4px;-fx-padding:0;-fx-content-display:graphic-only;",
                surface, border));
        addButtonMark.setStyle(String.format(
                "-fx-fill:%s;-fx-font-size:22px;-fx-font-weight:bold;",
                text));
        for (Node node : selectorChips.getChildren()) {
            if (node instanceof HBox wrap) {
                for (Node child : wrap.getChildren()) {
                    if (child instanceof Button close && close.getStyleClass().contains("agent-selector-close")
                            && close.getGraphic() instanceof Text closeMark) {
                        styleCloseButton(close, closeMark, themeIndex);
                    }
                }
            }
        }
        for (Node node : lookupAll(".agent-chrome-spinner")) {
            if (node instanceof Spinner<?> spinner) {
                AgentChromeSpinnerSupport.apply(spinner, themeIndex);
            }
        }
    }

    private static void styleCloseButton(Button close, Text closeMark, int themeIndex) {
        String text = EditorDialogThemes.color(themeIndex, 1);
        close.setStyle("-fx-background-color:transparent;-fx-border-color:transparent;-fx-padding:0 4 0 2;-fx-content-display:graphic-only;");
        closeMark.setStyle(String.format(
                "-fx-fill:%s;-fx-font-size:14px;-fx-font-weight:normal;",
                text));
    }

    public void reloadFromConfig() {
        AgentConfigManager.invalidateCache();
        tabPane.getTabs().clear();
        agentTabs.clear();
        sceneWritingTabs.clear();
        chatbotTabs.clear();
        loadFromConfig();
        wireActivityTracking();
        scheduleSelectorRebuild();
    }
}
