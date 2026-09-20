package com.manuskript.buchpreview;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HtmlExportFinderTest {

    @Test
    void findsHtml5ExportFolder(@TempDir Path project) throws Exception {
        Path htmlDir = project.resolve("Buch_html");
        Files.createDirectories(htmlDir);
        Files.writeString(htmlDir.resolve("Buch.html"), "<html><body><p>Hallo</p></body></html>",
                StandardCharsets.UTF_8);
        Files.createDirectories(project.resolve("data"));
        Files.writeString(project.resolve("data").resolve("note.txt"), "x", StandardCharsets.UTF_8);

        List<HtmlExportFinder.HtmlExport> found = HtmlExportFinder.find(project);
        assertEquals(1, found.size());
        assertTrue(found.get(0).htmlFile().getFileName().toString().endsWith(".html"));
        assertTrue(found.get(0).label().contains("Buch_html"));
    }

    @Test
    void skipsPandocTemplateHtml(@TempDir Path project) throws Exception {
        Path htmlDir = project.resolve("Buch_html");
        Files.createDirectories(htmlDir);
        Files.writeString(htmlDir.resolve("template.html"), "<html><body>$body$</body></html>",
                StandardCharsets.UTF_8);
        Files.writeString(htmlDir.resolve("_taschenbuch_preview.html"), "<html></html>",
                StandardCharsets.UTF_8);
        Files.writeString(htmlDir.resolve("Buch.html"), "<html><body><p>Hallo Welt</p></body></html>",
                StandardCharsets.UTF_8);
        List<HtmlExportFinder.HtmlExport> found = HtmlExportFinder.find(project);
        assertEquals(1, found.size());
        assertEquals("Buch.html", found.get(0).htmlFile().getFileName().toString());
    }

    @Test
    void findsExportViaOutputDirectoryInMetadata(@TempDir Path project, @TempDir Path output) throws Exception {
        Path htmlDir = output.resolve("Roman_html");
        Files.createDirectories(htmlDir);
        Files.writeString(htmlDir.resolve("Roman.html"), "<html><body><p>Export</p></body></html>",
                StandardCharsets.UTF_8);
        Path data = project.resolve("data");
        Files.createDirectories(data);
        Files.writeString(data.resolve("pandoc_metadata.json"), """
                {"outputDirectory":"%s","abstract":"Blurb","coverImage":""}
                """.formatted(output.toString().replace("\\", "\\\\")), StandardCharsets.UTF_8);

        List<HtmlExportFinder.HtmlExport> found = HtmlExportFinder.find(project);
        assertEquals(1, found.size());
        assertTrue(found.get(0).htmlFile().getFileName().toString().equals("Roman.html"));
    }

    @Test
    void relocatesWindowsExportFolderNextToProject(@TempDir Path project) throws Exception {
        Path htmlDir = project.resolve("exporte").resolve("Roman_html");
        Files.createDirectories(htmlDir);
        Files.writeString(htmlDir.resolve("Roman.html"), "<html><body><p>Export</p></body></html>",
                StandardCharsets.UTF_8);
        Path data = project.resolve("data");
        Files.createDirectories(data);
        Files.writeString(data.resolve("pandoc_metadata.json"), """
                {"outputDirectory":"G:\\\\exporte","abstract":"Klappentext"}
                """, StandardCharsets.UTF_8);

        List<HtmlExportFinder.HtmlExport> found = HtmlExportFinder.find(project);
        assertEquals(1, found.size());
        assertEquals("Roman.html", found.get(0).htmlFile().getFileName().toString());
        assertEquals("Klappentext", ExportMetadata.load(project).blurb);
    }

    @Test
    void relocatesWindowsExporteToExportFolder(@TempDir Path project) throws Exception {
        Path htmlDir = project.resolve("export").resolve("Roman_html");
        Files.createDirectories(htmlDir);
        Files.writeString(htmlDir.resolve("Roman.html"), "<html><body><p>Export</p></body></html>",
                StandardCharsets.UTF_8);
        Path data = project.resolve("data");
        Files.createDirectories(data);
        Files.writeString(data.resolve("pandoc_metadata.json"), """
                {"outputDirectory":"G:\\\\exporte","abstract":"X"}
                """, StandardCharsets.UTF_8);
        List<HtmlExportFinder.HtmlExport> found = HtmlExportFinder.find(project);
        assertEquals(1, found.size());
        assertEquals("Roman.html", found.get(0).htmlFile().getFileName().toString());
    }

    @Test
    void skipsHistory(@TempDir Path project) throws Exception {
        Path hidden = project.resolve(".history").resolve("alt_html");
        Files.createDirectories(hidden);
        Files.writeString(hidden.resolve("x.html"), "<html></html>", StandardCharsets.UTF_8);
        assertTrue(HtmlExportFinder.find(project).isEmpty());
    }

    @Test
    void prefersProjectBookOverLargerOtherExport(@TempDir Path root, @TempDir Path output) throws Exception {
        Path project = root.resolve("Der Gott von Demirantha");
        Files.createDirectories(project.resolve("data"));
        Files.writeString(project.resolve("data").resolve("pandoc_metadata.json"), """
                {"title":"Der Gott von Demirantha","outputDirectory":"%s","abstract":"Blurb"}
                """.formatted(output.toString().replace("\\", "\\\\")), StandardCharsets.UTF_8);

        Path held = output.resolve("Held_neu_html");
        Path gott = output.resolve("Der_Gott_von_Demirantha_html");
        Files.createDirectories(held);
        Files.createDirectories(gott);
        Files.writeString(held.resolve("Held_neu.html"), "<html><body>" + "x".repeat(80_000) + "</body></html>",
                StandardCharsets.UTF_8);
        Files.writeString(gott.resolve("Der_Gott_von_Demirantha.html"),
                "<html><body><p>Gott</p><img src=\"familie.png\"></body></html>",
                StandardCharsets.UTF_8);

        List<HtmlExportFinder.HtmlExport> found = HtmlExportFinder.find(project);
        assertEquals(2, found.size());
        assertTrue(found.get(0).label().contains("Der_Gott_von_Demirantha"), found.get(0).label());
    }

    @Test
    void prefersProjectFolderWhenMetadataTitleIsFromAnotherBook(@TempDir Path root, @TempDir Path output)
            throws Exception {
        Path project = root.resolve("Kuppelwelt").resolve("Kuppelwelt 0");
        Files.createDirectories(project.resolve("data"));
        Files.writeString(project.resolve("data").resolve("pandoc_metadata.json"), """
                {"title":"Der Gott von Demirantha","outputDirectory":"%s","abstract":"Jomar"}
                """.formatted(output.toString().replace("\\", "\\\\")), StandardCharsets.UTF_8);

        Path gott = output.resolve("Der_Gott_von_Demirantha_html");
        Path kuppel = output.resolve("Kuppelwelt_0_html");
        Files.createDirectories(gott);
        Files.createDirectories(kuppel);
        Files.writeString(gott.resolve("Der_Gott_von_Demirantha.html"),
                "<html><body>" + "g".repeat(80_000) + "</body></html>", StandardCharsets.UTF_8);
        Files.writeString(kuppel.resolve("Kuppelwelt_0.html"),
                "<html><body><p>Kuppel</p></body></html>", StandardCharsets.UTF_8);

        List<HtmlExportFinder.HtmlExport> found = HtmlExportFinder.find(project);
        assertEquals(2, found.size());
        assertTrue(found.get(0).label().contains("Kuppelwelt_0"), found.get(0).label());
    }

    @Test
    void slugMatchesSpacedProjectAndUnderscoreExport() {
        assertEquals("dergottvondemirantha", HtmlExportFinder.slug("Der Gott von Demirantha"));
        assertEquals("dergottvondemirantha", HtmlExportFinder.slug("Der_Gott_von_Demirantha"));
        assertEquals("heldneu", HtmlExportFinder.slug("Held_neu"));
    }

    @Test
    void skipsPreviewShellHtml(@TempDir Path project) throws Exception {
        Path htmlDir = project.resolve("Buch_html");
        Files.createDirectories(htmlDir);
        Files.writeString(htmlDir.resolve("_buch_preview.html"), "<html></html>", StandardCharsets.UTF_8);
        Files.writeString(htmlDir.resolve("Buch.html"), "<html><body><p>Hallo</p></body></html>",
                StandardCharsets.UTF_8);
        List<HtmlExportFinder.HtmlExport> found = HtmlExportFinder.find(project);
        assertEquals(1, found.size());
        assertEquals("Buch.html", found.get(0).htmlFile().getFileName().toString());
    }
}
