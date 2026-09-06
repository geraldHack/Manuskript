package com.manuskript.publish;

import com.manuskript.plugin.ManuskriptPlugin;
import com.manuskript.plugin.PluginHost;

/**
 * Publish-Paket-Helfer für KDP eBook und Tolino Media.
 */
public final class PublishPaketPlugin implements ManuskriptPlugin {

    private PublishPackageWindow window;

    @Override
    public String id() {
        return "publish-paket";
    }

    @Override
    public String label() {
        return "Publish-Paket";
    }

    @Override
    public void start(PluginHost host) {
        if (window == null) {
            window = new PublishPackageWindow(host);
        }
        window.show();
    }

    @Override
    public void stop() {
        window = null;
    }
}
