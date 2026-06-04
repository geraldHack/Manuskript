package com.manuskript;

import javafx.scene.control.ButtonType;
import javafx.scene.control.ButtonBar;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Zentrale Factory für alle Dialoge
 * Stellt sicher, dass alle Dialoge einheitlich als CustomStage/CustomAlert erstellt werden
 */
public class DialogFactory {
    
    private static final Logger logger = LoggerFactory.getLogger(DialogFactory.class);
    
    /**
     * Erstellt einen CustomAlert mit Bestätigung
     */
    public static CustomAlert createConfirmationAlert(String title, String header, String content, Window owner) {
        CustomAlert alert = new CustomAlert(CustomAlert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        
        if (owner != null) {
            alert.initOwner(owner);
        }
        
        // Theme anwenden
        ThemeManager.applyThemeToAlert(alert);
        
        return alert;
    }
    
    /**
     * Erstellt einen CustomAlert mit Information
     */
    public static CustomAlert createInfoAlert(String title, String header, String content, Window owner) {
        CustomAlert alert = new CustomAlert(CustomAlert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        
        if (owner != null) {
            alert.initOwner(owner);
        }
        
        // Theme anwenden
        ThemeManager.applyThemeToAlert(alert);
        
        return alert;
    }
    
    /**
     * Erstellt einen CustomAlert mit Warnung
     */
    public static CustomAlert createWarningAlert(String title, String header, String content, Window owner) {
        CustomAlert alert = new CustomAlert(CustomAlert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        
        if (owner != null) {
            alert.initOwner(owner);
        }
        
        // Theme anwenden
        ThemeManager.applyThemeToAlert(alert);
        
        return alert;
    }
    
    /**
     * Erstellt einen CustomAlert mit Fehler
     */
    public static CustomAlert createErrorAlert(String title, String header, String content, Window owner) {
        CustomAlert alert = new CustomAlert(CustomAlert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        
        if (owner != null) {
            alert.initOwner(owner);
        }
        
        // Theme anwenden
        ThemeManager.applyThemeToAlert(alert);
        
        return alert;
    }
    
    /**
     * Erstellt einen CustomAlert für "Ungespeicherte Änderungen"
     */
    public static CustomAlert createUnsavedChangesAlert(Window owner) {
        CustomAlert alert = createConfirmationAlert(
            "Ungespeicherte Änderungen",
            "Möchten Sie die aktuellen Änderungen speichern?",
            "Es gibt ungespeicherte Änderungen.",
            owner
        );
        
        // Standard-Buttons für ungespeicherte Änderungen (CustomAlert nutzt getButtonTypes(), nicht getDialogPane())
        ButtonType saveButton = new ButtonType("Ja, speichern");
        ButtonType noButton = new ButtonType("Nein, verwerfen");
        ButtonType cancelButton = new ButtonType("Abbrechen", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().clear();
        alert.getButtonTypes().addAll(saveButton, noButton, cancelButton);
        return alert;
    }
    
    /**
     * Erstellt einen CustomAlert für DOCX-Änderungen
     */
    public static CustomAlert createDocxChangedAlert(String fileName, Window owner) {
        // Direkt einen CustomAlert erstellen, nicht über createConfirmationAlert
        CustomAlert alert = new CustomAlert(CustomAlert.AlertType.CONFIRMATION);
        alert.setTitle("DOCX-Datei wurde extern verändert");
        alert.setHeaderText("Die DOCX-Datei '" + fileName + "' wurde extern verändert.");
        alert.setContentText("Was möchten Sie tun?");
        
        if (owner != null) {
            alert.initOwner(owner);
        }
        
        // Spezielle Buttons für DOCX-Änderungen (CustomAlert nutzt getButtonTypes(), nicht getDialogPane())
        ButtonType diffButton = new ButtonType("🔍 Diff anzeigen");
        ButtonType docxButton = new ButtonType("DOCX übernehmen");
        ButtonType ignoreButton = new ButtonType("Ignorieren");
        ButtonType cancelButton = new ButtonType("Abbrechen");
        alert.getButtonTypes().clear();
        alert.getButtonTypes().addAll(diffButton, docxButton, ignoreButton, cancelButton);
        // Theme direkt anwenden
        ThemeManager.applyThemeToAlert(alert);
        
        return alert;
    }
    
    /**
     * Zeigt einen einfachen Info-Dialog
     */
    public static void showInfo(String title, String content, Window owner) {
        CustomAlert alert = createInfoAlert(title, null, content, owner);
        alert.showAndWait(owner);
    }
    
    /**
     * Zeigt einen einfachen Error-Dialog
     */
    public static void showError(String title, String content, Window owner) {
        CustomAlert alert = createErrorAlert(title, null, content, owner);
        alert.showAndWait(owner);
    }
    
    /**
     * Zeigt einen einfachen Warning-Dialog
     */
    public static void showWarning(String title, String content, Window owner) {
        CustomAlert alert = createWarningAlert(title, null, content, owner);
        alert.showAndWait(owner);
    }
    
    /**
     * Zeigt einen Bestätigungs-Dialog und gibt das Ergebnis zurück
     */
    public static Optional<ButtonType> showConfirmation(String title, String content, Window owner) {
        CustomAlert alert = createConfirmationAlert(title, null, content, owner);
        return alert.showAndWait(owner);
    }
}