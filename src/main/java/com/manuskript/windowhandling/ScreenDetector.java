package com.manuskript.windowhandling;

import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Window;
import java.util.List;

/**
 * Utility-Klasse zur Erkennung und Verwaltung von Multi-Monitor-Setups
 * Speziell optimiert für macOS mit Unterstützung für verschiedene Bildschirmkonfigurationen
 */
public class ScreenDetector {
    
    /**
     * Ermittelt den Screen, auf dem sich das Fenster derzeit befindet
     * @param window Das zu überprüfende Fenster
     * @return Der Screen, der das Fenster enthält, oder Primary Screen als Fallback
     */
    public static Screen getCurrentScreen(Window window) {
        if (window == null) {
            return Screen.getPrimary();
        }
        
        // Fenster-Position und -Größe ermitteln
        double windowX = window.getX();
        double windowY = window.getY();
        double windowWidth = window.getWidth();
        double windowHeight = window.getHeight();
        
        // Rectangle für das Fenster erstellen
        Rectangle2D windowRect = new Rectangle2D(windowX, windowY, windowWidth, windowHeight);
        
        // Alle verfügbaren Screens durchgehen
        List<Screen> screens = Screen.getScreens();
        for (Screen screen : screens) {
            Rectangle2D screenBounds = screen.getBounds();
            
            // Prüfen, ob das Fenster sich auf diesem Screen befindet
            // Wir verwenden eine etwas großzügigere Prüfung für Multi-Monitor-Setups
            if (intersects(windowRect, screenBounds, 0.1)) { // 10% Überlappung genügt
                return screen;
            }
        }
        
        // Fallback: Screen mit größter Überlappung
        return getScreenWithMaxOverlap(windowRect, screens);
    }
    
    /**
     * Ermittelt die Visual Bounds des aktuellen Screens (ohne Dock/Menu Bar auf macOS)
     * @param window Das Fenster
     * @return Visual Bounds des aktuellen Screens
     */
    public static Rectangle2D getCurrentScreenVisualBounds(Window window) {
        Screen currentScreen = getCurrentScreen(window);
        return currentScreen.getVisualBounds();
    }
    
    /**
     * Ermittelt die Full Bounds des aktuellen Screens (komplette Bildschirmfläche)
     * @param window Das Fenster
     * @return Full Bounds des aktuellen Screens
     */
    public static Rectangle2D getCurrentScreenBounds(Window window) {
        Screen currentScreen = getCurrentScreen(window);
        return currentScreen.getBounds();
    }
    
    /**
     * Prüft, ob sich zwei Rechtecke überschneiden
     * @param rect1 Erstes Rechteck
     * @param rect2 Zweites Rechteck
     * @param minOverlap Mindestüberlappung in Prozent (0.0-1.0)
     * @return true wenn sich die Rechtecke ausreichend überschneiden
     */
    private static boolean intersects(Rectangle2D rect1, Rectangle2D rect2, double minOverlap) {
        if (!rect1.intersects(rect2)) {
            return false;
        }
        
        // Überlappungsfläche berechnen
        double overlapX = Math.max(0, Math.min(rect1.getMaxX(), rect2.getMaxX()) - Math.max(rect1.getMinX(), rect2.getMinX()));
        double overlapY = Math.max(0, Math.min(rect1.getMaxY(), rect2.getMaxY()) - Math.max(rect1.getMinY(), rect2.getMinY()));
        double overlapArea = overlapX * overlapY;
        
        // Flächen berechnen
        double rect1Area = rect1.getWidth() * rect1.getHeight();
        
        // Mindestüberlappung prüfen
        return rect1Area > 0 && (overlapArea / rect1Area) >= minOverlap;
    }
    
    /**
     * Ermittelt den Screen mit der größten Überlappung zum Fenster
     * @param windowRect Fenster-Rechteck
     * @param screens Liste aller Screens
     * @return Screen mit größter Überlappung
     */
    private static Screen getScreenWithMaxOverlap(Rectangle2D windowRect, List<Screen> screens) {
        Screen maxOverlapScreen = Screen.getPrimary();
        double maxOverlapArea = 0;
        
        for (Screen screen : screens) {
            Rectangle2D screenBounds = screen.getBounds();
            
            // Überlappungsfläche berechnen
            double overlapX = Math.max(0, Math.min(windowRect.getMaxX(), screenBounds.getMaxX()) - Math.max(windowRect.getMinX(), screenBounds.getMinX()));
            double overlapY = Math.max(0, Math.min(windowRect.getMaxY(), screenBounds.getMaxY()) - Math.max(windowRect.getMinY(), screenBounds.getMinY()));
            double overlapArea = overlapX * overlapY;
            
            if (overlapArea > maxOverlapArea) {
                maxOverlapArea = overlapArea;
                maxOverlapScreen = screen;
            }
        }
        
        return maxOverlapScreen;
    }
    
    /**
     * Prüft, ob ein Fenster auf dem primären Bildschirm maximiert ist
     * @param window Das zu überprüfende Fenster
     * @return true wenn das Fenster auf dem primären Screen maximiert ist
     */
    public static boolean isMaximizedOnPrimaryScreen(Window window) {
        if (window == null) return false;
        
        Rectangle2D windowBounds = new Rectangle2D(window.getX(), window.getY(), window.getWidth(), window.getHeight());
        Rectangle2D primaryVisualBounds = Screen.getPrimary().getVisualBounds();
        
        // Prüfen, ob Fenster die Visual Bounds des primären Screens ausfüllt
        return Math.abs(windowBounds.getWidth() - primaryVisualBounds.getWidth()) < 10 &&
               Math.abs(windowBounds.getHeight() - primaryVisualBounds.getHeight()) < 10 &&
               Math.abs(windowBounds.getMinX() - primaryVisualBounds.getMinX()) < 10 &&
               Math.abs(windowBounds.getMinY() - primaryVisualBounds.getMinY()) < 10;
    }
    
    /**
     * Prüft, ob ein Fenster auf seinem aktuellen Screen maximiert ist
     * @param window Das zu überprüfende Fenster
     * @return true wenn das Fenster auf seinem aktuellen Screen maximiert ist
     */
    public static boolean isMaximizedOnCurrentScreen(Window window) {
        if (window == null) return false;
        
        Rectangle2D windowBounds = new Rectangle2D(window.getX(), window.getY(), window.getWidth(), window.getHeight());
        Rectangle2D currentVisualBounds = getCurrentScreenVisualBounds(window);
        
        // Prüfen, ob Fenster die Visual Bounds des aktuellen Screens ausfüllt
        return Math.abs(windowBounds.getWidth() - currentVisualBounds.getWidth()) < 10 &&
               Math.abs(windowBounds.getHeight() - currentVisualBounds.getHeight()) < 10 &&
               Math.abs(windowBounds.getMinX() - currentVisualBounds.getMinX()) < 10 &&
               Math.abs(windowBounds.getMinY() - currentVisualBounds.getMinY()) < 10;
    }
    
    /**
     * Gibt Informationen über alle verfügbaren Screens zurück (für Debugging)
     * @return String mit Screen-Informationen
     */
    public static String getScreenInfo() {
        StringBuilder info = new StringBuilder();
        List<Screen> screens = Screen.getScreens();
        
        info.append("Available Screens: ").append(screens.size()).append("\n");
        
        for (int i = 0; i < screens.size(); i++) {
            Screen screen = screens.get(i);
            Rectangle2D bounds = screen.getBounds();
            Rectangle2D visualBounds = screen.getVisualBounds();
            
            info.append("Screen ").append(i).append(":\n");
            info.append("  Bounds: ").append(bounds).append("\n");
            info.append("  Visual Bounds: ").append(visualBounds).append("\n");
            info.append("  DPI: ").append(screen.getDpi()).append("\n");
            info.append("  Scale: ").append(screen.getOutputScaleX()).append("x").append(screen.getOutputScaleY()).append("\n");
        }
        
        return info.toString();
    }
}
