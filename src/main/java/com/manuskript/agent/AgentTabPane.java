package com.manuskript.agent;

import java.util.ArrayList;
import java.util.List;

import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

/**
 * TabPane-Container für mehrere Agenten-Tabs.
 * Ersetzt das alte AgentPanel im Editor-SplitPane.
 */
public class AgentTabPane extends TabPane {

    private final List<AgentTab> agentTabs = new ArrayList<>();
    private final Tab addTab;

    public AgentTabPane() {
        setTabClosingPolicy(TabClosingPolicy.ALL_TABS);
        getStyleClass().add("agent-tab-pane");

        // "+"-Tab zum Hinzufügen neuer Agenten
        addTab = new Tab("+");
        addTab.setClosable(false);
        addTab.getStyleClass().add("agent-add-tab");
        getTabs().add(addTab);

        // Listener für Tab-Wechsel (erkennen, ob "+"-Tab geklickt wurde)
        getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab == addTab) {
                addNewAgent();
            }
        });
    }

    /**
     * Lädt Agenten-Konfigurationen und erstellt die Tabs.
     */
    public void loadFromConfig() {
        List<AgentConfig> configs = AgentConfigManager.loadConfigs();
        for (AgentConfig config : configs) {
            addAgentTab(config, false);
        }
        if (!agentTabs.isEmpty()) {
            getSelectionModel().select(0);
        }
    }

    /**
     * Fügt einen neuen Agent-Tab hinzu.
     */
    public AgentTab addAgentTab(AgentConfig config, boolean saveConfig) {
        AgentTab agentTab = new AgentTab(config);

        // Bei Konfig-Änderungen speichern
        agentTab.setOnConfigChanged(this::saveAllConfigs);

        Tab tab = new Tab(config.getName());
        tab.setContent(agentTab);
        tab.getStyleClass().add("agent-tab-item");
        tab.setOnCloseRequest(e -> {
            if (agentTabs.size() <= 1) {
                e.consume();
                Alert alert = new Alert(Alert.AlertType.WARNING,
                    "Mindestens ein Agent muss vorhanden sein.", ButtonType.OK);
                alert.showAndWait();
                return;
            }
            agentTabs.remove(agentTab);
            saveAllConfigs();
        });

        // Tab-Namen aktualisieren wenn Agent-Name sich ändert
        agentTab.setOnConfigChanged(() -> {
            tab.setText(agentTab.getAgentConfig().getName());
            saveAllConfigs();
        });

        // Insert before the "+" tab
        int insertPos = getTabs().size() - 1;
        getTabs().add(insertPos, tab);
        agentTabs.add(agentTab);

        if (saveConfig) {
            saveAllConfigs();
        }

        getSelectionModel().select(tab);
        return agentTab;
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
        addAgentTab(defaultConfig, true);
    }

    private void saveAllConfigs() {
        List<AgentConfig> configs = new ArrayList<>();
        for (AgentTab tab : agentTabs) {
            configs.add(tab.getAgentConfig());
        }
        AgentConfigManager.saveConfigs(configs);
    }

    /**
     * Liefert den aktuell aktiven Agent-Tab.
     */
    public AgentTab getActiveTab() {
        Tab selected = getSelectionModel().getSelectedItem();
        if (selected != null && selected.getContent() instanceof AgentTab) {
            return (AgentTab) selected.getContent();
        }
        return agentTabs.isEmpty() ? null : agentTabs.get(0);
    }

    /**
     * Liefert alle Agent-Tabs.
     */
    public List<AgentTab> getAgentTabs() {
        return new ArrayList<>(agentTabs);
    }

    /**
     * Entfernt alle Tabs und lädt neu aus der Konfiguration.
     */
    public void reloadFromConfig() {
        AgentConfigManager.invalidateCache();
        getTabs().clear();
        agentTabs.clear();
        getTabs().add(addTab);
        loadFromConfig();
    }
}
