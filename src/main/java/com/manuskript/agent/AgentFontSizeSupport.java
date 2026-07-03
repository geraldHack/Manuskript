package com.manuskript.agent;

import com.manuskript.CustomChatArea;
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
        applyEditorFont(root, size, null, opacityLabel);
    }

    public static void applyEditorFont(Node root, int size, String fontFamily, Label opacityLabel) {
        if (root == null) {
            return;
        }
        applyToNode(root, clamp(size), cssFontFamily(fontFamily), opacityLabel);
    }

    private static void applyToNode(Node node, int size, String cssFamily, Label opacityLabel) {
        if (node instanceof TextInputControl textControl) {
            textControl.setStyle(mergeFontStyle(textControl.getStyle(), size, cssFamily));
        } else if (node instanceof Text text) {
            text.setStyle(mergeFontStyle(text.getStyle(), size, cssFamily));
        } else if (node instanceof Label label) {
            if (label == opacityLabel) {
                label.setStyle(mergeFontStyle(label.getStyle(), size, cssFamily) + " -fx-opacity: 0.75;");
            } else {
                label.setStyle(mergeFontStyle(label.getStyle(), size, cssFamily));
            }
        } else if (node instanceof Labeled labeled) {
            labeled.setStyle(mergeFontStyle(labeled.getStyle(), size, cssFamily));
        } else if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                applyToNode(child, size, cssFamily, opacityLabel);
            }
        }
    }

    private static String mergeFontStyle(String existing, int size, String cssFamily) {
        return mergeFontFamily(mergeFontSize(existing, size), cssFamily);
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

    private static String mergeFontFamily(String existing, String cssFamily) {
        if (cssFamily == null || cssFamily.isBlank()) {
            return existing != null ? existing : "";
        }
        String fontPart = String.format("-fx-font-family: %s;", cssFamily);
        if (existing == null || existing.isBlank()) {
            return fontPart;
        }
        if (existing.contains("-fx-font-family:")) {
            return existing.replaceAll("-fx-font-family:\\s*[^;]+;", fontPart);
        }
        return existing + " " + fontPart;
    }

    static String cssFontFamily(String fontFamily) {
        if (fontFamily == null || fontFamily.isBlank()) {
            return null;
        }
        return CustomChatArea.cssFontFamily(fontFamily);
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
