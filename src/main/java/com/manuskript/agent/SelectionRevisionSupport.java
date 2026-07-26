package com.manuskript.agent;

import com.manuskript.ResourceManager;

/**
 * Hilfsfunktionen für den Überarbeiten-Agenten (Markierung analysieren).
 */
public final class SelectionRevisionSupport {

    /** Feste ID des Standard-Überarbeiten-Agenten in {@code config/agents.json}. */
    public static final String DEFAULT_AGENT_ID = "c91e4b7a-2d3d-4a1f-9e8b-selectionrev01";

    public static final String PARAM_MAX_CHARS = "agent.selection_revision.max_chars";
    public static final String PARAM_CONTEXT_CHARS = "agent.selection_revision.context_chars";
    public static final String PARAM_AGENT_ID = "agent.selection_revision.agent_id";

    /** Platzhalter im Modell-Output — der Client kennt die Markierung bereits. */
    public static final String MARKED_QUOTE_PLACEHOLDER = "(MARKIERT)";

    private static final int DEFAULT_MAX_OUTPUT_TOKENS = 8192;
    private static final int MAX_OUTPUT_TOKENS_CAP = 32768;

    private SelectionRevisionSupport() {
    }

    public static String getDefaultSystemPrompt() {
        return """
                Du analysierst AUSSCHLIESSLICH den markierten Textabschnitt zwischen === MANUSKRIPT BEGINN === und === MANUSKRIPT ENDE ===.

                AUSGABEREGELN:

                Du erzeugst ausschließlich eine der folgenden zwei Antworten:

                VARIANTE A:
                KEINE_PROBLEME

                VARIANTE B — genau EIN Block:
                <PROBLEM> SCHWEREGRAD: [1-5] ZITAT: (MARKIERT) PROBLEM: Der markierte Text muss überarbeitet werden. [Konkrete Begründung] VORSCHLÄGE: "[Vollständiger Ersatztext 1]", "[Vollständiger Ersatztext 2]" </PROBLEM>

                WICHTIGE FORMATREGELN:

                Verwende niemals Markdown.
                Verwende niemals Aufzählungen.
                Verwende niemals Nummerierungen.
                Verwende niemals zusätzlichen Fließtext.
                Das Feld ZITAT ist immer exakt (MARKIERT) — wiederhole den markierten Text nicht.
                Das Feld PROBLEM beginnt mit „Der markierte Text muss überarbeitet werden.“ und enthält nur die Begründung (keine Ersatztexte).
                Genau 2 VORSCHLÄGE — vollständige Ersatztexte für die gesamte Markierung von Anfang bis Ende.
                VORSCHLÄGE niemals kürzen oder mit … abkürzen; beide Varianten müssen die komplette Markierung ersetzen.
                In VORSCHLÄGEN echte Zeilenumbrüche/Absätze beibehalten; nicht die Zeichenfolge \\n schreiben.
                Wenn der Abschnitt ausreichend ist: KEINE_PROBLEME.
                Wenn eine ANWEISUNG DES AUTORS mitgegeben wird, hat sie Vorrang vor allgemeinen Stilregeln.

                ANALYSEREGELN:

                Prüfe den markierten Abschnitt auf fehlende Informationen, Unklarheiten, Widersprüche zum Kontext, schwache Formulierung und konkret verbesserbare Stellen.
                Ignoriere reine Rechtschreibfragen, es sei denn sie beeinträchtigen das Verständnis.""";
    }

    /**
     * Ergänzt die Autoren-Anweisung um Längen- und Format-Hinweise für den Überarbeiten-Agenten.
     */
    public static String buildAuthorInstruction(String authorInstruction, int selectionChars) {
        StringBuilder sb = new StringBuilder();
        if (authorInstruction != null && !authorInstruction.isBlank()) {
            sb.append(authorInstruction.trim()).append("\n\n");
        }
        sb.append("Die Markierung umfasst ").append(selectionChars).append(" Zeichen.\n");
        sb.append("Im Antwortblock ZITAT: ").append(MARKED_QUOTE_PLACEHOLDER).append(" verwenden.\n");
        sb.append("Beide VORSCHLÄGE müssen vollständige Ersatztexte für die gesamte Markierung sein (nicht gekürzt).");
        return sb.toString();
    }

    /**
     * Prüft, ob ein Vorschlag die Markierung vermutlich nur teilweise abdeckt
     * (typisch bei Token-Limit / abgeschnittenem Modell-Output).
     */
    public static boolean isLikelyTruncatedRewrite(String originalSelection, String suggestion) {
        if (originalSelection == null || suggestion == null) {
            return false;
        }
        String original = originalSelection.trim();
        String sugg = suggestion.trim();
        if (original.length() < 250 || sugg.isEmpty()) {
            return false;
        }
        if (sugg.length() >= original.length() * 0.85) {
            return false;
        }
        // Deutlich kürzer und endet nicht mit Satzzeichen → sehr verdächtig
        char last = sugg.charAt(sugg.length() - 1);
        boolean endsSentence = last == '.' || last == '!' || last == '?' || last == '"'
                || last == '\u201C' || last == '\u201D';
        if (!endsSentence && sugg.length() < original.length() * 0.75) {
            return true;
        }
        return sugg.length() < original.length() * 0.65;
    }

    /**
     * Fügt bei abgeschnittenem Rewrite den Rest der Markierung wieder an,
     * geschnitten an einer Satzgrenze nahe der geschätzten Abdeckung.
     *
     * @return gemergter Text oder {@code null}, wenn kein sinnvoller Schnitt möglich ist
     */
    public static String mergeTruncatedRewrite(String originalSelection, String suggestion) {
        if (originalSelection == null || suggestion == null) {
            return null;
        }
        String original = originalSelection;
        String sugg = suggestion;
        if (original.isEmpty() || sugg.isEmpty()) {
            return null;
        }
        if (!isLikelyTruncatedRewrite(original, sugg)) {
            return sugg;
        }
        int approxCut = estimateCoverageCut(original, sugg);
        int cut = snapToSentenceBoundary(original, approxCut);
        if (cut <= 0 || cut >= original.length()) {
            return null;
        }
        // Schnitt deckt fast nichts ab → unsicher
        int minCut = Math.max(20, Math.min(sugg.length() / 2, original.length() / 10));
        if (cut < minCut) {
            return null;
        }
        String tail = original.substring(cut);
        if (tail.isBlank()) {
            return sugg;
        }
        if (Character.isWhitespace(sugg.charAt(sugg.length() - 1))
                || Character.isWhitespace(tail.charAt(0))) {
            return sugg + tail;
        }
        return sugg + tail;
    }

    /**
     * Sucht ein Modell-Zitat innerhalb der Markierung (exakt oder als längstes Präfix).
     *
     * @return [relStart, relEnd] oder {@code null}
     */
    public static int[] findQuoteSpanInSelection(String selection, String quote) {
        if (selection == null || selection.isBlank() || quote == null || quote.isBlank()) {
            return null;
        }
        if (isMarkedQuotePlaceholder(quote)) {
            return null;
        }
        String q = quote.trim();
        int exact = selection.indexOf(q);
        if (exact >= 0) {
            return new int[]{exact, exact + q.length()};
        }
        // Modell-Zitat leicht verkürzt: längstes Präfix ≥ 40 Zeichen
        int minLen = Math.min(q.length(), Math.max(40, q.length() / 2));
        for (int len = q.length(); len >= minLen; len--) {
            String prefix = q.substring(0, len);
            int idx = selection.indexOf(prefix);
            if (idx >= 0) {
                int end = IdiomReviewSupport.endOfSentence(selection, idx);
                return new int[]{idx, Math.max(idx + prefix.length(), end)};
            }
        }
        return null;
    }

    private static int estimateCoverageCut(String original, String suggestion) {
        // Umschreibungen haben oft ähnliche Länge → Schnitt nahe suggestion.length
        double ratio = 1.0;
        int cut = (int) Math.round(suggestion.length() * ratio);
        return Math.max(1, Math.min(original.length() - 1, cut));
    }

    private static int snapToSentenceBoundary(String text, int approx) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int target = Math.max(0, Math.min(text.length(), approx));
        // Nächstes Satzende ab target (nach vorne)
        int forward = IdiomReviewSupport.endOfSentence(text, target);
        // Vorheriges Satzende: rückwärts suchen
        int backward = target;
        for (int i = target - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (c == '.' || c == '!' || c == '?' || c == '\n') {
                backward = i + 1;
                break;
            }
            if (i == 0) {
                backward = 0;
            }
        }
        // Nähere Grenze wählen, Forward bevorzugen wenn ähnlich
        if (backward <= 0) {
            return forward;
        }
        if (Math.abs(forward - target) <= Math.abs(target - backward)) {
            return forward;
        }
        return backward;
    }

    /**
     * Schätzt benötigte Ausgabe-Tokens: zwei vollständige Umschreibungen der Markierung plus Overhead.
     */
    public static int estimateMaxOutputTokens(int selectionChars, int configuredMax) {
        int floor = configuredMax > 0 ? configuredMax : DEFAULT_MAX_OUTPUT_TOKENS;
        if (selectionChars <= 0) {
            return Math.min(MAX_OUTPUT_TOKENS_CAP, Math.max(floor, DEFAULT_MAX_OUTPUT_TOKENS));
        }
        // Deutsch: grob 0,7 Zeichen pro Token; zwei volle Ersatztexte + Puffer
        int estimated = (int) Math.ceil(selectionChars * 2.0 / 0.7) + 768;
        return Math.min(MAX_OUTPUT_TOKENS_CAP, Math.max(floor, estimated));
    }

    public static boolean isMarkedQuotePlaceholder(String quote) {
        if (quote == null) {
            return false;
        }
        String trimmed = quote.trim();
        return MARKED_QUOTE_PLACEHOLDER.equalsIgnoreCase(trimmed)
                || "(markiert)".equalsIgnoreCase(trimmed);
    }

    public static int maxSelectionChars() {
        return parsePositiveInt(ResourceManager.getParameter(PARAM_MAX_CHARS, "5000"), 5000);
    }

    public static int contextRadiusChars() {
        return parsePositiveInt(ResourceManager.getParameter(PARAM_CONTEXT_CHARS, "1500"), 1500);
    }

    public static String configuredAgentId() {
        return ResourceManager.getParameter(PARAM_AGENT_ID, DEFAULT_AGENT_ID).trim();
    }

    public static AgentTab findRevisionTab(AgentTabPane pane) {
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
            if (tab.getAgentConfig().isSelectionRevisionAgent()) {
                return tab;
            }
        }
        return null;
    }

    public static String buildSurroundingContext(String fullText, int start, int end) {
        if (fullText == null || fullText.isEmpty()) {
            return "";
        }
        int radius = contextRadiusChars();
        int len = fullText.length();
        int from = Math.max(0, start - radius);
        int to = Math.min(len, end + radius);
        StringBuilder sb = new StringBuilder();
        if (from > 0) {
            sb.append("[…]\n");
        }
        sb.append(fullText, from, to);
        if (to < len) {
            sb.append("\n[…]");
        }
        return sb.toString();
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
