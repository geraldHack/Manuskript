package com.manuskript;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import javafx.scene.control.TextArea;

/**
 * TextArea mit erzwungener nummerierter Liste (1., 2., 3., …).
 * Beim Einfügen oder Löschen wird automatisch neu nummeriert.
 */
public class NumberedListTextArea extends TextArea {

    private static final Pattern NUMBER_PREFIX = Pattern.compile("^\\d+\\.\\s*");

    private boolean updating = false;

    public NumberedListTextArea() {
        getStyleClass().add("numbered-list-textarea");
        setWrapText(true);
        textProperty().addListener((obs, oldText, newText) -> {
            if (updating || newText == null) {
                return;
            }
            renumber(newText);
        });
    }

    /**
     * Setzt Text ohne Neunummerierung (Datei laden). Anschließend wird normalisiert.
     */
    public void setPlainContent(String content) {
        updating = true;
        try {
            if (content == null || content.isBlank()) {
                setText("");
                return;
            }
            setText(content);
            renumber(getText());
        } finally {
            updating = false;
        }
    }

    /**
     * Liefert den Inhalt ohne Nummernpräfixe (zum Speichern).
     */
    public String getContentWithoutNumbers() {
        String[] lines = getText().split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String stripped = stripPrefix(lines[i]).trim();
            if (!stripped.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(stripped);
            }
        }
        return sb.toString();
    }

    /**
     * Liefert nummerierten Text für Anzeige/Prompt (1. …, 2. …).
     */
    public String getNumberedContent() {
        return getText();
    }

    private void renumber(String rawText) {
        int caret = getCaretPosition();
        int anchor = getAnchor();

        CaretContext before = mapCaretToContext(rawText, caret);
        CaretContext anchorBefore = mapCaretToContext(rawText, anchor);

        List<String> contents = new ArrayList<>();
        if (!rawText.isEmpty()) {
            for (String line : rawText.split("\n", -1)) {
                contents.add(stripPrefix(line));
            }
        }
        if (contents.isEmpty()) {
            contents.add("");
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < contents.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(i + 1).append(". ").append(contents.get(i));
        }
        String renumbered = sb.toString();
        if (renumbered.equals(rawText)) {
            return;
        }

        updating = true;
        try {
            setText(renumbered);
            selectRange(
                mapContextToOffset(renumbered, anchorBefore),
                mapContextToOffset(renumbered, before)
            );
        } finally {
            updating = false;
        }
    }

    private static String stripPrefix(String line) {
        if (line == null) {
            return "";
        }
        return NUMBER_PREFIX.matcher(line).replaceFirst("");
    }

    private static CaretContext mapCaretToContext(String text, int offset) {
        offset = Math.max(0, Math.min(offset, text.length()));
        String before = text.substring(0, offset);
        int lineIndex = before.isEmpty() ? 0 : before.split("\n", -1).length - 1;
        String[] lines = text.split("\n", -1);
        if (lineIndex >= lines.length) {
            lineIndex = Math.max(0, lines.length - 1);
        }
        String line = lines.length > 0 ? lines[lineIndex] : "";
        int prefixLen = prefixLength(line);
        int colInContent = Math.max(0, offset - lineStartOffset(text, lineIndex) - prefixLen);
        return new CaretContext(lineIndex, colInContent);
    }

    private static int mapContextToOffset(String text, CaretContext ctx) {
        String[] lines = text.split("\n", -1);
        if (lines.length == 0) {
            return 0;
        }
        int lineIndex = Math.max(0, Math.min(ctx.lineIndex, lines.length - 1));
        int lineStart = lineStartOffset(text, lineIndex);
        String line = lines[lineIndex];
        int prefixLen = prefixLength(line);
        int contentLen = Math.max(0, line.length() - prefixLen);
        int col = Math.max(0, Math.min(ctx.colInContent, contentLen));
        return lineStart + prefixLen + col;
    }

    private static int lineStartOffset(String text, int lineIndex) {
        int pos = 0;
        for (int i = 0; i < lineIndex; i++) {
            int next = text.indexOf('\n', pos);
            if (next < 0) {
                return text.length();
            }
            pos = next + 1;
        }
        return pos;
    }

    private static int prefixLength(String line) {
        var m = NUMBER_PREFIX.matcher(line);
        return m.find() ? m.end() : 0;
    }

    private static final class CaretContext {
        final int lineIndex;
        final int colInContent;

        CaretContext(int lineIndex, int colInContent) {
            this.lineIndex = lineIndex;
            this.colInContent = colInContent;
        }
    }
}
