package com.manuskript;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class OllamaStreamChunkTest {

    @Test
    void prefersContentOverThinking() {
        String line = "{\"message\":{\"role\":\"assistant\",\"content\":\"Hallo\",\"thinking\":\"intern\"}}";
        assertEquals("Hallo", OllamaService.extractChatStreamText(line));
    }

    @Test
    void fallsBackToThinkingWhenContentEmpty() {
        String line = "{\"message\":{\"role\":\"assistant\",\"content\":\"\",\"thinking\":\"Qwen-Denken\"}}";
        assertEquals("Qwen-Denken", OllamaService.extractChatStreamText(line));
    }

    @Test
    void returnsNullWithoutMessage() {
        assertNull(OllamaService.extractChatStreamText("{\"done\":false}"));
    }
}
