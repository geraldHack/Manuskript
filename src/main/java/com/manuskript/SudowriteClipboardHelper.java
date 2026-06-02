package com.manuskript;

import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

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
            content.putHtml(convertMarkdownToHTMLForClipboard(markdownContent));
            content.putString(markdownContent);
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
        return text
                .replaceAll("\\*\\*(.*?)\\*\\*", "<strong>$1</strong>")
                .replaceAll("\\*(.*?)\\*", "<em>$1</em>")
                .replaceAll("`(.*?)`", "<code style=\"background-color: #f8f9fa; padding: 2px 4px; border-radius: 3px;\">$1</code>")
                .replaceAll("\\[([^\\]]+)\\]\\(([^)]+)\\)", "<a href=\"$2\">$1</a>")
                .replaceAll("~~(.*?)~~", "<span style=\"text-decoration: line-through;\">$1</span>")
                .replaceAll("==(.*?)==", "<span style=\"background-color: yellow;\">$1</span>");
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
