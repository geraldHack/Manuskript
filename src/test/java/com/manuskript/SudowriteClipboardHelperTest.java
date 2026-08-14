package com.manuskript;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SudowriteClipboardHelperTest {

    @Test
    void htmlUsesSuperscriptReferencesAndFootnoteBlock() {
        String html = SudowriteClipboardHelper.buildHtmlForClipboard(
                "Text^[Hinweis mit **Betonung**.] Ende.");

        assertFalse(html.contains("^["));
        assertTrue(html.contains("<sup><a href=\"#fn-1\">1</a></sup>"));
        assertTrue(html.contains("<section class=\"footnotes\">"));
        assertTrue(html.contains("<li id=\"fn-1\">Hinweis mit <strong>Betonung</strong>.</li>"));
    }

    @Test
    void plainTextUsesCleanNumberedFootnoteSection() {
        String plain = SudowriteClipboardHelper.buildPlainTextForClipboard(
                "Text^[Hinweis mit **Betonung**.] Ende.");

        assertFalse(plain.contains("^["));
        assertTrue(plain.contains("Text[1] Ende."));
        assertTrue(plain.contains("Fußnoten\n\n[1] Hinweis mit Betonung."));
    }
}
