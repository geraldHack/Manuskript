package com.manuskript.agent;

import java.util.ArrayList;
import java.util.List;

import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

/**
 * TabPane-Container für mehrere Agenten-Tabs.
 * Ersetzt das alte AgentPanel im Editor-SplitPane.
 */
public class AgentTabPane extends TabPane {

    private final List<AgentTab> agentTabs = new ArrayList<>();
    private final List<SceneWritingAgentTab> sceneWritingTabs = new ArrayList<>();
    private final List<ChatbotAgentTab> chatbotTabs = new ArrayList<>();
    private final Tab addTab;
    private AgentActivityTracker activityTracker;

    public AgentTabPane() {
        setTabClosingPolicy(TabClosingPolicy.ALL_TABS);
        getStyleClass().add("agent-tab-pane");

        addTab = new Tab("+");
        addTab.setClosable(false);
        addTab.getStyleClass().add("agent-add-tab");
        getTabs().add(addTab);

        getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab == addTab) {
                addNewAgent();
            }
        });
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
        if (getTabs().size() > 1) {
            getSelectionModel().select(0);
        }
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
        });

        insertTabBeforeAdd(tab);
        agentTabs.add(agentTab);
        if (activityTracker != null) {
            agentTab.bindActivityTracker(activityTracker);
        }

        if (saveConfig) {
            saveAllConfigs();
        }
        getSelectionModel().select(tab);
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
        });

        insertTabBeforeAdd(tab);
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
        });

        insertTabBeforeAdd(tab);
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

    private void insertTabBeforeAdd(Tab tab) {
        int insertPos = getTabs().size() - 1;
        getTabs().add(insertPos, tab);
    }

    private void addNewAgent() {
        String backend = com.manuskript.ResourceManager.getParameter("agent.backend", "Ollama");
        String model;
        if ("OpenAI".equals(backend)) {
            model = com.manuskript.ResourceManager.getParameter("agent.openai.model", "gpt-4o-mini");
        } else {
            model = com.manuskript.ResourceManager.getParameter("agent.ollama.model", "gemma3:4b");
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

    private void saveAllConfigs() {
        List<AgentConfig> configs = new ArrayList<>();
        for (AgentTab tab : agentTabs) {
            configs.add(tab.getAgentConfig());
        }
        for (SceneWritingAgentTab tab : sceneWritingTabs) {
            configs.add(tab.getAgentConfig());
        }
        for (ChatbotAgentTab tab : chatbotTabs) {
            configs.add(tab.getAgentConfig());
        }
        AgentConfigManager.saveConfigs(configs);
    }

    public AgentTab getActiveTab() {
        Tab selected = getSelectionModel().getSelectedItem();
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
        for (Tab tab : getTabs()) {
            if (tab.getContent() == agentTab) {
                getSelectionModel().select(tab);
                return;
            }
        }
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
            tab.applyFontSize(fontSizePx);
        }
        for (SceneWritingAgentTab tab : sceneWritingTabs) {
            tab.applyFontSize(fontSizePx);
        }
        for (ChatbotAgentTab tab : chatbotTabs) {
            tab.applyFontSize(fontSizePx);
            if (themeIndex >= 0) {
                tab.applyChatTheme(themeIndex);
            }
            if (fontFamily != null && !fontFamily.isBlank()) {
                tab.applyEditorFont(fontFamily, fontSizePx);
            }
        }
    }

    public void reloadFromConfig() {
        AgentConfigManager.invalidateCache();
        getTabs().clear();
        agentTabs.clear();
        sceneWritingTabs.clear();
        chatbotTabs.clear();
        getTabs().add(addTab);
        loadFromConfig();
    }
}
