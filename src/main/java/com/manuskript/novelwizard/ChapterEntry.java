package com.manuskript.novelwizard;

/**
 * Ein Kapitel aus chapter.txt inkl. Kontext (Akte, Abschnitt) und Markdown-Texten.
 */
public record ChapterEntry(
        int number,
        String title,
        String summary,
        String overviewMarkdown,
        String actHeading,
        String actMarkdown,
        String groupHeading,
        String groupMarkdown) {

    public ChapterEntry(int number, String title, String summary) {
        this(number, title, summary, "", "", "", "", "");
    }
}
