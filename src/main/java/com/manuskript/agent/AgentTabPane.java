package com.manuskript.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;
import javafx.scene.Node;

/**
 * Agenten-Leiste: Tabs plus fest sichtbarer „+“-Button rechts.
 * Neue Agenten bleiben am Ende der Leiste, nicht zwischen den Builtins.
 */
public class AgentTabPane extends StackPane {

    private final TabPane tabPane = new TabPane();
    private final Button addButton = new Button("+");
    private final List<AgentTab> agentTabs = new ArrayList<>();
    private final List<SceneWritingAgentTab> sceneWritingTabs = new ArrayList<>();
    private final List<ChatbotAgentTab> chatbotTabs = new ArrayList<>();
    private AgentActivityTracker activityTracker;
    private Consumer<AgentTab> onAnalysisTabCreated;

    public AgentTabPane() {
        getStyleClass().add("agent-tab-host");
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        tabPane.getStyleClass().add("agent-tab-pane");
        tabPane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        addButton.getStyleClass().add("agent-add-button");
        addButton.setTooltip(new Tooltip(AgentTabTooltipSupport.addTabTooltip()));
        addButton.setFocusTraversable(false);
        addButton.setMinSize(28, 26);
        addButton.setPrefSize(32, 28);
        addButton.setOnAction(e -> addNewAgent());
        StackPane.setAlignment(addButton, Pos.TOP_RIGHT);
        StackPane.setMargin(addButton, new Insets(3, 4, 0, 0));

        getChildren().addAll(tabPane, addButton);

        tabPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                Platform.runLater(this::refreshTabTooltips);
            }
        });
        tabPane.getTabs().addListener((ListChangeListener<Tab>) change ->
                Platform.runLater(this::refreshTabTooltips));
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
        if (!tabPane.getTabs().isEmpty()) {
            tabPane.getSelectionModel().select(0);
        }
        wireActivityTracking();
        Platform.runLater(this::refreshTabTooltips);
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
            Platform.runLater(this::refreshTabTooltips);
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
        }
        revealTab(tab);
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
            Platform.runLater(this::refreshTabTooltips);
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
            Platform.runLater(this::refreshTabTooltips);
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
        Platform.runLater(this::refreshTabTooltips);
    }

    private void refreshTabTooltips() {
        if (tabPane.getScene() == null) {
            return;
        }
        Node headersRegion = tabPane.lookup(".tab-header-area .headers-region");
        if (!(headersRegion instanceof Parent headers)) {
            return;
        }
        var headerNodes = headers.getChildrenUnmodifiable();
        int count = Math.min(headerNodes.size(), tabPane.getTabs().size());
        for (int i = 0; i < count; i++) {
            Tab tab = tabPane.getTabs().get(i);
            Object tipText = tab.getProperties().get("agentTabTooltip");
            Node header = headerNodes.get(i);
            if (tipText instanceof String text && !text.isBlank()) {
                Tooltip tip = new Tooltip(text);
                tip.setWrapText(true);
                tip.setMaxWidth(420);
                Tooltip.install(header, tip);
            }
        }
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
        });
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

    public void reloadFromConfig() {
        AgentConfigManager.invalidateCache();
        tabPane.getTabs().clear();
        agentTabs.clear();
        sceneWritingTabs.clear();
        chatbotTabs.clear();
        loadFromConfig();
        wireActivityTracking();
        Platform.runLater(this::refreshTabTooltips);
    }
}
