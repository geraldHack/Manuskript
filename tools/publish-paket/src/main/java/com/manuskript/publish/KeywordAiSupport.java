package com.manuskript.publish;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Sammelt Projektkontext und baut den Prompt für KDP-Keywords.
 */
public final class KeywordAiSupport {

    private static final String[] WORLD_FILES = {
            "synopsis.txt",
            "characters.txt",
            "worldbuilding.txt",
            "outline.txt",
            "akte.txt",
            "context.txt",
            "style.txt",
            "chapter.txt"
    };

    private static final int MAX_FILE_CHARS = 2500;
    private static final int MAX_TOTAL_CONTEXT = 8_000;

    private KeywordAiSupport() {
    }

    public static String systemPrompt() {
        return """
                Du bist Spezialist für Amazon KDP Backend-Keywords (2026).
                Aufgabe: genau 7 Suchphrasen für das KDP-Keyword-Formular liefern.

                Regeln:
                - Jede Zeile = eine Phrase, natürliche Wortreihenfolge (wie Leser suchen).
                - 2–6 Wörter bzw. möglichst nahe an 50 Zeichen nutzen, keine Einwort-Keywords.
                - Deutsch, wenn das Buch deutsch ist; sonst Sprache des Buchs.
                - Keine Wiederholung von Wörtern aus Titel/Untertitel (Amazon indexiert die schon).
                - Keine Kommas, Anführungszeichen, Semikolons in einer Phrase.
                - Keine verbotenen Claims: Bestseller, gratis, free, Kindle Unlimited, Preis, Autorenname.
                - Mix: Subgenre/Tropes, Setting/Stimmung, Charaktertypen, Nischen-Modifier — nicht siebenmal dasselbe.
                -                 Keine erfundenen Fakten; nur was aus dem Kontext und den Autor-Hinweisen folgt.
                Wichtig: Antworte sofort mit den 7 Zeilen — kein internes Nachdenken in der Ausgabe.

                Ausgabeformat — streng:
                Nur 7 Zeilen, jeweils eine Phrase, nichts sonst. Keine Nummerierung, kein Markdown.
                """;
    }

    public static String userPrompt(PublishPackageModel model, Path projectRoot, String authorHints) {
        StringBuilder sb = new StringBuilder();
        sb.append("Erzeuge 7 KDP-Keyword-Phrasen für dieses Buch.\n\n");
        sb.append("### Metadaten\n");
        appendField(sb, "Titel", model == null ? "" : model.title);
        appendField(sb, "Untertitel", model == null ? "" : model.subtitle);
        appendField(sb, "Autor", model == null ? "" : model.author);
        appendField(sb, "Sprache", model == null ? "" : model.language);
        appendField(sb, "Altershinweis", model == null ? "" : model.ageRating);
        if (projectRoot != null) {
            appendField(sb, "Ordnername", projectRoot.getFileName().toString());
        }

        String hints = authorHints == null ? "" : authorHints.trim();
        sb.append("\n### Besondere Vorgaben vom Autor (höchste Priorität)\n");
        if (hints.isEmpty()) {
            sb.append("(keine — leite Genre/Tropes aus dem Kontext ab)\n");
        } else {
            sb.append(hints).append('\n');
        }

        String blurb = model == null || model.blurb == null ? "" : model.blurb.trim();
        sb.append("\n### Klappentext\n");
        if (blurb.isEmpty()) {
            sb.append("(leer)\n");
        } else {
            sb.append(trimTo(blurb, 2500)).append('\n');
        }

        if (projectRoot != null) {
            Map<String, String> world = loadWorldSnippets(projectRoot);
            if (!world.isEmpty()) {
                sb.append("\n### Welt-Editor / Projektdateien\n");
                int budget = MAX_TOTAL_CONTEXT;
                for (Map.Entry<String, String> entry : world.entrySet()) {
                    if (budget <= 200) {
                        break;
                    }
                    String chunk = trimTo(entry.getValue(), Math.min(MAX_FILE_CHARS, budget));
                    sb.append("--- ").append(entry.getKey()).append(" ---\n");
                    sb.append(chunk).append("\n\n");
                    budget -= chunk.length();
                }
            }
        }

        sb.append("\nAntworte jetzt mit genau 7 Zeilen Keyword-Phrasen.");
        return sb.toString();
    }

    public static Map<String, String> loadWorldSnippets(Path projectRoot) {
        Map<String, String> out = new LinkedHashMap<>();
        if (projectRoot == null) {
            return out;
        }
        for (String name : WORLD_FILES) {
            Path file = projectRoot.resolve(name);
            if (!Files.isRegularFile(file)) {
                continue;
            }
            try {
                String text = Files.readString(file, StandardCharsets.UTF_8).trim();
                if (!text.isEmpty()) {
                    out.put(name, text);
                }
            } catch (Exception ignored) {
                // optional
            }
        }
        return out;
    }

    /**
     * Parst KI-Antwort in bis zu 7 Phrasen (≤ 50 Zeichen, bereinigt).
     */
    public static List<String> parseKeywords(String raw) {
        List<String> result = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        String cleaned = raw.replace("\r\n", "\n").trim();
        if (cleaned.startsWith("```")) {
            int start = cleaned.indexOf('\n');
            int end = cleaned.lastIndexOf("```");
            if (start > 0 && end > start) {
                cleaned = cleaned.substring(start + 1, end).trim();
            }
        }
        for (String line : cleaned.split("\n")) {
            String phrase = sanitizePhrase(line);
            if (phrase.isEmpty()) {
                continue;
            }
            if (phrase.length() > PlatformRules.KDP_KEYWORD_MAX_CHARS) {
                phrase = softTrim(phrase, PlatformRules.KDP_KEYWORD_MAX_CHARS);
            }
            if (isDuplicate(result, phrase)) {
                continue;
            }
            result.add(phrase);
            if (result.size() >= PlatformRules.KDP_KEYWORD_SLOTS) {
                break;
            }
        }
        return result;
    }

    static String sanitizePhrase(String line) {
        if (line == null) {
            return "";
        }
        String s = line.trim();
        s = s.replaceFirst("(?i)^(keyword\\s*)?\\d+[.):\\-\\s]+", "");
        s = s.replaceFirst("^[-*•]+\\s*", "");
        s = s.replace('"', ' ').replace('„', ' ').replace('“', ' ').replace('”', ' ');
        s = s.replace(',', ' ').replace(';', ' ');
        s = s.replaceAll("\\s+", " ").trim();
        // Einwort ablehnen, außer zusammengesetzte mit Bindestrich und Länge
        if (!s.contains(" ") && !s.contains("-")) {
            return "";
        }
        if (s.length() < 5) {
            return "";
        }
        String lower = s.toLowerCase(Locale.ROOT);
        if (lower.contains("bestseller") || lower.contains("kindle unlimited")
                || lower.equals("gratis") || lower.equals("free")) {
            return "";
        }
        return s;
    }

    private static String softTrim(String phrase, int max) {
        if (phrase.length() <= max) {
            return phrase;
        }
        String cut = phrase.substring(0, max).trim();
        int space = cut.lastIndexOf(' ');
        if (space >= 12) {
            cut = cut.substring(0, space).trim();
        }
        return cut;
    }

    private static boolean isDuplicate(List<String> existing, String phrase) {
        String norm = phrase.toLowerCase(Locale.ROOT);
        for (String e : existing) {
            if (e.toLowerCase(Locale.ROOT).equals(norm)) {
                return true;
            }
        }
        return false;
    }

    private static void appendField(StringBuilder sb, String label, String value) {
        sb.append(label).append(": ");
        if (value == null || value.isBlank()) {
            sb.append("(leer)");
        } else {
            sb.append(value.trim());
        }
        sb.append('\n');
    }

    private static String trimTo(String text, int max) {
        if (text == null) {
            return "";
        }
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "\n…";
    }
}
