package com.manuskript.mindmap;

import com.manuskript.plugin.ManuskriptPlugin;
import com.manuskript.plugin.PluginHost;

public final class MindmapPlugin implements ManuskriptPlugin {

    private MindmapWindow window;

    @Override
    public String id() {
        return "mindmap";
    }

    @Override
    public String label() {
        return "Mindmap";
    }

    @Override
    public void start(PluginHost host) {
        if (window == null) {
            window = new MindmapWindow(host);
        }
        window.show();
    }

    @Override
    public void stop() {
        window = null;
    }
}
