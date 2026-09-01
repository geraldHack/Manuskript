package com.manuskript.agent;

import com.manuskript.MdTextArea;
import com.manuskript.MdTextAreaOptions;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Nur-Lesen-Markdown für Agentenantworten. Höchstens Suche, kein Format-Toolbar.
 */
final class AgentAnswerMdArea {

    private AgentAnswerMdArea() {
    }

    static MdTextArea create(String fontFamily, int fontSize, boolean compact) {
        return create(fontFamily, fontSize, compact, AgentFindingStyles.themeIndex());
    }

    static MdTextArea create(String fontFamily, int fontSize, boolean compact, int themeIndex) {
        String family = fontFamily != null && !fontFamily.isBlank() ? fontFamily.trim() : "Segoe UI";
        int size = fontSize > 0 ? fontSize : 14;
        int theme = themeIndex >= 0 ? themeIndex : AgentFindingStyles.themeIndex();
        MdTextArea area = new MdTextArea(MdTextAreaOptions.builder()
                .editable(false)
                .showToolbar(true)
                .enableUndoRedo(false)
                .enableFontControls(false)
                .enableJustify(false)
                .enableBasicFormatting(false)
                .enableExtendedFormatting(false)
                .enableSearch(true)
                .enableReplace(false)
                .enableHideMarkupToggle(false)
                .hideMarkup(true)
                .fontFamily(family)
                .fontSize(size)
                .themeIndex(theme)
                .build());
        area.getStyleClass().add("agent-answer-md");
        area.setMinWidth(0);
        area.setMinHeight(compact ? 80 : 0);
        area.setPrefHeight(Region.USE_COMPUTED_SIZE);
        area.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        VBox.setVgrow(area, Priority.ALWAYS);
        area.getEditor().setMinHeight(0);
        area.getEditor().setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(area.getEditor(), Priority.ALWAYS);
        area.applyTheme(theme);
        return area;
    }

    static void applyFont(MdTextArea area, String fontFamily, int fontSize) {
        if (area == null) {
            return;
        }
        if (fontFamily != null && !fontFamily.isBlank()) {
            area.getEditor().setFontFamilyForAll(fontFamily.trim());
        }
        if (fontSize > 0) {
            area.getEditor().setFontSizeForAll(fontSize);
        }
    }

    static void applyTheme(MdTextArea area, int themeIndex) {
        if (area == null || themeIndex < 0) {
            return;
        }
        area.applyTheme(themeIndex);
    }
}
