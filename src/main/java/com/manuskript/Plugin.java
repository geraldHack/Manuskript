package com.manuskript;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

/**
 * Repräsentiert ein Plugin für den KI-Assistenten
 */
public class Plugin {
    private String name;
    private String description;
    private String category;
    private String prompt;
    private double temperature;
    private int maxTokens;
    private boolean enabled;
    private Map<String, String> variables;
    private List<PluginVariable> variableDefinitions;
    
    public Plugin() {
        this.variables = new HashMap<>();
        this.variableDefinitions = new ArrayList<>();
    }
    
    public Plugin(String name, String description, String category, String prompt, 
                  double temperature, int maxTokens, boolean enabled) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.prompt = prompt;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.enabled = enabled;
        this.variables = new HashMap<>();
        this.variableDefinitions = new ArrayList<>();
    }
    
    /**
     * Ersetzt Variablen im Prompt mit den bereitgestellten Werten
     */
    public String getProcessedPrompt(Map<String, String> variableValues) {
        String processedPrompt = prompt;
        
        // Neue Syntax: {Name:Default:Type} - ersetze alle Variablen
        if (variableValues != null) {
            int start = 0;
            while ((start = processedPrompt.indexOf("{", start)) != -1) {
                int end = processedPrompt.indexOf("}", start);
                if (end != -1) {
                    String varDefinition = processedPrompt.substring(start + 1, end);
                    String varName = varDefinition;
                    
                    // Extrahiere den Namen aus {Name:Default:Type}
                    if (varDefinition.contains(":")) {
                        String[] parts = varDefinition.split(":", 3);
                        varName = parts[0].trim();
                    }
                    
                    // Ersetze mit dem Wert aus der Map
                    String value = variableValues.get(varName);
                    if (value != null) {
                        String fullPlaceholder = "{" + varDefinition + "}";
                        processedPrompt = processedPrompt.replace(fullPlaceholder, value);
                    }
                    
                    start = end + 1;
                } else {
                    break;
                }
            }
        }
        
        return processedPrompt;
    }
    
    /**
     * Extrahiert alle Variablen-Platzhalter aus dem Prompt
     */
    public java.util.Set<String> getVariables() {
        java.util.Set<String> vars = new java.util.HashSet<>();
        if (prompt != null) {
            int start = 0;
            while ((start = prompt.indexOf("{", start)) != -1) {
                int end = prompt.indexOf("}", start);
                if (end != -1) {
                    String var = prompt.substring(start + 1, end);
                    // Neue Syntax: {Name:Default:Typ} - extrahiere nur den Namen
                    if (var.contains(":")) {
                        String[] parts = var.split(":", 3);
                        if (parts.length >= 1) {
                            vars.add(parts[0].trim());
                        }
                    } else {
                        vars.add(var);
                    }
                    start = end + 1;
                } else {
                    break;
                }
            }
        }
        return vars;
    }
    
    /**
     * Gibt die Plugin-Variablen zurück (aus JSON oder aus Prompt extrahiert)
     */
    public List<PluginVariable> getVariableDefinitions() {
        // Falls explizite Variablen-Definitionen vorhanden sind (aus JSON), verwende diese
        if (variableDefinitions != null && !variableDefinitions.isEmpty()) {
            return new ArrayList<>(variableDefinitions);
        }
        
        // Fallback: Erstelle automatisch PluginVariable-Objekte basierend auf den Variablen im Prompt
        List<PluginVariable> definitions = new ArrayList<>();
        if (prompt != null) {
            int start = 0;
            while ((start = prompt.indexOf("{", start)) != -1) {
                int end = prompt.indexOf("}", start);
                if (end != -1) {
                    String varDefinition = prompt.substring(start + 1, end);
                    PluginVariable variable = parseVariableDefinition(varDefinition);
                    if (variable != null) {
                        definitions.add(variable);
                    }
                    start = end + 1;
                } else {
                    break;
                }
            }
        }
        return definitions;
    }
    
    /**
     * Parst eine Variablen-Definition im Format {Name:Default:Typ}
     */
    private PluginVariable parseVariableDefinition(String definition) {
        if (definition.contains(":")) {
            String[] parts = definition.split(":", 3);
            String name = parts[0].trim();
            String defaultValue = "";
            PluginVariable.Type type = PluginVariable.Type.SINGLE_LINE;
            
            if (parts.length >= 2) {
                defaultValue = parts[1].trim();
            }
            
            if (parts.length >= 3) {
                String typeStr = parts[2].trim().toLowerCase();
                if (typeStr.equals("area") || typeStr.equals("multiline")) {
                    type = PluginVariable.Type.MULTI_LINE;
                } else if (typeStr.equals("boolean") || typeStr.equals("bool")) {
                    type = PluginVariable.Type.BOOLEAN;
                }
            }
            
            return new PluginVariable(name, type, defaultValue);
        } else {
            // Fallback für alte Syntax: {Name}
            return new PluginVariable(definition.trim(), PluginVariable.Type.SINGLE_LINE);
        }
    }
    

    
    /**
     * Setzt die Variablen-Definitionen
     */
    public void setVariableDefinitions(List<PluginVariable> variableDefinitions) {
        this.variableDefinitions = variableDefinitions;
    }
    
    /**
     * Fügt eine Variable-Definition hinzu
     */
    public void addVariableDefinition(PluginVariable variable) {
        this.variableDefinitions.add(variable);
    }
    
    /**
     * Erstellt automatisch Variable-Definitionen basierend auf dem Prompt
     */
    public void createDefaultVariableDefinitions() {
        variableDefinitions.clear();
        java.util.Set<String> vars = getVariables();
        
        for (String varName : vars) {
            // Bestimme den Typ basierend auf dem Variablen-Namen
            PluginVariable.Type type = determineVariableType(varName);
            String defaultValue = getDefaultValueForVariable(varName);
            String description = getDescriptionForVariable(varName);
            
            PluginVariable variable = new PluginVariable(varName, type, defaultValue, description);
            variableDefinitions.add(variable);
        }
    }
    
    /**
     * Bestimmt den Typ einer Variable basierend auf ihrem Namen
     */
    private PluginVariable.Type determineVariableType(String varName) {
        String lowerName = varName.toLowerCase();
        
        // Boolean-Variablen
        if (lowerName.contains("aktiv") || lowerName.contains("enabled") ||
            lowerName.contains("wahr") || lowerName.contains("true") ||
            lowerName.contains("falsch") || lowerName.contains("false") ||
            lowerName.contains("ja") || lowerName.contains("nein") ||
            lowerName.contains("yes") || lowerName.contains("no") ||
            lowerName.contains("on") || lowerName.contains("off") ||
            lowerName.contains("show") || lowerName.contains("hide") ||
            lowerName.contains("anzeigen") || lowerName.contains("verstecken")) {
            return PluginVariable.Type.BOOLEAN;
        }
        
        // Mehrzeilige Variablen (längere Texte)
        if (lowerName.contains("text") || lowerName.contains("inhalt") || 
            lowerName.contains("beschreibung") || lowerName.contains("geschichte") ||
            lowerName.contains("plot") || lowerName.contains("szenario") ||
            lowerName.contains("dialog") || lowerName.contains("monolog") ||
            lowerName.contains("analyse") || lowerName.contains("kommentar") ||
            lowerName.contains("selektierter_text") || lowerName.contains("selected_text")) {
            return PluginVariable.Type.MULTI_LINE;
        }
        
        // Einzeilige Variablen (kurze Werte)
        return PluginVariable.Type.SINGLE_LINE;
    }
    
    /**
     * Gibt Standard-Werte für Variablen zurück
     */
    private String getDefaultValueForVariable(String varName) {
        String lowerName = varName.toLowerCase();
        
        if (lowerName.contains("name") || lowerName.contains("titel")) {
            return "Beispiel";
        }
        if (lowerName.contains("alter") || lowerName.contains("age")) {
            return "25";
        }
        if (lowerName.contains("geschlecht") || lowerName.contains("gender")) {
            return "männlich";
        }
        if (lowerName.contains("beruf") || lowerName.contains("job")) {
            return "Angestellter";
        }
        
        return "";
    }
    
    /**
     * Gibt Beschreibungen für Variablen zurück
     */
    private String getDescriptionForVariable(String varName) {
        String lowerName = varName.toLowerCase();
        
        if (lowerName.contains("name") || lowerName.contains("titel")) {
            return "Name oder Titel";
        }
        if (lowerName.contains("text") || lowerName.contains("inhalt")) {
            return "Text-Inhalt";
        }
        if (lowerName.contains("beschreibung")) {
            return "Detaillierte Beschreibung";
        }
        if (lowerName.contains("geschichte")) {
            return "Geschichte oder Hintergrund";
        }
        if (lowerName.contains("plot")) {
            return "Plot oder Handlung";
        }
        if (lowerName.contains("szenario")) {
            return "Szenario oder Situation";
        }
        if (lowerName.contains("dialog")) {
            return "Dialog oder Gespräch";
        }
        if (lowerName.contains("monolog")) {
            return "Monolog oder Gedanken";
        }
        if (lowerName.contains("analyse")) {
            return "Analyse oder Bewertung";
        }
        if (lowerName.contains("kommentar")) {
            return "Kommentar oder Anmerkung";
        }
        if (lowerName.contains("selektierter_text") || lowerName.contains("selected_text")) {
            return "Ausgewählter Text aus dem Editor";
        }
        
        return "Variable: " + varName;
    }
    
    // Getter und Setter
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    
    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }
    
    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
    
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    
    @Override
    public String toString() {
        return name;
    }
    
    /**
     * Konvertiert das Plugin zu einem JSON-String
     */
    public String toJsonString() {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"name\": \"").append(escapeJson(name)).append("\",\n");
        json.append("  \"description\": \"").append(escapeJson(description)).append("\",\n");
        json.append("  \"category\": \"").append(escapeJson(category)).append("\",\n");
        json.append("  \"prompt\": \"").append(escapeJson(prompt)).append("\",\n");
        json.append("  \"temperature\": ").append(temperature).append(",\n");
        json.append("  \"maxTokens\": ").append(maxTokens).append(",\n");
        json.append("  \"enabled\": ").append(enabled).append("\n");
        json.append("}");
        return json.toString();
    }
    
    /**
     * Escaped JSON-Strings
     */
    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
}
