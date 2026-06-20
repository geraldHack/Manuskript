package com.manuskript.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.manuskript.DocxFile;

class ChatbotContextBuilderTest {

    @TempDir
    File tempDir;

    @Test
    void truncateHistoryKeepsLastNTurns() {
        List<ChatTurn> turns = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            turns.add(ChatTurn.user("q" + i));
            turns.add(ChatTurn.assistant("a" + i));
        }
        List<ChatTurn> trimmed = ChatbotContextBuilder.truncateHistory(turns, 4);
        assertEquals(4, trimmed.size());
        assertEquals("q8", trimmed.get(0).content());
        assertEquals("a9", trimmed.get(3).content());
    }

    @Test
    void loadNeighborChaptersExcludesCurrent() throws Exception {
        File dataDir = new File(tempDir, "data");
        Files.createDirectories(dataDir.toPath());
        File ch1 = new File(dataDir, "kapitel1.md");
        File ch2 = new File(dataDir, "kapitel2.md");
        File ch3 = new File(dataDir, "kapitel3.md");
        Files.writeString(ch1.toPath(), "Kapitel 1 Text", StandardCharsets.UTF_8);
        Files.writeString(ch2.toPath(), "Kapitel 2 Text", StandardCharsets.UTF_8);
        Files.writeString(ch3.toPath(), "Kapitel 3 Text", StandardCharsets.UTF_8);

        File docx1 = new File(tempDir, "kapitel1.docx");
        File docx2 = new File(tempDir, "kapitel2.docx");
        File docx3 = new File(tempDir, "kapitel3.docx");
        Files.writeString(docx1.toPath(), "", StandardCharsets.UTF_8);
        Files.writeString(docx2.toPath(), "", StandardCharsets.UTF_8);
        Files.writeString(docx3.toPath(), "", StandardCharsets.UTF_8);

        List<DocxFile> order = List.of(
                new DocxFile(docx1),
                new DocxFile(docx2),
                new DocxFile(docx3)
        );

        String neighbors = ChatbotContextBuilder.loadNeighborChapters(
                tempDir, docx2, order, 1, 1, ChatbotContextSize.FULL);
        assertTrue(neighbors.contains("Kapitel 1 Text"));
        assertTrue(neighbors.contains("Kapitel 3 Text"));
        assertFalse(neighbors.contains("Kapitel 2 Text"));
    }

    @Test
    void buildIncludesWorldFilesWhenPillActive() throws Exception {
        Files.writeString(new File(tempDir, "characters.txt").toPath(),
                "Figur A", StandardCharsets.UTF_8);
        ChatbotContextConfig config = new ChatbotContextConfig();
        config.addSource(ChatbotContextSource.WORLD_EDITOR);
        config.setContextSize(ChatbotContextSize.FULL);

        String ctx = ChatbotContextBuilder.build(
                tempDir, null, null, null, null, null, List.of(), config);
        assertTrue(ctx.contains("Figur A"));
        assertTrue(ctx.contains("CHARAKTERE"));
    }

    @Test
    void buildIncludesAllChaptersFromDocxDataDir() throws Exception {
        File docx1 = new File(tempDir, "kapitel1.docx");
        File docx2 = new File(tempDir, "kapitel2.docx");
        Files.writeString(docx1.toPath(), "", StandardCharsets.UTF_8);
        Files.writeString(docx2.toPath(), "", StandardCharsets.UTF_8);
        File dataDir = new File(tempDir, "data");
        Files.createDirectories(dataDir.toPath());
        Files.writeString(new File(dataDir, "kapitel1.md").toPath(), "Inhalt eins", StandardCharsets.UTF_8);
        Files.writeString(new File(dataDir, "kapitel2.md").toPath(), "Inhalt zwei", StandardCharsets.UTF_8);

        List<DocxFile> order = List.of(new DocxFile(docx1), new DocxFile(docx2));
        ChatbotContextConfig config = new ChatbotContextConfig();
        config.addSource(ChatbotContextSource.ALL_CHAPTERS);
        config.setContextSize(ChatbotContextSize.FULL);

        String ctx = ChatbotContextBuilder.build(
                tempDir, null, null, null, null, null, order, config);
        assertTrue(ctx.contains("ALLE KAPITEL"));
        assertTrue(ctx.contains("Inhalt eins"));
        assertTrue(ctx.contains("Inhalt zwei"));
    }

    @Test
    void loadAllChaptersFromOrderSkipsMissingMd() throws Exception {
        File docx1 = new File(tempDir, "a.docx");
        Files.writeString(docx1.toPath(), "", StandardCharsets.UTF_8);
        File dataDir = new File(tempDir, "data");
        Files.createDirectories(dataDir.toPath());
        Files.writeString(new File(dataDir, "a.md").toPath(), "Nur A", StandardCharsets.UTF_8);

        String all = ChatbotContextBuilder.loadAllChaptersFromOrder(
                List.of(new DocxFile(docx1), new DocxFile(new File(tempDir, "fehlt.docx"))), null);
        assertTrue(all.contains("Nur A"));
        assertFalse(all.contains("fehlt"));
    }

    @Test
    void buildTruncatesWhenCompactBudgetExceeded() {
        String longText = "x".repeat(50_000);
        ChatbotContextConfig config = new ChatbotContextConfig();
        config.addSource(ChatbotContextSource.CURRENT_CHAPTER);
        config.setContextSize(ChatbotContextSize.COMPACT);

        String ctx = ChatbotContextBuilder.build(
                tempDir, null, "test.md", longText, null, null, List.of(), config);
        assertTrue(ctx.length() < longText.length());
        assertTrue(ctx.contains("gekürzt"));
    }
}
