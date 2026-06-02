package com.manuskript;

/**
 * Normalisiert HTML-Zeilenumbrüche für Pandoc-Export (EPUB/HTML).
 */
public final class ExportHtmlNormalizer {

    private ExportHtmlNormalizer() {
    }

    /**
     * Bereitet {@code <br>}-Tags für EPUB/HTML vor: XHTML-konforme {@code <br />} und
     * alleinstehende Zeilenumbruch-Tags als Leerzeile (Absatzabstand).
     */
    public static String normalizeBrTagsForEpubHtml(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        // Alleinstehende <br>-Zeilen → Leerzeile (stabiler als <p><br></p> in EPUB)
        String result = content.replaceAll("(?m)^[ \\t]*<br\\s*/?>[ \\t]*$", "");
        // Inline und übrige Varianten → XHTML
        result = result.replaceAll("(?i)<br\\s*/>", "<br />");
        result = result.replaceAll("(?i)<br>", "<br />");
        return result;
    }

    /**
     * Korrigiert {@code <br>} in generierten XHTML-Dateien (EPUB Post-Processing).
     */
    public static String fixBrTagsInXhtml(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        String result = content.replaceAll("(?i)<br\\s*/>", "<br />");
        result = result.replaceAll("(?i)<br>", "<br />");
        // Leere BR-Absätze entfernen (Pandoc-Artefakt)
        result = result.replaceAll("<p>\\s*<br />\\s*</p>", "");
        return result;
    }
}
