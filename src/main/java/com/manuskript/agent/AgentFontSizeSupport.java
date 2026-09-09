package com.manuskript.agent;

import com.manuskript.CustomChatArea;
import com.manuskript.MdTextArea;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
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
        applyToNode(root, clamp(size), fontFamily, cssFontFamily(fontFamily), opacityLabel);
    }

    /**
     * Label + Slider + Wert: volle Breite für den Slider, Chrome-Schrift, kein Theme auf den Track.
     */
    public static HBox createSliderRow(String labelText, Slider slider, Label valueLabel) {
        Label caption = new Label(labelText);
        caption.getStyleClass().add("agent-chrome-label");
        caption.setMinWidth(Region.USE_PREF_SIZE);
        caption.setPrefWidth(Region.USE_COMPUTED_SIZE);
        caption.setMaxWidth(Region.USE_PREF_SIZE);
        HBox.setHgrow(caption, Priority.NEVER);

        valueLabel.getStyleClass().add("agent-chrome-label");
        valueLabel.setMinWidth(48);
        valueLabel.setPrefWidth(56);
        valueLabel.setMaxWidth(64);
        valueLabel.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(valueLabel, Priority.NEVER);

        slider.setMinWidth(80);
        slider.setMaxWidth(Double.MAX_VALUE);
        slider.getStyleClass().add("agent-chrome-slider");
        HBox.setHgrow(slider, Priority.ALWAYS);

        HBox row = new HBox(8);
        row.getStyleClass().add("agent-chrome-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setFillHeight(true);
        row.getChildren().addAll(caption, slider, valueLabel);
        return row;
    }

    private static void applyToNode(Node node, int size, String fontFamily, String cssFamily, Label opacityLabel) {
        if (node == null || isChrome(node)) {
            return;
        }
        if (node instanceof MdTextArea md) {
            AgentAnswerMdArea.applyFont(md, fontFamily, size);
            return;
        }
        if (node instanceof Spinner || node instanceof ComboBoxBase || node instanceof Slider) {
            return;
        }
        if (node instanceof TextInputControl textControl) {
            textControl.setStyle(mergeFontStyle(textControl.getStyle(), size, cssFamily));
        } else if (node instanceof Text text) {
            text.setStyle(mergeFontStyle(text.getStyle(), size, cssFamily));
        } else if (node instanceof Button || node instanceof ToggleButton) {
            return;
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
                applyToNode(child, size, fontFamily, cssFamily, opacityLabel);
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

    private static boolean isChrome(Node node) {
        if (node == null) {
            return false;
        }
        return node.getStyleClass().contains("chatbot-context-pane")
                || node.getStyleClass().contains("agent-chrome-label")
                || node.getStyleClass().contains("agent-chrome-row");
    }
}
