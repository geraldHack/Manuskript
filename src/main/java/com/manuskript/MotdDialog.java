package com.manuskript;

import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.Optional;

/**
 * Zeigt eine MOTD als Markdown ({@link MdTextArea}) und speichert Dismiss / Feature-Aus.
 */
public final class MotdDialog {

    private static final double CONTENT_MIN_WIDTH = 640;
    private static final double DIALOG_PREF_WIDTH = 560;
    private static final double DIALOG_PREF_HEIGHT = 480;

    private MotdDialog() {
    }

    /**
     * @return true wenn der Dialog angezeigt wurde
     */
    public static boolean showIfNeeded(Window owner, MotdService.Message message, int themeIndex) {
        if (!MotdService.shouldShow(message)) {
            return false;
        }
        int theme = themeIndex >= 0 ? themeIndex : 0;
        CustomAlert alert = new CustomAlert(CustomAlert.AlertType.INFORMATION);
        alert.setTitle("Nachricht");
        String header = message.title() != null && !message.title().isBlank()
                ? message.title().trim()
                : "Neuigkeiten von Manuskript";
        alert.setHeaderText(header);

        MdTextArea bodyArea = new MdTextArea(MdTextAreaOptions.builder()
                .editable(false)
                .showToolbar(false)
                .enableUndoRedo(false)
                .enableFontControls(false)
                .enableJustify(false)
                .enableBasicFormatting(false)
                .enableExtendedFormatting(false)
                .enableSearch(false)
                .enableReplace(false)
                .enableHideMarkupToggle(false)
                .hideMarkup(true)
                .showLineNumbers(false)
                .fontFamily("Arial")
                .fontSize(14)
                .themeIndex(theme)
                .build());
        bodyArea.setText(message.body() != null ? message.body() : "");
        bodyArea.getEditor().flushFormatAutoRules();
        bodyArea.applyTheme(theme);

        // Äußeres ScrollPane übernimmt V+H; Editor ohne eigene Vertikal-Scrollbar.
        bodyArea.getEditor().setEmbeddedFieldMode(true, null);
        bodyArea.setMinWidth(CONTENT_MIN_WIDTH);
        bodyArea.setPrefWidth(CONTENT_MIN_WIDTH);
        bodyArea.setMaxWidth(Double.MAX_VALUE);
        bodyArea.setMinHeight(120);
        bodyArea.setMaxHeight(Double.MAX_VALUE);

        ScrollPane scroll = new ScrollPane(bodyArea);
        scroll.getStyleClass().add("scroll-pane");
        scroll.setFitToWidth(false);
        scroll.setFitToHeight(false);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setPannable(true);
        scroll.setMinWidth(0);
        scroll.setMinHeight(120);
        scroll.setPrefHeight(280);
        scroll.setMaxWidth(Double.MAX_VALUE);
        scroll.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Runnable relayoutBody = () -> {
            Bounds viewport = scroll.getViewportBounds();
            double viewportW = viewport.getWidth() > 1 ? viewport.getWidth() : CONTENT_MIN_WIDTH;
            double viewportH = viewport.getHeight() > 1 ? viewport.getHeight() : 200;
            double width = Math.max(CONTENT_MIN_WIDTH, viewportW);
            bodyArea.setPrefWidth(width);
            bodyArea.setMinWidth(CONTENT_MIN_WIDTH);
            double contentHeight = bodyArea.getEditor().measurePreferredHeightForEmbeddedField(width);
            bodyArea.setPrefHeight(Math.max(contentHeight, viewportH));
        };
        scroll.viewportBoundsProperty().addListener((obs, oldB, newB) -> relayoutBody.run());
        bodyArea.widthProperty().addListener((obs, o, n) -> {
            if (n.doubleValue() > 1) {
                double contentHeight = bodyArea.getEditor()
                        .measurePreferredHeightForEmbeddedField(n.doubleValue());
                Bounds viewport = scroll.getViewportBounds();
                double viewportH = viewport.getHeight() > 1 ? viewport.getHeight() : contentHeight;
                bodyArea.setPrefHeight(Math.max(contentHeight, viewportH));
            }
        });

        CheckBox disableAll = new CheckBox("Nachrichten dauerhaft ausschalten");
        disableAll.setSelected(false);
        disableAll.setWrapText(true);
        disableAll.setMaxWidth(Double.MAX_VALUE);

        VBox box = new VBox(12, scroll, disableAll);
        box.setPadding(new Insets(4, 0, 0, 0));
        box.setMinWidth(0);
        box.setPrefWidth(DIALOG_PREF_WIDTH - 40);
        box.setMaxWidth(Double.MAX_VALUE);
        box.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(box, Priority.ALWAYS);

        alert.setCustomContent(box);
        alert.setContentText("");
        alert.setButtonTypes(ButtonType.OK);
        alert.setPrefSize(DIALOG_PREF_WIDTH, DIALOG_PREF_HEIGHT);
        alert.applyTheme(theme);

        Optional<ButtonType> result = alert.showAndWait(owner);
        if (result.isPresent() && result.get() == ButtonType.OK) {
            MotdService.markSeen(message.id());
            if (disableAll.isSelected()) {
                MotdService.setEnabled(false);
            }
        }
        return true;
    }
}
