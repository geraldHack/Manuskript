package com.manuskript.publish;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Prompt und Parsing für KI-generierten KDP-Klappentext (Buchbeschreibung).
 */
public final class BlurbAiSupport {

    private static final Pattern LEADING_LABEL = Pattern.compile(
            "(?i)^(?:klappentext|buchbeschreibung|description|blurb)\\s*[:\\-]\\s*");
    private static final int MAX_CHAPTER_EXCERPT = 1200;
    private static final int MAX_CHAPTERS = 4;
    private static final int MAX_TOTAL_CONTEXT = 10_000;

    private BlurbAiSupport() {
    }

    public static String systemPrompt() {
        return """
                Du schreibst verkaufsstarke Klappentexte / Buchbeschreibungen für Amazon KDP (eBook).
                Ziel: Neugier wecken, Stimmung und Genre vermitteln — ohne den Plot zu spoilern.

                Regeln:
                - Sprache des Buchs (meist Deutsch).
                - Länge: etwa 120–220 Wörter (ca. 800–1400 Zeichen), maximal 3800 Zeichen.
                - Struktur: starker Hook (1–2 Absätze), dann Stakes/Setting/Protagonist, am Ende Spannungsbogen oder Frage.
                - Keine Bestseller-Claims, keine Preise, kein „Kindle Unlimited“, keine URLs.
                - Keine Nummerierung, keine Überschrift wie „Klappentext:“ in der Antwort.
                - Optional einfaches HTML erlaubt: <b>, <i>, <br> — sonst Plaintext mit Absätzen (Leerzeile).
                - Nur Fakten aus dem Kontext; nichts erfinden, was widerspricht.
                - Kein internes Reasoning in der Ausgabe — nur der fertige Klappentext.
                """;
    }

    public static String userPrompt(PublishPackageModel model, Path projectRoot, String blurbHints) {
        StringBuilder sb = new StringBuilder();
        sb.append("Schreibe einen Klappentext für dieses Buch.\n\n");
        sb.append("### Metadaten\n");
        appendField(sb, "Titel", model == null ? "" : model.title);
        appendField(sb, "Untertitel", model == null ? "" : model.subtitle);
        appendField(sb, "Autor", model == null ? "" : model.author);
        appendField(sb, "Sprache", model == null ? "" : model.language);
        appendField(sb, "Altershinweis", model == null ? "" : model.ageRating);
        if (projectRoot != null) {
            appendField(sb, "Ordnername", projectRoot.getFileName().toString());
        }

        String hints = mergeHints(model == null ? "" : model.authorHints, blurbHints);
        sb.append("\n### Vorgaben vom Autor (höchste Priorität)\n");
        if (hints.isBlank()) {
            sb.append("(keine — Genre und Stimmung aus Kontext ableiten)\n");
        } else {
            sb.append(hints.trim()).append('\n');
        }

        String existing = model == null || model.blurb == null ? "" : model.blurb.trim();
        if (!existing.isBlank()) {
            sb.append("\n### Bestehender Klappentext (optional überarbeiten, nicht blind kopieren)\n");
            sb.append(trimTo(existing, 2000)).append('\n');
        }

        int budget = MAX_TOTAL_CONTEXT;
        if (projectRoot != null) {
            Map<String, String> world = KeywordAiSupport.loadWorldSnippets(projectRoot);
            if (!world.isEmpty()) {
                sb.append("\n### Welt-Editor / Projektdateien\n");
                for (Map.Entry<String, String> entry : world.entrySet()) {
                    if (budget <= 300) {
                        break;
                    }
                    String chunk = trimTo(entry.getValue(), Math.min(2500, budget));
                    sb.append("--- ").append(entry.getKey()).append(" ---\n");
                    sb.append(chunk).append("\n\n");
                    budget -= chunk.length();
                }
            }
            appendChapterExcerpts(sb, projectRoot, budget);
        }

        sb.append("\nAntwort: nur der fertige Klappentext (Plaintext oder einfaches HTML).");
        return sb.toString();
    }

    /**
     * Bereinigt KI-Antwort für KDP Description (≤ 4000 Zeichen).
     */
    public static String parseBlurb(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String text = raw.replace("\r\n", "\n").trim();
        if (text.startsWith("```")) {
            int start = text.indexOf('\n');
            int end = text.lastIndexOf("```");
            if (start > 0 && end > start) {
                text = text.substring(start + 1, end).trim();
            }
        }
        text = LEADING_LABEL.matcher(text).replaceFirst("");
        text = text.replaceAll("(?m)^#+\\s+", "");
        text = text.trim();
        if (text.length() > PlatformRules.KDP_DESCRIPTION_MAX_CHARS) {
            text = softTrimParagraph(text, PlatformRules.KDP_DESCRIPTION_MAX_CHARS);
        }
        return text;
    }

    private static void appendChapterExcerpts(StringBuilder sb, Path projectRoot, int budget) {
        if (budget <= 400) {
            return;
        }
        List<String> selection = PublishPackageStore.loadSelection(projectRoot);
        if (selection.isEmpty()) {
            return;
        }
        Path data = projectRoot.resolve("data");
        sb.append("\n### Kapitelauszüge (Anfang je Kapitel)\n");
        int count = 0;
        for (String name : selection) {
            if (count >= MAX_CHAPTERS || budget <= 300) {
                break;
            }
            String base = name.endsWith(".docx") ? name.substring(0, name.length() - 5) : name;
            Path md = data.resolve(base + ".md");
            if (!Files.isRegularFile(md)) {
                continue;
            }
            try {
                String content = Files.readString(md, StandardCharsets.UTF_8).trim();
                if (content.isEmpty()) {
                    continue;
                }
                String chunk = trimTo(content, Math.min(MAX_CHAPTER_EXCERPT, budget));
                sb.append("--- ").append(base).append(" ---\n").append(chunk).append("\n\n");
                budget -= chunk.length();
                count++;
            } catch (Exception ignored) {
            }
        }
    }

    private static String mergeHints(String authorHints, String blurbHints) {
        String a = authorHints == null ? "" : authorHints.trim();
        String b = blurbHints == null ? "" : blurbHints.trim();
        if (a.isEmpty()) {
            return b;
        }
        if (b.isEmpty()) {
            return a;
        }
        return a + "\n" + b;
    }

    private static String softTrimParagraph(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        String cut = text.substring(0, max);
        int para = cut.lastIndexOf("\n\n");
        if (para >= max / 2) {
            return cut.substring(0, para).trim();
        }
        int space = cut.lastIndexOf(' ');
        if (space >= max * 3 / 4) {
            return cut.substring(0, space).trim();
        }
        return cut.trim();
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
