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
 * Agent-spezifische Sampling-Werte werden nur im Speicher gehalten (kein Überschreiben der globalen Parameter).
 */
public class OllamaBackend implements AIBackend {

    private final OllamaService ollamaService;
    private Double temperatureOverride;
    private Double topPOverride;
    private Double repeatPenaltyOverride;

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
    public void setTemperature(double temperature) {
        this.temperatureOverride = temperature;
    }

    public void setTopP(double topP) {
        this.topPOverride = topP;
    }

    public void setRepeatPenalty(double repeatPenalty) {
        this.repeatPenaltyOverride = repeatPenalty;
    }

    @Override
    public CompletableFuture<String> chat(String systemPrompt, String userMessage, int maxTokens) {
        String uniqueSessionId = "agent-" + UUID.randomUUID();
        ollamaService.setCurrentSession(uniqueSessionId);
        return ollamaService.chatWithSystemPrompt(
                systemPrompt, userMessage, maxTokens,
                effectiveTemperature(), effectiveTopP(), effectiveRepeatPenalty());
    }

    @Override
    public CompletableFuture<String> chatMultiTurn(String systemPrompt, String contextBlock,
                                                     List<ChatTurn> history, String newUserMessage,
                                                     int maxTokens) {
        String uniqueSessionId = "agent-" + UUID.randomUUID();
        ollamaService.setCurrentSession(uniqueSessionId);
        List<OllamaService.ChatMessage> ollamaHistory = new ArrayList<>();
        if (history != null) {
            for (ChatTurn turn : history) {
                ollamaHistory.add(new OllamaService.ChatMessage(turn.role(), turn.content()));
            }
        }
        return ollamaService.chatMultiTurn(
                systemPrompt, contextBlock, ollamaHistory, newUserMessage, maxTokens,
                effectiveTemperature(), effectiveTopP(), effectiveRepeatPenalty());
    }

    public OllamaService.StreamHandle chatStreaming(String systemPrompt, String userMessage,
                                                     Consumer<String> onChunk, Runnable onComplete, Consumer<Throwable> onError) {
        List<OllamaService.ChatMessage> messages = new ArrayList<>();
        messages.add(new OllamaService.ChatMessage("user", userMessage));
        return ollamaService.chatStreaming(systemPrompt, messages, null, onChunk, onComplete, onError);
    }

    private double effectiveTemperature() {
        return temperatureOverride != null ? temperatureOverride : ollamaService.getTemperature();
    }

    private double effectiveTopP() {
        return topPOverride != null ? topPOverride : ollamaService.getTopP();
    }

    private double effectiveRepeatPenalty() {
        return repeatPenaltyOverride != null ? repeatPenaltyOverride : ollamaService.getRepeatPenalty();
    }
}
