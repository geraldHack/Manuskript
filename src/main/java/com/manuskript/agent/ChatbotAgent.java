package com.manuskript.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.manuskript.CustomChatArea;
import com.manuskript.ResourceManager;

/**
 * Multi-Turn-Chat-Orchestrierung fuer den Chatbot-Agent-Tab.
 */
public class ChatbotAgent {

    public static final String DEFAULT_SYSTEM_PROMPT =
            "Du bist ein hilfreicher Schreib-Assistent fuer literarische Manuskripte.\n"
            + "Antworte auf Deutsch, praezise und konstruktiv.\n"
            + "Nutze den mitgelieferten Kontext, wenn er relevant ist.\n"
            + "Keine Meta-Kommentare ueber deine Rolle als KI — antworte direkt auf die Frage des Autors.";

    private final AIBackend backend;
    private String systemPrompt = DEFAULT_SYSTEM_PROMPT;

    public ChatbotAgent(AIBackend backend) {
        this.backend = backend;
    }

    public void setSystemPrompt(String systemPrompt) {
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            this.systemPrompt = systemPrompt;
        }
    }

    public CompletableFuture<String> sendMessage(
            String contextBlock,
            List<CustomChatArea.QAPair> qaPairs,
            String newUserMessage,
            int maxHistoryTurns,
            int maxTokens) {
        List<ChatTurn> allTurns = ChatbotSessionStore.qaPairsToTurns(qaPairs);
        int limit = maxHistoryTurns > 0 ? maxHistoryTurns
                : ChatbotSessionData.defaultMaxHistoryTurns();
        List<ChatTurn> history = ChatbotContextBuilder.truncateHistory(allTurns, limit * 2);
        return backend.chatMultiTurn(systemPrompt, contextBlock, history, newUserMessage, maxTokens);
    }

    public static int defaultMaxHistoryTurns() {
        return Integer.parseInt(ResourceManager.getParameter("agent.chatbot.max_history_turns", "10"));
    }
}
