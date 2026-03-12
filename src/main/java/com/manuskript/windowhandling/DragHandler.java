package com.manuskript.windowhandling;

import javafx.scene.input.MouseEvent;
import javafx.stage.Window;
import javafx.stage.Stage;

/**
 * Basisklasse für Drag-Handling von Fenstern
 * Abstrakte Basis für platform-spezifische Implementierungen
 */
public abstract class DragHandler {
    
    protected Window window;
    protected double xOffset = 0;
    protected double yOffset = 0;
    protected boolean isDragging = false;
    protected DragListener dragListener;
    
    /**
     * Interface für Benachrichtigungen während des Drag-Vorgangs
     */
    public interface DragListener {
        void onDragStart(MouseEvent event);
        void onDrag(MouseEvent event);
        void onDragEnd(MouseEvent event);
    }
    
    /**
     * Konstruktor
     * @param window Das zu bewegende Fenster
     */
    public DragHandler(Window window) {
        this.window = window;
    }
    
    /**
     * Setzt einen Listener für Drag-Ereignisse
     * @param listener Der Listener
     */
    public void setDragListener(DragListener listener) {
        this.dragListener = listener;
    }
    
    /**
     * Muss von der platform-spezifischen Implementierung überschrieben werden
     * @param event MousePressed Event
     */
    public abstract void handleMousePressed(MouseEvent event);
    
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
     * Prüft, ob das Fenster aktuell gezogen wird
     * @return true wenn Drag-Vorgang aktiv
     */
    public boolean isDragging() {
        return isDragging;
    }
    
    /**
     * Beendet den Drag-Vorgang erzwingen
     */
    public void stopDragging() {
        isDragging = false;
    }
    
    /**
     * Prüft, ob Dragging für das Fenster erlaubt ist
     * @return true wenn Dragging erlaubt ist
     */
    protected boolean canDrag() {
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
     * Benachrichtigt den Listener über Drag-Start
     * @param event Das MouseEvent
     */
    protected void notifyDragStart(MouseEvent event) {
        if (dragListener != null) {
            dragListener.onDragStart(event);
        }
    }
    
    /**
     * Benachrichtigt den Listener über Drag-Vorgang
     * @param event Das MouseEvent
     */
    protected void notifyDrag(MouseEvent event) {
        if (dragListener != null) {
            dragListener.onDrag(event);
        }
    }
    
    /**
     * Benachrichtigt den Listener über Drag-Ende
     * @param event Das MouseEvent
     */
    protected void notifyDragEnd(MouseEvent event) {
        if (dragListener != null) {
            dragListener.onDragEnd(event);
        }
    }
    
    /**
     * Berechnet die neue Fensterposition basierend auf dem Mouse-Event
     * @param event Das MouseEvent
     * @return Array mit [newX, newY]
     */
    protected double[] calculateNewPosition(MouseEvent event) {
        if (window == null) {
            return new double[]{0, 0};
        }
        
        double newX = event.getScreenX() - xOffset;
        double newY = event.getScreenY() - yOffset;
        
        // Sicherstellen, dass das Fenster nicht komplett außerhalb des Bildschirms liegt
        return constrainToScreen(newX, newY);
    }
    
    /**
     * Begrenzt die Fensterposition auf den sichtbaren Bereich des aktuellen Screens
     * @param x Gewünschte X-Position
     * @param y Gewünschte Y-Position
     * @return Array mit [constrainedX, constrainedY]
     */
    protected double[] constrainToScreen(double x, double y) {
        if (window == null) {
            return new double[]{x, y};
        }
        
        // Aktuellen Screen ermitteln
        var currentScreen = ScreenDetector.getCurrentScreen(window);
        var screenBounds = currentScreen.getVisualBounds();
        var windowWidth = window.getWidth();
        var windowHeight = window.getHeight();
        
        // X-Position begrenzen
        double constrainedX = x;
        if (constrainedX < screenBounds.getMinX()) {
            constrainedX = screenBounds.getMinX();
        } else if (constrainedX + windowWidth > screenBounds.getMaxX()) {
            constrainedX = screenBounds.getMaxX() - windowWidth;
        }
        
        // Y-Position begrenzen
        double constrainedY = y;
        if (constrainedY < screenBounds.getMinY()) {
            constrainedY = screenBounds.getMinY();
        } else if (constrainedY + windowHeight > screenBounds.getMaxY()) {
            constrainedY = screenBounds.getMaxY() - windowHeight;
        }
        
        return new double[]{constrainedX, constrainedY};
    }
    
    /**
     * Setzt die Fensterposition
     * @param x Neue X-Position
     * @param y Neue Y-Position
     */
    protected void setWindowPosition(double x, double y) {
        if (window != null && canDrag()) {
            window.setX(x);
            window.setY(y);
        }
    }
}
