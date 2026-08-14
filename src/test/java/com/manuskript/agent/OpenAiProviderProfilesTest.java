package com.manuskript.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiProviderProfilesTest {

    @Test
    void defaultsContainOpenAiAndMammouth() {
        List<OpenAiProviderProfiles.Profile> defaults = OpenAiProviderProfiles.defaultProfiles();
        assertNotNull(OpenAiProviderProfiles.findByName(defaults, "OpenAI"));
        assertNotNull(OpenAiProviderProfiles.findByName(defaults, "Mammouth"));
        assertNotNull(OpenAiProviderProfiles.findByName(defaults, "OpenRouter"));
        assertNull(OpenAiProviderProfiles.findByName(defaults, "TurboFieldfare"));
        assertEquals("https://api.mammouth.ai/v1",
                OpenAiProviderProfiles.findByName(defaults, "Mammouth").apiUrl());
    }

    @Test
    void parseAndUpsertRoundTrip() {
        String json = """
                [
                  {"name":"OpenAI","apiUrl":"https://api.openai.com/v1","apiKey":"sk-a","model":"gpt-4o-mini"},
                  {"name":"Mammouth","apiUrl":"https://api.mammouth.ai/v1","apiKey":"mm-1","model":"x"}
                ]
                """;
        List<OpenAiProviderProfiles.Profile> parsed = OpenAiProviderProfiles.parse(json);
        assertEquals(2, parsed.size());

        List<OpenAiProviderProfiles.Profile> updated = OpenAiProviderProfiles.upsert(
                parsed,
                new OpenAiProviderProfiles.Profile("Mammouth", "https://api.mammouth.ai/v1", "mm-2", "y"));
        OpenAiProviderProfiles.Profile mammouth = OpenAiProviderProfiles.findByName(updated, "mammouth");
        assertNotNull(mammouth);
        assertEquals("mm-2", mammouth.apiKey());
        assertEquals("y", mammouth.model());

        List<OpenAiProviderProfiles.Profile> removed =
                OpenAiProviderProfiles.removeByName(updated, "OpenAI");
        assertNull(OpenAiProviderProfiles.findByName(removed, "OpenAI"));
        assertTrue(OpenAiProviderProfiles.findByName(removed, "Mammouth") != null);
    }

    @Test
    void healRestoresCloudUrlWhenCorruptedWithLoopback() {
        OpenAiProviderProfiles.Profile corrupted = new OpenAiProviderProfiles.Profile(
                "OpenRouter",
                "http://127.0.0.1:8080/v1",
                "sk-or-test",
                "deepseek-v4-flash");
        OpenAiProviderProfiles.Profile healed = OpenAiProviderProfiles.healKnownProfile(corrupted);
        assertEquals("https://openrouter.ai/api/v1", healed.apiUrl());
        assertEquals("sk-or-test", healed.apiKey());
        assertEquals("deepseek-v4-flash", healed.model());
    }

    @Test
    void parsePreservesApiKeyWithoutTouchingUserStore() {
        String json = """
                [{"name":"RoundTripTest","apiUrl":"https://example.test/v1","apiKey":"sk-secret-key","model":"model-x"}]
                """;
        OpenAiProviderProfiles.Profile loaded =
                OpenAiProviderProfiles.findByName(OpenAiProviderProfiles.parse(json), "RoundTripTest");
        assertNotNull(loaded);
        assertEquals("sk-secret-key", loaded.apiKey());
        assertEquals("https://example.test/v1", loaded.apiUrl());
    }
}
