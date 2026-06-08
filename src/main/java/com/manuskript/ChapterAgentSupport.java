package com.manuskript;

import com.manuskript.agent.AIBackend;
import com.manuskript.agent.AgentConfig;
import com.manuskript.agent.AgentMemory;
import com.manuskript.agent.AgentTab;
import com.manuskript.agent.AgentTabPane;
import com.manuskript.agent.ChatbotAgent;
import com.manuskript.agent.ChatbotAgentTab;
import com.manuskript.agent.ChatbotContextBuilder;
import com.manuskript.agent.ChatbotContextConfig;
import com.manuskript.agent.OllamaBackend;
import com.manuskript.agent.OpenAIBackend;
import com.manuskript.agent.AgentAnalysisErrors;
import com.manuskript.agent.AgentSamplingParams;
import com.manuskript.agent.PlotholeAgent;
import com.manuskript.agent.SceneContextLoader;
import com.manuskript.agent.SceneWritingAgent;
import com.manuskript.agent.SceneWritingAgentTab;
import com.manuskript.agent.AgentSelectionRevisionRunner;
import com.manuskript.agent.SelectionRevisionSupport;
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
    public static final String PREF_AGENT_PANEL_VISIBLE = "chapter_editor_agent_panel_visible";

    private final ChapterEditorHost host;
    private final SplitPane mainSplitPane;
    private AgentTabPane agentTabPane;
    private final Map<String, PlotholeAgent> agentInstances = new HashMap<>();
    private final Map<String, AIBackend> agentBackends = new HashMap<>();
    private SceneOutlineWindow sceneOutlineWindow;
    private Timeline agentRealtimeTimeline;
    private boolean agentPanelVisible;
    private boolean userWantsPanelVisible = true;

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
        for (ChatbotAgentTab tab : agentTabPane.getChatbotTabs()) {
            setupChatbotTabCallbacks(tab);
        }
        userWantsPanelVisible = loadPanelVisiblePreference();
        ensurePanelVisible(userWantsPanelVisible);
        loadAgentModels();
        applyEditorAppearance();
    }

    public void applyFontSize(int size) {
        applyEditorAppearance();
    }

    public void applyEditorAppearance() {
        if (agentTabPane != null) {
            agentTabPane.applyEditorAppearance(
                    host.getEditorFontSizePx(),
                    host.getThemeIndex(),
                    host.getEditorFontFamily());
        }
    }

    public boolean isAvailable() {
        return agentTabPane != null;
    }

    public boolean isPanelVisible() {
        return agentPanelVisible;
    }

    public boolean getUserWantsPanelVisible() {
        return userWantsPanelVisible;
    }

    public void setPanelVisible(boolean visible, boolean persist) {
        if (agentTabPane == null) {
            return;
        }
        if (persist) {
            userWantsPanelVisible = visible;
            persistPanelVisible(visible);
        }
        ensurePanelVisible(visible);
    }

    public void restoreUserPanelVisibility() {
        if (agentTabPane != null) {
            ensurePanelVisible(userWantsPanelVisible);
        }
    }

    private static boolean loadPanelVisiblePreference() {
        return Preferences.userNodeForPackage(ChapterAgentSupport.class)
                .getBoolean(PREF_AGENT_PANEL_VISIBLE, true);
    }

    private static void persistPanelVisible(boolean visible) {
        Preferences.userNodeForPackage(ChapterAgentSupport.class)
                .putBoolean(PREF_AGENT_PANEL_VISIBLE, visible);
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
        if (visible && agentScrollPane != null) {
            int idx = items.indexOf(agentScrollPane);
            items.remove(agentScrollPane);
            if (!items.contains(agentTabPane)) {
                agentTabPane.setMinWidth(220);
                agentTabPane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
                items.add(idx >= 0 ? idx : items.size(), agentTabPane);
            }
            ChapterEditorSplitPreferences.apply(mainSplitPane);
            agentPanelVisible = true;
            return;
        }
        boolean hasPanel = agentScrollPane != null || items.contains(agentTabPane);
        if (visible && !hasPanel) {
            agentTabPane.setMinWidth(220);
            agentTabPane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            items.add(agentTabPane);
            ChapterEditorSplitPreferences.apply(mainSplitPane);
        } else if (!visible && hasPanel) {
            if (agentScrollPane != null) {
                items.remove(agentScrollPane);
            } else {
                items.remove(agentTabPane);
            }
            ChapterEditorSplitPreferences.apply(mainSplitPane);
        }
        agentPanelVisible = visible;
    }

    private void setupAgentTabCallbacks(AgentTab tab) {
        if (tab.getAgentConfig().isSelectionRevisionAgent()) {
            tab.setRealtimeEnabled(false);
            tab.setOnAnalyzeClicked(() -> runSelectionRevision(tab));
        } else {
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
        }
        tab.setOnQuoteClicked(quote -> ChapterAgentQuoteActions.jumpToQuote(host, quote));
        tab.setOnSuggestionClicked(finding -> ChapterAgentQuoteActions.replaceWithSuggestion(host, finding));
    }

    /** Überarbeiten-Agent für die aktuelle Editor-Markierung (Kontextmenü → mit Dialog). */
    public void runSelectionRevisionFromContextMenu() {
        if (agentTabPane == null) {
            host.updateStatus("Agenten-Panel nicht verfügbar.");
            return;
        }
        if (!host.hasTextSelection()) {
            host.updateStatus("Bitte zuerst Text markieren (max. "
                    + SelectionRevisionSupport.maxSelectionChars() + " Zeichen).");
            return;
        }
        int start = host.getSelectionStart();
        int end = host.getSelectionEnd();
        if (start > end) {
            int tmp = start;
            start = end;
            end = tmp;
        }
        String fullText = host.getText() != null ? host.getText() : "";
        if (start < 0 || end > fullText.length() || start >= end) {
            host.updateStatus("Ungültige Textmarkierung.");
            return;
        }
        String selected = fullText.substring(start, end);
        if (selected.trim().isEmpty()) {
            host.updateStatus("Die Markierung ist leer.");
            return;
        }
        if (selected.length() > SelectionRevisionSupport.maxSelectionChars()) {
            host.updateStatus("Markierung zu lang (max. "
                    + SelectionRevisionSupport.maxSelectionChars() + " Zeichen).");
            return;
        }
        AgentTab revisionTab = SelectionRevisionSupport.findRevisionTab(agentTabPane);
        String defaultInstruction = revisionTab != null
                ? revisionTab.getRevisionInstruction()
                : SelectionRevisionDialog.loadPersistedInstruction();
        SelectionRevisionDialog.show(
                host,
                host.getStage(),
                host.getThemeIndex(),
                selected,
                defaultInstruction,
                instruction -> {
                    if (revisionTab != null) {
                        revisionTab.setRevisionInstruction(instruction);
                    }
                    runSelectionRevisionWithInstruction(revisionTab, instruction);
                });
    }

    /** Überarbeiten-Agent für die aktuelle Editor-Markierung (Tab ▶). */
    public void runSelectionRevision() {
        runSelectionRevision(null);
    }

    public void runSelectionRevision(AgentTab explicitTab) {
        AgentTab tab = explicitTab != null ? explicitTab : SelectionRevisionSupport.findRevisionTab(agentTabPane);
        String instruction = tab != null ? tab.getRevisionInstruction() : SelectionRevisionDialog.loadPersistedInstruction();
        runSelectionRevisionWithInstruction(tab, instruction);
    }

    private void runSelectionRevisionWithInstruction(AgentTab explicitTab, String instruction) {
        if (agentTabPane == null) {
            host.updateStatus("Agenten-Panel nicht verfügbar.");
            return;
        }
        AgentSelectionRevisionRunner.run(
                host,
                agentTabPane,
                agentInstances,
                agentBackends,
                this::resolveProjectDir,
                () -> ensurePanelVisible(true),
                explicitTab,
                instruction);
    }

    private void setupSceneWritingTabCallbacks(SceneWritingAgentTab tab) {
        tab.setOnInsertClicked(host::insertTextAtCaret);
        tab.setGenerationHandler((instruction, contextSize, useParameterModel, overrideModel, onStatus, onComplete, onError) -> {
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
                    sceneOutlineText,
                    contextSize);
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
            AgentSamplingParams.applyAgentConfig(backend, config);
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

    private void setupChatbotTabCallbacks(ChatbotAgentTab tab) {
        tab.setOnInsertClicked(host::insertTextAtCaret);
        tab.setProjectProvider(() -> resolveProjectDir());
        tab.setMessageHandler((userMessage, historyBeforeSend, contextConfig, contextSize,
                               useParameterModel, overrideModel, temperature, onComplete, onError) -> {
            MainController main = host.getMainController();
            File docx = host.getOriginalDocxFile();
            File mdFile = host.asCanvasChapterEditor() != null
                    ? host.asCanvasChapterEditor().getLoadedChapterFile() : null;
            if (host.asLegacyEditorWindow() != null) {
                mdFile = host.asLegacyEditorWindow().getCurrentFile();
            }
            java.util.List<DocxFile> chapterOrder = main != null
                    ? main.getSelectedDocxFilesAsDocxFiles() : java.util.List.of();
            ChatbotContextConfig cfg = contextConfig != null ? contextConfig : new ChatbotContextConfig();
            if (contextSize != null) {
                cfg.setContextSize(contextSize);
            }
            String contextBlock = ChatbotContextBuilder.build(
                    resolveProjectDir(), main, host.getEditorKey(), host.getText(),
                    mdFile, docx, chapterOrder, cfg);
            AgentConfig config = tab.getAgentConfig();
            AIBackend backend = createGenerationBackend(useParameterModel, overrideModel, config);
            if (backend == null) {
                onError.accept(new IllegalStateException("Backend nicht verfügbar"));
                return null;
            }
            backend.setTemperature(temperature);
            AgentSamplingParams.applyAgentConfig(backend, config);
            ChatbotAgent agent = new ChatbotAgent(backend);
            agent.setSystemPrompt(config.getSystemPrompt());
            int maxTokens = config.getMaxTokens() > 0 ? config.getMaxTokens() : 4096;
            int maxHistory = ChatbotAgent.defaultMaxHistoryTurns();
            agent.sendMessage(contextBlock, historyBeforeSend, userMessage, maxHistory, maxTokens)
                    .thenAccept(onComplete)
                    .exceptionally(ex -> {
                        onError.accept(ex);
                        return null;
                    });
            return null;
        });
        tab.refreshProjectBinding();
        tab.applyChatTheme(host.getThemeIndex());
        tab.applyEditorFont(host.getEditorFontFamily(), host.getEditorFontSizePx());
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
        AgentConfig config = targetTab.getAgentConfig();
        agent.setSystemPrompt(config.getSystemPrompt());
        AIBackend backend = agentBackends.get(targetTab.getAgentId());
        if (backend != null) {
            AgentSamplingParams.applyAgentConfig(backend, config);
        }
        targetTab.setAnalyzing(true);
        String text = host.getText() != null ? host.getText() : "";
        boolean includeAllChapters = Boolean.parseBoolean(
                ResourceManager.getParameter("agent.plothole.include_all_chapters", "true"));
        String allChapters = "";
        MainController main = host.getMainController();
        if (includeAllChapters && main != null) {
            allChapters = main.loadAllChaptersExcluding(host.getEditorKey());
        }
        int maxOutputTokens = targetTab.getAgentConfig().getMaxTokens();
        agent.analyze(text, allChapters, maxOutputTokens)
                .thenAccept(targetTab::showParseResult)
                .exceptionally(ex -> {
                    String detail = AgentAnalysisErrors.format(ex);
                    logger.error("Plothole-Analyse fehlgeschlagen (Modell={}, Backend={}): {}",
                            model, targetTab.getAgentConfig().getBackend(), detail,
                            AgentAnalysisErrors.unwrap(ex));
                    targetTab.showError(detail);
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
            for (ChatbotAgentTab tab : agentTabPane.getChatbotTabs()) {
                tab.setModels(models);
            }
        }));
    }
}
