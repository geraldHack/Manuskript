package com.manuskript.mammouth;

import com.manuskript.plugin.ManuskriptPlugin;
import com.manuskript.plugin.PluginHost;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Mammouth-Monitor als In-Process-Plugin.
 */
public final class MammouthMonitorPlugin implements ManuskriptPlugin {

    private Stage stage;
    private MammouthMonitorUi.Session session;

    @Override
    public String id() {
        return "mammouth-monitor";
    }

    @Override
    public String label() {
        return "Mammouth-Monitor";
    }

    @Override
    public void start(PluginHost host) {
        if (stage != null && stage.isShowing()) {
            stage.toFront();
            return;
        }
        if (session != null) {
            session.close();
        }
        String[] args = new String[]{"--config-dir=" + host.configDir().toAbsolutePath()};
        MammouthConfigLoader.MammouthConfig config = MammouthConfigLoader.load(args);
        session = MammouthMonitorUi.create(config, host::openInBrowser);
        stage = host.createThemedStage("Mammouth Monitor");
        host.attachScene(stage, new Scene(session.root(), 920, 620));
        stage.setOnCloseRequest(e -> {
            if (session != null) {
                session.close();
            }
        });
        stage.show();
    }

    @Override
    public void stop() {
        if (session != null) {
            session.close();
        }
        if (stage != null) {
            stage.close();
        }
    }
}
