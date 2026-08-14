package com.manuskript;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownFootnoteSupportTest {

    @Test
    void parseFindsMultipleFootnotesInDocumentOrderWithRanges() {
        String markdown = "Alpha^[erste] Beta ^[zweite Note].";

        List<MarkdownFootnoteSupport.Footnote> footnotes = MarkdownFootnoteSupport.parse(markdown);

        assertEquals(2, footnotes.size());
        assertFootnote(markdown, footnotes.get(0), "^[erste]", "erste", 1);
        assertFootnote(markdown, footnotes.get(1), "^[zweite Note]", "zweite Note", 2);
        assertEquals(footnotes, MarkdownFootnoteSupport.find(markdown));
    }

    @Test
    void parseHandlesEscapedAndNestedBrackets() {
        String markdown = "Text ^[außen [innen] und maskiert \\] weiter] Ende";

        List<MarkdownFootnoteSupport.Footnote> footnotes = MarkdownFootnoteSupport.parse(markdown);

        assertEquals(1, footnotes.size());
        assertFootnote(markdown, footnotes.get(0),
                "^[außen [innen] und maskiert \\] weiter]",
                "außen [innen] und maskiert \\] weiter", 1);
    }

    @Test
    void parseIgnoresEscapedAndIncompleteFootnoteStarts() {
        String markdown = "\\^[keine] und ^[unvollständig";

        assertTrue(MarkdownFootnoteSupport.parse(markdown).isEmpty());
    }

    @Test
    void stripForTtsRemovesSyntaxAndPreservesParagraphStructure() {
        String markdown = "Alpha  ^[Notiz] , beta.\n\n Gamma^[zweite]Delta\nSchluss ^[dritte]";

        assertEquals("Alpha, beta.\n\nGamma Delta\nSchluss",
                MarkdownFootnoteSupport.stripForTts(markdown));
    }

    @Test
    void toPlainTextAddsReferencesAndNumberedFootnoteSection() {
        String markdown = "Alpha^[erste] und Beta ^[mit [Zusatz] und \\] Klammer].";

        assertEquals("""
                Alpha[1] und Beta [2].

                Fußnoten:
                1. erste
                2. mit [Zusatz] und ] Klammer""",
                MarkdownFootnoteSupport.toPlainText(markdown));
    }

    @Test
    void toHtmlClipboardEscapesSourceAndBuildsLinkedOrderedList() {
        String markdown = "<b>A & B</b>^[Hinweis <unsafe> & \\] ok]\nWeiter.";

        String html = MarkdownFootnoteSupport.toHtmlClipboard(markdown);

        assertEquals("""
                <div class="manuskript-footnotes-text">&lt;b&gt;A &amp; B&lt;/b&gt;<sup id="fnref-1"><a href="#fn-1">1</a></sup><br>
                Weiter.</div><section class="footnotes"><hr><ol><li id="fn-1">Hinweis &lt;unsafe&gt; &amp; ] ok <a href="#fnref-1" aria-label="Zurück zum Text">↩</a></li></ol></section>""",
                html);
    }

    @Test
    void convertersLeaveDocumentsWithoutFootnotesUseful() {
        String markdown = "Nur <Text>\n\nZweiter Absatz";

        assertEquals(markdown, MarkdownFootnoteSupport.stripForTts(markdown));
        assertEquals(markdown, MarkdownFootnoteSupport.toPlainText(markdown));
        assertEquals(
                "<div class=\"manuskript-footnotes-text\">Nur &lt;Text&gt;<br>\n<br>\nZweiter Absatz</div>",
                MarkdownFootnoteSupport.toHtmlClipboard(markdown));
    }

    @Test
    void editorInsertKeepsSelectionAndEscapesFootnoteContent() {
        MarkdownFootnoteSupport.EditResult result =
                MarkdownFootnoteSupport.insertAfterSelection(
                        "Ein markierter Satz.", 4, 14, "Quelle [Band 1]");

        assertEquals("Ein markierter^[Quelle \\[Band 1\\]] Satz.", result.markdown());
        assertEquals("Ein markierter^[Quelle \\[Band 1\\]]".length(), result.caretOffset());
    }

    @Test
    void editorReplaceAndDeleteOperateOnWholeFootnote() {
        String source = "Text^[alte Note].";
        MarkdownFootnoteSupport.Footnote footnote =
                MarkdownFootnoteSupport.parse(source).get(0);

        MarkdownFootnoteSupport.EditResult replaced =
                MarkdownFootnoteSupport.replace(source, footnote, "neue Note");
        assertEquals("Text^[neue Note].", replaced.markdown());

        MarkdownFootnoteSupport.Footnote updated =
                MarkdownFootnoteSupport.parse(replaced.markdown()).get(0);
        MarkdownFootnoteSupport.EditResult deleted =
                MarkdownFootnoteSupport.replace(replaced.markdown(), updated, "");
        assertEquals("Text.", deleted.markdown());
        assertEquals(4, deleted.caretOffset());
    }

    @Test
    void toReferenceMarkdownConvertsMultilineInlineNotesForPandoc() {
        String markdown = "Ich stürzte durch Schichten von Dunkelheit^[Das ist meine Fußnote\n"
                + "\nMehrzeilig!], durch etwas.";

        String converted = MarkdownFootnoteSupport.toReferenceMarkdown(markdown);

        assertEquals("""
                Ich stürzte durch Schichten von Dunkelheit[^1], durch etwas.

                [^1]: Das ist meine Fußnote Mehrzeilig!
                """, converted);
        assertTrue(MarkdownFootnoteSupport.parse(converted).isEmpty());
    }

    @Test
    void stripFootnotesRemovesNotesForDigitalExport() {
        String markdown = "Dunkelheit^[Das ist meine Fußnote\nMehrzeilig!], weiter.";

        assertEquals("Dunkelheit, weiter.", MarkdownFootnoteSupport.stripFootnotes(markdown));
    }

    private static void assertFootnote(
            String markdown,
            MarkdownFootnoteSupport.Footnote footnote,
            String fullSource,
            String content,
            int number) {
        assertEquals(fullSource, markdown.substring(
                footnote.fullRange().startInclusive(), footnote.fullRange().endExclusive()));
        assertEquals(content, markdown.substring(
                footnote.sourceRange().startInclusive(), footnote.sourceRange().endExclusive()));
        assertEquals(content, footnote.content());
        assertEquals(number, footnote.number());
    }
}
