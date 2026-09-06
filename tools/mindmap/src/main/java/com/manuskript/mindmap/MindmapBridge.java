package com.manuskript.mindmap;

/** JavaScript-Bridge für WebView (vis.js). */
public final class MindmapBridge {

    private final MindmapWindow window;

    public MindmapBridge(MindmapWindow window) {
        this.window = window;
    }

    public void onGraphChanged(String json) {
        window.onGraphChangedFromJs(json);
    }
}
