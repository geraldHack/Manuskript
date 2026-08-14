package com.manuskript;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PandocFootnoteFormatIntegrationTest {

    private static final String SAMPLE =
            "# Kapitel\n\nText mit Quelle^[Die Fußnote mit **Betonung**.] und Schluss.\n";

    private static final String MULTILINE_SAMPLE =
            "Ich stürzte durch Schichten von Dunkelheit^[Das ist meine Fußnote\n"
                    + "\nMehrzeilig!], durch etwas, das sich anfühlte wie Wasser.\n";

    @TempDir
    Path tempDir;

    @Test
    void createsNativeFootnotesInDocxLatexAndRtf() throws Exception {
        String pandoc = locateCommand("pandoc");
        assumeTrue(pandoc != null, "Pandoc ist für den Integrationstest optional");
        Path input = tempDir.resolve("footnote.md");
        // Wie im Exportfenster für Druckformate: erst Referenz-Fußnoten, dann Pandoc.
        Files.writeString(input,
                MarkdownFootnoteSupport.toReferenceMarkdown(MULTILINE_SAMPLE),
                StandardCharsets.UTF_8);

        Path docx = runPandoc(pandoc, input, "docx", "docx");
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            ZipEntry footnotes = zip.getEntry("word/footnotes.xml");
            assertTrue(footnotes != null, "DOCX muss einen nativen Fußnoten-Part enthalten");
            String xml = new String(zip.getInputStream(footnotes).readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(xml.contains("meine Fußnote"));
            assertTrue(xml.contains("Mehrzeilig"));
        }

        String latex = Files.readString(runPandoc(pandoc, input, "latex", "tex"));
        assertTrue(latex.contains("\\footnote{"));
        assertFalse(latex.contains("^["));

        String rtf = Files.readString(runPandoc(pandoc, input, "rtf", "rtf"));
        assertTrue(rtf.contains("\\footnote"));
        assertFalse(rtf.contains("^["));
    }

    @Test
    void htmlAndEpubOmitFootnotesEntirely() throws Exception {
        String pandoc = locateCommand("pandoc");
        assumeTrue(pandoc != null, "Pandoc ist für den Integrationstest optional");
        Path input = tempDir.resolve("digital.md");
        Files.writeString(input,
                MarkdownFootnoteSupport.stripFootnotes(MULTILINE_SAMPLE),
                StandardCharsets.UTF_8);

        String html = Files.readString(runPandoc(pandoc, input, "html5", "html"));
        assertFalse(html.contains("footnote"));
        assertFalse(html.contains("^["));
        assertFalse(html.contains("Meine Fußnote") || html.contains("meine Fußnote"));
        assertTrue(html.contains("Dunkelheit"));
        assertTrue(html.contains("Wasser"));

        Path epub = runPandoc(pandoc, input, "epub3", "epub");
        String epubMarkup = zipTextEntries(epub, ".xhtml", ".html");
        assertFalse(epubMarkup.contains("footnote"));
        assertFalse(epubMarkup.contains("^["));
        assertFalse(epubMarkup.contains("Mehrzeilig"));
    }

    @Test
    void multilineInlineFootnoteBecomesNativeDocxFootnoteViaExporter() throws Exception {
        assumeTrue(locateCommand("pandoc") != null, "Pandoc ist für den Integrationstest optional");
        Path docx = tempDir.resolve("multiline.docx");
        PandocFootnoteDocxExporter.export(MULTILINE_SAMPLE, docx.toFile());
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            ZipEntry footnotes = zip.getEntry("word/footnotes.xml");
            assertTrue(footnotes != null, "DOCX muss einen nativen Fußnoten-Part enthalten");
            String xml = new String(zip.getInputStream(footnotes).readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(xml.contains("meine Fußnote"));
            assertTrue(xml.contains("Mehrzeilig"));
            String document = new String(
                    zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(),
                    StandardCharsets.UTF_8);
            assertFalse(document.contains("^["));
        }
    }

    @Test
    void referenceDocxAndPdfKeepFootnoteWhenToolsAreAvailable() throws Exception {
        String pandoc = locateCommand("pandoc");
        assumeTrue(pandoc != null, "Pandoc ist für den Integrationstest optional");
        Path input = tempDir.resolve("footnote.md");
        Files.writeString(input, SAMPLE, StandardCharsets.UTF_8);

        Path reference = Path.of("pandoc", "reference-verlagsmanuskript.docx").toAbsolutePath();
        if (Files.isRegularFile(reference)) {
            Path docx = runPandoc(pandoc, input, "docx", "reference.docx",
                    "--reference-doc=" + reference);
            try (ZipFile zip = new ZipFile(docx.toFile())) {
                assertTrue(zip.getEntry("word/footnotes.xml") != null);
            }
        }

        String xelatex = locateCommand("xelatex");
        assumeTrue(xelatex != null, "PDF-Prüfung benötigt XeLaTeX");
        Path pdf = runPandoc(pandoc, input, null, "footnote.pdf", "--pdf-engine=" + xelatex);
        byte[] magic = Files.readAllBytes(pdf);
        assertTrue(magic.length > 4
                && magic[0] == '%' && magic[1] == 'P' && magic[2] == 'D' && magic[3] == 'F');
    }

    private Path runPandoc(String pandoc, Path input, String to, String fileName, String... extras)
            throws Exception {
        Path output = tempDir.resolve(fileName);
        List<String> command = new ArrayList<>();
        command.add(pandoc);
        command.add("--from=" + PandocExportWindow.pandocMarkdownReaderFormat());
        if (to != null) {
            command.add("--to=" + to);
        }
        command.add("--output=" + output);
        command.addAll(List.of(extras));
        command.add(input.toString());
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String log = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = process.waitFor();
        assertTrue(exit == 0, () -> "Pandoc fehlgeschlagen: " + log);
        assertTrue(Files.isRegularFile(output));
        return output;
    }

    private static String zipTextEntries(Path zipPath, String... suffixes) throws IOException {
        StringBuilder content = new StringBuilder();
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                for (String suffix : suffixes) {
                    if (entry.getName().endsWith(suffix)) {
                        content.append(new String(
                                zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8));
                    }
                }
            }
        }
        return content.toString();
    }

    private static String locateCommand(String name) {
        try {
            Process process = new ProcessBuilder("/bin/sh", "-c", "command -v " + name)
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return process.waitFor() == 0 && !output.isBlank() ? output : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
