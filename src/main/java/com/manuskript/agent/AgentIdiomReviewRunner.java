package com.manuskript.agent;

import com.manuskript.ChapterEditorHost;
import com.manuskript.DocxFile;
import com.manuskript.MainController;
import com.manuskript.OllamaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Führt den Sprachentflechtung-Agenten für eine Editor-Markierung aus.
 */
public final class AgentIdiomReviewRunner {

    private static final Logger logger = LoggerFactory.getLogger(AgentIdiomReviewRunner.class);

    private AgentIdiomReviewRunner() {
    }

    public static void run(
            ChapterEditorHost host,
            AgentTabPane agentTabPane,
            Map<String, PlotholeAgent> agentInstances,
            Map<String, AIBackend> agentBackends,
            Supplier<File> projectDirSupplier,
            Runnable showAgentPanel,
            AgentTab explicitTab) {
        if (host == null || agentTabPane == null) {
            return;
        }
        if (!host.hasTextSelection()) {
            host.updateStatus("Bitte zuerst Text markieren (max. "
                    + IdiomReviewSupport.maxSelectionChars() + " Zeichen).");
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
        int maxChars = IdiomReviewSupport.maxSelectionChars();
        if (selected.length() > maxChars) {
            host.updateStatus("Markierung zu lang (max. " + maxChars + " Zeichen).");
            return;
        }

        AgentTab targetTab = explicitTab != null ? explicitTab : IdiomReviewSupport.findIdiomReviewTab(agentTabPane);
        if (targetTab == null) {
            host.updateStatus("Sprachentflechtung-Agent nicht gefunden (config/agents.json).");
            return;
        }

        String model = targetTab.getAgentConfig().getModel();
        if (model == null || model.isBlank()) {
            targetTab.showError("Kein Modell gewählt");
            return;
        }

        PlotholeAgent agent = getOrCreateAgentForTab(
                targetTab, agentInstances, agentBackends, projectDirSupplier, host.getEditorKey());
        if (agent == null) {
            targetTab.showError("Agent konnte nicht initialisiert werden");
            return;
        }

        AgentConfig config = targetTab.getAgentConfig();
        agent.setSystemPrompt(config.getSystemPrompt());
        AIBackend backend = agentBackends.get(targetTab.getAgentId());
        if (backend != null) {
            AgentSamplingParams.applyAgentConfig(backend, config);
        }

        if (showAgentPanel != null) {
            showAgentPanel.run();
        }
        agentTabPane.selectTab(targetTab);
        targetTab.setAnalyzing(true);
        targetTab.setSelectionRevisionContext(start, end, selected);
        targetTab.setRewriteText("");
        targetTab.setApplyRewriteEnabled(false);

        File projectDir = projectDirSupplier != null ? projectDirSupplier.get() : null;
        MainController main = host.getMainController();
        File docx = host.getOriginalDocxFile();
        File mdFile = host.asCanvasChapterEditor() != null
                ? host.asCanvasChapterEditor().getLoadedChapterFile() : null;
        if (host.asLegacyEditorWindow() != null) {
            mdFile = host.asLegacyEditorWindow().getCurrentFile();
        }
        List<DocxFile> chapterOrder = main != null
                ? main.getSelectedDocxFilesAsDocxFiles() : List.of();

        ChatbotContextConfig contextConfig = targetTab.getIdiomContextConfig();
        if (contextConfig == null) {
            contextConfig = new ChatbotContextConfig();
            contextConfig.addSource(ChatbotContextSource.CURRENT_CHAPTER);
        }
        String chatContext = ChatbotContextBuilder.build(
                projectDir, main, host.getEditorKey(), fullText, mdFile, docx, chapterOrder, contextConfig);
        String surrounding = SelectionRevisionSupport.buildSurroundingContext(fullText, start, end);
        String combinedContext = IdiomReviewSupport.combineContextBlocks(chatContext, surrounding);

        int maxOutputTokens = IdiomReviewSupport.estimateMaxOutputTokens(selected.length(), config.getMaxTokens());
        logger.info("Sprachentflechtung: Markierung {} Zeichen, Kontext {} Zeichen, max_output_tokens={}",
                selected.length(), combinedContext.length(), maxOutputTokens);

        agent.analyzeRaw(selected, combinedContext, maxOutputTokens, null)
                .thenAccept(raw -> Platform.runLater(() -> {
                    String rewrite = IdiomReviewSupport.extractRewrite(raw);
                    PlotholeParseResult parsed = PlotholeResponseParser.parse(raw);
                    targetTab.setRewriteText(rewrite.isBlank() ? selected : rewrite);
                    targetTab.setApplyRewriteEnabled(!rewrite.isBlank()
                            || parsed.getOutcome() == PlotholeParseResult.Outcome.FINDINGS);
                    targetTab.showParseResult(parsed);
                }))
                .exceptionally(ex -> {
                    String detail = AgentAnalysisErrors.format(ex);
                    logger.error("Sprachentflechtung fehlgeschlagen: {}", detail, AgentAnalysisErrors.unwrap(ex));
                    Platform.runLater(() -> targetTab.showError(detail));
                    return null;
                });
    }

    private static PlotholeAgent getOrCreateAgentForTab(
            AgentTab tab,
            Map<String, PlotholeAgent> agentInstances,
            Map<String, AIBackend> agentBackends,
            Supplier<File> projectDirSupplier,
            String chapterKey) {
        String agentId = tab.getAgentId();
        PlotholeAgent agent = agentInstances.get(agentId);
        if (agent != null) {
            return agent;
        }
        File projectDir = projectDirSupplier != null ? projectDirSupplier.get() : null;
        if (projectDir == null) {
            projectDir = new File(System.getProperty("user.dir"));
        }
        AIBackend backend = agentBackends.get(agentId);
        if (backend == null) {
            backend = createBackend(tab.getAgentConfig());
            agentBackends.put(agentId, backend);
        }
        AgentMemory memory = new AgentMemory(projectDir, "agent_" + agentId, chapterKey);
        agent = new PlotholeAgent(backend, memory);
        agent.setSystemPrompt(tab.getAgentConfig().getSystemPrompt());
        agentInstances.put(agentId, agent);
        return agent;
    }

    private static AIBackend createBackend(AgentConfig config) {
        AIBackend backend = "OpenAI".equals(config.getBackend())
                ? new OpenAIBackend() : new OllamaBackend(new OllamaService());
        String model = config.getModel();
        if (model != null && !model.isBlank()) {
            backend.setCurrentModel(model.trim());
        }
        return backend;
    }
}
