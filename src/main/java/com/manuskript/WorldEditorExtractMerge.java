package com.manuskript;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fuehrt KI-Extraktion im Modus „Ergaenzen“ mit bestehendem Tab-Inhalt zusammen
 * (die KI liefert oft nur neue/aktualisierte Abschnitte, nicht den gesamten Tab).
 */
public final class WorldEditorExtractMerge {

    private static final Pattern H2_HEADING = Pattern.compile("(?m)^## ([^\\n\\r]+)$");

    private WorldEditorExtractMerge() {
    }

    public static String mergeAppendExtract(String filename, String existing, String extracted) {
        if (extracted == null || extracted.isBlank()) {
            return existing == null ? "" : existing;
        }
        if (existing == null || existing.isBlank()) {
            return extracted.trim();
        }
        return switch (filename) {
            case "characters.txt", "worldbuilding.txt", "outline.txt", "akte.txt" ->
                    mergeByH2Sections(existing, extracted);
            default -> existing.trim() + "\n\n" + extracted.trim();
        };
    }

    static String mergeByH2Sections(String existing, String extracted) {
        SectionDocument base = parseH2Sections(existing);
        SectionDocument updates = parseH2Sections(extracted);

        LinkedHashMap<String, String> titles = new LinkedHashMap<>(base.titles);
        LinkedHashMap<String, String> bodies = new LinkedHashMap<>(base.bodies);

        for (Map.Entry<String, String> entry : updates.titles.entrySet()) {
            String key = entry.getKey();
            titles.put(key, entry.getValue());
            bodies.put(key, updates.bodies.getOrDefault(key, ""));
        }

        if (titles.isEmpty()) {
            return existing.trim() + "\n\n" + extracted.trim();
        }

        StringBuilder out = new StringBuilder();
        if (!base.preamble.isBlank()) {
            out.append(base.preamble.trim()).append("\n\n");
        }
        boolean first = true;
        for (Map.Entry<String, String> entry : titles.entrySet()) {
            if (!first) {
                out.append("\n");
            }
            first = false;
            out.append("## ").append(entry.getValue()).append("\n");
            String body = bodies.getOrDefault(entry.getKey(), "");
            if (!body.isBlank()) {
                out.append(body.trim()).append("\n");
            }
        }
        return out.toString().trim();
    }

    static SectionDocument parseH2Sections(String text) {
        SectionDocument doc = new SectionDocument();
        if (text == null || text.isBlank()) {
            return doc;
        }

        List<HeadingMatch> headings = new ArrayList<>();
        Matcher matcher = H2_HEADING.matcher(text);
        while (matcher.find()) {
            headings.add(new HeadingMatch(matcher.start(), matcher.end(), matcher.group(1).trim()));
        }
        if (headings.isEmpty()) {
            doc.preamble = text.trim();
            return doc;
        }

        doc.preamble = text.substring(0, headings.get(0).start()).trim();
        for (int i = 0; i < headings.size(); i++) {
            HeadingMatch heading = headings.get(i);
            int bodyStart = heading.end();
            if (bodyStart < text.length() && text.charAt(bodyStart) == '\r') {
                bodyStart++;
            }
            if (bodyStart < text.length() && text.charAt(bodyStart) == '\n') {
                bodyStart++;
            }
            int bodyEnd = (i + 1 < headings.size()) ? headings.get(i + 1).start() : text.length();
            String body = text.substring(bodyStart, bodyEnd).trim();
            String key = normalizeSectionKey(heading.title());
            doc.titles.put(key, heading.title());
            doc.bodies.put(key, body);
        }
        return doc;
    }

    static String normalizeSectionKey(String title) {
        if (title == null) {
            return "";
        }
        String normalized = Normalizer.normalize(title.trim(), Normalizer.Form.NFKC);
        normalized = normalized.toLowerCase(Locale.ROOT);
        normalized = normalized
                .replace("ä", "ae")
                .replace("ö", "oe")
                .replace("ü", "ue")
                .replace("ß", "ss");
        normalized = normalized.replaceAll("\\s+", " ");
        return normalized;
    }

    private record HeadingMatch(int start, int end, String title) {
    }

    static final class SectionDocument {
        String preamble = "";
        final LinkedHashMap<String, String> titles = new LinkedHashMap<>();
        final LinkedHashMap<String, String> bodies = new LinkedHashMap<>();
    }
}
