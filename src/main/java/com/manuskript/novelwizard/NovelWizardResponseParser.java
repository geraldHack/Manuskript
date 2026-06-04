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
        NovelWizardTurn turn = new NovelWizardTurn();
        String response = raw == null ? "" : raw.trim();
        turn.setQuestion(extract(QUESTION, response));
        turn.setHint(extract(HINT, response));
        turn.setSummary(extract(SUMMARY, response));
        turn.setContent(extract(CONTENT, response));
        turn.setOptions(extractOptions(response));

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
