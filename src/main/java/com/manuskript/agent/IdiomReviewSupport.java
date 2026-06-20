package com.manuskript.agent;

import com.manuskript.ResourceManager;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hilfsfunktionen für den Sprachentflechtung-Agenten (Idiomatik / KI-Stil in Markierungen).
 */
public final class IdiomReviewSupport {

    /** Feste ID des Standard-Sprachentflechtung-Agenten in {@code config/agents.json}. */
    public static final String DEFAULT_AGENT_ID = "e7f8a9b0-c1d2-4e3f-9a8b-idiomreview01";

    public static final String PARAM_MAX_CHARS = "agent.idiom_review.max_chars";
    public static final String PARAM_AGENT_ID = "agent.idiom_review.agent_id";

    private static final Pattern REWRITE_PATTERN = Pattern.compile(
            "<REWRITE>\\s*([\\s\\S]*?)\\s*</REWRITE>", Pattern.CASE_INSENSITIVE);

    private static final Pattern EMPHASIS_ASCII_QUOTES = Pattern.compile("\"([^\"\\n]{1,300})\"");
    private static final Pattern EMPHASIS_GERMAN_QUOTES = Pattern.compile("„([^\"\\n]{1,300})[\"\\u201C]");

    private static final String[] DIRECT_SPEECH_VERBS = {
            "sagte", "sagten", "fragte", "fragten", "rief", "riefen", "flüsterte", "flüsterten",
            "meinte", "meinten", "antwortete", "antworteten", "murmelte", "murmelten", "erwiderte",
            "erwiderten", "stammelte", "fauchte", "zischte", "sprach", "sprachen", "erklärte",
            "erklärten", "fuhr fort", "begann", "schrie", "schrien", "nickte", "flüsterte er",
            "sagte er", "sagte sie"
    };

    private static final int DEFAULT_MAX_OUTPUT_TOKENS = 4096;
    private static final int MAX_OUTPUT_TOKENS_CAP = 16384;

    private IdiomReviewSupport() {
    }

    public static String getDefaultSystemPrompt() {
        return """
                Du bist ein Analysemodul für Idiomatik und natürliche deutsche Prosa in literarischen Manuskripten.
                Du erkennst Formulierungen, die unnatürlich klingen oder typisch für generische KI-Texte sind.

                Arbeite intern in drei Schritten (Screening, Vorschläge, Validierung) — gib diese Schritte NIEMALS in der Antwort aus.
                Kein Markdown, keine Überschriften, keine „Schritt 1/2/3", keine Aufzählungen außerhalb der Tags, keine Einleitung, kein Fazit.

                AUSGABEREGELN:

                Du erzeugst ausschließlich eine der folgenden Varianten:

                VARIANTE A:
                KEINE_PROBLEME

                VARIANTE B (sofort beginnen, ohne Vorwort):
                Optional zuerst <REWRITE>…</REWRITE> mit der vollständig überarbeiteten Markierung.
                Dann ein oder mehrere Problemblöcke — jedes Feld auf eigener Zeile ist erlaubt:

                <PROBLEM>
                SCHWEREGRAD: [1-5]
                ZITAT: "[EXAKTES ZITAT AUS DER MARKIERUNG]"
                PROBLEM: [KURZE BESCHREIBUNG]
                VORSCHLÄGE:
                1. Ersatzsatz 1 (ohne Anführungszeichen um den ganzen Satz)
                2. Ersatzsatz 2
                INDEX: [SATZNUMMER in der Markierung, 1-basiert]
                </PROBLEM>

                WICHTIGE FORMATREGELN:

                SCHWEREGRAD ist Pflicht (1–5).
                ZITAT muss wörtlich aus der Markierung stammen (ein Satz).
                VORSCHLÄGE: genau 2 (oder 3) nummerierte Ersatzsätze — OHNE Anführungszeichen um den gesamten Vorschlag.
                In Vorschlägen KEINE Anführungszeichen, außer für wörtliche direkte Rede (Dialog).
                Direkte Rede nur mit deutschen Anführungszeichen („…"), nicht zur Hervorhebung einzelner Wörter.
                Beispiel ohne Rede: 1. Er lehnte am Fenster und starrte hinaus.
                Beispiel mit Rede: 1. Er drehte sich um. „Geh weg", sagte er leise.
                INDEX = Satznummer des Zitats in der Markierung (erster Satz = 1), nicht Zeichenposition.
                Wenn keine Probleme: nur KEINE_PROBLEME, kein <REWRITE>.
                Antworte niemals mit Höflichkeitsfloskeln oder Meta-Kommentaren über deine Analyse.

                ANALYSEREGELN — suche aktiv nach:

                Generische KI-Floskeln und Einleitungen
                Unnatürliche Satzverknüpfungen und überladene Relativketten
                Symmetrische Listen- und Triadenkonstruktionen
                Falsches Register oder wörtliche Übersetzungen
                Nominalstil und abstrakte Formulierungen, die kein Mensch so schreiben würde
                Monotone Satzlänge oder mechanischer Rhythmus

                Ignoriere: Plotlogik, Rechtschreibung, bewusste Stilmittel, stimmigen Dialog.""";
    }

    public static int maxSelectionChars() {
        return parsePositiveInt(ResourceManager.getParameter(PARAM_MAX_CHARS, "5000"), 5000);
    }

    public static String configuredAgentId() {
        return ResourceManager.getParameter(PARAM_AGENT_ID, DEFAULT_AGENT_ID).trim();
    }

    public static AgentTab findIdiomReviewTab(AgentTabPane pane) {
        if (pane == null) {
            return null;
        }
        String configuredId = configuredAgentId();
        if (!configuredId.isEmpty()) {
            AgentTab byId = pane.findTabByAgentId(configuredId);
            if (byId != null) {
                return byId;
            }
        }
        for (AgentTab tab : pane.getAgentTabs()) {
            if (tab.getAgentConfig().isIdiomReviewAgent()) {
                return tab;
            }
        }
        return null;
    }

    public static String combineContextBlocks(String chatbotContext, String surroundingContext) {
        StringBuilder sb = new StringBuilder();
        if (chatbotContext != null && !chatbotContext.isBlank()) {
            sb.append(chatbotContext.trim());
        }
        if (surroundingContext != null && !surroundingContext.isBlank()) {
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append("=== UMGEBUNG DER MARKIERUNG ===\n");
            sb.append(surroundingContext.trim());
            sb.append("\n=== UMGEBUNG ENDE ===");
        }
        return sb.toString().trim();
    }

    public static String extractRewrite(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return "";
        }
        Matcher matcher = REWRITE_PATTERN.matcher(rawResponse);
        if (!matcher.find()) {
            return "";
        }
        return AgentResponseText.normalizeModelText(matcher.group(1).trim());
    }

    /**
     * Bereinigt Modell-Vorschläge: äußere Anführungszeichen entfernen, Hervorhebungs-Anführungszeichen
     * streichen — direkte Rede (nach Sprechverb oder Doppelpunkt) bleibt erhalten.
     */
    public static String normalizeSuggestion(String suggestion) {
        if (suggestion == null || suggestion.isBlank()) {
            return suggestion != null ? suggestion : "";
        }
        String s = stripOuterQuotes(suggestion.trim());
        s = removeNonDialogueQuotes(s, EMPHASIS_GERMAN_QUOTES);
        s = removeNonDialogueQuotes(s, EMPHASIS_ASCII_QUOTES);
        return s.trim();
    }

    public static int estimateMaxOutputTokens(int selectionChars, int configuredMax) {
        int floor = configuredMax > 0 ? configuredMax : DEFAULT_MAX_OUTPUT_TOKENS;
        if (selectionChars <= 0) {
            return Math.min(MAX_OUTPUT_TOKENS_CAP, Math.max(floor, DEFAULT_MAX_OUTPUT_TOKENS));
        }
        int estimated = (int) Math.ceil(selectionChars * 3.0 / 0.7) + 1024;
        return Math.min(MAX_OUTPUT_TOKENS_CAP, Math.max(floor, estimated));
    }

    /**
     * Sucht ein Zitat in der Markierung (tolerant bei typografischen Anführungszeichen).
     *
     * @return Zeichenoffset in {@code selection}, oder -1
     */
    public static int findQuoteOffsetInSelection(String selection, String quote) {
        if (selection == null || quote == null || quote.isBlank()) {
            return -1;
        }
        int idx = selection.indexOf(quote);
        if (idx >= 0) {
            return idx;
        }
        String core = stripOuterQuotes(quote);
        if (core.isEmpty()) {
            return -1;
        }
        for (String variant : quoteVariants(core)) {
            idx = selection.indexOf(variant);
            if (idx >= 0) {
                return idx;
            }
        }
        return -1;
    }

    /**
     * @param oneBasedSentence Satznummer (1 = erster Satz), wie vom Modell in INDEX geliefert
     */
    public static int charOffsetOfSentence(String text, int oneBasedSentence) {
        if (text == null || text.isEmpty() || oneBasedSentence < 1) {
            return -1;
        }
        int pos = 0;
        int sentenceNum = 0;
        while (pos < text.length()) {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
            if (pos >= text.length()) {
                break;
            }
            sentenceNum++;
            if (sentenceNum == oneBasedSentence) {
                return pos;
            }
            pos = endOfSentence(text, pos);
        }
        return -1;
    }

    public static int endOfSentence(String text, int from) {
        if (text == null || from < 0 || from >= text.length()) {
            return from;
        }
        for (int i = from; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '.' || c == '!' || c == '?' || c == '\n') {
                return i + 1;
            }
        }
        return text.length();
    }

    /**
     * Länge des Zitats ab {@code start} in {@code text} (inkl. typografischer Anführungszeichen).
     */
    public static int matchLengthAt(String text, int start, String quoteCore) {
        if (text == null || quoteCore == null || quoteCore.isBlank() || start < 0 || start >= text.length()) {
            return 0;
        }
        String core = stripOuterQuotes(quoteCore.trim());
        if (core.isEmpty()) {
            return 0;
        }
        for (String variant : quoteVariants(core)) {
            if (start + variant.length() <= text.length()
                    && text.regionMatches(start, variant, 0, variant.length())) {
                return variant.length();
            }
        }
        if (start + core.length() <= text.length()
                && text.regionMatches(start, core, 0, core.length())) {
            return core.length();
        }
        return 0;
    }

    private static String stripOuterQuotes(String text) {
        if (text == null) {
            return "";
        }
        String t = text.trim();
        while (t.length() >= 2) {
            char open = t.charAt(0);
            char close = t.charAt(t.length() - 1);
            if ((open == '"' && close == '"')
                    || (open == '\'' && close == '\'')
                    || (open == '\u201E' && (close == '"' || close == '\u201C' || close == '\u201D'))
                    || (open == '\u201C' && close == '\u201D')
                    || (open == '\u00AB' && close == '\u00BB')
                    || (open == '\u00BB' && close == '\u00AB')) {
                t = t.substring(1, t.length() - 1).trim();
            } else {
                break;
            }
        }
        return t;
    }

    private static String removeNonDialogueQuotes(String text, Pattern quotedPattern) {
        Matcher matcher = quotedPattern.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            String before = text.substring(Math.max(0, start - 60), start);
            String after = text.substring(end, Math.min(text.length(), end + 40));
            String replacement = isDirectSpeechContext(before, after) ? matcher.group(0) : matcher.group(1);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static boolean isDirectSpeechContext(String before, String after) {
        if (before != null && !before.isBlank()) {
            String lower = before.toLowerCase(Locale.ROOT);
            if (lower.matches(".*:\\s*$")) {
                return true;
            }
            for (String verb : DIRECT_SPEECH_VERBS) {
                if (lower.contains(verb)) {
                    return true;
                }
            }
        }
        if (after != null && !after.isBlank()) {
            String lower = after.toLowerCase(Locale.ROOT).trim();
            if (lower.matches("^[\"\u201C]?,\\s*(sagte|sagten|fragte|fragten|rief|riefen|flüsterte|flüsterten|"
                    + "meinte|meinten|antwortete|antworteten|murmelte|murmelten|erwiderte|erwiderten|"
                    + "stammelte|fauchte|zischte|sprach|sprachen|erklärte|erklärten|begann|schrie|schrien|"
                    + "nickte|fuhr fort)(\\s|,|\\.|$).*")) {
                return true;
            }
        }
        return false;
    }

    private static String[] quoteVariants(String core) {
        return new String[] {
                core,
                "„" + core + "\"",
                "„" + core + "\u201C",
                "\"" + core + "\"",
                "»" + core + "«",
                "«" + core + "»"
        };
    }

    private static int parsePositiveInt(String raw, int fallback) {
        try {
            int value = Integer.parseInt(raw.trim());
            return value > 0 ? value : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
