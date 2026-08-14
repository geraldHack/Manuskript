package com.manuskript;

import java.util.prefs.Preferences;

/**
 * Preference-Keys für den Canvas-Kapitel-Editor.
 * Liest noch Legacy-Keys {@code prototype_editor_*} und migriert sie einmalig.
 */
public final class CanvasEditorPrefs {

    public static final String PREFIX = "canvas_editor_";
    public static final String LEGACY_PREFIX = "prototype_editor_";
    public static final String WINDOW_TYPE = "canvas_editor";
    public static final String LEGACY_WINDOW_TYPE = "prototype_editor";
    public static final String SPLIT_PREFIX = "canvas_editor_main_split_";
    public static final String LEGACY_SPLIT_PREFIX = "prototype_editor_main_split_";

    private CanvasEditorPrefs() {
    }

    public static String key(String suffix) {
        return PREFIX + suffix;
    }

    public static String legacyKey(String suffix) {
        return LEGACY_PREFIX + suffix;
    }

    /** Liest String; migriert Legacy-Wert auf neuen Key. */
    public static String get(Preferences prefs, String suffix, String defaultValue) {
        String neu = key(suffix);
        String value = prefs.get(neu, null);
        if (value != null) {
            return value;
        }
        String alt = prefs.get(legacyKey(suffix), null);
        if (alt != null) {
            prefs.put(neu, alt);
            return alt;
        }
        return defaultValue;
    }

    public static double getDouble(Preferences prefs, String suffix, double defaultValue) {
        String neu = key(suffix);
        if (prefs.get(neu, null) != null) {
            return prefs.getDouble(neu, defaultValue);
        }
        String legacy = legacyKey(suffix);
        if (prefs.get(legacy, null) != null) {
            double v = prefs.getDouble(legacy, defaultValue);
            prefs.putDouble(neu, v);
            return v;
        }
        return defaultValue;
    }

    public static boolean getBoolean(Preferences prefs, String suffix, boolean defaultValue) {
        String neu = key(suffix);
        if (prefs.get(neu, null) != null) {
            return prefs.getBoolean(neu, defaultValue);
        }
        String legacy = legacyKey(suffix);
        if (prefs.get(legacy, null) != null) {
            boolean v = prefs.getBoolean(legacy, defaultValue);
            prefs.putBoolean(neu, v);
            return v;
        }
        return defaultValue;
    }

    public static void put(Preferences prefs, String suffix, String value) {
        prefs.put(key(suffix), value);
    }

    public static void putDouble(Preferences prefs, String suffix, double value) {
        prefs.putDouble(key(suffix), value);
    }

    public static void putBoolean(Preferences prefs, String suffix, boolean value) {
        prefs.putBoolean(key(suffix), value);
    }
}
