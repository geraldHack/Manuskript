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
    private Runnable onDisplayChange;
    
    public CustomChatArea() {
        initializeUI();
    }
    
    private void initializeUI() {
        // Frage-TextArea (oben, farblich hervorgehoben)
        questionArea = new TextArea("Keine Frage ausgewählt");
        questionArea.setEditable(false);
        questionArea.setWrapText(true);
        questionArea.setPrefRowCount(4);  // noch etwas höher
        questionArea.setMaxHeight(108);   // angepasst
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
        upButton.setId("btnChatUp");
        upButton.setPrefWidth(32);
        upButton.setPrefHeight(32);
        upButton.setDisable(true);
        upButton.setOnAction(e -> showPrevious());
        
        downButton = new Button("↓");
        downButton.setId("btnChatDown");
        downButton.setPrefWidth(32);
        downButton.setPrefHeight(32);
        downButton.setDisable(true);
        downButton.setOnAction(e -> showNext());
        
        // Keyboard-Hinweis (kleiner)
        Label keyboardHint = new Label("Ctrl+↑/↓");
        keyboardHint.setFont(Font.font("System", 8));
        keyboardHint.setStyle("-fx-font-style: italic;");
        
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
        Runnable action = () -> {
            String numberedQuestion = addNumberIfDuplicate(question);
            QAPair newPair = new QAPair(numberedQuestion, "");
            chatHistory.add(newPair);
            currentIndex = chatHistory.size() - 1;
            updateDisplay();
        };
        if (Platform.isFxApplicationThread()) action.run();
        else Platform.runLater(action);
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
        Runnable apply = () -> {
            chatHistory.clear();
            chatHistory.addAll(sessionHistory);
            currentIndex = chatHistory.size() - 1;
            updateDisplay();
        };
        if (javafx.application.Platform.isFxApplicationThread()) {
            apply.run();
        } else {
            Platform.runLater(apply);
        }
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

    /**
     * Gibt den Index des letzten Frage-Antwort-Paares zurück
     */
    public int getLastIndex() {
        return chatHistory.isEmpty() ? -1 : chatHistory.size() - 1;
    }

    /**
     * Setzt die Antwort für ein bestimmtes QAPair anhand des Index –
     * unabhängig davon, welcher Eintrag aktuell angezeigt wird.
     */
    public void setAnswerAt(int index, String response) {
        Platform.runLater(() -> {
            if (index >= 0 && index < chatHistory.size()) {
                QAPair pair = chatHistory.get(index);
                pair.setAnswer(response);
                if (index == currentIndex) {
                    updateDisplay();
                }
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
            // Beim Streamen automatisch nach unten scrollen
            int len = chatHistoryArea.getText() != null ? chatHistoryArea.getText().length() : 0;
            chatHistoryArea.positionCaret(len);
            try { chatHistoryArea.setScrollTop(Double.MAX_VALUE); } catch (Exception ignored) {}
            
            // Navigation-Buttons aktualisieren
            upButton.setDisable(currentIndex <= 0);
            downButton.setDisable(currentIndex >= chatHistory.size() - 1);
            
            // Scroll-Indikator aktualisieren
            updateScrollIndicator();
        } else {
            // Leerer Zustand: Felder zurücksetzen
            questionArea.setText("Keine Frage ausgewählt");
            chatHistoryArea.clear();
            upButton.setDisable(true);
            downButton.setDisable(true);
            scrollIndicator.getChildren().clear();
        }
        // Callback benachrichtigen (z. B. externes Ergebnisfenster aktualisieren)
        if (onDisplayChange != null) {
            try { onDisplayChange.run(); } catch (Exception ignored) {}
        }
    }

    public void setOnDisplayChange(Runnable callback) {
        this.onDisplayChange = callback;
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
            case 0: // Weiß
                backgroundColor = "#ffffff";
                textColor = "#111827";
                borderColor = "#d1d5db";
                highlightColor = "#e5e7eb";
                break;
            case 1: // Schwarz (Dark)
                backgroundColor = "#2b2b2b";
                textColor = "#ffffff";
                borderColor = "#4b5563";
                highlightColor = "#3182ce";
                break;
            case 2: // Pastell
                backgroundColor = "#e5e7eb";
                textColor = "#111827";
                borderColor = "#9ca3af";
                highlightColor = "#9ca3af";
                break;
            case 3: // Blau
                backgroundColor = "#1e3a8a";
                textColor = "#ffffff";
                borderColor = "#3b82f6";
                highlightColor = "#60a5fa";
                break;
            case 4: // Grün
                backgroundColor = "#064e3b";
                textColor = "#ffffff";
                borderColor = "#059669";
                highlightColor = "#10b981";
                break;
            case 5: // Lila
                backgroundColor = "#581c87";
                textColor = "#ffffff";
                borderColor = "#8b5cf6";
                highlightColor = "#a78bfa";
                break;
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
        
        // Buttons Theme (Navigation)
        String buttonStyle = String.format(
            "-fx-background-color: %s; -fx-text-fill: %s; -fx-border-color: %s; -fx-border-width: 1px; -fx-border-radius: 3px; -fx-background-radius: 3px;",
            backgroundColor, textColor, borderColor
        );
        upButton.setStyle(buttonStyle);
        downButton.setStyle(buttonStyle);
        // Hint-Farbe je Theme
        // Hell/Pastell: dunkleres Grau, sonst helles Grau
        String hintColor = (themeIndex == 0 || themeIndex == 2) ? "#6b7280" : "#d1d5db";
        // keyboardHint ist das erste Kind der NavigationBox, daher über Parent suchen
        try {
            Label hint = (Label)((VBox)((HBox)this.getChildren().get(1)).getChildren().get(1)).getChildren().get(0);
            hint.setStyle("-fx-text-fill: " + hintColor + "; -fx-font-style: italic;");
        } catch (Exception ignored) {}
        
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
                    // Andere Einträge - normal (leicht abgesetzt je nach Theme)
                    String normalBg = (themeIndex == 0 || themeIndex == 2) ? "#f3f4f6" : "#374151";
                    indicator.setStyle(String.format(
                        "-fx-background-color: %s; -fx-border-color: %s; -fx-border-width: 1px;",
                        normalBg, borderColor
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
    
    public String getCurrentAnswer() {
        if (currentIndex >= 0 && currentIndex < chatHistory.size()) {
            QAPair currentPair = chatHistory.get(currentIndex);
            return currentPair.getAnswer() != null ? currentPair.getAnswer() : "";
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