package com.manuskript.backup;

import com.manuskript.plugin.ManuskriptPlugin;
import com.manuskript.plugin.PluginHost;

/**
 * Projekt-Backup als In-Process-Plugin mit Überwachungsmodus.
 */
public final class ProjektBackupPlugin implements ManuskriptPlugin {

    private BackupWindow window;
    private BackupMonitor monitor;

    @Override
    public String id() {
        return "projekt-backup";
    }

    @Override
    public String label() {
        return "Backup";
    }

    @Override
    public boolean wantsBackgroundStart() {
        return true;
    }

    @Override
    public void startBackground(PluginHost host) {
        if (monitor == null) {
            monitor = new BackupMonitor(host);
        }
        monitor.start();
    }

    @Override
    public void start(PluginHost host) {
        startBackground(host);
        if (window == null) {
            window = new BackupWindow(host);
        }
        window.show();
    }

    @Override
    public void stop() {
        if (monitor != null) {
            monitor.stop();
            monitor = null;
        }
        window = null;
    }
}
