package com.manuskript.agent;

import com.manuskript.ChapterEditorHost;
import com.manuskript.OllamaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Führt den Überarbeiten-Agenten für eine Editor-Markierung aus.
 */
public final class AgentSelectionRevisionRunner {

    private static final Logger logger = LoggerFactory.getLogger(AgentSelectionRevisionRunner.class);

    private AgentSelectionRevisionRunner() {
    }

    public static void run(
            ChapterEditorHost host,
            AgentTabPane agentTabPane,
            Map<String, PlotholeAgent> agentInstances,
            Map<String, AIBackend> agentBackends,
            Supplier<File> projectDirSupplier,
            Runnable showAgentPanel,
            AgentTab explicitTab,
            String authorInstruction) {
        if (host == null || agentTabPane == null) {
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
        int maxChars = SelectionRevisionSupport.maxSelectionChars();
        if (selected.length() > maxChars) {
            host.updateStatus("Markierung zu lang (max. " + maxChars + " Zeichen).");
            return;
        }

        AgentTab targetTab = explicitTab != null ? explicitTab : SelectionRevisionSupport.findRevisionTab(agentTabPane);
        if (targetTab == null) {
            host.updateStatus("Überarbeiten-Agent nicht gefunden (config/agents.json).");
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

        String context = SelectionRevisionSupport.buildSurroundingContext(fullText, start, end);
        int maxOutputTokens = config.getMaxTokens();
        logger.info("Überarbeiten-Agent: Markierung {} Zeichen, Kontext {} Zeichen, Anweisung {} Zeichen",
                selected.length(), context.length(),
                authorInstruction != null ? authorInstruction.length() : 0);

        agent.analyze(selected, context, maxOutputTokens, authorInstruction)
                .thenAccept(targetTab::showParseResult)
                .exceptionally(ex -> {
                    String detail = AgentAnalysisErrors.format(ex);
                    logger.error("Überarbeiten-Analyse fehlgeschlagen: {}", detail, AgentAnalysisErrors.unwrap(ex));
                    targetTab.showError(detail);
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
