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

        int quoteStyle = host.getQuoteStyleIndex();
        String rawSuggestion = prepareReplacementText(suggestion, quoteStyle);
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
        String originalSlice = text.substring(range.start(), range.end());
        String toInsert = applyQuoteWrapping(originalSlice, rawSuggestion, quoteStyle);

        if (SelectionRevisionSupport.isLikelyTruncatedRewrite(originalSlice, rawSuggestion)) {
            String merged = SelectionRevisionSupport.mergeTruncatedRewrite(originalSlice, rawSuggestion);
            if (merged == null || merged.isBlank()) {
                host.updateStatus("Vorschlag scheint unvollständig – Ersetzung abgebrochen. "
                        + "Bitte kürzere Markierung wählen oder erneut analysieren.");
                return;
            }
            toInsert = applyQuoteWrapping(originalSlice, merged, quoteStyle);
            host.replaceRangePreserveView(range.start(), range.end(), toInsert);
            host.updateStatus("Vorschlag übernommen (unvollständig – Rest der Markierung behalten).");
            return;
        }

        host.replaceRangePreserveView(range.start(), range.end(), toInsert);
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
     * Anführungszeichen in den Editor-Stil konvertieren. Äußere Anführungszeichen bleiben.
     */
    static String prepareReplacementText(String suggestion, int quoteStyleIndex) {
        if (suggestion == null || suggestion.isBlank()) {
            return suggestion != null ? suggestion : "";
        }
        String normalized = AgentResponseText.normalizeModelText(suggestion.trim());
        return QuotationMarkSupport.convertTextToStyle(normalized, quoteStyleIndex);
    }

    /**
     * Steht der Vorschlag oder das Originalzitat in Anführungszeichen, bleibt die
     * Ersetzung ebenfalls in Anführungszeichen (Editor-Stil).
     */
    static String applyQuoteWrapping(String originalSlice, String replacement, int quoteStyleIndex) {
        String prepared = prepareReplacementText(replacement, quoteStyleIndex);
        if (prepared == null || prepared.isEmpty()) {
            return prepared != null ? prepared : "";
        }
        if (isWrappedInQuotes(prepared)) {
            return prepared;
        }
        if (!isWrappedInQuotes(originalSlice)) {
            return prepared;
        }
        String wrapped = wrappingOpen(originalSlice) + prepared + wrappingClose(originalSlice);
        return QuotationMarkSupport.convertTextToStyle(wrapped, quoteStyleIndex);
    }

    static boolean isWrappedInQuotes(String text) {
        if (text == null) {
            return false;
        }
        String trimmed = text.strip();
        return trimmed.length() >= 2
                && isWrappingQuoteChar(trimmed.charAt(0))
                && isWrappingQuoteChar(trimmed.charAt(trimmed.length() - 1));
    }

    private static char wrappingOpen(String text) {
        return text.strip().charAt(0);
    }

    private static char wrappingClose(String text) {
        String trimmed = text.strip();
        return trimmed.charAt(trimmed.length() - 1);
    }

    private static boolean isWrappingQuoteChar(char c) {
        return switch (c) {
            case '"', '\'', '\u00AB', '\u00BB', '\u2018', '\u2019', '\u201A', '\u201B',
                 '\u201C', '\u201D', '\u201E', '\u201F', '\u2039', '\u203A' -> true;
            default -> false;
        };
    }
}
