package com.manuskript;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DocxQuoteRoundtripTest {

    @Test
    void germanQuotesSurviveDocxExportAndReload(@TempDir Path tmp) throws Exception {
        String markdown = "Er sagte: \u201EKomm her.\u201C\n\n"
                + "\u201EDas geht\n\nüber zwei Absätze.\u201C\n";
        Path docx = tmp.resolve("zitate.docx");

        DocxProcessor processor = new DocxProcessor();
        processor.exportMarkdownToDocxWithOptions(markdown, docx.toFile(), null);

        String reloaded = processor.processDocxFileContent(docx.toFile(), 1, DocxProcessor.OutputFormat.MARKDOWN);
        assertTrue(reloaded.contains("\u201E"), "öffnendes deutsches Anführungszeichen fehlt in der DOCX");
        assertTrue(reloaded.contains("\u201C"), "schließendes deutsches Anführungszeichen fehlt in der DOCX");
        assertTrue(reloaded.contains("Komm her"));
        assertTrue(reloaded.contains("über zwei Absätze"));
    }
}
