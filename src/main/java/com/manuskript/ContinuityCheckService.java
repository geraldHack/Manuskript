package com.manuskript;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Leichte Kontinuitätsprüfung: Figurenfakten aus {@code characters.txt} gegen Kapiteltexte.
 * Regelbasiert (keine KI) – Hinweise, keine harten Beweise.
 */
public final class ContinuityCheckService {

    private static final Pattern H2 = Pattern.compile("(?m)^##\\s+(.+?)\\s*$");
    private static final Pattern FACT_LINE = Pattern.compile(
            "(?m)^\\*\\*([^*:]+):\\*\\*\\s*(.+)$");
    private static final Pattern COLOR_WORD = Pattern.compile(
            "(?iu)\\b(blau(e[nrs]?)?|grün(e[nrs]?)?|braun(e[nrs]?)?|schwarz(e[nrs]?)?|"
                    + "blond(e[nrs]?)?|rot(e[nrs]?)?|grau(e[nrs]?)?|violett(e[nrs]?)?|"
                    + "amber|haselnuss|grau-blau|dunkelbraun|hellbraun)\\b");

    private ContinuityCheckService() {
    }

    public record CharacterSheet(String name, Map<String, String> facts) {
    }

    public record Finding(String severity, String character, String message) {
    }

    public record Report(List<CharacterSheet> characters, List<Finding> findings) {
        public String asText() {
            StringBuilder sb = new StringBuilder();
            sb.append("Kontinuität (leicht) – ").append(findings.size()).append(" Hinweis(e)\n\n");
            if (characters.isEmpty()) {
                sb.append("Keine Figuren mit ##-Überschrift in characters.txt gefunden.\n");
            } else {
                sb.append("Figuren in der Bibel: ").append(characters.size()).append('\n');
            }
            if (findings.isEmpty()) {
                sb.append("\nKeine Auffälligkeiten nach den einfachen Regeln.\n");
                return sb.toString();
            }
            for (Finding f : findings) {
                sb.append("\n[").append(f.severity()).append("] ");
                if (f.character() != null && !f.character().isBlank()) {
                    sb.append(f.character()).append(": ");
                }
                sb.append(f.message()).append('\n');
            }
            return sb.toString();
        }
    }

    public static List<CharacterSheet> parseCharacters(String charactersMarkdown) {
        List<CharacterSheet> sheets = new ArrayList<>();
        if (charactersMarkdown == null || charactersMarkdown.isBlank()) {
            return sheets;
        }
        Matcher h2 = H2.matcher(charactersMarkdown);
        List<int[]> spans = new ArrayList<>();
        List<String> names = new ArrayList<>();
        while (h2.find()) {
            names.add(h2.group(1).trim());
            spans.add(new int[]{h2.start(), h2.end()});
        }
        for (int i = 0; i < names.size(); i++) {
            int bodyStart = spans.get(i)[1];
            int bodyEnd = i + 1 < spans.size() ? spans.get(i + 1)[0] : charactersMarkdown.length();
            String body = charactersMarkdown.substring(bodyStart, bodyEnd);
            Map<String, String> facts = new LinkedHashMap<>();
            Matcher fact = FACT_LINE.matcher(body);
            while (fact.find()) {
                facts.put(fact.group(1).trim(), fact.group(2).trim());
            }
            sheets.add(new CharacterSheet(names.get(i), facts));
        }
        return sheets;
    }

    public static Report check(String charactersMarkdown, Map<String, String> chapterNameToText) {
        List<CharacterSheet> sheets = parseCharacters(charactersMarkdown);
        List<Finding> findings = new ArrayList<>();
        String allChapters = String.join("\n\n", chapterNameToText.values());
        String allLower = allChapters.toLowerCase(Locale.GERMAN);

        Set<String> mentioned = new LinkedHashSet<>();
        for (CharacterSheet sheet : sheets) {
            String name = sheet.name();
            String first = firstName(name);
            boolean found = containsIgnoreCase(allChapters, name)
                    || (first.length() >= 3 && containsWord(allLower, first.toLowerCase(Locale.GERMAN)));
            if (!found) {
                findings.add(new Finding("info", name,
                        "Kommt in den geladenen Kapiteln (noch) nicht vor."));
            } else {
                mentioned.add(name);
            }

            String appearance = appearanceBlob(sheet.facts());
            if (appearance.isBlank()) {
                continue;
            }
            Set<String> bibleColors = extractColors(appearance);
            if (bibleColors.isEmpty()) {
                continue;
            }

            for (Map.Entry<String, String> chapter : chapterNameToText.entrySet()) {
                String text = chapter.getValue();
                if (text == null || text.isBlank()) {
                    continue;
                }
                if (!containsIgnoreCase(text, name) && !containsWord(
                        text.toLowerCase(Locale.GERMAN), first.toLowerCase(Locale.GERMAN))) {
                    continue;
                }
                Set<String> nearColors = colorsNearName(text, name, first);
                for (String color : nearColors) {
                    if (!bibleColors.contains(color) && !compatibleColor(bibleColors, color)) {
                        findings.add(new Finding("warn", name,
                                "In „" + chapter.getKey() + "“ Farbe/Merkmal „" + color
                                        + "“ nahe beim Namen – Bibel: " + String.join(", ", bibleColors)
                                        + " (bitte prüfen)."));
                    }
                }
            }
        }

        return new Report(List.copyOf(sheets), List.copyOf(findings));
    }

    private static String appearanceBlob(Map<String, String> facts) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : facts.entrySet()) {
            String key = e.getKey().toLowerCase(Locale.GERMAN);
            if (key.contains("aussehen") || key.contains("alter") || key.contains("auge")
                    || key.contains("haar") || key.contains("erscheinung")) {
                sb.append(' ').append(e.getValue());
            }
        }
        return sb.toString().trim();
    }

    private static Set<String> extractColors(String text) {
        Set<String> colors = new LinkedHashSet<>();
        Matcher m = COLOR_WORD.matcher(text);
        while (m.find()) {
            colors.add(normalizeColor(m.group()));
        }
        return colors;
    }

    private static Set<String> colorsNearName(String text, String fullName, String firstName) {
        Set<String> colors = new LinkedHashSet<>();
        String[] needles = fullName.equalsIgnoreCase(firstName)
                ? new String[]{fullName}
                : new String[]{fullName, firstName};
        String lower = text.toLowerCase(Locale.GERMAN);
        for (String needle : needles) {
            if (needle == null || needle.length() < 2) {
                continue;
            }
            String n = needle.toLowerCase(Locale.GERMAN);
            int from = 0;
            while (true) {
                int idx = lower.indexOf(n, from);
                if (idx < 0) {
                    break;
                }
                int start = Math.max(0, idx - 80);
                int end = Math.min(text.length(), idx + needle.length() + 80);
                colors.addAll(extractColors(text.substring(start, end)));
                from = idx + needle.length();
            }
        }
        return colors;
    }

    private static String normalizeColor(String raw) {
        String s = raw.toLowerCase(Locale.GERMAN);
        if (s.startsWith("blau")) return "blau";
        if (s.startsWith("grün") || s.startsWith("gruen")) return "grün";
        if (s.startsWith("braun") || s.contains("dunkelbraun") || s.contains("hellbraun")) return "braun";
        if (s.startsWith("schwarz")) return "schwarz";
        if (s.startsWith("blond")) return "blond";
        if (s.startsWith("rot")) return "rot";
        if (s.startsWith("grau")) return "grau";
        if (s.startsWith("violett")) return "violett";
        if (s.contains("haselnuss")) return "haselnuss";
        if (s.contains("amber")) return "amber";
        return s;
    }

    private static boolean compatibleColor(Set<String> bible, String found) {
        if (bible.contains(found)) {
            return true;
        }
        // „grau-blau“ vs blau/grau
        for (String b : bible) {
            if (b.contains(found) || found.contains(b)) {
                return true;
            }
        }
        return false;
    }

    private static String firstName(String full) {
        if (full == null || full.isBlank()) {
            return "";
        }
        String[] parts = full.trim().split("\\s+");
        return parts[0];
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        return haystack != null && needle != null && !needle.isBlank()
                && haystack.toLowerCase(Locale.GERMAN).contains(needle.toLowerCase(Locale.GERMAN));
    }

    private static boolean containsWord(String haystackLower, String wordLower) {
        if (haystackLower == null || wordLower == null || wordLower.isBlank()) {
            return false;
        }
        return Pattern.compile("(?iu)\\b" + Pattern.quote(wordLower) + "\\b")
                .matcher(haystackLower)
                .find();
    }
}
