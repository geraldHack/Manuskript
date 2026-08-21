package com.manuskript;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualLineBreakerTest {

    @Test
    void longWrappedChapterCoversEntireText() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 4000; i++) {
            builder.append("Luna trank den Kaffee in der Traumwelt und sah zu den Saeulen. ");
        }
        String text = builder.toString();
        List<VisualLineBreaker.Span> lines = breakWithFixedWidth(text, 10, 200);

        assertEquals(text.length(), VisualLineBreaker.coveredEnd(text, lines));
        assertTrue(lines.size() > 1024, "Umbruch muss deutlich mehr Zeilen erzeugen als die alte Guard");
        assertEquals(text.length(), lines.get(lines.size() - 1).end());
    }

    @Test
    void trailingParagraphWithoutNewlineIsKept() {
        String text = "Erste Zeile\nZweite Zeile ohne Umbruch am Ende";
        List<VisualLineBreaker.Span> lines = breakWithFixedWidth(text, 8, 800);

        assertEquals(2, lines.size());
        assertEquals(0, lines.get(0).start());
        assertEquals(11, lines.get(0).end());
        assertEquals(12, lines.get(1).start());
        assertEquals(text.length(), lines.get(1).end());
    }

    @Test
    void newlinesProduceEmptyVisualLines() {
        String text = "A\n\nB";
        List<VisualLineBreaker.Span> lines = breakWithFixedWidth(text, 8, 800);

        assertEquals(3, lines.size());
        assertEquals(new VisualLineBreaker.Span(0, 1), lines.get(0));
        assertEquals(new VisualLineBreaker.Span(2, 2), lines.get(1));
        assertEquals(new VisualLineBreaker.Span(3, 4), lines.get(2));
    }

    private static List<VisualLineBreaker.Span> breakWithFixedWidth(String text, double charWidth, double wrapWidth) {
        return VisualLineBreaker.breakLines(
                text,
                true,
                offset -> charWidth,
                offset -> offset >= 0 && offset < text.length() && Character.isWhitespace(text.charAt(offset)),
                lineStart -> wrapWidth,
                (start, end) -> false);
    }
}
