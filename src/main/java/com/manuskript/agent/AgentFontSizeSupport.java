package com.manuskript.agent;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.TextInputControl;
import javafx.scene.text.Text;

/**
 * Einheitliche Schriftgrößen-Anpassung für Agenten-Panel-UI.
 */
public final class AgentFontSizeSupport {

    private AgentFontSizeSupport() {
    }

    public static void apply(Node root, int size) {
        apply(root, size, null);
    }

    public static void apply(Node root, int size, Label opacityLabel) {
        if (root == null) {
            return;
        }
        applyToNode(root, clamp(size), opacityLabel);
    }

    private static void applyToNode(Node node, int size, Label opacityLabel) {
        String fontCss = String.format("-fx-font-size: %dpx;", size);
        if (node instanceof TextInputControl textControl) {
            textControl.setStyle(mergeFontSize(textControl.getStyle(), size));
        } else if (node instanceof Text text) {
            text.setStyle(mergeFontSize(text.getStyle(), size));
        } else if (node instanceof Label label) {
            if (label == opacityLabel) {
                label.setStyle(mergeFontSize(label.getStyle(), size) + " -fx-opacity: 0.75;");
            } else {
                label.setStyle(mergeFontSize(label.getStyle(), size));
            }
        } else if (node instanceof Labeled labeled) {
            labeled.setStyle(mergeFontSize(labeled.getStyle(), size));
        } else if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                applyToNode(child, size, opacityLabel);
            }
        }
    }

    private static String mergeFontSize(String existing, int size) {
        String fontPart = String.format("-fx-font-size: %dpx;", size);
        if (existing == null || existing.isBlank()) {
            return fontPart;
        }
        if (existing.contains("-fx-font-size:")) {
            return existing.replaceAll("-fx-font-size:\\s*[^;]+;", fontPart);
        }
        return existing + " " + fontPart;
    }

    private static int clamp(int size) {
        if (size < 8) {
            return 8;
        }
        if (size > 72) {
            return 72;
        }
        return size;
    }
}
