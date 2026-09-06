package com.manuskript.agent;

import com.manuskript.EditorDialogThemes;
import javafx.geometry.Bounds;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;

/** ContextMenus in Agent-Tabs (Screen-Koordinaten). */
public final class AgentPopupSupport {

    private AgentPopupSupport() {
    }

    public static void showMenuBelow(ContextMenu menu, Node anchor) {
        showMenuBelow(menu, anchor, AgentFindingStyles.themeIndex());
    }

    public static void showMenuBelow(ContextMenu menu, Node anchor, int themeIndex) {
        if (menu == null || anchor == null || menu.getItems().isEmpty()) {
            return;
        }
        EditorDialogThemes.styleContextMenu(menu, themeIndex);
        Bounds screen = anchor.localToScreen(anchor.getBoundsInLocal());
        if (screen != null) {
            menu.show(anchor, screen.getMinX(), screen.getMaxY());
        } else {
            menu.show(anchor, Side.BOTTOM, 0, 0);
        }
    }
}
