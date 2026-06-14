package com.manuskript.agent;

import java.util.List;
import java.util.ArrayList;

/**
 * Ein Fund (Widerspruch, Plothole) eines Agenten.
 */
public class Finding {
    private int severity;      // 1-5
    private String quote;      // exaktes Zitat aus dem Text
    private String problem;    // Beschreibung
    private String suggestion; // Korrekturvorschlag
    private List<String> suggestions; // Liste von Korrekturvorschlägen
    private int suggestionIndex = -1; // Index des ausgewählten Vorschlags
    /** Optional: feste Ersetzungs-Range (Überarbeiten-Agent). */
    private int replaceRangeStart = -1;
    private int replaceRangeEnd = -1;

    public Finding() {}

    public Finding(int severity, String quote, String problem, String suggestion) {
        this.severity = severity;
        this.quote = quote;
        this.problem = problem;
        this.suggestion = suggestion;
    }

    public int getSeverity() { return severity; }
    public void setSeverity(int severity) { this.severity = severity; }

    public String getQuote() { return quote; }
    public void setQuote(String quote) { this.quote = quote; }

    public String getProblem() { return problem; }
    public void setProblem(String problem) { this.problem = problem; }

    public String getSuggestion() { return suggestion; }
    public void setSuggestion(String suggestion) { this.suggestion = suggestion; }

    public List<String> getSuggestions() { return suggestions; }
    public void setSuggestions(List<String> suggestions) { this.suggestions = suggestions; }

    public int getSuggestionIndex() { return suggestionIndex; }
    public void setSuggestionIndex(int suggestionIndex) { this.suggestionIndex = suggestionIndex; }

    public int getReplaceRangeStart() { return replaceRangeStart; }
    public void setReplaceRangeStart(int replaceRangeStart) { this.replaceRangeStart = replaceRangeStart; }

    public int getReplaceRangeEnd() { return replaceRangeEnd; }
    public void setReplaceRangeEnd(int replaceRangeEnd) { this.replaceRangeEnd = replaceRangeEnd; }

    public boolean hasReplaceRange() {
        return replaceRangeStart >= 0 && replaceRangeEnd > replaceRangeStart;
    }

    public String getQuoteWithIndex() {
        return quote;
    }

    public String getSeverityStars() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < severity; i++) sb.append("\u2B50");
        return sb.toString();
    }

    @Override
    public String toString() {
        return getSeverityStars() + " " + problem;
    }
}
