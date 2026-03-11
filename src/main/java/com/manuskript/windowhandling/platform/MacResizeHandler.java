package com.manuskript.windowhandling.platform;

import com.manuskript.windowhandling.ResizeHandler;
import com.manuskript.windowhandling.ScreenDetector;
import javafx.scene.Cursor;
import javafx.scene.input.MouseEvent;
import javafx.stage.Window;
import javafx.stage.Stage;
import javafx.geometry.Rectangle2D;

/**
 * macOS-spezifische Implementierung des Resize-Handlers
 * Berücksichtigt macOS-Besonderheiten wie Menüleiste und Dock
 */
public class MacResizeHandler extends ResizeHandler {
    
    private static final int MAC_RESIZE_BORDER = 8; // macOS hat schmalere Resize-Borders
    private boolean enableSnapToEdges = true;
    private boolean enableVisualFeedback = true;
    
    /**
     * Konstruktor
     * @param window Das zu resizende Fenster
     */
    public MacResizeHandler(Window window) {
        super(window);
    }
    
    /**
     * Aktiviert/Deaktiviert Edge-Snapping während Resize
     * @param enableSnapToEdges true für Edge-Snapping
     */
    public void setEnableSnapToEdges(boolean enableSnapToEdges) {
        this.enableSnapToEdges = enableSnapToEdges;
    }
    
    /**
     * Aktiviert/Deaktiviert visuelles Feedback während Resize
     * @param enableVisualFeedback true für visuelles Feedback
     */
    public void setEnableVisualFeedback(boolean enableVisualFeedback) {
        this.enableVisualFeedback = enableVisualFeedback;
    }
    
    @Override
    public Cursor getCursorForPosition(MouseEvent event) {
        if (!canResize()) {
            return Cursor.DEFAULT;
        }
        
        String direction = determineResizeDirection(event);
        
        switch (direction) {
            case RESIZE_N: return Cursor.N_RESIZE;
            case RESIZE_S: return Cursor.S_RESIZE;
            case RESIZE_E: return Cursor.E_RESIZE;
            case RESIZE_W: return Cursor.W_RESIZE;
            case RESIZE_NE: return Cursor.NE_RESIZE;
            case RESIZE_NW: return Cursor.NW_RESIZE;
            case RESIZE_SE: return Cursor.SE_RESIZE;
            case RESIZE_SW: return Cursor.SW_RESIZE;
            default: return Cursor.DEFAULT;
        }
    }
    
    @Override
    public boolean shouldStartResize(MouseEvent event) {
        if (!canResize()) {
            return false;
        }
        
        // Prüfen, ob die Maus sich in einem Resize-Bereich befindet
        String direction = determineResizeDirection(event);
        return !direction.isEmpty();
    }
    
    @Override
    public void handleMouseDragged(MouseEvent event) {
        if (!isResizing || !canResize()) {
            return;
        }
        
        // Neue Dimensionen berechnen
        double[] newDimensions = calculateNewDimensions(event);
        double newX = newDimensions[0];
        double newY = newDimensions[1];
        double newWidth = newDimensions[2];
        double newHeight = newDimensions[3];
        
        // macOS-spezifische Anpassungen
        if (enableSnapToEdges) {
            double[] snappedDimensions = applyMacResizeSnapping(newX, newY, newWidth, newHeight);
            newX = snappedDimensions[0];
            newY = snappedDimensions[1];
            newWidth = snappedDimensions[2];
            newHeight = snappedDimensions[3];
        }
        
        // Auf Screen-Bounds begrenzen
        double[] constrainedDimensions = constrainToScreen(newX, newY, newWidth, newHeight);
        newX = constrainedDimensions[0];
        newY = constrainedDimensions[1];
        newWidth = constrainedDimensions[2];
        newHeight = constrainedDimensions[3];
        
        // Fensterdimensionen aktualisieren
        setWindowDimensions(newX, newY, newWidth, newHeight);
        
        // Event konsumieren
        event.consume();
        
        // Listener benachrichtigen
        notifyResize(event);
    }
    
    @Override
    public void handleMouseReleased(MouseEvent event) {
        endResize(event);
        event.consume();
    }
    
    /**
     * Startet den Resize-Vorgang (macOS-spezifisch)
     * @param event Das MouseEvent
     */
    public void startResize(MouseEvent event) {
        if (!shouldStartResize(event)) {
            return;
        }
        
        String direction = determineResizeDirection(event);
        startResize(event, direction);
        event.consume();
    }
    
    /**
     * Wendet macOS-spezifisches Resize-Snapping an
     * @param x Aktuelle X-Position
     * @param y Aktuelle Y-Position
     * @param width Aktuelle Breite
     * @param height Aktuelle Höhe
     * @return Array mit [snappedX, snappedY, snappedWidth, snappedHeight]
     */
    private double[] applyMacResizeSnapping(double x, double y, double width, double height) {
        if (window == null) {
            return new double[]{x, y, width, height};
        }
        
        // Aktuellen Screen und seine Bounds ermitteln
        var currentScreen = ScreenDetector.getCurrentScreen(window);
        var screenBounds = currentScreen.getBounds();
        var visualBounds = currentScreen.getVisualBounds();
        
        double snappedX = x;
        double snappedY = y;
        double snappedWidth = width;
        double snappedHeight = height;
        
        final double SNAP_THRESHOLD = 15.0;
        
        // Horizontales Snapping
        // Linker Rand
        if (Math.abs(x - visualBounds.getMinX()) < SNAP_THRESHOLD) {
            snappedX = visualBounds.getMinX();
        }
        // Rechter Rand
        else if (Math.abs(x + width - visualBounds.getMaxX()) < SNAP_THRESHOLD) {
            snappedX = visualBounds.getMaxX() - width;
        }
        
        // Vertikales Snapping
        // Oberer Rand (unter Menüleiste)
        if (Math.abs(y - visualBounds.getMinY()) < SNAP_THRESHOLD) {
            snappedY = visualBounds.getMinY();
        }
        // Unterer Rand (über Dock)
        else if (Math.abs(y + height - visualBounds.getMaxY()) < SNAP_THRESHOLD) {
            snappedY = visualBounds.getMaxY() - height;
        }
        
        // Width Snapping
        // Vollbreite des visuellen Bereichs
        if (Math.abs(width - visualBounds.getWidth()) < SNAP_THRESHOLD) {
            snappedWidth = visualBounds.getWidth();
            snappedX = visualBounds.getMinX();
        }
        // Hälfte des visuellen Bereichs
        else if (Math.abs(width - (visualBounds.getWidth() / 2)) < SNAP_THRESHOLD) {
            snappedWidth = visualBounds.getWidth() / 2;
        }
        
        // Height Snapping
        // Vollhöhe des visuellen Bereichs
        if (Math.abs(height - visualBounds.getHeight()) < SNAP_THRESHOLD) {
            snappedHeight = visualBounds.getHeight();
            snappedY = visualBounds.getMinY();
        }
        // Hälfte des visuellen Bereichs
        else if (Math.abs(height - (visualBounds.getHeight() / 2)) < SNAP_THRESHOLD) {
            snappedHeight = visualBounds.getHeight() / 2;
        }
        
        return new double[]{snappedX, snappedY, snappedWidth, snappedHeight};
    }
    
    /**
     * Begrenzt die Fensterdimensionen auf den sichtbaren Bereich des aktuellen Screens
     * @param x Gewünschte X-Position
     * @param y Gewünschte Y-Position
     * @param width Gewünschte Breite
     * @param height Gewünschte Höhe
     * @return Array mit [constrainedX, constrainedY, constrainedWidth, constrainedHeight]
     */
    private double[] constrainToScreen(double x, double y, double width, double height) {
        if (window == null) {
            return new double[]{x, y, width, height};
        }
        
        // Aktuellen Screen ermitteln
        var currentScreen = ScreenDetector.getCurrentScreen(window);
        var screenBounds = currentScreen.getBounds();
        var visualBounds = currentScreen.getVisualBounds();
        
        // Mindestgrößen
        double minWidth = 300.0; // Standard-Mindestbreite
        double minHeight = 200.0; // Standard-Mindesthöhe
        
        // Wenn Window eine Stage ist und Mindestgrößen hat, diese verwenden
        if (window instanceof Stage) {
            Stage stage = (Stage) window;
            minWidth = Math.max(minWidth, stage.getMinWidth());
            minHeight = Math.max(minHeight, stage.getMinHeight());
        }
        
        // Position begrenzen
        double constrainedX = Math.max(visualBounds.getMinX(), Math.min(x, visualBounds.getMaxX() - minWidth));
        double constrainedY = Math.max(visualBounds.getMinY(), Math.min(y, visualBounds.getMaxY() - minHeight));
        
        // Größe begrenzen
        double constrainedWidth = Math.max(minWidth, Math.min(width, visualBounds.getWidth()));
        double constrainedHeight = Math.max(minHeight, Math.min(height, visualBounds.getHeight()));
        
        // Sicherstellen, dass das Fenster nicht außerhalb liegt
        if (constrainedX + constrainedWidth > visualBounds.getMaxX()) {
            constrainedWidth = visualBounds.getMaxX() - constrainedX;
        }
        if (constrainedY + constrainedHeight > visualBounds.getMaxY()) {
            constrainedHeight = visualBounds.getMaxY() - constrainedY;
        }
        
        return new double[]{constrainedX, constrainedY, constrainedWidth, constrainedHeight};
    }
    
    /**
     * Gibt die Größe des Resize-Borders zurück (macOS-spezifisch)
     * @return Border-Größe in Pixeln
     */
    @Override
    protected int getResizeBorderSize() {
        return MAC_RESIZE_BORDER;
    }
    
    /**
     * Prüft, ob Resize für das Fenster erlaubt ist (macOS-spezifisch)
     * @return true wenn Resize erlaubt ist
     */
    @Override
    protected boolean canResize() {
        if (!super.canResize()) {
            return false;
        }
        
        // Zusätzliche macOS-Prüfungen
        if (window == null) {
            return false;
        }
        
        // Prüfen, ob das Fenster im Vollbildmodus ist (macOS Full Screen)
        if (window instanceof Stage) {
            Stage stage = (Stage) window;
            if (stage.isFullScreen()) {
                return false;
            }
        }
        
        // Prüfen, ob das Fenster minimiert ist
        if (window instanceof Stage) {
            Stage stage = (Stage) window;
            if (stage.isIconified()) {
                return false;
            }
        }
        
        // Prüfen, ob das Fenster eine Mindestgröße hat
        if (window.getWidth() <= 0 || window.getHeight() <= 0) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Ermittelt die Resize-Richtung mit macOS-spezifischer Logik
     * @param event Das MouseEvent
     * @param sceneX X-Position in der Scene
     * @param sceneY Y-Position in der Scene
     * @return Die Resize-Richtung
     */
    @Override
    protected String determineResizeDirection(MouseEvent event, double sceneX, double sceneY) {
        if (window == null) {
            return "";
        }
        
        double mouseX = sceneX;
        double mouseY = sceneY;
        double windowWidth = window.getWidth();
        double windowHeight = window.getHeight();
        
        int resizeBorder = getResizeBorderSize();
        
        // Eck-Bereiche auf macOS etwas größer für bessere Ergonomie
        int cornerSize = resizeBorder + 2;
        
        boolean atTop = mouseY <= cornerSize;
        boolean atBottom = mouseY >= windowHeight - cornerSize;
        boolean atLeft = mouseX <= cornerSize;
        boolean atRight = mouseX >= windowWidth - cornerSize;
        
        // Ecken haben absolute Priorität auf macOS
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
     * Ermittelt die Resize-Richtung (Legacy-Methode mit MouseEvent)
     * @param event Das MouseEvent
     * @return Die Resize-Richtung
     */
    public String determineResizeDirection(MouseEvent event) {
        // Diese Methode wird von außen aufgerufen, wir verwenden Scene-Koordinaten
        return determineResizeDirection(event, event.getSceneX(), event.getSceneY());
    }
}
