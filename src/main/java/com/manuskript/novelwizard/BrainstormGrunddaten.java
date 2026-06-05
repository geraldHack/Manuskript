package com.manuskript.novelwizard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pflicht-Grunddaten der Brainstorm-Phase und Extraktion fuer das Kurzprofil in context.txt.
 */
public final class BrainstormGrunddaten {

    public static final String KURZ_HEADING = "### Grunddaten (Kurz)";

    private static final Pattern WORD_COUNT = Pattern.compile(
            "(?i)(?:~|ca\\.?\\s*|ungefähr\\s*)?(\\d{1,3}[\\.,]?\\d{3})\\s*(?:wörter|worte|words)");

    private static final Aspect[] ASPECTS = {
            new Aspect("Genre",
                    "(?i)(welches\\s+genre|hauptgenre|literarisches\\s+genre|genre\\s+.*\\broman\\b|"
                            + "genre-zuordnung|genre-fokus|genre soll)",
                    "(?i)(fantasy|thriller|krimi|romance|romantasy|science.?fiction|sci-fi|horror|"
                            + "literatur|coming.of.age|young adult|ya-|dystop|abenteuer)"),
            new Aspect("Subgenre",
                    "(?i)(subgenre|untergenre|genre-nische|welches\\s+subgenre)",
                    null),
            new Aspect("Prämisse",
                    "(?i)(prämisse|premisse|kernidee|grundidee|ausgangsidee|zentrale\\s+geschichte|"
                            + "narrative\\s+herzstück|herzstück.*erzählen)",
                    null),
            new Aspect("Umfang",
                    "(?i)(umfang.*roman|romanlänge|wortzahl|wortanzahl|wie\\s+viele\\s+wörter|"
                            + "wie\\s+lang\\s+soll|seitenumfang|pacing.*wörter|strukturier.*umfang)",
                    null),
            new Aspect("Stil",
                    "(?i)(erzählstil|schreibstil|erzählperspektive|erzählstruktur|ich-perspektive|"
                            + "zeitform|erzählweise)",
                    null),
            new Aspect("Ton",
                    "(?i)(ton des|grundstimmung|emotionaler\\s+ton|stimmung des|welcher\\s+ton)",
                    null),
            new Aspect("Themen",
                    "(?i)(zentrale\\s+themen|leitthemen|hauptthemen|welche\\s+themen)",
                    null),
    };

    private BrainstormGrunddaten() {
    }

    public static List<String> missingAspectLabels(List<NovelWizardSession.ChatEntry> chatHistory) {
        Map<String, String> found = extractAspectAnswers(chatHistory);
        List<String> missing = new ArrayList<>();
        for (Aspect aspect : ASPECTS) {
            String value = found.get(aspect.label);
            if (value == null || value.isBlank()) {
                missing.add(aspect.label);
            }
        }
        return missing;
    }

    public static String buildKurzprofil(List<NovelWizardSession.ChatEntry> chatHistory) {
        Map<String, String> found = extractAspectAnswers(chatHistory);
        StringBuilder sb = new StringBuilder();
        sb.append(KURZ_HEADING).append("\n\n");
        boolean any = false;
        for (Aspect aspect : ASPECTS) {
            String value = found.get(aspect.label);
            if (value != null && !value.isBlank()) {
                sb.append("- **").append(aspect.label).append(":** ").append(compact(value)).append("\n");
                any = true;
            }
        }
        return any ? sb.toString().strip() : "";
    }

    public static String buildMissingAspectsInstruction(List<NovelWizardSession.ChatEntry> chatHistory) {
        List<String> missing = missingAspectLabels(chatHistory);
        if (missing.isEmpty()) {
            return """
                    Alle Pflicht-Grunddaten (Genre, Subgenre, Prämisse, Umfang, Stil, Ton, Themen) sind geklärt.
                    Du darfst jetzt vertiefende Fragen zum Grundgerüst stellen oder Details schärfen.
                    """;
        }
        String next = missing.getFirst();
        StringBuilder sb = new StringBuilder();
        sb.append("PFLICHT-GRUNDDATEN (in dieser Reihenfolge, bevor du in Story-Details wie Handlung, Figuren oder Quests gehst):\n");
        for (String label : missing) {
            sb.append("- ").append(label);
            if (label.equals(next)) {
                sb.append("  <-- NAECHSTE FRAGE MUSS DIES EXPLIZIT ABFRAGEN");
            }
            sb.append("\n");
        }
        sb.append("\nDie naechste <QUESTION> muss das erste offene Feld (").append(next).append(") direkt benennen");
        sb.append(" (z. B. \"Welches Genre …\", \"Welches Subgenre …\", \"Wie viele Wörter soll der Roman ungefähr haben?\").\n");
        sb.append("Optionen muessen konkrete Genre-/Stil-/Umfang-Vorschläge sein, keine Plot- oder Figurenfragen.\n");
        return sb.toString();
    }

    private static Map<String, String> extractAspectAnswers(List<NovelWizardSession.ChatEntry> chatHistory) {
        Map<String, String> result = new LinkedHashMap<>();
        if (chatHistory == null) {
            return result;
        }
        String lastQuestion = "";
        for (NovelWizardSession.ChatEntry entry : chatHistory) {
            if (entry == null || entry.phase != NovelWizardPhase.BRAINSTORM) {
                continue;
            }
            if ("assistant".equals(entry.role)) {
                String q = questionText(entry);
                if (!q.isBlank()) {
                    lastQuestion = q;
                }
            } else if ("user".equals(entry.role) && entry.choice != null && !entry.choice.isBlank()) {
                String answer = entry.choice.trim();
                if (answer.startsWith(NovelWizardSession.CORRECTION_PREFIX)) {
                    continue;
                }
                assignAspect(result, lastQuestion, answer);
                lastQuestion = "";
            }
        }
        fillUmfangFromAnyAnswer(result, chatHistory);
        fillGenreFromAnyAnswer(result, chatHistory);
        return result;
    }

    private static void assignAspect(Map<String, String> result, String question, String answer) {
        if (answer.isBlank()) {
            return;
        }
        String q = question == null ? "" : question;
        for (Aspect aspect : ASPECTS) {
            if (result.containsKey(aspect.label)) {
                continue;
            }
            if (aspect.matchesQuestion(q)) {
                result.put(aspect.label, answer);
                return;
            }
        }
    }

    private static void fillUmfangFromAnyAnswer(Map<String, String> result, List<NovelWizardSession.ChatEntry> chatHistory) {
        if (result.containsKey("Umfang") && !result.get("Umfang").isBlank()) {
            return;
        }
        for (NovelWizardSession.ChatEntry entry : chatHistory) {
            if (entry == null || entry.phase != NovelWizardPhase.BRAINSTORM || !"user".equals(entry.role)) {
                continue;
            }
            String choice = entry.choice == null ? "" : entry.choice;
            Matcher matcher = WORD_COUNT.matcher(choice);
            if (matcher.find()) {
                String count = matcher.group(1).trim();
                result.put("Umfang", "~" + count + " Wörter");
                return;
            }
        }
    }

    private static void fillGenreFromAnyAnswer(Map<String, String> result, List<NovelWizardSession.ChatEntry> chatHistory) {
        if (result.containsKey("Genre") && !result.get("Genre").isBlank()) {
            return;
        }
        for (NovelWizardSession.ChatEntry entry : chatHistory) {
            if (entry == null || entry.phase != NovelWizardPhase.BRAINSTORM || !"user".equals(entry.role)) {
                continue;
            }
            String choice = entry.choice == null ? "" : entry.choice;
            if (ASPECTS[0].matchesAnswer(choice) || choice.toLowerCase().contains("fantasy-fokus")
                    || choice.toLowerCase().contains("romantisch-fantastisch")) {
                result.put("Genre", compact(choice));
                return;
            }
        }
    }

    private static String questionText(NovelWizardSession.ChatEntry entry) {
        if (entry.parsed != null && entry.parsed.getQuestion() != null && !entry.parsed.getQuestion().isBlank()) {
            return entry.parsed.getQuestion();
        }
        return entry.raw == null ? "" : entry.raw;
    }

    private static String compact(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }

    private record Aspect(String label, String questionPattern, String answerPattern) {
        boolean matchesQuestion(String question) {
            if (question == null || question.isBlank()) {
                return false;
            }
            if ("Genre".equals(label) && question.toLowerCase().contains("subgenre")) {
                return false;
            }
            if ("Genre".equals(label) && question.toLowerCase().contains("finales genre")) {
                return true;
            }
            return Pattern.compile(questionPattern).matcher(question).find();
        }

        boolean matchesAnswer(String answer) {
            if (answerPattern == null || answer == null) {
                return false;
            }
            return Pattern.compile(answerPattern).matcher(answer).find();
        }
    }
}
