package com.manuskript;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.util.Duration;
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
    private Timeline lektoratRefreshTimeline;

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

    public boolean isActive() {
        return isSessionOpen();
    }

    /** Lektorat-Sitzung läuft oder Panel ist noch geöffnet (auch ohne offene Vorschläge). */
    public boolean isSessionOpen() {
        return inProgress || (panel != null && panel.isVisible()) || !currentMatches.isEmpty();
    }

    public void start(boolean enableAssessment) {
        start(enableAssessment, OnlineLektoratService.currentLektoratType());
    }

    public void start(boolean enableAssessment, String lektoratType) {
        String type = OnlineLektoratService.normalizeLektoratType(lektoratType);
        String text = host.getText();
        if (text == null || text.trim().isEmpty()) {
            host.updateStatus("Editor leer – kein Online-Lektorat möglich");
            return;
        }
        host.setOnlineLektoratMode(true);
        inProgress = true;
        host.setStatusBusyBarActive(true);
        if (panel != null) {
            panel.applyFontSize(host.getEditorFontSizePx());
            panel.showRunning();
        }
        if (enableAssessment && panel != null) {
            panel.appendChapterAssessment(text);
        }
        String typeLabel = OnlineLektoratService.formatLektoratTypeLabel(type);
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
        service.runLektorat(text, type, onProgress)
                .thenAccept(result -> Platform.runLater(() -> {
                    if (!inProgress) {
                        return;
                    }
                    inProgress = false;
                    host.setStatusBusyBarActive(false);
                    currentMatches.clear();
                    currentMatches.addAll(result.getMatches());
                    syncMatchesToCurrentText();
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
                        if (!inProgress) {
                            return;
                        }
                        inProgress = false;
                        host.setStatusBusyBarActive(false);
                        host.updateStatusError("Online-Lektorat fehlgeschlagen: " + ex.getMessage());
                        if (panel != null) {
                            panel.showHint(false);
                        }
                    });
                    logger.error("Online-Lektorat", ex);
                    return null;
                });
    }

    /** Beendet Lektorat (Abbrechen oder Schließen): Markierungen weg, Panel zu, Editor-Modus normal. */
    public void exit() {
        inProgress = false;
        if (lektoratRefreshTimeline != null) {
            lektoratRefreshTimeline.stop();
            lektoratRefreshTimeline = null;
        }
        host.setStatusBusyBarActive(false);
        currentMatches.clear();
        ManuskriptEditorTestWindow canvas = host.asCanvasChapterEditor();
        if (canvas != null) {
            canvas.getTextEditor().clearLektoratMatches();
            canvas.closeLektoratPanel();
        } else if (panel != null) {
            panel.clear();
        }
        host.setOnlineLektoratMode(false);
        host.updateStatus("Lektorat beendet");
    }

    /** Editor-Text hat sich geändert – Markierungen neu auflösen (debounced). */
    public void onEditorTextChanged() {
        if (inProgress || currentMatches.isEmpty()) {
            return;
        }
        if (lektoratRefreshTimeline != null) {
            lektoratRefreshTimeline.stop();
        }
        lektoratRefreshTimeline = new Timeline(new KeyFrame(Duration.millis(300), event -> {
            lektoratRefreshTimeline = null;
            if (inProgress || currentMatches.isEmpty()) {
                return;
            }
            syncMatchesToCurrentText();
            applyMatchesToCanvasEditor();
        }));
        lektoratRefreshTimeline.play();
    }

    private void syncMatchesToCurrentText() {
        String text = host.getText();
        if (text == null || currentMatches.isEmpty()) {
            return;
        }
        currentMatches.removeIf(match -> LektoratMatchLocator.resolveSpan(text, match) == null);
        LektoratMatchLocator.resolveAllInPlace(text, currentMatches);
    }

    private void applyMatchesToCanvasEditor() {
        ManuskriptEditorTestWindow canvas = host.asCanvasChapterEditor();
        if (canvas == null) {
            return;
        }
        syncMatchesToCurrentText();
        canvas.getTextEditor().applyLektoratMatches(currentMatches, this::onMatchSelected);
    }

    private void onMatchSelected(LektoratMatch match) {
        if (panel != null) {
            panel.showMatch(match, suggestion -> applySuggestion(match, suggestion));
        }
        ManuskriptEditorTestWindow canvas = host.asCanvasChapterEditor();
        if (canvas != null) {
            canvas.getTextEditor().revealLektoratMatch(match);
        }
    }

    private void applySuggestion(LektoratMatch match, String replacement) {
        String currentText = host.getText();
        if (currentText == null) {
            return;
        }
        String unescaped = replacement.replace("\\n", "\n").replace("\\t", "\t");
        int[] span = LektoratMatchLocator.resolveSpan(currentText, match);
        if (span == null) {
            host.updateStatus("Originaltext im Editor nicht mehr gefunden – evtl. wurde er geändert.");
            return;
        }
        int start = span[0];
        int end = span[1];
        String originalSegment = currentText.substring(start, end);
        String replacementWithBoundaries = preserveParagraphBoundaries(originalSegment, unescaped);

        host.replaceRangePreserveView(start, end, replacementWithBoundaries);
        currentMatches.remove(match);
        syncMatchesToCurrentText();

        if (currentMatches.isEmpty()) {
            exit();
            return;
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
        exit();
    }
}
