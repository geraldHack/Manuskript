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
    private static final int MAX_OUTPUT_TOKENS_CAP = 16384;

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
        return parsePositiveInt(ResourceManager.getParameter(PARAM_MAX_CHARS, "2000"), 2000);
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
