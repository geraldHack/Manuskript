package com.manuskript.windowhandling.platform;

import com.manuskript.windowhandling.DragHandler;
import com.manuskript.windowhandling.ScreenDetector;
import javafx.scene.input.MouseEvent;
import javafx.stage.Window;
import javafx.stage.Stage;
import javafx.stage.Screen;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * macOS-spezifische Implementierung des Drag-Handlers
 * Berücksichtigt Besonderheiten von macOS wie Menüleiste und Dock
 */
public class MacDragHandler extends DragHandler {
    private static final Logger LOGGER = Logger.getLogger(MacDragHandler.class.getName());
    
    private static final double SNAP_THRESHOLD = 15.0; // Pixels für Edge-Snapping
    private boolean snapToEdges = true;
    
    /**
     * Konstruktor
     * @param window Das zu bewegende Fenster
     */
    public MacDragHandler(Window window) {
        super(window);
    }
    
    /**
     * Aktiviert/Deaktiviert Edge-Snapping
     * @param snapToEdges true für Edge-Snapping
     */
    public void setSnapToEdges(boolean snapToEdges) {
        this.snapToEdges = snapToEdges;
    }
    
    @Override
    public void handleMousePressed(MouseEvent event) {
        if (!canDrag() || !shouldHandleDragEvent(event)) {
            return;
        }
        
        try {
            // Offset für Dragging berechnen
            if (window != null) {
                xOffset = event.getSceneX();
                yOffset = event.getSceneY();
                isDragging = true;
                
                notifyDragStart(event);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error in handleMousePressed", e);
        }
    }
    
    @Override
    public void handleMouseDragged(MouseEvent event) {
        if (!isDragging || !canDrag()) {
            return;
        }
        
        // Neue Position berechnen
        double[] newPos = calculateNewPosition(event);
        double newX = newPos[0];
        double newY = newPos[1];
        
        // Edge-Snapping auf macOS anwenden
        if (snapToEdges) {
            double[] snappedPos = applyMacEdgeSnapping(newX, newY);
            newX = snappedPos[0];
            newY = snappedPos[1];
        }
        
        // Fensterposition aktualisieren
        setWindowPosition(newX, newY);
        
        // Event konsumieren
        event.consume();
        
        // Listener benachrichtigen
        notifyDrag(event);
    }
    
    @Override
    public void handleMouseReleased(MouseEvent event) {
        if (!isDragging) {
            return;
        }
        
        // Drag-Status zurücksetzen
        isDragging = false;
        
        // Event konsumieren
        event.consume();
        
        // Listener benachrichtigen
        notifyDragEnd(event);
    }
    
    /**
     * Wendet macOS-spezifisches Edge-Snapping an
     * Berücksichtigt Menüleiste (oben) und Dock (unten/seitlich)
     * @param x Aktuelle X-Position
     * @param y Aktuelle Y-Position
     * @return Array mit [snappedX, snappedY]
     */
    private double[] applyMacEdgeSnapping(double x, double y) {
        if (window == null) {
            return new double[]{x, y};
        }
        
        // Aktuellen Screen und seine Bounds ermitteln
        var currentScreen = ScreenDetector.getCurrentScreen(window);
        var screenBounds = currentScreen.getBounds();
        var visualBounds = currentScreen.getVisualBounds();
        var windowWidth = window.getWidth();
        var windowHeight = window.getHeight();
        
        double snappedX = x;
        double snappedY = y;
        
        // Horizontal snapping
        // Linker Rand
        if (Math.abs(x - screenBounds.getMinX()) < SNAP_THRESHOLD) {
            snappedX = screenBounds.getMinX();
        }
        // Rechter Rand
        else if (Math.abs(x + windowWidth - screenBounds.getMaxX()) < SNAP_THRESHOLD) {
            snappedX = screenBounds.getMaxX() - windowWidth;
        }
        // Linker visueller Rand (unter Menüleiste)
        else if (Math.abs(x - visualBounds.getMinX()) < SNAP_THRESHOLD) {
            snappedX = visualBounds.getMinX();
        }
        // Rechter visueller Rand
        else if (Math.abs(x + windowWidth - visualBounds.getMaxX()) < SNAP_THRESHOLD) {
            snappedX = visualBounds.getMaxX() - windowWidth;
        }
        
        // Vertikal snapping
        // Oberer Rand (Berücksichtigt Menüleiste)
        if (Math.abs(y - visualBounds.getMinY()) < SNAP_THRESHOLD) {
            snappedY = visualBounds.getMinY();
        }
        // Unterer Rand (Berücksichtigt Dock)
        else if (Math.abs(y + windowHeight - visualBounds.getMaxY()) < SNAP_THRESHOLD) {
            snappedY = visualBounds.getMaxY() - windowHeight;
        }
        // Oberer voller Rand
        else if (Math.abs(y - screenBounds.getMinY()) < SNAP_THRESHOLD) {
            snappedY = screenBounds.getMinY();
        }
        // Unterer voller Rand
        else if (Math.abs(y + windowHeight - screenBounds.getMaxY()) < SNAP_THRESHOLD) {
            snappedY = screenBounds.getMaxY() - windowHeight;
        }
        
        // Center snapping (horizontal)
        double centerX = screenBounds.getMinX() + (screenBounds.getWidth() - windowWidth) / 2;
        if (Math.abs(x - centerX) < SNAP_THRESHOLD) {
            snappedX = centerX;
        }
        
        // Center snapping (vertikal)
        double centerY = visualBounds.getMinY() + (visualBounds.getHeight() - windowHeight) / 2;
        if (Math.abs(y - centerY) < SNAP_THRESHOLD) {
            snappedY = centerY;
        }
        
        return new double[]{snappedX, snappedY};
    }
    
    /**
     * Prüft, ob das Drag-Event behandelt werden sollte
     * @param event Das MouseEvent
     * @return true wenn das Event behandelt werden sollte
     */
    public boolean shouldHandleDragEvent(MouseEvent event) {
        return canDrag() && event.getClickCount() == 1;
    }
    
    /**
     * Prüft, ob Dragging für das Fenster erlaubt ist (macOS-spezifisch)
     * @return true wenn Dragging erlaubt ist
     */
    @Override
    protected boolean canDrag() {
        if (!super.canDrag()) {
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
        
        return true;
    }
    
    /**
     * macOS-spezifische Logik für die Positionsberechnung
     * Berücksichtigt Retina-Displays und Skalierung
     * @param event Das MouseEvent
     * @return Array mit [newX, newY]
     */
    @Override
    protected double[] calculateNewPosition(MouseEvent event) {
        if (window == null) {
            return new double[]{0, 0};
        }
        
        double newX = event.getScreenX() - xOffset;
        double newY = event.getScreenY() - yOffset;
        
        // Multi-Monitor-Unterstützung - Fenster kann über alle Monitore bewegt werden
        // Keine Begrenzung auf aktuellen Screen für Dragging!
        
        // Nur minimale Bounds, um Fenster komplett vom Bildschirm zu verhindern
        // Aber erlaube Bewegung über alle Monitore
        var allScreens = Screen.getScreens();
        if (!allScreens.isEmpty()) {
            // Berechne Gesamt-Bounds aller Monitore
            double minX = Double.MAX_VALUE;
            double minY = Double.MAX_VALUE;
            double maxX = Double.MIN_VALUE;
            double maxY = Double.MIN_VALUE;
            
            for (var screen : allScreens) {
                var bounds = screen.getBounds();
                minX = Math.min(minX, bounds.getMinX());
                minY = Math.min(minY, bounds.getMinY());
                maxX = Math.max(maxX, bounds.getMaxX());
                maxY = Math.max(maxY, bounds.getMaxY());
            }
            
            // Fenster im sichtbaren Bereich halten (mit 50px Buffer)
            double windowWidth = window.getWidth();
            double windowHeight = window.getHeight();
            
            newX = Math.max(minX + 50, Math.min(newX, maxX - windowWidth - 50));
            newY = Math.max(minY + 50, Math.min(newY, maxY - windowHeight - 50));
        }
        
        return new double[]{newX, newY};
    }
}
