package com.manuskript.windowhandling;

import javafx.scene.Cursor;
import javafx.scene.input.MouseEvent;
import javafx.stage.Window;
import javafx.stage.Stage;
import javafx.scene.Scene;

/**
 * Basisklasse für Resize-Handling von Fenstern
 * Abstrakte Basis für platform-spezifische Implementierungen
 */
public abstract class ResizeHandler {
    
    protected Window window;
    protected boolean isResizing = false;
    protected String resizeDirection = "";
    protected double startMouseX = 0;
    protected double startMouseY = 0;
    protected double startWindowX = 0;
    protected double startWindowY = 0;
    protected double startWindowWidth = 0;
    protected double startWindowHeight = 0;
    protected ResizeListener resizeListener;
    
    // Resize-Richtungen
    public static final String RESIZE_N = "N";
    public static final String RESIZE_S = "S";
    public static final String RESIZE_E = "E";
    public static final String RESIZE_W = "W";
    public static final String RESIZE_NE = "NE";
    public static final String RESIZE_NW = "NW";
    public static final String RESIZE_SE = "SE";
    public static final String RESIZE_SW = "SW";
    
    /**
     * Interface für Benachrichtigungen während des Resize-Vorgangs
     */
    public interface ResizeListener {
        void onResizeStart(MouseEvent event, String direction);
        void onResize(MouseEvent event, String direction);
        void onResizeEnd(MouseEvent event, String direction);
    }
    
    /**
     * Konstruktor
     * @param window Das zu resizende Fenster
     */
    public ResizeHandler(Window window) {
        this.window = window;
    }
    
    /**
     * Setzt einen Listener für Resize-Ereignisse
     * @param listener Der Listener
     */
    public void setResizeListener(ResizeListener listener) {
        this.resizeListener = listener;
    }
    
    /**
     * Muss von der platform-spezifischen Implementierung überschrieben werden
     * @param event MouseMoved Event
     * @return Der Cursor für die aktuelle Position
     */
    public abstract Cursor getCursorForPosition(MouseEvent event);
    
    /**
     * Muss von der platform-spezifischen Implementierung überschrieben werden
     * @param event MousePressed Event
     * @return true wenn Resize gestartet werden soll
     */
    public abstract boolean shouldStartResize(MouseEvent event);
    
    /**
     * Muss von der platform-spezifischen Implementierung überschrieben werden
     * @param event MouseDragged Event
     */
    public abstract void handleMouseDragged(MouseEvent event);
    
    /**
     * Muss von der platform-spezifischen Implementierung überschrieben werden
     * @param event MouseReleased Event
     */
    public abstract void handleMouseReleased(MouseEvent event);
    
    /**
     * Prüft, ob das Fenster aktuell resiziert wird
     * @return true wenn Resize-Vorgang aktiv
     */
    public boolean isResizing() {
        return isResizing;
    }
    
    /**
     * Beendet den Resize-Vorgang erzwingen
     */
    public void stopResizing() {
        isResizing = false;
        resizeDirection = "";
    }
    
    /**
     * Gibt die aktuelle Resize-Richtung zurück
     * @return Die Resize-Richtung
     */
    public String getResizeDirection() {
        return resizeDirection;
    }
    
    /**
     * Prüft, ob Resize für das Fenster erlaubt ist
     * @return true wenn Resize erlaubt ist
     */
    protected boolean canResize() {
        if (window == null) {
            return false;
        }
        
        // Nur für Stage Typen prüfen, da Window diese Methoden nicht hat
        if (window instanceof Stage) {
            Stage stage = (Stage) window;
            return !stage.isMaximized() && !stage.isFullScreen() && !stage.isIconified();
        }
        
        // Für andere Window-Typen nur Basis-Prüfungen
        return true;
    }
    
    /**
     * Ermittelt die Resize-Richtung basierend auf der Mouse-Position
     * @param event Das MouseEvent
     * @param sceneX X-Position in der Scene
     * @param sceneY Y-Position in der Scene
     * @return Die Resize-Richtung
     */
    protected String determineResizeDirection(MouseEvent event, double sceneX, double sceneY) {
        if (window == null) {
            return "";
        }
        
        double mouseX = sceneX;
        double mouseY = sceneY;
        double windowWidth = window.getWidth();
        double windowHeight = window.getHeight();
        
        int resizeBorder = getResizeBorderSize();
        
        boolean atTop = mouseY <= resizeBorder;
        boolean atBottom = mouseY >= windowHeight - resizeBorder;
        boolean atLeft = mouseX <= resizeBorder;
        boolean atRight = mouseX >= windowWidth - resizeBorder;
        
        // Ecken haben Priorität
        if (atTop && atLeft) return RESIZE_NW;
        if (atTop && atRight) return RESIZE_NE;
        if (atBottom && atLeft) return RESIZE_SW;
        if (atBottom && atRight) return RESIZE_SE;
        
        // Kanten
        if (atTop) return RESIZE_N;
        if (atBottom) return RESIZE_S;
        if (atLeft) return RESIZE_W;
        if (atRight) return RESIZE_E;
        
        return "";
    }
    
    /**
     * Ermittelt die Resize-Richtung (Legacy-Methode)
     * @param event Das MouseEvent
     * @return Die Resize-Richtung
     */
    protected String determineResizeDirection(MouseEvent event) {
        return determineResizeDirection(event, event.getSceneX(), event.getSceneY());
    }
    
    /**
     * Gibt die Größe des Resize-Borders zurück
     * @return Border-Größe in Pixeln
     */
    protected int getResizeBorderSize() {
        return 10; // Standard 10 Pixel
    }
    
    /**
     * Startet den Resize-Vorgang
     * @param event Das MouseEvent
     * @param direction Die Resize-Richtung
     */
    protected void startResize(MouseEvent event, String direction) {
        if (!canResize()) {
            return;
        }
        
        resizeDirection = direction;
        isResizing = true;
        
        // Startpositionen speichern
        startMouseX = event.getScreenX();
        startMouseY = event.getScreenY();
        startWindowX = window.getX();
        startWindowY = window.getY();
        startWindowWidth = window.getWidth();
        startWindowHeight = window.getHeight();
        
        // Listener benachrichtigen
        if (resizeListener != null) {
            resizeListener.onResizeStart(event, direction);
        }
    }
    
    /**
     * Beendet den Resize-Vorgang
     * @param event Das MouseEvent
     */
    protected void endResize(MouseEvent event) {
        if (!isResizing) {
            return;
        }
        
        isResizing = false;
        String direction = resizeDirection;
        resizeDirection = "";
        
        // Listener benachrichtigen
        if (resizeListener != null) {
            resizeListener.onResizeEnd(event, direction);
        }
    }
    
    /**
     * Berechnet neue Fensterdimensionen während des Resize
     * @param event Das MouseEvent
     * @return Array mit [newX, newY, newWidth, newHeight]
     */
    protected double[] calculateNewDimensions(MouseEvent event) {
        if (window == null) {
            return new double[]{startWindowX, startWindowY, startWindowWidth, startWindowHeight};
        }
        
        double deltaX = event.getScreenX() - startMouseX;
        double deltaY = event.getScreenY() - startMouseY;
        
        double newX = startWindowX;
        double newY = startWindowY;
        double newWidth = startWindowWidth;
        double newHeight = startWindowHeight;
        
        // Mindestgrößen
        double minWidth = 300.0; // Standard-Mindestbreite
        double minHeight = 200.0; // Standard-Mindesthöhe
        
        // Wenn Window eine Stage ist und Mindestgrößen hat, diese verwenden
        if (window instanceof Stage) {
            Stage stage = (Stage) window;
            minWidth = Math.max(minWidth, stage.getMinWidth());
            minHeight = Math.max(minHeight, stage.getMinHeight());
        }
        
        switch (resizeDirection) {
            case RESIZE_E:
                newWidth = Math.max(minWidth, startWindowWidth + deltaX);
                break;
            case RESIZE_W:
                newX = startWindowX + deltaX;
                newWidth = Math.max(minWidth, startWindowWidth - deltaX);
                break;
            case RESIZE_S:
                newHeight = Math.max(minHeight, startWindowHeight + deltaY);
                break;
            case RESIZE_N:
                newY = startWindowY + deltaY;
                newHeight = Math.max(minHeight, startWindowHeight - deltaY);
                break;
            case RESIZE_SE:
                newWidth = Math.max(minWidth, startWindowWidth + deltaX);
                newHeight = Math.max(minHeight, startWindowHeight + deltaY);
                break;
            case RESIZE_SW:
                newX = startWindowX + deltaX;
                newWidth = Math.max(minWidth, startWindowWidth - deltaX);
                newHeight = Math.max(minHeight, startWindowHeight + deltaY);
                break;
            case RESIZE_NE:
                newWidth = Math.max(minWidth, startWindowWidth + deltaX);
                newY = startWindowY + deltaY;
                newHeight = Math.max(minHeight, startWindowHeight - deltaY);
                break;
            case RESIZE_NW:
                newX = startWindowX + deltaX;
                newY = startWindowY + deltaY;
                newWidth = Math.max(minWidth, startWindowWidth - deltaX);
                newHeight = Math.max(minHeight, startWindowHeight - deltaY);
                break;
        }
        
        return new double[]{newX, newY, newWidth, newHeight};
    }
    
    /**
     * Setzt neue Fensterdimensionen
     * @param x Neue X-Position
     * @param y Neue Y-Position
     * @param width Neue Breite
     * @param height Neue Höhe
     */
    protected void setWindowDimensions(double x, double y, double width, double height) {
        if (window != null && canResize()) {
            // Nur ändern, wenn es tatsächlich neue Werte sind
            if (Math.abs(window.getX() - x) > 0.1) window.setX(x);
            if (Math.abs(window.getY() - y) > 0.1) window.setY(y);
            if (Math.abs(window.getWidth() - width) > 0.1) window.setWidth(width);
            if (Math.abs(window.getHeight() - height) > 0.1) window.setHeight(height);
        }
    }
    
    /**
     * Benachrichtigt den Listener über Resize-Vorgang
     * @param event Das MouseEvent
     */
    protected void notifyResize(MouseEvent event) {
        if (resizeListener != null) {
            resizeListener.onResize(event, resizeDirection);
        }
    }
}
