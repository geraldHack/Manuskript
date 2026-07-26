package com.manuskript;

/**
 * Hilfslogik: getipptes {@code --} nach Pause zum Gedankenstrich ({@code –}),
 * ohne {@code ---} (Markdown-Trennlinie) zu zerstören.
 */
public final class GedankenstrichSupport {

    /** Deutscher Gedankenstrich (Halbgeviertstrich). */
    public static final String GEDANKENSTRICH = "\u2013";

    /** Pause, bevor {@code --} ersetzt wird — länger tippen erlaubt {@code ---}. */
    public static final long PAUSE_MS = 480;

    private GedankenstrichSupport() {
    }

    /**
     * Prüft, ob vor {@code caret} genau zwei Bindestriche stehen, die zum Gedankenstrich
     * werden dürfen (kein dritter {@code -}, keine fertige {@code ---}-Zeile).
     *
     * @param text  aktueller Dokumenttext
     * @param caret Caret-Position (direkt hinter dem letzten {@code -})
     * @return Startindex der beiden {@code --}, oder {@code -1}
     */
    public static int convertibleDoubleHyphenStart(String text, int caret) {
        if (text == null || caret < 2 || caret > text.length()) {
            return -1;
        }
        if (text.charAt(caret - 1) != '-' || text.charAt(caret - 2) != '-') {
            return -1;
        }
        if (caret >= 3 && text.charAt(caret - 3) == '-') {
            return -1;
        }
        if (caret < text.length() && text.charAt(caret) == '-') {
            return -1;
        }
        int lineStart = text.lastIndexOf('\n', caret - 1) + 1;
        int lineEnd = text.indexOf('\n', caret);
        if (lineEnd < 0) {
            lineEnd = text.length();
        }
        String line = text.substring(lineStart, lineEnd).trim();
        if (line.matches("-{3,}")) {
            return -1;
        }
        return caret - 2;
    }
}
