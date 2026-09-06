package com.manuskript.publish;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Prüft Kapitelauswahl und Markdown-Überschriften.
 */
public final class ChapterTocChecker {

    public record TocResult(
            int selectedCount,
            int missingMd,
            int withoutHeading,
            List<String> issues
    ) {
        public boolean empty() {
            return selectedCount == 0;
        }
    }

    private ChapterTocChecker() {
    }

    public static TocResult check(Path projectRoot, List<String> selection) {
        List<String> issues = new ArrayList<>();
        if (selection == null || selection.isEmpty()) {
            issues.add("Keine Kapitel in der Buchauswahl (.manuskript_selection.json)");
            return new TocResult(0, 0, 0, issues);
        }
        Path data = projectRoot.resolve("data");
        int missing = 0;
        int withoutHeading = 0;
        for (String name : selection) {
            if (name == null || name.isBlank()) {
                continue;
            }
            Path md = resolveMarkdown(data, name.trim());
            if (md == null || !Files.isRegularFile(md)) {
                missing++;
                issues.add("Kein Markdown für: " + name);
                continue;
            }
            if (!hasHeading(md)) {
                withoutHeading++;
                issues.add("Keine #-Überschrift in: " + md.getFileName());
            }
        }
        return new TocResult(selection.size(), missing, withoutHeading, issues);
    }

    static Path resolveMarkdown(Path dataDir, String selectionName) {
        String base = selectionName;
        if (base.toLowerCase(Locale.ROOT).endsWith(".docx")) {
            base = base.substring(0, base.length() - 5);
        } else if (base.toLowerCase(Locale.ROOT).endsWith(".md")) {
            base = base.substring(0, base.length() - 3);
        }
        Path direct = dataDir.resolve(base + ".md");
        if (Files.isRegularFile(direct)) {
            return direct;
        }
        Path asIs = dataDir.resolve(selectionName);
        if (Files.isRegularFile(asIs)) {
            return asIs;
        }
        return direct;
    }

    static boolean hasHeading(Path md) {
        try {
            List<String> lines = Files.readAllLines(md, StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line == null) {
                    continue;
                }
                String trimmed = line.trim();
                if (trimmed.startsWith("#")) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
