package com.manuskript;

import com.manuskript.agent.FilterableModelOptionSelector;
import com.manuskript.agent.ModelOption;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Verwaltung aller Parameter (parameters.properties / User Preferences).
 * Pro Eintrag: Schlüssel, Wert, Hilfetext. Optisch gruppiert nach Kategorien.
 */
public class ParametersAdminWindow {

    private static final Logger logger = LoggerFactory.getLogger(ParametersAdminWindow.class);

    private CustomStage stage;
    private final Window owner;
    private final Map<String, Parent> keyToControl = new HashMap<>();
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
        tabPane.setMinHeight(0);

        for (String category : ParameterRegistry.getCategories()) {
            List<ParameterDef> params = ParameterRegistry.getByCategory(category);
            if (params.isEmpty()) continue;

            if ("Online-Lektorat".equals(category)) {
                VBox lektoratContent = buildOnlineLektoratTab(keyToControl, theme);
                for (ParameterDef def : params) {
                    keyToDef.put(def.getKey(), def);
                }
                ScrollPane scroll = createParamTabScroll(lektoratContent);
                Tab tab = new Tab(category, scroll);
                tab.setClosable(false);
                tabPane.getTabs().add(tab);
                continue;
            }

            if ("Agenten".equals(category)) {
                VBox agentenContent = buildAgentenTab(keyToControl, theme);
                for (ParameterDef def : params) {
                    keyToDef.put(def.getKey(), def);
                }
                ScrollPane scroll = createParamTabScroll(agentenContent);
                Tab tab = new Tab(category, scroll);
                tab.setClosable(false);
                tabPane.getTabs().add(tab);
                continue;
            }

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
            ScrollPane scroll = createParamTabScroll(content);
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

    private static ScrollPane createParamTabScroll(VBox content) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setMinHeight(0);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("param-tab-scroll");
        return scroll;
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

    private VBox buildAgentenTab(Map<String, Parent> keyToControl, int theme) {
        VBox content = new VBox(12);
        content.setPadding(new Insets(16));
        content.getStyleClass().addAll(getThemeStyleClasses(theme));

        String backendType = ResourceManager.getParameter("agent.backend", "Ollama");

        // Allgemeine Agenten-Parameter
        boolean agentEnabled = Boolean.parseBoolean(ResourceManager.getParameter("agent.enabled", "true"));
        CheckBox enabledCheck = new CheckBox("Plothole-Agent aktivieren");
        enabledCheck.setSelected(agentEnabled);
        enabledCheck.setMaxWidth(400);
        Label enabledLabel = new Label("agent.enabled");
        enabledLabel.getStyleClass().add("param-key-label");
        Label enabledHelp = new Label("Plothole-Agent komplett aktivieren.");
        enabledHelp.getStyleClass().add("param-help-label");
        enabledHelp.setWrapText(true);
        enabledHelp.setMaxWidth(680);
        VBox enabledCard = new VBox(4);
        enabledCard.getStyleClass().add("param-card");
        enabledCard.getChildren().addAll(enabledLabel, enabledCheck, enabledHelp);
        content.getChildren().add(enabledCard);
        keyToControl.put("agent.enabled", enabledCheck);

        // Backend-Auswahl
        ComboBox<String> backendCombo = new ComboBox<>();
        backendCombo.getItems().addAll("Ollama", "OpenAI");
        backendCombo.setValue(backendType);
        backendCombo.setPrefWidth(200);
        Label backendLabel = new Label("agent.backend");
        backendLabel.getStyleClass().add("param-key-label");
        Label backendHelp = new Label("KI-Backend fuer die Agenten-Analyse.");
        backendHelp.getStyleClass().add("param-help-label");
        backendHelp.setWrapText(true);
        backendHelp.setMaxWidth(680);
        VBox backendCard = new VBox(4);
        backendCard.getStyleClass().add("param-card");
        backendCard.getChildren().addAll(backendLabel, backendCombo, backendHelp);
        content.getChildren().add(backendCard);
        keyToControl.put("agent.backend", backendCombo);

        // Container für Backend-spezifische Parameter
        VBox ollamaParams = new VBox(8);
        VBox openaiParams = new VBox(8);

        // Ollama-spezifische Parameter
        Label ollamaHeader = new Label("Ollama-Einstellungen");
        ollamaHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-padding: 8 0 0 0;");
        ollamaParams.getChildren().add(ollamaHeader);

        String ollamaUrl = ResourceManager.getParameter("agent.ollama.api_url", "http://localhost:11434");
        TextField ollamaUrlField = new TextField(ollamaUrl);
        ollamaUrlField.setPrefWidth(400);
        Label ollamaUrlLabel = new Label("agent.ollama.api_url");
        ollamaUrlLabel.getStyleClass().add("param-key-label");
        Label ollamaUrlHelp = new Label("Basis-URL des Ollama-Servers.");
        ollamaUrlHelp.getStyleClass().add("param-help-label");
        ollamaUrlHelp.setWrapText(true);
        ollamaUrlHelp.setMaxWidth(680);
        VBox ollamaUrlCard = new VBox(4);
        ollamaUrlCard.getStyleClass().add("param-card");
        ollamaUrlCard.getChildren().addAll(ollamaUrlLabel, ollamaUrlField, ollamaUrlHelp);
        ollamaParams.getChildren().add(ollamaUrlCard);
        keyToControl.put("agent.ollama.api_url", ollamaUrlField);

        String ollamaModel = ResourceManager.getParameter("agent.ollama.model", "gemma3:4b");
        TextField ollamaModelField = new TextField(ollamaModel);
        ollamaModelField.setPrefWidth(400);
        Label ollamaModelLabel = new Label("agent.ollama.model");
        ollamaModelLabel.getStyleClass().add("param-key-label");
        Label ollamaModelHelp = new Label("Modell fuer die Ollama-Analyse (z.B. gemma3:4b, llama3, mistral).");
        ollamaModelHelp.getStyleClass().add("param-help-label");
        ollamaModelHelp.setWrapText(true);
        ollamaModelHelp.setMaxWidth(680);
        VBox ollamaModelCard = new VBox(4);
        ollamaModelCard.getStyleClass().add("param-card");
        ollamaModelCard.getChildren().addAll(ollamaModelLabel, ollamaModelField, ollamaModelHelp);
        ollamaParams.getChildren().add(ollamaModelCard);
        keyToControl.put("agent.ollama.model", ollamaModelField);

        // OpenAI-spezifische Parameter
        Label openaiHeader = new Label("OpenAI-Einstellungen");
        openaiHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-padding: 8 0 0 0;");
        openaiParams.getChildren().add(openaiHeader);

        String openaiKey = ResourceManager.getParameter("agent.openai.api_key", "");
        TextField openaiKeyField = new TextField(openaiKey);
        openaiKeyField.setPrefWidth(400);
        Label openaiKeyLabel = new Label("agent.openai.api_key");
        openaiKeyLabel.getStyleClass().add("param-key-label");
        Label openaiKeyHelp = new Label("API-Key fuer OpenAI (wird auch vom Online-Lektorat verwendet).");
        openaiKeyHelp.getStyleClass().add("param-help-label");
        openaiKeyHelp.setWrapText(true);
        openaiKeyHelp.setMaxWidth(680);
        VBox openaiKeyCard = new VBox(4);
        openaiKeyCard.getStyleClass().add("param-card");
        openaiKeyCard.getChildren().addAll(openaiKeyLabel, openaiKeyField, openaiKeyHelp);
        openaiParams.getChildren().add(openaiKeyCard);
        keyToControl.put("agent.openai.api_key", openaiKeyField);

        String openaiUrl = ResourceManager.getParameter("agent.openai.api_url", "https://api.openai.com/v1");
        TextField openaiUrlField = new TextField(openaiUrl);
        openaiUrlField.setPrefWidth(400);
        Label openaiUrlLabel = new Label("agent.openai.api_url");
        openaiUrlLabel.getStyleClass().add("param-key-label");
        Label openaiUrlHelp = new Label("Basis-URL der OpenAI-kompatiblen API.");
        openaiUrlHelp.getStyleClass().add("param-help-label");
        openaiUrlHelp.setWrapText(true);
        openaiUrlHelp.setMaxWidth(680);
        VBox openaiUrlCard = new VBox(4);
        openaiUrlCard.getStyleClass().add("param-card");
        openaiUrlCard.getChildren().addAll(openaiUrlLabel, openaiUrlField, openaiUrlHelp);
        openaiParams.getChildren().add(openaiUrlCard);
        keyToControl.put("agent.openai.api_url", openaiUrlField);

        String openaiModel = ResourceManager.getParameter("agent.openai.model", "gpt-4o-mini");
        FilterableModelOptionSelector openaiModelSelector = new FilterableModelOptionSelector(true);
        openaiModelSelector.setModelId(openaiModel);
        openaiModelSelector.setOnLoad(() -> loadAgentModels(
                openaiKeyField.getText(), openaiUrlField.getText(), openaiModelSelector));
        Label openaiModelLabel = new Label("agent.openai.model");
        openaiModelLabel.getStyleClass().add("param-key-label");
        Label openaiModelHelp = new Label("Modell fuer die OpenAI-Analyse (nach „Modelle laden“ auswählen oder frei eingeben). Kosten werden bei OpenRouter-kompatiblen APIs angezeigt.");
        openaiModelHelp.getStyleClass().add("param-help-label");
        openaiModelHelp.setWrapText(true);
        openaiModelHelp.setMaxWidth(680);
        VBox openaiModelCard = new VBox(4);
        openaiModelCard.getStyleClass().add("param-card");
        openaiModelCard.getChildren().addAll(openaiModelLabel, openaiModelSelector, openaiModelHelp);
        openaiParams.getChildren().add(openaiModelCard);
        keyToControl.put("agent.openai.model", openaiModelSelector);

        double openaiTemp = ResourceManager.getDoubleParameter("agent.openai.temperature", 0.7);
        Spinner<Double> openaiTempSpinner = new Spinner<>(0.0, 2.0, openaiTemp, 0.05);
        openaiTempSpinner.setEditable(true);
        openaiTempSpinner.setPrefWidth(120);
        Label openaiTempLabel = new Label("agent.openai.temperature");
        openaiTempLabel.getStyleClass().add("param-key-label");
        Label openaiTempHelp = new Label(
                "Temperatur fuer OpenAI-Backend (Welt-Editor, Agenten). Bereich 0.0–2.0; bei Claude-Modellen max. 1.0.");
        openaiTempHelp.getStyleClass().add("param-help-label");
        openaiTempHelp.setWrapText(true);
        openaiTempHelp.setMaxWidth(680);
        VBox openaiTempCard = new VBox(4);
        openaiTempCard.getStyleClass().add("param-card");
        openaiTempCard.getChildren().addAll(openaiTempLabel, openaiTempSpinner, openaiTempHelp);
        openaiParams.getChildren().add(openaiTempCard);
        keyToControl.put("agent.openai.temperature", openaiTempSpinner);

        String agentTimeoutStr = ResourceManager.getParameter("agent.openai.request_timeout_sec", "300");
        int agentTimeoutVal = parseInt(agentTimeoutStr, 300);
        agentTimeoutVal = Math.max(60, Math.min(900, agentTimeoutVal));
        Spinner<Integer> agentTimeoutSpinner = new Spinner<>(60, 900, agentTimeoutVal);
        agentTimeoutSpinner.setEditable(true);
        agentTimeoutSpinner.setPrefWidth(180);
        Label agentTimeoutLabel = new Label("agent.openai.request_timeout_sec");
        agentTimeoutLabel.getStyleClass().add("param-key-label");
        Label agentTimeoutHelp = new Label(
                "Timeout pro Agenten-Anfrage (Sekunden). Kimi mit vollem Buch-Kontext braucht oft 180–600 s.");
        agentTimeoutHelp.getStyleClass().add("param-help-label");
        agentTimeoutHelp.setWrapText(true);
        agentTimeoutHelp.setMaxWidth(680);
        VBox agentTimeoutCard = new VBox(4);
        agentTimeoutCard.getStyleClass().add("param-card");
        agentTimeoutCard.getChildren().addAll(agentTimeoutLabel, agentTimeoutSpinner, agentTimeoutHelp);
        openaiParams.getChildren().add(agentTimeoutCard);
        keyToControl.put("agent.openai.request_timeout_sec", agentTimeoutSpinner);

        boolean includeAllChapters = Boolean.parseBoolean(
                ResourceManager.getParameter("agent.plothole.include_all_chapters", "true"));
        CheckBox includeAllChaptersCheck = new CheckBox("Alle Kapitel als Kontext mitsenden");
        includeAllChaptersCheck.setSelected(includeAllChapters);
        Label includeAllLabel = new Label("agent.plothole.include_all_chapters");
        includeAllLabel.getStyleClass().add("param-key-label");
        Label includeAllHelp = new Label(
                "An = gesamtes Manuskript als Kontext (langsamer, mehr Timeout-Risiko). Aus = nur aktuelles Kapitel.");
        includeAllHelp.getStyleClass().add("param-help-label");
        includeAllHelp.setWrapText(true);
        includeAllHelp.setMaxWidth(680);
        VBox includeAllCard = new VBox(4);
        includeAllCard.getStyleClass().add("param-card");
        includeAllCard.getChildren().addAll(includeAllLabel, includeAllChaptersCheck, includeAllHelp);
        content.getChildren().add(includeAllCard);
        keyToControl.put("agent.plothole.include_all_chapters", includeAllChaptersCheck);

        // Sichtbarkeit basierend auf Backend-Auswahl
        Runnable updateVisibility = () -> {
            String selected = backendCombo.getValue();
            ollamaParams.setVisible("Ollama".equals(selected));
            ollamaParams.setManaged("Ollama".equals(selected));
            openaiParams.setVisible("OpenAI".equals(selected));
            openaiParams.setManaged("OpenAI".equals(selected));
        };
        backendCombo.setOnAction(e -> updateVisibility.run());

        content.getChildren().addAll(ollamaParams, openaiParams);

        // Echtzeit-Einstellungen
        Label realtimeHeader = new Label("Echtzeit-Prüfung");
        realtimeHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-padding: 12 0 0 0;");
        content.getChildren().add(realtimeHeader);

        boolean realtimeEnabled = Boolean.parseBoolean(ResourceManager.getParameter("agent.realtime_enabled", "false"));
        CheckBox realtimeCheck = new CheckBox("Echtzeit-Prüfung beim Tippen aktivieren");
        realtimeCheck.setSelected(realtimeEnabled);
        realtimeCheck.setMaxWidth(400);
        Label realtimeLabel = new Label("agent.realtime_enabled");
        realtimeLabel.getStyleClass().add("param-key-label");
        Label realtimeHelp = new Label("Echtzeit-Pruefung beim Tippen aktivieren.");
        realtimeHelp.getStyleClass().add("param-help-label");
        realtimeHelp.setWrapText(true);
        realtimeHelp.setMaxWidth(680);
        VBox realtimeCard = new VBox(4);
        realtimeCard.getStyleClass().add("param-card");
        realtimeCard.getChildren().addAll(realtimeLabel, realtimeCheck, realtimeHelp);
        content.getChildren().add(realtimeCard);
        keyToControl.put("agent.realtime_enabled", realtimeCheck);

        String debounceStr = ResourceManager.getParameter("agent.realtime_debounce_ms", "2000");
        int debounceVal = parseInt(debounceStr, 2000);
        debounceVal = Math.max(500, Math.min(10000, debounceVal));
        Spinner<Integer> debounceSpinner = new Spinner<>(500, 10000, debounceVal);
        debounceSpinner.setEditable(true);
        debounceSpinner.setPrefWidth(180);
        Label debounceLabel = new Label("agent.realtime_debounce_ms");
        debounceLabel.getStyleClass().add("param-key-label");
        Label debounceHelp = new Label("Verzoegerung in ms nach letztem Tippen, bevor die Echtzeit-Pruefung startet.");
        debounceHelp.getStyleClass().add("param-help-label");
        debounceHelp.setWrapText(true);
        debounceHelp.setMaxWidth(680);
        VBox debounceCard = new VBox(4);
        debounceCard.getStyleClass().add("param-card");
        debounceCard.getChildren().addAll(debounceLabel, debounceSpinner, debounceHelp);
        content.getChildren().add(debounceCard);
        keyToControl.put("agent.realtime_debounce_ms", debounceSpinner);

        // Initiale Sichtbarkeit setzen
        updateVisibility.run();

        return content;
    }

    private VBox buildOnlineLektoratTab(Map<String, Parent> keyToControl, int theme) {
        VBox content = new VBox(12);
        content.setPadding(new Insets(16));
        content.getStyleClass().addAll(getThemeStyleClasses(theme));

        String apiKey = ResourceManager.getParameter("api.lektorat.api_key", "");
        String baseUrl = ResourceManager.getParameter("api.lektorat.base_url", "https://api.openai.com/v1");
        String model = ResourceManager.getParameter("api.lektorat.model", "gpt-4o-mini");
        String extraPrompt = ResourceManager.getParameter("api.lektorat.extra_prompt", "");
        String lektoratType = ResourceManager.getParameter("api.lektorat.type", "allgemein");

        TextField apiKeyField = new TextField(apiKey != null ? apiKey : "");
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

        FilterableModelOptionSelector lektoratModelSelector = new FilterableModelOptionSelector(true);
        lektoratModelSelector.setInitialEditorText(model);
        lektoratModelSelector.setOnLoad(() -> loadLektoratModels(
                apiKeyField.getText(), baseUrlField.getText(), lektoratModelSelector));
        Label modelLabel = new Label("api.lektorat.model");
        modelLabel.getStyleClass().add("param-key-label");
        Label modelHelp = new Label("Modell für das Lektorat (nach „Modelle laden“ auswählen oder frei eingeben). Kosten werden bei OpenRouter-kompatiblen APIs angezeigt.");
        modelHelp.getStyleClass().add("param-help-label");
        modelHelp.setWrapText(true);
        modelHelp.setMaxWidth(680);
        VBox modelCard = new VBox(4);
        modelCard.getStyleClass().add("param-card");
        modelCard.getChildren().addAll(modelLabel, lektoratModelSelector, modelHelp);
        content.getChildren().add(modelCard);
        keyToControl.put("api.lektorat.model", lektoratModelSelector);

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

        // Vorschläge pro Eintrag (1–5)
        String suggestionsStr = ResourceManager.getParameter("api.lektorat.suggestions_per_entry", "2");
        int suggestionsVal = parseInt(suggestionsStr, 2);
        suggestionsVal = Math.max(1, Math.min(5, suggestionsVal));
        Spinner<Integer> suggestionsSpinner = new Spinner<>(1, 5, suggestionsVal);
        suggestionsSpinner.setEditable(true);
        suggestionsSpinner.setPrefWidth(180);
        Label suggestionsLabel = new Label("api.lektorat.suggestions_per_entry");
        suggestionsLabel.getStyleClass().add("param-key-label");
        Label suggestionsHelp = new Label("Anzahl Vorschläge pro Anmerkung (1–5). Weniger = weniger API-Output und Kosten. Nur Anmerkungen mit Gewichtung 3–5 werden angefordert.");
        suggestionsHelp.getStyleClass().add("param-help-label");
        suggestionsHelp.setWrapText(true);
        suggestionsHelp.setMaxWidth(680);
        VBox suggestionsCard = new VBox(4);
        suggestionsCard.getStyleClass().add("param-card");
        suggestionsCard.getChildren().addAll(suggestionsLabel, suggestionsSpinner, suggestionsHelp);
        content.getChildren().add(suggestionsCard);
        keyToControl.put("api.lektorat.suggestions_per_entry", suggestionsSpinner);

        // Sprechantwort/Selektion per Online-API statt Ollama
        String useOnlineApiStr = ResourceManager.getParameter("api.editor_rewrite.use_online_api", "false");
        boolean useOnlineApiVal = "true".equalsIgnoreCase(useOnlineApiStr != null ? useOnlineApiStr.trim() : "");
        CheckBox useOnlineApiCheck = new CheckBox("Sprechantwort korrigieren und Selektion überarbeiten per Online-API (statt Ollama)");
        useOnlineApiCheck.setSelected(useOnlineApiVal);
        useOnlineApiCheck.setMaxWidth(680);
        Label useOnlineApiLabel = new Label("api.editor_rewrite.use_online_api");
        useOnlineApiLabel.getStyleClass().add("param-key-label");
        Label useOnlineApiHelp = new Label("Sprechantwort korrigieren und Selektion überarbeiten per Online-API (OpenAI-kompatibel) statt Ollama. Erfordert api.lektorat.api_key.");
        useOnlineApiHelp.getStyleClass().add("param-help-label");
        useOnlineApiHelp.setWrapText(true);
        useOnlineApiHelp.setMaxWidth(680);
        VBox useOnlineApiCard = new VBox(4);
        useOnlineApiCard.getStyleClass().add("param-card");
        useOnlineApiCard.getChildren().addAll(useOnlineApiLabel, useOnlineApiCheck, useOnlineApiHelp);
        content.getChildren().add(useOnlineApiCard);
        keyToControl.put("api.editor_rewrite.use_online_api", useOnlineApiCheck);

        return content;
    }

    /**
     * Liest Preise aus dem Modell-JSON und formatiert sie als Kosten pro 1M Tokens
     * (übliche Anbieterangabe, z. B. „5 $/1M · 25 $/1M“ für Claude Opus).
     */
    private static String formatModelPricing(com.google.gson.JsonObject model) {
        if (model == null) return "";
        double inputPerToken = Double.NaN;
        double outputPerToken = Double.NaN;

        // Mammouth.ai u. ä.: model_info.input_cost_per_token + output_cost_per_token (pro Token)
        if (model.has("model_info")) {
            com.google.gson.JsonElement mi = model.get("model_info");
            if (mi != null && mi.isJsonObject()) {
                com.google.gson.JsonObject info = mi.getAsJsonObject();
                inputPerToken = parseDoubleSafe(info.get("input_cost_per_token"));
                outputPerToken = parseDoubleSafe(info.get("output_cost_per_token"));
            }
        }
        // OpenRouter / kompatible APIs: pricing.prompt + pricing.completion (pro Token; String oder Number)
        if (model.has("pricing")) {
            com.google.gson.JsonElement pe = model.get("pricing");
            if (pe != null && !pe.isJsonNull()) {
                if (pe.isJsonObject()) {
                    com.google.gson.JsonObject p = pe.getAsJsonObject();
                    inputPerToken = firstValidDouble(
                        parseDoubleSafe(p.get("prompt")),
                        parseDoubleSafe(p.get("input")));
                    outputPerToken = firstValidDouble(
                        parseDoubleSafe(p.get("completion")),
                        parseDoubleSafe(p.get("output")));
                } else if (pe.isJsonPrimitive() && pe.getAsJsonPrimitive().isString()) {
                    try {
                        com.google.gson.JsonObject p = new com.google.gson.Gson().fromJson(pe.getAsString(), com.google.gson.JsonObject.class);
                        if (p != null) {
                            inputPerToken = firstValidDouble(parseDoubleSafe(p.get("prompt")), parseDoubleSafe(p.get("input")));
                            outputPerToken = firstValidDouble(parseDoubleSafe(p.get("completion")), parseDoubleSafe(p.get("output")));
                        }
                    } catch (Exception ignored) { }
                }
            }
        }
        // Flache Felder auf Modell-Ebene (einige APIs)
        if (Double.isNaN(inputPerToken)) inputPerToken = parseDoubleSafe(model.get("prompt_price"));
        if (Double.isNaN(outputPerToken)) outputPerToken = parseDoubleSafe(model.get("completion_price"));
        // Pro 1M Tokens (exakt aus API)
        double inputPer1M = Double.NaN;
        double outputPer1M = Double.NaN;
        if (!Double.isNaN(inputPerToken)) inputPer1M = inputPerToken * 1_000_000.0;
        if (!Double.isNaN(outputPerToken)) outputPer1M = outputPerToken * 1_000_000.0;
        if (model.has("input_cost")) inputPer1M = parseDoubleSafe(model.get("input_cost"));
        if (model.has("output_cost")) outputPer1M = parseDoubleSafe(model.get("output_cost"));
        if (model.has("input_price")) inputPer1M = firstValidDouble(inputPer1M, parseDoubleSafe(model.get("input_price")));
        if (model.has("output_price")) outputPer1M = firstValidDouble(outputPer1M, parseDoubleSafe(model.get("output_price")));

        if (Double.isNaN(inputPer1M) && Double.isNaN(outputPer1M)) return "";
        Locale loc = Locale.GERMANY;
        // 1,8 Tokens/Zeichen (realistisch für dt. Text): 10k Zeichen ≈ 18k Tokens → 25/1e6 * 1,8 * 10000 = 0,45 $
        final double TOKENS_PER_10K_CHARS = 1.8 * 10_000.0;
        double inputPer10k = !Double.isNaN(inputPer1M) ? inputPer1M / 1_000_000.0 * TOKENS_PER_10K_CHARS : Double.NaN;
        double outputPer10k = !Double.isNaN(outputPer1M) ? outputPer1M / 1_000_000.0 * TOKENS_PER_10K_CHARS : Double.NaN;
        String in = Double.isNaN(inputPer1M) ? "–" : String.format(loc, "%.2f $/1M (%.2f $/10k Zeichen)", inputPer1M, inputPer10k);
        String out = Double.isNaN(outputPer1M) ? "–" : String.format(loc, "%.2f $/1M (%.2f $/10k Zeichen)", outputPer1M, outputPer10k);
        return "Input: " + in + " · Output: " + out;
    }

    /** Entfernt den Kosten-Anzeigetext, sodass nur die Modell-ID für die API übrig bleibt. */
    private static double firstValidDouble(double a, double b) {
        return !Double.isNaN(a) ? a : b;
    }

    private static double parseDoubleSafe(com.google.gson.JsonElement el) {
        if (el == null || el.isJsonNull()) return Double.NaN;
        try {
            if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString())
                return Double.parseDouble(el.getAsString().replace(',', '.'));
            return el.getAsDouble();
        } catch (Exception e) {
            return Double.NaN;
        }
    }

    @SuppressWarnings("unchecked")
    private void loadLektoratModels(String apiKey, String baseUrl, FilterableModelOptionSelector modelSelector) {
        loadOpenAIModels(apiKey, baseUrl, modelSelector, "Lektorat");
    }

    private void loadAgentModels(String apiKey, String baseUrl, FilterableModelOptionSelector modelSelector) {
        loadOpenAIModels(apiKey, baseUrl, modelSelector, "Agenten");
    }

    private void loadOpenAIModels(String apiKey, String baseUrl, FilterableModelOptionSelector modelSelector, String context) {
        if (apiKey == null || apiKey.isBlank() || baseUrl == null || baseUrl.isBlank()) {
            showInfo("Eingabe fehlt", "Bitte API-Key und Basis-URL eintragen.");
            return;
        }
        String base = baseUrl.replaceAll("/$", "").trim();
        // Mammouth.ai: Preise nur unter /public/models; /v1/models liefert oft keine model_info
        String url = (base.contains("mammouth.ai")) ? "https://api.mammouth.ai/public/models" : (base + "/models");
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .GET();
        if (apiKey != null && !apiKey.isBlank()) requestBuilder.header("Authorization", "Bearer " + apiKey.trim());
        HttpRequest request = requestBuilder.build();
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
                    showInfo("Modelle laden (" + context + ")", result);
                    return;
                }
                try {
                    com.google.gson.Gson gson = new com.google.gson.Gson();
                    com.google.gson.JsonArray data = null;
                    com.google.gson.JsonElement parsed = gson.fromJson(result, com.google.gson.JsonElement.class);
                    if (parsed != null && parsed.isJsonObject() && parsed.getAsJsonObject().has("data"))
                        data = parsed.getAsJsonObject().getAsJsonArray("data");
                    else if (parsed != null && parsed.isJsonArray())
                        data = parsed.getAsJsonArray();
                    java.util.List<ModelOption> items = new java.util.ArrayList<>();
                    if (data != null) {
                        for (int i = 0; i < data.size(); i++) {
                            com.google.gson.JsonElement el = data.get(i);
                            if (el != null && el.isJsonObject()) {
                                com.google.gson.JsonObject obj = el.getAsJsonObject();
                                if (obj.has("id")) {
                                    String id = obj.get("id").getAsString();
                                    String costStr = formatModelPricing(obj);
                                    String displayText = costStr.isEmpty() ? id : (id + " (" + costStr + ")");
                                    items.add(new ModelOption(id, displayText));
                                }
                            }
                        }
                        items.sort(java.util.Comparator.comparing(m -> m.id, String.CASE_INSENSITIVE_ORDER));
                    }
                    modelSelector.setModelOptions(items);
                    if (items.isEmpty()) {
                        showInfo("Modelle laden (" + context + ")", "Keine Modelle in der Antwort gefunden.");
                    } else {
                        showInfo("Modelle laden (" + context + ")", items.size() + " Modelle geladen. Bitte Modell auswählen und Speichern klicken.");
                    }
                } catch (Exception e) {
                    showInfo("Modelle laden (" + context + ")", "Antwort konnte nicht gelesen werden: " + e.getMessage());
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
                // Spezialfall für ComfyUI-Hilfe: Button statt CheckBox
                if ("comfyui.help_link".equals(def.getKey())) {
                    Button helpButton = new Button("ComfyUI Installationsanleitung");
                    helpButton.setOnAction(e -> {
                        try {
                            String userDir = System.getProperty("user.dir");
                            String filePath = userDir + "/config/help/comfyui_installation.html";
                            java.io.File file = new java.io.File(filePath);
                            
                            logger.info("ComfyUI-Hilfe Datei (Parameter): {}", filePath);
                            logger.info("Datei existiert: {}", file.exists());
                            
                            if (!file.exists()) {
                                // Versuche alternativen Pfad
                                filePath = userDir + "\\config\\help\\comfyui_installation.html";
                                file = new java.io.File(filePath);
                                logger.info("Alternativer Pfad (Parameter): {}", filePath);
                                logger.info("Datei existiert (alt): {}", file.exists());
                            }
                            
                            if (file.exists()) {
                                java.awt.Desktop.getDesktop().browse(file.toURI());
                            } else {
                                throw new java.io.IOException("Hilfe-Datei nicht gefunden: " + filePath);
                            }
                        } catch (Exception ex) {
                            logger.error("Konnte ComfyUI-Hilfe nicht öffnen (Parameter)", ex);
                            CustomAlert alert = new CustomAlert(CustomAlert.AlertType.ERROR);
                            alert.setHeaderText("Hilfe konnte nicht geöffnet werden");
                            alert.setContentText("Die ComfyUI Installationsanleitung wurde nicht gefunden.\n" +
                                               "Bitte überprüfen Sie: config/help/comfyui_installation.html\n" +
                                               "oder besuchen Sie: https://www.comfy.org/download");
                            alert.showAndWait();
                        }
                    });
                    helpButton.setMaxWidth(400);
                    return helpButton;
                }
                // Spezialfall für ComfyUI Voraussetzungen-Check: Button statt CheckBox
                if ("comfyui.prerequisites_check".equals(def.getKey())) {
                    Button checkButton = new Button("ComfyUI Voraussetzungen prüfen");
                    checkButton.setOnAction(e -> {
                        try {
                            String userDir = System.getProperty("user.dir");
                            String os = System.getProperty("os.name").toLowerCase();
                            
                            logger.info("ComfyUI Check gestartet - OS: {}, UserDir: {}", os, userDir);
                            
                            String scriptPath;
                            if (os.contains("win")) {
                                // Windows: PowerShell bevorzugen (funktioniert besser)
                                scriptPath = userDir + "\\check-comfyui-prerequisites.ps1";
                                java.io.File psFile = new java.io.File(scriptPath);
                                logger.info("PowerShell Script Pfad: {}, existiert: {}", scriptPath, psFile.exists());
                                if (!psFile.exists()) {
                                    // Fallback zu Batch
                                    scriptPath = userDir + "\\check-comfyui-prerequisites.bat";
                                    java.io.File batFile = new java.io.File(scriptPath);
                                    logger.info("Fallback Batch Script Pfad: {}, existiert: {}", scriptPath, batFile.exists());
                                }
                            } else {
                                // macOS/Linux: Shell-Skript
                                scriptPath = userDir + "/check-comfyui-prerequisites.sh";
                                logger.info("Shell Script Pfad: {}", scriptPath);
                            }
                            
                            java.io.File scriptFile = new java.io.File(scriptPath);
                            logger.info("Final Script Path: {}, exists: {}, canExecute: {}", 
                                       scriptPath, scriptFile.exists(), scriptFile.canExecute());
                            
                            if (scriptFile.exists()) {
                                // PowerShell Output lesen und in UI anzeigen
                                ProcessBuilder pb = new ProcessBuilder();
                                if (os.contains("win") && scriptPath.endsWith(".ps1")) {
                                    pb.command("powershell.exe", "-ExecutionPolicy", "Bypass", "-File", scriptPath);
                                    // UTF-8 Encoding für PowerShell Output über Umgebungsvariablen
                                    Map<String, String> env = pb.environment();
                                    env.put("PYTHONIOENCODING", "utf-8");
                                    env.put("PYTHONLEGACYWINDOWSSTDIN", "utf-8");
                                    env.put("UTF8", "1");
                                    logger.info("Führe PowerShell aus: powershell.exe -ExecutionPolicy Bypass -File {}", scriptPath);
                                } else if (os.contains("win")) {
                                    pb.command("cmd.exe", "/c", scriptPath);
                                    logger.info("Führe Batch aus: cmd.exe /c {}", scriptPath);
                                } else {
                                    pb.command("bash", scriptPath);
                                    logger.info("Führe Shell aus: bash {}", scriptPath);
                                }
                                
                                // Arbeitsverzeichnis setzen
                                pb.directory(new java.io.File(userDir));
                                pb.redirectErrorStream(true); // stderr in stdout mergen
                                
                                // Prozess im Hintergrund starten und Output lesen
                                new Thread(() -> {
                                    try {
                                        Process process = pb.start();
                                        logger.info("ComfyUI Check Prozess gestartet mit PID: {}", process.pid());
                                        
                                        // Output lesen mit UTF-8 Encoding
                                        StringBuilder output = new StringBuilder();
                                        try (BufferedReader reader = new BufferedReader(
                                                new InputStreamReader(process.getInputStream(), "UTF-8"))) {
                                            String line;
                                            while ((line = reader.readLine()) != null) {
                                                output.append(line).append("\n");
                                            }
                                        }
                                        
                                        int exitCode = process.waitFor();
                                        logger.info("ComfyUI Check beendet mit Exit-Code: {}", exitCode);
                                        
                                        // Ergebnisse in UI anzeigen
                                        Platform.runLater(() -> {
                                            CustomAlert alert = new CustomAlert(CustomAlert.AlertType.INFORMATION);
                                            alert.setHeaderText("ComfyUI Voraussetzungen-Check Ergebnisse");
                                            
                                            String content = output.toString();
                                            if (content.trim().isEmpty()) {
                                                content = "Der Check wurde ausgeführt, aber es gab keine Ausgabe.\n\nExit-Code: " + exitCode;
                                            }
                                            
                                            alert.setContentText(content);
                                            alert.showAndWait();
                                        });
                                        
                                    } catch (Exception ex) {
                                        logger.error("Fehler beim Ausführen des ComfyUI Checks", ex);
                                        Platform.runLater(() -> {
                                            CustomAlert alert = new CustomAlert(CustomAlert.AlertType.ERROR);
                                            alert.setHeaderText("Check-Fehler");
                                            alert.setContentText("Fehler beim Ausführen: " + ex.getMessage());
                                            alert.showAndWait();
                                        });
                                    }
                                }).start();
                                
                            } else {
                                throw new java.io.IOException("Check-Script nicht gefunden: " + scriptPath);
                            }
                        } catch (Exception ex) {
                            logger.error("Konnte ComfyUI Voraussetzungen-Check nicht starten", ex);
                            Platform.runLater(() -> {
                                CustomAlert alert = new CustomAlert(CustomAlert.AlertType.ERROR);
                                alert.setHeaderText("Check konnte nicht gestartet werden");
                                alert.setContentText("Das Voraussetzungen-Script wurde nicht gefunden.\n" +
                                                   "Bitte überprüfen Sie: check-comfyui-prerequisites.ps1/.bat\n" +
                                                   "oder führen Sie den Check manuell durch.\n\n" +
                                                   "Fehler: " + ex.getMessage());
                                alert.showAndWait();
                            });
                        }
                    });
                    checkButton.setMaxWidth(400);
                    return checkButton;
                }
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
            case CHOICE:
                ComboBox<String> choiceCombo = new ComboBox<>();
                if (def.getChoices() != null) {
                    choiceCombo.getItems().addAll(def.getChoices());
                }
                if (current != null && !current.isEmpty()) {
                    choiceCombo.setValue(current);
                } else if (def.getChoices() != null && def.getChoices().length > 0) {
                    choiceCombo.setValue(def.getChoices()[0]);
                }
                choiceCombo.setPrefWidth(200);
                return choiceCombo;
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
        for (Map.Entry<String, Parent> e : keyToControl.entrySet()) {
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
        // Cache der Agent-Konfigurationen invalidieren und neu laden, damit neue Modellnamen übernommen werden
        if (keyToDef.containsKey("agent.openai.model") || keyToDef.containsKey("agent.ollama.model")) {
            com.manuskript.agent.AgentConfigManager.invalidateCache();
            // Agent-Konfigurationen neu laden und speichern, um die neuen Modellnamen zu übernehmen
            java.util.List<com.manuskript.agent.AgentConfig> configs = com.manuskript.agent.AgentConfigManager.loadConfigs();
            com.manuskript.agent.AgentConfigManager.saveConfigs(configs);
            // Benachrichtige EditorWindow, um Agent-Instanzen neu zu erstellen
            com.manuskript.EditorWindow instance = com.manuskript.EditorWindow.getCurrentInstance();
            if (instance != null) {
                instance.clearAgentInstances();
            }
        }
        showInfo("Gespeichert", "Alle Parameter wurden gespeichert.");
    }

    private String getValueFromControl(Parent c, ParameterDef def) {
        if (c instanceof CheckBox) return String.valueOf(((CheckBox) c).isSelected());
        if (c instanceof Spinner) {
            Object v = ((Spinner<?>) c).getValue();
            return v != null ? v.toString() : def.getDefaultValue();
        }
        if (c instanceof TextArea) return ((TextArea) c).getText();
        if (c instanceof FilterableModelOptionSelector) {
            return ((FilterableModelOptionSelector) c).getModelId();
        }
        if (c instanceof ComboBox) {
            ComboBox<?> cb = (ComboBox<?>) c;
            Object v = cb.getValue();
            if (v != null) {
                return v.toString();
            }
            if (cb.isEditable() && cb.getEditor() != null) {
                return cb.getEditor().getText();
            }
            return def.getDefaultValue();
        }
        if (c instanceof TextField) return ((TextField) c).getText();
        return def.getDefaultValue();
    }

    private void restoreDefaults() {
        for (Map.Entry<String, Parent> e : keyToControl.entrySet()) {
            ParameterDef def = keyToDef.get(e.getKey());
            if (def == null) continue;
            setControlToDefault(e.getValue(), def);
        }
        showInfo("Standard", "Alle Werte auf Standard zurückgesetzt. Bitte „Speichern“ klicken, um zu übernehmen.");
    }

    @SuppressWarnings("unchecked")
    private void setControlToDefault(Parent c, ParameterDef def) {
        String d = def.getDefaultValue();
        if (c instanceof CheckBox) ((CheckBox) c).setSelected(Boolean.parseBoolean(d));
        else if (c instanceof Spinner) {
            Spinner<?> s = (Spinner<?>) c;
            if (s.getValue() instanceof Integer)
                ((Spinner<Integer>) s).getValueFactory().setValue(parseInt(d, 0));
            else
                ((Spinner<Double>) s).getValueFactory().setValue(parseDouble(d, 0.0));
        } else if (c instanceof TextArea) ((TextArea) c).setText(d != null ? d : "");
        else if (c instanceof FilterableModelOptionSelector) {
            ((FilterableModelOptionSelector) c).setInitialEditorText(d);
        } else if (c instanceof ComboBox) {
            ComboBox<?> cb = (ComboBox<?>) c;
            cb.getSelectionModel().clearSelection();
            if (cb.isEditable() && cb.getEditor() != null) {
                cb.getEditor().setText(d != null ? d : "");
            }
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
