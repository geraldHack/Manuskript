package com.manuskript;

/**
 * Repräsentiert eine Plugin-Variable mit verschiedenen Eingabetypen
 */
public class PluginVariable {
    
    public enum Type {
        SINGLE_LINE,    // Einzeilige Eingabe (TextField)
        MULTI_LINE,     // Mehrzeilige Eingabe (TextArea)
        BOOLEAN,        // Boolean-Eingabe (CheckBox)
        CHOICE,         // Auswahl (ComboBox)
        NUMBER          // Zahleneingabe (Spinner/TextField)
    }
    
    private String name;
    private String displayName;
    private Type type;
    private String defaultValue;
    private String description;
    private java.util.List<Option> options;
    private Double minValue;
    private Double maxValue;
    
    /**
     * Repräsentiert eine Option für Choice-Variablen
     */
    public static class Option {
        private String value;
        private String label;
        
        public Option(String value, String label) {
            this.value = value;
            this.label = label;
        }
        
        public String getValue() { return value; }
        public String getLabel() { return label; }
    }
    
    public PluginVariable(String name, Type type) {
        this.name = name;
        this.type = type;
        this.displayName = formatVariableName(name);
    }
    
    public PluginVariable(String name, Type type, String defaultValue) {
        this(name, type);
        this.defaultValue = defaultValue;
    }
    
    public PluginVariable(String name, Type type, String defaultValue, String description) {
        this(name, type, defaultValue);
        this.description = description;
    }
    
    // Getter und Setter
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    
    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }
    
    public String getDefaultValue() { return defaultValue; }
    public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public java.util.List<Option> getOptions() { return options; }
    public void setOptions(java.util.List<Option> options) { this.options = options; }
    
    public Double getMinValue() { return minValue; }
    public void setMinValue(Double minValue) { this.minValue = minValue; }
    
    public Double getMaxValue() { return maxValue; }
    public void setMaxValue(Double maxValue) { this.maxValue = maxValue; }
    
    /**
     * Formatiert einen Variablen-Namen für die Anzeige
     */
    private String formatVariableName(String variableName) {
        // Entferne geschweifte Klammern falls vorhanden
        String name = variableName.replace("{", "").replace("}", "");
        
        // Ersetze Unterstriche durch Leerzeichen und mache erste Buchstaben groß
        String[] words = name.split("_");
        StringBuilder result = new StringBuilder();
        
        for (int i = 0; i < words.length; i++) {
            if (i > 0) result.append(" ");
            if (words[i].length() > 0) {
                result.append(words[i].substring(0, 1).toUpperCase());
                if (words[i].length() > 1) {
                    result.append(words[i].substring(1).toLowerCase());
                }
            }
        }
        
        return result.toString();
    }
}
