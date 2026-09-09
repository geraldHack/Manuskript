package com.manuskript;

import javafx.beans.value.ChangeListener;
import javafx.scene.Node;

import java.io.File;

final class MdTextAreaTabContent implements WorldEditorTabContent {

    private final MdTextArea textArea;

    MdTextAreaTabContent(MdTextArea textArea) {
        this.textArea = textArea;
    }

    MdTextArea textArea() {
        return textArea;
    }

    @Override
    public Node getView() {
        return textArea;
    }

    @Override
    public String getText() {
        return textArea.getText();
    }

    @Override
    public void setText(String text) {
        textArea.setText(text == null ? "" : text);
    }

    @Override
    public void addTextChangeListener(ChangeListener<String> listener) {
        textArea.textProperty().addListener(listener);
    }

    @Override
    public void requestFocus() {
        textArea.requestFocus();
    }

    @Override
    public void navigateToSection(String heading) {
        int offset = WorldEditorWindow.findHeadingOffset(textArea.getText(), heading);
        if (offset >= 0) {
            textArea.positionCaret(offset);
        }
    }

    @Override
    public void setImageDirectories(File mdDirectory, File projectDirectory) {
        textArea.setImageDirectories(mdDirectory, projectDirectory);
    }

    @Override
    public void attachImageLightbox(MarkdownImageLightbox lightbox) {
        textArea.attachImageLightbox(lightbox);
    }
}
