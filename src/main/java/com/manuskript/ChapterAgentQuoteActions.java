package com.manuskript;

import com.manuskript.agent.AgentResponseText;
import com.manuskript.agent.Finding;
import com.manuskript.agent.IdiomReviewSupport;
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
        if (suggestion == null || suggestion.isBlank()) {
            if (finding.getSuggestions() != null && !finding.getSuggestions().isEmpty()) {
                suggestion = finding.getSuggestions().get(0);
            }
        }
        String quote = finding.getQuote();
        if (suggestion == null || suggestion.isBlank()) {
            return;
        }
        if ((quote == null || quote.isBlank()) && !finding.hasReplaceRange()) {
            return;
        }

        String rawSuggestion = prepareReplacementText(suggestion, host.getQuoteStyleIndex());
        String text = host.getText();
        if (text == null || text.isEmpty()) {
            return;
        }

        Optional<QuoteNavigation.QuoteRange> rangeOpt = resolveReplacementRange(text, finding);
        if (rangeOpt.isEmpty()) {
            host.updateStatus("Zitat im Kapitel nicht gefunden – Ersetzung nicht möglich.");
            return;
        }

        QuoteNavigation.QuoteRange range = rangeOpt.get();
        host.replaceRangePreserveView(range.start(), range.end(), rawSuggestion);
        host.updateStatus("Agenten-Vorschlag übernommen");
    }

    /**
     * Ermittelt den Ersetzungsbereich. Gespeicherte Absolute-Offsets aus der Analyse werden nur
     * verwendet, wenn der Text dort noch zum Zitat passt — nach früheren Ersetzungen wird per
     * Zitat-Suche im aktuellen Dokument gesucht (wie beim Sprung zum Zitat).
     */
    static Optional<QuoteNavigation.QuoteRange> resolveReplacementRange(String text, Finding finding) {
        if (text == null || text.isEmpty() || finding == null) {
            return Optional.empty();
        }
        String quote = finding.getQuote();

        if (SelectionRevisionSupport.isMarkedQuotePlaceholder(quote)) {
            return storedReplaceRangeIfValid(text, finding, null);
        }

        if (quote != null && !quote.isBlank()) {
            Optional<QuoteNavigation.QuoteRange> fromQuote = findQuoteRangeWithHint(text, quote, finding);
            if (fromQuote.isPresent()) {
                return fromQuote;
            }
        }

        return storedReplaceRangeIfValid(text, finding, quote);
    }

    private static Optional<QuoteNavigation.QuoteRange> findQuoteRangeWithHint(
            String text, String quote, Finding finding) {
        Optional<QuoteNavigation.QuoteRange> stored = storedReplaceRangeIfValid(text, finding, quote);
        if (stored.isPresent()) {
            return stored;
        }
        if (finding.hasReplaceRange()) {
            int hintStart = finding.getReplaceRangeStart();
            int hintEnd = Math.min(finding.getReplaceRangeEnd(), text.length());
            if (hintStart >= 0 && hintEnd > hintStart) {
                Optional<QuoteNavigation.QuoteRange> atHint = offsetQuoteRange(
                        QuoteNavigation.findQuoteRangeStrict(text.substring(hintStart, hintEnd), quote),
                        hintStart);
                if (atHint.isPresent()) {
                    return Optional.of(adjustReplacementRange(text, atHint.get(), finding));
                }
            }
        }
        Optional<QuoteNavigation.QuoteRange> found = QuoteNavigation.findQuoteRangeStrict(text, quote);
        if (found.isPresent()) {
            return Optional.of(adjustReplacementRange(text, found.get(), finding));
        }
        int quoteIndex = text.indexOf(quote);
        if (quoteIndex >= 0) {
            return Optional.of(adjustReplacementRange(text,
                    new QuoteNavigation.QuoteRange(quoteIndex, quoteIndex + quote.length()), finding));
        }
        return Optional.empty();
    }

    private static Optional<QuoteNavigation.QuoteRange> offsetQuoteRange(
            Optional<QuoteNavigation.QuoteRange> local, int offset) {
        if (local.isEmpty()) {
            return Optional.empty();
        }
        QuoteNavigation.QuoteRange range = local.get();
        return Optional.of(new QuoteNavigation.QuoteRange(offset + range.start(), offset + range.end()));
    }

    /**
     * Sprachentflechtung ersetzt ganze Sätze — Bereich bis Satzende erweitern, falls die Suche
     * nur einen kürzeren Treffer liefert.
     */
    private static QuoteNavigation.QuoteRange adjustReplacementRange(
            String text, QuoteNavigation.QuoteRange range, Finding finding) {
        if (finding.getSelectionQuoteIndex() < 0) {
            return range;
        }
        int sentenceEnd = IdiomReviewSupport.endOfSentence(text, range.start());
        if (sentenceEnd > range.end()) {
            return new QuoteNavigation.QuoteRange(range.start(), Math.min(text.length(), sentenceEnd));
        }
        return range;
    }

    private static Optional<QuoteNavigation.QuoteRange> storedReplaceRangeIfValid(
            String text, Finding finding, String quote) {
        if (!finding.hasReplaceRange()) {
            return Optional.empty();
        }
        int start = finding.getReplaceRangeStart();
        int end = finding.getReplaceRangeEnd();
        if (start < 0 || end <= start || end > text.length()) {
            return Optional.empty();
        }
        if (quote != null && !quote.isBlank()
                && !rangeContainsQuote(text, start, end, quote)) {
            return Optional.empty();
        }
        return Optional.of(new QuoteNavigation.QuoteRange(start, end));
    }

    private static boolean rangeContainsQuote(String document, int start, int end, String quote) {
        return QuoteNavigation.findQuoteRangeStrict(document.substring(start, end), quote).isPresent();
    }

    /**
     * Bereitet Agenten-Vorschläge für die Ersetzung im Editor vor: Escapes normalisieren,
     * Modell-Umhüllung entfernen, Anführungszeichen in den Editor-Stil konvertieren.
     */
    static String prepareReplacementText(String suggestion, int quoteStyleIndex) {
        if (suggestion == null || suggestion.isBlank()) {
            return suggestion != null ? suggestion : "";
        }
        String normalized = AgentResponseText.normalizeModelText(suggestion.trim());
        normalized = stripPeripheralWrapperQuotes(normalized);
        return QuotationMarkSupport.convertTextToStyle(normalized, quoteStyleIndex);
    }

    private static String stripPeripheralWrapperQuotes(String text) {
        if (text == null || text.length() < 2) {
            return text != null ? text : "";
        }
        char open = text.charAt(0);
        char close = text.charAt(text.length() - 1);
        if ((open == '"' && close == '"')
                || (open == '\u201E' && (close == '"' || close == '\u201C'))) {
            return text.substring(1, text.length() - 1).trim();
        }
        return text;
    }
}
