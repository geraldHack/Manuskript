package com.manuskript;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.ArrayList;
import java.util.List;

public class CustomChatArea extends VBox {
    private TextArea chatHistoryArea;
    private Label questionLabel;
    private Button upButton;
    private Button downButton;
    private VBox scrollIndicator;
    private List<QAPair> chatHistory = new ArrayList<>();
    private int currentIndex = -1;
    
    public CustomChatArea() {
        initializeUI();
    }
    
    private void initializeUI() {
        // Frage-Label (oben, farblich hervorgehoben)
        questionLabel = new Label("Keine Frage ausgewählt");
        questionLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        questionLabel.setWrapText(true);
        questionLabel.setMaxWidth(Double.MAX_VALUE);
        
        // TextArea für Antworten
        chatHistoryArea = new TextArea();
        chatHistoryArea.setEditable(false);
        chatHistoryArea.setWrapText(true);
        chatHistoryArea.setPrefRowCount(15);
        chatHistoryArea.setPromptText("Antwort wird hier angezeigt...");
        chatHistoryArea.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 12px;");
        
        // Keyboard-Navigation für TextArea
        chatHistoryArea.setOnKeyPressed(e -> {
            if (e.isControlDown()) {
                switch (e.getCode()) {
                    case UP:
                        showPrevious();
                        e.consume();
                        break;
                    case DOWN:
                        showNext();
                        e.consume();
                        break;
                }
            }
        });
        
        // Navigation-Buttons
        upButton = new Button("↑");
        upButton.setPrefWidth(40);
        upButton.setPrefHeight(40);
        upButton.setDisable(true);
        upButton.setOnAction(e -> showPrevious());
        
        downButton = new Button("↓");
        downButton.setPrefWidth(40);
        downButton.setPrefHeight(40);
        downButton.setDisable(true);
        downButton.setOnAction(e -> showNext());
        
        // Keyboard-Hinweis
        Label keyboardHint = new Label("Ctrl+↑/↓");
        keyboardHint.setFont(Font.font("System", 10));
        keyboardHint.setStyle("-fx-text-fill: #7f8c8d; -fx-font-style: italic;");
        
        // VBox als Scroll-Indikator
        scrollIndicator = new VBox();
        scrollIndicator.setPrefWidth(20);
        updateScrollIndicator();
        
        // Navigation-Container
        HBox navigationBox = new HBox(5);
        navigationBox.getChildren().addAll(keyboardHint, upButton, downButton, scrollIndicator);
        navigationBox.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        
        // Haupt-Layout
        this.setSpacing(10);
        this.setPadding(new Insets(10));
        this.getChildren().addAll(questionLabel, chatHistoryArea, navigationBox);
        
        VBox.setVgrow(chatHistoryArea, javafx.scene.layout.Priority.ALWAYS);
        
        // Theme anwenden
        applyTheme();
    }
    
    public void clearAndShowNewQuestion(String question) {
        Platform.runLater(() -> {
            // Neue Frage zum Array hinzufügen
            QAPair newPair = new QAPair(question, "");
            chatHistory.add(newPair);
            currentIndex = chatHistory.size() - 1;
            
            // UI aktualisieren
            updateDisplay();
        });
    }
    
    /**
     * Lädt die Chat-Historie aus einem Array von QAPairs
     */
    public void loadSessionHistory(List<QAPair> sessionHistory) {
        Platform.runLater(() -> {
            chatHistory.clear();
            chatHistory.addAll(sessionHistory);
            
            // Zum letzten Eintrag springen
            currentIndex = chatHistory.size() - 1;
            updateDisplay();
        });
    }
    
    /**
     * Gibt die aktuelle Chat-Historie zurück
     */
    public List<QAPair> getSessionHistory() {
        return new ArrayList<>(chatHistory);
    }
    
    /**
     * Leert die Chat-Historie
     */
    public void clearHistory() {
        Platform.runLater(() -> {
            chatHistory.clear();
            currentIndex = -1;
            updateDisplay();
        });
    }
    
    public void addAssistantResponse(String response) {
        Platform.runLater(() -> {
            if (currentIndex >= 0 && currentIndex < chatHistory.size()) {
                // Antwort zum aktuellen QAPair hinzufügen
                chatHistory.get(currentIndex).setAnswer(response);
                
                // UI aktualisieren
                updateDisplay();
            }
        });
    }
    
    private void showPrevious() {
        if (currentIndex > 0) {
            currentIndex--;
            updateDisplay();
        }
    }
    
    private void showNext() {
        if (currentIndex < chatHistory.size() - 1) {
            currentIndex++;
            updateDisplay();
        }
    }
    
    private void updateDisplay() {
        if (currentIndex >= 0 && currentIndex < chatHistory.size()) {
            QAPair currentPair = chatHistory.get(currentIndex);
            
            // Frage anzeigen
            questionLabel.setText("Frage: " + currentPair.getQuestion());
            
            // Antwort anzeigen
            chatHistoryArea.setText(currentPair.getAnswer());
            
            // Navigation-Buttons aktualisieren
            upButton.setDisable(currentIndex <= 0);
            downButton.setDisable(currentIndex >= chatHistory.size() - 1);
            
            // Scroll-Indikator aktualisieren
            updateScrollIndicator();
        }
    }
    
    private void updateScrollIndicator() {
        scrollIndicator.getChildren().clear();
        
        if (chatHistory.isEmpty()) {
            return;
        }
        
        int totalPairs = chatHistory.size();
        int currentPos = currentIndex;
        
        // Erstelle kleine Rechtecke für jeden Eintrag
        for (int i = 0; i < totalPairs; i++) {
            Region indicator = new Region();
            indicator.setPrefHeight(8);
            indicator.setPrefWidth(16);
            
            if (i == currentPos) {
                // Aktueller Eintrag - hervorgehoben
                indicator.setStyle("-fx-background-color: #3498db; -fx-border-color: #2980b9; -fx-border-width: 1px;");
            } else {
                // Andere Einträge - normal
                indicator.setStyle("-fx-background-color: #ecf0f1; -fx-border-color: #bdc3c7; -fx-border-width: 1px;");
            }
            
            scrollIndicator.getChildren().add(indicator);
        }
        
        // Theme für Scroll-Indikator anwenden
        applyTheme();
    }
    
    private void applyTheme() {
        // Theme aus den Preferences lesen
        int themeIndex = 4; // Standard: Grün
        try {
            themeIndex = Integer.parseInt(java.util.prefs.Preferences.userNodeForPackage(com.manuskript.EditorWindow.class).get("editor_theme", "4"));
        } catch (Exception e) {
            // Fallback auf Standard-Theme
        }
        
        String backgroundColor, textColor, borderColor, highlightColor;
        
        switch (themeIndex) {
            case 0: // Hell
                backgroundColor = "#ffffff";
                textColor = "#000000";
                borderColor = "#cccccc";
                highlightColor = "#e3f2fd";
                break;
            case 1: // Dunkel
                backgroundColor = "#2c3e50";
                textColor = "#ecf0f1";
                borderColor = "#34495e";
                highlightColor = "#3498db";
                break;
            case 2: // Blau
                backgroundColor = "#1e3a8a";
                textColor = "#ffffff";
                borderColor = "#3b82f6";
                highlightColor = "#60a5fa";
                break;
            case 3: // Rot
                backgroundColor = "#7f1d1d";
                textColor = "#ffffff";
                borderColor = "#dc2626";
                highlightColor = "#f87171";
                break;
            case 4: // Grün
            default:
                backgroundColor = "#064e3b";
                textColor = "#ffffff";
                borderColor = "#059669";
                highlightColor = "#10b981";
                break;
        }
        
        // Frage-Label Theme
        questionLabel.setStyle(String.format(
            "-fx-text-fill: %s; -fx-background-color: %s; -fx-padding: 10px; -fx-border-color: %s; -fx-border-width: 1px; -fx-border-radius: 4px; -fx-background-radius: 4px;",
            textColor, backgroundColor, borderColor
        ));
        
        // TextArea Theme
        chatHistoryArea.setStyle(String.format(
            "-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 12px; -fx-text-fill: %s; -fx-background-color: %s; -fx-control-inner-background: %s; -fx-border-color: %s;",
            textColor, backgroundColor, backgroundColor, borderColor
        ));
        
        // Buttons Theme
        upButton.setStyle(String.format(
            "-fx-background-color: %s; -fx-text-fill: %s; -fx-border-color: %s; -fx-border-width: 1px; -fx-border-radius: 3px; -fx-background-radius: 3px;",
            backgroundColor, textColor, borderColor
        ));
        
        downButton.setStyle(String.format(
            "-fx-background-color: %s; -fx-text-fill: %s; -fx-border-color: %s; -fx-border-width: 1px; -fx-border-radius: 3px; -fx-background-radius: 3px;",
            backgroundColor, textColor, borderColor
        ));
        
        // Scroll-Indikator Theme
        scrollIndicator.setStyle(String.format(
            "-fx-background-color: %s; -fx-border-color: %s; -fx-border-width: 1px;",
            backgroundColor, borderColor
        ));
        
        // Scroll-Indikator-Elemente aktualisieren
        for (javafx.scene.Node node : scrollIndicator.getChildren()) {
            if (node instanceof Region) {
                Region indicator = (Region) node;
                if (scrollIndicator.getChildren().indexOf(node) == currentIndex) {
                    // Aktueller Eintrag - hervorgehoben
                    indicator.setStyle(String.format(
                        "-fx-background-color: %s; -fx-border-color: %s; -fx-border-width: 1px;",
                        highlightColor, borderColor
                    ));
                } else {
                    // Andere Einträge - normal
                    indicator.setStyle(String.format(
                        "-fx-background-color: %s; -fx-border-color: %s; -fx-border-width: 1px;",
                        backgroundColor, borderColor
                    ));
                }
            }
        }
    }
    
    public void insertSavedHistoryAfterResponse() {
        // Diese Methode wird nicht mehr benötigt
    }
    
    public String getCurrentText() {
        if (currentIndex >= 0 && currentIndex < chatHistory.size()) {
            QAPair currentPair = chatHistory.get(currentIndex);
            return "Frage: " + currentPair.getQuestion() + "\n\nAntwort: " + currentPair.getAnswer();
        }
        return "";
    }
    
    // Innere Klasse für Frage-Antwort-Paare
    public static class QAPair {
        private String question;
        private String answer;
        
        public QAPair(String question, String answer) {
            this.question = question;
            this.answer = answer;
        }
        
        public String getQuestion() {
            return question;
        }
        
        public String getAnswer() {
            return answer;
        }
        
        public void setAnswer(String answer) {
            this.answer = answer;
        }
    }
} 