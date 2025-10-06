package com.manuskript;

import java.util.prefs.Preferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Robuste Verwaltung von User Preferences mit Validierung und Defaults
 */
public class PreferencesManager {
    
    private static final Logger logger = LoggerFactory.getLogger(PreferencesManager.class);
    
    // Standard-Werte für Fenster
    public static final double DEFAULT_WINDOW_WIDTH = 1200.0;
    public static final double DEFAULT_WINDOW_HEIGHT = 800.0;
    public static final double DEFAULT_EDITOR_WIDTH = 1200.0;
    public static final double DEFAULT_EDITOR_HEIGHT = 800.0;
    public static final double DEFAULT_DIFF_WIDTH = 1600.0;
    public static final double DEFAULT_DIFF_HEIGHT = 900.0;
    
    // Mindest- und Maximalwerte
    public static final double MIN_WINDOW_WIDTH = 800.0;
    public static final double MIN_WINDOW_HEIGHT = 600.0;
    public static final double MIN_EDITOR_WIDTH = 1200.0;
    public static final double MIN_EDITOR_HEIGHT = 800.0;
    public static final double MAX_WINDOW_WIDTH = 3000.0;
    public static final double MAX_WINDOW_HEIGHT = 2000.0;
    
    // Position-Bereiche
    public static final double MIN_POSITION = -1000.0;
    public static final double MAX_POSITION = 5000.0;
    
    /**
     * Lädt eine Double-Preference mit Validierung
     */
    public static double getDouble(Preferences prefs, String key, double defaultValue, 
                                  double minValue, double maxValue) {
        try {
            double value = prefs.getDouble(key, defaultValue);
            
            // Validierung
            if (Double.isNaN(value) || Double.isInfinite(value) || 
                value < minValue || value > maxValue) {
                logger.warn("Ungültiger Wert für {}: {} - verwende Default: {}", key, value, defaultValue);
                prefs.putDouble(key, defaultValue);
                return defaultValue;
            }
            
            return value;
        } catch (Exception e) {
            logger.error("Fehler beim Laden der Preference {}: {}", key, e.getMessage());
            return defaultValue;
        }
    }
    
    /**
     * Lädt eine Integer-Preference mit Validierung
     */
    public static int getInt(Preferences prefs, String key, int defaultValue, 
                            int minValue, int maxValue) {
        try {
            int value = prefs.getInt(key, defaultValue);
            
            // Validierung
            if (value < minValue || value > maxValue) {
                logger.warn("Ungültiger Wert für {}: {} - verwende Default: {}", key, value, defaultValue);
                prefs.putInt(key, defaultValue);
                return defaultValue;
            }
            
            return value;
        } catch (Exception e) {
            logger.error("Fehler beim Laden der Preference {}: {}", key, e.getMessage());
            return defaultValue;
        }
    }
    
    /**
     * Lädt eine String-Preference mit Validierung
     */
    public static String getString(Preferences prefs, String key, String defaultValue) {
        try {
            String value = prefs.get(key, defaultValue);
            
            // Validierung
            if (value == null || value.trim().isEmpty()) {
                logger.warn("Ungültiger Wert für {}: '{}' - verwende Default: '{}'", key, value, defaultValue);
                prefs.put(key, defaultValue);
                return defaultValue;
            }
            
            return value;
        } catch (Exception e) {
            logger.error("Fehler beim Laden der Preference {}: {}", key, e.getMessage());
            return defaultValue;
        }
    }
    
    /**
     * Lädt eine Boolean-Preference mit Validierung
     */
    public static boolean getBoolean(Preferences prefs, String key, boolean defaultValue) {
        try {
            return prefs.getBoolean(key, defaultValue);
        } catch (Exception e) {
            logger.error("Fehler beim Laden der Preference {}: {}", key, e.getMessage());
            return defaultValue;
        }
    }
    
    /**
     * Speichert eine Double-Preference mit Validierung
     */
    public static void putDouble(Preferences prefs, String key, double value, 
                                double minValue, double maxValue) {
        try {
            // Validierung vor dem Speichern
            if (Double.isNaN(value) || Double.isInfinite(value) || 
                value < minValue || value > maxValue) {
                logger.warn("Ungültiger Wert für {}: {} - speichere nicht", key, value);
                return;
            }
            
            prefs.putDouble(key, value);
        } catch (Exception e) {
            logger.error("Fehler beim Speichern der Preference {}: {}", key, e.getMessage());
        }
    }
    
    /**
     * Speichert eine Integer-Preference mit Validierung
     */
    public static void putInt(Preferences prefs, String key, int value, 
                             int minValue, int maxValue) {
        try {
            // Validierung vor dem Speichern
            if (value < minValue || value > maxValue) {
                logger.warn("Ungültiger Wert für {}: {} - speichere nicht", key, value);
                return;
            }
            
            prefs.putInt(key, value);
        } catch (Exception e) {
            logger.error("Fehler beim Speichern der Preference {}: {}", key, e.getMessage());
        }
    }
    
    /**
     * Speichert eine String-Preference mit Validierung
     */
    public static void putString(Preferences prefs, String key, String value) {
        try {
            // Validierung vor dem Speichern
            if (value == null || value.trim().isEmpty()) {
                logger.warn("Ungültiger Wert für {}: '{}' - speichere nicht", key, value);
                return;
            }
            
            prefs.put(key, value);
        } catch (Exception e) {
            logger.error("Fehler beim Speichern der Preference {}: {}", key, e.getMessage());
        }
    }
    
    /**
     * Speichert eine Boolean-Preference
     */
    public static void putBoolean(Preferences prefs, String key, boolean value) {
        try {
            prefs.putBoolean(key, value);
        } catch (Exception e) {
            logger.error("Fehler beim Speichern der Preference {}: {}", key, e.getMessage());
        }
    }
    
    /**
     * Lädt Fenster-Größe mit Standard-Validierung
     */
    public static double getWindowWidth(Preferences prefs, String key, double defaultValue) {
        return getDouble(prefs, key, defaultValue, MIN_WINDOW_WIDTH, MAX_WINDOW_WIDTH);
    }
    
    /**
     * Lädt Fenster-Höhe mit Standard-Validierung
     */
    public static double getWindowHeight(Preferences prefs, String key, double defaultValue) {
        return getDouble(prefs, key, defaultValue, MIN_WINDOW_HEIGHT, MAX_WINDOW_HEIGHT);
    }
    
    /**
     * Lädt Editor-Fenster-Größe mit Standard-Validierung
     */
    public static double getEditorWidth(Preferences prefs, String key, double defaultValue) {
        return getDouble(prefs, key, defaultValue, MIN_EDITOR_WIDTH, MAX_WINDOW_WIDTH);
    }
    
    /**
     * Lädt Editor-Fenster-Höhe mit Standard-Validierung
     */
    public static double getEditorHeight(Preferences prefs, String key, double defaultValue) {
        return getDouble(prefs, key, defaultValue, MIN_EDITOR_HEIGHT, MAX_WINDOW_HEIGHT);
    }
    
    /**
     * Lädt Fenster-Position mit Standard-Validierung
     */
    public static double getWindowPosition(Preferences prefs, String key, double defaultValue) {
        return getDouble(prefs, key, defaultValue, MIN_POSITION, MAX_POSITION);
    }
    
    /**
     * Speichert Fenster-Größe mit Standard-Validierung
     */
    public static void putWindowWidth(Preferences prefs, String key, double value) {
        putDouble(prefs, key, value, MIN_WINDOW_WIDTH, MAX_WINDOW_WIDTH);
    }
    
    /**
     * Speichert Fenster-Höhe mit Standard-Validierung
     */
    public static void putWindowHeight(Preferences prefs, String key, double value) {
        putDouble(prefs, key, value, MIN_WINDOW_HEIGHT, MAX_WINDOW_HEIGHT);
    }
    
    /**
     * Speichert Editor-Fenster-Größe mit Standard-Validierung
     */
    public static void putEditorWidth(Preferences prefs, String key, double value) {
        putDouble(prefs, key, value, MIN_EDITOR_WIDTH, MAX_WINDOW_WIDTH);
    }
    
    /**
     * Speichert Editor-Fenster-Höhe mit Standard-Validierung
     */
    public static void putEditorHeight(Preferences prefs, String key, double value) {
        putDouble(prefs, key, value, MIN_EDITOR_HEIGHT, MAX_WINDOW_HEIGHT);
    }
    
    /**
     * Speichert Fenster-Position mit Standard-Validierung
     */
    public static void putWindowPosition(Preferences prefs, String key, double value) {
        putDouble(prefs, key, value, MIN_POSITION, MAX_POSITION);
    }
    
    /**
     * Setzt alle Editor-Fenster-Preferences auf Standardwerte zurück
     */
    public static void resetEditorWindowPreferences(Preferences prefs) {
        try {
            prefs.putDouble("editor_window_width", DEFAULT_EDITOR_WIDTH);
            prefs.putDouble("editor_window_height", DEFAULT_EDITOR_HEIGHT);
            prefs.remove("editor_window_x");
            prefs.remove("editor_window_y");
        } catch (Exception e) {
            logger.error("Fehler beim Zurücksetzen der Editor-Fenster-Preferences: {}", e.getMessage());
        }
    }
    
    /**
     * Setzt alle Hauptfenster-Preferences auf Standardwerte zurück
     */
    public static void resetMainWindowPreferences(Preferences prefs) {
        try {
            prefs.putDouble("window_width", DEFAULT_WINDOW_WIDTH);
            prefs.putDouble("window_height", DEFAULT_WINDOW_HEIGHT);
            prefs.remove("window_x");
            prefs.remove("window_y");
        } catch (Exception e) {
            logger.error("Fehler beim Zurücksetzen der Hauptfenster-Preferences: {}", e.getMessage());
        }
    }
}
