package com.manuskript.openrouter;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.function.Consumer;

/**
 * Gemeinsame Credits/Logs-UI für Standalone-Start und In-Process-Plugin.
 */
public final class OpenRouterMonitorUi {

    private OpenRouterMonitorUi() {
    }

    public static Session create(ManuskriptConfigLoader.OpenRouterConfig config, Consumer<String> openUrl) {
        String apiUrl = config.isOpenRouter()
                ? config.normalizedBaseUrl()
                : "https://openrouter.ai/api/v1";

        OpenRouterClient client = new OpenRouterClient(apiUrl, config.apiKey());
        String managementKey = MonitorKeyStore.loadManagementKey(config.configRoot());
        if (managementKey != null && !managementKey.isBlank()) {
            client.setManagementApiKey(managementKey);
        }

        ManagementKeyBar managementKeyBar = new ManagementKeyBar(client, config.configRoot(), openUrl);
        CreditsPanel creditsPanel = new CreditsPanel(client);
        LogsPanel logsPanel = new LogsPanel(client);

        Tab creditsTab = new Tab("Credits", creditsPanel);
        creditsTab.setClosable(false);
        Tab logsTab = new Tab("Logs", logsPanel);
        logsTab.setClosable(false);

        TabPane tabPane = new TabPane(creditsTab, logsTab);
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Button refreshButton = new Button("Neu laden");
        Runnable refreshSelectedTab = () -> {
            if (!client.hasManagementApiKey()) {
                return;
            }
            Tab selected = tabPane.getSelectionModel().getSelectedItem();
            if (selected == creditsTab) {
                creditsPanel.refresh();
            } else if (selected == logsTab) {
                logsPanel.refresh();
            }
        };
        refreshButton.setOnAction(e -> refreshSelectedTab.run());

        Label autoRefreshLabel = new Label("Auto-Update:");
        ComboBox<AutoRefreshOption> autoRefreshCombo = new ComboBox<>();
        autoRefreshCombo.getItems().addAll(
                new AutoRefreshOption("Aus", 0),
                new AutoRefreshOption("1 Min", 1),
                new AutoRefreshOption("5 Min", 5),
                new AutoRefreshOption("10 Min", 10)
        );
        autoRefreshCombo.getSelectionModel().select(new AutoRefreshOption("1 Min", 1));
        autoRefreshCombo.setPrefWidth(110);

        final Timeline[] autoRefresh = {null};
        Runnable applyAutoRefresh = () -> {
            if (autoRefresh[0] != null) {
                autoRefresh[0].stop();
                autoRefresh[0] = null;
            }
            AutoRefreshOption selected = autoRefreshCombo.getSelectionModel().getSelectedItem();
            if (selected == null || selected.minutes() <= 0 || !client.hasManagementApiKey()) {
                return;
            }
            Timeline timeline = new Timeline(
                    new KeyFrame(Duration.minutes(selected.minutes()), e -> refreshSelectedTab.run()));
            timeline.setCycleCount(Timeline.INDEFINITE);
            timeline.play();
            autoRefresh[0] = timeline;
        };
        autoRefreshCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> applyAutoRefresh.run());

        managementKeyBar.setOnSaved(key -> {
            if (key != null && !key.isBlank()) {
                creditsPanel.refresh();
                if (tabPane.getSelectionModel().getSelectedItem() == logsTab) {
                    logsPanel.refresh();
                }
            }
            applyAutoRefresh.run();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox topBar = new HBox(12, autoRefreshLabel, autoRefreshCombo, spacer, refreshButton);
        topBar.setAlignment(Pos.CENTER_RIGHT);
        topBar.setPadding(new Insets(8, 12, 0, 12));

        applyAutoRefresh.run();

        BorderPane root = new BorderPane();
        VBox top = new VBox(managementKeyBar, topBar);
        root.setTop(top);
        root.setCenter(tabPane);

        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (!client.hasManagementApiKey()) {
                return;
            }
            if (newTab == creditsTab) {
                creditsPanel.refresh();
            } else if (newTab == logsTab) {
                logsPanel.refresh();
            }
        });

        if (client.hasManagementApiKey()) {
            creditsPanel.refresh();
        }

        return new Session(root, () -> {
            if (autoRefresh[0] != null) {
                autoRefresh[0].stop();
                autoRefresh[0] = null;
            }
        });
    }

    public record Session(Parent root, Runnable dispose) {
        public void close() {
            if (dispose != null) {
                dispose.run();
            }
        }
    }

    private record AutoRefreshOption(String label, int minutes) {
        @Override
        public String toString() {
            return label;
        }
    }
}
