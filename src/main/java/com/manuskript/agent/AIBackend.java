package com.manuskript.agent;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Abstraktion für KI-Backends (Ollama, OpenAI, etc.)
 */
public interface AIBackend {
    String getName();
    List<String> getAvailableModels();
    String getCurrentModel();
    void setCurrentModel(String model);

    CompletableFuture<String> chat(String systemPrompt, String userMessage, int maxTokens);

    /**
     * Wie {@link #chat}, liefert aber ankommende Textstücke sofort an {@code onDelta}.
     * Default: einmal der komplette Text nach Abschluss (kein echtes Streaming).
     */
    default CompletableFuture<String> chatStreaming(
            String systemPrompt, String userMessage, int maxTokens, Consumer<String> onDelta) {
        return chat(systemPrompt, userMessage, maxTokens).thenApply(result -> {
            if (onDelta != null && result != null && !result.isEmpty()) {
                onDelta.accept(result);
            }
            return result;
        });
    }

    /**
     * Multi-Turn-Chat: System-Prompt, optionaler Kontext-Block, bisherige Turns, neue User-Nachricht.
     */
    CompletableFuture<String> chatMultiTurn(
            String systemPrompt,
            String contextBlock,
            List<ChatTurn> history,
            String newUserMessage,
            int maxTokens);

    void setTemperature(double temperature);
}
