package com.manuskript;

import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.control.Label;

/**
 * Vollbild-Overlay für vergrößerte Bildvorschau (z. B. Welt-Editor).
 * Erneuter Klick auf das Bild schließt die Vorschau.
 */
public final class MarkdownImageLightbox {

    private final StackPane host;
    private final Pane overlay;
    private final ImageView imageView;
    private final Label captionLabel;
    private Integer shownBlockKey;

    public MarkdownImageLightbox(StackPane host) {
        this.host = host;
        overlay = new StackPane();
        overlay.getStyleClass().add("markdown-image-lightbox");
        overlay.setVisible(false);
        overlay.setManaged(false);
        overlay.setMouseTransparent(true);

        VBox content = new VBox(10);
        content.setAlignment(Pos.CENTER);
        content.setMaxWidth(Double.MAX_VALUE);
        content.setMaxHeight(Double.MAX_VALUE);

        imageView = new ImageView();
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.setPickOnBounds(true);
        imageView.setCursor(Cursor.HAND);
        imageView.getStyleClass().add("markdown-image-lightbox-image");

        captionLabel = new Label();
        captionLabel.getStyleClass().add("markdown-image-lightbox-caption");
        captionLabel.setWrapText(true);
        captionLabel.setMaxWidth(RegionHelper.maxLightboxCaptionWidth(host));
        captionLabel.setAlignment(Pos.CENTER);
        captionLabel.setMouseTransparent(true);

        content.getChildren().addAll(imageView, captionLabel);
        overlay.getChildren().add(content);
        StackPane.setAlignment(content, Pos.CENTER);

        imageView.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                hide();
                event.consume();
            }
        });

        host.getChildren().add(overlay);
        host.widthProperty().addListener((obs, old, width) -> refreshImageSize());
        host.heightProperty().addListener((obs, old, height) -> refreshImageSize());
    }

    public boolean isShowing() {
        return overlay.isVisible();
    }

    public void toggle(Image image, String caption, int blockKey) {
        if (image == null) {
            hide();
            return;
        }
        if (overlay.isVisible() && blockKey == shownBlockKey) {
            hide();
            return;
        }
        show(image, caption, blockKey);
    }

    public void show(Image image, String caption, int blockKey) {
        if (image == null) {
            hide();
            return;
        }
        shownBlockKey = blockKey;
        imageView.setImage(image);
        if (caption != null && !caption.isBlank()) {
            captionLabel.setText(caption.trim());
            captionLabel.setVisible(true);
            captionLabel.setManaged(true);
        } else {
            captionLabel.setText("");
            captionLabel.setVisible(false);
            captionLabel.setManaged(false);
        }
        captionLabel.setMaxWidth(RegionHelper.maxLightboxCaptionWidth(host));
        refreshImageSize();
        overlay.setVisible(true);
        overlay.setManaged(true);
        overlay.setMouseTransparent(false);
        overlay.toFront();
    }

    public void hide() {
        shownBlockKey = null;
        overlay.setVisible(false);
        overlay.setManaged(false);
        overlay.setMouseTransparent(true);
        imageView.setImage(null);
    }

    private void refreshImageSize() {
        Image image = imageView.getImage();
        if (image == null) {
            return;
        }
        double maxW = Math.max(160, host.getWidth() * 0.92);
        double maxH = Math.max(160, host.getHeight() * 0.86);
        double w = image.getWidth();
        double h = image.getHeight();
        if (w <= 0 || h <= 0) {
            imageView.setFitWidth(maxW);
            imageView.setFitHeight(0);
            return;
        }
        double scale = Math.min(maxW / w, maxH / h);
        imageView.setFitWidth(w * scale);
        imageView.setFitHeight(0);
    }

    /** Hilfsklasse, damit {@link javafx.scene.layout.Region}-Import nicht in der API liegt. */
    private static final class RegionHelper {
        private RegionHelper() {
        }

        static double maxLightboxCaptionWidth(StackPane host) {
            return Math.max(240, host.getWidth() * 0.85);
        }
    }
}
