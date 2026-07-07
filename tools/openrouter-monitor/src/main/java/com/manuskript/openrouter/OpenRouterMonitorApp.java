package com.manuskript.openrouter;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
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
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Eigenständiges JavaFX-Fenster für OpenRouter Credits und Logs.
 */
public class OpenRouterMonitorApp extends Application {

    private static String[] launchArgs = new String[0];

    public static void setLaunchArgs(String[] args) {
        launchArgs = args == null ? new String[0] : args.clone();
    }

    @Override
    public void start(Stage stage) {
        String[] args = mergeArgs(launchArgs, getParameters().getRaw().toArray(new String[0]));
        ManuskriptConfigLoader.OpenRouterConfig config = ManuskriptConfigLoader.load(args);
        if (!config.isUsable()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("OpenRouter Monitor");
            alert.setHeaderText("OpenRouter ist nicht konfiguriert");
            alert.setContentText(
                    config.describeProblem() + "\n\n"
                            + "Bitte in Manuskript die Parameter setzen (Parameter-Verwaltung):\n"
                            + "• agent.openai.api_url oder api.lektorat.base_url → https://openrouter.ai/api/v1\n"
                            + "• agent.openai.api_key oder api.lektorat.api_key → Ihr OpenRouter API-Key\n\n"
                            + "Gefunden: Key via " + config.keySource()
                            + (config.hasApiKey() ? " (gesetzt)" : " (leer)")
                            + ", URL via " + config.urlSource() + ": " + config.apiUrl() + "\n"
                            + "Konfigurationsverzeichnis: " + config.configRoot());
            alert.showAndWait();
            Platform.exit();
            return;
        }

        OpenRouterClient client = new OpenRouterClient(config.normalizedBaseUrl(), config.apiKey());
        String managementKey = MonitorKeyStore.loadManagementKey(config.configRoot());
        if (managementKey != null && !managementKey.isBlank()) {
            client.setManagementApiKey(managementKey);
        }

        ManagementKeyBar managementKeyBar = new ManagementKeyBar(client, config.configRoot(), getHostServices());
        CreditsPanel creditsPanel = new CreditsPanel(client);
        LogsPanel logsPanel = new LogsPanel(client);

        Tab creditsTab = new Tab("Credits", creditsPanel);
        creditsTab.setClosable(false);
        Tab logsTab = new Tab("Logs", logsPanel);
        logsTab.setClosable(false);

        TabPane tabPane = new TabPane(creditsTab, logsTab);
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        managementKeyBar.setOnSaved(key -> {
            creditsPanel.refresh();
            if (tabPane.getSelectionModel().getSelectedItem() == logsTab) {
                logsPanel.refresh();
            }
        });

        Button refreshButton = new Button("Neu laden");
        Runnable refreshSelectedTab = () -> {
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
            if (selected == null || selected.minutes() <= 0) {
                return;
            }
            Timeline timeline = new Timeline(
                    new KeyFrame(Duration.minutes(selected.minutes()), e -> refreshSelectedTab.run()));
            timeline.setCycleCount(Timeline.INDEFINITE);
            timeline.play();
            autoRefresh[0] = timeline;
        };
        autoRefreshCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> applyAutoRefresh.run());

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
            if (newTab == creditsTab) {
                creditsPanel.refresh();
            } else if (newTab == logsTab) {
                logsPanel.refresh();
            }
        });

        Scene scene = new Scene(root, 920, 620);
        stage.setTitle("OpenRouter Monitor");
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> {
            if (autoRefresh[0] != null) {
                autoRefresh[0].stop();
            }
            Platform.exit();
        });
        stage.show();

        creditsPanel.refresh();
    }

    public static void main(String[] args) {
        setLaunchArgs(args);
        launch(args);
    }

    private static String[] mergeArgs(String[] a, String[] b) {
        if (a == null || a.length == 0) return b == null ? new String[0] : b;
        if (b == null || b.length == 0) return a;
        String[] merged = new String[a.length + b.length];
        System.arraycopy(a, 0, merged, 0, a.length);
        System.arraycopy(b, 0, merged, a.length, b.length);
        return merged;
    }

    private record AutoRefreshOption(String label, int minutes) {
        @Override
        public String toString() {
            return label;
        }
    }
}
