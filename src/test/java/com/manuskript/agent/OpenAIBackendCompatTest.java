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
}
