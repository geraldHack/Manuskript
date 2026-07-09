package com.manuskript;

import java.util.prefs.Preferences;

/**
 * Getrennte Java-Preferences für Entwicklung und installierte jpackage-.app.
 * Verhindert, dass die gebaute App dieselben Pfade wie {@code mvn javafx:run} nutzt.
 */
public final class ApplicationPreferences {

    private static final String STANDALONE_NODE = "standalone";

    private ApplicationPreferences() {
    }

    public static boolean isPackagedApplication() {
        String appPath = System.getProperty("jpackage.app-path");
        return appPath != null && !appPath.isBlank();
    }

    public static Preferences resourceManagerNode() {
        Preferences base = Preferences.userNodeForPackage(ResourceManager.class);
        return isPackagedApplication() ? base.node(STANDALONE_NODE) : base;
    }

    public static Preferences mainControllerNode() {
        Preferences base = Preferences.userNodeForPackage(MainController.class);
        return isPackagedApplication() ? base.node(STANDALONE_NODE) : base;
    }
}
