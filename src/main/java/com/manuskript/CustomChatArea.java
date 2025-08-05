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
    private TextArea questionArea;  // Geändert von Label zu TextArea
    private Button upButton;
    private Button downButton;
    private VBox scrollIndicator;
    private List<QAPair> chatHistory = new ArrayList<>();
    private int currentIndex = -1;
    
    public CustomChatArea() {
        initializeUI();
    }
    
    private void initializeUI() {
        // Frage-TextArea (oben, farblich hervorgehoben)
        questionArea = new TextArea("Keine Frage ausgewählt");
        questionArea.setEditable(false);
        questionArea.setWrapText(true);
        questionArea.setPrefRowCount(2);  // Nur 2 Zeilen für die Frage
        questionArea.setMaxHeight(60);    // Maximale Höhe begrenzen
        questionArea.setStyle("-fx-font-family: 'System'; -fx-font-size: 11px; -fx-font-weight: bold; -fx-background-color: transparent; -fx-border-color: #bdc3c7; -fx-border-width: 1px;");
        
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
        
        // Navigation-Buttons (kleiner gemacht)
        upButton = new Button("↑");
        upButton.setPrefWidth(25);
        upButton.setPrefHeight(25);
        upButton.setDisable(true);
        upButton.setOnAction(e -> showPrevious());
        
        downButton = new Button("↓");
        downButton.setPrefWidth(25);
        downButton.setPrefHeight(25);
        downButton.setDisable(true);
        downButton.setOnAction(e -> showNext());
        
        // Keyboard-Hinweis (kleiner)
        Label keyboardHint = new Label("Ctrl+↑/↓");
        keyboardHint.setFont(Font.font("System", 8));
        keyboardHint.setStyle("-fx-text-fill: #7f8c8d; -fx-font-style: italic;");
        
        // VBox als Scroll-Indikator (schmaler)
        scrollIndicator = new VBox();
        scrollIndicator.setPrefWidth(12);
        updateScrollIndicator();
        
        // Navigation-Container (rechts neben der Antwort-TextArea)
        VBox navigationBox = new VBox(5);
        navigationBox.setAlignment(javafx.geometry.Pos.CENTER);
        navigationBox.getChildren().addAll(keyboardHint, upButton, downButton, scrollIndicator);
        
        // Antwort-Bereich mit Navigation (HBox für nebeneinander)
        HBox answerSection = new HBox(10);
        answerSection.getChildren().addAll(chatHistoryArea, navigationBox);
        VBox.setVgrow(chatHistoryArea, javafx.scene.layout.Priority.ALWAYS);
        HBox.setHgrow(chatHistoryArea, javafx.scene.layout.Priority.ALWAYS);
        
        // Haupt-Layout
        this.setSpacing(10);
        this.setPadding(new Insets(10));
        this.getChildren().addAll(questionArea, answerSection);
        VBox.setVgrow(answerSection, javafx.scene.layout.Priority.ALWAYS);
        
        // Theme anwenden
        applyTheme();
    }
    
    public void clearAndShowNewQuestion(String question) {
        Platform.runLater(() -> {
            // Prüfen ob die Frage bereits existiert und Nummer hinzufügen
            String numberedQuestion = addNumberIfDuplicate(question);
            
            // Neue Frage zum Array hinzufügen
            QAPair newPair = new QAPair(numberedQuestion, "");
            chatHistory.add(newPair);
            currentIndex = chatHistory.size() - 1;
            
            // UI aktualisieren
            updateDisplay();
        });
    }
    
    /**
     * Prüft ob eine Frage bereits existiert und fügt eine Nummer hinzu falls nötig
     */
    private String addNumberIfDuplicate(String question) {
        // Entferne eventuell vorhandene Nummer am Ende (z.B. "Frage (2)")
        String baseQuestion = question.replaceAll("\\s*\\(\\d+\\)\\s*$", "").trim();
        
        int counter = 1;
        String numberedQuestion = baseQuestion;
        
        // Prüfe ob die Frage bereits existiert
        for (QAPair qaPair : chatHistory) {
            String existingQuestion = qaPair.getQuestion().replaceAll("\\s*\\(\\d+\\)\\s*$", "").trim();
            if (existingQuestion.equals(baseQuestion)) {
                counter++;
            }
        }
        
        // Füge Nummer hinzu falls mehr als einmal vorhanden
        if (counter > 1) {
            numberedQuestion = baseQuestion + " (" + counter + ")";
        }
        
        return numberedQuestion;
    }
    
    /**
     * Lädt die Chat-Historie aus einem Array von QAPairs
     */
    public void loadSessionHistory(List<QAPair> sessionHistory) {
        System.out.println("DEBUG: loadSessionHistory() aufgerufen mit " + sessionHistory.size() + " QAPairs");
        Platform.runLater(() -> {
            chatHistory.clear();
            chatHistory.addAll(sessionHistory);
            
            System.out.println("DEBUG: loadSessionHistory() - chatHistory.size(): " + chatHistory.size());
            
            // Zum letzten Eintrag springen
            currentIndex = chatHistory.size() - 1;
            updateDisplay();
        });
    }
    
    /**
     * Gibt die aktuelle Chat-Historie zurück
     */
    public List<QAPair> getSessionHistory() {
        System.out.println("DEBUG: getSessionHistory() - chatHistory.size(): " + chatHistory.size());
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
            System.out.println("DEBUG: addAssistantResponse() aufgerufen mit: " + response.substring(0, Math.min(50, response.length())) + "...");
            System.out.println("DEBUG: currentIndex: " + currentIndex + ", chatHistory.size(): " + chatHistory.size());
            
            if (currentIndex >= 0 && currentIndex < chatHistory.size()) {
                // Antwort zum aktuellen QAPair hinzufügen
                QAPair currentPair = chatHistory.get(currentIndex);
                System.out.println("DEBUG: Vor setAnswer - Frage: " + currentPair.getQuestion() + ", Antwort: " + (currentPair.getAnswer() != null ? currentPair.getAnswer().length() + " Zeichen" : "null"));
                
                currentPair.setAnswer(response);
                
                System.out.println("DEBUG: Nach setAnswer - Frage: " + currentPair.getQuestion() + ", Antwort: " + (currentPair.getAnswer() != null ? currentPair.getAnswer().length() + " Zeichen" : "null"));
                
                // UI aktualisieren
                updateDisplay();
            } else {
                System.out.println("DEBUG: Ungültiger Index - currentIndex: " + currentIndex + ", chatHistory.size(): " + chatHistory.size());
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
            questionArea.setText("Frage: " + currentPair.getQuestion());
            
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
        
        // Erstelle kleine Rechtecke für jeden Eintrag (kleiner gemacht)
        for (int i = 0; i < totalPairs; i++) {
            Region indicator = new Region();
            indicator.setPrefHeight(6);
            indicator.setPrefWidth(10);
            
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
        
        // Frage-TextArea Theme
        questionArea.setStyle(String.format(
            "-fx-font-family: 'System'; -fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: %s; -fx-background-color: %s; -fx-control-inner-background: %s; -fx-border-color: %s; -fx-border-width: 1px; -fx-border-radius: 4px; -fx-background-radius: 4px;",
            textColor, backgroundColor, backgroundColor, borderColor
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
    
    public String getCurrentQuestion() {
        if (currentIndex >= 0 && currentIndex < chatHistory.size()) {
            QAPair currentPair = chatHistory.get(currentIndex);
            return currentPair.getQuestion();
        }
        return "";
    }
    
    public TextArea getChatHistoryArea() {
        return chatHistoryArea;
    }
    
    public TextArea getQuestionArea() {
        return questionArea;
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