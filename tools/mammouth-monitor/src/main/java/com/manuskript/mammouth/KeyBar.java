package com.manuskript.mammouth;

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
 * API-Key-Zeile für den Mammouth-Monitor.
 */
public class KeyBar extends VBox {

    private final MammouthClient client;
    private final Path configRoot;
    private final PasswordField keyField = new PasswordField();
    private final Label statusLabel = new Label();
    private Consumer<String> onSaved;

    public KeyBar(MammouthClient client, Path configRoot, String initialKey, String keySource, Consumer<String> openUrl) {
        this.client = client;
        this.configRoot = configRoot;
        setSpacing(6);
        setPadding(new Insets(10, 12, 10, 12));
        getStyleClass().add("management-key-bar");

        String key = initialKey == null ? "" : initialKey.trim();
        if (!key.isBlank()) {
            client.setApiKey(key);
            keyField.setText(key);
        }

        Label hint = new Label(
                "Mammouth-API-Key (derselbe Key wie in Manuskript). Credits kommen von GET /key/info, "
                        + "Modelle von /public/models. Dashboard: mammouth.ai/app/account/settings/api");
        hint.setWrapText(true);

        keyField.setPromptText("Mammouth API-Key");
        keyField.setPrefWidth(420);
        HBox.setHgrow(keyField, Priority.ALWAYS);

        Button saveButton = new Button("Key speichern");
        saveButton.setOnAction(e -> save());

        Hyperlink helpLink = new Hyperlink("API-Einstellungen öffnen");
        helpLink.setOnAction(e -> {
            if (openUrl != null) {
                openUrl.accept(MammouthClient.DASHBOARD_URL);
            }
        });

        HBox row = new HBox(10, new Label("API-Key:"), keyField, saveButton, helpLink);
        row.setAlignment(Pos.CENTER_LEFT);

        statusLabel.setWrapText(true);
        updateStatus(keySource);

        getChildren().addAll(hint, row, statusLabel);
    }

    public void setOnSaved(Consumer<String> onSaved) {
        this.onSaved = onSaved;
    }

    public void save() {
        String key = keyField.getText() == null ? "" : keyField.getText().trim();
        client.setApiKey(key);
        MammouthKeyStore.saveOverrideKey(configRoot, key);
        updateStatus("Monitor");
        if (onSaved != null) {
            onSaved.accept(key);
        }
    }

    private void updateStatus(String source) {
        if (client.hasApiKey()) {
            statusLabel.setText("API-Key gesetzt" + (source == null || source.isBlank() || "—".equals(source)
                    ? "." : " (" + source + ")."));
        } else {
            statusLabel.setText("Noch kein API-Key — Credits sind erst danach verfügbar. Modelle laden ohne Key.");
        }
    }
}
