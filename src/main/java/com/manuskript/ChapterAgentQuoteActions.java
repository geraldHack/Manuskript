package com.manuskript;

import com.manuskript.agent.Finding;

import java.util.Optional;

/**
 * Zitat-Sprung und Vorschlags-Ersetzung für Agenten im {@link ChapterEditorHost}.
 */
public final class ChapterAgentQuoteActions {

    private ChapterAgentQuoteActions() {
    }

    public static void jumpToQuote(ChapterEditorHost host, String quote) {
        if (host == null || quote == null || quote.isBlank()) {
            return;
        }
        String text = host.getText();
        Optional<QuoteNavigation.QuoteRange> range = QuoteNavigation.findQuoteRange(text, quote);
        if (range.isEmpty() && quote.contains("|")) {
            range = QuoteNavigation.findQuoteRange(text, quote.split("\\|", 2)[0].trim());
        }
        range.ifPresentOrElse(r -> {
            host.selectRange(r.start(), r.end());
            host.revealRange(r.start(), r.end());
            host.requestEditorFocus();
        }, () -> host.updateStatus("Zitat im Kapitel nicht gefunden."));
    }

    public static void replaceWithSuggestion(ChapterEditorHost host, Finding finding) {
        if (host == null || finding == null) {
            return;
        }
        String suggestion = finding.getSuggestion();
        String quote = finding.getQuote();
        if (suggestion == null || suggestion.isBlank() || quote == null || quote.isBlank()) {
            return;
        }

        String rawSuggestion = suggestion.replaceAll("^\"|\"$", "").trim();
        String text = host.getText();
        if (text == null || text.isEmpty()) {
            return;
        }

        Optional<QuoteNavigation.QuoteRange> rangeOpt = QuoteNavigation.findQuoteRange(text, quote);
        if (rangeOpt.isEmpty()) {
            int quoteIndex = text.indexOf(quote);
            if (quoteIndex >= 0) {
                rangeOpt = Optional.of(new QuoteNavigation.QuoteRange(quoteIndex, quoteIndex + quote.length()));
            }
        }
        if (rangeOpt.isEmpty()) {
            host.updateStatus("Zitat im Kapitel nicht gefunden – Ersetzung nicht möglich.");
            return;
        }

        QuoteNavigation.QuoteRange range = rangeOpt.get();
        int rangeStart = range.start();
        int rangeEnd = range.end();
        String replacement = rawSuggestion;

        int oldCaret = host.getCaretPosition();
        int lengthDiff = replacement.length() - (rangeEnd - rangeStart);
        int newCaret;
        if (oldCaret <= rangeStart) {
            newCaret = oldCaret;
        } else if (oldCaret >= rangeEnd) {
            newCaret = oldCaret + lengthDiff;
        } else {
            int oldRangeLen = Math.max(1, rangeEnd - rangeStart);
            double rel = (oldCaret - rangeStart) / (double) oldRangeLen;
            newCaret = rangeStart + (int) Math.round(rel * replacement.length());
        }

        host.replaceRange(rangeStart, rangeEnd, replacement);

        ManuskriptEditorTestWindow canvas = host.asCanvasChapterEditor();
        if (canvas != null) {
            ManuskriptTextEditor editor = canvas.getTextEditor();
            int clamped = Math.max(0, Math.min(editor.getText().length(), newCaret));
            editor.selectRange(clamped, clamped);
            editor.requestInputFocus();
        } else {
            host.selectRange(newCaret, newCaret);
        }
        host.updateStatus("Agenten-Vorschlag übernommen");
    }
}
