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
}
