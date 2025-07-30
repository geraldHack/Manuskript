package com.manuskript;

import javafx.beans.property.*;

public class MacroStep {
    private final IntegerProperty stepNumber;
    private final StringProperty searchText;
    private final StringProperty replaceText;
    private final StringProperty description;
    private final BooleanProperty useRegex;
    private final BooleanProperty caseSensitive;
    private final BooleanProperty wholeWord;
    private final BooleanProperty enabled;
    
    // Ersetzungsstatistiken
    private final IntegerProperty replacementCount;
    private final StringProperty executionStatus;
    
    public MacroStep(int stepNumber, String searchText, String replaceText, String description,
                    boolean useRegex, boolean caseSensitive, boolean wholeWord) {
        this.stepNumber = new SimpleIntegerProperty(stepNumber);
        this.searchText = new SimpleStringProperty(searchText);
        this.replaceText = new SimpleStringProperty(replaceText);
        this.description = new SimpleStringProperty(description);
        this.useRegex = new SimpleBooleanProperty(useRegex);
        this.caseSensitive = new SimpleBooleanProperty(caseSensitive);
        this.wholeWord = new SimpleBooleanProperty(wholeWord);
        this.enabled = new SimpleBooleanProperty(true); // Standardmäßig aktiviert
        this.replacementCount = new SimpleIntegerProperty(0);
        this.executionStatus = new SimpleStringProperty("Nicht ausgeführt");
    }
    
    // Default constructor
    public MacroStep() {
        this(0, "", "", "", false, false, false);
    }
    
    // Properties
    public IntegerProperty stepNumberProperty() { return stepNumber; }
    public StringProperty searchTextProperty() { return searchText; }
    public StringProperty replaceTextProperty() { return replaceText; }
    public StringProperty descriptionProperty() { return description; }
    public BooleanProperty useRegexProperty() { return useRegex; }
    public BooleanProperty caseSensitiveProperty() { return caseSensitive; }
    public BooleanProperty wholeWordProperty() { return wholeWord; }
    public BooleanProperty enabledProperty() { return enabled; }
    public IntegerProperty replacementCountProperty() { return replacementCount; }
    public StringProperty executionStatusProperty() { return executionStatus; }
    
    // Getters and Setters
    public int getStepNumber() { return stepNumber.get(); }
    public void setStepNumber(int stepNumber) { this.stepNumber.set(stepNumber); }
    
    public String getSearchText() { return searchText.get(); }
    public void setSearchText(String searchText) { this.searchText.set(searchText); }
    
    public String getReplaceText() { return replaceText.get(); }
    public void setReplaceText(String replaceText) { this.replaceText.set(replaceText); }
    
    public String getDescription() { return description.get(); }
    public void setDescription(String description) { this.description.set(description); }
    
    public boolean isUseRegex() { return useRegex.get(); }
    public void setUseRegex(boolean useRegex) { this.useRegex.set(useRegex); }
    
    public boolean isCaseSensitive() { return caseSensitive.get(); }
    public void setCaseSensitive(boolean caseSensitive) { this.caseSensitive.set(caseSensitive); }
    
    public boolean isWholeWord() { return wholeWord.get(); }
    public void setWholeWord(boolean wholeWord) { this.wholeWord.set(wholeWord); }
    
    public boolean isEnabled() { return enabled.get(); }
    public void setEnabled(boolean enabled) { this.enabled.set(enabled); }
    
    public int getReplacementCount() { return replacementCount.get(); }
    public void setReplacementCount(int count) { this.replacementCount.set(count); }
    
    public String getExecutionStatus() { return executionStatus.get(); }
    public void setExecutionStatus(String status) { this.executionStatus.set(status); }
    
    // Methoden für Ersetzungsstatistiken
    public void addReplacements(int count) {
        this.replacementCount.set(this.replacementCount.get() + count);
    }
    
    public void resetReplacementStats() {
        this.replacementCount.set(0);
        this.executionStatus.set("Nicht ausgeführt");
    }
    
    public void setRunning() {
        this.executionStatus.set("Läuft...");
    }
    
    public void setCompleted() {
        this.executionStatus.set("Fertig (" + this.replacementCount.get() + " Ersetzungen)");
    }
    
    public void setError(String error) {
        this.executionStatus.set("Fehler: " + error);
    }
    

    
    // Helper method to get options as string
    public String getOptionsString() {
        StringBuilder options = new StringBuilder();
        if (isUseRegex()) options.append("Regex ");
        if (isCaseSensitive()) options.append("Case ");
        if (isWholeWord()) options.append("Word ");
        return options.toString().trim();
    }
    
    @Override
    public String toString() {
        String desc = getDescription() != null && !getDescription().isEmpty() ? 
                     " (" + getDescription() + ")" : "";
        return "Schritt " + getStepNumber() + ": " + getSearchText() + " → " + getReplaceText() + desc;
    }
} 