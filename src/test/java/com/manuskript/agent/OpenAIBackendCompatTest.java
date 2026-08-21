package com.manuskript.agent;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAIBackendCompatTest {

    @Test
    void putMaxTokenLimitsSendsBothOpenAiAndCompletionFields() {
        JsonObject body = new JsonObject();
        OpenAIBackend.putMaxTokenLimits(body, 1024);

        assertEquals(1024, body.get("max_tokens").getAsInt());
        assertEquals(1024, body.get("max_completion_tokens").getAsInt());
    }

    @Test
    void resolveApiKeyUsesLocalPlaceholderForLoopback() {
        assertEquals("local", OpenAIBackend.resolveApiKey("", "http://127.0.0.1:8080/v1"));
        assertEquals("local", OpenAIBackend.resolveApiKey(null, "http://localhost:8080/v1"));
        assertEquals("sk-test", OpenAIBackend.resolveApiKey("sk-test", "http://127.0.0.1:8080/v1"));
        assertEquals("", OpenAIBackend.resolveApiKey("", "https://api.openai.com/v1"));
    }

    @Test
    void loopbackUrlDetection() {
        assertTrue(OpenAIBackend.isLoopbackOpenAiUrl("http://127.0.0.1:8080/v1"));
        assertTrue(OpenAIBackend.isLoopbackOpenAiUrl("http://localhost:11434/v1"));
        assertFalse(OpenAIBackend.isLoopbackOpenAiUrl("https://api.openai.com/v1"));
    }

    @Test
    void parseSseDataExtractsDeltaContentAndDone() {
        OpenAIChatCompletionParser.StreamEvent hello = OpenAIChatCompletionParser.parseSseData(
                "{\"choices\":[{\"delta\":{\"content\":\"Hallo\"},\"finish_reason\":null}]}");
        assertEquals("Hallo", hello.content());
        assertFalse(hello.done());

        OpenAIChatCompletionParser.StreamEvent done = OpenAIChatCompletionParser.parseSseData("[DONE]");
        assertTrue(done.done());

        OpenAIChatCompletionParser.StreamEvent err = OpenAIChatCompletionParser.parseSseData(
                "{\"error\":{\"message\":\"overloaded\"}}");
        assertEquals("overloaded", err.errorMessage());
    }

    @Test
    void gatewayRetryIncludesCloudflareTimeout() {
        assertTrue(com.manuskript.GatewayHttpRetry.isRetryableStatus(524));
        assertTrue(com.manuskript.GatewayHttpRetry.isRetryableStatus(502));
        assertFalse(com.manuskript.GatewayHttpRetry.isRetryableStatus(401));
    }

    @Test
    void deepSeekAutoUsesLowReasoningEffort() {
        assertTrue(OpenAIBackend.isDeepSeekModel("deepseek-v4-flash"));
        assertTrue(OpenAIBackend.isDeepSeekModel("deepseek/deepseek-v4-flash"));
        assertFalse(OpenAIBackend.isDeepSeekModel("gpt-4o-mini"));

        JsonObject auto = new JsonObject();
        OpenAIBackend.applyReasoningEffort(auto, "deepseek-v4-flash", "auto");
        assertEquals("low", auto.get("reasoning_effort").getAsString());
        assertEquals("enabled", auto.getAsJsonObject("thinking").get("type").getAsString());

        JsonObject off = new JsonObject();
        OpenAIBackend.applyReasoningEffort(off, "deepseek-v4-flash", "none");
        assertFalse(off.has("reasoning_effort"));
        assertEquals("disabled", off.getAsJsonObject("thinking").get("type").getAsString());

        JsonObject other = new JsonObject();
        OpenAIBackend.applyReasoningEffort(other, "gpt-4o-mini", "auto");
        assertFalse(other.has("reasoning_effort"));
        assertFalse(other.has("thinking"));
    }
}
