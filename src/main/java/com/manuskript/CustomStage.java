package com.manuskript;

import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.geometry.Pos;
import javafx.scene.input.MouseEvent;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.manuskript.windowhandling.MacWindowManager;

/**
 * Eigene Stage-Klasse mit benutzerdefinierter Titelleiste
 */
public class CustomStage extends Stage {
    
    private static final Logger logger = LoggerFactory.getLogger(CustomStage.class);
    
    // Flag, um zu verhindern, dass der Cursor überschrieben wird (z.B. während Export)
    private boolean cursorLocked = false;
    
    // macOS Window Manager für verbessertes Handling
    private MacWindowManager macWindowManager;
    private boolean useMacWindowManager = false;
    
    private static final String DEFAULT_TEXT_COLOR = "white";
    private static final String DEFAULT_BORDER_COLOR = "#1a252f";
    private static final String DEFAULT_ICON_BACKGROUND = "#3498db";
    private static final String DEFAULT_TITLEBAR_STYLE =
            "-fx-background-color: linear-gradient(from 0% 0% to 0% 100%, #2c3e50 0%, #34495e 100%); " +
            "-fx-padding: 5px; -fx-spacing: 5px; -fx-border-color: " + DEFAULT_BORDER_COLOR + "; -fx-border-width: 0 0 1 0;";
    private static final String DEFAULT_BUTTON_BACKGROUND = "transparent";
    private static final String DEFAULT_HOVER_BACKGROUND = "#34495e";
    private static final String BUTTON_STYLE_TEMPLATE =
            "-fx-background-color: %s; -fx-text-fill: %s; -fx-font-weight: bold; " +
            "-fx-font-size: %dpx; -fx-min-width: %spx; -fx-min-height: %spx; -fx-pref-width: %spx; -fx-pref-height: %spx; -fx-border-color: transparent; -fx-border-radius: 4px; -fx-background-radius: 4px;";
    private static final String DEFAULT_MINIMIZE_SYMBOL = "−";
    private static final String DEFAULT_MAXIMIZE_SYMBOL = "□";
    private static final String DEFAULT_MAXIMIZE_SYMBOL_MAXIMIZED = "⧉";
    private static final String MAC_MAXIMIZE_SYMBOL = "⤢";
    private static final String DEFAULT_CLOSE_SYMBOL = "×";
    private static final String DEFAULT_TITLE = "Manuskript";
    private static final String DEFAULT_ICON_TEXT = "M";
    private static final double ICON_SIZE = 30.0;
    private static final double MAC_BUTTON_SIZE = 14.0;
    private static final double MAC_BUTTON_SPACING = 8.0;
    private static final double MAC_SYMBOL_SIZE = 12.0;
    private static final String MAC_CLOSE_COLOR = "#ff5f57";
    private static final String MAC_CLOSE_HOVER_COLOR = "#ff3b30";
    private static final String MAC_CLOSE_TEXT_COLOR = "#ffffff";
    private static final String MAC_MINIMIZE_COLOR = "#ffbd2e";
    private static final String MAC_MINIMIZE_HOVER_COLOR = "#ff9500";
    private static final String MAC_MINIMIZE_TEXT_COLOR = "#ffffff";
    private static final String MAC_MAXIMIZE_COLOR = "#28ca42";
    private static final String MAC_MAXIMIZE_HOVER_COLOR = "#30d158";
    private static final String MAC_MAXIMIZE_TEXT_COLOR = "#ffffff";
    private static final String CLOSE_HOVER_BACKGROUND = "#e74c3c";
    private static final String CLOSE_HOVER_TEXT_COLOR = "white";
    
    private static final String PROPERTY_HIDE_ICON = "hideIcon";
    private static final String PROPERTY_USE_SIMPLE_ACTIONS = "useSimpleActions";
    private static final String PROPERTY_USE_LEGACY_WINDOW_SIZE = "useLegacyWindowSizePersistence";
    private static final String PROPERTY_WINDOW_PERSISTENCE_TYPE = "windowPersistenceType";
    
    private double xOffset = 0;
    private double yOffset = 0;
    private boolean isMaximized = false;
    private double restoreX;
    private double restoreY;
    private double restoreWidth;
    private double restoreHeight;
    private boolean hasRestoreBounds = false;
    private String currentTextColor = DEFAULT_TEXT_COLOR; // Aktuelle Textfarbe für Hover-Effekte
    private int activeThemeIndex = -1;
    private boolean windowSizeLoaded = false; // Flag um mehrfaches Laden zu verhindern
    
    private HBox titleBar;
    private Label titleLabel;
    private Label iconLabel;
    private Button minimizeBtn;
    private Button maximizeBtn;
    private Button closeBtn;
    private Region spacer;
    
    public CustomStage() {
        super();
        initStyle(StageStyle.UNDECORATED);
        
        // macOS Window Manager initialisieren
        initializeMacWindowManager();
        
        setupCustomTitleBar();
        
        // Synchronisiere isMaximized für Nicht-Mac-Systeme
        maximizedProperty().addListener((obs, oldVal, newVal) -> {
            if (isMacPlatform()) {
                return;
            }
            isMaximized = newVal;
            if (maximizeBtn != null) {
                maximizeBtn.setText(newVal ? DEFAULT_MAXIMIZE_SYMBOL_MAXIMIZED : DEFAULT_MAXIMIZE_SYMBOL);
            }
        });
    }

    /**
     * Initialisiert den macOS Window Manager, falls auf macOS.
     */
    private void initializeMacWindowManager() {
        boolean isMac = isMacPlatform();
        if (isMac) {
            useMacWindowManager = true;
            logger.debug("macOS erkannt - MacWindowManager wird verwendet");
        } else {
            useMacWindowManager = false;
            logger.debug("Nicht-macOS System erkannt - Standard Window Handling wird verwendet");
        }
    }
    
    public CustomStage(StageStyle style) {
        super(style);
        initStyle(StageStyle.UNDECORATED);
        
        // macOS Window Manager initialisieren
        initializeMacWindowManager();

        setupCustomTitleBar();
        
        // Synchronisiere isMaximized mit der JavaFX maximizedProperty
        maximizedProperty().addListener((obs, oldVal, newVal) -> {
            isMaximized = newVal;
            if (maximizeBtn != null) {
                String osName = System.getProperty("os. name").toLowerCase();
                boolean isMac = osName.contains("mac");
                if (isMac) {
                    // macOS uses a dedicated symbol handled via setMacButtonSymbol
                } else {
                    maximizeBtn.setText(newVal ? DEFAULT_MAXIMIZE_SYMBOL_MAXIMIZED : DEFAULT_MAXIMIZE_SYMBOL);
                }
            }
        });
    }
    
    /**
     * Erstellt die benutzerdefinierte Titelleiste
     */
    private void setupCustomTitleBar() {
        titleBar = new HBox();
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.setStyle(DEFAULT_TITLEBAR_STYLE + " -fx-min-height: 48px; -fx-pref-height: 48px; -fx-alignment: center-left; -fx-valignment: center; -fx-padding: 0 12px;");
        titleBar.setMinHeight(48);
        titleBar.setPrefHeight(48);
        if (!titleBar.getStyleClass().contains("title-bar")) {
            titleBar.getStyleClass().add("title-bar");
        }

        iconLabel = createIconLabel(DEFAULT_ICON_TEXT);

        titleLabel = new Label(DEFAULT_TITLE);
        titleLabel.setStyle(createTitleLabelStyle(currentTextColor));
        if (!titleLabel.getStyleClass().contains("title-text")) {
            titleLabel.getStyleClass().add("title-text");
        }
        titleLabel.setBackground(null);
        titleLabel.setOpacity(1.0);

        spacer = new Region();
        spacer.setStyle("-fx-background-color: transparent;");
        HBox.setHgrow(spacer, Priority.ALWAYS);

        configureButtonsByPlatform();
        updateSpecialWindowAdjustments();
        addButtonsToTitleBar();

        setupHoverEffects();
        setupDragAndDrop();
        
        // MacWindowManager nach setupCustomTitleBar() initialisieren
        if (useMacWindowManager && titleBar != null) {
            // MACWINDOWMANAGER DEAKTIVIERT - überschreibt die grundlegenden Handler
            macWindowManager = null; // Nicht initialisieren
            logger.debug("MacWindowManager deaktiviert - grundlegende Handler werden verwendet");
        }

    }
    
    /**
     * Erstellt einen Button für die Titelleiste mit einem Symbol
     */
    private Button createTitleButton(String symbol) {
        Button button = new Button(symbol);
        button.setStyle(createButtonStyle(currentTextColor, getButtonFontSize()));
        button.setMinSize(getButtonWidth(), getButtonHeight());
        button.setPrefSize(getButtonWidth(), getButtonHeight());
        button.setMaxSize(getButtonWidth(), getButtonHeight());
        button.setAlignment(Pos.CENTER);
        button.setFocusTraversable(false);
        if (!button.getStyleClass().contains("window-control-button")) {
            button.getStyleClass().add("window-control-button");
        }
        return button;
    }
    
    /**
     * Richtet Hover-Effekte für die Buttons ein
     */
    private void setupHoverEffects() {
        String osName = System.getProperty("os.name").toLowerCase();
        boolean isMac = osName.contains("mac");

        if (isMac) {
            setupMacHoverEffects();
            return;
        }

        boolean useSimpleActions = Boolean.TRUE.equals(getProperties().get(PROPERTY_USE_SIMPLE_ACTIONS));

        if (useSimpleActions) {
            applyTextOnlyHover(minimizeBtn, currentTextColor, null);
            applyTextOnlyHover(maximizeBtn, currentTextColor, null);
            applyTextOnlyHover(closeBtn, currentTextColor, CLOSE_HOVER_BACKGROUND);
        } else {
            String defaultStyle = createButtonStyle(currentTextColor, getButtonFontSize());
            String hoverStyle = createHoverButtonStyle(currentTextColor);
            applyHoverStyle(minimizeBtn, defaultStyle, hoverStyle);
            applyHoverStyle(maximizeBtn, defaultStyle, hoverStyle);
            applyHoverStyle(closeBtn, defaultStyle, buildCloseHoverStyle());
        }

        if (activeThemeIndex == 2) {
            minimizeBtn.setOnMouseEntered(null);
            minimizeBtn.setOnMouseExited(null);
            maximizeBtn.setOnMouseEntered(null);
            maximizeBtn.setOnMouseExited(null);
            closeBtn.setOnMouseEntered(null);
            closeBtn.setOnMouseExited(null);
        }
    }

    private void applyTextOnlyHover(Button button, String textColor, String hoverBackground) {
        if (button == null) {
            return;
        }
        String defaultStyle = createButtonStyle(textColor, getButtonFontSize());
        String hoverStyle;
        if (hoverBackground != null) {
            hoverStyle = String.format(BUTTON_STYLE_TEMPLATE, hoverBackground, CLOSE_HOVER_TEXT_COLOR, getButtonFontSize(),
                String.valueOf((int) getButtonWidth()), String.valueOf((int) getButtonHeight()), String.valueOf((int) getButtonWidth()), String.valueOf((int) getButtonHeight())) + 
                " -fx-alignment: center; -fx-content-display: center; -fx-valignment: center; -fx-padding: 0;";
        } else {
            hoverStyle = createHoverButtonStyle(textColor);
        }
        button.setStyle(defaultStyle);
        button.setOnMouseEntered(e -> button.setStyle(hoverStyle));
        button.setOnMouseExited(e -> button.setStyle(defaultStyle));
    }

    private void applyHoverStyle(Button button, String defaultStyle, String hoverStyle) {
        if (button == null) {
            return;
        }
        button.setStyle(defaultStyle);
        button.setOnMouseEntered(e -> button.setStyle(hoverStyle));
        button.setOnMouseExited(e -> button.setStyle(defaultStyle));
    }

    private String buildCloseHoverStyle() {
        return String.format(BUTTON_STYLE_TEMPLATE, CLOSE_HOVER_BACKGROUND, CLOSE_HOVER_TEXT_COLOR, getButtonFontSize(),
                String.valueOf((int) getButtonWidth()), String.valueOf((int) getButtonHeight()), String.valueOf((int) getButtonWidth()), String.valueOf((int) getButtonHeight())) + 
                " -fx-alignment: center; -fx-content-display: center; -fx-valignment: center; -fx-padding: 0;";
    }
    
    /**
     * Richtet Drag & Drop für die Titelleiste ein - betriebssystemspezifisch
     */
    private void setupDragAndDrop() {
        // IMMER die grundlegenden Handler einrichten (für alle OS)
        String osName = System.getProperty("os.name").toLowerCase();
        boolean isMac = osName.contains("mac");
        
        if (isMac) {
            setupMacDragAndDrop();
        } else {
            setupWindowsLinuxDragAndDrop();
        }
        
        // MacWindowManager nur für zusätzliche Features (nicht für grundlegendes Drag)
        if (useMacWindowManager && macWindowManager != null) {
            logger.debug("MacWindowManager für zusätzliche Features aktiv");
            // Nur für spezielle macOS-Features wie Edge-Snapping etc.
            // Grundlegendes Drag wird bereits oben gehandhabt
        }
    }
    
    /**
     * macOS-spezifisches Drag & Drop Handling
     */
    private void setupMacDragAndDrop() {
        // Minimale Größe sofort beim Start setzen
        setMinWindowSize();
        
        titleBar.setOnMousePressed(event -> {
            if (event.getClickCount() == 1 && !isResizing) {
                xOffset = event.getSceneX();
                yOffset = event.getSceneY();
            }
        });
        
        titleBar.setOnMouseDragged(event -> {
            if (!isResizing) {
                double newX = event.getScreenX() - xOffset;
                double newY = event.getScreenY() - yOffset;
                
                // Minimale Größe sicherstellen
                setMinWindowSize();
                
                // KEINE Bounds-Beschränkung - Fenster kann überall hin
                setX(newX);
                setY(newY);
            }
        });
        
        titleBar.setOnMouseReleased(event -> {
            // Wenn Drag beendet ist, Fenster an aktuellen Screen anpassen
            adjustWindowToCurrentScreen();
            
            // Fenstergröße speichern
            saveWindowSize();
        });

        // Doppelklick zum Maximieren/Restaurieren (Mac-Verhalten)
        titleBar.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && !isResizing) {
                toggleMaximize();
            }
        });
    }
    
    /**
     * Setzt die minimale Fenstergröße basierend auf dem aktuellen Screen
     */
    private void setMinWindowSize() {
        var currentScreen = getCurrentScreen();
        var visualBounds = currentScreen.getVisualBounds();
        
        // Absolute Minimalgrößen
        double minHeight = 600;  // Mindestens 600px für alle UI-Elemente
        double minWidth = 400;   // Mindestens 400px Breite
        
        // Wenn Screen sehr klein ist, passe die Höhe an
        if (visualBounds.getHeight() < minHeight) {
            minHeight = visualBounds.getHeight() - 50; // 50px Puffer
        }
        
        setMinWidth(minWidth);
        setMinHeight(minHeight);
        
        // Auch aktuelle Größe anpassen wenn zu klein
        if (getWidth() < minWidth) {
            setWidth(minWidth);
        }
        if (getHeight() < minHeight) {
            setHeight(minHeight);
        }
    }
    
    /**
     * Passt das Fenster an den aktuellen Screen an
     */
    private void adjustWindowToCurrentScreen() {
        var currentScreen = getCurrentScreen();
        var visualBounds = currentScreen.getVisualBounds();
        
        double windowX = getX();
        double windowY = getY();
        double windowWidth = getWidth();
        double windowHeight = getHeight();
        
        // Prüfe ob Fenster außerhalb des aktuellen Screens ist
        boolean needsAdjustment = false;
        
        // Wenn Fenster größer als der Screen ist
        if (windowWidth > visualBounds.getWidth() || windowHeight > visualBounds.getHeight()) {
            needsAdjustment = true;
        }
        
        // Wenn Fenster teilweise außerhalb des Screens ist
        if (windowX < visualBounds.getMinX() || 
            windowY < visualBounds.getMinY() ||
            windowX + windowWidth > visualBounds.getMaxX() ||
            windowY + windowHeight > visualBounds.getMaxY()) {
            needsAdjustment = true;
        }
        
        if (needsAdjustment) {
            // Fenster an Screen anpassen
            double newWidth = Math.min(windowWidth, visualBounds.getWidth() * 0.9);
            double newHeight = Math.min(windowHeight, visualBounds.getHeight() * 0.9);
            double newX = Math.max(visualBounds.getMinX(), Math.min(windowX, visualBounds.getMaxX() - newWidth));
            double newY = Math.max(visualBounds.getMinY(), Math.min(windowY, visualBounds.getMaxY() - newHeight));
            
            setWidth(newWidth);
            setHeight(newHeight);
            setX(newX);
            setY(newY);
            
            // NICHT speichern - nur bei echtem Resize/Drag speichern
        }
    }
    
    
    /**
     * Windows/Linux-spezifisches Drag & Drop Handling
     */
    private void setupWindowsLinuxDragAndDrop() {
        titleBar.setOnMousePressed(event -> {
            if (event.getClickCount() == 1 && !isResizing) {
                xOffset = event.getSceneX();
                yOffset = event.getSceneY();
            }
        });
        
        titleBar.setOnMouseDragged(event -> {
            if (!isResizing) {
                double newX = event.getScreenX() - xOffset;
                double newY = event.getScreenY() - yOffset;
                setX(newX);
                setY(newY);
            }
        });
        
        // Doppelklick zum Maximieren/Minimieren (Windows/Linux-Verhalten)
        titleBar.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && !isResizing) {
                toggleMaximize();
            }
        });
    }
    
    /**
     * Wechselt zwischen maximiertem und normalem Zustand
     */
    private void toggleMaximize() {
        if (isMacPlatform()) {
            if (isMaximized) {
                restoreWindowBounds();
            } else {
                maximizeToCurrentScreen();
            }
            return;
        }

        if (isMaximized) {
            setMaximized(false);
            isMaximized = false;
        } else {
            setMaximized(true);
            isMaximized = true;
        }
    }

    private void maximizeToCurrentScreen() {
        hasRestoreBounds = true;
        restoreX = getX();
        restoreY = getY();
        restoreWidth = getWidth();
        restoreHeight = getHeight();

        Rectangle2D bounds = getCurrentScreen().getVisualBounds();
        setX(bounds.getMinX());
        setY(bounds.getMinY());
        setWidth(bounds.getWidth());
        setHeight(bounds.getHeight());
        isMaximized = true;
    }

    private void restoreWindowBounds() {
        if (!hasRestoreBounds) {
            return;
        }
        setWidth(restoreWidth);
        setHeight(restoreHeight);
        setX(restoreX);
        setY(restoreY);
        isMaximized = false;
    }
    
    /**
     * Setzt die Scene mit benutzerdefinierter Titelleiste
     */
    public void setSceneWithTitleBar(Scene scene) {
        if (scene != null) {
            VBox newRoot = new VBox();

            // Ursprünglichen Root zwischenspeichern
            javafx.scene.Parent originalRoot = scene.getRoot();

            // WICHTIG: Theme-/Style-Klassen und ID vom alten Root auf den neuen Root übernehmen,
            // damit .root.theme-* und ähnliche Selektoren weiterhin greifen
            newRoot.getStyleClass().addAll(originalRoot.getStyleClass());
            if (originalRoot.getId() != null && !originalRoot.getId().isEmpty()) {
                newRoot.setId(originalRoot.getId());
            }

            newRoot.getChildren().addAll(titleBar, originalRoot);
            VBox.setVgrow(originalRoot, Priority.ALWAYS);
            
            // Theme-Klassen explizit auf newRoot setzen, damit .theme-dark.blau-theme .title-bar etc. greifen
            applyThemeClassesToNode(newRoot, activeThemeIndex);
            
            // Neue Scene mit Titelleiste erstellen
            Scene newScene = new Scene(newRoot);
            newScene.getStylesheets().addAll(scene.getStylesheets());
            
            newRoot.setStyle("-fx-border-width: 0px; -fx-border-color: transparent; -fx-padding: 0px; -fx-margin: 0px; -fx-spacing: 0px;");
            
            super.setScene(newScene);
            
            // Resize-Handles hinzufügen
            setupResizeHandles(newScene);
            
            // WICHTIG: Border sofort nach setScene setzen
            if (currentTextColor != null && activeThemeIndex != 2) { // Kein Border für Pastell-Theme
                // Border basierend auf aktueller Textfarbe setzen
                String borderColor = currentTextColor.equals("white") ? "white" : "black";
                setStageBorder(borderColor);
            } else if (activeThemeIndex == 2) {
                // Pastell-Theme: Kein Border setzen
            }
            
            // Nur bei expliziter Anforderung Legacy-Größen laden
            if (Boolean.TRUE.equals(getProperties().get(PROPERTY_USE_LEGACY_WINDOW_SIZE))) {
                loadWindowSize();
            }
            
        } else {
            super.setScene(null);
        }
    }

    /** Aktiviert das alte Laden der Fenstergröße aus ~/.manuskript_window.properties für Spezialfenster. */
    public void enableLegacyWindowSizePersistence() {
        getProperties().put(PROPERTY_USE_LEGACY_WINDOW_SIZE, Boolean.TRUE);
    }

    /** Setzt einen stabilen Schlüssel für die Legacy-Fenstergrößen-Persistenz. */
    public void setWindowPersistenceType(String windowType) {
        if (windowType == null || windowType.trim().isEmpty()) {
            getProperties().remove(PROPERTY_WINDOW_PERSISTENCE_TYPE);
            return;
        }
        getProperties().put(PROPERTY_WINDOW_PERSISTENCE_TYPE, windowType.trim());
    }
    // Debug vollständig entfernt
    
    /**
     * Richtet Resize-Handles für das Fenster ein
     */
    private void setupResizeHandles(Scene scene) {
        // IMMER die grundlegenden Resize-Handler einrichten
        setupStandardResizeHandles(scene);
        
        // MacWindowManager nur für zusätzliche macOS-Features
        if (useMacWindowManager && macWindowManager != null) {
            logger.debug("MacWindowManager für zusätzliche Resize-Features aktiv");
            // Zusätzliche macOS-Features wie Edge-Snapping etc.
            // Grundlegendes Resize wird bereits oben gehandhabt
        }
    }
    
    /**
     * Standard Resize-Handles (für non-macOS oder wenn MacWindowManager nicht verfügbar)
     */
    private void setupStandardResizeHandles(Scene scene) {
        final int RESIZE_BORDER = 10; // Vergrößert für bessere Erkennung
        
        // WICHTIG: EventFilter verwenden, um Events VOR anderen Handlern abzufangen
        scene.addEventFilter(MouseEvent.MOUSE_MOVED, event -> {
            // Cursor-Lock respektieren - nicht überschreiben wenn gesperrt
            if (cursorLocked) {
                return;
            }
            
            // Warte-Cursor respektieren - nicht überschreiben
            if (scene.getCursor() == javafx.scene.Cursor.WAIT) {
                return;
            }
            
            // KEINE Maximierungs-Checks mehr
            
            // Prüfe, ob wir uns in der Titelleiste befinden - dann kein Resize
            if (event.getY() < titleBar.getHeight()) {
                scene.setCursor(javafx.scene.Cursor.DEFAULT);
                return;
            }
            
            // Prüfe, ob wir uns in einem Textbereich befinden - dann kein Resize
            // ABER: Nur wenn wir nicht am Rand sind
            if (event.getTarget() instanceof Node) {
                Node target = (Node) event.getTarget();
                if (target != null && (target.getStyleClass().contains("code-area") || 
                                      target.getStyleClass().contains("text-area") ||
                                      target.getStyleClass().contains("text-field") ||
                                      target.getStyleClass().contains("editor"))) {
                    // Nur TEXT-Cursor setzen, wenn wir nicht am Rand sind
                    double x = event.getSceneX();
                    double y = event.getSceneY();
                    double width = scene.getWidth();
                    double height = scene.getHeight();
                    
                    // Wenn wir am Rand sind, Resize-Cursor verwenden
                    if (x < RESIZE_BORDER || x >= width - RESIZE_BORDER || 
                        y < RESIZE_BORDER || y >= height - RESIZE_BORDER) {
                        // Resize-Cursor wird weiter unten gesetzt
                    } else {
                        scene.setCursor(javafx.scene.Cursor.TEXT);
                        return;
                    }
                }
            }
            
            double x = event.getSceneX();
            double y = event.getSceneY();
            double width = scene.getWidth();
            double height = scene.getHeight();
            
            // Resize-Cursor anzeigen - ALLE RICHTUNGEN AKTIVIERT
            if (x < RESIZE_BORDER && y < RESIZE_BORDER) {
                scene.setCursor(javafx.scene.Cursor.NW_RESIZE);
            } else if (x >= width - RESIZE_BORDER && y < RESIZE_BORDER) {
                scene.setCursor(javafx.scene.Cursor.NE_RESIZE);
            } else if (x < RESIZE_BORDER && y >= height - RESIZE_BORDER) {
                scene.setCursor(javafx.scene.Cursor.SW_RESIZE);
            } else if (x >= width - RESIZE_BORDER && y >= height - RESIZE_BORDER) {
                scene.setCursor(javafx.scene.Cursor.SE_RESIZE);
            } else if (x < RESIZE_BORDER) {
                scene.setCursor(javafx.scene.Cursor.W_RESIZE);
            } else if (x >= width - RESIZE_BORDER) {
                scene.setCursor(javafx.scene.Cursor.E_RESIZE);
            } else if (y < RESIZE_BORDER) {
                scene.setCursor(javafx.scene.Cursor.N_RESIZE);
            } else if (y >= height - RESIZE_BORDER) {
                scene.setCursor(javafx.scene.Cursor.S_RESIZE);
            } else {
                scene.setCursor(javafx.scene.Cursor.DEFAULT);
            }
        });
        
        // WICHTIG: EventFilter für Mouse-Press verwenden
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            // KEINE Maximierungs-Checks mehr
            
            // Prüfe, ob wir uns in der Titelleiste befinden - dann kein Resize
            if (event.getY() < titleBar.getHeight()) {
                return;
            }
            
            // Prüfe, ob wir uns in einem Textbereich befinden - dann kein Resize
            if (event.getTarget() instanceof Node) {
                Node target = (Node) event.getTarget();
                if (target != null && (target.getStyleClass().contains("code-area") || 
                                      target.getStyleClass().contains("text-area") ||
                                      target.getStyleClass().contains("text-field") ||
                                      target.getStyleClass().contains("editor"))) {
                    return; // Textbereich - kein Resize
                }
            }
            
            // WICHTIG: Resize NUR starten, wenn der Cursor bereits auf einem Resize-Cursor steht - ALLE RICHTUNGEN AKTIVIERT
            if (scene.getCursor() == javafx.scene.Cursor.E_RESIZE ||
                scene.getCursor() == javafx.scene.Cursor.W_RESIZE ||
                scene.getCursor() == javafx.scene.Cursor.N_RESIZE ||
                scene.getCursor() == javafx.scene.Cursor.S_RESIZE ||
                scene.getCursor() == javafx.scene.Cursor.NE_RESIZE ||
                scene.getCursor() == javafx.scene.Cursor.NW_RESIZE ||
                scene.getCursor() == javafx.scene.Cursor.SE_RESIZE ||
                scene.getCursor() == javafx.scene.Cursor.SW_RESIZE) {
                
                double x = event.getSceneX();
                double y = event.getSceneY();
                double width = scene.getWidth();
                double height = scene.getHeight();
                
                startResize(event, x, y, width, height);
                event.consume(); // WICHTIG: Event konsumieren, damit ScrollPane es nicht bekommt
            }
        });
        
        // WICHTIG: EventFilter für Mouse-Drag verwenden
        scene.addEventFilter(MouseEvent.MOUSE_DRAGGED, event -> {
            if (isResizing) {
                performResize(event);
                event.consume(); // WICHTIG: Event konsumieren während Resize
            }
        });
        
        // WICHTIG: EventFilter für Mouse-Release verwenden
        scene.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
            if (isResizing) {
                isResizing = false;
                scene.setCursor(javafx.scene.Cursor.DEFAULT);
                
                // Größe nach Resize speichern
                saveWindowSize();
                
                event.consume(); // WICHTIG: Event konsumieren
            }
        });
    }
    
    private double resizeStartX, resizeStartY, resizeStartWidth, resizeStartHeight;
    private boolean isResizing = false;
    private String resizeDirection = "";
    
    /**
     * Startet das Resizing
     */
    private void startResize(MouseEvent event, double x, double y, double width, double height) {
        // KEINE Maximierungs-Checks mehr
        
        resizeStartX = event.getScreenX();
        resizeStartY = event.getScreenY();
        resizeStartWidth = getWidth();
        resizeStartHeight = getHeight();
        
        // Bestimme Resize-Richtung - ALLE RICHTUNGEN AKTIVIERT
        final int RESIZE_BORDER = 10; // Gleicher Wert wie in setupResizeHandles
        if (x < RESIZE_BORDER && y < RESIZE_BORDER) {
            resizeDirection = "NW";
        } else if (x >= width - RESIZE_BORDER && y < RESIZE_BORDER) {
            resizeDirection = "NE";
        } else if (x < RESIZE_BORDER && y >= height - RESIZE_BORDER) {
            resizeDirection = "SW";
        } else if (x >= width - RESIZE_BORDER && y >= height - RESIZE_BORDER) {
            resizeDirection = "SE";
        } else if (x < RESIZE_BORDER) {
            resizeDirection = "W";
        } else if (x >= width - RESIZE_BORDER) {
            resizeDirection = "E";
        } else if (y < RESIZE_BORDER) {
            resizeDirection = "N";
        } else if (y >= height - RESIZE_BORDER) {
            resizeDirection = "S";
        }
        
        isResizing = true;
        
    }
    
    /**
     * Führt das Resizing durch
     */
    private void performResize(MouseEvent event) {
        if (!isResizing) return;
        
        double deltaX = event.getScreenX() - resizeStartX;
        double deltaY = event.getScreenY() - resizeStartY;
        
        double newWidth = resizeStartWidth;
        double newHeight = resizeStartHeight;
        double newX = getX();
        double newY = getY();
        
        // Mindestgröße definieren
        double minWidth = Math.max(getMinWidth(), 300.0);
        double minHeight = Math.max(getMinHeight(), 200.0);
        
        // Resize basierend auf Richtung
        switch (resizeDirection) {
            case "SE":
                newWidth = Math.max(minWidth, resizeStartWidth + deltaX);
                newHeight = Math.max(minHeight, resizeStartHeight + deltaY);
                break;
            case "SW":
                newX = event.getScreenX();
                newWidth = Math.max(minWidth, getX() + getWidth() - newX);
                newHeight = Math.max(minHeight, resizeStartHeight + deltaY);
                break;
            case "NE":
                newWidth = Math.max(minWidth, resizeStartWidth + deltaX);
                newHeight = Math.max(minHeight, resizeStartHeight - deltaY);
                newY = getY() + (resizeStartHeight - newHeight);
                break;
            case "NW":
                newWidth = Math.max(minWidth, resizeStartWidth - deltaX);
                newHeight = Math.max(minHeight, resizeStartHeight - deltaY);
                newX = getX() + (resizeStartWidth - newWidth);
                newY = getY() + (resizeStartHeight - newHeight);
                break;
            case "E":
                newWidth = Math.max(minWidth, resizeStartWidth + deltaX);
                break;
            case "W":
                newX = event.getScreenX();
                newWidth = Math.max(minWidth, getX() + getWidth() - newX);
                break;
            case "S":
                newHeight = Math.max(minHeight, resizeStartHeight + deltaY);
                break;
            case "N":
                newHeight = Math.max(minHeight, resizeStartHeight - deltaY);
                newY = getY() + (resizeStartHeight - newHeight);
                break;
        }
        
        // Nur ändern, wenn es tatsächlich neue Werte sind
        if (Math.abs(getX() - newX) > 0.1) setX(newX);
        if (Math.abs(getY() - newY) > 0.1) setY(newY);
        if (Math.abs(getWidth() - newWidth) > 0.1) setWidth(newWidth);
        if (Math.abs(getHeight() - newHeight) > 0.1) setHeight(newHeight);
    }
    
    /**
     * Setzt den Titel der Titelleiste
     */
    public void setCustomTitle(String title) {
        super.setTitle(title);
        if (titleLabel != null) {
            titleLabel.setText(title);
        }
    }
    
    /**
     * Setzt den Titel (Icon wird automatisch angezeigt)
     */
    public void setTitleWithIcon(String icon, String title) {
        setCustomTitle(title);
    }
    
    /**
     * Ändert die Farbe der Titelleiste
     */
    public void setTitleBarColor(String backgroundColor) {
        setTitleBarColor(backgroundColor, "white");
    }
    
    /**
     * Ändert die Farbe der Titelleiste mit Textfarbe
     */
    public void setTitleBarColor(String backgroundColor, String textColor) {
        setTitleBarColor(backgroundColor, textColor, "#1a252f"); // Standard Border-Farbe
    }
    
    /**
     * Ändert die Farbe der Titelleiste mit Textfarbe und Border-Farbe
     */
    public void setTitleBarColor(String backgroundColor, String textColor, String borderColor) {
        currentTextColor = textColor;

        if (titleBar != null) {
            if (backgroundColor == null) {
                titleBar.setStyle("-fx-padding: 5px; -fx-spacing: 5px; -fx-border-color: " + borderColor + "; -fx-border-width: 1px; -fx-border-radius: 0; -fx-min-height: " + (ICON_SIZE + 4) + "px; -fx-pref-height: " + (ICON_SIZE + 4) + "px; -fx-background-color: transparent;");
            } else {
                titleBar.setStyle("-fx-background-color: " + backgroundColor + "; -fx-padding: 5px; -fx-spacing: 5px; -fx-border-color: " + borderColor + "; -fx-border-width: 1px; -fx-border-radius: 0; -fx-min-height: " + (ICON_SIZE + 4) + "px; -fx-pref-height: " + (ICON_SIZE + 4) + "px;");
            }
            iconLabel.setStyle(createIconStyle(textColor));
            titleLabel.setStyle(createTitleLabelStyle(textColor));

            String osName = System.getProperty("os.name").toLowerCase();
            boolean isMac = osName.contains("mac");

            if (!isMac) {
                updateButtonStyles(textColor, getButtonFontSize());
                setupHoverEffects();
            } else {
                setupMacHoverEffects();
            }
        }

        setStageBorder(borderColor);
    }
    
    /**
     * Setzt einen dünnen Rand um die gesamte Stage
     */
    private void setStageBorder(String borderColor) {
        if (getScene() != null && getScene().getRoot() != null) {
            Node root = getScene().getRoot();
            String currentStyle = root.getStyle();
            currentStyle = currentStyle.replaceAll("-fx-border-color:[^;]+;?", "");
            currentStyle = currentStyle.replaceAll("-fx-border-width:[^;]+;?", "");
            currentStyle = currentStyle.replaceAll("-fx-border-radius:[^;]+;?", "");

            if (activeThemeIndex == 2 || borderColor == null || borderColor.trim().isEmpty()) {
                root.setStyle(currentStyle.trim());
            } else {
                String newBorderStyle = "-fx-border-color: " + borderColor + "; -fx-border-width: 3px; -fx-border-radius: 0px;";
                if (currentStyle.trim().isEmpty()) {
                    root.setStyle(newBorderStyle);
                } else {
                    root.setStyle(currentStyle + "; " + newBorderStyle);
                }
            }
        } 
    }
    
    /**
     * Ändert die Farbe der Titelleiste basierend auf Theme
     */
    public void setTitleBarTheme(int themeIndex) {
        String borderColor;
        activeThemeIndex = themeIndex;
        switch (themeIndex) {
            case 0: borderColor = "black"; break;
            case 1: borderColor = "white"; break;
            case 2: borderColor = null; break;
            case 3: case 4: case 5: default: borderColor = "white"; break;
        }
        currentTextColor = (themeIndex == 0 || themeIndex == 2) ? "black" : "white";
        if (titleBar != null) {
            titleBar.setStyle("-fx-padding: 0 12px; -fx-min-height: 48px; -fx-pref-height: 48px;");
            iconLabel.setStyle("");
            titleLabel.setStyle("");
            String osName = System.getProperty("os.name", "").toLowerCase();
            boolean isMac = osName.contains("mac");
            if (!isMac && minimizeBtn != null) {
                updateButtonStyles(currentTextColor, getButtonFontSize());
                setupHoverEffects();
            } else if (isMac) {
                setupMacHoverEffects();
            }
        }
        setStageBorder(borderColor);
    }
    
    /**
     * Setzt die Theme-Style-Klassen auf einen Node (z. B. Scene-Root), damit CSS-Descendant-Selektoren greifen.
     */
    private void applyThemeClassesToNode(Node node, int themeIndex) {
        if (node == null) return;
        node.getStyleClass().removeAll("theme-dark", "theme-light", "weiss-theme", "pastell-theme", "blau-theme", "gruen-theme", "lila-theme");
        switch (themeIndex) {
            case 0: node.getStyleClass().add("weiss-theme"); break;
            case 1: node.getStyleClass().add("theme-dark"); break;
            case 2: node.getStyleClass().add("pastell-theme"); break;
            case 3: node.getStyleClass().addAll("theme-dark", "blau-theme"); break;
            case 4: node.getStyleClass().addAll("theme-dark", "gruen-theme"); break;
            case 5: node.getStyleClass().addAll("theme-dark", "lila-theme"); break;
            default: node.getStyleClass().add("weiss-theme"); break;
        }
    }
    
    /**
     * Wendet das Theme sowohl auf Titelleiste als auch auf den Inhalt an
     */
    public void setFullTheme(int themeIndex) {
        // Erst Titelleiste aktualisieren
        setTitleBarTheme(themeIndex);
        
        // Dann Inhalt aktualisieren
        if (getScene() != null && getScene().getRoot() != null) {
            Node root = getScene().getRoot();
            applyThemeClassesToNode(root, themeIndex);
            
            // Versuche, den tatsächlichen Inhalt (zweites Kind des VBox-Wrappers) zu stylen
            if (root instanceof VBox && ((VBox) root).getChildren().size() > 1) {
                Node contentNode = ((VBox) root).getChildren().get(1); // Zweites Kind ist der Inhalt
                contentNode.getStyleClass().removeAll("theme-dark", "theme-light", "weiss-theme", "pastell-theme", "blau-theme", "gruen-theme", "lila-theme");
                
                switch (themeIndex) {
                    case 0: // Weiß
                        contentNode.getStyleClass().add("weiss-theme");
                        break;
                    case 1: // Schwarz
                        contentNode.getStyleClass().add("theme-dark");
                        break;
                    case 2: // Pastell
                        contentNode.getStyleClass().add("pastell-theme");
                        break;
                    case 3: // Blau
                        contentNode.getStyleClass().add("theme-dark");
                        contentNode.getStyleClass().add("blau-theme");
                        break;
                    case 4: // Grün
                        contentNode.getStyleClass().add("theme-dark");
                        contentNode.getStyleClass().add("gruen-theme");
                        break;
                    case 5: // Lila
                        contentNode.getStyleClass().add("theme-dark");
                        contentNode.getStyleClass().add("lila-theme");
                        break;
                }
            }
            
            // WICHTIG: Alle UI-Elemente rekursiv durchgehen und Theme anwenden
            applyThemeToAllNodes(root, themeIndex);
        }
    }
    
    /**
     * Wendet das Theme rekursiv auf alle UI-Elemente an
     */
    private void applyThemeToAllNodes(Node node, int themeIndex) {
        if (node == null) return;
        // Titelleiste nicht mit Theme-Klassen versehen – wird nur über Parent und .title-bar gestylt
        if (node.getStyleClass().contains("title-bar")) {
            if (node instanceof Parent) {
                Parent parent = (Parent) node;
                for (Node child : parent.getChildrenUnmodifiable()) {
                    applyThemeToAllNodes(child, themeIndex);
                }
            }
            return;
        }
        // Theme-Klassen auf diesem Node anwenden
        node.getStyleClass().removeAll("theme-dark", "theme-light", "weiss-theme", "pastell-theme", "blau-theme", "gruen-theme", "lila-theme");
        switch (themeIndex) {
            case 0: // Weiß
                node.getStyleClass().add("weiss-theme");
                break;
            case 1: // Schwarz
                node.getStyleClass().add("theme-dark");
                break;
            case 2: // Pastell
                node.getStyleClass().add("pastell-theme");
                break;
            case 3: // Blau
                node.getStyleClass().add("theme-dark");
                node.getStyleClass().add("blau-theme");
                break;
            case 4: // Grün
                node.getStyleClass().add("theme-dark");
                node.getStyleClass().add("gruen-theme");
                break;
            case 5: // Lila
                node.getStyleClass().add("theme-dark");
                node.getStyleClass().add("lila-theme");
                break;
        }
        // Rekursiv alle Kinder durchgehen
        if (node instanceof Parent) {
            Parent parent = (Parent) node;
            for (Node child : parent.getChildrenUnmodifiable()) {
                applyThemeToAllNodes(child, themeIndex);
            }
        }
    }

    private String createTitleLabelStyle(String textColor) {
        return "-fx-text-fill: " + textColor + "; -fx-font-weight: bold; -fx-font-size: 14px; " +
               "-fx-padding: 0 10px; -fx-background-color: transparent; -fx-background-radius: 0; -fx-opacity: 1;";
    }

    private String createButtonStyle(String textColor, int fontSize) {
        return String.format(BUTTON_STYLE_TEMPLATE, DEFAULT_BUTTON_BACKGROUND, textColor, fontSize,
                String.valueOf((int) getButtonWidth()), String.valueOf((int) getButtonHeight()), String.valueOf((int) getButtonWidth()), String.valueOf((int) getButtonHeight())) + 
                " -fx-alignment: center; -fx-content-display: center; -fx-valignment: center; -fx-padding: 0;";
    }

    private String createHoverButtonStyle(String textColor) {
        return String.format(BUTTON_STYLE_TEMPLATE, DEFAULT_HOVER_BACKGROUND, textColor, getButtonFontSize(),
                String.valueOf((int) getButtonWidth()), String.valueOf((int) getButtonHeight()), String.valueOf((int) getButtonWidth()), String.valueOf((int) getButtonHeight())) + 
                " -fx-alignment: center; -fx-content-display: center; -fx-valignment: center; -fx-padding: 0;";
    }

    private void applyDefaultStyles() {
        titleBar.setStyle(DEFAULT_TITLEBAR_STYLE);
        iconLabel.setStyle(createIconStyle(DEFAULT_TEXT_COLOR));
        titleLabel.setStyle(createTitleLabelStyle(currentTextColor));

        String osName = System.getProperty("os.name").toLowerCase();
        boolean isMac = osName.contains("mac");

        if (isMac) {
            // macOS-Buttons behalten ihren eigenen Stil, keine Aktualisierung nötig
            return;
        }

        updateButtonStyles(currentTextColor, getButtonFontSize());
    }

    private void setupMacHoverEffects() {
        if (closeBtn != null) {
            closeBtn.setStyle(buildMacButtonStyle(MAC_CLOSE_COLOR, MAC_CLOSE_TEXT_COLOR));
            clearMacButtonSymbol(closeBtn);
            closeBtn.setOnMouseEntered(e -> {
                closeBtn.setStyle(buildMacButtonStyle(MAC_CLOSE_HOVER_COLOR, MAC_CLOSE_TEXT_COLOR));
                setMacButtonSymbol(closeBtn, "✕", MAC_CLOSE_TEXT_COLOR);
            });
            closeBtn.setOnMouseExited(e -> {
                closeBtn.setStyle(buildMacButtonStyle(MAC_CLOSE_COLOR, MAC_CLOSE_TEXT_COLOR));
                clearMacButtonSymbol(closeBtn);
            });
        }
        if (minimizeBtn != null) {
            minimizeBtn.setStyle(buildMacButtonStyle(MAC_MINIMIZE_COLOR, MAC_MINIMIZE_TEXT_COLOR));
            clearMacButtonSymbol(minimizeBtn);
            minimizeBtn.setOnMouseEntered(e -> {
                minimizeBtn.setStyle(buildMacButtonStyle(MAC_MINIMIZE_HOVER_COLOR, MAC_MINIMIZE_TEXT_COLOR));
                setMacButtonSymbol(minimizeBtn, "−", MAC_MINIMIZE_TEXT_COLOR);
            });
            minimizeBtn.setOnMouseExited(e -> {
                minimizeBtn.setStyle(buildMacButtonStyle(MAC_MINIMIZE_COLOR, MAC_MINIMIZE_TEXT_COLOR));
                clearMacButtonSymbol(minimizeBtn);
            });
        }
        if (maximizeBtn != null) {
            maximizeBtn.setStyle(buildMacButtonStyle(MAC_MAXIMIZE_COLOR, MAC_MAXIMIZE_TEXT_COLOR));
            clearMacButtonSymbol(maximizeBtn);
            maximizeBtn.setOnMouseEntered(e -> {
                maximizeBtn.setStyle(buildMacButtonStyle(MAC_MAXIMIZE_HOVER_COLOR, MAC_MAXIMIZE_TEXT_COLOR));
                setMacButtonSymbol(maximizeBtn, MAC_MAXIMIZE_SYMBOL, MAC_MAXIMIZE_TEXT_COLOR);
            });
            maximizeBtn.setOnMouseExited(e -> {
                maximizeBtn.setStyle(buildMacButtonStyle(MAC_MAXIMIZE_COLOR, MAC_MAXIMIZE_TEXT_COLOR));
                clearMacButtonSymbol(maximizeBtn);
            });
        }
    }

    private Label createIconLabel(String text) {
        Label label = new Label(text);
        label.setFont(Font.font("System", FontWeight.BOLD, ICON_SIZE));
        label.setPrefSize(ICON_SIZE, ICON_SIZE);
        label.setMinSize(ICON_SIZE, ICON_SIZE);
        label.setMaxSize(ICON_SIZE, ICON_SIZE);
        label.setAlignment(Pos.CENTER);
        label.getStyleClass().add("title-icon-label"); // CSS-Klasse für gezieltes Styling
        label.setStyle(createIconStyle(DEFAULT_TEXT_COLOR));
        return label;
    }

    private String createIconStyle(String textColor) {
        return "-fx-text-fill: " + textColor + " !important; -fx-font-weight: bold !important; -fx-background-color: transparent !important; -fx-background-radius: 3px !important; -fx-padding: 2px !important; -fx-min-width: " + ICON_SIZE + "px !important; -fx-min-height: " + ICON_SIZE + "px !important; -fx-pref-width: " + ICON_SIZE + "px !important; -fx-pref-height: " + ICON_SIZE + "px !important;";
    }

    private void updateButtonStyles(String textColor, int fontSize) {
        String style = createButtonStyle(textColor, fontSize);
        minimizeBtn.setStyle(style);
        maximizeBtn.setStyle(style);
        closeBtn.setStyle(style);
    }

    private void configureButtonsByPlatform() {
        String osName = System.getProperty("os.name").toLowerCase();
        boolean isMac = osName.contains("mac");

        boolean useSimpleActions = Boolean.TRUE.equals(getProperties().get(PROPERTY_USE_SIMPLE_ACTIONS));

        if (isMac) {
            minimizeBtn = createMacButton("", MAC_MINIMIZE_COLOR, MAC_MINIMIZE_TEXT_COLOR);
            minimizeBtn.setOnAction(e -> setIconified(true));

            maximizeBtn = createMacButton("", MAC_MAXIMIZE_COLOR, MAC_MAXIMIZE_TEXT_COLOR);
            maximizeBtn.setOnAction(e -> toggleMaximize());

            closeBtn = createMacButton("", MAC_CLOSE_COLOR, MAC_CLOSE_TEXT_COLOR);
            closeBtn.setOnAction(e -> fireEvent(new javafx.stage.WindowEvent(this, javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST)));
        } else {
            if (useSimpleActions) {
                minimizeBtn = createSimpleButton("_", getButtonFontSize());
                minimizeBtn.setOnAction(e -> setIconified(true));

                maximizeBtn = createSimpleButton("□", getButtonFontSize());
                maximizeBtn.setOnAction(e -> toggleMaximize());

                closeBtn = createSimpleButton("×", getButtonFontSize());
                closeBtn.setOnAction(e -> fireEvent(new javafx.stage.WindowEvent(this, javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST)));
            } else {
                minimizeBtn = createTitleButton(DEFAULT_MINIMIZE_SYMBOL);
                minimizeBtn.setOnAction(e -> setIconified(true));

                maximizeBtn = createTitleButton(DEFAULT_MAXIMIZE_SYMBOL);
                maximizeBtn.setOnAction(e -> toggleMaximize());

                closeBtn = createTitleButton(DEFAULT_CLOSE_SYMBOL);
                closeBtn.setOnAction(e -> fireEvent(new javafx.stage.WindowEvent(this, javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST)));
            }
        }

        applyDefaultStyles();
    }

    private Button createSimpleButton(String symbol, int fontSize) {
        Button button = new Button(symbol);
        button.setStyle(createButtonStyle(currentTextColor, getButtonFontSize()));
        button.setMinSize(getButtonWidth(), getButtonHeight());
        button.setPrefSize(getButtonWidth(), getButtonHeight());
        button.setMaxSize(getButtonWidth(), getButtonHeight());
        button.setAlignment(Pos.CENTER);
        button.setFocusTraversable(false);
        return button;
    }

    private String buildMacButtonStyle(String backgroundColor, String textColor) {
        return "-fx-background-color: " + backgroundColor + "; -fx-text-fill: " + textColor + "; -fx-font-weight: bold; -fx-font-size: 12px; " +
                "-fx-min-width: " + MAC_BUTTON_SIZE + "px !important; -fx-min-height: " + MAC_BUTTON_SIZE + "px !important; " +
                "-fx-pref-width: " + MAC_BUTTON_SIZE + "px !important; -fx-pref-height: " + MAC_BUTTON_SIZE + "px !important; " +
                "-fx-max-width: " + MAC_BUTTON_SIZE + "px !important; -fx-max-height: " + MAC_BUTTON_SIZE + "px !important; " +
                "-fx-border-color: transparent; -fx-border-radius: " + (MAC_BUTTON_SIZE / 2) + "px; -fx-background-radius: " + (MAC_BUTTON_SIZE / 2) + "px; " +
                "-fx-cursor: hand;" +
                "-fx-padding: 0px !important; " +
                "-fx-alignment: center; -fx-content-display: center; -fx-text-alignment: center;";
    }

    private void setMacButtonSymbol(Button button, String symbol, String textColor) {
        Text text = new Text(symbol);
        text.setFont(Font.font("System", FontWeight.BOLD, MAC_SYMBOL_SIZE));
        text.setStyle("-fx-fill: " + textColor + ";");
        button.setText("");
        button.setGraphic(text);
        button.setGraphicTextGap(0);
        button.setAlignment(Pos.CENTER);
    }

    private void clearMacButtonSymbol(Button button) {
        button.setText("");
        button.setGraphic(null);
        button.setGraphicTextGap(0);
        button.setAlignment(Pos.CENTER);
    }

    private Button createMacButton(String symbol, String backgroundColor, String textColor) {
        Button button = new Button(symbol);
        button.setStyle(buildMacButtonStyle(backgroundColor, textColor));
        button.setFont(new Font("System", 12));
        button.setMinSize(MAC_BUTTON_SIZE, MAC_BUTTON_SIZE);
        button.setPrefSize(MAC_BUTTON_SIZE, MAC_BUTTON_SIZE);
        button.setMaxSize(MAC_BUTTON_SIZE, MAC_BUTTON_SIZE);
        button.setFocusTraversable(false);
        return button;
    }

    private void addButtonsToTitleBar() {
        if (minimizeBtn == null || maximizeBtn == null || closeBtn == null) {
            return;
        }

        String osName = System.getProperty("os.name").toLowerCase();
        boolean isMac = osName.contains("mac");

        titleBar.getChildren().clear();

        boolean hideIcon = !iconLabel.isVisible();

        if (isMac) {
            HBox macButtonContainer = new HBox();
            macButtonContainer.setSpacing(MAC_BUTTON_SPACING);
            macButtonContainer.setAlignment(Pos.CENTER_LEFT);
            
            Region buttonSpacer = new Region();
            buttonSpacer.setMinWidth(12);
            
            macButtonContainer.getChildren().addAll(closeBtn, minimizeBtn, maximizeBtn);
            
            if (hideIcon) {
                titleBar.getChildren().addAll(macButtonContainer, buttonSpacer, titleLabel, spacer);
            } else {
                titleBar.getChildren().addAll(macButtonContainer, buttonSpacer, iconLabel, titleLabel, spacer);
            }
        } else {
            if (hideIcon) {
                titleBar.getChildren().addAll(titleLabel, spacer, minimizeBtn, maximizeBtn, closeBtn);
            } else {
                titleBar.getChildren().addAll(iconLabel, titleLabel, spacer, minimizeBtn, maximizeBtn, closeBtn);
            }
        }
    }

    private void updateSpecialWindowAdjustments() {
        Object hideIconProperty = getProperties().get(PROPERTY_HIDE_ICON);
        boolean hideIcon = hideIconProperty instanceof Boolean && (Boolean) hideIconProperty;
        iconLabel.setVisible(!hideIcon);
        iconLabel.setManaged(!hideIcon);

        Object simpleActionsProperty = getProperties().get(PROPERTY_USE_SIMPLE_ACTIONS);
        boolean useSimpleActions = simpleActionsProperty instanceof Boolean && (Boolean) simpleActionsProperty;

        if (useSimpleActions) {
            setSimpleActionSymbols();
        }
    }

    /**
     * Sperrt den Cursor, damit er nicht von EventFiltern überschrieben wird
     * (z.B. während Export-Prozessen)
     */
    public void setCursorLocked(boolean locked) {
        this.cursorLocked = locked;
    }
    
    /**
     * Prüft, ob der Cursor gesperrt ist
     */
    public boolean isCursorLocked() {
        return cursorLocked;
    }
    
    /**
     * Räumt den MacWindowManager auf (wenn vorhanden)
     */
    public void cleanup() {
        if (macWindowManager != null) {
            macWindowManager.cleanup();
            macWindowManager = null;
        }
    }
    
    /**
     * Gibt Debug-Informationen über den MacWindowManager zurück
     * @return Debug-Informationen oder leeren String wenn nicht verfügbar
     */
    public String getMacWindowManagerDebugInfo() {
        if (macWindowManager != null) {
            return macWindowManager.getDebugInfo();
        }
        return "MacWindowManager nicht verfügbar";
    }
    
    private void setSimpleActionSymbols() {
        if (minimizeBtn != null) {
            minimizeBtn.setText("-");
        }
        if (maximizeBtn != null) {
            maximizeBtn.setText("□");
        }
        if (closeBtn != null) {
            closeBtn.setText("×");
        }
    }
    
    private boolean isMacPlatform() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        return osName.contains("mac");
    }

    private Screen getCurrentScreen() {
        double windowCenterX = getX() + getWidth() / 2.0;
        double windowCenterY = getY() + getHeight() / 2.0;
        for (Screen screen : Screen.getScreens()) {
            Rectangle2D bounds = screen.getBounds();
            if (windowCenterX >= bounds.getMinX() && windowCenterX < bounds.getMaxX()
                    && windowCenterY >= bounds.getMinY() && windowCenterY < bounds.getMaxY()) {
                return screen;
            }
        }
        return Screen.getPrimary();
    }
    
    /**
     * Speichert die aktuelle Fenstergröße und Position.
     */
    private String getWindowType() {
        Object explicitWindowType = getProperties().get(PROPERTY_WINDOW_PERSISTENCE_TYPE);
        if (explicitWindowType instanceof String type && !type.trim().isEmpty()) {
            return type.trim();
        }

        // Bestimme den Fenstertyp basierend auf Titel oder Größe
        String title = getTitle();
        if (title != null) {
            if (title.contains("Manuskript") && !title.contains("-")) {
                return "main";
            } else if (title.contains("Sprachsynthese")) {
                return "tts";
            } else if (title.contains("Debug")) {
                return "debug";
            } else if (title.contains("Kapitel-Editor")) {
                return "editor";
            } else if (title.contains("Lektorat")) {
                return "lektorat";
            }
        }
        
        // Fallback basierend auf Größe
        if (getWidth() > 1200) {
            return "main";
        } else if (getWidth() > 800) {
            return "editor";
        } else {
            return "dialog";
        }
    }

    private void saveWindowSize() {
        try {
            Screen currentScreen = getCurrentScreen();
            String screenId = "screen_" + currentScreen.hashCode();
            
            // Fenster-Typ für eindeutige Keys
            String windowType = getWindowType();
            String prefix = windowType + ".";

            // Lade existierende Properties
            java.util.Properties props = new java.util.Properties();
            java.io.File configFile = new java.io.File(System.getProperty("user.home"), ".manuskript_window.properties");
            if (configFile.exists()) {
                try (java.io.FileInputStream fis = new java.io.FileInputStream(configFile)) {
                    props.load(fis);
                }
            }

            // Speichere nur die Werte dieses Fensters
            props.setProperty(prefix + "width", String.valueOf(getWidth()));
            props.setProperty(prefix + "height", String.valueOf(getHeight()));
            props.setProperty(prefix + "x", String.valueOf(getX()));
            props.setProperty(prefix + "y", String.valueOf(getY()));
            props.setProperty(prefix + "last.screen", screenId);

            // Screen-spezifische Werte auch speichern
            props.setProperty(screenId + "." + windowType + ".width", String.valueOf(getWidth()));
            props.setProperty(screenId + "." + windowType + ".height", String.valueOf(getHeight()));
            props.setProperty(screenId + "." + windowType + ".x", String.valueOf(getX()));
            props.setProperty(screenId + "." + windowType + ".y", String.valueOf(getY()));

            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(configFile)) {
                props.store(fos, "Manuskript Window Settings");
            }
        } catch (Exception e) {
            logger.warn("Fenstergröße konnte nicht gespeichert werden: {}", e.getMessage());
        }
    }

    /**
     * Lädt die gespeicherte Fenstergröße und Position
     */
    public void loadWindowSize() {
        // Verhindere mehrfaches Laden durch einen Flag
        if (windowSizeLoaded) {
            return;
        }
        
        try {
            java.io.File configFile = new java.io.File(System.getProperty("user.home"), ".manuskript_window.properties");
            if (!configFile.exists()) {
                windowSizeLoaded = true;
                return;
            }
            
            java.util.Properties props = new java.util.Properties();
            try (java.io.FileInputStream fis = new java.io.FileInputStream(configFile)) {
                props.load(fis);
            }
            
            var currentScreen = getCurrentScreen();
            String screenId = "screen_" + currentScreen.hashCode();
            String windowType = getWindowType();
            String prefix = windowType + ".";
            
            // Versuche zuerst screen-spezifische Werte zu laden
            String widthStr = props.getProperty(screenId + "." + windowType + ".width");
            String heightStr = props.getProperty(screenId + "." + windowType + ".height");
            String xStr = props.getProperty(screenId + "." + windowType + ".x");
            String yStr = props.getProperty(screenId + "." + windowType + ".y");
            
            // Fallback auf allgemeine Werte
            if (widthStr == null) widthStr = props.getProperty(prefix + "width");
            if (heightStr == null) heightStr = props.getProperty(prefix + "height");
            if (xStr == null) xStr = props.getProperty(prefix + "x");
            if (yStr == null) yStr = props.getProperty(prefix + "y");
            
            double width = widthStr != null ? Double.parseDouble(widthStr) : 800;
            double height = heightStr != null ? Double.parseDouble(heightStr) : 600;
            double x = xStr != null ? Double.parseDouble(xStr) : 100;
            double y = yStr != null ? Double.parseDouble(yStr) : 100;
            
            // Final für Lambda-Ausdruck
            final double finalWidth = width;
            final double finalHeight = height;
            final double finalX = x;
            final double finalY = y;
            
            // Prüfe ob die gespeicherte Größe auf dem aktuellen Screen passt
            var visualBounds = currentScreen.getVisualBounds();
            
            // Wenn gespeicherte Größe zu groß ist, anpassen
            if (finalWidth > visualBounds.getWidth()) {
                width = visualBounds.getWidth() * 0.8;
            }
            if (finalHeight > visualBounds.getHeight()) {
                height = visualBounds.getHeight() * 0.8;
            }
            
            // Position sicherstellen, dass sie im sichtbaren Bereich liegt
            if (finalX < visualBounds.getMinX() || finalX + finalWidth > visualBounds.getMaxX()) {
                x = visualBounds.getMinX() + (visualBounds.getWidth() - finalWidth) / 2;
            }
            if (finalY < visualBounds.getMinY() || finalY + finalHeight > visualBounds.getMaxY()) {
                y = visualBounds.getMinY() + (visualBounds.getHeight() - finalHeight) / 2;
            }
            
            // Final für Lambda-Ausdruck
            final double adjustedWidth = width;
            final double adjustedHeight = height;
            final double adjustedX = x;
            final double adjustedY = y;
            
            // Nur setzen wenn das Fenster sichtbar ist und Größe > 0
            if (isShowing() && adjustedWidth > 0 && adjustedHeight > 0) {
                setWidth(adjustedWidth);
                setHeight(adjustedHeight);
                setX(adjustedX);
                setY(adjustedY);
            } else {
                // Speichere für später wenn das Fenster noch nicht sichtbar ist
                Platform.runLater(() -> {
                    if (isShowing() && adjustedWidth > 0 && adjustedHeight > 0) {
                        setWidth(adjustedWidth);
                        setHeight(adjustedHeight);
                        setX(adjustedX);
                        setY(adjustedY);
                    }
                });
            }
            
            windowSizeLoaded = true;
            
        } catch (Exception e) {
            System.err.println("Fenstergröße konnte nicht geladen werden: " + e.getMessage());
            windowSizeLoaded = true; // Auch bei Fehler als "geladen" markieren
        }
    }

    private static double getButtonWidth() { return Math.max(35.0, ICON_SIZE + 5); }
    private static double getButtonHeight() { return Math.max(30.0, ICON_SIZE + 0); }
    private static int getButtonFontSize() { return (int) Math.max(16, ICON_SIZE * 0.6); }
}
