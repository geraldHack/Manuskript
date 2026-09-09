package com.manuskript;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SceneOutlinePathsTest {

    @TempDir
    Path tempDir;

    @Test
    void mdInDataDoesNotNestDataDirectory() throws Exception {
        Path data = tempDir.resolve("data");
        Files.createDirectories(data);
        Path md = data.resolve("Der Empfang.md");
        Files.writeString(md, "# Der Empfang\n");

        Path scenes = SceneOutlinePaths.scenesFileForMd(md.toFile()).toPath();
        assertEquals(data.resolve("Der Empfang-scenes.txt"), scenes);
    }

    @Test
    void findsExistingScenesNextToMarkdown() throws Exception {
        Path data = tempDir.resolve("data");
        Files.createDirectories(data);
        Files.writeString(tempDir.resolve("Der Empfang.docx"), "docx");
        Files.writeString(data.resolve("Der Empfang.md"), "# Der Empfang\n");
        Files.writeString(data.resolve("Der Empfang-scenes.txt"), "1. Szene\n");

        SceneOutlinePaths.Resolution fromDocx =
                SceneOutlinePaths.resolve(tempDir.resolve("Der Empfang.docx").toFile());
        SceneOutlinePaths.Resolution fromMd =
                SceneOutlinePaths.resolve(data.resolve("Der Empfang.md").toFile());
        SceneOutlinePaths.Resolution best = SceneOutlinePaths.resolveBest(
                tempDir.resolve("Der Empfang.docx").toFile(),
                data.resolve("Der Empfang.md").toFile());

        assertTrue(fromDocx.existing().isFile());
        assertTrue(fromMd.existing().isFile());
        assertEquals("Der Empfang-scenes.txt", best.existing().getName());
        assertEquals("1. Szene\n", Files.readString(best.existing().toPath()));
    }
}
