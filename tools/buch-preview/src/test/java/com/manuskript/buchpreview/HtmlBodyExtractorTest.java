package com.manuskript.buchpreview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HtmlBodyExtractorTest {

    @Test
    void extractsBodyAndPlainText() {
        String html = """
                <html><head><script>var x=1;</script></head>
                <body>
                <h1>Titel</h1>
                <p>Ein Satz mit &amp; Co.</p>
                <script>ignore()</script>
                </body></html>
                """;
        String body = HtmlBodyExtractor.bodyInnerHtml(html);
        assertTrue(body.contains("Ein Satz"));
        assertFalse(body.contains("ignore()"));
        String plain = HtmlBodyExtractor.plainText(html);
        assertEquals("Titel Ein Satz mit & Co.", plain);
        assertEquals(5, TextStats.wordCount(plain));
    }

    @Test
    void displayKeepsQuotesLikeOldPreview() {
        String html = "<html><body><p>»Nein«, sagte sie – und weiter …</p></body></html>";
        String display = HtmlBodyExtractor.displayInnerHtml(html);
        assertTrue(display.contains("»Nein«"));
        assertTrue(display.contains("–"));
        assertTrue(display.contains("…"));
    }

    @Test
    void previewDropsCoverImageAndEmptyParagraphs() {
        String html = """
                <html><body>
                <img src="cover_image.png" alt="Cover" class="cover-image" />
                <p>&nbsp;</p>
                <p>Erster Satz.</p>
                <p> </p>
                </body></html>
                """;
        String preview = HtmlBodyExtractor.previewInnerHtml(html);
        assertFalse(preview.contains("<img"));
        assertFalse(preview.contains("cover-image"));
        assertTrue(preview.contains("Erster Satz."));
        assertEquals("Erster Satz.", HtmlBodyExtractor.plainText(html));
    }

    @Test
    void previewReplacesAngleQuotesThatRenderAsBoxes() {
        String html = "<html><body><p>die sogenannten ›dunklen Muster‹</p></body></html>";
        String preview = HtmlBodyExtractor.previewInnerHtml(html);
        assertFalse(preview.contains("\u2039"));
        assertFalse(preview.contains("\u203a"));
        assertTrue(preview.contains("die sogenannten 'dunklen Muster'"));
    }

    @Test
    void previewReplacesGuillemetsThatRenderAsBoxes() {
        String html = "<html><body><p>»Nein«, sagte sie – und weiter …</p></body></html>";
        String preview = HtmlBodyExtractor.previewInnerHtml(html);
        assertFalse(preview.contains("\u00bb"));
        assertFalse(preview.contains("\u00ab"));
        assertFalse(preview.contains("\u2013"));
        assertTrue(preview.contains("\"Nein\", sagte sie - und weiter ..."));
    }

    @Test
    void previewDeletesNonPrintableAndBoxCharacters() {
        String html = "<html><body><p>Hallo\u0007\u00AD\u200B\uFFFD\u25A1Welt</p></body></html>";
        String preview = HtmlBodyExtractor.previewInnerHtml(html);
        assertEquals("<p>HalloWelt</p>", preview.trim());
        assertFalse(preview.contains("\u00AD"));
        assertFalse(preview.contains("\u25A1"));
        assertFalse(preview.contains("\uFFFD"));
    }

    @Test
    void stripKeepsTagsAndGermanLetters() {
        String html = "<p class=\"x\">Käse &amp; Öl</p>";
        assertEquals(html, HtmlBodyExtractor.stripNonPrintable(html));
    }

    @Test
    void extractsAbstractDiv() {
        String html = """
                <html><body>
                <div class="abstract"><h3>Zusammenfassung</h3><p>Kata ist eine Diebin.</p></div>
                <p>Kapiteltext</p>
                </body></html>
                """;
        assertEquals("Zusammenfassung Kata ist eine Diebin.", HtmlBodyExtractor.abstractText(html));
    }
}
