package com.manuskript;

import com.manuskript.DocxFile;
import com.manuskript.MainController;
import com.manuskript.ResourceManager;
import com.manuskript.agent.AIBackend;
import com.manuskript.agent.AgentConfig;
import com.manuskript.agent.AgentConfigManager;
import com.manuskript.agent.AgentMemory;
import com.manuskript.agent.AgentTab;
import com.manuskript.agent.AgentTabPane;
import com.manuskript.agent.ChatbotAgent;
import com.manuskript.agent.ChatbotAgentTab;
import com.manuskript.agent.ChatbotContextBuilder;
import com.manuskript.agent.ChatbotContextConfig;
import com.manuskript.agent.ChatbotContextSize;
import com.manuskript.agent.ChatbotContextSource;
import com.manuskript.agent.OllamaBackend;
import com.manuskript.agent.OpenAIBackend;
import com.manuskript.agent.AgentActivityTracker;
import com.manuskript.agent.AgentAnalysisErrors;
import com.manuskript.agent.AgentSamplingParams;
import com.manuskript.agent.PlotholeAgent;
import com.manuskript.agent.SceneContextLoader;
import com.manuskript.agent.SceneContextSize;
import com.manuskript.agent.SceneWritingAgent;
import com.manuskript.agent.SceneWritingAgentTab;
import com.manuskript.agent.AgentIdiomReviewRunner;
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
    private AgentActivityTracker activityTracker;

    public ChapterAgentSupport(ChapterEditorHost host, SplitPane mainSplitPane) {
        this.host = host;
        this.mainSplitPane = mainSplitPane;
    }

    public void setSceneOutlineWindow(SceneOutlineWindow sceneOutlineWindow) {
        this.sceneOutlineWindow = sceneOutlineWindow;
    }

    public void setActivityTracker(AgentActivityTracker tracker) {
        this.activityTracker = tracker;
        if (agentTabPane != null) {
            agentTabPane.setActivityTracker(tracker);
        }
    }

    public void setupIfEnabled() {
        if (!FeaturePacks.agentsEnabled()) {
            return;
        }
        agentTabPane = new AgentTabPane();
        if (activityTracker != null) {
            agentTabPane.setActivityTracker(activityTracker);
        }
        agentTabPane.setOnAnalysisTabCreated(tab -> {
            setupAgentTabCallbacks(tab);
            wireAgentTabStatus(tab);
            applyEditorAppearance();
            loadAgentModels();
        });
        agentTabPane.loadFromConfig();
        for (AgentTab tab : agentTabPane.getAgentTabs()) {
            setupAgentTabCallbacks(tab);
            wireAgentTabStatus(tab);
        }
        for (SceneWritingAgentTab tab : agentTabPane.getSceneWritingTabs()) {
            setupSceneWritingTabCallbacks(tab);
            wireAgentTabStatus(tab);
        }
        for (ChatbotAgentTab tab : agentTabPane.getChatbotTabs()) {
            setupChatbotTabCallbacks(tab);
            wireAgentTabStatus(tab);
        }
        userWantsPanelVisible = loadPanelVisiblePreference();
        ensurePanelVisible(userWantsPanelVisible);
        loadAgentModels();
        applyEditorAppearance();
        Platform.runLater(this::applyEditorAppearance);
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

    /**
     * Parameter (Backend/URL/Modell) neu einlesen: Tabs neu aufbauen, Agent-Instanzen verwerfen.
     */
    public void reloadAgentParameters() {
        if (agentTabPane == null) {
            return;
        }
        Runnable reload = () -> {
            agentInstances.clear();
            agentBackends.clear();
            AgentConfigManager.invalidateCache();
            java.util.List<AgentConfig> configs = AgentConfigManager.loadConfigs();
            AgentConfigManager.saveConfigs(configs);
            boolean keepVisible = agentPanelVisible || userWantsPanelVisible;
            agentTabPane.reloadFromConfig();
            for (AgentTab tab : agentTabPane.getAgentTabs()) {
                setupAgentTabCallbacks(tab);
                wireAgentTabStatus(tab);
            }
            for (SceneWritingAgentTab tab : agentTabPane.getSceneWritingTabs()) {
                setupSceneWritingTabCallbacks(tab);
                wireAgentTabStatus(tab);
            }
            for (ChatbotAgentTab tab : agentTabPane.getChatbotTabs()) {
                setupChatbotTabCallbacks(tab);
                wireAgentTabStatus(tab);
            }
            loadAgentModels();
            applyEditorAppearance();
            ensurePanelVisible(keepVisible);
            logger.info("Agenten-Parameter neu geladen (Backend={}, Modell={})",
                    ResourceManager.getParameter("agent.backend", "Ollama"),
                    ResourceManager.getParameter("agent.openai.model", ""));
        };
        if (Platform.isFxApplicationThread()) {
            reload.run();
        } else {
            Platform.runLater(reload);
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
            applyEditorAppearance();
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
        if (visible) {
            applyEditorAppearance();
        }
    }

    private void setupAgentTabCallbacks(AgentTab tab) {
        if (tab.getAgentConfig().isSelectionRevisionAgent()) {
            tab.setRealtimeEnabled(false);
            tab.setOnAnalyzeClicked(() -> runSelectionRevision(tab));
        } else if (tab.getAgentConfig().isIdiomReviewAgent()) {
            tab.setRealtimeEnabled(false);
            tab.setOnAnalyzeClicked(() -> runIdiomReview(tab));
            tab.setOnApplyRewriteClicked(() -> applyIdiomRewrite(tab));
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

    private void wireAgentTabStatus(AgentTab tab) {
        tab.setOnStatus(host::updateStatus);
        tab.setOnStatusError(host::updateStatusError);
        bindActivityTracker(tab);
    }

    private void wireAgentTabStatus(SceneWritingAgentTab tab) {
        tab.setOnStatus(host::updateStatus);
        tab.setOnStatusError(host::updateStatusError);
        bindActivityTracker(tab);
    }

    private void wireAgentTabStatus(ChatbotAgentTab tab) {
        tab.setOnStatus(host::updateStatus);
        tab.setOnStatusError(host::updateStatusError);
        bindActivityTracker(tab);
    }

    private void bindActivityTracker(AgentTab tab) {
        if (activityTracker != null) {
            tab.bindActivityTracker(activityTracker);
        }
    }

    private void bindActivityTracker(SceneWritingAgentTab tab) {
        if (activityTracker != null) {
            tab.bindActivityTracker(activityTracker);
        }
    }

    private void bindActivityTracker(ChatbotAgentTab tab) {
        if (activityTracker != null) {
            tab.bindActivityTracker(activityTracker);
        }
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
            int maxChars = SelectionRevisionSupport.maxSelectionChars();
            String msg = "Markierung zu lang: " + selected.length() + " Zeichen (max. " + maxChars + ").";
            host.updateStatus(msg);
            AgentTab revisionTab = SelectionRevisionSupport.findRevisionTab(agentTabPane);
            if (revisionTab != null) {
                revisionTab.showError(msg);
            }
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
        applyEditorAppearance();
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

    /** Sprachentflechtung für die aktuelle Editor-Markierung (Kontextmenü). */
    public void runIdiomReviewFromContextMenu() {
        runIdiomReview(null);
    }

    public void runIdiomReview() {
        runIdiomReview(null);
    }

    public void runIdiomReview(AgentTab explicitTab) {
        if (agentTabPane == null) {
            host.updateStatus("Agenten-Panel nicht verfügbar.");
            return;
        }
        applyEditorAppearance();
        AgentIdiomReviewRunner.run(
                host,
                agentTabPane,
                agentInstances,
                agentBackends,
                this::resolveProjectDir,
                () -> ensurePanelVisible(true),
                explicitTab);
    }

    private void applyIdiomRewrite(AgentTab tab) {
        if (tab == null) {
            return;
        }
        String text = tab.getRewriteText();
        if (text == null || text.isBlank()) {
            host.updateStatus("Kein überarbeiteter Text zum Übernehmen.");
            return;
        }
        int start = tab.getRevisionSelectionStart();
        int end = tab.getRevisionSelectionEnd();
        if (start < 0 || end <= start) {
            host.updateStatus("Keine gültige Markierung — bitte erneut analysieren.");
            return;
        }
        host.replaceRangePreserveView(start, end, ChapterAgentQuoteActions.prepareReplacementText(text, host.getQuoteStyleIndex()));
        host.requestEditorFocus();
        host.updateStatus("Markierung übernommen.");
    }

    private void setupSceneWritingTabCallbacks(SceneWritingAgentTab tab) {
        tab.setOnInsertClicked(host::insertTextAtCaret);
        tab.setGenerationHandler((instruction, contextSize, useParameterModel, overrideModel, onStatus, onComplete, onError) ->
                startSceneWritingRequest(tab, instruction, contextSize, useParameterModel, overrideModel,
                        onStatus, onComplete, onError, false, null, null));
        tab.setRevisionHandler((instruction, draft, feedback, contextSize, useParameterModel, overrideModel,
                                onStatus, onComplete, onError) ->
                startSceneWritingRequest(tab, instruction, contextSize, useParameterModel, overrideModel,
                        onStatus, onComplete, onError, true, draft, feedback));
    }

    private String startSceneWritingRequest(
            SceneWritingAgentTab tab,
            String instruction,
            SceneContextSize contextSize,
            boolean useParameterModel,
            String overrideModel,
            java.util.function.Consumer<String> onStatus,
            java.util.function.Consumer<SceneWritingAgent.GenerationResult> onComplete,
            java.util.function.Consumer<Throwable> onError,
            boolean revision,
            String draft,
            String feedback) {
        SceneContextLoadResult loaded = loadSceneWritingContext(instruction, contextSize);
        if (loaded.errorMessage != null) {
            return loaded.errorMessage;
        }
        SceneContextLoader.Context ctx = loaded.context;
        if (ctx.sceneOutline.isBlank()) {
            onStatus.accept("Hinweis: Keine Szenen-Outline — generiere aus Anweisung und Kapitelkontext.");
            logger.warn("Szene {} ohne Szenen-Outline ({} Zeichen Kapitel)",
                    revision ? "überarbeiten" : "generieren",
                    ctx.currentChapter != null ? ctx.currentChapter.length() : 0);
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
        int maxTokens = config.getMaxTokens() > 0 ? config.getMaxTokens() : 16384;
        int timeoutSec = OpenAIBackend.requestTimeoutSeconds();
        String model = backend.getCurrentModel();
        if (revision) {
            logger.info("Szene überarbeiten: Modell={}, max_tokens={}, API-Timeout={}s, Entwurf={} Zeichen",
                    model, maxTokens, timeoutSec, draft != null ? draft.length() : 0);
            onStatus.accept("Überarbeite Szene… (API-Timeout: " + timeoutSec + " s)");
            agent.revise(ctx, draft, feedback, maxTokens).thenAccept(onComplete).exceptionally(ex -> {
                onError.accept(ex);
                return null;
            });
        } else {
            logger.info("Szene generieren: Modell={}, max_tokens={}, API-Timeout={}s, Kontext={} Zeichen Kapitel",
                    model, maxTokens, timeoutSec, ctx.currentChapter != null ? ctx.currentChapter.length() : 0);
            onStatus.accept("Generiere Szene… (API-Timeout: " + timeoutSec + " s)");
            agent.generate(ctx, maxTokens).thenAccept(onComplete).exceptionally(ex -> {
                onError.accept(ex);
                return null;
            });
        }
        return null;
    }

    private record SceneContextLoadResult(SceneContextLoader.Context context, String errorMessage) {}

    private SceneContextLoadResult loadSceneWritingContext(String instruction, SceneContextSize contextSize) {
        File docx = host.getOriginalDocxFile();
        String sceneOutlineText = sceneOutlineWindow != null && docx != null
                ? sceneOutlineWindow.getOutlineTextForDocx(docx) : null;
        if (sceneOutlineText != null && sceneOutlineText.isBlank()) {
            sceneOutlineText = null;
        }
        MainController main = host.getMainController();
        File projectDir = resolveProjectDir();
        File mdFile = host.asCanvasChapterEditor() != null
                ? host.asCanvasChapterEditor().getLoadedChapterFile()
                : null;
        if (host.asLegacyEditorWindow() != null) {
            mdFile = host.asLegacyEditorWindow().getCurrentFile();
        }
        java.util.List<DocxFile> chapterOrder = main != null
                ? main.getSelectedDocxFilesAsDocxFiles() : java.util.List.of();
        SceneContextLoader.Context ctx = SceneContextLoader.load(
                projectDir,
                docx,
                mdFile,
                host.getText(),
                chapterOrder,
                instruction,
                sceneOutlineText,
                contextSize);
        if (ctx.targetSceneNumber != null && ctx.targetScene.isBlank()) {
            logger.warn("Szene {} nicht in Outline gefunden ({} Zeichen Outline)",
                    ctx.targetSceneNumber, ctx.sceneOutline.length());
            return new SceneContextLoadResult(ctx,
                    "Szene " + ctx.targetSceneNumber + " nicht in der Outline gefunden");
        }
        return new SceneContextLoadResult(ctx, null);
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
            if (cfg.hasSource(ChatbotContextSource.ALL_CHAPTERS)
                    && !contextBlock.contains("=== ALLE KAPITEL ===")) {
                logger.warn("Chatbot: ‚Alle Kapitel‘ aktiv, aber keine Kapitel-MDs geladen ({} Einträge in der Kapitelliste). "
                        + "MD-Dateien liegen unter <docx-Ordner>/data/.", chapterOrder.size());
            } else if (!contextBlock.isBlank()) {
                logger.debug("Chatbot-Kontext: {} Zeichen (Quellen: {})", contextBlock.length(), cfg.getSources());
            }
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
            int maxTokens = resolveChatbotMaxTokens(config, contextSize);
            int maxHistory = ChatbotAgent.defaultMaxHistoryTurns();
            logger.info("Chatbot: Modell={}, max_tokens={}, Kontextgröße={}, Kontext={} Zeichen",
                    backend.getCurrentModel(), maxTokens, contextSize,
                    contextBlock != null ? contextBlock.length() : 0);
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

    /**
     * Ausgabe-Budget für Chat: bei großem Kontext (Alles/Mehr) deutlich mehr Tokens,
     * damit komplexe Antworten nicht an max_tokens abbrechen.
     */
    private static int resolveChatbotMaxTokens(AgentConfig config, ChatbotContextSize size) {
        int base = config != null && config.getMaxTokens() > 0 ? config.getMaxTokens() : 4096;
        int fromParam = ResourceManager.getIntParameter("agent.chatbot.max_tokens", -1);
        if (fromParam > 0) {
            base = Math.max(base, fromParam);
        }
        ChatbotContextSize effective = size != null ? size : ChatbotContextSize.COMPACT;
        return switch (effective) {
            case FULL -> Math.max(base, 16384);
            case EXTENDED -> Math.max(base, 8192);
            case COMPACT -> base;
        };
    }

    private void runAgentAnalysis(AgentTab tab) {
        applyEditorAppearance();
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
        String allChapters = buildAnalysisContext(targetTab, text);
        int maxOutputTokens = targetTab.getAgentConfig().getMaxTokens();
        String agentName = config.getName() != null ? config.getName() : "Agent";
        logger.info("{}: Manuskript={} Zeichen, Kontext={} Zeichen, max_output_tokens={}",
                agentName, text.length(), allChapters.length(), maxOutputTokens);
        if (config.isFreeform()) {
            agent.analyzeRaw(text, allChapters, maxOutputTokens, null, targetTab::appendFreeformDelta, true)
                    .thenAccept(targetTab::finishFreeformAnalysis)
                    .exceptionally(ex -> {
                        String detail = AgentAnalysisErrors.format(ex);
                        logger.error("{} fehlgeschlagen (Modell={}, Backend={}): {}",
                                agentName, model, targetTab.getAgentConfig().getBackend(), detail,
                                AgentAnalysisErrors.unwrap(ex));
                        targetTab.showError(detail);
                        return null;
                    });
            return;
        }
        agent.analyze(text, allChapters, maxOutputTokens, null, targetTab::appendLiveFinding)
                .thenAccept(targetTab::finishLiveAnalysis)
                .exceptionally(ex -> {
                    String detail = AgentAnalysisErrors.format(ex);
                    logger.error("{} fehlgeschlagen (Modell={}, Backend={}): {}",
                            agentName, model, targetTab.getAgentConfig().getBackend(), detail,
                            AgentAnalysisErrors.unwrap(ex));
                    targetTab.showError(detail);
                    return null;
                });
    }

    private String buildAnalysisContext(AgentTab targetTab, String editorText) {
        MainController main = host.getMainController();
        File projectDir = resolveProjectDir();
        File docx = host.getOriginalDocxFile();
        File mdFile = host.asCanvasChapterEditor() != null
                ? host.asCanvasChapterEditor().getLoadedChapterFile() : null;
        if (host.asLegacyEditorWindow() != null) {
            mdFile = host.asLegacyEditorWindow().getCurrentFile();
        }
        java.util.List<DocxFile> chapterOrder = main != null
                ? main.getSelectedDocxFilesAsDocxFiles() : java.util.List.of();

        ChatbotContextConfig contextConfig = targetTab.getContextConfig();
        if (contextConfig == null) {
            contextConfig = new ChatbotContextConfig();
            contextConfig.addSource(ChatbotContextSource.WORLD_EDITOR);
        }
        return ChatbotContextBuilder.build(
                projectDir, main, host.getEditorKey(), editorText,
                mdFile, docx, chapterOrder, contextConfig);
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

    /**
     * Verzeichnis des aktuell geöffneten Buchs (DOCX-Projektordner), nicht die Manuskripte-Wurzel.
     * Chat-Sessions und Agent-Memory gehören unter {@code <Buch>/data/...}.
     */
    private File resolveProjectDir() {
        MainController main = host.getMainController();
        if (main != null) {
            String path = main.getCurrentDirectoryPath();
            if (path != null && !path.isBlank()) {
                File dir = new File(path.trim());
                if (dir.isDirectory()) {
                    return dir;
                }
            }
        }
        File md = null;
        if (host.asCanvasChapterEditor() != null) {
            md = host.asCanvasChapterEditor().getLoadedChapterFile();
        }
        if (md == null && host.asLegacyEditorWindow() != null) {
            md = host.asLegacyEditorWindow().getCurrentFile();
        }
        if (md != null && md.getParentFile() != null) {
            File parent = md.getParentFile();
            if ("data".equals(parent.getName()) && parent.getParentFile() != null) {
                return parent.getParentFile();
            }
            return parent;
        }
        File docx = host.getOriginalDocxFile();
        if (docx != null && docx.getParentFile() != null && docx.getParentFile().isDirectory()) {
            return docx.getParentFile();
        }
        // Kein Fallback auf user.dir — sonst landen Chat/Agent-Daten im App-/Repo-Verzeichnis.
        return null;
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
                return java.util.Arrays.asList(
                        ParameterRegistry.DEFAULT_OLLAMA_MODEL,
                        "gemma3:4b",
                        "mistral:7b-instruct",
                        "llama3.1:8b-instruct");
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
