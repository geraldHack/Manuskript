package com.manuskript;

import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

import java.util.ArrayList;
import java.util.List;

/**
 * Kopiert Kapiteltext im Sudowrite-kompatiblen Format (HTML + Markdown-Fallback).
 */
public final class SudowriteClipboardHelper {

    private SudowriteClipboardHelper() {
    }

    public static void copyForSudowrite(String rawMarkdown, java.util.function.Consumer<String> onSuccess,
                                        java.util.function.Consumer<String> onError) {
        try {
            String markdownContent = cleanTextForExport(rawMarkdown);
            markdownContent = stripLeadingChapterHeading(markdownContent);

            ClipboardContent content = new ClipboardContent();
            content.putHtml(buildHtmlForClipboard(markdownContent));
            content.putString(buildPlainTextForClipboard(markdownContent));
            Clipboard.getSystemClipboard().setContent(content);

            if (onSuccess != null) {
                onSuccess.accept("In Zwischenablage kopiert (Sudowrite)");
            }
        } catch (Exception ex) {
            if (onError != null) {
                onError.accept("Fehler beim Kopieren: " + ex.getMessage());
            }
            throw new RuntimeException(ex);
        }
    }

    public static String cleanTextForExport(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("¶", "");
    }

    private static String stripLeadingChapterHeading(String markdownContent) {
        String[] lines = markdownContent.split("\n", -1);
        if (lines.length == 0 || !lines[0].trim().startsWith("#")) {
            return markdownContent;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < lines.length; i++) {
            if (i > 1) {
                sb.append("\n");
            }
            sb.append(lines[i]);
        }
        return sb.toString();
    }

    static String buildPlainTextForClipboard(String markdown) {
        ParsedFootnotes parsed = parseInlineFootnotes(markdown);
        String plainBody = parsed.body();
        for (int i = 0; i < parsed.notes().size(); i++) {
            plainBody = plainBody.replace(footnoteMarker(i + 1), "[" + (i + 1) + "]");
        }
        StringBuilder plain = new StringBuilder(plainBody);
        if (!parsed.notes().isEmpty()) {
            plain.append("\n\nFußnoten\n");
            for (int i = 0; i < parsed.notes().size(); i++) {
                plain.append("\n[").append(i + 1).append("] ")
                        .append(toPlainInlineText(parsed.notes().get(i)));
            }
        }
        return plain.toString();
    }

    static String buildHtmlForClipboard(String markdown) {
        ParsedFootnotes parsed = parseInlineFootnotes(markdown);
        String html = convertMarkdownToHTMLForClipboard(parsed.body());
        if (parsed.notes().isEmpty()) {
            return html;
        }
        for (int i = 0; i < parsed.notes().size(); i++) {
            int number = i + 1;
            html = html.replace(footnoteMarker(number),
                    "<sup><a href=\"#fn-" + number + "\">" + number + "</a></sup>");
        }

        StringBuilder footnoteBlock = new StringBuilder(
                "\n<section class=\"footnotes\"><hr><ol>\n");
        for (int i = 0; i < parsed.notes().size(); i++) {
            int number = i + 1;
            footnoteBlock.append("<li id=\"fn-").append(number).append("\">")
                    .append(convertInlineMarkdownForClipboard(parsed.notes().get(i)))
                    .append("</li>\n");
        }
        footnoteBlock.append("</ol></section>\n");
        return html.replace("</body></html>", footnoteBlock + "</body></html>");
    }

    private static ParsedFootnotes parseInlineFootnotes(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return new ParsedFootnotes("", List.of());
        }
        List<MarkdownFootnoteSupport.Footnote> footnotes = MarkdownFootnoteSupport.parse(markdown);
        if (footnotes.isEmpty()) {
            return new ParsedFootnotes(markdown, List.of());
        }
        StringBuilder body = new StringBuilder(markdown.length());
        List<String> notes = new ArrayList<>(footnotes.size());
        int sourceOffset = 0;
        for (MarkdownFootnoteSupport.Footnote footnote : footnotes) {
            body.append(markdown, sourceOffset, footnote.fullRange().startInclusive());
            notes.add(footnote.content());
            body.append(footnoteMarker(footnote.number()));
            sourceOffset = footnote.fullRange().endExclusive();
        }
        body.append(markdown, sourceOffset, markdown.length());
        return new ParsedFootnotes(body.toString(), List.copyOf(notes));
    }

    private static String footnoteMarker(int number) {
        return "\uE000FN" + number + "\uE001";
    }

    private static String convertMarkdownToHTMLForClipboard(String markdown) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html><head><meta charset=\"UTF-8\"></head><body>\n");
        String[] lines = markdown.split("\n", -1);
        boolean lastWasEmpty = false;
        boolean lastWasParagraph = false;
        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.isEmpty()) {
                if (!lastWasEmpty && !lastWasParagraph) {
                    html.append("<br>\n");
                }
                lastWasEmpty = true;
                lastWasParagraph = false;
                continue;
            }
            lastWasEmpty = false;
            lastWasParagraph = false;

            if (trimmed.startsWith(">")) {
                String quoteText = trimmed.substring(1).trim();
                html.append("<div>&gt;").append(escapeHtml(quoteText)).append("</div>\n");
                continue;
            }

            if (trimmed.startsWith("# ")) {
                html.append("<p><strong>").append(convertInlineMarkdownForClipboard(trimmed.substring(2)))
                        .append("</strong></p>\n");
                lastWasParagraph = true;
                continue;
            } else if (trimmed.startsWith("## ")) {
                html.append("<p><strong>").append(convertInlineMarkdownForClipboard(trimmed.substring(3)))
                        .append("</strong></p>\n");
                lastWasParagraph = true;
                continue;
            } else if (trimmed.startsWith("### ")) {
                html.append("<p><strong>").append(convertInlineMarkdownForClipboard(trimmed.substring(4)))
                        .append("</strong></p>\n");
                lastWasParagraph = true;
                continue;
            }

            if (trimmed.matches("^[-*+]\\s+.*")) {
                html.append("<p>&bull; ")
                        .append(convertInlineMarkdownForClipboard(trimmed.substring(trimmed.indexOf(' ') + 1)))
                        .append("</p>\n");
                lastWasParagraph = true;
                continue;
            } else if (trimmed.matches("^\\d+\\.\\s+.*")) {
                html.append("<p>").append(convertInlineMarkdownForClipboard(trimmed)).append("</p>\n");
                lastWasParagraph = true;
                continue;
            }

            if (trimmed.matches("^[-*_]{3,}$")) {
                html.append("<p>──────────</p>\n");
                lastWasParagraph = true;
                continue;
            }

            if (trimmed.startsWith("```")) {
                continue;
            }

            html.append("<p>").append(convertInlineMarkdownForClipboard(line)).append("</p>\n");
            lastWasParagraph = true;
        }
        html.append("</body></html>");
        return html.toString();
    }

    private static String convertInlineMarkdownForClipboard(String text) {
        return escapeHtml(text)
                .replaceAll("\\*\\*(.*?)\\*\\*", "<strong>$1</strong>")
                .replaceAll("\\*(.*?)\\*", "<em>$1</em>")
                .replaceAll("`(.*?)`", "<code style=\"background-color: #f8f9fa; padding: 2px 4px; border-radius: 3px;\">$1</code>")
                .replaceAll("\\[([^\\]]+)\\]\\(([^)]+)\\)", "<a href=\"$2\">$1</a>")
                .replaceAll("~~(.*?)~~", "<span style=\"text-decoration: line-through;\">$1</span>")
                .replaceAll("==(.*?)==", "<span style=\"background-color: yellow;\">$1</span>");
    }

    private static String toPlainInlineText(String markdown) {
        return markdown
                .replaceAll("\\[([^\\]]+)]\\([^)]+\\)", "$1")
                .replaceAll("(\\*\\*|__|~~|==)", "")
                .replaceAll("(?<!\\*)\\*(?!\\*)", "")
                .replace("`", "")
                .replace("\\[", "[")
                .replace("\\]", "]")
                .trim();
    }

    private record ParsedFootnotes(String body, List<String> notes) {
    }

    private static String escapeHtml(String text) {
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
