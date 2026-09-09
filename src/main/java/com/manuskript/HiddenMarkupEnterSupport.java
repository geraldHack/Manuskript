package com.manuskript;

import java.util.List;

/**
 * Enter im WYSIWYG-Modus darf verstecktes Inline-Markup nicht vom Wort trennen.
 * Steht der Caret visuell am Anfang von {@code *huhu*}, liegt er logisch hinter dem
 * öffnenden {@code *} – ein Umbruch dort macht die Marker ungültig und zeigt sie
 * in der Zeile darüber.
 */
final class HiddenMarkupEnterSupport {

    private HiddenMarkupEnterSupport() {
    }

    record Span(int start, int end) {
        boolean empty() {
            return end <= start;
        }
    }

    /**
     * @param caret        gewünschte Einfügeposition (nach Caret-Normalisierung)
     * @param lineStart    Start der logischen Zeile (nicht über {@code \n} ziehen)
     * @param lineEnd      Ende der Zeile (Index von {@code \n} bzw. Textlänge)
     * @param hiddenMarkup versteckte Marker, jeweils {@code [start, end)}
     * @param inlineZones  volle Inline-Spannen inklusive Marker
     */
    static int insertionOffset(int caret, int lineStart, int lineEnd,
                               List<Span> hiddenMarkup, List<Span> inlineZones) {
        int lo = Math.max(0, lineStart);
        int hi = Math.max(lo, lineEnd);
        int offset = Math.max(lo, Math.min(hi, caret));
        offset = pullBeforeOpeningMarkup(offset, lo, hiddenMarkup, inlineZones);
        offset = pushAfterClosingMarkup(offset, hi, hiddenMarkup, inlineZones);
        return offset;
    }

    private static int pullBeforeOpeningMarkup(int offset, int lineStart,
                                               List<Span> hiddenMarkup, List<Span> inlineZones) {
        int pulled = offset;
        while (pulled > lineStart) {
            Span opening = hiddenRangeEndingAt(hiddenMarkup, pulled);
            if (opening == null || opening.start < lineStart || opening.empty()) {
                break;
            }
            if (!hasZoneStartingAt(inlineZones, opening.start, pulled)) {
                break;
            }
            pulled = opening.start;
        }
        return pulled;
    }

    private static int pushAfterClosingMarkup(int offset, int lineEnd,
                                              List<Span> hiddenMarkup, List<Span> inlineZones) {
        int pushed = offset;
        while (pushed < lineEnd) {
            Span closing = hiddenRangeStartingAt(hiddenMarkup, pushed);
            if (closing == null || closing.end > lineEnd || closing.empty()) {
                break;
            }
            if (!hasZoneEndingAt(inlineZones, closing.end, pushed)) {
                break;
            }
            pushed = closing.end;
        }
        return pushed;
    }

    private static Span hiddenRangeEndingAt(List<Span> hiddenMarkup, int offset) {
        Span found = null;
        for (Span range : hiddenMarkup) {
            if (range.end == offset && range.start < offset) {
                found = range;
            }
        }
        return found;
    }

    private static Span hiddenRangeStartingAt(List<Span> hiddenMarkup, int offset) {
        for (Span range : hiddenMarkup) {
            if (range.start == offset && range.end > offset) {
                return range;
            }
        }
        return null;
    }

    private static boolean hasZoneStartingAt(List<Span> inlineZones, int zoneStart, int caretInZone) {
        for (Span zone : inlineZones) {
            if (zone.start == zoneStart && caretInZone < zone.end) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasZoneEndingAt(List<Span> inlineZones, int zoneEnd, int caretInZone) {
        for (Span zone : inlineZones) {
            if (zone.end == zoneEnd && caretInZone > zone.start) {
                return true;
            }
        }
        return false;
    }
}
