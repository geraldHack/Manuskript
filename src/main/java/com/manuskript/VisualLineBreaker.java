package com.manuskript;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.function.IntToDoubleFunction;

/**
 * Zerlegt Markdown-Text in visuelle Zeilen inklusive Soft-Wrap.
 * Die frühere Abbruchgrenze {@code text.length() + 1024} war zu knapp:
 * jeder Umbruch scannt das überlaufende Wort erneut, lange Kapitel verloren dadurch das Ende.
 */
final class VisualLineBreaker {

    private VisualLineBreaker() {
    }

    record Span(int start, int end) {
    }

    static List<Span> breakLines(
            CharSequence text,
            boolean wrapText,
            IntToDoubleFunction charWidth,
            IntPredicate breakOpportunity,
            IntToDoubleFunction wrapWidthAtLineStart,
            LineOmitter omitter) {
        List<Span> lines = new ArrayList<>();
        if (text == null) {
            lines.add(new Span(0, 0));
            return lines;
        }
        int length = text.length();
        int lineStart = 0;
        int i = 0;
        int lastBreakOffset = -1;
        double lineWidthSoFar = 0;
        while (i <= length) {
            if (i == length) {
                if (!omitter.shouldOmit(lineStart, i)) {
                    lines.add(new Span(lineStart, i));
                }
                break;
            }

            if (text.charAt(i) == '\n') {
                if (!omitter.shouldOmit(lineStart, i)) {
                    lines.add(new Span(lineStart, i));
                }
                lineStart = i + 1;
                i++;
                lineWidthSoFar = 0;
                lastBreakOffset = -1;
                continue;
            }

            if (wrapText && breakOpportunity.test(i)) {
                lastBreakOffset = i;
            }

            double wrapWidth = wrapWidthAtLineStart.applyAsDouble(lineStart);
            double widthIncludingI = lineWidthSoFar + charWidth.applyAsDouble(i);
            if (wrapText && i > lineStart && widthIncludingI > wrapWidth) {
                int breakOffset = lastBreakOffset >= lineStart ? lastBreakOffset + 1 : Math.max(lineStart + 1, i);
                if (breakOffset <= lineStart) {
                    breakOffset = Math.min(length, lineStart + 1);
                }
                lines.add(new Span(lineStart, breakOffset));
                lineStart = breakOffset;
                lineWidthSoFar = 0;
                i = lineStart;
                lastBreakOffset = -1;
                continue;
            }

            lineWidthSoFar = widthIncludingI;
            i++;
        }
        if (lines.isEmpty()) {
            lines.add(new Span(0, 0));
        }
        appendUncoveredTail(text, lines, omitter);
        return lines;
    }

    static int coveredEnd(CharSequence text, List<Span> lines) {
        if (text == null || text.isEmpty() || lines == null || lines.isEmpty()) {
            return 0;
        }
        int cover = lines.get(lines.size() - 1).end();
        if (cover < text.length() && text.charAt(cover) == '\n') {
            cover++;
        }
        return cover;
    }

    private static void appendUncoveredTail(CharSequence text, List<Span> lines, LineOmitter omitter) {
        int cover = coveredEnd(text, lines);
        if (cover >= text.length()) {
            return;
        }
        if (!omitter.shouldOmit(cover, text.length())) {
            lines.add(new Span(cover, text.length()));
        }
    }

    @FunctionalInterface
    interface LineOmitter {
        boolean shouldOmit(int start, int end);
    }
}
