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
        this.options = options == null ? new ArrayList<>() : new ArrayList<>(options);
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
