package com.manuskript;

/**
 * Definition eines Eintrags für die Parameter-Verwaltung (parameters.properties / User Preferences).
 */
public class ParameterDef {
    public enum Type { STRING, INT, DOUBLE, BOOLEAN, CHOICE }

    private final String key;
    private final String title;
    private final Type type;
    private final String defaultValue;
    private final String helpText;
    private final String category;
    private final String[] choices;

    public ParameterDef(String key, Type type, String defaultValue, String helpText, String category) {
        this(key, null, type, defaultValue, helpText, category, null);
    }

    public ParameterDef(String key, Type type, String defaultValue, String helpText, String category, String[] choices) {
        this(key, null, type, defaultValue, helpText, category, choices);
    }

    public ParameterDef(String key, String title, Type type, String defaultValue, String helpText,
                        String category, String[] choices) {
        this.key = key;
        this.title = title == null || title.isBlank() ? null : title.trim();
        this.type = type;
        this.defaultValue = defaultValue == null ? "" : defaultValue;
        this.helpText = helpText == null ? "" : helpText;
        this.category = category == null ? "" : category;
        this.choices = choices;
    }

    public String getKey() { return key; }

    /** Nutzerfreundlicher Titel; fällt auf den Key zurück. */
    public String getTitle() { return title != null ? title : key; }

    public Type getType() { return type; }
    public String getDefaultValue() { return defaultValue; }
    public String getHelpText() { return helpText; }
    public String getCategory() { return category; }
    public String[] getChoices() { return choices; }
}
