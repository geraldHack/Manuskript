package com.manuskript;

import com.manuskript.agent.AgentResponseText;
import com.manuskript.agent.Finding;
import com.manuskript.agent.SelectionRevisionSupport;

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

        String rawSuggestion = AgentResponseText.normalizeModelText(
                suggestion.replaceAll("^\"|\"$", ""));
        String text = host.getText();
        if (text == null || text.isEmpty()) {
            return;
        }

        Optional<QuoteNavigation.QuoteRange> rangeOpt = Optional.empty();
        if (finding.hasReplaceRange() && finding.getReplaceRangeEnd() <= text.length()) {
            rangeOpt = Optional.of(new QuoteNavigation.QuoteRange(
                    finding.getReplaceRangeStart(), finding.getReplaceRangeEnd()));
        }
        if (rangeOpt.isEmpty() && !SelectionRevisionSupport.isMarkedQuotePlaceholder(quote)) {
            rangeOpt = QuoteNavigation.findQuoteRange(text, quote);
            if (rangeOpt.isEmpty()) {
                int quoteIndex = text.indexOf(quote);
                if (quoteIndex >= 0) {
                    rangeOpt = Optional.of(new QuoteNavigation.QuoteRange(quoteIndex, quoteIndex + quote.length()));
                }
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

        host.replaceRangePreserveView(rangeStart, rangeEnd, replacement);
        host.requestEditorFocus();
        host.updateStatus("Agenten-Vorschlag übernommen");
    }
}
