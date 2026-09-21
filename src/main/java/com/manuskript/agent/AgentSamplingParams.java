package com.manuskript.agent;

import com.manuskript.ResourceManager;

/**
 * Einheitliche Sampling-Parameter: Parameter-Tab als Default, Agent-Config als Override.
 */
public final class AgentSamplingParams {

    private AgentSamplingParams() {
    }

    public static double defaultTemperature(String backendType) {
        if (isOpenAi(backendType)) {
            return ResourceManager.getDoubleParameter("agent.openai.temperature", 0.7);
        }
        return ResourceManager.getDoubleParameter("ollama.temperature", 0.3);
    }

    /**
     * Wendet Sampling auf das Backend an.
     * {@code useParameterSampling=true}: Temperatur aus der Parameterverwaltung
     * ({@code agent.openai.temperature} / {@code ollama.temperature}).
     * Sonst: Werte aus der Agent-Config (UI-Slider).
     */
    public static void applyAgentConfig(AIBackend backend, AgentConfig config) {
        applyAgentConfig(backend, config, false);
    }

    public static void applyAgentConfig(AIBackend backend, AgentConfig config, boolean useParameterSampling) {
        if (backend == null || config == null) {
            return;
        }
        String backendType = backend instanceof OpenAIBackend ? "OpenAI" : "Ollama";
        if (useParameterSampling) {
            backend.setTemperature(defaultTemperature(backendType));
        } else {
            backend.setTemperature(config.getTemperature());
        }
        if (backend instanceof OllamaBackend ollamaBackend) {
            if (useParameterSampling) {
                ollamaBackend.setTopP(ResourceManager.getDoubleParameter("ollama.top_p", config.getTopP()));
                ollamaBackend.setRepeatPenalty(
                        ResourceManager.getDoubleParameter("ollama.repeat_penalty", config.getRepeatPenalty()));
            } else {
                ollamaBackend.setTopP(config.getTopP());
                ollamaBackend.setRepeatPenalty(config.getRepeatPenalty());
            }
        } else if (backend instanceof OpenAIBackend openAiBackend) {
            openAiBackend.setTopP(config.getTopP());
        }
    }

    private static boolean isOpenAi(String backendType) {
        return "OpenAI".equalsIgnoreCase(backendType);
    }
}
