package com.manuskript;

/**
 * Definition eines Eintrags für die Parameter-Verwaltung (parameters.properties / User Preferences).
 */
public class ParameterDef {
    public enum Type { STRING, INT, DOUBLE, BOOLEAN }

    private final String key;
    private final Type type;
    private final String defaultValue;
    private final String helpText;
    private final String category;

    public ParameterDef(String key, Type type, String defaultValue, String helpText, String category) {
        this.key = key;
        this.type = type;
        this.defaultValue = defaultValue == null ? "" : defaultValue;
        this.helpText = helpText == null ? "" : helpText;
        this.category = category == null ? "" : category;
    }

    public String getKey() { return key; }
    public Type getType() { return type; }
    public String getDefaultValue() { return defaultValue; }
    public String getHelpText() { return helpText; }
    public String getCategory() { return category; }
}
