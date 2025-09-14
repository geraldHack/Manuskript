package com.manuskript;

import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.geometry.Pos;
import javafx.scene.input.MouseEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Eigene Stage-Klasse mit benutzerdefinierter Titelleiste
 */
public class CustomStage extends Stage {
    
    private static final Logger logger = LoggerFactory.getLogger(CustomStage.class);
    
    private double xOffset = 0;
    private double yOffset = 0;
    private boolean isMaximized = false;
    private String currentTextColor = "white"; // Aktuelle Textfarbe für Hover-Effekte
    
    private HBox titleBar;
    private Label titleLabel;
    private ImageView appIcon;
    private Button minimizeBtn;
    private Button maximizeBtn;
    private Button closeBtn;
    
    public CustomStage() {
        super();
        initStyle(StageStyle.UNDECORATED);
        setupCustomTitleBar();
    }
    
    public CustomStage(StageStyle style) {
        super(style);
        initStyle(StageStyle.UNDECORATED);
        setupCustomTitleBar();
    }
    
    /**
     * Erstellt die benutzerdefinierte Titelleiste
     */
    private void setupCustomTitleBar() {
        // Titelleiste erstellen
        titleBar = new HBox();
        titleBar.setStyle("-fx-background-color: linear-gradient(from 0% 0% to 0% 100%, #2c3e50 0%, #34495e 100%); -fx-padding: 5px; -fx-spacing: 5px; -fx-border-color: #1a252f; -fx-border-width: 0 0 1 0;");
        titleBar.setAlignment(Pos.CENTER_LEFT);
        
        // App-Icon - als Label mit einfachem Symbol
        Label iconLabel = new Label("M");
        iconLabel.setStyle("-fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: bold; -fx-padding: 0 5px; -fx-background-color: #3498db; -fx-background-radius: 3px; -fx-min-width: 20px; -fx-min-height: 20px; -fx-alignment: center;");
        appIcon = new ImageView(); // Dummy für Kompatibilität
        
        // Titel-Label
        titleLabel = new Label("Manuskript");
        titleLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold; -fx-font-size: 14px; -fx-padding: 0 10px; -fx-background-color: transparent; -fx-background-radius: 0; -fx-opacity: 1;");
        titleLabel.setBackground(null);
        titleLabel.setOpacity(1.0);
        
        // Spacer für flexible Positionierung
        Region spacer = new Region();
        spacer.setStyle("-fx-background-color: transparent;");
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        // Window-Buttons mit macOS-Style (links, rot-gelb-grün)
        String osName = System.getProperty("os.name").toLowerCase();
        boolean isMac = osName.contains("mac");
        
        if (isMac) {
            // macOS-Style: Links, rot-gelb-grün
            closeBtn = new Button("×");
            closeBtn.setStyle("-fx-background-color: #ff5f57; -fx-text-fill: #4d0000; -fx-font-weight: bold; -fx-font-size: 12px; -fx-min-width: 12px; -fx-min-height: 12px; -fx-border-color: transparent; -fx-border-radius: 6px; -fx-background-radius: 6px;");
            closeBtn.setOnAction(e -> {
                fireEvent(new javafx.stage.WindowEvent(this, javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST));
            });
            
            minimizeBtn = new Button("−");
            minimizeBtn.setStyle("-fx-background-color: #ffbd2e; -fx-text-fill: #975500; -fx-font-weight: bold; -fx-font-size: 12px; -fx-min-width: 12px; -fx-min-height: 12px; -fx-border-color: transparent; -fx-border-radius: 6px; -fx-background-radius: 6px;");
            minimizeBtn.setOnAction(e -> setIconified(true));
            
            maximizeBtn = new Button("□");
            maximizeBtn.setStyle("-fx-background-color: #28ca42; -fx-text-fill: #003300; -fx-font-weight: bold; -fx-font-size: 10px; -fx-min-width: 12px; -fx-min-height: 12px; -fx-border-color: transparent; -fx-border-radius: 6px; -fx-background-radius: 6px;");
            maximizeBtn.setOnAction(e -> toggleMaximize());
        } else {
            // Windows/Linux-Style: Rechts, grau
            minimizeBtn = new Button("−");
            minimizeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 18px; -fx-min-width: 35px; -fx-min-height: 30px; -fx-border-color: transparent; -fx-border-radius: 4px; -fx-background-radius: 4px;");
            minimizeBtn.setOnAction(e -> setIconified(true));
            
            maximizeBtn = new Button("□");
            maximizeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 16px; -fx-min-width: 35px; -fx-min-height: 30px; -fx-border-color: transparent; -fx-border-radius: 4px; -fx-background-radius: 4px;");
            maximizeBtn.setOnAction(e -> toggleMaximize());
            
            closeBtn = new Button("×");
            closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 20px; -fx-min-width: 35px; -fx-min-height: 30px; -fx-border-color: transparent; -fx-border-radius: 4px; -fx-background-radius: 4px;");
            closeBtn.setOnAction(e -> {
                fireEvent(new javafx.stage.WindowEvent(this, javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST));
            });
        }
        
        // Hover-Effekte
        setupHoverEffects();
        
        // Titelleiste zusammenbauen - Button-Reihenfolge je nach OS
        if (isMac) {
            // macOS: Links rot-gelb-grün, dann Titel
            titleBar.getChildren().addAll(closeBtn, minimizeBtn, maximizeBtn, spacer, titleLabel);
        } else {
            // Windows/Linux: Links Titel, rechts Buttons
            titleBar.getChildren().addAll(iconLabel, titleLabel, spacer, minimizeBtn, maximizeBtn, closeBtn);
        }
        
        // Drag & Drop für Titelleiste
        setupDragAndDrop();
        
        logger.info("Benutzerdefinierte Titelleiste erstellt");
    }
    

    
    /**
     * Richtet Hover-Effekte für die Buttons ein
     */
    private void setupHoverEffects() {
        String osName = System.getProperty("os.name").toLowerCase();
        boolean isMac = osName.contains("mac");
        
        if (isMac) {
            // macOS-Style Hover-Effekte
            closeBtn.setOnMouseEntered(e -> closeBtn.setStyle("-fx-background-color: #ff3b30; -fx-text-fill: #4d0000; -fx-font-weight: bold; -fx-font-size: 12px; -fx-min-width: 12px; -fx-min-height: 12px; -fx-border-color: transparent; -fx-border-radius: 6px; -fx-background-radius: 6px;"));
            closeBtn.setOnMouseExited(e -> closeBtn.setStyle("-fx-background-color: #ff5f57; -fx-text-fill: #4d0000; -fx-font-weight: bold; -fx-font-size: 12px; -fx-min-width: 12px; -fx-min-height: 12px; -fx-border-color: transparent; -fx-border-radius: 6px; -fx-background-radius: 6px;"));
            
            minimizeBtn.setOnMouseEntered(e -> minimizeBtn.setStyle("-fx-background-color: #ff9500; -fx-text-fill: #975500; -fx-font-weight: bold; -fx-font-size: 12px; -fx-min-width: 12px; -fx-min-height: 12px; -fx-border-color: transparent; -fx-border-radius: 6px; -fx-background-radius: 6px;"));
            minimizeBtn.setOnMouseExited(e -> minimizeBtn.setStyle("-fx-background-color: #ffbd2e; -fx-text-fill: #975500; -fx-font-weight: bold; -fx-font-size: 12px; -fx-min-width: 12px; -fx-min-height: 12px; -fx-border-color: transparent; -fx-border-radius: 6px; -fx-background-radius: 6px;"));
            
            maximizeBtn.setOnMouseEntered(e -> maximizeBtn.setStyle("-fx-background-color: #30d158; -fx-text-fill: #003300; -fx-font-weight: bold; -fx-font-size: 10px; -fx-min-width: 12px; -fx-min-height: 12px; -fx-border-color: transparent; -fx-border-radius: 6px; -fx-background-radius: 6px;"));
            maximizeBtn.setOnMouseExited(e -> maximizeBtn.setStyle("-fx-background-color: #28ca42; -fx-text-fill: #003300; -fx-font-weight: bold; -fx-font-size: 10px; -fx-min-width: 12px; -fx-min-height: 12px; -fx-border-color: transparent; -fx-border-radius: 6px; -fx-background-radius: 6px;"));
        } else {
            // Windows/Linux-Style Hover-Effekte
            minimizeBtn.setOnMouseEntered(e -> minimizeBtn.setStyle("-fx-background-color: #34495e; -fx-text-fill: " + currentTextColor + "; -fx-font-weight: bold; -fx-font-size: 18px; -fx-min-width: 35px; -fx-min-height: 30px; -fx-border-color: transparent; -fx-border-radius: 4px; -fx-background-radius: 4px;"));
            minimizeBtn.setOnMouseExited(e -> minimizeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + currentTextColor + "; -fx-font-weight: bold; -fx-font-size: 18px; -fx-min-width: 35px; -fx-min-height: 30px; -fx-border-color: transparent; -fx-border-radius: 4px; -fx-background-radius: 4px;"));
            
            maximizeBtn.setOnMouseEntered(e -> maximizeBtn.setStyle("-fx-background-color: #34495e; -fx-text-fill: " + currentTextColor + "; -fx-font-weight: bold; -fx-font-size: 16px; -fx-min-width: 35px; -fx-min-height: 30px; -fx-border-color: transparent; -fx-border-radius: 4px; -fx-background-radius: 4px;"));
            maximizeBtn.setOnMouseExited(e -> maximizeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + currentTextColor + "; -fx-font-weight: bold; -fx-font-size: 16px; -fx-min-width: 35px; -fx-min-height: 30px; -fx-border-color: transparent; -fx-border-radius: 4px; -fx-background-radius: 4px;"));
            
            closeBtn.setOnMouseEntered(e -> closeBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: " + currentTextColor + "; -fx-font-weight: bold; -fx-font-size: 20px; -fx-min-width: 35px; -fx-min-height: 30px; -fx-border-color: transparent; -fx-border-radius: 4px; -fx-background-radius: 4px;"));
            closeBtn.setOnMouseExited(e -> closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + currentTextColor + "; -fx-font-weight: bold; -fx-font-size: 20px; -fx-min-width: 35px; -fx-min-height: 30px; -fx-border-color: transparent; -fx-border-radius: 4px; -fx-background-radius: 4px;"));
        }
    }
    
    /**
     * Richtet Drag & Drop für die Titelleiste ein
     */
    private void setupDragAndDrop() {
        titleBar.setOnMousePressed(event -> {
            if (event.getClickCount() == 1 && !isResizing) {
                xOffset = event.getSceneX();
                yOffset = event.getSceneY();
                logger.debug("Drag gestartet: Offset=({},{})", xOffset, yOffset);
            }
        });
        
        titleBar.setOnMouseDragged(event -> {
            if (!isMaximized && !isResizing) {
                double newX = event.getScreenX() - xOffset;
                double newY = event.getScreenY() - yOffset;
                setX(newX);
                setY(newY);
            }
        });
        
        // Doppelklick zum Maximieren/Minimieren
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
        if (isMaximized) {
            setMaximized(false);
            maximizeBtn.setText("□");
            isMaximized = false;
        } else {
            setMaximized(true);
            maximizeBtn.setText("⧉");
            isMaximized = true;
        }
    }
    
    /**
     * Setzt die Scene mit benutzerdefinierter Titelleiste
     */
    public void setSceneWithTitleBar(Scene scene) {
        if (scene != null) {
            // Erstelle neuen Root-Container mit Titelleiste
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
            
            // Neue Scene mit Titelleiste erstellen
            Scene newScene = new Scene(newRoot);
            newScene.getStylesheets().addAll(scene.getStylesheets());
            
            super.setScene(newScene);
            
            // Resize-Handles hinzufügen
            setupResizeHandles(newScene);
            
            logger.info("Scene mit benutzerdefinierter Titelleiste gesetzt");
        } else {
            super.setScene(null);
        }
    }
    
    /**
     * Richtet Resize-Handles für das Fenster ein
     */
    private void setupResizeHandles(Scene scene) {
        final int RESIZE_BORDER = 10; // Vergrößert für bessere Erkennung
        
        // WICHTIG: EventFilter verwenden, um Events VOR anderen Handlern abzufangen
        scene.addEventFilter(MouseEvent.MOUSE_MOVED, event -> {
            if (isMaximized) {
                scene.setCursor(javafx.scene.Cursor.DEFAULT);
                return;
            }
            
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
            if (isMaximized) return;
            
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
        if (isMaximized) return;
        
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
        
        logger.debug("Resize gestartet: Richtung={}, Position=({},{}), Größe=({},{})", 
                    resizeDirection, x, y, width, height);
    }
    
    /**
     * Führt das Resizing durch
     */
    private void performResize(MouseEvent event) {
        if (!isResizing || isMaximized) return;
        
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
                newWidth = Math.max(minWidth, resizeStartWidth - deltaX);
                newHeight = Math.max(minHeight, resizeStartHeight + deltaY);
                newX = getX() + (resizeStartWidth - newWidth);
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
                newWidth = Math.max(minWidth, resizeStartWidth - deltaX);
                newX = getX() + (resizeStartWidth - newWidth);
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
        // Aktuelle Textfarbe für Hover-Effekte speichern
        currentTextColor = textColor;
        
        if (titleBar != null) {
            // Dünne Border um die gesamte Titlebar (nicht nur unten)
            titleBar.setStyle("-fx-background-color: " + backgroundColor + "; -fx-padding: 5px; -fx-spacing: 5px; -fx-border-color: " + borderColor + "; -fx-border-width: 1px; -fx-border-radius: 0;");
            
            // Textfarben für alle Elemente anpassen
            if (titleLabel != null) {
                titleLabel.setStyle("-fx-text-fill: " + textColor + "; -fx-font-weight: bold; -fx-font-size: 14px; -fx-padding: 0 10px; -fx-background-color: transparent; -fx-background-radius: 0;");
            }
            
            // Button-Styles je nach OS
            String osName = System.getProperty("os.name").toLowerCase();
            boolean isMac = osName.contains("mac");
            
            if (isMac) {
                // macOS-Style Buttons (rot-gelb-grün)
                if (closeBtn != null) closeBtn.setStyle("-fx-background-color: #ff5f57; -fx-text-fill: #4d0000; -fx-font-weight: bold; -fx-font-size: 12px; -fx-min-width: 12px; -fx-min-height: 12px; -fx-border-color: transparent; -fx-border-radius: 6px; -fx-background-radius: 6px;");
                if (minimizeBtn != null) minimizeBtn.setStyle("-fx-background-color: #ffbd2e; -fx-text-fill: #975500; -fx-font-weight: bold; -fx-font-size: 12px; -fx-min-width: 12px; -fx-min-height: 12px; -fx-border-color: transparent; -fx-border-radius: 6px; -fx-background-radius: 6px;");
                if (maximizeBtn != null) maximizeBtn.setStyle("-fx-background-color: #28ca42; -fx-text-fill: #003300; -fx-font-weight: bold; -fx-font-size: 10px; -fx-min-width: 12px; -fx-min-height: 12px; -fx-border-color: transparent; -fx-border-radius: 6px; -fx-background-radius: 6px;");
            } else {
                // Windows/Linux-Style Buttons
                String minimizeButtonStyle = "-fx-background-color: transparent; -fx-text-fill: " + textColor + "; -fx-font-weight: bold; -fx-font-size: 18px; -fx-min-width: 35px; -fx-min-height: 30px; -fx-border-color: transparent; -fx-border-radius: 4px; -fx-background-radius: 4px;";
                String maximizeButtonStyle = "-fx-background-color: transparent; -fx-text-fill: " + textColor + "; -fx-font-weight: bold; -fx-font-size: 16px; -fx-min-width: 35px; -fx-min-height: 30px; -fx-border-color: transparent; -fx-border-radius: 4px; -fx-background-radius: 4px;";
                String closeButtonStyle = "-fx-background-color: transparent; -fx-text-fill: " + textColor + "; -fx-font-weight: bold; -fx-font-size: 20px; -fx-min-width: 35px; -fx-min-height: 30px; -fx-border-color: transparent; -fx-border-radius: 4px; -fx-background-radius: 4px;";
                
                if (minimizeBtn != null) minimizeBtn.setStyle(minimizeButtonStyle);
                if (maximizeBtn != null) maximizeBtn.setStyle(maximizeButtonStyle);
                if (closeBtn != null) closeBtn.setStyle(closeButtonStyle);
            }
            
            // Hover-Effekte neu setzen
            setupHoverEffects();
        }
        
        // Stage-Rand setzen
        setStageBorder(backgroundColor);
    }
    
    /**
     * Setzt einen dünnen Rand um die gesamte Stage
     */
    private void setStageBorder(String borderColor) {
        if (getScene() != null && getScene().getRoot() != null) {
            Node root = getScene().getRoot();
            String currentStyle = root.getStyle();
            
            // Entferne alte Border-Styles
            currentStyle = currentStyle.replaceAll("-fx-border-color:[^;]+;?", "");
            currentStyle = currentStyle.replaceAll("-fx-border-width:[^;]+;?", "");
            currentStyle = currentStyle.replaceAll("-fx-border-radius:[^;]+;?", "");
            
            // Füge neuen Border hinzu
            String newBorderStyle = "-fx-border-color: " + borderColor + "; -fx-border-width: 2px; -fx-border-radius: 0px;";
            
            if (currentStyle.trim().isEmpty()) {
                root.setStyle(newBorderStyle);
            } else {
                root.setStyle(currentStyle + "; " + newBorderStyle);
            }
        }
    }
    
    /**
     * Ändert die Farbe der Titelleiste basierend auf Theme
     */
    public void setTitleBarTheme(int themeIndex) {
        String backgroundColor;
        String textColor;
        String borderColor; // Neue Border-Farbe
        
        switch (themeIndex) {
            case 0: // Weiß
                backgroundColor = "linear-gradient(from 0% 0% to 0% 100%, #ffffff 0%, #f8f9fa 100%)";
                textColor = "black";
                borderColor = "black"; // Schwarze Border für weiße Themes
                break;
            case 1: // Schwarz
                backgroundColor = "#1a1a1a"; // Komplett schwarz, kein Gradient
                textColor = "white";
                borderColor = "white"; // Weiße Border für schwarze Themes
                break;
            case 2: // Pastell
                backgroundColor = "linear-gradient(from 0% 0% to 0% 100%, #f3e5f5 0%, #e1bee7 100%)";
                textColor = "black";
                borderColor = "black"; // Schwarze Border für helle Themes
                break;
            case 3: // Blau
                backgroundColor = "linear-gradient(from 0% 0% to 0% 100%, #1e3a8a 0%, #3b82f6 100%)";
                textColor = "white";
                borderColor = "white"; // Weiße Border für dunkle Themes
                break;
            case 4: // Grün
                backgroundColor = "linear-gradient(from 0% 0% to 0% 100%, #064e3b 0%, #10b981 100%)";
                textColor = "white";
                borderColor = "white"; // Weiße Border für dunkle Themes
                break;
            case 5: // Lila
                backgroundColor = "linear-gradient(from 0% 0% to 0% 100%, #581c87 0%, #8b5cf6 100%)";
                textColor = "white";
                borderColor = "white"; // Weiße Border für dunkle Themes
                break;
            default:
                backgroundColor = "linear-gradient(from 0% 0% to 0% 100%, #2c3e50 0%, #34495e 100%)";
                textColor = "white";
                borderColor = "white"; // Weiße Border für dunkle Themes
                break;
        }
        setTitleBarColor(backgroundColor, textColor, borderColor);
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
            
            // Alle Theme-Klassen entfernen
            root.getStyleClass().removeAll("theme-dark", "theme-light", "weiss-theme", "pastell-theme", "blau-theme", "gruen-theme", "lila-theme");
            
            // Neue Theme-Klasse hinzufügen
            switch (themeIndex) {
                case 0: // Weiß
                    root.getStyleClass().add("weiss-theme");
                    break;
                case 1: // Schwarz
                    root.getStyleClass().add("theme-dark");
                    break;
                case 2: // Pastell
                    root.getStyleClass().add("pastell-theme");
                    break;
                case 3: // Blau
                    root.getStyleClass().add("theme-dark");
                    root.getStyleClass().add("blau-theme");
                    break;
                case 4: // Grün
                    root.getStyleClass().add("theme-dark");
                    root.getStyleClass().add("gruen-theme");
                    break;
                case 5: // Lila
                    root.getStyleClass().add("theme-dark");
                    root.getStyleClass().add("lila-theme");
                    break;
            }
            
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
}
