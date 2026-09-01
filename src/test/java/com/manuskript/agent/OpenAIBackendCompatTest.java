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
    void reasoningEffortIsOnlySentWhenExplicitlyConfigured() {
        JsonObject none = new JsonObject();
        OpenAIBackend.applyReasoningEffort(none, "none");
        assertFalse(none.has("reasoning_effort"));
        assertFalse(none.has("thinking"));

        JsonObject auto = new JsonObject();
        OpenAIBackend.applyReasoningEffort(auto, "auto");
        assertFalse(auto.has("reasoning_effort"));
        assertFalse(auto.has("thinking"));

        JsonObject low = new JsonObject();
        OpenAIBackend.applyReasoningEffort(low, "low");
        assertEquals("low", low.get("reasoning_effort").getAsString());
        assertFalse(low.has("thinking"));

        JsonObject high = new JsonObject();
        OpenAIBackend.applyReasoningEffort(high, "high");
        assertEquals("high", high.get("reasoning_effort").getAsString());
        assertFalse(high.has("thinking"));
    }
}
