package com.manuskript;

/**
 * Normalisierung von Kapitel-Markdown (Speichern, Diff, Historie).
 */
public final class ChapterMarkdownFormat {

    private ChapterMarkdownFormat() {
    }

    /**
     * Einheitliche Absatz-/Zeilenumbrüche für Speichern und Textvergleich.
     */
    public static String normalizeParagraphSpacing(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }

        String normalized = markdown.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder result = new StringBuilder(normalized.length() + 64);
        String[] lines = normalized.split("\n", -1);
        boolean previousWasNonEmpty = false;

        for (String line : lines) {
            boolean currentIsNonEmpty = !line.trim().isEmpty();
            if (previousWasNonEmpty && currentIsNonEmpty) {
                result.append('\n');
            }
            result.append(line).append('\n');
            previousWasNonEmpty = currentIsNonEmpty;
            if (!currentIsNonEmpty) {
                previousWasNonEmpty = false;
            }
        }

        return result.toString();
    }
}
