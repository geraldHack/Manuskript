package com.manuskript;

import javafx.collections.ObservableMap;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.ScrollEvent;

/**
 * Plattformübergreifende Editor-Kürzel: Strg (Windows/Linux) und Cmd (macOS).
 */
public final class EditingShortcuts {

    private EditingShortcuts() {
    }

    /**
     * Modifikator für Bearbeitungskürzel (Strg, Cmd bzw. JavaFX-Shortcut-Taste).
     */
    public static boolean isShortcutDown(KeyEvent event) {
        if (event == null) {
            return false;
        }
        return event.isControlDown() || event.isMetaDown() || event.isShortcutDown();
    }

    public static boolean isShortcutDown(ScrollEvent event) {
        if (event == null) {
            return false;
        }
        return event.isControlDown() || event.isMetaDown();
    }

    public static void bindPlatformAccelerators(ObservableMap<KeyCombination, Runnable> accelerators,
                                                String keyCombination, Runnable action) {
        if (accelerators == null || action == null || keyCombination == null || keyCombination.isBlank()) {
            return;
        }
        accelerators.put(KeyCombination.valueOf("Shortcut+" + keyCombination), action);
        accelerators.put(KeyCombination.valueOf("Meta+" + keyCombination), action);
        accelerators.put(KeyCombination.valueOf("Ctrl+" + keyCombination), action);
    }

    public static boolean isMac() {
        String os = System.getProperty("os.name", "");
        return os.toLowerCase().contains("mac");
    }

    /** Anzeige in Tooltips/Menüs, z. B. „Strg+S“ oder „Strg/Cmd+S“. */
    public static String modifierLabel() {
        return isMac() ? "Strg/Cmd" : "Strg";
    }

    public static String acceleratorHint(String key) {
        return modifierLabel() + "+" + key;
    }
}
