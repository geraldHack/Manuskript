package com.manuskript.agent;

import com.manuskript.EditorDialogThemes;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.paint.Color;

/**
 * Davor/Danach-Spinner: dieselbe Fläche wie die Agent-Buttons, inkl. Zahlenfeld.
 * CSS-Themes erreichen das innere Textfeld nicht zuverlässig.
 */
final class AgentChromeSpinnerSupport {

    private static final String THEME_KEY = "agentChromeSpinnerTheme";
    private static final String HOOK_KEY = "agentChromeSpinnerHooked";

    private AgentChromeSpinnerSupport() {
    }

    static void apply(Spinner<?> spinner, int themeIndex) {
        if (spinner == null) {
            return;
        }
        int theme = Math.max(0, themeIndex);
        spinner.getProperties().put(THEME_KEY, theme);
        paint(spinner, theme);
        if (spinner.getProperties().putIfAbsent(HOOK_KEY, Boolean.TRUE) == null) {
            spinner.skinProperty().addListener((obs, oldSkin, newSkin) ->
                    Platform.runLater(() -> paint(spinner, themeOf(spinner))));
            spinner.sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene != null) {
                    Platform.runLater(() -> {
                        paint(spinner, themeOf(spinner));
                        Platform.runLater(() -> paint(spinner, themeOf(spinner)));
                    });
                }
            });
        }
    }

    private static int themeOf(Spinner<?> spinner) {
        Object value = spinner.getProperties().get(THEME_KEY);
        return value instanceof Integer index ? index : 1;
    }

    private static void paint(Spinner<?> spinner, int themeIndex) {
        String text = EditorDialogThemes.color(themeIndex, 1);
        String surface = EditorDialogThemes.color(themeIndex, 2);
        String border = EditorDialogThemes.color(themeIndex, 3);
        Color fill = Color.web(surface);
        Background body = new Background(new BackgroundFill(fill, new CornerRadii(4), Insets.EMPTY));
        Background inner = new Background(new BackgroundFill(fill, CornerRadii.EMPTY, Insets.EMPTY));

        spinner.getStyleClass().removeAll("theme-dark", "weiss-theme", "pastell-theme",
                "blau-theme", "gruen-theme", "lila-theme");
        spinner.setBackground(body);
        spinner.setStyle(String.format(
                "-fx-background-color: %s; -fx-control-inner-background: %s; -fx-text-fill: %s; "
                        + "-fx-border-color: %s; -fx-border-width: 1px; -fx-border-radius: 4px; "
                        + "-fx-background-radius: 4px; -fx-background-insets: 0;",
                surface, surface, text, border));

        TextField editor = spinner.getEditor();
        editor.getStyleClass().removeAll("theme-dark", "weiss-theme", "pastell-theme",
                "blau-theme", "gruen-theme", "lila-theme");
        editor.setBackground(inner);
        editor.setStyle(String.format(
                "-fx-background-color: %s; -fx-control-inner-background: %s; -fx-text-fill: %s; "
                        + "-fx-highlight-fill: %s; -fx-border-width: 0; -fx-background-insets: 0; "
                        + "-fx-background-radius: 0; -fx-font-family: system, \"Segoe UI\", sans-serif;",
                surface, surface, text, border));

        String arrowButton = String.format(
                "-fx-background-color: %s; -fx-background-insets: 0; -fx-border-width: 0;", surface);
        for (Node node : spinner.lookupAll(".increment-arrow-button")) {
            node.setStyle(arrowButton);
        }
        for (Node node : spinner.lookupAll(".decrement-arrow-button")) {
            node.setStyle(arrowButton);
        }
        String arrow = "-fx-background-color: " + text + ";";
        for (Node node : spinner.lookupAll(".increment-arrow")) {
            node.setStyle(arrow);
        }
        for (Node node : spinner.lookupAll(".decrement-arrow")) {
            node.setStyle(arrow);
        }
    }
}
