package com.manuskript;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanguageToolDictionaryTest {

    @Test
    void persistsWordsToConfiguredPath(@TempDir Path temp) throws Exception {
        Path dict = temp.resolve("config").resolve("languagetool-dictionary.txt");
        LanguageToolDictionary dictionary = new LanguageToolDictionary(dict);
        dictionary.addWord("Alduria");
        dictionary.addWord("Asthenar");

        assertTrue(Files.isRegularFile(dict));
        String content = Files.readString(dict);
        assertTrue(content.contains("alduria"));
        assertTrue(content.contains("asthenar"));

        LanguageToolDictionary reloaded = new LanguageToolDictionary(dict);
        assertEquals(2, reloaded.getWordCount());
        assertTrue(reloaded.containsWord("Alduria"));
    }
}
