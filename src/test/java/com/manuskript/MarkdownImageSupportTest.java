package com.manuskript;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownImageSupportTest {

    @Test
    void buildMarkdownStoresOnlyFileName(@TempDir Path tmp) {
        String markdown = MarkdownImageSupport.buildMarkdown("lyra.png", "Lyra", 60);
        assertTrue(markdown.startsWith("![](lyra.png){ width=60% }"));
        assertTrue(markdown.contains("><c>Lyra</c>"));
    }

    @Test
    void copyImageToProjectDirectoryUsesWorkingFolder(@TempDir Path tmp) throws Exception {
        Path source = tmp.resolve("source");
        Files.createDirectory(source);
        Path image = source.resolve("portrait.png");
        Files.write(image, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});

        Path project = tmp.resolve("roman");
        Files.createDirectory(project);

        java.io.File copied = MarkdownImageSupport.copyImageToProjectDirectory(image.toFile(), project.toFile());
        assertEquals("portrait.png", copied.getName());
        assertEquals(project.toFile().getCanonicalPath(), copied.getParentFile().getCanonicalPath());
        assertTrue(copied.isFile());
    }
}
