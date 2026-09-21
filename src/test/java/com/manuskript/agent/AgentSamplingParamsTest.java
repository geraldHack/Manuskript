package com.manuskript.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.manuskript.ResourceManager;

class AgentSamplingParamsTest {

    @AfterEach
    void restoreDefaults() {
        ResourceManager.saveParameter("agent.openai.temperature", "0.7");
        ResourceManager.saveParameter("ollama.temperature", "0.3");
    }

    @Test
    void applyAgentConfig_usesParameterTemperatureWhenRequested() {
        ResourceManager.saveParameter("agent.openai.temperature", "0.1");
        AgentConfig config = new AgentConfig("t", "OpenAI", "p", "m", 0.7, 1024, 0.9, 1.1);
        OpenAIBackend backend = new OpenAIBackend();

        AgentSamplingParams.applyAgentConfig(backend, config, true);

        assertEquals(0.1, backend.getTemperature(), 1e-9);
    }

    @Test
    void applyAgentConfig_usesAgentConfigTemperatureByDefault() {
        ResourceManager.saveParameter("agent.openai.temperature", "0.1");
        AgentConfig config = new AgentConfig("t", "OpenAI", "p", "m", 0.7, 1024, 0.9, 1.1);
        OpenAIBackend backend = new OpenAIBackend();

        AgentSamplingParams.applyAgentConfig(backend, config);

        assertEquals(0.7, backend.getTemperature(), 1e-9);
    }
}
