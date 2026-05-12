package com.manuskript.agent;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Abstraktion für KI-Backends (Ollama, OpenAI, etc.)
 */
public interface AIBackend {
    String getName();
    List<String> getAvailableModels();
    String getCurrentModel();
    void setCurrentModel(String model);

    CompletableFuture<String> chat(String systemPrompt, String userMessage, int maxTokens);
}
