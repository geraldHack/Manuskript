package com.manuskript.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static final String SYSTEM_PROMPT =
        "Du bist ein kritischer Redaktionsassistent für Manuskripte. Deine Aufgabe ist es, logische Widersprüche, Ungereimtheiten und Plotlöcher im Text zu finden.\n\n" +
        "WICHTIGE REGELN:\n" +
        "1. Du darfst KEINE Markdown-Formatierung verwenden (kein **, kein *, kein #, keine Nummerierung)\n" +
        "2. Gib das Ergebnis IMMER im folgenden strikten Format aus:\n\n" +
        "---\n" +
        "SCHWEREGRAD: 1-5\n" +
        "ZITAT: \"Der exakte Text aus dem Manuskript\"\n" +
        "PROBLEM: Kurze Beschreibung des Widerspruchs\n" +
        "VORSCHLAG: Wie man es beheben könnte\n\n" +
        "---\n" +
        "SCHWEREGRAD: 1-5\n" +
        "ZITAT: \"Der exakte Text aus dem Manuskript\"\n" +
        "PROBLEM: Kurze Beschreibung des Widerspruchs\n" +
        "VORSCHLAG: Wie man es beheben könnte\n\n" +
        "3. Wenn du KEINE Probleme findest, gib EXAKT aus: KEINE_PROBLEME\n" +
        "4. Suche aktiv nach Widersprüchen - sei kritisch!\n" +
        "5. Das Zitat muss exakt aus dem Text übernommen werden.\n" +
        "6. Verwende KEINE Aufzählungszeichen, keine Nummerierung, nur das Format oben.";

    private final AIBackend backend;
    private final AgentMemory memory;

    public PlotholeAgent(AIBackend backend, AgentMemory memory) {
        this.backend = backend;
        this.memory = memory;
    }

    /**
     * Analysiert den aktuellen Kapiteltext auf Widersprüche.
     */
    public CompletableFuture<List<Finding>> analyze(String currentChapterText) {
        // Gedächtnis vor der Analyse löschen, um alte Ergebnisse nicht zu beeinflussen
        memory.clear();

        StringBuilder userMessage = new StringBuilder();
        userMessage.append("=== AKTUELLER TEXT ===\n");
        userMessage.append(currentChapterText);
        userMessage.append("\n\nAnalysiere den Text auf Widersprüche.");

        return backend.chat(SYSTEM_PROMPT, userMessage.toString(), 2048)
                .thenApply(this::parseResponse);
    }

    /**
     * Parst die strukturierte Antwort des Agenten in Finding-Objekte.
     */
    List<Finding> parseResponse(String response) {
        List<Finding> findings = new ArrayList<>();

        if (response == null || response.trim().isEmpty()) {
            logger.warn("Leere Antwort vom Agenten");
            return findings;
        }

        String trimmed = response.trim();
        logger.info("Agent-Rohantwort ({} Zeichen): {}", trimmed.length(),
                trimmed.length() > 500 ? trimmed.substring(0, 500) + "..." : trimmed);

        // Prüfe auf "keine Probleme" (nur wenn die Antwort DAMIT beginnt)
        if (trimmed.startsWith("KEINE_PROBLEME")) {
            logger.info("Agent meldet: KEINE_PROBLEME");
            return findings;
        }

        // Normalisiere Zeilenumbrüche
        String normalized = trimmed.replace("\r\n", "\n").replace("\r", "\n");

        // Parse --- Blöcke (flexiblere Newlines)
        Pattern blockPattern = Pattern.compile(
            "---\\s*\\n" +
            "SCHWEREGRAD:\\s*(\\d+)\\s*\\n" +
            "ZITAT:\\s*\"([^\"]*)\"\\s*\\n" +
            "PROBLEM:\\s*([^\\n]*)\\s*\\n" +
            "VORSCHLAG:\\s*([^\\n]*)",
            Pattern.CASE_INSENSITIVE
        );

        Matcher m = blockPattern.matcher(normalized);
        while (m.find()) {
            try {
                int severity = Integer.parseInt(m.group(1));
                String quote = m.group(2).trim();
                String problem = m.group(3).trim();
                String suggestion = m.group(4).trim();

                logger.info("Geparster Fund: severity={}, problem={}", severity, problem);

                if (!problem.isEmpty()) {
                    findings.add(new Finding(severity, quote, problem, suggestion));
                }
            } catch (NumberFormatException e) {
                logger.warn("Konnte Schweregrad nicht parsen: {}", m.group(1));
            }
        }

        // Fallback: Wenn das strenge Format nicht eingehalten wurde,
        // versuche ein lockereres Parsing
        if (findings.isEmpty() && !trimmed.startsWith("KEINE_PROBLEME")) {
            logger.info("Striktes Parsing ergab keine Funde, versuche lockeres Parsing");
            findings.addAll(parseLooseFormat(normalized));
        }

        logger.info("Parse-Ergebnis: {} Findings", findings.size());
        return findings;
    }

    /**
     * Fallback-Parsing für weniger strikt formatierte Antworten (inkl. Markdown).
     */
    private List<Finding> parseLooseFormat(String response) {
        List<Finding> findings = new ArrayList<>();

        // Markdown-Formatierungen bereinigen
        String cleaned = response.replaceAll("\\*\\*\\*", "")  // ***
                                    .replaceAll("\\*\\*", "")   // **
                                    .replaceAll("\\*", "")     // *
                                    .replaceAll("#", "");       // #

        // Suche nach nummerierten Einträgen
        String[] lines = cleaned.split("\\n");
        Finding current = null;
        StringBuilder problemBuilder = new StringBuilder();

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            // Erkenne neuen Fund (beginnt mit Zahl, Stern, oder "Schweregrad"/"Widerspruch")
            if (line.matches("^\\d+[.)]\\s.*") ||
                line.toLowerCase().contains("schweregrad") ||
                line.toLowerCase().contains("widerspruch") ||
                line.toLowerCase().contains("problem")) {

                // Vorherigen Fund speichern
                if (current != null) {
                    if (problemBuilder.length() > 0) {
                        current.setProblem(problemBuilder.toString().trim());
                    }
                    if (current.getProblem() != null && !current.getProblem().isEmpty()) {
                        findings.add(current);
                    }
                }

                current = new Finding();
                current.setSeverity(3); // Default
                problemBuilder = new StringBuilder();
                // Entferne Präfix wie "1." oder "Widerspruch:"
                String problemText = line.replaceFirst("^\\d+[.)]\\s*", "")
                                        .replaceFirst("(?i)widerspruch:\\s*", "")
                                        .replaceFirst("(?i)problem:\\s*", "")
                                        .replaceFirst("(?i)widerspruch:\\s*", "")
                                        .replaceFirst("(?i)ungereimtheit:\\s*", "")
                                        .trim();
                problemBuilder.append(problemText);

                // Prüfe, ob Vorschlag in derselben Zeile steht
                if (line.toLowerCase().contains("vorschlag:") || line.toLowerCase().contains("suggestion:")) {
                    String[] parts = line.split("(?i)vorschlag:|suggestion:", 2);
                    if (parts.length > 1) {
                        current.setSuggestion(parts[1].trim());
                    }
                }
            } else if (current != null) {
                if (line.toLowerCase().startsWith("zitat:")) {
                    current.setQuote(line.substring(6).trim().replaceAll("^\"|\"$", ""));
                } else if (line.toLowerCase().startsWith("vorschlag:")) {
                    current.setSuggestion(line.substring(10).trim());
                } else if (line.toLowerCase().startsWith("suggestion:")) {
                    current.setSuggestion(line.substring(11).trim());
                } else if (line.contains("\"")) {
                    // Extrahiere Zitat in Anführungszeichen
                    Matcher qm = Pattern.compile("\"([^\"]+)\"").matcher(line);
                    if (qm.find()) {
                        current.setQuote(qm.group(1));
                    }
                } else {
                    // Fortsetzung des Problem-Textes
                    if (problemBuilder.length() > 0) {
                        problemBuilder.append(" ");
                    }
                    problemBuilder.append(line);
                }
            }
        }

        // Letzten Fund speichern
        if (current != null) {
            if (problemBuilder.length() > 0) {
                current.setProblem(problemBuilder.toString().trim());
            }
            if (current.getProblem() != null && !current.getProblem().isEmpty()) {
                findings.add(current);
            }
        }

        return findings;
    }
}
