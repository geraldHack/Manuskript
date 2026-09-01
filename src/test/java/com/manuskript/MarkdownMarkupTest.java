package com.manuskript;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownMarkupTest {

    @Test
    void headingAndUnderlineAreTheSameVisibleTitle() {
        assertTrue(MarkdownMarkup.equivalent("# Lyra", "<u>Lyra</u>"));
        assertTrue(MarkdownMarkup.equivalent("# Lyra", "Lyra"));
        assertEquals("Lyra", MarkdownMarkup.normalize("# Lyra"));
        assertEquals("Lyra", MarkdownMarkup.normalize("<u>Lyra</u>"));
    }

    @Test
    void emphasisIsResolved() {
        assertTrue(MarkdownMarkup.equivalent("Das ist *kursiv* und **fett**.", "Das ist kursiv und fett."));
    }

    @Test
    void gedankenstrichVariantsMatch() {
        assertTrue(MarkdownMarkup.equivalent("A \u2013 B", "A \u2014 B"));
        assertTrue(MarkdownMarkup.equivalent("A -- B", "A \u2013 B"));
        assertFalse(MarkdownMarkup.equivalent("---", "\u2013"));
    }

    @Test
    void unsavedMarkdownDiffStillSeesEmphasisEdits() {
        DiffProcessor.DiffResult diff = DiffProcessor.createDiff("Hallo Welt.", "Hallo *Welt*.");
        assertTrue(diff.hasChanges());
    }
}

class DocxMarkdownRoundtripTest {

    @Test
    void headingIsNotExportedAsLiteralUnderlineTags(@TempDir Path tmp) throws Exception {
        Path docx = tmp.resolve("lyra.docx");
        DocxProcessor processor = new DocxProcessor();
        processor.exportMarkdownToDocxWithOptions("# Lyra\n\nHallo *kursiv* und **fett**.\n", docx.toFile(), null);

        String reloaded = processor.processDocxFileContent(docx.toFile(), 1, DocxProcessor.OutputFormat.MARKDOWN);
        assertFalse(reloaded.contains("<u>"), "Überschrift darf nicht als <u> in der DOCX stehen");
        assertTrue(reloaded.contains("Lyra"));
        assertTrue(reloaded.contains("*kursiv*") || reloaded.contains("kursiv"));
        assertTrue(reloaded.contains("**fett**") || reloaded.contains("fett"));
    }

    @Test
    void underlineTagsBecomeRealFormattingNotVisibleMarkup(@TempDir Path tmp) throws Exception {
        Path docx = tmp.resolve("underline.docx");
        DocxProcessor processor = new DocxProcessor();
        processor.exportMarkdownToDocxWithOptions("<u>Lyra</u>\n", docx.toFile(), null);

        String xml;
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(docx.toFile())) {
            java.util.zip.ZipEntry entry = zip.getEntry("word/document.xml");
            xml = new String(zip.getInputStream(entry).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        assertFalse(xml.contains("&lt;u&gt;"), "DOCX darf die Tags nicht als sichtbaren Text speichern");
        assertTrue(xml.contains("Lyra"));
    }
}
