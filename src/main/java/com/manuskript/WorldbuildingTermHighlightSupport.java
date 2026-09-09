package com.manuskript;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Schaltbares Highlighting von Figuren-, Orts- und Lore-Begriffen im Canvas-Kapitel-Editor.
 */
public final class WorldbuildingTermHighlightSupport {

    private static final Logger logger = LoggerFactory.getLogger(WorldbuildingTermHighlightSupport.class);
    private static final Duration REFRESH_DEBOUNCE = Duration.millis(400);

    private final ChapterEditorHost host;
    private final ManuskriptTextEditor editor;
    private Timeline refreshTimeline;
    private boolean enabled;

    public WorldbuildingTermHighlightSupport(ChapterEditorHost host, ManuskriptTextEditor editor) {
        this.host = host;
        this.editor = editor;
        editor.setWorldTermOpenHandler(entry -> {
            MainController controller = host.getMainController();
            if (controller != null) {
                controller.openWorldEditorAt(entry.sourceFile(), entry.sectionHeading());
            }
        });
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) {
            refreshNow();
        } else {
            cancelScheduledRefresh();
            editor.clearWorldTermMatches();
        }
    }

    public void scheduleRefresh() {
        if (!enabled) {
            return;
        }
        if (refreshTimeline == null) {
            refreshTimeline = new Timeline(new KeyFrame(REFRESH_DEBOUNCE, event -> refreshNow()));
            refreshTimeline.setCycleCount(1);
        }
        refreshTimeline.stop();
        refreshTimeline.playFromStart();
    }

    public void refreshNow() {
        if (!enabled) {
            return;
        }
        File docx = host.getOriginalDocxFile();
        if (docx == null) {
            editor.clearWorldTermMatches();
            return;
        }
        try {
            String path = docx.getAbsolutePath();
            WorldbuildingTermIndex index = WorldbuildingTermIndex.load(
                    safeLoad(NovelManager.loadCharacters(path)),
                    safeLoad(NovelManager.loadWorldbuilding(path)));
            editor.applyWorldTermMatches(index.findMatches(host.getText()));
        } catch (RuntimeException ex) {
            logger.warn("Worldbuilding-Highlighting fehlgeschlagen: {}", ex.getMessage());
            editor.clearWorldTermMatches();
        }
    }

    private void cancelScheduledRefresh() {
        if (refreshTimeline != null) {
            refreshTimeline.stop();
        }
    }

    private static String safeLoad(String text) {
        return text != null ? text : "";
    }
}
