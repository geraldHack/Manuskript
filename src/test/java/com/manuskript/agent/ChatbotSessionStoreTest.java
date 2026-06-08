package com.manuskript.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.manuskript.CustomChatArea;

class ChatbotSessionStoreTest {

    @TempDir
    File projectDir;

    @Test
    void saveAndLoadRoundtrip() {
        ChatbotSessionData data = ChatbotSessionStore.newSession("Test-Session");
        data.getQaPairs().add(new CustomChatArea.QAPair("Hallo?", "Ja!"));
        data.applyContextConfig(new ChatbotContextConfig(
                EnumSet.of(ChatbotContextSource.CURRENT_CHAPTER, ChatbotContextSource.WORLD_EDITOR),
                2, 4, ChatbotContextSize.EXTENDED));
        data.setTemperature(0.5);
        data.setUseParameterModel(false);
        data.setModel("gpt-4o");

        ChatbotSessionStore.save(projectDir, data);
        ChatbotSessionData loaded = ChatbotSessionStore.load(projectDir, "Test-Session");

        assertEquals("Test-Session", loaded.getName());
        assertEquals(1, loaded.getQaPairs().size());
        assertEquals("Hallo?", loaded.getQaPairs().get(0).getQuestion());
        assertEquals("Ja!", loaded.getQaPairs().get(0).getAnswer());
        assertTrue(loaded.toContextConfig().hasSource(ChatbotContextSource.WORLD_EDITOR));
        assertEquals(2, loaded.getChaptersBefore());
        assertEquals(4, loaded.getChaptersAfter());
        assertEquals(0.5, loaded.getTemperature(), 0.001);
        assertFalse(loaded.isUseParameterModel());
        assertEquals("gpt-4o", loaded.getModel());
    }

    @Test
    void listAndDeleteSessions() {
        ChatbotSessionStore.save(projectDir, ChatbotSessionStore.newSession("A"));
        ChatbotSessionStore.save(projectDir, ChatbotSessionStore.newSession("B"));

        List<String> names = ChatbotSessionStore.listSessionNames(projectDir);
        assertEquals(2, names.size());

        ChatbotSessionStore.delete(projectDir, "A");
        names = ChatbotSessionStore.listSessionNames(projectDir);
        assertEquals(1, names.size());
        assertEquals("B", names.get(0));
    }

    @Test
    void ensureStandardSessionRecreatesMissingFile() {
        ChatbotSessionStore.save(projectDir, ChatbotSessionStore.newSession("Andere"));
        ChatbotSessionStore.ensureStandardSession(projectDir);

        List<String> names = ChatbotSessionStore.listSessionNames(projectDir);
        assertTrue(names.stream().anyMatch(ChatbotSessionStore::isStandardSession));
    }

    @Test
    void pickFallbackPrefersStandard() {
        List<String> names = List.of("Brainstorm", ChatbotSessionStore.STANDARD_SESSION_NAME, "Recherche");
        assertEquals(ChatbotSessionStore.STANDARD_SESSION_NAME,
                ChatbotSessionStore.pickFallbackSessionName(names));
    }

    @Test
    void qaPairsToTurnsAlternatesRoles() {
        List<CustomChatArea.QAPair> pairs = List.of(
                new CustomChatArea.QAPair("Q1", "A1"),
                new CustomChatArea.QAPair("Q2", "")
        );
        List<ChatTurn> turns = ChatbotSessionStore.qaPairsToTurns(pairs);
        assertEquals(3, turns.size());
        assertEquals("user", turns.get(0).role());
        assertEquals("Q1", turns.get(0).content());
        assertEquals("assistant", turns.get(1).role());
        assertEquals("Q2", turns.get(2).content());
    }
}
