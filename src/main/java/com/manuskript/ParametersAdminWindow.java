package com.manuskript;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Verwaltung aller Parameter (parameters.properties / User Preferences).
 * Pro Eintrag: Schlüssel, Wert, Hilfetext. Optisch gruppiert nach Kategorien.
 */
public class ParametersAdminWindow {

    private CustomStage stage;
    private final Window owner;
    private final Map<String, Control> keyToControl = new HashMap<>();
    private final Map<String, ParameterDef> keyToDef = new HashMap<>();

    public ParametersAdminWindow(Window owner) {
        this.owner = owner;
    }

    public static void show(Window owner) {
        ParametersAdminWindow w = new ParametersAdminWindow(owner);
        w.initializeWindow();
        w.stage.show();
    }

    private void initializeWindow() {
        stage = StageManager.createStage("Parameter-Verwaltung");
        if (owner != null && owner instanceof javafx.stage.Stage) {
            stage.initOwner(owner);
        }
        stage.setMinWidth(720);
        stage.setMinHeight(520);
        stage.setWidth(820);
        stage.setHeight(620);

        int theme = java.util.prefs.Preferences.userNodeForPackage(MainController.class).getInt("main_window_theme", 0);

        TabPane tabPane = new TabPane();
        tabPane.getStyleClass().add("tab-pane");
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        for (String category : ParameterRegistry.getCategories()) {
            List<ParameterDef> params = ParameterRegistry.getByCategory(category);
            if (params.isEmpty()) continue;

            if ("Online-Lektorat".equals(category)) {
                VBox lektoratContent = buildOnlineLektoratTab(keyToControl, theme);
                for (ParameterDef def : params) {
                    keyToDef.put(def.getKey(), def);
                }
                ScrollPane scroll = new ScrollPane(lektoratContent);
                scroll.setFitToWidth(true);
                scroll.getStyleClass().add("param-tab-scroll");
                Tab tab = new Tab(category, scroll);
                tab.setClosable(false);
                tabPane.getTabs().add(tab);
                continue;
            }

            ScrollPane scroll = new ScrollPane();
            scroll.setFitToWidth(true);
            scroll.getStyleClass().add("param-tab-scroll");
            VBox content = new VBox(12);
            content.setPadding(new Insets(16));
            content.getStyleClass().addAll(getThemeStyleClasses(theme));
            for (ParameterDef def : params) {
                keyToDef.put(def.getKey(), def);
                Control control = createControl(def);
                keyToControl.put(def.getKey(), control);
                Label keyLabel = new Label(def.getKey());
                keyLabel.getStyleClass().add("param-key-label");
                Label helpLabel = new Label(def.getHelpText());
                helpLabel.getStyleClass().add("param-help-label");
                helpLabel.setWrapText(true);
                helpLabel.setMaxWidth(680);
                VBox card = new VBox(4);
                card.getStyleClass().add("param-card");
                card.getChildren().addAll(keyLabel, control, helpLabel);
                content.getChildren().add(card);
            }
            scroll.setContent(content);
            Tab tab = new Tab(category, scroll);
            tab.setClosable(false);
            tabPane.getTabs().add(tab);
        }

        Button btnSave = new Button("Speichern");
        btnSave.setDefaultButton(true);
        btnSave.setOnAction(e -> saveAll());
        Button btnRestore = new Button("Standard wiederherstellen");
        btnRestore.setOnAction(e -> restoreDefaults());
        HBox buttons = new HBox(12);
        buttons.getChildren().addAll(btnSave, btnRestore);
        buttons.setAlignment(Pos.CENTER_LEFT);
        buttons.setPadding(new Insets(10, 16, 16, 16));

        VBox root = new VBox();
        root.getStyleClass().addAll(getThemeStyleClasses(theme));
        root.getChildren().addAll(tabPane, buttons);
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        Scene scene = new Scene(root);
        String cssPath = ResourceManager.getCssResource("css/manuskript.css");
        if (cssPath != null) scene.getStylesheets().add(cssPath);
        stage.setTitleBarTheme(theme);
        stage.setSceneWithTitleBar(scene);
        stage.setFullTheme(theme);
    }

    private static List<String> getThemeStyleClasses(int themeIndex) {
        switch (themeIndex) {
            case 0: return java.util.Collections.singletonList("weiss-theme");
            case 1: return java.util.Collections.singletonList("theme-dark");
            case 2: return java.util.Collections.singletonList("pastell-theme");
            case 3: return java.util.Arrays.asList("theme-dark", "blau-theme");
            case 4: return java.util.Arrays.asList("theme-dark", "gruen-theme");
            case 5: return java.util.Arrays.asList("theme-dark", "lila-theme");
            default: return java.util.Collections.singletonList("weiss-theme");
        }
    }

    private VBox buildOnlineLektoratTab(Map<String, Control> keyToControl, int theme) {
        VBox content = new VBox(12);
        content.setPadding(new Insets(16));
        content.getStyleClass().addAll(getThemeStyleClasses(theme));

        String apiKey = ResourceManager.getParameter("api.lektorat.api_key", "");
        String baseUrl = ResourceManager.getParameter("api.lektorat.base_url", "https://api.openai.com/v1");
        String model = ResourceManager.getParameter("api.lektorat.model", "gpt-4o-mini");
        String extraPrompt = ResourceManager.getParameter("api.lektorat.extra_prompt", "");
        String lektoratType = ResourceManager.getParameter("api.lektorat.type", "allgemein");

        PasswordField apiKeyField = new PasswordField();
        apiKeyField.setText(apiKey != null ? apiKey : "");
        apiKeyField.setPrefWidth(400);
        Label apiKeyLabel = new Label("api.lektorat.api_key");
        apiKeyLabel.getStyleClass().add("param-key-label");
        Label apiKeyHelp = new Label("API-Key für die Online-Lektorat-API (z. B. OpenAI).");
        apiKeyHelp.getStyleClass().add("param-help-label");
        apiKeyHelp.setWrapText(true);
        apiKeyHelp.setMaxWidth(680);
        VBox apiKeyCard = new VBox(4);
        apiKeyCard.getStyleClass().add("param-card");
        apiKeyCard.getChildren().addAll(apiKeyLabel, apiKeyField, apiKeyHelp);
        content.getChildren().add(apiKeyCard);
        keyToControl.put("api.lektorat.api_key", apiKeyField);

        TextField baseUrlField = new TextField(baseUrl != null ? baseUrl : "");
        baseUrlField.setPrefWidth(400);
        Label baseUrlLabel = new Label("api.lektorat.base_url");
        baseUrlLabel.getStyleClass().add("param-key-label");
        Label baseUrlHelp = new Label("Basis-URL der API (z. B. https://api.openai.com/v1).");
        baseUrlHelp.getStyleClass().add("param-help-label");
        baseUrlHelp.setWrapText(true);
        baseUrlHelp.setMaxWidth(680);
        VBox baseUrlCard = new VBox(4);
        baseUrlCard.getStyleClass().add("param-card");
        baseUrlCard.getChildren().addAll(baseUrlLabel, baseUrlField, baseUrlHelp);
        content.getChildren().add(baseUrlCard);
        keyToControl.put("api.lektorat.base_url", baseUrlField);

        ComboBox<String> modelCombo = new ComboBox<>();
        modelCombo.setEditable(true);
        modelCombo.setPrefWidth(400);
        modelCombo.getEditor().setText(model != null ? model : "");
        modelCombo.setPromptText("Modell wählen oder eingeben");
        Label modelLabel = new Label("api.lektorat.model");
        modelLabel.getStyleClass().add("param-key-label");
        Label modelHelp = new Label("Modell für das Lektorat (nach „Modelle laden“ auswählen oder frei eingeben).");
        modelHelp.getStyleClass().add("param-help-label");
        modelHelp.setWrapText(true);
        modelHelp.setMaxWidth(680);
        Button btnLoadModels = new Button("Modelle laden");
        btnLoadModels.setOnAction(e -> loadLektoratModels(apiKeyField.getText(), baseUrlField.getText(), modelCombo));
        HBox modelRow = new HBox(10);
        modelRow.getChildren().addAll(modelCombo, btnLoadModels);
        modelRow.setAlignment(Pos.CENTER_LEFT);
        VBox modelCard = new VBox(4);
        modelCard.getStyleClass().add("param-card");
        modelCard.getChildren().addAll(modelLabel, modelRow, modelHelp);
        content.getChildren().add(modelCard);
        keyToControl.put("api.lektorat.model", modelCombo);

        // Zusatzprompt (Textarea)
        TextArea extraPromptArea = new TextArea(extraPrompt != null ? extraPrompt : "");
        extraPromptArea.setPrefRowCount(4);
        extraPromptArea.setWrapText(true);
        extraPromptArea.setPrefWidth(680);
        extraPromptArea.setMaxWidth(Double.MAX_VALUE);
        Label extraPromptLabel = new Label("api.lektorat.extra_prompt");
        extraPromptLabel.getStyleClass().add("param-key-label");
        Label extraPromptHelp = new Label("Zusätzlicher Prompt (z. B. Stil-Anweisungen), wird an den Lektorat-Prompt angehängt.");
        extraPromptHelp.getStyleClass().add("param-help-label");
        extraPromptHelp.setWrapText(true);
        extraPromptHelp.setMaxWidth(680);
        VBox extraPromptCard = new VBox(4);
        extraPromptCard.getStyleClass().add("param-card");
        extraPromptCard.getChildren().addAll(extraPromptLabel, extraPromptArea, extraPromptHelp);
        content.getChildren().add(extraPromptCard);
        keyToControl.put("api.lektorat.extra_prompt", extraPromptArea);

        // Lektorat-Typ (Toggles)
        TextField typeField = new TextField(lektoratType != null ? lektoratType : "allgemein");
        typeField.setMaxWidth(0);
        typeField.setMinWidth(0);
        typeField.setOpacity(0);
        typeField.setFocusTraversable(false);
        ToggleGroup typeGroup = new ToggleGroup();
        typeField.setUserData(typeGroup);
        RadioButton rbAllgemein = new RadioButton("Allgemein");
        rbAllgemein.setToggleGroup(typeGroup);
        rbAllgemein.setUserData("allgemein");
        rbAllgemein.setOnAction(e -> typeField.setText("allgemein"));
        RadioButton rbStil = new RadioButton("Stil");
        rbStil.setToggleGroup(typeGroup);
        rbStil.setUserData("stil");
        rbStil.setOnAction(e -> typeField.setText("stil"));
        RadioButton rbGrammatik = new RadioButton("Grammatik");
        rbGrammatik.setToggleGroup(typeGroup);
        rbGrammatik.setUserData("grammatik");
        rbGrammatik.setOnAction(e -> typeField.setText("grammatik"));
        RadioButton rbPlot = new RadioButton("Plot / Dramaturgie");
        rbPlot.setToggleGroup(typeGroup);
        rbPlot.setUserData("plot");
        rbPlot.setOnAction(e -> typeField.setText("plot"));
        for (Toggle t : typeGroup.getToggles()) {
            if (lektoratType != null && lektoratType.equals(t.getUserData())) {
                typeGroup.selectToggle(t);
                break;
            }
        }
        if (typeGroup.getSelectedToggle() == null) {
            typeGroup.selectToggle(rbAllgemein);
            typeField.setText("allgemein");
        }
        HBox typeRow = new HBox(12);
        typeRow.getChildren().addAll(rbAllgemein, rbStil, rbGrammatik, rbPlot);
        typeRow.setAlignment(Pos.CENTER_LEFT);
        Label typeLabel = new Label("api.lektorat.type");
        typeLabel.getStyleClass().add("param-key-label");
        Label typeHelp = new Label("Art des Lektorats.");
        typeHelp.getStyleClass().add("param-help-label");
        typeHelp.setWrapText(true);
        typeHelp.setMaxWidth(680);
        VBox typeCard = new VBox(4);
        typeCard.getStyleClass().add("param-card");
        typeCard.getChildren().addAll(typeLabel, typeRow, typeField, typeHelp);
        content.getChildren().add(typeCard);
        keyToControl.put("api.lektorat.type", typeField);

        // Chunk-Größe (Zeichen pro API-Anfrage)
        String chunkSizeStr = ResourceManager.getParameter("api.lektorat.chunk_size", "12000");
        int chunkSizeVal = parseInt(chunkSizeStr, 12000);
        chunkSizeVal = Math.max(1000, Math.min(100000, chunkSizeVal));
        Spinner<Integer> chunkSizeSpinner = new Spinner<>(1000, 100000, chunkSizeVal);
        chunkSizeSpinner.setEditable(true);
        chunkSizeSpinner.setPrefWidth(180);
        Label chunkSizeLabel = new Label("api.lektorat.chunk_size");
        chunkSizeLabel.getStyleClass().add("param-key-label");
        Label chunkSizeHelp = new Label("Max. Zeichen pro API-Anfrage. Längere Kapitel werden in mehrere Abschnitte geteilt. Größer = weniger Anfragen (schneller), bei langsamen Modellen/Gateways aber evtl. Timeout. Kleiner = mehr Anfragen (robuster). Typisch 5000–15000.");
        chunkSizeHelp.getStyleClass().add("param-help-label");
        chunkSizeHelp.setWrapText(true);
        chunkSizeHelp.setMaxWidth(680);
        VBox chunkSizeCard = new VBox(4);
        chunkSizeCard.getStyleClass().add("param-card");
        chunkSizeCard.getChildren().addAll(chunkSizeLabel, chunkSizeSpinner, chunkSizeHelp);
        content.getChildren().add(chunkSizeCard);
        keyToControl.put("api.lektorat.chunk_size", chunkSizeSpinner);

        // Pause zwischen Abschnitten (ms)
        String delayStr = ResourceManager.getParameter("api.lektorat.delay_between_chunks_ms", "1500");
        int delayVal = parseInt(delayStr, 1500);
        delayVal = Math.max(0, Math.min(30000, delayVal));
        Spinner<Integer> delaySpinner = new Spinner<>(0, 30000, delayVal);
        delaySpinner.setEditable(true);
        delaySpinner.setPrefWidth(180);
        Label delayLabel = new Label("api.lektorat.delay_between_chunks_ms");
        delayLabel.getStyleClass().add("param-key-label");
        Label delayHelp = new Label("Pause in Millisekunden zwischen zwei Abschnitts-Anfragen. Viele Gateways verursachen sonst beim sofortigen Folgerequest einen Timeout; 1000–2000 ms behebt das oft. 0 = keine Pause.");
        delayHelp.getStyleClass().add("param-help-label");
        delayHelp.setWrapText(true);
        delayHelp.setMaxWidth(680);
        VBox delayCard = new VBox(4);
        delayCard.getStyleClass().add("param-card");
        delayCard.getChildren().addAll(delayLabel, delaySpinner, delayHelp);
        content.getChildren().add(delayCard);
        keyToControl.put("api.lektorat.delay_between_chunks_ms", delaySpinner);

        // Request-Timeout (Sekunden)
        String timeoutStr = ResourceManager.getParameter("api.lektorat.request_timeout_sec", "300");
        int timeoutVal = parseInt(timeoutStr, 300);
        timeoutVal = Math.max(60, Math.min(900, timeoutVal));
        Spinner<Integer> timeoutSpinner = new Spinner<>(60, 900, timeoutVal);
        timeoutSpinner.setEditable(true);
        timeoutSpinner.setPrefWidth(180);
        Label timeoutLabel = new Label("api.lektorat.request_timeout_sec");
        timeoutLabel.getStyleClass().add("param-key-label");
        Label timeoutHelp = new Label("Timeout pro API-Anfrage in Sekunden (60–900). Bei großen Abschnitten oder langsamen Modellen erhöhen (z. B. 300–600), wenn sonst Timeouts auftreten.");
        timeoutHelp.getStyleClass().add("param-help-label");
        timeoutHelp.setWrapText(true);
        timeoutHelp.setMaxWidth(680);
        VBox timeoutCard = new VBox(4);
        timeoutCard.getStyleClass().add("param-card");
        timeoutCard.getChildren().addAll(timeoutLabel, timeoutSpinner, timeoutHelp);
        content.getChildren().add(timeoutCard);
        keyToControl.put("api.lektorat.request_timeout_sec", timeoutSpinner);

        return content;
    }

    private void loadLektoratModels(String apiKey, String baseUrl, ComboBox<String> modelCombo) {
        if (apiKey == null || apiKey.isBlank() || baseUrl == null || baseUrl.isBlank()) {
            showInfo("Eingabe fehlt", "Bitte API-Key und Basis-URL eintragen.");
            return;
        }
        String url = baseUrl.replaceAll("/$", "") + "/models";
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + apiKey.trim())
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();
        CompletableFuture.supplyAsync(() -> {
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() != 200) {
                    return "Fehler: HTTP " + response.statusCode();
                }
                return response.body();
            } catch (Exception ex) {
                return "Fehler: " + ex.getMessage();
            }
        }).thenAccept(result -> {
            Platform.runLater(() -> {
                if (result.startsWith("Fehler:")) {
                    showInfo("Modelle laden", result);
                    return;
                }
                try {
                    com.google.gson.Gson gson = new com.google.gson.Gson();
                    com.google.gson.JsonObject root = gson.fromJson(result, com.google.gson.JsonObject.class);
                    com.google.gson.JsonArray data = root != null && root.has("data") ? root.getAsJsonArray("data") : null;
                    modelCombo.getItems().clear();
                    if (data != null) {
                        for (int i = 0; i < data.size(); i++) {
                            com.google.gson.JsonElement el = data.get(i);
                            if (el.isJsonObject() && el.getAsJsonObject().has("id")) {
                                modelCombo.getItems().add(el.getAsJsonObject().get("id").getAsString());
                            }
                        }
                    }
                    if (modelCombo.getItems().isEmpty()) {
                        showInfo("Modelle laden", "Keine Modelle in der Antwort gefunden.");
                    } else {
                        showInfo("Modelle laden", modelCombo.getItems().size() + " Modelle geladen. Bitte Modell auswählen und Speichern klicken.");
                    }
                } catch (Exception e) {
                    showInfo("Modelle laden", "Antwort konnte nicht gelesen werden: " + e.getMessage());
                }
            });
        });
    }

    private Control createControl(ParameterDef def) {
        boolean isTextanalyse = "Textanalyse".equals(def.getCategory());
        String current = isTextanalyse
                ? ResourceManager.getTextanalysisParameter(def.getKey(), def.getDefaultValue())
                : ResourceManager.getParameter(def.getKey(), def.getDefaultValue());
        switch (def.getType()) {
            case BOOLEAN:
                CheckBox cb = new CheckBox();
                cb.setSelected(Boolean.parseBoolean(current));
                cb.setMaxWidth(400);
                return cb;
            case INT:
                Spinner<Integer> si = new Spinner<>(Integer.MIN_VALUE, Integer.MAX_VALUE, parseInt(current, 0));
                si.setEditable(true);
                si.setPrefWidth(180);
                return si;
            case DOUBLE:
                Spinner<Double> sd = new Spinner<>(-1e6, 1e6, parseDouble(current, 0.0));
                sd.setEditable(true);
                sd.setPrefWidth(180);
                return sd;
            default:
                if (isTextanalyse) {
                    TextArea ta = new TextArea(current != null ? current : "");
                    ta.setPrefRowCount(4);
                    ta.setWrapText(true);
                    ta.setPrefWidth(680);
                    ta.setMaxWidth(Double.MAX_VALUE);
                    return ta;
                }
                TextField tf = new TextField(current != null ? current : "");
                tf.setPrefWidth(400);
                return tf;
        }
    }

    private static int parseInt(String s, int fallback) {
        if (s == null || s.isBlank()) return fallback;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return fallback; }
    }

    private static double parseDouble(String s, double fallback) {
        if (s == null || s.isBlank()) return fallback;
        try { return Double.parseDouble(s.trim().replace(',', '.')); } catch (NumberFormatException e) { return fallback; }
    }

    private void saveAll() {
        for (Map.Entry<String, Control> e : keyToControl.entrySet()) {
            String key = e.getKey();
            ParameterDef def = keyToDef.get(key);
            if (def == null) continue;
            String value = getValueFromControl(e.getValue(), def);
            if ("Textanalyse".equals(def.getCategory())) {
                ResourceManager.saveTextanalysisParameter(key, value);
            } else {
                ResourceManager.saveParameter(key, value);
            }
        }
        showInfo("Gespeichert", "Alle Parameter wurden gespeichert.");
    }

    private String getValueFromControl(Control c, ParameterDef def) {
        if (c instanceof CheckBox) return String.valueOf(((CheckBox) c).isSelected());
        if (c instanceof Spinner) {
            Object v = ((Spinner<?>) c).getValue();
            return v != null ? v.toString() : def.getDefaultValue();
        }
        if (c instanceof TextArea) return ((TextArea) c).getText();
        if (c instanceof ComboBox) {
            ComboBox<?> cb = (ComboBox<?>) c;
            Object v = cb.getValue();
            if (v != null) return v.toString();
            if (cb.isEditable() && cb.getEditor() != null) return cb.getEditor().getText();
            return def.getDefaultValue();
        }
        if (c instanceof TextField) return ((TextField) c).getText();
        return def.getDefaultValue();
    }

    private void restoreDefaults() {
        for (Map.Entry<String, Control> e : keyToControl.entrySet()) {
            ParameterDef def = keyToDef.get(e.getKey());
            if (def == null) continue;
            setControlToDefault(e.getValue(), def);
        }
        showInfo("Standard", "Alle Werte auf Standard zurückgesetzt. Bitte „Speichern“ klicken, um zu übernehmen.");
    }

    @SuppressWarnings("unchecked")
    private void setControlToDefault(Control c, ParameterDef def) {
        String d = def.getDefaultValue();
        if (c instanceof CheckBox) ((CheckBox) c).setSelected(Boolean.parseBoolean(d));
        else if (c instanceof Spinner) {
            Spinner<?> s = (Spinner<?>) c;
            if (s.getValue() instanceof Integer)
                ((Spinner<Integer>) s).getValueFactory().setValue(parseInt(d, 0));
            else
                ((Spinner<Double>) s).getValueFactory().setValue(parseDouble(d, 0.0));
        } else if (c instanceof TextArea) ((TextArea) c).setText(d != null ? d : "");
        else if (c instanceof ComboBox) {
            ComboBox<String> cb = (ComboBox<String>) c;
            cb.getSelectionModel().clearSelection();
            if (cb.isEditable() && cb.getEditor() != null) cb.getEditor().setText(d != null ? d : "");
        } else if (c instanceof TextField) {
            TextField tf = (TextField) c;
            tf.setText(d != null ? d : "");
            if (tf.getUserData() instanceof ToggleGroup) {
                ToggleGroup g = (ToggleGroup) tf.getUserData();
                for (Toggle t : g.getToggles())
                    if (d != null && d.equals(t.getUserData())) { g.selectToggle(t); break; }
            }
        }
    }

    private void showInfo(String title, String message) {
        Platform.runLater(() -> {
            int theme = java.util.prefs.Preferences.userNodeForPackage(MainController.class).getInt("main_window_theme", 0);
            CustomAlert a = new CustomAlert(Alert.AlertType.INFORMATION, title);
            a.setHeaderText(null);
            a.setContentText(message);
            a.applyTheme(theme);
            a.initOwner(stage);
            a.showAndWait();
        });
    }
}
