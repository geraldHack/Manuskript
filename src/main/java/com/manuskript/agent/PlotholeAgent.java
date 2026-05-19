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

    public CompletableFuture<List<Finding>> analyze(String currentChapterText) {
        return analyze(currentChapterText, "");
    }

    public CompletableFuture<List<Finding>> analyze(String currentChapterText, String allChapters) {
        // Gedächtnis vor der Analyse löschen, um alte Ergebnisse nicht zu beeinflussen
        memory.clear();

        String systemPrompt = customSystemPrompt != null ? customSystemPrompt : SYSTEM_PROMPT;

        StringBuilder userMessage = new StringBuilder();
        if (allChapters != null && !allChapters.isEmpty()) {
            userMessage.append("=== ALLE KAPITEL (KONTEXT) ===\n");
            userMessage.append(allChapters);
            userMessage.append("\n=== ALLE KAPITEL ENDE ===\n\n");
        }
        userMessage.append("=== MANUSKRIPT BEGINN ===\n");
        userMessage.append(currentChapterText);
        userMessage.append("\n=== MANUSKRIPT ENDE ===\n");
        userMessage.append("Analysiere das Manuskript gemäß den Systemregeln.");

        return backend.chat(systemPrompt, userMessage.toString(), 8192)
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
        logger.info("Agent-Rohantwort ({} Zeichen):\n{}", trimmed.length(), trimmed);

        // Prüfe auf "keine Probleme" (nur wenn die Antwort DAMIT beginnt)
        if (trimmed.startsWith("KEINE_PROBLEME")) {
            logger.info("Agent meldet: KEINE_PROBLEME");
            return findings;
        }

        // Normalisiere Zeilenumbrüche
        String normalized = trimmed.replace("\r\n", "\n").replace("\r", "\n");

        // Parse <PROBLEM>...</PROBLEM> Blöcke
        Pattern blockPattern = Pattern.compile(
            "<PROBLEM>\\s*SCHWEREGRAD:\\s*(\\d+)\\s*ZITAT:\\s*\"([^\"]*)\"\\s*PROBLEM:\\s*(.*?)</PROBLEM>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );

        Matcher m = blockPattern.matcher(normalized);
        while (m.find()) {
            try {
                int severity = Integer.parseInt(m.group(1));
                String quote = m.group(2).trim();
                String problemAndSuggestions = m.group(3).trim();

                logger.info("Geparster Fund: severity={}, problemAndSuggestions={}", severity, problemAndSuggestions);

                // Trenne Problem und Vorschläge
                String problem;
                String suggestionsText;
                int suggestionIndex = problemAndSuggestions.indexOf("VORSCHLÄGE:");
                if (suggestionIndex < 0) {
                    suggestionIndex = problemAndSuggestions.indexOf("VORSCHLAG:");
                }
                if (suggestionIndex >= 0) {
                    problem = problemAndSuggestions.substring(0, suggestionIndex).trim();
                    suggestionsText = problemAndSuggestions.substring(suggestionIndex).trim();
                } else {
                    problem = problemAndSuggestions;
                    suggestionsText = "";
                }

                logger.info("Problem: {}, SuggestionsText: {}", problem, suggestionsText);

                if (!problem.isEmpty()) {
                    Finding finding = new Finding(severity, quote, problem, "");
                    // Extrahiere alle Vorschläge aus dem suggestionsText
                    List<String> suggestions = new ArrayList<>();

                    // Format 1: VORSCHLÄGE: "Vorschlag 1", "Vorschlag 2", "Vorschlag 3"
                    // Entferne "VORSCHLÄGE:" oder "VORSCHLAG:" Prefix und INDEX:... Suffix
                    String cleanText = suggestionsText
                        .replaceFirst("(?i)VORSCHLÄGE:\\s*", "")
                        .replaceFirst("(?i)VORSCHLAG:\\s*", "");
                    // Entferne INDEX:... am Ende
                    int indexPos = cleanText.indexOf("INDEX:");
                    if (indexPos >= 0) {
                        cleanText = cleanText.substring(0, indexPos).trim();
                    }

                    // Versuche, Vorschläge in Anführungszeichen zu extrahieren
                    Pattern quotedPattern = Pattern.compile("\"([^\"]+)\"");
                    Matcher qm = quotedPattern.matcher(cleanText);
                    while (qm.find()) {
                        String suggestion = qm.group(1).trim();
                        if (!suggestion.isEmpty()) {
                            suggestions.add(suggestion);
                            logger.info("Vorschlag (Anführungszeichen) extrahiert: {}", suggestion);
                        }
                    }

                    // Fallback: Wenn keine Anführungszeichen gefunden, suche nach "VORSCHLAG:" Markern
                    if (suggestions.isEmpty()) {
                        Pattern suggestionPattern = Pattern.compile("VORSCHLAG:\\s*([^<]+)", Pattern.CASE_INSENSITIVE);
                        Matcher sm = suggestionPattern.matcher(suggestionsText);
                        while (sm.find()) {
                            String suggestion = sm.group(1).trim();
                            if (!suggestion.isEmpty()) {
                                suggestions.add(suggestion);
                                logger.info("Vorschlag (VORSCHLAG-Marker) extrahiert: {}", suggestion);
                            }
                        }
                    }

                    // Letzter Fallback: Splitting nach Komma
                    if (suggestions.isEmpty() && !cleanText.isEmpty()) {
                        String[] parts = cleanText.split(",");
                        for (String part : parts) {
                            String suggestion = part.trim().replaceAll("^\"|\"$", "").trim();
                            if (!suggestion.isEmpty()) {
                                suggestions.add(suggestion);
                                logger.info("Vorschlag (Komma-Fallback) extrahiert: {}", suggestion);
                            }
                        }
                    }

                    finding.setSuggestions(suggestions);
                    finding.setSuggestionIndex(suggestions.isEmpty() ? -1 : 0);
                    findings.add(finding);
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
                        // Speichere den Vorschlag auch in der suggestions-Liste
                        List<String> suggestions = new ArrayList<>();
                        if (current.getSuggestion() != null && !current.getSuggestion().isEmpty()) {
                            suggestions.add(current.getSuggestion());
                            current.setSuggestions(suggestions);
                            current.setSuggestionIndex(0);
                        }
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
                // Speichere den Vorschlag auch in der suggestions-Liste
                List<String> suggestions = new ArrayList<>();
                if (current.getSuggestion() != null && !current.getSuggestion().isEmpty()) {
                    suggestions.add(current.getSuggestion());
                    current.setSuggestions(suggestions);
                    current.setSuggestionIndex(0);
                }
                findings.add(current);
            }
        }

        return findings;
    }
}
