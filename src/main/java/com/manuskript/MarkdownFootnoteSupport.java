package com.manuskript;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Stateless support for Pandoc inline footnotes ({@code ^[text]}).
 */
public final class MarkdownFootnoteSupport {

    private MarkdownFootnoteSupport() {
    }

    /**
     * A half-open source range: {@code [startInclusive, endExclusive)}.
     */
    public record Range(int startInclusive, int endExclusive) {
        public Range {
            if (startInclusive < 0 || endExclusive < startInclusive) {
                throw new IllegalArgumentException("Invalid source range");
            }
        }
    }

    /**
     * A parsed footnote in document order.
     *
     * @param fullRange range including {@code ^[} and the closing bracket
     * @param sourceRange range containing only the footnote content
     * @param content source text between the footnote brackets
     * @param number one-based number in document order
     */
    public record Footnote(Range fullRange, Range sourceRange, String content, int number) {
        public Footnote {
            Objects.requireNonNull(fullRange, "fullRange");
            Objects.requireNonNull(sourceRange, "sourceRange");
            Objects.requireNonNull(content, "content");
            if (number < 1) {
                throw new IllegalArgumentException("Footnote number must be positive");
            }
        }
    }

    /** Ergebnis einer editorseitigen Quelltextänderung. */
    public record EditResult(String markdown, int caretOffset) {
        public EditResult {
            Objects.requireNonNull(markdown, "markdown");
            if (caretOffset < 0 || caretOffset > markdown.length()) {
                throw new IllegalArgumentException("Caret außerhalb des Textes");
            }
        }
    }

    /** Erzeugt sichere Pandoc-Syntax für einen einzeiligen Fußnotentext. */
    public static String toInlineSyntax(String content) {
        String value = content == null ? "" : content.trim();
        if (value.isEmpty()) {
            return "";
        }
        return "^[" + value.replace("\\", "\\\\")
                .replace("[", "\\[")
                .replace("]", "\\]") + "]";
    }

    /** Fügt die Fußnote hinter dem selektierten Bereich ein, ohne diesen zu ersetzen. */
    public static EditResult insertAfterSelection(
            String markdown, int selectionStart, int selectionEnd, String content) {
        String source = markdown == null ? "" : markdown;
        int safeStart = Math.max(0, Math.min(source.length(), selectionStart));
        int safeEnd = Math.max(safeStart, Math.min(source.length(), selectionEnd));
        String syntax = toInlineSyntax(content);
        if (syntax.isEmpty()) {
            return new EditResult(source, safeEnd);
        }
        return new EditResult(
                source.substring(0, safeEnd) + syntax + source.substring(safeEnd),
                safeEnd + syntax.length());
    }

    /** Ersetzt eine erkannte Fußnote; leerer Inhalt löscht sie vollständig. */
    public static EditResult replace(String markdown, Footnote footnote, String content) {
        String source = markdown == null ? "" : markdown;
        Objects.requireNonNull(footnote, "footnote");
        int start = footnote.fullRange().startInclusive();
        int end = footnote.fullRange().endExclusive();
        if (start < 0 || end > source.length() || start >= end) {
            throw new IllegalArgumentException("Fußnotenbereich passt nicht zum Text");
        }
        String syntax = toInlineSyntax(content);
        return new EditResult(
                source.substring(0, start) + syntax + source.substring(end),
                start + syntax.length());
    }

    /**
     * Parses all complete, unescaped Pandoc inline footnotes.
     */
    public static List<Footnote> parse(String markdown) {
        Objects.requireNonNull(markdown, "markdown");

        List<Footnote> footnotes = new ArrayList<>();
        int index = 0;
        while (index < markdown.length() - 1) {
            if (markdown.charAt(index) != '^'
                    || markdown.charAt(index + 1) != '['
                    || isEscaped(markdown, index)) {
                index++;
                continue;
            }

            int closingBracket = findClosingBracket(markdown, index + 1);
            if (closingBracket < 0) {
                index += 2;
                continue;
            }

            Range fullRange = new Range(index, closingBracket + 1);
            Range sourceRange = new Range(index + 2, closingBracket);
            footnotes.add(new Footnote(
                    fullRange,
                    sourceRange,
                    markdown.substring(sourceRange.startInclusive(), sourceRange.endExclusive()),
                    footnotes.size() + 1));
            index = closingBracket + 1;
        }
        return List.copyOf(footnotes);
    }

    /**
     * Alias for {@link #parse(String)}, suitable for callers that search a document.
     */
    public static List<Footnote> find(String markdown) {
        return parse(markdown);
    }

    /**
     * Removes complete inline footnotes and normalizes horizontal whitespace while
     * preserving every line break.
     */
    public static String stripForTts(String markdown) {
        Objects.requireNonNull(markdown, "markdown");
        return normalizeText(removeFootnotes(markdown, parse(markdown), false));
    }

    /**
     * Entfernt Inline-Fußnoten inklusive Inhalt, ohne weitere Textnormalisierung.
     * Für Formate ohne Seitenkonzept (HTML/EPUB).
     */
    public static String stripFootnotes(String markdown) {
        Objects.requireNonNull(markdown, "markdown");
        return removeFootnotes(markdown, parse(markdown), false);
    }

    /**
     * Produces text with numbered references and a numbered footnote section.
     */
    public static String toPlainText(String markdown) {
        Objects.requireNonNull(markdown, "markdown");
        List<Footnote> footnotes = parse(markdown);
        if (footnotes.isEmpty()) {
            return markdown;
        }

        String body = normalizeText(removeFootnotes(markdown, footnotes, true));
        StringBuilder result = new StringBuilder(body);
        if (!body.isEmpty()) {
            result.append("\n\n");
        }
        result.append("Fußnoten:\n");
        for (Footnote footnote : footnotes) {
            result.append(footnote.number())
                    .append(". ")
                    .append(displayContent(footnote.content()));
            if (footnote.number() < footnotes.size()) {
                result.append('\n');
            }
        }
        return result.toString();
    }

    /**
     * Wandelt Pandoc-Inline-Fußnoten {@code ^[...]} in Referenz-Fußnoten um.
     * Mehrzeilige bzw. durch Leerzeilen gestörte Inline-Notizen werden dabei zu
     * einzeiligen Definitionen normalisiert, damit Pandoc sie zuverlässig als
     * echte Fußnoten (DOCX/PDF/RTF/LaTeX) erkennt.
     */
    public static String toReferenceMarkdown(String markdown) {
        Objects.requireNonNull(markdown, "markdown");
        List<Footnote> footnotes = parse(markdown);
        if (footnotes.isEmpty()) {
            return markdown;
        }

        StringBuilder body = new StringBuilder(markdown.length() + footnotes.size() * 32);
        int position = 0;
        for (Footnote footnote : footnotes) {
            body.append(markdown, position, footnote.fullRange().startInclusive());
            body.append("[^").append(footnote.number()).append(']');
            position = footnote.fullRange().endExclusive();
        }
        body.append(markdown, position, markdown.length());

        while (!body.isEmpty()) {
            char last = body.charAt(body.length() - 1);
            if (last == '\n' || last == '\r') {
                body.setLength(body.length() - 1);
            } else {
                break;
            }
        }
        body.append("\n\n");
        for (Footnote footnote : footnotes) {
            body.append("[^").append(footnote.number()).append("]: ")
                    .append(flattenFootnoteDefinition(footnote.content()))
                    .append('\n');
        }
        return body.toString();
    }

    /**
     * Produces a self-contained HTML fragment with references and an ordered list.
     * All source text is escaped; it is never interpreted as pre-existing HTML.
     */
    public static String toHtmlClipboard(String markdown) {
        Objects.requireNonNull(markdown, "markdown");
        List<Footnote> footnotes = parse(markdown);

        StringBuilder body = new StringBuilder();
        int position = 0;
        for (Footnote footnote : footnotes) {
            body.append(escapeHtml(markdown.substring(position, footnote.fullRange().startInclusive())));
            int number = footnote.number();
            body.append("<sup id=\"fnref-").append(number).append("\"><a href=\"#fn-")
                    .append(number).append("\">").append(number).append("</a></sup>");
            position = footnote.fullRange().endExclusive();
        }
        body.append(escapeHtml(markdown.substring(position)));

        StringBuilder html = new StringBuilder();
        html.append("<div class=\"manuskript-footnotes-text\">")
                .append(lineBreaksToHtml(body.toString()))
                .append("</div>");
        if (!footnotes.isEmpty()) {
            html.append("<section class=\"footnotes\"><hr><ol>");
            for (Footnote footnote : footnotes) {
                html.append("<li id=\"fn-").append(footnote.number()).append("\">")
                        .append(lineBreaksToHtml(escapeHtml(displayContent(footnote.content()))))
                        .append(" <a href=\"#fnref-").append(footnote.number())
                        .append("\" aria-label=\"Zurück zum Text\">↩</a></li>");
            }
            html.append("</ol></section>");
        }
        return html.toString();
    }

    private static int findClosingBracket(String markdown, int openingBracket) {
        int depth = 1;
        for (int index = openingBracket + 1; index < markdown.length(); index++) {
            char current = markdown.charAt(index);
            if (isEscaped(markdown, index)) {
                continue;
            }
            if (current == '[') {
                depth++;
            } else if (current == ']' && --depth == 0) {
                return index;
            }
        }
        return -1;
    }

    private static boolean isEscaped(String text, int index) {
        int backslashes = 0;
        for (int cursor = index - 1; cursor >= 0 && text.charAt(cursor) == '\\'; cursor--) {
            backslashes++;
        }
        return (backslashes & 1) == 1;
    }

    private static String removeFootnotes(String markdown, List<Footnote> footnotes, boolean addReferences) {
        if (footnotes.isEmpty()) {
            return markdown;
        }
        StringBuilder result = new StringBuilder(markdown.length());
        int position = 0;
        for (Footnote footnote : footnotes) {
            result.append(markdown, position, footnote.fullRange().startInclusive());
            if (addReferences) {
                result.append('[').append(footnote.number()).append(']');
            } else if (needsSeparator(markdown, footnote.fullRange())) {
                result.append(' ');
            }
            position = footnote.fullRange().endExclusive();
        }
        return result.append(markdown, position, markdown.length()).toString();
    }

    private static boolean needsSeparator(String markdown, Range removedRange) {
        if (removedRange.startInclusive() == 0 || removedRange.endExclusive() == markdown.length()) {
            return false;
        }
        char before = markdown.charAt(removedRange.startInclusive() - 1);
        char after = markdown.charAt(removedRange.endExclusive());
        return !Character.isWhitespace(before) && !Character.isWhitespace(after)
                && !isOpeningPunctuation(before) && !isClosingPunctuation(after);
    }

    private static String normalizeText(String text) {
        StringBuilder normalized = new StringBuilder(text.length());
        boolean pendingSpace = false;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (current == '\r' || current == '\n') {
                pendingSpace = false;
                trimTrailingSpace(normalized);
                if (current == '\r' && index + 1 < text.length() && text.charAt(index + 1) == '\n') {
                    normalized.append("\r\n");
                    index++;
                } else {
                    normalized.append(current);
                }
                continue;
            }
            if (Character.isWhitespace(current)) {
                pendingSpace = normalized.length() > 0 && !endsWithLineBreak(normalized);
                continue;
            }
            if (pendingSpace && !isClosingPunctuation(current) && !endsWithOpeningPunctuation(normalized)) {
                normalized.append(' ');
            }
            pendingSpace = false;
            normalized.append(current);
        }
        trimTrailingSpace(normalized);
        return normalized.toString();
    }

    private static void trimTrailingSpace(StringBuilder text) {
        while (!text.isEmpty() && text.charAt(text.length() - 1) == ' ') {
            text.setLength(text.length() - 1);
        }
    }

    private static boolean endsWithLineBreak(StringBuilder text) {
        char last = text.charAt(text.length() - 1);
        return last == '\n' || last == '\r';
    }

    private static boolean endsWithOpeningPunctuation(StringBuilder text) {
        if (text.isEmpty()) {
            return false;
        }
        return isOpeningPunctuation(text.charAt(text.length() - 1));
    }

    private static boolean isOpeningPunctuation(char value) {
        return value == '(' || value == '[' || value == '{';
    }

    private static boolean isClosingPunctuation(char value) {
        return value == '.' || value == ',' || value == ';' || value == ':'
                || value == '!' || value == '?' || value == ')' || value == ']' || value == '}';
    }

    private static String displayContent(String content) {
        StringBuilder result = new StringBuilder(content.length());
        for (int index = 0; index < content.length(); index++) {
            char current = content.charAt(index);
            if (current == '\\' && index + 1 < content.length()) {
                char next = content.charAt(index + 1);
                if (next == '[' || next == ']' || next == '\\') {
                    result.append(next);
                    index++;
                    continue;
                }
            }
            result.append(current);
        }
        return result.toString();
    }

    /** Einzeilige Fußnoten-Definition ohne interne Zeilenumbrüche/Leerzeilen. */
    private static String flattenFootnoteDefinition(String content) {
        String display = displayContent(content == null ? "" : content).trim();
        if (display.isEmpty()) {
            return "";
        }
        StringBuilder flattened = new StringBuilder(display.length());
        boolean pendingSpace = false;
        for (int index = 0; index < display.length(); index++) {
            char current = display.charAt(index);
            if (Character.isWhitespace(current)) {
                pendingSpace = flattened.length() > 0;
                continue;
            }
            if (pendingSpace) {
                flattened.append(' ');
                pendingSpace = false;
            }
            flattened.append(current);
        }
        return flattened.toString();
    }

    private static String escapeHtml(String text) {
        StringBuilder escaped = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            switch (text.charAt(index)) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&#39;");
                default -> escaped.append(text.charAt(index));
            }
        }
        return escaped.toString();
    }

    private static String lineBreaksToHtml(String text) {
        return text.replace("\r\n", "<br>\n")
                .replace("\r", "<br>\n")
                .replace("\n", "<br>\n");
    }
}
