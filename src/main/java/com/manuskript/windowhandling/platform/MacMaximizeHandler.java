package com.manuskript.windowhandling.platform;

import com.manuskript.windowhandling.MaximizeHandler;
import com.manuskript.windowhandling.ScreenDetector;
import javafx.geometry.Rectangle2D;
import javafx.stage.Window;

/**
 * macOS-spezifische Implementierung des Maximize-Handlers
 * Berücksichtigt Multi-Monitor-Setups und macOS-Besonderheiten
 */
public class MacMaximizeHandler extends MaximizeHandler {
    
    private boolean useVisualBounds = true; // Standard: Visual Bounds (ohne Menüleiste/Dock)
    private boolean enableAnimation = true;
    private long animationDuration = 200; // ms
    
    /**
     * Konstruktor
     * @param window Das zu maximierende Fenster
     */
    public MacMaximizeHandler(Window window) {
        super(window);
    }
    
    /**
     * Aktiviert/Deaktiviert die Verwendung von Visual Bounds statt Full Bounds
     * @param useVisualBounds true für Visual Bounds (ohne Menüleiste/Dock)
     */
    public void setUseVisualBounds(boolean useVisualBounds) {
        this.useVisualBounds = useVisualBounds;
    }
    
    /**
     * Aktiviert/Deaktiviert Animation beim Maximieren/Wiederherstellen
     * @param enableAnimation true für Animation
     */
    public void setEnableAnimation(boolean enableAnimation) {
        this.enableAnimation = enableAnimation;
    }
    
    /**
     * Setzt die Animationsdauer in Millisekunden
     * @param duration Animationsdauer
     */
    public void setAnimationDuration(long duration) {
        this.animationDuration = duration;
    }
    
    @Override
    public void maximize() {
        if (!canMaximize()) {
            return;
        }
        
        // Aktuellen Zustand speichern
        saveCurrentState();
        
        // Ziel-Bounds ermitteln
        Rectangle2D targetBounds = getTargetBounds();
        
        if (targetBounds != null) {
            // Fenster maximieren
            if (enableAnimation) {
                animateMaximize(targetBounds);
            } else {
                setWindowBounds(targetBounds);
            }
            
            wasMaximized = true;
            notifyMaximize();
        }
    }
    
    @Override
    public void restore() {
        if (!canRestore() || !hasValidPreviousState()) {
            return;
        }
        
        // Prüfen, ob das Fenster tatsächlich maximiert ist
        if (!isCurrentlyMaximized()) {
            return;
        }
        
        // Vorherigen Zustand wiederherstellen
        if (enableAnimation) {
            animateRestore();
        } else {
            restorePreviousState();
        }
        
        wasMaximized = false;
        notifyRestore();
    }
    
    @Override
    public void toggleMaximize() {
        if (isCurrentlyMaximized()) {
            restore();
        } else {
            maximize();
        }
    }
    
    @Override
    public boolean isMaximized() {
        return isCurrentlyMaximized();
    }
    
    /**
     * Ermittelt die Ziel-Bounds für das Maximieren
     * @return Rectangle2D mit den Ziel-Bounds
     */
    private Rectangle2D getTargetBounds() {
        if (window == null) {
            return null;
        }
        
        // Aktuellen Screen des Fensters ermitteln
        var currentScreen = ScreenDetector.getCurrentScreen(window);
        
        // Standardmäßig Visual Bounds verwenden (ohne Menüleiste/Dock)
        if (useVisualBounds) {
            return currentScreen.getVisualBounds();
        } else {
            return currentScreen.getBounds();
        }
    }
    
    /**
     * Setzt die Fenster-Bounds
     * @param bounds Die neuen Bounds
     */
    private void setWindowBounds(Rectangle2D bounds) {
        if (window != null) {
            window.setX(bounds.getMinX());
            window.setY(bounds.getMinY());
            window.setWidth(bounds.getWidth());
            window.setHeight(bounds.getHeight());
        }
    }
    
    /**
     * Prüft, ob das Fenster aktuell maximiert ist
     * @return true wenn das Fenster maximiert ist
     */
    private boolean isCurrentlyMaximized() {
        if (window == null) {
            return false;
        }
        
        // Methode 1: ScreenDetector verwenden
        boolean isMaximizedOnCurrent = ScreenDetector.isMaximizedOnCurrentScreen(window);
        
        // Methode 2: Manuelle Prüfung als Fallback
        Rectangle2D windowBounds = new Rectangle2D(window.getX(), window.getY(), window.getWidth(), window.getHeight());
        Rectangle2D targetBounds = getTargetBounds();
        
        boolean isMaximizedManual = Math.abs(windowBounds.getWidth() - targetBounds.getWidth()) < 10 &&
                                   Math.abs(windowBounds.getHeight() - targetBounds.getHeight()) < 10 &&
                                   Math.abs(windowBounds.getMinX() - targetBounds.getMinX()) < 10 &&
                                   Math.abs(windowBounds.getMinY() - targetBounds.getMinY()) < 10;
        
        return isMaximizedOnCurrent || isMaximizedManual || wasMaximized;
    }
    
    /**
     * Animiert das Maximieren des Fensters
     * @param targetBounds Ziel-Bounds
     */
    private void animateMaximize(Rectangle2D targetBounds) {
        if (window == null) {
            return;
        }
        
        // Start-Bounds
        double startX = window.getX();
        double startY = window.getY();
        double startWidth = window.getWidth();
        double startHeight = window.getHeight();
        
        // Ziel-Bounds
        double endX = targetBounds.getMinX();
        double endY = targetBounds.getMinY();
        double endWidth = targetBounds.getWidth();
        double endHeight = targetBounds.getHeight();
        
        // Animation starten
        long startTime = System.currentTimeMillis();
        
        Thread animationThread = new Thread(() -> {
            try {
                while (true) {
                    long currentTime = System.currentTimeMillis();
                    long elapsed = currentTime - startTime;
                    
                    if (elapsed >= animationDuration) {
                        // Animation beenden
                        javafx.application.Platform.runLater(() -> setWindowBounds(targetBounds));
                        break;
                    }
                    
                    // Fortschritt berechnen (0.0 - 1.0)
                    double progress = (double) elapsed / animationDuration;
                    
                    // Ease-out animation
                    progress = 1.0 - Math.pow(1.0 - progress, 3);
                    
                    // Zwischenwerte berechnen
                    double currentX = startX + (endX - startX) * progress;
                    double currentY = startY + (endY - startY) * progress;
                    double currentWidth = startWidth + (endWidth - startWidth) * progress;
                    double currentHeight = startHeight + (endHeight - startHeight) * progress;
                    
                    // Fenster aktualisieren
                    javafx.application.Platform.runLater(() -> {
                        if (window != null) {
                            window.setX(currentX);
                            window.setY(currentY);
                            window.setWidth(currentWidth);
                            window.setHeight(currentHeight);
                        }
                    });
                    
                    // Warten für nächste Frame
                    Thread.sleep(16); // ~60 FPS
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        animationThread.setDaemon(true);
        animationThread.start();
    }
    
    /**
     * Animiert das Wiederherstellen des Fensters
     */
    private void animateRestore() {
        if (window == null || !hasValidPreviousState()) {
            return;
        }
        
        // Start-Bounds (aktuell maximiert)
        Rectangle2D startBounds = getTargetBounds();
        if (startBounds == null) {
            restorePreviousState();
            return;
        }
        
        double startX = startBounds.getMinX();
        double startY = startBounds.getMinY();
        double startWidth = startBounds.getWidth();
        double startHeight = startBounds.getHeight();
        
        // Ziel-Bounds (vorheriger Zustand)
        double endX = previousX;
        double endY = previousY;
        double endWidth = previousWidth;
        double endHeight = previousHeight;
        
        // Animation starten
        long startTime = System.currentTimeMillis();
        
        Thread animationThread = new Thread(() -> {
            try {
                while (true) {
                    long currentTime = System.currentTimeMillis();
                    long elapsed = currentTime - startTime;
                    
                    if (elapsed >= animationDuration) {
                        // Animation beenden
                        javafx.application.Platform.runLater(this::restorePreviousState);
                        break;
                    }
                    
                    // Fortschritt berechnen (0.0 - 1.0)
                    double progress = (double) elapsed / animationDuration;
                    
                    // Ease-out animation
                    progress = 1.0 - Math.pow(1.0 - progress, 3);
                    
                    // Zwischenwerte berechnen
                    double currentX = startX + (endX - startX) * progress;
                    double currentY = startY + (endY - startY) * progress;
                    double currentWidth = startWidth + (endWidth - startWidth) * progress;
                    double currentHeight = startHeight + (endHeight - startHeight) * progress;
                    
                    // Fenster aktualisieren
                    javafx.application.Platform.runLater(() -> {
                        if (window != null) {
                            window.setX(currentX);
                            window.setY(currentY);
                            window.setWidth(currentWidth);
                            window.setHeight(currentHeight);
                        }
                    });
                    
                    // Warten für nächste Frame
                    Thread.sleep(16); // ~60 FPS
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        animationThread.setDaemon(true);
        animationThread.start();
    }
    
    /**
     * Prüft, ob das Fenster maximiert werden kann (macOS-spezifisch)
     * @return true wenn Maximieren möglich ist
     */
    @Override
    protected boolean canMaximize() {
        if (!super.canMaximize()) {
            return false;
        }
        
        // Zusätzliche macOS-Prüfungen
        if (window == null) {
            return false;
        }
        
        // Prüfen, ob das Fenster bereits maximiert ist
        if (isCurrentlyMaximized()) {
            return false;
        }
        
        // Prüfen, ob das Fenster eine gültige Größe hat
        if (window.getWidth() <= 0 || window.getHeight() <= 0) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Prüft, ob das Fenster wiederhergestellt werden kann (macOS-spezifisch)
     * @return true wenn Wiederherstellen möglich ist
     */
    @Override
    protected boolean canRestore() {
        if (!super.canRestore()) {
            return false;
        }
        
        // Zusätzliche macOS-Prüfungen
        if (window == null) {
            return false;
        }
        
        // Prüfen, ob das Fenster tatsächlich maximiert ist
        if (!isCurrentlyMaximized()) {
            return false;
        }
        
        // Prüfen, ob ein gültiger vorheriger Zustand gespeichert ist
        if (!hasValidPreviousState()) {
            return false;
        }
        
        return true;
    }
}
