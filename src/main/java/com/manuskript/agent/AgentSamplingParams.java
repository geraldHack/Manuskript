package com.manuskript.agent;

import com.manuskript.ResourceManager;

/**
 * Einheitliche Sampling-Parameter: Parameter-Tab als Default, Agent-Config als Override.
 */
public final class AgentSamplingParams {

    private AgentSamplingParams() {
    }

    public static double defaultTemperature(String backendType) {
        if ("OpenAI".equals(backendType)) {
            return ResourceManager.getDoubleParameter("agent.openai.temperature", 0.7);
        }
        return ResourceManager.getDoubleParameter("ollama.temperature", 0.3);
    }

    /**
     * Wendet Agent-spezifische Werte auf das Backend an (überschreibt Parameter-Tab-Defaults).
     */
    public static void applyAgentConfig(AIBackend backend, AgentConfig config) {
        if (backend == null || config == null) {
            return;
        }
        backend.setTemperature(config.getTemperature());
        if (backend instanceof OllamaBackend ollamaBackend) {
            ollamaBackend.setTopP(config.getTopP());
            ollamaBackend.setRepeatPenalty(config.getRepeatPenalty());
        }
    }
}
