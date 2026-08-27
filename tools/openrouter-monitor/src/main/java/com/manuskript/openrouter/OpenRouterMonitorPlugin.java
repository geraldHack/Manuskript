package com.manuskript.openrouter;

import com.manuskript.plugin.ManuskriptPlugin;
import com.manuskript.plugin.PluginHost;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * OpenRouter-Monitor als In-Process-Plugin (CustomStage + Theme der Haupt-App).
 */
public final class OpenRouterMonitorPlugin implements ManuskriptPlugin {

    private Stage stage;
    private OpenRouterMonitorUi.Session session;

    @Override
    public String id() {
        return "openrouter-monitor";
    }

    @Override
    public String label() {
        return "OpenRouter-Monitor";
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
        ManuskriptConfigLoader.OpenRouterConfig config = ManuskriptConfigLoader.load(args);
        session = OpenRouterMonitorUi.create(config, host::openInBrowser);
        stage = host.createThemedStage("OpenRouter Monitor");
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
