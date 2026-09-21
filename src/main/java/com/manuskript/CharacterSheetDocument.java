package com.manuskript;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strukturiertes Modell für {@code characters.txt}: Preamble, Meta-Abschnitte und Figuren-Karten.
 */
public final class CharacterSheetDocument {

    private static final Pattern H2 = Pattern.compile("(?m)^##\\s+(.+?)\\s*$");
    private static final Pattern FACT_LINE = Pattern.compile("(?m)^\\*\\*([^*:]+):\\*\\*\\s*(.*)$");

    private static final Set<String> META_SECTIONS = Set.of(
            "character sheets", "roman-assistent figuren", "roman-assistent: figuren",
            "charaktere", "figuren", "worldbuilding", "orte", "orte und regionen",
            "regionen", "factions", "fraktionen", "glossar", "uebersicht", "übersicht",
            "setting", "lore", "roman-assistent welt", "roman-assistent: welt", "welt"
    );

    public static final List<String> STANDARD_FIELD_LABELS = List.of(
            "Kurzname",
            "Andere Namen / Alias",
            "Rolle",
            "Alter / Aussehen",
            "Persönlichkeit",
            "Hintergrund",
            "Ziele",
            "Schwächen / innere Konflikte",
            "Beziehungen",
            "Character Arc",
            "Notizen"
    );

    public record CharacterEntry(
            String name,
            String imageMarkdown,
            LinkedHashMap<String, String> fields) {

        public CharacterEntry(String name) {
            this(name, "", emptyFields());
        }

        public String field(String label) {
            return fields.getOrDefault(label, "");
        }

        public CharacterEntry withName(String newName) {
            return new CharacterEntry(newName, imageMarkdown, fields);
        }

        public CharacterEntry withImageMarkdown(String markdown) {
            return new CharacterEntry(name, markdown == null ? "" : markdown, fields);
        }

        public CharacterEntry withField(String label, String value) {
            LinkedHashMap<String, String> copy = new LinkedHashMap<>(fields);
            if (value == null || value.isBlank()) {
                copy.remove(label);
            } else {
                copy.put(label, value.trim());
            }
            return new CharacterEntry(name, imageMarkdown, copy);
        }
    }

    public sealed interface Block permits MetaBlock, CharacterBlock {
        String title();
    }

    public record MetaBlock(String title, String body) implements Block {
    }

    public record CharacterBlock(CharacterEntry entry) implements Block {
        @Override
        public String title() {
            return entry.name();
        }
    }

    public record Document(String preamble, List<Block> blocks) {
        public Document {
            blocks = List.copyOf(blocks);
        }

        public List<CharacterEntry> characters() {
            return blocks.stream()
                    .filter(CharacterBlock.class::isInstance)
                    .map(CharacterBlock.class::cast)
                    .map(CharacterBlock::entry)
                    .toList();
        }
    }

    private CharacterSheetDocument() {
    }

    public static Document parse(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return new Document("", List.of());
        }

        Matcher matcher = H2.matcher(markdown);
        List<int[]> spans = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        while (matcher.find()) {
            spans.add(new int[]{matcher.start(), matcher.end()});
            titles.add(matcher.group(1).trim());
        }
        if (spans.isEmpty()) {
            return new Document(markdown.trim(), List.of());
        }

        String preamble = markdown.substring(0, spans.get(0)[0]).trim();
        List<Block> blocks = new ArrayList<>();
        for (int i = 0; i < titles.size(); i++) {
            int bodyStart = spans.get(i)[1];
            if (bodyStart < markdown.length() && markdown.charAt(bodyStart) == '\r') {
                bodyStart++;
            }
            if (bodyStart < markdown.length() && markdown.charAt(bodyStart) == '\n') {
                bodyStart++;
            }
            int bodyEnd = i + 1 < spans.size() ? spans.get(i + 1)[0] : markdown.length();
            String title = titles.get(i);
            String body = markdown.substring(bodyStart, bodyEnd).trim();
            if (isMetaSection(title)) {
                blocks.add(new MetaBlock(title, body));
            } else {
                blocks.add(new CharacterBlock(parseCharacterBody(title, body)));
            }
        }
        return new Document(preamble, blocks);
    }

    public static String serialize(Document document) {
        if (document == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        if (document.preamble() != null && !document.preamble().isBlank()) {
            out.append(document.preamble().trim());
        }
        for (Block block : document.blocks()) {
            if (!out.isEmpty()) {
                out.append("\n\n");
            }
            if (block instanceof MetaBlock meta) {
                out.append("## ").append(meta.title()).append('\n');
                if (meta.body() != null && !meta.body().isBlank()) {
                    out.append(meta.body().trim()).append('\n');
                }
            } else if (block instanceof CharacterBlock characterBlock) {
                out.append(serializeCharacter(characterBlock.entry()).trim()).append('\n');
            }
        }
        return out.toString().trim();
    }

    public static Document withCharacters(Document base, List<CharacterEntry> characters) {
        List<Block> blocks = new ArrayList<>();
        for (Block block : base.blocks()) {
            if (!(block instanceof CharacterBlock)) {
                blocks.add(block);
            }
        }
        for (CharacterEntry entry : characters) {
            blocks.add(new CharacterBlock(entry));
        }
        return new Document(base.preamble(), blocks);
    }

    public static CharacterEntry mergeGenerated(CharacterEntry existing, CharacterEntry generated) {
        return mergeGenerated(existing, generated, null);
    }

    public static CharacterEntry mergeGenerated(
            CharacterEntry existing,
            CharacterEntry generated,
            CharacterCardAiOptions options) {
        CharacterEntry result = existing;
        if (generated.name() != null && !generated.name().isBlank()
                && (existing.name().isBlank() || isPlaceholderName(existing.name()))) {
            result = result.withName(generated.name());
        }
        if (generated.imageMarkdown() != null && !generated.imageMarkdown().isBlank()
                && (result.imageMarkdown() == null || result.imageMarkdown().isBlank())) {
            result = result.withImageMarkdown(generated.imageMarkdown());
        }
        boolean onlyEmpty = options == null || options.onlyEmptyFields();
        Set<String> allowedFields = options == null || options.fields().isEmpty()
                ? null
                : options.fields();
        for (Map.Entry<String, String> field : generated.fields().entrySet()) {
            String label = field.getKey();
            if (allowedFields != null && !allowedFields.contains(label)) {
                continue;
            }
            String value = field.getValue();
            if (value == null || value.isBlank()) {
                continue;
            }
            if (onlyEmpty && !result.field(label).isBlank()) {
                continue;
            }
            result = result.withField(label, value);
        }
        return result;
    }

    public static CharacterEntry parseCharacterSection(String markdown) {
        Document doc = parse(markdown);
        for (Block block : doc.blocks()) {
            if (block instanceof CharacterBlock characterBlock) {
                return characterBlock.entry();
            }
        }
        if (!doc.preamble().isBlank()) {
            return parseCharacterBody("Neue Figur", doc.preamble());
        }
        return new CharacterEntry("Neue Figur");
    }

    static CharacterEntry parseCharacterBody(String title, String body) {
        String working = body == null ? "" : body;
        String imageMarkdown = "";
        List<MarkdownImageSupport.ParsedBlock> images = MarkdownImageSupport.parseBlocks(working);
        if (!images.isEmpty()) {
            MarkdownImageSupport.ParsedBlock first = images.get(0);
            imageMarkdown = working.substring(first.start(), first.end()).trim();
            working = (working.substring(0, first.start()) + working.substring(first.end())).trim();
        }

        LinkedHashMap<String, String> fields = emptyFields();
        StringBuilder freeform = new StringBuilder();
        Matcher fact = FACT_LINE.matcher(working);
        int lastEnd = 0;
        String lastLabel = null;
        while (fact.find()) {
            if (fact.start() > lastEnd) {
                String gap = working.substring(lastEnd, fact.start());
                if (lastLabel != null) {
                    String merged = mergeFieldGap(fields.getOrDefault(lastLabel, ""), gap);
                    if (!merged.isBlank()) {
                        fields.put(lastLabel, merged);
                    }
                } else {
                    appendFreeform(freeform, gap);
                }
            }
            String label = canonicalizeFieldLabel(fact.group(1).trim());
            String value = sanitizeFieldText(fact.group(2).trim());
            if (!label.isBlank()) {
                String existing = fields.getOrDefault(label, "");
                fields.put(label, existing.isBlank() ? value : existing + "\n" + value);
                lastLabel = label;
            }
            lastEnd = fact.end();
        }
        if (lastEnd < working.length()) {
            String gap = working.substring(lastEnd);
            if (lastLabel != null) {
                String merged = mergeFieldGap(fields.getOrDefault(lastLabel, ""), gap);
                if (!merged.isBlank()) {
                    fields.put(lastLabel, merged);
                }
            } else {
                appendFreeform(freeform, gap);
            }
        }
        String notes = sanitizeFieldText(freeform.toString().trim());
        if (!notes.isEmpty()) {
            String existing = fields.getOrDefault("Notizen", "");
            fields.put("Notizen", existing.isBlank() ? notes : existing + "\n\n" + notes);
        }
        return new CharacterEntry(title, imageMarkdown, fields);
    }

    /** ASCII-Schreibweisen aus KI-Output auf Standard-Labels abbilden. */
    static String canonicalizeFieldLabel(String label) {
        if (label == null || label.isBlank()) {
            return "";
        }
        String trimmed = label.trim();
        String key = WorldbuildingTermIndex.normalizeKey(trimmed);
        return switch (key) {
            case "persoenlichkeit", "personality" -> "Persönlichkeit";
            case "alter / aussehen", "alter/aussehen", "aussehen", "aeusseres", "äußeres",
                    "aeussere beschreibung", "äußere beschreibung" -> "Alter / Aussehen";
            case "schwaechen / innere konflikte", "schwaechen/innere konflikte",
                    "schwaechen", "schwächen" -> "Schwächen / innere Konflikte";
            case "andere namen / alias", "andere namen", "alias", "aliase" -> "Andere Namen / Alias";
            case "character arc", "charakterbogen", "entwicklungsbogen" -> "Character Arc";
            default -> {
                for (String standard : STANDARD_FIELD_LABELS) {
                    if (WorldbuildingTermIndex.normalizeKey(standard).equals(key)
                            || standard.equalsIgnoreCase(trimmed)) {
                        yield standard;
                    }
                }
                yield trimmed;
            }
        };
    }

    private static String mergeFieldGap(String existing, String gap) {
        String cleaned = sanitizeFieldText(gap == null ? "" : gap.trim());
        if (cleaned.isBlank()) {
            return existing == null ? "" : existing;
        }
        if (existing == null || existing.isBlank()) {
            return cleaned;
        }
        return existing + "\n" + cleaned;
    }

    static String serializeCharacter(CharacterEntry entry) {
        StringBuilder out = new StringBuilder();
        out.append("## ").append(entry.name()).append('\n');
        if (entry.imageMarkdown() != null && !entry.imageMarkdown().isBlank()) {
            out.append('\n').append(entry.imageMarkdown().trim()).append("\n\n");
        }
        LinkedHashMap<String, String> ordered = orderedFields(entry.fields());
        for (Map.Entry<String, String> field : ordered.entrySet()) {
            if (field.getValue() == null || field.getValue().isBlank()) {
                continue;
            }
            out.append("**").append(field.getKey()).append(":** ")
                    .append(field.getValue().trim()).append('\n');
        }
        return out.toString().trim();
    }

    static LinkedHashMap<String, String> orderedFields(LinkedHashMap<String, String> fields) {
        LinkedHashMap<String, String> ordered = new LinkedHashMap<>();
        for (String label : STANDARD_FIELD_LABELS) {
            String value = fields.get(label);
            if (value != null && !value.isBlank()) {
                ordered.put(label, value);
            }
        }
        for (Map.Entry<String, String> field : fields.entrySet()) {
            if (!ordered.containsKey(field.getKey()) && field.getValue() != null && !field.getValue().isBlank()) {
                ordered.put(field.getKey(), field.getValue());
            }
        }
        return ordered;
    }

    static LinkedHashMap<String, String> emptyFields() {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        for (String label : STANDARD_FIELD_LABELS) {
            fields.put(label, "");
        }
        return fields;
    }

    static boolean isMetaSection(String title) {
        if (title == null || title.isBlank()) {
            return true;
        }
        String key = WorldbuildingTermIndex.normalizeKey(title);
        return META_SECTIONS.contains(key) || key.startsWith("roman-assistent");
    }

    static boolean isKnownField(String label) {
        if (label == null) {
            return false;
        }
        for (String standard : STANDARD_FIELD_LABELS) {
            if (standard.equalsIgnoreCase(label.trim())) {
                return true;
            }
        }
        return false;
    }

    static boolean isPlaceholderName(String name) {
        if (name == null) {
            return true;
        }
        String trimmed = name.trim();
        return trimmed.isEmpty()
                || "Neue Figur".equalsIgnoreCase(trimmed)
                || "Neuer Charakter".equalsIgnoreCase(trimmed);
    }

    private static final Pattern STANDALONE_RULE_LINE = Pattern.compile("^(?:\\*{3,}|-{3,}|_{3,})\\s*$");

    /**
     * Entfernt Markdown-Trennlinien ({@code ---}, {@code ***}, {@code ___}) als eigene Zeile.
     * Die landen sonst oft in „Notizen“ (Parser/ KI) und der Editor zeigt sie als HR an.
     */
    static String sanitizeFieldText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (String line : text.split("\\R", -1)) {
            if (STANDALONE_RULE_LINE.matcher(line.trim()).matches()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append('\n');
            }
            out.append(line);
        }
        return out.toString().trim();
    }

    private static void appendFreeform(StringBuilder freeform, String chunk) {
        if (chunk == null) {
            return;
        }
        String trimmed = chunk.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        if (!freeform.isEmpty()) {
            freeform.append('\n');
        }
        freeform.append(trimmed);
    }
}
