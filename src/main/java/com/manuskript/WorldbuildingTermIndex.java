package com.manuskript;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Index für Worldbuilding-Begriffe (Figuren, Orte, Lore) aus {@code characters.txt}
 * und {@code worldbuilding.txt} – Basis für Editor-Highlighting.
 */
public final class WorldbuildingTermIndex {

    private static final Pattern H2_HEADING = Pattern.compile("(?m)^## ([^\\n\\r]+)$");
    private static final Pattern H3_HEADING = Pattern.compile("(?m)^### ([^\\n\\r]+)$");

    private static final Pattern KURZNAME_FIELD = Pattern.compile("(?m)^\\*\\*Kurzname:\\*\\*\\s*(.+)$");
    private static final Pattern ALIAS_FIELD = Pattern.compile("(?m)^\\*\\*(?:Andere Namen|Alias):\\*\\*\\s*(.+)$");

    private static final Set<String> META_SECTIONS = Set.of(
            "character sheets", "roman-assistent figuren", "roman-assistent: figuren",
            "charaktere", "figuren", "worldbuilding", "orte", "orte und regionen",
            "regionen", "factions", "fraktionen", "glossar", "uebersicht", "übersicht",
            "setting", "lore", "roman-assistent welt", "roman-assistent: welt", "welt"
    );

    public enum Category {
        CHARACTER("Figur"),
        PLACE("Ort"),
        LORE("Lore");

        private final String label;

        Category(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record Entry(Category category, String term, String sourceFile, String sectionHeading, String excerpt) {
    }

    public record TextMatch(int start, int end, Entry entry) {
    }

    private final List<Entry> entries;

    private WorldbuildingTermIndex(List<Entry> entries) {
        this.entries = List.copyOf(entries);
    }

    public static WorldbuildingTermIndex load(String charactersText, String worldbuildingText) {
        List<Entry> result = new ArrayList<>();
        addCharacterEntries(result, charactersText);
        addWorldbuildingEntries(result, worldbuildingText);
        return new WorldbuildingTermIndex(deduplicate(result));
    }

    public List<Entry> entries() {
        return entries;
    }

    public List<TextMatch> findMatches(String chapterText) {
        if (chapterText == null || chapterText.isEmpty() || entries.isEmpty()) {
            return List.of();
        }
        List<Entry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparingInt((Entry e) -> e.term().length()).reversed());

        List<TextMatch> matches = new ArrayList<>();
        boolean[] occupied = new boolean[chapterText.length()];
        for (Entry entry : sorted) {
            String term = entry.term();
            if (term == null || term.length() < 2) {
                continue;
            }
            int from = 0;
            while (from < chapterText.length()) {
                int[] span = findNextTermSpan(chapterText, term, from);
                if (span == null) {
                    break;
                }
                int idx = span[0];
                int end = span[1];
                if (!overlaps(occupied, idx, end)) {
                    markOccupied(occupied, idx, end);
                    matches.add(new TextMatch(idx, end, entry));
                    from = end;
                } else {
                    from = idx + 1;
                }
            }
        }
        matches.sort(Comparator.comparingInt(TextMatch::start));
        return matches;
    }

    private static void addCharacterEntries(List<Entry> result, String charactersText) {
        if (charactersText == null || charactersText.isBlank()) {
            return;
        }
        List<HeadingSlice> sections = sliceByHeadings(charactersText, H2_HEADING);
        for (HeadingSlice slice : sections) {
            String canonical = normalizeCharacterHeading(slice.title());
            if (isMetaSection(canonical) || canonical.isBlank()) {
                continue;
            }
            String excerpt = excerpt(slice.body());
            result.add(new Entry(
                    Category.CHARACTER,
                    canonical,
                    NovelManager.CHARACTERS_FILE,
                    canonical,
                    excerpt));
            for (String alias : parseCharacterAliases(slice.body())) {
                if (alias.isBlank() || alias.equalsIgnoreCase(canonical)) {
                    continue;
                }
                result.add(new Entry(
                        Category.CHARACTER,
                        alias,
                        NovelManager.CHARACTERS_FILE,
                        canonical,
                        excerpt));
            }
        }
    }

    static String normalizeCharacterHeading(String title) {
        if (title == null) {
            return "";
        }
        String trimmed = title.trim();
        int paren = trimmed.indexOf(" (");
        if (paren > 0 && trimmed.endsWith(")")) {
            String before = trimmed.substring(0, paren).trim();
            String inside = trimmed.substring(paren + 2, trimmed.length() - 1).trim();
            if (before.equalsIgnoreCase(inside)) {
                return before;
            }
        }
        return trimmed;
    }

    private static List<String> parseCharacterAliases(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        LinkedHashMap<String, String> aliases = new LinkedHashMap<>();
        Matcher kurz = KURZNAME_FIELD.matcher(body);
        if (kurz.find()) {
            addAliasTerms(aliases, kurz.group(1));
        }
        Matcher andere = ALIAS_FIELD.matcher(body);
        if (andere.find()) {
            addAliasTerms(aliases, andere.group(1));
        }
        return new ArrayList<>(aliases.values());
    }

    private static void addAliasTerms(Map<String, String> aliases, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        for (String part : raw.split(",")) {
            String term = part.trim();
            if (term.length() >= 2) {
                aliases.putIfAbsent(normalizeKey(term), term);
            }
        }
    }

    private static void addWorldbuildingEntries(List<Entry> result, String worldbuildingText) {
        if (worldbuildingText == null || worldbuildingText.isBlank()) {
            return;
        }
        List<HeadingSlice> sections = sliceByHeadings(worldbuildingText, H2_HEADING);
        for (HeadingSlice slice : sections) {
            String key = normalizeKey(slice.title());
            if ("orte".equals(key)) {
                addH3Entries(result, slice.body(), Category.PLACE, NovelManager.WORLDBUILDING_FILE);
            } else if ("lore".equals(key)) {
                addH3Entries(result, slice.body(), Category.LORE, NovelManager.WORLDBUILDING_FILE);
            }
        }
    }

    private static void addH3Entries(List<Entry> result, String body, Category category, String sourceFile) {
        if (body == null || body.isBlank()) {
            return;
        }
        List<HeadingSlice> subsections = sliceByHeadings(body, H3_HEADING);
        for (HeadingSlice subsection : subsections) {
            if (subsection.title().isBlank()) {
                continue;
            }
            result.add(new Entry(
                    category,
                    subsection.title(),
                    sourceFile,
                    subsection.title(),
                    excerpt(subsection.body())));
        }
    }

    private static List<HeadingSlice> sliceByHeadings(String text, Pattern headingPattern) {
        List<HeadingSlice> slices = new ArrayList<>();
        Matcher matcher = headingPattern.matcher(text);
        List<int[]> positions = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        while (matcher.find()) {
            positions.add(new int[] {matcher.start(), matcher.end()});
            titles.add(matcher.group(1).trim());
        }
        for (int i = 0; i < positions.size(); i++) {
            int bodyStart = positions.get(i)[1];
            int bodyEnd = i + 1 < positions.size() ? positions.get(i + 1)[0] : text.length();
            String body = bodyStart < bodyEnd ? text.substring(bodyStart, bodyEnd).trim() : "";
            slices.add(new HeadingSlice(titles.get(i), body));
        }
        return slices;
    }

    private static List<Entry> deduplicate(List<Entry> entries) {
        LinkedHashMap<String, Entry> unique = new LinkedHashMap<>();
        for (Entry entry : entries) {
            String key = normalizeKey(entry.term());
            if (key.isEmpty()) {
                continue;
            }
            unique.putIfAbsent(key, entry);
        }
        return new ArrayList<>(unique.values());
    }

    private static boolean isMetaSection(String title) {
        String key = normalizeKey(title);
        return META_SECTIONS.contains(key) || key.startsWith("roman-assistent");
    }

    static String normalizeKey(String title) {
        if (title == null) {
            return "";
        }
        String normalized = Normalizer.normalize(title.trim(), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss");
        return normalized.replaceAll("\\s+", " ");
    }

    private static String excerpt(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        CharacterSheetDocument.CharacterEntry entry = CharacterSheetDocument.parseCharacterBody("", body);
        String structured = structuredCharacterExcerpt(entry);
        String plain = structured.isBlank() ? MarkdownMarkup.toPlainText(body) : structured;
        if (plain.length() <= 220) {
            return plain;
        }
        return plain.substring(0, 219).trim() + "…";
    }

    private static String structuredCharacterExcerpt(CharacterSheetDocument.CharacterEntry entry) {
        if (entry == null) {
            return "";
        }
        StringBuilder summary = new StringBuilder();
        appendExcerptField(summary, "Rolle", entry.field("Rolle"));
        appendExcerptField(summary, "Kurzname", entry.field("Kurzname"));
        appendExcerptField(summary, "Alter / Aussehen", entry.field("Alter / Aussehen"));
        appendExcerptField(summary, "Persönlichkeit", entry.field("Persönlichkeit"));
        if (summary.isEmpty()) {
            appendExcerptField(summary, "Hintergrund", entry.field("Hintergrund"));
        }
        return summary.toString().trim();
    }

    private static void appendExcerptField(StringBuilder summary, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!summary.isEmpty()) {
            summary.append(' ');
        }
        summary.append(label).append(": ").append(MarkdownMarkup.toPlainText(value));
    }

    private static int[] findNextTermSpan(String text, String term, int from) {
        while (from < text.length()) {
            int idx = indexOfIgnoreCase(text, term, from);
            if (idx < 0) {
                return null;
            }
            if (!regionMatchesIgnoreCase(text, idx, term)) {
                from = idx + 1;
                continue;
            }
            int end = extendPossessiveEnd(text, idx, term);
            if (hasWordBoundary(text, idx, end)) {
                return new int[] {idx, end};
            }
            from = idx + 1;
        }
        return null;
    }

    /** Erweitert Treffer um Genitiv-s (Nenes) oder Apostroph (' / 's), nur bei Einwort-Begriffen. */
    private static int extendPossessiveEnd(String text, int start, String term) {
        int end = start + term.length();
        if (term.contains(" ")) {
            return end;
        }
        if (end >= text.length()) {
            return end;
        }
        char next = text.charAt(end);
        if (next == '\'' || isApostropheLike(next)) {
            end++;
            if (end < text.length() && (text.charAt(end) == 's' || text.charAt(end) == 'S')) {
                end++;
            }
            return end;
        }
        if ((next == 's' || next == 'S') && !endsWithS(term)) {
            if (end + 1 >= text.length() || !isTermCharacter(text.charAt(end + 1))) {
                end++;
            }
        }
        return end;
    }

    private static boolean endsWithS(String term) {
        if (term == null || term.isEmpty()) {
            return false;
        }
        char last = term.charAt(term.length() - 1);
        return last == 's' || last == 'S' || last == 'ß';
    }

    private static boolean regionMatchesIgnoreCase(String text, int start, String term) {
        if (start < 0 || start + term.length() > text.length()) {
            return false;
        }
        for (int i = 0; i < term.length(); i++) {
            char textChar = normalizeApostrophe(text.charAt(start + i));
            char termChar = normalizeApostrophe(term.charAt(i));
            if (Character.toLowerCase(textChar) != Character.toLowerCase(termChar)) {
                return false;
            }
        }
        return true;
    }

    private static char normalizeApostrophe(char c) {
        return switch (c) {
            case '\u2019', '\u2018', '\u201B', '\u0060', '\u00B4', '\u02BC' -> '\'';
            default -> c;
        };
    }

    private static boolean isApostropheLike(char c) {
        return normalizeApostrophe(c) == '\'';
    }

    private static int indexOfIgnoreCase(String text, String term, int from) {
        if (from >= text.length()) {
            return -1;
        }
        int max = text.length() - term.length();
        for (int i = from; i <= max; i++) {
            if (regionMatchesIgnoreCase(text, i, term)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean hasWordBoundary(String text, int start, int end) {
        char before = start > 0 ? text.charAt(start - 1) : '\0';
        char after = end < text.length() ? text.charAt(end) : '\0';
        return !isTermCharacter(before) && !isTermCharacter(after);
    }

    static boolean isTermCharacter(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '\'';
    }

    private static boolean overlaps(boolean[] occupied, int start, int end) {
        for (int i = start; i < end && i < occupied.length; i++) {
            if (occupied[i]) {
                return true;
            }
        }
        return false;
    }

    private static void markOccupied(boolean[] occupied, int start, int end) {
        for (int i = start; i < end && i < occupied.length; i++) {
            occupied[i] = true;
        }
    }

    private record HeadingSlice(String title, String body) {
    }
}
