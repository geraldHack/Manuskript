package com.manuskript.agent;

import java.util.prefs.Preferences;

/**
 * Bild-Prompt-Agent: kurzer Prompt für eine Bild-KI.
 * Setting und Stil kommen aus dem World-Editor, der Bildinhalt aus dem Kapitel.
 * Kleine Ollama-Modelle laufen sonst in Endlos-Markdown und erfundene Welten.
 */
public final class BildPromptSupport {

    public static final int MAX_OUTPUT_TOKENS = 768;
    public static final int MAX_CONTEXT_CHARS = 12_000;

    public static final String DEFAULT_SYSTEM = """
            Du schreibst einen einzigen Bild-Prompt für eine Bild-KI zum geöffneten Kapitel.

            Quellen:
            - MANUSKRIPT: welcher Moment, was sichtbar passiert.
            - World-Editor (Charaktere, Worldbuilding, Schreibstil): Aussehen, Kleidung, Alter, Setting, Epoche, Stimmung.

            Keine Eigennamen:
            Eine Bild-KI kennt deine Figuren und Orte nicht. Keine Namen von Personen, Planeten, Schiffen, Städten oder Projekten.
            Ersetze jeden Namen durch sichtbare Merkmale aus dem World-Editor: Alter, Körperbau, Haar, Haut, Kleidung, Haltung, Ort als Bild (Material, Licht, Epoche) — nicht als Label.

            Regeln:
            - Genau ein Prompt, dann aufhören.
            - Höchstens 150 Wörter.
            - Kein Essay, keine Zusammenfassung, keine Alternativen.
            - Nichts erfinden, das nicht in MANUSKRIPT oder Kontext steht. Fehlt ein Aussehen, beschreibe nur das Sichtbare aus dem Text.
            - Prompt und Negative auf Englisch.

            Format — sonst nichts:

            **Prompt:**
            <ein Absatz, nur Sichtbares, ohne Namen>

            **Negative:**
            <kurze Ausschlussliste zum Look aus dem Kontext>""";

    public static final String OUTPUT_CONSTRAINTS = """
            STOPP-REGEL (überschreibt Längen- und Markdown-Anweisungen oben):
            Nur **Prompt:** und **Negative:** ausgeben. Maximal 150 Wörter. Dann STOPP.
            Keine Eigennamen. Nur sichtbare Beschreibung. Nichts erfinden.""";

    public static final String AUTHOR_INSTRUCTION =
            "Nur einen Bild-Prompt ohne Eigennamen. Figuren und Orte als Aussehen beschreiben "
                    + "(World-Editor), Moment aus dem Kapitel. Maximal 150 Wörter. Danach aufhören.";

    private static final String PREF_EXTRA = "extra_prompt";
    private static final String PREF_USE_SELECTION = "use_selection";

    private BildPromptSupport() {
    }

    public static String combineAuthorInstruction(String extraPrompt) {
        return combineAuthorInstruction(extraPrompt, null);
    }

    public static String combineAuthorInstruction(String extraPrompt, String selectedText) {
        StringBuilder sb = new StringBuilder();
        String binding = formatBindingExtraPrompt(extraPrompt);
        if (!binding.isBlank()) {
            sb.append(binding).append("\n\n");
        }
        sb.append(AUTHOR_INSTRUCTION);
        if (selectedText != null && !selectedText.isBlank()) {
            sb.append("\n\nDas Bild zeigt NUR den markierten Moment. Das Kapitel ist nur Kontext.");
            sb.append("\n=== MARKIERUNG BEGINN ===\n");
            sb.append(selectedText.trim());
            sb.append("\n=== MARKIERUNG ENDE ===");
        }
        if (!binding.isBlank()) {
            sb.append("\n\nSetze den VERBINDLICHEN ZUSATZPROMPT oben im Bild-Prompt um.");
        }
        return sb.toString();
    }

    public static String formatBindingExtraPrompt(String extraPrompt) {
        if (extraPrompt == null || extraPrompt.isBlank()) {
            return "";
        }
        return "=== VERBINDLICHER ZUSATZPROMPT (ZWINGEND) ===\n"
                + extraPrompt.trim()
                + "\n=== ENDE ZUSATZPROMPT ===";
    }

    public static String loadExtraPrompt(String agentId) {
        return extraPrefs(agentId).get(PREF_EXTRA, "");
    }

    public static void persistExtraPrompt(String agentId, String text) {
        extraPrefs(agentId).put(PREF_EXTRA, text != null ? text : "");
    }

    public static boolean loadUseSelection(String agentId) {
        return extraPrefs(agentId).getBoolean(PREF_USE_SELECTION, false);
    }

    public static void persistUseSelection(String agentId, boolean useSelection) {
        extraPrefs(agentId).putBoolean(PREF_USE_SELECTION, useSelection);
    }

    private static Preferences extraPrefs(String agentId) {
        String node = agentId == null || agentId.isBlank()
                ? AgentConfigManager.BILD_PROMPT_AGENT_ID
                : agentId;
        return Preferences.userNodeForPackage(BildPromptSupport.class).node(node);
    }

    public static boolean isBildPrompt(AgentConfig config) {
        if (config == null) {
            return false;
        }
        return AgentConfigManager.BILD_PROMPT_AGENT_ID.equals(config.getId())
                || "Bild-Prompt".equals(config.getName());
    }

    public static String effectiveSystemPrompt(String userPrompt) {
        return effectiveSystemPrompt(userPrompt, null);
    }

    public static String effectiveSystemPrompt(String userPrompt, String extraPrompt) {
        String base = userPrompt == null || userPrompt.isBlank() ? DEFAULT_SYSTEM : userPrompt.trim();
        StringBuilder sb = new StringBuilder(base);
        String binding = formatBindingExtraPrompt(extraPrompt);
        if (!binding.isBlank()) {
            sb.append("\n\n").append(binding);
            sb.append("\n\nDer Zusatzprompt hat Vorrang für Blickwinkel, Licht, Komposition, Kamerabild und Stimmung.");
            sb.append(" Er darf sichtbare Details ergänzen, die im Manuskript nicht stehen — ohne Eigennamen, ohne dem Text zu widersprechen.");
        }
        sb.append("\n\n").append(OUTPUT_CONSTRAINTS);
        return sb.toString();
    }

    public static int clampMaxTokens(int requested) {
        if (requested <= 0) {
            return MAX_OUTPUT_TOKENS;
        }
        return Math.max(256, Math.min(MAX_OUTPUT_TOKENS, requested));
    }

    public static String trimContext(String context) {
        if (context == null || context.isBlank()) {
            return "";
        }
        if (context.length() <= MAX_CONTEXT_CHARS) {
            return context;
        }
        int headLen = MAX_CONTEXT_CHARS * 35 / 100;
        int tailLen = MAX_CONTEXT_CHARS * 55 / 100;
        String head = context.substring(0, headLen);
        String tail = context.substring(context.length() - tailLen);
        return head + "\n\n[... KONTEXT GEKÜRZT ...]\n\n" + tail;
    }

    /**
     * Aktualisiert einen bestehenden Bild-Prompt-Agenten (Prompt, Tokens, kaputter Default).
     *
     * @return true wenn sich etwas geändert hat
     */
    public static boolean migrateExisting(AgentConfig config) {
        if (config == null) {
            return false;
        }
        boolean changed = false;
        if (config.isUserDefined()) {
            config.setUserDefined(false);
            changed = true;
        }
        if (config.getAgentType() == null || config.getAgentType().isBlank()) {
            config.setAgentType("analysis");
            changed = true;
        }
        if (!config.isFreeform()) {
            config.setFreeform(true);
            changed = true;
        }
        if (!DEFAULT_SYSTEM.equals(config.getDefaultPrompt())) {
            config.setDefaultPrompt(DEFAULT_SYSTEM);
            changed = true;
        }
        if (config.getMaxTokens() > MAX_OUTPUT_TOKENS) {
            config.setMaxTokens(MAX_OUTPUT_TOKENS);
            changed = true;
        }
        String sys = config.getSystemPrompt();
        if (needsPromptRewrite(sys)) {
            config.setSystemPrompt(DEFAULT_SYSTEM);
            changed = true;
        }
        return changed;
    }

    static boolean needsPromptRewrite(String systemPrompt) {
        if (systemPrompt == null || systemPrompt.isBlank()) {
            return true;
        }
        if (DEFAULT_SYSTEM.equals(systemPrompt)) {
            return false;
        }
        return systemPrompt.contains("so viel Markdown")
                || systemPrompt.contains("KEINE_PROBLEME")
                || systemPrompt.contains("Plotlöchern")
                || systemPrompt.contains("silver hair")
                || systemPrompt.contains("vent's perspective")
                || systemPrompt.contains("space opera")
                || systemPrompt.contains("muted industrial")
                || systemPrompt.contains("Stilvorgaben")
                || systemPrompt.contains("aktuellen Szene")
                || systemPrompt.contains("welcher Moment, welche Figuren");
    }
}
