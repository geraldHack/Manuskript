package com.manuskript.agent;

import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Plothole-Agent („Der Detektiv") — findet Widersprüche und Logiklücken.
 *
 * Arbeitet mit einem strukturierten Prompt, der den Agenten zwingt,
 * Funde in einem parsebaren Format zurückzugeben.
 */
public class PlotholeAgent {
    private static final Logger logger = LoggerFactory.getLogger(PlotholeAgent.class);

    private static final int MAX_CONTEXT_CHARS = 400_000;
    private static final int DEFAULT_MAX_OUTPUT_TOKENS = 4096;
    private static final int MAX_OUTPUT_TOKENS_CAP = 16384;

    private static final String SYSTEM_PROMPT =
        "Du bist ein Analysemodul zur Erkennung von Plotlöchern und logischen Widersprüchen in Manuskripten.\n\n" +
        "AUSGABEREGELN:\n\n" +
        "Du erzeugst ausschließlich eine der folgenden zwei Antworten:\n\n" +
        "VARIANTE A:\n" +
        "KEINE_PROBLEME\n\n" +
        "VARIANTE B:\n" +
        "Eine oder mehrere Problemblöcke im EXAKTEN Format:\n\n" +
        "<PROBLEM> SCHWEREGRAD: [1-5] ZITAT: \"[EXAKTES ZITAT AUS DEM TEXT]\" PROBLEM: [KURZE BESCHREIBUNG] VORSCHLAG: [DIREKTER KONKRETER TEXTVORSCHLAG 1] VORSCHLAG: [DIREKTER KONKRETER TEXTVORSCHLAG 2] </PROBLEM>\n\n" +
        "WICHTIGE FORMATREGELN:\n\n" +
        "Verwende niemals Markdown.\n" +
        "Verwende niemals Aufzählungen.\n" +
        "Verwende niemals Nummerierungen.\n" +
        "Verwende niemals zusätzlichen Fließtext.\n" +
        "Verwende niemals Erklärungen vor oder nach der Ausgabe.\n" +
        "Gib ausschließlich gültige Problemblöcke oder KEINE_PROBLEME aus.\n" +
        "Jeder Problemblock MUSS mit <PROBLEM> beginnen und mit </PROBLEM> enden.\n" +
        "Zwischen zwei Problemblöcken steht genau eine Leerzeile.\n" +
        "Das Feld ZITAT muss exakt aus dem Manuskript übernommen werden.\n" +
        "Verändere niemals den Wortlaut eines Zitats.\n" +
        "Jedes Problem MUSS mindestens 2 VORSCHLÄGE enthalten. Die Vorschläge müssen DIREKTE KONKRETE TEXTVORSCHLÄGE sein, keine Beschreibungen wie \"Ergänze eine Begründung\" oder \"Präzisiere die Frage\". Gib stattdessen den exakten Text, der eingefügt werden soll.\n" +
        "Wenn keine relevanten Probleme existieren, gib ausschließlich KEINE_PROBLEME aus.\n" +
        "Antworte niemals mit Höflichkeitsfloskeln.\n" +
        "Antworte niemals mit Einleitungen.\n" +
        "Antworte niemals mit Zusammenfassungen.\n\n" +
        "ANALYSEREGELN:\n\n" +
        "Suche aktiv nach:\n\n" +
        "logischen Widersprüchen\n" +
        "Plotlöchern\n" +
        "unstimmigen Motivationen\n" +
        "unmöglichen Abläufen\n" +
        "verletzten Weltregeln\n" +
        "zeitlichen Inkonsistenzen\n" +
        "Figurenwissen ohne Grundlage\n" +
        "physikalischen Unmöglichkeiten innerhalb der Weltlogik\n\n" +
        "Ignoriere:\n\n" +
        "Stilfragen\n" +
        "Grammatik\n" +
        "reine Geschmacksfragen\n" +
        "absichtliche Mysterien ohne Widerspruch";

    private final AIBackend backend;
    private final AgentMemory memory;
    private String customSystemPrompt;

    public PlotholeAgent(AIBackend backend, AgentMemory memory) {
        this.backend = backend;
        this.memory = memory;
    }

    /**
     * Analysiert den aktuellen Kapiteltext auf Widersprüche.
     */
    public void setSystemPrompt(String prompt) {
        this.customSystemPrompt = prompt;
    }

    public CompletableFuture<PlotholeParseResult> analyze(String currentChapterText) {
        return analyze(currentChapterText, "", DEFAULT_MAX_OUTPUT_TOKENS);
    }

    public CompletableFuture<PlotholeParseResult> analyze(String currentChapterText, String allChapters) {
        return analyze(currentChapterText, allChapters, DEFAULT_MAX_OUTPUT_TOKENS);
    }

    public CompletableFuture<PlotholeParseResult> analyze(String currentChapterText, String allChapters, int maxOutputTokens) {
        // Gedächtnis vor der Analyse löschen, um alte Ergebnisse nicht zu beeinflussen
        memory.clear();

        String systemPrompt = customSystemPrompt != null ? customSystemPrompt : SYSTEM_PROMPT;

        StringBuilder userMessage = new StringBuilder();
        if (allChapters != null && !allChapters.isEmpty()) {
            String context = allChapters;
            if (context.length() > MAX_CONTEXT_CHARS) {
                int headLen = MAX_CONTEXT_CHARS * 40 / 100;
                int tailLen = MAX_CONTEXT_CHARS * 40 / 100;
                String head = context.substring(0, headLen);
                String tail = context.substring(context.length() - tailLen);
                context = head + "\n\n[... KONTEXT GEKÜRZT: " + (context.length() - MAX_CONTEXT_CHARS) + " Zeichen entfernt ...]\n\n" + tail;
                logger.warn("PlotholeAgent: Kontext von {} auf {} Zeichen gekürzt", allChapters.length(), context.length());
            }
            userMessage.append("=== ALLE KAPITEL (KONTEXT) ===\n");
            userMessage.append(context);
            userMessage.append("\n=== ALLE KAPITEL ENDE ===\n\n");
        }
        userMessage.append("=== MANUSKRIPT BEGINN ===\n");
        userMessage.append(currentChapterText);
        userMessage.append("\n=== MANUSKRIPT ENDE ===\n\n");
        userMessage.append("ANALYSE-SCOPE (zwingend einzuhalten):\n");
        userMessage.append("- Erstelle Problemblöcke AUSSCHLIESSLICH für Probleme, die im Abschnitt zwischen ")
                .append("\"=== MANUSKRIPT BEGINN ===\" und \"=== MANUSKRIPT ENDE ===\" stehen.\n");
        userMessage.append("- Der Inhalt zwischen \"=== ALLE KAPITEL (KONTEXT) ===\" und ")
                .append("\"=== ALLE KAPITEL ENDE ===\" dient NUR als Kontext. ")
                .append("Erstelle für diesen Bereich KEINE Problemblöcke, auch wenn dort Probleme enthalten sind.\n");
        userMessage.append("- Du darfst andere Kapitel im Feld PROBLEM referenzieren (z.B. ")
                .append("\"widerspricht Kapitel 7\"), aber das Feld ZITAT MUSS wörtlich aus dem MANUSKRIPT-Abschnitt stammen.\n");
        userMessage.append("- Findest du im MANUSKRIPT keine Probleme, antworte ausschließlich mit KEINE_PROBLEME.\n\n");
        userMessage.append("Analysiere jetzt das MANUSKRIPT gemäß den Systemregeln.");

        String messageStr = userMessage.toString();
        int manuscriptLen = currentChapterText != null ? currentChapterText.length() : 0;
        int contextLen = allChapters != null ? allChapters.length() : 0;
        int maxTokens = clampMaxOutputTokens(maxOutputTokens);
        logger.info(
                "Plothole-Anfrage: aktuelles Kapitel={} Zeichen, Kontext(alle Kapitel)={} Zeichen, "
                        + "gesamt≈{} Tokens, max_output_tokens={}, HTTP-Timeout={}s",
                manuscriptLen,
                contextLen,
                messageStr.length() / 4,
                maxTokens,
                OpenAIBackend.requestTimeoutSeconds());

        return backend.chat(systemPrompt, messageStr, maxTokens)
                .thenApply(PlotholeResponseParser::parse);
    }

    private static int clampMaxOutputTokens(int maxOutputTokens) {
        if (maxOutputTokens <= 0) {
            return DEFAULT_MAX_OUTPUT_TOKENS;
        }
        return Math.max(256, Math.min(MAX_OUTPUT_TOKENS_CAP, maxOutputTokens));
    }

    /** Nur für Tests. */
    PlotholeParseResult parseResponse(String response) {
        return PlotholeResponseParser.parse(response);
    }
}
