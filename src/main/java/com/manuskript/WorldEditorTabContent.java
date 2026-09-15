package com.manuskript;

import javafx.beans.value.ChangeListener;
import javafx.scene.Node;

import java.io.File;

/**
 * Gemeinsame Schnittstelle für Welt-Editor-Tabs (Markdown-Fläche oder spezialisierte Ansichten).
 */
interface WorldEditorTabContent {

    Node getView();

    String getText();

    void setText(String text);

    void addTextChangeListener(ChangeListener<String> listener);

    void requestFocus();

    void navigateToSection(String heading);

    default void setImageDirectories(File mdDirectory, File projectDirectory) {
    }

    default void attachImageLightbox(MarkdownImageLightbox lightbox) {
    }

    default void applyTheme(int themeIndex) {
    }
}
