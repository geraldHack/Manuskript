package com.manuskript.agent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import com.manuskript.OllamaService;
import com.manuskript.ResourceManager;

/**
 * Ollama-Backend: Wrapper um den existierenden OllamaService.
 */
public class OllamaBackend implements AIBackend {

    private final OllamaService ollamaService;

    public OllamaBackend(OllamaService ollamaService) {
        this.ollamaService = ollamaService;
        String model = ResourceManager.getParameter("agent.ollama.model", "gemma3:4b");
        if (model != null && !model.isEmpty()) {
            ollamaService.setModel(model);
        }
    }

    @Override
    public String getName() {
        return "Ollama";
    }

    @Override
    public List<String> getAvailableModels() {
        try {
            String[] models = ollamaService.getAvailableModels().get();
            return Arrays.asList(models);
        } catch (Exception e) {
            return Arrays.asList("gemma3:4b", "mistral:7b-instruct", "llama3.1:8b-instruct");
        }
    }

    @Override
    public String getCurrentModel() {
        return ollamaService.getCurrentParameters().split(",")[0]
                .replace("Modell: ", "").trim();
    }

    @Override
    public void setCurrentModel(String model) {
        ollamaService.setModel(model);
    }

    @Override
    public CompletableFuture<String> chat(String systemPrompt, String userMessage, int maxTokens) {
        // Erzwinge neue Session für jede Analyse, um Kontext-Übertragung zu verhindern
        String uniqueSessionId = "agent-" + UUID.randomUUID().toString();
        ollamaService.setCurrentSession(uniqueSessionId);
        return ollamaService.chatWithSystemPrompt(systemPrompt, userMessage, maxTokens);
    }

    public OllamaService.StreamHandle chatStreaming(String systemPrompt, String userMessage,
                                                     Consumer<String> onChunk, Runnable onComplete, Consumer<Throwable> onError) {
        List<OllamaService.ChatMessage> messages = new ArrayList<>();
        messages.add(new OllamaService.ChatMessage("user", userMessage));
        return ollamaService.chatStreaming(systemPrompt, messages, null, onChunk, onComplete, onError);
    }
}
