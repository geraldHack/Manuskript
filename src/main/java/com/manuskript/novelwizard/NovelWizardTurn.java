package com.manuskript.novelwizard;

import java.util.ArrayList;
import java.util.List;

public class NovelWizardTurn {
    private String question = "";
    private List<String> options = new ArrayList<>();
    private String hint = "";
    private String summary = "";
    private String content = "";

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question == null ? "" : question.trim();
    }

    public List<String> getOptions() {
        return options;
    }

    public void setOptions(List<String> options) {
        this.options = new ArrayList<>();
        if (options == null) {
            return;
        }
        for (String option : options) {
            if (!isRedundantFreeTextOption(option)) {
                this.options.add(option);
            }
        }
    }

    /**
     * Freitext-Optionen entfallen – dafür gibt es das Eingabefeld „Eigene Antwort“ unter den Buttons.
     */
    public static boolean isRedundantFreeTextOption(String option) {
        if (option == null || option.isBlank()) {
            return true;
        }
        String lower = option.toLowerCase().trim();
        return lower.contains("freitext")
                || lower.contains("eigene antwort")
                || lower.contains("eigenen text")
                || lower.contains("selbst formulieren")
                || lower.contains("selber formulieren")
                || lower.contains("eigene formulierung")
                || lower.contains("andere option")
                || lower.startsWith("sonstig")
                || lower.contains(" etwas anderes")
                || lower.contains("was anderes");
    }

    public String getHint() {
        return hint;
    }

    public void setHint(String hint) {
        this.hint = hint == null ? "" : hint.trim();
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary == null ? "" : summary.trim();
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content == null ? "" : content.trim();
    }

    public boolean hasOptions() {
        return options != null && !options.isEmpty();
    }
}
