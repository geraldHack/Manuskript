package com.manuskript;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import java.util.List;

public class Macro {
    private String name;
    private String description;
    private ObservableList<MacroStep> steps;
    
    public Macro(String name) {
        this.name = name;
        this.description = "";
        this.steps = FXCollections.observableArrayList();
    }
    
    public Macro(String name, String description) {
        this.name = name;
        this.description = description;
        this.steps = FXCollections.observableArrayList();
    }
    
    public Macro(String name, String description, List<MacroStep> steps) {
        this.name = name;
        this.description = description;
        this.steps = FXCollections.observableArrayList(steps);
    }
    
    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public ObservableList<MacroStep> getSteps() { return steps; }
    public void setSteps(ObservableList<MacroStep> steps) { this.steps = steps; }
    
    // Helper methods
    public void addStep(MacroStep step) {
        step.setStepNumber(steps.size() + 1);
        steps.add(step);
    }
    
    public void removeStep(MacroStep step) {
        steps.remove(step);
        updateStepNumbers();
    }
    
    public void moveStepUp(MacroStep step) {
        int index = steps.indexOf(step);
        if (index > 0) {
            steps.remove(index);
            steps.add(index - 1, step);
            updateStepNumbers();
        }
    }
    
    public void moveStepDown(MacroStep step) {
        int index = steps.indexOf(step);
        if (index < steps.size() - 1) {
            steps.remove(index);
            steps.add(index + 1, step);
            updateStepNumbers();
        }
    }
    
    private void updateStepNumbers() {
        for (int i = 0; i < steps.size(); i++) {
            steps.get(i).setStepNumber(i + 1);
        }
    }
    
    public void addStepFromCurrentSearch(String searchText, String replaceText, String description,
                                       boolean useRegex, boolean caseSensitive, boolean wholeWord) {
        MacroStep step = new MacroStep(steps.size() + 1, searchText, replaceText, description,
                                      useRegex, caseSensitive, wholeWord);
        steps.add(step);
    }
    
    @Override
    public String toString() {
        return name + " (" + steps.size() + " Schritte)";
    }
} 