package com.manuskript;

import java.util.ArrayList;
import java.util.List;

/**
 * Ein Lektorat-Treffer: Originaltext mit Offset/Länge und 2–3 Änderungsvorschläge inkl. Begründung und Gewichtung.
 */
public class LektoratMatch {
    private int offset;
    private int length;
    private String original;
    private List<String> suggestions;
    private String reason;
    private int weight;

    public LektoratMatch() {
        this.suggestions = new ArrayList<>();
    }

    public LektoratMatch(int offset, int length, String original, List<String> suggestions, String reason, int weight) {
        this.offset = offset;
        this.length = length;
        this.original = original != null ? original : "";
        this.suggestions = suggestions != null ? new ArrayList<>(suggestions) : new ArrayList<>();
        this.reason = reason != null ? reason : "";
        this.weight = Math.max(1, Math.min(5, weight));
    }

    public int getOffset() { return offset; }
    public void setOffset(int offset) { this.offset = offset; }
    public int getLength() { return length; }
    public void setLength(int length) { this.length = length; }
    public String getOriginal() { return original; }
    public void setOriginal(String original) { this.original = original != null ? original : ""; }
    public List<String> getSuggestions() { return suggestions; }
    public void setSuggestions(List<String> suggestions) { this.suggestions = suggestions != null ? new ArrayList<>(suggestions) : new ArrayList<>(); }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason != null ? reason : ""; }
    public int getWeight() { return weight; }
    public void setWeight(int weight) { this.weight = Math.max(1, Math.min(5, weight)); }
}
