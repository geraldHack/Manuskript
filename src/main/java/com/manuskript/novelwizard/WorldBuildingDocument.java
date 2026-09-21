package com.manuskript.novelwizard;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Struktur fuer {@code worldbuilding.txt} aus dem Roman-Assistenten (Phase WORLD).
 * Weltbegriffe erwarten unter {@code ## Orte} / {@code ## Lore} Eintraege als {@code ### Name}.
 */
public final class WorldBuildingDocument {

    public static final String SECTION_SETTING = "## Setting";
    public static final String SECTION_PLACES = "## Orte";
    public static final String SECTION_LORE = "## Lore";

    /** Bullet oder Zeile: {@code * **Windbruch:** Beschreibung} bzw. {@code **Alduria**: …}. */
    private static final Pattern NAMED_BULLET = Pattern.compile(
            "^\\s*(?:[*\\-•]\\s+)?\\*\\*([^*:\\n]{2,80}?)\\*\\*\\s*:\\s*(.*)$");

    /** Variante mit Doppelpunkt innerhalb der Fettschrift: {@code **Windbruch:** Text}. */
    private static final Pattern NAMED_BULLET_COLON_INSIDE = Pattern.compile(
            "^\\s*(?:[*\\-•]\\s+)?\\*\\*([^*:\\n]{2,80}?):\\*\\*\\s*(.*)$");

    /** Ohne Fettdruck: {@code * Windbruch: Beschreibung}. */
    private static final Pattern PLAIN_NAMED_BULLET = Pattern.compile(
            "^\\s*[*\\-•]\\s+([^:\\n*]{2,80}?)\\s*:\\s*(.+)$");

    /** Zwischenüberschrift wie „Geografie und Orte:**“ oder „**Orte:**“. */
    private static final Pattern PLACES_LABEL = Pattern.compile(
            "(?i)^\\s*(?:\\*{0,2})\\s*(?:geografie|geographie)?\\s*(?:und\\s+)?orte\\s*(?:\\*{0,2})\\s*:?\\s*(?:\\*{0,2})\\s*$");

    private WorldBuildingDocument() {
    }

    public static boolean hasRequiredSections(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return false;
        }
        return markdown.contains(SECTION_SETTING)
                && markdown.contains(SECTION_PLACES)
                && markdown.contains(SECTION_LORE);
    }

    /**
     * Stellt Setting/Orte/Lore her und wandelt Bullet-{@code **Name:**}-Listen
     * unter Orte (und ggf. Lore) in {@code ### Name}-Abschnitte um.
     */
    public static String normalize(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return markdown == null ? "" : markdown;
        }
        String text = markdown.replace("\r\n", "\n").replace('\r', '\n').trim();
        text = ensureRequiredSections(text);
        text = demoteSiblingPlaceAndLoreHeadings(text);
        text = normalizeNamedBulletsInSection(text, SECTION_PLACES);
        text = normalizeNamedBulletsInSection(text, SECTION_LORE);
        text = extractLoosePlaceBulletsIntoOrte(text);
        text = normalizeNamedBulletsInSection(text, SECTION_PLACES);
        return text.trim() + "\n";
    }

    /**
     * Wandelt fälschlich als {@code ## Windbruch} (gleicher Level wie {@code ## Orte})
     * angelegte Schauplaetze in {@code ### Windbruch} unter {@code ## Orte} um.
     * Entsprechend fuer Lore.
     */
    static String demoteSiblingPlaceAndLoreHeadings(String text) {
        text = demoteSiblingEntriesAfter(text, SECTION_PLACES, SECTION_LORE, SECTION_SETTING);
        text = demoteSiblingEntriesAfter(text, SECTION_LORE, SECTION_SETTING, SECTION_PLACES);
        return text;
    }

    private static String demoteSiblingEntriesAfter(String text, String sectionHeading,
                                                   String stopA, String stopB) {
        int sectionIdx = text.indexOf(sectionHeading);
        if (sectionIdx < 0) {
            return text;
        }
        int bodyStart = sectionIdx + sectionHeading.length();
        if (bodyStart < text.length() && text.charAt(bodyStart) == '\n') {
            bodyStart++;
        }

        StringBuilder placesBody = new StringBuilder();
        String existingBody = "";
        int scan = bodyStart;
        // Bestehenden Abschnitt bis zur ersten weiteren ## behalten (ohne Sibling-##)
        int firstSibling = findNextH2(text, bodyStart);
        if (firstSibling < 0) {
            return text;
        }
        existingBody = text.substring(bodyStart, firstSibling).trim();
        // Wenn schon ###-Einträge da sind und keine Sibling-##-Orte folgen, nichts tun
        LinkedHashMap<String, String> siblings = new LinkedHashMap<>();
        int cursor = firstSibling;
        while (cursor >= 0 && cursor < text.length()) {
            int lineEnd = text.indexOf('\n', cursor);
            if (lineEnd < 0) {
                lineEnd = text.length();
            }
            String headingLine = text.substring(cursor, lineEnd).trim();
            if (!headingLine.startsWith("## ") || headingLine.startsWith("### ")) {
                break;
            }
            String title = headingLine.substring(3).trim();
            String titleKey = title.toLowerCase(Locale.ROOT);
            if (sectionHeading.substring(3).trim().equalsIgnoreCase(title)
                    || SECTION_SETTING.substring(3).trim().equalsIgnoreCase(title)
                    || SECTION_PLACES.substring(3).trim().equalsIgnoreCase(title)
                    || SECTION_LORE.substring(3).trim().equalsIgnoreCase(title)
                    || titleKey.startsWith("roman-assistent")
                    || "setting".equals(titleKey) || "orte".equals(titleKey) || "lore".equals(titleKey)
                    || "welt".equals(titleKey)) {
                break;
            }
            if (stopA != null && stopA.equals("## " + title)
                    || stopB != null && stopB.equals("## " + title)) {
                break;
            }
            int next = findNextH2(text, lineEnd + 1);
            String entryBody = next < 0
                    ? text.substring(lineEnd + 1).trim()
                    : text.substring(lineEnd + 1, next).trim();
            siblings.putIfAbsent(title, entryBody);
            cursor = next;
            if (next < 0) {
                break;
            }
        }
        if (siblings.isEmpty()) {
            return text;
        }

        if (!existingBody.isBlank()) {
            placesBody.append(existingBody.trim()).append("\n\n");
        }
        for (Map.Entry<String, String> e : siblings.entrySet()) {
            placesBody.append("### ").append(e.getKey()).append('\n');
            if (e.getValue() != null && !e.getValue().isBlank()) {
                placesBody.append(e.getValue().trim()).append('\n');
            }
            placesBody.append('\n');
        }

        int endOfSiblings = cursor < 0 ? text.length() : cursor;
        String before = text.substring(0, sectionIdx);
        String after = endOfSiblings < text.length() ? text.substring(endOfSiblings) : "";
        return before + sectionHeading + "\n\n" + placesBody.toString().trim() + "\n\n" + after;
    }

    static String ensureRequiredSections(String text) {
        StringBuilder out = new StringBuilder(text.trim());
        if (!out.toString().contains(SECTION_SETTING)) {
            out.insert(0, SECTION_SETTING + "\n\n(noch offen)\n\n");
        }
        if (!out.toString().contains(SECTION_PLACES)) {
            int loreIdx = indexOfSection(out.toString(), SECTION_LORE);
            String placesBlock = "\n\n" + SECTION_PLACES + "\n\n";
            if (loreIdx >= 0) {
                out.insert(loreIdx, placesBlock);
            } else {
                out.append(placesBlock);
            }
        }
        if (!out.toString().contains(SECTION_LORE)) {
            out.append("\n\n").append(SECTION_LORE).append("\n\n(noch offen)\n");
        }
        return out.toString().trim();
    }

    /**
     * Bullet-{@code **Name:**}-Bloecke ausserhalb von {@code ## Orte}
     * (z. B. unter Setting als „Geografie und Orte“) nach {@code ## Orte} verschieben.
     */
    static String extractLoosePlaceBulletsIntoOrte(String text) {
        SectionSlice orte = sectionSlice(text, SECTION_PLACES);
        if (orte == null) {
            return text;
        }
        if (countH3(orte.body()) > 0) {
            return text;
        }

        LinkedHashMap<String, String> found = new LinkedHashMap<>();
        String withoutExtracted = removePlaceLabelBlocks(text, found);
        if (found.isEmpty()) {
            return text;
        }

        orte = sectionSlice(withoutExtracted, SECTION_PLACES);
        if (orte == null) {
            return text;
        }
        String placesBody = renderH3Entries(found);
        return orte.before() + SECTION_PLACES + "\n\n" + placesBody + orte.after();
    }

    static String normalizeNamedBulletsInSection(String text, String sectionHeading) {
        SectionSlice slice = sectionSlice(text, sectionHeading);
        if (slice == null) {
            return text;
        }
        String body = slice.body();
        if (countH3(body) > 0 && !hasNamedBullet(body)) {
            return text;
        }
        LinkedHashMap<String, String> entries = parseNamedBullets(body);
        if (entries.isEmpty()) {
            return text;
        }
        String preface = nonBulletPreface(body);
        StringBuilder newBody = new StringBuilder();
        if (!preface.isBlank()) {
            newBody.append(preface.trim()).append("\n\n");
        }
        newBody.append(renderH3Entries(entries));
        return slice.before() + sectionHeading + "\n\n" + newBody + slice.after();
    }

    private static String removePlaceLabelBlocks(String text, Map<String, String> into) {
        String[] lines = text.split("\n", -1);
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < lines.length) {
            String line = lines[i];
            if (PLACES_LABEL.matcher(line.trim()).matches()) {
                i++;
                while (i < lines.length && lines[i].trim().isEmpty()) {
                    i++;
                }
                while (i < lines.length) {
                    String candidate = lines[i];
                    if (candidate.trim().startsWith("## ")) {
                        break;
                    }
                    NamedEntry entry = parseNamedBulletLine(candidate);
                    if (entry != null) {
                        into.putIfAbsent(entry.name(), entry.description());
                        i++;
                        continue;
                    }
                    if (candidate.trim().isEmpty()) {
                        i++;
                        // Leerzeile in der Liste: weiter; nach Liste oft Absatzende
                        if (i < lines.length && parseNamedBulletLine(lines[i]) == null
                                && !lines[i].trim().isEmpty()
                                && !PLACES_LABEL.matcher(lines[i].trim()).matches()) {
                            break;
                        }
                        continue;
                    }
                    break;
                }
                continue;
            }
            out.append(line);
            if (i < lines.length - 1) {
                out.append('\n');
            }
            i++;
        }
        return out.toString();
    }

    private static LinkedHashMap<String, String> parseNamedBullets(String body) {
        LinkedHashMap<String, String> entries = new LinkedHashMap<>();
        if (body == null || body.isBlank()) {
            return entries;
        }
        String[] lines = body.split("\n", -1);
        String currentName = null;
        StringBuilder currentDesc = new StringBuilder();
        for (String line : lines) {
            NamedEntry entry = parseNamedBulletLine(line);
            if (entry != null) {
                flushEntry(entries, currentName, currentDesc);
                currentName = entry.name();
                currentDesc.setLength(0);
                currentDesc.append(entry.description());
                continue;
            }
            if (currentName != null) {
                String trimmed = line.trim();
                if (trimmed.startsWith("## ") || trimmed.startsWith("### ")) {
                    flushEntry(entries, currentName, currentDesc);
                    currentName = null;
                    currentDesc.setLength(0);
                } else if (!trimmed.isEmpty() && !PLACES_LABEL.matcher(trimmed).matches()) {
                    if (!currentDesc.isEmpty()) {
                        currentDesc.append(' ');
                    }
                    currentDesc.append(trimmed);
                }
            }
        }
        flushEntry(entries, currentName, currentDesc);
        return entries;
    }

    private static void flushEntry(Map<String, String> entries, String name, StringBuilder desc) {
        if (name == null || name.isBlank()) {
            return;
        }
        entries.putIfAbsent(name, desc == null ? "" : desc.toString().trim());
    }

    private static NamedEntry parseNamedBulletLine(String line) {
        if (line == null) {
            return null;
        }
        Matcher colonInside = NAMED_BULLET_COLON_INSIDE.matcher(line);
        if (colonInside.matches()) {
            String name = cleanPlaceName(colonInside.group(1));
            if (name != null) {
                return new NamedEntry(name, colonInside.group(2).trim());
            }
        }
        Matcher bold = NAMED_BULLET.matcher(line);
        if (bold.matches()) {
            String name = cleanPlaceName(bold.group(1));
            if (name != null) {
                return new NamedEntry(name, bold.group(2).trim());
            }
        }
        Matcher plain = PLAIN_NAMED_BULLET.matcher(line);
        if (plain.matches()) {
            String name = cleanPlaceName(plain.group(1));
            if (name != null) {
                return new NamedEntry(name, plain.group(2).trim());
            }
        }
        return null;
    }

    private static String cleanPlaceName(String raw) {
        if (raw == null) {
            return null;
        }
        String name = raw.trim().replaceAll("\\s+", " ");
        if (name.length() < 2) {
            return null;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.equals("orte") || lower.equals("setting") || lower.equals("lore")
                || lower.startsWith("geografie") || lower.startsWith("geographie")) {
            return null;
        }
        return name;
    }

    private static boolean hasNamedBullet(String body) {
        if (body == null) {
            return false;
        }
        for (String line : body.split("\n")) {
            if (parseNamedBulletLine(line) != null) {
                return true;
            }
        }
        return false;
    }

    private static String nonBulletPreface(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        StringBuilder preface = new StringBuilder();
        for (String line : body.split("\n", -1)) {
            if (parseNamedBulletLine(line) != null) {
                break;
            }
            if (line.trim().startsWith("### ")) {
                break;
            }
            if (PLACES_LABEL.matcher(line.trim()).matches()) {
                continue;
            }
            preface.append(line).append('\n');
        }
        return preface.toString().trim();
    }

    private static String renderH3Entries(Map<String, String> entries) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : entries.entrySet()) {
            sb.append("### ").append(e.getKey()).append('\n');
            String desc = e.getValue() == null ? "" : e.getValue().trim();
            if (!desc.isEmpty()) {
                sb.append(desc).append('\n');
            }
            sb.append('\n');
        }
        return sb.toString().trim() + "\n";
    }

    private static int countH3(String body) {
        if (body == null || body.isBlank()) {
            return 0;
        }
        int count = 0;
        for (String line : body.split("\n")) {
            if (line.trim().startsWith("### ")) {
                count++;
            }
        }
        return count;
    }

    private static int indexOfSection(String text, String heading) {
        int idx = text.indexOf(heading);
        if (idx < 0) {
            return -1;
        }
        if (idx > 0 && text.charAt(idx - 1) != '\n') {
            // Mitte einer Zeile – trotzdem ok fuer Einfuegen vor Lore
            return idx;
        }
        return idx;
    }

    private static SectionSlice sectionSlice(String text, String heading) {
        int start = text.indexOf(heading);
        if (start < 0) {
            return null;
        }
        int bodyStart = start + heading.length();
        if (bodyStart < text.length() && text.charAt(bodyStart) == '\n') {
            bodyStart++;
        }
        int next = findNextH2(text, bodyStart);
        String before = text.substring(0, start);
        String body = next < 0 ? text.substring(bodyStart) : text.substring(bodyStart, next);
        String after = next < 0 ? "" : text.substring(next);
        return new SectionSlice(before, body, after);
    }

    private static int findNextH2(String text, int from) {
        int i = from;
        while (i < text.length()) {
            int lineStart = i;
            int nl = text.indexOf('\n', i);
            int lineEnd = nl < 0 ? text.length() : nl;
            String line = text.substring(lineStart, lineEnd).trim();
            if (line.startsWith("## ") && !line.startsWith("### ")) {
                return lineStart;
            }
            if (nl < 0) {
                break;
            }
            i = nl + 1;
        }
        return -1;
    }

    private record NamedEntry(String name, String description) {
    }

    private record SectionSlice(String before, String body, String after) {
    }
}
