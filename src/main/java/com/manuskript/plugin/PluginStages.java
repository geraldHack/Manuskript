package com.manuskript.plugin;

import com.manuskript.CustomStage;
import com.manuskript.EditorDialogThemes;
import com.manuskript.ResourceManager;
import com.manuskript.StageManager;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Erzeugt Plugin-Fenster im Manuskript-Look.
 */
public final class PluginStages {

    private PluginStages() {
    }

    public static Stage createThemedStage(String title, int themeIndex) {
        CustomStage stage = StageManager.createStage(title, null, false);
        stage.setFullTheme(themeIndex);
        stage.setTitleBarTheme(themeIndex);
        return stage;
    }

    public static void attachScene(Stage stage, Scene scene, int themeIndex) {
        if (scene != null && scene.getRoot() != null) {
            EditorDialogThemes.applyToNode(scene.getRoot(), themeIndex);
            ResourceManager.attachSceneStylesheets(scene);
        }
        if (stage instanceof CustomStage customStage) {
            customStage.setSceneWithTitleBar(scene);
            customStage.setFullTheme(themeIndex);
        } else if (stage != null) {
            stage.setScene(scene);
        }
    }
}
