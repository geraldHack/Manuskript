package com.manuskript.windowhandling;

import com.manuskript.windowhandling.platform.MacDragHandler;
import com.manuskript.windowhandling.platform.MacResizeHandler;
import com.manuskript.windowhandling.platform.MacMaximizeHandler;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.scene.Cursor;
import javafx.stage.Window;

/**
 * macOS-spezifischer Window Manager
 * Koordiniert Drag, Resize und Maximize für macOS
 */
public class MacWindowManager {
    
    private Window window;
    private Node titleBar;
    private Node resizeArea;
    
    private MacDragHandler dragHandler;
    private MacResizeHandler resizeHandler;
    private MacMaximizeHandler maximizeHandler;
    
    private boolean initialized = false;
    private boolean debugMode = false;
    
    /**
     * Konstruktor
     * @param window Das zu verwaltende Fenster
     * @param titleBar Die Titelleiste für Drag-Operationen
     */
    public MacWindowManager(Window window, Node titleBar) {
        this.window = window;
        this.titleBar = titleBar;
        initializeHandlers();
    }
    
    /**
     * Konstruktor mit Resize-Area
     * @param window Das zu verwaltende Fenster
     * @param titleBar Die Titelleiste für Drag-Operationen
     * @param resizeArea Der Bereich für Resize-Operationen (kann null sein für ganze Scene)
     */
    public MacWindowManager(Window window, Node titleBar, Node resizeArea) {
        this.window = window;
        this.titleBar = titleBar;
        this.resizeArea = resizeArea;
        initializeHandlers();
    }
    
    /**
     * Aktiviert/Deaktiviert Debug-Modus
     * @param debugMode true für Debug-Ausgaben
     */
    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
    }
    
    /**
     * Initialisiert alle Handler
     */
    private void initializeHandlers() {
        if (window == null) {
            logError("Window ist null - Handler können nicht initialisiert werden");
            return;
        }
        
        try {
            // Drag Handler
            dragHandler = new MacDragHandler(window);
            
            // Resize Handler
            resizeHandler = new MacResizeHandler(window);
            
            // Maximize Handler
            maximizeHandler = new MacMaximizeHandler(window);
            
            initialized = true;
            logDebug("MacWindowManager erfolgreich initialisiert");
            
        } catch (Exception e) {
            logError("Fehler bei der Initialisierung der Handler: " + e.getMessage());
        }
    }
    
    /**
     * Richtet alle Event-Handler ein
     */
    public void setupEventHandlers() {
        if (!initialized) {
            logError("MacWindowManager nicht initialisiert");
            return;
        }
        
        try {
            // Nur Drag-Handler einrichten (für zusätzliche Features wie Edge-Snapping)
            // Grundlegende Drag/Resize wird von CustomStage gehandhabt
            setupDragHandlers();
            
            logDebug("Event-Handler erfolgreich eingerichtet");
            
        } catch (Exception e) {
            logError("Fehler beim Einrichten der Event-Handler: " + e.getMessage());
        }
    }
    
    /**
     * Richtet Drag-Handler ein
     */
    private void setupDragHandlers() {
        if (titleBar == null || dragHandler == null) {
            return;
        }
        
        // Mouse-Pressed für Drag-Start
        titleBar.setOnMousePressed(event -> {
            if (dragHandler.shouldHandleDragEvent(event)) {
                dragHandler.handleMousePressed(event);
            }
        });
        
        // Mouse-Dragged für Drag-Vorgang
        titleBar.setOnMouseDragged(event -> {
            if (dragHandler.isDragging()) {
                dragHandler.handleMouseDragged(event);
            }
        });
        
        // Mouse-Released für Drag-Ende
        titleBar.setOnMouseReleased(event -> {
            if (dragHandler.isDragging()) {
                dragHandler.handleMouseReleased(event);
            }
        });
        
        // Doppelklick für Maximieren
        titleBar.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && !dragHandler.isDragging()) {
                maximizeHandler.toggleMaximize();
                event.consume();
            }
        });
    }
    
    /**
     * Richtet Resize-Handler ein
     */
    private void setupResizeHandlers() {
        if (resizeHandler == null) {
            return;
        }
        
        // Verzögerte Initialisierung, bis Scene bereit ist
        if (window.getScene() == null) {
            // Listener registrieren, der auslöst, wenn Scene verfügbar ist
            window.sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene != null) {
                    setupResizeHandlersWhenSceneReady();
                }
            });
            return;
        }
        
        setupResizeHandlersWhenSceneReady();
    }
    
    /**
     * Richtet Resize-Handler ein, wenn Scene bereit ist
     */
    private void setupResizeHandlersWhenSceneReady() {
        Node resizeTarget = resizeArea != null ? resizeArea : window.getScene().getRoot();
        
        if (resizeTarget == null) {
            return;
        }
        
        // Mouse-Moved für Cursor-Updates
        resizeTarget.setOnMouseMoved(event -> {
            if (!resizeHandler.isResizing()) {
                Cursor cursor = resizeHandler.getCursorForPosition(event);
                if (resizeTarget.getCursor() != cursor) {
                    resizeTarget.setCursor(cursor);
                }
            }
        });
        
        // Mouse-Pressed für Resize-Start
        resizeTarget.setOnMousePressed(event -> {
            if (resizeHandler.shouldStartResize(event)) {
                resizeHandler.startResize(event);
            }
        });
        
        // Mouse-Dragged für Resize-Vorgang
        resizeTarget.setOnMouseDragged(event -> {
            if (resizeHandler.isResizing()) {
                resizeHandler.handleMouseDragged(event);
            }
        });
        
        // Mouse-Released für Resize-Ende
        resizeTarget.setOnMouseReleased(event -> {
            if (resizeHandler.isResizing()) {
                resizeHandler.handleMouseReleased(event);
                // Cursor zurücksetzen
                Cursor cursor = resizeHandler.getCursorForPosition(event);
                resizeTarget.setCursor(cursor);
            }
        });
        
        // Mouse-Exited für Cursor-Reset
        resizeTarget.setOnMouseExited(event -> {
            if (!resizeHandler.isResizing()) {
                resizeTarget.setCursor(Cursor.DEFAULT);
            }
        });
        
        logDebug("Resize-Handler erfolgreich eingerichtet");
    }
    
    /**
     * Richtet Maximize-Handler ein
     */
    private void setupMaximizeHandlers() {
        // Maximize-Handler benötigt keine direkten Event-Handler
        // wird über Drag-Handler (Doppelklick) oder externe Aufrufe gesteuert
    }
    
    /**
     * Prüft, ob das Drag-Event behandelt werden sollte
     * @param event Das MouseEvent
     * @return true wenn das Event behandelt werden sollte
     */
    public boolean shouldHandleDragEvent(MouseEvent event) {
        if (!initialized || dragHandler == null) {
            return false;
        }
        
        return dragHandler.shouldHandleDragEvent(event);
    }
    
    /**
     * Getter für Drag-Handler
     * @return Der Drag-Handler
     */
    public MacDragHandler getDragHandler() {
        return dragHandler;
    }
    
    /**
     * Getter für Resize-Handler
     * @return Der Resize-Handler
     */
    public MacResizeHandler getResizeHandler() {
        return resizeHandler;
    }
    
    /**
     * Getter für Maximize-Handler
     * @return Der Maximize-Handler
     */
    public MacMaximizeHandler getMaximizeHandler() {
        return maximizeHandler;
    }
    
    /**
     * Prüft, ob der Manager initialisiert ist
     * @return true wenn initialisiert
     */
    public boolean isInitialized() {
        return initialized;
    }
    
    /**
     * Beendet alle Operationen sauber
     */
    public void cleanup() {
        if (dragHandler != null) {
            dragHandler.stopDragging();
        }
        
        if (resizeHandler != null) {
            resizeHandler.stopResizing();
        }
        
        logDebug("MacWindowManager aufgeräumt");
    }
    
    /**
     * Gibt Debug-Informationen aus
     * @return String mit Debug-Informationen
     */
    public String getDebugInfo() {
        if (!initialized) {
            return "MacWindowManager nicht initialisiert";
        }
        
        StringBuilder info = new StringBuilder();
        info.append("MacWindowManager Debug Info:\n");
        info.append("Initialized: ").append(initialized).append("\n");
        info.append("Debug Mode: ").append(debugMode).append("\n");
        
        if (dragHandler != null) {
            info.append("Drag Handler - Is Dragging: ").append(dragHandler.isDragging()).append("\n");
        }
        
        if (resizeHandler != null) {
            info.append("Resize Handler - Is Resizing: ").append(resizeHandler.isResizing()).append("\n");
            info.append("Resize Handler - Direction: ").append(resizeHandler.getResizeDirection()).append("\n");
        }
        
        if (maximizeHandler != null) {
            info.append("Maximize Handler - Is Maximized: ").append(maximizeHandler.isMaximized()).append("\n");
        }
        
        if (window != null) {
            info.append("Window Bounds: X=").append(window.getX())
                .append(", Y=").append(window.getY())
                .append(", W=").append(window.getWidth())
                .append(", H=").append(window.getHeight()).append("\n");
        }
        
        // Screen-Informationen
        info.append("\n").append(ScreenDetector.getScreenInfo());
        
        return info.toString();
    }
    
    /**
     * Loggt Debug-Informationen
     * @param message Die Nachricht
     */
    private void logDebug(String message) {
        if (debugMode) {
            System.out.println("[MacWindowManager DEBUG] " + message);
        }
    }
    
    /**
     * Loggt Fehler
     * @param message Die Fehlermeldung
     */
    private void logError(String message) {
        System.err.println("[MacWindowManager ERROR] " + message);
    }
}
