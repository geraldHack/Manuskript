package com.manuskript.mammouth;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MammouthClientTest {

    @Test
    void hostRootStripsV1() {
        assertEquals("https://api.mammouth.ai", MammouthClient.hostRoot("https://api.mammouth.ai/v1"));
        assertEquals("https://api.mammouth.ai", MammouthClient.hostRoot("https://api.mammouth.ai/v1/"));
    }

    @Test
    void parseKeyInfoFromLiteLlmWrapper() {
        String json = """
                {"key":"sk-abc","info":{"key_alias":"manuskript","spend":1.5,"max_budget":10.0,
                "budget_duration":"30d","budget_reset_at":"2026-09-01T00:00:00Z","models":["gpt-4.1"]}}
                """;
        MammouthClient.KeyInfo info = MammouthClient.parseKeyInfo(json);
        assertEquals("manuskript", info.keyAlias());
        assertEquals(1.5, info.spend());
        assertEquals(10.0, info.maxBudget());
        assertEquals(8.5, info.remaining());
        assertEquals("30d", info.budgetDuration());
        assertEquals(List.of("gpt-4.1"), info.allowedModels());
    }

    @Test
    void parseModelsUsesModelInfoCosts() {
        String json = """
                {"data":[{"id":"glm-5.2","model_info":{"input_cost_per_token":0.0000014,"output_cost_per_token":0.0000044}}]}
                """;
        List<MammouthClient.ModelInfo> models = MammouthClient.parseModels(json);
        assertEquals(1, models.size());
        assertEquals("glm-5.2", models.get(0).id());
        assertEquals(1.4, models.get(0).inputPerMillion(), 0.0001);
        assertEquals(4.4, models.get(0).outputPerMillion(), 0.0001);
    }

    @Test
    void mammouthUrlDetection() {
        assertTrue(MammouthConfigLoader.isMammouthUrl("https://api.mammouth.ai/v1"));
    }
}
