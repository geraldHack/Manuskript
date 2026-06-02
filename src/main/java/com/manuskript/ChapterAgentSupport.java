package com.manuskript;

import com.manuskript.agent.AIBackend;
import com.manuskript.agent.AgentConfig;
import com.manuskript.agent.AgentMemory;
import com.manuskript.agent.AgentTab;
import com.manuskript.agent.AgentTabPane;
import com.manuskript.agent.OllamaBackend;
import com.manuskript.agent.OpenAIBackend;
import com.manuskript.agent.PlotholeAgent;
import com.manuskript.agent.SceneContextLoader;
import com.manuskript.agent.SceneWritingAgent;
import com.manuskript.agent.SceneWritingAgentTab;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.prefs.Preferences;

/**
 * Agenten-Panel für einen {@link ChapterEditorHost}.
 */
public class ChapterAgentSupport {

    private static final Logger logger = LoggerFactory.getLogger(ChapterAgentSupport.class);

    private final ChapterEditorHost host;
    private final SplitPane mainSplitPane;
    private AgentTabPane agentTabPane;
    private final Map<String, PlotholeAgent> agentInstances = new HashMap<>();
    private final Map<String, AIBackend> agentBackends = new HashMap<>();
    private SceneOutlineWindow sceneOutlineWindow;
    private Timeline agentRealtimeTimeline;
    private boolean agentPanelVisible;

    public ChapterAgentSupport(ChapterEditorHost host, SplitPane mainSplitPane) {
        this.host = host;
        this.mainSplitPane = mainSplitPane;
    }

    public void setSceneOutlineWindow(SceneOutlineWindow sceneOutlineWindow) {
        this.sceneOutlineWindow = sceneOutlineWindow;
    }

    public void setupIfEnabled() {
        boolean agentEnabled = Boolean.parseBoolean(
                ResourceManager.getParameter("agent.enabled", "true"));
        if (!agentEnabled) {
            return;
        }
        agentTabPane = new AgentTabPane();
        agentTabPane.loadFromConfig();
        for (AgentTab tab : agentTabPane.getAgentTabs()) {
            setupAgentTabCallbacks(tab);
        }
        for (SceneWritingAgentTab tab : agentTabPane.getSceneWritingTabs()) {
            setupSceneWritingTabCallbacks(tab);
        }
        ensurePanelVisible(true);
        loadAgentModels();
        agentTabPane.applyFontSize(Preferences.userNodeForPackage(EditorWindow.class).getInt("fontSize", 12));
    }

    public void ensurePanelVisible(boolean visible) {
        if (mainSplitPane == null || agentTabPane == null) {
            return;
        }
        ObservableList<Node> items = mainSplitPane.getItems();
        ScrollPane agentScrollPane = null;
        for (Node node : items) {
            if (node instanceof ScrollPane scroll && scroll.getContent() == agentTabPane) {
                agentScrollPane = scroll;
                break;
            }
        }
        boolean hasPanel = agentScrollPane != null || items.contains(agentTabPane);
        if (visible && !hasPanel) {
            agentScrollPane = new ScrollPane(agentTabPane);
            agentScrollPane.setFitToWidth(true);
            agentScrollPane.setFitToHeight(true);
            items.add(agentScrollPane);
            double[] current = mainSplitPane.getDividerPositions();
            if (current.length >= 2) {
                mainSplitPane.setDividerPositions(current[0], 0.72, 0.85);
            } else if (current.length >= 1) {
                mainSplitPane.setDividerPositions(current[0], 0.85);
            }
        } else if (!visible && hasPanel) {
            if (agentScrollPane != null) {
                items.remove(agentScrollPane);
            } else {
                items.remove(agentTabPane);
            }
        }
        agentPanelVisible = visible;
    }

    private void setupAgentTabCallbacks(AgentTab tab) {
        tab.setOnAnalyzeClicked(() -> runAgentAnalysis(tab));
        boolean realtimeEnabled = Boolean.parseBoolean(
                ResourceManager.getParameter("agent.realtime_enabled", "false"));
        tab.setRealtimeEnabled(realtimeEnabled);
        tab.setOnRealtimeToggled(enabled -> {
            if (enabled) {
                triggerRealtimeCheck();
            } else if (agentRealtimeTimeline != null) {
                agentRealtimeTimeline.stop();
                agentRealtimeTimeline = null;
            }
        });
        tab.setOnQuoteClicked(quote -> ChapterAgentQuoteActions.jumpToQuote(host, quote));
        tab.setOnSuggestionClicked(finding -> ChapterAgentQuoteActions.replaceWithSuggestion(host, finding));
    }

    private void setupSceneWritingTabCallbacks(SceneWritingAgentTab tab) {
        tab.setOnInsertClicked(host::insertTextAtCaret);
        tab.setGenerationHandler((instruction, useParameterModel, overrideModel, onStatus, onComplete, onError) -> {
            File docx = host.getOriginalDocxFile();
            String sceneOutlineText = sceneOutlineWindow != null && docx != null
                    ? sceneOutlineWindow.getOutlineTextForDocx(docx) : null;
            MainController main = host.getMainController();
            String projectDir = main != null && main.getProjectRootDirectory() != null
                    ? main.getProjectRootDirectory().getAbsolutePath() : null;
            File mdFile = host.asCanvasChapterEditor() != null
                    ? host.asCanvasChapterEditor().getLoadedChapterFile()
                    : null;
            if (host.asLegacyEditorWindow() != null) {
                mdFile = host.asLegacyEditorWindow().getCurrentFile();
            }
            java.util.List<DocxFile> chapterOrder = main != null
                    ? main.getSelectedDocxFilesAsDocxFiles() : java.util.List.of();
            SceneContextLoader.Context ctx = SceneContextLoader.load(
                    projectDir != null ? new File(projectDir) : null,
                    docx,
                    mdFile,
                    host.getText(),
                    chapterOrder,
                    instruction,
                    sceneOutlineText);
            if (ctx.targetSceneNumber != null && ctx.targetScene.isBlank()) {
                return "Szene " + ctx.targetSceneNumber + " nicht in der Outline gefunden";
            }
            if (ctx.sceneOutline.isBlank()) {
                return "Keine Szenen-Outline für dieses Kapitel gefunden";
            }
            AgentConfig config = tab.getAgentConfig();
            AIBackend backend = createGenerationBackend(useParameterModel, overrideModel, config);
            if (backend == null) {
                onError.accept(new IllegalStateException("Backend nicht verfügbar"));
                return null;
            }
            backend.setTemperature(config.getTemperature());
            SceneWritingAgent agent = new SceneWritingAgent(backend);
            agent.setSystemPrompt(config.getSystemPrompt());
            int maxTokens = config.getMaxTokens() > 0 ? config.getMaxTokens() : 4096;
            agent.generate(ctx, maxTokens).thenAccept(onComplete).exceptionally(ex -> {
                onError.accept(ex);
                return null;
            });
            return null;
        });
    }

    private void runAgentAnalysis(AgentTab tab) {
        final AgentTab targetTab = tab != null ? tab
                : (agentTabPane != null ? agentTabPane.getActiveTab() : null);
        if (targetTab == null) {
            return;
        }
        String model = targetTab.getAgentConfig().getModel();
        if (model == null || model.isBlank()) {
            targetTab.showError("Kein Modell gewählt");
            return;
        }
        PlotholeAgent agent = getOrCreateAgentForTab(targetTab);
        if (agent == null) {
            return;
        }
        agent.setSystemPrompt(targetTab.getAgentConfig().getSystemPrompt());
        targetTab.setAnalyzing(true);
        String text = host.getText();
        String allChapters = "";
        MainController main = host.getMainController();
        if (main != null) {
            allChapters = main.loadAllChapters();
        }
        agent.analyze(text, allChapters)
                .thenAccept(targetTab::showFindings)
                .exceptionally(ex -> {
                    targetTab.showError(ex.getMessage());
                    return null;
                });
    }

    private PlotholeAgent getOrCreateAgentForTab(AgentTab tab) {
        String agentId = tab.getAgentId();
        PlotholeAgent agent = agentInstances.get(agentId);
        if (agent != null) {
            return agent;
        }
        File projectDir = resolveProjectDir();
        if (projectDir == null) {
            return null;
        }
        AIBackend backend = agentBackends.get(agentId);
        if (backend == null) {
            backend = createGenerationBackend(true, null, tab.getAgentConfig());
            agentBackends.put(agentId, backend);
        }
        String chapterName = host.getEditorKey();
        AgentMemory memory = new AgentMemory(projectDir, "agent_" + agentId, chapterName);
        agent = new PlotholeAgent(backend, memory);
        agent.setSystemPrompt(tab.getAgentConfig().getSystemPrompt());
        agentInstances.put(agentId, agent);
        return agent;
    }

    private File resolveProjectDir() {
        MainController main = host.getMainController();
        if (main != null && main.getProjectRootDirectory() != null) {
            return main.getProjectRootDirectory();
        }
        File md = host.asCanvasChapterEditor() != null
                ? host.asCanvasChapterEditor().getLoadedChapterFile() : null;
        if (md != null && md.getParentFile() != null) {
            File data = md.getParentFile();
            return data.getParentFile() != null ? data.getParentFile() : data;
        }
        return new File(System.getProperty("user.dir"));
    }

    private AIBackend createGenerationBackend(boolean useParameterModel, String overrideModel, AgentConfig config) {
        AIBackend backend = "OpenAI".equals(config.getBackend()) ? new OpenAIBackend() : new OllamaBackend(new OllamaService());
        String model = useParameterModel ? config.getModel() : overrideModel;
        if (model != null && !model.isBlank()) {
            backend.setCurrentModel(model.trim());
        }
        return backend;
    }

    private void triggerRealtimeCheck() {
        if (agentTabPane == null || !agentPanelVisible) {
            return;
        }
        if (agentRealtimeTimeline != null) {
            agentRealtimeTimeline.stop();
        }
        int debounceMs = Integer.parseInt(ResourceManager.getParameter("agent.realtime_debounce_ms", "2000"));
        agentRealtimeTimeline = new Timeline(new KeyFrame(Duration.millis(debounceMs), event -> {
            AgentTab currentTab = agentTabPane.getActiveTab();
            if (currentTab != null && currentTab.isRealtimeEnabled() && !currentTab.isAnalyzing()) {
                runAgentAnalysis(currentTab);
            }
        }));
        agentRealtimeTimeline.play();
    }

    private void loadAgentModels() {
        if (agentTabPane == null) {
            return;
        }
        CompletableFuture.supplyAsync(() -> {
            try {
                AIBackend backend = agentBackends.isEmpty()
                        ? ("OpenAI".equals(ResourceManager.getParameter("agent.backend", "Ollama"))
                        ? new OpenAIBackend() : new OllamaBackend(new OllamaService()))
                        : agentBackends.values().iterator().next();
                return backend.getAvailableModels();
            } catch (Exception e) {
                return java.util.Arrays.asList("gemma3:4b", "mistral:7b-instruct", "llama3.1:8b-instruct");
            }
        }).thenAccept(models -> Platform.runLater(() -> {
            if (agentTabPane == null) {
                return;
            }
            for (AgentTab tab : agentTabPane.getAgentTabs()) {
                tab.setModels(models);
            }
            for (SceneWritingAgentTab tab : agentTabPane.getSceneWritingTabs()) {
                tab.setModels(models);
            }
        }));
    }
}
