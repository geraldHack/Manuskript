package com.manuskript;

import java.util.List;

/**
 * Delete/Backspace im WYSIWYG-Modus bearbeiten sichtbaren Text, nicht die Marker.
 * Steht der Caret vor einem schließenden {@code *} von {@code *huhu*}, würde Delete
 * sonst nur das Markupzeichen entfernen und den Rest sichtbar machen.
 */
final class HiddenMarkupDeleteSupport {

    private HiddenMarkupDeleteSupport() {
    }

    /**
     * Bereich, den Delete entfernen soll, oder {@code null} wenn nichts Sichtbares folgt.
     */
    static int[] forwardRange(int caret, int length,
                              List<HiddenMarkupEnterSupport.Span> hiddenMarkup,
                              List<HiddenMarkupEnterSupport.Span> inlineZones) {
        if (caret >= length) {
            return null;
        }
        int pos = caret;
        while (pos < length && isHiddenAt(pos, hiddenMarkup)) {
            pos++;
        }
        if (pos >= length) {
            return null;
        }
        return expandEmptyZones(pos, pos + 1, hiddenMarkup, inlineZones);
    }

    /**
     * Bereich, den Backspace entfernen soll, oder {@code null} wenn nichts Sichtbares vorangeht.
     */
    static int[] backwardRange(int caret,
                               List<HiddenMarkupEnterSupport.Span> hiddenMarkup,
                               List<HiddenMarkupEnterSupport.Span> inlineZones) {
        if (caret <= 0) {
            return null;
        }
        int pos = caret;
        while (pos > 0 && isHiddenAt(pos - 1, hiddenMarkup)) {
            pos--;
        }
        if (pos <= 0) {
            return null;
        }
        return expandEmptyZones(pos - 1, pos, hiddenMarkup, inlineZones);
    }

    private static int[] expandEmptyZones(int start, int end,
                                          List<HiddenMarkupEnterSupport.Span> hiddenMarkup,
                                          List<HiddenMarkupEnterSupport.Span> inlineZones) {
        int expandedStart = start;
        int expandedEnd = end;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (HiddenMarkupEnterSupport.Span zone : inlineZones) {
                if (zone.end() <= expandedStart || zone.start() >= expandedEnd) {
                    continue;
                }
                if (zoneHasVisibleOutside(zone, expandedStart, expandedEnd, hiddenMarkup)) {
                    continue;
                }
                if (zone.start() < expandedStart || zone.end() > expandedEnd) {
                    expandedStart = Math.min(expandedStart, zone.start());
                    expandedEnd = Math.max(expandedEnd, zone.end());
                    changed = true;
                }
            }
        }
        return new int[]{expandedStart, expandedEnd};
    }

    private static boolean zoneHasVisibleOutside(HiddenMarkupEnterSupport.Span zone,
                                                 int deleteStart, int deleteEnd,
                                                 List<HiddenMarkupEnterSupport.Span> hiddenMarkup) {
        for (int offset = zone.start(); offset < zone.end(); offset++) {
            if (offset >= deleteStart && offset < deleteEnd) {
                continue;
            }
            if (!isHiddenAt(offset, hiddenMarkup)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isHiddenAt(int offset, List<HiddenMarkupEnterSupport.Span> hiddenMarkup) {
        for (HiddenMarkupEnterSupport.Span range : hiddenMarkup) {
            if (offset >= range.start() && offset < range.end()) {
                return true;
            }
        }
        return false;
    }
}
