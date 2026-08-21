package com.manuskript;

import com.manuskript.agent.FilterableModelOptionSelector;
import com.manuskript.agent.ModelOption;
import com.manuskript.agent.OpenAiProviderProfiles;

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
    /** Speichert das aktive OpenAI-Provider-Profil (inkl. API-Key) beim globalen Speichern. */
    private Runnable openaiProviderProfilesSaveHook;

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

        String ollamaModel = ResourceManager.getParameter("agent.ollama.model", ParameterRegistry.DEFAULT_OLLAMA_MODEL);
        ComboBox<String> ollamaModelCombo = new ComboBox<>();
        ollamaModelCombo.setEditable(true);
        ollamaModelCombo.setPrefWidth(400);
        ollamaModelCombo.setPromptText("Modell wählen oder eingeben…");
        if (ollamaModel != null && !ollamaModel.isBlank()) {
            ollamaModelCombo.getItems().add(ollamaModel);
            ollamaModelCombo.setValue(ollamaModel);
            ollamaModelCombo.getEditor().setText(ollamaModel);
        }
        Button ollamaLoadModelsBtn = new Button("Installierte laden");
        ollamaLoadModelsBtn.setTooltip(new Tooltip("Lädt lokal bei Ollama vorhandene Modelle in die Liste"));
        ollamaLoadModelsBtn.setOnAction(e -> loadOllamaInstalledModels(
                ollamaUrlField.getText(), ollamaModelCombo));
        HBox ollamaModelRow = new HBox(8, ollamaModelCombo, ollamaLoadModelsBtn);
        ollamaModelRow.setAlignment(Pos.CENTER_LEFT);
        Label ollamaModelLabel = new Label("agent.ollama.model");
        ollamaModelLabel.getStyleClass().add("param-key-label");
        Label ollamaModelHelp = new Label(
                "Aktives Ollama-Modell für Agenten. „Installierte laden“ listet vorhandene Modelle; "
                        + "unten kannst du weitere von ollama.com nachladen.");
        ollamaModelHelp.getStyleClass().add("param-help-label");
        ollamaModelHelp.setWrapText(true);
        ollamaModelHelp.setMaxWidth(680);
        VBox ollamaModelCard = new VBox(4);
        ollamaModelCard.getStyleClass().add("param-card");
        ollamaModelCard.getChildren().addAll(ollamaModelLabel, ollamaModelRow, ollamaModelHelp);
        ollamaParams.getChildren().add(ollamaModelCard);
        keyToControl.put("agent.ollama.model", ollamaModelCombo);

        Label ollamaInstallHeader = new Label("Ollama-Modell installieren");
        ollamaInstallHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-padding: 4 0 0 0;");
        ComboBox<String> ollamaInstallCombo = new ComboBox<>();
        ollamaInstallCombo.setEditable(true);
        ollamaInstallCombo.setPrefWidth(280);
        ollamaInstallCombo.setPromptText("z. B. jobautomation/OpenEuroLLM-German, gemma3:4b");
        ollamaInstallCombo.getItems().addAll(
                ParameterRegistry.DEFAULT_OLLAMA_MODEL,
                "gemma3:4b",
                "llama3.2",
                "llama3.2:3b",
                "qwen2.5:7b",
                "mistral:7b-instruct",
                "phi3:mini",
                "llama3.1:8b");
        ollamaInstallCombo.setValue(ParameterRegistry.DEFAULT_OLLAMA_MODEL);
        Button ollamaInstallBtn = new Button("Installieren");
        ollamaInstallBtn.setTooltip(new Tooltip(
                "Lädt das Modell über Ollama (API /api/pull, sonst ollama pull). Kann mehrere Minuten dauern."));
        Label ollamaInstallStatus = new Label("");
        ollamaInstallStatus.setWrapText(true);
        ollamaInstallStatus.setMaxWidth(680);
        ProgressIndicator ollamaInstallBusy = new ProgressIndicator();
        ollamaInstallBusy.setPrefSize(18, 18);
        ollamaInstallBusy.setVisible(false);
        ollamaInstallBusy.setManaged(false);
        ollamaInstallBtn.setOnAction(e -> installOllamaModelFromParams(
                ollamaUrlField.getText(),
                ollamaInstallCombo,
                ollamaModelCombo,
                ollamaInstallBtn,
                ollamaInstallStatus,
                ollamaInstallBusy));
        HBox ollamaInstallRow = new HBox(8, ollamaInstallCombo, ollamaInstallBtn, ollamaInstallBusy);
        ollamaInstallRow.setAlignment(Pos.CENTER_LEFT);
        Label ollamaInstallHelp = new Label(
                "Ollama muss laufen (z. B. App gestartet). Modellname wie auf https://ollama.com/library.");
        ollamaInstallHelp.getStyleClass().add("param-help-label");
        ollamaInstallHelp.setWrapText(true);
        ollamaInstallHelp.setMaxWidth(680);
        VBox ollamaInstallCard = new VBox(4);
        ollamaInstallCard.getStyleClass().add("param-card");
        ollamaInstallCard.getChildren().addAll(
                ollamaInstallHeader, ollamaInstallRow, ollamaInstallStatus, ollamaInstallHelp);
        ollamaParams.getChildren().add(ollamaInstallCard);

        // OpenAI-spezifische Parameter
        Label openaiHeader = new Label("OpenAI-Einstellungen");
        openaiHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-padding: 8 0 0 0;");
        openaiParams.getChildren().add(openaiHeader);

        String openaiKey = ResourceManager.getParameter("agent.openai.api_key", "");
        TextField openaiKeyField = new TextField(openaiKey);
        openaiKeyField.setPrefWidth(400);

        String openaiUrl = ResourceManager.getParameter("agent.openai.api_url", "https://api.openai.com/v1");
        TextField openaiUrlField = new TextField(openaiUrl);
        openaiUrlField.setPrefWidth(400);

        String openaiModel = ResourceManager.getParameter("agent.openai.model", "gpt-4o-mini");
        FilterableModelOptionSelector openaiModelSelector = new FilterableModelOptionSelector(true);
        openaiModelSelector.setModelId(openaiModel);
        openaiModelSelector.setOnLoad(() -> loadAgentModels(
                openaiKeyField.getText(), openaiUrlField.getText(), openaiModelSelector));

        ComboBox<String> providerProfileCombo = new ComboBox<>();
        providerProfileCombo.setEditable(true);
        providerProfileCombo.setPrefWidth(280);
        providerProfileCombo.setPromptText("Provider-Profil");
        final java.util.concurrent.atomic.AtomicBoolean providerApplyEnabled =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        final java.util.concurrent.atomic.AtomicReference<java.util.List<OpenAiProviderProfiles.Profile>>
                providerProfilesRef = new java.util.concurrent.atomic.AtomicReference<>(
                new java.util.ArrayList<>(OpenAiProviderProfiles.load()));
        final java.util.concurrent.atomic.AtomicReference<String> lastProviderProfileName =
                new java.util.concurrent.atomic.AtomicReference<>(OpenAiProviderProfiles.loadActiveName());
        /** Nur Profile, die in dieser Session wirklich übernommen wurden – verhindert Auto-Save-Korruption. */
        final java.util.Set<String> providerProfilesAppliedThisSession = new java.util.HashSet<>();

        java.util.function.Function<String, OpenAiProviderProfiles.Profile> snapshotCurrentProfile = name -> {
            if (name == null || name.isBlank()) {
                return null;
            }
            return new OpenAiProviderProfiles.Profile(
                    name.trim(),
                    openaiUrlField.getText(),
                    openaiKeyField.getText(),
                    openaiModelSelector.getModelId());
        };
        java.util.function.Function<OpenAiProviderProfiles.Profile, String> persistProfiles = profile -> {
            if (profile == null || !profile.hasName()) {
                return "Kein Profilname.";
            }
            java.util.List<OpenAiProviderProfiles.Profile> next =
                    OpenAiProviderProfiles.upsert(providerProfilesRef.get(), profile);
            providerProfilesRef.set(next);
            String error = OpenAiProviderProfiles.saveOrError(next);
            OpenAiProviderProfiles.saveActiveName(profile.name());
            lastProviderProfileName.set(profile.name());
            return error;
        };
        Runnable pushProviderFieldsToAgentParams = () -> {
            String url = openaiUrlField.getText() != null ? openaiUrlField.getText().trim() : "";
            String key = openaiKeyField.getText() != null ? openaiKeyField.getText() : "";
            String model = openaiModelSelector.getModelId();
            ResourceManager.saveParameter("agent.openai.api_url", url);
            ResourceManager.saveParameter("agent.openai.api_key", key == null ? "" : key);
            if (model != null && !model.isBlank()) {
                ResourceManager.saveParameter("agent.openai.model", model.trim());
            }
            ResourceManager.saveParameter("agent.backend", "OpenAI");
            try {
                ApplicationPreferences.resourceManagerNode().flush();
            } catch (Exception ex) {
                logger.debug("flush nach Provider-Übernahme: {}", ex.getMessage());
            }
            com.manuskript.agent.AgentConfigManager.invalidateCache();
            logger.info("Provider aktiv für Agenten: url={}, model={}", url, model);
            MainController.notifyOpenEditorsAgentParametersChanged();
        };
        java.util.function.Consumer<OpenAiProviderProfiles.Profile> applyProviderProfile = profile -> {
            if (profile == null) {
                return;
            }
            // Vorheriges Profil nur sichern, wenn es in dieser Session wirklich geladen wurde
            String previousName = lastProviderProfileName.get();
            if (previousName != null && !previousName.isBlank()
                    && !previousName.equalsIgnoreCase(profile.name())
                    && providerProfilesAppliedThisSession.contains(previousName.trim().toLowerCase(Locale.ROOT))) {
                OpenAiProviderProfiles.Profile previousSnapshot = snapshotCurrentProfile.apply(previousName);
                if (previousSnapshot != null) {
                    String persistError = persistProfiles.apply(previousSnapshot);
                    if (persistError != null) {
                        logger.warn("Vor Provider-Wechsel: Profil speichern fehlgeschlagen: {}", persistError);
                    }
                }
            }
            openaiUrlField.setText(profile.apiUrl() != null ? profile.apiUrl() : "");
            openaiKeyField.setText(profile.apiKey() != null ? profile.apiKey() : "");
            if (profile.model() != null && !profile.model().isBlank()) {
                openaiModelSelector.setModelId(profile.model());
            }
            OpenAiProviderProfiles.saveActiveName(profile.name());
            lastProviderProfileName.set(profile.name());
            providerProfilesAppliedThisSession.add(profile.name().trim().toLowerCase(Locale.ROOT));
            // Sofort in ResourceManager schreiben – sonst nutzen Agenten weiter die alte TF-URL
            pushProviderFieldsToAgentParams.run();
        };
        Runnable refreshProviderCombo = () -> {
            providerApplyEnabled.set(false);
            String previous = providerProfileCombo.getEditor().getText();
            providerProfileCombo.getItems().clear();
            for (OpenAiProviderProfiles.Profile profile : providerProfilesRef.get()) {
                if (profile != null && profile.hasName()) {
                    providerProfileCombo.getItems().add(profile.name());
                }
            }
            String active = OpenAiProviderProfiles.loadActiveName();
            if (active != null && !active.isBlank() && providerProfileCombo.getItems().contains(active)) {
                providerProfileCombo.setValue(active);
            } else if (previous != null && !previous.isBlank()) {
                providerProfileCombo.getEditor().setText(previous);
            }
            providerApplyEnabled.set(true);
        };
        providerProfileCombo.setOnAction(e -> {
            if (!providerApplyEnabled.get()) {
                return;
            }
            String name = providerProfileCombo.getValue();
            OpenAiProviderProfiles.Profile profile =
                    OpenAiProviderProfiles.findByName(providerProfilesRef.get(), name);
            if (profile != null) {
                applyProviderProfile.accept(profile);
            }
        });
        Button providerApplyBtn = new Button("Übernehmen");
        providerApplyBtn.setTooltip(new Tooltip("Gewähltes Profil in URL/Key/Modell laden"));
        providerApplyBtn.setOnAction(e -> {
            String name = providerProfileCombo.getEditor().getText();
            OpenAiProviderProfiles.Profile profile =
                    OpenAiProviderProfiles.findByName(providerProfilesRef.get(), name);
            if (profile == null) {
                showInfo("Provider-Profil", "Kein gespeichertes Profil mit diesem Namen.");
                return;
            }
            applyProviderProfile.accept(profile);
            providerApplyEnabled.set(false);
            providerProfileCombo.setValue(profile.name());
            providerApplyEnabled.set(true);
        });
        Button providerSaveBtn = new Button("Profil speichern");
        providerSaveBtn.setTooltip(new Tooltip(
                "Aktuelle URL, API-Key und Modell unter dem Profilnamen speichern (auch neuer Name)."));
        providerSaveBtn.setOnAction(e -> {
            String name = providerProfileCombo.getEditor().getText();
            if (name == null || name.isBlank()) {
                showInfo("Provider-Profil", "Bitte einen Profilnamen eingeben (z. B. Mammouth).");
                return;
            }
            OpenAiProviderProfiles.Profile profile = snapshotCurrentProfile.apply(name);
            String error = persistProfiles.apply(profile);
            refreshProviderCombo.run();
            providerApplyEnabled.set(false);
            providerProfileCombo.setValue(profile.name());
            providerApplyEnabled.set(true);
            if (error != null) {
                showInfo("Provider-Profil", "Speichern fehlgeschlagen: " + error);
            } else {
                showInfo("Provider-Profil", "Profil „" + profile.name() + "“ gespeichert "
                        + "(inkl. API-Key unter ~/.manuskript/).");
            }
        });
        Button providerDeleteBtn = new Button("Löschen");
        providerDeleteBtn.setTooltip(new Tooltip("Gewähltes Profil aus der Liste entfernen"));
        providerDeleteBtn.setOnAction(e -> {
            String name = providerProfileCombo.getEditor().getText();
            if (name == null || name.isBlank()) {
                return;
            }
            java.util.List<OpenAiProviderProfiles.Profile> next =
                    OpenAiProviderProfiles.removeByName(providerProfilesRef.get(), name);
            if (next.size() == providerProfilesRef.get().size()) {
                showInfo("Provider-Profil", "Profil nicht gefunden.");
                return;
            }
            providerProfilesRef.set(next);
            String error = OpenAiProviderProfiles.saveOrError(next);
            refreshProviderCombo.run();
            if (error != null) {
                showInfo("Provider-Profil", "Löschen gespeichert? " + error);
            }
        });
        refreshProviderCombo.run();
        HBox providerButtons = new HBox(8, providerApplyBtn, providerSaveBtn, providerDeleteBtn);
        providerButtons.setAlignment(Pos.CENTER_LEFT);
        Label providerLabel = new Label("Provider-Profil");
        providerLabel.getStyleClass().add("param-key-label");
        Label providerHelp = new Label(
                "Vorlagen für URL + API-Key (+ Modell). „Profil speichern“ oder globales „Speichern“ "
                        + "schreibt den Key mit. Beim Wechsel wird das vorherige Profil automatisch gesichert. "
                        + "Datei: ~/.manuskript/openai-provider-profiles.json");
        providerHelp.getStyleClass().add("param-help-label");
        providerHelp.setWrapText(true);
        providerHelp.setMaxWidth(680);
        VBox providerCard = new VBox(4);
        providerCard.getStyleClass().add("param-card");
        providerCard.getChildren().addAll(providerLabel, providerProfileCombo, providerButtons, providerHelp);
        openaiParams.getChildren().add(providerCard);
        openaiProviderProfilesSaveHook = () -> {
            String name = providerProfileCombo.getEditor() != null
                    ? providerProfileCombo.getEditor().getText()
                    : providerProfileCombo.getValue();
            if (name == null || name.isBlank()) {
                name = lastProviderProfileName.get();
            }
            OpenAiProviderProfiles.Profile snapshot = snapshotCurrentProfile.apply(name);
            if (snapshot != null) {
                String error = persistProfiles.apply(snapshot);
                if (error != null) {
                    logger.warn("Provider-Profil beim Speichern aller Parameter: {}", error);
                }
            }
        };

        Label openaiKeyLabel = new Label("agent.openai.api_key");
        openaiKeyLabel.getStyleClass().add("param-key-label");
        Label openaiKeyHelp = new Label(
                "API-Key fuer OpenAI/OpenRouter/Mammouth. Lokale Server akzeptieren oft den Platzhalter „local“.");
        openaiKeyHelp.getStyleClass().add("param-help-label");
        openaiKeyHelp.setWrapText(true);
        openaiKeyHelp.setMaxWidth(680);
        VBox openaiKeyCard = new VBox(4);
        openaiKeyCard.getStyleClass().add("param-card");
        openaiKeyCard.getChildren().addAll(openaiKeyLabel, openaiKeyField, openaiKeyHelp);
        openaiParams.getChildren().add(openaiKeyCard);
        keyToControl.put("agent.openai.api_key", openaiKeyField);

        Label openaiUrlLabel = new Label("agent.openai.api_url");
        openaiUrlLabel.getStyleClass().add("param-key-label");
        Label openaiUrlHelp = new Label(
                "Basis-URL der OpenAI-kompatiblen API (OpenAI, Mammouth, OpenRouter …).");
        openaiUrlHelp.getStyleClass().add("param-help-label");
        openaiUrlHelp.setWrapText(true);
        openaiUrlHelp.setMaxWidth(680);
        VBox openaiUrlCard = new VBox(4);
        openaiUrlCard.getStyleClass().add("param-card");
        openaiUrlCard.getChildren().addAll(openaiUrlLabel, openaiUrlField, openaiUrlHelp);
        openaiParams.getChildren().add(openaiUrlCard);
        keyToControl.put("agent.openai.api_url", openaiUrlField);

        Label openaiModelLabel = new Label("agent.openai.model");
        openaiModelLabel.getStyleClass().add("param-key-label");
        Label openaiModelHelp = new Label(
                "Modell fuer die OpenAI-Analyse (nach „Modelle laden“ auswählen oder frei eingeben). "
                        + "Kosten werden bei OpenRouter-kompatiblen APIs angezeigt.");
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

        ComboBox<String> reasoningCombo = new ComboBox<>();
        reasoningCombo.getItems().addAll("none", "low", "high");
        reasoningCombo.setValue(normalizeReasoningEffort(
                ResourceManager.getParameter("agent.openai.reasoning_effort", "low")));
        reasoningCombo.setPrefWidth(200);
        Label reasoningLabel = new Label("agent.openai.reasoning_effort");
        reasoningLabel.getStyleClass().add("param-key-label");
        Label reasoningHelp = new Label(
                "Nachdenken vor der Antwort. none = aus (schnell). low = wenig (empfohlen für DeepSeek v4 Flash). "
                        + "high = langes Nachdenken (kann Gateway-Timeouts auslösen).");
        reasoningHelp.getStyleClass().add("param-help-label");
        reasoningHelp.setWrapText(true);
        reasoningHelp.setMaxWidth(680);
        VBox reasoningCard = new VBox(4);
        reasoningCard.getStyleClass().add("param-card");
        reasoningCard.getChildren().addAll(reasoningLabel, reasoningCombo, reasoningHelp);
        openaiParams.getChildren().add(reasoningCard);
        keyToControl.put("agent.openai.reasoning_effort", reasoningCombo);

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
        Label extraPromptHelp = new Label("Zusätzliche Stil-Anweisungen, die Vorrang vor dem Standard-Lektorat-Prompt haben.");
        extraPromptHelp.getStyleClass().add("param-help-label");
        extraPromptHelp.setWrapText(true);
        extraPromptHelp.setMaxWidth(680);
        VBox extraPromptCard = new VBox(4);
        extraPromptCard.getStyleClass().add("param-card");
        extraPromptCard.getChildren().addAll(extraPromptLabel, extraPromptArea, extraPromptHelp);
        content.getChildren().add(extraPromptCard);
        keyToControl.put("api.lektorat.extra_prompt", extraPromptArea);

        // Lektorat-Fokus (Mehrfachauswahl)
        TextField typeField = new TextField(
                OnlineLektoratService.normalizeLektoratType(lektoratType != null ? lektoratType : "allgemein"));
        typeField.setMaxWidth(0);
        typeField.setMinWidth(0);
        typeField.setOpacity(0);
        typeField.setFocusTraversable(false);
        CheckBox cbStil = new CheckBox("Stil");
        CheckBox cbGrammatik = new CheckBox("Grammatik");
        CheckBox cbPlot = new CheckBox("Plot / Dramaturgie");
        List<String> selectedTypes = OnlineLektoratService.parseLektoratTypes(typeField.getText());
        cbStil.setSelected(selectedTypes.contains("stil"));
        cbGrammatik.setSelected(selectedTypes.contains("grammatik"));
        cbPlot.setSelected(selectedTypes.contains("plot"));
        Runnable syncTypeField = () -> {
            java.util.ArrayList<String> selected = new java.util.ArrayList<>();
            if (cbStil.isSelected()) {
                selected.add("stil");
            }
            if (cbGrammatik.isSelected()) {
                selected.add("grammatik");
            }
            if (cbPlot.isSelected()) {
                selected.add("plot");
            }
            typeField.setText(OnlineLektoratService.serializeLektoratTypes(selected));
        };
        cbStil.setOnAction(e -> syncTypeField.run());
        cbGrammatik.setOnAction(e -> syncTypeField.run());
        cbPlot.setOnAction(e -> syncTypeField.run());
        HBox typeRow = new HBox(12, cbStil, cbGrammatik, cbPlot);
        typeRow.setAlignment(Pos.CENTER_LEFT);
        Label typeLabel = new Label("api.lektorat.type");
        typeLabel.getStyleClass().add("param-key-label");
        Label typeHelp = new Label("Fokus des Lektorats (mehrere möglich). Keine Auswahl = Allgemein.");
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

    private void loadOllamaInstalledModels(String apiUrl, ComboBox<String> modelCombo) {
        String base = apiUrl != null && !apiUrl.isBlank()
                ? apiUrl.trim().replaceAll("/+$", "")
                : "http://localhost:11434";
        String current = comboEditableText(modelCombo);
        OllamaService service = new OllamaService();
        service.setBaseUrl(base);
        CompletableFuture.supplyAsync(() -> {
            try {
                return service.getAvailableModels().get(12, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) {
                return new String[]{"__ERROR__:" + e.getMessage()};
            }
        }).thenAccept(models -> Platform.runLater(() -> {
            if (models != null && models.length == 1 && models[0] != null && models[0].startsWith("__ERROR__:")) {
                showInfo("Ollama", "Modelle konnten nicht geladen werden: "
                        + models[0].substring("__ERROR__:".length())
                        + "\n\nLäuft Ollama unter " + base + "?");
                return;
            }
            modelCombo.getItems().clear();
            if (models != null) {
                for (String m : models) {
                    if (m != null && !m.isBlank() && !modelCombo.getItems().contains(m)) {
                        modelCombo.getItems().add(m);
                    }
                }
            }
            if (current != null && !current.isBlank()) {
                if (!modelCombo.getItems().contains(current)) {
                    modelCombo.getItems().add(0, current);
                }
                modelCombo.setValue(current);
                modelCombo.getEditor().setText(current);
            } else if (!modelCombo.getItems().isEmpty()) {
                modelCombo.getSelectionModel().selectFirst();
            }
            int n = modelCombo.getItems().size();
            showInfo("Ollama", n + " installierte Modell(e) geladen. Bitte wählen und Speichern.");
        }));
    }

    private void installOllamaModelFromParams(String apiUrl, ComboBox<String> installCombo,
                                             ComboBox<String> activeModelCombo, Button installBtn,
                                             Label statusLabel, ProgressIndicator busy) {
        String name = comboEditableText(installCombo);
        if (name == null || name.isBlank()) {
            showInfo("Ollama", "Bitte einen Modellnamen wählen oder eingeben.");
            return;
        }
        String base = apiUrl != null && !apiUrl.isBlank()
                ? apiUrl.trim().replaceAll("/+$", "")
                : "http://localhost:11434";
        installBtn.setDisable(true);
        busy.setVisible(true);
        busy.setManaged(true);
        statusLabel.setText("Installiere „" + name + "“ … (kann mehrere Minuten dauern)");
        OllamaService service = new OllamaService();
        service.installModel(name, base).whenComplete((result, ex) -> Platform.runLater(() -> {
            installBtn.setDisable(false);
            busy.setVisible(false);
            busy.setManaged(false);
            if (ex != null) {
                statusLabel.setText("Fehler: " + ex.getMessage());
                showInfo("Ollama", "Installation fehlgeschlagen: " + ex.getMessage());
                return;
            }
            String msg = result != null ? result : "Unbekanntes Ergebnis";
            statusLabel.setText(msg);
            if (msg.startsWith("✅")) {
                if (!activeModelCombo.getItems().contains(name)) {
                    activeModelCombo.getItems().add(name);
                }
                activeModelCombo.setValue(name);
                activeModelCombo.getEditor().setText(name);
                showInfo("Ollama", msg + "\n\nAls aktives Modell gesetzt – bitte Speichern klicken.");
            } else {
                showInfo("Ollama", msg);
            }
        }));
    }

    private static String comboEditableText(ComboBox<String> combo) {
        if (combo == null) {
            return "";
        }
        if (combo.isEditable() && combo.getEditor() != null) {
            String t = combo.getEditor().getText();
            if (t != null && !t.isBlank()) {
                return t.trim();
            }
        }
        String v = combo.getValue();
        return v != null ? v.trim() : "";
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

    static String normalizeReasoningEffort(String raw) {
        if (raw == null || raw.isBlank() || "auto".equalsIgnoreCase(raw.trim())) {
            return "low";
        }
        String v = raw.trim().toLowerCase(Locale.ROOT);
        if ("max".equals(v) || "xhigh".equals(v) || "medium".equals(v)) {
            return "high";
        }
        if ("disabled".equals(v) || "off".equals(v)) {
            return "none";
        }
        if ("none".equals(v) || "low".equals(v) || "high".equals(v)) {
            return v;
        }
        return "low";
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
        if (openaiProviderProfilesSaveHook != null) {
            openaiProviderProfilesSaveHook.run();
        }
        // Cache invalidieren und offene Kapitel-Editoren (Canvas + Legacy) neu laden
        if (keyToDef.containsKey("agent.openai.model")
                || keyToDef.containsKey("agent.ollama.model")
                || keyToDef.containsKey("agent.openai.api_url")
                || keyToDef.containsKey("agent.openai.api_key")
                || keyToDef.containsKey("agent.backend")) {
            com.manuskript.agent.AgentConfigManager.invalidateCache();
            java.util.List<com.manuskript.agent.AgentConfig> configs =
                    com.manuskript.agent.AgentConfigManager.loadConfigs();
            com.manuskript.agent.AgentConfigManager.saveConfigs(configs);
            MainController.notifyOpenEditorsAgentParametersChanged();
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
            @SuppressWarnings("unchecked")
            ComboBox<String> cb = (ComboBox<String>) c;
            if (d != null && cb.getItems().contains(d)) {
                cb.setValue(d);
            } else {
                cb.getSelectionModel().clearSelection();
                if (cb.isEditable() && cb.getEditor() != null) {
                    cb.getEditor().setText(d != null ? d : "");
                }
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
