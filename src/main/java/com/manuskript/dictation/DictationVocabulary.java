package com.manuskript.dictation;

import com.manuskript.ChapterEditorHost;
import com.manuskript.NovelManager;

import java.io.File;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Projekt-Glossar für Diktat: Nutzer-Datei + automatisch aus Figuren und Manuskript.
 */
public final class DictationVocabulary {

    private static final int MAX_WHISPER_PROMPT_CHARS = 420;
    private static final int MAX_LLM_TERMS = 120;

    private static final Pattern H2_HEADING = Pattern.compile("(?m)^## ([^\\n\\r]+)$");
    private static final Pattern HYPHEN_COMPOUND = Pattern.compile(
            "\\b([A-Za-zÄÖÜäöüß]{2,})-([A-Za-zÄÖÜäöüß]{2,})\\b");
    private static final Pattern MARKDOWN_TERM = Pattern.compile(
            "\\*\\*?([A-Za-zÄÖÜäöüß][A-Za-zÄÖÜäöüß'-]{1,})\\*\\*?");
    private static final Pattern PROPER_NOUN = Pattern.compile("\\b[A-ZÄÖÜ][a-zäöüß]{2,}\\b");

    private static final Set<String> META_SECTIONS = Set.of(
            "character sheets", "roman-assistent figuren", "roman-assistent: figuren",
            "charaktere", "figuren", "worldbuilding", "orte", "orte und regionen",
            "regionen", "factions", "fraktionen", "glossar", "uebersicht", "übersicht"
    );

    private final List<String> userGlossary;
    private final List<String> characterNames;
    private final List<String> autoTerms;

    private DictationVocabulary(List<String> userGlossary, List<String> characterNames,
                                List<String> autoTerms) {
        this.userGlossary = List.copyOf(userGlossary);
        this.characterNames = List.copyOf(characterNames);
        this.autoTerms = List.copyOf(autoTerms);
    }

    public static DictationVocabulary empty() {
        return new DictationVocabulary(List.of(), List.of(), List.of());
    }

    public static DictationVocabulary fromHost(ChapterEditorHost host) {
        if (host == null) {
            return empty();
        }
        File docx = host.getOriginalDocxFile();
        if (docx == null) {
            return fromSources("", "", "", host.getText());
        }
        String path = docx.getAbsolutePath();
        NovelManager.ensureDictationGlossary(path);
        return fromSources(
                safeLoad(NovelManager.loadDictationGlossary(path)),
                safeLoad(NovelManager.loadCharacters(path)),
                safeLoad(NovelManager.loadWorldbuilding(path)),
                host.getText());
    }

    static DictationVocabulary fromSources(String glossaryText, String charactersText,
                                           String worldText, String chapterText) {
        List<String> user = parseGlossaryFile(glossaryText);
        List<String> characters = extractSectionNames(charactersText);
        List<String> world = extractSectionNames(worldText);

        LinkedHashSet<String> auto = new LinkedHashSet<>();
        auto.addAll(world);
        auto.addAll(extractTermsFromManuscript(chapterText, mergeKnown(user, characters, world)));
        auto.removeAll(user);
        auto.removeAll(characters);

        return new DictationVocabulary(user, characters, new ArrayList<>(auto));
    }

    public boolean isEmpty() {
        return userGlossary.isEmpty() && characterNames.isEmpty() && autoTerms.isEmpty();
    }

    public List<String> userGlossaryTerms() {
        return userGlossary;
    }

    public List<String> characterNames() {
        return characterNames;
    }

    public List<String> autoTerms() {
        return autoTerms;
    }

    /** @deprecated Nur für Tests – nutze {@link #autoTerms()}. */
    @Deprecated
    public List<String> manuscriptTerms() {
        return autoTerms;
    }

    /** @deprecated Nur für Tests. */
    @Deprecated
    public List<String> worldTerms() {
        return autoTerms;
    }

    public int termCount() {
        return allTerms().size();
    }

    public boolean hasUserGlossary() {
        return !userGlossary.isEmpty();
    }

    public String whisperInitialPrompt() {
        List<String> terms = allTerms();
        if (terms.isEmpty()) {
            return "Deutsch. Eigennamen und Fremdwörter wörtlich.";
        }
        StringBuilder sb = new StringBuilder("Deutsch. Glossar: ");
        appendLimited(sb, terms, MAX_WHISPER_PROMPT_CHARS - 30);
        sb.append(". Fremdwörter wörtlich.");
        return trimToLength(sb.toString(), MAX_WHISPER_PROMPT_CHARS);
    }

    public String llmGlossaryBlock() {
        if (isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Projekt-Glossar (verbindliche Schreibweisen — Rohtranskript dagegen korrigieren):\n");
        if (!userGlossary.isEmpty()) {
            sb.append("Vom Autor gepflegt:\n");
            sb.append(joinLimited(userGlossary, MAX_LLM_TERMS)).append("\n");
        }
        if (!characterNames.isEmpty()) {
            sb.append("Figuren:\n");
            sb.append(joinLimited(characterNames, MAX_LLM_TERMS)).append("\n");
        }
        if (!autoTerms.isEmpty()) {
            sb.append("Aus Manuskript:\n");
            sb.append(joinLimited(autoTerms, MAX_LLM_TERMS)).append("\n");
        }
        sb.append("""
                
                Korrektur-Regeln:
                - Spracherkennung verhunzt Eigennamen und Englisch oft phonetisch (deutsche Lautschrift).
                - Jeden verdächtigen Begriff im Rohtranskript mit dem Glossar abgleichen.
                - Phonetisch ähnliche Fehler → Glossar-Schreibweise.
                - Fälschlich zusammengezogene Wörter (zwei Glossar-Einträge in einem) → trennen, Bindestrich wenn im Glossar so.
                """);
        return sb.toString().trim();
    }

    private List<String> allTerms() {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        merged.addAll(userGlossary);
        merged.addAll(characterNames);
        merged.addAll(autoTerms);
        return new ArrayList<>(merged);
    }

    static List<String> parseGlossaryFile(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            if (trimmed.contains(",")) {
                for (String part : trimmed.split(",")) {
                    addGlossaryTerm(terms, part.trim());
                }
            } else {
                addGlossaryTerm(terms, trimmed);
            }
        }
        return new ArrayList<>(terms);
    }

    private static void addGlossaryTerm(Set<String> terms, String term) {
        if (!term.isBlank()) {
            terms.add(term);
        }
    }

    static List<String> extractSectionNames(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> names = new LinkedHashSet<>();
        Matcher matcher = H2_HEADING.matcher(markdown);
        while (matcher.find()) {
            String title = matcher.group(1).trim();
            if (!title.isEmpty() && !isMetaSection(title)) {
                names.add(title);
            }
        }
        return new ArrayList<>(names);
    }

    static List<String> extractTermsFromManuscript(String text, Set<String> knownLower) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> terms = new LinkedHashSet<>();

        Matcher hyphen = HYPHEN_COMPOUND.matcher(text);
        while (hyphen.find()) {
            terms.add(hyphen.group());
        }

        Matcher markdown = MARKDOWN_TERM.matcher(text);
        while (markdown.find()) {
            addIfNew(terms, markdown.group(1), knownLower);
        }

        Matcher proper = PROPER_NOUN.matcher(text);
        while (proper.find()) {
            addIfNew(terms, proper.group(), knownLower);
        }

        return new ArrayList<>(terms);
    }

    private static void addIfNew(Set<String> terms, String word, Set<String> knownLower) {
        if (word == null || word.isBlank()) {
            return;
        }
        if (!knownLower.contains(word.toLowerCase(Locale.ROOT))) {
            terms.add(word);
        }
    }

    private static Set<String> mergeKnown(List<String> user, List<String> characters, List<String> world) {
        LinkedHashSet<String> known = new LinkedHashSet<>();
        for (String term : user) {
            known.add(term.toLowerCase(Locale.ROOT));
        }
        for (String term : characters) {
            known.add(term.toLowerCase(Locale.ROOT));
        }
        for (String term : world) {
            known.add(term.toLowerCase(Locale.ROOT));
        }
        return known;
    }

    private static boolean isMetaSection(String title) {
        String key = normalizeKey(title);
        return META_SECTIONS.contains(key) || key.startsWith("roman-assistent");
    }

    private static String normalizeKey(String title) {
        String normalized = Normalizer.normalize(title.trim(), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss");
        return normalized.replaceAll("\\s+", " ");
    }

    /**
     * Fügt neue Begriffe ans Glossar an (zeilenweise), ohne Duplikate (Groß/Klein egal).
     *
     * @return Anzahl neu hinzugefügter Begriffe
     */
    public static int mergeTermsIntoGlossaryText(StringBuilder glossaryText, Iterable<String> newTerms) {
        if (glossaryText == null || newTerms == null) {
            return 0;
        }
        LinkedHashSet<String> existingLower = new LinkedHashSet<>();
        for (String term : parseGlossaryFile(glossaryText.toString())) {
            existingLower.add(term.toLowerCase(Locale.ROOT));
        }
        int added = 0;
        StringBuilder append = new StringBuilder();
        for (String term : newTerms) {
            if (term == null) {
                continue;
            }
            String trimmed = term.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String key = trimmed.toLowerCase(Locale.ROOT);
            if (existingLower.contains(key)) {
                continue;
            }
            existingLower.add(key);
            append.append(trimmed).append('\n');
            added++;
        }
        if (added == 0) {
            return 0;
        }
        if (glossaryText.length() > 0 && glossaryText.charAt(glossaryText.length() - 1) != '\n') {
            glossaryText.append('\n');
        }
        glossaryText.append(append);
        return added;
    }

    /**
     * Sammelt World-Editor-Begriffe (##-Überschriften) aus Figuren- und Worldbuilding-Datei.
     */
    public static List<String> collectWorldEditorTerms(String charactersText, String worldbuildingText) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        terms.addAll(extractSectionNames(charactersText));
        terms.addAll(extractSectionNames(worldbuildingText));
        return new ArrayList<>(terms);
    }

    private static String safeLoad(String text) {
        return text != null ? text : "";
    }

    private static String joinLimited(List<String> items, int max) {
        if (items.isEmpty()) {
            return "";
        }
        String joined = String.join(", ", items.subList(0, Math.min(max, items.size())));
        if (items.size() > max) {
            joined += ", …";
        }
        return joined;
    }

    private static void appendLimited(StringBuilder sb, List<String> items, int maxChars) {
        if (items.isEmpty() || maxChars <= 0) {
            return;
        }
        boolean first = true;
        for (String item : items) {
            String part = first ? item : ", " + item;
            if (sb.length() + part.length() > maxChars) {
                sb.append(", …");
                return;
            }
            sb.append(part);
            first = false;
        }
    }

    private static String trimToLength(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max - 1).trim() + "…";
    }
}
