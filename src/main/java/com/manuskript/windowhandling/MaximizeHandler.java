package com.manuskript.windowhandling;

import javafx.stage.Window;
import javafx.stage.Stage;

/**
 * Basisklasse für Maximize-Handling von Fenstern
 * Abstrakte Basis für platform-spezifische Implementierungen
 */
public abstract class MaximizeHandler {
    
    protected Window window;
    protected MaximizeListener maximizeListener;
    
    // gespeicherter Zustand vor dem Maximieren
    protected double previousX = 0;
    protected double previousY = 0;
    protected double previousWidth = 0;
    protected double previousHeight = 0;
    protected boolean wasMaximized = false;
    
    /**
     * Interface für Benachrichtigungen während des Maximize-Vorgangs
     */
    public interface MaximizeListener {
        void onMaximize();
        void onRestore();
        void onMaximizeStateChanged(boolean isMaximized);
    }
    
    /**
     * Konstruktor
     * @param window Das zu maximierende Fenster
     */
    public MaximizeHandler(Window window) {
        this.window = window;
        initializePreviousState();
    }
    
    /**
     * Setzt einen Listener für Maximize-Ereignisse
     * @param listener Der Listener
     */
    public void setMaximizeListener(MaximizeListener listener) {
        this.maximizeListener = listener;
    }
    
    /**
     * Muss von der platform-spezifischen Implementierung überschrieben werden
     * Maximiert das Fenster auf dem aktuellen Screen
     */
    public abstract void maximize();
    
    /**
     * Muss von der platform-spezifischen Implementierung überschrieben werden
     * Stellt das Fenster auf die vorherige Größe wieder her
     */
    public abstract void restore();
    
    /**
     * Muss von der platform-spezifischen Implementierung überschrieben werden
     * Schaltet zwischen maximiert und normal hin und her
     */
    public abstract void toggleMaximize();
    
    /**
     * Prüft, ob das Fenster aktuell maximiert ist
     * @return true wenn das Fenster maximiert ist
     */
    public abstract boolean isMaximized();
    
    /**
     * Speichert den aktuellen Fensterzustand vor dem Maximieren
     */
    protected void saveCurrentState() {
        if (window != null) {
            previousX = window.getX();
            previousY = window.getY();
            previousWidth = window.getWidth();
            previousHeight = window.getHeight();
        }
    }
    
    /**
     * Initialisiert den vorherigen Zustand mit den aktuellen Werten
     */
    protected void initializePreviousState() {
        saveCurrentState();
    }
    
    /**
     * Stellt den vorherigen Zustand wieder her
     */
    protected void restorePreviousState() {
        if (window != null) {
            window.setX(previousX);
            window.setY(previousY);
            window.setWidth(previousWidth);
            window.setHeight(previousHeight);
        }
    }
    
    /**
     * Prüft, ob das Fenster maximiert werden kann
     * @return true wenn Maximieren möglich ist
     */
    protected boolean canMaximize() {
        if (window == null) {
            return false;
        }
        
        // Nur für Stage Typen prüfen, da Window diese Methoden nicht hat
        if (window instanceof Stage) {
            Stage stage = (Stage) window;
            return !stage.isFullScreen() && !stage.isIconified();
        }
        
        // Für andere Window-Typen nur Basis-Prüfungen
        return true;
    }
    
    /**
     * Prüft, ob das Fenster wiederhergestellt werden kann
     * @return true wenn Wiederherstellen möglich ist
     */
    protected boolean canRestore() {
        if (window == null) {
            return false;
        }
        
        // Nur für Stage Typen prüfen, da Window diese Methoden nicht hat
        if (window instanceof Stage) {
            Stage stage = (Stage) window;
            return !stage.isFullScreen() && !stage.isIconified();
        }
        
        // Für andere Window-Typen nur Basis-Prüfungen
        return true;
    }
    
    /**
     * Benachrichtigt den Listener über Maximieren
     */
    protected void notifyMaximize() {
        if (maximizeListener != null) {
            maximizeListener.onMaximize();
            maximizeListener.onMaximizeStateChanged(true);
        }
    }
    
    /**
     * Benachrichtigt den Listener über Wiederherstellen
     */
    protected void notifyRestore() {
        if (maximizeListener != null) {
            maximizeListener.onRestore();
            maximizeListener.onMaximizeStateChanged(false);
        }
    }
    
    /**
     * Gibt den vorherigen Zustand zurück
     * @return Array mit [previousX, previousY, previousWidth, previousHeight]
     */
    public double[] getPreviousState() {
        return new double[]{previousX, previousY, previousWidth, previousHeight};
    }
    
    /**
     * Setzt den vorherigen Zustand manuell
     * @param x X-Position
     * @param y Y-Position
     * @param width Breite
     * @param height Höhe
     */
    public void setPreviousState(double x, double y, double width, double height) {
        this.previousX = x;
        this.previousY = y;
        this.previousWidth = width;
        this.previousHeight = height;
    }
    
    /**
     * Prüft, ob ein gültiger vorheriger Zustand gespeichert ist
     * @return true wenn ein gültiger Zustand gespeichert ist
     */
    protected boolean hasValidPreviousState() {
        return previousWidth > 0 && previousHeight > 0;
    }
}
