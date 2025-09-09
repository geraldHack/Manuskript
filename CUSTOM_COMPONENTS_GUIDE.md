# Custom Components Guide - Konsistente Architektur

## Übersicht

Dieses Dokument beschreibt die neue konsistente Architektur für alle Fenster und Dialoge in Manuskript.

## Problem

**Vorher:** Inkonsistente Verwendung von Fenstern und Dialogen
- Manche Fenster als `CustomStage`
- Andere als normale `Stage`
- Manche Dialoge als `CustomAlert`
- Andere als normale `Alert`
- Unterschiedliche Theme-Anwendung
- Inkonsistente Rahmen und Styling

**Jetzt:** Einheitliche Architektur
- **ALLE** Fenster als `CustomStage`
- **ALLE** Dialoge als `CustomAlert`
- **Zentrale Theme-Verwaltung** über `ThemeManager`
- **Einheitliche Factory** über `DialogFactory`
- **Konsistente Rahmen** und Styling

## Neue Architektur

### 1. ThemeManager
**Zentrale Theme-Verwaltung für alle Custom-Komponenten**

```java
// Theme setzen
ThemeManager.setTheme(4); // Grün-Theme

// Theme auf alle Komponenten anwenden
ThemeManager.applyThemeToAll();

// Theme auf einzelne Komponente anwenden
ThemeManager.applyThemeToStage(customStage);
ThemeManager.applyThemeToAlert(customAlert);
```

### 2. DialogFactory
**Zentrale Factory für alle Dialoge**

```java
// Standard-Dialoge
DialogFactory.showInfo("Titel", "Nachricht", owner);
DialogFactory.showError("Fehler", "Fehlermeldung", owner);
DialogFactory.showWarning("Warnung", "Warnmeldung", owner);

// Bestätigungs-Dialog
Optional<ButtonType> result = DialogFactory.showConfirmation("Titel", "Nachricht", owner);

// Spezielle Dialoge
CustomAlert unsavedAlert = DialogFactory.createUnsavedChangesAlert(owner);
CustomAlert docxAlert = DialogFactory.createDocxChangedAlert("datei.docx", owner);
```

### 3. StageManager
**Zentrale Verwaltung für alle CustomStages**

```java
// Neue Stage erstellen
CustomStage stage = StageManager.createStage("Titel");

// Modal-Dialog erstellen
CustomStage modalStage = StageManager.createModalStage("Titel", owner);

// Stage mit Scene erstellen
CustomStage stageWithScene = StageManager.createStageWithScene("Titel", scene);
```

## Verwendung

### Neue Fenster erstellen
**❌ Falsch (alte Art):**
```java
Stage stage = new Stage();
stage.setTitle("Titel");
stage.show();
```

**✅ Richtig (neue Art):**
```java
CustomStage stage = StageManager.createStage("Titel");
stage.show();
```

### Neue Dialoge erstellen
**❌ Falsch (alte Art):**
```java
Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
alert.setTitle("Titel");
alert.setContentText("Nachricht");
alert.showAndWait();
```

**✅ Richtig (neue Art):**
```java
DialogFactory.showConfirmation("Titel", "Nachricht", owner);
```

### Theme anwenden
**❌ Falsch (alte Art):**
```java
// Jede Komponente einzeln thematisieren
stage.setTitleBarTheme(themeIndex);
alert.applyTheme(themeIndex);
```

**✅ Richtig (neue Art):**
```java
// Zentrale Theme-Verwaltung
ThemeManager.setTheme(themeIndex);
ThemeManager.applyThemeToAll();
```

## Migration

### Bestehende Code umstellen

1. **Alerts umstellen:**
   ```java
   // Alt
   Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
   
   // Neu
   CustomAlert alert = DialogFactory.createConfirmationAlert("Titel", "Header", "Content", owner);
   ```

2. **Stages umstellen:**
   ```java
   // Alt
   Stage stage = new Stage();
   
   // Neu
   CustomStage stage = StageManager.createStage("Titel");
   ```

3. **Theme-Anwendung umstellen:**
   ```java
   // Alt
   stage.setTitleBarTheme(themeIndex);
   
   // Neu
   ThemeManager.applyThemeToStage(stage);
   ```

## Vorteile

1. **Konsistenz:** Alle Fenster und Dialoge haben das gleiche Aussehen
2. **Wartbarkeit:** Zentrale Verwaltung erleichtert Änderungen
3. **Theme-Synchronisation:** Alle Komponenten reagieren auf Theme-Änderungen
4. **Einheitliche Rahmen:** Alle Fenster haben konsistente Rahmen
5. **Weniger Code-Duplikation:** Factory-Methoden reduzieren Wiederholungen

## Regeln

1. **NIEMALS** normale `Stage` oder `Alert` verwenden
2. **IMMER** `CustomStage` und `CustomAlert` verwenden
3. **IMMER** `DialogFactory` für Dialoge verwenden
4. **IMMER** `StageManager` für Fenster verwenden
5. **IMMER** `ThemeManager` für Theme-Verwaltung verwenden

## Beispiele

### Komplettes Beispiel für neuen Dialog
```java
public void showMyDialog() {
    CustomAlert alert = DialogFactory.createConfirmationAlert(
        "Mein Dialog",
        "Header Text",
        "Content Text",
        primaryStage
    );
    
    ButtonType yesButton = new ButtonType("Ja");
    ButtonType noButton = new ButtonType("Nein");
    alert.getDialogPane().getButtonTypes().setAll(yesButton, noButton);
    
    Optional<ButtonType> result = alert.showAndWait();
    if (result.isPresent() && result.get() == yesButton) {
        // Ja geklickt
    }
}
```

### Komplettes Beispiel für neues Fenster
```java
public void showMyWindow() {
    CustomStage stage = StageManager.createModalStage("Mein Fenster", primaryStage);
    
    VBox content = new VBox(10);
    content.setPadding(new Insets(15));
    content.getChildren().add(new Label("Fenster-Inhalt"));
    
    Scene scene = new Scene(content, 400, 300);
    stage.setScene(scene);
    
    stage.showAndWait();
}
```
