package com.manuskript.plugin;

/**
 * Einstiegspunkt für ein In-Process-Plugin (JAR in {@code plugins/}).
 * Implementierungen werden per {@link java.util.ServiceLoader} geladen.
 */
public interface ManuskriptPlugin {

    String id();

    String label();

    void start(PluginHost host);

    default void stop() {
    }
}
