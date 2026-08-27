package com.manuskript.plugin;

import com.manuskript.CustomStage;
import com.manuskript.EditorDialogThemes;
import com.manuskript.ResourceManager;
import com.manuskript.StageManager;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Erzeugt Plugin-Fenster im Manuskript-Look.
 */
public final class PluginStages {

    private static final Logger logger = LoggerFactory.getLogger(PluginStages.class);

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
        }
        try {
            String cssPath = ResourceManager.getCssResource("css/manuskript.css");
            if (cssPath != null && scene != null && !scene.getStylesheets().contains(cssPath)) {
                scene.getStylesheets().add(cssPath);
            }
        } catch (Exception e) {
            logger.warn("CSS für Plugin-Fenster nicht geladen: {}", e.getMessage());
        }
        if (stage instanceof CustomStage customStage) {
            customStage.setSceneWithTitleBar(scene);
            customStage.setFullTheme(themeIndex);
        } else if (stage != null) {
            stage.setScene(scene);
        }
    }
}
