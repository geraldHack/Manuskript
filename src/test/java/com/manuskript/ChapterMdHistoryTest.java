package com.manuskript;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChapterMdHistoryTest {

    @TempDir
    Path temp;

    @Test
    void beforeLektoratUsesNiceHistoryLabel() throws Exception {
        Path data = temp.resolve("data");
        Files.createDirectories(data);
        Path md = data.resolve("Kapitel 1.md");
        Files.writeString(md, "Ursprungstext", StandardCharsets.UTF_8);

        ChapterMdHistory.snapshotFromFile(md.toFile(), ChapterMdHistory.Reason.BEFORE_LEKTORAT);

        List<ChapterMdHistory.Entry> versions = ChapterMdHistory.listVersions(md.toFile());
        assertEquals(1, versions.size());
        assertEquals(ChapterMdHistory.Reason.BEFORE_LEKTORAT, versions.get(0).reason());
        assertTrue(versions.get(0).displayLabel().endsWith("Stand vor Lektorat"));
        assertEquals("Ursprungstext", ChapterMdHistory.readEntryContent(md.toFile(), versions.get(0)));
    }
}
