package com.manuskript;

/**
 * Gleichheit von Markdown und aus DOCX extrahiertem Markdown für Diffs:
 * Überschriften, Betonung, Unterstreichung und Gedankenstriche gelten als
 * dieselbe sichtbare Zeile, nicht als inhaltliche Änderung.
 */
public final class MarkdownMarkup {

    private MarkdownMarkup() {
    }

    public static boolean equivalent(String left, String right) {
        return normalize(left).equals(normalize(right));
    }

    public static String normalize(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String s = text.replace("\r\n", "\n").replace('\r', '\n').trim();
        s = s.replaceFirst("^#{1,6}\\s+", "");
        s = s.replaceAll("(?i)</?u>", "");
        s = s.replaceFirst("^\\s*[*+-]\\s+", "• ");
        s = s.replace("***", "").replace("**", "").replace("*", "");
        s = s.replace('\u2014', '\u2013');
        s = s.replaceAll("(?<!-)--(?!-)", "\u2013");
        s = s.replaceAll("[ \\t]+", " ").trim();
        return s;
    }

    /**
     * Markdown-Inline-Formatierung für Kurztexte auflösen (Popups, Tooltips).
     */
    public static String toPlainText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String s = text.replace("\r\n", " ").replace('\r', ' ').replace('\n', ' ');
        s = stripImageBlocks(s);
        s = s.replaceAll("(?i)><c>(.*?)</c>", "$1");
        s = s.replaceAll("(?i)><center>(.*?)</center>", "$1");
        s = s.replaceAll("\\[([^\\]]+)\\]\\([^)]+\\)", "$1");
        s = s.replaceAll("!\\[([^\\]]*)\\]\\([^)]+\\)(?:\\{\\s*width\\s*=\\s*\\d+%\\s*})?", "$1");
        for (int i = 0; i < 3; i++) {
            s = s.replaceAll("\\*\\*([^*]+)\\*\\*", "$1");
            s = s.replaceAll("__([^_]+)__", "$1");
            s = s.replaceAll("(?<!\\*)\\*([^*]+)\\*(?!\\*)", "$1");
            s = s.replaceAll("(?<!_)_([^_]+)_(?!_)", "$1");
        }
        s = s.replaceAll("`([^`]+)`", "$1");
        s = s.replaceAll("~~([^~]+)~~", "$1");
        s = s.replaceAll("==([^=]+)==", "$1");
        s = s.replaceAll("(?i)</?(?:sup|sub|b|i|em|strong|u|mark|span)[^>]*>", "");
        return normalize(s);
    }

    private static String stripImageBlocks(String text) {
        String s = text;
        java.util.List<MarkdownImageSupport.ParsedBlock> blocks = MarkdownImageSupport.parseBlocks(s);
        for (int i = blocks.size() - 1; i >= 0; i--) {
            MarkdownImageSupport.ParsedBlock block = blocks.get(i);
            s = s.substring(0, block.start()) + s.substring(block.end());
        }
        return s;
    }
}
