package com.manuskript.dictation;

import com.manuskript.CustomAlert;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Fehlerdialog für Diktat — nicht in der schmalen Statuszeile.
 */
public final class DictationErrorDialog {

    private DictationErrorDialog() {
    }

    public static void show(Window owner, int themeIndex, String header, String detail) {
        if (detail == null || detail.isBlank()) {
            detail = "Unbekannter Fehler.";
        }
        String title = header != null && !header.isBlank() ? header : "Diktat";

        CustomAlert alert = new CustomAlert(CustomAlert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(title);

        if (detail.length() <= 220 && !detail.contains("\n")) {
            alert.setContentText(detail);
        } else {
            TextArea area = new TextArea(detail);
            area.setEditable(false);
            area.setWrapText(true);
            area.setPrefRowCount(Math.min(14, detail.split("\\R").length + 1));
            area.setPrefWidth(520);

            ScrollPane scroll = new ScrollPane(area);
            scroll.setFitToWidth(true);
            scroll.setPrefViewportHeight(Math.min(320, area.getPrefRowCount() * 22.0 + 12));

            VBox box = new VBox(scroll);
            VBox.setVgrow(scroll, Priority.ALWAYS);
            alert.setCustomContent(box);
        }

        alert.applyTheme(themeIndex);
        if (owner != null) {
            alert.initOwner(owner);
        }
        if (owner instanceof Stage ownerStage) {
            alert.showAndWait(ownerStage);
        } else {
            alert.showAndWait();
        }
    }
}
