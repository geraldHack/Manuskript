package com.manuskript.mammouth;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void remainingUsesBudgetWhenSpendMissing() {
        String json = """
                {"info":{"max_budget":2.0}}
                """;
        MammouthClient.KeyInfo info = MammouthClient.parseKeyInfo(json);
        assertEquals(2.0, info.remaining());
        assertEquals("2,00 $", MammouthClient.formatUsd(info.remaining()));
    }

    @Test
    void remainingUsesExplicitBalanceField() {
        String json = """
                {"info":{"remaining":2,"spend":0}}
                """;
        MammouthClient.KeyInfo info = MammouthClient.parseKeyInfo(json);
        assertEquals(2.0, info.remaining());
        assertNull(info.maxBudget());
    }

    @Test
    void parseUserInfoBudget() {
        String json = """
                {"user_id":"u1","user_info":{"spend":0.25,"max_budget":2.0,"user_email":"a@b.c"}}
                """;
        MammouthClient.KeyInfo info = MammouthClient.parseUserInfo(json);
        assertEquals(1.75, info.remaining());
        assertEquals("a@b.c", info.keyAlias());
    }

    @Test
    void parseNestedBudgetTable() {
        String json = """
                {"info":{"spend":0.4,"litellm_budget_table":{"max_budget":2}}}
                """;
        MammouthClient.KeyInfo info = MammouthClient.parseKeyInfo(json);
        assertEquals(2.0, info.maxBudget());
        assertEquals(1.6, info.remaining());
    }

    @Test
    void mergePrefersKeySpendAndUserBudget() {
        MammouthClient.KeyInfo key = MammouthClient.parseKeyInfo("{\"info\":{\"spend\":0.5}}");
        MammouthClient.KeyInfo user = MammouthClient.parseUserInfo("{\"user_info\":{\"max_budget\":2}}");
        MammouthClient.KeyInfo merged = MammouthClient.mergeKeyInfo(key, user);
        assertEquals(0.5, merged.spend());
        assertEquals(2.0, merged.maxBudget());
        assertEquals(1.5, merged.remaining());
    }

    @Test
    void parseSpendLogsArray() {
        String json = """
                [{"request_id":"r1","model":"glm-5.2","startTime":"2026-09-07T10:00:00Z",
                "spend":0.012,"total_tokens":120}]
                """;
        List<MammouthClient.SpendLog> logs = MammouthClient.parseSpendLogs(json);
        assertEquals(1, logs.size());
        assertEquals("glm-5.2", logs.get(0).model());
        assertEquals(0.012, logs.get(0).spend());
        assertEquals(120L, logs.get(0).totalTokens());
    }

    @Test
    void parseSpendLogsV2Wrapper() {
        String json = """
                {"data":[{"model":"gpt-4.1","start_date":"2026-09-01","spend":1.2}]}
                """;
        List<MammouthClient.SpendLog> logs = MammouthClient.parseSpendLogs(json);
        assertEquals(1, logs.size());
        assertEquals("gpt-4.1", logs.get(0).model());
        assertEquals("2026-09-01", logs.get(0).startedAt());
    }

    @Test
    void parseDailyActivityByModel() {
        String json = """
                {"results":[{"date":"2026-09-07","breakdown":{"models":{
                "glm-5.2":{"spend":0.3,"total_tokens":80}}}}]}
                """;
        List<MammouthClient.SpendLog> logs = MammouthClient.parseDailyActivity(json);
        assertEquals(1, logs.size());
        assertEquals("glm-5.2", logs.get(0).model());
        assertEquals(0.3, logs.get(0).spend());
    }

    @Test
    void parseDailyActivityUnwrapsNestedMetricsAndModelGroups() {
        String json = """
                {"results":[{"date":"2026-09-07","breakdown":{"model_groups":{
                "deepseek-v3.2":{"metrics":{"spend":0.002702,"prompt_tokens":9700,
                "completion_tokens":208,"total_tokens":9908}}},
                "models":{"openrouter/deepseek/deepseek-v3.2":{"metrics":{"spend":0.002702}}}}}]}
                """;
        List<MammouthClient.SpendLog> logs = MammouthClient.parseDailyActivity(json);
        assertEquals(1, logs.size());
        assertEquals("deepseek-v3.2", logs.get(0).model());
        assertEquals(0.002702, logs.get(0).spend());
        assertEquals(9908L, logs.get(0).totalTokens());
        assertEquals(9700L, logs.get(0).promptTokens());
        assertEquals(208L, logs.get(0).completionTokens());
    }

    @Test
    void parseUserInfoReadsMetadataBaseBudget() {
        String json = """
                {"user_info":{"spend":0.0027022,"max_budget":2.0,
                "metadata":{"base_budget":2.0,"additional_credits":0.0}}}
                """;
        MammouthClient.KeyInfo info = MammouthClient.parseUserInfo(json);
        assertEquals(2.0, info.maxBudget());
        assertEquals(1.9972978, info.remaining(), 0.0000001);
    }

    @Test
    void shortModelNameUsesLastSegment() {
        assertEquals("deepseek-v3.2", MammouthClient.shortModelName("openrouter/deepseek/deepseek-v3.2"));
        assertEquals("glm-5.2", MammouthClient.shortModelName("glm-5.2"));
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
    void parseModelsSortsByNumericInputCost() {
        String json = """
                {"data":[
                  {"id":"zebra","model_info":{"input_cost_per_token":0.000005,"output_cost_per_token":0.00001}},
                  {"id":"alpha","model_info":{"input_cost_per_token":0.0000002,"output_cost_per_token":0.000001}},
                  {"id":"mid","model_info":{"input_cost_per_token":0.0000014,"output_cost_per_token":0.000004}}
                ]}
                """;
        List<MammouthClient.ModelInfo> models = MammouthClient.parseModels(json);
        assertEquals(List.of("alpha", "mid", "zebra"),
                models.stream().map(MammouthClient.ModelInfo::id).toList());
    }

    @Test
    void mammouthUrlDetection() {
        assertTrue(MammouthConfigLoader.isMammouthUrl("https://api.mammouth.ai/v1"));
    }
}
