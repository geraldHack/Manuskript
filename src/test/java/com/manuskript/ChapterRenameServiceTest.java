package com.manuskript;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChapterRenameServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void sanitizeStripsInvalidCharsAndDocxSuffix() {
        assertEquals("Kapitel 1", ChapterRenameService.sanitizeChapterFileName("  Kapitel 1.docx  "));
        assertEquals("A_B", ChapterRenameService.sanitizeChapterFileName("A/B"));
    }

    @Test
    void renameMovesDocxAndDataSidecars() throws Exception {
        Path project = tempDir.resolve("buch");
        Path data = project.resolve("data");
        Files.createDirectories(data.resolve("Kapitel 1-tts"));
        Files.createDirectories(data.resolve(".history").resolve("Kapitel 1"));
        Files.createDirectories(data.resolve("agents").resolve("plot").resolve("Kapitel_1"));

        Path docx = project.resolve("Kapitel 1.docx");
        Files.writeString(docx, "docx");
        Files.writeString(data.resolve("Kapitel 1.md"), "# Kapitel 1\n\nText");
        Files.writeString(data.resolve("Kapitel 1.notes"), "Notiz");
        Files.writeString(data.resolve("Kapitel 1.status"), "fertig");
        Files.writeString(data.resolve("Kapitel 1.docx.meta"), "abc");
        Files.writeString(data.resolve("Kapitel 1-scenes.txt"), "Szene");
        Files.writeString(data.resolve("Kapitel 1-tts-segments.json"), "[]");
        Files.writeString(data.resolve("Kapitel 1-tts-content.md"), "tts");
        Files.writeString(data.resolve("Kapitel 1-tts").resolve("block_001.mp3"), "mp3");
        Files.writeString(data.resolve(".history").resolve("Kapitel 1").resolve("x.md"), "hist");
        Files.writeString(data.resolve("agents").resolve("plot").resolve("Kapitel_1").resolve("latest.md"), "mem");

        ChapterRenameService.Result result = ChapterRenameService.rename(docx.toFile(), "Prolog");

        assertEquals("Prolog.docx", result.newDocxFile.getName());
        assertTrue(result.newDocxFile.isFile());
        assertFalse(Files.exists(docx));
        assertTrue(Files.exists(data.resolve("Prolog.md")));
        assertTrue(Files.readString(data.resolve("Prolog.md")).startsWith("# Prolog\n"));
        assertTrue(Files.exists(data.resolve("Prolog.notes")));
        assertTrue(Files.exists(data.resolve("Prolog.status")));
        assertTrue(Files.exists(data.resolve("Prolog.docx.meta")));
        assertTrue(Files.exists(data.resolve("Prolog-scenes.txt")));
        assertTrue(Files.exists(data.resolve("Prolog-tts-segments.json")));
        assertTrue(Files.exists(data.resolve("Prolog-tts-content.md")));
        assertTrue(Files.exists(data.resolve("Prolog-tts").resolve("block_001.mp3")));
        assertTrue(Files.exists(data.resolve(".history").resolve("Prolog").resolve("x.md")));
        assertTrue(Files.exists(data.resolve("agents").resolve("plot").resolve("Prolog").resolve("latest.md")));
        assertFalse(Files.exists(data.resolve("Kapitel 1.md")));
        assertFalse(Files.exists(data.resolve("Kapitel 1-tts")));
    }

    @Test
    void doesNotRenameUnrelatedKapitel10() throws Exception {
        Path project = tempDir.resolve("buch");
        Path data = project.resolve("data");
        Files.createDirectories(data);
        Files.writeString(project.resolve("Kapitel 1.docx"), "a");
        Files.writeString(project.resolve("Kapitel 10.docx"), "b");
        Files.writeString(data.resolve("Kapitel 1.md"), "eins");
        Files.writeString(data.resolve("Kapitel 10.md"), "zehn");

        ChapterRenameService.rename(project.resolve("Kapitel 1.docx").toFile(), "Anfang");

        assertTrue(Files.exists(data.resolve("Anfang.md")));
        assertTrue(Files.exists(data.resolve("Kapitel 10.md")));
        assertEquals("zehn", Files.readString(data.resolve("Kapitel 10.md"), StandardCharsets.UTF_8));
    }
}
