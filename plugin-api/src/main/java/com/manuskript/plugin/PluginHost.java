package com.manuskript.plugin;

import javafx.scene.Scene;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

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
     * Theme-Farbe wie {@code EditorDialogThemes}: 0=Hintergrund, 1=Text, 2=Fläche/Akzent, 3=Rahmen.
     */
    default String themeColor(int colorIndex) {
        return PluginHostThemes.color(themeIndex(), colorIndex);
    }

    /**
     * Neue Stage im aktuellen Manuskript-Look ({@code CustomStage} + Theme).
     * Inhalt mit {@link #attachScene(Stage, Scene)} setzen, danach {@link Stage#show()}.
     */
    Stage createThemedStage(String title);

    /** Setzt die Scene inkl. Titelleiste und Theme. */
    void attachScene(Stage stage, Scene scene);

    void openInBrowser(String uri);

    /**
     * One-Shot-Chat über dieselben Agenten-Parameter wie die App
     * ({@code agent.backend}, OpenAI/Ollama-Keys und Modelle).
     * Default: nicht unterstützt.
     */
    default CompletableFuture<String> completeChat(String systemPrompt, String userPrompt, int maxTokens) {
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("KI-Chat ist in diesem Host nicht verfügbar"));
    }
}
