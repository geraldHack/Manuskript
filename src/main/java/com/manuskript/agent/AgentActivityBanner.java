package com.manuskript.agent;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * Statusleiste oben im Editor, sichtbar solange mindestens ein Agent arbeitet.
 */
public class AgentActivityBanner extends HBox {

    private final ProgressIndicator busyIndicator;
    private final Label messageLabel;

    public AgentActivityBanner(AgentActivityTracker tracker) {
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(10);
        setPadding(new Insets(6, 12, 6, 12));
        getStyleClass().add("agent-activity-banner");

        busyIndicator = new ProgressIndicator();
        busyIndicator.getStyleClass().add("agent-activity-spinner");
        busyIndicator.setPrefSize(20, 20);
        busyIndicator.setMinSize(20, 20);
        busyIndicator.setMaxSize(20, 20);

        messageLabel = new Label();
        messageLabel.getStyleClass().add("agent-activity-message");
        messageLabel.setWrapText(true);
        HBox.setHgrow(messageLabel, Priority.ALWAYS);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        getChildren().addAll(busyIndicator, messageLabel, spacer);
        setVisible(false);
        setManaged(false);

        tracker.addListener(this::applyState);
    }

    private void applyState(AgentActivityTracker.State state) {
        if (state.active()) {
            messageLabel.setText(state.message());
            setVisible(true);
            setManaged(true);
        } else {
            setVisible(false);
            setManaged(false);
        }
    }
}
