package com.manuskript;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.Optional;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

/**
 * Fenster für den Ollama KI-Assistenten
 */
public class OllamaWindow {
    private static final Logger logger = Logger.getLogger(OllamaWindow.class.getName());
    
    private CustomStage stage;
    private OllamaService ollamaService;
    private EditorWindow editorWindow;
    private int currentThemeIndex = 0;
    private java.util.prefs.Preferences preferences;
    private PluginManager pluginManager;

    private TextArea inputArea;
    private TextArea contextArea;
    private ComboBox<String> modelComboBox;
    private ComboBox<String> functionComboBox;
    private Button generateButton;
    private Button previewPromptButton;
    private Button insertButton;
    private ProgressIndicator progressIndicator;
    private Label statusLabel;
    // Streaming-Handle für Abbrechen/Fortschritt
    private OllamaService.StreamHandle currentStreamHandle;
    private Button openResultWindowButton;
    private HBox resultButtonRow;
    // Persistentes Ergebnisfenster (WebView), damit es live aktualisiert werden kann
    private CustomStage resultStage;
    private javafx.scene.web.WebView resultWebView;
    private String lastPureChatAnswer = ""; // Speichert den reinen Chat-Text ohne Header
    
    // Spezielle Eingabefelder für verschiedene Funktionen
    private TextField characterField;
    private TextField situationField;
    private TextField emotionField;
    private TextField styleField;
    private TextField genreField;
    private ComboBox<String> rewriteTypeComboBox;
    private TextField additionalInstructionsField;
    private VBox specialFieldsBox;
    
    // Boxen für verschiedene Funktionen
    private HBox dialogBox;
    private HBox descBox;
    private HBox plotBox;
    private HBox rewriteBox;
    private VBox trainingBox;
    
    // Modell-Training Felder
    private TextField trainingModelNameField;
    private ComboBox<String> baseModelComboBox;
    private TextArea trainingDataArea;
    private Button startTrainingButton;
    private Button stopTrainingButton;
    private ProgressIndicator trainingProgressIndicator;
    
    // Parameter-Kontrollen
    private Slider temperatureSlider;
    private Slider maxTokensSlider;
    private Slider topPSlider;
    private Slider repeatPenaltySlider;
    private Label temperatureLabel;
    private Label maxTokensLabel;
    private Label topPLabel;
    private Label repeatPenaltyLabel;
    private VBox parametersBox;
    
            // Modell-Management
        private Button deleteModelButton;
        private ComboBox<String> deleteModelComboBox;
    
            // Modell-Installation
        private Button installModelButton;
        private ComboBox<String> installModelComboBox;
        private TextField installModelNameField; // Freitext-Eingabe für Modellnamen
        private Button installModelByNameButton; // Install per Name
        private Button openModelLibraryButton;   // Link zur Modellbibliothek
        private ProgressIndicator installProgressIndicator;
        private Label installStatusLabel;
        
        // Plugin-Verwaltung
        private Button openPluginFolderButton;
        private Button reloadPluginsButton;
    
    // Training-Status
    private Label trainingStatusLabel;
    
    // Chat-Session-Management
    private ComboBox<String> sessionComboBox;
    private Button newSessionButton;
    private Button deleteSessionButton;
    private Button clearContextButton;
    private CustomChatArea chatHistoryArea;
    private VBox chatSessionBox;
    
    // Einfache Session-Verwaltung mit QAPairs
    private Map<String, List<CustomChatArea.QAPair>> sessionHistories = new HashMap<>();
    private String currentSessionName = "default";
    

    
    // UI-Kontrollen für wegklappbare Bereiche
    private ToggleButton toggleUpperPanelButton;

    private VBox upperPanel;

    private VBox lowerPanel;
    
    // Labels und Ergebnis-TextArea für dynamisches Layout
    private Label inputLabel;
    private Label chatLabel;
    private Label contextLabel;
    private Label resultLabel;
    private TextArea resultArea;

    // Kontext-Accordion und Bereiche
    private Accordion contextAccordion;
    private CheckBox cbUserContext;
    private CheckBox cbEditorSnippet;
    private CheckBox cbSynopsis;
    private CheckBox cbCharacters;
    private CheckBox cbWorldbuilding;
    private CheckBox cbOutline;
    private CheckBox cbChapterNotes;
    private CheckBox cbStyle;
    private CheckBox cbNovelContext;

    private TextArea synopsisArea;
    private TextArea charactersArea;
    private TextArea worldbuildingArea;
    private TextArea outlineArea;
    private TextArea chapterNotesArea;
    private TextArea styleNotesArea;
    private TitledPane tpSynopsis;
    private TitledPane tpCharacters;
    private TitledPane tpWorldbuilding;
    private TitledPane tpOutline;
    private TitledPane tpChapter;
    private TitledPane tpStyle;
    
    // Merker: zuletzt automatisch übernommene Selektion
    private String lastAutoFilledFromSelection;
    
    // Flag entfernt - nicht mehr benötigt
    
    public OllamaWindow() {
        this.ollamaService = new OllamaService();
        this.preferences = java.util.prefs.Preferences.userNodeForPackage(OllamaWindow.class);
        this.pluginManager = new PluginManager();
        createWindow();
    }
    
    private void createWindow() {
        stage = StageManager.createStage("KI-Assistent - Ollama");
        stage.setWidth(800);   // Standard-Breite (schmaler gemacht)
        stage.setHeight(1100);  // Standard-Höhe (höher gemacht)
        stage.setMinWidth(400);
        stage.setMinHeight(700);
        
        // Haupt-Layout
        VBox mainLayout = new VBox(5);
        mainLayout.setPadding(new Insets(15));
        mainLayout.getStyleClass().add("ollama-container");
        
        // Toggle-Button für oberen Bereich
        toggleUpperPanelButton = new ToggleButton("⚙️ Einstellungen ein-/ausblenden");
        toggleUpperPanelButton.setSelected(true);
        toggleUpperPanelButton.setOnAction(e -> toggleUpperPanel());
        
        // Oberer Bereich (einklappbar)
        upperPanel = new VBox(10);
        upperPanel.setVisible(true);
        upperPanel.setManaged(true);
        
        // Modell-Auswahl
        HBox modelBox = new HBox(10);
        modelBox.setAlignment(Pos.CENTER_LEFT);
        
        Label modelLabel = new Label("Modell:");
        modelLabel.setPrefWidth(120);
        modelLabel.setMinWidth(120);
        modelLabel.setMaxWidth(120);
        modelComboBox = new ComboBox<>();
        modelComboBox.setId("cmbModel");
        modelComboBox.setPromptText("Lade Modelle...");
        modelComboBox.setPrefWidth(200);
        
        // Modelle dynamisch laden
        loadAvailableModels();
        
        modelComboBox.setOnAction(e -> {
            String selectedModel = modelComboBox.getValue();
            ollamaService.setModel(selectedModel);
            saveSelectedModel(selectedModel);
            updateStatus("Modell gewechselt: " + selectedModel);
        });
        
        modelBox.getChildren().addAll(modelLabel, modelComboBox);
        
        // Funktion-Auswahl
        HBox functionBox = new HBox(10);
        functionBox.setAlignment(Pos.CENTER_LEFT);
        
        Label functionLabel = new Label("Funktion:");
        functionLabel.setPrefWidth(120);
        functionLabel.setMinWidth(120);
        functionLabel.setMaxWidth(120);
        functionComboBox = new ComboBox<>();
        functionComboBox.setId("cmbFunction");
        functionComboBox.setPrefWidth(200);
        // Funktionen laden
        List<String> functionItems = new ArrayList<>();
        functionItems.add("Dialog generieren");
        functionItems.add("Charakter entwickeln");
        functionItems.add("Schreibstil analysieren");
        functionItems.add("Chat-Assistent");
        functionItems.add("Modell-Training");
        
        // Plugins hinzufügen
        if (pluginManager != null) {
            functionItems.add(""); // Trennlinie
            functionItems.add("📦 Plugins");
            
            // Plugins nach Kategorien gruppieren
            Set<String> categories = pluginManager.getCategories();
            for (String category : categories) {
                List<Plugin> categoryPlugins = pluginManager.getPluginsByCategory(category);
                for (Plugin plugin : categoryPlugins) {
                    functionItems.add("📦 " + plugin.getName());
                }
            }
        }
        
        functionComboBox.getItems().addAll(functionItems);
        // Lade die gespeicherte Funktion oder verwende "Chat-Assistent" als Standard
        String savedFunction = loadSelectedFunction();
        // Prüfe, ob die gespeicherte Funktion noch verfügbar ist
        if (!functionComboBox.getItems().contains(savedFunction)) {
            savedFunction = "Chat-Assistent"; // Fallback
        }
        functionComboBox.setValue(savedFunction);
        functionComboBox.setOnAction(e -> {
            String selectedFunction = functionComboBox.getValue();
            saveSelectedFunction(selectedFunction);
            updateInputFields();
            
            // Plugin-Vorschau anzeigen
            if (selectedFunction != null && selectedFunction.startsWith("📦 ")) {
                String pluginName = selectedFunction.substring(2); // "📦 " entfernen
                Plugin plugin = pluginManager.getPlugin(pluginName);
                if (plugin != null) {
                    // Plugin-Beschreibung in der Statusleiste anzeigen
                    updateStatus("📦 " + plugin.getName() + ": " + plugin.getDescription());
                    
                    // Plugin-Prompt als Placeholder im Input-Bereich anzeigen
                    String promptPreview = plugin.getPrompt();
                    if (promptPreview.length() > 200) {
                        promptPreview = promptPreview.substring(0, 200) + "...";
                    }
                    inputArea.setPromptText("Plugin: " + promptPreview);
                }
            } else {
                // Normale Funktionen zurücksetzen
                inputArea.setPromptText("Geben Sie hier Ihren Text ein...");
            }
        });
        
        functionBox.getChildren().addAll(functionLabel, functionComboBox);
        
        // Parameter-Kontrollen
        parametersBox = new VBox(5);
        parametersBox.setVisible(true);
        parametersBox.setManaged(true);
        
        // Temperatur-Slider
        HBox tempBox = new HBox(10);
        tempBox.setAlignment(Pos.CENTER_LEFT);
        
        double temperatureValue = ResourceManager.getDoubleParameter("ollama.temperature", 0.5);
        temperatureLabel = new Label(String.format("Temperatur: %.2f", temperatureValue));
        temperatureLabel.setPrefWidth(120);
        temperatureLabel.setMinWidth(120);
        temperatureLabel.setMaxWidth(120);
        temperatureSlider = new Slider(0.0, 2.0, temperatureValue);
        temperatureSlider.setShowTickLabels(true);
        temperatureSlider.setShowTickMarks(true);
        temperatureSlider.setMajorTickUnit(0.5);
        temperatureSlider.setMinorTickCount(4);
        temperatureSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            double temp = Math.round(newVal.doubleValue() * 100.0) / 100.0;
            temperatureLabel.setText(String.format("Temperatur: %.2f", temp));
            ollamaService.setTemperature(temp);
        });
        
        tempBox.getChildren().addAll(temperatureLabel, temperatureSlider);
        
        // Max Tokens-Slider
        HBox tokensBox = new HBox(10);
        tokensBox.setAlignment(Pos.CENTER_LEFT);
        
        int maxTokensValue = ResourceManager.getIntParameter("ollama.max_tokens", 2048);
        maxTokensLabel = new Label(String.format("Max Tokens: %d", maxTokensValue));
        maxTokensLabel.setPrefWidth(120);
        maxTokensLabel.setMinWidth(120);
        maxTokensLabel.setMaxWidth(120);
        maxTokensSlider = new Slider(100, 32768, maxTokensValue);
        

        maxTokensSlider.setShowTickLabels(true);
        maxTokensSlider.setShowTickMarks(true);
        maxTokensSlider.setMajorTickUnit(5000);
        maxTokensSlider.setMinorTickCount(4);
        maxTokensSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            int tokens = newVal.intValue();
            maxTokensLabel.setText(String.format("Max Tokens: %d", tokens));
            ollamaService.setMaxTokens(tokens);
        });
        
        tokensBox.getChildren().addAll(maxTokensLabel, maxTokensSlider);
        
        // Top-P-Slider
        HBox topPBox = new HBox(10);
        topPBox.setAlignment(Pos.CENTER_LEFT);
        
        double topPValue = ResourceManager.getDoubleParameter("ollama.top_p", 0.8);
        topPLabel = new Label(String.format("Top-P: %.2f", topPValue));
        topPLabel.setPrefWidth(120);
        topPLabel.setMinWidth(120);
        topPLabel.setMaxWidth(120);
        topPSlider = new Slider(0.0, 1.0, topPValue);
        topPSlider.setShowTickLabels(true);
        topPSlider.setShowTickMarks(true);
        topPSlider.setMajorTickUnit(0.2);
        topPSlider.setMinorTickCount(3);
        topPSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            double topP = Math.round(newVal.doubleValue() * 100.0) / 100.0;
            topPLabel.setText(String.format("Top-P: %.2f", topP));
            ollamaService.setTopP(topP);
        });
        
        topPBox.getChildren().addAll(topPLabel, topPSlider);
        
        // Repeat Penalty-Slider
        HBox penaltyBox = new HBox(10);
        penaltyBox.setAlignment(Pos.CENTER_LEFT);
        
        double repeatPenaltyValue = ResourceManager.getDoubleParameter("ollama.repeat_penalty", 1.2);
        repeatPenaltyLabel = new Label(String.format("Repeat Penalty: %.2f", repeatPenaltyValue));
        repeatPenaltyLabel.setPrefWidth(120);
        repeatPenaltyLabel.setMinWidth(120);
        repeatPenaltyLabel.setMaxWidth(120);
        repeatPenaltySlider = new Slider(0.0, 2.0, repeatPenaltyValue);
        repeatPenaltySlider.setShowTickLabels(true);
        repeatPenaltySlider.setShowTickMarks(true);
        repeatPenaltySlider.setMajorTickUnit(0.5);
        repeatPenaltySlider.setMinorTickCount(4);
        repeatPenaltySlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            double penalty = Math.round(newVal.doubleValue() * 100.0) / 100.0;
            repeatPenaltyLabel.setText(String.format("Repeat Penalty: %.2f", penalty));
            ollamaService.setRepeatPenalty(penalty);
        });
        
        penaltyBox.getChildren().addAll(repeatPenaltyLabel, repeatPenaltySlider);
        
        // Slider in perfekt symmetrischem 2x2 Layout anordnen
        VBox leftColumn = new VBox(10);
        leftColumn.setAlignment(Pos.BASELINE_LEFT);
        leftColumn.setSpacing(10);
        leftColumn.setPrefWidth(300);
        leftColumn.getChildren().addAll(tempBox, tokensBox);
        
        VBox rightColumn = new VBox(10);
        rightColumn.setAlignment(Pos.CENTER_LEFT);
        rightColumn.setSpacing(10);
        rightColumn.setPrefWidth(300);
        rightColumn.getChildren().addAll(topPBox, penaltyBox);
        
        HBox sliderContainer = new HBox(40);
        sliderContainer.setAlignment(Pos.CENTER_LEFT);
        sliderContainer.getChildren().addAll(leftColumn, rightColumn);
        
        parametersBox.getChildren().addAll(sliderContainer);
        
        // Modell-Management
        HBox modelManagementBox = new HBox(10);
        modelManagementBox.setAlignment(Pos.CENTER_LEFT);
        
        Label deleteModelLabel = new Label("Modell löschen:");
        deleteModelLabel.setPrefWidth(120);
        deleteModelLabel.setMinWidth(120);
        deleteModelLabel.setMaxWidth(120);      
        deleteModelComboBox = new ComboBox<>();
        deleteModelComboBox.setPromptText("Modell auswählen...");
        deleteModelComboBox.setPrefWidth(200);
        
        deleteModelButton = new Button("🗑 Löschen");
        deleteModelButton.setStyle("-fx-background-color: #e57373; -fx-text-fill: white;");
        deleteModelButton.setOnAction(e -> deleteSelectedModel());
        deleteModelButton.setTooltip(new Tooltip("Löscht das ausgewählte Modell unwiderruflich"));
        
        modelManagementBox.getChildren().addAll(deleteModelLabel, deleteModelComboBox, deleteModelButton);
        
        // Modell-Installation
        HBox modelInstallationBox = new HBox(10);
        modelInstallationBox.setAlignment(Pos.CENTER_LEFT);
        
        Label installModelLabel = new Label("Modell installieren:");
        installModelLabel.setPrefWidth(120);
        installModelLabel.setMinWidth(120);
        installModelLabel.setMaxWidth(120);
        installModelComboBox = new ComboBox<>();
        installModelComboBox.setPromptText("Modell auswählen...");
        installModelComboBox.setPrefWidth(200);
        
        // Empfohlene Modelle für kreatives Schreiben laden
        String[] recommendedModels = ollamaService.getRecommendedCreativeWritingModels();
        installModelComboBox.getItems().addAll(recommendedModels);
        
        installModelButton = new Button("📥 Installieren");
        installModelButton.setStyle("-fx-background-color: #81c784; -fx-text-fill: white;");
        installModelButton.setOnAction(e -> installSelectedModel());
        installModelButton.setTooltip(new Tooltip("Installiert das ausgewählte Modell von Ollama"));
        
        installProgressIndicator = new ProgressIndicator();
        installProgressIndicator.setVisible(false);
        installProgressIndicator.setPrefSize(20, 20);
        installProgressIndicator.setStyle("-fx-progress-color: #44aa44;");
        
        installStatusLabel = new Label("Bereit für Installation");
        installStatusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #666;");
        
        // NEU: Freitextfeld + Install-by-name-Button + Link
        installModelNameField = new TextField();
        installModelNameField.setPromptText("oder Modellname eingeben, z. B. llama3:8b");
        installModelNameField.setPrefWidth(200);

        installModelByNameButton = new Button("Installieren (Name)");
        installModelByNameButton.setOnAction(e -> installModelByName());
        installModelByNameButton.setTooltip(new Tooltip("Installiert das Modell anhand des eingegebenen Namens"));

        openModelLibraryButton = new Button("🔗 Modellbibliothek");
        openModelLibraryButton.setOnAction(e -> openOllamaWebsite());
        openModelLibraryButton.setTooltip(new Tooltip("Öffnet die Ollama Modell-Seite im Browser"));

        modelInstallationBox.getChildren().addAll(
            installModelLabel,
            installModelComboBox,
            installModelButton,
            installModelNameField,
            installModelByNameButton,
            openModelLibraryButton,
            installProgressIndicator,
            installStatusLabel
        );
        
        // Plugin-Verwaltung
        HBox pluginBox = new HBox(10);
        pluginBox.setAlignment(Pos.CENTER_LEFT);
        
        Label pluginLabel = new Label("Plugins:");
        pluginLabel.setPrefWidth(120);
        pluginLabel.setMinWidth(120);
        pluginLabel.setMaxWidth(120);
        
        openPluginFolderButton = new Button("📁 Ordner öffnen");
        openPluginFolderButton.setPrefWidth(120);
        openPluginFolderButton.setOnAction(e -> openPluginFolder());
        openPluginFolderButton.setTooltip(new Tooltip("Öffnet den Plugin-Ordner zum Bearbeiten"));
        
        reloadPluginsButton = new Button("🔄 Neu laden");
        reloadPluginsButton.setPrefWidth(120);
        reloadPluginsButton.setOnAction(e -> reloadPlugins());
        reloadPluginsButton.setTooltip(new Tooltip("Lädt alle Plugins neu"));
        
        Button pluginEditorButton = new Button("✏️ Editor");
        pluginEditorButton.setPrefWidth(120);
        pluginEditorButton.setOnAction(e -> openPluginEditor());
        pluginEditorButton.setTooltip(new Tooltip("Öffnet den Plugin Editor"));
        
        pluginBox.getChildren().addAll(pluginLabel, openPluginFolderButton, reloadPluginsButton, pluginEditorButton);
        
        // Chat-Session-Management
        chatSessionBox = new VBox(10);
        chatSessionBox.setVisible(false);
        chatSessionBox.setManaged(false);
        
        Label sessionLabel = new Label("Chat-Session:");
        sessionComboBox = new ComboBox<>();
        sessionComboBox.setPromptText("Session wählen...");
        sessionComboBox.getItems().add("default");
        sessionComboBox.setValue("default");
        sessionComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(oldVal)) {
                switchToSession(newVal);
                // Speichere die ausgewählte Session in den Preferences
                ResourceManager.saveParameter("ui.selected_session", newVal);
            }
        });
        
        newSessionButton = new Button("Neue Session");
        newSessionButton.setOnAction(e -> createNewSession());
        
        deleteSessionButton = new Button("Session löschen");
        deleteSessionButton.setOnAction(e -> deleteCurrentSession());
        
        clearContextButton = new Button("Kontext löschen");
        clearContextButton.setOnAction(e -> clearCurrentSession());
        
        HBox sessionControlsBox = new HBox(10);
        sessionControlsBox.setAlignment(Pos.CENTER_LEFT);
        sessionControlsBox.getChildren().addAll(sessionLabel, sessionComboBox, newSessionButton, deleteSessionButton, clearContextButton);
        
        // Chat-Historie (im oberen Bereich, damit sie nicht wegklappt)
        chatHistoryArea = new CustomChatArea();
        chatHistoryArea.setMinHeight(400);
        chatHistoryArea.getStyleClass().addAll("ollama-text-area");
        chatHistoryArea.setOnDisplayChange(() -> {
            if (resultStage != null && resultStage.isShowing() && resultWebView != null) {
                String currentAnswer = chatHistoryArea.getCurrentAnswer();
                if (currentAnswer == null) currentAnswer = "";
                updateResultWebView(buildHtmlForAnswer(currentAnswer), false);
            }
        });
        
        // Sessions aus Config laden (NACH der chatHistoryArea-Initialisierung)
        loadAllSessions();
        
        // Default Session initialisieren falls nicht vorhanden
        if (!sessionHistories.containsKey("default")) {
            sessionHistories.put("default", new ArrayList<>());
        }
        
        // Lade die zuletzt ausgewählte Session
        loadSelectedSession();
        
        // Kein Auto-Scrolling mehr - wird jetzt manuell gesteuert
        
        // Session-Controls im oberen Bereich (Chat-Historie ist jetzt im unteren Bereich)
        chatSessionBox.getChildren().addAll(sessionControlsBox);
        
        // Spezielle Eingabefelder (anfangs versteckt)
        this.specialFieldsBox = new VBox(5);
        this.specialFieldsBox.setVisible(false);
        this.specialFieldsBox.setManaged(false);
        
        // Dialog-Felder
        this.dialogBox = new HBox(10);
        dialogBox.setAlignment(Pos.CENTER_LEFT);
        
        Label charLabel = new Label("Charakter:");
        characterField = new TextField();
        characterField.setPromptText("Name des Charakters");
        characterField.setPrefWidth(150);
        
        Label sitLabel = new Label("Situation:");
        situationField = new TextField();
        situationField.setPromptText("Beschreibung der Situation");
        situationField.setPrefWidth(200);
        
        Label emoLabel = new Label("Emotion:");
        emotionField = new TextField();
        emotionField.setPromptText("Emotion des Charakters");
        emotionField.setPrefWidth(150);
        
        dialogBox.getChildren().addAll(charLabel, characterField, sitLabel, situationField, emoLabel, emotionField);
        
        // Beschreibungs-Felder
        this.descBox = new HBox(10);
        descBox.setAlignment(Pos.CENTER_LEFT);
        
        Label styleLabel = new Label("Stil:");
        styleField = new TextField();
        styleField.setPromptText("Beschreibungsstil (detailliert, atmosphärisch, etc.)");
        styleField.setPrefWidth(250);
        
        descBox.getChildren().addAll(styleLabel, styleField);
        
        // Plot-Felder
        this.plotBox = new HBox(10);
        plotBox.setAlignment(Pos.CENTER_LEFT);
        
        Label genreLabel = new Label("Genre:");
        genreField = new TextField();
        genreField.setPromptText("Genre der Geschichte");
        genreField.setPrefWidth(200);
        
        plotBox.getChildren().addAll(genreLabel, genreField);
        
        // Umschreibungs-Felder
        this.rewriteBox = new HBox(10);
        rewriteBox.setAlignment(Pos.CENTER_LEFT);
        
        Label rewriteTypeLabel = new Label("Umschreibungsart:");
        rewriteTypeComboBox = new ComboBox<>();
        rewriteTypeComboBox.getItems().addAll(
            "Kürzer",
            "Ausführlicher", 
            "Füllwörter ersetzen",
            "Show don't tell",
            "Aktiver Stil",
            "Passiver Stil",
            "Formeller",
            "Informeller",
            "Poetischer",
            "Nüchterner",
            "Spannender",
            "Ruhiger"
        );
        rewriteTypeComboBox.setPromptText("Wählen Sie eine Umschreibungsart");
        rewriteTypeComboBox.setPrefWidth(150);
        
        Label additionalLabel = new Label("Zusätzliche Anweisungen:");
        additionalInstructionsField = new TextField();
        additionalInstructionsField.setPromptText("Optionale spezielle Anweisungen...");
        additionalInstructionsField.setPrefWidth(200);
        
        Button reloadTextButton = new Button("Text neu laden");
        reloadTextButton.setOnAction(e -> reloadSelectedText());
        reloadTextButton.setTooltip(new Tooltip("Lädt den aktuell selektierten Text aus dem Editor neu"));
        
        rewriteBox.getChildren().addAll(rewriteTypeLabel, rewriteTypeComboBox, additionalLabel, additionalInstructionsField, reloadTextButton);
        
        // Modell-Training Felder
        this.trainingBox = new VBox(10);
        trainingBox.setAlignment(Pos.TOP_LEFT);
        
        // Modell-Name und Basis-Modell
        HBox modelConfigBox = new HBox(10);
        modelConfigBox.setAlignment(Pos.CENTER_LEFT);
        
        Label modelNameLabel = new Label("Modell-Name:");
        trainingModelNameField = new TextField();
        trainingModelNameField.setPromptText("z.B. mein-roman-modell");
        trainingModelNameField.setPrefWidth(150);
        
        Label baseModelLabel = new Label("Basis-Modell:");
        baseModelLabel.setPrefWidth(120);
        baseModelLabel.setMinWidth(120);
        baseModelLabel.setMaxWidth(120);
        baseModelComboBox = new ComboBox<>();
        baseModelComboBox.getItems().addAll("mistral:7b-instruct", "gemma3:4b", "llama3.1:8b-instruct");
        baseModelComboBox.setValue("mistral:7b-instruct");
        baseModelComboBox.setPrefWidth(150);
        
        modelConfigBox.getChildren().addAll(modelNameLabel, trainingModelNameField, baseModelLabel, baseModelComboBox);
        
        // Training-Parameter (entfernt - Ollama Modelfiles unterstützen keine Training-Parameter)
        
        // Training-Daten
        Label trainingDataLabel = new Label("Training-Daten (deine Romane):");
        trainingDataArea = new TextArea();
        trainingDataArea.setPromptText("Füge hier deine Roman-Texte ein...\n\nBeispiel:\n=== Kapitel 1 ===\nDer Wind ging kaum. Nur das Rascheln vereinzelter Blätter...\n\n=== Kapitel 2 ===\nKalem betrat den Saal...");
        trainingDataArea.setPrefRowCount(8);
        trainingDataArea.setWrapText(true);
        trainingDataArea.getStyleClass().add("ollama-text-area");
        
        // Training-Buttons
        HBox trainingButtonsBox = new HBox(10);
        trainingButtonsBox.setAlignment(Pos.CENTER);
        
        startTrainingButton = new Button("Training starten");
        startTrainingButton.setOnAction(e -> startModelTraining());
        
        stopTrainingButton = new Button("Training stoppen");
        stopTrainingButton.setOnAction(e -> stopModelTraining());
        stopTrainingButton.setDisable(true);
        
        trainingProgressIndicator = new ProgressIndicator();
        trainingProgressIndicator.setVisible(false);
        trainingProgressIndicator.setPrefSize(40, 40);
        trainingProgressIndicator.setMaxSize(40, 40);
        trainingProgressIndicator.setStyle("-fx-progress-color: #4CAF50; -fx-background-color: transparent;");
        
        // Status-Label für Training
        trainingStatusLabel = new Label("Bereit für Training");
        trainingStatusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #666;");
        
        HBox trainingStatusBox = new HBox(10);
        trainingStatusBox.setAlignment(Pos.CENTER);
        trainingStatusBox.getChildren().addAll(trainingProgressIndicator, trainingStatusLabel);
        
        trainingButtonsBox.getChildren().addAll(startTrainingButton, stopTrainingButton, trainingStatusBox);
        
        trainingBox.getChildren().addAll(modelConfigBox, trainingDataLabel, trainingDataArea, trainingButtonsBox);
        
        specialFieldsBox.getChildren().addAll(dialogBox, descBox, plotBox, rewriteBox, trainingBox);
        
        // Eingabe-Bereich
        inputArea = new TextArea();
        inputArea.setPromptText("Geben Sie hier Ihren Text oder Prompt ein...");
        inputArea.setPrefRowCount(4);
        inputArea.setWrapText(true);
        inputArea.getStyleClass().add("ollama-text-area");
        
        // Click-Event für Chatverlauf wird später gesetzt, nach vollständiger Initialisierung
        
        // Buttons
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER);
        
        generateButton = new Button("Generieren");
        generateButton.setOnAction(e -> generateContent());
        // Kleiner Icon-Button für Prompt-Vorschau
        previewPromptButton = new Button("\uD83D\uDD0D"); // Lupe-Icon
        previewPromptButton.setMinWidth(32);
        previewPromptButton.setPrefWidth(32);
        previewPromptButton.setMaxHeight(28);
        previewPromptButton.setFocusTraversable(false);
        // Farbkontrast über Theme
        previewPromptButton.setStyle("-fx-font-size: 12px; -fx-padding: 2 6; -fx-background-radius: 6;");
        previewPromptButton.getStyleClass().add("prompt-preview");
        Tooltip.install(previewPromptButton, new Tooltip("Prompt-Vorschau anzeigen"));
        previewPromptButton.setOnAction(e -> {
            try {
                String fullPrompt = buildCurrentFullPromptPreview();
                String formatted = formatPromptSimple(fullPrompt);
                showPromptPreviewStage("Anfrage an Ollama (Prompt)", formatted);
            } catch (Exception ex) {
                showAlert("Fehler", "Konnte Prompt nicht erzeugen: " + ex.getMessage());
            }
        });
        
        insertButton = new Button("In Editor einfügen");
        insertButton.setOnAction(e -> insertToEditor());
        insertButton.setDisable(true);
        

        
        progressIndicator = new ProgressIndicator();
        progressIndicator.setVisible(false);
        progressIndicator.setPrefSize(20, 20);
        
        Button cancelButton = new Button("Abbrechen");
        cancelButton.setDisable(true);
        cancelButton.setOnAction(e -> cancelOllamaRequest());
        
        statusLabel = new Label("Bereit");
        
        VBox buttonArea = new VBox(6);
        buttonArea.setAlignment(Pos.CENTER);
        HBox statusRow = new HBox(statusLabel);
        statusRow.setAlignment(Pos.CENTER);
        buttonArea.getChildren().addAll(buttonBox, statusRow);
        
        openResultWindowButton = new Button("Ergebnis in Fenster");
        openResultWindowButton.setOnAction(e -> openResultInWindow());
        buttonBox.getChildren().addAll(generateButton, previewPromptButton, insertButton, cancelButton, progressIndicator);
        // Button mittig unter der Antwortbox (wird unten in leftContent eingefügt)
        resultButtonRow = new HBox(openResultWindowButton);
        resultButtonRow.setAlignment(Pos.CENTER);
        resultButtonRow.setPadding(new Insets(0));
        resultButtonRow.setSpacing(0);
        resultButtonRow.setFillHeight(false);
        VBox.setVgrow(resultButtonRow, Priority.NEVER);
        resultButtonRow.setMinHeight(Region.USE_PREF_SIZE);
        resultButtonRow.setPrefHeight(Region.USE_COMPUTED_SIZE);
        resultButtonRow.setMaxHeight(Region.USE_PREF_SIZE);
        // Box-Klasse für gezieltes Styling per CSS
        resultButtonRow.getStyleClass().add("result-button-row");
        
        // Button-Bereich wird im linken Content-Bereich eingefügt, nicht direkt ins mainLayout
        
        // Kontext-Bereich (kleiner, da Chat-Verlauf wichtiger ist)
        contextArea = new TextArea();
        contextArea.setPromptText("Hier können Sie zusätzlichen Kontext eingeben, der bei jeder Anfrage an den Assistenten gesendet wird. " +
            "Z.B. Charaktere, Setting, Stil-Anweisungen, spezielle Anweisungen für den Assistenten, oder allgemeine Regeln für die Antworten. " +
            "Dieser Kontext wird automatisch zu jeder Chat-Nachricht hinzugefügt.");
        contextArea.setPrefRowCount(6);
        contextArea.setMinHeight(120);
        contextArea.setWrapText(true);
        contextArea.getStyleClass().add("ollama-text-area");
        contextArea.setEditable(true); // Jetzt editierbar
        
        // Auto-Scroll für Kontext-Bereich und automatisches Speichern (konsequent über NovelManager)
        contextArea.textProperty().addListener((obs, oldText, newText) -> {
            Platform.runLater(() -> {
                contextArea.setScrollTop(Double.MAX_VALUE);
                String dir = getNovelDirectory();
                if (dir != null && !dir.isEmpty()) {
                    try {
                        writeText(new File(dir, NovelManager.CONTEXT_FILE), newText);
                    } catch (Exception e) {
                        logger.warning("Fehler beim automatischen Speichern des Contexts: " + e.getMessage());
                    }
                }
            });
        });
        
        // Oberen Bereich mit allen Einstellungen füllen
        upperPanel.getChildren().addAll(
            functionBox,
            modelBox,
            modelManagementBox,
            modelInstallationBox,
            pluginBox,
            parametersBox
        );
        
        // Unterer Bereich (immer sichtbar) - Chat-Verlauf ist das Hauptfeld
        lowerPanel = new VBox(10);
        
        // Labels für dynamische Verwaltung
        inputLabel = new Label("Eingabe:");
        chatLabel = new Label("Chat-Verlauf:");
        contextLabel = new Label("Zusätzlicher Kontext (Textbox) und Projekt-Kontexte:");
        resultLabel = new Label("Ergebnis:");
        
        // Ergebnis-TextArea für "Text umschreiben"
        resultArea = new TextArea();
        resultArea.setPromptText("Hier wird das umgeschriebene Ergebnis angezeigt...");
        resultArea.setPrefRowCount(8);
        resultArea.setWrapText(true);
        resultArea.getStyleClass().add("ollama-text-area");
        resultArea.setEditable(false); // Nur lesbar
        
        // Initiale Konfiguration
        updateLowerPanelLayout("Chat-Assistent", inputLabel, chatLabel, contextLabel, resultLabel, resultArea);
        
        // Kontext-Accordion und Master-Checkboxen
        contextAccordion = new Accordion();
        contextAccordion.setVisible(true);
        contextAccordion.setManaged(true);
        contextAccordion.setMinHeight(150);

        // Checkbox-Gruppen untereinander für bessere Lesbarkeit
        VBox contextCheckboxBar = new VBox(6);
        HBox row1 = new HBox(10); row1.setAlignment(Pos.CENTER_LEFT);
        HBox row2 = new HBox(10); row2.setAlignment(Pos.CENTER_LEFT);
        HBox row3 = new HBox(10); row3.setAlignment(Pos.CENTER_LEFT);
        cbUserContext = new CheckBox("Textbox"); cbUserContext.setSelected(true);
        cbEditorSnippet = new CheckBox("Editor-Ausschnitt"); cbEditorSnippet.setSelected(true);
        cbSynopsis = new CheckBox("Synopsis");
        cbCharacters = new CheckBox("Charaktere");
        cbWorldbuilding = new CheckBox("Worldbuilding");
        cbOutline = new CheckBox("Outline");
        cbChapterNotes = new CheckBox("Kapitelnotizen");
        cbStyle = new CheckBox("Stil");
        cbNovelContext = new CheckBox("Roman-Kontext"); cbNovelContext.setSelected(false); // Standardmäßig deaktiviert
        Button selectAll = new Button("Alle");
        Button selectNone = new Button("Keine");
        selectAll.setOnAction(e -> {
            cbUserContext.setSelected(true);
            cbEditorSnippet.setSelected(true);
            cbSynopsis.setSelected(true);
            cbCharacters.setSelected(true);
            cbWorldbuilding.setSelected(true);
            cbOutline.setSelected(true);
            cbChapterNotes.setSelected(true);
            cbStyle.setSelected(true);
            cbNovelContext.setSelected(true);
        });
        selectNone.setOnAction(e -> {
            cbUserContext.setSelected(false);
            cbEditorSnippet.setSelected(false);
            cbSynopsis.setSelected(false);
            cbCharacters.setSelected(false);
            cbWorldbuilding.setSelected(false);
            cbOutline.setSelected(false);
            cbChapterNotes.setSelected(false);
            cbStyle.setSelected(false);
            cbNovelContext.setSelected(false);
        });
        row1.getChildren().addAll(new Label("Kontextquellen:"), selectAll, selectNone);
        row2.getChildren().addAll(cbUserContext, cbEditorSnippet, cbSynopsis, cbStyle);
        row3.getChildren().addAll(cbCharacters, cbWorldbuilding, cbOutline, cbChapterNotes, cbNovelContext);
        contextCheckboxBar.getChildren().addAll(row1, row2, row3);

        synopsisArea = new TextArea(); synopsisArea.setWrapText(true);
        synopsisArea.getStyleClass().add("ollama-text-area");
        charactersArea = new TextArea(); charactersArea.setWrapText(true);
        charactersArea.getStyleClass().add("ollama-text-area");
        worldbuildingArea = new TextArea(); worldbuildingArea.setWrapText(true);
        worldbuildingArea.getStyleClass().add("ollama-text-area");
        outlineArea = new TextArea(); outlineArea.setWrapText(true);
        outlineArea.getStyleClass().add("ollama-text-area");
        chapterNotesArea = new TextArea(); chapterNotesArea.setWrapText(true);
        chapterNotesArea.getStyleClass().add("ollama-text-area");

        tpSynopsis = new TitledPane("Synopsis", synopsisArea);
        tpSynopsis.getStyleClass().add("ollama-titled-pane");
        tpCharacters = new TitledPane("Charaktere", charactersArea);
        tpCharacters.getStyleClass().add("ollama-titled-pane");
        tpWorldbuilding = new TitledPane("Worldbuilding", worldbuildingArea);
        tpWorldbuilding.getStyleClass().add("ollama-titled-pane");
        tpOutline = new TitledPane("Outline", outlineArea);
        tpOutline.getStyleClass().add("ollama-titled-pane");
        tpChapter = new TitledPane("Kapitelnotizen", chapterNotesArea);
        tpChapter.getStyleClass().add("ollama-titled-pane");

        // Neues Klappfeld: Stil
        this.styleNotesArea = new TextArea();
        styleNotesArea.setWrapText(true);
        styleNotesArea.getStyleClass().add("ollama-text-area");
        this.tpStyle = new TitledPane("Stil", styleNotesArea);
        tpStyle.getStyleClass().add("ollama-titled-pane");
        contextAccordion.getPanes().addAll(tpSynopsis, tpCharacters, tpWorldbuilding, tpOutline, tpChapter, tpStyle);

        // Auto-Save der Projekt-Kontexte
        setupAutoSave(synopsisArea, NovelManager.SYNOPSIS_FILE);
        setupAutoSave(charactersArea, NovelManager.CHARACTERS_FILE);
        setupAutoSave(worldbuildingArea, NovelManager.WORLDBUILDING_FILE);
        setupAutoSave(outlineArea, NovelManager.OUTLINE_FILE);
        // Stil-Notizen auto-speichern
        styleNotesArea.textProperty().addListener((obs,o,n)->{
            java.util.Timer t = resetTimer("style");
            t.schedule(new java.util.TimerTask(){
                @Override public void run(){
                    Platform.runLater(() -> {
                        String dir = getNovelDirectory();
                        if (dir != null && !dir.isEmpty()) {
                            try {
                                writeText(new File(dir, "style.txt"), n);
                                updateStatus("Gespeichert: style.txt");
                            } catch(Exception e){ logger.warning("Fehler beim Speichern von style.txt: "+e.getMessage()); }
                        }
                    });
                }
            }, 600);
        });
        chapterNotesArea.textProperty().addListener((obs,o,n)->debouncedSaveChapter(n));

        // Beim Start: Projekt-Kontexte laden
        loadProjectContexts();

        // Aufteilung: Links Hauptinhalt, Rechts Kontextbereich (besser sichtbar, frei skalierbar)
        VBox leftContent = new VBox(6);
        leftContent.getChildren().addAll(
            specialFieldsBox,
            inputLabel,
            inputArea,
            buttonArea,
            chatLabel,
            chatHistoryArea,
            // Button direkt unter der Chat-Ausgabe platzieren
            resultButtonRow,
            // Nicht-Chat-Ergebnis weiter unten
            resultLabel,
            resultArea
        );
        // Kleiner Abstand nur direkt über dem Button
        VBox.setMargin(resultButtonRow, new Insets(2, 0, 0, 0));
        VBox.setVgrow(chatHistoryArea, Priority.ALWAYS);
        VBox.setVgrow(resultArea, Priority.ALWAYS);

        VBox rightContext = new VBox(8);
        rightContext.setPadding(new Insets(0, 0, 0, 8));
        rightContext.getChildren().addAll(
            contextLabel,
            contextArea,
            contextCheckboxBar,
            contextAccordion
        );
        rightContext.setMinWidth(260);
        VBox.setVgrow(contextAccordion, Priority.ALWAYS);

        SplitPane splitPane = new SplitPane();
        splitPane.setDividerPositions(0.68);
        splitPane.getItems().addAll(leftContent, rightContext);

        lowerPanel.getChildren().add(splitPane);
        // Wichtiger Schritt: SplitPane soll im unteren Panel vertikal mitwachsen (am Boden "kleben")
        VBox.setVgrow(splitPane, Priority.ALWAYS);
        
        // Chat-Session-Controls in den oberen Bereich (einklappbar)
        upperPanel.getChildren().add(chatSessionBox);
        
        // Alles zusammen
        mainLayout.getChildren().addAll(
            toggleUpperPanelButton,
            upperPanel,
            lowerPanel
        );
        
        // VBox.setVgrow für besseres Resizing
        VBox.setVgrow(inputArea, Priority.SOMETIMES);
        VBox.setVgrow(chatHistoryArea, Priority.ALWAYS); // Chat-Verlauf ist das Hauptfeld
        // Kontextbereiche wachsen rechts im SplitPane
        VBox.setVgrow(lowerPanel, Priority.ALWAYS);
        VBox.setVgrow(upperPanel, Priority.NEVER); // Oberer Bereich ist fest (nur Einstellungen)
        
        // Alle TextAreas verschiebbar machen
        makeTextAreasResizable();
        
        // Speichere Referenz für Zugriff
        this.specialFieldsBox = specialFieldsBox;
        
        Scene scene = new Scene(mainLayout);
        
        // CSS-Dateien laden
        // CSS mit ResourceManager laden
        String cssPath = ResourceManager.getCssResource("css/manuskript.css");
        
        if (cssPath != null) {
            scene.getStylesheets().add(cssPath);
        }
        
        stage.setSceneWithTitleBar(scene);

        // Gespeicherte Position/Größe laden (falls vorhanden)
        try {
            String sx = ResourceManager.getParameter("ui.ollama_window_x", "");
            String sy = ResourceManager.getParameter("ui.ollama_window_y", "");
            String sw = ResourceManager.getParameter("ui.ollama_window_w", "");
            String sh = ResourceManager.getParameter("ui.ollama_window_h", "");
            if (!sx.isEmpty() && !sy.isEmpty()) {
                stage.setX(Double.parseDouble(sx));
                stage.setY(Double.parseDouble(sy));
            }
            if (!sw.isEmpty() && !sh.isEmpty()) {
                double dw = Double.parseDouble(sw);
                double dh = Double.parseDouble(sh);
                if (dw >= stage.getMinWidth()) stage.setWidth(dw);
                if (dh >= stage.getMinHeight()) stage.setHeight(dh);
            }
        } catch (Exception ignored) {}

        // Beim Schließen: Debounce-Timer stoppen und alle Kontexte hart speichern + Geometrie sichern
        stage.setOnCloseRequest(ev -> {
            cancelAllDebounceTimers();
            saveAllContextsNow();
            saveAllSessions();
            try {
                ResourceManager.saveParameter("ui.ollama_window_x", String.valueOf(stage.getX()));
                ResourceManager.saveParameter("ui.ollama_window_y", String.valueOf(stage.getY()));
                ResourceManager.saveParameter("ui.ollama_window_w", String.valueOf(stage.getWidth()));
                ResourceManager.saveParameter("ui.ollama_window_h", String.valueOf(stage.getHeight()));
            } catch (Exception ignored) {}
        });
        
        // Überprüfe Ollama-Status beim Start
        checkOllamaStatus();
        
        // Initialisiere UI-Felder basierend auf Standard-Funktion
        updateInputFields();
        
                            // Click-Event für Frage-Bereich: Frage-Text in Eingabebox kopieren
                    chatHistoryArea.getQuestionArea().setOnMouseClicked(e -> {
                        // NUR den Frage-Text aus CustomChatArea auslesen
                        String questionText = chatHistoryArea.getCurrentQuestion();
                        
                        // Frage-Text in Eingabebox kopieren und Eingabebox leeren
                        if (questionText != null && !questionText.trim().isEmpty()) {
                            // Entferne alle Nummerierungen wie "(14)", "(15)" etc. am Ende
                            String cleanQuestionText = questionText.replaceAll("\\s*\\(\\d+\\)\\s*$", "").trim();
                            inputArea.setText(cleanQuestionText);
                        }
                    });
    }
    
    /**
     * Macht alle TextAreas verschiebbar
     */
    private void makeTextAreasResizable() {
        // Eingabe-Bereich
        inputArea.setPrefRowCount(4);
        inputArea.setMinHeight(80);
        inputArea.setMaxHeight(Double.MAX_VALUE);
        
        // Chat-Historie (Hauptfeld - größer)
        chatHistoryArea.setMinHeight(400); // Noch größer gemacht
        chatHistoryArea.setMaxHeight(Double.MAX_VALUE);
        
        // Kontext-Bereich (höher, besser lesbar)
        contextArea.setPrefRowCount(6);
        contextArea.setMinHeight(120);
        contextArea.setMaxHeight(Double.MAX_VALUE);
        
        // Ergebnis-Bereich (für "Text umschreiben" und andere Funktionen)
        resultArea.setMinHeight(300);
        resultArea.setMaxHeight(Double.MAX_VALUE);
        
        // Training-Daten
        trainingDataArea.setPrefRowCount(8);
        trainingDataArea.setMinHeight(150);
        trainingDataArea.setMaxHeight(Double.MAX_VALUE);
    }
    
    /**
     * Wechselt die Sichtbarkeit des oberen Panels
     */
    private void toggleUpperPanel() {
        boolean visible = toggleUpperPanelButton.isSelected();
        upperPanel.setVisible(visible);
        upperPanel.setManaged(visible);
        
        if (visible) {
            toggleUpperPanelButton.setText("⚙️ Einstellungen ausblenden");
        } else {
            toggleUpperPanelButton.setText("⚙️ Einstellungen anzeigen");
        }
    }
    
    /**
     * Wechselt die Sichtbarkeit des Kontext-Panels
     */

    /**
     * Passt das Token-Limit basierend auf der ausgewählten Funktion an
     */
    private void adjustTokenLimitForFunction(String selectedFunction, String input) {
        int inputTokens = input.split("\\s+").length; // Grobe Schätzung: 1 Token ≈ 1 Wort
        int recommendedTokens;
        
        switch (selectedFunction) {
                
            case "Dialog generieren":
                // Dialoge können variabel lang sein
                recommendedTokens = Math.max(4096, inputTokens * 2); // Mindestens 4096, sonst 2x Input
                break;
                
                
            case "Charakter entwickeln":
                // Charakterentwicklung braucht viel Platz
                recommendedTokens = Math.max(8192, inputTokens * 4); // Mindestens 8192, sonst 4x Input
                break;
                
            case "Schreibstil analysieren":
                // Analysen können detailliert sein
                recommendedTokens = Math.max(4096, inputTokens * 2); // Mindestens 4096, sonst 2x Input
                break;
                
                
            case "Chat-Assistent":
                // Chat braucht moderaten Platz
                recommendedTokens = Math.max(4096, inputTokens * 2); // Mindestens 4096, sonst 2x Input
                break;
                
            default: // Freier Text
                recommendedTokens = Math.max(4096, inputTokens * 2); // Standard
                break;
        }
        
        // Begrenzen auf maximal 32768 Tokens (sicherer Wert für die meisten Modelle)
        recommendedTokens = Math.min(recommendedTokens, 32768);
        
        // Token-Limit setzen (immer, unabhängig vom Slider-Bereich)
        ollamaService.setMaxTokens(recommendedTokens);
        
        // Slider-Wert aktualisieren (nur wenn er im sichtbaren Bereich ist)
        if (recommendedTokens <= maxTokensSlider.getMax()) {
            maxTokensSlider.setValue(recommendedTokens);
        } else {
            // Wenn der Wert außerhalb des Slider-Bereichs liegt, Slider auf Maximum setzen
            maxTokensSlider.setValue(maxTokensSlider.getMax());
        }
        
        // Label explizit aktualisieren (da der Slider-Listener nicht ausgelöst wird)
        maxTokensLabel.setText(String.format("Max Tokens: %d", recommendedTokens));
        
        updateStatus("Token-Limit angepasst: " + recommendedTokens + " für " + selectedFunction);
    }
    
    /**
     * Aktualisiert das Layout des unteren Panels basierend auf der ausgewählten Funktion
     */
    private void updateLowerPanelLayout(String selectedFunction, Label inputLabel, Label chatLabel, 
                                       Label contextLabel, Label resultLabel, TextArea resultArea) {
        boolean isRewriteMode = "Text umschreiben".equals(selectedFunction);
        boolean isChatMode = "Chat-Assistent".equals(selectedFunction);
        
        if (isRewriteMode) {
            // Text umschreiben: Eingabe und Kontext anzeigen, Chat ausblenden, Ergebnis anzeigen
            inputLabel.setText("Original-Text:");
            inputLabel.setVisible(true);
            inputArea.setVisible(true);
            inputArea.setManaged(true);
            
            chatLabel.setVisible(false);
            chatHistoryArea.setVisible(false);
            chatHistoryArea.setManaged(false);
            
            contextLabel.setVisible(true);
            contextArea.setVisible(true);
            contextArea.setManaged(true);
            
            resultLabel.setVisible(true);
            resultArea.setVisible(true);
            resultArea.setManaged(true);
            
        } else if (isChatMode) {
            // Chat-Assistent: Alle Elemente anzeigen
            inputLabel.setText("Eingabe:");
            inputLabel.setVisible(true);
            inputArea.setVisible(true);
            inputArea.setManaged(true);
            
            chatLabel.setVisible(true);
            chatHistoryArea.setVisible(true);
            chatHistoryArea.setManaged(true);
            
            contextLabel.setVisible(true);
            contextArea.setVisible(true);
            contextArea.setManaged(true);
            
            resultLabel.setVisible(false);
            resultArea.setVisible(false);
            resultArea.setManaged(false);
            
        } else {
            // Andere Funktionen: Eingabe und Ergebnis anzeigen, Chat ausblenden
            inputLabel.setText("Eingabe:");
            inputLabel.setVisible(true);
            inputArea.setVisible(true);
            inputArea.setManaged(true);
            
            chatLabel.setVisible(false);
            chatHistoryArea.setVisible(false);
            chatHistoryArea.setManaged(false);
            
            // Kontext-Textbox auch hier anzeigen
            contextLabel.setVisible(true);
            contextArea.setVisible(true);
            contextArea.setManaged(true);
            
            resultLabel.setVisible(true);
            resultArea.setVisible(true);
            resultArea.setManaged(true);
        }
    }
    
    private void updateInputFields() {
        String selectedFunction = functionComboBox.getValue();
        
        // Alle speziellen Felder zunächst ausblenden
        dialogBox.setVisible(false);
        dialogBox.setManaged(false);
        descBox.setVisible(false);
        descBox.setManaged(false);
        plotBox.setVisible(false);
        plotBox.setManaged(false);
        rewriteBox.setVisible(false);
        rewriteBox.setManaged(false);
        trainingBox.setVisible(false);
        trainingBox.setManaged(false);
        
        // Chat-Elemente und Eingabe-Elemente für "Text umschreiben" ausblenden
        boolean isRewriteMode = "Text umschreiben".equals(selectedFunction);
        boolean isChatMode = "Chat-Assistent".equals(selectedFunction);
        boolean isPlotHolesMode = "Plot-Holes Detection".equals(selectedFunction);
        
        // Chat-Session-Box nur für Chat-Assistent anzeigen
        chatSessionBox.setVisible(isChatMode);
        chatSessionBox.setManaged(isChatMode);
        
        // Layout des unteren Panels aktualisieren
        updateLowerPanelLayout(selectedFunction, inputLabel, chatLabel, contextLabel, resultLabel, resultArea);
        
        // Token-Limit basierend auf der ausgewählten Funktion anpassen
        String currentInput = inputArea.getText();
        if (currentInput == null) {
            currentInput = "";
        }
        adjustTokenLimitForFunction(selectedFunction, currentInput);
        

        
        // Spezielle Felder nur für bestimmte Funktionen anzeigen
        boolean showSpecialFields = false;
        boolean isTrainingMode = false;
        
        // Eingabe grundsätzlich editierbar machen (wird bei "Text umschreiben" gezielt deaktiviert)
        inputArea.setEditable(true);
        
        // Aktualisiere Prompt-Text basierend auf Funktion
        switch (selectedFunction) {
            case "Dialog generieren":
                inputArea.setPromptText("Geben Sie hier zusätzliche Details für den Dialog ein...");
                dialogBox.setVisible(true);
                dialogBox.setManaged(true);
                showSpecialFields = true;
                break;
            case "Charakter entwickeln":
                inputArea.setPromptText("Geben Sie hier die Grundmerkmale des Charakters ein...");
                break;
            case "Schreibstil analysieren":
                inputArea.setPromptText("Fügen Sie hier den Text ein, dessen Schreibstil analysiert werden soll...");
                break;
            case "Chat-Assistent":
                inputArea.setPromptText("Stellen Sie hier Ihre Frage an den KI-Assistenten...");
                inputArea.setEditable(true);
                chatSessionBox.setVisible(true);
                chatSessionBox.setManaged(true);
                // Keine Session-Verwaltung mehr
                break;
            case "Modell-Training":
                inputArea.setPromptText("Zusätzliche Anweisungen für das Training (optional)...");
                inputArea.setEditable(true);
                trainingBox.setVisible(true);
                trainingBox.setManaged(true);
                showSpecialFields = true;
                isTrainingMode = true;
                // Automatisch Editor-Text als Training-Daten laden
                if (editorWindow != null) {
                    String editorText = editorWindow.getCodeAreaText();
                    if (editorText != null && !editorText.trim().isEmpty()) {
                        trainingDataArea.setText(editorText);
                        updateStatus("Editor-Text als Training-Daten geladen: " + editorText.length() + " Zeichen");
                    }
                }
                break;
            default:
                inputArea.setPromptText("Geben Sie hier Ihren Text oder Prompt ein...");
                inputArea.setEditable(true);
                break;
        }

        // Wenn verfügbar: selektierten Editor-Text für andere Funktionen vorbefüllen (ohne Chat/Training)
        if (!"Chat-Assistent".equals(selectedFunction) && !"Modell-Training".equals(selectedFunction)) {
            if ((inputArea.getText() == null || inputArea.getText().isEmpty()) && editorWindow != null) {
                String sel = editorWindow.getSelectedText();
                if (sel != null && !sel.trim().isEmpty()) {
                    inputArea.setText(sel);
                }
            }
        }
        
        // Spezielle Felder Container nur anzeigen, wenn spezielle Felder sichtbar sind
        specialFieldsBox.setVisible(showSpecialFields);
        specialFieldsBox.setManaged(showSpecialFields);
        
        // Bei Modell-Training die normalen Buttons ausblenden
        if (isTrainingMode) {
            generateButton.setVisible(false);
            generateButton.setManaged(false);
            insertButton.setVisible(false);
            insertButton.setManaged(false);
            progressIndicator.setVisible(false);
            progressIndicator.setManaged(false);
            // Training-Status zurücksetzen wenn Training-Modus aktiviert wird
            resetTrainingStatus();
        } else {
            generateButton.setVisible(true);
            generateButton.setManaged(true);
            insertButton.setVisible(true);
            insertButton.setManaged(true);
            progressIndicator.setVisible(false);
            progressIndicator.setManaged(false);
        }
    }
    
    private void generateContent() {
        String selectedFunction = functionComboBox.getValue();
        String input = inputArea.getText().trim();
        
        // Bei Plugins und Plot-Holes Detection ist keine Eingabe erforderlich
        if (input.isEmpty() && (selectedFunction == null || (!selectedFunction.startsWith("📦 ") && !"Plot-Holes Detection".equals(selectedFunction)))) {
            showAlert("Eingabe erforderlich", "Bitte geben Sie einen Text ein.");
            return;
        }
        
        // Token-Limit basierend auf Funktion anpassen (nur beim Funktionswechsel, nicht beim Generieren)
        // adjustTokenLimitForFunction(selectedFunction, input);
        
        // Für Plot-Holes Detection: Token-Limit explizit anpassen
        if ("Plot-Holes Detection".equals(selectedFunction)) {
            adjustTokenLimitForFunction(selectedFunction, input);
        }
        
        // DEBUG entfernt
        
        setGenerating(true);
        insertButton.setDisable(true);
        updateStatus("⏳ Anfrage läuft...");

        // DEBUG: Plugin-Erkennung
        if (selectedFunction != null && (selectedFunction.equals("Chat-Assistent") || selectedFunction.startsWith("📦 ") || selectedFunction.startsWith("? "))) {
            // Plugin-Namen normalisieren (entferne "? " und füge "📦 " hinzu)
            if (selectedFunction.startsWith("? ")) {
                selectedFunction = "📦 " + selectedFunction.substring(2);
            }
            // DEBUG entfernt
            // Chat-Modus mit manueller Verlaufsverwaltung
            String userMessage = inputArea.getText();
            
            // Plugin-Logik: Wenn ein Plugin ausgewählt ist, erlaube leeren Input
            if (selectedFunction.startsWith("📦 ") || selectedFunction.startsWith("? ")) {
                // Plugin-Modus: Erlaube leeren Input
                if (userMessage == null) {
                    userMessage = "";
                }
            } else {
                // Normaler Chat-Modus: Input erforderlich
                if (userMessage == null || userMessage.trim().isEmpty()) {
                    setGenerating(false);
                    updateStatus("Bitte gib eine Nachricht ein.");
                    return;
                }
            }
            
            // Plugin-Logik wird nach der chatMessages Definition verschoben
            
            // UI-Verwaltung mit CustomChatArea - Frage hinzufügen (ohne Session zu speichern)
            chatHistoryArea.clearAndShowNewQuestion(userMessage);
            
            // Zusätzlichen Kontext aus dem Context-Bereich holen
            String additionalContext = contextArea.getText() != null ? contextArea.getText().trim() : "";
            // DEBUG entfernt
            
            // Chat-Historie als Kontext hinzufügen (nur vollständige QAPairs)
            List<CustomChatArea.QAPair> sessionHistory = chatHistoryArea.getSessionHistory();
            StringBuilder contextBuilder = new StringBuilder();
            
            // DEBUG entfernt
            
            if (!sessionHistory.isEmpty()) {
                int completePairs = 0;
                for (CustomChatArea.QAPair qaPair : sessionHistory) {
                    // Nur vollständige QAPairs (mit Antworten) als Kontext verwenden
                    if (qaPair.getAnswer() != null && !qaPair.getAnswer().trim().isEmpty()) {
                        contextBuilder.append("Du: ").append(qaPair.getQuestion()).append("\n");
                        contextBuilder.append("Assistent: ").append(qaPair.getAnswer()).append("\n");
                        completePairs++;
                    }
                }
                // DEBUG entfernt
            }
            
            // Checkbox-gesteuerte Kontexte hinzufügen
            StringBuilder selectedContexts = new StringBuilder();
            if (cbUserContext != null && cbUserContext.isSelected() && !additionalContext.isEmpty()) {
                selectedContexts.append("\n=== BENUTZER-KONTEXT ===\n").append(limitText(additionalContext)).append("\n");
            }

            if (cbEditorSnippet != null && cbEditorSnippet.isSelected() && editorWindow != null) {
                try {
                    String fullText = editorWindow.getText();
                    String selected = editorWindow.getSelectedText();
                    int caret = fullText != null ? Math.min(editorWindow.getCaretPosition(), Math.max(0, fullText.length())) : 0;
                    String contextSnippet = null;
                    if (fullText != null && !fullText.isEmpty()) {
                        int window = 12000;
                        int half = window / 2;
                        int foundIndex = (selected != null && !selected.isEmpty()) ? fullText.indexOf(selected) : -1;
                        int anchor = foundIndex >= 0 ? foundIndex : caret;
                        int start = Math.max(0, anchor - half);
                        int end = Math.min(fullText.length(), (foundIndex >= 0 ? (foundIndex + selected.length()) : caret) + half);
                        if (end > start) {
                            contextSnippet = fullText.substring(start, end);
                        }
                    }
                    if (contextSnippet != null && !contextSnippet.isEmpty()) {
                        selectedContexts.append("\n=== EDITOR-KONTEXT ===\n").append(limitText(contextSnippet)).append("\n");
                    }
                } catch (Exception ignored) {}
            }

            // Projekt-Kontexte aus den TextAreas je nach Auswahl
            if (cbSynopsis != null && cbSynopsis.isSelected() && synopsisArea != null && !synopsisArea.getText().trim().isEmpty()) {
                selectedContexts.append("\n=== SYNOPSIS ===\n").append(limitText(synopsisArea.getText().trim())).append("\n");
            }
            if (cbCharacters != null && cbCharacters.isSelected() && charactersArea != null && !charactersArea.getText().trim().isEmpty()) {
                selectedContexts.append("\n=== CHARAKTERE ===\n").append(limitText(charactersArea.getText().trim())).append("\n");
            }
            if (cbWorldbuilding != null && cbWorldbuilding.isSelected() && worldbuildingArea != null && !worldbuildingArea.getText().trim().isEmpty()) {
                selectedContexts.append("\n=== WORLDBUILDING ===\n").append(limitText(worldbuildingArea.getText().trim())).append("\n");
            }
            if (cbOutline != null && cbOutline.isSelected() && outlineArea != null && !outlineArea.getText().trim().isEmpty()) {
                selectedContexts.append("\n=== OUTLINE ===\n").append(limitText(outlineArea.getText().trim())).append("\n");
            }
            if (cbChapterNotes != null && cbChapterNotes.isSelected() && chapterNotesArea != null && !chapterNotesArea.getText().trim().isEmpty()) {
                selectedContexts.append("\n=== KAPITELNOTIZEN ===\n").append(limitText(chapterNotesArea.getText().trim())).append("\n");
            }
            if (cbStyle != null && cbStyle.isSelected() && styleNotesArea != null && !styleNotesArea.getText().trim().isEmpty()) {
                selectedContexts.append("\n=== STIL ===\n").append(limitText(styleNotesArea.getText().trim())).append("\n");
            }

            // Kontext aus der context.txt des aktuellen Romans (nur wenn Checkbox aktiviert)
            if (cbNovelContext != null && cbNovelContext.isSelected()) {
                String currentDocxFile = getCurrentDocxFileName();
                if (currentDocxFile != null) {
                    String novelContext = NovelManager.loadContext(currentDocxFile);
                    if (!novelContext.trim().isEmpty()) {
                        selectedContexts.append("\n=== ROMAN-KONTEXT ===\n").append(limitText(novelContext)).append("\n");
                    }
                }
            }

            if (selectedContexts.length() > 0) {
                contextBuilder.append("\n").append(selectedContexts);
                logger.info("DEBUG: selectedContexts hinzugefügt: " + selectedContexts.toString().substring(0, Math.min(200, selectedContexts.length())));
            }
            
            String fullContext = contextBuilder.toString();
            logger.info("DEBUG: Finaler Kontext (erste 500 Zeichen): " + fullContext.substring(0, Math.min(500, fullContext.length())));
            // DEBUG entfernt
            
            // Anzahl der vollständigen QAPairs für Debug-Zwecke
            long completeQAPairs = sessionHistory.stream().filter(qa -> qa.getAnswer() != null && !qa.getAnswer().trim().isEmpty()).count();
            
            // Vollständigen Prompt mit Kontext erstellen
            StringBuilder fullPromptBuilder = new StringBuilder();
            fullPromptBuilder.append("Du bist ein hilfreicher deutscher Assistent.\nAntworte bitte auf Deutsch.\n\n");
            
            if (!fullContext.isEmpty()) {
                fullPromptBuilder.append(fullContext).append("\n");
            }
            
            fullPromptBuilder.append("Du: ").append(userMessage).append("\n");
            fullPromptBuilder.append("Assistent: ");
            
            String fullPrompt = fullPromptBuilder.toString();
            // DEBUG entfernt
            

            
            // Chat-API mit echter Konversationshistorie verwenden
            StringBuilder aggregated = new StringBuilder();
            final int qaIndex = chatHistoryArea.getLastIndex(); // Index des frisch hinzugefügten Q&A sichern
            
            // Chat-Historie in ChatMessage-Format konvertieren
            List<OllamaService.ChatMessage> chatMessages = new ArrayList<>();
            for (CustomChatArea.QAPair qaPair : sessionHistory) {
                if (qaPair.getAnswer() != null && !qaPair.getAnswer().trim().isEmpty()) {
                    chatMessages.add(new OllamaService.ChatMessage("user", qaPair.getQuestion()));
                    chatMessages.add(new OllamaService.ChatMessage("assistant", qaPair.getAnswer()));
                }
            }
            // Plugin-Logik für Variablen-Ersetzung
            String processedUserMessage = userMessage;
            
            // Normalisierung: ? zu 📦 konvertieren
            if (selectedFunction.startsWith("? ")) {
                selectedFunction = "📦 " + selectedFunction.substring(2);
            }
            
            if (selectedFunction.startsWith("📦 ")) {
                String pluginName = selectedFunction.substring(2).trim(); // "📦 " entfernen und trimmen
                logger.info("DEBUG: Plugin erkannt: " + pluginName);
    
                Plugin plugin = pluginManager.getPlugin(pluginName);
                if (plugin != null) {
                    logger.info("DEBUG: Plugin gefunden: " + pluginName);

                    // Selektierten Text aus Editor oder Chat-Input holen
                    String selectedText = getSelectedTextFromEditor();
                    if (selectedText == null || selectedText.trim().isEmpty()) {
                        selectedText = userMessage; // Fallback auf Chat-Input
                    }
                    

                    // Prüfe ob Plugin Variablen hat
                    List<PluginVariable> variableDefinitions = plugin.getVariableDefinitions();
                    boolean hasUserVariables = false;
                    
                    // Prüfe ob es User-Variablen gibt (nicht nur "selektierter Text")
                    for (PluginVariable varDef : variableDefinitions) {
                        if (!isSelectedTextVariable(varDef.getName())) {
                            hasUserVariables = true;
                            break;
                        }
                    }
                    
                    Map<String, String> variables;
                    if (hasUserVariables) {
                        // Dialog für Plugin-Variablen anzeigen
                        variables = PluginVariableDialog.showDialog(plugin, selectedText, currentThemeIndex);
                    } else {
                        // Plugin ohne User-Variablen: automatisch ausführen
                        variables = new HashMap<>();
                        // "selektierter Text" automatisch setzen falls vorhanden
                        for (PluginVariable varDef : variableDefinitions) {
                            if (isSelectedTextVariable(varDef.getName())) {
                                variables.put(varDef.getName(), selectedText != null ? selectedText : "");
                            }
                        }
                    }
                    
                    if (variables != null) {
                        // Debug: Ausgabe der Variablen
                        
                        // Plugin-Prompt mit Variablen verarbeiten
                        String pluginPrompt = plugin.getProcessedPrompt(variables);

                        
                        // Plugin-Prompt als Benutzer-Nachricht hinzufügen (damit er in der Chat-Historie sichtbar ist)
                        chatMessages.add(new OllamaService.ChatMessage("user", "Plugin: " + pluginPrompt));
                        processedUserMessage = "Führe das Plugin aus.";
                        
                        // Plugin-Prompt in die Chat-Frage-Box einfügen
                        chatHistoryArea.setQuestionAt(qaIndex, "Plugin: " + pluginPrompt);
                        
                        // Plugin-Prompt erfolgreich verarbeitet

                    } else {

                        setGenerating(false);
                        updateStatus("Plugin-Ausführung abgebrochen");
                        return; // Dialog abgebrochen
                    }
                } else {

                }
            }
            
            // Neue Nachricht hinzufügen
            chatMessages.add(new OllamaService.ChatMessage("user", processedUserMessage));
            
            // Chat-Nachrichten bereit für Streaming
            
            // Finale Variablen für Lambda-Ausdrücke
            final String finalSelectedFunction = selectedFunction;
            final TextArea finalResultArea = resultArea;
            
            currentStreamHandle = ollamaService.chatStreaming(
                chatMessages,
                fullContext,
                chunk -> Platform.runLater(() -> {
                    aggregated.append(chunk);
                    statusLabel.setText("⏳ Läuft… " + aggregated.length() + " Zeichen");
                    
                    // Plugin-Ergebnis auch in resultArea schreiben für Ergebnis-Fenster
                    if (finalSelectedFunction != null && finalSelectedFunction.startsWith("📦 ")) {
                        finalResultArea.setText(aggregated.toString());
                    }
                    
                    // Live: Antwort fortlaufend exakt in dem Q&A sichern, zu dem diese Antwort gehört
                    try { 
                        chatHistoryArea.setAnswerAt(qaIndex, aggregated.toString()); 
                        // UI sofort aktualisieren - stärkere Aktualisierung für Plugins
                        chatHistoryArea.getChatHistoryArea().requestLayout();
                        chatHistoryArea.getChatHistoryArea().requestFocus();
                        // Zusätzliche UI-Aktualisierung
                        Platform.runLater(() -> {
                            chatHistoryArea.getChatHistoryArea().requestLayout();
                            chatHistoryArea.getChatHistoryArea().setScrollTop(Double.MAX_VALUE);
                        });
                    } catch (Exception e) {
    
                    }
                    // Live-Update im externen Fenster (immer ans Ende scrollen) - OHNE Header
                    if (resultStage != null && resultStage.isShowing() && resultWebView != null) {
                        // Nur den reinen Text ohne Header für das Fenster verwenden
                        updateResultWebView(buildHtmlForAnswer(aggregated.toString()), true);
                    }
                    // Auch im Standard-Output-Feld (Chat-Historie) immer ans Ende springen
                    try {
                        String txt = chatHistoryArea.getCurrentAnswer();
                        int l = txt != null ? txt.length() : 0;
                        chatHistoryArea.getChatHistoryArea().positionCaret(l);
                        chatHistoryArea.getChatHistoryArea().setScrollTop(Double.MAX_VALUE);
                    } catch (Exception ignored) {}
                }),
                () -> Platform.runLater(() -> {
                        insertButton.setDisable(false);
                        setGenerating(false);
                    updateStatus("✅ Antwort erhalten");
                        inputArea.clear();
                    String header = String.format(java.util.Locale.US, "[%s | temp=%.2f, top_p=%.2f, repeat_penalty=%.2f]",
                            ollamaService != null ? ollamaService.getCurrentParameters().split(",")[0].replace("Modell: ", "").trim() : modelComboBox.getValue(),
                            ResourceManager.getDoubleParameter("ollama.temperature", 0.3),
                            ResourceManager.getDoubleParameter("ollama.top_p", 0.7),
                            ResourceManager.getDoubleParameter("ollama.repeat_penalty", 1.3)
                    );
                    // Finale Antwort-String
                    String finalAnswer = header + "\n" + aggregated.toString();
                    // Reinen Chat-Text für "Ergebnis in Fenster" speichern
                    lastPureChatAnswer = aggregated.toString();
                    
                    // Plugin-Ergebnis auch in resultArea schreiben für Ergebnis-Fenster
                    if (finalSelectedFunction != null && finalSelectedFunction.startsWith("📦 ")) {
                        finalResultArea.setText(aggregated.toString());
                        // Ergebnis-Fenster sichtbar machen
                        finalResultArea.setVisible(true);
                        finalResultArea.setManaged(true);
                    }
                    
                    // UI aktualisieren (asynchron ok)
                    chatHistoryArea.setAnswerAt(qaIndex, finalAnswer);
                    
                    // "Ergebnis in Fenster" mit reinem Text (ohne Header) aktualisieren
                    if (resultStage != null && resultStage.isShowing() && resultWebView != null) {
                        updateResultWebView(buildHtmlForAnswer(lastPureChatAnswer), false);
                    }
                    
                    // Persistenz: Snapshot holen und SICHER den betreffenden Index mit finaler Antwort überschreiben
                    List<CustomChatArea.QAPair> snapshot = chatHistoryArea.getSessionHistory();
                    if (qaIndex >= 0 && qaIndex < snapshot.size()) {
                        CustomChatArea.QAPair original = snapshot.get(qaIndex);
                        snapshot.set(qaIndex, new CustomChatArea.QAPair(original.getQuestion(), finalAnswer));
                    }
                    sessionHistories.put(currentSessionName, snapshot);
                    ResourceManager.saveSession(currentSessionName, snapshot);
                            checkAndSplitSession(currentSessionName);
                }),
                (Throwable err) -> Platform.runLater(() -> {
                        setGenerating(false);
                    updateStatus("Fehler: " + err.getMessage());
                })
            );
            return;
        }
        // DEBUG entfernt
        
        CompletableFuture<String> future = null;

        // Kontext sammeln (außer bei Chat):
        // - Benutzer-Kontext aus der kleinen Kontext-Textbox unten
        // - Editor-Kontext (großzügiges Fenster um Selektion/Caret)
        String combinedContext = null;
        String userContext = null;
        if (!"Chat-Assistent".equals(selectedFunction)) {
            // Benutzer-Kontext holen
            try {
                if (contextArea != null) {
                    String ctx = contextArea.getText();
                    if (ctx != null && !ctx.trim().isEmpty()) {
                        userContext = ctx.trim();
                    }
                }
            } catch (Exception ignored) {}
        }

        String contextSnippet = null;
        if (!"Chat-Assistent".equals(selectedFunction) && editorWindow != null) {
            try {
                String fullText = editorWindow.getText();
                String selected = editorWindow.getSelectedText();
                int caret = fullText != null ? Math.min(editorWindow.getCaretPosition(), Math.max(0, fullText.length())) : 0;

                if (fullText != null && !fullText.isEmpty()) {
                    int window = 12000; // großzügiger Kontext (~ein paar hundert Zeilen)
                    int half = window / 2;
                    int foundIndex = (selected != null && !selected.isEmpty()) ? fullText.indexOf(selected) : -1;
                    int anchor = foundIndex >= 0 ? foundIndex : caret;
                    int start = Math.max(0, anchor - half);
                    int end = Math.min(fullText.length(), (foundIndex >= 0 ? (foundIndex + selected.length()) : caret) + half);
                    if (end > start) {
                        contextSnippet = fullText.substring(start, end);
                    }
                }
            } catch (Exception ignored) {
            }
        }

        if (!"Chat-Assistent".equals(selectedFunction)) {
            StringBuilder sb = new StringBuilder();
            if (userContext != null && cbUserContext != null && cbUserContext.isSelected()) {
                sb.append("=== BENUTZER-KONTEXT ===\n");
                sb.append(userContext).append("\n\n");
            }
            if (contextSnippet != null && !contextSnippet.isEmpty() && cbEditorSnippet != null && cbEditorSnippet.isSelected()) {
                sb.append("=== EDITOR-KONTEXT ===\n");
                sb.append(contextSnippet);
            }
            // Projekt-Kontexte
            String currentDocx = getCurrentDocxFileName();
            if (currentDocx != null) {
                if (cbSynopsis != null && cbSynopsis.isSelected()) {
                    String syn = synopsisArea != null ? synopsisArea.getText() : NovelManager.loadSynopsis(currentDocx);
                    if (syn != null && !syn.trim().isEmpty()) sb.append("\n\n=== SYNOPSIS ===\n").append(limitText(syn));
                }
                if (cbCharacters != null && cbCharacters.isSelected()) {
                    String chars = charactersArea != null ? charactersArea.getText() : NovelManager.loadCharacters(currentDocx);
                    if (chars != null && !chars.trim().isEmpty()) sb.append("\n\n=== CHARAKTERE ===\n").append(limitText(chars));
                }
                if (cbWorldbuilding != null && cbWorldbuilding.isSelected()) {
                    String wb = worldbuildingArea != null ? worldbuildingArea.getText() : NovelManager.loadWorldbuilding(currentDocx);
                    if (wb != null && !wb.trim().isEmpty()) sb.append("\n\n=== WORLDBUILDING ===\n").append(limitText(wb));
                }
                if (cbOutline != null && cbOutline.isSelected()) {
                    String ol = outlineArea != null ? outlineArea.getText() : NovelManager.loadOutline(currentDocx);
                    if (ol != null && !ol.trim().isEmpty()) sb.append("\n\n=== OUTLINE ===\n").append(limitText(ol));
                }
                if (cbChapterNotes != null && cbChapterNotes.isSelected()) {
                    String ch = chapterNotesArea != null ? chapterNotesArea.getText() : NovelManager.loadChapter(currentDocx);
                    if (ch != null && !ch.trim().isEmpty()) sb.append("\n\n=== KAPITELNOTIZEN ===\n").append(limitText(ch));
                }
            }
            combinedContext = sb.length() > 0 ? sb.toString() : null;
        }
        
        // Streaming für alle Funktionen verwenden (außer Chat, der bereits Streaming hat)
        StringBuilder aggregated = new StringBuilder();
        
        switch (selectedFunction) {
            case "Dialog generieren":
                String character = characterField.getText().trim();
                String situation = situationField.getText().trim();
                String emotion = emotionField.getText().trim();
                
                if (character.isEmpty() || situation.isEmpty()) {
                    showAlert("Eingabe unvollständig", "Bitte füllen Sie alle Felder für den Dialog aus.");
                    setGenerating(false);
                    return;
                }
                
                // Streaming für Dialog generieren
                currentStreamHandle = ollamaService.generateDialogueStreaming(character, situation, emotion, combinedContext,
                    chunk -> Platform.runLater(() -> {
                        aggregated.append(chunk);
                        statusLabel.setText("⏳ Läuft… " + aggregated.length() + " Zeichen");
                        resultArea.setText(aggregated.toString());
                        // Live-Update im externen Fenster
                        if (resultStage != null && resultStage.isShowing() && resultWebView != null) {
                            updateResultWebView(buildHtmlForAnswer(aggregated.toString()), true);
                        }
                    }),
                    () -> Platform.runLater(() -> {
                        insertButton.setDisable(false);
                        setGenerating(false);
                        updateStatus("✅ Dialog generiert: " + aggregated.length() + " Zeichen");
                        resultArea.setText(aggregated.toString());
                    }),
                    (Throwable err) -> Platform.runLater(() -> {
                        setGenerating(false);
                        updateStatus("Fehler: " + err.getMessage());
                    })
                );
                return;
                
            case "Beschreibung erweitern":
                String style = styleField.getText().trim();
                if (style.isEmpty()) {
                    style = "detailliert";
                }
                
                // Streaming für Beschreibung erweitern
                currentStreamHandle = ollamaService.expandDescriptionStreaming(input, style, combinedContext,
                    chunk -> Platform.runLater(() -> {
                        aggregated.append(chunk);
                        statusLabel.setText("⏳ Läuft… " + aggregated.length() + " Zeichen");
                        resultArea.setText(aggregated.toString());
                        if (resultStage != null && resultStage.isShowing() && resultWebView != null) {
                            updateResultWebView(buildHtmlForAnswer(aggregated.toString()), true);
                        }
                    }),
                    () -> Platform.runLater(() -> {
                        insertButton.setDisable(false);
                        setGenerating(false);
                        updateStatus("✅ Beschreibung erweitert: " + aggregated.length() + " Zeichen");
                        resultArea.setText(aggregated.toString());
                    }),
                    (Throwable err) -> Platform.runLater(() -> {
                        setGenerating(false);
                        updateStatus("Fehler: " + err.getMessage());
                    })
                );
                return;
                
            case "Plot-Ideen entwickeln":
                String genre = genreField.getText().trim();
                if (genre.isEmpty()) {
                    genre = "Allgemein";
                }
                
                // Streaming für Plot-Ideen
                currentStreamHandle = ollamaService.developPlotIdeasStreaming(genre, input, combinedContext,
                    chunk -> Platform.runLater(() -> {
                        aggregated.append(chunk);
                        statusLabel.setText("⏳ Läuft… " + aggregated.length() + " Zeichen");
                        resultArea.setText(aggregated.toString());
                        if (resultStage != null && resultStage.isShowing() && resultWebView != null) {
                            updateResultWebView(buildHtmlForAnswer(aggregated.toString()), true);
                        }
                    }),
                    () -> Platform.runLater(() -> {
                        insertButton.setDisable(false);
                        setGenerating(false);
                        updateStatus("✅ Plot-Ideen entwickelt: " + aggregated.length() + " Zeichen");
                        resultArea.setText(aggregated.toString());
                    }),
                    (Throwable err) -> Platform.runLater(() -> {
                        setGenerating(false);
                        updateStatus("Fehler: " + err.getMessage());
                    })
                );
                return;
                
            case "Charakter entwickeln":
                String characterName = characterField.getText().trim();
                if (characterName.isEmpty()) {
                    characterName = "Charakter";
                }
                
                // Streaming für Charakter entwickeln
                currentStreamHandle = ollamaService.developCharacterStreaming(characterName, input, combinedContext,
                    chunk -> Platform.runLater(() -> {
                        aggregated.append(chunk);
                        statusLabel.setText("⏳ Läuft… " + aggregated.length() + " Zeichen");
                        resultArea.setText(aggregated.toString());
                        if (resultStage != null && resultStage.isShowing() && resultWebView != null) {
                            updateResultWebView(buildHtmlForAnswer(aggregated.toString()), true);
                        }
                    }),
                    () -> Platform.runLater(() -> {
                        insertButton.setDisable(false);
                        setGenerating(false);
                        updateStatus("✅ Charakter entwickelt: " + aggregated.length() + " Zeichen");
                        resultArea.setText(aggregated.toString());
                    }),
                    (Throwable err) -> Platform.runLater(() -> {
                        setGenerating(false);
                        updateStatus("Fehler: " + err.getMessage());
                    })
                );
                return;
                
            case "Schreibstil analysieren":
                // Streaming für Schreibstil analysieren
                currentStreamHandle = ollamaService.analyzeWritingStyleStreaming(input, combinedContext,
                    chunk -> Platform.runLater(() -> {
                        aggregated.append(chunk);
                        statusLabel.setText("⏳ Läuft… " + aggregated.length() + " Zeichen");
                        resultArea.setText(aggregated.toString());
                        if (resultStage != null && resultStage.isShowing() && resultWebView != null) {
                            updateResultWebView(buildHtmlForAnswer(aggregated.toString()), true);
                        }
                    }),
                    () -> Platform.runLater(() -> {
                        insertButton.setDisable(false);
                        setGenerating(false);
                        updateStatus("✅ Schreibstil analysiert: " + aggregated.length() + " Zeichen");
                        resultArea.setText(aggregated.toString());
                    }),
                    (Throwable err) -> Platform.runLater(() -> {
                        setGenerating(false);
                        updateStatus("Fehler: " + err.getMessage());
                    })
                );
                return;
                
            case "Text umschreiben":
                String rewriteType = rewriteTypeComboBox.getValue();
                String additionalInstructions = additionalInstructionsField.getText().trim();
                
                if (rewriteType == null || rewriteType.isEmpty()) {
                    showAlert("Umschreibungsart erforderlich", "Bitte wählen Sie eine Umschreibungsart aus.");
                    setGenerating(false);
                    return;
                }
                
                // Streaming für Text umschreiben
                currentStreamHandle = ollamaService.rewriteTextStreaming(input, rewriteType, additionalInstructions, combinedContext,
                    chunk -> Platform.runLater(() -> {
                        aggregated.append(chunk);
                        statusLabel.setText("⏳ Läuft… " + aggregated.length() + " Zeichen");
                        resultArea.setText(aggregated.toString());
                        if (resultStage != null && resultStage.isShowing() && resultWebView != null) {
                            updateResultWebView(buildHtmlForAnswer(aggregated.toString()), true);
                        }
                    }),
                    () -> Platform.runLater(() -> {
                        insertButton.setDisable(false);
                        setGenerating(false);
                        updateStatus("✅ Text umgeschrieben: " + aggregated.length() + " Zeichen");
                        resultArea.setText(aggregated.toString());
                    }),
                    (Throwable err) -> Platform.runLater(() -> {
                        setGenerating(false);
                        updateStatus("Fehler: " + err.getMessage());
                    })
                );
                return;
                
            default: // Freier Text
                // Verwende den eingegebenen Kontext
                String freeContext = contextArea.getText() != null ? contextArea.getText().trim() : "";
                StringBuilder ctx = new StringBuilder();
                if (!freeContext.isEmpty()) {
                    ctx.append("=== BENUTZER-KONTEXT ===\n").append(freeContext).append("\n\n");
                }
                if (contextSnippet != null && !contextSnippet.isEmpty()) {
                    ctx.append("=== EDITOR-KONTEXT ===\n").append(contextSnippet);
                }
                String finalContext = ctx.length() > 0 ? ctx.toString() : null;
                
                // Streaming für freien Text
                currentStreamHandle = ollamaService.generateTextStreaming(input, finalContext,
                    chunk -> Platform.runLater(() -> {
                        aggregated.append(chunk);
                        statusLabel.setText("⏳ Läuft… " + aggregated.length() + " Zeichen");
                        resultArea.setText(aggregated.toString());
                        if (resultStage != null && resultStage.isShowing() && resultWebView != null) {
                            updateResultWebView(buildHtmlForAnswer(aggregated.toString()), true);
                        }
                    }),
                    () -> Platform.runLater(() -> {
                        insertButton.setDisable(false);
                        setGenerating(false);
                        updateStatus("✅ Text generiert: " + aggregated.length() + " Zeichen");
                        resultArea.setText(aggregated.toString());
                    }),
                    (Throwable err) -> Platform.runLater(() -> {
                        setGenerating(false);
                        updateStatus("Fehler: " + err.getMessage());
                    })
                );
                return;
        }
        
        // Alle Funktionen verwenden jetzt Streaming, daher ist der future-Code nicht mehr nötig
    }
    
    // Baut den vollständigen Prompt (inkl. Kontext) so, wie er an den Service übergeben würde
    private String buildCurrentFullPromptPreview() {
        String selectedFunction = functionComboBox.getValue();
        String input = inputArea.getText() != null ? inputArea.getText().trim() : "";
        if (input.isEmpty() && !"Chat-Assistent".equals(selectedFunction)) {
            return "(Kein Eingabetext)";
        }

        // Kontext sammeln (für alle Funktionen, inkl. Chat)
        StringBuilder selectedContexts = new StringBuilder();
        // Benutzer-Kontext
        if (cbUserContext != null && cbUserContext.isSelected() && contextArea != null) {
            String u = contextArea.getText();
            if (u != null && !u.trim().isEmpty()) {
                selectedContexts.append("\n=== BENUTZER-KONTEXT ===\n").append(limitText(u.trim())).append("\n");
            }
        }
        // Editor-Ausschnitt
        if (cbEditorSnippet != null && cbEditorSnippet.isSelected() && editorWindow != null) {
            try {
                String fullText = editorWindow.getText();
                String selected = editorWindow.getSelectedText();
                int caret = fullText != null ? Math.min(editorWindow.getCaretPosition(), Math.max(0, fullText.length())) : 0;
                if (fullText != null && !fullText.isEmpty()) {
                    int window = 12000; int half = window / 2;
                    int foundIndex = (selected != null && !selected.isEmpty()) ? fullText.indexOf(selected) : -1;
                    int anchor = foundIndex >= 0 ? foundIndex : caret;
                    int start = Math.max(0, anchor - half);
                    int end = Math.min(fullText.length(), (foundIndex >= 0 ? (foundIndex + selected.length()) : caret) + half);
                    if (end > start) {
                        String snip = fullText.substring(start, end);
                        selectedContexts.append("\n=== EDITOR-KONTEXT ===\n").append(limitText(snip)).append("\n");
                    }
                }
            } catch (Exception ignored) {}
        }
        // Projekt-Kontexte
        String currentDocx = getCurrentDocxFileName();
        if (currentDocx != null) {
            if (cbSynopsis != null && cbSynopsis.isSelected()) {
                String syn = synopsisArea != null ? synopsisArea.getText() : NovelManager.loadSynopsis(currentDocx);
                if (syn != null && !syn.trim().isEmpty()) selectedContexts.append("\n=== SYNOPSIS ===\n").append(limitText(syn)).append("\n");
            }
            if (cbCharacters != null && cbCharacters.isSelected()) {
                String chars = charactersArea != null ? charactersArea.getText() : NovelManager.loadCharacters(currentDocx);
                if (chars != null && !chars.trim().isEmpty()) selectedContexts.append("\n=== CHARAKTERE ===\n").append(limitText(chars)).append("\n");
            }
            if (cbWorldbuilding != null && cbWorldbuilding.isSelected()) {
                String wb = worldbuildingArea != null ? worldbuildingArea.getText() : NovelManager.loadWorldbuilding(currentDocx);
                if (wb != null && !wb.trim().isEmpty()) selectedContexts.append("\n=== WORLDBUILDING ===\n").append(limitText(wb)).append("\n");
            }
            if (cbOutline != null && cbOutline.isSelected()) {
                String ol = outlineArea != null ? outlineArea.getText() : NovelManager.loadOutline(currentDocx);
                if (ol != null && !ol.trim().isEmpty()) selectedContexts.append("\n=== OUTLINE ===\n").append(limitText(ol)).append("\n");
            }
            if (cbChapterNotes != null && cbChapterNotes.isSelected()) {
                String ch = chapterNotesArea != null ? chapterNotesArea.getText() : NovelManager.loadChapter(currentDocx);
                if (ch != null && !ch.trim().isEmpty()) selectedContexts.append("\n=== KAPITELNOTIZEN ===\n").append(limitText(ch)).append("\n");
            }
            if (cbStyle != null && cbStyle.isSelected()) {
                String st;
                if (styleNotesArea != null && styleNotesArea.getText() != null) {
                    st = styleNotesArea.getText();
                } else {
                    String dir = getNovelDirectory();
                    st = dir != null ? safeRead(new File(dir, "style.txt")) : "";
                }
                if (st != null && !st.trim().isEmpty()) selectedContexts.append("\n=== STIL ===\n").append(limitText(st)).append("\n");
            }
            String novelCtx = NovelManager.loadContext(currentDocx);
            if (novelCtx != null && !novelCtx.trim().isEmpty()) selectedContexts.append("\n=== ROMAN-KONTEXT ===\n").append(limitText(novelCtx)).append("\n");
        }

        String fullContext = selectedContexts.length() > 0 ? selectedContexts.toString().trim() : "";

        if ("Chat-Assistent".equals(selectedFunction)) {
            String userMsg = inputArea.getText();
            StringBuilder contextBuilder = new StringBuilder();
            // Chat-Historie (nur vollständige Paare)
            if (chatHistoryArea != null) {
                List<CustomChatArea.QAPair> sessionHistory = chatHistoryArea.getSessionHistory();
                if (sessionHistory != null && !sessionHistory.isEmpty()) {
                    for (CustomChatArea.QAPair qaPair : sessionHistory) {
                        if (qaPair.getAnswer() != null && !qaPair.getAnswer().trim().isEmpty()) {
                            contextBuilder.append("Du: ").append(qaPair.getQuestion()).append("\n");
                            contextBuilder.append("Assistent: ").append(qaPair.getAnswer()).append("\n");
                        }
                    }
                }
            }
            if (!fullContext.isEmpty()) {
                contextBuilder.append("\n").append(fullContext);
            }
            String ctx = contextBuilder.toString();
            StringBuilder fullPromptBuilder = new StringBuilder();
            fullPromptBuilder.append("Du bist ein hilfreicher deutscher Assistent.\nAntworte bitte auf Deutsch.\n");
            fullPromptBuilder.append("Regeln: Antworte ausschließlich mit der geforderten Ausgabe. Keine Erklärungen. ")
                              .append("Kein Vor- oder Nachtext. Keine Anführungszeichen. ")
                              .append("Gib niemals Kontext, Regeln oder die Frage wieder. Nur die reine Antwort.\n\n");
            if (!ctx.isEmpty()) fullPromptBuilder.append(ctx).append("\n");
            fullPromptBuilder.append("Du: ").append(userMsg).append("\n");
            fullPromptBuilder.append("Assistent: ");
            return fullPromptBuilder.toString();
        }

        // Prompt je Funktion (ohne eigentliche Anfrage auszulösen)
        String prompt;
        switch (selectedFunction) {
            case "Dialog generieren":
                prompt = String.format("Schreibe einen authentischen Dialog...\n\nEingabe:\n%s", input);
                break;
            case "Beschreibung erweitern":
                String style = styleField != null ? styleField.getText().trim() : "detailliert";
                prompt = String.format("Erweitere diese kurze Beschreibung im Stil '%s':\n\n%s\n\nSchreibe eine detaillierte, atmosphärische Beschreibung (max. 3 Sätze).", style, input);
                break;
            case "Plot-Ideen entwickeln":
                String genre = genreField != null ? genreField.getText().trim() : "Allgemein";
                prompt = String.format("Entwickle Plot-Ideen für eine %s-Geschichte basierend auf dieser Grundidee:\n%s\n\nGib 3-5 konkrete Plot-Entwicklungen an.", genre, input);
                break;
            case "Charakter entwickeln":
                String characterName = characterField != null && characterField.getText() != null && !characterField.getText().trim().isEmpty() ? characterField.getText().trim() : "Charakter";
                prompt = String.format("Entwickle den Charakter '%s' weiter:\n\nGrundmerkmale: %s\n\nErstelle ein detailliertes Charakterprofil...", characterName, input);
                break;
            case "Schreibstil analysieren":
                prompt = String.format("Analysiere NUR den Schreibstil dieses Textes:\n\n%s\n\nAntworte nur mit der Stil-Analyse!", input);
                break;
            case "Text umschreiben":
                String rewriteType = rewriteTypeComboBox != null ? rewriteTypeComboBox.getValue() : "Umschreiben";
                String add = additionalInstructionsField != null ? additionalInstructionsField.getText().trim() : "Keine";
                prompt = String.format("DU BIST EIN EXPERTE FÜR TEXT-UMFORMULIERUNG!\n\nORIGINALTEXT (nur dieser Abschnitt darf verändert werden):\n%s\n\nUMSCHREIBUNGSART: %s\n\nZUSÄTZLICHE ANWEISUNGEN: %s\n\nAUFGABE: Umschreibe NUR den oben angegebenen ORIGINALTEXT. Nutze Kontext nur zum Verständnis. WICHTIG: Antworte ausschließlich mit der umgeschriebenen Version des ORIGINALTEXTES.", input, rewriteType, (add.isEmpty()?"Keine":add));
                break;
            default:
                prompt = input;
        }

        if (!fullContext.isEmpty()) {
            String rules = "Regeln: Du erhältst zuerst 'Kontext', dann 'Anweisung'. Der Kontext dient NUR dem Verständnis.\n" +
                           "Gib NIEMALS den Kontext wieder.\n" +
                           "Antworte ausschließlich mit dem Ergebnis aus der Anweisung.\n" +
                           "Keine Erklärungen, kein Vor-/Nachtext, keine Anführungszeichen.";
            return rules + "\n\nKontext:\n" + fullContext + "\n\nAnweisung:\n" + prompt;
        }
        return prompt;
    }

    // Liefert den kombinierten Kontext entsprechend der gesetzten Checkboxen
    private String buildContextForPreview() {
        StringBuilder selectedContexts = new StringBuilder();
        // Benutzer-Kontext
        if (cbUserContext != null && cbUserContext.isSelected() && contextArea != null) {
            String u = contextArea.getText();
            if (u != null && !u.trim().isEmpty()) {
                selectedContexts.append("\n=== BENUTZER-KONTEXT ===\n").append(limitText(u.trim())).append("\n");
            }
        }
        // Editor-Ausschnitt
        if (cbEditorSnippet != null && cbEditorSnippet.isSelected() && editorWindow != null) {
            try {
                String fullText = editorWindow.getText();
                String selected = editorWindow.getSelectedText();
                int caret = fullText != null ? Math.min(editorWindow.getCaretPosition(), Math.max(0, fullText.length())) : 0;
                if (fullText != null && !fullText.isEmpty()) {
                    int window = 12000; int half = window / 2;
                    int foundIndex = (selected != null && !selected.isEmpty()) ? fullText.indexOf(selected) : -1;
                    int anchor = foundIndex >= 0 ? foundIndex : caret;
                    int start = Math.max(0, anchor - half);
                    int end = Math.min(fullText.length(), (foundIndex >= 0 ? (foundIndex + selected.length()) : caret) + half);
                    if (end > start) {
                        String snip = fullText.substring(start, end);
                        selectedContexts.append("\n=== EDITOR-KONTEXT ===\n").append(limitText(snip)).append("\n");
                    }
                }
            } catch (Exception ignored) {}
        }
        // Projekt-Kontexte
        String currentDocx = getCurrentDocxFileName();
        if (currentDocx != null) {
            if (cbSynopsis != null && cbSynopsis.isSelected()) {
                String syn = synopsisArea != null ? synopsisArea.getText() : NovelManager.loadSynopsis(currentDocx);
                if (syn != null && !syn.trim().isEmpty()) selectedContexts.append("\n=== SYNOPSIS ===\n").append(limitText(syn)).append("\n");
            }
            if (cbCharacters != null && cbCharacters.isSelected()) {
                String chars = charactersArea != null ? charactersArea.getText() : NovelManager.loadCharacters(currentDocx);
                if (chars != null && !chars.trim().isEmpty()) selectedContexts.append("\n=== CHARAKTERE ===\n").append(limitText(chars)).append("\n");
            }
            if (cbWorldbuilding != null && cbWorldbuilding.isSelected()) {
                String wb = worldbuildingArea != null ? worldbuildingArea.getText() : NovelManager.loadWorldbuilding(currentDocx);
                if (wb != null && !wb.trim().isEmpty()) selectedContexts.append("\n=== WORLDBUILDING ===\n").append(limitText(wb)).append("\n");
            }
            if (cbOutline != null && cbOutline.isSelected()) {
                String ol = outlineArea != null ? outlineArea.getText() : NovelManager.loadOutline(currentDocx);
                if (ol != null && !ol.trim().isEmpty()) selectedContexts.append("\n=== OUTLINE ===\n").append(limitText(ol)).append("\n");
            }
            if (cbChapterNotes != null && cbChapterNotes.isSelected()) {
                String ch = chapterNotesArea != null ? chapterNotesArea.getText() : NovelManager.loadChapter(currentDocx);
                if (ch != null && !ch.trim().isEmpty()) selectedContexts.append("\n=== KAPITELNOTIZEN ===\n").append(limitText(ch)).append("\n");
            }
            if (cbStyle != null && cbStyle.isSelected()) {
                String st;
                if (styleNotesArea != null && styleNotesArea.getText() != null) {
                    st = styleNotesArea.getText();
                } else {
                    String dir = getNovelDirectory();
                    st = dir != null ? safeRead(new File(dir, "style.txt")) : "";
                }
                if (st != null && !st.trim().isEmpty()) selectedContexts.append("\n=== STIL ===\n").append(limitText(st)).append("\n");
            }
            String novelCtx = NovelManager.loadContext(currentDocx);
            if (novelCtx != null && !novelCtx.trim().isEmpty()) selectedContexts.append("\n=== ROMAN-KONTEXT ===\n").append(limitText(novelCtx)).append("\n");
        }
        return selectedContexts.length() > 0 ? selectedContexts.toString().trim() : null;
    }

    // Zeigt eine themenfähige Stage mit einer read-only TextArea
    private void showPromptPreviewStage(String title, String content) {
        CustomStage s = StageManager.createStage(title);
        s.setTitle(title);
        TextArea ta = new TextArea(content == null ? "" : content);
        ta.setWrapText(true);
        ta.setEditable(false);
        // Volle Ausnutzung des Fensters
        ta.setMaxWidth(Double.MAX_VALUE);
        ta.setMaxHeight(Double.MAX_VALUE);
        VBox box = new VBox(8, ta);
        VBox.setVgrow(ta, Priority.ALWAYS);
        box.setFillWidth(true);
        box.setPadding(new Insets(10));
        Scene sc = new Scene(box, 900, 700);
        // Styles anwenden (ResourceManager, damit Pfade konsistent sind)
        String stylesCssPath = ResourceManager.getCssResource("css/styles.css");
        String editorCssPath = ResourceManager.getCssResource("css/editor.css");
        if (stylesCssPath != null) sc.getStylesheets().add(stylesCssPath);
        if (editorCssPath != null) sc.getStylesheets().add(editorCssPath);
        s.setSceneWithTitleBar(sc);
        s.initOwner(stage);
        s.show();
        // Theme-Klasse spiegeln
        applyThemeToNode(box, currentThemeIndex);
        applyThemeToNode(ta, currentThemeIndex);
    }

    // Sehr einfache Lesbarmachung: ein paar Leerzeilen zwischen Abschnitten
    private String formatPromptSimple(String prompt) {
        if (prompt == null) return "";
        String s = prompt.replace("\r\n", "\n").replace('\r', '\n');
        // Abschnittsüberschriften optisch trennen
        s = s.replace("\nKontext:\n", "\n\nKontext:\n");
        s = s.replace("\nAnweisung:\n", "\n\nAnweisung:\n");
        // Regeln ggf. umbrechen
        if (s.startsWith("Regeln:")) {
            s = s.replace("Regeln: ", "Regeln:\n");
        }
        // Mehr als zwei Leerzeilen vermeiden
        s = s.replaceAll("\n{3,}", "\n\n");
        return s.trim();
    }

    // Formatiert das JSON für Menschen: zeigt Modell, Optionen und ent-escapten Prompt separat
    private String jsonToHuman(String json) {
        if (json == null || json.isEmpty()) return "(leer)";
        try {
            String model = extractJsonValue(json, "model");
            String promptEsc = extractJsonValue(json, "prompt");
            String numPredict = extractJsonOption(json, "num_predict");
            String temp = extractJsonOption(json, "temperature");
            String topP = extractJsonOption(json, "top_p");
            String repPen = extractJsonOption(json, "repeat_penalty");
            String prompt = unescapeJson(promptEsc);
            StringBuilder sb = new StringBuilder();
            sb.append("Model: ").append(model).append("\n");
            sb.append("Optionen:\n");
            sb.append("  num_predict: ").append(numPredict).append("\n");
            sb.append("  temperature: ").append(temp).append("\n");
            sb.append("  top_p: ").append(topP).append("\n");
            sb.append("  repeat_penalty: ").append(repPen).append("\n\n");

            // Prompt in Abschnitte zerlegen
            String rulesHeader = "Regeln:";
            String anweisungHeader = "\n\nAnweisung:\n";
            String contextHeader = "\n\nKontext:\n";
            if (prompt.startsWith(rulesHeader)) {
                int ctxIdx = prompt.indexOf(contextHeader);
                int anwIdx = prompt.indexOf(anweisungHeader);
                if (ctxIdx >= 0 && anwIdx > ctxIdx) {
                    String rules = prompt.substring(0, ctxIdx).trim();
                    String context = prompt.substring(ctxIdx + contextHeader.length(), anwIdx).trim();
                    String instr = prompt.substring(anwIdx + anweisungHeader.length()).trim();
                    sb.append("Regeln:\n").append(rules.replaceFirst("^Regeln: ", "")).append("\n\n");
                    // Kontext gliedern
                    sb.append("Kontext (gekürzt):\n");
                    sb.append(context).append("\n\n");
                    sb.append("Anweisung:\n").append(instr);
                    return sb.toString();
                }
            }
            // Fallback: kompletter Prompt
            sb.append("Prompt:\n").append(prompt);
            return sb.toString();
        } catch (Exception e) {
            return json; // Fallback: rohes JSON
        }
    }

    private String extractJsonValue(String json, String key) {
        String needle = "\"" + key + "\":\"";
        int i = json.indexOf(needle);
        if (i < 0) return "";
        int start = i + needle.length();
        StringBuilder sb = new StringBuilder();
        boolean esc = false;
        for (int p = start; p < json.length(); p++) {
            char c = json.charAt(p);
            if (esc) {
                sb.append(c);
                esc = false;
            } else if (c == '\\') {
                esc = true;
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private String extractJsonOption(String json, String key) {
        String needle = "\"" + key + "\":";
        int i = json.indexOf(needle);
        if (i < 0) return "";
        int start = i + needle.length();
        StringBuilder sb = new StringBuilder();
        for (int p = start; p < json.length(); p++) {
            char c = json.charAt(p);
            if (c == ',' || c == '}' ) break;
            sb.append(c);
        }
        return sb.toString().trim();
    }

    private String unescapeJson(String s) {
        if (s == null) return null;
        StringBuilder sb = new StringBuilder();
        boolean esc = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (esc) {
                switch (c) {
                    case '\\': sb.append('\\'); break;
                    case '"': sb.append('"'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    default: sb.append(c); break;
                }
                esc = false;
            } else if (c == '\\') {
                esc = true;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // entfernt: prettyPrintJson (nicht mehr benötigt)
    
    private void insertToEditor() {
        String selectedFunction = functionComboBox.getValue();
        String textToInsert = "";
        
        if ("Chat-Assistent".equals(selectedFunction)) {
            // Bei Chat: Verwende den letzten Text aus der Chat-Historie
            List<CustomChatArea.QAPair> history = chatHistoryArea.getSessionHistory();
            if (!history.isEmpty()) {
                CustomChatArea.QAPair lastPair = history.get(history.size() - 1);
                textToInsert = lastPair.getAnswer();
            }
        } else {
            // Bei anderen Funktionen: Verwende den Text aus der resultArea
            textToInsert = resultArea.getText();
        }
        
        if (!textToInsert.isEmpty()) {
            if (editorWindow != null) {
                editorWindow.insertTextFromAI(textToInsert);
                updateStatus("Text in Editor eingefügt: " + textToInsert.length() + " Zeichen");
            } else {
                showAlert("Fehler", "Keine Verbindung zum Editor verfügbar.");
            }
        } else {
            showAlert("Kein Text verfügbar", "Es ist kein Text zum Einfügen verfügbar.");
        }
    }

    private String limitText(String text) {
        if (text == null) return null;
        int max = 2000; // Reduziert von 8000 auf 2000 Zeichen pro Kontext-Bereich
        return text.length() > max ? text.substring(0, max) + "\n..." : text;
    }
    
    private void checkOllamaStatus() {
        updateStatus("Überprüfe Ollama-Status...");
        
        ollamaService.isOllamaRunning().thenAccept(isRunning -> {
            Platform.runLater(() -> {
                if (isRunning) {
                    updateStatus("Ollama läuft");
                } else {
                    updateStatus("Ollama nicht erreichbar – Generieren ist dennoch möglich, es erscheint ggf. eine Fehlermeldung.");
                    // Button NICHT mehr global deaktivieren; Fehler wird beim Versuch abgefangen
                    if (shouldShowOllamaDialog()) {
                        showOllamaInstallationDialog();
                    }
                }
                // Immer sicherstellen, dass der Button klickbar ist (außer während setGenerating(true))
                if (!progressIndicator.isVisible()) {
                    generateButton.setDisable(false);
                }
            });
        });
    }

    private void loadProjectContexts() {
        try {
            String dir = getNovelDirectory();
            String currentDocx = getCurrentDocxFileName();
            if (dir == null || dir.isEmpty()) return;

            // Basisdateien still anlegen, falls sie fehlen
            try { if (!new File(dir, NovelManager.SYNOPSIS_FILE).exists()) writeText(new File(dir, NovelManager.SYNOPSIS_FILE), ""); } catch (Exception ignored) {}
            try { if (!new File(dir, NovelManager.CHARACTERS_FILE).exists()) writeText(new File(dir, NovelManager.CHARACTERS_FILE), ""); } catch (Exception ignored) {}
            try { if (!new File(dir, NovelManager.WORLDBUILDING_FILE).exists()) writeText(new File(dir, NovelManager.WORLDBUILDING_FILE), ""); } catch (Exception ignored) {}
            try { if (!new File(dir, NovelManager.OUTLINE_FILE).exists()) writeText(new File(dir, NovelManager.OUTLINE_FILE), ""); } catch (Exception ignored) {}
            try { if (!new File(dir, NovelManager.CONTEXT_FILE).exists()) writeText(new File(dir, NovelManager.CONTEXT_FILE), ""); } catch (Exception ignored) {}
            try { if (!new File(dir, "chapter.txt").exists()) writeText(new File(dir, "chapter.txt"), ""); } catch (Exception ignored) {}
            try { if (!new File(dir, "style.txt").exists()) writeText(new File(dir, "style.txt"), ""); } catch (Exception ignored) {}

            String syn = safeRead(new File(dir, NovelManager.SYNOPSIS_FILE));
            String chars = safeRead(new File(dir, NovelManager.CHARACTERS_FILE));
            String wb = safeRead(new File(dir, NovelManager.WORLDBUILDING_FILE));
            String ol = safeRead(new File(dir, NovelManager.OUTLINE_FILE));
            String chap = (currentDocx != null) ? NovelManager.loadChapter(currentDocx) : safeRead(new File(dir, "chapter.txt"));
            String ctx = safeRead(new File(dir, NovelManager.CONTEXT_FILE));
            String style = safeRead(new File(dir, "style.txt"));

            if (synopsisArea != null) synopsisArea.setText(syn);
            if (charactersArea != null) charactersArea.setText(chars);
            if (worldbuildingArea != null) worldbuildingArea.setText(wb);
            if (outlineArea != null) outlineArea.setText(ol);
            if (chapterNotesArea != null) chapterNotesArea.setText(chap);
            if (contextArea != null && !ctx.equals(contextArea.getText())) contextArea.setText(ctx);
            if (styleNotesArea != null) styleNotesArea.setText(style);
        } catch (Exception ignored) { }
    }

    // Debounced Auto-Save Hilfsfunktionen für Projekt-Kontexte
    private java.util.Timer debounceTimerSynopsis;
    private java.util.Timer debounceTimerCharacters;
    private java.util.Timer debounceTimerWorldbuilding;
    private java.util.Timer debounceTimerOutline;
    private java.util.Timer debounceTimerChapter;

    private void setupAutoSave(TextArea area, String fileName) {
        area.textProperty().addListener((obs, o, n) -> {
            debounceSave(area, fileName, n);
        });
    }

    private void debounceSave(TextArea area, String fileName, String content) {
        java.util.Timer timer;
        if (fileName.equals(NovelManager.SYNOPSIS_FILE)) timer = resetTimer("synopsis");
        else if (fileName.equals(NovelManager.CHARACTERS_FILE)) timer = resetTimer("characters");
        else if (fileName.equals(NovelManager.WORLDBUILDING_FILE)) timer = resetTimer("worldbuilding");
        else if (fileName.equals(NovelManager.OUTLINE_FILE)) timer = resetTimer("outline");
        else timer = resetTimer("chapter");

        timer.schedule(new java.util.TimerTask() {
            @Override public void run() {
                Platform.runLater(() -> {
                    String dir = getNovelDirectory();
                    if (dir != null && !dir.isEmpty()) {
                        try {
                            writeText(new File(dir, fileName), content);
                            updateStatus("Gespeichert: " + fileName);
                        } catch (Exception e) {
                            logger.warning("Fehler beim Speichern von " + fileName + ": " + e.getMessage());
                        }
                    }
                });
            }
        }, 600);
    }

    private java.util.Timer resetTimer(String key) {
        java.util.Timer t;
        switch (key) {
            case "synopsis": if (debounceTimerSynopsis != null) debounceTimerSynopsis.cancel(); t = new java.util.Timer(); debounceTimerSynopsis = t; return t;
            case "characters": if (debounceTimerCharacters != null) debounceTimerCharacters.cancel(); t = new java.util.Timer(); debounceTimerCharacters = t; return t;
            case "worldbuilding": if (debounceTimerWorldbuilding != null) debounceTimerWorldbuilding.cancel(); t = new java.util.Timer(); debounceTimerWorldbuilding = t; return t;
            case "outline": if (debounceTimerOutline != null) debounceTimerOutline.cancel(); t = new java.util.Timer(); debounceTimerOutline = t; return t;
            case "style": return new java.util.Timer();
            default: if (debounceTimerChapter != null) debounceTimerChapter.cancel(); t = new java.util.Timer(); debounceTimerChapter = t; return t;
        }
    }

    private void debouncedSaveChapter(String content) {
        java.util.Timer t = resetTimer("chapter");
        t.schedule(new java.util.TimerTask() {
            @Override public void run() {
                Platform.runLater(() -> {
                    String dir = getNovelDirectory();
                    if (dir != null && !dir.isEmpty()) {
                        try {
                            writeText(new File(dir, "chapter.txt"), content);
                            updateStatus("Gespeichert: Kapitelnotizen");
                        } catch (Exception e) {
                            logger.warning("Fehler beim Speichern der Kapitelnotizen: " + e.getMessage());
                        }
                    }
                });
            }
        }, 600);
    }
    
    private void cancelAllDebounceTimers() {
        if (debounceTimerSynopsis != null) debounceTimerSynopsis.cancel();
        if (debounceTimerCharacters != null) debounceTimerCharacters.cancel();
        if (debounceTimerWorldbuilding != null) debounceTimerWorldbuilding.cancel();
        if (debounceTimerOutline != null) debounceTimerOutline.cancel();
        if (debounceTimerChapter != null) debounceTimerChapter.cancel();
    }

    private void saveAllContextsNow() {
        String dir = getNovelDirectory();
        if (dir == null || dir.isEmpty()) return;
        try {
            writeText(new File(dir, NovelManager.SYNOPSIS_FILE), synopsisArea != null ? synopsisArea.getText() : "");
            writeText(new File(dir, NovelManager.CHARACTERS_FILE), charactersArea != null ? charactersArea.getText() : "");
            writeText(new File(dir, NovelManager.WORLDBUILDING_FILE), worldbuildingArea != null ? worldbuildingArea.getText() : "");
            writeText(new File(dir, NovelManager.OUTLINE_FILE), outlineArea != null ? outlineArea.getText() : "");
            writeText(new File(dir, "chapter.txt"), chapterNotesArea != null ? chapterNotesArea.getText() : "");
            writeText(new File(dir, NovelManager.CONTEXT_FILE), contextArea != null ? contextArea.getText() : "");
            writeText(new File(dir, "style.txt"), styleNotesArea != null ? styleNotesArea.getText() : "");
            updateStatus("Alle TextAreas gespeichert");
        } catch (Exception e) {
            logger.warning("Fehler beim Speichern der TextAreas beim Schließen: " + e.getMessage());
        }
    }

    private static void writeText(File f, String content) throws java.io.IOException {
        if (f == null) return;
        java.nio.file.Files.writeString(f.toPath(), content != null ? content : "", java.nio.charset.StandardCharsets.UTF_8);
    }
    
    private static String safeRead(File f) {
        try { return (f != null && f.exists()) ? java.nio.file.Files.readString(f.toPath()) : ""; } catch (Exception e) { return ""; }
    }
    
    private void setGenerating(boolean generating) {
        generateButton.setDisable(generating);
        progressIndicator.setVisible(generating);
        // Cancel-Button aktivieren/deaktivieren
        Node parent = generateButton.getParent();
        if (parent instanceof HBox) {
            for (javafx.scene.Node child : ((HBox) parent).getChildren()) {
                if (child instanceof Button && "Abbrechen".equals(((Button) child).getText())) {
                    child.setDisable(!generating);
                }
            }
        }
        if (generating) {
            generateButton.setText("Generiere...");
        } else {
            generateButton.setText("Generieren");
        }
    }

    // Bricht laufende Anfrage ab (UI-seitig)
    private volatile boolean cancelRequested = false;
    private void cancelOllamaRequest() {
        cancelRequested = true;
        if (currentStreamHandle != null) {
            currentStreamHandle.cancel();
        }
        updateStatus("⏹️ Anfrage abgebrochen");
        setGenerating(false);
    }

    // Öffnet ein Vollbild-Fenster (WebView) mit der aktuellen Antwort (intelligent gerendert)
    private void openResultInWindow() {
        try {
            String answer = "";
            // Priorität: KI-Assistenten-Ergebnis (resultArea) vor Chat-Ergebnis
            if (resultArea != null && !resultArea.getText().trim().isEmpty()) {
                answer = resultArea.getText();
            } else if (lastPureChatAnswer != null && !lastPureChatAnswer.trim().isEmpty()) {
                // Für Chat: Reinen Text ohne Header verwenden
                answer = lastPureChatAnswer;
            } else if (chatHistoryArea != null) {
                answer = chatHistoryArea.getCurrentAnswer();
                if (answer == null) answer = "";
            }
            if (resultStage == null) {
                resultStage = StageManager.createStage("Ergebnis (gerendert)");
                resultStage.setTitle("Ergebnis (gerendert)");
                resultWebView = new javafx.scene.web.WebView();
                resultWebView.setContextMenuEnabled(true);
                resultWebView.setPrefSize(1000, 800);
                
                // Button für Text-Ersetzung hinzufügen
                Button replaceSelectedButton = new Button("📝 Markierten Text ersetzen");
                replaceSelectedButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8px 16px;");
                replaceSelectedButton.setOnAction(e -> replaceSelectedTextInEditor());
                
                VBox box = new VBox(5);
                box.getChildren().addAll(replaceSelectedButton, resultWebView);
                VBox.setVgrow(resultWebView, Priority.ALWAYS);
                box.setPadding(new Insets(10));
                
                Scene sc = new Scene(box, 1000, 800);
                resultStage.setSceneWithTitleBar(sc);
                resultStage.initOwner(stage);
                applyThemeToNode(box, currentThemeIndex);
            }
            // Inhalt laden/refreshen und anzeigen (kein Auto-Scroll beim Öffnen)
            updateResultWebView(buildHtmlForAnswer(answer), false);
            if (!resultStage.isShowing()) resultStage.show();
        } catch (Exception e) {
            showAlert("Fehler", "Konnte Ergebnisfenster nicht öffnen: " + e.getMessage());
        }
    }

    private String buildHtmlForAnswer(String text) {
        String bgColor;
        String fgColor;
        String codeBg;
        switch (currentThemeIndex) {
            case 0: bgColor = "#ffffff"; fgColor = "#111827"; codeBg = "#f5f5f5"; break; // Weiß
            case 2: bgColor = "#ffffff"; fgColor = "#111827"; codeBg = "#f3f4f6"; break; // Pastell hell
            case 3: bgColor = "#0b1220"; fgColor = "#e2e8f0"; codeBg = "#0f172a"; break; // Blau dunkel (etwas dunkler)
            case 4: bgColor = "#064e3b"; fgColor = "#d1fae5"; codeBg = "#065f46"; break; // Grün (angepasst an Theme CSS)
            case 5: bgColor = "#581c87"; fgColor = "#f3e8ff"; codeBg = "#7c3aed"; break; // Lila (angepasst)
            default: bgColor = "#1f2937"; fgColor = "#e5e7eb"; codeBg = "#111827"; // Dark
        }
        if (text == null) text = "";
        
        // Unicode-Escape-Sequenzen in echte HTML-Tags umwandeln (für gesamten Text)
        text = text.replace("u003c", "<").replace("u003e", ">");

        // Header-Zeile im Format: [model | param, param, ...] erkennen
        String headerHtml = "";
        String bodyText = text;
        try {
            String trimmed = text.replace("\r\n", "\n");
            // erste nicht-leere Zeile
            int idx = 0;
            while (idx < trimmed.length() && (trimmed.charAt(idx) == '\n' || trimmed.charAt(idx) == '\r')) idx++;
            int nl = trimmed.indexOf('\n', idx);
            String firstLine = (nl >= 0 ? trimmed.substring(idx, nl) : trimmed.substring(idx)).trim();
            if (firstLine.startsWith("[") && firstLine.contains("]")) {
                int rb = firstLine.indexOf(']');
                String inside = firstLine.substring(1, rb).trim();
                bodyText = (nl >= 0 ? trimmed.substring(nl + 1) : "");
                String model = inside;
                String params = null;
                int bar = inside.indexOf('|');
                if (bar >= 0) {
                    model = inside.substring(0, bar).trim();
                    params = inside.substring(bar + 1).trim();
                }
                String chipBg = codeBg;
                String chipBorder = (currentThemeIndex == 4) ? "#047857" : (currentThemeIndex == 3 ? "#1d4ed8" : (currentThemeIndex == 5 ? "#6d28d9" : "#9ca3af"));
                String chipText = fgColor;
                String modelChipBg = (currentThemeIndex == 4) ? "#065f46" : (currentThemeIndex == 3 ? "#0f172a" : (currentThemeIndex == 5 ? "#7c3aed" : chipBg));
                String modelChipText = fgColor;
                StringBuilder header = new StringBuilder();
                header.append("<div class='answer-header'>");
                header.append("<span class='chip model' style='background:").append(modelChipBg).append(";color:").append(modelChipText)
                      .append(";border:1px solid ").append(chipBorder).append(";'>").append(escapeHtml(model)).append("</span>");
                if (params != null && !params.isEmpty()) {
                    for (String p : params.split(",")) {
                        String t = p.trim();
                        if (t.isEmpty()) continue;
                        header.append("<span class='chip' style='background:").append(chipBg).append(";color:").append(chipText)
                              .append(";border:1px solid ").append(chipBorder).append(";'>").append(escapeHtml(t)).append("</span>");
                    }
                }
                header.append("</div>");
                headerHtml = header.toString();
            }
        } catch (Exception ignored) {}

        String htmlBody;
        if (looksLikeJson(bodyText)) {
            htmlBody = "<pre><code class='json'>" + escapeHtml(prettyJson(bodyText)) + "</code></pre>";
        } else {
            htmlBody = markdownToHtml(bodyText);
        }
        // Schönere Typographie/Abstände und zentrierte Inhaltsbreite
        String linkColor = (currentThemeIndex == 4) ? "#34d399" : (currentThemeIndex == 3 ? "#60a5fa" : (currentThemeIndex == 5 ? "#c084fc" : "#2563eb"));
        String borderColor = (currentThemeIndex == 4) ? "#047857" : (currentThemeIndex == 3 ? "#1d4ed8" : (currentThemeIndex == 5 ? "#6d28d9" : "#9ca3af"));
        String muted = (currentThemeIndex == 4) ? "#a7f3d0" : (currentThemeIndex == 3 ? "#cbd5e1" : (currentThemeIndex == 5 ? "#e9d5ff" : "#6b7280"));

        String css = "html,body{height:100%;} body{margin:0;background:"+bgColor+";color:"+fgColor+";font-family:Segoe UI,Arial,sans-serif;line-height:1.65;}"+
                ".content{max-width:920px;margin:24px auto 28px auto;padding:0 18px;}"+
                "h1{font-size:1.8rem;margin:1.2em 0 0.4em;} h2{font-size:1.5rem;margin:1.1em 0 0.4em;} h3{font-size:1.25rem;margin:1em 0 0.4em;} h4,h5,h6{margin:0.9em 0 0.35em;}"+
                "p{margin:0.6em 0;} ul,ol{padding-left:24px;margin:0.4em 0;} li{margin:0.2em 0;}"+
                "pre,code{background:"+codeBg+";border-radius:8px;} pre{padding:12px 14px;overflow:auto;border:1px solid "+borderColor+";} code{padding:2px 6px;border:1px solid "+borderColor+";}"+
                "table{border-collapse:collapse;width:100%;margin:0.8em 0;} th,td{border:1px solid "+borderColor+";padding:8px 10px;} th{background:"+codeBg+";color:"+fgColor+";}"+
                "a{color:"+linkColor+";text-decoration:none;} a:hover{text-decoration:underline;}"+
                ".muted{color:"+muted+";}"+
                ".answer-header{display:flex;flex-wrap:wrap;gap:8px;margin:0 0 12px 0;} .chip{padding:4px 8px;border-radius:999px;font-size:12px;} .chip.model{font-weight:600;}";
        return "<!doctype html><html><head><meta charset='utf-8'><style>"+css+"</style></head><body><div class='content'>"+headerHtml+htmlBody+"</div></body></html>";
    }

    private boolean looksLikeJson(String s) {
        String t = s.trim();
        return (t.startsWith("{") && t.endsWith("}")) || (t.startsWith("[") && t.endsWith("]"));
    }

    private String prettyJson(String s) {
        try {
            com.google.gson.JsonElement el = com.google.gson.JsonParser.parseString(s);
            return new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(el);
        } catch (Exception e) {
            return s;
        }
    }

    private String markdownToHtml(String md) {
        // Sehr einfache Markdown-Konvertierung (Headings, Code-Fences, Inline-Code, Bold/Italic, Links, Listen)
        String s = md.replace("\r\n", "\n").replace('\r', '\n');
        // Code-Fences ``` mittels Matcher ersetzen
        try {
            java.util.regex.Pattern fence = java.util.regex.Pattern.compile("(?s)```(\\w+)?\\n(.*?)```\n?");
            java.util.regex.Matcher m = fence.matcher(s);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                String lang = m.group(1) == null ? "" : (" class='" + m.group(1) + "'");
                String body = escapeHtml(m.group(2));
                String repl = "<pre><code" + lang + ">" + body + "</code></pre>";
                m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(repl));
            }
            m.appendTail(sb);
            s = sb.toString();
        } catch (Exception ignore) {}
        // Headings
        s = s.replaceAll("(?m)^######\\s+(.*)$", "<h6>$1</h6>");
        s = s.replaceAll("(?m)^#####\\s+(.*)$", "<h5>$1</h5>");
        s = s.replaceAll("(?m)^####\\s+(.*)$", "<h4>$1</h4>");
        s = s.replaceAll("(?m)^###\\s+(.*)$", "<h3>$1</h3>");
        s = s.replaceAll("(?m)^##\\s+(.*)$", "<h2>$1</h2>");
        s = s.replaceAll("(?m)^#\\s+(.*)$", "<h1>$1</h1>");
        // Listen manuell gruppieren und Inline-Formatierungen anwenden
        String[] lines = s.split("\n", -1);
        StringBuilder html = new StringBuilder();
        boolean inUl = false, inOl = false;
        int ulIndent = -1;
        int olIndent = -1;
        StringBuilder para = new StringBuilder();
        Runnable flushPara = () -> { if (para.length() > 0) { html.append("<p>").append(para.toString()).append("</p>"); para.setLength(0);} };
        for (String line : lines) {
            int leading = 0; while (leading < line.length() && line.charAt(leading) == ' ') leading++;
            String bl = line.trim();
            java.util.regex.Matcher mNum = java.util.regex.Pattern.compile("^(\\d+)\\.\\s+(.*)$").matcher(bl);
            java.util.regex.Matcher mBul = java.util.regex.Pattern.compile("^(?:-|\\*)\\s+(.*)$").matcher(bl);
            if (mBul.matches()) {
                // Bullet unter Umständen als Unterliste innerhalb einer OL belassen: OL NICHT schließen
                // UL je nach Einrückung öffnen
                if (!inUl) { flushPara.run(); html.append("<ul>"); inUl = true; ulIndent = leading; }
                html.append("<li>").append(applyInlineMd(mBul.group(1))).append("</li>");
            } else if (mNum.matches()) {
                // Falls gerade eine UL auf tieferer Einrückung offen ist und wir auf OL-Ebene zurückkehren, UL schließen
                if (inUl && leading <= ulIndent) { html.append("</ul>"); inUl = false; ulIndent = -1; }
                if (!inOl) { flushPara.run(); html.append("<ol>"); inOl = true; olIndent = leading; }
                html.append("<li>").append(applyInlineMd(mNum.group(2))).append("</li>");
            } else if (bl.isEmpty()) {
                if (inUl) { html.append("</ul>"); inUl = false; ulIndent = -1; }
                // OL bewusst offen lassen, damit Nummerierung nach Unterliste weiterläuft
                flushPara.run();
            } else {
                if (inUl) { html.append("</ul>"); inUl = false; ulIndent = -1; }
                if (inOl) { html.append("</ol>"); inOl = false; olIndent = -1; }
                if (para.length() > 0) para.append("<br>");
                para.append(applyInlineMd(bl));
            }
        }
        if (inUl) html.append("</ul>");
        if (inOl) html.append("</ol>");
        if (para.length() > 0) html.append("<p>").append(para.toString()).append("</p>");
        return html.toString();
    }

    private String applyInlineMd(String t) {
        if (t == null) return "";
        String r = t;
        r = r.replaceAll("`([^`]+)`", "<code>$1</code>");
        r = r.replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>");
        r = r.replaceAll("_(.+?)_", "<em>$1</em>");
        r = r.replaceAll("\\[(.*?)\\]\\((.*?)\\)", "<a href='$2' target='_blank' rel='noreferrer noopener'>$1</a>");
        return r;
    }

    private String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // Lädt HTML in das Ergebnis-WebView und scrollt optional ans Ende (nach Render)
    private void updateResultWebView(String html, boolean scrollToBottom) {
        if (resultWebView == null) return;
        resultWebView.getEngine().loadContent(html, "text/html");
        if (scrollToBottom) {
            resultWebView.getEngine().getLoadWorker().stateProperty().addListener((obs, old, state) -> {
                if (state == javafx.concurrent.Worker.State.SUCCEEDED) {
                    try {
                        resultWebView.getEngine().executeScript("window.scrollTo(0, document.body.scrollHeight);");
                    } catch (Exception ignored) {}
                }
            });
        }
    }
    
    /**
     * Ersetzt den markierten Text im WebView mit dem markierten Text im Editor
     */
    private void replaceSelectedTextInEditor() {
        try {
            if (resultWebView == null || editorWindow == null) {
                showAlert("Fehler", "WebView oder Editor nicht verfügbar");
                return;
            }
            
            // JavaScript ausführen um markierten Text zu holen
            String selectedText = (String) resultWebView.getEngine().executeScript(
                "window.getSelection().toString()"
            );
            
            if (selectedText == null || selectedText.trim().isEmpty()) {
                showAlert("Hinweis", "Kein Text im WebView markiert. Bitte markieren Sie zuerst Text im gerenderten Fenster.");
                return;
            }
            
            // Prüfe ob im Editor Text markiert ist
            String editorSelectedText = editorWindow.getSelectedText();
            boolean hasSelection = editorSelectedText != null && !editorSelectedText.trim().isEmpty();
            
            // Bestätigung anzeigen
            CustomAlert confirmAlert = new CustomAlert(Alert.AlertType.CONFIRMATION, "Text einfügen");
            confirmAlert.setHeaderText(hasSelection ? "Text ersetzen" : "Text einfügen");
            
            String contentText;
            if (hasSelection) {
                contentText = "Soll der markierte Text im Editor:\n\n" +
                             "\"" + editorSelectedText + "\"\n\n" +
                             "durch den markierten Text aus dem WebView:\n\n" +
                             "\"" + selectedText + "\"\n\n" +
                             "ersetzt werden?";
            } else {
                contentText = "Soll der markierte Text aus dem WebView:\n\n" +
                             "\"" + selectedText + "\"\n\n" +
                             "an der Cursor-Position im Editor eingefügt werden?";
            }
            
            confirmAlert.setContentText(contentText);
            confirmAlert.applyTheme(currentThemeIndex);
            confirmAlert.initOwner(resultStage);
            
            Optional<ButtonType> result = confirmAlert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                if (hasSelection) {
                    // Text im Editor ersetzen
                    editorWindow.replaceSelectedText(selectedText);
                    updateStatus("Text erfolgreich ersetzt");
                } else {
                    // Text an Cursor-Position einfügen
                    editorWindow.insertTextAtCursor(selectedText);
                    updateStatus("Text erfolgreich eingefügt");
                }
                
                // WebView-Fenster schließen
                if (resultStage != null && resultStage.isShowing()) {
                    resultStage.hide();
                }
            }
            
        } catch (Exception e) {
            logger.severe("Fehler beim Ersetzen des Textes: " + e.getMessage());
            showAlert("Fehler", "Fehler beim Ersetzen des Textes: " + e.getMessage());
        }
    }
    
    private void updateStatus(String status) {
        if (statusLabel != null) {
        statusLabel.setText(status);
        }
        logger.info("Ollama-Status: " + status);
    }
    
    /**
     * Lädt den aktuell selektierten Text aus dem Editor neu
     */
    private void reloadSelectedText() {
        if (editorWindow != null) {
            String selectedText = editorWindow.getSelectedText();
            if (selectedText != null && !selectedText.trim().isEmpty()) {
                inputArea.setText(selectedText);
                updateStatus("Selektierter Text neu geladen: " + selectedText.length() + " Zeichen");
            } else {
                updateStatus("Kein Text im Editor selektiert");
                showAlert("Kein Text selektiert", "Bitte selektieren Sie zuerst Text im Editor.");
            }
        } else {
            updateStatus("Keine Verbindung zum Editor");
            showAlert("Fehler", "Keine Verbindung zum Editor verfügbar.");
        }
    }
    
    private void showAlert(String title, String message) {
        CustomAlert alert = new CustomAlert(Alert.AlertType.INFORMATION, title);
        alert.setContentText(message); // KORRIGIERT: message als Content setzen
        // alert.setHeaderText(null); // ENTFERNT: Setzt 'null' String
        alert.applyTheme(currentThemeIndex);
        alert.initOwner(stage);
        alert.showAndWait();
    }
    
    private void showOllamaInstallationDialog() {
        // Erstelle einen detaillierten Content
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        
        // Erklärung
        Label explanationLabel = new Label(
            "Ollama ist eine lokale KI-Plattform, die auf Ihrem Computer läuft.\n" +
            "Es ermöglicht Ihnen, KI-Modelle lokal auszuführen, ohne Daten zu übertragen."
        );
        explanationLabel.setWrapText(true);
        
        // Hardware-Anforderungen
        Label requirementsLabel = new Label(
            "Hardware-Anforderungen:\n" +
            "• Mindestens 8 GB RAM (16 GB empfohlen)\n" +
            "• Moderne CPU (Intel i5/AMD Ryzen 5 oder besser)\n" +
            "• 2 GB freier Speicherplatz\n" +
            "• Windows 10/11, macOS oder Linux"
        );
        requirementsLabel.setWrapText(true);
        
        // "Nicht mehr anzeigen" Checkbox
        CheckBox dontShowAgainCheckBox = new CheckBox("Dieses Dialog nicht mehr anzeigen");
        
        content.getChildren().addAll(explanationLabel, requirementsLabel, dontShowAgainCheckBox);
        
        CustomAlert alert = new CustomAlert(Alert.AlertType.CONFIRMATION, "Ollama Installation");
        alert.setHeaderText("Ollama ist nicht installiert");
        alert.setContentText("Möchten Sie Ollama installieren?");
        alert.applyTheme(currentThemeIndex);
        alert.initOwner(stage);
        
        // Content setzen
        alert.getDialogPane().setContent(content);
        
        // Button-Typen setzen
        ButtonType installButtonType = new ButtonType("Ollama installieren");
        ButtonType cancelButtonType = new ButtonType("Später");
        alert.setButtonTypes(installButtonType, cancelButtonType);
        
        Optional<ButtonType> result = alert.showAndWait();
        
        // Wenn "Nicht mehr anzeigen" aktiviert ist, speichere die Einstellung
        if (result.isPresent() && dontShowAgainCheckBox.isSelected()) {
            saveOllamaDialogPreference(true);
        }
        
        // Aktion basierend auf Ergebnis
        if (result.isPresent() && result.get() == installButtonType) {
            openOllamaWebsite();
        }
    }
    
    private void saveOllamaDialogPreference(boolean dontShowAgain) {
        try {
            java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userNodeForPackage(OllamaWindow.class);
            prefs.putBoolean("dont_show_ollama_dialog", dontShowAgain);
        } catch (Exception e) {
            logger.warning("Konnte Ollama-Dialog-Einstellung nicht speichern: " + e.getMessage());
        }
    }
    
    private boolean shouldShowOllamaDialog() {
        try {
            java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userNodeForPackage(OllamaWindow.class);
            return !prefs.getBoolean("dont_show_ollama_dialog", false);
        } catch (Exception e) {
            logger.warning("Konnte Ollama-Dialog-Einstellung nicht laden: " + e.getMessage());
            return true; // Standardmäßig anzeigen
        }
    }
    
    private void openOllamaWebsite() {
        try {
            // Öffne die Ollama-Website im Standard-Browser
            java.awt.Desktop.getDesktop().browse(new java.net.URI("https://ollama.com/search"));
            updateStatus("Ollama Modellbibliothek geöffnet");
        } catch (Exception e) {
            logger.warning("Konnte Ollama-Website nicht öffnen: " + e.getMessage());
            showAlert("Fehler", "Konnte Browser nicht öffnen. Bitte besuchen Sie manuell: https://ollama.com/search");
        }
    }
    
    /**
     * Öffnet den Plugin-Ordner im Datei-Explorer
     */
    private void openPluginFolder() {
        try {
            File pluginDir = new File("config/plugins");
            if (!pluginDir.exists()) {
                pluginDir.mkdirs();
            }
            java.awt.Desktop.getDesktop().open(pluginDir);
        } catch (Exception e) {
            showAlert("Fehler", "Konnte Plugin-Ordner nicht öffnen: " + e.getMessage());
        }
    }
    
    /**
     * Öffnet den Plugin Editor
     */
    private void openPluginEditor() {
        try {
            // Theme vom Editor holen
            int currentTheme = 0;
            if (editorWindow != null) {
                currentTheme = editorWindow.getCurrentThemeIndex();
                logger.info("Theme vom Editor übernommen: " + currentTheme);
            }
            
            PluginEditorWindow editor = new PluginEditorWindow(currentTheme);
            editor.show();
            updateStatus("Plugin Editor geöffnet");
        } catch (Exception e) {
            logger.severe("Fehler beim Öffnen des Plugin Editors: " + e.getMessage());
            updateStatus("Fehler beim Öffnen des Plugin Editors");
        }
    }
    

    
    /**
     * Prüft ob eine Variable eine "selektierter Text" Variable ist
     */
    private boolean isSelectedTextVariable(String variableName) {
        if (variableName == null) return false;
        String lower = variableName.toLowerCase();
        return lower.contains("selektierter text") || 
               lower.contains("selected text") || 
               lower.contains("hier den text einfügen") ||
               lower.contains("text zum analysieren");
    }
    
    /**
     * Holt den selektierten Text aus dem Editor
     */
    private String getSelectedTextFromEditor() {
        if (editorWindow != null) {
            try {
                String selectedText = editorWindow.getSelectedText();
                if (selectedText != null && !selectedText.trim().isEmpty()) {
                    return selectedText.trim();
                }
            } catch (Exception e) {
                logger.warning("Fehler beim Holen des selektierten Texts: " + e.getMessage());
            }
        }
        return null;
    }
    

    
    /**
     * Lädt alle Plugins neu und aktualisiert die Funktionsauswahl
     */
    private void reloadPlugins() {
        try {
            pluginManager.loadPlugins();
            
            // Funktionsauswahl neu laden
            String currentSelection = functionComboBox.getValue();
            functionComboBox.getItems().clear();
            
            // Funktionen laden
            List<String> functionItems = new ArrayList<>();
            functionItems.add("Dialog generieren");
            functionItems.add("Charakter entwickeln");
            functionItems.add("Schreibstil analysieren");
            functionItems.add("Chat-Assistent");
            functionItems.add("Modell-Training");
            
            // Plugins hinzufügen
            if (pluginManager != null) {
                functionItems.add(""); // Trennlinie
                functionItems.add("📦 Plugins");
                
                // Plugins nach Kategorien gruppieren
                Set<String> categories = pluginManager.getCategories();
                for (String category : categories) {
                    List<Plugin> categoryPlugins = pluginManager.getPluginsByCategory(category);
                    for (Plugin plugin : categoryPlugins) {
                        functionItems.add("📦 " + plugin.getName());
                    }
                }
            }
            
            functionComboBox.getItems().addAll(functionItems);
            
            // Vorherige Auswahl wiederherstellen falls möglich
            if (currentSelection != null) {
                functionComboBox.setValue(currentSelection);
            }
            
            updateStatus("✅ Plugins neu geladen: " + pluginManager.getActivePlugins().size() + " aktiv");
            
        } catch (Exception e) {
            showAlert("Fehler", "Fehler beim Neuladen der Plugins: " + e.getMessage());
        }
    }
    
    private void applyDialogTheme(Alert alert, int themeIndex) {
        DialogPane pane = alert.getDialogPane();
        // CSS (styles.css + editor.css) hinzufügen, damit globale Dialog-Styles greifen
        String stylesCss = ResourceManager.getCssResource("css/styles.css");
        String editorCss = ResourceManager.getCssResource("css/editor.css");
        if (stylesCss != null && !pane.getStylesheets().contains(stylesCss)) {
            pane.getStylesheets().add(stylesCss);
        }
        if (editorCss != null && !pane.getStylesheets().contains(editorCss)) {
            pane.getStylesheets().add(editorCss);
        }

        // Theme-Klassen am DialogPane setzen (wie im EditorWindow)
        if (themeIndex == 0) pane.getStyleClass().add("weiss-theme");
        else if (themeIndex == 2) pane.getStyleClass().add("pastell-theme");
        else if (themeIndex == 3) pane.getStyleClass().addAll("theme-dark", "blau-theme");
        else if (themeIndex == 4) pane.getStyleClass().addAll("theme-dark", "gruen-theme");
        else if (themeIndex == 5) pane.getStyleClass().addAll("theme-dark", "lila-theme");
        else pane.getStyleClass().add("theme-dark");

        String dialogStyle = "";
        String contentStyle = "";
        String labelStyle = "";
        String buttonStyle = "";
        String checkboxStyle = "";
        String headerBg = "";
        String headerText = "";
        
        switch (themeIndex) {
            case 0: // Weiß
                dialogStyle = "-fx-background-color: #ffffff; -fx-text-fill: #000000;";
                contentStyle = "-fx-background-color: #ffffff; -fx-text-fill: #000000;";
                labelStyle = "-fx-text-fill: #000000;";
                buttonStyle = "-fx-background-color: #f0f0f0; -fx-text-fill: #000000; -fx-border-color: #cccccc;";
                checkboxStyle = "-fx-text-fill: #000000;";
                headerBg = "#ffffff";
                headerText = "#000000";
                break;
            case 1: // Schwarz
                dialogStyle = "-fx-background-color: #1a1a1a; -fx-text-fill: #ffffff;";
                contentStyle = "-fx-background-color: #1a1a1a; -fx-text-fill: #ffffff;";
                labelStyle = "-fx-text-fill: #ffffff;";
                buttonStyle = "-fx-background-color: #2d2d2d; -fx-text-fill: #ffffff; -fx-border-color: #404040;";
                checkboxStyle = "-fx-text-fill: #ffffff;";
                headerBg = "#1a1a1a";
                headerText = "#ffffff";
                break;
            case 2: // Pastell
                dialogStyle = "-fx-background-color: #f3e5f5; -fx-text-fill: #000000;";
                contentStyle = "-fx-background-color: #f3e5f5; -fx-text-fill: #000000;";
                labelStyle = "-fx-text-fill: #000000;";
                buttonStyle = "-fx-background-color: #e1bee7; -fx-text-fill: #000000; -fx-border-color: #ba68c8;";
                checkboxStyle = "-fx-text-fill: #000000;";
                headerBg = "#f3e5f5";
                headerText = "#000000";
                break;
            case 3: // Blau
                dialogStyle = "-fx-background-color: #1e3a8a; -fx-text-fill: #ffffff;";
                contentStyle = "-fx-background-color: #1e3a8a; -fx-text-fill: #ffffff;";
                labelStyle = "-fx-text-fill: #ffffff;";
                buttonStyle = "-fx-background-color: #3b82f6; -fx-text-fill: #ffffff; -fx-border-color: #1d4ed8;";
                checkboxStyle = "-fx-text-fill: #ffffff;";
                headerBg = "#1e3a8a";
                headerText = "#ffffff";
                break;
            case 4: // Grün
                dialogStyle = "-fx-background-color: #064e3b; -fx-text-fill: #ffffff;";
                contentStyle = "-fx-background-color: #064e3b; -fx-text-fill: #ffffff;";
                labelStyle = "-fx-text-fill: #ffffff;";
                buttonStyle = "-fx-background-color: #059669; -fx-text-fill: #ffffff; -fx-border-color: #047857;";
                checkboxStyle = "-fx-text-fill: #ffffff;";
                headerBg = "#064e3b";
                headerText = "#ffffff";
                break;
            case 5: // Lila
                dialogStyle = "-fx-background-color: #581c87; -fx-text-fill: #ffffff;";
                contentStyle = "-fx-background-color: #581c87; -fx-text-fill: #ffffff;";
                labelStyle = "-fx-text-fill: #ffffff;";
                buttonStyle = "-fx-background-color: #7c3aed; -fx-text-fill: #ffffff; -fx-border-color: #6d28d9;";
                checkboxStyle = "-fx-text-fill: #ffffff;";
                headerBg = "#581c87";
                headerText = "#ffffff";
                break;
        }
        
        // Styles anwenden
        pane.setStyle(dialogStyle);
        
        // Alle Child-Elemente durchgehen und Styles anwenden
        for (javafx.scene.Node node : pane.getChildren()) {
            if (node instanceof VBox) {
                node.setStyle(contentStyle);
                // Rekursiv durch alle Child-Elemente gehen
                applyStyleToChildren(node, labelStyle, buttonStyle, checkboxStyle);
            }
        }

        // Header-Bereich gezielt einfärben, sobald der Dialog sichtbar ist
        final String headerBgFinal = headerBg;
        final String headerTextFinal = headerText;
        alert.setOnShown(ev -> {
            Node headerPanel = pane.lookup(".header-panel");
            if (headerPanel != null && headerBgFinal != null && !headerBgFinal.isEmpty()) {
                headerPanel.setStyle(String.format("-fx-background-color: %s; -fx-background-insets: 0; -fx-padding: 8 12;", headerBgFinal));
                Node headerLabel = headerPanel.lookup(".label");
                if (headerLabel instanceof Label) {
                    ((Label) headerLabel).setTextFill(javafx.scene.paint.Color.web(headerTextFinal != null && !headerTextFinal.isEmpty() ? headerTextFinal : "#ffffff"));
                }
            }
        });
    }
    
    private void applyStyleToChildren(javafx.scene.Node parent, String labelStyle, String buttonStyle, String checkboxStyle) {
        if (parent instanceof Parent) {
            for (javafx.scene.Node child : ((Parent) parent).getChildrenUnmodifiable()) {
                if (child instanceof Label) {
                    child.setStyle(labelStyle);
                } else if (child instanceof Button) {
                    child.setStyle(buttonStyle);
                } else if (child instanceof CheckBox) {
                    child.setStyle(checkboxStyle);
                } else if (child instanceof HBox || child instanceof VBox) {
                    // Rekursiv für Container
                    applyStyleToChildren(child, labelStyle, buttonStyle, checkboxStyle);
                }
            }
        }
    }
    
    public void show() {
        stage.show();
        stage.requestFocus();
    }
    
    public void hide() {
        // Flush aller Kontexte und Timer stoppen
        cancelAllDebounceTimers();
        saveAllContextsNow();
        stage.hide();
    }
    
    public boolean isShowing() {
        return stage.isShowing();
    }
    
    public void setEditorReference(EditorWindow editorWindow) {
        this.editorWindow = editorWindow;
        logger.info("Editor-Referenz gesetzt");
        
        // Theme vom Editor übernehmen
        if (editorWindow != null) {
            // Hole das aktuelle Theme vom Editor
            int currentTheme = editorWindow.getCurrentThemeIndex();
            setTheme(currentTheme);
            logger.info("Theme vom Editor übernommen: " + currentTheme);
            
            // Wenn "Text umschreiben" aktiv ist, automatisch selektierten Text laden
            if ("Text umschreiben".equals(functionComboBox.getValue())) {
                String selectedText = editorWindow.getSelectedText();
                if (selectedText != null && !selectedText.trim().isEmpty()) {
                    inputArea.setText(selectedText);
                    updateStatus("Selektierter Text geladen: " + selectedText.length() + " Zeichen");
                }
            }
        }
        
        // Lade Kontext aus der context.txt des aktuellen Romans
        loadContextFromNovel();
    }
    
    /**
     * Setzt das Theme für das Ollama-Fenster
     */
    public void setTheme(int themeIndex) {
        this.currentThemeIndex = themeIndex;
        applyTheme(themeIndex);
    }
    
    /**
     * Wendet das Theme auf alle UI-Elemente an
     */
    private void applyTheme(int themeIndex) {
        if (stage != null && stage.getScene() != null) {
            // Root zuerst vollständig setzen, damit Dropdown-Pfeile initial korrekt sind
            javafx.scene.Node root = stage.getScene().getRoot();
            root.getStyleClass().removeAll("theme-dark", "theme-light", "blau-theme", "gruen-theme", "lila-theme", "weiss-theme", "pastell-theme");
            if (themeIndex == 0) root.getStyleClass().add("weiss-theme");
            else if (themeIndex == 2) root.getStyleClass().add("pastell-theme");
            else {
                root.getStyleClass().add("theme-dark");
                if (themeIndex == 3) root.getStyleClass().add("blau-theme");
                if (themeIndex == 4) root.getStyleClass().add("gruen-theme");
                if (themeIndex == 5) root.getStyleClass().add("lila-theme");
            }
            // Stylesheets neu anhängen, damit Mark-Farben greifen
            String cssPath = ResourceManager.getCssResource("css/manuskript.css");
            if (cssPath != null && !stage.getScene().getStylesheets().contains(cssPath)) {
                stage.getScene().getStylesheets().add(cssPath);
            }
            applyThemeToNode(root, themeIndex);
            applyThemeToNode(modelComboBox, themeIndex);
            applyThemeToNode(functionComboBox, themeIndex);
            applyThemeToNode(generateButton, themeIndex);
            applyThemeToNode(insertButton, themeIndex);
            applyThemeToNode(previewPromptButton, themeIndex);
            applyThemeToNode(inputArea, themeIndex);
            applyThemeToNode(contextArea, themeIndex);
            applyThemeToNode(contextAccordion, themeIndex);
            applyThemeToNode(tpSynopsis, themeIndex);
            applyThemeToNode(tpCharacters, themeIndex);
            applyThemeToNode(tpWorldbuilding, themeIndex);
            applyThemeToNode(tpOutline, themeIndex);
            applyThemeToNode(tpChapter, themeIndex);
            applyThemeToNode(tpStyle, themeIndex);
            applyThemeToNode(synopsisArea, themeIndex);
            applyThemeToNode(charactersArea, themeIndex);
            applyThemeToNode(worldbuildingArea, themeIndex);
            applyThemeToNode(outlineArea, themeIndex);
            applyThemeToNode(chapterNotesArea, themeIndex);
            applyThemeToNode(styleNotesArea, themeIndex);
            applyThemeToNode(statusLabel, themeIndex);
            applyThemeToNode(characterField, themeIndex);
            applyThemeToNode(situationField, themeIndex);
            applyThemeToNode(emotionField, themeIndex);
            applyThemeToNode(styleField, themeIndex);
            applyThemeToNode(genreField, themeIndex);
            
            // Pfeile in dunklen Themes für wichtige Combos explizit erzwingen
            // Entfernt: Code-seitige Pfeil-/Popup-Erzwingung – zurück zu CSS

        }
    }

    // Entfernt: Code-seitiges Erzwingen der Pfeil-/Popupfarben – zurück zu CSS
    
    /**
     * Lädt die verfügbaren Modelle von Ollama
     */
    private void loadAvailableModels() {
        ollamaService.getAvailableModels().thenAccept(models -> {
            Platform.runLater(() -> {
                modelComboBox.getItems().clear();
                modelComboBox.getItems().addAll(models);
                
                // Aktualisiere auch die Lösch-ComboBox
                deleteModelComboBox.getItems().clear();
                deleteModelComboBox.getItems().addAll(models);
                
                // Lade das gespeicherte Modell oder setze das erste verfügbare als Standard
                if (models.length > 0) {
                    String savedModel = loadSelectedModel();
                    String modelToUse = savedModel;
                    
                    // Prüfe ob das gespeicherte Modell noch verfügbar ist
                    boolean modelAvailable = false;
                    for (String model : models) {
                        if (model.equals(savedModel)) {
                            modelAvailable = true;
                            break;
                        }
                    }
                    
                    // Wenn das gespeicherte Modell nicht verfügbar ist, verwende das erste
                    if (!modelAvailable) {
                        modelToUse = models[0];
                        logger.info("Gespeichertes Modell '" + savedModel + "' nicht verfügbar, verwende '" + modelToUse + "'");
                    }
                    
                    modelComboBox.setValue(modelToUse);
                    ollamaService.setModel(modelToUse);
                    updateStatus("Modelle geladen: " + models.length + " verfügbar, Modell: " + modelToUse);
                } else {
                    updateStatus("Keine Modelle gefunden");
                }
            });
        }).exceptionally(ex -> {
            Platform.runLater(() -> {
                // Fallback-Modelle
                modelComboBox.getItems().addAll("mistral:7b-instruct", "gemma3:4b");
                deleteModelComboBox.getItems().addAll("mistral:7b-instruct", "gemma3:4b");
                modelComboBox.setValue("gemma3:4b");
                updateStatus("Fehler beim Laden der Modelle - verwende Fallback");
            });
            return null;
        });
    }
    
    /**
     * Wendet das Theme auf ein einzelnes UI-Element an
     */
    private void applyThemeToNode(javafx.scene.Node node, int themeIndex) {
        if (node != null) {
            // Alle Theme-Klassen entfernen
            node.getStyleClass().removeAll("theme-dark", "theme-light", "blau-theme", "gruen-theme", "lila-theme", "weiss-theme", "pastell-theme");
            
            if (themeIndex == 0) { // Weiß-Theme
                node.getStyleClass().add("weiss-theme");
            } else if (themeIndex == 1) { // Schwarz-Theme
                node.getStyleClass().add("theme-dark");
            } else if (themeIndex == 2) { // Pastell-Theme
                node.getStyleClass().add("pastell-theme");
            } else if (themeIndex == 3) { // Blau-Theme
                node.getStyleClass().addAll("theme-dark", "blau-theme");
            } else if (themeIndex == 4) { // Grün-Theme
                node.getStyleClass().addAll("theme-dark", "gruen-theme");
            } else if (themeIndex == 5) { // Lila-Theme
                node.getStyleClass().addAll("theme-dark", "lila-theme");
            }

        }
    }

    
    /**
     * Gibt die aktuell ausgewählte Funktion zurück
     */
    public String getCurrentFunction() {
        return functionComboBox.getValue();
    }
    
    /**
     * Aktualisiert den selektierten Text im Eingabefeld
     */
    public void updateSelectedText(String selectedText) {
        if (selectedText != null && !selectedText.trim().isEmpty()) {
            inputArea.setText(selectedText);
            updateStatus("Selektierter Text automatisch geladen: " + selectedText.length() + " Zeichen");
        }
    }
    
    /**
     * Startet das Modell-Training
     */
    private void startModelTraining() {
        String modelName = trainingModelNameField.getText().trim();
        String baseModel = baseModelComboBox.getValue();
        String trainingData = trainingDataArea.getText().trim();
        String additionalInstructions = inputArea.getText().trim();
        
        if (modelName.isEmpty()) {
            showAlert("Modell-Name erforderlich", "Bitte geben Sie einen Namen für das zu trainierende Modell ein.");
            return;
        }
        
        if (trainingData.isEmpty()) {
            showAlert("Training-Daten erforderlich", "Bitte fügen Sie Training-Daten (deine Romane) ein.");
            return;
        }
        
        // UI für Training vorbereiten
        startTrainingButton.setDisable(true);
        stopTrainingButton.setDisable(false);
        trainingProgressIndicator.setVisible(true);
        trainingStatusLabel.setText("🔄 Starte Training...");
        trainingStatusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #FF8C00;");
        updateStatus("Starte Modell-Training...");
        
        // Training-Bereich visuell hervorheben
        trainingBox.setStyle("-fx-border-color: #FF8C00; -fx-border-width: 2; -fx-border-radius: 5; -fx-background-color: rgba(255, 140, 0, 0.1);");
        
        // Training über OllamaService starten
        ollamaService.startModelTraining(modelName, baseModel, trainingData, 0, additionalInstructions)
            .thenAccept(result -> {
                Platform.runLater(() -> {
                    startTrainingButton.setDisable(false);
                    stopTrainingButton.setDisable(true);
                    trainingProgressIndicator.setVisible(false);
                    trainingStatusLabel.setText("✅ Training abgeschlossen");
                    trainingStatusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #4CAF50;");
                    updateStatus("Training abgeschlossen");
                    
                    // Training-Bereich zurücksetzen
                    trainingBox.setStyle("");
                    
                    // Modell-Liste nach erfolgreichem Training aktualisieren
                    loadAvailableModels();
                });
            })
            .exceptionally(ex -> {
                Platform.runLater(() -> {
                    startTrainingButton.setDisable(false);
                    stopTrainingButton.setDisable(true);
                    trainingProgressIndicator.setVisible(false);
                    trainingStatusLabel.setText("❌ Training fehlgeschlagen");
                    trainingStatusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #F44336;");
                    updateStatus("Training fehlgeschlagen");
                    
                    // Training-Bereich zurücksetzen
                    trainingBox.setStyle("");
                });
                return null;
            });
    }
    
    /**
     * Stoppt das Modell-Training
     */
    private void stopModelTraining() {
        ollamaService.stopModelTraining()
            .thenAccept(result -> {
                Platform.runLater(() -> {
                    startTrainingButton.setDisable(false);
                    stopTrainingButton.setDisable(true);
                    trainingProgressIndicator.setVisible(false);
                    trainingStatusLabel.setText("⏹️ Training gestoppt");
                    trainingStatusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #FF9800;");
                    updateStatus("Training gestoppt");
                    
                    // Training-Bereich zurücksetzen
                    trainingBox.setStyle("");
                });
            });
    }
    
    /**
     * Löscht das ausgewählte Modell
     */
    private void deleteSelectedModel() {
        String selectedModel = deleteModelComboBox.getValue();
        
        if (selectedModel == null || selectedModel.isEmpty()) {
            showAlert("Fehler", "Bitte wählen Sie ein Modell zum Löschen aus.");
            return;
        }
        
        // Bestätigungsdialog
        CustomAlert alert = new CustomAlert(Alert.AlertType.CONFIRMATION, "Möchten Sie das Modell '" + selectedModel + "' wirklich löschen?\n\n" +
                           "⚠️  Diese Aktion kann nicht rückgängig gemacht werden!\n" +
                           "💾 Das Modell wird unwiderruflich von der Festplatte entfernt.");
        alert.setTitle("Modell löschen");
        alert.setHeaderText("Modell löschen bestätigen");
        alert.applyTheme(currentThemeIndex);
        alert.initOwner(stage);
        
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            // UI-Status setzen
            deleteModelButton.setDisable(true);
            updateStatus("Lösche Modell...");
            
                         // Modell über OllamaService löschen
             ollamaService.deleteModel(selectedModel)
                 .thenAccept(success -> {
                     Platform.runLater(() -> {
                         if (success) {
                             showAlert("Erfolg", "Modell '" + selectedModel + "' wurde erfolgreich gelöscht.");
                             updateStatus("Modell gelöscht");
                             // Modell-Listen aktualisieren
                             loadAvailableModels();
                             // ComboBox leeren
                             deleteModelComboBox.setValue(null);
                         } else {
                             showAlert("Fehler", "Fehler beim Löschen des Modells '" + selectedModel + "'.");
                             updateStatus("Modell-Löschung fehlgeschlagen");
                         }
                         deleteModelButton.setDisable(false);
                     });
                 })
                 .exceptionally(throwable -> {
                     Platform.runLater(() -> {
                         showAlert("Fehler", "Fehler beim Löschen des Modells: " + throwable.getMessage());
                         deleteModelButton.setDisable(false);
                         updateStatus("Modell-Löschung fehlgeschlagen");
                     });
                     return null;
                 });
        }
    }
    
    /**
     * Installiert das ausgewählte Modell
     */
    private void installSelectedModel() {
        String selectedModel = installModelComboBox.getValue();
        
        if (selectedModel == null || selectedModel.isEmpty()) {
            showAlert("Fehler", "Bitte wählen Sie ein Modell zum Installieren aus.");
            return;
        }
        
        // UI-Status setzen
        installModelButton.setDisable(true);
        installProgressIndicator.setVisible(true);
        installStatusLabel.setText("🔄 Installiere " + selectedModel + "...");
        installStatusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #44aa44;");
        
        // Modell über OllamaService installieren
        ollamaService.installModel(selectedModel)
            .thenAccept(result -> {
                Platform.runLater(() -> {
                    installModelButton.setDisable(false);
                    installProgressIndicator.setVisible(false);
                    
                    if (result.startsWith("✅")) {
                        installStatusLabel.setText("✅ Installation abgeschlossen");
                        installStatusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #44aa44;");
                        updateStatus("Modell installiert: " + selectedModel + " - " + result);
                        // Modell-Listen aktualisieren
                        loadAvailableModels();
                    } else {
                        installStatusLabel.setText("❌ Installation fehlgeschlagen");
                        installStatusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #ff4444;");
                        updateStatus("Modell-Installation fehlgeschlagen: " + result);
                    }
                });
            })
            .exceptionally(throwable -> {
                Platform.runLater(() -> {
                    installModelButton.setDisable(false);
                    installProgressIndicator.setVisible(false);
                    installStatusLabel.setText("❌ Installation fehlgeschlagen");
                    installStatusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #ff4444;");
                    updateStatus("Modell-Installation fehlgeschlagen: " + throwable.getMessage());
                });
                return null;
            });
    }

    // NEU: Installation anhand Freitext
    private void installModelByName() {
        String name = installModelNameField != null ? installModelNameField.getText() : null;
        if (name == null || name.trim().isEmpty()) {
            showAlert("Fehler", "Bitte geben Sie einen Modellnamen ein.");
            return;
        }
        installModelButton.setDisable(true);
        installModelByNameButton.setDisable(true);
        installProgressIndicator.setVisible(true);
        installStatusLabel.setText("🔄 Installiere " + name + "...");
        updateStatus("Installiere Modell per Name: " + name);

        ollamaService.installModel(name.trim())
            .thenAccept(result -> {
                Platform.runLater(() -> {
                    installModelButton.setDisable(false);
                    installModelByNameButton.setDisable(false);
                    installProgressIndicator.setVisible(false);
                    if (result.startsWith("✅")) {
                        installStatusLabel.setText("✅ Installation abgeschlossen");
                        updateStatus("Modell installiert: " + name);
                        loadAvailableModels();
                    } else {
                        installStatusLabel.setText("❌ Installation fehlgeschlagen");
                        showAlert("Fehler", result);
                    }
                });
            })
            .exceptionally(ex -> {
                Platform.runLater(() -> {
                    installModelButton.setDisable(false);
                    installModelByNameButton.setDisable(false);
                    installProgressIndicator.setVisible(false);
                    installStatusLabel.setText("❌ Installation fehlgeschlagen");
                    showAlert("Fehler", "Fehler bei der Installation: " + ex.getMessage());
                });
                return null;
            });
    }
    
    /**
     * Setzt den Training-Status zurück
     */
    private void resetTrainingStatus() {
        trainingProgressIndicator.setVisible(false);
        trainingStatusLabel.setText("Bereit für Training");
        trainingStatusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #666;");
        trainingBox.setStyle("");
    }
    
    /**
     * Prüft ob das angegebene Modell ein trainiertes Modell ist
     * Trainierte Modelle haben normalerweise einen benutzerdefinierten Namen
     */
    private boolean isTrainedModel(String modelName) {
        if (modelName == null || modelName.trim().isEmpty()) {
            return false;
        }
        
        // Standard-Modelle die NICHT trainiert sind
        String[] standardModels = {
            "mistral", "mistral:7b-instruct", "gemma3:4b", 
            "llama2", "llama2:7b", "llama2:13b", "llama2:70b",
            "codellama", "codellama:7b", "codellama:13b", "codellama:34b",
            "phi", "phi:2.7b", "phi:3.5", "phi:3.5:3.8b",
            "qwen2.5", "qwen2.5:7b", "qwen2.5:14b", "qwen2.5:32b"
        };
        
        // Prüfe ob es ein Standard-Modell ist
        for (String standardModel : standardModels) {
            if (modelName.equalsIgnoreCase(standardModel)) {
                return false;
            }
        }
        
        // Wenn es kein Standard-Modell ist, ist es wahrscheinlich ein trainiertes Modell
        return true;
    }

    // ==== Session-Verwaltung ====
    
    /**
     * Textfarbe für aktuelles Theme
     */
    private String getTextColorForTheme() {
        switch (currentThemeIndex) {
            case 0: return "#000000"; // Weiß
            case 2: return "#000000"; // Pastell
            case 3: return "#ffffff"; // Blau
            case 4: return "#ffffff"; // Grün
            case 5: return "#ffffff"; // Lila
            default: return "#ffffff"; // Dark
        }
    }
    
    /**
     * Erstellt eine neue Chat-Session mit Namenseingabe
     */
    private void createNewSession() {
        // TextField für Session-Name
        TextField sessionNameField = new TextField();
        sessionNameField.setPromptText("z.B. Projekt A, Charakter B, etc.");
        sessionNameField.setPrefWidth(300);
        
        // CustomAlert verwenden
        CustomAlert alert = new CustomAlert(Alert.AlertType.INFORMATION, "Neue Chat-Session");
        alert.setHeaderText("Session-Name eingeben");
        alert.setTextField(sessionNameField); // TextField setzen
        alert.setButtonTypes(ButtonType.OK, ButtonType.CANCEL);
        
        // Theme anwenden
        alert.applyTheme(currentThemeIndex);
        alert.initOwner(stage);
        
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            String sessionName = sessionNameField.getText().trim();
            if (!sessionName.isEmpty() && !sessionHistories.containsKey(sessionName)) {
                // Aktuelle Historie speichern (nur im Speicher, nicht persistent)
                sessionHistories.put(currentSessionName, chatHistoryArea.getSessionHistory());
                
                // Neue Session erstellen
                currentSessionName = sessionName;
                sessionHistories.put(sessionName, new ArrayList<>());
                
                // ComboBox aktualisieren
                sessionComboBox.getItems().add(sessionName);
                sessionComboBox.setValue(sessionName);
                
                // Chat-Historie zurücksetzen
                chatHistoryArea.clearHistory();
                
                updateStatus("Neue Session erstellt: " + sessionName);
            } else if (sessionHistories.containsKey(sessionName)) {
                showAlert("Fehler", "Eine Session mit diesem Namen existiert bereits.");
            }
        }
    }
    
    /**
     * Löscht die aktuelle Session
     */
    private void deleteCurrentSession() {
        String currentSession = sessionComboBox.getValue();
        if (currentSession != null) {
            CustomAlert alert = new CustomAlert(Alert.AlertType.CONFIRMATION, "Session löschen");
            alert.setHeaderText("Session löschen?");
            alert.setContentText("Möchten Sie die Session '" + currentSession + "' wirklich löschen?\n\nDie Chat-Historie wird unwiderruflich gelöscht.");
            alert.applyTheme(currentThemeIndex);
            alert.initOwner(stage);
            
            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                // Session löschen
                sessionHistories.remove(currentSession);
                
                // Session-Datei löschen
                ResourceManager.deleteSession(currentSession);
                
                // ComboBox aktualisieren
                sessionComboBox.getItems().remove(currentSession);
                
                // Nach Löschung: auf eine verbleibende Session wechseln (falls keine vorhanden: lege 'default' leer an)
                if (sessionComboBox.getItems().isEmpty()) {
                    sessionComboBox.getItems().add("default");
                    sessionHistories.put("default", new ArrayList<>());
                }
                String pick = sessionComboBox.getItems().get(0);
                sessionComboBox.setValue(pick);
                currentSessionName = pick;
                chatHistoryArea.loadSessionHistory(sessionHistories.getOrDefault(pick, new ArrayList<>()));
                
                updateStatus("Session gelöscht: " + currentSession);
            }
        }
    }
    
    /**
     * Löscht den Kontext der aktuellen Session
     */
    private void clearCurrentSession() {
        String currentSession = sessionComboBox.getValue();
        if (currentSession != null) {
            CustomAlert alert = new CustomAlert(Alert.AlertType.CONFIRMATION, "Kontext löschen");
            alert.setHeaderText("Chat-Historie löschen?");
            alert.setContentText("Möchten Sie die Chat-Historie der Session '" + currentSession + "' wirklich löschen?\n\nDie Session bleibt erhalten, aber alle Nachrichten werden gelöscht.");
            alert.applyTheme(currentThemeIndex);
            alert.initOwner(stage);
            
            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                // Kontext löschen
                sessionHistories.put(currentSession, new ArrayList<>());
                
                // Session persistent speichern
                ResourceManager.saveSession(currentSession, new ArrayList<>());
                
                // Chat-Historie zurücksetzen
                chatHistoryArea.clearHistory();
                
                updateStatus("Kontext gelöscht: " + currentSession);
            }
        }
    }
    
    /**
     * Wechselt zu einer anderen Session
     */
    private void switchToSession(String sessionName) {
        if (sessionName != null && !sessionName.isEmpty()) {
    
            
            // Aktuelle Historie speichern (nur im Speicher, nicht persistent)
            List<CustomChatArea.QAPair> currentHistory = chatHistoryArea.getSessionHistory();

            
            // Nur speichern wenn die Historie nicht leer ist oder es nicht die erste Session ist
            if (currentHistory.size() > 0 || !currentSessionName.equals("default")) {
                sessionHistories.put(currentSessionName, currentHistory);
            } else {

            }
            
            // Session wechseln
            currentSessionName = sessionName;
            
            // Chat-Historie aus der Session laden
            List<CustomChatArea.QAPair> newHistory = sessionHistories.getOrDefault(sessionName, new ArrayList<>());

            chatHistoryArea.loadSessionHistory(newHistory);
            // Externes Ergebnisfenster live aktualisieren (falls offen)
            if (resultStage != null && resultStage.isShowing()) {
                String currentAnswer = chatHistoryArea.getCurrentAnswer();
                if (currentAnswer == null) currentAnswer = "";
                resultWebView.getEngine().loadContent(buildHtmlForAnswer(currentAnswer), "text/html");
            }
            
            updateStatus("Session gewechselt: " + sessionName);
        }
    }
    
    /**
     * Lädt alle Sessions aus dem Config-Ordner
     */
    private void loadAllSessions() {
        // ComboBox leeren
        sessionComboBox.getItems().clear();
        
        List<String> availableSessions = ResourceManager.getAvailableSessions();

        
        for (String sessionName : availableSessions) {
            List<CustomChatArea.QAPair> sessionData = ResourceManager.loadSession(sessionName);
            sessionHistories.put(sessionName, sessionData);
            sessionComboBox.getItems().add(sessionName);

        }
        
        // Default NICHT automatisch hinzufügen, außer es gibt GAR KEINE Sessions
        if (availableSessions.isEmpty()) {
            sessionComboBox.getItems().add("default");
            sessionHistories.put("default", new ArrayList<>());

        }
        
        // Keine Auswahl hier treffen; Auswahl erfolgt in loadSelectedSession() nach Persistenz-Regel
    }
    
    /**
     * Prüft ob eine Session aufgeteilt werden muss und führt die Aufteilung durch
     */
    private void checkAndSplitSession(String sessionName) {
        List<CustomChatArea.QAPair> sessionData = sessionHistories.get(sessionName);
        if (sessionData == null) return;
        
        // Session aufteilen wenn mehr als der konfigurierte Wert QAPairs vorhanden sind
        final int MAX_QAPAIRS_PER_SESSION = ResourceManager.getIntParameter("session.max_qapairs_per_session", 20);
        
        if (sessionData.size() > MAX_QAPAIRS_PER_SESSION) {

            
            // Neue Session-Namen generieren
            String baseName = sessionName;
            int partNumber = 1;
            
            // Finde den nächsten freien Teil-Namen
            while (sessionHistories.containsKey(baseName + "." + partNumber)) {
                partNumber++;
            }
            
            String newSessionName = baseName + "." + partNumber;
            
            // Erste 20 QAPairs in der ursprünglichen Session behalten
            List<CustomChatArea.QAPair> remainingData = new ArrayList<>(sessionData.subList(0, MAX_QAPAIRS_PER_SESSION));
            sessionHistories.put(sessionName, remainingData);
            
            // Rest in neue Session verschieben
            List<CustomChatArea.QAPair> newSessionData = new ArrayList<>(sessionData.subList(MAX_QAPAIRS_PER_SESSION, sessionData.size()));
            sessionHistories.put(newSessionName, newSessionData);
            
            // ComboBox aktualisieren
            if (!sessionComboBox.getItems().contains(newSessionName)) {
                sessionComboBox.getItems().add(newSessionName);
            }
            
            // Sessions speichern
            ResourceManager.saveSession(sessionName, remainingData);
            ResourceManager.saveSession(newSessionName, newSessionData);
            

            
            // Nach dem Split automatisch in die neue Session wechseln,
            // damit nicht bei jeder neuen Anfrage erneut ein weiterer Teil entsteht
            Platform.runLater(() -> {
                // Session-Auswahl aktualisieren und wechseln
                if (!sessionComboBox.getItems().contains(newSessionName)) {
                    sessionComboBox.getItems().add(newSessionName);
                }
                sessionComboBox.setValue(newSessionName);
                currentSessionName = newSessionName;
                // Chat-Historie der neuen Session laden
                chatHistoryArea.loadSessionHistory(newSessionData);
                updateStatus("Session wurde aufgeteilt – weiter in '" + newSessionName + "'");
            });
        }
    }
    
    /**
     * Speichert alle Sessions in den Config-Ordner
     */
    private void saveAllSessions() {

        for (Map.Entry<String, List<CustomChatArea.QAPair>> entry : sessionHistories.entrySet()) {
            String sessionName = entry.getKey();
            List<CustomChatArea.QAPair> qaPairs = entry.getValue();
                ResourceManager.saveSession(sessionName, qaPairs);
        }
    }
    
    /**
     * Ermittelt das Verzeichnis, aus dem die DOCX-Dateien geladen wurden
     */
    private String getNovelDirectory() {
        // Verwende den Pfad aus der gespeicherten Dateiauswahl
        String savedSelectionPath = ResourceManager.getParameter("ui.last_docx_directory", "");
        
        if (!savedSelectionPath.isEmpty()) {
            File directory = new File(savedSelectionPath);
            if (directory.exists() && directory.isDirectory()) {
                return savedSelectionPath;
            }
        }
        return null;
    }
    
    /**
     * Ermittelt den aktuellen DOCX-Dateinamen basierend auf dem Editor
     */
    private String getCurrentDocxFileName() {
        try {
            // 1) Primär: vom Editor die originale DOCX holen
            if (editorWindow != null) {
                File original = editorWindow.getOriginalDocxFile();
                if (original != null && original.exists()) {
                    return original.getAbsolutePath();
                }
            }

            // 2) Sekundär: verwende das gespeicherte DOCX-Verzeichnis und nimm die erste .docx
                String savedSelectionPath = ResourceManager.getParameter("ui.last_docx_directory", "");
                if (!savedSelectionPath.isEmpty()) {
                    File directory = new File(savedSelectionPath);
                    if (directory.exists() && directory.isDirectory()) {
                    File[] files = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".docx"));
                        if (files != null && files.length > 0) {
                            return files[0].getAbsolutePath();
                        }
                    }
                }
        } catch (Exception ignored) {}
        return null;
    }
    
    /**
     * Lädt den Kontext aus der context.txt des DOCX-Verzeichnisses
     */
    private void loadContextFromNovel() {
        String docxDirectory = getNovelDirectory();
        
        if (docxDirectory != null) {
            File contextFile = new File(docxDirectory, "context.txt");
            
            if (contextFile.exists()) {
                try {
                    String context = new String(java.nio.file.Files.readAllBytes(contextFile.toPath()), "UTF-8");
                    
                    if (!context.trim().isEmpty()) {
                        contextArea.setText(context);
                        logger.info("Kontext aus context.txt geladen für: " + contextFile.getAbsolutePath());
                    } else {
                        logger.info("context.txt ist leer: " + contextFile.getAbsolutePath());
                    }
                } catch (Exception e) {
                    logger.warning("Fehler beim Laden der context.txt: " + e.getMessage());
                }
            } else {
                logger.info("Keine context.txt gefunden in: " + docxDirectory);
            }
        } else {
            logger.info("Kein DOCX-Verzeichnis gefunden");
        }
    }
    
    /**
     * Speichert den aktuellen Context in die context.txt Datei
     */
    private void saveContextToFile() {
        String docxDirectory = getNovelDirectory();
        if (docxDirectory != null && contextArea != null) {
            String context = contextArea.getText();
            
            try {
                File contextFile = new File(docxDirectory, "context.txt");
                java.nio.file.Files.write(contextFile.toPath(), context.getBytes("UTF-8"));
                logger.info("Context gespeichert für: " + contextFile.getAbsolutePath());
            } catch (Exception e) {
                logger.warning("Fehler beim Speichern des Contexts: " + e.getMessage());
            }
        }
    }
    
    /**
     * Lädt die zuletzt ausgewählte Session aus den Preferences
     */
    private void loadSelectedSession() {
        String savedSession = ResourceManager.getParameter("ui.selected_session", "");
        String pick = null;
        if (savedSession != null && !savedSession.isEmpty() && sessionComboBox.getItems().contains(savedSession)) {
            pick = savedSession;
            logger.info("Gespeicherte Session geladen: " + savedSession);
        } else {
            // Fallback: höchste default.X, sonst 'default'

            int bestPart = -1;
            for (String name : sessionComboBox.getItems()) {
                if ("default".equals(name)) continue;
                if (name.startsWith("default.")) {
                    try {
                        int part = Integer.parseInt(name.substring("default.".length()));
                        if (part > bestPart) { bestPart = part; pick = name; }
                    } catch (NumberFormatException ignored) {}
                }
            }
            if (pick == null) pick = sessionComboBox.getItems().contains("default") ? "default" : sessionComboBox.getItems().get(0);
            logger.info("Fallback Session gewählt: " + pick + " (saved='" + savedSession + "')");
        }
        sessionComboBox.setValue(pick);
        currentSessionName = pick;
        List<CustomChatArea.QAPair> hist = sessionHistories.getOrDefault(pick, new ArrayList<>());
        chatHistoryArea.loadSessionHistory(hist);
    }
    
    /**
     * Speichert das ausgewählte Modell in den Preferences
     */
    private void saveSelectedModel(String modelName) {
        if (modelName != null && !modelName.trim().isEmpty()) {
            try {
                preferences.put("selected_model", modelName);
                preferences.flush();
                logger.info("Modell gespeichert: " + modelName);
            } catch (Exception e) {
                logger.warning("Fehler beim Speichern des Modells: " + e.getMessage());
            }
        }
    }
    
    /**
     * Lädt das gespeicherte Modell aus den Preferences
     */
    private String loadSelectedModel() {
        String savedModel = preferences.get("selected_model", "");
        if (savedModel != null && !savedModel.trim().isEmpty()) {
            logger.info("Gespeichertes Modell geladen: " + savedModel);
            return savedModel;
        }
        // Fallback auf Standard-Modell
        String defaultModel = "gemma3:4b";
        logger.info("Kein gespeichertes Modell gefunden, verwende Standard: " + defaultModel);
        return defaultModel;
    }
    
    /**
     * Behandelt die Plot-Holes Detection Funktion
     */
    private void handlePlotHolesDetection() {
        try {
            // Lade alle Kapitel aus dem MainController
            String allChapters = loadAllChapters();
            if (allChapters == null || allChapters.trim().isEmpty()) {
                showAlert("Keine Kapitel", "Bitte wählen Sie zuerst Kapitel aus dem Hauptfenster aus.");
                setGenerating(false);
                return;
            }
            
            // Zeige nur eine kurze Info im Input-Bereich
            // Extrahiere die Kapitel-Anzahl aus dem Text
            int chapterCount = 0;
            if (allChapters.contains("__CHAPTER_COUNT__:")) {
                String countPart = allChapters.substring(allChapters.indexOf("__CHAPTER_COUNT__:") + 18);
                try {
                    chapterCount = Integer.parseInt(countPart.trim());
                } catch (NumberFormatException e) {
                    chapterCount = 0;
                }
            }
            inputArea.setText("Analysiere " + chapterCount + " Kapitel auf Plot-Löcher und Inkonsistenzen...");
            
            // Erstelle speziellen Prompt für Plot-Holes Detection
            // Begrenze Text VOR der Prompt-Erstellung
            String limitedChapters = allChapters;
            if (allChapters.length() > 50000) {
                limitedChapters = allChapters.substring(0, 50000) + "\n\n[Text wurde auf 50.000 Zeichen begrenzt um Memory-Problem zu vermeiden]";
            }
            
            String prompt = buildPlotHolesPrompt(limitedChapters);
            
            // Debug-Informationen
            
            // Debug: Zeige den ersten Teil des Prompts
            String promptStart = prompt.length() > 500 ? prompt.substring(0, 500) + "..." : prompt;
            
            // Zeige Text-Länge im Status
            updateStatus("Lade " + chapterCount + " Kapitel (" + allChapters.length() + " Zeichen) für Plot-Holes Analyse...");
            
            // Streaming für Plot-Holes Detection - verwende Chat API für bessere Anweisungsbefolgung
            StringBuilder aggregated = new StringBuilder();
            List<OllamaService.ChatMessage> messages = Arrays.asList(
                new OllamaService.ChatMessage("user", prompt)
            );
            currentStreamHandle = ollamaService.chatStreaming(messages, null,
                chunk -> Platform.runLater(() -> {
                    aggregated.append(chunk);
                    statusLabel.setText("⏳ Analysiere Plot-Holes... " + aggregated.length() + " Zeichen");
                    resultArea.setText(aggregated.toString());
                    if (resultStage != null && resultStage.isShowing() && resultWebView != null) {
                        updateResultWebView(buildHtmlForAnswer(aggregated.toString()), true);
                    }
                }),
                () -> Platform.runLater(() -> {
                    insertButton.setDisable(false);
                    setGenerating(false);
                    updateStatus("✅ Plot-Holes Analyse abgeschlossen: " + aggregated.length() + " Zeichen");
                    resultArea.setText(aggregated.toString());
                }),
                (Throwable err) -> Platform.runLater(() -> {
                    setGenerating(false);
                    updateStatus("Fehler bei Plot-Holes Analyse: " + err.getMessage());
                })
            );
            
        } catch (Exception e) {
            logger.severe("Fehler bei Plot-Holes Detection: " + e.getMessage());
            setGenerating(false);
            updateStatus("Fehler: " + e.getMessage());
        }
    }
    
    /**
     * Lädt alle Kapitel aus dem aktuellen Verzeichnis
     */
    private String loadAllChapters() {
        try {
            // Hole das DOCX-Verzeichnis direkt aus den gespeicherten Einstellungen
            String savedSelectionPath = ResourceManager.getParameter("ui.last_docx_directory", "");
            if (!savedSelectionPath.isEmpty()) {
                File directory = new File(savedSelectionPath);
                if (directory.exists() && directory.isDirectory()) {
                    // Lade alle DOCX-Dateien im Verzeichnis
                    File[] docxFiles = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".docx"));
                    if (docxFiles != null && docxFiles.length > 0) {
                        StringBuilder allText = new StringBuilder();
                        logger.info("Lade " + docxFiles.length + " DOCX-Dateien für Plot-Holes Detection");
                        
                        for (File docxFile : docxFiles) {
                            // Lade den Markdown-Inhalt der entsprechenden MD-Datei
                            File mdFile = deriveMdFileFor(docxFile);
                            if (mdFile != null && mdFile.exists()) {
                                String content = java.nio.file.Files.readString(mdFile.toPath(), java.nio.charset.StandardCharsets.UTF_8);
                                allText.append("=== ").append(docxFile.getName()).append(" ===\n");
                                allText.append(content).append("\n\n");
                                logger.info("Kapitel geladen: " + docxFile.getName() + " (" + content.length() + " Zeichen)");
                            } else {
                                logger.warning("Keine MD-Datei gefunden für: " + docxFile.getName());
                            }
                        }
                        // Speichere die Anzahl der geladenen Kapitel für die Anzeige
                        allText.append("__CHAPTER_COUNT__:").append(docxFiles.length);
                        return allText.toString();
                    } else {
                        logger.warning("Keine DOCX-Dateien im Verzeichnis gefunden: " + directory.getAbsolutePath());
                    }
                } else {
                    logger.warning("Verzeichnis existiert nicht: " + savedSelectionPath);
                }
            } else {
                logger.warning("Kein gespeichertes DOCX-Verzeichnis gefunden");
            }
        } catch (Exception e) {
            logger.severe("Fehler beim Laden der Kapitel: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Leitet eine DOCX-Datei zur entsprechenden MD-Datei ab
     */
    private File deriveMdFileFor(File docxFile) {
        String baseName = docxFile.getName().replaceAll("\\.docx$", "");
        return new File(docxFile.getParent(), baseName + ".md");
    }
    
    /**
     * Erstellt den speziellen Prompt für Plot-Holes Detection
     */
    private String buildPlotHolesPrompt(String allChapters) {
        // Entferne die Kapitel-Anzahl aus dem Text für die KI
        String cleanChapters = allChapters;
        if (allChapters.contains("__CHAPTER_COUNT__:")) {
            cleanChapters = allChapters.substring(0, allChapters.indexOf("__CHAPTER_COUNT__:"));
        }
        
        // Text wurde bereits begrenzt, keine weitere Begrenzung nötig
        
        // Einfacher, direkter Prompt
        String timestamp = java.time.LocalDateTime.now().toString();
        
        return "ANALYSE-AUFTRAG " + timestamp + " ID:" + System.currentTimeMillis() + "\n\n" +
               "Du bist ein erfahrener Lektor und Literaturkritiker. Analysiere diesen Roman gründlich und ausführlich.\n\n" +
               "WICHTIG: Antworte ausschließlich auf Deutsch!\n\n" +
               "1. ZUERST: Erstelle eine detaillierte Zusammenfassung mit:\n" +
               "   - Vollständiger Titel des Romans\n" +
               "   - Alle Hauptcharaktere mit Namen und kurzer Beschreibung\n" +
               "   - Detaillierte Handlung Kapitel für Kapitel (mindestens 2-3 Sätze pro Kapitel)\n" +
               "   - Vollständiges Ende der Geschichte\n\n" +
               "2. DANN: Finde konkrete Plot-Löcher und Inkonsistenzen:\n" +
               "   - Zeitliche Widersprüche (z.B. Charakter ist an zwei Orten gleichzeitig)\n" +
               "   - Charakter-Inkonsistenzen (Verhalten passt nicht zur Persönlichkeit)\n" +
               "   - Logikfehler (Handlungen sind unmöglich oder widersprüchlich)\n" +
               "   - Lose Enden (Handlungsstränge werden nicht abgeschlossen)\n" +
               "   - Weltbau-Inkonsistenzen (Regeln der Welt werden gebrochen)\n\n" +
               "3. ZUSÄTZLICH: Bewerte auch:\n" +
               "   - Charakterentwicklung (sind die Charaktere glaubwürdig?)\n" +
               "   - Spannungsaufbau (funktioniert die Dramaturgie?)\n" +
               "   - Dialoge (sind sie natürlich und charakteristisch?)\n\n" +
               "Nenne bei jedem gefundenen Problem:\n" +
               "- Spezifische Stellen (Kapitel, Szene)\n" +
               "- Betroffene Charakternamen\n" +
               "- Konkrete Beschreibung des Problems\n" +
               "- Mögliche Lösungsvorschläge\n\n" +
               "Sei gründlich und detailliert in deiner Analyse!\n\n" +
               "ROMAN:\n" + cleanChapters;
    }
    
    /**
     * Speichert die ausgewählte Funktion in den Preferences
     */
    private void saveSelectedFunction(String functionName) {
        if (functionName != null && !functionName.trim().isEmpty()) {
            try {
                preferences.put("selected_function", functionName);
                preferences.flush();
                logger.info("Funktion gespeichert: " + functionName);
            } catch (Exception e) {
                logger.warning("Fehler beim Speichern der Funktion: " + e.getMessage());
            }
        }
    }
    
    /**
     * Lädt die gespeicherte Funktion aus den Preferences
     */
    private String loadSelectedFunction() {
        String savedFunction = preferences.get("selected_function", "");
        if (savedFunction != null && !savedFunction.trim().isEmpty()) {
            logger.info("Gespeicherte Funktion geladen: " + savedFunction);
            return savedFunction;
        }
        // Fallback auf Standard-Funktion
        String defaultFunction = "Chat-Assistent";
        logger.info("Keine gespeicherte Funktion gefunden, verwende Standard: " + defaultFunction);
        return defaultFunction;
    }
} 