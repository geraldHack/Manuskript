package com.manuskript.buchpreview;

import com.manuskript.plugin.ManuskriptPlugin;
import com.manuskript.plugin.PluginHost;

public final class BuchPreviewPlugin implements ManuskriptPlugin {

    private BuchPreviewWindow window;

    @Override
    public String id() {
        return "buch-preview";
    }

    @Override
    public String label() {
        return "Taschenbuch-Preview";
    }

    @Override
    public void start(PluginHost host) {
        if (window == null) {
            window = new BuchPreviewWindow(host);
        }
        window.show();
    }

    @Override
    public void stop() {
        window = null;
    }
}
