package com.manuskript.stats;

import com.manuskript.plugin.ManuskriptPlugin;
import com.manuskript.plugin.PluginHost;

/**
 * Snapshot-Statistik als In-Process-Plugin (kein Überwachungsmodus).
 */
public final class SchreibStatistikPlugin implements ManuskriptPlugin {

    private StatsWindow window;

    @Override
    public String id() {
        return "schreib-statistik";
    }

    @Override
    public String label() {
        return "Statistik";
    }

    @Override
    public void start(PluginHost host) {
        if (window == null) {
            window = new StatsWindow(host);
        }
        window.show();
    }

    @Override
    public void stop() {
        window = null;
    }
}
