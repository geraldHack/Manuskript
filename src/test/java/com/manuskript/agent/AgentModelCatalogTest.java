package com.manuskript.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentModelCatalogTest {

    @Test
    void mammouthUsesPublicModelsEndpoint() {
        assertEquals(AgentModelCatalog.MAMMOUTH_PUBLIC_MODELS,
                AgentModelCatalog.modelsUrl("https://api.mammouth.ai/v1"));
        assertEquals(AgentModelCatalog.MAMMOUTH_PUBLIC_MODELS,
                AgentModelCatalog.modelsUrl("https://api.mammouth.ai/v1/"));
    }

    @Test
    void otherProvidersUseV1Models() {
        assertEquals("https://openrouter.ai/api/v1/models",
                AgentModelCatalog.modelsUrl("https://openrouter.ai/api/v1"));
        assertEquals("https://api.openai.com/v1/models",
                AgentModelCatalog.modelsUrl("https://api.openai.com/v1/"));
    }

    @Test
    void parseModelIdsFromOpenAiWrapper() {
        String json = """
                {"data":[{"id":"gpt-4o-mini"},{"id":"gpt-4o"},{"id":"gpt-4o"}]}
                """;
        assertEquals(List.of("gpt-4o", "gpt-4o-mini"), AgentModelCatalog.parseModelIds(json));
        assertEquals(List.of("real-id"), AgentModelCatalog.parseModelIds(
                "{\"data\":[{\"id\":\"real-id\",\"model\":\"alias\"}]}"));
    }

    @Test
    void parseModelIdsFromMammouthArray() {
        String json = """
                [{"id":"deepseek-v3.2"},{"model_name":"glm-5.2"}]
                """;
        List<String> ids = AgentModelCatalog.parseModelIds(json);
        assertTrue(ids.contains("deepseek-v3.2"));
        assertTrue(ids.contains("glm-5.2"));
    }
}
