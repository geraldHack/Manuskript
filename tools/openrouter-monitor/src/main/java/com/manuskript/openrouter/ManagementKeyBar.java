package com.manuskript.openrouter;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Eingabezeile für den OpenRouter Management-/Provisioning-Key (immer sichtbar).
 */
public class ManagementKeyBar extends VBox {

    private final OpenRouterClient client;
    private final Path configRoot;
    private final PasswordField managementKeyField = new PasswordField();
    private final Label statusLabel = new Label();
    private Consumer<String> onSaved;

    public ManagementKeyBar(OpenRouterClient client, Path configRoot, Consumer<String> openUrl) {
        this.client = client;
        this.configRoot = configRoot;
        setSpacing(6);
        setPadding(new Insets(10, 12, 10, 12));
        getStyleClass().add("management-key-bar");

        String savedKey = MonitorKeyStore.loadManagementKey(configRoot);
        if (savedKey != null && !savedKey.isBlank()) {
            client.setManagementApiKey(savedKey);
            managementKeyField.setText(savedKey);
        }

        Label hint = new Label(
                "Für Logs und Kontoguthaben: Management-/Provisioning-Key von OpenRouter "
                        + "(nicht der Inference-Key aus Manuskript).");
        hint.setWrapText(true);

        managementKeyField.setPromptText("sk-or-v1-… (Management Key)");
        managementKeyField.setPrefWidth(420);
        HBox.setHgrow(managementKeyField, Priority.ALWAYS);

        Button saveButton = new Button("Key speichern");
        saveButton.setOnAction(e -> save());

        Hyperlink helpLink = new Hyperlink("Key bei OpenRouter erstellen");
        helpLink.setOnAction(e -> {
            if (openUrl != null) {
                openUrl.accept("https://openrouter.ai/settings/management-keys");
            }
        });

        HBox row = new HBox(10,
                new Label("Management-Key:"),
                managementKeyField,
                saveButton,
                helpLink);
        row.setAlignment(Pos.CENTER_LEFT);

        statusLabel.setWrapText(true);
        updateStatus();

        getChildren().addAll(hint, row, statusLabel);
    }

    public void setOnSaved(Consumer<String> onSaved) {
        this.onSaved = onSaved;
    }

    public void save() {
        String key = managementKeyField.getText() == null ? "" : managementKeyField.getText().trim();
        client.setManagementApiKey(key);
        MonitorKeyStore.saveManagementKey(configRoot, key);
        updateStatus();
        if (onSaved != null) {
            onSaved.accept(key);
        }
    }

    private void updateStatus() {
        if (client.hasManagementApiKey()) {
            statusLabel.setText("Management-Key gespeichert.");
        } else {
            statusLabel.setText("Noch kein Management-Key gesetzt – Logs und Kontoguthaben sind erst danach verfügbar.");
        }
    }
}
