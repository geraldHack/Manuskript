package com.manuskript.plugin;

/**
 * Einstiegspunkt für ein In-Process-Plugin (JAR in {@code plugins/}).
 * Implementierungen werden per {@link java.util.ServiceLoader} geladen.
 */
public interface ManuskriptPlugin {

    String id();

    String label();

    /**
     * Nutzer hat den Toolbar-Button geklickt — hier das Fenster öffnen.
     */
    void start(PluginHost host);

    /**
     * {@code true}: Manuskript ruft {@link #startBackground(PluginHost)} beim Laden
     * (Plugin ist aktiv in {@code plugins/}), ohne ein Fenster zu öffnen.
     * Für Zeitpläne und Überwachung, solange die App läuft.
     */
    default boolean wantsBackgroundStart() {
        return false;
    }

    /**
     * Überwachungsmodus ohne GUI. Mehrfach aufrufbar — Implementierungen sollen
     * idempotent sein. Läuft auf dem JavaFX-Application-Thread.
     */
    default void startBackground(PluginHost host) {
    }

    /**
     * Nach dem Laden des Plugins, sobald ein Projekt gewählt sein kann.
     * Standard: nichts. Mehrfach aufrufbar — Implementierungen sollen idempotent sein.
     */
    default void onLoaded(PluginHost host) {
    }

    default void stop() {
    }
}
