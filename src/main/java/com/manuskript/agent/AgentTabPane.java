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
    private final Tab addTab;

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
        AgentConfigManager.saveConfigs(configs);
    }

    public AgentTab getActiveTab() {
        Tab selected = getSelectionModel().getSelectedItem();
        if (selected != null && selected.getContent() instanceof AgentTab) {
            return (AgentTab) selected.getContent();
        }
        return agentTabs.isEmpty() ? null : agentTabs.get(0);
    }

    public List<AgentTab> getAgentTabs() {
        return new ArrayList<>(agentTabs);
    }

    public List<SceneWritingAgentTab> getSceneWritingTabs() {
        return new ArrayList<>(sceneWritingTabs);
    }

    public void applyFontSize(int size) {
        for (SceneWritingAgentTab tab : sceneWritingTabs) {
            tab.applyFontSize(size);
        }
    }

    public void reloadFromConfig() {
        AgentConfigManager.invalidateCache();
        getTabs().clear();
        agentTabs.clear();
        sceneWritingTabs.clear();
        getTabs().add(addTab);
        loadFromConfig();
    }
}
