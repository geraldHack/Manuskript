package com.manuskript.mindmap;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Liest Welt-Editor und Kapitel für KI-Kontext (kompakt, für schnellere KI-Antworten). */
public final class MindmapContextCollector {

    private static final String[] WORLD_FILES = {
            "synopsis.txt", "characters.txt", "worldbuilding.txt", "outline.txt",
            "chapter.txt", "akte.txt", "context.txt", "style.txt"
    };

    /** Welt-Dateien vollständig; Kapitel als Anfangsauszug (reicht für Beziehungen, spart Tokens). */
    private static final int MAX_FILE = 3500;
    private static final int MAX_CHAPTER = 900;
    private static final int MAX_TOTAL = 10_000;

    private MindmapContextCollector() {
    }

    public static String collect(Path projectRoot) {
        if (projectRoot == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Projekt: ").append(projectRoot.getFileName()).append('\n');

        int budget = MAX_TOTAL;
        for (String name : WORLD_FILES) {
            if (budget <= 200) {
                break;
            }
            Path file = projectRoot.resolve(name);
            if (!Files.isRegularFile(file)) {
                continue;
            }
            try {
                String text = Files.readString(file, StandardCharsets.UTF_8).trim();
                if (text.isEmpty()) {
                    continue;
                }
                String chunk = trim(text, Math.min(MAX_FILE, budget));
                sb.append("\n--- ").append(name).append(" ---\n").append(chunk).append('\n');
                budget -= chunk.length();
            } catch (Exception ignored) {
            }
        }

        List<String> selection = PublishSelectionReader.load(projectRoot);
        Path data = projectRoot.resolve("data");
        sb.append("\n--- Kapitel (Anfang je Kapitel, bis Token-Budget) ---\n");
        for (String name : selection) {
            if (budget <= 300) {
                sb.append("… (weitere Kapitel weggelassen — Budget)\n");
                break;
            }
            Path md = resolveMd(data, name);
            if (md == null || !Files.isRegularFile(md)) {
                continue;
            }
            try {
                String text = Files.readString(md, StandardCharsets.UTF_8).trim();
                String excerpt = chapterExcerpt(text, Math.min(MAX_CHAPTER, budget));
                if (excerpt.isEmpty()) {
                    continue;
                }
                sb.append("\n### ").append(md.getFileName()).append("\n").append(excerpt).append('\n');
                budget -= excerpt.length();
            } catch (Exception ignored) {
            }
        }
        return sb.toString();
    }

    /** Nur Überschrift + Anfang — reicht für Mindmap-Extraktion. */
    static String chapterExcerpt(String text, int max) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String[] lines = text.split("\n", 20);
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (sb.length() + line.length() + 1 > max) {
                break;
            }
            sb.append(line).append('\n');
        }
        String out = sb.toString().trim();
        if (text.length() > out.length()) {
            out += "\n…";
        }
        return trim(out, max);
    }

    static Path resolveMd(Path dataDir, String selectionName) {
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
        return Files.isRegularFile(asIs) ? asIs : direct;
    }

    private static String trim(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "\n…";
    }
}

final class PublishSelectionReader {

    private PublishSelectionReader() {
    }

    static List<String> load(Path projectRoot) {
        Path file = projectRoot.resolve("data").resolve(".manuskript_selection.json");
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            com.google.gson.JsonElement el = com.google.gson.JsonParser.parseString(json);
            if (!el.isJsonArray()) {
                return List.of();
            }
            java.util.ArrayList<String> out = new java.util.ArrayList<>();
            for (com.google.gson.JsonElement item : el.getAsJsonArray()) {
                if (item.isJsonPrimitive()) {
                    out.add(item.getAsString());
                }
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }
}
