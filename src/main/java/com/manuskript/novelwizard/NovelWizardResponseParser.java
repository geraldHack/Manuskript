package com.manuskript.novelwizard;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NovelWizardResponseParser {
    private static final Pattern QUESTION = Pattern.compile("<QUESTION>(.*?)</QUESTION>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern OPTION = Pattern.compile("<OPTION(?:\\s+[^>]*)?>(.*?)</OPTION>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern HINT = Pattern.compile("<HINT(?:\\s+[^>]*)?>(.*?)</HINT>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern SUMMARY = Pattern.compile("<SUMMARY(?:\\s+[^>]*)?>(.*?)</SUMMARY>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTENT = Pattern.compile("<CONTENT>(.*?)</CONTENT>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private NovelWizardResponseParser() {
    }

    public static NovelWizardTurn parse(String raw) {
        return parse(raw, false);
    }

    public static NovelWizardTurn parse(String raw, boolean contentPhase) {
        NovelWizardTurn turn = new NovelWizardTurn();
        String response = raw == null ? "" : raw.trim();
        turn.setQuestion(extract(QUESTION, response));
        turn.setHint(extract(HINT, response));
        turn.setSummary(extract(SUMMARY, response));
        turn.setContent(extract(CONTENT, response));
        turn.setOptions(extractOptions(response));

        if (contentPhase) {
            applyContentPhaseRules(turn, response);
            return turn;
        }

        if (turn.getQuestion().isBlank() && turn.getContent().isBlank()) {
            turn.setQuestion(firstNonEmptyLine(response));
        }
        if (!turn.hasOptions() && turn.getContent().isBlank()) {
            turn.setOptions(extractNumberedOptions(response));
        }
        if (turn.getQuestion().isBlank() && !turn.getContent().isBlank()) {
            turn.setQuestion("Bitte prüfe den Vorschlag und entscheide, wie es weitergehen soll.");
        }
        return turn;
    }

    /** Schreibphase: Entwurf retten, Interview-Format verwerfen. */
    private static void applyContentPhaseRules(NovelWizardTurn turn, String response) {
        if (turn.getContent().isBlank()) {
            turn.setContent(extractContentFallback(response));
        }
        if (!turn.getContent().isBlank()) {
            turn.setQuestion("");
            turn.setOptions(List.of());
            if (turn.getHint().isBlank() && !turn.getSummary().isBlank()) {
                turn.setHint(turn.getSummary());
            }
            return;
        }
        turn.setQuestion("");
        turn.setOptions(List.of());
        if (turn.getHint().isBlank()) {
            turn.setHint("Die KI hat keine Synopsis bzw. keinen Entwurf geliefert (nur Rueckfragen). "
                    + "Bitte „Entwurf erneut anfordern“ wählen.");
        }
    }

    private static String extractContentFallback(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String stripped = raw
                .replaceAll("(?is)<QUESTION>.*?</QUESTION>", "")
                .replaceAll("(?is)<OPTIONS>.*?</OPTIONS>", "")
                .replaceAll("(?is)<OPTION[^>]*>.*?</OPTION>", "")
                .replaceAll("(?is)<HINT[^>]*>.*?</HINT>", "")
                .replaceAll("(?is)<SUMMARY[^>]*>.*?</SUMMARY>", "")
                .replaceAll("(?is)</?CONTENT>", "")
                .trim();
        stripped = cleanup(stripped);
        if (stripped.length() >= 120 && looksLikeNarrativeDraft(stripped)) {
            return stripped;
        }
        String contentOnly = extract(CONTENT, raw);
        return contentOnly.isBlank() ? "" : contentOnly;
    }

    private static boolean looksLikeNarrativeDraft(String text) {
        if (text.contains("<OPTION") || text.contains("<QUESTION")) {
            return false;
        }
        long words = text.split("\\s+").length;
        return words >= 40;
    }

    private static String extract(Pattern pattern, String raw) {
        Matcher matcher = pattern.matcher(raw);
        return matcher.find() ? cleanup(matcher.group(1)) : "";
    }

    private static List<String> extractOptions(String raw) {
        List<String> options = new ArrayList<>();
        Matcher matcher = OPTION.matcher(raw);
        while (matcher.find()) {
            String option = cleanup(matcher.group(1));
            if (!option.isBlank()) {
                options.add(option);
            }
        }
        return options;
    }

    private static List<String> extractNumberedOptions(String raw) {
        List<String> options = new ArrayList<>();
        Pattern numbered = Pattern.compile("(?m)^\\s*(?:\\d+[\\.)]|[-*])\\s+(.+)$");
        Matcher matcher = numbered.matcher(raw);
        while (matcher.find() && options.size() < 8) {
            String option = cleanup(matcher.group(1));
            if (!option.isBlank()) {
                options.add(option);
            }
        }
        return options;
    }

    private static String firstNonEmptyLine(String raw) {
        for (String line : raw.split("\\R")) {
            if (!line.trim().isEmpty()) {
                return cleanup(line);
            }
        }
        return "";
    }

    private static String cleanup(String text) {
        return text == null ? "" : text.replaceAll("<[^>]+>", "").trim();
    }
}
