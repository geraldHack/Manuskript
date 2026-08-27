package com.manuskript.mammouth;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Eigenständiges JavaFX-Fenster für Mammouth Credits und Modelle.
 */
public class MammouthMonitorApp extends Application {

    private static String[] launchArgs = new String[0];

    public static void setLaunchArgs(String[] args) {
        launchArgs = args == null ? new String[0] : args.clone();
    }

    @Override
    public void start(Stage stage) {
        String[] args = mergeArgs(launchArgs, getParameters().getRaw().toArray(new String[0]));
        MammouthConfigLoader.MammouthConfig config = MammouthConfigLoader.load(args);
        MammouthMonitorUi.Session session = MammouthMonitorUi.create(
                config,
                url -> getHostServices().showDocument(url));

        Scene scene = new Scene(session.root(), 920, 620);
        stage.setTitle("Mammouth Monitor");
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> {
            session.close();
            Platform.exit();
        });
        stage.show();
    }

    public static void main(String[] args) {
        setLaunchArgs(args);
        launch(args);
    }

    private static String[] mergeArgs(String[] a, String[] b) {
        if (a == null || a.length == 0) return b == null ? new String[0] : b;
        if (b == null || b.length == 0) return a;
        String[] merged = new String[a.length + b.length];
        System.arraycopy(a, 0, merged, 0, a.length);
        System.arraycopy(b, 0, merged, a.length, b.length);
        return merged;
    }
}
