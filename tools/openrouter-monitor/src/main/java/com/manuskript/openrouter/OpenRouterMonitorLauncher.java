package com.manuskript.openrouter;

/**
 * Einstieg für das Fat-JAR: startet JavaFX mit korrektem Modulpfad, falls nötig.
 */
public final class OpenRouterMonitorLauncher {

    private OpenRouterMonitorLauncher() {
    }

    public static void main(String[] args) {
        OpenRouterMonitorApp.setLaunchArgs(args);
        OpenRouterMonitorApp.launch(OpenRouterMonitorApp.class, args);
    }
}
