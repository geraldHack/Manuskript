package com.manuskript;

import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Online-Lektorat für {@link ChapterEditorHost} (Canvas-Editor).
 */
public class ChapterOnlineLektoratHelper {

    private static final Logger logger = LoggerFactory.getLogger(ChapterOnlineLektoratHelper.class);

    private final ChapterEditorHost host;
    private final ChapterLektoratPanel panel;
    private final List<LektoratMatch> currentMatches = new ArrayList<>();
    private volatile boolean inProgress;

    public ChapterOnlineLektoratHelper(ChapterEditorHost host, ChapterLektoratPanel panel) {
        this.host = host;
        this.panel = panel;
    }

    public List<LektoratMatch> getCurrentMatches() {
        return currentMatches;
    }

    public boolean isInProgress() {
        return inProgress;
    }

    public void start(boolean enableAssessment) {
        host.setOnlineLektoratMode(true);
        String text = host.getText();
        if (enableAssessment && panel != null) {
            panel.appendChapterAssessment(text);
        }
        if (text == null || text.trim().isEmpty()) {
            host.updateStatus("Editor leer – kein Online-Lektorat möglich");
            return;
        }
        inProgress = true;
        host.setStatusBusyBarActive(true);
        if (panel != null) {
            panel.applyFontSize(host.getEditorFontSizePx());
        }
        String typeLabel = OnlineLektoratService.currentLektoratTypeLabel();
        host.updateStatus("Lektorat (" + typeLabel + ") wird erstellt – bitte warten…");
        OnlineLektoratService service = new OnlineLektoratService();
        BiConsumer<Integer, Integer> onProgress = (done, total) -> Platform.runLater(() -> {
            if (!inProgress) {
                return;
            }
            if (total == 100) {
                host.updateStatus("Lektorat (" + typeLabel + "): " + done + "% …");
            } else {
                host.updateStatus("Lektorat (" + typeLabel + "): " + done + "/" + total + " Abschnitte …");
            }
        });
        service.runLektorat(text, onProgress)
                .thenAccept(result -> Platform.runLater(() -> {
                    inProgress = false;
                    host.setStatusBusyBarActive(false);
                    currentMatches.clear();
                    currentMatches.addAll(result.getMatches());
                    applyMatchesToCanvasEditor();
                    if (panel != null) {
                        panel.showHint(!currentMatches.isEmpty());
                    }
                    if (result.isPartial()) {
                        host.updateStatus(currentMatches.size() + " partielle Lektorat-Vorschläge");
                    } else {
                        host.updateStatus(currentMatches.isEmpty()
                                ? "Keine Lektorat-Vorschläge"
                                : currentMatches.size() + " Lektorat-Vorschläge");
                    }
                    ManuskriptEditorTestWindow canvas = host.asCanvasChapterEditor();
                    if (canvas != null) {
                        canvas.getTextEditor().scrollToOffset(0);
                    }
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        inProgress = false;
                        host.setStatusBusyBarActive(false);
                        host.updateStatusError("Online-Lektorat fehlgeschlagen: " + ex.getMessage());
                    });
                    logger.error("Online-Lektorat", ex);
                    return null;
                });
    }

    private void applyMatchesToCanvasEditor() {
        ManuskriptEditorTestWindow canvas = host.asCanvasChapterEditor();
        if (canvas == null) {
            return;
        }
        canvas.getTextEditor().applyLektoratMatches(currentMatches, this::onMatchSelected);
    }

    private void onMatchSelected(LektoratMatch match) {
        if (panel != null) {
            panel.showMatch(match, suggestion -> applySuggestion(match, suggestion));
        }
        ManuskriptEditorTestWindow canvas = host.asCanvasChapterEditor();
        if (canvas != null) {
            canvas.getTextEditor().revealMatchAt(match.getOffset(), match.getOffset() + match.getLength());
        }
    }

    private void applySuggestion(LektoratMatch match, String replacement) {
        String currentText = host.getText();
        if (currentText == null) {
            return;
        }
        String unescaped = replacement.replace("\\n", "\n").replace("\\t", "\t");
        String original = match.getOriginal();
        int offset = match.getOffset();
        int len = match.getLength();

        int start = offset;
        int end = offset + len;
        if (start < 0 || end > currentText.length()
                || !currentText.substring(start, end).equals(original)) {
            int idx = currentText.indexOf(original);
            if (idx < 0) {
                host.updateStatus("Originaltext im Editor nicht mehr gefunden – evtl. wurde er geändert.");
                return;
            }
            start = idx;
            end = idx + original.length();
        }
        String originalSegment = currentText.substring(start, end);
        String replacementWithBoundaries = preserveParagraphBoundaries(originalSegment, unescaped);
        int originalEnd = start + originalSegment.length();
        int delta = replacementWithBoundaries.length() - originalSegment.length();

        host.replaceRangePreserveView(start, end, replacementWithBoundaries);
        currentMatches.remove(match);

        if (delta != 0 && !currentMatches.isEmpty()) {
            for (LektoratMatch other : currentMatches) {
                if (other == match) {
                    continue;
                }
                int otherOffset = other.getOffset();
                if (otherOffset >= originalEnd) {
                    other.setOffset(otherOffset + delta);
                } else if (otherOffset >= start && otherOffset < originalEnd) {
                    other.setOffset(start);
                }
            }
        }

        if (currentMatches.isEmpty()) {
            ManuskriptEditorTestWindow canvas = host.asCanvasChapterEditor();
            if (canvas != null) {
                canvas.getTextEditor().clearLektoratMatches();
            }
            if (panel != null) {
                panel.clear();
            }
        } else {
            applyMatchesToCanvasEditor();
            if (panel != null) {
                panel.showAppliedHint();
            }
        }
        host.updateStatus("Lektorat-Vorschlag übernommen");
    }

    private static String preserveParagraphBoundaries(String originalText, String replacementText) {
        if (originalText == null) {
            return replacementText;
        }
        if (replacementText == null) {
            return null;
        }
        String result = replacementText;

        Matcher leadingMatcher = Pattern.compile("^(\\n+)").matcher(originalText);
        if (leadingMatcher.find()) {
            String originalLeading = leadingMatcher.group(1);
            Matcher replacementLeadingMatcher = Pattern.compile("^(\\n+)").matcher(result);
            String replacementLeading = replacementLeadingMatcher.find() ? replacementLeadingMatcher.group(1) : "";
            if (replacementLeading.length() < originalLeading.length()) {
                result = originalLeading.substring(replacementLeading.length()) + result;
            }
        }

        Matcher trailingMatcher = Pattern.compile("(\\n+)$").matcher(originalText);
        if (trailingMatcher.find()) {
            String originalTrailing = trailingMatcher.group(1);
            Matcher replacementTrailingMatcher = Pattern.compile("(\\n+)$").matcher(result);
            String replacementTrailing = replacementTrailingMatcher.find() ? replacementTrailingMatcher.group(1) : "";
            if (replacementTrailing.length() < originalTrailing.length()) {
                result = result + originalTrailing.substring(replacementTrailing.length());
            }
        }
        return result;
    }

    public void clear() {
        currentMatches.clear();
        ManuskriptEditorTestWindow canvas = host.asCanvasChapterEditor();
        if (canvas != null) {
            canvas.getTextEditor().clearLektoratMatches();
        }
        if (panel != null) {
            panel.clear();
        }
    }
}
