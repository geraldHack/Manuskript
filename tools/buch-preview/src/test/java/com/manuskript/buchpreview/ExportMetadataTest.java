package com.manuskript.buchpreview;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportMetadataTest {

    @Test
    void readsAbstractAndCoverFromPandocMetadata(@TempDir Path project) throws Exception {
        Path data = project.resolve("data");
        Files.createDirectories(data);
        Files.writeString(data.resolve("pandoc_metadata.json"), """
                {
                  "title": "Der Gott",
                  "author": "Autorin",
                  "abstract": "Klappentext aus dem Exportmodul.",
                  "coverImage": "cover.png"
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(project.resolve("cover.png"), "fake", StandardCharsets.UTF_8);

        ExportMetadata meta = ExportMetadata.load(project);
        assertEquals("Der Gott", meta.title);
        assertEquals("Autorin", meta.author);
        assertEquals("Klappentext aus dem Exportmodul.", meta.blurb);
        assertEquals("cover.png", meta.coverPath);
        assertTrue(Files.isRegularFile(meta.resolveCover(project)));
    }

    @Test
    void readsAuthorNoteForBackCover(@TempDir Path project) throws Exception {
        Path data = project.resolve("data");
        Files.createDirectories(data);
        Files.writeString(data.resolve("pandoc_metadata.json"), """
                {
                  "author": "Mira Vale",
                  "abstract": "Klappentext.",
                  "authorNote": "Lebt in Köln und schreibt Space Opera."
                }
                """, StandardCharsets.UTF_8);
        ExportMetadata meta = ExportMetadata.load(project);
        assertEquals("Mira Vale", meta.author);
        assertEquals("Lebt in Köln und schreibt Space Opera.", meta.authorInfo);
    }

    @Test
    void fallsBackToCoverImagePngAndPublishBlurb(@TempDir Path project) throws Exception {
        Path data = project.resolve("data");
        Files.createDirectories(data);
        Files.writeString(data.resolve("publish_package.json"), """
                {"blurb":"Klappentext aus dem Publish-Paket."}
                """, StandardCharsets.UTF_8);
        Files.writeString(project.resolve("cover_image.png"), "fake", StandardCharsets.UTF_8);

        ExportMetadata meta = ExportMetadata.load(project);
        assertEquals("Klappentext aus dem Publish-Paket.", meta.blurb);
        assertTrue(Files.isRegularFile(meta.resolveCover(project)));
        assertTrue(meta.resolveCover(project).getFileName().toString().equals("cover_image.png"));
    }

    @Test
    void readsOutputDirectory(@TempDir Path project) throws Exception {
        Path data = project.resolve("data");
        Files.createDirectories(data);
        Files.writeString(data.resolve("pandoc_metadata.json"), """
                {"outputDirectory":"/tmp/exporte","abstract":"A"}
                """, StandardCharsets.UTF_8);
        assertEquals("/tmp/exporte", ExportMetadata.load(project).outputDirectory);
    }

    @Test
    void ignoresWindowsOutputDirectoryOnNonWindows(@TempDir Path project) throws Exception {
        Path data = project.resolve("data");
        Files.createDirectories(data);
        Files.writeString(data.resolve("pandoc_metadata.json"), """
                {"outputDirectory":"G:\\\\exporte","abstract":"A","coverImage":"C:\\\\covers\\\\a.png"}
                """, StandardCharsets.UTF_8);
        ExportMetadata meta = ExportMetadata.load(project);
        if (File.separatorChar != '\\') {
            assertEquals(null, meta.resolveOutputDirectory(project));
            assertEquals(null, meta.resolveCover(project));
        }
        assertEquals("A", meta.blurb);
        assertEquals("exporte", ExportMetadata.lastPathSegment("G:\\exporte"));
    }

    @Test
    void missingFileIsEmpty(@TempDir Path project) {
        ExportMetadata meta = ExportMetadata.load(project);
        assertEquals("", meta.blurb);
        assertEquals("", meta.coverPath);
    }
}
