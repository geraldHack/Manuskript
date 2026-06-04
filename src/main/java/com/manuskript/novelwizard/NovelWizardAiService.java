package com.manuskript.novelwizard;

import com.manuskript.OllamaService;
import com.manuskript.ResourceManager;
import com.manuskript.agent.AIBackend;
import com.manuskript.agent.OllamaBackend;
import com.manuskript.agent.OpenAIBackend;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class NovelWizardAiService {
    private static final int DEFAULT_MAX_TOKENS = 1800;

    private final AIBackend backend;
    private final NovelWizardPromptRegistry promptRegistry;

    public NovelWizardAiService(Path promptConfig) {
        this.backend = createBackend();
        this.promptRegistry = new NovelWizardPromptRegistry(promptConfig);
    }

    public CompletableFuture<NovelWizardTurn> nextTurn(NovelWizardSession session, String existingContext) {
        NovelWizardPhase phase = session.getCurrentPhase();
        NovelWizardPromptRegistry.PromptDef prompt = promptRegistry.get(phase);
        String systemPrompt = buildSystemPrompt(prompt, phase);
        String userPrompt = buildUserPrompt(session, prompt, existingContext);
        session.addPromptLog(phase, systemPrompt, userPrompt);

        return backend.chat(systemPrompt, userPrompt, maxTokens())
                .thenApply(raw -> {
                    NovelWizardTurn turn = NovelWizardResponseParser.parse(raw);
                    if (turn.getQuestion().isBlank() && turn.getContent().isBlank()) {
                        turn.setQuestion("Wie moechtest du in dieser Phase weitermachen?");
                    }
                    return turn;
                });
    }

    private static AIBackend createBackend() {
        String backendType = ResourceManager.getParameter("agent.backend", "Ollama");
        AIBackend backend;
        if ("OpenAI".equalsIgnoreCase(backendType)) {
            backend = new OpenAIBackend();
            backend.setCurrentModel(ResourceManager.getParameter("agent.openai.model", "gpt-4o-mini"));
            backend.setTemperature(ResourceManager.getDoubleParameter("agent.openai.temperature", 0.7));
        } else {
            backend = new OllamaBackend(new OllamaService());
            backend.setCurrentModel(ResourceManager.getParameter("agent.ollama.model", "gemma3:4b"));
            backend.setTemperature(ResourceManager.getDoubleParameter("ollama.temperature", 0.5));
        }
        return backend;
    }

    private static int maxTokens() {
        try {
            return Integer.parseInt(ResourceManager.getParameter("novelwizard.max_tokens", String.valueOf(DEFAULT_MAX_TOKENS)));
        } catch (NumberFormatException e) {
            return DEFAULT_MAX_TOKENS;
        }
    }

    private String buildSystemPrompt(NovelWizardPromptRegistry.PromptDef prompt, NovelWizardPhase phase) {
        boolean contentPhase = phase == NovelWizardPhase.SYNOPSIS
                || phase == NovelWizardPhase.STRUCTURE
                || phase == NovelWizardPhase.CHAPTERS;
        String format = contentPhase
                ? """
                Antworte ausschliesslich in diesem Format:
                <CONTENT>
                Markdown-Inhalt
                </CONTENT>
                <SUMMARY>Kurze Einordnung</SUMMARY>
                """
                : """
                Antworte ausschliesslich in diesem Format:
                <QUESTION>Eine klare Frage</QUESTION>
                <OPTIONS>
                  <OPTION id="1">Option</OPTION>
                  <OPTION id="2">Option</OPTION>
                  <OPTION id="3">Option</OPTION>
                  <OPTION id="4">Option</OPTION>
                </OPTIONS>
                <HINT>Kurzer Hinweis</HINT>
                <SUMMARY>Kurze Einordnung des bisherigen Stands</SUMMARY>
                """;
        return prompt.systemPrompt + "\n\n"
                + "Arbeite auf Deutsch. Die Optionen muessen KI-generiert und auf das konkrete Projekt bezogen sein. "
                + "Keine Meta-Erklaerungen ausserhalb der Tags.\n\n" + format;
    }

    private String buildUserPrompt(NovelWizardSession session, NovelWizardPromptRegistry.PromptDef prompt, String existingContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("Aktuelle Phase: ").append(session.getCurrentPhase().getTitle()).append("\n");
        sb.append("Anweisung: ").append(prompt.instruction).append("\n\n");
        if (existingContext != null && !existingContext.isBlank()) {
            sb.append("<EXISTING_CONTEXT>\n").append(existingContext).append("\n</EXISTING_CONTEXT>\n\n");
        }
        if (!session.getProjectSummary().isBlank()) {
            sb.append("<PROJECT_SUMMARY>\n").append(session.getProjectSummary()).append("\n</PROJECT_SUMMARY>\n\n");
        }
        sb.append("<COLLECTED>\n");
        for (Map.Entry<String, String> entry : session.getCollected().entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        sb.append("</COLLECTED>\n\n");
        sb.append("Letzte Antworten:\n");
        int start = Math.max(0, session.getChatHistory().size() - 8);
        for (int i = start; i < session.getChatHistory().size(); i++) {
            NovelWizardSession.ChatEntry entry = session.getChatHistory().get(i);
            if ("user".equals(entry.role)) {
                sb.append("Autor: ").append(entry.choice).append("\n");
            } else if (entry.parsed != null && !entry.parsed.getSummary().isBlank()) {
                sb.append("Assistent: ").append(entry.parsed.getSummary()).append("\n");
            }
        }
        return sb.toString();
    }
}
