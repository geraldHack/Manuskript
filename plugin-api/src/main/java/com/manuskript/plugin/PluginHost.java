package com.manuskript.plugin;

import javafx.scene.Scene;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Dienste der laufenden Manuskript-Anwendung für ein Plugin.
 */
public interface PluginHost {

    /** Aktuelles Projektverzeichnis, falls eines gewählt ist. */
    Optional<Path> projectRoot();

    /** Programmverzeichnis (bei jpackage: {@code Contents/app} bzw. {@code app/}). */
    Path applicationHome();

    /**
     * Wurzel für Konfiguration — in Manuskript dasselbe wie {@link #applicationHome()}
     * (darunter liegt {@code config/}).
     */
    Path configDir();

    /** Markdown des aktuell ausgewählten Kapitels, falls vorhanden. */
    Optional<String> currentChapterMarkdown();

    int themeIndex();

    /**
     * Neue Stage im aktuellen Manuskript-Look ({@code CustomStage} + Theme).
     * Inhalt mit {@link #attachScene(Stage, Scene)} setzen, danach {@link Stage#show()}.
     */
    Stage createThemedStage(String title);

    /** Setzt die Scene inkl. Titelleiste und Theme. */
    void attachScene(Stage stage, Scene scene);

    void openInBrowser(String uri);
}
