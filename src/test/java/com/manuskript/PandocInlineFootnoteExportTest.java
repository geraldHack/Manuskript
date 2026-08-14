package com.manuskript;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PandocInlineFootnoteExportTest {

    @Test
    void markdownReaderEnablesInlineFootnotesExplicitly() {
        String format = PandocExportWindow.pandocMarkdownReaderFormat();
        assertTrue(format.contains("+footnotes"));
        assertTrue(format.contains("+inline_notes"));
    }

    @Test
    void digitalFormatsOmitFootnotesWhilePrintFormatsKeepThem() {
        assertTrue(PandocExportWindow.isFootnotelessDigitalFormat("html5"));
        assertTrue(PandocExportWindow.isFootnotelessDigitalFormat("epub3"));
        assertFalse(PandocExportWindow.isFootnotelessDigitalFormat("docx"));
        assertFalse(PandocExportWindow.isFootnotelessDigitalFormat("pdf"));
        assertFalse(PandocExportWindow.isFootnotelessDigitalFormat("rtf"));
    }

    @Test
    void bookParagraphFormattingKeepsMultilineInlineFootnoteTogether() {
        String markdown = "Erste Zeile\n"
                + "Text mit ^[Eine Fußnote\n"
                + "mit [verschachteltem] Inhalt.] danach.\n"
                + "Letzte Zeile";

        String formatted = MainController.formatMarkdownParagraphs(markdown);

        assertTrue(formatted.contains(
                "^[Eine Fußnote\nmit [verschachteltem] Inhalt.]"));
    }
}
